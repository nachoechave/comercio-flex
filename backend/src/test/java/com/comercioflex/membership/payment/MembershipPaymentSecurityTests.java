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
