package com.comercioflex.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.ServletException;

class RequestCorrelationFilterTests {

	private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

	@Test
	void preservesSafeClientRequestId() throws ServletException, IOException {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(RequestCorrelationFilter.HEADER, "checkout-123");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, new MockFilterChain());

		assertThat(response.getHeader(RequestCorrelationFilter.HEADER)).isEqualTo("checkout-123");
	}

	@Test
	void replacesUnsafeRequestId() throws ServletException, IOException {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(RequestCorrelationFilter.HEADER, "bad id with spaces");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, new MockFilterChain());

		assertThat(response.getHeader(RequestCorrelationFilter.HEADER))
			.matches("[0-9a-f-]{36}");
	}
}
