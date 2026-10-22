package org.simplemodeling.textus.smworkflow.preparation

import io.circe.Json
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.{GoalPhaseOrderedHumanDecision, NativeExternalEvidence,
  NativeHumanDecisionScope, NativeSplitAnswerScope, RepositorySyncHumanConflict, SplitPhaseNativeHumanDirection,
  SplitPhaseReasoningHumanDirection, SplitPhaseHumanDirectionAnswerContract, ThreeWorkflowExternalEvidencePorts}
import org.simplemodeling.textus.smworkflow.value as V

/** Passive preparation of supplied Human direction using the existing v7 codec.
  * Native completion owns admission; the independent issuer owns provenance and provisioning.
  * @since   Oct. 14, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
object NativeHumanDecisionPreparation {
  final case class Prepared(recordIdentity: String, record: Record)

  def goalC(context: V.WorkflowHumanDecisionContext, answer: V.GoalPhaseOrderedDecisionAnswer,
    reference: V.WorkflowArtifactReference, execution: V.WorkflowExecutionEvidence,
    submittingParticipant: String, issuer: String, occurredAt: Instant): Consequence[Prepared] = {
    if (answer == null || !GoalPhaseOrderedHumanDecision.choices.contains(answer.choice)) {
      Consequence.stateConflict("Goal Human preparation requires a declared direction")
    } else _prepare_c(context, NativeExternalEvidence.Purpose.GoalHuman,
      Set(GoalPhaseOrderedHumanDecision.inputCodec.typeIdentity), GoalPhaseOrderedHumanDecision.answerCodec.typeIdentity,
      Json.obj("choice" -> Json.fromString(answer.choice)), reference, execution, submittingParticipant, issuer, occurredAt)
  }

  def repositoryC(context: V.WorkflowHumanDecisionContext, answer: V.RepositorySyncHumanConflictAnswer,
    reference: V.WorkflowArtifactReference, execution: V.WorkflowExecutionEvidence,
    submittingParticipant: String, issuer: String, occurredAt: Instant): Consequence[Prepared] = {
    if (answer == null || !RepositorySyncHumanConflict.choices.contains(answer.choice) ||
        (answer.choice == "ProvideResolution" && (answer.resolutions == null || answer.resolutions.exists(r =>
          r == null || r.conflictIdentity == null || r.text == null)))) {
      Consequence.stateConflict("Repository Human preparation requires the selected direction and literal resolutions")
    } else _prepare_c(context, NativeExternalEvidence.Purpose.RepositoryHuman,
      Set(RepositorySyncHumanConflict.inputCodec.typeIdentity), RepositorySyncHumanConflict.answerCodec.typeIdentity,
      Json.obj("choice" -> Json.fromString(answer.choice), "resolutions" -> Json.fromValues(
        if (answer.choice == "ProvideResolution") answer.resolutions.map(r => Json.obj(
          "conflictIdentity" -> Json.fromString(r.conflictIdentity), "text" -> Json.fromString(r.text))) else Vector.empty)),
      reference, execution, submittingParticipant, issuer, occurredAt)
  }

  def splitC(context: V.WorkflowHumanDecisionContext, answer: V.SplitPhaseHumanDirectionAnswer,
    reference: V.WorkflowArtifactReference, execution: V.WorkflowExecutionEvidence,
    submittingParticipant: String, issuer: String, occurredAt: Instant): Consequence[Prepared] = {
    if (answer == null || answer.choice == null) {
      return Consequence.stateConflict("Split Human preparation requires its typed answer")
    }
    val choice = answer.choice.value
    val native = choice match {
      case "ProvideResolution" => Option(answer.resolution).flatten.filter(_ != null).map(r =>
        SplitPhaseHumanDirectionAnswerContract.Answer.ProvideResolution(
          org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitPartitionSemantic.ContextReference(r.identity, r.revision)))
      case "Defer" => Some(SplitPhaseHumanDirectionAnswerContract.Answer.Defer)
      case "Cancel" => Some(SplitPhaseHumanDirectionAnswerContract.Answer.Cancel)
      case _ => None
    }
    for {
      actual <- native.map(Consequence.success).getOrElse(Consequence.stateConflict("Split Human preparation has no declared direction"))
      encoded <- SplitPhaseHumanDirectionAnswerContract.encodeC(actual)
      direction = Json.obj("choice" -> Json.fromString(choice), "resolution" ->
        encoded.hcursor.downField("payload").downField("resolution").focus.getOrElse(Json.Null))
      prepared <- _prepare_c(context, NativeExternalEvidence.Purpose.SplitAnswer,
        Set(SplitPhaseNativeHumanDirection.inputCodec.typeIdentity, SplitPhaseReasoningHumanDirection.inputCodec.typeIdentity),
        SplitPhaseHumanDirectionAnswerContract.resultType, direction, reference, execution, submittingParticipant, issuer, occurredAt)
    } yield prepared
  }

  private def _prepare_c(context: V.WorkflowHumanDecisionContext, purpose: NativeExternalEvidence.Purpose,
    inputs: Set[String], resultType: String, direction: Json, reference: V.WorkflowArtifactReference,
    execution: V.WorkflowExecutionEvidence, participant: String, issuer: String, occurredAt: Instant): Consequence[Prepared] = for {
    _ <- if (context != null && context.purpose == purpose.identity && _exact(context.componentInstance) &&
        context.question != null && reference != null && _exact(reference.identity) && _exact(reference.revision) &&
        execution != null && execution.workerIdentity != null && execution.workerIdentity.exists(_exact) &&
        _exact(participant) && _exact(issuer) && occurredAt != null) Consequence.unit
      else Consequence.stateConflict("Human preparation requires actual purpose, owner, question, reference, worker, submitter, issuer and time")
    raw <- context.question.toJsonStringC.flatMap(text => io.circe.parser.parse(text)
      .fold(error => Consequence.stateConflict(error.message), Consequence.success))
    wire = NativeHumanDecisionQuestionWire.normalize(raw, purpose)
    question <- (if (purpose == NativeExternalEvidence.Purpose.SplitAnswer) NativeSplitAnswerScope.selectQuestion(wire)
      else NativeHumanDecisionScope.selectQuestion(wire, purpose)).fold(Consequence.stateConflict(_), Consequence.success)
    _ <- if (purpose != NativeExternalEvidence.Purpose.GoalHuman) Consequence.unit
      else {
        val recovery = question.hcursor.downField("source").downField("sourceHistoryRecovery").focus.nonEmpty
        val choice = direction.hcursor.get[String]("choice").toOption
        if (choice.exists(value => if (recovery) Set("AuthorizeNewAttempt", "Defer")(value)
            else value != "AuthorizeNewAttempt")) Consequence.unit
        else Consequence.stateConflict("Goal Human direction differs from its recovery question")
      }
    _ <- if (question.hcursor.get[String]("inputType").toOption.exists(inputs) &&
        question.hcursor.get[String]("resultType").toOption.contains(resultType) &&
        Vector("runId", "continuationId", "requiredOperation", "inputType", "resultType").forall(k =>
          question.hcursor.get[String](k).toOption.exists(_exact)) &&
        Vector("handle", "operation", "completionOperation").forall(k =>
          question.hcursor.downField(k).focus.flatMap(_.asObject).exists(_.values.forall(_.asString.exists(_exact))))) Consequence.unit
      else Consequence.stateConflict("Human preparation requires matching actual native type and completion identity")
    answer = direction.mapObject(_.add("reference", Json.obj("identity" -> Json.fromString(reference.identity),
      "revision" -> Json.fromString(reference.revision))))
    binding = if (purpose == NativeExternalEvidence.Purpose.SplitAnswer) NativeSplitAnswerScope.binding(
      context.componentInstance, participant, question, answer) else NativeHumanDecisionScope.humanBinding(
      context.componentInstance, purpose, participant, question, answer.mapObject(_.add("worker", Json.fromString(execution.workerIdentity.get))))
    identity = ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision)
    encoded <- NativeExternalEvidence.encodeStorageC(NativeExternalEvidence.Evidence(NativeExternalEvidence.schema, purpose.identity,
      identity, reference.identity, reference.revision, issuer, execution.workerIdentity.get, binding, occurredAt.toString))
  } yield Prepared(NativeExternalEvidence.recordIdentity(purpose, identity), Record.data(NativeExternalEvidence.storageField -> encoded))

  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
}
