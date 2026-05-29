package com.transport.tracker.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
* Predicted or scheduled arrival of a transit vehicle at a stop.
*/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ArrivalPrediction {

    private String stopId;
    private String stopName;
    private String routeId;
    private String routeName;
    private String tripId;
    private String headsign;

    /** Scheduled arrival time from static GTFS */
    private Instant scheduledArrival;

    /** Real-time predicted arrival; null if not available */
    private Instant predictedArrival;

    /** Delay in seconds. Derived as predictedArrival - scheduledArrival */
    private Integer delaySeconds;

    /**
     * Status: ON_TIME, DELAYED, EARLY, CANCELLED, NO_DATA
     */
    private String status;

    /** Platform or track number */
    private String platform;

    /** True = real-time data; false = schedule only */
    private boolean realtime;

    /** Sequence of this stop within the trip */
    private Integer stopSequence;

    /** Minutes until arrival (convenience field) */
    private Integer minutesToArrival;
}