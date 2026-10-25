package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.file.{Files, Path, StandardOpenOption}
import java.nio.charset.StandardCharsets.UTF_8
import java.sql.DriverManager
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import org.goldenport.convert.ValueReader
import org.goldenport.record.RecordPresentable
import org.goldenport.record.io.RecordDecoder
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms.Target
import org.goldenport.cncf.processexecution.GitOperationKind
import org.goldenport.cncf.workflow.{WorkflowInstanceRecordJsonV1, WorkflowMechanicalReceiptLimits,
  WorkflowMechanicalTransition, WorkflowMechanicalTransitionJsonV1,
  WorkflowWorkOrderJsonV1}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowContinuation
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec
import GoalPhaseAcceptanceBoundary as B
import GoalPhaseOrderedProgression as O
import GoalPhaseValidationRepairAdoption as A
import GoalPhaseValidationPriorInvocation as P
import GoalPhaseNativeValidation as N
import GoalPhaseNativeClosure as C
import GoalPhaseNativeRun as S
import NativeWorkflowCliFixture as F
import SplitPhaseExplicitPartitionFixture.take

/** Staged fixture support lives in this owned suite. It never manufactures a
  * commandExecution source, native receipt or genuine Human authority.
  * @since Oct. 24, 2026
  * @version Oct. 25, 2026
  * @author ASAMI, Tomoharu
  */
private[admission] object NativeValidationRepairInstalledFixture extends Matchers {
  val suite = "org.simplemodeling.textus.smworkflow.admission.NativeGoalUnobservedPhaseRepairAdoptionCliSpec"
  val sourcePath = "src/test/scala/ProductCheck.scala"
  val outputName = "old-output-limit-advance"
  final case class State(root: String, taskRoot: String, sessionId: String, plan: O.Plan,
    oldExecutable: String, oldInstallations: String, callerThread: String, command: String)
  /** Exact observed bytes, not a manufactured workflow/controller receipt. */
  final case class Snapshot(immutable: Vector[String], current: String, launches: Vector[String], controller: Json)
  private lazy val _catalog = take(WorkflowInstanceRecordJsonV1.catalogC(Vector(take(GoalPhaseRuntimeCatalog.loadC()).definition)))
  private lazy val _limits = take(WorkflowMechanicalReceiptLimits.createC(16 * 1024 * 1024))
  final case class Invocation(exit: Int, stdout: Path, stderr: Path) {
    def business[A: ValueReader]: A = {
      withClue(Files.readString(stderr)) { exit shouldBe 0 }
      take(summon[ValueReader[A]].readC(take(new RecordDecoder().json(Files.readString(stdout)))))
    }
  }
  final class Running(private val _process: Process, private val _stdout: Path, private val _stderr: Path) {
    def await(): Invocation = try {
      require(_process.waitFor(300L, TimeUnit.SECONDS), "Installed fixture command did not terminate within its bound")
      Invocation(_process.exitValue(), _stdout, _stderr)
    } finally close()
    def close(): Unit = {
      if (_process.isAlive) {
        val stream = _process.toHandle.descendants()
        val children = try stream.iterator().asScala.toVector finally stream.close()
        children.filter(_.isAlive).foreach(_.destroy())
        _process.destroy()
        if (!_process.waitFor(10L, TimeUnit.SECONDS)) _process.destroyForcibly()
        children.filter(_.isAlive).foreach(_.destroyForcibly())
        require(_process.waitFor(10L, TimeUnit.SECONDS), "Fixture-owned process did not terminate")
      }
      _process.getInputStream.close()
      _process.getErrorStream.close()
    }
  }
  def required(name: String): String = sys.props.getOrElse(name, throw new IllegalArgumentException("Missing " + name))
  def path(name: String): Path = {
    val value = Path.of(required(name))
    require(value.isAbsolute && value.normalize == value, name + " requires an absolute normalized path")
    value.toRealPath()
  }
  def json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  def read(path: Path): Json = parse(Files.readString(path)).fold(e => throw new AssertionError(e.message), identity)
  def readBusiness[A: ValueReader](file: Path): A =
    take(summon[ValueReader[A]].readC(take(new RecordDecoder().json(Files.readString(file)))))
  def stateFile: Path = {
    val value = Path.of(required("smworkflow.validationRepair.state"))
    require(value.isAbsolute && value.normalize == value, "State output must be absolute and normalized")
    value
  }
  def load(): State = read(stateFile).as[State].fold(e => throw new AssertionError(e.message), identity)
  final class Cli(val root: Path, executable: Path, installations: Path) {
    private var _ordinal = 0
    private val _run = Files.createTempDirectory(Files.createDirectories(root.resolve("validation-repair-installed-logs")), "run-")
    def start(service: String, operation: String, arguments: Vector[String]): Running = {
      val index = _ordinal
      _ordinal += 1
      val stdout = _run.resolve(index + ".stdout.json")
      val stderr = _run.resolve(index + ".stderr.log")
      val builder = new ProcessBuilder((Vector(executable.toString, "--runtime-root", root.toString, "command",
        SmWorkflowComponent.componentId.name + "." + service + "." + operation) ++ arguments ++ Vector("--json")).asJava)
        .directory(root.toFile).redirectOutput(stdout.toFile).redirectError(stderr.toFile)
      builder.environment().put("SM_WORKFLOW_INSTALLATIONS", installations.toString)
      val process = builder.start()
      process.getOutputStream.close()
      new Running(process, stdout, stderr)
    }
    def call(service: String, operation: String, arguments: Vector[String]): Invocation = start(service, operation, arguments).await()
    def goal(operation: String, value: RecordPresentable): Invocation = call("GoalPhaseService", operation, Vector("--input", json(value)))
    def advance(session: String): Invocation = call("GoalPhaseService", "advanceGoalPhase", Vector("--sessionId", session))
    def prepare(operation: String, value: RecordPresentable): Invocation = call("NativeWorkflowPreparationService", operation, Vector("--input", json(value)))
    def file(name: String, value: String): String = {
      val file = _run.resolve(name + ".json")
      Files.writeString(file, value, StandardOpenOption.CREATE_NEW)
      file.toUri.toASCIIString
    }
    def resource(operation: String, name: String, value: RecordPresentable, minimumBytes: Long): Invocation = {
      val text = json(value)
      text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length.toLong should be > minimumBytes
      call("GoalPhaseService", operation, Vector("--inputResource", file(name, text)))
    }
    def importPrepared(value: Invocation): Unit = {
      value.exit shouldBe 0
      read(value.stdout).hcursor.downField("datastore").focus.get.asArray.get should not be empty
      val config = root.resolve(".textus/config.json")
      Files.writeString(config, read(config).mapObject(_.add("textus.import.data.file", value.stdout.toString.asJson)).noSpaces)
    }
    def submit(request: V.GoalPhaseAdvanceResult, result: V.GoalPhaseWorkResult, reference: V.WorkflowArtifactReference,
      execution: Option[V.WorkflowExecutionEvidence] = None): Invocation = call("GoalPhaseService", request.submitOperation.get,
      Vector("--sessionId", request.submitSessionId.get, "--result", json(result), "--resultReference", json(reference)) ++
        execution.toVector.flatMap(e => Vector("--executionEvidence", json(e))))
  }
  def result(plan: O.Plan, request: V.GoalPhaseAdvanceResult, outcome: String): V.GoalPhaseWorkResult = {
    val acceptance = take(B.captureC(None, plan)).effectiveAcceptance.map(boundary => V.GoalPhaseAcceptanceReport(
      B.applicable(boundary, request.work.get.kind).map(c => V.GoalPhaseAcceptanceObservation(c.identity,
        if (c.required) "satisfied" else "unsatisfied", "SPECIFICATION-OWNED observed " + c.description)), Vector.empty))
    V.GoalPhaseWorkResult(outcome, "SPECIFICATION-OWNED " + request.work.get.kind + " " + outcome, Vector.empty,
      acceptance, None, None, None, None, None, None, None, dependencyValidation = None)
  }
  def complete(cli: Cli, plan: O.Plan, request: V.GoalPhaseAdvanceResult, name: String): Unit =
    cli.submit(request, result(plan, request, "completed"), V.WorkflowArtifactReference(name, "1"))
      .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
  def review(cli: Cli, plan: O.Plan, issued: Invocation, name: String): V.GoalPhaseAdvanceResult = {
    val request = issued.business[V.GoalPhaseAdvanceResult]
    val answer = result(plan, request, "accept")
    val reference = V.WorkflowArtifactReference(name, "1")
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("specification-independent-reviewer"), None, None)
    cli.importPrepared(cli.prepare("prepareEvidence", V.WorkflowEvidencePreparationInput("GoalPhase", "review",
      issued.stdout.toUri.toASCIIString, cli.file(name + "-result", json(answer)), cli.file(name + "-reference", json(reference)),
      cli.file(name + "-execution", json(execution)), "normal-development-host", "2026-10-24T00:00:00Z",
      "entity:smworkflow.cli.host_evidence", None)))
    cli.submit(request, answer, reference, Some(execution)).business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
    request
  }
  /** Stops at actual independent Step closure authority, before the next Advance. */
  def authorizeStep(cli: Cli, plan: O.Plan, issued: Invocation, name: String): Invocation = {
    val request = review(cli, plan, issued, name)
    request.work.get.kind shouldBe "ReviewStep"
    val waiting = cli.advance(request.sessionId)
    waiting.business[V.GoalPhaseAdvanceResult].authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
    cli.importPrepared(cli.prepare("prepareAuthority", V.WorkflowAuthorityPreparationInput(waiting.stdout.toUri.toASCIIString,
      cli.file(name + "-authority", json(V.WorkflowArtifactReference(name + "-authority", "1"))), "participant",
      "normal-development-host", "2026-10-24T00:00:00Z", "entity:smworkflow.cli.host_evidence")))
    waiting
  }
  def executions(root: Path): Vector[String] = {
    val marker = root.resolve("sbt-executions.txt")
    if (Files.exists(marker)) Files.readString(marker).linesIterator.toVector else Vector.empty
  }
  /** Existing fixture observation pattern: read-only SQLite and normal codecs.
    * Neither rows nor private storage metadata are written by this observer.
    */
  private def _stored_json(root: Path): Vector[String] = {
    val database = root.resolve("workflow.sqlite")
    Files.isRegularFile(database) shouldBe true
    val connection = DriverManager.getConnection("jdbc:sqlite:" + database.toUri.toASCIIString + "?mode=ro")
    try {
      val statement = connection.createStatement()
      try {
        val tables = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")
        val names = Vector.newBuilder[String]
        try { while (tables.next()) names += tables.getString(1) } finally tables.close()
        names.result().flatMap { name =>
          val query = statement.executeQuery("SELECT * FROM \"" + name.replace("\"", "\"\"") + "\"")
          val values = Vector.newBuilder[String]
          try {
            while (query.next()) (1 to query.getMetaData.getColumnCount).foreach { column =>
              Option(query.getString(column)).filter(_.startsWith("cncf.workflow-json.v1:"))
                .foreach(encoded => values += encoded.stripPrefix("cncf.workflow-json.v1:"))
            }
          } finally query.close()
          values.result()
        }
      } finally statement.close()
    } finally connection.close()
  }
  private def _current(root: Path, session: String): InstanceRecord = {
    val values = _stored_json(root).flatMap(raw => WorkflowInstanceRecordJsonV1.decodeC(raw, _catalog).toOption)
      .filter(_.identity.value == session)
    values.size shouldBe 1
    values.head
  }
  private def _receipts[A](root: Path, session: String, codec: PayloadCodec[A]): Vector[WorkflowMechanicalTransition.Receipt[A]] =
    _stored_json(root).flatMap(raw => WorkflowMechanicalTransitionJsonV1.decodeC(raw, _catalog, codec, _limits).toOption)
      .filter(_.current.identity.value == session).sortBy(_.next.revision.value)
  private def _requests_at(values: Vector[String], session: String): Vector[GoalPhaseNativeWork.Request] =
    values.flatMap(raw => WorkflowWorkOrderJsonV1.decodeC(raw, GoalPhaseNativeWork.inputCodec).toOption)
      .map(_.current.asInstanceOf[WorkflowContinuation.WorkOrder[GoalPhaseNativeWork.Request]].request.input.get.value)
      .filter(_.instanceIdentity == session).sortBy(_.sourceRevision.toLong)
  private def _requests(root: Path, session: String): Vector[GoalPhaseNativeWork.Request] =
    _requests_at(_stored_json(root), session)
  private def _snapshot(root: Path, session: String, controller: Json): Snapshot = Snapshot(
    _stored_json(root).filter(raw => WorkflowInstanceRecordJsonV1.decodeC(raw, _catalog).toOption.isEmpty).sorted,
    take(WorkflowInstanceRecordJsonV1.encodeC(_current(root, session))), executions(root), controller)
  private def _prefix(root: Path, session: String, baseline: Snapshot, launches: Int): Unit = {
    val actual = _snapshot(root, session, baseline.controller)
    // Compare a multiset of complete stored bytes, retaining semantic results,
    // reviews, closure commits and every mechanical receipt, not only names.
    baseline.immutable.diff(actual.immutable) shouldBe Vector.empty
    val original = take(WorkflowInstanceRecordJsonV1.decodeC(baseline.current, _catalog))
    _current(root, session).history.take(original.history.size) shouldBe original.history
    actual.launches.take(baseline.launches.size) shouldBe baseline.launches
    actual.launches.size shouldBe launches
  }
  private def _unchanged(root: Path, session: String, baseline: Snapshot, directions: Int = 0): Unit = {
    _prefix(root, session, baseline, baseline.launches.size)
    take(WorkflowInstanceRecordJsonV1.encodeC(_current(root, session))) shouldBe baseline.current
    _receipts(root, session, A.codec).size shouldBe directions
    def _same_receipts_[A](codec: PayloadCodec[A]): Unit = {
      val original = baseline.immutable.flatMap(raw => WorkflowMechanicalTransitionJsonV1.decodeC(raw, _catalog, codec, _limits).toOption)
        .filter(_.current.identity.value == session).sortBy(_.next.revision.value)
      _receipts(root, session, codec) shouldBe original
    }
    _same_receipts_(S.codec)
    _same_receipts_(N.codec)
    _same_receipts_(C.codec)
    _same_receipts_(A.codec)
    _requests(root, session) shouldBe _requests_at(baseline.immutable, session)
    val originalresults = baseline.immutable.filter(raw => org.goldenport.cncf.workflow.WorkflowResultJsonV1
      .decodeC(raw, GoalPhaseNativeWork.resultCodec).isSuccess)
    _stored_json(root).filter(raw => org.goldenport.cncf.workflow.WorkflowResultJsonV1
      .decodeC(raw, GoalPhaseNativeWork.resultCodec).isSuccess).sorted shouldBe originalresults.sorted
  }
  private def _baseline(root: Path): Snapshot =
    read(root.resolve("validation-repair-baseline.json")).as[Snapshot].fold(e => throw new AssertionError(e.message), identity)
  private def _preserve_source(root: Path, session: String, target: A.Target): Unit = {
    val intent = target.source.hcursor.downField("intent").get[String]("identity").toOption.get
    val receipts = _receipts(root, session, N.codec)
    receipts.count(_.reference.identity == intent) shouldBe 1
    if (target.limitations == A.historicalLimitations) {
      target.source.hcursor.get[String]("resultDisposition").toOption shouldBe Some("unobserved")
      val original = receipts.find(_.reference.identity == intent).get.payload.value
      original.outcome shouldBe None
      original.receipts shouldBe Vector.empty
      // Result/validation identity derives from the retained intent's actual
      // sourceRevision/check/attempt. New corrective rounds have other prefixes.
      val prefix = intent.take(intent.lastIndexOf(':') + 1)
      val suffix = intent.substring(intent.lastIndexOf(':') + 1)
      suffix should startWith ("check-intent-")
      receipts.exists(_.reference.identity == prefix + suffix.replaceFirst("check-intent-", "check-result-")) shouldBe false
      receipts.exists(_.reference.identity == prefix + "validation") shouldBe false
    } else {
      val result = target.source.hcursor.downField("result").get[String]("identity").toOption.get
      val saved = receipts.filter(_.reference.identity == result)
      saved.size shouldBe 1
      N.outputLimited(saved.head.payload.value) shouldBe true
    }
  }
  private def _config(root: Path)(change: Json => Json): Unit = {
    val file = root.resolve(".textus/config.json")
    Files.writeString(file, change(read(file)).noSpaces)
  }
  /** Ordinary ScalaTest discovery and test task; no ProductCheck main or manual enrollment. */
  private def _product(root: Path): Unit = {
    Files.writeString(root.resolve("repository/build.sbt"), "scalaVersion := \"2.12.18\"\nlibraryDependencies += \"org.scalatest\" %% \"scalatest\" % \"3.2.19\" % Test\n")
    Files.writeString(root.resolve("repository").resolve(sourcePath), """import org.scalatest.wordspec.AnyWordSpec
      |import org.scalatest.matchers.should.Matchers
      |import org.scalatest.GivenWhenThen
      |class ProductCheck extends AnyWordSpec with Matchers with GivenWhenThen {
      |  "Selected product" should {
      |    "retain the accepted newline and bounded diagnostics" in {
      |      Given("the selected product workspace and its accepted newline input")
      |      val root = java.nio.file.Paths.get("").toAbsolutePath.normalize
      |      val parent = root.getParent
      |      When("the real product Test records this launch and emits its configured diagnostics")
      |      java.nio.file.Files.write(parent.resolve("sbt-executions.txt"),
      |        (java.lang.ProcessHandle.current().pid().toString + "\n").getBytes("UTF-8"),
      |        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)
      |      val boundeddiagnostics = false
      |      if (java.nio.file.Files.exists(parent.resolve("phase-output-limit")) && !boundeddiagnostics) {
      |        System.err.print("OUTPUT_LIMIT_REAL_DIAGNOSTIC" * 10000)
      |        System.err.flush()
      |      }
      |      Then("the product retains exactly the accepted value and one newline")
      |      java.nio.file.Files.readString(root.resolve("value.txt")) shouldBe "expected\n"
      |    }
      |  }
      |}
      |""".stripMargin)
  }
  /** Adds finite real typed Test workspace programs. These are ordinary host
    * declarations, not arbitrary padding or an edited target.processPlan.
    */
  private def _large_plan(plan: Json, repository: Path): Json = {
    def _array_(name: String) = plan.hcursor.get[Vector[Json]](name).toOption.get
    val ordinary = _array_("sbtPrograms").head
    val owner = SmWorkflowComponent.componentId.name
    val extra = (1 to 4096).map { n =>
      val id = "qualification-test-workspace-" + n
      ordinary.mapObject(_.add("target", id.asJson).add("identity", id.asJson).add("capability", id.asJson))
    }.toVector
    val ids = extra.map(_.hcursor.get[String]("target").toOption.get)
    val workspaces = ids.map(id => Json.obj("identity" -> id.asJson, "root" -> repository.toString.asJson))
    val grants = ids.map(id => Json.obj("componentId" -> owner.asJson, "workspaceId" -> id.asJson, "access" -> "manage".asJson))
    val capabilities = (_array_("gitPrograms") ++ _array_("sbtPrograms") ++ extra).map(_.hcursor.get[String]("capability").toOption.get)
    plan.mapObject(_.add("sbtPrograms", Json.fromValues(_array_("sbtPrograms") ++ extra))
      .add("workspaces", Json.fromValues(_array_("workspaces") ++ workspaces))
      .add("workspaceGrants", Json.fromValues(_array_("workspaceGrants") ++ grants))
      .add("grants", Json.arr(Json.obj("componentId" -> owner.asJson, "capabilities" -> capabilities.asJson))))
  }
  /** Setup uses ordinary old installed Operations and stops before the final
    * command. Only the parent invokes that final command and acquires its page.
    */
  def setup(executable: Path, installations: Path, callerThread: String, large: Boolean): State = {
    val scenario = F.fresh("validation-repair-stage", bootstrapSelectedInput = false,
      sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable), repairContinuation = true)
    scenario.selectCompletionPolicy(GoalPhasePostCompletionSync.localPolicy)
    val root = scenario.root
    val repository = root.resolve("repository")
    val paths = scenario.host.goal.candidatePaths ++ Vector(sourcePath, "build.sbt")
    val goal = scenario.host.goal.copy(candidatePaths = paths, closure = scenario.host.goal.closure.copy(programs =
      scenario.host.goal.closure.programs :+ GoalPhaseNativeClosure.StepProgram("step-3", 3)))
    val host = scenario.host.copy(goal = goal)
    val plan = scenario.plan.copy(repairLimit = 10, steps = scenario.plan.steps.updated(0,
      scenario.plan.steps.head.copy(ownedPaths = paths)), acceptance = Some(B.Adoption("Original accepted Phase", (1 to 31).map(n =>
        B.Condition("original-phase-" + n, "phase", true, "Preserve original requirement " + n)).toVector)))
    Files.writeString(root.resolve("goal-plan.json"), plan.asJson.noSpaces)
    val target = Target(repository, host.target.localBranch, host.target.remote, host.target.remoteBranch, host.target.endpoint)
    val git = TypedGitHostPlanFixture.plan(target, kinds = TypedGitHostPlanFixture.observations ++
      Vector(GitOperationKind.IndexTree, GitOperationKind.PendingPaths, GitOperationKind.PendingDiff, GitOperationKind.CommitMetadata),
      goalPaths = paths, goalMessages = Vector("Complete selected continuation Step 1", "Complete selected continuation Step 2", "Complete corrective validation Step"),
      goalSelections = Vector(paths, Vector("docs/phase.md"), Vector(sourcePath)))
    val process = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(1), Some(git))
    val selected = if (large) _large_plan(process, repository) else process
    _config(root)(_.mapObject(_.add("sm-workflow.runtime.definition", ThreeWorkflowRuntimeParameters.encode(host).noSpaces.asJson)
      .add("textus.subsystem.process-execution.plan", selected.noSpaces.asJson)
      .add("textus.resource.read.max-bytes", "16777216".asJson)
      .add("workflow.mechanical-receipt.max-bytes", "16777216".asJson)))
    val cli = new Cli(root, executable, installations)
    cli.importPrepared(cli.prepare("prepareGoalStart", scenario.selectedGoalPreparationInput))
    val session = cli.goal("startGoalPhase", host.goal.input).business[V.GoalPhaseStartResult].sessionId
    val first = cli.advance(session).business[V.GoalPhaseAdvanceResult]
    first.work.get.kind shouldBe "ImplementStep"
    _product(root)
    Files.writeString(repository.resolve("value.txt"), "expected\n")
    Files.writeString(repository.resolve("docs/phase.md"), "# Accepted original guide\n\nFirst entry.\n")
    complete(cli, plan, first, "original-step-one")
    authorizeStep(cli, plan, cli.advance(session), "original-step-one-review")
    val second = cli.advance(session).business[V.GoalPhaseAdvanceResult]
    second.work.get.stepIdentity shouldBe Some("step-2")
    Files.writeString(repository.resolve("docs/phase.md"), "# Accepted original guide\n\nFirst entry.\n\nSecond entry.\n")
    complete(cli, plan, second, "original-step-two")
    val waiting = authorizeStep(cli, plan, cli.advance(session), "original-step-two-review")
    _finish_setup(root, plan, session, parse(json(second)).toOption.get, read(waiting.stdout),
      executable, installations, callerThread)
  }
  /** Finish only the fixture-owned handoff after the actual independent review.
    * All Workflow observations use the same read-only store and normal codecs.
    */
  private def _finish_setup(root: Path, rawplan: O.Plan, session: String, secondjson: Json,
    waitingjson: Json, executable: Path, installations: Path, callerthread: String): State = {
    val baseline = root.resolve("validation-repair-baseline.json")
    val marker = root.resolve("phase-output-limit")
    val task = root.resolve("target/sm-workflow/tasks/validation-repair-qualification")
    require(Vector(baseline, marker, task).forall(path => !Files.exists(path)),
      "Finish setup cannot replace an existing fixture baseline, output-limit marker or task")
    val second = take(summon[ValueReader[V.GoalPhaseAdvanceResult]].readC(take(new RecordDecoder().json(secondjson.noSpaces))))
    val waiting = take(summon[ValueReader[V.GoalPhaseAdvanceResult]].readC(take(new RecordDecoder().json(waitingjson.noSpaces))))
    rawplan.effectiveAcceptance shouldBe None
    rawplan.acceptance.get.conditions.size shouldBe 31
    val captured = take(B.captureC(None, rawplan))
    captured.effectiveAcceptance.get.conditions.size shouldBe 31
    second.sessionId shouldBe session
    second.work.get.kind shouldBe "ImplementStep"
    second.work.get.stepIdentity shouldBe Some("step-2")
    waiting.sessionId shouldBe session
    waiting.kind shouldBe "Wait"
    val assigned = _requests(root, session).last
    assigned.instanceIdentity shouldBe session
    assigned.kind shouldBe "ReviewStep"
    assigned.stepIdentity shouldBe Some("step-2")
    assigned.selectedPlan shouldBe captured
    val current = _current(root, session)
    current.currentProgression.map(_.value) shouldBe Some("phase.Closing")
    current.suspension shouldBe None
    val authority = waiting.authorityContext.get
    authority.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
    authority.scope.getString("instance") shouldBe Some(session)
    authority.scope.getString("source_revision") shouldBe Some(current.revision.value.toString)
    authority.scope.getString("step") shouldBe Some("step-2")
    authority.scope.getString("plan") shouldBe Some(captured.identity)
    authority.scope.getString("plan_revision") shouldBe Some(captured.revision)
    executions(root).size shouldBe 2
    val observed = _snapshot(root, session, Json.obj("lastIssuedWork" -> secondjson,
      "lastAssignedControllerInput" -> GoalPhaseNativeWork.inputCodec.encode(assigned),
      "closureAuthorityWait" -> waitingjson))
    _receipts(root, session, S.codec).map(_.payload.value.plan) shouldBe Vector(captured)
    _receipts(root, session, C.codec).flatMap(_.payload.value.commit).size shouldBe 1
    Files.writeString(baseline, observed.asJson.noSpaces, StandardOpenOption.CREATE_NEW)
    Files.writeString(marker, "final whole Test only\n", StandardOpenOption.CREATE_NEW)
    Files.createDirectory(Files.createDirectories(task.getParent).resolve(task.getFileName))
    Files.createDirectory(task.resolve(outputName))
    val policy = P.CallerSourcePolicy("sm-workflow.validation-repair-caller-source-policy.v1", callerthread,
      executable.toString, root.toString, task.toString)
    P.policyValid(policy) shouldBe true
    _config(root)(_.mapObject(_.add(ThreeWorkflowRuntimeParameters.validationRepairCallerSource.name, policy.asJson)))
    State(root.toString, task.toString, session, rawplan, executable.toString, installations.toString,
      callerthread, P.command(policy, session, outputName))
  }
  /** Test-only completion of a successfully executed setup, with no command
    * invocation, authority import, Workflow row write or effect replay.
    */
  def finishSetup(): State = {
    val root = path("smworkflow.validationRepair.existingRoot")
    val lastwork = path("smworkflow.validationRepair.lastWork")
    val closurewait = path("smworkflow.validationRepair.closureWait")
    require(lastwork.startsWith(root) && closurewait.startsWith(root) &&
      Files.isRegularFile(lastwork) && Files.isRegularFile(closurewait),
      "Finish setup requires the retained actual second work and closure Wait under the existing fixture")
    val state = stateFile
    require(Vector(state, state.resolveSibling(state.getFileName.toString + ".baseline.json"),
      state.resolveSibling(state.getFileName.toString + ".command.txt"),
      state.resolveSibling(state.getFileName.toString + ".environment.json")).forall(path => !Files.exists(path)),
      "Finish setup cannot replace a staged State or its retained handoff")
    val rawplan = read(root.resolve("goal-plan.json")).as[O.Plan].fold(e => throw new AssertionError(e.message), identity)
    val secondjson = read(lastwork)
    val second = take(summon[ValueReader[V.GoalPhaseAdvanceResult]].readC(take(new RecordDecoder().json(secondjson.noSpaces))))
    _finish_setup(root, rawplan, second.sessionId, secondjson, read(closurewait),
      path("smworkflow.validationRepair.oldCommand"), path("smworkflow.validationRepair.oldInstallations"),
      required("smworkflow.validationRepair.callerThread"))
  }
  /** Reuse a genuine installed incomplete observation and its original caller
    * policy without creating a runtime call or writing any fixture/native row.
    */
  def existingNative(executable: Path, finishAdopted: Boolean = false): (State, Path, V.GoalPhaseAdvanceResult) = {
    val root = path("smworkflow.validationRepair.nativeExistingRoot")
    val observation = path("smworkflow.validationRepair.nativeObservation")
    val installations = path("smworkflow.validationRepair.nativeExistingInstallations")
    require(Files.isDirectory(root) && observation.startsWith(root) && Files.isRegularFile(observation),
      "The native observation must be the retained existing fixture output")
    val rawplan = read(root.resolve("goal-plan.json")).as[O.Plan].fold(e => throw new AssertionError(e.message), identity)
    val observed = readBusiness[V.GoalPhaseAdvanceResult](observation)
    val policy = read(root.resolve(".textus/config.json")).hcursor
      .get[P.CallerSourcePolicy](ThreeWorkflowRuntimeParameters.validationRepairCallerSource.name)
      .fold(e => throw new AssertionError(e.message), identity)
    policy.runtimeRoot shouldBe root.toString
    policy.installedExecutable shouldBe executable.toString
    val task = root.resolve("target/sm-workflow/tasks/validation-repair-qualification")
    Path.of(policy.taskRoot).toRealPath() shouldBe task
    policy.taskRoot shouldBe task.toString
    rawplan.effectiveAcceptance shouldBe None
    rawplan.acceptance.get.conditions.size shouldBe 31
    val captured = take(B.captureC(None, rawplan))
    captured.effectiveAcceptance.get.conditions.size shouldBe 31
    val session = observed.sessionId
    _receipts(root, session, S.codec).map(_.payload.value.plan) shouldBe Vector(captured)
    val current = _current(root, session)
    current.identity.value shouldBe session
    if (finishAdopted) {
      current.currentProgression.map(_.value) shouldBe Some("phase.Implementation")
      _receipts(root, session, A.codec).size shouldBe 1
      val issued = _requests(root, session).filter(request =>
        request.kind == "ImplementStep" && request.stepIdentity.contains("step-3"))
      issued should have size 1
      _requests(root, session).last shouldBe issued.head
      current.suspension.map(_.continuationIdentity.value) shouldBe
        Some(session + ":ordered-implementation:" + issued.head.sourceRevision)
    } else {
      current.currentProgression.map(_.value) shouldBe Some("phase.Decision")
      current.suspension shouldBe None
    }
    executions(root).size shouldBe 3
    _receipts(root, session, C.codec).flatMap(_.payload.value.commit).size shouldBe 2
    _prefix(root, session, _baseline(root), 3)
    val state = State(root.toString, task.toString, session, rawplan, executable.toString,
      installations.toString, policy.threadId, P.command(policy, session, outputName))
    (state, observation, observed)
  }
  def save(state: State): Unit = {
    require(!Files.exists(stateFile), "Do not replace an existing staged Session")
    Files.writeString(stateFile, state.asJson.noSpaces, StandardOpenOption.CREATE_NEW)
    Files.copy(Path.of(state.root).resolve("validation-repair-baseline.json"),
      stateFile.resolveSibling(stateFile.getFileName.toString + ".baseline.json"))
    Files.writeString(stateFile.resolveSibling(stateFile.getFileName.toString + ".command.txt"), state.command + "\n", StandardOpenOption.CREATE_NEW)
    Files.writeString(stateFile.resolveSibling(stateFile.getFileName.toString + ".environment.json"),
      Json.obj("SM_WORKFLOW_INSTALLATIONS" -> state.oldInstallations.asJson, "cwd" -> state.root.asJson,
        "expectedSourceType" -> "commandExecution".asJson, "outputName" -> outputName.asJson).noSpaces,
      StandardOpenOption.CREATE_NEW)
  }
  private def _proposal(state: State): GoalPhasePlanRevision.Proposal = {
    val corrective = state.plan.copy(revision = "2", steps = state.plan.steps :+
      O.Step("step-3", "Bound actual test diagnostic output without weakening original acceptance", Vector(sourcePath),
        state.plan.steps.map(_.identity), Vector("test")))
    GoalPhasePlanRevision.Proposal("validation-repair", "1", "Preserve original31 requirements and bound diagnostic output", corrective)
  }
  private def _prepare_adoption(cli: Cli, described: Invocation, answer: V.GoalPhaseValidationRepairAdoptionInput,
    name: String): Unit = {
    val execution = V.WorkflowExecutionEvidence(Vector(answer.reference), Some("participant"), None, None)
    cli.importPrepared(cli.prepare("prepareEvidence", V.WorkflowEvidencePreparationInput("GoalPhase", "validation-repair-adoption",
      described.stdout.toUri.toASCIIString, cli.file(name + "-answer", json(answer)), cli.file(name + "-reference", json(answer.reference)),
      cli.file(name + "-execution", json(execution)), "normal-development-host", "2026-10-24T00:00:00Z",
      "entity:smworkflow.cli.host_evidence", Some("participant"))))
  }
  private def _refused(root: Path, session: String, baseline: Snapshot, invocation: => Invocation,
    directions: Int = 0): Unit = {
    val observed = invocation
    withClue(Files.readString(observed.stdout) + Files.readString(observed.stderr)) { observed.exit should not be 0 }
    _unchanged(root, session, baseline, directions)
  }
  /** Mutations here arrange negative fixture inputs only; restore exact normal
    * host bytes and never alter a native row or the positive source page.
    */
  private def _refusals(cli: Cli, state: State, described: Invocation, input: V.GoalPhaseValidationRepairDescriptionInput,
    answer: V.GoalPhaseValidationRepairAdoptionInput, selector: Option[P.PriorInvocationSelector], baseline: Snapshot): Unit = {
    val root = cli.root
    val config = root.resolve(".textus/config.json")
    val original = Files.readAllBytes(config)
    val resource = cli.file("negative-controls-full-adoption", json(answer))
    def _adopt_(): Invocation = cli.call("GoalPhaseService", A.adoptionOperation, Vector("--inputResource", resource))
    def _changed_config_(change: Json => Json): Unit = try {
      _config(root)(change)
      _refused(root, state.sessionId, baseline, _adopt_())
    } finally Files.write(config, original)
    _refused(root, state.sessionId, baseline, cli.call("GoalPhaseService", A.adoptionOperation,
      Vector("--input", "{}", "--inputResource", resource)))
    _refused(root, state.sessionId, baseline, cli.call("GoalPhaseService", A.adoptionOperation, Vector.empty))
    val incomplete = parse(json(answer)).toOption.get.mapObject(_.remove("proposal"))
    _refused(root, state.sessionId, baseline, cli.call("GoalPhaseService", A.adoptionOperation,
      Vector("--inputResource", cli.file("negative-only-incomplete-dto", incomplete.noSpaces))))
    val invalid = root.resolve("negative-only-invalid-utf8.json")
    Files.write(invalid, Array(0xc3.toByte, 0x28.toByte), StandardOpenOption.CREATE_NEW)
    _refused(root, state.sessionId, baseline, cli.call("GoalPhaseService", A.adoptionOperation,
      Vector("--inputResource", invalid.toUri.toASCIIString)))
    _changed_config_(_.mapObject(_.add("textus.resource.read.max-bytes", "128".asJson)))
    val denied = Files.createDirectory(root.resolve("negative-only-resource-root"))
    _changed_config_(_.mapObject(_.add("textus.resource.url.file.roots", denied.toString.asJson)))
    val outside = Files.createTempFile(root.getParent, "validation-repair-negative-outside-", ".json")
    try {
      Files.writeString(outside, json(answer))
      _refused(root, state.sessionId, baseline, cli.call("GoalPhaseService", A.adoptionOperation,
        Vector("--inputResource", outside.toUri.toASCIIString)))
    } finally Files.delete(outside)
    val target = take(A.targetC(answer.target))
    val changedtarget = answer.copy(target = A.targetRecord(target.copy(generation = target.generation + 1L)))
    _refused(root, state.sessionId, baseline, cli.call("GoalPhaseService", A.adoptionOperation,
      Vector("--inputResource", cli.file("negative-only-changed-target", json(changedtarget)))))
    val stalefrontier = if (selector.nonEmpty) target.source.mapObject(_.add("recovery",
      target.source.hcursor.downField("recovery").focus.get.mapObject(_.add("frontierRevision",
        (_current(root, state.sessionId).revision.value - 1L).toString.asJson))))
      else target.source.mapObject(_.add("frontierRevision", (_current(root, state.sessionId).revision.value - 1L).toString.asJson))
    _refused(root, state.sessionId, baseline, cli.call("GoalPhaseService", A.adoptionOperation,
      Vector("--inputResource", cli.file("negative-only-stale-frontier", json(answer.copy(target = A.targetRecord(target.copy(source = stalefrontier))))))))
    _changed_config_ { value =>
      val key = "textus.subsystem.process-execution.plan"
      val plan = parse(value.hcursor.get[String](key).toOption.get).toOption.get
      val programs = plan.hcursor.get[Vector[Json]]("sbtPrograms").toOption.get
      val changed = programs.head.mapObject(_.add("limits", programs.head.hcursor.downField("limits").focus.get
        .mapObject(_.add("stdoutBytes", 131073.asJson))))
      value.mapObject(_.add(key, plan.mapObject(_.add("sbtPrograms", (changed +: programs.tail).asJson)).noSpaces.asJson))
    }
    _changed_config_ { value =>
      val key = "sm-workflow.runtime.definition"
      val definition = parse(value.hcursor.get[String](key).toOption.get).toOption.get
      val goal = definition.hcursor.downField("goal").focus.get
      val policy = goal.hcursor.downField("phaseValidation").focus.get
      val checks = policy.hcursor.get[Vector[Json]]("checks").toOption.get
      val changed = checks.head.mapObject(_.add("identity", "negative-only-different-whole-test".asJson))
      value.mapObject(_.add(key, definition.mapObject(_.add("goal", goal.mapObject(_.add("phaseValidation",
        policy.mapObject(_.add("checks", (changed +: checks.tail).asJson)))))).noSpaces.asJson))
    }
    selector.foreach { selected =>
      _changed_config_ { value =>
        val key = ThreeWorkflowRuntimeParameters.validationRepairCallerSource.name
        val policy = value.hcursor.get[P.CallerSourcePolicy](key).toOption.get
        value.mapObject(_.add(key, policy.copy(threadId = "negative-only-foreign-caller").asJson))
      }
      val source = Path.of(java.net.URI.create(selected.sourceResource))
      val sourcebytes = Files.readAllBytes(source).toVector
      val absent = source.resolveSibling(source.getFileName.toString + ".fixture-unavailable")
      Files.move(source, absent)
      try _refused(root, state.sessionId, baseline, _adopt_()) finally Files.move(absent, source)
      // An exact copy initially describes the same genuine item, but is ONLY a
      // negative source-reread control. It never supplies positive acquisition.
      val negative = root.resolve("negative-only-changed-source-page.json")
      Files.copy(source, negative)
      val negativeinput = input.copy(priorInvocation = Some(GoalPhaseNativeMetadata.record(
        selected.copy(sourceResource = negative.toUri.toASCIIString).asJson)))
      val negativedescribed = cli.goal(A.describeOperation, negativeinput)
      val negativetarget = negativedescribed.business[V.GoalPhaseValidationRepairDescriptionResult].target
      val negativeanswer = answer.copy(target = negativetarget,
        reference = V.WorkflowArtifactReference("negative-only-source-reread-human", "1"))
      _prepare_adoption(cli, negativedescribed, negativeanswer, "negative-only-source-reread")
      Files.writeString(negative, "{}")
      _refused(root, state.sessionId, baseline, cli.call("GoalPhaseService", A.adoptionOperation,
        Vector("--inputResource", cli.file("negative-only-changed-source-answer", json(negativeanswer)))))
      // Restore the exact positive independent import after provisioning the
      // separately labelled negative source. No native history was changed.
      Files.write(config, original)
      Files.readAllBytes(source).toVector shouldBe sourcebytes
    }
    Files.readAllBytes(config).toVector shouldBe original.toVector
    // Preserve the exact positive description bytes as a retained observable;
    // the negative source description and evidence above remain separate.
    take(A.targetC(described.business[V.GoalPhaseValidationRepairDescriptionResult].target)) shouldBe target
    _unchanged(root, state.sessionId, baseline)
  }
  private def _git(root: Path, arguments: Vector[String]): String = {
    val directory = Files.createTempDirectory(root, "fixture-git-control-")
    val stdout = directory.resolve("stdout.txt")
    val stderr = directory.resolve("stderr.txt")
    val process = new ProcessBuilder((Vector("git", "-C", root.resolve("repository").toString) ++ arguments).asJava)
      .redirectOutput(stdout.toFile).redirectError(stderr.toFile).start()
    process.getOutputStream.close()
    val owned = new Running(process, stdout, stderr)
    val observed = owned.await()
    withClue(Files.readString(stderr)) { observed.exit shouldBe 0 }
    Files.readString(stdout)
  }
  private def _before_work_refusals(cli: Cli, state: State, target: A.Target, baseline: Snapshot): Unit = {
    val root = cli.root
    val selected = root.resolve("repository/value.txt")
    val original = Files.readAllBytes(selected)
    val index = _git(root, Vector("write-tree"))
    try {
      Files.writeString(selected, "negative-only-candidate-drift\n")
      _refused(root, state.sessionId, baseline, cli.advance(state.sessionId), directions = 1)
      _git(root, Vector("add", "--", "value.txt"))
      Files.write(selected, original)
      _refused(root, state.sessionId, baseline, cli.advance(state.sessionId), directions = 1)
    } finally {
      Files.write(selected, original)
      _git(root, Vector("reset", "--", "value.txt"))
    }
    _git(root, Vector("write-tree")) shouldBe index
    Files.readAllBytes(selected).toVector shouldBe original.toVector
    _preserve_source(root, state.sessionId, target)
    _unchanged(root, state.sessionId, baseline, directions = 1)
  }
  private def _continue(state: State, cli: Cli, selector: Option[P.PriorInvocationSelector], threshold: Long): Unit = {
    val root = cli.root
    val task = Path.of(state.taskRoot)
    val setupbaseline = _baseline(root)
    _prefix(root, state.sessionId, setupbaseline, 3)
    _receipts(root, state.sessionId, C.codec).flatMap(_.payload.value.commit).size shouldBe 2
    val actual = _snapshot(root, state.sessionId, setupbaseline.controller)
    val beforedescription = task.resolve("before-description-native-snapshot.json")
    val original = if (Files.exists(beforedescription)) {
      val saved = read(beforedescription).as[Snapshot].fold(e => throw new AssertionError(e.message), identity)
      saved shouldBe actual
      saved
    } else {
      Files.writeString(beforedescription, actual.asJson.noSpaces, StandardOpenOption.CREATE_NEW)
      actual
    }
    val unrelated = root.resolve("repository/unrelated-observation.txt")
    val unrelatedbytes = "fixture-owned unrelated dirty path\n".getBytes(UTF_8).toVector
    if (Files.exists(unrelated)) Files.readAllBytes(unrelated).toVector shouldBe unrelatedbytes
    else Files.write(unrelated, unrelatedbytes.toArray, StandardOpenOption.CREATE_NEW)
    def _dirty_(): Unit = Files.readAllBytes(unrelated).toVector shouldBe unrelatedbytes
    val proposal = _proposal(state)
    val proposalrecord = GoalPhasePlanRevision.publicProposal(Some(proposal)).get
    val input = V.GoalPhaseValidationRepairDescriptionInput(state.sessionId, proposalrecord,
      selector.map(selected => GoalPhaseNativeMetadata.record(selected.asJson)))
    val described = cli.call("GoalPhaseService", A.describeOperation,
      Vector("--inputResource", cli.file("describe-full", json(input))))
    val description = described.business[V.GoalPhaseValidationRepairDescriptionResult]
    val target = take(A.targetC(description.target))
    val captured = take(B.captureC(None, state.plan))
    state.plan.effectiveAcceptance shouldBe None
    state.plan.acceptance.get.conditions.size shouldBe 31
    target.plan shouldBe captured
    target.completed.map(_.step) shouldBe state.plan.steps.map(_.identity)
    target.plan.acceptance.get.conditions.size shouldBe 31
    target.plan.effectiveAcceptance.get.conditions.size shouldBe 31
    val lastwork = GoalPhaseNativeWork.inputCodec.decode(setupbaseline.controller.hcursor
      .get[Json]("lastAssignedControllerInput").toOption.get).fold(e => throw new AssertionError(e), identity)
    target.repairProgress shouldBe lastwork.repairProgress.get
    // Closing the last accepted Step advances the controller generation once;
    // the retained work request still names its preceding implementation.
    target.generation shouldBe lastwork.generation + 1L
    val start = _receipts(root, state.sessionId, S.codec).head.payload.value
    start.plan shouldBe captured
    val sourcebase = if (selector.nonEmpty) target.source.hcursor.downField("recovery").get[O.Snapshot]("base").toOption.get
      else target.source.hcursor.get[O.Snapshot]("base").toOption.get
    sourcebase shouldBe start.base
    val commits = _receipts(root, state.sessionId, C.codec).flatMap(_.payload.value.commit)
    target.completed.map(_.commit) shouldBe commits
    target.limitations shouldBe (if (selector.nonEmpty) A.historicalLimitations else A.nativeLimitations)
    description.choices shouldBe Vector(if (selector.nonEmpty) A.historicalChoice else "AdoptRepairPlan", "Defer")
    _preserve_source(root, state.sessionId, target)
    _unchanged(root, state.sessionId, original)
    _dirty_()
    val processplanbytes = target.processPlan.getBytes(UTF_8).length.toLong
    if (selector.nonEmpty) processplanbytes should be > threshold
    val reference = V.WorkflowArtifactReference("specification-independent-human-validation-repair", "1")
    val answer = V.GoalPhaseValidationRepairAdoptionInput(state.sessionId, description.target, proposalrecord,
      if (selector.nonEmpty) A.historicalChoice else "AdoptRepairPlan",
      "SPECIFICATION-OWNED independent Human adopts this exact corrective Plan with all stated source limitations", reference)
    _prepare_adoption(cli, described, answer, "positive-adoption")
    val answerbytes = json(answer).getBytes(UTF_8).length.toLong
    answerbytes should be <= 16777216L
    if (selector.nonEmpty) answerbytes should be > threshold
    Files.writeString(task.resolve("resource-transport-measurement.json"), Json.obj(
      "transportThresholdBytes" -> threshold.asJson, "processPlanBytes" -> processplanbytes.asJson,
      "fullAdoptionDtoBytes" -> answerbytes.asJson, "resourceLimitBytes" -> 16777216.asJson).noSpaces,
      StandardOpenOption.CREATE_NEW)
    _refusals(cli, state, described, input, answer, selector, original)
    _dirty_()
    val resource = cli.file("positive-racing-full-adoption", json(answer))
    val arguments = Vector("--inputResource", resource)
    val first = cli.start("GoalPhaseService", A.adoptionOperation, arguments)
    val second = try cli.start("GoalPhaseService", A.adoptionOperation, arguments) catch {
      case failure: Throwable => first.close(); throw failure
    }
    val results = try Vector(first.await(), second.await()) finally { first.close(); second.close() }
    val directions = _receipts(root, state.sessionId, A.codec)
    directions.size should be <= 1
    directions.size shouldBe 1
    results.filter(_.exit == 0).foreach(_.business[V.GoalPhaseValidationRepairAdoptionResult].accepted shouldBe true)
    results.exists(_.exit == 0) shouldBe true
    directions.head.payload.value.target shouldBe target
    _current(root, state.sessionId).revision.value shouldBe
      take(WorkflowInstanceRecordJsonV1.decodeC(original.current, _catalog)).revision.value + 1L
    _requests(root, state.sessionId) shouldBe _requests_at(original.immutable, state.sessionId)
    _prefix(root, state.sessionId, original, 3)
    _preserve_source(root, state.sessionId, target)
    _dirty_()
    val adopted = _snapshot(root, state.sessionId, original.controller)
    val duplicate = cli.call("GoalPhaseService", A.adoptionOperation, arguments)
    if (duplicate.exit == 0) duplicate.business[V.GoalPhaseValidationRepairAdoptionResult].accepted shouldBe true
    _unchanged(root, state.sessionId, adopted, directions = 1)
    _before_work_refusals(cli, state, target, adopted)
    _dirty_()
    val issued = cli.advance(state.sessionId).business[V.GoalPhaseAdvanceResult]
    _finish_continuation(state, cli, target, original, sourcebase, commits, proposal, issued)
  }
  /** The remaining ordinary corrective work is shared by the full scenario and
    * explicit completion of an actual previously issued, uncompleted Step.
    */
  private def _finish_continuation(state: State, cli: Cli, target: A.Target, original: Snapshot,
    sourcebase: O.Snapshot, commits: Vector[O.Commit], proposal: GoalPhasePlanRevision.Proposal,
    issued: V.GoalPhaseAdvanceResult): Unit = {
    val root = cli.root
    val task = Path.of(state.taskRoot)
    val unrelated = root.resolve("repository/unrelated-observation.txt")
    val unrelatedbytes = "fixture-owned unrelated dirty path\n".getBytes(UTF_8).toVector
    def _dirty_(): Unit = Files.readAllBytes(unrelated).toVector shouldBe unrelatedbytes
    issued.work.get.kind shouldBe "ImplementStep"
    issued.work.get.stepIdentity shouldBe Some("step-3")
    issued.work.get.originalBase.head shouldBe sourcebase.head
    issued.work.get.originalBase.tree shouldBe sourcebase.tree
    issued.work.get.completed.map(_.commit.identity) shouldBe target.completed.map(_.commit.identity)
    issued.repairProgress shouldBe Some(GoalPhaseRepairProgress.publicSnapshot(target.repairProgress))
    _requests(root, state.sessionId).last.generation shouldBe target.generation + 1L
    _prefix(root, state.sessionId, original, 3)
    _preserve_source(root, state.sessionId, target)
    _dirty_()
    val sourcefile = root.resolve("repository").resolve(sourcePath)
    val previous = Files.readString(sourcefile)
    require(previous.contains("val boundeddiagnostics = false"), "Apply the one selected fixture-owned product correction")
    Files.writeString(sourcefile, previous.replace("val boundeddiagnostics = false", "val boundeddiagnostics = true"))
    val pending = _snapshot(root, state.sessionId, original.controller)
    cli.advance(state.sessionId).business[V.GoalPhaseAdvanceResult] shouldBe issued
    _unchanged(root, state.sessionId, pending, directions = 1)
    _prefix(root, state.sessionId, original, 3)
    complete(cli, proposal.plan, issued, "corrective-product-work")
    authorizeStep(cli, proposal.plan, cli.advance(state.sessionId), "corrective-independent-review")
    val phase = cli.advance(state.sessionId)
    phase.business[V.GoalPhaseAdvanceResult].work.get.kind shouldBe "ReviewPhase"
    phase.business[V.GoalPhaseAdvanceResult].repairProgress shouldBe Some(GoalPhaseRepairProgress.publicSnapshot(target.repairProgress))
    _prefix(root, state.sessionId, original, 5)
    review(cli, proposal.plan, phase, "corrective-phase-independent-review")
    val terminal = cli.advance(state.sessionId).business[V.GoalPhaseAdvanceResult]
    terminal.kind shouldBe "Terminal"
    terminal.completion.get.completed.take(2).map(_.step) shouldBe target.completed.map(_.step)
    terminal.completion.get.originalBase shouldBe issued.work.get.originalBase
    _requests(root, state.sessionId).filter(_.generation > target.generation).foreach { request =>
      request.repairProgress shouldBe Some(target.repairProgress)
      request.originalBase shouldBe sourcebase
      request.completed.take(2) shouldBe target.completed
      request.selectedPlan shouldBe take(B.captureC(None, proposal.plan))
    }
    _receipts(root, state.sessionId, C.codec).flatMap(_.payload.value.commit).take(2) shouldBe commits
    _receipts(root, state.sessionId, C.codec).flatMap(_.payload.value.commit).size shouldBe 3
    _receipts(root, state.sessionId, A.codec).size shouldBe 1
    _requests(root, state.sessionId).count(_.kind == "ReviewPhase") shouldBe 1
    _requests(root, state.sessionId).count(request => request.kind == "ImplementStep" && request.stepIdentity.contains("step-3")) shouldBe 1
    _prefix(root, state.sessionId, original, 5)
    _preserve_source(root, state.sessionId, target)
    _dirty_()
    val closed = _snapshot(root, state.sessionId, original.controller)
    cli.advance(state.sessionId).business[V.GoalPhaseAdvanceResult] shouldBe terminal
    _unchanged(root, state.sessionId, closed, directions = 1)
    Files.writeString(task.resolve("after-closure-native-snapshot.json"), closed.asJson.noSpaces, StandardOpenOption.CREATE_NEW)
    Files.writeString(task.resolve("qualification-result.json"), Json.obj("sessionId" -> state.sessionId.asJson,
      "outcome" -> "qualified".asJson, "sourceKind" -> target.source.hcursor.get[String]("kind").toOption.get.asJson,
      "sourceLocator" -> target.source.hcursor.get[P.PriorInvocationSnapshot]("priorInvocation").toOption.map(_.sourceLocator).asJson,
      "scope" -> "SPECIFICATION-OWNED isolated fixture; no real consumer Phase authority".asJson).noSpaces,
      StandardOpenOption.CREATE_NEW)
  }
  /** Finish a genuine already-adopted fixture after the failed negative guard
    * test issued Step3. This does not rerun adoption or reinterpret that failure.
    */
  def finishAdopted(state: State, executable: Path, installations: Path): Unit = {
    val root = Path.of(state.root)
    val task = Path.of(state.taskRoot)
    val original = read(task.resolve("before-description-native-snapshot.json")).as[Snapshot]
      .fold(e => throw new AssertionError(e.message), identity)
    val directions = _receipts(root, state.sessionId, A.codec)
    directions should have size 1
    val direction = directions.head.payload.value
    val target = direction.target
    A.sessionId(target) shouldBe state.sessionId
    state.plan.effectiveAcceptance shouldBe None
    state.plan.acceptance.get.conditions.size shouldBe 31
    val captured = take(B.captureC(None, state.plan))
    target.plan shouldBe captured
    target.plan.acceptance.get.conditions.size shouldBe 31
    target.plan.effectiveAcceptance.get.conditions.size shouldBe 31
    val proposal = _proposal(state)
    target.captured.proposal shouldBe proposal
    direction.proposal shouldBe proposal
    target.captured.plan shouldBe take(B.captureC(None, proposal.plan))
    val commits = _receipts(root, state.sessionId, C.codec).flatMap(_.payload.value.commit)
    commits should have size 2
    target.completed.map(_.step) shouldBe state.plan.steps.map(_.identity)
    target.completed.map(_.commit) shouldBe commits
    val setupbaseline = _baseline(root)
    val lastwork = GoalPhaseNativeWork.inputCodec.decode(setupbaseline.controller.hcursor
      .get[Json]("lastAssignedControllerInput").toOption.get).fold(e => throw new AssertionError(e), identity)
    target.generation shouldBe lastwork.generation + 1L
    target.repairProgress shouldBe lastwork.repairProgress.get
    val start = _receipts(root, state.sessionId, S.codec).head.payload.value
    start.plan shouldBe captured
    val sourcebase = if (target.limitations == A.historicalLimitations)
      target.source.hcursor.downField("recovery").get[O.Snapshot]("base").toOption.get
      else target.source.hcursor.get[O.Snapshot]("base").toOption.get
    sourcebase shouldBe start.base
    val current = _current(root, state.sessionId)
    current.currentProgression.map(_.value) shouldBe Some("phase.Implementation")
    val requests = _requests(root, state.sessionId)
    val originalrequests = _requests_at(original.immutable, state.sessionId)
    requests.take(originalrequests.size) shouldBe originalrequests
    requests should have size (originalrequests.size + 1)
    val stepthree = requests.filter(request => request.kind == "ImplementStep" && request.stepIdentity.contains("step-3"))
    stepthree should have size 1
    requests.last shouldBe stepthree.head
    current.suspension.map(_.continuationIdentity.value) shouldBe
      Some(state.sessionId + ":ordered-implementation:" + stepthree.head.sourceRevision)
    stepthree.head.generation shouldBe target.generation + 1L
    stepthree.head.selectedPlan shouldBe target.captured.plan
    stepthree.head.originalBase shouldBe sourcebase
    stepthree.head.completed shouldBe target.completed
    _prefix(root, state.sessionId, original, 3)
    _preserve_source(root, state.sessionId, target)
    Files.readAllBytes(root.resolve("repository/unrelated-observation.txt")).toVector shouldBe
      "fixture-owned unrelated dirty path\n".getBytes(UTF_8).toVector
    Files.readString(root.resolve("repository").resolve(sourcePath)) should include ("val boundeddiagnostics = false")
    executions(root).size shouldBe 3
    val cli = new Cli(root, executable, installations)
    val pending = _snapshot(root, state.sessionId, original.controller)
    val issued = cli.advance(state.sessionId).business[V.GoalPhaseAdvanceResult]
    _unchanged(root, state.sessionId, pending, directions = 1)
    _finish_continuation(state, cli, target, original, sourcebase, commits, proposal, issued)
  }
  def qualifyNative(state: State, executable: Path, installations: Path, observed: V.GoalPhaseAdvanceResult): Unit = {
    val root = Path.of(state.root)
    val receipts = _receipts(root, state.sessionId, N.codec).filter(value => N.outputLimited(value.payload.value))
    receipts should not be empty
    val raw = _stored_json(root).filter(text => WorkflowMechanicalTransitionJsonV1.decodeC(text, _catalog, N.codec, _limits)
      .toOption.exists(value => value.current.identity.value == state.sessionId && N.outputLimited(value.payload.value)))
    raw should not be empty
    val actual = receipts.last.payload.value.receipts.last.processObservation.get
    actual.stderr.truncated shouldBe true
    actual.stderr.content should not be empty
    actual.stderr.byteCount shouldBe actual.stderr.content.length.toLong
    val retained = Path.of(state.taskRoot).resolve("native-output-limited-receipts.json")
    if (Files.exists(retained)) Files.readString(retained) shouldBe raw.asJson.noSpaces
    else Files.writeString(retained, raw.asJson.noSpaces, StandardOpenOption.CREATE_NEW)
    val fresh = new Cli(root, executable, installations)
    fresh.advance(state.sessionId).business[V.GoalPhaseAdvanceResult] shouldBe observed
    _stored_json(root).intersect(raw).sorted shouldBe raw.sorted
    _receipts(root, state.sessionId, N.codec).filter(value => N.outputLimited(value.payload.value)) shouldBe receipts
    executions(root).size shouldBe 3
    _continue(state, fresh, None, 0L)
  }
  def qualify(state: State, executable: Path, installations: Path): Unit = {
    val root = Path.of(state.root)
    val task = Path.of(state.taskRoot)
    val source = path("smworkflow.validationRepair.sourcePage")
    require(source.startsWith(root), "Retain the genuine complete public page under the fixture Resource root")
    val locator = V.WorkflowCodexSourceLocator(state.callerThread,
      required("smworkflow.validationRepair.sourceTurn"), required("smworkflow.validationRepair.sourceItem"))
    // The page is acquired by the parent from the actual direct old CLI item.
    // This program never constructs, rewrites or substitutes a source page.
    val selector = P.PriorInvocationSelector("sm-workflow.validation-prior-invocation-selector.v1", locator,
      source.toUri.toASCIIString, outputName)
    val oldresponse = task.resolve(outputName).resolve("response.json")
    val oldstderr = task.resolve(outputName).resolve("stderr.log")
    require(Files.isRegularFile(oldresponse) && Files.isRegularFile(oldstderr),
      "Run the exported old installed command exactly once before resume")
    val oldraw = Files.readString(oldresponse)
    val diagnostic = Files.readString(oldstderr)
    oldraw shouldBe ""
    diagnostic should include ("transaction-indeterminate")
    diagnostic should include ("validation result has invalid identity, missing capture, truncation or byte-count mismatch")
    val events = diagnostic.linesIterator.filter(_.startsWith("[cncf-sbt-event] ")).map(line =>
      parse(line.stripPrefix("[cncf-sbt-event] ")).toOption.get).toVector
    val terminals = events.filter(_.hcursor.get[String]("state").contains("terminal"))
    terminals.size shouldBe 1
    terminals.head.hcursor.get[String]("operation").toOption shouldBe Some("test")
    terminals.head.hcursor.get[String]("termination").toOption shouldBe Some("OutputLimitExceeded(Stderr)")
    diagnostic.indexOf("OutputLimitExceeded(Stderr)") should be < diagnostic.indexOf("validation result has invalid identity")
    executions(root).size shouldBe 3
    _prefix(root, state.sessionId, _baseline(root), 3)
    val actual = _snapshot(root, state.sessionId, _baseline(root).controller)
    val passive = task.resolve("old-passive-validation-recovery.json")
    val unknownfile = task.resolve("old-unknown-native-snapshot.json")
    require(Files.exists(passive) == Files.exists(unknownfile),
      "Retained passive description and original native snapshot must exist together")
    val retained = Files.exists(passive)
    val unknown = if (retained) {
      val saved = read(unknownfile).as[Snapshot].fold(e => throw new AssertionError(e.message), identity)
      saved shouldBe actual
      _unchanged(root, state.sessionId, saved)
      saved
    } else actual
    // A retained genuine passive response is decoded directly, without another
    // old call or a manufactured Invocation/exit status.
    val (description, firstobservation) = if (retained) {
      (readBusiness[V.GoalPhaseValidationRecoveryDescriptionResult](passive), Option.empty[Path])
    } else {
      val old = new Cli(root, Path.of(state.oldExecutable), Path.of(state.oldInstallations))
      val described = old.call("GoalPhaseService", GoalPhaseValidationRecovery.describeOperation,
        Vector("--sessionId", state.sessionId))
      (described.business[V.GoalPhaseValidationRecoveryDescriptionResult], Some(described.stdout))
    }
    val recovery = take(GoalPhaseValidationRecovery.targetC(description.target))
    recovery.frontierRevision shouldBe recovery.intentRevision
    recovery.knownFailedPhase shouldBe None
    recovery.terminatedTimeoutPhase shouldBe None
    recovery.dependency shouldBe None
    val intent = _receipts(root, state.sessionId, N.codec).filter(_.next.revision.value.toString == recovery.intentRevision)
    intent.size shouldBe 1
    intent.head.payload.value.outcome shouldBe None
    intent.head.payload.value.receipts shouldBe Vector.empty
    _unchanged(root, state.sessionId, unknown)
    firstobservation.foreach { stdout =>
      Files.copy(stdout, passive)
      Files.writeString(unknownfile, unknown.asJson.noSpaces, StandardOpenOption.CREATE_NEW)
    }
    val argmax = required("smworkflow.validationRepair.argMaxBytes").toLong
    require(argmax > 0L, "Use the actual platform ARG_MAX from the parent qualification environment")
    val environmentallowance = sys.env.toVector.map { (name, value) =>
      (name + "=" + value).getBytes(java.nio.charset.StandardCharsets.UTF_8).length.toLong + 1L
    }.sum + state.oldInstallations.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 128L
    val platformbaseline = task.resolve("platform-transport-baseline.json")
    val savedallowance = if (Files.exists(platformbaseline)) {
      val saved = read(platformbaseline)
      saved.hcursor.get[Long]("argMaxBytes").toOption.get shouldBe argmax
      val allowance = saved.hcursor.get[Long]("environmentAllowanceBytes").toOption.get
      allowance should be >= 0L
      allowance
    } else {
      Files.writeString(platformbaseline, Json.obj("argMaxBytes" -> argmax.asJson,
        "environmentAllowanceBytes" -> environmentallowance.asJson).noSpaces, StandardOpenOption.CREATE_NEW)
      environmentallowance
    }
    val transportthreshold = Math.addExact(argmax, Math.max(savedallowance, environmentallowance))
    val sourcebytes = Files.readAllBytes(source).toVector
    _continue(state, new Cli(root, executable, installations), Some(selector), transportthreshold)
    Files.readString(oldresponse) shouldBe oldraw
    Files.readString(oldstderr) shouldBe diagnostic
    Files.readAllBytes(source).toVector shouldBe sourcebytes
  }
}

/** Explicit staged installed qualification. Setup and resume are separate Test
  * invocations; the final old382 command belongs to the actual parent caller.
  * @since Oct. 24, 2026
  * @version Oct. 24, 2026
  * @author ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalUnobservedPhaseRepairAdoptionCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import NativeValidationRepairInstalledFixture.*
  "Staged installed historical validation repair" should afterWord("in spec:goal-phase-validation-repair-adoption, example:E4, rules:R1,R2,R3,R4,R5,R6") {
    "retain one old382 Session and qualify exact adopted correction through the candidate installation" in {
      required("smworkflow.validationRepair.stage") match {
        case "setup" =>
          Given("the immutable382 installation, normal SBT, and a fresh isolated31-condition Plan")
          val executable = path("smworkflow.validationRepair.oldCommand")
          val installations = path("smworkflow.validationRepair.oldInstallations")
          When("ordinary old Operations accept the original Steps and await the final Advance")
          val state = setup(executable, installations, required("smworkflow.validationRepair.callerThread"), large = true)
          save(state)
          Then("fixture state and the exact direct command exist without a fabricated final commandExecution")
          Files.isRegularFile(stateFile) shouldBe true
          Files.exists(Path.of(state.taskRoot).resolve(outputName).resolve("response.json")) shouldBe false
        case "finish-setup" =>
          Given("the existing fixture after its actual Start, two implementations and independent reviews, with no published handoff")
          When("read-only native observations and the retained public second work and closure Wait finish only the setup tail")
          val state = finishSetup()
          save(state)
          Then("the raw Plan and genuine current baseline are retained before the still-unexecuted exported old Advance")
          state.plan.effectiveAcceptance shouldBe None
          Files.isRegularFile(stateFile) shouldBe true
          Files.exists(Path.of(state.taskRoot).resolve(outputName).resolve("response.json")) shouldBe false
        case "finish-adopted" =>
          Given("the same retained Session with actual prior adoption and an issued but uncompleted corrective Step after the failed negative guard test")
          val state = load()
          When("a normal pending-work read leaves that Session unchanged and only the remaining corrective completion proceeds")
          finishAdopted(state, path("smworkflow.spec.installedCommand"), path("smworkflow.spec.runtimeRoot"))
          Then("the isolated continuation closes while the earlier failed guard observation remains failed")
          Files.isRegularFile(Path.of(state.taskRoot).resolve("qualification-result.json")) shouldBe true
        case "resume" =>
          Given("the same staged Session after the parent ran the exported command and retained its genuine public page")
          val state = load()
          When("the selected candidate installation describes adopts and ordinarily advances that Session")
          qualify(state, path("smworkflow.spec.installedCommand"), path("smworkflow.spec.runtimeRoot"))
          Then("only the isolated corrective continuation has been qualified")
          Files.isRegularFile(Path.of(state.taskRoot).resolve("qualification-result.json")) shouldBe true
        case other => fail("Unsupported exact staged selector " + other)
      }
    }
  }
}
