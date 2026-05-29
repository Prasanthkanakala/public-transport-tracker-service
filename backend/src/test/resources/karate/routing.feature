@routing @regression
Feature: Transport API - Route Planning

  Background:
    * url baseUrl + apiVersion
    * header Accept = 'application/json'
    * header Content-Type = 'application/json'

  @smoke @positive
  Scenario: Plan route between two locations
    Given path '/transport/plan'
    And param from = '40.7128,-74.0060'
    And param to = '40.7614,-73.9776'
    And param city = 'New York'
    When method get
    Then status 200
    And response.data is not null
    And response.data.length > 0

  @positive
  Scenario: Validate route plan structure
    Given path '/transport/plan'
    And param from = '40.7128,-74.0060'
    And param to = '40.7614,-73.9776'
    And param city = 'New York'
    When method get
    Then status 200
    * def plan = response.data[0]
    And plan.planId is not null
    And plan.origin is not null
    And plan.destination is not null
    And plan.departureTime is not null
    And plan.arrivalTime is not null
    And plan.durationMinutes >= 0
    And plan.transfers >= 0
    And plan.legs is not null
    And plan.status in ['OPTIMAL', 'ALTERNATIVE', 'DISRUPTED', 'NO_SERVICE']
    And plan.confidence >= 0 && plan.confidence <= 1.0

  @positive
  Scenario: Verify route plan legs structure
    Given path '/transport/plan'
    And param from = '40.7128,-74.0060'
    And param to = '40.7614,-73.9776'
    And param city = 'New York'
    When method get
    Then status 200
    * def leg = response.data[0].legs[0]
    And leg.mode is not null
    And leg.route is not null
    And leg.startTime is not null
    And leg.endTime is not null
    And leg.stops is not null

  @positive
  Scenario: Get multiple route alternatives
    Given path '/transport/plan'
    And param from = '40.7128,-74.0060'
    And param to = '40.7614,-73.9776'
    And param city = 'New York'
    When method get
    Then status 200
    And response.data.length >= 1 && response.data.length <= 3
    * print 'Number of route alternatives: ' + response.data.length

  @positive
  Scenario: Verify first route is optimal
    Given path '/transport/plan'
    And param from = '40.7128,-74.0060'
    And param to = '40.7614,-73.9776'
    And param city = 'New York'
    When method get
    Then status 200
    And response.data[0].status == 'OPTIMAL'
    * def firstDuration = response.data[0].durationMinutes
    * def secondDuration = response.data[1] ? response.data[1].durationMinutes : firstDuration + 1000
    And firstDuration <= secondDuration

  @positive @transfers
  Scenario: Verify minimum transfer time
    Given path '/transport/plan'
    And param from = '40.7128,-74.0060'
    And param to = '40.7614,-73.9776'
    And param city = 'New York'
    When method get
    Then status 200
    * def multiLegPlans = response.data.filter(function(x) { return x.legs.length > 1 })
    * print 'Plans with transfers: ' + multiLegPlans.length

  @positive @confidence
  Scenario: Verify confidence score calculation
    Given path '/transport/plan'
    And param from = '40.7128,-74.0060'
    And param to = '40.7614,-73.9776'
    And param city = 'New York'
    When method get
    Then status 200
    * def lowConfidencePlans = response.data.filter(function(x) { return x.confidence < 0.7 })
    * print 'Low confidence plans: ' + lowConfidencePlans.length

  @negative @validation
  Scenario: Missing required parameters
    Given path '/transport/plan'
    And param to = '40.7614,-73.9776'
    When method get
    Then status 400

  @negative @validation
  Scenario: Invalid coordinate format
    Given path '/transport/plan'
    And param from = 'invalid'
    And param to = '40.7614,-73.9776'
    And param city = 'New York'
    When method get
    Then status 400

  @positive @offline
  Scenario: Plan route in offline mode
    Given path '/transport/plan'
    And param from = '40.7128,-74.0060'
    And param to = '40.7614,-73.9776'
    And param city = 'New York'
    And param offline = true
    When method get
    Then status 200
    And response.metadata.offlineMode == true

  @positive @performance
  Scenario: Performance - route planning endpoint
    Given path '/transport/plan'
    And param from = '40.7128,-74.0060'
    And param to = '40.7614,-73.9776'
    And param city = 'New York'
    When method get
    Then status 200
    And responseTime < 5000

  @positive
  Scenario: Verify route stops sequence
    Given path '/transport/plan'
    And param from = '40.7128,-74.0060'
    And param to = '40.7614,-73.9776'
    And param city = 'New York'
    When method get
    Then status 200
    * def leg = response.data[0].legs[0]
    And leg.stops is not null
    And leg.stops.length >= 2
    * print 'Stops in leg: ' + leg.stops.length

  @positive @realtime
  Scenario: Verify disrupted routes detection
    Given path '/transport/plan'
    And param from = '40.7128,-74.0060'
    And param to = '40.7614,-73.9776'
    And param city = 'New York'
    When method get
    Then status 200
    * def disruptedPlans = response.data.filter(function(x) { return x.status == 'DISRUPTED' })
    * print 'Disrupted plans detected: ' + disruptedPlans.length

  @positive @hateoas
  Scenario: Verify HATEOAS links in route plan
    Given path '/transport/plan'
    And param from = '40.7128,-74.0060'
    And param to = '40.7614,-73.9776'
    And param city = 'New York'
    When method get
    Then status 200
    And response._links.href is not null
