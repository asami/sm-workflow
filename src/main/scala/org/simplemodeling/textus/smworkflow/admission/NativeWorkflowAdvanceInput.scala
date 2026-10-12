package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationRequest
import org.goldenport.protocol.spec as spec

/** Selected advance targets passed directly to the native action owner.
  * Not a public decoder, command envelope or additional admission stage.
  * @since   Oct. 11, 2026
  * @version Oct. 12, 2026
  */
private[smworkflow] object NativeWorkflowAdvanceInput {
  sealed trait Resolved
  final case class Goal(instanceIdentity: String) extends Resolved
  final case class Repository(instanceIdentity: String) extends Resolved
  final case class Split(command: SplitPhaseAdvanceTarget) extends Resolved

  trait Receiver extends spec.OperationDefinition {
    def createAdvanceOperationRequestC(request: Request, input: Resolved): Consequence[OperationRequest]
  }
}
