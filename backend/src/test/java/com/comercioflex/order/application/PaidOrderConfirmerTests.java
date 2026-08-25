package com.comercioflex.order.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.comercioflex.notification.application.CustomerNotificationPublisher;

class PaidOrderConfirmerTests {
	private static final Instant NOW = Instant.parse("2026-08-25T15:00:00Z");

	@AfterEach
	void cleanupTransactionFlag() {
		TransactionSynchronizationManager.setActualTransactionActive(false);
	}

	@Test
	void everySuccessfulPaidConfirmationPublishesTheCentralCustomerEvent() {
		OrderTransitionExecutor executor = mock(OrderTransitionExecutor.class);
		CustomerNotificationPublisher notifications = mock(CustomerNotificationPublisher.class);
		AdminOrderDetail detail = mock(AdminOrderDetail.class);
		when(executor.execute(any())).thenReturn(OrderTransitionExecution.completed(detail));
		TransactionSynchronizationManager.setActualTransactionActive(true);

		new PaidOrderConfirmer(executor, notifications, Clock.fixed(NOW, ZoneOffset.UTC))
			.confirmWithinCurrentTransaction(UUID.randomUUID(), UUID.randomUUID());

		verify(notifications).orderConfirmed(detail, NOW);
	}
}
