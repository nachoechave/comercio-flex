package com.comercioflex.order.infrastructure.jdbc;

import java.util.List;

import org.springframework.stereotype.Component;

import com.comercioflex.catalog.domain.VariantOptionValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
class OrderOptionsJsonCodec {

	private static final TypeReference<List<VariantOptionValue>> TYPE = new TypeReference<>() {};
	private final ObjectMapper objectMapper;

	OrderOptionsJsonCodec(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	String write(List<VariantOptionValue> options) {
		try {
			return objectMapper.writeValueAsString(options);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("No se pudieron serializar las opciones del pedido.", exception);
		}
	}

	List<VariantOptionValue> read(String json) {
		try {
			return List.copyOf(objectMapper.readValue(json, TYPE));
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("Las opciones guardadas del pedido no son válidas.", exception);
		}
	}
}
