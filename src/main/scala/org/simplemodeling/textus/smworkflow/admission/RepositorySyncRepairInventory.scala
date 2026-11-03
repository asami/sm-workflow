package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
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
  final case class Entry(path: String, mode: String, blob: String)
  final case class Scope(inventoryPolicy: String, instanceIdentity: String, sourceRevision: String,
    validatedRevision: String, failureReference: ContextReference, failureIdentity: String,
    localTip: String, remoteTip: String, candidateTree: String, paths: Vector[String], rationale: String,
    additions: Vector[Entry])
  given Encoder[Entry] = deriveEncoder
  given Decoder[Entry] = deriveDecoder
  given Encoder[Scope] = deriveEncoder
  given Decoder[Scope] = deriveDecoder

  def valid(scope: Scope, review: V.Request, failure: Option[String]): Boolean = scope != null &&
    review != null && review.validatedRevision != null && review.changedPaths != null &&
    review.checks != null && review.selectedChecks != null &&
    failure != null && failure.contains(scope.failureIdentity) && scope.inventoryPolicy == policy &&
    scope.instanceIdentity == review.instanceIdentity && scope.validatedRevision == review.validatedRevision &&
    scope.sourceRevision != null && scope.sourceRevision.toLongOption.exists(revision =>
      review.validatedRevision.toLongOption.exists(_ < revision)) &&
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

  def qualifyC(source: R.Source, paths: Vector[String], rationale: String,
    failureReference: ContextReference): Consequence[Scope] = for {
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
}
