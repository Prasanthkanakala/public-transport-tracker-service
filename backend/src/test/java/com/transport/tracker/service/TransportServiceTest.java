package com.transport.tracker.service;

import com.transport.tracker.cache.CacheService;
import com.transport.tracker.client.TransitApiClient;
import com.transport.tracker.exception.TransitApiException;
import com.transport.tracker.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
* Unit tests for TransportService degradation strategy.
*
* Verifies the 5-stage fallback chain:
* 1. Offline mode → MOCK
* 2. Cache HIT → CACHE
* 3. Live API success → LIVE
* 4. Live API failure + stale cache → STALE_CACHE
* 5. Live API failure + no stale → MOCK
*/
@ExtendWith(MockitoExtension.class)
@DisplayName("TransportService")
class TransportServiceTest {

    @Mock private CacheService cacheService;
    @Mock private AlertService alertService;
    @Mock private MockDataService mockDataService;
    @Mock private RoutePlannerService routePlannerService;
    @Mock private TransitApiClient mtaClient;
    @Mock private TransitApiClient transitLandClient;
    @Mock private TransitApiClient septaClient;
    @Mock private TransitApiClient tflClient;

    private TransportService transportService;

    @BeforeEach
    void setUp() {
        transportService = new TransportService(
                cacheService, alertService, mockDataService,
                routePlannerService, mtaClient, transitLandClient, septaClient, tflClient
        );
        ReflectionTestUtils.setField(transportService, "globalOfflineMode", false);

        when(cacheService.buildKey(anyString(), any())).thenReturn("nyc:A");
        lenient().when(cacheService.withKeyLock(anyString(), any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(1);
            return supplier.get();
        });
    }

    @Nested
    @DisplayName("Stage 1: Offline mode")
    class OfflineModeTests {

        @Test
        @DisplayName("Should return MOCK data when offline=true is passed")
        void shouldReturnMockWhenOffline() {
            TransportData mockData = TransportData.builder().city("nyc").routeId("A").build();
            when(mockDataService.getMockData("nyc", "A")).thenReturn(mockData);
            when(alertService.evaluate(any())).thenReturn(List.of());

            var response = transportService.getTransportData("nyc", "A", true);

            assertThat(response.getMetadata().getDataSource()).isEqualTo("MOCK");
            assertThat(response.getMetadata().isOfflineMode()).isTrue();
            verify(cacheService, never()).get(anyString());
            verify(mtaClient, never()).fetchVehicleLocations(any(), any());
        }

        @Test
        @DisplayName("Should return MOCK data when global offline mode is enabled")
        void shouldReturnMockWhenGlobalOfflineEnabled() {
            ReflectionTestUtils.setField(transportService, "globalOfflineMode", true);
            TransportData mockData = TransportData.builder().city("nyc").build();
            when(mockDataService.getMockData("nyc", "A")).thenReturn(mockData);
            when(alertService.evaluate(any())).thenReturn(List.of());

            var response = transportService.getTransportData("nyc", "A", null);

            assertThat(response.getMetadata().getDataSource()).isEqualTo("MOCK");
        }
    }

    @Nested
    @DisplayName("Stage 2: Cache hit")
    class CacheHitTests {

        @Test
        @DisplayName("Should return CACHE data on cache hit")
        void shouldReturnCacheDataOnCacheHit() {
            TransportData cachedData = TransportData.builder().city("nyc").routeId("A").build();
            when(cacheService.get("nyc:A")).thenReturn(Optional.of(cachedData));
            when(cacheService.getCacheAge("nyc:A")).thenReturn(Optional.of(120L));

            var response = transportService.getTransportData("nyc", "A", false);

            assertThat(response.getMetadata().getDataSource()).isEqualTo("CACHE");
            assertThat(response.getMetadata().isCached()).isTrue();
            assertThat(response.getMetadata().getCacheAgeSeconds()).isEqualTo(120L);
            verify(mtaClient, never()).fetchVehicleLocations(any(), any());
        }
    }

    @Nested
    @DisplayName("Stage 3: Live API success")
    class LiveApiTests {

        @Test
        @DisplayName("Should return LIVE data and cache it on successful API call")
        void shouldReturnAndCacheLiveData() {
            when(cacheService.get("nyc:A")).thenReturn(Optional.empty());
            when(mtaClient.isAvailable()).thenReturn(true);
            when(mtaClient.fetchVehicleLocations("nyc", "A"))
                    .thenReturn(List.of(VehicleLocation.builder().vehicleId("VEH-001").build()));
            when(mtaClient.fetchServiceAlerts("nyc")).thenReturn(List.of());
            when(mtaClient.fetchCrowdingInfo("A")).thenReturn(List.of());
            when(alertService.evaluate(any())).thenReturn(List.of());

            var response = transportService.getTransportData("nyc", "A", false);

            assertThat(response.getMetadata().getDataSource()).isEqualTo("LIVE");
            assertThat(response.getMetadata().isCached()).isFalse();
            verify(cacheService).put(eq("nyc:A"), any(TransportData.class));
        }
    }

    @Nested
    @DisplayName("Stage 4: Stale cache fallback")
    class StaleCacheTests {

        @Test
        @DisplayName("Should return STALE_CACHE data when live API fails")
        void shouldReturnStaleCacheWhenApiFails() {
            when(septaClient.isAvailable()).thenReturn(false);
            when(transitLandClient.isAvailable()).thenReturn(false);
            when(cacheService.get("nyc:A")).thenReturn(Optional.empty());
            when(mtaClient.isAvailable()).thenReturn(false);

            TransportData staleData = TransportData.builder().city("nyc").build();
            when(cacheService.getStale("nyc:A")).thenReturn(Optional.of(staleData));
            when(cacheService.getCacheAge("nyc:A")).thenReturn(Optional.of(450L));

            var response = transportService.getTransportData("nyc", "A", false);

            assertThat(response.getMetadata().getDataSource()).isEqualTo("STALE_CACHE");
        }
    }

    @Nested
    @DisplayName("Stage 5: Mock fallback")
    class MockFallbackTests {

        @Test
        @DisplayName("Should return MOCK data when API fails and no stale cache")
        void shouldReturnMockWhenApiFailsAndNoStaleCache() {
            when(septaClient.isAvailable()).thenReturn(false);
            when(transitLandClient.isAvailable()).thenReturn(false);
            when(cacheService.get("nyc:A")).thenReturn(Optional.empty());
            when(mtaClient.isAvailable()).thenReturn(false);
            when(cacheService.getStale("nyc:A")).thenReturn(Optional.empty());

            TransportData mockData = TransportData.builder().city("nyc").build();
            when(mockDataService.getMockData("nyc", "A")).thenReturn(mockData);
            when(alertService.evaluate(any())).thenReturn(List.of());

            var response = transportService.getTransportData("nyc", "A", false);

            assertThat(response.getMetadata().getDataSource()).isEqualTo("MOCK");
            verify(mockDataService).getMockData("nyc", "A");
        }
    }

    @Test
    @DisplayName("Response should always contain HATEOAS _links")
    void shouldAlwaysContainHateoasLinks() {
        TransportData mockData = TransportData.builder().city("nyc").build();
        when(mockDataService.getMockData("nyc", "A")).thenReturn(mockData);
        when(alertService.evaluate(any())).thenReturn(List.of());

        var response = transportService.getTransportData("nyc", "A", true);

        assertThat(response.getLinks()).isNotEmpty();
        assertThat(response.getLinks()).containsKey("self");
        assertThat(response.getLinks()).containsKey("vehicles");
        assertThat(response.getLinks()).containsKey("alerts");
    }
}
