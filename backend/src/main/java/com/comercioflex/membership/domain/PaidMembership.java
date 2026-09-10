package com.comercioflex.membership.domain;
import java.time.Instant;
import java.util.UUID;
public record PaidMembership(long id, UUID publicId, UUID platformUserId, long currentPlanId,
 Instant startedAt, Instant cancelledAt, Instant createdAt, Instant updatedAt) {}
