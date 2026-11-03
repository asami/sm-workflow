package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import org.goldenport.cncf.processexecution.SbtOperation
import org.goldenport.cncf.workflow.{WorkflowInstanceRecordJsonV1, WorkflowMechanicalTransitionJsonV1}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.record.RecordPresentable
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import RepositorySyncNativeMergeValidation as C
import RepositorySyncNativeProgressStore as S
import RepositorySyncNativeMergeReview as M
import RepositorySyncValidationContinuation as N
import RepositorySyncReviewInputContinuation as R
import SplitPhaseExplicitPartitionFixture.take

/** Two separately selected publication stages share one genuine native Session.
  * Independent semantic answers are specification-owned evidence, never live
  * provider acceptance. SQLite is observed read-only, never seeded or repaired.
  * @since Nov. 3, 2026
  * @version Nov. 3, 2026
  */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeRepositoryReviewInputContinuationCliSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with RepositorySyncMaintenanceFixture {
  private val _checks = Vector(C.Check("compile1", 1, WorkflowSbtValidation.Kind.Compile),
    C.Check("test2", 2, WorkflowSbtValidation.Kind.Test), C.Check("lint3", 3, WorkflowSbtValidation.Kind.FixCheck))
  private val _unknown = Vector("README.md", "scripts/probe.py", "skills/probe/SKILL.md")
  private val _consumed = Vector("value.txt", "src", "build.sbt", "project", ".scalafix.conf", "scripts", "skills")
  private val _rationale = "Specification-owned independent review: the complete parent diffs show the resolved value and Scala value agree. " +
    "README, script and skill classification remains Unexamined; this decision-needed result accepts no path or partial semantic coverage."
  private lazy val _catalog = take(WorkflowInstanceRecordJsonV1.catalogC(Vector(take(RepositorySyncDefinitionBinding.bindC))))
  private final case class Episode(root: String, normalJson: Json, plan: Json, session: String, originalRecord: String,
    head: String, indexTree: String, mergeHead: String, reviewStdout: String, continuationId: String, result: String, reference: String,
    execution: String, nativeRows: Vector[String], formalReceipts: Vector[(String, String, String)],
    invocations: Vector[String], commands: Vector[String], lintEntries: Vector[String])
  private final case class Live(scenario: F.Scenario, session: String) {
    def root: Path = scenario.root
    def repository: Path = Path.of(scenario.host.target.root)
    def call(service: String, operation: String, parameters: Vector[String]): F.Result = scenario.callInstalled(
      _installed, service, operation, parameters, None, Some(root.resolve(".textus/assembly.yaml")))
    def repositoryCall(operation: String, parameters: Vector[String]): F.Result = call("RepositorySyncService", operation, parameters)
    def advance(): F.Result = repositoryCall("advanceRepositorySync", Vector("--sessionId", session))
  }
  private def _installed: Path = {
    val path = Path.of(sys.props.getOrElse("smworkflow.spec.installedCommand", fail("Select the normal absolute installed command")))
    path.isAbsolute shouldBe true
    path.normalize shouldBe path
    Files.isRegularFile(path) shouldBe true
    Files.isExecutable(path) shouldBe true
    path
  }
  private def _episode_path: Path = {
    val path = Path.of(sys.props.getOrElse("smworkflow.spec.reviewInputEpisode", fail("Select the private absolute reviewInputEpisode file")))
    path.isAbsolute shouldBe true
    path.normalize shouldBe path
    Files.isDirectory(path.getParent) shouldBe true
    path
  }
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def _save(root: Path, name: String, value: String): String = {
    val path = root.resolve(name)
    Files.writeString(path, value, UTF_8, StandardOpenOption.CREATE_NEW)
    path.toUri.toASCIIString
  }
  private def _lines(root: Path, name: String): Vector[String] = {
    val path = root.resolve(name)
    if (Files.exists(path)) Files.readString(path).linesIterator.filter(_.nonEmpty).toVector else Vector.empty
  }
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
  private def _requests(live: Live): Vector[M.Request] = {
    def _nested_(value: Json): Vector[M.Request] = M.inputCodec.decode(value).toOption.toVector ++
      value.asObject.toVector.flatMap(_.values.toVector.flatMap(_nested_)) ++
      value.asArray.toVector.flatMap(_.flatMap(_nested_))
    _stored(live.root).flatMap(raw => parse(raw).toOption.toVector.flatMap(_nested_))
      .filter(_.instanceIdentity == live.session).distinct
  }
  private def _receipts(live: Live) = _stored(live.root).flatMap(raw =>
    WorkflowMechanicalTransitionJsonV1.decodeC(raw, _catalog, S.codec).toOption).filter(_.next.identity.value == live.session)
  private def _formal(live: Live): Vector[(String, String, String)] = _receipts(live).filter(receipt =>
    receipt.reference.identity.contains("validation-result-") && receipt.payload.value.validation.exists(
      _.receipts.lastOption.exists(_.sbt.nonEmpty))).map(receipt => (receipt.reference.identity,
        receipt.reference.revision, receipt.payload.value.validation.get.receipts.last.asJson.noSpaces)).sortBy(_._1)
  private def _snapshot(live: Live) = (take(WorkflowInstanceRecordJsonV1.encodeC(_current(live))),
    git(live.root, "-C", live.repository.toString, "rev-parse", "HEAD"),
    git(live.root, "-C", live.repository.toString, "write-tree"),
    git(live.root, "-C", live.repository.toString, "rev-parse", "MERGE_HEAD"),
    git(live.root, "-C", live.repository.toString, "status", "--porcelain"), _formal(live),
    _lines(live.root, "sbt-invocations.txt"), _lines(live.root, "sbt-command-lines.txt"), _lines(live.root, "lint-entered.txt"))
  private def _impact(checks: Vector[C.Check] = _checks): RepositorySyncProgramImpact.Policy =
    RepositorySyncProgramImpact.Policy(checks.map(check => RepositorySyncProgramImpact.Coverage(check,
      _consumed, _consumed, "The real selected product checks consume declared source script and skill inputs")),
      checks, Vector("value.txt", "src"), "Actual source delta requires the existing full validation", Vector.empty)
  private def _configure(scenario: F.Scenario, corrected: Boolean, demote: Boolean = false,
    checks: Vector[C.Check] = _checks, impact: Option[RepositorySyncProgramImpact.Policy] = None): Unit = {
    val root = scenario.root
    val base = process_plan(root, "Equal", scenario.host.goal)
    def _array_(value: Json, key: String): Vector[Json] = value.hcursor.get[Vector[Json]](key).toOption.get
    val plans = Vector(SbtOperation.Compile, SbtOperation.Test, SbtOperation.FixCheck).zipWithIndex.map {
      (operation, index) => TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable,
        Vector(index + 1), operation = operation, executionTimeoutMillis = 180000L)
    }
    val programs = plans.flatMap(_array_(_, "sbtPrograms"))
    val plan = base.mapObject(fields => fields.add("sbtPrograms", Json.fromValues(programs))
      .add("workspaces", Json.fromValues((_array_(base, "workspaces") ++ plans.flatMap(_array_(_, "workspaces"))).distinct))
      .add("workspaceGrants", Json.fromValues((_array_(base, "workspaceGrants") ++ plans.flatMap(_array_(_, "workspaceGrants"))).distinct))
      .add("grants", fields("grants").get.mapArray(_.map(_.mapObject(grant => grant.add("capabilities",
        grant("capabilities").get.mapArray(existing => (existing ++ programs.map(_.hcursor.get[Json]("capability").toOption.get)).distinct)))))))
    val documents = Vector("docs") ++ Option.when(corrected)("README.md") ++ (if (demote) Vector("scripts", "skills") else Vector.empty)
    val consumed = _consumed.filterNot(path => path == "scripts" || path == "skills") ++
      (if (corrected && !demote) Vector("scripts", "skills") else Vector.empty)
    val inputs = RepositorySyncDocumentInputs.Policy(documents, consumed, "Explicit proposed classification of the actual tiny fixture inputs")
    val file = root.resolve(".textus/config.json")
    val config = parse(Files.readString(file)).toOption.get
    val parameters = maintenance_parameters() ++ Map("textus.subsystem.process-execution.plan" -> plan.noSpaces,
      "sm-workflow.repository-sync.program-inputs" -> impact.getOrElse(_impact(checks)).asJson.noSpaces,
      "sm-workflow.repository-sync.document-inputs" -> inputs.asJson.noSpaces,
      "textus.resource.url.file.roots" -> root.toString)
    Files.writeString(file, config.mapObject(fields => parameters.foldLeft(fields.remove("sm-workflow.runtime.definition")) {
      case (prior, (key, value)) => prior.add(key, Json.fromString(value))
    }).noSpaces)
  }
  private def _scenario(): F.Scenario = {
    sys.props.get("smworkflow.spec.sbt") should not be empty
    val scenario = F.fresh("installed-review-input", sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable), bootstrapSelectedInput = false)
    val root = scenario.root
    val repository = root.resolve("repository")
    Files.createDirectories(repository.resolve("src/main/scala"))
    Files.createDirectories(repository.resolve("scripts"))
    Files.createDirectories(repository.resolve("skills/probe"))
    Files.writeString(repository.resolve("src/main/scala/ReviewInput.scala"), "object ReviewInput { val value = 0 }\n")
    Files.writeString(repository.resolve("README.md"), "# Probe\nInitial fixture documentation.\n")
    Files.writeString(repository.resolve("scripts/probe.py"), "VALUE = 0\n")
    Files.writeString(repository.resolve("skills/probe/SKILL.md"), "# Probe skill\nRead value 0.\n")
    Files.writeString(repository.resolve("src/test/scala/ProductCheck.scala"), """object ProductCheck {
      |  def main(args: Array[String]): Unit = {
      |    assert(java.nio.file.Files.readString(java.nio.file.Paths.get("value.txt")) == "resolved product input\n")
      |    assert(ReviewInput.value == 1)
      |    assert(java.nio.file.Files.readString(java.nio.file.Paths.get("scripts/probe.py")) == "VALUE = 1\n")
      |    assert(java.nio.file.Files.readString(java.nio.file.Paths.get("skills/probe/SKILL.md")) == "# Probe skill\nRead value 1.\n")
      |    println("ACTUAL_REVIEW_INPUT_PRODUCT_TEST_PASSED")
      |  }
      |}
      |""".stripMargin)
    Files.writeString(repository.resolve("project/plugins.sbt"), "addSbtPlugin(\"ch.epfl.scala\" % \"sbt-scalafix\" % \"0.12.1\")\n")
    Files.writeString(repository.resolve(".scalafix.conf"), "rules = [DisableSyntax]\nDisableSyntax.noFinalize = true\n")
    Files.writeString(repository.resolve("build.sbt"), """
      |lazy val reviewInputLintMarker = taskKey[Unit]("Observe ordinary selected lint entry")
      |reviewInputLintMarker := IO.append(file(__LINT__), java.lang.ProcessHandle.current().pid().toString + "\n")
      |addCommandAlias("fixCheck", ";reviewInputLintMarker;Compile / scalafix --check")
      |Global / onLoad := {
      |  val original = (Global / onLoad).value
      |  state => {
      |    val loaded = original(state)
      |    IO.append(file(__INVOCATIONS__), java.lang.ProcessHandle.current().pid().toString + "\n")
      |    IO.append(file(__COMMANDS__), state.remainingCommands.map(_.commandLine).mkString("", "\n", "\n"))
      |    loaded
      |  }
      |}
      |""".stripMargin.replace("__LINT__", root.resolve("lint-entered.txt").toString.asJson.noSpaces)
      .replace("__INVOCATIONS__", root.resolve("sbt-invocations.txt").toString.asJson.noSpaces)
      .replace("__COMMANDS__", root.resolve("sbt-command-lines.txt").toString.asJson.noSpaces), StandardOpenOption.APPEND)
    git(root, "-C", repository.toString, "add", "--", "src", "scripts", "skills", "README.md", "project/plugins.sbt", ".scalafix.conf", "build.sbt")
    git(root, "-C", repository.toString, "commit", "-m", "Tiny actual review input consumption and ordinary fixed checks")
    git(root, "-C", repository.toString, "push", root.resolve("upstream.git").toString, "HEAD:refs/heads/main")
    val peer = root.resolve("peer")
    git(root, "clone", "--branch", "main", "--", root.resolve("upstream.git").toString, peer.toString)
    Files.writeString(peer.resolve("value.txt"), "remote product input\n")
    Files.writeString(peer.resolve("src/main/scala/ReviewInput.scala"), "object ReviewInput { val value = 1 }\n")
    Files.writeString(peer.resolve("README.md"), "# Probe\nThe fixture reads value 1.\n")
    Files.writeString(peer.resolve("scripts/probe.py"), "VALUE = 1\n")
    Files.writeString(peer.resolve("skills/probe/SKILL.md"), "# Probe skill\nRead value 1.\n")
    git(root, "-C", peer.toString, "add", "--", "value.txt", "src", "README.md", "scripts", "skills")
    git(root, "-C", peer.toString, "commit", "-m", "Remote actual product and unknown classification inputs")
    git(root, "-C", peer.toString, "push", root.resolve("upstream.git").toString, "HEAD:refs/heads/main")
    Files.writeString(repository.resolve("value.txt"), "local product input\n")
    git(root, "-C", repository.toString, "add", "--", "value.txt")
    git(root, "-C", repository.toString, "commit", "-m", "Local conflicting input")
    _configure(scenario, corrected = false)
    scenario
  }
  private def _prepare(live: Live, operation: String, issued: F.Result, result: String,
    reference: V.WorkflowArtifactReference, execution: V.WorkflowExecutionEvidence): Unit = {
    val directory = Files.createTempDirectory(live.root, "independent-review-input-")
    val input = V.WorkflowEvidencePreparationInput("RepositorySync", operation, issued.invocation.stdout.toUri.toASCIIString,
      _save(directory, "result.json", result), _save(directory, "reference.json", _json(reference)),
      _save(directory, "execution.json", _json(execution)), "normal-development-host", java.time.Instant.now().toString,
      "entity:smworkflow.cli.host_evidence", Option.when(operation == "validation-continuation")("participant"))
    val prepared = live.call("NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", _json(input)))
    withClue(prepared.invocation.logs) { prepared.exitCode shouldBe 0 }
    live.scenario.selectPreparedImport(prepared)
  }
  private def _reopen(episode: Episode): Live = {
    val root = Path.of(episode.root)
    val host = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(episode.normalJson))
    val plan = episode.plan.as[GoalPhaseOrderedProgression.Plan].toOption.get
    val logs = Files.list(root.resolve("logs"))
    val next = try { math.max(1000, logs.filter(path => Files.isRegularFile(path) &&
      path.getFileName.toString.matches("[0-9]+\\.(stdout\\.json|stderr)"))
      .mapToInt(path => path.getFileName.toString.takeWhile(_.isDigit).toInt).max().orElse(999) + 1) } finally logs.close()
    Live(F.reopenInstalledRepository(root, host, plan, next), episode.session)
  }
  private def _retained(live: Live, episode: Episode): Unit = {
    _formal(live) shouldBe episode.formalReceipts
    _lines(live.root, "sbt-invocations.txt") shouldBe episode.invocations
    _lines(live.root, "sbt-command-lines.txt") shouldBe episode.commands
    _lines(live.root, "lint-entered.txt") shouldBe episode.lintEntries
    val original = take(WorkflowInstanceRecordJsonV1.decodeC(episode.originalRecord, _catalog))
    _current(live).history.take(original.history.size) shouldBe original.history
    val currentrows = _stored(live.root)
    episode.nativeRows.filterNot(raw => WorkflowInstanceRecordJsonV1.decodeC(raw, _catalog).toOption.nonEmpty)
      .foreach(raw => currentrows should contain(raw))
  }

  "Installed retained review input continuation" should {
    "capture REVIEW-INPUT actual saved decision-needed" in {
      Given("the old normal published CAR, a new tiny real local/bare Git fixture and one unused private episode path")
      Files.exists(_episode_path) shouldBe false
      val scenario = _scenario()
      val started = Live(scenario, "").repositoryCall("startRepositorySync", Vector("--input",
        "{\"repositoryIdentity\":\"review-input-fixture\",\"syncMode\":\"FULL_STATE\"}")).business[V.RepositorySyncStartResult]
      val live = Live(scenario, started.sessionId)
      When("ordinary Advance issues the real value conflict and the fixture submits its explicit semantic resolution")
      live.advance().business[V.RepositorySyncAdvanceResult].condition shouldBe Some("MergeConflicted")
      val conflict = live.advance().business[V.RepositorySyncAdvanceResult]
      val resolution = V.RepositorySyncConflictResult("resolved", conflict.conflict.get.conflicts.map(value =>
        V.RepositorySyncConflictResolution(value.identity, "resolved product input\n")), "Specification-owned actual conflict resolution")
      val conflictref = V.WorkflowArtifactReference("fixture-review-input-conflict", "1")
      val conflictexecution = V.WorkflowExecutionEvidence(Vector(conflictref), Some("fixture-conflict-author"), None, None)
      val submitted = live.repositoryCall("submitRepositorySyncWorkResult", Vector("--sessionId", live.session,
        "--result", _json(resolution), "--resultReference", _json(conflictref), "--executionEvidence", _json(conflictexecution)))
      Then("only the actual conflict result is accepted")
      submitted.business[V.RepositorySyncConflictSubmissionResult].accepted shouldBe true
      When("ordinary Advance runs actual Compile Test and scalafix before issuing independent detailed review")
      val output = live.advance()
      val issued = output.business[V.RepositorySyncAdvanceResult]
      Then("the complete candidate and all three successful check receipts accompany actual Unexamined input")
      val review = issued.review.get
      review.changedPaths should contain allElementsOf _unknown
      review.localDiff.trim shouldBe git(live.root, "-C", live.repository.toString, "diff", "--cached", review.localTip, "--")
      review.remoteDiff.trim shouldBe git(live.root, "-C", live.repository.toString, "diff", "--cached", review.remoteTip, "--")
      val originalinput = _requests(live).head
      originalinput.judgmentMode shouldBe "detailed-review"
      _unknown.foreach(path => originalinput.uncertainties should contain("Unexamined unknown input: " + path))
      val formal = _formal(live)
      formal.map(value => parse(value._3).toOption.get.as[C.Receipt].toOption.get).map(receipt =>
        receipt.identity -> receipt.sbt.get.outcome).toSet shouldBe _checks.map(check => check.identity -> WorkflowSbtValidation.Outcome.Succeeded).toSet
      _lines(live.root, "sbt-invocations.txt").size shouldBe 3
      _lines(live.root, "lint-entered.txt").size shouldBe 1
      val result = V.RepositorySyncNativeMergeReviewResult("decision-needed", _rationale, Vector.empty)
      val reference = V.WorkflowArtifactReference("fixture-review-input-independent-original", "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("fixture-independent-original-reviewer"), None, None)
      When("the independent fixture prepares imports and submits decision-needed once through normal public operations")
      _prepare(live, "review", output, _json(result), reference, execution)
      val accepted = live.repositoryCall("submitNativeMergeReview", Vector("--sessionId", live.session, "--result", _json(result),
        "--resultReference", _json(reference), "--executionEvidence", _json(execution)))
      Then("the actual decision-needed completion is accepted and ordinary Advance retains a nonterminal Wait")
      accepted.business[V.RepositorySyncNativeMergeReviewSubmissionResult].accepted shouldBe true
      live.advance().business[V.RepositorySyncAdvanceResult].kind shouldBe "Wait"
      val current = _current(live)
      current.currentProgression shouldBe Some(ProgressionReference("sync.ReviewDecisionRequired"))
      current.lifecycle shouldBe Lifecycle.Active
      current.suspension shouldBe None
      val saved = _snapshot(live)
      When("the old running CAR collects the already saved decision-needed frontier again")
      live.advance().business[V.RepositorySyncAdvanceResult].kind shouldBe "Wait"
      Then("the native instance candidate original result and check counters stay unchanged and one episode is retained")
      _snapshot(live) shouldBe saved
      val episode = Episode(live.root.toString, ThreeWorkflowRuntimeParameters.encode(scenario.host), scenario.plan.asJson, live.session,
        take(WorkflowInstanceRecordJsonV1.encodeC(current)), review.localTip, review.indexTree, review.remoteTip,
        output.invocation.stdout.toString, issued.issuedWorkContext.get.continuationId, _json(result), _json(reference), _json(execution), _stored(live.root), formal,
        _lines(live.root, "sbt-invocations.txt"), _lines(live.root, "sbt-command-lines.txt"), _lines(live.root, "lint-entered.txt"))
      Files.writeString(_episode_path, episode.asJson.noSpaces, UTF_8, StandardOpenOption.CREATE_NEW)
      Files.setPosixFilePermissions(_episode_path, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"))
    }

    "continue REVIEW-INPUT retained review without check replay" in {
      Given("the newly published normal CAR and the single saved old decision-needed Session with actual successful receipts")
      val episode = parse(Files.readString(_episode_path)).toOption.get.as[Episode].toOption.get
      val live = _reopen(episode)
      take(WorkflowInstanceRecordJsonV1.encodeC(_current(live))) shouldBe episode.originalRecord
      _stored(live.root).sorted shouldBe episode.nativeRows.sorted
      _retained(live, episode)
      git(live.root, "-C", live.repository.toString, "write-tree") shouldBe episode.indexTree
      val oldrequest = _requests(live).find(_.indexTree == episode.indexTree).get
      oldrequest.uncertainties.exists(_.startsWith("Unexamined")) shouldBe true
      val reference = V.WorkflowArtifactReference("fixture-review-input-independent-direction", "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
      val answer = V.RepositorySyncValidationContinuationAnswer("AdoptInputClassificationAndContinueReview",
        "Independent fixture direction classifies only old unknown inputs and preserves all check applicability", false, false)
      def _describe_(): F.Result = live.repositoryCall(N.describeOperation, Vector("--sessionId", live.session))
      def _adopt_(value: V.RepositorySyncValidationContinuationAdoptionInput): F.Result =
        live.repositoryCall(N.adoptionOperation, Vector("--input", _json(value)))
      When("only the host mapping proposes README documentation but wrongly demotes selected consumed scripts and skills")
      _configure(live.scenario, corrected = true, demote = true)
      val initial = _snapshot(live)
      val badmapping = _describe_().business[V.RepositorySyncValidationContinuationDescriptionResult]
      Then("the passive description exposes issues and permits only Defer without changing old native facts")
      badmapping.choices shouldBe Vector("Defer")
      val badwire = take(badmapping.target.toJsonStringC)
      badwire should include("issues")
      _snapshot(live) shouldBe initial
      When("a proposed current fixed check selection drops lint coverage")
      _configure(live.scenario, corrected = true, checks = _checks.dropRight(1))
      val badchecks = _describe_().business[V.RepositorySyncValidationContinuationDescriptionResult]
      Then("changed applicability permits only Defer and never synthesizes another successful receipt")
      badchecks.choices shouldBe Vector("Defer")
      _snapshot(live) shouldBe initial
      When("the external host proposes the exact corrected README and consumed script skill classification")
      _configure(live.scenario, corrected = true)
      val described = _describe_()
      val description = described.business[V.RepositorySyncValidationContinuationDescriptionResult]
      val targetwire = parse(take(description.target.toJsonStringC)).toOption.get
      val target = R.decodePublicTarget(targetwire).toOption.get
      Then("the public compact v3 target binds the unchanged actual candidate and original decision-needed review")
      targetwire.hcursor.get[String]("continuation_policy") shouldBe Right("sm-workflow.repository-sync-review-input-continuation.v3")
      target.sessionId shouldBe episode.session
      target.candidate.tree shouldBe episode.indexTree
      target.candidate.before.head shouldBe episode.head
      target.candidate.mergeHead shouldBe episode.mergeHead
      target.review.continuationId shouldBe episode.continuationId
      target.review.result.identity shouldBe "fixture-review-input-independent-original"
      target.review.decision shouldBe "decision-needed"
      target.originalInputs.unknownPaths shouldBe _unknown
      target.proposedInputs.unknownPaths shouldBe empty
      target.proposedInputs.binaryPaths shouldBe empty
      target.proposedInputs.consumedPaths should contain allElementsOf Vector("scripts/probe.py", "skills/probe/SKILL.md")
      target.proposedInputs.consumedPaths should not contain "README.md"
      target.selectedChecks shouldBe _checks
      target.applicabilityIssues shouldBe empty
      description.choices shouldBe Vector(answer.choice, "Defer")
      _snapshot(live) shouldBe initial
      val input = V.RepositorySyncValidationContinuationAdoptionInput(live.session, description.target, answer.choice,
        answer.rationale, reference, false, false, execution)
      Vector(input, input.copy(sessionId = "foreign-session"), input.copy(choice = "AuthorizeMissingRequiredLint"),
        input.copy(priorExecutionEnded = true), input.copy(acceptOriginalUncertainty = true),
        input.copy(executionEvidence = execution.copy(workerIdentity = Some("foreign-participant")))).foreach { value =>
        When("an unsupported unprepared crossed or bogus UNKNOWN answer attempts adoption")
        val refused = _adopt_(value)
        Then("adoption refuses before effects and retains the exact old candidate and successful checks")
        refused.exitCode should not be 0
        _snapshot(live) shouldBe initial
      }
      val deferredreference = reference.copy(identity = reference.identity + "-defer")
      val deferredexecution = execution.copy(references = Vector(deferredreference))
      When("the independent fixture prepares imports and adopts an exact false false Defer answer")
      _prepare(live, "validation-continuation", described, _json(answer.copy(choice = "Defer")), deferredreference, deferredexecution)
      val deferred = _adopt_(input.copy(choice = "Defer", reference = deferredreference, executionEvidence = deferredexecution))
      Then("Defer leaves the actual saved original result history and check counters unchanged")
      deferred.business[V.RepositorySyncValidationContinuationAdoptionResult].accepted shouldBe false
      _snapshot(live) shouldBe initial
      When("the independent fixture prepares the exact adopting direction but a crossed reference revision is submitted")
      _prepare(live, "validation-continuation", described, _json(answer), reference, execution)
      val crossed = _adopt_(input.copy(reference = reference.copy(revision = "2")))
      Then("the foreign reference refuses without appending any direction")
      crossed.exitCode should not be 0
      _snapshot(live) shouldBe initial
      val stalewire = parse(_json(input)).toOption.get.mapObject(fields => fields.add("target",
        fields("target").get.mapObject(_.add("frontier_revision", "999999".asJson))))
      When("the direction carries a stale public frontier revision")
      val stale = live.repositoryCall(N.adoptionOperation, Vector("--input", stalewire.noSpaces))
      Then("the stale target refuses before any native or Git effect")
      stale.exitCode should not be 0
      _snapshot(live) shouldBe initial
      When("normal adoption receives the exact independently prepared and imported false false classification direction")
      val adopted = _adopt_(input).business[V.RepositorySyncValidationContinuationAdoptionResult]
      Then("only an append-only admitted direction is stored with no review issuance check execution or semantic acceptance")
      adopted.accepted shouldBe true
      _current(live).currentProgression shouldBe Some(ProgressionReference("sync.ReviewInputContinuationAdmitted"))
      _requests(live) should contain(oldrequest)
      _requests(live).size shouldBe 1
      _retained(live, episode)
      val admission = _snapshot(live)
      When("the old direction is adopted again after its frontier has moved")
      val duplicate = _adopt_(input)
      Then("duplicate adoption refuses without another history entry or any check")
      duplicate.exitCode should not be 0
      _snapshot(live) shouldBe admission
      When("a separate normal Advance issues a fresh independent review over the same full actual candidate")
      val output = live.advance()
      val issued = output.business[V.RepositorySyncAdvanceResult]
      val review = issued.review.get
      val revised = _requests(live).find(_.validatedRevision != oldrequest.validatedRevision).get
      Then("the new issue retains original rationale reference limitations full diffs and genuine successful receipts without replay")
      issued.issuedWorkContext.get.continuationId should not be episode.continuationId
      issued.issuedWorkContext.get.continuationId shouldBe live.session + ":native-review:" + revised.validatedRevision
      revised.indexTree shouldBe oldrequest.indexTree
      revised.localTip shouldBe oldrequest.localTip
      revised.remoteTip shouldBe oldrequest.remoteTip
      revised.changedPaths shouldBe oldrequest.changedPaths
      revised.localDiff shouldBe oldrequest.localDiff
      revised.remoteDiff shouldBe oldrequest.remoteDiff
      revised.checks shouldBe oldrequest.checks
      revised.selectedChecks shouldBe oldrequest.selectedChecks
      val context = revised.uncertainties.mkString("\n")
      context should include("fixture-review-input-independent-original")
      context should include(_rationale)
      context.toLowerCase should include("decision-needed")
      context should include("no typed accepted subcoverage")
      oldrequest.uncertainties.foreach(limit => context should include(limit))
      revised.uncertainties.exists(_.startsWith("Unexamined")) shouldBe false
      _requests(live).size shouldBe 2
      _retained(live, episode)
      val issuedstate = _snapshot(live)
      When("ordinary Advance recollects the pending fresh review without a Submit")
      val recollected = live.advance().business[V.RepositorySyncAdvanceResult]
      recollected.review shouldBe issued.review
      recollected.issuedWorkContext.get.continuationId shouldBe issued.issuedWorkContext.get.continuationId
      Then("the same fresh review is issued only once and the old completion remains retained")
      _requests(live).size shouldBe 2
      _snapshot(live) shouldBe issuedstate
      _retained(live, episode)
      val result = V.RepositorySyncNativeMergeReviewResult("accept",
        "Specification-owned independent review examined both complete actual parent diffs: resolved value, Scala, script and skill all use 1; README is documentation only. No old partial prose is accepted coverage and no live provider acceptance is claimed.", Vector.empty)
      val reviewreference = V.WorkflowArtifactReference("fixture-review-input-independent-follow-up", "1")
      val reviewexecution = V.WorkflowExecutionEvidence(Vector(reviewreference), Some("fixture-independent-follow-up-reviewer"), None, None)
      When("a distinct independent fixture result is prepared imported and submitted after reviewing the real tiny full candidate")
      review.localDiff.trim shouldBe git(live.root, "-C", live.repository.toString, "diff", "--cached", review.localTip, "--")
      review.remoteDiff.trim shouldBe git(live.root, "-C", live.repository.toString, "diff", "--cached", review.remoteTip, "--")
      _prepare(live, "review", output, _json(result), reviewreference, reviewexecution)
      val accepted = live.repositoryCall("submitNativeMergeReview", Vector("--sessionId", live.session, "--result", _json(result),
        "--resultReference", _json(reviewreference), "--executionEvidence", _json(reviewexecution)))
      Then("normal Submit accepts only the fresh independent review and retains original decision-needed evidence")
      accepted.business[V.RepositorySyncNativeMergeReviewSubmissionResult].accepted shouldBe true
      _retained(live, episode)
      val acceptedstate = _snapshot(live)
      val acceptedrows = _stored(live.root).sorted
      val acceptedrequests = _requests(live)
      // This exercises the durable revised source after independent Submit,
      // before acceptance is consumed by convergence. Interruption between
      // saving that source and issuing is not simulated; the parent's source
      // review qualifies that path through the shared applicability gate.
      val changedcoverage = _impact().copy(coverage = _impact().coverage.map(coverage =>
        coverage.copy(inputPaths = coverage.inputPaths :+ "another-consumed-input")))
      for ((checks, impact) <- Vector((_checks.dropRight(1), Option.empty[RepositorySyncProgramImpact.Policy]),
          (_checks, Some(changedcoverage)))) {
        When("CB-RRI-01 changes only external current check selection or consumed coverage after independent accept and before convergence")
        _configure(live.scenario, corrected = true, checks = checks, impact = impact)
        val refused = live.advance()
        Then("Advance refuses before effects with the same latest source issue receipts counters and native history")
        refused.exitCode should not be 0
        refused.invocation.logs should include("check applicability drifted")
        _snapshot(live) shouldBe acceptedstate
        _stored(live.root).sorted shouldBe acceptedrows
        _requests(live) shouldBe acceptedrequests
        _retained(live, episode)
      }
      _configure(live.scenario, corrected = true)
      When("ordinary Advance performs its existing local merge commit file-local bare push and final fetch")
      val terminal = live.advance().business[V.RepositorySyncAdvanceResult]
      Then("the same Session completes with unchanged checks and the expected genuine two-parent merge")
      terminal.kind shouldBe "Terminal"
      _current(live).identity.value shouldBe episode.session
      _current(live).lifecycle shouldBe Lifecycle.Completed
      _current(live).suspension shouldBe None
      val tip = git(live.root, "-C", live.repository.toString, "rev-parse", "HEAD")
      git(live.root, "-C", live.repository.toString, "show", "-s", "--format=%P", "HEAD").split(" ").toVector shouldBe
        Vector(episode.head, episode.mergeHead)
      git(live.root, "--git-dir=" + live.root.resolve("upstream.git"), "rev-parse", "refs/heads/main") shouldBe tip
      git(live.root, "-C", live.repository.toString, "rev-parse", "refs/remotes/origin/main") shouldBe tip
      git(live.root, "-C", live.repository.toString, "status", "--porcelain") shouldBe ""
      Files.exists(live.repository.resolve(".git/MERGE_HEAD")) shouldBe false
      _retained(live, episode)
      val completed = take(WorkflowInstanceRecordJsonV1.encodeC(_current(live)))
      val rows = _stored(live.root).sorted
      When("ordinary Advance recollects the terminal Session")
      val repeated = live.advance().business[V.RepositorySyncAdvanceResult]
      Then("terminal collection is passive and preserves the complete actual native records")
      repeated shouldBe terminal
      take(WorkflowInstanceRecordJsonV1.encodeC(_current(live))) shouldBe completed
      _stored(live.root).sorted shouldBe rows
      _retained(live, episode)
    }
  }
}
