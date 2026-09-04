package com.comercioflex.order.application;

import java.math.BigDecimal;

public record OrderPaymentPricing(
        boolean bankTransferEnabled,
        BigDecimal bankTransferDiscountPercentage
) {
}