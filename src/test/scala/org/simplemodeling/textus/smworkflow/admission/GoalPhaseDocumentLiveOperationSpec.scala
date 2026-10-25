package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.nio.file.{Files, Path, StandardOpenOption}
import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.preparation.{NativeGoalStartInput, NativeHumanDecisionPreparation}
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseDocumentSource as D
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeWork as S
import GoalPhaseOrderedHumanDecision as H
import GoalPhaseAcceptanceBoundary as B
import SplitPhaseExplicitPartitionFixture.take

/** Public Operation-created history in isolated Git-only verification episodes.
  * Fixture-ignored documents exercise acquisition and decisions, not tracked
  * document writeback, Step commits, SBT/provider execution or full B2 acceptance.
  * @since   Oct. 17, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseDocumentLiveOperationSpec extends AnyWordSpec with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  private val _source = D.Source("verification-inputs/phase.md", "verification-inputs/checklist.md")
  private val _input = V.GoalPhaseStartInput("verification-p61-document-live", "sm-workflow")
  private val _plan = O.Plan("verification-live-document-plan", "1", _input.phaseIdentity, _input.repositoryIdentity,
    Vector(_step("step-1"), _step("step-2")), Vector("test"), repairLimit = 1)

  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json =
    TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString), kinds = TypedGitHostPlanFixture.observations)

  private def _step(identity: String): O.Step =
    O.Step(identity, "Implement " + identity, Vector("value.txt"), Vector.empty, Vector("test"))
  private def _root(): Path = Files.createTempDirectory(Files.createDirectories(
    Path.of("target/sm-workflow/work/document-live").toAbsolutePath), "episode-").toRealPath()
  private def _host(root: Path): ThreeWorkflowRuntimeParameters.Definition = {
    val host = ThreeWorkflowNormalHostFixture.definition(root)
    host.copy(goal = host.goal.copy(input = _input))
  }
  private def _phase(plan: O.Plan, reverse: Boolean = false): String = {
    val bindings = plan.steps.map { step =>
      val items = Vector("VERIFY-" + step.identity + "-A", "VERIFY-" + step.identity + "-B")
      Json.obj("step" -> Json.fromString(step.identity), "items" -> (if (reverse) items.reverse else items).asJson)
    }
    "# Isolated live verification Phase\n\nNarrative is presentation only.\n\n```sm-workflow-plan\n" +
      Json.obj("schemaVersion" -> Json.fromString("sm-workflow.phase-plan.v1"), "plan" -> plan.asJson,
        "bindings" -> Json.fromValues(bindings)).spaces2 + "\n```\n"
  }
  private def _checklist(changed: Boolean = false, checked: Boolean = false, reverse: Boolean = false): String = {
    val items = Vector("step-1", "step-2", "step-3").flatMap(step => Vector("A", "B").map { suffix =>
      val acceptance = if (changed && step == "step-1" && suffix == "A") "Changed protected acceptance"
        else "Accept " + step + " " + suffix
      "- [" + (if (checked) "x" else " ") + "] VERIFY-" + step + "-" + suffix + ": " + acceptance
    })
    "# Isolated verification checklist\n\n" + (if (reverse) items.reverse else items).mkString("\n") + "\n"
  }
  private def _write_phase(root: Path, plan: O.Plan, reverse: Boolean = false): Unit =
    Files.writeString(root.resolve("repository").resolve(_source.phasePath), _phase(plan, reverse))
  private def _write_checklist(root: Path, text: String): Unit =
    Files.writeString(root.resolve("repository").resolve(_source.checklistPath), text)
  private def _documents(root: Path): Unit = {
    val repository = root.resolve("repository")
    Files.writeString(repository.resolve(".git/info/exclude"), "\n/verification-inputs/\n", StandardOpenOption.APPEND)
    Files.createDirectories(repository.resolve("verification-inputs"))
    _write_phase(root, _plan)
    _write_checklist(root, _checklist())
  }
  private def _prepared(): Record = {
    val declaration = Json.obj("schemaVersion" -> Json.fromString(D.declarationSchema),
      "phasePath" -> Json.fromString(_source.phasePath), "checklistPath" -> Json.fromString(_source.checklistPath))
    val files = Map("input.json" -> _input.asJson.noSpaces, "plan.json" -> declaration.noSpaces)
    take(NativeGoalStartInput.prepareC(Map("input" -> "input.json", "plan" -> "plan.json",
      "selection-id" -> "verification-live-document-selection", "participant" -> "participant", "origin" -> "ExplicitHuman",
      "idempotency-key" -> "verification-live-document-start", "selection-collection" -> "entity:smworkflow.cli.goal_selection",
      "plan-collection" -> "entity:smworkflow.cli.goal_plans"), path =>
      files.get(path).map(Consequence.success).getOrElse(Consequence.stateConflict("Unselected verification file"))))
  }
  private def _install_c(call: Setup): Consequence[Unit] = {
    val entries = _prepared().getVector("datastore").get.map(_.asInstanceOf[Record])
    entries.zip(Vector("goal_selection", "goal_plans")).foldLeft(Consequence.unit) { case (prior, (entry, name)) =>
      val row = entry.getVector("records").get.head.asInstanceOf[Record]
      prior.flatMap(_ => call.createC(name, row.getString("id").get, row.removeKeys(Set("id"))))
    }
  }
  private def _storage_c(call: Setup): Consequence[WorkflowComponentStorage] = for {
    catalog <- GoalPhaseRuntimeCatalog.loadC()
    storage <- WorkflowComponentStorage.bindC(ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin),
      SmWorkflowComponent.componentId, _configuration_, Vector(catalog.definition), _collection_("instances"), _collection_("continuations"))
  } yield storage
  private def _with_episode(root: Path, initialize: Boolean = true)(task: Episode => Unit): Unit =
    _with_runtime_(root, "DocumentLive", Some(_host(root))) { (owner, component, actor, prepare) =>
      if (initialize) prepare(call => call.setupC(_setup_commands_(root, "DocumentLive").size).flatMap { _ =>
        _documents(root)
        _install_c(call)
      })
      task(new Episode(owner, component, actor, prepare))
    }

  private final class Episode(owner: Subsystem, component: org.goldenport.cncf.component.Component,
    actor: ExecutionContext, prepare: (Setup => Consequence[Unit]) => Unit) {
    def call(operation: String, properties: List[Property]) = owner.executeOperationResponseInContext(
      Request.of(component = component.name, service = "GoalPhaseService", operation = operation, properties = properties), actor)
    def invoke[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
      read_value[A](take(call(operation, properties)))
    def start(): V.GoalPhaseStartResult = invoke[V.GoalPhaseStartResult]("startGoalPhase", NativeWorkflowOperationInputFixture.goal(_input))
    def advance(identity: String): V.GoalPhaseAdvanceResult = invoke[V.GoalPhaseAdvanceResult]("advanceGoalPhase",
      NativeWorkflowOperationInputFixture.advance(identity))
    def current(identity: String): InstanceRecord = {
      var actual = Option.empty[InstanceRecord]
      prepare(call => call.goalCurrentC(InstanceIdentity(identity)).map(value => { actual = Some(value); () }))
      actual.get
    }
    def work(identity: String): WorkflowInteraction[S.Request, Nothing] = {
      var actual = Option.empty[WorkflowInteraction[S.Request, Nothing]]
      prepare(call => call.goalWorkC(InstanceIdentity(identity)).map(value => { actual = Some(value); () }))
      actual.get
    }
    def question(identity: String): WorkflowInteraction[H.Input, Nothing] = {
      var actual = Option.empty[WorkflowInteraction[H.Input, Nothing]]
      prepare(call => call.goalQuestionC(InstanceIdentity(identity)).map(value => { actual = Some(value); () }))
      actual.get
    }
    def savedStart(identity: String): GoalPhaseNativeRun.Payload = {
      var actual = Option.empty[GoalPhaseNativeRun.Payload]
      prepare(call => for {
        storage <- _storage_c(call)
        starts <- storage.bindMechanicalC(GoalPhaseNativeRun.startOperation, GoalPhaseNativeRun.codec)
        retained <- starts.loadRetainedC(WorkflowMechanicalTransition.Reference(identity + ":ordered-start", "1"))(using call.executionContext)
        payload <- retained.map(value => Consequence.success(value.receipt.payload.value))
          .getOrElse(Consequence.stateConflict("No Operation-created verification Start"))
      } yield { actual = Some(payload); () })
      actual.get
    }
    def selection(from: InstanceRecord): Option[GoalPhaseNativeDocumentPlan.Payload] = {
      var actual = Option.empty[GoalPhaseNativeDocumentPlan.Payload]
      prepare(call => for {
        storage <- _storage_c(call)
        selections <- storage.bindMechanicalC(GoalPhaseNativeRun.advanceOperation, GoalPhaseNativeDocumentPlan.codec)
        retained <- selections.loadRetainedC(GoalPhaseNativeDocumentPlan.reference(from))(using call.executionContext)
      } yield { actual = retained.map(_.receipt.payload.value); () })
      actual
    }
    def observe(): RepositorySyncManagedGitObservation.Observed = {
      var actual = Option.empty[RepositorySyncManagedGitObservation.Observed]
      prepare(call => call.observeC().map(value => { actual = Some(value); () }))
      actual.get
    }
    def complete(work: WorkflowInteraction[S.Request, Nothing]): ContinuationResult[S.Result] = {
      val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request
      val reference = ContextReference("verification-live-semantic-result", order.expectedRevision.value)
      val result = ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision,
        order.context.snapshot, TypedValue(S.resultCodec.typeIdentity,
          S.Result("completed", "Specification-owned assigned implementation is complete", Vector.empty)),
        reference, Vector.empty, ExecutionEvidence(Vector(reference), Some("specification-implementer")))
      invoke[V.GoalPhaseWorkSubmissionResult]("submitGoalPhaseWorkResult",
        take(NativeWorkflowOperationInputFixture.submissionC(result))).accepted shouldBe true
      result
    }
    def answer(identity: String, displayed: V.GoalPhaseAdvanceResult, choice: String): ContinuationResult[H.Answer] = {
      val question = this.question(identity)
      val order = question.current.asInstanceOf[WorkflowContinuation.Decision[H.Input]].request
      val reference = ContextReference("verification-live-human-" + choice, order.expectedRevision.value)
      val answer = ContinuationResult(question.handle, order.runId, order.continuationId, order.expectedRevision,
        order.context.snapshot, TypedValue(H.answerCodec.typeIdentity, H.Answer(choice, "Explicit verification-owned Human direction")),
        reference, Vector.empty, ExecutionEvidence(Vector(reference), Some("specification-human")))
      prepare(call => for {
        prepared <- NativeHumanDecisionPreparation.goalC(displayed.human.get.humanDecisionContext.get,
          V.GoalPhaseOrderedDecisionAnswer(choice, answer.result.value.rationale),
          V.WorkflowArtifactReference(reference.identity, reference.revision),
          V.WorkflowExecutionEvidence(Vector.empty, answer.evidence.workerIdentity, None, None),
          "participant", "normal-development-host", call.executionContext.clock.instant().minusSeconds(60))
        _ <- call.createC("host_evidence", prepared.recordIdentity, prepared.record)
      } yield ())
      invoke[V.GoalPhaseOrderedDecisionSubmissionResult]("submitGoalPhaseOrderedDecision",
        take(NativeWorkflowOperationInputFixture.submissionC(answer))).accepted shouldBe true
      answer
    }
  }
  private def _input_of(work: WorkflowInteraction[S.Request, Nothing]): S.Request =
    work.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value
  private def _context_of(work: WorkflowInteraction[S.Request, Nothing]): ContextBundle =
    work.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.context
  private def _document_events(record: InstanceRecord): Vector[HistoryEntry] =
    record.history.filter(_.causation.value.startsWith("ordered-document-plan-"))
  private def _candidate_events(record: InstanceRecord): Vector[HistoryEntry] =
    record.history.filter(_.causation.value.startsWith("ordered-candidate:"))
  private def _same_instance(actual: InstanceRecord, expected: InstanceRecord): Unit = {
    (actual.definition.workflowIdentity, actual.definition.workflowRevision, actual.definition.sourceCorrelation,
      actual.definition.producerAbiIdentity, actual.definition.workflowAbiIdentity, actual.definition.bootstrapAbiIdentity,
      actual.definition.producerRevision) shouldBe
      (expected.definition.workflowIdentity, expected.definition.workflowRevision, expected.definition.sourceCorrelation,
        expected.definition.producerAbiIdentity, expected.definition.workflowAbiIdentity, expected.definition.bootstrapAbiIdentity,
        expected.definition.producerRevision)
    actual.copy(definition = expected.definition) shouldBe expected
  }

  "Live document selection through normal GoalPhase Operations" should {
    "select a changed unissued purpose and reordered added tail and retain the exact issue across reopening" in {
      Given("an Operation-created Start with isolated source documents and an original clean native base")
      val root = _root()
      var identity = ""
      var work = Option.empty[WorkflowInteraction[S.Request, Nothing]]
      var pending = Option.empty[InstanceRecord]
      var initial = Option.empty[InstanceRecord]
      val proposed = _plan.copy(revision = "2", steps = Vector(_step("step-2").copy(purpose = "Implement the latest selected purpose"),
        _step("step-3"), _step("step-1")))
      _with_episode(root) { episode =>
        identity = episode.start().sessionId
        initial = Some(episode.current(identity))
        val saved = episode.savedStart(identity)
        When("the purpose and valid unissued tail change before the first public Advance")
        _write_phase(root, proposed)
        val displayed = episode.advance(identity)
        work = Some(episode.work(identity))
        pending = Some(episode.current(identity))
        val selected = episode.selection(initial.get).get
        Then("the actual canonical selection and real issued input use the fresh Plan with the original base and generation")
        displayed.kind shouldBe "Request"
        _input_of(work.get).selectedPlan shouldBe take(B.captureC(None, proposed))
        _input_of(work.get).stepIdentity shouldBe Some("step-2")
        _input_of(work.get).purpose shouldBe "Implement the latest selected purpose"
        _input_of(work.get).generation shouldBe 1
        _input_of(work.get).originalBase shouldBe saved.base
        _input_of(work.get).completed shouldBe empty
        selected.sourceRevision shouldBe initial.get.revision.value.toString
        selected.plan shouldBe _input_of(work.get).selectedPlan
        selected.documents shouldBe _input_of(work.get).documents.get
        selected.conflicts shouldBe empty
        selected.documents.source shouldBe _source
        _document_events(pending.get).map(_.causation.value) shouldBe Vector("ordered-document-plan-selected")
        pending.get.history.find(_.causation.value == "ordered-document-plan-selected").get.currentProgression shouldBe
          Some(ProgressionReference("phase.Implementation"))
        saved.plan shouldBe take(B.captureC(None, _plan))
        saved.base shouldBe _input_of(work.get).originalBase
      }
      _with_episode(root, initialize = false) { episode =>
        When("a reopened normal runtime displays the actual pending assignment")
        val displayed = episode.advance(identity)
        val retained = episode.work(identity)
        Then("the exact saved input, continuation and context remain and no new selection or issue is created")
        displayed.kind shouldBe "Request"
        _input_of(retained) shouldBe _input_of(work.get)
        _context_of(retained) shouldBe _context_of(work.get)
        retained.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.continuationId shouldBe
          work.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.continuationId
        _same_instance(episode.current(identity), pending.get)
        _document_events(episode.current(identity)).size shouldBe 1
      }
    }

    "retain pending acceptance and accept its genuine result before asking Human about a later contradiction" in {
      Given("an actual pending ImplementStep selected from the original checklist")
      val root = _root()
      _with_episode(root) { episode =>
        val identity = episode.start().sessionId
        episode.advance(identity).kind shouldBe "Request"
        val work = episode.work(identity)
        val pending = episode.current(identity)
        val base = episode.observe()
        When("protected checklist acceptance changes while the assignment remains pending")
        _write_checklist(root, _checklist(changed = true))
        val redisplayed = episode.advance(identity)
        val same = episode.work(identity)
        Then("pending Advance displays only the saved assignment with its old acceptance and context")
        redisplayed.kind shouldBe "Request"
        _input_of(same) shouldBe _input_of(work)
        _context_of(same) shouldBe _context_of(work)
        _context_of(same).summary should include("Accept step-1 A")
        _context_of(same).summary should not include "Changed protected acceptance"
        _same_instance(episode.current(identity), pending)
        When("normal Submit accepts that actual assigned completion and a later Advance selects the new documents")
        val submitted = episode.complete(work)
        val completed = episode.current(identity)
        val displayed = episode.advance(identity)
        val question = episode.question(identity).current.asInstanceOf[WorkflowContinuation.Decision[H.Input]].request
        val current = episode.current(identity)
        Then("a genuine Human conflict carries the real prior input and result before any native candidate or checks")
        completed.currentProgression shouldBe Some(ProgressionReference("phase.InspectCandidate"))
        completed.suspension shouldBe None
        displayed.kind shouldBe "Human"
        displayed.human.get.humanDecisionContext should not be empty
        question.input.get.value.work shouldBe _input_of(work)
        question.input.get.value.requestingResult shouldBe submitted.resultReference
        question.input.get.value.result shouldBe submitted.result.value
        question.input.get.value.reason should include("document-acceptance:step-1")
        current.currentProgression shouldBe Some(ProgressionReference("phase.Decision"))
        _document_events(current).map(_.causation.value) shouldBe Vector("ordered-document-plan-conflicted")
        _candidate_events(current) shouldBe empty
        current.history.exists(_.causation.value.startsWith("ordered-validation:")) shouldBe false
        episode.observe().head shouldBe base.head
        episode.observe().tree shouldBe base.tree
        episode.observe().dirty shouldBe empty
      }
    }

    "resume a reconciled document conflict at actual InspectCandidate through evidenced answer Submit without native execution" in {
      Given("a completed actual implementation followed by a valid protected document contradiction")
      val root = _root()
      _with_episode(root) { episode =>
        val identity = episode.start().sessionId
        episode.advance(identity)
        val work = episode.work(identity)
        val submitted = episode.complete(work)
        _write_checklist(root, _checklist(changed = true))
        val displayed = episode.advance(identity)
        val pending = episode.current(identity)
        val question = episode.question(identity)
        When("the checklist is reconciled and normal Human preparation and Submit admit actual evidenced Resume")
        _write_checklist(root, _checklist())
        val answer = episode.answer(identity, displayed, "ResumeAssignedWork")
        val answered = episode.current(identity)
        Then("the real answer returns the exact native source with original generation and no implicit candidate execution")
        displayed.kind shouldBe "Human"
        question.current.asInstanceOf[WorkflowContinuation.Decision[H.Input]].request.input.get.value.requestingResult shouldBe
          submitted.resultReference
        answered.currentProgression shouldBe Some(ProgressionReference("phase.InspectCandidate"))
        answered.suspension shouldBe None
        answered.history.size shouldBe pending.history.size + 1
        answered.history.take(pending.history.size) shouldBe pending.history
        answered.history.last.causation.value shouldBe answer.resultReference.identity
        _input_of(work).generation shouldBe 1
        _input_of(work).repairDirective shouldBe None
        _candidate_events(answered) shouldBe empty
        answered.history.exists(_.causation.value.startsWith("ordered-validation:")) shouldBe false
        episode.savedStart(identity).base shouldBe _input_of(work).originalBase
      }
    }

    for (choice <- Vector("Defer", "Cancel")) {
      s"retain evidenced document $choice and return stable Wait without another question or effect" in {
        Given("a real completed implementation and its actual document-conflict Human question")
        val root = _root()
        _with_episode(root) { episode =>
          val identity = episode.start().sessionId
          episode.advance(identity)
          val work = episode.work(identity)
          val submitted = episode.complete(work)
          _write_checklist(root, _checklist(changed = true))
          val displayed = episode.advance(identity)
          val pending = episode.current(identity)
          When("normal evidenced Human Submit records the explicit direction and Advance is called again")
          val answer = episode.answer(identity, displayed, choice)
          val answered = episode.current(identity)
          val first = episode.advance(identity)
          val second = episode.advance(identity)
          Then("the real answer and previous result remain while both Advances wait without new native history")
          first.kind shouldBe "Wait"
          second.kind shouldBe "Wait"
          first.condition shouldBe Some("phase.Decision")
          second.condition shouldBe first.condition
          answered.suspension shouldBe None
          answered.history.take(pending.history.size) shouldBe pending.history
          answered.history.last.causation.value shouldBe answer.resultReference.identity
          answered.history.exists(_.causation.value == submitted.resultReference.identity) shouldBe true
          _same_instance(episode.current(identity), answered)
          _candidate_events(answered) shouldBe empty
          _document_events(answered).size shouldBe 1
        }
      }
    }

    for (malformed <- Vector("malformed-plan", "duplicate-item")) {
      s"fail ordinary Advance for $malformed after saved work without fabricating Human or native effects" in {
        Given("a genuine accepted implementation result at the native inspection frontier")
        val root = _root()
        _with_episode(root) { episode =>
          val identity = episode.start().sessionId
          episode.advance(identity)
          val submitted = episode.complete(episode.work(identity))
          val completed = episode.current(identity)
          When("a relevant selected document becomes malformed and normal Advance acquires it")
          if (malformed == "malformed-plan") Files.writeString(root.resolve("repository").resolve(_source.phasePath),
            "```sm-workflow-plan\n{\n```\n")
          else _write_checklist(root, _checklist() + "- [ ] VERIFY-step-1-A: Repeated selected identity\n")
          val result = episode.call("advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(identity))
          Then("ordinary input failure preserves the accepted result and exact actual frontier without a Human question")
          result.isSuccess shouldBe false
          _same_instance(episode.current(identity), completed)
          completed.currentProgression shouldBe Some(ProgressionReference("phase.InspectCandidate"))
          completed.suspension shouldBe None
          completed.history.last.causation.value shouldBe submitted.resultReference.identity
          _document_events(completed) shouldBe empty
          _candidate_events(completed) shouldBe empty
        }
      }
    }

    "ignore checkbox narrative and membership presentation order changes before first dispatch" in {
      Given("an actual Start whose document acceptance is captured while checkboxes remain unchecked")
      val root = _root()
      _with_episode(root) { episode =>
        val identity = episode.start().sessionId
        val initial = episode.current(identity)
        val saved = episode.savedStart(identity)
        When("only checkbox flags, narrative and presentation order change before normal Advance")
        _write_phase(root, _plan, reverse = true)
        Files.writeString(root.resolve("repository").resolve(_source.phasePath), "Extra narrative only.\n" + _phase(_plan, reverse = true))
        _write_checklist(root, _checklist(checked = true, reverse = true))
        val displayed = episode.advance(identity)
        val work = episode.work(identity)
        val current = episode.current(identity)
        Then("semantic acceptance stays original and no mechanical selection or evidence arises solely from presentation")
        displayed.kind shouldBe "Request"
        _input_of(work).selectedPlan shouldBe saved.plan
        _input_of(work).documents shouldBe saved.documents
        _input_of(work).documents.get.items.forall(!_.checked) shouldBe true
        _input_of(work).completed shouldBe empty
        _context_of(work).summary should include("Accept step-1 A")
        episode.selection(initial) shouldBe None
        _document_events(current) shouldBe empty
        current.history.size shouldBe initial.history.size + 1
        current.suspension should not be empty
        episode.observe().dirty shouldBe empty
      }
    }
  }
}
