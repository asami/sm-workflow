package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import GoalPhaseNativeMetadata.given
import java.nio.file.{Files, Path}
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.context.RuntimeContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.record.Record
import org.goldenport.record.io.RecordDecoder
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.preparation.NativeReviewPreparation as Preparation
import org.simplemodeling.textus.smworkflow.value as V
import NativeExternalEvidence.{Purpose, Evidence}
import SplitPhaseExplicitPartitionFixture.take
import NativePreparationFixture.expectedRecordJson

/** Specification-issued WorkOrders and rows exercise passive preparation.
  * They are not genuine independent review, Human consent or effect authority.
  * @since   Oct. 14, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
final class NativeReviewPreparationSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  private val _now = Instant.parse("2026-10-07T00:00:00Z")
  private val _reference = V.WorkflowArtifactReference("specification-review", "1")
  private val _execution = V.WorkflowExecutionEvidence(Vector(_reference), Some("specification-reviewer"), None, None)
  private val _goal_result = V.GoalPhaseWorkResult("accept", "Specification-owned review response", Vector.empty, None, None, None, None, None, None, None)
  private val _repository_result = V.RepositorySyncNativeMergeReviewResult("accept", "Specification-owned merge review", Vector.empty)
  private val _receipt = RepositorySyncNativeMergeValidation.Receipt("focused", "exited", Some(0), "diagnostic output", "")
  private val _goal_input = NativeHumanEvidenceFixture.work.copy(sourceRevision = "7", kind = "ReviewStep",
    generation = 1, candidate = Some(NativeHumanEvidenceFixture.snapshot), checksReceipt = Some("selected-checks"),
    review = Some(GoalPhaseNativeReview.Context(Vector("one.scala"), "specification diff", Vector(_receipt))))
  private val _repository_input = RepositorySyncNativeMergeReview.Request("instance", "7", "a" * 40, "c" * 40,
    "b" * 40, Vector("one.scala"), "local diagnostic diff", "remote diagnostic diff", "required",
    "Selected specification check", Vector("focused"), Vector(_receipt))
  private val _typed_receipt = _receipt.copy(sbt = Some(WorkflowSbtValidation.Facts(
    WorkflowSbtValidation.Kind.TestOnly, WorkflowSbtValidation.Outcome.Succeeded,
    Some(WorkflowSbtValidation.Selection(Vector("example.FirstSpec", "example.SecondSpec"), 2, 25L)), None)))
  private val _typed_goal_input = _goal_input.copy(review = _goal_input.review.map(_.copy(checks = Vector(_typed_receipt))))
  private val _typed_repository_input = _repository_input.copy(checks = Vector(_typed_receipt))
  private val _declared = NativeExternalEvidence.Declaration("workflow", DataStore.CollectionId.EntityStore(
    EntityCollectionId("test", "host", "review_evidence")), "normal-development-host")
  private val _key = ThreeWorkflowExternalEvidencePorts.key(_reference.identity, _reference.revision)

  private def _with_core(body: Setup => Consequence[Unit]): Unit = {
    val parent = Files.createDirectories(Path.of("target/sm-workflow/work/review-preparation").toAbsolutePath)
    val root = Files.createTempDirectory(parent, "specification-")
    _with_runtime_(root, "UpToDate") { (_, _, _, prepare) => prepare(body) }
  }

  // Explicit specification issuance, not a production issuer or stored history.
  private def _work[A](workflow: String, input: A, inputtype: String, resulttype: String,
    completion: StateMachineOperationIdentity): WorkflowInteraction[A, Nothing] = {
    val handle = WorkflowHandle(SmWorkflowComponent.componentId, WorkflowDefinitionIdentity(workflow),
      WorkflowDefinitionRevision("specification-executable"), InstanceIdentity("instance"))
    val request = ContinuationRequest(StateMachineRunIdentity("specification-run"), ContinuationIdentity("specification-review-slot"),
      StateMachineRevision("8"), StateMachineRequiredOperationIdentity("specification-review-capability"),
      StateMachineOperationIdentity("SpecificationReview", "review"), Some(TypedValue(inputtype, input)),
      Some(StateMachineResultTypeReference(resulttype)), ContextBundle("Specification input", Vector.empty, Vector.empty,
        ContextSnapshot(handle.workflowRevision.value)), CompletionContract("specification-completion", Vector.empty),
      EvidenceContract("specification-evidence", Vector.empty), completion)
    WorkflowInteraction[A, Nothing](handle, WorkflowContinuation.WorkOrder(request,
      ExecutionRequirement(Vector.empty, RiskLevel("SOURCE_REVIEW"), ReasoningLevel.Standard, true),
      MinimalPresentation("Specification review", "No genuine host attestation")))
  }
  private def _goal_work(input: GoalPhaseNativeWork.Request = _goal_input) = _work("GoalPhaseWorkflow", input,
    GoalPhaseNativeWork.inputCodec.typeIdentity, GoalPhaseNativeWork.resultCodec.typeIdentity, GoalPhaseNativeRun.completionOperation)
  private def _repository_work = _work("RepositorySyncWorkflow", _repository_input,
    RepositorySyncNativeMergeReview.inputCodec.typeIdentity, RepositorySyncNativeMergeReview.resultCodec.typeIdentity,
    RepositorySyncNativeMergeReviewContinuation.completionOperation)
  private def _typed_repository_work = _work("RepositorySyncWorkflow", _typed_repository_input,
    RepositorySyncNativeMergeReview.inputCodec.typeIdentity, RepositorySyncNativeMergeReview.resultCodec.typeIdentity,
    RepositorySyncNativeMergeReviewContinuation.completionOperation)
  private def _submitted[W, A](work: WorkflowInteraction[W, Nothing], value: A, typeidentity: String): ContinuationResult[A] = {
    val request = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[W]].request
    val reference = ContextReference(_reference.identity, _reference.revision)
    ContinuationResult(work.handle, request.runId, request.continuationId, request.expectedRevision, request.context.snapshot,
      TypedValue(typeidentity, value), reference, Vector.empty, ExecutionEvidence(Vector(reference), _execution.workerIdentity))
  }
  private def _wire(record: Record): Record = take(new RecordDecoder().json(take(record.toJsonStringC)))
  private def _json(record: Record): Json = io.circe.parser.parse(take(record.toJsonStringC)).toOption.get
  private def _record(json: Json): Record = take(new RecordDecoder().json(json.noSpaces))
  private def _evidence(prepared: Preparation.Prepared): Evidence =
    take(NativeExternalEvidence.decodeStorageC(prepared.record.getString(NativeExternalEvidence.storageField).get))
  private def _observe(evidence: Evidence, purpose: Purpose, actual: Json): Consequence[NativeExternalEvidence.Observed] =
    NativeExternalEvidence.observeC(evidence, _declared, purpose, _key, actual, _now)
  private def _verdict(evidence: Evidence): Json =
    evidence.binding.hcursor.downField("attestation").downField("result").downField("verdict").focus.get
  private val _handle_names = Vector("componentIdentity" -> "component_identity",
    "workflowIdentity" -> "workflow_identity", "workflowRevision" -> "workflow_revision",
    "instanceIdentity" -> "instance_identity", "protocolVersion" -> "protocol_version")
  private val _goal_names = Vector("sourceRevision" -> "source_revision", "workKind" -> "work_kind",
    "checksReceipt" -> "checks_receipt", "changedPaths" -> "changed_paths")
  private val _repository_names = Vector("validatedRevision" -> "validated_revision", "localTip" -> "local_tip",
    "remoteTip" -> "remote_tip", "indexTree" -> "index_tree", "validationMode" -> "validation_mode",
    "changedPaths" -> "changed_paths", "selectedChecks" -> "selected_checks")
  private def _prepare(context: V.WorkflowReviewContext): Consequence[Preparation.Prepared] =
    if (context.purpose == Purpose.GoalReview.identity)
      Preparation.goalC(context, _goal_result, _reference, _execution, _declared.issuer, _now)
    else Preparation.repositoryC(context, _repository_result, _reference, _execution, _declared.issuer, _now)

  "E11 finite review observations" should afterWord(
    "in spec:goal-phase-finding-recording, example:E11, rules:R1,R5, phase:6.2"
  ) {
    "retain public observations in the actual prepared verdict and refuse reuse for changed content" in {
      Given("a specification-issued actual review context and a public result carrying nonblocking review observations")
      val observations = GoalPhaseFindingRecording.Observations(Vector(GoalPhaseFindingRecording.Observation(
        "hygiene:1", "HYGIENE", "Clarify the selected name", "Required behavior is already correct")))
      val result = GoalPhaseNativeWork.Result("accept", _goal_result.summary, Vector.empty,
        reviewObservations = Some(observations))
      val publicresult = _goal_result.copy(reviewObservations = GoalPhaseFindingRecording.publicObservations(result.reviewObservations))
      _with_core { call =>
        val work = _goal_work()
        val context = take(NativeReviewContext.goalC(call.core, work)).get
        val decoded = take(V.GoalPhaseWorkResult.createC(_wire(publicresult.toRecord())))
        val submitted = _submitted(work, result, GoalPhaseNativeWork.resultCodec.typeIdentity)
        When("the actual generated public reader and passive preparation encode the complete observations")
        val prepared = _evidence(take(Preparation.goalC(context, decoded, _reference, _execution, _declared.issuer, _now)))
        val actual = take(ThreeWorkflowExternalEvidencePorts.goalReviewBindingC(call.core, _goal_input, submitted))
        Then("the selected verdict and matching independent evidence include the actual observation body")
        _verdict(prepared).hcursor.get[Json]("reviewObservations") shouldBe Right(observations.asJson)
        _observe(prepared, Purpose.GoalReview, actual).isSuccess shouldBe true
        take(GoalPhaseFindingRecording.internalObservationsC(decoded.reviewObservations)) shouldBe Some(observations)

        Given("the same source keys and verdict but a different observation rationale")
        val changed = result.copy(reviewObservations = Some(observations.copy(items = observations.items.map(
          _.copy(rationale = "A different independent nonblocking rationale")))))
        val crossed = submitted.copy(result = submitted.result.copy(value = changed))
        When("the actual result binding is prepared for that different observation body")
        val changedbinding = take(ThreeWorkflowExternalEvidencePorts.goalReviewBindingC(call.core, _goal_input, crossed))
        Then("the old prepared evidence cannot be reused and the new body requires its own prepared evidence")
        changedbinding should not be actual
        _observe(prepared, Purpose.GoalReview, changedbinding).isSuccess shouldBe false
        val newlyprepared = _evidence(take(Preparation.goalC(context,
          decoded.copy(reviewObservations = GoalPhaseFindingRecording.publicObservations(changed.reviewObservations)),
          _reference, _execution, _declared.issuer, _now)))
        _observe(newlyprepared, Purpose.GoalReview, changedbinding).isSuccess shouldBe true

        Given("an absent historical field and separately explicit empty and malformed observations")
        When("passive preparation selects each actual public result")
        val historical = _evidence(take(Preparation.goalC(context, _goal_result, _reference, _execution, _declared.issuer, _now)))
        val empty = _evidence(take(Preparation.goalC(context,
          _goal_result.copy(reviewObservations = Some(V.GoalPhaseReviewObservations(Vector.empty))),
          _reference, _execution, _declared.issuer, _now)))
        Then("absence preserves its old verdict while explicit empty content stays present and malformed input fails")
        _verdict(historical).hcursor.downField("reviewObservations").focus shouldBe None
        _verdict(empty).hcursor.get[Json]("reviewObservations") shouldBe Right(Json.obj("items" -> Json.arr()))
        Vector(null, Some(null), Some(V.GoalPhaseReviewObservations(null))).foreach { invalid =>
          Preparation.goalC(context, _goal_result.copy(reviewObservations = invalid),
            _reference, _execution, _declared.issuer, _now).isSuccess shouldBe false
        }
        Consequence.unit
      }
    }
  }

  "Original Step review completion conditions" should {
    "preserve normal public readback conditions in managed review evidence and refuse changed removed or malformed content" in {
      Given("docs/spec/native-goal-command-task.md; a specification-owned ReviewStep with selected owned paths and one terminal-LF repair finding")
      val paths = Vector("docs/reader.md", "one.scala")
      val step = _goal_input.selectedPlan.steps.head.copy(ownedPaths = paths)
      val input = _goal_input.copy(selectedPlan = _goal_input.selectedPlan.copy(steps = Vector(step)),
        review = _goal_input.review.map(_.copy(changedPaths = paths)))
      val conditions = GoalPhaseNativeReviewCompletion.Conditions("terminal-lf", Vector(paths.head))
      val result = GoalPhaseNativeWork.Result("repair", "Remove only the extra terminal LF",
        Vector("Remove the extra terminal LF in " + paths.head), completionConditions = Some(conditions))
      val publicresult = _goal_result.copy(outcome = result.outcome, summary = result.summary, findings = result.findings,
        completionConditions = Some(GoalPhaseNativeMetadata.record(conditions.asJson)))
      _with_core { call =>
        val work = _goal_work(input)
        val submitted = _submitted(work, result, GoalPhaseNativeWork.resultCodec.typeIdentity)
        When("the normal command formatter and generated Record readers feed passive preparation with default property names")
        val resultjson = NativeExternalEvidenceToolFixture.normalCommandJson(publicresult)
        val decodedresult = take(V.GoalPhaseWorkResult.createC(take(new RecordDecoder().json(resultjson))))
        val output = take(NativeWorkflowOperationOutput.goalWorkRequestC(work, call.core))
        val decoded = take(V.GoalPhaseAdvanceResult.createC(take(new RecordDecoder().json(
          NativeExternalEvidenceToolFixture.normalCommandJson(output)))))
        val context = decoded.work.get.review.get.reviewContext.get
        val prepared = take(Preparation.goalC(context, decodedresult, _reference, _execution, _declared.issuer, _now))
        val evidence = _evidence(prepared)
        val actual = take(ThreeWorkflowExternalEvidencePorts.goalReviewBindingC(call.core, input, submitted))
        Then("the formatted result and prepared verdict retain the exact selected conditions and matching independent binding")
        io.circe.parser.parse(resultjson).toOption.get.hcursor.get[Json]("completion_conditions") shouldBe Right(conditions.asJson)
        take(GoalPhaseNativeReviewCompletion.fromPublicConditionsC(decodedresult.completionConditions)) shouldBe Some(conditions)
        _verdict(evidence).hcursor.get[Json]("completionConditions") shouldBe Right(conditions.asJson)
        _observe(evidence, Purpose.GoalReview, actual).isSuccess shouldBe true

        Given("the specification provisions that exact prepared row through its existing managed Create permission")
        take(call.createC("host_evidence", prepared.recordIdentity, prepared.record))
        val reader = take(NativeExternalEvidence.bindC(call.core.component.get.subsystem.get.configuration,
          GoalPhaseRuntimeParameters.Persistence(_configuration_, _collection_("instances"), _collection_("continuations"))))
        val port = new ThreeWorkflowExternalEvidencePorts(reader).goalReview
        val changed = result.copy(completionConditions = Some(conditions.copy(paths = Vector(paths(1)))))
        val removed = result.copy(completionConditions = None)
        When("the ordinary Goal review port consumes matching changed and removed conditions against the stored evidence")
        val accepted = port.admitC(call.core, input, submitted)
        val crossed = Vector(changed, removed).map(value => port.admitC(call.core, input,
          submitted.copy(result = submitted.result.copy(value = value))))
        Then("the matching independently bound result is admitted and neither alteration can reuse its evidence")
        accepted.isSuccess shouldBe true
        crossed.foreach(_.isSuccess shouldBe false)

        Given("a historical result with no completion field and null options null Records or malformed present Records")
        val historicalresult = publicresult.copy(completionConditions = None)
        val historicaljson = NativeExternalEvidenceToolFixture.normalCommandJson(historicalresult)
        val historical = take(V.GoalPhaseWorkResult.createC(take(new RecordDecoder().json(historicaljson))))
        val malformed = Vector[Option[Record]](null, Some(null), Some(Record.empty),
          Some(GoalPhaseNativeMetadata.record(conditions.copy(kind = "semantic").asJson)),
          Some(GoalPhaseNativeMetadata.record(conditions.copy(paths = Vector.empty).asJson)),
          Some(GoalPhaseNativeMetadata.record(Json.obj("kind" -> Json.fromString("terminal-lf"), "paths" -> Json.Null))))
        When("passive preparation consumes historical readback and each malformed public option")
        val oldevidence = _evidence(take(Preparation.goalC(context, historical, _reference, _execution, _declared.issuer, _now)))
        val refused = malformed.map(value => Preparation.goalC(context, publicresult.copy(completionConditions = value),
          _reference, _execution, _declared.issuer, _now))
        Then("historical absence stays absent and validly bound while malformed presence is rejected")
        historical.completionConditions shouldBe None
        _verdict(oldevidence).hcursor.downField("completionConditions").focus shouldBe None
        _observe(oldevidence, Purpose.GoalReview, take(ThreeWorkflowExternalEvidencePorts.goalReviewBindingC(call.core, input,
          submitted.copy(result = submitted.result.copy(value = removed))))).isSuccess shouldBe true
        refused.foreach(_.isSuccess shouldBe false)
        Consequence.unit
      }
    }
  }

  "Selected validation metadata review context" should {
    "normalize CNCF public metadata names and declared outer aliases while rejecting ambiguous or malformed batches" in {
      Given("a specification-issued review of a real fixture selection carried through the actual public generated Record reader")
      val batches = GoalPhaseNativeMetadataFixture.prepared._2
      _with_core { call =>
        val input = _typed_goal_input.copy(review = _typed_goal_input.review.map(_.copy(metadata = batches)))
        val output = take(NativeWorkflowOperationOutput.goalWorkRequestC(_goal_work(input), call.core))
        val decoded = take(V.GoalPhaseAdvanceResult.createC(_wire(output.toRecord())))
        val context = decoded.work.get.review.get.reviewContext.get
        val target = _json(context.target)
        val metadata = target.hcursor.downField("validationMetadata").focus.get
        val fields = target.asObject.get
        val spellings = Vector(fields, fields.remove("validationMetadata").add("validation_metadata", metadata),
          fields.add("validation_metadata", metadata))
        When("passive preparation consumes canonical snake-only and agreeing duplicate outer metadata names")
        val prepared = spellings.map(value => _evidence(take(_prepare(context.copy(
          target = GoalPhaseNativeMetadata.record(Json.fromJsonObject(value)))))))
        Then("all variants retain the same actual full inventory and canonical nested Batch keys")
        prepared.map(_.binding).distinct.size shouldBe 1
        prepared.foreach { value =>
          value.binding.hcursor.downField("attestation").downField("target").get[Vector[Json]]("validationMetadata") shouldBe
            Right(batches.map(_.asJson))
        }
        decoded.work.get.review.get.metadata.map(_json) shouldBe batches.map(_.asJson)
        metadata.hcursor.downArray.downField("inventory").get[Vector[Json]]("diagnostics") shouldBe Right(Vector.empty)

        Given("the same selected context target rendered by the actual CNCF default property naming context")
        val rendered = _wire(RuntimeContext.PropertyNameContext.default.transformRecord(context.target))
        val publictarget = _json(rendered)
        publictarget.hcursor.downField("validation_metadata").downArray.get[String]("check_identity") shouldBe
          Right(batches.head.checkIdentity)
        When("passive preparation consumes the actual nested public snake_case representation")
        val publicevidence = _evidence(take(_prepare(context.copy(target = rendered))))
        Then("its canonical selected binding equals the binding prepared from the original internal context")
        publicevidence.binding shouldBe prepared.head.binding

        Given("the actual public target with equal-valued source_revision and sourceRevision in a nested artifact")
        val ambiguous = publictarget.hcursor.downField("validation_metadata").downArray.downField("profile")
          .downField("scope").downField("artifacts").downArray
          .withFocus(_.mapObject(_.add("sourceRevision", Json.fromString("source-1")))).top
          .getOrElse(fail("Actual public target must retain its nested artifact"))
        When("passive preparation consumes that ambiguous nested property through a full Record")
        val collision = _prepare(context.copy(target = GoalPhaseNativeMetadata.record(ambiguous)))
        Then("no selected binding can silently discard either spelling even though the values agree")
        collision.isSuccess shouldBe false

        Given("the actual selected metadata and alternate outer names with contradictory empty malformed or unknown enum values")
        val unknowninventory = batches.head.inventory.asJson.mapObject(_.add("completeness", Json.fromString("FUTURE")))
        val unknownbatch = batches.head.asJson.mapObject(_.add("inventory", unknowninventory))
        val invalid = Vector(Json.arr(), Json.True, Json.arr(Json.obj()), Json.arr(unknownbatch))
        When("passive preparation receives each conflicting or invalid nonnull alias as a canonical Record")
        val refused = invalid.map(value => _prepare(context.copy(target = GoalPhaseNativeMetadata.record(
          Json.fromJsonObject(fields.add("validation_metadata", value))))))
        Then("no alternate spelling can erase selected context or manufacture a valid Batch")
        refused.foreach(_.isSuccess shouldBe false)
        invalid.tail.foreach { value =>
          _prepare(context.copy(target = GoalPhaseNativeMetadata.record(
            target.mapObject(_.add("validationMetadata", value))))).isSuccess shouldBe false
        }
        Given("Core R4 omits optional null validation metadata and a null alternate alias")
        val nullmetadata = target.mapObject(_.add("validationMetadata", Json.Null))
        val omittedmetadata = target.mapObject(_.remove("validationMetadata"))
        val nullrecord = GoalPhaseNativeMetadata.record(nullmetadata)
        When("passive preparation consumes absent metadata or the surviving valid canonical alias")
        val absent = Vector(nullrecord, _record(omittedmetadata)).map(value => _evidence(take(_prepare(context.copy(target = value)))))
        val nullalias = _evidence(take(_prepare(context.copy(target = GoalPhaseNativeMetadata.record(
          target.mapObject(_.add("validation_metadata", Json.Null)))))))
        Then("omission manufactures no metadata and a removed alias leaves the canonical selected binding unchanged")
        _json(nullrecord) shouldBe expectedRecordJson(nullmetadata)
        _json(nullrecord).hcursor.downField("validationMetadata").focus shouldBe None
        absent.map(_.binding).distinct.size shouldBe 1
        absent.head.binding.hcursor.downField("attestation").downField("target").downField("validationMetadata").focus shouldBe None
        absent.head.binding should not be prepared.head.binding
        nullalias.binding shouldBe prepared.head.binding
        Consequence.unit
      }
    }
  }

  "R4 selected review repair progress" should {
    "normalize actual typed progress and reject crossed or malformed presence in passive preparation and the managed Reader" in {
      Given("a specification-issued review captures actual selected Work progress 1/1/2")
      _with_core { call =>
        val input = _typed_goal_input.copy(selectedPlan = _typed_goal_input.selectedPlan.copy(repairLimit = 2),
          repairProgress = Some(GoalPhaseRepairProgress.Snapshot(1, 1, 2)))
        val work = _goal_work(input)
        val submitted = _submitted(work, GoalPhaseNativeWork.Result("accept", "Independent specification verdict", Vector.empty),
          GoalPhaseNativeWork.resultCodec.typeIdentity)
        When("public output and its generated reader carry progress to passive review preparation")
        val output = take(NativeWorkflowOperationOutput.goalWorkRequestC(work, call.core))
        val decoded = take(V.GoalPhaseAdvanceResult.createC(_wire(output.toRecord())))
        val context = decoded.work.get.review.get.reviewContext.get
        val raw = _json(context.target)
        val progress = raw.hcursor.downField("repairProgress").focus.get
        val snakeprogress = Json.obj("automatic_count" -> Json.fromInt(1), "ordinary_count" -> Json.fromInt(1), "limit" -> Json.fromInt(2))
        val snake = raw.mapObject(_.remove("repairProgress").add("repair_progress", snakeprogress))
        val matching = raw.mapObject(_.add("repair_progress", snakeprogress))
        val prepared = Vector(context, context.copy(target = _record(snake)), context.copy(target = _record(matching)))
          .map(c => take(_prepare(c)))
        val expected = take(ThreeWorkflowExternalEvidencePorts.goalReviewBindingC(call.core, input, submitted))
        Then("normal declaration and wire fields retain exactly the saved Work counts while the verdict has none")
        decoded.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(1, 1, 2))
        prepared.map(p => _evidence(p).binding).distinct.size shouldBe 1
        prepared.foreach(p => _observe(_evidence(p), Purpose.GoalReview, expected).isSuccess shouldBe true)
        _verdict(_evidence(prepared.head)).hcursor.downField("repairProgress").focus shouldBe None
        progress shouldBe input.repairProgress.get.asJson

        Given("the explicit prepared row provisioned with the existing managed Create permission and different selected review counts")
        take(call.createC("host_evidence", prepared.head.recordIdentity, prepared.head.record))
        val reader = take(NativeExternalEvidence.bindC(call.core.component.get.subsystem.get.configuration,
          GoalPhaseRuntimeParameters.Persistence(_configuration_, _collection_("instances"), _collection_("continuations"))))
        val crossed = take(ThreeWorkflowExternalEvidencePorts.goalReviewBindingC(call.core,
          input.copy(repairProgress = Some(GoalPhaseRepairProgress.Snapshot(2, 1, 2))), submitted))
        When("the existing current-call Reader checks exact and crossed selected progress against that same row")
        val accepted = take(reader.loadC(call.core, Purpose.GoalReview, _key, expected))
        val rejected = reader.loadC(call.core, Purpose.GoalReview, _key, crossed)
        Then("different actual review counts cannot reuse the independent evidence")
        accepted.map(_.evidence.binding) shouldBe Some(_evidence(prepared.head).binding)
        rejected.isSuccess shouldBe false

        Given("incomplete, noninteger and contradictory nonnull supplied progress fields")
        def _supplied_record_(value: Json): Record = {
          val record = _record(value)
          _json(record) shouldBe expectedRecordJson(value)
          record
        }
        val badvalues = Vector(Json.obj(), Json.True, Json.obj("automaticCount" -> Json.fromDoubleOrNull(1.5),
          "ordinaryCount" -> Json.fromInt(1), "limit" -> Json.fromInt(2)),
          progress.mapObject(_.add("ordinaryCount", Json.fromInt(2))),
          progress.mapObject(_.add("automatic_count", Json.fromInt(2))))
        val malformedtargets = badvalues.map(v => raw.mapObject(_.add("repairProgress", v))) ++ Vector(
          raw.mapObject(_.add("repair_progress", GoalPhaseRepairProgress.Snapshot(2, 1, 2).asJson)))
        val malformed = malformedtargets.map(target => context.copy(target = _supplied_record_(target)))
        When("public passive preparation encounters each present malformed value")
        val failures = malformed.map(_prepare)
        Then("none is silently omitted or admitted as selected context")
        failures.zipWithIndex.foreach { case (failure, index) =>
          val suppliedprogress = Vector("repairProgress", "repair_progress").map { name =>
            name + "=" + malformedtargets(index).hcursor.downField(name).focus.map(_.noSpaces).getOrElse("<absent>")
          }.mkString(", ")
          withClue("malformed progress case " + index + ": " + suppliedprogress.take(300) + ": ") {
            failure.isSuccess shouldBe false
          }
        }
        Given("Core R4 omits optional null review progress before passive preparation")
        val nullprogress = raw.mapObject(_.add("repairProgress", Json.Null))
        val nullrecord = _supplied_record_(nullprogress)
        When("the surviving target and its explicitly omitted equivalent are selected")
        val absent = Vector(nullrecord, _record(raw.mapObject(_.remove("repairProgress")))).map(value =>
          _evidence(take(_prepare(context.copy(target = value)))))
        Then("progress stays absent and cannot reproduce the original selected review counts")
        _json(nullrecord).hcursor.downField("repairProgress").focus shouldBe None
        absent.map(_.binding).distinct.size shouldBe 1
        absent.head.binding.hcursor.downField("attestation").downField("target").downField("repairProgress").focus shouldBe None
        _observe(absent.head, Purpose.GoalReview, expected).isSuccess shouldBe false
        Consequence.unit
      }
    }
  }

  "Structured issue target and independent coverage binding" should {
    "preserve actual issue context and report through strict wire intake and reject mismatches in the managed Reader" in {
      Given("a specification-issued re-review with one unresolved required REVIEW issue and explicit independent coverage")
      _with_core { call =>
        val issue = GoalPhaseFixIssues.Issue("issue-1", _typed_goal_input.selectedPlan.repository,
          _typed_goal_input.stepIdentity.get, "REVIEW", None, "Required selected behavior", "prior-independent-review", "OPEN", Some("FIXED"))
        val state = GoalPhaseFixIssues.State(Vector(issue), Vector(GoalPhaseFixIssues.Transition(
          issue.identity, None, "OPEN", issue.evidenceReference)), Some(GoalPhaseFixIssues.Movement(
          "prior-independent-review", 1, 0, 1, 0, false)))
        val input = _typed_goal_input.copy(issues = Some(state))
        val report = GoalPhaseFixIssues.Report(Vector(GoalPhaseFixIssues.Disposition(
          issue.identity, "RESOLVED", "Independent fixture observed the actual corrected behavior", None)))
        val result = GoalPhaseNativeWork.Result("accept", "Explicit independent coverage", Vector.empty, issueReport = Some(report))
        val publicresult = V.GoalPhaseWorkResult(result.outcome, result.summary, result.findings, None, None,
          GoalPhaseFixIssues.publicReport(result.issueReport), None, GoalPhaseFindingRecording.publicObservations(result.reviewObservations), None,
          result.completionConditions.map(condition => GoalPhaseNativeMetadata.record(condition.asJson)))
        val work = _goal_work(input)
        val native = _submitted(work, result, GoalPhaseNativeWork.resultCodec.typeIdentity)
        When("normal formatted output and generated readers carry actual selected issues into public passive preparation")
        val displayed = take(NativeWorkflowOperationOutput.goalWorkRequestC(work, call.core))
        val decoded = take(V.GoalPhaseAdvanceResult.createC(take(new RecordDecoder().json(
          NativeExternalEvidenceToolFixture.normalCommandJson(displayed)))))
        val supplied = take(V.GoalPhaseWorkResult.createC(_wire(publicresult.toRecord())))
        val context = decoded.work.get.review.get.reviewContext.get
        val prepared = take(Preparation.goalC(context, supplied, _reference, _execution, _declared.issuer, _now.minusSeconds(60)))
        val expected = take(ThreeWorkflowExternalEvidencePorts.goalReviewBindingC(call.core, input, native))
        val evidence = _evidence(prepared)
        Then("the selected state and actual coverage remain exactly in the target and verdict")
        take(GoalPhaseFixIssues.internalStateC(decoded.work.get.issues)) shouldBe Some(state)
        _verdict(evidence).hcursor.downField("issueReport").focus shouldBe Some(report.asJson)
        evidence.binding.hcursor.downField("attestation").downField("target").downField("issues").focus shouldBe Some(state.asJson)
        _observe(evidence, Purpose.GoalReview, expected).isSuccess shouldBe true

        Given("that explicit prepared row provisioned only through the existing managed Create permission")
        take(call.createC("host_evidence", prepared.recordIdentity, prepared.record))
        val reader = take(NativeExternalEvidence.bindC(call.core.component.get.subsystem.get.configuration,
          GoalPhaseRuntimeParameters.Persistence(_configuration_, _collection_("instances"), _collection_("continuations"))))
        val changedstate = state.copy(issues = Vector(issue.copy(summary = "A different required behavior")))
        val changedreport = report.copy(dispositions = report.dispositions.map(_.copy(disposition = "NOT_REVIEWED")))
        val foreigntarget = take(ThreeWorkflowExternalEvidencePorts.goalReviewBindingC(call.core, input.copy(issues = Some(changedstate)), native))
        val foreignverdict = take(ThreeWorkflowExternalEvidencePorts.goalReviewBindingC(call.core, input,
          native.copy(result = TypedValue(GoalPhaseNativeWork.resultCodec.typeIdentity, result.copy(issueReport = Some(changedreport))))))
        When("the normal managed Reader checks the exact binding and separately crossed issue target or coverage")
        val accepted = take(reader.loadC(call.core, Purpose.GoalReview, _key, expected))
        val rejected = Vector(foreigntarget, foreignverdict).map(reader.loadC(call.core, Purpose.GoalReview, _key, _))
        Then("only the actual selected issue context and actual independent report admit that same physical row")
        accepted.map(_.evidence.binding) shouldBe Some(evidence.binding)
        rejected.foreach(_.isSuccess shouldBe false)

        Given("P62-C-NULL-FIXTURE-03; present malformed typed public issue reports")
        val malformed = Vector(publicresult.copy(issueReport = null), publicresult.copy(issueReport = Some(null)),
          publicresult.copy(issueReport = Some(V.GoalPhaseIssueReport(Vector(null)))))
        When("public passive preparation strictly converts each present structured value")
        val failures = malformed.map(Preparation.goalC(context, _, _reference, _execution, _declared.issuer, _now))
        Then("malformed presence produces typed failure without dropping the state or coverage")
        failures.foreach(_.isSuccess shouldBe false)
        Given("Core R4 omits an optional null selected issue target")
        val rawtarget = _json(context.target).mapObject(_.add("issues", Json.Null))
        val nulltarget = context.copy(target = _record(rawtarget))
        val omittedtarget = context.copy(target = _record(rawtarget.mapObject(_.remove("issues"))))
        When("passive preparation consumes the surviving target and explicit omission with the same supplied report")
        val absent = Vector(nulltarget, omittedtarget).map(value => _evidence(take(Preparation.goalC(
          value, publicresult, _reference, _execution, _declared.issuer, _now))))
        Then("the target remains without issues and cannot reuse the original issue-bound evidence")
        _json(nulltarget.target) shouldBe expectedRecordJson(rawtarget)
        _json(nulltarget.target).hcursor.downField("issues").focus shouldBe None
        absent.map(_.binding).distinct.size shouldBe 1
        absent.head.binding.hcursor.downField("attestation").downField("target").downField("issues").focus shouldBe None
        _observe(absent.head, Purpose.GoalReview, expected).isSuccess shouldBe false
        Consequence.unit
      }
    }
  }

  "Saved quality boundary and typed report preparation" should afterWord(
    "in spec:goal-phase-acceptance-boundary, example:E3,E5, rules:R5,R6, phase:6"
  ) {
    "retain the generated report and effective_acceptance aliases alongside actual typed SBT facts" in {
      Given("a specification-issued configured review with saved explicit exclusion, typed TestOnly receipt and honest report")
      _with_core { call =>
        val boundary = GoalPhaseAcceptanceBoundary
        val project = boundary.Project(Vector(boundary.Condition("behavior", "all", true, "Selected behavior"),
          boundary.Condition("extra", "all", true, "Extra robustness")))
        val adoption = boundary.Adoption("Extra robustness is excluded", Vector(
          boundary.Condition("extra", "all", false, "Keep extra robustness advisory")))
        val plan = take(boundary.captureC(Some(project), _typed_goal_input.selectedPlan.copy(acceptance = Some(adoption))))
        val input = _typed_goal_input.copy(selectedPlan = plan)
        val report = boundary.Report(Vector(boundary.Observation("behavior", "satisfied", "Observed required behavior"),
          boundary.Observation("extra", "unsatisfied", "Excluded suggestion")), Vector("Consider extra later"))
        val result = GoalPhaseNativeWork.Result("accept", "Fixture review", Vector.empty, Some(report))
        val publicresult = V.GoalPhaseWorkResult(result.outcome, result.summary, result.findings, boundary.publicReport(result.acceptance),
          GoalPhaseFixAssessment.publicReport(result.fixAssessment), GoalPhaseFixIssues.publicReport(result.issueReport), None,
          GoalPhaseFindingRecording.publicObservations(result.reviewObservations), None,
          result.completionConditions.map(condition => GoalPhaseNativeMetadata.record(condition.asJson)))
        val work = _goal_work(input)
        val native = _submitted(work, result, GoalPhaseNativeWork.resultCodec.typeIdentity)
        When("the normal command formatter and generated readers carry the issued view and result into passive preparation")
        val output = take(NativeWorkflowOperationOutput.goalWorkRequestC(work, call.core))
        val decoded = take(V.GoalPhaseAdvanceResult.createC(take(new RecordDecoder().json(
          NativeExternalEvidenceToolFixture.normalCommandJson(output)))))
        val context = decoded.work.get.review.get.reviewContext.get
        val supplied = take(V.GoalPhaseWorkResult.createC(_wire(publicresult.toRecord())))
        val prepared = take(Preparation.goalC(context, supplied, _reference, _execution, _declared.issuer, _now))
        val evidence = _evidence(prepared)
        val expected = take(ThreeWorkflowExternalEvidencePorts.goalReviewBindingC(call.core, input, native))
        Then("normal host observation retains the full report, effective boundary and typed SBT selection")
        _observe(evidence, Purpose.GoalReview, expected).isSuccess shouldBe true
        _verdict(evidence).hcursor.downField("acceptance").focus shouldBe Some(report.asJson)
        val target = _json(context.target)
        target.hcursor.downField("test_checks").downArray.downField("sbt").focus
          .orElse(target.hcursor.downField("testChecks").downArray.downField("sbt").focus) should not be empty
        val effective = plan.effectiveAcceptance.get.asJson
        def _context_with_(value: Json) = context.copy(target = _record(value))
        val canonical = target.mapObject(_.remove("effective_acceptance").add("effectiveAcceptance", effective))
        val alternatives = Vector(canonical, canonical.mapObject(_.remove("effectiveAcceptance").add("effective_acceptance", effective)),
          canonical.mapObject(_.add("effective_acceptance", effective)))
        alternatives.foreach { target =>
          Given("declaration-only, wire-only or agreeing effectiveAcceptance/effective_acceptance spellings")
          When("passive preparation consumes the fixed aliases")
          val actual = take(Preparation.goalC(_context_with_(target), supplied, _reference, _execution, _declared.issuer, _now))
          Then("the exact selected quality and typed SBT binding remains the same")
          _evidence(actual).binding shouldBe evidence.binding
        }
        val conflicting = effective.mapObject(_.add("conditions", effective.hcursor.downField("conditions").focus.get.mapArray(
          _.map(_.mapObject(_.add("required", Json.True))))))
        Vector(conflicting, Json.True).foreach { different =>
          Given("contradictory effectiveAcceptance and effective_acceptance values")
          When("the fixed alias intake receives both spellings")
          val failed = Preparation.goalC(_context_with_(canonical.mapObject(_.add("effective_acceptance", different))),
            supplied, _reference, _execution, _declared.issuer, _now)
          Then("neither spelling wins over contradictory quality meaning")
          failed.isSuccess shouldBe false
        }
        Given("a generated report retaining invalid nested nulls or a null optional report container")
        val malformed = Vector(supplied.copy(acceptance = null), supplied.copy(acceptance = Some(
          V.GoalPhaseAcceptanceReport(Vector(null), Vector.empty))))
        When("passive preparation validates supplied report structure before encoding")
        val failures = malformed.map(Preparation.goalC(context, _, _reference, _execution, _declared.issuer, _now))
        Then("each fails as a typed consequence without throwing or inventing report data")
        failures.foreach(_.isSuccess shouldBe false)
        Consequence.unit
      }
    }
  }

  "E1 Goal issued context and strict wire intake" should afterWord(
    "in spec:native-host-review-preparation, example:E1, rules:R1,R2,R3, phase:6"
  ) {
    "prepare the selected Goal review relative to its actual native owner and issued WorkOrder" in {
      Given("Spec: docs/spec/native-host-review-preparation.md; Rules: R1,R2,R3; Example: E1; a specification-issued Goal WorkOrder and explicit reviewer inputs")
      _with_core { call =>
        val work = _goal_work()
        val native = _submitted(work, GoalPhaseNativeWork.Result(_goal_result.outcome, _goal_result.summary, _goal_result.findings),
          GoalPhaseNativeWork.resultCodec.typeIdentity)
        When("the actual output crosses normal structured JSON and its strict generated reader before public passive preparation")
        val output = take(NativeWorkflowOperationOutput.goalWorkRequestC(work, call.core))
        val decoded = take(V.GoalPhaseAdvanceResult.createC(_wire(output.toRecord())))
        val context = decoded.work.get.review.get.reviewContext.get
        val prepared = take(Preparation.goalC(context, _goal_result, _reference, _execution, _declared.issuer, _now.minusSeconds(60)))
        val expected = take(ThreeWorkflowExternalEvidencePorts.goalReviewBindingC(call.core, _goal_input, native))
        val evidence = _evidence(prepared)
        Then("the existing v7 codec admits only that actual owner, selected target and completion, without native revision or snapshot copies")
        context.componentInstance shouldBe call.core.component.get.core.instanceId.canonicalKey
        context.completion.runId shouldBe native.runId.value
        context.completion.continuationId shouldBe native.continuationId.value
        _observe(evidence, Purpose.GoalReview, expected).isSuccess shouldBe true
        prepared.recordIdentity shouldBe NativeExternalEvidence.recordIdentity(Purpose.GoalReview, _key)
        evidence.binding.noSpaces should not include "expectedRevision"
        evidence.binding.noSpaces should not include "contextSnapshot"
        Given("the same explicit specification input assigned to implementation or repair, with no review")
        val nonreview = Vector("ImplementStep", "RepairStep").map(kind => _goal_work(_goal_input.copy(kind = kind, review = None)))
        When("the owning extraction projects those nonreview work assignments")
        val contexts = nonreview.map(NativeReviewContext.goalC(call.core, _))
        Then("no review context is issued for implementation or repair")
        contexts.map(take(_)) shouldBe Vector(None, None)
        Consequence.unit
      }
    }
  }

  "E2 Repository issued context and strict nested Record intake" should afterWord(
    "in spec:native-host-review-preparation, example:E2, rules:R1,R2,R3, phase:6"
  ) {
    "prepare a selected merge review with the issued successful check receipt" in {
      Given("Spec: docs/spec/native-host-review-preparation.md; Rules: R1,R2,R3; Example: E2; a specification-issued Repository WorkOrder with selected check exit zero")
      _with_core { call =>
        val work = _repository_work
        val native = _submitted(work, RepositorySyncNativeMergeReview.Result(_repository_result.decision,
          _repository_result.rationale, Vector.empty), RepositorySyncNativeMergeReview.resultCodec.typeIdentity)
        When("the actual merge output crosses strict generated nested Record intake and public passive preparation")
        val output = take(NativeWorkflowOperationOutput.repositoryReviewRequestC(work, call.core))
        val decoded = take(V.RepositorySyncAdvanceResult.createC(_wire(output.toRecord())))
        val context = decoded.review.get.reviewContext.get
        val prepared = take(Preparation.repositoryC(context, _repository_result, _reference, _execution,
          _declared.issuer, _now.minusSeconds(60)))
        val expected = take(ThreeWorkflowExternalEvidencePorts.repositoryReviewBindingC(call.core, _repository_input, native))
        val evidence = _evidence(prepared)
        Then("selected native parents, tree, check exit and completion remain applicable to the actual owner")
        decoded.review.get.checks.head.exitCode shouldBe 0
        context.componentInstance shouldBe call.core.component.get.core.instanceId.canonicalKey
        context.completion.typeIdentity shouldBe native.result.typeIdentity
        _observe(evidence, Purpose.RepositoryReview, expected).isSuccess shouldBe true
        evidence.binding.noSpaces should not include "diagnostic"
        prepared.recordIdentity shouldBe NativeExternalEvidence.recordIdentity(Purpose.RepositoryReview, _key)
        Consequence.unit
      }
    }
  }

  "E3 Purpose-specific repair meaning" should afterWord(
    "in spec:native-host-review-preparation, example:E3, rules:R2,R3, phase:6"
  ) {
    "carry exact repair findings and directives while ignoring nonrepair diagnostics" in {
      Given("Spec: docs/spec/native-host-review-preparation.md; Rules: R2,R3; Example: E3; specification-issued contexts and explicit repair DTOs")
      _with_core { call =>
        val goal = take(NativeReviewContext.goalC(call.core, _goal_work())).get
        val repository = take(NativeReviewContext.repositoryC(call.core, _repository_work))
        val repair = V.GoalPhaseWorkResult("repair", "Fix the assigned case", Vector("Missing assigned case"), None, None, None, None, None, None, None)
        val finding = V.RepositorySyncNativeMergeReviewFinding("missing-case", Vector("one.scala"), "Repair only this native path")
        When("public preparation encodes each selected repair result through the existing binding")
        val goalrepair = _evidence(take(Preparation.goalC(goal, repair, _reference, _execution, _declared.issuer, _now)))
        val repositoryrepair = _evidence(take(Preparation.repositoryC(repository,
          V.RepositorySyncNativeMergeReviewResult("repair", "Unused overall rationale", Vector(finding)),
          _reference, _execution, _declared.issuer, _now)))
        val firstgoal = _evidence(take(Preparation.goalC(goal, _goal_result, _reference, _execution, _declared.issuer, _now)))
        val secondgoal = _evidence(take(Preparation.goalC(goal, _goal_result.copy(summary = "Other unused diagnostics"),
          _reference, _execution, _declared.issuer, _now)))
        val firstrepository = _evidence(take(Preparation.repositoryC(repository,
          V.RepositorySyncNativeMergeReviewResult("decision-needed", "Explanation", Vector(finding)),
          _reference, _execution, _declared.issuer, _now)))
        val secondrepository = _evidence(take(Preparation.repositoryC(repository,
          V.RepositorySyncNativeMergeReviewResult("decision-needed", "Other explanation", Vector(finding.copy(rationale = "Other unused rationale"))),
          _reference, _execution, _declared.issuer, _now)))
        Then("repair summary, findings, affected paths and rationale survive exactly, while nonrepair annotations retain Json.Null")
        _verdict(goalrepair) shouldBe Json.obj("outcome" -> Json.fromString("repair"),
          "findings" -> Json.arr(Json.fromString("Missing assigned case")), "repairSummary" -> Json.fromString("Fix the assigned case"))
        _verdict(repositoryrepair) shouldBe Json.obj("decision" -> Json.fromString("repair"), "findings" -> Json.arr(Json.obj(
          "identity" -> Json.fromString("missing-case"), "affectedPaths" -> Json.arr(Json.fromString("one.scala")),
          "repairRationale" -> Json.fromString("Repair only this native path"))))
        firstgoal.binding shouldBe secondgoal.binding
        _verdict(firstgoal).hcursor.downField("repairSummary").focus shouldBe Some(Json.Null)
        firstrepository.binding shouldBe secondrepository.binding
        _verdict(firstrepository).hcursor.downField("findings").downArray.downField("repairRationale").focus shouldBe Some(Json.Null)
        Consequence.unit
      }
    }
  }

  "E4 Missing consumed inputs" should afterWord(
    "in spec:native-host-review-preparation, example:E4, rules:R1,R3,R4, phase:6"
  ) {
    "refuse incomplete or crossed contexts without choosing a worker reference issuer or clock" in {
      Given("Spec: docs/spec/native-host-review-preparation.md; Rules: R1,R3,R4; Example: E4; actual specification context and variants missing consumed inputs")
      _with_core { call =>
        val goal = take(NativeReviewContext.goalC(call.core, _goal_work())).get
        val repository = take(NativeReviewContext.repositoryC(call.core, _repository_work))
        val variants = Vector(null, goal.copy(purpose = Purpose.RepositoryReview.identity),
          goal.copy(componentInstance = ""), goal.copy(target = null), goal.copy(completion = null),
          goal.copy(completion = goal.completion.copy(handle = null)),
          goal.copy(completion = goal.completion.copy(handle = Record.empty)),
          goal.copy(completion = goal.completion.copy(runId = "")),
          goal.copy(completion = goal.completion.copy(continuationId = "")),
          goal.copy(completion = goal.completion.copy(typeIdentity = RepositorySyncNativeMergeReview.resultCodec.typeIdentity)))
        When("passive preparation receives missing actual context, result reference, worker, issuer, time or wrong purpose/type")
        val missing = variants.map(Preparation.goalC(_, _goal_result, _reference, _execution, _declared.issuer, _now)) ++ Vector(
          Preparation.goalC(goal, _goal_result, null, _execution, _declared.issuer, _now),
          Preparation.goalC(goal, _goal_result, _reference.copy(identity = ""), _execution, _declared.issuer, _now),
          Preparation.goalC(goal, _goal_result, _reference.copy(revision = ""), _execution, _declared.issuer, _now),
          Preparation.goalC(goal, _goal_result, _reference, null, _declared.issuer, _now),
          Preparation.goalC(goal, _goal_result, _reference, _execution.copy(workerIdentity = None), _declared.issuer, _now),
          Preparation.goalC(goal, _goal_result, _reference, _execution.copy(workerIdentity = Some("")), _declared.issuer, _now),
          Preparation.goalC(goal, _goal_result, _reference, _execution, "", _now),
          Preparation.goalC(goal, _goal_result, _reference, _execution, _declared.issuer, null),
          Preparation.repositoryC(repository.copy(purpose = Purpose.GoalReview.identity), _repository_result, _reference,
            _execution, _declared.issuer, _now),
          Preparation.repositoryC(repository.copy(completion = repository.completion.copy(typeIdentity = GoalPhaseNativeWork.resultCodec.typeIdentity)),
            _repository_result, _reference, _execution, _declared.issuer, _now))
        val absentowner = NativeReviewContext.goalC(call.core.copy(component = None), _goal_work())
        Then("every missing or crossed consumed input fails without placeholders or manufactured success")
        missing.foreach(_.isSuccess shouldBe false)
        absentowner.isSuccess shouldBe false
        Consequence.unit
      }
    }
  }

  "E5 Independent applicability and managed reading" should afterWord(
    "in spec:native-host-review-preparation, example:E5, rules:R3,R4,R5, phase:6"
  ) {
    "refuse crossed actual bindings and require the existing managed current-call read permission" in {
      Given("Spec: docs/spec/native-host-review-preparation.md; Rules: R3,R4,R5; Example: E5; a prepared specification row and separately configured native Reader")
      var retained = Option.empty[(NativeExternalEvidence.Reader, ActionCall.Core, Json)]
      _with_core { call =>
        val work = _goal_work()
        val context = take(NativeReviewContext.goalC(call.core, work)).get
        val result = _submitted(work, GoalPhaseNativeWork.Result(_goal_result.outcome, _goal_result.summary, _goal_result.findings),
          GoalPhaseNativeWork.resultCodec.typeIdentity)
        val prepared = take(Preparation.goalC(context, _goal_result, _reference, _execution, _declared.issuer, _now.minusSeconds(60)))
        val evidence = _evidence(prepared)
        val expected = take(ThreeWorkflowExternalEvidencePorts.goalReviewBindingC(call.core, _goal_input, result))
        val foreignowner = expected.mapObject(_.add("componentInstance", Json.fromString("foreign-owner")))
        val foreigntarget = NativeHumanEvidenceFixture.alter(expected, Vector("attestation", "target", "candidate"))(
          _.mapObject(_.add("tree", Json.fromString("d" * 40))))
        val resultpath = Vector("attestation", "result")
        val variants = Vector(foreignowner, foreigntarget,
          NativeHumanEvidenceFixture.alter(expected, resultpath :+ "reference")(_.mapObject(_.add("identity", Json.fromString("foreign-result")))),
          NativeHumanEvidenceFixture.alter(expected, resultpath)(_.mapObject(_.add("worker", Json.fromString("foreign-reviewer")))),
          NativeHumanEvidenceFixture.alter(expected, resultpath)(_.mapObject(_.add("continuationId", Json.fromString("foreign-slot")))),
          NativeHumanEvidenceFixture.alter(expected, resultpath :+ "verdict")(_.mapObject(_.add("outcome", Json.fromString("decision-needed")))))
        val reader = take(NativeExternalEvidence.bindC(call.core.component.get.subsystem.get.configuration,
          GoalPhaseRuntimeParameters.Persistence(_configuration_, _collection_("instances"), _collection_("continuations"))))
        When("the existing admission compares foreign bindings and tries reading before the specification provisions any row")
        val refused = variants.map(_observe(evidence, Purpose.GoalReview, _))
        val absent = take(reader.loadC(call.core, Purpose.GoalReview, _key, expected))
        Then("foreign owner, target, reference, worker, verdict and slot fail; preparation alone created no storage input")
        refused.foreach(_.isSuccess shouldBe false)
        absent shouldBe None
        _observe(evidence, Purpose.GoalHuman, expected).isSuccess shouldBe false
        _observe(evidence, Purpose.StepClosure, expected).isSuccess shouldBe false
        Given("the specification explicitly provisions the prepared row using the existing managed Create permission")
        take(call.createC("host_evidence", prepared.recordIdentity, prepared.record))
        When("the independently configured Reader loads that physical row in its live managed call")
        val accepted = take(reader.loadC(call.core, Purpose.GoalReview, _key, expected))
        val contextfree = reader.loadC(null, Purpose.GoalReview, _key, expected)
        Then("only the granted current call reads the applicable row; no preparation return grants read access")
        accepted.map(_.evidence.binding) shouldBe Some(evidence.binding)
        contextfree.isSuccess shouldBe false
        retained = Some((reader, call.core, expected))
        Consequence.unit
      }
      When("the call and its managed runtime have ended but the passive prepared facts remain")
      val (reader, core, expected) = retained.get
      val ended = reader.loadC(core, Purpose.GoalReview, _key, expected)
      Then("a retained call without live managed read permission cannot read the row")
      ended.isSuccess shouldBe false
    }
  }

  "E6 Selected-field annotations" should afterWord(
    "in spec:native-host-review-preparation, example:E6, rules:R2,R4,R6, phase:6"
  ) {
    "discard unused metadata and grant annotations while refusing consumed wrong types" in {
      Given("Spec: docs/spec/native-host-review-preparation.md; Rules: R2,R4,R6; Example: E6; a selected issued Goal context with inert specification annotations")
      _with_core { call =>
        val context = take(NativeReviewContext.goalC(call.core, _goal_work())).get
        val annotated = context.copy(target = _record(_json(context.target).mapObject(
          _.add("commandGrant", Json.True).add("diff", Json.False).add("managementHash", Json.Null))),
          completion = context.completion.copy(handle = _record(_json(context.completion.handle).mapObject(_.add("grant", Json.True)))))
        val result = take(V.GoalPhaseWorkResult.createC(_record(Json.obj("outcome" -> Json.fromString("accept"),
          "summary" -> Json.fromString(_goal_result.summary), "findings" -> Json.arr(), "grant" -> Json.True))))
        When("public passive preparation and the existing v7 codec consume only selected current fields")
        val original = take(Preparation.goalC(context, _goal_result, _reference, _execution, _declared.issuer, _now))
        val changed = take(Preparation.goalC(annotated, result, _reference,
          _execution.copy(modelIdentity = Some("unused-model")), _declared.issuer, _now))
        val raw = original.record.getString(NativeExternalEvidence.storageField).get
        val payload = io.circe.parser.parse(raw.substring(NativeExternalEvidence.schema.length + 1)).toOption.get
        val decoded = take(NativeExternalEvidence.decodeStorageC(NativeExternalEvidence.schema + ":" + payload.mapObject(
          _.add("grant", Json.True).add("validUntil", Json.False)).noSpaces))
        val wrong = annotated.copy(target = _record(_json(context.target).mapObject(_.add("generation", Json.True))))
        val wrongtype = Preparation.goalC(wrong, result, _reference, _execution, _declared.issuer, _now)
        Then("annotations supply no authority, occurrence renewal or extra seal, while wrong consumed generation fails")
        changed.record.getString(NativeExternalEvidence.storageField) shouldBe original.record.getString(NativeExternalEvidence.storageField)
        decoded shouldBe _evidence(original)
        wrongtype.isSuccess shouldBe false
        decoded.binding.noSpaces should not include "grant"
        decoded.binding.noSpaces should not include "managementHash"
        decoded.binding.noSpaces should not include "validUntil"
        Consequence.unit
      }
    }
  }

  "E7 Normal command review naming" should afterWord(
    "in spec:native-host-review-preparation, example:E7, rules:R1,R2,R3,R5,R6, phase:6"
  ) {
    "retain each issued Goal Step and Phase target through the actual formatter and strict reader" in {
      _with_core { call =>
        Vector("ReviewStep", "ReviewPhase").foreach { kind =>
          Given("Spec: docs/spec/native-host-review-preparation.md; Rules: R1,R2,R3,R5,R6; Example: E7; specification-issued Goal " + kind)
          val input = _goal_input.copy(kind = kind)
          val work = _goal_work(input)
          val native = _submitted(work, GoalPhaseNativeWork.Result(_goal_result.outcome, _goal_result.summary, _goal_result.findings),
            GoalPhaseNativeWork.resultCodec.typeIdentity)
          val output = take(NativeWorkflowOperationOutput.goalWorkRequestC(work, call.core))
          When("the normal command JSON crosses the strict generated reader and passive preparation")
          val decoded = take(V.GoalPhaseAdvanceResult.createC(take(new RecordDecoder().json(
            NativeExternalEvidenceToolFixture.normalCommandJson(output)))))
          val context = decoded.work.get.review.get.reviewContext.get
          val prepared = take(_prepare(context))
          val canonical = take(_prepare(output.work.get.review.get.reviewContext.get))
          val expected = take(ThreeWorkflowExternalEvidencePorts.goalReviewBindingC(call.core, input, native))
          Then("canonical and normal wire inputs retain the same native owner candidate target and completion for " + kind)
          prepared.record.getString(NativeExternalEvidence.storageField) shouldBe canonical.record.getString(NativeExternalEvidence.storageField)
          prepared.recordIdentity shouldBe canonical.recordIdentity
          _observe(_evidence(prepared), Purpose.GoalReview, expected).isSuccess shouldBe true
          context.completion.runId shouldBe native.runId.value
          context.completion.continuationId shouldBe native.continuationId.value
        }
        Consequence.unit
      }
    }

    "retain the selected Repository parents tree successful checks and continuation through normal command JSON" in {
      Given("Spec: docs/spec/native-host-review-preparation.md; Rules: R1,R2,R3,R5,R6; Example: E7; a specification-issued Repository review with check exit zero")
      _with_core { call =>
        val work = _repository_work
        val native = _submitted(work, RepositorySyncNativeMergeReview.Result(_repository_result.decision,
          _repository_result.rationale, Vector.empty), RepositorySyncNativeMergeReview.resultCodec.typeIdentity)
        val output = take(NativeWorkflowOperationOutput.repositoryReviewRequestC(work, call.core))
        When("the actual normal command formatter and strict family reader supply passive preparation")
        val decoded = take(V.RepositorySyncAdvanceResult.createC(take(new RecordDecoder().json(
          NativeExternalEvidenceToolFixture.normalCommandJson(output)))))
        val context = decoded.review.get.reviewContext.get
        val prepared = take(_prepare(context))
        val canonical = take(_prepare(output.review.get.reviewContext.get))
        val expected = take(ThreeWorkflowExternalEvidencePorts.repositoryReviewBindingC(call.core, _repository_input, native))
        Then("the existing v7 evidence applies to the same native target and completion with the zero exit receipt")
        decoded.review.get.checks.head.exitCode shouldBe 0
        prepared.record.getString(NativeExternalEvidence.storageField) shouldBe canonical.record.getString(NativeExternalEvidence.storageField)
        prepared.recordIdentity shouldBe canonical.recordIdentity
        _observe(_evidence(prepared), Purpose.RepositoryReview, expected).isSuccess shouldBe true
        context.completion.runId shouldBe native.runId.value
        context.completion.continuationId shouldBe native.continuationId.value
        Consequence.unit
      }
    }
  }

  "E8 Consumed declaration and wire aliases" should afterWord(
    "in spec:native-host-review-preparation, example:E8, rules:R1,R2,R4,R6, phase:6"
  ) {
    "accept agreement and reject conflict missing null and wrong types for every selected name pair" in {
      _with_core { call =>
        val goal = take(NativeReviewContext.goalC(call.core, _goal_work())).get
        val repository = take(NativeReviewContext.repositoryC(call.core, _repository_work))
        val groups = Vector(
          ("Goal handle", goal, _handle_names, Vector.empty[String]),
          ("Repository handle", repository, _handle_names, Vector.empty[String]),
          ("Goal target", goal, _goal_names, Vector.empty[String]),
          ("Repository target", repository, _repository_names, Vector.empty[String]),
          ("Repository check", repository, Vector("exitCode" -> "exit_code"), Vector("checks", "0")))
        groups.foreach { (label, context, names, path) =>
          val ishandle = label.endsWith("handle")
          val original = _json(if (ishandle) context.completion.handle else context.target)
          def _selected_(value: Json): Json = if (path.isEmpty) value else value.hcursor.downField("checks").downArray.focus.get
          def _replace_(value: Json): V.WorkflowReviewContext = {
            val updated = if (path.isEmpty) value else original.mapObject(_.add("checks", Json.fromValues(
              original.hcursor.downField("checks").focus.get.asArray.get.updated(0, value))))
            if (ishandle) context.copy(completion = context.completion.copy(handle = _record(updated)))
            else context.copy(target = _record(updated))
          }
          names.foreach { (declaration, wire) =>
            Given("Spec: docs/spec/native-host-review-preparation.md; Rules: R1,R2,R4,R6; Example: E8; " + label + " pair " + declaration + "/" + wire)
            val fields = _selected_(original).asObject.get
            val value = fields(declaration).get
            val matching = _replace_(Json.fromJsonObject(fields.add(wire, value)))
            val snake = _replace_(Json.fromJsonObject(fields.remove(declaration).add(wire, value)))
            val different = value.asString.map(s => Json.fromString(s + "-conflict"))
              .orElse(value.asArray.map(a => Json.fromValues(a :+ Json.fromString("conflict"))))
              .getOrElse(Json.fromInt(1))
            val invalid = Vector(
              "contradictory valid values" -> fields.add(wire, different),
              "contradictory wire type" -> fields.add(wire, Json.True),
              "contradictory declaration type" -> fields.add(declaration, Json.True).add(wire, value),
              "missing both spellings" -> fields.remove(declaration).remove(wire),
              "null declaration" -> fields.add(declaration, Json.Null),
              "wrong declaration type" -> fields.add(declaration, Json.True),
              "null wire" -> fields.remove(declaration).add(wire, Json.Null),
              "wrong wire type" -> fields.remove(declaration).add(wire, Json.True),
              "matching null spellings" -> fields.add(declaration, Json.Null).add(wire, Json.Null),
              "matching wrong types" -> fields.add(declaration, Json.True).add(wire, Json.True))
            val canonical = take(_prepare(context))
            Vector("declaration-only spelling" -> context, "matching double spelling" -> matching,
              "snake-only spelling" -> snake).foreach { (condition, alternative) =>
              Given("Spec: docs/spec/native-host-review-preparation.md; Rules: R1,R2,R4,R6; Example: E8; " + label + " " + declaration + " with " + condition)
              When("passive preparation consumes that agreeing selected name alternative")
              val prepared = take(_prepare(alternative))
              Then("the " + condition + " preserves the selected native binding for " + declaration)
              prepared.recordIdentity shouldBe canonical.recordIdentity
              _evidence(prepared).binding shouldBe _evidence(canonical).binding
              _observe(_evidence(prepared), if (context.purpose == Purpose.GoalReview.identity) Purpose.GoalReview else Purpose.RepositoryReview,
                _evidence(canonical).binding).isSuccess shouldBe true
            }
            invalid.foreach { (condition, alternative) =>
              Given("Spec: docs/spec/native-host-review-preparation.md; Rules: R1,R2,R4,R6; Example: E8; " + label + " " + declaration + " with " + condition)
              When("passive preparation receives that consumed name alternative")
              val refused = _prepare(_replace_(Json.fromJsonObject(alternative)))
              Then("the " + condition + " alternative fails without a preferred spelling or manufactured value")
              refused.isSuccess shouldBe false
            }
          }
        }
        Consequence.unit
      }
    }

    "refuse malformed selected objects checks and integer fields without interpreting annotations as authority" in {
      _with_core { call =>
        val goal = take(NativeReviewContext.goalC(call.core, _goal_work())).get
        val repository = take(NativeReviewContext.repositoryC(call.core, _repository_work))
        val target = _json(repository.target)
        val fractionalexit = target.hcursor.downField("checks").downArray.focus.get
          .mapObject(_.add("exitCode", Json.fromBigDecimal(BigDecimal("0.5"))))
        val variants = Vector(
          "fractional generation" -> goal.copy(target = _record(_json(goal.target).mapObject(
            _.add("generation", Json.fromBigDecimal(BigDecimal("1.5")))))),
          "wrong checks type" -> repository.copy(target = _record(target.mapObject(_.add("checks", Json.True)))),
          "null checks" -> repository.copy(target = _record(target.mapObject(_.add("checks", Json.Null)))),
          "missing checks" -> repository.copy(target = _record(target.mapObject(_.remove("checks")))),
          "nonobject check" -> repository.copy(target = _record(target.mapObject(_.add("checks", Json.arr(Json.True))))),
          "fractional exit" -> repository.copy(target = _record(target.mapObject(_.add("checks", Json.arr(fractionalexit))))))
        variants.foreach { (condition, context) =>
          Given("Spec: docs/spec/native-host-review-preparation.md; Rules: R1,R2,R4,R6; Example: E8; selected fixture target with " + condition)
          When("passive preparation consumes that malformed structure or integer field")
          val failure = _prepare(context)
          Then("the " + condition + " fails under the existing native target type semantics")
          failure.isSuccess shouldBe false
        }
        Given("Spec: docs/spec/native-host-review-preparation.md; Rules: R2,R6; Example: E8; a null element in the selected checks array")
        When("ordinary canonical Record decoding receives that array before preparation")
        val nullcheck = new RecordDecoder().json(target.mapObject(_.add("checks", Json.arr(Json.Null))).noSpaces)
        Then("canonical decoding refuses the null element before any review context can be prepared")
        nullcheck.isSuccess shouldBe false
        Consequence.unit
      }
    }
  }

  "E9 Typed SBT review evidence" should afterWord(
    "in spec:native-host-review-preparation, example:E9, rules:R1,R2,R4,R5,R6, phase:6"
  ) {
    Vector("ReviewStep", "ReviewPhase", "Repository").foreach { label =>
      s"retain explicit selected TestOnly facts through normal command intake storage and managed observation for $label" in {
        Given("Spec: docs/spec/native-host-review-preparation.md; Rules: R1,R2,R4,R5,R6; Example: E9; a specification-issued typed " + label + " WorkOrder")
        _with_core { call =>
          When("the actual normal formatter and generated reader feed passive preparation and the v7 storage decoder")
          val (context, canonical, expected, purpose) = if (label == "Repository") {
            val work = _typed_repository_work
            val native = _submitted(work, RepositorySyncNativeMergeReview.Result("accept", "Specification review", Vector.empty),
              RepositorySyncNativeMergeReview.resultCodec.typeIdentity)
            val output = take(NativeWorkflowOperationOutput.repositoryReviewRequestC(work, call.core))
            val decoded = take(V.RepositorySyncAdvanceResult.createC(take(new RecordDecoder().json(
              NativeExternalEvidenceToolFixture.normalCommandJson(output)))))
            (decoded.review.get.reviewContext.get, output.review.get.reviewContext.get,
              take(ThreeWorkflowExternalEvidencePorts.repositoryReviewBindingC(call.core, _typed_repository_input, native)), Purpose.RepositoryReview)
          } else {
            val input = _typed_goal_input.copy(kind = label)
            val work = _goal_work(input)
            val native = _submitted(work, GoalPhaseNativeWork.Result("accept", "Specification review", Vector.empty),
              GoalPhaseNativeWork.resultCodec.typeIdentity)
            val output = take(NativeWorkflowOperationOutput.goalWorkRequestC(work, call.core))
            val decoded = take(V.GoalPhaseAdvanceResult.createC(take(new RecordDecoder().json(
              NativeExternalEvidenceToolFixture.normalCommandJson(output)))))
            (decoded.work.get.review.get.reviewContext.get, output.work.get.review.get.reviewContext.get,
              take(ThreeWorkflowExternalEvidencePorts.goalReviewBindingC(call.core, input, native)), Purpose.GoalReview)
          }
          val prepared = take(_prepare(context))
          val evidence = _evidence(prepared)
          val raw = prepared.record.getString(NativeExternalEvidence.storageField).get
          val checkskey = if (purpose == Purpose.GoalReview) "testChecks" else "checks"
          val stored = io.circe.parser.parse(raw.substring(NativeExternalEvidence.schema.length + 1)).toOption.get
            .hcursor.downField("binding").downField("attestation").downField("target").downField(checkskey).downArray.downField("sbt")
          Then("raw canonical storage retains each explicit fact and the original owner completion and selected meaning")
          stored.get[String]("kind") shouldBe Right("test-only")
          stored.get[String]("outcome") shouldBe Right("succeeded")
          stored.downField("selection").get[Vector[String]]("testClasses") shouldBe Right(Vector("example.FirstSpec", "example.SecondSpec"))
          stored.downField("selection").get[Int]("completedSuites") shouldBe Right(2)
          stored.downField("selection").get[Long]("testsRun") shouldBe Right(25L)
          evidence.binding shouldBe _evidence(take(_prepare(canonical))).binding
          _observe(evidence, purpose, expected).isSuccess shouldBe true
          val reader = take(NativeExternalEvidence.bindC(call.core.component.get.subsystem.get.configuration,
            GoalPhaseRuntimeParameters.Persistence(_configuration_, _collection_("instances"), _collection_("continuations"))))
          Given("the specification explicitly provisions that returned row using its existing managed Create permission")
          take(call.createC("host_evidence", prepared.recordIdentity, prepared.record))
          When("the live managed Reader loads the row against original and changed valid selected test counts")
          val accepted = take(reader.loadC(call.core, purpose, _key, expected))
          val changed = NativeHumanEvidenceFixture.alter(expected,
            Vector("attestation", "target", checkskey))(checks => Json.fromValues(checks.asArray.get.map(check =>
              NativeHumanEvidenceFixture.alter(check, Vector("sbt", "selection"))(
          _.mapObject(_.add("testsRun", Json.fromLong(26L)))))))
          Then("the actual native binding is observed and a different selected count cannot reuse the stored review")
          accepted.map(_.evidence.binding) shouldBe Some(evidence.binding)
          reader.loadC(call.core, purpose, _key, changed).isSuccess shouldBe false
          Consequence.unit
        }
      }
    }

    "normalize only the fixed typed check aliases and reject malformed present facts and contradictory selections" in {
      Given("Spec: docs/spec/native-host-review-preparation.md; Rules: R2,R5,R6; Example: E9; specification-issued typed Goal and Repository review contexts")
      _with_core { call =>
        val contexts = Vector(take(NativeReviewContext.goalC(call.core, _goal_work(_typed_goal_input))).get,
          take(NativeReviewContext.repositoryC(call.core, _typed_repository_work)))
        val selectionnames = Vector("testClasses" -> "test_classes", "completedSuites" -> "completed_suites", "testsRun" -> "tests_run")
        def _supplied_record_(value: Json): Record = {
          val record = _record(value)
          _json(record) shouldBe expectedRecordJson(value)
          record
        }
        contexts.foreach { context =>
          val goal = context.purpose == Purpose.GoalReview.identity
          val key = if (goal) "testChecks" else "checks"
          val target = _json(context.target)
          val check = target.hcursor.downField(key).downArray.focus.get
          val facts = check.hcursor.downField("sbt").focus.get
          val selection = facts.hcursor.downField("selection").focus.get
          def _selected_(check: Json): V.WorkflowReviewContext = context.copy(target = _record(target.mapObject(_.add(key, Json.arr(check)))))
          def _selected_present_(check: Json): V.WorkflowReviewContext = context.copy(target = _supplied_record_(target.mapObject(_.add(key, Json.arr(check)))))
          def _with_selection_(value: Json): Json = check.mapObject(_.add("sbt", facts.mapObject(_.add("selection", value))))
          val canonical = _evidence(take(_prepare(context)))
          selectionnames.foreach { (declaration, wire) =>
            val fields = selection.asObject.get
            val value = fields(declaration).get
            val alternatives = Vector(fields, fields.remove(declaration).add(wire, value), fields.add(wire, value),
              fields.add(wire, Json.Null), fields.remove(declaration).add(declaration, Json.Null).add(wire, value))
            alternatives.foreach { alternative =>
              Given("the typed " + key + " selection with valid " + declaration + " aliases after optional null alias omission")
              When("passive preparation canonicalizes that bounded nested selection")
              val prepared = take(_prepare(_selected_present_(_with_selection_(Json.fromJsonObject(alternative)))))
              Then("the selected binding preserves the explicit typed facts")
              _evidence(prepared).binding shouldBe canonical.binding
            }
            val different = if (declaration == "testClasses") Json.arr(Json.fromString("example.ThirdSpec"), Json.fromString("example.FourthSpec"))
              else Json.fromInt(if (declaration == "testsRun") 26 else 3)
            val invalid = Vector(fields.add(wire, different), fields.remove(declaration), fields.add(declaration, Json.Null),
              fields.add(declaration, Json.True), fields.remove(declaration).add(wire, Json.Null),
              fields.remove(declaration).add(wire, Json.True), fields.add(declaration, Json.True).add(wire, value),
              fields.add(wire, Json.True), fields.add(declaration, Json.Null).add(wire, Json.Null))
            invalid.foreach { alternative =>
              Given("a missing malformed or contradictory consumed nested " + declaration + " alias")
              When("passive preparation consumes both supplied spellings")
              val refused = _prepare(_selected_present_(_with_selection_(Json.fromJsonObject(alternative))))
              Then("missing required fields and surviving malformed or contradictory aliases fail")
              refused.isSuccess shouldBe false
            }
          }
          val invalidselections = Vector(
            selection.mapObject(_.add("completedSuites", Json.fromBigDecimal(BigDecimal("2.5")))),
            selection.mapObject(_.add("completedSuites", Json.fromLong(Int.MaxValue.toLong + 1))),
            selection.mapObject(_.add("testsRun", Json.fromBigDecimal(BigDecimal("25.5")))),
            selection.mapObject(_.add("testsRun", Json.fromBigInt(BigInt(Long.MaxValue) + 1))),
            selection.mapObject(_.add("testsRun", Json.fromInt(0))),
            selection.mapObject(_.add("completedSuites", Json.fromInt(1))),
            selection.mapObject(_.add("testClasses", Json.arr(Json.fromString("invalid")))), Json.Null, Json.True)
          val invalidfacts = Vector(Json.True, Json.obj(), facts.mapObject(_.add("kind", Json.fromString("unknown"))),
            facts.mapObject(_.add("outcome", Json.fromString("unknown")))) ++
            invalidselections.map(value => facts.mapObject(_.add("selection", value)))
          invalidfacts.foreach { malformed =>
            Given("present malformed sbt or selected classes suite count or test count in " + key)
            When("passive preparation delegates normalized facts to the existing Facts decoder")
            val refused = _prepare(_selected_present_(check.mapObject(_.add("sbt", malformed))))
            Then("invalid presence is refused without historical defaults or relaxed integer rules")
            refused.isSuccess shouldBe false
          }
          Given("historical absent or Core-omitted null sbt and an explicit empty check array in " + key)
          When("passive preparation encodes those absence shapes")
          val historical = _evidence(take(_prepare(_selected_(check.mapObject(_.remove("sbt"))))))
          val nullfacts = _evidence(take(_prepare(_selected_present_(check.mapObject(_.add("sbt", Json.Null))))))
          val empty = _evidence(take(_prepare(context.copy(target = _record(target.mapObject(_.add(key, Json.arr())))))))
          Then("no typed facts are manufactured and an explicitly empty array remains explicit")
          historical.binding.hcursor.downField("attestation").downField("target").downField(key).downArray.downField("sbt").focus shouldBe None
          nullfacts.binding shouldBe historical.binding
          nullfacts.binding should not be canonical.binding
          empty.binding.hcursor.downField("attestation").downField("target").get[Vector[Json]](key) shouldBe Right(Vector.empty)
          Given("Spec: docs/spec/native-host-review-preparation.md; Rules: R2,R6; Example: E9; a null typed class or check array element in " + key)
          val nullelements = Vector(target.mapObject(_.add(key, Json.arr(Json.Null))),
            target.mapObject(_.add(key, Json.arr(_with_selection_(selection.mapObject(_.add("testClasses", Json.arr(Json.Null))))))))
          When("ordinary canonical Record decoding sees those array elements before review preparation")
          val decodefailures = nullelements.map(value => new RecordDecoder().json(value.noSpaces))
          Then("each null array element refuses canonical decoding rather than disappearing or reaching preparation")
          decodefailures.foreach(_.isSuccess shouldBe false)
          if (goal) {
            val snakeselection = selectionnames.foldLeft(selection.asObject.get) { case (fields, (declaration, wire)) =>
              fields.remove(declaration).add(wire, fields(declaration).get)
            }
            val snakecheck = _with_selection_(Json.fromJsonObject(snakeselection)).mapObject(
              fields => fields.remove("exitCode").add("exit_code", fields("exitCode").get).add("annotation", Json.True))
            val snake = Json.arr(snakecheck)
            Vector(target.asObject.get.remove("testChecks").add("test_checks", snake),
              target.asObject.get.add("test_checks", snake)).foreach { fields =>
              Given("Goal test_checks uses snake nested fields while testChecks is absent or agrees semantically")
              When("both structured alias values are independently projected")
              val prepared = _evidence(take(_prepare(context.copy(target = _record(Json.fromJsonObject(fields))))))
              Then("different spellings and inert check annotations retain one canonical selected meaning")
              prepared.binding shouldBe canonical.binding
            }
            val malformedarrays = Vector(Json.True, Json.arr(Json.True), Json.arr(),
              Json.arr(_with_selection_(selection.mapObject(_.add("testsRun", Json.fromInt(26))))))
            malformedarrays.foreach { value =>
              Given("testChecks and test_checks have malformed or contradictory selected arrays")
              When("passive preparation consumes the structured Goal aliases")
              val refused = _prepare(context.copy(target = _supplied_record_(target.mapObject(_.add("test_checks", value)))))
              Then("the alternate spelling cannot conceal malformed presence or different selected checks")
              refused.isSuccess shouldBe false
            }
            Given("the historical Goal target omits testChecks or supplies an optional null attribute")
            When("passive preparation encodes the historical target")
            val absent = _evidence(take(_prepare(context.copy(target = _record(target.mapObject(_.remove("testChecks")))))))
            val nullchecks = _evidence(take(_prepare(context.copy(target = _supplied_record_(
              target.mapObject(_.add("testChecks", Json.Null)))))))
            val nullalias = _evidence(take(_prepare(context.copy(target = _supplied_record_(
              target.mapObject(_.add("test_checks", Json.Null)))))))
            Then("the absent historical field remains omitted")
            absent.binding.hcursor.downField("attestation").downField("target").downField("testChecks").focus shouldBe None
            nullchecks.binding shouldBe absent.binding
            nullchecks.binding should not be canonical.binding
            nullalias.binding shouldBe canonical.binding
            Given("Spec: docs/spec/native-host-review-preparation.md; Rules: R2,R6; Example: E9; a null check element in the alternate Goal array")
            When("ordinary canonical Record decoding receives test_checks before preparation")
            val nullaliaselement = new RecordDecoder().json(target.mapObject(_.add("test_checks", Json.arr(Json.Null))).noSpaces)
            Then("the null array element fails canonical decoding even beside a valid canonical array")
            nullaliaselement.isSuccess shouldBe false
          }
        }
        Consequence.unit
      }
    }
  }
  "E10 Ordinary Goal host admission" should afterWord(
    "in spec:native-host-review-preparation, example:E10, rules:R3,R4,R5,R7, phase:6.1"
  ) {
    Vector("ReviewStep", "ReviewPhase").foreach { kind =>
      s"admit only the matching independently provisioned $kind result without CBD" in {
        Given("Spec: docs/spec/native-host-review-preparation.md; Rules: R3,R4,R5,R7; Example: E10; a specification-issued " + kind + " in the normal SmWorkflow-only managed runtime without CBD or scoped-quality metadata")
        _with_core { call =>
          val input = _goal_input.copy(kind = kind)
          val work = _goal_work(input)
          val submitted = _submitted(work,
            GoalPhaseNativeWork.Result(_goal_result.outcome, _goal_result.summary, _goal_result.findings),
            GoalPhaseNativeWork.resultCodec.typeIdentity)
          val context = take(NativeReviewContext.goalC(call.core, work)).get
          val prepared = take(Preparation.goalC(context, _goal_result, _reference, _execution,
            _declared.issuer, _now.minusSeconds(60)))
          val reader = take(NativeExternalEvidence.bindC(call.core.component.get.subsystem.get.configuration,
            GoalPhaseRuntimeParameters.Persistence(_configuration_, _collection_("instances"), _collection_("continuations"))))
          val port = new ThreeWorkflowExternalEvidencePorts(reader).goalReview
          When("the actual Goal review port admits the supplied result before any host row is provisioned")
          val absent = port.admitC(call.core, input, submitted)
          Then("passive preparation alone supplies no independent host admission")
          absent.isSuccess shouldBe false

          Given("the specification explicitly provisions the prepared host row using its existing managed Create permission")
          take(call.createC("host_evidence", prepared.recordIdentity, prepared.record))
          When("the actual Goal review port consumes the matching result through its configured managed Reader")
          val accepted = port.admitC(call.core, input, submitted)
          Then("the matching result is host-admitted without a CBD component or an additional AI review")
          accepted.isSuccess shouldBe true

          Given("the same provisioned row and separately altered candidate tree, result reference, worker and verdict")
          val crossedcandidate = input.copy(candidate = input.candidate.map(_.copy(tree = "d" * 40)))
          val crossedreference = submitted.copy(resultReference = submitted.resultReference.copy(identity = "foreign-result"))
          val crossedworker = submitted.copy(evidence = submitted.evidence.copy(workerIdentity = Some("foreign-reviewer")))
          val crossedverdict = submitted.copy(result = submitted.result.copy(value = submitted.result.value.copy(outcome = "decision-needed")))
          When("each altered fact is submitted to that same actual Goal review port")
          val refused = Vector(port.admitC(call.core, crossedcandidate, submitted),
            port.admitC(call.core, input, crossedreference), port.admitC(call.core, input, crossedworker),
            port.admitC(call.core, input, crossedverdict))
          Then("none can reuse the matching result's independently provisioned host evidence")
          refused.foreach(_.isSuccess shouldBe false)
          Consequence.unit
        }
      }
    }
  }
}
