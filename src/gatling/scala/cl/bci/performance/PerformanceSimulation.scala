package cl.bci.performance

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class PerformanceSimulation extends Simulation {

  private val baseUrl = System.getProperty("baseUrl")
  private val targetPath = System.getProperty("targetPath", "/health")
  private val users = Integer.getInteger("users", 10)
  private val rampSeconds = Integer.getInteger("rampSeconds", 30)
  private val durationSeconds = Integer.getInteger("durationSeconds", 60)

  private val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .userAgentHeader("gatling-performance/1.0")

  private val scn = scenario("Prueba de rendimiento")
    .during(durationSeconds.seconds) {
      exec(
        http("GET endpoint")
          .get(targetPath)
          .check(status.in(200, 201, 202, 204))
      ).pause(1.second)
    }

  setUp(
    scn.inject(rampUsers(users).during(rampSeconds.seconds))
  ).protocols(httpProtocol)
   .assertions(
     global.failedRequests.percent.lte(1.0),
     global.responseTime.percentile(90.0).lte(2000)
   )
}
