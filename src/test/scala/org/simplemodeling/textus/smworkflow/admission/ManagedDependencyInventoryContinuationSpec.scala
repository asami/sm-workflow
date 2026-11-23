package org.simplemodeling.textus.smworkflow.admission

import org.scalatest.wordspec.AnyWordSpec

/** Connected specification for a supplemental inventory on a pending managed destination.
  * @since   Nov. 23, 2026
  * @version Nov. 23, 2026
  * @author ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class ManagedDependencyInventoryContinuationSpec extends AnyWordSpec {
  "Managed dependency inventory continuation" should {
    "apply only the exact selected paths to the same destination and preserve the original parent and destination history" in {
      (new Phase7ManagedDevelopmentSpec).exerciseManagedDependencyInventory(leavePending = false)
    }

    "retain a separate pending destination for old and new installed runtimes" in {
      (new Phase7ManagedDevelopmentSpec).exerciseManagedDependencyInventory(leavePending = true)
    }
  }
}
