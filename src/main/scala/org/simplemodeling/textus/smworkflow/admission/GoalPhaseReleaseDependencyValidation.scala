package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.deriveDecoder
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workspace.WorkspaceId
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.{ContinuationResult, TypedValue}
import GoalPhaseNativeRun as F
import GoalPhaseDependencyValidation as D
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeCandidate as C
import GoalPhaseNativeWork as W
import GoalPhaseNativeDependencyValidation as N
import WorkflowManagedProcessExecution as E
import D.given

/** Release-only dependency succession retains the original primary candidate.
  * Only the native walk can turn these mechanical receipts into chain authority.
  * @since   Oct. 28, 2026
  * @version Oct. 28, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseReleaseDependencyValidation {
  val schema = "sm-workflow.goal-phase-release-dependency-validation.v1"
  val causationPrefix = "ordered-release-dependency:"
  final case class Payload(session: String, plan: ContextReference, base: O.Snapshot,
    generation: Long, repairProgress: GoalPhaseRepairProgress.Snapshot, frontierRevision: String,
    primaryRecordRevision: String, primarySourceRevision: String, primary: O.Snapshot,
    admission: D.Admission, staged: Vector[String], capture: Option[D.Capture] = None)
  private val _payload_decoder: Decoder[Payload] = deriveDecoder
  private given Decoder[Payload] = Decoder.instance { cursor =>
    val required = Set("session", "plan", "base", "generation", "repairProgress", "frontierRevision",
      "primaryRecordRevision", "primarySourceRevision", "primary", "admission", "staged")
    val keys = required ++ Option.when(cursor.downField("capture").focus.nonEmpty)("capture")
    if (!cursor.value.asObject.exists(o => o.keys.toSet == keys && o.values.forall(!_.isNull)))
      Left(DecodingFailure("Release dependency requires exact non-null fields", cursor.history))
    else _payload_decoder(cursor).flatMap(p => if (payloadValid(p)) Right(p)
      else Left(DecodingFailure("Invalid Release dependency frontier or capture", cursor.history)))
  }
  val codec = new WorkflowResultJsonV1.PayloadCodec[Payload] {
    val typeIdentity = "GoalPhaseReleaseDependencyValidationReceipt"
    def encode(value: Payload): Json = Json.obj("schemaVersion" -> schema.asJson,
      "payload" -> value.asJson.mapObject(o => if (value.capture.isEmpty) o.remove("capture") else o))
    def decode(value: Json): Either[String, Payload] = for {
      wire <- Option(value).toRight("Release dependency receipt is missing")
      _ <- Either.cond(wire.asObject.exists(_.keys.toSet == Set("schemaVersion", "payload")), (),
        "Release dependency receipt has unknown fields")
      version <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(version == schema, (), "Release dependency schema differs")
      payload <- wire.hcursor.get[Payload]("payload").left.map(_.message)
      _ <- Either.cond(encode(payload) == wire, (), "Release dependency payload has unknown or noncanonical nested fields")
    } yield payload
  }
  final case class Source(session: F.Session, from: InstanceRecord, controller: O.State,
    candidate: C.Saved, admission: D.Admission, chain: GoalPhaseNativeWorkContinuation.DependencyChain)
  final case class Saved(record: InstanceRecord, payload: Payload)
  final case class Progress(source: Source, saved: Saved, unknown: Boolean) {
    def complete: Boolean = saved.payload.capture.nonEmpty
  }

  private def _revision(value: String): Boolean = value != null &&
    value.toLongOption.exists(n => n > 0 && n.toString == value)
  def payloadValid(value: Payload): Boolean = value != null && value.session != null && value.session.nonEmpty &&
    value.plan != null && value.plan.identity != null && value.plan.identity.nonEmpty &&
    value.plan.revision != null && value.plan.revision.nonEmpty && value.base != null && value.primary != null &&
    Vector(value.base.head, value.base.tree, value.primary.head, value.primary.tree).forall(v => GitObjectId.parseC(v).isSuccess) &&
    value.generation > 0 && GoalPhaseRepairProgress.validateC(value.repairProgress).isSuccess && value.repairProgress.automaticCount > 0 &&
    _revision(value.frontierRevision) && _revision(value.primaryRecordRevision) && _revision(value.primarySourceRevision) &&
    BigInt(value.primarySourceRevision) < BigInt(value.primaryRecordRevision) &&
    BigInt(value.primaryRecordRevision) <= BigInt(value.frontierRevision) &&
    D.admissionValid(value.admission) && value.admission.binding.step.startsWith("phase:") &&
    value.admission.acceptedPrefix.exists(p => p.sessionId == value.session && p.plan == value.plan &&
      !p.paths.exists(value.admission.binding.paths.contains)) &&
    value.staged != null && value.staged == value.admission.binding.paths.take(value.staged.size) &&
    value.capture != null && value.capture.forall(c => D.captureValid(c) && c.session == value.session &&
      c.plan == value.plan && c.primaryBase == value.base && c.generation == value.generation &&
      c.admission == value.admission && value.staged == value.admission.binding.paths &&
      BigInt(c.sourceRevision) > BigInt(value.frontierRevision))

  /** Public JSON can pass structural validation but cannot construct this Source's chain. */
  def sourceC(session: F.Session, from: InstanceRecord, controller: O.State, candidate: C.Saved,
    binding: D.Binding, approvals: Vector[D.Approval], chain: GoalPhaseNativeWorkContinuation.DependencyChain,
    lastResult: Option[(W.Request, ContinuationResult[W.Result])]): Consequence[Source] = for {
    _ <- _check(controller.stage == O.Stage.PhaseValidation && controller.releaseRepairActive &&
      controller.completed.map(_.step) == controller.plan.steps.map(_.identity) &&
      controller.completed.forall(O.validStepAcceptance) && controller.cursor == controller.plan.steps.size &&
      controller.checksReceipt.isEmpty && controller.reviewEvidence.isEmpty &&
      candidate.payload.step == "phase:" + controller.plan.phase && candidate.payload.candidate.nonEmpty &&
      candidate.payload.diff.nonEmpty && candidate.payload.candidate == controller.candidate &&
      candidate.payload.dependency.isEmpty && candidate.payload.dependencySourceCapture.isEmpty &&
      candidate.record.currentProgression.contains(ProgressionReference("phase.PhaseValidation")) &&
      lastResult.exists { case (request, result) => request.kind == "RepairStep" && request.stepIdentity.isEmpty &&
        request.instanceIdentity == session.current.identity.value && request.generation == controller.generation &&
        request.selectedPlan == controller.plan && request.originalBase == controller.base &&
        result.result.value.outcome == "completed" }, "requires the accepted actual Release repair and original primary candidate")
    prefix <- chain.anchorC(binding)
    admission <- D.phaseAdmissionC(binding, approvals, controller.plan, prefix)
    _ <- _check(prefix.exists(p => p.sessionId == session.current.identity.value) &&
      chain.acceptedSources.forall(a => a.capture.direction.target.binding.dependency.identity != binding.dependency.identity),
      "phase succession crosses its native Session or revised-source lineage")
    _ <- chain.resolveC(admission)
    actual <- GoalPhaseNativeHistory.prefixC(session.current, from.revision)
    primary <- GoalPhaseNativeHistory.prefixC(session.current, candidate.record.revision)
    _ <- _check(actual == from && primary == candidate.record && from.suspension.isEmpty &&
      from.currentProgression.contains(ProgressionReference("phase.PhaseValidation")) &&
      from.revision.value >= candidate.record.revision.value &&
      !from.history.drop(candidate.record.history.size).exists(_.causation.value.startsWith("ordered-validation:")),
      "original Phase validation has already selected checks, metadata, an intent or an outcome")
    source = Source(session, from, controller, candidate, admission, chain)
    _ <- _check(payloadValid(_initial(source)), "invalid native Release succession source")
  } yield source

  private def _initial(source: Source): Payload = Payload(source.from.identity.value,
    ContextReference(source.controller.plan.identity, source.controller.plan.revision), source.controller.base,
    source.controller.generation, GoalPhaseRepairProgress.snapshot(source.controller), source.from.revision.value.toString,
    source.candidate.record.revision.value.toString, source.candidate.payload.sourceRevision,
    source.candidate.payload.candidate.get, source.admission, Vector.empty)
  private def _reference(source: Source, key: String) = WorkflowMechanicalTransition.Reference(
    source.from.identity.value + ":" + causationPrefix + source.from.revision.value + ":" + key, "1")
  private def _selection_c(path: String, index: Int): Consequence[GitSelectionParameters] = for {
    identity <- GitSelectionId.parseC("goal-release-path-" + (index + 1))
    selected <- GitPath.parseC(path)
  } yield GitSelectionParameters(identity, Vector(selected), None)

  def preflightC(core: ActionCall.Core, admission: D.Admission): Consequence[Unit] =
    admission.binding.paths.zipWithIndex.foldLeft(Consequence.unit) { case (prior, (path, index)) => prior.flatMap { _ => for {
      workspace <- WorkspaceId.parseC(admission.binding.dependency.workspace)
      selected <- _selection_c(path, index)
      _ <- GitExecutionPreflight.validateC(core.executionContext, GitTarget(workspace, Some(selected)),
        GitOperation.StageSelectedAll(selected.identity))
    } yield () } }

  /** Prefix-tree-to-index includes unstaged/untracked paths through PendingPaths;
    * protected old files must have no worktree delta at every effect boundary. */
  def repositoryC(core: ActionCall.Core, source: Source, staged: Boolean): Consequence[Unit] = for {
    actual <- N.repositoryC(core, source.admission, staged, Some(source.chain))
    prefix <- source.admission.acceptedPrefix.map(Consequence.success).getOrElse(D.invalid("missing native prefix"))
    _ <- _check(actual.dirty.filter(p => prefix.paths.contains(p.path) || p.renamedFrom.exists(prefix.paths.contains))
      .forall(p => p.worktree == ' ' && p.renamedFrom.forall(prefix.paths.contains)),
      "protected prefix contains unstaged changes or a rename into the new purpose")
    parent <- GitObjectId.parseC(prefix.candidate.tree)
    changes <- E.executeGitC(core, GitOperation.PendingPaths(parent), source.admission.binding.dependency.workspace).flatMap(_.valueC)
    _ <- _check(changes.forall(p => source.admission.binding.paths.contains(p.value)),
      "protected prefix index changed or unapproved paths are pending")
  } yield ()

  private def _load_c(source: Source, core: ActionCall.Core, adapter: WorkflowManagedMechanicalTransition[Payload],
    key: String, previous: Saved, expected: Payload): Consequence[Option[Saved]] = for {
    retained <- adapter.loadRetainedC(_reference(source, key))(using core.executionContext)
    result <- retained match {
      case None => Consequence.success(Option.empty[Saved])
      case Some(value) => for {
        record <- GoalPhaseNativeHistory.prefixC(source.session.current, value.receipt.next.revision)
        canonical <- previous.record.appendC(previous.record.revision,
          F.history(previous.record, "phase.PhaseValidation", causationPrefix + key))
        _ <- _check(record == value.receipt.next && record == canonical && value.receipt.payload.value == expected,
          "receipt has a crossed source, native predecessor, causation or payload: " + key)
      } yield Some(Saved(record, expected))
    }
  } yield result

  /** Walk every actual contiguous receipt. A result-less effect intent is UNKNOWN. */
  def retainedC(source: Source, core: ActionCall.Core): Consequence[Progress] = for {
    adapter <- source.session.storage.bindMechanicalC(F.advanceOperation, codec)
    initial = Saved(source.from, _initial(source))
    stages <- source.admission.binding.paths.zipWithIndex.foldLeft(Consequence.success(Progress(source, initial, false))) {
      case (prior, (path, index)) => prior.flatMap { progress =>
        if (progress.unknown || progress.saved.payload.staged.size != index) Consequence.success(progress)
        else for {
          intent <- _load_c(source, core, adapter, "stage-intent-" + index, progress.saved, progress.saved.payload)
          next <- intent match {
            case None => Consequence.success(progress)
            case Some(value) => _load_c(source, core, adapter, "stage-result-" + index, value,
              value.payload.copy(staged = value.payload.staged :+ path)).map {
                case Some(result) => Progress(source, result, false)
                case None => Progress(source, value, true)
              }
          }
        } yield next
      }
    }
    result <- if (stages.unknown || stages.saved.payload.staged != source.admission.binding.paths) Consequence.success(stages)
      else for {
        intent <- _load_c(source, core, adapter, "observation-intent", stages.saved, stages.saved.payload)
        result <- intent match {
          case None => Consequence.success(stages)
          case Some(value) => for {
            retained <- adapter.loadRetainedC(_reference(source, "observation-result"))(using core.executionContext)
            completed <- retained match {
              case None => Consequence.success(Progress(source, value, true))
              case Some(saved) => for {
                capture <- saved.receipt.payload.value.capture.map(Consequence.success).getOrElse(D.invalid("capture result is empty"))
                _ <- _check(D.captureValid(capture) && capture.sourceRevision == value.record.revision.value.toString,
                  "capture source is not its actual observation intent")
                checked <- _load_c(source, core, adapter, "observation-result", value,
                  value.payload.copy(capture = Some(capture))).flatMap(_.map(Consequence.success)
                    .getOrElse(D.invalid("capture result disappeared")))
              } yield Progress(source, checked, false)
            }
          } yield completed
        }
      } yield result
    next <- GoalPhaseNativeHistory.nextC(source.session.current, result.saved.record)
    _ <- _check(!next.exists(_.causation.value.startsWith(causationPrefix)),
      "unrecognized, skipped or contradictory Release succession history")
  } yield result

  private def _save_c(source: Source, d: F.Declaration, core: ActionCall.Core,
    adapter: WorkflowManagedMechanicalTransition[Payload], key: String, previous: Saved, payload: Payload): Consequence[Saved] = for {
    _ <- _check(payloadValid(payload), "invalid generated payload")
    saved <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, _reference(source, key),
      previous.record, previous.record.revision, F.history(previous.record, "phase.PhaseValidation", causationPrefix + key),
      TypedValue(codec.typeIdentity, payload)),
      ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
  } yield Saved(saved.receipt.next, saved.receipt.payload.value)

  def advanceC(source: Source, d: F.Declaration, core: ActionCall.Core,
    acknowledgement: String => Consequence[Unit]): Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult] = for {
    progress <- retainedC(source, core)
    _ <- _check(!progress.unknown, "UNKNOWN effect intent cannot be replayed")
    _ <- _check(source.session.current == progress.saved.record, "succession is not the current native frontier")
    _ <- _check(d.phaseDependencyValidation.contains(source.admission.binding), "selected phase binding changed")
    _ <- preflightC(core, source.admission)
    _ <- repositoryC(core, source, false)
    adapter <- source.session.storage.bindMechanicalC(F.advanceOperation, codec)
    staged <- source.admission.binding.paths.zipWithIndex.drop(progress.saved.payload.staged.size)
      .foldLeft(Consequence.success(progress.saved)) { case (prior, (path, index)) => for {
        previous <- prior
        _ <- repositoryC(core, source, false)
        selected <- _selection_c(path, index)
        intent <- _save_c(source, d, core, adapter, "stage-intent-" + index, previous, previous.payload)
        _ <- acknowledgement("goal-release-dependency-stage-prepared:" + path)
        _ <- E.executeSelectedGitC(core, GitOperation.StageSelectedAll(selected.identity), selected,
          source.admission.binding.dependency.workspace).flatMap(_.valueC)
        saved <- _save_c(source, d, core, adapter, "stage-result-" + index, intent, previous.payload.copy(staged = previous.payload.staged :+ path))
        _ <- acknowledgement("goal-release-dependency-stage-recorded:" + path)
      } yield saved }
    intent <- _save_c(source, d, core, adapter, "observation-intent", staged, staged.payload)
    _ <- acknowledgement("goal-release-dependency-observation-prepared")
    _ <- repositoryC(core, source, true)
    binding = source.admission.binding
    before <- N.repositoryC(core, source.admission, true, Some(source.chain))
    tree <- E.executeGitC(core, GitOperation.IndexTree, binding.dependency.workspace).flatMap(_.valueC)
    base <- GitObjectId.parseC(N.baseline(source.admission))
    paths <- E.executeGitC(core, GitOperation.PendingPaths(base), binding.dependency.workspace).flatMap(_.valueC)
    diff <- E.executeGitC(core, GitOperation.PendingDiff(base), binding.dependency.workspace).flatMap(_.valueC)
    _ <- repositoryC(core, source, true)
    after <- N.repositoryC(core, source.admission, true, Some(source.chain))
    aftertree <- E.executeGitC(core, GitOperation.IndexTree, binding.dependency.workspace).flatMap(_.valueC)
    afterpaths <- E.executeGitC(core, GitOperation.PendingPaths(base), binding.dependency.workspace).flatMap(_.valueC)
    afterdiff <- E.executeGitC(core, GitOperation.PendingDiff(base), binding.dependency.workspace).flatMap(_.valueC)
    capture = D.Capture(source.from.identity.value, ContextReference(source.controller.plan.identity, source.controller.plan.revision),
      binding.step, source.controller.generation, intent.record.revision.value.toString, source.controller.base,
      source.admission, O.Snapshot(before.head, tree.value), paths.map(_.value), diff.text)
    _ <- _check(before == after && tree == aftertree && paths == afterpaths && diff == afterdiff &&
      !diff.isBinary && D.captureValid(capture),
      "dependency changed during observation or incremental candidate is empty, binary or unapproved")
    saved <- _save_c(source, d, core, adapter, "observation-result", intent, staged.payload.copy(capture = Some(capture)))
    _ <- acknowledgement("goal-release-dependency-observation-recorded")
    wire <- F.waitC(saved.record, "Release dependency captured", "Actual new dependency paths are staged and captured. Original primary candidate and publication remain; ordinary Phase checks and independent review follow.")
  } yield wire

  private def _check(condition: Boolean, reason: String): Consequence[Unit] =
    if (condition) Consequence.unit else D.invalid("Release succession: " + reason)
}
