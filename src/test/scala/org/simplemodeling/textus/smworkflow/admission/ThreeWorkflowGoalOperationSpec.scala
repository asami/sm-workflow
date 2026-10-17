package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Property, Request}
import org.simplemodeling.textus.smworkflow.value as V
import org.simplemodeling.textus.smworkflow.host.{NativeHostReviewPreparation, NativeHostHumanPreparation, NativeHostAuthorityPreparation, NativeWorkflowHostExecution}
import WorkflowExecutionHarness as H
import org.goldenport.record.Record
import org.goldenport.record.io.RecordDecoder
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeWork as S
import SplitPhaseExplicitPartitionFixture.take
import GoalPhaseWorkClassification as W
import GoalPhaseAcceptanceBoundary as B

/** The spec owns selected input, source edits and semantic/review responses.
  * Real public Operations own all history, checks, staging, commits and closure.
  * Host evidence is explicit test data, not independent production issuance.
  * @since   Oct.  7, 2026
  *  version Oct. 15, 2026
  * @version Oct. 16, 2026
  * @author  ASAMI, Tomoharu
  */
final class ThreeWorkflowGoalOperationSpec extends AnyWordSpec with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  private def _assert_issued_context[A](value: Option[V.WorkflowIssuedWorkContext],
    issued: WorkflowInteraction[A, Nothing], mode: String,
    codec: WorkflowResultJsonV1.PayloadCodec[A]): Unit = {
    value should not be empty
    val view = value.get
    val order = issued.current.asInstanceOf[WorkflowContinuation.WorkOrder[A]]
    val request = order.request
    val canonical = take(new RecordDecoder().json(take(WorkflowWorkOrderJsonV1.encodeC(issued, codec))))
    val native = canonical.getRecord("request").get
    view.thinkingMode shouldBe mode
    view.schemaVersion shouldBe "cncf.workflow-protocol.v1"
    view.handle.getString("componentIdentity") shouldBe Some(issued.handle.componentIdentity.name)
    view.handle.getString("workflowIdentity") shouldBe Some(issued.handle.workflowIdentity.value)
    view.handle.getString("workflowRevision") shouldBe Some(issued.handle.workflowRevision.value)
    view.handle.getString("instanceIdentity") shouldBe Some(issued.handle.instanceIdentity.value)
    view.runId shouldBe request.runId.value
    view.continuationId shouldBe request.continuationId.value
    view.expectedRevision shouldBe request.expectedRevision.value
    view.requiredOperation shouldBe request.requiredOperation.capability
    view.inputType shouldBe request.input.map(_.typeIdentity)
    view.resultType shouldBe request.resultType.map(_.value)
    view.operation.getString("service") shouldBe Some(request.operation.service)
    view.operation.getString("operation") shouldBe Some(request.operation.operation)
    view.completionOperation.getString("service") shouldBe Some(request.completionOperation.service)
    view.completionOperation.getString("operation") shouldBe Some(request.completionOperation.operation)
    view.context shouldBe native.getRecord("context").get
    view.completion shouldBe native.getRecord("completion").get
    view.evidence shouldBe native.getRecord("evidence").get
    view.executionRequirement shouldBe canonical.getRecord("requirement").get
    view.executionRequirement.getString("reasoning") shouldBe Some(order.requirement.reasoning.value)
    view.executionRequirement.getString("reasoningClass") shouldBe order.requirement.reasoningClass.map(_.value)
    view.executionRequirement.getBoolean("reviewRequired") shouldBe Some(order.requirement.reviewRequired)
  }

  "GoalPhase public Operations on the production three-workflow composition" should afterWord(
    "in spec:native-issued-work-context, example:O-Goal, rules:R1,R2,R4,R6, phase:6"
  ) {
    "interleave two independent executions from the same chat using only returned session IDs" in {
      Given("Phase5.3 P53-02–05: one business plan, one authenticated chat and two real Starts")
      val workroot = Files.createDirectories(Path.of("target/sm-workflow/work/direct-repository-operation").toAbsolutePath)
      val root = Files.createTempDirectory(workroot, "goal-sessions-")
      val host = ThreeWorkflowNormalHostFixture.definition(root)
      val selection = _goal_selection_(host.goal.input, "two-execution-plan", "host-plan-entry")
      val plan = O.Plan("two-execution-plan", "1", host.goal.input.phaseIdentity, host.goal.input.repositoryIdentity,
        Vector(O.Step("step-1", "Implement the selected boundary", host.goal.candidatePaths, Vector.empty, Vector("test"))),
        Vector("test"), repairLimit = 0)
      _with_runtime_(root, "Goal", Some(host)) { (owner, component, actor, prepare) =>
        def invoke[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
          read_value[A](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
            service = "GoalPhaseService", operation = operation, properties = properties), actor)))
        prepare(call => call.setupC(_setup_commands_(root, "Goal").size).flatMap(_ =>
          call.createC("goal_selection", NativeWorkflowStartBusinessKey(NativeWorkflowStartInput.Goal(host.goal.input),
            selection.participantIdentity), Record.data(ProfileSelectionJsonV1.storageField ->
              take(ProfileSelectionJsonV1.encodeStorageC(selection)))).flatMap(_ =>
            call.createC("goal_plans", selection.selectionId, Record.data(GoalPhaseSelectedPlanJsonV1.storageField ->
              take(GoalPhaseSelectedPlanJsonV1.encodeStorageC(GoalPhaseNativeRun.SelectedPlan(selection.selectionId,
                selection.participantIdentity, plan))))))))
        When("the same business input starts two independent executions")
        val started = Vector.fill(2)(invoke[V.GoalPhaseStartResult]("startGoalPhase", NativeWorkflowOperationInputFixture.goal(host.goal.input)))
        Then("each Start returns its own session rather than an association selector or derived plan ID")
        started.map(_.sessionId).distinct.size shouldBe 2
        val ids = started.map(s => InstanceIdentity(s.sessionId))
        def current(id: InstanceIdentity): InstanceRecord = {
          var observed = Option.empty[InstanceRecord]
          prepare(call => call.goalCurrentC(id).map(value => { observed = Some(value); () }))
          observed.get
        }
        val initial = ids.map(current)
        When("their Advances are interleaved by the returned session IDs")
        val first = invoke[V.GoalPhaseAdvanceResult]("advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(ids.head.value))
        val afterfirst = ids.map(current)
        val second = invoke[V.GoalPhaseAdvanceResult]("advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(ids.last.value))
        val firstagain = invoke[V.GoalPhaseAdvanceResult]("advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(ids.head.value))
        Then("each exposes only its own assigned work and leaves the other execution's progression untouched")
        first.sessionId shouldBe ids.head.value
        second.sessionId shouldBe ids.last.value
        firstagain.sessionId shouldBe ids.head.value
        Vector(first, second, firstagain).foreach { value =>
          value.kind shouldBe "Request"
          value.submitSessionId shouldBe Some(value.sessionId)
          value.work.get.kind shouldBe "ImplementStep"
          value.work.get.selectedPlan.identity shouldBe plan.identity
          var actual = Option.empty[WorkflowInteraction[S.Request, Nothing]]
          prepare(call => call.goalWorkC(InstanceIdentity(value.sessionId)).map(work => { actual = Some(work); () }))
          _assert_issued_context(value.issuedWorkContext, actual.get, "implementation", S.inputCodec)
        }
        firstagain.issuedWorkContext shouldBe first.issuedWorkContext
        first.issuedWorkContext.get.handle.getString("instanceIdentity") should not be
          second.issuedWorkContext.get.handle.getString("instanceIdentity")
        afterfirst.last.revision shouldBe initial.last.revision
        current(ids.head).revision shouldBe afterfirst.head.revision
      }
    }
    for ((withDecisions, mapped, boundary) <- Vector((false, false, false), (true, false, false),
      (true, true, false), (true, true, true))) {
    "Public Goal review and closing authority preparation" should afterWord(
      "in spec:native-host-review-preparation, example:E1, rules:R1,R3,R4,R5, phase:6; spec:native-host-authority-preparation, example:E5, rules:R1,R4,R5, phase:6; spec:native-host-authority-preparation, example:E6, rules:R1,R4,R5, phase:6" +
        (if (mapped) "; spec:goal-phase-native-execution, example:E5,E6, rules:R1,R2,R3,R4,R5,R6, phase:6; spec:native-host-execution, example:E7,E8,E9, rules:R1,R2,R3,R4,R5, phase:6" else "") +
        (if (boundary) "; spec:goal-phase-acceptance-boundary, example:E7,E8,E9, rules:R1,R2,R3,R4,R5,R6, phase:6" else "")
    ) {
    (if (boundary) "close the configured fixture Phase with saved explicit exclusions and independently prepared bounded repair"
      else if (mapped) "close the mapped fixture Phase with saved dispositions, replacement mapping and verified Human resumption"
      else if (withDecisions) "close the whole ordered Phase after verified Human resumption of implementation, review, repair and Phase review"
      else "close the whole ordered two-Step Phase after assigned repair, checks, reviews and actual repository closure") in {
      Given("Spec: docs/spec/native-host-review-preparation.md; Rules: R1,R3,R4,R5; Examples: E1,E5; Spec: docs/spec/native-host-authority-preparation.md; Rules: R1,R4,R5; Examples: E5,E6; a clean owned Git repository with a tracked normal SBT build and a specification-owned two-Step plan")
      val workroot = Files.createDirectories(Path.of("target/sm-workflow/work/direct-repository-operation").toAbsolutePath)
      val root = Files.createTempDirectory(workroot, "episode-")
      val original = ThreeWorkflowNormalHostFixture.definition(root)
      val paths = Vector("one.scala", "two.scala")
      val classification = W.Assignment(
        W.Classification(W.Kind.CODING, W.Complexity.SIMPLE, W.Domain.PROGRAMMING, W.Footprint.SMALL),
        W.Classification(W.Kind.CODING, W.Complexity.COMPLEX, W.Domain.PROGRAMMING, W.Footprint.LARGE),
        W.Classification(W.Kind.REVIEW, W.Complexity.STANDARD, W.Domain.ENGINEERING, W.Footprint.SMALL))
      val phaseClassification = W.Classification(W.Kind.REVIEW, W.Complexity.COMPLEX, W.Domain.ENGINEERING, W.Footprint.LARGE)
      val steps = Vector(O.Step("step-1", "Implement first boundary", Vector(paths(0)), Vector.empty, Vector("test"), Some(classification)),
        O.Step("step-2", "Implement second boundary", Vector(paths(1)), Vector("step-1"), Vector("test"), Some(classification)))
      val project = B.Project(Vector(B.Condition("behavior", "all", true, "Implement the selected behavior"),
        B.Condition("extra-robustness", "all", true, "Add additional robustness"),
        B.Condition("phase-closure", "phase", true, "Review complete ordered Phase closure")))
      val adoption = B.Adoption("Implement the selected behavior; extra robustness is excluded from this task",
        Vector(B.Condition("extra-robustness", "all", false, "Keep extra robustness advisory")))
      val selectedplan = O.Plan("selected-two-step-plan", "1", original.goal.input.phaseIdentity,
        original.goal.input.repositoryIdentity, steps, Vector("test"), repairLimit = 1,
        phaseReviewClassification = Some(phaseClassification), acceptance = if (boundary) Some(adoption) else None)
      val plan = take(B.captureC(if (boundary) Some(project) else None, selectedplan))
      val host = original.copy(goal = original.goal.copy(candidatePaths = paths,
        closure = original.goal.closure.copy(programs = Vector(
          GoalPhaseNativeClosure.StepProgram("step-1", 1), GoalPhaseNativeClosure.StepProgram("step-2", 2)))))
      val selection = _goal_selection_(host.goal.input, "selected-goal", "direct-goal-start")
      var execution = Option.empty[InstanceIdentity]
      def identity: InstanceIdentity = execution.get
      Given("advance contains unused diagnostics and caller hints but only its actual instance is consumed")
      def advance: List[Property] = NativeWorkflowOperationInputFixture.advance(identity.value) ++ List(
        Property("stage", "Completed", None), Property("argv", Vector("must-not-execute"), None),
        Property("diagnostics", io.circe.Json.obj("expectedRevision" -> io.circe.Json.fromInt(999)), None))
      var assigned = Option.empty[WorkflowInteraction[S.Request, Nothing]]
      var humancontext = Option.empty[V.WorkflowHostHumanContext]
      var reviewcontext = Option.empty[V.WorkflowHostReviewContext]
      var saved = Option.empty[InstanceRecord]
      var firstbase = Option.empty[O.Snapshot]
      var completed = Vector.empty[O.ClosedStep]
      // These configurations and observations belong to this specification.
      // They do not identify real task executors or grant reviewer/commit authority.
      val routine1 = WorkflowConcreteExecutionProfile("spec-routine-1", "spec-provider", "spec-routine-model-1", Map("effort" -> "bounded"))
      val routine2 = routine1.copy(identity = "spec-routine-2", model = "spec-routine-model-2")
      val current2 = routine1.copy(identity = "spec-current-2", model = "spec-current-model-2")
      val deep = routine1.copy(identity = "spec-deep", model = "spec-deep-model")
      val review = routine1.copy(identity = "spec-review", model = "spec-review-model")
      val reviewalternate = review.copy(identity = "spec-review-alternate", model = "spec-review-alternate-model")
      val critical = routine1.copy(identity = "spec-critical", model = "spec-critical-model")
      def _profile_json_(p: WorkflowConcreteExecutionProfile): io.circe.Json = io.circe.Json.obj(
        "identity" -> io.circe.Json.fromString(p.identity), "provider" -> io.circe.Json.fromString(p.provider),
        "model" -> io.circe.Json.fromString(p.model),
        "controls" -> io.circe.Json.obj(p.controls.toVector.map((key, value) => key -> io.circe.Json.fromString(value))*))
      def _episode_parameters_(session: String): Map[String, String] = if (!mapped) Map.empty else {
        val first = session == "issue"
        val omitroutine = Set("pending-replacement", "review-repair")(session)
        val routine = if (first) routine1 else routine2
        val classes = Vector("CodingProgrammingDeep" -> deep.identity, "ReviewStandard" -> review.identity,
          "ReviewCritical" -> critical.identity) ++ (if (omitroutine) Vector.empty else Vector("CodingProgrammingRoutine" -> routine.identity))
        val profiles = io.circe.Json.obj("schemaVersion" -> io.circe.Json.fromString(WorkflowExecutionProfileParameters.schemaVersion),
          "identity" -> io.circe.Json.fromString("spec-goal-mapping"), "revision" -> io.circe.Json.fromString(if (first) "1" else "2"),
          "profiles" -> io.circe.Json.fromValues(Vector(routine, deep, review, critical, reviewalternate).map(_profile_json_)),
          "classes" -> io.circe.Json.fromValues(classes.map((name, profile) => io.circe.Json.obj(
            "reasoningClass" -> io.circe.Json.fromString(name), "profile" -> io.circe.Json.fromString(profile)))),
          "fallbacks" -> io.circe.Json.fromValues(if (!boundary) Vector.empty else Vector(io.circe.Json.obj(
            "reasoningClass" -> io.circe.Json.fromString("ReviewStandard"),
            "profile" -> io.circe.Json.fromString(reviewalternate.identity)))))
        val origin = io.circe.Json.obj("schemaVersion" -> io.circe.Json.fromString(GoalPhaseNativeExecution.schemaVersion),
          "current" -> io.circe.Json.obj("contextIdentity" -> io.circe.Json.fromString(if (first) "spec-origin-1" else "spec-origin-2"),
            "profile" -> _profile_json_(if (first) routine1 else current2)),
          "preference" -> io.circe.Json.obj("forceInline" -> io.circe.Json.False, "isolateContext" -> io.circe.Json.False))
        val parameters = Map(WorkflowExecutionProfileParameters.declaration.name -> profiles.noSpaces,
          GoalPhaseNativeExecution.declaration.name -> origin.noSpaces)
        if (!boundary) parameters else {
          val current = if (first) project else B.Project(Vector(
            B.Condition("later-host-condition", "all", true, "A later task default must not replace the saved boundary")))
          parameters.updated(B.declaration.name, current.asJson.mapObject(_.add("schemaVersion", io.circe.Json.fromString(B.schemaVersion))).noSpaces)
        }
      }
      var expectedissues = Map.empty[String, (ExecutionDisposition, String)]
      var hostsessions = Map.empty[String, NativeWorkflowHostExecution.Prepared]
      var usedreviewalternate = false
      val sessions = Vector("issue") ++ (if (mapped) Vector("pending-replacement") else Vector.empty) ++
        Vector("implement-1", "review-repair", "repair", "review-1", "implement-2", "review-2", "phase-review", "repository-close")
      for (session <- sessions) _with_runtime_(root, "Goal", Some(host), _episode_parameters_(session)) { (owner, component, actor, prepare) =>
        val invocationcontext = actor
        Given("every normal episode uses the actual invocation session across managed SQLite runtimes")
        def _call_(operation: String, properties: List[Property]) = owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "GoalPhaseService", operation = operation,
            properties = properties), invocationcontext)
        def _invoke_[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
          read_value[A](take(_call_(operation, properties)))
        def _current_(): InstanceRecord = {
          var state = Option.empty[InstanceRecord]
          prepare(call => call.goalCurrentC(identity).map(value => { state = Some(value); () }))
          state.get
        }
        def _assert_unchanged_(before: InstanceRecord): Unit = {
          val actual = _current_()
          actual.copy(definition = before.definition) shouldBe before
          Vector(actual.definition.workflowIdentity, actual.definition.workflowRevision,
            actual.definition.sourceCorrelation, actual.definition.producerAbiIdentity,
            actual.definition.workflowAbiIdentity, actual.definition.bootstrapAbiIdentity,
            actual.definition.producerRevision) shouldBe
            Vector(before.definition.workflowIdentity, before.definition.workflowRevision,
              before.definition.sourceCorrelation, before.definition.producerAbiIdentity,
              before.definition.workflowAbiIdentity, before.definition.bootstrapAbiIdentity,
              before.definition.producerRevision)
        }
        def _next_(): WorkflowInteraction[S.Request, Nothing] = {
          Given("O-Goal: the actual pending native Goal and its selected business plan, without reconstructed work")
          When("normal Advance displays the actual owning WorkOrder and its declared issued context")
          val response = _invoke_[V.GoalPhaseAdvanceResult]("advanceGoalPhase", advance)
          Then("the declared context preserves that issue's mode, native requirement, identities and completion route")
          response.kind shouldBe "Request"
          response.sessionId shouldBe identity.value
          response.submitSessionId shouldBe Some(identity.value)
          response.work.get.selectedPlan.identity shouldBe plan.identity
          var work = Option.empty[WorkflowInteraction[S.Request, Nothing]]
          prepare(call => call.goalWorkC(identity).map(value => { work = Some(value); () }))
          response.work.get.kind shouldBe _input_(work.get).kind
          response.work.get.stepIdentity shouldBe _input_(work.get).stepIdentity
          if (boundary) {
            Given("E8: Start captured the original defaults and explicit exclusion before later host settings changed")
            When("this reopened episode projects the actual saved issued Plan")
            val savedplan = _input_(work.get).selectedPlan
            Then("public and internal Plans retain original scopes, requirements, provenance and Usage Contract")
            savedplan shouldBe plan
            savedplan.effectiveAcceptance.get.conditions.map(c => (c.identity, c.scope, c.required, c.source)) shouldBe
              Vector(("behavior", "all", true, "project"), ("extra-robustness", "all", false, "adopted"),
                ("phase-closure", "phase", true, "project"))
            B.internalAdoption(response.work.get.selectedPlan.acceptance) shouldBe Some(adoption)
            B.internalEffective(response.work.get.selectedPlan.effectiveAcceptance) shouldBe plan.effectiveAcceptance
            response.work.get.decisionContext.map(c => B.internalReport(c.requestingResult.acceptance)) shouldBe
              _input_(work.get).decisionContext.map(_.requestingResult.acceptance)
          }
          val mode = _input_(work.get).kind match {
            case "ImplementStep" | "RepairStep" => "implementation"
            case "ReviewStep" | "ReviewPhase" => "review"
            case other => fail("Unexpected issued Goal kind: " + other)
          }
          _assert_issued_context(response.issuedWorkContext, work.get, mode, S.inputCodec)
          if (mapped) {
            Given("E7,E8: the actual public issued DTO and its first-issue canonical Component snapshot, or the original retained host session")
            val issued = response.issuedWorkContext.get
            val key = issued.continuationId
            When("the host prepares only a new continuation or restores its retained session during redisplay")
            val prepared = hostsessions.get(key) match {
              case Some(retained) => take(NativeWorkflowHostExecution.restoreC(issued,
                take(H.decodeC(take(H.encodeC(retained.session))))))
              case None =>
                val mapping = take(WorkflowExecutionProfileParameters.resolveC(component.initializationParameters)).get
                val origin = take(GoalPhaseNativeExecution.resolveOriginC(component.initializationParameters)).get.current
                take(NativeWorkflowHostExecution.prepareC(issued, mapping, origin))
            }
            hostsessions = hostsessions.updated(key, prepared)
            Then("the host retains the original public scope and native reference without re-resolving pending work")
            prepared.issued shouldBe issued
            prepared.session.workReference.continuationId.value shouldBe key
            prepared.session.workReference.expectedRevision.value shouldBe issued.expectedRevision
            prepared.session.requirement shouldBe work.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].requirement
            if (session == "issue") {
              Then("first Routine INLINE selects the actual origin and requires no child or alternate")
              prepared.session.primary shouldBe routine1
              prepared.session.alternate shouldBe None
              prepared.session.requirement.disposition.get.placement shouldBe ExecutionPlacement.Inline
            }
          }
          reviewcontext = response.work.get.review.flatMap(_.hostContext)
          if (_input_(work.get).kind.startsWith("Review")) reviewcontext should not be empty
          else reviewcontext shouldBe empty
          work.get
        }
        def _question_(): WorkflowInteraction[GoalPhaseOrderedHumanDecision.Input, Nothing] = {
          Given("O-Goal: a native ordered Human question after the assigned semantic result")
          When("normal Advance displays that question without a WorkOrder context")
          val response = _invoke_[V.GoalPhaseAdvanceResult]("advanceGoalPhase", advance)
          Then("Human retains its independent question context and has no issued-work view")
          response.kind shouldBe "Human"
          response.issuedWorkContext shouldBe None
          response.human.get.work.kind shouldBe _input_(assigned.get).kind
          humancontext = response.human.get.hostContext
          humancontext should not be empty
          var question = Option.empty[WorkflowInteraction[GoalPhaseOrderedHumanDecision.Input, Nothing]]
          prepare(call => call.goalQuestionC(identity).map(value => { question = Some(value); () }))
          if (boundary) {
            Given("E8: the current configured Human question and retained decision-needed report")
            When("the normal public Human view projects the saved business values")
            val input = question.get.current.asInstanceOf[WorkflowContinuation.Decision[GoalPhaseOrderedHumanDecision.Input]].request.input.get.value
            Then("the public effective boundary and requesting report retain their complete typed meaning")
            B.internalEffective(response.human.get.work.selectedPlan.effectiveAcceptance) shouldBe plan.effectiveAcceptance
            B.internalReport(response.human.get.requestingResult.acceptance) shouldBe input.result.acceptance
          }
          question.get
        }
        def _input_(value: WorkflowInteraction[S.Request, Nothing]): S.Request =
          value.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value
        def _result_(outcome: String, findings: Vector[String] = Vector.empty): ContinuationResult[S.Result] = {
          val work = assigned.get
          val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request
          val input = order.input.get.value
          val reference = ContextReference("specification-" + input.kind, input.sourceRevision)
          val evidence = if (!mapped) ExecutionEvidence(Vector(reference), Some(
            if (input.kind.startsWith("Review")) "specification-reviewer" else "participant")) else {
            Given("E7,E9: the retained actual public issue and specification-owned actual provider observations, independent of semantic result representation")
            val key = order.continuationId.value
            val retained = hostsessions(key)
            val before = _current_()
            When("the host records actual operational completion once, with a single configured unavailable review fallback when selected")
            val completed = if (retained.session.completionObservationC.toOption.nonEmpty) retained else {
              val selected = if (boundary && !usedreviewalternate && input.kind == "ReviewStep") {
                val unavailable = take(retained.recordC(retained.session.nextSelection.get,
                  H.Outcome(H.Status.Unavailable, H.Reason.ProviderUnavailable, "Specification-owned primary review provider was unavailable")))
                Then("only its explicit alternate is selected while the actual WorkOrder and native pending revision remain unchanged")
                unavailable.session.nextSelection.get.profile shouldBe reviewalternate
                unavailable.session.workReference shouldBe retained.session.workReference
                unavailable.session.requirement shouldBe retained.session.requirement
                _assert_unchanged_(before)
                usedreviewalternate = true
                unavailable
              } else retained
              val selection = selected.session.nextSelection.get
              val disposition = selected.session.requirement.disposition.get
              val actual = WorkflowExecutorDescriptor(if (disposition.placement == ExecutionPlacement.Inline)
                selected.session.origin.contextIdentity else "spec-executor-" + key, selection.profile)
              When("the specification-owned actual selected executor returns its assigned work")
              take(selected.recordC(selection, H.Outcome(H.Status.Completed, H.Reason.Completed,
                "Specification-owned actual executor returned the assigned semantic work", Some(actual))))
            }
            hostsessions = hostsessions.updated(key, completed)
            val publicevidence = take(completed.completionEvidenceC(Vector(V.WorkflowArtifactReference(reference.identity, reference.revision))))
            val actual = publicevidence.execution.get
            Then("only final COMPLETED supplies execution evidence; it neither submits the semantic result nor grants independent review")
            completed.session.nextSelection shouldBe None
            completed.session.attempts.last.outcome.status shouldBe H.Status.Completed
            publicevidence.workerIdentity shouldBe Some(actual.executorContextIdentity)
            _assert_unchanged_(before)
            ExecutionEvidence(Vector(reference), publicevidence.workerIdentity, publicevidence.modelIdentity,
              execution = Some(ExecutionObservation(ExecutionPlacement.values.find(_.value == actual.placement).get,
                actual.originContextIdentity, actual.executorContextIdentity, actual.profileIdentity)))
          }
          val report = input.selectedPlan.effectiveAcceptance.map { effective =>
            B.Report(B.applicable(effective, input.kind).map { condition =>
              val status = if (!condition.required) "unsatisfied" else if (outcome == "decision-needed") "not-evaluated"
                else if (outcome == "repair" && condition.identity == "behavior") "unsatisfied" else "satisfied"
              val detail = if (condition.required && status == "unsatisfied") findings.head
                else if (condition.required) "Observed assigned " + condition.identity
                else "Additional robustness is outside the adopted task"
              B.Observation(condition.identity, status, detail)
            }, Vector("Consider extra robustness as a separate future task"))
          }
          ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision, order.context.snapshot,
            TypedValue(S.resultCodec.typeIdentity, S.Result(outcome, "Specification-owned response to only the assigned work", findings, report)),
            reference, Vector.empty, evidence)
        }
        def _complete_(result: ContinuationResult[S.Result]): Unit = {
          if (boundary && !_input_(assigned.get).kind.startsWith("Review") && result.result.value.outcome == "completed") {
            Given("E7: configured pending implementation or repair, with the actual mapped execution observation")
            val before = _current_()
            val value = result.result.value
            val failed = value.acceptance.get.copy(conditions = value.acceptance.get.conditions.map(o =>
              if (o.identity == "behavior") o.copy(status = "unsatisfied", detail = "Required behavior is missing") else o))
            Vector(value.copy(acceptance = None), value.copy(acceptance = Some(failed))).foreach { invalid =>
              When("normal typed Submit receives a missing report or an unsatisfied required condition")
              val refused = _call_("submitGoalPhaseWorkResult", take(NativeWorkflowOperationInputFixture.submissionC(
                result.copy(result = TypedValue(S.resultCodec.typeIdentity, invalid)))))
              Then("quality admission fails without changing pending state, history, generation or repair allowance")
              refused.isSuccess shouldBe false
              refused.toString should include("GoalPhase acceptance")
              _assert_unchanged_(before)
            }
          }
          if (mapped) {
            Given("E6: an actual pending mapped issue and specification-owned missing or contradictory execution observations")
            val before = _current_()
            val valid = result.evidence.execution.get
            val contradictory = valid.copy(executorContextIdentity =
              if (valid.placement == ExecutionPlacement.Inline) "spec-distinct-executor" else valid.originContextIdentity)
            val invalid = Vector(result.copy(evidence = result.evidence.copy(execution = None)),
              result.copy(evidence = result.evidence.copy(execution = Some(contradictory))))
            When("normal Submit receives each observation that cannot satisfy the saved placement relationship")
            invalid.foreach { value =>
              val rejected = _call_("submitGoalPhaseWorkResult", take(NativeWorkflowOperationInputFixture.submissionC(value)))
              Then("native admission rejects without revision, progression, history or suspension changes")
              rejected.isSuccess shouldBe false
              _assert_unchanged_(before)
            }
          }
          Given("a named business result with inert command/grant annotations, not a native routing envelope")
          val value = result.result.value
          val plain = io.circe.Json.obj("outcome" -> io.circe.Json.fromString(value.outcome),
            "summary" -> io.circe.Json.fromString(value.summary), "findings" -> io.circe.Json.fromValues(value.findings.map(io.circe.Json.fromString)),
            "command" -> io.circe.Json.True, "grant" -> io.circe.Json.True)
          val business = value.acceptance.fold(plain)(report => plain.mapObject(_.add("acceptance", report.asJson)))
          val properties = take(NativeWorkflowOperationInputFixture.submissionC(result)).map {
            case p if p.name == "result" => Property("result", business, None)
            case p => p
          } :+ Property("diagnostics", true, None)
          When("the actual result commits without control echoes or an unused-field certificate")
          val acknowledgement = _invoke_[V.GoalPhaseWorkSubmissionResult]("submitGoalPhaseWorkResult", properties)
          Then("the response truthfully reports acceptance without eagerly issuing the next work")
          acknowledgement.sessionId shouldBe identity.value
          acknowledgement.accepted shouldBe true
          acknowledgement.message should include(value.summary)
          if (boundary && Set("completed", "accept")(value.outcome)) {
            Given("E9: the accepted configured work still contains excluded observations and a nonblocking idea")
            When("normal Submit has admitted that honest scoped report")
            Then("excluded unsatisfied robustness and the advisory remain accepted without becoming findings")
            value.acceptance.get.conditions.find(_.identity == "extra-robustness").get.status shouldBe "unsatisfied"
            value.acceptance.get.advisories shouldBe Vector("Consider extra robustness as a separate future task")
            value.findings shouldBe empty
          }
          if (mapped) {
            Then("the accepted mapped result has cleared its suspension and leaves next work to separate Advance")
            val acknowledged = _current_()
            acknowledged.lifecycle shouldBe Lifecycle.Active
            acknowledged.suspension shouldBe empty
          }
        }
        def _retain_(next: WorkflowInteraction[S.Request, Nothing]): Unit = {
          Given("the actual owning Advance WorkOrder and the specification's expected issuance episode")
          val order = next.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]]
          val (reasoning, semanticClass, reviewRequired) = order.request.input.get.value.kind match {
            case "ImplementStep" => (ReasoningLevel.Routine, "CodingProgrammingRoutine", false)
            case "RepairStep" => (ReasoningLevel.Deep, "CodingProgrammingDeep", false)
            case "ReviewStep" => (ReasoningLevel.Standard, "ReviewStandard", true)
            case "ReviewPhase" => (ReasoningLevel.Critical, "ReviewCritical", true)
            case other => fail("Unexpected assigned semantic work: " + other)
          }
          When("its selected requirement is consumed without regenerating the WorkOrder")
          Then("the exact semantic class, reasoning, review responsibility and saved disposition match that issuance")
          order.requirement.reasoning shouldBe reasoning
          order.requirement.reasoningClass shouldBe Some(ReasoningClassReference(semanticClass))
          order.requirement.reviewRequired shouldBe reviewRequired
          if (mapped) {
            val expected = expectedissues.getOrElse(order.request.continuationId.value, {
              val first = session == "issue"
              val placement = if (first && semanticClass == "CodingProgrammingRoutine") ExecutionPlacement.Inline else ExecutionPlacement.Delegated
              val independence = if (reviewRequired) ExecutionIndependence.Required else ExecutionIndependence.Optional
              val profile = semanticClass match {
                case "CodingProgrammingRoutine" => if (first) routine1.identity else routine2.identity
                case "CodingProgrammingDeep" => deep.identity
                case "ReviewStandard" => review.identity
                case "ReviewCritical" => critical.identity
              }
              (ExecutionDisposition(placement, independence, if (first) "spec-origin-1" else "spec-origin-2"), profile)
            })
            order.requirement.disposition shouldBe Some(expected._1)
            hostsessions(order.request.continuationId.value).session.primary.identity shouldBe expected._2
            expectedissues = expectedissues.updated(order.request.continuationId.value, expected)
          } else order.requirement.disposition shouldBe None
          assigned = Some(next)
          saved = Some(_current_())
        }
        def _prepare_review_(result: ContinuationResult[S.Result]): Unit = {
          Given("the actual public issued review context and explicit specification-owned result, reference, worker, issuer and event time")
          val value = result.result.value
          val reference = V.WorkflowArtifactReference(result.resultReference.identity, result.resultReference.revision)
          val execution = result.evidence.execution.map(e => V.WorkflowExecutionObservation(e.placement.value,
            e.originContextIdentity, e.executorContextIdentity, e.profileIdentity))
          val evidence = V.WorkflowExecutionEvidence(Vector(reference), result.evidence.workerIdentity, None, execution)
          if (mapped) {
            Given("E6: a valid fixture execution observation for Required review with no independently prepared review evidence")
            val before = _current_()
            When("the mapped review result arrives before separate host review preparation")
            val missing = _call_("submitGoalPhaseWorkResult", take(NativeWorkflowOperationInputFixture.submissionC(result)))
            Then("placement evidence alone cannot admit independent review or change the pending issue")
            missing.isSuccess shouldBe false
            _assert_unchanged_(before)
          }
          When("the public passive preparation produces a managed record input and the specification provisions it through existing Create")
          prepare(call => for {
            prepared <- NativeHostReviewPreparation.goalC(reviewcontext.get,
              V.GoalPhaseWorkResult(value.outcome, value.summary, value.findings, B.publicReport(value.acceptance)), reference, evidence,
              "normal-development-host", call.executionContext.clock.instant().minusSeconds(60))
            _ <- call.createC("host_evidence", prepared.recordIdentity, prepared.record)
          } yield ())
          Then("preparation has supplied only specification input and the pending native revision is unchanged")
          _current_().revision shouldBe saved.get.revision
        }
        def _human_roundtrip_(): Unit = {
          Given("Spec: docs/spec/native-host-human-preparation.md; Example: E6; Rules: R1,R3,R4,R5; the actual assigned fixture work and explicit decision-needed response")
          val previous = assigned.get
          val prior = _input_(previous)
          val requesting = _result_("decision-needed")
          if (prior.kind.startsWith("Review")) _prepare_review_(requesting)
          When("only the assigned " + prior.kind + " result reports a semantic decision, not consent or new repair authority")
          _complete_(requesting)
          val question = _question_()
          val decision = question.current.asInstanceOf[WorkflowContinuation.Decision[GoalPhaseOrderedHumanDecision.Input]].request
          Then("the issued Human question retains the original work and supplied result reference")
          decision.input.get.value.work shouldBe prior
          decision.input.get.value.requestingResult shouldBe requesting.resultReference
          decision.input.get.value.result shouldBe requesting.result.value
          val pending = _current_()
          pending.currentProgression shouldBe Some(ProgressionReference("phase.Decision"))
          pending.suspension should not be empty
          val reference = ContextReference("specification-human-" + prior.kind, decision.expectedRevision.value)
          val answer = ContinuationResult(question.handle, decision.runId, decision.continuationId, decision.expectedRevision,
            decision.context.snapshot, TypedValue(GoalPhaseOrderedHumanDecision.answerCodec.typeIdentity,
              GoalPhaseOrderedHumanDecision.Answer("ResumeAssignedWork", "Continue only the same original assigned semantic work")),
            reference, Vector.empty, ExecutionEvidence(Vector(reference), Some("specification-human")))
          Given("the actual Human answer carries only choice/rationale plus an inert command annotation")
          val wire = take(NativeWorkflowOperationInputFixture.submissionC(answer)) :+
            Property("command", true, None)
          When("an answer has no separately prepared host Human evidence")
          _call_("submitGoalPhaseOrderedDecision", wire).isSuccess shouldBe false
          Then("the actual question, repository and original allowance remain unchanged")
          _current_().revision shouldBe pending.revision
          Given("Spec: docs/spec/native-host-human-preparation.md; Example: E6; Rules: R4,R5; explicit specification-owned Human evidence bound to that exact question and answer")
          When("public Human preparation consumes the retained issued context and the supplied fixture answer before managed Create")
          prepare(call => for {
            prepared <- NativeHostHumanPreparation.goalC(humancontext.get,
              V.GoalPhaseOrderedDecisionAnswer(answer.result.value.choice, answer.result.value.rationale),
              V.WorkflowArtifactReference(reference.identity, reference.revision),
              V.WorkflowExecutionEvidence(Vector.empty, answer.evidence.workerIdentity, None, None),
              selection.participantIdentity, "normal-development-host", call.executionContext.clock.instant().minusSeconds(60))
            _ <- call.createC("host_evidence", prepared.recordIdentity, prepared.record)
          } yield ())
          Then("passive preparation and fixture provisioning preserve the pending native revision")
          _current_().revision shouldBe pending.revision
          When("only the public ordered Decision completion records the verified answer")
          val acknowledgement = _invoke_[V.GoalPhaseOrderedDecisionSubmissionResult]("submitGoalPhaseOrderedDecision", wire)
          Then("normal Submit acknowledges only the supplied ResumeAssignedWork answer")
          acknowledgement.accepted shouldBe true
          acknowledgement.message should include("ResumeAssignedWork")
          val answered = _current_()
          answered.suspension shouldBe empty
          answered.history.size shouldBe pending.history.size + 1
          When("owning advance issues only fresh-generation work for the same semantic task")
          _retain_(_next_())
          Then("the next assignment preserves the original semantic task and allowance")
          val resumed = _input_(assigned.get)
          resumed.kind shouldBe prior.kind
          resumed.stepIdentity shouldBe prior.stepIdentity
          resumed.selectedPlan shouldBe prior.selectedPlan
          resumed.originalBase shouldBe prior.originalBase
          resumed.completed shouldBe prior.completed
          resumed.repairDirective shouldBe prior.repairDirective
          resumed.generation shouldBe prior.generation + 1
          resumed.decisionContext.get.requestingResult shouldBe requesting.result.value
          resumed.decisionContext.get.choice shouldBe "ResumeAssignedWork"
          resumed.selectedPlan.repairLimit shouldBe 1
          if (boundary) {
            Given("E8: explicit Human resumption of the same configured semantic task")
            When("the resumed public Human and Work views consume the retained report and boundary")
            Then("the saved original effective conditions and decision-needed report survive without host re-resolution")
            resumed.selectedPlan.effectiveAcceptance shouldBe plan.effectiveAcceptance
            resumed.decisionContext.get.requestingResult.acceptance shouldBe requesting.result.value.acceptance
          }
        }
        if (session == "issue") {
          prepare(call => for {
            _ <- call.setupC(_setup_commands_(root, "Goal").size)
            _ <- call.createC("goal_selection", NativeWorkflowStartBusinessKey(NativeWorkflowStartInput.Goal(host.goal.input), selection.participantIdentity), Record.data(ProfileSelectionJsonV1.storageField ->
              take(ProfileSelectionJsonV1.encodeStorageC(selection))))
            _ <- call.createC("goal_plans", selection.selectionId, Record.data(GoalPhaseSelectedPlanJsonV1.storageField ->
              take(GoalPhaseSelectedPlanJsonV1.encodeStorageC(GoalPhaseNativeRun.SelectedPlan(
                selection.selectionId, selection.participantIdentity, selectedplan)))))
          } yield ())
          When("public Start saves the complete selected plan and captures the actual clean base")
          val started = _invoke_[V.GoalPhaseStartResult]("startGoalPhase", NativeWorkflowOperationInputFixture.goal(host.goal.input))
          execution = Some(InstanceIdentity(started.sessionId))
          When("advance consumes its actual instance while ignoring caller stage argv and diagnostic revision")
          _retain_(_next_())
          Then("only the original plan's first implementation is issued rather than the caller's Completed stage")
          _input_(assigned.get).selectedPlan shouldBe plan
          _input_(assigned.get).kind shouldBe "ImplementStep"
          _input_(assigned.get).stepIdentity shouldBe Some("step-1")
          firstbase = Some(_input_(assigned.get).originalBase)
          When("the actual target instance is changed while the unused diagnostic fields are retained")
          val foreign = advance.map {
            case p if p.name == "sessionId" => Property("sessionId", "foreign-instance", None)
            case p => p
          }
          val rejected = _call_("advanceGoalPhase", foreign)
          Then("the actual native Start lookup refuses the absent instance without changing original pending work")
          rejected.isSuccess shouldBe false
          rejected.toString should include("no actual native ordered Start")
          _current_().revision shouldBe saved.get.revision
        } else if (session == "pending-replacement") {
          Given("E5: the saved first Inline issue and a separately booted revision2 mapping without its Routine class")
          val before = _current_()
          val previous = assigned.get
          val key = previous.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.continuationId.value
          val originalsession = hostsessions(key).session
          When("normal Advance reads the pending issue under the new origin and incomplete-for-old-work mapping")
          _retain_(_next_())
          Then("the actual saved WorkOrder, original origin and native history remain unchanged")
          assigned.get shouldBe previous
          _assert_unchanged_(before)
          hostsessions(key).session.primary shouldBe originalsession.primary
          hostsessions(key).session.mappingRevision shouldBe "1"
          assigned.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].requirement.disposition shouldBe
            Some(ExecutionDisposition(ExecutionPlacement.Inline, ExecutionIndependence.Optional, "spec-origin-1"))
          for ((status, reason) <- Vector(H.Status.Blocked -> H.Reason.AuthorityBlocked,
              H.Status.Failed -> H.Reason.UncertainExecution, H.Status.Declined -> H.Reason.CapabilityDeclined)) {
            Given("E8: an independent specification branch of the actual retained pending issue with operational " + status.value)
            val branch = take(NativeWorkflowHostExecution.restoreC(hostsessions(key).issued,
              take(H.decodeC(take(H.encodeC(originalsession))))))
            When("the host records that actual noncompletion and returns control without Submit or Advance")
            val stopped = take(branch.recordC(branch.session.nextSelection.get, H.Outcome(status, reason, "Specification-owned terminal host outcome")))
            Then("no semantic evidence or next attempt is produced and the connected pending native state remains unchanged")
            stopped.session.nextSelection shouldBe None
            stopped.completionEvidenceC(Vector(V.WorkflowArtifactReference("noncompletion", "1"))) shouldBe a[Consequence.Failure[?]]
            _assert_unchanged_(before)
          }
        } else if (session == "repository-close") {
          Given("E6: accepted Phase review and the actual missing Phase authority frontier")
          When("Phase review alone leaves closure waiting for separately prepared specification-owned authority")
          val waiting = _invoke_[V.GoalPhaseAdvanceResult]("advanceGoalPhase", advance)
          val current = _current_()
          Then("the Phase remains Active at its original target without a child Start")
          waiting.kind shouldBe "Wait"
          waiting.issuedWorkContext shouldBe None
          waiting.authorityContext should not be empty
          waiting.authorityContext.get.purpose shouldBe NativeHostEvidence.Purpose.PhaseClosure.identity
          current.lifecycle shouldBe Lifecycle.Active
          current.currentProgression shouldBe Some(ProgressionReference("phase.PhaseClosing"))
          current.revision shouldBe saved.get.revision
          val input = _input_(assigned.get)
          val reviewreference = _result_("accept").resultReference
          val invocation = identity.value + ":repository-closure:" + current.revision.value
          val child = _repository_selection_(host.repository.input, "goal-child-host-entry", invocation)
          Given("the actual Phase Wait wire and explicit specification-owned reference participant issuer and historical time")
          When("the full saved Phase Wait crosses the strict generated reader")
          val decoded = take(V.GoalPhaseAdvanceResult.createC(take(new RecordDecoder().json(take(waiting.toRecord().toJsonStringC)))))
          val context = decoded.authorityContext.get
          Then("the selected context retains the current original Phase target")
          context.scope.getString("instance") shouldBe Some(identity.value)
          context.scope.getString("sourceRevision") shouldBe Some(current.revision.value.toString)
          context.scope.getString("review") shouldBe Some(reviewreference.identity)
          context.scope.getString("childInvocation") shouldBe Some(invocation)
          NativeHostAuthorityPreparationFixture.json(context.scope).hcursor.downField("candidate").get[String]("tree").toOption shouldBe
            Some(input.candidate.get.tree)
          When("public passive Phase preparation supplies only input and the specification provisions the row and independent child prerequisites")
          prepare(call => for {
            prepared <- NativeHostAuthorityPreparation.goalC(context, V.WorkflowArtifactReference("specification-phase-authority", "1"),
              "participant", "normal-development-host", call.executionContext.clock.instant().minusSeconds(60))
            _ <- call.createC("host_evidence", prepared.recordIdentity, prepared.record)
            _ <- _prepare_input_(call, host.repository.input, child, root)
          } yield ())
          Then("passive encoding and normal input Create leave the Phase revision unchanged")
          _current_().revision shouldBe current.revision
          When("owning advance calls the installed RepositorySync Operations and closes only their observed convergence")
          val outcome = _invoke_[V.GoalPhaseAdvanceResult]("advanceGoalPhase", advance)
          outcome.kind shouldBe "Terminal"
          outcome.issuedWorkContext shouldBe None
          outcome.sessionId shouldBe identity.value
          val result = outcome.completion.get
          Then("the whole selected plan and both actual Step commits appear in the durable Phase terminal")
          result.selectedPlan.identity shouldBe plan.identity
          result.selectedPlan.steps.map(_.identity) shouldBe plan.steps.map(_.identity)
          if (boundary) {
            Given("E8: whole-Phase terminal after saved configured implementation, repair and Human episodes")
            When("the completion projects the selected saved Plan")
            Then("terminal still retains the original explicit exclusion and complete effective quality boundary")
            B.internalAdoption(result.selectedPlan.acceptance) shouldBe plan.acceptance
            B.internalEffective(result.selectedPlan.effectiveAcceptance) shouldBe plan.effectiveAcceptance
          }
          result.originalBase.head shouldBe firstbase.get.head
          result.originalBase.tree shouldBe firstbase.get.tree
          result.completed.map(_.commit.identity) shouldBe completed.map(_.commit.identity)
          result.completed.size shouldBe 2
          result.repository.tip shouldBe result.reviewedCandidate.head
          result.repository.tree shouldBe result.reviewedCandidate.tree
          var observed = Option.empty[RepositorySyncManagedGitObservation.Observed]
          prepare(call => call.observeC().map(value => { observed = Some(value); () }))
          observed.get.head shouldBe result.repository.tip
          observed.get.trackingTip shouldBe Some(result.repository.tip)
          observed.get.dirty shouldBe empty
          val actual = _current_()
          actual.lifecycle shouldBe Lifecycle.Completed
          outcome.sessionId shouldBe actual.identity.value
          Files.readString(root.resolve("repository/one.scala")) shouldBe "object FirstRepaired\n"
          Files.readString(root.resolve("repository/two.scala")) shouldBe "object Second\n"
          saved = Some(actual)
        } else {
          When("a fresh runtime supplies the assigned " + session + " work for its semantic result")
          _retain_(_next_())
          if (withDecisions && Set("implement-1", "review-repair", "repair", "phase-review")(session)) _human_roundtrip_()
          val input = _input_(assigned.get)
          input.selectedPlan shouldBe plan
          input.originalBase shouldBe firstbase.get
          if (session.startsWith("implement") || session == "repair") {
            input.kind shouldBe (if (session == "repair") "RepairStep" else "ImplementStep")
            val path = if (session == "implement-2") "two.scala" else "one.scala"
            val content = if (session == "implement-2") "object Second\n" else if (session == "repair") "object FirstRepaired\n" else "object First\n"
            if (session == "repair") {
              input.repairDirective.get.findings shouldBe Vector("Repair the assigned first boundary")
              input.completed shouldBe empty
              input.generation shouldBe (if (withDecisions) 5L else 2L)
            }
            Files.writeString(root.resolve("repository").resolve(path), content)
            _complete_(_result_("completed"))
            _current_().lifecycle shouldBe Lifecycle.Active
            _retain_(_next_())
            _input_(assigned.get).kind shouldBe "ReviewStep"
            Then("the issued review carries actual SBT cwd, pass output, external PID and the selected diff")
            _assert_product_check_(root, _input_(assigned.get).review.get.checks.last.stdout)
            _input_(assigned.get).review.get.changedPaths shouldBe Vector(path)
          } else {
            input.kind shouldBe (if (session == "phase-review") "ReviewPhase" else "ReviewStep")
            val repair = session == "review-repair"
            val result = _result_(if (repair) "repair" else "accept", if (repair) Vector("Repair the assigned first boundary") else Vector.empty)
            if (boundary) {
              Given("E9: an actual independent review slot with required observations satisfied and excluded robustness unsatisfied")
              val before = _current_()
              val advisory = _result_("accept")
              val reference = ContextReference("excluded-only-" + input.kind, input.sourceRevision)
              val value = advisory.result.value.copy(outcome = "repair", findings = Vector("Add excluded extra robustness"))
              val excluded = advisory.copy(result = TypedValue(S.resultCodec.typeIdentity, value), resultReference = reference,
                evidence = advisory.evidence.copy(references = Vector(reference)))
              When("a distinct genuinely fixture-prepared review requests repair only for the excluded idea")
              _prepare_review_(excluded)
              val refused = _call_("submitGoalPhaseWorkResult", take(NativeWorkflowOperationInputFixture.submissionC(excluded)))
              Then("the quality gate rejects that evidenced proposal without consuming repair count or changing native state")
              refused.isSuccess shouldBe false
              refused.toString should include("repair findings must be exactly the ordered unsatisfied required observation details")
              _assert_unchanged_(before)
            }
            val wire = take(NativeWorkflowOperationInputFixture.submissionC(result))
            When("the named review result is submitted before provisioning any review record")
            _call_("submitGoalPhaseWorkResult", wire).isSuccess shouldBe false
            Then("review strings without separately prepared reviewer evidence append no native transition")
            _current_().revision shouldBe saved.get.revision
            _prepare_review_(result)
            _complete_(result)
            if (repair) {
              _retain_(_next_())
              _input_(assigned.get).kind shouldBe "RepairStep"
            } else if (session != "phase-review") {
              Given("E5: the actual accepted Step review and current Closing frontier without a recorded commit grant")
              val current = _current_()
              val parent = input.completed.lastOption.map(s => O.Snapshot(s.commit.identity, s.commit.tree)).getOrElse(input.originalBase)
              When("explicit Advance reaches actual missing Step authority")
              val waiting = _invoke_[V.GoalPhaseAdvanceResult]("advanceGoalPhase", advance)
              Then("the Step remains Active at the same revision and reviewed target without a commit")
              waiting.kind shouldBe "Wait"
              waiting.issuedWorkContext shouldBe None
              waiting.authorityContext should not be empty
              waiting.authorityContext.get.purpose shouldBe NativeHostEvidence.Purpose.StepClosure.identity
              _current_().lifecycle shouldBe Lifecycle.Active
              _current_().revision shouldBe current.revision
              var before = Option.empty[RepositorySyncManagedGitObservation.Observed]
              prepare(call => call.observeC().map(value => { before = Some(value); () }))
              before.get.head shouldBe parent.head
              Given("the actual closing Wait crosses strict generated JSON intake with explicit specification-owned operator metadata")
              When("the full saved Step Wait crosses the strict generated reader")
              val decoded = take(V.GoalPhaseAdvanceResult.createC(take(new RecordDecoder().json(take(waiting.toRecord().toJsonStringC)))))
              val context = decoded.authorityContext.get
              Then("the selected context retains the actual current Step parent candidate generation and review")
              context.scope.getString("instance") shouldBe Some(identity.value)
              context.scope.getString("sourceRevision") shouldBe Some(current.revision.value.toString)
              context.scope.getString("step") shouldBe input.stepIdentity
              context.scope.getString("reviewEvidence") shouldBe Some(result.resultReference.identity)
              val target = NativeHostAuthorityPreparationFixture.json(context.scope)
              target.hcursor.get[Long]("generation").toOption shouldBe Some(input.generation)
              target.hcursor.downField("parent").get[String]("head").toOption shouldBe Some(parent.head)
              target.hcursor.downField("candidate").get[String]("tree").toOption shouldBe Some(input.candidate.get.tree)
              When("public passive Step preparation supplies input for existing managed specification Create")
              prepare(call => for {
                prepared <- NativeHostAuthorityPreparation.goalC(context, V.WorkflowArtifactReference("specification-step-authority", "1"),
                  "participant", "normal-development-host", call.executionContext.clock.instant().minusSeconds(60))
                _ <- call.createC("host_evidence", prepared.recordIdentity, prepared.record)
              } yield ())
              Then("preparation and input provisioning neither commit nor Advance the native revision")
              _current_().revision shouldBe current.revision
              When("owning advance commits only the reviewed Step and selects the next ordered frontier")
              _retain_(_next_())
              completed = _input_(assigned.get).completed
              Then("the actual ordered commit establishes the reviewed tree and native parent before selecting the next work")
              completed.last.commit.parent shouldBe parent.head
              completed.last.commit.tree shouldBe input.candidate.get.tree
              completed.last.commit.identity should not be parent.head
              _current_().lifecycle shouldBe Lifecycle.Active
              if (session == "review-1") {
                completed.size shouldBe 1
                _input_(assigned.get).kind shouldBe "ImplementStep"
                _input_(assigned.get).stepIdentity shouldBe Some("step-2")
              } else {
                completed.size shouldBe 2
                _input_(assigned.get).kind shouldBe "ReviewPhase"
                Then("Phase review carries the real ordinary SBT cwd, pass output and external PID")
                _assert_product_check_(root, _input_(assigned.get).review.get.checks.last.stdout)
              }
            } else {
              saved = Some(_current_())
              _current_().lifecycle shouldBe Lifecycle.Active
            }
          }
        }
      }
    }
    }
    }
    "Public Human Defer and Cancel preparation" should afterWord("in spec:native-host-human-preparation, example:E6, rules:R1,R3,R4,R5,R6, phase:6") {
    for (choice <- Vector("Defer", "Cancel")) {
      ("retain verified Human " + choice + " without issuing work or executing repository effects") in {
        Given("a selected one-Step Phase and explicit specification-owned input")
        val parent = Files.createDirectories(Path.of("target/sm-workflow/work/direct-repository-operation").toAbsolutePath)
        val root = Files.createTempDirectory(parent, "episode-")
        val host = ThreeWorkflowNormalHostFixture.definition(root)
        val selection = _goal_selection_(host.goal.input, "human-latch", "human-latch-start")
        var execution = Option.empty[InstanceIdentity]
        def identity: InstanceIdentity = execution.get
        def advance: List[Property] = NativeWorkflowOperationInputFixture.advance(identity.value)
        val plan = O.Plan("human-latch-plan", "1", host.goal.input.phaseIdentity, host.goal.input.repositoryIdentity,
          Vector(O.Step("step-1", "Implement only the original boundary", host.goal.candidatePaths, Vector.empty, Vector("test"))),
          Vector("test"), repairLimit = 0)
        var humancontext = Option.empty[V.WorkflowHostHumanContext]
        var question = Option.empty[WorkflowInteraction[GoalPhaseOrderedHumanDecision.Input, Nothing]]
        var retained = Option.empty[V.GoalPhaseAdvanceResult]
        var saved = Option.empty[InstanceRecord]
        for (session <- Vector("issue", "answer")) _with_runtime_(root, "Goal", Some(host)) { (owner, component, actor, prepare) =>
          def invoke[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A = read_value[A](take(owner.executeOperationResponseInContext(
            Request.of(component = component.name, service = "GoalPhaseService", operation = operation,
              properties = properties), actor)))
          def loadWork(): WorkflowInteraction[S.Request, Nothing] = {
            var value = Option.empty[WorkflowInteraction[S.Request, Nothing]]
            prepare(call => call.goalWorkC(identity).map(work => { value = Some(work); () }))
            value.get
          }
          def loadQuestion(): WorkflowInteraction[GoalPhaseOrderedHumanDecision.Input, Nothing] = {
            var value = Option.empty[WorkflowInteraction[GoalPhaseOrderedHumanDecision.Input, Nothing]]
            prepare(call => call.goalQuestionC(identity).map(question => { value = Some(question); () }))
            value.get
          }
          def current(): InstanceRecord = {
            var result = Option.empty[InstanceRecord]
            prepare(call => call.goalCurrentC(identity).map(value => { result = Some(value); () }))
            result.get
          }
          if (session == "issue") {
            prepare(call => for {
              _ <- call.setupC(_setup_commands_(root, "Goal").size)
              _ <- call.createC("goal_selection", NativeWorkflowStartBusinessKey(NativeWorkflowStartInput.Goal(host.goal.input), selection.participantIdentity), Record.data(ProfileSelectionJsonV1.storageField ->
                take(ProfileSelectionJsonV1.encodeStorageC(selection))))
              _ <- call.createC("goal_plans", selection.selectionId, Record.data(GoalPhaseSelectedPlanJsonV1.storageField ->
                take(GoalPhaseSelectedPlanJsonV1.encodeStorageC(GoalPhaseNativeRun.SelectedPlan(
                  selection.selectionId, selection.participantIdentity, plan)))))
            } yield ())
            When("the public semantic completion reports only its assigned decision-needed result")
            val started = invoke[V.GoalPhaseStartResult]("startGoalPhase", NativeWorkflowOperationInputFixture.goal(host.goal.input))
            execution = Some(InstanceIdentity(started.sessionId))
            val next = invoke[V.GoalPhaseAdvanceResult]("advanceGoalPhase", advance)
            next.kind shouldBe "Request"
            next.work.get.kind shouldBe "ImplementStep"
            val work = loadWork()
            val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request
            val reference = ContextReference("need-human", "1")
            val result = ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision,
              order.context.snapshot, TypedValue(S.resultCodec.typeIdentity, S.Result("decision-needed", "Human direction required", Vector.empty)),
              reference, Vector.empty, ExecutionEvidence(Vector(reference), Some("participant")))
            invoke[V.GoalPhaseWorkSubmissionResult]("submitGoalPhaseWorkResult", take(NativeWorkflowOperationInputFixture.submissionC(result))).accepted shouldBe true
            val displayed = invoke[V.GoalPhaseAdvanceResult]("advanceGoalPhase", advance)
            Then("the pending public Human retains its actual context")
            displayed.kind shouldBe "Human"
            displayed.issuedWorkContext shouldBe None
            humancontext = displayed.human.get.hostContext
            humancontext should not be empty
            question = Some(loadQuestion())
            saved = Some(current())
          } else if (session == "answer") {
            When("a fresh runtime supplies the Human question and admits specification-owned Human evidence")
            val displayed = invoke[V.GoalPhaseAdvanceResult]("advanceGoalPhase", advance)
            Then("the pending public Human retains its actual context")
            displayed.kind shouldBe "Human"
            displayed.issuedWorkContext shouldBe None
            humancontext = displayed.human.get.hostContext
            humancontext should not be empty
            question = Some(loadQuestion())
            val decision = question.get.current.asInstanceOf[WorkflowContinuation.Decision[GoalPhaseOrderedHumanDecision.Input]].request
            val reference = ContextReference("human-latch-answer", "1")
            val answer = ContinuationResult(question.get.handle, decision.runId, decision.continuationId, decision.expectedRevision,
              decision.context.snapshot, TypedValue(GoalPhaseOrderedHumanDecision.answerCodec.typeIdentity,
                GoalPhaseOrderedHumanDecision.Answer(choice, "Do not resume this decision path")), reference,
              Vector.empty, ExecutionEvidence(Vector(reference), Some("specification-human")))
            Given("the freshly displayed context and explicit fixture Human direction")
            When("public passive preparation supplies the existing managed Create input")
            prepare(call => for {
              prepared <- NativeHostHumanPreparation.goalC(humancontext.get,
                V.GoalPhaseOrderedDecisionAnswer(answer.result.value.choice, answer.result.value.rationale),
                V.WorkflowArtifactReference(reference.identity, reference.revision),
                V.WorkflowExecutionEvidence(Vector.empty, answer.evidence.workerIdentity, None, None),
                selection.participantIdentity, "normal-development-host", call.executionContext.clock.instant().minusSeconds(60))
              _ <- call.createC("host_evidence", prepared.recordIdentity, prepared.record)
            } yield ())
            Then("preparation leaves the actual pending revision unchanged")
            current().revision shouldBe saved.get.revision
            When("normal Human Submit consumes the evidenced selected answer")
            val wire = take(NativeWorkflowOperationInputFixture.submissionC(answer))
            val acknowledgement = invoke[V.GoalPhaseOrderedDecisionSubmissionResult]("submitGoalPhaseOrderedDecision", wire)
            Then("the answer is acknowledged without implicitly advancing")
            acknowledgement.accepted shouldBe true
            acknowledgement.message should include(choice)
            val answered = current()
            answered.history.size shouldBe saved.get.history.size + 1
            answered.suspension shouldBe empty
            When("the caller explicitly Advances after acknowledgement")
            retained = Some(invoke[V.GoalPhaseAdvanceResult]("advanceGoalPhase", advance))
            retained.get.kind shouldBe "Wait"
            retained.get.issuedWorkContext shouldBe None
            Then("the answer stays latched without another question, WorkOrder or Phase completion")
            current().currentProgression shouldBe Some(ProgressionReference("phase.Decision"))
            answered.lifecycle shouldBe Lifecycle.Active
            answered.currentProgression shouldBe Some(ProgressionReference("phase.Decision"))
            saved = Some(answered)
          }
        }
      }
    }
  }
  }
}
