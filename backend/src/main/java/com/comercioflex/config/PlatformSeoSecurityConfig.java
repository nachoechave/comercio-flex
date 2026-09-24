package com.comercioflex.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class PlatformSeoSecurityConfig {

	@Bean
	@Order(0)
	SecurityFilterChain platformSeoSecurityFilterChain(HttpSecurity http) throws Exception {
		return http
			.securityMatcher("/robots.txt", "/sitemap.xml", "/platform-index.html")
			.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
			.csrf(csrf -> csrf.disable())
			.build();
	}
}
