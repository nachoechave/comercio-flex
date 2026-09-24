package com.comercioflex.order.application;

import com.comercioflex.order.domain.OrderStatus;

/** Delivery invariants applied within the existing order transaction. */
public interface OrderFulfillmentPolicy {
  void beforeTransition(LockedAdminOrder order, OrderStatus target);

  void afterTransition(LockedAdminOrder order, OrderStatus target);

  static OrderFulfillmentPolicy noop() {
    return new OrderFulfillmentPolicy() {
      public void beforeTransition(LockedAdminOrder order, OrderStatus target) {}

      public void afterTransition(LockedAdminOrder order, OrderStatus target) {}
    };
  }
}
