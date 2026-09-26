package com.comercioflex.tenant.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.comercioflex.identity.application.TenantMembershipAuthorizer;
import com.comercioflex.tenant.application.TenantContext;
import com.comercioflex.tenant.application.TenantResolver;
import com.comercioflex.tenant.infrastructure.control.TenantActivityRecorder;

class TenantResolutionFilterTests {

	private final TenantResolutionFilter filter = new TenantResolutionFilter(
		mock(TenantResolver.class),
		mock(TenantContext.class),
		mock(TenantMembershipAuthorizer.class),
		mock(TenantActivityRecorder.class),
		mock(HandlerExceptionResolver.class));

	@Test
	void resolvesTenantForPublicAnalyticsIngestion() {
		MockHttpServletRequest request = new MockHttpServletRequest(
			"POST",
			"/api/v1/stores/la-ola-madre/analytics/events");

		assertThat(filter.shouldNotFilter(request)).isFalse();
	}

	@Test
	void stillSkipsUnknownStoreResources() {
		MockHttpServletRequest request = new MockHttpServletRequest(
			"POST",
			"/api/v1/stores/la-ola-madre/unknown-resource");

		assertThat(filter.shouldNotFilter(request)).isTrue();
	}
}
