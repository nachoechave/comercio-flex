package com.comercioflex.membership.payment;
import java.net.URI;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import com.comercioflex.membership.application.*;
import com.comercioflex.membership.domain.*;
import com.comercioflex.payment.application.*;
import com.comercioflex.payment.domain.PaymentEnvironment;
import com.comercioflex.tenant.application.*;
@Service
public class MembershipCheckoutService {
 private final MembershipRepository members;
 private final MembershipPaymentRepository payments;
 private final MemberIdentityDirectory identities;
 private final MembershipService memberships;
 private final MembershipPaymentRouting routes;
 private final PaymentCredentialResolver credentials;
 private final CheckoutProGateway gateway;
 private final CheckoutProProperties properties;
 private final PaymentOAuthProperties oauth;
 private final TenantDomainResolver domains;
 private final TransactionTemplate tx;
 private final Clock clock;
 public MembershipCheckoutService(MembershipRepository members,MembershipPaymentRepository payments,MemberIdentityDirectory identities,MembershipService memberships,MembershipPaymentRouting routes,PaymentCredentialResolver credentials,CheckoutProGateway gateway,CheckoutProProperties properties,PaymentOAuthProperties oauth,TenantDomainResolver domains,@Qualifier("tenantTransactionTemplate") TransactionTemplate tx,@Qualifier("membershipClock") Clock clock) {this.members=members;this.payments=payments;this.identities=identities;this.memberships=memberships;this.routes=routes;this.credentials=credentials;this.gateway=gateway;this.properties=properties;this.oauth=oauth;this.domains=domains;this.tx=tx;this.clock=clock;}
 public record CheckoutView(boolean available,String status,String checkoutUrl,String message) {}
 private record Prepared(MembershipPaymentAttempt attempt,boolean create) {}
 public boolean available(ResolvedTenant tenant) {return properties.enabled() && routes.enabled(tenant.id(),oauth.environment()) && credentials.isAvailable(tenant.id(),tenant.slug());}
 public CheckoutView state(ResolvedTenant tenant,UUID user) {
  identities.requireActive(user);boolean enabled=available(tenant);
  return tx.execute(s->{var member=members.member(user,false).orElse(null);if(member==null)return new CheckoutView(enabled,"NONE",null,"");var period=members.current(member.id(),memberships.currentMonth()).orElse(null);if(member.cancelledAt()!=null)return new CheckoutView(false,"CANCELLED_MEMBERSHIP",null,"La membresía está cancelada.");if(period==null)return new CheckoutView(enabled,"NONE",null,"");if("ACCREDITED".equals(period.accreditationStatus()))return new CheckoutView(enabled,"APPROVED",null,"Cuota pagada");return payments.forPeriod(period.publicId()).map(a->view(a,enabled,false)).orElse(new CheckoutView(enabled,"NOT_STARTED",null,enabled?"":"Mercado Pago no está disponible para esta radio."));});
 }
 String returnUrl(ResolvedTenant tenant) { return domains.verifiedPrimaryHostname(tenant.id()).map(h->"https://"+h+"/mi-cuenta/pago-retorno").orElseGet(()->properties.frontendBaseUri().resolve(TenantPublicPaths.radio(tenant.slug())+"/mi-cuenta/pago-retorno").toString()); }
 public CheckoutView initiate(ResolvedTenant tenant,UUID user) {
  identities.requireActive(user);
  if(!available(tenant))throw new MembershipProblem(409,"Mercado Pago no está disponible para esta radio.");
  PaymentCredential credential=credentials.resolve(tenant.id(),tenant.slug());
  String returnUrl=returnUrl(tenant);
  Prepared prepared=tx.execute(s->{
   var member=members.member(user,true).orElseThrow(MembershipProblem::missing);
   if(member.cancelledAt()!=null)throw MembershipProblem.conflict("La membresía está cancelada.");
   var period=members.current(member.id(),memberships.currentMonth()).orElseThrow(MembershipProblem::missing);
   if(!"PENDING".equals(period.accreditationStatus()))throw MembershipProblem.conflict("Cuota pagada.");
   if(period.amount().signum()<=0)throw MembershipProblem.conflict("Esta cuota no requiere Checkout Pro. Consultá a la radio.");
   var old=payments.forPeriod(period.publicId());
   if(old.isPresent()) {var a=old.get();validateCredential(a,credential);validateFingerprint(tenant.id(),a);return new Prepared(a,false);}
   UUID id=UUID.randomUUID();String key="rm-"+id;
   Instant expires=period.coverageEndExclusive().plusDays(7).atStartOfDay(ZoneOffset.UTC).toInstant();
   payments.insert(id,period.publicId(),key,fingerprint(tenant.id(),period.publicId(),period.amount().toPlainString(),period.currency(),credential.environment(),credential.sellerAccountId()),"rm_"+UUID.randomUUID(),credential.environment(),credential.sellerAccountId(),returnUrl,expires,clock.instant());
   return new Prepared(payments.find(id,false),true);
  });
  var a=prepared.attempt();
  // Register the route before any provider call. Each database commits independently.
  routes.ensure(tenant,a);
  if(prepared.create()) {
   try {a=attach(tenant,a,gateway.createPreference(credential,command(tenant.id(),a)),credential);}
   catch(RuntimeException e) {tx.executeWithoutResult(s->payments.unknown(prepared.attempt().publicId(),clock.instant()));throw new MembershipProblem(409,"Estamos verificando la operación. No inicies otro pago; consultá el estado en un minuto.");}
  } else if(a.preferenceId()==null && Boolean.TRUE.equals(tx.execute(s->payments.claimRecovery(prepared.attempt().publicId(),clock.instant())))) {
   a=recover(tenant,a,credential);
  }
  if(a.preferenceId()!=null)routes.activate(tenant.id(),a);
  return view(a,true,true);
 }
 public MembershipPaymentAttempt recover(ResolvedTenant tenant,MembershipPaymentAttempt a,PaymentCredential credential) {
  validateCredential(a,credential);validateFingerprint(tenant.id(),a);
  var found=gateway.recoverMembershipPreference(credential,command(tenant.id(),a));
  return found.map(p->attach(tenant,a,p,credential)).orElseGet(()->payments.find(a.publicId(),false));
 }
 private MembershipPaymentAttempt attach(ResolvedTenant tenant,MembershipPaymentAttempt a,CreatedCheckoutPreference p,PaymentCredential c) {
  if(p==null || p.preferenceId()==null || !c.sellerAccountId().equals(p.collectorAccountId()) || !safeCheckout(p.checkoutUri()))throw MembershipProblem.conflict("La preferencia requiere revisión.");
  var saved=tx.execute(s->{var locked=payments.lock(a.publicId());payments.attach(locked.publicId(),p.preferenceId(),p.checkoutUri().toString(),clock.instant());return payments.find(locked.publicId(),false);});
  routes.activate(tenant.id(),saved);return saved;
 }
 private boolean safeCheckout(URI uri) {if(uri==null||!"https".equalsIgnoreCase(uri.getScheme())||uri.getUserInfo()!=null||uri.getPort()!=-1)return false;String host=uri.getHost();return host!=null && (host.equals("mercadopago.com.ar")||host.endsWith(".mercadopago.com.ar")||host.equals("mercadopago.com")||host.endsWith(".mercadopago.com"));}
 public CheckoutPreferenceCommand command(long tenant,MembershipPaymentAttempt a) {return new CheckoutPreferenceCommand(a.publicId(),a.idempotencyKey(),a.externalReference(),"Cuota · "+a.title(),a.amount(),a.currency(),URI.create(a.returnUrl()),properties.publicBackendBaseUri().resolve("/api/v1/integrations/mercado-pago/webhooks?route="+routes.token(tenant,a.publicId())+"&source_news=webhooks"),a.expiresAt());}
 public static void validateCredential(MembershipPaymentAttempt a,PaymentCredential c) {if(a.environment()!=c.environment()||!a.seller().equals(c.sellerAccountId()))throw new CheckoutPaymentException("MEMBERSHIP_CREDENTIAL_MISMATCH","La cuenta o ambiente cambió. El pago requiere revisión.");}
 public static byte[] fingerprint(long tenant,UUID period,String amount,String currency,PaymentEnvironment env,String seller) {return MembershipPaymentRouting.hash("radio-membership:v1:"+tenant+":"+period+":"+new java.math.BigDecimal(amount).stripTrailingZeros().toPlainString()+":"+currency+":"+env+":"+seller);}
 public static void validateFingerprint(long tenant,MembershipPaymentAttempt a) {if(!MessageDigest.isEqual(a.fingerprint(),fingerprint(tenant,a.periodPublicId(),a.amount().toPlainString(),a.currency(),a.environment(),a.seller())))throw new CheckoutPaymentException("MEMBERSHIP_SNAPSHOT_MISMATCH","La cuota no coincide con el intento.");}
 private CheckoutView view(MembershipPaymentAttempt a,boolean enabled,boolean redirect) {
  if(a.expiresAt().isBefore(clock.instant()))return new CheckoutView(false,"EXPIRED_CHECKOUT",null,"El checkout venció. Consultá a la radio antes de volver a pagar.");
  String status=a.technicalStatus();String latest=payments.latestStatus(a.id());if(latest!=null)status=latest;
  String message=switch(status) {case "REJECTED","CANCELLED"->"No pudimos completar el pago.";case "PENDING","IN_PROCESS","AUTHORIZED","UNKNOWN","CREATING"->"Estamos confirmando tu pago.";case "REFUNDED","CHARGED_BACK"->"El pago requiere revisión de la radio.";default->"";};
  return new CheckoutView(enabled,status,enabled&&redirect?a.checkoutUrl():null,message);
 }
}
