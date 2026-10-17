package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path}
import io.circe.Json
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.cncf.security.SecuritySubject
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, InstanceRecord, Lifecycle, ProgressionReference}
import org.goldenport.protocol.Request
import org.goldenport.record.io.RecordDecoder
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.host.NativeHostRepositoryStartInput as Main
import org.simplemodeling.textus.smworkflow.value as V

/** Explicit files, metadata and controlled host claims are specification-owned.
  * Preparation and fixture Start establish no genuine installed task acceptance.
  * @since   Oct. 14, 2026
  * @version Oct. 15, 2026
  * @author  ASAMI, Tomoharu
  */
final class NativeHostRepositoryStartInputSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  private def _json(value: Record): Json = io.circe.parser.parse(value.toJsonStringC.toOption.get).toOption.get
  private val _input = V.RepositorySyncStartInput("sm-workflow", "main", "origin", "main", "owned-upstream", "FULL_STATE")
  private val _fields = Vector("repositoryIdentity", "localBranch", "trackingRemote", "trackingBranch",
    "remoteEndpointIdentity", "syncMode")
  private val _selection_collection = "entity:smworkflow.host.repository_selections"
  private val _args = Map("input" -> "input.json", "selection-id" -> "specification-repository-selection",
    "participant" -> "explicit-participant", "origin" -> "ExplicitHuman",
    "idempotency-key" -> "specification-repository-invocation", "selection-collection" -> _selection_collection)

  private def _read(text: String)(path: String): Consequence[String] =
    if (path == "input.json") Consequence.success(text)
    else Consequence.stateConflict("Selected specification file absent")
  private def _take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => throw new AssertionError(failure.toString)
  }
  private def _replace(key: String, value: String, args: Map[String, String] = _args): Map[String, String] =
    args.updated(key, value)
  private def _entries(value: Record): Vector[Json] =
    _json(value).hcursor.get[Vector[Json]]("datastore").toOption.get
  private def _row(entry: Json): Json = entry.hcursor.get[Vector[Json]]("records").toOption.get.head
  private def _selection(entries: Vector[Json]): ProfileStartAdmission.Selection =
    _take(ProfileSelectionJsonV1.decodeStorageC(_row(entries.head).hcursor
      .get[String](ProfileSelectionJsonV1.storageField).toOption.get))
  private def _business(input: V.RepositorySyncStartInput): NativeWorkflowStartInput.Repository =
    NativeWorkflowStartInput.Repository(input.repositoryIdentity, input.localBranch, input.trackingRemote,
      input.trackingBranch, input.remoteEndpointIdentity, input.syncMode)

  "E1 Explicit current selected Repository input" should afterWord(
    "in spec:native-host-repository-start-preparation, example:E1, rules:R1,R2,R3,R4, phase:6") {
    "emit one explicitly addressed row with canonical input and the actual packaged definition" in {
      Given("one selected business file, the current packaged Repository definition and all six explicit metadata fields")
      val definition = _take(RepositorySyncDefinitionBinding.bindC)
      var reads = Vector.empty[String]
      val reader: String => Consequence[String] = path => { reads :+= path; _read(_input.asJson.noSpaces)(path) }
      val args = _args.toVector.reverse.toMap

      When("the passive tool prepares that file with an independent ordering of finite fields")
      val text = _take(Main.prepareC(args, reader))
      val entries = _entries(text)
      val selection = _selection(entries)

      Then("exactly one collection and one business-key row preserve canonical meaning and explicit metadata")
      reads shouldBe Vector("input.json")
      _json(text).asObject.get.keys.toSet shouldBe Set("datastore")
      entries.size shouldBe 1
      entries.head.asObject.get.keys.toSet shouldBe Set("collection", "records")
      entries.head.hcursor.get[String]("collection").toOption shouldBe Some(_selection_collection)
      entries.head.hcursor.get[Vector[Json]]("records").toOption.get.size shouldBe 1
      _row(entries.head).asObject.get.keys.toSet shouldBe Set("id", ProfileSelectionJsonV1.storageField)
      _row(entries.head).hcursor.get[String]("id").toOption shouldBe
        Some(NativeWorkflowStartBusinessKey(_business(_input), "explicit-participant"))
      selection.selectionId shouldBe "specification-repository-selection"
      selection.startOperation shouldBe RepositorySyncManagedFetch.startOperation
      selection.workflowIdentity shouldBe definition.workflowIdentity
      selection.workflowRevision shouldBe definition.workflowRevision
      selection.participantIdentity shouldBe "explicit-participant"
      selection.origin shouldBe ProfileStartAdmission.SelectionOrigin.ExplicitHuman
      selection.idempotencyKey shouldBe "specification-repository-invocation"
      selection.recommendationReference shouldBe None
      selection.selectedInput.get.typeIdentity shouldBe NativeWorkflowStartInput.Kind.RepositorySync.inputType
      selection.selectedInput.get.value.as[V.RepositorySyncStartInput].toOption shouldBe Some(_input)
      selection.selectedInput.get.value.asObject.get.keys.toSet shouldBe _fields.toSet
    }
  }

  "E2 Canonical generated Repository reader" should afterWord(
    "in spec:native-host-repository-start-preparation, example:E2, rules:R1,R2,R3,R5, phase:6") {
    "normalize snake-case aliases and ignore unused authority-looking input claims" in {
      Given("all six generated snake-case fields and unused selection, participant, origin, permission, authentication, consent and argv claims")
      val input = Json.obj("repository_identity" -> Json.fromString(_input.repositoryIdentity),
        "local_branch" -> Json.fromString(_input.localBranch), "tracking_remote" -> Json.fromString(_input.trackingRemote),
        "tracking_branch" -> Json.fromString(_input.trackingBranch),
        "remote_endpoint_identity" -> Json.fromString(_input.remoteEndpointIdentity), "sync_mode" -> Json.fromString(_input.syncMode),
        "selectionId" -> Json.fromString("unselected"), "participantIdentity" -> Json.fromString("foreign"),
        "origin" -> Json.fromString("TerminalResult"), "idempotencyKey" -> Json.fromString("claimed"),
        "permission" -> Json.True, "authenticated" -> Json.True, "consent" -> Json.True,
        "argv" -> Json.arr(Json.fromString("push")))

      When("the passive tool reads the supplied business file through the generated ValueReader")
      val entries = _entries(_take(Main.prepareC(_args, _read(input.noSpaces))))
      val selection = _selection(entries)

      Then("only canonical business fields and explicit metadata reach the single selection row")
      selection.selectedInput.get.value.as[V.RepositorySyncStartInput].toOption shouldBe Some(_input)
      selection.selectedInput.get.value.asObject.get.keys.toSet shouldBe _fields.toSet
      selection.selectionId shouldBe "specification-repository-selection"
      selection.participantIdentity shouldBe "explicit-participant"
      selection.origin shouldBe ProfileStartAdmission.SelectionOrigin.ExplicitHuman
      selection.idempotencyKey shouldBe "specification-repository-invocation"
      selection.recommendationReference shouldBe None
      entries.map(_.hcursor.get[String]("collection").toOption.get) shouldBe Vector(_selection_collection)
      entries.head.hcursor.get[Vector[Json]]("records").toOption.get.size shouldBe 1
      _row(entries.head).asObject.get.keys.toSet shouldBe Set("id", ProfileSelectionJsonV1.storageField)
    }

    "return errors for every missing or null required field and malformed or nonobject JSON" in {
      Given("each of the six required fields omitted or null, plus malformed and nonobject selected JSON")
      val inputs = _fields.flatMap(field => Vector(
        _input.asJson.mapObject(_.remove(field)).noSpaces,
        _input.asJson.mapObject(_.add(field, Json.Null)).noSpaces)) ++
        Vector("{}", "null", "[]", "true", "17", "\"input\"", "{")

      When("the generated reader receives each invalid selected file")
      val results = inputs.map(input => Main.prepareC(_args, _read(input)))

      Then("every rejected file returns a Consequence error without successful importer JSON")
      results.foreach(_.toOption shouldBe None)
    }

    "preserve generated String conversion and canonical alias priority while leaving semantic admission native" in {
      Given("canonical fields with a conflicting alias, empty branch and endpoint, and a nonnull Boolean syncMode")
      val input = _input.asJson.mapObject(_.add("repository_identity", Json.fromString("foreign-alias"))
        .add("localBranch", Json.fromString("")).add("remoteEndpointIdentity", Json.fromString(""))
        .add("syncMode", Json.True))
      val expected = _input.copy(localBranch = "", remoteEndpointIdentity = "", syncMode = "true")

      When("the passive converter uses the ordinary generated reader without a second business policy")
      val entries = _entries(_take(Main.prepareC(_args, _read(input.noSpaces))))
      val selection = _selection(entries)

      Then("canonical priority and generated conversion survive for later native target and mode admission")
      selection.selectedInput.get.value.as[V.RepositorySyncStartInput].toOption shouldBe Some(expected)
      selection.selectedInput.get.value.asObject.get.keys.toSet shouldBe _fields.toSet
      _row(entries.head).hcursor.get[String]("id").toOption shouldBe
        Some(NativeWorkflowStartBusinessKey(_business(expected), "explicit-participant"))
      entries.size shouldBe 1
    }
  }

  "E3 Explicit finite Repository arguments" should afterWord(
    "in spec:native-host-repository-start-preparation, example:E3, rules:R1,R4, phase:6") {
    "reject omitted unknown null inexact origin and malformed collection metadata before reading" in {
      Given("the six required finite fields and variants omitting or corrupting their explicit metadata")
      val invalidargs: Vector[Map[String, String]] = Vector(null, Map.empty[String, String],
        _args.updated("permission", "granted"), _args.updated("help", "true")) ++
        _args.keys.toVector.map(_args - _) ++ _args.keys.toVector.flatMap(key =>
          Vector[String](null, "", " ", " trimmed", "trailing ", "control\nvalue").map(value => _args.updated(key, value))) ++
        Vector("Recommendation", "Skill", "Workflow", "TerminalResult", "explicitHuman")
          .map(value => _replace("origin", value)) ++
        Vector("smworkflow.host.selection", "entity:smworkflow.host", "entity:smworkflow..selection",
          "entity:smworkflow.host.bad-name", "entity:1smworkflow.host.selection", "entity:smworkflow.host.selection.extra")
          .map(value => _replace("selection-collection", value))
      var reads = 0
      val reader: String => Consequence[String] = _ => { reads += 1; Consequence.stateConflict("No input should be read") }

      When("each invalid argument set reaches the passive converter")
      val results = invalidargs.map(args => Main.prepareC(args, reader))

      Then("each call fails before any selected file access")
      results.foreach(_.toOption shouldBe None)
      reads shouldBe 0
    }

    "contain absent failed null and throwing file readers as Consequence errors" in {
      Given("valid arguments with absent, failed, null-result, null-text or throwing callbacks")
      val failed: String => Consequence[String] = _ => Consequence.stateConflict("Selected file unavailable")
      val nullresult: String => Consequence[String] = _ => null
      val nulltext: String => Consequence[String] = _ => Consequence.success(null)
      val throwing: String => Consequence[String] = _ => throw new IllegalArgumentException("Selected file failure")

      When("the converter receives each unavailable reader, including a null reader on an empty field set")
      val results = Vector(Main.prepareC(_args, null), Main.prepareC(Map.empty[String, String], null),
        Main.prepareC(_args, failed), Main.prepareC(_args, nullresult),
        Main.prepareC(_args, nulltext), Main.prepareC(_args, throwing))

      Then("each callback failure is contained without successful importer JSON")
      results.foreach(_.toOption shouldBe None)
    }

    "refuse an unknown field without acquiring selected input" in {
      Given("valid finite metadata with an additional unsupported authority field")
      var reads = 0
      When("the passive decoder validates that field set")
      val result = Main.prepareC(_args.updated("authority", "claimed"), _ => {
        reads += 1; Consequence.stateConflict("No acquisition expected")
      })
      Then("no structured importer or file acquisition is produced")
      result.toOption shouldBe None
      reads shouldBe 0
    }
  }

  "E4 Prepared Repository selection at the actual native Start consumer" should afterWord(
    "in spec:native-host-repository-start-preparation, example:E4, rules:R1,R2,R3,R5,R6, phase:6") {
    "refuse a missing business entry and consume only the prepared row in a NEW public Start" in {
      Given("a fresh controlled managed host episode with its actual Repository input and no selected business row")
      val work = Files.createDirectories(Path.of("target/sm-workflow/work/repository-start-preparation").toAbsolutePath)
      val root = Files.createTempDirectory(work, "selected-entry-")
      val input = ThreeWorkflowNormalHostFixture.definition(root).repository.input
      _with_runtime_(root, "Equal") { (owner, component, actor, prepare) =>
        val participant = SecuritySubject.from(actor.security).subjectId
        val failedproposal = "specification-missing-repository-entry"
        val newproposal = "specification-new-repository-entry"
        def _start_(proposal: String) = owner.executeOperationResponseInContext(Request.of(component = component.name,
          service = "RepositorySyncService", operation = "startRepositorySync",
          properties = NativeWorkflowOperationInputFixture.repository(input, Some(proposal))), actor)

        When("the public Start is invoked with a separate explicit proposed execution before row provisioning")
        val refused = _start_(failedproposal)

        Then("the actual native consumer refuses the missing host business entry")
        refused.isSuccess shouldBe false
        refused.toString should include("no admitted host business entry")

        Given("the host's generated business JSON and explicit specification-owned selection metadata matching its actor")
        val args = _replace("participant", participant,
          _replace("selection-collection", "entity:smworkflow.cli.repository_selections"))

        When("the passive input helper prepares one row and normal managed Create provisions its emitted selection payload under the separate fixed business-key identity")
        val entries = _entries(_take(Main.prepareC(args, _read(input.asJson.noSpaces))))
        val row = _take(new RecordDecoder().json(_row(entries.head).noSpaces))
        val identity = row.getString("id").get
        val payload = row.removeKeys(Set("id"))
        prepare(setup => setup.createC("repository_selections", identity, payload))

        Then("the emitted business-key row keeps its identity separate from the unchanged managed selection payload with the current actor and target")
        entries.map(_.hcursor.get[String]("collection").toOption.get) shouldBe
          Vector("entity:smworkflow.cli.repository_selections")
        entries.head.hcursor.get[Vector[Json]]("records").toOption.get.size shouldBe 1
        row.keySet shouldBe Set("id", ProfileSelectionJsonV1.storageField)
        payload.keySet shouldBe Set(ProfileSelectionJsonV1.storageField)
        payload.getString(ProfileSelectionJsonV1.storageField) shouldBe
          Some(_row(entries.head).hcursor.get[String](ProfileSelectionJsonV1.storageField).toOption.get)
        identity shouldBe NativeWorkflowStartBusinessKey(_business(input), participant)
        _selection(entries).participantIdentity shouldBe participant
        _selection(entries).selectedInput.get.value.as[V.RepositorySyncStartInput].toOption shouldBe Some(input)

        Given("a different proposed execution after provisioning, with no Advance or effect input")
        When("a NEW public Start consumes the prepared row and its actual returned ID is used for a managed read")
        val started = read_value[V.RepositorySyncStartResult](_take(_start_(newproposal)))
        val actualid = started.sessionId
        var current = Option.empty[InstanceRecord]
        prepare(setup => setup.currentC(InstanceIdentity(actualid)).map(value => { current = Some(value); () }))

        Then("the actual execution is Active at native Start and claims no fetch, push or completed result")
        actualid should not be empty
        actualid should not be failedproposal
        started.outcome shouldBe "started"
        started.message shouldBe "Started repository synchronization for " + input.repositoryIdentity + " on " +
          input.localBranch + ". Advance owns the next Git assessment; no fetch or push is claimed."
        current.get.identity shouldBe InstanceIdentity(actualid)
        current.get.lifecycle shouldBe Lifecycle.Active
        current.get.currentProgression shouldBe Some(ProgressionReference("sync.BoundaryAssessment"))
        current.get.correlation.value shouldBe "specification-repository-invocation"
      }
    }
  }
}
