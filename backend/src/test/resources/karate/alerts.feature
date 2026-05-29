@alerts @regression
Feature: Transport API - Service Alerts

  Background:
    * url baseUrl + apiVersion
    * header Accept = 'application/json'
    * header Content-Type = 'application/json'

  @smoke @positive
  Scenario: Get service alerts for a city
    Given path '/transport/alerts'
    And param city = 'New York'
    When method get
    Then status 200
    And response.data is not null
    And response.metadata.dataSource in ['LIVE', 'CACHE', 'STALE_CACHE', 'MOCK']

  @positive
  Scenario: Validate alert structure
    Given path '/transport/alerts'
    And param city = 'New York'
    When method get
    Then status 200
    * def alert = response.data[0]
    And alert.type in ['DELAY', 'DISRUPTION', 'WEATHER', 'CROWDING', 'PLANNED_WORK', 'GENERAL_INFO']
    And alert.severity in ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
    And alert.cause in ['TECHNICAL_PROBLEM', 'ACCIDENT', 'WEATHER', 'MAINTENANCE', 'CONSTRUCTION']
    And alert.effect in ['DETOUR', 'STOP_MOVED', 'SERVICE_CHANGE', 'SUSPENSION', 'SIGNIFICANT_DELAYS', 'REDUCED_SERVICE']

  @positive
  Scenario: Verify affected routes in alerts
    Given path '/transport/alerts'
    And param city = 'New York'
    When method get
    Then status 200
    * def alertsWithRoutes = response.data.filter(function(x) { return x.affectedRoutes && x.affectedRoutes.length > 0 })
    * print 'Alerts affecting routes: ' + alertsWithRoutes.length

  @positive
  Scenario: Verify alert time windows
    Given path '/transport/alerts'
    And param city = 'New York'
    When method get
    Then status 200
    * def alert = response.data[0]
    And alert.activeFrom is not null
    And alert.activeUntil is not null
    And alert.activeFrom matches '\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}'
    And alert.activeUntil matches '\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}'

  @positive
  Scenario: Filter critical alerts
    Given path '/transport/alerts'
    And param city = 'New York'
    When method get
    Then status 200
    * def criticalAlerts = response.data.filter(function(x) { return x.severity == 'CRITICAL' })
    * print 'Critical alerts count: ' + criticalAlerts.length
    And criticalAlerts[*].severity contains 'CRITICAL'

  @positive
  Scenario: Verify disruption alerts have detailed information
    Given path '/transport/alerts'
    And param city = 'New York'
    When method get
    Then status 200
    * def disruptions = response.data.filter(function(x) { return x.type == 'DISRUPTION' })
    And disruptions[0].description is not null
    And disruptions[0].affectedRoutes is not null
    And disruptions[0].affectedRoutes.length >= 0

  @positive @offline
  Scenario: Get alerts in offline mode
    Given path '/transport/alerts'
    And param city = 'New York'
    And param offline = true
    When method get
    Then status 200
    And response.metadata.offlineMode == true
    And response.metadata.dataSource == 'MOCK'

  @positive @performance
  Scenario: Performance - alerts endpoint
    Given path '/transport/alerts'
    And param city = 'New York'
    When method get
    Then status 200
    And responseTime < 3000

  @positive
  Scenario: Verify alert descriptions
    Given path '/transport/alerts'
    And param city = 'New York'
    When method get
    Then status 200
    * def highSeverityAlerts = response.data.filter(function(x) { return x.severity == 'HIGH' || x.severity == 'CRITICAL' })
    And highSeverityAlerts[*].description length > 0

  @negative @validation
  Scenario: Alerts without city parameter
    Given path '/transport/alerts'
    When method get
    Then status 200
    And response.metadata.dataSource in ['MOCK', 'CACHE']

  @positive @weather-alerts
  Scenario: Verify weather-related alerts
    Given path '/transport/alerts'
    And param city = 'New York'
    When method get
    Then status 200
    * def weatherAlerts = response.data.filter(function(x) { return x.type == 'WEATHER' })
    And weatherAlerts[*].cause contains 'WEATHER'

  @positive @caching
  Scenario: Verify alerts caching
    # First request
    Given path '/transport/alerts'
    And param city = 'New York'
    When method get
    Then status 200
    * def firstResponse = response
    * def firstCount = response.data.length

    # Second request should return same cached data
    Given path '/transport/alerts'
    And param city = 'New York'
    When method get
    Then status 200
    And response.data.length == firstCount
    And response.metadata.cached == firstResponse.metadata.cached
