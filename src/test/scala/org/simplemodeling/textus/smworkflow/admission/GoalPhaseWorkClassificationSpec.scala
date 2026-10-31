package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.cncf.workflow.{WorkflowConcreteExecutionProfile, WorkflowExecutionProfileMapping, WorkflowExecutorDescriptor}
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseWorkClassification.*
import GoalPhaseWorkClassification.given
import GoalPhaseOrderedProgression as O

/** Planning/codec evidence only: not execution, consent or Skill integration.
  * @since   Oct.  8, 2026
  *  version Oct. 24, 2026
  * @version Oct. 31, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
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
  private val unclassified = O.Plan("selected-plan", "1", "PHASE-5", "owned-repository",
    Vector(O.Step("S1", "Implement the selected boundary", Vector("src/one.scala"), Vector.empty, Vector("focused"))),
    Vector("whole-phase"), repairLimit = 1)
  private val classified = unclassified.copy(steps = unclassified.steps.map(_.copy(workClassification = Some(assignment))),
    phaseReviewClassification = Some(review.copy(footprint = Footprint.LARGE)))
  private def selection(plan: O.Plan) = GoalPhaseNativeRun.SelectedPlan("selected-start", "participant",
    plan)
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
    "E3 retain absent optional classification and classified selected-plan storage" must metadata("E3", "R3") {
      "without introducing concrete runtime settings into current Plan data" in {
        Given("Spec: docs/spec/goal-phase-work-classification.md; Rule: R3; Example: E3; current Plans with optional classification absent or present")
        When("the normal selected-plan codec persists and decodes both current values")
        val unclassifiedwire = take(GoalPhaseSelectedPlanJsonV1.encodeC(selection(unclassified)))
        val classifiedwire = take(GoalPhaseSelectedPlanJsonV1.encodeStorageC(selection(classified)))
        val parsed = io.circe.parser.parse(unclassifiedwire).toOption.get.hcursor.downField("payload").downField("plan")
        Then("absence stays absence and classified history roundtrips independently of a current profile mapping")
        parsed.downField("phaseReviewClassification").focus shouldBe None
        parsed.downField("steps").downArray.downField("workClassification").focus shouldBe None
        take(GoalPhaseSelectedPlanJsonV1.decodeC(unclassifiedwire)) shouldBe selection(unclassified)
        take(GoalPhaseSelectedPlanJsonV1.decodeStorageC(classifiedwire)) shouldBe selection(classified)
        classifiedwire should not include "gpt-"
        classifiedwire should not include "reasoning-effort"
      }
    }
    "E4 preserve classification through native Start and Work request codecs" must metadata("E4", "R3") {
      "on the existing operation payload surfaces" in {
        Given("Spec: docs/spec/goal-phase-work-classification.md; Rule: R3; Example: E4; retained native Start and an assigned semantic request")
        val snapshot = O.Snapshot("a" * 40, "b" * 40)
        val inputs = Vector(unclassified, classified)
        When("the existing Start-result and Work-input codecs encode and decode their payloads")
        val outcomes = inputs.map { plan =>
          val start = GoalPhaseNativeRun.Payload("participant", "/owned/repository", "main", plan, snapshot)
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
    "E5 consume only actual semantic classification and complete assignments" must metadata("E5", "R2,R4") {
      "without rejecting inert model hints or accepting malformed semantic values" in {
        Given("Spec: docs/spec/goal-phase-work-classification.md; Rules: R2,R4; Example: E5; typed classification input")
        val json = coding.asJson
        When("input adds unused concrete hints, unsupported enums or null required members")
        val annotated = json.deepMerge(Json.obj("model" -> Json.fromString("unrequested"), "effort" -> Json.True))
        val consumed = annotated.as[Classification]
        val rejected = Vector(json.mapObject(_.add("complexity", Json.fromString("UNBOUNDED"))),
          json.mapObject(_.add("footprint", Json.Null))).map(_.as[Classification])
        val missing = assignment.copy(repair = null).validateC
        Then("unused model hints change no classification while actual semantic contradictions fail at their owner")
        consumed shouldBe Right(coding)
        rejected.foreach(_.isLeft shouldBe true)
        missing shouldBe a[Consequence.Failure[?]]
      }
    }
    "E6 preserve Split independent review in internal semantic-only derivation" must metadata("E6", "R1,R2,R6") {
      "while strict Goal and Repository classification still reject review-kind contradictions" in {
        Given("Spec: docs/spec/goal-phase-work-classification.md; Rules: R1,R2,R6; Example: E6; DESIGN and ANALYSIS with assigned independent review")
        val inputs = Vector(Kind.DESIGN, Kind.ANALYSIS).map(kind => coding.copy(kind = kind))
        val assigned = base(true).copy(disposition = Some(ExecutionDisposition(ExecutionPlacement.Delegated,
          ExecutionIndependence.Required, "original-origin")))
        When("Split consumes the internal semantic helper while strict consumers retain requirementC")
        val semantic = inputs.map(value => take(value.semanticRequirementC(assigned)))
        val strict = inputs.map(_.requirementC(assigned))
        val invalid = Vector(inputs.head.semanticRequirementC(null), inputs.head.copy(domain = null).semanticRequirementC(assigned))
        Then("semantic fields change alone, independent obligation and disposition survive, and strict or malformed input fails")
        semantic.zip(Vector("DesignStandard", "AnalysisStandard")).foreach { (value, name) =>
          value.reasoningClass shouldBe Some(ReasoningClassReference(name))
          value.copy(reasoningClass = assigned.reasoningClass) shouldBe assigned
        }
        (strict ++ invalid).foreach(_.isSuccess shouldBe false)
      }
    }

    "E7 reuse only unanimous retained repair intent and resolve it through the public native mapping" must metadata("E7", "R1,R7") {
      "without selecting a Step, concrete model or new execution permission" in {
        Given("two retained Steps with different implementation/review assignments but the same explicit medium repair Classification")
        val shared = coding.copy(footprint = Footprint.MEDIUM)
        val first = classified.steps.head.copy(workClassification = Some(assignment.copy(repair = shared)))
        val second = first.copy(identity = "S2", ownedPaths = Vector("src/two.scala"), dependencies = Vector("S1"),
          workClassification = Some(assignment.copy(implementation = coding.copy(complexity = Complexity.COMPLEX),
            repair = shared, review = review.copy(footprint = Footprint.LARGE))))
        val plan = classified.copy(steps = Vector(first, second))
        val assigned = base(false).copy(disposition = Some(ExecutionDisposition(ExecutionPlacement.Delegated,
          ExecutionIndependence.Optional, "retained-origin")))
        When("a Phase-wide RepairStep reconstructs its requirement from either retained Step ordering")
        val requirements = plan.steps.permutations.map(steps => take(GoalPhaseWorkClassification.requirementC(
          "RepairStep", None, plan.copy(steps = steps), assigned))).toVector
        Then("the shared repair class alone changes semantic intent while capability, risk and assigned disposition remain exact")
        requirements.foreach { requirement =>
          requirement shouldBe take(shared.requirementC(assigned))
          requirement.reasoningClass shouldBe Some(ReasoningClassReference("CodingProgrammingStandard"))
          requirement.copy(reasoning = assigned.reasoning, reasoningClass = assigned.reasoningClass) shouldBe assigned
        }
        Given("a specification-owned concrete profile mapping for that actual semantic class and a different caller profile")
        val profile = WorkflowConcreteExecutionProfile("fixture-coding", "fixture-provider", "fixture-standard", Map("reasoningEffort" -> "medium"))
        val mapping = take(WorkflowExecutionProfileMapping.createC("fixture-phase-repair", "1", Vector(profile),
          Vector(WorkflowExecutionProfileMapping.ClassBinding("CodingProgrammingStandard", profile.identity))))
        val origin = WorkflowExecutorDescriptor("fixture-origin", profile.copy(identity = "fixture-caller", model = "fixture-other"))
        val semantic = take(GoalPhaseWorkClassification.requirementC("RepairStep", None, plan, base(false)))
        When("the existing public invocation boundary resolves the newly reconstructed semantic requirement")
        val resolved = take(mapping.resolveC(semantic, origin))
        Then("normal mapping succeeds with its configured profile and preserves capability, risk and independence")
        resolved.profile shouldBe profile
        resolved.mappingIdentity shouldBe "fixture-phase-repair"
        resolved.requirement.disposition shouldBe Some(ExecutionDisposition(ExecutionPlacement.Delegated,
          ExecutionIndependence.Optional, origin.contextIdentity))
        resolved.requirement.copy(disposition = None) shouldBe semantic
        mapping.resolveC(base(false), origin).isSuccess shouldBe false
        GoalPhaseWorkClassification.requirementC("RepairStep", None, plan, base(true)).isSuccess shouldBe false
      }
    }
    "E8 preserve absent classification for missing or differing retained repair values" must metadata("E8", "R3,R7") {
      "without taking the first last aggregate or Phase-review classification" in {
        Given("retained Plans with no classification, one missing assignment, or a different repair member")
        val first = classified.steps.head
        val second = first.copy(identity = "S2", ownedPaths = Vector("src/two.scala"), dependencies = Vector("S1"))
        val differing = Vector(assignment.repair.copy(complexity = Complexity.STANDARD),
          assignment.repair.copy(domain = Domain.ENGINEERING), assignment.repair.copy(footprint = Footprint.LARGE))
        val steps = Vector(Vector.empty[O.Step], Vector(first.copy(workClassification = None)),
          Vector(first.copy(workClassification = None), second.copy(workClassification = None)),
          Vector(first, second.copy(workClassification = None)),
          Vector(first.copy(workClassification = None), second)) ++ differing.map(value =>
          Vector(first, second.copy(workClassification = Some(assignment.copy(repair = value)))))
        val assigned = base(false).copy(disposition = Some(ExecutionDisposition(ExecutionPlacement.Delegated,
          ExecutionIndependence.Optional, "retained-origin")))
        When("Phase-wide repair reconstructs each Plan, including both possible retained orderings")
        val outcomes = steps.flatMap(values => Vector(values, values.reverse)).map(values =>
          take(GoalPhaseWorkClassification.requirementC("RepairStep", None, classified.copy(steps = values), assigned)))
        Then("every requirement is unchanged, even though a separate Phase-review classification exists")
        outcomes.foreach(_ shouldBe assigned)
        take(GoalPhaseWorkClassification.requirementC("RepairStep", None, unclassified, assigned)) shouldBe assigned
      }
    }
    "E9 retain normal Step and whole-Phase selection and current Work codecs" must metadata("E9", "R2,R3,R7") {
      "while only the Phase-wide repair frontier gains the bounded fallback" in {
        Given("the original explicit implementation repair Step-review and Phase-review classifications")
        val routes = Vector(("ImplementStep", Some("S1"), false, assignment.implementation),
          ("RepairStep", Some("S1"), false, assignment.repair),
          ("TriageStep", Some("S1"), true, assignment.review),
          ("ReviewStep", Some("S1"), true, assignment.review),
          ("ReviewPhase", None, true, classified.phaseReviewClassification.get))
        When("the shared constructor receives the same normal native frontier inputs")
        val outcomes = routes.map { case (kind, step, independent, expected) =>
          take(GoalPhaseWorkClassification.requirementC(kind, step, classified, base(independent))) ->
            take(expected.requirementC(base(independent)))
        }
        Then("ordinary lookup and strict requirement meaning stay identical")
        outcomes.foreach { case (actual, expected) => actual shouldBe expected }
        Vector("ImplementStep", "TriageStep", "ReviewStep").foreach { kind =>
          take(GoalPhaseWorkClassification.requirementC(kind, None, classified, base(false))) shouldBe base(false)
        }
        take(GoalPhaseWorkClassification.requirementC("RepairStep", Some("absent"), classified, base(false))) shouldBe base(false)
        take(GoalPhaseWorkClassification.requirementC("ReviewPhase", None, unclassified, base(true))) shouldBe base(true)
        Given("a Phase-wide repair request carrying its original selected Plan and no Step identity")
        val request = GoalPhaseNativeWork.Request("instance", "2", "RepairStep", None, 1,
          "Repair retained release delta", classified, O.Snapshot("a" * 40, "b" * 40), Vector.empty, None, None, None)
        When("the unchanged current Work codec roundtrips this frontier")
        val decoded = GoalPhaseNativeWork.inputCodec.decode(GoalPhaseNativeWork.inputCodec.encode(request))
        Then("the same Plan and absent Step identity are retained without new fields or concrete profile data")
        decoded shouldBe Right(request)
      }
    }

  }
}
