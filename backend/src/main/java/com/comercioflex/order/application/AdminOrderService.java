package com.comercioflex.order.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.order.domain.OrderStatus;

@Service
public class AdminOrderService {

	private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
		OrderStatus.PENDING_CONFIRMATION,
		EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.REJECTED),
		OrderStatus.CONFIRMED,
		EnumSet.of(OrderStatus.READY_FOR_PICKUP, OrderStatus.CANCELLED),
		OrderStatus.READY_FOR_PICKUP,
		EnumSet.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED));

	private final AdminOrderRepository repository;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	@Autowired
	public AdminOrderService(
			AdminOrderRepository repository,
			@Qualifier("tenantTransactionTemplate") TransactionTemplate transactionTemplate) {
		this(repository, transactionTemplate, Clock.systemUTC());
	}

	AdminOrderService(
			AdminOrderRepository repository,
			TransactionTemplate transactionTemplate,
			Clock clock) {
		this.repository = repository;
		this.transactionTemplate = transactionTemplate;
		this.clock = clock;
	}

	public AdminOrderPage findPage(AdminOrderSearch rawSearch) {
		AdminOrderSearch search = validate(rawSearch);
		return transactionTemplate.execute(ignored -> {
			repository.expirePendingOrders();
			return repository.findPage(search);
		});
	}

	public AdminOrderDetail find(UUID orderId) {
		return transactionTemplate.execute(ignored -> {
			repository.expirePendingOrders();
			return repository.findDetail(orderId)
				.orElseThrow(AdminOrderNotFoundException::new);
		});
	}

	public AdminOrderDetail transition(OrderTransitionCommand rawCommand) {
		OrderTransitionCommand command = validate(rawCommand);
		TransitionOutcome outcome;
		try {
			outcome = transactionTemplate.execute(ignored -> transitionInside(command));
		}
		catch (DuplicateKeyException exception) {
			return transactionTemplate.execute(ignored -> replayAfterDuplicate(command, exception));
		}
		if (outcome == null || outcome.expired()) {
			throw new InvalidOrderTransitionException(
				"La reserva del pedido ya venció.");
		}
		return outcome.detail();
	}

	private TransitionOutcome transitionInside(OrderTransitionCommand command) {
		LockedAdminOrder order = repository.lockOrder(command.orderId())
			.orElseThrow(AdminOrderNotFoundException::new);
		var replay = repository.findTransition(command.idempotencyKey());
		if (replay.isPresent()) {
			requireSameTransition(replay.get(), command);
			return TransitionOutcome.completed(repository.findDetail(command.orderId())
				.orElseThrow(AdminOrderNotFoundException::new));
		}
		if (order.status() == OrderStatus.PENDING_CONFIRMATION
				&& !order.reservationExpiresAt().isAfter(clock.instant())) {
			repository.expireOrder(order.internalId());
			return TransitionOutcome.expiration();
		}
		if (!ALLOWED.getOrDefault(order.status(), Set.of())
				.contains(command.targetStatus())) {
			throw new InvalidOrderTransitionException(
				"La transición solicitada no está permitida.");
		}

		if (command.targetStatus() == OrderStatus.CONFIRMED) {
			int expected = moveStock(order, command, false);
			requireReservations(expected, repository.updateReservations(
				order.internalId(), "ACTIVE", "CONSUMED"));
		}
		else if (command.targetStatus() == OrderStatus.REJECTED) {
			int expected = repository.findStockLinesForUpdate(order.internalId()).size();
			requireReservations(expected, repository.updateReservations(
				order.internalId(), "ACTIVE", "RELEASED"));
		}
		else if (command.targetStatus() == OrderStatus.CANCELLED) {
			int expected = moveStock(order, command, true);
			requireReservations(expected, repository.updateReservations(
				order.internalId(), "CONSUMED", "RELEASED"));
		}

		repository.updateOrderStatus(
			order.internalId(),
			order.version(),
			command.targetStatus());
		repository.insertHistory(
			order.internalId(),
			command.idempotencyKey(),
			order.status(),
			command.targetStatus(),
			command.note(),
			command.actorId(),
			command.actorDisplayName());
		return TransitionOutcome.completed(repository.findDetail(command.orderId())
			.orElseThrow(AdminOrderNotFoundException::new));
	}

	private AdminOrderDetail replayAfterDuplicate(
			OrderTransitionCommand command,
			DuplicateKeyException original) {
		StoredOrderTransition stored = repository.findTransition(command.idempotencyKey())
			.orElseThrow(() -> original);
		requireSameTransition(stored, command);
		return repository.findDetail(command.orderId())
			.orElseThrow(AdminOrderNotFoundException::new);
	}

	private int moveStock(
			LockedAdminOrder order,
			OrderTransitionCommand command,
			boolean restoring) {
		var lines = repository.findStockLinesForUpdate(order.internalId());
		for (OrderStockLine line : lines) {
			BigDecimal before = repository.findBalanceForUpdate(line.variantInternalId());
			BigDecimal after = restoring
				? before.add(line.quantity())
				: before.subtract(line.quantity());
			if (after.signum() < 0) {
				throw new InvalidOrderTransitionException(
					"No hay stock físico suficiente para confirmar el pedido.");
			}
			long balanceVersion = repository.updateBalance(line.variantInternalId(), after);
			UUID movementId = movementId(command.idempotencyKey(), line.variantId());
			repository.insertInventoryMovement(
				movementId,
				order.internalId(),
				line,
				before,
				after,
				balanceVersion,
				restoring,
				movementId,
				command.actorId(),
				command.actorDisplayName());
		}
		return lines.size();
	}

	private void requireReservations(int expected, int changed) {
		if (expected == 0 || changed != expected) {
			throw new InvalidOrderTransitionException(
				"Las reservas del pedido ya no están disponibles.");
		}
	}

	private UUID movementId(UUID key, UUID variantId) {
		return UUID.nameUUIDFromBytes(
			(key + ":" + variantId).getBytes(StandardCharsets.UTF_8));
	}

	private AdminOrderSearch validate(AdminOrderSearch search) {
		if (search == null || search.page() < 0 || search.size() < 1 || search.size() > 100) {
			throw new InvalidOrderTransitionException("Paginación inválida.");
		}
		String query = normalize(search.query(), 30);
		return new AdminOrderSearch(search.page(), search.size(), query, search.status());
	}

	private OrderTransitionCommand validate(OrderTransitionCommand command) {
		if (command == null
				|| command.orderId() == null
				|| command.idempotencyKey() == null
				|| command.targetStatus() == null
				|| command.actorId() == null
				|| command.actorDisplayName() == null) {
			throw new InvalidOrderTransitionException("La transición está incompleta.");
		}
		return new OrderTransitionCommand(
			command.orderId(),
			command.idempotencyKey(),
			command.targetStatus(),
			normalize(command.note(), 500),
			command.actorId(),
			command.actorDisplayName());
	}

	private void requireSameTransition(
			StoredOrderTransition stored,
			OrderTransitionCommand requested) {
		if (!stored.orderId().equals(requested.orderId())
				|| stored.targetStatus() != requested.targetStatus()
				|| !Objects.equals(stored.note(), requested.note())) {
			throw new OrderTransitionIdempotencyConflictException();
		}
	}

	private String normalize(String value, int maxLength) {
		if (value == null || value.isBlank()) return null;
		String normalized = value.trim().replaceAll("\\s+", " ");
		if (normalized.length() > maxLength
				|| normalized.chars().anyMatch(Character::isISOControl)) {
			throw new InvalidOrderTransitionException("El texto ingresado no es válido.");
		}
		return normalized;
	}

	private record TransitionOutcome(AdminOrderDetail detail, boolean expired) {

		private static TransitionOutcome completed(AdminOrderDetail detail) {
			return new TransitionOutcome(detail, false);
		}

		private static TransitionOutcome expiration() {
			return new TransitionOutcome(null, true);
		}
	}
}
