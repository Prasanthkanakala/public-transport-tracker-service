package com.transport.tracker.bdd;

import com.intuit.karate.junit5.Karate;

/**
 * Cache Management BDD Tests
 * Tests for cache operations and statistics endpoints
 */
public class CacheApiTest {

    @Karate.Test
    public Karate testCache() {
        return Karate.run("classpath:karate/cache.feature")
                .karateEnv("test")
                .tags("@cache");
    }

}
