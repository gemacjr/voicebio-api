@transit
Feature: Transit vehicles: one endpoint, protobuf or JSON by content negotiation

  # Runs against the Karate mock (KarateApiIT) and against the real MBTA feed (KarateLiveIT),
  # so assertions describe the contract, not specific vehicles.
  # vehicleSchema uses strict equality: adding a field to transit.proto (directionId) failed this test until the
  # schema listed it. Protobuf clients ignore new fields; strict JSON consumers do not.

  Background:
    * url baseUrl
    * path '/api/transit/vehicles'
    * def VehicleList = Java.type('com.example.api.transit.v1.VehicleList')
    * def vehicleSchema = { vehicleId: '#string', routeId: '#string', latitude: '#number', longitude: '#number', label: '##string', tripId: '##string', bearing: '##number', speedMetersPerSecond: '##number', status: '##string', stopId: '##string', observedAt: '##string', directionId: '##number' }

  Scenario: JSON is protobuf's canonical JSON mapping
    Given param limit = 5
    And header Accept = 'application/json'
    When method get
    Then status 200
    And match karate.response.header('Content-Type') contains 'application/json'
    And match response == { feedTimestamp: '#string', totalInFeed: '#number', vehicles: '#[_ <= 5]' }
    And match each response.vehicles == vehicleSchema

  Scenario: binary protobuf decodes with the generated class
    Given param limit = 5
    And header Accept = 'application/x-protobuf'
    When method get
    Then status 200
    And match karate.response.header('Content-Type') contains 'application/x-protobuf'
    And match karate.response.header('X-Protobuf-Message') == 'voicebio.transit.v1.VehicleList'
    * def list = VehicleList.parseFrom(responseBytes)
    * assert list.getVehiclesCount() > 0 && list.getVehiclesCount() <= 5
    * assert list.getTotalInFeed() >= list.getVehiclesCount()
    * assert list.getVehicles(0).getVehicleId().length > 0

  Scenario: protobuf is smaller than JSON for the same data
    Given param limit = 50
    And header Accept = 'application/json'
    When method get
    Then status 200
    * def jsonSize = responseBytes.length
    Given path '/api/transit/vehicles'
    And param limit = 50
    And header Accept = 'application/x-protobuf'
    When method get
    Then status 200
    * def protobufSize = responseBytes.length
    * karate.log('JSON bytes:', jsonSize, 'protobuf bytes:', protobufSize)
    * assert protobufSize < jsonSize

  Scenario: filter by route
    Given param routeId = 'Red'
    And header Accept = 'application/json'
    When method get
    Then status 200
    And match each response.vehicles contains { routeId: 'Red' }

  Scenario: one vehicle, looked up from the list
    Given param limit = 1
    And header Accept = 'application/json'
    When method get
    Then status 200
    * def id = response.vehicles[0].vehicleId
    Given path '/api/transit/vehicles', id
    And header Accept = 'application/json'
    When method get
    Then status 200
    And match response.vehicleId == id

  Scenario: errors stay readable problem+json even when protobuf was requested
    Given path 'no-such-vehicle'
    And header Accept = 'application/x-protobuf'
    When method get
    Then status 404
    And match karate.response.header('Content-Type') contains 'application/problem+json'
    And match response.title == 'Vehicle not found'

  Scenario Outline: limit must be between 1 and 1000: <limit>
    Given param limit = <limit>
    And header Accept = 'application/json'
    When method get
    Then status 400
    And match response.fieldErrors[0].field == 'limit'

    Examples:
      | limit |
      | 0     |
      | 1001  |
