package com.comercioflex.order.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.comercioflex.order.domain.OrderStatus;

@Component
class OrderTransitionExecutor {

	private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
		OrderStatus.PENDING_CONFIRMATION,
		EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.REJECTED),
		OrderStatus.CONFIRMED,
		EnumSet.of(OrderStatus.READY_FOR_PICKUP, OrderStatus.CANCELLED),
		OrderStatus.READY_FOR_PICKUP,
		EnumSet.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED));

	private final AdminOrderRepository repository;
	private final Clock clock;

	OrderTransitionExecutor(AdminOrderRepository repository) {
		this(repository, Clock.systemUTC());
	}

	OrderTransitionExecutor(AdminOrderRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	OrderTransitionExecution execute(OrderTransitionCommand command) {
		LockedAdminOrder order = repository.lockOrder(command.orderId())
			.orElseThrow(AdminOrderNotFoundException::new);
		var replay = repository.findTransition(command.idempotencyKey());
		if (replay.isPresent()) {
			AdminOrderService.requireSameTransition(replay.get(), command);
			return OrderTransitionExecution.completed(repository.findDetail(command.orderId())
				.orElseThrow(AdminOrderNotFoundException::new));
		}
		if (order.status() == OrderStatus.PENDING_CONFIRMATION
				&& !order.reservationExpiresAt().isAfter(clock.instant())) {
			repository.expireOrder(order.internalId());
			return OrderTransitionExecution.expiration();
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
		return OrderTransitionExecution.completed(repository.findDetail(command.orderId())
			.orElseThrow(AdminOrderNotFoundException::new));
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
}
