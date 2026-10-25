package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.processexecution.{LocalProcessExecutionDriver, ProcessExecutionCapture, ProcessExecutionResult, ProcessExecutionTermination,
  SbtExecutionResult, SbtOperation, SbtTarget}
import org.goldenport.cncf.workspace.WorkspaceId
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.preparation.NativeValidationRecoveryPreparation
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseNativeValidation as N
import GoalPhaseValidationRecovery as R
import GoalPhaseOrderedProgression as O
import SplitPhaseExplicitPartitionFixture.take

/** Isolated normal Operations own all native state. The acknowledgement hook
  * deliberately retains unknown state before any check effect. Test-owned
  * evidence is not an independent production Human or registered-runner proof.
  * @since   Oct. 20, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
private[admission] trait GoalPhaseValidationRecoveryOperationFixture extends ThreeWorkflowManagedRepositoryFixture {
  private var _admission = "allowed"
  private var _sequential = false
  private def _check_names: Vector[String] = if (_sequential) Vector("compile", "compile-second") else Vector("compile")
  protected def _recovery_plan = O.Plan("validation-recovery-plan", "1", "phase-2", "sm-workflow",
    Vector(O.Step("step-1", "Implement selected compile boundary", Vector("value.txt"), Vector.empty, _check_names)),
    _check_names, repairLimit = 2)

  protected def recoveryHost(root: Path): ThreeWorkflowRuntimeParameters.Definition = {
    val original = ThreeWorkflowNormalHostFixture.definition(root)
    val checks = _check_names.zipWithIndex.map { case (name, index) =>
      N.Check(name, index + 1, WorkflowSbtValidation.Kind.Compile)
    }
    original.copy(goal = original.goal.copy(candidatePaths = Vector("value.txt"),
      stepValidation = N.Policy("recovery-step-checks", 1, checks),
      phaseValidation = N.Policy("recovery-phase-checks", 1, checks)))
  }
  override protected def _prepare_sbt_build_(root: Path, scenario: String): Unit =
    if (!Files.exists(root.resolve("repository/src/main/scala/CompileConsumer.scala"))) {
      TypedSbtHostPlanFixture.build(root, true)
      TypedSbtHostPlanFixture.buildCompile(root, true)
    }
  override protected def _setup_commands_(root: Path, scenario: String): Vector[Vector[String]] = {
    val commands = super._setup_commands_(root, scenario)
    commands.patch(commands.indexWhere(_.contains("commit")), Vector(Vector("-C", root.resolve("repository").toString,
      "--literal-pathspecs", "add", "--", "src/main/scala/CompileConsumer.scala")), 0)
  }
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val git = TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString), goalPaths = goal.candidatePaths,
      goalMessages = goal.closure.programs.map(p => "Goal Step " + p.step), goalSelections = Vector(goal.candidatePaths))
    val actual = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable,
      goal.stepValidation.checks.map(_.ordinal), Some(git),
      operation = SbtOperation.Compile)
    if (_admission == "allowed") actual
    else {
      val target = "sm-workflow-repository-validation-1"
      val withoutProgram = actual.mapObject(_.add("sbtPrograms", Json.arr()).add("grants", Json.arr(Json.obj(
        "componentId" -> Json.fromString(SmWorkflowComponent.componentId.name),
        "capabilities" -> Json.fromValues(actual.hcursor.get[Vector[Json]]("gitPrograms").toOption.get.map(
          _.hcursor.downField("capability").focus.get))))))
      if (_admission == "missing-program") withoutProgram
      else {
        require(_admission == "missing-workspace", "Only explicitly selected admission fixtures are supported")
        withoutProgram.mapObject(_.add("workspaces", Json.fromValues(
          actual.hcursor.get[Vector[Json]]("workspaces").toOption.get.filterNot(_.hcursor.get[String]("identity").contains(target))))
          .add("workspaceGrants", Json.fromValues(actual.hcursor.get[Vector[Json]]("workspaceGrants").toOption.get
            .filterNot(_.hcursor.get[String]("workspaceId").contains(target)))))
      }
    }
  }
  protected def freshRecoveryRoot(name: String): Path = Files.createTempDirectory(Files.createDirectories(
    Path.of("target/sm-workflow/work/validation-recovery").toAbsolutePath), name + "-").toRealPath()
  protected def compileExecutions(root: Path): Vector[Long] = {
    val marker = root.resolve("compile-generations.txt")
    if (Files.exists(marker)) Files.readString(marker).linesIterator.map(_.toLong).toVector else Vector.empty
  }
  protected def withRecoveryEpisode(root: Path, initialize: Boolean = false, interrupt: Boolean = false,
    admission: String = "allowed", sequential: Boolean = false,
    acknowledgement: Option[String => Consequence[Unit]] = None,
    importData: Option[String] = None, componentInstance: String = "repository-spec")(task: RecoveryEpisode => Unit): Unit = {
    val previous = _admission
    val previoussequence = _sequential
    _admission = admission
    _sequential = sequential
    val defaultack: String => Consequence[Unit] = point =>
      if (interrupt && point == "goal-check-prepared:compile")
        Consequence.stateConflict("Test-owned acknowledgement failure immediately after actual check intent commit, before effect")
      else Consequence.unit
    try _with_runtime_(root, "Goal", Some(recoveryHost(root)),
      hostParameters = Map("textus.resource.url.file.roots" -> root.toAbsolutePath.normalize.toString) ++
        importData.map(value => "textus.import.data.file" -> value),
      goalAcknowledgement = Some(acknowledgement.getOrElse(defaultack)), componentInstance = componentInstance) {
      (owner, component, actor, prepare) =>
        if (initialize) prepare(call => call.setupC(_setup_commands_(root, "Goal").size).flatMap { _ =>
          val selection = _goal_selection_(recoveryHost(root).goal.input, "validation-recovery-selection", "validation-recovery-invocation")
          call.createC("goal_selection", NativeWorkflowStartBusinessKey(NativeWorkflowStartInput.Goal(recoveryHost(root).goal.input),
            selection.participantIdentity), Record.data(ProfileSelectionJsonV1.storageField ->
              take(ProfileSelectionJsonV1.encodeStorageC(selection)))).flatMap(_ =>
            call.createC("goal_plans", selection.selectionId, Record.data(GoalPhaseSelectedPlanJsonV1.storageField ->
              take(GoalPhaseSelectedPlanJsonV1.encodeStorageC(GoalPhaseNativeRun.SelectedPlan(selection.selectionId,
                selection.participantIdentity, _recovery_plan))))))
        })
        task(new RecoveryEpisode(root, owner, component, actor, prepare))
    } finally {
      _admission = previous
      _sequential = previoussequence
    }
  }

  protected final class RecoveryEpisode(val root: Path, val owner: Subsystem, val component: Component,
    val actor: ExecutionContext, val prepare: (Setup => Consequence[Unit]) => Unit) {
    def call(operation: String, properties: List[Property]) = owner.executeOperationResponseInContext(Request.of(
      component = component.name, service = "GoalPhaseService", operation = operation, properties = properties), actor)
    def invoke[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A = read_value[A](take(call(operation, properties)))
    def start(): V.GoalPhaseStartResult = invoke[V.GoalPhaseStartResult]("startGoalPhase",
      NativeWorkflowOperationInputFixture.goal(recoveryHost(root).goal.input))
    def advance(identity: String): V.GoalPhaseAdvanceResult = invoke[V.GoalPhaseAdvanceResult]("advanceGoalPhase",
      NativeWorkflowOperationInputFixture.advance(identity))
    def submitImplementation(request: V.GoalPhaseAdvanceResult): V.GoalPhaseWorkSubmissionResult = {
      Files.writeString(root.resolve("repository/value.txt"), "selected semantic change\n")
      val result = V.GoalPhaseWorkResult("completed", "Test-owned selected semantic implementation", Vector.empty,
        None, None, None, None, None, None, None, None, dependencyValidation = None, diagnosticRepair = None)
      invoke[V.GoalPhaseWorkSubmissionResult](request.submitOperation.get, List(Property("sessionId", request.submitSessionId.get, None),
        Property("result", result.toRecord(), None), Property("resultReference", V.WorkflowArtifactReference("recovery-implementation", "1").toRecord(), None)))
    }
    def describe(identity: String): V.GoalPhaseValidationRecoveryDescriptionResult =
      invoke[V.GoalPhaseValidationRecoveryDescriptionResult](R.describeOperation, List(Property("sessionId", identity, None)))
    def recover(input: V.GoalPhaseValidationRecoveryInput): V.GoalPhaseValidationRecoveryResult =
      invoke[V.GoalPhaseValidationRecoveryResult](R.recoveryOperation, List(Property("input", input.toRecord(), None)))
    def current(identity: String): InstanceRecord = {
      var observed = Option.empty[InstanceRecord]
      prepare(call => call.goalCurrentC(InstanceIdentity(identity)).map(value => { observed = Some(value); () }))
      observed.get
    }
    def retained(identity: String, sourceRevision: String, key: String): Option[N.Saved] = {
      var observed = Option.empty[N.Saved]
      prepare(call => for {
        catalog <- GoalPhaseRuntimeCatalog.loadC()
        storage <- WorkflowComponentStorage.bindC(ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin),
          SmWorkflowComponent.componentId, _configuration_, Vector(catalog.definition), _collection_("instances"), _collection_("continuations"))
        reader <- storage.bindMechanicalC(GoalPhaseNativeRun.advanceOperation, N.codec)
        saved <- reader.loadRetainedC(WorkflowMechanicalTransition.Reference(identity + ":ordered-validation:" + sourceRevision + ":" + key, "1"))(
          using call.executionContext)
        _ = observed = saved.map(value => N.Saved(value.receipt.next, value.receipt.payload.value))
      } yield ())
      observed
    }
    def proof(description: V.GoalPhaseValidationRecoveryDescriptionResult, choice: String,
      rationale: String, reference: V.WorkflowArtifactReference, participant: String = "participant"):
      V.GoalPhaseValidationRecoveryInput = {
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some(participant), None, None)
      val prepared = take(NativeValidationRecoveryPreparation.prepareC(description,
        V.GoalPhaseValidationRecoveryAnswer(choice, rationale), reference, execution, participant, "normal-development-host",
        actor.clock.instant().minusSeconds(1)))
      prepare(call => call.createC("host_evidence", prepared.recordIdentity, prepared.record))
      V.GoalPhaseValidationRecoveryInput(description.sessionId, description.target, choice, rationale, reference)
    }
    /** Fixture-owned independent review and authority use the normal evidence ports. */
    def acceptStep(review: V.GoalPhaseAdvanceResult): Unit = {
      var assigned = Option.empty[WorkflowInteraction[GoalPhaseNativeWork.Request, Nothing]]
      prepare(call => call.goalWorkC(InstanceIdentity(review.sessionId)).map(value => { assigned = Some(value); () }))
      val work = assigned.get
      val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[GoalPhaseNativeWork.Request]].request
      val selected = order.input.get.value
      val reference = ContextReference("known-failed-fixture-step-review", "1")
      val completion = ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision,
        order.context.snapshot, TypedValue(GoalPhaseNativeWork.resultCodec.typeIdentity,
          GoalPhaseNativeWork.Result("accept", "Independent fixture reviewer accepts the exact Step", Vector.empty)),
        reference, Vector.empty, ExecutionEvidence(Vector(reference), Some("fixture-independent-reviewer")))
      prepare(call => ThreeWorkflowExternalEvidencePorts.goalReviewBindingC(call.core, selected, completion)
        .flatMap(binding => call.evidenceC(NativeExternalEvidence.Purpose.GoalReview,
          ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision), reference,
          "fixture-independent-reviewer", binding)))
      invoke[V.GoalPhaseWorkSubmissionResult]("submitGoalPhaseWorkResult",
        take(NativeWorkflowOperationInputFixture.submissionC(completion))).accepted shouldBe true
      val pending = current(review.sessionId)
      val closure = GoalPhaseNativeClosure.Input(review.sessionId, pending.revision.value.toString,
        selected.selectedPlan.identity, selected.selectedPlan.revision, selected.stepIdentity.get,
        selected.generation, selected.originalBase, selected.candidate.get, reference.identity)
      prepare(call => call.evidenceC(NativeExternalEvidence.Purpose.StepClosure,
        ThreeWorkflowExternalEvidencePorts.key(closure.instance, closure.sourceRevision), ContextReference("fixture-step-authority", "1"),
        "participant", take(ThreeWorkflowExternalEvidencePorts.stepClosureBindingC(call.core, closure))))
    }
  }
}

/** Normal managed Operations prove storage/authority separation. Real SBT is
  * required by the parent's selected runtime preparation; this worker runs none.
  * Introduced:   Oct. 20, 2026
  *  Updated: Oct. 22, 2026
  * Updated: Oct. 25, 2026
  * Author:  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class GoalPhaseValidationRecoveryOperationSpec extends AnyWordSpec with GivenWhenThen
    with GoalPhaseValidationRecoveryOperationFixture {
  "Public validation recovery Operations" should {
    "E12 preserve a formal Phase failure and admit only one independently bound fresh trial" should afterWord(
      "in spec:goal-phase-validation-recovery, example:E12, rules:R8,R9, phase:6.4") {
      "reconstruct the same history and reject stale crossed missing and multi-check direction" in {
        object Fixture extends GoalPhaseKnownFailedPhaseValidationFixture {
          // Select the known-failed Decision contract at this NEW Session's
          // Start; a positive allowance instead selects automatic Release FIX.
          override protected def known_repair_limit: Int = 0
        }
        val root = Fixture.newKnownRoot("known-failed-operation")
        var identity = ""
        var failed = Option.empty[N.Saved]
        var before = Option.empty[InstanceRecord]
        var target = Option.empty[R.RecoveryTarget]
        var directed = Option.empty[InstanceRecord]
        Given("docs/spec/goal-phase-validation-recovery.md R8,R9 E12: a fresh fixture Step with real Compile and local commit, then real failing Phase Test")
        Fixture.inKnownEpisode(root, initialize = true) { episode =>
          identity = episode.start().sessionId
          episode.submitImplementation(episode.advance(identity)).accepted shouldBe true
          val review = episode.advance(identity)
          review.work.get.kind shouldBe "ReviewStep"
          review.work.get.review.get.checks.head.sbt.get.kind shouldBe "compile"
          episode.acceptStep(review)
          episode.advance(identity).kind shouldBe "Wait"
          Fixture.phaseExecutions(root).size shouldBe 1
          before = Some(episode.current(identity))
          before.get.currentProgression shouldBe Some(ProgressionReference("phase.Decision"))
          val description = episode.describe(identity)
          val actual = take(R.targetC(description.target))
          target = Some(actual)
          failed = episode.retained(identity, actual.sourceRevision, "validation")
          failed.get.payload.receipts.head.exitCode shouldBe Some(1)
          failed.get.payload.receipts.head.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Failed
          actual.knownFailedPhase.get.participant shouldBe "participant"
          description.message should include("KNOWN FAILED")
          episode.current(identity).history shouldBe before.get.history
          When("ordinary Advance repeats without direction and public recovery receives missing or crossed proof")
          val nodirection = episode.advance(identity)
          val missing = V.GoalPhaseValidationRecoveryInput(identity, description.target, "AuthorizeNewAttempt", "Fresh trial",
            V.WorkflowArtifactReference("missing-known-proof", "1"))
          val missingresult = episode.call(R.recoveryOperation, List(Property("input", missing.toRecord(), None)))
          val genuine = episode.proof(description, "AuthorizeNewAttempt", "Fresh trial after known failure",
            V.WorkflowArtifactReference("known-direction", "1"))
          val crossed = Vector(actual.copy(candidate = actual.candidate.copy(tree = "c" * 40)),
            actual.copy(knownFailedPhase = None), actual.copy(step = "step-1"),
            actual.copy(knownFailedPhase = actual.knownFailedPhase.map(_.copy(exitCode = 2))),
            actual.copy(knownFailedPhase = actual.knownFailedPhase.map(_.copy(participant = "other"))))
          val crossedresults = crossed.map(t => episode.call(R.recoveryOperation,
            List(Property("input", genuine.copy(target = R.targetRecord(t)).toRecord(), None))))
          val defer = episode.proof(description, "Defer", "Keep formal failure", V.WorkflowArtifactReference("known-defer", "1"))
          val deferred = episode.recover(defer)
          val deferredhistory = episode.current(identity).history
          val deferredexecutions = Fixture.phaseExecutions(root).size
          Then("no direction, missing or crossed proof and Defer preserve the known failure without another Test")
          nodirection.kind shouldBe "Wait"
          missingresult.isSuccess shouldBe false
          crossedresults.foreach(_.isSuccess shouldBe false)
          deferred.outcome shouldBe "deferred"
          deferredhistory shouldBe before.get.history
          deferredexecutions shouldBe 1
          When("the actual independent Human evidence admits the exact extended target")
          val authorized = episode.recover(genuine)
          directed = Some(episode.current(identity))
          val repeated = episode.call(R.recoveryOperation, List(Property("input", genuine.toRecord(), None)))
          val authorizedexecutions = Fixture.phaseExecutions(root).size
          val preserved = episode.retained(identity, actual.sourceRevision, "validation").get.payload
          Then("one durable direction selects PhaseValidation without Test, refund or historical mutation")
          authorized.outcome shouldBe "new-attempt-authorized"
          directed.get.history.dropRight(1) shouldBe before.get.history
          directed.get.currentProgression shouldBe Some(ProgressionReference("phase.PhaseValidation"))
          repeated.isSuccess shouldBe false
          authorizedexecutions shouldBe 1
          preserved shouldBe failed.get.payload
        }
        Given("the same fixture reopened after changing only the external readiness marker")
        Files.writeString(root.resolve("phase-ready"), "ready\n")
        Fixture.inKnownEpisode(root) { episode =>
          When("ordinary Advance reconstructs the saved direction and executes the fresh real Phase Test")
          val review = episode.advance(identity)
          Then("ordinary PhaseReview retains the completed Step, Plan, base, failed receipt and counters")
          review.work.get.kind shouldBe "ReviewPhase"
          review.work.get.completed.size shouldBe 1
          review.work.get.selectedPlan.identity shouldBe target.get.planIdentity
          review.work.get.originalBase.head shouldBe target.get.base.head
          review.work.get.candidate.get.tree shouldBe target.get.candidate.tree
          review.work.get.review.get.checks.head.exitCode shouldBe Some(0)
          review.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(0, 0, 0, Some("sm-workflow.repair-trend.v1"), Some(0), Some(0)))
          episode.current(identity).history.take(directed.get.history.size) shouldBe directed.get.history
          episode.retained(identity, target.get.sourceRevision, "validation").get.payload shouldBe failed.get.payload
          episode.retained(identity, target.get.sourceRevision, "check-recovery-0") shouldBe None
          Fixture.phaseExecutions(root).size shouldBe 2
          When("ordinary Advance repeats after the fresh whole-Phase check succeeds")
          val repeated = episode.advance(identity)
          val repeatedexecutions = Fixture.phaseExecutions(root).size
          Then("the existing PhaseReview returns without another Test")
          repeated shouldBe review
          repeatedexecutions shouldBe 2
        }
        Given("a separate fresh fixture with two selected whole-Phase checks")
        object Multi extends GoalPhaseKnownFailedPhaseValidationFixture {
          override protected def known_repair_limit: Int = 0
          override protected def known_phase_checks = Vector("phase-test", "phase-sibling")
        }
        val multiroot = Multi.newKnownRoot("known-multi-refused")
        Multi.inKnownEpisode(multiroot, initialize = true) { episode =>
          val id = episode.start().sessionId
          episode.submitImplementation(episode.advance(id)).accepted shouldBe true
          When("the accepted Step closes before whole-Phase validation")
          val review = episode.advance(id)
          episode.acceptStep(review)
          val failed = episode.advance(id)
          val described = episode.call(R.describeOperation, List(Property("sessionId", id, None)))
          val executions = Multi.phaseExecutions(multiroot).size
          Then("the known first failure waits and the multi-check target refuses recovery description")
          failed.kind shouldBe "Wait"
          described.isSuccess shouldBe false
          executions shouldBe 1
        }
      }
    }
    "E14 preserve three unknown Phase attempts before a real failed fourth attempt and one new trial" should afterWord(
      "in spec:goal-phase-validation-recovery, example:E14, rules:R3,R4,R8,R9, phase:6.4") {
      "bind the latest real failure and require its own independent direction after reopening" in {
        object Fixture extends GoalPhaseKnownFailedPhaseValidationFixture {
          override protected def known_repair_limit: Int = 0
        }
        Given("docs/spec/goal-phase-validation-recovery.md R3,R4,R8,R9 E14: a NEW isolated normal Operation fixture")
        val root = Fixture.newKnownRoot("known-failed-three-unknowns-operation")
        var identity = ""
        var implementation = Option.empty[V.GoalPhaseAdvanceResult]
        var oldproof = Option.empty[V.GoalPhaseValidationRecoveryInput]
        var source = ""
        var before = Option.empty[InstanceRecord]
        var failed = Option.empty[N.Saved]
        var target = Option.empty[R.RecoveryTarget]
        var preserved = Vector.empty[(String, N.Saved)]
        When("normal Operations start and submit the selected Step implementation")
        Fixture.inKnownEpisode(root, initialize = true) { episode =>
          identity = episode.start().sessionId
          implementation = Some(episode.advance(identity))
          val submission = episode.submitImplementation(implementation.get)
          Then("the selected implementation is accepted")
          submission.accepted shouldBe true
          When("ordinary Compile and independent fixture review prepare actual Step closure")
          val review = episode.advance(identity)
          episode.acceptStep(review)
          Then("the real Compile succeeds and independent review is prepared for the selected Step")
          review.work.get.kind shouldBe "ReviewStep"
          review.work.get.review.get.checks.head.sbt.get.kind shouldBe "compile"
          review.work.get.review.get.checks.head.exitCode shouldBe Some(0)
        }
        (0 until 3).foreach { ordinal =>
          When("test-owned normal Operation acknowledgement interrupts Phase intent " + ordinal + " before its SBT effect")
          Fixture.inKnownEpisode(root, acknowledgement = Some(Fixture.unknownPhaseAcknowledgement)) { episode =>
            val interrupted = episode.call("advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(identity))
            val description = episode.describe(identity)
            val actual = take(R.targetC(description.target))
            val unknown = episode.retained(identity, actual.sourceRevision,
              "check-intent-0" + (if (ordinal == 0) "" else "-retry-" + ordinal)).get
            Then("the real selected intent has no result and the preceding old outcomes remain unknown")
            interrupted.isSuccess shouldBe false
            actual.attempt shouldBe ordinal
            actual.knownFailedPhase shouldBe None
            actual.intentRevision shouldBe unknown.record.revision.value.toString
            if (ordinal == 0) source = actual.sourceRevision else actual.sourceRevision shouldBe source
            Fixture.phaseExecutions(root) shouldBe empty
            When("separate fixture-owned exact R6 evidence authorizes only the next attempt")
            val proof = episode.proof(description, "AuthorizeNewAttempt", "Old outcome remains unknown; authorize one next attempt",
              V.WorkflowArtifactReference("combined-unknown-human-" + ordinal, "1"))
            val accepted = episode.recover(proof)
            oldproof = Some(proof)
            Then("authorization adds one R6 record without executing a check")
            accepted.outcome shouldBe "new-attempt-authorized"
            Fixture.phaseExecutions(root) shouldBe empty
          }
        }
        val closedtip = Files.readString(root.resolve("repository/.git/refs/heads/main"))
        val reflog = Files.readString(root.resolve("repository/.git/logs/HEAD"))
        val product = Files.readAllBytes(root.resolve("repository/src/test/scala/ProductCheck.scala")).toVector
        When("the next ordinary Operation actually executes the fourth selected Phase attempt and retains its failed result")
        Fixture.inKnownEpisode(root) { episode =>
          val waiting = episode.advance(identity)
          Fixture.phaseExecutions(root).size shouldBe 1
          before = Some(episode.current(identity))
          val description = episode.describe(identity)
          val actual = take(R.targetC(description.target))
          target = Some(actual)
          failed = episode.retained(identity, source, "validation")
          val finalintent = episode.retained(identity, source, "check-intent-0-retry-3").get
          preserved = ((0 until 3).flatMap(ordinal => {
            val suffix = if (ordinal == 0) "" else "-retry-" + ordinal
            Vector("check-intent-0" + suffix, "check-recovery-0" + suffix)
          }).toVector ++ Vector("check-intent-0-retry-3", "check-result-0-retry-3", "validation")).map(key =>
            key -> episode.retained(identity, source, key).get)
          Then("description binds attempt3 and its own intent, Decision and complete failed receipt")
          waiting.kind shouldBe "Wait"
          actual.attempt shouldBe 3
          actual.intentRevision shouldBe finalintent.record.revision.value.toString
          actual.sourceRevision shouldBe source
          actual.frontierRevision shouldBe before.get.revision.value.toString
          actual.knownFailedPhase.get.failedReceiptRevision shouldBe actual.frontierRevision
          failed.get.payload.newAttempts.map(_.attempt) shouldBe Vector(0, 1, 2)
          failed.get.payload.receipts.head.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Failed
          failed.get.payload.receipts.head.exitCode shouldBe Some(1)
          Fixture.phaseExecutions(root).size shouldBe 1
          When("the failed target receives missing, old-R6, wrong-ordinal, crossed candidate/Plan or crossed Human proof")
          val missing = V.GoalPhaseValidationRecoveryInput(identity, description.target, "AuthorizeNewAttempt", "Fresh trial",
            V.WorkflowArtifactReference("missing-combined-known", "1"))
          val genuine = episode.proof(description, "AuthorizeNewAttempt", "Independent NEW direction after actual failed attempt3",
            V.WorkflowArtifactReference("combined-known-human", "1"))
          val otherdescription = description.copy(target = R.targetRecord(actual.copy(
            knownFailedPhase = actual.knownFailedPhase.map(_.copy(participant = "other")))))
          val otherhuman = episode.proof(otherdescription, "AuthorizeNewAttempt", "Crossed participant",
            V.WorkflowArtifactReference("combined-other-human", "1"), participant = "other")
          val crossed = Vector(actual.copy(attempt = 0), actual.copy(intentRevision = take(R.targetC(oldproof.get.target)).intentRevision),
            actual.copy(planRevision = "2"), actual.copy(candidate = actual.candidate.copy(tree = "c" * 40)))
          val refused = (Vector(missing, oldproof.get, oldproof.get.copy(target = description.target), otherhuman) ++
            crossed.map(value => genuine.copy(target = R.targetRecord(value)))).map(input => episode.call(R.recoveryOperation,
              List(Property("input", input.toRecord(), None))))
          val nodirection = episode.advance(identity)
          val deferred = episode.recover(episode.proof(description, "Defer", "Retain actual failure",
            V.WorkflowArtifactReference("combined-known-defer", "1")))
          val unchanged = episode.current(identity)
          Then("no old direction or Defer can authorize another check or change the failed history")
          refused.foreach(_.isSuccess shouldBe false)
          nodirection.kind shouldBe "Wait"
          deferred.outcome shouldBe "deferred"
          unchanged.history shouldBe before.get.history
          Fixture.phaseExecutions(root).size shouldBe 1
          When("the exact NEW independent known-failed direction is admitted once")
          val accepted = episode.recover(genuine)
          val repeated = episode.call(R.recoveryOperation, List(Property("input", genuine.toRecord(), None)))
          Then("one separate durable direction grants a fresh validation round without executing it")
          accepted.outcome shouldBe "new-attempt-authorized"
          repeated.isSuccess shouldBe false
          Fixture.phaseExecutions(root).size shouldBe 1
        }
        When("only external readiness changes and the same Session reopens for ordinary Advance")
        Files.writeString(root.resolve("phase-ready"), "ready\n")
        Fixture.inKnownEpisode(root) { episode =>
          val review = episode.advance(identity)
          val repeated = episode.advance(identity)
          val current = episode.current(identity)
          val retained = preserved.map { (key, _) => key -> episode.retained(identity, source, key).get }
          Then("one real fresh Test reaches PhaseReview with all bindings, counters, commits and old records retained")
          review.sessionId shouldBe identity
          review.work.get.kind shouldBe "ReviewPhase"
          review.work.get.selectedPlan shouldBe implementation.get.work.get.selectedPlan
          review.work.get.originalBase shouldBe implementation.get.work.get.originalBase
          review.work.get.candidate.get.head shouldBe target.get.candidate.head
          review.work.get.candidate.get.tree shouldBe target.get.candidate.tree
          review.work.get.completed.size shouldBe 1
          review.work.get.completed.head.commit.identity shouldBe closedtip.trim
          review.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(0, 0, 0, Some("sm-workflow.repair-trend.v1"), Some(0), Some(0)))
          review.work.get.review.get.checks.head.exitCode shouldBe Some(0)
          repeated shouldBe review
          Fixture.phaseExecutions(root).size shouldBe 2
          current.history.take(before.get.history.size) shouldBe before.get.history
          current.history.count(_.causation.value.startsWith("ordered-known-failed-phase-validation-recovery:")) shouldBe 1
          retained.map((key, saved) => (key, saved.payload, saved.record.history)) shouldBe
            preserved.map((key, saved) => (key, saved.payload, saved.record.history))
          (0 until 3).foreach { ordinal =>
            val suffix = if (ordinal == 0) "" else "-retry-" + ordinal
            episode.retained(identity, source, "check-result-0" + suffix) shouldBe None
          }
          Files.readString(root.resolve("repository/.git/refs/heads/main")) shouldBe closedtip
          Files.readString(root.resolve("repository/.git/logs/HEAD")) shouldBe reflog
          Files.readAllBytes(root.resolve("repository/src/test/scala/ProductCheck.scala")).toVector shouldBe product
        }
      }
    }
    "settle an actual cancelled Compile at Decision after runtime process admission closes" in {
      Given("a fresh normal two-check recovery episode and a Compile source generator that waits after emitting captures")
      val root = freshRecoveryRoot("closed-admission-cancelled")
      TypedSbtHostPlanFixture.build(root, true)
      TypedSbtHostPlanFixture.buildCompile(root, true)
      val build = root.resolve("repository/build.sbt")
      Files.writeString(build, Files.readString(build).replace("  Seq(generated)", """  println("CANCELLED_COMPILE_STDOUT")
        |  System.err.println("CANCELLED_COMPILE_STDERR")
        |  IO.write(root.getParentFile / "cancel-ready.txt", "ready")
        |  Thread.sleep(60000L)
        |  Seq(generated)""".stripMargin))
      withRecoveryEpisode(root, initialize = true, sequential = true) { episode =>
        val identity = episode.start().sessionId
        val work = episode.advance(identity)
        episode.submitImplementation(work).accepted shouldBe true
        val before = episode.current(identity)
        val driver = episode.actor.scope.processExecutionDriverOption.get match {
          case local: LocalProcessExecutionDriver => local
          case other => fail("Normal fixture requires its actual local process driver: " + other.safeIdentity)
        }
        val stopped = new java.util.concurrent.atomic.AtomicReference[Option[Consequence[Unit]]](None)
        val finished = new java.util.concurrent.atomic.AtomicBoolean(false)
        val cancellation = new Thread(() => {
          val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(120L)
          while (!finished.get && !Files.exists(root.resolve("cancel-ready.txt")) && System.nanoTime() < deadline)
            Thread.sleep(25L)
          if (!finished.get && Files.exists(root.resolve("cancel-ready.txt"))) stopped.set(Some(driver.requestStopC()))
        }, "test-owned-compile-cancellation")
        cancellation.setDaemon(true)
        cancellation.start()
        When("ordinary Advance awaits the real Compile while the test requests runtime stop and closes all further process admission")
        val waiting = try episode.advance(identity) finally {
          finished.set(true)
          cancellation.join(2000L)
        }
        Then("the actual incomplete receipt and failed outcome commit to Decision and return Wait without another process or repair")
        stopped.get.map(_.isSuccess) shouldBe Some(true)
        cancellation.isAlive shouldBe false
        driver.terminationConfirmed shouldBe true
        waiting.kind shouldBe "Wait"
        waiting.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(0, 0, 2, Some("sm-workflow.repair-trend.v1"), Some(2), Some(2)))
        val current = episode.current(identity)
        current.currentProgression shouldBe Some(ProgressionReference("phase.Decision"))
        current.history.take(before.history.size) shouldBe before.history
        workBase(current) shouldBe workBase(before)
        val source = current.history.find(_.currentProgression.contains(ProgressionReference("phase.Validation"))).get.revision.value.toString
        val result = episode.retained(identity, source, "check-result-0").get
        val validation = episode.retained(identity, source, "validation").get
        val receipt = result.payload.receipts.head
        receipt.identity shouldBe "compile"
        receipt.termination shouldBe "cancelled"
        receipt.exitCode shouldBe None
        receipt.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Incomplete
        receipt.stdout should include("CANCELLED_COMPILE_STDOUT")
        receipt.stderr should include("CANCELLED_COMPILE_STDERR")
        validation.payload shouldBe result.payload.copy(outcome = Some("failed"))
        validation.payload.selected.map(_.identity) shouldBe Vector("compile", "compile-second")
        validation.payload.receipts.map(_.identity) shouldBe Vector("compile")
        N.repairable(validation.payload) shouldBe false
        episode.retained(identity, source, "check-intent-1") shouldBe None
        compileExecutions(root).size shouldBe 1
      }
    }

    "retain cancelled timed-out and unconfirmed Compile captures at Decision without repair or another selected check" in {
      Given("a selected two-check Plan with an actual-shaped incomplete first Compile result and unchanged zero repair counts")
      val base = O.Snapshot("a" * 40, "b" * 40)
      val candidate = O.Snapshot(base.head, "c" * 40)
      val plan = O.Plan("cancelled-plan", "1", "phase-2", "sm-workflow",
        Vector(O.Step("step-1", "Selected work", Vector("value.txt"), Vector.empty, Vector("compile", "after"))),
        Vector("compile", "after"), repairLimit = 2)
      val initial = take(O.startC(plan, base))
      val implemented = take(initial.acceptC(O.SemanticCompleted(initial.next.asInstanceOf[O.Semantic].key, "Actual-shaped implementation")))
      val validating = take(implemented.acceptC(O.CandidateObserved(candidate)))
      val bytes = "COMPILE_BEFORE_CANCEL 日本語\n".getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector
      val capture = ProcessExecutionCapture(bytes, bytes.size.toLong, false)
      val terminations = Vector(ProcessExecutionTermination.Cancelled -> "cancelled", ProcessExecutionTermination.TimedOut -> "timed-out",
        ProcessExecutionTermination.TerminationUnconfirmed -> "termination-unconfirmed")
      When("the normal typed receipt boundary and selected controller consume each actual incomplete prefix")
      val observed = terminations.map { (termination, expected) =>
        val process = ProcessExecutionResult(termination, capture, capture, Vector.empty, 7L, "selected-sbt")
        val result = SbtExecutionResult(SbtTarget(take(WorkspaceId.parseC("sm-workflow-repository-validation-1"))),
          SbtOperation.Compile, process, None)
        val receipt = take(RepositorySyncNativeMergeValidation.sbtReceiptC("compile", WorkflowSbtValidation.Kind.Compile, result))
        val payload = N.Payload("12", "step-1", candidate, Vector(N.Check("compile", 1, WorkflowSbtValidation.Kind.Compile)),
          Vector(receipt), Some("failed"))
        (receipt, payload, take(N.observedStateC(validating, payload, "actual-incomplete-receipt")), expected)
      }
      Then("Decision retains logs, Plan/base/candidate and zero counts without Repair, review or an automatic next check")
      observed.foreach { (receipt, payload, state, expected) =>
        receipt.termination shouldBe expected
        receipt.exitCode shouldBe None
        receipt.stdout shouldBe "COMPILE_BEFORE_CANCEL 日本語\n"
        receipt.stderr shouldBe receipt.stdout
        receipt.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Incomplete
        N.codec.decode(N.codec.encode(payload)) shouldBe Right(payload)
        N.repairable(payload) shouldBe false
        state.stage shouldBe O.Stage.Decision
        state.plan shouldBe plan
        state.base shouldBe base
        state.candidate shouldBe Some(candidate)
        state.repairs shouldBe 0
        state.ordinaryRepairs shouldBe 0
        state.pendingRepair shouldBe empty
        state.reviewEvidence shouldBe empty
        N.observedStateC(state, payload, "forbidden-retry").isSuccess shouldBe false
        N.observedStateC(validating, payload.copy(outcome = Some("repair-required")), "forbidden-repair").isSuccess shouldBe false
        payload.receipts.map(_.identity) shouldBe Vector("compile")
      }
    }

    for (missing <- Vector("missing-workspace", "missing-program")) {
      s"save actual $missing admission separately and use a new attempt after host correction" in {
        Given("docs/spec/goal-phase-validation-recovery.md R1,R2: normal Operations with a real selected Compile and " + missing)
        val root = freshRecoveryRoot(missing)
        var identity = ""
        var failed = Option.empty[N.Saved]
        withRecoveryEpisode(root, initialize = true, admission = missing) { episode =>
          val started = episode.start()
          identity = started.sessionId
          val implementation = episode.advance(identity)
          episode.submitImplementation(implementation).accepted shouldBe true
          When("ordinary Advance reaches the owning UoW's actual admission check")
          val waiting = episode.advance(identity)
          val current = episode.current(identity)
          val source = current.history.find(_.currentProgression.contains(ProgressionReference("phase.Validation"))).get.revision.value.toString
          failed = episode.retained(identity, source, "check-result-0")
          Then("one actual admission diagnostic is retained without a process receipt or repair update")
          waiting.kind shouldBe "Wait"
          waiting.message should include("admission")
          waiting.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(0, 0, 2, Some("sm-workflow.repair-trend.v1"), Some(2), Some(2)))
          failed.get.payload.admissionFailures.map(_.checkIdentity) shouldBe Vector("compile")
          failed.get.payload.admissionFailures.head.diagnostic.trim should not be empty
          failed.get.payload.receipts shouldBe empty
          failed.get.payload.stopped shouldBe empty
          failed.get.payload.uncertainties shouldBe empty
          failed.get.payload.outcome shouldBe None
          compileExecutions(root) shouldBe empty
          episode.call(R.describeOperation, List(Property("sessionId", identity, None))).isSuccess shouldBe false
        }
        Given("the same native Session and selected Plan reopened after real host preparation correction")
        withRecoveryEpisode(root) { episode =>
          When("the next ordinary Advance runs the newly admitted attempt")
          val reviewed = episode.advance(identity)
          val actual = episode.retained(identity, failed.get.payload.sourceRevision, "check-result-0-retry-1").get
          Then("normal Compile succeeds and independent review is pending without Start, Submit or repair renewal")
          reviewed.kind shouldBe "Request"
          reviewed.work.get.kind shouldBe "ReviewStep"
          reviewed.work.get.selectedPlan.identity shouldBe _recovery_plan.identity
          reviewed.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(0, 0, 2, Some("sm-workflow.repair-trend.v1"), Some(2), Some(2)))
          actual.payload.admissionFailures shouldBe failed.get.payload.admissionFailures
          actual.payload.receipts.map(_.termination) shouldBe Vector("exited")
          actual.payload.receipts.head.exitCode shouldBe Some(0)
          actual.payload.receipts.head.sbt.get.kind shouldBe WorkflowSbtValidation.Kind.Compile
          val original = episode.retained(identity, failed.get.payload.sourceRevision, "check-result-0").get
          original.payload shouldBe failed.get.payload
          original.record.history shouldBe failed.get.record.history
          compileExecutions(root).size shouldBe 1
        }
      }
    }

    "require exact independent Human evidence and preserve unknown history through authorization" in {
      Given("R3,R4,R5: an isolated Session with test-owned acknowledgement failure after actual intent commit and before effect")
      val root = freshRecoveryRoot("unknown-intent")
      var identity = ""
      var before = Option.empty[InstanceRecord]
      var description = Option.empty[V.GoalPhaseValidationRecoveryDescriptionResult]
      withRecoveryEpisode(root, initialize = true, interrupt = true) { episode =>
        identity = episode.start().sessionId
        val work = episode.advance(identity)
        episode.submitImplementation(work).accepted shouldBe true
        When("ordinary Advance commits its real intent and the explicit fixture acknowledgement interrupts")
        val interrupted = episode.call("advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(identity))
        Then("the unresolved actual intent remains without a receipt or any Compile effect")
        interrupted.isSuccess shouldBe false
        before = Some(episode.current(identity))
        before.get.causation.value shouldBe "ordered-validation:check-intent-0"
        compileExecutions(root) shouldBe empty
      }

      Given("that same Session reopened without replay, with no Human evidence yet")
      withRecoveryEpisode(root) { episode =>
        When("ordinary Advance waits and the read-only public recovery Operation describes its exact target")
        val waiting = episode.advance(identity)
        description = Some(episode.describe(identity))
        val actual = description.get
        val target = take(R.targetC(actual.target))
        Then("description uses the original Validation source and current actual intent frontier without a write")
        waiting.kind shouldBe "Wait"
        waiting.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(0, 0, 2, Some("sm-workflow.repair-trend.v1"), Some(2), Some(2)))
        actual.outcome shouldBe "decision-required"
        actual.choices shouldBe Vector("AuthorizeNewAttempt", "Defer")
        actual.recoveryOperation shouldBe R.recoveryOperation
        actual.message should include("old selected check outcome remains unknown")
        actual.message should include("repeat effects")
        target.sessionId shouldBe identity
        target.planIdentity shouldBe _recovery_plan.identity
        target.planRevision shouldBe _recovery_plan.revision
        target.sourceRevision.toLong should be < target.intentRevision.toLong
        target.frontierRevision shouldBe before.get.revision.value.toString
        target.intentRevision shouldBe target.frontierRevision
        target.step shouldBe "step-1"
        target.checkIdentity shouldBe "compile"
        target.checkOrdinal shouldBe 1
        target.attempt shouldBe 0
        target.base shouldBe O.Snapshot(workBase(before.get)._1, workBase(before.get)._2)
        val originalIntent = episode.retained(identity, target.sourceRevision, "check-intent-0").get
        originalIntent.payload.candidate shouldBe target.candidate
        originalIntent.payload.receipts shouldBe empty
        episode.retained(identity, target.sourceRevision, "check-result-0") shouldBe None
        episode.current(identity).history shouldBe before.get.history
        compileExecutions(root) shouldBe empty

        Given("missing evidence, crossed evidence participant/reference and crossed complete public targets")
        val missing = V.GoalPhaseValidationRecoveryInput(identity, actual.target, "AuthorizeNewAttempt", "Accept repeated effects",
          V.WorkflowArtifactReference("missing-independent-proof", "1"))
        val crossedParticipant = episode.proof(actual, "AuthorizeNewAttempt", "Accept repeated effects",
          V.WorkflowArtifactReference("crossed-participant", "1"), participant = "another-participant")
        val genuine = episode.proof(actual, "AuthorizeNewAttempt", "Accept repeated effects", V.WorkflowArtifactReference("genuine-human", "1"))
        val crossedTargets = Vector(target.copy(sessionId = "another-session"), target.copy(planIdentity = "another-plan"),
          target.copy(planRevision = "2"), target.copy(base = target.base.copy(tree = "c" * 40)),
          target.copy(candidate = target.candidate.copy(tree = "d" * 40)), target.copy(sourceRevision = "1"),
          target.copy(frontierRevision = (target.frontierRevision.toLong + 1).toString), target.copy(step = "step-2"),
          target.copy(checkIdentity = "another-check"), target.copy(checkOrdinal = 2), target.copy(attempt = 1),
          target.copy(intentRevision = target.sourceRevision))
        When("those missing/crossed requests call the actual public recovery Operation")
        val refused = (Vector(missing, crossedParticipant, genuine.copy(reference = V.WorkflowArtifactReference("crossed-reference", "2"))) ++
          crossedTargets.map(t => genuine.copy(target = R.targetRecord(t)))).map(input =>
            episode.call(R.recoveryOperation, List(Property("input", input.toRecord(), None))))
        Then("every request refuses with no native-state or execution change")
        refused.foreach(_.isSuccess shouldBe false)
        episode.current(identity).history shouldBe before.get.history
        compileExecutions(root) shouldBe empty

        Given("a real test-owned independent Defer direction bound to the exact description")
        val defer = episode.proof(actual, "Defer", "Leave the old outcome unknown", V.WorkflowArtifactReference("defer-human", "1"))
        When("Defer is admitted and ordinary Advance is requested again")
        val deferred = episode.recover(defer)
        val unchanged = episode.advance(identity)
        Then("the old intent remains unchanged and no retry is granted")
        deferred.accepted shouldBe true
        deferred.outcome shouldBe "deferred"
        unchanged.kind shouldBe "Wait"
        episode.current(identity).history shouldBe before.get.history
        compileExecutions(root) shouldBe empty

        Given("the exact independently provisioned test-owned AuthorizeNewAttempt direction")
        When("the public recovery Operation admits that direction without executing Advance")
        val accepted = episode.recover(genuine)
        val recovered = episode.current(identity)
        val saved = episode.retained(identity, target.sourceRevision, "check-recovery-0").get
        Then("exactly one authorization is appended and every old fact and repair counter is retained")
        accepted.accepted shouldBe true
        accepted.outcome shouldBe "new-attempt-authorized"
        recovered.history.dropRight(1) shouldBe before.get.history
        recovered.revision.value shouldBe before.get.revision.value + 1
        saved.payload.copy(newAttempts = Vector.empty) shouldBe originalIntent.payload
        saved.payload.newAttempts.size shouldBe 1
        saved.payload.newAttempts.head.intentRevision shouldBe target.intentRevision
        saved.payload.newAttempts.head.evidenceIdentity shouldBe "genuine-human"
        val retained = episode.retained(identity, target.sourceRevision, "check-intent-0").get
        (retained.record.definition.workflowIdentity, retained.record.definition.workflowRevision,
          retained.record.definition.sourceCorrelation, retained.record.definition.producerAbiIdentity,
          retained.record.definition.workflowAbiIdentity, retained.record.definition.bootstrapAbiIdentity,
          retained.record.definition.producerRevision) shouldBe
          (originalIntent.record.definition.workflowIdentity, originalIntent.record.definition.workflowRevision,
            originalIntent.record.definition.sourceCorrelation, originalIntent.record.definition.producerAbiIdentity,
            originalIntent.record.definition.workflowAbiIdentity, originalIntent.record.definition.bootstrapAbiIdentity,
            originalIntent.record.definition.producerRevision)
        retained.record.copy(definition = originalIntent.record.definition) shouldBe originalIntent.record
        retained.payload shouldBe originalIntent.payload
        episode.retained(identity, target.sourceRevision, "check-result-0") shouldBe None
        episode.call(R.recoveryOperation, List(Property("input", genuine.toRecord(), None))).isSuccess shouldBe false
        episode.current(identity).history shouldBe recovered.history
        compileExecutions(root) shouldBe empty

        When("one later ordinary Advance uses that new attempt authorization")
        val review = episode.advance(identity)
        Then("the same Session reaches pending independent review after actual normal Compile, with no Start or resubmission")
        review.sessionId shouldBe identity
        review.kind shouldBe "Request"
        review.work.get.kind shouldBe "ReviewStep"
        review.work.get.selectedPlan.identity shouldBe _recovery_plan.identity
        review.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(0, 0, 2, Some("sm-workflow.repair-trend.v1"), Some(2), Some(2)))
        val result = episode.retained(identity, target.sourceRevision, "check-result-0-retry-1").get
        result.payload.newAttempts shouldBe saved.payload.newAttempts
        result.payload.receipts.head.exitCode shouldBe Some(0)
        result.payload.receipts.head.stdout should include("COMPILE_GENERATOR_ENTERED")
        episode.retained(identity, target.sourceRevision, "check-result-0") shouldBe None
        compileExecutions(root).size shouldBe 1
        episode.call(R.describeOperation, List(Property("sessionId", identity, None))).isSuccess shouldBe false
      }
    }
  }

  private def workBase(record: InstanceRecord): (String, String) = {
    val entry = record.history.find(_.causation.value.startsWith("ordered-goal-start:")).get.causation.value.split(':')
    (entry(entry.length - 2), entry.last)
  }
}
