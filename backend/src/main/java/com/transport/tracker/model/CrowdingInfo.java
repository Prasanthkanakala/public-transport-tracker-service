package com.transport.tracker.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
* Passenger crowding and capacity data for a vehicle or stop.
*/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CrowdingInfo {

    private String vehicleId;
    private String routeId;
    private String tripId;
    private String stopId;

    /** Total passenger capacity */
    private Integer capacity;

    /** Estimated current passenger count */
    private Integer currentPassengers;

    /** Occupancy as a percentage 0–100 */
    private Double occupancyPercentage;

    /**
     * Crowding level: LOW (< 50%), MEDIUM (50–75%), HIGH (75–90%), FULL (>90%)
     */
    private String level;

    /**
     * GTFS-RT OccupancyStatus string:
     * EMPTY, MANY_SEATS_AVAILABLE, FEW_SEATS_AVAILABLE,
     * STANDING_ROOM_ONLY, CRUSHED_STANDING_ROOM_ONLY, FULL
     */
    private String gtfsOccupancyStatus;

    /** User-facing message when crowding is high */
    private String displayMessage;
    
    /** Timestamp of the crowding data */
    private java.time.Instant timestamp;
}