package com.comercioflex.shipping.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PickupBranchSelectionTest {
    @Test
    void requiresBranch() {
        assertThatThrownBy(() -> new PickupBranchSelection(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("sucursal");
    }
}
