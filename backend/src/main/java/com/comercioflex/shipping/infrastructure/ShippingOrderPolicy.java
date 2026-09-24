package com.comercioflex.shipping.infrastructure;

import com.comercioflex.order.application.*;
import com.comercioflex.order.domain.*;
import com.comercioflex.shipping.application.ShippingRepository;
import com.comercioflex.shipping.domain.ShippingModels.Status;
import org.springframework.stereotype.Component;

@Component
public class ShippingOrderPolicy implements OrderFulfillmentPolicy {
  private final ShippingRepository shipping;

  public ShippingOrderPolicy(ShippingRepository shipping) {
    this.shipping = shipping;
  }

  @Override
  public void beforeTransition(LockedAdminOrder order, OrderStatus target) {
    if (order.fulfillmentType() != FulfillmentType.SHIPPING) return;
    var shipment = shipping.shipment(order.id(), true);
    if (target == OrderStatus.CANCELLED
        && shipment != null
        && (shipment.status() == Status.SHIPPED || shipment.status() == Status.DELIVERED)) {
      throw new InvalidOrderTransitionException(
          "Un pedido despachado no puede cancelarse ni reponer stock sin una devolución.");
    }
    if (target == OrderStatus.COMPLETED
        && (shipment == null || shipment.status() != Status.DELIVERED)) {
      throw new InvalidOrderTransitionException(
          "Marcá el envío como entregado antes de completar el pedido.");
    }
  }

  @Override
  public void afterTransition(LockedAdminOrder order, OrderStatus target) {
    if (order.fulfillmentType() == FulfillmentType.SHIPPING
        && (target == OrderStatus.CANCELLED || target == OrderStatus.REJECTED)) {
      shipping.cancelPending(order.id());
    }
  }
}
