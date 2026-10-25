package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseOrderedProgression as O
import GoalPhaseRepairProgress as P
import NativeHumanEvidenceFixture as H
import SplitPhaseExplicitPartitionFixture.take

/** Selected-window projections only; no external Human or review execution.
  * @since   Oct. 18, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseRepairProgressSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _plan = H.plan.copy(repairLimit = 1)
  private val _base = H.snapshot
  private val _candidate = _base.copy(tree = "c" * 40)
  private val _directive = O.RepairDirective("Correct selected check", Vector("Correct actual failed input"))
  private def _key(state: O.State): O.WorkKey = state.next.asInstanceOf[O.Semantic].key
  private def _repair: O.State = {
    val initial = take(O.startC(_plan, _base))
    val implemented = take(initial.acceptC(O.SemanticCompleted(_key(initial), "Implemented")))
    val inspected = take(implemented.acceptC(O.CandidateObserved(_candidate)))
    take(inspected.acceptC(O.RepairableChecksObserved(_candidate, _plan.steps.head.checks, "failed-check", _directive)))
  }

  "R1 strict selected repair progress" should {
    Vector(P.Snapshot(0, 0, 0), P.Snapshot(0, 0, 2), P.Snapshot(2, 1, 2), P.Snapshot(64, 64, 64)).foreach { value =>
      s"roundtrip the valid snapshot $value without changing values" in {
        Given("a valid selected-window snapshot from spec:goal-phase-repair-progress R1")
        When("its typed codec and generated public reader consume the exact fields")
        val decoded = value.asJson.as[P.Snapshot]
        val publicvalue = P.publicSnapshot(value)
        val publicread = V.GoalPhaseRepairProgress.createC(publicvalue.toRecord())
        Then("both retain every required integer")
        decoded shouldBe Right(value)
        take(publicread) shouldBe publicvalue
        P.validateOptionC(Some(value)) shouldBe Consequence.unit
      }
    }

    Vector(null, P.Snapshot(-1, 0, 1), P.Snapshot(0, -1, 1), P.Snapshot(1, 2, 2),
      P.Snapshot(2, 1, 1), P.Snapshot(0, 0, 65), P.Snapshot(0, 0, -1), P.Snapshot(1, 0, 0)).foreach { value =>
      s"refuse invalid typed snapshot $value" in {
        Given("null or out-of-range selected progress")
        When("the typed optional admission and public projection are requested")
        val admitted = P.validateOptionC(Some(value))
        val projected = P.publicOptionC(Some(value))
        Then("invalid presence never becomes omission")
        admitted.isSuccess shouldBe false
        projected.isSuccess shouldBe false
      }
    }

    Vector(Json.Null, Json.True, Json.obj(), Json.obj("automaticCount" -> Json.fromInt(1), "ordinaryCount" -> Json.fromInt(1)),
      Json.obj("automaticCount" -> Json.fromDoubleOrNull(1.5), "ordinaryCount" -> Json.fromInt(0), "limit" -> Json.fromInt(2)),
      Json.obj("automaticCount" -> Json.Null, "ordinaryCount" -> Json.fromInt(0), "limit" -> Json.fromInt(2)),
      Json.obj("automaticCount" -> Json.fromInt(2), "ordinaryCount" -> Json.fromInt(3), "limit" -> Json.fromInt(2))).zipWithIndex.foreach {
      (value, index) => s"refuse malformed wire presence $index" in {
        Given("a present null, incomplete, noninteger or contradictory snapshot")
        When("the strict decoder and selected optional projection consume it")
        val decoded = value.as[P.Snapshot]
        val selected = P.selectOption(io.circe.JsonObject("repairProgress" -> value))
        Then("both reject it instead of dropping the field")
        decoded.isLeft shouldBe true
        selected.isLeft shouldBe true
      }
    }

    "preserve unknown absence and reject null Option containers" in {
      Given("absent progress and a null internal Option are different inputs")
      When("the optional decoder, admission and public projection consume them")
      val absent = P.decodeOption(io.circe.JsonObject.empty)
      val nulloption = P.validateOptionC(null)
      Then("absence stays unknown while a null container is rejected")
      absent shouldBe Right(None)
      take(P.publicOptionC(None)) shouldBe None
      nulloption.isSuccess shouldBe false
      P.publicOptionC(null).isSuccess shouldBe false
    }
  }

  "R2 saved current-window assignment context" should {
    "admit exact retained predecessor counts after refresh while leaving absent legacy counts unknown" in {
      Given("the actual selected controller after its first failed check")
      val controller = _repair
      val progress = P.snapshot(controller)
      When("saved actual, crossed, absent and null assignment context is admitted")
      val exact = P.retainedC(Some(progress), controller)
      val crossed = P.retainedC(Some(P.Snapshot(0, 0, 1)), controller)
      Then("only actual or honestly absent context is retained")
      progress shouldBe P.Snapshot(1, 1, 1)
      exact shouldBe Consequence.unit
      crossed.isSuccess shouldBe false
      P.retainedC(None, controller) shouldBe Consequence.unit
      P.retainedC(null, controller).isSuccess shouldBe false
      Given("the selected plan revision refreshes without changing its immutable repair limit")
      When("retained progress is compared with the refreshed actual predecessor")
      val refreshed = take(controller.refreshedC(_plan.copy(revision = "2"), false)).toOption.get
      Then("progress follows the adopted controller while preserving its current window")
      P.retainedC(Some(progress), refreshed) shouldBe Consequence.unit
      P.retainedC(Some(P.Snapshot(0, 0, 1)), refreshed).isSuccess shouldBe false
    }

    "capture zero-limit context without inventing an allowance" in {
      Given("an actual newly started controller whose selected repair limit is zero")
      val initial = take(O.startC(_plan.copy(repairLimit = 0), _base))
      When("its read-only progress is captured")
      val progress = P.snapshot(initial)
      Then("only zero automatic and ordinary counts are displayed")
      progress shouldBe P.Snapshot(0, 0, 0)
      P.validateC(progress) shouldBe Consequence.unit
    }

    "preserve Work and Decision optional codec versions and reject malformed presence" in {
      Given("saved Work and Human inputs with exact typed progress and counterparts with unknown counts")
      val request = H.work.copy(repairProgress = Some(P.Snapshot(0, 0, H.plan.repairLimit)))
      val question = H.goalInput.copy(work = request, repairProgress = request.repairProgress)
      When("existing versioned Work and existing Decision codecs roundtrip the additive context")
      val workwire = GoalPhaseNativeWork.inputCodec.encode(request)
      val humanwire = GoalPhaseOrderedHumanDecision.inputCodec.encode(question)
      Then("present counts survive and absent fields remain omitted")
      GoalPhaseNativeWork.inputCodec.decode(workwire) shouldBe Right(request)
      GoalPhaseOrderedHumanDecision.inputCodec.decode(humanwire) shouldBe Right(question)
      workwire.hcursor.get[String]("schemaVersion").toOption shouldBe Some("sm-workflow.goal-phase-native-work-request.v2")
      GoalPhaseNativeWork.inputCodec.encode(H.work).hcursor.downField("payload").downField("repairProgress").focus shouldBe None
      GoalPhaseOrderedHumanDecision.inputCodec.encode(H.goalInput).hcursor.downField("repairProgress").focus shouldBe None
      GoalPhaseNativeWork.inputCodec.decode(GoalPhaseNativeWork.inputCodec.encode(H.work)) shouldBe Right(H.work)
      GoalPhaseOrderedHumanDecision.inputCodec.decode(GoalPhaseOrderedHumanDecision.inputCodec.encode(H.goalInput)) shouldBe Right(H.goalInput)
      Given("null or malformed progress added to the stored input")
      val malformed = Vector(Json.Null, Json.obj(), P.Snapshot(2, 1, 1).asJson)
      When("the normal input decoders encounter every present value")
      val works = malformed.map(v => GoalPhaseNativeWork.inputCodec.decode(workwire.mapObject(_.add("payload",
        workwire.hcursor.downField("payload").focus.get.mapObject(_.add("repairProgress", v))))))
      val humans = malformed.map(v => GoalPhaseOrderedHumanDecision.inputCodec.decode(humanwire.mapObject(_.add("repairProgress", v))))
      Then("neither route converts malformed presence to absence")
      works.foreach(_.isLeft shouldBe true)
      humans.foreach(_.isLeft shouldBe true)
      GoalPhaseNativeWork.admitSemanticResultC(request.copy(repairProgress = null),
        GoalPhaseNativeWork.Result("completed", "Selected work completed", Vector.empty)).isSuccess shouldBe false
    }

    "retain prior assignment counts separately after an actual admitted ordinary exemption" in {
      Given("an actual first repair controller and a bounded minor change admitted by the existing B owner")
      val before = _repair
      val request = H.work.copy(kind = "RepairStep", selectedPlan = _plan, candidate = None, checksReceipt = None,
        repairDirective = Some(_directive), review = Some(GoalPhaseNativeReview.Context(Vector("one.scala"), "Actual diff", Vector.empty)),
        repairProgress = Some(P.snapshot(before)))
      val assessment = GoalPhaseFixAssessment.Report(GoalPhaseFixAssessment.Change("SIMPLE_LOGIC", Vector("selected-unit"),
        false, true, "Bounded minor bug correction", false, false),
        GoalPhaseFixAssessment.Convergence("PROGRESSING", "HIGH", "The actual selected correction progressed"))
      val result = GoalPhaseNativeWork.Result("completed", "Minor correction completed", Vector.empty, fixAssessment = Some(assessment))
      val exempt = take(GoalPhaseFixAssessment.ordinaryCountExemptC(request, result))
      When("the actual controller consumes that exemption and a fresh selected failure reaches Human")
      val completed = take(before.acceptC(O.SemanticCompleted(_key(before), result.summary, exempt)))
      val inspected = take(completed.acceptC(O.CandidateObserved(_candidate)))
      val current = take(inspected.acceptC(O.RepairableChecksObserved(_candidate, _plan.steps.head.checks, "fresh-failure", _directive)))
      val question = H.goalInput.copy(work = request, result = result, repairProgress = Some(P.snapshot(current)))
      val decoded = GoalPhaseOrderedHumanDecision.inputCodec.decode(GoalPhaseOrderedHumanDecision.inputCodec.encode(question))
      Then("the saved Work remains 1/1 while current Human honestly captures 1/0")
      exempt shouldBe true
      current.stage shouldBe O.Stage.Decision
      request.repairProgress shouldBe Some(P.Snapshot(1, 1, 1))
      question.repairProgress shouldBe Some(P.Snapshot(1, 0, 1))
      decoded shouldBe Right(question)
      take(P.publicOptionC(question.repairProgress)) shouldBe Some(V.GoalPhaseRepairProgress(1, 0, 1, None, None, None))
      take(P.publicOptionC(request.repairProgress)) shouldBe Some(V.GoalPhaseRepairProgress(1, 1, 1, None, None, None))

      Given("that actual current controller capture and its unmodified prior Work in the saved typed Human question")
      val decision = H.goalQuestion.current.asInstanceOf[WorkflowContinuation.Decision[GoalPhaseOrderedHumanDecision.Input]]
      val issued = H.goalQuestion.copy(current = decision.copy(request = decision.request.copy(
        input = Some(TypedValue(GoalPhaseOrderedHumanDecision.inputCodec.typeIdentity, decoded.toOption.get)))))
      When("the native source producer and the selected Human evidence scope consume both saved snapshots")
      val source = take(ThreeWorkflowExternalEvidencePorts.goalHumanSourceC(issued))
      val binding = take(NativeHumanDecisionScope.questionBindingC(issued, source))
      val selected = NativeHumanDecisionScope.selectQuestion(binding, NativeExternalEvidence.Purpose.GoalHuman)
      Then("the actual ordinary exemption survives selection without rewriting the prior assignment")
      selected shouldBe Right(binding)
      source.hcursor.downField("repairProgress").focus shouldBe Some(P.Snapshot(1, 0, 1).asJson)
      source.hcursor.downField("assignment").downField("repairProgress").focus shouldBe Some(P.Snapshot(1, 1, 1).asJson)
    }
  }
}
