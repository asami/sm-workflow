package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.ComponentInstanceId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/**
 * Separate legacy host-evidence ingress; no storage grant or public authority
 * entry. The normal ordered Goal Human reader is a different boundary.
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object GoalPhaseHumanInteractionVerification {
  import GoalPhaseHumanDecisionContract.*
  val purpose = "goal-phase-decision-answer"
  final case class Binding(componentInstance: ComponentInstanceId, question: Question,
    answer: Answer, submittingParticipant: String)
  def sameBindingScope(left: Binding, right: Binding): Boolean =
    left != null && right != null && left.componentInstance == right.componentInstance &&
      left.submittingParticipant == right.submittingParticipant && left.answer == right.answer &&
      sameQuestionScope(left.question, right.question)
  /** Only a host verifier returns this value. There is deliberately no request decoder. */
  final case class Receipt(reference: String, issuer: String, humanParticipant: String,
    purposeIdentity: String, binding: Binding, occurredAt: Instant)
  trait Verifier {
    /** Current host-selected issuer, read once when admitting its observation;
      * not a before/after declaration-immutability witness.
      */
    def issuerIdentity: String
    /** Resolve independently issued human evidence and admit this exact relay/binding.
      * Returning an authentication claim is not a conforming implementation.
      */
    def verifyForCallC(reference: String, expected: Binding, core: ActionCall.Core): Consequence[Option[Receipt]]
  }
  final class Admitted private[GoalPhaseHumanInteractionVerification](receipt: Receipt, call: ActionCall.Core) {
    /** Prevent value equality/copy from transferring admission to another call.
      * A writer still needs its own actual managed lease and captured storage grant.
      */
    def receiptForCallC(core: ActionCall.Core): Consequence[Receipt] =
      if (core != null && (core eq call)) Consequence.success(receipt)
      else Consequence.securityPermissionDenied("Human admission belongs to another call")
  }

  def admitForCallC(expected: Binding, reference: String, asOf: Instant,
    core: ActionCall.Core, verifier: Verifier): Consequence[Admitted] =
    if (expected == null || expected.componentInstance == null || expected.question == null ||
        core == null || core.executionContext == null || core.component == null || !core.component.exists(value =>
          value.componentId == SmWorkflowComponent.componentId && value.subsystem.nonEmpty &&
            value.core.instanceId == expected.componentInstance) ||
        !canonical(reference, 1024) || asOf == null || verifier == null)
      Consequence.securityPermissionDenied("Human verification requires its owning current call and explicit verifier")
    else for {
      _ <- expected.question.admitAnswerC(expected.answer)
      _ <- if (expected.question.intent.handle.componentIdentity == core.component.get.componentId) Consequence.unit
        else Consequence.securityPermissionDenied("Human question belongs to another Component")
      _ <- GoalPhaseParticipantAdmission.admitC(expected.submittingParticipant, core.executionContext)
      observed <- try {
        Option(verifier.verifyForCallC(reference, expected, core)).getOrElse(
          Consequence.serviceUnavailable("Human verifier returned no outcome; no answer admitted"))
      }
        catch { case NonFatal(_) => Consequence.serviceUnavailable("Human verification indeterminate; no answer admitted") }
      issuer <- try {
        val value = verifier.issuerIdentity
        if (canonical(value, 256)) Consequence.success(value)
        else Consequence.securityPermissionDenied("Human verifier issuer is invalid")
      } catch { case NonFatal(_) => Consequence.serviceUnavailable("Human verifier issuer unavailable; no answer admitted") }
      receipt <- observed match {
        case Some(value) if value != null && value.reference == reference && value.issuer == issuer &&
            canonical(value.humanParticipant, 256) && value.purposeIdentity == purpose && sameBindingScope(value.binding, expected) &&
            value.occurredAt != null && !value.occurredAt.isAfter(asOf) => Consequence.success(value)
        case _ => Consequence.securityPermissionDenied("Human evidence is absent, future-dated or differs from the exact question/answer")
      }
      _ <- receipt.binding.question.admitAnswerC(receipt.binding.answer)
    } yield new Admitted(receipt, core)
}
