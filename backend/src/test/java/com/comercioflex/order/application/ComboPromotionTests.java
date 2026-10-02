package com.comercioflex.order.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ComboPromotionTests {
  private final UUID shirt = UUID.randomUUID();
  private final UUID pants = UUID.randomUUID();
  private final ActiveQuantityPromotion promo = new ActiveQuantityPromotion(UUID.randomUUID(),
      List.of(shirt), List.of(pants), 2, new BigDecimal("40000"));
  private void check(String expected, Map<UUID, List<BigDecimal>> prices) {
    assertEquals(0, new BigDecimal(expected).compareTo(promo.comboDiscount(prices)));
  }
  @Test void requiresBothGroups() {
    check("0", Map.of(pants, List.of(new BigDecimal("35000"), new BigDecimal("35000"))));
    check("0", Map.of(shirt, List.of(new BigDecimal("25000"), new BigDecimal("25000"))));
    check("10000", Map.of(shirt, List.of(new BigDecimal("15000")), pants, List.of(new BigDecimal("35000"))));
  }
  @Test void leavesUnpairedUnitsAtRegularPrice() {
    check("10000", Map.of(shirt, List.of(new BigDecimal("15000")), pants,
        List.of(new BigDecimal("35000"), new BigDecimal("35000"))));
  }
  @Test void repeatsPairsWithoutIncreasingPrices() {
    check("20000", Map.of(shirt, List.of(new BigDecimal("15000"), new BigDecimal("15000")), pants,
        List.of(new BigDecimal("35000"), new BigDecimal("35000"))));
    check("0", Map.of(shirt, List.of(new BigDecimal("10000")), pants, List.of(new BigDecimal("20000"))));
  }
}
