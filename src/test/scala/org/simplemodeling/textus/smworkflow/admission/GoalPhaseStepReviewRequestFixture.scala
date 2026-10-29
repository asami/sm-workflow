package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.SbtOperation
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.Property
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.value as V
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import GoalPhaseNativeWork as W
import GoalPhaseOrderedProgression as O
import GoalPhaseStepReviewRequest as R
import SplitPhaseExplicitPartitionFixture.take

/** All state originates in normal Start, assigned work and native typed effects.
  * Evidence is owned by these isolated tests, never a live Human or review.
  * @since   Oct. 28, 2026
  * @version Oct. 28, 2026
  * @author  ASAMI, Tomoharu
  */
private[admission] trait GoalPhaseStepReviewRequestFixture extends GoalPhaseValidationRecoveryOperationFixture {
  private val _policy = GoalPhaseStepAssessment.Policy("conditional-documentation", "documentation",
    "conditional", "off", "off", Vector.empty)
  override protected def _recovery_plan: O.Plan = O.Plan("precommit-review-plan", "1", "phase-2", "sm-workflow",
    (1 to 3).toVector.map(index => O.Step("step-" + index, "Complete selected documentation boundary " + index,
      Vector("value.txt"), (1 until index).toVector.map("step-" + _), Vector("compile"), actionPolicy = Some(_policy))),
    Vector("compile"), repairLimit = 10)
  override protected def recoveryHost(root: Path): ThreeWorkflowRuntimeParameters.Definition = {
    val original = super.recoveryHost(root)
    original.copy(goal = original.goal.copy(closure = original.goal.closure.copy(programs =
      _recovery_plan.steps.zipWithIndex.map((step, index) => GoalPhaseNativeClosure.StepProgram(step.identity, index + 1)))))
  }
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val git = TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString), goalPaths = goal.candidatePaths,
      goalMessages = goal.closure.programs.map(program => "Goal Step " + program.step),
      goalSelections = goal.closure.programs.map(_ => goal.candidatePaths))
    TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable,
      goal.stepValidation.checks.map(_.ordinal), Some(git), operation = SbtOperation.Compile)
  }
  protected def new_review_root(name: String): Path = freshRecoveryRoot(name)
  protected def review_executions(root: Path): Vector[Long] = compileExecutions(root)
  protected def review_episode(root: Path, initialize: Boolean = false,
    acknowledgement: Option[String => Consequence[Unit]] = None)(task: ReviewEpisode => Unit): Unit =
    withRecoveryEpisode(root, initialize = initialize, acknowledgement = acknowledgement)(native => task(new ReviewEpisode(native)))

  protected final class ReviewEpisode(val native: RecoveryEpisode) {
    private var _issued_inputs = Map.empty[String, W.Request]
    val root: Path = native.root
    def current(session: String): InstanceRecord = native.current(session)
    def start(): String = native.start().sessionId
    def advance(session: String): V.GoalPhaseAdvanceResult = native.advance(session)
    def call(operation: String, value: Record) = native.call(operation, List(Property("input", value, None)))
    def describe(session: String): V.GoalPhaseStepReviewDescriptionResult =
      native.invoke[V.GoalPhaseStepReviewDescriptionResult](R.describeOperation,
        List(Property("input", V.GoalPhaseStepReviewDescriptionInput(session).toRecord(), None)))
    def request(input: V.GoalPhaseStepReviewRequestInput): V.GoalPhaseStepReviewRequestResult =
      native.invoke[V.GoalPhaseStepReviewRequestResult](R.requestOperation, List(Property("input", input.toRecord(), None)))
    def observation(): RepositorySyncManagedGitObservation.Observed = {
      var selected = Option.empty[RepositorySyncManagedGitObservation.Observed]
      native.prepare(call => call.observeC().map(value => { selected = Some(value); () }))
      selected.get
    }
    def retainedChecks(target: R.Target): GoalPhaseNativeValidation.Saved =
      native.retained(target.sessionId, target.validationSourceRevision, "validation").get
    def complete(request: V.GoalPhaseAdvanceResult, content: String, name: String): Unit = {
      Files.writeString(root.resolve("repository/value.txt"), content)
      _submit(request, "completed", name)
    }
    def review(request: V.GoalPhaseAdvanceResult, outcome: String, name: String): ContextReference =
      _submit(request, outcome, name)
    def decide(request: V.GoalPhaseAdvanceResult, name: String): Unit = { _submit(request, "decision-needed", name); () }

    private def _issued_input(request: V.GoalPhaseAdvanceResult): W.Request = {
      val continuation = request.issuedWorkContext.get.continuationId
      _issued_inputs.getOrElse(continuation, {
        val (_, order) = _issued_order(request)
        val input = order.input.get.value
        _issued_inputs = _issued_inputs.updated(continuation, input)
        input
      })
    }
    def generation(request: V.GoalPhaseAdvanceResult): Long = _issued_input(request).generation
    private def _issued_order(request: V.GoalPhaseAdvanceResult) = {
      var selected = Option.empty[WorkflowInteraction[W.Request, Nothing]]
      native.prepare(call => call.goalWorkC(InstanceIdentity(request.sessionId)).map(value => { selected = Some(value); () }))
      val work = selected.get
      val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[W.Request]].request
      order.continuationId.value shouldBe request.issuedWorkContext.get.continuationId
      (work, order)
    }
    private def _submit(request: V.GoalPhaseAdvanceResult, outcome: String, name: String): ContextReference = {
      val (work, order) = _issued_order(request)
      val input = order.input.get.value
      _issued_inputs = _issued_inputs.updated(request.issuedWorkContext.get.continuationId, input)
      val issues = input.issues.toVector.flatMap(_.issues).filter(issue =>
        input.stepIdentity.contains(issue.step) && (input.kind != "RepairStep" || issue.status != "RESOLVED"))
      val report = Option.when(input.kind != "ImplementStep" && input.issues.nonEmpty)(GoalPhaseFixIssues.Report(
        issues.map(issue => GoalPhaseFixIssues.Disposition(issue.identity,
          if (input.kind == "RepairStep") "FIXED" else if (outcome == "accept") "RESOLVED" else "NOT_REVIEWED",
          "Specification-owned exact issue disposition", None))))
      val result = W.Result(outcome, "Specification-owned " + input.kind + " " + outcome,
        if (outcome == "repair") Vector("Correct the selected documentation cleanup defect") else Vector.empty,
        issueReport = report)
      val reference = ContextReference(name, "1")
      val completion = ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision,
        order.context.snapshot, TypedValue(W.resultCodec.typeIdentity, result), reference, Vector.empty,
        ExecutionEvidence(Vector(reference), Some(if (input.kind == "ReviewStep") "fixture-independent-reviewer" else "fixture-worker")))
      if (input.kind == "ReviewStep") native.prepare(call =>
        ThreeWorkflowExternalEvidencePorts.goalReviewBindingC(call.core, input, completion).flatMap(binding =>
          call.evidenceC(NativeExternalEvidence.Purpose.GoalReview, ThreeWorkflowExternalEvidencePorts.key(name, "1"),
            reference, "fixture-independent-reviewer", binding)))
      native.invoke[V.GoalPhaseWorkSubmissionResult]("submitGoalPhaseWorkResult",
        take(NativeWorkflowOperationInputFixture.submissionC(completion))).accepted shouldBe true
      reference
    }
    def authorizeConditional(target: R.Target, name: String): Unit = {
      val parent = target.completed.lastOption.map(s => O.Snapshot(s.commit.identity, s.commit.tree)).getOrElse(target.base)
      _authorize(GoalPhaseNativeClosure.Input(target.sessionId, target.sourceRevision, target.plan.identity,
        target.plan.revision, target.step, target.generation, parent, target.candidate, None, Some(target.admission)), name)
    }
    def authorizeReviewed(review: V.GoalPhaseAdvanceResult, reference: ContextReference, name: String): Unit = {
      val input = _issued_input(review)
      val parent = input.completed.lastOption.map(s => O.Snapshot(s.commit.identity, s.commit.tree))
        .getOrElse(input.originalBase)
      _authorize(GoalPhaseNativeClosure.Input(review.sessionId, current(review.sessionId).revision.value.toString,
        input.selectedPlan.identity, input.selectedPlan.revision, input.stepIdentity.get, input.generation,
        parent, input.candidate.get, Some(reference.identity)), name)
    }
    private def _authorize(input: GoalPhaseNativeClosure.Input, name: String): Unit =
      native.prepare(call => ThreeWorkflowExternalEvidencePorts.stepClosureBindingC(call.core, input).flatMap(binding =>
        call.evidenceC(NativeExternalEvidence.Purpose.StepClosure,
          ThreeWorkflowExternalEvidencePorts.key(input.instance, input.sourceRevision), ContextReference(name, "1"), "participant", binding)))
  }
}
