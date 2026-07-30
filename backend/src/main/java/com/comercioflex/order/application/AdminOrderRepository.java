package com.comercioflex.order.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.comercioflex.order.domain.OrderStatus;

public interface AdminOrderRepository {

	AdminOrderPage findPage(AdminOrderSearch search);

	void expirePendingOrders();

	Optional<AdminOrderDetail> findDetail(UUID orderId);

	Optional<LockedAdminOrder> lockOrder(UUID orderId);

	Optional<StoredOrderTransition> findTransition(UUID idempotencyKey);

	List<OrderStockLine> findStockLinesForUpdate(long orderInternalId);

	BigDecimal findBalanceForUpdate(long variantInternalId);

	long updateBalance(long variantInternalId, BigDecimal quantity);

	void insertInventoryMovement(
		UUID movementId,
		long orderInternalId,
		OrderStockLine line,
		BigDecimal before,
		BigDecimal after,
		long balanceVersion,
		boolean restoring,
		UUID movementIdempotencyKey,
		UUID actorId,
		String actorName);

	int updateReservations(long orderInternalId, String fromStatus, String toStatus);

	void updateOrderStatus(long orderInternalId, long version, OrderStatus targetStatus);

	void insertHistory(
		long orderInternalId,
		UUID idempotencyKey,
		OrderStatus previousStatus,
		OrderStatus newStatus,
		String note,
		UUID actorId,
		String actorName);

	void expireOrder(long orderInternalId);
}
