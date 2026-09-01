package com.comercioflex.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
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
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.payment.domain.PaymentEnvironment;

class QrSetupServiceTests {

	private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");
	private static final UUID TENANT_PUBLIC_ID =
		UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
	private static final UUID POS_KEY =
		UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");

	private final QrSetupRepository repository = mock(QrSetupRepository.class);
	private final MercadoPagoQrProvisioningGateway gateway =
		mock(MercadoPagoQrProvisioningGateway.class);
	private final PaymentCredentialResolver credentials = mock(PaymentCredentialResolver.class);
	private final TransactionTemplate transactions = mock(TransactionTemplate.class);
	private QrSetupService service;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		when(transactions.execute(any())).thenAnswer(invocation -> {
			TransactionCallback<Object> callback = invocation.getArgument(0);
			return callback.doInTransaction(mock(TransactionStatus.class));
		});
		doAnswer(invocation -> {
			Consumer<TransactionStatus> callback = invocation.getArgument(0);
			callback.accept(mock(TransactionStatus.class));
			return null;
		}).when(transactions).executeWithoutResult(any());
		when(repository.requireActiveTenant(1L, "tienda-a"))
			.thenReturn(new QrSetupTenant(1L, TENANT_PUBLIC_ID, "tienda-a"));
		when(repository.createIfMissing(
			any(Long.class), any(), anyString(), anyString(), any(), any()))
			.thenReturn(setup());
		when(repository.claimVerification(any(), any(), any())).thenReturn(true);
		when(credentials.resolve(1L, "tienda-a")).thenReturn(credential());
		service = new QrSetupService(
			repository, gateway, credentials, properties(), transactions,
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void exposesNoConfigurationWithoutCallingMercadoPago() {
		when(repository.find(1L, PaymentEnvironment.PRODUCTION))
			.thenReturn(Optional.empty());

		QrSetupView view = service.view(1L, "tienda-a");

		assertThat(view.status()).isEqualTo(QrProvisioningStatus.NO_CONFIGURADO);
		assertThat(view.authorization()).isEqualTo(QrAuthorizationStatus.NOT_CHECKED);
		assertThat(view.qrOrdersReady()).isFalse();
		verify(gateway, never()).findStore(any(), anyString());
	}

	@Test
	void readOnlyDiscoveryReportsAuthorizedButMissingResources() {
		when(gateway.findStore(any(), anyString())).thenReturn(Optional.empty());
		when(gateway.findPos(any(), anyString())).thenReturn(Optional.empty());

		QrSetupView result = service.discover(1L, "tienda-a");

		assertThat(result.authorization()).isEqualTo(QrAuthorizationStatus.AUTHORIZED);
		assertThat(result.storeConfigured()).isFalse();
		assertThat(result.posConfigured()).isFalse();
		verify(gateway, never()).createStore(any(), anyString(), any());
	}

	@Test
	void adoptsAValidExistingStoreAndPosWithoutCreatingDuplicates() {
		when(gateway.findStore(any(), anyString())).thenReturn(Optional.of(store()));
		when(gateway.findPos(any(), anyString())).thenReturn(Optional.of(pos("123456")));

		QrSetupView result = service.discover(1L, "tienda-a");

		assertThat(result.status()).isEqualTo(QrProvisioningStatus.LISTO);
		assertThat(result.qrOrdersReady()).isTrue();
		verify(gateway, never()).createStore(any(), anyString(), any());
		verify(gateway, never()).createPos(any(), anyString(), anyString(), anyString(), any());
	}

	@Test
	void createsStoreThenPdvPosOnlyAfterExplicitConfiguration() {
		when(gateway.findStore(any(), anyString())).thenReturn(Optional.empty());
		when(gateway.findPos(any(), anyString())).thenReturn(Optional.empty());
		when(gateway.createStore(any(), anyString(), any())).thenReturn(store());
		when(gateway.createPos(any(), anyString(), anyString(), anyString(), any()))
			.thenReturn(pos("123456"));

		QrSetupView result = service.configure(1L, "tienda-a", command());

		assertThat(result.qrOrdersReady()).isTrue();
		verify(gateway).createPos(
			credential(), "store-provider", setup().externalStoreId(),
			setup().externalPosId(), POS_KEY);
	}

	@Test
	void recoversThePosByExternalIdAfterAnAmbiguousTimeout() {
		when(gateway.findStore(any(), anyString())).thenReturn(Optional.of(store()));
		when(gateway.findPos(any(), anyString()))
			.thenReturn(Optional.empty())
			.thenReturn(Optional.of(pos("123456")));
		doThrow(new QrProviderException(
			QrAuthorizationStatus.PROVIDER_ERROR, true,
			"Proveedor temporalmente no disponible", null))
			.when(gateway).createPos(any(), anyString(), anyString(), anyString(), any());

		QrSetupView result = service.configure(1L, "tienda-a", command());

		assertThat(result.qrOrdersReady()).isTrue();
		verify(gateway, org.mockito.Mockito.times(2)).findPos(any(), anyString());
	}

	@Test
	void recoversTheStoreByExternalIdAfterTimeoutOrDuplicateConflict() {
		when(gateway.findStore(any(), anyString()))
			.thenReturn(Optional.empty())
			.thenReturn(Optional.of(store()));
		when(gateway.findPos(any(), anyString())).thenReturn(Optional.empty());
		doThrow(new QrProviderException(
			QrAuthorizationStatus.PROVIDER_ERROR, true,
			"Resultado de creación ambiguo", null))
			.when(gateway).createStore(any(), anyString(), any());
		when(gateway.createPos(any(), anyString(), anyString(), anyString(), any()))
			.thenReturn(pos("123456"));

		QrSetupView result = service.configure(1L, "tienda-a", command());

		assertThat(result.qrOrdersReady()).isTrue();
		verify(gateway, org.mockito.Mockito.times(2)).findStore(any(), anyString());
		verify(gateway).createPos(
			credential(), "store-provider", setup().externalStoreId(),
			setup().externalPosId(), POS_KEY);
	}

	@Test
	void rejectsAPosOwnedByAnotherOAuthSeller() {
		when(gateway.findStore(any(), anyString())).thenReturn(Optional.of(store()));
		when(gateway.findPos(any(), anyString())).thenReturn(Optional.of(pos("other-seller")));

		assertThatThrownBy(() -> service.discover(1L, "tienda-a"))
			.isInstanceOfSatisfying(QrSetupException.class, exception ->
				assertThat(exception.code()).isEqualTo("QR_POS_MISMATCH"));
		verify(repository).saveResult(
			any(), any(), any(),
			org.mockito.ArgumentMatchers.eq(QrProvisioningStatus.ERROR),
			org.mockito.ArgumentMatchers.eq(QrAuthorizationStatus.AUTHORIZED),
			org.mockito.ArgumentMatchers.eq("QR_VALIDATION_FAILED"), any());
	}

	@Test
	void classifiesUnauthorizedProviderDiscoveryWithoutLeakingTheCredential() {
		when(gateway.findStore(any(), anyString())).thenThrow(new QrProviderException(
			QrAuthorizationStatus.UNAUTHORIZED_SCOPES, false,
			"No autorizado", null));

		assertThatThrownBy(() -> service.discover(1L, "tienda-a"))
			.isInstanceOfSatisfying(QrSetupException.class, exception -> {
				assertThat(exception.code()).isEqualTo("QR_PROVIDER_UNAUTHORIZED_SCOPES");
				assertThat(exception.toString()).doesNotContain("access-token-fixture");
			});
	}

	@Test
	void preventsConcurrentProvisioningBeforeAnyProviderCall() {
		when(repository.claimVerification(any(), any(), any())).thenReturn(false);

		assertThatThrownBy(() -> service.configure(1L, "tienda-a", command()))
			.isInstanceOfSatisfying(QrSetupException.class, exception ->
				assertThat(exception.code()).isEqualTo("QR_SETUP_IN_PROGRESS"));
		verify(gateway, never()).findStore(any(), anyString());
	}

	private StoredQrSetup setup() {
		return new StoredQrSetup(
			9L, 1L, PaymentEnvironment.PRODUCTION, null,
			"CFSPaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", null,
			"CFPPaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
			QrProvisioningStatus.NO_CONFIGURADO,
			QrAuthorizationStatus.NOT_CHECKED, POS_KEY, 0L);
	}

	private QrProviderStore store() {
		return new QrProviderStore("store-provider", setup().externalStoreId());
	}

	private QrProviderPos pos(String seller) {
		return new QrProviderPos(
			"pos-provider", setup().externalPosId(), "store-provider",
			setup().externalStoreId(), seller, "active", "pdv");
	}

	private PaymentCredential credential() {
		return new PaymentCredential(
			"access-token-fixture", "123456", PaymentEnvironment.PRODUCTION,
			PaymentCredential.Source.TENANT_OAUTH);
	}

	private QrStoreSetupCommand command() {
		return new QrStoreSetupCommand(
			"Sucursal Centro", "San Martin", "123", "Cordoba", "Cordoba",
			new BigDecimal("-31.4167"), new BigDecimal("-64.1833"), null);
	}

	private PaymentOAuthProperties properties() {
		return new PaymentOAuthProperties(
			true, PaymentEnvironment.PRODUCTION, "client", "secret",
			URI.create("https://example.test/callback"),
			URI.create("https://auth.mercadopago.test"),
			URI.create("https://api.mercadopago.test"),
			URI.create("https://api.mercadolibre.test"),
			URI.create("https://example.test"), Duration.ofSeconds(3),
			Duration.ofSeconds(8), "v1", "fixture-key");
	}
}
