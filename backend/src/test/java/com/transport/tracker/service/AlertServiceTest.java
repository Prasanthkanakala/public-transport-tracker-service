package com.transport.tracker.service;

import com.transport.tracker.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
* Unit tests for AlertService conditional alert rules.
*
* Tests verify:
* - Delay > 15 min triggers DELAY alert
* - DISRUPTION alert type triggers DISRUPTION message
* - HIGH/FULL crowding triggers CROWDING message
* - WEATHER cause triggers WEATHER message
* - No false positives when conditions are not met
*/
@ExtendWith(MockitoExtension.class)
@DisplayName("AlertService")
class AlertServiceTest {

    @InjectMocks
    private AlertService alertService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(alertService, "significantDelayMinutes", 15);
    }

    @Nested
    @DisplayName("Delay threshold rule")
    class DelayTests {

        @Test
        @DisplayName("Should generate DELAY alert when vehicle delay exceeds 15 minutes")
        void shouldGenerateDelayAlertWhenDelayExceeds15Min() {
            // 16 minutes = 960 seconds
            VehicleLocation delayed = VehicleLocation.builder()
                    .vehicleId("VEH-001")
                    .delaySeconds(960)
                    .build();

            TransportData data = TransportData.builder()
                    .vehicles(List.of(delayed))
                    .build();

            List<AlertMessage> alerts = alertService.evaluate(data);

            assertThat(alerts).anyMatch(a ->
                    "DELAY".equals(a.getType()) &&
                    "Significant delays - Plan accordingly".equals(a.getMessage())
            );
        }

        @Test
        @DisplayName("Should NOT generate DELAY alert when delay is below threshold")
        void shouldNotGenerateDelayAlertWhenDelayBelowThreshold() {
            VehicleLocation slightlyLate = VehicleLocation.builder()
                    .vehicleId("VEH-002")
                    .delaySeconds(600) // 10 minutes, below threshold
                    .build();

            TransportData data = TransportData.builder()
                    .vehicles(List.of(slightlyLate))
                    .build();

            List<AlertMessage> alerts = alertService.evaluate(data);

            assertThat(alerts).noneMatch(a -> "DELAY".equals(a.getType()));
        }

        @Test
        @DisplayName("Should NOT generate DELAY alert when delay is exactly 15 minutes")
        void shouldNotGenerateDelayAlertAtExactThreshold() {
            VehicleLocation exactThreshold = VehicleLocation.builder()
                    .vehicleId("VEH-003")
                    .delaySeconds(900) // exactly 15 minutes
                    .build();

            TransportData data = TransportData.builder()
                    .vehicles(List.of(exactThreshold))
                    .build();

            List<AlertMessage> alerts = alertService.evaluate(data);

            assertThat(alerts).noneMatch(a -> "DELAY".equals(a.getType()));
        }
    }

    @Nested
    @DisplayName("Service disruption rule")
    class DisruptionTests {

        @Test
        @DisplayName("Should generate DISRUPTION alert when service alert type is DISRUPTION")
        void shouldGenerateDisruptionAlert() {
            ServiceAlert disruption = ServiceAlert.builder()
                    .alertId("ALT-001")
                    .type("DISRUPTION")
                    .severity("HIGH")
                    .build();

            TransportData data = TransportData.builder()
                    .alerts(List.of(disruption))
                    .build();

            List<AlertMessage> alerts = alertService.evaluate(data);

            assertThat(alerts).anyMatch(a ->
                    "DISRUPTION".equals(a.getType()) &&
                    "Service alert - Check alternative routes".equals(a.getMessage())
            );
        }

        @Test
        @DisplayName("Should generate DISRUPTION alert when effect is SUSPENSION")
        void shouldGenerateDisruptionAlertForSuspension() {
            ServiceAlert suspension = ServiceAlert.builder()
                    .alertId("ALT-002")
                    .type("PLANNED_WORK")
                    .effect("SUSPENSION")
                    .build();

            TransportData data = TransportData.builder()
                    .alerts(List.of(suspension))
                    .build();

            List<AlertMessage> alerts = alertService.evaluate(data);

            assertThat(alerts).anyMatch(a -> "DISRUPTION".equals(a.getType()));
        }
    }

    @Nested
    @DisplayName("Crowding rule")
    class CrowdingTests {

        @Test
        @DisplayName("Should generate CROWDING alert when vehicle level is HIGH")
        void shouldGenerateCrowdingAlertForHighLevel() {
            CrowdingInfo highCrowding = CrowdingInfo.builder()
                    .vehicleId("VEH-001")
                    .level("HIGH")
                    .occupancyPercentage(85.0)
                    .build();

            TransportData data = TransportData.builder()
                    .crowding(List.of(highCrowding))
                    .build();

            List<AlertMessage> alerts = alertService.evaluate(data);

            assertThat(alerts).anyMatch(a ->
                    "CROWDING".equals(a.getType()) &&
                    "Vehicle at capacity - Consider next service".equals(a.getMessage())
            );
        }

        @Test
        @DisplayName("Should generate CROWDING alert when GTFS status is FULL")
        void shouldGenerateCrowdingAlertForFullGtfsStatus() {
            CrowdingInfo full = CrowdingInfo.builder()
                    .vehicleId("VEH-002")
                    .gtfsOccupancyStatus("FULL")
                    .level("MEDIUM")
                    .build();

            TransportData data = TransportData.builder()
                    .crowding(List.of(full))
                    .build();

            List<AlertMessage> alerts = alertService.evaluate(data);

            assertThat(alerts).anyMatch(a -> "CROWDING".equals(a.getType()));
        }

        @Test
        @DisplayName("Should NOT generate CROWDING alert when level is LOW")
        void shouldNotGenerateCrowdingAlertForLowLevel() {
            CrowdingInfo low = CrowdingInfo.builder()
                    .vehicleId("VEH-003")
                    .level("LOW")
                    .occupancyPercentage(30.0)
                    .build();

            TransportData data = TransportData.builder()
                    .crowding(List.of(low))
                    .build();

            List<AlertMessage> alerts = alertService.evaluate(data);

            assertThat(alerts).noneMatch(a -> "CROWDING".equals(a.getType()));
        }
    }

    @Nested
    @DisplayName("Weather rule")
    class WeatherTests {

        @Test
        @DisplayName("Should generate WEATHER alert when alert cause is WEATHER")
        void shouldGenerateWeatherAlert() {
            ServiceAlert weather = ServiceAlert.builder()
                    .alertId("ALT-003")
                    .type("GENERAL_INFO")
                    .cause("WEATHER")
                    .build();

            TransportData data = TransportData.builder()
                    .alerts(List.of(weather))
                    .build();

            List<AlertMessage> alerts = alertService.evaluate(data);

            assertThat(alerts).anyMatch(a ->
                    "WEATHER".equals(a.getType()) &&
                    "Weather impact on schedule".equals(a.getMessage())
            );
        }
    }

    @Test
    @DisplayName("Should return empty list when all data is empty")
    void shouldReturnEmptyAlertsForEmptyData() {
        TransportData data = TransportData.builder()
                .vehicles(List.of())
                .alerts(List.of())
                .crowding(List.of())
                .build();

        List<AlertMessage> alerts = alertService.evaluate(data);

        assertThat(alerts).isEmpty();
    }

    @Test
    @DisplayName("Should return empty list when data is null")
    void shouldReturnEmptyAlertsForNullData() {
        List<AlertMessage> alerts = alertService.evaluate(null);
        assertThat(alerts).isEmpty();
    }

    @Test
    @DisplayName("Should generate multiple alerts when multiple conditions are met")
    void shouldGenerateMultipleAlertsWhenMultipleConditionsMet() {
        TransportData data = TransportData.builder()
                .vehicles(List.of(
                        VehicleLocation.builder().vehicleId("V1").delaySeconds(1200).build() // 20 min
                ))
                .alerts(List.of(
                        ServiceAlert.builder().alertId("A1").type("DISRUPTION").build(),
                        ServiceAlert.builder().alertId("A2").cause("WEATHER").build()
                ))
                .crowding(List.of(
                        CrowdingInfo.builder().vehicleId("V1").level("FULL").build()
                ))
                .build();

        List<AlertMessage> alerts = alertService.evaluate(data);

        assertThat(alerts).hasSizeGreaterThanOrEqualTo(3);
        assertThat(alerts).extracting(AlertMessage::getType)
                .containsAnyOf("DELAY", "DISRUPTION", "CROWDING", "WEATHER");
    }
}