package com.transport.tracker.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.transport.tracker.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
* Provides mock transit data for:
*  1. Offline mode (when explicitly enabled via toggle)
*  2. Fallback when both live API and stale cache are unavailable
*
* Mock data is loaded from src/main/resources/mock-data/*.json.
* If the JSON files are missing or fail to load, synthetic data is generated
* programmatically so the service never returns empty results.
*/
@Slf4j
@Service
@RequiredArgsConstructor
public class MockDataService {

    private final ObjectMapper objectMapper;
    private final Random random = new Random(42L); // deterministic for tests

    public TransportData getMockData(String city, String routeId) {
        log.info("Serving MOCK data for city={}, routeId={}", city, routeId);

        return TransportData.builder()
                .city(city != null ? city : "nyc")
                .routeId(routeId != null ? routeId : "A")
                .vehicles(loadMockVehicles(routeId))
                .arrivals(loadMockArrivals(routeId))
                .alerts(loadMockAlerts())
                .crowding(loadMockCrowding(routeId))
                .routePlans(List.of())
                .build();
    }

    // ─── Load from JSON (falls back to synthetic) ─────────────────────────────

    private List<VehicleLocation> loadMockVehicles(String routeId) {
        try {
            ClassPathResource resource = new ClassPathResource("mock-data/vehicles.json");
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    List<VehicleLocation> vehicles = objectMapper.readValue(is,
                            new TypeReference<>() {});
                    if (routeId != null) {
                        vehicles.forEach(v -> v.setRouteId(routeId));
                    }
                    return vehicles;
                }
            }
        } catch (Exception e) {
            log.warn("Could not load mock vehicles.json, using synthetic data: {}", e.getMessage());
        }
        return syntheticVehicles(routeId);
    }

    private List<ArrivalPrediction> loadMockArrivals(String routeId) {
        try {
            ClassPathResource resource = new ClassPathResource("mock-data/arrivals.json");
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    return objectMapper.readValue(is, new TypeReference<>() {});
                }
            }
        } catch (Exception e) {
            log.warn("Could not load mock arrivals.json, using synthetic data: {}", e.getMessage());
        }
        return syntheticArrivals(routeId);
    }

    private List<ServiceAlert> loadMockAlerts() {
        try {
            ClassPathResource resource = new ClassPathResource("mock-data/alerts.json");
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    return objectMapper.readValue(is, new TypeReference<>() {});
                }
            }
        } catch (Exception e) {
            log.warn("Could not load mock alerts.json, using synthetic data: {}", e.getMessage());
        }
        return syntheticAlerts();
    }

    private List<CrowdingInfo> loadMockCrowding(String routeId) {
        return syntheticCrowding(routeId);
    }

    // ─── Synthetic data generators ────────────────────────────────────────────

    private List<VehicleLocation> syntheticVehicles(String routeId) {
        String route = routeId != null ? routeId : "A";
        List<VehicleLocation> vehicles = new ArrayList<>();
        double baseLat = 40.7128;
        double baseLon = -74.0060;

        for (int i = 1; i <= 5; i++) {
            int delaySeconds = (i % 3 == 0) ? 960 : (i % 2 == 0) ? 120 : 0; // one is 16 min late
            vehicles.add(VehicleLocation.builder()
                    .vehicleId("VEH-" + route + "-" + String.format("%03d", i))
                    .routeId(route)
                    .tripId("TRIP-" + route + "-" + i)
                    .latitude(baseLat + (i * 0.005))
                    .longitude(baseLon + (i * 0.005))
                    .bearing((double) (i * 72 % 360))
                    .speedKmh(28.0 + i)
                    .status("IN_TRANSIT_TO")
                    .currentStopId("STOP-" + route + "-" + (i + 1))
                    .delaySeconds(delaySeconds)
                    .delayLabel(delaySeconds == 0 ? "On time" : (delaySeconds / 60) + " min late")
                    .occupancyStatus(i == 2 ? "FEW_SEATS_AVAILABLE" : "MANY_SEATS_AVAILABLE")
                    .timestamp(Instant.now())
                    .build());
        }
        return vehicles;
    }

    private List<ArrivalPrediction> syntheticArrivals(String routeId) {
        String route = routeId != null ? routeId : "A";
        List<ArrivalPrediction> arrivals = new ArrayList<>();
        Instant base = Instant.now();

        String[] stops = {"Times Square-42 St", "34 St-Penn Station", "14 St", "Fulton St", "Atlantic Av"};
        for (int i = 0; i < stops.length; i++) {
            int offsetMinutes = 3 + (i * 7);
            int delaySec = (i == 1) ? 1080 : 0; // 18-min delay at second stop
            Instant scheduled = base.plusSeconds(offsetMinutes * 60L);
            Instant predicted = scheduled.plusSeconds(delaySec);

            arrivals.add(ArrivalPrediction.builder()
                    .stopId("STOP-" + route + "-" + (i + 1))
                    .stopName(stops[i])
                    .routeId(route)
                    .routeName(route + " Train")
                    .headsign("Far Rockaway")
                    .scheduledArrival(scheduled)
                    .predictedArrival(predicted)
                    .delaySeconds(delaySec)
                    .status(delaySec > 60 ? "DELAYED" : "ON_TIME")
                    .realtime(true)
                    .minutesToArrival(offsetMinutes + delaySec / 60)
                    .platform(String.valueOf(i % 4 + 1))
                    .build());
        }
        return arrivals;
    }

    private List<ServiceAlert> syntheticAlerts() {
        return List.of(
                ServiceAlert.builder()
                        .alertId("ALT-001")
                        .type("DISRUPTION")
                        .severity("HIGH")
                        .headerText("Track work at Atlantic Av-Barclays Ctr")
                        .descriptionText("A and C trains are suspended between Jay St-MetroTech and Atlantic Av-Barclays Ctr. "
                                + "Take the F train as an alternative.")
                        .displayMessage("Service alert - Check alternative routes")
                        .affectedRoutes(List.of("A", "C"))
                        .cause("MAINTENANCE")
                        .effect("SUSPENSION")
                        .activeFrom(Instant.now().minusSeconds(3600))
                        .activeUntil(Instant.now().plusSeconds(7200))
                        .build(),
                ServiceAlert.builder()
                        .alertId("ALT-002")
                        .type("WEATHER")
                        .severity("MEDIUM")
                        .headerText("Service adjustments due to heavy rain")
                        .descriptionText("Allow extra travel time. Some surface routes are experiencing delays.")
                        .displayMessage("Weather impact on schedule")
                        .affectedRoutes(List.of("B57", "B63","B67"))
                        .cause("WEATHER")
                        .effect("SIGNIFICANT_DELAYS")
                        .activeFrom(Instant.now())
                        .build()
        );
    }

    private List<CrowdingInfo> syntheticCrowding(String routeId) {
        String route = routeId != null ? routeId : "A";
        return List.of(
                CrowdingInfo.builder()
                        .vehicleId("VEH-" + route + "-001")
                        .routeId(route)
                        .capacity(100)
                        .currentPassengers(95)
                        .occupancyPercentage(95.0)
                        .level("FULL")
                        .gtfsOccupancyStatus("CRUSHED_STANDING_ROOM_ONLY")
                        .displayMessage("Vehicle at capacity - Consider next service")
                        .build(),
                CrowdingInfo.builder()
                        .vehicleId("VEH-" + route + "-002")
                        .routeId(route)
                        .capacity(100)
                        .currentPassengers(45)
                        .occupancyPercentage(45.0)
                        .level("LOW")
                        .gtfsOccupancyStatus("MANY_SEATS_AVAILABLE")
                        .build()
        );
    }
}