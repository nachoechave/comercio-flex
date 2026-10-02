package com.comercioflex.order.application;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.comercioflex.inventory.application.BranchFulfillmentStockService;
import com.comercioflex.inventory.application.InsufficientStockException;

/** Validates physical branch stock minus active reservations for the same branch. */
@Component
public class BranchReservationStockGuard {
    private final JdbcTemplate jdbcTemplate;
    private final BranchFulfillmentStockService stock;

    public BranchReservationStockGuard(
            @Qualifier("tenantJdbcTemplate") JdbcTemplate jdbcTemplate,
            BranchFulfillmentStockService stock) {
        this.jdbcTemplate = jdbcTemplate;
        this.stock = stock;
    }

    public void requireAvailable(UUID branchId, long variantInternalId, BigDecimal requested) {
        var branch = stock.resolveActive(branchId, true);
        BigDecimal physical = stock.available(branch.id(), variantInternalId);
        BigDecimal reserved = jdbcTemplate.queryForObject("""
            SELECT COALESCE(SUM(quantity), 0.000)
            FROM inventory_reservations
            WHERE variant_id = ? AND branch_id = ? AND status = 'ACTIVE' AND expires_at > CURRENT_TIMESTAMP(6)
            """, BigDecimal.class, variantInternalId, branch.internalId());
        if (physical.subtract(reserved == null ? BigDecimal.ZERO : reserved).compareTo(requested) < 0) {
            throw new InsufficientStockException();
        }
    }
}
