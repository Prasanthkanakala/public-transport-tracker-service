package com.transport.tracker.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
* A complete journey plan from origin to destination,
* potentially containing multiple legs and transfers.
*/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoutePlan {

    private String planId;
    private String origin;
    private String destination;
    private String city;

    private Instant departureTime;
    private Instant arrivalTime;

    /** Total journey duration in minutes */
    private Integer durationMinutes;

    /** Number of transfers required */
    private int transfers;

    /** Ordered list of journey legs */
    private List<Leg> legs;

    /**
     * Plan status: OPTIMAL, ALTERNATIVE, DISRUPTED, NO_SERVICE
     */
    private String status;

    /** Confidence score 0.0–1.0 based on real-time data availability */
    private double confidence;

    /** Walking distance in meters */
    private Integer walkingDistanceMeters;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Leg {

        /** Transport mode: BUS, SUBWAY, RAIL, FERRY, WALK, TRAM */
        private String mode;

        private String routeId;
        private String routeName;
        private String tripId;
        private String headsign;

        private String fromStopId;
        private String fromStopName;
        private String toStopId;
        private String toStopName;

        private Instant departureTime;
        private Instant arrivalTime;

        /** Leg duration in minutes */
        private Integer durationMinutes;

        /** Number of stops for transit legs */
        private Integer numStops;

        /** Delay in seconds (positive = late) */
        private Integer delaySeconds;

        /** Walking distance in meters for WALK legs */
        private Integer walkingDistanceMeters;
    }
}