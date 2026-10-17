package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.{ComponentRecordStorage, DataStore}
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Property, Request}
import org.simplemodeling.textus.smworkflow.value as V
import org.goldenport.record.Record
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms.Target
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeWork as S
import SplitPhaseExplicitPartitionFixture.take

/** Explicit artifact-dependent selection through runtimeTestOnly. Normal sbt
  * owns its native locks; whole workflows use a clean typed Git/SBT host plan.
  */
@DoNotDiscover
/**
 * @since   Oct.  7, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class ThreeWorkflowSbtOperationSpec extends AnyWordSpec with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  private def _sbt_executable: Path = {
    val path = Path.of(sys.props.getOrElse("smworkflow.spec.sbt", fail("Missing normal smworkflow.spec.sbt executable")))
    path.isAbsolute shouldBe true
    Files.isRegularFile(path.toRealPath()) shouldBe true
    Files.isExecutable(path.toRealPath()) shouldBe true
    path
  }
  override protected def _setup_commands_(root: Path, scenario: String): Vector[Vector[String]] = {
    val original = super._setup_commands_(root, scenario)
    require(Set("Equal", "Goal")(scenario), "Only this owned selected build is admitted")
    original.take(2) ++ Vector(Vector("-C", root.resolve("repository").toString, "add", "--", ".")) ++ original.drop(2)
  }
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowHostParameters.Goal): Json = {
    val isolated = Set("NormalSbtSuccess", "NormalSbtFailure", "NormalSbtUnknown")(scenario)
    require(isolated || Set("Equal", "Goal")(scenario), "Only the selected normal SBT episodes are admitted")
    val gitPlan = if (isolated) None else Some(TypedGitHostPlanFixture.plan(
      Target(root.resolve("repository"), "main", "origin", "main", root.resolve("upstream.git").toString),
      goalPaths = if (scenario == "Goal") goal.candidatePaths else Vector.empty,
      goalMessages = if (scenario == "Goal") goal.closure.programs.map(p => "Goal Step " + p.step) else Vector.empty))
    val ordinals = if (isolated || scenario != "Goal") Vector(1) else
      (goal.stepValidation.checks ++ goal.phaseValidation.checks).map(_.ordinal).distinct
    TypedSbtHostPlanFixture.plan(root, _sbt_executable, ordinals, gitPlan)
  }
  private def _build(root: Path, successful: Boolean): Unit = TypedSbtHostPlanFixture.build(root, successful)
  private def _same_native(actual: InstanceRecord, expected: InstanceRecord): Unit = {
    // Bindings belong to each runtime. Compare their persisted business fields,
    // and compare the full remaining native record including revision/history.
    actual.copy(definition = expected.definition) shouldBe expected
    Vector(actual.definition.workflowIdentity, actual.definition.workflowRevision, actual.definition.sourceCorrelation,
      actual.definition.producerAbiIdentity, actual.definition.workflowAbiIdentity, actual.definition.bootstrapAbiIdentity,
      actual.definition.producerRevision) shouldBe Vector(expected.definition.workflowIdentity,
      expected.definition.workflowRevision, expected.definition.sourceCorrelation, expected.definition.producerAbiIdentity,
      expected.definition.workflowAbiIdentity, expected.definition.bootstrapAbiIdentity, expected.definition.producerRevision)
  }
  private val _storage_field = "normalSbtEvidence"
  private def _stored_c(call: Setup, identity: String): Consequence[Option[Json]] = for {
    storage <- ComponentRecordStorage.bindForCallC(call.core, "workflow", _collection_("host_evidence"), DataStore.StringEntryId(identity))
    record <- storage.loadC()
    value <- record match {
      case None => Consequence.success(None)
      case Some(row) => row.getRecord(_storage_field).map(stored => stored.toJsonStringC.flatMap(text => io.circe.parser.parse(text).fold(
        _ => Consequence.stateConflict("Stored normal SBT evidence is invalid"), value => Consequence.success(Some(value)))))
        .getOrElse(Consequence.stateConflict("Stored normal SBT evidence is absent"))
    }
  } yield value
  private def _retained_c(call: Setup): Consequence[Json] = _stored_c(call, "normal-sbt-result").flatMap {
    case Some(value) => Consequence.success(value)
    case None => Consequence.stateConflict("Normal SBT intent has no recorded result; uncertain effect is not replayed")
  }
  private def _capture(capture: ProcessExecutionCapture): Json = Json.obj(
    "bytes" -> Json.fromValues(capture.content.map(byte => Json.fromInt(byte.toInt))),
    "byteCount" -> Json.fromLong(capture.byteCount), "truncated" -> Json.fromBoolean(capture.truncated))
  private def _result(result: SbtExecutionResult, receipt: RepositorySyncNativeMergeValidation.Receipt): Json = Json.obj(
    "target" -> Json.fromString(result.target.workspaceId.print), "operation" -> Json.fromString(result.operation match {
      case SbtOperation.Compile => "compile"
      case SbtOperation.Test => "test"
      case SbtOperation.TestOnly => "test-only"
    }),
    "outcome" -> Json.fromString(result.outcome.toString), "termination" -> Json.fromString(result.processResult.termination.toString),
    "stdout" -> _capture(result.processResult.stdout), "stderr" -> _capture(result.processResult.stderr),
    "elapsedMillis" -> Json.fromLong(result.processResult.elapsedMillis),
    "artifacts" -> Json.fromValues(result.processResult.artifacts.map(artifact => Json.obj(
      "name" -> Json.fromString(artifact.name.value), "kind" -> Json.fromString(artifact.kind.toString),
      "byteCount" -> Json.fromLong(artifact.byteCount)))),
    "safeProgramIdentity" -> Json.fromString(result.processResult.safeProgramIdentity), "receipt" -> receipt.asJson)

  "Normal typed SBT connection" should {
    for (successful <- Vector(true, false)) {
      (if (successful) "persist normal typed SBT success and reread its actual result after reopening without replay"
        else "persist normal typed SBT failure and reread its original failure after reopening without replay") in {
        Given("a native selected build, SBT-only V2 authority and an owning SpecificationSetup Operation")
        val directory = Files.createDirectories(Path.of("target/sm-workflow/work/normal-typed-sbt").toAbsolutePath)
        val root = Files.createTempDirectory(directory, "episode-")
        _build(root, successful)
        val scenario = if (successful) "NormalSbtSuccess" else "NormalSbtFailure"
        val intent = Json.obj("target" -> Json.fromString("sm-workflow-repository-validation-1"),
          "operation" -> Json.fromString("test"), "checkIdentity" -> Json.fromString("test"), "state" -> Json.fromString("prepared"))
        var stored = Option.empty[Json]
        _with_runtime_(root, scenario) { (_, _, _, prepare) =>
          Given("the selected intent is committed by a completed managed Operation before the build effect")
          prepare(call => call.createC("host_evidence", "normal-sbt-intent", Record.data(_storage_field -> intent.noSpaces)))
          Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
          When("a fresh managed call reads that intent and invokes the protected typed Test DSL exactly once")
          prepare(call => for {
            retained <- _stored_c(call, "normal-sbt-intent")
            _ = retained shouldBe Some(intent)
            actual <- WorkflowManagedProcessExecution.repositoryValidationC(call.core, 1)
            receipt <- RepositorySyncNativeMergeValidation.sbtReceiptC("test", WorkflowSbtValidation.Kind.Test, actual)
            evidence = _result(actual, receipt)
            _ <- call.createC("host_evidence", "normal-sbt-result", Record.data(_storage_field -> evidence.noSpaces))
            _ = { stored = Some(evidence) }
          } yield ())
          Then("the typed outcome and durable receipt contain the external exit, bounded bytes and native checkout output")
          val value = stored.get
          value.hcursor.get[String]("target").toOption shouldBe Some("sm-workflow-repository-validation-1")
          value.hcursor.get[String]("operation").toOption shouldBe Some("test")
          value.hcursor.get[String]("outcome").toOption.get shouldBe
            (if (successful) SbtExecutionOutcome.Succeeded.toString else
              SbtExecutionOutcome.Failed(value.hcursor.downField("receipt").get[Int]("exitCode").toOption.get).toString)
          val receipt = take(value.hcursor.downField("receipt").as[RepositorySyncNativeMergeValidation.Receipt].fold(
            _ => Consequence.stateConflict("Actual receipt cannot be decoded"), Consequence.success))
          receipt.termination shouldBe "exited"
          value.hcursor.get[String]("termination").toOption shouldBe Some(ProcessExecutionTermination.Exited(receipt.exitCode.get).toString)
          value.hcursor.get[String]("safeProgramIdentity").toOption shouldBe Some("sm-workflow-repository-validation-1")
          value.hcursor.get[Long]("elapsedMillis").toOption.get should be >= 0L
          value.hcursor.downField("artifacts").focus.get shouldBe Json.arr()
          receipt.stdout should include("PRODUCT_CHECK_ENTERED " + root.resolve("repository"))
          if (successful) {
            receipt.exitCode shouldBe Some(0)
            receipt.stdout should include("PRODUCT_CHECK_PASSED")
          } else {
            receipt.exitCode.get should not be 0
            (receipt.stdout + receipt.stderr) should include("actual product input differs")
            receipt.stdout should not include "PRODUCT_CHECK_PASSED"
          }
          for (stream <- Vector("stdout", "stderr")) {
            val capture = value.hcursor.downField(stream)
            capture.get[Boolean]("truncated").toOption shouldBe Some(false)
            capture.get[Long]("byteCount").toOption.get shouldBe capture.get[Vector[Int]]("bytes").toOption.get.size.toLong
            capture.get[Long]("byteCount").toOption.get should be <= (if (stream == "stdout") 131072L else 16384L)
          }
          Files.readString(root.resolve("sbt-executions.txt")).linesIterator.size shouldBe 1
          Files.readString(root.resolve("sbt-executions.txt")).trim.toLong should not be ProcessHandle.current().pid()
        }
        Given("the first runtime is closed and only its saved intent and actual result are retained")
        var reread = Option.empty[Json]
        _with_runtime_(root, scenario) { (_, _, _, prepare) =>
          When("a freshly reopened runtime reads the result through a new managed call")
          prepare(call => _retained_c(call).map(value => { reread = Some(value); () }))
          Then("the complete original evidence is unchanged and no new child effect has occurred")
          reread shouldBe stored
          prepare(call => _stored_c(call, "normal-sbt-intent").map(value => { value shouldBe Some(intent); () }))
          Files.readString(root.resolve("sbt-executions.txt")).linesIterator.size shouldBe 1
        }
      }
    }

    "retain normal typed SBT uncertainty without executing a saved intent after reopening" in {
      Given("a committed selected intent whose original process outcome was never recorded")
      val directory = Files.createDirectories(Path.of("target/sm-workflow/work/normal-typed-sbt").toAbsolutePath)
      val root = Files.createTempDirectory(directory, "uncertain-")
      _build(root, true)
      val intent = Json.obj("target" -> Json.fromString("sm-workflow-repository-validation-1"),
        "operation" -> Json.fromString("test"), "checkIdentity" -> Json.fromString("test"), "state" -> Json.fromString("prepared"))
      _with_runtime_(root, "NormalSbtUnknown") { (_, _, _, prepare) =>
        prepare(call => call.createC("host_evidence", "normal-sbt-intent", Record.data(_storage_field -> intent.noSpaces)))
      }
      When("a fresh runtime requests the retained result through its owning Operation")
      var uncertain = false
      _with_runtime_(root, "NormalSbtUnknown") { (_, _, _, prepare) =>
        prepare(call => _retained_c(call) match {
          case Consequence.Failure(_) => uncertain = true; Consequence.unit
          case _ => Consequence.stateConflict("An unrecorded effect cannot become success")
        })
        Then("absence remains uncertain, intent survives and no replay launches a child")
        uncertain shouldBe true
        prepare(call => _stored_c(call, "normal-sbt-intent").map(value => { value shouldBe Some(intent); () }))
        Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
      }
    }
  }
  "Direct RepositorySync Operations with the runtime SBT adapter" should {
    for (successful <- Vector(true, false)) {
      (if (successful) "consume actual successful SBT test output and converge"
        else "consume an actual failed product test and wait without push") in {
        Given("a real selected build, public typed Git/SBT V3 authority and separately prepared repository effect evidence")
        val directory = Files.createDirectories(Path.of("target/sm-workflow/work/direct-repository-operation").toAbsolutePath)
        val root = Files.createTempDirectory(directory, "sbt-episode-")
        _build(root, successful)
        val input = ThreeWorkflowNormalHostFixture.definition(root).repository.input
        val selection = _repository_selection_(input, "selected-repository", "direct-sbt-start")
        var execution = Option.empty[InstanceIdentity]
        var retainedResult = Option.empty[V.RepositorySyncAdvanceResult]
        var retainedNative = Option.empty[InstanceRecord]
        var retainedValidation = Option.empty[RepositorySyncNativeMergeValidation.State]
        var retainedObservation = Option.empty[RepositorySyncManagedGitObservation.Observed]
        def identity: InstanceIdentity = execution.get
        def advance: List[Property] = NativeWorkflowOperationInputFixture.advance(identity.value)
        for (session <- Vector("execute")) _with_runtime_(root, "Equal") { (owner, component, actor, prepare) =>
          def invoke[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A = read_value[A](take(owner.executeOperationResponseInContext(
            Request.of(component = component.name, service = "RepositorySyncService", operation = operation,
              properties = properties), actor)))
          if (session == "execute") {
            prepare(call => call.setupC(_setup_commands_(root, "Equal").size).flatMap(_ => _prepare_input_(call, input, selection, root)))
            val started = invoke[V.RepositorySyncStartResult]("startRepositorySync", NativeWorkflowOperationInputFixture.repository(input))
            execution = Some(InstanceIdentity(started.sessionId))
          }
          When("public advance runs only the selected fixed SBT test, or observes its retained result in " + session)
          val result = invoke[V.RepositorySyncAdvanceResult]("advanceRepositorySync", advance)
          var current = Option.empty[InstanceRecord]
          var validation = Option.empty[RepositorySyncNativeMergeValidation.State]
          var observed = Option.empty[RepositorySyncManagedGitObservation.Observed]
          prepare(call => call.currentC(identity).flatMap(value => { current = Some(value)
            call.validationC(input, identity, root, if (successful) "integration-validated" else "validation-result-1")
              .flatMap(value => { validation = Some(value); call.observeC().map(value => { observed = Some(value); () }) })
          }))
          Then("the durable receipt contains the real compiled product test and actual launcher exit, not a claimed success")
          val receipt = validation.get.receipts.last
          receipt.identity shouldBe "test"
          receipt.termination shouldBe "exited"
          receipt.stdout should include("PRODUCT_CHECK_ENTERED " + root.resolve("repository"))
          if (successful) {
            receipt.exitCode shouldBe Some(0)
            receipt.stdout should include("PRODUCT_CHECK_PASSED")
            result.kind shouldBe "Terminal"
            result.convergence.get.tip shouldBe observed.get.head
            current.get.lifecycle shouldBe Lifecycle.Completed
          } else {
            receipt.exitCode.get should not be 0
            (receipt.stdout + receipt.stderr) should include("actual product input differs")
            receipt.stdout should not include "PRODUCT_CHECK_PASSED"
            result.kind shouldBe "Wait"
            current.get.lifecycle shouldBe Lifecycle.Active
            observed.get.counts shouldBe Some(RepositorySyncManagedGitObservation.Counts(0, 0))
          }
          val actual = Files.readString(root.resolve("sbt-executions.txt"))
          actual.linesIterator.size shouldBe 1
          actual.trim.toLong should not be ProcessHandle.current().pid()
          retainedResult = Some(result)
          retainedNative = current
          retainedValidation = validation
          retainedObservation = observed
        }
        Given("the original repository runtime is closed with its actual selected check result and native history retained")
        val originalExecution = Files.readString(root.resolve("sbt-executions.txt"))
        _with_runtime_(root, "Equal") { (owner, component, actor, prepare) =>
          When("public Advance observes the same RepositorySync in a fresh runtime without setup or another check")
          val reopened = read_value[V.RepositorySyncAdvanceResult](take(owner.executeOperationResponseInContext(
            Request.of(component = component.name, service = "RepositorySyncService", operation = "advanceRepositorySync",
              properties = advance), actor)))
          var current = Option.empty[InstanceRecord]
          var validation = Option.empty[RepositorySyncNativeMergeValidation.State]
          var observed = Option.empty[RepositorySyncManagedGitObservation.Observed]
          prepare(call => for {
            native <- call.currentC(identity)
            checked <- call.validationC(input, identity, root, if (successful) "integration-validated" else "validation-result-1")
            git <- call.observeC()
          } yield { current = Some(native); validation = Some(checked); observed = Some(git) })
          Then("the original Terminal or failed Wait, receipt, native revision and history survive with exactly one ProductCheck process")
          reopened shouldBe retainedResult.get
          reopened.kind shouldBe (if (successful) "Terminal" else "Wait")
          current.get.lifecycle shouldBe (if (successful) Lifecycle.Completed else Lifecycle.Active)
          _same_native(current.get, retainedNative.get)
          validation shouldBe retainedValidation
          observed shouldBe retainedObservation
          Files.readString(root.resolve("sbt-executions.txt")) shouldBe originalExecution
          originalExecution.linesIterator.size shouldBe 1
        }
      }
    }
  }
  "Direct GoalPhase Operations with the runtime SBT adapter" should afterWord(
    "in spec:normal-typed-sbt-validation, example:wholeGoal, rule:owning-workflow; spec:goal-phase-native-retention-applicability, example:E16, rule:R6") {
    Vector(false, true).foreach { lostAcknowledgement =>
    (if (lostAcknowledgement) "retain actual completion after its first post-append acknowledgement is lost"
      else "validate the Step and whole Phase, commit and converge through RepositorySync") in {
      Given("a real selected one-Step build, public typed Git/SBT V3 authority and separate semantic review and effect evidence")
      val directory = Files.createDirectories(Path.of("target/sm-workflow/work/direct-repository-operation").toAbsolutePath)
      val root = Files.createTempDirectory(directory, "sbt-goal-episode-")
      _build(root, true)
      Files.writeString(root.resolve("repository/value.txt"), "initial\n")
      val original = ThreeWorkflowNormalHostFixture.definition(root)
      val host = original.copy(goal = original.goal.copy(candidatePaths = Vector("value.txt")))
      val plan = O.Plan("selected-sbt-goal-plan", "1", host.goal.input.phaseIdentity,
        host.goal.input.repositoryIdentity, Vector(O.Step("step-1", "Correct selected product input",
          Vector("value.txt"), Vector.empty, Vector("test"))), Vector("test"), repairLimit = 1)
      val selection = _goal_selection_(host.goal.input, "selected-sbt-goal", "direct-sbt-goal-start")
      var execution = Option.empty[InstanceIdentity]
      def identity: InstanceIdentity = execution.get
      def advance: List[Property] = NativeWorkflowOperationInputFixture.advance(identity.value)
      var assigned = Option.empty[WorkflowInteraction[S.Request, Nothing]]
      var base = Option.empty[O.Snapshot]
      var phaseReviewReference = Option.empty[ContextReference]
      var retainedGoal = Option.empty[V.GoalPhaseAdvanceResult]
      var retainedGoalNative = Option.empty[InstanceRecord]
      var retainedChild = Option.empty[InstanceIdentity]
      var retainedChildNative = Option.empty[InstanceRecord]
      var retainedChildValidation = Option.empty[RepositorySyncNativeMergeValidation.State]
      var retainedObservation = Option.empty[RepositorySyncManagedGitObservation.Observed]
      var closureSourceRevision = Option.empty[String]
      var completionAcknowledgements = 0
      val acknowledge: String => Consequence[Unit] = point => {
        if (point == "goal-repository-completed") {
          completionAcknowledgements += 1
          if (completionAcknowledgements == 1) Consequence.stateConflict("Specification lost post-append Goal acknowledgement")
          else Consequence.unit
        } else Consequence.unit
      }
      def executions(): Vector[Long] = if (Files.exists(root.resolve("sbt-executions.txt")))
        Files.readString(root.resolve("sbt-executions.txt")).linesIterator.map(_.toLong).toVector else Vector.empty
      for (session <- Vector("issue", "implement", "step-review", "phase-review", "close"))
        _with_runtime_(root, "Goal", Some(host),
          goalAcknowledgement = if (lostAcknowledgement && session == "close") Some(acknowledge) else None) {
          (owner, component, actor, prepare) =>
          def invoke[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A = read_value[A](take(owner.executeOperationResponseInContext(
            Request.of(component = component.name, service = "GoalPhaseService", operation = operation,
              properties = properties), actor)))
          def current(): InstanceRecord = {
            var value = Option.empty[InstanceRecord]
            prepare(call => call.goalCurrentC(identity).map(record => { value = Some(record); () }))
            value.get
          }
          def input(): S.Request = assigned.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value
          def next(): Unit = {
            val response = invoke[V.GoalPhaseAdvanceResult]("advanceGoalPhase", advance)
            response.kind shouldBe "Request"
            response.sessionId shouldBe identity.value
            response.work.get.selectedPlan.identity shouldBe plan.identity
            prepare(call => call.goalWorkC(identity).map(value => { assigned = Some(value); () }))
            response.work.get.kind shouldBe input().kind
          }
          def result(outcome: String): ContinuationResult[S.Result] = {
            val work = assigned.get
            val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request
            val selected = order.input.get.value
            val reference = ContextReference("specification-sbt-" + selected.kind, selected.sourceRevision)
            ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision, order.context.snapshot,
              TypedValue(S.resultCodec.typeIdentity, S.Result(outcome, "Specification response to assigned work only", Vector.empty)),
              reference, Vector.empty, ExecutionEvidence(Vector(reference), Some(
                if (selected.kind.startsWith("Review")) "specification-reviewer" else "participant")))
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
            When("public Start captures the real clean base and owning advance issues only implementation")
            val started = invoke[V.GoalPhaseStartResult]("startGoalPhase", NativeWorkflowOperationInputFixture.goal(host.goal.input))
            execution = Some(InstanceIdentity(started.sessionId))
            next()
            Then("no SBT check has run before actual implementation")
            input().kind shouldBe "ImplementStep"
            input().selectedPlan shouldBe plan
            base = Some(input().originalBase)
            executions() shouldBe empty
          } else if (session == "close") {
            When("Phase review completion alone is advanced without separately prepared effect authority")
            invoke[V.GoalPhaseAdvanceResult]("advanceGoalPhase", advance).kind shouldBe "Wait"
            val pending = current()
            pending.currentProgression shouldBe Some(ProgressionReference("phase.PhaseClosing"))
            executions().size shouldBe 2
            Given("test-owned Phase closure evidence and separately selected RepositorySync input")
            val invocation = identity.value + ":repository-closure:" + pending.revision.value
            val closure = GoalPhaseNativeRepositoryClosure.Input(identity.value, pending.revision.value.toString,
              plan, input().originalBase, input().completed, input().candidate.get, phaseReviewReference.get.identity, invocation)
            closureSourceRevision = Some(closure.sourceRevision)
            val child = _repository_selection_(host.repository.input, "goal-sbt-child-host-entry", invocation)
            prepare(call => call.evidenceC(NativeHostEvidence.Purpose.PhaseClosure,
              ThreeWorkflowHostEvidencePorts.key(closure.instance, closure.sourceRevision), ContextReference("specification-sbt-phase-authority", "1"),
              "participant", take(ThreeWorkflowHostEvidencePorts.phaseClosureBindingC(call.core, closure)))
              .flatMap(_ => _prepare_input_(call, host.repository.input, child, root)))
            When("owning advance executes the installed RepositorySync Operations and their actual SBT check")
            val outcome = if (lostAcknowledgement) {
              Given("the Goal observer will lose only the first acknowledgement after the actual native completion append")
              When("the public close commits completion but its post-append acknowledgement fails")
              val interrupted = owner.executeOperationResponseInContext(Request.of(component = component.name,
                service = "GoalPhaseService", operation = "advanceGoalPhase", properties = advance), actor)
              Then("the public call reports failure while the actual Goal is already Completed after three product checks")
              interrupted.isSuccess shouldBe false
              val committed = current()
              committed.lifecycle shouldBe Lifecycle.Completed
              committed.currentProgression shouldBe None
              completionAcknowledgements shouldBe 1
              executions().size shouldBe 3
              val committedExecutions = executions()
              When("a normal public retry consumes the owning committed completion in the same runtime")
              val retry = invoke[V.GoalPhaseAdvanceResult]("advanceGoalPhase", advance)
              Then("the actual typed Terminal has no second append, acknowledgement or fourth product check")
              retry.kind shouldBe "Terminal"
              _same_native(current(), committed)
              completionAcknowledgements shouldBe 1
              executions() shouldBe committedExecutions
              retry
            } else invoke[V.GoalPhaseAdvanceResult]("advanceGoalPhase", advance)
            outcome.kind shouldBe "Terminal"
            val actual = outcome.completion.get
            var childsession = Option.empty[InstanceIdentity]
            prepare(call => call.goalChildSessionC(identity, closure.sourceRevision).map(value => {
              childsession = Some(InstanceIdentity(value)); ()
            }))
            var observed = Option.empty[RepositorySyncManagedGitObservation.Observed]
            var checked = Option.empty[RepositorySyncNativeMergeValidation.State]
            var childCurrent = Option.empty[InstanceRecord]
            prepare(call => call.observeC().flatMap(value => { observed = Some(value)
              call.validationC(host.repository.input, childsession.get, root).flatMap(value => { checked = Some(value)
                call.currentC(childsession.get).map(value => { childCurrent = Some(value); () }) }) }))
            Then("the whole selected Phase retains its actual Step commit, converged repository and third real test receipt")
            actual.selectedPlan.identity shouldBe plan.identity
            actual.selectedPlan.steps.map(_.identity) shouldBe plan.steps.map(_.identity)
            actual.originalBase.head shouldBe base.get.head
            actual.originalBase.tree shouldBe base.get.tree
            actual.completed.size shouldBe 1
            actual.completed.head.commit.parent shouldBe base.get.head
            actual.completed.head.commit.identity should not be base.get.head
            actual.repository.tip shouldBe actual.reviewedCandidate.head
            observed.get.head shouldBe actual.repository.tip
            observed.get.trackingTip shouldBe Some(actual.repository.tip)
            observed.get.dirty shouldBe empty
            checked.get.receipts.last.exitCode shouldBe Some(0)
            checked.get.receipts.last.stdout should include("PRODUCT_CHECK_PASSED")
            executions().size shouldBe 3
            executions().distinct.size shouldBe 3
            executions() should not contain ProcessHandle.current().pid()
            val completed = current()
            completed.lifecycle shouldBe Lifecycle.Completed
            childCurrent.get.lifecycle shouldBe Lifecycle.Completed
            retainedGoal = Some(outcome)
            retainedGoalNative = Some(completed)
            retainedChild = childsession
            retainedChildNative = childCurrent
            retainedChildValidation = checked
            retainedObservation = observed
            Given("the actual completed Goal, child convergence, Step commit and three check receipts in this runtime")
            val completedExecutions = executions()
            for (_ <- 1 to 2) {
              When("public Goal Advance rereads the selected completed result in the same runtime")
              val repeated = invoke[V.GoalPhaseAdvanceResult]("advanceGoalPhase", advance)
              var repeatedChild = Option.empty[InstanceRecord]
              var repeatedValidation = Option.empty[RepositorySyncNativeMergeValidation.State]
              var repeatedGit = Option.empty[RepositorySyncManagedGitObservation.Observed]
              prepare(call => for {
                child <- call.currentC(childsession.get)
                validation <- call.validationC(host.repository.input, childsession.get, root)
                git <- call.observeC()
              } yield { repeatedChild = Some(child); repeatedValidation = Some(validation); repeatedGit = Some(git) })
              Then("the identical typed completion and native frontiers retain the original Step commit, tips and receipts without another effect")
              repeated shouldBe outcome
              repeated.completion.get.completed.head.commit shouldBe actual.completed.head.commit
              _same_native(current(), completed)
              _same_native(repeatedChild.get, childCurrent.get)
              repeatedValidation shouldBe checked
              repeatedGit shouldBe observed
              executions() shouldBe completedExecutions
              executions().size shouldBe 3
              completionAcknowledgements shouldBe (if (lostAcknowledgement) 1 else 0)
            }
          } else {
            When("a freshly opened runtime supplies the assigned task for its semantic result")
            next()
            val selected = input()
            selected.selectedPlan shouldBe plan
            selected.originalBase shouldBe base.get
            if (session == "implement") {
              Given("the specification implements only the assigned product input, without supplying test receipts")
              Files.writeString(root.resolve("repository/value.txt"), "expected\n")
              val completion = result("completed")
              When("public completion records implementation and owning advance runs the selected Step SBT test")
              invoke[V.GoalPhaseWorkSubmissionResult]("submitGoalPhaseWorkResult", take(NativeWorkflowOperationInputFixture.submissionC(completion))).accepted shouldBe true
              next()
              Then("independent Step review contains the actual native-cwd SBT output and exit")
              input().kind shouldBe "ReviewStep"
              input().review.get.changedPaths shouldBe Vector("value.txt")
              input().review.get.checks.last.exitCode shouldBe Some(0)
              input().review.get.checks.last.stdout should include("PRODUCT_CHECK_ENTERED " + root.resolve("repository"))
              input().review.get.checks.last.stdout should include("PRODUCT_CHECK_PASSED")
              executions().size shouldBe 1
            } else {
              selected.kind shouldBe (if (session == "step-review") "ReviewStep" else "ReviewPhase")
              executions().size shouldBe (if (session == "step-review") 1 else 2)
              val completion = result("accept")
              prepare(call => ThreeWorkflowHostEvidencePorts.goalReviewBindingC(call.core, selected, completion)
                .flatMap(binding => call.evidenceC(NativeHostEvidence.Purpose.GoalReview,
                  ThreeWorkflowHostEvidencePorts.key(completion.resultReference.identity, completion.resultReference.revision),
                  completion.resultReference, "specification-reviewer", binding)))
              When("public completion accepts only the assigned, separately evidenced review")
              invoke[V.GoalPhaseWorkSubmissionResult]("submitGoalPhaseWorkResult", take(NativeWorkflowOperationInputFixture.submissionC(completion))).accepted shouldBe true
              if (session == "step-review") {
                val pending = current()
                val closure = GoalPhaseNativeClosure.Input(identity.value, pending.revision.value.toString, plan.identity, plan.revision,
                  selected.stepIdentity.get, selected.generation, selected.originalBase, selected.candidate.get, completion.resultReference.identity)
                prepare(call => call.evidenceC(NativeHostEvidence.Purpose.StepClosure,
                  ThreeWorkflowHostEvidencePorts.key(closure.instance, closure.sourceRevision), ContextReference("specification-sbt-step-authority", "1"),
                  "participant", take(ThreeWorkflowHostEvidencePorts.stepClosureBindingC(call.core, closure))))
                When("owning advance commits the actual Step and runs the whole-Phase SBT test")
                next()
                Then("whole-Phase review contains a real second check and the retained original allowance")
                input().kind shouldBe "ReviewPhase"
                input().completed.size shouldBe 1
                input().selectedPlan.repairLimit shouldBe 1
                input().review.get.checks.last.exitCode shouldBe Some(0)
                input().review.get.checks.last.stdout should include("PRODUCT_CHECK_PASSED")
                executions().size shouldBe 2
              } else {
                phaseReviewReference = Some(completion.resultReference)
              }
            }
          }
        }
      Given("the completed Goal and its actual RepositorySync child have closed their runtimes with three real ProductCheck results")
      val originalExecutions = executions()
      _with_runtime_(root, "Goal", Some(host)) { (owner, component, actor, prepare) =>
        When("fresh public Goal and child Repository Advances observe the retained whole-Phase completion")
        val reopenedGoal = read_value[V.GoalPhaseAdvanceResult](take(owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "GoalPhaseService", operation = "advanceGoalPhase",
            properties = advance), actor)))
        var childsession = Option.empty[InstanceIdentity]
        prepare(call => call.goalChildSessionC(identity, closureSourceRevision.get).map(value => {
          childsession = Some(InstanceIdentity(value)); ()
        }))
        val reopenedChild = read_value[V.RepositorySyncAdvanceResult](take(owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "RepositorySyncService", operation = "advanceRepositorySync",
            properties = NativeWorkflowOperationInputFixture.advance(childsession.get.value)), actor)))
        var goalCurrent = Option.empty[InstanceRecord]
        var childCurrent = Option.empty[InstanceRecord]
        var checked = Option.empty[RepositorySyncNativeMergeValidation.State]
        var observed = Option.empty[RepositorySyncManagedGitObservation.Observed]
        prepare(call => for {
          goal <- call.goalCurrentC(identity)
          child <- call.currentC(childsession.get)
          validation <- call.validationC(host.repository.input, childsession.get, root)
          git <- call.observeC()
        } yield { goalCurrent = Some(goal); childCurrent = Some(child); checked = Some(validation); observed = Some(git) })
        Then("the same whole-Phase result, child convergence and receipt, native revisions and histories survive without a fourth check")
        reopenedGoal shouldBe retainedGoal.get
        reopenedGoal.kind shouldBe "Terminal"
        reopenedChild.kind shouldBe "Terminal"
        childsession shouldBe retainedChild
        reopenedChild.convergence shouldBe retainedGoal.get.completion.map(_.repository)
        goalCurrent.get.lifecycle shouldBe Lifecycle.Completed
        childCurrent.get.lifecycle shouldBe Lifecycle.Completed
        _same_native(goalCurrent.get, retainedGoalNative.get)
        _same_native(childCurrent.get, retainedChildNative.get)
        checked shouldBe retainedChildValidation
        observed shouldBe retainedObservation
        executions() shouldBe originalExecutions
        executions().size shouldBe 3
        executions().distinct.size shouldBe 3
        executions() should not contain ProcessHandle.current().pid()
        completionAcknowledgements shouldBe (if (lostAcknowledgement) 1 else 0)
      }
    }
    }
  }
}
