package com.comercioflex.order.application;

import java.util.UUID;

/** Projection exposed by order APIs for branch-aware fulfillment. */
public record BranchFulfillmentView(UUID id, String name, String address) {}
