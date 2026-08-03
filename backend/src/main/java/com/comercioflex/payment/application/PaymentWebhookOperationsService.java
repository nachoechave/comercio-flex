package com.comercioflex.payment.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.identity.application.PlatformPrincipal;

@Service
public class PaymentWebhookOperationsService {

	private static final int LIST_LIMIT = 100;

	private final CheckoutControlRepository repository;
	private final TransactionTemplate transactions;
	private final PaymentWebhookMetrics metrics;
	private final PaymentOAuthProperties oauthProperties;
	private final Clock clock;

	@Autowired
	public PaymentWebhookOperationsService(
			CheckoutControlRepository repository,
			@Qualifier("controlTransactionTemplate") TransactionTemplate transactions,
			PaymentWebhookMetrics metrics,
			PaymentOAuthProperties oauthProperties) {
		this(repository, transactions, metrics, oauthProperties, Clock.systemUTC());
	}

	PaymentWebhookOperationsService(
			CheckoutControlRepository repository,
			TransactionTemplate transactions,
			PaymentWebhookMetrics metrics,
			PaymentOAuthProperties oauthProperties,
			Clock clock) {
		this.repository = repository;
		this.transactions = transactions;
		this.metrics = metrics;
		this.oauthProperties = oauthProperties;
		this.clock = clock;
	}

	public List<FailedWebhookEvent> listFailed(long tenantId) {
		return repository.findDeadWebhooks(
			tenantId, oauthProperties.environment(), LIST_LIMIT);
	}

	public WebhookRetryScheduled retry(
			long tenantId, UUID eventId, PlatformPrincipal actor) {
		// MySQL TIMESTAMP(6) stores microseconds. Normalizing before persisting keeps
		// the first response identical to later idempotent responses read from MySQL.
		Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
		WebhookRetryOutcome outcome = transactions.execute(status -> repository.retryWebhook(
			tenantId, oauthProperties.environment(), eventId,
			actor.id(), actor.publicId(), now));
		if (outcome == null || outcome.result() == WebhookRetryResult.NOT_FOUND) {
			throw new CheckoutPaymentException(
				"WEBHOOK_EVENT_NOT_FOUND", "No se encontro el evento de pago.");
		}
		if (outcome.result() == WebhookRetryResult.PROCESSED) {
			throw new CheckoutPaymentException(
				"WEBHOOK_ALREADY_PROCESSED", "El evento ya fue procesado y no puede reintentarse.");
		}
		if (outcome.result() == WebhookRetryResult.SCHEDULED) {
			metrics.manuallyScheduled();
		}
		return new WebhookRetryScheduled(
			eventId, "RETRY_SCHEDULED", outcome.availableAt());
	}

	public record WebhookRetryScheduled(UUID eventId, String status, Instant scheduledAt) {
	}
}
