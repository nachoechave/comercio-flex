package com.comercioflex.order.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GuestOrder(
        UUID id,
        long orderNumber,
        OrderStatus status,
        FulfillmentType fulfillmentType,
        OrderPaymentMethod paymentMethod,
        String customerName,
        String customerPhone,
        String customerEmail,
        String notes,
        String currencyCode,
        BigDecimal listSubtotal,
        BigDecimal discountPercentage,
        BigDecimal discountAmount,
        BigDecimal subtotal,
        Instant reservationExpiresAt,
        Instant createdAt,
        List<GuestOrderItem> items,
BigDecimal shippingAmount, com.comercioflex.shipping.domain.ShippingModels.Snapshot shipping) {
 public GuestOrder(UUID id,
        long orderNumber,
        OrderStatus status,
        FulfillmentType fulfillmentType,
        OrderPaymentMethod paymentMethod,
        String customerName,
        String customerPhone,
        String customerEmail,
        String notes,
        String currencyCode,
        BigDecimal listSubtotal,
        BigDecimal discountPercentage,
        BigDecimal discountAmount,
        BigDecimal subtotal,
        Instant reservationExpiresAt,
        Instant createdAt,
        List<GuestOrderItem> items) { this(id,orderNumber,status,fulfillmentType,paymentMethod,customerName,customerPhone,customerEmail,notes,currencyCode,listSubtotal,discountPercentage,discountAmount,subtotal,reservationExpiresAt,createdAt,items,BigDecimal.ZERO.setScale(2),null); }
 public BigDecimal total() { return subtotal.add(shippingAmount); }

}