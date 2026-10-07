package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*

/** Answerable contract, separate from immutable V1 stop-only metadata.
  * Workflow declares choices; neither a choice nor its decoding is a Git grant.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseHumanDecisionContract {
  val questionSchema = "sm-workflow.goal-phase-human-question.v1"
  val answerSchema = "sm-workflow.goal-phase-human-answer.v1"
  val resultType = "GoalPhaseHumanDecisionAnswer"
  final case class Choice(identity: String, label: String)
  final case class Answer(choiceIdentity: String)
  /** Native result payload only. Decoding supplies neither human admission nor
    * permission; the exact question must still admit the decoded choice.
    */
  val answerPayloadCodec: WorkflowResultJsonV1.PayloadCodec[Answer] =
    new WorkflowResultJsonV1.PayloadCodec[Answer] {
      val typeIdentity = resultType
      def encode(value: Answer): Json =
        encodeAnswerC(value).toOption.flatMap(wire => parse(wire).toOption).getOrElse(Json.Null)
      def decode(value: Json): Either[String, Answer] =
        if (value == null) Left("Human answer payload is absent")
        else decodeAnswerC(value.noSpaces).toOption.toRight("Human answer payload differs from its closed contract")
    }
  final case class Question(intent: GoalPhaseClosureAuthority.Intent,
    request: ContinuationRequest[GoalPhaseClosureAuthority.Input], choices: Vector[Choice],
    schemaVersion: String = questionSchema) {
    def validateC: Consequence[Unit] =
      if (schemaVersion != questionSchema || intent == null || request == null || choices == null || choices.size < 2 || choices.size > 16 ||
          choices.exists(value => value == null || !canonical(value.identity, 128) || !canonical(value.label, 512)) ||
          choices.map(_.identity).distinct.size != choices.size || intent.handle == null || intent.input == null ||
          intent.handle.instanceIdentity == null || intent.expectedRevision == null || intent.snapshot == null ||
          !canonical(intent.idempotencyKey, 256) ||
          !canonical(intent.input.reason, 256) || !canonical(intent.input.candidateRevision, 256) ||
          !canonical(intent.input.requestDigest, 256) ||
          request.context == null || request.input != Some(TypedValue(GoalPhaseClosureAuthority.inputCodec.typeIdentity, intent.input)) ||
          request.expectedRevision != intent.expectedRevision || request.context.snapshot != intent.snapshot ||
          request.runId == null || request.runId.value != intent.handle.instanceIdentity.value ||
          request.continuationId == null || !canonical(request.continuationId.value, 256) ||
          request.resultType != Some(StateMachineResultTypeReference(resultType)) ||
          request.completion != CompletionContract("human-decision-answer-v1", Vector.empty) ||
          request.evidence != EvidenceContract("independently-verified-human-interaction-v1", Vector.empty))
        Consequence.stateConflict("Human question differs from its admitted answerable contract")
      else for {
        _ <- if (request.completionOperation == GoalPhaseNativeHumanQuestion.completionOperation)
          GoalPhaseNativeHumanQuestion.admitRouteC(request)
        else if (request.completionOperation == StateMachineOperationIdentity("GoalPhaseService", "recordGoalPhaseHumanAnswer"))
          // Only the declared human completion is admitted; presentation still
          // cannot route to a semantic GoalPhase Operation.
          GoalPhaseClosureDecisionTemplate(request.requiredOperation, request.operation, request.operation).validateC
        else GoalPhaseClosureDecisionTemplate(request.requiredOperation, request.operation, request.completionOperation).validateC
        _ <- intent.handle.validateC
        _ <- StepCloseRequestAdapter.submissionC(intent.input.candidate)
        _ <- GoalPhaseClosureAuthority.inputCodec.decode(GoalPhaseClosureAuthority.inputCodec.encode(intent.input))
          .fold(Consequence.stateConflict(_), _ => Consequence.unit)
        _ <- if (request.context.requiredFacts == Vector(ContextReference(intent.input.candidate.stepIdentity, intent.input.candidateRevision)) &&
            request.context.references == Vector(ContextReference(intent.input.candidate.submissionIdentity, intent.input.requestDigest)))
          Consequence.unit else Consequence.stateConflict("Human question context differs from its intent")
        _ <- WorkflowDecisionJsonV1.encodeC(WorkflowInteraction[GoalPhaseClosureAuthority.Input, Nothing](intent.handle,
          WorkflowContinuation.Decision(request, MinimalPresentation("Human Decision", "Not command authorization"))),
          GoalPhaseClosureAuthority.inputCodec)
      } yield ()

    def admitAnswerC(answer: Answer): Consequence[Unit] = validateC.flatMap { _ =>
      if (answer != null && choices.exists(_.identity == answer.choiceIdentity)) Consequence.unit
      else Consequence.stateConflict("Human answer is not an exact declared choice")
    }
  }

  def createC(identity: String, intent: GoalPhaseClosureAuthority.Intent,
    routing: GoalPhaseClosureDecisionTemplate, choices: Vector[Choice]): Consequence[Question] =
    if (!canonical(identity, 256) || intent == null || intent.handle == null || intent.handle.instanceIdentity == null ||
        intent.input == null || intent.input.candidate == null || routing == null)
      Consequence.stateConflict("Human question requires a Workflow-allocated identity and routing")
    else {
      val request = ContinuationRequest(StateMachineRunIdentity(intent.handle.instanceIdentity.value), ContinuationIdentity(identity),
        intent.expectedRevision, routing.requiredOperation, routing.operation,
        Some(TypedValue(GoalPhaseClosureAuthority.inputCodec.typeIdentity, intent.input)),
        Some(StateMachineResultTypeReference(resultType)), ContextBundle("Select the exact declared answer",
          Vector(ContextReference(intent.input.candidate.stepIdentity, intent.input.candidateRevision)),
          Vector(ContextReference(intent.input.candidate.submissionIdentity, intent.input.requestDigest)), intent.snapshot),
        CompletionContract("human-decision-answer-v1", Vector.empty),
        EvidenceContract("independently-verified-human-interaction-v1", Vector.empty), routing.completionOperation)
      val question = Question(intent, request, choices)
      question.validateC.map(_ => question)
    }

  def encodeAnswerC(answer: Answer): Consequence[String] =
    if (answer == null || !canonical(answer.choiceIdentity, 128)) Consequence.stateConflict("Human answer is invalid")
    else Consequence.success(Json.obj("schemaVersion" -> Json.fromString(answerSchema),
      "choiceIdentity" -> Json.fromString(answer.choiceIdentity)).noSpaces)

  def decodeAnswerC(wire: String): Consequence[Answer] = {
    val decoded = for {
      text <- Option(wire).filter(_.length <= 2048).toRight("Human answer is missing or unbounded")
      json <- parse(text).left.map(_ => "Human answer JSON is invalid")
      fields <- json.asObject.filter(_.keys.toSet == Set("schemaVersion", "choiceIdentity")).toRight("Human answer fields differ")
      _ <- Either.cond(fields("schemaVersion").flatMap(_.asString).contains(answerSchema), (), "Human answer schema differs")
      choice <- fields("choiceIdentity").flatMap(_.asString).filter(canonical(_, 128)).toRight("Human answer choice is invalid")
    } yield Answer(choice)
    decoded.fold(Consequence.stateConflict(_), Consequence.success(_))
  }

  private[admission] def canonical(value: String, maximum: Int): Boolean =
    value != null && value.nonEmpty && value.length <= maximum && value.trim == value && !value.exists(_.isControl)
}
