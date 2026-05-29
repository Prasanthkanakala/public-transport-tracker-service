package com.transport.tracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
* Security configuration.
*
* Approach:
* - Stateless REST API: no sessions, no CSRF (JWT / API key optional extension)
* - Actuator health/info: public (needed for Docker healthchecks and load balancers)
* - Swagger UI: public (for development and documentation consumers)
* - All /api/v1/** endpoints: permit all (add API key filter here in production)
*
* Security headers:
* - X-Content-Type-Options: nosniff
* - X-Frame-Options: DENY
* - Strict-Transport-Security (applied by reverse proxy in prod)
*
* API key protection:
* In production, replace permitAll() on /api/v1/** with an
* ApiKeyAuthenticationFilter registered via .addFilterBefore().
* The key is stored in environment variable API_KEY (never in source code).
*/
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Swagger UI and OpenAPI spec
                .requestMatchers(
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**",
                    "/v3/api-docs"
                ).permitAll()
                // Spring Boot Actuator – health and readiness probes
                .requestMatchers(
                    "/actuator/health",
                    "/actuator/info"
                ).permitAll()
                // Actuator metrics – restrict in prod (here open for assessment)
                .requestMatchers("/actuator/**").permitAll()
                // Public API endpoints
                .requestMatchers("/api/v1/**").permitAll()
                .anyRequest().authenticated()
            )
            .headers(headers -> headers
                .contentTypeOptions(ct -> {}) // X-Content-Type-Options: nosniff
                .frameOptions(fo -> fo.deny())  // X-Frame-Options: DENY
            );

        return http.build();
    }
}