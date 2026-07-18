package bci.cards.simulation

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.http.request.builder.HttpRequestBuilder
import scala.concurrent.duration._
import java.util.UUID

class BcimsInformacionBasicaTpsSimulation extends Simulation {

  // --- VARIABLES DE ENTORNO ---
  private val loginUrl = sys.env.getOrElse("BCI_LOGIN_URL", "https://bci-api-crt001.internal.bci.cl/operaciones/seguridad-y-acceso/ms-loginclientes-util/v1.3/oauth/token")
  private val apiBaseUrl = sys.env.getOrElse("BCI_API_BASE_URL", "http://bci-api-crt004.bci.cl")
  private val loginBasicAuth = sys.env.getOrElse("BCI_LOGIN_BASIC_AUTH", "YXBwLW1vdmlsLXBlcnNvbmFzOnNlY3JldGlzaW1v")
  private val loginApplicationId = sys.env.getOrElse("BCI_LOGIN_APPLICATION_ID", "Pruebas de Rendimiento Andes Cert")
  private val loginChannel = sys.env.getOrElse("BCI_LOGIN_CHANNEL", "910")
  private val loginReferenceService = sys.env.getOrElse("BCI_LOGIN_REFERENCE_SERVICE", "Pruebas de Rendimiento Andes Cert")
  private val loginReferenceOperation = sys.env.getOrElse("BCI_LOGIN_REFERENCE_OPERATION", "111")
  private val loginOriginAddr = sys.env.getOrElse("BCI_LOGIN_ORIGIN_ADDR", "111")
  private val loginCookie = sys.env.getOrElse("BCI_LOGIN_COOKIE", "_cfuvid=NNYC.3ZNbRjmgnd9QKyxyuqKgbYVlKjGfqjl71bWJSk-1728303064117-0.0.1.1-604800000")
  
  private val loginXIbmClientId = sys.env.get("BCI_LOGIN_X_IBM_CLIENT_ID").filter(_.nonEmpty)
  private val loginClientId = sys.env.get("BCI_LOGIN_CLIENT_ID").filter(_.nonEmpty)

  private val apiApplicationId = sys.env.getOrElse("BCI_API_APPLICATION_ID", "postman")
  private val apiChannel = sys.env.getOrElse("BCI_API_CHANNEL", "910")
  private val apiReferenceService = sys.env.getOrElse("BCI_API_REFERENCE_SERVICE", "postman")
  private val apiReferenceOperation = sys.env.getOrElse("BCI_API_REFERENCE_OPERATION", "postman")
  private val apiOriginAddr = sys.env.getOrElse("BCI_API_ORIGIN_ADDR", "127.0.0.1")

  // --- FEEDER EN MEMORIA CON TUS DATOS COMPROBADOS ---
  private val usuariosFeeder = List(
    Map("rutLogin" -> "10063842-8", "claveLogin" -> "111222", "cuenta" -> "000000064289", "tarjeta" -> "4481656890002891"),
    Map("rutLogin" -> "10063843-6", "claveLogin" -> "111222", "cuenta" -> "000000064293", "tarjeta" -> "4481656890002909"),
    Map("rutLogin" -> "10063844-4", "claveLogin" -> "111222", "cuenta" -> "000000064295", "tarjeta" -> "4548122400006576"),
    Map("rutLogin" -> "10063845-2", "claveLogin" -> "111222", "cuenta" -> "000000064296", "tarjeta" -> "4548122400006584"),
    Map("rutLogin" -> "10063846-0", "claveLogin" -> "111222", "cuenta" -> "000000064300", "tarjeta" -> "4548122400006592")
  ).toArray.circular

  private def addOptionalHeader(requestBuilder: HttpRequestBuilder, headerName: String, headerValue: Option[String]): HttpRequestBuilder =
    headerValue.map(v => requestBuilder.header(headerName, v)).getOrElse(requestBuilder)

  private val loginRequest = {
    val base = http("POST Login Dinámico")
      .post(loginUrl)
      .header("Accept", "application/json")
      .header("Content-Type", "application/x-www-form-urlencoded")
      .header("Authorization", s"Basic $loginBasicAuth")
      .header("Application-Id", loginApplicationId)
      .header("Channel", loginChannel)
      .header("Reference-Service", loginReferenceService)
      .header("Reference-Operation", loginReferenceOperation)
      .header("Origin-addr", loginOriginAddr)
      .header("Tracking-Id", "#{trackingId}")
      .header("Cookie", loginCookie)
      .formParam("username", "#{rutLogin}")   
      .formParam("password", "#{claveLogin}") 
      .formParam("grant_type", "password")
      .check(status.is(200))
      .check(jsonPath("$.access_token").saveAs("authToken"))

    val withIbmClient = addOptionalHeader(base, "x-ibm-client-id", loginXIbmClientId)
    addOptionalHeader(withIbmClient, "client-id", loginClientId)
  }

  private def withApiHeaders(req: HttpRequestBuilder): HttpRequestBuilder =
    req
      .header("application-id", apiApplicationId)
      .header("channel", apiChannel)
      .header("reference-service", apiReferenceService)
      .header("reference-operation", apiReferenceOperation)
      .header("origin-addr", apiOriginAddr)
      .header("tracking-id", "#{trackingId}")
      .header("authorization", "bearer #{authToken}")

  private val httpProtocol = http
    .baseUrl(apiBaseUrl)
    .contentTypeHeader("application/json")
    .acceptHeader("application/json")

  // ====================================================================================
  // --- ESCENARIOS TPS CORREGIDOS (Login una vez + bucle continuo) ---
  // ====================================================================================

  private val scnTarjetas = scenario("Flujo TPS - Tarjetas")
    .feed(usuariosFeeder).exec(session => session.set("trackingId", UUID.randomUUID().toString)).exec(loginRequest).exitHereIfFailed
    .forever { 
      exec(
        withApiHeaders(
          http("GET Tarjetas Persona")
            .get("/operaciones-y-ejecucion/tarjetas/ms-tdc-informacionbasica-orq/v2.1/personas/tarjetas/#{rutLogin}")
            .queryParam("incluirTarjetasEmpresa", "true")
            .check(status.is(200))
        )
      )
    }

  private val scnSaldos = scenario("Flujo TPS - Saldos Tarjeta")
    .feed(usuariosFeeder).exec(session => session.set("trackingId", UUID.randomUUID().toString)).exec(loginRequest).exitHereIfFailed
    .forever { 
      exec(
        withApiHeaders(
          http("POST Saldos Tarjeta")
            .post("/operaciones-y-ejecucion/tarjetas/ms-tdc-informacionbasica-orq/v2.1/personas/tarjeta/saldos")
            .body(StringBody("""{"numeroCuenta":"#{cuenta}","numeroTarjeta":"#{tarjeta}","rutCliente":"#{rutLogin}"}"""))
            .check(status.is(200))
        )
      )
    }

  private val scnDeuda = scenario("Flujo TPS - Deuda Mora")
    .feed(usuariosFeeder).exec(session => session.set("trackingId", UUID.randomUUID().toString)).exec(loginRequest).exitHereIfFailed
    .forever { 
      exec(
        withApiHeaders(
          http("POST Deuda Mora")
            .post("/operaciones-y-ejecucion/tarjetas/ms-tdc-informacionbasica-orq/v2.1/personas/cuenta/deuda-mora")
            .body(StringBody("""{"numeroCuenta":"#{cuenta}","rutCliente":"#{rutLogin}"}"""))
            .check(status.is(200))
        )
      )
    }

  // ====================================================================================
  // --- INYECCIÓN CON THROTTLE EXCLUSIVA DE TPS (Meseta: 14 minutos totales) ---
  // ====================================================================================
  setUp(
    scnTarjetas.inject(rampUsers(50).during(10.seconds)),
    scnSaldos.inject(rampUsers(100).during(10.seconds)),
    scnDeuda.inject(rampUsers(70).during(10.seconds))
  ).protocols(httpProtocol)
   .maxDuration(14.minutes)
   .throttle(
     // Meseta Plana Combinada (Tarjetas 48 + Saldos 103 + Deuda 63 = 214 Rps en total)
     reachRps(214).in(30.seconds),
     holdFor(13.minutes),
     reachRps(3).in(30.seconds) // Cooldown de bajada controlado al final
   )
   .assertions(
     global.failedRequests.percent.lt(1.0),
     global.responseTime.percentile(95).lt(3000)
   )
}