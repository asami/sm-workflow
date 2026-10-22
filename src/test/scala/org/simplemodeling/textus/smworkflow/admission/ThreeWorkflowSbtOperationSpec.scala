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
 * @version Oct. 23, 2026
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
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val compile = Set("NormalSbtCompileSuccess", "NormalSbtCompileFailure")(scenario)
    val isolated = compile || Set("NormalSbtSuccess", "NormalSbtFailure", "NormalSbtUnknown")(scenario)
    require(isolated || Set("Equal", "Goal")(scenario), "Only the selected normal SBT episodes are admitted")
    val gitplan = if (isolated) None else Some(TypedGitHostPlanFixture.plan(
      Target(root.resolve("repository"), "main", "origin", "main", root.resolve("upstream.git").toString),
      goalPaths = if (scenario == "Goal") goal.candidatePaths else Vector.empty,
      goalMessages = if (scenario == "Goal") goal.closure.programs.map(p => "Goal Step " + p.step) else Vector.empty,
      goalSelections = if (scenario == "Goal") Vector(goal.candidatePaths) else Vector.empty))
    val ordinals = if (isolated || scenario != "Goal") Vector(1) else
      (goal.stepValidation.checks ++ goal.phaseValidation.checks).map(_.ordinal).distinct
    TypedSbtHostPlanFixture.plan(root, _sbt_executable, ordinals, gitplan,
      operation = if (compile) SbtOperation.Compile else SbtOperation.Test)
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

    for (successful <- Vector(true, false)) {
      (if (successful) "persist successful Compile source generation and compiled classes without replay after reopening"
        else "persist failed Compile source generation and its real compiler failure without replay after reopening") in {
        Given("an isolated normal Compile build with a configured source generator and no application main")
        val directory = Files.createDirectories(Path.of("target/sm-workflow/work/normal-typed-sbt").toAbsolutePath)
        val root = Files.createTempDirectory(directory, "compile-episode-")
        TypedSbtHostPlanFixture.buildCompile(root, successful)
        val scenario = if (successful) "NormalSbtCompileSuccess" else "NormalSbtCompileFailure"
        val intent = Json.obj("target" -> Json.fromString("sm-workflow-repository-validation-1"),
          "operation" -> Json.fromString("compile"), "checkIdentity" -> Json.fromString("compile"), "state" -> Json.fromString("prepared"))
        val marker = root.resolve("compile-generations.txt")
        var stored = Option.empty[Json]
        var generation = Option.empty[String]
        _with_runtime_(root, scenario) { (_, _, _, prepare) =>
          When("a completed managed Setup Operation persists the Compile intent before any build effect")
          prepare(call => call.createC("host_evidence", "normal-sbt-intent", Record.data(_storage_field -> intent.noSpaces)))
          Then("the intent exists without entering the configured source generator")
          prepare(call => _stored_c(call, "normal-sbt-intent").map(value => { value shouldBe Some(intent); () }))
          Files.exists(marker) shouldBe false

          Given("that durable Compile intent and a fresh owning managed call")
          When("the call loads the intent, executes typed Compile once and retains its actual receipt")
          prepare(call => for {
            retained <- _stored_c(call, "normal-sbt-intent")
            _ = retained shouldBe Some(intent)
            actual <- WorkflowManagedProcessExecution.repositoryValidationC(call.core, 1, WorkflowSbtValidation.Kind.Compile)
            _ = actual.operation shouldBe SbtOperation.Compile
            receipt <- RepositorySyncNativeMergeValidation.sbtReceiptC("compile", WorkflowSbtValidation.Kind.Compile, actual)
            _ = actual.outcome shouldBe (if (successful) SbtExecutionOutcome.Succeeded else SbtExecutionOutcome.Failed(receipt.exitCode.get))
            evidence = _result(actual, receipt)
            _ <- call.createC("host_evidence", "normal-sbt-result", Record.data(_storage_field -> evidence.noSpaces))
            _ = { stored = Some(evidence) }
          } yield ())
          Then("the external compiler result, bounded captures and Compile-only product gate describe the real generated source")
          val value = stored.get
          value.hcursor.get[String]("target").toOption shouldBe Some("sm-workflow-repository-validation-1")
          value.hcursor.get[String]("operation").toOption shouldBe Some("compile")
          value.hcursor.get[String]("safeProgramIdentity").toOption shouldBe Some("sm-workflow-repository-validation-1")
          val receipt = take(value.hcursor.downField("receipt").as[RepositorySyncNativeMergeValidation.Receipt].fold(
            _ => Consequence.stateConflict("Actual Compile receipt cannot be decoded"), Consequence.success))
          receipt.identity shouldBe "compile"
          receipt.termination shouldBe "exited"
          value.hcursor.get[String]("termination").toOption shouldBe Some(ProcessExecutionTermination.Exited(receipt.exitCode.get).toString)
          value.hcursor.get[String]("outcome").toOption shouldBe Some(
            (if (successful) SbtExecutionOutcome.Succeeded else SbtExecutionOutcome.Failed(receipt.exitCode.get)).toString)
          value.hcursor.get[Long]("elapsedMillis").toOption.get should be >= 0L
          value.hcursor.downField("artifacts").focus.get shouldBe Json.arr()
          val facts = receipt.sbt.get
          facts.kind shouldBe WorkflowSbtValidation.Kind.Compile
          facts.outcome shouldBe (if (successful) WorkflowSbtValidation.Outcome.Succeeded else WorkflowSbtValidation.Outcome.Failed)
          facts.selection shouldBe None
          facts.reason shouldBe None
          RepositorySyncNativeMergeValidation.productSuccessful(receipt, WorkflowSbtValidation.Kind.Compile) shouldBe successful
          RepositorySyncNativeMergeValidation.productSuccessful(receipt, WorkflowSbtValidation.Kind.Test) shouldBe false
          RepositorySyncNativeMergeValidation.productSuccessful(receipt, WorkflowSbtValidation.Kind.TestOnly) shouldBe false
          receipt.stdout should include("COMPILE_GENERATOR_ENTERED " + root.resolve("repository"))
          val generated = root.resolve("repository/target/selected-generated/CompileGenerated.scala")
          Files.readString(generated) shouldBe
            ("object CompileGenerated { val value: Int = " + (if (successful) "42" else "\"incorrect\"") + " }\n")
          val classes = root.resolve("repository/target/scala-2.12/classes")
          if (successful) {
            receipt.exitCode shouldBe Some(0)
            Files.isRegularFile(classes.resolve("CompileGenerated$.class")) shouldBe true
            Files.isRegularFile(classes.resolve("CompileConsumer$.class")) shouldBe true
          } else {
            receipt.exitCode.get should not be 0
            (receipt.stdout + receipt.stderr) should include("type mismatch")
            Files.exists(classes.resolve("CompileConsumer$.class")) shouldBe false
          }
          for (stream <- Vector("stdout", "stderr")) {
            val capture = value.hcursor.downField(stream)
            capture.get[Boolean]("truncated").toOption shouldBe Some(false)
            capture.get[Long]("byteCount").toOption.get shouldBe capture.get[Vector[Int]]("bytes").toOption.get.size.toLong
            capture.get[Long]("byteCount").toOption.get should be <= (if (stream == "stdout") 131072L else 16384L)
          }
          generation = Some(Files.readString(marker))
          generation.get.linesIterator.size shouldBe 1
          generation.get.trim.toLong should not be ProcessHandle.current().pid()
        }
        Given("the original runtime is closed with its exact Compile intent and actual result retained")
        _with_runtime_(root, scenario) { (_, _, _, prepare) =>
          When("a reopened runtime reads the durable intent and result through fresh managed calls")
          var reread = Option.empty[Json]
          var retainedintent = Option.empty[Json]
          prepare(call => _stored_c(call, "normal-sbt-intent").map(value => { retainedintent = value; () }))
          prepare(call => _retained_c(call).map(value => { reread = Some(value); () }))
          Then("both records are identical and the configured source generator still has exactly one external PID entry")
          retainedintent shouldBe Some(intent)
          reread shouldBe stored
          Files.readString(marker) shouldBe generation.get
          Files.readString(marker).linesIterator.size shouldBe 1
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
        var retainedresult = Option.empty[V.RepositorySyncAdvanceResult]
        var retainednative = Option.empty[InstanceRecord]
        var retainedvalidation = Option.empty[RepositorySyncNativeMergeValidation.State]
        var retainedobservation = Option.empty[RepositorySyncManagedGitObservation.Observed]
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
          retainedresult = Some(result)
          retainednative = current
          retainedvalidation = validation
          retainedobservation = observed
        }
        Given("the original repository runtime is closed with its actual selected check result and native history retained")
        val originalexecution = Files.readString(root.resolve("sbt-executions.txt"))
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
          reopened shouldBe retainedresult.get
          reopened.kind shouldBe (if (successful) "Terminal" else "Wait")
          current.get.lifecycle shouldBe (if (successful) Lifecycle.Completed else Lifecycle.Active)
          _same_native(current.get, retainednative.get)
          validation shouldBe retainedvalidation
          observed shouldBe retainedobservation
          Files.readString(root.resolve("sbt-executions.txt")) shouldBe originalexecution
          originalexecution.linesIterator.size shouldBe 1
        }
      }
    }
  }
  "Direct GoalPhase Operations with the runtime SBT adapter" should afterWord(
    "in spec:normal-typed-sbt-validation, example:wholeGoal, rule:owning-workflow; spec:goal-phase-native-retention-applicability, example:E16, rule:R6") {
    "repair a real failed SBT check through assigned semantic work and fresh runtime validation" in {
      Given("an isolated clean one-Step Goal with only value.txt owned and a normal typed Test host")
      val directory = Files.createDirectories(Path.of("target/sm-workflow/work/direct-repository-operation").toAbsolutePath)
      val root = Files.createTempDirectory(directory, "sbt-goal-check-repair-")
      _build(root, true)
      Files.writeString(root.resolve("repository/value.txt"), "initial\n")
      val original = ThreeWorkflowNormalHostFixture.definition(root)
      val host = original.copy(goal = original.goal.copy(candidatePaths = Vector("value.txt")))
      val plan = O.Plan("selected-sbt-check-repair-plan", "1", host.goal.input.phaseIdentity,
        host.goal.input.repositoryIdentity, Vector(O.Step("step-1", "Correct selected product input",
          Vector("value.txt"), Vector.empty, Vector("test"))), Vector("test"), repairLimit = 1)
      val selection = _goal_selection_(host.goal.input, "selected-sbt-check-repair", "direct-sbt-check-repair-start")
      var execution = Option.empty[InstanceIdentity]
      var assigned = Option.empty[WorkflowInteraction[S.Request, Nothing]]
      var implementation = Option.empty[S.Request]
      var base = Option.empty[O.Snapshot]
      var failedwork = Option.empty[WorkflowInteraction[S.Request, Nothing]]
      var failedresponse = Option.empty[V.GoalPhaseAdvanceResult]
      var failure = Option.empty[S.CheckFailureContext]
      var faileddiagnostics = Option.empty[(String, String)]
      var reviewwork = Option.empty[WorkflowInteraction[S.Request, Nothing]]
      var reviewresponse = Option.empty[V.GoalPhaseAdvanceResult]
      def _identity_ : InstanceIdentity = execution.get
      def _advance_ : List[Property] = NativeWorkflowOperationInputFixture.advance(_identity_.value)
      def _input_(): S.Request = assigned.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value
      def _executions_(): Vector[Long] = if (Files.exists(root.resolve("sbt-executions.txt")))
        Files.readString(root.resolve("sbt-executions.txt")).linesIterator.map(_.toLong).toVector else Vector.empty
      // This deterministic participant supplies semantic edits only; it is not provider evidence.
      def _result_(): ContinuationResult[S.Result] = {
        val work = assigned.get
        val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request
        val selected = order.input.get.value
        val reference = ContextReference("specification-sbt-check-repair-" + selected.kind, selected.sourceRevision)
        ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision, order.context.snapshot,
          TypedValue(S.resultCodec.typeIdentity, S.Result("completed", "Specification response to assigned semantic work only", Vector.empty)),
          reference, Vector.empty, ExecutionEvidence(Vector(reference), Some("participant")))
      }
      for (session <- Vector("issue", "implement", "repair", "review"))
        _with_runtime_(root, "Goal", Some(host)) { (owner, component, actor, prepare) =>
          def _invoke_[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A = read_value[A](take(owner.executeOperationResponseInContext(
            Request.of(component = component.name, service = "GoalPhaseService", operation = operation,
              properties = properties), actor)))
          def _next_(): V.GoalPhaseAdvanceResult = {
            val response = _invoke_[V.GoalPhaseAdvanceResult]("advanceGoalPhase", _advance_)
            response.kind shouldBe "Request"
            response.sessionId shouldBe _identity_.value
            prepare(call => call.goalWorkC(_identity_).map(value => { assigned = Some(value); () }))
            response.work.get.kind shouldBe _input_().kind
            _input_().selectedPlan shouldBe plan
            response.work.get.selectedPlan.identity shouldBe plan.identity
            response
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
            When("ordinary public Start and Advance issue the selected implementation")
            val started = _invoke_[V.GoalPhaseStartResult]("startGoalPhase", NativeWorkflowOperationInputFixture.goal(host.goal.input))
            execution = Some(InstanceIdentity(started.sessionId))
            _next_()
            Then("the actual assigned implementation retains its plan and clean base without running SBT")
            _input_().kind shouldBe "ImplementStep"
            _input_().stepIdentity shouldBe Some("step-1")
            _input_().completed shouldBe empty
            implementation = Some(_input_())
            base = Some(_input_().originalBase)
            _executions_() shouldBe empty
          } else if (session == "implement") {
            Given("the original runtime is closed and the actual pending implementation is reopened")
            _next_()
            _input_() shouldBe implementation.get
            When("the semantic fixture writes incorrect input and submits only its assigned completed result")
            Files.writeString(root.resolve("repository/value.txt"), "incorrect\n")
            _invoke_[V.GoalPhaseWorkSubmissionResult]("submitGoalPhaseWorkResult", take(NativeWorkflowOperationInputFixture.submissionC(_result_()))).accepted shouldBe true
            Then("Submit accepts semantic completion without launching a product check")
            _executions_() shouldBe empty
            When("public Advance executes the selected real Test and assigns RepairStep for its failure")
            val response = _next_()
            Then("internal and public repair work retain the actual failed candidate and typed diagnostics")
            val selected = _input_()
            selected.kind shouldBe "RepairStep"
            selected.stepIdentity shouldBe implementation.get.stepIdentity
            selected.originalBase shouldBe base.get
            selected.completed shouldBe implementation.get.completed
            selected.generation shouldBe implementation.get.generation + 1L
            selected.review shouldBe None
            selected.candidate shouldBe None
            selected.checksReceipt shouldBe None
            val context = selected.checkFailure.get
            val validation = context.validation
            context.changedPaths shouldBe Vector("value.txt")
            context.diff should include("+incorrect")
            validation.candidate.head shouldBe base.get.head
            validation.candidate.tree should not be base.get.tree
            validation.step shouldBe "step-1"
            validation.outcome shouldBe Some("repair-required")
            validation.selected.map(c => (c.identity, c.kind)) shouldBe Vector(("test", WorkflowSbtValidation.Kind.Test))
            validation.receipts.size shouldBe 1
            val receipt = validation.receipts.head
            receipt.identity shouldBe "test"
            receipt.termination shouldBe "exited"
            receipt.exitCode.get should not be 0
            receipt.sbt.get.kind shouldBe WorkflowSbtValidation.Kind.Test
            receipt.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Failed
            receipt.stdout should include("PRODUCT_CHECK_ENTERED " + root.resolve("repository"))
            (receipt.stdout + receipt.stderr) should include("actual product input differs")
            receipt.stdout should not include "PRODUCT_CHECK_PASSED"
            selected.repairDirective.get.reason should include("test")
            selected.repairDirective.get.reason should include(receipt.exitCode.get.toString)
            val publicwork = response.work.get
            publicwork.review shouldBe None
            publicwork.candidate shouldBe None
            publicwork.checksReceipt shouldBe None
            publicwork.stepIdentity shouldBe selected.stepIdentity
            publicwork.completed shouldBe empty
            publicwork.checkFailure.get.changedPaths shouldBe context.changedPaths
            publicwork.checkFailure.get.diff shouldBe context.diff
            publicwork.checkFailure.get.validation shouldBe NativeWorkflowOperationOutput.goalValidation(validation)
            _executions_().size shouldBe 1
            _executions_() should not contain ProcessHandle.current().pid()
            failedwork = assigned
            failedresponse = Some(response)
            failure = Some(context)
            faileddiagnostics = Some((receipt.stdout, receipt.stderr))
          } else if (session == "repair") {
            Given("the failed runtime is closed with its full issued RepairStep and raw failure context saved")
            When("a fresh public Advance rereads the pending repair before any correction")
            val reopened = _next_()
            Then("the same repair request and handle retain the original diagnostics without another SBT launch")
            reopened shouldBe failedresponse.get
            assigned shouldBe failedwork
            _input_().originalBase shouldBe base.get
            _input_().checkFailure shouldBe failure
            val receipt = _input_().checkFailure.get.validation.receipts.head
            (receipt.stdout, receipt.stderr) shouldBe faileddiagnostics.get
            _executions_().size shouldBe 1
            When("the assigned semantic fixture corrects value.txt and submits completed without receipts or review evidence")
            Files.writeString(root.resolve("repository/value.txt"), "expected\n")
            _invoke_[V.GoalPhaseWorkSubmissionResult]("submitGoalPhaseWorkResult", take(NativeWorkflowOperationInputFixture.submissionC(_result_()))).accepted shouldBe true
            Then("repair Submit records semantic work without executing another check")
            _executions_().size shouldBe 1
            When("public Advance freshly inspects and stages the corrected candidate and executes the real selected Test")
            val response = _next_()
            Then("independent ReviewStep receives a fresh successful receipt while the consumed repair allowance and failed evidence remain retained")
            val selected = _input_()
            selected.kind shouldBe "ReviewStep"
            selected.checkFailure shouldBe None
            response.work.get.checkFailure shouldBe None
            selected.review.get.changedPaths shouldBe Vector("value.txt")
            selected.review.get.diff should include("+expected")
            val positive = selected.review.get.checks.last
            positive.identity shouldBe "test"
            positive.termination shouldBe "exited"
            positive.exitCode shouldBe Some(0)
            positive.sbt.get.kind shouldBe WorkflowSbtValidation.Kind.Test
            positive.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded
            positive.stdout should include("PRODUCT_CHECK_ENTERED " + root.resolve("repository"))
            positive.stdout should include("PRODUCT_CHECK_PASSED")
            selected.candidate.get.head shouldBe base.get.head
            selected.candidate.get.tree should not be failure.get.validation.candidate.tree
            selected.originalBase shouldBe base.get
            selected.generation shouldBe failedwork.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value.generation
            selected.completed shouldBe empty
            selected.selectedPlan.repairLimit shouldBe 1
            response.work.get.review.get.changedPaths shouldBe Vector("value.txt")
            (failure.get.validation.receipts.head.stdout, failure.get.validation.receipts.head.stderr) shouldBe faileddiagnostics.get
            failure.get.validation.outcome shouldBe Some("repair-required")
            _executions_().size shouldBe 2
            _executions_().distinct.size shouldBe 2
            _executions_() should not contain ProcessHandle.current().pid()
            reviewwork = assigned
            reviewresponse = Some(response)
          } else {
            Given("the corrected runtime is closed with independent ReviewStep still pending")
            When("a fresh public Advance and native issued-work read reopen that ReviewStep")
            val reopened = _next_()
            Then("the identical review request and handle retain the positive receipt in active phase.Review without a third launch")
            reopened shouldBe reviewresponse.get
            assigned shouldBe reviewwork
            _input_().kind shouldBe "ReviewStep"
            _input_().review.get.checks.last.exitCode shouldBe Some(0)
            _input_().review.get.checks.last.stdout should include("PRODUCT_CHECK_PASSED")
            prepare(call => call.goalCurrentC(_identity_).map { current =>
              current.currentProgression shouldBe Some(ProgressionReference("phase.Review"))
              current.lifecycle shouldBe Lifecycle.Active
              current.suspension.nonEmpty shouldBe true
              ()
            })
            _executions_().size shouldBe 2
            _executions_().distinct.size shouldBe 2
            _executions_() should not contain ProcessHandle.current().pid()
          }
        }
    }
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
      var retainedgoal = Option.empty[V.GoalPhaseAdvanceResult]
      var retainedgoalnative = Option.empty[InstanceRecord]
      var retainedchild = Option.empty[InstanceIdentity]
      var retainedchildnative = Option.empty[InstanceRecord]
      var retainedchildvalidation = Option.empty[RepositorySyncNativeMergeValidation.State]
      var retainedobservation = Option.empty[RepositorySyncManagedGitObservation.Observed]
      var closuresourcerevision = Option.empty[String]
      var completionacknowledgements = 0
      val acknowledge: String => Consequence[Unit] = point => {
        if (point == "goal-repository-completed") {
          completionacknowledgements += 1
          if (completionacknowledgements == 1) Consequence.stateConflict("Specification lost post-append Goal acknowledgement")
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
            closuresourcerevision = Some(closure.sourceRevision)
            val child = _repository_selection_(host.repository.input, "goal-sbt-child-host-entry", invocation)
            prepare(call => call.evidenceC(NativeExternalEvidence.Purpose.PhaseClosure,
              ThreeWorkflowExternalEvidencePorts.key(closure.instance, closure.sourceRevision), ContextReference("specification-sbt-phase-authority", "1"),
              "participant", take(ThreeWorkflowExternalEvidencePorts.phaseClosureBindingC(call.core, closure)))
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
              completionacknowledgements shouldBe 1
              executions().size shouldBe 3
              val committedexecutions = executions()
              When("a normal public retry consumes the owning committed completion in the same runtime")
              val retry = invoke[V.GoalPhaseAdvanceResult]("advanceGoalPhase", advance)
              Then("the actual typed Terminal has no second append, acknowledgement or fourth product check")
              retry.kind shouldBe "Terminal"
              _same_native(current(), committed)
              completionacknowledgements shouldBe 1
              executions() shouldBe committedexecutions
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
            var childcurrent = Option.empty[InstanceRecord]
            prepare(call => call.observeC().flatMap(value => { observed = Some(value)
              call.validationC(host.repository.input, childsession.get, root).flatMap(value => { checked = Some(value)
                call.currentC(childsession.get).map(value => { childcurrent = Some(value); () }) }) }))
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
            childcurrent.get.lifecycle shouldBe Lifecycle.Completed
            retainedgoal = Some(outcome)
            retainedgoalnative = Some(completed)
            retainedchild = childsession
            retainedchildnative = childcurrent
            retainedchildvalidation = checked
            retainedobservation = observed
            Given("the actual completed Goal, child convergence, Step commit and three check receipts in this runtime")
            val completedexecutions = executions()
            for (_ <- 1 to 2) {
              When("public Goal Advance rereads the selected completed result in the same runtime")
              val repeated = invoke[V.GoalPhaseAdvanceResult]("advanceGoalPhase", advance)
              var repeatedchild = Option.empty[InstanceRecord]
              var repeatedvalidation = Option.empty[RepositorySyncNativeMergeValidation.State]
              var repeatedgit = Option.empty[RepositorySyncManagedGitObservation.Observed]
              prepare(call => for {
                child <- call.currentC(childsession.get)
                validation <- call.validationC(host.repository.input, childsession.get, root)
                git <- call.observeC()
              } yield { repeatedchild = Some(child); repeatedvalidation = Some(validation); repeatedgit = Some(git) })
              Then("the identical typed completion and native frontiers retain the original Step commit, tips and receipts without another effect")
              repeated shouldBe outcome
              repeated.completion.get.completed.head.commit shouldBe actual.completed.head.commit
              _same_native(current(), completed)
              _same_native(repeatedchild.get, childcurrent.get)
              repeatedvalidation shouldBe checked
              repeatedgit shouldBe observed
              executions() shouldBe completedexecutions
              executions().size shouldBe 3
              completionacknowledgements shouldBe (if (lostAcknowledgement) 1 else 0)
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
              prepare(call => ThreeWorkflowExternalEvidencePorts.goalReviewBindingC(call.core, selected, completion)
                .flatMap(binding => call.evidenceC(NativeExternalEvidence.Purpose.GoalReview,
                  ThreeWorkflowExternalEvidencePorts.key(completion.resultReference.identity, completion.resultReference.revision),
                  completion.resultReference, "specification-reviewer", binding)))
              When("public completion accepts only the assigned, separately evidenced review")
              invoke[V.GoalPhaseWorkSubmissionResult]("submitGoalPhaseWorkResult", take(NativeWorkflowOperationInputFixture.submissionC(completion))).accepted shouldBe true
              if (session == "step-review") {
                val pending = current()
                val closure = GoalPhaseNativeClosure.Input(identity.value, pending.revision.value.toString, plan.identity, plan.revision,
                  selected.stepIdentity.get, selected.generation, selected.originalBase, selected.candidate.get, completion.resultReference.identity)
                prepare(call => call.evidenceC(NativeExternalEvidence.Purpose.StepClosure,
                  ThreeWorkflowExternalEvidencePorts.key(closure.instance, closure.sourceRevision), ContextReference("specification-sbt-step-authority", "1"),
                  "participant", take(ThreeWorkflowExternalEvidencePorts.stepClosureBindingC(call.core, closure))))
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
      val originalexecutions = executions()
      _with_runtime_(root, "Goal", Some(host)) { (owner, component, actor, prepare) =>
        When("fresh public Goal and child Repository Advances observe the retained whole-Phase completion")
        val reopenedgoal = read_value[V.GoalPhaseAdvanceResult](take(owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "GoalPhaseService", operation = "advanceGoalPhase",
            properties = advance), actor)))
        var childsession = Option.empty[InstanceIdentity]
        prepare(call => call.goalChildSessionC(identity, closuresourcerevision.get).map(value => {
          childsession = Some(InstanceIdentity(value)); ()
        }))
        val reopenedchild = read_value[V.RepositorySyncAdvanceResult](take(owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "RepositorySyncService", operation = "advanceRepositorySync",
            properties = NativeWorkflowOperationInputFixture.advance(childsession.get.value)), actor)))
        var goalcurrent = Option.empty[InstanceRecord]
        var childcurrent = Option.empty[InstanceRecord]
        var checked = Option.empty[RepositorySyncNativeMergeValidation.State]
        var observed = Option.empty[RepositorySyncManagedGitObservation.Observed]
        prepare(call => for {
          goal <- call.goalCurrentC(identity)
          child <- call.currentC(childsession.get)
          validation <- call.validationC(host.repository.input, childsession.get, root)
          git <- call.observeC()
        } yield { goalcurrent = Some(goal); childcurrent = Some(child); checked = Some(validation); observed = Some(git) })
        Then("the same whole-Phase result, child convergence and receipt, native revisions and histories survive without a fourth check")
        reopenedgoal shouldBe retainedgoal.get
        reopenedgoal.kind shouldBe "Terminal"
        reopenedchild.kind shouldBe "Terminal"
        childsession shouldBe retainedchild
        reopenedchild.convergence shouldBe retainedgoal.get.completion.map(_.repository)
        goalcurrent.get.lifecycle shouldBe Lifecycle.Completed
        childcurrent.get.lifecycle shouldBe Lifecycle.Completed
        _same_native(goalcurrent.get, retainedgoalnative.get)
        _same_native(childcurrent.get, retainedchildnative.get)
        checked shouldBe retainedchildvalidation
        observed shouldBe retainedobservation
        executions() shouldBe originalexecutions
        executions().size shouldBe 3
        executions().distinct.size shouldBe 3
        executions() should not contain ProcessHandle.current().pid()
        completionacknowledgements shouldBe (if (lostAcknowledgement) 1 else 0)
      }
    }
    }
  }
}
