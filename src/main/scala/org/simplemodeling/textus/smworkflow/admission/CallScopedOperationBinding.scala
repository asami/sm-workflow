package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, ProcedureActionCall}
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec
import org.goldenport.cncf.workflow.WorkflowProtocolV1.ContinuationResult

/** Bind application callbacks and resolved results per execution while retaining
  * the canonical completion engine. No captured host core or result-wire adapter.
  * @since   Oct.  1, 2026
  * @version Oct. 12, 2026
  */
private[smworkflow] object CallScopedOperationBinding {
  def wrapResult(declared: spec.OperationDefinition,
    bind: (ActionCall.Core, ContinuationResult[NativeWorkflowResultInput.Value]) => Consequence[spec.OperationDefinition]): spec.OperationDefinition =
    new NativeWorkflowResultInput.Receiver {
      val specification = declared.specification
      def createResultOperationRequestC(incoming: Request,
        result: ContinuationResult[NativeWorkflowResultInput.Value]): Consequence[OperationRequest] = Consequence.success(new CommandAction {
        val request = incoming
        override def commandExecutionMode = org.goldenport.cncf.action.CommandExecutionMode.Sync
        def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
          val core = c
          private var delegate: Option[ActionCall] = None
          def execute(): Consequence[OperationResponse] =
            bind(core, result).flatMap(_.createOperationRequest(incoming)).flatMap {
              case action: CommandAction =>
                val call = action.createCall(core.copy(action = action))
                delegate = Some(call)
                call.execute()
              case _ => Consequence.stateConflict("Resolved completion is not a canonical command")
            }
          override def afterCommitResponseC(response: OperationResponse): Consequence[OperationResponse] = {
            val selected = delegate
            delegate = None
            selected.map(_.afterCommitResponseC(response)).getOrElse(
              Consequence.stateConflict("Resolved completion has no executed delegate"))
          }
        }
      })
    }

  def wrap(declared: spec.OperationDefinition,
    bind: ActionCall.Core => Consequence[spec.OperationDefinition]): spec.OperationDefinition =
    declared match {
      case _: NativeWorkflowStartInput.Receiver => _wrapStart(declared, bind)
      case _ => _wrapCommand(declared, bind)
    }

  private def _wrapStart(declared: spec.OperationDefinition,
    bind: ActionCall.Core => Consequence[spec.OperationDefinition]): spec.OperationDefinition =
    new NativeWorkflowStartInput.Receiver {
      val specification = declared.specification
      def createOperationRequest(request: Request): Consequence[OperationRequest] =
        Consequence.stateConflict("Start requires its resolved business input")
      def createStartOperationRequestC(incoming: Request, input: NativeWorkflowStartInput.BusinessInput,
        selection: ProfileStartAdmission.Selection): Consequence[OperationRequest] = Consequence.success(new CommandAction {
        val request = incoming
        override def commandExecutionMode = org.goldenport.cncf.action.CommandExecutionMode.Sync
        def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
          val core = c
          private var delegate: Option[ActionCall] = None
          def execute(): Consequence[OperationResponse] = bind(core).flatMap {
            case receiver: NativeWorkflowStartInput.Receiver =>
              receiver.createStartOperationRequestC(incoming, input, selection)
            case _ => Consequence.configurationInvalid("Bound Start has no direct business-input receiver")
          }.flatMap {
            case action: CommandAction =>
              val call = action.createCall(core.copy(action = action))
              delegate = Some(call)
              call.execute()
            case _ => Consequence.stateConflict("Resolved Start is not a command")
          }
          override def afterCommitResponseC(response: OperationResponse): Consequence[OperationResponse] = {
            val selected = delegate
            delegate = None
            selected.map(_.afterCommitResponseC(response)).getOrElse(
              Consequence.stateConflict("Resolved Start has no executed delegate"))
          }
        }
      })
    }

  private def _wrapCommand(declared: spec.OperationDefinition,
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
