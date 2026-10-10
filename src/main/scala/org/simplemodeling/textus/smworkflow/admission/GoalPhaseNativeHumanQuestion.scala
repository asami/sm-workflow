package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import io.circe.Json
import io.circe.parser.parse
import domain.statemachine.workflow.GoalPhaseWorkflowStateMachineWorkflow1
import domain.statemachine.providedapi.GoalPhaseWorkflowStateMachineProvidedApi1
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as InstanceContinuationIdentity, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/** Pure native publication preparation. The caller must independently admit
  * the retained accepted review. This object grants no human/effect authority,
  * opens no store and never appends history independently.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseNativeHumanQuestion {
  import GoalPhaseHumanDecisionContract.*
  val completionOperation = StateMachineOperationIdentity("GoalPhaseService", "submitGoalPhaseHumanDecision")
  private val schema = "sm-workflow.goal-phase-native-human-presentation.v1"
  private val title = "GoalPhase human Decision"
  private val maximumBytes = 1024 * 1024
  final case class Prepared(question: Question, issued: WorkflowInteraction[GoalPhaseClosureAuthority.Input, Nothing],
    intent: WorkflowInstanceAtomicTransitionV1.SuspensionIntent, next: InstanceRecord)

  private def routingC: Consequence[GoalPhaseClosureDecisionTemplate] = {
    val generated = GoalPhaseWorkflowStateMachineWorkflow1.workflow
    val required = generated.requiredOperations.filter(_.identity.capability == "human-decision-capability")
    val provided = GoalPhaseWorkflowStateMachineProvidedApi1.workflow.providedOperations
      .filter(value => value.service == completionOperation.service && value.name == completionOperation.operation)
    if (required.size != 1 || provided.size != 1 || required.head.actionIdentity != "PresentHumanDecision" ||
        required.head.inputType.map(_.value) != Some(GoalPhaseClosureAuthority.inputCodec.typeIdentity) ||
        required.head.resultType.map(_.value) != Some(resultType) ||
        provided.head.inputType != Some(resultType) || provided.head.resultType != Some("GoalPhaseHumanAnswerResult"))
      Consequence.configurationInvalid("Native human route differs from generated GoalPhase ABI")
    else Consequence.success(GoalPhaseClosureDecisionTemplate(
      StateMachineRequiredOperationIdentity(required.head.identity.capability),
      StateMachineOperationIdentity(required.head.operation.service, required.head.operation.operation), completionOperation))
  }

  private[admission] def admitRouteC(request: ContinuationRequest[GoalPhaseClosureAuthority.Input]): Consequence[Unit] =
    routingC.flatMap { routing =>
      if (request != null && request.requiredOperation == routing.requiredOperation && request.operation == routing.operation &&
          request.completionOperation == routing.completionOperation) Consequence.unit
      else Consequence.stateConflict("Native human question routing differs from the generated declaration")
    }

  /** Choices and immutable intent identity travel inside the single issued
    * Decision payload, not an independently written shadow question record.
    */
  def projectC(question: Question): Consequence[WorkflowInteraction[GoalPhaseClosureAuthority.Input, Nothing]] =
    if (question == null) Consequence.stateConflict("Native human question is absent")
    else for {
      _ <- admitQuestionC(question)
      body = Json.obj("schemaVersion" -> Json.fromString(schema),
        "intentIdentity" -> Json.fromString(question.intent.idempotencyKey),
        "choices" -> Json.arr(question.choices.map(value => Json.obj(
          "identity" -> Json.fromString(value.identity), "label" -> Json.fromString(value.label)))*)).noSpaces
      issued = WorkflowInteraction[GoalPhaseClosureAuthority.Input, Nothing](question.intent.handle,
        WorkflowContinuation.Decision(question.request, MinimalPresentation(title, body)))
      wire <- WorkflowDecisionJsonV1.encodeC(issued, GoalPhaseClosureAuthority.inputCodec)
      _ <- if (wire.getBytes(StandardCharsets.UTF_8).length <= maximumBytes) Consequence.unit
        else Consequence.stateConflict("Native human question payload is unbounded")
    } yield issued

  def recoverC(issued: WorkflowInteraction[GoalPhaseClosureAuthority.Input, Nothing]): Consequence[Question] =
    if (issued == null) Consequence.stateConflict("Native human Decision is absent")
    else issued.current match {
      case WorkflowContinuation.Decision(request, presentation) if request != null && presentation != null &&
          presentation.title == title && presentation.currentSituation != null &&
          presentation.currentSituation.getBytes(StandardCharsets.UTF_8).length <= maximumBytes =>
        val decoded = for {
          json <- parse(presentation.currentSituation).left.map(_ => "Native human presentation is invalid")
          fields <- json.asObject.filter(_.keys.toSet == Set("schemaVersion", "intentIdentity", "choices"))
            .toRight("Native human presentation fields differ")
          _ <- Either.cond(fields("schemaVersion").flatMap(_.asString).contains(schema), (), "Native human presentation schema differs")
          identity <- fields("intentIdentity").flatMap(_.asString).filter(canonical(_, 256)).toRight("Native human intent identity is invalid")
          items <- fields("choices").flatMap(_.asArray).filter(values => values.size >= 2 && values.size <= 16)
            .toRight("Native human choices are unbounded")
          choices <- items.foldLeft[Either[String, Vector[Choice]]](Right(Vector.empty)) { (prior, item) => for {
            accumulated <- prior
            value <- item.asObject.filter(_.keys.toSet == Set("identity", "label")).toRight("Native human choice fields differ")
            id <- value("identity").flatMap(_.asString).toRight("Native human choice identity is absent")
            label <- value("label").flatMap(_.asString).toRight("Native human choice label is absent")
          } yield accumulated :+ Choice(id, label) }
        } yield (identity, choices)
        decoded.fold(Consequence.stateConflict(_), { case (identity, choices) => request.input match {
          case Some(input) if input.typeIdentity == GoalPhaseClosureAuthority.inputCodec.typeIdentity =>
            val intent = GoalPhaseClosureAuthority.Intent(issued.handle, request.expectedRevision,
              Option(request.context).map(_.snapshot).orNull, input.value, identity)
            val question = Question(intent, request, choices)
            admitQuestionC(question).map(_ => question)
          case _ => Consequence.stateConflict("Native human Decision has no typed intent")
        }})
      case _ => Consequence.stateConflict("Native human recovery requires an issued answerable Decision")
    }

  /** Validate the selected typed question, not equality to regenerated JSON or
    * presentation. Diagnostic summaries and JSON layout are not restart gates.
    */
  private def admitQuestionC(question: Question): Consequence[Unit] =
    question.validateC.flatMap { _ =>
      val generated = GoalPhaseWorkflowStateMachineWorkflow1.workflow
      if (question.request.completionOperation == completionOperation &&
          question.intent.handle.componentIdentity == SmWorkflowComponent.componentId &&
          question.intent.handle.workflowIdentity.value == generated.identity &&
          question.intent.handle.workflowRevision.value == generated.version &&
          question.request.continuationId.value.startsWith("goal-phase-native-human:")) Consequence.unit
      else Consequence.stateConflict("Native human question has a different execution reference or route")
    }

  /** Adjacency is necessary, not proof that reviewIntent came from an accepted
    * review. The managed current-call layer must supply that provenance.
    */
  def prepareC(configuration: Configuration, current: InstanceRecord, reviewIdentity: ContinuationIdentity,
    reviewIntent: GoalPhaseClosureAuthority.Intent, choices: Vector[Choice]): Consequence[Prepared] =
    if (configuration == null || current == null || reviewIntent == null || reviewIdentity == null ||
        !canonical(reviewIdentity.value, 128)) Consequence.stateConflict("Native human preparation is incomplete")
    else for {
      base <- current.validateC
      handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, base)
      _ <- if (base.lifecycle == Lifecycle.Active && base.suspension.isEmpty &&
          base.currentProgression.contains(ProgressionReference("step.Awaiting")) &&
          base.revision.value < Long.MaxValue && reviewIntent.handle == handle &&
          reviewIntent.expectedRevision == StateMachineRevision(base.revision.value.toString) &&
          reviewIntent.snapshot != null && reviewIntent.snapshot.workflowRevision == base.definition.workflowRevision.value)
        Consequence.unit else Consequence.stateConflict("Native human preparation is not adjacent to its reviewed base")
      nextRevision = base.revision.next
      identity = s"goal-phase-native-human:${base.identity.value}:${nextRevision.value}:${reviewIdentity.value}"
      intent = reviewIntent.copy(expectedRevision = StateMachineRevision(nextRevision.value.toString), idempotencyKey = identity)
      routing <- routingC
      question <- createC(identity, intent, routing, choices)
      issued <- projectC(question)
      required = StateMachineRequiredOperation(routing.requiredOperation, "PresentHumanDecision", routing.operation,
        Some(StateMachineInputTypeReference(GoalPhaseClosureAuthority.inputCodec.typeIdentity)), question.request.resultType,
        StateMachineRequiredOperationMetadata(ContextContract("native-human-context-v1", Vector.empty, Vector.empty),
          question.request.completion, question.request.evidence, Vector.empty))
      continuation = Continuation(question.request.runId, question.request.continuationId,
        question.request.expectedRevision, required, question.request.context)
      entry = HistoryEntry(HistorySequence(base.history.size.toLong + 1L), nextRevision, Lifecycle.Active,
        Some(ProgressionReference("HumanPending")), base.correlation, CausationReference(identity + ":issued"),
        base.derivedCompositeOccurrence, base.committedPredecessor,
        Some(SuspensionBoundary(InstanceContinuationIdentity(identity), nextRevision,
          ContextSnapshotReference(intent.snapshot.workflowRevision), CompletionReference(question.request.completion.identity),
          EvidenceReference(question.request.evidence.identity), CorrelationReference(identity))))
      suspension = WorkflowInstanceAtomicTransitionV1.SuspensionIntent(WorkflowInstanceAtomicTransitionV1.schemaVersion,
        configuration, base, base.revision, entry,
        ContinuationRuntimePersistence.Record(continuation, ContinuationRuntimePersistence.Status.Available))
      admitted <- WorkflowInstanceAtomicTransitionV1.admitSuspensionC(suspension)
    } yield Prepared(question, issued, suspension, admitted.next)
}
