package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentInit}
import org.goldenport.cncf.config.{ComponentInitializationParameters, ComponentParameterKey, ComponentParameterPathRoute}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.protocol.spec.{ServiceDefinition, ServiceDefinitionGroup}
import org.goldenport.protocol.logic.ProtocolLogic
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.impl.SmWorkflowParticipantFactoryBase

/** One host-owned bundle after canonical initialization-parameter resolution.
  * No binder runs in create_Core; each component owns its creation capsule.
  * Binding does not create process/storage drivers, caller identity or grants.
  * @since   Oct.  6, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] final class SmWorkflowConfiguredComponentFactory(
  declarations: Vector[ComponentParameterKey[?]],
  routes: Vector[ComponentParameterPathRoute],
  bind: (ComponentCreate, ComponentInitializationParameters, ExecutionContext) => Consequence[Vector[ServiceDefinition]],
  publishedMcpOperations: Set[String] = Set.empty
) extends Component.BundleFactory {
  def primaryFactory: Component.PrimaryComponentFactory = new Primary
  override def componentletFactories: Vector[Component.ComponentletFactory] = Vector.empty

  private final class ConfiguredComponent(val creation: ComponentCreate) extends SmWorkflowComponent {
    override def mcpReadyServices: Set[String] = Set.empty
  }

  private final class Primary extends SmWorkflowParticipantFactoryBase with Component.PrimaryComponentFactory {
    override def initializationParameterDeclarations: Vector[ComponentParameterKey[?]] = declarations
    override def initializationParameterPathRoutes: Vector[ComponentParameterPathRoute] = routes

    override protected def create_Component(params: ComponentCreate): Component = {
      require(params != null && declarations != null && routes != null && bind != null && publishedMcpOperations != null,
        "sm-workflow configured bundle factory is incomplete")
      new ConfiguredComponent(params)
    }

    override protected def create_Core(params: ComponentCreate, component: Component): Component.Core =
      spec_create(SmWorkflowComponent.name, SmWorkflowComponent.componentId, Vector.empty[ServiceDefinition])

    override protected def initialize_component_c(component: Component, params: ComponentInit): Consequence[Component] =
      component match {
        case configured: ConfiguredComponent if params != null && params.initializationParameters != null =>
          bind(configured.creation, params.initializationParameters, component.logic.executionContext()).flatMap { services =>
            if (services == null || services.isEmpty || services.exists(_ == null))
              Consequence.configurationInvalid("sm-workflow configured service bundle is missing")
            else if (services.map(_.name).distinct.size != services.size ||
                services.exists(s => params.core.protocol.services.services.exists(_.name == s.name)))
              Consequence.configurationInvalid("sm-workflow configured service bundle conflicts with host services")
            else if (!publishedMcpOperations.subsetOf(services.flatMap(s => s.operations.operations.toVector
                .map(operation => s"${s.name}.${operation.name}")).toSet))
              Consequence.configurationInvalid("sm-workflow MCP publication requires actual bound operations")
            else {
              val protocol = params.core.protocol.copy(services = ServiceDefinitionGroup(
                params.core.protocol.services.services ++ services))
              super.initialize_component_c(component, params.copy(core = params.core.copy(
                protocol = protocol, protocolLogic = ProtocolLogic(protocol))))
                .map(_.withMcpReadyOperations(publishedMcpOperations))
            }
          }
        case _ => Consequence.configurationInvalid("sm-workflow configured component authority is missing")
      }
  }
}
