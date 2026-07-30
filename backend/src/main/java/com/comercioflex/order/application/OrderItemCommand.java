package com.comercioflex.order.application;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemCommand(UUID variantId, BigDecimal quantity) {
}

