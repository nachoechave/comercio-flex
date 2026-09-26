package com.comercioflex.analytics.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import com.comercioflex.tenant.api.TenantResolutionFilter;

@Configuration
public class AnalyticsSecurityConfig {

    @Bean
    @Order(0)
    SecurityFilterChain analyticsSecurityFilterChain(
            HttpSecurity http,
            TenantResolutionFilter tenantResolutionFilter) throws Exception {
        var matcher = PathPatternRequestMatcher.withDefaults()
                .matcher(HttpMethod.POST, "/api/v1/stores/*/analytics/events");

        return http
                .securityMatcher(matcher)
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .addFilterAfter(tenantResolutionFilter, AnonymousAuthenticationFilter.class)
                .build();
    }
}
