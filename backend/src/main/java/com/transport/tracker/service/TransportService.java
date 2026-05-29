package com.transport.tracker.service;

import com.transport.tracker.cache.CacheService;
import com.transport.tracker.client.TransitApiClient;
import com.transport.tracker.exception.TransitApiException;
import com.transport.tracker.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
* Core transport service implementing Option A: Resilience and Offline Mode.
*
* Degradation Strategy (in priority order):
* ──────────────────────────────────────────
*  1. OFFLINE MODE ON    → immediately return MOCK data (no API call attempted)
*  2. FRESH CACHE HIT    → return cached data (source = CACHE)
*  3. LIVE API SUCCESS   → fetch, evaluate, store in cache, return (source = LIVE)
*  4. LIVE API FAILURE   → return STALE cache data if available (source = STALE_CACHE)
*  5. NO STALE DATA      → return MOCK data (source = MOCK)
*
* This ensures the service NEVER returns an error to the end user;
* it always gracefully degrades to the next available data source.
*/
@Slf4j
@Service
public class TransportService {

    private final CacheService cacheService;
    private final AlertService alertService;
    private final MockDataService mockDataService;
    private final RoutePlannerService routePlannerService;

    // Primary provider: MTA; fallback: TransitLand and TfL (injected by qualifier)
    private final TransitApiClient mtaClient;
    private final TransitApiClient transitLandClient;
    private final TransitApiClient septaClient;
    private final TransitApiClient tflClient;

    private static record CacheLoadOutcome(TransportData data, boolean createdFresh, String providerName) {}

    public TransportService(
            CacheService cacheService,
            AlertService alertService,
            MockDataService mockDataService,
            RoutePlannerService routePlannerService,
            @Qualifier("mtaClient") TransitApiClient mtaClient,
            @Qualifier("transitLandClient") TransitApiClient transitLandClient,
            @Qualifier("septaClient") TransitApiClient septaClient,
            @Qualifier("tflClient") TransitApiClient tflClient) {
        this.cacheService = cacheService;
        this.alertService = alertService;
        this.mockDataService = mockDataService;
        this.routePlannerService = routePlannerService;
        this.mtaClient = mtaClient;
        this.transitLandClient = transitLandClient;
        this.septaClient = septaClient;
        this.tflClient = tflClient;
    }

    @Value("${transit.offline.enabled:false}")
    private boolean globalOfflineMode;

    // ─── Main entry point ─────────────────────────────────────────────────────

    /**
     * Retrieves complete transport data for a city and optional route.
     *
     * @param city     City identifier (e.g., "nyc")
     * @param routeId  Route identifier; null = all routes
     * @param offline  Per-request offline override; null = use global setting
     * @return HATEOAS-wrapped response with data and metadata
     */
    public TransportResponse<TransportData> getTransportData(String city, String routeId, Boolean offline) {
        boolean isOffline = (offline != null) ? offline : globalOfflineMode;
        String cacheKey = cacheService.buildKey(city, routeId);

        log.info("getTransportData: city={}, routeId={}, offline={}, cacheKey={}", city, routeId, isOffline, cacheKey);

        // ── Stage 1: Offline mode shortcut ────────────────────────────────────
        if (isOffline) {
            TransportData mockData = mockDataService.getMockData(city, routeId);
            enrichData(mockData);
            return buildResponse(mockData, "MOCK", null, city, routeId, true, null);
        }

        // ── Stage 2: Fresh cache hit ──────────────────────────────────────────
        Optional<TransportData> cached = cacheService.get(cacheKey);
        if (cached.isPresent()) {
            Long age = cacheService.getCacheAge(cacheKey).orElse(0L);
            log.debug("Cache HIT for key: {} (age={}s)", cacheKey, age);
            return buildResponse(cached.get(), "CACHE", age, city, routeId, false, null);
        }

        // ── Stage 3: Live API fetch ───────────────────────────────────────────
        try {
            CacheLoadOutcome result = cacheService.withKeyLock(cacheKey, () -> {
                Optional<TransportData> existing = cacheService.get(cacheKey);
                if (existing.isPresent()) {
                    return new CacheLoadOutcome(existing.get(), false, null);
                }
                TransitApiClient selectedClient = selectClient(city);
                TransportData liveData = fetchFromApis(city, routeId, selectedClient);
                cacheService.put(cacheKey, liveData);
                return new CacheLoadOutcome(liveData, true, selectedClient.getProviderName());
            });

            if (!result.createdFresh()) {
                Long age = cacheService.getCacheAge(cacheKey).orElse(0L);
                log.debug("Cache filled by concurrent request for key: {}", cacheKey);
                return buildResponse(result.data(), "CACHE", age, city, routeId, false, null);
            }

            enrichData(result.data());
            log.info("Successfully fetched and cached LIVE data for key: {}", cacheKey);
            return buildResponse(result.data(), "LIVE", 0L, city, routeId, false, result.providerName());

        } catch (TransitApiException ex) {
            log.warn("Live API fetch failed for key {}: {}. Attempting stale cache.", cacheKey, ex.getMessage());

            // ── Stage 4: Stale cache fallback ─────────────────────────────────
            Optional<TransportData> stale = cacheService.getStale(cacheKey);
            if (stale.isPresent()) {
                Long staleAge = cacheService.getCacheAge(cacheKey).orElse(null);
                log.warn("Serving STALE cache for key {} (age={}s)", cacheKey, staleAge);
                return buildResponse(stale.get(), "STALE_CACHE", staleAge, city, routeId, false, null);
            }

            // ── Stage 5: Mock fallback ─────────────────────────────────────────
            log.warn("No stale cache available for key {}. Falling back to MOCK data.", cacheKey);
            TransportData mock = mockDataService.getMockData(city, routeId);
            enrichData(mock);
            return buildResponse(mock, "MOCK", null, city, routeId, false, null);
        }
    }

    /**
     * Retrieves vehicle locations only.
     */
    public TransportResponse<List<VehicleLocation>> getVehicleLocations(
            String city, String routeId, Boolean offline) {

        TransportResponse<TransportData> full = getTransportData(city, routeId, offline);
        return TransportResponse.<List<VehicleLocation>>builder()
                .data(full.getData().getVehicles())
                .metadata(full.getMetadata())
                .links(buildVehicleLinks(city, routeId))
                .build();
    }

    /**
     * Retrieves arrival predictions for a stop.
     */
    public TransportResponse<List<ArrivalPrediction>> getArrivals(
            String stopId, String routeId, Boolean offline) {

        boolean isOffline = (offline != null) ? offline : globalOfflineMode;
        String cacheKey = cacheService.buildKey("arrivals", stopId + ":" + (routeId != null ? routeId : "all"));

        if (isOffline) {
            TransportData mock = mockDataService.getMockData(null, routeId);
            return wrapArrivals(mock.getArrivals(), "MOCK", null, stopId, routeId, true, null);
        }

        Optional<TransportData> cached = cacheService.get(cacheKey);
        if (cached.isPresent()) {
            Long age = cacheService.getCacheAge(cacheKey).orElse(0L);
            return wrapArrivals(cached.get().getArrivals(), "CACHE", age, stopId, routeId, false, null);
        }

        try {
            CacheLoadOutcome result = cacheService.withKeyLock(cacheKey, () -> {
                Optional<TransportData> existing = cacheService.get(cacheKey);
                if (existing.isPresent()) {
                    return new CacheLoadOutcome(existing.get(), false, null);
                }
                TransitApiClient client = selectClient(null);
                List<ArrivalPrediction> arrivals = client.fetchArrivalPredictions(stopId, routeId);

                TransportData data = TransportData.builder()
                        .arrivals(arrivals)
                        .routeId(routeId)
                        .build();
                cacheService.put(cacheKey, data);
                return new CacheLoadOutcome(data, true, client.getProviderName());
            });

            if (!result.createdFresh()) {
                Long age = cacheService.getCacheAge(cacheKey).orElse(0L);
                return wrapArrivals(result.data().getArrivals(), "CACHE", age, stopId, routeId, false, null);
            }
            return wrapArrivals(result.data().getArrivals(), "LIVE", 0L, stopId, routeId, false, result.providerName());

        } catch (TransitApiException ex) {
            log.warn("Arrivals API failed for stop {}: {}", stopId, ex.getMessage());
            Optional<TransportData> stale = cacheService.getStale(cacheKey);
            if (stale.isPresent()) {
                Long age = cacheService.getCacheAge(cacheKey).orElse(null);
                return wrapArrivals(stale.get().getArrivals(), "STALE_CACHE", age, stopId, routeId, false, null);
            }
            TransportData mock = mockDataService.getMockData(null, routeId);
            return wrapArrivals(mock.getArrivals(), "MOCK", null, stopId, routeId, false, null);
        }
    }

    /**
     * Retrieves service alerts for a city.
     */
    public TransportResponse<List<ServiceAlert>> getServiceAlerts(String city, Boolean offline) {
        boolean isOffline = (offline != null) ? offline : globalOfflineMode;
        String cacheKey = cacheService.buildKey(city, "alerts");

        if (isOffline) {
            TransportData mock = mockDataService.getMockData(city, null);
            return wrapAlerts(mock.getAlerts(), "MOCK", null, city, false, true, null);
        }

        Optional<TransportData> cached = cacheService.get(cacheKey);
        if (cached.isPresent()) {
            Long age = cacheService.getCacheAge(cacheKey).orElse(0L);
            return wrapAlerts(cached.get().getAlerts(), "CACHE", age, city, false, false, null);
        }

        try {
            CacheLoadOutcome result = cacheService.withKeyLock(cacheKey, () -> {
                Optional<TransportData> existing = cacheService.get(cacheKey);
                if (existing.isPresent()) {
                    return new CacheLoadOutcome(existing.get(), false, null);
                }
                TransitApiClient client = selectClient(city);
                List<ServiceAlert> alerts = client.fetchServiceAlerts(city);

                TransportData data = TransportData.builder()
                        .alerts(alerts)
                        .city(city)
                        .build();
                cacheService.put(cacheKey, data);
                return new CacheLoadOutcome(data, true, client.getProviderName());
            });

            if (!result.createdFresh()) {
                Long age = cacheService.getCacheAge(cacheKey).orElse(0L);
                return wrapAlerts(result.data().getAlerts(), "CACHE", age, city, false, false, null);
            }
            return wrapAlerts(result.data().getAlerts(), "LIVE", 0L, city, false, false, result.providerName());

        } catch (TransitApiException ex) {
            log.warn("Alerts API failed for city {}: {}", city, ex.getMessage());
            Optional<TransportData> stale = cacheService.getStale(cacheKey);
            if (stale.isPresent()) {
                Long age = cacheService.getCacheAge(cacheKey).orElse(null);
                return wrapAlerts(stale.get().getAlerts(), "STALE_CACHE", age, city, false, false, null);
            }
            TransportData mock = mockDataService.getMockData(city, null);
            return wrapAlerts(mock.getAlerts(), "MOCK", null, city, false, false, null);
        }
    }

    /**
     * Computes route plans from origin to destination.
     */
    public TransportResponse<List<RoutePlan>> getRoutePlans(
            String from, String to, String city, Boolean offline) {

        boolean isOffline = (offline != null) ? offline : globalOfflineMode;

        List<ServiceAlert> alerts = List.of();
        List<ArrivalPrediction> arrivals = List.of();
        String provider = null;

        if (!isOffline) {
            try {
                TransitApiClient client = selectClient(city);
                provider = client.getProviderName();
                alerts = client.fetchServiceAlerts(city);
                arrivals = client.fetchArrivalPredictions(from, null);
            } catch (Exception ex) {
                log.warn("Could not fetch live data for route planning: {}", ex.getMessage());
            }
        }

        List<RoutePlan> plans = routePlannerService.plan(from, to, city, alerts, arrivals);
        String source = isOffline ? "OFFLINE" : "LIVE";

        String attribution = null;
        String lagWarning = null;

        if ("NYC-MTA".equals(provider) || "MTA".equals(provider)) {
            attribution = "Data obtained from MTA";
        } else if ("TransitLand".equals(provider)) {
            attribution = "Data obtained from TransitLand";
        } else if ("TfL".equals(provider)) {
            attribution = "Data obtained from TfL";
        } else if ("OFFLINE".equals(source)) {
            attribution = "Offline route planning";
        }

        return TransportResponse.<List<RoutePlan>>builder()
                .data(plans)
                .metadata(TransportResponse.ResponseMetadata.builder()
                        .cached(false)
                        .dataSource(source)
                        .timestamp(Instant.now())
                        .city(city)
                        .offlineMode(isOffline)
                        .provider(provider)
                        .attribution(attribution)
                        .lagWarning(lagWarning)
                        .build())
                .links(buildPlanLinks(from, to, city))
                .build();
    }

    // ─── Internals ────────────────────────────────────────────────────────────

    private TransportData fetchFromApis(String city, String routeId, TransitApiClient client) {
        log.debug("Using API provider: {}", client.getProviderName());

        List<VehicleLocation> vehicles = safeCall(() -> client.fetchVehicleLocations(city, routeId), "vehicles");
        List<ArrivalPrediction> arrivals = List.of();
        List<ServiceAlert> alerts = safeCall(() -> client.fetchServiceAlerts(city), "alerts");
        List<CrowdingInfo> crowding = safeCall(() -> client.fetchCrowdingInfo(routeId), "crowding");

        // For MTA, fetch arrivals using the route-based method
        if ("NYC-MTA".equals(client.getProviderName())) {
            // Use null stopId to fetch all arrivals for the route
            arrivals = safeCall(() -> client.fetchArrivalPredictions(null, routeId), "arrivals");
        }
        // For TfL (London), fetch arrivals for a default station to populate the dashboard
        else if ("TfL".equals(client.getProviderName())) {
            // Use a major station as default for London routes
            String defaultStop = getDefaultStopForRoute(routeId, city);
            if (defaultStop != null) {
                arrivals = safeCall(() -> client.fetchArrivalPredictions(defaultStop, routeId), "arrivals");
            }
        }
        // For SEPTA, fetch arrivals for a default station to populate the dashboard
        else if ("SEPTA".equals(client.getProviderName())) {
            arrivals = safeCall(() -> client.fetchArrivalPredictions("30th Street Station", routeId), "arrivals");
        }

        // If all data is empty, the API provider is likely unavailable
        if (vehicles.isEmpty() && arrivals.isEmpty() && alerts.isEmpty() && crowding.isEmpty()) {
            throw new TransitApiException("All data from " + client.getProviderName() + " is empty; API may be unavailable");
        }

        return TransportData.builder()
                .city(city)
                .routeId(routeId)
                .vehicles(vehicles)
                .arrivals(arrivals)
                .alerts(alerts)
                .crowding(crowding)
                .routePlans(List.of())
                .build();
    }

    /**
     * Enrich data with conditional alerts and display messages.
     */
    private void enrichData(TransportData data) {
        List<AlertMessage> conditionalAlerts = alertService.evaluate(data);
        data.setConditionalAlerts(conditionalAlerts);
        alertService.enrichWithDisplayMessages(data);
    }

    /**
     * Selects the best available API client.
     * Tries TransitLand first (has API key); falls back to MTA.
     */
    private TransitApiClient selectClient(String city) {
        if (city != null && city.equalsIgnoreCase("london")) {
            if (tflClient.isAvailable()) {
                return tflClient;
            }
            log.warn("TfL unavailable for London, falling back to TransitLand");
            if (transitLandClient.isAvailable()) {
                return transitLandClient;
            }
        }

        if (city != null && city.equalsIgnoreCase("nyc")) {
            if (mtaClient.isAvailable()) {
                return mtaClient;
            }
            log.warn("MTA unavailable for NYC, falling back to TransitLand");
            if (transitLandClient.isAvailable()) {
                return transitLandClient;
            }
        }

        if (city != null && city.equalsIgnoreCase("philly")) {
            if (septaClient.isAvailable()) {
                return septaClient;
            }
            log.warn("SEPTA unavailable for Philly, falling back to TransitLand");
            if (transitLandClient.isAvailable()) {
                return transitLandClient;
            }
        }

        if (transitLandClient.isAvailable()) {
            return transitLandClient;
        }
        if (tflClient.isAvailable()) {
            return tflClient;
        }
        if (mtaClient.isAvailable()) {
            return mtaClient;
        }
        if (septaClient.isAvailable()) {
            return septaClient;
        }
        throw new TransitApiException("All upstream transit APIs are unavailable");
    }

    private <T> List<T> safeCall(java.util.function.Supplier<List<T>> supplier, String name) {
        try {
            List<T> result = supplier.get();
            return result != null ? result : List.of();
        } catch (Exception ex) {
            log.warn("Failed to fetch {}: {}", name, ex.getMessage());
            return List.of();
        }
    }

    // ─── Response builders ────────────────────────────────────────────────────

    private TransportResponse<TransportData> buildResponse(
            TransportData data, String source, Long cacheAge,
            String city, String routeId, boolean offlineMode, String provider) {

        String attribution = null;
        String lagWarning = null;

        if ("NYC-MTA".equals(provider) || "MTA".equals(provider)) {
            attribution = "Data obtained from MTA";
        } else if ("TransitLand".equals(provider)) {
            attribution = "Data obtained from TransitLand";
        } else if ("MOCK".equals(source)) {
            attribution = "Mock data for demonstration purposes";
        }

        if (cacheAge != null && cacheAge > 60) {
            lagWarning = "Data may not be real-time due to caching lag";
        }

        return TransportResponse.<TransportData>builder()
                .data(data)
                .metadata(TransportResponse.ResponseMetadata.builder()
                        .cached(!source.equals("LIVE"))
                        .cacheAgeSeconds(cacheAge)
                        .dataSource(source)
                        .timestamp(Instant.now())
                        .city(city)
                        .routeId(routeId)
                        .offlineMode(offlineMode)
                        .provider(provider)
                        .attribution(attribution)
                        .lagWarning(lagWarning)
                        .build())
                .links(buildTransportLinks(city, routeId))
                .build();
    }

    private TransportResponse<List<ArrivalPrediction>> wrapArrivals(
            List<ArrivalPrediction> arrivals, String source, Long cacheAge,
            String stopId, String routeId, boolean offline, String provider) {

        String attribution = null;
        String lagWarning = null;

        if ("NYC-MTA".equals(provider) || "MTA".equals(provider)) {
            attribution = "Data obtained from MTA";
        } else if ("TransitLand".equals(provider)) {
            attribution = "Data obtained from TransitLand";
        } else if ("MOCK".equals(source)) {
            attribution = "Mock data for demonstration purposes";
        }

        if (cacheAge != null && cacheAge > 60) {
            lagWarning = "Data may not be real-time due to caching lag";
        }

        return TransportResponse.<List<ArrivalPrediction>>builder()
                .data(arrivals)
                .metadata(TransportResponse.ResponseMetadata.builder()
                        .cached(!source.equals("LIVE"))
                        .cacheAgeSeconds(cacheAge)
                        .dataSource(source)
                        .timestamp(Instant.now())
                        .offlineMode(offline)
                        .provider(provider)
                        .attribution(attribution)
                        .lagWarning(lagWarning)
                        .build())
                .links(buildArrivalLinks(stopId, routeId))
                .build();
    }

    private TransportResponse<List<ServiceAlert>> wrapAlerts(
            List<ServiceAlert> alerts, String source, Long cacheAge,
            String city, boolean cached, boolean offline, String provider) {

        String attribution = null;
        String lagWarning = null;

        if ("NYC-MTA".equals(provider) || "MTA".equals(provider)) {
            attribution = "Data obtained from MTA";
        } else if ("TransitLand".equals(provider)) {
            attribution = "Data obtained from TransitLand";
        } else if ("MOCK".equals(source)) {
            attribution = "Mock data for demonstration purposes";
        }

        if (cacheAge != null && cacheAge > 60) {
            lagWarning = "Data may not be real-time due to caching lag";
        }

        return TransportResponse.<List<ServiceAlert>>builder()
                .data(alerts)
                .metadata(TransportResponse.ResponseMetadata.builder()
                        .cached(cached || !source.equals("LIVE"))
                        .cacheAgeSeconds(cacheAge)
                        .dataSource(source)
                        .timestamp(Instant.now())
                        .city(city)
                        .offlineMode(offline)
                        .provider(provider)
                        .attribution(attribution)
                        .lagWarning(lagWarning)
                        .build())
                .links(buildAlertLinks(city))
                .build();
    }

    // ─── HATEOAS link builders ────────────────────────────────────────────────

    private Map<String, TransportResponse.HalLink> buildTransportLinks(String city, String routeId) {
        Map<String, TransportResponse.HalLink> links = new LinkedHashMap<>();
        String base = "/api/v1/transport";
        String params = "?city=" + nvl(city) + "&routeId=" + nvl(routeId);
        links.put("self",     hal(base + params));
        links.put("vehicles", hal(base + "/vehicles" + params));
        links.put("alerts",   hal(base + "/alerts?city=" + nvl(city)));
        links.put("arrivals", hal(base + "/arrivals" + params));
        links.put("plan",     hal(base + "/plan?from=ORIGIN&to=DEST&city=" + nvl(city)));
        links.put("cache",    hal("/api/v1/cache/stats"));
        return links;
    }

    private Map<String, TransportResponse.HalLink> buildVehicleLinks(String city, String routeId) {
        Map<String, TransportResponse.HalLink> links = new LinkedHashMap<>();
        String params = "?city=" + nvl(city) + "&routeId=" + nvl(routeId);
        links.put("self",      hal("/api/v1/transport/vehicles" + params));
        links.put("transport", hal("/api/v1/transport" + params));
        return links;
    }

    private Map<String, TransportResponse.HalLink> buildArrivalLinks(String stopId, String routeId) {
        Map<String, TransportResponse.HalLink> links = new LinkedHashMap<>();
        links.put("self", hal("/api/v1/transport/arrivals?stopId=" + nvl(stopId) + "&routeId=" + nvl(routeId)));
        return links;
    }

    private Map<String, TransportResponse.HalLink> buildAlertLinks(String city) {
        Map<String, TransportResponse.HalLink> links = new LinkedHashMap<>();
        links.put("self",      hal("/api/v1/transport/alerts?city=" + nvl(city)));
        links.put("transport", hal("/api/v1/transport?city=" + nvl(city)));
        return links;
    }

    private Map<String, TransportResponse.HalLink> buildPlanLinks(String from, String to, String city) {
        Map<String, TransportResponse.HalLink> links = new LinkedHashMap<>();
        links.put("self", hal("/api/v1/transport/plan?from=" + nvl(from) + "&to=" + nvl(to) + "&city=" + nvl(city)));
        return links;
    }

    private TransportResponse.HalLink hal(String href) {
        return TransportResponse.HalLink.builder().href(href).method("GET").build();
    }

    private String nvl(String s) {
        return s != null ? s : "";
    }
    
    /**
     * Returns a default stop ID for a given route to fetch arrival predictions.
     * This is used for routes where we want to display arrivals without requiring a specific stop.
     */
    private String getDefaultStopForRoute(String routeId, String city) {
        if (city == null || routeId == null) {
            return null;
        }
        
        // London TfL default stops (major stations)
        if (city.equalsIgnoreCase("london")) {
            return switch (routeId.toLowerCase()) {
                case "central" -> "940GZZLUTCR"; // Tottenham Court Road
                case "northern" -> "940GZZLUKSX"; // King's Cross St. Pancras
                case "piccadilly" -> "940GZZLUPCC"; // Piccadilly Circus
                case "victoria" -> "940GZZLUVIC"; // Victoria
                case "district" -> "940GZZLUWSM"; // Westminster
                case "circle" -> "940GZZLULVT"; // Liverpool Street
                case "metropolitan" -> "940GZZLUBKR"; // Baker Street
                case "bakerloo" -> "940GZZLUOXC"; // Oxford Circus
                case "jubilee" -> "940GZZLUWLO"; // Waterloo
                case "hammersmith-city" -> "940GZZLUPAH"; // Paddington
                case "waterloo-city" -> "940GZZLUWLO"; // Waterloo
                default -> "940GZZLUTCR"; // Default to Tottenham Court Road
            };
        }
        
        return null;
    }
}
