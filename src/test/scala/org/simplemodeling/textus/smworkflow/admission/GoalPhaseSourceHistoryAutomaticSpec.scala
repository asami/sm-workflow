package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.time.LocalDate
import org.goldenport.cncf.context.RuntimeContext
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workspace.{FileTarget, WorkspaceId}
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseSourceHistoryAutomatic as A
import GoalPhaseSourceHistoryNormalization as N
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeWork as W
import SplitPhaseExplicitPartitionFixture.take

/** Specification-owned values prove codec/matching behavior, never native
  * authority, actual transport termination or a historical File outcome.
  * @since   Nov.  6, 2026
  * @version Nov.  8, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseSourceHistoryAutomaticSpec extends AnyWordSpec with NativeGoalSourceHistoryCliFixture {
  private val _invocation = A.Invocation("spec-component", "session", "GoalPhaseService.advanceGoalPhase",
    "b32f8d9cb7534bfd9f00dab409c5424b")
  private def _target(pending: Boolean = false): N.Target = {
    val rule = take(WorkflowSourceHistoryRule.decodeC(enabledSettings(ruleKey)))
    val legacy = SourceHistoryNormalizationSpecificationFixture.target(sourcePath, enabledSettings(ruleKey),
      rule.identity, prefixSize = 2, pending = pending)
    legacy.copy(recovery = legacy.recovery.copy(intent = legacy.recovery.intent.copy(
      invocation = Some(_invocation), historyPolicy = Some(enabledSettings(ruleKey)))))
  }
  private def _observation(target: N.Target): A.CallerObservation = A.CallerObservation(A.observationSchema,
    target, _invocation, ContextReference("actual-caller-owned-terminal-result", "1"),
    "2026-11-06T00:00:00Z", "exited", Some(1))
  private def _public_target(target: N.Target): Json = {
    val projected = RuntimeContext.PropertyNameContext.default.transformRecord(N.targetRecord(target))
    io.circe.parser.parse(take(projected.toJsonStringC)).fold(error => fail(error.message), identity)
  }
  private def _append(from: InstanceRecord, cause: String): InstanceRecord =
    take(from.appendC(from.revision, GoalPhaseNativeRun.history(from, "phase.InspectCandidate", cause)))
  private def _failed_facts: (O.State, A.Saved, A.Observed) = {
    val definition = take(GoalPhaseRuntimeCatalog.loadC()).definition
    val original = _target()
    val target = original.copy(recovery = original.recovery.copy(definitionIdentity = definition.workflowIdentity.value,
      definitionRevision = definition.workflowRevision.value))
    val recovery = target.recovery
    val plan = O.Plan(recovery.planIdentity, recovery.planRevision, "synthetic-phase", "synthetic-repository",
      Vector(O.Step(recovery.step, "Preserve the selected source", recovery.ownedPaths, Vector.empty, Vector("test"))),
      Vector("test"), repairLimit = 1)
    val initial = take(O.startC(plan, recovery.base))
    val controller = take(initial.acceptC(O.SemanticCompleted(initial.next.asInstanceOf[O.Semantic].key,
      "Synthetic accepted implementation")))
    val empty = take(InstanceRecord(InstanceIdentity(recovery.sessionId), definition,
      initialRevision, Lifecycle.NotStarted, None, CorrelationReference("synthetic-correlation"),
      CausationReference("synthetic-start"), None, None, None, Vector.empty).validateC)
    val frontier = (empty.revision.value until target.frontierRevision.toLong).foldLeft(empty)((previous, index) =>
      _append(previous, "synthetic-prefix-" + index))
    val selection = A.Selection(target, _invocation, _observation(target))
    val saved = A.Saved(_append(frontier, A.cause(target)), selection)
    val intended = _append(saved.record, A.causePrefix + "reconciliation:intent")
    val recorded = _append(intended, A.causePrefix + "reconciliation:result")
    (controller, saved, A.Observed(recorded, A.Reconciliation(selection, Some("failed"),
      Some("Source history rule: multiple managed block comments"))))
  }
  private def _repair_request(controller: O.State, observed: A.Observed): W.Request = {
    val semantic = controller.next.asInstanceOf[O.Semantic]
    W.Request(observed.record.identity.value, observed.record.revision.value.toString,
      semantic.key.kind.toString, semantic.key.step, semantic.key.generation, semantic.purpose,
      controller.plan, controller.base, controller.completed, semantic.candidate, semantic.checksReceipt,
      semantic.repairDirective, repairProgress = Some(GoalPhaseRepairProgress.snapshot(controller)))
  }

  "SHF-01 Formal failed reconciliation eligibility" should {
    "admit completed or decision-needed repair semantics only with the exact internal failed fact and current controller" in {
      Given("SHF-SUBMIT-02: synthetic failed reconciliation, current ordinary Repair and matching Request; none supplies native authority")
      val (inspection, saved, failed) = _failed_facts
      val controller = take(A.failedRepairStateC(saved, failed, inspection))
      val request = _repair_request(controller, failed)
      val completed = W.Result("completed", "Preserved both declarations under one managed header", Vector.empty)
      val decision = completed.copy(outcome = "decision-needed", summary = "A semantic direction remains necessary")
      When("strict admission and guarded native-owner admission consume the same context-free request")
      val strict = Vector(completed, decision).map(W.admitSemanticResultC(request, _))
      val admitted = Vector(completed, decision).map(W.admitSourceHistoryRepairResultC(request, _, failed, controller))
      Then("default admission still refuses while the internal proof admits only ordinary semantic completion or decision")
      strict.foreach(_.isSuccess shouldBe false)
      admitted.foreach(_.isSuccess shouldBe true)
      controller.repairs shouldBe 1
      controller.ordinaryRepairs shouldBe 1
      failed.reconciliation.outcome shouldBe Some("failed")
      failed.reconciliation.selection.target.recovery.intent.outcome shouldBe None
    }

    "refuse absent or crossed failure Request and controller bindings without relaxing context-free repairs" in {
      Given("SHF-SUBMIT-02: one synthetic exact failed source and reserved current-Step repair")
      val (inspection, saved, failed) = _failed_facts
      val controller = take(A.failedRepairStateC(saved, failed, inspection))
      val request = _repair_request(controller, failed)
      val result = W.Result("completed", "Synthetic source correction", Vector.empty)
      val requests = Vector(request.copy(instanceIdentity = "crossed-session"),
        request.copy(stepIdentity = Some("crossed-step")), request.copy(stepIdentity = None),
        request.copy(selectedPlan = request.selectedPlan.copy(identity = "crossed-plan")),
        request.copy(selectedPlan = request.selectedPlan.copy(revision = "crossed-plan-revision")),
        request.copy(originalBase = request.originalBase.copy(head = "c" * 40)),
        request.copy(completed = Vector(null)), request.copy(generation = request.generation + 1),
        request.copy(repairProgress = Some(GoalPhaseRepairProgress.snapshot(controller).copy(ordinaryCount = 0))),
        request.copy(repairDirective = Some(O.RepairDirective("Unproven failure", Vector(sourcePath)))),
        request.copy(repairDirective = None), request.copy(sourceRevision = (failed.record.revision.value - 1).toString),
        request.copy(sourceRevision = null), request.copy(kind = "ImplementStep"),
        request.copy(candidate = Some(controller.base)), request.copy(checksReceipt = Some("unproven-checks")),
        request.copy(review = Some(GoalPhaseNativeReview.Context(Vector(sourcePath), "Unrelated review diff", Vector.empty))))
      val succeeded = failed.copy(reconciliation = failed.reconciliation.copy(outcome = Some("updated-current"), diagnostic = None))
      val resultless = failed.copy(reconciliation = failed.reconciliation.copy(outcome = None, diagnostic = None))
      val foreignsession = failed.copy(record = failed.record.copy(identity = InstanceIdentity("crossed-session")))
      val wrongcause = failed.copy(record = _append(failed.record, "unrelated-native-result"))
      val next = take(controller.acceptC(O.SemanticCompleted(controller.next.asInstanceOf[O.Semantic].key, "Synthetic completion")))
      val tipplan = inspection.plan.copy(identity = "crossed-controller-plan")
      val tipinitial = take(O.startC(tipplan, inspection.base.copy(tree = "d" * 40)))
      val tipinspection = take(tipinitial.acceptC(O.SemanticCompleted(tipinitial.next.asInstanceOf[O.Semantic].key, "Synthetic implementation")))
      val crossedcontroller = take(tipinspection.continueSourceHistoryRepairC("synthetic-other-failure", request.repairDirective.get))
      When("the typed native-owner gate receives each crossed request, source kind or supplied controller")
      val refused = requests.map(W.admitSourceHistoryRepairResultC(_, result, failed, controller)) ++
        Vector(null, succeeded, resultless, foreignsession, wrongcause,
          failed.copy(reconciliation = failed.reconciliation.copy(diagnostic = Some(" "))))
          .map(W.admitSourceHistoryRepairResultC(request, result, _, controller)) ++
        Vector(null, inspection, next, crossedcontroller).map(W.admitSourceHistoryRepairResultC(request, result, failed, _))
      Then("none may replace the original failed source or select a different semantic frontier")
      refused.foreach(_.isSuccess shouldBe false)
      W.admitSourceHistoryRepairResultC(null, result, failed, controller).isSuccess shouldBe false
      W.admitSemanticResultC(request, result).isSuccess shouldBe false
      controller.next.asInstanceOf[O.Semantic].key.step shouldBe request.stepIdentity
      controller.ordinaryRepairs shouldBe 1
    }

    "retain complete semantic result restrictions after authenticating a failed source" in {
      Given("SHF-SUBMIT-02: a matching synthetic failed-source repair and payloads violating ordinary semantic contracts")
      val (inspection, saved, failed) = _failed_facts
      val controller = take(A.failedRepairStateC(saved, failed, inspection))
      val request = _repair_request(controller, failed)
      val result = W.Result("completed", "Synthetic source correction", Vector.empty)
      val blocked = GoalPhaseFixAssessment.Report(GoalPhaseFixAssessment.Change("STANDARD_LOGIC", Vector("managed-header"),
        false, false, "One managed comment preserves both declarations", false, false),
        GoalPhaseFixAssessment.Convergence("BLOCKED", "HIGH", "A direction is still required"))
      val invalid = Vector(result.copy(outcome = "accept"), result.copy(summary = " "),
        result.copy(findings = Vector("Unresolved semantic finding")), result.copy(fixAssessment = Some(blocked)),
        result.copy(issueReport = Some(GoalPhaseFixIssues.Report(Vector(
          GoalPhaseFixIssues.Disposition("unassigned-issue", "FIXED", "Unproven issue disposition", None))))),
        result.copy(acceptance = Some(GoalPhaseAcceptanceBoundary.Report(Vector(
          GoalPhaseAcceptanceBoundary.Observation("unselected-condition", "satisfied", "Unproven acceptance")), Vector.empty))),
        result.copy(reviewObservations = Some(GoalPhaseFindingRecording.Observations(Vector(
          GoalPhaseFindingRecording.Observation("unassigned-review", "HYGIENE", "Unrelated observation", "Repair cannot review"))))),
        result.copy(dependencyProposal = null), result.copy(dependencyValidation = null),
        result.copy(completionConditions = Some(GoalPhaseNativeReviewCompletion.Conditions("terminal-lf", Vector(sourcePath)))))
      When("the guarded gate applies the complete existing semantic validators after its source check")
      val refused = invalid.map(W.admitSourceHistoryRepairResultC(request, _, failed, controller))
      Then("failed-source provenance grants no review acceptance issue dependency assessment or closure bypass")
      refused.foreach(_.isSuccess shouldBe false)
      W.admitSourceHistoryRepairResultC(request, result, failed, controller).isSuccess shouldBe true
    }

    "carry the same internal failed proof through prior retention and ordinary count assessment without a special exemption" in {
      Given("SHF-SUBMIT-02: a synthetic actual-shaped completion and exact internal provenance, with no prior review")
      val (inspection, saved, failed) = _failed_facts
      val controller = take(A.failedRepairStateC(saved, failed, inspection))
      val request = _repair_request(controller, failed)
      val result = W.Result("completed", "Synthetic source correction", Vector.empty)
      val provenance = Some(failed -> controller)
      val reference = ContextReference("synthetic-repair-result", "1")
      val submitted = ContinuationResult(
        WorkflowHandle(org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId,
          failed.record.definition.workflowIdentity, failed.record.definition.workflowRevision, failed.record.identity),
        StateMachineRunIdentity(request.instanceIdentity),
        org.goldenport.cncf.workflow.ContinuationIdentity("synthetic-repair-continuation"),
        StateMachineRevision(request.sourceRevision), ContextSnapshot(failed.record.definition.workflowRevision.value),
        TypedValue(W.resultCodec.typeIdentity, result), reference, Vector.empty,
        ExecutionEvidence(Vector.empty, Some("synthetic-worker-with-no-authority")))
      val minor = GoalPhaseFixAssessment.Report(GoalPhaseFixAssessment.Change("SIMPLE_LOGIC", Vector("managed-header"),
        false, true, "A bounded minor correction", false, false),
        GoalPhaseFixAssessment.Convergence("PROGRESSING", "HIGH", "The concrete diagnostic is addressed"))
      When("the two completion admission points receive the proof or their unchanged default arguments")
      val retained = GoalPhaseNativeReview.retainPriorC(Vector.empty, request, submitted, provenance)
      val counted = GoalPhaseFixAssessment.ordinaryCountExemptC(request, result, provenance)
      val exempt = GoalPhaseFixAssessment.ordinaryCountExemptC(request, result.copy(fixAssessment = Some(minor)), provenance)
      Then("proof forwards no invented prior report and only the existing assessment predicate can exempt an ordinary count")
      take(retained) shouldBe Vector.empty
      take(counted) shouldBe false
      take(exempt) shouldBe true
      GoalPhaseNativeReview.retainPriorC(Vector.empty, request, submitted).isSuccess shouldBe false
      GoalPhaseNativeReview.retainPriorC(Vector(null), request, submitted, provenance).isSuccess shouldBe false
      GoalPhaseNativeReview.retainPriorC(Vector.empty, request.copy(kind = "ImplementStep"), submitted, provenance).isSuccess shouldBe false
      GoalPhaseNativeReview.retainPriorC(Vector.empty, request, submitted, Some(null)).isSuccess shouldBe false
      GoalPhaseFixAssessment.ordinaryCountExemptC(request, result).isSuccess shouldBe false
      GoalPhaseFixAssessment.ordinaryCountExemptC(request.copy(generation = request.generation + 1), result, provenance).isSuccess shouldBe false
      GoalPhaseFixAssessment.ordinaryCountExemptC(request, result, Some(null.asInstanceOf[A.Observed] -> controller)).isSuccess shouldBe false
      GoalPhaseFixAssessment.ordinaryCountExemptC(request, result, Some(null)).isSuccess shouldBe false
      controller.repairs shouldBe 1
      controller.ordinaryRepairs shouldBe 1
    }

    "admit only the exact native bookkeeping successor from failed inspection to Decision" in {
      Given("CB-SHF-01-001: synthetic canonical failed-result and adjacent native records; these pure values are not runtime authority")
      val (_, _, failed) = _failed_facts
      val cause = A.causePrefix + "repair-decision"
      def _decision_(from: InstanceRecord, selectedcause: String, progression: String): InstanceRecord =
        take(from.appendC(from.revision, GoalPhaseNativeRun.history(from, progression, selectedcause)))
      val decision = _decision_(failed.record, cause, "phase.Decision")
      When("the exact failed payload and actual adjacent Decision record are checked through the pure native-edge guard")
      val admitted = take(A.failureDecisionRecordC(decision, failed, decision, failed.reconciliation))
      Then("only the declared new edge is admitted while the original failure record remains inspection")
      admitted shouldBe decision
      failed.record.currentProgression shouldBe Some(ProgressionReference("phase.InspectCandidate"))
      decision.history.dropRight(1) shouldBe failed.record.history
      When("cause progression adjacency payload or original failure identity crosses the exact bookkeeping boundary")
      val wrongcause = _decision_(failed.record, "synthetic-crossed-cause", "phase.Decision")
      val wrongprogression = _decision_(failed.record, cause, "phase.InspectCandidate")
      val extra = _decision_(decision, cause, "phase.Decision")
      val succeeded = failed.copy(reconciliation = failed.reconciliation.copy(outcome = Some("updated-current"), diagnostic = None))
      val missing = failed.copy(reconciliation = failed.reconciliation.copy(outcome = None, diagnostic = None))
      val refused = Vector(A.failureDecisionRecordC(wrongcause, failed, wrongcause, failed.reconciliation),
        A.failureDecisionRecordC(wrongprogression, failed, wrongprogression, failed.reconciliation),
        A.failureDecisionRecordC(extra, failed, extra, failed.reconciliation),
        A.failureDecisionRecordC(decision, failed, decision, failed.reconciliation.copy(diagnostic = Some("crossed diagnostic"))),
        A.failureDecisionRecordC(decision, succeeded, decision, succeeded.reconciliation),
        A.failureDecisionRecordC(decision, missing, decision, missing.reconciliation),
        A.failureDecisionRecordC(decision, failed.copy(record = failed.record.copy(identity = InstanceIdentity("foreign-session"))),
          decision, failed.reconciliation))
      Then("all crossed and resultless values refuse instead of relabelling an old record or supplying Human authority")
      refused.foreach(_.isSuccess shouldBe false)
      failed.reconciliation.outcome shouldBe Some("failed")
      failed.reconciliation.selection.target.recovery.intent.outcome shouldBe None
    }

    "distinguish the exact failed fact from missing and successful results without any native effect" in {
      Given("synthetic canonical selection, adjacent intent/result records and pure inspection controller; these values grant no native authority")
      val (controller, saved, failed) = _failed_facts
      val intended = take(GoalPhaseNativeHistory.prefixC(failed.record, saved.record.revision.next))
      val resultless = A.Observed(intended, failed.reconciliation.copy(outcome = None, diagnostic = None))
      val succeeded = failed.copy(reconciliation = failed.reconciliation.copy(outcome = Some("updated-current"), diagnostic = None))
      When("the pure owner connection receives the distinct observed facts")
      val repaired = take(A.failedRepairStateC(saved, failed, controller))
      val repeat = take(A.failedRepairStateC(saved, failed, controller))
      Then("only the formal failure reconstructs one same-Step ordinary repair with the actual reason and target path")
      failed.failed shouldBe true
      failed.succeeded shouldBe false
      resultless.failed shouldBe false
      succeeded.failed shouldBe false
      succeeded.succeeded shouldBe true
      A.failedRepairStateC(saved, resultless, controller).isSuccess shouldBe false
      A.failedRepairStateC(saved, succeeded, controller).isSuccess shouldBe false
      repaired.stage shouldBe O.Stage.Repair
      repaired.repairs shouldBe 1
      repaired.ordinaryRepairs shouldBe 1
      repaired.plan shouldBe controller.plan
      repaired.base shouldBe controller.base
      repaired.tipSnapshot shouldBe controller.tipSnapshot
      repaired.repairDirective.get.reason should include(sourcePath)
      repaired.repairDirective.get.reason should include("multiple managed block comments")
      repaired.repairDirective.get.findings shouldBe Vector(sourcePath + ": Source history rule: multiple managed block comments")
      repeat.next shouldBe repaired.next
      repeat.repairs shouldBe 1
      repeat.ordinaryRepairs shouldBe 1
      A.failedRepairStateC(saved, failed, repaired).isSuccess shouldBe false
      saved.selection.target.recovery.intent.outcome shouldBe None
    }

    "refuse crossed selection Step Plan base tip and native predecessor facts" in {
      Given("SHF-01: synthetic adjacent failure facts with their exact selected target and controller")
      val (controller, saved, failed) = _failed_facts
      val target = saved.selection.target
      val altered = target.copy(frontierCause = "crossed-frontier")
      val crossed = saved.selection.copy(target = altered, observation = saved.selection.observation.copy(target = altered))
      val wrongcause = _append(take(GoalPhaseNativeHistory.prefixC(failed.record, saved.record.revision.next)), "synthetic-wrong-result")
      val extra = _append(failed.record, A.causePrefix + "reconciliation:result")
      def _controller_(plan: O.Plan, base: O.Snapshot): O.State = {
        val initial = take(O.startC(plan, base))
        take(initial.acceptC(O.SemanticCompleted(initial.next.asInstanceOf[O.Semantic].key, "Synthetic acceptance")))
      }
      val controllers = Vector(_controller_(controller.plan.copy(identity = "crossed-plan"), controller.base),
        _controller_(controller.plan.copy(revision = "crossed-revision"), controller.base),
        _controller_(controller.plan.copy(steps = controller.plan.steps.map(_.copy(identity = "crossed-step"))), controller.base),
        _controller_(controller.plan, controller.base.copy(head = "c" * 40)),
        _controller_(controller.plan, controller.base.copy(tree = "d" * 40)))
      When("each supplied fact crosses exactly one frozen boundary")
      val refused = controllers.map(A.failedRepairStateC(saved, failed, _)) ++ Vector(
        A.failedRepairStateC(saved, failed.copy(reconciliation = failed.reconciliation.copy(selection = crossed)), controller),
        A.failedRepairStateC(saved.copy(record = saved.record.copy(identity = InstanceIdentity("crossed-session"))), failed, controller),
        A.failedRepairStateC(saved, failed.copy(record = wrongcause), controller),
        A.failedRepairStateC(saved, failed.copy(record = extra), controller),
        A.failedRepairStateC(saved, failed.copy(reconciliation = failed.reconciliation.copy(diagnostic = None)), controller))
      Then("no crossed or malformed fact reconstructs a repair reservation")
      refused.foreach(_.isSuccess shouldBe false)
      controller.repairs shouldBe 0
      controller.ordinaryRepairs shouldBe 0
    }
  }

  "E1 Legacy bytes and associated source-history intent" should afterWord(
    "in spec:goal-phase-source-history-recovery, example:E1, rules:SHR-AUTO-01, phase:common-repair") {
    "retain the original v1 shape and require the complete v2 invocation and rule" in {
      Given("docs/spec/goal-phase-source-history-recovery.md SHR-AUTO-01 E1: an original v1 intent with its original date")
      val legacy = _target().recovery.intent.copy(invocation = None, historyPolicy = None)
      val original = Json.obj("schemaVersion" -> "sm-workflow.goal-phase-native-source-history.v1".asJson,
        "payload" -> Json.obj("sourceRevision" -> legacy.sourceRevision.asJson, "step" -> legacy.step.asJson,
          "path" -> legacy.path.asJson, "style" -> legacy.style.asJson, "providerIdentity" -> legacy.providerIdentity.asJson,
          "updatedOn" -> legacy.updatedOn.asJson, "dateUncertain" -> legacy.dateUncertain.asJson, "outcome" -> Json.Null))
      val codec = GoalPhaseNativeSourceHistory.codec
      When("the same legacy intent and a new associated intent are encoded and decoded")
      val fresh = _target().recovery.intent
      val wire = codec.encode(fresh)
      Then("legacy bytes remain exact and the explicit v2 retains both invocation and complete rule")
      codec.encode(legacy).noSpaces shouldBe original.noSpaces
      codec.decode(original) shouldBe Right(legacy)
      wire.hcursor.get[String]("schemaVersion") shouldBe Right("sm-workflow.goal-phase-native-source-history.v2")
      codec.decode(wire) shouldBe Right(fresh)
      val result = fresh.copy(outcome = Some("updated"))
      codec.decode(codec.encode(result)) shouldBe Right(result)
      result.invocation shouldBe fresh.invocation
      result.historyPolicy shouldBe fresh.historyPolicy
      Vector("invocation", "historyPolicy").foreach { key =>
        codec.decode(wire.mapObject(fields => fields.add("payload", fields("payload").get.mapObject(_.remove(key))))).isLeft shouldBe true
        codec.decode(wire.mapObject(fields => fields.add("payload", fields("payload").get.mapObject(_.add(key, Json.Null))))).isLeft shouldBe true
      }
      codec.decode(wire.mapObject(_.add("schemaVersion", "sm-workflow.goal-phase-native-source-history.v1".asJson))).isLeft shouldBe true
      codec.decode(codec.encode(fresh.copy(historyPolicy = Some(enabledSettings(ruleKey).mapObject(_.add("excess", Json.True)))))).isLeft shouldBe true
      codec.decode(codec.encode(fresh.copy(invocation = Some(_invocation.copy(operation = "submitGoalPhaseWorkResult"))))).isLeft shouldBe true
    }
  }

  "E2 Closed finite caller transport observation" should afterWord(
    "in spec:goal-phase-source-history-recovery, example:E2, rules:SHR-AUTO-01, phase:common-repair") {
    "accept the unchanged public target projection while closing every other observation field and retaining strict durable targets" in {
      Given("docs/spec/goal-phase-source-history-recovery.md SHR-AUTO-01 E2: the actual public Record naming projection of a complete target and an otherwise canonical finite observation")
      val target = _target()
      val observation = _observation(target)
      val publictarget = _public_target(target)
      val publicbytes = publictarget.noSpaces.getBytes(UTF_8).toVector
      val wire = observation.asJson.mapObject(_.add("target", publictarget))
      When("the Resource boundary decodes the copied public target without changing the finite factual remainder")
      val decoded = A.decodeObservation(wire)
      Then("only the target is normalized into its exact internal contract and durable codecs remain strict")
      publictarget.hcursor.downField("recovery").get[String]("session_id") shouldBe Right("session")
      decoded shouldBe Right(observation)
      publictarget.noSpaces.getBytes(UTF_8).toVector shouldBe publicbytes
      A.matchObservationC(_invocation.componentInstance, target, decoded.toOption.get).isSuccess shouldBe true
      N.decodeTarget(publictarget).isLeft shouldBe true
      val selection = A.Selection(target, _invocation, observation)
      val durable = A.codec.encode(selection)
      A.codec.decode(durable) shouldBe Right(selection)
      A.codec.decode(durable.mapObject(fields => fields.add("payload", fields("payload").get
        .mapObject(_.add("target", publictarget))))).isLeft shouldBe true
      Vector(publictarget.mapObject(_.add("extra", Json.True)),
        publictarget.mapObject(_.add("frontier_revision", Json.Null)),
        publictarget.mapObject(_.add("frontierRevision", target.frontierRevision.asJson))).foreach { altered =>
        A.decodeObservation(wire.mapObject(_.add("target", altered))).isLeft shouldBe true
      }
      A.decodeObservation(wire.mapObject(_.add("observed_at", observation.observedAt.asJson))).isLeft shouldBe true
      A.decodeObservation(wire.mapObject(_.add("sourceReference", Json.Null))).isLeft shouldBe true
      val stale = wire.mapObject(_.add("target", publictarget.mapObject(_.add("frontier_cause", "stale-cause".asJson))))
      val stalefact = A.decodeObservation(stale).toOption.get
      stalefact.target.frontierCause shouldBe "stale-cause"
      A.matchObservationC(_invocation.componentInstance, target, stalefact).isSuccess shouldBe false
    }

    "reject missing explicit-null extra malformed and incompatible termination fields" in {
      Given("docs/spec/goal-phase-source-history-recovery.md SHR-AUTO-01 E2: one complete test-owned caller observation")
      val observation = _observation(_target())
      val wire = observation.asJson
      When("closed observation values are acquired without interpreting them as consent")
      val unknown = observation.copy(termination = "unknown", exitCode = None)
      val live = observation.copy(termination = "live", exitCode = None)
      Then("only canonical finite transport shapes decode and exitCode belongs only to exited")
      A.decodeObservation(wire) shouldBe Right(observation)
      A.decodeObservation(unknown.asJson) shouldBe Right(unknown)
      A.decodeObservation(live.asJson) shouldBe Right(live)
      wire.asObject.get.keys.foreach(key => A.decodeObservation(wire.mapObject(_.remove(key))).isLeft shouldBe true)
      A.decodeObservation(wire.mapObject(_.add("humanAnswer", Json.True))).isLeft shouldBe true
      A.decodeObservation(live.asJson.mapObject(_.add("exitCode", Json.Null))).isLeft shouldBe true
      Vector(observation.copy(termination = "exited", exitCode = None), observation.copy(termination = "live"),
        observation.copy(termination = "timed-out"), observation.copy(observedAt = "yesterday"),
        observation.copy(sourceReference = ContextReference("", "1")),
        observation.copy(invocation = _invocation.copy(identity = "not-an-invocation"))).foreach(value =>
          A.decodeObservation(value.asJson).isLeft shouldBe true)
    }
  }

  "E3 Exact associated frontier matching" should afterWord(
    "in spec:goal-phase-source-history-recovery, example:E3, rules:SHR-AUTO-01, phase:common-repair") {
    "admit only exited for the exact Component Session intent invocation full rule and native target" in {
      Given("docs/spec/goal-phase-source-history-recovery.md SHR-AUTO-01 E3: the complete original target with old successful v1 prefixes")
      val target = _target()
      val observation = _observation(target)
      When("terminal and crossed factual observations are matched against the reconstructed native target")
      val crossed = Vector(observation.copy(termination = "live", exitCode = None),
        observation.copy(termination = "unknown", exitCode = None),
        observation.copy(invocation = _invocation.copy(componentInstance = "other-component")),
        observation.copy(invocation = _invocation.copy(sessionId = "other-session")),
        observation.copy(invocation = _invocation.copy(operation = "GoalPhaseService.startGoalPhase")),
        observation.copy(invocation = _invocation.copy(identity = "1395912e582b44fdb47b170fe2baf326")),
        observation.copy(target = target.copy(frontierCause = "stale-cause")),
        observation.copy(target = target.copy(recovery = target.recovery.copy(intentIdentity = "crossed-intent"))))
      Then("only the exact terminal association admits selection and old v1 successful prefixes remain reusable facts")
      A.matchObservationC(_invocation.componentInstance, target, observation).isSuccess shouldBe true
      A.matchObservationC("other-component", target, observation).isSuccess shouldBe false
      crossed.foreach(value => A.matchObservationC(_invocation.componentInstance, target, value).isSuccess shouldBe false)
      target.recovery.completedPrefix.foreach(_.intent.invocation shouldBe None)
      val selected = A.Selection(target, _invocation, observation)
      A.codec.decode(A.codec.encode(selected)) shouldBe Right(selected)
      val unresolved = A.Reconciliation(selected, None, None)
      A.reconciliationCodec.decode(A.reconciliationCodec.encode(unresolved)) shouldBe Right(unresolved)
      val failed = unresolved.copy(outcome = Some("failed"), diagnostic = Some("Actual managed source is ambiguous"))
      A.reconciliationCodec.decode(A.reconciliationCodec.encode(failed)) shouldBe Right(failed)
      A.reconciliationCodec.decode(A.reconciliationCodec.encode(unresolved.copy(outcome = Some("old-success")))).isLeft shouldBe true
      A.reconciliationCodec.decode(A.reconciliationCodec.encode(failed.copy(diagnostic = None))).isLeft shouldBe true
      N.codec.decode(A.codec.encode(selected)).isLeft shouldBe true
      A.codec.decode(N.codec.encode(N.Direction(target, N.choices.head, "Explicit old API only", "participant",
        ContextReference("held-policy", "1"), true))).isLeft shouldBe true
      target.recovery.intent.outcome shouldBe None
    }

    "retain legacy UNKNOWN and its genuine-question boundary without inventing an invocation or Human answer" in {
      Given("docs/spec/goal-phase-source-history-recovery.md SHR-AUTO-01 E3: legacy pending-question target data with no invocation association")
      val target = _target(pending = true)
      val legacy = target.copy(recovery = target.recovery.copy(intent = target.recovery.intent.copy(
        invocation = None, historyPolicy = None)))
      val oldwire = legacy.asJson
      When("a caller supplies an otherwise terminal observation for unassociable legacy data")
      val refused = A.matchObservationC(_invocation.componentInstance, legacy, _observation(legacy))
      Then("the exact missing fact refuses automatic authority and leaves the pending target and UNKNOWN unchanged")
      refused.isSuccess shouldBe false
      refused.toString should include("no retained invocation association")
      A.validSelection(A.Selection(legacy, _invocation, _observation(legacy))) shouldBe false
      legacy.asJson shouldBe oldwire
      legacy.recovery.intent.outcome shouldBe None
      legacy.pendingQuestion should not be empty
      // Data alone does not create a genuine native question or exercise supersession.
    }
  }

  "E4 Read-only current postcondition" should afterWord(
    "in spec:goal-phase-source-history-recovery, example:E4, rules:SHR-AUTO-01, phase:common-repair") {
    "observe conformance or a unique correction without File write authority and retain concrete ambiguity" in {
      Given("docs/spec/goal-phase-source-history-recovery.md SHR-AUTO-01 E4: current text and the original retained date with read-only File authority")
      val target = _target()
      val rule = take(WorkflowSourceHistoryRule.decodeC(target.recovery.historyPolicy))
      val day = LocalDate.parse(target.recovery.intent.updatedOn)
      val conformant = take(WorkflowSourceHistoryRule.applyC(rule, WorkflowSourceHistoryRule.Input(sourcePath,
        changedText, day, None, None, true))).text
      val ambiguous = "/**\n * @version Oct. 20, 2026\n * @version Oct. 21, 2026\n */\n" + changedText
      Vector(changedText -> "correction-required", conformant -> "observed-current-postcondition", ambiguous -> "failed").foreach {
        (text, expected) =>
          val root = WorkflowFileRuntimeFixture.fresh()
          val file = root.resolve(sourcePath)
          Files.createDirectories(file.getParent)
          Files.writeString(file, text)
          val modified = Files.getLastModifiedTime(file)
          WorkflowFileRuntimeFixture.withCore(root, workspace = "sm-workflow-repository", access = Some("read-only"),
            mapping = enabledSettings) { core =>
            When("the current managed comment is observed before transport termination is established")
            val observation = take(A.currentObservationC(core, target))
            Then("current text is reported without claiming the old outcome or termination and bytes and mtime stay intact")
            observation should include(expected)
            observation should include("does not prove the old File outcome or invocation termination")
            if (expected == "failed") observation should include("duplicate since or latest version")
            Files.readString(file) shouldBe text
            Files.getLastModifiedTime(file) shouldBe modified
          }
      }
    }
  }
}
