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
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeWork as S
import SplitPhaseExplicitPartitionFixture.take
import GoalPhaseWorkClassification as W

/** The spec owns selected input, source edits and semantic/review responses.
  * Real public Operations own all history, checks, staging, commits and closure.
  * Host evidence is explicit test data, not independent production issuance.
  * @since   Oct.  7, 2026
  * @version Oct. 12, 2026
  */
final class ThreeWorkflowGoalOperationSpec extends AnyWordSpec with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  "GoalPhase public Operations on the production three-workflow composition" should {
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
        }
        afterfirst.last.revision shouldBe initial.last.revision
        current(ids.head).revision shouldBe afterfirst.head.revision
      }
    }
    for (withDecisions <- Vector(false, true)) {
    (if (withDecisions) "close the whole ordered Phase after verified Human resumption of implementation, review, repair and Phase review"
      else "close the whole ordered two-Step Phase after assigned repair, checks, reviews and actual repository closure") in {
      Given("a clean owned Git repository and an explicitly selected two-Step plan")
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
      val plan = O.Plan("selected-two-step-plan", "1", original.goal.input.phaseIdentity,
        original.goal.input.repositoryIdentity, steps, Vector("test"), repairLimit = 1,
        phaseReviewClassification = Some(phaseClassification))
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
      var saved = Option.empty[InstanceRecord]
      var firstbase = Option.empty[O.Snapshot]
      var completed = Vector.empty[O.ClosedStep]
      val sessions = Vector("issue", "implement-1", "review-repair", "repair", "review-1", "implement-2",
        "review-2", "phase-review", "repository-close")
      for (session <- sessions) _with_runtime_(root, "Goal", Some(host)) { (owner, component, actor, prepare) =>
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
        def _next_(): WorkflowInteraction[S.Request, Nothing] = {
          val response = _invoke_[V.GoalPhaseAdvanceResult]("advanceGoalPhase", advance)
          response.kind shouldBe "Request"
          response.sessionId shouldBe identity.value
          response.submitSessionId shouldBe Some(identity.value)
          response.work.get.selectedPlan.identity shouldBe plan.identity
          var work = Option.empty[WorkflowInteraction[S.Request, Nothing]]
          prepare(call => call.goalWorkC(identity).map(value => { work = Some(value); () }))
          response.work.get.kind shouldBe _input_(work.get).kind
          response.work.get.stepIdentity shouldBe _input_(work.get).stepIdentity
          work.get
        }
        def _question_(): WorkflowInteraction[GoalPhaseOrderedHumanDecision.Input, Nothing] = {
          val response = _invoke_[V.GoalPhaseAdvanceResult]("advanceGoalPhase", advance)
          response.kind shouldBe "Human"
          response.human.get.work.kind shouldBe _input_(assigned.get).kind
          var question = Option.empty[WorkflowInteraction[GoalPhaseOrderedHumanDecision.Input, Nothing]]
          prepare(call => call.goalQuestionC(identity).map(value => { question = Some(value); () }))
          question.get
        }
        def _input_(value: WorkflowInteraction[S.Request, Nothing]): S.Request =
          value.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value
        def _result_(outcome: String, findings: Vector[String] = Vector.empty): ContinuationResult[S.Result] = {
          val work = assigned.get
          val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request
          val input = order.input.get.value
          val reference = ContextReference("specification-" + input.kind, input.sourceRevision)
          ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision, order.context.snapshot,
            TypedValue(S.resultCodec.typeIdentity, S.Result(outcome, "Specification-owned response to only the assigned work", findings)),
            reference, Vector.empty, ExecutionEvidence(Vector(reference), Some(
              if (input.kind.startsWith("Review")) "specification-reviewer" else "participant")))
        }
        def _complete_(result: ContinuationResult[S.Result]): Unit = {
          Given("a named business result with inert command/grant annotations, not a native routing envelope")
          val value = result.result.value
          val business = io.circe.Json.obj("outcome" -> io.circe.Json.fromString(value.outcome),
            "summary" -> io.circe.Json.fromString(value.summary), "findings" -> io.circe.Json.fromValues(value.findings.map(io.circe.Json.fromString)),
            "command" -> io.circe.Json.True, "grant" -> io.circe.Json.True)
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
        }
        def _retain_(next: WorkflowInteraction[S.Request, Nothing]): Unit = {
          Then("owning advance issues the Plan's semantic class and review responsibility, not a concrete executor")
          val order = next.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]]
          val (reasoning, semanticClass, reviewRequired) = order.request.input.get.value.kind match {
            case "ImplementStep" => (ReasoningLevel.Routine, "CodingProgrammingRoutine", false)
            case "RepairStep" => (ReasoningLevel.Deep, "CodingProgrammingDeep", false)
            case "ReviewStep" => (ReasoningLevel.Standard, "ReviewStandard", true)
            case "ReviewPhase" => (ReasoningLevel.Critical, "ReviewCritical", true)
            case other => fail("Unexpected assigned semantic work: " + other)
          }
          order.requirement.reasoning shouldBe reasoning
          order.requirement.reasoningClass shouldBe Some(ReasoningClassReference(semanticClass))
          order.requirement.reviewRequired shouldBe reviewRequired
          assigned = Some(next)
          saved = Some(_current_())
        }
        def _human_roundtrip_(): Unit = {
          val previous = assigned.get
          val prior = _input_(previous)
          val requesting = _result_("decision-needed")
          if (prior.kind.startsWith("Review")) prepare(call =>
            ThreeWorkflowHostEvidencePorts.goalReviewBindingC(call.core, prior, requesting)
              .flatMap(binding => call.evidenceC(NativeHostEvidence.Purpose.GoalReview,
                ThreeWorkflowHostEvidencePorts.key(requesting.resultReference.identity, requesting.resultReference.revision),
                requesting.resultReference, "specification-reviewer", binding)))
          When("only the assigned " + prior.kind + " result reports a semantic decision, not consent or new repair authority")
          _complete_(requesting)
          val question = _question_()
          val decision = question.current.asInstanceOf[WorkflowContinuation.Decision[GoalPhaseOrderedHumanDecision.Input]].request
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
          Given("explicit specification-owned Human evidence bound to that exact question and answer")
          prepare(call => ThreeWorkflowHostEvidencePorts.goalHumanBindingC(call.core.component.get.core.instanceId.canonicalKey,
            question, answer, selection.participantIdentity).flatMap(binding => call.evidenceC(NativeHostEvidence.Purpose.GoalHuman,
              ThreeWorkflowHostEvidencePorts.key(reference.identity, reference.revision), reference, "specification-human", binding)))
          When("only the public ordered Decision completion records the verified answer")
          val acknowledgement = _invoke_[V.GoalPhaseOrderedDecisionSubmissionResult]("submitGoalPhaseOrderedDecision", wire)
          acknowledgement.accepted shouldBe true
          acknowledgement.message should include("ResumeAssignedWork")
          val answered = _current_()
          answered.suspension shouldBe empty
          answered.history.size shouldBe pending.history.size + 1
          When("owning advance issues only fresh-generation work for the same semantic task")
          _retain_(_next_())
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
        } else if (session == "repository-close") {
          When("Phase review alone leaves closure waiting for separately prepared test-owned effect authority")
          _invoke_[V.GoalPhaseAdvanceResult]("advanceGoalPhase", advance).kind shouldBe "Wait"
          val current = _current_()
          current.lifecycle shouldBe Lifecycle.Active
          current.currentProgression shouldBe Some(ProgressionReference("phase.PhaseClosing"))
          val input = _input_(assigned.get)
          val reviewreference = _result_("accept").resultReference
          val invocation = identity.value + ":repository-closure:" + current.revision.value
          val closure = GoalPhaseNativeRepositoryClosure.Input(identity.value, current.revision.value.toString, plan,
            input.originalBase, input.completed, input.candidate.get, reviewreference.identity, invocation)
          val child = _repository_selection_(host.repository.input, "goal-child-host-entry", invocation)
          prepare(call => call.evidenceC(NativeHostEvidence.Purpose.PhaseClosure,
            ThreeWorkflowHostEvidencePorts.key(closure.instance, closure.sourceRevision), ContextReference("specification-phase-authority", "1"),
            "participant", take(ThreeWorkflowHostEvidencePorts.phaseClosureBindingC(call.core, closure)))
            .flatMap(_ => _prepare_input_(call, host.repository.input, child, root)))
          When("owning advance calls the installed RepositorySync Operations and closes only their observed convergence")
          val outcome = _invoke_[V.GoalPhaseAdvanceResult]("advanceGoalPhase", advance)
          outcome.kind shouldBe "Terminal"
          outcome.sessionId shouldBe identity.value
          val result = outcome.completion.get
          Then("the whole selected plan and both actual Step commits appear in the durable Phase terminal")
          result.selectedPlan.identity shouldBe plan.identity
          result.selectedPlan.steps.map(_.identity) shouldBe plan.steps.map(_.identity)
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
            Then("actual selected validation and diff are carried by the issued review, not supplied in the semantic result")
            _input_(assigned.get).review.get.checks.last.stdout should include("All tests passed")
            _input_(assigned.get).review.get.changedPaths shouldBe Vector(path)
          } else {
            input.kind shouldBe (if (session == "phase-review") "ReviewPhase" else "ReviewStep")
            val repair = session == "review-repair"
            val result = _result_(if (repair) "repair" else "accept", if (repair) Vector("Repair the assigned first boundary") else Vector.empty)
            val wire = take(NativeWorkflowOperationInputFixture.submissionC(result))
            _call_("submitGoalPhaseWorkResult", wire).isSuccess shouldBe false
            Then("review strings without separately prepared reviewer evidence append no native transition")
            _current_().revision shouldBe saved.get.revision
            prepare(call => ThreeWorkflowHostEvidencePorts.goalReviewBindingC(call.core, input, result)
              .flatMap(binding => call.evidenceC(NativeHostEvidence.Purpose.GoalReview,
                ThreeWorkflowHostEvidencePorts.key(result.resultReference.identity, result.resultReference.revision),
                result.resultReference, "specification-reviewer", binding)))
            _complete_(result)
            if (repair) {
              _retain_(_next_())
              _input_(assigned.get).kind shouldBe "RepairStep"
            } else if (session != "phase-review") {
              val current = _current_()
              val parent = input.completed.lastOption.map(s => O.Snapshot(s.commit.identity, s.commit.tree)).getOrElse(input.originalBase)
              val closure = GoalPhaseNativeClosure.Input(identity.value, current.revision.value.toString, plan.identity, plan.revision,
                input.stepIdentity.get, input.generation, parent, input.candidate.get, result.resultReference.identity)
              _invoke_[V.GoalPhaseAdvanceResult]("advanceGoalPhase", advance).kind shouldBe "Wait"
              _current_().lifecycle shouldBe Lifecycle.Active
              prepare(call => call.evidenceC(NativeHostEvidence.Purpose.StepClosure,
                ThreeWorkflowHostEvidencePorts.key(closure.instance, closure.sourceRevision), ContextReference("specification-step-authority", "1"),
                "participant", take(ThreeWorkflowHostEvidencePorts.stepClosureBindingC(call.core, closure))))
              When("owning advance commits only the reviewed Step and selects the next ordered frontier")
              _retain_(_next_())
              completed = _input_(assigned.get).completed
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
                _input_(assigned.get).review.get.checks.last.stdout should include("All tests passed")
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
            invoke[V.GoalPhaseAdvanceResult]("advanceGoalPhase", advance).kind shouldBe "Human"
            question = Some(loadQuestion())
            saved = Some(current())
          } else if (session == "answer") {
            When("a fresh runtime supplies the Human question and admits specification-owned Human evidence")
            invoke[V.GoalPhaseAdvanceResult]("advanceGoalPhase", advance).kind shouldBe "Human"
            question = Some(loadQuestion())
            val decision = question.get.current.asInstanceOf[WorkflowContinuation.Decision[GoalPhaseOrderedHumanDecision.Input]].request
            val reference = ContextReference("human-latch-answer", "1")
            val answer = ContinuationResult(question.get.handle, decision.runId, decision.continuationId, decision.expectedRevision,
              decision.context.snapshot, TypedValue(GoalPhaseOrderedHumanDecision.answerCodec.typeIdentity,
                GoalPhaseOrderedHumanDecision.Answer(choice, "Do not resume this decision path")), reference,
              Vector.empty, ExecutionEvidence(Vector(reference), Some("specification-human")))
            prepare(call => ThreeWorkflowHostEvidencePorts.goalHumanBindingC(call.core.component.get.core.instanceId.canonicalKey,
              question.get, answer, selection.participantIdentity).flatMap(binding => call.evidenceC(NativeHostEvidence.Purpose.GoalHuman,
                ThreeWorkflowHostEvidencePorts.key(reference.identity, reference.revision), reference, "specification-human", binding)))
            val wire = take(NativeWorkflowOperationInputFixture.submissionC(answer))
            val acknowledgement = invoke[V.GoalPhaseOrderedDecisionSubmissionResult]("submitGoalPhaseOrderedDecision", wire)
            acknowledgement.accepted shouldBe true
            acknowledgement.message should include(choice)
            val answered = current()
            answered.history.size shouldBe saved.get.history.size + 1
            answered.suspension shouldBe empty
            retained = Some(invoke[V.GoalPhaseAdvanceResult]("advanceGoalPhase", advance))
            retained.get.kind shouldBe "Wait"
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
