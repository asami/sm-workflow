package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.validation.{ValidationDiscoveryV1 as D, ValidationMetadataV1 as M}

/** Consumer FULL boundary over canonical CNCF metadata, shared with the SBT adapter.
  * @since Oct. 24, 2026
  * @version Oct. 24, 2026
  */
private[smworkflow] object ValidationTestPolicy {
  val runtimeCapability = "sm-workflow.runtime"
  val externalServiceCapability = "external-service"

  final case class Execution(suite: String, operations: Vector[D.Operation]) {
    def requiresRuntime: Boolean = operations.exists(ValidationTestPolicy.requiresRuntime)
  }

  def executionsC(operations: Vector[D.Operation], exact: Set[String], containing: Vector[String]):
      Consequence[Vector[Execution]] = {
    val selected = operations.filter(operation => exact.isEmpty && containing.isEmpty ||
      exact(operation.selector.testName) || containing.exists(operation.selector.testName.contains))
    if (selected.isEmpty) Consequence.stateConflict("No labelled runnable operation selected; an empty run is not PASS")
    else Consequence.success(selected.groupBy(_.selector.suiteClassName).toVector.sortBy(_._1)
      .map { case (suite, values) => Execution(suite, values) })
  }

  def requiresRuntime(operation: D.Operation): Boolean = operation.metadata.exists(
    _.executionRequirement.exists(_.capabilities.exists(_.identity == runtimeCapability)))

  def fullConflict(operation: D.Operation): Boolean = operation.metadata.exists { metadata =>
    metadata.purposes.contains(M.Purpose.FULL) && (
      metadata.purposes.contains(M.Purpose.HEAVY) ||
        metadata.executionRequirement.exists(_.capabilities.exists(_.identity == externalServiceCapability)))
  }

  def admitC(query: D.SelectionQuery, operations: Vector[D.Operation]): Consequence[Unit] =
    if (!query.purposes.contains(M.Purpose.FULL)) Consequence.unit
    else if (operations.exists(operation => fullConflict(operation) || operation.metadata.exists { metadata =>
        metadata.purposes.contains(M.Purpose.HEAVY) ||
          metadata.executionRequirement.exists(_.capabilities.exists(_.identity == externalServiceCapability))
      })) Consequence.stateConflict("FULL excludes HEAVY and external-service requirements; correct contradictory metadata or select an explicit separate run")
    else Consequence.unit
}
