package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.processexecution.{GitObjectId, GitOperation}
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import GoalPhaseNativeRun as F
import GoalPhaseOrderedProgression as O
import RepositorySyncManagedGitObservation as G
import WorkflowManagedProcessExecution as E
import GoalPhaseNativeCandidateBoundary as B
import GoalPhaseDependencyValidation.given

/** Native selected-Step or whole-Phase inspection. Mechanical effect intents keep the logical
  * InspectCandidate phase unchanged; only actual candidate observation takes
  * the CML candidate-observed edge. Git staging cannot roll back with storage.
  * An intent without its actual recorded result is retained, never replayed.
  * @since   Oct.  6, 2026
  * @version Oct. 27, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseNativeCandidate {
  final case class Payload(sourceRevision: String, step: String,
    selectedPaths: Vector[String], stagedPaths: Vector[String], candidate: Option[O.Snapshot],
    changedPaths: Vector[String], diff: Option[String], continuation: Option[O.CandidateContinuation] = None,
    stepEvidence: Option[Vector[StepEvidence]] = None,
    dependencyAdmission: Option[GoalPhaseDependencyValidation.Admission] = None,
    dependencyStaged: Vector[String] = Vector.empty,
    dependency: Option[GoalPhaseDependencyValidation.Capture] = None,
    dependencyOrigin: Option[GoalPhaseDependencyValidation.CandidateOrigin] = None,
    dependencySourceDirection: Option[GoalPhaseDependencySourceRevision.Direction] = None,
    dependencySourceGuard: Option[GoalPhaseDependencySourceRevision.Guard] = None,
    dependencySourceStages: Vector[GoalPhaseDependencySourceRevision.StageFact] = Vector.empty,
    dependencySourceCapture: Option[GoalPhaseDependencySourceRevision.Capture] = None)
  final case class StepEvidence(step: String, candidate: O.Snapshot, changedPaths: Vector[String], diff: String,
    commit: O.Commit, eofCorrections: Vector[String], partialCompletion: Option[O.StepCommitChain] = None)
  object StepEvidence {
    given Encoder[StepEvidence] = deriveEncoder[StepEvidence].mapJson(_.mapObject(_.filter {
      case ("partialCompletion", value) => !value.isNull
      case _ => true
    }))
    private val _decoder: Decoder[StepEvidence] = deriveDecoder
    given Decoder[StepEvidence] = Decoder.instance(cursor => _decoder(cursor).flatMap(value =>
      if (!cursor.downField("partialCompletion").focus.exists(_.isNull) && value.partialCompletion.forall(c =>
          O.validStepCommitChain(c) && c.completion == value.commit)) Right(value)
      else Left(DecodingFailure("Step evidence partial chain is malformed or crossed", cursor.history))))
  }
  object Payload {
    given Encoder[Payload] = deriveEncoder[Payload].mapJson(_.mapObject(_.filter {
      case ("continuation" | "stepEvidence" | "dependencyAdmission" | "dependency" | "dependencyOrigin" |
          "dependencySourceDirection" | "dependencySourceGuard" | "dependencySourceCapture", value) => !value.isNull
      case ("dependencyStaged" | "dependencySourceStages", value) => !value.asArray.exists(_.isEmpty)
      case _ => true
    }))
    private val _decoder: Decoder[Payload] = deriveDecoder
    given Decoder[Payload] = Decoder.instance(cursor => {
      val legacy = if (cursor.downField("dependencyStaged").focus.isEmpty)
        cursor.value.mapObject(_.add("dependencyStaged", Json.arr())) else cursor.value
      val normalized = if (cursor.downField("dependencySourceStages").focus.isEmpty)
        legacy.mapObject(_.add("dependencySourceStages", Json.arr())) else legacy
      _decoder(normalized.hcursor).flatMap(value => {
      val valid = cursor.value.asObject.exists(_.keys.forall(Set("sourceRevision", "step", "selectedPaths",
        "stagedPaths", "candidate", "changedPaths", "diff", "continuation", "stepEvidence", "dependencyAdmission",
        "dependencyStaged", "dependency", "dependencyOrigin", "dependencySourceDirection", "dependencySourceGuard",
        "dependencySourceStages", "dependencySourceCapture"))) && !Vector("continuation", "stepEvidence", "dependencyAdmission", "dependency", "dependencyOrigin",
          "dependencySourceDirection", "dependencySourceGuard", "dependencySourceCapture").exists(name => cursor.downField(name).focus.exists(_.isNull)) &&
        GoalPhaseDependencySourceRevision.candidateValid(value) &&
        value.dependencyAdmission != null && value.dependencyAdmission.forall(GoalPhaseDependencyValidation.admissionValid) &&
        value.dependencyStaged != null && value.dependencyStaged.distinct == value.dependencyStaged &&
        value.dependencyStaged == value.dependencyAdmission.toVector.flatMap(_.binding.paths).take(value.dependencyStaged.size) &&
        value.dependencyOrigin != null && value.dependencyOrigin.forall(o =>
          GoalPhaseDependencyValidation.originValid(o) && value.dependency != null && value.dependency.exists(c =>
            c != null && c.sourceRevision == o.observationSourceRevision) && value.sourceRevision != null &&
          value.sourceRevision.toLongOption.exists(n => n > 0 && n.toString == value.sourceRevision) &&
          BigInt(o.observationSourceRevision) <= BigInt(value.sourceRevision)) &&
        value.dependency != null && value.dependency.forall(c => GoalPhaseDependencyValidation.captureValid(c) &&
          value.dependencyAdmission.contains(c.admission) && value.dependencyStaged == c.admission.binding.paths) &&
        value.continuation.forall(c => c != null && c.parent != null && c.lineage != null &&
        c.lineage.lastOption.exists(last => last != null && O.continuationValid(O.Snapshot(last.identity, last.tree), c))) &&
        value.stepEvidence.forall(es => es.nonEmpty && es.size <= 64 && es.map(_.step).distinct.size == es.size &&
          es.forall(e => e != null && e.step != null && e.step.nonEmpty && e.candidate != null && e.commit != null &&
            Vector(e.candidate.head, e.candidate.tree, e.commit.identity, e.commit.parent, e.commit.tree).forall(value =>
              value != null && value.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")) && e.changedPaths != null &&
            e.changedPaths.distinct == e.changedPaths && e.diff != null && e.eofCorrections != null &&
            e.eofCorrections.distinct == e.eofCorrections && e.partialCompletion != null &&
            e.partialCompletion.forall(chain => O.validStepCommitChain(chain) && chain.completion == e.commit)))
      if (valid) Right(value) else Left(DecodingFailure("Invalid native candidate extension", cursor.history))
      })
    })
  }
  private[admission] val codec = new WorkflowResultJsonV1.PayloadCodec[Payload] {
    val typeIdentity = "GoalPhaseNativeCandidateReceipt"
    def encode(value: Payload): Json = Json.obj("schemaVersion" -> Json.fromString("sm-workflow.goal-phase-native-candidate.v2"),
      "payload" -> value.asJson)
    def decode(value: Json): Either[String, Payload] = for {
      wire <- Option(value).toRight("ordered candidate is missing")
      schema <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(schema == "sm-workflow.goal-phase-native-candidate.v2", (), "ordered candidate schema differs")
      payload <- wire.hcursor.get[Payload]("payload").left.map(_.message)
    } yield payload
  }
  final case class Saved(record: InstanceRecord, payload: Payload)

  def ownershipC(session: F.Session, d: F.Declaration, core: ActionCall.Core, controller: O.State): Consequence[Unit] = for {
    actual <- G.observeC(core)
    owned = if (controller.releaseRepairActive) controller.plan.steps.flatMap(_.ownedPaths).distinct
      else controller.plan.steps(controller.cursor).ownedPaths
    root <- G.rootC(core)
    _ <- B.continuationC(core, controller.tipSnapshot, actual)
    parent <- GitObjectId.parseC(actual.head)
    pending <- E.executeGitC(core, GitOperation.PendingPaths(parent)).flatMap(_.valueC)
    after <- G.observeC(core)
    afterroot <- G.rootC(core)
    _ <- if (root == d.target.root.toString && afterroot == root && actual.branch == d.target.localBranch &&
        after.branch == actual.branch && after.head == actual.head && after.tree == actual.tree &&
        B.pathsOwned(pending.map(_.value), owned) && B.scopedDirty(after.dirty, owned, false)) Consequence.unit
      else F.invalid("actual repair crosses its original base, branch or selected Step ownership")
  } yield ()

  def retainedC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    from: InstanceRecord, controller: O.State,
    documentPolicy: Option[GoalPhaseDocumentProgress.Policy] = None,
    recordingPaths: Vector[String] = Vector.empty,
    completedCandidates: Vector[Payload] = Vector.empty): Consequence[Saved] =
    retainedOptionC(session, d, core, from, controller, documentPolicy, recordingPaths, completedCandidates).flatMap(_.map(Consequence.success).getOrElse(
      F.invalid("review has no actual saved native candidate")))

  def retainedOptionC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    from: InstanceRecord, controller: O.State,
    documentPolicy: Option[GoalPhaseDocumentProgress.Policy] = None,
    recordingPaths: Vector[String] = Vector.empty,
    completedCandidates: Vector[Payload] = Vector.empty,
    retainedOnly: Boolean = false): Consequence[Option[Saved]] = for {
    _ <- documentPolicy.fold(Consequence.unit)(_.validateC(controller.plan, d.candidatePaths))
    _ <- GoalPhaseNativeFindingRecording.validatePathsC(recordingPaths, controller.plan, d.candidatePaths)
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    store = new Store(adapter, session, from, controller, d, core, _ => Consequence.unit, documentPolicy, recordingPaths, completedCandidates,
      retainedOnly = retainedOnly)
    saved <- store.loadC("candidate").flatMap(_.fold(Consequence.success(Option.empty[Saved]))(
      value => store.completedC(value).map(Some(_))))
  } yield saved

  /** Load the original inspection through the same path/ownership admission as candidates. */
  def recoveryInspectionC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    from: InstanceRecord, controller: O.State, documentPolicy: Option[GoalPhaseDocumentProgress.Policy],
    recordingPaths: Vector[String], completedCandidates: Vector[Payload]): Consequence[Saved] = for {
    _ <- if (controller.stage == O.Stage.InspectCandidate && controller.cursor < controller.plan.steps.size &&
        controller.plan.steps(controller.cursor).ownedPaths.forall(d.candidatePaths.contains)) Consequence.unit
      else F.invalid("source history recovery requires the selected Step inspection and unchanged path policy")
    _ <- documentPolicy.fold(Consequence.unit)(_.validateC(controller.plan, d.candidatePaths))
    _ <- GoalPhaseNativeFindingRecording.validatePathsC(recordingPaths, controller.plan, d.candidatePaths)
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    store = new Store(adapter, session, from, controller, d, core, _ => Consequence.unit,
      documentPolicy, recordingPaths, completedCandidates)
    inspection <- store.loadC("inspection").flatMap(_.map(Consequence.success)
      .getOrElse(F.invalid("source history recovery has no exact saved original inspection")))
    candidate <- store.loadC("candidate")
    stageintent <- store.loadC("stage-intent-0")
    stageresult <- store.loadC("stage-result-0")
    _ <- if (candidate.isEmpty && stageintent.isEmpty && stageresult.isEmpty &&
        inspection.payload.stagedPaths.isEmpty && inspection.payload.candidate.isEmpty &&
        inspection.payload.changedPaths.isEmpty && inspection.payload.diff.isEmpty &&
        inspection.payload.stepEvidence.isEmpty) Consequence.unit
      else F.invalid("source history recovery cannot cross any candidate or staging fact")
  } yield inspection

  def advanceC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    from: InstanceRecord, controller: O.State, acknowledgement: String => Consequence[Unit],
    runValidation: Boolean = true,
    documentPolicy: Option[GoalPhaseDocumentProgress.Policy] = None,
    recordingPaths: Vector[String] = Vector.empty,
    completedCandidates: Vector[Payload] = Vector.empty,
    sourceHistoryRecovery: Option[GoalPhaseSourceHistoryRecovery.RecoveryTarget] = None,
    candidateObservation: Option[GoalPhaseCandidateRecovery.RecoveryTarget] = None,
    candidateObservationContinuation: Option[GoalPhaseCandidateObservationContinuation.Target] = None,
    wholePhaseCandidateRecovery: Option[GoalPhaseWholePhaseCandidateRecovery.Target] = None,
    dependencyAdmission: Option[GoalPhaseDependencyValidation.Admission] = None,
    dependencyChain: Option[GoalPhaseNativeWorkContinuation.DependencyChain] = None,
    sourceHistoryNormalization: Option[GoalPhaseSourceHistoryNormalization.Target] = None,
    dependencySourceDirection: Option[GoalPhaseDependencySourceRevision.Direction] = None): Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult] = for {
    whole <- Consequence.success(controller.cursor == controller.plan.steps.size)
    inspection = if (whole) "PhaseInspection" else "InspectCandidate"
    _ <- if (controller.stage.toString == inspection &&
        from.currentProgression.contains(ProgressionReference("phase." + inspection)))
      Consequence.unit else F.invalid("candidate has no actual admitted implementation successor")
    _ <- if (whole || controller.plan.steps(controller.cursor).ownedPaths.forall(d.candidatePaths.contains)) Consequence.unit
      else F.invalid("host has no fixed staging program for the selected Step coverage")
    _ <- documentPolicy.fold(Consequence.unit)(_.validateC(controller.plan, d.candidatePaths))
    _ <- GoalPhaseNativeFindingRecording.validatePathsC(recordingPaths, controller.plan, d.candidatePaths)
    _ <- dependencyAdmission.fold(Consequence.unit)(GoalPhaseNativeDependencyValidation.resolvedC(_, dependencyChain))
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    store = new Store(adapter, session, from, controller, d, core, acknowledgement, documentPolicy, recordingPaths, completedCandidates,
      sourceHistoryRecovery, dependencyAdmission = dependencyAdmission, dependencyChain = dependencyChain,
      sourceHistoryNormalization = sourceHistoryNormalization, dependencySourceDirection = dependencySourceDirection)
    complete <- store.loadC("candidate")
    selected <- complete match {
      case Some(value) => store.completedC(value)
      case None if wholePhaseCandidateRecovery.nonEmpty => _recover_whole_observation_c(store, wholePhaseCandidateRecovery.get)
      case None => candidateObservation match {
        case Some(target) => _recover_observation_c(store, target, candidateObservationContinuation)
        case None => _inspect_c(store)
      }
    }
    policy = if (whole) d.phaseValidationPolicy else d.validationPolicy
    wire <- if (runValidation && selected.payload.candidate.nonEmpty && policy.nonEmpty)
      GoalPhaseNativeValidation.advanceC(session, d, core, selected, controller, acknowledgement,
        documentPolicy = documentPolicy, recordingPaths = recordingPaths, dependencyChain = dependencyChain)
    else if (runValidation && selected.payload.candidate.nonEmpty &&
        session.current.revision.value > selected.record.revision.value)
      F.invalid("advanced validation has lost its actual host policy")
    else F.waitC(selected.record, if (whole) "Whole-Phase candidate inspection" else "Ordered Step candidate inspection",
      if (selected.payload.candidate.nonEmpty)
        "Actual selected candidate tree and diff are saved. Native checks, independent review and Step/Phase closure remain unfinished."
      else "A native candidate effect has no recorded completed candidate. Its uncertainty is retained; no automatic replay is granted.")
  } yield wire

  private def _inspect_c(store: Store): Consequence[Saved] = for {
    reused <- store.reusePrefixC
    actual <- store.inspectC
    continuation <- if (store.whole) Consequence.success(Option.empty[O.CandidateContinuation])
      else B.continuationC(store.core, store.base, actual)
    parent <- B.parentC(store.base, continuation)
    _ <- store.inspectPinnedC(parent)
    paths = if (store.whole && !store.controller.releaseRepairActive) store.managementPaths.filter(p =>
      actual.dirty.exists(x => x.path == p || x.renamedFrom.contains(p)))
    else store.d.candidatePaths.filter(p => store.ownedPaths.contains(p) &&
      actual.dirty.exists(x => _covers(p, x.path) || x.renamedFrom.exists(_covers(p, _))))
    historypaths = if (store.whole && !store.controller.releaseRepairActive) Vector.empty[String] else actual.dirty.filter(p =>
      p.index != 'D' && p.worktree != 'D' && paths.exists(_covers(_, p.path)) &&
        store.ownedPaths.exists(_covers(_, p.path))).map(_.path).distinct
    history <- GoalPhaseNativeSourceHistory.prepareC(store.core, historypaths.filterNot(reused.contains))
    initial = Payload(store.from.revision.value.toString, store.scopeIdentity,
      paths, Vector.empty, None, Vector.empty, None, continuation,
      dependencyAdmission = store.dependencyAdmission, dependencySourceDirection = store.dependencySourceDirection)
    selected <- store.saveC("inspection", store.from, initial)
    _ <- if (store.whole) store.acknowledgeC("goal-whole-phase-candidate-inspected") else Consequence.unit
    normalized <- GoalPhaseNativeSourceHistory.advanceC(store.session, store.d, store.core, store.from,
      selected.record, store.controller, store.scopeIdentity, store.inspectionStage, history, store.acknowledgeC)
    staged <- selected.payload.selectedPaths.zipWithIndex.foldLeft(Consequence.success(selected.copy(record = normalized))) {
      case (prior, (path, index)) => prior.flatMap { current =>
        if (current.payload.stagedPaths.size != index) Consequence.success(current)
        else for {
          _ <- store.inspectPinnedC(parent)
          prepared <- store.saveC("stage-intent-" + index, current.record, current.payload)
          _ <- store.acknowledgeC("goal-candidate-prepared:" + path)
          actual <- E.stageGoalCandidateC(store.core, store.d.candidatePaths.indexOf(path) + 1)
          _ <- store.acknowledgeC("goal-candidate-executed:" + path)
          _ <- actual.valueC
          recorded <- store.saveC("stage-result-" + index, prepared.record,
            current.payload.copy(stagedPaths = current.payload.stagedPaths :+ path))
          _ <- store.acknowledgeC("goal-candidate-recorded:" + path)
          saved = recorded
        } yield saved
      }
    }
    legacyDependency <- GoalPhaseNativeDependencyValidation.captureC(store.core, store.controller, staged,
      store.loadC, store.saveC, store.acknowledgeC, store.dependencyChain)
    dependency <- GoalPhaseDependencySourceRevision.captureC(store.core, store.controller, legacyDependency,
      store.loadC, store.saveC, store.acknowledgeC)
    candidate <- if (dependency.payload.stagedPaths == dependency.payload.selectedPaths) _observe_c(store, dependency, parent)
      else Consequence.success(staged)
  } yield candidate

  /** Whole-Phase recovery never repeats preparation, source normalization or staging. */
  private def _recover_whole_observation_c(store: Store,
    target: GoalPhaseWholePhaseCandidateRecovery.Target): Consequence[Saved] = for {
    _ <- if (store.whole && GoalPhaseWholePhaseCandidateRecovery.validTarget(target) &&
        target.controller == GoalPhaseCandidateRecovery.controllerSnapshot(store.controller) &&
        target.sessionId == store.from.identity.value && target.participant == store.session.participantIdentity &&
        target.definitionIdentity == store.from.definition.workflowIdentity.value &&
        target.definitionRevision == store.from.definition.workflowRevision.value &&
        target.root == store.d.target.root.toString && target.branch == store.d.target.localBranch &&
        target.candidatePaths == store.d.candidatePaths && target.managementPaths == store.managementPaths &&
        target.documentPolicy == store.documentPolicy && target.recordingPaths == store.recordingPaths &&
        target.completedCandidates == store.completedCandidates &&
        store.from.revision.value == target.frontierRevision.toLong + 1 &&
        store.from.causation.value == "ordered-whole-phase-candidate-recovery:" + target.sourceRevision &&
        store.session.current == store.from) Consequence.unit
      else F.invalid("whole-Phase remaining observation crosses its exact independent direction or controller")
    existing <- store.loadC("observation-intent")
    saved <- existing match {
      case Some(value) => Consequence.success(value)
      case None => for {
        payload <- Consequence.success(target.stagingPrefix.lastOption.map(_.result).getOrElse(target.inspection)
          .copy(sourceRevision = store.from.revision.value.toString))
        intent <- store.saveC("observation-intent", store.from, payload)
        _ <- store.acknowledgeC("goal-whole-phase-candidate-observation-prepared")
        _ <- GoalPhaseWholePhaseCandidateRecovery.requirePolicyC(store.d, store.core, target)
        actual <- GoalPhaseWholePhaseCandidateRecovery.observeRepositoryC(store.core, target)
        _ <- if (actual == target.repository) Consequence.unit
          else F.invalid("whole-Phase approved repository/index snapshot changed")
        result <- _observe_c(store, intent, store.base, Some(actual.indexTree), Some(actual))
      } yield result
    }
  } yield saved

  /** Persist before the first File/Git observation; an unresolved fresh intent is never replayed. */
  private def _recover_observation_c(store: Store, target: GoalPhaseCandidateRecovery.RecoveryTarget,
    continuation: Option[GoalPhaseCandidateObservationContinuation.Target]): Consequence[Saved] = for {
    _ <- if (!store.whole && GoalPhaseCandidateRecovery.validTarget(target) && GoalPhaseCandidateRecovery.observationAllowed(target) &&
        target.inspection.dependencyAdmission == store.dependencyAdmission &&
        target.controller == GoalPhaseCandidateRecovery.controllerSnapshot(store.controller) &&
        target.sessionId == store.from.identity.value && target.participant == store.session.participantIdentity &&
        target.definitionIdentity == store.from.definition.workflowIdentity.value &&
        target.definitionRevision == store.from.definition.workflowRevision.value &&
        target.root == store.d.target.root.toString && target.branch == store.d.target.localBranch &&
        target.candidatePaths == store.d.candidatePaths && target.managementPaths == store.managementPaths &&
        (continuation match {
          case None => store.from.revision.value == target.frontierRevision.toLong + 1 &&
            store.from.causation.value == "ordered-candidate-recovery:" + target.sourceRevision
          case Some(value) => GoalPhaseCandidateObservationContinuation.validTarget(value) && value.original == target &&
            store.from.revision.value == value.frontierRevision.toLong + 1 &&
            store.from.causation.value == "ordered-candidate-observation-continuation:" + value.directionSourceRevision
        }) &&
        store.session.current == store.from) Consequence.unit
      else F.invalid("candidate observation crosses its exact admitted direction or controller")
    existing <- store.loadC("observation-intent")
    result <- existing match {
      case Some(value) => Consequence.success(value)
      case None => for {
        parent <- B.parentC(store.base, target.inspection.continuation)
        payload = continuation.map(GoalPhaseCandidateObservationContinuation.preparedPayload)
          .getOrElse(GoalPhaseCandidateRecovery.preparedPayload(target)).copy(sourceRevision = store.from.revision.value.toString)
        intent <- store.saveC("observation-intent", store.from, payload)
        _ <- store.acknowledgeC("goal-candidate-observation-prepared")
        budget <- org.goldenport.cncf.processexecution.GitDiffParameters.resolveC(store.core.executionContext)
        _ <- if (continuation.forall(_.maximumDiffBytes == budget)) Consequence.unit
          else F.invalid("candidate observation continuation selected CNCF diff budget changed")
        snapshot <- continuation match {
          case None => Consequence.success(Option.empty[GoalPhaseCandidateObservationContinuation.RepositorySnapshot])
          case Some(value) => GoalPhaseCandidateObservationContinuation.observeRepositoryC(store.core, target).flatMap { actual =>
            if (actual == value.repository) Consequence.success(Some(actual))
            else F.invalid("candidate observation continuation approved repository/index snapshot changed")
          }
        }
        _ <- continuation.flatMap(_.dependencyRepository).fold(Consequence.unit) { captured =>
          payload.dependencyAdmission.map(a => GoalPhaseNativeDependencyValidation.snapshotC(store.core, a, store.dependencyChain).flatMap { actual =>
            if (actual == captured) Consequence.unit else F.invalid("authorized dependency repository/index snapshot changed")
          }).getOrElse(F.invalid("dependency snapshot has no admitted subject"))
        }
        dependency <- if (payload.dependency.nonEmpty) payload.dependency.fold(Consequence.success(intent))(capture =>
          GoalPhaseNativeDependencyValidation.freshnessC(store.core, capture, store.dependencyChain).map(_ => intent))
          else if (payload.dependencyAdmission.nonEmpty) GoalPhaseNativeDependencyValidation.observeC(
            store.core, store.controller, intent, target.sourceRevision, store.saveC, store.acknowledgeC, store.dependencyChain)
          else Consequence.success(intent)
        before <- store.inspectPinnedC(parent)
        _ <- if (B.scopedDirty(before.dirty, payload.selectedPaths, true)) Consequence.unit
          else F.invalid("recovered candidate has changed selected worktree or index ownership")
        tree <- G.indexTreeC(store.core)
        _ <- GoalPhaseNativeSourceHistory.verifyCandidatePrefixC(store.core, target)
        after <- store.inspectPinnedC(parent)
        aftertree <- G.indexTreeC(store.core)
        _ <- if (after == before && tree == aftertree) Consequence.unit
          else F.invalid("candidate repository or index changed while checking saved history")
        saved <- _observe_c(store, dependency, parent, Some(tree), snapshot)
      } yield saved
    }
  } yield result

  /** Shared remaining post-staging observation. Recovery never enters the setup/staging loop. */
  private def _observe_c(store: Store, staged: Saved, parent: O.Snapshot,
    expectedIndex: Option[String] = None,
    expectedSnapshot: Option[GoalPhaseCandidateObservationContinuation.RepositorySnapshot] = None): Consequence[Saved] = for {
      _ <- staged.payload.dependency.fold(Consequence.unit)(GoalPhaseNativeDependencyValidation.freshnessC(store.core, _, store.dependencyChain))
      _ <- staged.payload.dependencySourceCapture.fold(Consequence.unit)(GoalPhaseDependencySourceRevision.freshnessC(store.core, _))
      actual <- store.inspectPinnedC(parent)
      _ <- if (B.scopedDirty(actual.dirty, store.stagingPaths, true)) Consequence.unit
        else F.invalid("candidate has unstaged or unsupported native changes")
      tree <- G.indexTreeC(store.core)
      _ <- if (expectedIndex.forall(_ == tree)) Consequence.unit
        else F.invalid("candidate index changed before remaining observation")
      _ <- if (expectedSnapshot.forall(_ == GoalPhaseCandidateObservationContinuation.snapshot(
          store.d.target.root.toString, actual, tree))) Consequence.unit
        else F.invalid("candidate approved repository/index snapshot changed before remaining observation")
      management <- if (store.whole && !store.controller.releaseRepairActive && store.managementPaths.nonEmpty) for {
        tip <- GitObjectId.parseC(store.base.head)
        pending <- E.executeGitC(store.core, GitOperation.PendingPaths(tip)).flatMap(_.valueC)
        paths = pending.map(_.value)
        _ <- if (paths.distinct == paths && paths.forall(store.managementPaths.contains) &&
            paths.forall(staged.payload.stagedPaths.contains)) Consequence.unit
          else F.invalid("whole-Phase management delta crosses actual selected source staging")
      } yield paths else Consequence.success(Vector.empty[String])
      actualparent <- GitObjectId.parseC(parent.head)
      global <- E.executeGitC(store.core, GitOperation.PendingPaths(actualparent)).flatMap(_.valueC)
      _ <- if (B.pathsOwned(global.map(_.value), store.stagingPaths)) Consequence.unit
        else F.invalid("candidate index crosses actual selected ownership")
      diffbase <- GitObjectId.parseC(if (store.whole && !store.scopedPhase) store.controller.base.head else parent.head)
      nativepaths <- E.executeGitC(store.core, GitOperation.PendingPaths(diffbase)).flatMap(_.valueC)
      pendingpaths = nativepaths.map(_.value)
      _ <- if (B.pathsOwned(pendingpaths, store.ownedPaths)) Consequence.unit
        else F.invalid("candidate diff crosses selected Step ownership")
      nativediff <- E.executeGitC(store.core, GitOperation.PendingDiff(diffbase)).flatMap(_.valueC)
      evidence <- store.phaseEvidenceC
      paths = (evidence.toVector.flatten.flatMap(e => e.changedPaths ++ e.eofCorrections) ++ pendingpaths).distinct
      diff = (evidence.toVector.flatten.map(e => e.diff + (if (e.eofCorrections.isEmpty) "" else
        "\nNative Step commit " + e.commit.identity + " includes permitted EOF corrections after review: " + e.eofCorrections.mkString(", ") + "\n")) :+ nativediff.text).mkString("\n")
      _ <- if (!nativediff.isBinary) Consequence.unit
        else F.invalid("candidate diff is binary")
      after <- store.inspectPinnedC(parent)
      aftertree <- G.indexTreeC(store.core)
      _ <- if (after == actual && aftertree == tree && B.scopedDirty(after.dirty, store.stagingPaths, true)) Consequence.unit
        else F.invalid("candidate branch, root, parent, ownership or index changed during observation")
      _ <- staged.payload.dependency.fold(Consequence.unit)(GoalPhaseNativeDependencyValidation.freshnessC(store.core, _, store.dependencyChain))
      _ <- staged.payload.dependencySourceCapture.fold(Consequence.unit)(GoalPhaseDependencySourceRevision.freshnessC(store.core, _))
      snapshot = O.Snapshot(actual.head, tree)
      payload = staged.payload.copy(candidate = Some(snapshot), changedPaths = paths, diff = Some(diff), stepEvidence = evidence)
      _ <- if (!store.whole || (if (store.controller.releaseRepairActive)
        global.isEmpty == (snapshot.tree == store.base.tree)
        else management.isEmpty == (snapshot.tree == store.base.tree))) Consequence.unit
        else F.invalid("whole-Phase management paths contradict the actual index tree")
      inspected <- observedStateC(store.controller, payload, store.documentPolicy, store.recordingPaths)
      _ <- if (inspected.stage.toString == store.validationStage &&
          domain.statemachine.GoalPhaseRunLifecycle.permits(store.inspectionStage,
            if (store.whole) "phase-candidate-observed" else "candidate-observed", store.validationStage)) Consequence.unit
        else F.invalid("native candidate successor differs from CML/controller")
      saved <- store.saveC("candidate", staged.record, payload)
      _ <- store.acknowledgeC("goal-candidate-observed")
  } yield saved

  private[admission] def observedStateC(controller: O.State, payload: Payload,
    documentPolicy: Option[GoalPhaseDocumentProgress.Policy], recordingPaths: Vector[String] = Vector.empty): Consequence[O.State] = {
    val whole = controller.cursor == controller.plan.steps.size
    val management = (documentPolicy.toVector.flatMap(_.paths) ++ recordingPaths).distinct
    if (payload.dependencyAdmission.nonEmpty && !payload.dependency.exists(c =>
        GoalPhaseDependencyValidation.captureValid(c) && payload.dependencyAdmission.contains(c.admission)))
      return F.invalid("candidate lost its required captured dependency")
    if (!GoalPhaseDependencySourceRevision.candidateValid(payload) ||
        payload.dependencySourceDirection.nonEmpty && payload.dependencySourceCapture.isEmpty)
      return F.invalid("candidate lost its required actual source-revision capture")
    if (whole && payload.continuation.nonEmpty || !whole && payload.stepEvidence.nonEmpty)
      return F.invalid("candidate extension crosses its selected Step or whole-Phase scope")
    payload.candidate match {
      case Some(snapshot) if whole && controller.releaseRepairActive => for {
        _ <- if (payload.selectedPaths == payload.stagedPaths &&
          B.pathsOwned(payload.selectedPaths, controller.plan.steps.flatMap(_.ownedPaths).distinct) &&
          B.pathsOwned(payload.changedPaths, controller.plan.steps.flatMap(_.ownedPaths).distinct)) Consequence.unit
          else F.invalid("release repair candidate crosses the unchanged accepted Plan scope")
        state <- controller.acceptC(O.CandidateObserved(snapshot))
      } yield state
      case Some(snapshot) if whole && snapshot.tree != controller.tipSnapshot.tree => for {
        _ <- if (payload.selectedPaths.nonEmpty && payload.selectedPaths == payload.stagedPaths &&
          payload.selectedPaths.distinct == payload.selectedPaths && payload.selectedPaths.forall(management.contains)) Consequence.unit
          else F.invalid("management candidate differs from its actually selected and staged source paths")
        state <- controller.acceptC(O.PhaseManagementCandidateObserved(snapshot, payload.selectedPaths))
      } yield state
      case Some(snapshot) if !whole && payload.continuation.nonEmpty =>
        controller.acceptC(O.CandidateContinuationObserved(snapshot, payload.continuation.get))
      case Some(snapshot) if payload.continuation.isEmpty => controller.acceptC(O.CandidateObserved(snapshot))
      case Some(_) => F.invalid("whole-Phase candidate cannot introduce a continuation")
      case None => F.invalid("candidate observation has no actual snapshot")
    }
  }

  /** Retained receipt structure is checked independently of current Git authority. */
  private[admission] def retainedEvidenceC(payload: Payload, controller: O.State,
    completedCandidates: Vector[Payload], managementPaths: Vector[String]): Consequence[Unit] = payload.stepEvidence match {
    case None if controller.cursor == controller.plan.steps.size && payload.candidate.nonEmpty &&
        controller.completed.exists(_.partialCompletion.nonEmpty) =>
      F.invalid("partial-chain whole-Phase history requires its retained Step evidence")
    case None => Consequence.unit // Legacy ordinary receipts retain their original full-diff interpretation.
    case Some(values) if controller.cursor == controller.plan.steps.size &&
        (completedCandidates.exists(_.continuation.nonEmpty) || controller.completed.exists(_.partialCompletion.nonEmpty)) && values.size == controller.completed.size &&
        completedCandidates.size == values.size && values.zipWithIndex.forall { (value, index) =>
          val actual = completedCandidates(index)
          val closed = controller.completed(index)
          value.step == closed.step && value.commit == closed.commit && value.partialCompletion == closed.partialCompletion && actual.step == closed.step &&
            actual.candidate.contains(value.candidate) && actual.changedPaths == value.changedPaths && actual.diff.contains(value.diff) &&
            B.pathsOwned(value.eofCorrections, controller.plan.steps(index).ownedPaths) &&
            (value.eofCorrections.isEmpty == (value.candidate.tree == closed.commit.tree))
        } =>
      val accepted = values.flatMap(value => value.changedPaths ++ value.eofCorrections).distinct
      if (payload.changedPaths.take(accepted.size) == accepted &&
          payload.changedPaths.drop(accepted.size).forall(path =>
            (if (controller.releaseRepairActive) controller.plan.steps.exists(_.ownedPaths.exists(_covers(_, path)))
              else managementPaths.contains(path)) && payload.stagedPaths.exists(_covers(_, path))) &&
          values.forall(value => payload.diff.exists(_.contains(value.diff)))) Consequence.unit
      else F.invalid("whole-Phase evidence changes exact successful Step facts or includes imported work")
    case _ => F.invalid("whole-Phase retained Step evidence differs from actual successful replay")
  }

  private final class Store(val adapter: WorkflowManagedMechanicalTransition[Payload], val session: F.Session,
    val from: InstanceRecord, val controller: O.State, val d: F.Declaration, val core: ActionCall.Core,
    acknowledgement: String => Consequence[Unit], val documentPolicy: Option[GoalPhaseDocumentProgress.Policy],
    val recordingPaths: Vector[String], val completedCandidates: Vector[Payload],
    val sourceHistoryRecovery: Option[GoalPhaseSourceHistoryRecovery.RecoveryTarget] = None,
    val retainedOnly: Boolean = false,
    val dependencyAdmission: Option[GoalPhaseDependencyValidation.Admission] = None,
    val dependencyChain: Option[GoalPhaseNativeWorkContinuation.DependencyChain] = None,
    val sourceHistoryNormalization: Option[GoalPhaseSourceHistoryNormalization.Target] = None,
    val dependencySourceDirection: Option[GoalPhaseDependencySourceRevision.Direction] = None) {
    val plan = controller.plan
    val whole = controller.cursor == plan.steps.size
    val scopeIdentity = if (whole) "phase:" + plan.phase else plan.steps(controller.cursor).identity
    val ownedPaths = if (whole) plan.steps.flatMap(_.ownedPaths).distinct else plan.steps(controller.cursor).ownedPaths
    val inspectionStage = if (whole) "PhaseInspection" else "InspectCandidate"
    val validationStage = if (whole) "PhaseValidation" else "Validation"
    val base = controller.tipSnapshot
    val managementPaths = (documentPolicy.toVector.flatMap(_.paths) ++ recordingPaths).distinct
    val stagingPaths = if (whole && !controller.releaseRepairActive) managementPaths else ownedPaths
    val scopedPhase = whole && (completedCandidates.exists(_.continuation.nonEmpty) ||
      controller.completed.exists(_.partialCompletion.nonEmpty))
    def reusePrefixC: Consequence[Vector[String]] = for {
      prefix <- _reuse_prefix_c
      affected <- sourceHistoryNormalization match {
        case None => Consequence.success(Vector.empty[String])
        case Some(target) if sourceHistoryRecovery.contains(target.recovery) && target.recovery.planRevision == plan.revision =>
          GoalPhaseSourceHistoryNormalization.verifyAffectedC(core, target).map(Vector(_))
        case _ => F.invalid("normalization continuation has no matching admitted original source")
      }
    } yield (prefix ++ affected).distinct
    private def _reuse_prefix_c: Consequence[Vector[String]] = sourceHistoryRecovery match {
      case None => Consequence.success(Vector.empty)
      case Some(target) if !whole && GoalPhaseSourceHistoryRecovery.validTarget(target) &&
          target.sessionId == from.identity.value && target.definitionIdentity == from.definition.workflowIdentity.value &&
          target.definitionRevision == from.definition.workflowRevision.value && target.participant == session.participantIdentity &&
          target.planIdentity == plan.identity && target.step == scopeIdentity && target.controllerTip == base &&
          target.base == session.started.receipt.payload.value.base && target.candidatePaths == d.candidatePaths &&
          target.ownedPaths == ownedPaths && target.managementPaths == managementPaths &&
          from.revision.value > target.frontierRevision.toLong =>
        GoalPhaseNativeSourceHistory.verifyCompletedPrefixC(core, target)
      case _ => F.invalid("source history reuse crosses its admitted selected-Step continuation")
    }
    def phaseEvidenceC: Consequence[Option[Vector[StepEvidence]]] = if (!scopedPhase) Consequence.success(None)
      else for {
        _ <- if (completedCandidates.size == controller.completed.size &&
            completedCandidates.map(_.step) == controller.completed.map(_.step)) Consequence.unit
          else F.invalid("whole-Phase evidence has no exact successful ordered Step candidates")
        evidence <- completedCandidates.zip(controller.completed).foldLeft(Consequence.success(Vector.empty[StepEvidence])) {
          case (prior, (payload, closed)) => for {
            values <- prior
            snapshot <- payload.candidate.map(Consequence.success).getOrElse(F.invalid("successful Step lost its actual candidate"))
            before <- GitObjectId.parseC(snapshot.tree)
            after <- GitObjectId.parseC(closed.commit.tree)
            changes <- E.executeGitC(core, GitOperation.TreeChanges(before, after)).flatMap(_.valueC)
            corrections = changes.map(_.path.value)
            _ <- if (B.pathsOwned(corrections, plan.steps(values.size).ownedPaths) && payload.diff.nonEmpty)
              Consequence.unit else F.invalid("successful Step correction crosses retained ownership")
          } yield values :+ StepEvidence(closed.step, snapshot, payload.changedPaths, payload.diff.get, closed.commit, corrections, closed.partialCompletion)
        }
      } yield Some(evidence)
    private def _evidence_c(payload: Payload): Consequence[Unit] =
      retainedEvidenceC(payload, controller, completedCandidates, managementPaths).flatMap { _ =>
        payload.stepEvidence match {
          case None => Consequence.unit
          case Some(_) if retainedOnly => Consequence.unit
          case Some(values) => phaseEvidenceC.flatMap { actual =>
            if (actual.contains(values)) Consequence.unit
            else F.invalid("whole-Phase evidence changes exact successful Step facts or includes imported work")
          }
        }
      }
    private def _reference(key: String) = WorkflowMechanicalTransition.Reference(from.identity.value +
      ":ordered-candidate:" + from.revision.value + ":" + key, "1")
    def loadSourceC(source: String, key: String): Consequence[Option[Saved]] =
      adapter.loadRetainedC(WorkflowMechanicalTransition.Reference(from.identity.value +
        ":ordered-candidate:" + source + ":" + key, "1"))(using core.executionContext).map(
        _.map(value => Saved(value.receipt.next, value.receipt.payload.value)))
    def loadC(key: String): Consequence[Option[Saved]] = adapter.loadRetainedC(_reference(key))(using core.executionContext).flatMap {
      case None => Consequence.success(None)
      case Some(value) =>
        val p = value.receipt.payload.value
        if (p.sourceRevision == from.revision.value.toString && p.step == scopeIdentity &&
            p.selectedPaths.distinct == p.selectedPaths && (if (whole && !controller.releaseRepairActive) p.selectedPaths.isEmpty ||
              p.selectedPaths.forall(managementPaths.contains) else
              p.selectedPaths.forall(x => ownedPaths.contains(x) && d.candidatePaths.contains(x))) &&
            p.stagedPaths == p.selectedPaths.take(p.stagedPaths.size) && p.stagedPaths.size <= p.selectedPaths.size &&
            p.changedPaths.distinct == p.changedPaths)
          B.parentC(base, p.continuation).flatMap(_ => _evidence_c(p)).map(_ => Some(Saved(value.receipt.next, p)))
        else F.invalid("retained candidate crosses the actual source, Step or host path policy")
    }
    def saveC(key: String, current: InstanceRecord, payload: Payload): Consequence[Saved] = for {
      _ <- if (current.currentProgression.contains(ProgressionReference("phase." + inspectionStage))) Consequence.unit
        else F.invalid("candidate preparation has no native unsuspended inspection frontier")
      saved <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, _reference(key), current, current.revision,
        F.history(current, "phase." + (if (payload.candidate.nonEmpty) validationStage else inspectionStage),
          "ordered-candidate:" + key), TypedValue(codec.typeIdentity, payload)),
        ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
    } yield Saved(saved.receipt.next, saved.receipt.payload.value)
    def completedC(value: Saved): Consequence[Saved] =
      if (value.record.currentProgression.contains(ProgressionReference("phase." + validationStage)) &&
          value.payload.candidate.exists(snapshot => B.parentC(base, value.payload.continuation).toOption.exists(_.head == snapshot.head)) && value.payload.diff.nonEmpty &&
          value.payload.stagedPaths == value.payload.selectedPaths && value.payload.changedPaths.forall(p => ownedPaths.exists(_covers(_, p))))
        GoalPhaseNativeDependencyValidation.retainedC(value, loadC, Some(loadSourceC)).flatMap(_ =>
          GoalPhaseDependencySourceRevision.retainedCaptureC(value, loadC)).flatMap(_ =>
          observedStateC(controller, value.payload, documentPolicy, recordingPaths)).map(_ => value)
      else F.invalid("retained candidate is not its actual native Validation successor")
    def inspectC: Consequence[G.Observed] = for {
      root <- G.rootC(core)
      actual <- G.observeC(core)
      _ <- if (root == d.target.root.toString && actual.branch == d.target.localBranch &&
          (!whole || (actual.head == base.head && actual.tree == base.tree)) &&
          B.scopedDirty(actual.dirty, stagingPaths, false)) Consequence.unit
        else F.invalid("actual candidate crosses its original base, branch or selected Step ownership")
    } yield actual
    def inspectPinnedC(parent: O.Snapshot): Consequence[G.Observed] = for {
      actual <- inspectC
      _ <- if (actual.head == parent.head && actual.tree == parent.tree) Consequence.unit
        else F.invalid("candidate actual parent changed after inspection")
      identity <- GitObjectId.parseC(parent.head)
      pending <- E.executeGitC(core, GitOperation.PendingPaths(identity)).flatMap(_.valueC)
      _ <- if (B.pathsOwned(pending.map(_.value), stagingPaths)) Consequence.unit
        else F.invalid("candidate global index crosses selected ownership before staging")
    } yield actual
    def acknowledgeC(stage: String): Consequence[Unit] = acknowledgement(stage) match {
      case Consequence.Success(_) => Consequence.unit
      case _ => org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate(
        "GoalPhase candidate acknowledgement interrupted; retained intent is not replayed: " + stage)
    }
  }
  private def _covers(owner: String, path: String): Boolean = path == owner || path.startsWith(owner + "/")
}
