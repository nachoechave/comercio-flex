package com.comercioflex.catalog.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Validates purchasable quantities without floating-point arithmetic. */
public record SaleQuantityRules(SaleUnit unit, BigDecimal minimum, BigDecimal step, BigDecimal maximum) {
    public SaleQuantityRules {
        if (unit == null || minimum == null || step == null || maximum == null
                || minimum.signum() <= 0 || step.signum() <= 0 || maximum.compareTo(minimum) < 0) {
            throw new IllegalArgumentException("Invalid sale quantity configuration");
        }
        if (unit == SaleUnit.UNIT && (minimum.stripTrailingZeros().scale() > 0
                || step.stripTrailingZeros().scale() > 0 || maximum.stripTrailingZeros().scale() > 0)) {
            throw new IllegalArgumentException("Unit quantities must be whole numbers");
        }
    }

    public static SaleQuantityRules standardUnit() {
        return new SaleQuantityRules(SaleUnit.UNIT, BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("99"));
    }

    public static SaleQuantityRules standardWeight() {
        return new SaleQuantityRules(SaleUnit.KG, new BigDecimal("0.500"),
                new BigDecimal("0.500"), new BigDecimal("99.000"));
    }

    public boolean accepts(BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(minimum) < 0 || quantity.compareTo(maximum) > 0) {
            return false;
        }
        return quantity.subtract(minimum).remainder(step).signum() == 0;
    }

    public BigDecimal total(BigDecimal pricePerUnit, BigDecimal quantity) {
        if (pricePerUnit == null || pricePerUnit.signum() <= 0 || !accepts(quantity)) {
            throw new IllegalArgumentException("Invalid sale quantity or price");
        }
        return pricePerUnit.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
    }
}
