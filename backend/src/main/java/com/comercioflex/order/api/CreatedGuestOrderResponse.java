package com.comercioflex.order.api;

import com.comercioflex.order.application.GuestOrderCreation;

public record CreatedGuestOrderResponse(
	GuestOrderResponse order,
	String lookupToken,
	boolean replayed) {

	static CreatedGuestOrderResponse from(GuestOrderCreation creation) {
		return new CreatedGuestOrderResponse(
			GuestOrderResponse.from(creation.order()),
			creation.lookupToken(),
			creation.replayed());
	}
}
