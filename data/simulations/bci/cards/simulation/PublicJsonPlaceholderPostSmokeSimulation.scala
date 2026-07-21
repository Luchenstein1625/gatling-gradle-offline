package bci.cards.simulation

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class PublicJsonPlaceholderPostSmokeSimulation extends Simulation {

  // Requerido para verificar el flujo Vault -> proceso Gatling.
  // La credencial no sale del pod y no participa en la petición pública.
  private val vaultIntegrationCheck = sys.env.getOrElse("BCI_LOGIN_BASIC_AUTH", "TOKEN_PRUEBAS")

  private val baseUrl = sys.env.getOrElse(
    "PUBLIC_MOCK_BASE_URL",
    "https://jsonplaceholder.typicode.com"
  )

  private val httpProtocol = http
    .baseUrl(baseUrl)
    .contentTypeHeader("application/json")
    .acceptHeader("application/json")
    .userAgentHeader("gatling-control-center-smoke/1.0")

  private val escenarioPost = scenario("Mock público - JSONPlaceholder POST")
    .exec(
      http("POST simulado")
        .post("/posts")
        .body(StringBody("""{"title":"prueba-controlada","body":"gatling-smoke","userId":1}"""))
        .check(status.is(201))
        .check(jsonPath("$.id").exists)
    )

  setUp(
    escenarioPost.inject(atOnceUsers(1))
  ).protocols(httpProtocol)
    .maxDuration(15.seconds)
    .assertions(
      global.failedRequests.count.is(0),
      global.responseTime.max.lt(10000)
    )
}
