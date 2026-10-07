package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import java.time.Instant
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.event.EventRecord
import org.goldenport.cncf.workflow.{CandidateAdmissionProducerAbi, ContinuationIdentity, WorkflowCompletionServiceOperation}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.ContinuationResult
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.value.ReviewStepResult

/** Workflow owns the choices and mechanical transition. Transport supplies only
  * retained review identity, never an intent, target, result or permission.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseNativeHumanAdvanceOperation {
  import GoalPhaseHumanDecisionContract.*
  val operationName = "advanceGoalPhaseHumanDecision"
  val schema = "sm-workflow.goal-phase-native-human-advance-command.v1"
  val resultSchema = "sm-workflow.goal-phase-native-human-question-result.v1"
  val choices = Vector(Choice("keep-open", "Keep the Step open"),
    Choice("request-authorization", "Request separate bounded authorization"))

  def responseC(question: Question): Consequence[OperationResponse] = for {
    wire <- GoalPhaseHumanQuestionRecord.encodeJsonC(question)
    payload <- parse(wire).fold(_ => Consequence.stateConflict("Native question encoding failed"), Consequence.success(_))
  } yield OperationResponse.Scalar(Json.obj("schemaVersion" -> Json.fromString(resultSchema), "question" -> payload).noSpaces)

  def encodeCommand(review: ContinuationIdentity): String = Json.obj(
    "schemaVersion" -> Json.fromString(schema), "reviewContinuationIdentity" -> Json.fromString(review.value)).noSpaces

  def decodeCommandC(wire: String): Consequence[ContinuationIdentity] = {
    val decoded = for {
      text <- Option(wire).filter(_.length <= 2048).toRight("Native advance command is absent or unbounded")
      json <- parse(text).left.map(_ => "Native advance command JSON is invalid")
      fields <- json.asObject.filter(_.keys.toSet == Set("schemaVersion", "reviewContinuationIdentity"))
        .toRight("Native advance fields differ")
      _ <- Either.cond(fields("schemaVersion").flatMap(_.asString).contains(schema), (), "Native advance schema differs")
      review <- fields("reviewContinuationIdentity").flatMap(_.asString).filter(canonical(_, 128))
        .toRight("Native advance review identity is invalid")
    } yield ContinuationIdentity(review)
    decoded.fold(Consequence.stateConflict(_), Consequence.success(_))
  }

  def bindC(params: ComponentCreate, declaration: GoalPhaseManagedStartAssembly.Declaration,
    selection: ProfileStartAdmission.CallScopedSelectionPort,
    artifact: CandidateAdmissionProducerAbi.Artifact, asOf: () => Instant,
    snapshot: GoalPhaseStepClosureBoundary.SnapshotPort, authority: GoalPhaseClosureAuthority.Ports,
    events: (ContinuationResult[ReviewStepResult], WorkflowCompletionServiceOperation.SelectedClosing) => Consequence[Vector[EventRecord]]
  )(using ExecutionContext): Consequence[spec.OperationDefinition] =
    GoalPhaseReviewRecoveryServiceOperation.bindNativeAdvanceC(params, declaration, selection,
      artifact, asOf, snapshot, authority, events)
}
