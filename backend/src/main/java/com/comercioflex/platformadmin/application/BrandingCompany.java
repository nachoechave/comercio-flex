package com.comercioflex.platformadmin.application;

import java.util.UUID;

public record BrandingCompany(
	long internalId,
	UUID publicId,
	String slug,
	String databaseKey) {
}
