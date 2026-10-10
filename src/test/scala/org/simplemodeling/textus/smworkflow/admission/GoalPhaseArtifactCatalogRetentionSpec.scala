package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.blob.{BlobRepository, BlobStorageRef}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowHandle
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.StepCloseRequest
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
final class GoalPhaseArtifactCatalogRetentionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail(s"Artifact retention fixture failed: $other")
  }
  private val handle = WorkflowHandle(SmWorkflowComponent.componentId, WorkflowDefinitionIdentity("GoalPhaseWorkflow"),
    WorkflowDefinitionRevision("revision-1"), InstanceIdentity("catalog-instance"))
  private val request = StepCloseRequest("submission", "step", "a" * 64, "b" * 64, Vector.empty)
  private val scope = take(GoalPhaseCallScopedSnapshot.scopeC(handle, request, GoalPhaseCallScopedSnapshot.Purpose.Candidate))
  private val id = take(EntityId.bridgeFromParts("cncf", "artifact", BlobRepository.CollectionId, Instant.EPOCH, "artifact"))
  private val ref = GoalPhaseBlobArtifactSnapshot.Reference(id, BlobStorageRef("local", "artifacts", "artifact.bin"))
  private val pair = GoalPhaseBlobArtifactSnapshot.References("selection", 0L, ref, ref)
  private def record(selected: GoalPhaseBlobArtifactSnapshot.References = pair) = Record.data(
    GoalPhaseArtifactReferenceRecord.storageField -> take(GoalPhaseArtifactReferenceRecord.encodeStorageC(scope, selected, 16)))
  private final class Storage extends GoalPhaseArtifactCatalogRetention.Port {
    var row: Option[Record] = None
    var creates = 0
    var afterCreate: String = "ordinary"
    var failInitialRead = false
    def loadC(): Consequence[Option[Record]] =
      if (creates == 0 && failInitialRead) Consequence.stateConflict("Observation failed before Create")
      else if (creates == 0) Consequence.success(row)
      else afterCreate match {
        case "missing" => Consequence.success(None)
        case "failed" => Consequence.stateConflict("Post-Create observation failed")
        case "invalid-scope" => Consequence.success(Some(Record.data(GoalPhaseArtifactReferenceRecord.storageField -> "invalid")))
        case "changed-view" => Consequence.success(Some(record(pair.copy(selectionRevision = 1L))))
        case "throw-read" => throw new IllegalStateException("Post-Create observation threw")
        case _ => Consequence.success(row)
      }
    def createC(value: Record): Consequence[Unit] = {
      creates += 1
      value.getString("id") shouldBe None
      row = Some(value)
      afterCreate match {
        case "failed-create" => Consequence.stateConflict("Lost Create acknowledgement")
        case "throw-create" => throw new IllegalStateException("Lost Create acknowledgement")
        case _ => Consequence.unit
      }
    }
  }
  private def retain(storage: Storage) = GoalPhaseArtifactCatalogRetention.retainC(scope, pair, 16, storage)
  private def indeterminate(value: Consequence[?]): Unit = value match {
    case Consequence.Failure(conclusion) => conclusion.observation.cause.descriptor.facets.collect {
      case org.goldenport.observation.Descriptor.Facet.Reason(name) => name
    } should contain("transaction-indeterminate")
    case other => fail(s"Uncertain effect returned success: $other")
  }

  "Initial artifact catalog retention" should {
    "decode only a bounded typed initial-registration command without caller-owned authority or storage targets" in {
      Given("the exact initial artifact pair and Step subject, with a sixteen-byte bound")
      val command = GoalPhaseArtifactRegistrationServiceOperation.Command(request, pair)
      When("the initial registration command is encoded and decoded")
      val wire = GoalPhaseArtifactRegistrationServiceOperation.encodeCommand(command)
      val decoded = take(GoalPhaseArtifactRegistrationServiceOperation.decodeCommandC(wire, 16))
      Then("the complete typed command is preserved")
      decoded shouldBe command
      Given("the same wire with caller-owned authority, bounds or storage fields added")
      val json = io.circe.parser.parse(wire).toOption.get
      Vector("handle", "purpose", "receipt", "collection", "maximumBytes", "approval").foreach { field =>
        val changed = json.mapObject(_.add(field, io.circe.Json.obj())).noSpaces
        When("the added field is decoded")
        val result = GoalPhaseArtifactRegistrationServiceOperation.decodeCommandC(changed, 16)
        Then("the caller-owned field is refused")
        result shouldBe a[Consequence.Failure[?]]
      }
      When("the configured bound is zero")
      val zero = GoalPhaseArtifactRegistrationServiceOperation.decodeCommandC(wire, 0)
      Then("the invalid bound is refused")
      zero shouldBe a[Consequence.Failure[?]]
      When("the configured execution read limit is one byte")
      val short = GoalPhaseArtifactRegistrationServiceOperation.decodeCommandC(wire, 1)
      Then("the configured limit is accepted; actual bounded payload reading belongs to execution")
      take(short) shouldBe command
      Given("a selection revision encoded as a string rather than the declared number")
      val malformed = json.mapObject(_.add("selection", json.hcursor.downField("selection").focus.get
        .mapObject(_.add("revision", io.circe.Json.fromString("0"))))).noSpaces
      When("the malformed revision is decoded")
      val revision = GoalPhaseArtifactRegistrationServiceOperation.decodeCommandC(malformed, 16)
      Then("the unsupported revision representation is refused")
      revision shouldBe a[Consequence.Failure[?]]
      Vector(null, "{}", "{").foreach { value =>
        When("an absent or malformed command payload is decoded")
        val result = GoalPhaseArtifactRegistrationServiceOperation.decodeCommandC(value, 16)
        Then("no registration command is produced")
        result shouldBe a[Consequence.Failure[?]]
      }
    }

    "use the generated CML operation and typed command/result declarations" in {
      Given("the two declared public initial-registration parameter names")
      val expected = Set("subject", "selection")
      When("the generated CML registration operation specification is read")
      val generated = SmWorkflowComponent.GoalPhaseServiceService.RegisterGoalPhaseArtifactsOperation.specification
      Then("the generated operation has the canonical name and exact parameters")
      generated.name shouldBe GoalPhaseArtifactRegistrationServiceOperation.operationName
      generated.request.parameters.map(_.name).toSet shouldBe expected
      When("the generated typed command is constructed with the initial artifact references")
      val command = org.simplemodeling.textus.smworkflow.value.GoalPhaseArtifactRegistrationCommand(
        request, org.simplemodeling.textus.smworkflow.value.GoalPhaseArtifactSelection("selection", 0L,
          org.simplemodeling.textus.smworkflow.value.GoalPhaseArtifactReference(id.print,
            org.simplemodeling.textus.smworkflow.value.GoalPhaseArtifactStorageReference("local", "artifacts",
              "artifact.bin", None, None)),
          org.simplemodeling.textus.smworkflow.value.GoalPhaseArtifactReference(id.print,
            org.simplemodeling.textus.smworkflow.value.GoalPhaseArtifactStorageReference("local", "artifacts",
              "artifact.bin", None, None))))
      Then("the subject and initial selection revision remain typed data")
      command.subject shouldBe request
      command.selection.revision shouldBe 0L
    }

    "Create once and return only the committed pair, with equal retry observing the retained record" in {
      Given("an empty artifact catalog and the exact initial pair")
      val storage = new Storage
      When("the initial pair is retained")
      val initial = take(retain(storage))
      Then("the committed pair is returned")
      initial shouldBe pair
      When("the identical pair is retained again")
      val repeated = take(retain(storage))
      Then("the existing pair is observed without another Create or caller-owned row ID")
      repeated shouldBe pair
      storage.creates shouldBe 1
      storage.row.get.fields.map(_.key).toSet shouldBe Set(GoalPhaseArtifactReferenceRecord.storageField)
    }
    "consume actual stored selections without requiring equality to a retry input or readback view" in {
      Given("Spec: docs/spec/goal-phase-artifact-reference-applicability.md; Rule: R3; Example: E3; a retained scoped selection")
      val actual = pair.copy(selectionRevision = 3L, program = ref.copy(storageRef = ref.storageRef.copy(key = "current.bin")))
      val storage = new Storage
      storage.row = Some(record(actual))
      var observed = Vector.empty[GoalPhaseBlobArtifactSnapshot.References]
      When("a retry supplies the earlier pair but the current scoped row is selected")
      val selected = take(GoalPhaseArtifactCatalogRetention.retainC(scope, pair, 16, storage,
        value => { observed :+= value; Consequence.unit }))
      Then("only the actual retained input is read and no Create or replacement occurs")
      selected shouldBe actual
      observed shouldBe Vector(actual)
      storage.creates shouldBe 0
      val changing = new Storage
      changing.afterCreate = "changed-view"
      When("a successful Create is followed by an applicable readback view with another selection revision")
      take(retain(changing)).selectionRevision shouldBe 1L
      Then("readback is observed, not compared as a no-change certificate")
      changing.creates shouldBe 1
    }

    "stop actual selected-resource failure before Create" in {
      Given("Spec: docs/spec/goal-phase-artifact-reference-applicability.md; Rule: R3; Example: E4; an empty catalog and an unavailable selected resource")
      val storage = new Storage
      When("the actual scoped payload read refuses access or availability")
      val result = GoalPhaseArtifactCatalogRetention.retainC(scope, pair, 16, storage,
        _ => Consequence.stateConflict("Actual selected resource unavailable"))
      Then("no reference row is published and failure is not treated as absence or permission")
      result shouldBe a[Consequence.Failure[?]]
      storage.creates shouldBe 0
      storage.row shouldBe None
    }

    "refuse malformed stored selections without replacement" in {
      Vector(Record.data("unknown" -> "value"),
        Record.data(GoalPhaseArtifactReferenceRecord.storageField -> "invalid")).foreach { existing =>
        Given("a catalog row with another selection or malformed payload")
        val storage = new Storage
        storage.row = Some(existing)
        When("initial retention observes that row")
        val result = retain(storage)
        Then("the row is refused without Create or replacement")
        result shouldBe a[Consequence.Failure[?]]
        storage.creates shouldBe 0
        storage.row shouldBe Some(existing)
      }
    }
    "propagate an initial observation failure without treating it as absence" in {
      Given("an empty catalog whose initial observation fails")
      val storage = new Storage
      storage.failInitialRead = true
      When("initial artifact retention attempts to observe the row")
      val result = retain(storage)
      Then("the failure is propagated without Create")
      result shouldBe a[Consequence.Failure[?]]
      storage.creates shouldBe 0
    }
    "retain uncertainty after Create or readback failure and recover the same physical row on retry" in {
      Vector("missing", "failed", "invalid-scope", "throw-read", "failed-create", "throw-create").foreach { mode =>
        Given("an empty catalog with uncertain Create acknowledgement or post-Create readback")
        val storage = new Storage
        storage.afterCreate = mode
        When("the initial pair is retained through the selected fault")
        val result = retain(storage)
        Then("uncertainty is retained after one physical Create")
        indeterminate(result)
        storage.creates shouldBe 1
        storage.row.nonEmpty shouldBe true
        storage.afterCreate = "ordinary"
        When("readback becomes ordinary and the same pair is retried")
        val recovered = take(retain(storage))
        Then("the original pair is recovered without another Create")
        recovered shouldBe pair
        storage.creates shouldBe 1
      }
    }
    "reject invalid preparation before any storage observation or Create" in {
      Given("an empty catalog port before any registration preparation")
      val storage = new Storage
      When("the owning artifact scope is absent")
      val missingscope = GoalPhaseArtifactCatalogRetention.retainC(null, pair, 16, storage)
      Then("the absent scope is refused")
      missingscope shouldBe a[Consequence.Failure[?]]
      When("the artifact pair is absent")
      val missingpair = GoalPhaseArtifactCatalogRetention.retainC(scope, null, 16, storage)
      Then("the absent pair is refused")
      missingpair shouldBe a[Consequence.Failure[?]]
      When("the configured bound is invalid")
      val bound = GoalPhaseArtifactCatalogRetention.retainC(scope, pair, 0, storage)
      Then("the invalid bound is refused")
      bound shouldBe a[Consequence.Failure[?]]
      When("the retention port is absent")
      val missingport = GoalPhaseArtifactCatalogRetention.retainC(scope, pair, 16, null)
      Then("no invalid preparation observes or creates a row")
      missingport shouldBe a[Consequence.Failure[?]]
      storage.creates shouldBe 0
      storage.row shouldBe None
    }
  }
}
