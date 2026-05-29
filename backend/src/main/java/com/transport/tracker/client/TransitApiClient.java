package com.transport.tracker.client;

import com.transport.tracker.model.*;

import java.util.List;

/**
* Contract for all transit data provider clients.
* Follows Interface Segregation and Dependency Inversion (SOLID).
*
* Implementations:
* - {@link MtaApiClient}       → NYC MTA API
* - {@link TransitLandApiClient} → Transit.land generic API
* - {@link TflApiClient}       → TfL Unified API
*/
public interface TransitApiClient {

    /**
     * Fetches real-time vehicle positions for a given city/route.
     *
     * @param city    City identifier (e.g., "nyc", "london")
     * @param routeId Route identifier (e.g., "A", "1", "M15")
     * @return List of current vehicle locations; empty list if none found
     */
    List<VehicleLocation> fetchVehicleLocations(String city, String routeId);

    /**
     * Fetches arrival predictions for a stop.
     *
     * @param stopId  Stop/station identifier
     * @param routeId Route to filter by (null = all routes at stop)
     * @return Ordered list of upcoming arrivals
     */
    List<ArrivalPrediction> fetchArrivalPredictions(String stopId, String routeId);

    /**
     * Fetches active service alerts for a city or area.
     *
     * @param city City identifier
     * @return Active service alerts ordered by severity
     */
    List<ServiceAlert> fetchServiceAlerts(String city);

    /**
     * Fetches suggested route plans from origin to destination.
     *
     * @param from    Origin stop/address
     * @param to      Destination stop/address
     * @param city    City identifier
     * @return List of route plan options (optimal first)
     */
    List<RoutePlan> fetchRoutePlans(String from, String to, String city);

    /**
     * Fetches crowding/capacity information for a route.
     *
     * @param routeId Route identifier
     * @return Crowding info per vehicle or stop
     */
    List<CrowdingInfo> fetchCrowdingInfo(String routeId);

    /**
     * Tests whether the upstream API is reachable.
     * Used to decide whether to use cache or live data.
     *
     * @return true if API is available and responding
     */
    boolean isAvailable();

    /**
     * Returns the human-readable provider name for logging and metadata.
     */
    String getProviderName();
}