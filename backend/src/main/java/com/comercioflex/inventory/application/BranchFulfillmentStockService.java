package com.comercioflex.inventory.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class BranchFulfillmentStockService {
    private final JdbcTemplate jdbcTemplate;
    public BranchFulfillmentStockService(@Qualifier("tenantJdbcTemplate") JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public Branch resolveActive(UUID publicId, boolean lock) {
        String filter = publicId == null ? "is_default = TRUE" : "public_id = UUID_TO_BIN(?)";
        String sql = "SELECT id, BIN_TO_UUID(public_id) public_id, name, address FROM store_branches WHERE " + filter + " AND active = TRUE LIMIT 1" + (lock ? " FOR UPDATE" : "");
        List<Branch> branches = publicId == null ? jdbcTemplate.query(sql, (rs, rowNum) -> map(rs)) : jdbcTemplate.query(sql, (rs, rowNum) -> map(rs), publicId.toString());
        if (branches.isEmpty()) throw new IllegalArgumentException("La sucursal de retiro no existe o está inactiva.");
        return branches.get(0);
    }

    public BigDecimal available(UUID branchPublicId, long variantInternalId) {
        return available(resolveActive(branchPublicId, false).internalId(), variantInternalId);
    }

    public BigDecimal available(long branchInternalId, long variantInternalId) {
        BigDecimal quantity = jdbcTemplate.queryForObject("SELECT COALESCE(quantity, 0.000) FROM branch_inventory_balances WHERE branch_id = ? AND variant_id = ?", BigDecimal.class, branchInternalId, variantInternalId);
        return quantity == null ? BigDecimal.ZERO : quantity;
    }

    public void applyDelta(UUID branchPublicId, long variantInternalId, BigDecimal delta) {
        applyDelta(resolveActive(branchPublicId, true).internalId(), variantInternalId, delta);
    }

    public void applyDelta(long branchInternalId, long variantInternalId, BigDecimal delta) {
        jdbcTemplate.update("INSERT IGNORE INTO branch_inventory_balances (branch_id, variant_id, quantity) VALUES (?, ?, 0.000)", branchInternalId, variantInternalId);
        BigDecimal before = jdbcTemplate.queryForObject("SELECT quantity FROM branch_inventory_balances WHERE branch_id = ? AND variant_id = ? FOR UPDATE", BigDecimal.class, branchInternalId, variantInternalId);
        BigDecimal after = before.add(delta);
        if (after.signum() < 0) throw new InsufficientStockException();
        jdbcTemplate.update("UPDATE branch_inventory_balances SET quantity = ?, version = version + 1 WHERE branch_id = ? AND variant_id = ?", after, branchInternalId, variantInternalId);
    }

    private static Branch map(java.sql.ResultSet rs) throws java.sql.SQLException { return new Branch(rs.getLong("id"), UUID.fromString(rs.getString("public_id")), rs.getString("name"), rs.getString("address")); }
    public record Branch(long internalId, UUID id, String name, String address) {}
}
