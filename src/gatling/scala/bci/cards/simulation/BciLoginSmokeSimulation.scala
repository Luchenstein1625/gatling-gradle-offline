package bci.cards.simulation

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import java.util.UUID

class BciLoginSmokeSimulation extends Simulation {

  // --- VARIABLES DE ENTORNO (Configuradas con los datos de tu cURL) ---
  private val loginUrl = sys.env.getOrElse("BCI_LOGIN_URL", "https://bci-api-crt001.internal.bci.cl/operaciones/seguridad-y-acceso/ms-loginclientes-util/v1.3/oauth/token")
  private val loginBasicAuth = sys.env.getOrElse("BCI_LOGIN_BASIC_AUTH", "YXBwLW1vdmlsLXBlcnNvbmFzOnNlY3JldGlzaW1v")
  private val loginApplicationId = sys.env.getOrElse("BCI_LOGIN_APPLICATION_ID", "asd")
  private val loginChannel = sys.env.getOrElse("BCI_LOGIN_CHANNEL", "910")
  private val loginReferenceService = sys.env.getOrElse("BCI_LOGIN_REFERENCE_SERVICE", "sad")
  private val loginReferenceOperation = sys.env.getOrElse("BCI_LOGIN_REFERENCE_OPERATION", "111")
  private val loginOriginAddr = sys.env.getOrElse("BCI_LOGIN_ORIGIN_ADDR", "111")
  private val loginCookie = sys.env.getOrElse("BCI_LOGIN_COOKIE", "_cfuvid=NNYC.3ZNbRjmgnd9QKyxyuqKgbYVlKjGfqjl71bWJSk-1728303064117-0.0.1.1-604800000")

  // --- FEEDER EN MEMORIA CON LOS 5 RUTS DE TU LISTA ---
  private val usuariosFeeder = Map(
    "10063842-8" -> "111222",
    "10063843-6" -> "111222",
    "10063844-4" -> "111222",
    "10063845-2" -> "111222",
    "10063846-0" -> "111222"
  ).map { case (rut, clave) => Map("rutLogin" -> rut, "claveLogin" -> clave) }.toArray.circular

  // --- PETICIÓN DE LOGIN DINÁMICA ---
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
    // Consumimos las variables dinámicas extraídas del feeder
    .formParam("username", "#{rutLogin}")
    .formParam("password", "#{claveLogin}")
    .formParam("grant_type", "password")
    .check(status.is(200))
    .check(jsonPath("$.access_token").exists)

  // --- PROTOCOLO HTTP BASE ---
  private val httpProtocol = http
    .acceptHeader("application/json")

  // --- ESCENARIO ---
  private val scnSmokeTest = scenario("Smoke Test - Validar 5 RUTs")
    .feed(usuariosFeeder) // Cada usuario virtual toma un set del Map
    .exec(session => session.set("trackingId", UUID.randomUUID().toString))
    .exec(smokeLoginRequest)

  // --- PERFIL DE INYECCIÓN (5 usuarios concurrentes de golpe) ---
  setUp(
    scnSmokeTest.inject(atOnceUsers(5))
  ).protocols(httpProtocol)
   .assertions(
     global.failedRequests.count.is(0), // Si falla aunque sea 1 de los 5 RUTs, la prueba falla
     global.responseTime.max.lt(3000)   // Ningún login debería tardar más de 3 segundos
   )
}