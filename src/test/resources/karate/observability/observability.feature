@live
Feature: Metrics produced by the API (needs a running app with the dev profile: Actuator endpoints exposed)

  Background:
    * url baseUrl

  Scenario: the service timer counts calls per method
    Given path '/api/mbta/routes/Red'
    When method get
    Then status 200
    Given path '/management/metrics/mbta.service'
    And param tag = 'method:route'
    When method get
    Then status 200
    * def count = karate.filter(response.measurements, function(m){ return m.statistic == 'COUNT' })[0].value
    And assert count >= 1

  Scenario: a repeated lookup is a cache hit
    Given path '/api/mbta/routes/Orange'
    When method get
    Then status 200
    Given path '/api/mbta/routes/Orange'
    When method get
    Then status 200
    Given path '/management/metrics/cache.gets'
    And param tag = ['cache:mbtaRoutes', 'result:hit']
    When method get
    Then status 200
    And assert response.measurements[0].value >= 1

  Scenario: outbound calls are recorded with URI templates, never ids
    Given path '/management/metrics/http.client.requests'
    When method get
    Then status 200
    * def uris = karate.filter(response.availableTags, function(t){ return t.tag == 'uri' })[0].values
    And match uris contains '/routes/{id}'
    And match each uris == '#regex ^(?!.*/routes/(Red|Orange)).*$'

  Scenario: Prometheus exposes the metrics the CloudWatch Lambda scrapes
    * configure headers = null
    Given path '/management/prometheus'
    When method get
    Then status 200
    And match response contains 'mbta_service_seconds_bucket'
    And match response contains 'mbta_ratelimit_remaining'
    And match response contains 'transit_feed_age_seconds'
