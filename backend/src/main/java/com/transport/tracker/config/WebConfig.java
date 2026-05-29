package com.transport.tracker.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
* CORS configuration — allows the React frontend (port 3000 in dev,
* same origin in prod via nginx proxy) to call the backend APIs.
*
* Allowed origins are configured via environment variables in production.
* Never use "*" in production with credentials; list origins explicitly.
*/
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(
                    "http://localhost:3000",
                    "http://localhost:80",
                    "http://localhost",
                    "${ALLOWED_ORIGINS:http://localhost:3000}"
                )
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}