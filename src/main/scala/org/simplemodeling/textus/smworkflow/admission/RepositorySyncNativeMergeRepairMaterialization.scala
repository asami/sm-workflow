package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord
import RepositorySyncFileEffect as P
import RepositorySyncNativeConflictInput as I
import RepositorySyncNativeMergeRepair as M
import RepositorySyncNativeProgressStore as N
import RepositorySyncManagedGitObservation as G
import WorkflowManagedProcessExecution as E

/** Managed intent boundaries surround fixed file and ordinal stage effects.
  * Retained uncertainty never replays an apply/stage effect or rolls back.
  * @since   Oct. 15, 2026
  * @version Oct. 15, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object RepositorySyncNativeMergeRepairMaterialization {
  def advanceC(store: N.Store, review: RepositorySyncNativeMergeReviewContinuation.SavedReview,
    input: M.Request, result: M.Result, proposal: InstanceRecord, policy: I.Policy): Consequence[N.Stage] = for {
    _ <- M.admitC(input, result, policy)
    _ <- if (result.decision == "repaired") Consequence.unit else N.invalid("decision-needed cannot materialize repair")
    namespace = "repair-" + review.next.revision.value
    request = P.Request(store.identity, review.next.revision.value.toString,
      input.files.zip(result.replacements).map((assigned, replacement) => P.File(assigned.path, Some(assigned.text), replacement.text)))
    pending <- store.loadC(N.candidateKey(namespace, "pending-merge"))
    selected <- pending match {
      case Some(value) => Consequence.success(value)
      case None => for {
        existing <- store.loadC(N.candidateKey(namespace, "materialization-intent"))
        intent <- existing match {
          case Some(value) => for {
            _ <- _parents_c(store, review.validated)
            observed <- P.observeC(store.core, policy, request)
            _ <- if (observed.status == "applied") Consequence.unit
              else N.uncertain("retained repair intent has partial, ready or conflicting files; no apply replay")
          } yield value
          case None => for {
            _ <- RepositorySyncNativeMergeValidation.preconditionsC(store, review.validated)
            observed <- P.observeC(store.core, policy, request)
            _ <- if (observed.status == "ready") Consequence.unit
              else N.invalid("repair expected texts changed before the first application intent")
            intent <- store.saveC(N.candidateKey(namespace, "materialization-intent"), "native-repair-materialization-prepared",
              proposal, review.validated.payload.copy(stage = "MaterializingRepair", validation = None))
            _ <- store.acknowledgeC("repair-materialization-prepared")
            _ <- RepositorySyncNativeMergeValidation.preconditionsC(store, review.validated)
            applied <- P.applyC(store.core, policy, request)
            _ <- if (applied.status == "applied") Consequence.unit
              else N.invalid("repair file application has an intervening edit; no replay or rollback")
            _ <- store.acknowledgeC("repair-files-applied")
          } yield intent
        }
        staged <- input.files.zipWithIndex.foldLeft(Consequence.success(intent)) { case (prior, (file, index)) => prior.flatMap { current => for {
          recorded <- store.loadC(N.candidateKey(namespace, "stage-result-" + index))
          selected <- recorded match {
            case Some(value) => Consequence.success(value)
            case None => for {
              retained <- store.loadC(N.candidateKey(namespace, "stage-intent-" + index))
              _ <- if (retained.isEmpty) Consequence.unit else N.uncertain("retained repair stage intent lacks its result; no stage replay")
              _ <- _parents_c(store, review.validated)
              observed <- P.observeC(store.core, policy, request)
              _ <- if (observed.status == "applied") Consequence.unit else N.invalid("repair replacement text changed before staging")
              prepared <- store.saveC(N.candidateKey(namespace, "stage-intent-" + index), "native-repair-stage-prepared",
                current.record, intent.payload)
              _ <- store.acknowledgeC("repair-stage-prepared:" + file.path)
              _ <- E.stageConflictC(store.core, policy.semanticPaths.toVector.sorted.indexOf(file.path) + 1)
                .flatMap(_.valueC)
              _ <- store.acknowledgeC("repair-stage-executed:" + file.path)
              completed <- store.saveC(N.candidateKey(namespace, "stage-result-" + index), "native-repair-stage-recorded",
                prepared.record, intent.payload)
              _ <- store.acknowledgeC("repair-stage-recorded:" + file.path)
            } yield completed
          }
        } yield selected }}
        after <- _parents_c(store, review.validated)
        _ <- if (after.observed.dirty.forall(path => !path.unmerged && path.worktree == ' ' &&
            path.index != '?' && path.index != '!')) Consequence.unit
          else N.invalid("repaired index has unmerged or unexpected unstaged paths")
        observed <- P.observeC(store.core, policy, request)
        _ <- if (observed.status == "applied") Consequence.unit else N.invalid("repair replacement texts changed after staging")
        tree <- G.indexTreeC(store.core)
        saved <- store.saveC(N.candidateKey(namespace, "pending-merge"), "native-repair-candidate-observed", staged.record,
          review.validated.payload.copy(stage = "PendingMergePrepared", indexTree = Some(tree), after = Some(after.observed),
            validation = None))
        _ <- store.acknowledgeC("repair-candidate-observed")
      } yield saved
    }
  } yield selected

  private def _parents_c(store: N.Store, original: N.Stage): Consequence[G.Native] = for {
    native <- G.inspectC(store.core)
    _ <- store.scopeC(native.observed)
    _ <- if (original.payload.after.exists(after => after.head == native.observed.head &&
        after.tree == native.observed.tree && after.branch == native.observed.branch &&
        after.trackingTip == native.observed.trackingTip) && original.payload.before.head == native.observed.head &&
        original.payload.mergeHead.nonEmpty && original.payload.mergeHead == native.mergeHead &&
        native.observed.trackingTip == native.mergeHead) Consequence.unit
      else N.invalid("repair crosses its actual reviewed pending merge parents/branch/tracking")
  } yield native

}
