package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.file.{Files, Path, StandardOpenOption}
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.SbtOperation
import org.goldenport.cncf.workflow.{WorkflowInstanceRecordJsonV1, WorkflowMechanicalTransition, WorkflowMechanicalTransitionJsonV1}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseDevelopmentReconstruction as R
import GoalPhaseNativeValidation as N
import GoalPhaseOrderedProgression as O
import NativeCallerExecutionFixture.json
import NativeWorkflowCliFixture as F
import SplitPhaseExplicitPartitionFixture.take

/** Installed validation binds the actual checkout and reconstructs only a settled native prefix.
  * @since   Nov. 24, 2026
  * @version Nov. 24, 2026
  */
@org.scalatest.DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class GoalPhasePrimaryValidationWorkspaceSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  private val _phase = "docs/phase.md"
  private val _checklist = "docs/checklist.md"
  private val _process_key = "textus.subsystem.process-execution.plan"
  private val _fixture = new NativeGoalDependencyValidationCliSpec

  "Installed ordinary GoalPhase validation" should {
    "reject a crossed primary workspace and reconstruct the exact old Session after a terminated check and a prelaunch refusal" in {
      Given("the explicitly selected normal installed command, CAR and task-private locator")
      val mode = _property("primaryValidationWorkspaceMode")
      val locator = Path.of(_property("primaryValidationWorkspaceEpisode"))
      val installed = Path.of(_property("installedCommand"))
      installed.isAbsolute shouldBe true
      Files.isExecutable(installed) shouldBe true
      _component_file
      locator.isAbsolute shouldBe true
      locator.normalize shouldBe locator
      Files.isDirectory(locator.getParent) shouldBe true

      When("the requested capture or continuation executes against that installed component")
      val evidence = mode match {
        case "capture-old" => _capture(locator)
        case "continue-new" => _continue(locator)
        case _ => fail("Select capture-old or continue-new explicitly")
      }
      Then("the actual Session observations and public invocation evidence remain available")
      Files.isRegularFile(evidence) shouldBe true
    }
  }

  private def _property(name: String): String =
    sys.props.getOrElse("smworkflow.spec." + name, fail("Supply smworkflow.spec." + name))

  private def _component_file: Path = {
    val path = Path.of(_property("componentFile"))
    require(path.isAbsolute && path.normalize == path && Files.isRegularFile(path),
      "Select the actual absolute normal installed CAR")
    path
  }

  private def _read(path: Path): Json =
    parse(Files.readString(path)).fold(problem => fail(problem.message), identity)

  private def _array(value: Json, name: String): Vector[Json] =
    value.hcursor.get[Vector[Json]](name).toOption.get

  private def _location(value: Json, name: String): Path =
    Path.of(value.hcursor.get[String](name).toOption.get)

  private def _lines(path: Path): Vector[String] =
    if (Files.isRegularFile(path)) Files.readAllLines(path).toArray(new Array[String](0)).toVector else Vector.empty

  private def _configure(root: Path, config: Json, processes: Json): Unit =
    Files.writeString(root.resolve(".textus/config.json"), config.mapObject(
      _.add("textus.component.file", _component_file.toString.asJson)
        .add(_process_key, processes.noSpaces.asJson)).noSpaces)

  private def _set_workspace(processes: Json, ordinal: Int, root: Path): Json = {
    val workspace = "sm-workflow-repository-validation-" + ordinal
    val bindings = _array(processes, "workspaces")
    bindings.count(_.hcursor.get[String]("identity").contains(workspace)) shouldBe 1
    processes.mapObject(_.add("workspaces", bindings.map { entry =>
      if (entry.hcursor.get[String]("identity").contains(workspace)) entry.mapObject(_.add("root", root.toString.asJson))
      else entry
    }.asJson))
  }

  private final case class Episode(scenario: F.Scenario, task: Path, request: V.GoalPhaseAdvanceResult, accepted: F.Result)

  private def _begin(name: String): Episode = {
    val executable = TypedSbtHostPlanFixture.normalExecutable
    val initial = F.fresh(name, sbtRepair = Some(executable), repairContinuation = true)
    val root = initial.root
    val repository = Path.of(initial.host.target.root)
    val alternate = root.resolve("alternate")
    TypedSbtHostPlanFixture.build(alternate, successful = true)
    Files.writeString(repository.resolve(_checklist), "# Checklist\n- [ ] current: Check the current input.\n")
    Files.writeString(repository.resolve("build.sbt"), """
      |lazy val fixCheck = taskKey[Unit]("Record the exact owned candidate checkout")
      |fixCheck := {
      |  val root = baseDirectory.value
      |  IO.append(root.getParentFile / "fix-check-executions.txt", root.getCanonicalPath + "\n")
      |}
      |""".stripMargin, StandardOpenOption.APPEND)
    val git = Vector("-C", repository.toString)
    take(_run_setup_command_(git ++ Vector("add", "--", _checklist, "build.sbt")))
    take(_run_setup_command_(git ++ Vector("-c", "user.name=Validation Workspace Fixture",
      "-c", "user.email=validation-workspace@example.invalid", "commit", "--no-verify", "--no-gpg-sign",
      "-m", "Arrange fixture build and checklist before Start")))

    val checks = Vector(N.Check("test", 1, WorkflowSbtValidation.Kind.Test),
      N.Check("fix-check", 2, WorkflowSbtValidation.Kind.FixCheck))
    val host = initial.host.copy(goal = initial.host.goal.copy(
      stepValidation = initial.host.goal.stepValidation.copy(checks = checks)))
    val original = initial.plan
    val plan = original.copy(steps = original.steps.updated(0, original.steps.head.copy(checks = checks.map(_.identity))))
    val planfile = root.resolve("goal-plan.json")
    Files.writeString(planfile, plan.asJson.noSpaces)
    val config = _read(root.resolve(".textus/config.json"))
    val base = parse(config.hcursor.get[String](_process_key).toOption.get).toOption.get
    val fixcheck = TypedSbtHostPlanFixture.plan(root, executable, Vector(2), operation = SbtOperation.FixCheck)
    val combined = base.mapObject { fields =>
      val programs = _array(base, "sbtPrograms") ++ _array(fixcheck, "sbtPrograms")
      val capabilities = (_array(base, "gitPrograms") ++ programs).map(_.hcursor.get[String]("capability").toOption.get)
      fields.add("sbtPrograms", programs.asJson)
        .add("workspaces", (_array(base, "workspaces") ++ _array(fixcheck, "workspaces")).asJson)
        .add("workspaceGrants", (_array(base, "workspaceGrants") ++ _array(fixcheck, "workspaceGrants")).asJson)
        .add("grants", Json.arr(Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson,
          "capabilities" -> capabilities.distinct.asJson)))
    }
    val wrongroot = alternate.resolve("repository")
    val crossed = _set_workspace(_set_workspace(combined, 1, wrongroot), 2, wrongroot)
    _configure(root, config.mapObject(
      _.add("sm-workflow.runtime.definition", ThreeWorkflowRuntimeParameters.encode(host).noSpaces.asJson)),
      crossed)
    val prepared = take(org.simplemodeling.textus.smworkflow.preparation.NativeGoalStartInput.prepareC(Map(
      "input" -> root.resolve("goal-input.json").toUri.toASCIIString, "plan" -> planfile.toUri.toASCIIString,
      "selection-id" -> "native-goal-selection", "participant" -> "participant", "origin" -> "ExplicitHuman",
      "idempotency-key" -> "native-specification-input", "selection-collection" -> "entity:smworkflow.cli.goal_selection",
      "plan-collection" -> "entity:smworkflow.cli.goal_plans"), resource =>
      Consequence(Files.readString(Path.of(java.net.URI.create(resource))))))
    Files.writeString(root.resolve("selected-input.json"), take(prepared.toJsonStringC))
    val scenario = F.reopenCanonicalSource(root, host, plan, 100)
    val task = scenario.ownTask("primary-validation-workspace")
    scenario.retain(task, scenario.start().business[V.GoalPhaseStartResult])
    val request = scenario.readGoalPhaseResult(scenario.advance(task))
    request.work.get.kind shouldBe "ImplementStep"
    Files.writeString(repository.resolve("value.txt"), "expected\n")
    val outcome = Json.obj("outcome" -> "completed".asJson, "summary" -> "Set the actual fixture product input".asJson,
      "findings" -> Json.arr())
    val reference = V.WorkflowArtifactReference("workspace-fixture-implementation", "1")
    val accepted = scenario.call("command", request.submitOperation.get,
      Vector("--sessionId", request.submitSessionId.get, "--result", outcome.noSpaces,
        "--resultReference", json(reference.toRecord()).noSpaces))
    accepted.business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
    Episode(scenario, task, request, accepted)
  }

  private def _reconstruct(scenario: F.Scenario, input: Path): F.Result =
    scenario.call("command", R.operation, Vector("--inputResource", input.toUri.toASCIIString))

  private def _capture(locator: Path): Path = {
    Given("a new tiny candidate whose two typed validation workspaces point at a separate build")
    Files.exists(locator) shouldBe false
    val episode = _begin("primary-validation-workspace-old")
    val scenario = episode.scenario
    val root = scenario.root
    val session = episode.request.sessionId
    val repository = Path.of(scenario.host.target.root)

    When("the old installed runtime advances the accepted semantic work")
    val stopped = scenario.advance(episode.task)
    val waiting = scenario.readGoalPhaseResult(stopped)
    Then("the wrong checkout's actual Test succeeds, and the next FixCheck is denied before dispatch")
    waiting.kind shouldBe "Wait"
    waiting.message should include("rejected admission before execution")
    waiting.message should include("selected FixCheck binding does not match the Session candidate checkout")
    waiting.validation.get.checks.map(check => (check.identity, check.termination, check.exitCode)) shouldBe
      Vector(("test", "exited", Some(0)))
    waiting.validation.get.checks.head.stdout should include(root.resolve("alternate/repository").toString)
    _lines(root.resolve("sbt-executions.txt")) shouldBe empty
    _lines(root.resolve("alternate/sbt-executions.txt")).size shouldBe 1
    _lines(root.resolve("fix-check-executions.txt")) shouldBe empty
    val rows = _fixture.retainedValidationHistory(scenario, session)
    val input = root.resolve("reconstruct-primary-validation.json")
    val proposal = GoalPhasePlanRevision.Proposal("workspace-current-source", "1", "Retain the exact ordinary Plan",
      scenario.plan)
    Files.writeString(input, json(V.GoalPhaseDevelopmentReconstructionInput(session, _phase, _checklist,
      GoalPhaseNativeMetadata.record(proposal.asJson), "Reconstruct changed owned input on the same Plan").toRecord()).noSpaces)
    Files.writeString(repository.resolve(_phase), "\nCurrent source clarifies the same requirement.\n", StandardOpenOption.APPEND)

    When("the existing reconstruction operation receives the same Plan with changed owned source")
    val rejected = _reconstruct(scenario, input)
    Then("the old operation returns a conflict and the exact successful and rejected prefix remains in storage")
    rejected.exitCode should not be 0
    Files.readString(rejected.invocation.stderr) should include("status=409")
    Files.readString(rejected.invocation.stderr) should include("Collect the actual pending work or Human decision")
    _fixture.retainedValidationHistory(scenario, session) shouldBe rows
    val record = Json.obj("root" -> root.toString.asJson, "task" -> episode.task.toString.asJson,
      "sessionId" -> session.asJson, "input" -> input.toString.asJson, "host" -> ThreeWorkflowRuntimeParameters.encode(scenario.host),
      "plan" -> scenario.plan.asJson, "candidateHead" -> scenario.candidateHead.asJson, "retainedRows" -> rows.asJson,
      "implementation" -> episode.accepted.invocation.stdout.toString.asJson,
      "stop" -> stopped.invocation.stdout.toString.asJson, "oldConflict" -> rejected.invocation.stderr.toString.asJson,
      "oldSourceRevision" -> waiting.validation.get.sourceRevision.asJson, "oldTree" -> waiting.validation.get.candidate.tree.asJson,
      "car" -> _component_file.toString.asJson, "config" -> Files.readString(root.resolve(".textus/config.json")).asJson)
    Files.writeString(locator, record.spaces2 + "\n", StandardOpenOption.CREATE_NEW)
    locator
  }

  private def _continue(locator: Path): Path = {
    Given("the exact private old host and database with its original accepted implementation and typed receipts")
    val old = _read(locator)
    val root = _location(old, "root")
    val host = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(old.hcursor.get[Json]("host").toOption.get))
    val plan = old.hcursor.get[O.Plan]("plan").toOption.get
    val scenario = F.reopenCanonicalSource(root, host, plan, 1000)
    val task = _location(old, "task")
    val session = scenario.retrieve(task)
    session shouldBe old.hcursor.get[String]("sessionId").toOption.get
    val original = old.hcursor.get[Map[String, String]]("retainedRows").toOption.get
    _fixture.retainedValidationHistory(scenario, session) shouldBe original
    val sourcerevision = old.hcursor.get[String]("oldSourceRevision").toOption.get
    val admitted = _attempt(original, session, sourcerevision, "check-result-1")
    val pending = _attempt(original, session, sourcerevision, "check-intent-1")

    When("the recorded refusal and the original unresolved intent are considered at their exact respective frontiers")
    val acceptedprefix = N.completedPrelaunchRejectionPrefix(admitted)
    val pendingprefix = N.completedPrelaunchRejectionPrefix(pending)
    Then("only the known prelaunch refusal with a completed prefix is suitable for reconstruction")
    acceptedprefix shouldBe true
    pendingprefix shouldBe false
    val uncertain = admitted.payload.copy(admissionFailures = Vector.empty,
      uncertainties = Vector(N.Uncertainty("fix-check", 0, "The effect could not be determined")))
    N.completedPrelaunchRejectionPrefix(admitted.copy(payload = uncertain)) shouldBe false
    admitted.payload.admissionFailures.map(value => (value.checkIdentity, value.attempt)) shouldBe Vector(("fix-check", 0))

    Given("the actual same candidate root registered for both normal typed programs")
    val config = _read(root.resolve(".textus/config.json"))
    val processes = parse(config.hcursor.get[String](_process_key).toOption.get).toOption.get
    val repository = Path.of(host.target.root)
    _configure(root, config, _set_workspace(_set_workspace(processes, 1, repository), 2, repository))

    When("the same public reconstruction is adopted before a separate ordinary Advance")
    val selected = _reconstruct(scenario, _location(old, "input"))
    val result = scenario.readGoalPhaseResult(selected)
    Then("the existing Session is ready for fresh capture without executing a check or changing old history")
    result.kind shouldBe "Wait"
    result.sessionId shouldBe session
    result.human shouldBe None
    _lines(root.resolve("sbt-executions.txt")) shouldBe empty
    _lines(root.resolve("fix-check-executions.txt")) shouldBe empty
    _preserves(original, _fixture.retainedValidationHistory(scenario, session))

    When("ordinary Advance captures and checks current source using the corrected primary checkout")
    val advanced = scenario.advance(task)
    val review = scenario.readGoalPhaseResult(advanced)
    val current = _fixture.retainedValidationHistory(scenario, session)
    Then("a fresh candidate reaches independent review with the same Session, Plan, base and repair counts")
    review.sessionId shouldBe session
    review.work.get.kind shouldBe "ReviewStep"
    review.work.get.selectedPlan shouldBe F.publicPlan(plan)
    review.work.get.candidate.get.tree should not be old.hcursor.get[String]("oldTree").toOption.get
    review.work.get.originalBase.head shouldBe old.hcursor.get[String]("candidateHead").toOption.get
    review.repairProgress shouldBe result.repairProgress
    scenario.candidateHead shouldBe review.work.get.originalBase.head
    _lines(root.resolve("sbt-executions.txt")).size shouldBe 1
    _lines(root.resolve("fix-check-executions.txt")) shouldBe Vector(repository.toString)
    _lines(root.resolve("alternate/sbt-executions.txt")).size shouldBe 1
    _preserves(original, current)

    Given("a separate private candidate on the same new runtime whose first typed workspace is crossed")
    val crossed = _begin("primary-validation-workspace-crossed")
    When("the new runtime first prepares that crossed primary Test")
    val refused = crossed.scenario.advance(crossed.task)
    val rejection = crossed.scenario.readGoalPhaseResult(refused)
    Then("it records prelaunch refusal without a Test receipt or any effect in either checkout")
    rejection.kind shouldBe "Wait"
    rejection.message should include("rejected admission before execution")
    rejection.validation.get.checks shouldBe empty
    val foreign = crossed.scenario.root
    _lines(foreign.resolve("alternate/sbt-executions.txt")) shouldBe empty
    _lines(foreign.resolve("sbt-executions.txt")) shouldBe empty
    _lines(foreign.resolve("fix-check-executions.txt")) shouldBe empty
    val evidence = root.resolve("primary-validation-reconstruction-result.json")
    Files.writeString(evidence, Json.obj("sessionId" -> session.asJson, "car" -> _component_file.toString.asJson,
      "reconstruct" -> selected.invocation.stdout.toString.asJson, "freshReview" -> advanced.invocation.stdout.toString.asJson,
      "crossed" -> refused.invocation.stdout.toString.asJson, "retainedRows" -> current.asJson).spaces2 + "\n",
      StandardOpenOption.CREATE_NEW)
    evidence
  }

  private def _preserves(old: Map[String, String], current: Map[String, String]): Unit = {
    (old - "instance").foreach { case (key, value) => current.get(key) shouldBe Some(value) }
    val definitions = take(WorkflowInstanceRecordJsonV1.catalogC(Vector(take(GoalPhaseRuntimeCatalog.loadC()).definition)))
    val prior = take(WorkflowInstanceRecordJsonV1.decodeC(old("instance"), definitions))
    val later = take(WorkflowInstanceRecordJsonV1.decodeC(current("instance"), definitions))
    later.history.take(prior.history.size) shouldBe prior.history
  }

  private def _attempt(rows: Map[String, String], session: String, source: String, key: String): N.Saved = {
    val id = take(WorkflowMechanicalTransition.keyC(WorkflowMechanicalTransition.Reference(
      session + ":ordered-validation:" + source + ":" + key, "1")))
    val raw = parse(rows(id)).toOption.get.hcursor.get[String]("mechanicalPayload").toOption.get
    raw should startWith("cncf.workflow-json.v1:")
    val definitions = take(WorkflowInstanceRecordJsonV1.catalogC(Vector(take(GoalPhaseRuntimeCatalog.loadC()).definition)))
    val transition = take(WorkflowMechanicalTransitionJsonV1.decodeC(raw.stripPrefix("cncf.workflow-json.v1:"),
      definitions, N.codec))
    N.Saved(transition.next, transition.payload.value)
  }
}
