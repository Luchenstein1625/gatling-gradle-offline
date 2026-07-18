package bci.cards.simulation

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration._

class PublicMockSimulation extends Simulation {

  private val baseUrl =
    System.getProperty("mock.baseUrl", "https://jsonplaceholder.typicode.com")

  private val path =
    System.getProperty("mock.path", "/posts/1")

  private val method =
    System.getProperty("mock.method", "GET").toUpperCase

  private val users =
    Integer.getInteger("mock.users", 1)

  private val rampSeconds =
    Integer.getInteger("mock.rampSeconds", 1)

  private val durationSeconds =
    Integer.getInteger("mock.durationSeconds", 10)

  private val protocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .userAgentHeader("gatling-control-api-smoke-test/1.0")

  private val request =
    method match {
      case "GET" =>
        http("Public mock GET")
          .get(path)
          .check(status.in(200, 201, 202, 204))

      case "POST" =>
        http("Public mock POST")
          .post(path)
          .body(StringBody("""{"title":"gatling","body":"smoke","userId":1}"""))
          .check(status.in(200, 201, 202, 204))

      case other =>
        throw new IllegalArgumentException(
          s"Metodo mock no soportado: $other. Use GET o POST."
        )
    }

  private val scn = scenario("Public Mock Smoke")
    .during(durationSeconds.seconds) {
      exec(request).pause(1.second)
    }

  setUp(
    scn.inject(rampUsers(users).during(rampSeconds.seconds))
  ).protocols(protocol)
   .assertions(
     global.failedRequests.percent.lte(1.0)
   )
}
