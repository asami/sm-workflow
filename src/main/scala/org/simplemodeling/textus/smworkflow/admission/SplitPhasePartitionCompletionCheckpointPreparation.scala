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
  * @version Oct.  8, 2026
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
      _ <- inspectC(record)
    } yield new Captured(record, checkpoint)
  }

  /** Complete inert relationships, never a native acknowledgement. */
  def inspectC(value: Record): Consequence[Unit] = _guarded {
    val c = value.context; val t = value.start; val r = value.completion; val b = value.before; val n = value.next
    val work = value.issued.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request
    for {
      _ <- lineageKeyC(r.result.continuationId)
      _ <- b.validateC
      _ <- n.validateC
      _ <- WorkflowProtocolV1.admitResultC(value.issued, r.result).map(_ => ())
      _ <- if (t.reference == c.owner.start && t.configuration == c.owner.configuration &&
          t.instanceIdentity == b.identity && b.identity == n.identity && SplitPhaseDefinitionBinding.sameDefinition(b.definition, n.definition) &&
          value.issued.handle == c.owner.handle && work.input.exists(_.value.context == c.reference) &&
          work.continuationId == c.owner.continuation && r.result.continuationId == c.owner.continuation &&
          c.owner.instanceRevision.value <= Long.MaxValue - 2 && b.revision.value == c.owner.instanceRevision.value + 1 &&
          n.revision.value == b.revision.value + 1 && n.history.dropRight(1) == b.history &&
          b.suspension.exists(_.continuationIdentity.value == work.continuationId.value) && n.suspension.isEmpty &&
          r.instanceRevision == n.revision && r.closingResult.exists(x => x.typeReference == P.closingType &&
            x.contextReference == r.result.resultReference) && value.wire != null && value.checkpoint != null && value.header != null)
        Consequence.unit else _invalid("original issue/result/complete native lineage differ")
      _ <- WorkflowWaitJsonV1.decodeC(value.wire).flatMap { wait =>
        wait.current match {
          case w: WorkflowContinuation.Wait if wait.handle == value.issued.handle && w.expectedRevision.value == n.revision.value.toString =>
            Consequence.unit
          case _ => _invalid("original typed response differs from completion")
        }
      }
      _ <- r.result.result.value.decision match {
        case S.Decision.DecisionNeeded if value.checkpoint.isEmpty && value.header.isEmpty => Consequence.unit
        case S.Decision.Enriched => value.checkpoint match {
          case Some(k) => for {
            _ <- K.inspectC(k)
            expected = E.Record(S.ResultReference(r.result.resultReference.identity, r.result.resultReference.revision), c.reference,
              value.issued.handle.componentIdentity.name, n.identity.value, r.result.continuationId.value, n.revision.value,
              work.input.get.value.reply.resolutions.estimates, work.input.get.value.reply.resolutions.handoffs,
              Option(k.snapshot.selection.planning).filter(_ != c.facts.planning).map(_.reference))
            _ <- if (k.reference == mappingReference(r.result) && k.originalContext == c && k.originalStart == t &&
                k.origin == K.Origin.PartitionCompletion(r.result.continuationId, expected.result) &&
                k.predecessorRevision == c.owner.instanceRevision && k.committedRevision == n.revision &&
                value.header.contains(expected)) Consequence.unit else _invalid("checkpoint or compatibility header differs")
          } yield ()
          case None => _invalid("enriched completion lacks its actual captured checkpoint")
        }
        case _ => _invalid("decision and captured planning differ")
      }
    } yield ()
  }
  private def _invalid[A](why: String): Consequence[A] = Consequence.stateConflict("SplitPhase AI checkpoint " + why)
  private def _guarded[A](body: => Consequence[A]): Consequence[A] = try body catch {
    case NonFatal(_) => _invalid("complete data is absent or malformed")
  }
}
