package com.comercioflex.config;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

	static final String HEADER = "X-Request-ID";
	private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._:-]{1,100}");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain chain) throws ServletException, IOException {
		String supplied = request.getHeader(HEADER);
		String requestId = supplied != null && SAFE_ID.matcher(supplied).matches()
			? supplied
			: UUID.randomUUID().toString();
		response.setHeader(HEADER, requestId);
		MDC.put("requestId", requestId);
		try {
			chain.doFilter(request, response);
		}
		finally {
			MDC.remove("requestId");
		}
	}
}
