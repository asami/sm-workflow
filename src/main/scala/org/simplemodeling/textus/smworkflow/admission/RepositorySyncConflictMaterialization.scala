package org.simplemodeling.textus.smworkflow.admission

import io.circe.parser
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord
import org.simplemodeling.textus.smworkflow.runtime.RepositoryConflictFileProgram as P
import RepositorySyncNativeConflictInput as I
import RepositorySyncConflictSemantic as S
import RepositorySyncManagedGitObservation as G
import RepositorySyncNativeProgressStore as N
import WorkflowManagedProcessExecution as E

/** Genuine saved proposal -> intent -> managed files -> fixed path staging.
  * No command receipt supplied by AI and no file/Git/DB atomicity claim.
  * @since   Oct.  6, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositorySyncConflictMaterialization {
  def advanceC(store: N.Store, input: I.Request, result: S.Result, current: InstanceRecord,
    proposal: InstanceRecord, policy: I.Policy): Consequence[N.Stage] = for {
    _ <- S.admitC(input, result, policy)
    _ <- if (result.decision == "resolved") Consequence.unit else N.invalid("Human decision cannot materialize a proposal")
    request = P.Request(input.instanceIdentity, input.conflictRevision,
      input.conflicts.zip(result.resolutions).map((c, r) => P.File(c.path, Some(c.worktree), r.text)))
    pending <- store.loadC("pending-merge")
    selected <- pending match {
      case Some(value) => Consequence.success(value)
      case None => for {
        before <- G.inspectC(store.core)
        _ <- store.scopeC(before.observed)
        original <- store.saveC("conflict-materialization", "conflict-materialization-prepared", proposal,
          store.payload("MaterializingConflict", before.observed).copy(mergeHead = before.mergeHead))
        applied <- filesC(store, request, E.Purpose.ApplyConflictFiles)
        _ <- if (applied.status == "applied") Consequence.unit
          else N.invalid("selected conflict text has an intervening edit; Human handling required")
        _ <- input.conflicts.foldLeft(Consequence.unit) { (z, conflict) => z.flatMap { _ =>
          E.stageConflictC(store.core, policy.semanticPaths.toVector.sorted.indexOf(conflict.path) + 1)
            .flatMap(G.requiredTextC).map(_ => ())
        }}
        after <- G.inspectC(store.core)
        tree <- G.indexTreeC(store.core)
        saved <- store.saveC("pending-merge", "merge-resolution-observed", original.record,
          store.payload("PendingMergePrepared", original.payload.before).copy(indexTree = Some(tree),
            after = Some(after.observed), mergeHead = after.mergeHead, method = Some("resolved-conflict")))
      } yield saved
    }
  } yield selected

  private def filesC(store: N.Store, request: P.Request, purpose: E.Purpose): Consequence[P.Result] = for {
    wire <- E.executeC(store.core, purpose, P.encode(request)).flatMap(G.requiredTextC)
    result <- parser.parse(wire).left.map(_.message).flatMap(P.decodeResult) match {
      case Right(value) => Consequence.success(value)
      case _ => N.invalid("managed file observation crosses the assigned application intent")
    }
  } yield result
}
