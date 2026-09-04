package com.comercioflex.order.application;

import java.util.List;
import java.util.UUID;

import com.comercioflex.order.domain.OrderPaymentMethod;

public record CreateGuestOrderCommand(
        UUID idempotencyKey,
        String customerName,
        String customerPhone,
        String customerEmail,
        String notes,
        OrderPaymentMethod paymentMethod,
        List<OrderItemCommand> items) {
}