package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
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
import org.simplemodeling.textus.smworkflow.host.NativeHostReviewPreparation as Preparation
import org.simplemodeling.textus.smworkflow.value as V
import NativeHostEvidence.{Purpose, Evidence}
import SplitPhaseExplicitPartitionFixture.take

/** Specification-issued WorkOrders and rows exercise passive preparation.
  * They are not genuine independent review, Human consent or effect authority.
  * @since   Oct. 14, 2026
  * @version Oct. 16, 2026
  * @author  ASAMI, Tomoharu
  */
final class NativeHostReviewPreparationSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  private val _now = Instant.parse("2026-10-07T00:00:00Z")
  private val _reference = V.WorkflowArtifactReference("specification-review", "1")
  private val _execution = V.WorkflowExecutionEvidence(Vector(_reference), Some("specification-reviewer"), None, None)
  private val _goal_result = V.GoalPhaseWorkResult("accept", "Specification-owned review response", Vector.empty, None)
  private val _repository_result = V.RepositorySyncNativeMergeReviewResult("accept", "Specification-owned merge review", Vector.empty)
  private val _receipt = RepositorySyncNativeMergeValidation.Receipt("focused", "exited", Some(0), "diagnostic output", "")
  private val _goal_input = NativeHostHumanEvidenceFixture.work.copy(sourceRevision = "7", kind = "ReviewStep",
    generation = 1, candidate = Some(NativeHostHumanEvidenceFixture.snapshot), checksReceipt = Some("selected-checks"),
    review = Some(GoalPhaseNativeReview.Context(Vector("one.scala"), "specification diff", Vector(_receipt))))
  private val _repository_input = RepositorySyncNativeMergeReview.Request("instance", "7", "a" * 40, "c" * 40,
    "b" * 40, Vector("one.scala"), "local diagnostic diff", "remote diagnostic diff", "required",
    "Selected specification check", Vector("focused"), Vector(_receipt))
  private val _typed_receipt = _receipt.copy(sbt = Some(WorkflowSbtValidation.Facts(
    WorkflowSbtValidation.Kind.TestOnly, WorkflowSbtValidation.Outcome.Succeeded,
    Some(WorkflowSbtValidation.Selection(Vector("example.FirstSpec", "example.SecondSpec"), 2, 25L)), None)))
  private val _typed_goal_input = _goal_input.copy(review = _goal_input.review.map(_.copy(checks = Vector(_typed_receipt))))
  private val _typed_repository_input = _repository_input.copy(checks = Vector(_typed_receipt))
  private val _declared = NativeHostEvidence.Declaration("workflow", DataStore.CollectionId.EntityStore(
    EntityCollectionId("test", "host", "review_evidence")), "normal-development-host")
  private val _key = ThreeWorkflowHostEvidencePorts.key(_reference.identity, _reference.revision)

  private def _with_core(body: Setup => Consequence[Unit]): Unit = {
    val parent = Files.createDirectories(Path.of("target/sm-workflow/work/review-preparation").toAbsolutePath)
    val root = Files.createTempDirectory(parent, "specification-")
    _with_runtime_(root, "UpToDate") { (_, _, _, prepare) => prepare(body) }
  }

  // Explicit specification issuance, not a production issuer or stored history.
  private def _work[A](workflow: String, input: A, inputType: String, resultType: String,
    completion: StateMachineOperationIdentity): WorkflowInteraction[A, Nothing] = {
    val handle = WorkflowHandle(SmWorkflowComponent.componentId, WorkflowDefinitionIdentity(workflow),
      WorkflowDefinitionRevision("specification-executable"), InstanceIdentity("instance"))
    val request = ContinuationRequest(StateMachineRunIdentity("specification-run"), ContinuationIdentity("specification-review-slot"),
      StateMachineRevision("8"), StateMachineRequiredOperationIdentity("specification-review-capability"),
      StateMachineOperationIdentity("SpecificationReview", "review"), Some(TypedValue(inputType, input)),
      Some(StateMachineResultTypeReference(resultType)), ContextBundle("Specification input", Vector.empty, Vector.empty,
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
  private def _submitted[W, A](work: WorkflowInteraction[W, Nothing], value: A, typeIdentity: String): ContinuationResult[A] = {
    val request = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[W]].request
    val reference = ContextReference(_reference.identity, _reference.revision)
    ContinuationResult(work.handle, request.runId, request.continuationId, request.expectedRevision, request.context.snapshot,
      TypedValue(typeIdentity, value), reference, Vector.empty, ExecutionEvidence(Vector(reference), _execution.workerIdentity))
  }
  private def _wire(record: Record): Record = take(new RecordDecoder().json(take(record.toJsonStringC)))
  private def _json(record: Record): Json = io.circe.parser.parse(take(record.toJsonStringC)).toOption.get
  private def _record(json: Json): Record = take(new RecordDecoder().json(json.noSpaces))
  private def _evidence(prepared: Preparation.Prepared): Evidence =
    take(NativeHostEvidence.decodeStorageC(prepared.record.getString(NativeHostEvidence.storageField).get))
  private def _observe(evidence: Evidence, purpose: Purpose, actual: Json): Consequence[NativeHostEvidence.Observed] =
    NativeHostEvidence.observeC(evidence, _declared, purpose, _key, actual, _now)
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
  private def _prepare(context: V.WorkflowHostReviewContext): Consequence[Preparation.Prepared] =
    if (context.purpose == Purpose.GoalReview.identity)
      Preparation.goalC(context, _goal_result, _reference, _execution, _declared.issuer, _now)
    else Preparation.repositoryC(context, _repository_result, _reference, _execution, _declared.issuer, _now)

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
        val publicresult = V.GoalPhaseWorkResult(result.outcome, result.summary, result.findings, boundary.publicReport(result.acceptance))
        val work = _goal_work(input)
        val native = _submitted(work, result, GoalPhaseNativeWork.resultCodec.typeIdentity)
        When("the normal command formatter and generated readers carry the issued view and result into passive preparation")
        val output = take(NativeWorkflowOperationOutput.goalWorkRequestC(work, call.core))
        val decoded = take(V.GoalPhaseAdvanceResult.createC(take(new RecordDecoder().json(
          NativeHostEvidenceToolFixture.normalCommandJson(output)))))
        val context = decoded.work.get.review.get.hostContext.get
        val supplied = take(V.GoalPhaseWorkResult.createC(_wire(publicresult.toRecord())))
        val prepared = take(Preparation.goalC(context, supplied, _reference, _execution, _declared.issuer, _now))
        val evidence = _evidence(prepared)
        val expected = take(ThreeWorkflowHostEvidencePorts.goalReviewBindingC(call.core, input, native))
        Then("normal host observation retains the full report, effective boundary and typed SBT selection")
        _observe(evidence, Purpose.GoalReview, expected).isSuccess shouldBe true
        _verdict(evidence).hcursor.downField("acceptance").focus shouldBe Some(report.asJson)
        val target = _json(context.target)
        target.hcursor.downField("test_checks").downArray.downField("sbt").focus
          .orElse(target.hcursor.downField("testChecks").downArray.downField("sbt").focus) should not be empty
        val effective = plan.effectiveAcceptance.get.asJson
        def contextWith(value: Json) = context.copy(target = _record(value))
        val canonical = target.mapObject(_.remove("effective_acceptance").add("effectiveAcceptance", effective))
        val alternatives = Vector(canonical, canonical.mapObject(_.remove("effectiveAcceptance").add("effective_acceptance", effective)),
          canonical.mapObject(_.add("effective_acceptance", effective)))
        alternatives.foreach { target =>
          Given("declaration-only, wire-only or agreeing effectiveAcceptance/effective_acceptance spellings")
          When("passive preparation consumes the fixed aliases")
          val actual = take(Preparation.goalC(contextWith(target), supplied, _reference, _execution, _declared.issuer, _now))
          Then("the exact selected quality and typed SBT binding remains the same")
          _evidence(actual).binding shouldBe evidence.binding
        }
        val conflicting = effective.mapObject(_.add("conditions", effective.hcursor.downField("conditions").focus.get.mapArray(
          _.map(_.mapObject(_.add("required", Json.True))))))
        Vector(conflicting, Json.True).foreach { different =>
          Given("contradictory effectiveAcceptance and effective_acceptance values")
          When("the fixed alias intake receives both spellings")
          val failed = Preparation.goalC(contextWith(canonical.mapObject(_.add("effective_acceptance", different))),
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
        val context = decoded.work.get.review.get.hostContext.get
        val prepared = take(Preparation.goalC(context, _goal_result, _reference, _execution, _declared.issuer, _now.minusSeconds(60)))
        val expected = take(ThreeWorkflowHostEvidencePorts.goalReviewBindingC(call.core, _goal_input, native))
        val evidence = _evidence(prepared)
        Then("the existing v7 codec admits only that actual owner, selected target and completion, without native revision or snapshot copies")
        context.componentInstance shouldBe call.core.component.get.core.instanceId.canonicalKey
        context.completion.runId shouldBe native.runId.value
        context.completion.continuationId shouldBe native.continuationId.value
        _observe(evidence, Purpose.GoalReview, expected).isSuccess shouldBe true
        prepared.recordIdentity shouldBe NativeHostEvidence.recordIdentity(Purpose.GoalReview, _key)
        evidence.binding.noSpaces should not include "expectedRevision"
        evidence.binding.noSpaces should not include "contextSnapshot"
        Given("the same explicit specification input assigned to implementation or repair, with no review")
        val nonreview = Vector("ImplementStep", "RepairStep").map(kind => _goal_work(_goal_input.copy(kind = kind, review = None)))
        When("the owning extraction projects those nonreview work assignments")
        val contexts = nonreview.map(NativeHostReviewContext.goalC(call.core, _))
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
        val context = decoded.review.get.hostContext.get
        val prepared = take(Preparation.repositoryC(context, _repository_result, _reference, _execution,
          _declared.issuer, _now.minusSeconds(60)))
        val expected = take(ThreeWorkflowHostEvidencePorts.repositoryReviewBindingC(call.core, _repository_input, native))
        val evidence = _evidence(prepared)
        Then("selected native parents, tree, check exit and completion remain applicable to the actual owner")
        decoded.review.get.checks.head.exitCode shouldBe 0
        context.componentInstance shouldBe call.core.component.get.core.instanceId.canonicalKey
        context.completion.typeIdentity shouldBe native.result.typeIdentity
        _observe(evidence, Purpose.RepositoryReview, expected).isSuccess shouldBe true
        evidence.binding.noSpaces should not include "diagnostic"
        prepared.recordIdentity shouldBe NativeHostEvidence.recordIdentity(Purpose.RepositoryReview, _key)
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
        val goal = take(NativeHostReviewContext.goalC(call.core, _goal_work())).get
        val repository = take(NativeHostReviewContext.repositoryC(call.core, _repository_work))
        val repair = V.GoalPhaseWorkResult("repair", "Fix the assigned case", Vector("Missing assigned case"), None)
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
        val goal = take(NativeHostReviewContext.goalC(call.core, _goal_work())).get
        val repository = take(NativeHostReviewContext.repositoryC(call.core, _repository_work))
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
        val absentowner = NativeHostReviewContext.goalC(call.core.copy(component = None), _goal_work())
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
      var retained = Option.empty[(NativeHostEvidence.Reader, ActionCall.Core, Json)]
      _with_core { call =>
        val work = _goal_work()
        val context = take(NativeHostReviewContext.goalC(call.core, work)).get
        val result = _submitted(work, GoalPhaseNativeWork.Result(_goal_result.outcome, _goal_result.summary, _goal_result.findings),
          GoalPhaseNativeWork.resultCodec.typeIdentity)
        val prepared = take(Preparation.goalC(context, _goal_result, _reference, _execution, _declared.issuer, _now.minusSeconds(60)))
        val evidence = _evidence(prepared)
        val expected = take(ThreeWorkflowHostEvidencePorts.goalReviewBindingC(call.core, _goal_input, result))
        val foreignowner = expected.mapObject(_.add("componentInstance", Json.fromString("foreign-owner")))
        val foreigntarget = NativeHostHumanEvidenceFixture.alter(expected, Vector("attestation", "target", "candidate"))(
          _.mapObject(_.add("tree", Json.fromString("d" * 40))))
        val resultpath = Vector("attestation", "result")
        val variants = Vector(foreignowner, foreigntarget,
          NativeHostHumanEvidenceFixture.alter(expected, resultpath :+ "reference")(_.mapObject(_.add("identity", Json.fromString("foreign-result")))),
          NativeHostHumanEvidenceFixture.alter(expected, resultpath)(_.mapObject(_.add("worker", Json.fromString("foreign-reviewer")))),
          NativeHostHumanEvidenceFixture.alter(expected, resultpath)(_.mapObject(_.add("continuationId", Json.fromString("foreign-slot")))),
          NativeHostHumanEvidenceFixture.alter(expected, resultpath :+ "verdict")(_.mapObject(_.add("outcome", Json.fromString("decision-needed")))))
        val reader = take(NativeHostEvidence.bindC(call.core.component.get.subsystem.get.configuration,
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
        val context = take(NativeHostReviewContext.goalC(call.core, _goal_work())).get
        val annotated = context.copy(target = _record(_json(context.target).mapObject(
          _.add("commandGrant", Json.True).add("diff", Json.False).add("managementHash", Json.Null))),
          completion = context.completion.copy(handle = _record(_json(context.completion.handle).mapObject(_.add("grant", Json.True)))))
        val result = take(V.GoalPhaseWorkResult.createC(_record(Json.obj("outcome" -> Json.fromString("accept"),
          "summary" -> Json.fromString(_goal_result.summary), "findings" -> Json.arr(), "grant" -> Json.True))))
        When("public passive preparation and the existing v7 codec consume only selected current fields")
        val original = take(Preparation.goalC(context, _goal_result, _reference, _execution, _declared.issuer, _now))
        val changed = take(Preparation.goalC(annotated, result, _reference,
          _execution.copy(modelIdentity = Some("unused-model")), _declared.issuer, _now))
        val raw = original.record.getString(NativeHostEvidence.storageField).get
        val payload = io.circe.parser.parse(raw.substring(NativeHostEvidence.schema.length + 1)).toOption.get
        val decoded = take(NativeHostEvidence.decodeStorageC(NativeHostEvidence.schema + ":" + payload.mapObject(
          _.add("grant", Json.True).add("validUntil", Json.False)).noSpaces))
        val wrong = annotated.copy(target = _record(_json(context.target).mapObject(_.add("generation", Json.True))))
        val wrongtype = Preparation.goalC(wrong, result, _reference, _execution, _declared.issuer, _now)
        Then("annotations supply no authority, occurrence renewal or extra seal, while wrong consumed generation fails")
        changed.record.getString(NativeHostEvidence.storageField) shouldBe original.record.getString(NativeHostEvidence.storageField)
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
            NativeHostEvidenceToolFixture.normalCommandJson(output)))))
          val context = decoded.work.get.review.get.hostContext.get
          val prepared = take(_prepare(context))
          val canonical = take(_prepare(output.work.get.review.get.hostContext.get))
          val expected = take(ThreeWorkflowHostEvidencePorts.goalReviewBindingC(call.core, input, native))
          Then("canonical and normal wire inputs retain the same native owner candidate target and completion for " + kind)
          prepared.record.getString(NativeHostEvidence.storageField) shouldBe canonical.record.getString(NativeHostEvidence.storageField)
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
          NativeHostEvidenceToolFixture.normalCommandJson(output)))))
        val context = decoded.review.get.hostContext.get
        val prepared = take(_prepare(context))
        val canonical = take(_prepare(output.review.get.hostContext.get))
        val expected = take(ThreeWorkflowHostEvidencePorts.repositoryReviewBindingC(call.core, _repository_input, native))
        Then("the existing v7 evidence applies to the same native target and completion with the zero exit receipt")
        decoded.review.get.checks.head.exitCode shouldBe 0
        prepared.record.getString(NativeHostEvidence.storageField) shouldBe canonical.record.getString(NativeHostEvidence.storageField)
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
        val goal = take(NativeHostReviewContext.goalC(call.core, _goal_work())).get
        val repository = take(NativeHostReviewContext.repositoryC(call.core, _repository_work))
        val groups = Vector(
          ("Goal handle", goal, _handle_names, Vector.empty[String]),
          ("Repository handle", repository, _handle_names, Vector.empty[String]),
          ("Goal target", goal, _goal_names, Vector.empty[String]),
          ("Repository target", repository, _repository_names, Vector.empty[String]),
          ("Repository check", repository, Vector("exitCode" -> "exit_code"), Vector("checks", "0")))
        groups.foreach { (label, context, names, path) =>
          val ishandle = label.endsWith("handle")
          val original = _json(if (ishandle) context.completion.handle else context.target)
          def selected(value: Json): Json = if (path.isEmpty) value else value.hcursor.downField("checks").downArray.focus.get
          def replace(value: Json): V.WorkflowHostReviewContext = {
            val updated = if (path.isEmpty) value else original.mapObject(_.add("checks", Json.fromValues(
              original.hcursor.downField("checks").focus.get.asArray.get.updated(0, value))))
            if (ishandle) context.copy(completion = context.completion.copy(handle = _record(updated)))
            else context.copy(target = _record(updated))
          }
          names.foreach { (declaration, wire) =>
            Given("Spec: docs/spec/native-host-review-preparation.md; Rules: R1,R2,R4,R6; Example: E8; " + label + " pair " + declaration + "/" + wire)
            val fields = selected(original).asObject.get
            val value = fields(declaration).get
            val matching = replace(Json.fromJsonObject(fields.add(wire, value)))
            val snake = replace(Json.fromJsonObject(fields.remove(declaration).add(wire, value)))
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
              val refused = _prepare(replace(Json.fromJsonObject(alternative)))
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
        val goal = take(NativeHostReviewContext.goalC(call.core, _goal_work())).get
        val repository = take(NativeHostReviewContext.repositoryC(call.core, _repository_work))
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
          "null check" -> repository.copy(target = _record(target.mapObject(_.add("checks", Json.arr(Json.Null))))),
          "fractional exit" -> repository.copy(target = _record(target.mapObject(_.add("checks", Json.arr(fractionalexit))))))
        variants.foreach { (condition, context) =>
          Given("Spec: docs/spec/native-host-review-preparation.md; Rules: R1,R2,R4,R6; Example: E8; selected fixture target with " + condition)
          When("passive preparation consumes that malformed structure or integer field")
          val failure = _prepare(context)
          Then("the " + condition + " fails under the existing native target type semantics")
          failure.isSuccess shouldBe false
        }
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
              NativeHostEvidenceToolFixture.normalCommandJson(output)))))
            (decoded.review.get.hostContext.get, output.review.get.hostContext.get,
              take(ThreeWorkflowHostEvidencePorts.repositoryReviewBindingC(call.core, _typed_repository_input, native)), Purpose.RepositoryReview)
          } else {
            val input = _typed_goal_input.copy(kind = label)
            val work = _goal_work(input)
            val native = _submitted(work, GoalPhaseNativeWork.Result("accept", "Specification review", Vector.empty),
              GoalPhaseNativeWork.resultCodec.typeIdentity)
            val output = take(NativeWorkflowOperationOutput.goalWorkRequestC(work, call.core))
            val decoded = take(V.GoalPhaseAdvanceResult.createC(take(new RecordDecoder().json(
              NativeHostEvidenceToolFixture.normalCommandJson(output)))))
            (decoded.work.get.review.get.hostContext.get, output.work.get.review.get.hostContext.get,
              take(ThreeWorkflowHostEvidencePorts.goalReviewBindingC(call.core, input, native)), Purpose.GoalReview)
          }
          val prepared = take(_prepare(context))
          val evidence = _evidence(prepared)
          val raw = prepared.record.getString(NativeHostEvidence.storageField).get
          val checksKey = if (purpose == Purpose.GoalReview) "testChecks" else "checks"
          val stored = io.circe.parser.parse(raw.substring(NativeHostEvidence.schema.length + 1)).toOption.get
            .hcursor.downField("binding").downField("attestation").downField("target").downField(checksKey).downArray.downField("sbt")
          Then("raw canonical storage retains each explicit fact and the original owner completion and selected meaning")
          stored.get[String]("kind") shouldBe Right("test-only")
          stored.get[String]("outcome") shouldBe Right("succeeded")
          stored.downField("selection").get[Vector[String]]("testClasses") shouldBe Right(Vector("example.FirstSpec", "example.SecondSpec"))
          stored.downField("selection").get[Int]("completedSuites") shouldBe Right(2)
          stored.downField("selection").get[Long]("testsRun") shouldBe Right(25L)
          evidence.binding shouldBe _evidence(take(_prepare(canonical))).binding
          _observe(evidence, purpose, expected).isSuccess shouldBe true
          val reader = take(NativeHostEvidence.bindC(call.core.component.get.subsystem.get.configuration,
            GoalPhaseRuntimeParameters.Persistence(_configuration_, _collection_("instances"), _collection_("continuations"))))
          Given("the specification explicitly provisions that returned row using its existing managed Create permission")
          take(call.createC("host_evidence", prepared.recordIdentity, prepared.record))
          When("the live managed Reader loads the row against original and changed valid selected test counts")
          val accepted = take(reader.loadC(call.core, purpose, _key, expected))
          val changed = NativeHostHumanEvidenceFixture.alter(expected,
            Vector("attestation", "target", checksKey))(checks => Json.fromValues(checks.asArray.get.map(check =>
              NativeHostHumanEvidenceFixture.alter(check, Vector("sbt", "selection"))(
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
        val contexts = Vector(take(NativeHostReviewContext.goalC(call.core, _goal_work(_typed_goal_input))).get,
          take(NativeHostReviewContext.repositoryC(call.core, _typed_repository_work)))
        val selectionNames = Vector("testClasses" -> "test_classes", "completedSuites" -> "completed_suites", "testsRun" -> "tests_run")
        // Adversarial supplied Records retain explicit nulls at passive intake;
        // the ordinary JSON decoder drops null object fields.
        def suppliedValue(value: Json): Any = value.fold[Any](null, boolean => boolean,
          number => number.toBigDecimal.get, string => string, array => array.map(suppliedValue),
          fields => Record.createFull(fields.toVector.map { case (key, field) => key -> suppliedValue(field) }))
        def suppliedRecord(value: Json): Record = {
          val record = suppliedValue(value).asInstanceOf[Record]
          _json(record) shouldBe value
          record
        }
        contexts.foreach { context =>
          val goal = context.purpose == Purpose.GoalReview.identity
          val key = if (goal) "testChecks" else "checks"
          val target = _json(context.target)
          val check = target.hcursor.downField(key).downArray.focus.get
          val facts = check.hcursor.downField("sbt").focus.get
          val selection = facts.hcursor.downField("selection").focus.get
          def selected(check: Json): V.WorkflowHostReviewContext = context.copy(target = _record(target.mapObject(_.add(key, Json.arr(check)))))
          def selectedPresent(check: Json): V.WorkflowHostReviewContext = context.copy(target = suppliedRecord(target.mapObject(_.add(key, Json.arr(check)))))
          def withSelection(value: Json): Json = check.mapObject(_.add("sbt", facts.mapObject(_.add("selection", value))))
          val canonical = _evidence(take(_prepare(context)))
          selectionNames.foreach { (declaration, wire) =>
            val fields = selection.asObject.get
            val value = fields(declaration).get
            val alternatives = Vector(fields, fields.remove(declaration).add(wire, value), fields.add(wire, value))
            alternatives.foreach { alternative =>
              Given("the typed " + key + " selection with declaration-only snake-only or agreeing " + declaration + " aliases")
              When("passive preparation canonicalizes that bounded nested selection")
              val prepared = take(_prepare(selected(withSelection(Json.fromJsonObject(alternative)))))
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
              val refused = _prepare(selectedPresent(withSelection(Json.fromJsonObject(alternative))))
              Then("neither alias overrides malformed or contradictory presence")
              refused.isSuccess shouldBe false
            }
          }
          val invalidSelections = Vector(
            selection.mapObject(_.add("completedSuites", Json.fromBigDecimal(BigDecimal("2.5")))),
            selection.mapObject(_.add("completedSuites", Json.fromLong(Int.MaxValue.toLong + 1))),
            selection.mapObject(_.add("testsRun", Json.fromBigDecimal(BigDecimal("25.5")))),
            selection.mapObject(_.add("testsRun", Json.fromBigInt(BigInt(Long.MaxValue) + 1))),
            selection.mapObject(_.add("testsRun", Json.fromInt(0))),
            selection.mapObject(_.add("completedSuites", Json.fromInt(1))),
            selection.mapObject(_.add("testClasses", Json.arr(Json.fromString("invalid")))), Json.Null, Json.True)
          val invalidFacts = Vector(Json.Null, Json.True, Json.obj(), facts.mapObject(_.add("kind", Json.fromString("unknown"))),
            facts.mapObject(_.add("outcome", Json.fromString("unknown")))) ++
            invalidSelections.map(value => facts.mapObject(_.add("selection", value)))
          invalidFacts.foreach { malformed =>
            Given("present malformed sbt or selected classes suite count or test count in " + key)
            When("passive preparation delegates normalized facts to the existing Facts decoder")
            val refused = _prepare(selectedPresent(check.mapObject(_.add("sbt", malformed))))
            Then("invalid presence is refused without historical defaults or relaxed integer rules")
            refused.isSuccess shouldBe false
          }
          Given("historical absent sbt and an explicit empty check array in " + key)
          When("passive preparation encodes those absence shapes")
          val historical = _evidence(take(_prepare(selected(check.mapObject(_.remove("sbt"))))))
          val empty = _evidence(take(_prepare(context.copy(target = _record(target.mapObject(_.add(key, Json.arr())))))))
          Then("no typed facts are manufactured and an explicitly empty array remains explicit")
          historical.binding.hcursor.downField("attestation").downField("target").downField(key).downArray.downField("sbt").focus shouldBe None
          empty.binding.hcursor.downField("attestation").downField("target").get[Vector[Json]](key) shouldBe Right(Vector.empty)
          if (goal) {
            val snakeSelection = selectionNames.foldLeft(selection.asObject.get) { case (fields, (declaration, wire)) =>
              fields.remove(declaration).add(wire, fields(declaration).get)
            }
            val snakeCheck = withSelection(Json.fromJsonObject(snakeSelection)).mapObject(
              fields => fields.remove("exitCode").add("exit_code", fields("exitCode").get).add("annotation", Json.True))
            val snake = Json.arr(snakeCheck)
            Vector(target.asObject.get.remove("testChecks").add("test_checks", snake),
              target.asObject.get.add("test_checks", snake)).foreach { fields =>
              Given("Goal test_checks uses snake nested fields while testChecks is absent or agrees semantically")
              When("both structured alias values are independently projected")
              val prepared = _evidence(take(_prepare(context.copy(target = _record(Json.fromJsonObject(fields))))))
              Then("different spellings and inert check annotations retain one canonical selected meaning")
              prepared.binding shouldBe canonical.binding
            }
            val malformedArrays = Vector(Json.Null, Json.True, Json.arr(Json.Null), Json.arr(Json.True), Json.arr(),
              Json.arr(withSelection(selection.mapObject(_.add("testsRun", Json.fromInt(26))))))
            malformedArrays.foreach { value =>
              Given("testChecks and test_checks have malformed or contradictory selected arrays")
              When("passive preparation consumes the structured Goal aliases")
              val refused = _prepare(context.copy(target = suppliedRecord(target.mapObject(_.add("test_checks", value)))))
              Then("the alternate spelling cannot conceal malformed presence or different selected checks")
              refused.isSuccess shouldBe false
            }
            Given("the historical Goal target omits testChecks entirely")
            When("passive preparation encodes the historical target")
            val absent = _evidence(take(_prepare(context.copy(target = _record(target.mapObject(_.remove("testChecks")))))))
            Then("the absent historical field remains omitted")
            absent.binding.hcursor.downField("attestation").downField("target").downField("testChecks").focus shouldBe None
          }
        }
        Consequence.unit
      }
    }
  }
}
