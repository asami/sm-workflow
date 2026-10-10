package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value.GoalPhaseStartInput
import GoalPhaseWorkClassification.*
import GoalPhaseWorkClassification.given
import GoalPhaseOrderedProgression as O

/** Planning/codec evidence only: not execution, consent or Skill integration.
  * @since   Oct.  8, 2026
  * @version Oct.  8, 2026
  */
final class GoalPhaseWorkClassificationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail(s"WorkClassification failed: $other")
  }
  private def base(review: Boolean) = ExecutionRequirement(Vector(CapabilityRequirement("semantic-work")),
    RiskLevel("SOURCE_IMPLEMENTATION"), ReasoningLevel.Standard, review)
  private val coding = Classification(Kind.CODING, Complexity.STANDARD, Domain.PROGRAMMING, Footprint.SMALL)
  private val review = Classification(Kind.REVIEW, Complexity.STANDARD, Domain.ENGINEERING, Footprint.SMALL)
  private val assignment = Assignment(coding, coding.copy(complexity = Complexity.COMPLEX), review)
  private val legacy = O.Plan("selected-plan", "1", "PHASE-5", "owned-repository",
    Vector(O.Step("S1", "Implement the selected boundary", Vector("src/one.scala"), Vector.empty, Vector("focused"))),
    Vector("whole-phase"), repairLimit = 1)
  private val classified = legacy.copy(steps = legacy.steps.map(_.copy(workClassification = Some(assignment))),
    phaseReviewClassification = Some(review.copy(footprint = Footprint.LARGE)))
  private def selection(plan: O.Plan) = GoalPhaseNativeRun.SelectedPlan("selected-start", "participant",
    GoalPhaseStartInput(plan.phase, plan.repository), plan)
  private def metadata(example: String, rules: String) =
    afterWord(s"in spec:goal-phase-work-classification, example:$example, rules:$rules, phase:5")

  "GoalPhase planning classification" should {
    "E1 derive semantic requirements without provider or execution placement" must metadata("E1", "R1") {
      "across the complete bounded classification table" in {
        Given("Spec: docs/spec/goal-phase-work-classification.md; Rule: R1; Example: E1; all six work kinds and supported hints")
        val inputs = for {
          kind <- Kind.values.toVector
          complexity <- Complexity.values.toVector
          domain <- Domain.values.toVector
          footprint <- Footprint.values.toVector
        } yield Classification(kind, complexity, domain, footprint)
        When("planning values produce logical requirements, without Component configuration")
        val outcomes = inputs.map(value => value -> take(value.requirementC(base(value.kind == Kind.REVIEW))))
        Then("requirements contain only semantic class/level and preserve capability, risk and review responsibility")
        outcomes.size shouldBe 144
        outcomes.foreach { (value, requirement) =>
          val expected = if (value.kind == Kind.REVIEW) {
            if (value.complexity == Complexity.COMPLEX || value.footprint == Footprint.LARGE) ReasoningLevel.Critical
            else ReasoningLevel.Standard
          } else value.complexity match {
            case Complexity.TRIVIAL | Complexity.SIMPLE => ReasoningLevel.Routine
            case Complexity.STANDARD => ReasoningLevel.Standard
            case Complexity.COMPLEX => ReasoningLevel.Deep
          }
          val domainname = if (value.kind == Kind.CODING) value.domain.toString.toLowerCase.capitalize else ""
          requirement.reasoning shouldBe expected
          requirement.reasoningClass shouldBe Some(ReasoningClassReference(
            value.kind.toString.toLowerCase.capitalize + domainname + expected.value.toLowerCase.capitalize))
          requirement.disposition shouldBe None
          requirement.capabilities shouldBe base(value.kind == Kind.REVIEW).capabilities
          requirement.risk shouldBe base(value.kind == Kind.REVIEW).risk
          requirement.reviewRequired shouldBe (value.kind == Kind.REVIEW)
        }
      }
    }
    "E2 keep implementation, repair and independent-review assignment distinct" must metadata("E2", "R2") {
      "without silently changing an assigned review boundary" in {
        Given("Spec: docs/spec/goal-phase-work-classification.md; Rule: R2; Example: E2; an explicitly classified complete Plan")
        When("the Plan and each assigned requirement are validated")
        val admitted = O.validatePlanC(classified)
        val implement = take(assignment.implementation.requirementC(base(false)))
        val repair = take(assignment.repair.requirementC(base(false)))
        val independent = take(assignment.review.requirementC(base(true)))
        val rejected = Vector(review.requirementC(base(false)), coding.requirementC(base(true)),
          O.validatePlanC(classified.copy(phaseReviewClassification = Some(coding))),
          O.validatePlanC(classified.copy(steps = Vector(classified.steps.head.copy(
            workClassification = Some(assignment.copy(review = coding)))))))
        Then("implementation/repair intensity is selected independently while only review keeps reviewRequired")
        take(admitted) shouldBe ()
        implement.reasoningClass.map(_.value) shouldBe Some("CodingProgrammingStandard")
        repair.reasoningClass.map(_.value) shouldBe Some("CodingProgrammingDeep")
        independent.reasoningClass.map(_.value) shouldBe Some("ReviewStandard")
        rejected.foreach(_ shouldBe a[Consequence.Failure[?]])
      }
    }
    "E3 retain absent legacy fields and classified selected-plan storage" must metadata("E3", "R3") {
      "without introducing concrete runtime settings into historical Plan data" in {
        Given("Spec: docs/spec/goal-phase-work-classification.md; Rule: R3; Example: E3; legacy and newly classified selected Plans")
        When("the normal selected-plan codec persists and decodes both formats")
        val legacywire = take(GoalPhaseSelectedPlanJsonV1.encodeC(selection(legacy)))
        val classifiedwire = take(GoalPhaseSelectedPlanJsonV1.encodeStorageC(selection(classified)))
        val parsed = io.circe.parser.parse(legacywire).toOption.get.hcursor.downField("payload").downField("plan")
        Then("absence stays absence and classified history roundtrips independently of a current profile mapping")
        parsed.downField("phaseReviewClassification").focus shouldBe None
        parsed.downField("steps").downArray.downField("workClassification").focus shouldBe None
        take(GoalPhaseSelectedPlanJsonV1.decodeC(legacywire)) shouldBe selection(legacy)
        take(GoalPhaseSelectedPlanJsonV1.decodeStorageC(classifiedwire)) shouldBe selection(classified)
        classifiedwire should not include "gpt-"
        classifiedwire should not include "reasoning-effort"
      }
    }
    "E4 preserve classification through native Start and Work request codecs" must metadata("E4", "R3") {
      "on the existing operation payload surfaces" in {
        Given("Spec: docs/spec/goal-phase-work-classification.md; Rule: R3; Example: E4; retained native Start and an assigned semantic request")
        val snapshot = O.Snapshot("a" * 40, "b" * 40)
        val inputs = Vector(legacy, classified)
        When("the existing Start-result and Work-input codecs encode and decode their payloads")
        val outcomes = inputs.map { plan =>
          val start = GoalPhaseNativeRun.Payload(Json.obj(), "/owned/repository", "main", plan, snapshot)
          val request = GoalPhaseNativeWork.Request("instance", "2", "ImplementStep", Some("S1"), 1,
            plan.steps.head.purpose, plan, snapshot, Vector.empty, None, None, None)
          (start, GoalPhaseNativeRun.codec.decode(GoalPhaseNativeRun.codec.encode(start)),
            request, GoalPhaseNativeWork.inputCodec.decode(GoalPhaseNativeWork.inputCodec.encode(request)))
        }
        Then("both payload surfaces retain the selected semantic Plan and no executor/profile is inferred")
        outcomes.foreach { (start, decodedStart, request, decodedRequest) =>
          decodedStart shouldBe Right(start)
          decodedRequest shouldBe Right(request)
        }
      }
    }
    "E5 reject unknown classification input and incomplete assignments" must metadata("E5", "R2,R4") {
      "rather than accepting model controls as semantic classification" in {
        Given("Spec: docs/spec/goal-phase-work-classification.md; Rules: R2,R4; Example: E5; typed classification input")
        val json = coding.asJson
        When("input adds concrete controls, unsupported enums or null required members")
        val rejected = Vector(json.deepMerge(Json.obj("model" -> Json.fromString("unrequested"))),
          json.mapObject(_.add("complexity", Json.fromString("UNBOUNDED"))),
          json.mapObject(_.add("footprint", Json.Null))).map(_.as[Classification])
        val missing = assignment.copy(repair = null).validateC
        Then("classification decoding and Plan assignment validation reject without issuing work")
        rejected.foreach(_.isLeft shouldBe true)
        missing shouldBe a[Consequence.Failure[?]]
      }
    }
  }
}
