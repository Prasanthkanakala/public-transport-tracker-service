package com.transport.tracker.bdd;

import com.intuit.karate.junit5.Karate;

/**
 * Routing and Planning BDD Tests
 * Tests for route planning and crowding endpoints
 */
public class RoutingApiTest {

    @Karate.Test
    public Karate testRouting() {
        return Karate.run("classpath:karate/routing.feature")
                .karateEnv("test")
                .tags("@routing");
    }

    @Karate.Test
    public Karate testCrowding() {
        return Karate.run("classpath:karate/crowding.feature")
                .karateEnv("test")
                .tags("@crowding");
    }

}
