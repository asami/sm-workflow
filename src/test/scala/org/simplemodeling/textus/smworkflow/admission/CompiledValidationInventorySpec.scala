package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Path
import scala.concurrent.duration.*
import org.goldenport.cncf.validation.{ValidationDiscoveryV1 as D, ValidationMetadataV1 as M}
import org.goldenport.cncf.validation.annotation.ValidationSpecification
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration

/** @since Oct. 30, 2026
  * @version Oct. 30, 2026
  * @author ASAMI, Tomoharu
  */
@ValidationSpecification(declaration = classOf[LocalTestDeclaration])
final class CompiledValidationInventorySpec extends AnyWordSpec with Matchers {
  private val C = CompiledValidationInventory
  private val suite = "fixture.SelectedSpec"
  private val leaf = D.Operation(D.OperationReference("fixture", "selected", "first"),
    D.Provenance("tests", "1", "compiled-source", "leaf-1"), D.ScalaTestSelector(suite, "first"),
    Some(M.Metadata(Set.empty, Set.empty, Some(2.seconds), M.DurationClass.NORMAL, None, M.Origins(true, false))), Vector.empty)
  private val scope = D.DiscoveryScope("fixture", "1", Vector(D.ArtifactScope(
    "fixture", "tests", "1", "compiled-source", Path.of("target"), "fixture")))
  private val inventory = D.Inventory(scope, D.Completeness.COMPLETE, Vector(leaf), Vector.empty)

  "Compiled canonical inventory transport" should {
    "preserve resolved leaf duration and bind observation to the current candidate source" in {
      val selected = C.selectedC(C.encode(inventory), "candidate-tree", Vector(suite), None).toOption.get
      selected.head.selector shouldBe leaf.selector
      selected.head.metadata.get.expectedDuration shouldBe Some(2.seconds)
      selected.head.metadata.get.purposes shouldBe empty
      selected.head.provenance.sourceRevision shouldBe "candidate-tree"
      val plan = ValidationEstimateDeadline.planC(selected, "changed ordinary leaf",
        ValidationEstimateDeadline.Preparation(1.second, Duration.Zero, Duration.Zero, 1.second)).toOption.get
      ValidationEstimateDeadline.executionEstimateC(plan).toOption.get.timeoutMillisC.toOption shouldBe Some(6000L)
    }
    "refuse empty, ambiguous, missing and incomplete required selections" in {
      Vector(inventory.copy(operations = Vector.empty), inventory.copy(operations = Vector(leaf, leaf)),
        inventory.copy(operations = Vector(leaf.copy(metadata = None))),
        inventory.copy(diagnostics = Vector(D.Diagnostic("enumeration", "fixture", "failed")))).foreach { bad =>
        C.selectedC(C.encode(bad), "source", Vector(suite), None).isSuccess shouldBe false
      }
      C.selectedC(C.encode(inventory), "source", Vector("fixture.AbsentSpec"), None).isSuccess shouldBe false
      C.selectedC(C.encode(inventory), "source", Vector(suite), Some("absent")).isSuccess shouldBe false
    }
    "retain the global completeness limit while admitting only an unaffected complete selected projection" in {
      val partial = inventory.copy(completeness = D.Completeness.PARTIAL,
        diagnostics = Vector(D.Diagnostic("unsupported-suite", "fixture.OtherSpec", "requires arguments")))
      C.encode(partial) should include("PARTIAL")
      C.selectedC(C.encode(partial), "source", Vector(suite), None).isSuccess shouldBe true
      val affected = partial.copy(diagnostics = Vector(D.Diagnostic("unsupported-suite", suite, "unsupported")))
      C.selectedC(C.encode(affected), "source", Vector(suite), None).isSuccess shouldBe false
    }
  }
}
