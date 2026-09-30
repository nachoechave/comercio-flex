package com.comercioflex.pos.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.comercioflex.inventory.domain.InventoryActor;
import com.comercioflex.pos.application.PosSaleService.Failure;
import com.comercioflex.pos.application.PosSaleService.PosSaleException;

@Service
public class CashSessionService {

	private final JdbcTemplate jdbcTemplate;
	private final TransactionTemplate transactionTemplate;

	public CashSessionService(
			@Qualifier("tenantJdbcTemplate") JdbcTemplate jdbcTemplate,
			@Qualifier("tenantTransactionTemplate") TransactionTemplate transactionTemplate) {
		this.jdbcTemplate = jdbcTemplate;
		this.transactionTemplate = transactionTemplate;
	}

	public CashSessionView current(UUID branchId) {
		if (branchId == null) throw invalid("La sucursal es obligatoria.");
		return transactionTemplate.execute(ignored -> findOpenByBranch(branchId));
	}

	public List<CashSessionView> list(UUID branchId, int limit) {
		int safeLimit = Math.max(1, Math.min(limit, 100));
		return transactionTemplate.execute(ignored -> {
			String filter = branchId == null ? "" : " WHERE branch.public_id = UUID_TO_BIN(?)";
			String sql = """
				SELECT session.id, BIN_TO_UUID(session.public_id) public_id,
					BIN_TO_UUID(branch.public_id) branch_public_id, branch.name branch_name,
					session.opened_by_display_name, session.opening_amount, session.opened_at,
					session.closed_by_display_name, session.closing_amount,
					session.expected_cash, session.difference_amount, session.closed_at, session.status
				FROM cash_sessions session
				JOIN store_branches branch ON branch.id = session.branch_id
				""" + filter + """
				ORDER BY session.opened_at DESC, session.id DESC
				LIMIT ?
				""";
			Object[] params = branchId == null
				? new Object[] { safeLimit }
				: new Object[] { branchId.toString(), safeLimit };
			return jdbcTemplate.query(sql, (rs, rowNum) -> mapSession(rs), params);
		});
	}

	public CashSessionView open(UUID branchId, BigDecimal rawOpeningAmount, InventoryActor actor) {
		if (branchId == null) throw invalid("La sucursal es obligatoria.");
		BigDecimal openingAmount = money(rawOpeningAmount, "El saldo inicial");
		return transactionTemplate.execute(ignored -> {
			Branch branch = lockBranch(branchId);
			if (!branch.active()) throw invalid("No se puede abrir caja en una sucursal inactiva.");
			Long openCount = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM cash_sessions
				WHERE branch_id = ? AND status = 'OPEN'
				""", Long.class, branch.internalId());
			if (openCount != null && openCount > 0) {
				throw conflict("Ya hay una caja abierta en " + branch.name() + ".");
			}

			UUID sessionId = UUID.randomUUID();
			jdbcTemplate.update("""
				INSERT INTO cash_sessions (
					public_id, branch_id, opened_by_public_id,
					opened_by_display_name, opening_amount, status
				) VALUES (UUID_TO_BIN(?), ?, UUID_TO_BIN(?), ?, ?, 'OPEN')
				""", sessionId.toString(), branch.internalId(), actor.id().toString(),
				actor.displayName(), openingAmount);
			return requireSession(sessionId, false);
		});
	}

	public CashSessionView close(UUID sessionId, BigDecimal rawClosingAmount, InventoryActor actor) {
		if (sessionId == null) throw invalid("La caja es obligatoria.");
		BigDecimal closingAmount = money(rawClosingAmount, "El efectivo contado");
		return transactionTemplate.execute(ignored -> {
			CashSessionLock session = lockSession(sessionId);
			if (!"OPEN".equals(session.status())) {
				throw conflict("La caja ya fue cerrada.");
			}

			BigDecimal cashSales = jdbcTemplate.queryForObject("""
				SELECT COALESCE(SUM(sale.subtotal), 0.00)
				FROM pos_sales sale
				WHERE sale.branch_id = ?
					AND sale.payment_method = 'CASH'
					AND sale.created_at >= ?
					AND sale.created_at <= UTC_TIMESTAMP(6)
				""", BigDecimal.class, session.branchInternalId(),
				java.sql.Timestamp.from(session.openedAt()));
		if (cashSales == null) cashSales = BigDecimal.ZERO.setScale(2);
		BigDecimal expected = session.openingAmount().add(cashSales).setScale(2, RoundingMode.UNNECESSARY);
		BigDecimal difference = closingAmount.subtract(expected).setScale(2, RoundingMode.UNNECESSARY);

			jdbcTemplate.update("""
				UPDATE cash_sessions
				SET closed_by_public_id = UUID_TO_BIN(?),
					closed_by_display_name = ?, closing_amount = ?, expected_cash = ?,
					difference_amount = ?, closed_at = UTC_TIMESTAMP(6), status = 'CLOSED'
				WHERE id = ? AND status = 'OPEN'
				""", actor.id().toString(), actor.displayName(), closingAmount,
				expected, difference, session.internalId());
			return requireSession(sessionId, false);
		});
	}

	private CashSessionView findOpenByBranch(UUID branchId) {
		return jdbcTemplate.query("""
			SELECT session.id, BIN_TO_UUID(session.public_id) public_id,
				BIN_TO_UUID(branch.public_id) branch_public_id, branch.name branch_name,
				session.opened_by_display_name, session.opening_amount, session.opened_at,
				session.closed_by_display_name, session.closing_amount,
				session.expected_cash, session.difference_amount, session.closed_at, session.status
			FROM cash_sessions session
			JOIN store_branches branch ON branch.id = session.branch_id
			WHERE branch.public_id = UUID_TO_BIN(?) AND session.status = 'OPEN'
			ORDER BY session.opened_at DESC, session.id DESC
			LIMIT 1
			""", (rs, rowNum) -> mapSession(rs), branchId.toString()).stream().findFirst().orElse(null);
	}

	private CashSessionView requireSession(UUID sessionId, boolean lock) {
		String suffix = lock ? " FOR UPDATE" : "";
		return jdbcTemplate.query("""
			SELECT session.id, BIN_TO_UUID(session.public_id) public_id,
				BIN_TO_UUID(branch.public_id) branch_public_id, branch.name branch_name,
				session.opened_by_display_name, session.opening_amount, session.opened_at,
				session.closed_by_display_name, session.closing_amount,
				session.expected_cash, session.difference_amount, session.closed_at, session.status
			FROM cash_sessions session
			JOIN store_branches branch ON branch.id = session.branch_id
			WHERE session.public_id = UUID_TO_BIN(?)
			""" + suffix, (rs, rowNum) -> mapSession(rs), sessionId.toString())
			.stream().findFirst().orElseThrow(() -> notFound("La caja no existe."));
	}

	private CashSessionLock lockSession(UUID sessionId) {
		return jdbcTemplate.query("""
			SELECT session.id, session.branch_id, session.opening_amount,
				session.opened_at, session.status
			FROM cash_sessions session
			WHERE session.public_id = UUID_TO_BIN(?) FOR UPDATE
			""", (rs, rowNum) -> new CashSessionLock(
			rs.getLong("id"), rs.getLong("branch_id"),
			rs.getBigDecimal("opening_amount"), rs.getTimestamp("opened_at").toInstant(),
			rs.getString("status")), sessionId.toString()).stream().findFirst()
			.orElseThrow(() -> notFound("La caja no existe."));
	}

	private Branch lockBranch(UUID branchId) {
		return jdbcTemplate.query("""
			SELECT id, name, active FROM store_branches
			WHERE public_id = UUID_TO_BIN(?) FOR UPDATE
			""", (rs, rowNum) -> new Branch(
			rs.getLong("id"), rs.getString("name"), rs.getBoolean("active")),
			branchId.toString()).stream().findFirst()
			.orElseThrow(() -> notFound("La sucursal no existe."));
	}

	private CashSessionView mapSession(java.sql.ResultSet rs) throws java.sql.SQLException {
		var closedAt = rs.getTimestamp("closed_at");
		return new CashSessionView(
			UUID.fromString(rs.getString("public_id")),
			UUID.fromString(rs.getString("branch_public_id")),
			rs.getString("branch_name"), rs.getString("status"),
			rs.getString("opened_by_display_name"), rs.getBigDecimal("opening_amount"),
			rs.getTimestamp("opened_at").toInstant(), rs.getString("closed_by_display_name"),
			rs.getBigDecimal("closing_amount"), rs.getBigDecimal("expected_cash"),
			rs.getBigDecimal("difference_amount"), closedAt == null ? null : closedAt.toInstant());
	}

	private BigDecimal money(BigDecimal value, String label) {
		if (value == null) throw invalid(label + " es obligatorio.");
		try {
			BigDecimal normalized = value.setScale(2, RoundingMode.UNNECESSARY);
			if (normalized.signum() < 0) throw invalid(label + " no puede ser negativo.");
			return normalized;
		}
		catch (ArithmeticException exception) {
			throw invalid(label + " admite como máximo dos decimales.");
		}
	}

	private PosSaleException invalid(String message) {
		return new PosSaleException(Failure.INVALID, message);
	}

	private PosSaleException conflict(String message) {
		return new PosSaleException(Failure.CONFLICT, message);
	}

	private PosSaleException notFound(String message) {
		return new PosSaleException(Failure.NOT_FOUND, message);
	}

	public record CashSessionView(
		UUID id,
		UUID branchId,
		String branchName,
		String status,
		String openedByDisplayName,
		BigDecimal openingAmount,
		Instant openedAt,
		String closedByDisplayName,
		BigDecimal closingAmount,
		BigDecimal expectedCash,
		BigDecimal differenceAmount,
		Instant closedAt) {}

	private record Branch(long internalId, String name, boolean active) {}
	private record CashSessionLock(
		long internalId,
		long branchInternalId,
		BigDecimal openingAmount,
		Instant openedAt,
		String status) {}
}
