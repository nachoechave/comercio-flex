package com.comercioflex.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class BranchFulfillmentStockServiceContractTest {
    @Test
    void exposesBranchSpecificAvailabilityAndMutation() {
        var methods = Arrays.stream(BranchFulfillmentStockService.class.getDeclaredMethods())
            .map(Method::getName).toList();
        assertThat(methods).contains("resolveActive", "available", "applyDelta");
    }
}
