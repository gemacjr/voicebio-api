Feature: Stand-in for the MBTA GTFS-Realtime feed in self-contained tests (binary protobuf)

  Background:
    # built in Java so the bytes are real protobuf, identical to what the unit tests use
    * def Fixtures = Java.type('com.example.transit.GtfsFixtures')

  Scenario: pathMatches('/realtime/VehiclePositions.pb') && methodIs('get')
    * def responseHeaders = { 'Content-Type': 'application/x-protobuf' }
    * def response = Fixtures.vehiclePositionsBytes()

  Scenario:
    * def responseStatus = 404
    * def response = {}
