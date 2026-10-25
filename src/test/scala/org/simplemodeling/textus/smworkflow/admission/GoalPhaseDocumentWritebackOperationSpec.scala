package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.processexecution.{GitObjectId, GitOperation}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.preparation.{NativeGoalStartInput, NativeReviewPreparation, NativeAuthorityPreparation}
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseDocumentProgress as P
import GoalPhaseDocumentSource as D
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeWork as S
import SplitPhaseExplicitPartitionFixture.take

/** Tracked documents and normal production Operations own the episode's real
  * effects. Selected source edits, reviews and authority rows are explicit
  * specification inputs; no production provider evidence is claimed.
  * @since   Oct. 17, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class GoalPhaseDocumentWritebackOperationSpec extends AnyWordSpec with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  private val _source = D.Source("docs/phase.md", "docs/checklist.md")
  private val _sourcepaths = Vector(_source.phasePath, _source.checklistPath)
  private val _paths = _sourcepaths ++ Vector("step-1.txt", "step-2.txt")
  private val _input = V.GoalPhaseStartInput("verification-p61-writeback", "sm-workflow")
  private def _step(identity: String): O.Step = O.Step(identity, "Implement " + identity,
    _sourcepaths :+ (identity + ".txt"), if (identity == "step-1") Vector.empty else Vector("step-1"), Vector("test"))
  private val _initialplan = O.Plan("verification-writeback-plan", "1", _input.phaseIdentity, _input.repositoryIdentity,
    Vector(_step("step-1")), Vector("test"))
  private val _tailplan = _initialplan.copy(revision = "2", steps = Vector(_step("step-1"), _step("step-2")))

  override protected def _prepare_sbt_build_(root: Path, scenario: String): Unit = {
    super._prepare_sbt_build_(root, scenario)
    val directory = Files.createDirectories(root.resolve("repository/docs"))
    if (!Files.exists(directory.resolve("phase.md"))) Files.writeString(directory.resolve("phase.md"), _phase(_initialplan))
    if (!Files.exists(directory.resolve("checklist.md"))) Files.writeString(directory.resolve("checklist.md"),
      "# Tracked verification checklist\n\n" + _items("step-1") + "\n" +
        "```sm-workflow-progress\n" + P.Projection(_input.phaseIdentity, Vector.empty).asJson.spaces2 + "\n```\n")
  }
  override protected def _setup_commands_(root: Path, scenario: String): Vector[Vector[String]] = {
    val commands = super._setup_commands_(root, scenario)
    val firstcommit = commands.indexWhere(_.contains("commit"))
    commands.patch(firstcommit, Vector(Vector("-C", root.resolve("repository").toString,
      "--literal-pathspecs", "add", "--", _source.phasePath, _source.checklistPath)), 0)
  }
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val git = TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString), goalPaths = goal.candidatePaths,
      goalMessages = goal.closure.programs.map(program => "Goal Step " + program.step),
      goalSelections = goal.closure.programs.map(program => _sourcepaths :+ (program.step + ".txt")))
    TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(1), Some(git))
  }

  private def _root(): Path = Files.createTempDirectory(Files.createDirectories(
    Path.of("target/sm-workflow/work/document-writeback").toAbsolutePath), "episode-").toRealPath()
  private def _host(root: Path): ThreeWorkflowRuntimeParameters.Definition = {
    val original = ThreeWorkflowNormalHostFixture.definition(root)
    original.copy(goal = original.goal.copy(input = _input, candidatePaths = _paths,
      closure = original.goal.closure.copy(programs = Vector(
        GoalPhaseNativeClosure.StepProgram("step-1", 1), GoalPhaseNativeClosure.StepProgram("step-2", 2)))))
  }
  private def _items(step: String): String = Vector("A", "B").map(suffix =>
    "- [ ] VERIFY-" + step + "-" + suffix + ": Accept " + step + " " + suffix).mkString("\n") + "\n"
  private def _phase(plan: O.Plan): String = "# Tracked verification Phase\n\nOriginal narrative.\n\n```sm-workflow-plan\n" +
    Json.obj("schemaVersion" -> Json.fromString("sm-workflow.phase-plan.v1"), "plan" -> plan.asJson,
      "bindings" -> Json.fromValues(plan.steps.map(step => Json.obj("step" -> Json.fromString(step.identity),
        "items" -> Vector("VERIFY-" + step.identity + "-B", "VERIFY-" + step.identity + "-A").asJson)))).spaces2 + "\n```\n"
  private def _append_tail(root: Path): Unit = {
    val repository = root.resolve("repository")
    Files.writeString(repository.resolve(_source.phasePath), _phase(_tailplan) + "\nTail selected after actual Step1 commit.\n")
    val path = repository.resolve(_source.checklistPath)
    Files.writeString(path, Files.readString(path) + "\nLatest independently edited narrative.\n" + _items("step-2"))
  }
  private def _install_c(call: Setup): Consequence[Unit] = {
    val declaration = Json.obj("schemaVersion" -> Json.fromString(D.declarationSchema),
      "phasePath" -> Json.fromString(_source.phasePath), "checklistPath" -> Json.fromString(_source.checklistPath))
    val files = Map("input.json" -> _input.asJson.noSpaces, "plan.json" -> declaration.noSpaces)
    val prepared = take(NativeGoalStartInput.prepareC(Map("input" -> "input.json", "plan" -> "plan.json",
      "selection-id" -> "verification-writeback-selection", "participant" -> "participant", "origin" -> "ExplicitHuman",
      "idempotency-key" -> "verification-writeback-start", "selection-collection" -> "entity:smworkflow.cli.goal_selection",
      "plan-collection" -> "entity:smworkflow.cli.goal_plans"), path =>
      files.get(path).map(Consequence.success).getOrElse(Consequence.stateConflict("Unselected specification file"))))
    prepared.getVector("datastore").get.map(_.asInstanceOf[Record]).zip(Vector("goal_selection", "goal_plans"))
      .foldLeft(Consequence.unit) { case (prior, (entry, name)) =>
        val row = entry.getVector("records").get.head.asInstanceOf[Record]
        prior.flatMap(_ => call.createC(name, row.getString("id").get, row.removeKeys(Set("id"))))
      }
  }
  private def _storage_c(call: Setup): Consequence[WorkflowComponentStorage] = for {
    catalog <- GoalPhaseRuntimeCatalog.loadC()
    storage <- WorkflowComponentStorage.bindC(ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin),
      SmWorkflowComponent.componentId, _configuration_, Vector(catalog.definition), _collection_("instances"), _collection_("continuations"))
  } yield storage
  private def _with_episode(root: Path, initialize: Boolean = false,
    interrupt: Boolean = false)(task: Episode => Unit): Unit = {
    val acknowledge: String => Consequence[Unit] = point => {
      if (point == "goal-step-commit-recorded:step-1") _append_tail(root)
      if (interrupt && point == "goal-document-progress-prepared") Consequence.stateConflict("Specification interrupts before File effect")
      else Consequence.unit
    }
    _with_runtime_(root, "DocumentWriteback", Some(_host(root)), goalAcknowledgement = Some(acknowledge)) {
      (owner, component, actor, prepare) =>
        if (initialize) prepare(call => call.setupC(_setup_commands_(root, "DocumentWriteback").size).flatMap(_ => _install_c(call)))
        task(new Episode(root, owner, component, actor, prepare))
    }
  }

  private final class Episode(root: Path, owner: Subsystem, component: Component, actor: ExecutionContext,
    prepare: (Setup => Consequence[Unit]) => Unit) {
    def call(operation: String, properties: List[Property]) = owner.executeOperationResponseInContext(
      Request.of(component = component.name, service = "GoalPhaseService", operation = operation, properties = properties), actor)
    def invoke[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A = read_value[A](take(call(operation, properties)))
    def start(): String = invoke[V.GoalPhaseStartResult]("startGoalPhase", NativeWorkflowOperationInputFixture.goal(_input)).sessionId
    def advance(identity: String): V.GoalPhaseAdvanceResult = invoke[V.GoalPhaseAdvanceResult]("advanceGoalPhase",
      NativeWorkflowOperationInputFixture.advance(identity))
    def current(identity: String): InstanceRecord = {
      var result = Option.empty[InstanceRecord]
      prepare(call => call.goalCurrentC(InstanceIdentity(identity)).map(value => { result = Some(value); () }))
      result.get
    }
    def work(identity: String): WorkflowInteraction[S.Request, Nothing] = {
      var result = Option.empty[WorkflowInteraction[S.Request, Nothing]]
      prepare(call => call.goalWorkC(InstanceIdentity(identity)).map(value => { result = Some(value); () }))
      result.get
    }
    def observe(): RepositorySyncManagedGitObservation.Observed = {
      var result = Option.empty[RepositorySyncManagedGitObservation.Observed]
      prepare(call => call.observeC().map(value => { result = Some(value); () }))
      result.get
    }
    def index(): String = {
      var result = Option.empty[String]
      prepare(call => RepositorySyncManagedGitObservation.indexTreeC(call.core).map(value => { result = Some(value); () }))
      result.get
    }
    def pendingDiff(parent: String): String = {
      var result = Option.empty[String]
      prepare(call => GitObjectId.parseC(parent).flatMap(id => WorkflowManagedProcessExecution.executeGitC(
        call.core, GitOperation.PendingDiff(id)).flatMap(_.valueC)).map(value => { result = Some(value.text); () }))
      result.get
    }
    def progress(identity: String): Vector[GoalPhaseNativeDocumentProgress.Saved] = {
      val record = current(identity)
      val sources = record.history.zipWithIndex.collect {
        case (entry, index) if entry.causation.value == "ordered-document-progress-result" =>
          take(GoalPhaseNativeHistory.prefixC(record, record.history(index - 2).revision))
      }
      var result = Vector.empty[GoalPhaseNativeDocumentProgress.Saved]
      prepare(call => for {
        storage <- _storage_c(call)
        reader <- storage.bindMechanicalC(GoalPhaseNativeRun.advanceOperation, GoalPhaseNativeDocumentProgress.codec)
        _ <- sources.foldLeft(Consequence.unit) { (prior, from) => prior.flatMap { _ =>
          reader.loadRetainedC(GoalPhaseNativeDocumentProgress.reference(from, "result"))(using call.executionContext).flatMap {
            case Some(value) => result = result :+ GoalPhaseNativeDocumentProgress.Saved(value.receipt.next, value.receipt.payload.value); Consequence.unit
            case None => Consequence.stateConflict("Actual progress result has no retained receipt")
          }
        }}
      } yield ())
      result
    }
    def submit(work: WorkflowInteraction[S.Request, Nothing], displayed: Option[V.GoalPhaseAdvanceResult] = None,
      resultRevision: Option[String] = None, expectedRejection: Boolean = false): ContinuationResult[S.Result] = {
      val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request
      val input = order.input.get.value
      val review = input.kind.startsWith("Review")
      val reference = ContextReference("specification-writeback-" + input.kind, resultRevision.getOrElse(order.expectedRevision.value))
      val value = S.Result(if (review) "accept" else "completed", "Specification-owned assigned semantic result", Vector.empty)
      val result = ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision, order.context.snapshot,
        TypedValue(S.resultCodec.typeIdentity, value), reference, Vector.empty,
        ExecutionEvidence(Vector(reference), Some(if (review) "specification-reviewer" else "participant")))
      if (review) prepare(call => for {
        prepared <- NativeReviewPreparation.goalC(displayed.get.work.get.review.get.reviewContext.get,
          V.GoalPhaseWorkResult(value.outcome, value.summary, value.findings, None,
            GoalPhaseFixAssessment.publicReport(value.fixAssessment), GoalPhaseFixIssues.publicReport(value.issueReport), None,
            GoalPhaseFindingRecording.publicObservations(value.reviewObservations), None,
            value.completionConditions.map(condition => GoalPhaseNativeMetadata.record(condition.asJson)), None, dependencyValidation = GoalPhaseDependencyValidation.publicSelection(value.dependencyValidation)),
          V.WorkflowArtifactReference(reference.identity, reference.revision),
          V.WorkflowExecutionEvidence(Vector(V.WorkflowArtifactReference(reference.identity, reference.revision)), result.evidence.workerIdentity, None, None),
          "normal-development-host", call.executionContext.clock.instant().minusSeconds(60))
        _ <- call.createC("host_evidence", prepared.recordIdentity, prepared.record)
      } yield ())
      val submitted = call("submitGoalPhaseWorkResult", take(NativeWorkflowOperationInputFixture.submissionC(result)))
      if (expectedRejection) {
        submitted.isSuccess shouldBe false
        submitted.toString should include("actual review report or original target is invalid")
      }
      else read_value[V.GoalPhaseWorkSubmissionResult](take(submitted)).accepted shouldBe true
      result
    }
    def authority(identity: String, waiting: V.GoalPhaseAdvanceResult, child: Boolean = false): Unit = {
      val from = current(identity)
      val selection = _repository_selection_(_host(root).repository.input, "verification-writeback-child",
        identity + ":repository-closure:" + from.revision.value)
      prepare(call => for {
        prepared <- NativeAuthorityPreparation.goalC(waiting.authorityContext.get,
          V.WorkflowArtifactReference("specification-writeback-authority", from.revision.value.toString),
          "participant", "normal-development-host", call.executionContext.clock.instant().minusSeconds(60))
        _ <- call.createC("host_evidence", prepared.recordIdentity, prepared.record)
        _ <- if (child) _prepare_input_(call, _host(root).repository.input, selection, root) else Consequence.unit
      } yield ())
    }
    def childCheckpoint(identity: String, from: InstanceRecord): RepositorySyncNativeProgressStore.Stage = {
      var result = Option.empty[RepositorySyncNativeProgressStore.Stage]
      prepare(call => for {
        child <- call.goalChildSessionC(InstanceIdentity(identity), from.revision.value.toString)
        stage <- call.nativeStageC(_host(root).repository.input, InstanceIdentity(child), root, "preserved")
        _ <- stage.map(value => { result = Some(value); Consequence.unit })
          .getOrElse(Consequence.stateConflict("Actual child has no preserved state"))
      } yield ())
      result.get
    }
  }

  private def _request(work: WorkflowInteraction[S.Request, Nothing]): S.Request =
    work.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value
  private def _order(work: WorkflowInteraction[S.Request, Nothing]): ContinuationRequest[S.Request] =
    work.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request
  private def _checks(root: Path): Int = {
    val marker = root.resolve("sbt-executions.txt")
    if (Files.exists(marker)) Files.readString(marker).linesIterator.size else 0
  }
  private def _outside(text: String): (String, String) = {
    val start = text.indexOf("```sm-workflow-progress\n") + "```sm-workflow-progress\n".length
    val end = text.indexOf("```", start)
    text.substring(0, start) -> text.substring(end)
  }
  private def _assert_projection(text: String, desired: P.Projection): Unit =
    take(P.mergeC(text, desired.phase, Some(desired), desired)) shouldBe P.Merged(true, None)

  "Tracked document progress through normal GoalPhase Operations" should {
    "advance an opaque implementation reference and reject an opaque review revision before consuming its pending work" in {
      Given("Spec: docs/spec/native-goal-command-task.md; a disposable tracked first Step with normal Operations and explicit specification review evidence")
      val root = _root()
      val revision = "p64-e1-proposal-2026-10-20-1"
      _with_episode(root, initialize = true) { episode =>
        val identity = episode.start()
        episode.advance(identity).kind shouldBe "Request"
        val implementation = episode.work(identity)
        _request(implementation).kind shouldBe "ImplementStep"
        Files.writeString(root.resolve("repository/step-1.txt"), "First assigned boundary.\n")
        When("normal Submit accepts the assigned implementation with its opaque result revision and ordinary Advance reconstructs it")
        val completed = episode.submit(implementation, resultRevision = Some(revision))
        val displayed = episode.advance(identity)
        val pending = episode.work(identity)
        val before = episode.current(identity)
        Then("the original reference stays exact and successful candidate checks reach the independent Step review")
        completed.resultReference.revision shouldBe revision
        displayed.kind shouldBe "Request"
        _request(pending).kind shouldBe "ReviewStep"
        _assert_product_check_(root, _request(pending).review.get.checks.last.stdout)
        _request(pending).review.get.checks.last.exitCode shouldBe Some(0)

        When("normal review preparation supplies actual host evidence and Submit receives an opaque review revision")
        episode.submit(pending, Some(displayed), resultRevision = Some(revision), expectedRejection = true)
        Then("Submit refuses before changing the current instance, history or pending continuation")
        val after = episode.current(identity)
        (after.definition.workflowIdentity, after.definition.workflowRevision, after.definition.sourceCorrelation,
          after.definition.producerAbiIdentity, after.definition.workflowAbiIdentity, after.definition.bootstrapAbiIdentity,
          after.definition.producerRevision) shouldBe
          (before.definition.workflowIdentity, before.definition.workflowRevision, before.definition.sourceCorrelation,
            before.definition.producerAbiIdentity, before.definition.workflowAbiIdentity, before.definition.bootstrapAbiIdentity,
            before.definition.producerRevision)
        after.copy(definition = before.definition) shouldBe before
        after.history shouldBe before.history
        episode.work(identity) shouldBe pending

        When("the same pending review is submitted with its valid numeric revision and ordinary authority and Advance continue")
        val accepted = episode.submit(pending, Some(displayed))
        val waiting = episode.advance(identity)
        episode.authority(identity, waiting)
        val next = episode.advance(identity)
        val second = _request(episode.work(identity))
        Then("the unconsumed review completes normally and the committed first Step reaches the next implementation")
        next.kind shouldBe "Request"
        second.kind shouldBe "ImplementStep"
        second.stepIdentity shouldBe Some("step-2")
        second.completed.size shouldBe 1
        second.completed.head.review shouldBe Some(accepted.resultReference.identity)
      }
    }

    "select the added second Step after the actual first commit, preserve latest edits, detect overlap and close the staged full Phase" in {
      Given("tracked Phase/checklist documents, an empty opt-in, fixed Git programs and a normal selected ProductCheck build")
      val root = _root()
      val checklist = root.resolve("repository").resolve(_source.checklistPath)
      var identity = ""
      var step2 = Option.empty[WorkflowInteraction[S.Request, Nothing]]
      var pending = Option.empty[InstanceRecord]
      var originalbase = Option.empty[O.Snapshot]
      var firstprojection = Option.empty[P.Projection]
      _with_episode(root, initialize = true) { episode =>
        identity = episode.start()
        val first = episode.advance(identity)
        val implementation = episode.work(identity)
        originalbase = Some(_request(implementation).originalBase)
        first.kind shouldBe "Request"
        _request(implementation).selectedPlan.steps.map(_.identity) shouldBe Vector("step-1")
        When("the specification performs only assigned Step1 source work and normal Submit/Advance runs actual candidate and SBT checks")
        Files.writeString(root.resolve("repository/step-1.txt"), "First assigned boundary.\n")
        episode.submit(implementation)
        val displayed = episode.advance(identity)
        val review = episode.work(identity)
        Then("the independent Step1 assignment carries actual product-check success and its candidate")
        _request(review).kind shouldBe "ReviewStep"
        _assert_product_check_(root, _request(review).review.get.checks.last.stdout)
        _request(review).review.get.checks.last.exitCode shouldBe Some(0)
        When("explicit specification review evidence and separate Step authority admit the real first commit")
        val accepted = episode.submit(review, Some(displayed))
        val waiting = episode.advance(identity)
        episode.authority(identity, waiting)
        val next = episode.advance(identity)
        step2 = Some(episode.work(identity))
        pending = Some(episode.current(identity))
        val progress = episode.progress(identity)
        firstprojection = Some(progress.head.payload.desired)
        Then("the exact commit callback's new tail is selected before genuine Step1 writeback and Step2 issue")
        next.kind shouldBe "Request"
        val input = _request(step2.get)
        input.kind shouldBe "ImplementStep"
        input.stepIdentity shouldBe Some("step-2")
        input.selectedPlan.steps.map(_.identity) shouldBe Vector("step-1", "step-2")
        input.selectedPlan.revision shouldBe "2"
        input.originalBase shouldBe originalbase.get
        input.completed.size shouldBe 1
        input.completed.head.review shouldBe Some(accepted.resultReference.identity)
        progress.map(_.payload.outcome) shouldBe Vector(Some("updated"))
        firstprojection.get.steps.map(_.commit) shouldBe input.completed.map(_.commit)
        firstprojection.get.steps.map(_.review) shouldBe input.completed.map(_.review)
        firstprojection.get.steps.head.items shouldBe Vector("VERIFY-step-1-A", "VERIFY-step-1-B")
        _assert_projection(Files.readString(checklist), firstprojection.get)
        Files.readString(checklist) should include("Latest independently edited narrative.")
        val causes = pending.get.history.map(_.causation.value)
        val selectionindex = causes.lastIndexOf("ordered-document-plan-selected")
        val progressindex = causes.indexOf("ordered-document-progress-intent")
        val issueindex = causes.lastIndexWhere(_.startsWith("ordered-implementation-issued:"))
        selectionindex should be >= 0
        progressindex should be > selectionindex
        issueindex should be > causes.indexOf("ordered-document-progress-result")
      }

      _with_episode(root) { episode =>
        When("a reopened normal runtime displays the real pending Step2 assignment")
        val displayed = episode.advance(identity)
        val retained = episode.work(identity)
        Then("the saved continuation, request and context are exact and no new progress update appears")
        displayed.kind shouldBe "Request"
        _request(retained) shouldBe _request(step2.get)
        _order(retained).continuationId shouldBe _order(step2.get).continuationId
        _order(retained).context shouldBe _order(step2.get).context
        episode.current(identity).revision shouldBe pending.get.revision
        episode.progress(identity).size shouldBe 1
        Given("Step2 is still pending while narrative, item presentation and one owned review field change")
        val oldreview = firstprojection.get.steps.head.review
        val edited = Files.readString(checklist).replace(oldreview.asJson.noSpaces, "verification-overlap".asJson.noSpaces)
          .replace(_items("step-1"), _items("step-1").linesIterator.toVector.reverse.map(_.replace("[ ]", "[x]")).mkString("\n") + "\n")
        Files.writeString(checklist, "Another current narrative edit.\n" + edited)
        val before = episode.current(identity)
        When("pending Advance displays the assignment and normal Submit accepts its actual semantic completion")
        episode.advance(identity).kind shouldBe "Request"
        episode.current(identity).revision shouldBe before.revision
        Files.writeString(root.resolve("repository/step-2.txt"), "Second assigned boundary.\n")
        val submitted = episode.submit(retained)
        val completed = episode.current(identity)
        val index = episode.index()
        val checks = _checks(root)
        val observed = episode.observe()
        val failed = episode.call("advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(identity))
        Then("unissued Advance explicitly rejects the owned overlap before candidate, staging or checks")
        failed.isSuccess shouldBe false
        failed.toString should include("overlap")
        completed.history.last.causation.value shouldBe submitted.resultReference.identity
        episode.current(identity).revision shouldBe completed.revision
        episode.current(identity).history shouldBe completed.history
        episode.index() shouldBe index
        episode.observe() shouldBe observed
        _checks(root) shouldBe checks
        episode.progress(identity).size shouldBe 1
        When("only the edited owned field is reconciled to actual Step1 facts and ordinary Advance continues")
        Files.writeString(checklist, Files.readString(checklist).replace("verification-overlap".asJson.noSpaces, oldreview.asJson.noSpaces))
        val outside = _outside(Files.readString(checklist))
        val stepdisplay = episode.advance(identity)
        val stepreview = episode.work(identity)
        Then("actual Step2 checks and its normal independent review preserve the old completed prefix")
        _request(stepreview).kind shouldBe "ReviewStep"
        _request(stepreview).completed shouldBe _request(retained).completed
        _assert_product_check_(root, _request(stepreview).review.get.checks.last.stdout)
        _request(stepreview).review.get.checks.last.exitCode shouldBe Some(0)
        When("normal independent review, separate authority and the second real Step commit reach whole-Phase review")
        episode.submit(stepreview, Some(stepdisplay))
        val stepwaiting = episode.advance(identity)
        episode.authority(identity, stepwaiting)
        val phasedisplay = episode.advance(identity)
        val phasereview = episode.work(identity)
        val phaseinput = _request(phasereview)
        val progress = episode.progress(identity)
        val desired = progress.last.payload.desired
        val inspected = episode.observe()
        Then("both actual commits are projected and only fixed source documents form the staged management delta")
        phaseinput.kind shouldBe "ReviewPhase"
        phaseinput.originalBase shouldBe originalbase.get
        phaseinput.completed.size shouldBe 2
        phaseinput.completed.head shouldBe _request(retained).completed.head
        desired.steps.map(_.commit) shouldBe phaseinput.completed.map(_.commit)
        desired.steps.map(_.review) shouldBe phaseinput.completed.map(_.review)
        progress.map(_.payload.outcome) shouldBe Vector(Some("updated"), Some("updated"))
        progress.last.payload.previous shouldBe firstprojection
        _assert_projection(Files.readString(checklist), desired)
        _outside(Files.readString(checklist)) shouldBe outside
        phaseinput.review.get.changedPaths.toSet shouldBe _paths.toSet
        phaseinput.review.get.diff should include("First assigned boundary.")
        phaseinput.review.get.diff should include("Second assigned boundary.")
        phaseinput.review.get.diff should include("Another current narrative edit.")
        phaseinput.review.get.diff should include(phaseinput.completed.last.commit.identity)
        inspected.head shouldBe phaseinput.completed.last.commit.identity
        inspected.tree shouldBe phaseinput.completed.last.commit.tree
        inspected.dirty should not be empty
        inspected.dirty.forall(path => _sourcepaths.contains(path.path) && path.worktree == ' ' && !path.unmerged) shouldBe true
        episode.index() shouldBe phaseinput.candidate.get.tree
        phaseinput.candidate.get.tree should not be phaseinput.completed.last.commit.tree
        _assert_product_check_(root, phaseinput.review.get.checks.last.stdout)
        phaseinput.review.get.checks.last.exitCode shouldBe Some(0)
        When("normal Phase review and separate Phase/child input authority run the existing FULL_STATE closure")
        episode.submit(phasereview, Some(phasedisplay))
        val waiting = episode.advance(identity)
        val closing = episode.current(identity)
        val writecount = progress.size
        episode.authority(identity, waiting, child = true)
        val terminal = episode.advance(identity)
        val result = terminal.completion.get
        val checkpoint = episode.childCheckpoint(identity, closing)
        val actual = episode.observe()
        Then("the child checkpoints the reviewed document index and converges with a separate actual tip/tree")
        terminal.kind shouldBe "Terminal"
        result.originalBase.head shouldBe originalbase.get.head
        result.originalBase.tree shouldBe originalbase.get.tree
        result.completed.map(_.commit.identity) shouldBe phaseinput.completed.map(_.commit.identity)
        result.reviewedCandidate.head shouldBe phaseinput.completed.last.commit.identity
        result.reviewedCandidate.tree shouldBe phaseinput.candidate.get.tree
        checkpoint.payload.method shouldBe Some("checkpoint")
        checkpoint.payload.indexTree shouldBe Some(phaseinput.candidate.get.tree)
        checkpoint.payload.checkpoint shouldBe Some(result.repository.get.tip)
        result.repository.get.tip should not be result.reviewedCandidate.head
        result.repository.get.tree shouldBe result.reviewedCandidate.tree
        actual.head shouldBe result.repository.get.tip
        actual.tree shouldBe result.repository.get.tree
        actual.trackingTip shouldBe Some(result.repository.get.tip)
        actual.dirty shouldBe empty
        episode.index() shouldBe actual.tree
        episode.pendingDiff(phaseinput.completed.last.commit.identity) should include(desired.steps.last.commit.identity)
        _assert_projection(Files.readString(checklist), desired)
        Files.readString(root.resolve("repository").resolve(_source.phasePath)) should include("Tail selected after actual Step1 commit.")
        Files.readString(root.resolve("repository/step-1.txt")) shouldBe "First assigned boundary.\n"
        Files.readString(root.resolve("repository/step-2.txt")) shouldBe "Second assigned boundary.\n"
        episode.current(identity).lifecycle shouldBe Lifecycle.Completed
        _checks(root) should be >= 3
        Given("the completed native Phase and committed final progress block")
        val closed = episode.current(identity)
        val finaltext = Files.readString(checklist)
        When("subsequent normal read and Advance reuse the existing actual completion")
        val again = episode.advance(identity)
        Then("the terminal and current source remain stable without another progress write")
        again.kind shouldBe "Terminal"
        again.completion.get.repository shouldBe result.repository
        episode.current(identity).revision shouldBe closed.revision
        episode.progress(identity).size shouldBe writecount
        Files.readString(checklist) shouldBe finaltext
      }
    }

    "retain a prepared unknown File effect across reopening without executing it again" in {
      Given("a normal tracked first Step whose commit selects a second Step and an acknowledgement interrupted before File")
      val root = _root()
      var identity = ""
      var current = Option.empty[InstanceRecord]
      var text = ""
      var checks = 0
      _with_episode(root, initialize = true, interrupt = true) { episode =>
        identity = episode.start()
        episode.advance(identity)
        val implementation = episode.work(identity)
        Files.writeString(root.resolve("repository/step-1.txt"), "First assigned boundary.\n")
        episode.submit(implementation)
        val displayed = episode.advance(identity)
        episode.submit(episode.work(identity), Some(displayed))
        episode.authority(identity, episode.advance(identity))
        When("owning Advance durably prepares progress and receives the interrupted acknowledgement")
        val failed = episode.call("advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(identity))
        current = Some(episode.current(identity))
        text = Files.readString(root.resolve("repository").resolve(_source.checklistPath))
        checks = _checks(root)
        Then("actual history retains only its intent and no updated projection is claimed")
        failed.isSuccess shouldBe false
        current.get.history.last.causation.value shouldBe "ordered-document-progress-intent"
        current.get.suspension shouldBe None
        episode.progress(identity) shouldBe empty
        take(P.mergeC(text, _input.phaseIdentity, None, P.Projection(_input.phaseIdentity, Vector.empty))) shouldBe P.Merged(true, None)
      }
      _with_episode(root) { episode =>
        When("a reopened ordinary Advance reconstructs the intent with no actual result")
        val waiting = episode.advance(identity)
        val repeated = episode.advance(identity)
        Then("it returns stable Wait at the actual record without replay, dispatch or another check")
        waiting.kind shouldBe "Wait"
        repeated.kind shouldBe "Wait"
        waiting.toString should include("unknown File effect")
        episode.current(identity).revision shouldBe current.get.revision
        episode.current(identity).history shouldBe current.get.history
        episode.progress(identity) shouldBe empty
        _checks(root) shouldBe checks
        Files.readString(root.resolve("repository").resolve(_source.checklistPath)) shouldBe text
      }
    }
  }
}
