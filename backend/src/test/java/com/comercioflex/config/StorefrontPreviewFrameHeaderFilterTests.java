package com.comercioflex.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;

class StorefrontPreviewFrameHeaderFilterTests {

	private final StorefrontPreviewFrameHeaderFilter filter = new StorefrontPreviewFrameHeaderFilter();

	@Test
	void previewKeepsSameOriginEvenIfDownstreamWritesDeny() throws Exception {
		MockHttpServletRequest request = previewRequest("/tiendas/la-ola-madre");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, downstreamWritingDeny());

		assertThat(response.getHeader("X-Frame-Options")).isEqualTo("SAMEORIGIN");
	}

	@Test
	void forwardedPreviewKeepsSameOriginForIndexHtmlDispatch() throws Exception {
		MockHttpServletRequest request = previewRequest("/index.html");
		request.setAttribute(RequestDispatcher.FORWARD_REQUEST_URI, "/tiendas/la-ola-madre");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, downstreamWritingDeny());

		assertThat(response.getHeader("X-Frame-Options")).isEqualTo("SAMEORIGIN");
	}

	@Test
	void regularStorefrontKeepsDownstreamDeny() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/tiendas/la-ola-madre");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, downstreamWritingDeny());

		assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
	}

	private static MockHttpServletRequest previewRequest(String path) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
		request.setParameter("preview", "1");
		request.setParameter("previewTemplate", "FRESH");
		return request;
	}

	private static FilterChain downstreamWritingDeny() {
		return (request, response) -> ((HttpServletResponse) response)
			.setHeader("X-Frame-Options", "DENY");
	}
}
