package com.transport.tracker.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
* Active service disruption, delay notification, or operational alert.
*/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ServiceAlert {

    private String alertId;

    /**
     * Alert type: DELAY, DISRUPTION, WEATHER, CROWDING, PLANNED_WORK, GENERAL_INFO
     */
    private String type;

    /**
     * Severity: LOW, MEDIUM, HIGH, CRITICAL
     */
    private String severity;

    /** Short headline shown in the UI */
    private String headerText;

    /** Full description */
    private String descriptionText;

    /** Formatted display message obeying conditional alert rules */
    private String displayMessage;

    /** Routes affected by this alert */
    private List<String> affectedRoutes;

    /** Stops affected by this alert */
    private List<String> affectedStops;

    private Instant activeFrom;
    private Instant activeUntil;

    /**
     * Cause: TECHNICAL_PROBLEM, ACCIDENT, WEATHER, MAINTENANCE, CONSTRUCTION, OTHER
     */
    private String cause;

    /**
     * Effect: DETOUR, STOP_MOVED, SERVICE_CHANGE, SUSPENSION, SIGNIFICANT_DELAYS, REDUCED_SERVICE
     */
    private String effect;

    /** URL for more details */
    private String url;
}