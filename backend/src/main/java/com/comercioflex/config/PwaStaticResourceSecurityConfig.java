package com.comercioflex.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class PwaStaticResourceSecurityConfig {

	@Bean
	@Order(0)
	SecurityFilterChain pwaStaticResourceSecurityFilterChain(HttpSecurity http) throws Exception {
		return http
			.securityMatcher(
				"/admin.webmanifest",
				"/admin-sw.js",
				"/admin-offline.html",
				"/admin-index.html")
			.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
			.csrf(csrf -> csrf.disable())
			.build();
	}
}
