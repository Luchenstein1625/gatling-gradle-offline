package bci.cards.simulation

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class BciLoginSmokeSimulation extends Simulation {

  // --- VARIABLES DE ENTORNO (alineadas al cURL entregado) ---
  private val loginUrl = sys.env.getOrElse("BCI_LOGIN_URL", "http://api-dsr01.bci.cl/operaciones/seguridad-y-acceso/ms-loginclientes-util/develop/oauth/token")
  private val loginBasicAuth = sys.env.getOrElse("BCI_LOGIN_BASIC_AUTH", "YXBwLXBydWViYXMtYW5kZXM6MlZ3RlFCSHBtY0dBck5rYg==")
  private val loginTrackingId = sys.env.getOrElse("BCI_LOGIN_TRACKING_ID", "asd")
  private val loginApplicationId = sys.env.getOrElse("BCI_LOGIN_APPLICATION_ID", "app-prueba-andes")
  private val loginChannel = sys.env.getOrElse("BCI_LOGIN_CHANNEL", "910")
  private val loginReferenceOperation = sys.env.getOrElse("BCI_LOGIN_REFERENCE_OPERATION", "111")
  private val loginReferenceService = sys.env.getOrElse("BCI_LOGIN_REFERENCE_SERVICE", "sad")
  private val loginOriginAddr = sys.env.getOrElse("BCI_LOGIN_ORIGIN_ADDR", "111")
  private val loginRut = sys.env.getOrElse("BCI_LOGIN_RUT", "10063846-0")

  private val loginGrantType = "client_credentials"

  private def maskSecret(value: String): String = {
    if (value == null || value.isEmpty) {
      "[VACÍO]"
    } else if (value.length <= 2) {
      "*" * value.length
    } else {
      s"${value.head}${"*" * math.min(value.length - 2, 10)}${value.last}"
    }
  }

  private val loginBasicAuthSource =
    if (sys.env.contains("BCI_LOGIN_BASIC_AUTH")) {
      "VARIABLE_ENTORNO"
    } else {
      "VALOR_PREDETERMINADO_SCALA"
    }

  println("===================================================")
  println("[SCALA] CONFIGURACIÓN REAL DE LA PRUEBA")
  println(s"[SCALA] Clase: ${getClass.getName}")
  println(s"[SCALA] URL: $loginUrl")
  println(s"[SCALA] Grant type: $loginGrantType")
  println(s"[SCALA] Application-Id: $loginApplicationId")
  println(s"[SCALA] Channel: $loginChannel")
  println(s"[SCALA] Reference-Service: $loginReferenceService")
  println(s"[SCALA] Reference-Operation: $loginReferenceOperation")
  println(s"[SCALA] Origin-Addr: $loginOriginAddr")
  println(
    s"[SCALA] BCI_LOGIN_BASIC_AUTH: ${maskSecret(loginBasicAuth)}"
  )
  println(s"[SCALA] Fuente Basic Auth: $loginBasicAuthSource")
  println("===================================================")

  // --- PETICIÓN DE LOGIN (client_credentials) ---
  private val smokeLoginRequest = http("POST Smoke Test - Login Client Credentials")
    .post(loginUrl)
    .header("Accept", "application/json")
    .header("Content-Type", "application/x-www-form-urlencoded")
    .header("Authorization", s"Basic $loginBasicAuth")
    .header("Tracking-Id", loginTrackingId)
    .header("Application-Id", loginApplicationId)
    .header("Channel", loginChannel)
    .header("Reference-Service", loginReferenceService)
    .header("Reference-Operation", loginReferenceOperation)
    .header("Origin-addr", loginOriginAddr)
    .formParam("grant_type", loginGrantType)
    .formParam("rut", loginRut)
    .check(status.is(200))
    .check(jsonPath("$.access_token").saveAs("authToken"))

  // --- PROTOCOLO HTTP BASE ---
  private val httpProtocol = http
    .acceptHeader("application/json")

  // --- ESCENARIO ---
  private val scnSmokeTest = scenario("Smoke Test - Login client_credentials")
    .exec(smokeLoginRequest)
    .exec { session =>
      session
    }

  // --- PERFIL DE INYECCIÓN (1 usuario, una ejecución) ---
  setUp(
    scnSmokeTest.inject(atOnceUsers(1))
  ).protocols(httpProtocol)
   .assertions(
     global.failedRequests.count.is(0),
     global.responseTime.max.lt(3000)
   )
}