package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Path
import org.goldenport.cncf.validation.{ValidationDiscoveryV1 as D, ValidationMetadataV1 as M}
import org.goldenport.cncf.validation.annotation.ValidationSpecification
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.testing.{LabelledTestSelection, LocalTestDeclaration}

/** Metadata-driven discovery, exclusion and explicit selection without executing HEAVY bodies.
  * @since   Oct. 24, 2026
  * @version Oct. 27, 2026
  */
@ValidationSpecification(declaration = classOf[LocalTestDeclaration])
final class ValidationTestPolicySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _operation(purposes: Set[M.Purpose], external: Boolean = false): D.Operation = {
    val declaration = M.Declaration(purposes = Some(purposes), executionRequirement = Some(
      ExecutionRequirement(if (external) Vector(CapabilityRequirement(ValidationTestPolicy.externalServiceCapability))
        else Vector.empty, RiskLevel("local"), ReasoningLevel.Standard, false)))
    D.Operation(D.OperationReference("component:test", "test", "test"),
      D.Provenance("test", "1", "1", "1"), D.ScalaTestSelector("test.Spec", "test"),
      M.resolveC(Some(declaration), None).toOption, Vector.empty)
  }

  "The shared FULL boundary" should {
    "preserve colliding test names per suite and derive preparation after operation filtering" in {
      Given("two suites sharing a name and an unselected runtime operation in the first suite")
      val local = _operation(Set(M.Purpose.FULL))
      val first = local.copy(selector = D.ScalaTestSelector("fixture.First", "local"))
      val runtime = local.copy(selector = D.ScalaTestSelector("fixture.First", "shared"), metadata =
        M.resolveC(Some(M.Declaration(purposes = Some(Set(M.Purpose.HEAVY)), executionRequirement = Some(
          ExecutionRequirement(Vector(CapabilityRequirement(ValidationTestPolicy.runtimeCapability)),
            RiskLevel("local"), ReasoningLevel.Standard, false)))), None).toOption)
      val second = local.copy(selector = D.ScalaTestSelector("fixture.Second", "shared"))
      When("FULL-selected operations form execution groups and an explicit local-name filter is applied")
      val full = ValidationTestPolicy.executionsC(Vector(first, second), Set.empty, Vector.empty).toOption.get
      val filtered = ValidationTestPolicy.executionsC(Vector(first, runtime), Set("local"), Vector.empty).toOption.get
      Then("the first suite never receives the second suite's name and removed operations require no preparation")
      full.map(value => value.suite -> value.operations.map(_.selector.testName)).toMap shouldBe
        Map("fixture.First" -> Vector("local"), "fixture.Second" -> Vector("shared"))
      filtered.map(_.suite) shouldBe Vector("fixture.First")
      filtered.head.requiresRuntime shouldBe false
    }

    "reject contradictory HEAVY and external-service metadata while allowing explicit HEAVY" in {
      Given("canonical declarations containing forbidden FULL combinations")
      val heavy = _operation(Set(M.Purpose.FULL, M.Purpose.HEAVY))
      val external = _operation(Set(M.Purpose.FULL), external = true)
      When("the consumer admits the same operations for FULL and explicit HEAVY")
      val full = D.SelectionQuery(Set(M.Purpose.FULL))
      val heavyfull = ValidationTestPolicy.admitC(full, Vector(heavy))
      val externalfull = ValidationTestPolicy.admitC(full, Vector(external))
      val explicit = ValidationTestPolicy.admitC(D.SelectionQuery(Set(M.Purpose.HEAVY)), Vector(heavy))
      Then("neither contradiction is a FULL pass and HEAVY remains explicitly selectable")
      heavyfull.toOption shouldBe None
      externalfull.toOption shouldBe None
      explicit.toOption shouldBe Some(())
    }

    "retain local SQLite and same-JVM loopback eligibility without requiring runtime artifacts" in {
      Given("local behavior with no external-service requirement")
      val local = _operation(Set(M.Purpose.FULL, M.Purpose.FOCUSED))
      When("FULL applies the shared policy")
      val result = ValidationTestPolicy.admitC(D.SelectionQuery(Set(M.Purpose.FULL)), Vector(local))
      Then("local resource use does not create an invented external-service restriction")
      result.toOption shouldBe Some(())
      ValidationTestPolicy.requiresRuntime(local) shouldBe false
    }
  }

  "Canonical discovery through the build bridge" should {
    "honor an explicit installed suite while retaining automatic discovery exclusions" in {
      Given("the actual explicit-only installed qualification and an ordinary local suite")
      val root = Path.of(getClass.getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath().toString
      val installed = classOf[NativePhase68DeliveryCliSpec].getName
      val declared = new NativePhase68DeliveryCliSpec().testNames
      val local = getClass.getName
      val heavy = classOf[NativeGoalDependencyValidationCliSpec].getName
      When("the bridge selects explicit names and automatic FULL or HEAVY purposes without executing bodies")
      val explicit = LabelledTestSelection.select(root, "test", "EXPLICIT", Array(installed))
      val full = LabelledTestSelection.select(root, "test", "FULL", Array(installed, local))
      val expensive = LabelledTestSelection.select(root, "test", "HEAVY", Array(installed, heavy))
      Then("all installed registrations are explicitly runnable and retain their runtime classification")
      declared.size shouldBe 5
      explicit.map(row => row(0) -> row(1)).toSet shouldBe declared.map(installed -> _)
      explicit.forall(_(2) == "true") shouldBe true
      And("automatic selections preserve their normal memberships and do not acquire the installed suite")
      full.map(_(0)).toSet shouldBe Set(local)
      expensive.map(_(0)).toSet shouldBe Set(heavy)
    }

    "retain exact-name and substring selection as suite-local operations through the real bridge" in {
      val root = Path.of(getClass.getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath().toString
      val report = classOf[GoalPhaseStepTestReportRepairSpec].getName
      val progression = classOf[GoalPhaseOrderedProgressionSpec].getName
      val expected = new GoalPhaseStepTestReportRepairSpec().testNames.map(report -> _) ++
        new GoalPhaseOrderedProgressionSpec().testNames.map(progression -> _)
      val exact = new GoalPhaseStepTestReportRepairSpec().testNames.toVector.sorted.head
      val classes = Array(report, progression)
      val exactRows = LabelledTestSelection.select(root, "test", "EXPLICIT", classes, Array(exact), Array.empty[String])
      val containingRows = LabelledTestSelection.select(root, "test", "EXPLICIT", classes, Array.empty[String], Array("repair"))
      exactRows.map(row => row(0) -> row(1)).toSet shouldBe expected.filter(_._2 == exact)
      containingRows.map(row => row(0) -> row(1)).toSet shouldBe expected.filter(_._2.contains("repair"))
      exactRows should not be empty
      containingRows should not be empty
      (exactRows ++ containingRows).foreach(row => expected should contain(row(0) -> row(1)))
      (exactRows ++ containingRows).forall(_(2) == "false") shouldBe true
    }

    "select ADMISSION without changing FULL or admitting HEAVY" in {
      Given("real admission, ordinary local and HEAVY declarations")
      val root = Path.of(getClass.getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath().toString
      val admission = classOf[GoalPhaseOrderedProgressionSpec].getName
      val local = getClass.getName
      val heavy = classOf[NativeGoalDependencyValidationCliSpec].getName
      val candidates = Array(admission, local, heavy)
      When("each purpose selects independently through canonical discovery")
      val selected = LabelledTestSelection.select(root, "test", "ADMISSION", candidates)
      val full = LabelledTestSelection.select(root, "test", "FULL", candidates)
      val expensive = LabelledTestSelection.select(root, "test", "HEAVY", candidates)
      Then("ADMISSION is nonempty and local, while existing FULL and HEAVY memberships remain")
      selected.map(_(0)).toSet shouldBe Set(admission)
      selected.map(_(1)).toSet shouldBe new GoalPhaseOrderedProgressionSpec().testNames
      selected.forall(_(2) == "false") shouldBe true
      full.map(_(0)).toSet shouldBe Set(admission, local)
      expensive.map(_(0)).toSet shouldBe Set(heavy)
    }

    "discover the dependency CLI without manual enrollment and exclude it from FULL" in {
      Given("the actual compiled test artifact and two explicitly labelled suites")
      val root = Path.of(getClass.getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath().toString
      val dependency = classOf[NativeGoalDependencyValidationCliSpec].getName
      val declared = new NativeGoalDependencyValidationCliSpec().testNames
      val local = getClass.getName
      When("the canonical producer selects explicit, HEAVY and FULL operations")
      val explicit = LabelledTestSelection.select(root, "test", "EXPLICIT", Array(dependency))
      val heavy = LabelledTestSelection.select(root, "test", "HEAVY", Array(dependency))
      val full = LabelledTestSelection.select(root, "test", "FULL", Array(dependency, local))
      Then("the real registrations are nonempty, preparation is declared and FULL keeps only local work")
      explicit should not be empty
      explicit.map(row => row(0) -> row(1)).toSet shouldBe declared.map(dependency -> _)
      heavy.map(_.toVector).toVector shouldBe explicit.map(_.toVector).toVector
      explicit.forall(_(2) == "true") shouldBe true
      full.map(_(0)).toSet shouldBe Set(local)
    }

    "reject empty explicit selection and preserve intentional helper exclusion" in {
      Given("an actual helper suite retained only for input/discovery contracts")
      val root = Path.of(getClass.getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath().toString
      val helper = classOf[metafixture.SelectedMetadataFixtureSpec].getName
      When("a routine build selection requests only that helper or no suite")
      val helperresult = scala.util.Try(LabelledTestSelection.select(root, "test", "EXPLICIT", Array(helper)))
      val emptyresult = scala.util.Try(LabelledTestSelection.select(root, "test", "EXPLICIT", Array.empty[String]))
      Then("both requests fail visibly instead of reporting a zero-test pass")
      helperresult.failed.get shouldBe a[IllegalArgumentException]
      emptyresult.failed.get shouldBe a[IllegalArgumentException]
    }
  }
}
