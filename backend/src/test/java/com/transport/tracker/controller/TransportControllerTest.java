package com.transport.tracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.transport.tracker.config.SecurityConfig;
import com.transport.tracker.model.*;
import com.transport.tracker.security.JwtAuthenticationFilter;
import com.transport.tracker.security.JwtService;
import com.transport.tracker.service.TransportService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
* Integration tests for TransportController.
* Verifies HTTP contract: status codes, response structure, parameter handling.
*
* SecurityConfig is imported to test the real security filter chain.
* JwtAuthenticationFilter and JwtService are mocked because @WebMvcTest
* does not scan @Service/@Component beans outside the web layer.
* @WithMockUser provides an authenticated security context with ROLE_VIEWER
* to satisfy the @PreAuthorize("hasAnyRole('VIEWER','OPERATOR','ADMIN')") on the controller.
*/
@WebMvcTest(TransportController.class)
@Import(SecurityConfig.class)
@DisplayName("TransportController")
@WithMockUser(username = "viewer", roles = {"VIEWER"})
class TransportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransportService transportService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtService jwtService;

    /**
     * Configure the mocked JwtAuthenticationFilter to pass requests through the filter chain.
     * <p>
     * Without this setup, the @MockBean mock's doFilter() is a no-op that never calls
     * filterChain.doFilter(), causing all requests to be swallowed by the mock filter
     * before reaching the DispatcherServlet (symptoms: Handler: Type = null, empty body).
     * </p>
     * <p>
     * We stub the public doFilter(ServletRequest, ServletResponse, FilterChain) method
     * (inherited from GenericFilterBean/Filter interface) rather than the protected
     * doFilterInternal() method, because Mockito mocks the entire class hierarchy and
     * the public doFilter() is the entry point called by the servlet container.
     * </p>
     */
    @BeforeEach
    void configureMockJwtFilter() throws Exception {
        doAnswer(invocation -> {
            ServletRequest request = invocation.getArgument(0);
            ServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(jwtAuthenticationFilter)
          .doFilter(any(ServletRequest.class), any(ServletResponse.class), any(FilterChain.class));
    }

    private TransportResponse<TransportData> buildMockResponse(String source) {
        TransportData data = TransportData.builder()
                .city("nyc")
                .routeId("A")
                .vehicles(List.of(
                        VehicleLocation.builder()
                                .vehicleId("VEH-001")
                                .routeId("A")
                                .latitude(40.7580)
                                .longitude(-73.9855)
                                .delaySeconds(0)
                                .build()
                ))
                .alerts(List.of())
                .crowding(List.of())
                .conditionalAlerts(List.of())
                .build();

        return TransportResponse.<TransportData>builder()
                .data(data)
                .metadata(TransportResponse.ResponseMetadata.builder()
                        .cached(!source.equals("LIVE"))
                        .dataSource(source)
                        .timestamp(Instant.now())
                        .city("nyc")
                        .routeId("A")
                        .offlineMode(false)
                        .build())
                .links(Map.of(
                        "self", TransportResponse.HalLink.builder().href("/api/v1/transport?city=nyc&routeId=A").build()
                ))
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/transport should return 200 with transport data")
    void shouldReturn200WithTransportData() throws Exception {
        when(transportService.getTransportData(eq("nyc"), eq("A"), isNull()))
                .thenReturn(buildMockResponse("LIVE"));

        mockMvc.perform(get("/api/v1/transport")
                        .param("city", "nyc")
                        .param("routeId", "A")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.city").value("nyc"))
                .andExpect(jsonPath("$.data.routeId").value("A"))
                .andExpect(jsonPath("$.metadata.dataSource").value("LIVE"))
                .andExpect(jsonPath("$._links.self").exists());
    }

    @Test
    @DisplayName("GET /api/v1/transport with offline=true should return MOCK data")
    void shouldReturnMockDataWhenOfflineQueryParam() throws Exception {
        when(transportService.getTransportData(eq("nyc"), eq("A"), eq(true)))
                .thenReturn(buildMockResponse("MOCK"));

        mockMvc.perform(get("/api/v1/transport")
                        .param("city", "nyc")
                        .param("routeId", "A")
                        .param("offline", "true")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.dataSource").value("MOCK"));
    }

    @Test
    @DisplayName("GET /api/v1/transport/arrivals without stopId should return 400")
    void shouldReturn400WhenStopIdMissing() throws Exception {
        mockMvc.perform(get("/api/v1/transport/arrivals")
                        .param("routeId", "A")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/transport/plan without from/to should return 400")
    void shouldReturn400WhenFromOrToMissing() throws Exception {
        mockMvc.perform(get("/api/v1/transport/plan")
                        .param("city", "nyc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/transport/alerts should return 200")
    void shouldReturn200ForAlerts() throws Exception {
        var alertsResponse = TransportResponse.<List<ServiceAlert>>builder()
                .data(List.of())
                .metadata(TransportResponse.ResponseMetadata.builder()
                        .dataSource("LIVE")
                        .timestamp(Instant.now())
                        .build())
                .build();

        when(transportService.getServiceAlerts(eq("nyc"), isNull()))
                .thenReturn(alertsResponse);

        mockMvc.perform(get("/api/v1/transport/alerts")
                        .param("city", "nyc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.dataSource").value("LIVE"));
    }
}