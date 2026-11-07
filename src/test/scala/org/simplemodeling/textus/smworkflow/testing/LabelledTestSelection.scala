package org.simplemodeling.textus.smworkflow.testing

import java.nio.file.Path
import org.goldenport.cncf.validation.{ValidationDiscoveryV1 as D, ValidationMetadataV1 as M}
import org.scalatest.DoNotDiscover
import org.simplemodeling.textus.smworkflow.admission.{ValidationTestPolicy, CompiledValidationInventory}

/** Java-only handback for the Scala 2 SBT build, using the Scala 3 CNCF producer.
  * This adapter owns no suite catalog, annotation interpreter or metadata merge.
  * @since   Oct. 24, 2026
  *  version Oct. 31, 2026
  * @version Nov.  8, 2026
  */
object LabelledTestSelection {
  def inventory(root: String, revision: String): String = {
    val scope = D.DiscoveryScope("sm-workflow-tests", "1", Vector(D.ArtifactScope(
      "component:sm-workflow", "sm-workflow-tests", revision, "compiled-working-source",
      Path.of(root).toRealPath(), "org.simplemodeling.textus.smworkflow")))
    val discovered = D.discoverC(scope, getClass.getClassLoader).fold(
      failure => throw new IllegalStateException(s"Canonical CNCF discovery failed: $failure"), identity)
    CompiledValidationInventory.encode(discovered)
  }
  def select(root: String, revision: String, purpose: String, suites: Array[String],
    exact: Array[String], containing: Array[String], explicitSuites: Array[String]): Array[Array[String]] = {
    val loader = getClass.getClassLoader
    val scope = D.DiscoveryScope("sm-workflow-tests", "1", Vector(D.ArtifactScope(
      "component:sm-workflow", "sm-workflow-tests", revision, "working-tree",
      Path.of(root).toRealPath(), "org.simplemodeling.textus.smworkflow")))
    val inventory = D.discoverC(scope, loader,
      if (Set("EXPLICIT", "FOCUSED")(purpose)) explicitSuites.toSet else Set.empty[String]).fold(
      failure => throw new IllegalStateException(s"Canonical CNCF test discovery failed: $failure"), identity)
    // DoNotDiscover excludes automatic purpose discovery, not a caller's actual
    // explicit suite selection. Metadata and nonempty-operation admission still
    // apply to every explicitly selected suite below.
    val candidates = suites.toSet.filter(name =>
      !Class.forName(name, false, loader).isAnnotationPresent(classOf[DoNotDiscover]) ||
        (Set("EXPLICIT", "FOCUSED")(purpose) && explicitSuites.contains(name)))
    val relevant = inventory.operations.filter(operation => candidates(operation.selector.suiteClassName))
    val missing = candidates -- relevant.map(_.selector.suiteClassName).toSet
    val diagnostics = inventory.diagnostics.filter(d => candidates(d.source) ||
      Set("enumeration", "truncated")(d.code)) ++ relevant.flatMap(_.diagnostics)
    require(missing.isEmpty && diagnostics.isEmpty,
      s"Incomplete selected test discovery: missing=$missing diagnostics=$diagnostics")
    require(relevant.forall(_.metadata.nonEmpty),
      "Runnable test has invalid canonical metadata; ordinary tests need no purpose label")
    val query = D.SelectionQuery(purposes = if (Set("EXPLICIT", "FOCUSED", "FULL")(purpose)) Set.empty else Set(M.Purpose.valueOf(purpose)))
    // Select only the explicit SBT candidate projection. Negative metadata fixtures
    // outside it cannot invalidate a normal run. Keep the original completeness;
    // this projection makes no claim that the global inventory is COMPLETE.
    val candidateinventory = inventory.copy(operations = relevant, diagnostics = diagnostics)
    // The producer remains responsible for query semantics and class/operation composition.
    val candidatesSelected = D.selectC(candidateinventory, query).fold(
      failure => throw new IllegalStateException(s"Canonical CNCF test selection failed: $failure"), identity)
    val selected = if (purpose == "FULL")
      ValidationTestPolicy.fullC(candidatesSelected).toOption.getOrElse(throw new IllegalArgumentException(
        "Contradictory FULL metadata")) else candidatesSelected
    require(ValidationTestPolicy.admitC(query, selected).toOption.isDefined,
      "FULL classification conflict: HEAVY/external-service work must be a separate explicit selection")
    val executions = ValidationTestPolicy.executionsC(selected, exact.toSet, containing.toVector).toOption
      .getOrElse(throw new IllegalArgumentException("No labelled runnable operation selected; an empty run is not PASS"))
    executions.flatMap(_.operations).map(operation => Array(operation.selector.suiteClassName,
      operation.selector.testName, ValidationTestPolicy.requiresRuntime(operation).toString)).toArray
  }

  def select(root: String, revision: String, purpose: String, suites: Array[String],
    exact: Array[String], containing: Array[String]): Array[Array[String]] =
    select(root, revision, purpose, suites, exact, containing,
      if (Set("EXPLICIT", "FOCUSED")(purpose)) suites else Array.empty[String])

  def select(root: String, revision: String, purpose: String, suites: Array[String]): Array[Array[String]] =
    select(root, revision, purpose, suites, Array.empty[String], Array.empty[String])
}
