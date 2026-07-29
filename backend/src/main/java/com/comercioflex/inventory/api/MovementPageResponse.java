package com.comercioflex.inventory.api;

import java.util.List;

import com.comercioflex.inventory.application.MovementPage;

public record MovementPageResponse(
	List<MovementResponse> items,
	int page,
	int size,
	long totalItems,
	long totalPages) {

	static MovementPageResponse from(MovementPage page) {
		return new MovementPageResponse(
			page.items().stream().map(MovementResponse::from).toList(),
			page.page(),
			page.size(),
			page.totalItems(),
			page.totalPages());
	}
}
