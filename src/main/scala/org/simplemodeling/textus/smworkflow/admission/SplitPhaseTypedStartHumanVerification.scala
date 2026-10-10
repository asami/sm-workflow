package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.ComponentInstanceId
import org.goldenport.cncf.context.SubjectKind
import org.goldenport.cncf.security.SecuritySubject
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseTypedStartPreparation as T

/**
 * Start admission consumes the independent host boundary's outcome. It does
 * not wrap that outcome in another receipt or repeat its evidence checks.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object SplitPhaseTypedStartHumanVerification {
  val purpose = "split-phase-start"
  final case class StartContext(componentInstance: ComponentInstanceId, start: T.Record)
  trait EvidencePort {
    /** Independently issued human consent to the actual Start, including any
      * authorized relay. The host owns evidence loading and its validation;
      * authentication alone is not consent.
      */
    def admitC(core: ActionCall.Core, lookupIdentity: String, target: Json): Consequence[Unit]
  }
  final class Admitted private[SplitPhaseTypedStartHumanVerification] (prepared: T.Prepared, call: ActionCall.Core) {
    def preparedForCallC(core: ActionCall.Core): Consequence[T.Prepared] =
      if (core != null && (core eq call)) Consequence.success(prepared)
      else Consequence.securityPermissionDenied("SplitPhase human Start admission belongs to another call")
  }
  def admitForCallC(prepared: T.Prepared, core: ActionCall.Core, evidence: EvidencePort): Consequence[Admitted] =
    if (prepared == null || core == null || core.executionContext == null ||
        !core.component.exists(c => c.componentId == SmWorkflowComponent.componentId && c.subsystem.nonEmpty) ||
        evidence == null)
      Consequence.securityPermissionDenied("SplitPhase human Start requires its current owner and independent host evidence")
    else for {
      _ <- participantC(prepared.record.command.participantIdentity, core)
      target <- ThreeWorkflowHostEvidencePorts.splitStartBindingC(StartContext(core.component.get.core.instanceId, prepared.record))
      lookup = ThreeWorkflowHostEvidencePorts.key(prepared.record.reference.identity, prepared.record.reference.revision)
      _ <- try Option(evidence.admitC(core, lookup, target)).getOrElse(
        Consequence.serviceUnavailable("SplitPhase human Start host evidence returned no outcome"))
        catch { case NonFatal(_) => Consequence.serviceUnavailable("SplitPhase human Start verification is indeterminate") }
    } yield new Admitted(prepared, core)

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
