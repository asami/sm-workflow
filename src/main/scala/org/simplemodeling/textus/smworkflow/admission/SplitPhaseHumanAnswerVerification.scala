package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.ComponentInstanceId
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseNativeHumanDirection as H,
  SplitPhaseHumanDirectionAnswerContract as A}

/** Independent host evidence for actual issued direction, not login consent
  * or complete question/frontier/result copy certification.
  * No receipt decoder, assembly clock, storage grant or command permission.
  *
  * @since   Oct.  4, 2026
  * @version Oct. 23, 2026
  */
private[smworkflow] object SplitPhaseHumanAnswerVerification {
  val purpose = "split-phase-human-direction-answer"
  final case class AnswerContext(componentInstance: ComponentInstanceId,
    question: WorkflowInteraction[?, Nothing], result: ContinuationResult[A.Answer],
    submittingParticipant: String)
  /** The host boundary admits independently issued evidence once. No receipt
    * is returned to be revalidated by this internal consumer.
    */
  trait EvidencePort {
    def admitC(core: ActionCall.Core, reference: ContextReference, target: Json): Consequence[Unit]
  }
  final class Admitted private[SplitPhaseHumanAnswerVerification] (call: ActionCall.Core) {
    def forCallC(core: ActionCall.Core): Consequence[Unit] =
      if (core != null && (core eq call)) Consequence.unit
      else Consequence.securityPermissionDenied("SplitPhase Human answer admission belongs to another call")
  }

  def admitForCallC(expected: AnswerContext, core: ActionCall.Core, evidence: EvidencePort): Consequence[Admitted] =
    if (expected == null || expected.question == null || expected.result == null || expected.componentInstance == null ||
        core == null || core.action == null || core.action.request == null || core.executionContext == null ||
        core.executionContext.clock == null || !core.component.exists(value =>
          value.componentId == SmWorkflowComponent.componentId && value.subsystem.nonEmpty &&
            value.core.instanceId == expected.componentInstance) || evidence == null ||
        !core.action.request.service.contains(H.completionOperation.service) ||
        core.action.request.operation != H.completionOperation.operation)
      Consequence.securityPermissionDenied("SplitPhase Human answer requires its owning completion call and independent host evidence")
    else for {
      _ <- GoalPhaseParticipantAdmission.admitC(expected.submittingParticipant, core.executionContext)
      target <- ThreeWorkflowExternalEvidencePorts.splitAnswerBindingC(expected)
      _ <- _observe_c("evidence") {
        Option(evidence.admitC(core, expected.result.resultReference, target)).getOrElse(
          Consequence.serviceUnavailable("SplitPhase Human host evidence returned no outcome"))
      }
    } yield new Admitted(core)

  private def _observe_c[A](label: String)(read: => Consequence[A]): Consequence[A] =
    try read catch { case NonFatal(_) =>
      Consequence.serviceUnavailable("SplitPhase Human " + label + " observation is indeterminate; no answer admitted") }
}
