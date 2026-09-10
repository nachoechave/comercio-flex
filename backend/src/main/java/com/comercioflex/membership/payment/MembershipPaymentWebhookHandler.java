package com.comercioflex.membership.payment;
import java.time.Clock;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.micrometer.core.instrument.MeterRegistry;
import com.comercioflex.payment.application.*;
import com.comercioflex.payment.domain.PaymentEnvironment;
import com.comercioflex.tenant.application.TenantResolver;
@Service
public class MembershipPaymentWebhookHandler {
 private static final Logger LOG=LoggerFactory.getLogger(MembershipPaymentWebhookHandler.class);
 private final MembershipPaymentRepository repository;
 private final MembershipCheckoutService checkout;
 private final TenantResolver tenants;
 private final TransactionTemplate tx;
 private final Clock clock;
 private final MeterRegistry metrics;
 public MembershipPaymentWebhookHandler(MembershipPaymentRepository repository,MembershipCheckoutService checkout,TenantResolver tenants,@Qualifier("tenantTransactionTemplate") TransactionTemplate tx,@Qualifier("membershipClock") Clock clock,MeterRegistry metrics) {this.repository=repository;this.checkout=checkout;this.tenants=tenants;this.tx=tx;this.clock=clock;this.metrics=metrics;}
 public void apply(CheckoutRoute route,PaymentCredential credential,VerifiedProviderPayment payment,String expectedPaymentId) {
  try {
   var tenant=tenants.resolveActive(route.tenantSlug());
   if(!route.membership()||tenant.id()!=route.tenantId()||!tenant.databaseKey().equals(route.tenantDatabaseKey())||tenant.tenantType()!=com.comercioflex.tenant.domain.TenantType.RADIO)fail("TENANT");
   var a=repository.find(route.paymentAttemptId(),false);
   MembershipCheckoutService.validateCredential(a,credential);MembershipCheckoutService.validateFingerprint(route.tenantId(),a);
   if(payment==null||!expectedPaymentId.matches("[0-9]{1,20}")||!expectedPaymentId.equals(payment.providerPaymentId()))fail("PAYMENT_ID");
   if(!a.seller().equals(payment.sellerAccountId())||!a.seller().equals(route.expectedSellerAccountId()))fail("SELLER");
   if(a.environment()!=route.environment()||payment.liveMode()!=(a.environment()==PaymentEnvironment.PRODUCTION))fail("ENVIRONMENT");
   if(!a.externalReference().equals(payment.externalReference()))fail("REFERENCE");
   if(payment.amount()==null||a.amount().compareTo(payment.amount())!=0)fail("AMOUNT");
   if(!a.currency().equals(payment.currencyCode()))fail("CURRENCY");
   if(a.preferenceId()==null) {a=checkout.recover(tenant,a,credential);if(a.preferenceId()==null)throw new CheckoutPaymentException("CHECKOUT_ROUTE_NOT_READY","La preferencia sigue en conciliación.");}
   if(!a.preferenceId().equals(payment.preferenceId())||(route.preferenceId()!=null&&!a.preferenceId().equals(route.preferenceId())))fail("PREFERENCE");
   if(payment.providerUpdatedAt()==null||!Set.of("APPROVED","PENDING","IN_PROCESS","AUTHORIZED","REJECTED","CANCELLED","REFUNDED","CHARGED_BACK").contains(payment.rawStatus()))fail("STATUS");
   var selected=a;
   String result=tx.execute(s->{var locked=repository.lock(selected.publicId());MembershipCheckoutService.validateFingerprint(route.tenantId(),locked);return repository.observe(locked,payment,clock.instant());});
   LOG.info("membership_payment tenant={} period={} attempt={} provider_payment={} result={}",route.tenantSlug(),a.periodPublicId(),a.publicId(),payment.providerPaymentId(),result);
   metrics.counter("comercio.flex.membership.payments","result",result).increment();
  } catch(CheckoutPaymentException e) {tx.executeWithoutResult(s->repository.incident(route.paymentAttemptId(),e.code(),clock.instant()));throw e;}
 }
 private static void fail(String reason) {throw new CheckoutPaymentException("MEMBERSHIP_"+reason+"_MISMATCH","El pago no coincide con la cuota.");}
}
