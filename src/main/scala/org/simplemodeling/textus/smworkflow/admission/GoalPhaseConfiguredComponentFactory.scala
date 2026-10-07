package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentCreate}
import org.goldenport.cncf.config.{ComponentInitializationParameters, ComponentParameterKey, ComponentParameterPathRoute}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.protocol.spec.ServiceDefinition

/** Host-only service composition after canonical parameter resolution.
  * Captured creation authority belongs to each component, not the reusable factory.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] final class GoalPhaseConfiguredComponentFactory(
  declarations: Vector[ComponentParameterKey[?]],
  routes: Vector[ComponentParameterPathRoute],
  bind: (ComponentCreate, ComponentInitializationParameters, ExecutionContext) => Consequence[ServiceDefinition],
  publishedMcpOperations: Set[String] = Set.empty,
  additionalBindings: Vector[(ComponentCreate, ComponentInitializationParameters, ExecutionContext) => Consequence[ServiceDefinition]] = Vector.empty
) extends Component.BundleFactory {
  private val delegate = new SmWorkflowConfiguredComponentFactory(declarations, routes,
    (creation, parameters, context) =>
      if (bind == null || additionalBindings == null || additionalBindings.exists(_ == null))
        Consequence.configurationInvalid("GoalPhase configured factory is incomplete")
      else (bind +: additionalBindings).foldLeft(Consequence.success(Vector.empty[ServiceDefinition])) { (prior, binding) =>
        prior.flatMap(services => binding(creation, parameters, context).map(services :+ _))
      }, publishedMcpOperations)
  def primaryFactory: Component.PrimaryComponentFactory = delegate.primaryFactory
  override def componentletFactories: Vector[Component.ComponentletFactory] = Vector.empty
}
