package com.comercioflex.notification.infrastructure;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.comercioflex.notification.application.CustomerNotificationPublisher;
import com.comercioflex.notification.application.NotificationOutboxRepository;
import com.comercioflex.notification.application.TransactionalEmail;
import com.comercioflex.order.application.AdminOrderDetail;
import com.comercioflex.order.application.AdminOrderNotFoundException;
import com.comercioflex.order.application.AdminOrderRepository;
import com.comercioflex.payment.application.BankTransferPayment;
import com.comercioflex.tenant.application.StoreSettingsRepository;

@Service
class OutboxCustomerNotificationService implements CustomerNotificationPublisher {
	private final NotificationOutboxRepository outbox;
	private final AdminOrderRepository orders;
	private final StoreSettingsRepository stores;
	private final EmailTemplateRenderer templates;

	OutboxCustomerNotificationService(NotificationOutboxRepository outbox,
			AdminOrderRepository orders, StoreSettingsRepository stores,
			EmailTemplateRenderer templates) {
		this.outbox = outbox;
		this.orders = orders;
		this.stores = stores;
		this.templates = templates;
	}

	@Override
	public void orderConfirmed(AdminOrderDetail order, Instant confirmedAt) {
		if (!hasRecipient(order)) return;
		var store = stores.findCurrent().orElseThrow();
		RenderedEmail rendered = templates.orderConfirmed(order, store, confirmedAt);
		String eventKey = "ORDER_CONFIRMED:" + order.id();
		outbox.enqueue(eventKey, "ORDER_CONFIRMED", order.id(), null,
			new TransactionalEmail(order.customerEmail(), rendered.subject(),
				rendered.html(), rendered.text()));
	}

	@Override
	public void bankTransferReceiptRejected(BankTransferPayment payment, Instant rejectedAt) {
		AdminOrderDetail order = orders.findDetail(payment.orderId())
			.orElseThrow(AdminOrderNotFoundException::new);
		if (!hasRecipient(order)) return;
		var store = stores.findCurrent().orElseThrow();
		RenderedEmail rendered = templates.receiptRejected(order, payment, store);
		String eventKey = "BANK_TRANSFER_RECEIPT_REJECTED:" + payment.id();
		outbox.enqueue(eventKey, "BANK_TRANSFER_RECEIPT_REJECTED", order.id(),
			payment.internalId(), new TransactionalEmail(order.customerEmail(), rendered.subject(),
				rendered.html(), rendered.text()));
	}

	private boolean hasRecipient(AdminOrderDetail order) {
		return order.customerEmail() != null && !order.customerEmail().isBlank();
	}
}
