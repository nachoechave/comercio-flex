package com.comercioflex.inventory.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.inventory.domain.AdjustmentDirection;
import com.comercioflex.inventory.domain.InventoryItem;
import com.comercioflex.inventory.domain.InventoryMovement;
import com.comercioflex.inventory.domain.InventoryReason;

@Service
public class InventoryService {

	private static final BigDecimal MAX_QUANTITY =
		new BigDecimal("999999999999.999");

	private final InventoryRepository repository;
	private final TransactionTemplate transactionTemplate;

	public InventoryService(
			InventoryRepository repository,
			@Qualifier("tenantTransactionTemplate") TransactionTemplate transactionTemplate) {
		this.repository = repository;
		this.transactionTemplate = transactionTemplate;
	}

	public InventoryPage findPage(InventorySearch search) {
		String query = normalizeQuery(search.query());
		return transactionTemplate.execute(ignored -> repository.findPage(
			new InventorySearch(
				search.page(),
				search.size(),
				query,
				search.availability())));
	}

	public InventoryItem findItem(UUID variantId) {
		return transactionTemplate.execute(ignored -> requireItem(variantId));
	}

	public MovementPage findMovements(UUID variantId, int page, int size) {
		return transactionTemplate.execute(ignored -> {
			requireItem(variantId);
			return repository.findMovements(variantId, page, size);
		});
	}

	public AdjustmentResult adjust(AdjustmentCommand rawCommand) {
		AdjustmentCommand command = validate(rawCommand);
		return transactionTemplate.execute(ignored -> {
			LockedInventoryVariant variant = repository.lockVariant(command.variantId())
				.orElseThrow(InventoryNotFoundException::new);
			var replay = repository.findAdjustment(command.idempotencyKey());
			if (replay.isPresent()) {
				requireSamePayload(replay.get(), variant.internalId(), command);
				return new AdjustmentResult(
					requireItem(command.variantId()),
					replay.get().movement(),
					true);
			}

			repository.ensureBalance(variant.internalId());
			BigDecimal before = repository.findBalanceForUpdate(variant.internalId());
			BigDecimal delta = command.direction() == AdjustmentDirection.INCREASE
				? command.quantity()
				: command.quantity().negate();
			BigDecimal after = canonical(before.add(delta));
			if (after.signum() < 0) {
				throw new InsufficientStockException();
			}
			if (after.compareTo(MAX_QUANTITY) > 0) {
				throw new InventoryCapacityExceededException();
			}
			long balanceVersion =
				repository.updateBalance(variant.internalId(), after);
			repository.insertMovement(
				UUID.randomUUID(),
				variant.internalId(),
				command,
				delta,
				before,
				after,
				balanceVersion);
			InventoryMovement movement = repository.findMovement(
					variant.internalId(),
					command.idempotencyKey())
				.orElseThrow(() -> new IllegalStateException(
					"El movimiento insertado no pudo recuperarse."));
			return new AdjustmentResult(
				requireItem(command.variantId()),
				movement,
				false);
		});
	}

	private AdjustmentCommand validate(AdjustmentCommand command) {
		BigDecimal quantity;
		try {
			quantity = canonical(command.quantity());
		}
		catch (ArithmeticException exception) {
			throw new InvalidInventoryAdjustmentException(
				"La cantidad admite como mÃ¡ximo tres decimales.");
		}
		if (quantity.signum() <= 0) {
			throw new InvalidInventoryAdjustmentException(
				"La cantidad debe ser mayor que cero.");
		}
		if (quantity.compareTo(MAX_QUANTITY) > 0) {
			throw new InvalidInventoryAdjustmentException(
				"La cantidad excede el mÃ¡ximo permitido.");
		}
		if (quantity.stripTrailingZeros().scale() > 0) {
			throw new InvalidInventoryAdjustmentException(
				"Durante el piloto los ajustes manuales requieren unidades enteras.");
		}
		String note = normalizeNote(command.note());
		if (command.reason() == InventoryReason.OTHER && note == null) {
			throw new InvalidInventoryAdjustmentException(
				"El motivo OTHER requiere una observaciÃ³n.");
		}
		return new AdjustmentCommand(
			command.variantId(),
			command.idempotencyKey(),
			command.direction(),
			quantity,
			command.reason(),
			note,
			command.actor());
	}

	private void requireSamePayload(
			StoredAdjustment stored,
			long variantInternalId,
			AdjustmentCommand requested) {
		if (stored.variantInternalId() != variantInternalId
				|| stored.direction() != requested.direction()
				|| stored.quantity().compareTo(requested.quantity()) != 0
				|| stored.reason() != requested.reason()
				|| !Objects.equals(stored.note(), requested.note())) {
			throw new IdempotencyConflictException();
		}
	}

	private InventoryItem requireItem(UUID variantId) {
		return repository.findItem(variantId)
			.orElseThrow(InventoryNotFoundException::new);
	}

	private String normalizeQuery(String query) {
		if (query == null || query.isBlank()) {
			return null;
		}
		String normalized = query.trim().replaceAll("\\s+", " ");
		if (normalized.length() > 100) {
			throw new InvalidInventoryAdjustmentException(
				"La bÃºsqueda no puede superar 100 caracteres.");
		}
		return normalized;
	}

	private String normalizeNote(String note) {
		if (note == null || note.isBlank()) {
			return null;
		}
		if (note.chars().anyMatch(Character::isISOControl)) {
			throw new InvalidInventoryAdjustmentException(
				"La observación contiene caracteres no permitidos.");
		}
		String normalized = note.trim().replaceAll("\\s+", " ");
		if (normalized.length() > 500) {
			throw new InvalidInventoryAdjustmentException(
				"La observaciÃ³n no puede superar 500 caracteres.");
		}
		return normalized;
	}

	private BigDecimal canonical(BigDecimal value) {
		if (value == null) {
			throw new InvalidInventoryAdjustmentException(
				"La cantidad es obligatoria.");
		}
		return value.setScale(3, RoundingMode.UNNECESSARY);
	}
}
