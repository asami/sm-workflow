package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.{ContinuationIdentity, StateMachineOperationIdentity}
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
final class GoalPhaseHumanDecisionServiceOperationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import HumanDecisionContractFixture.*
  import GoalPhaseHumanDecisionServiceOperation.*
  "Public Human Decision commands" should {
    "admit only the bounded review reference and answer return fields, never caller authority" in {
      Given("closed preparation and answer commands for a Workflow-issued question")
      val review = ContinuationIdentity("review-1")
      val command = AnswerCommand(review, ContinuationIdentity("question-1"), GoalPhaseHumanDecisionContract.Answer("keep-open"), "interaction-1")
      val prepare = encodePreparationCommand(review)
      val answer = encodeAnswerCommand(command)
      When("both exact envelopes are decoded")
      take(decodePreparationCommandC(prepare)) shouldBe review
      take(decodeAnswerCommandC(answer)) shouldBe command
      Then("extra provenance, dispatch, storage or revision fields and unbounded/noncanonical identities refuse")
      Vector("receipt", "verifier", "participantIdentity", "storage", "expectedRevision", "approve", "operation").foreach { key =>
        val p = parse(prepare).toOption.get.mapObject(_.add(key, Json.fromString("caller"))).noSpaces
        val answerWithExtra = parse(answer).toOption.get.mapObject(_.add(key, Json.fromString("caller"))).noSpaces
        decodePreparationCommandC(p) shouldBe a[Consequence.Failure[?]]
        decodeAnswerCommandC(answerWithExtra) shouldBe a[Consequence.Failure[?]]
      }
      Vector(null, "", "{}", answer + ("x" * 8192)).foreach(value => decodeAnswerCommandC(value) shouldBe a[Consequence.Failure[?]])
      decodeAnswerCommandC(encodeAnswerCommand(command.copy(interactionReference = " interaction-1"))) shouldBe a[Consequence.Failure[?]]
      decodeAnswerCommandC(encodeAnswerCommand(command.copy(answer = GoalPhaseHumanDecisionContract.Answer("x" * 129)))) shouldBe a[Consequence.Failure[?]]
    }
    "declare separate CML Operations and require host-only human dependencies" in {
      Given("the generated service specifications and no host human verifier")
      val prepare = SmWorkflowComponent.GoalPhaseServiceService.PrepareGoalPhaseHumanDecisionOperation.specification
      val answer = SmWorkflowComponent.GoalPhaseServiceService.RecordGoalPhaseHumanAnswerOperation.specification
      When("the public signatures and incomplete host dependency are inspected")
      prepare.name shouldBe prepareOperationName
      answer.name shouldBe answerOperationName
      Then("request fields contain no authority and missing verifier prevents host admission")
      prepare.request.parameters.map(_.name).toSet shouldBe Set("reviewContinuationIdentity")
      answer.request.parameters.map(_.name).toSet shouldBe Set("reviewContinuationIdentity", "questionIdentity", "choiceIdentity", "interactionReference")
      Host(routing, null).validateC shouldBe a[Consequence.Failure[?]]
      bindC(null, null, null) shouldBe a[Consequence.Failure[?]]
    }
    "encode public contracts without storage prefixes while retaining the original storage envelope" in {
      Given("an answerable question whose completion is the exact declared public human Operation")
      val publicQuestion = take(GoalPhaseHumanDecisionContract.createC("question-public", intent,
        routing.copy(completionOperation = StateMachineOperationIdentity("GoalPhaseService", answerOperationName)),
        GoalPhaseHumanDecisionServiceOperation.choices))
      When("public JSON and persistent storage are round-tripped separately")
      val json = take(GoalPhaseHumanQuestionRecord.encodeJsonC(publicQuestion))
      take(GoalPhaseHumanQuestionRecord.decodeJsonC(json)) shouldBe publicQuestion
      take(GoalPhaseHumanQuestionRecord.decodeStorageC(take(GoalPhaseHumanQuestionRecord.encodeStorageC(publicQuestion)))) shouldBe publicQuestion
      val now = java.time.Instant.parse("2026-10-01T00:00:00Z")
      val receipt = GoalPhaseHumanInteractionVerification.Receipt("reference", "fixture-issuer", "human",
        GoalPhaseHumanInteractionVerification.purpose, GoalPhaseHumanInteractionVerification.Binding(
          org.goldenport.cncf.component.ComponentInstanceId.default(handle.componentIdentity), publicQuestion,
          GoalPhaseHumanDecisionContract.Answer("keep-open"), "relay"), now)
      val receiptJson = take(GoalPhaseHumanAnswerRecord.encodeJsonC(receipt))
      take(GoalPhaseHumanAnswerRecord.decodeJsonC(receiptJson)) shouldBe receipt
      take(GoalPhaseHumanAnswerRecord.decodeStorageC(take(GoalPhaseHumanAnswerRecord.encodeStorageC(receipt)))) shouldBe receipt
      Then("storage decoding cannot accept public JSON and semantic completion is still forbidden")
      parse(json).isRight shouldBe true
      parse(receiptJson).toOption.get.hcursor.get[String]("question").toOption.get.startsWith("{") shouldBe true
      GoalPhaseHumanQuestionRecord.decodeStorageC(json) shouldBe a[Consequence.Failure[?]]
      GoalPhaseHumanAnswerRecord.decodeStorageC(receiptJson) shouldBe a[Consequence.Failure[?]]
      publicQuestion.copy(request = publicQuestion.request.copy(completionOperation = StateMachineOperationIdentity("GoalPhaseService", "submitStepReview")))
        .validateC shouldBe a[Consequence.Failure[?]]
    }
  }
}
