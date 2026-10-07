package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.ComponentInstanceId
import org.goldenport.cncf.context.SubjectKind
import org.goldenport.cncf.security.SecuritySubject
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseTypedStartPreparation as T

/**
 * Independently verified host evidence, not an authentication/selection claim.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object SplitPhaseTypedStartHumanVerification {
  val purpose = "split-phase-start"
  final case class EvidenceReference(identity: String, revision: String)
  final case class Binding(componentInstance: ComponentInstanceId, start: T.Record)
  final case class Receipt(reference: EvidenceReference, issuer: String, humanParticipant: String,
    purposeIdentity: String, binding: Binding, occurredAt: Instant, validUntil: Instant)
  trait Verifier {
    def issuerIdentity: String
    /** Independently issued human evidence for this exact Start, including any
      * authorized relay. Authentication alone is not a conforming verifier.
      */
    def verifyForCallC(expected: Binding, core: ActionCall.Core): Consequence[Option[Receipt]]
  }
  final class Admitted private[SplitPhaseTypedStartHumanVerification] (receipt: Receipt, call: ActionCall.Core) {
    def receiptForCallC(core: ActionCall.Core): Consequence[Receipt] =
      if (core != null && (core eq call)) Consequence.success(receipt)
      else Consequence.securityPermissionDenied("SplitPhase human Start admission belongs to another call")
  }
  def admitForCallC(prepared: T.Prepared, asOf: Instant, core: ActionCall.Core, verifier: Verifier): Consequence[Admitted] =
    if (prepared == null || core == null || core.executionContext == null ||
        !core.component.exists(c => c.componentId == SmWorkflowComponent.componentId && c.subsystem.nonEmpty) ||
        asOf == null || verifier == null)
      Consequence.securityPermissionDenied("SplitPhase human Start requires its current owner and independent verifier")
    else for {
      _ <- participantC(prepared.record.command.participantIdentity, core)
      binding = Binding(core.component.get.core.instanceId, prepared.record)
      issuer <- try {
        val value = verifier.issuerIdentity
        if (name(value)) Consequence.success(value)
        else Consequence.securityPermissionDenied("SplitPhase human Start issuer is invalid")
      } catch { case NonFatal(_) => Consequence.serviceUnavailable("SplitPhase human Start issuer is unavailable") }
      observed <- try Option(verifier.verifyForCallC(binding, core)).getOrElse(
        Consequence.serviceUnavailable("SplitPhase human Start verifier returned no outcome"))
        catch { case NonFatal(_) => Consequence.serviceUnavailable("SplitPhase human Start verification is indeterminate") }
      receipt <- observed match {
        case Some(value) if value != null && value.reference != null && name(value.reference.identity) &&
            name(value.reference.revision) && value.issuer == issuer && name(value.humanParticipant) &&
            value.purposeIdentity == purpose && value.binding == binding && value.occurredAt != null &&
            value.validUntil != null && !value.occurredAt.isAfter(asOf) && asOf.isBefore(value.validUntil) =>
          Consequence.success(value)
        case _ => Consequence.securityPermissionDenied("SplitPhase human Start evidence is absent stale or differs from the complete binding")
      }
    } yield new Admitted(receipt, core)

  private def participantC(identity: String, core: ActionCall.Core): Consequence[Unit] = {
    val security = core.executionContext.security
    if (security == null || !name(identity)) Consequence.securityPermissionDenied("SplitPhase Start participant is incomplete")
    else {
      val subject = SecuritySubject.from(security)
      if (subject.isAuthenticated && (subject.isProviderAuthenticated || subject.isTrustedLocalAuthenticated) &&
          (security.subjectKind == SubjectKind.User || security.subjectKind == SubjectKind.Service) && subject.subjectId == identity)
        Consequence.unit
      else Consequence.securityPermissionDenied("SplitPhase Start participant differs from the admitted current principal")
    }
  }
  private def name(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
}
