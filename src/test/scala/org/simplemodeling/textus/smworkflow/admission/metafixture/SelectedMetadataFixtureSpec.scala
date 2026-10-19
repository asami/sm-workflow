package org.simplemodeling.textus.smworkflow.admission.metafixture

import scala.concurrent.duration.*
import org.goldenport.cncf.validation.ValidationDeclarationProvider
import org.goldenport.cncf.validation.ValidationMetadataV1.*
import org.goldenport.cncf.validation.annotation.{ValidationScenario, ValidationSpecification}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Public local declaration providers and three real, isolated registrations.
  * @since   Oct. 18, 2026
  * @version Oct. 18, 2026
  * @author  ASAMI, Tomoharu
  */
class SelectedMetadataEmptyDeclarations extends ValidationDeclarationProvider {
  def declaration: Declaration = Declaration()
}

/** @since Oct. 18, 2026
  * @version Oct. 18, 2026
  * @author ASAMI, Tomoharu
  */
class SelectedMetadataSmokeDeclarations extends ValidationDeclarationProvider {
  def declaration: Declaration = Declaration(purposes = Some(Set(Purpose.SMOKE)),
    features = Set(FeatureReference("component:sm-workflow-fixture", "feature-a")))
}

/** @since Oct. 18, 2026
  * @version Oct. 18, 2026
  * @author ASAMI, Tomoharu
  */
class SelectedMetadataFocusedDeclarations extends ValidationDeclarationProvider {
  def declaration: Declaration = Declaration(purposes = Some(Set(Purpose.FOCUSED)),
    features = Set(FeatureReference("component:sm-workflow-fixture", "feature-b")),
    expectedDuration = Some(500.millis), durationClass = Some(DurationClass.NORMAL),
    executionRequirement = Some(ExecutionRequirement(Vector(CapabilityRequirement("metadata-fixture")),
      RiskLevel("local"), ReasoningLevel.Standard, reviewRequired = false)))
}

/** Annotation anchors associate metadata with exact registrations without running them.
  * @since   Oct. 18, 2026
  * @version Oct. 18, 2026
  * @author  ASAMI, Tomoharu
  */
@DoNotDiscover
@ValidationSpecification(identity = "metadata-fixture", revision = "1",
  declaration = classOf[SelectedMetadataEmptyDeclarations])
class SelectedMetadataFixtureSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  @ValidationScenario(name = "metadata input should run smoke feature a", identity = "smoke-a",
    declaration = classOf[SelectedMetadataSmokeDeclarations])
  def smokeAnchor(): Unit = ()

  @ValidationScenario(name = "metadata input should run focused feature b", identity = "focused-b",
    declaration = classOf[SelectedMetadataFocusedDeclarations])
  def focusedAnchor(): Unit = ()

  "metadata input" should {
    "run smoke feature a" in {
      Given("two small integers")
      val inputs = Vector(2, 3)
      When("their sum is calculated")
      val result = inputs.sum
      Then("the smoke calculation retains both values")
      result shouldBe 5
    }

    "run focused feature b" in {
      Given("three small integers")
      val inputs = Vector(2, 3, 4)
      When("their product is calculated")
      val result = inputs.product
      Then("the focused calculation multiplies every value")
      result shouldBe 24
    }

    "retain unannotated supplement" in {
      Given("a repeated sequence without a scenario metadata anchor")
      val inputs = Vector(1, 2, 2, 3)
      When("distinct values are retained")
      val result = inputs.distinct
      Then("the ordinary registration has its expected calculation")
      result shouldBe Vector(1, 2, 3)
    }
  }
}
