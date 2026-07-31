package com.comercioflex.config;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.comercioflex.identity.application.LoginRateLimitProperties;
import com.comercioflex.identity.application.PlatformUserDetailsService;
import com.comercioflex.identity.application.TenantPermissionAuthorizationManager;
import com.comercioflex.identity.domain.TenantPermission;
import com.comercioflex.tenant.api.TenantResolutionFilter;

@Configuration
@EnableConfigurationProperties(LoginRateLimitProperties.class)
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			TenantResolutionFilter tenantResolutionFilter,
			SecurityContextRepository securityContextRepository,
			CsrfTokenRepository csrfTokenRepository) throws Exception {
		return http
			.cors(Customizer.withDefaults())
			.csrf(csrf -> csrf
				.csrfTokenRepository(csrfTokenRepository)
				.csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
			.securityContext(context -> context
				.requireExplicitSave(true)
				.securityContextRepository(securityContextRepository))
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
				.requestMatchers("/api/v1/stores/*/settings").permitAll()
				.requestMatchers(
					HttpMethod.GET,
					"/api/v1/stores/*/catalog/categories",
					"/api/v1/stores/*/catalog/products",
					"/api/v1/stores/*/catalog/products/*")
				.permitAll()
				.requestMatchers(
					HttpMethod.POST,
					"/api/v1/stores/*/orders")
				.permitAll()
				.requestMatchers(
					HttpMethod.GET,
					"/api/v1/stores/*/orders/*")
				.permitAll()
				.requestMatchers(
					"/api/v1/auth/csrf",
					"/api/v1/auth/login",
					"/api/v1/auth/session").permitAll()
				.requestMatchers(
					HttpMethod.GET,
					"/api/v1/stores/*/admin/categories",
					"/api/v1/stores/*/admin/categories/*")
				.access(new TenantPermissionAuthorizationManager(
					TenantPermission.VIEW_CATALOG))
				.requestMatchers(
					"/api/v1/stores/*/admin/categories",
					"/api/v1/stores/*/admin/categories/**")
				.access(new TenantPermissionAuthorizationManager(
					TenantPermission.MANAGE_CATALOG))
				.requestMatchers(
					HttpMethod.GET,
					"/api/v1/stores/*/admin/products",
					"/api/v1/stores/*/admin/products/**")
				.access(new TenantPermissionAuthorizationManager(
					TenantPermission.VIEW_CATALOG))
				.requestMatchers(
					"/api/v1/stores/*/admin/products",
					"/api/v1/stores/*/admin/products/**")
				.access(new TenantPermissionAuthorizationManager(
					TenantPermission.MANAGE_CATALOG))
				.requestMatchers(
					HttpMethod.GET,
					"/api/v1/stores/*/admin/inventory",
					"/api/v1/stores/*/admin/inventory/**")
				.access(new TenantPermissionAuthorizationManager(
					TenantPermission.VIEW_INVENTORY))
				.requestMatchers(
					HttpMethod.POST,
					"/api/v1/stores/*/admin/inventory/**")
				.access(new TenantPermissionAuthorizationManager(
					TenantPermission.ADJUST_STOCK))
				.requestMatchers(
					"/api/v1/stores/*/admin/orders",
					"/api/v1/stores/*/admin/orders/**")
				.access(new TenantPermissionAuthorizationManager(
					TenantPermission.MANAGE_ORDERS))
				.requestMatchers(
					"/api/v1/stores/*/admin/payment-connection",
					"/api/v1/stores/*/admin/payment-connection/**")
				.access(new TenantPermissionAuthorizationManager(
					TenantPermission.MANAGE_PAYMENTS))
				.requestMatchers(
					HttpMethod.GET,
					"/api/v1/integrations/mercado-pago/oauth/callback")
				.authenticated()
				.anyRequest().authenticated())
			.addFilterAfter(tenantResolutionFilter, AnonymousAuthenticationFilter.class)
			.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new DelegatingPasswordEncoder(
			"bcrypt",
			Map.of("bcrypt", new BCryptPasswordEncoder(12)));
	}

	@Bean
	AuthenticationManager authenticationManager(
			PlatformUserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		provider.setHideUserNotFoundExceptions(true);
		return new ProviderManager(provider);
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	@Bean
	SessionAuthenticationStrategy sessionAuthenticationStrategy() {
		return new ChangeSessionIdAuthenticationStrategy();
	}

	@Bean
	CsrfTokenRepository csrfTokenRepository() {
		CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		repository.setCookieName("XSRF-TOKEN");
		repository.setHeaderName("X-XSRF-TOKEN");
		repository.setCookiePath("/");
		return repository;
	}

	@Bean
	FilterRegistrationBean<TenantResolutionFilter> disableTenantFilterContainerRegistration(
			TenantResolutionFilter filter) {
		FilterRegistrationBean<TenantResolutionFilter> registration =
			new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(
			@Value("${app.cors.allowed-origin}") String allowedOrigin) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(List.of(allowedOrigin));
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of(
			"Content-Type",
			"X-XSRF-TOKEN",
			"X-Request-ID",
			"Idempotency-Key"));
		configuration.setAllowCredentials(true);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}
