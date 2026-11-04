/**
 * @since   Oct. 26, 2026
 * @version Oct. 27, 2026
 */
package org.simplemodeling.textus.smworkflow.preparation

import cats.data.NonEmptyVector
import io.circe.Json
import io.circe.syntax.*
import java.time.Instant
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.workflow.*
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.admission.*
import org.simplemodeling.textus.smworkflow.admission.StandaloneFixExecution.given
import org.simplemodeling.textus.smworkflow.admission.GoalTaskMalformedIssuedWorkRecoveryFixture.*
import org.simplemodeling.textus.smworkflow.value as V
import GoalTaskMalformedIssuedWorkRecovery as R

/** Specification-owned evidence; passive preparation establishes no live authority. */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalTaskExternalEvidencePreparationSpec extends AnyWordSpec with Matchers with org.scalatest.GivenWhenThen {
  private val now = Instant.parse("2026-10-27T00:00:00Z")
  private val artifact = V.WorkflowArtifactReference(reference.identity, reference.revision)
  private val execution = V.WorkflowExecutionEvidence(Vector(artifact), Some(target.participant), None, None)
  private val description = V.GoalTaskMalformedIssuedWorkRecoveryDescriptionResult(target.sessionId, target.componentInstance,
    "decision-required", R.limitation, target.asJson.noSpaces, R.choices, R.recoveryOperation, "UNKNOWN", None)
  private val answer = V.GoalTaskMalformedIssuedWorkRecoveryInput(target.sessionId, target.oldContinuationId,
    target.asJson.noSpaces, R.choices.head, "Fixture independently observed Human choice", target.participant, artifact)
  private def record(json: Json): Record = take(new org.goldenport.record.io.RecordDecoder().json(json.noSpaces))
  private val declared = NativeExternalEvidence.Declaration("workflow", DataStore.CollectionId.EntityStore(
    EntityCollectionId("test", "host", "task_evidence")), "fixture-host")

  "Task recovery external evidence" should {
    "retain the exact Human direction and reject stale, foreign and future observations" in {
      val prepared = take(NativeRetainedWorkRecoveryPreparation.prepareC(description, answer, artifact, execution,
        target.participant, declared.issuer, now))
      val evidence = take(NativeExternalEvidence.decodeStorageC(prepared.record.getString(NativeExternalEvidence.storageField).get))
      val purpose = NativeExternalEvidence.Purpose.GoalTaskMalformedIssuedWorkRecovery
      val lookup = ThreeWorkflowExternalEvidencePorts.key(artifact.identity, artifact.revision)
      NativeExternalEvidence.observeC(evidence, declared, purpose, lookup, evidence.binding, now).isSuccess shouldBe true
      NativeExternalEvidence.observeC(evidence, declared.copy(issuer = "foreign"), purpose, lookup, evidence.binding, now).isSuccess shouldBe false
      NativeExternalEvidence.observeC(evidence, declared, purpose, lookup, evidence.binding, now.minusSeconds(1)).isSuccess shouldBe false
      val stale = take(R.bindingC(target.componentInstance, target.copy(frontierRevision = 4L), answer.choice,
        answer.rationale, target.participant, reference))
      NativeExternalEvidence.observeC(evidence, declared, purpose, lookup, stale, now).isSuccess shouldBe false
      val claimRelease = take(R.bindingC(target.componentInstance, target.copy(continuationRevision = "3"), answer.choice,
        answer.rationale, target.participant, reference))
      NativeExternalEvidence.observeC(evidence, declared, purpose, lookup, claimRelease, now).isSuccess shouldBe false
      NativeRetainedWorkRecoveryPreparation.prepareC(description, answer.copy(target = target.copy(frontierRevision = 4L).asJson.noSpaces),
        artifact, execution, target.participant, declared.issuer, now).isSuccess shouldBe false
      NativeRetainedWorkRecoveryPreparation.prepareC(description, answer, artifact,
        execution.copy(workerIdentity = Some("foreign-human")), target.participant, declared.issuer, now).isSuccess shouldBe false
    }
    "use the GoalTask family and dedicated purpose in the public passive evidence route" in {
      val files = Map("request" -> description.toRecord(), "result" -> answer.toRecord(),
        "reference" -> artifact.toRecord(), "execution" -> execution.toRecord()).view.mapValues(r => take(r.toJsonStringC)).toMap
      val options = Map("family" -> "GoalTask", "kind" -> "malformed-issued-work-recovery", "request" -> "request", "result" -> "result",
        "reference" -> "reference", "execution-evidence" -> "execution", "participant" -> target.participant,
        "issuer" -> declared.issuer, "occurred-at" -> now.toString, "collection" -> "entity:test.host.task_evidence")
      def read(path: String) = org.goldenport.Consequence.success(files(path))
      val bootstrap = take(NativeExternalEvidenceInput.prepareC(options, read))
      val json = io.circe.parser.parse(take(bootstrap.toJsonStringC)).fold(error => fail(error.getMessage), identity)
      val imported = json.hcursor.downField("datastore").downArray
      imported.get[String]("collection").toOption shouldBe Some("entity:test.host.task_evidence")
      val row = imported.downField("records").downArray
      val direct = take(NativeRetainedWorkRecoveryPreparation.prepareC(description, answer, artifact, execution,
        target.participant, declared.issuer, now))
      row.get[String]("id").toOption shouldBe Some(direct.recordIdentity)
      val payload = row.get[String](NativeExternalEvidence.storageField).fold(error => fail(error.getMessage), identity)
      Some(payload) shouldBe direct.record.getString(NativeExternalEvidence.storageField)
      val evidence = take(NativeExternalEvidence.decodeStorageC(payload))
      evidence.purpose shouldBe NativeExternalEvidence.Purpose.GoalTaskMalformedIssuedWorkRecovery.identity
      evidence.participant shouldBe target.participant
      evidence.binding shouldBe take(R.bindingC(target.componentInstance, target, answer.choice,
        answer.rationale, target.participant, reference))
      // A model identity's presentation is not the public importer collection address.
      NativeExternalEvidenceInput.prepareC(options.updated("collection",
        EntityCollectionId("test", "host", "task_evidence").print), read).isSuccess shouldBe false
      NativeExternalEvidenceInput.prepareC(options.updated("family", "GoalPhase"), read).isSuccess shouldBe false
    }
  }

  "Task trend-continuation passive preparation" should {
    "preserve opaque target keys and exact Human provenance through the ordinary family route" in {
      import org.simplemodeling.textus.smworkflow.admission.GoalTaskRepairContinuation as C
      Given("a complete opaque Task trend-stop description and separately supplied specification Human facts")
      val target = GoalTaskRepairContinuationFixture.target.copy(frontier = Json.obj(
        "nativeCamelCase" -> Json.obj("arbitrary-profile-key" -> "preserved".asJson)))
      val description = V.GoalTaskRepairContinuationDescriptionResult(target.sessionId, target.componentInstance,
        "decision-required", "Fixture Human admission required", target.asJson.noSpaces, C.choices, C.resumeOperation)
      val reference = V.WorkflowArtifactReference("fixture-continuation-human", "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some(target.participant), None, None)
      val answer = V.GoalTaskRepairContinuationInput(target.sessionId, description.target, "ContinueRepair",
        "Fixture independently observed Human continuation", target.participant, reference)
      val files = Map("request" -> description.toRecord(), "result" -> answer.toRecord(),
        "reference" -> reference.toRecord(), "execution" -> execution.toRecord()).view.mapValues(r => take(r.toJsonStringC)).toMap
      val options = Map("family" -> "GoalTask", "kind" -> "repair-continuation", "request" -> "request", "result" -> "result",
        "reference" -> "reference", "execution-evidence" -> "execution", "participant" -> target.participant,
        "issuer" -> declared.issuer, "occurred-at" -> now.toString, "collection" -> "entity:test.host.task_evidence")
      def _read_(path: String) = org.goldenport.Consequence.success(files(path))
      When("passive prepareEvidence builds only the explicit StartupImport record")
      val bootstrap = take(NativeExternalEvidenceInput.prepareC(options, _read_))
      val json = io.circe.parser.parse(take(bootstrap.toJsonStringC)).toOption.get
      val row = json.hcursor.downField("datastore").downArray.downField("records").downArray
      val evidence = take(NativeExternalEvidence.decodeStorageC(row.get[String](NativeExternalEvidence.storageField).toOption.get))
      val binding = take(C.bindingC(target.componentInstance, target, answer.choice, answer.rationale, target.participant,
        ContextReference(reference.identity, reference.revision)))
      Then("the dedicated purpose and unprojected target bind the exact actual Human source")
      evidence.purpose shouldBe NativeExternalEvidence.Purpose.GoalTaskRepairContinuation.identity
      evidence.binding shouldBe binding
      evidence.binding.hcursor.downField("goalTaskRepairContinuation").get[Json]("target").toOption shouldBe Some(target.asJson)
      val purpose = NativeExternalEvidence.Purpose.GoalTaskRepairContinuation
      val lookup = ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision)
      NativeExternalEvidence.observeC(evidence, declared, purpose, lookup, binding, now).isSuccess shouldBe true
      NativeExternalEvidence.observeC(evidence.copy(participant = "foreign-human"), declared, purpose, lookup, binding, now).isSuccess shouldBe false
      val foreignsource = binding.mapObject(_.add("goalTaskRepairContinuation", binding.hcursor.downField("goalTaskRepairContinuation").focus.get
        .mapObject(_.add("answer", binding.hcursor.downField("goalTaskRepairContinuation").downField("answer").focus.get
          .mapObject(_.add("source", "Model".asJson))))))
      C.selectBinding(foreignsource).isLeft shouldBe true
      C.selectBinding(binding.mapObject(_.add("unsupported", true.asJson))).isLeft shouldBe true
      NativeReviewPreparation.taskRepairContinuationC(description, answer.copy(participant = "foreign-human"), reference,
        execution, target.participant, declared.issuer, now).isSuccess shouldBe false
      NativeReviewPreparation.taskRepairContinuationC(description, answer, reference,
        execution.copy(workerIdentity = Some("foreign-worker")), target.participant, declared.issuer, now).isSuccess shouldBe false
      NativeReviewPreparation.taskRepairContinuationC(description, answer.copy(reference = reference.copy(revision = "2")), reference,
        execution, target.participant, declared.issuer, now).isSuccess shouldBe false
      NativeReviewPreparation.taskRepairContinuationC(description, answer.copy(target = target.copy(frontierRevision = 9L).asJson.noSpaces),
        reference, execution, target.participant, declared.issuer, now).isSuccess shouldBe false
      NativeReviewPreparation.taskRepairContinuationC(description, answer.copy(target = "{malformed"), reference,
        execution, target.participant, declared.issuer, now).isSuccess shouldBe false
      NativeExternalEvidenceInput.prepareC(options.updated("family", "GoalPhase"), _read_).isSuccess shouldBe false
      NativeExternalEvidenceInput.prepareC(options.removed("participant"), _read_).isSuccess shouldBe false
    }
  }

  "Task independent review preparation" should {
    "retain Task completion identity and reject StandaloneFix identity for the same typed Task result" in {
      val work = StandaloneFixExecution.Work(target.sessionId, "7", "Review", state.copy(stage = "Review"))
      val handle = record(Json.obj("componentIdentity" -> "fixture.Component".asJson, "workflowIdentity" -> "GoalTaskWorkflow".asJson,
        "workflowRevision" -> "fixture-v1".asJson, "instanceIdentity" -> target.sessionId.asJson, "protocolVersion" -> "cncf.workflow-protocol.v1".asJson))
      val context = V.WorkflowReviewContext(NativeExternalEvidence.Purpose.StandaloneReview.identity, target.componentInstance,
        record(work.asJson), V.WorkflowReviewCompletion(handle, target.sessionId, "fixture-task-review", "GoalTaskWorkResult"))
      val result = V.GoalTaskWorkResult("accept", "Fixture integrated Task review", Vector.empty,
        NonEmptyVector.one(V.GoalTaskCondition("behavior", true, "Fixture behavior addressed")), Vector.empty, None, None)
      val reviewer = execution.copy(workerIdentity = Some("fixture-independent-reviewer"))
      val prepared = take(NativeReviewPreparation.taskC(context, result, artifact, reviewer, declared.issuer, now))
      val evidence = take(NativeExternalEvidence.decodeStorageC(prepared.record.getString(NativeExternalEvidence.storageField).get))
      evidence.binding.hcursor.downField("attestation").downField("result").get[String]("typeIdentity").toOption.get shouldBe "GoalTaskWorkResult"
      NativeReviewPreparation.taskC(context.copy(completion = context.completion.copy(typeIdentity = "StandaloneFixWorkResult")),
        result, artifact, reviewer, declared.issuer, now).isSuccess shouldBe false
      NativeReviewPreparation.taskC(context.copy(target = record(work.copy(state = work.state.copy(input = input.copy(task = None))).asJson)),
        result, artifact, reviewer, declared.issuer, now).isSuccess shouldBe false
    }
  }
}
