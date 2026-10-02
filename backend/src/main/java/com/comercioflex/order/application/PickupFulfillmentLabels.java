package com.comercioflex.order.application;

import com.comercioflex.shipping.domain.ShippingModels.Status;

public final class PickupFulfillmentLabels {
    private PickupFulfillmentLabels() {}

    public static String label(Status status) {
        return switch (status) {
            case PENDING -> "Pendiente";
            case PREPARING -> "Preparando";
            case SHIPPED -> "Listo para retirar";
            case DELIVERED -> "Entregado";
            case CANCELLED -> "Cancelado";
        };
    }
}
