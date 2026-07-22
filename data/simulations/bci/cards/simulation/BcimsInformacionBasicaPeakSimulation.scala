package bci.cards.simulation

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.http.request.builder.HttpRequestBuilder
import scala.concurrent.duration._
import java.util.UUID

class BcimsInformacionBasicaPeakSimulation extends Simulation {

  // --- VARIABLES DE ENTORNO ---
  private val loginUrl = sys.env.getOrElse("BCI_LOGIN_URL", "https://bci-api-crt001.internal.bci.cl/operaciones/seguridad-y-acceso/ms-loginclientes-util/v1.4/oauth/token")
  private val apiBaseUrl = sys.env.getOrElse("BCI_API_BASE_URL", "http://bci-api-crt004.bci.cl")
  private val loginBasicAuth = sys.env.getOrElse("BCI_LOGIN_BASIC_AUTH", "YXBwLXBydWViYXMtYW5kZXM6MlZ3RlFCSHBtY0dBck5rYg==")
  private val loginApplicationId = sys.env.getOrElse("BCI_LOGIN_APPLICATION_ID", "app-prueba-andes")
  private val loginChannel = sys.env.getOrElse("BCI_LOGIN_CHANNEL", "910")
  private val loginReferenceService = sys.env.getOrElse("BCI_LOGIN_REFERENCE_SERVICE", "sad")
  private val loginReferenceOperation = sys.env.getOrElse("BCI_LOGIN_REFERENCE_OPERATION", "111")
  private val loginOriginAddr = sys.env.getOrElse("BCI_LOGIN_ORIGIN_ADDR", "111")

  private val apiApplicationId = sys.env.getOrElse("BCI_API_APPLICATION_ID", "postman")
  private val apiChannel = sys.env.getOrElse("BCI_API_CHANNEL", "910")
  private val apiReferenceService = sys.env.getOrElse("BCI_API_REFERENCE_SERVICE", "postman")
  private val apiReferenceOperation = sys.env.getOrElse("BCI_API_REFERENCE_OPERATION", "postman")
  private val apiOriginAddr = sys.env.getOrElse("BCI_API_ORIGIN_ADDR", "127.0.0.1")

  // --- FEEDER EN MEMORIA ---
  private val usuariosFeeder = List(
    Map("rutLogin" -> "10063842-8", "cuenta" -> "000000064289", "tarjeta" -> "4481656890002891"),
    Map("rutLogin" -> "10063843-6", "cuenta" -> "000000064293", "tarjeta" -> "4481656890002909"),
    Map("rutLogin" -> "10063844-4", "cuenta" -> "000000064295", "tarjeta" -> "4548122400006576"),
    Map("rutLogin" -> "10063845-2", "cuenta" -> "000000064296", "tarjeta" -> "4548122400006584"),
    Map("rutLogin" -> "10063846-0", "cuenta" -> "000000064300", "tarjeta" -> "4548122400006592")
  ).toArray.circular

  // --- PETICIÓN DE LOGIN CON LOGS DE ESTADO Y RUT ---
  private val loginRequest = http("POST Login Client Credentials")
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
    .formParam("grant_type", "client_credentials")
    .formParam("rut", "#{rutLogin}")
    .check(status.saveAs("httpStatusLogin"))
    .check(jsonPath("$.access_token").optional.saveAs("authToken"))

  // --- HEADERS DE LAS PETICIONES DE NEGOCIO ---
  private def withApiHeaders(req: HttpRequestBuilder): HttpRequestBuilder =
    req
      .header("application-id", apiApplicationId)
      .header("channel", apiChannel)
      .header("reference-service", apiReferenceService)
      .header("reference-operation", apiReferenceOperation)
      .header("origin-addr", apiOriginAddr)
      .header("tracking-id", "#{trackingId}")
      .header("authorization", "Bearer #{authToken}")

  private val httpProtocol = http
    .baseUrl(apiBaseUrl)
    .contentTypeHeader("application/json")
    .acceptHeader("application/json")

  // --- ESCENARIOS (CON LOGS DE ENTRADA Y SALIDA POR RUT) ---
  private val scnTarjetas = scenario("Flujo PEAK - Tarjetas")
    .feed(usuariosFeeder)
    .exec(session => session.set("trackingId", UUID.randomUUID().toString))
    .exec { session =>
      val rut = session("rutLogin").as[String]
      println(s"--> [LOGIN ATTEMPT] Probando autenticación para RUT: $rut")
      session
    }
    .exec(loginRequest)
    .exec { session =>
      val rut = session("rutLogin").as[String]
      val status = session("httpStatusLogin").asOption[Int].getOrElse(0)
      val tokenOpt = session("authToken").asOption[String]

      tokenOpt match {
        case Some(token) =>
          val tokenCorto = if (token.length > 20) token.take(20) + "..." else token
          println(s"<-- [LOGIN SUCCESS] RUT: $rut | Status: $status | Token: $tokenCorto")
        case None =>
          println(s"<-- [LOGIN FAILED] RUT: $rut | Status: $status | Sin Token")
      }
      session
    }
    .exitHereIfFailed
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

  private val scnSaldos = scenario("Flujo PEAK - Saldos Tarjeta")
    .feed(usuariosFeeder)
    .exec(session => session.set("trackingId", UUID.randomUUID().toString))
    .exec(loginRequest)
    .exitHereIfFailed
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

  private val scnDeuda = scenario("Flujo PEAK - Deuda Mora")
    .feed(usuariosFeeder)
    .exec(session => session.set("trackingId", UUID.randomUUID().toString))
    .exec(loginRequest)
    .exitHereIfFailed
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

  // --- CONFIGURACIÓN DE PRUEBA CORTA ---
setUp(
    scnTarjetas.inject(atOnceUsers(5)),
    scnSaldos.inject(atOnceUsers(5)),
    scnDeuda.inject(atOnceUsers(5))
  ).protocols(httpProtocol)
   .maxDuration(10.seconds)
   .assertions(
     global.failedRequests.count.is(0),
     global.responseTime.max.lt(5000)
   )
}