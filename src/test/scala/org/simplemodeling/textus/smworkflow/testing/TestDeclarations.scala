package org.simplemodeling.textus.smworkflow.testing

import org.goldenport.cncf.validation.ValidationDeclarationProvider
import org.goldenport.cncf.validation.ValidationMetadataV1.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*

/** Source-audited local behavior: in-memory values, owned files/Git and SQLite.
  * No external server, provider or nested SBT is a prerequisite.
  * @since Oct. 24, 2026
  * @version Oct. 24, 2026
  */
class LocalTestDeclaration extends ValidationDeclarationProvider {
  def declaration: Declaration = Declaration(purposes = Some(Set(Purpose.FOCUSED, Purpose.FULL)),
    features = Set(FeatureReference("component:sm-workflow", "local-validation")),
    durationClass = Some(DurationClass.NORMAL),
    executionRequirement = Some(ExecutionRequirement(Vector.empty, RiskLevel("local"),
      ReasoningLevel.Standard, reviewRequired = false)))
}

/** Multi-process CLI/SBT scenarios have variable, substantial execution cost.
  * Artifact preparation is a separate requirement, not the reason for HEAVY.
  * @since Oct. 24, 2026
  * @version Oct. 24, 2026
  */
class RuntimeTestDeclaration extends ValidationDeclarationProvider {
  def declaration: Declaration = Declaration(purposes = Some(Set(Purpose.FOCUSED, Purpose.HEAVY)),
    features = Set(FeatureReference("component:sm-workflow", "native-workflows")),
    durationClass = Some(DurationClass.LONG_RUNNING),
    executionRequirement = Some(ExecutionRequirement(Vector(CapabilityRequirement("sm-workflow.runtime")),
      RiskLevel("local"), ReasoningLevel.Standard, reviewRequired = false)))
}

/** Separately started/required services remain outside default/FULL.
  * @since Oct. 24, 2026
  * @version Oct. 24, 2026
  */
class ExternalServiceTestDeclaration extends RuntimeTestDeclaration {
  override def declaration: Declaration = super.declaration.copy(executionRequirement =
    super.declaration.executionRequirement.map(value => value.copy(capabilities =
      value.capabilities :+ CapabilityRequirement("external-service"))))
}

/** Two-repository dependency continuation through many ordinary CLI invocations.
  * @since Oct. 24, 2026
  * @version Oct. 24, 2026
  */
class DependencyValidationTestDeclaration extends RuntimeTestDeclaration {
  override def declaration: Declaration = super.declaration.copy(features =
    Set(FeatureReference("component:sm-workflow", "dependency-validation")))
}
