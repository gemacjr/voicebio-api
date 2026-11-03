@board
Feature: Live route board: MBTA V3 REST + GTFS-Realtime protobuf combined

  Background:
    * url baseUrl

  Scenario: both sources merged
    Given path '/api/routes/Red/live'
    When method get
    Then status 200
    And match response.sources == { v3: 'OK', gtfsRealtime: 'OK' }
    And match response.route contains { id: 'Red', name: 'Red Line' }
    And match response.feedTimestamp == '#string'
    And match each response.vehicles contains { vehicleId: '#string', latitude: '#number', longitude: '#number' }
    # every vehicle stopped at / heading to a platform got its station name from V3
    * def withStop = karate.filter(response.vehicles, function(v){ return v.stop != null })
    * def named = karate.filter(withStop, function(v){ return v.stop.name != null })
    * karate.log('vehicles:', response.vehicles.length, 'with a stop:', withStop.length, 'named:', named.length)
    * assert named.length == withStop.length
    # direction_id from protobuf resolves to a destination from V3
    * def destinations = response.route.directions.map(function(d){ return d.destination })
    * def withDirection = karate.filter(response.vehicles, function(v){ return v.directionId != null })
    * match each withDirection[*].destination == '#? destinations.indexOf(_) >= 0'

  Scenario: unknown route is a 404 even though the feed answered
    Given path '/api/routes/NoSuchRoute/live'
    When method get
    Then status 404
    And match response.title == 'Route not found'

  Scenario: the caller's correlation id is echoed
    Given path '/api/routes/Red/live'
    And header X-Request-Id = 'karate-board-1'
    When method get
    Then status 200
    And match karate.response.header('X-Request-Id') == 'karate-board-1'

  Scenario: no token, no access
    * configure headers = null
    Given path '/api/routes/Red/live'
    When method get
    Then status 401
