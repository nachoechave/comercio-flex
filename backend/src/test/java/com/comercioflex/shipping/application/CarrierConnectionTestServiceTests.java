package com.comercioflex.shipping.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.comercioflex.payment.application.CredentialCipher;
import com.comercioflex.shipping.domain.CarrierModels.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CarrierConnectionTestServiceTests {
  private CarrierRepository repository;
  private CarrierGateway gateway;
  private CarrierConnectionTestService service;

  @BeforeEach
  void setUp() {
    repository = mock(CarrierRepository.class);
    gateway = mock(CarrierGateway.class);
    when(gateway.provider()).thenReturn(Provider.ANDREANI);
    service =
        new CarrierConnectionTestService(
            repository, List.of(gateway), mock(CredentialCipher.class));
  }

  @Test
  void validatesAuthenticationAndQuoteWithoutPersistingCredentials() {
    when(gateway.quote(any(), any(), any()))
        .thenReturn(
            new QuoteResult(
                "ANDREANI_DOMICILIO", new BigDecimal("3450.00"), 2, "Entrega Andreani"));

    ConnectionTestResult result = service.test(request());

    assertThat(result.success()).isTrue();
    assertThat(result.authenticationOk()).isTrue();
    assertThat(result.quoteOk()).isTrue();
    assertThat(result.providerCost()).isEqualByComparingTo("3450.00");
    assertThat(result.serviceCode()).isEqualTo("ANDREANI_DOMICILIO");
    assertThat(result.checks()).extracting(ConnectionCheck::code).containsExactly("AUTH", "QUOTE");
    verify(gateway).authenticate(any());
    verify(gateway).quote(any(), any(), any());
  }

  @Test
  void reportsPartialSuccessWhenAuthenticationWorksButQuoteFails() {
    doThrow(new CarrierUnavailableException("Contrato sin tarifa para ese destino."))
        .when(gateway)
        .quote(any(), any(), any());

    ConnectionTestResult result = service.test(request());

    assertThat(result.success()).isFalse();
    assertThat(result.authenticationOk()).isTrue();
    assertThat(result.quoteOk()).isFalse();
    assertThat(result.checks().get(1).message()).contains("Contrato sin tarifa");
  }

  @Test
  void stopsBeforeQuoteWhenAndreaniRejectsAuthentication() {
    doThrow(new CarrierUnavailableException("Andreani rechazó la autenticación."))
        .when(gateway)
        .authenticate(any());

    ConnectionTestResult result = service.test(request());

    assertThat(result.success()).isFalse();
    assertThat(result.authenticationOk()).isFalse();
    assertThat(result.quoteOk()).isFalse();
    assertThat(result.checks().getFirst().message()).contains("rechazó");
  }

  private ConnectionTestRequest request() {
    return new ConnectionTestRequest(
        Provider.ANDREANI,
        Environment.SANDBOX,
        "CLIENTE",
        "CONTRATO",
        "usuario",
        "secreto",
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
        "1900");
  }
}
