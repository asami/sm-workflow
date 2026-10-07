package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.ComponentInstanceId
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseNativeHumanDirection as H,
  SplitPhaseHumanDirectionAnswerContract as A}

/** Independent host evidence for the complete question/result, not login consent.
  * No receipt decoder, assembly clock, storage grant or command permission.
  *
  * @since Oct.  4, 2026
  * @version Oct.  4, 2026
  */
private[smworkflow] object SplitPhaseHumanAnswerVerification {
  val purpose = "split-phase-human-direction-answer"
  final case class EvidenceReference(identity: String, revision: String)
  final case class Binding(componentInstance: ComponentInstanceId,
    question: WorkflowInteraction[?, Nothing], result: ContinuationResult[A.Answer],
    submittingParticipant: String)
  /** Returned only by an independent host verifier; never request-decoded. */
  final case class Receipt(reference: EvidenceReference, issuer: String, humanParticipant: String,
    purposeIdentity: String, binding: Binding, occurredAt: Instant, validUntil: Instant)
  trait Verifier {
    def issuerIdentity: String
    def verifyForCallC(reference: EvidenceReference, expected: Binding,
      core: ActionCall.Core): Consequence[Option[Receipt]]
  }
  final class Admitted private[SplitPhaseHumanAnswerVerification] (receipt: Receipt, call: ActionCall.Core) {
    def receiptForCallC(core: ActionCall.Core): Consequence[Receipt] =
      if (core != null && (core eq call)) Consequence.success(receipt)
      else Consequence.securityPermissionDenied("SplitPhase Human answer admission belongs to another call")
  }

  def admitForCallC(expected: Binding, core: ActionCall.Core, verifier: Verifier): Consequence[Admitted] =
    if (expected == null || expected.question == null || expected.result == null || expected.componentInstance == null ||
        core == null || core.action == null || core.action.request == null || core.executionContext == null ||
        core.executionContext.clock == null || !core.component.exists(value =>
          value.componentId == SmWorkflowComponent.componentId && value.subsystem.nonEmpty &&
            value.core.instanceId == expected.componentInstance) || verifier == null ||
        !core.action.request.service.contains(H.completionOperation.service) ||
        core.action.request.operation != H.completionOperation.operation)
      Consequence.securityPermissionDenied("SplitPhase Human answer requires its owning completion call and independent verifier")
    else for {
      _ <- WorkflowProtocolV1.admitDecisionResultC(expected.question, expected.result).map(_ => ())
      _ <- if (expected.question.handle.componentIdentity == SmWorkflowComponent.componentId &&
          expected.result.result.typeIdentity == A.resultType) Consequence.unit
        else Consequence.securityPermissionDenied("SplitPhase Human answer has a foreign Component or result type")
      _ <- A.encodeC(expected.result.result.value)
      _ <- GoalPhaseParticipantAdmission.admitC(expected.submittingParticipant, core.executionContext)
      reference = EvidenceReference(expected.result.resultReference.identity, expected.result.resultReference.revision)
      _ <- if (_canonical(reference.identity) && _canonical(reference.revision)) Consequence.unit
        else Consequence.securityPermissionDenied("SplitPhase Human evidence reference is invalid")
      issuer <- _observe_c("issuer") {
        val value = verifier.issuerIdentity
        if (_canonical(value)) Consequence.success(value)
        else Consequence.securityPermissionDenied("SplitPhase Human verifier issuer is invalid")
      }
      observed <- _observe_c("evidence") {
        Option(verifier.verifyForCallC(reference, expected, core)).getOrElse(
          Consequence.serviceUnavailable("SplitPhase Human verifier returned no outcome"))
      }
      now <- _observe_c("execution clock") {
        Option(core.executionContext.clock.instant()).map(Consequence.success).getOrElse(
          Consequence.serviceUnavailable("SplitPhase Human execution clock is unavailable"))
      }
      receipt <- observed match {
        case Some(value) if value != null && value.reference == reference && value.issuer == issuer &&
            _canonical(value.humanParticipant) && value.purposeIdentity == purpose && value.binding == expected &&
            value.occurredAt != null && value.validUntil != null && !value.occurredAt.isAfter(now) &&
            now.isBefore(value.validUntil) => Consequence.success(value)
        case _ => Consequence.securityPermissionDenied("SplitPhase Human evidence is absent stale or differs from the entire question/result")
      }
      _ <- _observe_c("final issuer") {
        if (verifier.issuerIdentity == issuer) Consequence.unit
        else Consequence.securityPermissionDenied("SplitPhase Human verifier issuer changed")
      }
    } yield new Admitted(receipt, core)

  private def _canonical(value: String): Boolean =
    value != null && value.nonEmpty && value.length <= 1024 && value.trim == value && !value.exists(_.isControl)
  private def _observe_c[A](label: String)(read: => Consequence[A]): Consequence[A] =
    try read catch { case NonFatal(_) =>
      Consequence.serviceUnavailable("SplitPhase Human " + label + " observation is indeterminate; no answer admitted") }
}
