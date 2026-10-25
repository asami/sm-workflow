package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, parser}
import org.goldenport.cncf.processexecution.GitOperationKind
import java.nio.file.{Files, Path, StandardOpenOption}
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.goldenport.record.RecordPresentable
import org.goldenport.convert.ValueReader
import org.goldenport.record.io.RecordDecoder
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import SplitPhaseExplicitPartitionFixture.take

/** Actual public CLI/SBT/Git on an isolated specification-owned repository.
  * Fixture reviews and _authority are explicit test data, not genuine AI proof.
  * @since   Oct. 24, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class Phase65GoalPhaseFixCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _call(s: F.Scenario, operation: String, parameters: Vector[String]): F.Result =
    sys.props.get("smworkflow.spec.installedCommand") match {
      case Some(executable) => s.callInstalled(Path.of(executable), "GoalPhaseService", operation, parameters,
        Some(Path.of(sys.props.getOrElse("smworkflow.spec.releaseRepairNewInstallations",
          throw new IllegalArgumentException("Installed Release qualification requires releaseRepairNewInstallations")))))
      case None => s.call("command", operation, parameters)
    }
  private def _advance(s: F.Scenario, task: Path): F.Result =
    _call(s, "advanceGoalPhase", Vector("--sessionId", s.retrieve(task)))
  private def _prepare(s: F.Scenario, operation: String, value: RecordPresentable): F.Result =
    sys.props.get("smworkflow.spec.installedCommand") match {
      case Some(executable) => s.callInstalled(Path.of(executable), "NativeWorkflowPreparationService", operation,
        Vector("--input", _json(value)), Some(Path.of(sys.props("smworkflow.spec.releaseRepairNewInstallations"))))
      case None => s.prepareHostInput(operation, value.toRecord())
    }
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def _occurred_at: String = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString
  private def _save(s: F.Scenario, name: String, value: RecordPresentable): String = {
    val path = s.root.resolve(name + ".json")
    Files.writeString(path, _json(value))
    path.toUri.toASCIIString
  }
  private def _result(outcome: String, findings: Vector[String] = Vector.empty,
    report: Option[V.GoalPhaseIssueReport] = None): V.GoalPhaseWorkResult =
    V.GoalPhaseWorkResult(outcome, "Specification-owned assigned work", findings,
      None, None, report, None, None, None, None,
      repairFindingSources = None, dependencyValidation = None, diagnosticRepair = None)
  private def _submit(s: F.Scenario, request: V.GoalPhaseAdvanceResult, value: V.GoalPhaseWorkResult,
    ref: V.WorkflowArtifactReference, execution: Option[V.WorkflowExecutionEvidence] = None): Unit = {
    val response = _call(s, request.submitOperation.get, Vector("--sessionId", request.submitSessionId.get,
      "--result", _json(value), "--resultReference", _json(ref)) ++ execution.toVector.flatMap(e =>
      Vector("--executionEvidence", _json(e)))).business[V.GoalPhaseWorkSubmissionResult]
    response.accepted shouldBe true
    response.outcome shouldBe "accepted"
    response.sessionId shouldBe request.sessionId
  }
  private def _review(s: F.Scenario, actual: F.Result, value: V.GoalPhaseWorkResult, name: String): Unit = {
    val request = actual.business[V.GoalPhaseAdvanceResult]
    val ref = V.WorkflowArtifactReference(name, "1")
    val execution = V.WorkflowExecutionEvidence(Vector(ref), Some("specification-release-reviewer"), None, None)
    val input = V.WorkflowEvidencePreparationInput("GoalPhase", "review", actual.invocation.stdout.toUri.toASCIIString,
      _save(s, name + "-result", value), _save(s, name + "-reference", ref), _save(s, name + "-execution", execution),
      "normal-development-host", _occurred_at, "entity:smworkflow.cli.host_evidence", None)
    val prepared = _prepare(s, "prepareEvidence", input)
    withClue(prepared.invocation.logs) { prepared.exitCode shouldBe 0 }
    s.selectPreparedImport(prepared)
    _submit(s, request, value, ref, Some(execution))
  }
  private def _authority(s: F.Scenario, actual: F.Result, name: String): Unit = {
    val input = V.WorkflowAuthorityPreparationInput(actual.invocation.stdout.toUri.toASCIIString,
      _save(s, name + "-authority-reference", V.WorkflowArtifactReference(name, "1")), "participant",
      "normal-development-host", _occurred_at, "entity:smworkflow.cli.host_evidence")
    val prepared = _prepare(s, "prepareAuthority", input)
    withClue(prepared.invocation.logs) { prepared.exitCode shouldBe 0 }
    s.selectPreparedImport(prepared)
  }
  private def _executions(s: F.Scenario): Vector[String] =
    Files.readString(s.root.resolve("sbt-executions.txt")).linesIterator.toVector
  private def _retained_business[A: ValueReader](path: Path, root: Path): A = {
    require(path.isAbsolute && path.normalize == path && path.toRealPath() == path &&
      path.startsWith(root.resolve("logs")) && Files.isRegularFile(path), "Read only actual retained fixture stdout")
    take(summon[ValueReader[A]].readC(take(new RecordDecoder().json(Files.readString(path)))))
  }
  private def _deny_final_program(s: F.Scenario): String = {
    val path = s.root.resolve(".textus/config.json")
    val original = Files.readString(path)
    val config = parser.parse(original).toOption.get
    val process = parser.parse(config.hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
    val capability = TypedGitHostPlanFixture.capability(GitOperationKind.CommitSelected, Some("goal-step-2"))
    val grants = process.hcursor.get[Vector[Json]]("grants").toOption.get.map { grant =>
      val capabilities = grant.hcursor.get[Vector[String]]("capabilities").toOption.get.filterNot(_ == capability)
      grant.mapObject(_.add("capabilities", Json.fromValues(capabilities.map(Json.fromString))))
    }
    val denied = process.mapObject(_.add("grants", Json.fromValues(grants)))
    Files.writeString(path, config.mapObject(_.add("textus.subsystem.process-execution.plan", Json.fromString(denied.noSpaces))).noSpaces)
    original
  }

  sys.props.get("smworkflow.spec.releaseRepairRetainedFixtures").foreach { raw =>
    val selected = Path.of(raw)
    require(selected.isAbsolute && selected.normalize == selected && Files.isRegularFile(selected),
      "Select the explicit retained Release fixture manifest")
    val manifest = parser.parse(Files.readString(selected)).toOption.get
    require(manifest.hcursor.get[String]("schemaVersion").contains("retained-release-fixtures.v1"), "Select the retained fixture schema")
    val fixtures = manifest.hcursor.get[Vector[Json]]("fixtures").toOption.get
    val logstart = sys.props.get("smworkflow.spec.releaseRepairRetainedLogStart").map(_.toInt).getOrElse(2000)
    require(logstart > 0 && logstart < Int.MaxValue, "Select a positive finite unused retained log start")
    require(fixtures.size == 4 && fixtures.map(value => (
      value.hcursor.get[Boolean]("recording").toOption.get, value.hcursor.get[Boolean]("unknownFinal").toOption.get,
      value.hcursor.get[Boolean]("failedPhase").toOption.get)).toSet ==
      Set((false, false, false), (true, false, false), (false, true, false), (false, false, true)),
      "Select exactly the four retained qualification frontiers")
    "Retained installed FIX continuation" should {
      fixtures.foreach { fixture =>
        val recording = fixture.hcursor.get[Boolean]("recording").toOption.get
        val unknownfinal = fixture.hcursor.get[Boolean]("unknownFinal").toOption.get
        val failedphase = fixture.hcursor.get[Boolean]("failedPhase").toOption.get
        s"continue only the remaining retained frontier recording=$recording unknownFinal=$unknownfinal failedPhase=$failedphase" in {
          Given("the explicitly selected installed image and actual stopped fixture without replaying its successful prefix")
          Vector("smworkflow.spec.installedCommand", "smworkflow.spec.releaseRepairNewInstallations").foreach { name =>
            val path = Path.of(sys.props.getOrElse(name, throw new IllegalArgumentException("Missing " + name)))
            require(path.isAbsolute && path.normalize == path && Files.exists(path), "Select the actual installed fixture image")
          }
          val root = Path.of(fixture.hcursor.get[String]("root").toOption.get)
          val primary = Path.of("").toAbsolutePath.normalize
          require(root.isAbsolute && root.normalize == root && root.toRealPath() == root &&
            root.getParent == primary.resolve("target/sm-workflow/work/native-cli") &&
            root.getFileName.toString.matches(if (recording) "p65-release-fix-recording-[0-9]+" else "p65-release-fix-[0-9]+"),
            "Select only the actual isolated producer Release fixture")
          val session = fixture.hcursor.get[String]("sessionId").toOption.get
          val task = root.resolve("target/sm-workflow/tasks/release-fix-task")
          val routing = parser.parse(Files.readString(task.resolve("workflow-session.json"))).toOption.get
          routing.asObject.get.keys.toSet shouldBe Set("family", "sessionId")
          routing.hcursor.get[String]("family") shouldBe Right("GoalPhase")
          routing.hcursor.get[String]("sessionId") shouldBe Right(session)
          When("the actual retained Phase and Release repair outputs are decoded without constructing an invocation")
          val integrated = _retained_business[V.GoalPhaseAdvanceResult](Path.of(fixture.hcursor.get[String]("phaseOutput").toOption.get), root)
          val originalrepair = _retained_business[V.GoalPhaseAdvanceResult](Path.of(fixture.hcursor.get[String]("repairOutput").toOption.get), root)
          Then("the exact Session Plan base closed Step and original check PIDs identify the retained frontier")
          integrated.sessionId shouldBe session
          originalrepair.sessionId shouldBe session
          integrated.work.get.kind shouldBe "ReviewPhase"
          originalrepair.work.get.kind shouldBe "RepairStep"
          originalrepair.work.get.stepIdentity shouldBe None
          val original = integrated.work.get
          val acceptedprefix = original.completed
          acceptedprefix.size shouldBe 1
          val head = fixture.hcursor.get[String]("head").toOption.get
          head shouldBe acceptedprefix.head.commit.identity
          originalrepair.work.get.completed shouldBe acceptedprefix
          originalrepair.work.get.selectedPlan shouldBe original.selectedPlan
          originalrepair.work.get.originalBase shouldBe original.originalBase
          val oldchecks = fixture.hcursor.get[Vector[String]]("checks").toOption.get
          oldchecks.size shouldBe 4
          oldchecks.foreach(_ should fullyMatch regex "[1-9][0-9]*")
          Files.readString(root.resolve("sbt-executions.txt")).linesIterator.toVector shouldBe oldchecks
          Given("the saved stopped output and the exact isolated host configuration")
          val configpath = root.resolve(".textus/config.json")
          val configtext = Files.readString(configpath)
          val config = parser.parse(configtext).toOption.get
          val host = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(parser.parse(config.hcursor.get[String](
            "sm-workflow.runtime.definition").toOption.get).toOption.get))
          val paths = if (recording) F.findingRecordingPaths else Vector("value.txt", "docs/usage.md")
          host.goal.candidatePaths shouldBe paths
          Path.of(host.target.root) shouldBe root.resolve("repository")
          val plan = parser.parse(Files.readString(root.resolve("goal-plan.json"))).toOption.get.as[GoalPhaseOrderedProgression.Plan].toOption.get
          F.publicPlan(plan) shouldBe original.selectedPlan
          plan.steps.size shouldBe 1
          plan.steps.head.identity shouldBe "step-1"
          plan.steps.head.ownedPaths shouldBe paths
          val programs = Vector(GoalPhaseNativeClosure.StepProgram("step-1", 1), GoalPhaseNativeClosure.StepProgram("phase-release", 2))
          val stoppedpath = Path.of(fixture.hcursor.get[String]("stoppedOutput").toOption.get)
          require(stoppedpath.isAbsolute && stoppedpath.normalize == stoppedpath && stoppedpath.toRealPath() == stoppedpath &&
            stoppedpath.startsWith(root.resolve("logs")) && Files.isRegularFile(stoppedpath), "Select the actual stopped output")
          require(!Files.exists(root.resolve(s"logs/$logstart.stdout.json")) && !Files.exists(root.resolve(s"logs/$logstart.stderr")),
            "Retained continuation logs must be fresh")
          val terminaloutput = fixture.hcursor.get[Option[String]]("terminalOutput").toOption.get.map(value => Path.of(value))
          if (terminaloutput.nonEmpty) {
            require(!failedphase && !unknownfinal, "Only a completed ordinary Final may select passive Terminal verification")
            Given("the actual completed Final stdout and the unchanged fully restored fixture host")
            When("the actual saved Terminal business result is decoded without reconstructing an invocation")
            val terminal = _retained_business[V.GoalPhaseAdvanceResult](terminaloutput.get, root)
            Then("the complete original Plan base and closed Step identify the same already completed Phase")
            terminal.kind shouldBe "Terminal"
            terminal.sessionId shouldBe session
            terminal.repairProgress shouldBe None
            terminal.completion.get.selectedPlan shouldBe original.selectedPlan
            terminal.completion.get.originalBase shouldBe original.originalBase
            terminal.completion.get.completed shouldBe acceptedprefix
            host.goal.closure.programs shouldBe programs
            val s = F.reopenReleaseFix(root, TypedSbtHostPlanFixture.normalExecutable, logstart, recording)
            s.retrieve(task) shouldBe session
            s.candidateHead shouldBe terminal.completion.get.reviewedCandidate.head
            s.candidateParent shouldBe head
            s.candidateIndex shouldBe ""
            _executions(s) shouldBe oldchecks
            if (recording) {
              terminal.findingRecording.get.pending shouldBe empty
              terminal.findingRecording.get.completed.last.result.items.map(_.outcome) shouldBe Vector("recorded", "recorded")
              Vector("docs/journal.md", "docs/candidates.md").foreach(path =>
                Files.readString(Path.of(s.host.target.root).resolve(path)) should startWith (F.findingRecordingBaseline.toMap.apply(path)))
            }
            When("ordinary Advance only observes the already closed retained Session")
            val retained = _advance(s, task).business[V.GoalPhaseAdvanceResult]
            Then("Terminal completion and recording remain identical without another Final or successful check")
            retained.kind shouldBe "Terminal"
            retained.sessionId shouldBe session
            retained.repairProgress shouldBe None
            retained.completion shouldBe terminal.completion
            retained.findingRecording shouldBe terminal.findingRecording
            s.candidateHead shouldBe terminal.completion.get.reviewedCandidate.head
            _executions(s) shouldBe oldchecks
          } else {
          val stopped = if (failedphase) {
            host.goal.closure.programs shouldBe programs
            Files.readString(stoppedpath.resolveSibling(stoppedpath.getFileName.toString.stripSuffix(".stdout.json") + ".stderr")) should
              include ("status=409")
            None
          } else {
            val retained = _retained_business[V.GoalPhaseAdvanceResult](stoppedpath, root)
            retained.sessionId shouldBe session
            retained.kind shouldBe "Wait"
            retained.message should include ("explicit host-fixed phase-release program")
            host.goal.closure.programs shouldBe Vector(programs.head)
            val restored = host.copy(goal = host.goal.copy(closure = host.goal.closure.copy(programs = programs)))
            When("only the pending fixture host program restoration is completed with a fresh saved configuration")
            Files.writeString(root.resolve("retained-missing-program-config-r2.json"), configtext, StandardOpenOption.CREATE_NEW)
            Files.writeString(configpath, config.mapObject(_.add("sm-workflow.runtime.definition",
              Json.fromString(ThreeWorkflowRuntimeParameters.encode(restored).noSpaces))).noSpaces)
            Some(retained)
          }
          val s = F.reopenReleaseFix(root, TypedSbtHostPlanFixture.normalExecutable, logstart, recording)
          Then("the retained caller routing head and exact successful check prefix remain unchanged")
          s.retrieve(task) shouldBe session
          s.candidateHead shouldBe head
          val repochecks = _executions(s)
          repochecks shouldBe oldchecks
          val repo = Path.of(s.host.target.root)
          val originalissues = originalrepair.work.get.issues.get.issues.filter(_.status != "RESOLVED")
          originalissues.size shouldBe 1
          originalissues.head.source shouldBe "REVIEW"
          val finalwait = if (failedphase) {
            val pendingrepair = fixture.hcursor.get[Option[String]]("pendingRepairOutput").toOption.get.map(value => Path.of(value))
            val repair = pendingrepair match {
              case Some(path) =>
                Given("the actual already issued second Release Repair whose previous Submit was refused before acceptance")
                val issued = _retained_business[V.GoalPhaseAdvanceResult](path, root)
                issued.sessionId shouldBe session
                issued.kind shouldBe "Request"
                issued.work.get.kind shouldBe "RepairStep"
                issued.work.get.selectedPlan shouldBe original.selectedPlan
                issued.work.get.originalBase shouldBe original.originalBase
                issued.work.get.completed shouldBe acceptedprefix
                issued.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(2, 2, 2))
                When("ordinary Advance only observes the same pending actual issued repair")
                val observed = _advance(s, task).business[V.GoalPhaseAdvanceResult]
                Then("the exact issued work context and original four check PIDs remain unchanged")
                observed.work shouldBe issued.work
                observed.issuedWorkContext shouldBe issued.issuedWorkContext
                observed.repairProgress shouldBe issued.repairProgress
                _executions(s) shouldBe oldchecks
                observed
              case None =>
                When("ordinary Advance consumes the retained completed failed Phase check without another SBT execution")
                _advance(s, task).business[V.GoalPhaseAdvanceResult]
            }
            Then("the second repair retains the actual original report and exactly the fresh failed TEST issue")
            repair.sessionId shouldBe session
            repair.kind shouldBe "Request"
            repair.work.get.kind shouldBe "RepairStep"
            repair.work.get.stepIdentity shouldBe None
            repair.work.get.selectedPlan shouldBe original.selectedPlan
            repair.work.get.originalBase shouldBe original.originalBase
            repair.work.get.completed shouldBe acceptedprefix
            repair.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(2, 2, 2))
            repair.work.get.review.get.priorReviews shouldBe originalrepair.work.get.review.get.priorReviews
            repair.work.get.review.get.checks shouldBe originalrepair.work.get.review.get.checks
            repair.work.get.review.get.checks.forall(_.exitCode.contains(0)) shouldBe true
            val failure = repair.work.get.checkFailure.get.validation
            failure.outcome shouldBe Some("repair-required")
            failure.checks.last.exitCode.exists(_ != 0) shouldBe true
            val issues = repair.work.get.issues.get.issues.filter(_.status != "RESOLVED")
            issues.size shouldBe 2
            issues.filter(_.source == "REVIEW").map(_.identity) shouldBe originalissues.map(_.identity)
            val testissues = issues.filter(_.source == "TEST")
            testissues.size shouldBe 1
            testissues.head.repository shouldBe original.selectedPlan.repository
            testissues.head.step shouldBe failure.scopeIdentity
            testissues.head.sourceIdentity shouldBe Some(failure.selectedChecks.last.kind + ":" + failure.selectedChecks.last.identity)
            _executions(s) shouldBe oldchecks
            Given("only the new second Release repair fixes both exact assigned issues")
            if (pendingrepair.nonEmpty) Files.readString(repo.resolve("value.txt")) shouldBe "expected\n"
            else Files.writeString(repo.resolve("value.txt"), "expected\n")
            val fixed = V.GoalPhaseIssueReport(issues.map(issue => V.GoalPhaseIssueDisposition(issue.identity,
              "FIXED", "Corrected the retained original REVIEW and newly failed Phase TEST issue", None)))
            When("the new repair is submitted once and the next Advance executes only its new fifth check")
            _submit(s, repair, _result("completed", report = Some(fixed)), V.WorkflowArtifactReference(
              if (pendingrepair.nonEmpty) "retained-second-release-fix-r3" else "retained-second-release-fix-r2", "1"))
            val reviewcall = _advance(s, task)
            val review = reviewcall.business[V.GoalPhaseAdvanceResult]
            Then("scoped review retains the original evidence and resolves only the actual passing TEST issue")
            review.work.get.kind shouldBe "ReviewPhase"
            review.work.get.completed shouldBe acceptedprefix
            review.work.get.review.get.priorReviews shouldBe repair.work.get.review.get.priorReviews
            review.work.get.issues.get.issues.filter(_.source == "TEST").forall(_.status == "RESOLVED") shouldBe true
            _executions(s).take(4) shouldBe oldchecks
            _executions(s).size shouldBe 5
            _executions(s).last should not be oldchecks.last
            val resolved = V.GoalPhaseIssueReport(originalissues.map(issue => V.GoalPhaseIssueDisposition(issue.identity,
              "RESOLVED", "The scoped independent fixture review confirms the original blocker is resolved", None)))
            Vector("result", "reference", "execution").foreach(name =>
              require(!Files.exists(root.resolve("retained-scoped-release-review-r2-" + name + ".json")), "Never replace a retained review sidecar"))
            When("the remaining scoped review is independently prepared and submitted once")
            _review(s, reviewcall, _result("accept", report = Some(resolved)), "retained-scoped-release-review-r2")
            _advance(s, task)
          } else {
            When("ordinary Advance continues only the retained accepted release frontier")
            _advance(s, task)
          }
          val waiting = finalwait.business[V.GoalPhaseAdvanceResult]
          Then("the unchanged original Step awaits only normal Final authority")
          waiting.kind shouldBe "Wait"
          waiting.sessionId shouldBe session
          waiting.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(if (failedphase) 2 else 1, if (failedphase) 2 else 1, if (failedphase) 2 else 1))
          s.candidateHead shouldBe head
          if (!failedphase) _executions(s) shouldBe oldchecks
          require(!Files.exists(root.resolve("retained-final-closure-r2-authority-reference.json")), "Never replace a retained authority sidecar")
          When("the actual remaining Final authority is independently prepared for the new invocation")
          _authority(s, finalwait, "retained-final-closure-r2")
          if (unknownfinal) {
            Given("the selected Final capability becomes unavailable after the remaining authority")
            val originalconfig = _deny_final_program(s)
            When("the new Final attempt saves intent but cannot obtain an actual commit result")
            _advance(s, task).exitCode should not be 0
            Files.writeString(configpath, originalconfig)
            val retained = _advance(s, task).business[V.GoalPhaseAdvanceResult]
            Then("restoration leaves the uncertain intent waiting without replaying it")
            retained.kind shouldBe "Wait"
            _advance(s, task).business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
            s.candidateHead shouldBe head
            _executions(s) shouldBe oldchecks
          } else {
            When("ordinary Advance executes the one remaining authorized Final")
            val terminal = _advance(s, task).business[V.GoalPhaseAdvanceResult]
            Then("one real local Release commit closes the same Session and immutable Step prefix")
            terminal.kind shouldBe "Terminal"
            terminal.sessionId shouldBe session
            terminal.completion.get.completed shouldBe acceptedprefix
            terminal.repairProgress shouldBe None
            s.candidateParent shouldBe head
            s.candidateHead should not be head
            s.candidateIndex shouldBe ""
            val checks = _executions(s)
            checks.take(4) shouldBe oldchecks
            checks.size shouldBe (if (failedphase) 5 else 4)
            if (recording) {
              terminal.findingRecording.get.pending shouldBe empty
              terminal.findingRecording.get.completed shouldBe stopped.get.findingRecording.get.completed
              terminal.findingRecording.get.completed.last.result.items.map(_.outcome) shouldBe Vector("recorded", "recorded")
              Vector("docs/journal.md", "docs/candidates.md").foreach(path =>
                Files.readString(repo.resolve(path)) should startWith (F.findingRecordingBaseline.toMap.apply(path)))
            }
            _advance(s, task).business[V.GoalPhaseAdvanceResult].kind shouldBe "Terminal"
            s.candidateHead shouldBe terminal.completion.get.reviewedCandidate.head
            _executions(s) shouldBe checks
          }
          }
        }
      }
    }
  }

  "P65-Q2 Step and Release FIX" should {
    Vector((false, false, false), (true, false, false), (false, true, false), (false, false, true)).foreach { (recording, unknownfinal, failedphase) =>
      s"repair a failed Step through Release FIX with recording=$recording and unknown Final intent=$unknownfinal and failed fresh Phase check=$failedphase" in {
        Given("a selected product input and its usage contract in one original Step with a bounded repair window")
        val limit = if (failedphase) 2 else 1
        val releasecount = if (failedphase) 2 else 1
        val finalexecutions = if (failedphase) 5 else 4
        val s = F.freshReleaseFix(if (recording) "p65-release-fix-recording" else "p65-release-fix",
          TypedSbtHostPlanFixture.normalExecutable, recording, repairLimit = limit)
        val task = s.ownTask("release-fix-task")
        val started = _call(s, "startGoalPhase", Vector("--input", s.input.noSpaces)).business[V.GoalPhaseStartResult]
        s.retain(task, started)
        val implementation = _advance(s, task).business[V.GoalPhaseAdvanceResult]
        val original = implementation.work.get
        val repo = Path.of(s.host.target.root)
        Files.writeString(repo.resolve("value.txt"), "incorrect\n")
        Files.createDirectories(repo.resolve("docs"))
        Files.writeString(repo.resolve("docs/usage.md"), "The input accepts arbitrary text.\n")
        _submit(s, implementation, _result("completed"), V.WorkflowArtifactReference("fixture-implementation", "1"))
        When("the actual selected Step check fails and ordinary Advance issues the existing FIX")
        val steprepair = _advance(s, task).business[V.GoalPhaseAdvanceResult]
        steprepair.work.get.kind shouldBe "RepairStep"
        steprepair.work.get.stepIdentity shouldBe Some("step-1")
        steprepair.work.get.checkFailure.get.validation.outcome shouldBe Some("repair-required")
        steprepair.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(1, 1, limit))
        _executions(s).size shouldBe 1
        Files.writeString(repo.resolve("value.txt"), "expected\n")
        val stepissues = steprepair.work.get.issues.get.issues.filter(_.status != "RESOLVED")
        val stepfixed = V.GoalPhaseIssueReport(stepissues.map(i => V.GoalPhaseIssueDisposition(i.identity,
          "FIXED", "Corrected the selected actual failed product input", None)))
        _submit(s, steprepair, _result("completed", report = Some(stepfixed)), V.WorkflowArtifactReference("fixture-step-fix", "1"))
        val stepreview = _advance(s, task)
        stepreview.business[V.GoalPhaseAdvanceResult].work.get.kind shouldBe "ReviewStep"
        stepreview.business[V.GoalPhaseAdvanceResult].work.get.issues.get.issues.forall(_.status == "RESOLVED") shouldBe true
        _review(s, stepreview, _result("accept"), "fixture-step-review")
        val stepclosing = _advance(s, task)
        stepclosing.business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
        _authority(s, stepclosing, "fixture-step-closure")
        val phasereview = _advance(s, task)
        val integrated = phasereview.business[V.GoalPhaseAdvanceResult]
        integrated.kind shouldBe "Request"
        integrated.work.get.kind shouldBe "ReviewPhase"
        val acceptedprefix = integrated.work.get.completed
        acceptedprefix.size shouldBe 1
        acceptedprefix.head.commit.identity shouldBe s.candidateHead
        integrated.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(0, 0, limit))
        _executions(s).size shouldBe 3

        When("the integrated reviewer identifies the existing required usage-contract mismatch")
        def _observations_(identity: String, category: String) = Some(V.GoalPhaseReviewObservations(Vector(
          V.GoalPhaseReviewObservation(identity, category, "Retained nonblocking observation", "Record after accepted Phase review"))))
        val originalreview = _result("repair", Vector("docs/usage.md must state that the accepted input is exactly expected followed by LF"))
        _review(s, phasereview, if (recording) originalreview.copy(reviewObservations = _observations_("original-phase-note", "HYGIENE")) else originalreview,
          "fixture-integrated-review")
        val repaircall = _advance(s, task)
        val repair = repaircall.business[V.GoalPhaseAdvanceResult]
        Then("ordinary Advance issues Release repair without a Human question new Plan or reopened Step")
        repair.kind shouldBe "Request"
        repair.work.get.kind shouldBe "RepairStep"
        repair.work.get.stepIdentity shouldBe None
        repair.work.get.selectedPlan shouldBe original.selectedPlan
        repair.work.get.originalBase shouldBe original.originalBase
        repair.work.get.completed shouldBe acceptedprefix
        repair.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(1, 1, limit))
        if (recording) {
          repair.findingRecording.get.pending.map(_.observation.identity) shouldBe Vector("original-phase-note")
          repair.findingRecording.get.request shouldBe None
        }
        repair.work.get.review.get.priorReviews.exists(r => take(r.toJsonStringC).contains("ReviewPhase")) shouldBe true
        val open = repair.work.get.issues.get.issues.filter(_.status != "RESOLVED")
        open.size shouldBe 1
        open.head.source shouldBe "REVIEW"
        s.candidateHead shouldBe acceptedprefix.head.commit.identity
        _executions(s).size shouldBe 3
        val repeated = _advance(s, task).business[V.GoalPhaseAdvanceResult]
        repeated shouldBe repair

        Given("only the assigned release finding is corrected within the original ownership")
        Files.writeString(repo.resolve("docs/usage.md"), "The accepted input is exactly expected followed by LF.\n")
        val fixed = V.GoalPhaseIssueReport(open.map(i => V.GoalPhaseIssueDisposition(i.identity,
          "FIXED", "Corrected the exact accepted-input description; validation and review remain pending", None)))
        if (failedphase) Files.writeString(repo.resolve("value.txt"), "incorrect after the first Release correction\n")
        _submit(s, repair, _result("completed", report = Some(fixed)), V.WorkflowArtifactReference("fixture-release-fix", "1"))
        val rereviewcall = if (!failedphase) _advance(s, task) else {
          Given("the first ordinary Release correction causes the actual selected Phase check to fail")
          When("ordinary Advance issues the next Release RepairStep with the original integrated report and fresh failed check")
          val failedcall = _advance(s, task)
          val failedrepair = failedcall.business[V.GoalPhaseAdvanceResult]
          Then("the issued repair retains original review evidence and exactly the newly failed check")
          failedrepair.kind shouldBe "Request"
          failedrepair.work.get.kind shouldBe "RepairStep"
          failedrepair.work.get.stepIdentity shouldBe None
          failedrepair.work.get.selectedPlan shouldBe original.selectedPlan
          failedrepair.work.get.originalBase shouldBe original.originalBase
          failedrepair.work.get.completed shouldBe acceptedprefix
          failedrepair.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(2, 2, limit))
          val historical = failedrepair.work.get.review.get
          historical.checks shouldBe repair.work.get.review.get.checks
          historical.checks.forall(_.exitCode.contains(0)) shouldBe true
          historical.priorReviews shouldBe repair.work.get.review.get.priorReviews
          historical.priorReviews.exists(value => take(value.toJsonStringC).contains("fixture-integrated-review")) shouldBe true
          val actualfailure = failedrepair.work.get.checkFailure.get.validation
          actualfailure.outcome shouldBe Some("repair-required")
          actualfailure.checks.last.exitCode.exists(_ != 0) shouldBe true
          actualfailure.candidate.tree should not be integrated.work.get.candidate.get.tree
          val assigned = failedrepair.work.get.issues.get.issues.filter(_.status != "RESOLVED")
          assigned.filter(_.source == "REVIEW").map(_.identity) shouldBe open.map(_.identity)
          assigned.filter(_.source == "TEST").size shouldBe 1
          assigned.filter(_.source == "TEST").head.step shouldBe actualfailure.scopeIdentity
          assigned.filter(_.source == "TEST").head.sourceIdentity shouldBe Some(
            actualfailure.selectedChecks.last.kind + ":" + actualfailure.selectedChecks.last.identity)
          assigned.size shouldBe 2
          _executions(s).size shouldBe 4
          _advance(s, task).business[V.GoalPhaseAdvanceResult] shouldBe failedrepair
          Files.writeString(repo.resolve("value.txt"), "expected\n")
          val corrected = V.GoalPhaseIssueReport(assigned.map(issue => V.GoalPhaseIssueDisposition(issue.identity,
            "FIXED", "Corrected the exact original REVIEW issue and actual failed Phase TEST issue", None)))
          _submit(s, failedrepair, _result("completed", report = Some(corrected)), V.WorkflowArtifactReference("fixture-second-release-fix", "1"))
          Then("exact legitimate dispositions are accepted and the next separate Advance executes only the remaining fresh check")
          _advance(s, task)
        }
        val rereview = rereviewcall.business[V.GoalPhaseAdvanceResult]
        Then("actual fresh selected checks precede the affected review and preserve the original report")
        rereview.kind shouldBe "Request"
        rereview.work.get.kind shouldBe "ReviewPhase"
        rereview.work.get.completed shouldBe acceptedprefix
        rereview.work.get.selectedPlan shouldBe original.selectedPlan
        rereview.work.get.review.get.priorReviews.exists(r => take(r.toJsonStringC).contains("ReviewPhase")) shouldBe true
        rereview.work.get.review.get.checks.head.exitCode shouldBe Some(0)
        if (failedphase) {
          rereview.work.get.issues.get.issues.filter(_.source == "TEST").forall(_.status == "RESOLVED") shouldBe true
          rereview.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(2, 2, limit))
        }
        _executions(s).size shouldBe finalexecutions
        if (recording) {
          rereview.findingRecording.get.pending.map(_.sourceReference.identity) shouldBe Vector("fixture-integrated-review")
          rereview.findingRecording.get.request shouldBe None
          Files.readString(repo.resolve("docs/journal.md")) shouldBe F.findingRecordingBaseline.toMap.apply("docs/journal.md")
        }
        val covered = V.GoalPhaseIssueReport(open.map(i => V.GoalPhaseIssueDisposition(i.identity,
          "RESOLVED", "Scoped review confirms the usage contract now matches the unchanged product input", None)))
        val acceptedreview = _result("accept", report = Some(covered))
        _review(s, rereviewcall, if (recording) acceptedreview.copy(reviewObservations = _observations_("accepted-phase-note", "DEVELOPMENT_CANDIDATE"))
          else acceptedreview, "fixture-scoped-release-review")
        if (recording) {
          val assignedcall = _advance(s, task)
          val assigned = assignedcall.business[V.GoalPhaseAdvanceResult]
          assigned.submitOperation shouldBe Some("submitGoalPhaseFindingRecording")
          val request = assigned.findingRecording.get.request.get
          request.stepIdentity shouldBe None
          request.items.map(_.sourceReference.identity) shouldBe Vector("fixture-integrated-review", "fixture-scoped-release-review")
          request.items.map(_.observation.identity) shouldBe Vector("original-phase-note", "accepted-phase-note")
          request.items.map(_.sourceKind) shouldBe Vector("ReviewPhase", "ReviewPhase")
          request.items.map(_.destination) shouldBe Vector("docs/journal.md", "docs/candidates.md")
          request.completed shouldBe acceptedprefix
          _executions(s).size shouldBe finalexecutions
          _advance(s, task).business[V.GoalPhaseAdvanceResult] shouldBe assigned
          val items = request.items.map { item =>
            val path = repo.resolve(item.destination)
            val section = "## " + item.observation.identity
            Files.writeString(path, Files.readString(path) + "\n" + section + "\n\n" +
              item.sourceReference.identity + "@" + item.sourceReference.revision + ": " + item.observation.summary + "\n")
            V.GoalPhaseFindingRecordingItemResult(item.sourceReference, item.observation.identity, "recorded",
              Vector(V.GoalPhaseFindingSavedSection(item.destination, section)), "Appended the exact source and observation")
          }
          val reply = V.GoalPhaseFindingRecordingResult("completed", "Recorded both actual Phase sources", items)
          val ack = _call(s, assigned.submitOperation.get, Vector("--sessionId", assigned.submitSessionId.get,
            "--result", _json(reply), "--resultReference", _json(V.WorkflowArtifactReference("fixture-phase-recording", "1"))))
            .business[V.GoalPhaseFindingRecordingSubmissionResult]
          ack.accepted shouldBe true
          val reviewedusage = Files.readString(repo.resolve("docs/usage.md"))
          When("an owned release file changes after its independent acceptance")
          Files.writeString(repo.resolve("docs/usage.md"), "Unreviewed release content.\n")
          _advance(s, task).exitCode should not be 0
          s.stageReleaseRecordingCounterexample()
          _advance(s, task).exitCode should not be 0
          Then("both unstaged and staged corruption reject before a recording intent or Final commit")
          s.candidateHead shouldBe acceptedprefix.head.commit.identity
          _executions(s).size shouldBe finalexecutions
          Files.writeString(repo.resolve("docs/usage.md"), reviewedusage)
          s.stageReleaseRecordingCounterexample()
        }
        Given("the accepted Release candidate while the fixed Final program is temporarily absent")
        val finalconfigpath = s.root.resolve(".textus/config.json")
        val finalconfig = Files.readString(finalconfigpath)
        val missingprogram = s.host.copy(goal = s.host.goal.copy(closure = s.host.goal.closure.copy(programs =
          s.host.goal.closure.programs.filterNot(_.step == GoalPhaseNativeReleaseClosure.programIdentity))))
        Files.writeString(finalconfigpath, parser.parse(finalconfig).toOption.get.mapObject(_.add("sm-workflow.runtime.definition",
          Json.fromString(ThreeWorkflowRuntimeParameters.encode(missingprogram).noSpaces))).noSpaces)
        When("Final has no explicit phase-release host program")
        val missingfinal = _advance(s, task).business[V.GoalPhaseAdvanceResult]
        Then("the runtime waits for the host-fixed phase-release program and retains the accepted checks")
        missingfinal.kind shouldBe "Wait"
        missingfinal.message should include ("explicit host-fixed phase-release program")
        s.candidateHead shouldBe acceptedprefix.head.commit.identity
        _executions(s).size shouldBe finalexecutions
        Files.writeString(finalconfigpath, finalconfig)
        val finalwait = _advance(s, task)
        finalwait.business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
        finalwait.business[V.GoalPhaseAdvanceResult].repairProgress shouldBe Some(V.GoalPhaseRepairProgress(releasecount, releasecount, limit))
        _authority(s, finalwait, "fixture-final-closure")
        if (unknownfinal) {
          Given("the actual Final commit capability becomes unavailable after its accepted authority and candidate")
          val config = _deny_final_program(s)
          When("the normal Final executor retains its intent but cannot obtain an actual commit result")
          _advance(s, task).exitCode should not be 0
          Files.writeString(s.root.resolve(".textus/config.json"), config)
          val retained = _advance(s, task).business[V.GoalPhaseAdvanceResult]
          Then("restoring the host does not replay that uncertain intent or claim Terminal")
          retained.kind shouldBe "Wait"
          _advance(s, task).business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
          s.candidateHead shouldBe acceptedprefix.head.commit.identity
          _executions(s).size shouldBe finalexecutions
        } else {
        val completed = _advance(s, task).business[V.GoalPhaseAdvanceResult]
        Then("Final creates a real single-parent local commit and reaches Terminal with unchanged Step history and repair use")
        completed.kind shouldBe "Terminal"
        completed.sessionId shouldBe started.sessionId
        completed.completion.get.completed shouldBe acceptedprefix
        s.candidateParent shouldBe acceptedprefix.head.commit.identity
        s.candidateHead should not be acceptedprefix.head.commit.identity
        s.candidateIndex shouldBe ""
        _executions(s).size shouldBe finalexecutions
        if (recording) {
          completed.findingRecording.get.pending shouldBe empty
          completed.findingRecording.get.completed.last.result.items.map(_.outcome) shouldBe Vector("recorded", "recorded")
          Vector("docs/journal.md", "docs/candidates.md").foreach(path =>
            Files.readString(repo.resolve(path)) should startWith (F.findingRecordingBaseline.toMap.apply(path)))
        }
        s.retrieve(task) shouldBe started.sessionId
        val retained = _advance(s, task).business[V.GoalPhaseAdvanceResult]
        retained.kind shouldBe "Terminal"
        s.candidateHead shouldBe completed.completion.get.reviewedCandidate.head
        _executions(s).size shouldBe finalexecutions
        }
      }
    }
  }
}
