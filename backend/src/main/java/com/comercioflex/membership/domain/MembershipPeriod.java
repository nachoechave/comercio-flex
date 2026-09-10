package com.comercioflex.membership.domain;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
public record MembershipPeriod(UUID publicId, int periodYear, int periodMonth, LocalDate coverageStart,
 LocalDate coverageEndExclusive, UUID planPublicId, String planNameSnapshot, BigDecimal amount,
 String currency, String accreditationStatus, Instant accreditedAt, Instant createdAt, Instant updatedAt) {}
