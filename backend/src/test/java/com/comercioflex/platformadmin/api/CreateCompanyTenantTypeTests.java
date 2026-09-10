package com.comercioflex.platformadmin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.comercioflex.tenant.domain.TenantType;
import org.junit.jupiter.api.Test;

class CreateCompanyTenantTypeTests {

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
