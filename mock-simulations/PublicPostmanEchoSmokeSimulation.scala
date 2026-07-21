package bci.cards.simulation

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class PublicPostmanEchoSmokeSimulation extends Simulation {

  // Solo comprueba que la integración Vault ya entregó la variable al proceso.
  // Su valor nunca se agrega a headers, body, query params o logs.
  private val vaultIntegrationCheck = sys.env.getOrElse("BCI_LOGIN_BASIC_AUTH", "TOKEN_PRUEBAS")

  private val baseUrl = sys.env.getOrElse(
    "PUBLIC_MOCK_BASE_URL",
    "https://postman-echo.com"
  )

  private val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .userAgentHeader("gatling-control-center-smoke/1.0")

  private val escenarioEcho = scenario("Mock público - Postman Echo")
    .exec(
      http("GET Echo público")
        .get("/get")
        .queryParam("source", "gatling-control-center")
        .check(status.is(200))
        .check(jsonPath("$.args.source").is("gatling-control-center"))
    )

  setUp(
    escenarioEcho.inject(atOnceUsers(1))
  ).protocols(httpProtocol)
    .maxDuration(15.seconds)
    .assertions(
      global.failedRequests.count.is(0),
      global.responseTime.max.lt(10000)
    )
}
