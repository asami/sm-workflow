package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.{DataStore, SearchableDataStore, Query, QueryDirective, QueryProjection,
  QueryOrder, OrderDirection, QueryLimit}
import org.goldenport.cncf.directive.{Query as Q}
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.value as V

/** Finite public query over the fixed owning component Journal store.
  * @since   Oct. 20, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object DevelopmentJournalFind {
  val summaryLimit = 280
  val truncationMarker = " [Journal summary truncated]"
  val projectionFields = Vector("id", DevelopmentJournal.storageField, "kind", "repositoryIdentity",
    "phaseIdentity", "sessionIdentity", "sourceIdentity", "sourceRevision", "recordedAt", "revision")

  def validateInputC(input: V.JournalFindInput): Consequence[Unit] = {
    val valid = try {
      input != null && _exact(input.repositoryIdentity) &&
        Vector(input.phaseIdentity, input.sessionIdentity, input.kind, input.recordedFrom, input.recordedBefore)
          .forall(o => o != null && o.forall(_exact)) &&
        input.kind.forall(k => k == DevelopmentJournal.kind || k == DevelopmentJournalFinding.kind ||
          k == DevelopmentJournalEvidence.kind || k == DevelopmentJournalAssessment.kind || k == DevelopmentJournalMergeAssessment.kind) &&
        input.sourceReference != null && input.sourceReference.forall(s => s != null && _exact(s.identity) && _exact(s.revision)) &&
        input.limit != null && input.limit.forall(n => n >= 1 && n <= 20) &&
        input.recordedFrom.forall(_second) && input.recordedBefore.forall(_second) &&
        (input.recordedFrom.isEmpty || input.recordedBefore.isEmpty ||
          Instant.parse(input.recordedFrom.get).isBefore(Instant.parse(input.recordedBefore.get)))
    } catch { case NonFatal(_) => false }
    if (valid) Consequence.unit else Consequence.stateConflict("Journal find input is invalid")
  }

  def directiveC(input: V.JournalFindInput): Consequence[QueryDirective] = validateInputC(input).map { _ =>
    val clauses = Vector[Q.Expr](Q.Eq("repositoryIdentity", input.repositoryIdentity)) ++
      input.phaseIdentity.map(Q.Eq("phaseIdentity", _)) ++ input.sessionIdentity.map(Q.Eq("sessionIdentity", _)) ++
      input.kind.map(Q.Eq("kind", _)) ++ input.sourceReference.toVector.flatMap(s =>
        Vector(Q.Eq("sourceIdentity", s.identity), Q.Eq("sourceRevision", s.revision))) ++
      input.recordedFrom.map(s => Q.Gte("recordedAt", s.dropRight(1))) ++
      input.recordedBefore.map(s => Q.Lt("recordedAt", s.dropRight(1)))
    QueryDirective(Query.Expr(Q.And(clauses)), QueryProjection.Fields(projectionFields),
      QueryOrder.By("id", OrderDirection.Asc), QueryLimit.Limit(input.limit.getOrElse(20) + 1), 0)
  }

  /** The by-name store resolves only after input admission and enabled policy.
    * Production supplies protected component_datastore from FindCall.execute.
    */
  def searchC(policy: Option[DevelopmentJournal.Policy], input: V.JournalFindInput,
    store: => DataStore): Consequence[V.JournalFindResult] = {
    if (!validateInputC(input).isSuccess) Consequence.success(_result("invalid", "Journal find input is invalid"))
    else policy match {
      case None => Consequence.success(_result("disabled", "Journal is disabled by the host"))
      case Some(p) => try {
        val resolved = store
        val attempted = resolved match {
          case searchable: SearchableDataStore => for {
            directive <- directiveC(input)
            found <- searchable.search(p.collection, directive)
            entries <- found.records.foldLeft(Consequence.success(Vector.empty[V.JournalEntry])) { (acc, row) =>
              acc.flatMap(entries => _entry_c(resolved, row).map(entries :+ _))
            }
          } yield V.JournalFindResult("found", "Journal entries found; refine filters when hasMore is true",
            entries.take(input.limit.getOrElse(20)).map(_summary), entries.size > input.limit.getOrElse(20))
          case _ => Consequence.stateConflict("Journal store does not support search")
        }
        Consequence.success(attempted.fold(f => _result("unavailable", "Journal find unavailable" +
          s" (status=${f.status.webCode.code}, detail=${f.status.detailCode.map(_.code).getOrElse(0)})"), v => v))
      } catch { case NonFatal(error) => Consequence.success(_result("unavailable",
        "Journal find interrupted: " + error.getClass.getSimpleName)) }
    }
  }

  private def _entry_c(store: DataStore, row: Record): Consequence[V.JournalEntry] =
    row.getString(store.recordKey("id")) match {
      case Some(id) if DevelopmentJournal.sourceC(id).isSuccess =>
        DevelopmentJournal.storedC(row, id).map(DevelopmentJournal.publicEntry)
      case Some(id) if DevelopmentJournalFinding.sourceC(id).isSuccess =>
        DevelopmentJournalFinding.storedC(row, id).map(DevelopmentJournalFinding.publicEntry)
      case Some(id) if DevelopmentJournalEvidence.validIdentity(id) =>
        DevelopmentJournalEvidence.storedC(row, id).map(DevelopmentJournalEvidence.publicEntry)
      case Some(id) if DevelopmentJournalMergeAssessment.validIdentity(id) =>
        DevelopmentJournalMergeAssessment.storedC(row, id).map(DevelopmentJournalMergeAssessment.publicEntry)
      case Some(id) if DevelopmentJournalAssessment.validIdentity(id) =>
        DevelopmentJournalAssessment.storedC(row, id).map(DevelopmentJournalAssessment.publicEntry)
      case _ => Consequence.stateConflict("Journal physical identity is invalid")
    }
  private def _summary(entry: V.JournalEntry): V.JournalEntrySummary = {
    val truncated = entry.summary.length > summaryLimit
    val summary = if (truncated) entry.summary.take(summaryLimit - truncationMarker.length) + truncationMarker else entry.summary
    V.JournalEntrySummary(V.JournalEntryReference(entry.identity, entry.revision), entry.kind, entry.repositoryIdentity,
      entry.phaseIdentity, entry.stepIdentity, entry.sessionIdentity, entry.sourceReference, entry.recordedAt, summary, truncated)
  }
  private def _result(status: String, message: String): V.JournalFindResult =
    V.JournalFindResult(status, message, Vector.empty, false)
  private def _exact(value: String): Boolean = value != null && value.trim.nonEmpty && value.trim == value &&
    !value.exists(_.isControl) && UTF_8.newEncoder().canEncode(value)
  private def _second(value: String): Boolean = try {
    value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z") &&
      Instant.parse(value).getNano == 0 && Instant.parse(value).toString == value
  } catch { case NonFatal(_) => false }
}
