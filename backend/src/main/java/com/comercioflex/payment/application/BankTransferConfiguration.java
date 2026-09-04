package com.comercioflex.payment.application;

import java.math.BigDecimal;

public record BankTransferConfiguration(
        boolean enabled,
        BigDecimal discountPercentage,
        String bankName,
        String accountHolder,
        String alias,
        String cbuCvu
) {
}