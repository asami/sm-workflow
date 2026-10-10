package org.simplemodeling.textus.smworkflow.admission

import scala.collection.mutable
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.value.{RepositorySyncMergeReviewRequest, RepositorySyncMergeReviewResult}

/** Host-owned typed review retention. A saved Result is not commit authority:
  * consumers must re-admit it against the retained candidate and current history.
  * Production durability is supplied by the host, not by the fixture below.
  * @since   Sep. 28, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] trait RepositorySyncMergeReviewReceiptStore {
  import RepositorySyncMergeReviewReceiptStore.Record
  def loadC(reference: ContextReference): Consequence[Option[Record]]
  /** Persist before returning. An occupied reference selects its actual record;
    * later proposals do not replace it or require complete-body equality.
    */
  def putIfAbsentC(record: Record): Consequence[Record]
}

private[smworkflow] object RepositorySyncMergeReviewReceiptStore {
  final case class Record(retained: RepositorySyncMergeReviewServiceOperation.Retained,
    issued: WorkflowInteraction[RepositorySyncMergeReviewRequest, Nothing],
    submitted: ContinuationResult[RepositorySyncMergeReviewResult]) {
    def reference: ContextReference = submitted.resultReference
  }
  final class InMemory extends RepositorySyncMergeReviewReceiptStore {
    private val records = mutable.Map.empty[ContextReference, Record]
    def loadC(reference: ContextReference): Consequence[Option[Record]] = synchronized {
      if (!_reference(reference)) Consequence.stateConflict("RepositorySync review lookup reference is missing")
      else Consequence.success(records.get(reference))
    }
    def putIfAbsentC(record: Record): Consequence[Record] = synchronized {
      if (record == null || record.retained == null || record.issued == null || record.submitted == null ||
          !_reference(record.submitted.resultReference))
        Consequence.stateConflict("RepositorySync review record is incomplete")
      else records.get(record.reference) match {
        case Some(saved) => Consequence.success(saved)
        case None => records.update(record.reference, record); Consequence.success(record)
      }
    }
  }
  private def _reference(value: ContextReference): Boolean = value != null &&
    value.identity != null && value.identity.trim.nonEmpty && value.revision != null && value.revision.trim.nonEmpty
}
