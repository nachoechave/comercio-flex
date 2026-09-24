package com.comercioflex.order.application;

import com.comercioflex.order.domain.OrderStatus;
import java.time.Instant;
import java.util.UUID;

public record LockedAdminOrder(
    long internalId,
    UUID id,
    OrderStatus status,
    Instant reservationExpiresAt,
    long version,
    com.comercioflex.order.domain.FulfillmentType fulfillmentType) {
  public LockedAdminOrder(
      long internalId, UUID id, OrderStatus status, Instant reservationExpiresAt, long version) {
    this(
        internalId,
        id,
        status,
        reservationExpiresAt,
        version,
        com.comercioflex.order.domain.FulfillmentType.PICKUP);
  }
}
