package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.generic.auto.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.processexecution.{GitObjectId, GitOperation, GitPath}
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import GoalPhaseNativeCandidate as C
import GoalPhaseNativeRun as F
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeCandidateBoundary as B
import RepositorySyncManagedGitObservation as G
import WorkflowManagedProcessExecution as E

/** Supplemental committed work for an already observed clean checkpoint.
  * Capture is read-only Git plus one managed append; replay never observes Git.
  * @since   Nov.  9, 2026
  * @version Nov.  9, 2026
  */
private[smworkflow] object GoalPhaseCommittedStepWork {
  final case class Work(originalCandidate: ContextReference, acceptedTip: O.Snapshot,
    continuation: O.CandidateContinuation, paths: Vector[String], diff: String)
  object Work {
    given Encoder[Work] = deriveEncoder
    private val _decoder: Decoder[Work] = deriveDecoder
    given Decoder[Work] = Decoder.instance(cursor => _decoder(cursor).flatMap(value =>
      if (cursor.value.asObject.exists(_.keys.toSet == Set("originalCandidate", "acceptedTip", "continuation", "paths", "diff")) &&
          valid(value)) Right(value)
      else Left(DecodingFailure("Invalid committed Step work", cursor.history))))
  }
  def valid(value: Work): Boolean = value != null && value.originalCandidate != null &&
    value.originalCandidate.identity != null && value.originalCandidate.identity.nonEmpty &&
    value.originalCandidate.revision != null && value.originalCandidate.revision.toLongOption.exists(n =>
      n > 0 && n.toString == value.originalCandidate.revision) &&
    value.continuation != null && value.continuation.lineage != null &&
    value.continuation.lineage.headOption.exists(c => c != null && c.parents != null && c.parents.size == 1 &&
      O.qualifiedCheckpointValid(value.acceptedTip, value.continuation,
        O.Commit(c.identity, c.parents.head, c.tree))) &&
    value.paths != null && value.paths.nonEmpty &&
    value.paths.distinct == value.paths && value.paths.forall(p => p != null && GitPath.parseC(p).isSuccess) &&
    value.diff != null && value.diff.trim.nonEmpty && !value.diff.contains('\u0000')

  def bound(payload: C.Payload, work: Work): Boolean = payload != null && valid(work) &&
    payload.continuation.contains(work.continuation) && payload.candidate.contains(work.continuation.parent) &&
    payload.changedPaths == Vector.empty && payload.diff.contains("") && payload.stepEvidence.isEmpty &&
    payload.selectedPaths == payload.stagedPaths && payload.sourceRevision != null &&
    payload.sourceRevision.toLongOption.exists(n => n > 0 && n < work.originalCandidate.revision.toLong)

  def eligible(controller: O.State, candidate: C.Saved): Boolean = controller != null && candidate != null &&
    candidate.payload != null && candidate.payload.committedWork.isEmpty &&
    controller.stage == O.Stage.Validation && controller.cursor < controller.plan.steps.size && controller.candidate.nonEmpty &&
    controller.checksReceipt.isEmpty && controller.reviewEvidence.isEmpty && controller.closureIntent.isEmpty &&
    controller.stepAdmission.isEmpty && candidate.payload.step == controller.plan.steps(controller.cursor).identity &&
    candidate.payload.candidate == controller.candidate && candidate.payload.continuation.exists(p =>
      p.parent == controller.candidateBase && p.parent.tree == controller.candidate.get.tree &&
      O.continuationValid(controller.tipSnapshot, p) &&
      p.lineage.sliding(2).forall(pair => pair.head.parents == Vector(pair.last.identity))) &&
    candidate.payload.changedPaths.isEmpty && candidate.payload.diff.contains("") &&
    candidate.payload.selectedPaths == candidate.payload.stagedPaths && candidate.payload.stepEvidence.isEmpty

  private def _reference(record: InstanceRecord) = WorkflowMechanicalTransition.Reference(
    record.identity.value + ":ordered-candidate:" + record.revision.value + ":committed-work", "1")

  /** Structural relation only. Storage readers separately authenticate both records. */
  private[admission] def relationC(original: C.Saved, supplement: C.Saved,
    controller: O.State): Consequence[Unit] = for {
    _ <- if (eligible(controller, original) && supplement != null && supplement.payload != null)
      Consequence.unit else F.invalid("committed work relation has no eligible original candidate")
    work <- supplement.payload.committedWork.filter(w => bound(supplement.payload, w) &&
      w.originalCandidate == ContextReference(original.record.identity.value, original.record.revision.value.toString) &&
      w.acceptedTip == controller.tipSnapshot && B.pathsOwned(w.paths, controller.plan.steps(controller.cursor).ownedPaths))
      .map(Consequence.success).getOrElse(F.invalid("committed work relation crosses its exact original record, Step or ownership"))
    _ <- if (supplement.payload.copy(committedWork = None) == original.payload) Consequence.unit
      else F.invalid("committed work relation changes original candidate facts")
    expected <- original.record.appendC(original.record.revision,
      F.history(original.record, "phase.Validation", "ordered-candidate:committed-work"))
    _ <- if (expected == supplement.record) Consequence.unit
      else F.invalid("committed work relation is not the exact adjacent native Validation history")
  } yield ()

  private def _original_c(session: F.Session, core: ActionCall.Core, candidate: C.Saved): Consequence[Unit] = for {
    _ <- if (candidate.record != null && candidate.payload.committedWork.isEmpty &&
        candidate.record.currentProgression.contains(ProgressionReference("phase.Validation")) &&
        candidate.record.causation.value == "ordered-candidate:candidate") Consequence.unit
      else F.invalid("committed work has no original native candidate")
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, C.codec)
    original <- adapter.loadRetainedC(WorkflowMechanicalTransition.Reference(candidate.record.identity.value +
      ":ordered-candidate:" + candidate.payload.sourceRevision + ":candidate", "1"))(using core.executionContext)
    _ <- if (original.exists(v => v.receipt.next == candidate.record && v.receipt.payload.value == candidate.payload))
      Consequence.unit else F.invalid("committed work rewrites its original actual managed candidate")
    instances <- session.storage.bindInstancesC()(using core.executionContext)
    actual <- instances.loadC(candidate.record.identity)(using core.executionContext).flatMap(_.map(Consequence.success)
      .getOrElse(F.invalid("committed work instance is absent")))
    prefix <- GoalPhaseNativeHistory.prefixC(actual, candidate.record.revision)
    _ <- if (prefix == candidate.record) Consequence.unit else F.invalid("committed work original is not actual native history")
  } yield ()

  /** Only load the exact adjacent supplement. Never append from retained code. */
  def retainedC(session: F.Session, core: ActionCall.Core, original: C.Saved,
    controller: O.State): Consequence[Option[C.Saved]] = for {
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, C.codec)
    value <- adapter.loadRetainedC(_reference(original.record))(using core.executionContext)
    saved <- value match {
      case None => Consequence.success(Option.empty[C.Saved])
      case Some(value) => for {
        _ <- _original_c(session, core, original)
        payload = value.receipt.payload.value
        _ <- relationC(original, C.Saved(value.receipt.next, payload), controller)
        expected = value.receipt.next
        instances <- session.storage.bindInstancesC()(using core.executionContext)
        current <- instances.loadC(original.record.identity)(using core.executionContext).flatMap(_.map(Consequence.success)
          .getOrElse(F.invalid("committed work instance is absent")))
        prefix <- GoalPhaseNativeHistory.prefixC(current, expected.revision)
        _ <- if (prefix == expected) Consequence.unit else F.invalid("committed work supplement is outside actual native history")
      } yield Some(C.Saved(expected, payload))
    }
  } yield saved

  def authenticateC(session: F.Session, core: ActionCall.Core, candidate: C.Saved,
    controller: O.State): Consequence[Unit] = for {
    work <- candidate.payload.committedWork.map(Consequence.success).getOrElse(F.invalid("committed work is absent"))
    instances <- session.storage.bindInstancesC()(using core.executionContext)
    current <- instances.loadC(candidate.record.identity)(using core.executionContext).flatMap(_.map(Consequence.success)
      .getOrElse(F.invalid("committed work instance is absent")))
    revision <- if (valid(work)) Consequence.success(InstanceRevision(work.originalCandidate.revision.toLong))
      else F.invalid("committed work has no valid original revision")
    original <- GoalPhaseNativeHistory.prefixC(current, revision)
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, C.codec)
    retained <- adapter.loadRetainedC(_reference(original))(using core.executionContext)
    _ <- _original_c(session, core, C.Saved(original, candidate.payload.copy(committedWork = None)))
    expected <- original.appendC(original.revision, F.history(original, "phase.Validation", "ordered-candidate:committed-work"))
    _ <- if (work.originalCandidate.identity == original.identity.value && bound(candidate.payload, work) &&
        expected == candidate.record && retained.exists(v => v.receipt.next == expected && v.receipt.payload.value == candidate.payload) &&
        work.acceptedTip == controller.tipSnapshot && candidate.payload.step == controller.plan.steps(controller.cursor).identity &&
        B.pathsOwned(work.paths, controller.plan.steps(controller.cursor).ownedPaths)) Consequence.unit
      else F.invalid("committed work does not bind its exact retained candidate and current Step")
  } yield ()

  /** Retained live replay checks immutable Git facts without pinning later HEAD/index. */
  def verifyCommittedFactsC(core: ActionCall.Core, work: Work): Consequence[Unit] = for {
    _ <- if (valid(work)) Consequence.unit else F.invalid("committed work proof is malformed")
    _ <- work.continuation.lineage.foldLeft(Consequence.unit) { (prior, saved) => for {
      _ <- prior
      actual <- G.commitC(core, saved.identity)
      _ <- if (actual.identity == saved.identity && actual.tree == saved.tree && actual.parents == saved.parents) Consequence.unit
        else F.invalid("committed work ancestry differs from actual native metadata")
    } yield () }
    basetree <- GitObjectId.parseC(work.acceptedTip.tree)
    endtree <- GitObjectId.parseC(work.continuation.parent.tree)
    changes <- E.executeGitC(core, GitOperation.TreeChanges(basetree, endtree)).flatMap(_.valueC)
    _ <- if (changes.map(_.path.value).toSet == work.paths.toSet) Consequence.unit
      else F.invalid("committed work paths differ from immutable accepted-tip-to-checkpoint trees")
  } yield ()

  /** Typed Git observations pin HEAD/tree/index before and after the full diff. */
  def observeC(d: F.Declaration, core: ActionCall.Core, work: Work): Consequence[Unit] = for {
    _ <- if (valid(work)) Consequence.unit else F.invalid("committed work proof is malformed")
    root <- G.rootC(core)
    before <- G.inspectC(core)
    index <- G.indexTreeC(core)
    _ <- if (root == d.target.root.toString && before.observed.branch == d.target.localBranch &&
        before.mergeHead.isEmpty && before.observed.dirty.isEmpty &&
        O.Snapshot(before.observed.head, before.observed.tree) == work.continuation.parent && index == work.continuation.parent.tree)
      Consequence.unit else F.invalid("committed work checkpoint root, branch, clean state or index drifted")
    _ <- verifyCommittedFactsC(core, work)
    anchor <- GitObjectId.parseC(work.acceptedTip.head)
    paths <- E.executeGitC(core, GitOperation.PendingPaths(anchor)).flatMap(_.valueC)
    diff <- E.executeGitC(core, GitOperation.PendingDiff(anchor)).flatMap(_.valueC)
    _ <- if (paths.map(_.value) == work.paths &&
        !diff.isBinary && diff.text == work.diff) Consequence.unit
      else F.invalid("committed work paths or diff differ from the complete accepted-tip-to-checkpoint work")
    after <- G.inspectC(core)
    afterindex <- G.indexTreeC(core)
    afterroot <- G.rootC(core)
    _ <- if (before == after && index == afterindex && root == afterroot) Consequence.unit
      else F.invalid("committed work checkpoint drifted during observation")
  } yield ()

  def advanceC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    from: InstanceRecord, controller: O.State, original: C.Saved): Consequence[C.Saved] = for {
    _ <- if (from == original.record && eligible(controller, original)) Consequence.unit
      else F.invalid("committed work requires its exact unchecked original Validation frontier")
    _ <- _original_c(session, core, original)
    proof = original.payload.continuation.get
    anchor <- GitObjectId.parseC(controller.tipSnapshot.head)
    paths <- E.executeGitC(core, GitOperation.PendingPaths(anchor)).flatMap(_.valueC)
    diff <- E.executeGitC(core, GitOperation.PendingDiff(anchor)).flatMap(_.valueC)
    work = Work(ContextReference(from.identity.value, from.revision.value.toString), controller.tipSnapshot,
      proof, paths.map(_.value), diff.text)
    _ <- if (bound(original.payload, work) && !diff.isBinary && B.pathsOwned(work.paths,
        controller.plan.steps(controller.cursor).ownedPaths)) Consequence.unit
      else F.invalid("committed work is empty, binary or outside current Step ownership")
    _ <- observeC(d, core, work)
    instances <- session.storage.bindInstancesC()(using core.executionContext)
    current <- instances.loadC(from.identity)(using core.executionContext)
    _ <- if (current.contains(from)) Consequence.unit else F.invalid("committed work cannot omit later native history")
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, C.codec)
    saved <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, _reference(from), from, from.revision,
      F.history(from, "phase.Validation", "ordered-candidate:committed-work"),
      TypedValue(C.codec.typeIdentity, original.payload.copy(committedWork = Some(work)))),
      ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
  } yield C.Saved(saved.receipt.next, saved.receipt.payload.value)
}
