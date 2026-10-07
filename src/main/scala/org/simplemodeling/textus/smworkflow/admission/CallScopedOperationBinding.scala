package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, ProcedureActionCall}
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec

/** Bind application callbacks per execution while retaining the canonical
  * Operation's declaration, decoder and completion engine. No captured host core.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object CallScopedOperationBinding {
  def wrap(declared: spec.OperationDefinition,
    bind: ActionCall.Core => Consequence[spec.OperationDefinition]): spec.OperationDefinition =
    new spec.OperationDefinition {
      val specification = declared.specification
      def createOperationRequest(request: Request): Consequence[OperationRequest] =
        declared.createOperationRequest(request).flatMap {
          case command: CommandAction => Consequence.success(new CommandAction {
            val request: Request = command.request
            override def commandExecutionMode = command.commandExecutionMode
            def createCall(capsule: ActionCall.Core): ActionCall = new ProcedureActionCall {
              val core = capsule
              private var delegate: Option[ActionCall] = None
              def execute(): Consequence[OperationResponse] =
                if (core == null || core.executionContext == null)
                  Consequence.stateConflict("Call-scoped completion has no current Operation context")
                else bind(core).flatMap(_.createOperationRequest(command.request)).flatMap {
                  case bound: CommandAction =>
                    val call = bound.createCall(core.copy(action = bound))
                    delegate = Some(call)
                    call.execute()
                  case _ => Consequence.stateConflict("Call-scoped completion is not a canonical command")
                }
              override def afterCommitResponseC(response: OperationResponse): Consequence[OperationResponse] = {
                val selected = delegate
                delegate = None
                selected match {
                  case Some(call) => call.afterCommitResponseC(response)
                  case None => Consequence.stateConflict("Call-scoped completion has no executed delegate")
                }
              }
            }
          })
          case _ => Consequence.stateConflict("Call-scoped completion declaration is not a command")
        }
    }
}
