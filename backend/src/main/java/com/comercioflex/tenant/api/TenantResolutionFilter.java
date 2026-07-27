package com.comercioflex.tenant.api;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.comercioflex.tenant.application.ResolvedTenant;
import com.comercioflex.tenant.application.TenantContext;
import com.comercioflex.tenant.application.TenantNotFoundException;
import com.comercioflex.tenant.application.TenantResolver;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class TenantResolutionFilter extends OncePerRequestFilter {

	private static final Pattern STORE_SETTINGS_PATH =
		Pattern.compile("^/api/v1/stores/([^/]+)/settings$");

	private final TenantResolver tenantResolver;
	private final TenantContext tenantContext;
	private final HandlerExceptionResolver exceptionResolver;

	public TenantResolutionFilter(
			TenantResolver tenantResolver,
			TenantContext tenantContext,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
		this.tenantResolver = tenantResolver;
		this.tenantContext = tenantContext;
		this.exceptionResolver = exceptionResolver;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !STORE_SETTINGS_PATH.matcher(requestPath(request)).matches();
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		Matcher matcher = STORE_SETTINGS_PATH.matcher(requestPath(request));
		if (!matcher.matches()) {
			filterChain.doFilter(request, response);
			return;
		}

		try {
			ResolvedTenant tenant = tenantResolver.resolveActive(matcher.group(1));
			try (TenantContext.Scope ignored = tenantContext.open(tenant.databaseKey())) {
				filterChain.doFilter(request, response);
			}
		}
		catch (TenantNotFoundException exception) {
			exceptionResolver.resolveException(request, response, null, exception);
		}
	}

	private String requestPath(HttpServletRequest request) {
		String requestUri = request.getRequestURI();
		String contextPath = request.getContextPath();
		return contextPath.isEmpty() ? requestUri : requestUri.substring(contextPath.length());
	}
}
