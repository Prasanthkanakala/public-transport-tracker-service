package com.transport.tracker.service;

import com.transport.tracker.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
* Evaluates transit data against business rules and generates
* conditional alert messages to display in the UI.
*
* Rules:
*  1. Any vehicle delayed > 15 minutes  → DELAY    / "Significant delays - Plan accordingly"
*  2. Active service disruption present → DISRUPTION / "Service alert - Check alternative routes"
*  3. Any vehicle crowding HIGH / FULL  → CROWDING  / "Vehicle at capacity - Consider next service"
*  4. Alert with cause == WEATHER       → WEATHER   / "Weather impact on schedule"
*/
@Slf4j
@Service
public class AlertService {

    @Value("${transit.delays.significant-threshold-minutes:15}")
    private int significantDelayMinutes;

    /**
     * Evaluates all available transport data and returns the applicable
     * conditional alert messages.
     */
    public List<AlertMessage> evaluate(TransportData data) {
        List<AlertMessage> alerts = new ArrayList<>();

        if (data == null) return alerts;

        checkVehicleDelays(data.getVehicles(), alerts);
        checkServiceDisruptions(data.getAlerts(), alerts);
        checkCrowding(data.getCrowding(), alerts);
        checkWeatherAlerts(data.getAlerts(), alerts);

        log.debug("Generated {} conditional alerts", alerts.size());
        return alerts;
    }

    // ─── Rule: Delay > 15 minutes ─────────────────────────────────────────────

    private void checkVehicleDelays(List<VehicleLocation> vehicles, List<AlertMessage> out) {
        if (vehicles == null) return;
        int thresholdSeconds = significantDelayMinutes * 60;

        boolean significantDelay = vehicles.stream()
                .anyMatch(v -> v.getDelaySeconds() != null && v.getDelaySeconds() > thresholdSeconds);

        if (significantDelay) {
            out.add(AlertMessage.builder()
                    .type("DELAY")
                    .level("WARNING")
                    .message("Significant delays - Plan accordingly")
                    .icon("clock-alert")
                    .build());
            log.info("Conditional alert: Significant delay > {} min detected", significantDelayMinutes);
        }
    }

    // ─── Rule: Service disruption alert present ───────────────────────────────

    private void checkServiceDisruptions(List<ServiceAlert> serviceAlerts, List<AlertMessage> out) {
        if (serviceAlerts == null) return;
        boolean hasDisruption = serviceAlerts.stream()
                .anyMatch(a -> "DISRUPTION".equalsIgnoreCase(a.getType())
                        || "SUSPENSION".equalsIgnoreCase(a.getEffect())
                        || "DETOUR".equalsIgnoreCase(a.getEffect()));

        if (hasDisruption) {
            out.add(AlertMessage.builder()
                    .type("DISRUPTION")
                    .level("ERROR")
                    .message("Service alert - Check alternative routes")
                    .icon("triangle-alert")
                    .build());
            log.info("Conditional alert: Service disruption detected");
        }
    }

    // ─── Rule: Vehicle at HIGH / FULL crowding ────────────────────────────────

    private void checkCrowding(List<CrowdingInfo> crowding, List<AlertMessage> out) {
        if (crowding == null) return;
        boolean highCrowding = crowding.stream()
                .anyMatch(c -> "HIGH".equalsIgnoreCase(c.getLevel())
                        || "FULL".equalsIgnoreCase(c.getLevel())
                        || "CRUSHED_STANDING_ROOM_ONLY".equalsIgnoreCase(c.getGtfsOccupancyStatus())
                        || "FULL".equalsIgnoreCase(c.getGtfsOccupancyStatus()));

        if (highCrowding) {
            out.add(AlertMessage.builder()
                    .type("CROWDING")
                    .level("WARNING")
                    .message("Vehicle at capacity - Consider next service")
                    .icon("users-alert")
                    .build());
            log.info("Conditional alert: High crowding detected");
        }
    }

    // ─── Rule: Weather affecting service ─────────────────────────────────────

    private void checkWeatherAlerts(List<ServiceAlert> serviceAlerts, List<AlertMessage> out) {
        if (serviceAlerts == null) return;
        boolean weatherImpact = serviceAlerts.stream()
                .anyMatch(a -> "WEATHER".equalsIgnoreCase(a.getCause())
                        || "WEATHER".equalsIgnoreCase(a.getType()));

        if (weatherImpact) {
            out.add(AlertMessage.builder()
                    .type("WEATHER")
                    .level("INFO")
                    .message("Weather impact on schedule")
                    .icon("cloud-alert")
                    .build());
            log.info("Conditional alert: Weather impact detected");
        }
    }

    /**
     * Also enriches raw VehicleLocation and CrowdingInfo objects with user-facing
     * display messages for inline presentation.
     */
    public void enrichWithDisplayMessages(TransportData data) {
        if (data == null) return;

        if (data.getCrowding() != null) {
            data.getCrowding().forEach(c -> {
                if ("HIGH".equalsIgnoreCase(c.getLevel()) || "FULL".equalsIgnoreCase(c.getLevel())) {
                    c.setDisplayMessage("Vehicle at capacity - Consider next service");
                }
            });
        }
    }
}