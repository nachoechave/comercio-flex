package com.comercioflex.shipping.domain;

import java.util.UUID;

/** Explicit branch selection for PICKUP fulfillment. */
public record PickupBranchSelection(UUID pickupBranchId) {
    public PickupBranchSelection {
        if (pickupBranchId == null) {
            throw new IllegalArgumentException("La sucursal de retiro es obligatoria.");
        }
    }
}
