package com.comercioflex.membership.application;
import java.time.*;
import java.util.*;
import com.comercioflex.membership.domain.*;
public interface MembershipRepository {
 List<MembershipPlan> plans(boolean activeOnly);
 MembershipPlan plan(UUID publicId, boolean lock);
 MembershipPlan plan(long id);
 UUID savePlan(UUID publicId, MembershipPlan plan);
 Optional<PaidMembership> member(UUID user, boolean lock);
 PaidMembership memberByPublicId(UUID id);
 void ensureMember(UUID user, long planId, Instant now);
 void changePlan(long membershipId, long planId, Instant now);
 void cancel(long membershipId, Instant now);
 Optional<MembershipPeriod> current(long memberId, YearMonth month);
 List<MembershipPeriod> periods(long memberId, int offset);
 void ensurePeriod(long memberId, MembershipPlan plan, YearMonth month, Instant now);
 void replacePending(long memberId, MembershipPlan plan, YearMonth month, Instant now);
 List<PaidMembership> members(YearMonth month, MembershipState state, UUID planId, int offset);
}
