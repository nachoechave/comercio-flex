package com.comercioflex;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class PwaStaticResourceSecurityTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void pwaInfrastructureDoesNotRequireAuthentication() throws Exception {
		// The frontend bundle is copied into the backend only in the Docker image,
		// so backend-only tests legitimately return 404 here. The important part is
		// that these requests are public instead of being intercepted with 401.
		mockMvc.perform(get("/admin.webmanifest"))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/admin-sw.js"))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/admin-offline.html"))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/admin-index.html"))
			.andExpect(status().isNotFound());
	}
}
