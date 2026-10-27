package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.processexecution.{GitDiffParameters, GitObjectId, GitOperation}
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseNativeRun as F
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeCandidate as C
import GoalPhaseCandidateRecovery as R
import GoalPhaseCandidateObservationContinuation as K
import GoalPhaseNativeCandidateBoundary as B
import RepositorySyncManagedGitObservation as G

/** One independently authorized remaining observation of an original whole-Phase inspection.
  * Retained UNKNOWN history is immutable. Failed new intents have no retry contract.
  * @since   Oct. 22, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseWholePhaseCandidateRecovery {
  val purpose = "goal-phase-whole-phase-candidate-recovery"
  val describeOperation = "describeGoalPhaseWholePhaseCandidateRecovery"
  val recoveryOperation = "recoverGoalPhaseWholePhaseCandidate"
  val choices = Vector("AuthorizeNewObservation", "Defer")
  val processPlanKey = "textus.subsystem.process-execution.plan"
  val message = "The original whole-Phase observation remains UNKNOWN with termination-unconfirmed. " +
    "Description observes the actual repository and index through typed Git; IndexTree/write-tree may write Git objects and index cache. " +
    "A fresh independent Human answer must attest that the prior invocation ended, from actual terminal invocation evidence. " +
    "The Boolean alone and this passive description/preparation are not proof of termination. " +
    "AuthorizeNewObservation permits ONE remaining observation on this exact repository/index and resolved host policy. " +
    "Recovery appends direction only. Separate ordinary Advance persists its own intent before effects, without staging or history writes. " +
    "Completed Steps, Plan, counters, original UNKNOWN and receipts remain retained. Defer grants no effect."

  final case class Policy(phaseValidation: GoalPhaseNativeValidation.Policy, maximumDiffBytes: Long, processPlan: Json)
  object Policy {
    // Opaque JSON text protects arbitrary host-plan map keys from public Record
    // property-name normalization. Admission compares parsed JSON semantics.
    given Encoder[Policy] = Encoder.instance(value => Json.obj("phaseValidation" -> value.phaseValidation.asJson,
      "maximumDiffBytes" -> value.maximumDiffBytes.asJson,
      "processPlan" -> io.circe.Printer.noSpaces.copy(sortKeys = true).print(value.processPlan).asJson))
    given Decoder[Policy] = Decoder.instance(cursor => for {
      phase <- cursor.get[GoalPhaseNativeValidation.Policy]("phaseValidation")
      budget <- cursor.get[Long]("maximumDiffBytes")
      raw <- cursor.get[String]("processPlan")
      plan <- io.circe.parser.parse(raw).left.map(error => io.circe.DecodingFailure(error.message, cursor.history))
    } yield Policy(phase, budget, plan))
  }
  final case class Target(sessionId: String, controller: R.Controller, definitionIdentity: String,
    definitionRevision: String, participant: String, sourceRevision: String, frontierRevision: String,
    root: String, branch: String, candidatePaths: Vector[String], managementPaths: Vector[String],
    documentPolicy: Option[GoalPhaseDocumentProgress.Policy], recordingPaths: Vector[String],
    completedCandidates: Vector[C.Payload], inspectionIdentity: String, inspectionRevision: String,
    inspectionRecordRevision: String, inspection: C.Payload, stagingPrefix: Vector[R.StagePair],
    oldObservationOutcome: String, oldObservationTermination: String, repository: K.RepositorySnapshot, policy: Policy)
  object Target {
    // This complete public target omits nested absent Options. Its outer
    // canonical comparison still rejects every unknown or noncanonical field;
    // do not impose other recovery codecs' explicit-null wire shapes.
    private given Decoder[R.Controller] = deriveDecoder[R.Controller]
    private given Decoder[K.Dirty] = deriveDecoder[K.Dirty]
    private given Decoder[K.RepositorySnapshot] = deriveDecoder[K.RepositorySnapshot]
    given Encoder[Target] = deriveEncoder[Target].mapJson(_.deepDropNullValues)
    given Decoder[Target] = deriveDecoder[Target]
  }
  final case class Direction(target: Target, reference: ContextReference, participant: String,
    rationale: String, priorExecutionEnded: Boolean)
  object Direction {
    given Encoder[Direction] = deriveEncoder[Direction]
    given Decoder[Direction] = deriveDecoder[Direction]
  }
  trait EvidencePort {
    def admitC(core: ActionCall.Core, reference: ContextReference, binding: Json): Consequence[Unit]
  }
  final case class Installation(evidence: EvidencePort) {
    def validateC: Consequence[Unit] = if (evidence != null) Consequence.unit
      else _invalid("requires a separate independent Human evidence provider")
  }
  def targetRecord(value: Target): Record = GoalPhaseNativeMetadata.record(value.asJson)
  def targetC(value: Record): Consequence[Target] = if (value == null) _invalid("target is absent")
    else value.toJsonStringC.flatMap(text => io.circe.parser.parse(text).left.map(_.message)
      .flatMap(GoalPhaseNativeMetadata.normalizePublicJson).flatMap(decodeTarget).fold(_invalid, Consequence.success))
  // Canonical comparison rejects unknown fields recursively as well as malformed required fields.
  def decodeTarget(value: Json): Either[String, Target] = for {
    wire <- Option(value).toRight("whole-Phase target is absent")
    target <- wire.as[Target].left.map(_.message)
    _ <- Either.cond(validTarget(target) && target.asJson == wire, (), "whole-Phase target is incomplete, crossed or has unknown fields")
  } yield target

  def validPolicy(value: Policy): Boolean = value != null && value.phaseValidation != null &&
    value.phaseValidation.validateC.isSuccess && value.maximumDiffBytes > 0 &&
    value.maximumDiffBytes <= GitDiffParameters.maximumBytes && value.processPlan != null && value.processPlan.asObject.exists(_.nonEmpty)

  /** Opaque ordinary resolved plan; CNCF alone interprets programs, budgets and grants. */
  def resolvePolicyC(d: F.Declaration, core: ActionCall.Core): Consequence[Policy] = for {
    phase <- d.phaseValidationPolicy.map(Consequence.success).getOrElse(_invalid("fixed Phase validation policy is absent"))
    _ <- phase.validateC
    budget <- GitDiffParameters.resolveC(core.executionContext)
    raw <- Option(core.executionContext.resolvedParameters).flatMap(_.get(processPlanKey)).map(_.value)
      .map(Consequence.success).getOrElse(_invalid("ordinary resolved process-execution plan is absent"))
    plan <- (raw match {
      case ConfigurationValue.StringValue(text) if text != null => io.circe.parser.parse(text).left.map(_.message)
      case value => configurationJson(value)
    }).fold(_invalid, Consequence.success)
    selected = Policy(phase, budget, plan)
    _ <- if (validPolicy(selected)) Consequence.unit else _invalid("resolved host policy is malformed")
  } yield selected
  def configurationJson(value: ConfigurationValue): Either[String, Json] = value match {
    case ConfigurationValue.StringValue(v) if v != null => Right(Json.fromString(v))
    case ConfigurationValue.NumberValue(v) if v != null => Right(Json.fromBigDecimal(v))
    case ConfigurationValue.BooleanValue(v) => Right(Json.fromBoolean(v))
    case ConfigurationValue.NullValue => Right(Json.Null)
    case ConfigurationValue.ListValue(vs) if vs != null => vs.foldLeft[Either[String, Vector[Json]]](Right(Vector.empty)) {
      (prior, next) => for { values <- prior; value <- configurationJson(next) } yield values :+ value
    }.map(Json.fromValues)
    case ConfigurationValue.ObjectValue(vs) if vs != null => vs.toVector.foldLeft[Either[String, Vector[(String, Json)]]](Right(Vector.empty)) {
      case (prior, (key, next)) => for {
        values <- prior
        _ <- Either.cond(key != null, (), "invalid process plan key")
        value <- configurationJson(next)
      } yield values :+ (key -> value)
    }.map(Json.fromFields)
    case _ => Left("invalid resolved process plan")
  }
  def validRepository(t: Target, snapshot: K.RepositorySnapshot): Boolean = snapshot != null &&
    snapshot.root == t.root && snapshot.branch == t.branch && snapshot.head == t.controller.tip.head &&
    snapshot.tree == t.controller.tip.tree && Vector(snapshot.head, snapshot.tree, snapshot.indexTree).forall(_object) &&
    snapshot.dirty != null && snapshot.dirty.forall(p => p != null && p.index != null && p.index.length == 1 &&
      p.worktree != null && p.worktree.length == 1 && RepositorySyncNativeConflictInput.relative(p.path) &&
      p.renamedFrom != null && p.renamedFrom.forall(RepositorySyncNativeConflictInput.relative)) &&
    snapshot.dirty.map(_.path).distinct.size == snapshot.dirty.size &&
    B.scopedDirty(snapshot.dirty.map(p => G.DirtyPath(p.index.head, p.worktree.head, p.path, p.renamedFrom)), t.managementPaths, true) &&
    snapshot.dirty.filter(p => t.controller.plan.phaseRepairInventory.exists(owner =>
      B.covers(owner, p.path) || p.renamedFrom.exists(B.covers(owner, _)))).forall(p =>
        t.controller.plan.phasePathsOwned((Vector(p.path) ++ p.renamedFrom.toVector).distinct))

  def validTarget(t: Target): Boolean = scala.util.Try(_valid_target(t)).getOrElse(false)
  private def _valid_target(t: Target): Boolean = t != null &&
    Vector(t.sessionId, t.definitionIdentity, t.definitionRevision, t.participant, t.root, t.branch).forall(_exact) &&
    scala.util.Try(java.nio.file.Path.of(t.root)).toOption.exists(p => p.isAbsolute && p.normalize.toString == t.root) &&
    Vector(t.sourceRevision, t.frontierRevision, t.inspectionRecordRevision).forall(_revision) &&
    t.controller != null && O.validatePlanC(t.controller.plan).isSuccess && t.controller.stage == "PhaseInspection" &&
    t.controller.cursor == t.controller.plan.steps.size && t.controller.completed != null &&
    t.controller.completed.size == t.controller.cursor && t.controller.completed.map(_.step) == t.controller.plan.steps.map(_.identity) &&
    t.controller.completed.forall(s => s != null && s.commit != null && O.validStepAcceptance(s) &&
      Vector(s.commit.identity, s.commit.parent, s.commit.tree).forall(_object)) &&
    t.controller.generation >= 0 && t.controller.repairs >= 0 && t.controller.ordinaryRepairs >= 0 &&
    t.controller.ordinaryRepairs <= t.controller.repairs && _snapshot(t.controller.base) && _snapshot(t.controller.tip) &&
    t.controller.tip == t.controller.completed.lastOption.map(s => O.Snapshot(s.commit.identity, s.commit.tree)).getOrElse(t.controller.base) &&
    Vector(t.controller.candidate, t.controller.checksReceipt, t.controller.reviewEvidence, t.controller.closureIntent,
      t.controller.decision, t.controller.repositoryReceipt, t.controller.decisionSource, t.controller.pendingRepair,
      t.controller.phaseReviewedCandidate, t.controller.candidateParent).forall(v => v != null && v.isEmpty) &&
    !t.controller.documentDecision && t.controller.phaseRecordingPaths != null && t.controller.phaseRecordingPaths.isEmpty &&
    t.candidatePaths != null && t.candidatePaths.distinct == t.candidatePaths && t.managementPaths != null &&
    t.managementPaths.distinct == t.managementPaths && t.recordingPaths != null && t.recordingPaths.distinct == t.recordingPaths &&
    (t.candidatePaths ++ t.managementPaths).forall(RepositorySyncNativeConflictInput.relative) &&
    t.controller.plan.phaseOwnedPaths.forall(t.candidatePaths.contains) &&
    t.documentPolicy != null && t.documentPolicy.forall(_.validateC(t.controller.plan, t.candidatePaths).isSuccess) &&
    GoalPhaseNativeFindingRecording.validatePathsC(t.recordingPaths, t.controller.plan, t.candidatePaths).isSuccess &&
    t.managementPaths == (t.documentPolicy.toVector.flatMap(_.paths) ++ t.recordingPaths).distinct &&
    t.completedCandidates != null && t.completedCandidates.size == t.controller.completed.size &&
    t.completedCandidates.zip(t.controller.completed).forall { (p, s) => p != null &&
      C.codec.decode(C.codec.encode(p)).isRight && p.step == s.step && p.candidate.nonEmpty && p.diff.nonEmpty &&
      p.selectedPaths == p.stagedPaths && p.candidate.exists(c => _snapshot(c) && c.head == s.commit.parent) &&
      p.stepEvidence.isEmpty && p.changedPaths != null && p.changedPaths.distinct == p.changedPaths &&
      B.pathsOwned((p.selectedPaths ++ p.changedPaths).distinct, t.controller.plan.steps.find(_.identity == s.step).get.ownedPaths) } &&
    t.inspection != null && C.codec.decode(C.codec.encode(t.inspection)).isRight &&
    t.inspection.sourceRevision == t.sourceRevision && t.inspection.step == "phase:" + t.controller.plan.phase &&
    t.inspection.selectedPaths != null && t.inspection.selectedPaths.distinct == t.inspection.selectedPaths &&
    t.inspection.selectedPaths.forall(t.managementPaths.contains) && t.inspection.stagedPaths.isEmpty &&
    t.inspection.candidate.isEmpty && t.inspection.changedPaths.isEmpty && t.inspection.diff.isEmpty &&
    t.inspection.continuation.isEmpty && t.inspection.stepEvidence.isEmpty &&
    t.inspectionIdentity == _native_identity(t, "inspection") && t.inspectionRevision == "1" &&
    BigInt(t.inspectionRecordRevision) == BigInt(t.sourceRevision) + 1 && t.stagingPrefix != null &&
    t.stagingPrefix.size == t.inspection.selectedPaths.size && t.stagingPrefix.zipWithIndex.forall { (p, i) =>
      p != null && p.index == i && p.intentRevision == "1" && p.resultRevision == "1" &&
      p.intentIdentity == _native_identity(t, "stage-intent-" + i) && p.resultIdentity == _native_identity(t, "stage-result-" + i) &&
      Vector(p.intentRecordRevision, p.resultRecordRevision).forall(_revision) &&
      BigInt(p.intentRecordRevision) == BigInt(t.sourceRevision) + 2 + BigInt(i) * 2 &&
      BigInt(p.resultRecordRevision) == BigInt(p.intentRecordRevision) + 1 &&
      p.intent == t.inspection.copy(stagedPaths = t.inspection.selectedPaths.take(i)) &&
      p.result == t.inspection.copy(stagedPaths = t.inspection.selectedPaths.take(i + 1))
    } && BigInt(t.frontierRevision) == BigInt(t.sourceRevision) + 1 + BigInt(t.stagingPrefix.size) * 2 &&
    BigInt(t.frontierRevision) < BigInt(Long.MaxValue) - 2 && t.oldObservationOutcome == "UNKNOWN" &&
    t.oldObservationTermination == "termination-unconfirmed" && validPolicy(t.policy) && validRepository(t, t.repository)

  def observeRepositoryC(core: ActionCall.Core, target: Target): Consequence[K.RepositorySnapshot] = for {
    root <- G.rootC(core)
    before <- G.observeC(core)
    index <- G.indexTreeC(core)
    tip <- GitObjectId.parseC(target.controller.tip.head)
    pending <- WorkflowManagedProcessExecution.executeGitC(core, GitOperation.PendingPaths(tip)).flatMap(_.valueC)
    after <- G.observeC(core)
    afterindex <- G.indexTreeC(core)
    afterroot <- G.rootC(core)
    snapshot = K.snapshot(root, before, index)
    _ <- if (root == afterroot && before == after && index == afterindex && validRepository(target, snapshot) &&
        B.pathsOwned(pending.map(_.value), target.managementPaths) &&
        pending.map(_.value).forall(target.inspection.selectedPaths.contains)) Consequence.unit
      else _invalid("repository/index is unstable or crosses original tip, root, branch or completed management staging")
  } yield snapshot

  def bindingC(component: String, target: Target, choice: String, rationale: String, participant: String,
    reference: ContextReference, priorExecutionEnded: Boolean): Consequence[Json] =
    if (!_exact(component) || !validTarget(target) || !choices.contains(choice) || !_text(rationale) ||
        participant != target.participant || reference == null || !_exact(reference.identity) || !_exact(reference.revision) ||
        (choice == "AuthorizeNewObservation" && !priorExecutionEnded)) _invalid("requires exact fresh Human binding and affirmative ended attestation")
    else Consequence.success(Json.obj("componentInstance" -> component.asJson, "wholePhaseCandidateRecovery" -> Json.obj(
      "target" -> target.asJson, "answer" -> Json.obj("choice" -> choice.asJson, "rationale" -> rationale.asJson,
        "participant" -> participant.asJson, "reference" -> reference.asJson, "priorExecutionEnded" -> priorExecutionEnded.asJson))))
  def selectBinding(value: Json): Either[String, Json] = for {
    wire <- Option(value).toRight("whole-Phase binding is absent")
    component <- wire.hcursor.get[String]("componentInstance").left.map(_.message)
    targetwire <- wire.hcursor.downField("wholePhaseCandidateRecovery").get[Json]("target").left.map(_.message)
    target <- decodeTarget(targetwire)
    answer = wire.hcursor.downField("wholePhaseCandidateRecovery").downField("answer")
    choice <- answer.get[String]("choice").left.map(_.message)
    rationale <- answer.get[String]("rationale").left.map(_.message)
    participant <- answer.get[String]("participant").left.map(_.message)
    reference <- answer.get[ContextReference]("reference").left.map(_.message)
    ended <- answer.get[Boolean]("priorExecutionEnded").left.map(_.message)
    selected <- bindingC(component, target, choice, rationale, participant, reference, ended)
      .map[Either[String, Json]](Right(_)).fold(error => Left(error.toString), identity)
    _ <- Either.cond(selected == wire, (), "whole-Phase binding fields differ")
  } yield selected
  def admitC(d: F.Declaration, core: ActionCall.Core, target: Target,
    input: V.GoalPhaseWholePhaseCandidateRecoveryInput, participant: String): Consequence[Unit] = for {
    installation <- d.wholePhaseCandidateRecovery.map(Consequence.success).getOrElse(
      Consequence.securityPermissionDenied("Whole-Phase recovery has no independent Human evidence provider"))
    _ <- installation.validateC
    reference = ContextReference(input.reference.identity, input.reference.revision)
    binding <- bindingC(core.component.get.core.instanceId.canonicalKey, target, input.choice, input.rationale,
      participant, reference, input.priorExecutionEnded)
    _ <- installation.evidence.admitC(core, reference, binding)
  } yield ()

  val codec = new WorkflowResultJsonV1.PayloadCodec[Direction] {
    val typeIdentity = "GoalPhaseWholePhaseCandidateRecoveryDirection"
    def encode(value: Direction): Json = Json.obj("schemaVersion" -> "sm-workflow.goal-phase-whole-phase-candidate-recovery.v1".asJson,
      "payload" -> value.asJson)
    def decode(value: Json): Either[String, Direction] = for {
      wire <- Option(value).toRight("whole-Phase direction is absent")
      schema <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(schema == "sm-workflow.goal-phase-whole-phase-candidate-recovery.v1", (), "whole-Phase schema differs")
      direction <- wire.hcursor.get[Direction]("payload").left.map(_.message)
      _ <- decodeTarget(direction.target.asJson)
      _ <- Either.cond(direction.participant == direction.target.participant && direction.reference != null &&
        _exact(direction.reference.identity) && _exact(direction.reference.revision) && _text(direction.rationale) &&
        direction.priorExecutionEnded && encode(direction) == wire, (), "whole-Phase direction is incomplete or crossed")
    } yield direction
  }
  private def _reference(from: InstanceRecord) = WorkflowMechanicalTransition.Reference(
    from.identity.value + ":ordered-whole-phase-candidate-recovery:" + from.revision.value, "1")
  private def _native_reference(from: InstanceRecord, key: String) = WorkflowMechanicalTransition.Reference(
    from.identity.value + ":ordered-candidate:" + from.revision.value + ":" + key, "1")
  private def _native_identity(t: Target, key: String) = t.sessionId + ":ordered-candidate:" + t.sourceRevision + ":" + key

  /** Source admission after the caller's retained native history traversal. */
  def originalSourceC(controller: O.State, from: InstanceRecord): Consequence[Unit] =
    if (controller != null && controller.plan != null && controller.plan.steps != null && from != null &&
        controller.stage == O.Stage.PhaseInspection && controller.cursor == controller.plan.steps.size &&
        from.suspension != null && from.suspension.isEmpty && from.currentProgression != null &&
        from.currentProgression.contains(ProgressionReference("phase.PhaseInspection"))) Consequence.unit
    else _invalid("requires the original all-closed whole-Phase inspection source")

  /** History-only reconstruction. Supplied snapshot/policy are compared live only by callers. */
  def historicalTargetC(session: F.Session, d: F.Declaration, core: ActionCall.Core, from: InstanceRecord,
    controller: O.State, documentPolicy: Option[GoalPhaseDocumentProgress.Policy], recordingPaths: Vector[String],
    completedCandidates: Vector[C.Payload], repository: K.RepositorySnapshot, policy: Policy): Consequence[Target] = for {
    _ <- originalSourceC(controller, from)
    original <- GoalPhaseNativeHistory.prefixC(session.current, from.revision)
    _ <- if (original == from) Consequence.unit else _invalid("source differs from actual history")
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, C.codec)
    inspected <- _load_c(adapter, core, from, "inspection")
    inspectionrecord <- R.adjacentC(session.current, from, inspected._1, "ordered-candidate:inspection")
    staged <- inspected._2.selectedPaths.indices.foldLeft(Consequence.success((inspectionrecord, Vector.empty[R.StagePair]))) {
      case (prior, index) => for {
        previous <- prior
        intent <- _load_c(adapter, core, from, "stage-intent-" + index)
        intentrecord <- R.adjacentC(session.current, previous._1, intent._1, "ordered-candidate:stage-intent-" + index)
        result <- _load_c(adapter, core, from, "stage-result-" + index)
        resultrecord <- R.adjacentC(session.current, intentrecord, result._1, "ordered-candidate:stage-result-" + index)
      } yield (resultrecord, previous._2 :+ R.StagePair(index, _native_reference(from, "stage-intent-" + index).identity,
        "1", intentrecord.revision.value.toString, _native_reference(from, "stage-result-" + index).identity,
        "1", resultrecord.revision.value.toString, intent._2, result._2))
    }
    candidate <- adapter.loadRetainedC(_native_reference(from, "candidate"))(using core.executionContext)
    observation <- adapter.loadRetainedC(_native_reference(from, "observation-intent"))(using core.executionContext)
    historyadapter <- session.storage.bindMechanicalC(F.advanceOperation, GoalPhaseNativeSourceHistory.codec)
    historyintent <- historyadapter.loadRetainedC(_native_reference(from, "source-history-intent-0"))(using core.executionContext)
    historyresult <- historyadapter.loadRetainedC(_native_reference(from, "source-history-result-0"))(using core.executionContext)
    _ <- if (historyintent.isEmpty && historyresult.isEmpty) Consequence.unit
      else _invalid("whole-Phase preparation cannot contain any source-history receipt")
    _ <- if (candidate.isEmpty && observation.isEmpty) Consequence.unit else _invalid("original candidate or observation intent already exists")
    target = Target(from.identity.value, R.controllerSnapshot(controller), from.definition.workflowIdentity.value,
      from.definition.workflowRevision.value, session.participantIdentity, from.revision.value.toString,
      staged._1.revision.value.toString, d.target.root.toString, d.target.localBranch, d.candidatePaths,
      (documentPolicy.toVector.flatMap(_.paths) ++ recordingPaths).distinct, documentPolicy, recordingPaths,
      completedCandidates, _native_reference(from, "inspection").identity, "1", inspectionrecord.revision.value.toString,
      inspected._2, staged._2, "UNKNOWN", "termination-unconfirmed", repository, policy)
    _ <- if (target.controller.base == session.started.receipt.payload.value.base && validTarget(target)) Consequence.unit
      else _invalid("all-closed controller, completed candidates, inspection or contiguous staging prefix differs")
  } yield target
  private def _load_c(adapter: WorkflowManagedMechanicalTransition[C.Payload], core: ActionCall.Core,
    source: InstanceRecord, key: String): Consequence[(InstanceRecord, C.Payload)] =
    adapter.loadRetainedC(_native_reference(source, key))(using core.executionContext).flatMap(_.map(value =>
      Consequence.success((value.receipt.next, value.receipt.payload.value))).getOrElse(_invalid("missing exact native receipt: " + key)))

  def requireFrontierC(session: F.Session, target: Target): Consequence[Unit] =
    if (session.current.revision.value.toString == target.frontierRevision && session.current.suspension.isEmpty &&
        session.current.currentProgression.contains(ProgressionReference("phase.PhaseInspection"))) Consequence.unit
    else _invalid("requires the exact original whole-Phase inspection/staging frontier")
  def requirePolicyC(d: F.Declaration, core: ActionCall.Core, target: Target): Consequence[Unit] =
    resolvePolicyC(d, core).flatMap(actual => if (actual == target.policy) Consequence.unit
      else _invalid("resolved Phase validation, process host plan or diff policy changed"))
  def appendC(session: F.Session, d: F.Declaration, core: ActionCall.Core, from: InstanceRecord,
    target: Target, reference: ContextReference, rationale: String, priorExecutionEnded: Boolean): Consequence[Unit] = for {
    _ <- requireFrontierC(session, target)
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    existing <- adapter.loadRetainedC(_reference(from))(using core.executionContext)
    _ <- if (existing.isEmpty) Consequence.unit else _invalid("direction already exists")
    payload = Direction(target, reference, session.participantIdentity, rationale, priorExecutionEnded)
    _ <- codec.decode(codec.encode(payload)).fold(_invalid, _ => Consequence.unit)
    _ <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, _reference(from), session.current,
      session.current.revision, F.history(session.current, "phase.PhaseInspection", "ordered-whole-phase-candidate-recovery:" + from.revision.value),
      TypedValue(codec.typeIdentity, payload)), ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(
      using core.executionContext)
  } yield ()
  final case class Resumed(record: InstanceRecord, target: Target)
  /** Reconstruct accepted history with its captured paths; current admission stays strict. */
  private def _captured_target_c(session: F.Session, d: F.Declaration, core: ActionCall.Core, from: InstanceRecord,
    controller: O.State, documentpolicy: Option[GoalPhaseDocumentProgress.Policy], recordingpaths: Vector[String],
    completedcandidates: Vector[C.Payload], captured: Target): Consequence[Target] = for {
    _ <- if (validTarget(captured) && d.candidatePaths != null && d.candidatePaths.distinct == d.candidatePaths &&
        d.candidatePaths.forall(path => path != null && RepositorySyncNativeConflictInput.relative(path)) &&
        d.candidatePaths.startsWith(captured.candidatePaths)) Consequence.unit
      else _invalid("retained direction candidate policy is not a safe ordered extension of its captured original")
    actual <- historicalTargetC(session, d.copy(candidatePaths = captured.candidatePaths), core, from, controller,
      documentpolicy, recordingpaths, completedcandidates, captured.repository, captured.policy)
    _ <- if (actual == captured) Consequence.unit else _invalid("retained direction differs from actual original prefix")
  } yield actual
  def retainedC(session: F.Session, d: F.Declaration, core: ActionCall.Core, from: InstanceRecord,
    controller: O.State, documentPolicy: Option[GoalPhaseDocumentProgress.Policy], recordingPaths: Vector[String],
    completedCandidates: Vector[C.Payload]): Consequence[Option[Resumed]] = for {
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    saved <- adapter.loadRetainedC(_reference(from))(using core.executionContext)
    resumed <- saved match {
      case None => Consequence.success(Option.empty[Resumed])
      case Some(value) => for {
        direction <- codec.decode(codec.encode(value.receipt.payload.value)).fold(_invalid, Consequence.success)
        actual <- _captured_target_c(session, d, core, from, controller, documentPolicy, recordingPaths,
          completedCandidates, direction.target)
        frontier <- GoalPhaseNativeHistory.prefixC(session.current, InstanceRevision(actual.frontierRevision.toLong))
        next <- R.adjacentC(session.current, frontier, value.receipt.next, "ordered-whole-phase-candidate-recovery:" + from.revision.value)
      } yield Some(Resumed(next, actual))
    }
  } yield resumed

  def operations(port: GoalPhaseNativeWorkContinuation.Port): Vector[spec.OperationDefinition] =
    Vector(new Operation(true, port), new Operation(false, port))
  private final class Operation(describe: Boolean, port: GoalPhaseNativeWorkContinuation.Port) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (describe)
      SmWorkflowComponent.GoalPhaseServiceService.DescribeGoalPhaseWholePhaseCandidateRecoveryOperation
      else SmWorkflowComponent.GoalPhaseServiceService.RecoverGoalPhaseWholePhaseCandidateOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains("GoalPhaseService") ||
          request.operation != (if (describe) describeOperation else recoveryOperation)) _invalid("operation identity differs")
      else if (describe) NativeWorkflowOperationInput.textC(request, "sessionId").map(id => Action(request, Some(id), None, port))
      else NativeWholePhaseRecoveryInput.selectC(request).map(source => Action(request, None, Some(source), port))
  }
  private final case class Action(request: Request, sessionId: Option[String], input: Option[NativeWholePhaseRecoveryInput.Source],
    port: GoalPhaseNativeWorkContinuation.Port) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = for {
        supplied <- input match {
          case None => Consequence.success(Option.empty[V.GoalPhaseWholePhaseCandidateRecoveryInput])
          case Some(source) => NativeWholePhaseRecoveryInput.readC(source, reference => read_resource_text(reference, Some(UTF_8))).flatMap { value =>
            if (value == null || !_exact(value.sessionId) || value.reference == null || !choices.contains(value.choice) ||
                !_text(value.rationale) || !_exact(value.reference.identity) || !_exact(value.reference.revision))
              _invalid("explicit Human input is incomplete")
            else Consequence.success(Some(value))
          }
        }
        id <- supplied.map(_.sessionId).orElse(sessionId).map(Consequence.success)
          .getOrElse(_invalid("Session is absent"))
        context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(id))
        selected = core.copy(executionContext = context)
        output <- supplied match {
          case None => port.describeWholePhaseCandidateRecoveryC(id, selected).map(NativeWorkflowOperationOutput.response(_))
          case Some(value) => port.recoverWholePhaseCandidateC(value, selected).map(NativeWorkflowOperationOutput.response(_))
        }
      } yield output
    }
  }
  private def _revision(value: String): Boolean = value != null && value.toLongOption.exists(n => n > 0 && n.toString == value)
  private def _snapshot(value: O.Snapshot): Boolean = value != null && Vector(value.head, value.tree).forall(_object)
  private def _object(value: String): Boolean = value != null && value.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")
  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  private def _text(value: String): Boolean = value != null && value.trim.nonEmpty && !value.contains('\u0000') &&
    java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(value) && value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 65536
  private def _invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("Whole-Phase candidate recovery: " + reason)
}
