Feature: Stand-in for the MBTA V3 API (https://api-v3.mbta.com) in self-contained tests

  # Serves the same JSON:API fixtures as the unit tests, with MBTA's content type and rate-limit header.
  # Like the real API: unknown routes are 404 with a JSON:API error document.

  Background:
    * def fixture = function(name){ return karate.read('classpath:fixtures/mbta/' + name) }
    * def jsonApi = { 'Content-Type': 'application/vnd.api+json', 'x-ratelimit-remaining': '19' }

  Scenario: pathMatches('/routes/{id}') && methodIs('get')
    * def responseHeaders = jsonApi
    * def known = pathParams.id == 'Red'
    * def responseStatus = known ? 200 : 404
    * def response = known ? fixture('route-red.json') : fixture('error-404.json')

  Scenario: pathMatches('/routes') && methodIs('get')
    * def responseHeaders = jsonApi
    * def response = fixture('routes-subway.json')

  Scenario: pathMatches('/stops') && methodIs('get')
    * def responseHeaders = jsonApi
    * def response = paramValue('filter[route]') == 'Red' ? fixture('stations-red.json') : { data: [], included: [] }

  Scenario: pathMatches('/predictions') && methodIs('get')
    * def responseHeaders = jsonApi
    * def response = fixture('predictions-pktrm.json')

  Scenario: pathMatches('/alerts') && methodIs('get')
    * def responseHeaders = jsonApi
    * def response = fixture('alerts-red.json')

  Scenario:
    * def responseStatus = 404
    * def response = fixture('error-404.json')
