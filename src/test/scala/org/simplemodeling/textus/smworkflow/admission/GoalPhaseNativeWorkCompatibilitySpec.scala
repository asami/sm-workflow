package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import io.circe.Json
import io.circe.syntax.*
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import GoalPhaseNativeWork as W
import GoalPhaseOperatingProfiles as P
import GoalPhaseOrderedProgression as O
import IncrementalSbtDiagnosisRepair as D
import SplitPhaseExplicitPartitionFixture.take

/** Synthetic retained work exercises read compatibility without a consumer
  * store, execution grant, new issue or native Human evidence.
  * @since   Nov.  1, 2026
  * @version Nov.  8, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseNativeWorkCompatibilitySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _prefix = "sm-workflow.goal-phase-native-work-request."
  private val _profile = P.Capture(P.Selection("standard", "1", "default"),
    Some("CodingEngineeringDeep"), None, None, None, None)
  private val _review_profile = _profile.copy(reasoningClass = Some("ReviewCritical"))
  private val _concrete = P.Concrete("fixture-profile", "fixture-provider", "fixture-model", Map("effort" -> "fixture"))
  private val _fallback = _concrete.copy(identity = "fixture-fallback", model = "fixture-fallback-model")
  private val _mapped_profile = _profile.copy(selection = P.Selection("quality", "1", "config"),
    mappingIdentity = Some("fixture-mapping"), mappingRevision = Some("1"),
    configured = Some(_concrete), selected = Some(_concrete))
  private val _logical_profile = _profile.copy(reasoningClass = None,
    mappingIdentity = Some("caller-standard"), mappingRevision = Some(GoalPhaseDispatchPolicy.revision),
    dispatch = Some(GoalPhaseDispatchPolicy.Capture(GoalPhaseDispatchPolicy.revision, "ordinary",
      GoalPhaseDispatchPolicy.chain("ordinary"), "DELEGATED", None, GoalPhaseDispatchPolicy.defaultEdges)))
  private val _review = GoalPhasePurposeRepairFixture.request.copy(
    repairProgress = Some(GoalPhaseRepairProgress.snapshot(GoalPhasePurposeRepairFixture.controller)))
  private val _implementation = _review.copy(kind = "ImplementStep", review = None, issues = None,
    candidate = None, checksReceipt = None, purposeRepair = None)
  private val _manifest = D.Manifest(D.schema, "earlier-correction", "12", _review.candidate.get,
    "The earlier selected declaration failed its retained check",
    Vector(D.Locator("focused", "stdout", 1, 1, "Retained failure location")),
    Vector(ContextReference("earlier-information", "1")), Vector("current.scala"),
    Vector("Preserve the selected behavior while correcting the declaration"), Vector.empty,
    Vector("focused"), Vector("The current result still needs its own native checks"))
  private val _prior = D.Prior(_implementation.stepIdentity, _implementation.generation,
    ContextReference("earlier-accepted-repair", "1"), _manifest)
  private val _decision = W.DecisionContext(W.Result("decision-needed", "Retained semantic question",
    Vector("An unresolved selected behavior")), "ContinueRepairs", "Continue within the retained boundary",
    Some(ContextReference("retained-human-direction", "2")))
  private val _repair = _implementation.copy(kind = "RepairStep", generation = _implementation.generation + 1,
    repairDirective = Some(O.RepairDirective("Correct retained behavior", Vector("An actual selected defect"))),
    decisionContext = Some(_decision), diagnosticHistory = Some(Vector(_prior)))

  private def _payload(wire: Json)(change: Json => Json): Json =
    wire.mapObject(fields => fields.add("payload", change(fields("payload").get)))
  private def _version(wire: Json, version: String): Json = wire.mapObject(_.add("schemaVersion", (_prefix + version).asJson))
  private def _profile_wire(wire: Json)(change: Json => Json): Json = _payload(wire)(value =>
    value.mapObject(fields => fields.add("operatingProfile", change(fields("operatingProfile").get))))
  private def _selection_wire(wire: Json)(change: Json => Json): Json = _profile_wire(wire)(value =>
    value.mapObject(fields => fields.add("selection", change(fields("selection").get))))

  // A small common transport fixture only; no runtime continuation is issued.
  private def _frame(value: W.Request): WorkflowInteraction[W.Request, Nothing] = {
    val handle = WorkflowHandle(SmWorkflowComponent.componentId, WorkflowDefinitionIdentity("GoalPhaseWorkflow"),
      WorkflowDefinitionRevision("1"), InstanceIdentity(value.instanceIdentity))
    val context = ContextBundle("retained-work-context", Vector(ContextReference("selected-plan", "1")),
      Vector(ContextReference("retained-base", "1")), ContextSnapshot(handle.workflowRevision.value, Some("model-1"), Some("workspace-1"), Some("evidence-1")))
    val request = ContinuationRequest(StateMachineRunIdentity("retained-run"), ContinuationIdentity("retained-continuation"),
      StateMachineRevision(value.sourceRevision), StateMachineRequiredOperationIdentity("goal-phase-repair-capability"),
      StateMachineOperationIdentity("FixtureGoalService", "repairStep"), Some(TypedValue(W.inputCodec.typeIdentity, value)),
      Some(StateMachineResultTypeReference(W.resultCodec.typeIdentity)), context,
      CompletionContract("retained-completion", Vector(ContextReference("selected-behavior", "1"))),
      EvidenceContract("retained-evidence", Vector(ContextReference("independent-result", "1"))),
      StateMachineOperationIdentity("FixtureGoalService", "completeRepair"))
    WorkflowInteraction(handle, WorkflowContinuation.WorkOrder(request,
      ExecutionRequirement(Vector(CapabilityRequirement("semantic-repair")), RiskLevel("bounded"), ReasoningLevel.Deep, true),
      MinimalPresentation("Retained repair", "Return the selected semantic result")))
  }

  "Retained GoalPhase operating profile compatibility" should {
    "preserve historical estimate presence inside a retained check failure WorkOrder" in {
      Given("a specification-owned v5 RepairStep with a failed typed SBT receipt")
      val check = GoalPhaseNativeValidation.Check("focused", 1, WorkflowSbtValidation.Kind.Test)
      val receipt = RepositorySyncNativeMergeValidation.Receipt("focused", "exited", Some(1),
        "Specification-owned failed test", "", Some(WorkflowSbtValidation.Facts(
          WorkflowSbtValidation.Kind.Test, WorkflowSbtValidation.Outcome.Failed, None, None)))
      val validation = GoalPhaseNativeValidation.Payload("12", _repair.stepIdentity.get,
        O.Snapshot("a" * 40, "b" * 40), Vector(check), Vector(receipt), Some("repair-required"))
      val failure = take(W.checkFailureContextC(Vector("current.scala"), "Specification-owned source diff", validation))
      val request = _repair.copy(operatingProfile = Some(_profile), checkFailure = Some(failure))
      def changeSbt(wire: Json)(change: Json => Json): Json = _payload(wire)(payload =>
        payload.mapObject(p => p.add("checkFailure", p("checkFailure").get.mapObject(f =>
          f.add("validation", f("validation").get.mapObject(v =>
            v.add("receipts", v("receipts").get.mapArray(rs => rs.updated(0,
              rs(0).mapObject(r => r.add("sbt", change(r("sbt").get))))))))))))
      val absent = W.inputCodec.encode(request)
      val explicit = changeSbt(absent)(_.mapObject(_.add("estimate", Json.Null)))
      val publication = W.PublicationFailureContext(ContextReference(_repair.instanceIdentity, "1"),
        Vector("current.scala"), "Retained primary publication failure", PrimaryPublicationContinuationSpecificationFixture.failed,
        testsExecuted = false)
      val invalidV6 = W.inputCodec.encode(request.copy(candidate = None, checksReceipt = None,
        publicationFailure = Some(publication)))
      W.inputCodec.decode(invalidV6).isLeft shouldBe true
      When("both historical representations pass the exact input and shared WorkOrder codecs")
      Vector(absent, explicit).foreach { wire =>
        val restored = W.inputCodec.decode(wire).toOption.get
        val order = _frame(restored)
        val encoded = take(WorkflowWorkOrderJsonV1.encodeC(order, W.inputCodec))
        val decoded = take(WorkflowWorkOrderJsonV1.decodeC(encoded, W.inputCodec))
        Then("the representation and original stored bytes remain unchanged on a fresh read")
        W.inputCodec.encode(restored) shouldBe wire
        take(WorkflowWorkOrderJsonV1.encodeC(decoded, W.inputCodec)) shouldBe encoded
        W.inputCodec.decode(changeSbt(wire)(_.mapObject(_.add("estimateFieldPresent", Json.True)))).isLeft shouldBe true
        W.inputCodec.decode(changeSbt(wire)(_.mapObject(_.add("unexpected", Json.True)))).isLeft shouldBe true
        val root = Files.createTempDirectory("retained-sbt-estimate-")
        try {
          take(new IssuedWorkOrderPersistence.LocalJson(root, W.inputCodec).putIfAbsentC(order))
          val paths = Files.list(root)
          val file = try paths.iterator().asScala.toVector.head finally paths.close()
          val before = Files.readAllBytes(file).toVector
          new String(before.toArray, StandardCharsets.UTF_8) shouldBe encoded
          val loaded = take(new IssuedWorkOrderPersistence.LocalJson(root, W.inputCodec)
            .loadC(ContinuationIdentity("retained-continuation"))).get
          take(WorkflowWorkOrderJsonV1.encodeC(loaded, W.inputCodec)) shouldBe encoded
          Files.readAllBytes(file).toVector shouldBe before
        } finally {
          val paths = Files.list(root)
          try paths.iterator().asScala.foreach(path => Files.deleteIfExists(path)) finally paths.close()
          Files.deleteIfExists(root)
        }
      }
    }
    "losslessly preserve implementation review and repair payloads with their original history decisions and counters" in {
      Given("small semantic fixtures with a committed prefix, retained progress and an explicit finite v5 profile")
      val requests = Vector(_implementation.copy(operatingProfile = Some(_profile)),
        _review.copy(operatingProfile = Some(_review_profile)), _repair.copy(operatingProfile = Some(_profile)))
      val wires = requests.map(W.inputCodec.encode)
      When("the owning input codec reads and reprojects each retained request")
      val decoded = wires.map(W.inputCodec.decode)
      Then("v5 remains v5 and every original semantic field and retained metadata value stays intact")
      decoded shouldBe requests.map(Right(_))
      decoded.zip(wires).foreach { case (result, wire) =>
        W.inputCodec.encode(result.toOption.get) shouldBe wire
        wire.hcursor.get[String]("schemaVersion") shouldBe Right(_prefix + "v5")
      }
      val restored = decoded.last.toOption.get
      restored.instanceIdentity shouldBe _repair.instanceIdentity
      restored.sourceRevision shouldBe _repair.sourceRevision
      restored.generation shouldBe _repair.generation
      restored.selectedPlan shouldBe _repair.selectedPlan
      restored.originalBase shouldBe _repair.originalBase
      restored.candidate shouldBe _repair.candidate
      restored.completed shouldBe _repair.completed
      restored.completed should not be empty
      restored.repairProgress shouldBe _repair.repairProgress
      restored.repairProgress should not be empty
      restored.diagnosticHistory shouldBe Some(Vector(_prior))
      restored.decisionContext shouldBe Some(_decision)
      restored.operatingProfile shouldBe Some(_profile)
      wires(1).hcursor.downField("payload").downField("candidate").focus should not be empty
      wires.last.hcursor.downField("payload").downField("diagnosticHistory").focus should not be empty
      wires.last.hcursor.downField("payload").downField("decisionContext").focus should not be empty
    }

    "canonically retain expanded captures and selection provenance in v5 and v6 without inventing execution observations" in {
      Given("the adopted Capture contract with optional reasoning, configured concrete routes and logical dispatch")
      val standard = Vector("explicit", "config", "default").map(source =>
        _profile.copy(selection = _profile.selection.copy(source = source)))
      val mapped = Vector("cost", "quality", "local-experiment").flatMap(identity =>
        Vector("explicit", "config", "default").map(source =>
          _mapped_profile.copy(selection = P.Selection(identity, "1", source))))
      val profiles = standard ++ mapped ++ Vector(_profile.copy(reasoningClass = None),
        _profile.copy(reasoningClass = Some("UnknownClass")), _mapped_profile,
        _mapped_profile.copy(selection = _profile.selection, fallback = Some(_fallback)), _logical_profile)
      // The existing fixture is a synthetic typed failure, never a native execution observation.
      val failure = W.PublicationFailureContext(ContextReference(_repair.instanceIdentity, "1"),
        Vector("current.scala"), "Retained primary publication failure", PrimaryPublicationContinuationSpecificationFixture.failed,
        testsExecuted = false)
      profiles.foreach { profile =>
        val ordinary = _repair.copy(operatingProfile = Some(profile))
        val publication = ordinary.copy(candidate = None, checksReceipt = None, publicationFailure = Some(failure))
        Vector("v5" -> ordinary, "v6" -> publication).foreach { case (version, request) =>
          When("the exact canonical retained request is decoded and saved again")
          val wire = W.inputCodec.encode(request)
          val decoded = W.inputCodec.decode(wire)
          Then("all capture fields and original selection provenance survive without normalization")
          P.captureValid(profile) shouldBe true
          wire.hcursor.get[String]("schemaVersion") shouldBe Right(_prefix + version)
          decoded shouldBe Right(request)
          W.inputCodec.encode(decoded.toOption.get) shouldBe wire
          decoded.toOption.get.operatingProfile shouldBe Some(profile)
          W.inputCodec.decode(wire.mapObject(_.add("extra", Json.True))).isLeft shouldBe true
          W.inputCodec.decode(_payload(wire)(_.mapObject(_.add("extra", Json.True)))).isLeft shouldBe true
          W.inputCodec.decode(_profile_wire(wire)(_.mapObject(_.add("extra", Json.True)))).isLeft shouldBe true
          W.inputCodec.decode(_selection_wire(wire)(_.mapObject(_.add("extra", Json.True)))).isLeft shouldBe true
          W.inputCodec.decode(_version(wire, if (version == "v5") "v6" else "v5")).isLeft shouldBe true
        }
      }
    }

    "report a safe deterministic structural location for canonical v5 and v6 rejection" in {
      Given("canonical retained requests and unrecognized secret-bearing fields at several structural depths")
      val failure = W.PublicationFailureContext(ContextReference(_repair.instanceIdentity, "1"),
        Vector("current.scala"), "Retained primary publication failure", PrimaryPublicationContinuationSpecificationFixture.failed,
        testsExecuted = false)
      val v5 = _repair.copy(operatingProfile = Some(_profile))
      val requests = Vector(v5, v5.copy(candidate = None, checksReceipt = None, publicationFailure = Some(failure)))
      val secretKey = "secret-unrecognized-name"
      val secretValue = "secret-payload-value"
      requests.foreach { request =>
        val wire = W.inputCodec.encode(request)
        val root = wire.mapObject(_.add(secretKey, secretValue.asJson))
        val nested = _payload(wire)(_.mapObject(fields => fields.add("originalBase",
          fields("originalBase").get.mapObject(_.add(secretKey, secretValue.asJson)))))
        val array = _payload(wire)(_.mapObject(fields => fields.add("completed",
          fields("completed").get.mapArray(items => items.updated(0,
            items(0).mapObject(_.add(secretKey, secretValue.asJson)))))))
        When("the unchanged codec decodes the canonical wire and each noncanonical variation")
        W.inputCodec.decode(wire) shouldBe Right(request)
        W.inputCodec.encode(W.inputCodec.decode(wire).toOption.get) shouldBe wire
        val missingReceipt = _payload(wire)(_.mapObject(_.remove("checksReceipt")))
        W.inputCodec.decode(missingReceipt) shouldBe Left(
          "retained v5 work must preserve its exact canonical payload [path=/payload/checksReceipt; category=missing-field]")
        W.inputCodec.decode(missingReceipt) shouldBe W.inputCodec.decode(missingReceipt)
        val variations = Vector(root -> "/", nested -> "/payload/originalBase", array -> "/payload/completed/0")
        Then("only rejected wires report their first known location without either secret")
        variations.foreach { case (value, path) =>
          val error = W.inputCodec.decode(value).swap.toOption.get
          error shouldBe ("retained v5 work must preserve its exact canonical payload [path=" + path +
            "; category=unexpected-field]")
          W.inputCodec.decode(value) shouldBe Left(error)
          error should not include secretKey
          error should not include secretValue
        }
      }
    }

    "locate nested schema differences without exposing opaque map keys or values" in {
      val expected = Json.obj("payload" -> Json.obj("selectedPlan" -> Json.obj(
        "steps" -> Json.arr(Json.obj("checks" -> Json.arr(), "purpose" -> Json.fromString("original"))))))
      val actual = Json.obj("payload" -> Json.obj("selectedPlan" -> Json.obj(
        "steps" -> Json.arr(Json.obj("purpose" -> Json.fromString("original"))))))
      W._structural_mismatch(expected, actual) shouldBe Some("/payload/selectedPlan/steps/0/checks" -> "missing-field")
      val secret = "secret-map-key"
      val left = Json.obj("operatingProfile" -> Json.obj("configured" -> Json.obj(
        "controls" -> Json.obj(secret -> Json.fromString("secret-map-value")))))
      val right = Json.obj("operatingProfile" -> Json.obj("configured" -> Json.obj(
        "controls" -> Json.obj(secret -> Json.fromString("another-secret")))))
      val mismatch = W._structural_mismatch(left, right)
      mismatch shouldBe Some("/operatingProfile/configured/controls" -> "field-mismatch")
      mismatch.toString should not include secret
      mismatch.toString should not include "secret-map-value"
      W._structural_mismatch(left, right) shouldBe mismatch
    }

    "keep new unmarked requests and legacy v2 v3 v4 version and normalization boundaries unchanged" in {
      Given("ordinary constructors without a retained profile and existing purpose/history markers")
      val legacy = Vector(_implementation, _review, _repair)
      val versions = Vector("v2", "v3", "v4")
      val wires = legacy.map(W.inputCodec.encode)
      When("the reader consumes old encodings and incompatible legacy marker combinations")
      val mismatches = Vector(_version(wires(1), "v2"), _version(wires(0), "v3"),
        _version(wires(0), "v4"), _version(wires(2), "v2"), _version(wires(2), "v3"))
      Then("absence stays absent and a version cannot silently gain or discard its old purpose or diagnostic contract")
      legacy.zip(wires).zip(versions).foreach { case ((request, wire), version) =>
        request.operatingProfile shouldBe None
        wire.hcursor.get[String]("schemaVersion") shouldBe Right(_prefix + version)
        wire.hcursor.downField("payload").downField("operatingProfile").focus shouldBe None
        W.inputCodec.decode(wire) shouldBe Right(request)
        val annotated = _payload(wire.mapObject(_.add("oldRootAnnotation", Json.True)))(
          _.mapObject(_.add("oldPayloadAnnotation", Json.True)))
        W.inputCodec.decode(annotated) shouldBe Right(request)
        W.inputCodec.encode(W.inputCodec.decode(annotated).toOption.get) shouldBe wire
      }
      mismatches.foreach(value => W.inputCodec.decode(value).isLeft shouldBe true)
      W.inputCodec.decode(_version(wires.head, "v5")).isLeft shouldBe true
    }

    "reject malformed null unknown or contradictory capture metadata and profile markers on every old schema" in {
      Given("one canonical standard capture and the adopted expanded metadata validation boundary")
      val wire = W.inputCodec.encode(_repair.copy(operatingProfile = Some(_profile)))
      val profile = wire.hcursor.downField("payload").downField("operatingProfile").focus.get
      val malformedselection = Vector(
        _selection_wire(wire)(_.mapObject(_.add("identity", "other".asJson))),
        _selection_wire(wire)(_.mapObject(_.add("revision", "2".asJson))),
        _selection_wire(wire)(_.mapObject(_.add("source", "other".asJson))),
        _selection_wire(wire)(_.mapObject(_.add("extra", Json.True)))) ++
        Vector("identity", "revision", "source").flatMap(key => Vector(
          _selection_wire(wire)(_.mapObject(_.remove(key))),
          _selection_wire(wire)(_.mapObject(_.add(key, Json.Null)))))
      val malformedprofile = Vector(
        _profile_wire(wire)(_.mapObject(_.add("reasoningClass", "".asJson))),
        _profile_wire(wire)(_.mapObject(_.add("reasoningClass", " CodingEngineeringDeep".asJson))),
        _profile_wire(wire)(_.mapObject(_.add("extra", Json.True))),
        _profile_wire(wire)(_ => Json.Null), _profile_wire(wire)(_ => Json.arr())) ++
        Vector(_profile_wire(wire)(_.mapObject(_.remove("selection")))) ++
        Vector("selection", "reasoningClass", "mappingIdentity", "mappingRevision", "configured", "selected", "fallback", "dispatch").map(key =>
          _profile_wire(wire)(_.mapObject(_.add(key, Json.Null))))
      val contradictory = Vector(
        _profile.copy(mappingIdentity = Some("fixture-mapping")),
        _profile.copy(mappingRevision = Some("1")),
        _profile.copy(configured = Some(_concrete)),
        _profile.copy(selected = Some(_concrete)),
        _profile.copy(fallback = Some(_fallback)),
        _mapped_profile.copy(reasoningClass = None),
        _mapped_profile.copy(mappingRevision = None),
        _mapped_profile.copy(configured = None),
        _mapped_profile.copy(selected = None),
        _mapped_profile.copy(fallback = Some(_fallback)),
        _logical_profile.copy(selection = _mapped_profile.selection),
        _logical_profile.copy(fallback = Some(_fallback))).map(profile =>
          W.inputCodec.encode(_repair.copy(operatingProfile = Some(profile))))
      val mappedwire = W.inputCodec.encode(_repair.copy(operatingProfile = Some(_mapped_profile)))
      val malformedconcrete = Vector("configured", "selected").map(key =>
        _profile_wire(mappedwire)(value => value.mapObject(fields => fields.add(key,
          fields(key).get.mapObject(_.add("extra", Json.True))))))
      val malformeddispatch = _profile_wire(W.inputCodec.encode(_repair.copy(operatingProfile = Some(_logical_profile))))(
        value => value.mapObject(fields => fields.add("dispatch", fields("dispatch").get.mapObject(_.add("extra", Json.True)))))
      val oldwithprofile = Vector(_implementation, _review, _repair).map(W.inputCodec.encode).flatMap(value => Vector(
        _payload(value)(_.mapObject(_.add("operatingProfile", profile))),
        _payload(value)(_.mapObject(_.add("operatingProfile", Json.Null)))))
      When("the finite nested readers and schema-specific presence rules inspect each incompatible value")
      val results = (malformedselection ++ malformedprofile ++ contradictory ++ malformedconcrete ++ Vector(malformeddispatch) ++ oldwithprofile)
        .map(W.inputCodec.decode)
      Then("unsupported metadata and schema hybrids refuse without fallback or version downgrade")
      results.foreach(_.isLeft shouldBe true)
      W.inputCodec.decode(_payload(wire)(_.mapObject(_.remove("operatingProfile")))).isLeft shouldBe true
      W.inputCodec.decode(_version(wire, "v6")).isLeft shouldBe true
    }

    "reject lossy v5 root payload and nested projections while retaining existing semantic validation" in {
      Given("a closed canonical v5 repair with nonempty decision and diagnostic history")
      val wire = W.inputCodec.encode(_repair.copy(operatingProfile = Some(_profile)))
      val malformed = Vector(wire.mapObject(_.add("extra", Json.True)),
        _payload(wire)(_.mapObject(_.add("extra", Json.True))),
        _payload(wire)(value => value.mapObject(fields => fields.add("selectedPlan",
          fields("selectedPlan").get.mapObject(_.add("extra", Json.True))))),
        _payload(wire)(value => value.mapObject(fields => fields.add("decisionContext",
          fields("decisionContext").get.mapObject(_.add("extra", Json.True))))),
        _payload(wire)(_.mapObject(_.add("diagnosticHistory", Json.arr()))),
        _payload(wire)(_.mapObject(_.add("purposeRepair", Json.Null))),
        wire.mapObject(_.add("schemaVersion", Json.Null)), wire.mapObject(_.remove("schemaVersion")),
        wire.mapObject(_.add("payload", Json.Null)))
      When("the v5 reader re-encodes its validated Request and compares the complete original wire")
      val rejected = malformed.map(W.inputCodec.decode)
      Then("discarded annotations missing data and invalid retained semantic contracts cannot be silently normalized")
      rejected.foreach(_.isLeft shouldBe true)
      W.inputCodec.decode(Json.Null).isLeft shouldBe true
      W.inputCodec.decode(null).isLeft shouldBe true
    }

    "preserve expanded captures through the retained-review-description-path shared wire reader and passive replay" in {
      Given("isolated synthetic ReviewCritical review and CodingEngineeringDeep repair and historical review WorkOrders")
      val requests = Vector(_review.copy(operatingProfile = Some(_review_profile)),
        _repair.copy(operatingProfile = Some(_profile)), _review.copy(operatingProfile = Some(_profile)),
        _repair.copy(operatingProfile = Some(_mapped_profile)), _review.copy(operatingProfile = Some(_logical_profile)))
      def _saved_bytes_(root: Path): Map[Path, Vector[Byte]] = {
        val paths = Files.list(root)
        try paths.iterator().asScala.map(path => path -> Files.readAllBytes(path).toVector).toMap
        finally paths.close()
      }
      requests.foreach { request =>
        val root = Files.createTempDirectory("retained-review-description-path-")
        try {
          val issued = _frame(request)
          val mode = take(NativeWorkflowIssuedWorkContext.goalModeC(request.kind))
          val before = take(NativeWorkflowIssuedWorkContext.fromIssuedC(issued, mode, W.inputCodec))
          val encoded = take(WorkflowWorkOrderJsonV1.encodeC(issued, W.inputCodec))
          val saved = take(new IssuedWorkOrderPersistence.LocalJson(root, W.inputCodec).putIfAbsentC(issued))
          val originalbytes = _saved_bytes_(root)
          val identity = ContinuationIdentity("retained-continuation")
          When("fresh LocalJson readers load normally stored wire through the common decoder used by managed description")
          // LocalJson and managed DataStore share the wire decoder, not the storage adapter or Session lifecycle.
          val restored = take(new IssuedWorkOrderPersistence.LocalJson(root, W.inputCodec).loadC(identity)).get
          val repeated = take(new IssuedWorkOrderPersistence.LocalJson(root, W.inputCodec).loadC(identity)).get
          val replayed = take(NativeWorkflowIssuedWorkContext.fromIssuedC(restored, mode, W.inputCodec))
          val input = take(NativeWorkflowOperationOutput.workInputC(restored))
          val output = NativeWorkflowOperationOutput.goalWork(input)
          Then("the complete request contracts requirements history counters source candidate profile and saved bytes stay identical")
          saved shouldBe issued
          restored shouldBe issued
          repeated shouldBe issued
          restored.handle shouldBe issued.handle
          originalbytes.size shouldBe 1
          new String(originalbytes.values.head.toArray, StandardCharsets.UTF_8) shouldBe encoded
          take(WorkflowWorkOrderJsonV1.decodeC(encoded, W.inputCodec)) shouldBe issued
          take(WorkflowWorkOrderJsonV1.encodeC(restored, W.inputCodec)) shouldBe encoded
          take(replayed.toRecord().toJsonStringC) shouldBe take(before.toRecord().toJsonStringC)
          take(output.toRecord().toJsonStringC) shouldBe
            take(NativeWorkflowOperationOutput.goalWork(request).toRecord().toJsonStringC)
          input shouldBe request
          input.instanceIdentity shouldBe request.instanceIdentity
          input.sourceRevision shouldBe request.sourceRevision
          input.kind shouldBe request.kind
          input.generation shouldBe request.generation
          input.selectedPlan shouldBe request.selectedPlan
          input.originalBase shouldBe request.originalBase
          input.candidate shouldBe request.candidate
          input.completed shouldBe request.completed
          input.repairProgress shouldBe request.repairProgress
          input.diagnosticHistory shouldBe request.diagnosticHistory
          input.decisionContext shouldBe request.decisionContext
          input.operatingProfile shouldBe request.operatingProfile
          _saved_bytes_(root) shouldBe originalbytes
        } finally {
          val paths = Files.list(root)
          try paths.iterator().asScala.foreach(path => Files.deleteIfExists(path))
          finally paths.close()
          Files.deleteIfExists(root)
        }
      }
    }

    "identify the first retained cross-field rejection without accepting or normalizing its payload" in {
      Given("synthetic previously typed requests with one semantic history or cross-field contradiction")
      val requests = Vector(
        "diagnosticHistory" -> _repair.copy(diagnosticHistory = Some(Vector.empty)),
        "purposeRepair" -> _review.copy(purpose = _review.purpose + " changed"),
        "releaseRepairContract" -> _repair.copy(releaseRepairContract = Some(W.RELEASE_REPAIR_CONTRACT)),
        "diagnosticHistory" -> _repair.copy(diagnosticHistory = Some(Vector.empty),
          releaseRepairContract = Some(W.RELEASE_REPAIR_CONTRACT)))
      requests.foreach { case (field, request) =>
        val wire = W.inputCodec.encode(request)
        When("the unchanged request admission predicates inspect the exact synthetic encoding")
        val decoded = W.inputCodec.decode(wire)
        val order = take(WorkflowWorkOrderJsonV1.encodeC(_frame(request), W.inputCodec))
        val propagated = WorkflowWorkOrderJsonV1.decodeC(order, W.inputCodec)
        Then("the first failing predicate is identified and the request remains rejected at both boundaries")
        decoded shouldBe Left("Invalid retained check failure context [field=" + field + "]")
        propagated.isSuccess shouldBe false
        W.inputCodec.encode(request) shouldBe wire
      }
    }

    "retain semantic result admission independently of expanded capture metadata" in {
      Given("an unsupported semantic result and each finite retained profile")
      val unsupported = W.Result("accept", "Metadata does not accept semantic work", Vector.empty)
      val profiles = Vector(_profile, _review_profile, _mapped_profile, _logical_profile)
      When("existing semantic and initial-result admission sees requests with and without retained metadata")
      val semantic = profiles.map(profile => W.admitSemanticResultC(_repair.copy(operatingProfile = Some(profile)), unsupported))
      val initial = profiles.map(profile => W.admitInitialResultC(_implementation.copy(operatingProfile = Some(profile)), unsupported))
      val unmarked = W.admitSemanticResultC(_repair, unsupported)
      Then("metadata grants no acceptance authority and unmarked implementation encoding remains v2")
      semantic.foreach(_.isSuccess shouldBe false)
      initial.foreach(_.isSuccess shouldBe false)
      unmarked.isSuccess shouldBe false
      W.inputCodec.encode(_implementation).hcursor.get[String]("schemaVersion") shouldBe Right(_prefix + "v2")
    }
  }
}
