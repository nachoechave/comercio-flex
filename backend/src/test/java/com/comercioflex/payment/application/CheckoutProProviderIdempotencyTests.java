package com.comercioflex.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.order.application.AdminOrderRepository;
import com.comercioflex.order.application.PaidOrderConfirmer;
import com.comercioflex.order.domain.OrderStatus;
import com.comercioflex.payment.domain.PaymentEnvironment;
import com.comercioflex.payment.domain.PaymentIntentStatus;
import com.comercioflex.tenant.application.ResolvedTenant;
import com.comercioflex.tenant.application.TenantResolver;

class CheckoutProProviderIdempotencyTests {

	private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");
	private static final String LOOKUP_TOKEN =
		"abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ";
	private static final UUID ORDER_ID = UUID.fromString(
		"11111111-1111-4111-8111-111111111111");

	@Test
	void persistsTheIntentBeforeSendingItsStableProviderKey() {
		Harness harness = new Harness(ORDER_ID);
		UUID key = UUID.fromString("22222222-2222-4222-8222-222222222222");
		AtomicReference<CheckoutPreferenceCommand> sent = new AtomicReference<>();
		harness.providerCall.set(command -> {
			assertThat(harness.attempt.get()).isNotNull();
			assertThat(harness.attempt.get().idempotencyKey()).isEqualTo(key);
			sent.set(command);
			return preference("pref-stable");
		});

		CheckoutInitiation result = harness.service().initiate(
			"tienda-a", ORDER_ID, LOOKUP_TOKEN, key);

		assertThat(sent.get().providerIdempotencyKey()).isNotBlank();
		assertThat(sent.get().providerIdempotencyKey()).isNotEqualTo(key.toString());
		assertThat(sent.get().providerIdempotencyKey())
			.isNotEqualTo(sent.get().externalReference());
		assertThat(sent.get().externalReference()).isNotEqualTo(key.toString());
		assertThat(result.checkoutUrl()).isEqualTo(preference("pref-stable").checkoutUri());
		assertThat(harness.inserts).hasValue(1);
		assertThat(harness.attachments).hasValue(1);
	}

	@Test
	void knownProviderFailureStillMarksTheIntentForReviewAndExpiresItsRoute() {
		Harness harness = new Harness(ORDER_ID);
		UUID key = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
		harness.providerCall.set(command -> {
			throw new CheckoutPaymentException(
				"PREFERENCE_CREATION_FAILED", "synthetic provider rejection");
		});

		assertThatThrownBy(() -> harness.service().initiate(
			"tienda-a", ORDER_ID, LOOKUP_TOKEN, key))
			.isInstanceOf(CheckoutPaymentException.class)
			.satisfies(exception -> assertThat(((CheckoutPaymentException) exception).code())
				.isEqualTo("PREFERENCE_CREATION_FAILED"));

		verify(harness.repository).markCreationForReview(any());
		verify(harness.controlRepository).expireRoute(any(), any());
	}

	@Test
	void retriesAnUnknownProviderOutcomeWithTheSameKeyAfterAServiceRestart() {
		Harness harness = new Harness(ORDER_ID);
		UUID key = UUID.fromString("33333333-3333-4333-8333-333333333333");
		AtomicInteger calls = new AtomicInteger();
		List<String> sentKeys = new CopyOnWriteArrayList<>();
		harness.providerCall.set(command -> {
			sentKeys.add(command.providerIdempotencyKey());
			if (calls.getAndIncrement() == 0) {
				throw new CheckoutPaymentException(
					"PREFERENCE_CREATION_OUTCOME_UNKNOWN", "synthetic timeout");
			}
			return preference("pref-after-timeout");
		});

		assertThatThrownBy(() -> harness.service().initiate(
			"tienda-a", ORDER_ID, LOOKUP_TOKEN, key))
			.isInstanceOf(CheckoutPaymentException.class)
			.satisfies(exception -> assertThat(((CheckoutPaymentException) exception).code())
				.isEqualTo("PREFERENCE_CREATION_OUTCOME_UNKNOWN"));
		assertThat(harness.attempt.get().status()).isEqualTo(PaymentIntentStatus.CREATED);
		verify(harness.repository, never()).markCreationForReview(any());
		verify(harness.controlRepository, never()).expireRoute(any(), any());

		CheckoutInitiation retried = harness.service().initiate(
			"tienda-a", ORDER_ID, LOOKUP_TOKEN, key);

		assertThat(retried.checkoutUrl()).isNotNull();
		assertThat(sentKeys).hasSize(2).allSatisfy(sentKey ->
			assertThat(sentKey).isEqualTo(sentKeys.getFirst()));
		assertThat(sentKeys.getFirst()).isNotEqualTo(key.toString());
		assertThat(harness.inserts).hasValue(1);
		assertThat(harness.attachments).hasValue(1);
	}

	@Test
	void usesDifferentProviderKeysForDifferentPaymentIntents() {
		UUID firstKey = UUID.fromString("44444444-4444-4444-8444-444444444444");
		UUID secondKey = UUID.fromString("55555555-5555-4555-8555-555555555555");
		Harness first = new Harness(ORDER_ID);
		Harness second = new Harness(UUID.fromString(
			"66666666-6666-4666-8666-666666666666"));
		AtomicReference<String> firstSent = new AtomicReference<>();
		AtomicReference<String> secondSent = new AtomicReference<>();
		first.providerCall.set(command -> {
			firstSent.set(command.providerIdempotencyKey());
			return preference("pref-first");
		});
		second.providerCall.set(command -> {
			secondSent.set(command.providerIdempotencyKey());
			return preference("pref-second");
		});

		first.service().initiate("tienda-a", first.orderId, LOOKUP_TOKEN, firstKey);
		second.service().initiate("tienda-a", second.orderId, LOOKUP_TOKEN, secondKey);

		assertThat(firstSent.get()).isNotEqualTo(firstKey.toString());
		assertThat(secondSent.get()).isNotEqualTo(secondKey.toString());
		assertThat(firstSent.get()).isNotEqualTo(secondSent.get());
	}

	@Test
	void concurrentRetriesOfTheSameIntentUseOneProviderKeyAndOneAssociation()
			throws Exception {
		Harness harness = new Harness(ORDER_ID);
		UUID key = UUID.fromString("77777777-7777-4777-8777-777777777777");
		CountDownLatch providerCalls = new CountDownLatch(2);
		List<String> sentKeys = new CopyOnWriteArrayList<>();
		harness.providerCall.set(command -> {
			sentKeys.add(command.providerIdempotencyKey());
			providerCalls.countDown();
			await(providerCalls);
			return preference("pref-concurrent");
		});
		var executor = Executors.newFixedThreadPool(2);
		try {
			Future<CheckoutInitiation> first = executor.submit(() -> harness.service().initiate(
				"tienda-a", ORDER_ID, LOOKUP_TOKEN, key));
			Future<CheckoutInitiation> second = executor.submit(() -> harness.service().initiate(
				"tienda-a", ORDER_ID, LOOKUP_TOKEN, key));

			assertThat(first.get(10, TimeUnit.SECONDS).checkoutUrl()).isNotNull();
			assertThat(second.get(10, TimeUnit.SECONDS).checkoutUrl()).isNotNull();
		}
		finally {
			executor.shutdownNow();
		}
		assertThat(sentKeys).hasSize(2).allSatisfy(sentKey ->
			assertThat(sentKey).isEqualTo(sentKeys.getFirst()));
		assertThat(sentKeys.stream().distinct()).containsExactly(sentKeys.getFirst());
		assertThat(harness.inserts).hasValue(1);
		assertThat(harness.attachments).hasValue(1);
		assertThat(harness.attempt.get().preferenceId()).isEqualTo("pref-concurrent");
	}

	@Test
	void concurrentRequestsWithDifferentKeysNeverReachTheProviderTwice()
			throws Exception {
		Harness harness = new Harness(ORDER_ID);
		UUID firstKey = UUID.fromString("88888888-8888-4888-8888-888888888888");
		UUID secondKey = UUID.fromString("99999999-9999-4999-8999-999999999999");
		CountDownLatch firstProviderCall = new CountDownLatch(1);
		CountDownLatch releaseProvider = new CountDownLatch(1);
		List<String> sentKeys = new CopyOnWriteArrayList<>();
		harness.providerCall.set(command -> {
			sentKeys.add(command.providerIdempotencyKey());
			firstProviderCall.countDown();
			await(releaseProvider);
			return preference("pref-only");
		});
		var executor = Executors.newFixedThreadPool(2);
		try {
			Future<CheckoutInitiation> first = executor.submit(() -> harness.service().initiate(
				"tienda-a", ORDER_ID, LOOKUP_TOKEN, firstKey));
			assertThat(firstProviderCall.await(10, TimeUnit.SECONDS)).isTrue();
			Future<Object> second = executor.submit(() -> {
				try {
					return harness.service().initiate(
						"tienda-a", ORDER_ID, LOOKUP_TOKEN, secondKey);
				}
				catch (RuntimeException exception) {
					return exception;
				}
			});
			Object secondResult = second.get(10, TimeUnit.SECONDS);
			releaseProvider.countDown();
			assertThat(first.get(10, TimeUnit.SECONDS).checkoutUrl()).isNotNull();
			assertThat(secondResult).isInstanceOf(CheckoutPaymentException.class);
			assertThat(((CheckoutPaymentException) secondResult).code())
				.isEqualTo("PAYMENT_ALREADY_IN_PROGRESS");
		}
		finally {
			releaseProvider.countDown();
			executor.shutdownNow();
		}
		assertThat(sentKeys).hasSize(1);
		assertThat(sentKeys.getFirst()).isNotEqualTo(firstKey.toString());
		assertThat(harness.inserts).hasValue(1);
		assertThat(harness.attachments).hasValue(1);
	}

	@Test
	void neverOverwritesAnAlreadyAssociatedPreference() {
		Harness harness = new Harness(ORDER_ID);
		UUID key = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
		harness.providerCall.set(command -> {
			harness.associate("pref-existing");
			return preference("pref-different");
		});

		assertThatThrownBy(() -> harness.service().initiate(
			"tienda-a", ORDER_ID, LOOKUP_TOKEN, key))
			.isInstanceOf(CheckoutPaymentException.class)
			.satisfies(exception -> assertThat(((CheckoutPaymentException) exception).code())
				.isEqualTo("PAYMENT_CONCURRENT_UPDATE"));

		assertThat(harness.attempt.get().preferenceId()).isEqualTo("pref-existing");
		assertThat(harness.attachments).hasValue(0);
	}

	private static CreatedCheckoutPreference preference(String id) {
		return new CreatedCheckoutPreference(
			id, URI.create("https://sandbox.mercadopago.com/checkout/" + id), "seller-1");
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(10, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Timed out waiting for concurrent checkout test.");
			}
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(exception);
		}
	}

	private static final class Harness {

		private final UUID orderId;
		private final CheckoutRepository repository = mock(CheckoutRepository.class);
		private final CheckoutControlRepository controlRepository =
			mock(CheckoutControlRepository.class);
		private final CheckoutProGateway gateway = mock(CheckoutProGateway.class);
		private final AtomicReference<StoredCheckoutAttempt> attempt = new AtomicReference<>();
		private final AtomicReference<Function<CheckoutPreferenceCommand,
			CreatedCheckoutPreference>> providerCall = new AtomicReference<>(
				command -> preference("pref-default"));
		private final AtomicInteger inserts = new AtomicInteger();
		private final AtomicInteger attachments = new AtomicInteger();
		private final ReentrantLock tenantTransactionLock = new ReentrantLock();
		private final PaymentCredential credential = new PaymentCredential(
			"synthetic-access-token", "seller-1", PaymentEnvironment.TEST,
			PaymentCredential.Source.CENTRAL_TEST);

		private Harness(UUID orderId) {
			this.orderId = orderId;
			CheckoutOrder order = new CheckoutOrder(
				42L, orderId, OrderStatus.PENDING_CONFIRMATION,
				new BigDecimal("5.00"), "ARS", NOW.plusSeconds(1800));
			when(repository.lockOrder(any(), any())).thenReturn(Optional.of(order));
			when(repository.findByIdempotencyKey(any())).thenAnswer(invocation -> {
				StoredCheckoutAttempt current = attempt.get();
				UUID key = invocation.getArgument(0);
				return current != null && current.idempotencyKey().equals(key)
					? Optional.of(current) : Optional.empty();
			});
			when(repository.hasBlockingIntent(42L)).thenAnswer(invocation -> {
				StoredCheckoutAttempt current = attempt.get();
				return current != null && List.of(
					PaymentIntentStatus.CREATED, PaymentIntentStatus.PENDING,
					PaymentIntentStatus.APPROVED, PaymentIntentStatus.REQUIRES_REVIEW)
					.contains(current.status());
			});
			when(repository.nextAttemptNumber(42L)).thenReturn(1);
			doAnswer(invocation -> {
				UUID paymentAttemptId = invocation.getArgument(0);
				UUID idempotencyKey = invocation.getArgument(2);
				byte[] fingerprint = invocation.getArgument(3);
				UUID transitionKey = invocation.getArgument(4);
				Instant returnExpiresAt = invocation.getArgument(6);
				BigDecimal amount = invocation.getArgument(8);
				String currency = invocation.getArgument(9);
				attempt.set(new StoredCheckoutAttempt(
					1L, paymentAttemptId, 42L, orderId, 42L,
					OrderStatus.PENDING_CONFIRMATION.name(), order.reservationExpiresAt(),
					idempotencyKey, fingerprint, transitionKey, PaymentIntentStatus.CREATED,
					amount, currency, paymentAttemptId.toString(), returnExpiresAt,
					null, null, null, null, null, NOW, 0L));
				inserts.incrementAndGet();
				return null;
			}).when(repository).insertIntent(
				any(), any(Long.class), any(), any(), any(), any(), any(),
				any(Integer.class), any(), any());
			when(repository.findByPublicId(any(), anyBoolean())).thenAnswer(invocation -> {
				StoredCheckoutAttempt current = attempt.get();
				UUID requested = invocation.getArgument(0);
				return current != null && current.id().equals(requested)
					? Optional.of(current) : Optional.empty();
			});
			doAnswer(invocation -> {
				StoredCheckoutAttempt current = invocation.getArgument(0);
				String preferenceId = invocation.getArgument(1);
				URI checkoutUri = invocation.getArgument(2);
				Instant expiresAt = invocation.getArgument(3);
				String seller = invocation.getArgument(4);
				PaymentEnvironment environment = invocation.getArgument(5);
				Instant now = invocation.getArgument(6);
				if (attempt.get().status() != PaymentIntentStatus.CREATED
						|| attempt.get().version() != current.version()) {
					throw new CheckoutPaymentException(
						"PAYMENT_CONCURRENT_UPDATE", "synthetic concurrent update");
				}
				associate(preferenceId, checkoutUri, expiresAt, seller, environment, now);
				attachments.incrementAndGet();
				return null;
			}).when(repository).attachPreference(
				any(), any(), any(), any(), any(), any(), any());
			when(gateway.createPreference(any(), any())).thenAnswer(invocation ->
				providerCall.get().apply(invocation.getArgument(1)));
		}

		private CheckoutProService service() {
			PaymentCredentialResolver credentials = mock(PaymentCredentialResolver.class);
			when(credentials.resolve(1L, "tienda-a")).thenReturn(credential);
			TenantResolver tenants = mock(TenantResolver.class);
			when(tenants.resolveActive("tienda-a"))
				.thenReturn(new ResolvedTenant(1L, "tienda-a", "Tienda A", "tenant-a"));
			PaymentOAuthProperties oauth = mock(PaymentOAuthProperties.class);
			when(oauth.environment()).thenReturn(PaymentEnvironment.TEST);
			CheckoutProProperties properties = new CheckoutProProperties(
				true, null, null, null,
				URI.create("https://api.example.test"),
				URI.create("https://shop.example.test"),
				"synthetic-webhook-secret", Duration.ofSeconds(1), Duration.ofSeconds(1),
				Duration.ofSeconds(30), Duration.ofSeconds(30), 3, Duration.ofHours(1));
			return new CheckoutProService(
				repository, controlRepository, credentials, gateway,
				mock(PaidOrderConfirmer.class), mock(RejectedPaymentNotifier.class),
				mock(AdminOrderRepository.class), tenants, properties, oauth,
				transactions(tenantTransactionLock), transactions(new ReentrantLock()),
				Clock.fixed(NOW, ZoneOffset.UTC));
		}

		private void associate(String preferenceId) {
			associate(preferenceId, preference(preferenceId).checkoutUri(),
				NOW.plusSeconds(1800), "seller-1", PaymentEnvironment.TEST, NOW);
		}

		private void associate(
				String preferenceId, URI checkoutUri, Instant expiresAt,
				String seller, PaymentEnvironment environment, Instant now) {
			StoredCheckoutAttempt current = attempt.get();
			attempt.set(new StoredCheckoutAttempt(
				current.internalId(), current.id(), current.orderInternalId(), current.orderId(),
				current.orderNumber(), current.orderStatus(), current.reservationExpiresAt(),
				current.idempotencyKey(), current.requestFingerprint(),
				current.transitionIdempotencyKey(), PaymentIntentStatus.PENDING,
				current.amount(), current.currencyCode(), current.externalReference(),
				current.returnTokenExpiresAt(), preferenceId, checkoutUri, expiresAt,
				seller, environment, now, current.version() + 1));
		}
	}

	private static TransactionTemplate transactions(ReentrantLock lock) {
		TransactionTemplate transactions = mock(TransactionTemplate.class);
		when(transactions.execute(any(TransactionCallback.class))).thenAnswer(invocation -> {
			lock.lock();
			try {
				TransactionCallback<?> callback = invocation.getArgument(0);
				return callback.doInTransaction(mock(TransactionStatus.class));
			}
			finally {
				lock.unlock();
			}
		});
		doAnswer(invocation -> {
			lock.lock();
			try {
				Consumer<TransactionStatus> callback = invocation.getArgument(0);
				callback.accept(mock(TransactionStatus.class));
				return null;
			}
			finally {
				lock.unlock();
			}
		}).when(transactions).executeWithoutResult(any());
		return transactions;
	}
}
