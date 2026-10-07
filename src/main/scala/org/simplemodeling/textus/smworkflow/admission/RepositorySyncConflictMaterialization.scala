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
    intent <- store.loadC("conflict-materialization")
    pending <- store.loadC("pending-merge")
    selected <- pending match {
      case Some(value) => for {
        _ <- if (value.payload.materialization.contains(P.encode(request)) && prefix(current, value.record)) Consequence.unit
          else N.invalid("pending merge crosses its actual selected proposal")
        actual <- G.inspectC(store.core)
        tree <- G.indexTreeC(store.core)
        _ <- if (value.payload.after.contains(actual.observed) && value.payload.mergeHead == actual.mergeHead &&
            value.payload.indexTree.contains(tree)) Consequence.unit else N.invalid("pending resolved merge changed")
      } yield value
      case None => for {
        original <- intent match {
          case Some(value) if value.payload.materialization.contains(P.encode(request)) && prefix(current, value.record) => Consequence.success(value)
          case Some(_) => N.invalid("materialization intent crosses the selected native proposal")
          case None => for {
            _ <- if (current == proposal) Consequence.unit else N.invalid("proposal is not the actual current native successor")
            before <- verifyIndexC(store, input, result, allowStaged = false)
            observation <- filesC(store, request, E.Purpose.ObserveConflictFiles)
            _ <- if (Set("ready", "applied", "partial")(observation.status)) Consequence.unit
              else N.invalid("selected conflict text has an intervening edit; Human handling required")
            saved <- store.saveC("conflict-materialization", "conflict-materialization-prepared", proposal,
              store.payload("MaterializingConflict", before.observed).copy(mergeHead = before.mergeHead,
                materialization = Some(P.encode(request))))
            _ <- store.acknowledgeC("conflict-materializing")
          } yield saved
        }
        _ <- verifyIndexC(store, input, result, allowStaged = true)
        observed <- filesC(store, request, E.Purpose.ObserveConflictFiles)
        applied <- observed.status match {
          case "applied" => Consequence.success(observed)
          case "ready" | "partial" => filesC(store, request, E.Purpose.ApplyConflictFiles)
          case _ => N.invalid("materialization observes an intervening edit; Human handling required")
        }
        _ <- if (applied.status == "applied") Consequence.unit else N.uncertain("file application is not established; observe on later advance")
        _ <- store.acknowledgeC("conflict-files-applied")
        _ <- input.conflicts.zip(result.resolutions).foldLeft(Consequence.unit) { case (z, (conflict, resolution)) => z.flatMap { _ => for {
          rows <- entriesC(store)
          _ <- verifyIndexC(store, input, result, allowStaged = true)
          _ <- if (rows.filter(_.path == conflict.path).forall(_.stage == 0)) Consequence.unit
            else E.stageConflictC(store.core, policy.semanticPaths.toVector.sorted.indexOf(conflict.path) + 1)
              .flatMap(G.requiredTextC).map(_ => ())
          _ <- store.acknowledgeC("conflict-path-staged")
        } yield () }}
        after <- verifyIndexC(store, input, result, allowStaged = true)
        rows <- entriesC(store)
        _ <- if (rows.filter(r => input.conflicts.exists(_.path == r.path)).forall(_.stage == 0) &&
            !after.observed.dirty.exists(_.unmerged)) Consequence.unit else N.invalid("resolved index remains unmerged")
        tree <- G.indexTreeC(store.core)
        saved <- store.saveC("pending-merge", "merge-resolution-observed", original.record,
          store.payload("PendingMergePrepared", original.payload.before).copy(indexTree = Some(tree),
            after = Some(after.observed), mergeHead = after.mergeHead, method = Some("resolved-conflict"),
            materialization = Some(P.encode(request))))
        _ <- store.acknowledgeC("conflict-materialized")
      } yield saved
    }
  } yield selected

  private def filesC(store: N.Store, request: P.Request, purpose: E.Purpose): Consequence[P.Result] = for {
    wire <- E.executeC(store.core, purpose, P.encode(request)).flatMap(G.requiredTextC)
    result <- parser.parse(wire).left.map(_.message).flatMap(P.decodeResult) match {
      case Right(value) if value.instance == request.instance && value.revision == request.revision &&
          value.files == request.files && Set("ready", "partial", "applied", "conflict")(value.status) => Consequence.success(value)
      case _ => N.invalid("managed file observation crosses the assigned application intent")
    }
  } yield result
  private def entriesC(store: N.Store): Consequence[Vector[I.Entry]] =
    E.executeC(store.core, E.Purpose.GitIndexEntries, "").flatMap(G.requiredTextC).flatMap(I.parseAllC)
  private def verifyIndexC(store: N.Store, input: I.Request, result: S.Result, allowStaged: Boolean): Consequence[G.Native] = for {
    native <- G.inspectC(store.core)
    _ <- store.scopeC(native.observed)
    _ <- if (native.observed.head == input.localTip && native.mergeHead.contains(input.remoteTip) &&
        native.observed.trackingTip.contains(input.remoteTip)) Consequence.unit
      else N.invalid("selected HEAD/tracking/MERGE_HEAD changed before materialization")
    original <- store.loadC("merge-conflicted").flatMap {
      case Some(value) => Consequence.success(value)
      case None => N.invalid("materialization has no actual original native conflict")
    }
    paths = input.conflicts.map(_.path).toSet
    _ <- if (original.payload.after.exists(before =>
        before.dirty.filterNot(p => paths(p.path)) == native.observed.dirty.filterNot(p => paths(p.path)))) Consequence.unit
      else N.invalid("unassigned native repository changes require Human handling")
    rows <- entriesC(store)
    _ <- input.conflicts.zip(result.resolutions).foldLeft(Consequence.unit) { case (z, (conflict, resolution)) => z.flatMap { _ =>
      val selected = rows.filter(_.path == conflict.path)
      val expected = Vector(I.Entry(conflict.mode, conflict.base.identity, 1, conflict.path),
        I.Entry(conflict.mode, conflict.local.identity, 2, conflict.path), I.Entry(conflict.mode, conflict.remote.identity, 3, conflict.path))
      if (selected == expected) Consequence.unit
      else selected match {
        case Vector(entry) if allowStaged && entry.stage == 0 && entry.mode == conflict.mode =>
          E.executeNativeGitC(store.core, E.NativeGitEffect.ReadBlob(entry.blob)).flatMap(G.requiredTextC).flatMap(text =>
            if (text == resolution.text) Consequence.unit else N.invalid("staged resolution differs from selected proposal"))
        case _ => N.invalid("actual conflict stages differ from assigned native input")
      }
    }}
    _ <- if (rows.filter(_.stage != 0).forall(r => input.conflicts.exists(_.path == r.path))) Consequence.unit
      else N.invalid("an additional unassigned conflict exists")
  } yield native
  private def prefix(actual: InstanceRecord, selected: InstanceRecord): Boolean = actual.identity == selected.identity &&
    actual.definition == selected.definition && actual.history.size >= selected.history.size &&
    actual.history.take(selected.history.size) == selected.history
}
