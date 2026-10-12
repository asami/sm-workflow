package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePartitionCompletionCheckpointPreparation as A,
  SplitPhasePartitionCompletionCheckpointJsonV1 as J, SplitPhasePlanningCheckpointPublication as U,
  SplitPhasePartitionEnrichmentJsonV1 as E}

/**
 * Complete publication data. Does not stage, admit or commit any row.
 * @since   Oct.  2, 2026
 * @version Oct. 12, 2026
 */
private[smworkflow] object SplitPhasePartitionCompletionCheckpointPublication {
  def prepareRowsC(captured: A.Captured, targets: U.Targets): Consequence[Vector[U.Row]] =
    if (captured == null || targets == null) Consequence.stateConflict("SplitPhase AI publication is incomplete")
    else for {
      raw <- J.encodeC(captured.record)
      key <- A.lineageKeyC(captured.continuationId)
      header <- captured.record.header.fold(Consequence.success(Vector.empty[U.Row])) { h =>
        E.encodeC(h).map(bytes => Vector(U.Row(targets.publication, h.result.identity, Record.data(E.storageField -> bytes))))
      }
      closing = header :+ U.Row(targets.publication, key, Record.data(J.storageField -> raw))
      rows <- captured.checkpoint match {
        case Some(k) => U.prepareC(k, targets, closing).map(_.rows)
        case None => Consequence.success(closing)
      }
    } yield rows
}
