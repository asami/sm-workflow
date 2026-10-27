package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import java.time.Instant
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import org.goldenport.cncf.processexecution.SbtOperation
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.record.RecordPresentable
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseOrderedProgression as O
import GoalPhaseRepairInventoryContinuation as K
import NativeWorkflowCliFixture as F
import SplitPhaseExplicitPartitionFixture.take

/** Isolated real Git/Compile/Test and public native Operations. Optional installed
  * selection uses the same fixture, with no production Human/provider claim.
  * @since   Oct. 27, 2026
  * @version Oct. 27, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalPhaseRepairInventoryCliSpec extends AnyWordSpec with GivenWhenThen
    with GoalPhaseKnownFailedPhaseValidationFixture {
  private val _product = "src/test/scala/ProductCheck.scala"
  private val _primary = Path.of("").toAbsolutePath.normalize
  override protected def known_repair_limit: Int = 1
  override protected def goal_completion_policy: String = GoalPhasePostCompletionSync.localPolicy
  override protected def recoveryHost(root: Path): ThreeWorkflowRuntimeParameters.Definition = {
    val original = super.recoveryHost(root)
    val directory = Option.when(root.getFileName.toString.startsWith("phase-inventory-directory"))("new-tests").toVector
    original.copy(goal = original.goal.copy(candidatePaths = Vector("value.txt", _product) ++ directory,
      closure = original.goal.closure.copy(programs = Vector(GoalPhaseNativeClosure.StepProgram("step-1", 1),
        GoalPhaseNativeClosure.StepProgram(GoalPhaseNativeReleaseClosure.programIdentity, 2)))))
  }
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val git = TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString), goalPaths = goal.candidatePaths,
      goalMessages = Vector("Accepted original Step", "Commit the exact repaired Phase inventory"),
      goalSelections = Vector(Vector("value.txt"), Vector(_product)))
    val compile = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(1), Some(git),
      executionTimeoutMillis = 1200000L, operation = SbtOperation.Compile)
    val test = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(2),
      executionTimeoutMillis = 1200000L, operation = SbtOperation.Test)
    def array(value: Json, key: String): Vector[Json] = value.hcursor.get[Vector[Json]](key).toOption.get
    val programs = array(compile, "sbtPrograms") ++ array(test, "sbtPrograms")
    compile.mapObject(_.add("sbtPrograms", Json.fromValues(programs))
      .add("workspaces", Json.fromValues(array(compile, "workspaces") ++ array(test, "workspaces")))
      .add("workspaceGrants", Json.fromValues(array(compile, "workspaceGrants") ++ array(test, "workspaceGrants")))
      .add("grants", Json.arr(Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson,
        "capabilities" -> Json.fromValues((array(compile, "gitPrograms") ++ programs)
          .map(_.hcursor.downField("capability").focus.get))))))
  }
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def _read(path: Path): Json = parse(Files.readString(path)).toOption.get
  private def _result(outcome: String, proposal: Option[GoalPhasePlanRevision.Proposal] = None): V.GoalPhaseWorkResult =
    V.GoalPhaseWorkResult(outcome, "SPECIFICATION-OWNED exact Phase repair result", Vector.empty,
      None, None, None, None, None, None, None, None, dependencyValidation = None, diagnosticRepair = None)
      .copy(planRevision = GoalPhasePlanRevision.publicProposal(proposal))
  private def _issued_result(issued: V.GoalPhaseAdvanceResult, result: V.GoalPhaseWorkResult): V.GoalPhaseWorkResult = {
    val work = issued.work.get
    if (work.kind != "RepairStep") result
    else {
      work.stepIdentity shouldBe None
      val unresolved = work.issues.toVector.flatMap(_.issues).filter(_.status != "RESOLVED")
      val assigned = unresolved.filter(_.repository == work.selectedPlan.repository)
      assigned should have size 1
      assigned shouldBe unresolved
      assigned.head.step shouldBe "phase:" + work.selectedPlan.phase
      assigned.head.identity should not be empty
      Set("TEST", "REVIEW") should contain(assigned.head.source)
      val disposition = result.outcome match {
        case "decision-needed" => "BLOCKED"
        case "completed" => "FIXED"
        case other => fail("Unexpected fixture RepairStep outcome: " + other)
      }
      val rationale = if (disposition == "BLOCKED")
        "SPECIFICATION-OWNED assigned repair requires the exact inventory or Human direction before completion"
      else "SPECIFICATION-OWNED assigned correction is submitted for fresh native checks and independent review"
      result.copy(issueReport = Some(V.GoalPhaseIssueReport(assigned.map(issue =>
        V.GoalPhaseIssueDisposition(issue.identity, disposition, rationale, None)))))
    }
  }

  private final class Cli(val root: Path, val session: String) {
    private var _ordinal = 0
    private val _deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(40)
    def call(service: String, operation: String, args: Vector[String]): F.Result = {
      require(_ordinal < 80 && System.nanoTime() < _deadline, "Finite inventory fixture operation budget exhausted")
      val index = _ordinal
      _ordinal += 1
      val logs = Files.createDirectories(root.resolve("inventory-cli-logs"))
      val stdout = logs.resolve(index + ".json")
      val stderr = logs.resolve(index + ".log")
      val launcher = sys.props.get("smworkflow.spec.installedCommand") match {
        case Some(executable) =>
          val selected = Path.of(executable)
          require(selected.isAbsolute && Files.isExecutable(selected), "Select an absolute installed CLI")
          Vector(selected.toString, "--runtime-root", root.toString,
            "--textus.assembly.descriptor=" + root.resolve(".textus/assembly.yaml"))
        case None => Vector(_primary.resolve("bin/launcher").toString, "--classpath-file",
          _primary.resolve("target/cncf.d/runtime-classpath.txt").toString, "--main-class", "org.goldenport.cncf.CncfMain",
          "--", "--force-exit", "--component-dev-dir", _primary.toString,
          "--textus.assembly.descriptor=" + root.resolve(".textus/assembly.yaml"))
      }
      val argv = launcher ++ Vector("command", SmWorkflowComponent.componentId.name + "." + service + "." + operation) ++ args ++ Vector("--json")
      val builder = new ProcessBuilder(argv.asJava).directory(root.toFile).redirectOutput(stdout.toFile).redirectError(stderr.toFile)
      sys.props.get("smworkflow.spec.runtimeRoot").orElse(sys.props.get("smworkflow.spec.installations"))
        .foreach(value => builder.environment().put("SM_WORKFLOW_INSTALLATIONS", value))
      val process = builder.start()
      try {
        process.getOutputStream.close()
        withClue(Files.readString(stderr)) {
          process.waitFor(math.min(_deadline - System.nanoTime(), TimeUnit.MINUTES.toNanos(22)), TimeUnit.NANOSECONDS) shouldBe true
        }
        F.Result(process.exitValue(), F.Invocation(argv, process.pid(), stdout, stderr))
      } finally {
        if (process.isAlive) {
          val stream = process.toHandle.descendants()
          val descendants = try stream.iterator().asScala.toVector finally stream.close()
          process.destroy()
          descendants.filter(_.isAlive).foreach(_.destroy())
          if (!process.waitFor(10L, TimeUnit.SECONDS)) process.destroyForcibly()
          descendants.filter(_.isAlive).foreach(_.destroyForcibly())
          process.waitFor(10L, TimeUnit.SECONDS) shouldBe true
        }
        process.getInputStream.close()
        process.getErrorStream.close()
      }
    }
    def goal(operation: String, args: Vector[String]): F.Result = call("GoalPhaseService", operation, args)
    def advance(): F.Result = goal("advanceGoalPhase", Vector("--sessionId", session))
    def file(name: String, value: RecordPresentable): String = {
      val path = Files.createTempDirectory(root, name + "-").resolve("input.json")
      Files.writeString(path, _json(value))
      path.toUri.toASCIIString
    }
    def prepare(operation: String, value: RecordPresentable): Unit = {
      val result = call("NativeWorkflowPreparationService", operation, Vector("--input", _json(value)))
      withClue(result.invocation.logs) { result.exitCode shouldBe 0 }
      _read(result.invocation.stdout).hcursor.downField("datastore").focus.get.asArray.get should not be empty
      val config = root.resolve(".textus/config.json")
      Files.writeString(config, _read(config).mapObject(_.add("textus.import.data.file", result.invocation.stdout.toString.asJson)).noSpaces)
    }
    def submit(work: V.GoalPhaseAdvanceResult, result: V.GoalPhaseWorkResult, name: String,
      execution: Option[V.WorkflowExecutionEvidence] = None): Unit = {
      goal(work.submitOperation.get, Vector("--sessionId", work.submitSessionId.get, "--result", _json(_issued_result(work, result)),
        "--resultReference", _json(V.WorkflowArtifactReference(name, "1"))) ++ execution.toVector.flatMap(value =>
        Vector("--executionEvidence", _json(value)))).business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
    }
    def answer(issued: F.Result, choice: String): Unit = {
      val question = issued.business[V.GoalPhaseAdvanceResult]
      val answer = V.GoalPhaseOrderedDecisionAnswer(choice, "SPECIFICATION-OWNED actual " + choice)
      val reference = V.WorkflowArtifactReference("inventory-human-" + choice, "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("fixture-human"), None, None)
      prepare("prepareEvidence", V.WorkflowEvidencePreparationInput("GoalPhase", "human", issued.invocation.stdout.toUri.toASCIIString,
        file("answer", answer), file("reference", reference), file("execution", execution), "normal-development-host",
        Instant.now().minusSeconds(1).toString, "entity:smworkflow.cli.host_evidence", Some("participant")))
      goal(question.submitOperation.get, Vector("--sessionId", question.submitSessionId.get, "--answer", _json(answer),
        "--resultReference", _json(reference), "--executionEvidence", _json(execution)))
        .business[V.GoalPhaseOrderedDecisionSubmissionResult].accepted shouldBe true
    }
    def describe(proposal: GoalPhasePlanRevision.Proposal): F.Result = goal(GoalPhaseRemainingPlanAdoption.describeOperation,
      Vector("--input", _json(V.GoalPhaseRemainingPlanDescriptionInput(session, GoalPhasePlanRevision.publicProposal(Some(proposal)).get))))
    def input(description: V.GoalPhaseRemainingPlanDescriptionResult, proposal: GoalPhasePlanRevision.Proposal,
      name: String): V.GoalPhaseRemainingPlanAdoptionInput = V.GoalPhaseRemainingPlanAdoptionInput(session, description.target,
      GoalPhasePlanRevision.publicProposal(Some(proposal)).get, "SPECIFICATION-OWNED independently observed inventory direction",
      V.WorkflowArtifactReference(name, "1"))
    def direction(described: F.Result, input: V.GoalPhaseRemainingPlanAdoptionInput): Unit =
      prepare("prepareEvidence", V.WorkflowEvidencePreparationInput("GoalPhase", "remaining-plan-adoption",
        described.invocation.stdout.toUri.toASCIIString, file("direction", input), file("reference", input.reference),
        file("execution", V.WorkflowExecutionEvidence(Vector(input.reference), Some("participant"), None, None)),
        "normal-development-host", Instant.now().minusSeconds(1).toString, "entity:smworkflow.cli.host_evidence", Some("participant")))
    def adopt(input: V.GoalPhaseRemainingPlanAdoptionInput): F.Result = goal(GoalPhaseRemainingPlanAdoption.adoptionOperation, Vector("--input", _json(input)))
    def review(issued: F.Result, outcome: String = "accept", findings: Vector[String] = Vector.empty): Unit = {
      val reference = V.WorkflowArtifactReference("inventory-independent-phase-review", "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("fixture-independent-reviewer"), None, None)
      val work = issued.business[V.GoalPhaseAdvanceResult]
      val known = work.work.get.issues.toVector.flatMap(_.issues).filter(issue =>
        issue.repository == work.work.get.selectedPlan.repository && issue.source == "REVIEW")
      val result = _result(outcome).copy(findings = findings, issueReport = Option.when(outcome == "accept" && known.nonEmpty)(
        V.GoalPhaseIssueReport(known.map(issue => V.GoalPhaseIssueDisposition(issue.identity, "RESOLVED",
          "SPECIFICATION-OWNED independent Phase review confirms the issued finding is resolved", None)))))
      prepare("prepareEvidence", V.WorkflowEvidencePreparationInput("GoalPhase", "review", issued.invocation.stdout.toUri.toASCIIString,
        file("review", result), file("reference", reference), file("execution", execution), "normal-development-host",
        Instant.now().minusSeconds(1).toString, "entity:smworkflow.cli.host_evidence", None))
      submit(work, result, reference.identity, Some(execution))
    }
    def authority(issued: F.Result, name: String): Unit = prepare("prepareAuthority", V.WorkflowAuthorityPreparationInput(
      issued.invocation.stdout.toUri.toASCIIString, file("authority", V.WorkflowArtifactReference(name, "1")), "participant",
      "normal-development-host", Instant.now().minusSeconds(1).toString, "entity:smworkflow.cli.host_evidence"))
    def git(args: Vector[String]): String = {
      val stdout = Files.createTempFile(root, "git-read-", ".txt")
      val stderr = Files.createTempFile(root, "git-read-", ".log")
      val process = new ProcessBuilder((Vector("/usr/bin/git", "-C", root.resolve("repository").toString) ++ args).asJava)
        .redirectOutput(stdout.toFile).redirectError(stderr.toFile).start()
      try {
        process.waitFor(15L, TimeUnit.SECONDS) shouldBe true
        withClue(Files.readString(stderr)) { process.exitValue() shouldBe 0 }
        Files.readString(stdout).trim
      } finally { if (process.isAlive) { process.destroyForcibly(); process.waitFor(10L, TimeUnit.SECONDS) }; () }
    }
  }

  private final case class Episode(root: Path, cli: Cli, original: V.GoalPhaseAdvanceResult, repair: V.GoalPhaseAdvanceResult,
    closedtip: String, history: InstanceRecord)
  private def _episode(name: String, integratedReviewRepair: Boolean = false): Episode = {
    val root = newKnownRoot(name)
    var session = ""
    var original = Option.empty[V.GoalPhaseAdvanceResult]
    inKnownEpisode(root, initialize = true, componentInstance = "default") { episode =>
      session = episode.start().sessionId
      original = Some(episode.advance(session))
      episode.submitImplementation(original.get).accepted shouldBe true
      episode.acceptStep(episode.advance(session))
    }
    val config = root.resolve(".textus/config.json")
    val settings = _read(_primary.resolve("examples/source-history/goldenport-config.json")).asObject.get
    Files.writeString(config, _read(config).mapObject(fields => settings.toVector.foldLeft(fields)((saved, entry) => saved.add(entry._1, entry._2))).noSpaces)
    if (sys.props.contains("smworkflow.spec.installedCommand")) Files.writeString(config, Vector(1, 2).foldLeft(_read(config))((value, ordinal) =>
      value.mapObject(_.add("cncf.sbt.runtime-binding.sm-workflow-repository-validation-" + ordinal, "installed".asJson))).noSpaces)
    val cli = new Cli(root, session)
    if (integratedReviewRepair) {
      Files.writeString(root.resolve("phase-ready"), "SPECIFICATION-OWNED ready input\n")
      val phase = cli.advance()
      phase.business[V.GoalPhaseAdvanceResult].work.get.kind shouldBe "ReviewPhase"
      cli.review(phase, "repair", Vector("The accepted product needs a further in-scope correction"))
    }
    val repair = cli.advance().business[V.GoalPhaseAdvanceResult]
    repair.work.get.kind shouldBe "RepairStep"
    repair.work.get.stepIdentity shouldBe None
    repair.work.get.completed.map(_.step) shouldBe Vector("step-1")
    repair.repairProgress.get.automaticCount shouldBe 1
    phaseExecutions(root).size shouldBe 1
    var history = Option.empty[InstanceRecord]
    inKnownEpisode(root, componentInstance = "default")(episode => history = Some(episode.current(session)))
    Episode(root, cli, original.get, repair, cli.git(Vector("rev-parse", "HEAD")), history.get)
  }
  private def _proposal: GoalPhasePlanRevision.Proposal = GoalPhasePlanRevision.Proposal("phase-inventory", "1",
    "The product check itself must be corrected inside the assigned Phase repair",
    _recovery_plan.copy(revision = "inventory-2", phaseRepairInventory = Vector(_product)))
  private def _bounded_target(target: K.Target): Unit = {
    target.schemaVersion shouldBe "sm-workflow.phase-repair-inventory.v2"
    val record = K.targetRecord(target)
    take(record.toJsonStringC).getBytes(java.nio.charset.StandardCharsets.UTF_8).length should be < (64 * 1024)
    take(K.targetC(record)) shouldBe target
    Vector("semanticRequest", "semanticResult", "pendingPrefix", "deferredAnswer").foreach(field =>
      target.asJson.hcursor.downField(field).succeeded shouldBe false)
  }

  "E3 automatic inventory continuation" should afterWord(
    "in spec:goal-phase-repair-inventory-continuation, example:E3, rules:R2,R3,R6, phase:P99-INVENTORY-01") {
    "admit the actual typed proposal without a Human then perform fresh checks independent review and actual release commit" in {
      Given("docs/spec/goal-phase-repair-inventory-continuation.md R2,R3,R6 E3: a NEW isolated Session with one accepted Step and a real failed Phase Test")
      val episode = _episode("phase-inventory-automatic")
      val cli = episode.cli
      When("the assigned phase repair returns the inventory-only typed planRevision")
      cli.submit(episode.repair, _result("decision-needed", Some(_proposal)), "inventory-proposal-result")
      val continued = cli.advance().business[V.GoalPhaseAdvanceResult]
      Then("ordinary Advance issues the same phase Repair once without another Human or batch")
      continued.kind shouldBe "Request"
      continued.work.get.kind shouldBe "RepairStep"
      continued.work.get.stepIdentity shouldBe None
      continued.work.get.selectedPlan.steps shouldBe episode.repair.work.get.selectedPlan.steps
      continued.work.get.selectedPlan.phaseRepairInventory shouldBe Vector(_product)
      continued.work.get.originalBase shouldBe episode.original.work.get.originalBase
      continued.work.get.completed shouldBe episode.repair.work.get.completed
      continued.repairProgress shouldBe episode.repair.repairProgress
      continued.submitSessionId shouldBe episode.repair.submitSessionId
      continued.issuedWorkContext.get.continuationId should not be episode.repair.issuedWorkContext.get.continuationId
      cli.advance().business[V.GoalPhaseAdvanceResult] shouldBe continued
      phaseExecutions(episode.root).size shouldBe 1
      val source = episode.root.resolve("repository").resolve(_product)
      val predicate = "val ready = Files.isRegularFile(root.resolve(\"phase-ready\"))"
      Files.readString(source) should include(predicate)
      Files.writeString(source, Files.readString(source).replace(predicate, "val ready = true"))
      cli.submit(continued, _result("completed"), "inventory-repair-completed")
      val reviewcall = cli.advance()
      val review = reviewcall.business[V.GoalPhaseAdvanceResult]
      Then("the admitted product file is the only staged repair and fresh normal Test succeeds before independent Phase review")
      review.work.get.kind shouldBe "ReviewPhase"
      review.work.get.review.get.changedPaths should contain(_product)
      review.work.get.review.get.checks.head.exitCode shouldBe Some(0)
      review.work.get.review.get.checks.head.sbt.get.outcome shouldBe "succeeded"
      cli.git(Vector("diff", "--cached", "--name-only")) shouldBe _product
      phaseExecutions(episode.root).size shouldBe 2
      cli.review(reviewcall)
      var finalresult = cli.advance()
      var rounds = 0
      while (finalresult.business[V.GoalPhaseAdvanceResult].authorityContext.nonEmpty && rounds < 4) {
        cli.authority(finalresult, "inventory-release-authority-" + rounds)
        rounds += 1
        finalresult = cli.advance()
      }
      val terminal = finalresult.business[V.GoalPhaseAdvanceResult]
      Then("the native release commit selects the inventory file and retains the accepted Step and original failed check history")
      terminal.kind shouldBe "Terminal"
      cli.git(Vector("rev-parse", "HEAD^")) shouldBe episode.closedtip
      cli.git(Vector("diff-tree", "--no-commit-id", "--name-only", "-r", "HEAD")) shouldBe _product
      cli.git(Vector("status", "--porcelain=v1", "--untracked-files=all")) shouldBe ""
      inKnownEpisode(episode.root, componentInstance = "default") { reopened =>
        val current = reopened.current(cli.session)
        current.history.take(episode.history.history.size) shouldBe episode.history.history
        current.history.count(_.causation.value == K.causation) shouldBe 1
        current.history.count(_.causation.value == "ordered-release-closure:committed") shouldBe 1
        val inventoryentry = current.history.find(_.causation.value == K.causation).get
        val releaseentry = current.history.find(_.causation.value == "ordered-release-closure:intent").get
        reopened.prepare(call => for {
          catalog <- GoalPhaseRuntimeCatalog.loadC()
          storage <- WorkflowComponentStorage.bindC(ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin),
            SmWorkflowComponent.componentId, _configuration_, Vector(catalog.definition), _collection_("instances"), _collection_("continuations"))
          inventoryreader <- storage.bindMechanicalC(GoalPhaseNativeRun.advanceOperation, K.codec)
          inventory <- inventoryreader.loadRetainedC(WorkflowMechanicalTransition.Reference(cli.session + ":ordered-phase-repair-inventory:" +
            (inventoryentry.revision.value - 1), "1"))(using call.executionContext)
          releasereader <- storage.bindMechanicalC(GoalPhaseNativeRun.advanceOperation, GoalPhaseNativeReleaseClosure.codec)
          release <- releasereader.loadRetainedC(WorkflowMechanicalTransition.Reference(cli.session + ":ordered-release-closure:" +
            (releaseentry.revision.value - 1) + ":committed", "1"))(using call.executionContext)
          _ = {
            val target = inventory.get.receipt.payload.value.target
            _bounded_target(target)
            target.plan.steps shouldBe _recovery_plan.steps
            target.repairProgress.automaticCount shouldBe 1
            target.requestingWork shouldBe ContextReference(episode.history.suspension.get.continuationIdentity.value,
              (episode.history.revision.value - 1).toString)
            target.requestingResult shouldBe ContextReference("inventory-proposal-result", "1")
            target.sourceRevision shouldBe (episode.history.revision.value + 1).toString
            target.frontierRevision shouldBe target.sourceRevision
            target.questionIdentity shouldBe None
            target.pendingRevision shouldBe None
            target.deferredResult shouldBe None
            target.deferredChoice shouldBe None
            release.get.receipt.payload.value.execution.get.selectedPaths shouldBe Vector(_product)
            release.get.receipt.payload.value.input.plan.phaseRepairInventory shouldBe Vector(_product)
            release.get.receipt.payload.value.commit.get.parent shouldBe episode.closedtip
            release.get.receipt.payload.value.commit.get.identity shouldBe cli.git(Vector("rev-parse", "HEAD"))
          }
        } yield ())
      }
      phaseExecutions(episode.root).size shouldBe 2
    }
  }

  "E4 actual unanswered and accepted-Defer recovery" should afterWord(
    "in spec:goal-phase-repair-inventory-continuation, example:E4, rules:R4,R5, phase:P99-INVENTORY-01") {
    "retain ordinary integrated-review phase Repair Human resumption without an inventory receipt" in {
      Given("docs/spec/goal-phase-repair-inventory-continuation.md R4,R5 E4: an integrated Phase review reserved the ordinary phase repair")
      val episode = _episode("phase-inventory-ordinary-resume", integratedReviewRepair = true)
      val cli = episode.cli
      episode.repair.work.get.review should not be empty
      cli.submit(episode.repair, _result("decision-needed"), "ordinary-review-repair-decision")
      val question = cli.advance()
      question.business[V.GoalPhaseAdvanceResult].kind shouldBe "Human"
      When("the actual Human accepts ResumeAssignedWork and owning Advance reopens its retained successor")
      cli.answer(question, "ResumeAssignedWork")
      val resumed = cli.advance().business[V.GoalPhaseAdvanceResult]
      Then("the ordinary owner reissues the same phase Repair and never probes inventory provenance")
      resumed.work.get.kind shouldBe "RepairStep"
      resumed.work.get.stepIdentity shouldBe None
      resumed.work.get.review shouldBe episode.repair.work.get.review
      resumed.work.get.selectedPlan.phaseRepairInventory shouldBe Vector.empty
      resumed.work.get.completed shouldBe episode.repair.work.get.completed
      resumed.work.get.originalBase shouldBe episode.repair.work.get.originalBase
      resumed.repairProgress shouldBe episode.repair.repairProgress
      cli.advance().business[V.GoalPhaseAdvanceResult] shouldBe resumed
      inKnownEpisode(episode.root, componentInstance = "default") { reopened =>
        val current = reopened.current(cli.session)
        val ordinary = current.history.filter(_.causation.value == "inventory-human-ResumeAssignedWork")
        ordinary.size shouldBe 1
        ordinary.head.currentProgression shouldBe Some(ProgressionReference("phase.Repair"))
        current.history.exists(_.causation.value.startsWith(K.humanCausationPrefix)) shouldBe false
        current.history.exists(_.causation.value == K.causation) shouldBe false
      }
      phaseExecutions(episode.root).size shouldBe 1
      cli.git(Vector("rev-parse", "HEAD")) shouldBe episode.closedtip
    }

    "refuse a registered directory inventory before source history or staging can expand it" in {
      Given("docs/spec/goal-phase-repair-inventory-continuation.md R1,R6 E4: the host registers a directory-shaped literal and two dirty descendants")
      val episode = _episode("phase-inventory-directory")
      val cli = episode.cli
      val proposal = _proposal.copy(plan = _proposal.plan.copy(phaseRepairInventory = Vector("new-tests")))
      cli.submit(episode.repair, _result("decision-needed", Some(proposal)), "directory-inventory-proposal")
      val continued = cli.advance().business[V.GoalPhaseAdvanceResult]
      continued.work.get.selectedPlan.phaseRepairInventory shouldBe Vector("new-tests")
      val directory = Files.createDirectories(episode.root.resolve("repository/new-tests"))
      val first = directory.resolve("A.scala")
      val second = directory.resolve("B.scala")
      Files.writeString(first, "object A\n")
      Files.writeString(second, "object B\n")
      var before = Option.empty[InstanceRecord]
      inKnownEpisode(episode.root, componentInstance = "default")(opened => before = Some(opened.current(cli.session)))
      val index = cli.git(Vector("write-tree"))
      When("semantic completion asks the native owner to admit those actual dirty descendant paths")
      val rejected = cli.goal(continued.submitOperation.get, Vector("--sessionId", continued.submitSessionId.get,
        "--result", _json(_issued_result(continued, _result("completed"))),
        "--resultReference", _json(V.WorkflowArtifactReference("directory-repair-completed", "1"))))
      Then("the native ownership check refuses before any new history or selected stage effect")
      rejected.exitCode should not be 0
      cli.git(Vector("write-tree")) shouldBe index
      cli.git(Vector("diff", "--cached", "--name-only")) shouldBe ""
      Files.readString(first) shouldBe "object A\n"
      Files.readString(second) shouldBe "object B\n"
      inKnownEpisode(episode.root, componentInstance = "default")(opened =>
        take(WorkflowInstanceRecordJsonV1.encodeC(opened.current(cli.session))) shouldBe
          take(WorkflowInstanceRecordJsonV1.encodeC(before.get)))
      phaseExecutions(episode.root).size shouldBe 1
    }

    Vector("unanswered", "Defer", "Cancel").foreach { choice =>
      ("preserve the actual " + choice + " frontier and reject stale duplicate crossed or unproved recovery") in {
        Given("docs/spec/goal-phase-repair-inventory-continuation.md R4,R5 E4: an actual phase repair Human frontier with choice " + choice)
        val episode = _episode("phase-inventory-" + choice.toLowerCase)
        val cli = episode.cli
        cli.submit(episode.repair, _result("decision-needed"), "ordinary-inventory-decision")
        val question = cli.advance()
        question.business[V.GoalPhaseAdvanceResult].kind shouldBe "Human"
        var humanpending = Option.empty[InstanceRecord]
        inKnownEpisode(episode.root, componentInstance = "default")(opened => humanpending = Some(opened.current(cli.session)))
        val prior = if (choice == "Defer") Some(cli.describe(_proposal).business[V.GoalPhaseRemainingPlanDescriptionResult]) else None
        if (choice != "unanswered") cli.answer(question, choice)
        val describedcall = cli.describe(_proposal)
        if (choice == "Cancel") {
          Then("an actual Cancel never becomes inventory permission or a resumable source")
          describedcall.exitCode should not be 0
        } else {
          val description = describedcall.business[V.GoalPhaseRemainingPlanDescriptionResult]
          val target = take(K.targetC(description.target))
          _bounded_target(target)
          take(description.target.toJsonStringC).getBytes(java.nio.charset.StandardCharsets.UTF_8).length should be < (64 * 1024)
          target.requestingWork shouldBe ContextReference(episode.history.suspension.get.continuationIdentity.value,
            (episode.history.revision.value - 1).toString)
          target.requestingResult shouldBe ContextReference("ordinary-inventory-decision", "1")
          target.sourceRevision shouldBe (episode.history.revision.value + 1).toString
          target.questionIdentity shouldBe Some(humanpending.get.suspension.get.continuationIdentity.value)
          target.pendingRevision shouldBe Some(humanpending.get.revision.value.toString)
          target.frontierRevision shouldBe (humanpending.get.revision.value + (if (choice == "Defer") 1 else 0)).toString
          target.deferredResult shouldBe Option.when(choice == "Defer")(ContextReference("inventory-human-Defer", "1"))
          target.deferredChoice shouldBe Option.when(choice == "Defer")("Defer")
          val unregistered = _proposal.copy(plan = _proposal.plan.copy(phaseRepairInventory = Vector("src/test/Unregistered.scala")))
          cli.describe(unregistered).exitCode should not be 0
          prior.foreach(value => cli.adopt(cli.input(value, _proposal, "stale-before-defer")).exitCode should not be 0)
          target.deferredResult.nonEmpty shouldBe (choice == "Defer")
          target.questionIdentity should not be empty
          target.pendingRevision should not be empty
          target.plan.steps shouldBe _recovery_plan.steps
          val input = cli.input(description, _proposal, "inventory-independent-direction")
          cli.adopt(input).exitCode should not be 0
          val crossed = input.copy(target = K.targetRecord(target.copy(generation = target.generation + 1)))
          cli.adopt(crossed).exitCode should not be 0
          val malformed = input.copy(target = GoalPhaseNativeMetadata.record(target.asJson.mapObject(_.add("extra", Json.True))))
          cli.adopt(malformed).exitCode should not be 0
          val explicitnull = input.copy(target = GoalPhaseNativeMetadata.record(target.asJson.mapObject(_.add("deferredResult", Json.Null))))
          cli.adopt(explicitnull).exitCode should not be 0
          val wrongproposal = input.copy(proposal = GoalPhasePlanRevision.publicProposal(Some(_proposal.copy(reason = "Crossed rationale"))).get)
          val mismatched = cli.call("NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", _json(
            V.WorkflowEvidencePreparationInput("GoalPhase", "remaining-plan-adoption", describedcall.invocation.stdout.toUri.toASCIIString,
              cli.file("crossed-direction", wrongproposal), cli.file("crossed-reference", input.reference),
              cli.file("crossed-execution", V.WorkflowExecutionEvidence(Vector(input.reference), Some("participant"), None, None)),
              "normal-development-host", Instant.now().minusSeconds(1).toString, "entity:smworkflow.cli.host_evidence", Some("participant")))))
          mismatched.exitCode should not be 0
          val stale = cli.input(description, _proposal, "inventory-before-defer")
          if (choice == "unanswered") {
            // Prove unanswered supersession directly; this old question remains
            // in native history with no fabricated answer.
            target.deferredChoice shouldBe None
          }
          When("public passive preparation imports independently attributable exact direction and adoption appends one guarded receipt")
          cli.direction(describedcall, input)
          cli.adopt(input).business[V.GoalPhaseRemainingPlanAdoptionResult].accepted shouldBe true
          cli.adopt(input).exitCode should not be 0
          cli.adopt(stale).exitCode should not be 0
          val resumed = cli.advance().business[V.GoalPhaseAdvanceResult]
          Then("reopening issues only same-phase Repair with original base prefix and counts")
          resumed.work.get.kind shouldBe "RepairStep"
          resumed.work.get.stepIdentity shouldBe None
          resumed.work.get.completed shouldBe episode.repair.work.get.completed
          resumed.work.get.originalBase shouldBe episode.original.work.get.originalBase
          resumed.repairProgress shouldBe episode.repair.repairProgress
          cli.advance().business[V.GoalPhaseAdvanceResult] shouldBe resumed
          cli.git(Vector("rev-parse", "HEAD")) shouldBe episode.closedtip
          phaseExecutions(episode.root).size shouldBe 1
          inKnownEpisode(episode.root, componentInstance = "default") { reopened =>
            val current = reopened.current(cli.session)
            current.history.take(episode.history.history.size) shouldBe episode.history.history
            val deferred = current.history.filter(_.causation.value == "inventory-human-Defer")
            current.history.find(_.revision.value.toString == target.pendingRevision.get).get.suspension.get.continuationIdentity.value shouldBe
              target.questionIdentity.get
            deferred.size shouldBe (if (choice == "Defer") 1 else 0)
            if (deferred.nonEmpty) current.history.find(_.causation.value == K.causation).get.revision.value shouldBe deferred.head.revision.next.value
            if (choice == "unanswered") current.history.count(_.causation.value == K.humanCausationPrefix + input.reference.identity) shouldBe 1
          }
        }
      }
    }
  }
}
