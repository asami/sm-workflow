package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, parser}
import io.circe.syntax.*
import io.circe.generic.auto.*
import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import scala.jdk.CollectionConverters.*
import org.goldenport.cncf.processexecution.{GitOperationKind, SbtOperation}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import SplitPhaseExplicitPartitionFixture.take

/** A fresh normal host, actual ordinary SBT publication and separate CLI
  * processes. No real development Session is read or advanced.
  * @since   Oct. 26, 2026
  *  version Oct. 28, 2026
  * @version Nov. 18, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.PackagedRuntimeTestDeclaration])
final class NativeLocalPublicationCliSpec extends AnyWordSpec with GivenWhenThen with ThreeWorkflowManagedRepositoryFixture {
  private val _primary = Path.of("").toAbsolutePath.normalize
  private val _collection = ThreeWorkflowNormalHostFixture.collection("local_publication")
  private val _catalog = "src/main/catalog/car/org/example/publication-fixture.yaml"
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val git = TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString))
    val sbt = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(1),
      operation = SbtOperation.PublishLocal, executionTimeoutMillis = 240000L, stdoutBytes = 1048576, stderrBytes = 131072)
      .hcursor.get[Vector[Json]]("sbtPrograms").toOption.get.head
      .mapObject(_.add("target", TypedGitHostPlanFixture.workspace.asJson)
        .add("identity", "fixture-publish-local".asJson).add("capability", "fixture-publish-local".asJson))
    val ordinary = git.hcursor.get[Vector[Json]]("gitPrograms").toOption.get
    val catalog = ordinary.find(_.hcursor.get[String]("operation").toOption.contains(GitOperationKind.IndexTree.wireName)).get
      .mapObject(_.add("operation", GitOperationKind.TreeEntries.wireName.asJson)
        .add("selectionId", "publication-catalog".asJson).add("selectedPath", Json.Null)
        .add("selectedPaths", Vector(_catalog).asJson).add("identity", "fixture-publication-catalog".asJson)
        .add("capability", "fixture-publication-catalog".asJson))
    val programs = ordinary :+ catalog
    val capabilities = programs.map(
      _.hcursor.get[Json]("capability").toOption.get) :+ "fixture-publish-local".asJson
    require(git.hcursor.get[Vector[Json]]("workspaceGrants").toOption.get.exists(g =>
      g.hcursor.get[String]("workspaceId").toOption.contains(TypedGitHostPlanFixture.workspace) &&
        g.hcursor.get[String]("access").toOption.contains("manage")), "The ordinary fixture must admit File reads")
    git.mapObject(_.add("gitPrograms", programs.asJson).add("sbtPrograms", Json.arr(sbt)).add("grants", Json.arr(Json.obj(
      "componentId" -> SmWorkflowComponent.componentId.name.asJson, "capabilities" -> Json.fromValues(capabilities)))))
  }
  private def _initialize(root: Path, installed: Boolean, seedUnknown: Boolean = false): Unit = {
    val storage = ThreeWorkflowNormalHostFixture.storagePlan.mapObject(fields => fields.add("records",
      Json.fromValues(fields("records").get.asArray.get :+ Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson,
        "storeName" -> "workflow".asJson, "collection" -> _collection.asJson))))
    val publication = Json.obj("storeName" -> "workflow".asJson, "collection" -> _collection.asJson,
      "catalogOutputs" -> Json.arr(Json.obj("targetIdentity" -> "fixture-producer".asJson,
        "path" -> _catalog.asJson, "selection" -> "publication-catalog".asJson)),
      "targets" -> Json.arr(Json.obj("identity" -> "fixture-producer".asJson,
        "component" -> "org.example.PublicationFixture".asJson, "workspace" -> TypedGitHostPlanFixture.workspace.asJson)))
    val parameters = Map("textus.subsystem.storage.plan" -> storage.noSpaces, "sm-workflow.local-publication" -> publication.noSpaces)
    _with_runtime_(root, "Goal", hostParameters = parameters, componentInstance = "default") { (_, _, _, setup) =>
      setup(_call => _call.setupC(_setup_commands_(root, "Goal").size, scenario = "Goal"))
    }
    val build = root.resolve("repository/build.sbt")
    Files.writeString(build, """scalaVersion := "2.12.18"
      |organization := "org.example.sm-workflow-publication-fixture"
      |name := "local-publication-fixture"
      |version := "0.0.1-SNAPSHOT"
      |publishLocal := {
      |  val result = publishLocal.value
      |  val catalog = file("src/main/catalog/car/org/example/publication-fixture.yaml")
      |  IO.write(catalog, IO.read(catalog).replaceAll("(?m)^    publishedAt: [^\\r\\n]+", "    publishedAt: " + java.time.OffsetDateTime.now().toString))
      |  IO.append(file("../publication-count.txt"), "published\n")
      |  result
      |}
      |""".stripMargin)
    val catalog = root.resolve("repository").resolve(_catalog)
    Files.createDirectories(catalog.getParent)
    Files.writeString(catalog, "schemaVersion: 2\nkind: car\nversions:\n  - version: 0.0.1-SNAPSHOT\n" +
      "    component: org.example.PublicationFixture\n    publishedAt: 2026-10-29T00:00:00+09:00\n" +
      "    file: repository/car/fixture.car\n")
    take(_run_setup_command_(Vector("-C", root.resolve("repository").toString, "add", "--", "build.sbt", _catalog)))
    take(_run_setup_command_(Vector("-C", root.resolve("repository").toString, "-c", "user.name=Fixture",
      "-c", "user.email=fixture@example.invalid", "commit", "-m", "configure ordinary local publication")))
    if (seedUnknown) _with_runtime_(root, "Goal", hostParameters = parameters, componentInstance = "default") { (_, _, _, setup) =>
      setup { call =>
        val target = LocalPublication.Target("fixture-producer", "org.example.PublicationFixture", TypedGitHostPlanFixture.workspace)
        val policy = LocalPublicationRuntime.Policy("workflow", _collection_("local_publication"), Vector(target))
        val port = LocalPublicationRuntime._port(call.core, policy)
        val input = LocalPublication.Input("fixture-unknown", target.identity, explicit = true)
        val key = "local-publication:participant:fixture-unknown"
        for {
          source <- port.sourceC(target)
          request = LocalPublication.Request(input, target, source, "participant", "SPECIFICATION-OWNED interrupted intent", None, Vector.empty)
          _ <- port.createC(key + ":request", request.asJson)
          _ <- port.createC("local-publication-target:fixture-producer:1", Json.obj("key" -> key.asJson, "request" -> request.asJson))
          _ <- port.createC(key + ":intent", request.asJson)
        } yield ()
      }
    }
    if (seedUnknown) {
      val configfile = root.resolve(".textus/config.json")
      val configuration = parser.parse(Files.readString(configfile)).toOption.get
      Files.writeString(configfile, configuration.mapObject(_.remove("sm-workflow.runtime.definition")
        .add("sm-workflow.goal-phase.persistence.instance-collection", ThreeWorkflowNormalHostFixture.collection("instances").asJson)
        .add("sm-workflow.goal-phase.persistence.continuation-collection", ThreeWorkflowNormalHostFixture.collection("continuations").asJson)).noSpaces)
    }
    if (installed) {
      val component = sys.props.get("smworkflow.spec.componentFile").orElse(sys.env.get("SM_WORKFLOW_SPEC_COMPONENT_FILE"))
        .getOrElse(fail("Select the candidate component CAR"))
      val selected = Path.of(component).toAbsolutePath.normalize
      require(Files.isRegularFile(selected), "The selected candidate component CAR must exist")
      val config = root.resolve(".textus/config.json")
      val json = parser.parse(Files.readString(config)).toOption.get
      Files.writeString(config, json.mapObject(_.add("textus.component.file", selected.toString.asJson)).noSpaces)
    }
  }
  private def _call(root: Path, operation: String, id: String, installed: Boolean,
    recovery: Boolean = false, rejected: Boolean = false, supplied: Option[Json] = None,
    service: String = "LocalPublicationService", extra: Vector[String] = Vector.empty,
    candidate: Boolean = false): Json = {
    val logs = Files.createDirectories(root.resolve("publication-cli"))
    val ordinal = Files.list(logs)
    val n = try ordinal.count() finally ordinal.close()
    val stdout = logs.resolve(n + ".stdout.json")
    val stderr = logs.resolve(n + ".stderr.log")
    val input = Json.obj("requestIdentity" -> id.asJson, "targetIdentity" -> "fixture-producer".asJson,
      "explicit" -> false.asJson)
    val arguments = Vector("command", SmWorkflowComponent.componentId.name + "." + service + "." + operation,
      "--input", supplied.getOrElse(if (recovery) input.mapObject(_.add("successorIdentity", "fixture-successor".asJson)) else input).noSpaces,
      "--json") ++ extra
    val argv = if (candidate) {
      val executable = sys.props.get("smworkflow.spec.cncfCommand").orElse(sys.env.get("SM_WORKFLOW_SPEC_CNCF_COMMAND"))
        .map(Path.of(_)).getOrElse(sys.env.getOrElse("PATH", "").split(java.io.File.pathSeparator).iterator
          .filter(_.nonEmpty).map(x => Path.of(x).toAbsolutePath.normalize.resolve("cncf"))
          .find(x => Files.isRegularFile(x) && Files.isExecutable(x)).get)
      val car = Path.of(sys.props.get("smworkflow.spec.componentFile").orElse(sys.env.get("SM_WORKFLOW_SPEC_COMPONENT_FILE"))
        .getOrElse(throw new IllegalArgumentException("Select the candidate component CAR"))).toAbsolutePath.normalize
      val archive = new ZipFile(car.toFile)
      val minimum = try {
        val stream = archive.getInputStream(archive.getEntry("car-runtime-manifest.json"))
        val content = try new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8) finally stream.close()
        parser.parse(content).toOption.get.hcursor.downField("runtime").downField("cncf").get[String]("minimum").toOption.get
      } finally archive.close()
      val runtime = sys.props.get("smworkflow.spec.cncfRuntime").orElse(sys.env.get("SM_WORKFLOW_SPEC_CNCF_RUNTIME"))
        .orElse(Some(minimum))
      NativeWorkflowCliFixture.PackagedInstalled(executable, car, runtime)
      val runtimearguments = sys.props.get("smworkflow.spec.cncfRuntimeBundle")
        .map(bundle => Vector("--runtime-bundle-dir", Path.of(bundle).toAbsolutePath.normalize.toString))
        .getOrElse(NativeWorkflowCliFixture.runtimeArguments(runtime))
      Vector(executable.toString) ++ runtimearguments ++
        Vector(car.toString, "command", "--force-exit", "--no-default-components",
          "--textus.assembly.descriptor=" + root.resolve(".textus/assembly.yaml")) ++ arguments.tail
    } else if (installed) Vector(sys.props.get("smworkflow.spec.installedCommand")
      .orElse(sys.env.get("SM_WORKFLOW_SPEC_INSTALLED_COMMAND"))
      .getOrElse(throw new IllegalArgumentException("Select the installed executable"))) ++ arguments.tail
    else Vector(_primary.resolve("bin/launcher").toString, "--classpath-file", _primary.resolve("target/cncf.d/runtime-classpath.txt").toString,
      "--main-class", "org.goldenport.cncf.CncfMain", "--", "--force-exit", "--component-dev-dir", _primary.toString,
      "--textus.assembly.descriptor=" + root.resolve(".textus/assembly.yaml")) ++ arguments
    val builder = new ProcessBuilder(argv.asJava).directory(root.toFile).redirectOutput(stdout.toFile).redirectError(stderr.toFile)
    val process = builder.start()
    try {
      process.getOutputStream.close()
      withClue(Files.readString(stderr)) { process.waitFor(300L, TimeUnit.SECONDS) shouldBe true }
      if (rejected) {
        withClue(Files.readString(stderr) + Files.readString(stdout)) { process.exitValue() should not be 0 }
        Json.Null
      } else {
        withClue(Files.readString(stderr) + Files.readString(stdout)) { process.exitValue() shouldBe 0 }
        parser.parse(Files.readString(stdout)).toOption.get
      }
    } finally {
      if (process.isAlive) {
        val descendants = process.toHandle.descendants()
        val owned = try descendants.iterator().asScala.toVector finally descendants.close()
        process.destroy()
        owned.filter(_.isAlive).foreach(_.destroy())
        if (!process.waitFor(10L, TimeUnit.SECONDS)) process.destroyForcibly()
        owned.filter(_.isAlive).foreach(_.destroyForcibly())
        process.waitFor()
      }
      process.getInputStream.close(); process.getErrorStream.close()
    }
  }
  private def _qualify(installed: Boolean): Unit = {
    val root = Files.createTempDirectory(Files.createDirectories(_primary.resolve("target/local-publication-cli")), "case-").toRealPath()
    _initialize(root, installed)
    def _status_(op: String, id: String) = _call(root, op, id, installed).hcursor.get[String]("status").toOption.get
    When("ordinary CLI ensure publishes once, a new process collects it, and a new prerequisite observes the same source")
    val first = _status_("ensureLocalPublication", "first")
    val collected = _status_("collectLocalPublication", "first")
    val repeated = _status_("ensureLocalPublication", "first")
    val samesource = _status_("ensureLocalPublication", "same-source")
    Then("only the runtime-owned actual SBT invocation published and no Session was started")
    first shouldBe "published"
    collected shouldBe "published"
    repeated shouldBe "published"
    samesource shouldBe "unnecessary"
    Files.readString(root.resolve("publication-count.txt")).linesIterator.toVector shouldBe Vector("published")
    When("standalone recovery is requested for a completed publication without a Human direction")
    _call(root, "describeLocalPublicationRecovery", "first", installed, recovery = true, rejected = true)
    _call(root, "advanceLocalPublicationRecovery", "first", installed, recovery = true, rejected = true)
    Then("the public operations refuse the ineligible request without another publication")
    Files.readString(root.resolve("publication-count.txt")).linesIterator.toVector shouldBe Vector("published")
    val catalog = root.resolve("repository").resolve(_catalog)
    Files.readString(catalog) should not include "2026-10-29T00:00:00+09:00"
    Given("the retained successful publication with its actual timestamp output")
    When("actual catalog metadata changes after the timestamp-only publication output")
    Files.writeString(catalog, Files.readString(catalog).replace("repository/car/fixture.car", "repository/car/changed.car"))
    val changedcatalog = _status_("ensureLocalPublication", "changed-catalog")
    Then("the meaningful catalog input requires a second ordinary publication")
    changedcatalog shouldBe "published"
    Files.readString(root.resolve("publication-count.txt")).linesIterator.toVector shouldBe Vector("published", "published")
    Given("the actual second publication and its changed catalog")
    When("the changed catalog is staged and another source input is dirty")
    take(_run_setup_command_(Vector("-C", root.resolve("repository").toString, "add", "--", _catalog)))
    Files.writeString(root.resolve("repository/build.sbt"), "\n// A genuine changed build input\n", java.nio.file.StandardOpenOption.APPEND)
    val stagedsource = _status_("ensureLocalPublication", "staged-catalog-and-source")
    Then("projection preserves staged catalog and other source changes")
    stagedsource shouldBe "published"
    Files.readString(root.resolve("publication-count.txt")).linesIterator.toVector shouldBe Vector.fill(3)("published")
    _status_("collectLocalPublication", "absent") shouldBe "not-found"
  }
  "Runtime-owned local publication" should {
    "publish and recollect through the ordinary source host" in {
      Given("a new test-owned host and plain build without sbt-cozy")
      _qualify(false)
    }
    "publish and skip through the isolated installed CLI" in {
      Given("the explicitly selected installed runtime and a new isolated plain SBT producer")
      _qualify(true)
    }
    "recover a specification-owned standalone UNKNOWN through the installed CLI standard runtime" in {
      Given("a fresh test-owned standard host without a selected Phase or Plan, seeded with an intent and no terminal result")
      val root = Files.createTempDirectory(Files.createDirectories(_primary.resolve("target/local-publication-cli")), "unknown-").toRealPath()
      _initialize(root, installed = true, seedUnknown = true)
      val original = Json.obj("requestIdentity" -> "fixture-unknown".asJson,
        "targetIdentity" -> "fixture-producer".asJson, "explicit" -> true.asJson)
      val recovery = original.mapObject(_.add("successorIdentity", "fixture-successor".asJson))
      def call(operation: String, value: Json) = _call(root, operation, "", true, supplied = Some(value), candidate = true)
      def status(value: Json) = value.hcursor.get[String]("status").toOption.get
      def save(name: String, value: Json): String = {
        val path = root.resolve(name + ".json")
        Files.writeString(path, value.noSpaces)
        path.toUri.toASCIIString
      }
      When("ordinary ensure and describe observe the uncertain intent")
      status(call("ensureLocalPublication", original)) shouldBe "uncertain"
      val description = call("describeLocalPublicationRecovery", recovery)
      status(description) shouldBe "direction-required"
      Files.exists(root.resolve("publication-count.txt")) shouldBe false
      val reference = Json.obj("identity" -> "SPECIFICATION-OWNED-human".asJson, "revision" -> "1".asJson)
      val answer = Json.obj("target" -> description.hcursor.get[Json]("publication").toOption.get,
        "choice" -> LocalPublicationRecovery.choice.asJson, "rationale" -> "SPECIFICATION-OWNED accept unknown effects".asJson,
        "participant" -> "participant".asJson, "reference" -> reference)
      val execution = Json.obj("references" -> Json.arr(reference), "workerIdentity" -> "participant".asJson)
      val preparation = Json.obj("family" -> "LocalPublication".asJson, "kind" -> "standalone-recovery".asJson,
        "request" -> save("description", description).asJson, "result" -> save("answer", answer).asJson,
        "reference" -> save("reference", reference).asJson, "executionEvidence" -> save("execution", execution).asJson,
        "issuer" -> "normal-development-host".asJson, "occurredAt" -> java.time.Instant.now().minusSeconds(60).toString.asJson,
        "collection" -> "entity:smworkflow.cli.host_evidence".asJson, "participant" -> "participant".asJson)
      val prepared = _call(root, "prepareEvidence", "", true, supplied = Some(preparation),
        service = "NativeWorkflowPreparationService", extra = Vector("--preparationFormat", "legacy-full"), candidate = true)
      val imported = root.resolve("independent-host-input.json")
      Files.writeString(imported, prepared.noSpaces)
      val configfile = root.resolve(".textus/config.json")
      val config = parser.parse(Files.readString(configfile)).toOption.get
      Files.writeString(configfile, config.mapObject(_.add("textus.import.data.file", imported.toString.asJson)).noSpaces)
      When("the independently provisioned Human answer is admitted")
      status(call("admitLocalPublicationRecovery", recovery.mapObject(_.add("answer", answer)))) shouldBe "admitted"
      Files.exists(root.resolve("publication-count.txt")) shouldBe false
      When("the admitted successor advances and is recollected")
      val published = call("advanceLocalPublicationRecovery", recovery)
      status(published) shouldBe "published"
      call("advanceLocalPublicationRecovery", recovery) shouldBe published
      status(call("collectLocalPublication", original)) shouldBe "uncertain"
      val successor = Json.obj("requestIdentity" -> "fixture-successor".asJson,
        "targetIdentity" -> "fixture-producer".asJson, "explicit" -> false.asJson)
      status(call("collectLocalPublication", successor)) shouldBe "published"
      Then("one actual typed publication ran and the original UNKNOWN was retained")
      Files.readString(root.resolve("publication-count.txt")).linesIterator.toVector shouldBe Vector("published")
    }
  }
}
