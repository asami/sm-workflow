/**
 * @since   Nov. 17, 2026
 * @version Nov. 17, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.GitOperation
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, InstanceRecord}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import SplitPhaseExplicitPartitionFixture.take

/** Isolated native Git/DataStore and explicitly specification-owned Human
  * evidence. These authored scenarios grant no live Human direction.
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class RepositorySyncPolicyContinuationSpec extends AnyWordSpec with GivenWhenThen
    with Phase69ConfiguredRepositoryFixture {
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val plan = super.process_plan(root, "Equal", goal)
    if (scenario != "WorkBranch") plan else plan.mapObject(fields => fields.add("gitRepositories",
      fields("gitRepositories").get.mapArray(_.map(row =>
        if (row.hcursor.get[String]("target").contains("sm-workflow-repository"))
          row.mapObject(_.add("localBranch", "work-policy".asJson)) else row))))
  }
  override protected def _setup_commands_(root: Path, scenario: String): Vector[Vector[String]] = {
    val commands = super._setup_commands_(root, "Equal")
    if (scenario != "WorkBranch") commands else commands :+
      Vector("-C", root.resolve("repository").toString, "checkout", "-b", "work-policy")
  }
  private def root(name: String): Path = Files.createTempDirectory(
    Files.createDirectories(Path.of("target/repository-policy-continuation").toAbsolutePath), name + "-")
  private def input(dirty: String, route: String = "direct"): Record = Record.data(
    "repositoryIdentity" -> "sm-workflow", "syncMode" -> "FULL_STATE",
    "mainIntegration" -> route, "uncommittedPolicy" -> dirty)
  private def encoded(value: InstanceRecord): String = take(WorkflowInstanceRecordJsonV1.encodeC(value))

  "Retained RepositorySync policies" should {
    for (route <- Vector("local-first", "confirm")) {
      s"retain the actual canonical-main limitation before source mutation for $route" in {
        val location = root(route)
        _with_runtime_(location, "WorkBranch") { (owner, component, actor, prepare) =>
          prepare(_.setupC(_setup_commands_(location, "WorkBranch").size))
          def invoke[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
            read_value[A](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
              service = "RepositorySyncService", operation = operation, properties = properties), actor)))
          val business = if (route == "local-first") Record.data("repositoryIdentity" -> "sm-workflow", "syncMode" -> "FULL_STATE")
            else input("auto-commit", route)
          val started = invoke[V.RepositorySyncStartResult]("startRepositorySync", List(Property("input", business, None)))
          val result = invoke[V.RepositorySyncAdvanceResult]("advanceRepositorySync", List(Property("sessionId", started.sessionId, None)))
          if (route == "local-first") {
            result.kind shouldBe "Wait"
            result.condition shouldBe Some("CanonicalMainSetupRequired")
          } else {
            result.kind shouldBe "Human"
            result.human.get.choices shouldBe Vector("Direct", "Defer", "Cancel")
            result.human.get.policy should not be empty
            result.human.get.rationale should include("CanonicalMainSetupRequired")
          }
          prepare(_.currentC(InstanceIdentity(started.sessionId)).map { current =>
            current.history.exists(_.causation.value.startsWith("managed-fetch-intent:")) shouldBe false
            ()
          })
        }
      }
    }
    for (directory <- Vector(false, true)) {
      s"preserve dirty changes and refuse before fetch for error, directory=$directory" in {
        val location = root("error")
        val parameters = Map(RepositorySyncDirectoryList.declaration.name ->
          Vector(location.resolve("repository").toString).asJson.noSpaces,
          RepositorySyncPolicies.uncommittedPolicy.name -> "auto-commit")
        _with_runtime_(location, "Equal", hostParameters = parameters) { (owner, component, actor, prepare) =>
          prepare(_.setupC(_setup_commands_(location, "Equal").size))
          Files.writeString(location.resolve("repository/value.txt"), "preserved dirty value\n")
          def invoke[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
            read_value[A](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
              service = "RepositorySyncService", operation = operation, properties = properties), actor)))
          var before = Option.empty[RepositorySyncManagedGitObservation.Observed]
          prepare(_.observeC().map(value => { before = Some(value); () }))
          val session = if (directory) {
            val batch = invoke[V.RepositoryDirectoryBatchResult](RepositorySyncDirectoryList.syncOperation,
              List(Property("input", Record.data("syncMode" -> "FULL_STATE", "uncommittedPolicy" -> "error"), None)))
            batch.repositories.head.kind shouldBe "Error"
            batch.repositories.head.condition shouldBe Some("UncommittedChanges")
            batch.repositories.head.sessionId
          } else {
            val started = invoke[V.RepositorySyncStartResult]("startRepositorySync", List(Property("input", input("error"), None)))
            val refused = invoke[V.RepositorySyncAdvanceResult]("advanceRepositorySync",
              List(Property("sessionId", started.sessionId, None)))
            refused.kind shouldBe "Error"
            refused.condition shouldBe Some("UncommittedChanges")
            started.sessionId
          }
          prepare(call => for {
            observed <- call.observeC()
            current <- call.currentC(InstanceIdentity(session))
          } yield {
            Then("no fetch or dirty commit intent is appended and actual dirty source facts remain")
            observed shouldBe before.get
            current.history.exists(_.causation.value.startsWith("managed-fetch-intent:")) shouldBe false
            current.history.exists(_.currentProgression.exists(_.value.startsWith("sync.Purpose"))) shouldBe false
            Files.readString(location.resolve("repository/value.txt")) shouldBe "preserved dirty value\n"
            ()
          })
        }
      }
    }

    for (choice <- Vector("AutoCommitPurposes", "Defer", "Cancel")) {
      s"require independent Human evidence and retain $choice in the original Session across changed configuration" in {
        val location = root(choice)
        var session = ""
        var pending = Option.empty[V.RepositorySyncAdvanceResult]
        var question = Option.empty[WorkflowInteraction[RepositorySyncPolicyDecision.Input, Nothing]]
        var original = Option.empty[InstanceRecord]
        var before = Option.empty[RepositorySyncManagedGitObservation.Observed]
        _with_runtime_(location, "Equal") { (owner, component, actor, prepare) =>
          prepare(_.setupC(_setup_commands_(location, "Equal").size))
          Files.writeString(location.resolve("repository/value.txt"), "intended and excluded work remain pending\n")
          def invoke[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
            read_value[A](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
              service = "RepositorySyncService", operation = operation, properties = properties), actor)))
          session = invoke[V.RepositorySyncStartResult]("startRepositorySync", List(Property("input", input("confirm"), None))).sessionId
          val advance = List(Property("sessionId", session, None))
          pending = Some(invoke[V.RepositorySyncAdvanceResult]("advanceRepositorySync", advance))
          pending.get.kind shouldBe "Human"
          prepare(call => for {
            value <- call.repositoryPolicyQuestionC(InstanceIdentity(session))
            current <- call.currentC(InstanceIdentity(session))
            actual <- call.observeC()
          } yield {
            question = Some(value); original = Some(current); before = Some(actual)
            val issued = value.current.asInstanceOf[WorkflowContinuation.Decision[RepositorySyncPolicyDecision.Input]].request.input.get.value
            val codec = RepositorySyncPolicyDecision.inputCodec
            val wire = codec.encode(issued)
            val target = wire.hcursor.get[String]("target").toOption.get
            codec.decode(wire) shouldBe Right(issued)
            io.circe.parser.parse(target) shouldBe Right(RepositorySyncPolicyDecision.source(issued).hcursor.get[Json]("policyDecision").toOption.get)
            val public = V.RepositorySyncPolicyDecisionRequest(target)
            public.target shouldBe target
            val publicJson = io.circe.parser.parse(take(public.toRecord().toJsonStringC)).toOption.get
            codec.decode(GoalPhaseNativeMetadata.normalizePublicJson(publicJson).toOption.get) shouldBe Right(issued)
            Vector(Json.obj(), Json.obj("target" -> Json.Null), Json.obj("target" -> Json.fromInt(1)),
              Json.obj("target" -> io.circe.parser.parse(target).toOption.get),
              Json.obj("target" -> "{".asJson), Json.obj("target" -> "[]".asJson),
              Json.obj("target" -> "{}".asJson)).foreach { malformed => codec.decode(malformed).isLeft shouldBe true }
            Vector(issued.copy(sessionId = ""), issued.copy(indexTree = "invalid"),
              issued.copy(choices = Vector("Direct", "AutoCommitPurposes"))).foreach { invalid =>
              codec.decode(codec.encode(invalid)).isLeft shouldBe true
            }
            RepositorySyncPolicyDecision.inputSource(RepositorySyncPolicyDecision.source(issued)) shouldBe
              Right(RepositorySyncPolicyDecision.source(issued))
            ()
          })
          val repeated = invoke[V.RepositorySyncAdvanceResult]("advanceRepositorySync", advance)
          repeated shouldBe pending.get
          prepare(_.currentC(InstanceIdentity(session)).map(value => { encoded(value) shouldBe encoded(original.get); () }))
        }
        Given("a later normal configuration differs, while the retained original selection remains confirm")
        _with_runtime_(location, "Equal", hostParameters = Map(RepositorySyncPolicies.uncommittedPolicy.name -> "error")) {
          (owner, component, actor, prepare) =>
          def call(operation: String, properties: List[Property]) = owner.executeOperationResponseInContext(
            Request.of(component = component.name, service = "RepositorySyncService", operation = operation, properties = properties), actor)
          val selected = question.get
          val request = selected.current.asInstanceOf[WorkflowContinuation.Decision[RepositorySyncPolicyDecision.Input]].request
          val reference = ContextReference("specification-policy-" + choice, "1")
          val answer = ContinuationResult(selected.handle, request.runId, request.continuationId, request.expectedRevision,
            request.context.snapshot, TypedValue(RepositorySyncPolicyDecision.answerCodec.typeIdentity,
              RepositorySyncPolicyDecision.Answer(choice, "Explicit specification-owned Human direction")),
            reference, Vector.empty, ExecutionEvidence(Vector(reference), Some("participant")))
          val humanContext = pending.get.human.get.humanDecisionContext.get
          val humanJson = GoalPhaseNativeMetadata.normalizePublicJson(
            io.circe.parser.parse(take(humanContext.question.toJsonStringC)).toOption.get).toOption.get
          def prepareAnswer(context: V.WorkflowHumanDecisionContext, selectedChoice: String) =
            org.simplemodeling.textus.smworkflow.preparation.NativeHumanDecisionPreparation.repositoryPolicyC(context,
              V.RepositorySyncPolicyDecisionAnswer(selectedChoice, "SPECIFICATION-OWNED Human direction"),
              V.WorkflowArtifactReference(reference.identity, reference.revision),
              V.WorkflowExecutionEvidence(Vector.empty, Some("participant"), None, None), "participant",
              "normal-development-host", actor.clock.instant().minusSeconds(1))
          prepareAnswer(humanContext, choice).isSuccess shouldBe true
          prepareAnswer(humanContext, "Direct").isSuccess shouldBe false
          val crossed = humanContext.copy(question = GoalPhaseNativeMetadata.record(humanJson.deepMerge(
            Json.obj("inputType" -> "RepositorySyncStartInput".asJson))))
          prepareAnswer(crossed, choice).isSuccess shouldBe false
          val invalidSource = humanContext.copy(question = GoalPhaseNativeMetadata.record(humanJson.deepMerge(
            Json.obj("source" -> Json.obj("policyDecision" -> Json.obj("sessionId" -> "".asJson))))))
          prepareAnswer(invalidSource, choice).isSuccess shouldBe false
          val properties = take(NativeWorkflowOperationInputFixture.submissionC(answer))
          When("the answer has no independent host observation")
          call(RepositorySyncPolicyDecision.completionOperation.operation, properties).isSuccess shouldBe false
          prepare(_.currentC(InstanceIdentity(session)).map(value => { encoded(value) shouldBe encoded(original.get); () }))
          Given("the explicit fixture Human observation, separately prepared and provisioned")
          prepare(_.policyHumanEvidenceC(pending.get.human.get.humanDecisionContext.get, answer))
          val accepted = read_value[V.RepositorySyncHumanConflictSubmissionResult](take(
            call(RepositorySyncPolicyDecision.completionOperation.operation, properties)))
          accepted.accepted shouldBe true
          prepare(call => call.observeC().map(value => { value shouldBe before.get; () }))
          val advanced = read_value[V.RepositorySyncAdvanceResult](take(call("advanceRepositorySync", List(Property("sessionId", session, None)))))
          advanced.sessionId shouldBe session
          if (choice == "AutoCommitPurposes") {
            Then("the same Session issues ordinary purpose grouping before any dirty commit")
            advanced.kind shouldBe "Request"
            advanced.resultType shouldBe Some("RepositorySyncPurposeResult")
          } else {
            advanced.kind shouldBe "Wait"
            advanced.condition shouldBe Some(choice)
          }
          val repeated = read_value[V.RepositorySyncAdvanceResult](take(call("advanceRepositorySync", List(Property("sessionId", session, None)))))
          repeated shouldBe advanced
          prepare(call => for {
            observed <- call.observeC()
            current <- call.currentC(InstanceIdentity(session))
          } yield {
            if (choice == "AutoCommitPurposes") {
              // The authorized ordinary Advance fetches before purpose grouping.
              // Assert its exact reference-only change and actual native history.
              observed shouldBe before.get.copy(trackingTip = Some(before.get.head),
                counts = Some(RepositorySyncManagedGitObservation.Counts(0, 0)), mergeBase = Some(before.get.head))
              current.history.count(_.causation.value.startsWith("managed-fetch-intent:")) shouldBe 1
              current.history.count(_.causation.value.startsWith("managed-fetch-observed:")) shouldBe 1
            } else {
              observed shouldBe before.get
              current.history.exists(_.causation.value.startsWith("managed-fetch-intent:")) shouldBe false
            }
            current.history.take(original.get.history.size) shouldBe original.get.history
            current.history.count(_.causation.value.endsWith(":sync-policy:uncommitted:issued")) shouldBe 1
            Files.readString(location.resolve("repository/value.txt")) shouldBe "intended and excluded work remain pending\n"
            ()
          })
        }
      }
    }
  }
}
