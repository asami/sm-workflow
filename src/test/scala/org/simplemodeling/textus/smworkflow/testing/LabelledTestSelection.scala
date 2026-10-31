package org.simplemodeling.textus.smworkflow.testing

import java.nio.file.Path
import org.goldenport.cncf.validation.{ValidationDiscoveryV1 as D, ValidationMetadataV1 as M}
import org.scalatest.DoNotDiscover
import org.simplemodeling.textus.smworkflow.admission.ValidationTestPolicy

/** Java-only handback for the Scala 2 SBT build, using the Scala 3 CNCF producer.
  * This adapter owns no suite catalog, annotation interpreter or metadata merge.
  * @since   Oct. 24, 2026
  * @version Oct. 27, 2026
  */
object LabelledTestSelection {
  def select(root: String, revision: String, purpose: String, suites: Array[String],
    exact: Array[String], containing: Array[String], explicitSuites: Array[String]): Array[Array[String]] = {
    val loader = getClass.getClassLoader
    val scope = D.DiscoveryScope("sm-workflow-tests", "1", Vector(D.ArtifactScope(
      "component:sm-workflow", "sm-workflow-tests", revision, "working-tree",
      Path.of(root).toRealPath(), "org.simplemodeling.textus.smworkflow")))
    val inventory = D.discoverC(scope, loader).fold(
      failure => throw new IllegalStateException(s"Canonical CNCF test discovery failed: $failure"), identity)
    // DoNotDiscover excludes automatic purpose discovery, not a caller's actual
    // explicit suite selection. Metadata and nonempty-operation admission still
    // apply to every explicitly selected suite below.
    val candidates = suites.toSet.filter(name => purpose == "EXPLICIT" ||
      !Class.forName(name, false, loader).isAnnotationPresent(classOf[DoNotDiscover]))
    val relevant = inventory.operations.filter(operation => candidates(operation.selector.suiteClassName))
    val missing = candidates -- relevant.map(_.selector.suiteClassName).toSet
    val diagnostics = inventory.diagnostics.filter(d => candidates(d.source) ||
      Set("enumeration", "truncated")(d.code)) ++ relevant.flatMap(_.diagnostics)
    require(missing.isEmpty && diagnostics.isEmpty,
      s"Incomplete selected test discovery: missing=$missing diagnostics=$diagnostics")
    require(relevant.forall(_.metadata.exists(_.purposes.nonEmpty)),
      "Unclassified runnable test: declare canonical ValidationSpecification metadata")
    val query = D.SelectionQuery(purposes = if (purpose == "EXPLICIT") Set.empty else Set(M.Purpose.valueOf(purpose)))
    // Select only the explicit SBT candidate projection. Negative metadata fixtures
    // outside it cannot invalidate a normal run. Keep the original completeness;
    // this projection makes no claim that the global inventory is COMPLETE.
    val candidateinventory = inventory.copy(operations = relevant, diagnostics = diagnostics)
    // The producer remains responsible for query semantics and class/operation composition.
    val selected = D.selectC(candidateinventory, query).fold(
      failure => throw new IllegalStateException(s"Canonical CNCF test selection failed: $failure"), identity)
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
