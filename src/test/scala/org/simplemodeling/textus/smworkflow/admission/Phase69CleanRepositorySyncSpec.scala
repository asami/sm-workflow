package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, Lifecycle}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowContinuation
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
  * @version Oct. 28, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class Phase69CleanRepositorySyncSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with Phase69ConfiguredRepositoryFixture {


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

    "preserve unfinished dirty work and issue normal purpose grouping without a checkpoint" in {
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/phase69-clean-sync").toAbsolutePath), "dirty-")
      _with_runtime_(root, "Equal") { (owner, component, actor, prepare) =>
        prepare(call => call.setupC(_setup_commands_(root, "Equal").size))
        val started = read_value[V.RepositorySyncStartResult](take(owner.executeOperationResponseInContext(Request.of(
          component = component.name, service = "RepositorySyncService", operation = "startRepositorySync",
          properties = List(Property("input", org.goldenport.record.Record.data(
            "repositoryIdentity" -> "sm-workflow", "syncMode" -> "FULL_STATE"), None))), actor)))
        val unfinished = root.resolve("repository/ongoing.txt")
        Files.writeString(unfinished, "another unfinished purpose\n")
        var original = Option.empty[RepositorySyncManagedGitObservation.Observed]
        var originalIndex = ""
        prepare(call => for {
          observed <- call.observeC()
          index <- RepositorySyncManagedGitObservation.indexTreeC(call.core)
        } yield { original = Some(observed); originalIndex = index; () })
        When("normal public continuation inventories the unfinished dirty purpose")
        val result = owner.executeOperationResponseInContext(Request.of(component = component.name,
          service = "RepositorySyncService", operation = "advanceRepositorySync",
          properties = List(Property("sessionId", started.sessionId, None))), actor)
        val issued = read_value[V.RepositorySyncAdvanceResult](take(result))
        Then("actual purpose work is issued while unfinished content and local state remain intact")
        issued.kind shouldBe "Request"
        issued.submitOperation shouldBe Some("submitRepositoryPurposeResult")
        issued.submitSessionId shouldBe Some(started.sessionId)
        issued.resultType shouldBe Some("RepositorySyncPurposeResult")
        issued.issuedWorkContext should not be empty
        issued.purposeWork should not be empty
        issued.convergence shouldBe None
        Files.readString(unfinished) shouldBe "another unfinished purpose\n"
        val input = ThreeWorkflowNormalHostFixture.definition(root).repository.input.copy(
          remoteEndpointIdentity = root.resolve("upstream.git").toString)
        prepare(call => for {
          work <- call.repositoryWorkC(InstanceIdentity(started.sessionId),
            RepositorySyncPurposeContinuation.completionOperation, RepositorySyncPurpose.inputCodec)
          current <- call.currentC(InstanceIdentity(started.sessionId))
          observed <- call.observeC()
          index <- RepositorySyncManagedGitObservation.indexTreeC(call.core)
          preservation <- call.nativeStageC(input, InstanceIdentity(started.sessionId), root, "preservation-intent")
          checkpoint <- call.nativeStageC(input, InstanceIdentity(started.sessionId), root, "checkpoint-created")
          push <- call.nativeStageC(input, InstanceIdentity(started.sessionId), root, "push-intent")
          commit <- call.nativeStageC(input, InstanceIdentity(started.sessionId), root, "commit-intent")
        } yield {
          val request = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncPurpose.Request]].request
          val captured = request.input.get.value
          work.handle.instanceIdentity.value shouldBe started.sessionId
          request.requiredOperation.capability shouldBe "group-repository-purpose-capability"
          captured.files.map(_.path) shouldBe Vector("ongoing.txt")
          captured.files.head.worktree shouldBe Some("another unfinished purpose\n")
          captured.files.head.indexed shouldBe None
          captured.parent shouldBe original.get.head
          captured.indexTree shouldBe originalIndex
          current.lifecycle shouldBe Lifecycle.Active
          current.suspension.get.continuationIdentity.value shouldBe issued.issuedWorkContext.get.continuationId
          observed.head shouldBe original.get.head
          observed.tree shouldBe original.get.tree
          observed.dirty shouldBe original.get.dirty
          index shouldBe originalIndex
          preservation shouldBe None
          checkpoint shouldBe None
          push shouldBe None
          commit shouldBe None
          ()
        })
        Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
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
