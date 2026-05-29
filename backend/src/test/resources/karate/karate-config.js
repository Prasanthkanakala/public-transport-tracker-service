function() {
  var config = {
    // Base URL for API endpoints
    baseUrl: 'http://localhost:8080',
    
    // API version
    apiVersion: '/api/v1',
    
    // Default timeouts
    connectTimeout: 5000,
    readTimeout: 10000,
    
    // Test data
    testCities: ['New York', 'London', 'Philadelphia'],
    testRoutes: ['M1', '1', 'Route-25'],
    testStops: ['40001', '40002', '40003'],
    
    // Sample coordinates for route planning
    testOrigin: { lat: 40.7128, lon: -74.0060 },  // Times Square, NYC
    testDestination: { lat: 40.7614, lon: -73.9776 },  // Central Park, NYC
    
    // Expected response times (ms)
    maxResponseTime: 5000,
    
    // Retry configuration
    maxRetries: 3,
    retryDelay: 1000,
    
    // Print configuration details
    karate: {
      configure: function() {
        karate.log('Karate Test Configuration Loaded');
        karate.log('Base URL: ' + config.baseUrl);
      }
    }
  };
  
  // Set system properties for test environment
  java.lang.System.setProperty('app.test.mode', 'true');
  
  return config;
}
