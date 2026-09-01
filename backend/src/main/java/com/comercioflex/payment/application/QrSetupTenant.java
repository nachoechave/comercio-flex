package com.comercioflex.payment.application;

import java.util.UUID;

public record QrSetupTenant(
	long id,
	UUID publicId,
	String slug) {
}
