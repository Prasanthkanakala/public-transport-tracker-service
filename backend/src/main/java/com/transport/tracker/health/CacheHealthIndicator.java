package com.transport.tracker.health;

import com.transport.tracker.cache.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Custom health indicator for cache service.
 * 
 * Monitors:
 * - Cache operational status
 * - Cache size and utilization
 * - Cache performance metrics
 * 
 * Status:
 * - UP: Cache is operational and below threshold
 * - DEGRADED: Cache utilization > 80%
 * - DOWN: Cache service is not operational
 */
@Slf4j
@Component
public class CacheHealthIndicator implements HealthIndicator {

    private final CacheService cacheService;
    
    @Value("${transit.cache.max-size:1000}")
    private int maxCacheSize;
    
    private static final double DEGRADED_THRESHOLD = 0.8; // 80%

    public CacheHealthIndicator(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Override
    public Health health() {
        try {
            int currentSize = cacheService.size();
            double utilizationPercent = (double) currentSize / maxCacheSize * 100;
            
            Health.Builder builder;
            String status;
            
            if (utilizationPercent > DEGRADED_THRESHOLD * 100) {
                builder = Health.status("DEGRADED");
                status = "Cache utilization high";
                log.warn("Cache health DEGRADED: {}% utilization", String.format("%.1f", utilizationPercent));
            } else {
                builder = Health.up();
                status = "Cache operational";
            }
            
            return builder
                    .withDetail("size", currentSize)
                    .withDetail("maxSize", maxCacheSize)
                    .withDetail("utilizationPercent", String.format("%.2f", utilizationPercent))
                    .withDetail("status", status)
                    .build();
            
        } catch (Exception e) {
            log.error("Cache health check failed", e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withDetail("status", "Cache service unavailable")
                    .build();
        }
    }
}
