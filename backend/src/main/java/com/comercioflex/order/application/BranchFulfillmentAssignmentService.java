package com.comercioflex.order.application;

import com.comercioflex.inventory.application.BranchFulfillmentStockService;
import com.comercioflex.inventory.application.BranchFulfillmentStockService.Branch;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class BranchFulfillmentAssignmentService {

  private final JdbcTemplate jdbcTemplate;
  private final BranchFulfillmentStockService stock;

  public BranchFulfillmentAssignmentService(
      @Qualifier("tenantJdbcTemplate") JdbcTemplate jdbcTemplate,
      BranchFulfillmentStockService stock) {
    this.jdbcTemplate = jdbcTemplate;
    this.stock = stock;
  }

  public UUID resolveForOrderPlaceholder() {
    return stock.resolveActive(null, true).id();
  }

  public Branch assign(long orderInternalId, UUID requestedBranchId) {
    Branch branch = stock.resolveActive(requestedBranchId, true);
    jdbcTemplate.update(
        "UPDATE orders SET fulfillment_branch_id = ? WHERE id = ?",
        branch.internalId(),
        orderInternalId);
    jdbcTemplate.update(
        "UPDATE inventory_reservations SET branch_id = ? WHERE order_id = ?",
        branch.internalId(),
        orderInternalId);
    return branch;
  }

  public Optional<Branch> findAssignedForOrder(long orderInternalId, boolean lock) {
    String suffix = lock ? " FOR UPDATE" : "";
    var rows =
        jdbcTemplate.query(
            """
            SELECT branch.id, BIN_TO_UUID(branch.public_id) public_id, branch.name, branch.address
            FROM orders orders
            JOIN store_branches branch ON branch.id = orders.fulfillment_branch_id
            WHERE orders.id = ? AND branch.active = TRUE
            """
                + suffix,
            (rs, rowNum) ->
                new Branch(
                    rs.getLong("id"),
                    UUID.fromString(rs.getString("public_id")),
                    rs.getString("name"),
                    rs.getString("address")),
            orderInternalId);
    return rows.stream().findFirst();
  }

  public Branch resolveForOrder(long orderInternalId, boolean lock) {
    String suffix = lock ? " FOR UPDATE" : "";
    var rows =
        jdbcTemplate.query(
            """
            SELECT branch.id, BIN_TO_UUID(branch.public_id) public_id, branch.name, branch.address
            FROM orders orders
            LEFT JOIN store_branches selected ON selected.id = orders.fulfillment_branch_id
            JOIN store_branches branch ON branch.id = COALESCE(
              selected.id,
              (SELECT fallback.id
               FROM store_branches fallback
               WHERE fallback.is_default = TRUE AND fallback.active = TRUE
               LIMIT 1))
            WHERE orders.id = ? AND branch.active = TRUE
            """
                + suffix,
            (rs, rowNum) ->
                new Branch(
                    rs.getLong("id"),
                    UUID.fromString(rs.getString("public_id")),
                    rs.getString("name"),
                    rs.getString("address")),
            orderInternalId);
    if (rows.isEmpty()) {
      throw new IllegalStateException("No se pudo resolver la sucursal del pedido.");
    }
    return rows.get(0);
  }
}
