package com.comercioflex.payment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectBankTransferRequest(
	@NotBlank @Size(max = 500) String reason
) {
}
