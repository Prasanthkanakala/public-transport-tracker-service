package com.transport.tracker.controller;

import com.transport.tracker.model.*;
import com.transport.tracker.service.TransportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
* REST controller exposing all public transport tracker endpoints.
*
* Base path: /api/v1/transport
* All responses are wrapped in {@link TransportResponse} (HATEOAS HAL format).
*/
@RestController
@RequestMapping("/api/v1/transport")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAnyRole('VIEWER','OPERATOR','ADMIN')")
@Tag(name = "Transport", description = "Real-time public transport data endpoints")
public class TransportController {

    private final TransportService transportService;

    // ─── Full transport data ──────────────────────────────────────────────────

    @Operation(
        summary = "Get complete transport data",
        description = "Returns vehicles, arrivals, alerts, crowding and conditional alert messages "
                + "for a city or route. Implements full resilience chain (LIVE → CACHE → STALE_CACHE → MOCK)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Data returned (may be cached or mock)"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters",
                     content = @Content(schema = @Schema(implementation = com.transport.tracker.exception.ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<TransportResponse<TransportData>> getTransportData(
            @Parameter(description = "City identifier (e.g. nyc, london, sydney)", example = "nyc")
            @RequestParam(required = false) String city,

            @Parameter(description = "Route identifier (e.g. A, 1, M15, 4B)", example = "A")
            @RequestParam(required = false) String routeId,

            @Parameter(description = "Force offline / mock mode for this request")
            @RequestParam(required = false) Boolean offline) {

        return ResponseEntity.ok(transportService.getTransportData(city, routeId, offline));
    }

    // ─── Vehicle locations ────────────────────────────────────────────────────

    @Operation(
        summary = "Get real-time vehicle locations",
        description = "Returns GPS positions, speed, heading and delay for all vehicles on a route."
    )
    @GetMapping("/vehicles")
    public ResponseEntity<TransportResponse<List<VehicleLocation>>> getVehicles(
            @Parameter(description = "City identifier", example = "nyc")
            @RequestParam(required = false) String city,

            @Parameter(description = "Route identifier", example = "A")
            @RequestParam(required = false) String routeId,

            @Parameter(description = "Offline mode override")
            @RequestParam(required = false) Boolean offline) {

        return ResponseEntity.ok(transportService.getVehicleLocations(city, routeId, offline));
    }

    // ─── Arrival predictions ──────────────────────────────────────────────────

    @Operation(
        summary = "Get arrival predictions for a stop",
        description = "Returns upcoming arrivals at a given stop, with scheduled and predicted times, "
                + "delay in seconds, and realtime flag."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Arrival predictions returned"),
        @ApiResponse(responseCode = "400", description = "stopId is required")
    })
    @GetMapping("/arrivals")
    public ResponseEntity<TransportResponse<List<ArrivalPrediction>>> getArrivals(
            @Parameter(description = "Stop or station identifier", required = true, example = "STOP-A-001")
            @RequestParam @NotBlank String stopId,

            @Parameter(description = "Filter by route", example = "A")
            @RequestParam(required = false) String routeId,

            @Parameter(description = "Offline mode override")
            @RequestParam(required = false) Boolean offline) {

        return ResponseEntity.ok(transportService.getArrivals(stopId, routeId, offline));
    }

    // ─── Service alerts ───────────────────────────────────────────────────────

    @Operation(
        summary = "Get active service alerts",
        description = "Returns disruptions, delays, planned works and weather warnings for a city."
    )
    @GetMapping("/alerts")
    public ResponseEntity<TransportResponse<List<ServiceAlert>>> getAlerts(
            @Parameter(description = "City identifier", example = "nyc")
            @RequestParam(required = false) String city,

            @Parameter(description = "Offline mode override")
            @RequestParam(required = false) Boolean offline) {

        return ResponseEntity.ok(transportService.getServiceAlerts(city, offline));
    }

    // ─── Route planning ───────────────────────────────────────────────────────

    @Operation(
        summary = "Plan a journey",
        description = "Returns 1–3 route plan options from origin to destination, with legs, "
                + "transfers, duration and confidence score. Marks plans DISRUPTED if active alerts affect the route."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Route plans returned"),
        @ApiResponse(responseCode = "400", description = "from and to are required")
    })
    @GetMapping("/plan")
    public ResponseEntity<TransportResponse<List<RoutePlan>>> planRoute(
            @Parameter(description = "Origin stop or address", required = true, example = "Times Square-42 St")
            @RequestParam @NotBlank String from,

            @Parameter(description = "Destination stop or address", required = true, example = "Atlantic Av-Barclays Ctr")
            @RequestParam @NotBlank String to,

            @Parameter(description = "City identifier", example = "nyc")
            @RequestParam(required = false) String city,

            @Parameter(description = "Offline mode override")
            @RequestParam(required = false) Boolean offline) {

        return ResponseEntity.ok(transportService.getRoutePlans(from, to, city, offline));
    }

    // ─── Crowding ─────────────────────────────────────────────────────────────

    @Operation(
        summary = "Get crowding information for a route",
        description = "Returns capacity, occupancy percentage and crowding level (LOW/MEDIUM/HIGH/FULL) "
                + "per vehicle on the route."
    )
    @GetMapping("/crowding")
    public ResponseEntity<TransportResponse<TransportData>> getCrowding(
            @Parameter(description = "Route identifier", required = true, example = "A")
            @RequestParam @NotBlank String routeId,

            @Parameter(description = "City identifier", example = "nyc")
            @RequestParam(required = false) String city,

            @Parameter(description = "Offline mode override")
            @RequestParam(required = false) Boolean offline) {

        return ResponseEntity.ok(transportService.getTransportData(city, routeId, offline));
    }
}