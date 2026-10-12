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
import org.goldenport.protocol.{Argument, Request}
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
      "E7 advance only the explicitly selected associated Phase when a session has two real Starts" in {
        Given("Spec: docs/spec/session-workflow-context-resolution.md; Rule: R3; Example: E7; two selected plans on an owned repository and one actual invocation session")
        val workroot = Files.createDirectories(Path.of("target/sm-workflow/work/direct-repository-operation").toAbsolutePath)
        val root = Files.createTempDirectory(workroot, "goal-session-targets-")
        val host = ThreeWorkflowNormalHostFixture.definition(root)
        val selections = Vector("first", "second").map(label => _goal_selection_(host.goal.input,
          "session-goal-" + label, "session-goal-start-" + label))
        val identities = selections.map(selection => take(GoalPhaseNativeRun.identityC(selection.selectionId, selection.idempotencyKey)))
        for (episode <- Vector("publish")) _with_runtime_(root, "Goal", Some(host)) {
          (owner, component, actor, prepare) =>
          val context = take(ExecutionContext.withRoutingSessionIdentity(actor, "two-goal-targets"))
          def _call_(target: Option[String], foreign: Boolean = false) = {
            val query = io.circe.Json.obj("schemaVersion" -> io.circe.Json.fromString(NativeWorkflowSessionInput.schema))
            val arguments = target.map(value => List(Argument("arg1", query.mapObject(
              _.add("instanceIdentity", io.circe.Json.fromString(value))).noSpaces))).getOrElse(Nil)
            val invocation = if (foreign) take(ExecutionContext.withRoutingSessionIdentity(actor, "unassociated-goal-session"))
              else context
            owner.executeOperationResponseInContext(Request.of(component = component.name, service = "GoalPhaseService",
              operation = "advanceGoalPhase", arguments = arguments), invocation)
          }
          def _states_(): Vector[InstanceRecord] = {
            var records = Vector.empty[InstanceRecord]
            prepare(call => identities.foldLeft(Consequence.unit)((prior, identity) =>
              prior.flatMap(_ => call.goalCurrentC(identity).map(value => { records = records :+ value; () }))))
            records
          }
          def _expect_unchanged_(observed: Vector[InstanceRecord], original: Vector[InstanceRecord]): Unit = {
            observed.size shouldBe original.size
            observed.zip(original).foreach { (current, prior) =>
              current.revision shouldBe prior.revision
            }
          }
          if (episode == "publish") {
            When("the fixture prepares selected Plans with inert metadata and the two public Starts publish their actual associations")
            prepare(call => call.setupC(_setup_commands_(root, "Goal").size).flatMap(_ =>
              selections.foldLeft(Consequence.unit) { (prior, selection) => prior.flatMap { _ =>
                val plan = O.Plan(selection.selectionId, "1", host.goal.input.phaseIdentity, host.goal.input.repositoryIdentity,
                  Vector(O.Step("step-1", "Implement the selected Phase", host.goal.candidatePaths, Vector.empty, Vector("test"))),
                  Vector("test"), repairLimit = 1)
                val data = io.circe.parser.parse(take(GoalPhaseSelectedPlanJsonV1.encodeC(
                  GoalPhaseNativeRun.SelectedPlan(selection.selectionId, selection.participantIdentity, plan)))).toOption.get
                val annotated = data.mapObject(fields => fields.add("diagnostic", io.circe.Json.True)
                  .add("payload", fields("payload").get.mapObject(_.add("input", io.circe.Json.True))))
                val stored = GoalPhaseSelectedPlanJsonV1.schemaVersion + ":" + annotated.noSpaces
                call.createC("goal_selection", selection.selectionId, Record.data(ProfileSelectionJsonV1.storageField ->
                  take(ProfileSelectionJsonV1.encodeStorageC(selection)))).flatMap(_ =>
                  call.createC("goal_plans", selection.selectionId, Record.data(GoalPhaseSelectedPlanJsonV1.storageField -> stored)))
              } }))
            val started = selections.map(selection => take(WorkflowWaitJsonV1.decodeC(_scalar_(take(
              owner.executeOperationResponseInContext(Request.of(component = component.name, service = "GoalPhaseService",
                operation = "startGoalPhase", arguments = List(Argument("arg1",
                  NativeWorkflowOperationInputFixture.goal(host.goal.input, Some(selection.selectionId))))), context))))).handle.instanceIdentity)
            Then("both distinct native targets were created by their owning Start rather than inserted by a fixture")
            started shouldBe identities
            started.distinct.size shouldBe 2
          }
          val before = _states_()
          When("advance omits a target selector while both legitimate associations exist")
          val ambiguous = _call_(None)
          val afterambiguous = _states_()
          Then("genuine ambiguity refuses before progressing either Phase")
          ambiguous.isSuccess shouldBe false
          ambiguous.toString should include("ambiguous instance")
          _expect_unchanged_(afterambiguous, before)
          When("another session names a target that exists but is not associated with that session")
          val foreign = _call_(Some(identities.head.value), foreign = true)
          val afterforeign = _states_()
          Then("the selector is not authority to borrow another session's association")
          foreign.isSuccess shouldBe false
          foreign.toString should include("no associated instance")
          _expect_unchanged_(afterforeign, before)
          When("the owning session supplies only the selector needed to identify its first Phase")
          val wire = _scalar_(take(_call_(Some(identities.head.value))))
          val work = take(WorkflowWorkOrderJsonV1.decodeC(wire, S.inputCodec))
          val afterselected = _states_()
          Then("only the selected Phase issues or retains its actual next semantic work, without instance or revision echoes for the other Phase")
          work.handle.instanceIdentity shouldBe identities.head
          work.current shouldBe a[WorkflowContinuation.WorkOrder[?]]
          _expect_unchanged_(afterselected.tail, before.tail)
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
      val identity = take(GoalPhaseNativeRun.identityC(selection.selectionId, selection.idempotencyKey))
      Given("advance contains unused diagnostics and caller hints but only its actual instance is consumed")
      val advance = io.circe.parser.parse(NativeWorkflowOperationInputFixture.advance()).toOption.get
        .mapObject(_.add("stage", io.circe.Json.fromString("Completed"))
          .add("argv", io.circe.Json.arr(io.circe.Json.fromString("must-not-execute")))
          .add("diagnostics", io.circe.Json.obj("expectedRevision" -> io.circe.Json.fromInt(999)))).spaces2
      var assigned = Option.empty[WorkflowInteraction[S.Request, Nothing]]
      var saved = Option.empty[InstanceRecord]
      var firstbase = Option.empty[O.Snapshot]
      var completed = Vector.empty[O.ClosedStep]
      val sessions = Vector("issue", "implement-1", "review-repair", "repair", "review-1", "implement-2",
        "review-2", "phase-review", "repository-close")
      for (session <- sessions) _with_runtime_(root, "Goal", Some(host)) { (owner, component, actor, prepare) =>
        val invocationcontext = actor
        Given("every normal episode uses the actual invocation session across managed SQLite runtimes")
        def _call_(operation: String, wire: String) = owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "GoalPhaseService", operation = operation,
            arguments = if (operation == "advanceGoalPhase" && wire == "{}") Nil
              else List(Argument("arg1", wire))), invocationcontext)
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
        def _complete_(result: ContinuationResult[S.Result]): Unit = {
          Given("the pending request is unique and its business answer carries inert command/grant diagnostics")
          val wire = io.circe.parser.parse(take(NativeWorkflowOperationInputFixture.encodeC(result, S.resultCodec))).toOption.get
          val value = wire.hcursor.downField("result").downField("value").focus.get
          val body = value.hcursor.downField("payload").focus.get
          val minimal = wire.mapObject(_.remove("requestIdentity").add("diagnostics", io.circe.Json.True)
            .add("result", wire.hcursor.downField("result").focus.get.mapObject(_.add("value",
              value.mapObject(_.add("payload", body.mapObject(_.add("command", io.circe.Json.True)
                .add("grant", io.circe.Json.True)))))))).noSpaces
          When("the actual semantic result completes without routing echoes or an unused-field certificate")
          val acknowledgement = take(WorkflowWaitJsonV1.decodeC(_invoke_("submitGoalPhaseWorkResult", minimal)))
          Then("the owning Operation returns its real pending frontier without executing diagnostic commands")
          acknowledgement.current shouldBe a[WorkflowContinuation.Wait]
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
          val question = take(WorkflowDecisionJsonV1.decodeC(_invoke_("advanceGoalPhase", advance),
            GoalPhaseOrderedHumanDecision.inputCodec))
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
          val encoded = io.circe.parser.parse(take(NativeWorkflowOperationInputFixture.encodeC(answer,
            GoalPhaseOrderedHumanDecision.answerCodec))).toOption.get
          val wire = encoded.mapObject(_.add("result", encoded.hcursor.downField("result").focus.get.mapObject(
            _.add("value", encoded.hcursor.downField("result").downField("value").focus.get
              .mapObject(_.add("command", io.circe.Json.True)))))).noSpaces
          When("an answer has no separately prepared host Human evidence")
          _call_("submitGoalPhaseOrderedDecision", wire).isSuccess shouldBe false
          Then("the actual question, repository and original allowance remain unchanged")
          _current_().revision shouldBe pending.revision
          Given("explicit specification-owned Human evidence bound to that exact question and answer")
          prepare(call => ThreeWorkflowHostEvidencePorts.goalHumanBindingC(call.core.component.get.core.instanceId.canonicalKey,
            question, answer, selection.participantIdentity).flatMap(binding => call.evidenceC(NativeHostEvidence.Purpose.GoalHuman,
              ThreeWorkflowHostEvidencePorts.key(reference.identity, reference.revision), reference, "specification-human", binding)))
          When("only the public ordered Decision completion records the verified answer")
          val acknowledgement = _invoke_("submitGoalPhaseOrderedDecision", wire)
          take(WorkflowWaitJsonV1.decodeC(acknowledgement))
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
            _ <- call.createC("goal_selection", selection.selectionId, Record.data(ProfileSelectionJsonV1.storageField ->
              take(ProfileSelectionJsonV1.encodeStorageC(selection))))
            _ <- call.createC("goal_plans", selection.selectionId, Record.data(GoalPhaseSelectedPlanJsonV1.storageField ->
              take(GoalPhaseSelectedPlanJsonV1.encodeStorageC(GoalPhaseNativeRun.SelectedPlan(
                selection.selectionId, selection.participantIdentity, plan)))))
          } yield ())
          When("public Start saves the complete selected plan and captures the actual clean base")
          take(WorkflowWaitJsonV1.decodeC(_invoke_("startGoalPhase", NativeWorkflowOperationInputFixture.goal(host.goal.input))))
          When("advance consumes its actual instance while ignoring caller stage argv and diagnostic revision")
          _retain_(_next_())
          Then("only the original plan's first implementation is issued rather than the caller's Completed stage")
          _input_(assigned.get).selectedPlan shouldBe plan
          _input_(assigned.get).kind shouldBe "ImplementStep"
          _input_(assigned.get).stepIdentity shouldBe Some("step-1")
          firstbase = Some(_input_(assigned.get).originalBase)
          When("the actual target instance is changed while the unused diagnostic fields are retained")
          val foreign = io.circe.parser.parse(advance).toOption.get
            .mapObject(_.add("instanceIdentity", io.circe.Json.fromString("foreign-instance"))).noSpaces
          val rejected = _call_("advanceGoalPhase", foreign)
          Then("the actual native Start lookup refuses the absent instance without changing original pending work")
          rejected.isSuccess shouldBe false
          rejected.toString should include("no associated instance")
          _current_().revision shouldBe saved.get.revision
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
          val child = _repository_selection_(host.repository.input, host.repository.closureSelection, invocation)
          prepare(call => call.evidenceC(NativeHostEvidence.Purpose.PhaseClosure,
            ThreeWorkflowHostEvidencePorts.key(closure.instance, closure.sourceRevision), ContextReference("specification-phase-authority", "1"),
            "participant", take(ThreeWorkflowHostEvidencePorts.phaseClosureBindingC(call.core, closure)))
            .flatMap(_ => _prepare_input_(call, host.repository.input, child, root)))
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
            val wire = take(NativeWorkflowOperationInputFixture.encodeC(result, S.resultCodec))
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
              take(WorkflowWaitJsonV1.decodeC(_invoke_("advanceGoalPhase", advance)))
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
        val identity = take(GoalPhaseNativeRun.identityC(selection.selectionId, selection.idempotencyKey))
        val advance = NativeWorkflowOperationInputFixture.advance()
        val plan = O.Plan("human-latch-plan", "1", host.goal.input.phaseIdentity, host.goal.input.repositoryIdentity,
          Vector(O.Step("step-1", "Implement only the original boundary", host.goal.candidatePaths, Vector.empty, Vector("test"))),
          Vector("test"), repairLimit = 0)
        var question = Option.empty[WorkflowInteraction[GoalPhaseOrderedHumanDecision.Input, Nothing]]
        var retained = Option.empty[String]
        var saved = Option.empty[InstanceRecord]
        for (session <- Vector("issue", "answer")) _with_runtime_(root, "Goal", Some(host)) { (owner, component, actor, prepare) =>
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
              _ <- call.createC("goal_selection", selection.selectionId, Record.data(ProfileSelectionJsonV1.storageField ->
                take(ProfileSelectionJsonV1.encodeStorageC(selection))))
              _ <- call.createC("goal_plans", selection.selectionId, Record.data(GoalPhaseSelectedPlanJsonV1.storageField ->
                take(GoalPhaseSelectedPlanJsonV1.encodeStorageC(GoalPhaseNativeRun.SelectedPlan(
                  selection.selectionId, selection.participantIdentity, plan)))))
            } yield ())
            When("the public semantic completion reports only its assigned decision-needed result")
            take(WorkflowWaitJsonV1.decodeC(invoke("startGoalPhase", NativeWorkflowOperationInputFixture.goal(host.goal.input))))
            val work = take(WorkflowWorkOrderJsonV1.decodeC(invoke("advanceGoalPhase", advance), S.inputCodec))
            val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request
            val reference = ContextReference("need-human", "1")
            val result = ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision,
              order.context.snapshot, TypedValue(S.resultCodec.typeIdentity, S.Result("decision-needed", "Human direction required", Vector.empty)),
              reference, Vector.empty, ExecutionEvidence(Vector(reference), Some("participant")))
            take(WorkflowWaitJsonV1.decodeC(invoke("submitGoalPhaseWorkResult", take(NativeWorkflowOperationInputFixture.encodeC(result, S.resultCodec)))))
            question = Some(take(WorkflowDecisionJsonV1.decodeC(invoke("advanceGoalPhase", advance), GoalPhaseOrderedHumanDecision.inputCodec)))
            saved = Some(current())
          } else if (session == "answer") {
            When("a fresh runtime supplies the Human question and admits specification-owned Human evidence")
            question = Some(take(WorkflowDecisionJsonV1.decodeC(invoke("advanceGoalPhase", advance), GoalPhaseOrderedHumanDecision.inputCodec)))
            val decision = question.get.current.asInstanceOf[WorkflowContinuation.Decision[GoalPhaseOrderedHumanDecision.Input]].request
            val reference = ContextReference("human-latch-answer", "1")
            val answer = ContinuationResult(question.get.handle, decision.runId, decision.continuationId, decision.expectedRevision,
              decision.context.snapshot, TypedValue(GoalPhaseOrderedHumanDecision.answerCodec.typeIdentity,
                GoalPhaseOrderedHumanDecision.Answer(choice, "Do not resume this decision path")), reference,
              Vector.empty, ExecutionEvidence(Vector(reference), Some("specification-human")))
            prepare(call => ThreeWorkflowHostEvidencePorts.goalHumanBindingC(call.core.component.get.core.instanceId.canonicalKey,
              question.get, answer, selection.participantIdentity).flatMap(binding => call.evidenceC(NativeHostEvidence.Purpose.GoalHuman,
                ThreeWorkflowHostEvidencePorts.key(reference.identity, reference.revision), reference, "specification-human", binding)))
            val wire = take(NativeWorkflowOperationInputFixture.encodeC(answer, GoalPhaseOrderedHumanDecision.answerCodec))
            val acknowledgement = invoke("submitGoalPhaseOrderedDecision", wire)
            take(WorkflowWaitJsonV1.decodeC(acknowledgement))
            val answered = current()
            answered.history.size shouldBe saved.get.history.size + 1
            answered.suspension shouldBe empty
            retained = Some(invoke("advanceGoalPhase", advance))
            take(WorkflowWaitJsonV1.decodeC(retained.get))
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
