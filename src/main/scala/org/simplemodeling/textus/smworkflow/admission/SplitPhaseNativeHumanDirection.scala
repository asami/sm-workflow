package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json, parser}
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import domain.statemachine.SplitPhaseHumanDirectionLifecycle
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as NativeIdentity, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseManagedScopeProjectionCheckpoint as D,
  SplitPhaseScopeProjectionCheckpointPreparation as C, SplitPhasePlanningCheckpointPreparation as K,
  SplitPhaseScopeProjectionSnapshot as V, SplitPhaseScopeProjectionSnapshotJsonV1 as J,
  SplitPhaseExplicitPartitionSemantic as S, SplitPhaseExplicitPlanningAdvance as W}

/** Pure native Decision preparation from the owning whole-frontier observation.
  * Decoded input is passive data, never Human consent or an execution permit.
  *
  * @since Oct.  4, 2026
  * @version Oct.  5, 2026
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
  val maximumBytes = 1024 * 1024
  val choices = SplitPhaseHumanDirectionAnswerContract.choices

  val inputCodec: PayloadCodec[Input] = new PayloadCodec[Input] {
    val typeIdentity = "SplitPhaseHumanDirectionRequest"
    def encode(value: Input): Json = _json_c(value).toOption.getOrElse(
      throw new IllegalArgumentException("Invalid or oversized SplitPhase Human input"))
    def decode(value: Json): Either[String, Input] = for {
      json <- Option(value).filter(v => _bounded(v.noSpaces)).toRight("Human input is absent or oversized")
      root <- json.asObject.filter(_.keys.toSet == Set("schemaVersion", "payload")).toRight("Human input fields differ")
      _ <- Either.cond(root("schemaVersion").flatMap(_.asString).contains(inputSchema), (), "Human input schema differs")
      input <- root("payload").get.as[Input].left.map(_.message)
      canonical <- _json_c(input).toOption.toRight("Human frontier or purpose references differ")
      _ <- Either.cond(canonical == json, (), "Human nested fields differ")
    } yield input
  }

  /** Full passive snapshot admission; no native provenance is reconstructed. */
  def frontierC(input: Input): Consequence[V.Record] =
    if (input == null || input.context == null || input.checkpoint == null || input.scopeCheckpoint == null ||
        input.frontier == null || input.frontier.value == null || !input.frontier.value.startsWith(J.schema + ":") ||
        !_bounded(input.frontier.value)) _invalid("complete input is absent or oversized")
    else for {
      json <- parser.parse(input.frontier.value.substring(J.schema.length + 1)).fold(_ => _invalid("frontier JSON differs"), Consequence.success)
      snapshot <- J.payloadCodec.decode(json).fold(_ => _invalid("whole frontier differs"), Consequence.success)
      reference <- C.referenceC(snapshot.first.reference, SplitPhaseTypedScopeWorkIssue.identity(snapshot.first),
        ContextReference(snapshot.result.identity, snapshot.result.revision))
      _ <- if (snapshot.first.originalContext.reference == input.context && snapshot.first.reference == input.checkpoint &&
          reference == input.scopeCheckpoint) Consequence.unit else _invalid("original context or publication purpose differs")
    } yield snapshot

  /** Workflow selects the whole Human frontier. ALL-Built instead remains in
    * the existing deterministic continuation; it is not made Human work.
    */
  def prepareC(observed: D.Observed): Consequence[Option[Plan]] =
    if (observed == null) _invalid("owning published observation is absent")
    else W.continuePublishedScopeC(observed).flatMap {
      case _: W.PublishedHumanRequired => _prepare_c(observed).map(Some(_))
      case _: W.PublishedContinued => Consequence.success(None)
    }

  /** Passive stored joins only. This cannot construct Plan or native authority. */
  def inspectStoredC(scopeCheckpoint: C.Record, question: WorkflowInteraction[Input, Nothing],
    issued: InstanceRecord): Consequence[Unit] =
    if (scopeCheckpoint == null || question == null || issued == null) _invalid("stored question is incomplete")
    else _prepare_data_c(scopeCheckpoint).flatMap { expected =>
      val needed = scopeCheckpoint.snapshot.frontier match {
        case V.Frontier.HumanDirectionRequired => true
        case V.Frontier.Applied(candidates) => candidates.exists(candidate => candidate.output match {
          case _: V.CandidateOutput.Built => false
          case _ => true
        })
      }
      if (needed && expected.question == question && SplitPhaseDefinitionBinding.sameRecord(expected.next, issued))
        Consequence.unit else _invalid("stored complete question or issued history differs")
    }

  private def _prepare_c(observed: D.Observed): Consequence[Plan] = for {
    data <- _prepare_data_c(observed.record)
    base = observed.record.next
    _ <- if (observed.current.history.take(base.history.size) == base.history) Consequence.unit
      else _invalid("current complete prefix differs")
  } yield new Plan(observed, data.intent, data.next, data.question, data.wire)

  private def _prepare_data_c(record: C.Record): Consequence[Data] = for {
    frontier <- J.encodeC(record.snapshot)
    source = record.snapshot.first
    input = Input(source.originalContext.reference, source.reference, record.reference, FrontierPayload(frontier))
    _ <- frontierC(input)
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
    _ <- if (_bounded(wire)) Consequence.unit else _invalid("complete Decision exceeds the UTF-8 bound")
  } yield new Data(intent, admitted.next, question, wire)

  private def _json_c(input: Input): Consequence[Json] = frontierC(input).flatMap { _ =>
    val json = Json.obj("schemaVersion" -> Json.fromString(inputSchema), "payload" -> input.asJson)
    if (_bounded(json.noSpaces)) Consequence.success(json) else _invalid("complete input exceeds the UTF-8 bound")
  }
  private def _bounded(raw: String): Boolean = UTF_8.newEncoder().canEncode(raw) && raw.getBytes(UTF_8).length <= maximumBytes
  private def _invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("SplitPhase Human direction " + reason)
}
