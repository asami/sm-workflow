package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import RepositorySyncNativeProgressStore as N
import RepositorySyncPurpose as P
import RepositorySyncPurposeContentContinuation as H
import RepositorySyncPurposeRuntime.attemptPrefix

/** Resolve the preceding candidate from this Purpose's actual native history.
  * @since Nov. 21, 2026
  * @version Nov. 21, 2026
  */
private[smworkflow] object RepositorySyncPurposeCandidateLineage {
  def priorC(store: N.Store, source: N.Stage, progress: P.Progress): Consequence[Option[P.Candidate]] =
    if (progress.attempt == 0 && progress.baselineTree.isEmpty) Consequence.success(None)
    else for {
      _ <- if (source.record.identity.value == store.identity && progress.attempt > 0 && progress.baselineTree.nonEmpty &&
          source.payload.purposes.contains(progress) && progress.candidate.isEmpty) Consequence.unit
        else N.invalid("Purpose preparation has no applicable repair lineage")
      record = source.record
      prefix = "native-repository:" + store.identity + ":"
      admittedkey = "purpose-" + (progress.cursor + 1) + "-repair-" + progress.attempt + "-admitted"
      admittedentry <- _entry_c(record, prefix + store.key(admittedkey))
      admitted <- _load_c(store, record, admittedkey, admittedentry, "PurposeGrouped")
      previous <- record.history.reverseIterator.find(entry => entry.revision.value < admittedentry.revision.value &&
          entry.suspension.isEmpty && entry.currentProgression.contains(ProgressionReference("sync.PurposePrepared")) &&
          entry.causation.value.startsWith(prefix) && entry.causation.value.endsWith("-prepared"))
        .map(Consequence.success).getOrElse(N.invalid("Purpose repair has no actual preceding prepared candidate"))
      preparedkey = previous.causation.value.stripPrefix(prefix)
      prepared <- _load_c(store.scoped(""), record, preparedkey, previous, "PurposePrepared")
      prior <- _progress_c(prepared)
      _ <- if (preparedkey == store.key(attemptPrefix(prior) + "-prepared")) Consequence.unit
        else N.invalid("Purpose candidate was prepared in another attempt or scope")
      intentkey = attemptPrefix(prior) + "-prepare-intent"
      intententry <- _entry_c(record, prefix + store.key(intentkey))
      intent <- _load_c(store, record, intentkey, intententry, "PurposePreparing")
      _ <- if (prepared.record.revision == intent.record.revision.next &&
          prepared.record.history.dropRight(1) == intent.record.history) Consequence.unit
        else N.invalid("Purpose candidate preparation has no adjacent native intent")
      started <- _progress_c(intent)
      accepted <- _progress_c(admitted)
      candidate <- qualifyC(progress, accepted, started, prior)
    } yield Some(candidate)

  private def _entry_c(record: InstanceRecord, cause: String): Consequence[HistoryEntry] =
    record.history.filter(_.causation.value == cause) match {
      case Vector(entry) => Consequence.success(entry)
      case _ => N.invalid("Purpose repair requires one exact native history entry: " + cause)
    }

  private def _load_c(store: N.Store, record: InstanceRecord, key: String,
    entry: HistoryEntry, stage: String): Consequence[N.Stage] = for {
    expected <- H.prefixC(record, entry.revision.value.toString)
    retained <- store.loadC(key).flatMap(_.map(Consequence.success)
      .getOrElse(N.invalid("Purpose repair history receipt is absent: " + key)))
    _ <- if (retained.record == expected && retained.record.suspension.isEmpty && retained.payload.stage == stage)
      Consequence.unit else N.invalid("Purpose repair history crosses its immutable native stage: " + key)
  } yield retained

  private def _progress_c(stage: N.Stage): Consequence[P.Progress] =
    stage.payload.purposes.map(Consequence.success).getOrElse(N.invalid("Purpose repair history has no retained progress"))

  private[admission] def qualifyC(current: P.Progress, admitted: P.Progress,
    intent: P.Progress, prepared: P.Progress): Consequence[P.Candidate] = for {
    candidate <- prepared.candidate.map(Consequence.success).getOrElse(N.invalid("Purpose preparation has no candidate"))
    oldgroup <- prepared.plan.flatMap(_.groups.lift(prepared.cursor)).map(Consequence.success)
      .getOrElse(N.invalid("Previous candidate has no retained Purpose group"))
    newgroup <- admitted.plan.flatMap(_.groups.lift(admitted.cursor)).map(Consequence.success)
      .getOrElse(N.invalid("Admitted repair has no retained Purpose group"))
    _ <- if (current.copy(preparationNamespace = admitted.preparationNamespace, effects = admitted.effects) == admitted &&
        current.effects.startsWith(admitted.effects) &&
        prepared.copy(candidate = None, effects = intent.effects) == intent &&
        prepared.effects.size == intent.effects.size + 1 && prepared.effects.startsWith(intent.effects) &&
        _succeeded(prepared.effects.last) &&
        admitted.effects.startsWith(prepared.effects) &&
        admitted.attempt == prepared.attempt + 1 && admitted.cursor == prepared.cursor &&
        admitted.slotCursor == prepared.slotCursor + 1 && admitted.commits == prepared.commits &&
        admitted.baselineTree.contains(candidate.tree) && candidate.baselineTree == prepared.baselineTree &&
        admitted.request.copy(files = prepared.request.files) == prepared.request &&
        admitted.request.files.startsWith(prepared.request.files) && oldgroup.identity == newgroup.identity &&
        candidate.message == oldgroup.message && oldgroup.message == newgroup.message &&
        candidate.changes == RepositorySyncPurposeOwnership.effectiveGroup(prepared, oldgroup).content &&
        candidate.expected == candidate.changes.flatMap(content => RepositorySyncPurposeOwnership.expected(prepared, content.path)))
      Consequence.unit else N.invalid("Previous Purpose candidate does not match the accepted repair and its prepared source")
  } yield candidate

  private def _succeeded(effect: io.circe.Json): Boolean =
    effect.hcursor.get[String]("outcome").toOption.contains("Succeeded") &&
      effect.hcursor.downField("process").focus.flatMap(WorkflowSbtCapture.decode(_).toOption)
        .exists(process => WorkflowSbtCapture.exitCode(process).contains(0))
}
