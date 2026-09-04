package com.comercioflex.tenant.application;

import java.math.BigDecimal;

public record UpdateStoreSettingsCommand(
        String storeName,
        String contactPhone,
        String contactEmail,
        String pickupAddress,
        String pickupInstructions,
        boolean bankTransferEnabled,
        BigDecimal bankTransferDiscountPercentage,
        String bankName,
        String bankAccountHolder,
        String bankAlias,
        String bankCbuCvu
) {
}