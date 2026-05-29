package com.transport.tracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
* Public Transport Tracker Microservice
*
* Implements Option A: Resilience and Offline Mode
* - In-memory cache with TTL (no third-party cache library)
* - Serves stale data on upstream API failure
* - Falls back to mock data when stale cache is also unavailable
* - Explicit offline mode toggle
*/
@SpringBootApplication
@EnableScheduling
public class TransportTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransportTrackerApplication.class, args);
    }
}