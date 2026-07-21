package bci.cards.simulation

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class PublicJsonPlaceholderSmokeSimulation extends Simulation {

  // El backend exige que Vault entregue esta variable antes de iniciar Gatling.
  // La credencial NO se envía a la API pública y NO se imprime en los logs.
  private val vaultIntegrationCheck = sys.env.getOrElse("BCI_LOGIN_BASIC_AUTH", "TOKEN_PRUEBAS")

  private val baseUrl = sys.env.getOrElse(
    "PUBLIC_MOCK_BASE_URL",
    "https://jsonplaceholder.typicode.com"
  )

  private val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .userAgentHeader("gatling-control-center-smoke/1.0")

  private val scenarioPublico = scenario("Mock público - JSONPlaceholder")
    .exec(
      http("GET Todo público")
        .get("/todos/1")
        .check(status.is(200))
        .check(jsonPath("$.id").exists)
        .check(jsonPath("$.title").exists)
    )

  setUp(
    scenarioPublico.inject(atOnceUsers(1))
  ).protocols(httpProtocol)
    .maxDuration(15.seconds)
    .assertions(
      global.failedRequests.count.is(0),
      global.responseTime.max.lt(10000)
    )
}
