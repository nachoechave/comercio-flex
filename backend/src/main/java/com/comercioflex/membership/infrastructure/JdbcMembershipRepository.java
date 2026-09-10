package com.comercioflex.membership.infrastructure;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.comercioflex.membership.application.*;
import com.comercioflex.membership.domain.*;
@Repository
public class JdbcMembershipRepository implements MembershipRepository {
 private final JdbcTemplate jdbc;
 private final ObjectMapper json;
 private static final String PLAN = "SELECT p.*, BIN_TO_UUID(p.public_id) uuid FROM membership_plans p ";
 private static final String MEMBER = "SELECT m.*, BIN_TO_UUID(m.public_id) uuid, BIN_TO_UUID(m.platform_user_id) user_uuid FROM paid_memberships m ";
 private static final String PERIOD = "SELECT p.*, BIN_TO_UUID(p.public_id) uuid, BIN_TO_UUID(mp.public_id) plan_uuid FROM membership_periods p JOIN membership_plans mp ON mp.id=p.plan_id ";
 public JdbcMembershipRepository(@Qualifier("tenantJdbcTemplate") JdbcTemplate jdbc, ObjectMapper json) { this.jdbc=jdbc; this.json=json; }
 public List<MembershipPlan> plans(boolean activeOnly) { return jdbc.query(PLAN + (activeOnly ? "WHERE active=TRUE " : "") + "ORDER BY display_order,id", this::planRow); }
 public MembershipPlan plan(UUID id, boolean lock) { return jdbc.query(PLAN+"WHERE p.public_id=UUID_TO_BIN(?)"+(lock?" FOR UPDATE":""),this::planRow,id.toString()).stream().findFirst().orElseThrow(MembershipProblem::missing); }
 public MembershipPlan plan(long id) { return jdbc.query(PLAN+"WHERE p.id=?",this::planRow,id).stream().findFirst().orElseThrow(MembershipProblem::missing); }
 public UUID savePlan(UUID id, MembershipPlan p) {
  String benefits;
  try { benefits=json.writeValueAsString(p.benefits()); } catch(Exception e) { throw new IllegalStateException(e); }
  if(id==null) { id=UUID.randomUUID(); jdbc.update("INSERT INTO membership_plans(public_id,name,description,price,currency,benefits,active,display_order) VALUES(UUID_TO_BIN(?),?,?,?,?,?,?,?)",id.toString(),p.name(),p.description(),p.price(),p.currency(),benefits,p.active(),p.displayOrder()); }
  else if(jdbc.update("UPDATE membership_plans SET name=?,description=?,price=?,currency=?,benefits=?,active=?,display_order=?,updated_at=CURRENT_TIMESTAMP(6) WHERE public_id=UUID_TO_BIN(?)",p.name(),p.description(),p.price(),p.currency(),benefits,p.active(),p.displayOrder(),id.toString())!=1) throw MembershipProblem.missing();
  return id;
 }
 public Optional<PaidMembership> member(UUID user, boolean lock) { return jdbc.query(MEMBER+"WHERE platform_user_id=UUID_TO_BIN(?)"+(lock?" FOR UPDATE":""),this::memberRow,user.toString()).stream().findFirst(); }
 public PaidMembership memberByPublicId(UUID id) { return jdbc.query(MEMBER+"WHERE m.public_id=UUID_TO_BIN(?)",this::memberRow,id.toString()).stream().findFirst().orElseThrow(MembershipProblem::missing); }
 public void ensureMember(UUID user,long plan,Instant now) {
  jdbc.update("INSERT INTO paid_memberships(public_id,platform_user_id,current_plan_id,started_at,created_at,updated_at) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,?,?) ON DUPLICATE KEY UPDATE id=id",UUID.randomUUID().toString(),user.toString(),plan,Timestamp.from(now),Timestamp.from(now),Timestamp.from(now));
 }
 public void changePlan(long member,long plan,Instant now) { jdbc.update("UPDATE paid_memberships SET current_plan_id=?,updated_at=? WHERE id=?",plan,Timestamp.from(now),member); }
 public void cancel(long member,Instant now) { jdbc.update("UPDATE paid_memberships SET cancelled_at=COALESCE(cancelled_at,?),updated_at=? WHERE id=?",Timestamp.from(now),Timestamp.from(now),member); }
 public Optional<MembershipPeriod> current(long member,YearMonth month) { return jdbc.query(PERIOD+"WHERE membership_id=? AND period_year=? AND period_month=? FOR UPDATE",this::periodRow,member,month.getYear(),month.getMonthValue()).stream().findFirst(); }
 public List<MembershipPeriod> periods(long member,int offset) { return jdbc.query(PERIOD+"WHERE membership_id=? ORDER BY period_year DESC,period_month DESC LIMIT 100 OFFSET ?",this::periodRow,member,offset); }
 public void ensurePeriod(long member,MembershipPlan plan,YearMonth month,Instant now) {
  jdbc.update("INSERT INTO membership_periods(public_id,membership_id,period_year,period_month,coverage_start,coverage_end_exclusive,plan_id,plan_name_snapshot,amount_snapshot,currency_snapshot,created_at,updated_at) VALUES(UUID_TO_BIN(?),?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id",UUID.randomUUID().toString(),member,month.getYear(),month.getMonthValue(),java.sql.Date.valueOf(month.atDay(1)),java.sql.Date.valueOf(month.plusMonths(1).atDay(1)),plan.id(),plan.name(),plan.price(),plan.currency(),Timestamp.from(now),Timestamp.from(now));
 }
 public void replacePending(long member,MembershipPlan plan,YearMonth month,Instant now) {
  jdbc.update("UPDATE membership_periods SET plan_id=?,plan_name_snapshot=?,amount_snapshot=?,currency_snapshot=?,updated_at=? WHERE membership_id=? AND period_year=? AND period_month=? AND accreditation_status='PENDING' AND accredited_at IS NULL",plan.id(),plan.name(),plan.price(),plan.currency(),Timestamp.from(now),member,month.getYear(),month.getMonthValue());
 }
 public List<PaidMembership> members(YearMonth month,MembershipState state,UUID plan,int offset) {
  String sql=MEMBER+"LEFT JOIN membership_periods cp ON cp.membership_id=m.id AND cp.period_year=? AND cp.period_month=? WHERE 1=1";
  List<Object> args=new ArrayList<>(List.of(month.getYear(),month.getMonthValue()));
  if(plan!=null) { sql+=" AND m.current_plan_id=(SELECT id FROM membership_plans WHERE public_id=UUID_TO_BIN(?))";args.add(plan.toString()); }
  if(state!=null) { sql+=" AND (CASE WHEN m.cancelled_at IS NOT NULL THEN 'CANCELLED' WHEN cp.id IS NULL THEN 'EXPIRED' WHEN cp.accreditation_status='ACCREDITED' THEN 'ACTIVE' ELSE 'PENDING' END)=?";args.add(state.name()); }
  sql+=" ORDER BY m.id DESC LIMIT 100 OFFSET ?";args.add(offset);
  return jdbc.query(sql,this::memberRow,args.toArray());
 }
 private MembershipPlan planRow(ResultSet r,int n) throws SQLException {
  List<String> benefits;
  try { benefits=json.readValue(r.getString("benefits"),new TypeReference<List<String>>(){}); } catch(Exception e) { throw new IllegalStateException(e); }
  return new MembershipPlan(r.getLong("id"),UUID.fromString(r.getString("uuid")),r.getString("name"),r.getString("description"),r.getBigDecimal("price"),r.getString("currency"),benefits,r.getBoolean("active"),r.getInt("display_order"),instant(r,"created_at"),instant(r,"updated_at"));
 }
 private PaidMembership memberRow(ResultSet r,int n) throws SQLException { return new PaidMembership(r.getLong("id"),UUID.fromString(r.getString("uuid")),UUID.fromString(r.getString("user_uuid")),r.getLong("current_plan_id"),instant(r,"started_at"),instant(r,"cancelled_at"),instant(r,"created_at"),instant(r,"updated_at")); }
 private MembershipPeriod periodRow(ResultSet r,int n) throws SQLException { return new MembershipPeriod(UUID.fromString(r.getString("uuid")),r.getInt("period_year"),r.getInt("period_month"),r.getDate("coverage_start").toLocalDate(),r.getDate("coverage_end_exclusive").toLocalDate(),UUID.fromString(r.getString("plan_uuid")),r.getString("plan_name_snapshot"),r.getBigDecimal("amount_snapshot"),r.getString("currency_snapshot"),r.getString("accreditation_status"),instant(r,"accredited_at"),instant(r,"created_at"),instant(r,"updated_at")); }
 private Instant instant(ResultSet r,String name) throws SQLException { Timestamp value=r.getTimestamp(name); return value==null?null:value.toInstant(); }
}
