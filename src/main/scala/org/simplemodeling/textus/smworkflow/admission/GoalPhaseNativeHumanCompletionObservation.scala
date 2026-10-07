package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*

/** Historical structural projection only. No human verification, renewal,
  * state write, receipt synthesis or permission is performed here.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseNativeHumanCompletionObservation {
  import GoalPhaseHumanDecisionContract.*
  val schema = "sm-workflow.goal-phase-native-human-completion-observation.v1"

  def projectC(issued: WorkflowInteraction[GoalPhaseClosureAuthority.Input, Nothing],
    retained: WorkflowCompletionRecordJsonV1.Record[Answer], current: InstanceRecord): Consequence[String] =
    if (retained == null || retained.instanceRevision == null || retained.instanceRevision.value <= 1L ||
        retained.result == null || retained.closingResult == null || current == null)
      Consequence.stateConflict("Native human completion observation is incomplete")
    else for {
      _ <- if (retained.closingResult.contains(StateMachineOperationResult(
          StateMachineResultTypeReference("GoalPhaseHumanAnswerResult"), retained.result.resultReference))) Consequence.unit
        else Consequence.stateConflict("Native human completion has no matching typed closing result")
      pending <- prefixC(current, InstanceRevision(retained.instanceRevision.value - 1L))
      expected <- GoalPhaseNativeHumanAnswer.prepareC(issued, retained.result, pending)
      completed <- prefixC(current, retained.instanceRevision)
      actualBytes <- WorkflowInstanceRecordJsonV1.encodeC(completed)
      expectedBytes <- WorkflowInstanceRecordJsonV1.encodeC(expected)
      _ <- if (actualBytes == expectedBytes) Consequence.unit
        else Consequence.stateConflict("Native human completion is not in the retained history")
      decision <- WorkflowDecisionJsonV1.encodeC(issued, GoalPhaseClosureAuthority.inputCodec)
      completion <- WorkflowCompletionRecordJsonV1.encodeC(retained, answerPayloadCodec)
    } yield Json.obj("schemaVersion" -> Json.fromString(schema),
      "questionIdentity" -> Json.fromString(retained.result.continuationId.value),
      "choiceIdentity" -> Json.fromString(retained.result.result.value.choiceIdentity),
      "humanEvidenceReference" -> Json.fromString(retained.result.resultReference.identity),
      "completingRevision" -> Json.fromLong(retained.instanceRevision.value),
      "currentRevision" -> Json.fromLong(current.revision.value),
      "issuedDecision" -> Json.fromString(decision), "completion" -> Json.fromString(completion)).noSpaces

  private def prefixC(current: InstanceRecord, revision: InstanceRevision): Consequence[InstanceRecord] =
    current.validateC.flatMap { valid =>
      val prefix = valid.history.takeWhile(_.revision.value <= revision.value)
      prefix.lastOption match {
        case Some(last) if last.revision == revision => valid.copy(revision = last.revision,
          lifecycle = last.lifecycle, currentProgression = last.currentProgression, correlation = last.correlation,
          causation = last.causation, derivedCompositeOccurrence = last.derivedCompositeOccurrence,
          committedPredecessor = last.committedPredecessor, suspension = last.suspension, history = prefix).validateC
        case _ => Consequence.stateConflict("Native human completion history prefix is unavailable")
      }
    }
}
