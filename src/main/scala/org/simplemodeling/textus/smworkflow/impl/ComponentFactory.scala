package org.simplemodeling.textus.smworkflow.impl

import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.ThreeWorkflowHostParameters
import org.goldenport.cncf.component.Component

/**
 * @since   Sep. 25, 2026
 * @version Oct. 12, 2026
 */
final class ComponentFactory extends Component.BundleFactory {
  def primaryFactory: Component.PrimaryComponentFactory =
    ThreeWorkflowHostParameters.factory.primaryFactory

  override def componentletFactories: Vector[Component.ComponentletFactory] =
    Vector.empty
}

abstract class SmWorkflowParticipantFactoryBase extends SmWorkflowComponent.Factory
