package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import scala.concurrent.duration.*
import org.goldenport.Consequence
import org.goldenport.cncf.validation.{ValidationDiscoveryV1 as D, ValidationMetadataV1 as M}

/** Transport of canonical discovery performed after TestCompile, before bodies.
  * This is generated evidence, never an authored test membership registry.
  * @since   Oct. 30, 2026
  * @version Oct. 30, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object CompiledValidationInventory {
  val schema = "sm-workflow.compiled-validation-inventory.v1"
  final case class Leaf(component: String, specification: String, operation: String,
    artifact: String, artifactRevision: String, operationRevision: String,
    suite: String, test: String, purposes: Vector[String], expectedNanos: Option[Long],
    valid: Boolean)
  final case class Problem(code: String, source: String)
  final case class Inventory(schema: String, enumerated: Boolean, discoveryCompleteness: String,
    problems: Vector[Problem], leaves: Vector[Leaf])

  def encode(inventory: D.Inventory): String = Inventory(schema,
    !inventory.diagnostics.exists(d => Set("enumeration", "truncated")(d.code)),
    inventory.completeness.toString, inventory.diagnostics.map(d => Problem(d.code, d.source)),
    inventory.operations.map { operation =>
      Leaf(operation.reference.component, operation.reference.specification, operation.reference.operation,
        operation.provenance.artifactIdentity, operation.provenance.artifactRevision, operation.provenance.operationRevision,
        operation.selector.suiteClassName, operation.selector.testName,
        operation.metadata.toVector.flatMap(_.purposes.toVector.map(_.toString)).sorted,
        operation.metadata.flatMap(_.expectedDuration).map(_.toNanos),
        operation.metadata.nonEmpty && operation.diagnostics.isEmpty)
    }).asJson.noSpaces

  def selectedC(raw: String, sourceRevision: String, classes: Vector[String],
    scenario: Option[String]): Consequence[Vector[D.Operation]] = for {
    inventory <- io.circe.parser.decode[Inventory](raw).fold(
      _ => invalid("Compiled discovery output is malformed"), Consequence.success)
    _ <- if (inventory.schema == schema && inventory.enumerated && inventory.leaves != null &&
        inventory.leaves.forall(_ != null) && inventory.problems != null && inventory.problems.forall(_ != null) &&
        classes != null && classes.nonEmpty &&
        classes.distinct.size == classes.size && sourceRevision != null && sourceRevision.nonEmpty)
      Consequence.unit else invalid("Complete compiled discovery and exact source/selection are required")
    selected = inventory.leaves.filter(leaf => classes.contains(leaf.suite) && scenario.forall(_ == leaf.test))
    _ <- if (!inventory.problems.exists(p => classes.contains(p.source)) &&
        selected.nonEmpty && selected.map(_.suite).toSet == classes.toSet &&
        selected.map(leaf => (leaf.suite, leaf.test)).distinct.size == selected.size &&
        selected.forall(leaf => leaf.valid && leaf.purposes != null &&
          leaf.purposes.forall(p => M.Purpose.values.exists(_.toString == p)) && leaf.expectedNanos != null &&
          leaf.expectedNanos.forall(_ > 0))) Consequence.unit
      else invalid("Selected compiled leaves are absent, ambiguous or invalid")
  } yield selected.map { leaf =>
    D.Operation(D.OperationReference(leaf.component, leaf.specification, leaf.operation),
      D.Provenance(leaf.artifact, leaf.artifactRevision, sourceRevision, leaf.operationRevision),
      D.ScalaTestSelector(leaf.suite, leaf.test),
      Some(M.Metadata(leaf.purposes.map(M.Purpose.valueOf).toSet, Set.empty, leaf.expectedNanos.map(_.nanos),
        M.DurationClass.NORMAL, None, M.Origins(true, true))), Vector.empty)
  }

  private def invalid[A](message: String): Consequence[A] = Consequence.stateConflict("Validation preparation: " + message)
}
