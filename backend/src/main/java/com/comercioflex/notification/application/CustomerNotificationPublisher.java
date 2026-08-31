package com.comercioflex.notification.application;

import java.time.Instant;
import java.util.UUID;

import com.comercioflex.order.application.AdminOrderDetail;
import com.comercioflex.payment.application.BankTransferPayment;

public interface CustomerNotificationPublisher {
	void orderConfirmed(AdminOrderDetail order, Instant confirmedAt, String paymentMethod);
	default void orderConfirmed(AdminOrderDetail order, Instant confirmedAt) {
		orderConfirmed(order, confirmedAt, "Pago registrado");
	}
	void mercadoPagoPaymentRejected(
		AdminOrderDetail order, UUID paymentIntentId, Instant rejectedAt);
	void bankTransferReceiptRejected(BankTransferPayment payment, Instant rejectedAt);

	static CustomerNotificationPublisher noop() {
		return new CustomerNotificationPublisher() {
			@Override public void orderConfirmed(
					AdminOrderDetail order, Instant confirmedAt, String paymentMethod) { }
			@Override public void mercadoPagoPaymentRejected(
					AdminOrderDetail order, UUID paymentIntentId, Instant rejectedAt) { }
			@Override public void bankTransferReceiptRejected(
					BankTransferPayment payment, Instant rejectedAt) { }
		};
	}
}
