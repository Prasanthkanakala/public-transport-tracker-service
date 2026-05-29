package com.transport.tracker.bdd;

import com.intuit.karate.junit5.Karate;

/**
 * Integration Testing BDD Tests
 * Tests for end-to-end scenarios and API integration
 */
public class IntegrationTest {

    @Karate.Test
    public Karate testIntegration() {
        return Karate.run("classpath:karate/integration.feature")
                .karateEnv("test")
                .tags("@integration");
    }

}
