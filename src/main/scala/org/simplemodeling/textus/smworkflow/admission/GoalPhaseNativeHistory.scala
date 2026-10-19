package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.ContextReference

/** Read-only projection of contiguous actual native history.
  * @since   Oct. 17, 2026
  * @version Oct. 17, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseNativeHistory {
  def prefixC(current: InstanceRecord, revision: InstanceRevision): Consequence[InstanceRecord] = for {
    _ <- _record_c(current)
    selected <- current.history.filter(_.revision == revision) match {
      case Vector(entry) => Consequence.success(entry)
      case _ => _invalid("selected revision is missing or ambiguous")
    }
    prefix <- current.copy(revision = selected.revision, lifecycle = selected.lifecycle,
      currentProgression = selected.currentProgression, correlation = selected.correlation,
      causation = selected.causation, derivedCompositeOccurrence = selected.derivedCompositeOccurrence,
      committedPredecessor = selected.committedPredecessor, suspension = selected.suspension,
      history = current.history.takeWhile(_.revision.value <= selected.revision.value)).validateC
  } yield prefix

  def nextC(current: InstanceRecord, from: InstanceRecord): Consequence[Option[HistoryEntry]] = for {
    _ <- _record_c(current)
    _ <- _record_c(from)
    _ <- if (current.identity == from.identity &&
        current.definition.workflowIdentity == from.definition.workflowIdentity &&
        current.definition.workflowRevision == from.definition.workflowRevision) Consequence.unit
      else _invalid("source crosses its instance or definition")
    admitted <- prefixC(current, from.revision)
    _ <- if (admitted.currentProgression == from.currentProgression && admitted.lifecycle == from.lifecycle &&
        admitted.suspension == from.suspension) Consequence.unit
      else _invalid("source is not its actual native frontier")
  } yield current.history.drop(admitted.history.size).headOption

  def completionC(current: InstanceRecord, pending: InstanceRecord, reference: ContextReference,
    stage: String): Consequence[InstanceRecord] = for {
    following <- nextC(current, pending)
    entry <- following match {
      case Some(value) if reference != null && value.revision == pending.revision.next &&
          value.causation.value == reference.identity && value.suspension.isEmpty &&
          value.currentProgression.contains(ProgressionReference("phase." + stage)) => Consequence.success(value)
      case _ => _invalid("paired completion has no exact actual successor")
    }
    completed <- prefixC(current, entry.revision)
  } yield completed

  private def _record_c(value: InstanceRecord): Consequence[InstanceRecord] =
    if (value == null) _invalid("native record is absent") else value.validateC
  private def _invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("GoalPhase native history: " + reason)
}
