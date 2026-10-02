package com.comercioflex.order.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ActiveQuantityPromotion(
    UUID promotionId,
    List<UUID> productIds,
    int bundleQuantity,
    BigDecimal bundlePrice) {
}
