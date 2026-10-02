package com.comercioflex.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class BranchFulfillmentPhase4ContractTest {
    @Test
    void pickupReadyEventKeepsBranchSnapshot() {
        var names = Arrays.stream(PickupReadyEvent.class.getRecordComponents())
            .map(RecordComponent::getName).toList();
        assertThat(names).contains("orderId", "customerEmail", "branchName", "branchAddress");
    }
}
