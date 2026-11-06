package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path}
import org.goldenport.cncf.validation.annotation.ValidationSpecification
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration

/** Isolation of test arrangement only; no publication/activation receipt is asserted. */
@ValidationSpecification(declaration = classOf[LocalTestDeclaration])
final class NativeWorkflowFixtureIsolationSpec extends AnyWordSpec with Matchers {
  "One validation preparation" should {
    "share only its baseline and prepare mutable locations independently for each scenario" in {
      val parent = Path.of("target", "sm-workflow", "fixture-isolation").toAbsolutePath.normalize
      Files.createDirectories(parent)
      val root = Files.createTempDirectory(parent, "run-")
      val image = Files.createDirectory(root.resolve("baseline-image"))
      val input = Files.writeString(root.resolve("common-input.txt"), "immutable common input\n")
      var preparations = 0
      val run = SharedValidationPreparation.prepare(root) {
        preparations += 1
        SharedValidationPreparation.Baseline(image, Vector(input))
      }
      val first = run.scenario("first")
      val second = run.scenario("second")
      preparations shouldBe 1
      first.baseline should be theSameInstanceAs second.baseline
      val firstMutable = Vector(first.repository, first.configuration, first.stores, first.selection, first.publication)
      val secondMutable = Vector(second.repository, second.configuration, second.stores, second.selection, second.publication)
      firstMutable.zip(secondMutable).foreach { case (one, two) =>
        one should not be two
        Files.writeString(one.resolve("state"), "first scenario only\n")
        Files.exists(two.resolve("state")) shouldBe false
      }
      Files.readString(input) shouldBe "immutable common input\n"
    }
  }
}
