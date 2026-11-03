package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workspace.*
import RepositorySyncNativeMergeRepairContinuation as R
import RepositorySyncNativeMergeReview as V
import RepositorySyncNativeProgressStore as N

/** Runtime-qualified existing file inventory, independent of lifecycle consent.
  * @since Nov. 3, 2026
  * @version Nov. 3, 2026
  */
private[smworkflow] object RepositorySyncRepairInventory {
  val policy = "sm-workflow.repository-sync-repair-inventory.v2"
  val purposePolicy = "sm-workflow.repository-sync-purpose-repair-inventory.v1"
  final case class Entry(path: String, mode: String, blob: String)
  final case class Purpose(snapshot: RepositorySyncPurpose.RepairSnapshot, files: Vector[Entry],
    baselines: Vector[RepositorySyncPurpose.File])
  final case class Scope(inventoryPolicy: String, instanceIdentity: String, sourceRevision: String,
    validatedRevision: String, failureReference: ContextReference, failureIdentity: String,
    localTip: String, remoteTip: String, candidateTree: String, paths: Vector[String], rationale: String,
    additions: Vector[Entry], purpose: Option[Purpose] = None)
  given Encoder[Entry] = deriveEncoder
  given Decoder[Entry] = deriveDecoder
  given Encoder[Purpose] = Encoder.instance(value => Json.obj("snapshot" -> value.snapshot.asJson,
    "files" -> value.files.asJson, "baselines" -> RepositorySyncPurposeContentContinuation.canonical(value.baselines.asJson)))
  private def _purpose_decoder: Decoder[Purpose] = deriveDecoder
  given Decoder[Purpose] = Decoder.instance(c => _purpose_decoder(c).flatMap(value =>
    if (value.asJson == c.value) Right(value)
    else Left(io.circe.DecodingFailure("Purpose inventory requires exact candidate blobs and baseline files", c.history))))
  given Encoder[Scope] = deriveEncoder[Scope].mapJson(_.mapObject(_.filter {
    case ("purpose", value) => !value.isNull; case _ => true
  }))
  private def _scope_decoder: Decoder[Scope] = deriveDecoder
  given Decoder[Scope] = Decoder.instance(c => _scope_decoder(c).flatMap(value =>
    if (value.asJson == c.value) Right(value)
    else Left(io.circe.DecodingFailure("Repair inventory requires its exact canonical contract", c.history))))

  def valid(scope: Scope, review: V.Request, failure: Option[String]): Boolean = scope != null &&
    review != null && review.validatedRevision != null && review.changedPaths != null &&
    review.checks != null && review.selectedChecks != null &&
    failure != null && failure.contains(scope.failureIdentity) && scope.purpose != null &&
    (if (scope.inventoryPolicy == policy) scope.purpose.isEmpty
      else scope.inventoryPolicy == purposePolicy && scope.purpose.exists(value => validPurpose(scope, value, review))) &&
    scope.instanceIdentity == review.instanceIdentity && scope.validatedRevision == review.validatedRevision &&
    scope.sourceRevision != null && scope.sourceRevision.toLongOption.exists(revision =>
      review.validatedRevision.toLongOption.exists(value => if (scope.inventoryPolicy == purposePolicy) value == revision else value < revision)) &&
    scope.failureReference != null && scope.failureReference.identity != null &&
    scope.failureReference.identity.startsWith(scope.instanceIdentity + ":native-") && scope.failureReference.revision == "1" &&
    scope.localTip == review.localTip && scope.remoteTip == review.remoteTip && scope.candidateTree == review.indexTree &&
    review.checks.exists(receipt => receipt != null && receipt.exitCode != null &&
      receipt.identity == scope.failureIdentity && receipt.termination == "exited" &&
      receipt.exitCode.exists(_ != 0) && review.selectedChecks.contains(receipt.identity)) &&
    scope.paths != null && scope.paths.nonEmpty && scope.paths == scope.paths.distinct.sorted &&
    scope.paths.forall(RepositorySyncNativeConflictInput.relative) && scope.rationale != null &&
    scope.rationale.trim.nonEmpty && !scope.rationale.contains('\u0000') &&
    java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(scope.rationale) &&
    scope.additions != null && scope.additions.forall(entry => entry != null && Set("100644", "100755")(entry.mode) &&
      entry.blob != null && entry.blob.matches("[0-9a-f]{40}|[0-9a-f]{64}")) &&
    scope.additions.map(_.path) == scope.paths.filterNot(review.changedPaths.contains)

  private def _entry(value: Entry): Boolean = value != null && RepositorySyncNativeConflictInput.relative(value.path) &&
    Set("100644", "100755")(value.mode) && value.blob != null && value.blob.matches("[0-9a-f]{40}|[0-9a-f]{64}")
  private[admission] def validPurpose(scope: Scope, value: Purpose, review: V.Request): Boolean = value != null &&
    scope.paths != null && scope.additions != null && review != null && review.changedPaths != null &&
    RepositorySyncPurposeRuntime.validRepairSnapshot(value.snapshot) && value.files != null &&
    value.files.forall(_entry) && value.files.map(_.path) == scope.paths && value.baselines != null &&
    value.baselines.map(_.path) == scope.paths.filterNot(value.snapshot.progress.request.files.map(_.path).contains) &&
    value.baselines.forall(file => file != null && file.indexCode == " " && file.worktreeCode == " " &&
      file.unexamined.isEmpty && file.original.nonEmpty && file.original == file.indexed && file.indexed == file.worktree &&
      file.worktree.exists(text => !text.contains('\u0000') && java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(text))) &&
    scope.additions == value.files.filterNot(entry => review.changedPaths.contains(entry.path)) &&
    value.snapshot.progress.candidate.exists(candidate => candidate.parent == review.localTip &&
      candidate.parent == review.remoteTip && candidate.tree == review.indexTree) &&
    value.snapshot.progress.receipts.drop(1) == review.checks &&
    value.snapshot.progress.selection.exists(_.checks.map(_.identity) == review.selectedChecks) &&
    RepositorySyncPurposeRuntime.laterRepairOverlap(value.snapshot.progress, scope.paths).isEmpty

  def qualifyC(source: R.Source, paths: Vector[String], rationale: String,
    failureReference: ContextReference): Consequence[Scope] =
    if (source.basis.validated.payload.purposes.nonEmpty) _qualify_purpose_c(source, paths, rationale, failureReference)
    else for {
    _ <- if (source.session.context.syncMode == "FULL_STATE" && source.basis.validationFailure.nonEmpty &&
        source.basis.findings.isEmpty && source.basis.validated.payload.purposes.isEmpty && paths != null &&
        paths.nonEmpty && paths == paths.distinct.sorted &&
        paths.forall(RepositorySyncNativeConflictInput.relative) && rationale != null && rationale.trim.nonEmpty)
      Consequence.unit else N.invalid("automatic inventory requires concrete sorted existing paths, causal rationale and the same FULL_STATE failed check")
    _ <- RepositorySyncNativeMergeValidation.preconditionsC(source.store, source.basis.validated)
    tree <- GitObjectId.parseC(source.basis.input.indexTree)
    workspace <- WorkspaceId.parseC("sm-workflow-repository")
    selectionpaths = (source.basis.input.changedPaths ++ paths).distinct.sorted
    entries <- paths.foldLeft(Consequence.success(Vector.empty[Entry])) { case (prior, name) => for {
      values <- prior
      selection <- GitSelectionId.parseC("conflict-" + (selectionpaths.indexOf(name) + 1))
      rows <- WorkflowManagedProcessExecution.executeGitC(source.store.core, GitOperation.TreeEntries(tree, selection)).flatMap(_.valueC)
      _ <- if (rows.map(_.path.value) == Vector(name)) Consequence.unit
        else N.invalid("automatic inventory tree read exceeded the exact requested path: " + name)
      entry <- rows.head.entry.filter(value => Set("100644", "100755")(value.mode))
        .map(Consequence.success).getOrElse(N.invalid("automatic inventory requires an existing tracked regular candidate file: " + name))
      blob <- WorkflowManagedProcessExecution.executeGitC(source.store.core, GitOperation.BlobContent(entry.blob)).flatMap(_.valueC)
      _ <- if (blob.text.exists(text => !text.contains('\u0000') &&
          text.getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector == blob.bytes)) Consequence.unit
        else N.invalid("automatic inventory candidate must contain complete ordinary UTF-8 text: " + name)
      path <- WorkspaceFilePath.parseC(name)
      actual <- WorkflowFileExecution.readTextC(source.store.core, FileTarget(workspace), path)
      _ <- if (actual.text.exists(text => !text.contains('\u0000') &&
          java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(text)) && actual.text == blob.text) Consequence.unit
        else N.invalid("automatic inventory workspace must be the existing ordinary candidate file: " + name)
    } yield values :+ Entry(name, entry.mode, entry.blob.value) }
    scope = Scope(policy, source.store.identity, source.basis.next.revision.value.toString,
      source.basis.input.validatedRevision, failureReference, source.basis.validationFailure.get,
      source.basis.input.localTip, source.basis.input.remoteTip, source.basis.input.indexTree, paths, rationale,
      entries.filterNot(entry => source.basis.input.changedPaths.contains(entry.path)))
    _ <- if (valid(scope, source.basis.input, source.basis.validationFailure)) Consequence.unit
      else N.invalid("automatic inventory does not bind the actual failed candidate")
    _ <- RepositorySyncNativeMergeValidation.preconditionsC(source.store, source.basis.validated)
  } yield scope

  private def _qualify_purpose_c(source: R.Source, paths: Vector[String], rationale: String,
    failureReference: ContextReference): Consequence[Scope] = for {
    _ <- if (source.session.context.syncMode == "FULL_STATE" && source.basis.validationFailure.nonEmpty &&
        source.basis.findings.isEmpty && paths != null && paths.nonEmpty && paths == paths.distinct.sorted &&
        paths.forall(RepositorySyncNativeConflictInput.relative) && rationale != null && rationale.trim.nonEmpty &&
        !rationale.contains('\u0000') && java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(rationale))
      Consequence.unit else N.invalid("Purpose repair inventory requires sorted safe existing paths and the same FULL_STATE failed check")
    snapshot <- RepositorySyncPurposeRuntime.repairSnapshotC(source.store, source.basis.validated)
    _ <- RepositorySyncPurposeRuntime.noLaterRepairOverlapC(snapshot.progress, paths)
    candidate = snapshot.progress.candidate.get
    tree <- GitObjectId.parseC(candidate.tree)
    workspace <- WorkspaceId.parseC("sm-workflow-repository")
    captured <- paths.zipWithIndex.foldLeft(Consequence.success(Vector.empty[(Entry, String)])) { case (prior, (name, ordinal)) => for {
      values <- prior
      path <- GitPath.parseC(name)
      id <- GitSelectionId.parseC("conflict-" + (ordinal + 1))
      rows <- WorkflowManagedProcessExecution.executePurposeC(source.store.core, GitOperation.TreeEntries(tree, id),
        GitSelectionParameters(id, Vector(path))).flatMap(_.valueC)
      _ <- if (rows.map(_.path.value) == Vector(name)) Consequence.unit else N.invalid("Purpose inventory tree read crossed selected path: " + name)
      entry <- rows.head.entry.filter(value => Set("100644", "100755")(value.mode)).map(Consequence.success)
        .getOrElse(N.invalid("Purpose inventory requires an existing tracked regular candidate file: " + name))
      blob <- WorkflowManagedProcessExecution.executeGitC(source.store.core, GitOperation.BlobContent(entry.blob)).flatMap(_.valueC)
      text <- blob.text.filter(text => !text.contains('\u0000') &&
        java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(text) && text.getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector == blob.bytes)
        .map(Consequence.success).getOrElse(N.invalid("Purpose inventory requires complete ordinary UTF-8 candidate text: " + name))
      _ <- if (source.basis.input.changedPaths.contains(name)) Consequence.unit else for {
        file <- WorkspaceFilePath.parseC(name)
        actual <- WorkflowFileExecution.readTextC(source.store.core, FileTarget(workspace), file)
        _ <- if (actual.text.contains(text)) Consequence.unit
          else N.invalid("Purpose repair added baseline differs from the actual original worktree: " + name)
      } yield ()
    } yield values :+ (Entry(name, entry.mode, entry.blob.value) -> text) }
    baselines = captured.filterNot(value => snapshot.progress.request.files.exists(_.path == value._1.path)).map { (entry, text) =>
      RepositorySyncPurpose.File(entry.path, " ", " ", Some(text), Some(text), Some(text), None) }
    scope = Scope(purposePolicy, source.store.identity, source.basis.next.revision.value.toString,
      source.basis.input.validatedRevision, failureReference, source.basis.validationFailure.get,
      source.basis.input.localTip, source.basis.input.remoteTip, candidate.tree, paths, rationale,
      captured.map(_._1).filterNot(entry => source.basis.input.changedPaths.contains(entry.path)),
      Some(Purpose(snapshot, captured.map(_._1), baselines)))
    _ <- if (valid(scope, source.basis.input, source.basis.validationFailure)) Consequence.unit
      else N.invalid("Purpose repair inventory does not bind the actual failed progress and candidate")
    actual <- RepositorySyncPurposeRuntime.repairSnapshotC(source.store, source.basis.validated)
    _ <- if (actual == snapshot) Consequence.unit else N.invalid("Purpose repair source drifted during qualification")
  } yield scope

  /** Ordinary Advance recaptures candidate text, then repeats qualification;
    * neither preparation nor lifecycle adoption writes repository content. */
  def capturePurposeC(source: R.Source, scope: Scope): Consequence[Vector[RepositorySyncNativeMergeRepair.File]] = for {
    current <- qualifyC(source, scope.paths, scope.rationale, scope.failureReference)
    _ <- if (current == scope) Consequence.unit else N.invalid("Purpose repair inventory or original worktree has drifted")
    files <- scope.purpose.get.files.foldLeft(Consequence.success(Vector.empty[RepositorySyncNativeMergeRepair.File])) { (prior, entry) => for {
      values <- prior
      oid <- GitObjectId.parseC(entry.blob)
      blob <- WorkflowManagedProcessExecution.executeGitC(source.store.core, GitOperation.BlobContent(oid)).flatMap(_.valueC)
      text <- blob.text.map(Consequence.success).getOrElse(N.invalid("Qualified Purpose candidate text is unavailable: " + entry.path))
    } yield values :+ RepositorySyncNativeMergeRepair.File(entry.path, text) }
    after <- qualifyC(source, scope.paths, scope.rationale, scope.failureReference)
    _ <- if (after == scope) Consequence.unit else N.invalid("Purpose candidate drifted while capturing assigned text")
  } yield files
}
