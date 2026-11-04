package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import org.goldenport.cncf.processexecution.SbtOperation
import org.goldenport.cncf.workflow.{WorkflowInstanceRecordJsonV1, WorkflowMechanicalTransitionJsonV1,
  WorkflowWorkOrderJsonV1}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowContinuation
import org.goldenport.record.RecordPresentable
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import RepositorySyncRepairScopeCorrection as N
import RepositorySyncNativeProgressStore as S
import SplitPhaseExplicitPartitionFixture.take

/** Two separately selected stages exercise a genuine old installed pending
  * issue and its upgrade in the same physical repository and native datastore.
  * All proposals and independent evidence are fixture-authored specification
  * inputs; no live Human or reviewer acceptance is claimed.
  * @since Nov. 3, 2026
  * @version Nov. 3, 2026
  */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeRepositoryRepairScopeCorrectionCliSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with RepositorySyncMaintenanceFixture {
  private val _chosen = Vector("docs/repair.md", "src/test/scala/ProductCheck.scala")
  private val _changed = _chosen :+ "value.txt"
  private val _resolved = "resolved product input\n"
  private val _document = "完全な修復記録を保持する\n" * 80000
  private lazy val _catalog = take(WorkflowInstanceRecordJsonV1.catalogC(Vector(take(RepositorySyncDefinitionBinding.bindC))))

  private final case class Episode(root: String, normalJson: Json, plan: Json, session: String,
    originalRecord: String, head: String, indexTree: String, mergeHead: String, requestFile: String,
    originalWorkIdentity: String, formalReceipts: Vector[(String, String)], invocations: Vector[String])
  private final case class Live(scenario: F.Scenario, session: String) {
    def root: Path = scenario.root
    def repository: Path = Path.of(scenario.host.target.root)
    def call(service: String, operation: String, parameters: Vector[String]): F.Result = scenario.callInstalled(
      _installed, service, operation, parameters, None, Some(root.resolve(".textus/assembly.yaml")))
    def repositoryCall(operation: String, parameters: Vector[String]): F.Result = call("RepositorySyncService", operation, parameters)
    def advance(): F.Result = repositoryCall("advanceRepositorySync", Vector("--sessionId", session))
  }
  private def _installed: Path = {
    val path = Path.of(sys.props.getOrElse("smworkflow.spec.installedCommand", fail("Select the normal installed command explicitly")))
    path.isAbsolute shouldBe true
    path.normalize shouldBe path
    Files.isRegularFile(path) shouldBe true
    Files.isExecutable(path) shouldBe true
    path
  }
  private def _episode_path: Path = {
    val path = Path.of(sys.props.getOrElse("smworkflow.spec.repairScopeEpisode", fail("Select -Dsmworkflow.spec.repairScopeEpisode=<absolute evidence file>")))
    path.isAbsolute shouldBe true
    path.normalize shouldBe path
    Files.isDirectory(path.getParent) shouldBe true
    path
  }
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def _save(root: Path, name: String, text: String): String = {
    val path = root.resolve(name)
    Files.writeString(path, text, UTF_8, StandardOpenOption.CREATE_NEW)
    path.toUri.toASCIIString
  }
  private def _product(expected: String): String = """object ProductCheck {
    |  def main(args: Array[String]): Unit = {
    |    val root = java.nio.file.Paths.get("").toAbsolutePath.normalize
    |    java.nio.file.Files.write(root.getParent.resolve("sbt-executions.txt"),
    |      (java.lang.ProcessHandle.current().pid().toString + "\n").getBytes("UTF-8"),
    |      java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)
    |    println("PRODUCT_CHECK_ENTERED " + root)
    |    if (java.nio.file.Files.readString(root.resolve("value.txt")) != __EXPECTED__)
    |      throw new IllegalStateException("actual resolved product input differs")
    |    println("PRODUCT_CHECK_PASSED")
    |  }
    |}
    |""".stripMargin.replace("__EXPECTED__", Json.fromString(expected).noSpaces)

  /** Actual rows are read for observation only, never seeded or rewritten. */
  private def _stored(root: Path): Vector[String] = {
    val connection = new org.sqlite.JDBC().connect("jdbc:sqlite:" + root.resolve("workflow.sqlite").toUri.toASCIIString + "?mode=ro",
      new java.util.Properties())
    try {
      val statement = connection.createStatement()
      try {
        val tables = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")
        val names = Vector.newBuilder[String]
        try { while (tables.next()) names += tables.getString(1) } finally tables.close()
        names.result().flatMap { name =>
          val rows = statement.executeQuery("SELECT * FROM \"" + name.replace("\"", "\"\"") + "\"")
          val values = Vector.newBuilder[String]
          try { while (rows.next()) (1 to rows.getMetaData.getColumnCount).foreach { column =>
            Option(rows.getString(column)).filter(_.startsWith("cncf.workflow-json.v1:"))
              .foreach(raw => values += raw.stripPrefix("cncf.workflow-json.v1:"))
          } } finally rows.close()
          values.result()
        }
      } finally statement.close()
    } finally connection.close()
  }
  private def _current(live: Live): InstanceRecord = {
    val selected = _stored(live.root).flatMap(WorkflowInstanceRecordJsonV1.decodeC(_, _catalog).toOption)
      .filter(_.identity.value == live.session)
    selected.size shouldBe 1
    selected.head
  }
  private def _receipts(live: Live) = _stored(live.root).flatMap(raw =>
    WorkflowMechanicalTransitionJsonV1.decodeC(raw, _catalog, S.codec).toOption).filter(_.next.identity.value == live.session)
  private def _formal(live: Live): Vector[(String, String)] = _receipts(live).filter(receipt =>
    receipt.reference.identity.contains("validation-result-") && receipt.payload.value.validation.exists(
      _.receipts.lastOption.exists(_.sbt.nonEmpty))).map(receipt => receipt.reference.identity ->
        receipt.payload.value.validation.get.receipts.last.asJson.noSpaces).sortBy(_._1)
  private def _original_work(live: Live, identity: String): RepositorySyncNativeMergeRepair.Request = {
    val inputs = _stored(live.root).flatMap(raw => WorkflowWorkOrderJsonV1.decodeC(raw,
      RepositorySyncNativeMergeRepair.inputCodec).toOption).flatMap(_.current match {
      case order: WorkflowContinuation.WorkOrder[RepositorySyncNativeMergeRepair.Request]
          if order.request.continuationId.value == identity => order.request.input.map(_.value)
      case _ => None
    })
    inputs.size shouldBe 1
    inputs.head
  }
  private def _invocations(root: Path): Vector[String] = {
    val path = root.resolve("sbt-invocations.txt")
    if (Files.exists(path)) Files.readString(path).linesIterator.toVector else Vector.empty
  }
  private def _snapshot(live: Live): (InstanceRecord, String, String, String, String, Vector[String], Vector[(String, String)]) =
    (_current(live), git(live.root, "-C", live.repository.toString, "rev-parse", "HEAD"),
      git(live.root, "-C", live.repository.toString, "write-tree"),
      git(live.root, "-C", live.repository.toString, "rev-parse", "MERGE_HEAD"),
      git(live.root, "-C", live.repository.toString, "status", "--porcelain"), _invocations(live.root), _formal(live))

  private def _reopen(episode: Episode): Live = {
    val root = Path.of(episode.root)
    val host = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(episode.normalJson))
    val plan = episode.plan.as[GoalPhaseOrderedProgression.Plan].toOption.get
    val existinglogs = Files.list(root.resolve("logs"))
    val startinglogindex = try {
      math.max(1000, existinglogs.filter(path => Files.isRegularFile(path) &&
        path.getFileName.toString.matches("[0-9]+\\.(stdout\\.json|stderr)"))
        .mapToInt(path => path.getFileName.toString.takeWhile(_.isDigit).toInt).max().orElse(999) + 1)
    } finally existinglogs.close()
    Live(F.reopenInstalledRepository(root, host, plan, startinglogindex), episode.session)
  }

  private def _scenario(name: String, unchangedRegression: Boolean = false): F.Scenario = {
    val executable = TypedSbtHostPlanFixture.normalExecutable
    val scenario = F.fresh(name, sbtRepair = Some(executable), bootstrapSelectedInput = false)
    val root = scenario.root
    val repository = root.resolve("repository")
    // These are genuine lint inputs in the pre-Start common baseline. F.fresh
    // has only initialized the repository; no Workflow or divergent input exists.
    Files.writeString(repository.resolve("project/plugins.sbt"), "addSbtPlugin(\"ch.epfl.scala\" % \"sbt-scalafix\" % \"0.12.1\")\n")
    Files.copy(Path.of(".scalafix.conf").toAbsolutePath, repository.resolve(".scalafix.conf"))
    Files.writeString(repository.resolve("build.sbt"), """
      |addCommandAlias("fixCheck", "Test / scalafix --check")
      |Global / onLoad := {
      |  val original = (Global / onLoad).value
      |  state => {
      |    val loaded = original(state)
      |    IO.append(file(__INVOCATIONS__), java.lang.ProcessHandle.current().pid().toString + "\n")
      |    loaded
      |  }
      |}
      |""".stripMargin.replace("__INVOCATIONS__", Json.fromString(root.resolve("sbt-invocations.txt").toString).noSpaces),
      StandardOpenOption.APPEND)
    Files.writeString(repository.resolve("src/test/scala/ProductCheck.scala"), _product("initial\n"))
    Files.writeString(repository.resolve("docs/repair.md"), "Initial repair explanation\n")
    if (unchangedRegression) {
      Files.write(repository.resolve("binary-fixture.dat"), Array[Byte](0, 1, 2))
      Files.createSymbolicLink(repository.resolve("symlink-fixture"), Path.of("value.txt"))
      Files.createDirectory(repository.resolve("fixture-directory"))
      Files.writeString(repository.resolve("fixture-directory/entry.txt"), "ordinary baseline entry\n")
      git(root, "-C", repository.toString, "add", "--", "binary-fixture.dat", "symlink-fixture", "fixture-directory/entry.txt")
    }
    git(root, "-C", repository.toString, "add", "--", "project/plugins.sbt", ".scalafix.conf", "build.sbt",
      "src/test/scala/ProductCheck.scala", "docs/repair.md")
    git(root, "-C", repository.toString, "commit", "-m", "Initial ordinary repair-scope and real lint fixture inputs")
    git(root, "-C", repository.toString, "push", root.resolve("upstream.git").toString, "HEAD:refs/heads/main")
    val peer = root.resolve("peer")
    git(root, "clone", "--branch", "main", "--", root.resolve("upstream.git").toString, peer.toString)
    Files.writeString(peer.resolve("value.txt"), "remote product input\n")
    if (!unchangedRegression) Files.writeString(peer.resolve("src/test/scala/ProductCheck.scala"), _product("remote product input\n"))
    Files.writeString(peer.resolve("docs/repair.md"), if (unchangedRegression) "Remote repair explanation\n" else _document)
    if (!unchangedRegression) Files.size(peer.resolve("docs/repair.md")) should be >= 2170000L
    git(root, "-C", peer.toString, "add", "--", "value.txt", "src/test/scala/ProductCheck.scala", "docs/repair.md")
    git(root, "-C", peer.toString, "commit", "-m", "Remote nonconflict check and complete document with conflicting value")
    git(root, "-C", peer.toString, "push", root.resolve("upstream.git").toString, "HEAD:refs/heads/main")
    Files.writeString(repository.resolve("value.txt"), "local product input\n")
    git(root, "-C", repository.toString, "add", "--", "value.txt")
    git(root, "-C", repository.toString, "commit", "-m", "Local conflicting product input")

    val checks = Vector(RepositorySyncNativeMergeValidation.Check("formalCompile", 1, WorkflowSbtValidation.Kind.Compile),
      RepositorySyncNativeMergeValidation.Check("formalTest", 2, WorkflowSbtValidation.Kind.Test),
      RepositorySyncNativeMergeValidation.Check("formalFixCheck", 3, WorkflowSbtValidation.Kind.FixCheck))
    val impact = RepositorySyncProgramImpact.Policy(Vector(RepositorySyncProgramImpact.Coverage(checks.head,
      Vector("value.txt", "src"), Vector("value.txt", "src"), "Actual product inputs require ordinary checks")),
      checks, Vector("value.txt", "src"), "Changed product behavior requires complete Compile/Test/FixCheck", Vector.empty)
    val base = process_plan(root, "Equal", scenario.host.goal)
    val plans = Vector(SbtOperation.Compile, SbtOperation.Test, SbtOperation.FixCheck).zipWithIndex.map { (operation, index) =>
      TypedSbtHostPlanFixture.plan(root, executable, Vector(index + 1), operation = operation)
    }
    def _array_(value: Json, key: String): Vector[Json] = value.hcursor.get[Vector[Json]](key).toOption.get
    val programs = plans.flatMap(_array_(_, "sbtPrograms"))
    plans.foreach(selected => Vector("workspaces", "workspaceGrants").foreach(key =>
      _array_(selected, key).foreach(entry => _array_(base, key).count(_ == entry) shouldBe 1)))
    val nativeplan = base.mapObject(fields => fields.add("sbtPrograms", Json.fromValues(programs))
      .add("grants", fields("grants").get.mapArray(_.map(_.mapObject(grant => grant.add("capabilities",
        grant("capabilities").get.mapArray(existing =>
          (existing ++ programs.map(_.hcursor.get[Json]("capability").toOption.get)).distinct)))))))
    def _large_(value: Json): Json = value.mapObject(fields => fields.add("limits", fields("limits").get.mapObject(limits =>
      Vector("stdinBytes", "stdoutBytes", "artifactBytes").foldLeft(limits)((prior, key) => prior.add(key, Json.fromLong(8388608L)))
        .add("workAreaBytes", Json.fromLong(33554432L)))))
    val plan = Vector("gitPrograms", "gitRepositories").foldLeft(nativeplan)((prior, key) => prior.mapObject(fields =>
      fields.add(key, fields(key).get.mapArray(_.map(_large_)))))
    val configfile = root.resolve(".textus/config.json")
    val config = parse(Files.readString(configfile)).toOption.get
    val parameters = maintenance_parameters() ++ Map("textus.subsystem.process-execution.plan" -> plan.noSpaces,
      "sm-workflow.repository-sync.program-inputs" -> impact.asJson.noSpaces,
      "textus.resource.url.file.roots" -> root.toString, "cncf.resource.read.max-bytes" -> "16777216",
      "cncf.resource.write.max-bytes" -> "8388608")
    Files.writeString(configfile, config.mapObject(fields => parameters.foldLeft(fields.remove("sm-workflow.runtime.definition")) {
      case (prior, (key, value)) => prior.add(key, Json.fromString(value))
    }.add("cncf.git.diff.maximum-bytes", Json.fromLong(8388608L))).noSpaces)
    scenario
  }

  private def _failed(scenario: F.Scenario, changedPaths: Vector[String] = _changed): (Live, F.Result, V.RepositorySyncAdvanceResult) = {
    val client = Live(scenario, "")
    val started = client.repositoryCall("startRepositorySync", Vector("--input",
      "{\"repositoryIdentity\":\"repair-scope-fixture\",\"syncMode\":\"FULL_STATE\"}")).business[V.RepositorySyncStartResult]
    val live = Live(scenario, started.sessionId)
    val waiting = live.advance().business[V.RepositorySyncAdvanceResult]
    waiting.kind shouldBe "Wait"
    waiting.condition shouldBe Some("MergeConflicted")
    val conflict = live.advance().business[V.RepositorySyncAdvanceResult]
    conflict.conflict.get.conflicts.map(_.path) shouldBe Vector("value.txt")
    val result = V.RepositorySyncConflictResult("resolved", conflict.conflict.get.conflicts.map(value =>
      V.RepositorySyncConflictResolution(value.identity, _resolved)), "Fixture-authored resolved product input")
    val reference = V.WorkflowArtifactReference("fixture-conflict-proposal", "1")
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("fixture-conflict-author"), None, None)
    live.repositoryCall("submitRepositorySyncWorkResult", Vector("--sessionId", live.session, "--result", _json(result),
      "--resultReference", _json(reference), "--executionEvidence", _json(execution)))
      .business[V.RepositorySyncConflictSubmissionResult].accepted shouldBe true
    val output = live.advance()
    val repair = output.business[V.RepositorySyncAdvanceResult]
    repair.repair should not be empty
    repair.repair.get.review.changedPaths shouldBe changedPaths
    repair.repair.get.validationFailure shouldBe Some("formalTest")
    val formal = _formal(live)
    formal.size shouldBe 2
    val receipts = formal.map((_, raw) => parse(raw).toOption.get.as[RepositorySyncNativeMergeValidation.Receipt].toOption.get)
    receipts.find(_.identity == "formalCompile").get.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded
    receipts.find(_.identity == "formalTest").get.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Failed
    _invocations(live.root).size shouldBe 2
    (live, output, repair)
  }

  "AUTO-FILE-RS installed unchanged regression continuation" should {
    "qualify baseline files automatically and carry the exact inventory through native issue Submit File and Git effects" in {
      Given("a small isolated installed normal CLI repository whose unchanged baseline check fails only after the product input changes")
      val regression = "src/test/scala/ProductCheck.scala"
      val changed = Vector("docs/repair.md", "value.txt")
      val (live, _, initialrepair) = _failed(_scenario("installed-automatic-unchanged-regression", unchangedRegression = true), changed)
      val initial = _snapshot(live)
      val original = _current(live)
      val old = original.suspension.get.continuationIdentity.value
      initialrepair.repair.get.files.map(_.path) shouldBe changed
      initialrepair.repair.get.review.changedPaths should not contain regression
      git(live.root, "-C", live.repository.toString, "diff", "HEAD", "--", regression) shouldBe ""
      def _prepare_(paths: Vector[String], resource: Boolean = false): F.Result = {
        val input = Json.obj("sessionId" -> live.session.asJson, "paths" -> paths.asJson,
          "rationale" -> "The actual formalTest failed because the baseline ProductCheck expectation still describes the old product input".asJson)
        val parameters = if (resource) Vector("--inputResource", _save(live.root, "automatic-inventory.json", input.noSpaces))
          else Vector("--input", input.noSpaces)
        live.repositoryCall(N.prepareOperation, parameters)
      }
      When("normal description observes the new target before its file inventory is qualified")
      val unprepared = live.repositoryCall(N.describeOperation, Vector("--sessionId", live.session))
        .business[V.RepositorySyncRepairScopeCorrectionDescriptionResult]
      val unpreparedtarget = take(N.targetC(unprepared.target))
      Then("v3 requires preparation with no Human choices adoption or count field and leaves the pending issue untouched")
      unprepared.outcome shouldBe "preparation-required"
      unprepared.choices shouldBe Vector.empty
      unprepared.adoptionOperation shouldBe N.prepareOperation
      unpreparedtarget.correctionPolicy shouldBe N.uncappedPolicy
      unpreparedtarget.maximumFiles shouldBe None
      unpreparedtarget.inventory shouldBe None
      _snapshot(live) shouldBe initial
      When("automatic preparation receives missing untracked outside-root binary symlink and tree selections")
      Vector(Vector("missing.scala"), Vector("../outside.scala"), Vector("binary-fixture.dat"),
        Vector("symlink-fixture"), Vector("fixture-directory")).foreach { paths =>
        _prepare_(paths).exitCode should not be 0
        _snapshot(live) shouldBe initial
      }
      val untracked = live.repository.resolve("untracked.scala")
      Files.writeString(untracked, "untracked fixture text\n")
      try { _prepare_(Vector("untracked.scala")).exitCode should not be 0 } finally Files.delete(untracked)
      val prepared = _prepare_(Vector(regression), resource = true)
      val description = prepared.business[V.RepositorySyncRepairScopeCorrectionDescriptionResult]
      val target = take(N.targetC(description.target))
      Then("the runtime qualifies the unchanged tracked file without any Human file grant and leaves old issue checks and Git state untouched")
      target.correctionPolicy shouldBe N.uncappedPolicy
      target.maximumFiles shouldBe None
      target.changedPaths shouldBe changed
      target.inventory.get.paths shouldBe Vector(regression)
      target.inventory.get.additions.map(_.path) shouldBe Vector(regression)
      description.choices shouldBe N.lifecycleChoices
      description.outcome shouldBe "adoption-required"
      description.adoptionOperation shouldBe N.adoptionOperation
      _snapshot(live) shouldBe initial
      val reference = V.WorkflowArtifactReference("fixture-prior-worker-lifecycle", "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
      val answer = V.RepositorySyncRepairScopeCorrectionAnswer(N.lifecycleChoices.head, Vector.empty,
        "Fixture author supplies that the old worker ended and explicitly accepts its UNKNOWN proposal effects", true, true)
      val input = V.RepositorySyncRepairScopeCorrectionAdoptionInput(live.session, description.target, answer.choice,
        Vector.empty, answer.rationale, reference, true, true, execution)
      def _adopt_(value: V.RepositorySyncRepairScopeCorrectionAdoptionInput): F.Result =
        live.repositoryCall(N.adoptionOperation, Vector("--input", _json(value)))
      When("the distinct lifecycle concern has no independent answer or is replaced by a Human path selection")
      _adopt_(input.copy(target = unprepared.target)).exitCode should not be 0
      _adopt_(input).exitCode should not be 0
      _adopt_(input.copy(paths = Vector(regression))).exitCode should not be 0
      _adopt_(input.copy(priorExecutionEnded = false)).exitCode should not be 0
      _snapshot(live) shouldBe initial
      val preparation = V.WorkflowEvidencePreparationInput("RepositorySyncRepairScopeCorrection", "adoption",
        prepared.invocation.stdout.toUri.toASCIIString,
        _save(live.root, "automatic-lifecycle-answer.json", _json(answer)),
        _save(live.root, "automatic-lifecycle-reference.json", _json(reference)),
        _save(live.root, "automatic-lifecycle-execution.json", _json(execution)), "normal-development-host",
        java.time.Instant.now().toString, "entity:smworkflow.cli.host_evidence", Some("participant"))
      val evidence = live.call("NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", _json(preparation)))
      evidence.exitCode shouldBe 0
      live.scenario.selectPreparedImport(evidence)
      When("the fixture supplies its own actual-shaped lifecycle answer and separate ordinary Advance issues the runtime inventory")
      _adopt_(input).business[V.RepositorySyncRepairScopeCorrectionAdoptionResult].accepted shouldBe true
      val adopted = _current(live)
      adopted.history.take(original.history.size) shouldBe original.history
      val issued = live.advance().business[V.RepositorySyncAdvanceResult]
      val current = _current(live)
      val actual = _original_work(live, current.suspension.get.continuationIdentity.value)
      Then("the native WorkOrder retains the admitted scope and factual diff while old receipts history and repair budget survive")
      issued.repair.get.files.map(_.path) shouldBe Vector(regression)
      actual.admittedInventory shouldBe target.inventory
      actual.review.changedPaths shouldBe changed
      current.history.flatMap(_.suspension.map(_.continuationIdentity.value)).filter(_.startsWith(live.session + ":native-repair:"))
        .map(N.budgetIdentity).distinct shouldBe Vector(old)
      _original_work(live, old).files.map(_.path) shouldBe changed
      _formal(live) shouldBe initial._7
      _invocations(live.root) shouldBe initial._6
      _prepare_(Vector(regression)).exitCode should not be 0
      val resultreference = V.WorkflowArtifactReference("fixture-automatic-regression-proposal", "1")
      val resultevidence = V.WorkflowExecutionEvidence(Vector(resultreference), Some("fixture-regression-author"), None, None)
      val result = V.RepositorySyncNativeMergeRepairResult("repaired", "Update only the assigned baseline regression expectation",
        Vector(V.RepositorySyncNativeMergeRepairFile(regression, _product(_resolved))), None)
      val metadata = Vector("--sessionId", live.session, "--resultReference", _json(resultreference), "--executionEvidence", _json(resultevidence))
      When("native Submit receives an unassigned file before the exact qualified regression proposal")
      live.repositoryCall("submitNativeMergeRepair", metadata ++ Vector("--result", _json(result.copy(
        replacements = Vector(V.RepositorySyncNativeMergeRepairFile("value.txt", "unassigned proposal")))))).exitCode should not be 0
      live.repositoryCall("submitNativeMergeRepair", metadata ++ Vector("--result", _json(result)))
        .business[V.RepositorySyncNativeMergeRepairSubmissionResult].accepted shouldBe true
      val reviewoutput = live.advance()
      val review = reviewoutput.business[V.RepositorySyncAdvanceResult]
      Then("owning Advance materializes and stages the regression with matching full-invocation ordinals and runs fresh real checks")
      review.review should not be empty
      _invocations(live.root).size shouldBe initial._6.size + 3
      _formal(live).filter(initial._7.contains) shouldBe initial._7
      Files.readString(live.repository.resolve(regression)) shouldBe _product(_resolved)
      git(live.root, "-C", live.repository.toString, "diff", "--", regression) shouldBe ""
      val verdict = V.RepositorySyncNativeMergeReviewResult("accept", "Fixture independent reviewer accepts this actual new candidate", Vector.empty)
      val reviewreference = V.WorkflowArtifactReference("fixture-automatic-independent-review", "1")
      val reviewexecution = V.WorkflowExecutionEvidence(Vector(reviewreference), Some("fixture-independent-reviewer"), None, None)
      val reviewpreparation = V.WorkflowEvidencePreparationInput("RepositorySync", "review", reviewoutput.invocation.stdout.toUri.toASCIIString,
        _save(live.root, "automatic-review.json", _json(verdict)), _save(live.root, "automatic-review-reference.json", _json(reviewreference)),
        _save(live.root, "automatic-review-execution.json", _json(reviewexecution)), "normal-development-host", java.time.Instant.now().toString,
        "entity:smworkflow.cli.host_evidence", None)
      val reviewevidence = live.call("NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", _json(reviewpreparation)))
      reviewevidence.exitCode shouldBe 0
      live.scenario.selectPreparedImport(reviewevidence)
      When("separate fixture-owned independent review completes the same Session against its isolated local bare upstream")
      live.repositoryCall("submitNativeMergeReview", Vector("--sessionId", live.session, "--result", _json(verdict),
        "--resultReference", _json(reviewreference), "--executionEvidence", _json(reviewexecution)))
        .business[V.RepositorySyncNativeMergeReviewSubmissionResult].accepted shouldBe true
      val terminal = live.advance().business[V.RepositorySyncAdvanceResult]
      Then("the original Session reaches its ordinary terminal state without rewriting old evidence")
      terminal.kind shouldBe "Terminal"
      _current(live).lifecycle shouldBe Lifecycle.Completed
      _formal(live).filter(initial._7.contains) shouldBe initial._7
    }
  }

  "Installed RepositorySync repair scope upgrade" should {
    "capture legacy failed-check repair" in {
      Given("the previous normally installed CAR and a fresh real local bare/peer fixture with actual Compile/Test/FixCheck inputs")
      val path = _episode_path
      Files.exists(path) shouldBe false
      val scenario = _scenario("installed-legacy-repair-scope")
      When("normal Start conflict Submit and Advance retain a real successful Compile and formal Test failure")
      val (live, output, repair) = _failed(scenario)
      Then("the actually old installed product issues only the old conflict path while all three changed files remain in its candidate")
      repair.repair.get.files.map(_.path) shouldBe Vector("value.txt")
      val current = _current(live)
      val old = current.suspension.get.continuationIdentity.value
      _original_work(live, old).files.map(_.path) shouldBe Vector("value.txt")
      val episode = Episode(live.root.toString, ThreeWorkflowRuntimeParameters.encode(scenario.host), scenario.plan.asJson,
        live.session, take(WorkflowInstanceRecordJsonV1.encodeC(current)),
        git(live.root, "-C", live.repository.toString, "rev-parse", "HEAD"),
        git(live.root, "-C", live.repository.toString, "write-tree"),
        git(live.root, "-C", live.repository.toString, "rev-parse", "MERGE_HEAD"), output.invocation.stdout.toString,
        old, _formal(live), _invocations(live.root))
      Files.writeString(path, episode.asJson.noSpaces, UTF_8, StandardOpenOption.CREATE_NEW)
      Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"))
      _current(live) shouldBe current
      _invocations(live.root) shouldBe episode.invocations
    }

    "continue retained failed-check repair" in {
      Given("the exact retained legacy episode and the newly published normal installed CAR using the same native datastore and physical repository")
      val episode = parse(Files.readString(_episode_path)).toOption.get.as[Episode].toOption.get
      val root = Path.of(episode.root)
      val live = _reopen(episode)
      val scenario = live.scenario
      val original = take(WorkflowInstanceRecordJsonV1.decodeC(episode.originalRecord, _catalog))
      _current(live) shouldBe original
      git(root, "-C", live.repository.toString, "rev-parse", "HEAD") shouldBe episode.head
      git(root, "-C", live.repository.toString, "write-tree") shouldBe episode.indexTree
      git(root, "-C", live.repository.toString, "rev-parse", "MERGE_HEAD") shouldBe episode.mergeHead
      _formal(live) shouldBe episode.formalReceipts
      _invocations(root) shouldBe episode.invocations
      val initial = _snapshot(live)
      When("the new installed public operation describes the actual old narrow pending issue without checks or mutation")
      val unprepared = live.repositoryCall(N.describeOperation, Vector("--sessionId", live.session))
        .business[V.RepositorySyncRepairScopeCorrectionDescriptionResult]
      val unpreparedtarget = take(N.targetC(unprepared.target))
      Then("the complete current scope includes the nonconflict check and large document and the original issue remains pending")
      unpreparedtarget.changedPaths shouldBe _changed
      unpreparedtarget.oldAssignedPaths shouldBe Vector("value.txt")
      unpreparedtarget.correctionPolicy shouldBe N.uncappedPolicy
      unpreparedtarget.maximumFiles shouldBe None
      unpreparedtarget.inventory shouldBe None
      unprepared.outcome shouldBe "preparation-required"
      unprepared.choices shouldBe Vector.empty
      unprepared.adoptionOperation shouldBe N.prepareOperation
      _snapshot(live) shouldBe initial
      When("runtime preparation qualifies the existing check and document inventory for that same failed candidate")
      val preparation = Json.obj("sessionId" -> live.session.asJson, "paths" -> _chosen.asJson,
        "rationale" -> "The retained formalTest failure requires the existing check and its complete repair explanation".asJson)
      val described = live.repositoryCall(N.prepareOperation, Vector("--input", preparation.noSpaces))
      val description = described.business[V.RepositorySyncRepairScopeCorrectionDescriptionResult]
      val target = take(N.targetC(description.target))
      Then("only the prepared fixed inventory offers the existing lifecycle answer without a Human path selection")
      target shouldBe unpreparedtarget.copy(inventory = target.inventory)
      target.inventory.get.paths shouldBe _chosen
      description.outcome shouldBe "adoption-required"
      description.choices shouldBe N.lifecycleChoices
      description.adoptionOperation shouldBe N.adoptionOperation
      _snapshot(live) shouldBe initial
      val directory = Files.createTempDirectory(root, "fixture-authored-worker-lifecycle-")
      val reference = V.WorkflowArtifactReference(directory.getFileName.toString, "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
      val answer = V.RepositorySyncRepairScopeCorrectionAnswer(N.lifecycleChoices.head, Vector.empty,
        "Fixture-authored lifecycle direction: the proposal worker ended; original proposal effects remain UNKNOWN", true, true)
      val input = V.RepositorySyncRepairScopeCorrectionAdoptionInput(live.session, description.target, answer.choice,
        answer.paths, answer.rationale, reference, true, true, execution)
      def _adopt_(value: V.RepositorySyncRepairScopeCorrectionAdoptionInput): F.Result =
        live.repositoryCall(N.adoptionOperation, Vector("--input", _json(value)))
      When("absent unknown stale crossed paths participant reference evidence or lifecycle assertions are supplied")
      _adopt_(input.copy(target = unprepared.target)).exitCode should not be 0
      val missing = parse(_json(input)).toOption.get.mapObject(_.remove("target"))
      val unknown = parse(_json(input)).toOption.get.mapObject(_.add("unknown", Json.True))
      Vector(missing, unknown).foreach { value =>
        live.repositoryCall(N.adoptionOperation, Vector("--input", value.noSpaces)).exitCode should not be 0
        _snapshot(live) shouldBe initial
      }
      val invalid = Vector(input.copy(target = N.targetRecord(target.copy(frontierRevision = "999", expectedRevision = "999"))),
        input.copy(sessionId = "foreign-session"), input.copy(paths = Vector("foreign.txt")),
        input.copy(executionEvidence = execution.copy(workerIdentity = Some("foreign-participant"))),
        input.copy(priorExecutionEnded = false), input.copy(acceptOriginalUncertainty = false),
        input.copy(reference = reference.copy(identity = "missing-independent-human")), input)
      invalid.foreach { value =>
        _adopt_(value).exitCode should not be 0
        _snapshot(live) shouldBe initial
      }
      Then("every refusal retains the exact native record Git candidate and actual check invocation count")
      _current(live) shouldBe original
      def _provision_(selectedanswer: V.RepositorySyncRepairScopeCorrectionAnswer, selectedreference: V.WorkflowArtifactReference): Unit = {
        val preparation = V.WorkflowEvidencePreparationInput("RepositorySyncRepairScopeCorrection", "adoption",
          described.invocation.stdout.toUri.toASCIIString,
          _save(directory, selectedreference.identity + "-answer.json", _json(selectedanswer)),
          _save(directory, selectedreference.identity + "-reference.json", _json(selectedreference)),
          _save(directory, selectedreference.identity + "-execution.json", _json(execution.copy(references = Vector(selectedreference)))),
          "normal-development-host", java.time.Instant.now().toString, "entity:smworkflow.cli.host_evidence", Some("participant"))
        val prepared = live.call("NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", _json(preparation)))
        withClue(prepared.invocation.logs) { prepared.exitCode shouldBe 0 }
        scenario.selectPreparedImport(prepared)
      }
      When("a separately supplied fixture-authored Defer answer is prepared and adopted")
      val deferreference = reference.copy(identity = reference.identity + "-defer")
      val deferred = answer.copy(choice = "Defer", paths = Vector.empty, priorExecutionEnded = false, acceptOriginalUncertainty = false)
      _provision_(deferred, deferreference)
      _adopt_(input.copy(choice = "Defer", paths = Vector.empty, reference = deferreference,
        priorExecutionEnded = false, acceptOriginalUncertainty = false)).business[V.RepositorySyncRepairScopeCorrectionAdoptionResult].accepted shouldBe false
      Then("Defer performs no native transition or Git effect and executes no check")
      _snapshot(live) shouldBe initial
      When("normal passive preparation supplies the exact adopting answer and its independent fixture-owned evidence")
      _provision_(answer, reference)
      _adopt_(input.copy(reference = reference.copy(revision = "2"))).exitCode should not be 0
      _snapshot(live) shouldBe initial
      _adopt_(input).business[V.RepositorySyncRepairScopeCorrectionAdoptionResult].accepted shouldBe true
      val adopted = _current(live)
      Then("native atomic supersession retains old history and check receipts while UNKNOWN effects remain acknowledged")
      adopted.history.take(original.history.size) shouldBe original.history
      adopted.suspension shouldBe None
      adopted.revision shouldBe original.revision.next
      _formal(live) shouldBe episode.formalReceipts
      _invocations(root) shouldBe episode.invocations
      _original_work(live, episode.originalWorkIdentity).files.map(_.path) shouldBe Vector("value.txt")
      val adoptedstate = _snapshot(live)
      _adopt_(input).business[V.RepositorySyncRepairScopeCorrectionAdoptionResult].outcome shouldBe "already-adopted"
      _adopt_(input.copy(paths = Vector(_chosen.head))).exitCode should not be 0
      _snapshot(live) shouldBe adoptedstate
      When("one separate ordinary Advance issues the corrected exact two-file assignment")
      val issued = live.advance().business[V.RepositorySyncAdvanceResult]
      val work = issued.repair.get
      Then("only the runtime-qualified current files are assigned once without increasing the original repair budget")
      work.files.map(_.path) shouldBe _chosen
      val current = _current(live)
      val identities = current.history.flatMap(_.suspension.map(_.continuationIdentity.value))
        .filter(_.startsWith(live.session + ":native-repair:")).map(N.budgetIdentity).distinct
      identities shouldBe Vector(target.oldContinuationId)
      _formal(live) shouldBe episode.formalReceipts
      _invocations(root) shouldBe episode.invocations
      live.advance().business[V.RepositorySyncAdvanceResult] shouldBe issued

      val result = V.RepositorySyncNativeMergeRepairResult("repaired", "Fixture repairs only the issued ProductCheck expectation and genuine document explanation",
        Vector(V.RepositorySyncNativeMergeRepairFile(_chosen.head, _document + "\n修復後の実際の入力と検証を記録する\n"),
          V.RepositorySyncNativeMergeRepairFile(_chosen(1), _product(_resolved))), None)
      val resource = Path.of(java.net.URI.create(_save(root, "complete-repair-result.json", _json(result))))
      Files.size(resource) should be > 2170000L
      val resultreference = V.WorkflowArtifactReference("fixture-complete-repair-proposal", "1")
      val resultevidence = V.WorkflowExecutionEvidence(Vector(resultreference), Some("fixture-repair-author"), None, None)
      val metadata = Vector("--sessionId", live.session, "--resultReference", _json(resultreference), "--executionEvidence", _json(resultevidence))
      val assigned = _snapshot(live)
      When("a proposal for the original narrow issue is submitted to the corrected continuation")
      val oldproposal = V.RepositorySyncNativeMergeRepairResult("repaired", "Fixture-authored proposal for the superseded issue",
        Vector(V.RepositorySyncNativeMergeRepairFile("value.txt", _resolved + "old proposal\n")), None)
      live.repositoryCall("submitNativeMergeRepair", metadata ++ Vector("--result", _json(oldproposal))).exitCode should not be 0
      Then("the old assignment cannot satisfy the new exact two-file result contract")
      _snapshot(live) shouldBe assigned
      val malformed = _save(root, "malformed-repair.json", "{")
      val invalidutf8 = root.resolve("invalid-utf8-repair.json")
      Files.write(invalidutf8, Array(0xc3.toByte, 0x28.toByte))
      val oversized = root.resolve("oversized-repair.json")
      Files.write(oversized, Array.fill[Byte](16777217)(' '.toByte))
      val outside = Files.createTempFile(root.getParent, "outside-repair-root-", ".json")
      Files.writeString(outside, _json(result))
      When("the complete repair Resource boundary receives seven refused transports before the valid result")
      try {
        val refused = Vector(Vector("--resultResource", root.resolve("missing-repair.json").toUri.toASCIIString),
          Vector("--resultResource", outside.toUri.toASCIIString), Vector("--resultResource", malformed),
          Vector("--resultResource", invalidutf8.toUri.toASCIIString), Vector("--resultResource", oversized.toUri.toASCIIString),
          Vector("--result", "{}", "--resultResource", resource.toUri.toASCIIString), Vector.empty[String])
        refused.foreach { arguments =>
          live.repositoryCall("submitNativeMergeRepair", metadata ++ arguments).exitCode should not be 0
          _snapshot(live) shouldBe assigned
        }
      } finally Files.deleteIfExists(outside)
      Then("all seven refusals preserve the assigned native record candidate and check count")
      _snapshot(live) shouldBe assigned
      When("the normal installed Submit reads the complete result Resource once and accepts only its current issued files")
      live.repositoryCall("submitNativeMergeRepair", metadata ++ Vector("--resultResource", resource.toUri.toASCIIString))
        .business[V.RepositorySyncNativeMergeRepairSubmissionResult].accepted shouldBe true
      _invocations(root) shouldBe episode.invocations
      _finish_admitted(live, episode, result)
    }

    "finish admitted repair after normal host upgrade" in {
      Given("the same retained legacy Session whose complete corrected repair proposal is already admitted")
      val episode = parse(Files.readString(_episode_path)).toOption.get.as[Episode].toOption.get
      val live = _reopen(episode)
      _current(live).currentProgression shouldBe Some(ProgressionReference("sync.RepairProposalAdmitted"))
      _formal(live) shouldBe episode.formalReceipts
      _invocations(live.root) shouldBe episode.invocations
      _original_work(live, episode.originalWorkIdentity).files.map(_.path) shouldBe Vector("value.txt")
      val result = take(new org.goldenport.record.io.RecordDecoder()
        .json(Files.readString(live.root.resolve("complete-repair-result.json")))
        .flatMap(summon[org.goldenport.convert.ValueReader[V.RepositorySyncNativeMergeRepairResult]].readC))
      result.replacements.map(_.path) shouldBe _chosen
      When("ordinary continuation resumes the actual admitted proposal without repeating Start adoption or submission")
      _finish_admitted(live, episode, result)
    }
  }

  private def _finish_admitted(live: Live, episode: Episode, result: V.RepositorySyncNativeMergeRepairResult): Unit = {
    val root = live.root
    val reviewed = live.advance()
    val review = reviewed.business[V.RepositorySyncAdvanceResult]
    Then("the genuinely new candidate has its own successful Compile Test and real Test/scalafix checks before independent review")
    review.review should not be empty
    _invocations(root).size shouldBe episode.invocations.size + 3
    val fresh = _formal(live).filterNot(episode.formalReceipts.contains)
      .map((_, raw) => parse(raw).toOption.get.as[RepositorySyncNativeMergeValidation.Receipt].toOption.get)
    fresh.map(_.identity).toSet shouldBe Set("formalCompile", "formalTest", "formalFixCheck")
    fresh.foreach(_.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded)
    _formal(live).filter(episode.formalReceipts.contains) shouldBe episode.formalReceipts
    Files.readString(live.repository.resolve("value.txt")) shouldBe _resolved
    Files.readString(live.repository.resolve(_chosen.head)) shouldBe result.replacements.head.text
    Files.readString(live.repository.resolve(_chosen(1))) shouldBe result.replacements(1).text
    When("a fixture-authored independent reviewer supplies normal prepared review evidence for that actual new candidate")
    val verdict = V.RepositorySyncNativeMergeReviewResult("accept", "Independent fixture reviewer examined the actual complete changed candidate; no live acceptance claimed", Vector.empty)
    val reviewreference = V.WorkflowArtifactReference("fixture-new-candidate-independent-review", "1")
    val reviewexecution = V.WorkflowExecutionEvidence(Vector(reviewreference), Some("fixture-independent-reviewer"), None, None)
    val preparation = V.WorkflowEvidencePreparationInput("RepositorySync", "review", reviewed.invocation.stdout.toUri.toASCIIString,
      _save(root, "review-result.json", _json(verdict)), _save(root, "review-reference.json", _json(reviewreference)),
      _save(root, "review-execution.json", _json(reviewexecution)), "normal-development-host", java.time.Instant.now().toString,
      "entity:smworkflow.cli.host_evidence", None)
    val prepared = live.call("NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", _json(preparation)))
    withClue(prepared.invocation.logs) { prepared.exitCode shouldBe 0 }
    live.scenario.selectPreparedImport(prepared)
    live.repositoryCall("submitNativeMergeReview", Vector("--sessionId", live.session, "--result", _json(verdict),
      "--resultReference", _json(reviewreference), "--executionEvidence", _json(reviewexecution)))
      .business[V.RepositorySyncNativeMergeReviewSubmissionResult].accepted shouldBe true
    val terminal = live.advance().business[V.RepositorySyncAdvanceResult]
    Then("native commit local-bare push final fetch converge in the original Session and later collection is passive")
    terminal.kind shouldBe "Terminal"
    val completed = _current(live)
    completed.lifecycle shouldBe Lifecycle.Completed
    val tip = git(root, "-C", live.repository.toString, "rev-parse", "HEAD")
    git(root, "--git-dir=" + root.resolve("upstream.git"), "rev-parse", "refs/heads/main") shouldBe tip
    git(root, "-C", live.repository.toString, "status", "--porcelain") shouldBe ""
    val invocations = _invocations(root)
    val receipts = _formal(live)
    live.advance().business[V.RepositorySyncAdvanceResult] shouldBe terminal
    _current(live) shouldBe completed
    _invocations(root) shouldBe invocations
    _formal(live) shouldBe receipts
    When("a separate fresh small candidate reaches a failed selected check under the new normal installed runtime")
    val (freshlive, _, freshrepair) = _failed(_scenario("installed-fresh-complete-repair-scope"))
    Then("fresh check-origin issuance already includes all three actual changed text files without a correction")
    freshrepair.repair.get.files.map(_.path) shouldBe _changed
    _formal(freshlive).size shouldBe 2
  }
}
