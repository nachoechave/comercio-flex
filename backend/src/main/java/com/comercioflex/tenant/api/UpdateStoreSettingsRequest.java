package com.comercioflex.tenant.api;

import com.comercioflex.tenant.application.UpdateStoreSettingsCommand;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

public record UpdateStoreSettingsRequest(
	@NotBlank @Size(min = 2, max = 160) String storeName,
	@Size(max = 40) @Pattern(regexp = "^$|^[+0-9][0-9 ()\\-]{6,39}$") String contactPhone,
	@Size(max = 254) @Email String contactEmail,
	@NotBlank @Size(min = 5, max = 240) String pickupAddress,
	@Size(max = 500) String pickupInstructions,
	boolean bankTransferEnabled,
	@DecimalMin("0.00")
	@DecimalMax("50.00")
	@Digits(integer = 2, fraction = 2)
	BigDecimal bankTransferDiscountPercentage,
	@Size(max = 120) String bankName,
	@Size(max = 160) String bankAccountHolder,
	@Size(max = 120) String bankAlias,
	@Size(max = 40) @Pattern(regexp = "^$|^[0-9]{6,40}$") String bankCbuCvu
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

	@AssertTrue(message = "La transferencia requiere titular y alias o CBU/CVU.")
	public boolean isBankTransferConfigurationValid() {
		return !bankTransferEnabled
			|| (hasText(bankAccountHolder) && (hasText(bankAlias) || hasText(bankCbuCvu)));
	}

	UpdateStoreSettingsCommand toCommand() {
		return new UpdateStoreSettingsCommand(
			storeName,
			contactPhone,
			contactEmail,
			pickupAddress,
			pickupInstructions,
			bankTransferEnabled,
			bankTransferDiscountPercentage,
			bankName,
			bankAccountHolder,
			bankAlias,
			bankCbuCvu);
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
