package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import domain.statemachine.SplitPhaseHumanDirectionLifecycle
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as NativeIdentity, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseManagedScopeProjectionCheckpoint as D,
  SplitPhaseScopeProjectionCheckpointPreparation as C, SplitPhasePlanningCheckpointPreparation as K,
  SplitPhaseScopeProjectionSnapshotJsonV1 as J,
  SplitPhaseExplicitPartitionSemantic as S, SplitPhaseExplicitPlanningAdvance as W}

/** Pure native Decision preparation from the owning whole-frontier observation.
  * Decoded input is passive data, never Human consent or an execution permit.
  *
  * @since Oct.  4, 2026
  * @version Oct. 11, 2026
  */
private[smworkflow] object SplitPhaseNativeHumanDirection {
  final case class FrontierPayload(value: String)
  final case class Input(context: S.ContextReference, checkpoint: K.Reference,
    scopeCheckpoint: C.Reference, frontier: FrontierPayload)
  final class Plan private[SplitPhaseNativeHumanDirection] (val source: D.Observed,
    val intent: WorkflowInstanceAtomicTransitionV1.SuspensionIntent, val next: InstanceRecord,
    val question: WorkflowInteraction[Input, Nothing], val wire: String)
  private final class Data(val intent: WorkflowInstanceAtomicTransitionV1.SuspensionIntent,
    val next: InstanceRecord, val question: WorkflowInteraction[Input, Nothing], val wire: String)

  val completionOperation = StateMachineOperationIdentity("SplitPhaseService", "submitSplitHumanDirection")
  val closingType = StateMachineResultTypeReference("SplitPhaseHumanDirectionSubmissionResult")
  val inputSchema = "sm-workflow.split-human-direction-input.v1"
  val choices = SplitPhaseHumanDirectionAnswerContract.choices

  val inputCodec: PayloadCodec[Input] = new PayloadCodec[Input] {
    val typeIdentity = "SplitPhaseHumanDirectionRequest"
    def encode(value: Input): Json = Json.obj("schemaVersion" -> Json.fromString(inputSchema), "payload" -> value.asJson)
    def decode(value: Json): Either[String, Input] = for {
      json <- Option(value).toRight("Human input is absent")
      root <- json.asObject.toRight("Human input is not an object")
      _ <- Either.cond(root("schemaVersion").flatMap(_.asString).contains(inputSchema), (), "Human input schema differs")
      payload <- root("payload").toRight("Human input payload is absent")
      input <- payload.as[Input].left.map(_.message)
    } yield input
  }

  /** Workflow selects the whole Human frontier. ALL-Built instead remains in
    * the existing deterministic continuation; it is not made Human work.
    */
  def prepareC(observed: D.Observed): Consequence[Option[Plan]] =
    if (observed == null) _invalid("owning published observation is absent")
    else W.continuePublishedScopeC(observed).flatMap {
      case _: W.PublishedHumanRequired => _prepare_c(observed).map(Some(_))
      case _: W.PublishedContinued => Consequence.success(None)
    }

  /** Consume the actual question selected by the owning storage lookup. */
  def consumeIssuedC(plan: Plan, question: WorkflowInteraction[Input, Nothing]): Consequence[Plan] =
    WorkflowDecisionJsonV1.encodeC(question, inputCodec).map { wire =>
      new Plan(plan.source, plan.intent, plan.next, question, wire)
    }

  private def _prepare_c(observed: D.Observed): Consequence[Plan] = for {
    data <- _prepare_data_c(observed.record)
  } yield new Plan(observed, data.intent, data.next, data.question, data.wire)

  private def _prepare_data_c(record: C.Record): Consequence[Data] = for {
    frontier <- J.encodeC(record.snapshot)
    source = record.snapshot.first
    input = Input(source.originalContext.reference, source.reference, record.reference, FrontierPayload(frontier))
    required <- SplitPhaseDefinitionBinding.humanDirectionOperationC
    provided = SplitPhaseDefinitionBinding.providedDefinition.providedOperations.filter(operation =>
      operation.service == completionOperation.service && operation.name == completionOperation.operation)
    _ <- if (required.inputType.map(_.value).contains(inputCodec.typeIdentity) &&
        required.resultType.map(_.value).contains("SplitPhaseHumanDirectionAnswer") && provided.size == 1 &&
        provided.head.inputType.contains("SplitPhaseHumanDirectionAnswer") && provided.head.resultType.contains(closingType.value))
      Consequence.unit else _invalid("generated Human Required/Provided contracts differ")
    base <- record.next.validateC
    _ <- if (base.lifecycle == Lifecycle.Active && base.suspension.isEmpty && base.revision.value < Long.MaxValue &&
        base.currentProgression.exists(value => Set("entry.EntryAssessment", "entry.ProposalAssessment", "entry.SemanticAssessment",
          "EntryPending", "ProposalPending", "SemanticPending").contains(value.value)) &&
        SplitPhaseHumanDirectionLifecycle.permits("Idle", "human-direction-issued", "Awaiting")) Consequence.unit
      else _invalid("published predecessor differs")
    identity = ContinuationIdentity("split-human:" + base.identity.value + ":" + base.revision.next.value.toString)
    bundle = ContextBundle("Human direction for the complete published SplitPhase frontier",
      Vector(ContextReference(input.context.identity, input.context.revision), ContextReference(input.checkpoint.identity, input.checkpoint.revision)),
      Vector(ContextReference(input.scopeCheckpoint.identity, input.scopeCheckpoint.revision),
        ContextReference(source.originalStart.reference.identity, source.originalStart.reference.revision)),
      ContextSnapshot(base.definition.workflowRevision.value, modelRevision = Some(source.originalContext.facts.proposal.source.reference.revision),
        evidenceRevision = Some(input.scopeCheckpoint.revision)))
    continuation = Continuation(StateMachineRunIdentity(base.identity.value), identity,
      StateMachineRevision(base.revision.next.value.toString), required, bundle)
    request = ContinuationRequest(continuation.runId, identity, continuation.expectedRevision, required.identity, required.operation,
      Some(TypedValue(inputCodec.typeIdentity, input)), required.resultType, bundle,
      required.metadata.completionContract, required.metadata.evidenceContract, completionOperation)
    presentation = Json.obj("schemaVersion" -> Json.fromString("sm-workflow.split-human-direction-presentation.v1"),
      "choices" -> Json.arr(choices.map(Json.fromString)*),
      "notice" -> Json.fromString("Resolution needs independent Human evidence and Workflow validation; no authority expansion or document application is granted."))
    question = WorkflowInteraction[Input, Nothing](source.originalContext.owner.handle,
      WorkflowContinuation.Decision(request, MinimalPresentation("SplitPhase Human direction", presentation.noSpaces)))
    entry = HistoryEntry(HistorySequence(base.history.size.toLong + 1L), base.revision.next, Lifecycle.Active,
      Some(ProgressionReference("HumanPending")), base.correlation, CausationReference(identity.value + ":issued"),
      base.derivedCompositeOccurrence, base.committedPredecessor,
      Some(SuspensionBoundary(NativeIdentity(identity.value), base.revision.next,
        ContextSnapshotReference(bundle.snapshot.workflowRevision), CompletionReference(request.completion.identity),
        EvidenceReference(request.evidence.identity), base.correlation)))
    intent = WorkflowInstanceAtomicTransitionV1.SuspensionIntent(WorkflowInstanceAtomicTransitionV1.schemaVersion,
      source.originalStart.configuration, base, base.revision, entry,
      ContinuationRuntimePersistence.Record(continuation, ContinuationRuntimePersistence.Status.Available))
    admitted <- WorkflowInstanceAtomicTransitionV1.admitSuspensionC(intent)
    wire <- WorkflowDecisionJsonV1.encodeC(question, inputCodec)
  } yield new Data(intent, admitted.next, question, wire)

  private def _invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("SplitPhase Human direction " + reason)
}
