package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Path
import org.goldenport.cncf.validation.{ValidationDiscoveryV1 as D, ValidationMetadataV1 as M,
  ValidationDeclarationProvider, ValidationInputsV1 as I, ValidationExecutionModeV1 as E}
import org.goldenport.cncf.processexecution.SbtScenarioParameters.Value
import org.goldenport.cncf.config.OperationMode
import org.goldenport.cncf.validation.annotation.ValidationSpecification
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.scalatest.{Args, ConfigMap, DoNotDiscover, GivenWhenThen, Outcome, Reporter}
import org.scalatest.events.{Event, TestFailed, TestSucceeded}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.testing.{LabelledTestSelection, LocalTestDeclaration}
import org.simplemodeling.textus.smworkflow.testing.metafixture.explicit.ExplicitDispatchFixtureSpec

/** Metadata-driven discovery, exclusion and explicit selection without executing HEAVY bodies.
  * @since   Oct. 24, 2026
  *  version Oct. 31, 2026
  * @version Nov.  8, 2026
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

  "Managed ConfigMap compatibility through the real build bridge" should {
    "validate the selected source mode and schema before the actual ScalaTest fixture consumes them" in {
      Given("an explicit source-declared test variant with one canonical integer input")
      val root = Path.of(getClass.getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath().toString
      val fixture = new AdapterModeInputFixtureSpec
      val suite = fixture.getClass.getName
      val leaf = fixture.testNames.head
      val properties = Array("-Dcncf.validation.mode=test", "-Dcncf.validation.provider=fixture", "-Dcncf.validation.input.count=3")
      When("the existing bridge selects the exact operation with shared framework properties")
      val selected = LabelledTestSelection.select(root, "test", "EXPLICIT", Array(suite), Array(leaf),
        Array.empty[String], Array(suite), properties)
      val events = scala.collection.mutable.ArrayBuffer.empty[Event]
      val reporter = new Reporter { def apply(event: Event): Unit = { events += event; () } }
      val status = fixture.run(Some(leaf), Args(reporter, configMap = ConfigMap(
        E.modeKey -> "test", E.providerKey -> "fixture", "cncf.validation.input.count" -> "3")))
      status.waitUntilCompleted()
      Then("selection stays suite-local and real ConfigMap consumption validates the typed source inputs")
      selected.map(row => row(0) -> row(1)).toSet shouldBe Set(suite -> leaf)
      status.succeeds() shouldBe true
      events.collect { case event: TestSucceeded => event.testName }.toVector shouldBe Vector(leaf)
    }

    "refuse unsupported property channels, mode conflicts and undeclared or malformed source inputs" in {
      Given("the same exact operation and its shared source declaration")
      val root = Path.of(getClass.getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath().toString
      val suite = classOf[AdapterModeInputFixtureSpec].getName
      val base = Array("-Dcncf.validation.mode=test", "-Dcncf.validation.provider=fixture", "-Dcncf.validation.input.count=3")
      val properties = Vector(base :+ "-Djava.home=/other", base :+ "-Dcncf.validation.input.unknown=4",
        base.updated(2, "-Dcncf.validation.input.count=3.5"), base.updated(0, "-Dcncf.validation.mode=production"),
        base :+ "-Dcncf.validation.mode=test")
      When("the actual bridge admits each argument projection before any body dispatch")
      val rejected = properties.map(values => scala.util.Try(LabelledTestSelection.select(root, "test", "EXPLICIT", Array(suite),
        Array.empty[String], Array.empty[String], Array(suite), values)))
      Then("each incompatible request fails instead of being dropped or converted to an environment override")
      rejected.foreach(_.isFailure shouldBe true)
    }

    "exclude standard Slow from ordinary FULL while retaining exact focused selection" in {
      Given("a harmless standard Slow suite and an ordinary local suite")
      val root = Path.of(getClass.getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath().toString
      val slow = classOf[AdapterSlowFixtureSpec].getName
      val local = getClass.getName
      When("FULL and an actually named focused request pass through the same bridge")
      val full = LabelledTestSelection.select(root, "test", "FULL", Array(local, slow), Array.empty[String], Array.empty[String], Array(slow))
      val focused = LabelledTestSelection.select(root, "test", "FOCUSED", Array(slow), Array.empty[String], Array.empty[String], Array(slow))
      Then("routine FULL excludes Slow and explicit focused scope retains it")
      full.map(_(0)).toSet shouldBe Set(local)
      focused.map(_(0)).toSet shouldBe Set(slow)
    }
  }

  "Canonical discovery through the build bridge" should {
    "exclude an explicit-only fixture from unnamed EXPLICIT and FOCUSED selections including substring filters" in {
      Given("an explicit-only arithmetic fixture and an ordinary local suite")
      val root = Path.of(getClass.getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath().toString
      val fixture = classOf[ExplicitDispatchFixtureSpec].getName
      val leaf = new ExplicitDispatchFixtureSpec().testNames.head
      val local = getClass.getName
      When("the seven-argument bridge receives candidates without any exact explicit suite name")
      val selections = Vector("EXPLICIT", "FOCUSED").map { purpose =>
        val rows = LabelledTestSelection.select(root, "test", purpose, Array(fixture, local),
          Array.empty[String], Array.empty[String], Array.empty[String])
        val filtered = scala.util.Try(LabelledTestSelection.select(root, "test", purpose, Array(fixture, local),
          Array.empty[String], Array(leaf), Array.empty[String]))
        rows -> filtered
      }
      Then("ordinary named suites remain selectable and a substring cannot acquire the excluded fixture")
      selections.foreach { case (rows, filtered) =>
        rows.map(_(0)).toSet shouldBe Set(local)
        filtered.failed.get shouldBe a[IllegalArgumentException]
        filtered.failed.get.getMessage should include("No labelled runnable operation selected")
      }
    }

    "exclude an explicit-only fixture from FULL ADMISSION and HEAVY even when named exactly" in {
      Given("an explicit-only fixture alongside automatically discoverable local admission and HEAVY suites")
      val root = Path.of(getClass.getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath().toString
      val fixture = classOf[ExplicitDispatchFixtureSpec].getName
      val admission = classOf[GoalPhaseOrderedProgressionSpec].getName
      val heavy = classOf[NativeGoalMetadataCliSpec].getName
      When("automatic purposes receive the fixture in both candidates and the exact explicit suite list")
      val selections = Vector("FULL", "ADMISSION", "HEAVY").map { purpose =>
        purpose -> LabelledTestSelection.select(root, "test", purpose, Array(fixture, admission, heavy),
          Array.empty[String], Array.empty[String], Array(fixture))
      }.toMap
      Then("each automatic purpose retains its ordinary membership without acquiring the fixture")
      selections("FULL").map(_(0)).toSet shouldBe Set(admission)
      selections("ADMISSION").map(_(0)).toSet shouldBe Set(admission)
      selections("HEAVY").map(_(0)).toSet shouldBe Set(heavy)
    }

    "select an exactly named explicit-only fixture for EXPLICIT and FOCUSED and dispatch its single leaf successfully" in {
      Given("one local arithmetic registration and a reporter recording actual ScalaTest events")
      val root = Path.of(getClass.getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath().toString
      val fixture = new ExplicitDispatchFixtureSpec
      val suite = fixture.getClass.getName
      val leaf = fixture.testNames.head
      val events = scala.collection.mutable.ArrayBuffer.empty[Event]
      val reporter = new Reporter {
        override def apply(event: Event): Unit = events += event
      }
      When("the bridge admits the exact suite for both purposes and ScalaTest runs one selected leaf once")
      val selections = Vector("EXPLICIT", "FOCUSED").map { purpose =>
        LabelledTestSelection.select(root, "test", purpose, Array(suite),
          Array.empty[String], Array.empty[String], Array(suite))
      }
      val selected = selections.head.head
      val status = fixture.run(Some(selected(1)), Args(reporter))
      status.waitUntilCompleted()
      Then("both selections contain only the actual local leaf and dispatch reports exactly one successful test")
      fixture.testNames shouldBe Set(leaf)
      selections.foreach(rows => rows.map(_.toVector).toVector shouldBe Vector(Vector(suite, leaf, "false")))
      events.collect { case event: TestSucceeded => event.suiteClassName -> event.testName }.toVector shouldBe
        Vector(Some(suite) -> leaf)
      events.collect { case event: TestFailed => event } shouldBe empty
      status.succeeds() shouldBe true
    }

    "honor an explicit installed suite while retaining automatic discovery exclusions" in {
      Given("the actual explicit-only installed qualification and automatically discoverable local and HEAVY suites")
      val root = Path.of(getClass.getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath().toString
      val installed = classOf[NativePhase68DeliveryCliSpec].getName
      val declared = new NativePhase68DeliveryCliSpec().testNames
      val local = getClass.getName
      val heavy = classOf[NativeGoalMetadataCliSpec].getName
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
      Given("two ordinary suites with their actual registered test names")
      val root = Path.of(getClass.getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath().toString
      val report = classOf[GoalPhaseStepTestReportRepairSpec].getName
      val progression = classOf[GoalPhaseOrderedProgressionSpec].getName
      val expected = new GoalPhaseStepTestReportRepairSpec().testNames.map(report -> _) ++
        new GoalPhaseOrderedProgressionSpec().testNames.map(progression -> _)
      val exact = new GoalPhaseStepTestReportRepairSpec().testNames.toVector.sorted.head
      val classes = Array(report, progression)
      When("the bridge applies an exact test-name filter and a substring filter")
      val exactrows = LabelledTestSelection.select(root, "test", "EXPLICIT", classes, Array(exact), Array.empty[String])
      val containingrows = LabelledTestSelection.select(root, "test", "EXPLICIT", classes, Array.empty[String], Array("repair"))
      Then("each selection retains only matching suite-local registrations without runtime preparation")
      exactrows.map(row => row(0) -> row(1)).toSet shouldBe expected.filter(_._2 == exact)
      containingrows.map(row => row(0) -> row(1)).toSet shouldBe expected.filter(_._2.contains("repair"))
      exactrows should not be empty
      containingrows should not be empty
      (exactrows ++ containingrows).foreach(row => expected should contain(row(0) -> row(1)))
      (exactrows ++ containingrows).forall(_(2) == "false") shouldBe true
    }

    "select ADMISSION without changing FULL or admitting HEAVY" in {
      Given("automatically discoverable admission, ordinary local and HEAVY declarations")
      val root = Path.of(getClass.getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath().toString
      val admission = classOf[GoalPhaseOrderedProgressionSpec].getName
      val local = getClass.getName
      val heavy = classOf[NativeGoalMetadataCliSpec].getName
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

    "discover the dependency CLI explicitly without manual enrollment and exclude it from automatic FULL and HEAVY" in {
      Given("the actual compiled test artifact with an explicit-only dependency CLI and an ordinary local suite")
      val root = Path.of(getClass.getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath().toString
      val dependency = classOf[NativeGoalDependencyValidationCliSpec].getName
      val declared = new NativeGoalDependencyValidationCliSpec().testNames
      val local = getClass.getName
      When("the bridge requests the dependency CLI explicitly and through automatic HEAVY and FULL selection")
      val explicit = LabelledTestSelection.select(root, "test", "EXPLICIT", Array(dependency))
      val heavy = scala.util.Try(LabelledTestSelection.select(root, "test", "HEAVY", Array(dependency)))
      val full = LabelledTestSelection.select(root, "test", "FULL", Array(dependency, local))
      Then("explicit registrations retain runtime preparation, automatic HEAVY rejects the empty selection and FULL keeps only local work")
      explicit should not be empty
      explicit.map(row => row(0) -> row(1)).toSet shouldBe declared.map(dependency -> _)
      heavy.failed.get shouldBe a[IllegalArgumentException]
      heavy.failed.get.getMessage should include("No labelled runnable operation selected")
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


/** Source-only fixture used by the actual build bridge compatibility specification.
  * Introduced Nov. 6, 2026; updated Nov. 6, 2026.
  */
class AdapterModeInputDeclaration extends ValidationDeclarationProvider {
  def declaration: M.Declaration = M.Declaration(purposes = Some(Set(M.Purpose.FULL, M.Purpose.FOCUSED)),
    requiredSemantics = Set("executionModes", "inputSchema"), executionModes = Some(Set("test")),
    inputSchema = Some(Vector(I.Parameter("count", I.Kind.Integer))))
}

@DoNotDiscover
@ValidationSpecification(declaration = classOf[AdapterModeInputDeclaration])
class AdapterModeInputFixtureSpec extends AnyWordSpec with Matchers {
  "Adapter input" should { "retain the selected variant" in { 1 + 2 shouldBe 3 } }
  override protected def withFixture(test: NoArgTest): Outcome = {
    I.fromConfigMapC(new AdapterModeInputDeclaration().declaration.inputSchema.get, test.configMap.toMap)
      .toOption.get shouldBe Map("count" -> Value.Integer(3L))
    E.consumeC(test.configMap.toMap, getClass.getName, test.name, OperationMode.Test, "fixture").isSuccess shouldBe true
    super.withFixture(test)
  }
}

@org.scalatest.tags.Slow
@ValidationSpecification(declaration = classOf[LocalTestDeclaration])
class AdapterSlowFixtureSpec extends AnyWordSpec with Matchers {
  "Explicit Slow fixture" should { "retain harmless focused scope" in { 2 + 3 shouldBe 5 } }
}
