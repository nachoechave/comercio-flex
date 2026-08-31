package com.comercioflex.payment.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.comercioflex.notification.application.CustomerNotificationPublisher;
import com.comercioflex.order.application.AdminOrderNotFoundException;
import com.comercioflex.order.application.AdminOrderRepository;

@Component
public class RejectedPaymentNotifier {

	private final AdminOrderRepository orders;
	private final CustomerNotificationPublisher notifications;

	public RejectedPaymentNotifier(AdminOrderRepository orders,
			CustomerNotificationPublisher notifications) {
		this.orders = orders;
		this.notifications = notifications;
	}

	public void notifyWithinCurrentTransaction(
			UUID orderId, UUID paymentIntentId, Instant rejectedAt) {
		if (!TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException(
				"La notificación de pago rechazado requiere una transacción tenant activa.");
		}
		var order = orders.findDetail(orderId).orElseThrow(AdminOrderNotFoundException::new);
		notifications.mercadoPagoPaymentRejected(order, paymentIntentId, rejectedAt);
	}
}
