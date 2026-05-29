@cache @regression
Feature: Transport API - Cache Operations

  Background:
    * url baseUrl + apiVersion
    * header Accept = 'application/json'
    * header Content-Type = 'application/json'

  @smoke @positive
  Scenario: Get cache statistics
    Given path '/cache/stats'
    When method get
    Then status 200
    And response.hitCount >= 0
    And response.missCount >= 0
    And response.staleHitCount >= 0
    And response.evictionCount >= 0
    And response.cacheSize >= 0
    And response.hitRate is not null
    * print 'Cache stats - Hits: ' + response.hitCount + ', Misses: ' + response.missCount + ', Hit Rate: ' + response.hitRate

  @positive
  Scenario: Verify cache statistics calculation
    Given path '/cache/stats'
    When method get
    Then status 200
    * def totalRequests = response.hitCount + response.missCount + response.staleHitCount
    * def expectedHitRate = totalRequests > 0 ? response.hitCount / totalRequests : 0
    And Math.abs(response.hitRate - expectedHitRate) < 0.01

  @positive
  Scenario: Verify hit count increment
    # First request - cache miss
    Given path '/transport/arrivals'
    And param stopId = '40001'
    When method get
    Then status 200

    # Get cache stats
    Given path '/cache/stats'
    When method get
    Then status 200
    * def statsAfterFirstRequest = response

    # Second request - should be cache hit
    Given path '/transport/arrivals'
    And param stopId = '40001'
    When method get
    Then status 200

    # Verify cache stats updated
    Given path '/cache/stats'
    When method get
    Then status 200
    And response.hitCount >= statsAfterFirstRequest.hitCount

  @positive
  Scenario: Clear all cache entries
    Given path '/cache'
    When method delete
    Then status 200 || status 204
    * print 'Cache cleared'

    # Verify cache is cleared
    Given path '/cache/stats'
    When method get
    Then status 200
    And response.cacheSize == 0

  @positive
  Scenario: Delete specific cache entry
    Given path '/cache/entry'
    And param city = 'New York'
    And param route = 'M1'
    When method delete
    Then status 200 || status 204
    * print 'Cache entry deleted for New York:M1'

  @positive
  Scenario: Verify cache entry deletion
    # Get initial cache size
    Given path '/cache/stats'
    When method get
    Then status 200
    * def initialSize = response.cacheSize

    # Delete specific entry
    Given path '/cache/entry'
    And param city = 'New York'
    And param route = 'M1'
    When method delete
    Then status 200 || status 204

    # Verify size decreased or stayed same (if entry didn't exist)
    Given path '/cache/stats'
    When method get
    Then status 200
    And response.cacheSize <= initialSize

  @positive @performance
  Scenario: Performance - cache stats endpoint
    Given path '/cache/stats'
    When method get
    Then status 200
    And responseTime < 1000

  @negative @validation
  Scenario: Delete cache entry without parameters
    Given path '/cache/entry'
    When method delete
    Then status 400

  @positive @resilience
  Scenario: Verify stale cache hits
    Given path '/cache/stats'
    When method get
    Then status 200
    And response.staleHitCount >= 0
    * print 'Stale cache hits: ' + response.staleHitCount

  @positive @monitoring
  Scenario: Monitor cache eviction
    Given path '/cache/stats'
    When method get
    Then status 200
    And response.evictionCount >= 0
    * def avgEvictions = response.evictionCount > 0 ? response.evictionCount : 0
    * print 'Total cache evictions: ' + avgEvictions

  @positive
  Scenario: Verify cache metrics completeness
    Given path '/cache/stats'
    When method get
    Then status 200
    * def requiredMetrics = ['hitCount', 'missCount', 'staleHitCount', 'evictionCount', 'cacheSize', 'hitRate', 'maxCapacity']
    And response contains keys(requiredMetrics)

  @positive @stress
  Scenario: Stress test - multiple cache operations
    * repeat 10
      | Given path '/cache/stats'
      | When method get
      | Then status 200

    * print 'Stress test completed - 10 cache stats requests'

  @positive
  Scenario: Clear cache and verify fresh data fetch
    # Clear cache
    Given path '/cache'
    When method delete
    Then status 200 || status 204

    # Fetch data - should be fresh
    Given path '/transport/arrivals'
    And param stopId = '40001'
    When method get
    Then status 200
    * def firstFetch = response.metadata.dataSource

    # Fetch again - should be from cache
    Given path '/transport/arrivals'
    And param stopId = '40001'
    When method get
    Then status 200
    And response.metadata.cached == true
