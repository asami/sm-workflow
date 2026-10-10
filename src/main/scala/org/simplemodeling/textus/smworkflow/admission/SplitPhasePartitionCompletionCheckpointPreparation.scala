package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.charset.StandardCharsets.UTF_8
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseTypedPartitionCompletion as P,
  SplitPhasePlanningCheckpointPreparation as K, SplitPhaseExplicitPartitionContext as C,
  SplitPhaseTypedStartPreparation as T, SplitPhaseExplicitPartitionSemantic as S,
  SplitPhasePartitionEnrichmentPublication as E}

/** Actual original completion facts. Raw Record is inert; only a selected Plan
  * can construct Captured. Nothing here executes a transition or recovers a Plan.
  * @since   Oct.  2, 2026
  * @version Oct. 10, 2026
  */
private[smworkflow] object SplitPhasePartitionCompletionCheckpointPreparation {
  final case class Record(context: C.Record, start: T.Record, issued: WorkflowInteraction[S.Request, Nothing],
    completion: WorkflowCompletionRecordJsonV1.Record[S.Judgment], before: InstanceRecord, next: InstanceRecord,
    checkpoint: Option[K.Record], header: Option[E.Record], wire: String)
  final class Captured private[SplitPhasePartitionCompletionCheckpointPreparation] (
    val record: Record, val checkpoint: Option[K.Captured])

  def lineageKeyC(identity: org.goldenport.cncf.workflow.ContinuationIdentity): Consequence[String] = _guarded {
    WorkflowMechanicalTransition.keyC(WorkflowMechanicalTransition.Reference(identity.value, "1"))
      .flatMap(k => if (("partition-completion:" + k).getBytes(UTF_8).length <= 1024)
        Consequence.success("partition-completion:" + k) else _invalid("lineage locator exceeds its final bound"))
  }
  def mappingReference(result: ContinuationResult[S.Judgment]): K.Reference = K.Reference(
    Json.arr(Json.fromString(result.continuationId.value), Json.fromString(result.resultReference.identity),
      Json.fromString(result.resultReference.revision)).noSpaces, "1")

  def captureC(plan: P.Plan): Consequence[Captured] = _guarded {
    if (plan == null) _invalid("actual selected completion is absent")
    else for {
      checkpoint <- K.captureCompletionC(plan, mappingReference(plan.submitted))
      header = plan.semantic match {
        case enriched: S.Enriched =>
          val admitted = enriched.admitted
          val original = plan.pending.binding.context.record
          val planning = checkpoint.map(_.record.snapshot.selection.planning).filter(_ != original.facts.planning)
          Some(E.Record(admitted.resultReference, original.reference, plan.pending.issued.handle.componentIdentity.name,
            plan.next.identity.value, plan.submitted.continuationId.value, plan.next.revision.value,
            admitted.references.estimates, admitted.references.handoffs, planning.map(_.reference)))
        case _: S.DecisionNeeded => None
      }
      record = Record(plan.pending.binding.context.record, plan.pending.binding.start.record, plan.pending.issued,
        WorkflowCompletionRecordJsonV1.Record(plan.next.revision, plan.submitted, Some(plan.closing)),
        plan.pending.current, plan.next, checkpoint.map(_.record), header, plan.wire)
    } yield new Captured(record, checkpoint)
  }
  private def _invalid[A](why: String): Consequence[A] = Consequence.stateConflict("SplitPhase AI checkpoint " + why)
  private def _guarded[A](body: => Consequence[A]): Consequence[A] = try body catch {
    case NonFatal(_) => _invalid("complete data is absent or malformed")
  }
}
