package com.comercioflex.tenant.api;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.comercioflex.tenant.application.ResolvedTenant;
import com.comercioflex.tenant.application.TenantContext;
import com.comercioflex.tenant.application.TenantNotFoundException;
import com.comercioflex.tenant.application.TenantResolver;
import com.comercioflex.identity.application.PlatformPrincipal;
import com.comercioflex.identity.application.TenantAccessDeniedException;
import com.comercioflex.identity.application.TenantMembership;
import com.comercioflex.identity.application.TenantMembershipAuthorizer;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class TenantResolutionFilter extends OncePerRequestFilter {

	public static final String TENANT_MEMBERSHIP_ATTRIBUTE =
		TenantResolutionFilter.class.getName() + ".membership";

	private static final Pattern STORE_PATH =
		Pattern.compile("^/api/v1/stores/([^/]+)(/.*)$");

	private final TenantResolver tenantResolver;
	private final TenantContext tenantContext;
	private final TenantMembershipAuthorizer membershipAuthorizer;
	private final HandlerExceptionResolver exceptionResolver;

	public TenantResolutionFilter(
			TenantResolver tenantResolver,
			TenantContext tenantContext,
			TenantMembershipAuthorizer membershipAuthorizer,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
		this.tenantResolver = tenantResolver;
		this.tenantContext = tenantContext;
		this.membershipAuthorizer = membershipAuthorizer;
		this.exceptionResolver = exceptionResolver;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		Matcher matcher = STORE_PATH.matcher(requestPath(request));
		if (!matcher.matches()) {
			return true;
		}
		String storeResource = matcher.group(2);
		return !storeResource.equals("/settings")
			&& !storeResource.equals("/catalog")
			&& !storeResource.startsWith("/catalog/")
			&& !storeResource.equals("/orders")
			&& !storeResource.startsWith("/orders/")
			&& !storeResource.startsWith("/payment-returns/")
			&& !storeResource.startsWith("/media/product-images/")
			&& !storeResource.startsWith("/media/branding/")
			&& !storeResource.equals("/admin")
			&& !storeResource.startsWith("/admin/");
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		Matcher matcher = STORE_PATH.matcher(requestPath(request));
		if (!matcher.matches()) {
			filterChain.doFilter(request, response);
			return;
		}

		try {
			String storeResource = matcher.group(2);
			boolean administrative = storeResource.equals("/admin")
				|| storeResource.startsWith("/admin/");
			PlatformPrincipal principal = currentPrincipal();
			if (administrative && principal == null) {
				filterChain.doFilter(request, response);
				return;
			}

			ResolvedTenant tenant = tenantResolver.resolveActive(matcher.group(1));
			if (administrative) {
				TenantMembership membership = membershipAuthorizer.requireActiveMembership(
					principal.id(),
					tenant.id());
				request.setAttribute(TENANT_MEMBERSHIP_ATTRIBUTE, membership);
			}
			try (TenantContext.Scope ignored = tenantContext.open(tenant.databaseKey())) {
				filterChain.doFilter(request, response);
			}
		}
		catch (TenantNotFoundException | TenantAccessDeniedException exception) {
			exceptionResolver.resolveException(request, response, null, exception);
		}
	}

	private PlatformPrincipal currentPrincipal() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null
				|| !authentication.isAuthenticated()
				|| !(authentication.getPrincipal() instanceof PlatformPrincipal principal)) {
			return null;
		}
		return principal;
	}

	private String requestPath(HttpServletRequest request) {
		String requestUri = request.getRequestURI();
		String contextPath = request.getContextPath();
		return contextPath.isEmpty() ? requestUri : requestUri.substring(contextPath.length());
	}
}
