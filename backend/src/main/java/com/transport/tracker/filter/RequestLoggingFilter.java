package com.transport.tracker.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that adds request correlation IDs and MDC context for structured logging.
 * 
 * Features:
 * - Generates unique request ID for each request
 * - Adds MDC context (requestId, method, uri, userId)
 * - Logs request/response details with latency
 * - Supports correlation across distributed systems
 */
@Slf4j
@Component
public class RequestLoggingFilter implements Filter {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        // Generate or extract request ID
        String requestId = httpRequest.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isEmpty()) {
            requestId = UUID.randomUUID().toString();
        }
        
        // Extract correlation ID (for distributed tracing)
        String correlationId = httpRequest.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = requestId;
        }
        
        // Add to MDC for structured logging
        MDC.put("requestId", requestId);
        MDC.put("correlationId", correlationId);
        MDC.put("method", httpRequest.getMethod());
        MDC.put("uri", httpRequest.getRequestURI());
        MDC.put("remoteAddr", httpRequest.getRemoteAddr());
        
        // Add request ID to response headers
        httpResponse.setHeader(REQUEST_ID_HEADER, requestId);
        httpResponse.setHeader(CORRELATION_ID_HEADER, correlationId);
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Wrap request/response for content caching
            ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(httpRequest);
            ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(httpResponse);
            
            // Log incoming request
            log.info("Incoming request: {} {} from {}", 
                    httpRequest.getMethod(), 
                    httpRequest.getRequestURI(),
                    httpRequest.getRemoteAddr());
            
            // Process request
            chain.doFilter(wrappedRequest, wrappedResponse);
            
            // Calculate latency
            long latency = System.currentTimeMillis() - startTime;
            MDC.put("latencyMs", String.valueOf(latency));
            MDC.put("statusCode", String.valueOf(wrappedResponse.getStatus()));
            
            // Log response
            log.info("Request completed: {} {} - status={} latency={}ms",
                    httpRequest.getMethod(),
                    httpRequest.getRequestURI(),
                    wrappedResponse.getStatus(),
                    latency);
            
            // Copy response body
            wrappedResponse.copyBodyToResponse();
            
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            log.error("Request failed: {} {} - error={} latency={}ms",
                    httpRequest.getMethod(),
                    httpRequest.getRequestURI(),
                    e.getMessage(),
                    latency,
                    e);
            throw e;
        } finally {
            // Clear MDC to prevent memory leaks
            MDC.clear();
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("RequestLoggingFilter initialized");
    }

    @Override
    public void destroy() {
        log.info("RequestLoggingFilter destroyed");
    }
}
