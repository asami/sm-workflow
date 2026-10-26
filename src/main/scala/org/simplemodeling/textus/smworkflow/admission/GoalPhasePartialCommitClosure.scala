package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workflow.WorkflowResultJsonV1
import GoalPhaseNativeClosure as K
import GoalPhaseClosureRecovery as R
import GoalPhaseOrderedProgression as O
import RepositorySyncManagedGitObservation as G
import WorkflowManagedProcessExecution as E

/** The bounded known-success P -> C -> D exception to ordinary Step closure.
  * All identities below are native Git object identities. No effect is inferred
  * from missing execution output, and completion records never replay an intent.
  * @since   Oct. 26, 2026
  * @version Oct. 26, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhasePartialCommitClosure {
  private given Encoder[K.Change] = deriveEncoder
  private given Decoder[K.Change] = deriveDecoder
  final case class Change(path: String, oldMode: String, newMode: String,
    oldBlob: Option[String], newBlob: Option[String])
  object Change {
    given Encoder[Change] = deriveEncoder
    given Decoder[Change] = deriveDecoder
  }
  final case class Proof(originalParent: O.Snapshot, partial: O.Commit, expectedTree: String,
    normalization: Vector[K.Change], candidateChanges: Vector[Change], committedChanges: Vector[Change],
    remainder: Vector[Change], selectedPaths: Vector[String])
  object Proof {
    given Encoder[Proof] = deriveEncoder
    given Decoder[Proof] = deriveDecoder
  }
  final case class Intent(direction: R.Direction, original: K.Payload)
  object Intent {
    given Encoder[Intent] = deriveEncoder
    given Decoder[Intent] = deriveDecoder
  }
  final case class Execution(intent: Intent, result: R.Execution)
  object Execution {
    given Encoder[Execution] = deriveEncoder
    given Decoder[Execution] = deriveDecoder
  }

  private def _oid(value: String): Boolean = value != null && GitObjectId.parseC(value).isSuccess
  private def _covers(scope: String, path: String): Boolean = scope == path || path.startsWith(scope + "/")
  def coverage(required: Vector[String], selected: Vector[String]): Boolean =
    required != null && required.nonEmpty && required.size <= 256 && required.distinct == required &&
      required.forall(path => path != null && GitPath.parseC(path).isSuccess) && R.validPaths(selected) &&
      required.forall(path => selected.exists(_covers(_, path)))
  private def _entry(mode: String, blob: Option[String]): Boolean = blob != null &&
    (if (mode == "000000") blob.isEmpty else Set("100644", "100755", "120000", "160000")(mode) && blob.exists(_oid))
  private def _changes(values: Vector[Change]): Boolean = values != null && values.size <= 256 &&
    values.forall(c =>
      c != null && c.path != null && GitPath.parseC(c.path).isSuccess && _entry(c.oldMode, c.oldBlob) &&
        _entry(c.newMode, c.newBlob) && (c.oldMode != c.newMode || c.oldBlob != c.newBlob)) &&
    values.map(_.path).distinct.size == values.size && values.sortBy(_.path) == values
  def validProof(value: Proof): Boolean = value != null && value.originalParent != null &&
    _oid(value.originalParent.head) && _oid(value.originalParent.tree) && value.partial != null &&
    _oid(value.partial.identity) && _oid(value.partial.tree) && value.partial.parent == value.originalParent.head &&
    value.partial.identity != value.originalParent.head && _oid(value.expectedTree) && value.expectedTree != value.partial.tree &&
    value.normalization != null && value.normalization.size <= 256 &&
    value.normalization.forall(_ != null) && value.normalization.map(_.path).distinct.size == value.normalization.size && value.normalization.forall(c =>
      c != null && c.path != null && GitPath.parseC(c.path).isSuccess && Set("100644", "100755")(c.mode) &&
        _oid(c.oldBlob) && _oid(c.newBlob) && c.oldBlob != c.newBlob) &&
    value.normalization.forall(c => value.candidateChanges != null && value.candidateChanges.exists(change =>
      change != null && change.path == c.path && change.newMode == c.mode && change.newBlob.contains(c.newBlob))) &&
    _changes(value.candidateChanges) && value.candidateChanges.nonEmpty &&
    _changes(value.committedChanges) && value.committedChanges.nonEmpty && _changes(value.remainder) && value.remainder.nonEmpty &&
    value.committedChanges.forall(value.candidateChanges.contains) &&
    value.remainder == value.candidateChanges.filterNot(value.committedChanges.contains) &&
    coverage(value.remainder.map(_.path), value.selectedPaths)

  def applicable(target: R.RecoveryTarget): Boolean = target != null && target.partialCompletion.exists { proof =>
    val observed = target.observation
    validProof(proof) && proof.originalParent == target.parent &&
      target.execution.exists(e => R.terminalExecution(e) && e.outcome == "succeeded" && e.exitCode.contains(0)) &&
      observed.head == proof.partial.identity && observed.tree == proof.partial.tree &&
      observed.commit.parents == Vector(target.parent.head) && observed.indexTree == proof.expectedTree && observed.mergeHead.isEmpty &&
      proof.selectedPaths == observed.selectedPaths &&
      (proof.normalization.nonEmpty || proof.expectedTree == target.candidate.tree) &&
      GoalPhaseNativeCandidateBoundary.pathsOwned(proof.candidateChanges.map(_.path), target.ownedPaths) &&
      GoalPhaseNativeCandidateBoundary.scopedDirty(observed.dirty, target.ownedPaths, true) &&
      observed.pendingPaths.toSet == proof.candidateChanges.map(_.path).toSet &&
      observed.dirty.filter(d => GoalPhaseNativeCandidateBoundary.touches(d, target.ownedPaths)).forall(d => d.worktree == ' ' &&
        Vector(Some(d.path), d.renamedFrom).flatten.forall(p => target.ownedPaths.exists(_covers(_, p))))
  }

  private def _change(value: GitTreeChange): Change = Change(value.path.value, value.oldMode, value.newMode,
    value.oldBlob.map(_.value), value.newBlob.map(_.value))
  private def _tree_changes_c(core: ActionCall.Core, before: String, after: String): Consequence[Vector[GitTreeChange]] = for {
    b <- GitObjectId.parseC(before)
    a <- GitObjectId.parseC(after)
    changes <- E.executeGitC(core, GitOperation.TreeChanges(b, a)).flatMap(_.valueC)
  } yield changes

  /** Invalid known partial observations remain describable with passive Defer. */
  def prepareC(core: ActionCall.Core, target: R.RecoveryTarget, original: K.Payload,
    failed: Vector[K.Precheck]): Consequence[Option[Proof]] = {
    if (!target.execution.exists(e => R.terminalExecution(e) && e.outcome == "succeeded") ||
        target.observation.head == target.parent.head || target.observation.commit.parents != Vector(target.parent.head) ||
        target.observation.tree == target.observation.indexTree) return Consequence.success(None)
    for {
      normalization <- _tree_changes_c(core, target.candidate.tree, target.observation.indexTree)
      blobs <- normalization.flatMap(c => c.oldBlob ++ c.newBlob).distinct.foldLeft(
        Consequence.success(Map.empty[String, GitBlobContent])) { (prior, id) => for {
          values <- prior
          blob <- E.executeGitC(core, GitOperation.BlobContent(id)).flatMap(_.valueC)
        } yield values.updated(id.value, blob) }
      accepted = K.admitEofCorrectionC(target.input, target.ownedPaths, failed, normalization, blobs).toOption
      finalchanges <- _tree_changes_c(core, target.parent.tree, target.observation.indexTree)
      committed <- _tree_changes_c(core, target.parent.tree, target.observation.tree)
      remaining <- _tree_changes_c(core, target.observation.tree, target.observation.indexTree)
      proof = Proof(target.parent, O.Commit(target.observation.head, target.parent.head, target.observation.tree),
        target.observation.indexTree, original.changes, finalchanges.map(_change).sortBy(_.path),
        committed.map(_change).sortBy(_.path), remaining.map(_change).sortBy(_.path), target.observation.selectedPaths)
    } yield Option.when(accepted.contains(original.changes) && applicable(target.copy(partialCompletion = Some(proof))))(proof)
  }

  def validIntent(value: Intent): Boolean = value != null && value.direction != null && value.original != null &&
    R.directionCodec.decode(R.directionCodec.encode(value.direction)).isRight &&
    value.direction.choice == "CompletePartialCommit" && value.original.input == value.direction.target.input &&
    value.original.ordinal == value.direction.target.ordinal && value.original.attempt == value.direction.target.attempt &&
    value.original.commit.isEmpty && value.original.partialCompletion.isEmpty && value.original.check.exists(_.findings.isEmpty) &&
    value.direction.target.partialCompletion.exists(_.normalization == value.original.changes)
  def validExecution(value: Execution): Boolean = value != null && validIntent(value.intent) &&
    R.validExecution(value.result) && value.result.input == value.intent.original.input &&
    value.result.ordinal == value.intent.original.ordinal && value.result.attempt == value.intent.original.attempt &&
    value.result.selectedPaths.forall(path => value.intent.direction.target.partialCompletion.get.selectedPaths.exists(_covers(_, path)))

  private def _codec[A: Encoder: Decoder](identity: String, schema: String, valid: A => Boolean) =
    new WorkflowResultJsonV1.PayloadCodec[A] {
      val typeIdentity = identity
      def encode(value: A): Json = Json.obj("schemaVersion" -> schema.asJson, "payload" -> value.asJson)
      def decode(value: Json): Either[String, A] = for {
        wire <- Option(value).toRight("Partial completion receipt is absent")
        version <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
        _ <- Either.cond(version == schema, (), "Partial completion receipt schema differs")
        payload <- wire.hcursor.get[A]("payload").left.map(_.message)
        _ <- Either.cond(valid(payload), (), "Partial completion receipt is invalid or crossed")
      } yield payload
    }
  val intentCodec: WorkflowResultJsonV1.PayloadCodec[Intent] = _codec[Intent]("GoalPhasePartialCompletionIntent",
    "sm-workflow.goal-phase-partial-completion-intent.v1", validIntent)
  val executionCodec: WorkflowResultJsonV1.PayloadCodec[Execution] = _codec[Execution]("GoalPhasePartialCompletionExecution",
    "sm-workflow.goal-phase-partial-completion-execution.v1", validExecution)
}
