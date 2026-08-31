package com.comercioflex.order.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.comercioflex.order.domain.OrderStatus;

@Component
class OrderTransitionExecutor {

	private static final UUID PAYMENT_SYSTEM_ACTOR_ID = UUID.fromString(
		"00000000-0000-4000-8000-000000000001");

	private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
		OrderStatus.PENDING_CONFIRMATION,
		EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.REJECTED),
		OrderStatus.CONFIRMED,
		EnumSet.of(OrderStatus.READY_FOR_PICKUP, OrderStatus.CANCELLED),
		OrderStatus.READY_FOR_PICKUP,
		EnumSet.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED));

	private final AdminOrderRepository repository;
	private final OrderPaymentPolicy paymentPolicy;
	private final Clock clock;

	@Autowired
	OrderTransitionExecutor(
			AdminOrderRepository repository,
			OrderPaymentPolicy paymentPolicy) {
		this(repository, paymentPolicy, Clock.systemUTC());
	}

	OrderTransitionExecutor(AdminOrderRepository repository, Clock clock) {
		this(repository, OrderPaymentPolicy.allowAll(), clock);
	}

	OrderTransitionExecutor(
			AdminOrderRepository repository,
			OrderPaymentPolicy paymentPolicy,
			Clock clock) {
		this.repository = repository;
		this.paymentPolicy = paymentPolicy;
		this.clock = clock;
	}

	OrderTransitionExecution execute(OrderTransitionCommand command) {
		return execute(command, false);
	}

	OrderTransitionExecution executePaid(OrderTransitionCommand command) {
		return execute(command, true);
	}

	private OrderTransitionExecution execute(
			OrderTransitionCommand command, boolean verifiedPayment) {
		LockedAdminOrder order = repository.lockOrder(command.orderId())
			.orElseThrow(AdminOrderNotFoundException::new);
		var replay = repository.findTransition(command.idempotencyKey());
		if (replay.isPresent()) {
			AdminOrderService.requireSameTransition(replay.get(), command);
			return OrderTransitionExecution.completed(repository.findDetail(command.orderId())
				.orElseThrow(AdminOrderNotFoundException::new));
		}
		if (!verifiedPayment
				&& order.status() == OrderStatus.PENDING_CONFIRMATION
				&& !order.reservationExpiresAt().isAfter(clock.instant())) {
			repository.expireOrder(order.internalId());
			return OrderTransitionExecution.expiration();
		}
		if (!ALLOWED.getOrDefault(order.status(), Set.of())
				.contains(command.targetStatus())) {
			throw new InvalidOrderTransitionException(
				"La transición solicitada no está permitida.");
		}
		if (command.targetStatus() == OrderStatus.CONFIRMED
				&& command.actorId() != null
				&& paymentPolicy.blocksManualConfirmation(order.internalId())) {
			throw new InvalidOrderTransitionException(
				"El pedido tiene un pago en proceso y no puede confirmarse manualmente.");
		}
		if (command.targetStatus() == OrderStatus.CANCELLED
				&& paymentPolicy.hasAppliedPayment(order.internalId())) {
			throw new InvalidOrderTransitionException(
				"El pedido cobrado no puede cancelarse sin un reembolso.");
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
			effectiveActorId(command),
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
				effectiveActorId(command),
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

	private UUID effectiveActorId(OrderTransitionCommand command) {
		return command.actorId() == null ? PAYMENT_SYSTEM_ACTOR_ID : command.actorId();
	}
}
