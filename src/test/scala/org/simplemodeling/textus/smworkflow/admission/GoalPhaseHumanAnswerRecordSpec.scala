package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentInstanceId
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
final class GoalPhaseHumanAnswerRecordSpec extends AnyWordSpec with Matchers with GivenWhenThen with TableDrivenPropertyChecks {
  import HumanDecisionContractFixture.*
  import GoalPhaseHumanInteractionVerification.*
  import GoalPhaseHumanDecisionContract.Answer
  import GoalPhaseHumanAnswerRecord.*
  private val expected = Binding(ComponentInstanceId.default(handle.componentIdentity), question, Answer("keep-open"), "relay")
  private val now = Instant.parse("2026-10-01T00:00:00Z")
  private val receipt = Receipt("interaction-1", "fixture-issuer", "human", purpose, expected, now)
  private final class Storage extends GoalPhaseClosureDecisionRetention.Port {
    var retained = Option.empty[Record]
    var creates = 0
    var fault = "ordinary"
    def loadC(): Consequence[Option[Record]] =
      if (creates == 0 && fault == "initial-failed") Consequence.stateConflict("Initial read failed")
      else if (creates > 0 && fault == "missing") Consequence.success(None)
      else if (creates > 0 && fault == "malformed") Consequence.success(Some(Record.data(storageField -> "invalid")))
      else if (creates > 0 && fault == "read-throw") throw new IllegalStateException("Readback threw")
      else Consequence.success(retained)
    def createC(value: Record) = {
      creates += 1
      retained = Some(if (fault == "compatible") Record.data(storageField -> take(encodeStorageC(compatible))) else value)
      fault match {
        case "lost-create" => Consequence.stateConflict("Create acknowledgement lost")
        case "create-throw" => throw new IllegalStateException("Create acknowledgement threw")
        case _ => Consequence.unit
      }
    }
  }
  private def compatible = receipt.copy(binding = expected.copy(question = question.copy(
    intent = question.intent.copy(snapshot = question.intent.snapshot.copy(workspaceRevision = Some("saved-diagnostic-workspace"))),
    choices = question.choices.map(value => value.copy(label = "Saved " + value.label)),
    request = question.request.copy(context = question.request.context.copy(summary = "Saved display context",
      snapshot = question.request.context.snapshot.copy(workspaceRevision = Some("saved-diagnostic-workspace")))))))
  "Immutable human answer record" should {
    "read historical answers with obsolete expiry without rewriting or reverifying their attribution" in {
      Given("the retained question and answer in the historical v1 storage envelope")
      val legacyversion = "sm-workflow.goal-phase-human-answer-record.v1"
      val legacyprefix = "sm-workflow.goal-phase-human-answer-json.v1:"
      val current = take(encodeStorageC(receipt))
      val payload = io.circe.parser.parse(current.drop(current.indexOf('{'))).toOption.get
        .mapObject(_.add("schemaVersion", io.circe.Json.fromString(legacyversion)))
      forAll(Table("obsoleteExpiry", None, Some(io.circe.Json.fromString(now.minusSeconds(1).toString)),
        Some(io.circe.Json.fromString("no-longer-parsed")), Some(io.circe.Json.Null))) { expiry =>
        val raw = legacyprefix + expiry.fold(payload)(value => payload.mapObject(_.add("validUntil", value))).noSpaces
        val storage = new Storage
        storage.retained = Some(Record.data(storageField -> raw))
        When("the legacy receipt is decoded and the same answer is observed through ordinary retention")
        val decoded = take(decodeStorageC(raw))
        val retained = take(retainC(expected, receipt.reference, storage)(Consequence.stateConflict("Must not reverify")))
        Then("the historical attribution/event time remain and neither renewal, Create nor a stored-row rewrite occurs")
        decoded shouldBe receipt
        retained shouldBe receipt
        storage.creates shouldBe 0
        storage.retained.get.getString(storageField) shouldBe Some(raw)
      }
      When("current output and a crossed prefix or caller-added claim are decoded")
      val currentjson = io.circe.parser.parse(current.drop(current.indexOf('{'))).toOption.get
      Then("current output contains no expiry and historical recognition admits no other undeclared claims")
      currentjson.asObject.get.keys.toSet should not contain "validUntil"
      decodeStorageC(legacyprefix + currentjson.noSpaces).isSuccess shouldBe false
      decodeStorageC(legacyprefix + payload.mapObject(_.add("claimedApproval", io.circe.Json.True)).noSpaces).isSuccess shouldBe false
    }
    "round-trip the exact answer, visible question, component, human and relay attribution" in {
      Given("a structurally valid fixture receipt, not a production human admission")
      val wire = take(encodeStorageC(receipt))
      When("the closed storage payload is decoded")
      val decoded = take(decodeStorageC(wire))
      Then("every attribution and binding survives without creating an Admitted capability")
      decoded shouldBe receipt
      encodeStorageC(receipt.copy(binding = expected.copy(question = question.copy(intent = null)))) shouldBe a[Consequence.Failure[?]]
      val offset = wire.indexOf('{')
      val prefix = wire.take(offset)
      val json = io.circe.parser.parse(wire.drop(offset)).toOption.get
      Vector(json.mapObject(_.add("approval", io.circe.Json.True)),
        json.mapObject(_.add("schemaVersion", io.circe.Json.fromString("foreign"))),
        json.mapObject(_.add("purpose", io.circe.Json.fromString("command-authorization"))),
        json.mapObject(_.add("occurredAt", io.circe.Json.fromString("invalid"))),
        json.mapObject(_.add("validUntil", io.circe.Json.fromString(now.minusSeconds(1).toString))))
        .foreach(value => decodeStorageC(prefix + value.noSpaces) shouldBe a[Consequence.Failure[?]])
      decodeStorageC(wire.drop(offset)) shouldBe a[Consequence.Failure[?]]
      decodeStorageC(wire + ("x" * (2 * 1024 * 1024))) shouldBe a[Consequence.Failure[?]]
    }
    "verify only absence and observe the original receipt on exact retry" in {
      Given("an absent immutable answer row and an independently supplied verification callback")
      val storage = new Storage
      var verifications = 0
      def verify = { verifications += 1; Consequence.success(receipt) }
      When("the first attempt and exact retry retain the same answer")
      take(retainC(expected, receipt.reference, storage)(verify)) shouldBe receipt
      val row = storage.retained
      take(retainC(expected, receipt.reference, storage)(verify)) shouldBe receipt
      Then("one Create and one verification retain unchanged evidence")
      storage.creates shouldBe 1
      verifications shouldBe 1
      storage.retained shouldBe row
      retainC(expected.copy(answer = Answer("request-authorization")), receipt.reference, storage)(verify) shouldBe a[Consequence.Failure[?]]
      retainC(expected, "foreign-interaction", storage)(verify) shouldBe a[Consequence.Failure[?]]
      verifications shouldBe 1
      storage.retained shouldBe row
    }
    "consume actual committed and legacy saved question diagnostics without a complete receipt witness" in {
      Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R5; Example: E16; a structurally valid stored answer with the same actual question target and choice")
      val storage = new Storage
      storage.fault = "compatible"
      var admissions = 0
      def observed = { admissions += 1; Consequence.success(receipt) }
      When("the owning writer receives actual readback rather than its complete emitted copy")
      val committed = take(retainC(expected, receipt.reference, storage)(observed))
      Then("the actual saved record is consumed without requiring its visible text to match the emitted receipt")
      committed shouldBe compatible
      committed should not be receipt
      storage.creates shouldBe 1
      admissions shouldBe 1
      val raw = storage.retained.get.getString(storageField).get
      val json = io.circe.parser.parse(raw.drop(raw.indexOf('{'))).toOption.get
        .mapObject(_.add("schemaVersion", io.circe.Json.fromString("sm-workflow.goal-phase-human-answer-record.v1"))
          .add("validUntil", io.circe.Json.fromString("ignored-old-expiry")))
      val legacy = "sm-workflow.goal-phase-human-answer-json.v1:" + json.noSpaces
      storage.retained = Some(Record.data(storageField -> legacy))
      When("the selected original legacy answer is retried without a new admission callback")
      val retained = take(retainC(expected, receipt.reference, storage)(Consequence.stateConflict("Must not readmit")))
      Then("the saved question and attribution survive without rewrite renewal or a full-body comparison")
      retained shouldBe compatible
      storage.retained.get.getString(storageField) shouldBe Some(legacy)
      storage.creates shouldBe 1
      admissions shouldBe 1
      When("the real question slot or model scope is foreign")
      val foreign = Vector(expected.copy(question = question.copy(request = question.request.copy(
        continuationId = org.goldenport.cncf.workflow.ContinuationIdentity("foreign-question")))),
        expected.copy(question = question.copy(intent = question.intent.copy(snapshot = question.intent.snapshot.copy(modelRevision = Some("foreign-model"))),
          request = question.request.copy(context = question.request.context.copy(snapshot =
            question.request.context.snapshot.copy(modelRevision = Some("foreign-model")))))))
      val refusals = foreign.map(value => retainC(value, receipt.reference, storage)(observed))
      Then("actual target conflicts reject without another admission or Create")
      refusals.foreach(_.isSuccess shouldBe false)
      storage.creates shouldBe 1
      admissions shouldBe 1
    }
    "refuse failed verification, mismatched receipts and failed initial reads before Create" in {
      Given("an absent row and adversarial verification outcomes")
      val outcomes = Vector(Consequence.stateConflict[Receipt]("unavailable"), Consequence.success(receipt.copy(reference = "foreign")),
        Consequence.success(receipt.copy(binding = expected.copy(answer = Answer("request-authorization")))))
      When("retention receives unavailable or different human evidence")
      outcomes.foreach { outcome =>
        val storage = new Storage
        retainC(expected, receipt.reference, storage)(outcome) shouldBe a[Consequence.Failure[?]]
        Then("no answer is created for that rejected evidence")
        storage.creates shouldBe 0
      }
      val storage = new Storage
      storage.fault = "initial-failed"
      var verified = false
      retainC(expected, receipt.reference, storage) { verified = true; Consequence.success(receipt) } shouldBe a[Consequence.Failure[?]]
      verified shouldBe false
      storage.creates shouldBe 0
    }
    "withhold post-Create uncertainty and recover the original row without reverification" in {
      Given("fixture storage faults after insertion; these are not SQLite driver faults")
      Vector("lost-create", "create-throw", "missing", "malformed", "read-throw").foreach { fault =>
        val storage = new Storage
        storage.fault = fault
        When(s"the committed observation is uncertain: $fault")
        retainC(expected, receipt.reference, storage)(Consequence.success(receipt)) match {
          case Consequence.Failure(conclusion) => conclusion.observation.cause.descriptor.facets.collect {
            case org.goldenport.observation.Descriptor.Facet.Reason(value) => value
          } should contain("transaction-indeterminate")
          case other => fail(s"Uncertain answer published success: $other")
        }
        val row = storage.retained
        storage.fault = "ordinary"
        Then("ordinary retry observes the inserted receipt without another Create or verification")
        take(retainC(expected, receipt.reference, storage)(Consequence.stateConflict("Must not reverify"))) shouldBe receipt
        storage.creates shouldBe 1
        storage.retained shouldBe row
      }
    }
  }
}
