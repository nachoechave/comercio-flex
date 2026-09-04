package com.comercioflex.order.api;

import java.util.List;
import com.comercioflex.order.domain.OrderPaymentMethod;

import jakarta.validation.constraints.NotNull;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record CreateGuestOrderRequest(
	@NotBlank @Size(max = 160) String customerName,
	@NotBlank @Size(max = 40) String customerPhone,
	@NotBlank @Email @Size(max = 254) String customerEmail,
	@Size(max = 1000) String notes,
	@NotNull OrderPaymentMethod paymentMethod,
	@NotEmpty @Size(max = 50) List<@Valid CreateGuestOrderItemRequest> items) {
}
