package org.simplemodeling.textus.smworkflow.impl

import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.ThreeWorkflowHostParameters
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId}

/**
 * @since   Sep. 25, 2026
 * @version Oct.  8, 2026
 */
final class ComponentFactory extends Component.BundleFactory {
  def primaryFactory: Component.PrimaryComponentFactory =
    ThreeWorkflowHostParameters.factory.primaryFactory

  override def componentletFactories: Vector[Component.ComponentletFactory] =
    Vector.empty
}

abstract class SmWorkflowParticipantFactoryBase extends SmWorkflowComponent.Factory {
  protected final val shared_services =
    Vector(
      SmWorkflowComponent.GoalPhaseServiceService,
      SmWorkflowComponent.StepReviewServiceService
    )

  protected final def component_core(
    name: String,
    componentId: ComponentId
  ): Component.Core =
    spec_create(name, componentId, shared_services)

}

final class SmWorkflowPrimaryComponent extends SmWorkflowComponent {
  override def mcpReadyServices: Set[String] =
    Set.empty
}

object SmWorkflowPrimaryFactory extends SmWorkflowParticipantFactoryBase with Component.PrimaryComponentFactory {
  override protected def create_Component(params: ComponentCreate): Component =
    new SmWorkflowPrimaryComponent()

  override protected def create_Core(
    params: ComponentCreate,
    comp: Component
  ): Component.Core =
    component_core(SmWorkflowComponent.name, SmWorkflowComponent.componentId)
}
