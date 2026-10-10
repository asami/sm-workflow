package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.StepCloseRequest
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Pure outcome faults, not proof of host write authority or physical storage.
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
final class GoalPhaseClosureDecisionRetentionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"Decision fixture failed: $other")
  }
  private val handle = WorkflowHandle(SmWorkflowComponent.componentId, WorkflowDefinitionIdentity("GoalPhaseWorkflow"),
    WorkflowDefinitionRevision("revision-1"), InstanceIdentity("decision-retention-instance"))
  private val candidate = StepCloseRequest("submission", "step", "a" * 64, "b" * 64, Vector.empty)
  private val revision = take(StepCloseRequestAdapter.submissionC(candidate)).candidate.reference.revision
  private val intent = GoalPhaseClosureAuthority.Intent(handle, StateMachineRevision("3"),
    ContextSnapshot(handle.workflowRevision.value, modelRevision = Some(revision)),
    GoalPhaseClosureAuthority.Input(GoalPhaseClosureAuthority.reason, candidate, revision), "fixture-intent")
  private val decision = ContinuationRequest(StateMachineRunIdentity(handle.instanceIdentity.value), ContinuationIdentity("question-1"),
    intent.expectedRevision, StateMachineRequiredOperationIdentity("host.step-close-authority"),
    StateMachineOperationIdentity("HumanBoundaryFixture", "decideStepClose"),
    Some(TypedValue(GoalPhaseClosureAuthority.inputCodec.typeIdentity, intent.input)), None,
    ContextBundle("Resolve exact authority", Vector(ContextReference(candidate.stepIdentity, revision)),
      Vector.empty, intent.snapshot),
    CompletionContract("human-stop-only", Vector.empty), EvidenceContract("human-evidence", Vector.empty),
    StateMachineOperationIdentity("HumanBoundaryFixture", "recordStepCloseAuthority"))
  private def wire(value: ContinuationRequest[GoalPhaseClosureAuthority.Input] = decision) =
    take(WorkflowDecisionJsonV1.encodeC(WorkflowInteraction[GoalPhaseClosureAuthority.Input, Nothing](handle,
      WorkflowContinuation.Decision(value, MinimalPresentation("Stop", "No approval"))), GoalPhaseClosureAuthority.inputCodec))
  private def record(value: ContinuationRequest[GoalPhaseClosureAuthority.Input] = decision) = Record.data(
    GoalPhaseClosureDecisionRecord.storageField -> take(GoalPhaseClosureDecisionRecord.encodeStorageC(intent, wire(value))))
  private def foreignRecord = Record.data(GoalPhaseClosureDecisionRecord.storageField ->
    take(GoalPhaseClosureDecisionRecord.encodeStorageC(intent.copy(expectedRevision = StateMachineRevision("9")),
      wire(decision.copy(expectedRevision = StateMachineRevision("9"))))))
  private final class Storage extends GoalPhaseClosureDecisionRetention.Port {
    var row = Option.empty[Record]
    var creates = 0
    var reads = 0
    var fault = "ordinary"
    def loadC(): Consequence[Option[Record]] = {
      reads += 1
      if (creates == 0) fault match {
        case "initial-failed" => Consequence.stateConflict("Initial observation unavailable")
        case "initial-throw" => throw new IllegalStateException("Initial observation threw")
        case _ => Consequence.success(row)
      }
      else fault match {
        case "missing" => Consequence.success(None)
        case "failed" => Consequence.stateConflict("Readback failed")
        case "foreign-scope" => Consequence.success(Some(foreignRecord))
        case "selected-metadata" => Consequence.success(Some(record(decision.copy(
          continuationId = ContinuationIdentity("stored-question"), context = decision.context.copy(
            references = Vector(ContextReference("historical-provenance", "diagnostic-only")))))))
        case "malformed" => Consequence.success(Some(Record.data(GoalPhaseClosureDecisionRecord.storageField -> "invalid")))
        case "throw-read" => throw new IllegalStateException("Readback threw")
        case _ => Consequence.success(row)
      }
    }
    def createC(value: Record): Consequence[Unit] = {
      creates += 1
      value.fields.map(_.key).toSet shouldBe Set(GoalPhaseClosureDecisionRecord.storageField)
      row = Some(value)
      fault match {
        case "failed-create" => Consequence.stateConflict("Lost Create acknowledgement")
        case "throw-create" => throw new IllegalStateException("Lost Create acknowledgement")
        case _ => Consequence.unit
      }
    }
  }
  private def retain(storage: Storage) = GoalPhaseClosureDecisionRetention.retainC(intent, wire(), storage)
  private def uncertain(result: Consequence[?]): Unit = result match {
    case Consequence.Failure(conclusion) => conclusion.observation.cause.descriptor.facets.collect {
      case org.goldenport.observation.Descriptor.Facet.Reason(name) => name
    } should contain("transaction-indeterminate")
    case other => fail(s"Uncertain Decision write published success: $other")
  }

  "Stop-only Decision retention" should {
    "derive stable host-routed question metadata from intent without adding a result or grant" in {
      Given("the exact closure intent and nonsemantic host question routes")
      val template = GoalPhaseClosureDecisionTemplate(decision.requiredOperation, decision.operation, decision.completionOperation)
      When("the host-routed stop question is constructed")
      val request = take(template.requestC(intent))
      When("the same intent constructs its question again")
      val repeated = take(template.requestC(intent))
      Then("the identity and complete stop-only contract are deterministic and grant no result")
      repeated shouldBe request
      request.continuationId shouldBe ContinuationIdentity("goal-phase-authority:" + intent.idempotencyKey)
      request.input shouldBe decision.input
      request.expectedRevision shouldBe intent.expectedRevision
      request.context.snapshot shouldBe intent.snapshot
      request.resultType shouldBe None
      request.completion shouldBe CompletionContract("human-stop-only", Vector.empty)
      When("the stop-only question is encoded and decoded as retained metadata")
      val decoded = take(GoalPhaseClosureDecisionRecord.decodeStorageC(intent,
        take(GoalPhaseClosureDecisionRecord.encodeStorageC(intent, wire(request)))))
      Then("the complete host-routed request survives storage")
      decoded shouldBe request
    }
    "refuse malformed or semantic routing and malformed intents before constructing a question" in {
      Given("a host-only template and variants with absent, malformed or semantic routes")
      val template = GoalPhaseClosureDecisionTemplate(decision.requiredOperation, decision.operation, decision.completionOperation)
      Vector(template.copy(requiredOperation = null), template.copy(operation = null),
        template.copy(completionOperation = null), template.copy(requiredOperation = StateMachineRequiredOperationIdentity(" ")),
        template.copy(operation = StateMachineOperationIdentity("GoalPhaseService", "submitStepReview")),
        template.copy(completionOperation = StateMachineOperationIdentity("GoalPhaseService", "submitStepRepair")),
        template.copy(requiredOperation = StateMachineRequiredOperationIdentity(
          domain.statemachine.workflow.GoalPhaseWorkflowStateMachineWorkflow1.workflow.requiredOperations.head.identity.capability)))
        .foreach { value =>
          When("the invalid template attempts to construct the question")
          val result = value.requestC(intent)
          Then("the routing is refused before a question is constructed")
          result shouldBe a[Consequence.Failure[?]]
        }
      When("the closure intent is absent")
      val absent = template.requestC(null)
      Then("an absent intent is refused")
      absent shouldBe a[Consequence.Failure[?]]
      When("the intent has no typed input")
      val input = template.requestC(intent.copy(input = null))
      Then("an incomplete intent is refused")
      input shouldBe a[Consequence.Failure[?]]
      When("the intent has a noncanonical idempotency key")
      val key = template.requestC(intent.copy(idempotencyKey = " key"))
      Then("the malformed key is refused")
      key shouldBe a[Consequence.Failure[?]]
    }
    "bind the generated preparation command and reject recovery schema or caller-owned question facts" in {
      Given("the sole public preparation parameter is a continuation identity")
      val expected = Set("continuationIdentity")
      When("the generated preparation operation specification is read")
      val generated = org.simplemodeling.textus.smworkflow.SmWorkflowComponent.GoalPhaseServiceService.PrepareGoalPhaseReviewDecisionOperation.specification
      Then("the canonical preparation operation declares only that parameter")
      generated.name shouldBe GoalPhaseReviewRecoveryServiceOperation.preparationOperationName
      generated.request.parameters.map(_.name).toSet shouldBe expected
      Given("the accepted-review continuation selected for preparation")
      val identity = ContinuationIdentity("accepted-review")
      val wire = GoalPhaseReviewRecoveryServiceOperation.encodePreparationCommand(identity)
      def decode(value: String) = GoalPhaseReviewRecoveryServiceOperation.decodeCommandC(value,
        GoalPhaseReviewRecoveryServiceOperation.preparationWireSchema)
      When("the preparation wire is decoded with its exact schema")
      val decoded = take(decode(wire))
      Then("the selected continuation is preserved")
      decoded shouldBe identity
      When("the recovery wire is presented to the preparation schema")
      val recovery = decode(GoalPhaseReviewRecoveryServiceOperation.encodeCommand(identity))
      Then("crossed recovery schema is refused")
      recovery shouldBe a[Consequence.Failure[?]]
      When("the preparation wire is presented to the ordinary recovery decoder")
      val preparation = GoalPhaseReviewRecoveryServiceOperation.decodeCommandC(wire)
      Then("crossed preparation schema is refused")
      preparation shouldBe a[Consequence.Failure[?]]
      Given("the preparation wire with caller-owned question, result or authority fields")
      val json = io.circe.parser.parse(wire).toOption.get
      Vector("result", "handle", "revision", "approval", "collection", "decision", "template", "actor").foreach { key =>
        When("the augmented preparation wire is decoded")
        val result = decode(json.mapObject(_.add(key, io.circe.Json.obj())).noSpaces)
        Then("caller-owned question facts are refused")
        result shouldBe a[Consequence.Failure[?]]
      }
    }
    "Create once, read back the committed request and observe equal retry without replacement" in {
      Given("an empty decision store and the exact stop-only request")
      val storage = new Storage
      When("the request is retained for the first time")
      val initial = take(retain(storage))
      Then("the committed request is observed after Create")
      initial shouldBe decision
      storage.reads shouldBe 2
      When("the identical request is retained again")
      val repeated = take(retain(storage))
      Then("the existing request is read without replacement or another Create")
      repeated shouldBe decision
      storage.creates shouldBe 1
      storage.reads shouldBe 3
    }
    "consume the selected scoped row without a proposed-body equality certificate" in {
      Given("a stored Decision with its allocated question identity and historical diagnostic metadata")
      val saved = decision.copy(continuationId = ContinuationIdentity("stored-question"),
        context = decision.context.copy(snapshot = intent.snapshot.copy(workspaceRevision = Some("old-workspace")),
          references = Vector(ContextReference("historical-provenance", "diagnostic-only"))))
      val storage = new Storage
      storage.row = Some(record(saved))
      When("retention receives a newly constructed proposal for the same actual target")
      val selected = take(retain(storage))
      Then("the selected row is returned without comparing or replacing its entire body")
      selected shouldBe saved
      storage.reads shouldBe 1
      storage.creates shouldBe 0
      storage.row shouldBe Some(record(saved))
      Given("a Create whose authoritative readback carries a different allocated question identity")
      val readback = new Storage
      readback.fault = "selected-metadata"
      When("retention resolves the actual committed scoped request")
      val observed = take(retain(readback))
      Then("readback need not certify equality to the proposed payload")
      observed.continuationId shouldBe ContinuationIdentity("stored-question")
      readback.creates shouldBe 1
      readback.reads shouldBe 2
    }
    "refuse foreign scope or malformed existing records without any Create" in {
      Vector(foreignRecord,
        Record.data("unknown" -> "value"), Record.data(GoalPhaseClosureDecisionRecord.storageField -> "invalid"), null).foreach { row =>
        Given("a different or malformed stored decision row")
        val storage = new Storage
        storage.row = Some(row)
        When("retention observes that existing row")
        val result = retain(storage)
        Then("the row is refused without Create or replacement")
        result shouldBe a[Consequence.Failure[?]]
        storage.creates shouldBe 0
        storage.row shouldBe Some(row)
      }
    }
    "refuse failed or throwing initial observations without treating them as missing" in {
      Vector("initial-failed", "initial-throw").foreach { fault =>
        Given("a store whose initial observation fails or throws")
        val storage = new Storage
        storage.fault = fault
        When("retention observes the decision before Create")
        val result = retain(storage)
        Then("the failure is not treated as absence and creates no row")
        result shouldBe a[Consequence.Failure[?]]
        storage.creates shouldBe 0
        storage.row shouldBe None
      }
    }
    "keep post-Create failures indeterminate and recover the same row by observation" in {
      Vector("missing", "failed", "foreign-scope", "malformed", "throw-read", "failed-create", "throw-create").foreach { fault =>
        Given("an empty store with a selected post-Create uncertainty fault")
        val storage = new Storage
        storage.fault = fault
        When("the decision is retained through that fault")
        val result = retain(storage)
        Then("one Create leaves transaction-indeterminate uncertainty")
        uncertain(result)
        storage.creates shouldBe 1
        val retained = storage.row
        storage.fault = "ordinary"
        When("ordinary readback retries the identical decision")
        val recovered = take(retain(storage))
        Then("the same committed row is recovered without another Create")
        recovered shouldBe decision
        storage.creates shouldBe 1
        storage.row shouldBe retained
      }
    }
    "reject invalid intent or Decision before any observation or Create" in {
      Given("an empty store before any retention observation")
      val storage = new Storage
      When("an absent intent attempts to retain a stop question")
      val absent = GoalPhaseClosureDecisionRetention.retainC(null, wire(), storage)
      Then("the missing intent is refused")
      absent shouldBe a[Consequence.Failure[?]]
      Vector(null, "{}", "invalid").foreach { value =>
        When("an absent or malformed Decision wire is retained")
        val result = GoalPhaseClosureDecisionRetention.retainC(intent, value, storage)
        Then("the invalid Decision is refused")
        result shouldBe a[Consequence.Failure[?]]
      }
      When("the retention port is absent")
      val port = GoalPhaseClosureDecisionRetention.retainC(intent, wire(), null)
      Then("no invalid retention attempts observation or Create")
      port shouldBe a[Consequence.Failure[?]]
      storage.reads shouldBe 0
      storage.creates shouldBe 0
    }
    "keep recovery input closed to a continuation identity, not caller authority or results" in {
      Given("the exact review continuation selected for recovery")
      val identity = ContinuationIdentity("review-1")
      When("the ordinary recovery command is encoded and decoded")
      val encoded = GoalPhaseReviewRecoveryServiceOperation.encodeCommand(identity)
      val decoded = take(GoalPhaseReviewRecoveryServiceOperation.decodeCommandC(encoded))
      Then("only the selected continuation identity is retained")
      decoded shouldBe identity
      Given("the same recovery wire with additional authority or result fields")
      val json = io.circe.parser.parse(encoded).toOption.get
      Vector("result", "handle", "revision", "approval", "collection", "decision").foreach { key =>
        When("an augmented recovery command is decoded")
        val result = GoalPhaseReviewRecoveryServiceOperation.decodeCommandC(json.mapObject(_.add(key, io.circe.Json.obj())).noSpaces)
        Then("the additional field is refused")
        result shouldBe a[Consequence.Failure[?]]
      }
      Vector(io.circe.Json.Null, io.circe.Json.fromInt(1), io.circe.Json.fromString(""), io.circe.Json.fromString(" review-1")).foreach { value =>
        When("a malformed continuation identity is decoded")
        val result = GoalPhaseReviewRecoveryServiceOperation.decodeCommandC(json.mapObject(_.add("continuationIdentity", value)).noSpaces)
        Then("the unsupported identity representation is refused")
        result shouldBe a[Consequence.Failure[?]]
      }
      Vector(null, "{}", "{").foreach { value =>
        When("an absent or malformed recovery payload is decoded")
        val result = GoalPhaseReviewRecoveryServiceOperation.decodeCommandC(value)
        Then("no recovery identity is produced")
        result shouldBe a[Consequence.Failure[?]]
      }
    }
  }
}
