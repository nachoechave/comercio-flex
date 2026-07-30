package com.comercioflex.order.application;

import java.math.BigDecimal;
import java.util.UUID;

public record ReservedOrderItem(
		LockedOrderVariant variant,
		UUID reservationId,
		BigDecimal quantity,
		BigDecimal lineTotal) {
}

