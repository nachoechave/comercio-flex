package com.comercioflex.order.application;

import com.comercioflex.order.domain.OrderPaymentMethod;
import java.util.List;
import java.util.UUID;

public record CreateGuestOrderCommand(
    UUID idempotencyKey,
    String customerName,
    String customerPhone,
    String customerEmail,
    String notes,
    OrderPaymentMethod paymentMethod,
    List<OrderItemCommand> items,
    com.comercioflex.shipping.domain.ShippingModels.Selection shipping) {
  public CreateGuestOrderCommand(
      UUID idempotencyKey,
      String customerName,
      String customerPhone,
      String customerEmail,
      String notes,
      OrderPaymentMethod paymentMethod,
      List<OrderItemCommand> items) {
    this(
        idempotencyKey,
        customerName,
        customerPhone,
        customerEmail,
        notes,
        paymentMethod,
        items,
        null);
  }
}
