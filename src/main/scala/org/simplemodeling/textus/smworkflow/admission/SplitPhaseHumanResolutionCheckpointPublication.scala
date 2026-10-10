package org.simplemodeling.textus.smworkflow.admission

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.record.Record as StorageRecord
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseHumanResolutionCheckpointPreparation as C,
  SplitPhaseScopeProjectionSnapshot as V, SplitPhasePlanningCheckpointPublication as U,
  SplitPhaseExplicitPlanningInputJsonV1 as F}

/** Human successor purpose metadata and complete passive Create descriptors.
  * No provider, pool, execution context or commit authority is retained here.
  *
  * @since Oct.  5, 2026
  * @version Oct. 11, 2026
  */
private[smworkflow] object SplitPhaseHumanResolutionCheckpointPublication {
  val prefix = "sm-workflow.split-phase.human-checkpoints."

  final class Targets private[SplitPhaseHumanResolutionCheckpointPublication] (val documents: U.Targets) {
    def storeName: String = documents.storeName
  }
  final class Prepared private[SplitPhaseHumanResolutionCheckpointPublication] (
    val captured: C.Captured, val targets: Targets, val rows: Vector[U.Row])

  /** Only actual document targets are consumed. Old human-checkpoints metadata
    * selects no storage effect and is not an admission or resume certificate.
    */
  def bindTargetsC(host: ResolvedConfiguration, workflow: SplitPhaseManagedStartAssembly.Declaration):
      Consequence[Targets] = _guarded {
    U.bindTargetsC(host, workflow).map(new Targets(_))
  }

  def prepareC(captured: C.Captured, targets: Targets): Consequence[Prepared] = _guarded {
    if (captured == null || targets == null)
      _invalid("private capture or targets are absent")
    else {
      val record = captured.record
      for {
        documents <- record.frontier.collect {
          case V.Candidate(_, _, V.CandidateOutput.Built(value, false)) => value
        }.foldLeft(Consequence.success(Vector.empty[U.Row])) { (prior, value) => for {
          accumulated <- prior
          proposal <- F.encodeProposalC(value.proposal)
          ownership <- F.encodeOwnershipC(value.ownership)
          handoffs <- F.encodeHandoffsC(value.handoffs)
        } yield accumulated ++ Vector(
          U.Row(targets.documents.proposals, value.proposal.reference.identity, StorageRecord.data(F.storageField -> proposal)),
          U.Row(targets.documents.ownerships, value.ownership.reference.identity, StorageRecord.data(F.storageField -> ownership)),
          U.Row(targets.documents.handoffs, value.handoffs.reference.identity, StorageRecord.data(F.storageField -> handoffs))) }
        rows = documents
      } yield new Prepared(captured, targets, rows)
    }
  }

  private def _invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("SplitPhase Human successor publication " + reason)
  private def _guarded[A](body: => Consequence[A]): Consequence[A] = try body catch {
    case NonFatal(_) => _invalid("complete source, purpose metadata or descriptors are malformed")
  }
}
