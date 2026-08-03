package com.comercioflex.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.identity.application.PlatformPrincipal;
import com.comercioflex.identity.application.UserCredentials;
import com.comercioflex.identity.domain.UserStatus;
import com.comercioflex.payment.domain.PaymentEnvironment;

class PaymentWebhookOperationsServiceTests {

	private static final Instant NOW = Instant.parse("2026-08-01T20:00:00Z");
	private final CheckoutControlRepository repository = mock(CheckoutControlRepository.class);
	private final PaymentWebhookMetrics metrics = mock(PaymentWebhookMetrics.class);
	private PaymentWebhookOperationsService service;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		TransactionTemplate transactions = mock(TransactionTemplate.class);
		when(transactions.execute(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
			TransactionCallback<Object> callback = invocation.getArgument(0);
			return callback.doInTransaction(mock(TransactionStatus.class));
		});
		service = new PaymentWebhookOperationsService(
			repository, transactions, metrics, oauthProperties(),
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void schedulesDeadEventAndRecordsMetric() {
		UUID eventId = UUID.randomUUID();
		PlatformPrincipal actor = actor();
		when(repository.retryWebhook(
			7L, PaymentEnvironment.TEST, eventId, actor.id(), actor.publicId(), NOW))
			.thenReturn(new WebhookRetryOutcome(WebhookRetryResult.SCHEDULED, NOW));

		var response = service.retry(7L, eventId, actor);

		assertThat(response.eventId()).isEqualTo(eventId);
		assertThat(response.status()).isEqualTo("RETRY_SCHEDULED");
		assertThat(response.scheduledAt()).isEqualTo(NOW);
		verify(metrics).manuallyScheduled();
	}

	@Test
	void repeatedRequestIsIdempotentAndDoesNotDuplicateMetric() {
		UUID eventId = UUID.randomUUID();
		PlatformPrincipal actor = actor();
		Instant persistedSchedule = NOW.minusSeconds(10);
		when(repository.retryWebhook(
			7L, PaymentEnvironment.TEST, eventId, actor.id(), actor.publicId(), NOW))
			.thenReturn(new WebhookRetryOutcome(
				WebhookRetryResult.ALREADY_SCHEDULED, persistedSchedule));

		var response = service.retry(7L, eventId, actor);
		assertThat(response.status()).isEqualTo("RETRY_SCHEDULED");
		assertThat(response.scheduledAt()).isEqualTo(persistedSchedule);
		verifyNoInteractions(metrics);
	}

	@Test
	void processedEventCannotBeRequeued() {
		UUID eventId = UUID.randomUUID();
		PlatformPrincipal actor = actor();
		when(repository.retryWebhook(
			7L, PaymentEnvironment.TEST, eventId, actor.id(), actor.publicId(), NOW))
			.thenReturn(new WebhookRetryOutcome(WebhookRetryResult.PROCESSED, NOW));

		assertThatThrownBy(() -> service.retry(7L, eventId, actor))
			.isInstanceOf(CheckoutPaymentException.class)
			.extracting(exception -> ((CheckoutPaymentException) exception).code())
			.isEqualTo("WEBHOOK_ALREADY_PROCESSED");
	}

	private PlatformPrincipal actor() {
		return new PlatformPrincipal(new UserCredentials(
			11L, UUID.randomUUID(), "owner@example.com", "Owner", "hash", UserStatus.ACTIVE));
	}

	private PaymentOAuthProperties oauthProperties() {
		return new PaymentOAuthProperties(
			true, PaymentEnvironment.TEST, "client", "secret",
			java.net.URI.create("https://example.test/oauth"), null, null, null,
			java.net.URI.create("https://shop.example.test"),
			java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(2),
			"v1", "key");
	}
}
