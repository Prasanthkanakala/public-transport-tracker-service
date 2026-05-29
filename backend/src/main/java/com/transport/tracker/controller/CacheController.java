package com.transport.tracker.controller;

import com.transport.tracker.cache.CacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
* Cache management and diagnostics endpoints.
* Exposes cache stats and allows selective or full cache invalidation.
*/
@RestController
@RequestMapping("/api/v1/cache")
@RequiredArgsConstructor
@Tag(name = "Cache", description = "In-memory cache management and diagnostics")
public class CacheController {

    private final CacheService cacheService;

    @Operation(summary = "Get cache statistics",
               description = "Returns hit rate, miss count, stale hit count, size and TTL settings.")
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(cacheService.getStats());
    }

    @Operation(summary = "Clear all cache entries",
               description = "Removes all cached transport data. Next requests will hit live APIs.")
    @DeleteMapping
    public ResponseEntity<Map<String, String>> clearCache() {
        cacheService.clear();
        return ResponseEntity.ok(Map.of("message", "Cache cleared successfully"));
    }

    @Operation(summary = "Invalidate cache for a specific city/route",
               description = "Removes cached data for the given city+routeId combination.")
    @DeleteMapping("/entry")
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