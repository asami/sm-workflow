package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.record.io.RecordDecoder
import org.simplemodeling.textus.smworkflow.value as V

/** Selected native authority target from the actual owning Closing input.
  * Exposing this target neither authenticates its origin nor grants permission.
  * @since   Oct. 14, 2026
  * @version Oct. 14, 2026
  */
private[smworkflow] object NativeHostAuthorityContext {
  def stepC(core: ActionCall.Core, input: GoalPhaseNativeClosure.Input): Consequence[V.WorkflowHostAuthorityContext] = for {
    owner <- _owner_c(core)
    _ <- if (input != null) { Consequence.unit } else { _invalid("Step Closing input is absent") }
    binding <- ThreeWorkflowHostEvidencePorts.stepClosureBindingC(core, input)
    context <- _context_c(NativeHostEvidence.Purpose.StepClosure, owner, binding)
  } yield context

  def phaseC(core: ActionCall.Core, input: GoalPhaseNativeRepositoryClosure.Input): Consequence[V.WorkflowHostAuthorityContext] = for {
    owner <- _owner_c(core)
    _ <- if (input != null) { Consequence.unit } else { _invalid("Phase Closing input is absent") }
    binding <- ThreeWorkflowHostEvidencePorts.phaseClosureBindingC(core, input)
    context <- _context_c(NativeHostEvidence.Purpose.PhaseClosure, owner, binding)
  } yield context

  private def _owner_c(core: ActionCall.Core): Consequence[String] = {
    Option(core).flatMap(_.component).map(_.core.instanceId.canonicalKey).filter(_exact)
      .map(Consequence.success).getOrElse(_invalid("Closing target has no actual owning component"))
  }
  private def _context_c(purpose: NativeHostEvidence.Purpose, owner: String, binding: Json): Consequence[V.WorkflowHostAuthorityContext] = for {
    scope <- binding.hcursor.downField("scope").focus.map(Consequence.success)
      .getOrElse(_invalid("Closing target has no selected scope"))
    selected <- new RecordDecoder().json(scope.noSpaces)
  } yield V.WorkflowHostAuthorityContext(purpose.identity, owner, selected)

  private def _exact(value: String): Boolean = {
    value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  }
  private def _invalid[A](reason: String): Consequence[A] = { Consequence.stateConflict(reason) }
}
