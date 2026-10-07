package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant
import java.nio.file.Files
import java.security.MessageDigest
import org.goldenport.Consequence
import org.goldenport.bag.Bag
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.blob.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowHandle
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.StepCloseRequest
import org.goldenport.datatype.ContentType
import org.simplemodeling.model.datatype.EntityId
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
final class GoalPhaseBlobArtifactSnapshotSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import GoalPhaseBlobArtifactSnapshot.*
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail(s"Blob snapshot fixture failed: $other")
  }
  private val id = take(EntityId.bridgeFromParts("cncf", "program", BlobRepository.CollectionId, Instant.EPOCH, "program"))
  private val bytes = Vector[Byte](0, -1, 2, 10)
  private val digest = MessageDigest.getInstance("SHA-256").digest(bytes.toArray).map(byte => f"${byte & 0xff}%02x").mkString
  private val reference = Reference(id, BlobStorageRef("local", "artifacts", "program.bin"), bytes.size, digest)
  private val read = BlobReadResult(id, reference.storageRef, ContentType.APPLICATION_OCTET_STREAM, bytes.size,
    digest, Bag.binary(bytes.toArray), BlobAccessUrl.unresolved, Instant.EPOCH)

  "Managed Blob snapshot bytes" should {
    "require a separate exact artifact declaration without aliasing other host purposes" in {
      import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
      import org.goldenport.cncf.datastore.DataStore
      import org.simplemodeling.model.datatype.EntityCollectionId
      Given("an artifact collection declared in the resolved host snapshot")
      val collection = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "artifacts", "references"))
      val prefix = GoalPhaseArtifactReadPolicy.prefix
      val values: Map[String, ConfigurationValue] = Map(prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
        prefix + "reference-collection" -> ConfigurationValue.StringValue(collection.print))
      def configuration(data: Map[String, ConfigurationValue]) = ResolvedConfiguration(HostConfiguration(data), ConfigurationTrace.empty)
      When("the reader requests that exact destination without opening a provider")
      take(GoalPhaseArtifactReadPolicy.admitC(configuration(values), "workflow", collection))
      val nested = Map("sm-workflow" -> ConfigurationValue.ObjectValue(Map("goal-phase" ->
        ConfigurationValue.ObjectValue(Map("artifacts" -> ConfigurationValue.ObjectValue(values.map { (key, value) =>
          key.stripPrefix(prefix) -> value }))))))
      take(GoalPhaseArtifactReadPolicy.admitC(configuration(nested), "workflow", collection))
      Then("flat and nested typed snapshots express the same restriction, not a storage grant")
      val malformed = Vector(Map.empty[String, ConfigurationValue], values.removed(prefix + "store-name"),
        values.updated(prefix + "store-name", ConfigurationValue.BooleanValue(true)),
        values.updated(prefix + "reference-collection", ConfigurationValue.StringValue(collection.print + " ")))
      malformed.foreach(data => GoalPhaseArtifactReadPolicy.admitC(configuration(data), "workflow", collection) shouldBe
        a[Consequence.Failure[?]])
      GoalPhaseArtifactReadPolicy.admitC(configuration(values), "other", collection) shouldBe a[Consequence.Failure[?]]
      GoalPhaseArtifactReadPolicy.admitC(configuration(values), "workflow", null) shouldBe a[Consequence.Failure[?]]
      GoalPhaseArtifactReadPolicy.admitC(null, "workflow", collection) shouldBe a[Consequence.Failure[?]]

      When("a complete existing host declaration assigns that collection to authorization")
      val hostPrefix = GoalPhaseHostRecordReadPolicy.prefix
      val hostValues: Map[String, ConfigurationValue] = Map(hostPrefix + "store-name" -> ConfigurationValue.StringValue("workflow")) ++
        GoalPhaseHostRecordReadPolicy.Purpose.values.map(role => hostPrefix + role.key -> ConfigurationValue.StringValue(
          (if (role == GoalPhaseHostRecordReadPolicy.Purpose.Authorization) collection else
            DataStore.CollectionId.EntityStore(EntityCollectionId("test", "artifacts", role.toString.toLowerCase))).print))
      Then("an artifact declaration cannot repurpose it, and a partial host declaration is not ignored")
      GoalPhaseArtifactReadPolicy.admitC(configuration(values ++ hostValues), "workflow", collection) shouldBe
        a[Consequence.Failure[?]]
      GoalPhaseArtifactReadPolicy.admitC(configuration(values + (hostPrefix + "store-name" -> ConfigurationValue.StringValue("workflow"))),
        "workflow", collection) shouldBe a[Consequence.Failure[?]]
    }

    "retain a closed catalog pair for only its exact source scope without digest-addressing" in {
      Given("a host-selected versioned pair and a typed Workflow artifact scope")
      val handle = WorkflowHandle(SmWorkflowComponent.componentId, WorkflowDefinitionIdentity("GoalPhaseWorkflow"),
        WorkflowDefinitionRevision("revision-1"), InstanceIdentity("catalog-workflow"))
      val request = StepCloseRequest("catalog-submission", "step", "a" * 64, "b" * 64, Vector.empty)
      def scope(h: WorkflowHandle = handle, r: StepCloseRequest = request,
        purpose: GoalPhaseCallScopedSnapshot.Purpose = GoalPhaseCallScopedSnapshot.Purpose.Candidate) =
        take(GoalPhaseCallScopedSnapshot.scopeC(h, r, purpose))
      val selected = References("catalog-selection", 3L,
        reference.copy(storageRef = reference.storageRef.copy(version = Some("v3"), etag = Some("opaque-etag"))), reference)
      val original = scope()
      val wire = take(GoalPhaseArtifactReferenceRecord.encodeStorageC(original, selected, 16))
      When("the pure catalog codec reloads that selected pair")
      take(GoalPhaseArtifactReferenceRecord.decodeStorageC(original, wire, 16)) shouldBe selected
      Then("both full references, optional provider facts and explicit selection revision survive without effects")
      selected.selectionRevision shouldBe 3L
      val changedCandidate = scope(r = request.copy(programDigest = "c" * 64))
      take(GoalPhaseArtifactReferenceRecord.keyC(changedCandidate)) shouldBe take(GoalPhaseArtifactReferenceRecord.keyC(original))

      When("another call has a different Workflow, submission, Step, purpose or source candidate")
      Vector(scope(h = handle.copy(instanceIdentity = InstanceIdentity("other-workflow"))),
        scope(h = handle.copy(workflowRevision = WorkflowDefinitionRevision("revision-2"))),
        scope(r = request.copy(submissionIdentity = "other-submission")),
        scope(r = request.copy(stepIdentity = "other-step")),
        scope(purpose = GoalPhaseCallScopedSnapshot.Purpose.RepairSuccessor), changedCandidate).foreach { other =>
        GoalPhaseArtifactReferenceRecord.decodeStorageC(other, wire, 16) shouldBe a[Consequence.Failure[?]]
      }
      Then("a stored claim cannot retarget scope and content changes do not allocate another catalog address")
      GoalPhaseArtifactReferenceRecord.keyC(null) shouldBe a[Consequence.Failure[?]]
      GoalPhaseArtifactReferenceRecord.encodeStorageC(original, null, 16) shouldBe a[Consequence.Failure[?]]
    }

    "reject malformed catalog payloads rather than defaulting references, scope or versions" in {
      Given("a closed catalog payload with exact fields at every nested level")
      val handle = WorkflowHandle(SmWorkflowComponent.componentId, WorkflowDefinitionIdentity("GoalPhaseWorkflow"),
        WorkflowDefinitionRevision("revision-1"), InstanceIdentity("catalog-workflow"))
      val request = StepCloseRequest("catalog-submission", "step", "a" * 64, "b" * 64, Vector.empty)
      val scope = take(GoalPhaseCallScopedSnapshot.scopeC(handle, request, GoalPhaseCallScopedSnapshot.Purpose.Candidate))
      val selected = References("selection", 0L, reference, reference)
      val wire = take(GoalPhaseArtifactReferenceRecord.encodeStorageC(scope, selected, 16))
      val colon = wire.indexOf(':')
      val prefix = wire.substring(0, colon + 1)
      val json = io.circe.parser.parse(wire.substring(colon + 1)).toOption.get
      val selection = json.hcursor.downField("selection").focus.get
      val program = selection.hcursor.downField("program").focus.get
      val storage = program.hcursor.downField("storageRef").focus.get
      def replaceSelection(value: io.circe.Json) = json.mapObject(_.add("selection", value))
      def replaceProgram(value: io.circe.Json) = replaceSelection(selection.mapObject(_.add("program", value)))
      def replaceStorage(value: io.circe.Json) = replaceProgram(program.mapObject(_.add("storageRef", value)))
      When("schemas, required fields, correlation, numeric facts or provider references are incompatible")
      val malformed = Vector(json.mapObject(_.remove("scope")), json.mapObject(_.add("approval", io.circe.Json.True)),
        json.mapObject(_.add("schemaVersion", io.circe.Json.fromString("legacy"))),
        json.mapObject(_.add("catalogKey", io.circe.Json.fromString("foreign"))),
        replaceSelection(selection.mapObject(_.add("revision", io.circe.Json.fromString("0")))),
        replaceSelection(selection.mapObject(_.add("revision", io.circe.Json.fromDoubleOrNull(1.5)))),
        replaceProgram(program.mapObject(_.add("byteSize", io.circe.Json.fromLong(17L)))),
        replaceProgram(program.mapObject(_.add("id", io.circe.Json.fromString("not-an-entity")))),
        replaceStorage(storage.mapObject(_.remove("etag"))),
        replaceStorage(storage.mapObject(_.add("version", io.circe.Json.fromString("")))),
        replaceStorage(storage.mapObject(_.add("key", io.circe.Json.fromString("../outside")))))
      malformed.foreach(value => GoalPhaseArtifactReferenceRecord.decodeStorageC(scope, prefix + value.noSpaces, 16) shouldBe
        a[Consequence.Failure[?]])
      Vector(null, "", wire.substring(colon + 1), prefix + "{").foreach(value =>
        GoalPhaseArtifactReferenceRecord.decodeStorageC(scope, value, 16) shouldBe a[Consequence.Failure[?]])
      Then("no old shape, unknown field, lossy number or incomplete member yields a usable pair")
      take(GoalPhaseArtifactReferenceRecord.decodeStorageC(scope, prefix + json.spaces2, 16)) shouldBe selected
      GoalPhaseArtifactReferenceRecord.encodeStorageC(scope, selected.copy(program =
        reference.copy(storageRef = reference.storageRef.copy(version = Some("")))), 16) shouldBe a[Consequence.Failure[?]]
    }

    "require explicit selection identity and revision without deriving them from content" in {
      Given("an allocated host selection with a complete bounded Blob pair")
      val selected = References("selection-one", 0L, reference, reference)
      When("the selected pair is validated independently of payload access")
      take(validateReferencesC(selected, 16))
      take(validateReferencesC(selected.copy(selectionRevision = Long.MaxValue), 16))
      Then("revision is an explicit observation, not a digest, counter increment or authority grant")
      selected.selectionIdentity shouldBe "selection-one"
      selected.selectionRevision shouldBe 0L

      When("a selection field or either member is missing, malformed or unbounded")
      Vector(null, selected.copy(selectionIdentity = null), selected.copy(selectionIdentity = ""),
        selected.copy(selectionIdentity = " selection-one"), selected.copy(selectionRevision = -1L),
        selected.copy(program = null), selected.copy(planning = reference.copy(byteSize = 17L))).foreach { other =>
        validateReferencesC(other, 16) shouldBe a[Consequence.Failure[?]]
      }
      Vector(0, -1, Int.MaxValue).foreach(limit =>
        validateReferencesC(selected, limit) shouldBe a[Consequence.Failure[?]])
      Then("no incomplete selection is normalized, defaulted or treated as a complete pair")
      take(validateReferencesC(selected, 16))
    }

    "keep same-labelled Steps isolated by Workflow, submission, candidate and read purpose" in {
      Given("two Workflow instances whose Step labels are deliberately identical")
      val first = WorkflowHandle(SmWorkflowComponent.componentId, WorkflowDefinitionIdentity("GoalPhaseWorkflow"),
        WorkflowDefinitionRevision("revision-1"), InstanceIdentity("workflow-one"))
      val second = first.copy(instanceIdentity = InstanceIdentity("workflow-two"))
      val request = StepCloseRequest("submission-one", "same-step", "a" * 64, "b" * 64, Vector.empty)
      var observed = Vector.empty[GoalPhaseCallScopedSnapshot.Scope]
      val reader = new GoalPhaseCallScopedSnapshot.ScopePort {
        def loadForScopeC(scope: GoalPhaseCallScopedSnapshot.Scope) = {
          observed = observed :+ scope
          Consequence.success(GoalPhaseStepClosureBoundary.ArtifactSnapshot(bytes, bytes))
        }
      }
      When("each admitted scope is separately bound before its artifact read")
      val one = take(GoalPhaseCallScopedSnapshot.forScopeC(reader, first, request))
      val two = take(GoalPhaseCallScopedSnapshot.forScopeC(reader, second,
        request.copy(submissionIdentity = "submission-two", programDigest = "c" * 64)))
      take(one.loadC(request.stepIdentity))
      take(two.loadC(request.stepIdentity))
      Then("no global Step key or ambient latest selection replaces their explicit scope")
      observed.map(_.handle) shouldBe Vector(first, second)
      observed.map(_.request.submissionIdentity) shouldBe Vector("submission-one", "submission-two")
      observed.head.candidateRevision should not be observed.last.candidateRevision
      observed.foreach(_.purpose shouldBe GoalPhaseCallScopedSnapshot.Purpose.Candidate)
      one.loadC("another-step") shouldBe a[Consequence.Failure[?]]
      reader.loadC(request.stepIdentity) shouldBe a[Consequence.Failure[?]]
      observed.size shouldBe 2

      When("a repaired successor is read using its retained original candidate lineage")
      take(take(GoalPhaseCallScopedSnapshot.forScopeC(reader, first, request,
        GoalPhaseCallScopedSnapshot.Purpose.RepairSuccessor)).loadC(request.stepIdentity))
      Then("the old candidate anchors the request without claiming repaired bytes have its old digests")
      observed.last.request shouldBe request
      observed.last.candidateRevision shouldBe observed.head.candidateRevision
      observed.last.purpose shouldBe GoalPhaseCallScopedSnapshot.Purpose.RepairSuccessor
    }

    "refuse incomplete scoped reads before invoking a host reader" in {
      Given("an internal scoped reader with no permissive unscoped route")
      var reads = 0
      val reader = new GoalPhaseCallScopedSnapshot.ScopePort {
        def loadForScopeC(scope: GoalPhaseCallScopedSnapshot.Scope) = {
          reads += 1
          Consequence.success(GoalPhaseStepClosureBoundary.ArtifactSnapshot(bytes, bytes))
        }
      }
      val handle = WorkflowHandle(SmWorkflowComponent.componentId, WorkflowDefinitionIdentity("GoalPhaseWorkflow"),
        WorkflowDefinitionRevision("revision-1"), InstanceIdentity("workflow-one"))
      val request = StepCloseRequest("submission", "step", "a" * 64, "b" * 64, Vector.empty)
      When("a null, foreign, malformed or missing-call scope is supplied")
      GoalPhaseCallScopedSnapshot.forScopeC(reader, null, request) shouldBe a[Consequence.Failure[?]]
      GoalPhaseCallScopedSnapshot.forScopeC(reader, handle.copy(componentIdentity =
        org.goldenport.cncf.component.ComponentId("org.foreign.Owner")), request) shouldBe a[Consequence.Failure[?]]
      GoalPhaseCallScopedSnapshot.forScopeC(reader, handle, null) shouldBe a[Consequence.Failure[?]]
      GoalPhaseCallScopedSnapshot.forScopeC(reader, handle, request.copy(programDigest = "invalid")) shouldBe
        a[Consequence.Failure[?]]
      GoalPhaseCallScopedSnapshot.forScopeC(reader, handle, request, null) shouldBe a[Consequence.Failure[?]]
      val currentOnly = new GoalPhaseCallScopedSnapshot.ScopedPort {
        def loadForScopeCallC(scope: GoalPhaseCallScopedSnapshot.Scope, core: ActionCall.Core) = {
          reads += 1
          Consequence.success(GoalPhaseStepClosureBoundary.ArtifactSnapshot(bytes, bytes))
        }
      }
      GoalPhaseCallScopedSnapshot.forScopeC(currentOnly, handle, request) shouldBe a[Consequence.Failure[?]]
      val noCall = GoalPhaseCallScopedSnapshot.forCall(currentOnly, null)
      take(GoalPhaseCallScopedSnapshot.forScopeC(noCall, handle, request)).loadC(request.stepIdentity) shouldBe
        a[Consequence.Failure[?]]
      Then("neither scope construction nor a fabricated current-call wrapper issues artifact access")
      reads shouldBe 0
    }

    "require actual bounded bytes as well as exact host-pinned metadata" in {
      Given("an exact managed reference and binary Blob result")
      When("the result is checked against the host reference")
      val accepted = take(verifyReadC(reference, read, 16))
      Then("the exact bytes are returned without text conversion")
      accepted shouldBe bytes

      When("metadata is substituted, the payload is truncated or grows, or bytes change at equal length")
      Vector(read.copy(byteSize = 3), read.copy(digest = "b" * 64), read.copy(id = null),
        read.copy(storageRef = reference.storageRef.copy(key = "other.bin")), read.copy(payload = null),
        read.copy(payload = Bag.binary(bytes.dropRight(1).toArray)),
        read.copy(payload = Bag.binary((bytes :+ 4.toByte).toArray)),
        read.copy(payload = Bag.binary(Vector[Byte](9, 9, 9, 9).toArray))).foreach { other =>
        verifyReadC(reference, other, 16) shouldBe a[Consequence.Failure[?]]
      }
      Then("no metadata digest or size assertion can replace actual byte verification")
      verifyReadC(reference, null, 16) shouldBe a[Consequence.Failure[?]]
      verifyReadC(reference, read, 3) shouldBe a[Consequence.Failure[?]]
      take(verifyReadC(reference, read, 16)) shouldBe bytes
    }

    "reject invalid references and unavailable payloads without defaults or current-call inference" in {
      Given("a host reference resolver with no transport-facing path input")
      var resolutions = 0
      val resolver = new ReferencePort {
        def verifyForCallC(scope: GoalPhaseCallScopedSnapshot.Scope, core: ActionCall.Core) =
          Consequence.stateConflict("No admitted storage lease in pure fixture")
        def verifySelectionForCallC(scope: GoalPhaseCallScopedSnapshot.Scope, selected: References,
          core: ActionCall.Core) = Consequence.stateConflict("No admitted selection in pure fixture")
        def resolveForCallC(scope: GoalPhaseCallScopedSnapshot.Scope, core: ActionCall.Core) = {
          resolutions += 1
          Consequence.success(References("pure-selection", 0L, reference, reference))
        }
      }
      When("configuration or pinned reference facts are malformed or exceed bounds")
      Vector(null, "", " workflow").foreach(name => bindC(name, resolver, 16) shouldBe a[Consequence.Failure[?]])
      Vector(0, -1, Int.MaxValue).foreach(limit => bindC("workflow", resolver, limit) shouldBe a[Consequence.Failure[?]])
      bindC("workflow", null, 16) shouldBe a[Consequence.Failure[?]]
      Vector(null, reference.copy(byteSize = -1), reference.copy(byteSize = 17), reference.copy(digest = "bad"),
        reference.copy(id = null), reference.copy(storageRef = null),
        reference.copy(storageRef = reference.storageRef.copy(key = "../outside"))).foreach { other =>
        verifyReadC(other, read, 16) shouldBe a[Consequence.Failure[?]]
      }
      val missing = Files.createTempDirectory("goal-blob-missing-").resolve("missing.bin")
      verifyReadC(reference, read.copy(payload = Bag.file(missing).promoteToBinary()), 16) shouldBe a[Consequence.Failure[?]]
      Then("no invalid fact or missing payload yields replacement bytes")
      resolutions shouldBe 0

      When("the bound reader is called without its current owning Operation")
      val port = take(bindC("workflow", resolver, 16))
      port.loadC("step") shouldBe a[Consequence.Failure[?]]
      port.loadForCallC("step", null) shouldBe a[Consequence.Failure[?]]
      Then("reference selection and Blob access do not run at all")
      resolutions shouldBe 0
    }
  }
}
