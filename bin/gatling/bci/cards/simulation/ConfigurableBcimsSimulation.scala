package bci.cards.simulation

import io.gatling.core.Predef._
import io.gatling.core.controller.throttle.ThrottleStep
import io.gatling.http.Predef._
import io.gatling.http.request.builder.HttpRequestBuilder
import org.yaml.snakeyaml.Yaml

import java.io.{FileInputStream, File}
import java.util.UUID
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

class ConfigurableBcimsSimulation extends Simulation {

  private val configFile = System.getProperty("configFile")
  private val usersFile = System.getProperty("usersFile", "")

  require(configFile != null && configFile.nonEmpty, "Falta -DconfigFile")

  private val yaml = new Yaml()
  private val root = {
    val in = new FileInputStream(configFile)
    try yaml.load[java.util.Map[String, Object]](in).asScala.toMap
    finally in.close()
  }

  private def mapAt(name: String): Map[String, Object] =
    root(name).asInstanceOf[java.util.Map[String, Object]].asScala.toMap

  private def string(map: Map[String, Object], key: String, default: String = ""): String =
    Option(map.getOrElse(key, default)).map(_.toString).getOrElse(default)

  private def int(map: Map[String, Object], key: String, default: Int): Int =
    Option(map.getOrElse(key, default)).map(_.toString.toInt).getOrElse(default)

  private def double(map: Map[String, Object], key: String, default: Double): Double =
    Option(map.getOrElse(key, default)).map(_.toString.toDouble).getOrElse(default)

  private def resolve(value: String): String = {
    val pattern = """\$\{([^}]+)\}""".r
    pattern.replaceAllIn(value, m =>
      sys.env.getOrElse(m.group(1),
        throw new IllegalArgumentException(s"Falta variable de entorno ${m.group(1)}"))
    )
  }

  private val auth = mapAt("authentication")
  private val target = mapAt("target")
  private val scenarios = mapAt("scenarios")
  private val assertionsConfig = mapAt("assertions")

  private val baseUrl = resolve(string(target, "baseUrl"))
  private val authMode = string(auth, "mode").toUpperCase

  require(
    usersFile.nonEmpty && new File(usersFile).isFile,
    "Falta el archivo CSV de usuarios. Suba users.csv junto con configuration.yaml"
  )

  /*
   * Ambas modalidades necesitan rutLogin, cuenta y tarjeta para ejecutar
   * las operaciones. LOGIN además utiliza claveLogin.
   *
   * Al usar siempre csv(...).circular el tipo queda definido como
   * FeederBuilder y evita que Scala lo reduzca a Object.
   */
  private val usuariosFeeder = csv(usersFile).circular

  private def optionalHeader(req: HttpRequestBuilder, name: String, value: String): HttpRequestBuilder =
    if (value == null || value.isBlank) req else req.header(name, value)

  private val loginRequest = {
    val base = http("POST Login Dinamico")
      .post(resolve(string(auth, "loginUrl")))
      .header("Accept", "application/json")
      .header("Content-Type", "application/x-www-form-urlencoded")
      .header("Authorization", s"Basic ${resolve(string(auth, "basicAuth"))}")
      .header("Application-Id", resolve(string(auth, "applicationId")))
      .header("Channel", resolve(string(auth, "channel")))
      .header("Reference-Service", resolve(string(auth, "referenceService")))
      .header("Reference-Operation", resolve(string(auth, "referenceOperation")))
      .header("Origin-addr", resolve(string(auth, "originAddr")))
      .header("Tracking-Id", "#{trackingId}")
      .formParam("username", "#{rutLogin}")
      .formParam("password", "#{claveLogin}")
      .formParam("grant_type", "password")
      .check(status.is(200))
      .check(jsonPath("$.access_token").saveAs("authToken"))

    val cookie = Option(string(auth, "cookie")).filter(_.nonEmpty).map(resolve).getOrElse("")
    val ibm = Option(string(auth, "xIbmClientId")).filter(_.nonEmpty).map(resolve).getOrElse("")
    val client = Option(string(auth, "clientId")).filter(_.nonEmpty).map(resolve).getOrElse("")

    optionalHeader(optionalHeader(optionalHeader(base, "Cookie", cookie), "x-ibm-client-id", ibm), "client-id", client)
  }

  private def externalToken: String = {
    val envName = string(auth, "tokenEnvironmentVariable", "BCI_API_TOKEN")
    val prefix = string(auth, "tokenPrefix", "bearer")
    val token = sys.env.getOrElse(envName,
      throw new IllegalArgumentException(s"Falta secreto/variable $envName"))
    s"$prefix $token"
  }

  private def authenticate =
    if (authMode == "LOGIN") exec(loginRequest).exitHereIfFailed
    else exec(session => session.set("authToken", externalToken.stripPrefix("bearer ").stripPrefix("Bearer ")))

  private def apiHeaders(req: HttpRequestBuilder): HttpRequestBuilder =
    req
      .header("application-id", resolve(string(target, "applicationId")))
      .header("channel", resolve(string(target, "channel")))
      .header("reference-service", resolve(string(target, "referenceService")))
      .header("reference-operation", resolve(string(target, "referenceOperation")))
      .header("origin-addr", resolve(string(target, "originAddr")))
      .header("tracking-id", "#{trackingId}")
      .header("authorization",
        if (authMode == "LOGIN") "bearer #{authToken}" else externalToken)

  private val httpProtocol = http
    .baseUrl(baseUrl)
    .contentTypeHeader("application/json")
    .acceptHeader("application/json")

  private def baseScenario(name: String) =
    scenario(name)
      .feed(usuariosFeeder)
      .exec(session => session.set("trackingId", UUID.randomUUID().toString))
      .exec(authenticate)

  private val tarjetas = baseScenario("Tarjetas")
    .forever {
      exec(session => session.set("trackingId", UUID.randomUUID().toString))
        .exec(apiHeaders(
          http("GET Tarjetas Persona")
            .get("/operaciones-y-ejecucion/tarjetas/ms-tdc-informacionbasica-orq/v2.1/personas/tarjetas/#{rutLogin}")
            .queryParam("incluirTarjetasEmpresa", "true")
            .check(status.is(200))
        ))
    }

  private val saldos = baseScenario("Saldos")
    .forever {
      exec(session => session.set("trackingId", UUID.randomUUID().toString))
        .exec(apiHeaders(
          http("POST Saldos Tarjeta")
            .post("/operaciones-y-ejecucion/tarjetas/ms-tdc-informacionbasica-orq/v2.1/personas/tarjeta/saldos")
            .body(StringBody("""{"numeroCuenta":"#{cuenta}","numeroTarjeta":"#{tarjeta}","rutCliente":"#{rutLogin}"}"""))
            .check(status.is(200))
        ))
    }

  private val deuda = baseScenario("Deuda")
    .forever {
      exec(session => session.set("trackingId", UUID.randomUUID().toString))
        .exec(apiHeaders(
          http("POST Deuda Mora")
            .post("/operaciones-y-ejecucion/tarjetas/ms-tdc-informacionbasica-orq/v2.1/personas/cuenta/deuda-mora")
            .body(StringBody("""{"numeroCuenta":"#{cuenta}","rutCliente":"#{rutLogin}"}"""))
            .check(status.is(200))
        ))
    }

  private def scenarioSettings(name: String): Map[String, Object] =
    scenarios(name).asInstanceOf[java.util.Map[String, Object]].asScala.toMap

  private val throttleSteps: Seq[ThrottleStep] = {
    val items = root("throttle")
      .asInstanceOf[java.util.List[java.util.Map[String, Object]]]
      .asScala

    items.map(_.asScala.toMap).map { item =>
      string(item, "action").toLowerCase match {
        case "reach" => reachRps(int(item, "rps", 1)).in(int(item, "seconds", 1).seconds)
        case "hold"  => holdFor(int(item, "seconds", 1).seconds)
        case other   => throw new IllegalArgumentException(s"Throttle action no soportada: $other")
      }
    }.toSeq
  }

  private val tarjetasCfg = scenarioSettings("tarjetas")
  private val saldosCfg = scenarioSettings("saldos")
  private val deudaCfg = scenarioSettings("deuda")

  setUp(
    tarjetas.inject(rampUsers(int(tarjetasCfg, "users", 1)).during(int(tarjetasCfg, "rampSeconds", 1).seconds)),
    saldos.inject(rampUsers(int(saldosCfg, "users", 1)).during(int(saldosCfg, "rampSeconds", 1).seconds)),
    deuda.inject(rampUsers(int(deudaCfg, "users", 1)).during(int(deudaCfg, "rampSeconds", 1).seconds))
  ).protocols(httpProtocol)
    .maxDuration(int(root, "maxDurationSeconds", 840).seconds)
    .throttle(throttleSteps)
    .assertions(
      global.failedRequests.percent.lt(double(assertionsConfig, "maxFailedPercentage", 1.0)),
      global.responseTime.percentile(int(assertionsConfig, "percentile", 95))
        .lt(int(assertionsConfig, "maxResponseTimeMilliseconds", 3000))
    )
}
