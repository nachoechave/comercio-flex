package com.comercioflex.membership.application;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import com.comercioflex.membership.domain.*;
import com.comercioflex.tenant.application.StoreSettingsQueryService;
@Service
public class MembershipService {
 private final MembershipRepository repository;
 private final MemberIdentityDirectory identities;
 private final StoreSettingsQueryService settings;
 private final TransactionTemplate tx;
 private final Clock clock;
 public MembershipService(MembershipRepository repository,MemberIdentityDirectory identities,StoreSettingsQueryService settings,@Qualifier("tenantTransactionTemplate") TransactionTemplate tx,@Qualifier("membershipClock") Clock clock) { this.repository=repository;this.identities=identities;this.settings=settings;this.tx=tx;this.clock=clock; }
 public record View(UUID publicId, MembershipState state, MembershipPlan plan, MembershipPeriod currentPeriod, Instant startedAt, Instant cancelledAt) {}
 public record AdminView(View membership, MemberIdentityDirectory.Identity identity) {}
 public YearMonth currentMonth() { return YearMonth.now(clock.withZone(ZoneId.of(settings.findCurrent().timezone()))); }
 public List<MembershipPlan> plans(boolean publicOnly) { return tx.execute(s->repository.plans(publicOnly)); }
 public MembershipPlan savePlan(UUID id,MembershipPlan plan) {
  try { Currency.getInstance(plan.currency()); } catch(IllegalArgumentException e) { throw new MembershipProblem(400,"Moneda inválida."); }
  if(!plan.currency().equals(settings.findCurrent().currencyCode())) throw new MembershipProblem(400,"Usá la moneda configurada para este sitio.");
  return tx.execute(s-> { UUID saved=repository.savePlan(id,plan);return repository.plan(saved,false); });
 }
 public View mine(UUID user) { identities.requireActive(user); return tx.execute(s->view(repository.member(user,false).orElse(null),currentMonth())); }
 public View join(UUID user,UUID planId) {
  identities.requireActive(user);
  return tx.execute(s->{
   MembershipPlan selected=repository.plan(planId,false);
   repository.ensureMember(user,selected.id(),clock.instant());
   PaidMembership member=repository.member(user,true).orElseThrow(MembershipProblem::missing);
   requireNotCancelled(member);
   if(member.currentPlanId()!=selected.id()) throw MembershipProblem.conflict("Ya tenés un plan. Usá Cambiar plan.");
   selected=activePlan(planId);
   YearMonth month=currentMonth(); repository.ensurePeriod(member.id(),selected,month,clock.instant());
   return view(member,month);
  });
 }
 public View ensureCurrent(UUID user) {
  identities.requireActive(user);
  return tx.execute(s->{PaidMembership member=locked(user);requireNotCancelled(member);YearMonth month=currentMonth();
   if(repository.current(member.id(),month).isEmpty()) {
    MembershipPlan plan=activePlan(repository.plan(member.currentPlanId()).publicId());
    repository.ensurePeriod(member.id(),plan,month,clock.instant());
   }
   return view(member,month);
  });
 }
 public View changePlan(UUID user,UUID planId) {
  identities.requireActive(user);
  return tx.execute(s->{PaidMembership member=locked(user);requireNotCancelled(member);MembershipPlan plan=activePlan(planId);YearMonth month=currentMonth();
   if(member.currentPlanId()!=plan.id()) {
    repository.changePlan(member.id(),plan.id(),clock.instant());
    // Only an explicit change of plan may replace a pending snapshot. No payment model exists yet.
    repository.replacePending(member.id(),plan,month,clock.instant());
   }
   return view(locked(user),month);
  });
 }
 public View cancel(UUID user) { identities.requireActive(user);return tx.execute(s->{PaidMembership member=locked(user);repository.cancel(member.id(),clock.instant());return view(locked(user),currentMonth());}); }
 public List<MembershipPeriod> periods(UUID user,int offset) { identities.requireActive(user);return tx.execute(s->repository.member(user,false).map(m->repository.periods(m.id(),offset)).orElse(List.of())); }
 public List<MembershipPeriod> adminPeriods(UUID id,int offset) { return tx.execute(s->repository.periods(repository.memberByPublicId(id).id(),offset)); }
 public List<AdminView> members(MembershipState state,UUID plan,int offset) {
  record Row(UUID user,View view) {}
  List<Row> rows=tx.execute(s->{YearMonth month=currentMonth();return repository.members(month,state,plan,offset).stream().map(m->new Row(m.platformUserId(),view(m,month))).toList();});
  Map<UUID,MemberIdentityDirectory.Identity> people=identities.findAll(rows.stream().map(Row::user).toList());
  return rows.stream().map(r->new AdminView(r.view(),people.get(r.user()))).toList();
 }
 private PaidMembership locked(UUID user) { return repository.member(user,true).orElseThrow(MembershipProblem::missing); }
 private MembershipPlan activePlan(UUID id) { MembershipPlan plan=repository.plan(id,false);if(!plan.active())throw MembershipProblem.conflict("El plan no está disponible para nuevas cuotas. Elegí otro plan.");return plan; }
 private void requireNotCancelled(PaidMembership member) { if(member.cancelledAt()!=null)throw MembershipProblem.conflict("La membresía está cancelada."); }
 private View view(PaidMembership member,YearMonth month) {
  if(member==null)return new View(null,MembershipState.NONE,null,null,null,null);
  MembershipPeriod current=repository.current(member.id(),month).orElse(null);
  return new View(member.publicId(),MembershipState.derive(member,current),repository.plan(member.currentPlanId()),current,member.startedAt(),member.cancelledAt());
 }
}
