package com.transport.tracker.bdd;

import com.intuit.karate.junit5.Karate;

/**
 * Transport API BDD Tests
 * Tests for main transport endpoints
 */
public class TransportApiTest {

    @Karate.Test
    public Karate testTransport() {
        return Karate.run("classpath:karate/transport.feature")
                .karateEnv("test")
                .tags("@transport");
    }

    @Karate.Test
    public Karate testArrivals() {
        return Karate.run("classpath:karate/arrivals.feature")
                .karateEnv("test")
                .tags("@arrivals");
    }

    @Karate.Test
    public Karate testVehicles() {
        return Karate.run("classpath:karate/vehicles.feature")
                .karateEnv("test")
                .tags("@vehicles");
    }

    @Karate.Test
    public Karate testAlerts() {
        return Karate.run("classpath:karate/alerts.feature")
                .karateEnv("test")
                .tags("@alerts");
    }

}
