package com.comercioflex.order.application;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderStockLine(
	long variantInternalId,
	UUID variantId,
	BigDecimal quantity) {
}
