package com.transport.tracker.bdd;

import com.intuit.karate.junit5.Karate;

/**
 * Error Handling and Resilience BDD Tests
 * Tests for error scenarios and system resilience
 */
public class ErrorHandlingTest {

    @Karate.Test
    public Karate testErrorScenarios() {
        return Karate.run("classpath:karate/error-scenarios.feature")
                .karateEnv("test")
                .tags("@error-scenarios");
    }

}
