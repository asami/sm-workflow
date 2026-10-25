package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as NativeIdentity, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Argument, Request as ProtocolRequest}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import GoalPhaseNativeRun as F
import GoalPhaseNativeWork as S
import GoalPhaseOrderedProgression as O
import GoalPhaseDocumentSource as D
import GoalPhaseDocumentProgress as P
import GoalPhaseOrderedHumanDecision as H
import GoalPhaseNativeFindingRecording as N
import GoalPhaseNativeRecordingCandidate as C
import GoalPhaseNativeReviewCompletion as Q

/** Ordered implementation, repair and review work and their provided result are installed
  * together. Canonical atomic suspension/completion owns all writes. No result
  * runs Git/SBT or turns implementation completion into Step/Phase acceptance.
  * @since   Oct.  6, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseNativeWorkContinuation {
  val closingType = StateMachineResultTypeReference("GoalPhaseWorkSubmissionResult")
  /** Constructed only while walking genuine accepted native closures. Decoding
    * an Admission never creates this ephemeral authority. */
  final case class AcceptedDependency private[GoalPhaseNativeWorkContinuation](
    capture: GoalPhaseDependencyValidation.Capture, closureRevision: String)
  private[GoalPhaseNativeWorkContinuation] final case class LegacyAdmission(session: String, plan: ContextReference, step: String,
    generation: Long, primaryBase: O.Snapshot, completionRevision: Long,
    admission: GoalPhaseDependencyValidation.Admission)
  final class DependencyChain private[GoalPhaseNativeWorkContinuation](val accepted: Vector[AcceptedDependency],
    private[GoalPhaseNativeWorkContinuation] val _legacy: Vector[LegacyAdmission]) {
    import GoalPhaseDependencyValidation as V
    private def _same(a: V.Binding, b: V.Binding): Boolean =
      a.requester == b.requester && a.dependency == b.dependency && a.root == b.root &&
        a.branch == b.branch && a.base == b.base
    private def _prior(binding: V.Binding): Vector[AcceptedDependency] =
      accepted.takeWhile(_.capture.step != binding.step).filter(a =>
        a.capture.admission.binding.dependency.identity == binding.dependency.identity ||
          a.capture.admission.binding.dependency.repository == binding.dependency.repository ||
          a.capture.admission.binding.dependency.workspace == binding.dependency.workspace ||
          a.capture.admission.binding.root == binding.root)
    def anchorC(binding: V.Binding): Consequence[Option[V.AcceptedPrefix]] = {
      val previous = _prior(binding)
      if (!V.bindingValid(binding) || previous.exists(a => !_same(a.capture.admission.binding, binding)))
        V.invalid("accepted dependency prefix crosses its Component, repository, workspace, root, branch or original base")
      else Consequence.success(previous.lastOption.map { last =>
        V.AcceptedPrefix(V.acceptedPrefixSchema, last.capture.session, last.capture.plan, last.capture.step,
          last.closureRevision, last.capture.candidate, previous.flatMap(_.capture.changedPaths).distinct)
      })
    }
    def resolveC(admission: V.Admission): Consequence[Unit] = for {
      anchor <- anchorC(admission.binding)
      _ <- if (V.admissionValid(admission) && (admission.acceptedPrefix == anchor ||
          (admission.acceptedPrefix.isEmpty && _legacy.exists(_.admission == admission))) &&
          accepted.find(_.capture.step == admission.binding.step).forall(_.capture.admission == admission)) Consequence.unit
        else V.invalid("admission has a missing, forged, skipped or crossed accepted native prefix")
    } yield ()
    def captureC(capture: V.Capture): Consequence[Unit] = for {
      _ <- resolveC(capture.admission)
      _ <- if (V.captureValid(capture) && accepted.find(_.capture.step == capture.step).forall(_.capture == capture) &&
          (capture.admission.acceptedPrefix.nonEmpty || _legacy.exists(e => e.admission == capture.admission &&
            e.session == capture.session && e.plan == capture.plan && e.step == capture.step &&
            e.generation == capture.generation && e.primaryBase == capture.primaryBase &&
            BigInt(capture.sourceRevision) >= e.completionRevision)) &&
          _prior(capture.admission.binding).forall(a => a.capture.session == capture.session &&
            a.capture.plan == capture.plan && a.capture.primaryBase == capture.primaryBase) &&
          (capture.admission.acceptedPrefix.nonEmpty || _prior(capture.admission.binding).forall(a =>
            a.capture.admission.acceptedPrefix.isEmpty && a.capture.admission.binding.base == capture.admission.binding.base &&
              a.capture.candidate == capture.candidate && a.capture.changedPaths == capture.changedPaths &&
              a.capture.diff == capture.diff))) Consequence.unit
        else V.invalid("dependency capture crosses its accepted Session, Plan, primary base or native candidate")
    } yield ()
  }
  private val _empty_dependency_chain = new DependencyChain(Vector.empty, Vector.empty)
  private final case class Prepared(source: F.Session, input: S.Request,
    intent: Option[WorkflowInstanceAtomicTransitionV1.SuspensionIntent], pending: InstanceRecord,
    work: WorkflowInteraction[S.Request, Nothing], controller: O.State,
    candidate: Option[GoalPhaseNativeCandidate.Saved], documentpolicy: Option[P.Policy] = None,
    findings: N.State = N.State(),
    dependencyApprovals: Vector[GoalPhaseDependencyValidation.Approval] = Vector.empty,
    dependencyAdmission: Option[GoalPhaseDependencyValidation.Admission] = None,
    dependencyBinding: Option[GoalPhaseDependencyValidation.Binding] = None,
    dependencyChain: DependencyChain = _empty_dependency_chain)
  private final case class Cursor(from: InstanceRecord, controller: O.State,
    documents: Option[D.Bound], candidate: Option[GoalPhaseNativeCandidate.Saved],
    validation: Option[GoalPhaseNativeValidation.Saved],
    lastresult: Option[(S.Request, ContinuationResult[S.Result])],
    decisioncontext: Option[S.DecisionContext], progress: Option[P.Projection] = None,
    documentpolicy: Option[P.Policy] = None, issues: Option[GoalPhaseFixIssues.State] = None,
    findings: N.State = N.State(), recordingCandidate: Option[C.Saved] = None,
    priorReviews: Vector[GoalPhaseNativeReview.Prior] = Vector.empty,
    completedCandidates: Vector[GoalPhaseNativeCandidate.Payload] = Vector.empty,
    inspectionSource: Option[InstanceRecord] = None,
    sourceHistoryRecoveryConsumed: Boolean = false,
    sourceHistoryRecovery: Option[GoalPhaseSourceHistoryRecovery.RecoveryTarget] = None,
    sourceHistoryContinuation: Option[GoalPhaseSourceHistoryRecovery.RecoveryTarget] = None,
    closureSource: Option[InstanceRecord] = None,
    candidateObservation: Option[GoalPhaseCandidateRecovery.RecoveryTarget] = None,
    candidateObservationContinuation: Option[GoalPhaseCandidateObservationContinuation.Target] = None,
    wholePhaseCandidateRecovery: Option[GoalPhaseWholePhaseCandidateRecovery.Target] = None,
    completionDirection: Option[GoalPhaseCompletionPolicyAdoption.Direction] = None,
    legacyClosure: Option[GoalPhaseNativeRepositoryClosure.Payload] = None,
    repositoryFrontier: Option[InstanceRecord] = None,
    dependencyApprovals: Vector[GoalPhaseDependencyValidation.Approval] = Vector.empty,
    dependencyAdmission: Option[GoalPhaseDependencyValidation.Admission] = None,
    completedDependencies: Vector[GoalPhaseNativeValidation.Payload] = Vector.empty,
    validationRepair: Option[(GoalPhaseValidationRepairAdoption.Source, GoalPhaseValidationRepairAdoption.Direction)] = None,
    dependencyChain: DependencyChain = _empty_dependency_chain)
  private sealed trait Located
  private final case class Frontier(cursor: Cursor) extends Located
  private final case class Work(prepared: Prepared) extends Located
  private final case class Human(source: H.Source, findings: N.State) extends Located
  private final case class LocatedRecording(source: N.Source) extends Located
  private final case class Paused(cursor: Cursor, reason: String, repository: Boolean = false) extends Located

  final class Port private[GoalPhaseNativeWorkContinuation](d: F.Declaration, definition: DefinitionBinding,
    acknowledgement: String => Consequence[Unit]) {
    def diagnosticInformationC(input: org.simplemodeling.textus.smworkflow.value.GoalPhaseDiagnosticInformationInput,
      core: ActionCall.Core): Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseDiagnosticInformationResult] = for {
      _ <- IncrementalSbtDiagnosisInformation.validateC(input)
      source <- F.retainedSessionC(input.issuedWorkContext.runId, d, definition, core)
      initial <- _initial_cursor_c(source)
      located <- _walk_c(source, initial, None, None, None, core, historyonly = true)
      prepared <- located match {
        case Work(value) if value.input.kind == "RepairStep" && value.input.checkFailure.nonEmpty => Consequence.success(value)
        case _ => F.invalid("Diagnostic information requires the current uncompleted RepairStep with retained check failure")
      }
      mode <- NativeWorkflowIssuedWorkContext.goalModeC(prepared.input.kind)
      actual <- NativeWorkflowIssuedWorkContext.fromIssuedC(prepared.work, mode, S.inputCodec)
      _ <- IncrementalSbtDiagnosisInformation.sameWorkC(actual, input.issuedWorkContext)
      result <- IncrementalSbtDiagnosisInformation.resolveC(d, core, prepared.input, input)
    } yield result

    def advanceC(identity: String, core: ActionCall.Core): Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult] = for {
      retained <- F.retainedSessionC(identity, d, definition, core)
      completed <- GoalPhasePostCompletionSync.retainedC(retained, d, core, acknowledgement)
      result <- completed match {
        case Some(value) => Consequence.success(value)
        case None => _advance_retained_c(identity, retained, core)
      }
    } yield result

    private def _advance_retained_c(identity: String, retained: F.Session, core: ActionCall.Core):
      Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult] = for {
      initial <- _initial_cursor_c(retained)
      located <- _walk_c(retained, initial, None, None, None, core, historyonly = true)
      result <- located match {
        case Frontier(cursor) if cursor.controller.stage == O.Stage.PhaseClosing &&
            GoalPhasePostCompletionSync.usesLocalPolicy(retained, cursor.completionDirection) =>
          _frontier_c(retained, cursor, core)
        // Admission and intent persistence must precede even the normal entry's Git observations.
        case Frontier(cursor) if cursor.candidateObservation.nonEmpty || cursor.wholePhaseCandidateRecovery.nonEmpty =>
          _dispatch_c(retained, cursor, core)
        case Frontier(cursor) if cursor.controller.stage == O.Stage.Implementation &&
            cursor.controller.repairDirective.exists(_.reason.startsWith("Human-requested reconciliation: ")) =>
          _dispatch_c(retained, cursor, core)
        case Work(plan) if plan.input.kind == "ImplementStep" &&
            plan.input.repairDirective.exists(_.reason.startsWith("Human-requested reconciliation: ")) =>
          NativeWorkflowOperationOutput.goalWorkRequestC(plan.work, core)
            .map(NativeWorkflowOperationOutput.withFindingRecording(_, plan.findings))
        case Paused(cursor, reason, false) if cursor.candidateObservation.nonEmpty || cursor.wholePhaseCandidateRecovery.nonEmpty =>
          _wait_cursor_c(cursor, reason)
        case _ => F.sessionC(identity, d, definition, core).flatMap(_advance_session_c(_, core))
      }
    } yield result

    def describeCompletionPolicyC(identity: String, core: ActionCall.Core):
      Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseCompletionPolicyDescriptionResult] =
      _completion_policy_source_c(identity, core).flatMap(GoalPhaseCompletionPolicyAdoption.describeC(_, core))

    def adoptCompletionPolicyC(input: org.simplemodeling.textus.smworkflow.value.GoalPhaseCompletionPolicyAdoptionInput,
      core: ActionCall.Core): Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseCompletionPolicyAdoptionResult] =
      _completion_policy_source_c(input.sessionId, core).flatMap(GoalPhaseCompletionPolicyAdoption.adoptC(_, d, core, input))

    private def _completion_policy_source_c(identity: String, core: ActionCall.Core):
      Consequence[GoalPhaseCompletionPolicyAdoption.Source] = for {
      source <- F.retainedSessionC(identity, d, definition, core)
      initial <- _initial_cursor_c(source)
      located <- _walk_c(source, initial, None, None, None, core, historyonly = true)
      cursor <- located match {
        case Frontier(value) if value.controller.stage == O.Stage.PhaseClosing && value.completionDirection.isEmpty =>
          Consequence.success(value)
        case Paused(value, _, true) if value.controller.stage == O.Stage.PhaseClosing && value.completionDirection.isEmpty =>
          Consequence.success(value)
        case _ => F.invalid("Completion policy adoption requires its original unsuspended PhaseClosing frontier")
      }
      _ <- if (cursor.from == source.current) Consequence.unit else F.invalid("Completion policy frontier is not current")
    } yield GoalPhaseCompletionPolicyAdoption.Source(source, cursor.from, cursor.controller, cursor.legacyClosure)

    def describeRemainingPlanC(input: org.simplemodeling.textus.smworkflow.value.GoalPhaseRemainingPlanDescriptionInput,
      core: ActionCall.Core): Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseRemainingPlanDescriptionResult] =
      _remaining_plan_source_c(input.sessionId, core).flatMap(GoalPhaseRemainingPlanAdoption.describeC(_, d, core, input.proposal))

    def adoptRemainingPlanC(input: org.simplemodeling.textus.smworkflow.value.GoalPhaseRemainingPlanAdoptionInput,
      core: ActionCall.Core): Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseRemainingPlanAdoptionResult] =
      _remaining_plan_source_c(input.sessionId, core).flatMap(GoalPhaseRemainingPlanAdoption.adoptC(_, d, core, input))

    def describeReviewPurposeRepairC(identity: String, core: ActionCall.Core):
      Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseReviewPurposeRepairDescriptionResult] =
      _purpose_repair_source_c(identity, core).flatMap(GoalPhaseLegacyPurposeRepairContinuation.describeC(_, core))

    def admitReviewPurposeRepairC(input: org.simplemodeling.textus.smworkflow.value.GoalPhaseReviewPurposeRepairInput,
      core: ActionCall.Core): Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseReviewPurposeRepairResult] =
      _purpose_repair_source_c(input.sessionId, core).flatMap(GoalPhaseLegacyPurposeRepairContinuation.admitC(_, d, core, input))

    private def _purpose_repair_source_c(identity: String, core: ActionCall.Core): Consequence[H.Source] = for {
      source <- F.retainedSessionC(identity, d, definition, core)
      initial <- _initial_cursor_c(source)
      located <- _walk_c(source, initial, None, None, None, core, historyonly = true)
      selected <- located match {
        case Human(value, _) => Consequence.success(value)
        case _ => F.invalid("purpose repair requires its current unanswered legacy Review Human frontier")
      }
    } yield selected

    def describeReleaseRepairContinuationC(identity: String, core: ActionCall.Core):
      Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseReleaseRepairContinuationDescriptionResult] =
      _purpose_repair_source_c(identity, core).flatMap(GoalPhaseReleaseRepairContinuation.describeC(_, d, core))

    def recoverReleaseRepairContinuationC(input: org.simplemodeling.textus.smworkflow.value.GoalPhaseReleaseRepairContinuationRecoveryInput,
      core: ActionCall.Core): Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseReleaseRepairContinuationRecoveryResult] =
      _purpose_repair_source_c(input.sessionId, core).flatMap(GoalPhaseReleaseRepairContinuation.recoverC(_, d, core, input))

    def describeFailedPhaseRepairC(input: org.simplemodeling.textus.smworkflow.value.GoalPhaseFailedPhaseRepairDescriptionInput,
      core: ActionCall.Core): Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseFailedPhaseRepairDescriptionResult] =
      _failed_phase_repair_source_c(input.sessionId, core)
        .flatMap(GoalPhaseFailedPhaseRepairAdoption.describeC(_, d, core, input.proposal))

    def describeValidationRepairC(input: org.simplemodeling.textus.smworkflow.value.GoalPhaseValidationRepairDescriptionInput,
      core: ActionCall.Core, readText: String => Consequence[String]):
      Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseValidationRepairDescriptionResult] =
      _validation_repair_source_c(input.sessionId, core).flatMap(GoalPhaseValidationRepairAdoption.describeC(_, d, core, input, readText))

    def adoptValidationRepairC(input: org.simplemodeling.textus.smworkflow.value.GoalPhaseValidationRepairAdoptionInput,
      core: ActionCall.Core, readText: String => Consequence[String]):
      Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseValidationRepairAdoptionResult] =
      _validation_repair_source_c(input.sessionId, core).flatMap(GoalPhaseValidationRepairAdoption.adoptC(_, d, core, input, readText))

    private def _validation_repair_source_c(identity: String, core: ActionCall.Core): Consequence[GoalPhaseValidationRepairAdoption.Source] = for {
      source <- F.retainedSessionC(identity, d, definition, core)
      initial <- _initial_cursor_c(source)
      located <- _walk_c(source, initial, None, None, None, core, historyonly = true)
      cursor <- located match {
        case Frontier(value) if value.controller.stage == O.Stage.Decision && value.validation.nonEmpty && value.candidate.nonEmpty => Consequence.success(value)
        case Paused(value, _, false) if value.controller.stage == O.Stage.PhaseValidation && value.validation.nonEmpty && value.candidate.nonEmpty => Consequence.success(value)
        case _ => F.invalid("Validation repair requires its original current whole-Phase incomplete Decision or latest pending intent")
      }
      _ <- if (cursor.from == source.current) Consequence.unit else F.invalid("Validation repair frontier is not current")
    } yield _validation_repair_source(source, cursor)

    private def _validation_repair_source(source: F.Session, cursor: Cursor): GoalPhaseValidationRepairAdoption.Source =
      GoalPhaseValidationRepairAdoption.Source(source, cursor.from, cursor.controller, cursor.candidate.get,
        cursor.validation.get, cursor.documents.nonEmpty, cursor.documentpolicy, N.managementPaths(cursor.findings))

    private def _resume_validation_repair_c(source: F.Session, cursor: Cursor, soughtwork: Option[ContinuationIdentity],
      soughthuman: Option[ContinuationIdentity], soughtrecording: Option[ContinuationIdentity], core: ActionCall.Core,
      historyonly: Boolean): Consequence[Located] = {
      val original = _validation_repair_source(source, cursor)
      for {
        retained <- GoalPhaseValidationRepairAdoption.retainedC(original, d, core)
        result <- _walk_c(source, cursor.copy(from = retained.record, controller = retained.controller,
          candidate = None, validation = None, decisioncontext = None, recordingCandidate = None,
          inspectionSource = None, lastresult = None, candidateObservation = None, candidateObservationContinuation = None,
          wholePhaseCandidateRecovery = None, sourceHistoryRecovery = None, sourceHistoryContinuation = None,
          validationRepair = Some(original -> retained.direction)), soughtwork, soughthuman, soughtrecording, core, historyonly)
      } yield result
    }

    def adoptFailedPhaseRepairC(input: org.simplemodeling.textus.smworkflow.value.GoalPhaseFailedPhaseRepairAdoptionInput,
      core: ActionCall.Core): Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseFailedPhaseRepairAdoptionResult] =
      _failed_phase_repair_source_c(input.sessionId, core)
        .flatMap(GoalPhaseFailedPhaseRepairAdoption.adoptC(_, d, core, input))

    private def _failed_phase_repair_source_c(identity: String, core: ActionCall.Core):
      Consequence[GoalPhaseFailedPhaseRepairAdoption.Source] = for {
      source <- F.retainedSessionC(identity, d, definition, core)
      initial <- _initial_cursor_c(source)
      located <- _walk_c(source, initial, None, None, None, core, historyonly = true)
      selected <- located match {
        case Frontier(cursor) if cursor.controller.stage == O.Stage.Decision &&
            cursor.controller.decisionSource.contains(O.Stage.PhaseValidation) &&
            cursor.candidate.nonEmpty && cursor.validation.nonEmpty => Consequence.success(
          GoalPhaseFailedPhaseRepairAdoption.Source(source, cursor.from, cursor.controller,
            cursor.candidate.get, cursor.validation.get, cursor.documents.nonEmpty))
        case _ => F.invalid("Phase repair adoption requires the exact original terminal Failed or confirmed timeout Phase Decision")
      }
    } yield selected

    private def _remaining_plan_source_c(identity: String, core: ActionCall.Core): Consequence[H.Source] = for {
      source <- F.retainedSessionC(identity, d, definition, core)
      initial <- _initial_cursor_c(source)
      located <- _walk_c(source, initial, None, None, None, core)
      selected <- located match {
        case Human(value, _) => Consequence.success(value)
        case _ => F.invalid("remaining Plan adoption requires its actual unanswered semantic Human frontier")
      }
    } yield selected

    def describeWholePhaseCandidateRecoveryC(identity: String, core: ActionCall.Core):
      Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseWholePhaseCandidateRecoveryDescriptionResult] = for {
      located <- _whole_phase_candidate_recovery_source_c(identity, core)
      (source, cursor, from) = located
      policy <- GoalPhaseWholePhaseCandidateRecovery.resolvePolicyC(d, core)
      // A history-only scaffold is never returned or admitted as a repository observation.
      tip = cursor.controller.tipSnapshot
      scaffold = GoalPhaseCandidateObservationContinuation.RepositorySnapshot(d.target.root.toString,
        d.target.localBranch, tip.head, tip.tree, Vector.empty, tip.tree)
      prefix <- GoalPhaseWholePhaseCandidateRecovery.historicalTargetC(source, d, core, from, cursor.controller,
        cursor.documentpolicy, N.managementPaths(cursor.findings), cursor.completedCandidates, scaffold, policy)
      _ <- GoalPhaseWholePhaseCandidateRecovery.requireFrontierC(source, prefix)
      snapshot <- GoalPhaseWholePhaseCandidateRecovery.observeRepositoryC(core, prefix)
      target = prefix.copy(repository = snapshot)
      _ <- GoalPhaseWholePhaseCandidateRecovery.requirePolicyC(d, core, target)
    } yield org.simplemodeling.textus.smworkflow.value.GoalPhaseWholePhaseCandidateRecoveryDescriptionResult(identity,
      "decision-required", GoalPhaseWholePhaseCandidateRecovery.message, core.component.get.core.instanceId.canonicalKey,
      GoalPhaseWholePhaseCandidateRecovery.targetRecord(target), GoalPhaseWholePhaseCandidateRecovery.choices,
      GoalPhaseWholePhaseCandidateRecovery.recoveryOperation)

    def recoverWholePhaseCandidateC(input: org.simplemodeling.textus.smworkflow.value.GoalPhaseWholePhaseCandidateRecoveryInput,
      core: ActionCall.Core): Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseWholePhaseCandidateRecoveryResult] = for {
      supplied <- GoalPhaseWholePhaseCandidateRecovery.targetC(input.target)
      located <- _whole_phase_candidate_recovery_source_c(input.sessionId, core)
      (source, cursor, from) = located
      policy <- GoalPhaseWholePhaseCandidateRecovery.resolvePolicyC(d, core)
      actual <- GoalPhaseWholePhaseCandidateRecovery.historicalTargetC(source, d, core, from, cursor.controller,
        cursor.documentpolicy, N.managementPaths(cursor.findings), cursor.completedCandidates, supplied.repository, policy)
      _ <- GoalPhaseWholePhaseCandidateRecovery.requireFrontierC(source, actual)
      _ <- if (actual == supplied) Consequence.unit else F.invalid("whole-Phase candidate recovery target or resolved host policy changed")
      _ <- GoalPhaseWholePhaseCandidateRecovery.admitC(d, core, actual, input, source.participantIdentity)
      _ <- if (input.choice == "Defer") Consequence.unit else if (input.choice == "AuthorizeNewObservation")
        GoalPhaseWholePhaseCandidateRecovery.appendC(source, d, core, from, actual,
          ContextReference(input.reference.identity, input.reference.revision), input.rationale, input.priorExecutionEnded)
        else F.invalid("whole-Phase candidate recovery choice is undeclared")
    } yield org.simplemodeling.textus.smworkflow.value.GoalPhaseWholePhaseCandidateRecoveryResult(input.sessionId, true,
      if (input.choice == "Defer") "deferred" else "new-observation-authorized",
      if (input.choice == "Defer") "Independent Human deferred. The original UNKNOWN history remains; no effect or transition is granted."
      else "Independent Human and prior-invocation-ended attestation admitted. Direction only was appended; a separate ordinary Advance may persist one fresh remaining observation intent. Original UNKNOWN history and all completed Steps remain retained.")

    private def _whole_phase_candidate_recovery_source_c(identity: String, core: ActionCall.Core):
      Consequence[(F.Session, Cursor, InstanceRecord)] = for {
      source <- F.retainedSessionC(identity, d, definition, core)
      initial <- _initial_cursor_c(source)
      located <- _walk_c(source, initial, None, None, None, core, historyonly = true)
      cursor <- located match {
        case Paused(value, _, false) if value.controller.stage == O.Stage.PhaseInspection &&
            value.controller.cursor == value.controller.plan.steps.size && value.inspectionSource.nonEmpty &&
            value.wholePhaseCandidateRecovery.isEmpty && value.candidate.isEmpty && value.validation.isEmpty => Consequence.success(value)
        case _ => F.invalid("whole-Phase recovery requires the original paused all-closed whole-Phase inspection frontier")
      }
    } yield (source, cursor, cursor.inspectionSource.get)

    def describeCandidateRecoveryC(identity: String, core: ActionCall.Core):
      Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseCandidateRecoveryDescriptionResult] = for {
      located <- _candidate_recovery_source_c(identity, core)
      (source, cursor, from) = located
      target <- GoalPhaseCandidateRecovery.currentTargetC(source, d, core, from, cursor.controller,
        cursor.documentpolicy, N.managementPaths(cursor.findings), cursor.completedCandidates)
    } yield org.simplemodeling.textus.smworkflow.value.GoalPhaseCandidateRecoveryDescriptionResult(identity,
      "decision-required", GoalPhaseCandidateRecovery.message, core.component.get.core.instanceId.canonicalKey,
      GoalPhaseCandidateRecovery.targetRecord(target), GoalPhaseCandidateRecovery.choicesFor(target), GoalPhaseCandidateRecovery.recoveryOperation)

    def recoverCandidateC(input: org.simplemodeling.textus.smworkflow.value.GoalPhaseCandidateRecoveryInput,
      core: ActionCall.Core): Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseCandidateRecoveryResult] = for {
      supplied <- GoalPhaseCandidateRecovery.targetC(input.target)
      located <- _candidate_recovery_source_c(input.sessionId, core)
      (source, cursor, from) = located
      actual <- GoalPhaseCandidateRecovery.currentTargetC(source, d, core, from, cursor.controller,
        cursor.documentpolicy, N.managementPaths(cursor.findings), cursor.completedCandidates)
      _ <- if (supplied == actual) Consequence.unit else F.invalid("candidate recovery crosses its exact current target")
      _ <- GoalPhaseCandidateRecovery.admitC(d, core, actual, input, source.participantIdentity)
      _ <- if (input.choice == "Defer") Consequence.unit else if (GoalPhaseCandidateRecovery.choicesFor(actual).contains(input.choice))
        GoalPhaseCandidateRecovery.appendC(source, d, core, from, actual,
          ContextReference(input.reference.identity, input.reference.revision), input.rationale, cursor.controller, input.choice)
        else F.invalid("candidate recovery choice is undeclared")
    } yield org.simplemodeling.textus.smworkflow.value.GoalPhaseCandidateRecoveryResult(input.sessionId, true,
      if (input.choice == "Defer") "deferred" else if (input.choice == "ReviseWork") "revision-work-authorized" else "new-observation-authorized",
      if (input.choice == "Defer") "Human deferred candidate recovery. The old observation remains unknown; no new observation is granted."
      else if (input.choice == "ReviseWork") "Independent same-Step revision direction was appended; all unknown effects and counters remain retained."
      else "Independent Human direction was appended. The old observation remains UNKNOWN with termination unconfirmed; one later ordinary Advance may perform only a new remaining candidate observation.")

    private def _candidate_recovery_source_c(identity: String, core: ActionCall.Core):
      Consequence[(F.Session, Cursor, InstanceRecord)] = for {
      source <- F.retainedSessionC(identity, d, definition, core)
      initial <- _initial_cursor_c(source)
      located <- _walk_c(source, initial, None, None, None, core, historyonly = true)
      cursor <- located match {
        case Paused(value, _, false) if value.controller.stage == O.Stage.InspectCandidate &&
            value.inspectionSource.nonEmpty && value.candidateObservation.isEmpty && !value.sourceHistoryRecoveryConsumed => Consequence.success(value)
        case _ => F.invalid("candidate recovery requires the original paused selected-Step complete staging frontier")
      }
    } yield (source, cursor, cursor.inspectionSource.get)

    def describeCandidateObservationContinuationC(identity: String, core: ActionCall.Core):
      Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseCandidateObservationContinuationDescriptionResult] = for {
      located <- _candidate_observation_continuation_source_c(identity, core)
      (source, cursor) = located
      budget <- org.goldenport.cncf.processexecution.GitDiffParameters.resolveC(core.executionContext)
      snapshot <- GoalPhaseCandidateObservationContinuation.observeRepositoryC(core, cursor.candidateObservation.get)
      dependency <- GoalPhaseCandidateRecovery.preparedPayload(cursor.candidateObservation.get).dependencyAdmission match {
        case None => Consequence.success(Option.empty[GoalPhaseCandidateObservationContinuation.RepositorySnapshot])
        case Some(value) => GoalPhaseNativeDependencyValidation.snapshotC(core, value, Some(cursor.dependencyChain)).map(Some(_))
      }
      target <- GoalPhaseCandidateObservationContinuation.currentTargetC(source, d, core, cursor.from, cursor.controller,
        cursor.candidateObservation.get, cursor.candidateObservationContinuation, budget, snapshot, dependency)
      _ <- GoalPhaseCandidateObservationContinuation.requireFrontierC(source, target)
    } yield org.simplemodeling.textus.smworkflow.value.GoalPhaseCandidateObservationContinuationDescriptionResult(identity,
      "decision-required", GoalPhaseCandidateObservationContinuation.message, core.component.get.core.instanceId.canonicalKey,
      GoalPhaseCandidateObservationContinuation.targetRecord(target), GoalPhaseCandidateObservationContinuation.choicesFor(target),
      GoalPhaseCandidateObservationContinuation.continuationOperation)

    def continueCandidateObservationC(input: org.simplemodeling.textus.smworkflow.value.GoalPhaseCandidateObservationContinuationInput,
      core: ActionCall.Core): Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseCandidateObservationContinuationResult] = for {
      supplied <- GoalPhaseCandidateObservationContinuation.targetC(input.target)
      located <- _candidate_observation_continuation_source_c(input.sessionId, core)
      (source, cursor) = located
      budget <- org.goldenport.cncf.processexecution.GitDiffParameters.resolveC(core.executionContext)
      _ <- GoalPhaseCandidateRecovery.preparedPayload(cursor.candidateObservation.get).dependencyAdmission
        .fold(Consequence.unit)(cursor.dependencyChain.resolveC)
      actual <- GoalPhaseCandidateObservationContinuation.currentTargetC(source, d, core, cursor.from, cursor.controller,
        cursor.candidateObservation.get, cursor.candidateObservationContinuation, budget, supplied.repository, supplied.dependencyRepository)
      _ <- GoalPhaseCandidateObservationContinuation.requireFrontierC(source, actual)
      _ <- if (supplied == actual) Consequence.unit else F.invalid("candidate observation continuation crosses its exact current target or selected budget")
      _ <- GoalPhaseCandidateObservationContinuation.admitC(d, core, actual, input, source.participantIdentity)
      _ <- if (input.choice == "Defer") Consequence.unit else if (
          GoalPhaseCandidateObservationContinuation.choicesFor(actual).contains(input.choice))
        GoalPhaseCandidateObservationContinuation.appendC(source, d, core, cursor.from, actual,
          ContextReference(input.reference.identity, input.reference.revision), input.rationale, cursor.controller, input.choice)
        else F.invalid("candidate observation continuation choice is undeclared")
    } yield org.simplemodeling.textus.smworkflow.value.GoalPhaseCandidateObservationContinuationResult(input.sessionId, true,
      if (input.choice == "Defer") "deferred" else if (input.choice == "ReviseWork") "revision-work-authorized" else "new-observation-authorized",
      if (input.choice == "Defer") "Fresh independent Human deferred. Every prior observation remains UNKNOWN with termination-unconfirmed; no observation is granted."
      else if (input.choice == "ReviseWork") "Fresh independent Human revision-work direction was appended without Git/File/SBT effects. Every prior observation remains UNKNOWN with termination-unconfirmed. A separate ordinary Advance may issue new semantic reconciliation work for the same Step; changed requirements require the existing decision-needed planRevision flow."
      else "Fresh independent Human direction was appended without Git/File/SBT effects. Every prior observation remains UNKNOWN with termination-unconfirmed. One later ordinary Advance may observe only the approved repository/index snapshot under the bound CNCF diff policy.")

    private def _candidate_observation_continuation_source_c(identity: String, core: ActionCall.Core):
      Consequence[(F.Session, Cursor)] = for {
      source <- F.retainedSessionC(identity, d, definition, core)
      initial <- _initial_cursor_c(source)
      located <- _walk_c(source, initial, None, None, None, core, historyonly = true)
      cursor <- located match {
        case Paused(value, _, false) if value.controller.stage == O.Stage.InspectCandidate &&
            value.candidateObservation.nonEmpty && value.candidate.isEmpty && value.validation.isEmpty => Consequence.success(value)
        case _ => F.invalid("candidate observation continuation requires an independently authorized observation with its final unresolved intent")
      }
      directionRevision = cursor.candidateObservationContinuation.map(_.frontierRevision)
        .getOrElse(cursor.candidateObservation.get.frontierRevision).toLong + 1
      direction <- GoalPhaseNativeHistory.prefixC(source.current, InstanceRevision(directionRevision))
      adapter <- source.storage.bindMechanicalC(F.advanceOperation, GoalPhaseNativeCandidate.codec)
      intent <- adapter.loadRetainedC(WorkflowMechanicalTransition.Reference(direction.identity.value +
        ":ordered-candidate:" + direction.revision.value + ":observation-intent", "1"))(using core.executionContext)
        .flatMap(_.map(Consequence.success).getOrElse(F.invalid("continuation has no retained observation intent")))
      record <- GoalPhaseCandidateRecovery.adjacentC(source.current, direction, intent.receipt.next,
        "ordered-candidate:observation-intent")
      dependency <- GoalPhaseCandidateRecovery.dependencyPrefixC(source, core, direction, record)
      _ <- if (dependency._1 == source.current) Consequence.unit
        else F.invalid("continuation requires the exact retained observation frontier")
    } yield (source, cursor.copy(from = direction))

    def describeSourceHistoryRecoveryC(identity: String, core: ActionCall.Core):
      Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseSourceHistoryRecoveryDescriptionResult] = for {
      located <- _source_history_recovery_source_c(identity, core)
      (source, cursor, from) = located
      target <- GoalPhaseSourceHistoryRecovery.currentTargetC(source, d, core, from, cursor.controller,
        cursor.documentpolicy, N.managementPaths(cursor.findings), cursor.completedCandidates)
    } yield org.simplemodeling.textus.smworkflow.value.GoalPhaseSourceHistoryRecoveryDescriptionResult(identity,
      "decision-required", GoalPhaseSourceHistoryRecovery.message, core.component.get.core.instanceId.canonicalKey,
      GoalPhaseSourceHistoryRecovery.targetRecord(target), GoalPhaseSourceHistoryRecovery.choices,
      GoalPhaseSourceHistoryRecovery.recoveryOperation)

    def recoverSourceHistoryC(input: org.simplemodeling.textus.smworkflow.value.GoalPhaseSourceHistoryRecoveryInput,
      core: ActionCall.Core): Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseSourceHistoryRecoveryResult] = for {
      supplied <- GoalPhaseSourceHistoryRecovery.targetC(input.target)
      located <- _source_history_recovery_source_c(input.sessionId, core)
      (source, cursor, from) = located
      actual <- GoalPhaseSourceHistoryRecovery.currentTargetC(source, d, core, from, cursor.controller,
        cursor.documentpolicy, N.managementPaths(cursor.findings), cursor.completedCandidates)
      _ <- if (supplied == actual) Consequence.unit else F.invalid("source history recovery crosses its exact current target")
      _ <- GoalPhaseSourceHistoryRecovery.admitC(d, core, actual, input, source.participantIdentity)
      _ <- if (input.choice == "Defer") Consequence.unit else if (input.choice == "AuthorizeNewAttempt")
        GoalPhaseSourceHistoryRecovery.appendC(source, d, core, from, actual,
          ContextReference(input.reference.identity, input.reference.revision), input.rationale)
        else F.invalid("source history recovery choice is undeclared")
    } yield org.simplemodeling.textus.smworkflow.value.GoalPhaseSourceHistoryRecoveryResult(input.sessionId, true,
      if (input.choice == "Defer") "deferred" else "new-attempt-authorized",
      if (input.choice == "Defer") "Human deferred source history recovery. The old unknown effect remains; no retry is granted."
      else "Independent Human direction was appended. The old effect remains unknown; a later ordinary Advance starts a new candidate attempt and may repeat source normalization.")

    private def _source_history_recovery_source_c(identity: String, core: ActionCall.Core):
      Consequence[(F.Session, Cursor, InstanceRecord)] = for {
      source <- F.retainedSessionC(identity, d, definition, core)
      initial <- _initial_cursor_c(source)
      located <- _walk_c(source, initial, None, None, None, core, historyonly = true)
      cursor <- located match {
        case Paused(value, _, false) if value.controller.stage == O.Stage.InspectCandidate &&
            value.inspectionSource.nonEmpty && !value.sourceHistoryRecoveryConsumed => Consequence.success(value)
        case _ => F.invalid("source history recovery requires the actual paused selected-Step unresolved history intent")
      }
    } yield (source, cursor, cursor.inspectionSource.get)

    def describeClosureRecoveryC(identity: String, core: ActionCall.Core):
      Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseClosureRecoveryDescriptionResult] = for {
      located <- _closure_recovery_source_c(identity, core)
      (source, cursor, from) = located
      target <- GoalPhaseClosureRecovery.currentTargetC(source, d, core, from, cursor.controller)
      granted <- d.closureInstallation.map(_.authority.isGrantedC(core, target.input))
        .getOrElse(Consequence.success(false))
      ready = granted && GoalPhaseClosureRecovery.continuationAction(target).nonEmpty
    } yield org.simplemodeling.textus.smworkflow.value.GoalPhaseClosureRecoveryDescriptionResult(identity,
      if (ready) "continuation-ready" else "decision-required",
      if (ready) GoalPhaseClosureRecovery.continuationMessage else GoalPhaseClosureRecovery.message,
      core.component.get.core.instanceId.canonicalKey, GoalPhaseClosureRecovery.targetRecord(target),
      if (ready) Vector.empty else GoalPhaseClosureRecovery.choices,
      if (ready) "advanceGoalPhase" else GoalPhaseClosureRecovery.recoveryOperation)

    def recoverClosureC(input: org.simplemodeling.textus.smworkflow.value.GoalPhaseClosureRecoveryInput,
      core: ActionCall.Core): Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseClosureRecoveryResult] = for {
      supplied <- GoalPhaseClosureRecovery.targetC(input.target)
      located <- _closure_recovery_source_c(input.sessionId, core)
      (source, cursor, from) = located
      actual <- GoalPhaseClosureRecovery.currentTargetC(source, d, core, from, cursor.controller)
      _ <- if (supplied == actual) Consequence.unit else F.invalid("closure recovery crosses its exact current target or selection")
      _ <- GoalPhaseClosureRecovery.admitC(d, core, actual, input, source.participantIdentity)
      _ <- if (input.choice == "Defer") Consequence.unit else GoalPhaseClosureRecovery.appendC(source, d, core, actual, input)
    } yield org.simplemodeling.textus.smworkflow.value.GoalPhaseClosureRecoveryResult(input.sessionId, true,
      if (input.choice == "Defer") "deferred" else if (input.choice == "ReconcileCommit") "reconciliation-authorized"
        else "new-attempt-authorized",
      if (input.choice == "Defer") "Human deferred closure recovery. Native history and the original unknown intent remain unchanged."
      else "Independent Human direction was appended with prior execution ended attestation. A separate ordinary Advance rechecks the exact observation and selection before continuing closure; old facts, accepted checks/review and counters remain retained.")

    private def _closure_recovery_source_c(identity: String, core: ActionCall.Core):
      Consequence[(F.Session, Cursor, InstanceRecord)] = for {
      source <- F.retainedSessionC(identity, d, definition, core)
      initial <- _initial_cursor_c(source)
      located <- _walk_c(source, initial, None, None, None, core, historyonly = true)
      cursor <- located match {
        case Paused(value, _, false) if value.controller.stage == O.Stage.Closing && value.closureSource.nonEmpty =>
          Consequence.success(value)
        case _ => F.invalid("closure recovery requires the actual paused selected-Step unresolved commit intent")
      }
    } yield (source, cursor, cursor.closureSource.get)

    def describeValidationRecoveryC(identity: String, core: ActionCall.Core):
      Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseValidationRecoveryDescriptionResult] = for {
      located <- _validation_recovery_source_c(identity, core)
      (source, cursor, frontier) = located
      target <- _validation_recovery_target_c(source, cursor, frontier, core)
    } yield org.simplemodeling.textus.smworkflow.value.GoalPhaseValidationRecoveryDescriptionResult(identity,
      "decision-required", GoalPhaseValidationRecovery.messageFor(target), core.component.get.core.instanceId.canonicalKey,
      GoalPhaseValidationRecovery.targetRecord(target), GoalPhaseValidationRecovery.choicesFor(target), GoalPhaseValidationRecovery.recoveryOperation)

    def recoverValidationC(input: org.simplemodeling.textus.smworkflow.value.GoalPhaseValidationRecoveryInput,
      core: ActionCall.Core): Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseValidationRecoveryResult] = for {
      supplied <- GoalPhaseValidationRecovery.targetC(input.target)
      located <- _validation_recovery_source_c(input.sessionId, core)
      (source, cursor, frontier) = located
      actual <- _validation_recovery_target_c(source, cursor, frontier, core)
      _ <- if (supplied == actual) Consequence.unit else F.invalid("Human new-attempt input crosses its exact current recovery target")
      _ <- if (GoalPhaseValidationRecovery.choicesFor(actual).contains(input.choice)) Consequence.unit
        else F.invalid("validation recovery choice is undeclared for the actual target")
      _ <- GoalPhaseValidationRecovery.admitC(d, core, actual, input, source.participantIdentity)
      _ <- if (input.choice == "Defer") Consequence.unit
      else if (input.choice == "LimitedRepair" && actual.terminatedTimeoutPhase.nonEmpty) Consequence.unit
      else if (input.choice == "AuthorizeNewAttempt" && actual.terminatedTimeoutPhase.nonEmpty)
        GoalPhaseTerminatedTimeoutValidationRecovery.appendC(source, d, core, cursor.from, cursor.controller,
          cursor.candidate.get, cursor.validation.get, cursor.documents.nonEmpty, actual,
          ContextReference(input.reference.identity, input.reference.revision), input.rationale)
      else if (input.choice == "AuthorizeNewAttempt" && actual.knownFailedPhase.nonEmpty)
        GoalPhaseKnownFailedValidationRecovery.appendC(source, d, core, cursor.from, cursor.controller,
          cursor.candidate.get, cursor.validation.get, actual,
          ContextReference(input.reference.identity, input.reference.revision), input.rationale)
      else if (input.choice == "AuthorizeNewAttempt") {
        val authorization = GoalPhaseNativeValidation.NewAttempt(actual.checkIdentity, actual.attempt, actual.intentRevision,
          input.reference.identity, input.reference.revision, source.participantIdentity, input.rationale)
        GoalPhaseNativeValidation.appendRecoveryC(source, d, core, cursor.candidate.get, cursor.controller, frontier,
          cursor.documentpolicy, N.managementPaths(cursor.findings), actual, authorization).map(_ => ())
      } else F.invalid("validation recovery choice is undeclared")
    } yield org.simplemodeling.textus.smworkflow.value.GoalPhaseValidationRecoveryResult(input.sessionId, true,
      if (input.choice == "Defer") "deferred"
      else if (input.choice == "LimitedRepair") "limited-repair-preparation-required" else "new-attempt-authorized",
      if (input.choice == "LimitedRepair") GoalPhaseValidationRecovery.limitedRepairMessage
      else if (actual.terminatedTimeoutPhase.nonEmpty && input.choice == "Defer")
        "Independent Human deferred the confirmed terminated timeout, permitting deeper investigation without retry. The original incomplete receipt/output remains unchanged; no new attempt is granted."
      else if (actual.terminatedTimeoutPhase.nonEmpty)
        GoalPhaseValidationRecovery.messageFor(actual)
      else if (actual.knownFailedPhase.nonEmpty && input.choice == "Defer")
        "Independent Human deferred the KNOWN FAILED whole-Phase check. Its terminated nonzero receipt remains; no fresh trial is granted."
      else if (actual.knownFailedPhase.nonEmpty)
        "Independent Human direction for the KNOWN FAILED whole-Phase check was appended. The terminated failed receipt remains intact. A later ordinary advanceGoalPhase runs one fresh trial on the same retained candidate; all completed Steps and repair counters remain."
      else if (input.choice == "Defer") "Independent Human deferred validation recovery. The old unknown outcome and intent remain; no retry is granted."
      else "Independent HumanNewAttempt authorization was appended. The old outcome remains unknown and every prior fact, Plan and repair count remains. Call ordinary advanceGoalPhase to attempt the same selected check under current host permissions.")

    private def _validation_recovery_source_c(identity: String, core: ActionCall.Core):
      Consequence[(F.Session, Cursor, InstanceRecord)] = for {
      source <- F.retainedSessionC(identity, d, definition, core)
      initial <- _initial_cursor_c(source)
      located <- _walk_c(source, initial, None, None, None, core, historyonly = true)
      cursor <- located match {
        case Paused(value, _, false) if Set(O.Stage.Validation, O.Stage.PhaseValidation)(value.controller.stage) &&
            value.validation.nonEmpty && value.candidate.nonEmpty => Consequence.success(value)
        case Frontier(value) if value.controller.stage == O.Stage.Decision &&
            value.controller.decisionSource.contains(O.Stage.PhaseValidation) && value.validation.nonEmpty &&
            value.candidate.nonEmpty => Consequence.success(value)
        case _ => F.invalid("validation recovery requires an unresolved Validation check or an actual terminal Failed/confirmed timeout whole-Phase Decision")
      }
      revision <- cursor.validation.get.payload.sourceRevision.toLongOption
        .filter(n => n > 0 && n.toString == cursor.validation.get.payload.sourceRevision)
        .map(n => Consequence.success(InstanceRevision(n))).getOrElse(F.invalid("validation recovery source revision is invalid"))
      // Paused.from is the current native record. The original Validation source
      // remains the payload's admitted history prefix, including selected metadata.
      frontier <- GoalPhaseNativeHistory.prefixC(source.current, revision)
    } yield (source, cursor, frontier)

    private def _validation_recovery_target_c(source: F.Session, cursor: Cursor, frontier: InstanceRecord,
      core: ActionCall.Core): Consequence[GoalPhaseValidationRecovery.RecoveryTarget] =
      if (cursor.controller.stage == O.Stage.Decision) GoalPhaseNativeValidation.terminalOutcome(cursor.validation.get.payload) match {
        case Some(GoalPhaseNativeValidation.TerminalOutcome.TerminatedTimeout) =>
          GoalPhaseTerminatedTimeoutValidationRecovery.currentTargetC(source, d, core, cursor.from,
            cursor.controller, cursor.candidate.get, cursor.validation.get, cursor.documents.nonEmpty)
        case Some(GoalPhaseNativeValidation.TerminalOutcome.Failed) =>
          GoalPhaseKnownFailedValidationRecovery.currentTargetC(source, d, core, cursor.from,
            cursor.controller, cursor.candidate.get, cursor.validation.get)
        case None => F.invalid("Final Phase recovery requires a genuine terminal Failed or confirmed timeout source")
      }
      else GoalPhaseNativeValidation.recoveryTargetC(source, d, core, cursor.candidate.get,
        cursor.controller, frontier, cursor.documentpolicy, N.managementPaths(cursor.findings))

    private def _initial_cursor_c(source: F.Session): Consequence[Cursor] = for {
      from <- GoalPhaseNativeHistory.prefixC(source.current, source.started.receipt.next.revision)
      controller <- O.startC(source.started.receipt.payload.value.plan, source.started.receipt.payload.value.base)
    } yield Cursor(from, controller, source.started.receipt.payload.value.documents, None, None, None, None)

    /** The sole read-only traversal follows only contiguous actual predecessors. */
    private def _walk_c(source: F.Session, cursor: Cursor, soughtwork: Option[ContinuationIdentity],
      soughthuman: Option[ContinuationIdentity], soughtrecording: Option[ContinuationIdentity], core: ActionCall.Core,
      historyonly: Boolean = false): Consequence[Located] = for {
      following <- GoalPhaseNativeHistory.nextC(source.current, cursor.from)
      located <- following match {
        case Some(entry) if Set("ordered-journal-findings-intent", "ordered-journal-findings-result")(entry.causation.value) => for {
          retained <- GoalPhaseNativeJournalFinding.retainedC(_recording_source(source, cursor), d, core)
            .flatMap(_.map(Consequence.success).getOrElse(F.invalid("actual Journal Finding history has no retained intent")))
          result <- if (!retained.policyMatches) _paused_c(source, cursor,
            "Journal Finding policy conflicts with the retained intent. No automatic replay or destination change.")
          else retained.payload.outcomes match {
            case None => _paused_c(source, cursor,
              "Journal Finding intent has an unknown recording outcome. No automatic append replay or further work.")
            case Some(_) => _walk_c(source, cursor.copy(from = retained.record,
              findings = N.journaled(cursor.findings, GoalPhaseNativeJournalFinding.completed(retained))),
              soughtwork, soughthuman, soughtrecording, core, historyonly)
          }
        } yield result
        case Some(entry) if Set("ordered-document-progress-intent", "ordered-document-progress-result")(entry.causation.value) =>
          cursor.documents match {
            case None => F.invalid("document progress has no actual selected source")
            case Some(documents) => for {
              retained <- GoalPhaseNativeDocumentProgress.retainedC(source, d, core, cursor.from,
                cursor.controller, documents, cursor.progress).flatMap(_.map(Consequence.success)
                  .getOrElse(F.invalid("actual progress history has no retained intent")))
              result <- retained.payload.outcome match {
                case Some("updated") => for {
                  policy <- P.policyC(cursor.controller.plan, documents, d.candidatePaths)
                  result <- _walk_c(source, cursor.copy(from = retained.record, progress = Some(retained.payload.desired),
                    documentpolicy = Some(policy)), soughtwork, soughthuman, soughtrecording, core, historyonly)
                } yield result
                case Some(conflict) => Consequence.success[Located](Paused(cursor.copy(from = retained.record),
                  "Document progress File conflict recorded: " + conflict + ". No automatic retry or rewrite."))
                case None => _paused_c(source, cursor,
                  "Document progress intent has an unknown File effect. No automatic replay or further work.")
              }
            } yield result
          }
        case Some(entry) if Set("ordered-document-plan-selected", "ordered-document-plan-conflicted")(entry.causation.value) =>
          cursor.documents match {
            case None => F.invalid("document selection has no original selected source")
            case Some(previous) => for {
              selected <- GoalPhaseNativeDocumentPlan.retainedC(source, core, cursor.from, cursor.controller, previous)
                .flatMap(_.map(Consequence.success).getOrElse(F.invalid("actual document transition has no retained business selection")))
              result <- _walk_c(source, _selected_cursor(cursor, selected), soughtwork, soughthuman, soughtrecording, core, historyonly)
            } yield result
          }
        case Some(entry) if entry.suspension.nonEmpty => for {
          pending <- GoalPhaseNativeHistory.prefixC(source.current, entry.revision)
          boundary = entry.suspension.get
          recording <- N.requiredC
          human <- H.requiredC
          result <- if (boundary.completion.value == recording.metadata.completionContract.identity) {
            val recordingsource = _recording_source(source, cursor)
            if (soughtrecording.contains(N.identity(recordingsource)))
              _recording_port.issuedC(recordingsource, core).map(_ => LocatedRecording(recordingsource): Located)
            else _recording_port.retainedC(recordingsource, core).flatMap {
              case None => Consequence.success[Located](LocatedRecording(recordingsource))
              case Some(value) => _walk_c(source, cursor.copy(from = value.record,
                findings = N.consumed(cursor.findings, value.completed)), soughtwork, soughthuman, soughtrecording, core, historyonly)
            }
          } else if (boundary.completion.value == human.metadata.completionContract.identity) for {
            decision <- _human_source_c(source, cursor)
            _ <- if (boundary.continuationIdentity.value == H.identity(decision).value) Consequence.unit
              else F.invalid("actual Human suspension differs from its Decision source")
            port <- _human_port_c
            adopted <- if (decision.sourceHistoryRecovery.nonEmpty) Consequence.success(Option.empty[GoalPhaseRemainingPlanAdoption.Selected])
              else GoalPhaseRemainingPlanAdoption.retainedC(decision, core, Some(d))
            result <- adopted match {
              case Some(value) => for {
                policy <- value.documents.map(documents => P.policyC(value.controller.plan, documents, d.candidatePaths).map(Some(_)))
                  .getOrElse(Consequence.success(Option.empty[P.Policy]))
                result <- _walk_c(source, cursor.copy(from = value.record, controller = value.controller,
                  documents = value.documents, documentpolicy = policy, candidate = None, validation = None,
                  decisioncontext = value.decisionContext, recordingCandidate = None), soughtwork, soughthuman, soughtrecording, core, historyonly)
              } yield result
              case None => if (soughthuman.contains(H.identity(decision))) Consequence.success[Located](Human(decision, cursor.findings))
              else port.retainedC(decision, core).flatMap {
                case None => Consequence.success[Located](Human(decision, cursor.findings))
                case Some((state, next, answer, input)) => for {
                  documents <- if (answer.result.value.choice == "AdoptPlanRevision")
                    input.planRevision.map(value => Consequence.success(value.documents))
                      .getOrElse(F.invalid("retained adoption has no saved captured proposal"))
                    else Consequence.success(cursor.documents)
                  _ <- if (answer.result.value.choice == "AdoptPlanRevision")
                    cursor.documentpolicy.fold(Consequence.unit)(_.validateC(state.plan, d.candidatePaths))
                    else Consequence.unit
                  resumed = cursor.copy(from = next, controller = state, documents = documents,
                    dependencyApprovals = if (answer.result.value.choice == "ApproveDependencyScope")
                      cursor.dependencyApprovals :+ GoalPhaseDependencyValidation.Approval(
                        cursor.controller.plan.steps.lift(cursor.controller.cursor).map(_.identity)
                          .getOrElse("phase:" + cursor.controller.plan.phase),
                        decision.result.result.value.dependencyProposal.get, answer.resultReference)
                      else cursor.dependencyApprovals,
                    inspectionSource = if (input.sourceHistoryRecovery.nonEmpty) None else cursor.inspectionSource,
                    sourceHistoryRecovery = None,
                    sourceHistoryContinuation = input.sourceHistoryRecovery.orElse(cursor.sourceHistoryContinuation),
                    sourceHistoryRecoveryConsumed = cursor.sourceHistoryRecoveryConsumed || input.sourceHistoryRecovery.nonEmpty,
                    candidate = if (answer.result.value.choice == "AdoptPlanRevision") None else cursor.candidate,
                    validation = if (answer.result.value.choice == "AdoptPlanRevision") None else cursor.validation,
                    decisioncontext = if (cursor.controller.documentDecision || input.sourceHistoryRecovery.nonEmpty) cursor.decisioncontext else Some(
                      S.DecisionContext(decision.result.result.value, answer.result.value.choice, answer.result.value.rationale,
                        Some(answer.resultReference))))
                  result <- if (Set("ResumeAssignedWork", "ContinueRepairs", "AdoptPlanRevision", "RejectPlanRevision", "ApproveDependencyScope", "RejectDependencyScope", "AuthorizeNewAttempt")(answer.result.value.choice))
                    _walk_c(source, resumed, soughtwork, soughthuman, soughtrecording, core, historyonly)
                  else Consequence.success[Located](Paused(resumed, if (answer.result.value.choice == "Defer")
                    "Human deferred this decision. No automatic question, work, command or closure follows."
                    else "Human cancelled this decision path. Repository state and history remain; the Phase is not completed."))
                } yield result
              }
            }
          } yield result else for {
            prepared <- _retained_work_c(source, cursor, pending, core)
            identity = ContinuationIdentity(boundary.continuationIdentity.value)
            result <- if (soughtwork.contains(identity)) Consequence.success[Located](Work(prepared))
              else for {
                native <- _native_c(source.storage)
                retained <- native.loadRetainedC(identity)(using core.executionContext)
                result <- retained match {
                  case None => Consequence.success[Located](Work(prepared))
                  case Some(value) => for {
                    _ <- if (value.completion.instanceRevision == pending.revision.next) Consequence.unit
                      else F.invalid("retained semantic completion crosses its actual native boundary")
                    // Only this real native-result branch can create provisional legacy authority.
                    // It reaches the next cursor only after contiguous completion verification below.
                    authenticated = value.completion.result.result.value.dependencyAdmission.filter(_.acceptedPrefix.isEmpty)
                      .filter(_ => !_is_review(prepared.input) && prepared.input.kind != "TriageStep").fold(prepared.dependencyChain) { admission =>
                        new DependencyChain(prepared.dependencyChain.accepted, prepared.dependencyChain._legacy :+
                          LegacyAdmission(prepared.input.instanceIdentity,
                            ContextReference(prepared.input.selectedPlan.identity, prepared.input.selectedPlan.revision),
                            prepared.input.stepIdentity.getOrElse(""), prepared.input.generation, prepared.input.originalBase,
                            pending.revision.next.value, admission))
                      }
                    state <- _completed_state_c(prepared.copy(dependencyChain = authenticated), value.completion.result)
                    findings <- N.accumulateC(cursor.findings, prepared.input, value.completion.result)
                    issues <- GoalPhaseFixIssues.completedC(prepared.input, value.completion.result.result.value,
                      value.completion.result.resultReference.identity)
                    priorreviews <- GoalPhaseNativeReview.retainPriorC(cursor.priorReviews, prepared.input,
                      value.completion.result)
                    next <- GoalPhaseNativeHistory.completionC(source.current, pending,
                      value.completion.result.resultReference, state.stage.toString)
                    clear = state.stage == O.Stage.InspectCandidate || state.stage == O.Stage.Repair ||
                      (prepared.input.kind == "RepairStep" && state.stage == O.Stage.PhaseInspection)
                    result <- _walk_c(source, cursor.copy(from = next, controller = state,
                      documents = prepared.input.documents,
                      candidate = if (clear) None else cursor.candidate,
                      validation = if (clear) None else cursor.validation,
                      lastresult = Some(prepared.input -> value.completion.result), decisioncontext = None,
                      validationRepair = if (prepared.input.kind == "ImplementStep") None else cursor.validationRepair,
                      issues = issues, findings = findings, priorReviews = priorreviews,
                      dependencyChain = authenticated,
                      dependencyAdmission = value.completion.result.result.value.dependencyAdmission.orElse(cursor.dependencyAdmission)),
                      soughtwork, soughthuman, soughtrecording, core, historyonly)
                  } yield result
                }
              } yield result
          } yield result
        } yield result
        case None => Consequence.success[Located](Frontier(cursor))
        case Some(entry) => _native_next_c(source, cursor, entry, soughtwork, soughthuman, soughtrecording, core, historyonly)
      }
    } yield located

    private def _native_next_c(source: F.Session, cursor: Cursor, entry: HistoryEntry,
      soughtwork: Option[ContinuationIdentity], soughthuman: Option[ContinuationIdentity],
      soughtrecording: Option[ContinuationIdentity],
      core: ActionCall.Core, historyonly: Boolean): Consequence[Located] = cursor.controller.stage match {
      case O.Stage.Decision if entry.causation.value.startsWith("ordered-validation-repair-adoption:") &&
          cursor.candidate.nonEmpty && cursor.validation.nonEmpty =>
        _resume_validation_repair_c(source, cursor, soughtwork, soughthuman, soughtrecording, core, historyonly)
      case O.Stage.PhaseClosing if entry.causation.value.startsWith("ordered-completion-policy-adoption:") => for {
        retained <- GoalPhaseCompletionPolicyAdoption.retainedC(GoalPhaseCompletionPolicyAdoption.Source(
          source, cursor.from, cursor.controller, cursor.legacyClosure), core)
        result <- _walk_c(source, cursor.copy(from = retained.record, completionDirection = Some(retained.direction)),
          soughtwork, soughthuman, soughtrecording, core, historyonly)
      } yield result
      case O.Stage.Decision if entry.causation.value.startsWith("ordered-failed-phase-repair-adoption:") &&
          cursor.candidate.nonEmpty && cursor.validation.nonEmpty => for {
        // The repair owner dispatches by the actual typed terminal source and
        // checks its v1/v2 Direction against this historical Decision prefix.
        retained <- GoalPhaseFailedPhaseRepairAdoption.retainedC(GoalPhaseFailedPhaseRepairAdoption.Source(
          source, cursor.from, cursor.controller, cursor.candidate.get, cursor.validation.get, cursor.documents.nonEmpty), d, core)
        result <- _walk_c(source, cursor.copy(from = retained.record, controller = retained.controller,
          candidate = None, validation = None, decisioncontext = None, recordingCandidate = None,
          inspectionSource = None, sourceHistoryRecovery = None, sourceHistoryContinuation = None,
          sourceHistoryRecoveryConsumed = false, candidateObservation = None, candidateObservationContinuation = None,
          wholePhaseCandidateRecovery = None, lastresult = None),
          soughtwork, soughthuman, soughtrecording, core, historyonly)
      } yield result
      case O.Stage.Decision if entry.causation.value.startsWith("ordered-terminated-timeout-validation-recovery:") &&
          cursor.candidate.nonEmpty && cursor.validation.nonEmpty => for {
        retained <- GoalPhaseTerminatedTimeoutValidationRecovery.retainedC(source, d, core, cursor.from,
          cursor.controller, cursor.candidate.get, cursor.validation.get, cursor.documents.nonEmpty)
        result <- _walk_c(source, cursor.copy(from = retained.record, controller = retained.controller,
          validation = None, decisioncontext = None, recordingCandidate = None),
          soughtwork, soughthuman, soughtrecording, core, historyonly)
      } yield result
      case O.Stage.Decision if entry.causation.value.startsWith("ordered-known-failed-phase-validation-recovery:") &&
          cursor.candidate.nonEmpty && cursor.validation.nonEmpty => for {
        retained <- GoalPhaseKnownFailedValidationRecovery.retainedC(source, d, core, cursor.from,
          cursor.controller, cursor.candidate.get, cursor.validation.get)
        result <- _walk_c(source, cursor.copy(from = retained.record, controller = retained.controller,
          validation = None, decisioncontext = None, recordingCandidate = None),
          soughtwork, soughthuman, soughtrecording, core, historyonly)
      } yield result
      case O.Stage.InspectCandidate | O.Stage.PhaseInspection => for {
        retained <- GoalPhaseNativeCandidate.retainedOptionC(source, d, core, cursor.from, cursor.controller,
          cursor.documentpolicy, N.managementPaths(cursor.findings), cursor.completedCandidates, retainedOnly = historyonly)
        result <- retained match {
          case None if cursor.wholePhaseCandidateRecovery.nonEmpty => _paused_c(source, cursor,
            "The authorized whole-Phase observation has an unresolved intent. Original and fresh outcomes remain UNKNOWN; no automatic retry or further authorization is granted.")
          case None if cursor.controller.stage == O.Stage.PhaseInspection => for {
            recovery <- GoalPhaseWholePhaseCandidateRecovery.retainedC(source, d, core, cursor.from, cursor.controller,
              cursor.documentpolicy, N.managementPaths(cursor.findings), cursor.completedCandidates)
            next <- recovery match {
              case Some(value) => _walk_c(source, cursor.copy(from = value.record, inspectionSource = None,
                wholePhaseCandidateRecovery = Some(value.target)), soughtwork, soughthuman, soughtrecording, core, historyonly)
              case None => _paused_c(source, cursor.copy(inspectionSource = Some(cursor.from)),
                "Original whole-Phase inspection has a partial or UNKNOWN result; a separate independent Human whole-Phase recovery direction is required.")
            }
          } yield next
          case None if cursor.candidateObservation.nonEmpty => for {
            continuation <- GoalPhaseCandidateObservationContinuation.retainedC(source, d, core, cursor.from,
              cursor.controller, cursor.candidateObservation.get, cursor.candidateObservationContinuation)
            next <- continuation match {
              case Some(value) if value.choice == "ReviseWork" => _walk_c(source, cursor.copy(from = value.record,
                controller = value.controller, candidate = None, validation = None, inspectionSource = None,
                sourceHistoryContinuation = None, candidateObservation = None, candidateObservationContinuation = None),
                soughtwork, soughthuman, soughtrecording, core, historyonly)
              case Some(value) => _walk_c(source, cursor.copy(from = value.record,
                controller = value.controller, candidateObservationContinuation = Some(value.target)), soughtwork, soughthuman, soughtrecording, core, historyonly)
              case None => _paused_c(source, cursor,
                "The separately authorized candidate observation has an unresolved intent; no automatic retry is granted. ALL prior observations remain UNKNOWN with termination-unconfirmed.")
            }
          } yield next
          case None => for {
            candidateRecovery <- GoalPhaseCandidateRecovery.retainedC(source, d, core, cursor.from, cursor.controller,
              cursor.documentpolicy, N.managementPaths(cursor.findings))
            next <- candidateRecovery match {
              case Some(value) if value.choice == "ReviseWork" => _walk_c(source, cursor.copy(from = value.record,
                controller = value.controller, candidate = None, validation = None, inspectionSource = None,
                sourceHistoryContinuation = None, candidateObservation = None, candidateObservationContinuation = None),
                soughtwork, soughthuman, soughtrecording, core, historyonly)
              case Some(value) => _walk_c(source, cursor.copy(from = value.record, controller = value.controller, inspectionSource = None,
                candidateObservation = Some(value.target)), soughtwork, soughthuman, soughtrecording, core, historyonly)
              case None => for {
                recovery <- GoalPhaseSourceHistoryRecovery.retainedC(source, d, core, cursor.from, cursor.controller,
                  cursor.documentpolicy, N.managementPaths(cursor.findings), cursor.completedCandidates)
                next <- recovery match {
                  case Some(value) => _walk_c(source, cursor.copy(from = value.record, inspectionSource = None,
                    sourceHistoryRecoveryConsumed = true, sourceHistoryContinuation = Some(value.target)),
                    soughtwork, soughthuman, soughtrecording, core, historyonly)
                  case None => _source_history_human_c(source, cursor, soughtwork, soughthuman, soughtrecording, core, historyonly)
                }
              } yield next
            }
          } yield next
          case Some(value) => for {
            _ <- if (value.payload.dependencyAdmission == cursor.dependencyAdmission.filter(a =>
                cursor.controller.plan.steps.lift(cursor.controller.cursor).exists(_.identity == a.binding.step))) Consequence.unit
              else F.invalid("candidate omitted or replaced its adopted dependency obligation")
            _ <- value.payload.dependency.fold(Consequence.unit)(capture =>
              GoalPhaseDependencyValidation.capturedForC(capture, source.current.identity.value,
                cursor.controller.plan, value.payload.step, cursor.controller.generation,
                value.payload.dependencyOrigin.map(_.observationSourceRevision).getOrElse(value.payload.sourceRevision),
                cursor.controller.base, capture.admission))
            _ <- value.payload.dependency.fold(Consequence.unit)(cursor.dependencyChain.captureC)
            state <- GoalPhaseNativeCandidate.observedStateC(cursor.controller, value.payload, cursor.documentpolicy, N.managementPaths(cursor.findings))
            record <- _native_record_c(source, cursor.from, value.record, "ordered-candidate:", state.stage)
            result <- _walk_c(source, cursor.copy(from = record, controller = state,
              candidate = Some(value.copy(record = record)), validation = None,
              sourceHistoryContinuation = None, candidateObservation = None, candidateObservationContinuation = None,
              wholePhaseCandidateRecovery = None), soughtwork, soughthuman, soughtrecording, core, historyonly)
          } yield result
        }
      } yield result
      case O.Stage.Validation | O.Stage.PhaseValidation => cursor.candidate match {
        case None => F.invalid("actual Validation has no current native candidate")
        case Some(candidate) => for {
          retained <- GoalPhaseNativeValidation.retainedC(source, d, core, candidate, cursor.controller, Some(cursor.from),
            cursor.documentpolicy, N.managementPaths(cursor.findings), allowRepairDirection = true)
          result <- retained match {
            case None => _paused_c(source, cursor, "Native checks have an actual partial or unknown result; no automatic replay.")
            case Some(value) if GoalPhaseNativeValidation.continuable(value) =>
              Consequence.success[Located](Frontier(cursor.copy(validation = Some(value))))
            case Some(value) if cursor.controller.stage == O.Stage.PhaseValidation && cursor.controller.releaseRepairActive &&
                GoalPhaseNativeValidation.completedFailurePending(value) =>
              Consequence.success[Located](Frontier(cursor.copy(validation = Some(value))))
            case Some(value) if value.payload.outcome.isEmpty => for {
              following <- GoalPhaseNativeHistory.nextC(source.current, value.record)
              result <- if (following.exists(_.causation.value == "ordered-validation-repair-adoption:" + value.record.revision.value))
                _resume_validation_repair_c(source, cursor.copy(from = value.record, validation = Some(value)),
                  soughtwork, soughthuman, soughtrecording, core, historyonly)
              else _paused_c(source, cursor.copy(validation = Some(value)),
                "Actual metadata selection and completed check prefix are retained. Any unresolved check intent remains non-replayable.")
            } yield result
            case Some(value) => for {
              state <- GoalPhaseNativeValidation.observedStateC(cursor.controller, value.payload,
                value.record.identity.value + ":ordered-validation:" + value.payload.sourceRevision + ":validation", candidate.payload.changedPaths)
              record <- _native_record_c(source, cursor.from, value.record, "ordered-validation:", state.stage)
              issues <- GoalPhaseFixIssues.validationC(source.current.identity.value, cursor.controller.plan.repository,
                cursor.issues, value.payload,
                value.record.identity.value + ":ordered-validation:" + value.payload.sourceRevision + ":validation")
              result <- _walk_c(source, cursor.copy(from = record, controller = state,
                validation = Some(value.copy(record = record)), issues = issues), soughtwork, soughthuman, soughtrecording, core, historyonly)
            } yield result
          }
        } yield result
      }
      case O.Stage.Review if entry.causation.value.startsWith("ordered-review-completion:") => for {
        selected <- _review_completion_source_c(cursor).flatMap(_.map(Consequence.success)
          .getOrElse(F.invalid("retained covered completion has no eligible actual repair and original review")))
        retained <- Q.retainedC(source, core, selected)
        result <- retained match {
          case None => _paused_c(source, cursor,
            "Covered review completion has native history without its known saved result; no automatic semantic request or effect follows.")
          case Some(value) => _walk_c(source, cursor.copy(from = value.record, controller = value.controller,
            issues = value.issues), soughtwork, soughthuman, soughtrecording, core, historyonly)
        }
      } yield result
      case O.Stage.Closing => for {
        retained <- if (soughtrecording.nonEmpty || historyonly)
          GoalPhaseNativeClosure.retainedRecordingHistoryOptionC(source, d, core, cursor.from, cursor.controller)
        else GoalPhaseNativeClosure.retainedOptionC(source, d, core, cursor.from, cursor.controller)
        result <- retained match {
          case None => for {
            message <- GoalPhaseNativeClosure.waitMessageC(source, d, core, cursor.from, cursor.controller)
            stopped <- _paused_c(source, cursor.copy(closureSource = Some(cursor.from)), message)
          } yield stopped
          case Some(value) if value.payload.commit.isEmpty =>
            Consequence.success[Located](Frontier(cursor))
          case Some(value) => for {
            record <- _native_record_c(source, cursor.from, value.record, "ordered-step-closure:", value.controller.stage)
            chain <- _accepted_dependency_c(cursor, record, value.controller)
            result <- _walk_c(source, cursor.copy(from = record, controller = value.controller,
              candidate = None, validation = None,
              sourceHistoryRecoveryConsumed = if (value.controller.cursor > cursor.controller.cursor) false
                else cursor.sourceHistoryRecoveryConsumed,
              sourceHistoryContinuation = if (value.controller.cursor > cursor.controller.cursor) None
                else cursor.sourceHistoryContinuation,
              completedCandidates = cursor.completedCandidates ++ cursor.candidate.map(_.payload),
              completedDependencies = cursor.completedDependencies ++ cursor.validation.map(_.payload).filter(_.dependency.nonEmpty),
              dependencyChain = chain),
              soughtwork, soughthuman, soughtrecording, core, historyonly)
          } yield result
        }
      } yield result
      case O.Stage.PhaseClosing if entry.causation.value.startsWith("ordered-release-closure:") => for {
        retained <- GoalPhaseNativeReleaseClosure.retainedC(source, d, core, cursor.from, cursor.controller)
        located <- retained match {
          case None => _paused_c(source, cursor,
            "Final Release commit has an unresolved intent or uncollected actual result; no automatic replay.")
          case Some(value) => _walk_c(source, cursor.copy(from = value.record, controller = value.controller),
            soughtwork, soughthuman, soughtrecording, core, historyonly)
        }
      } yield located
      case O.Stage.PhaseClosing if entry.causation.value.startsWith("ordered-recording-candidate:") => for {
        retained <- C.retainedC(source, d, core, cursor.from, cursor.controller, cursor.findings)
        result <- retained match {
          case Some(value) if value.payload.finalCandidate.nonEmpty => for {
            state <- C.observedC(cursor.controller, value.payload)
            record <- _native_record_c(source, cursor.from, value.record, "ordered-recording-candidate:", state.stage)
            next <- _walk_c(source, cursor.copy(from = record, controller = state,
              recordingCandidate = Some(value.copy(record = record))), soughtwork, soughthuman, soughtrecording, core, historyonly)
          } yield next
          case Some(value) => _paused_c(source, cursor.copy(recordingCandidate = Some(value)),
            "Phase recording staging has a retained partial or unknown intent. No automatic replay or closure follows.")
          case None => F.invalid("recording candidate history has no retained actual inspection")
        }
      } yield result
      case O.Stage.PhaseClosing if entry.causation.value.startsWith("ordered-repository-intent:") => for {
        input <- GoalPhaseNativeRepositoryClosure.inputC(cursor.from, cursor.controller)
        adapter <- source.storage.bindMechanicalC(F.advanceOperation, GoalPhaseNativeRepositoryClosure.codec)
        retained <- adapter.loadRetainedC(WorkflowMechanicalTransition.Reference(input.instance +
          ":ordered-repository-closure:" + input.sourceRevision + ":intent", "1"))(using core.executionContext)
          .flatMap(_.map(Consequence.success).getOrElse(F.invalid("Repository intent history has no retained child")))
        actual <- GoalPhaseNativeHistory.prefixC(source.current, entry.revision)
        _ <- if (retained.receipt.next == actual && retained.receipt.payload.value.input == input &&
            retained.receipt.payload.value.result.isEmpty) Consequence.unit else F.invalid("Repository intent crosses its original frontier")
        result <- _walk_c(source, cursor.copy(from = actual, legacyClosure = Some(retained.receipt.payload.value),
          repositoryFrontier = Some(cursor.from)), soughtwork, soughthuman, soughtrecording, core, historyonly)
      } yield result
      case O.Stage.PhaseClosing if entry.causation.value.startsWith("ordered-repository-closed:") =>
        Consequence.success[Located](Paused(cursor, "The existing child repository closure owns its retained input and effects.", repository = true))
      case _ => _paused_c(source, cursor, "Unrecognized advanced native predecessor: " + entry.causation.value + ". No automatic replay.")
    }

    private def _paused_c(source: F.Session, cursor: Cursor, reason: String): Consequence[Located] =
      Consequence.success(Paused(cursor.copy(from = source.current), reason))

    /** Retain the unknown inspection/intent before recognizing its normal Human
      * branch. Eligibility failures preserve the original fail-closed stop.
      */
    private def _source_history_human_c(source: F.Session, cursor: Cursor,
      soughtwork: Option[ContinuationIdentity], soughthuman: Option[ContinuationIdentity],
      soughtrecording: Option[ContinuationIdentity], core: ActionCall.Core, historyonly: Boolean): Consequence[Located] = {
      val stopped = cursor.copy(inspectionSource = Some(cursor.from))
      def _pause_ = _paused_c(source, stopped,
        "Candidate inspection has an actual partial or unknown result; no automatic replay.")
      if (cursor.sourceHistoryRecoveryConsumed || d.humanDecision.isEmpty) _pause_
      else GoalPhaseSourceHistoryRecovery.historicalTargetC(source, d, core, cursor.from, cursor.controller,
        cursor.documentpolicy, N.managementPaths(cursor.findings), cursor.completedCandidates).toOption match {
        case None => _pause_
        case Some(target) => for {
          frontier <- GoalPhaseNativeHistory.prefixC(source.current, InstanceRevision(target.frontierRevision.toLong))
          following <- GoalPhaseNativeHistory.nextC(source.current, frontier)
          retained = stopped.copy(from = frontier, sourceHistoryRecovery = Some(target))
          result <- following match {
            case Some(entry) if entry.suspension.exists(boundary =>
                boundary.continuationIdentity.value == frontier.identity.value + ":ordered-human:" + frontier.revision.value &&
                boundary.completion.value == "goal-phase-ordered-human-completion-v1") =>
              _walk_c(source, retained, soughtwork, soughthuman, soughtrecording, core, historyonly)
            case None if !historyonly => for {
              _ <- GoalPhaseSourceHistoryRecovery.currentTargetC(source, d, core, cursor.from, cursor.controller,
                cursor.documentpolicy, N.managementPaths(cursor.findings), cursor.completedCandidates)
              decision <- _human_source_c(source, retained)
            } yield Human(decision, cursor.findings): Located
            case _ => _pause_
          }
        } yield result
      }
    }

    private def _native_record_c(source: F.Session, from: InstanceRecord, saved: InstanceRecord,
      cause: String, stage: O.Stage): Consequence[InstanceRecord] = for {
      record <- GoalPhaseNativeHistory.prefixC(source.current, saved.revision)
      entries = record.history.drop(from.history.size)
      _ <- if (saved.identity == from.identity &&
          saved.definition.workflowIdentity == from.definition.workflowIdentity &&
          saved.definition.workflowRevision == from.definition.workflowRevision && entries.nonEmpty &&
          entries.forall(value => value.suspension.isEmpty && value.causation.value.startsWith(cause)) &&
          record.currentProgression.contains(ProgressionReference("phase." + stage.toString))) Consequence.unit
        else F.invalid("retained native result has no corresponding contiguous actual history")
    } yield record

    private def _selected_cursor(cursor: Cursor, selected: GoalPhaseNativeDocumentPlan.Selected): Cursor = {
      val clear = selected.controller.candidate.isEmpty && cursor.controller.candidate.nonEmpty
      cursor.copy(from = selected.record, controller = selected.controller, documents = Some(selected.documents),
        candidate = if (clear) None else cursor.candidate, validation = if (clear) None else cursor.validation)
    }

    private def _retained_work_c(source: F.Session, cursor: Cursor, pending: InstanceRecord,
      core: ActionCall.Core): Consequence[Prepared] = for {
      reader <- source.storage.bindIssuedC(F.completionOperation, S.inputCodec)
      boundary <- pending.suspension.map(Consequence.success).getOrElse(F.invalid("actual work suspension is absent"))
      actual <- reader.loadC(ContinuationIdentity(boundary.continuationIdentity.value))(using core.executionContext)
        .flatMap(_.map(Consequence.success).getOrElse(F.invalid("actual semantic suspension has no saved issue")))
      order <- actual.current match {
        case value: WorkflowContinuation.WorkOrder[S.Request] => Consequence.success(value)
        case _ => F.invalid("actual semantic issue is not a WorkOrder")
      }
      input <- order.request.input.map(value => Consequence.success(value.value)).getOrElse(F.invalid("actual issue has no business input"))
      _ <- GoalPhaseAcceptanceBoundary.savedPlanC(input.selectedPlan)
      _ <- GoalPhasePurposeRepair.validateRequestC(input)
      _ <- if (S.releaseRepairContractValid(input)) Consequence.unit
        else F.invalid("retained work has an invalid captured Release repair contract")
      controller <- (cursor.documents, input.documents) match {
        case (None, None) => cursor.controller.refreshedC(input.selectedPlan, false).flatMap {
          case Right(value) => Consequence.success(value)
          case Left(labels) => F.invalid("actual issued Plan contradicts its predecessor: " + labels.mkString(", "))
        }
        case (Some(previous), Some(documents)) => GoalPhaseDocumentRefresh.evaluateC(cursor.controller, previous,
          input.selectedPlan, documents, false).flatMap {
          case Right(value) => Consequence.success(value.controller)
          case Left(labels) => F.invalid("actual issued documents contradict their predecessor: " + labels.mkString(", "))
        }
        case _ => F.invalid("actual issued document source option differs from its predecessor")
      }
      _ <- GoalPhaseRepairProgress.retainedC(input.repairProgress, controller)
      semantic <- controller.next match {
        case value: O.Semantic => Consequence.success(value)
        case _ => F.invalid("actual issue has no semantic controller frontier")
      }
      required <- S.requiredC(input.kind)
      context <- _review_context_c(cursor.copy(controller = controller), input.kind)
      checkfailure <- _check_failure_context_c(cursor.copy(controller = controller), input.kind)
      _ <- if (input.instanceIdentity == source.current.identity.value && input.sourceRevision == cursor.from.revision.value.toString &&
          input.kind == semantic.key.kind.toString && input.stepIdentity == semantic.key.step && input.generation == semantic.key.generation &&
          input.originalBase == semantic.originalBase && input.completed == semantic.completed && input.purpose == semantic.purpose &&
          input.candidate == semantic.candidate && input.checksReceipt == semantic.checksReceipt &&
          input.repairDirective == semantic.repairDirective && GoalPhaseNativeReview.sameIssuedContext(input.review, context) && input.checkFailure == checkfailure &&
          input.decisionContext == cursor.decisioncontext && input.issues == cursor.issues &&
          input.triage == _triage_context(cursor, input.kind) &&
          actual.handle.instanceIdentity.value == source.current.identity.value && order.request.runId.value == source.current.identity.value &&
          order.request.continuationId.value == boundary.continuationIdentity.value &&
          _assigned_id(source, input).value == boundary.continuationIdentity.value &&
          pending.currentProgression == cursor.from.currentProgression &&
          order.request.expectedRevision.value == pending.revision.value.toString && order.request.completionOperation == F.completionOperation &&
          boundary.completion.value == required.metadata.completionContract.identity &&
          order.request.requiredOperation == required.identity) Consequence.unit
        else F.invalid("actual issued business input differs from its contiguous native predecessor")
    } yield Prepared(source, input, None, pending, actual, controller,
      if (_is_review(input) || input.kind == "TriageStep") cursor.candidate else None, cursor.documentpolicy, cursor.findings,
      cursor.dependencyApprovals, cursor.dependencyAdmission, d.dependencyValidation, cursor.dependencyChain)

    private def _review_context_c(cursor: Cursor, kind: String): Consequence[Option[GoalPhaseNativeReview.Context]] = kind match {
      case "ReviewStep" | "ReviewPhase" => (cursor.candidate, cursor.validation) match {
        case (Some(candidate), Some(validation)) if validation.payload.outcome.contains("passed") && candidate.payload.diff.nonEmpty =>
          GoalPhaseNativeReview.priorReviewsForC(cursor.priorReviews, kind,
            if (kind == "ReviewPhase") None else Some(cursor.controller.plan.steps(cursor.controller.cursor).identity))
            .map(priorreviews => Some(GoalPhaseNativeReview.Context(candidate.payload.changedPaths,
              candidate.payload.diff.get, validation.payload.receipts.filter(r =>
                validation.payload.selected.exists(c => c.identity == r.identity && c.dependency.isEmpty)),
              validation.payload.metadata, priorreviews,
              dependencyValidations = cursor.completedDependencies ++ Vector(validation.payload).filter(_.dependency.nonEmpty),
              triageJudgment = cursor.lastresult.flatMap { case (input, result) =>
                if (input.candidate != cursor.controller.candidate || input.checksReceipt != cursor.controller.checksReceipt) None
                else if (input.kind == "TriageStep") Some(GoalPhaseStepTriage.Judgment(result.resultReference,
                  result.result.value.outcome, result.result.value.summary, result.result.value.findings))
                else input.review.flatMap(_.triageJudgment)
              })))

        case _ => F.invalid("review has no actual current candidate and passed native checks")
      }
      case "RepairStep" if cursor.validation.exists(_.payload.outcome.exists(
          Set("repair-required", "repair-limit-reached"))) && !cursor.controller.releaseRepairActive => Consequence.success(None)
      case "RepairStep" => cursor.lastresult match {
        case Some((input, submitted)) => input.review.map(context =>
          GoalPhaseNativeReview.retainPriorC(context.priorReviews, input, submitted)
            .flatMap { priorreviews =>
              if (cursor.controller.releaseRepairActive && cursor.validation.exists(_.payload.outcome.exists(
                  Set("repair-required", "repair-limit-reached"))) &&
                  !priorreviews.findLast(_.kind == "ReviewPhase").exists(_.result.outcome == "repair"))
                F.invalid("Release check repair has no retained actual integrated repair review")
              else Consequence.success(Some(context.copy(priorReviews = priorreviews)))
            })
          .getOrElse(F.invalid("repair has no retained independent review context"))
        case None => F.invalid("repair has no retained independent review context")
      }
      case _ => Consequence.success(None)
    }

    private def _check_failure_context_c(cursor: Cursor, kind: String): Consequence[Option[S.CheckFailureContext]] =
      if (kind != "RepairStep" || !cursor.validation.exists(_.payload.outcome.exists(
          Set("repair-required", "repair-limit-reached"))))
        Consequence.success(None)
      else (cursor.candidate, cursor.validation) match {
        case (Some(candidate), Some(validation)) if candidate.payload.candidate.contains(validation.payload.candidate) &&
            candidate.payload.diff.nonEmpty =>
          S.checkFailureContextC(candidate.payload.changedPaths, candidate.payload.diff.get, validation.payload).map(Some(_))
        case _ => F.invalid("repair has no matching actual retained failed candidate and validation")
      }

    private def _advance_session_c(source: F.Session, core: ActionCall.Core):
      Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult] = for {
        initial <- _initial_cursor_c(source)
        located <- _walk_c(source, initial, None, None, None, core)
        wire <- located match {
          case Work(plan) => NativeWorkflowOperationOutput.goalWorkRequestC(plan.work, core)
            .map(NativeWorkflowOperationOutput.withFindingRecording(_, plan.findings))
          case Human(decision, findings) => _human_output_c(decision, findings, core)
          case LocatedRecording(recording) => _recording_port.issuedC(recording, core)
            .flatMap(NativeWorkflowOperationOutput.goalRecordingRequestC(_, recording.findings))
          case Paused(cursor, _, true) => GoalPhaseNativeRepositoryClosure.advanceC(source, d, core,
            cursor.repositoryFrontier.getOrElse(cursor.from), cursor.controller, acknowledgement, cursor.documentpolicy, N.managementPaths(cursor.findings))
            .map(NativeWorkflowOperationOutput.withFindingRecording(_, cursor.findings))
          case Paused(cursor, reason, false) => _wait_cursor_c(cursor, reason)
          case Frontier(cursor) => _frontier_c(source, cursor, core)
        }
      } yield wire

    private def _human_output_c(source: H.Source, findings: N.State, core: ActionCall.Core):
      Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult] = {
      val result = if (source.session.current.suspension.nonEmpty) for {
        reader <- source.session.storage.bindDecisionC(H.completionOperation, H.inputCodec)
        question <- reader.loadC(H.identity(source))(using core.executionContext).flatMap(
          _.map(Consequence.success).getOrElse(F.invalid("pending ordered Human question is absent")))
        output <- NativeWorkflowOperationOutput.goalHumanC(question, core)
      } yield output else _human_port_c.flatMap(_.advanceC(source, core))
      result.map(NativeWorkflowOperationOutput.withFindingRecording(_, findings))
    }

    private def _frontier_c(source: F.Session, cursor: Cursor, core: ActionCall.Core):
      Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult] = {
      val recording = _recording_source(source, cursor)
      if (cursor.candidateObservation.nonEmpty || cursor.wholePhaseCandidateRecovery.nonEmpty) _dispatch_c(source, cursor, core)
      else if (N.eligible(recording) && d.journalPolicy.nonEmpty) {
        if (cursor.findings.pending.size > 256) _wait_cursor_c(cursor,
          "The accumulated recording batch exceeds the bounded 256-item input. All pending observations are retained; no subset is recorded.")
        else GoalPhaseNativeJournalFinding.advanceC(recording, d, core, acknowledgement).flatMap(value =>
          F.waitC(value.record, "Automatic Finding recording", "Actual Journal Finding intent/result is retained.")
            .flatMap(_after_native_c(source, cursor, _, core)))
      }
      else if (N.eligible(recording)) N.selectC(recording, d).flatMap {
        case Left(reason) => _wait_cursor_c(cursor, reason)
        case Right(request) => _recording_port.advanceC(recording, request, core)
      }
      else if (cursor.findings.pending.nonEmpty && !N.awaitsPhaseAcceptance(recording) && Set(O.Stage.Implementation, O.Stage.PhaseInspection,
          O.Stage.PhaseClosing)(cursor.controller.stage)) F.invalid("pending observations cross the completed Step or Phase recording frontier")
      else _document_frontier_c(source, cursor, core)
    }

    private def _document_frontier_c(source: F.Session, cursor: Cursor, core: ActionCall.Core):
      Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult] = for {
      selected <- cursor.documents match {
        case Some(documents) if cursor.controller.stage != O.Stage.Decision && cursor.controller.stage != O.Stage.Closed =>
          GoalPhaseNativeDocumentPlan.advanceC(source, d, core, cursor.from, cursor.controller, documents, cursor.lastresult.nonEmpty)
        case _ => Consequence.success(Option.empty[GoalPhaseNativeDocumentPlan.Selected])
      }
      result <- selected match {
        case Some(_) => F.sessionC(source.current.identity.value, d, definition, core).flatMap(_advance_session_c(_, core))
        case None => _progress_c(source, cursor, core)
      }
    } yield result

    private def _progress_c(source: F.Session, cursor: Cursor, core: ActionCall.Core):
      Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult] = cursor.documents match {
      case Some(documents) if cursor.controller.stage != O.Stage.Decision && cursor.controller.stage != O.Stage.Closed => for {
        saved <- GoalPhaseNativeDocumentProgress.advanceC(source, d, core, cursor.from, cursor.controller,
          documents, cursor.progress, acknowledgement)
        result <- saved match {
          case None => _dispatch_c(source, cursor, core)
          case Some(value) => F.waitC(value.record, "Selected document progress",
            "Actual File intent/result is retained; the owning Port reconstructs its outcome before dispatch.")
            .flatMap(_after_native_c(source, cursor, _, core))
        }
      } yield result
      case _ => _dispatch_c(source, cursor, core)
    }

    private def _dispatch_c(source: F.Session, cursor: Cursor, core: ActionCall.Core):
      Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult] =
      (if (Set(O.Stage.Review, O.Stage.PhaseReview, O.Stage.Closing, O.Stage.PhaseClosing)(cursor.controller.stage))
        _dependency_freshness_c(cursor, core) else Consequence.unit).flatMap { _ => cursor.controller.stage match {
      case O.Stage.Review => for {
        selected <- _review_completion_source_c(cursor)
        saved <- selected.fold(Consequence.success(Option.empty[Q.Saved]))(Q.advanceC(source, d, core, _))
        wire <- saved match {
          case None => _semantic_frontier_c(source, cursor, core)
          case Some(value) => F.waitC(value.record, "Original independent review conditions completed", Q.message(value.payload))
            .flatMap(_after_native_c(source, cursor, _, core))
            .map(result => result.copy(message = Q.message(value.payload) + " " + result.message))
        }
      } yield wire
      case O.Stage.Implementation | O.Stage.Repair | O.Stage.Triage | O.Stage.PhaseReview => _semantic_frontier_c(source, cursor, core)
      case O.Stage.InspectCandidate | O.Stage.PhaseInspection =>
        if (cursor.controller.stage == O.Stage.InspectCandidate && d.candidatePaths.isEmpty ||
            cursor.controller.stage == O.Stage.PhaseInspection && d.phaseValidationPolicy.isEmpty && cursor.wholePhaseCandidateRecovery.isEmpty)
          _wait_cursor_c(cursor, "Native candidate inspection is not installed; selected work and Phase remain open.")
        else GoalPhaseNativeCandidate.advanceC(source, d, core, cursor.from, cursor.controller, acknowledgement,
          runValidation = false, documentPolicy = cursor.documentpolicy,
          recordingPaths = N.managementPaths(cursor.findings), completedCandidates = cursor.completedCandidates,
          sourceHistoryRecovery = cursor.sourceHistoryContinuation, candidateObservation = cursor.candidateObservation,
          candidateObservationContinuation = cursor.candidateObservationContinuation,
          wholePhaseCandidateRecovery = cursor.wholePhaseCandidateRecovery,
          dependencyAdmission = cursor.dependencyAdmission.filter(a =>
            cursor.controller.plan.steps.lift(cursor.controller.cursor).exists(_.identity == a.binding.step)),
          dependencyChain = Some(cursor.dependencyChain))
          .flatMap(_after_native_c(source, cursor, _, core))
      case O.Stage.Validation | O.Stage.PhaseValidation =>
        if ((if (cursor.controller.stage == O.Stage.PhaseValidation) d.phaseValidationPolicy else d.validationPolicy).isEmpty)
          _wait_cursor_c(cursor, "Actual candidate is retained; selected native check policy remains unavailable.")
        else cursor.candidate.map(candidate => GoalPhaseNativeValidation.advanceC(source, d, core, candidate,
          cursor.controller, acknowledgement, Some(cursor.from), cursor.documentpolicy, N.managementPaths(cursor.findings),
          dependencyChain = Some(cursor.dependencyChain))
          .flatMap { result =>
            if (result.validation.exists(validation => validation.outcome.isEmpty ||
                validation.outcome.contains("failed") && validation.checks.lastOption.exists(check =>
                  Set("cancelled", "timed-out", "termination-unconfirmed")(check.termination) &&
                    check.exitCode.isEmpty && check.sbt.exists(_.outcome == "incomplete"))))
              Consequence.success(NativeWorkflowOperationOutput.withFindingRecording(result.copy(repairProgress =
                Some(GoalPhaseRepairProgress.publicSnapshot(GoalPhaseRepairProgress.snapshot(cursor.controller)))), cursor.findings))
            else _after_native_c(source, cursor, result, core)
          })
          .getOrElse(F.invalid("selected checks have no actual current candidate"))
      case O.Stage.Closing => if (d.closureInstallation.isEmpty)
        _wait_cursor_c(cursor, "Selected checks and review are retained; Step closure remains unavailable.")
        else for {
          closed <- GoalPhaseNativeClosure.advanceC(source, d, core, cursor.from, cursor.controller, acknowledgement,
            () => _dependency_freshness_c(cursor, core))
          wire <- closed match {
            case None => for {
              input <- GoalPhaseNativeClosure.inputC(cursor.from, cursor.controller)
              context <- NativeAuthorityContext.stepC(core, input)
              waiting <- _wait_cursor_c(cursor, "Actual Step commit authority is unavailable; the selected Step and Phase remain open.")
            } yield waiting.copy(authorityContext = Some(context))
            case Some(value) if value.payload.commit.isEmpty =>
              F.waitC(value.record, if (value.execution.nonEmpty) "Selected Step commit remains open" else "Selected Step index check failed",
                value.execution.map(GoalPhaseNativeClosure.executionMessage).orElse(value.payload.check.map(check =>
                "Paths: " + check.eofPaths.mkString(", ") + ". " + check.findings.mkString("; ") +
                  ". Correct selected staged content before the next ordinary Advance."))
                .getOrElse("The original commit intent remains unknown; explicit public closure recovery is required."))
                .map(NativeWorkflowOperationOutput.withFindingRecording(_, cursor.findings))
            case Some(_) => _wait_cursor_c(cursor, "Actual ordered Step commit is retained; whole-Phase closure remains unfinished.")
              .flatMap(_after_native_c(source, cursor, _, core))
          }
        } yield wire
      case O.Stage.PhaseClosing if N.phasePaths(cursor.findings).nonEmpty && cursor.recordingCandidate.isEmpty => for {
        saved <- C.advanceC(source, d, core, cursor.from, cursor.controller, cursor.findings, acknowledgement)
        fallback <- _wait_cursor_c(cursor, if (saved.payload.finalCandidate.nonEmpty)
          "Actual Phase recording candidate is retained; repository closure remains unfinished."
          else "Phase recording has an unknown staging intent; it is not automatically replayed.")
        output <- _after_native_c(source, cursor, fallback, core)
      } yield output
      case O.Stage.PhaseClosing if GoalPhaseNativeReleaseClosure.needed(cursor.controller) =>
        if (!d.closureInstallation.exists(_.policy.programs.exists(_.step == GoalPhaseNativeReleaseClosure.programIdentity)))
          _wait_cursor_c(cursor, "Final Release commit requires an explicit host-fixed phase-release program; accepted checks/review remain retained.")
        else GoalPhaseNativeReleaseClosure.advanceC(source, d, core, cursor.from, cursor.controller, acknowledgement)
          .map(value => value.copy(repairProgress = Some(GoalPhaseRepairProgress.publicSnapshot(
            GoalPhaseRepairProgress.snapshot(cursor.controller)))))
          .flatMap(_after_native_c(source, cursor, _, core))
      case O.Stage.PhaseClosing if GoalPhasePostCompletionSync.usesLocalPolicy(source, cursor.completionDirection) =>
        GoalPhasePostCompletionSync.advanceC(source, d, core, cursor.from, cursor.controller,
          cursor.completionDirection, cursor.legacyClosure, cursor.findings, acknowledgement)
          .map(NativeWorkflowOperationOutput.withFindingRecording(_, cursor.findings))
      case O.Stage.PhaseClosing => if (d.repositoryClosure.nonEmpty)
        GoalPhaseNativeRepositoryClosure.advanceC(source, d, core, cursor.repositoryFrontier.getOrElse(cursor.from), cursor.controller, acknowledgement,
          cursor.documentpolicy, N.managementPaths(cursor.findings))
          .map(NativeWorkflowOperationOutput.withFindingRecording(_, cursor.findings))
        else _wait_cursor_c(cursor, "Whole-Phase checks and review are retained; repository closure remains unavailable.")
      case O.Stage.Decision =>
        if (d.humanDecision.nonEmpty && (cursor.controller.pendingRepair.nonEmpty || cursor.controller.documentDecision || cursor.controller.decisionSource.exists(
            Set(O.Stage.Implementation, O.Stage.Repair, O.Stage.Review, O.Stage.PhaseReview)))) for {
          decision <- _human_source_c(source, cursor)
          port <- _human_port_c
          wire <- port.advanceC(decision, core)
        } yield NativeWorkflowOperationOutput.withFindingRecording(wire, cursor.findings)
        else _wait_cursor_c(cursor, cursor.controller.decision.getOrElse("Native Decision remains open."))
      case O.Stage.Closed => _wait_cursor_c(cursor, "Actual native Phase closure is retained.")
    }}

    private def _accepted_dependency_c(cursor: Cursor, record: InstanceRecord, next: O.State): Consequence[DependencyChain] =
      if (next.cursor == cursor.controller.cursor || cursor.candidate.flatMap(_.payload.dependency).isEmpty)
        Consequence.success(cursor.dependencyChain)
      else for {
        capture <- cursor.candidate.flatMap(_.payload.dependency).map(Consequence.success)
          .getOrElse(F.invalid("accepted Step lost its dependency candidate"))
        _ <- cursor.dependencyChain.captureC(capture)
        _ <- if (next.cursor == cursor.controller.cursor + 1 && cursor.dependencyChain.accepted.size < 64 &&
            cursor.validation.exists(v => v.payload.dependency.contains(capture) && v.payload.outcome.contains("passed") &&
              GoalPhaseNativeValidation.successful(v.payload.receipts, v.payload.selected)) &&
            next.completed.lastOption.exists(_.step == capture.step)) Consequence.unit
          else F.invalid("accepted dependency anchor requires genuine successful checks, review and Step closure")
      } yield new DependencyChain(cursor.dependencyChain.accepted :+ AcceptedDependency(capture, record.revision.value.toString),
        cursor.dependencyChain._legacy)

    private def _dependency_freshness_c(cursor: Cursor, core: ActionCall.Core): Consequence[Unit] =
      GoalPhaseNativeDependencyValidation.chainFreshnessC(core, Some(cursor.dependencyChain),
        cursor.completedDependencies.flatMap(_.dependency) ++ cursor.candidate.toVector.flatMap(_.payload.dependency))

    private def _review_completion_source_c(cursor: Cursor): Consequence[Option[Q.Source]] = for {
      primary <- GoalPhaseNativeValidation.selectedChecksC(d, cursor.controller.plan,
        Some(cursor.controller.plan.steps(cursor.controller.cursor).identity))
      checks <- GoalPhaseNativeDependencyValidation.checksC(primary, cursor.candidate.flatMap(_.payload.dependency))
      selected <- Q.selectC(cursor.from, cursor.controller, cursor.candidate, cursor.validation,
        cursor.lastresult, cursor.issues, cursor.priorReviews, checks)
    } yield selected

    private def _semantic_frontier_c(source: F.Session, cursor: Cursor, core: ActionCall.Core):
      Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult] =
      if (Set(O.Stage.Review, O.Stage.PhaseReview)(cursor.controller.stage) && d.reviewInstallation.isEmpty)
        _wait_cursor_c(cursor, "Actual native checks are retained; independent review installation remains unavailable.")
      else for {
        semantic <- cursor.controller.next match {
          case value: O.Semantic => Consequence.success(value)
          case _ => F.invalid("current frontier has no assigned semantic work")
        }
        _ <- if (cursor.controller.stage == O.Stage.Implementation) cursor.validationRepair.fold(Consequence.unit) {
          case (original, direction) => GoalPhaseValidationRepairAdoption.beforeFirstWorkC(original, direction, d, core)
        } else Consequence.unit
        _ <- if (_is_review_kind(semantic.key.kind.toString)) d.reviewInstallation.get.validateC else Consequence.unit
        context <- _review_context_c(cursor, semantic.key.kind.toString)
        checkfailure <- _check_failure_context_c(cursor, semantic.key.kind.toString)
        input = _request(source, cursor, semantic, context, checkfailure)
        draft <- _prepare_assigned_c(source, input, cursor.from, cursor.controller,
          if (_is_review(input) || input.kind == "TriageStep") cursor.candidate else None, cursor.documentpolicy, cursor.findings,
          cursor.dependencyApprovals, cursor.dependencyAdmission, cursor.dependencyChain)
        plan <- _issue_assigned_c(draft, core)
        wire <- NativeWorkflowOperationOutput.goalWorkRequestC(plan.work, core)
      } yield NativeWorkflowOperationOutput.withFindingRecording(wire, cursor.findings)

    private def _after_native_c(source: F.Session, cursor: Cursor,
      fallback: org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult,
      core: ActionCall.Core): Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult] =
      F.sessionC(source.current.identity.value, d, definition, core).flatMap { current =>
        if (current.current.revision.value > cursor.from.revision.value) _advance_session_c(current, core).map { result =>
          fallback.validation.filter(v => v.evidenceRecording.nonEmpty || v.assessment.nonEmpty).fold(result) { original =>
            val projected = result.validation.filter(v => v.sourceRevision == original.sourceRevision &&
              v.scopeIdentity == original.scopeIdentity).getOrElse(original)
            result.copy(validation = Some(projected.copy(evidenceRecording = original.evidenceRecording,
              assessment = original.assessment, assessmentJournal = original.assessmentJournal)))
          }
        }
        else Consequence.success(NativeWorkflowOperationOutput.withFindingRecording(fallback, cursor.findings))
      }

    private def _request(source: F.Session, cursor: Cursor, semantic: O.Semantic,
      context: Option[GoalPhaseNativeReview.Context], checkfailure: Option[S.CheckFailureContext]): S.Request =
      S.Request(source.current.identity.value, cursor.from.revision.value.toString, semantic.key.kind.toString,
        semantic.key.step, semantic.key.generation, semantic.purpose, semantic.selectedPlan,
        semantic.originalBase, semantic.completed, semantic.candidate, semantic.checksReceipt,
        semantic.repairDirective, context, cursor.decisioncontext, cursor.documents, checkfailure, cursor.issues,
        Some(GoalPhaseRepairProgress.snapshot(cursor.controller)),
        GoalPhasePurposeRepair.capture(semantic.key.kind.toString, semantic.key.step, semantic.selectedPlan),
        releaseRepairContract = Option.when(semantic.key.kind == O.WorkKind.ReviewPhase)(S.RELEASE_REPAIR_CONTRACT),
        triage = _triage_context(cursor, semantic.key.kind.toString))

    private def _triage_context(cursor: Cursor, kind: String): Option[GoalPhaseStepTriage.Context] =
      if (kind != "TriageStep") None else for {
        candidate <- cursor.candidate
        diff <- candidate.payload.diff
        validation <- cursor.validation
        assessment <- validation.payload.assessment
        if assessment.branch == "triage" && validation.payload.outcome.contains("passed") &&
          candidate.payload.candidate.contains(assessment.candidate) &&
          validation.payload.receipts.size == validation.payload.selected.size &&
          GoalPhaseNativeValidation.successful(validation.payload.receipts, validation.payload.selected)
        selected = validation.payload.selected.filter(_.dependency.isEmpty)
        checks = validation.payload.receipts.filter(r => selected.exists(_.identity == r.identity))
        if GoalPhaseNativeMetadata.declaredIdentities(selected) == assessment.checks &&
          checks.map(_.identity) == selected.map(_.identity)
      } yield GoalPhaseStepTriage.Context(candidate.payload.changedPaths, diff, checks, assessment)

    private def _issue_assigned_c(plan: Prepared, core: ActionCall.Core): Consequence[Prepared] = for {
      _ <- GoalPhaseReleaseRepairContinuation.beforeWorkC(d, core, plan.input)
      intent <- plan.intent.map(Consequence.success).getOrElse(F.invalid("retained work cannot be resuspended"))
      issued <- plan.source.storage.bindIssuedC(F.completionOperation, S.inputCodec)
      existing <- issued.loadC(_assigned_id(plan.source, plan.input))(using core.executionContext)
      _ <- if (existing.isEmpty) Consequence.unit else F.invalid("saved issue has no corresponding actual pending frontier")
      _ <- plan.candidate.map(GoalPhaseNativeValidation.preconditionsC(plan.source, d, core, _, plan.controller,
        documentPolicy = plan.documentpolicy, recordingPaths = N.managementPaths(plan.findings),
        dependencyChain = Some(plan.dependencyChain))).getOrElse(Consequence.unit)
      _ <- if (_is_review(plan.input)) GoalPhaseNativeDependencyValidation.chainFreshnessC(core, Some(plan.dependencyChain),
        plan.input.review.toVector.flatMap(_.dependencyValidations).flatMap(_.dependency))
        else Consequence.unit
      work <- GoalPhaseNativeExecution.bindIssuedC(plan.work, d.executionProfiles, d.executionOrigin,
        isolateContext = plan.input.kind == "TriageStep")
      _ <- issued.suspendC(intent, work)(using core.executionContext)
    } yield plan.copy(work = work)

    private def _human_source_c(source: F.Session, cursor: Cursor): Consequence[H.Source] =
      cursor.lastresult.map { case (input, result) =>
        if (cursor.controller.stage == O.Stage.Decision ||
            (cursor.controller.stage == O.Stage.InspectCandidate && cursor.sourceHistoryRecovery.nonEmpty)) Consequence.success(
          H.Source(source, if (cursor.sourceHistoryRecovery.nonEmpty) input else input.copy(issues = cursor.issues), cursor.controller, cursor.from,
            result, cursor.candidate, cursor.documents, cursor.sourceHistoryRecovery))
        else F.invalid("Human question has no actual Decision predecessor")
      }.getOrElse(F.invalid("Human question has no genuine preceding semantic result"))

    private def _human_port_c: Consequence[H.Port] =
      d.humanDecision.map(installation => Consequence.success(new H.Port(
        d, definition, installation, _locate_decision_c, _resume_decision_c, acknowledgement)))
        .getOrElse(F.invalid("ordered Human handling is not installed"))

    def bindHumanCompletionC(params: ComponentCreate): Consequence[spec.OperationDefinition] =
      _human_port_c.flatMap(_.bindCompletionC(params))

    private def _recording_source(source: F.Session, cursor: Cursor): N.Source =
      N.Source(source, cursor.from, cursor.controller, cursor.findings, cursor.issues)
    private def _recording_port: N.Port = new N.Port(d, definition, _locate_recording_c,
      (source, core) => F.sessionC(source.from.identity.value, d, definition, core).flatMap(_advance_session_c(_, core)), acknowledgement)
    def bindRecordingCompletionC(params: ComponentCreate): Consequence[spec.OperationDefinition] =
      _recording_port.bindCompletionC(params)
    private def _locate_recording_c(identity: String, selected: ContinuationIdentity,
      core: ActionCall.Core): Consequence[N.Source] = for {
      source <- F.retainedSessionC(identity, d, definition, core)
      initial <- _initial_cursor_c(source)
      located <- _walk_c(source, initial, None, None, Some(selected), core)
      recording <- located match {
        case LocatedRecording(value) if N.identity(value) == selected => Consequence.success(value)
        case _ => F.invalid("submitted recording is unreachable through actual contiguous native history")
      }
    } yield recording

    private def _resume_decision_c(source: H.Source, state: O.State, from: InstanceRecord,
      answer: H.Answer, core: ActionCall.Core): Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult] =
      F.sessionC(source.session.current.identity.value, d, definition, core).flatMap(_advance_session_c(_, core))

    private def _locate_decision_c(identity: String, selected: ContinuationIdentity,
      core: ActionCall.Core): Consequence[H.Source] = for {
      source <- F.retainedSessionC(identity, d, definition, core)
      initial <- _initial_cursor_c(source)
      located <- _walk_c(source, initial, None, Some(selected), None, core, historyonly = true)
      decision <- located match {
        case Human(value, _) if H.identity(value) == selected => Consequence.success(value)
        case _ => F.invalid("submitted Human answer is unreachable through actual contiguous native history")
      }
    } yield decision

    private def _locate_assigned_c(source: F.Session, selected: ContinuationIdentity,
      core: ActionCall.Core): Consequence[Prepared] = for {
      initial <- _initial_cursor_c(source)
      located <- _walk_c(source, initial, Some(selected), None, None, core)
      prepared <- located match {
        case Work(value) if value.pending.suspension.exists(_.continuationIdentity.value == selected.value) => Consequence.success(value)
        case _ => F.invalid("submitted work is unreachable through actual contiguous native history")
      }
    } yield prepared

    private def _wait_cursor_c(cursor: Cursor, reason: String):
      Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult] =
      F.waitC(cursor.from, "Ordered GoalPhase remains open", reason).map(value => value.copy(
        validation = cursor.validation.map(saved => NativeWorkflowOperationOutput.goalValidation(saved.payload)),
        repairProgress = Some(GoalPhaseRepairProgress.publicSnapshot(GoalPhaseRepairProgress.snapshot(cursor.controller))),
        findingRecording = NativeWorkflowOperationOutput.goalFindingRecording(cursor.findings)))

    private def _prepare_assigned_c(source: F.Session, input: S.Request, from: InstanceRecord,
      controller: O.State, candidate: Option[GoalPhaseNativeCandidate.Saved] = None,
      documentpolicy: Option[P.Policy] = None, findings: N.State = N.State(),
      dependencyapprovals: Vector[GoalPhaseDependencyValidation.Approval] = Vector.empty,
      dependencyadmission: Option[GoalPhaseDependencyValidation.Admission] = None,
      dependencychain: DependencyChain = _empty_dependency_chain): Consequence[Prepared] = for {
      _ <- GoalPhaseRepairProgress.validateOptionC(input.repairProgress)
      _ <- GoalPhaseAcceptanceBoundary.savedPlanC(input.selectedPlan)
      required <- S.requiredC(input.kind)
      independent = _is_review(input)
      requirement <- _requirement_c(input, required, independent)
      documents <- GoalPhaseDocumentSource.instructionsC(input.documents, input.stepIdentity)
      sources <- GoalPhaseNativeValidation.sourceInstructionsC(d, input.selectedPlan, input.stepIdentity)
      bundle = ContextBundle((if (input.kind == "TriageStep")
        "Assess only the selected immediate semantic uncertainty in a context separate from implementation. Use the actual candidate diff, passed check results, exact assessment triggers and applicable prior decisions. Return outcome clean, suspicious or decline with a concrete summary; findings are attributable suspected observations, never confirmed blockers. clean requires empty observations. suspicious requires nonempty observations and scope for detailed review. decline is a genuine semantic inability to judge; transport/tool failure is not a business result and leaves this work open. Never edit, execute commands, waive required checks or manufacture independent review. No Plan or dependency proposal, acceptance, fix/issue report or review observations are permitted. Runtime routes clean to formal Step admission and suspicious/decline to one detailed review."
        else if (independent) "Independently review the actual candidate and selected native check outcomes"
        else if (input.kind == "RepairStep" && input.stepIdentity.isEmpty)
          "Repair only the assigned Release delta within original Plan ownership. Preserve closed Step commits, prior integrated review and applicable checks. Runtime owns fresh validation, independent scoped review and Final; this is no replacement Step or Plan amendment."
        else if (input.kind == "RepairStep" && input.checkFailure.nonEmpty)
          "Correct semantic source only within the selected Step to fix its selected failed checks using the retained actual typed diagnostics. Runtime owns commands, fresh candidate observation and revalidation before independent review."
        else if (input.kind == "RepairStep") "Repair only the assigned Step findings from the retained independent review"
        else "Implement only the current Step of the complete selected ordered Phase plan") +
        (if (input.kind == "ImplementStep" && input.repairDirective.exists(_.reason.startsWith("Human-requested reconciliation: ")))
          " This is explicit human-requested reconciliation of current scoped development changes, not an independent reviewer finding. Preserve the index and every completed old stage/history effect; old unresolved observations remain UNKNOWN and termination-unconfirmed. First assess whether the current changes require different adopted requirements: if so, return decision-needed with the existing complete bounded planRevision Proposal before implementation. Otherwise reconcile within the saved Plan, checks, ownership and limits. Never execute old intent or repeat old staging/history. Ordinary follow-on candidate staging belongs only to this newly issued generation after its semantic result."
          else "") +
        (if (input.kind == "TriageStep") documents + sources else GoalPhaseAcceptanceBoundary.instructions(input.selectedPlan) + documents + sources) +
        (if (input.kind == "TriageStep") "" else
          GoalPhaseDependencyValidation.instructions(dependencyadmission.map(_.binding).orElse(d.dependencyValidation),
            dependencyapprovals, input.stepIdentity, input.kind)) +
        (if (independent) GoalPhaseDependencyValidation.reviewInstructions(
          input.review.toVector.flatMap(_.dependencyValidations).flatMap(_.dependency)) else "") +
        (if (input.kind == "TriageStep") "" else
        " A dependencyProposal is an untrusted request, never direction: ImplementStep may report PLANNING or IMPLEMENTATION, RepairStep REPAIR, and independent Review REVIEW including discovered edits, only as decision-needed. Review never edits dependencies, adopts a Plan or approves itself." +
        " An ApproveDependencyScope context selects only one bounded linked unit: its requesting semantic result, exact proposal, acceptance, dependentWork and actual Human decisionReference. Reuse that saved answer at this same native frontier without a new question for each selected file. New out-of-scope dependency work requires another proposal and question; no arbitrary future scope, automatic dispatch, hidden dependency workflow or source permission follows. RejectDependencyScope retains explicit rejection. All effect, target and acceptance authorities still apply; ReviewStep and ReviewPhase remain read-only.") +
        (if (input.kind == "ReviewStep") " Report design or requirement gaps as findings for later repair; ReviewStep never proposes or adopts Plan revisions. Optional completionConditions may appear only on an original repair verdict after every semantic judgment is settled and its only remaining ordered findings are extra terminal LF corrections, exactly one finding per explicit current Step owned path. Use kind terminal-lf with 1..16 distinct relative paths in finding order. Omit this option whenever any semantic question remains; supplying it is never required. Review stays read-only. Runtime must observe the actual repair delta and fresh complete selected Compile and Test receipts before ordinary Closing admission; this original repair report never becomes a new PASS."
          else if (input.kind == "ReviewPhase") " Only an actual integrated blocker may request decision-needed with a genuine nonempty finding and one raw complete planRevision Proposal appending exactly one corrective Step. Preserve every old Step exactly, the Plan identity/Phase/repository/repair limit/Phase checks/review classification and raw/effective acceptance; use a fresh revision, depend on all old Step identities exactly, and keep nonempty owned paths within original owned-path scopes and nonempty checks within the predeclared host policy. The old Plan must have fewer than 64 Steps. No dependencyProposal may accompany it. Document-backed Phase amendment is unsupported. Review remains read-only: never edit or adopt; actual Human adoption and existing execution, validation, review and closure authority remain required."
          else if (input.kind == "TriageStep") ""
          else " Report genuine test, suite, requirement or design gaps as decision-needed with a raw complete bounded planRevision Proposal. Do not amend the current Plan or broaden the selected host profile before explicit Human adoption; source mutation and permissions remain separately admitted.") +
        (if (independent && input.review.exists(_.priorReviews.nonEmpty))
          " For affected re-review, use the actual prior reports and findings, current combined diff and check outcomes, and adopted requirements. Reuse unrelated still-applicable judgment and assess outstanding integration findings and regressions. Do not require a reviewer for each edit, skip approval, or automatically rerun all Step semantics."
          else ""),
        Vector(ContextReference(input.selectedPlan.identity, input.selectedPlan.revision)), Vector.empty,
        ContextSnapshot(from.definition.workflowRevision.value, evidenceRevision = Some(input.sourceRevision)))
      continuation = Continuation(StateMachineRunIdentity(from.identity.value), _assigned_id(source, input),
        StateMachineRevision(from.revision.next.value.toString), required, bundle)
      entry = HistoryEntry(HistorySequence(from.history.size.toLong + 1), from.revision.next, Lifecycle.Active,
        from.currentProgression, from.correlation, CausationReference(
          (if (input.kind == "ImplementStep") "ordered-implementation-issued:"
            else if (input.kind == "RepairStep") "ordered-repair-issued:"
            else if (input.kind == "TriageStep") "ordered-triage-issued:" else "ordered-review-issued:") + from.identity.value),
        from.derivedCompositeOccurrence, from.committedPredecessor,
        Some(SuspensionBoundary(NativeIdentity(continuation.continuationId.value), from.revision.next,
          ContextSnapshotReference(bundle.snapshot.workflowRevision), CompletionReference(required.metadata.completionContract.identity),
          EvidenceReference(required.metadata.evidenceContract.identity), from.correlation)))
      intent = WorkflowInstanceAtomicTransitionV1.SuspensionIntent(WorkflowInstanceAtomicTransitionV1.schemaVersion,
        d.configuration, from, from.revision, entry,
        ContinuationRuntimePersistence.Record(continuation, ContinuationRuntimePersistence.Status.Available))
      pending <- from.appendC(from.revision, entry)
      handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, from)
      work = WorkflowInteraction[S.Request, Nothing](handle, WorkflowContinuation.WorkOrder(
        ContinuationRequest(continuation.runId, continuation.continuationId, continuation.expectedRevision,
          required.identity, required.operation, Some(TypedValue(S.inputCodec.typeIdentity, input)), required.resultType,
          bundle, required.metadata.completionContract, required.metadata.evidenceContract, F.completionOperation),
        requirement, MinimalPresentation(if (input.kind == "ReviewPhase") "Independent whole-Phase review"
            else if (independent) "Independent selected Step review"
            else if (input.kind == "TriageStep") "Assess selected Step semantic uncertainty"
            else if (input.kind == "RepairStep" && input.stepIdentity.isEmpty) "Repair selected Release delta"
            else if (input.kind == "RepairStep") "Repair selected Phase Step" else "Implement selected Phase Step",
          "Return only the assigned semantic result. Workflow owns commands, validation, Step and Phase closure.")))
    } yield Prepared(source, input, Some(intent), pending, work, controller, candidate, documentpolicy, findings,
      dependencyapprovals, dependencyadmission, d.dependencyValidation, dependencychain)

    private def _requirement_c(input: S.Request, required: StateMachineRequiredOperation,
      independent: Boolean): Consequence[ExecutionRequirement] = {
      val base = ExecutionRequirement(Vector(CapabilityRequirement(required.identity.capability)),
        RiskLevel("SOURCE_IMPLEMENTATION"), ReasoningLevel.Standard, independent)
      val classification = if (input.kind == "ReviewPhase") input.selectedPlan.phaseReviewClassification
        else input.stepIdentity.flatMap(identity => input.selectedPlan.steps.find(_.identity == identity))
          .flatMap(_.workClassification).map { work => input.kind match {
            case "ImplementStep" => work.implementation
            case "RepairStep" => work.repair
            case "TriageStep" => work.review
            case _ => work.review
          }}
      classification match {
        // Reconstruct semantic work from its retained selected Plan, never
        // from a later Component profile mapping. Only fresh issue after an
        // absent issued-row lookup binds disposition; reconstruction stays semantic.
        case Some(value) => value.requirementC(base)
        case None => Consequence.success(base) // Current Plan classification is optional business input.
      }
    }

    private[GoalPhaseNativeWorkContinuation] def operationC(core: Option[ActionCall.Core], native: WorkflowManagedCompletion[S.Result],
      result: Option[ContinuationResult[NativeWorkflowResultInput.Value]] = None): Consequence[spec.OperationDefinition] = {
      val provided = S.providedDefinition
      val op = provided.providedOperations.find(o => o.service == F.completionOperation.service && o.name == F.completionOperation.operation)
      if (op.isEmpty) return F.invalid("ordered completion is absent from generated ABI")
      val instances = CallScopedWorkflowInstances.managed(d.configuration, definition, d.instances, d.continuations)
      val issued = new IssuedWorkOrderPersistence[S.Request] {
        def putIfAbsentC(work: WorkflowInteraction[S.Request, Nothing]) = F.invalid("result submission cannot issue work")
        def loadC(id: ContinuationIdentity) = core match {
          case None => F.invalid("issue lookup requires its current completion call")
          case Some(call) => F.bindC(d, definition, call).flatMap(_.bindIssuedC(F.completionOperation, S.inputCodec))
            .flatMap(_.loadC(id)(using call.executionContext))
        }
      }
      var admittedTriage = Option.empty[S.Request]
      val codec = new WorkflowCompletionServiceOperation.Codec[S.Result] {
        val resultTypeIdentity = S.resultCodec.typeIdentity
        def decodeC(request: ProtocolRequest) = for {
          raw <- NativeWorkflowOperationInput.recordC(request, "result")
          _ <- GoalPhaseDependencyValidation.validatePublicResultC(raw)
          submitted <- result.map(NativeWorkflowResultInput.typedC(_, S.resultCodec))
            .getOrElse(F.invalid("ordered completion has no resolved semantic result"))
          call <- core.map(Consequence.success).getOrElse(F.invalid("completion requires its actual owning call"))
          source <- F.retainedSessionC(submitted.handle.instanceIdentity.value, d, definition, call)
          plan <- _locate_assigned_c(source, submitted.continuationId, call)
          admission <- _dependency_admission_c(plan, submitted.result.value)
        } yield submitted.copy(result = TypedValue(S.resultCodec.typeIdentity,
          submitted.result.value.copy(dependencyAdmission = admission)))
        def encodeC(resume: ContinuationRuntime.Resume) = F.invalid("result acknowledgement requires native committed completion")
        override def encodeAfterCommitC(resume: ContinuationRuntime.Resume, submitted: ContinuationResult[S.Result],
          completed: Option[InstanceRecord]): Consequence[OperationResponse] =
          completed.map { record => for {
            journal <- (admittedTriage, core) match {
              case (Some(request), Some(call)) => DevelopmentJournalAssessment.recordTriageC(d, call, request,
                submitted.result.value, submitted.resultReference)
              case _ => Consequence.success(None)
            }
          } yield NativeWorkflowOperationOutput.response(NativeWorkflowOperationOutput.accepted(submitted, record, journal)) }
            .getOrElse(_uncertain("ordered result has no native committed state"))
      }
      val closing = new WorkflowCompletionServiceOperation.ClosingProgram[S.Result] {
        def selectC(submitted: ContinuationResult[S.Result], current: InstanceRecord) = core match {
          case None => F.invalid("ordered closing requires the current owning call")
          case Some(call) => for {
            actual <- issued.loadC(submitted.continuationId).flatMap {
              case Some(value) => Consequence.success(value)
              case None => F.invalid("result has no actual assigned work")
            }
            source <- F.sessionC(actual.handle.instanceIdentity.value, d, definition, call)
            draft <- _locate_assigned_c(source, submitted.continuationId, call)
            plan = draft
            _ <- plan.candidate.map(GoalPhaseNativeValidation.preconditionsC(source, d, call, _, plan.controller,
              documentPolicy = plan.documentpolicy, recordingPaths = N.managementPaths(plan.findings),
              dependencyChain = Some(plan.dependencyChain))).getOrElse(Consequence.unit)
            _ <- if (plan.input.kind == "RepairStep") GoalPhaseNativeCandidate.ownershipC(source, d, call, plan.controller)
              else Consequence.unit
            _ <- if (_is_review(plan.input)) d.reviewInstallation.get.evidence.admitC(call, plan.input, submitted)
              else Consequence.unit
            _ <- if (_is_review(plan.input)) GoalPhaseNativeDependencyValidation.chainFreshnessC(call, Some(plan.dependencyChain),
              plan.input.review.toVector.flatMap(_.dependencyValidations).flatMap(_.dependency))
              else Consequence.unit
            _ <- GoalPhaseNativeReview.retainPriorC(Vector.empty, plan.input, submitted)
            state <- _completed_state_c(plan, submitted)
            next <- _completed_c(plan, submitted, state)
            _ = if (plan.input.kind == "TriageStep") admittedTriage = Some(plan.input)
          } yield {
            val program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
              ActionExecution.Completed(StateMachineOperationResult(closingType, submitted.resultReference)))
            WorkflowCompletionServiceOperation.SelectedClosing(program, Some(next))
          }
        }
      }
      val actualInstances = core.fold[WorkflowInstancePersistence](instances)(CallScopedWorkflowInstances.forCall(instances, _))
      WorkflowCompletionServiceOperation.bindC(provided, op.get, SmWorkflowComponent.GoalPhaseServiceService.SubmitGoalPhaseWorkResultOperation.specification.request, SmWorkflowComponent.GoalPhaseServiceService.SubmitGoalPhaseWorkResultOperation.specification.response,
        codec, ContinuationRuntime.empty, issued, actualInstances, d.configuration,
        () => throw new IllegalStateException("Ordered completion requires canonical managed UoW"), Some(closing), Some(native),
        descriptiveAttributes = SmWorkflowComponent.GoalPhaseServiceService.SubmitGoalPhaseWorkResultOperation.specification.content.descriptiveAttributes)
    }
  }

  def bindC(d: F.Declaration, definition: DefinitionBinding,
    acknowledgement: String => Consequence[Unit] = _ => Consequence.unit): Consequence[Port] = F.validateC(d).flatMap(_ =>
    if (definition == null || acknowledgement == null) F.invalid("ordered definition or acknowledgement is missing")
    else Consequence.success(new Port(d, definition, acknowledgement)))
  def bindCompletionC(params: ComponentCreate, d: F.Declaration, definition: DefinitionBinding): Consequence[spec.OperationDefinition] = for {
    port <- bindC(d, definition)
    storage <- WorkflowComponentStorage.bindC(params, SmWorkflowComponent.componentId, d.configuration,
      Vector(definition), d.instances, d.continuations)
    native <- _native_c(storage)
    declared <- port.operationC(None, native)
  } yield CallScopedOperationBinding.wrapResult(declared, (core, result) => F.bindC(d, definition, core)
    .flatMap(_native_c).flatMap(n => port.operationC(Some(core), n, Some(result))))

  private def _completed_state_c(plan: Prepared, submitted: ContinuationResult[S.Result]): Consequence[O.State] = for {
    dependency <- _dependency_admission_c(plan, submitted.result.value, retained = true)
    _ <- if (dependency == submitted.result.value.dependencyAdmission) Consequence.unit
      else F.invalid("retained completion changed its adopted dependency validation obligation")
    _ <- GoalPhaseFixIssues.admitC(plan.input, submitted.result.value)
    _ <- N.accumulateC(plan.findings, plan.input, submitted)
    next <- if (plan.input.kind == "TriageStep")
      GoalPhaseStepTriage.completedC(plan.input, submitted.result.value, submitted.resultReference, plan.controller)
    else if (_is_review(plan.input))
      GoalPhaseNativeReview.completedRequestC(plan.input, submitted.result.value, submitted.resultReference.identity, plan.controller)
    else for {
      exempt <- GoalPhaseFixAssessment.ordinaryCountExemptC(plan.input, submitted.result.value)
      stage <- if (submitted.result.value.outcome == "completed") plan.controller.next match {
        case value: O.Semantic if value.key.kind.toString == plan.input.kind &&
            value.key.plan == plan.input.selectedPlan.identity && value.key.revision == plan.input.selectedPlan.revision &&
            value.key.phase == plan.input.selectedPlan.phase && value.key.repository == plan.input.selectedPlan.repository &&
            value.key.step == plan.input.stepIdentity && value.key.generation == plan.input.generation =>
          plan.controller.acceptC(O.SemanticCompleted(value.key, submitted.result.value.summary, exempt))
        case _ => F.invalid("work has no controller-assigned semantic frontier")
      } else plan.controller.acceptC(O.NativeFailure(submitted.result.value.summary))
    } yield stage
  } yield next

  /** Public input has references only. Only this native owner enriches the saved
    * semantic completion with the actual admitted binding and Human history.
    */
  private def _dependency_admission_c(plan: Prepared, result: S.Result, retained: Boolean = false):
    Consequence[Option[GoalPhaseDependencyValidation.Admission]] = {
    val standing = plan.dependencyAdmission.filter(a => plan.input.stepIdentity.contains(a.binding.step))
    if (!retained && result.dependencyAdmission.nonEmpty)
      F.invalid("public completion cannot supply an admitted dependency snapshot")
    else if (_is_review(plan.input) || plan.input.kind == "TriageStep") {
      if (result.dependencyValidation.isEmpty && result.dependencyAdmission.isEmpty) Consequence.success(None)
      else F.invalid("independent review or triage cannot select or adopt dependency validation")
    } else result.dependencyValidation match {
      case None => if (retained && result.dependencyAdmission != standing)
        F.invalid("completion omitted or replaced its standing dependency validation")
        else Consequence.success(standing)
      case Some(selection) => for {
        binding <- (if (retained) result.dependencyAdmission.map(_.binding)
          else standing.map(_.binding).orElse(plan.dependencyBinding)).map(Consequence.success)
          .getOrElse(F.invalid("dependency validation has no explicitly selected host binding"))
        plain <- GoalPhaseDependencyValidation.admitC(selection, binding, plan.dependencyApprovals, plan.input)
        anchor <- plan.dependencyChain.anchorC(binding)
        _ <- if (anchor.forall(p => p.sessionId == plan.input.instanceIdentity &&
            p.plan == ContextReference(plan.input.selectedPlan.identity, plan.input.selectedPlan.revision) &&
            BigInt(p.closureRevision) <= BigInt(plan.input.sourceRevision))) Consequence.unit
          else F.invalid("dependency prefix crosses the current Session, Plan or semantic source")
        legacy = (if (retained) result.dependencyAdmission else standing).filter(a => a.acceptedPrefix.isEmpty &&
          a == plain && plan.dependencyChain._legacy.exists(e => e.admission == a &&
            e.session == plan.input.instanceIdentity &&
            e.plan == ContextReference(plan.input.selectedPlan.identity, plan.input.selectedPlan.revision) &&
            plan.input.stepIdentity.contains(e.step) && e.primaryBase == plan.input.originalBase))
        admitted = legacy.getOrElse(plain.copy(acceptedPrefix = anchor))
        _ <- plan.dependencyChain.resolveC(admitted)
        _ <- if (standing.forall(_ == admitted) && (!retained || result.dependencyAdmission.contains(admitted))) Consequence.unit
          else F.invalid("captured dependency binding or exact approval union changed")
      } yield Some(admitted)
    }
  }

  private def _completed_c(plan: Prepared, submitted: ContinuationResult[S.Result], state: O.State): Consequence[InstanceRecord] =
    plan.pending.appendC(plan.pending.revision,
      F.history(plan.pending, "phase." + state.stage.toString, submitted.resultReference.identity))
  private def _continuation_id(source: F.Session) = ContinuationIdentity(source.current.identity.value +
    ":ordered-implementation:" + source.started.receipt.next.revision.value)
  private def _assigned_id(source: F.Session, input: S.Request) = if (input.kind == "ImplementStep" && input.generation == 1) _continuation_id(source)
    else ContinuationIdentity(source.current.identity.value +
      (if (input.kind == "RepairStep") ":ordered-repair:"
        else if (input.kind == "ImplementStep") ":ordered-implementation:"
        else if (input.kind == "TriageStep") ":ordered-triage:" else ":ordered-review:") + input.sourceRevision)
  private def _native_c(storage: WorkflowComponentStorage) = storage.bindCompletionC(F.completionOperation,
    S.inputCodec, S.resultCodec, closingType, (_, _) => Consequence.success(Vector.empty))
  private def _uncertain[A](reason: String): Consequence[A] =
    org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate(reason)
  private def _is_review_kind(kind: String): Boolean = kind == "ReviewStep" || kind == "ReviewPhase"
  private def _is_review(input: S.Request): Boolean = _is_review_kind(input.kind)
}
