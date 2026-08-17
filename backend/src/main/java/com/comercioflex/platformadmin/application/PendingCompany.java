package com.comercioflex.platformadmin.application;

import java.util.UUID;

import com.comercioflex.platformadmin.domain.CompanyStatus;

public record PendingCompany(
	long internalId,
	UUID publicId,
	String databaseKey,
	String databaseName,
	String name,
	String administratorEmail,
	String administratorPhone,
	CompanyStatus requestedStatus) {
}
