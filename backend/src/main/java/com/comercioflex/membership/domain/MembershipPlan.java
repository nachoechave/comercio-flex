package com.comercioflex.membership.domain;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public record MembershipPlan(@com.fasterxml.jackson.annotation.JsonIgnore long id, UUID publicId,
 String name, String description, BigDecimal price, String currency, List<String> benefits,
 boolean active, int displayOrder, Instant createdAt, Instant updatedAt) {}
