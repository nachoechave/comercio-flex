package com.comercioflex.platformadmin.api;

import com.comercioflex.platformadmin.application.CreateCompanyCommand;
import com.comercioflex.platformadmin.domain.CompanyStatus;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;

public record CreateCompanyRequest(
	@NotBlank @Size(max = 160) String name,
	@NotBlank @Size(max = 100)
	@Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") String slug,
	@NotBlank @Size(max = 100) String industry,
	@NotBlank @Email @Size(max = 254) String administratorEmail,
	@NotBlank @Size(max = 160) String administratorName,
	@Size(max = 40) String administratorPhone,
	@Size(max = 253)
	@Pattern(
		regexp = "^$|(?=.{1,253}$)(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,63}$")
	String domain,
	@NotBlank @Size(min = 12, max = 128) String initialPassword,
	@NotNull CompanyStatus status) {

	@AssertTrue(message = "status must be ACTIVE or INACTIVE")
	public boolean isSupportedInitialStatus() {
		return status == null || status == CompanyStatus.ACTIVE || status == CompanyStatus.INACTIVE;
	}

	CreateCompanyCommand toCommand() {
		return new CreateCompanyCommand(
			name,
			slug,
			industry,
			administratorEmail,
			administratorName,
			administratorPhone,
			domain,
			initialPassword,
			status);
	}
}
