package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path}
import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.SbtOperation
import org.goldenport.protocol.{Property, Request}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value as V
import ManagedDevelopment.*
import SplitPhaseExplicitPartitionFixture.take

/** Minimal real producer -> private repository -> immutable selection -> consumer
  * compilation. Fixture setup creates inputs; SBT effects use CNCF's native UoW.
  * @since   Nov. 12, 2026
  * @version Nov. 12, 2026
  * @author ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class Phase7ManagedDevelopmentSpec extends AnyWordSpec with GivenWhenThen with ThreeWorkflowManagedRepositoryFixture {
  private val edge = Edge("consumer-provider",
    GoalPhaseDependencyScope.Component("org.example.Provider", "provider-repository", TypedGitHostPlanFixture.workspace),
    GoalPhaseDependencyScope.Component("org.example.Consumer", "consumer-repository", "development-consumer"),
    "development-artifacts", true, None, Coordinate("org.example.smworkflow-p7", "provider_2.12", "0.0.1-SNAPSHOT"))
  private val definition = Definition("lane-a", "development-artifacts", "artifacts/ivy", "staging", Vector(edge))
  private val collection = ThreeWorkflowNormalHostFixture.collection("managed_development")
  private val policy = ManagedDevelopmentRuntime.Policy("workflow", collection, definition)
  private val input = Input(edge.identity, "publish-one", "publication-one", "producer-input-one")

  private class Memory extends Port {
    val rows = scala.collection.mutable.Map.empty[String, Json]
    var calls = 0
    var execution = Execution("succeeded", Json.obj())
    var completedOutput = true
    val participant = "owner"
    val source = Json.obj("head" -> ("a" * 40).asJson, "dirty" -> Vector("actual-change.scala").asJson)
    def loadC(key: String) = Consequence.success(rows.get(key))
    def createC(key: String, value: Json): Consequence[Unit] =
      if (rows.contains(key)) invalid("duplicate native record") else { rows(key) = value; Consequence.unit }
    def sourceC(e: Edge) = Consequence.success(source)
    def executeC(e: Edge, i: Input, selected: Option[Publication]) = { calls += 1; Consequence.success(execution) }
    def publicationC(e: Edge, i: Input, origin: Json): Consequence[Publication] =
      if (!completedOutput) invalid("required completed adapter output is unavailable")
      else Consequence.success(Publication(e, definition.scope,
        i.publication, i.selection, e.requested.copy(version = version(e.requested.version, definition.scope, i.publication)),
        "/owner/artifacts/jar", "/owner/artifacts/ivy", origin, Json.obj(), Json.obj()))
    def consumerReportC(e: Edge, i: Input, p: Publication) = Consequence.success(Json.obj("actual" -> true.asJson))
  }
  private def run(operation: String, m: Memory, value: Input = input) =
    take(runC(operation, definition, value, m))

  "Minimal managed development" should {
    "retain source origin, exact publication and immutable edge selection without implicitly building a provider" in {
      Given("an explicit independent source, JAR and project binding")
      val m = new Memory
      run("publish", m).status shouldBe "published"
      run("publish", m).status shouldBe "published"
      m.calls shouldBe 1
      val consumer = input.copy(requestIdentity = "consumer-one", selection = "consumer-selection-one")
      When("the consumer selects the exact completed result")
      run("select", m, consumer).status shouldBe "selected-unverified"
      m.calls shouldBe 1
      run("status", m, consumer).status shouldBe "selected-unverified"
      run("compile", m, consumer).status shouldBe "verified"
      run("compile", m, consumer).status shouldBe "verified"
      m.calls shouldBe 2
      run("status", m, consumer).status shouldBe "verified"
      val retained = m.rows(ManagedDevelopment.key(definition, edge, "publication", input.publication)).as[Publication].toOption.get
      retained.source shouldBe m.source
      retained.selected.version should not be edge.requested.version
      runC("select", definition, consumer.copy(publication = "missing"), m).isSuccess shouldBe false
      runC("compile", definition, consumer.copy(selection = "foreign"), m).isSuccess shouldBe false
      m.calls shouldBe 2
      run("publish", m, input.copy(requestIdentity = "publish-two", publication = "publication-two")).status shouldBe "published"
      runC("select", definition, consumer.copy(publication = "publication-two"), m).isSuccess shouldBe false
      m.calls shouldBe 3
    }

    "preserve failed and unknown effects without automatic publication replay or selectable partial output" in {
      val failed = new Memory
      failed.execution = Execution("failed", Json.obj("exit" -> 1.asJson))
      run("publish", failed).status shouldBe "failed"
      run("publish", failed).status shouldBe "failed"
      failed.calls shouldBe 1
      run("status", failed).status shouldBe "failed"
      runC("select", definition, input, failed).isSuccess shouldBe false
      val incomplete = new Memory
      incomplete.completedOutput = false
      run("publish", incomplete).status shouldBe "unverified"
      run("publish", incomplete).status shouldBe "unverified"
      incomplete.calls shouldBe 1
      runC("select", definition, input, incomplete).isSuccess shouldBe false
      val interrupted = new Memory
      interrupted.rows(ManagedDevelopment.key(definition, edge, "publish-intent", input.requestIdentity)) =
        Json.obj("input" -> input.asJson, "participant" -> interrupted.participant.asJson)
      run("publish", interrupted).status shouldBe "unknown"
      interrupted.calls shouldBe 0
      runC("publish", definition, input.copy(publication = "crossed"), interrupted).isSuccess shouldBe false
      validateC(definition.copy(edges = Vector(edge.copy(localRepository = "foreign")))).isSuccess shouldBe false
      version("1.0", "a-b", "c") should not be version("1.0", "a", "b-c")
    }

    "reject changed modified or untracked contents even when Git status and paths stay identical" in {
      val dirty = Vector(
        RepositorySyncManagedGitObservation.DirtyPath(' ', 'M', "already-modified.scala", None),
        RepositorySyncManagedGitObservation.DirtyPath('?', '?', "untracked.scala", None))
      val observed = RepositorySyncManagedGitObservation.Observed("main", "a" * 40, "b" * 40, dirty, None, None)
      dirty.foreach { changed =>
        Given("unchanged Git HEAD, tree and modified/untracked path metadata")
        var contents = dirty.map(entry => entry.path -> "before".getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector).toMap
        val m = new Memory {
          override def sourceC(e: Edge): Consequence[Json] = ManagedDevelopmentRuntime.sourceContentsC(observed,
            name => Consequence.success(contents.get(name)))
          override def executeC(e: Edge, i: Input, selected: Option[Publication]): Consequence[Execution] = {
            contents = contents.updated(changed.path, "after!".getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector)
            super.executeC(e, i, selected)
          }
        }
        val before = take(m.sourceC(edge))
        When("the successful producer execution changes an already dirty file's contents")
        run("publish", m).status shouldBe "unverified"
        val after = take(m.sourceC(edge))
        before.hcursor.downField("dirty").focus shouldBe after.hcursor.downField("dirty").focus
        before should not be after
        Then("the changed output is retained as unverified and cannot be selected or replayed")
        m.rows.contains(ManagedDevelopment.key(definition, edge, "publication", input.publication)) shouldBe false
        runC("select", definition, input, m).isSuccess shouldBe false
        run("status", m).status shouldBe "unverified"
        run("publish", m).status shouldBe "unverified"
        m.calls shouldBe 1
      }
    }

    "publish a real Scala library privately and compile a never-published original dependency through the public Operations" in {
      Given("an enabled published sbt-cozy plugin and two explicitly registered build workspaces")
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/phase7-managed-development").toAbsolutePath), "native-").toRealPath()
      prepareBuild(root.resolve("repository"), producer = true)
      prepareBuild(root.resolve("consumer"), producer = false)
      Files.createDirectories(root.resolve(".textus/sm-workflow/work.d"))
      val shared = Path.of(System.getProperty("user.home"), ".ivy2/local", edge.requested.organization, edge.requested.module)
      val baselineExists = Files.exists(shared)
      val baseline = tree(shared)
      val storage = ThreeWorkflowNormalHostFixture.storagePlan.mapObject(fields => fields.add("records",
        Json.fromValues(fields("records").get.asArray.get :+ Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson,
          "storeName" -> "workflow".asJson, "collection" -> collection.asJson))))
      _with_runtime_(root, "Goal", hostParameters = Map("sm-workflow.managed-development" -> policy.asJson.noSpaces,
        "textus.subsystem.storage.plan" -> storage.noSpaces)) { (subsystem, component, context, setup) =>
        setup(call => call.setupC(_setup_commands_(root, "Goal").size, scenario = "Goal"))
        def call(operation: String, value: Input): V.ManagedDevelopmentResult = read_control_value[V.ManagedDevelopmentResult](
          take(subsystem.executeOperationResponseInContext(Request.of(component = component.name,
            service = "ManagedDevelopmentService", operation = operation,
            properties = List(Property("input", GoalPhaseNativeMetadata.record(value.asJson), None))), context)))
        When("the actual producer publishes without shared publishLocal")
        val publication = call("publishDevelopmentArtifact", input)
        withClue(publication.toRecord().toString) { publication.status shouldBe "published" }
        val selected = input.copy(requestIdentity = "consumer-one", selection = "consumer-selection-one")
        call("selectDevelopmentArtifact", selected).status shouldBe "selected-unverified"
        Then("the original dependency need never have existed in a baseline repository")
        Files.exists(shared) shouldBe baselineExists
        val compiled = call("compileDevelopmentConsumer", selected)
        withClue(compiled.toRecord().toString) { compiled.status shouldBe "verified" }
        call("readDevelopmentArtifactStatus", selected).status shouldBe "verified"
        val output = root.resolve("consumer/target/scala-2.12/classes/example/Consumer$.class")
        Files.isRegularFile(output) shouldBe true
        val source = root.resolve("consumer/src/main/scala/example/Consumer.scala")
        Files.writeString(source, "package example\nobject Consumer { val incompatible: Int = Provider.missingApi }\n")
        val incompatible = call("compileDevelopmentConsumer", selected.copy(requestIdentity = "consumer-incompatible"))
        incompatible.status shouldBe "failed"
        call("readDevelopmentArtifactStatus", selected.copy(requestIdentity = "consumer-incompatible")).status shouldBe "failed"
        tree(shared) shouldBe baseline
        val archive = root.resolve(".textus/sm-workflow/work.d").resolve(base(definition, edge, input.publication))
          .resolve("jars/" + edge.requested.module + ".jar")
        Files.isRegularFile(archive) shouldBe true
        val marker = archive.getParent.getParent.resolve("completed.json")
        Files.isRegularFile(marker) shouldBe true
        call("publishDevelopmentArtifact", input).status shouldBe "published"
      }
      tree(shared) shouldBe baseline
    }
  }

  private def tree(path: Path): Map[String, Vector[Byte]] = if (!Files.exists(path)) Map.empty else {
    import scala.jdk.CollectionConverters.*
    val stream = Files.walk(path)
    try stream.iterator().asScala.filter(Files.isRegularFile(_)).map(p => path.relativize(p).toString -> Files.readAllBytes(p).toVector).toMap
    finally stream.close()
  }
  private def prepareBuild(root: Path, producer: Boolean): Unit = {
    Files.createDirectories(root.resolve("project"))
    Files.createDirectories(root.resolve("src/main/scala/example"))
    Files.writeString(root.resolve("project/build.properties"), "sbt.version=1.9.7\n")
    Files.writeString(root.resolve("project/plugins.sbt"),
      "resolvers += \"SimpleModeling.org\" at \"https://www.simplemodeling.org/repository/maven\"\n" +
      "resolvers += Resolver.defaultLocal\naddSbtPlugin(\"org.goldenport\" % \"sbt-cozy\" % \"0.1.18-SNAPSHOT\")\n")
    Files.writeString(root.resolve(".gitignore"), "target/\nproject/target/\nproject/project/\n")
    if (producer) {
      Files.createDirectories(root.resolve("src/test/scala"))
      Files.writeString(root.resolve("src/test/scala/ProductCheck.scala"), "object ProductCheck\n")
      Files.writeString(root.resolve("value.txt"), "fixture setup input\n")
    }
    Files.writeString(root.resolve("build.sbt"),
      "enablePlugins(org.goldenport.cozy.CozyDevelopmentArtifactsPlugin)\n" +
      "scalaVersion := \"2.12.18\"\norganization := \"" + edge.requested.organization + "\"\n" +
      "name := \"" + (if (producer) "provider" else "consumer") + "\"\nversion := \"0.0.1-SNAPSHOT\"\n" +
      (if (producer) "" else "libraryDependencies += cozyDevelopmentDependency(\"" + edge.requested.organization + "\" %% \"provider\" % \"0.0.1-SNAPSHOT\").value\n" +
        "cozyDevelopmentModules += (\"" + edge.requested.organization + "\" %% \"provider\" % \"0.0.1-SNAPSHOT\")\n"))
    Files.writeString(root.resolve("src/main/scala/example/" + (if (producer) "Provider" else "Consumer") + ".scala"),
      if (producer) "package example\nobject Provider { def answer: Int = 42 }\n"
      else "package example\nobject Consumer { val answer: Int = Provider.answer }\n")
  }

  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val git = TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main", root.resolve("upstream.git").toString))
    val template = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(1),
      operation = SbtOperation.Compile, executionTimeoutMillis = 240000L, stdoutBytes = 1048576, stderrBytes = 131072)
      .hcursor.get[Vector[Json]]("sbtPrograms").toOption.get.head
    val programs = Vector((edge.provider.workspace, "scoped-publish"), (edge.consumer.workspace, "compile")).map { (workspace, operation) =>
      template.mapObject(_.add("target", workspace.asJson).add("operation", operation.asJson)
        .add("capability", (workspace + "-" + operation).asJson).add("identity", (workspace + "-" + operation).asJson))
    }
    val workspaces = Vector(edge.consumer.workspace -> root.resolve("consumer"), definition.ownerWorkspace -> root.resolve(".textus/sm-workflow/work.d"))
    def array(name: String): Vector[Json] = git.hcursor.get[Vector[Json]](name).toOption.get
    val capabilities = array("gitPrograms").map(_.hcursor.get[String]("capability").toOption.get) ++
      programs.map(_.hcursor.get[String]("capability").toOption.get)
    git.mapObject(_.add("sbtPrograms", programs.asJson)
      .add("workspaces", (array("workspaces") ++ workspaces.map { (id, path) => Json.obj("identity" -> id.asJson, "root" -> path.toString.asJson) }).asJson)
      .add("workspaceGrants", (array("workspaceGrants") ++ workspaces.map { (id, _) => Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson,
        "workspaceId" -> id.asJson, "access" -> "manage".asJson) }).asJson)
      .add("grants", Json.arr(Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson, "capabilities" -> capabilities.asJson))))
  }
}
