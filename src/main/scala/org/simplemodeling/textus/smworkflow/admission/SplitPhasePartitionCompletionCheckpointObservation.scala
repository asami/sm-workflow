package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePartitionCompletionCheckpointPreparation as A,
  SplitPhasePlanningCheckpointObservation as O, SplitPhaseExplicitPartitionSemantic as S,
  SplitPhasePartitionEnrichmentPublication as E}

/**
 * Whole inert data comparisons only; Unit is not native completion evidence.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object SplitPhasePartitionCompletionCheckpointObservation {
  def verifyC(saved: A.Record, original: O.Original, checkpoint: Option[O.Data], header: Option[E.Record],
    issued: WorkflowInteraction[S.Request, Nothing], completion: WorkflowCompletionRecordJsonV1.Record[S.Judgment],
    current: InstanceRecord): Consequence[Unit] = for {
    _ <- A.inspectC(saved)
    _ <- org.goldenport.cncf.workflow.WorkflowInstancePersistence.validateC(current)
    _ <- O.verifyStartLineageC(original, saved.before)
    _ <- if (saved.context == original.context && saved.start == original.start && original.facts == saved.context.facts &&
        saved.issued == issued && saved.completion == completion && saved.header == header &&
        current.identity == saved.next.identity && SplitPhaseDefinitionBinding.sameDefinition(current.definition, saved.next.definition) &&
        current.history.take(saved.before.history.size) == saved.before.history &&
        current.history.take(saved.next.history.size) == saved.next.history && current.revision.value >= saved.next.revision.value)
      Consequence.unit else _invalid("independent original/result/history prefix differ")
    _ <- (saved.checkpoint, checkpoint) match {
      case (None, None) => Consequence.unit
      case (Some(k), Some(data)) if data.mapping == k && data.index == SplitPhasePlanningCheckpointPreparation.index(k) &&
          data.original == original && data.effective == k.snapshot.selection && data.documents == O.builtDocuments(k.snapshot) => Consequence.unit
      case _ => _invalid("complete independent checkpoint facts or Built documents differ")
    }
  } yield ()
  private def _invalid[A](why: String): Consequence[A] = Consequence.stateConflict("SplitPhase AI observation " + why)
}
