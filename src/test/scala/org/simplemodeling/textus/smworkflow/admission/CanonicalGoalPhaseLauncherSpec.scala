package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.TimeUnit
import java.net.{InetAddress, ServerSocket, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration
import io.circe.Json
import org.simplemodeling.textus.smworkflow.value.{GoalPhaseStartCommand, GoalPhaseStartInput}
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Real normal-user CLI processes, not fixture-admitted embedding hosts.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class CanonicalGoalPhaseLauncherSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val project = Paths.get("").toAbsolutePath.normalize
  private val launcher = project.resolve("bin/launcher")
  private val runtimeClasspath = project.resolve("target/cncf.d/runtime-classpath.txt")
  private val storageKey = "textus.subsystem.storage.plan"
  private val owner = SmWorkflowComponent.componentId.name
  private val operations = Set("startGoalPhase", "advanceGoalPhase", "submitGoalPhaseWorkResult", "submitGoalPhaseOrderedDecision")
  private def collection(name: String): String = EntityCollectionId("smworkflow", "cli", name).print
  private val plan = ThreeWorkflowNormalHostFixture.storagePlan.noSpaces

  private final case class Host(cwd: Path, assembly: Path, database: Path)
  private def host(filePlan: Boolean, serverPort: Option[Int] = None): Host = {
    val cwd = Files.createTempDirectory("smwf-canonical-cli-")
    val database = cwd.resolve("runtime.sqlite")
    val values = Map(
      "textus.datastore.kind" -> "sqlite", "textus.datastore.path" -> database.toString) ++
      Map("store-name" -> "workflow", "store-identity" -> "normal-cli-store",
        "migration-policy" -> "v1", "retention-policy" -> "v1", "lease-policy" -> "v1",
        "instance-collection" -> collection("instances"), "continuation-collection" -> collection("continuations"))
        .map { case (key, value) => ("sm-workflow.goal-phase.persistence." + key) -> value } ++
      Map("review-risk" -> "standard", "review-reasoning" -> "DEEP", "repair-risk" -> "standard",
        "repair-reasoning" -> "STANDARD", "maximum-repairs" -> "2", "maximum-artifact-bytes" -> "4096")
        .map { case (key, value) => ("sm-workflow.goal-phase.execution." + key) -> value } ++
      Map("store-name" -> "workflow", "selection-collection" -> collection("selections"),
        "association-collection" -> collection("submissions"), "authorization-collection" -> collection("authorizations"),
        "decision-collection" -> collection("decisions"))
        .map { case (key, value) => ("sm-workflow.goal-phase.host-records." + key) -> value } ++
      Map("store-name" -> "workflow", "reference-collection" -> collection("artifacts"))
        .map { case (key, value) => ("sm-workflow.goal-phase.artifacts." + key) -> value } ++
      (if (filePlan) Map(storageKey -> plan) else Map.empty[String, String]) ++
      ThreeWorkflowNormalHostFixture.values(cwd) ++
      serverPort.map(port => Map("textus.server.host" -> "127.0.0.1",
        "textus.server.port" -> port.toString,
        "textus.server.port-assignment-file" -> cwd.resolve("ports.json").toString)).getOrElse(Map.empty)
    val directory = Files.createDirectories(cwd.resolve(".textus"))
    Files.writeString(directory.resolve("config.json"),
      Json.obj(values.toVector.map { case (key, value) => key -> Json.fromString(value) }*).noSpaces)
    val assembly = cwd.resolve("assembly.yaml")
    Files.writeString(assembly,
      """subsystem: sm-workflow-cli
        |components:
        |  - namespace: org.simplemodeling.textus
        |    id: SmWorkflow
        |    version: 0.1.0-SNAPSHOT
        |security:
        |  authentication:
        |    local_subject:
        |      id: sm-workflow-cli-user
        |config:
        |  textus.subsystem.user-mode: standalone
        |""".stripMargin)
    Host(cwd, assembly, database)
  }

  private final case class Observation(exit: Int, output: String)
  private def run(host: Host, extra: Vector[String] = Vector.empty,
    application: Vector[String] = Vector.empty): Observation = {
    Vector(runtimeClasspath, project.resolve("target/cncf.d/component-descriptor.json"),
      project.resolve("target/cncf.d/car-runtime-manifest.json")).foreach { path =>
      withClue(s"Official cozyPrepareRuntime evidence required: $path")(Files.isRegularFile(path) shouldBe true)
    }
    val output = Files.createTempFile(host.cwd, "cli-", ".log")
    val command = Vector(launcher.toString, "--classpath-file", runtimeClasspath.toString,
      "--main-class", "org.goldenport.cncf.CncfMain", "--",
      "--component-dev-dir", project.toString, s"--textus.assembly.descriptor=${host.assembly}") ++
      extra ++ (if (application.isEmpty) Vector("command", "meta.help", s"$owner.GoalPhaseService") else application)
    val process = new ProcessBuilder(command*).directory(host.cwd.toFile)
      .redirectErrorStream(true).redirectOutput(output.toFile).start()
    try {
      if (!process.waitFor(30, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        process.waitFor(10, TimeUnit.SECONDS)
        fail(s"Owned CLI timed out; output retained at $output")
      }
      Files.size(output) should be <= 131072L
      Observation(process.exitValue(), Files.readString(output))
    } finally {
      if (process.isAlive) {
        process.destroyForcibly()
        process.waitFor(10, TimeUnit.SECONDS)
      }
    }
  }

  "Canonical normal-user GoalPhase launcher" should {
    "install all three native services through the default factory without fixture host enrollment" in {
      Given("normal host-file configuration, one managed SQLite store and the official development CAR")
      val configured = host(filePlan = true)
      When("three cold CLI processes ask for their respective installed Service Help")
      val observed = Vector("GoalPhaseService", "SplitPhaseService", "RepositorySyncService").map { service =>
        service -> run(configured, application = Vector("command", "meta.help", s"$owner.$service"))
      }
      Then("each publishes exactly its generated native operations on the same normal connection")
      observed.foreach { (service, observation) =>
        withClue(observation.output) {
          observation.exit shouldBe 0
          observation.output should include(s"name: $service")
          val expected = ThreeWorkflowLauncherAssembly.publishedMcpOperations.filter(_.startsWith(service + "."))
            .map(_.stripPrefix(service + "."))
          observation.output.linesIterator.toVector.dropWhile(_ != "children:").drop(1)
            .takeWhile(_.startsWith("- ")).map(_.drop(2)).toSet shouldBe expected
        }
      }
      Files.isRegularFile(configured.database) shouldBe true
    }

    "publish real MCP tools through an owned normal server and preserve the CLI Start refusal" in {
      Given("the official development CAR and a normal host with no human selection")
      val reservation = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
      val port = try reservation.getLocalPort finally reservation.close()
      val configured = host(filePlan = true, serverPort = Some(port))
      val baseline = run(configured)
      withClue(baseline.output)(baseline.output should include("type: service"))
      val before = Files.readAllBytes(configured.database).toVector
      val output = Files.createTempFile(configured.cwd, "server-", ".log")
      val command = Vector(project.resolve("scripts/run-server.sh").toString,
        s"--textus.assembly.descriptor=${configured.assembly}")
      val process = new ProcessBuilder(command*).directory(configured.cwd.toFile)
        .redirectErrorStream(true).redirectOutput(output.toFile).start()
      val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
      def rpc(id: Int, method: String, params: Json): Json = {
        val body = Json.obj("jsonrpc" -> Json.fromString("2.0"), "id" -> Json.fromInt(id),
          "method" -> Json.fromString(method), "params" -> params).noSpaces
        val request = HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$port/mcp"))
          .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json")
          .header("MCP-Protocol-Version", "2025-11-25")
          .POST(HttpRequest.BodyPublishers.ofString(body)).build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        response.statusCode shouldBe 200
        response.body.length should be <= 131072
        io.circe.parser.parse(response.body).fold(error => fail(error.message), identity)
      }
      try {
        When("the normal launcher starts the canonical loopback server")
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        val ready = s"HTTP server started at 127.0.0.1:$port."
        while (process.isAlive && System.nanoTime() < deadline &&
          !Files.readString(output).contains(ready)) Thread.sleep(50)
        withClue(Files.readString(output)) {
          process.isAlive shouldBe true
          Files.readString(output) should include(ready)
        }
        val initialized = rpc(1, "initialize", Json.obj("protocolVersion" -> Json.fromString("2025-11-25")))
        val listed = rpc(2, "tools/list", Json.obj())
        Then("the complete network MCP catalog is the standard six tools plus all fifteen native workflow operations")
        initialized.hcursor.downField("result").get[String]("protocolVersion").toOption shouldBe Some("2025-11-25")
        val tools = listed.hcursor.downField("result").get[Vector[Json]]("tools")
          .fold(error => fail(error.message), identity)
        val managedNames = ThreeWorkflowLauncherAssembly.publishedMcpOperations.map(operation => s"$owner.$operation")
        val builtinNames = Set("decimal.calculate", "web.head", "resource.read", "web.search", "web.fetch", "time.now")
          .map(operation => s"org.goldenport.cncf.Tool.$operation")
        tools.map(_.hcursor.get[String]("name").toOption.get).toSet shouldBe managedNames ++ builtinNames
        tools.size shouldBe 21
        managedNames.foreach { name =>
          val tool = tools.find(_.hcursor.get[String]("name").toOption.contains(name)).get
          tool.hcursor.downField("inputSchema").downField("properties").downField("arg1")
            .get[String]("type").toOption shouldBe Some("string")
        }
        When("MCP and a separate CLI process submit identical Start while the server is live")
        val wire = GoalPhaseStartServiceOperation.encodeCommand(GoalPhaseStartCommand(
          GoalPhaseStartInput("phase-2", "sm-workflow"), "unrecorded-normal-cli-selection",
          "sm-workflow-cli-user", "normal-cli-unrecorded-start"))
        val refused = rpc(3, "tools/call", Json.obj("name" -> Json.fromString(s"$owner.GoalPhaseService.startGoalPhase"),
          "arguments" -> Json.obj("arg1" -> Json.fromString(wire))))
        val cli = run(configured, application = Vector("command", s"$owner.GoalPhaseService.startGoalPhase", wire))
        Then("neither transport manufactures consent or changes managed SQLite")
        refused.noSpaces should include("actual selected Start is absent")
        cli.output should include("actual selected Start is absent")
        Files.readAllBytes(configured.database).toVector shouldBe before

        When("the four native GoalPhase Operations receive the same malformed command through MCP and ordinary CLI")
        val decoderRefusals = Map(
          "startGoalPhase" -> "GoalPhase Start JSON has an incompatible shape",
          "advanceGoalPhase" -> "advance cannot select a stage, cursor, command or receipt",
          "submitGoalPhaseWorkResult" -> "Workflow result JSON fields are missing or unknown",
          "submitGoalPhaseOrderedDecision" -> "Workflow result JSON fields are missing or unknown")
        decoderRefusals.keySet shouldBe operations
        val outcomes = decoderRefusals.toVector.sortBy(_._1).zipWithIndex.map {
          case ((operation, expected), index) =>
            val name = s"$owner.GoalPhaseService.$operation"
            val mcp = rpc(10 + index, "tools/call", Json.obj("name" -> Json.fromString(name),
              "arguments" -> Json.obj("arg1" -> Json.fromString("{}"))))
            val cli = run(configured, application = Vector("command", name, "{}"))
            (operation, expected, mcp, cli)
        }
        Then("each transport reaches the respective closed decoder without accepting a result or changing managed state")
        outcomes.foreach { case (operation, expected, mcp, cli) =>
          withClue(s"$operation MCP: ${mcp.noSpaces}; CLI: ${cli.output}") {
            mcp.hcursor.downField("result").get[Boolean]("isError").toOption shouldBe Some(true)
            mcp.noSpaces should include(expected)
            cli.output should include(expected)
          }
        }
        Files.readAllBytes(configured.database).toVector shouldBe before
      } finally {
        process.destroy()
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
          process.destroyForcibly()
          process.waitFor(10, TimeUnit.SECONDS)
        }
        withClue(s"Owned server was not reaped; log: $output")(process.isAlive shouldBe false)
      }
    }

    "expose exactly the managed operations from two cold ordinary CLI processes" in {
      Given("the actual development CAR, host file grants and normal standalone user assembly")
      val configured = host(filePlan = true)
      When("two separate canonical CLI processes initialize and terminate against the same host")
      val observations = Vector(run(configured), run(configured))
      Then("both expose the real managed service without controlled admission or placeholders")
      observations.foreach { observation =>
        withClue(observation.output) {
          observation.exit shouldBe 0
          observation.output should include("type: service")
          observation.output should include("name: GoalPhaseService")
          observation.output should include("Own the selected Phase's native entry")
          observation.output should not include "summary: Service: GoalPhaseService"
          observation.output should include(s"component_id: Some($owner)")
          observation.output should not include "Exception"
          observation.output should not include "ERROR"
          val lines = observation.output.linesIterator.toVector
          lines.dropWhile(_ != "children:").drop(1).takeWhile(_.startsWith("- "))
            .map(_.drop(2)).toSet shouldBe operations
          lines.dropWhile(_ != "  operations:").drop(1).takeWhile(_.startsWith("  - "))
            .map(_.drop(4)).toSet shouldBe operations
        }
      }
      Files.isRegularFile(configured.database) shouldBe true
    }

    "refuse Start without human selection and reject caller-asserted human origin without modifying SQLite" in {
      Given("a normal-user managed CLI host with storage grants but no recorded human selection")
      val configured = host(filePlan = true)
      val help = run(configured)
      withClue(help.output) {
        help.exit shouldBe 0
        help.output should include("type: service")
      }
      val before = Files.readAllBytes(configured.database).toVector
      val command = GoalPhaseStartCommand(GoalPhaseStartInput("phase-2", "sm-workflow"),
        "unrecorded-normal-cli-selection", "sm-workflow-cli-user", "normal-cli-unrecorded-start")
      val wire = GoalPhaseStartServiceOperation.encodeCommand(command)
      def start(value: String) = run(configured, application = Vector("command",
        s"$owner.GoalPhaseService.startGoalPhase", value))
      When("two fresh CLI processes dispatch the exact typed Start without a host selection")
      val attempts = Vector(start(wire), start(wire))
      Then("normal user and storage authority do not substitute human consent or create Workflow history")
      attempts.foreach { observation =>
        withClue(observation.output) {
          observation.output should include("actual selected Start is absent")
          observation.output should not include "Goal Phase started"
        }
      }
      Files.readAllBytes(configured.database).toVector shouldBe before

      When("a caller adds an ExplicitHuman origin claim to the public command")
      val forged = io.circe.parser.parse(wire).toOption.get
        .mapObject(_.add("origin", Json.fromString("ExplicitHuman"))).noSpaces
      val refused = start(forged)
      Then("the closed command rejects the claim without accepting or writing consent")
      withClue(refused.output) {
        refused.output should include("GoalPhase Start JSON has an incompatible shape")
        refused.output should not include "Goal Phase started"
      }
      Files.readAllBytes(configured.database).toVector shouldBe before
    }

    "reject a valid argument plan before creating managed SQLite or exposing the service" in {
      Given("normal-user configuration without a host file storage plan")
      val configured = host(filePlan = false)
      When("the identical valid plan is supplied only as a direct CLI argument")
      val observation = run(configured, Vector(s"--$storageKey=$plan"))
      Then("the source authority refusal is explicit even if the CLI returns zero")
      withClue(observation.output) {
        observation.output should include("Host storage requires a selected Subsystem file binding")
        observation.output should not include "type: service"
        Files.exists(configured.database) shouldBe false
      }
    }
  }
}
