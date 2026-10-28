package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, parser}
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit
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
  * @version Oct. 28, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
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
  private def _initialize(root: Path): Unit = {
    val storage = ThreeWorkflowNormalHostFixture.storagePlan.mapObject(fields => fields.add("records",
      Json.fromValues(fields("records").get.asArray.get :+ Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson,
        "storeName" -> "workflow".asJson, "collection" -> _collection.asJson))))
    val publication = Json.obj("storeName" -> "workflow".asJson, "collection" -> _collection.asJson,
      "catalogOutputs" -> Json.arr(Json.obj("targetIdentity" -> "fixture-producer".asJson,
        "path" -> _catalog.asJson, "selection" -> "publication-catalog".asJson)),
      "targets" -> Json.arr(Json.obj("identity" -> "fixture-producer".asJson,
        "component" -> "org.example.PublicationFixture".asJson, "workspace" -> TypedGitHostPlanFixture.workspace.asJson)))
    _with_runtime_(root, "Goal", hostParameters = Map("textus.subsystem.storage.plan" -> storage.noSpaces,
      "sm-workflow.local-publication" -> publication.noSpaces), componentInstance = "default") { (_, _, _, setup) =>
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
  }
  private def _call(root: Path, operation: String, id: String, installed: Boolean): Json = {
    val logs = Files.createDirectories(root.resolve("publication-cli"))
    val ordinal = Files.list(logs)
    val n = try ordinal.count() finally ordinal.close()
    val stdout = logs.resolve(n + ".stdout.json")
    val stderr = logs.resolve(n + ".stderr.log")
    val arguments = Vector("command", SmWorkflowComponent.componentId.name + ".LocalPublicationService." + operation,
      "--input", Json.obj("requestIdentity" -> id.asJson, "targetIdentity" -> "fixture-producer".asJson,
        "explicit" -> false.asJson).noSpaces, "--json")
    val argv = if (installed) Vector(sys.props.getOrElse("smworkflow.spec.installedCommand",
      throw new IllegalArgumentException("Select the installed executable")), "--runtime-root", root.toString) ++ arguments
    else Vector(_primary.resolve("bin/launcher").toString, "--classpath-file", _primary.resolve("target/cncf.d/runtime-classpath.txt").toString,
      "--main-class", "org.goldenport.cncf.CncfMain", "--", "--force-exit", "--component-dev-dir", _primary.toString,
      "--textus.assembly.descriptor=" + root.resolve(".textus/assembly.yaml")) ++ arguments
    val builder = new ProcessBuilder(argv.asJava).directory(root.toFile).redirectOutput(stdout.toFile).redirectError(stderr.toFile)
    if (installed) builder.environment().put("SM_WORKFLOW_INSTALLATIONS", sys.props.getOrElse("smworkflow.spec.runtimeRoot",
      throw new IllegalArgumentException("Select the installed runtime root")))
    val process = builder.start()
    try {
      process.getOutputStream.close()
      withClue(Files.readString(stderr)) { process.waitFor(300L, TimeUnit.SECONDS) shouldBe true }
      withClue(Files.readString(stderr) + Files.readString(stdout)) { process.exitValue() shouldBe 0 }
      parser.parse(Files.readString(stdout)).toOption.get
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
    _initialize(root)
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
  }
}
