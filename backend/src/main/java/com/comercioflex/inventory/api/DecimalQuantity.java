package com.comercioflex.inventory.api;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class DecimalQuantity {

	private DecimalQuantity() {
	}

	static String format(BigDecimal value) {
		return value.setScale(3, RoundingMode.UNNECESSARY).toPlainString();
	}
}
