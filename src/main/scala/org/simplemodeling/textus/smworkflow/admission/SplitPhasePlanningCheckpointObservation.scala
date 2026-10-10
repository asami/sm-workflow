package org.simplemodeling.textus.smworkflow.admission

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePlanningCheckpointPreparation as K,
  SplitPhaseTypedStartPreparation as T, SplitPhaseExplicitPartitionContext as C,
  SplitPhasePlanningSnapshot as R, SplitPhaseExplicitProposalPrecheck as P,
  SplitPhaseExplicitBoundaryValidation as B}

/** Selected native address relations, not complete saved-copy certificates.
  * Unit is not a native witness or an
  * executable plan; native receipt/prefix admission stays with CNCF.
  * @since   Oct.  2, 2026
  * @version Oct. 11, 2026
  */
private[smworkflow] object SplitPhasePlanningCheckpointObservation {
  final case class Original(context: C.Record, start: T.Routing, facts: C.Facts)
  object Original {
    def apply(context: C.Record, start: T.Record, facts: C.Facts): Original =
      new Original(context, start.routing, facts)
  }
  final case class Documents(proposal: P.ProposalRecord, ownership: B.Ownership, handoffs: B.HandoffPlan)
  final case class Data(index: K.ContextIndex, mapping: K.Record, original: Original,
    effective: C.Facts, documents: Vector[Documents])

  /** Shared raw Start ancestry, also for the later suspended AI boundary. This
    * does not construct a prefix instance, prepare a Start or admit execution.
    */
  def verifyStartLineageC(original: Original, predecessor: InstanceRecord): Consequence[Unit] = guarded {
    val context = original.context
    val start = original.start
    val handle = context.owner.handle
    val first = predecessor.history.headOption
    if (start.instanceIdentity == predecessor.identity &&
        handle.instanceIdentity == predecessor.identity && handle.componentIdentity == SmWorkflowComponent.componentId &&
        handle.workflowIdentity == predecessor.definition.workflowIdentity &&
        handle.workflowRevision == predecessor.definition.workflowRevision &&
        context.owner.instanceRevision.value <= predecessor.revision.value &&
        predecessor.correlation == CorrelationReference(start.command.selectionIdentity) && first.exists(h =>
          h.sequence == HistorySequence(1) && h.revision == InstanceRevision(1) && h.lifecycle == Lifecycle.Active &&
          h.currentProgression == Some(ProgressionReference("entry.EntryAssessment")) &&
          h.correlation == CorrelationReference(start.command.selectionIdentity) &&
          h.causation == CausationReference(start.reference.identity) && h.derivedCompositeOccurrence.isEmpty &&
          h.committedPredecessor.isEmpty && h.suspension.isEmpty)) Consequence.unit
    else invalid("original Start/context/native predecessor differ")
  }

  def builtDocuments(snapshot: R.Record): Vector[Documents] =
    snapshot.projection.toVector.flatMap(_.candidates).collect {
      case value: R.CandidateProjectionView.BuiltView => Documents(value.proposal, value.ownership, value.handoffs)
    }

  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("SplitPhase checkpoint observation " + reason)
  private def guarded[A](body: => Consequence[A]): Consequence[A] = try body catch {
    case NonFatal(_) => invalid("complete data is absent or malformed")
  }
}
