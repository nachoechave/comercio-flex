package com.comercioflex.membership.domain;
public enum MembershipState {
 NONE, PENDING, ACTIVE, EXPIRED, CANCELLED;
 public static MembershipState derive(PaidMembership member, MembershipPeriod current) {
  if (member == null) return NONE;
  if (member.cancelledAt() != null) return CANCELLED;
  if (current == null) return EXPIRED;
  return "ACCREDITED".equals(current.accreditationStatus()) ? ACTIVE : PENDING;
 }
}
