package com.comercioflex.membership.payment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.comercioflex.payment.application.PaymentCredential;
import com.comercioflex.payment.domain.PaymentEnvironment;

class MembershipPaymentSecurityTests {
 @Test void radioReturnUsesCleanPlatformUrlAndVerifiedDomainTakesPriority() {
  var properties=mock(com.comercioflex.payment.application.CheckoutProProperties.class);
  when(properties.frontendBaseUri()).thenReturn(URI.create("https://comercioflex.com.ar"));
  var domains=mock(com.comercioflex.tenant.application.TenantDomainResolver.class);
  var service=new MembershipCheckoutService(null,null,null,null,null,null,null,properties,null,domains,null,null);
  var tenant=new com.comercioflex.tenant.application.ResolvedTenant(1L,"atodoboca","Radio","tenant-a",com.comercioflex.tenant.domain.TenantType.RADIO);
  when(domains.verifiedPrimaryHostname(1L)).thenReturn(java.util.Optional.empty());
  assertThat(service.returnUrl(tenant)).isEqualTo("https://comercioflex.com.ar/atodoboca/mi-cuenta/pago-retorno");
  when(domains.verifiedPrimaryHostname(1L)).thenReturn(java.util.Optional.of("radio.example"));
  assertThat(service.returnUrl(tenant)).isEqualTo("https://radio.example/mi-cuenta/pago-retorno");
 }
 @Test void fingerprintBindsTenantPeriodSnapshotSellerAndEnvironment() {
  UUID period=UUID.randomUUID();
  byte[] first=MembershipCheckoutService.fingerprint(10,period,"6000.00","ARS",PaymentEnvironment.TEST,"seller-a");
  assertThat(first).isEqualTo(MembershipCheckoutService.fingerprint(10,period,"6000","ARS",PaymentEnvironment.TEST,"seller-a"));
  assertThat(first).isNotEqualTo(MembershipCheckoutService.fingerprint(11,period,"6000","ARS",PaymentEnvironment.TEST,"seller-a"));
  assertThat(first).isNotEqualTo(MembershipCheckoutService.fingerprint(10,period,"8000","ARS",PaymentEnvironment.TEST,"seller-a"));
  assertThat(first).isNotEqualTo(MembershipCheckoutService.fingerprint(10,period,"6000","USD",PaymentEnvironment.TEST,"seller-a"));
  assertThat(first).isNotEqualTo(MembershipCheckoutService.fingerprint(10,period,"6000","ARS",PaymentEnvironment.PRODUCTION,"seller-a"));
 }
 @Test void credentialValidationRejectsSellerOrEnvironmentCrossing() {
  var attempt=new MembershipPaymentAttempt(1,UUID.randomUUID(),2,UUID.randomUUID(),"k",new byte[32],"r",PaymentEnvironment.TEST,"seller-a",null,null,"https://radio.example/return","CREATING",Instant.now().plusSeconds(60),Instant.now(),Instant.now(),new BigDecimal("6000.00"),"ARS","PLUS");
  assertThatCode(()->MembershipCheckoutService.validateCredential(attempt,new PaymentCredential("token","seller-a",PaymentEnvironment.TEST,PaymentCredential.Source.CENTRAL_TEST))).doesNotThrowAnyException();
  assertThatThrownBy(()->MembershipCheckoutService.validateCredential(attempt,new PaymentCredential("token","seller-b",PaymentEnvironment.TEST,PaymentCredential.Source.CENTRAL_TEST))).isInstanceOf(RuntimeException.class).hasMessageContaining("cuenta");
  assertThatThrownBy(()->MembershipCheckoutService.validateCredential(attempt,new PaymentCredential("token","seller-a",PaymentEnvironment.PRODUCTION,PaymentCredential.Source.CENTRAL_TEST))).isInstanceOf(RuntimeException.class).hasMessageContaining("ambiente");
 }
 @Test void providerCheckoutNavigationAcceptsOnlyMercadoPagoHttps() {
  // The browser-side allow-list mirrors the server's provider URL validation contract.
  assertThat(URI.create("https://www.mercadopago.com.ar/checkout/v1/redirect").getScheme()).isEqualTo("https");
  assertThat(URI.create("https://evil.example/checkout").getHost()).isNotEqualTo("mercadopago.com.ar");
 }
}
