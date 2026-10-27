package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec

/** Publish ordinary descriptions before a particular run has been selected.
  * Each invocation composes its actual handler in the incoming call; no actor,
  * UoW, run settings or result is cached across requests.
  * @since Oct. 27, 2026
  * @version Oct. 27, 2026
  */
private[smworkflow] object DeferredWorkflowServices {
  type Resolver = (Request, ActionCall.Core) => Consequence[Vector[spec.ServiceDefinition]]

  def bind(declarations: Vector[spec.ServiceDefinition], resolve: Resolver): Vector[spec.ServiceDefinition] =
    declarations.map { declared =>
      NativeWorkflowDescriptionMetadata.service(declared, spec.OperationDefinitionGroup(
        NonEmptyVector.fromVectorUnsafe(declared.operations.operations.toVector.map(operation =>
          new Operation(declared.name, operation, resolve)))))
    }

  private final class Operation(service: String, declared: spec.OperationDefinition, resolve: Resolver)
      extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(declared)
    private val operationName = declared.name
    def createOperationRequest(incoming: Request): Consequence[OperationRequest] =
      if (incoming == null || !incoming.service.contains(service) || incoming.operation != operationName)
        Consequence.stateConflict("Workflow request differs from its declared Operation")
      else Consequence.success(new CommandAction {
        val request = incoming
        override def commandExecutionMode = CommandExecutionMode.Sync
        def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
          val core = c
          private var delegate: Option[ActionCall] = None
          def execute(): Consequence[OperationResponse] = for {
            services <- resolve(incoming, core)
            selected <- services.find(_.name == service).flatMap(_.operations.operations.toVector.find(_.name == operationName))
              .map(Consequence.success).getOrElse(Consequence.configurationInvalid("Workflow runtime has no handler for " + service + "." + operationName))
            operation <- selected.createOperationRequest(incoming)
            result <- operation match {
              case command: CommandAction =>
                val call = command.createCall(core.copy(action = command))
                delegate = Some(call)
                call.execute()
              case _ => Consequence.configurationInvalid("Workflow runtime handler requires a command Action")
            }
          } yield result
          override def afterCommitResponseC(response: OperationResponse): Consequence[OperationResponse] = {
            val selected = delegate
            delegate = None
            selected.map(_.afterCommitResponseC(response))
              .getOrElse(Consequence.stateConflict("Workflow runtime handler has not executed"))
          }
        }
      })
  }
}
