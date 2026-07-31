package com.comercioflex.order.application;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.comercioflex.order.domain.OrderStatus;

@Component
public class PaidOrderConfirmer {

	private static final String SYSTEM_ACTOR = "Sistema de pagos";

	private final OrderTransitionExecutor executor;

	PaidOrderConfirmer(OrderTransitionExecutor executor) {
		this.executor = executor;
	}

	public OrderTransitionExecution confirmWithinCurrentTransaction(
			UUID orderId,
			UUID idempotencyKey) {
		if (!TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException(
				"La confirmación por pago requiere una transacción tenant activa.");
		}
		return executor.execute(new OrderTransitionCommand(
			orderId,
			idempotencyKey,
			OrderStatus.CONFIRMED,
			"Pago aprobado y verificado",
			null,
			SYSTEM_ACTOR));
	}
}
