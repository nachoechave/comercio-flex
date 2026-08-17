package com.comercioflex.platformadmin.api;

import com.comercioflex.platformadmin.application.UpdateCompanyCommand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateCompanyRequest(
	@NotBlank @Size(max = 160) String name,
	@NotBlank @Size(max = 100) String industry,
	@Size(max = 40) String phone,
	@Size(max = 253)
	@Pattern(
		regexp = "^$|(?=.{1,253}$)(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,63}$")
	String domain) {

	UpdateCompanyCommand toCommand() {
		return new UpdateCompanyCommand(name, industry, phone, domain);
	}
}
