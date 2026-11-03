@mbta
Feature: MBTA V3 REST API, reshaped into this API's JSON

  # Runs against the Karate mock (KarateApiIT) and the real MBTA V3 API (KarateLiveIT):
  # assertions describe the contract, not live values.

  Background:
    * url baseUrl
    * def directionSchema = { id: '#number', name: '#string', destination: '#string' }
    * def routeSchema = { id: '#string', name: '#string', color: '#string', textColor: '#string', kind: '#string', directions: '#[] directionSchema' }

  Scenario: subway routes by default
    Given path '/api/mbta/routes'
    When method get
    Then status 200
    And match each response == routeSchema
    And match response[*].id contains 'Red'

  Scenario: one route with its directions
    Given path '/api/mbta/routes/Red'
    When method get
    Then status 200
    And match response contains { id: 'Red', name: 'Red Line', kind: 'SUBWAY' }
    And match response.directions[*].destination contains 'Alewife'

  Scenario: unknown route is a 404 problem
    Given path '/api/mbta/routes/NoSuchRoute'
    When method get
    Then status 404
    And match response contains { title: 'Route not found', detail: 'Route NoSuchRoute does not exist' }

  Scenario: path values are validated before reaching MBTA
    Given path '/api/mbta/routes/Red Line!'
    When method get
    Then status 400

  Scenario: stations come with their boarding platforms
    Given path '/api/mbta/routes/Red/stations'
    When method get
    Then status 200
    And match response[*].name contains 'Park Street'
    And match each response contains { id: '#string', name: '#string', platforms: '#[]' }

  Scenario: next predictions at a station
    Given path '/api/mbta/stops/place-pktrm/predictions'
    And param routeId = 'Red'
    And param limit = 3
    When method get
    Then status 200
    And match response == '#[_ <= 3]'
    And match each response contains { routeId: 'Red', stopId: '#string' }

  Scenario: service alerts
    Given path '/api/mbta/routes/Red/alerts'
    When method get
    Then status 200
    And match each response contains { id: '#string', effect: '#string' }

  Scenario Outline: predictions limit must be between 1 and 50: <limit>
    Given path '/api/mbta/stops/place-pktrm/predictions'
    And param limit = <limit>
    When method get
    Then status 400
    And match response.fieldErrors[0].field == 'limit'

    Examples:
      | limit |
      | 0     |
      | 51    |
