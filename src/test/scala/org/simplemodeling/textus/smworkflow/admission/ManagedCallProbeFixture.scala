package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec

/** Private current-call fixture; no business input, Start or execution grant.
  * @since   Oct. 12, 2026
  * @version Oct. 12, 2026
  */
private[admission] object ManagedCallProbeFixture {
  val serviceName = "ManagedProbeService"
  val operationName = "probe"

  def request(component: String): Request =
    Request.of(component = component, service = serviceName, operation = operationName)

  def factory(task: ActionCall.Core => Consequence[Unit]) =
    new SmWorkflowConfiguredComponentFactory(Vector.empty, Vector.empty,
      (creation, parameters, resolved) => Consequence.success(Vector(service(task)))).primaryFactory

  def service(task: ActionCall.Core => Consequence[Unit]): spec.ServiceDefinition = {
    val operation = new spec.OperationDefinition {
      val specification = spec.OperationDefinition.Specification(name = operationName,
        request = spec.RequestDefinition.empty, response = spec.ResponseDefinition.void)

      def createOperationRequest(input: Request): Consequence[OperationRequest] =
        Consequence.success(new CommandAction {
          val request = input
          override def commandExecutionMode = CommandExecutionMode.Sync
          def createCall(capsule: ActionCall.Core): ActionCall = new ProcedureActionCall {
            val core = capsule
            def execute(): Consequence[OperationResponse] =
              task(core).map(_ => OperationResponse.Scalar("observed"))
          }
        })
    }
    spec.ServiceDefinition(serviceName, spec.OperationDefinitionGroup(operation))
  }
}
