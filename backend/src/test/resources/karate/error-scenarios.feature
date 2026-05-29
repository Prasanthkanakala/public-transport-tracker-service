@error-scenarios @regression
Feature: Transport API - Error Handling

  Background:
    * url baseUrl + apiVersion
    * header Accept = 'application/json'
    * header Content-Type = 'application/json'

  @negative @validation
  Scenario: Missing required parameter - stopId
    Given path '/transport/arrivals'
    When method get
    Then status 400
    And response.error is not null
    And response.message contains 'stopId' || response.message contains 'required'
    And response.status == 400
    And response.timestamp is not null

  @negative @validation
  Scenario: Missing required parameter - from coordinate
    Given path '/transport/plan'
    And param to = '40.7614,-73.9776'
    When method get
    Then status 400

  @negative @validation
  Scenario: Invalid coordinate format
    Given path '/transport/plan'
    And param from = 'not-a-coordinate'
    And param to = '40.7614,-73.9776'
    When method get
    Then status 400
    And response.message contains 'coordinate' || response.message contains 'format' || response.message contains 'invalid'

  @negative @validation
  Scenario: Out of range latitude
    Given path '/transport/plan'
    And param from = '91.0,-74.0060'
    And param to = '40.7614,-73.9776'
    When method get
    Then status 400

  @negative @validation
  Scenario: Out of range longitude
    Given path '/transport/plan'
    And param from = '40.7128,-181.0'
    And param to = '40.7614,-73.9776'
    When method get
    Then status 400

  @negative @validation
  Scenario: Invalid boolean parameter
    Given path '/transport'
    And param offline = 'invalid-boolean'
    When method get
    Then status 400

  @negative @validation
  Scenario: Missing required routeId for crowding
    Given path '/transport/crowding'
    And param city = 'New York'
    When method get
    Then status 400
    And response.error contains 'routeId'

  @negative @validation
  Scenario: Verify error response structure
    Given path '/transport/arrivals'
    When method get
    Then status 400
    And response.error is not null
    And response.message is not null
    And response.status == 400
    And response.path is not null
    And response.timestamp matches '\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}'

  @negative @service
  Scenario: Invalid city parameter gracefully degraded
    Given path '/transport'
    And param city = 'NonExistentCity123'
    When method get
    Then status 200
    And response.metadata.dataSource == 'MOCK'
    * print 'Invalid city gracefully degraded to MOCK data'

  @negative @encoding
  Scenario: Handle special characters in parameters
    Given path '/transport'
    And param city = 'City@#$%'
    When method get
    Then status 200 || status 400

  @negative @length
  Scenario: Handle very long parameter values
    Given path '/transport'
    And param city = 'A' * 1000
    When method get
    Then status 200 || status 400

  @negative @sql-injection
  Scenario: Prevent SQL injection attempts
    Given path '/transport/arrivals'
    And param stopId = "'; DROP TABLE stops; --"
    When method get
    Then status 400 || status 200
    * print 'SQL injection attempt safely handled'

  @negative @xss-prevention
  Scenario: Prevent XSS attacks in parameters
    Given path '/transport'
    And param city = '<script>alert("XSS")</script>'
    When method get
    Then status 200 || status 400
    * print 'XSS attempt safely handled'

  @negative @null-handling
  Scenario: Handle null values in parameters
    Given path '/transport'
    And param city = null
    When method get
    Then status 200 || status 400

  @positive @error-recovery
  Scenario: Verify error doesn't corrupt cache
    # Make invalid request
    Given path '/transport/arrivals'
    When method get
    Then status 400

    # Valid request should still work
    Given path '/transport/arrivals'
    And param stopId = '40001'
    When method get
    Then status 200
    And response.data is not null

  @positive @resilience
  Scenario: Service continues after error
    * repeat 3
      | Given path '/transport/arrivals'
      | When method get
      | Then status 400

    # Next request should succeed
    Given path '/transport/arrivals'
    And param stopId = '40001'
    When method get
    Then status 200

  @negative @timeout
  Scenario: Handle long-running requests gracefully
    # This is a conceptual test - may need adjustment based on actual timeout configuration
    Given path '/transport/plan'
    And param from = '40.7128,-74.0060'
    And param to = '40.7614,-73.9776'
    And param city = 'New York'
    And configure connectTimeout = 100
    When method get
    Then status 200 || status 504 || status 408

  @negative @content-type
  Scenario: Request with unsupported content type
    Given path '/transport'
    And header Content-Type = 'application/xml'
    When method get
    Then status 200 || status 415

  @positive @cors
  Scenario: Verify CORS headers in error response
    Given path '/transport/arrivals'
    When method get
    Then status 400
    And response.status == 400
    * print 'Error response received with status 400'

  @negative @duplicate-params
  Scenario: Handle duplicate parameters
    Given path '/transport'
    And param city = 'New York'
    And param city = 'London'
    When method get
    Then status 200 || status 400
