package com.transport.tracker.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
* OpenAPI 3.0 / Swagger configuration.
* Accessible at: http://localhost:8080/swagger-ui/index.html
*/
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private int serverPort;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Public Transport Tracker API")
                .version("1.0.0")
                .description("""
                    Real-time public transport tracker microservice.

                    **Option A – Resilience & Offline Mode** implemented:
                    - In-memory cache with configurable TTL (default 5 minutes)
                    - Automatic fallback to stale data (up to 1 hour) on API failure
                    - Offline mode toggle (per-request or globally via env var)
                    - Falls back to rich mock data if no cached data is available

                    **Data Sources:**
                    - NYC MTA: https://api.mta.info/
                    - Transit.land: https://transit.land/api/v2/rest

                    **Conditional Alerts** (automatically included in responses):
                    - Delay > 15 min → "Significant delays - Plan accordingly"
                    - Service disruption → "Service alert - Check alternative routes"
                    - High crowding → "Vehicle at capacity - Consider next service"
                    - Weather → "Weather impact on schedule"
                    """)
                .contact(new Contact()
                    .name("Transport Tracker Team")
                    .email("transport-tracker@example.com"))
                .license(new License().name("Apache 2.0")))
            .servers(List.of(
                new Server().url("http://localhost:" + serverPort).description("Local development"),
                new Server().url("https://transport-tracker.example.com").description("Production")
            ))
            .components(new Components()
                .addSecuritySchemes("ApiKeyHeader", new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.HEADER)
                    .name("X-Api-Key")
                    .description("Optional API key for rate-limited access")));
    }
}
