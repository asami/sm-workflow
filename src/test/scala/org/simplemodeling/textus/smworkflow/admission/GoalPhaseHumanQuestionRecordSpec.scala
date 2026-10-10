package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
final class GoalPhaseHumanQuestionRecordSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import HumanDecisionContractFixture.*
  import GoalPhaseHumanQuestionRecord.*
  private def row(value: GoalPhaseHumanDecisionContract.Question = question) = Record.data(storageField -> take(encodeStorageC(value)))
  private final class Storage extends GoalPhaseClosureDecisionRetention.Port {
    var retained = Option.empty[Record]
    var reads = 0
    var creates = 0
    var fault = "ordinary"
    def loadC(): Consequence[Option[Record]] = {
      reads += 1
      if (creates == 0 && fault == "initial-failed") Consequence.stateConflict("Initial read failed")
      else if (creates == 0 && fault == "initial-throw") throw new IllegalStateException("Initial read threw")
      else if (creates > 0 && fault == "missing") Consequence.success(None)
      else if (creates > 0 && fault == "malformed") Consequence.success(Some(Record.data(storageField -> "invalid")))
      else if (creates > 0 && fault == "read-throw") throw new IllegalStateException("Readback threw")
      else Consequence.success(retained)
    }
    def createC(value: Record) = {
      creates += 1
      value.fields.map(_.key).toSet shouldBe Set(storageField)
      retained = Some(value)
      fault match {
        case "lost-create" => Consequence.stateConflict("Create acknowledgement lost")
        case "create-throw" => throw new IllegalStateException("Create acknowledgement threw")
        case _ => Consequence.unit
      }
    }
  }
  "Answerable question record" should {
    "round-trip the complete immutable intent, routes, answer type and visible choice contract" in {
      Given("an answerable question with its complete immutable visible contract")
      val selected = question
      When("the question is encoded and decoded through its storage codec")
      val decoded = take(decodeStorageC(take(encodeStorageC(selected))))
      Then("all question fields and the declared result type are retained")
      decoded shouldBe question
      question.request.resultType.nonEmpty shouldBe true
    }
    "refuse altered schemas, root and nested fields, legacy stop wire and unbounded storage" in {
      Given("the valid question wire and an inert legacy stop interaction")
      val raw = take(encodeStorageC(question))
      val offset = raw.indexOf('{')
      val prefix = raw.take(offset)
      val json = io.circe.parser.parse(raw.drop(offset)).toOption.get
      val legacy = take(org.goldenport.cncf.workflow.WorkflowDecisionJsonV1.encodeC(
        org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowInteraction[GoalPhaseClosureAuthority.Input, Nothing](handle,
          org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowContinuation.Decision(take(routing.requestC(intent)),
            org.goldenport.cncf.workflow.WorkflowProtocolV1.MinimalPresentation("Stop", "No answer"))), GoalPhaseClosureAuthority.inputCodec))
      Vector(json.mapObject(_.add("approval", io.circe.Json.fromBoolean(true))),
        json.mapObject(_.add("schemaVersion", io.circe.Json.fromString("foreign"))),
        json.mapObject(_.add("questionSchema", io.circe.Json.fromString("foreign"))),
        json.mapObject(_.add("question", io.circe.Json.fromString(legacy))),
        json.mapObject(_.add("choices", io.circe.Json.arr(io.circe.Json.obj("identity" -> io.circe.Json.fromString("x"))))))
        .foreach { value =>
          When("a changed schema, nested field or legacy question is decoded")
          val result = decodeStorageC(prefix + value.noSpaces)
          Then("the altered question is refused")
          result shouldBe a[Consequence.Failure[?]]
        }
      Vector(null, "", "{}", raw + ("x" * (1024 * 1024))).foreach { value =>
        When("missing, malformed or oversized question storage is decoded")
        val result = decodeStorageC(value)
        Then("no answerable question is reconstructed")
        result shouldBe a[Consequence.Failure[?]]
      }
    }
    "Create once and return only committed readback, with exact retry preserving the same row" in {
      Given("an empty question store and the same immutable question for both requests")
      val storage = new Storage
      When("the question is retained for the first time")
      val initial = take(retainC(question, storage))
      Then("the question is returned only after one Create and committed readback")
      initial shouldBe question
      storage.creates shouldBe 1
      storage.reads shouldBe 2
      val first = storage.retained
      When("the identical question is retained again")
      val repeated = take(retainC(question, storage))
      Then("the exact row is returned without another Create")
      repeated shouldBe question
      storage.creates shouldBe 1
      storage.retained shouldBe first
    }
    "consume the selected question and declared choices without a proposed-body equality proof" in {
      Given("a saved question with its own valid visible labels and diagnostic context")
      val changed = question.copy(choices = choices.updated(0, choices.head.copy(label = "Another question")))
      val storage = new Storage
      val saved = row(changed)
      storage.retained = Some(saved)
      When("retention receives a later proposal for the same allocated question and target")
      val actual = take(retainC(question, storage))
      Then("saved declarations are consumed without comparing or replacing their full body")
      actual shouldBe changed
      storage.creates shouldBe 0
      storage.reads shouldBe 1
      storage.retained shouldBe Some(saved)
      actual.admitAnswerC(GoalPhaseHumanDecisionContract.Answer(choices.head.identity)).isSuccess shouldBe true
      actual.admitAnswerC(GoalPhaseHumanDecisionContract.Answer("not-declared")).isSuccess shouldBe false
    }
    "refuse foreign allocated slots, malformed rows and initial observation failures without Create" in {
      Given("a question belonging to another allocated slot or invalid existing rows")
      val foreign = question.copy(request = question.request.copy(
        continuationId = org.goldenport.cncf.workflow.ContinuationIdentity("another-question")))
      Vector(row(foreign), Record.data(storageField -> "invalid"), Record.data("unknown" -> "payload"), null).foreach { value =>
        val storage = new Storage
        storage.retained = Some(value)
        When("retention observes the conflicting or malformed existing row")
        val result = retainC(question, storage)
        Then("the row is refused without Create or replacement")
        result shouldBe a[Consequence.Failure[?]]
        storage.creates shouldBe 0
        storage.retained shouldBe Some(value)
      }
      Vector("initial-failed", "initial-throw").foreach { fault =>
        val storage = new Storage
        storage.fault = fault
        When("the initial question observation fails or throws")
        val result = retainC(question, storage)
        Then("retention fails without attempting Create")
        result shouldBe a[Consequence.Failure[?]]
        storage.creates shouldBe 0
      }
    }
    "keep post-Create uncertainty and recover the same row without replacing or re-creating it" in {
      Vector("lost-create", "create-throw", "missing", "malformed", "read-throw").foreach { fault =>
        Given("an empty store whose Create acknowledgement or committed observation is uncertain")
        val storage = new Storage
        storage.fault = fault
        When("the question is retained through the selected post-Create failure")
        val result = retainC(question, storage)
        Then("the result remains transaction-indeterminate rather than publishing success")
        result match {
          case Consequence.Failure(conclusion) => conclusion.observation.cause.descriptor.facets.collect {
            case org.goldenport.observation.Descriptor.Facet.Reason(value) => value
          } should contain("transaction-indeterminate")
          case other => fail(s"Uncertain question published success: $other")
        }
        val created = storage.retained
        storage.creates shouldBe 1
        storage.fault = "ordinary"
        When("a later ordinary observation retries the same question")
        val recovered = take(retainC(question, storage))
        Then("the original committed row is recovered without replacement or another Create")
        recovered shouldBe question
        storage.retained shouldBe created
        storage.creates shouldBe 1
      }
    }
    "require a separate exact question declaration disjoint from Workflow, stop metadata and artifacts" in {
      import org.goldenport.cncf.datastore.DataStore
      import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
      import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
      import org.simplemodeling.model.datatype.EntityCollectionId
      Given("one Workflow declaration and distinct logical question, host-record and artifact collections")
      def collection(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "humanquestions", name))
      val runtime = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("human-question-spec")),
        MigrationPolicy("v1"), RetentionPolicy("v1"), LeasePolicy("v1"), DeliveryConfiguration.SameStore(WorkflowStoreIdentity("human-question-spec")))
      val workflow = GoalPhaseManagedStartAssembly.Declaration(runtime, null, collection("instances"), collection("continuations"))
      val records = GoalPhaseHostRecordReadPolicy.Purpose.values.toVector.map(role => role -> collection(role.toString.toLowerCase))
      val questions = collection("questions")
      val artifact = collection("artifacts")
      val prefix = GoalPhaseHumanQuestionStoragePolicy.prefix
      val values: Map[String, ConfigurationValue] = Map(
        prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
        prefix + "question-collection" -> ConfigurationValue.StringValue(questions.print),
        GoalPhaseHostRecordReadPolicy.prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
        GoalPhaseArtifactReadPolicy.prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
        GoalPhaseArtifactReadPolicy.prefix + "reference-collection" -> ConfigurationValue.StringValue(artifact.print)) ++
        records.map((role, id) => GoalPhaseHostRecordReadPolicy.prefix + role.key -> ConfigurationValue.StringValue(id.print))
      def decode(data: Map[String, ConfigurationValue]) = GoalPhaseHumanQuestionStoragePolicy.decodeC(
        ResolvedConfiguration(HostConfiguration(data), ConfigurationTrace.empty), workflow)
      When("the complete disjoint question configuration is decoded")
      val declaration = take(decode(values))
      Then("the exact question store and collection are selected")
      declaration shouldBe GoalPhaseHumanQuestionStoragePolicy.Declaration("workflow", questions)
      Vector(workflow.instances, workflow.continuations, artifact).concat(records.map(_._2)).foreach { id =>
        When("the question collection aliases a Workflow, host-record or artifact collection")
        val result = decode(values.updated(prefix + "question-collection", ConfigurationValue.StringValue(id.print)))
        Then("the overlapping declaration is refused")
        result shouldBe a[Consequence.Failure[?]]
      }
      When("the question collection is missing")
      val missing = decode(values.removed(prefix + "question-collection"))
      Then("an incomplete declaration is refused")
      missing shouldBe a[Consequence.Failure[?]]
      When("the question store name differs from the owning Workflow store")
      val foreign = decode(values.updated(prefix + "store-name", ConfigurationValue.StringValue("other")))
      Then("the foreign store declaration is refused")
      foreign shouldBe a[Consequence.Failure[?]]
    }
  }
}
