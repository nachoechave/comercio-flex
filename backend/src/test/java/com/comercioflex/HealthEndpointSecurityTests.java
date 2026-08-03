package com.comercioflex;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class HealthEndpointSecurityTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void healthIsPublic() throws Exception {
		mockMvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void unknownApplicationEndpointRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/not-yet-implemented"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void publicCheckoutWritesDoNotRequireCsrf() throws Exception {
		String token = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ";
		mockMvc.perform(post("/api/v1/stores/tienda-a/orders"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.title").value("Tienda no encontrada"));
		mockMvc.perform(post("/api/v1/stores/tienda-a/orders/order-id/payments/checkout-pro"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.title").value("Tienda no encontrada"));
		mockMvc.perform(post("/api/v1/stores/tienda-a/payment-returns/"
				+ token + "/inspect"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.title").value("Tienda no encontrada"));
		mockMvc.perform(post("/api/v1/stores/tienda-a/payment-returns/"
				+ token + "/reconcile"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.title").value("Tienda no encontrada"));
	}
}
