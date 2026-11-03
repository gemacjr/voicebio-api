@ignore
Feature: Obtain a dev-only bearer token (called once per run from karate-config.js)

  Scenario:
    Given url baseUrl + '/api/authenticate'
    And request { username: 'karate' }
    When method post
    Then status 200
    * def token = response.id_token
