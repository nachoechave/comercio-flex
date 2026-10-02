package com.comercioflex.order.application;

import java.util.UUID;

/** Payload used by the transactional email/outbox adapter when a pickup becomes ready. */
public record PickupReadyEvent(
    UUID orderId,
    String customerEmail,
    String branchName,
    String branchAddress) {}
