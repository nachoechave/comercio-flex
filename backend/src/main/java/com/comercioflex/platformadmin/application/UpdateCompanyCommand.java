package com.comercioflex.platformadmin.application;

public record UpdateCompanyCommand(
	String name,
	String industry,
	String phone,
	String domain) {
}
