package com.transport.tracker.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.transport.tracker.client.MtaApiClient;
import com.transport.tracker.client.TransitApiClient;
import com.transport.tracker.client.TransitLandApiClient;
import com.transport.tracker.client.SeptaApiClient;
import com.transport.tracker.client.TflApiClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
* Core application bean configuration.
*/
@Configuration
public class AppConfig {

    /**
     * ObjectMapper configured for ISO-8601 date-time serialization.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * Explicit bean for MTA client injected with qualifier in TransportService.
     */
    @Bean
    @Qualifier("mtaClient")
    public TransitApiClient mtaApiClientBean(MtaApiClient mtaApiClient) {
        return mtaApiClient;
    }

    /**
     * Explicit bean for TransitLand client injected with qualifier in TransportService.
     */
    @Bean
    @Qualifier("transitLandClient")
    public TransitApiClient transitLandClientBean(TransitLandApiClient transitLandApiClient) {
        return transitLandApiClient;
    }

    /**
     * Explicit bean for SEPTA client injected with qualifier in TransportService.
     */
    @Bean
    @Qualifier("septaClient")
    public TransitApiClient septaClientBean(SeptaApiClient septaApiClient) {
        return septaApiClient;
    }

    /**
     * Explicit bean for TfL client injected with qualifier in TransportService.
     */
    @Bean
    @Qualifier("tflClient")
    public TransitApiClient tflClientBean(TflApiClient tflApiClient) {
        return tflApiClient;
    }
}