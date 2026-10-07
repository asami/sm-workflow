package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.StepCloseRequest
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
private[admission] object HumanDecisionContractFixture {
  def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => throw new IllegalArgumentException(s"Human fixture preparation failed: $other")
  }
  val handle = WorkflowHandle(SmWorkflowComponent.componentId, WorkflowDefinitionIdentity("GoalPhaseWorkflow"),
    WorkflowDefinitionRevision("revision-1"), InstanceIdentity("human-contract-instance"))
  val candidate = StepCloseRequest("submission", "step", "a" * 64, "b" * 64, Vector.empty)
  val revision = take(StepCloseRequestAdapter.submissionC(candidate)).candidate.reference.revision
  val digest = java.security.MessageDigest.getInstance("SHA-256")
    .digest(GoalPhaseStepCloseJson.encode(candidate).getBytes(java.nio.charset.StandardCharsets.UTF_8))
    .map(byte => f"${byte & 0xff}%02x").mkString
  val intent = GoalPhaseClosureAuthority.Intent(handle, StateMachineRevision("3"),
    ContextSnapshot(handle.workflowRevision.value, modelRevision = Some(revision), evidenceRevision = Some(digest)),
    GoalPhaseClosureAuthority.Input(GoalPhaseClosureAuthority.reason, candidate, revision, digest), "fixture-intent")
  val routing = GoalPhaseClosureDecisionTemplate(StateMachineRequiredOperationIdentity("fixture.human-decision"),
    StateMachineOperationIdentity("HumanContractFixture", "presentQuestion"),
    StateMachineOperationIdentity("HumanContractFixture", "recordAnswer"))
  val choices = Vector(GoalPhaseHumanDecisionContract.Choice("keep-open", "Keep the Step open"),
    GoalPhaseHumanDecisionContract.Choice("request-authorization", "Request separate bounded authorization"))
  val question = take(GoalPhaseHumanDecisionContract.createC("human-question-1", intent, routing, choices))
}

final class GoalPhaseHumanDecisionContractSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import GoalPhaseHumanDecisionContract.*
  import HumanDecisionContractFixture.*
  "Answerable human question contract" should {
    "carry the closed answer through the native Decision Result codec without human or command authority" in {
      Given("a declared keep-open answer and the exact native Decision result capsule")
      val answer = Answer("keep-open")
      val request = question.request
      val decision = WorkflowInteraction[GoalPhaseClosureAuthority.Input, Nothing](handle,
        WorkflowContinuation.Decision(request, MinimalPresentation("Fixture question", "No command permission")))
      val result = ContinuationResult(handle, request.runId, request.continuationId,
        request.expectedRevision, request.context.snapshot, TypedValue(resultType, answer),
        ContextReference("fixture-answer", "1"), Vector.empty, ExecutionEvidence(Vector.empty))
      When("the native Decision result is encoded and decoded with the answer payload codec")
      val wire = take(WorkflowResultJsonV1.encodeC(result, answerPayloadCodec))
      val decoded = take(WorkflowResultJsonV1.decodeC(wire, answerPayloadCodec))
      Then("the complete typed result survives the codec")
      decoded shouldBe result
      When("the question structurally admits the decoded answer")
      val answeradmission = take(question.admitAnswerC(decoded.result.value))
      Then("the declared choice is structurally valid without establishing human verification")
      answeradmission shouldBe ()
      // This structural admission is deliberately not a real human verifier.
      When("the result is admitted through the Decision completion boundary")
      val decisionadmission = WorkflowProtocolV1.admitDecisionResultC(decision, decoded)
      Then("the matching Decision result is accepted")
      decisionadmission.isSuccess shouldBe true
      When("the same Decision result is offered to the ordinary WorkOrder boundary")
      val workadmission = WorkflowProtocolV1.admitResultC(decision, decoded)
      Then("it cannot be substituted for an AI work completion")
      workadmission.isSuccess shouldBe false
      When("the closed answer payload is encoded and decoded by itself")
      val payload = answerPayloadCodec.encode(answer)
      val decodedpayload = answerPayloadCodec.decode(payload)
      Then("the declared choice is preserved")
      decodedpayload shouldBe Right(answer)
      Vector("approval", "actor", "issuer", "receipt", "question", "commandPermission").foreach { key =>
        When("an approval, actor or command-authority field is inserted into the payload")
        val result = answerPayloadCodec.decode(payload.mapObject(_.add(key, io.circe.Json.fromBoolean(true))))
        Then("the extra authority field is refused")
        result.isLeft shouldBe true
      }
      When("a JSON null answer is decoded")
      val jsonnull = answerPayloadCodec.decode(io.circe.Json.Null)
      Then("JSON null is refused")
      jsonnull.isLeft shouldBe true
      When("an absent payload is decoded")
      val absent = answerPayloadCodec.decode(null)
      Then("the absent payload is refused")
      absent.isLeft shouldBe true
      When("an absent answer is encoded and then decoded")
      val nullanswer = answerPayloadCodec.decode(answerPayloadCodec.encode(null))
      Then("it cannot form a valid answer")
      nullanswer.isLeft shouldBe true
      When("a noncanonical choice is encoded and then decoded")
      val padded = answerPayloadCodec.decode(answerPayloadCodec.encode(Answer(" keep-open")))
      Then("the padded choice is refused")
      padded.isLeft shouldBe true
      Given("a syntactically valid answer naming an undeclared choice")
      val unknown = answerPayloadCodec.decode(answerPayloadCodec.encode(Answer("invented-approval"))).toOption.get
      When("the question admits that unknown choice")
      val undeclared = question.admitAnswerC(unknown)
      Then("syntactic validity does not grant an undeclared answer")
      undeclared.isSuccess shouldBe false
    }

    "retain the Workflow-allocated identity, declared choices and typed answer contract" in {
      Given("the Workflow-allocated question identity, exact intent, routing and visible choices")
      val identity = "human-question-1"
      When("the answerable question is constructed")
      val created = take(createC(identity, intent, routing, choices))
      Then("the allocated identity, choices and typed answer contract are retained")
      created shouldBe question
      question.request.continuationId shouldBe ContinuationIdentity("human-question-1")
      question.request.resultType shouldBe Some(StateMachineResultTypeReference(resultType))
      question.intent shouldBe intent
      question.choices shouldBe choices
      When("the other declared request-authorization choice is admitted structurally")
      val admitted = take(question.admitAnswerC(Answer("request-authorization")))
      Then("it is a valid answer, not a grant of that authorization")
      admitted shouldBe ()
    }
    "keep V1 stop-only metadata unchanged and refuse its use as an answerable question" in {
      Given("the unchanged stop-only routing template and closure intent")
      val selected = routing
      When("the V1 stop request is constructed")
      val stop = take(selected.requestC(intent))
      Then("the stop request declares no answer result")
      stop.resultType shouldBe None
      When("stop-only metadata is used as an answerable question")
      val questionvalidation = Question(intent, stop, choices).validateC
      Then("the missing result contract is refused")
      questionvalidation shouldBe a[Consequence.Failure[?]]
      Given("an actual answerable native Decision wire")
      val wire = take(WorkflowDecisionJsonV1.encodeC(WorkflowInteraction[GoalPhaseClosureAuthority.Input, Nothing](handle,
        WorkflowContinuation.Decision(question.request, MinimalPresentation("Question", "No grant"))), GoalPhaseClosureAuthority.inputCodec))
      When("that answerable Decision is stored using the stop-only record contract")
      val storage = GoalPhaseClosureDecisionRecord.encodeStorageC(intent, wire)
      Then("the answerable contract is refused and V1 completion metadata stays unchanged")
      storage shouldBe a[Consequence.Failure[?]]
      stop.completion shouldBe CompletionContract("human-stop-only", Vector.empty)
    }
    "preserve a native Decision without admitting it through the WorkOrder publication wire" in {
      // Given an answerable human Decision, not an AI WorkOrder.
      val decision = WorkflowInteraction[GoalPhaseClosureAuthority.Input, Nothing](handle,
        WorkflowContinuation.Decision(question.request, MinimalPresentation("Question", "No grant")))
      // When projected through the actual CNCF Decision codec.
      val wire = take(WorkflowDecisionJsonV1.encodeC(decision, GoalPhaseClosureAuthority.inputCodec))
      // Then it round-trips as a Decision, but the managed WorkOrder wire cannot accept it.
      take(WorkflowDecisionJsonV1.decodeC(wire, GoalPhaseClosureAuthority.inputCodec)) shouldBe decision
      WorkflowWorkOrderJsonV1.encodeC(decision, GoalPhaseClosureAuthority.inputCodec) shouldBe a[Consequence.Failure[?]]
      WorkflowWorkOrderJsonV1.decodeC(wire, GoalPhaseClosureAuthority.inputCodec) shouldBe a[Consequence.Failure[?]]
    }
    "refuse empty, duplicate, unbounded or noncanonical question contracts" in {
      Given("invalid choice collections under the existing bounded question contract")
      Vector(Vector.empty[Choice], Vector(choices.head), choices :+ choices.head,
        choices.updated(0, Choice(" keep-open", "Keep")), choices.updated(0, Choice("keep-open", "x" * 513)),
        Vector.fill(17)(choices.head), null).foreach { value =>
        When("a question is constructed with that invalid choice collection")
        val result = createC("question", intent, routing, value)
        Then("the invalid visible choice contract is refused")
        result shouldBe a[Consequence.Failure[?]]
      }
      Vector(null, "", " question", "a" * 257, "question\nline").foreach { identity =>
        When("a question is constructed with an absent, padded or unbounded identity")
        val result = createC(identity, intent, routing, choices)
        Then("the noncanonical identity is refused")
        result shouldBe a[Consequence.Failure[?]]
      }
    }
    "encode and decode a closed bounded answer without question, actor or approval payloads" in {
      Given("the declared keep-open answer without authority or question metadata")
      val answer = Answer("keep-open")
      When("the bounded standalone answer wire is round-tripped")
      val wire = take(encodeAnswerC(answer))
      val decoded = take(decodeAnswerC(wire))
      Then("only the closed typed answer is retained")
      decoded shouldBe answer
      Given("the same answer wire with caller-owned metadata fields added")
      val json = io.circe.parser.parse(wire).toOption.get
      Vector("approval", "explicitHuman", "actor", "issuer", "receipt", "question", "revision", "collection").foreach { key =>
        When("the augmented standalone answer is decoded")
        val result = decodeAnswerC(json.mapObject(_.add(key, io.circe.Json.fromBoolean(true))).noSpaces)
        Then("the additional authority or question field is refused")
        result shouldBe a[Consequence.Failure[?]]
      }
      Vector(null, "{}", "{", "x" * 2049).foreach { value =>
        When("an absent, malformed or oversized standalone answer is decoded")
        val result = decodeAnswerC(value)
        Then("no answer is reconstructed")
        result shouldBe a[Consequence.Failure[?]]
      }
      Vector(io.circe.Json.Null, io.circe.Json.fromBoolean(true), io.circe.Json.fromString(" keep-open")).foreach { value =>
        When("the choice identity is null, incorrectly typed or padded")
        val result = decodeAnswerC(json.mapObject(_.add("choiceIdentity", value)).noSpaces)
        Then("the unsupported choice representation is refused")
        result shouldBe a[Consequence.Failure[?]]
      }
    }
    "refuse an undeclared answer even when it is syntactically decodable" in {
      Given("a bounded decodable answer whose choice is not declared by this question")
      val unknown = take(decodeAnswerC(take(encodeAnswerC(Answer("invented-approval")))))
      When("the question admits that undeclared answer")
      val undeclared = question.admitAnswerC(unknown)
      Then("codec success grants no choice authority")
      undeclared shouldBe a[Consequence.Failure[?]]
      When("the question receives no answer")
      val absent = question.admitAnswerC(null)
      Then("an absent answer is refused")
      absent shouldBe a[Consequence.Failure[?]]
      When("the question receives a differently cased choice")
      val changed = question.admitAnswerC(Answer("Keep-open"))
      Then("case drift does not match a declared choice")
      changed shouldBe a[Consequence.Failure[?]]
    }
    "refuse altered revision, snapshot, evidence or semantic completion routes" in {
      Given("question variants with foreign schema, incomplete intent, stale context or semantic routing")
      Vector(question.copy(schemaVersion = "foreign-version"),
        question.copy(intent = intent.copy(idempotencyKey = null)),
        question.copy(request = question.request.copy(expectedRevision = StateMachineRevision("4"))),
        question.copy(request = question.request.copy(context = question.request.context.copy(snapshot = ContextSnapshot("foreign")))),
        question.copy(request = question.request.copy(evidence = EvidenceContract("authentication-only", Vector.empty))),
        question.copy(request = question.request.copy(operation = StateMachineOperationIdentity("GoalPhaseService", "submitStepReview"))))
        .foreach { value =>
          When("the altered question contract is validated")
          val result = value.validateC
          Then("the foreign or stale contract is refused")
          result shouldBe a[Consequence.Failure[?]]
        }
    }
  }
}
