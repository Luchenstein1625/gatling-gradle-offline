package bci.cards.simulation

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import java.util.UUID

class BciLoginSmokeSimulation extends Simulation {

  // --- VARIABLES DE ENTORNO ---
  private val loginUrl = sys.env.getOrElse("BCI_LOGIN_URL", "https://bci-api-crt001.internal.bci.cl/operaciones/seguridad-y-acceso/ms-loginclientes-util/v1.4/oauth/token")
  private val loginBasicAuth = sys.env.getOrElse("BCI_LOGIN_BASIC_AUTH", "YXBwLXBydWViYXMtYW5kZXM6MlZ3RlFCSHBtY0dBck5rYg==")
  private val loginApplicationId = sys.env.getOrElse("BCI_LOGIN_APPLICATION_ID", "asd")
  private val loginChannel = sys.env.getOrElse("BCI_LOGIN_CHANNEL", "910")
  private val loginReferenceService = sys.env.getOrElse("BCI_LOGIN_REFERENCE_SERVICE", "sad")
  private val loginReferenceOperation = sys.env.getOrElse("BCI_LOGIN_REFERENCE_OPERATION", "111")
  private val loginOriginAddr = sys.env.getOrElse("BCI_LOGIN_ORIGIN_ADDR", "111")
  private val loginCookie = sys.env.getOrElse("BCI_LOGIN_COOKIE", "_cfuvid=NNYC.3ZNbRjmgnd9QKyxyuqKgbYVlKjGfqjl71bWJSk-1728303064117-0.0.1.1-604800000")

  // --- FEEDER EN COLA (.queue) PARA LOS 5 RUTS ---
  private val usuariosFeeder = Array(
    Map("rutLogin" -> "10063842-8", "claveLogin" -> "111222"),
    Map("rutLogin" -> "10063843-6", "claveLogin" -> "111222"),
    Map("rutLogin" -> "10063844-4", "claveLogin" -> "111222"),
    Map("rutLogin" -> "10063845-2", "claveLogin" -> "111222"),
    Map("rutLogin" -> "10063846-0", "claveLogin" -> "111222")
  ).queue

  // --- PETICIÓN DE LOGIN ---
  private val smokeLoginRequest = http("POST Smoke Test - Login MultiUsuario")
    .post(loginUrl)
    .header("Content-Type", "application/x-www-form-urlencoded")
    .header("Authorization", s"Basic $loginBasicAuth")
    .header("Tracking-Id", "#{trackingId}")
    .header("Application-Id", loginApplicationId)
    .header("Channel", loginChannel)
    .header("Reference-Operation", loginReferenceOperation)
    .header("Reference-Service", loginReferenceService)
    .header("Origin-addr", loginOriginAddr)
    .header("Cookie", loginCookie)
    .formParam("username", "#{rutLogin}")
    .formParam("password", "#{claveLogin}")
    .formParam("grant_type", "password")
    .check(status.is(200))
    .check(jsonPath("$.access_token").saveAs("accessToken"))

  // --- PROTOCOLO HTTP ---
  private val httpProtocol = http
    .acceptHeader("application/json")

  // --- ESCENARIO ---
  private val scnSmokeTest = scenario("Smoke Test - Validar 5 RUTs")
    .feed(usuariosFeeder)
    .exec(session => session.set("trackingId", UUID.randomUUID().toString))
    .exec { session =>
      val rut = session("rutLogin").as[String]
      val trackingId = session("trackingId").as[String]
      println(s"--> [INICIO] Ejecutando Login para RUT: $rut | Tracking-Id: $trackingId")
      session
    }
    .exec(smokeLoginRequest)
    .exec { session =>
      val rut = session("rutLogin").as[String]
      // Sintaxis corregida: se accede a la clave dentro de session y luego .asOption
      val token = session("accessToken").asOption[String].getOrElse("NO OBTENIDO")
      val tokenCorto = if (token.length > 15) token.take(15) + "..." else token
      println(s"<-- [ÉXITO] Login OK para RUT: $rut | Token: $tokenCorto")
      session
    }

  // --- INYECCIÓN DE 5 USUARIOS CONCURRENTES ---
  setUp(
    scnSmokeTest.inject(atOnceUsers(5))
  ).protocols(httpProtocol)
   .assertions(
     global.failedRequests.count.is(0),
     global.responseTime.max.lt(3000)
   )
}