package com.comercioflex.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import com.comercioflex.tenant.application.TenantResolver;

class PlatformSeoControllerTests {

	private final PlatformSeoController controller = new PlatformSeoController();

	@Test
	void exposesRobotsForPlatformHostAndAllowsOaiSearchBot() {
		MockHttpServletRequest request = request("comercioflex.com.ar");

		var response = controller.robots(request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody())
			.contains("User-agent: OAI-SearchBot")
			.contains("Allow: /")
			.contains("Disallow: /admin/")
			.contains("Sitemap: https://comercioflex.com.ar/sitemap.xml");
	}

	@Test
	void exposesSitemapWithOnlyThePlatformLanding() {
		MockHttpServletRequest request = request("www.comercioflex.com.ar");

		var response = controller.sitemap(request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody())
			.contains("<loc>https://comercioflex.com.ar/</loc>")
			.doesNotContain("/admin")
			.doesNotContain("/tiendas/");
	}

	@Test
	void doesNotPublishPlatformSeoResourcesOnTenantDomains() {
		MockHttpServletRequest request = request("laolamadre.com.ar");

		assertThat(controller.robots(request).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(controller.sitemap(request).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void platformRootUsesSeoShellWhileTenantRootKeepsNeutralShell() {
		SpaForwardController spa = new SpaForwardController(mock(TenantResolver.class));

		assertThat(spa.forwardRootToAngular(request("comercioflex.com.ar")))
			.isEqualTo("forward:/platform-index.html");
		assertThat(spa.forwardRootToAngular(request("laolamadre.com.ar")))
			.isEqualTo("forward:/index.html");
	}

	private MockHttpServletRequest request(String host) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setServerName(host);
		return request;
	}
}
