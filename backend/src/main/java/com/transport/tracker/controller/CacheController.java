package com.transport.tracker.controller;

import com.transport.tracker.cache.CacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Cache management and diagnostics endpoints.
 * Exposes cache stats and allows selective or full cache invalidation.
 * Access is restricted based on user roles:
 * - Stats: OPERATOR and ADMIN
 * - Clear/Invalidate: ADMIN only
 */
@RestController
@RequestMapping("/api/v1/cache")
@RequiredArgsConstructor
@Tag(name = "Cache", description = "In-memory cache management and diagnostics")
public class CacheController {

    private final CacheService cacheService;

    @Operation(summary = "Get cache statistics",
               description = "Returns hit rate, miss count, stale hit count, size and TTL settings. Requires OPERATOR or ADMIN role.")
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(cacheService.getStats());
    }

    @Operation(summary = "Clear all cache entries (POST)",
               description = "Removes all cached transport data via POST. Requires ADMIN role.")
    @PostMapping("/clear")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> clearCachePost() {
        cacheService.clear();
        return ResponseEntity.ok(Map.of("message", "Cache cleared successfully"));
    }

    @Operation(summary = "Clear all cache entries (DELETE)",
               description = "Removes all cached transport data. Requires ADMIN role.")
    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> clearCache() {
        cacheService.clear();
        return ResponseEntity.ok(Map.of("message", "Cache cleared successfully"));
    }

    @Operation(summary = "Invalidate cache for a specific city/route",
               description = "Removes cached data for the given city+routeId combination. Requires ADMIN role.")
    @DeleteMapping("/entry")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> invalidateEntry(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String routeId) {

        String key = cacheService.buildKey(city, routeId);
        cacheService.invalidate(key);
        return ResponseEntity.ok(Map.of(
                "message", "Cache entry invalidated",
                "key", key
        ));
    }
}