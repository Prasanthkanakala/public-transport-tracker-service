package com.transport.tracker.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
* HATEOAS-compliant API response envelope following HAL (Hypertext Application Language) format.
*
* Contains:
* - data: The actual response payload
* - metadata: Cache/source information for transparency
* - _links: Hypermedia links (HATEOAS, 12-Factor principle)
*
* @param <T> Type of the payload
*/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransportResponse<T> {

    private T data;
    private ResponseMetadata metadata;

    @JsonProperty("_links")
    private Map<String, HalLink> links;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseMetadata {

        /** Whether the response was served from cache */
        private boolean cached;

        /** Age of cached data in seconds */
        private Long cacheAgeSeconds;

        /**
         * Data provenance: LIVE, CACHE, STALE_CACHE, MOCK, OFFLINE
         */
        private String dataSource;

        /** ISO-8601 timestamp of this response */
        private Instant timestamp;

        /** City/area queried */
        private String city;

        /** Route queried */
        private String routeId;

        /** Whether the service is operating in offline/degraded mode */
        private boolean offlineMode;

        /** Name of upstream API provider used */
        private String provider;

        /** Attribution text for data source */
        private String attribution;

        /** Warning if data is not real-time due to lag */
        private String lagWarning;
    }

    /**
     * Hypermedia link for HATEOAS navigation.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HalLink {
        private String href;
        private String method;
        private String type;
    }
}