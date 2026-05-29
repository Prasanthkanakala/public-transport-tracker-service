package com.transport.tracker.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
* Aggregated transport data returned by the service layer.
* Contains all real-time data for a given city/route query.
*/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransportData {

    private List<VehicleLocation> vehicles;
    private List<ArrivalPrediction> arrivals;
    private List<ServiceAlert> alerts;
    private List<RoutePlan> routePlans;
    private List<CrowdingInfo> crowding;

    /** Conditional alert messages derived from threshold evaluation */
    private List<AlertMessage> conditionalAlerts;

    /** City or area this data covers */
    private String city;

    /** Route identifier if query was route-specific */
    private String routeId;
}