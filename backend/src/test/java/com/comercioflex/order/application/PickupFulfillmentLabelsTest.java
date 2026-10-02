package com.comercioflex.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.comercioflex.shipping.domain.ShippingModels.Status;

class PickupFulfillmentLabelsTest {
    @Test
    void mapsExistingShipmentStatesToPickupLanguage() {
        assertThat(PickupFulfillmentLabels.label(Status.PENDING)).isEqualTo("Pendiente");
        assertThat(PickupFulfillmentLabels.label(Status.PREPARING)).isEqualTo("Preparando");
        assertThat(PickupFulfillmentLabels.label(Status.SHIPPED)).isEqualTo("Listo para retirar");
        assertThat(PickupFulfillmentLabels.label(Status.DELIVERED)).isEqualTo("Entregado");
        assertThat(PickupFulfillmentLabels.label(Status.CANCELLED)).isEqualTo("Cancelado");
    }
}
