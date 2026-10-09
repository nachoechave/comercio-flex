package com.comercioflex.catalog.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SaleQuantityRulesTests {
    @Test
    void weightAcceptsHalfKiloStepsOnly() {
        var rules = SaleQuantityRules.standardWeight();
        assertTrue(rules.accepts(new BigDecimal("0.5")));
        assertTrue(rules.accepts(new BigDecimal("1.0")));
        assertTrue(rules.accepts(new BigDecimal("2.5")));
        assertFalse(rules.accepts(new BigDecimal("2.7")));
        assertFalse(rules.accepts(new BigDecimal("0.25")));
        assertFalse(rules.accepts(new BigDecimal("0")));
    }

    @Test
    void weightPriceUsesExactDecimalMultiplication() {
        var rules = SaleQuantityRules.standardWeight();
        assertEquals(new BigDecimal("35000.00"),
            rules.total(new BigDecimal("14000.00"), new BigDecimal("2.5")));
    }

    @Test
    void unitsRejectFractionalQuantitiesAndBounds() {
        var rules = SaleQuantityRules.standardUnit();
        assertTrue(rules.accepts(new BigDecimal("2")));
        assertFalse(rules.accepts(new BigDecimal("2.5")));
        assertThrows(IllegalArgumentException.class,
            () -> new SaleQuantityRules(SaleUnit.UNIT, new BigDecimal("0.5"),
                BigDecimal.ONE, new BigDecimal("99")));
    }
}
