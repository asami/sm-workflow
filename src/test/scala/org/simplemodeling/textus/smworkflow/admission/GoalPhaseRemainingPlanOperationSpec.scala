package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentOrigin}
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
import org.simplemodeling.textus.smworkflow.preparation.{NativeAuthorityPreparation, NativeHumanDecisionPreparation, NativeRemainingPlanPreparation, NativeReviewPreparation}
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeWork as S
import GoalPhaseOrderedHumanDecision as H
import GoalPhasePlanRevision as R
import SplitPhaseExplicitPartitionFixture.take

/** Managed SQL, normal Operations and real Git/SBT own the isolated episode.
  * Human direction and semantic responses are specification-owned inputs.
  * @since   Oct. 20, 2026
  * @version Oct. 27, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class GoalPhaseRemainingPlanOperationSpec extends AnyWordSpec with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  "Remaining Plan adoption through normal Operations" should {
    "retain an accepted Step and the old unanswered Human while adopting only remaining work and reopening the same Session" in {
      new GoalPhaseNativeFindingRecordingSpec().qualifyRemainingPlanAdoption(this)
    }
    "retain the actual blocker review and unanswered Human while adopting one new condition and reopening at RepairStep" in {
      new GoalPhaseNativeFindingRecordingSpec().qualifyReviewRepairAdoption(this)
    }
  }
  "E4 CBD12-PLAN-HUMAN-409 public remaining Plan continuation" should afterWord(
    "in spec:goal-phase-remaining-plan-adoption, example:E4, rules:R17,R18,R19, phase:CBD12-PLAN-HUMAN-409") {
    "make public remaining Plan description and adoption reachable after CBD12-PLAN-HUMAN-409 question issuance" in {
      qualifyRemainingPurpose(this, remaining = true)
    }
  }

  private val _input = V.GoalPhaseStartInput("verification-cbd12-plan-human", "sm-workflow")
  private val _paths = Vector("step-1.txt", "step-2.txt", "step-3.txt")
  private val _plan = O.Plan("verification-cbd12-plan", "1", _input.phaseIdentity, _input.repositoryIdentity,
    _paths.zipWithIndex.map { (path, index) => O.Step("step-" + (index + 1), "Original purpose " + (index + 1),
      Vector(path), if (index == 0) Vector.empty else Vector("step-" + index), Vector("test")) },
    Vector("test"), repairLimit = 2)
  override protected def goal_selected_paths(goal: ThreeWorkflowRuntimeParameters.Goal): Vector[Vector[String]] =
    _plan.steps.map(_.ownedPaths)
  private def _host(root: Path): ThreeWorkflowRuntimeParameters.Definition = {
    val original = ThreeWorkflowNormalHostFixture.definition(root)
    original.copy(goal = original.goal.copy(input = _input, candidatePaths = _paths,
      closure = original.goal.closure.copy(programs = _plan.steps.zipWithIndex.map { (step, index) =>
        GoalPhaseNativeClosure.StepProgram(step.identity, index + 1)
      })))
  }
  private def _episode(root: Path, initialize: Boolean = false)(task: Episode => Unit): Unit =
    _with_runtime_(root, "Goal", Some(_host(root))) { (owner, component, actor, prepare) =>
      if (initialize) prepare(call => for {
        _ <- call.setupC(_setup_commands_(root, "Goal").size)
        selection = _goal_selection_(_input, "spec-cbd12-selection", "spec-cbd12-start")
        _ <- call.createC("goal_selection", NativeWorkflowStartBusinessKey(NativeWorkflowStartInput.Goal(_input),
          selection.participantIdentity), Record.data(ProfileSelectionJsonV1.storageField -> take(ProfileSelectionJsonV1.encodeStorageC(selection))))
        _ <- call.createC("goal_plans", selection.selectionId, Record.data(GoalPhaseSelectedPlanJsonV1.storageField ->
          take(GoalPhaseSelectedPlanJsonV1.encodeStorageC(GoalPhaseNativeRun.SelectedPlan(selection.selectionId, selection.participantIdentity, _plan)))))
      } yield ())
      task(new Episode(owner, component, actor, prepare))
    }
  private def _request[A](work: WorkflowInteraction[A, Nothing]): ContinuationRequest[A] =
    work.current.asInstanceOf[WorkflowContinuation.WorkOrder[A]].request
  private def _business[A](work: WorkflowInteraction[A, Nothing]): A = _request(work).input.get.value
  private def _checks(root: Path): Int = {
    val file = root.resolve("sbt-executions.txt")
    if (Files.exists(file)) Files.readString(file).linesIterator.size else 0
  }
  private final class Episode(owner: Subsystem, component: Component, actor: ExecutionContext,
    prepare: (Setup => Consequence[Unit]) => Unit) {
    def call(operation: String, properties: List[Property]) = owner.executeOperationResponseInContext(
      Request.of(component = component.name, service = "GoalPhaseService", operation = operation, properties = properties), actor)
    def invoke[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A = read_value[A](take(call(operation, properties)))
    def advance(id: String): V.GoalPhaseAdvanceResult = invoke[V.GoalPhaseAdvanceResult]("advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(id))
    def current(id: String): InstanceRecord = {
      var saved = Option.empty[InstanceRecord]
      prepare(call => call.goalCurrentC(InstanceIdentity(id)).map(value => { saved = Some(value); () }))
      saved.get
    }
    def work(id: String): WorkflowInteraction[S.Request, Nothing] = {
      var saved = Option.empty[WorkflowInteraction[S.Request, Nothing]]
      prepare(call => call.goalWorkC(InstanceIdentity(id)).map(value => { saved = Some(value); () }))
      saved.get
    }
    def question(id: String): WorkflowInteraction[H.Input, Nothing] = {
      var saved = Option.empty[WorkflowInteraction[H.Input, Nothing]]
      prepare(call => call.goalQuestionC(InstanceIdentity(id)).map(value => { saved = Some(value); () }))
      saved.get
    }
    def retained(id: String): WorkflowInteraction[H.Input, Nothing] = {
      var saved = Option.empty[WorkflowInteraction[H.Input, Nothing]]
      prepare(call => for {
        catalog <- GoalPhaseRuntimeCatalog.loadC()
        storage <- WorkflowComponentStorage.bindC(ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin),
          SmWorkflowComponent.componentId, _configuration_, Vector(catalog.definition), _collection_("instances"), _collection_("continuations"))
        reader <- storage.bindDecisionC(H.completionOperation, H.inputCodec)
        value <- reader.loadC(org.goldenport.cncf.workflow.ContinuationIdentity(id))(using call.executionContext)
      } yield { saved = value; () })
      saved.get
    }
    def submit(work: WorkflowInteraction[S.Request, Nothing], outcome: String = "completed",
      proposal: Option[R.Proposal] = None, displayed: Option[V.GoalPhaseAdvanceResult] = None): ContinuationResult[S.Result] = {
      val order = _request(work)
      val reference = ContextReference("spec-cbd12-" + _business(work).kind, order.expectedRevision.value)
      val value = S.Result(outcome, "SPECIFICATION-OWNED semantic response", Vector.empty, planRevision = proposal)
      val evidence = ExecutionEvidence(Vector(reference), Some(if (_business(work).kind.startsWith("Review")) "spec-reviewer" else "participant"))
      val result = ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision, order.context.snapshot,
        TypedValue(S.resultCodec.typeIdentity, value), reference, Vector.empty, evidence)
      displayed.foreach(display => prepare(call => for {
        prepared <- NativeReviewPreparation.goalC(display.work.get.review.get.reviewContext.get,
          V.GoalPhaseWorkResult(outcome, value.summary, Vector.empty, None, None, None, None, None, None, None, None,
            dependencyValidation = None, diagnosticRepair = None),
          V.WorkflowArtifactReference(reference.identity, reference.revision),
          V.WorkflowExecutionEvidence(Vector(V.WorkflowArtifactReference(reference.identity, reference.revision)), evidence.workerIdentity, None, None),
          "normal-development-host", call.executionContext.clock.instant().minusSeconds(60))
        _ <- call.createC("host_evidence", prepared.recordIdentity, prepared.record)
      } yield ()))
      invoke[V.GoalPhaseWorkSubmissionResult]("submitGoalPhaseWorkResult", take(NativeWorkflowOperationInputFixture.submissionC(result))).accepted shouldBe true
      result
    }
    def authority(id: String, displayed: V.GoalPhaseAdvanceResult): Unit = {
      val revision = current(id).revision.value.toString
      prepare(call => for {
      prepared <- NativeAuthorityPreparation.goalC(displayed.authorityContext.get,
        V.WorkflowArtifactReference("spec-cbd12-authority", revision), "participant",
        "normal-development-host", call.executionContext.clock.instant().minusSeconds(60))
      _ <- call.createC("host_evidence", prepared.recordIdentity, prepared.record)
      } yield ())
    }
    def human(displayed: V.GoalPhaseAdvanceResult, answer: ContinuationResult[H.Answer]): Unit = prepare(call => for {
      prepared <- NativeHumanDecisionPreparation.goalC(displayed.human.get.humanDecisionContext.get,
        V.GoalPhaseOrderedDecisionAnswer(answer.result.value.choice, answer.result.value.rationale),
        V.WorkflowArtifactReference(answer.resultReference.identity, answer.resultReference.revision),
        V.WorkflowExecutionEvidence(Vector.empty, answer.evidence.workerIdentity, None, None),
        "participant", "normal-development-host", call.executionContext.clock.instant().minusSeconds(60))
      _ <- call.createC("host_evidence", prepared.recordIdentity, prepared.record)
    } yield ())
    def remaining(description: V.GoalPhaseRemainingPlanDescriptionResult, input: V.GoalPhaseRemainingPlanAdoptionInput): Unit =
      prepare(call => for {
        prepared <- NativeRemainingPlanPreparation.prepareC(description, input, input.reference,
          V.WorkflowExecutionEvidence(Vector(input.reference), Some("participant"), None, None), "participant",
          "normal-development-host", call.executionContext.clock.instant().minusSeconds(60))
        _ <- call.createC("host_evidence", prepared.recordIdentity, prepared.record)
      } yield ())
  }

  /** Two distinct public continuations share the accepted-result-before-Human reproducer. */
  private[admission] def qualifyRemainingPurpose(narrator: GivenWhenThen, remaining: Boolean): Unit = {
    narrator.Given("docs/spec/goal-phase-plan-revision.md and goal-phase-remaining-plan-adoption.md R17–R19 E4: an isolated actual accepted prefix and one accepted decision-needed result, with no issued Human")
    val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/sm-workflow/work/cbd12-plan").toAbsolutePath), "episode-").toRealPath()
    var session = ""
    var original = Option.empty[S.Request]
    var accepted = Option.empty[ContinuationResult[S.Result]]
    var before = Option.empty[InstanceRecord]
    var pending = Option.empty[WorkflowInteraction[H.Input, Nothing]]
    var displayed = Option.empty[V.GoalPhaseAdvanceResult]
    val raw = _plan.copy(revision = "remaining-2", steps = _plan.steps.zipWithIndex.map { (step, index) =>
      if (index == 0) step else step.copy(purpose = "New remaining purpose " + step.identity)
    })
    val proposal = R.Proposal("spec-cbd12-proposal", "1", "SPECIFICATION-OWNED remaining purpose decision", raw)
    _episode(root, initialize = true) { e =>
      session = e.invoke[V.GoalPhaseStartResult]("startGoalPhase", NativeWorkflowOperationInputFixture.goal(_input)).sessionId
      e.advance(session)
      Files.writeString(root.resolve("repository/step-1.txt"), "Accepted first Step\n")
      e.submit(e.work(session))
      val review = e.advance(session)
      e.submit(e.work(session), "accept", displayed = Some(review))
      val authority = e.advance(session)
      e.authority(session, authority)
      e.advance(session).work.get.stepIdentity shouldBe Some("step-2")
      original = Some(_business(e.work(session)))
      original.get.completed.size shouldBe 1
      accepted = Some(e.submit(e.work(session), "decision-needed", Some(proposal)))
      before = Some(e.current(session))
      before.get.suspension shouldBe None
      _checks(root) shouldBe 1
    }
    _episode(root) { e =>
      narrator.When("one ordinary Advance reopens the exact accepted result and issues its scoped Human without another semantic Submit")
      displayed = Some(e.advance(session))
      pending = Some(e.question(session))
      val input = pending.get.current.asInstanceOf[WorkflowContinuation.Decision[H.Input]].request.input.get.value
      narrator.Then("the actual question binds the exact accepted result, changed suffix and immutable counts once")
      displayed.get.kind shouldBe "Human"
      displayed.get.sessionId shouldBe session
      displayed.get.human.get.choices shouldBe Vector("AdoptPlanRevision", "RejectPlanRevision", "Defer", "Cancel")
      input.work shouldBe original.get
      input.result shouldBe accepted.get.result.value
      input.requestingResult shouldBe accepted.get.resultReference
      input.planRevision.get.revisionScope shouldBe Some(R.remainingPurposeScope)
      input.planRevision.get.proposal shouldBe proposal
      input.planRevision.get.plan.effectiveAcceptance shouldBe original.get.selectedPlan.effectiveAcceptance
      val presentation = pending.get.current.asInstanceOf[WorkflowContinuation.Decision[H.Input]].presentation.currentSituation
      presentation should include("remaining-purpose")
      presentation should include("step-2")
      presentation should include("step-3")
      e.current(session).history.take(before.get.history.size) shouldBe before.get.history
      e.advance(session) shouldBe displayed.get
      e.question(session) shouldBe pending.get
      _checks(root) shouldBe 1
    }
    _episode(root) { e =>
      narrator.When("retained readback preserves the saved scope and independently admitted direction adopts through the selected public route")
      e.advance(session) shouldBe displayed.get
      val question = e.question(session)
      question shouldBe pending.get
      val request = question.current.asInstanceOf[WorkflowContinuation.Decision[H.Input]].request
      val answer = ContinuationResult(question.handle, request.runId, request.continuationId, request.expectedRevision, request.context.snapshot,
        TypedValue(H.answerCodec.typeIdentity, H.Answer("AdoptPlanRevision", "SPECIFICATION-OWNED exact remaining-purpose direction")),
        ContextReference("spec-cbd12-answer", "1"), Vector.empty, ExecutionEvidence(Vector.empty, Some("spec-human")))
      def _answer_ = e.call(H.completionOperation.operation, take(NativeWorkflowOperationInputFixture.submissionC(answer)))
      val current = e.current(session)
      _answer_.isSuccess shouldBe false
      take(org.goldenport.cncf.workflow.WorkflowInstanceRecordJsonV1.encodeC(e.current(session))) shouldBe
        take(org.goldenport.cncf.workflow.WorkflowInstanceRecordJsonV1.encodeC(current))
      if (remaining) {
        val public = R.publicProposal(Some(proposal)).get
        val description = e.invoke[V.GoalPhaseRemainingPlanDescriptionResult](GoalPhaseRemainingPlanAdoption.describeOperation,
          List(Property("input", V.GoalPhaseRemainingPlanDescriptionInput(session, public).toRecord(), None)))
        val target = take(GoalPhaseRemainingPlanAdoption.targetC(description.target))
        target.completed shouldBe original.get.completed
        val input = V.GoalPhaseRemainingPlanAdoptionInput(session, description.target, public,
          "SPECIFICATION-OWNED separate remaining Plan directive", V.WorkflowArtifactReference("spec-cbd12-remaining", "1"))
        def _adopt_(value: V.GoalPhaseRemainingPlanAdoptionInput) = e.call(GoalPhaseRemainingPlanAdoption.adoptionOperation,
          List(Property("input", value.toRecord(), None)))
        _adopt_(input).isSuccess shouldBe false
        e.remaining(description, input)
        _adopt_(input.copy(rationale = "Crossed independent evidence")).isSuccess shouldBe false
        e.invoke[V.GoalPhaseRemainingPlanAdoptionResult](GoalPhaseRemainingPlanAdoption.adoptionOperation,
          List(Property("input", input.toRecord(), None))).accepted shouldBe true
        _adopt_(input).isSuccess shouldBe false
      } else {
        e.human(displayed.get, answer)
        val crossed = answer.copy(result = TypedValue(H.answerCodec.typeIdentity, answer.result.value.copy(choice = "RejectPlanRevision")))
        e.call(H.completionOperation.operation, take(NativeWorkflowOperationInputFixture.submissionC(crossed))).isSuccess shouldBe false
        e.invoke[V.GoalPhaseOrderedDecisionSubmissionResult](H.completionOperation.operation,
          take(NativeWorkflowOperationInputFixture.submissionC(answer))).accepted shouldBe true
      }
      _answer_.isSuccess shouldBe false
      e.retained(request.continuationId.value) shouldBe pending.get
      _checks(root) shouldBe 1
    }
    _episode(root) { e =>
      narrator.When("a separate ordinary Advance reopens retained adoption and issues only the same current unaccepted Step")
      val next = e.advance(session)
      val work = _business(e.work(session))
      narrator.Then("generation increments once with the original Session, prefix, base, issues, policies and repair counts")
      next.sessionId shouldBe session
      work.kind shouldBe "ImplementStep"
      work.stepIdentity shouldBe original.get.stepIdentity
      work.selectedPlan.copy(effectiveAcceptance = None) shouldBe raw
      work.selectedPlan.effectiveAcceptance shouldBe original.get.selectedPlan.effectiveAcceptance
      work.selectedPlan.steps.head shouldBe original.get.selectedPlan.steps.head
      work.completed shouldBe original.get.completed
      work.originalBase shouldBe original.get.originalBase
      work.repairProgress shouldBe original.get.repairProgress
      work.issues shouldBe original.get.issues
      work.generation shouldBe original.get.generation + 1
      e.current(session).history.take(before.get.history.size) shouldBe before.get.history
      e.advance(session) shouldBe next
      _checks(root) shouldBe 1
    }
  }
}
