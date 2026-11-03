// Runs before every scenario. Decides where the API is and how to authenticate.
//   -Dkarate.baseUrl  API under test (default http://localhost:8081)
//   -Dkarate.token    bearer token; when absent, one is fetched once from the dev-only /api/authenticate
function fn() {
  var config = {
    baseUrl: karate.properties['karate.baseUrl'] || 'http://localhost:8081',
    problemSchema: { type: '#string', title: '#string', status: '#number', detail: '##string', instance: '#string', path: '#string', message: '#string' }
  };
  var token = karate.properties['karate.token'];
  if (!token) {
    token = karate.callSingle('classpath:karate/common/get-token.feature', config).token;
  }
  config.token = token;
  karate.configure('headers', { Authorization: 'Bearer ' + token });
  return config;
}
