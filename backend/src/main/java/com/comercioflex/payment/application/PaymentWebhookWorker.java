package com.comercioflex.payment.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.tenant.application.TenantContext;

@Component
@ConditionalOnProperty(
	prefix = "app.payments.checkout-pro", name = "enabled", havingValue = "true")
public class PaymentWebhookWorker {

	private static final int BATCH_SIZE = 20;

	private final CheckoutControlRepository repository;
	private final PaymentCredentialResolver credentials;
	private final CheckoutProGateway gateway;
	private final CheckoutProService checkoutService;
	private final CheckoutProProperties properties;
	private final PaymentOAuthProperties oauthProperties;
	private final TenantContext tenantContext;
	private final TransactionTemplate controlTransactions;
	private final PaymentWebhookMetrics metrics;
	private final Clock clock;

	@Autowired
	public PaymentWebhookWorker(
			CheckoutControlRepository repository,
			PaymentCredentialResolver credentials,
			CheckoutProGateway gateway,
			CheckoutProService checkoutService,
			CheckoutProProperties properties,
			PaymentOAuthProperties oauthProperties,
			TenantContext tenantContext,
			@Qualifier("controlTransactionTemplate") TransactionTemplate controlTransactions,
			PaymentWebhookMetrics metrics) {
		this(repository, credentials, gateway, checkoutService, properties,
			oauthProperties, tenantContext, controlTransactions, metrics,
			Clock.systemUTC());
	}

	PaymentWebhookWorker(
			CheckoutControlRepository repository,
			PaymentCredentialResolver credentials,
			CheckoutProGateway gateway,
			CheckoutProService checkoutService,
			CheckoutProProperties properties,
			PaymentOAuthProperties oauthProperties,
			TenantContext tenantContext,
			TransactionTemplate controlTransactions,
			PaymentWebhookMetrics metrics,
			Clock clock) {
		this.repository = repository;
		this.credentials = credentials;
		this.gateway = gateway;
		this.checkoutService = checkoutService;
		this.properties = properties;
		this.oauthProperties = oauthProperties;
		this.tenantContext = tenantContext;
		this.controlTransactions = controlTransactions;
		this.metrics = metrics;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${app.payments.checkout-pro.worker-delay:1000}")
	public void drain() {
		for (int index = 0; index < BATCH_SIZE; index++) {
			Optional<ClaimedWebhookEvent> claimed = controlTransactions.execute(status ->
				repository.claimNext(clock.instant(), clock.instant().plus(properties.webhookLease())));
			if (claimed == null || claimed.isEmpty()) {
				return;
			}
			process(claimed.get());
		}
	}

	private void process(ClaimedWebhookEvent event) {
		try {
			CheckoutRoute route = event.route();
			if (!"ACTIVE".equals(route.status()) || route.preferenceId() == null) {
				throw new CheckoutPaymentException("CHECKOUT_ROUTE_NOT_READY", "La ruta aún no está lista.");
			}
			PaymentCredential credential = credentials.resolve(route.tenantId(), route.tenantSlug());
			if (!credential.sellerAccountId().equals(route.expectedSellerAccountId())
					|| credential.environment() != route.environment()
					|| route.environment() != oauthProperties.environment()) {
				throw new CheckoutPaymentException(
					"WEBHOOK_CREDENTIAL_MISMATCH", "La credencial no coincide con la ruta.");
			}
			VerifiedProviderPayment payment = gateway.findPayment(
				credential, event.providerResourceId());
			try (TenantContext.Scope ignored = tenantContext.open(route.tenantDatabaseKey())) {
				checkoutService.applyVerifiedPayment(route.paymentAttemptId(), payment);
			}
			Boolean changed = controlTransactions.execute(status ->
				repository.markProcessed(
					event.internalId(), event.attemptCount(), clock.instant()));
			if (Boolean.TRUE.equals(changed)) {
				metrics.processed();
			}
		}
		catch (RuntimeException exception) {
			boolean retryable = retryable(exception);
			boolean dead = !retryable || event.attemptCount() >= properties.maxWebhookAttempts();
			String code = safeCode(exception);
			Instant next = clock.instant().plus(retryDelay(event.attemptCount()));
			Boolean changed = controlTransactions.execute(status ->
				repository.markFailed(
					event.internalId(), event.attemptCount(), dead, code, next));
			if (Boolean.TRUE.equals(changed)) {
				metrics.failed(dead, retryable);
			}
		}
	}

	private boolean retryable(RuntimeException exception) {
		if (exception instanceof TransientDataAccessException) {
			return true;
		}
		if (exception instanceof PaymentOAuthException oauth) {
			return oauth.code().equals("OAUTH_PROVIDER_UNAVAILABLE")
				|| oauth.code().equals("SELLER_PROFILE_UNAVAILABLE");
		}
		return exception instanceof CheckoutPaymentException checkout
			&& (checkout.code().equals("PAYMENT_LOOKUP_FAILED")
				|| checkout.code().equals("CHECKOUT_ROUTE_NOT_READY"));
	}

	private Duration retryDelay(int attempt) {
		long multiplier = 1L << Math.min(Math.max(attempt - 1, 0), 6);
		return properties.retryDelay().multipliedBy(multiplier);
	}

	private String safeCode(RuntimeException exception) {
		if (exception instanceof CheckoutPaymentException checkout) {
			return checkout.code().length() <= 64
				? checkout.code() : "WEBHOOK_PROCESSING_FAILED";
		}
		if (exception instanceof PaymentOAuthException oauth
				&& oauth.code().length() <= 64) {
			return oauth.code();
		}
		return "WEBHOOK_PROCESSING_FAILED";
	}
}
