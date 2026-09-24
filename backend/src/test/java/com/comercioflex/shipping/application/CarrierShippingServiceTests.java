package com.comercioflex.shipping.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.comercioflex.payment.application.CredentialCipher;
import com.comercioflex.payment.application.EncryptedSecret;
import com.comercioflex.payment.application.PaymentOAuthProperties;
import com.comercioflex.shipping.application.CarrierRepository.StoredQuote;
import com.comercioflex.shipping.application.CarrierRepository.StoredSettings;
import com.comercioflex.shipping.domain.CarrierModels.*;
import com.comercioflex.shipping.domain.ShippingModels.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CarrierShippingServiceTests {
  private CarrierRepository repository;
  private ShippingRepository shippingRepository;
  private CarrierGateway gateway;
  private CredentialCipher cipher;
  private CarrierShippingService service;
  private StoredSettings settings;

  @BeforeEach
  void setUp() {
    repository = mock(CarrierRepository.class);
    shippingRepository = mock(ShippingRepository.class);
    gateway = mock(CarrierGateway.class);
    cipher = mock(CredentialCipher.class);
    PaymentOAuthProperties properties = mock(PaymentOAuthProperties.class);
    when(gateway.provider()).thenReturn(Provider.ANDREANI);
    when(cipher.decrypt(any(), any())).thenReturn("credential");

    EncryptedSecret secret = new EncryptedSecret("v1", new byte[12], new byte[] {1, 2, 3});
    settings =
        new StoredSettings(
            Provider.ANDREANI,
            true,
            Environment.SANDBOX,
            "cliente",
            "contrato",
            secret,
            secret,
            UUID.randomUUID().toString(),
            new Origin(
                "1925",
                "Calle",
                "123",
                "Ensenada",
                "Buenos Aires",
                "Argentina",
                "Tienda",
                "tienda@example.com",
                "2210000000",
                DocumentType.CUIT,
                "30700000001"),
            new Parcel(
                new BigDecimal("500"),
                new BigDecimal("20"),
                new BigDecimal("15"),
                new BigDecimal("10")),
            15,
            2);
    service =
        new CarrierShippingService(
            repository,
            shippingRepository,
            List.of(gateway),
            cipher,
            properties,
            null,
            null);
  }

  @Test
  void quoteStoresShortLivedCarrierTokenAndScalesWeightByUnits() {
    when(repository.settings(false)).thenReturn(settings);
    when(gateway.quote(any(), eq("1900"), any()))
        .thenReturn(
            new QuoteResult(
                "ANDREANI_DOMICILIO", new BigDecimal("3200.00"), 2, "Entrega Andreani"));

    var quotes =
        service.quotes(
            new BigDecimal("12000.00"),
            BigDecimal.ZERO.setScale(2),
            null,
            "1900",
            new BigDecimal("3"));

    assertThat(quotes).hasSize(1);
    Quote quote = quotes.getFirst();
    assertThat(quote.type()).isEqualTo(MethodType.CARRIER);
    assertThat(quote.provider()).isEqualTo("ANDREANI");
    assertThat(quote.shippingAmount()).isEqualByComparingTo("3200.00");
    assertThat(quote.total()).isEqualByComparingTo("15200.00");
    assertThat(quote.quoteToken()).isNotNull();

    ArgumentCaptor<StoredQuote> saved = ArgumentCaptor.forClass(StoredQuote.class);
    verify(repository).saveQuote(saved.capture());
    assertThat(saved.getValue().parcel().weightGrams()).isEqualByComparingTo("1500");
    assertThat(saved.getValue().expiresAt()).isAfter(Instant.now());
  }

  @Test
  void freeShippingKeepsProviderCostForMerchant() {
    when(repository.settings(false)).thenReturn(settings);
    when(gateway.quote(any(), eq("1900"), any()))
        .thenReturn(
            new QuoteResult(
                "ANDREANI_DOMICILIO", new BigDecimal("3200.00"), null, "Entrega Andreani"));

    Quote quote =
        service
            .quotes(
                new BigDecimal("50000.00"),
                new BigDecimal("5000.00"),
                new BigDecimal("50000.00"),
                "1900",
                BigDecimal.ONE)
            .getFirst();

    assertThat(quote.freeShipping()).isTrue();
    assertThat(quote.shippingAmount()).isZero();
    assertThat(quote.total()).isEqualByComparingTo("45000.00");
    ArgumentCaptor<StoredQuote> saved = ArgumentCaptor.forClass(StoredQuote.class);
    verify(repository).saveQuote(saved.capture());
    assertThat(saved.getValue().providerCost()).isEqualByComparingTo("3200.00");
  }

  @Test
  void expiredQuoteCannotBecomeAnOrderSnapshot() {
    UUID token = UUID.randomUUID();
    UUID method = CarrierShippingService.ANDREANI_METHOD_ID;
    when(repository.quote(token, true))
        .thenReturn(
            java.util.Optional.of(
                storedQuote(token, method, Instant.now().minusSeconds(1), null)));

    Selection selection =
        new Selection(
            method,
            address("1900"),
            new BigDecimal("15200.00"),
            token,
            DocumentType.DNI,
            "41131132");

    assertThatThrownBy(
            () ->
                service.select(
                    selection, new BigDecimal("12000.00"), BigDecimal.ZERO.setScale(2), address("1900")))
        .isInstanceOf(ShippingException.class)
        .hasMessageContaining("venció");
  }

  @Test
  void validCarrierQuoteBecomesImmutableSnapshotAndIsConsumedSeparately() {
    UUID token = UUID.randomUUID();
    UUID method = CarrierShippingService.ANDREANI_METHOD_ID;
    when(repository.quote(token, true))
        .thenReturn(
            java.util.Optional.of(
                storedQuote(token, method, Instant.now().plusSeconds(300), null)));
    Address address = address("1900");
    Selection selection =
        new Selection(
            method,
            address,
            new BigDecimal("15200.00"),
            token,
            DocumentType.DNI,
            "41131132");

    Snapshot snapshot =
        service.select(
            selection, new BigDecimal("12000.00"), BigDecimal.ZERO.setScale(2), address);

    assertThat(snapshot.type()).isEqualTo(MethodType.CARRIER);
    assertThat(snapshot.provider()).isEqualTo("ANDREANI");
    assertThat(snapshot.quoteToken()).isEqualTo(token);
    assertThat(snapshot.documentNumber()).isEqualTo("41131132");
    assertThat(snapshot.providerCost()).isEqualByComparingTo("3200.00");

    service.consume(snapshot);
    verify(repository).consumeQuote(token);
  }

  private StoredQuote storedQuote(UUID token, UUID method, Instant expires, Instant consumed) {
    return new StoredQuote(
        token,
        method,
        Provider.ANDREANI,
        "ANDREANI_DOMICILIO",
        new BigDecimal("3200.00"),
        new BigDecimal("3200.00"),
        new BigDecimal("12000.00"),
        BigDecimal.ZERO.setScale(2),
        new BigDecimal("15200.00"),
        "1900",
        settings.defaultParcel(),
        expires,
        consumed);
  }

  private Address address(String postal) {
    return new Address("Calle", "10", null, "La Plata", "Buenos Aires", postal);
  }
}
