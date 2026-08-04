package com.comercioflex.tenant.api;

import com.comercioflex.tenant.application.UpdateStoreSettingsCommand;
import com.comercioflex.tenant.domain.BrandTheme;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateStoreSettingsRequest(
	@NotBlank @Size(min = 2, max = 160) String storeName,
	@Size(max = 40) @Pattern(regexp = "^$|^[+0-9][0-9 ()\\-]{6,39}$") String contactPhone,
	@Size(max = 254) @Email String contactEmail,
	@NotBlank @Size(min = 5, max = 240) String pickupAddress,
	@Size(max = 500) String pickupInstructions,
	@NotNull BrandTheme brandTheme
) {
	@AssertTrue(message = "Debe indicar al menos un teléfono o correo de contacto.")
	public boolean isContactProvided() {
		return hasText(contactPhone) || hasText(contactEmail);
	}

	@AssertTrue(message = "El nombre y la dirección deben respetar sus longitudes.")
	public boolean isTrimmedLengthValid() {
		return trimmedLengthBetween(storeName, 2, 160)
			&& trimmedLengthBetween(pickupAddress, 5, 240);
	}

	UpdateStoreSettingsCommand toCommand() {
		return new UpdateStoreSettingsCommand(storeName, contactPhone, contactEmail,
			pickupAddress, pickupInstructions, brandTheme);
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static boolean trimmedLengthBetween(String value, int minimum, int maximum) {
		if (value == null) return false;
		int length = value.trim().length();
		return length >= minimum && length <= maximum;
	}
}
