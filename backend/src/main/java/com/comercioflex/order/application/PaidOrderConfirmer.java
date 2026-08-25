package com.comercioflex.order.application;

import java.time.Clock;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.comercioflex.order.domain.OrderStatus;
import com.comercioflex.notification.application.CustomerNotificationPublisher;

@Component
public class PaidOrderConfirmer {

	private static final String SYSTEM_ACTOR = "Sistema de pagos";

	private final OrderTransitionExecutor executor;
	private final CustomerNotificationPublisher notifications;
	private final Clock clock;

	@Autowired
	PaidOrderConfirmer(OrderTransitionExecutor executor,
			CustomerNotificationPublisher notifications) {
		this(executor, notifications, Clock.systemUTC());
	}

	PaidOrderConfirmer(OrderTransitionExecutor executor,
			CustomerNotificationPublisher notifications, Clock clock) {
		this.executor = executor;
		this.notifications = notifications;
		this.clock = clock;
	}

	public OrderTransitionExecution confirmWithinCurrentTransaction(
			UUID orderId,
			UUID idempotencyKey) {
		if (!TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException(
				"La confirmación por pago requiere una transacción tenant activa.");
		}
		OrderTransitionExecution result = executor.execute(new OrderTransitionCommand(
			orderId,
			idempotencyKey,
			OrderStatus.CONFIRMED,
			"Pago aprobado y verificado",
			null,
			SYSTEM_ACTOR));
		if (!result.expired()) notifications.orderConfirmed(result.detail(), clock.instant());
		return result;
	}
}
