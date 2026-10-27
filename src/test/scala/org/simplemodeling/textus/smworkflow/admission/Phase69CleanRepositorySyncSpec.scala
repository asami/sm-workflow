package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.file.{Files, Path}
import org.goldenport.cncf.subsystem.GenericSubsystemDescriptor
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, Lifecycle}
import org.goldenport.protocol.{Property, Request}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import SplitPhaseExplicitPartitionFixture.take

/** Controlled repositories and actual public normal Operations. These cases
  * author qualification; they do not attest an installed deployment or consent
  * from a live user. The product route uses CNCF default Git, no gitPrograms.
  * @since   Oct. 28, 2026
  * @version Oct. 27, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class Phase69CleanRepositorySyncSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  override protected def standard_runtime = true

  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val descriptor = take(GenericSubsystemDescriptor.loadComponentArchive(Path.of("src/main/car").toAbsolutePath))
    val template = io.circe.parser.parse(descriptor.config("textus.subsystem.process-execution.plan")).toOption.get
    val values = Map("repository.root" -> root.resolve("repository").toString, "git.branch" -> "main",
      "git.remote" -> "origin", "git.remote-branch" -> "main", "git.endpoint" -> root.resolve("upstream.git").toString,
      "git.user-name" -> "Fixture", "git.user-email" -> "fixture@example.invalid",
      "environment.path" -> "/usr/bin:/bin", "environment.java-home" -> "", "environment.ssh-auth-sock" -> "")
    def expand(value: Json): Json = value.mapString(text => values.foldLeft(text) { case (prior, (key, value)) =>
      prior.replace("${" + key + "}", value) }).mapArray(_.map(expand)).mapObject(_.mapValues(expand))
    // With no SBT binding an accidental product check cannot pass. The product
    // Git boundary resolves its installed default executable once per target.
    val removedcapabilities = template.hcursor.get[Vector[Json]]("sbtPrograms").toOption.get
      .map(_.hcursor.get[Json]("capability").toOption.get).toSet
    expand(template).mapObject(fields => fields.add("sbtPrograms", Json.arr()).add("grants",
      fields("grants").get.mapArray(_.map(_.mapObject(grant => grant.add("capabilities",
        grant("capabilities").get.mapArray(_.filterNot(removedcapabilities))))))))
  }

  "Phase 6.9 configured clean sync" should {
    for (scenario <- Vector("Equal", "LocalAhead", "RemoteAhead")) {
      s"converge $scenario through normal public Start/Advance without caller grants, tests or review" in {
        val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/phase69-clean-sync").toAbsolutePath), "case-")
        val input = ThreeWorkflowNormalHostFixture.definition(root).repository.input
        var session = ""
        Given("a configured default Git repository, an authenticated actor and no selection or effect rows")
        _with_runtime_(root, scenario) { (owner, component, actor, prepare) =>
          prepare(call => call.setupC(_setup_commands_(root, scenario).size))
          def invoke[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
            read_value[A](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
              service = "RepositorySyncService", operation = operation, properties = properties), actor)))
          When("the actor explicitly requests synchronization")
          val started = invoke[V.RepositorySyncStartResult]("startRepositorySync", List(Property("input", org.goldenport.record.Record.data(
            "repositoryIdentity" -> input.repositoryIdentity, "syncMode" -> input.syncMode), None)))
          session = started.sessionId
          val result = invoke[V.RepositorySyncAdvanceResult]("advanceRepositorySync", List(Property("sessionId", session, None)))
          Then("actual local, tracking and advertised remote convergence closes the native Session")
          result.kind shouldBe "Terminal"
          result.issuedWorkContext shouldBe None
          prepare(call => for {
            observed <- call.observeC()
            current <- call.currentC(InstanceIdentity(session))
            validation <- call.validationC(input, InstanceIdentity(session), root)
            push <- call.nativeStageC(input, InstanceIdentity(session), root, "push-intent")
            commit <- call.nativeStageC(input, InstanceIdentity(session), root, "commit-intent")
          } yield {
            observed.head shouldBe result.convergence.get.tip
            observed.trackingTip shouldBe Some(observed.head)
            observed.counts shouldBe Some(RepositorySyncManagedGitObservation.Counts(0, 0))
            observed.dirty shouldBe empty
            current.lifecycle shouldBe Lifecycle.Completed
            validation.mode shouldBe "explicit-no-product-tests"
            validation.selected shouldBe empty
            validation.receipts.map(_.identity) shouldBe Vector("index-local")
            push.nonEmpty shouldBe (scenario == "LocalAhead")
            commit shouldBe None
            ()
          })
          Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
        }
        When("the runtime is reopened and the completed result is collected")
        _with_runtime_(root, scenario) { (owner, component, actor, _) =>
          val retained = read_value[V.RepositorySyncAdvanceResult](take(owner.executeOperationResponseInContext(Request.of(
            component = component.name, service = "RepositorySyncService", operation = "advanceRepositorySync",
            properties = List(Property("sessionId", session, None))), actor)))
          retained.kind shouldBe "Terminal"
          retained.sessionId shouldBe session
          Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
        }
      }
    }

    "preserve dirty work and report unfinished local preparation without a checkpoint" in {
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/phase69-clean-sync").toAbsolutePath), "dirty-")
      _with_runtime_(root, "Equal") { (owner, component, actor, prepare) =>
        prepare(call => call.setupC(_setup_commands_(root, "Equal").size))
        val started = read_value[V.RepositorySyncStartResult](take(owner.executeOperationResponseInContext(Request.of(
          component = component.name, service = "RepositorySyncService", operation = "startRepositorySync",
          properties = List(Property("input", org.goldenport.record.Record.data(
            "repositoryIdentity" -> "sm-workflow", "syncMode" -> "FULL_STATE"), None))), actor)))
        val unfinished = root.resolve("repository/ongoing.txt")
        Files.writeString(unfinished, "another unfinished purpose\n")
        val result = owner.executeOperationResponseInContext(Request.of(component = component.name,
          service = "RepositorySyncService", operation = "advanceRepositorySync",
          properties = List(Property("sessionId", started.sessionId, None))), actor)
        result.isSuccess shouldBe false
        result.toString should include("local preparation is unfinished")
        Files.readString(unfinished) shouldBe "another unfinished purpose\n"
        prepare(call => call.nativeStageC(ThreeWorkflowNormalHostFixture.definition(root).repository.input,
          InstanceIdentity(started.sessionId), root, "preservation-intent").map(value => { value shouldBe None; () }))
      }
    }

    "refuse missing intent and mismatched tracking before creating a native Start" in {
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/phase69-clean-sync").toAbsolutePath), "invalid-")
      _with_runtime_(root, "Equal") { (owner, component, actor, _) =>
        val operation = Request.of(component = component.name, service = "RepositorySyncService", operation = "startRepositorySync")
        owner.executeOperationResponseInContext(operation, actor).isSuccess shouldBe false
        val input = ThreeWorkflowNormalHostFixture.definition(root).repository.input.copy(localBranch = "other")
        val result = owner.executeOperationResponseInContext(operation.copy(properties = List(Property("input", input, None))), actor)
        result.isSuccess shouldBe false
        result.toString should include("host storage/tracking declaration differs")
      }
    }
  }
}
