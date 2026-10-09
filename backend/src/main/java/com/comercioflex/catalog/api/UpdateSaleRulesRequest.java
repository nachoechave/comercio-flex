package com.comercioflex.catalog.api;

import java.math.BigDecimal;
import com.comercioflex.catalog.domain.SaleUnit;
import com.comercioflex.catalog.domain.SaleQuantityRules;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateSaleRulesRequest(
    @NotNull SaleUnit saleUnit,
    @NotNull BigDecimal saleMinimum,
    @NotNull BigDecimal saleStep,
    @NotNull BigDecimal saleMaximum,
    @NotNull @PositiveOrZero Long version) {
    public SaleQuantityRules rules() {
        return new SaleQuantityRules(saleUnit, saleMinimum, saleStep, saleMaximum);
    }
}
