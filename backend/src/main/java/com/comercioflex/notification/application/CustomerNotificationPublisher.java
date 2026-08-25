package com.comercioflex.notification.application;

import java.time.Instant;

import com.comercioflex.order.application.AdminOrderDetail;
import com.comercioflex.payment.application.BankTransferPayment;

public interface CustomerNotificationPublisher {
	void orderConfirmed(AdminOrderDetail order, Instant confirmedAt);
	void bankTransferReceiptRejected(BankTransferPayment payment, Instant rejectedAt);

	static CustomerNotificationPublisher noop() {
		return new CustomerNotificationPublisher() {
			@Override public void orderConfirmed(AdminOrderDetail order, Instant confirmedAt) { }
			@Override public void bankTransferReceiptRejected(
					BankTransferPayment payment, Instant rejectedAt) { }
		};
	}
}
