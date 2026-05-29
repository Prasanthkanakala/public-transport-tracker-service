package com.transport.tracker.health;

import com.transport.tracker.client.TransitApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom health indicator for transit API providers.
 * 
 * Checks availability of:
 * - MTA API
 * - TransitLand API
 * - SEPTA API
 * - TfL API
 * 
 * Overall status:
 * - UP: At least one API is available
 * - DOWN: All APIs are unavailable
 * - DEGRADED: Some APIs are unavailable
 */
@Slf4j
@Component
public class TransitApiHealthIndicator implements HealthIndicator {

    private final TransitApiClient mtaClient;
    private final TransitApiClient transitLandClient;
    private final TransitApiClient septaClient;
    private final TransitApiClient tflClient;

    public TransitApiHealthIndicator(
            @Qualifier("mtaClient") TransitApiClient mtaClient,
            @Qualifier("transitLandClient") TransitApiClient transitLandClient,
            @Qualifier("septaClient") TransitApiClient septaClient,
            @Qualifier("tflClient") TransitApiClient tflClient) {
        this.mtaClient = mtaClient;
        this.transitLandClient = transitLandClient;
        this.septaClient = septaClient;
        this.tflClient = tflClient;
    }

    @Override
    public Health health() {
        Map<String, String> apiStatus = new HashMap<>();
        
        boolean mtaAvailable = mtaClient.isAvailable();
        boolean transitLandAvailable = transitLandClient.isAvailable();
        boolean septaAvailable = septaClient.isAvailable();
        boolean tflAvailable = tflClient.isAvailable();
        
        apiStatus.put("mta", mtaAvailable ? "UP" : "DOWN");
        apiStatus.put("transitland", transitLandAvailable ? "UP" : "DOWN");
        apiStatus.put("septa", septaAvailable ? "UP" : "DOWN");
        apiStatus.put("tfl", tflAvailable ? "UP" : "DOWN");
        
        int availableCount = (mtaAvailable ? 1 : 0) + 
                             (transitLandAvailable ? 1 : 0) + 
                             (septaAvailable ? 1 : 0) +
                             (tflAvailable ? 1 : 0);
        
        Health.Builder builder;
        String message;
        
        if (availableCount == 0) {
            builder = Health.down();
            message = "All transit APIs are unavailable";
            log.error("Health check FAILED: All transit APIs down");
        } else if (availableCount == 4) {
            builder = Health.up();
            message = "All transit APIs are available";
        } else {
            builder = Health.status("DEGRADED");
            message = String.format("%d out of 4 transit APIs are available", availableCount);
            log.warn("Health check DEGRADED: {} APIs available", availableCount);
        }
        
        return builder
                .withDetail("apis", apiStatus)
                .withDetail("availableCount", availableCount)
                .withDetail("totalCount", 4)
                .withDetail("message", message)
                .build();
    }
}
