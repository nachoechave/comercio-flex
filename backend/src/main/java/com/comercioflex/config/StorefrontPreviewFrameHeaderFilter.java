package com.comercioflex.config;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class StorefrontPreviewFrameHeaderFilter extends OncePerRequestFilter {

	private static final String FRAME_OPTIONS = "X-Frame-Options";
	private static final String SAME_ORIGIN = "SAMEORIGIN";

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		if (!isStorefrontPreviewRequest(request)) {
			filterChain.doFilter(request, response);
			return;
		}

		HttpServletResponseWrapper previewResponse = new HttpServletResponseWrapper(response) {
			@Override
			public void setHeader(String name, String value) {
				if (FRAME_OPTIONS.equalsIgnoreCase(name)) {
					super.setHeader(FRAME_OPTIONS, SAME_ORIGIN);
					return;
				}
				super.setHeader(name, value);
			}

			@Override
			public void addHeader(String name, String value) {
				if (FRAME_OPTIONS.equalsIgnoreCase(name)) {
					super.setHeader(FRAME_OPTIONS, SAME_ORIGIN);
					return;
				}
				super.addHeader(name, value);
			}
		};

		previewResponse.setHeader(FRAME_OPTIONS, SAME_ORIGIN);
		filterChain.doFilter(request, previewResponse);
	}

	private static boolean isStorefrontPreviewRequest(HttpServletRequest request) {
		String path = originalPath(request);
		String contextPath = request.getContextPath();
		if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
			path = path.substring(contextPath.length());
		}
		return ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))
			&& path.startsWith("/tiendas/")
			&& "1".equals(request.getParameter("preview"))
			&& request.getParameter("previewTemplate") != null;
	}

	private static String originalPath(HttpServletRequest request) {
		Object forwardedPath = request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
		if (forwardedPath instanceof String path && !path.isBlank()) {
			return path;
		}
		return request.getRequestURI();
	}
}
