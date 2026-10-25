package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.resource.ResourceReference
import org.goldenport.protocol.Request
import org.simplemodeling.textus.smworkflow.value as V

/** Transport selection only; the unchanged recovery port owns admission.
  * Resource reads belong to the executing call's normal CNCF resource context.
  * @since   Oct. 24, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object NativeWholePhaseRecoveryInput {
  sealed trait Source
  final case class Inline(value: V.GoalPhaseWholePhaseCandidateRecoveryInput) extends Source
  final case class Resource(reference: ResourceReference.Url) extends Source

  def selectC(request: Request): Consequence[Source] = {
    NativeWorkflowResourceInput.selectC[V.GoalPhaseWholePhaseCandidateRecoveryInput](request).map {
      case NativeWorkflowResourceInput.Inline(value) => Inline(value)
      case NativeWorkflowResourceInput.Resource(reference) => Resource(reference)
    }
  }

  def readC(source: Source, readText: ResourceReference => Consequence[String]): Consequence[V.GoalPhaseWholePhaseCandidateRecoveryInput] =
    NativeWorkflowResourceInput.readC(source match {
      case Inline(value) => NativeWorkflowResourceInput.Inline(value)
      case Resource(reference) => NativeWorkflowResourceInput.Resource(reference)
    }, readText)

  private def _invalid[A](message: String): Consequence[A] =
    Consequence.stateConflict("Whole-Phase candidate recovery input: " + message)
}
