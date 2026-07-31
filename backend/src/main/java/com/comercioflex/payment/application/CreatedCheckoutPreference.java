package com.comercioflex.payment.application;

import java.net.URI;

public record CreatedCheckoutPreference(
	String preferenceId,
	URI checkoutUri,
	String collectorAccountId) {
}
