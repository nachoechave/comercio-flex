package com.comercioflex.inventory.application;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import com.comercioflex.inventory.domain.InventoryItem;
import com.comercioflex.inventory.domain.InventoryMovement;

public interface InventoryRepository {

	InventoryPage findPage(InventorySearch search);

	Optional<InventoryItem> findItem(UUID variantId);

	Optional<LockedInventoryVariant> lockVariant(UUID variantId);

	void ensureBalance(long variantInternalId);

	BigDecimal findBalanceForUpdate(long variantInternalId);

	long updateBalance(long variantInternalId, BigDecimal quantity);

	Optional<StoredAdjustment> findAdjustment(UUID idempotencyKey);

	void insertMovement(
		UUID movementId,
		long variantInternalId,
		AdjustmentCommand command,
		BigDecimal delta,
		BigDecimal before,
		BigDecimal after,
		long balanceVersion);

	Optional<InventoryMovement> findMovement(
		long variantInternalId,
		UUID idempotencyKey);

	MovementPage findMovements(UUID variantId, int page, int size);
}
