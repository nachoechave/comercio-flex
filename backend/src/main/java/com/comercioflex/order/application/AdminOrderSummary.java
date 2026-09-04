package com.comercioflex.order.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.comercioflex.order.domain.FulfillmentType;
import com.comercioflex.order.domain.OrderPaymentMethod;
import com.comercioflex.order.domain.OrderStatus;

public record AdminOrderSummary(
        UUID id,
        long number,
        OrderStatus status,
        FulfillmentType fulfillmentType,
        OrderPaymentMethod paymentMethod,
        String customerName,
        String customerPhone,
        String currencyCode,
        BigDecimal listSubtotal,
        BigDecimal discountPercentage,
        BigDecimal discountAmount,
        BigDecimal subtotal,
        Instant createdAt) {
}