package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseNativeRun as F
import GoalPhaseNativeClosure as K
import GoalPhaseOrderedProgression as O
import RepositorySyncManagedGitObservation as G
import WorkflowManagedProcessExecution as E
import GoalPhasePartialCommitClosure as P

/** Explicit recovery of an unresolved selected Step commit. Old intent and
  * terminal observations are immutable; direction never executes a commit.
  * @since   Oct. 22, 2026
  * @version Oct. 27, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseClosureRecovery {
  val purpose = "goal-phase-closure-recovery"
  val describeOperation = "describeGoalPhaseClosureRecovery"
  val recoveryOperation = "recoverGoalPhaseClosure"
  val choices = Vector("AuthorizeNewAttempt", "ReconcileCommit", "CompletePartialCommit", "Defer")
  def choicesFor(target: RecoveryTarget): Vector[String] = choices.filter(choice => choiceC(target, choice).isSuccess)
  val continuationMessage = "The saved actual local selected commit execution ended and its current typed Git " +
    "observation permits mechanical continuation under the existing Step closure authority. Use ordinary " +
    "advanceGoalPhase for this same Session. No new Human recovery direction or repeated Test/review is required."
  val message = "The original commit intent remains unknown unless its actual execution summary was saved. " +
    "Independent trusted Human evidence must attest that its prior execution owner ended. AuthorizeNewAttempt " +
    "permits a NEW commit attempt and may repeat an old effect; ReconcileCommit records only an actual matching " +
    "single-parent commit. CompletePartialCommit permits one new completion commit only for a proved known successful " +
    "partial commit, retaining the actual original-parent -> partial -> completion chain. Recovery appends direction only. " +
    "A separate ordinary Advance rechecks the exact live " +
    "Git observation and physical selection. Original Session, Plan, accepted checks/review, history and counters remain. " +
    "Defer changes nothing. Authentication, unchanged HEAD and an absent receipt do not prove termination or nonexecution."

  // Derive each small nested contract once before composing recovery codecs.
  private given _snapshot_encoder: Encoder[O.Snapshot] = deriveEncoder
  private given _snapshot_decoder: Decoder[O.Snapshot] = deriveDecoder
  private given _dirty_path_encoder: Encoder[G.DirtyPath] = deriveEncoder
  private given _dirty_path_decoder: Decoder[G.DirtyPath] = deriveDecoder
  private given _commit_encoder: Encoder[G.Commit] = deriveEncoder
  private given _commit_decoder: Decoder[G.Commit] = deriveDecoder
  private given _reference_encoder: Encoder[ContextReference] = deriveEncoder
  private given _reference_decoder: Decoder[ContextReference] = deriveDecoder

  final case class Observation(root: String, branch: String, head: String, tree: String,
    indexTree: String, mergeHead: Option[String], dirty: Vector[G.DirtyPath], selectedPaths: Vector[String],
    selectedChangesEmpty: Boolean, pendingPaths: Vector[String], commit: G.Commit)
  object Observation {
    given Encoder[Observation] = deriveEncoder
    given Decoder[Observation] = deriveDecoder
  }
  final case class Execution(input: K.Input, ordinal: Int, attempt: Int, operation: String, target: String,
    outcome: String, termination: Option[String], exitCode: Option[Int], stderr: String,
    selectedPaths: Vector[String], stderrTruncated: Boolean = false)
  object Execution {
    given Encoder[Execution] = deriveEncoder
    given Decoder[Execution] = deriveDecoder
  }
  final case class RecoveryTarget(sessionId: String, definitionIdentity: String, definitionRevision: String,
    participant: String, planIdentity: String, planRevision: String, step: String, generation: Long,
    sourceRevision: String, frontierRevision: String, intentIdentity: String, intentRevision: String,
    intentRecordRevision: String, ordinal: Int, attempt: Int, parent: O.Snapshot, candidate: O.Snapshot,
    reviewEvidence: Option[String], ownedPaths: Vector[String], observation: Observation, execution: Option[Execution],
    admission: Option[GoalPhaseStepAssessment.Admission] = None,
    partialCompletion: Option[P.Proof] = None) {
    def input: K.Input = K.Input(sessionId, sourceRevision, planIdentity, planRevision, step, generation,
      parent, candidate, reviewEvidence, admission)
  }
  object RecoveryTarget {
    given Encoder[RecoveryTarget] = Encoder.instance(value => Json.obj(
      "sessionId" -> value.sessionId.asJson,
      "definitionIdentity" -> value.definitionIdentity.asJson,
      "definitionRevision" -> value.definitionRevision.asJson,
      "participant" -> value.participant.asJson,
      "planIdentity" -> value.planIdentity.asJson,
      "planRevision" -> value.planRevision.asJson,
      "step" -> value.step.asJson,
      "generation" -> value.generation.asJson,
      "sourceRevision" -> value.sourceRevision.asJson,
      "frontierRevision" -> value.frontierRevision.asJson,
      "intentIdentity" -> value.intentIdentity.asJson,
      "intentRevision" -> value.intentRevision.asJson,
      "intentRecordRevision" -> value.intentRecordRevision.asJson,
      "ordinal" -> value.ordinal.asJson,
      "attempt" -> value.attempt.asJson,
      "parent" -> value.parent.asJson,
      "candidate" -> value.candidate.asJson,
      "reviewEvidence" -> value.reviewEvidence.asJson,
      "ownedPaths" -> value.ownedPaths.asJson,
      "observation" -> value.observation.asJson,
      "execution" -> value.execution.asJson).mapObject { fields =>
        val admitted = value.admission.fold(fields)(a => fields.add("admission", a.asJson))
        value.partialCompletion.fold(admitted)(p => admitted.add("partialCompletion", p.asJson))
      })
    given Decoder[RecoveryTarget] = Decoder.instance(cursor => for {
      sessionid <- cursor.get[String]("sessionId")
      definitionidentity <- cursor.get[String]("definitionIdentity")
      definitionrevision <- cursor.get[String]("definitionRevision")
      participant <- cursor.get[String]("participant")
      planidentity <- cursor.get[String]("planIdentity")
      planrevision <- cursor.get[String]("planRevision")
      step <- cursor.get[String]("step")
      generation <- cursor.get[Long]("generation")
      sourcerevision <- cursor.get[String]("sourceRevision")
      frontierrevision <- cursor.get[String]("frontierRevision")
      intentidentity <- cursor.get[String]("intentIdentity")
      intentrevision <- cursor.get[String]("intentRevision")
      intentrecordrevision <- cursor.get[String]("intentRecordRevision")
      ordinal <- cursor.get[Int]("ordinal")
      attempt <- cursor.get[Int]("attempt")
      parent <- cursor.get[O.Snapshot]("parent")
      candidate <- cursor.get[O.Snapshot]("candidate")
      reviewevidence <- cursor.get[Option[String]]("reviewEvidence")
      admission <- cursor.get[Option[GoalPhaseStepAssessment.Admission]]("admission")
      ownedpaths <- cursor.get[Vector[String]]("ownedPaths")
      observation <- cursor.get[Observation]("observation")
      execution <- cursor.get[Option[Execution]]("execution")
      partial <- cursor.get[Option[P.Proof]]("partialCompletion")
      _ <- Either.cond(!cursor.downField("partialCompletion").focus.exists(_.isNull), (),
        io.circe.DecodingFailure("Partial completion proof cannot be null", cursor.history))
    } yield RecoveryTarget(sessionid, definitionidentity, definitionrevision, participant, planidentity, planrevision,
      step, generation, sourcerevision, frontierrevision, intentidentity, intentrevision, intentrecordrevision,
      ordinal, attempt, parent, candidate, reviewevidence, ownedpaths, observation, execution, admission, partial))
  }
  final case class Direction(target: RecoveryTarget, choice: String, rationale: String,
    participant: String, reference: ContextReference, priorExecutionEnded: Boolean)
  object Direction {
    given Encoder[Direction] = deriveEncoder
    given Decoder[Direction] = deriveDecoder
  }
  final case class ExecutionSaved(record: InstanceRecord, payload: Execution)
  final case class DirectionSaved(record: InstanceRecord, payload: Direction)
  final case class Continuation(target: RecoveryTarget, action: String)
  object Continuation {
    given Encoder[Continuation] = deriveEncoder
    given Decoder[Continuation] = deriveDecoder
  }
  final case class ContinuationSaved(record: InstanceRecord, payload: Continuation)

  /** A finite actual exit, never a diagnostic or an inferred absence of effect. */
  def terminalExecution(value: Execution): Boolean = validExecution(value) && value.exitCode.exists { code =>
    value.termination.contains("Exited(" + code + ")") &&
      ((value.outcome == "failed" && code != 0) || (value.outcome == "succeeded" && code == 0))
  }
  def continuationAction(target: RecoveryTarget): Option[String] =
    Option(target).filter(validTarget).filter(_.execution.exists(terminalExecution)).flatMap { actual =>
      if (choiceC(actual, "ReconcileCommit").isSuccess) Some("reconcile-commit")
      else if (actual.execution.exists(result => result.outcome == "failed" && result.exitCode.exists(_ != 0)) &&
          choiceC(actual, "AuthorizeNewAttempt").isSuccess) Some("retry-preparation")
      else None
    }

  trait EvidencePort {
    def admitC(core: ActionCall.Core, reference: ContextReference, binding: Json): Consequence[Unit]
  }
  final case class Installation(evidence: EvidencePort) {
    def validateC: Consequence[Unit] = if (evidence != null) Consequence.unit
      else Consequence.configurationInvalid("Closure recovery requires independent trusted Human evidence")
  }

  def targetRecord(value: RecoveryTarget): Record = GoalPhaseNativeMetadata.record(value.asJson)
  def targetC(value: Record): Consequence[RecoveryTarget] = if (value == null) _invalid("target is absent")
    else value.toJsonStringC.flatMap(text => io.circe.parser.parse(text).left.map(_.message)
      .flatMap(GoalPhaseNativeMetadata.normalizePublicJson).flatMap(decodeTarget).fold(_invalid, Consequence.success))
  def decodeTarget(value: Json): Either[String, RecoveryTarget] = for {
    wire <- Option(value).toRight("Closure recovery target is absent")
    _ <- Either.cond(wire.hcursor.downField("generation").focus.flatMap(_.asNumber).flatMap(_.toLong).isDefined &&
      Vector("ordinal", "attempt").forall(key =>
      wire.hcursor.downField(key).focus.flatMap(_.asNumber).flatMap(_.toInt).isDefined), (),
      "Closure recovery generation, ordinal and attempt require JSON integers")
    target <- wire.as[RecoveryTarget].left.map(_.message)
    _ <- Either.cond(validTarget(target), (), "Closure recovery target is incomplete or crossed")
  } yield target

  def validPaths(values: Vector[String]): Boolean = values != null && values.nonEmpty && values.size <= 256 &&
    values.distinct == values && values.forall(path => path != null && GitPath.parseC(path).isSuccess)
  def validExecution(value: Execution): Boolean = value != null && value.input != null &&
    Vector(value.input.instance, value.input.plan, value.input.planRevision, value.input.step).forall(_exact) &&
    O.validStepAcceptance(O.ClosedStep(value.input.step, O.Commit("", "", ""), value.input.reviewEvidence, value.input.admission)) &&
    value.input.admission.forall(_.assessment.candidate == value.input.candidate) &&
    _revision(value.input.sourceRevision) && value.input.generation > 0 &&
    _snapshot(value.input.parent) && _snapshot(value.input.candidate) && value.input.candidate.head == value.input.parent.head &&
    value.ordinal > 0 && value.ordinal <= 64 && value.attempt >= 0 && value.attempt < 256 &&
    value.operation == "commit-selected" && value.target == "sm-workflow-repository" &&
    Set("succeeded", "failed", "incomplete", "invalid-output", "conflict-observation-required")(value.outcome) &&
    value.termination != null && value.termination.forall(_exact) && value.exitCode != null &&
    value.stderr != null && value.stderr.length <= 8192 && !value.stderr.contains('\u0000') &&
    value.selectedPaths != null && value.selectedPaths.size <= 256 &&
    value.selectedPaths.distinct == value.selectedPaths && value.selectedPaths.forall(p => p != null && GitPath.parseC(p).isSuccess)
  def validTarget(value: RecoveryTarget): Boolean = value != null &&
    Vector(value.sessionId, value.definitionIdentity, value.definitionRevision, value.participant,
      value.planIdentity, value.planRevision, value.step).forall(_exact) &&
    O.validStepAcceptance(O.ClosedStep(value.step, O.Commit("", "", ""), value.reviewEvidence, value.admission)) &&
    value.admission.forall(_.assessment.candidate == value.candidate) &&
    value.generation > 0 && value.ordinal > 0 && value.ordinal <= 64 && value.attempt >= 0 && value.attempt < 255 &&
    Vector(value.sourceRevision, value.frontierRevision, value.intentRecordRevision).forall(_revision) &&
    value.sourceRevision.toLong < value.intentRecordRevision.toLong &&
    value.intentRecordRevision.toLong <= value.frontierRevision.toLong && value.frontierRevision.toLong < Long.MaxValue - 4 &&
    value.intentIdentity == reference(value.input, "intent", value.attempt).identity && value.intentRevision == "1" &&
    _snapshot(value.parent) && _snapshot(value.candidate) && value.candidate.head == value.parent.head &&
    validPaths(value.ownedPaths) && _observation_valid(value.observation) &&
    value.observation.selectedPaths.forall(path => value.ownedPaths.exists(_covers(_, path))) &&
    value.execution != null && value.execution.forall(result => validExecution(result) && result.input == value.input &&
      result.ordinal == value.ordinal && result.attempt == value.attempt &&
      result.selectedPaths.forall(path => value.ownedPaths.exists(_covers(_, path)))) &&
    value.partialCompletion != null && value.partialCompletion.forall(_ => P.applicable(value)) &&
    BigInt(value.frontierRevision) == BigInt(value.intentRecordRevision) + value.execution.size

  private def _observation_valid(value: Observation): Boolean = value != null && _exact(value.root) &&
    _exact(value.branch) && Vector(value.head, value.tree, value.indexTree).forall(_oid) &&
    value.mergeHead != null && value.mergeHead.forall(_oid) && value.dirty != null && value.dirty.size <= 4096 &&
    value.dirty.forall(path => path != null && path.path != null && GitPath.parseC(path.path).isSuccess &&
      path.renamedFrom != null && path.renamedFrom.forall(p => p != null && GitPath.parseC(p).isSuccess)) &&
    validPaths(value.selectedPaths) && value.pendingPaths != null && value.pendingPaths.size <= 256 &&
    value.pendingPaths.distinct == value.pendingPaths && value.pendingPaths.forall(p => p != null && GitPath.parseC(p).isSuccess) &&
    value.commit != null && value.commit.identity == value.head && value.commit.tree == value.tree &&
    value.commit.parents != null && value.commit.parents.size <= 256 && value.commit.parents.forall(_oid)

  def choiceC(target: RecoveryTarget, choice: String): Consequence[Unit] = {
    if (!validTarget(target) || !choices.contains(choice)) return _invalid("invalid target or choice")
    val observed = target.observation
    val selectedclean = !observed.dirty.exists(path => target.ownedPaths.exists(owner =>
      (_covers(owner, path.path) || path.renamedFrom.exists(_covers(owner, _))) && path.worktree != ' '))
    val common = observed.mergeHead.isEmpty && observed.selectedChangesEmpty &&
      observed.indexTree == target.candidate.tree && selectedclean &&
      GoalPhaseNativeCandidateBoundary.scopedDirty(observed.dirty, target.ownedPaths, true) &&
      GoalPhaseNativeCandidateBoundary.pathsOwned(observed.pendingPaths, target.ownedPaths)
    if (choice == "Defer") Consequence.unit
    else if (choice == "CompletePartialCommit" && P.applicable(target)) Consequence.unit
    else if (choice == "AuthorizeNewAttempt" && common && observed.head == target.parent.head &&
        observed.tree == target.parent.tree) Consequence.unit
    else if (choice == "ReconcileCommit" && common && observed.head != target.parent.head &&
        observed.tree == target.candidate.tree && observed.commit.parents == Vector(target.parent.head) &&
        !observed.dirty.exists(path => target.ownedPaths.exists(owner => _covers(owner, path.path) ||
          path.renamedFrom.exists(_covers(owner, _))))) Consequence.unit
    else _invalid("current actual Git observation cannot admit the requested recovery choice")
  }

  def bindingC(component: String, target: RecoveryTarget, choice: String, rationale: String,
    participant: String, reference: ContextReference, priorExecutionEnded: Boolean): Consequence[Json] =
    if (!_exact(component) || !validTarget(target) || !choices.contains(choice) || !_text(rationale) ||
        participant != target.participant || reference == null || !_exact(reference.identity) || !_exact(reference.revision) ||
        (choice != "Defer" && !priorExecutionEnded)) _invalid("Human evidence must bind the complete target, participant, actual reference and prior execution ended attestation")
    else Consequence.success(Json.obj("componentInstance" -> component.asJson, "closureRecovery" -> Json.obj(
      "target" -> target.asJson, "answer" -> Json.obj("choice" -> choice.asJson, "rationale" -> rationale.asJson,
        "participant" -> participant.asJson, "reference" -> reference.asJson,
        "priorExecutionEnded" -> priorExecutionEnded.asJson))))

  def selectBinding(value: Json): Either[String, Json] = for {
    wire <- Option(value).toRight("Closure recovery Human evidence is absent")
    component <- wire.hcursor.get[String]("componentInstance").left.map(_.message)
    targetwire <- wire.hcursor.downField("closureRecovery").get[Json]("target").left.map(_.message)
    target <- decodeTarget(targetwire)
    answer = wire.hcursor.downField("closureRecovery").downField("answer")
    choice <- answer.get[String]("choice").left.map(_.message)
    rationale <- answer.get[String]("rationale").left.map(_.message)
    participant <- answer.get[String]("participant").left.map(_.message)
    reference <- answer.get[ContextReference]("reference").left.map(_.message)
    ended <- answer.get[Boolean]("priorExecutionEnded").left.map(_.message)
    binding <- bindingC(component, target, choice, rationale, participant, reference, ended).toOption
      .toRight("Closure recovery attestation is incomplete")
  } yield binding

  def admitC(d: F.Declaration, core: ActionCall.Core, target: RecoveryTarget,
    input: V.GoalPhaseClosureRecoveryInput, participant: String): Consequence[Unit] = for {
    installation <- d.closureRecovery.map(Consequence.success).getOrElse(
      Consequence.securityPermissionDenied("Closure recovery has no independent trusted Human evidence provider"))
    _ <- installation.validateC
    reference = ContextReference(input.reference.identity, input.reference.revision)
    binding <- bindingC(core.component.get.core.instanceId.canonicalKey, target, input.choice, input.rationale,
      participant, reference, input.priorExecutionEnded)
    _ <- installation.evidence.admitC(core, reference, binding)
  } yield ()

  private def _codec[A: Encoder: Decoder](identity: String, schema: String, valid: A => Boolean) =
    new WorkflowResultJsonV1.PayloadCodec[A] {
      val typeIdentity = identity
      def encode(value: A): Json = Json.obj("schemaVersion" -> schema.asJson, "payload" -> value.asJson)
      def decode(value: Json): Either[String, A] = for {
        wire <- Option(value).toRight("Closure recovery receipt is absent")
        version <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
        _ <- Either.cond(version == schema, (), "Closure recovery receipt schema differs")
        payload <- wire.hcursor.get[A]("payload").left.map(_.message)
        _ <- Either.cond(valid(payload), (), "Closure recovery receipt is invalid")
      } yield payload
    }
  val executionCodec: WorkflowResultJsonV1.PayloadCodec[Execution] = _codec[Execution](
    "GoalPhaseClosureExecutionResult", "sm-workflow.goal-phase-closure-execution.v1", validExecution)
  val directionCodec: WorkflowResultJsonV1.PayloadCodec[Direction] = _codec[Direction](
    "GoalPhaseClosureRecoveryDirection", "sm-workflow.goal-phase-closure-recovery.v1", value => value != null &&
      validTarget(value.target) && Set("AuthorizeNewAttempt", "ReconcileCommit", "CompletePartialCommit")(value.choice) &&
      _text(value.rationale) && value.participant == value.target.participant && value.priorExecutionEnded &&
      value.reference != null && _exact(value.reference.identity) && _exact(value.reference.revision) &&
      choiceC(value.target, value.choice).isSuccess)
  val continuationCodec: WorkflowResultJsonV1.PayloadCodec[Continuation] = _codec[Continuation](
    "GoalPhaseClosureContinuation", "sm-workflow.goal-phase-closure-continuation.v1", value => value != null &&
      continuationAction(value.target).contains(value.action))

  /** Capture only actual typed terminal facts, before projecting valueC. */
  def executionC(input: K.Input, ordinal: Int, attempt: Int,
    result: GitExecutionResult[Unit]): Consequence[Execution] = for {
    selection <- GitSelectionId.parseC("goal-step-" + ordinal)
    _ <- if (result != null && result.processResult != null && result.processResult.stderr != null &&
        result.processResult.stderr.content != null && result.selectedPaths != null &&
        result.target != null && result.target.workspaceId.value == "sm-workflow-repository" &&
        result.operation == GitOperation.CommitSelected(selection)) Consequence.unit
      else _invalid("actual closure execution result is incomplete or belongs to another operation")
    process = result.processResult
    outcome = result.outcome match {
      case GitExecutionOutcome.Succeeded => "succeeded"
      case GitExecutionOutcome.Failed(_) => "failed"
      case GitExecutionOutcome.Incomplete(_) => "incomplete"
      case GitExecutionOutcome.InvalidOutput(_) => "invalid-output"
      case GitExecutionOutcome.ConflictObservationRequired => "conflict-observation-required"
    }
    exit = process.termination match {
      case ProcessExecutionTermination.Exited(code) => Some(code)
      case _ => None
    }
    bytes = process.stderr.content.take(8192)
    diagnostics = new String(bytes.toArray, java.nio.charset.StandardCharsets.UTF_8)
      .map(c => if (c == '\n' || c == '\t' || !Character.isISOControl(c)) c else ' ')
    execution = Execution(input, ordinal, attempt, "commit-selected", result.target.workspaceId.value,
      outcome, Option(process.termination).map(_.toString), exit, diagnostics,
      result.selectedPaths.map(_.value), process.stderr.truncated || process.stderr.content.size > bytes.size)
    _ <- if (validExecution(execution)) Consequence.unit else _invalid("actual closure execution summary is invalid")
  } yield execution

  def reference(input: K.Input, role: String, attempt: Int): WorkflowMechanicalTransition.Reference =
    WorkflowMechanicalTransition.Reference(input.instance + ":ordered-step-closure:" + input.sourceRevision + ":" +
      role + (if (attempt == 0) "" else "-retry-" + attempt), "1")
  def cause(role: String, attempt: Int): String = "ordered-step-closure:" + role +
    (if (attempt == 0) "" else "-retry-" + attempt)

  def adjacentC(previous: InstanceRecord, saved: InstanceRecord, role: String, attempt: Int): Consequence[Unit] = for {
    _ <- if (previous != null && saved != null && previous.revision.value < Long.MaxValue &&
        previous.lifecycle == Lifecycle.Active && previous.suspension.isEmpty &&
        previous.currentProgression.contains(ProgressionReference("phase.Closing"))) Consequence.unit
      else _invalid("recovery predecessor is not an unsuspended Closing record")
    _ <- previous.validateC
    _ <- saved.validateC
    expected <- previous.appendC(previous.revision, F.history(previous, "phase.Closing", cause(role, attempt)))
    _ <- if (expected == saved) Consequence.unit else _invalid("recovery receipt has a gap or crossed native history")
  } yield ()

  def observeC(d: F.Declaration, core: ActionCall.Core, input: K.Input, ordinal: Int): Consequence[Observation] = for {
    root <- G.rootC(core)
    before <- G.inspectC(core)
    index <- G.indexTreeC(core)
    selection <- GitSelectionId.parseC("goal-step-" + ordinal)
    candidate <- GitObjectId.parseC(input.candidate.tree)
    selected <- E.executeGitC(core, GitOperation.SelectedIndexChanges(selection, candidate))
    differences <- selected.valueC
    parent <- GitObjectId.parseC(input.parent.head)
    physical <- E.executeGitC(core, GitOperation.SelectedIndexChanges(selection, parent))
    physicalchanges <- physical.valueC
    pending <- E.executeGitC(core, GitOperation.PendingPaths(parent)).flatMap(_.valueC)
    commit <- G.commitC(core, before.observed.head)
    after <- G.inspectC(core)
    afterindex <- G.indexTreeC(core)
    afterroot <- G.rootC(core)
    _ <- if (before == after && index == afterindex && root == afterroot && root == d.target.root.toString &&
        before.observed.branch == d.target.localBranch) Consequence.unit
      else _invalid("actual Git observation drifted or crossed the installed target")
  } yield Observation(root, before.observed.branch, before.observed.head, before.observed.tree, index,
    before.mergeHead, before.observed.dirty,
    if (before.observed.head != input.parent.head && before.observed.tree != index) physical.selectedPaths.map(_.value)
      else physicalchanges.map(_.path.value), differences.isEmpty,
    pending.map(_.value), commit)

  def currentTargetC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    from: InstanceRecord, controller: O.State): Consequence[RecoveryTarget] = for {
    retained <- K.recoveryProgressC(session, d, core, from, controller)
    (input, ordinal, progress) = retained
    pending <- progress.pending.filter(saved => saved.record.causation.value == cause("intent", saved.payload.attempt))
      .map(Consequence.success).getOrElse(_invalid("recovery requires an actual unresolved commit intent"))
    _ <- if (progress.nextAttempt.isEmpty && progress.direction.isEmpty && progress.continuation.isEmpty && progress.record == session.current)
      Consequence.unit else _invalid("recovery direction is stale, already used or not at the exact current frontier")
    observed <- observeC(d, core, input, ordinal)
    base = RecoveryTarget(input.instance, from.definition.workflowIdentity.value, from.definition.workflowRevision.value,
      session.participantIdentity, input.plan, input.planRevision, input.step, input.generation,
      input.sourceRevision, progress.record.revision.value.toString, reference(input, "intent", pending.payload.attempt).identity,
      "1", pending.record.revision.value.toString, ordinal, pending.payload.attempt, input.parent, input.candidate,
      input.reviewEvidence, controller.plan.steps(controller.cursor).ownedPaths, observed, progress.execution.map(_.payload), input.admission)
    proof <- P.prepareC(core, base, pending.payload, progress.failed.flatMap(_.payload.check))
    target = base.copy(partialCompletion = proof)
    _ <- if (validTarget(target)) Consequence.unit else _invalid("actual unresolved closure target is invalid")
  } yield target

  def continueC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    target: RecoveryTarget): Consequence[Unit] = for {
    action <- continuationAction(target).map(Consequence.success).getOrElse(_invalid("actual execution and Git facts do not permit continuation"))
    _ <- if (session.current.revision.value.toString == target.frontierRevision) Consequence.unit
      else _invalid("continuation frontier changed")
    _ <- saveC(session, d, core, target.input, "continuation", target.attempt, session.current,
      Continuation(target, action), continuationCodec)
  } yield ()

  def appendC(session: F.Session, d: F.Declaration, core: ActionCall.Core, target: RecoveryTarget,
    input: V.GoalPhaseClosureRecoveryInput): Consequence[Unit] = for {
    _ <- choiceC(target, input.choice)
    direction = Direction(target, input.choice, input.rationale, session.participantIdentity,
      ContextReference(input.reference.identity, input.reference.revision), input.priorExecutionEnded)
    _ <- directionCodec.decode(directionCodec.encode(direction)).fold(_invalid, _ => Consequence.unit)
    _ <- if (session.current.revision.value.toString == target.frontierRevision) Consequence.unit
      else _invalid("direction frontier changed")
    _ <- saveC(session, d, core, target.input, "recovery", target.attempt, session.current, direction, directionCodec)
  } yield ()

  def saveC[A](session: F.Session, d: F.Declaration, core: ActionCall.Core, input: K.Input,
    role: String, attempt: Int, from: InstanceRecord, payload: A,
    codec: WorkflowResultJsonV1.PayloadCodec[A]): Consequence[InstanceRecord] = for {
    _ <- if (from != null && from.lifecycle == Lifecycle.Active && from.suspension.isEmpty &&
        from.currentProgression.contains(ProgressionReference("phase.Closing")) && from.revision.value < Long.MaxValue)
      Consequence.unit else _invalid("receipt has no exact native Closing predecessor")
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    saved <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, reference(input, role, attempt), from,
      from.revision, F.history(from, "phase.Closing", cause(role, attempt)), TypedValue(codec.typeIdentity, payload)),
      ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
  } yield saved.receipt.next

  def loadC[A](session: F.Session, core: ActionCall.Core, input: K.Input, role: String, attempt: Int,
    codec: WorkflowResultJsonV1.PayloadCodec[A]): Consequence[Option[(InstanceRecord, A)]] = for {
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    retained <- adapter.loadRetainedC(reference(input, role, attempt))(using core.executionContext)
    saved <- retained match {
      case None => Consequence.success(Option.empty[(InstanceRecord, A)])
      case Some(value) => for {
        instances <- session.storage.bindInstancesC()(using core.executionContext)
        current <- instances.loadC(session.current.identity)(using core.executionContext).flatMap(_.map(Consequence.success)
          .getOrElse(_invalid("native recovery instance is absent")))
        prefix <- GoalPhaseNativeHistory.prefixC(current, value.receipt.next.revision)
        _ <- if (prefix == value.receipt.next) Consequence.unit else _invalid("receipt is not its exact native history prefix")
      } yield Some((prefix, value.receipt.payload.value))
    }
  } yield saved

  def operations(port: GoalPhaseNativeWorkContinuation.Port): Vector[spec.OperationDefinition] =
    Vector(new Operation(true, port), new Operation(false, port))
  private final class Operation(describe: Boolean, port: GoalPhaseNativeWorkContinuation.Port) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (describe)
      SmWorkflowComponent.GoalPhaseServiceService.DescribeGoalPhaseClosureRecoveryOperation
      else SmWorkflowComponent.GoalPhaseServiceService.RecoverGoalPhaseClosureOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains("GoalPhaseService") ||
          request.operation != (if (describe) describeOperation else recoveryOperation)) _invalid("Operation identity differs")
      else if (describe) NativeWorkflowOperationInput.textC(request, "sessionId").map(identity => Action(request, identity, None, port))
      else NativeWorkflowOperationInput.valueC[V.GoalPhaseClosureRecoveryInput](request, "input").flatMap { input =>
        if (input == null || !_exact(input.sessionId) || !choices.contains(input.choice) || !_text(input.rationale) ||
            input.reference == null || !_exact(input.reference.identity) || !_exact(input.reference.revision) ||
            (input.choice != "Defer" && !input.priorExecutionEnded))
          _invalid("input requires exact Human direction, reference and affirmative prior execution ended attestation")
        else Consequence.success(Action(request, input.sessionId, Some(input), port))
      }
  }
  private final case class Action(request: Request, sessionId: String, input: Option[V.GoalPhaseClosureRecoveryInput],
    port: GoalPhaseNativeWorkContinuation.Port) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = for {
        context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(sessionId))
        selected = core.copy(executionContext = context)
        result <- input match {
          case None => port.describeClosureRecoveryC(sessionId, selected).map(NativeWorkflowOperationOutput.response(_))
          case Some(value) => port.recoverClosureC(value, selected).map(NativeWorkflowOperationOutput.response(_))
        }
      } yield result
    }
  }
  private def _covers(owner: String, path: String): Boolean = path == owner || path.startsWith(owner + "/")
  private def _revision(value: String): Boolean = value != null && value.toLongOption.exists(n => n > 0 && n.toString == value)
  private def _oid(value: String): Boolean = value != null && GitObjectId.parseC(value).isSuccess
  private def _snapshot(value: O.Snapshot): Boolean = value != null && _oid(value.head) && _oid(value.tree)
  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  private def _text(value: String): Boolean = value != null && value.trim.nonEmpty && !value.contains('\u0000') &&
    java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(value) && value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 65536
  private def _invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("Closure recovery: " + reason)
}
