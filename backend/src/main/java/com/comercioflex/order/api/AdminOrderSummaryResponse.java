package com.comercioflex.order.api;

import java.time.Instant;
import java.util.UUID;

import com.comercioflex.order.application.AdminOrderSummary;
import com.comercioflex.order.domain.FulfillmentType;
import com.comercioflex.order.domain.OrderPaymentMethod;
import com.comercioflex.order.domain.OrderStatus;

public record AdminOrderSummaryResponse(
        UUID id,
        String number,
        OrderStatus status,
        FulfillmentType fulfillmentType,
        OrderPaymentMethod paymentMethod,
        String customerName,
        String customerPhone,
        String currencyCode,
        String listSubtotal,
        String discountPercentage,
        String discountAmount,
        String subtotal,
        Instant createdAt) {

        static AdminOrderSummaryResponse from(AdminOrderSummary order) {
                return new AdminOrderSummaryResponse(
                        order.id(),
                        "ORD-%06d".formatted(order.number()),
                        order.status(),
                        order.fulfillmentType(),
                        order.paymentMethod(),
                        order.customerName(),
                        order.customerPhone(),
                        order.currencyCode(),
                        order.listSubtotal().toPlainString(),
                        order.discountPercentage().toPlainString(),
                        order.discountAmount().toPlainString(),
                        order.subtotal().toPlainString(),
                        order.createdAt());
        }
}