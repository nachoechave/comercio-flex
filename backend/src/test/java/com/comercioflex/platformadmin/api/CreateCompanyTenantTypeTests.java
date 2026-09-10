package com.comercioflex.platformadmin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.comercioflex.tenant.domain.TenantType;
import org.junit.jupiter.api.Test;

class CreateCompanyTenantTypeTests {
	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = {"admin", "api", "tiendas", "superadmin", "assets"})
	void rejectsReservedSlugsForEveryTenantType(String slug) throws Exception {
		try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
			for (String type : new String[]{"RADIO", "ECOMMERCE"}) {
				var request = mapper.readValue("{\"slug\":\"" + slug + "\",\"tenantType\":\"" + type + "\"}", CreateCompanyRequest.class);
				assertThat(factory.getValidator().validate(request)).anyMatch(v -> v.getPropertyPath().toString().equals("availableSlug"));
			}
		}
	}

	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	void omittedTypeDefaultsToEcommerceAndRadioIsPreserved() throws Exception {
		assertThat(mapper.readValue("{}", CreateCompanyRequest.class).toCommand().tenantType())
			.isEqualTo(TenantType.ECOMMERCE);
		assertThat(mapper.readValue("{\"tenantType\":\"RADIO\"}", CreateCompanyRequest.class)
			.toCommand().tenantType()).isEqualTo(TenantType.RADIO);
	}

	@Test
	void arbitraryTypeIsRejectedDuringRequestDeserialization() {
		assertThatThrownBy(() -> mapper.readValue("{\"tenantType\":\"UNKNOWN\"}", CreateCompanyRequest.class))
			.isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
		assertThatThrownBy(() -> mapper.readValue("{\"tenantType\":1}", CreateCompanyRequest.class))
			.isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
	}
}
