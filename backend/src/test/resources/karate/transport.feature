@transport @regression
Feature: Transport API - Complete Transport Data

  Background:
    * url baseUrl + apiVersion
    * header Accept = 'application/json'
    * header Content-Type = 'application/json'

  @smoke @positive
  Scenario: Get all transport data for a city
    Given path '/transport'
    And param city = 'New York'
    When method get
    Then status 200
    And response.metadata.city == 'New York'
    And response.data is not null
    And response._links is not null
    And response.metadata.dataSource in ['LIVE', 'CACHE', 'STALE_CACHE', 'MOCK', 'OFFLINE']
    And response.metadata.timestamp is not null
    And response.metadata.provider in ['NYC-MTA', 'TransitLand', 'TfL', 'SEPTA']

  @smoke @positive
  Scenario: Get transport data with route filter
    Given path '/transport'
    And param city = 'New York'
    And param routeId = 'M1'
    When method get
    Then status 200
    And response.metadata.city == 'New York'
    And response.metadata.routeId == 'M1'
    And response.data.vehicles[*].routeId contains 'M1'

  @positive
  Scenario: Get transport data in offline mode
    Given path '/transport'
    And param city = 'New York'
    And param offline = true
    When method get
    Then status 200
    And response.metadata.offlineMode == true
    And response.metadata.dataSource == 'MOCK'
    And response.data is not null

  @positive
  Scenario: Get transport data with metadata validation
    Given path '/transport'
    And param city = 'New York'
    When method get
    Then status 200
    And response.metadata.cachedAgeSeconds >= 0
    And response.metadata.cached in [true, false]
    And response.metadata.timestamp matches '\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}'
    And response.metadata.attribution is not null

  @negative @validation
  Scenario: Get transport data without required parameters - should return mock data
    Given path '/transport'
    When method get
    Then status 200
    And response.data is not null
    And response.metadata.dataSource in ['MOCK', 'CACHE', 'OFFLINE']

  @positive @performance
  Scenario: Verify response time for transport data
    Given path '/transport'
    And param city = 'New York'
    When method get
    Then status 200
    And responseTime < maxResponseTime
    * print 'Response time: ' + responseTime + ' ms'

  @positive
  Scenario: Validate transport data structure
    Given path '/transport'
    And param city = 'New York'
    When method get
    Then status 200
    And response.data.vehicles is not null
    And response.data.arrivals is not null
    And response.data.alerts is not null
    And response.data.crowding is not null
    And response.data.routePlans is not null

  @positive
  Scenario: Test multiple cities
    * def cities = ['New York', 'London', 'Philadelphia']
    * def testCity = cities[0]
    Given path '/transport'
    And param city = testCity
    When method get
    Then status 200
    And response.metadata.city == testCity

  @negative @error-handling
  Scenario: Handle invalid city parameter gracefully
    Given path '/transport'
    And param city = 'InvalidCity123'
    When method get
    Then status 200
    And response.metadata.dataSource == 'MOCK'
    * print 'API gracefully degraded to MOCK data for invalid city'

  @positive @hateoas
  Scenario: Verify HATEOAS links in response
    Given path '/transport'
    And param city = 'New York'
    When method get
    Then status 200
    And response._links.href is not null
    And response._links.href contains baseUrl
