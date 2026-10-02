package com.comercioflex.shipping.api;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores/{storeSlug}/shipping/pickup-branches")
public class PickupBranchController {
    private final JdbcTemplate jdbcTemplate;

    public PickupBranchController(@Qualifier("tenantJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    ResponseEntity<List<PickupBranch>> list(jakarta.servlet.http.HttpServletRequest request) {
        ShippingAccess.requireEcommerce(request);
        List<PickupBranch> branches = jdbcTemplate.query("""
            SELECT BIN_TO_UUID(public_id) public_id, name, address
            FROM store_branches
            WHERE active = TRUE
            ORDER BY is_default DESC, name ASC
            """, (rs, rowNum) -> new PickupBranch(
                UUID.fromString(rs.getString("public_id")), rs.getString("name"), rs.getString("address")));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(branches);
    }

    public record PickupBranch(UUID id, String name, String address) {}
}
