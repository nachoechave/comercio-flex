package com.comercioflex.payment.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.comercioflex.order.domain.OrderPaymentMethod;
import com.comercioflex.order.domain.OrderStatus;

public record LockedPaymentOrder(
        long internalId,
        UUID id,
        OrderStatus status,
        OrderPaymentMethod paymentMethod,
        BigDecimal amount,
        String currencyCode,
        Instant reservationExpiresAt) {
}