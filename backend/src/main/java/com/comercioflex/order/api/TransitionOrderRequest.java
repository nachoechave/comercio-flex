package com.comercioflex.order.api;

import com.comercioflex.order.domain.OrderStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransitionOrderRequest(
	@NotNull OrderStatus targetStatus,
	@Size(max = 500) String note) {
}
