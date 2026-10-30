package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, parser}
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import SplitPhaseExplicitPartitionFixture.take

/** Real publication and native synchronization in a disposable repository.
  * @since   Oct. 30, 2026
  * @version Oct. 30, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class RepositorySyncPublicationCliSpec extends AnyWordSpec with GivenWhenThen
    with Phase69ConfiguredRepositoryFixture {
  private val catalog = "src/main/catalog/car/org/example/publication.yaml"
  private val collection = ThreeWorkflowNormalHostFixture.collection("local_publication")
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val git = super.process_plan(root, scenario, goal)
    val sbt = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(1),
      operation = org.goldenport.cncf.processexecution.SbtOperation.PublishLocal, executionTimeoutMillis = 240000L)
      .hcursor.get[Vector[Json]]("sbtPrograms").toOption.get.head.mapObject(_.add("target", "sm-workflow-repository".asJson))
    git.mapObject(_.add("sbtPrograms", Json.arr(sbt)).add("grants",
      git.hcursor.get[Vector[Json]]("grants").toOption.get.map(_.mapObject(g =>
        g.add("capabilities", Json.fromValues(g("capabilities").get.asArray.get :+
          sbt.hcursor.get[Json]("capability").toOption.get)))).asJson))
  }
  private def call(root: Path, operation: String, arguments: Vector[String]): Json = {
    val executable = sys.props.getOrElse("smworkflow.spec.installedCommand",
      throw new IllegalArgumentException("Select installed sm-workflow"))
    val logs = Files.createDirectories(root.resolve("cli"))
    val files = Files.list(logs)
    val n = try files.count() finally files.close()
    val out = logs.resolve(n + ".json"); val err = logs.resolve(n + ".stderr")
    val argv = Vector(executable, "--runtime-root", root.toString, "command",
      SmWorkflowComponent.componentId.name + ".RepositorySyncService." + operation) ++ arguments ++ Vector("--json")
    val builder = new ProcessBuilder(argv.asJava).directory(root.resolve("repository").toFile)
      .redirectOutput(out.toFile).redirectError(err.toFile)
    builder.environment().put("SM_WORKFLOW_INSTALLATIONS", sys.props.getOrElse("smworkflow.spec.runtimeRoot",
      throw new IllegalArgumentException("Select installed runtime root")))
    val process = builder.start()
    try {
      process.getOutputStream.close()
      withClue(Files.readString(err)) { process.waitFor(300L, TimeUnit.SECONDS) shouldBe true }
      withClue(Files.readString(out) + Files.readString(err)) { process.exitValue() shouldBe 0 }
      parser.parse(Files.readString(out)).toOption.get
    } finally {
      if (process.isAlive) {
        val stream = process.toHandle.descendants()
        val children = try stream.iterator().asScala.toVector finally stream.close()
        process.destroy(); children.filter(_.isAlive).foreach(_.destroy())
        if (!process.waitFor(5L, TimeUnit.SECONDS)) process.destroyForcibly()
        children.filter(_.isAlive).foreach(_.destroyForcibly()); process.waitFor()
      }
    }
  }
  "Installed RepositorySync final publication" should {
    "publish once, commit its metadata, push and complete with a clean equal tree" in {
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/repository-publication-cli").toAbsolutePath), "case-")
      val storage = ThreeWorkflowNormalHostFixture.storagePlan.mapObject(f => f.add("records",
        Json.fromValues(f("records").get.asArray.get :+ Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson,
          "storeName" -> "workflow".asJson, "collection" -> collection.asJson))))
      val policy = Json.obj("storeName" -> "workflow".asJson, "collection" -> collection.asJson,
        "targets" -> Json.arr(Json.obj("identity" -> "fixture-producer".asJson,
          "component" -> "org.example.Fixture".asJson, "workspace" -> "sm-workflow-repository".asJson,
          "beforeSync" -> true.asJson)))
      _with_runtime_(root, "Equal", hostParameters = Map("textus.subsystem.storage.plan" -> storage.noSpaces,
        "sm-workflow.local-publication" -> policy.noSpaces), componentInstance = "default") { (_, _, _, setup) =>
        setup(c => c.setupC(_setup_commands_(root, "Equal").size))
      }
      val repo = root.resolve("repository")
      Files.writeString(repo.resolve(".gitignore"), "target/\nproject/target/\nproject/project/\n.bsp/\n")
      Files.createDirectories(repo.resolve("project"))
      Files.writeString(repo.resolve("project/build.properties"), "sbt.version=1.9.7\n")
      Files.createDirectories(repo.resolve(catalog).getParent)
      Files.writeString(repo.resolve(catalog), "published: before\n")
      Files.writeString(repo.resolve("build.sbt"), """scalaVersion := "2.12.18"
        |publishLocal := {
        |  IO.write(file("src/main/catalog/car/org/example/publication.yaml"), "published: after\n")
        |  IO.append(file("../publication-count.txt"), "published\n")
        |}
        |""".stripMargin)
      take(_run_setup_command_(Vector("-C", repo.toString, "add", "--", ".gitignore", "project/build.properties", "build.sbt", catalog)))
      take(_run_setup_command_(Vector("-C", repo.toString, "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid",
        "commit", "-m", "configure actual publication fixture")))
      val descriptor = root.resolve(".textus/assembly.yaml")
      Files.writeString(descriptor, Files.readString(descriptor).replace("components: []",
        "components:\n  - namespace: org.simplemodeling.textus\n    id: SmWorkflow\n    version: 0.1.0-SNAPSHOT"))
      Given("a local-ahead clean repository with explicit beforeSync publication")
      val started = call(root, "startRepositorySync", Vector("--input",
        Json.obj("repositoryIdentity" -> "sm-workflow".asJson, "syncMode" -> "FULL_STATE".asJson).noSpaces))
      val session = started.hcursor.get[String]("session_id").toOption.get
      When("normal Advance publishes and synchronizes")
      val result = call(root, "advanceRepositorySync", Vector("--sessionId", session))
      result.hcursor.get[String]("kind").toOption shouldBe Some("Terminal")
      Files.readString(root.resolve("publication-count.txt")) shouldBe "published\n"
      Files.readString(repo.resolve(catalog)) shouldBe "published: after\n"
      take(_run_setup_command_(Vector("-C", repo.toString, "diff", "--exit-code", "HEAD", "--")))
      take(_run_setup_command_(Vector("-C", repo.toString, "diff", "--exit-code", "HEAD", "refs/remotes/origin/main", "--")))
      Then("a new process collects the terminal without publishing again")
      call(root, "advanceRepositorySync", Vector("--sessionId", session)).hcursor.get[String]("kind").toOption shouldBe Some("Terminal")
      Files.readString(root.resolve("publication-count.txt")) shouldBe "published\n"
    }
  }
}
