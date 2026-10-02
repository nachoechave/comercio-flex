package com.comercioflex.order.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ActiveQuantityPromotion(
    UUID promotionId,
    List<UUID> productIds,
    List<UUID> secondProductIds,
    int bundleQuantity,
    BigDecimal bundlePrice) {
  public ActiveQuantityPromotion(UUID promotionId, List<UUID> productIds, int bundleQuantity, BigDecimal bundlePrice) {
    this(promotionId, productIds, List.of(), bundleQuantity, bundlePrice);
  }
  public BigDecimal comboDiscount(java.util.Map<UUID, List<BigDecimal>> pricesByProduct) {
    List<BigDecimal> first = productIds.stream().flatMap(id -> pricesByProduct.getOrDefault(id, List.of()).stream())
        .sorted(java.util.Comparator.reverseOrder()).toList();
    List<BigDecimal> second = secondProductIds.stream().flatMap(id -> pricesByProduct.getOrDefault(id, List.of()).stream())
        .sorted(java.util.Comparator.reverseOrder()).toList();
    BigDecimal discount = BigDecimal.ZERO;
    for (int i = 0; i < Math.min(first.size(), second.size()); i++) {
      discount = discount.add(first.get(i).add(second.get(i)).subtract(bundlePrice).max(BigDecimal.ZERO));
    }
    return discount;
  }
}
