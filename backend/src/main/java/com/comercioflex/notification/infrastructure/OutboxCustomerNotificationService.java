package com.comercioflex.notification.infrastructure;

import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.comercioflex.notification.application.CustomerNotificationPublisher;
import com.comercioflex.notification.application.NotificationOutboxRepository;
import com.comercioflex.notification.application.NotificationQueuedEvent;
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
	private final ApplicationEventPublisher events;

	OutboxCustomerNotificationService(NotificationOutboxRepository outbox,
			AdminOrderRepository orders, StoreSettingsRepository stores,
			EmailTemplateRenderer templates, ApplicationEventPublisher events) {
		this.outbox = outbox;
		this.orders = orders;
		this.stores = stores;
		this.templates = templates;
		this.events = events;
	}

	@Override
	public void orderConfirmed(AdminOrderDetail order, Instant confirmedAt) {
		if (!hasRecipient(order)) return;
		var store = stores.findCurrent().orElseThrow();
		RenderedEmail rendered = templates.orderConfirmed(order, store, confirmedAt);
		String eventKey = "ORDER_CONFIRMED:" + order.id();
		if (outbox.enqueue(eventKey, "ORDER_CONFIRMED", order.id(), null,
				new TransactionalEmail(order.customerEmail(), rendered.subject(),
					rendered.html(), rendered.text()))) {
			events.publishEvent(new NotificationQueuedEvent(eventKey));
		}
	}

	@Override
	public void bankTransferReceiptRejected(BankTransferPayment payment, Instant rejectedAt) {
		AdminOrderDetail order = orders.findDetail(payment.orderId())
			.orElseThrow(AdminOrderNotFoundException::new);
		if (!hasRecipient(order)) return;
		var store = stores.findCurrent().orElseThrow();
		RenderedEmail rendered = templates.receiptRejected(order, payment, store);
		String eventKey = "BANK_TRANSFER_RECEIPT_REJECTED:" + payment.id();
		if (outbox.enqueue(eventKey, "BANK_TRANSFER_RECEIPT_REJECTED", order.id(),
				payment.internalId(), new TransactionalEmail(order.customerEmail(), rendered.subject(),
					rendered.html(), rendered.text()))) {
			events.publishEvent(new NotificationQueuedEvent(eventKey));
		}
	}

	private boolean hasRecipient(AdminOrderDetail order) {
		return order.customerEmail() != null && !order.customerEmail().isBlank();
	}
}
