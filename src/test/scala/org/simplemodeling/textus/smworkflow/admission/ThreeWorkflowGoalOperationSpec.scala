package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{GoalPhaseStartCommand, RepositorySyncStartCommand}
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeWork as S
import SplitPhaseExplicitPartitionFixture.take

/** The spec owns selected input, source edits and semantic/review responses.
  * Real public Operations own all history, checks, staging, commits and closure.
  * Host evidence is explicit test data, not independent production issuance.
  * @since   Oct.  7, 2026
  * @version Oct.  8, 2026
  */
final class ThreeWorkflowGoalOperationSpec extends AnyWordSpec with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  "GoalPhase public Operations on the production three-workflow composition" should {
    for (withDecisions <- Vector(false, true)) {
    (if (withDecisions) "close the whole ordered Phase after verified Human resumption of implementation, review, repair and Phase review"
      else "close the whole ordered two-Step Phase after assigned repair, checks, reviews and actual repository closure") in {
      Given("a clean owned Git repository and an explicitly selected two-Step plan")
      val workroot = Files.createDirectories(Path.of("target/sm-workflow/work/direct-repository-operation").toAbsolutePath)
      val root = Files.createTempDirectory(workroot, "episode-")
      val original = ThreeWorkflowNormalHostFixture.definition(root)
      val paths = Vector("one.scala", "two.scala")
      val steps = Vector(O.Step("step-1", "Implement first boundary", Vector(paths(0)), Vector.empty, Vector("test")),
        O.Step("step-2", "Implement second boundary", Vector(paths(1)), Vector("step-1"), Vector("test")))
      val plan = O.Plan("selected-two-step-plan", "1", original.goal.input.phaseIdentity,
        original.goal.input.repositoryIdentity, steps, Vector("test"), repairLimit = 1)
      val host = original.copy(goal = original.goal.copy(candidatePaths = paths,
        closure = original.goal.closure.copy(programs = Vector(
          GoalPhaseNativeClosure.StepProgram("step-1", 1), GoalPhaseNativeClosure.StepProgram("step-2", 2)))))
      val command = GoalPhaseStartCommand(host.goal.input, "selected-goal", "participant", "direct-goal-start")
      val identity = take(GoalPhaseNativeRun.identityC(command))
      val advance = GoalPhaseNativeServiceAssembly.encodeAdvance(identity.value)
      var assigned = Option.empty[WorkflowInteraction[S.Request, Nothing]]
      var saved = Option.empty[InstanceRecord]
      var terminal = Option.empty[String]
      var firstbase = Option.empty[O.Snapshot]
      var completed = Vector.empty[O.ClosedStep]
      val sessions = Vector("issue", "implement-1", "review-repair", "repair", "review-1", "implement-2",
        "review-2", "phase-review", "repository-close", "fresh terminal")
      for (session <- sessions) _with_runtime_(root, "Goal", Some(host)) { (owner, component, actor, prepare) =>
        def _call_(operation: String, wire: String) = owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "GoalPhaseService", operation = operation,
            arguments = List(Argument("arg1", wire))), actor)
        def _invoke_(operation: String, wire: String) = _scalar_(take(_call_(operation, wire)))
        def _current_(): InstanceRecord = {
          var state = Option.empty[InstanceRecord]
          prepare(call => call.goalCurrentC(identity).map(value => { state = Some(value); () }))
          state.get
        }
        def _next_(): WorkflowInteraction[S.Request, Nothing] =
          take(WorkflowWorkOrderJsonV1.decodeC(_invoke_("advanceGoalPhase", advance), S.inputCodec))
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
        def _complete_(result: ContinuationResult[S.Result]): Unit =
          take(WorkflowWaitJsonV1.decodeC(_invoke_("submitGoalPhaseWorkResult", take(WorkflowResultJsonV1.encodeC(result, S.resultCodec)))))
        def _retain_(next: WorkflowInteraction[S.Request, Nothing]): Unit = {
          assigned = Some(next)
          saved = Some(_current_())
        }
        def _human_roundtrip_(): Unit = {
          val previous = assigned.get
          val prior = _input_(previous)
          val requesting = _result_("decision-needed")
          if (prior.kind.startsWith("Review")) prepare(call =>
            ThreeWorkflowHostEvidencePorts.reviewBindingC(call.core, S.inputCodec.encode(prior), requesting, S.resultCodec)
              .flatMap(binding => call.evidenceC(NativeHostEvidence.Purpose.GoalReview,
                ThreeWorkflowHostEvidencePorts.key(requesting.resultReference.identity, requesting.resultReference.revision),
                requesting.resultReference, "specification-reviewer", binding)))
          When("only the assigned " + prior.kind + " result reports a semantic decision, not consent or new repair authority")
          _complete_(requesting)
          val question = take(WorkflowDecisionJsonV1.decodeC(_invoke_("advanceGoalPhase", advance),
            GoalPhaseOrderedHumanDecision.inputCodec))
          val decision = question.current.asInstanceOf[WorkflowContinuation.Decision[GoalPhaseOrderedHumanDecision.Input]].request
          decision.input.get.value.work shouldBe prior
          decision.input.get.value.requestingResult shouldBe requesting.resultReference
          decision.input.get.value.result shouldBe requesting.result.value
          val pending = _current_()
          pending.currentProgression shouldBe Some(ProgressionReference("phase.Decision"))
          pending.suspension should not be empty
          When("another JVM observes the exact issued ordered Human question without reissuing work")
          val reopened = _separate_operation_observation_(root, "Goal", true, identity, advance, Some(host))
          take(WorkflowDecisionJsonV1.decodeC(reopened.hcursor.get[String]("result").toOption.get,
            GoalPhaseOrderedHumanDecision.inputCodec)) shouldBe question
          reopened.hcursor.get[String]("before").toOption.get shouldBe take(WorkflowInstanceRecordJsonV1.encodeC(pending))
          val reference = ContextReference("specification-human-" + prior.kind, decision.expectedRevision.value)
          val answer = ContinuationResult(question.handle, decision.runId, decision.continuationId, decision.expectedRevision,
            decision.context.snapshot, TypedValue(GoalPhaseOrderedHumanDecision.answerCodec.typeIdentity,
              GoalPhaseOrderedHumanDecision.Answer("ResumeAssignedWork", "Continue only the same original assigned semantic work")),
            reference, Vector.empty, ExecutionEvidence(Vector(reference), Some("specification-human")))
          val wire = take(WorkflowResultJsonV1.encodeC(answer, GoalPhaseOrderedHumanDecision.answerCodec))
          When("an answer has no separately prepared host Human evidence")
          _call_("submitGoalPhaseOrderedDecision", wire).isSuccess shouldBe false
          Then("the actual question, repository and original allowance remain unchanged")
          SplitPhaseDefinitionBinding.sameRecord(_current_(), pending) shouldBe true
          Given("explicit specification-owned Human evidence bound to that exact question and answer")
          prepare(call => ThreeWorkflowHostEvidencePorts.goalHumanBindingC(call.core.component.get.core.instanceId.canonicalKey,
            question, answer, command.participantIdentity).flatMap(binding => call.evidenceC(NativeHostEvidence.Purpose.GoalHuman,
              ThreeWorkflowHostEvidencePorts.key(reference.identity, reference.revision), reference, "specification-human", binding)))
          When("only the public ordered Decision completion records the verified answer")
          val acknowledgement = _invoke_("submitGoalPhaseOrderedDecision", wire)
          take(WorkflowWaitJsonV1.decodeC(acknowledgement))
          val answered = _current_()
          answered.suspension shouldBe empty
          answered.history.size shouldBe pending.history.size + 1
          Then("the exact retained answer can be acknowledged without a second history entry")
          _invoke_("submitGoalPhaseOrderedDecision", wire) shouldBe acknowledgement
          val altered = answer.copy(result = answer.result.copy(value = answer.result.value.copy(choice = "Cancel")))
          _call_("submitGoalPhaseOrderedDecision", take(WorkflowResultJsonV1.encodeC(altered,
            GoalPhaseOrderedHumanDecision.answerCodec))).isSuccess shouldBe false
          SplitPhaseDefinitionBinding.sameRecord(_current_(), answered) shouldBe true
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
          When("another JVM reopens the saved resumed task without installing input or old history")
          val observed = _separate_operation_observation_(root, "Goal", true, identity, advance, Some(host))
          take(WorkflowWorkOrderJsonV1.decodeC(observed.hcursor.get[String]("result").toOption.get, S.inputCodec)) shouldBe assigned.get
          observed.hcursor.get[String]("before").toOption.get shouldBe take(WorkflowInstanceRecordJsonV1.encodeC(saved.get))
        }
        if (session == "issue") {
          prepare(call => for {
            _ <- call.setupC(_setup_commands_(root, "Goal").size)
            catalog <- GoalPhaseRuntimeCatalog.loadC()
            selection = ProfileStartAdmission.Selection(command.selectionIdentity, GoalPhaseNativeRun.startOperation,
              catalog.definition.workflowIdentity, catalog.definition.workflowRevision, command.participantIdentity,
              ProfileStartAdmission.SelectionOrigin.ExplicitHuman, command.idempotencyKey, None,
              Some(GoalPhaseStartCommandAdapter.selectedInput(command.input)))
            _ <- call.createC("goal_selection", selection.selectionId, Record.data(ProfileSelectionJsonV1.storageField ->
              take(ProfileSelectionJsonV1.encodeStorageC(selection))))
            _ <- call.createC("goal_plans", command.selectionIdentity, Record.data(GoalPhaseSelectedPlanJsonV1.storageField ->
              take(GoalPhaseSelectedPlanJsonV1.encodeStorageC(GoalPhaseNativeRun.SelectedPlan(
                command.selectionIdentity, command.participantIdentity, command.input, plan)))))
          } yield ())
          When("public Start saves the complete selected plan and captures the actual clean base")
          take(WorkflowWaitJsonV1.decodeC(_invoke_("startGoalPhase", GoalPhaseStartServiceOperation.encodeCommand(command))))
          _retain_(_next_())
          _input_(assigned.get).selectedPlan shouldBe plan
          _input_(assigned.get).kind shouldBe "ImplementStep"
          _input_(assigned.get).stepIdentity shouldBe Some("step-1")
          firstbase = Some(_input_(assigned.get).originalBase)
          When("another JVM reopens the saved first implementation without installing selected rows or Workflow history")
          val reopened = _separate_operation_observation_(root, "Goal", true, identity, advance, Some(host))
          take(WorkflowWorkOrderJsonV1.decodeC(reopened.hcursor.get[String]("result").toOption.get, S.inputCodec)) shouldBe assigned.get
          reopened.hcursor.get[String]("before").toOption.get shouldBe take(WorkflowInstanceRecordJsonV1.encodeC(saved.get))
        } else if (session == "fresh terminal") {
          When("a fresh canonical runtime observes whole-Phase completion without reinstalling input or authority")
          _invoke_("advanceGoalPhase", advance) shouldBe terminal.get
          SplitPhaseDefinitionBinding.sameRecord(_current_(), saved.get) shouldBe true
          When("another JVM retains the complete ordered Phase terminal and native history without replay")
          val reopened = _separate_operation_observation_(root, "Goal", true, identity, advance, Some(host))
          reopened.hcursor.get[String]("result").toOption.get shouldBe terminal.get
          reopened.hcursor.get[String]("before").toOption.get shouldBe take(WorkflowInstanceRecordJsonV1.encodeC(saved.get))
        } else if (session == "repository-close") {
          When("Phase review alone leaves closure waiting for separately prepared test-owned effect authority")
          take(WorkflowWaitJsonV1.decodeC(_invoke_("advanceGoalPhase", advance)))
          val current = _current_()
          current.lifecycle shouldBe Lifecycle.Active
          current.currentProgression shouldBe Some(ProgressionReference("phase.PhaseClosing"))
          val input = _input_(assigned.get)
          val reviewreference = _result_("accept").resultReference
          val invocation = identity.value + ":repository-closure:" + current.revision.value
          val closure = GoalPhaseNativeRepositoryClosure.Input(identity.value, current.revision.value.toString, plan,
            input.originalBase, input.completed, input.candidate.get, reviewreference.identity, invocation)
          val child = RepositorySyncStartCommand(host.repository.input, host.repository.closureSelection, "participant", invocation)
          prepare(call => call.evidenceC(NativeHostEvidence.Purpose.PhaseClosure,
            ThreeWorkflowHostEvidencePorts.key(closure.instance, closure.sourceRevision), ContextReference("specification-phase-authority", "1"),
            "participant", ThreeWorkflowHostEvidencePorts.ownedBinding(call.core, closure.asJson))
            .flatMap(_ => _prepare_input_(call, child, root)))
          When("owning advance calls the installed RepositorySync Operations and closes only their observed convergence")
          val wire = _invoke_("advanceGoalPhase", advance)
          val outcome = take(WorkflowTerminalJsonV1.decodeC(wire, GoalPhaseNativeRepositoryClosure.resultCodec))
          val result = outcome.current.asInstanceOf[WorkflowContinuation.Terminal[GoalPhaseNativeRepositoryClosure.Result]].result.value
          Then("the whole selected plan and both actual Step commits appear in the durable Phase terminal")
          result.plan shouldBe plan
          result.originalBase shouldBe firstbase.get
          result.completed shouldBe completed
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
          outcome.handle shouldBe take(WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, actual))
          Files.readString(root.resolve("repository/one.scala")) shouldBe "object FirstRepaired\n"
          Files.readString(root.resolve("repository/two.scala")) shouldBe "object Second\n"
          terminal = Some(wire)
          saved = Some(actual)
        } else {
          When("a fresh runtime returns the exact assigned " + session + " work from saved state")
          _next_() shouldBe assigned.get
          SplitPhaseDefinitionBinding.sameRecord(_current_(), saved.get) shouldBe true
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
            if (session == "implement-1") {
              When("another JVM completes only the saved assigned implementation and advances through real checks")
              val completion = take(WorkflowResultJsonV1.encodeC(_result_("completed"), S.resultCodec))
              val resumed = _separate_operation_observation_(root, "Goal", true, identity, advance, Some(host), Some(completion))
              take(WorkflowWorkOrderJsonV1.decodeC(resumed.hcursor.get[String]("result").toOption.get, S.inputCodec)) shouldBe assigned.get
              take(WorkflowWaitJsonV1.decodeC(resumed.hcursor.get[String]("acknowledgement").toOption.get))
              val review = take(WorkflowWorkOrderJsonV1.decodeC(resumed.hcursor.get[String]("resumed").toOption.get, S.inputCodec))
              _input_(review).kind shouldBe "ReviewStep"
              _next_() shouldBe review
              resumed.hcursor.get[String]("after").toOption.get shouldBe take(WorkflowInstanceRecordJsonV1.encodeC(_current_()))
            } else _complete_(_result_("completed"))
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
            val wire = take(WorkflowResultJsonV1.encodeC(result, S.resultCodec))
            _call_("submitGoalPhaseWorkResult", wire).isSuccess shouldBe false
            Then("review strings without the exact separately prepared reviewer evidence leave pending history unchanged")
            SplitPhaseDefinitionBinding.sameRecord(_current_(), saved.get) shouldBe true
            prepare(call => ThreeWorkflowHostEvidencePorts.reviewBindingC(call.core, S.inputCodec.encode(input), result, S.resultCodec)
              .flatMap(binding => call.evidenceC(NativeHostEvidence.Purpose.GoalReview,
                ThreeWorkflowHostEvidencePorts.key(result.resultReference.identity, result.resultReference.revision),
                result.resultReference, "specification-reviewer", binding)))
            _complete_(result)
            if (repair) {
              _retain_(_next_())
              _input_(assigned.get).kind shouldBe "RepairStep"
              When("another JVM reopens the exact assigned repair and retained findings without refunding the selected allowance")
              val reopened = _separate_operation_observation_(root, "Goal", true, identity, advance, Some(host))
              take(WorkflowWorkOrderJsonV1.decodeC(reopened.hcursor.get[String]("result").toOption.get, S.inputCodec)) shouldBe assigned.get
              reopened.hcursor.get[String]("before").toOption.get shouldBe take(WorkflowInstanceRecordJsonV1.encodeC(saved.get))
            } else if (session != "phase-review") {
              val current = _current_()
              val parent = input.completed.lastOption.map(s => O.Snapshot(s.commit.identity, s.commit.tree)).getOrElse(input.originalBase)
              val closure = GoalPhaseNativeClosure.Input(identity.value, current.revision.value.toString, plan.identity, plan.revision,
                input.stepIdentity.get, input.generation, parent, input.candidate.get, result.resultReference.identity)
              take(WorkflowWaitJsonV1.decodeC(_invoke_("advanceGoalPhase", advance)))
              _current_().lifecycle shouldBe Lifecycle.Active
              prepare(call => call.evidenceC(NativeHostEvidence.Purpose.StepClosure,
                ThreeWorkflowHostEvidencePorts.key(closure.instance, closure.sourceRevision), ContextReference("specification-step-authority", "1"),
                "participant", ThreeWorkflowHostEvidencePorts.ownedBinding(call.core, closure.asJson)))
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
        val command = GoalPhaseStartCommand(host.goal.input, "human-latch", "participant", "human-latch-start")
        val identity = take(GoalPhaseNativeRun.identityC(command))
        val advance = GoalPhaseNativeServiceAssembly.encodeAdvance(identity.value)
        val plan = O.Plan("human-latch-plan", "1", host.goal.input.phaseIdentity, host.goal.input.repositoryIdentity,
          Vector(O.Step("step-1", "Implement only the original boundary", host.goal.candidatePaths, Vector.empty, Vector("test"))),
          Vector("test"), repairLimit = 0)
        var question = Option.empty[WorkflowInteraction[GoalPhaseOrderedHumanDecision.Input, Nothing]]
        var retained = Option.empty[String]
        var saved = Option.empty[InstanceRecord]
        var originalRepository = Option.empty[RepositorySyncManagedGitObservation.Observed]
        for (session <- Vector("issue", "answer", "fresh retained")) _with_runtime_(root, "Goal", Some(host)) { (owner, component, actor, prepare) =>
          def invoke(operation: String, wire: String) = _scalar_(take(owner.executeOperationResponseInContext(
            Request.of(component = component.name, service = "GoalPhaseService", operation = operation,
              arguments = List(Argument("arg1", wire))), actor)))
          def current(): InstanceRecord = {
            var result = Option.empty[InstanceRecord]
            prepare(call => call.goalCurrentC(identity).map(value => { result = Some(value); () }))
            result.get
          }
          if (session == "issue") {
            prepare(call => for {
              _ <- call.setupC(_setup_commands_(root, "Goal").size)
              _ <- call.observeC().map(value => { originalRepository = Some(value); () })
              catalog <- GoalPhaseRuntimeCatalog.loadC()
              selection = ProfileStartAdmission.Selection(command.selectionIdentity, GoalPhaseNativeRun.startOperation,
                catalog.definition.workflowIdentity, catalog.definition.workflowRevision, command.participantIdentity,
                ProfileStartAdmission.SelectionOrigin.ExplicitHuman, command.idempotencyKey, None,
                Some(GoalPhaseStartCommandAdapter.selectedInput(command.input)))
              _ <- call.createC("goal_selection", selection.selectionId, Record.data(ProfileSelectionJsonV1.storageField ->
                take(ProfileSelectionJsonV1.encodeStorageC(selection))))
              _ <- call.createC("goal_plans", command.selectionIdentity, Record.data(GoalPhaseSelectedPlanJsonV1.storageField ->
                take(GoalPhaseSelectedPlanJsonV1.encodeStorageC(GoalPhaseNativeRun.SelectedPlan(
                  command.selectionIdentity, command.participantIdentity, command.input, plan)))))
            } yield ())
            When("the public semantic completion reports only its assigned decision-needed result")
            take(WorkflowWaitJsonV1.decodeC(invoke("startGoalPhase", GoalPhaseStartServiceOperation.encodeCommand(command))))
            val work = take(WorkflowWorkOrderJsonV1.decodeC(invoke("advanceGoalPhase", advance), S.inputCodec))
            val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request
            val reference = ContextReference("need-human", "1")
            val result = ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision,
              order.context.snapshot, TypedValue(S.resultCodec.typeIdentity, S.Result("decision-needed", "Human direction required", Vector.empty)),
              reference, Vector.empty, ExecutionEvidence(Vector(reference), Some("participant")))
            take(WorkflowWaitJsonV1.decodeC(invoke("submitGoalPhaseWorkResult", take(WorkflowResultJsonV1.encodeC(result, S.resultCodec)))))
            question = Some(take(WorkflowDecisionJsonV1.decodeC(invoke("advanceGoalPhase", advance), GoalPhaseOrderedHumanDecision.inputCodec)))
            saved = Some(current())
          } else if (session == "answer") {
            When("a fresh runtime observes that exact saved question and admits specification-owned Human evidence")
            take(WorkflowDecisionJsonV1.decodeC(invoke("advanceGoalPhase", advance), GoalPhaseOrderedHumanDecision.inputCodec)) shouldBe question.get
            SplitPhaseDefinitionBinding.sameRecord(current(), saved.get) shouldBe true
            val decision = question.get.current.asInstanceOf[WorkflowContinuation.Decision[GoalPhaseOrderedHumanDecision.Input]].request
            val reference = ContextReference("human-latch-answer", "1")
            val answer = ContinuationResult(question.get.handle, decision.runId, decision.continuationId, decision.expectedRevision,
              decision.context.snapshot, TypedValue(GoalPhaseOrderedHumanDecision.answerCodec.typeIdentity,
                GoalPhaseOrderedHumanDecision.Answer(choice, "Do not resume this decision path")), reference,
              Vector.empty, ExecutionEvidence(Vector(reference), Some("specification-human")))
            prepare(call => ThreeWorkflowHostEvidencePorts.goalHumanBindingC(call.core.component.get.core.instanceId.canonicalKey,
              question.get, answer, command.participantIdentity).flatMap(binding => call.evidenceC(NativeHostEvidence.Purpose.GoalHuman,
                ThreeWorkflowHostEvidencePorts.key(reference.identity, reference.revision), reference, "specification-human", binding)))
            val wire = take(WorkflowResultJsonV1.encodeC(answer, GoalPhaseOrderedHumanDecision.answerCodec))
            val acknowledgement = invoke("submitGoalPhaseOrderedDecision", wire)
            take(WorkflowWaitJsonV1.decodeC(acknowledgement))
            val answered = current()
            answered.history.size shouldBe saved.get.history.size + 1
            answered.suspension shouldBe empty
            invoke("submitGoalPhaseOrderedDecision", wire) shouldBe acknowledgement
            retained = Some(invoke("advanceGoalPhase", advance))
            take(WorkflowWaitJsonV1.decodeC(retained.get))
            Then("the answer stays latched without another question, WorkOrder or Phase completion")
            SplitPhaseDefinitionBinding.sameRecord(current(), answered) shouldBe true
            answered.lifecycle shouldBe Lifecycle.Active
            answered.currentProgression shouldBe Some(ProgressionReference("phase.Decision"))
            saved = Some(answered)
          } else {
            When("a further runtime and a separate JVM observe the retained direction")
            invoke("advanceGoalPhase", advance) shouldBe retained.get
            SplitPhaseDefinitionBinding.sameRecord(current(), saved.get) shouldBe true
            val reopened = _separate_operation_observation_(root, "Goal", true, identity, advance, Some(host))
            reopened.hcursor.get[String]("result").toOption.get shouldBe retained.get
            Then("no native history or repository effect is replayed")
            reopened.hcursor.get[String]("before").toOption.get shouldBe take(WorkflowInstanceRecordJsonV1.encodeC(saved.get))
            var observed = Option.empty[RepositorySyncManagedGitObservation.Observed]
            prepare(call => call.observeC().map(value => { observed = Some(value); () }))
            observed.get.dirty shouldBe empty
            // No fetch or convergence was requested on this decision path.
            // Compare actual repository facts, including absent tracking state,
            // with the real initial observation rather than assuming an upstream.
            observed shouldBe originalRepository
          }
        }
      }
    }
  }
}
