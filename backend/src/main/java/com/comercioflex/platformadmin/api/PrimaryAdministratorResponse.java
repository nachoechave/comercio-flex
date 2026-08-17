package com.comercioflex.platformadmin.api;

import com.comercioflex.platformadmin.domain.PrimaryAdministrator;

public record PrimaryAdministratorResponse(String name, String email) {

	static PrimaryAdministratorResponse from(PrimaryAdministrator administrator) {
		return administrator == null
			? null
			: new PrimaryAdministratorResponse(administrator.name(), administrator.email());
	}
}
