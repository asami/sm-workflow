package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as NativeIdentity, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{SplitPhasePartitionContextReference}
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitReasoningSemantic as S,
  SplitPhaseExplicitReasoningSemanticJson as J, SplitPhasePlanningSnapshotJsonV1 as R,
  SplitPhaseHumanDirectionAnswerContract as A}

/** Separate reasoning Decision; native readers, not decoded payloads, establish
  * the saved AI completion and its original whole planning frontier.
  * @since Oct. 7, 2026
  * @version Oct. 14, 2026
  */
private[smworkflow] object SplitPhaseReasoningHumanDirection {
  final case class Input(workIdentity: ContinuationIdentity, request: S.Request,
    resultReference: ContextReference, judgment: S.Judgment, frontier: SplitPhasePlanningSnapshot.Record) {
    def context: SplitPhaseExplicitPartitionSemantic.ContextReference = request.context
  }
  final class Plan private[SplitPhaseReasoningHumanDirection] (
    val source: SplitPhaseManagedReasoningCompletion.Observed,
    val intent: WorkflowInstanceAtomicTransitionV1.SuspensionIntent,
    val next: InstanceRecord, val question: WorkflowInteraction[Input, Nothing])
  val completionOperation = SplitPhaseNativeHumanDirection.completionOperation
  val closingType = SplitPhaseNativeHumanDirection.closingType
  val schema = "sm-workflow.split-reasoning-human-input.v2"
  val inputCodec: WorkflowResultJsonV1.PayloadCodec[Input] = new WorkflowResultJsonV1.PayloadCodec[Input] {
    val typeIdentity = "SplitPhaseReasoningHumanDirectionRequest"
    def encode(value: Input): Json = Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> Json.obj(
      "workIdentity" -> Json.fromString(value.workIdentity.value), "request" -> J.requestCodec.encode(value.request),
      "resultReference" -> Json.obj("identity" -> Json.fromString(value.resultReference.identity),
        "revision" -> Json.fromString(value.resultReference.revision)),
      "judgment" -> J.resultCodec.encode(value.judgment), "frontier" -> R.payloadCodec.encode(value.frontier)))
    def decode(value: Json): Either[String, Input] = for {
      json <- Option(value).toRight("Reasoning Human input is absent")
      root <- json.asObject.toRight("Reasoning Human input is not an object")
      _ <- Either.cond(root("schemaVersion").flatMap(_.asString).contains(schema), (), "Reasoning Human schema differs")
      payload <- root("payload").toRight("Reasoning Human payload is absent")
      work <- payload.hcursor.get[String]("workIdentity").left.map(_.message)
      request <- payload.hcursor.get[Json]("request").left.map(_.message).flatMap(J.requestCodec.decode)
      identity <- payload.hcursor.downField("resultReference").get[String]("identity").left.map(_.message)
      revision <- payload.hcursor.downField("resultReference").get[String]("revision").left.map(_.message)
      judgment <- payload.hcursor.get[Json]("judgment").left.map(_.message).flatMap(J.resultCodec.decode)
      frontier <- payload.hcursor.get[Json]("frontier").left.map(_.message).flatMap(R.payloadCodec.decode)
    } yield Input(ContinuationIdentity(work), request, ContextReference(identity, revision), judgment, frontier)
  }

  def commandC(question: WorkflowInteraction[Input, Nothing]): Consequence[SplitPhaseAdvanceTarget] =
    question.current match {
      case decision: WorkflowContinuation.Decision[?] if decision.request.input.exists(v =>
          v.typeIdentity == inputCodec.typeIdentity && v.value.isInstanceOf[Input]) =>
        val input = decision.request.input.get.value.asInstanceOf[Input]
        Consequence.success(SplitPhaseAdvanceTarget(question.handle.instanceIdentity.value,
          SplitPhasePartitionContextReference(input.context.identity, input.context.revision)))
      case _ => _invalid("issued input differs")
    }

  def sourceC(command: SplitPhaseAdvanceTarget, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, core: ActionCall.Core): Consequence[Plan] = for {
    _ <- SplitPhaseReasoningInvocation.actorC(core, None)
    storage <- storageC(declaration, catalog, core)
    instances <- storage.bindInstancesC()(using core.executionContext)
    current <- instances.loadC(InstanceIdentity(command.instanceIdentity))(using core.executionContext)
      .flatMap(_.map(Consequence.success).getOrElse(_invalid("native instance is absent")))
    reader <- SplitPhaseExecutionFrontierDataStorePort.bindForCallC(core, declaration)
    revision = if (current.suspension.nonEmpty) InstanceRevision(current.revision.value - 1L) else current.revision
    frontier <- reader.loadForCallC(current.identity, revision, core).flatMap(
      _.map(Consequence.success).getOrElse(_invalid("reasoning business source is absent")))
    identity <- frontier match {
      case SplitPhaseExecutionFrontier.Value.ReasoningCompletion(work) => Consequence.success(work)
      case SplitPhaseExecutionFrontier.Value.ReasoningHumanAnswer(decision) => for {
        decisions <- storage.bindDecisionC(completionOperation, inputCodec)
        question <- decisions.loadC(decision)(using core.executionContext).flatMap(
          _.map(Consequence.success).getOrElse(_invalid("reasoning Human question is absent")))
        input <- NativeWorkflowOperationOutput.decisionInputC(question)
      } yield input.workIdentity
      case _ => _invalid("current business reference is not a reasoning Human source")
    }
    saved <- SplitPhaseManagedReasoningCompletion.observeForCallC(identity, declaration, catalog, core)
      .flatMap(_.map(Consequence.success).getOrElse(_invalid("canonical reasoning answer is absent")))
    _ <- if (saved.request.context ==
        SplitPhaseExplicitPartitionSemantic.ContextReference(command.context.identity, command.context.revision))
      Consequence.unit else _invalid("query crosses original context")
    plan <- prepareC(saved)
  } yield plan

  def prepareC(source: SplitPhaseManagedReasoningCompletion.Observed): Consequence[Plan] = for {
    _ <- if (source != null && source.completion.result.result.value.decision == S.Decision.DecisionNeeded && source.downstream.isEmpty)
      Consequence.unit else _invalid("saved result is not a genuine DecisionNeeded")
    base = source.next
    original = source.source
    input = Input(source.completion.result.continuationId, source.request,
      source.completion.result.resultReference, source.completion.result.result.value, original.snapshot)
    required <- SplitPhaseDefinitionBinding.reasoningHumanDirectionOperationC
    identity = ContinuationIdentity("split-reasoning-human:" + base.identity.value + ":" + base.revision.next.value.toString)
    bundle = ContextBundle("Human classification of the retained unresolved reasoning work",
      Vector(ContextReference(input.context.identity, input.context.revision), source.completion.result.resultReference),
      Vector(ContextReference(original.checkpoint.reference.identity, original.checkpoint.reference.revision)),
      source.completion.result.contextSnapshot)
    continuation = Continuation(StateMachineRunIdentity(base.identity.value), identity,
      StateMachineRevision(base.revision.next.value.toString), required, bundle)
    request = ContinuationRequest(continuation.runId, identity, continuation.expectedRevision, required.identity,
      required.operation, Some(TypedValue(inputCodec.typeIdentity, input)), required.resultType, bundle,
      required.metadata.completionContract, required.metadata.evidenceContract, completionOperation)
    question = WorkflowInteraction[Input, Nothing](source.completion.result.handle, WorkflowContinuation.Decision(request,
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
  } yield new Plan(source, intent, admitted.next, question)

  /** Wrap actual issued business input, without an expected-question certificate. */
  def consumeIssuedC(plan: Plan, question: WorkflowInteraction[Input, Nothing]): Consequence[Plan] =
    Consequence.success(new Plan(plan.source, plan.intent, plan.next, question))

  def advanceC(command: SplitPhaseAdvanceTarget, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, core: ActionCall.Core): Consequence[org.simplemodeling.textus.smworkflow.value.SplitPhaseAdvanceResult] =
    sourceC(command, declaration, catalog, core).flatMap(_advance_plan_c(_, declaration, catalog, core))

  def advanceObservedC(source: SplitPhaseManagedReasoningCompletion.Observed,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    core: ActionCall.Core): Consequence[org.simplemodeling.textus.smworkflow.value.SplitPhaseAdvanceResult] =
    prepareC(source).flatMap(_advance_plan_c(_, declaration, catalog, core))

  private def _advance_plan_c(plan: Plan, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, core: ActionCall.Core): Consequence[org.simplemodeling.textus.smworkflow.value.SplitPhaseAdvanceResult] = for {
    storage <- storageC(declaration, catalog, core)
    decisions <- storage.bindDecisionC(completionOperation, inputCodec)
    identity = plan.question.current.asInstanceOf[WorkflowContinuation.Decision[Input]].request.continuationId
    retained <- decisions.loadC(identity)(using core.executionContext)
    question <- retained match {
      case Some(value) =>
        Consequence.success(value)
      case None =>
        decisions.suspendC(plan.intent, plan.question)(using core.executionContext) match {
          case Consequence.Success(_) => Consequence.success(plan.question)
          case _ => _uncertain("question commit is not established; observe later without reissuing")
        }
    }
    wire <- NativeWorkflowOperationOutput.splitReasoningHumanC(question, core)
  } yield wire

  def storageC(declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, core: ActionCall.Core): Consequence[WorkflowComponentStorage] =
    WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
      SmWorkflowComponent.componentId, declaration.configuration, Vector(catalog.definition), declaration.instances, declaration.continuations)

  private def _invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("SplitPhase reasoning Human " + reason)
  private def _uncertain[A](reason: String): Consequence[A] = DataStoreConditionalTransitionFailure.transactionIndeterminate(reason)
}
