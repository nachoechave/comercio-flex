package com.comercioflex.membership.payment;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.comercioflex.payment.application.*;
import com.comercioflex.payment.domain.PaymentEnvironment;
import com.comercioflex.tenant.application.ResolvedTenant;
@Repository
public class MembershipPaymentRouting {
 private final JdbcTemplate jdbc;
 private final CheckoutProProperties properties;
 public MembershipPaymentRouting(@Qualifier("controlJdbcTemplate") JdbcTemplate jdbc, CheckoutProProperties properties) { this.jdbc=jdbc;this.properties=properties; }
 public boolean enabled(long tenant, PaymentEnvironment environment) {
  return jdbc.queryForObject("SELECT COUNT(*) FROM merchant_payment_capabilities c JOIN tenants t ON t.id=c.tenant_id WHERE c.tenant_id=? AND c.environment=? AND c.membership_checkout_enabled=TRUE AND t.tenant_type='RADIO' AND t.status='ACTIVE'",Integer.class,tenant,environment.name())==1;
 }
 public void enabled(long tenant,PaymentEnvironment environment,boolean enabled) {jdbc.update("INSERT INTO merchant_payment_capabilities(tenant_id,environment,membership_checkout_enabled) VALUES(?,?,?) ON DUPLICATE KEY UPDATE membership_checkout_enabled=VALUES(membership_checkout_enabled)",tenant,environment.name(),enabled);}
 public String token(long tenant, UUID attempt) {
  try { Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(properties.webhookSecret().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(("radio-membership-route:v1:"+tenant+":"+attempt).getBytes(StandardCharsets.UTF_8))); }
  catch(Exception e) { throw new IllegalStateException("No se pudo firmar la ruta.",e); }
 }
 public void ensure(ResolvedTenant tenant, MembershipPaymentAttempt a) {
  jdbc.update("INSERT INTO payment_webhook_routes(public_id,route_token_hash,tenant_id,environment,payment_intent_public_id,expected_seller_account_id,status,expires_at,destination_type) VALUES(UUID_TO_BIN(?),?,?,?,UUID_TO_BIN(?),?,'PENDING',?,'RADIO_MEMBERSHIP') ON DUPLICATE KEY UPDATE id=id",UUID.randomUUID().toString(),hash(token(tenant.id(),a.publicId())),tenant.id(),a.environment().name(),a.publicId().toString(),a.seller(),java.sql.Timestamp.from(a.expiresAt()));
  Integer valid=jdbc.queryForObject("SELECT COUNT(*) FROM payment_webhook_routes WHERE route_token_hash=? AND tenant_id=? AND environment=? AND payment_intent_public_id=UUID_TO_BIN(?) AND expected_seller_account_id=? AND destination_type='RADIO_MEMBERSHIP'",Integer.class,hash(token(tenant.id(),a.publicId())),tenant.id(),a.environment().name(),a.publicId().toString(),a.seller());
  if(valid!=1)throw new CheckoutPaymentException("MEMBERSHIP_ROUTE_CONFLICT","La ruta de pago requiere revisión.");
 }
 public void activate(long tenant,MembershipPaymentAttempt a) {
  int count=jdbc.update("UPDATE payment_webhook_routes SET provider_preference_id=?,status='ACTIVE' WHERE tenant_id=? AND environment=? AND payment_intent_public_id=UUID_TO_BIN(?) AND destination_type='RADIO_MEMBERSHIP' AND (provider_preference_id IS NULL OR provider_preference_id=?)",a.preferenceId(),tenant,a.environment().name(),a.publicId().toString(),a.preferenceId());
  if(count!=1)throw new CheckoutPaymentException("MEMBERSHIP_ROUTE_CONFLICT","La ruta de pago requiere revisión.");
 }
 public static byte[] hash(String s) { try{return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalStateException(e);} }
}
