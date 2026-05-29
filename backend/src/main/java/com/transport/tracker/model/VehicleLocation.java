package com.transport.tracker.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
* Real-time position and status of a transit vehicle (bus, train, ferry, etc.)
*/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VehicleLocation {

    /** Unique vehicle identifier */
    private String vehicleId;

    /** Route the vehicle is currently serving */
    private String routeId;

    /** Current active trip identifier */
    private String tripId;

    /** WGS-84 latitude */
    private double latitude;

    /** WGS-84 longitude */
    private double longitude;

    /** Compass bearing in degrees (0-359) */
    private Double bearing;

    /** Speed in km/h */
    private Double speedKmh;

    /**
     * Vehicle status: IN_TRANSIT_TO, STOPPED_AT, INCOMING_AT
     */
    private String status;

    /** Stop ID where vehicle is currently at or heading to */
    private String currentStopId;

    /** Next stop ID */
    private String nextStopId;

    /** Delay in seconds (positive = late, negative = early) */
    private Integer delaySeconds;

    /** GTFS occupancy status: EMPTY, MANY_SEATS, FEW_SEATS, STANDING_ONLY, CRUSHED, FULL */
    private String occupancyStatus;

    /** Timestamp of this position report */
    private Instant timestamp;

    /** Human-readable delay label, e.g. "5 min late" */
    private String delayLabel;
}