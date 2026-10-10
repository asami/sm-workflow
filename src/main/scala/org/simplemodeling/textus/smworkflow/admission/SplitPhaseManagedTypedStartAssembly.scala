package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.configuration.{ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.spec as spec

/**
 * Post-resolution service assembly only; the Subsystem already owns its pool.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object SplitPhaseManagedTypedStartAssembly {
  final class InvocationRevision private[SplitPhaseManagedTypedStartAssembly] (val value: String)
  final case class Host(evidence: SplitPhaseTypedStartHumanVerification.EvidencePort)
  val revisionKey = SplitPhaseTypedStartReadPolicy.prefix + "invocation-record-revision"
  def revisionC(host: ResolvedConfiguration): Consequence[InvocationRevision] =
    if (host == null) Consequence.configurationInvalid("SplitPhase typed invocation revision is absent")
    else GoalPhaseHostRecordReadPolicy.storedValue(host.configuration.values, revisionKey) match {
      case Some(ConfigurationValue.StringValue(value)) if value != null && value.nonEmpty && value.trim == value =>
        Consequence.success(new InvocationRevision(value))
      case _ => Consequence.configurationInvalid("SplitPhase typed invocation requires its explicitly selected host record revision")
    }
  def bindHostC(params: ComponentCreate, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, host: Host)(using ExecutionContext): Consequence[spec.OperationDefinition] =
    if (params == null || host == null || host.evidence == null)
      Consequence.configurationInvalid("SplitPhase typed public Start requires explicit native host dependencies")
    else for {
      revision <- revisionC(params.configuration)
      inputs <- SplitPhaseTypedStartReadPolicy.bindC(params.configuration, declaration, catalog)
      binding <- SplitPhaseManagedStartAssembly.bindPersistenceC(params, declaration, catalog)
      instances = CallScopedWorkflowInstances.managed(declaration.configuration, binding.definition,
        declaration.instances, declaration.continuations)
      operation <- SplitPhaseTypedStartServiceOperation.bindC(inputs, revision, host, instances)
    } yield operation
}
