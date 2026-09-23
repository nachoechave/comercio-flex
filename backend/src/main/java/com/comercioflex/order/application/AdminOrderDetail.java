package com.comercioflex.order.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.comercioflex.order.domain.FulfillmentType;
import com.comercioflex.order.domain.GuestOrderItem;
import com.comercioflex.order.domain.OrderPaymentMethod;
import com.comercioflex.order.domain.OrderStatus;

public record AdminOrderDetail(
        UUID id,
        long number,
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
        long version,
        List<GuestOrderItem> items,
        List<OrderHistoryEntry> history,
BigDecimal shippingAmount, com.comercioflex.shipping.domain.ShippingModels.Snapshot shipping) {
 public AdminOrderDetail(UUID id,
        long number,
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
        long version,
        List<GuestOrderItem> items,
        List<OrderHistoryEntry> history) { this(id,number,status,fulfillmentType,paymentMethod,customerName,customerPhone,customerEmail,notes,currencyCode,listSubtotal,discountPercentage,discountAmount,subtotal,reservationExpiresAt,createdAt,version,items,history,BigDecimal.ZERO.setScale(2),null); }
 public BigDecimal total() { return subtotal.add(shippingAmount); }

}