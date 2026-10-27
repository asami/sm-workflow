/**
 * @since   Oct. 26, 2026
 * @version Oct. 27, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import StandaloneFixExecution as S

/** Typed host configuration only; these descriptors are specification data,
  * not observations of a provider or execution authority.
  */
private[admission] object BoundedWorkExecutionAdmissionFixture {
  val profile = WorkflowConcreteExecutionProfile("bounded-spec-profile", "spec-provider", "spec-model", Map.empty)
  val origin = GoalPhaseNativeExecution.Origin(WorkflowExecutorDescriptor("bounded-spec-origin", profile), WorkflowExecutionPreference())
  val classes = Vector("CodingProgrammingStandard", "DesignStandard", "ReviewStandard")
  def mappingC(supportedClasses: Vector[String] = classes): Consequence[WorkflowExecutionProfileMapping] =
    WorkflowExecutionProfileMapping.createC("bounded-spec-mapping", "1", Vector(profile),
      supportedClasses.map(name => WorkflowExecutionProfileMapping.ClassBinding(name, profile.identity)))
  private val _profile = Json.obj("identity" -> Json.fromString(profile.identity), "provider" -> Json.fromString(profile.provider),
    "model" -> Json.fromString(profile.model), "controls" -> Json.obj())
  val parameters: Map[String, String] = Map(
    WorkflowExecutionProfileParameters.declaration.name -> Json.obj(
      "schemaVersion" -> Json.fromString(WorkflowExecutionProfileParameters.schemaVersion),
      "identity" -> Json.fromString("bounded-spec-mapping"), "revision" -> Json.fromString("1"),
      "profiles" -> Json.arr(_profile), "classes" -> Json.fromValues(classes.map(name => Json.obj(
        "reasoningClass" -> Json.fromString(name), "profile" -> Json.fromString(profile.identity))))).noSpaces,
    GoalPhaseNativeExecution.declaration.name -> Json.obj("schemaVersion" -> Json.fromString(GoalPhaseNativeExecution.schemaVersion),
      "current" -> Json.obj("contextIdentity" -> Json.fromString(origin.current.contextIdentity), "profile" -> _profile),
      "preference" -> Json.obj("forceInline" -> Json.False, "isolateContext" -> Json.False)).noSpaces)
}

@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class BoundedWorkExecutionAdmissionSpec extends AnyWordSpec with GivenWhenThen with ThreeWorkflowManagedRepositoryFixture {
  import BoundedWorkExecutionAdmissionFixture.*
  private def _take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail("Bounded execution specification admission failed: " + other)
  }
  private def _input(kind: String, task: Boolean): S.Input = S.Input("sm-workflow", "bounded-request", "Adopted bounded objective",
    Vector("src"), Vector("focused"), Vector("behavior"), Vector(ContextReference("adopted", "1")), false,
    kind = kind, scalaLint = "off", task = if (task) Some(S.Task("direct-instruction", Vector.empty)) else None)
  private def _host(profiles: Option[WorkflowExecutionProfileMapping], origin: Option[GoalPhaseNativeExecution.Origin]) =
    BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
      "sm-workflow", "unused-before-preflight", "main", Vector("src"), Vector.empty, profiles, origin, None, None, None)

  "Fresh bounded work execution admission" should {
    "derive code, documentation and independent review intent while retaining each entry's exact logical capability" in {
      Given("the Task and standalone Required SPI and adopted code or documentation kind")
      Vector(false, true).foreach { task =>
        val runtime = if (task) GoalTaskNativeRun else StandaloneFixNativeRun
        val wire = if (task) "goal-task" else "standalone-fix"
        Vector("code", "documentation").foreach { kind =>
          Vector("Repair", "Review").concat(if (task) Vector("Implement") else Vector.empty).foreach { stage =>
            When("the native owner derives a fresh " + stage + " requirement")
            val requirement = _take(runtime.executionRequirementC(_input(kind, task), stage))
            Then("semantic classification preserves the Required SPI and review boundary")
            val operation = if (stage == "Review") "review" else if (stage == "Implement") "implementation" else "repair"
            requirement.capabilities shouldBe Vector(CapabilityRequirement(wire + "-" + operation + "-capability"))
            requirement.reasoningClass shouldBe Some(ReasoningClassReference(
              if (stage == "Review") "ReviewStandard" else if (kind == "code") "CodingProgrammingStandard" else "DesignStandard"))
            requirement.reasoning shouldBe ReasoningLevel.Standard
            requirement.risk shouldBe RiskLevel("SOURCE_IMPLEMENTATION")
            requirement.reviewRequired shouldBe (stage == "Review")
            requirement.disposition shouldBe None
          }
        }
      }
    }

    "require the adopted implementation or repair class and possible review class before a new Start" in {
      Given("a complete typed origin and mappings with different supported semantic classes")
      Vector(false, true).foreach { task =>
        val runtime = if (task) GoalTaskNativeRun else StandaloneFixNativeRun
        Vector("code", "documentation").foreach { kind =>
          val input = _input(kind, task)
          val requiredclass = if (kind == "code") "CodingProgrammingStandard" else "DesignStandard"
          val exact = _take(mappingC(Vector(requiredclass, "ReviewStandard")))
          When("new Start preflights only the classes needed by its adopted kind")
          runtime.preflightExecutionC(input, Some(exact), Some(origin)).isSuccess shouldBe true
          Then("neither a missing implementation class nor missing possible scoped review is usable")
          Vector(Vector(requiredclass), Vector("ReviewStandard")).foreach { supported =>
            runtime.preflightExecutionC(input, Some(_take(mappingC(supported))), Some(origin)).isSuccess shouldBe false
          }
        }
      }
    }

    "reject absent, partial, invalid-origin, incompatible placement and inadequate host configuration before native creation or managed effects" in {
      Given("new Task, standalone test-fix and review-origin requests with no execution core or effect ports")
      val complete = _take(mappingC())
      val bad: Vector[(Option[WorkflowExecutionProfileMapping], Option[GoalPhaseNativeExecution.Origin])] = Vector((None, None), (Some(complete), None), (None, Some(origin)),
        (Some(complete), Some(origin.copy(current = null))), (Some(complete), Some(origin.copy(preference = null))),
        (Some(complete), Some(origin.copy(current = origin.current.copy(profile = null)))),
        (Some(complete), Some(origin.copy(current = origin.current.copy(contextIdentity = "")))),
        (Some(complete), Some(origin.copy(preference = WorkflowExecutionPreference(forceInline = true)))),
        (Some(complete), Some(origin.copy(preference = WorkflowExecutionPreference(forceInline = true, isolateContext = true)))),
        (Some(_take(mappingC(Vector("ReviewStandard")))), Some(origin)),
        (Some(_take(mappingC(Vector("CodingProgrammingStandard", "DesignStandard")))), Some(origin)))
      val revieworigin = StandaloneReviewFix.Origin(ContextReference("original-review", "1"), "Adopted original review context",
        Vector(StandaloneReviewFix.Finding("finding-1", "Repair adopted finding", Vector("src/Behavior.scala"), Vector.empty)))
      val inputs = Vector(_input("code", true), _input("documentation", true), _input("code", false),
        _input("documentation", false).copy(reviewOrigin = Some(revieworigin)))
      inputs.foreach { input =>
        val runtime = if (input.task.nonEmpty) GoalTaskNativeRun else StandaloneFixNativeRun
        _take(S.inputC(input))
        bad.foreach { (profiles, from) =>
          When("Start is asked to create new work with unusable execution configuration")
          // Null ports are deliberate sentinels: touching selection, core, Git,
          // persistence or process execution before rejection would fail this spec.
          val result = runtime.startC(input, "participant", "start-key", "selected", null,
            _host(profiles, from), null, null)
          Then("configuration rejection returns before any native or managed effect boundary is reached")
          result.isSuccess shouldBe false
        }
      }
    }
  }
}
