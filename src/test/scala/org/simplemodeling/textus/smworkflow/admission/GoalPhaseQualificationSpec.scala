/**
 * @since   Nov. 17, 2026
 * @version Nov. 18, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseQualification as Q
import GoalPhaseNativeValidation as N
import SplitPhaseExplicitPartitionFixture.take

@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseQualificationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val original = Vector(N.Check("compile", 1, WorkflowSbtValidation.Kind.Compile),
    N.Check("test", 2, WorkflowSbtValidation.Kind.Test))
  private val suffix = Vector(N.Check("selected-qualification", 3, WorkflowSbtValidation.Kind.TestOnly))
  private val snapshot = GoalPhaseOrderedProgression.Snapshot("a" * 40, "b" * 40)
  private val target = Q.Target("specification-session", "GoalPhaseWorkflow", "specification-revision", "participant",
    20L, "plan", "1", "step-1", 1L, snapshot, 19L, "host-inventory", "1",
    Json.obj("specificationOwnedHostPlan" -> Json.True).noSpaces, original, suffix)
  private def payload = N.Payload("21", "step-1", snapshot, original ++ suffix, Vector.empty, None,
    qualification = Some(Q.Selection(target)))

  "The exact qualification relation" should {
    "admit only an earlier uniquely referenced preparatory TestCompile inside the finite suffix" in {
      Given("a managed metadata profile and its independently captured preparatory check")
      val build = N.Check("qualification-build", 3, WorkflowSbtValidation.Kind.TestCompile)
      val profile = GoalPhaseNativeMetadataFixture.profile.copy(buildCheckIdentity = Some(build.identity))
      val selected = N.Check("qualification-metadata", 4, WorkflowSbtValidation.Kind.TestOnly, Some(profile))
      When("the finite suffix is captured with absent crossed later or unused build references")
      val valid = Vector(build, selected)
      val invalid = Vector(Vector(selected), Vector(selected, build), Vector(build.copy(ordinal = 5), selected),
        Vector(build.copy(kind = WorkflowSbtValidation.Kind.Compile), selected), Vector(build),
        Vector(build, selected.copy(metadata = Some(profile.copy(buildCheckIdentity = Some("unknown-build"))))))
      Then("only the exact preceding managed reference is admitted and original Compile FULL obligations remain")
      Q.checksValid(valid) shouldBe true
      take(Q.combinedC(original, valid)) shouldBe original ++ valid
      invalid.foreach(values => Q.checksValid(values) shouldBe false)
      Q.checksValid(suffix) shouldBe true
    }

    "require both preparatory builds before either metadata selection instead of accepting interleaved discovery" in {
      Given("two distinct managed metadata groups with earlier build references and unique ordinals")
      val builda = N.Check("qualification-build-a", 3, WorkflowSbtValidation.Kind.TestCompile)
      val buildb = N.Check("qualification-build-b", 4, WorkflowSbtValidation.Kind.TestCompile)
      val profilea = GoalPhaseNativeMetadataFixture.profile.copy(buildCheckIdentity = Some(builda.identity))
      val profileb = GoalPhaseNativeMetadataFixture.profile.copy(buildCheckIdentity = Some(buildb.identity))
      val metaa = N.Check("qualification-metadata-a", 5, WorkflowSbtValidation.Kind.TestOnly, Some(profilea))
      val metab = N.Check("qualification-metadata-b", 6, WorkflowSbtValidation.Kind.TestOnly, Some(profileb))
      When("the same finite groups are captured with all builds first or with a build after the first metadata group")
      val ordered = Vector(builda, buildb, metaa, metab)
      val interleaved = Vector(builda, metaa, buildb, metab)
      Then("only the shared build-prefix ordering is admitted without changing original required checks")
      Q.checksValid(ordered) shouldBe true
      take(Q.combinedC(original, ordered)) shouldBe original ++ ordered
      Q.checksValid(interleaved) shouldBe false
      Q.combinedC(original, interleaved).isSuccess shouldBe false
    }

    "refuse the new triage combination while leaving the absent qualification contract unchanged" in {
      val policy = GoalPhaseStepAssessment.Policy("triage-boundary", "documentation", "conditional", "off", "off",
        Vector.empty, semanticTriage = "conditional")
      val step = GoalPhaseOrderedProgression.Step("step-1", "Retain the selected input", Vector("value.txt"),
        Vector.empty, original.map(_.identity), actionPolicy = Some(policy))
      val plan = GoalPhaseOrderedProgression.Plan("triage-boundary", "1", "phase-1", "fixture", Vector(step), original.map(_.identity))
      GoalPhaseQualification.triageCompatible(take(GoalPhaseOrderedProgression.startC(plan, snapshot))) shouldBe false
      val legacy = plan.copy(steps = Vector(step.copy(actionPolicy = None)))
      GoalPhaseQualification.triageCompatible(take(GoalPhaseOrderedProgression.startC(legacy, snapshot))) shouldBe true
      val assessment = GoalPhaseStepAssessment.Result(policy, snapshot, "fixture-checks", original.map(_.identity),
        "triage", "Specification-owned actual triage branch", "required", "required")
      Q.payloadValid(payload.copy(outcome = Some("passed"), assessment = Some(assessment))) shouldBe false
      Q.payloadValid(payload.copy(qualification = None, outcome = Some("passed"), assessment = Some(assessment))) shouldBe true
    }

    "retain the unchanged original prefix and separately authenticate the finite typed suffix" in {
      Given("an unchanged original compile/test selection and an independent TestOnly suffix")
      take(Q.combinedC(original, suffix)) shouldBe original ++ suffix
      Q.targetValid(target) shouldBe true
      Q.codec.decode(Q.codec.encode(Q.Selection(target))) shouldBe Right(Q.Selection(target))
      Q.payloadValid(payload) shouldBe true
      Q.originalPrimary(payload) shouldBe original
      Q.payloadValid(payload.copy(selected = original)) shouldBe false
      Q.payloadValid(payload.copy(selected = original, outcome = Some("passed"))) shouldBe false
      Q.payloadValid(payload.copy(selected = original, outcome = Some("repair-required"))) shouldBe false
    }

    "refuse duplicate undeclared crossed stale and widened source bindings without reclassifying the original checks" in {
      Q.combinedC(original, suffix.map(_.copy(identity = "test"))).isSuccess shouldBe false
      Q.combinedC(original, suffix.map(_.copy(ordinal = 2))).isSuccess shouldBe false
      Q.combinedC(original, suffix.map(_.copy(kind = WorkflowSbtValidation.Kind.Test))).isSuccess shouldBe false
      Q.payloadValid(payload.copy(sourceRevision = "22")) shouldBe false
      Q.payloadValid(payload.copy(candidate = snapshot.copy(tree = "c" * 40))) shouldBe false
      Q.payloadValid(payload.copy(step = "foreign-step")) shouldBe false
      Q.payloadValid(payload.copy(selected = original ++ suffix.map(_.copy(identity = "undeclared")))) shouldBe false
      val capture = DependencySourceRevisionSpecificationFixture.capture
      Q.payloadValid(payload.copy(dependencySource = Some(capture))) shouldBe false
      Q.payloadValid(payload.copy(sourceConsumption = Some(GoalPhaseDependencySourceRevision.consumptionOf(capture)))) shouldBe false
      val candidate = GoalPhaseNativeCandidate.Payload("18", "step-1", Vector("value.txt"), Vector("value.txt"),
        Some(snapshot), Vector("value.txt"), Some("SPECIFICATION-OWNED diff"))
      Q.noConsumption(candidate) shouldBe true
      Q.noConsumption(candidate.copy(dependencySourceCapture = Some(capture))) shouldBe false
      val encoded = Q.codec.encode(Q.Selection(target))
      Q.codec.decode(encoded.mapObject(_.add("extra", Json.True))).isLeft shouldBe true
      Q.codec.decode(Q.codec.encode(Q.Selection(target.copy(hostPlan = "{}")))).isLeft shouldBe true
    }

    "retain legacy absent qualification wire and exact metadata expansion without adding an active purpose label" in {
      val legacy = N.Payload("21", "step-1", snapshot, original, Vector.empty, None)
      val wire = N.codec.encode(legacy)
      wire.hcursor.downField("payload").downField("qualification").focus shouldBe None
      N.codec.decode(wire) shouldBe Right(legacy)
      val profile = GoalPhaseNativeMetadataFixture.profile.copy(query =
        org.goldenport.cncf.validation.ValidationDiscoveryV1.SelectionQuery(standardFramework = true,
          explicitSuites = Set(GoalPhaseNativeMetadataFixture.fixtureClass.getName)), supplemental = Vector.empty)
      val declared = suffix.map(_.copy(metadata = Some(profile)))
      val expanded = take(GoalPhaseNativeMetadata.prepareC(original ++ declared, GoalPhaseNativeMetadataFixture.loader))
      val qualified = target.copy(supplemental = declared)
      val actual = payload.copy(selected = expanded._1, metadata = expanded._2, qualification = Some(Q.Selection(qualified)))
      Q.payloadValid(actual) shouldBe true
      Q.payloadValid(actual.copy(selected = original, metadata = Vector.empty)) shouldBe true
      Q.payloadValid(actual.copy(selected = original, metadata = Vector.empty, outcome = Some("passed"))) shouldBe false
      Q.payloadValid(actual.copy(selected = original, metadata = Vector.empty, outcome = Some("repair-required"))) shouldBe true
      Q.originalPrimary(actual) shouldBe original
      GoalPhaseNativeMetadata.validateRetainedC(original ++ declared, actual.selected, actual.metadata).isSuccess shouldBe true
    }
  }
}
