package com.comercioflex.membership.payment;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.comercioflex.membership.application.MembershipProblem;
import com.comercioflex.payment.domain.PaymentEnvironment;
import com.comercioflex.payment.application.VerifiedProviderPayment;
@Repository
public class MembershipPaymentRepository {
 private final JdbcTemplate jdbc;
 private static final String ATTEMPT="SELECT a.*,BIN_TO_UUID(a.public_id) uuid,BIN_TO_UUID(p.public_id) period_uuid,p.amount_snapshot,p.currency_snapshot,p.plan_name_snapshot FROM membership_payment_attempts a JOIN membership_periods p ON p.id=a.membership_period_id ";
 public MembershipPaymentRepository(@Qualifier("tenantJdbcTemplate") JdbcTemplate jdbc) {this.jdbc=jdbc;}
 public Optional<MembershipPaymentAttempt> forPeriod(UUID id) {return jdbc.query(ATTEMPT+"WHERE p.public_id=UUID_TO_BIN(?) FOR UPDATE",this::row,id.toString()).stream().findFirst();}
 public MembershipPaymentAttempt find(UUID id,boolean lock) {return jdbc.query(ATTEMPT+"WHERE a.public_id=UUID_TO_BIN(?)"+(lock?" FOR UPDATE":""),this::row,id.toString()).stream().findFirst().orElseThrow(MembershipProblem::missing);}
 public void insert(UUID id,UUID period,String key,byte[] fingerprint,String reference,PaymentEnvironment environment,String seller,String returnUrl,Instant expires,Instant now) {
  jdbc.update("UPDATE membership_periods SET payment_locked_at=COALESCE(payment_locked_at,?) WHERE public_id=UUID_TO_BIN(?)",Timestamp.from(now),period.toString());
  jdbc.update("INSERT INTO membership_payment_attempts(public_id,membership_period_id,idempotency_key,fingerprint,external_reference,environment,seller_account_id,return_url,technical_status,expires_at,created_at,updated_at) SELECT UUID_TO_BIN(?),p.id,?,?,?,?,?,?,'CREATING',?,?,? FROM membership_periods p WHERE p.public_id=UUID_TO_BIN(?)",id.toString(),key,fingerprint,reference,environment.name(),seller,returnUrl,Timestamp.from(expires),Timestamp.from(now),Timestamp.from(now),period.toString());
 }
 public MembershipPaymentAttempt lock(UUID id) {var a=find(id,false);jdbc.queryForObject("SELECT id FROM membership_periods WHERE id=? FOR UPDATE",Long.class,a.periodId());return find(id,true);}
 public record AttemptView(UUID publicId,UUID periodPublicId,String status,String lastErrorCode,String preferenceId,String externalReference,Instant createdAt) {}
 public List<AttemptView> attempts(UUID membership,int offset) {return jdbc.query("SELECT BIN_TO_UUID(a.public_id) uuid,BIN_TO_UUID(p.public_id) period_uuid,a.* FROM membership_payment_attempts a JOIN membership_periods p ON p.id=a.membership_period_id JOIN paid_memberships m ON m.id=p.membership_id WHERE m.public_id=UUID_TO_BIN(?) ORDER BY a.id DESC LIMIT 100 OFFSET ?",(r,n)->new AttemptView(UUID.fromString(r.getString("uuid")),UUID.fromString(r.getString("period_uuid")),r.getString("technical_status"),r.getString("last_error_code"),r.getString("preference_id"),r.getString("external_reference"),instant(r,"created_at")),membership.toString(),offset);}
 public String latestStatus(long attempt) {return jdbc.query("SELECT provider_status FROM membership_payments WHERE payment_attempt_id=? ORDER BY provider_updated_at DESC,id DESC LIMIT 1",(r,n)->r.getString(1),attempt).stream().findFirst().orElse(null);}
 public void incident(UUID attempt,String code,Instant now) {jdbc.update("UPDATE membership_payment_attempts SET last_error_code=?,updated_at=? WHERE public_id=UUID_TO_BIN(?)",code,Timestamp.from(now),attempt.toString());}
 public void unknown(UUID id,Instant now) {jdbc.update("UPDATE membership_payment_attempts SET technical_status='UNKNOWN',last_error_code='CREATION_OUTCOME_UNKNOWN',updated_at=? WHERE public_id=UUID_TO_BIN(?) AND preference_id IS NULL",Timestamp.from(now),id.toString());}
 public void attach(UUID id,String preference,String url,Instant now) {
  int count=jdbc.update("UPDATE membership_payment_attempts SET preference_id=?,checkout_url=?,technical_status='CREATED',last_error_code=NULL,updated_at=? WHERE public_id=UUID_TO_BIN(?) AND (preference_id IS NULL OR preference_id=?)",preference,url,Timestamp.from(now),id.toString(),preference);
  if(count!=1)throw MembershipProblem.conflict("El intento cambió. Revisá el estado del pago.");
 }
 public boolean claimRecovery(UUID id,Instant now) { return jdbc.update("UPDATE membership_payment_attempts SET technical_status='UNKNOWN',updated_at=? WHERE public_id=UUID_TO_BIN(?) AND technical_status IN ('UNKNOWN','CREATING') AND preference_id IS NULL AND updated_at<?",Timestamp.from(now),id.toString(),Timestamp.from(now.minusSeconds(60)))==1; }
 public record PaymentView(UUID publicId, UUID periodPublicId, String providerStatus, Instant paidAt, Instant appliedAt, String reviewReason, String providerPaymentId, UUID attemptPublicId, String externalReference, Instant updatedAt, int periodYear, int periodMonth, String planName, java.math.BigDecimal amount, String currency, String periodStatus) {}
 public List<PaymentView> history(UUID user,UUID membership,int offset,boolean admin) {
  String where=admin?"m.public_id=UUID_TO_BIN(?)":"m.platform_user_id=UUID_TO_BIN(?)";
  return jdbc.query("SELECT BIN_TO_UUID(pay.public_id) uuid,BIN_TO_UUID(p.public_id) period_uuid,pay.*,p.period_year,p.period_month,p.plan_name_snapshot,p.amount_snapshot,p.currency_snapshot,p.accreditation_status,BIN_TO_UUID(a.public_id) attempt_uuid,a.external_reference FROM membership_payments pay JOIN membership_periods p ON p.id=pay.membership_period_id JOIN paid_memberships m ON m.id=p.membership_id JOIN membership_payment_attempts a ON a.id=pay.payment_attempt_id WHERE "+where+" ORDER BY pay.updated_at DESC,pay.id DESC LIMIT 100 OFFSET ?",(r,n)->new PaymentView(UUID.fromString(r.getString("uuid")),UUID.fromString(r.getString("period_uuid")),r.getString("provider_status"),instant(r,"paid_at"),instant(r,"applied_at"),r.getString("review_reason"),admin?r.getString("provider_payment_id"):null,admin?UUID.fromString(r.getString("attempt_uuid")):null,admin?r.getString("external_reference"):null,instant(r,"updated_at"),r.getInt("period_year"),r.getInt("period_month"),r.getString("plan_name_snapshot"),r.getBigDecimal("amount_snapshot"),r.getString("currency_snapshot"),r.getString("accreditation_status")),(admin?membership:user).toString(),offset);
 }
 public String observe(MembershipPaymentAttempt a,VerifiedProviderPayment p,Instant now) {
  var existing=jdbc.query("SELECT payment_attempt_id,provider_updated_at,provider_status FROM membership_payments WHERE provider='MERCADO_PAGO' AND environment=? AND seller_account_id=? AND provider_payment_id=? FOR UPDATE",(r,n)->new Object[]{r.getLong(1),r.getTimestamp(2).toInstant(),r.getString(3)},a.environment().name(),p.sellerAccountId(),p.providerPaymentId());
  if(!existing.isEmpty()) {
   Object[] row=existing.getFirst();
   if(!row[0].equals(a.id()))throw MembershipProblem.conflict("El pago ya corresponde a otro intento.");
   if(((Instant)row[1]).isAfter(p.providerUpdatedAt()))return "STALE";
   if(row[2].equals("APPROVED") && !Set.of("APPROVED","REFUNDED","CHARGED_BACK").contains(p.rawStatus()))return "STALE";
   if(Set.of("REFUNDED","CHARGED_BACK").contains(row[2]) && !Set.of("REFUNDED","CHARGED_BACK").contains(p.rawStatus()))return "STALE";
  }
  jdbc.update("INSERT INTO membership_payments(public_id,membership_period_id,payment_attempt_id,provider_payment_id,amount,currency,provider_status,environment,seller_account_id,paid_at,provider_updated_at,created_at,updated_at) VALUES(UUID_TO_BIN(?),?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE provider_status=VALUES(provider_status),paid_at=COALESCE(paid_at,VALUES(paid_at)),provider_updated_at=VALUES(provider_updated_at),updated_at=VALUES(updated_at)",UUID.randomUUID().toString(),a.periodId(),a.id(),p.providerPaymentId(),p.amount(),p.currencyCode(),p.rawStatus(),a.environment().name(),p.sellerAccountId(),timestamp(p.paidAt()),timestamp(p.providerUpdatedAt()),timestamp(now),timestamp(now));
  String reason=null;
  if("APPROVED".equals(p.rawStatus())) {
   int changed=jdbc.update("UPDATE membership_periods SET accreditation_status='ACCREDITED',accredited_at=?,updated_at=? WHERE id=? AND accreditation_status='PENDING'",timestamp(now),timestamp(now),a.periodId());
   if(changed==1)jdbc.update("UPDATE membership_payments SET applied_at=? WHERE payment_attempt_id=? AND provider_payment_id=?",timestamp(now),a.id(),p.providerPaymentId());
   else {
    Integer applied=jdbc.queryForObject("SELECT COUNT(*) FROM membership_payments WHERE payment_attempt_id=? AND provider_payment_id=? AND applied_at IS NOT NULL",Integer.class,a.id(),p.providerPaymentId());
    if(applied==0)reason="SECOND_APPROVED_PAYMENT";
   }
  } else if(Set.of("REFUNDED","CHARGED_BACK").contains(p.rawStatus()))reason="REVERSAL_REQUIRES_REVIEW";
  if(reason!=null)jdbc.update("UPDATE membership_payments SET review_reason=? WHERE payment_attempt_id=? AND provider_payment_id=?",reason,a.id(),p.providerPaymentId());
  return reason==null?p.rawStatus():reason;
 }
 private MembershipPaymentAttempt row(ResultSet r,int n)throws SQLException{return new MembershipPaymentAttempt(r.getLong("id"),UUID.fromString(r.getString("uuid")),r.getLong("membership_period_id"),UUID.fromString(r.getString("period_uuid")),r.getString("idempotency_key"),r.getBytes("fingerprint"),r.getString("external_reference"),PaymentEnvironment.valueOf(r.getString("environment")),r.getString("seller_account_id"),r.getString("preference_id"),r.getString("checkout_url"),r.getString("return_url"),r.getString("technical_status"),instant(r,"expires_at"),instant(r,"created_at"),instant(r,"updated_at"),r.getBigDecimal("amount_snapshot"),r.getString("currency_snapshot"),r.getString("plan_name_snapshot"));}
 private static Instant instant(ResultSet r,String name)throws SQLException {Timestamp t=r.getTimestamp(name);return t==null?null:t.toInstant();}
 private static Timestamp timestamp(Instant t) {return t==null?null:Timestamp.from(t);}
}
