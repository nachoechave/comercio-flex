package com.comercioflex.inventory.api;

import java.util.UUID;

import com.comercioflex.inventory.domain.InventoryActor;

public record MovementActorResponse(UUID id, String displayName) {

	static MovementActorResponse from(InventoryActor actor) {
		return new MovementActorResponse(actor.id(), actor.displayName());
	}
}
