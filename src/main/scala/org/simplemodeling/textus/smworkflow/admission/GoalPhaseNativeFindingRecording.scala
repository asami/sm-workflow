package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as NativeIdentity, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.Request as ProtocolRequest
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import GoalPhaseFindingRecording as R
import GoalPhaseNativeRun as F
import GoalPhaseNativeWork as S
import GoalPhaseOrderedProgression as O

/** Finite clerical work reconstructed from actual accepted review completions.
  * Canonical suspension/completion owns persistence; submission has no effects.
  * @since   Oct. 18, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseNativeFindingRecording {
  final case class Pending(sourceReference: ContextReference, sourceKind: String,
    stepIdentity: Option[String], observation: R.Observation)
  final case class Completed(request: R.Request, resultReference: ContextReference, result: R.Result)
  final case class Journaled(sourceRevision: String, committedRevision: String, pending: Vector[Pending],
    outcomes: Vector[DevelopmentJournalFinding.RecordingState])
  final case class State(pending: Vector[Pending] = Vector.empty, completed: Vector[Completed] = Vector.empty,
    journaled: Vector[Journaled] = Vector.empty)
  final case class Source(session: F.Session, from: InstanceRecord, controller: O.State,
    findings: State, issues: Option[GoalPhaseFixIssues.State])
  final case class Retained(completed: Completed, record: InstanceRecord, submitted: ContinuationResult[R.Result])
  val completionOperation = StateMachineOperationIdentity("GoalPhaseService", "submitGoalPhaseFindingRecording")
  val closingType = StateMachineResultTypeReference("GoalPhaseFindingRecordingSubmissionResult")

  private def _key(value: Pending) = (value.sourceReference.identity, value.sourceReference.revision, value.observation.identity)
  private def _pending(value: R.Item) = Pending(value.sourceReference, value.sourceKind, value.stepIdentity, value.observation)
  def accumulateC(state: State, input: S.Request, submitted: ContinuationResult[S.Result]): Consequence[State] = for {
    _ <- R.admitObservationsC(input.kind, submitted.result.value.reviewObservations)
    added = submitted.result.value.reviewObservations.toVector.flatMap(_.items).map(observation =>
      Pending(submitted.resultReference, input.kind, input.stepIdentity, observation))
    all = state.pending ++ state.completed.flatMap(_.request.items.map(_pending)) ++ state.journaled.flatMap(_.pending) ++ added
    _ <- if (all.map(_key).distinct.size == all.size) Consequence.unit
      else F.invalid("review observation source keys are duplicated or conflicting")
  } yield state.copy(pending = state.pending ++ added)

  def eligible(source: Source): Boolean = source.from.suspension.isEmpty && source.controller.closureIntent.isEmpty &&
    source.findings.pending.nonEmpty && (source.controller.stage match {
      case O.Stage.Implementation | O.Stage.PhaseInspection => source.controller.completed.lastOption.exists(last =>
        source.findings.pending.forall(p => p.sourceKind == "ReviewStep" && p.stepIdentity.contains(last.step)))
      case O.Stage.PhaseClosing => source.findings.pending.forall(p => p.sourceKind == "ReviewPhase" && p.stepIdentity.isEmpty)
      case _ => false
    })

  /** A blocking Phase report is not a recording frontier. Keep its original
    * observations through affected inspection/checks until Phase acceptance.
    * Step observations and mixed batches must still satisfy their own frontier.
    */
  def awaitsPhaseAcceptance(source: Source): Boolean = source.from.suspension.isEmpty &&
    source.controller.closureIntent.isEmpty && source.controller.releaseRepairActive &&
    source.controller.stage == O.Stage.PhaseInspection && source.findings.pending.nonEmpty &&
    source.findings.pending.forall(p => p.sourceKind == "ReviewPhase" && p.stepIdentity.isEmpty)
  def identity(source: Source): ContinuationIdentity =
    ContinuationIdentity(source.from.identity.value + ":ordered-recording:" + source.from.revision.value)

  /** Paths grant bounded management inclusion even for an honestly failed reply. */
  def managementPaths(state: State): Vector[String] = state.completed.flatMap(_.request.items.map(_.destination)).distinct
  def phasePaths(state: State): Vector[String] = state.completed.filter(_.request.stepIdentity.isEmpty)
    .flatMap(_.request.items.map(_.destination)).distinct
  def validatePathsC(paths: Vector[String], plan: O.Plan, fixed: Vector[String]): Consequence[Unit] =
    if (paths != null && paths.distinct == paths && paths.forall(p =>
        RepositorySyncNativeConflictInput.relative(p) && p.endsWith(".md") && fixed.contains(p) &&
          plan.steps.forall(_.ownedPaths.contains(p)))) Consequence.unit
    else F.invalid("recording paths must be literal Markdown destinations owned by every Step and the fixed host")

  def selectC(source: Source, d: F.Declaration): Consequence[Either[String, R.Request]] =
    if (!eligible(source)) F.invalid("recording requires its reviewed and committed Step or accepted Phase frontier")
    else if (source.findings.pending.size > 256) Consequence.success(Left(
      "The accumulated recording batch exceeds the bounded 256-item input. All pending observations are retained; no subset is issued."))
    else d.findingRecordingPolicy match {
      case None => Consequence.success(Left("Finding recording policy is unavailable. Pending review observations are retained."))
      case Some(policy) => for {
        _ <- policy.validateC(source.controller.plan, d.candidatePaths)
        _ <- validatePathsC(Vector(policy.hygiene, policy.developmentCandidate).distinct, source.controller.plan, d.candidatePaths)
        request = R.Request(source.from.identity.value, source.from.revision.value.toString, identity(source).value,
          source.controller.plan, source.controller.base, source.controller.completed,
          if (source.controller.stage == O.Stage.PhaseClosing) None else source.controller.completed.lastOption.map(_.step),
          source.findings.pending.map(p => R.Item(p.sourceReference, p.sourceKind, p.stepIdentity, p.observation,
            if (p.observation.category == "HYGIENE") policy.hygiene else policy.developmentCandidate)),
          source.issues, Some(GoalPhaseRepairProgress.snapshot(source.controller)))
        _ <- capturedC(source, d, request)
      } yield Right(request)
    }

  /** Captured destinations are checked against ownership, never a newer policy. */
  def capturedC(source: Source, d: F.Declaration, request: R.Request): Consequence[Unit] = for {
    _ <- R.validateRequestC(request)
    _ <- validatePathsC(request.items.map(_.destination).distinct, source.controller.plan, d.candidatePaths)
    _ <- if (eligible(source) && request.instanceIdentity == source.from.identity.value &&
        request.sourceRevision == source.from.revision.value.toString && request.batchIdentity == identity(source).value &&
        request.selectedPlan == source.controller.plan && request.originalBase == source.controller.base &&
        request.completed == source.controller.completed && request.items.map(_pending) == source.findings.pending &&
        request.requiredIssues == source.issues && request.repairProgress == Some(GoalPhaseRepairProgress.snapshot(source.controller)) &&
        request.stepIdentity == (if (source.controller.stage == O.Stage.PhaseClosing) None else source.controller.completed.lastOption.map(_.step)))
      Consequence.unit else F.invalid("saved recording request differs from its contiguous actual source")
  } yield ()

  def consumed(state: State, value: Completed): State = {
    val keys = value.request.items.map(_pending).map(_key).toSet
    state.copy(pending = state.pending.filterNot(p => keys(_key(p))), completed = state.completed :+ value)
  }

  def journaled(state: State, value: Journaled): State = {
    val keys = value.pending.map(_key).toSet
    state.copy(pending = state.pending.filterNot(p => keys(_key(p))), journaled = state.journaled :+ value)
  }

  def requiredC: Consequence[StateMachineRequiredOperation] = {
    val found = domain.statemachine.workflow.GoalPhaseWorkflowStateMachineWorkflow1.workflow.requiredOperations
      .filter(_.identity.capability == "goal-phase-finding-recording-capability")
    if (found.size != 1) F.invalid("recording Required SPI is absent or ambiguous")
    else {
      val r = found.head
      if (!r.inputType.exists(_.value == R.inputCodec.typeIdentity) || !r.resultType.exists(_.value == R.resultCodec.typeIdentity))
        F.invalid("recording Required SPI input/result identity differs")
      else Consequence.success(StateMachineRequiredOperation(StateMachineRequiredOperationIdentity(r.identity.capability), r.actionIdentity,
        StateMachineOperationIdentity(r.operation.service, r.operation.operation),
        r.inputType.map(v => StateMachineInputTypeReference(v.value)), r.resultType.map(v => StateMachineResultTypeReference(v.value)),
        StateMachineRequiredOperationMetadata(ContextContract("goal-phase-finding-recording-context-v1", Vector.empty, Vector.empty),
          CompletionContract("goal-phase-finding-recording-completion-v1", Vector.empty),
          EvidenceContract("goal-phase-finding-recording-evidence-v1", Vector.empty), Vector.empty)))
    }
  }

  final class Port(d: F.Declaration, definition: DefinitionBinding,
    locate: (String, ContinuationIdentity, ActionCall.Core) => Consequence[Source],
    resume: (Source, ActionCall.Core) => Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult],
    acknowledge: String => Consequence[Unit]) {
    def issuedC(source: Source, core: ActionCall.Core): Consequence[WorkflowInteraction[R.Request, Nothing]] = for {
      reader <- source.session.storage.bindIssuedC(completionOperation, R.inputCodec)
      work <- reader.loadC(identity(source))(using core.executionContext).flatMap(
        _.map(Consequence.success).getOrElse(F.invalid("actual recording suspension has no retained issued input")))
      request <- NativeWorkflowOperationOutput.workInputC(work)
      _ <- capturedC(source, d, request)
      required <- requiredC
      order <- work.current match {
        case value: WorkflowContinuation.WorkOrder[R.Request] => Consequence.success(value)
        case _ => F.invalid("actual recording issue is not a WorkOrder")
      }
      following <- GoalPhaseNativeHistory.nextC(source.session.current, source.from)
      pending <- following.map(entry => GoalPhaseNativeHistory.prefixC(source.session.current, entry.revision))
        .getOrElse(F.invalid("recording issue has no actual suspension"))
      _ <- if (pending.currentProgression == source.from.currentProgression && pending.suspension.exists(b =>
          b.continuationIdentity.value == identity(source).value && b.completion.value == required.metadata.completionContract.identity) &&
          pending.causation.value == "ordered-recording-issued:" + source.from.identity.value &&
          work.handle.instanceIdentity == source.from.identity && order.request.runId.value == source.from.identity.value &&
          order.request.continuationId == identity(source) && order.request.expectedRevision.value == pending.revision.value.toString &&
          order.request.requiredOperation == required.identity && order.request.operation == required.operation &&
          order.request.resultType == required.resultType && order.request.completionOperation == completionOperation)
        Consequence.unit else F.invalid("recording issue differs from its actual run, revision, SPI or operation")
    } yield work

    def retainedC(source: Source, core: ActionCall.Core): Consequence[Option[Retained]] = for {
      work <- issuedC(source, core)
      request <- NativeWorkflowOperationOutput.workInputC(work)
      native <- _native_c(source.session.storage)
      actual <- native.loadRetainedC(identity(source))(using core.executionContext)
      result <- actual match {
        case None => Consequence.success(Option.empty[Retained])
        case Some(value) => for {
          _ <- R.admitResultC(request, value.completion.result.result.value)
          pending <- GoalPhaseNativeHistory.prefixC(source.session.current, source.from.revision.next)
          next <- GoalPhaseNativeHistory.completionC(source.session.current, pending,
            value.completion.result.resultReference, source.controller.stage.toString)
        } yield Some(Retained(Completed(request, value.completion.result.resultReference,
          value.completion.result.result.value), next, value.completion.result))
      }
    } yield result

    def advanceC(source: Source, request: R.Request, core: ActionCall.Core):
      Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult] = for {
      _ <- capturedC(source, d, request)
      issued <- source.session.storage.bindIssuedC(completionOperation, R.inputCodec)
      existing <- issued.loadC(identity(source))(using core.executionContext)
      _ <- if (existing.isEmpty) Consequence.unit else F.invalid("recording issue has no corresponding actual pending frontier")
      required <- requiredC
      bundle = ContextBundle("Record only the assigned nonblocking review observations in the exact Markdown destinations. " +
        "Read the current files, preserve prior content, append source identities and rationale in meaningful sections, " +
        "and report honest file/section outcomes for every assigned item. File editing only; no implementation adoption, " +
        "commands, counters, Plan changes or recursive review. Partial or failed recording is terminal for this batch; no automatic retry.",
        request.items.map(_.sourceReference).distinct, Vector.empty,
        ContextSnapshot(source.from.definition.workflowRevision.value, evidenceRevision = Some(request.sourceRevision)))
      continuation = Continuation(StateMachineRunIdentity(source.from.identity.value), identity(source),
        StateMachineRevision(source.from.revision.next.value.toString), required, bundle)
      entry = HistoryEntry(HistorySequence(source.from.history.size.toLong + 1), source.from.revision.next, Lifecycle.Active,
        source.from.currentProgression, source.from.correlation, CausationReference("ordered-recording-issued:" + source.from.identity.value),
        source.from.derivedCompositeOccurrence, source.from.committedPredecessor,
        Some(SuspensionBoundary(NativeIdentity(identity(source).value), source.from.revision.next,
          ContextSnapshotReference(bundle.snapshot.workflowRevision), CompletionReference(required.metadata.completionContract.identity),
          EvidenceReference(required.metadata.evidenceContract.identity), source.from.correlation)))
      intent = WorkflowInstanceAtomicTransitionV1.SuspensionIntent(WorkflowInstanceAtomicTransitionV1.schemaVersion,
        d.configuration, source.from, source.from.revision, entry,
        ContinuationRuntimePersistence.Record(continuation, ContinuationRuntimePersistence.Status.Available))
      handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, source.from)
      draft = WorkflowInteraction[R.Request, Nothing](handle, WorkflowContinuation.WorkOrder(
        ContinuationRequest(continuation.runId, continuation.continuationId, continuation.expectedRevision, required.identity,
          required.operation, Some(TypedValue(R.inputCodec.typeIdentity, request)), required.resultType, bundle,
          required.metadata.completionContract, required.metadata.evidenceContract, completionOperation),
        ExecutionRequirement(Vector(CapabilityRequirement(required.identity.capability)), RiskLevel("SOURCE_IMPLEMENTATION"),
          ReasoningLevel.Standard, false), MinimalPresentation("Record accepted review observations",
          "Edit only the assigned Markdown files and return the finite typed recording result.")))
      work <- GoalPhaseNativeExecution.bindIssuedC(draft, d.executionProfiles, d.executionOrigin)
      _ <- issued.suspendC(intent, work)(using core.executionContext)
      output <- NativeWorkflowOperationOutput.goalRecordingRequestC(work, source.findings)
    } yield output

    def bindCompletionC(params: ComponentCreate): Consequence[spec.OperationDefinition] = for {
      storage <- WorkflowComponentStorage.bindC(params, SmWorkflowComponent.componentId, d.configuration,
        Vector(definition), d.instances, d.continuations)
      native <- _native_c(storage)
      declared <- _operation_c(None, native)
    } yield CallScopedOperationBinding.wrapResult(declared, (core, result) => F.bindC(d, definition, core)
      .flatMap(_native_c).flatMap(n => _operation_c(Some(core), n, Some(result))))

    private def _operation_c(core: Option[ActionCall.Core], native: WorkflowManagedCompletion[R.Result],
      result: Option[ContinuationResult[NativeWorkflowResultInput.Value]] = None): Consequence[spec.OperationDefinition] = {
      val provided = S.providedDefinition
      val operation = provided.providedOperations.find(o => o.service == completionOperation.service && o.name == completionOperation.operation)
      if (operation.isEmpty) return F.invalid("recording completion is absent from generated Provided ABI")
      val instances = CallScopedWorkflowInstances.managed(d.configuration, definition, d.instances, d.continuations)
      val issued = new IssuedWorkOrderPersistence[R.Request] {
        def putIfAbsentC(work: WorkflowInteraction[R.Request, Nothing]) = F.invalid("recording Submit cannot issue work")
        def loadC(id: ContinuationIdentity) = core match {
          case None => F.invalid("recording lookup requires the current owning call")
          case Some(call) => F.bindC(d, definition, call).flatMap(_.bindIssuedC(completionOperation, R.inputCodec))
            .flatMap(_.loadC(id)(using call.executionContext))
        }
      }
      final class RecordingCodec extends WorkflowCompletionServiceOperation.Codec[R.Result] {
        // This codec belongs to this invocation, never to a retained workflow.
        private var _admitted_request = Option.empty[R.Request]
        def capture(request: R.Request): Unit = { _admitted_request = Some(request) }
        val resultTypeIdentity = R.resultCodec.typeIdentity
        def decodeC(request: ProtocolRequest) = result.map(NativeWorkflowResultInput.typedC(_, R.resultCodec))
          .getOrElse(F.invalid("recording completion has no resolved result"))
        def encodeC(value: ContinuationRuntime.Resume) = F.invalid("recording acknowledgement requires committed completion")
        override def encodeAfterCommitC(value: ContinuationRuntime.Resume, submitted: ContinuationResult[R.Result],
          completed: Option[InstanceRecord]): Consequence[OperationResponse] = for {
          actual <- completed.map(Consequence.success).getOrElse(
            org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate("recording has no native committed state"))
          journal <- core match {
            case Some(call) => _admitted_request match {
              case Some(request) => DevelopmentJournal.recordAfterCommitC(d.journalPolicy, call, request, submitted, actual)
              case None => Consequence.success(org.simplemodeling.textus.smworkflow.value.JournalRecordingState(
                "indeterminate", "Journal has no admitted invocation request", None,
                org.simplemodeling.textus.smworkflow.value.WorkflowArtifactReference(submitted.resultReference.identity, submitted.resultReference.revision)))
            }
            case None => Consequence.success(org.simplemodeling.textus.smworkflow.value.JournalRecordingState(
              "unavailable", "Journal requires the owning committed call", None,
              org.simplemodeling.textus.smworkflow.value.WorkflowArtifactReference(submitted.resultReference.identity, submitted.resultReference.revision)))
          }
          _ <- acknowledge("goal-recording-completed")
        } yield NativeWorkflowOperationOutput.response(NativeWorkflowOperationOutput.accepted(submitted, actual, Some(journal)))
      }
      val codec = new RecordingCodec
      val closing = new WorkflowCompletionServiceOperation.ClosingProgram[R.Result] {
        def selectC(submitted: ContinuationResult[R.Result], current: InstanceRecord) = core match {
          case None => F.invalid("recording closing requires the current owning call")
          case Some(call) => for {
            actual <- issued.loadC(submitted.continuationId).flatMap(
              _.map(Consequence.success).getOrElse(F.invalid("recording result has no actual assigned work")))
            source <- locate(actual.handle.instanceIdentity.value, submitted.continuationId, call)
            work <- issuedC(source, call)
            request <- NativeWorkflowOperationOutput.workInputC(work)
            _ <- R.admitResultC(request, submitted.result.value)
            _ = codec.capture(request)
            pending <- GoalPhaseNativeHistory.prefixC(source.session.current, source.from.revision.next)
            next <- pending.appendC(pending.revision, F.history(pending, "phase." + source.controller.stage.toString,
              submitted.resultReference.identity))
          } yield WorkflowCompletionServiceOperation.SelectedClosing(
            ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
              ActionExecution.Completed(StateMachineOperationResult(closingType, submitted.resultReference))), Some(next))
        }
      }
      val actualinstances = core.fold[WorkflowInstancePersistence](instances)(CallScopedWorkflowInstances.forCall(instances, _))
      val declared = SmWorkflowComponent.GoalPhaseServiceService.SubmitGoalPhaseFindingRecordingOperation.specification
      WorkflowCompletionServiceOperation.bindC(provided, operation.get, declared.request, declared.response,
        codec, ContinuationRuntime.empty, issued, actualinstances, d.configuration,
        () => throw new IllegalStateException("Recording completion requires canonical managed UoW"), Some(closing), Some(native),
        descriptiveAttributes = declared.content.descriptiveAttributes)
    }
  }
  private def _native_c(storage: WorkflowComponentStorage) = storage.bindCompletionC(completionOperation,
    R.inputCodec, R.resultCodec, closingType, (_, _) => Consequence.success(Vector.empty))
}
