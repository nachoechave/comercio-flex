package com.comercioflex.order.application;

import java.math.BigDecimal;
import java.util.UUID;

public record ActiveQuantityPromotion(
    UUID productId,
    int bundleQuantity,
    BigDecimal bundlePrice) {
}
