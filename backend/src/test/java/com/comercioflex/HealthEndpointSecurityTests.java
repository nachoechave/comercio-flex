package com.comercioflex;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
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
	void storefrontAndAdminShellRoutesArePublic() throws Exception {
			mockMvc.perform(get("/tiendas/tienda-a/catalogo"))
					.andExpect(status().isOk())
					.andExpect(forwardedUrl("/index.html"));

			mockMvc.perform(get("/carrito")
							.header("Host", "laolamadre.com.ar"))
					.andExpect(status().isOk())
					.andExpect(forwardedUrl("/index.html"));

			mockMvc.perform(get("/checkout")
							.header("Host", "laolamadre.com.ar"))
					.andExpect(status().isOk())
					.andExpect(forwardedUrl("/index.html"));

			mockMvc.perform(get("/mis-pedidos")
							.header("Host", "laolamadre.com.ar"))
					.andExpect(status().isOk())
					.andExpect(forwardedUrl("/index.html"));

			mockMvc.perform(get("/pedidos/ORD-123")
							.header("Host", "laolamadre.com.ar"))
					.andExpect(status().isOk())
					.andExpect(forwardedUrl("/index.html"));

			mockMvc.perform(get("/productos/remera-surf")
							.header("Host", "laolamadre.com.ar"))
					.andExpect(status().isOk())
					.andExpect(forwardedUrl("/index.html"));

			mockMvc.perform(get("/superadmin/empresas/bc979239-95a1-11f1-9748-8234a5e60875"))
					.andExpect(status().isOk())
					.andExpect(forwardedUrl("/index.html"));

			mockMvc.perform(get("/stores/tienda-a/payment-return/opaque-token")
							.queryParam("status", "approved")
							.queryParam("payment_id", "173304330197"))
					.andExpect(status().isOk())
					.andExpect(forwardedUrl("/index.html"));

			mockMvc.perform(get("/admin/login"))
					.andExpect(status().isOk())
					.andExpect(forwardedUrl("/index.html"));
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

	@Test
	void storefrontTenantResolutionIsPublic() throws Exception {
		mockMvc.perform(get("/api/v1/storefront/resolve")
				.header("Host", "laolamadre.com.ar"))
			.andExpect(status().isNotFound());
	}
}