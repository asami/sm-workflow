package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.preparation.NativeGoalStartInput as Main
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseWorkClassification as W

/** Explicit files and selection metadata are specification-owned inputs.
  * Successful preparation is no authenticated selection or native admission.
  * @since   Oct. 14, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class NativeGoalStartInputSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _json(value: Record): Json = io.circe.parser.parse(value.toJsonStringC.toOption.get).toOption.get
  private val _input = V.GoalPhaseStartInput("phase-6", "sm-workflow")
  private val _coding = W.Classification(W.Kind.CODING, W.Complexity.COMPLEX,
    W.Domain.PROGRAMMING, W.Footprint.MEDIUM)
  private val _review = W.Classification(W.Kind.REVIEW, W.Complexity.STANDARD,
    W.Domain.ENGINEERING, W.Footprint.SMALL)
  private val _assignment = W.Assignment(_coding, _coding.copy(complexity = W.Complexity.STANDARD), _review)
  private val _plan = GoalPhaseOrderedProgression.Plan("explicit-plan", "revision-2", _input.phaseIdentity,
    _input.repositoryIdentity, Vector(
      GoalPhaseOrderedProgression.Step("step-1", "Implement selected input", Vector("src/main/Input.scala"),
        Vector.empty, Vector("focused-input"), Some(_assignment)),
      GoalPhaseOrderedProgression.Step("step-2", "Document selected input", Vector("docs/input.md"),
        Vector("step-1"), Vector("focused-docs"))), Vector("phase-check"), 2, Some(_review))
  private val _selection_collection = "entity:smworkflow.host.goal_selection"
  private val _plan_collection = "entity:smworkflow.host.goal_plans"
  private val _args = Map("input" -> "input.json", "plan" -> "plan.json", "selection-id" -> "selected-goal",
    "participant" -> "explicit-participant", "origin" -> "ExplicitHuman", "idempotency-key" -> "explicit-key",
    "selection-collection" -> _selection_collection, "plan-collection" -> _plan_collection)
  private def _files(input: Json = _input.asJson, plan: Json = _plan.asJson): Map[String, String] =
    Map("input.json" -> input.noSpaces, "plan.json" -> plan.noSpaces)
  private def _read(files: Map[String, String])(path: String): Consequence[String] =
    files.get(path).map(Consequence.success).getOrElse(Consequence.stateConflict("Selected specification file absent"))
  private def _take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => throw new AssertionError(failure.toString)
  }
  private def _replace(key: String, value: String): Map[String, String] = _args.updated(key, value)
  private def _entries(value: Record): Vector[Json] =
    _json(value).hcursor.get[Vector[Json]]("datastore").toOption.get
  private def _row(entry: Json): Json = entry.hcursor.get[Vector[Json]]("records").toOption.get.head
  private def _selection(entries: Vector[Json]): ProfileStartAdmission.Selection =
    _take(ProfileSelectionJsonV1.decodeStorageC(_row(entries.head).hcursor
      .get[String](ProfileSelectionJsonV1.storageField).toOption.get))
  private def _selected_plan(entries: Vector[Json]): GoalPhaseNativeRun.SelectedPlan =
    _take(GoalPhaseSelectedPlanJsonV1.decodeStorageC(_row(entries(1)).hcursor
      .get[String](GoalPhaseSelectedPlanJsonV1.storageField).toOption.get))

  "E1 Explicit current selected input" should afterWord(
    "in spec:native-host-goal-start-preparation, example:E1, rules:R1,R3,R4,R5, phase:6") {
    "emit exactly the two explicitly addressed rows with current typed input and complete selected Plan" in {
      Given("two explicit business and Plan files, current packaged Goal definition, and all eight selected metadata fields")
      val files = _files()
      val definition = _take(GoalPhaseRuntimeCatalog.loadC()).definition
      var reads = Vector.empty[String]
      val reader: String => Consequence[String] = path => { reads :+= path; _read(files)(path) }
      val args = _args.toVector.reverse.toMap

      When("the passive tool prepares the files using an independent ordering of finite fields")
      val entries = _entries(_take(Main.prepareC(args, reader)))
      val selection = _selection(entries)
      val selectedplan = _selected_plan(entries)

      Then("only the selected input and Plan collections contain one canonical-owner encoded row each")
      reads shouldBe Vector("input.json", "plan.json")
      entries.map(_.hcursor.get[String]("collection").toOption.get) shouldBe
        Vector(_selection_collection, _plan_collection)
      entries.foreach { entry =>
        entry.asObject.get.keys.toSet shouldBe Set("collection", "records")
        entry.hcursor.get[Vector[Json]]("records").toOption.get.size shouldBe 1
      }
      _row(entries.head).asObject.get.keys.toSet shouldBe Set("id", ProfileSelectionJsonV1.storageField)
      _row(entries(1)).asObject.get.keys.toSet shouldBe Set("id", GoalPhaseSelectedPlanJsonV1.storageField)
      _row(entries.head).hcursor.get[String]("id").toOption shouldBe
        Some(NativeWorkflowStartBusinessKey(NativeWorkflowStartInput.Goal(_input), "explicit-participant"))
      _row(entries(1)).hcursor.get[String]("id").toOption shouldBe Some("selected-goal")
      selection.selectionId shouldBe "selected-goal"
      selection.startOperation shouldBe GoalPhaseNativeRun.startOperation
      selection.workflowIdentity shouldBe definition.workflowIdentity
      selection.workflowRevision shouldBe definition.workflowRevision
      selection.participantIdentity shouldBe "explicit-participant"
      selection.origin shouldBe ProfileStartAdmission.SelectionOrigin.ExplicitHuman
      selection.idempotencyKey shouldBe "explicit-key"
      selection.recommendationReference shouldBe None
      selection.selectedInput.get.typeIdentity shouldBe NativeWorkflowStartInput.Kind.GoalPhase.inputType
      selection.selectedInput.get.value.as[V.GoalPhaseStartInput].toOption shouldBe Some(_input)
      selection.selectedInput.get.value.asObject.get.keys.toSet shouldBe Set("phaseIdentity", "repositoryIdentity")
      selectedplan.selectionIdentity shouldBe selection.selectionId
      selectedplan.participantIdentity shouldBe selection.participantIdentity
      selectedplan.plan shouldBe _plan
    }
  }

  "E2 Canonical input and Plan readers" should afterWord(
    "in spec:native-host-goal-start-preparation, example:E2, rules:R2,R3, phase:6") {
    "normalize generated wire aliases and ignore unused authority-looking diagnostics" in {
      Given("snake_case business fields and unused claimed selection, permission, consent and runtime diagnostics in both files")
      val input = Json.obj("phase_identity" -> Json.fromString(_input.phaseIdentity),
        "repository_identity" -> Json.fromString(_input.repositoryIdentity),
        "origin" -> Json.fromString("TerminalResult"), "selectionId" -> Json.fromString("unselected"),
        "participantIdentity" -> Json.fromString("foreign"), "authenticated" -> Json.True,
        "consent" -> Json.True, "nativeHandle" -> Json.obj("instance" -> Json.fromString("claimed")))
      val plan = _plan.asJson.mapObject(_.add("permission", Json.True).add("admitted", Json.True)
        .add("workflowRevision", Json.fromString("claimed")))

      When("the generated ValueReader and existing Plan Decoder consume the supplied files")
      val entries = _entries(_take(Main.prepareC(_args, _read(_files(input, plan)))))
      val selection = _selection(entries)

      Then("canonical business meaning and explicit metadata survive without diagnostic claims in either selected row")
      selection.selectedInput.get.value.as[V.GoalPhaseStartInput].toOption shouldBe Some(_input)
      selection.selectedInput.get.value.asObject.get.keys.toSet shouldBe Set("phaseIdentity", "repositoryIdentity")
      selection.origin shouldBe ProfileStartAdmission.SelectionOrigin.ExplicitHuman
      selection.selectionId shouldBe "selected-goal"
      selection.participantIdentity shouldBe "explicit-participant"
      _selected_plan(entries).plan shouldBe _plan
      entries.size shouldBe 2
    }

    "return failure for business fields rejected by the generated reader and structurally invalid Plan JSON" in {
      Given("missing or null required business fields, malformed JSON, and Plan fields with incompatible structural types")
      val inputvariants = Vector("{}", "null", "[]", "{",
        "{\"phaseIdentity\":null,\"repositoryIdentity\":\"sm-workflow\"}",
        "{\"phaseIdentity\":\"phase-6\"}")
      val planvariants = Vector("{}", "null", "[]", "{",
        _plan.asJson.mapObject(_.add("steps", Json.fromString("invalid"))).noSpaces,
        _plan.asJson.mapObject(_.add("repairLimit", Json.fromString("invalid"))).noSpaces,
        _plan.asJson.mapObject(_.add("phaseChecks", Json.True)).noSpaces)
      val baseline = _files()

      When("the passive tool reads each rejected business or Plan file instead of the selected valid file")
      val results = inputvariants.map(raw => Main.prepareC(_args, _read(baseline.updated("input.json", raw)))) ++
        planvariants.map(raw => Main.prepareC(_args, _read(baseline.updated("plan.json", raw))))

      Then("each canonical decoding failure returns an error without successful importer output")
      results.foreach(_.toOption shouldBe None)
    }

    "leave semantic scope and Plan admission with the native owners" in {
      Given("structurally readable business input and a Plan with foreign scope, no Steps and an invalid semantic repair allowance")
      val plan = _plan.copy(phase = "foreign-phase", repository = "foreign-repository", steps = Vector.empty, repairLimit = -1)

      When("the converter prepares the supplied Plan without starting or validating a Workflow")
      val entries = _entries(_take(Main.prepareC(_args, _read(_files(plan = plan.asJson)))))

      Then("the selected Plan retains those values for later native acceptance and no runtime row appears")
      _selected_plan(entries).plan shouldBe plan
      _selection(entries).selectedInput.get.value.as[V.GoalPhaseStartInput].toOption shouldBe Some(_input)
      entries.map(_.hcursor.get[String]("collection").toOption.get) shouldBe
        Vector(_selection_collection, _plan_collection)
    }
  }

  "E3 Explicit finite arguments" should afterWord(
    "in spec:native-host-goal-start-preparation, example:E3, rules:R1,R2,R4,R5, phase:6") {
    "reject omitted unknown null empty inexact origin and collection metadata before reading files" in {
      Given("the eight required finite fields and bounded variants that omit, duplicate, replace or corrupt selected metadata")
      val invalidargs: Vector[Map[String, String]] = Vector(null, Map.empty[String, String],
        _args.updated("permission", "granted"), _args.updated("help", "true")) ++
        _args.keys.toVector.map(_args - _) ++ _args.keys.toVector.flatMap(key =>
          Vector[String](null, "", " ", " trimmed", "trailing ", "control\nvalue").map(value => _args.updated(key, value))) ++
        Vector("Recommendation", "Skill", "Workflow", "TerminalResult", "explicitHuman")
          .map(value => _replace("origin", value)) ++
        Vector("smworkflow.host.selection", "entity:smworkflow.host", "entity:smworkflow..selection",
          "entity:smworkflow.host.bad-name", "entity:1smworkflow.host.selection", "entity:smworkflow.host.selection.extra")
          .map(value => _replace("selection-collection", value)) ++ Vector(_replace("plan-collection", _selection_collection),
          _replace("plan-collection", "entity:smworkflow.host"))
      var reads = 0
      val reader: String => Consequence[String] = _ => { reads += 1; Consequence.stateConflict("No input should be read") }

      When("each invalid explicit argument set is passed to the converter")
      val results = invalidargs.map(Main.prepareC(_, reader))

      Then("each call returns an error and neither selected file has been read")
      results.foreach(_.toOption shouldBe None)
      reads shouldBe 0
    }

    "contain absent failing and throwing file callbacks as Consequence errors" in {
      Given("valid explicit arguments and absent, failed, null-result or throwing readers with no successful file input")
      val failed: String => Consequence[String] = _ => Consequence.stateConflict("Explicit file unavailable")
      val nullresult: String => Consequence[String] = _ => null
      val throwing: String => Consequence[String] = _ => throw new IllegalArgumentException("Invalid selected file")

      When("preparation receives each unavailable reader including a null reader on an empty field set")
      val results = Vector(Main.prepareC(_args, null), Main.prepareC(Map.empty[String, String], null),
        Main.prepareC(_args, failed), Main.prepareC(_args, nullresult), Main.prepareC(_args, throwing))

      Then("no callback failure escapes as an unchecked exception or successful importer JSON")
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

  "Explicit selected document preparation" should {
    "emit only the two normal selected rows without acquiring the declared documents" in {
      Given("the ordinary eight preparation fields and a source declaration in the selected plan file")
      val source = GoalPhaseDocumentSource.Source("docs/phase.md", "docs/checklist.md")
      val declaration = Json.obj("schemaVersion" -> Json.fromString(GoalPhaseDocumentSource.declarationSchema),
        "phasePath" -> Json.fromString(source.phasePath), "checklistPath" -> Json.fromString(source.checklistPath))
      var reads = Vector.empty[String]
      val files = _files(plan = declaration)
      When("normal passive preparation reads the explicit business input and source declaration")
      val entries = _entries(_take(Main.prepareC(_args, path => { reads :+= path; _read(files)(path) })))
      val row = _row(entries(1))
      val stored = row.hcursor.get[String](GoalPhaseSelectedPlanJsonV1.storageField).toOption.get
      Then("the existing typed plan field contains passive source selection and no document read or placeholder Plan")
      reads shouldBe Vector("input.json", "plan.json")
      entries.size shouldBe 2
      entries.map(_.hcursor.get[String]("collection").toOption.get) shouldBe Vector(_selection_collection, _plan_collection)
      row.asObject.get.keys.toSet shouldBe Set("id", GoalPhaseSelectedPlanJsonV1.storageField)
      row.hcursor.get[String]("id").toOption shouldBe Some("selected-goal")
      _take(GoalPhaseDocumentSource.decodeStorageC(stored)) shouldBe
        GoalPhaseDocumentSource.Selection("selected-goal", "explicit-participant", source)
      _selection(entries).selectedInput.get.value.as[V.GoalPhaseStartInput].toOption shouldBe Some(_input)
    }

    "refuse every invalid schema-bearing declaration without falling back to a structurally valid legacy Plan" in {
      Given("legacy Plan JSON augmented with a declaration schema, extra authority or malformed source fields")
      val source = Json.obj("schemaVersion" -> Json.fromString(GoalPhaseDocumentSource.declarationSchema),
        "phasePath" -> Json.fromString("docs/phase.md"), "checklistPath" -> Json.fromString("docs/checklist.md"))
      val variants = Vector(_plan.asJson.mapObject(_.add("schemaVersion", Json.fromString("unknown"))),
        _plan.asJson.mapObject(_.add("schemaVersion", Json.Null)),
        source.mapObject(_.add("plan", _plan.asJson)), source.mapObject(_.add("permission", Json.True)),
        source.mapObject(_.remove("checklistPath")), source.mapObject(_.add("phasePath", Json.fromString("../phase.md"))))
      When("the normal preparation route receives each selected plan file")
      val results = variants.map(plan => Main.prepareC(_args, _read(_files(plan = plan))))
      Then("no declaration is reinterpreted as a legacy Plan, while the schema-less legacy bytes remain unchanged")
      results.foreach(_.isSuccess shouldBe false)
      val entries = _entries(_take(Main.prepareC(_args, _read(_files()))))
      val legacy = _row(entries(1)).hcursor.get[String](GoalPhaseSelectedPlanJsonV1.storageField).toOption.get
      legacy shouldBe _take(GoalPhaseSelectedPlanJsonV1.encodeStorageC(
        GoalPhaseNativeRun.SelectedPlan("selected-goal", "explicit-participant", _plan)))
    }
  }
}
