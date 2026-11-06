package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.validation.{ValidationDiscoveryV1 as D, ValidationMetadataV1 as M}

/** Consumer FULL boundary over canonical CNCF metadata, shared with the SBT adapter.
  * @since Oct. 24, 2026
  * @version Oct. 30, 2026
  */
private[smworkflow] object ValidationTestPolicy {
  val runtimeCapability = "sm-workflow.runtime"
  val externalServiceCapability = "external-service"

  final case class Execution(suite: String, operations: Vector[D.Operation]) {
    def requiresRuntime: Boolean = operations.exists(ValidationTestPolicy.requiresRuntime)
  }

  final case class FocusedSelection(reason: String, query: D.SelectionQuery,
    selectors: Set[D.ScalaTestSelector], operations: Vector[D.Operation])

  /** FOCUSED selects actual leaves for a change. It never queries a FOCUSED
    * membership or changes the selected operation's classification.
    */
  def focusedC(inventory: D.Inventory, selectors: Set[D.ScalaTestSelector],
    features: Set[M.FeatureReference], reason: String): Consequence[FocusedSelection] = for {
    _ <- if (inventory != null && inventory.completeness == D.Completeness.COMPLETE &&
        inventory.diagnostics != null && inventory.diagnostics.isEmpty && selectors != null &&
        features != null && (selectors.nonEmpty || features.nonEmpty) && reason != null && reason.trim.nonEmpty)
      Consequence.unit else Consequence.stateConflict("FOCUSED needs a complete inventory, actual selection conditions and reason")
    query = D.SelectionQuery(features = features)
    candidates <- D.selectC(inventory, query)
    selected = candidates.filter(operation => selectors.isEmpty || selectors(operation.selector))
    _ <- if (selected.nonEmpty && selected.forall(o => o.diagnostics.isEmpty) &&
        selectors.subsetOf(selected.map(_.selector).toSet) &&
        selected.map(_.selector).distinct.size == selected.size) Consequence.unit
      else Consequence.stateConflict("FOCUSED selection is empty, incomplete or ambiguous; no requested leaf may disappear")
  } yield FocusedSelection(reason, query, selectors, selected)

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

  /** Ordinary and ADMISSION operations enter FULL without an authored FULL
    * membership. Contradictory declarations fail rather than disappear.
    */
  def fullC(operations: Vector[D.Operation]): Consequence[Vector[D.Operation]] =
    if (operations == null || operations.exists(fullConflict))
      Consequence.stateConflict("FULL metadata contradicts HEAVY or external-service requirements")
    else Consequence.success(operations.filterNot(_.metadata.exists(metadata =>
      metadata.purposes.contains(M.Purpose.HEAVY) || metadata.executionRequirement.exists(
        _.capabilities.exists(_.identity == externalServiceCapability)))))

  def admitC(query: D.SelectionQuery, operations: Vector[D.Operation]): Consequence[Unit] =
    if (!query.purposes.contains(M.Purpose.FULL)) Consequence.unit
    else if (operations.exists(operation => fullConflict(operation) || operation.metadata.exists { metadata =>
        metadata.purposes.contains(M.Purpose.HEAVY) ||
          metadata.executionRequirement.exists(_.capabilities.exists(_.identity == externalServiceCapability))
      })) Consequence.stateConflict("FULL excludes HEAVY and external-service requirements; correct contradictory metadata or select an explicit separate run")
    else Consequence.unit
}
