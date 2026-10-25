package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms
import org.simplemodeling.textus.smworkflow.value.{GoalPhaseStartInput, GoalPhaseStartResult}
import GoalPhaseOrderedProgression as O
import RepositorySyncManagedGitObservation as G

/** Native ordered Phase Start. Captures declarations only; every call borrows
  * the Subsystem's canonical store and managed Git execution. No private pool,
  * controller digest or AI-supplied plan admission is introduced.
  * @since   Oct.  6, 2026
  * @version Oct. 26, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseNativeRun {
  val startOperation = StateMachineOperationIdentity("GoalPhaseService", "startGoalPhase")
  val advanceOperation = StateMachineOperationIdentity("GoalPhaseService", "advanceGoalPhase")
  val completionOperation = StateMachineOperationIdentity("GoalPhaseService", "submitGoalPhaseWorkResult")
  final case class Declaration(configuration: Configuration, instances: DataStore.CollectionId,
    continuations: DataStore.CollectionId, input: GoalPhaseStartInput, target: RepositoryGitRuntimePrograms.Target,
    candidatePaths: Vector[String] = Vector.empty,
    validationPolicy: Option[GoalPhaseNativeValidation.Policy] = None,
    reviewInstallation: Option[GoalPhaseNativeReview.Installation] = None,
    closureInstallation: Option[GoalPhaseNativeClosure.Installation] = None,
    phaseValidationPolicy: Option[GoalPhaseNativeValidation.Policy] = None,
    repositoryClosure: Option[GoalPhaseNativeRepositoryClosure.Installation] = None,
    humanDecision: Option[GoalPhaseOrderedHumanDecision.Installation] = None,
    executionProfiles: Option[WorkflowExecutionProfileMapping] = None,
    executionOrigin: Option[GoalPhaseNativeExecution.Origin] = None,
    projectAcceptance: Option[GoalPhaseAcceptanceBoundary.Project] = None,
    findingRecordingPolicy: Option[GoalPhaseFindingRecording.Policy] = None,
    validationRecovery: Option[GoalPhaseValidationRecovery.Installation] = None,
    journalPolicy: Option[DevelopmentJournal.Policy] = None,
    evidencePolicy: Option[CommonEvidence.Policy] = None,
    remainingPlanAdoption: Option[GoalPhaseRemainingPlanAdoption.Installation] = None,
    sourceHistoryRecovery: Option[GoalPhaseSourceHistoryRecovery.Installation] = None,
    closureRecovery: Option[GoalPhaseClosureRecovery.Installation] = None,
    candidateRecovery: Option[GoalPhaseCandidateRecovery.Installation] = None,
    candidateObservationContinuation: Option[GoalPhaseCandidateObservationContinuation.Installation] = None,
    wholePhaseCandidateRecovery: Option[GoalPhaseWholePhaseCandidateRecovery.Installation] = None,
    failedPhaseRepairAdoption: Option[GoalPhaseFailedPhaseRepairAdoption.Installation] = None,
    completionPolicyAdoption: Option[GoalPhaseCompletionPolicyAdoption.Installation] = None,
    selectedCompletionPolicy: String = GoalPhasePostCompletionSync.localPolicy,
    reviewPurposeRepair: Option[GoalPhaseLegacyPurposeRepairContinuation.Installation] = None,
    dependencyValidation: Option[GoalPhaseDependencyValidation.Binding] = None,
    validationRepairAdoption: Option[GoalPhaseValidationRepairAdoption.Installation] = None,
    validationRepairCallerSource: Option[GoalPhaseValidationPriorInvocation.CallerSourcePolicy] = None,
    developmentObservation: Option[DevelopmentOperationalView.Configuration] = None,
    releaseRepairContinuation: Option[GoalPhaseReleaseRepairContinuation.Installation] = None,
    repairPolicyAdoption: Option[GoalPhaseRepairPolicyAdoption.Installation] = None)
  final case class SelectedPlan(selectionIdentity: String, participantIdentity: String,
    plan: O.Plan, documents: Option[GoalPhaseDocumentSource.Bound] = None)
  trait PlanPort {
    /** Host resolves the genuinely selected complete plan, not an AI request's
      * claim or a latest arbitrary planning file. Only Start selects this plan.
      */
    def loadForCallC(selectionIdentity: String, core: ActionCall.Core): Consequence[Option[SelectedPlan]]
  }
  final case class Payload(participantIdentity: String, root: String, branch: String, plan: O.Plan, base: O.Snapshot,
    documents: Option[GoalPhaseDocumentSource.Bound] = None,
    completionPolicy: Option[String] = None, developmentStartedAt: Option[String] = None,
    repairPolicy: Option[String] = None)
  final case class Session(storage: WorkflowComponentStorage,
    started: WorkflowManagedMechanicalTransition.Retained[Payload], participantIdentity: String,
    current: InstanceRecord)

  val codec: WorkflowResultJsonV1.PayloadCodec[Payload] = new WorkflowResultJsonV1.PayloadCodec[Payload] {
    val typeIdentity = "GoalPhaseNativeStartRecord"
    def encode(value: Payload) = Json.obj("schemaVersion" -> Json.fromString("sm-workflow.goal-phase-native-start.v2"),
      "payload" -> value.asJson.mapObject(fields => {
        val documents = if (value.documents.isEmpty) fields.remove("documents") else fields
        val policy = if (value.completionPolicy.isEmpty) documents.remove("completionPolicy") else documents
        val occurrence = if (value.developmentStartedAt.isEmpty) policy.remove("developmentStartedAt") else policy
        if (value.repairPolicy.isEmpty) occurrence.remove("repairPolicy") else occurrence
      }))
    def decode(value: Json): Either[String, Payload] = for {
      wire <- Option(value).toRight("native ordered Start is missing")
      schema <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(schema == "sm-workflow.goal-phase-native-start.v2",
        (), "native ordered Start schema differs")
      payload <- wire.hcursor.get[Payload]("payload").left.map(_.message)
      _ <- Either.cond(!wire.hcursor.downField("payload").downField("completionPolicy").focus.contains(Json.Null), (),
        "native ordered Start completion policy must be omitted or explicitly selected")
      _ <- Either.cond(payload.completionPolicy.forall(GoalPhasePostCompletionSync.policies.contains), (),
        "native ordered Start completion policy is unsupported")
      _ <- Either.cond(!wire.hcursor.downField("payload").downField("repairPolicy").focus.contains(Json.Null) &&
        payload.repairPolicy != null && payload.repairPolicy.forall(_ == GoalPhaseRuntimeRepairControl.version), (),
        "native ordered Start repair policy must be omitted or explicitly supported")
      _ <- Either.cond(payload.developmentStartedAt != null && payload.developmentStartedAt.forall(at =>
        scala.util.Try(java.time.Instant.parse(at)).isSuccess), (), "native ordered Start occurrence time is invalid")
    } yield payload
  }

  def startC(input: GoalPhaseStartInput, selected: ProfileStartAdmission.Selection,
    d: Declaration, definition: DefinitionBinding, plans: PlanPort, core: ActionCall.Core): Consequence[GoalPhaseStartResult] = for {
    storage <- bindC(d, definition, core)
    _ <- if (plans != null && input != null && selected != null) Consequence.unit
      else invalid("Start business input or plan source is missing")
    _ <- GoalPhaseParticipantAdmission.admitC(selected.participantIdentity, core.executionContext)
    identity <- core.executionContext.security.session.flatMap(_.sessionId).map(value =>
      Consequence.success(InstanceIdentity(value))).getOrElse(invalid("Start requires its canonical sessionId"))
    admitted <- plans.loadForCallC(selected.selectionId, core).flatMap {
      case Some(value) if value.selectionIdentity == selected.selectionId &&
          value.participantIdentity == selected.participantIdentity => Consequence.success(value)
      case _ => invalid("Start has no independently selected complete plan")
    }
    _ <- O.validatePlanC(admitted.plan)
    _ <- if (admitted.plan.repairLimit <= GoalPhaseRuntimeRepairControl.ceiling) Consequence.unit
      else invalid("new native Starts require selected repair allowance at most 10")
    plan <- GoalPhaseAcceptanceBoundary.captureC(d.projectAcceptance, admitted.plan)
    _ <- if (plan.phase == input.phaseIdentity && plan.repository == input.repositoryIdentity &&
        input.phaseIdentity == d.input.phaseIdentity && input.repositoryIdentity == d.input.repositoryIdentity)
      Consequence.unit else invalid("selected plan crosses its requested Phase/repository or host target")
    root <- G.rootC(core)
    observed <- G.observeC(core)
    _ <- if (root == d.target.root.toString && observed.branch == d.target.localBranch && observed.dirty.isEmpty)
      Consequence.unit else invalid("ordered Phase requires its selected clean native root/branch")
    base = O.Snapshot(observed.head, observed.tree)
    payload = Payload(selected.participantIdentity, root, observed.branch, plan, base, admitted.documents,
      Some(d.selectedCompletionPolicy), d.developmentObservation.map(_ => core.executionContext.clock.instant().toString),
      Some(GoalPhaseRuntimeRepairControl.version))
    // Ordinary native Git facts and explicit plan references, not source hashes.
    cause = CausationReference("ordered-goal-start:" + token(admitted.plan.identity) + ":" +
      token(admitted.plan.revision) + ":" + base.head + ":" + base.tree)
    initial <- InstanceRecord(identity, definition, initialRevision, Lifecycle.NotStarted, None,
      CorrelationReference(selected.idempotencyKey), cause, None, None, None, Vector.empty).validateC
    predicted <- initial.appendC(initialRevision, history(initial, "phase.Implementation", cause.value))
    events <- GoalPhaseDevelopmentEvents.recordsC(d.developmentObservation, identity.value,
      predicted.revision.next.value, "GoalPhaseStarted", cause.value, core, payload.developmentStartedAt.map(java.time.Instant.parse))
    starts <- storage.bindMechanicalC(startOperation, codec)
    instances <- storage.bindInstancesC()(using core.executionContext)
    _ <- instances.createC(initial)(using core.executionContext)
    active <- instances.appendC(identity, initialRevision, history(initial, "phase.Implementation", cause.value))(using core.executionContext)
    saved <- starts.executeC(WorkflowMechanicalTransition.Intent(d.configuration, reference(identity.value),
      active, active.revision, history(active, "phase.Implementation", "ordered-goal-plan-saved:" + identity.value),
      TypedValue(codec.typeIdentity, payload)), empty, events)(using core.executionContext)
  } yield GoalPhaseStartResult(saved.receipt.next.identity.value, "started",
    "Started Phase " + input.phaseIdentity + " in " + input.repositoryIdentity +
      ". The selected plan is saved; Advance selects the first work.")

  def sessionC(identity: String, d: Declaration, definition: DefinitionBinding, core: ActionCall.Core): Consequence[Session] = for {
    session <- retainedSessionC(identity, d, definition, core)
    p = session.started.receipt.payload.value
    root <- G.rootC(core)
    branch <- G.branchC(core)
    _ <- if (root == p.root && branch == p.branch) Consequence.unit else invalid("actual root/branch crosses the original plan")
  } yield session

  def retainedSessionC(identity: String, d: Declaration, definition: DefinitionBinding, core: ActionCall.Core): Consequence[Session] =
    retainedSessionOptionC(identity, d, definition, core).flatMap {
      case Some(value) => Consequence.success(value)
      case None => invalid("owning advance has no actual native ordered Start")
    }

  def retainedSessionOptionC(identity: String, d: Declaration, definition: DefinitionBinding, core: ActionCall.Core): Consequence[Option[Session]] = for {
    storage <- bindC(d, definition, core)
    starts <- storage.bindMechanicalC(startOperation, codec)
    retained <- starts.loadRetainedC(reference(identity))(using core.executionContext)
    result <- retained match {
      case None => Consequence.success(Option.empty[Session])
      case Some(started) => for {
        p <- Consequence.success(started.receipt.payload.value)
        _ <- if (p.root == d.target.root.toString && p.branch == d.target.localBranch &&
            p.plan.phase == d.input.phaseIdentity && p.plan.repository == d.input.repositoryIdentity)
          Consequence.unit else invalid("retained original plan scope differs from the host")
        _ <- GoalPhaseParticipantAdmission.admitC(p.participantIdentity, core.executionContext)
      } yield Some(Session(storage, started, p.participantIdentity, started.current))
    }
  } yield result

  def validateC(d: Declaration): Consequence[Unit] =
    if (d == null || d.configuration == null || d.instances == null || d.continuations == null ||
        d.instances == d.continuations || d.input == null || d.target == null || d.target.root == null ||
        !d.target.root.isAbsolute || d.target.root.normalize != d.target.root || d.target.localBranch == null ||
        d.candidatePaths == null || d.candidatePaths.size > 256 || d.candidatePaths.distinct != d.candidatePaths ||
        !d.candidatePaths.forall(RepositorySyncNativeConflictInput.relative) || d.validationPolicy == null || d.reviewInstallation == null ||
        d.closureInstallation == null || d.phaseValidationPolicy == null || d.repositoryClosure == null || d.humanDecision == null ||
        d.dependencyValidation == null || d.dependencyValidation.exists(b => !GoalPhaseDependencyValidation.bindingValid(b)) ||
        d.executionProfiles == null || d.executionProfiles.exists(_ == null) || d.findingRecordingPolicy == null ||
        d.validationRecovery == null || d.validationRecovery.exists(_ == null) || d.journalPolicy == null ||
        d.releaseRepairContinuation == null || d.releaseRepairContinuation.exists(i => i == null || !i.validateC.isSuccess) ||
        d.validationRepairAdoption == null || d.validationRepairAdoption.exists(i => i == null || !i.validateC.isSuccess) ||
        d.validationRepairCallerSource == null || d.validationRepairCallerSource.exists(p => !GoalPhaseValidationPriorInvocation.policyValid(p)) ||
        d.developmentObservation == null || d.developmentObservation.exists(c =>
          c == null || c.schemaVersion != DevelopmentOperationalView.schema || !DevelopmentOperationalView.valid(c.subject) ||
          c.subject.repositoryIdentity != d.input.repositoryIdentity || c.subject.phaseIdentity != d.input.phaseIdentity ||
          c.ownerDetailsUrl == null || !c.ownerDetailsUrl.forall(DevelopmentOperationalView.safeDetailsUrl)) ||
        d.journalPolicy.exists(_ == null) || d.evidencePolicy == null ||
        d.sourceHistoryRecovery == null || d.sourceHistoryRecovery.exists(_ == null) ||
        d.candidateRecovery == null || d.candidateRecovery.exists(_ == null) ||
        d.candidateObservationContinuation == null || d.candidateObservationContinuation.exists(_ == null) ||
        d.wholePhaseCandidateRecovery == null || d.wholePhaseCandidateRecovery.exists(_ == null) ||
        d.closureRecovery == null || d.closureRecovery.exists(_ == null) ||
        d.remainingPlanAdoption == null || d.remainingPlanAdoption.exists(_ == null) ||
        d.failedPhaseRepairAdoption == null || d.failedPhaseRepairAdoption.exists(_ == null) ||
        d.completionPolicyAdoption == null || d.completionPolicyAdoption.exists(_ == null) ||
        d.repairPolicyAdoption == null || d.repairPolicyAdoption.exists(i => i == null || !i.validateC.isSuccess) ||
        !GoalPhasePostCompletionSync.policies.contains(d.selectedCompletionPolicy) ||
        d.findingRecordingPolicy.exists(p => p == null || !RepositorySyncNativeConflictInput.relative(p.hygiene) ||
          !RepositorySyncNativeConflictInput.relative(p.developmentCandidate) ||
          !p.hygiene.endsWith(".md") || !p.developmentCandidate.endsWith(".md")))
      invalid("native ordered host declaration is incomplete")
    else CommonEvidence.validatePolicyC(d.evidencePolicy, Vector(d.instances, d.continuations) ++ d.journalPolicy.map(_.collection)).flatMap(_ =>
      DevelopmentJournal.validatePolicyC(d.journalPolicy, d.instances, d.continuations)).flatMap(_ =>
      GoalPhaseAcceptanceBoundary.validateProjectOptionC(d.projectAcceptance)).flatMap(_ =>
      GoalPhaseNativeExecution.validatePairC(d.executionProfiles, d.executionOrigin)).flatMap(_ =>
      d.configuration.validateC).flatMap(_ => d.validationPolicy.fold(Consequence.unit)(p =>
      if (p == null) invalid("native ordered validation policy is missing") else p.validateC)).flatMap(_ =>
        d.reviewInstallation.fold(Consequence.unit)(r =>
          if (r == null || d.validationPolicy.isEmpty || d.candidatePaths.isEmpty)
            invalid("native review requires candidate/check installation") else r.validateC)).flatMap(_ =>
        d.closureInstallation.fold(Consequence.unit)(c =>
          if (c == null || d.reviewInstallation.isEmpty) invalid("native Step closure requires review installation")
          else c.validateC)).flatMap(_ => d.phaseValidationPolicy.fold(Consequence.unit)(p =>
          if (p == null || d.closureInstallation.isEmpty) invalid("whole-Phase checks require native ordered Step closure installation")
          else p.validateC)).flatMap(_ => d.repositoryClosure.fold(Consequence.unit)(r =>
          if (r == null) invalid("repository closure host is missing") else r.validateC(d))).flatMap(_ =>
            d.humanDecision.fold(Consequence.unit)(h =>
              if (h == null) invalid("ordered Human decision installation is missing") else h.validateC)).flatMap(_ =>
                d.validationRecovery.fold(Consequence.unit)(_.validateC)).flatMap(_ =>
                  d.remainingPlanAdoption.fold(Consequence.unit)(_.validateC)).flatMap(_ =>
                    d.sourceHistoryRecovery.fold(Consequence.unit)(_.validateC)).flatMap(_ =>
                    d.closureRecovery.fold(Consequence.unit)(_.validateC)).flatMap(_ =>
                    d.candidateRecovery.fold(Consequence.unit)(_.validateC)).flatMap(_ =>
                    d.candidateObservationContinuation.fold(Consequence.unit)(_.validateC)).flatMap(_ =>
                    d.wholePhaseCandidateRecovery.fold(Consequence.unit)(_.validateC)).flatMap(_ =>
                    d.failedPhaseRepairAdoption.fold(Consequence.unit)(_.validateC)).flatMap(_ =>
                    d.completionPolicyAdoption.fold(Consequence.unit)(_.validateC))
  def bindC(d: Declaration, definition: DefinitionBinding, core: ActionCall.Core): Consequence[WorkflowComponentStorage] =
    validateC(d).flatMap(_ => d.releaseRepairContinuation.fold(Consequence.unit)(_.validateC)).flatMap { _ =>
      if (core == null || core.executionContext == null || core.action == null || core.action.request == null ||
          !(core.action.request.service.contains("DevelopmentObservationService") &&
            Set("readDevelopmentState", "readDevelopmentHistory")(core.action.request.operation) ||
            core.action.request.service.contains("GoalPhaseService") &&
          Set(startOperation.operation, advanceOperation.operation, completionOperation.operation,
            GoalPhaseValidationRecovery.describeOperation, GoalPhaseValidationRecovery.recoveryOperation,
            GoalPhaseSourceHistoryRecovery.describeOperation, GoalPhaseSourceHistoryRecovery.recoveryOperation,
            GoalPhaseCandidateRecovery.describeOperation, GoalPhaseCandidateRecovery.recoveryOperation,
            GoalPhaseCandidateObservationContinuation.describeOperation, GoalPhaseCandidateObservationContinuation.continuationOperation,
            GoalPhaseWholePhaseCandidateRecovery.describeOperation, GoalPhaseWholePhaseCandidateRecovery.recoveryOperation,
            GoalPhaseClosureRecovery.describeOperation, GoalPhaseClosureRecovery.recoveryOperation,
            GoalPhaseRemainingPlanAdoption.describeOperation, GoalPhaseRemainingPlanAdoption.adoptionOperation,
            GoalPhaseLegacyPurposeRepairContinuation.describeOperation, GoalPhaseLegacyPurposeRepairContinuation.admissionOperation,
            GoalPhaseReleaseRepairContinuation.describeOperation, GoalPhaseReleaseRepairContinuation.recoveryOperation,
            GoalPhaseFailedPhaseRepairAdoption.describeOperation, GoalPhaseFailedPhaseRepairAdoption.adoptionOperation,
            GoalPhaseValidationRepairAdoption.describeOperation, GoalPhaseValidationRepairAdoption.adoptionOperation,
            GoalPhaseCompletionPolicyAdoption.describeOperation, GoalPhaseCompletionPolicyAdoption.adoptionOperation,
            GoalPhaseRepairPolicyAdoption.describeOperation, GoalPhaseRepairPolicyAdoption.adoptionOperation,
            GoalPhaseOrderedHumanDecision.completionOperation.operation,
            GoalPhaseNativeFindingRecording.completionOperation.operation)(core.action.request.operation)) ||
          !core.component.exists(c => c.componentId == SmWorkflowComponent.componentId && c.subsystem.nonEmpty))
        invalid("native ordered entry requires its actual owning call")
      else WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
        SmWorkflowComponent.componentId, d.configuration, Vector(definition), d.instances, d.continuations)
    }
  def history(from: InstanceRecord, progression: String, cause: String): HistoryEntry =
    HistoryEntry(HistorySequence(from.history.size.toLong + 1), from.revision.next, Lifecycle.Active,
      Some(ProgressionReference(progression)), from.correlation, CausationReference(cause),
      from.derivedCompositeOccurrence, from.committedPredecessor, None)
  def waitC(record: InstanceRecord, title: String, detail: String):
    Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult] =
    Consequence.success(NativeWorkflowOperationOutput.goalWait(record.identity.value, title + ": " + detail,
      record.currentProgression.map(_.value).getOrElse("pending")))
  def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("GoalPhase native run: " + reason)
  private def token(value: String) = URLEncoder.encode(value, UTF_8)
  private def reference(identity: String) = WorkflowMechanicalTransition.Reference(identity + ":ordered-start", "1")
  private def empty = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](())
}
