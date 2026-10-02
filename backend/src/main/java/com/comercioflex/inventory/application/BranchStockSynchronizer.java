package com.comercioflex.inventory.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class BranchStockSynchronizer {

	private final JdbcTemplate jdbcTemplate;

	public BranchStockSynchronizer(
			@Qualifier("tenantJdbcTemplate") JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void applyDefaultDelta(long variantInternalId, BigDecimal delta) {
		Long branchId = jdbcTemplate.queryForObject("""
			SELECT id FROM store_branches WHERE is_default = TRUE AND active = TRUE LIMIT 1 FOR UPDATE
			""", Long.class);
		if (branchId == null) {
			throw new IllegalStateException("No existe una sucursal principal activa.");
		}
		jdbcTemplate.update("""
			INSERT IGNORE INTO branch_inventory_balances (branch_id, variant_id, quantity)
			SELECT ?, ?, COALESCE(balance.quantity, 0.000)
			FROM (SELECT 1) seed
			LEFT JOIN inventory_balances balance ON balance.variant_id = ?
			""", branchId, variantInternalId, variantInternalId);
		BigDecimal before = jdbcTemplate.queryForObject("""
			SELECT quantity FROM branch_inventory_balances
			WHERE branch_id = ? AND variant_id = ? FOR UPDATE
			""", BigDecimal.class, branchId, variantInternalId);
		BigDecimal after = before.add(delta);
		if (after.signum() < 0) {
			throw new InsufficientStockException();
		}
		jdbcTemplate.update("""
			UPDATE branch_inventory_balances
			SET quantity = ?, version = version + 1
			WHERE branch_id = ? AND variant_id = ?
			""", after, branchId, variantInternalId);
	}

	public BigDecimal findDefaultAvailable(long variantInternalId) {
		BigDecimal quantity = jdbcTemplate.queryForObject("""
			SELECT COALESCE(branch_balance.quantity, aggregate_balance.quantity, 0.000)
			FROM store_branches branch
			LEFT JOIN branch_inventory_balances branch_balance
				ON branch_balance.branch_id = branch.id AND branch_balance.variant_id = ?
			LEFT JOIN inventory_balances aggregate_balance
				ON aggregate_balance.variant_id = ?
			WHERE branch.is_default = TRUE AND branch.active = TRUE
			LIMIT 1
			""", BigDecimal.class, variantInternalId, variantInternalId);
		return quantity == null ? BigDecimal.ZERO : quantity;
	}

	public BigDecimal findBranchAvailable(UUID branchPublicId, long variantInternalId) {
		long branchId = resolveBranch(branchPublicId, false);
		BigDecimal quantity = jdbcTemplate.queryForObject("""
			SELECT COALESCE(balance.quantity, 0.000)
			FROM store_branches branch
			LEFT JOIN branch_inventory_balances balance
				ON balance.branch_id = branch.id AND balance.variant_id = ?
			WHERE branch.id = ?
			LIMIT 1
			""", BigDecimal.class, variantInternalId, branchId);
		return quantity == null ? BigDecimal.ZERO : quantity;
	}

	public void applyBranchDelta(UUID branchPublicId, long variantInternalId, BigDecimal delta) {
		long branchId = resolveBranch(branchPublicId, true);
		jdbcTemplate.update("""
			INSERT IGNORE INTO branch_inventory_balances (branch_id, variant_id, quantity)
			VALUES (?, ?, 0.000)
			""", branchId, variantInternalId);
		BigDecimal before = jdbcTemplate.queryForObject("""
			SELECT quantity FROM branch_inventory_balances
			WHERE branch_id = ? AND variant_id = ? FOR UPDATE
			""", BigDecimal.class, branchId, variantInternalId);
		BigDecimal after = before.add(delta);
		if (after.signum() < 0) {
			throw new InsufficientStockException();
		}
		jdbcTemplate.update("""
			UPDATE branch_inventory_balances
			SET quantity = ?, version = version + 1
			WHERE branch_id = ? AND variant_id = ?
			""", after, branchId, variantInternalId);
	}

	private long resolveBranch(UUID branchPublicId, boolean lock) {
		if (branchPublicId == null) {
			throw new IllegalArgumentException("La sucursal es obligatoria.");
		}
		String sql = """
			SELECT id FROM store_branches
			WHERE public_id = UUID_TO_BIN(?) AND active = TRUE
			LIMIT 1
			""" + (lock ? " FOR UPDATE" : "");
		List<Long> ids = jdbcTemplate.query(
			sql,
			(rs, rowNum) -> rs.getLong("id"),
			branchPublicId.toString());
		if (ids.isEmpty()) {
			throw new IllegalArgumentException("La sucursal de retiro no existe o está inactiva.");
		}
		return ids.get(0);
	}
}
