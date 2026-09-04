package com.comercioflex.order.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.order.domain.GuestOrder;
import com.comercioflex.order.domain.OrderStatus;

@Service
public class GuestOrderService {

	private static final Duration RESERVATION_DURATION = Duration.ofMinutes(30);
	private static final BigDecimal MAX_SUBTOTAL = new BigDecimal("9999999999999.99");
	private static final String TOKEN_NAMESPACE = "comercio-flex-order-token:";

	private final GuestOrderRepository repository;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	@Autowired
	public GuestOrderService(
			GuestOrderRepository repository,
			@Qualifier("tenantTransactionTemplate") TransactionTemplate transactionTemplate) {
		this(repository, transactionTemplate, Clock.systemUTC());
	}

	GuestOrderService(
			GuestOrderRepository repository,
			TransactionTemplate transactionTemplate,
			Clock clock) {
		this.repository = repository;
		this.transactionTemplate = transactionTemplate;
		this.clock = clock;
	}

	public GuestOrderCreation create(CreateGuestOrderCommand rawCommand) {
		CreateGuestOrderCommand command = validate(rawCommand);
		byte[] fingerprint = fingerprint(command);
		String lookupToken = deriveLookupToken(command.idempotencyKey());
		byte[] tokenHash = sha256(lookupToken);

		try {
			return transactionTemplate.execute(status ->
				createInsideTransaction(command, fingerprint, lookupToken, tokenHash));
		}
		catch (DuplicateKeyException exception) {
			return transactionTemplate.execute(status ->
				replay(command.idempotencyKey(), fingerprint, lookupToken));
		}
	}

	public GuestOrder find(UUID orderId, String lookupToken) {
		if (orderId == null || lookupToken == null || lookupToken.isBlank()) {
			throw new GuestOrderNotFoundException();
		}
		byte[] tokenHash = sha256(lookupToken);
		return transactionTemplate.execute(status -> {
			GuestOrder order = repository.findByPublicIdAndTokenHash(orderId, tokenHash)
				.orElseThrow(GuestOrderNotFoundException::new);
			if (order.status() == OrderStatus.PENDING_CONFIRMATION
					&& !order.reservationExpiresAt().isAfter(clock.instant())) {
				repository.expireOrder(order.orderNumber());
				return repository.findByInternalId(order.orderNumber());
			}
			return order;
		});
	}

	private GuestOrderCreation createInsideTransaction(
			CreateGuestOrderCommand command,
			byte[] fingerprint,
			String lookupToken,
			byte[] tokenHash) {
		var existing = repository.findByIdempotencyKey(command.idempotencyKey());
		if (existing.isPresent()) {
			return replay(existing.get(), fingerprint, lookupToken);
		}

		List<ReservedOrderItem> items = new ArrayList<>();
		BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
		for (OrderItemCommand requested : command.items()) {
			LockedOrderVariant variant = repository.lockVariant(requested.variantId())
				.orElseThrow(OrderUnavailableException::new);
			if (!variant.sellable()
					|| variant.physicalQuantity()
						.subtract(variant.reservedQuantity())
						.compareTo(requested.quantity()) < 0) {
				throw new OrderUnavailableException();
			}
			BigDecimal lineTotal = variant.unitPrice()
				.multiply(requested.quantity())
				.setScale(2, RoundingMode.HALF_UP);
			subtotal = subtotal.add(lineTotal);
			if (subtotal.compareTo(MAX_SUBTOTAL) > 0) {
				throw new InvalidGuestOrderException(
					"El total del pedido supera el máximo permitido.");
			}
			items.add(new ReservedOrderItem(
				variant,
				UUID.randomUUID(),
				requested.quantity(),
				lineTotal));
		}

		BigDecimal listSubtotal = subtotal;
		OrderPaymentPricing paymentPricing = repository.findPaymentPricing();

		BigDecimal discountPercentage = BigDecimal.ZERO.setScale(2);
		BigDecimal discountAmount = BigDecimal.ZERO.setScale(2);

		if (command.paymentMethod()
				== com.comercioflex.order.domain.OrderPaymentMethod.BANK_TRANSFER) {

				if (!paymentPricing.bankTransferEnabled()) {
						throw new InvalidGuestOrderException(
								"La transferencia bancaria no está habilitada para esta tienda.");
				}

				discountPercentage = paymentPricing.bankTransferDiscountPercentage() == null
						? BigDecimal.ZERO.setScale(2)
						: paymentPricing.bankTransferDiscountPercentage()
								.setScale(2, RoundingMode.HALF_UP);

				discountAmount = listSubtotal
						.multiply(discountPercentage)
						.divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
		}

		BigDecimal finalSubtotal = listSubtotal
				.subtract(discountAmount)
				.setScale(2, RoundingMode.HALF_UP);

		Instant expiresAt = clock.instant().plus(RESERVATION_DURATION);
		UUID orderId = UUID.randomUUID();

		long internalId = repository.insertOrder(
				orderId,
				command.idempotencyKey(),
				fingerprint,
				tokenHash,
				command.customerName(),
				command.customerPhone(),
				command.customerEmail(),
				command.notes(),
				repository.findCurrencyCode(),
				command.paymentMethod(),
				listSubtotal,
				discountPercentage,
				discountAmount,
				finalSubtotal,
				expiresAt);

		repository.insertInitialHistory(internalId);
		repository.insertItemsAndReservations(internalId, items, expiresAt);

		return new GuestOrderCreation(
				repository.findByInternalId(internalId),
				lookupToken,
				false);
		}

	private GuestOrderCreation replay(
			UUID idempotencyKey,
			byte[] fingerprint,
			String lookupToken) {
		StoredGuestOrder stored = repository.findByIdempotencyKey(idempotencyKey)
			.orElseThrow(() -> new IllegalStateException(
				"No se pudo recuperar el pedido idempotente."));
		return replay(stored, fingerprint, lookupToken);
	}

	private GuestOrderCreation replay(
			StoredGuestOrder stored,
			byte[] fingerprint,
			String lookupToken) {
		if (!MessageDigest.isEqual(stored.requestFingerprint(), fingerprint)) {
			throw new OrderIdempotencyConflictException();
		}
		GuestOrder order = stored.order();
		if (order.status() == OrderStatus.PENDING_CONFIRMATION
				&& !order.reservationExpiresAt().isAfter(clock.instant())) {
			repository.expireOrder(order.orderNumber());
			order = repository.findByInternalId(order.orderNumber());
		}
		return new GuestOrderCreation(order, lookupToken, true);
	}

	private CreateGuestOrderCommand validate(CreateGuestOrderCommand command) {
		if (command == null || !isVersionFour(command.idempotencyKey())) {
			throw new InvalidGuestOrderException(
				"Idempotency-Key debe ser un UUID v4 válido.");
		}
		if (command.paymentMethod() == null) {
				throw new InvalidGuestOrderException(
						"Debe indicar un medio de pago.");
		}
		String name = requiredText(command.customerName(), 160, "El nombre");
		String phone = requiredText(command.customerPhone(), 40, "El teléfono");
		String email = requiredText(command.customerEmail(), 254, "El correo")
			.toLowerCase(Locale.ROOT);
		String notes = optionalText(command.notes(), 1000, "Las observaciones");
		if (command.items() == null || command.items().isEmpty()) {
			throw new InvalidGuestOrderException(
				"El pedido debe contener al menos un producto.");
		}
		if (command.items().size() > 50) {
			throw new InvalidGuestOrderException(
				"El pedido no puede contener más de 50 productos.");
		}
		Set<UUID> variants = new HashSet<>();
		List<OrderItemCommand> items = command.items().stream()
			.map(item -> validateItem(item, variants))
			.sorted(Comparator.comparing(item -> item.variantId().toString()))
			.toList();
		return new CreateGuestOrderCommand(
				command.idempotencyKey(),
				name,
				phone,
				email,
				notes,
				command.paymentMethod(),
				items);
	}

	private OrderItemCommand validateItem(
			OrderItemCommand item,
			Set<UUID> variants) {
		if (item == null || item.variantId() == null) {
			throw new InvalidGuestOrderException(
				"Cada producto debe indicar una variante válida.");
		}
		if (!variants.add(item.variantId())) {
			throw new InvalidGuestOrderException(
				"No se puede repetir una variante en el pedido.");
		}
		BigDecimal quantity;
		try {
			quantity = Objects.requireNonNull(item.quantity()).setScale(
				3,
				RoundingMode.UNNECESSARY);
		}
		catch (NullPointerException | ArithmeticException exception) {
			throw new InvalidGuestOrderException(
				"La cantidad debe ser un número entero.");
		}
		if (quantity.stripTrailingZeros().scale() > 0
				|| quantity.compareTo(BigDecimal.ONE) < 0
				|| quantity.compareTo(new BigDecimal("99")) > 0) {
			throw new InvalidGuestOrderException(
				"Cada cantidad debe ser un entero entre 1 y 99.");
		}
		return new OrderItemCommand(item.variantId(), quantity);
	}

	private String requiredText(String value, int maximum, String label) {
		String normalized = optionalText(value, maximum, label);
		if (normalized == null) {
			throw new InvalidGuestOrderException(label + " es obligatorio.");
		}
		return normalized;
	}

	private String optionalText(String value, int maximum, String label) {
		if (value == null || value.isBlank()) {
			return null;
		}
		if (value.chars().anyMatch(Character::isISOControl)) {
			throw new InvalidGuestOrderException(
				label + " contiene caracteres no permitidos.");
		}
		String normalized = value.trim().replaceAll("\\s+", " ");
		if (normalized.length() > maximum) {
			throw new InvalidGuestOrderException(
				label + " supera el máximo de " + maximum + " caracteres.");
		}
		return normalized;
	}

	private boolean isVersionFour(UUID value) {
		return value != null && value.version() == 4;
	}

	private byte[] fingerprint(CreateGuestOrderCommand command) {
			StringBuilder canonical = new StringBuilder()
					.append(command.customerName()).append('\n')
					.append(command.customerPhone()).append('\n')
					.append(Objects.toString(command.customerEmail(), "")).append('\n')
					.append(Objects.toString(command.notes(), "")).append('\n')
					.append(command.paymentMethod().name());

			command.items().forEach(item -> canonical
					.append('\n')
					.append(item.variantId())
					.append(':')
					.append(item.quantity().toPlainString()));

			return sha256(canonical.toString());
	}

	private String deriveLookupToken(UUID idempotencyKey) {
		return Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString(sha256(TOKEN_NAMESPACE + idempotencyKey));
	}

	private byte[] sha256(String value) {
		try {
			return MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 no está disponible.", exception);
		}
	}
}
