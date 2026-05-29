package com.transport.tracker.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.transport.tracker.exception.TransitApiException;
import com.transport.tracker.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * HTTP client for the TfL API.
 *
 * API Docs: https://api.tfl.gov.uk/
 * Uses java.net.http.HttpClient and JSON parsing via Jackson.
 */
@Slf4j
@Component
public class TflApiClient implements TransitApiClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${transit.api.tfl.base-url:https://api.tfl.gov.uk}")
    private String baseUrl;

    @Value("${transit.api.tfl.api-key:}")
    private String apiKey;

    @Value("${transit.api.tfl.timeout-ms:5000}")
    private int timeoutMs;

    public TflApiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(5000))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public List<VehicleLocation> fetchVehicleLocations(String city, String routeId) {
        if (routeId == null || routeId.isBlank()) {
            log.warn("TfL: routeId is null or blank, returning empty vehicle list");
            return List.of();
        }

        String url = baseUrl + "/Line/" + encode(routeId) + "/Arrivals" + authQuery();
        log.debug("TfL: fetching vehicle arrivals for line {} from {}", routeId, url);

        try {
            HttpResponse<String> response = sendGet(url);
            if (response.statusCode() == 200) {
                return parseVehicles(response.body(), routeId);
            }
            log.warn("TfL vehicles: HTTP {} for line {}", response.statusCode(), routeId);
            return List.of();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("TfL vehicles API error: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public List<ArrivalPrediction> fetchArrivalPredictions(String stopId, String routeId) {
        if (stopId == null || stopId.isBlank()) {
            log.warn("TfL: stopId is null or blank, returning empty arrival list");
            return List.of();
        }

        String url = baseUrl + "/StopPoint/" + encode(stopId) + "/Arrivals" + authQuery();
        if (routeId != null && !routeId.isBlank()) {
            url += "&lineId=" + encode(routeId);
        }
        log.debug("TfL: fetching arrivals for stop {} from {}", stopId, url);

        try {
            HttpResponse<String> response = sendGet(url);
            if (response.statusCode() == 200) {
                return parseArrivals(response.body(), stopId);
            }
            log.warn("TfL arrivals: HTTP {} for stop {}", response.statusCode(), stopId);
            return List.of();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransitApiException("TfL arrivals API unavailable: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ServiceAlert> fetchServiceAlerts(String city) {
        String url = baseUrl + "/Line/Mode/tube/Status" + authQuery();
        log.debug("TfL: fetching service status alerts for city {} from {}", city, url);

        try {
            HttpResponse<String> response = sendGet(url);
            if (response.statusCode() == 200) {
                return parseAlerts(response.body());
            }
            log.warn("TfL alerts: HTTP {}", response.statusCode());
            return List.of();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransitApiException("TfL alerts API unavailable: " + e.getMessage(), e);
        }
    }

    @Override
    public List<RoutePlan> fetchRoutePlans(String from, String to, String city) {
        log.debug("TfL route planning is not supported directly by API; delegating to internal route planner");
        return List.of();
    }

    @Override
    public List<CrowdingInfo> fetchCrowdingInfo(String routeId) {
        log.debug("TfL crowding data is not available from this API endpoint");
        return List.of();
    }

    @Override
    public boolean isAvailable() {
        try {
            String url = baseUrl + "/Line/Mode/tube/Status" + authQuery();
            HttpResponse<String> response = sendGet(url);
            return response.statusCode() < 500;
        } catch (Exception e) {
            log.warn("TfL availability check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getProviderName() {
        return "TfL";
    }

    private HttpResponse<String> sendGet(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .timeout(Duration.ofMillis(timeoutMs))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private List<VehicleLocation> parseVehicles(String json, String routeId) {
        List<VehicleLocation> vehicles = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    String vehicleId = node.path("vehicleId").asText("UNKNOWN");
                    Instant timestamp = parseInstant(node.path("timestamp").asText(null));
                    vehicles.add(VehicleLocation.builder()
                            .vehicleId(vehicleId)
                            .routeId(routeId)
                            .tripId(node.path("tripId").asText(null))
                            .latitude(node.path("latitude").asDouble(0))
                            .longitude(node.path("longitude").asDouble(0))
                            .bearing(node.has("bearing") ? node.path("bearing").asDouble() : null)
                            .status(node.path("currentLocation").asText(null))
                            .currentStopId(node.path("naptanId").asText(null))
                            .nextStopId(node.path("destinationNaptanId").asText(null))
                            .delaySeconds(node.has("timeToStation") ? node.path("timeToStation").asInt(0) : null)
                            .timestamp(timestamp)
                            .delayLabel(node.has("timeToStation") ? node.path("timeToStation").asInt(0) + " sec to stop" : null)
                            .build());
                }
            }
        } catch (Exception e) {
            log.error("Error parsing TfL vehicle data: {}", e.getMessage(), e);
        }
        return vehicles;
    }

    private List<ArrivalPrediction> parseArrivals(String json, String stopId) {
        List<ArrivalPrediction> predictions = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    Instant expected = parseInstant(node.path("expectedArrival").asText(null));
                    Instant scheduled = parseInstant(node.path("scheduledArrival").asText(null));
                    if (scheduled == null) {
                        scheduled = expected != null ? expected.minusSeconds(node.path("timeToStation").asLong(0)) : Instant.now();
                    }
                    long delaySeconds = expected != null && scheduled != null ? expected.getEpochSecond() - scheduled.getEpochSecond() : 0;
                    predictions.add(ArrivalPrediction.builder()
                            .stopId(stopId)
                            .stopName(node.path("stationName").asText(null))
                            .routeId(node.path("lineId").asText(routeIdFromNode(node)))
                            .headsign(node.path("destinationName").asText(null))
                            .scheduledArrival(scheduled)
                            .predictedArrival(expected != null ? expected : scheduled)
                            .delaySeconds((int) delaySeconds)
                            .status(delaySeconds > 60 ? "DELAYED" : delaySeconds < -60 ? "EARLY" : "ON_TIME")
                            .realtime(expected != null)
                            .minutesToArrival(node.path("timeToStation").asInt(0) / 60)
                            .build());
                }
            }
        } catch (Exception e) {
            log.error("Error parsing TfL arrival data: {}", e.getMessage(), e);
        }
        return predictions;
    }

    private List<ServiceAlert> parseAlerts(String json) {
        List<ServiceAlert> alerts = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    String lineId = node.path("id").asText(null);
                    String lineName = node.path("name").asText(null);
                    String severity = node.path("lineStatuses").isArray() && node.path("lineStatuses").size() > 0
                            ? node.path("lineStatuses").get(0).path("statusSeverityDescription").asText("UNKNOWN")
                            : "UNKNOWN";
                    String description = node.path("lineStatuses").isArray() && node.path("lineStatuses").size() > 0
                            ? node.path("lineStatuses").get(0).path("reason").asText(null)
                            : null;
                    alerts.add(ServiceAlert.builder()
                            .alertId(lineId)
                            .type("DISRUPTION")
                            .severity(severity)
                            .headerText(lineName != null ? lineName + " status" : "TfL status")
                            .descriptionText(description)
                            .cause(description != null && description.toLowerCase().contains("weather") ? "WEATHER" : "OTHER")
                            .effect("SERVICE_CHANGE")
                            .affectedRoutes(lineId != null ? List.of(lineId) : List.of())
                            .build());
                }
            }
        } catch (Exception e) {
            log.error("Error parsing TfL alerts: {}", e.getMessage(), e);
        }
        return alerts;
    }

    private Instant parseInstant(String text) {
        try {
            return text == null || text.isBlank() ? null : Instant.parse(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String routeIdFromNode(JsonNode node) {
        return node.path("lineId").asText(node.path("lineName").asText(null));
    }

    private String authQuery() {
        return apiKey == null || apiKey.isBlank() ? "" : "?app_key=" + encode(apiKey);
    }

    private String encode(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
