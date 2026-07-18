// BCI Login Smoke Simulation - Correct Version
// Upload this file using the UI: 1b. Upload Scala Dinamico

class BciLoginBusinessSmokeSimulation extends Simulation {
  
  val httpProtocol = http
    .baseUrl("https://jsonplaceholder.typicode.com")
    .acceptHeader("application/json")
    .userAgentHeader("Gatling Demo/1.0")
    .connectTimeout(5000)
    .requestTimeout(10000)

  val scn = scenario("Demo Posts API")
    .exec(http("Get Posts")
      .get("/posts")
      .check(status.is(200)))
    .pause(1, 2)
    
    .exec(http("Get Post #1")
      .get("/posts/1")
      .check(status.is(200))
      .check(jsonPath("$.title").exists))
    .pause(1, 2)
    
    .exec(http("Get Comments for Post #1")
      .get("/posts/1/comments")
      .check(status.is(200))
      .check(jsonPath("$[*].email").count.gt(0)))
    .pause(2, 3)

  setUp(
    scn.inject(atOnceUsers(5))
  ).protocols(httpProtocol)
    .assertions(
      global.responseTime.max.lt(5000),
      global.successfulRequests.percent.gt(95)
    )
}
