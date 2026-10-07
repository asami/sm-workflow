package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, parser}
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as NativeIdentity, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{SplitPhaseAdvanceCommand, SplitPhasePartitionContextReference}
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitReasoningSemantic as S,
  SplitPhaseExplicitReasoningSemanticJson as J, SplitPhasePlanningSnapshotJsonV1 as R,
  SplitPhaseHumanDirectionAnswerContract as A}

/** Separate reasoning Decision; native readers, not decoded payloads, establish
  * the saved AI completion and its original whole planning frontier.
  * @since Oct. 7, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseReasoningHumanDirection {
  final case class Input(context: SplitPhaseExplicitPartitionSemantic.ContextReference,
    reasoningWork: String, reasoningResult: String, frontier: String)
  final class Plan private[SplitPhaseReasoningHumanDirection] (
    val source: SplitPhaseManagedReasoningCompletion.Observed,
    val intent: WorkflowInstanceAtomicTransitionV1.SuspensionIntent,
    val next: InstanceRecord, val question: WorkflowInteraction[Input, Nothing])
  val completionOperation = SplitPhaseNativeHumanDirection.completionOperation
  val closingType = SplitPhaseNativeHumanDirection.closingType
  val schema = "sm-workflow.split-reasoning-human-input.v1"
  val inputCodec: WorkflowResultJsonV1.PayloadCodec[Input] = new WorkflowResultJsonV1.PayloadCodec[Input] {
    val typeIdentity = "SplitPhaseReasoningHumanDirectionRequest"
    def encode(value: Input): Json = _json_c(value).toOption.getOrElse(
      throw new IllegalArgumentException("Invalid reasoning Human input"))
    def decode(value: Json): Either[String, Input] = for {
      json <- Option(value).filter(v => _bounded(v.noSpaces)).toRight("Reasoning Human input is absent or oversized")
      root <- json.asObject.filter(_.keys.toSet == Set("schemaVersion", "payload")).toRight("Reasoning Human fields differ")
      _ <- Either.cond(root("schemaVersion").flatMap(_.asString).contains(schema), (), "Reasoning Human schema differs")
      input <- root("payload").get.as[Input].left.map(_.message)
      canonical <- _json_c(input).toOption.toRight("Reasoning Human source data differs")
      _ <- Either.cond(canonical == json, (), "Reasoning Human nested fields differ")
    } yield input
  }

  def commandC(question: WorkflowInteraction[Input, Nothing]): Consequence[SplitPhaseAdvanceCommand] =
    question.current match {
      case decision: WorkflowContinuation.Decision[?] if decision.request.input.exists(v =>
          v.typeIdentity == inputCodec.typeIdentity && v.value.isInstanceOf[Input]) =>
        val input = decision.request.input.get.value.asInstanceOf[Input]
        _json_c(input).map(_ => SplitPhaseAdvanceCommand(question.handle.instanceIdentity.value,
          SplitPhasePartitionContextReference(input.context.identity, input.context.revision)))
      case _ => _invalid("issued input differs")
    }

  def sourceC(command: SplitPhaseAdvanceCommand, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, core: ActionCall.Core): Consequence[Plan] = for {
    _ <- SplitPhaseReasoningInvocation.actorC(core, None)
    storage <- storageC(declaration, catalog, core)
    instances <- storage.bindInstancesC()(using core.executionContext)
    current <- instances.loadC(InstanceIdentity(command.instanceIdentity))(using core.executionContext)
      .flatMap(_.map(Consequence.success).getOrElse(_invalid("native instance is absent")))
    required <- SplitPhaseDefinitionBinding.reasoningSemanticOperationC
    identity <- current.history.reverseIterator.flatMap(_.suspension)
      .find(_.completion.value == required.metadata.completionContract.identity)
      .map(v => Consequence.success(ContinuationIdentity(v.continuationIdentity.value)))
      .getOrElse(_invalid("native reasoning assignment is absent"))
    saved <- SplitPhaseManagedReasoningCompletion.observeForCallC(identity, declaration, catalog, core)
      .flatMap(_.map(Consequence.success).getOrElse(_invalid("canonical reasoning answer is absent")))
    _ <- if (saved.plan.pending.issue.prepared.request.context ==
        SplitPhaseExplicitPartitionSemantic.ContextReference(command.context.identity, command.context.revision))
      Consequence.unit else _invalid("query crosses original context")
    plan <- prepareC(saved)
  } yield plan

  def prepareC(source: SplitPhaseManagedReasoningCompletion.Observed): Consequence[Plan] = for {
    _ <- if (source != null && source.plan.semantic.isInstanceOf[S.DecisionNeeded] && source.plan.downstream.isEmpty)
      Consequence.unit else _invalid("saved result is not a genuine DecisionNeeded")
    base = source.plan.next
    original = source.plan.pending.issue.source
    _ <- if (base.lifecycle == Lifecycle.Active && base.suspension.isEmpty && base.revision.value < Long.MaxValue &&
        source.current.history.take(base.history.size) == base.history) Consequence.unit
      else _invalid("saved completion native prefix differs")
    work <- J.encodeWorkOrderC(source.plan.pending.work)
    result <- WorkflowResultJsonV1.encodeC(source.plan.submitted, J.resultCodec)
    frontier <- R.encodeC(original.snapshot)
    input = Input(source.plan.pending.issue.prepared.request.context, work, result, frontier)
    _ <- _json_c(input)
    required <- SplitPhaseDefinitionBinding.reasoningHumanDirectionOperationC
    identity = ContinuationIdentity("split-reasoning-human:" + base.identity.value + ":" + base.revision.next.value.toString)
    bundle = ContextBundle("Human classification of the retained unresolved reasoning work",
      Vector(ContextReference(input.context.identity, input.context.revision), source.plan.submitted.resultReference),
      Vector(ContextReference(original.checkpoint.reference.identity, original.checkpoint.reference.revision)),
      source.plan.submitted.contextSnapshot)
    continuation = Continuation(StateMachineRunIdentity(base.identity.value), identity,
      StateMachineRevision(base.revision.next.value.toString), required, bundle)
    request = ContinuationRequest(continuation.runId, identity, continuation.expectedRevision, required.identity,
      required.operation, Some(TypedValue(inputCodec.typeIdentity, input)), required.resultType, bundle,
      required.metadata.completionContract, required.metadata.evidenceContract, completionOperation)
    question = WorkflowInteraction[Input, Nothing](source.plan.submitted.handle, WorkflowContinuation.Decision(request,
      MinimalPresentation("Reasoning Human direction", Json.obj("choices" -> Json.arr(A.choices.map(Json.fromString)*),
        "notice" -> Json.fromString("Return only Human direction; Workflow validates unknown-item coverage and calculates the successor.")).noSpaces)))
    entry = HistoryEntry(HistorySequence(base.history.size.toLong + 1L), base.revision.next, Lifecycle.Active,
      Some(ProgressionReference("HumanPending")), base.correlation, CausationReference(identity.value + ":issued"),
      base.derivedCompositeOccurrence, base.committedPredecessor,
      Some(SuspensionBoundary(NativeIdentity(identity.value), base.revision.next,
        ContextSnapshotReference(bundle.snapshot.workflowRevision), CompletionReference(request.completion.identity),
        EvidenceReference(request.evidence.identity), base.correlation)))
    intent = WorkflowInstanceAtomicTransitionV1.SuspensionIntent(WorkflowInstanceAtomicTransitionV1.schemaVersion,
      original.checkpoint.originalStart.configuration, base, base.revision, entry,
      ContinuationRuntimePersistence.Record(continuation, ContinuationRuntimePersistence.Status.Available))
    admitted <- WorkflowInstanceAtomicTransitionV1.admitSuspensionC(intent)
    _ <- WorkflowDecisionJsonV1.encodeC(question, inputCodec).flatMap(raw =>
      if (_bounded(raw)) Consequence.unit else _invalid("Decision exceeds its UTF-8 bound"))
  } yield new Plan(source, intent, admitted.next, question)

  def advanceC(command: SplitPhaseAdvanceCommand, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, core: ActionCall.Core): Consequence[String] = for {
    plan <- sourceC(command, declaration, catalog, core)
    storage <- storageC(declaration, catalog, core)
    decisions <- storage.bindDecisionC(completionOperation, inputCodec)
    identity = plan.question.current.asInstanceOf[WorkflowContinuation.Decision[Input]].request.continuationId
    retained <- decisions.loadC(identity)(using core.executionContext)
    question <- retained match {
      case Some(value) if value == plan.question && SplitPhaseDefinitionBinding.sameRecord(plan.source.current, plan.next) =>
        Consequence.success(value)
      case Some(_) => _invalid("actual question or current pending native state differs")
      case None if SplitPhaseDefinitionBinding.sameRecord(plan.source.current, plan.intent.current) =>
        decisions.suspendC(plan.intent, plan.question)(using core.executionContext) match {
          case Consequence.Success(committed) if SplitPhaseDefinitionBinding.sameRecord(committed.instance, plan.next) => for {
            saved <- decisions.loadC(identity)(using core.executionContext)
            current <- sourceC(command, declaration, catalog, core)
            _ <- if (saved.contains(plan.question) && current.question == plan.question &&
                SplitPhaseDefinitionBinding.sameRecord(current.source.current, plan.next)) Consequence.unit
              else _uncertain("issued question readback differs; observe later without reissuing")
          } yield saved.get
          case _ => _uncertain("question commit is not established; observe later without reissuing")
        }
      case None => _invalid("advanced history lacks its saved question")
    }
    wire <- WorkflowDecisionJsonV1.encodeC(question, inputCodec)
  } yield wire

  def storageC(declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, core: ActionCall.Core): Consequence[WorkflowComponentStorage] =
    WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
      SmWorkflowComponent.componentId, declaration.configuration, Vector(catalog.definition), declaration.instances, declaration.continuations)

  private def _json_c(input: Input): Consequence[Json] = for {
    _ <- if (input != null && input.context != null && Vector(input.reasoningWork, input.reasoningResult, input.frontier)
        .forall(v => v != null && _bounded(v))) Consequence.unit else _invalid("complete input is absent or oversized")
    work <- WorkflowWorkOrderJsonV1.decodeC(input.reasoningWork, J.requestCodec)
    result <- WorkflowResultJsonV1.decodeC(input.reasoningResult, J.resultCodec)
    _ <- WorkflowProtocolV1.admitResultC(work, result).map(_ => ())
    frontier <- R.decodeC(input.frontier)
    request = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value
    _ <- if (request.context == input.context && result.result.value.decision == S.Decision.DecisionNeeded &&
        result.result.value.assignments.isEmpty && frontier.selection.proposal.source.reference == request.source)
      Consequence.unit else _invalid("saved request, result or whole frontier crosses its reasoning source")
    json = Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> input.asJson)
    _ <- if (_bounded(json.noSpaces)) Consequence.unit else _invalid("complete input exceeds its UTF-8 bound")
  } yield json
  private def _bounded(raw: String): Boolean = UTF_8.newEncoder().canEncode(raw) && raw.getBytes(UTF_8).length <= 1024 * 1024
  private def _invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("SplitPhase reasoning Human " + reason)
  private def _uncertain[A](reason: String): Consequence[A] = DataStoreConditionalTransitionFailure.transactionIndeterminate(reason)
}
