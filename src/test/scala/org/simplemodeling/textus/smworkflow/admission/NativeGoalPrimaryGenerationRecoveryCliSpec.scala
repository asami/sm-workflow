package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.file.{Files, Path, StandardOpenOption}
import java.time.Instant
import org.goldenport.convert.ValueReader
import org.goldenport.cncf.processexecution.{GitOperationKind, SbtOperation}
import org.goldenport.cncf.workflow.{WorkflowMechanicalReceiptLimits, WorkflowMechanicalReceiptParameters}
import org.goldenport.record.{Record, RecordPresentable}
import org.goldenport.record.io.RecordDecoder
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhasePrerequisitePrimaryRepair as A
import GoalPhasePrerequisitePublicationRecovery as B
import GoalPhaseOrderedProgression as O
import GoalPhaseSourceHistoryRecovery as R
import GoalPhaseSourceHistoryNormalization as N
import NativeWorkflowCliFixture as F
import SplitPhaseExplicitPartitionFixture.take

/** Immutable external settings and read-only native codec views for the tiny
  * paired history subject. Introduced Nov. 6, 2026 by ASAMI, Tomoharu. */
private[admission] object PrimaryGenerationHistorySpecificationFixture extends NativeGoalSourceHistoryRecoveryCliSupport {
  val rule: Json = enabledSettings(ruleKey).mapObject(_.add("sourceRoots", Vector("src/main/scala").asJson))
  def native(fixture: Implemented): Vector[String] = _stored_json(fixture).sorted
  def current(fixture: Implemented): org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord = _current(fixture)
  def intent(fixture: Implemented, target: R.RecoveryTarget): GoalPhaseNativeSourceHistory.Payload =
    _receipt(fixture, org.goldenport.cncf.workflow.WorkflowMechanicalTransition.Reference(target.intentIdentity, target.intentRevision),
      GoalPhaseNativeSourceHistory.codec).payload.value
  def primary(fixture: Implemented): Vector[A.Saved] = _stored_json(fixture).flatMap(raw =>
    org.goldenport.cncf.workflow.WorkflowMechanicalTransitionJsonV1.decodeC(raw, _catalog, A.codec).toOption)
    .map(receipt => A.Saved(receipt.next, receipt.payload.value))
  def intents(fixture: Implemented): Vector[GoalPhaseNativeSourceHistory.Payload] = _stored_json(fixture).flatMap(raw =>
    org.goldenport.cncf.workflow.WorkflowMechanicalTransitionJsonV1.decodeC(raw, _catalog, GoalPhaseNativeSourceHistory.codec).toOption)
    .filter(_.reference.identity.contains(":source-history-intent-")).map(_.payload.value)
}

/** Paired installed-only qualification of one real tiny sbt-cozy failure.
  * The locator is caller-owned routing, never native state or consumer authority.
  * All publication/check effects use the normal installed CLI and typed UoW.
  * @since   Nov.  6, 2026
  *  version Nov.  6, 2026
  * @version Nov.  8, 2026
  * @author  ASAMI, Tomoharu
  */
@org.scalatest.DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalPrimaryGenerationRecoveryCliSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  private val _source = "src/main/cozy/generation.cml"
  private val _history_source = "src/main/scala/fixture/primarygeneration/HistoryProbe.scala"
  private val _history_duplicate = """package fixture.primarygeneration
    |
    |/** First executable declaration history.
    |  * @since Nov. 6, 2026
    |  * @version Nov. 6, 2026
    |  * @author ASAMI, Tomoharu
    |  */
    |object HistoryProbe { val value: String = "retained-history-value" }
    |
    |/** Second executable declaration history.
    |  * @since Nov. 6, 2026
    |  * @version Nov. 6, 2026
    |  * @author ASAMI, Tomoharu
    |  */
    |object HistoryCompanion { val value: String = HistoryProbe.value }
    |""".stripMargin
  private val _history_corrected = """package fixture.primarygeneration
    |
    |/** File history for both executable declarations.
    |  * @since Nov. 6, 2026
    |  * @version Nov. 6, 2026
    |  * @author ASAMI, Tomoharu
    |  */
    |/** First executable declaration introduced and updated Nov. 6, 2026 by ASAMI, Tomoharu. */
    |object HistoryProbe { val value: String = "retained-history-value" }
    |
    |/** Second executable declaration introduced and updated Nov. 6, 2026 by ASAMI, Tomoharu. */
    |object HistoryCompanion { val value: String = HistoryProbe.value }
    |""".stripMargin
  private val _suite = "fixture.primarygeneration.GenerationInputSpec"
  private val _checks = Vector(
    GoalPhaseNativeValidation.Check("primary-compile", 14, WorkflowSbtValidation.Kind.Compile),
    GoalPhaseNativeValidation.Check("primary-focused", 15, WorkflowSbtValidation.Kind.TestOnly))
  private val _publication = LocalPublication.Target("fixture-primary-generation-publication", "fixture.primarygeneration.GenerationProbe",
    TypedGitHostPlanFixture.workspace, None, beforeChecks = true)
  private val _missing_input = """# COMPONENT
    |
    |## GenerationProbe
    |
    |### DESCRIPTION
    |
    |Qualify the selected generated input contract.
    |
    |### PACKAGE
    |
    |fixture.primarygeneration.generationprobe
    |
    |# VALUE
    |
    |## ProbeInput
    |
    |### ATTRIBUTE
    |
    || name | type | multiplicity |
    ||------+------|--------------|
    || value | string | 1 |
    |
    |## ProbeResult
    |
    |### ATTRIBUTE
    |
    || name | type | multiplicity |
    ||------+------|--------------|
    || value | string | 1 |
    |
    |# SERVICE
    |
    |## ProbeService
    |
    |### DESCRIPTION
    |
    |Expose the generated input and result contract.
    |
    |### OPERATION
    |
    |#### echo
    |
    |##### DESCRIPTION
    |
    |Return the selected input value.
    |
    |##### TYPE
    |
    |COMMAND
    |
    |##### OUTPUT
    |
    |###### TYPE
    |
    |ProbeResult
    |""".stripMargin
  private val _input_only = _missing_input.replace("##### OUTPUT", """##### INPUT
    |
    |###### TYPE
    |
    |ProbeInput
    |
    |##### PARAMETER
    |
    || name | type | multiplicity |
    ||------+------|--------------|
    || input | ProbeInput | 1 |
    |
    |##### OUTPUT""".stripMargin)
  private val _corrected_input = _input_only.replace("## ProbeInput\n\n### ATTRIBUTE",
    "## ProbeInput\n\n- input-kind :: COMMAND\n\n### ATTRIBUTE")
  private val _workflow_model = """
    |
    |# STATE-MACHINE
    |
    |## ProbeLifecycle
    |
    |### State
    |
    |#### Ready
    |
    |##### Transition
    |
    |to = Done
    |on = echoed
    |
    |#### Done
    |
    |# WORKFLOW
    |
    |## GenerationWorkflow
    |
    |version = v1
    |
    |### COMPOSITE-STATEMACHINE
    |
    |#### CONSTITUENT
    |
    |##### probe
    |
    |state-machine = ProbeLifecycle
    |
    |#### STATE
    |
    |##### Ready
    |
    |##### Done
    |
    |#### DERIVATION
    |
    |##### ready
    |
    |state = Ready
    |when = probe.Ready
    |
    |##### done
    |
    |state = Done
    |when = probe.Done
    |
    |#### INITIAL
    |
    |probe.Ready
    |
    |### OPERATION
    |
    |#### echo
    |
    |""".stripMargin
  private val _structural_binding = """##### TYPE
    |
    |COMMAND
    |
    |##### INPUT
    |
    |###### TYPE
    |
    |ProbeInput
    |
    |##### OUTPUT
    |
    |###### TYPE
    |
    |ProbeResult
    |""".stripMargin
  private val _structural_source = _corrected_input + _workflow_model + _structural_binding
  private val _structural_corrected = _corrected_input + _workflow_model + "operation = ProbeService.echo\n"
  private val _structural_error = "[error] Error[org.goldenport.exception.SyntaxErrorFaultException: WORKFLOW 'GenerationWorkflow' " +
    "OPERATION 'echo' does not admit nested structural content.]"

  private def _array(value: Json, key: String): Vector[Json] = value.hcursor.get[Vector[Json]](key).fold(
    error => fail(error.message), identity)
  private def _json(value: RecordPresentable): Json = parse(take(value.toRecord().toJsonStringC)).fold(
    error => fail(error.message), identity)
  private def _json(value: Record): Json = parse(take(value.toJsonStringC)).fold(error => fail(error.message), identity)
  private def _read(path: Path): Json = parse(Files.readString(path)).fold(error => fail(error.message), identity)
  private def _business[A: ValueReader](path: Path): A =
    take(summon[ValueReader[A]].readC(take(new RecordDecoder().json(Files.readString(path)))))
  private def _required(property: String): Path = {
    val path = Path.of(sys.props.getOrElse(property, fail("Explicitly select " + property)))
    path.isAbsolute shouldBe true
    path.normalize shouldBe path
    path
  }
  private def _lines(path: Path): Vector[String] =
    if (Files.isRegularFile(path)) Files.readString(path).linesIterator.toVector else Vector.empty
  private def _publication_result(value: V.LocalPublicationResult): LocalPublication.Result =
    GoalPhaseNativeMetadata.normalizePublicJson(_json(value.publication.get)).flatMap(
      _.as[LocalPublication.Result].left.map(_.message)).fold(fail(_), identity)
  private def _latest(scenario: F.Scenario, session: String): LocalPublication.Result =
    _publication_result(scenario.callInstalled(_required("smworkflow.spec.installedCommand"), "LocalPublicationService",
      "collectLatestLocalPublication", Vector("--input", _json(V.LocalPublicationLatestCollectionInput(
        _publication.identity, session)).noSpaces), assemblyDescriptor = Some(scenario.root.resolve(".textus/assembly.yaml")))
      .business[V.LocalPublicationResult])
  private def _original(scenario: F.Scenario, failed: LocalPublication.Result): LocalPublication.Result =
    _publication_result(scenario.callInstalled(_required("smworkflow.spec.installedCommand"), "LocalPublicationService",
      "collectLocalPublication", Vector("--input", failed.request.input.asJson.mapObject(_.remove("answer")).noSpaces),
      assemblyDescriptor = Some(scenario.root.resolve(".textus/assembly.yaml"))).business[V.LocalPublicationResult])
  private def _submit(scenario: F.Scenario, request: V.GoalPhaseAdvanceResult, label: String): F.Result = {
    val result = Json.obj("outcome" -> "completed".asJson, "summary" ->
      ("SPECIFICATION-OWNED actual " + label + " CML source edit").asJson, "findings" -> Json.arr())
    val output = scenario.call("command", request.submitOperation.get, Vector("--sessionId", request.submitSessionId.get,
      "--result", result.noSpaces, "--resultReference", _json(V.WorkflowArtifactReference("generation-" + label, "1")).noSpaces))
    output.business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
    output
  }

  /** Only fixed fixture build inputs are committed here, before Start. The
    * faulty and corrected operation source remains assigned semantic work. */
  private def _build(scenario: F.Scenario, structural: Boolean = false): Unit = {
    val repository = Path.of(scenario.host.target.root)
    Files.createDirectories(repository.resolve("src/main/cozy"))
    Files.createDirectories(repository.resolve("project"))
    val version = "0.0.0-" + scenario.root.getFileName + "-SNAPSHOT"
    Files.writeString(repository.resolve("project/plugins.sbt"), "resolvers += Resolver.defaultLocal\n" +
      "resolvers += \"SimpleModeling.org\" at \"https://www.simplemodeling.org/repository/maven\"\n" +
      "addSbtPlugin(\"org.goldenport\" % \"sbt-cozy\" % \"0.1.18-SNAPSHOT\")\n")
    Files.writeString(repository.resolve("project.yaml"), s"""project:
      |  namespace: "fixture.primarygeneration"
      |  id: "GenerationProbe"
      |  name: "primarygeneration-generation-probe"
      |  kind: car
      |  organization: "fixture.primarygeneration"
      |  scalaPackage: "fixture.primarygeneration.generationprobe"
      |  component:
      |    name: "GenerationProbe"
      |    className: "GenerationProbeComponent"
      |    version: "$version"
      |build:
      |  scalaVersion: "3.3.8"
      |  cozyVersion: "0.3.3-SNAPSHOT"
      |  dependencies:
      |    compile:
      |      - "org.goldenport::goldenport-cncf:0.5.3-SNAPSHOT"
      |    test:
      |      - "org.scalatest::scalatest:3.2.19"
      |cml:
      |  source: "$_source"
      |publication:
      |  source_manifest:
      |    enabled: false
      |packaging:
      |  kind: car
      |  car:
      |    runtime:
      |      cncf:
      |        minimum: "0.5.3-SNAPSHOT"
      |        excluded: []
      |        tested:
      |          - "0.5.3-SNAPSHOT"
      |""".stripMargin)
    val calls = scenario.root.resolve("generation-sbt-invocations.txt").toString.asJson.noSpaces
    val attempts = scenario.root.resolve("generation-publication-attempts.txt").toString.asJson.noSpaces
    Files.writeString(repository.resolve("build.sbt"), s"""enablePlugins(org.goldenport.cozy.CozyPlugin)
      |scalaVersion := "3.3.8"
      |organization := "fixture.primarygeneration"
      |name := "primarygeneration-generation-probe"
      |version := "$version"
      |cozyManifestMetadata ++= org.goldenport.cozy.CozyProjectIdentityContract.requireAdmitted(cozyProjectMetadata.value, scalaBinaryVersion.value).manifestMetadata
      |resolvers += Resolver.defaultLocal
      |resolvers += "SimpleModeling.org" at "https://www.simplemodeling.org/repository/maven"
      |libraryDependencies ++= Seq(
      |  "org.goldenport" %% "goldenport-cncf" % "0.5.3-SNAPSHOT",
      |  "org.scalatest" %% "scalatest" % "3.2.19" % Test)
      |Test / fork := false
      |Test / parallelExecution := false
      |Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat
      |Global / onLoad := {
      |  val previous = (Global / onLoad).value
      |  state => {
      |    val commands = state.remainingCommands.map(_.commandLine).mkString("|")
      |    IO.append(file($calls), commands + "\\n")
      |    if (commands.contains("publishLocal")) IO.append(file($attempts), commands + "\\n")
      |    previous(state)
      |  }
      |}
      |""".stripMargin)
    val testsource = """package fixture.primarygeneration
      |
      |import java.nio.file.{Files, Path, StandardOpenOption}
      |import org.scalatest.GivenWhenThen
      |import org.scalatest.matchers.should.Matchers
      |import org.scalatest.wordspec.AnyWordSpec
      |import fixture.primarygeneration.generationprobe.value.{ProbeInput, ProbeResult}
      |
      |/** @since Nov. 6, 2026
      |  * @version Nov. 6, 2026
      |  * @author ASAMI, Tomoharu
      |  */
      |final class GenerationInputSpec extends AnyWordSpec with Matchers with GivenWhenThen {
      |  "the corrected generated operation input" should {
      |    "retain the selected value through the generated input and result types" in {
      |      Given("the actual classes generated from the corrected CML INPUT")
      |      val input = ProbeInput("qualified-input")
      |      When("the generated result consumes the public generated input field")
      |      val result = ProbeResult(input.value)
      |      Then("the selected value is available and the actual focused test records completion")
      |      result.value shouldBe "qualified-input"
      |      Files.writeString(Path.of("").toAbsolutePath.normalize.getParent.resolve("generation-real-test-passed.txt"),
      |        "qualified-input\n", StandardOpenOption.CREATE_NEW)
      |    }
      |  }
      |}
      |""".stripMargin
    Files.writeString(repository.resolve("src/test/scala/ProductCheck.scala"), if (structural)
      testsource.replace("StandardOpenOption.CREATE_NEW", "StandardOpenOption.CREATE, StandardOpenOption.APPEND") else testsource)
    take(_run_setup_command_(Vector("-C", repository.toString, "-c", "core.hooksPath=/dev/null", "--literal-pathspecs",
      "add", "--", "build.sbt", "project/plugins.sbt", "project.yaml", "src/test/scala/ProductCheck.scala")))
    take(_run_setup_command_(Vector("-C", repository.toString, "-c", "core.hooksPath=/dev/null", "-c",
      "user.name=Generation Recovery Specification", "-c", "user.email=generation@example.invalid", "commit",
      "--no-verify", "--no-gpg-sign", "-m", "Prepare tiny real sbt-cozy generation fixture")))
  }

  private def _configure(initial: F.Scenario, history: Boolean = false): F.Scenario = {
    val root = initial.root
    val paths = if (history) Vector("src/main") else Vector(_source)
    val host = initial.host.copy(goal = initial.host.goal.copy(candidatePaths = paths,
      stepValidation = initial.host.goal.stepValidation.copy(checks = _checks),
      phaseValidation = initial.host.goal.phaseValidation.copy(checks = _checks),
      closure = initial.host.goal.closure.copy(programs = Vector(GoalPhaseNativeClosure.StepProgram("step-1", 1)))))
    val plan = O.Plan(if (history) "native-primary-generation-history-plan" else "native-primary-generation-plan", "1", host.goal.input.phaseIdentity,
      host.goal.input.repositoryIdentity, Vector(O.Step("step-1", "Correct the generated operation input contract",
        paths, Vector.empty, _checks.map(_.identity), actionPolicy = Some(GoalPhaseStepAssessment.Policy(
          "generation-required-independent-review", "code", "required", "off", "off", Vector.empty)))),
      _checks.map(_.identity), repairLimit = 2)
    Files.writeString(root.resolve("goal-plan.json"), plan.asJson.noSpaces)
    val git = TypedGitHostPlanFixture.plan(Programs.Target(Path.of(host.target.root), host.target.localBranch,
      host.target.remote, host.target.remoteBranch, host.target.endpoint),
      kinds = TypedGitHostPlanFixture.observations ++ Vector(GitOperationKind.IndexTree, GitOperationKind.PendingPaths,
        GitOperationKind.PendingDiff, GitOperationKind.CommitMetadata), goalPaths = paths,
      goalMessages = Vector("Complete selected continuation Step 1"), goalSelections = Vector(paths))
    val checks = Vector(
      TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(14),
        executionTimeoutMillis = 1200000L, operation = SbtOperation.Compile),
      TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(15),
        executionTimeoutMillis = 1200000L, operation = SbtOperation.TestOnly, testClasses = Vector(_suite)))
    val publication = _array(TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(5),
      executionTimeoutMillis = 1200000L, operation = SbtOperation.PublishLocal), "sbtPrograms").map(_.mapObject(
      _.add("target", _publication.workspace.asJson).add("identity", _publication.identity.asJson)
        .add("capability", _publication.identity.asJson)))
    val sbt = checks.flatMap(_array(_, "sbtPrograms")) ++ publication
    val process = git.mapObject(_.add("sbtPrograms", sbt.asJson)
      .add("workspaces", (_array(git, "workspaces") ++ checks.flatMap(_array(_, "workspaces"))).asJson)
      .add("workspaceGrants", (_array(git, "workspaceGrants") ++ checks.flatMap(_array(_, "workspaceGrants"))).asJson)
      .add("grants", Json.arr(Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson,
        "capabilities" -> (_array(git, "gitPrograms") ++ sbt).map(_.hcursor.get[String]("capability").toOption.get).distinct.asJson))))
    val path = root.resolve(".textus/config.json")
    val config = _read(path)
    val storage = parse(config.hcursor.get[String]("textus.subsystem.storage.plan").toOption.get).toOption.get
    val collection = ThreeWorkflowNormalHostFixture.collection("prerequisite_publications")
    val selectedstorage = storage.mapObject(_.add("records", (_array(storage, "records") :+ Json.obj(
      "componentId" -> SmWorkflowComponent.componentId.name.asJson, "storeName" -> "workflow".asJson,
      "collection" -> collection.asJson)).asJson))
    val policy = Json.obj("storeName" -> "workflow".asJson, "collection" -> collection.asJson,
      "targets" -> Vector(_publication).asJson)
    val historyconfig = if (history) config.mapObject(_.add("sm-workflow.source-history.style", "goldenport".asJson)
      .add("sm-workflow.source-history.rule", PrimaryGenerationHistorySpecificationFixture.rule)) else config
    Files.writeString(path, historyconfig.mapObject(_.add("sm-workflow.runtime.definition", ThreeWorkflowRuntimeParameters.encode(host).noSpaces.asJson)
      .add("textus.assembly.descriptor", root.resolve(".textus/assembly.yaml").toString.asJson)
      .add("textus.subsystem.process-execution.plan", process.noSpaces.asJson)
      .add("textus.subsystem.storage.plan", selectedstorage.noSpaces.asJson)
      .add(LocalPublicationRuntime.declaration.name, policy.noSpaces.asJson)
      .add("textus.resource.read.max-bytes", "16777216".asJson)
      .add(WorkflowMechanicalReceiptParameters.maxBytes.name, take(WorkflowMechanicalReceiptLimits.createC(16 * 1024 * 1024)).maxBytes.toString.asJson)).noSpaces)
    F.reopenPrimaryGeneration(root, host, plan, 1000)
  }

  private def _capture(locator: Path, historical: Boolean = true, structural: Boolean = false): Unit = {
    Given(if (historical) "RGEN01 E15: an explicitly selected old installed CLI and a fresh tiny real sbt-cozy project with no native state"
      else "RGEN01 E15: the current normal installed CLI and a new tiny project with canonical CAR packaging metadata")
    Files.exists(locator) shouldBe false
    val initial = F.fresh("primary-generation-capture", sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable), repairContinuation = true)
    _build(initial, structural)
    val scenario = _configure(initial)
    val task = scenario.ownTask("primary-generation-recovery")
    val prepared = scenario.prepareHostInput("prepareGoalStart", scenario.selectedGoalPreparationInput.toRecord())
    scenario.selectPreparedImport(prepared)
    val started = scenario.start().business[V.GoalPhaseStartResult]
    scenario.retain(task, started)
    val issued = scenario.advance(task)
    val request = issued.business[V.GoalPhaseAdvanceResult]
    request.work.get.kind shouldBe "ImplementStep"
    val source = if (structural) _structural_source else _missing_input
    Files.writeString(Path.of(scenario.host.target.root).resolve(_source), source, StandardOpenOption.CREATE_NEW)
    val accepted = _submit(scenario, request, "initial")
    When("ordinary installed Advance captures that actual CML and executes its real primary publishLocal")
    val output = scenario.advance(task)
    val failed = output.business[V.GoalPhaseAdvanceResult]
    val publication = _latest(scenario, started.sessionId)
    val process = WorkflowSbtCapture.decode(publication.process.get).toOption.get
    val text = WorkflowSbtCapture.text(process.stdout).value + "\n" + WorkflowSbtCapture.text(process.stderr).value
    Then(if (historical) "the actual terminated cozyGenerate failure precedes every selected check and the old description fails visibly"
      else "the actual terminated cozyGenerate failure precedes every selected check and automatically reserves ordinary repair one")
    failed.outcome shouldBe Some("failed")
    failed.validation shouldBe None
    publication.status shouldBe "failed"
    WorkflowSbtCapture.exitCode(process) shouldBe Some(1)
    WorkflowSbtCapture.text(process.stdout).textStatus shouldBe "complete"
    WorkflowSbtCapture.text(process.stderr).textStatus shouldBe "complete"
    process.stdout.truncated shouldBe false
    process.stderr.truncated shouldBe false
    text should include(if (structural) _structural_error else "[error] Error[No input]")
    text should include("GENERATION_PROVENANCE_OUTPUT_TAMPERED")
    text should include("[error] (cozyGenerate) [sbt-cozy] cozy delegate failed (1) for ")
    _lines(scenario.root.resolve("generation-publication-attempts.txt")).size shouldBe 1
    _lines(scenario.root.resolve("generation-sbt-invocations.txt")).size shouldBe 1
    Files.exists(scenario.root.resolve("generation-real-test-passed.txt")) shouldBe false
    val description = if (historical) {
      val description = scenario.call("command", B.describeOperation, Vector("--sessionId", started.sessionId))
      description.exitCode should not be 0
      if (structural) {
        description.invocation.logs should include("Unsupported primary publication failure")
        description.invocation.logs should include("409")
      } else {
        description.invocation.logs should include("None.get")
        description.invocation.logs should include("500")
      }
      Some(description)
    } else {
      withClue(output.invocation.logs) {
      failed.repairProgress should not be empty
      failed.repairProgress.get.automaticCount shouldBe 1
      failed.repairProgress.get.ordinaryCount shouldBe 1
      failed.repairProgress.get.limit shouldBe 2
      }
      None
    }
    if (structural) {
      When("ordinary old-runtime recollection inspects the retained unsupported structural failure")
      scenario.advance(task).business[V.GoalPhaseAdvanceResult].work shouldBe None
      Then("neither publication nor any selected check replays while the old failure remains collectable")
      _latest(scenario, started.sessionId) shouldBe publication
      _lines(scenario.root.resolve("generation-publication-attempts.txt")).size shouldBe 1
      _lines(scenario.root.resolve("generation-sbt-invocations.txt")).size shouldBe 1
    }
    _original(scenario, publication) shouldBe publication
    val snapshot = Json.obj("root" -> scenario.root.toString.asJson,
      "host" -> ThreeWorkflowRuntimeParameters.encode(scenario.host), "plan" -> scenario.plan.asJson,
      "task" -> task.toString.asJson, "sessionId" -> started.sessionId.asJson,
      "initialOutput" -> issued.invocation.stdout.toString.asJson, "acceptedOutput" -> accepted.invocation.stdout.toString.asJson,
      "failedOutput" -> output.invocation.stdout.toString.asJson, "failedBytes" -> Files.readString(output.invocation.stdout).asJson,
      "structural" -> structural.asJson, "source" -> source.asJson,
      "initialBytes" -> Files.readString(issued.invocation.stdout).asJson,
      "acceptedBytes" -> Files.readString(accepted.invocation.stdout).asJson,
      "failedStderr" -> Files.readString(output.invocation.stderr).asJson,
      "failedStderrPath" -> output.invocation.stderr.toString.asJson,
      "descriptionOutput" -> description.map(_.invocation.stdout.toString).asJson,
      "descriptionStderr" -> description.map(value => Files.readString(value.invocation.stderr)).asJson,
      "repairProgress" -> failed.repairProgress.asJson,
      "failedPublication" -> publication.asJson, "config" -> Files.readString(scenario.root.resolve(".textus/config.json")).asJson,
      "head" -> scenario.candidateHead.asJson, "indexDiff" -> scenario.candidateIndexDiff.asJson,
      "calls" -> _lines(scenario.root.resolve("generation-sbt-invocations.txt")).asJson)
    Files.writeString(locator, snapshot.noSpaces, StandardOpenOption.CREATE_NEW)
  }

  private def _continue(locator: Path, alreadyreserved: Boolean = false, structural: Boolean = false): Unit = {
    Given(if (alreadyreserved) "RGEN01 E15: the exact new current-runtime locator with ordinary repair one already reserved"
      else "RGEN01 E15: the exact caller-owned locator from capture-old and the newly published installed CLI")
    val saved = _read(locator)
    val locatorbytes = Files.readAllBytes(locator).toVector
    if (structural) saved.hcursor.get[Boolean]("structural") shouldBe Right(true)
    val root = Path.of(saved.hcursor.get[String]("root").toOption.get)
    val host = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(saved.hcursor.get[Json]("host").toOption.get))
    val plan = saved.hcursor.get[O.Plan]("plan").toOption.get
    val logindex = sys.props.get("smworkflow.spec.generationLogIndex").fold(2000)(_.toInt)
    Set(2000, 3000)(logindex) shouldBe true
    if (alreadyreserved) logindex shouldBe 2000
    val scenario = F.reopenPrimaryGeneration(root, host, plan, logindex)
    val task = Path.of(saved.hcursor.get[String]("task").toOption.get)
    val session = scenario.retrieve(task)
    session shouldBe saved.hcursor.get[String]("sessionId").toOption.get
    val initialpath = Path.of(saved.hcursor.get[String]("initialOutput").toOption.get)
    val initial = _business[V.GoalPhaseAdvanceResult](initialpath)
    withClue(Files.readString(initialpath)) { initial.work should not be empty }
    val acceptedpath = Path.of(saved.hcursor.get[String]("acceptedOutput").toOption.get)
    val acceptedbytes = Files.readAllBytes(acceptedpath).toVector
    _business[V.GoalPhaseWorkSubmissionResult](acceptedpath).accepted shouldBe true
    val failedpath = Path.of(saved.hcursor.get[String]("failedOutput").toOption.get)
    val failedbytes = saved.hcursor.get[String]("failedBytes").toOption.get
    val failed = saved.hcursor.get[LocalPublication.Result]("failedPublication").toOption.get
    val oldcalls = saved.hcursor.get[Vector[String]]("calls").toOption.get
    if (structural) {
      Files.readString(initialpath) shouldBe saved.hcursor.get[String]("initialBytes").toOption.get
      Files.readString(acceptedpath) shouldBe saved.hcursor.get[String]("acceptedBytes").toOption.get
      Files.readString(Path.of(host.target.root).resolve(_source)) shouldBe saved.hcursor.get[String]("source").toOption.get
      Files.readString(root.resolve("goal-plan.json")) shouldBe plan.asJson.noSpaces
      _business[V.GoalPhaseAdvanceResult](failedpath).repairProgress.asJson shouldBe saved.hcursor.downField("repairProgress").focus.get
    }
    Files.readString(root.resolve(".textus/config.json")) shouldBe saved.hcursor.get[String]("config").toOption.get
    Files.readString(failedpath) shouldBe failedbytes
    scenario.candidateHead shouldBe saved.hcursor.get[String]("head").toOption.get
    scenario.candidateIndexDiff shouldBe saved.hcursor.get[String]("indexDiff").toOption.get
    _latest(scenario, session) shouldBe failed
    if (!alreadyreserved) {
      When("public description inspects the saved failed frontier without accepting the implementation again")
      val described = scenario.call("command", B.describeOperation, Vector("--sessionId", session))
        .business[V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult]
      val target = take(A.targetC(described.target))
      Then("v5 identifies the genuine generation failure and the original Plan base prefix and counters")
      target.schemaVersion shouldBe A.generationTargetSchema
      target.failure.failed shouldBe failed
      target.failure.plan shouldBe plan
      GoalPhaseNativeMetadata.normalizePublicJson(_json(F.publicPlan(target.failure.plan))).fold(fail(_), identity) shouldBe
        GoalPhaseNativeMetadata.normalizePublicJson(_json(initial.work.get.selectedPlan)).fold(fail(_), identity)
      target.failure.base.head shouldBe initial.work.get.originalBase.head
      target.failure.base.tree shouldBe initial.work.get.originalBase.tree
      target.failure.completed shouldBe empty
      target.failure.generation shouldBe 1L
      target.failure.repairProgress.ordinaryCount shouldBe 0
      described.message should include("generation failure")
      described.message should not include("Compile failure")
    }
    When(if (alreadyreserved) "normal Advance issues the same-Step RepairStep from the existing reservation without another reservation"
      else "normal Advance consumes only the saved exact failure and issues the ordinary same-Step repair")
    val reservedoutput = scenario.advance(task)
    val reserved = reservedoutput.business[V.GoalPhaseAdvanceResult]
    if (alreadyreserved) withClue(reservedoutput.invocation.logs) { reserved.work should not be empty }
    val repairoutput = if (alreadyreserved || reserved.work.nonEmpty) reservedoutput else {
      reserved.outcome shouldBe Some("failed")
      scenario.advance(task)
    }
    val repair = repairoutput.business[V.GoalPhaseAdvanceResult]
    Then("one real repair reservation retains actual failure context and truthful unexecuted tests")
    withClue(repairoutput.invocation.logs) {
      repair.work should not be empty
      repair.work.foreach(_.publicationFailure should not be empty)
      repair.repairProgress should not be empty
    }
    repair.work.get.kind shouldBe "RepairStep"
    repair.work.get.stepIdentity shouldBe Some("step-1")
    repair.work.get.completed shouldBe initial.work.get.completed
    repair.work.get.originalBase shouldBe initial.work.get.originalBase
    repair.work.get.selectedPlan shouldBe initial.work.get.selectedPlan
    repair.work.get.checkFailure shouldBe None
    repair.validation shouldBe None
    val context = GoalPhaseNativeMetadata.normalizePublicJson(_json(repair.work.get.publicationFailure.get)).flatMap(
      _.as[GoalPhaseNativeWork.PublicationFailureContext].left.map(_.message)).fold(fail(_), identity)
    context.publication shouldBe failed
    context.testsExecuted shouldBe false
    context.changedPaths shouldBe Vector(_source)
    context.diff should include("#### echo")
    val progress = repair.repairProgress.get
    progress.ordinaryCount shouldBe 1
    progress.automaticCount shouldBe 1
    progress.limit shouldBe 2
    When("the next installed process recollects the pending repair before its assigned completion")
    val repeated = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
    Then("no allowance publication or check is repeated")
    repeated shouldBe repair
    _lines(root.resolve("generation-sbt-invocations.txt")) shouldBe oldcalls
    _original(scenario, failed) shouldBe failed
    When(if (structural) "only the newly assigned RepairStep replaces the nested Workflow binding with direct operation = ProbeService.echo"
      else "the assigned correction adds only the missing CML INPUT in the same physical repository")
    val source = Path.of(host.target.root).resolve(_source)
    Files.readString(source) shouldBe (if (structural) _structural_source else _missing_input)
    Files.writeString(source, if (structural) _structural_corrected else _corrected_input)
    _submit(scenario, repair, "corrected")
    val output = scenario.advance(task)
    val reviewed = output.business[V.GoalPhaseAdvanceResult]
    Then("normal publication Compile and a real focused test reach the independent review frontier")
    reviewed.sessionId shouldBe session
    withClue(output.invocation.logs) {
      reviewed.work should not be empty
      reviewed.work.foreach(_.review should not be empty)
    }
    reviewed.work.get.kind shouldBe "ReviewStep"
    reviewed.work.get.completed shouldBe initial.work.get.completed
    reviewed.work.get.selectedPlan shouldBe initial.work.get.selectedPlan
    reviewed.work.get.originalBase shouldBe initial.work.get.originalBase
    reviewed.repairProgress shouldBe repair.repairProgress
    reviewed.work.get.review.get.changedPaths shouldBe Vector(_source)
    Files.readString(root.resolve("generation-real-test-passed.txt")) shouldBe "qualified-input\n"
    val published = _latest(scenario, session)
    published.status shouldBe "published"
    WorkflowSbtCapture.exitCode(WorkflowSbtCapture.decode(published.process.get).toOption.get) shouldBe Some(0)
    published.request.input.requestIdentity should not be failed.request.input.requestIdentity
    published.request.source.head shouldBe failed.request.source.head
    published.request.source.tree should not be failed.request.source.tree
    _lines(root.resolve("generation-publication-attempts.txt")).size shouldBe 2
    val calls = _lines(root.resolve("generation-sbt-invocations.txt"))
    calls.size shouldBe oldcalls.size + 3
    calls.count(_.contains("publishLocal")) shouldBe 2
    calls.count(_.contains("testOnly")) shouldBe 1
    scenario.candidateHead shouldBe saved.hcursor.get[String]("head").toOption.get
    When("another installed Advance recollects the independent review request and both publication records")
    val recollected = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
    reviewed.validation should not be empty
    recollected.validation should not be empty
    recollected.validation.get.assessmentJournal shouldBe None
    recollected shouldBe reviewed.copy(validation = reviewed.validation.map(_.copy(assessmentJournal = None)))
    val original = _original(scenario, failed)
    val latest = _latest(scenario, session)
    Then("old failure bytes accepted work repair accounting and successful effects remain exact without replay")
    original shouldBe failed
    latest shouldBe published
    Files.readString(failedpath) shouldBe failedbytes
    Files.readAllBytes(acceptedpath).toVector shouldBe acceptedbytes
    _lines(root.resolve("generation-sbt-invocations.txt")) shouldBe calls
    _lines(root.resolve("generation-publication-attempts.txt")).size shouldBe 2
    if (structural) {
      _close_structural_step(scenario, task, output, saved, failedpath, acceptedpath, locator, locatorbytes, calls)
    } else Files.writeString(root.resolve("generation-continuation-result.json"), Json.obj("sessionId" -> session.asJson,
      "frontier" -> "independent-review-required".asJson, "reviewOutput" -> output.invocation.stdout.toString.asJson,
      "originalFailure" -> original.asJson, "published" -> latest.asJson, "calls" -> calls.asJson).noSpaces,
      StandardOpenOption.CREATE_NEW)
  }

  /** Controlled review and authority inputs exercise native closure; neither
    * constitutes actual independent provider, Human or consumer evidence. */
  private def _close_structural_step(scenario: F.Scenario, task: Path, output: F.Result, saved: Json,
    failedpath: Path, acceptedpath: Path, locator: Path, locatorbytes: Vector[Byte], stepcalls: Vector[String]): Unit = {
    val root = scenario.root
    val reviewed = output.business[V.GoalPhaseAdvanceResult]
    val work = reviewed.work.get
    val failed = saved.hcursor.get[LocalPublication.Result]("failedPublication").toOption.get
    val parent = saved.hcursor.get[String]("head").toOption.get
    def _save_(name: String, value: Json): String = {
      val path = root.resolve("structural-" + name + ".json")
      Files.writeString(path, value.noSpaces, StandardOpenOption.CREATE_NEW)
      path.toUri.toASCIIString
    }
    Given("the actual ReviewStep with one successful Step Compile and focused test, plus SPECIFICATION-OWNED controlled review inputs")
    work.kind shouldBe "ReviewStep"
    work.stepIdentity shouldBe Some("step-1")
    reviewed.validation.get.scopeIdentity shouldBe "step-1"
    val stepchecks = work.review.get.checks
    stepchecks.map(_.identity) shouldBe _checks.map(_.identity)
    stepchecks.map(_.sbt.get.kind) shouldBe Vector("compile", "test-only")
    stepchecks.foreach { receipt =>
      receipt.termination shouldBe "exited"
      receipt.exitCode shouldBe Some(0)
      receipt.sbt.get.outcome shouldBe "succeeded"
    }
    stepchecks.last.sbt.get.selection.get.completedSuites shouldBe 1
    stepchecks.last.sbt.get.selection.get.testsRun shouldBe 1
    _lines(root.resolve("generation-real-test-passed.txt")) shouldBe Vector("qualified-input")
    val steppublication = _latest(scenario, reviewed.sessionId)
    steppublication.status shouldBe "published"
    steppublication.request.source.head shouldBe work.candidate.get.head
    steppublication.request.source.tree shouldBe work.candidate.get.tree
    val result = Json.obj("outcome" -> "accept".asJson,
      "summary" -> "SPECIFICATION-OWNED controlled structural fixture review; no independent provider or consumer claim".asJson,
      "findings" -> Json.arr())
    val reference = V.WorkflowArtifactReference("specification-structural-step-review", "1")
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("specification-controlled-reviewer"), None, None)
    val input = V.WorkflowEvidencePreparationInput("GoalPhase", "review", output.invocation.stdout.toUri.toASCIIString,
      _save_("review-result", result), _save_("review-reference", _json(reference)), _save_("review-execution", _json(execution)),
      "normal-development-host", Instant.now().minusSeconds(60).toString, "entity:smworkflow.cli.host_evidence", None)
    When("normal passive prepareEvidence and its actual selected import precede one native Review Submit")
    val prepared = scenario.prepareHostInput("prepareEvidence", input.toRecord())
    withClue(prepared.invocation.logs) { prepared.exitCode shouldBe 0 }
    scenario.selectPreparedImport(prepared)
    val accepted = scenario.call("command", reviewed.submitOperation.get, Vector("--sessionId", reviewed.submitSessionId.get,
      "--result", result.noSpaces, "--resultReference", _json(reference).noSpaces, "--executionEvidence", _json(execution).noSpaces))
    Then("the controlled fixture review is accepted without replaying implementation publication or Step checks")
    accepted.business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
    _lines(root.resolve("generation-sbt-invocations.txt")) shouldBe stepcalls

    When("ordinary Advance reaches the real missing-authority StepClosure frontier")
    val closing = scenario.advance(task)
    val waiting = closing.business[V.GoalPhaseAdvanceResult]
    Then("closure waits for its own SPECIFICATION-OWNED authority while retaining the checked candidate")
    waiting.kind shouldBe "Wait"
    waiting.authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
    scenario.candidateHead shouldBe parent
    _lines(root.resolve("generation-sbt-invocations.txt")) shouldBe stepcalls

    Given("that actual closure Wait and a SPECIFICATION-OWNED controlled local closure reference, with no manufactured Human answer")
    val authorityreference = V.WorkflowArtifactReference("specification-structural-step-closure", "1")
    val authority = V.WorkflowAuthorityPreparationInput(closing.invocation.stdout.toUri.toASCIIString,
      _save_("closure-reference", _json(authorityreference)), "participant", "normal-development-host",
      Instant.now().minusSeconds(60).toString, "entity:smworkflow.cli.host_evidence")
    When("normal prepareAuthority import and ordinary Advance perform the actual typed Step commit and reach the next Phase frontier")
    val admitted = scenario.prepareHostInput("prepareAuthority", authority.toRecord())
    withClue(admitted.invocation.logs) { admitted.exitCode shouldBe 0 }
    scenario.selectPreparedImport(admitted)
    val closedoutput = scenario.advance(task)
    val phase = closedoutput.business[V.GoalPhaseAdvanceResult]
    Then("one real Step commit and a separately declared Phase check lead to pending ReviewPhase without claiming Phase completion")
    withClue(closedoutput.invocation.logs) { phase.work should not be empty }
    phase.sessionId shouldBe reviewed.sessionId
    phase.completion shouldBe None
    phase.work.get.kind shouldBe "ReviewPhase"
    phase.work.get.stepIdentity shouldBe None
    phase.validation.get.scopeIdentity shouldBe "phase:" + scenario.plan.phase
    phase.validation.get.sourceRevision should not be reviewed.validation.get.sourceRevision
    phase.work.get.originalBase shouldBe work.originalBase
    phase.work.get.selectedPlan shouldBe work.selectedPlan
    phase.work.get.completed.map(_.step) shouldBe Vector("step-1")
    val closed = phase.work.get.completed.head
    closed.review shouldBe Some(reference.identity)
    closed.commit.parent shouldBe parent
    closed.commit.identity shouldBe scenario.candidateHead
    scenario.candidateParent shouldBe parent
    closed.commit.identity should not be parent
    closed.commit.tree shouldBe work.candidate.get.tree
    phase.work.get.candidate.get.head shouldBe closed.commit.identity
    phase.work.get.candidate.get.tree shouldBe closed.commit.tree
    phase.issuedWorkContext should not be reviewed.issuedWorkContext
    val phasechecks = phase.work.get.review.get.checks
    phasechecks.map(_.identity) shouldBe _checks.map(_.identity)
    phasechecks.map(_.sbt.get.kind) shouldBe Vector("compile", "test-only")
    phasechecks.foreach { receipt =>
      receipt.termination shouldBe "exited"
      receipt.exitCode shouldBe Some(0)
      receipt.sbt.get.outcome shouldBe "succeeded"
    }
    phasechecks.last.sbt.get.selection.get.completedSuites shouldBe 1
    phasechecks.last.sbt.get.selection.get.testsRun shouldBe 1
    _lines(root.resolve("generation-real-test-passed.txt")) shouldBe Vector.fill(2)("qualified-input")
    val calls = _lines(root.resolve("generation-sbt-invocations.txt"))
    calls.take(stepcalls.size) shouldBe stepcalls
    calls.size shouldBe stepcalls.size + 3
    calls.count(_.contains("publishLocal")) shouldBe 3
    calls.count(_.contains("testOnly")) shouldBe 2
    _lines(root.resolve("generation-publication-attempts.txt")).size shouldBe 3
    reviewed.repairProgress.get.ordinaryCount shouldBe 1
    reviewed.repairProgress.get.automaticCount shouldBe 1
    _original(scenario, failed) shouldBe failed
    Files.readString(failedpath) shouldBe saved.hcursor.get[String]("failedBytes").toOption.get
    Files.readString(Path.of(saved.hcursor.get[String]("failedStderrPath").toOption.get)) shouldBe
      saved.hcursor.get[String]("failedStderr").toOption.get
    Files.readString(acceptedpath) shouldBe saved.hcursor.get[String]("acceptedBytes").toOption.get
    Files.readAllBytes(locator).toVector shouldBe locatorbytes
    Files.readString(Path.of(scenario.host.target.root).resolve(_source)) shouldBe _structural_corrected
    val published = _latest(scenario, reviewed.sessionId)
    published.status shouldBe "published"
    published.request.source.head shouldBe closed.commit.identity
    published.request.source.tree shouldBe closed.commit.tree
    published.request.input.requestIdentity should not be failed.request.input.requestIdentity
    published.request.input.requestIdentity should not be steppublication.request.input.requestIdentity
    WorkflowSbtCapture.exitCode(WorkflowSbtCapture.decode(published.process.get).fold(fail(_), identity)) shouldBe Some(0)
    _original(scenario, steppublication) shouldBe steppublication

    When("ordinary Advance recollects the pending Phase frontier and all three distinct source-publication results")
    val repeated = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
    Then("Step closure and distinct Step and Phase checks remain retained without another successful effect")
    repeated.work shouldBe phase.work
    repeated.issuedWorkContext shouldBe phase.issuedWorkContext
    repeated.repairProgress shouldBe phase.repairProgress
    repeated.completion shouldBe None
    scenario.candidateHead shouldBe closed.commit.identity
    scenario.candidateParent shouldBe parent
    _latest(scenario, reviewed.sessionId) shouldBe published
    _original(scenario, failed) shouldBe failed
    _original(scenario, steppublication) shouldBe steppublication
    _lines(root.resolve("generation-sbt-invocations.txt")) shouldBe calls
    _lines(root.resolve("generation-publication-attempts.txt")).size shouldBe 3
    _lines(root.resolve("generation-real-test-passed.txt")) shouldBe Vector.fill(2)("qualified-input")
    Files.readString(failedpath) shouldBe saved.hcursor.get[String]("failedBytes").toOption.get
    Files.readString(acceptedpath) shouldBe saved.hcursor.get[String]("acceptedBytes").toOption.get
    Files.readAllBytes(locator).toVector shouldBe locatorbytes
    Files.writeString(root.resolve("structural-generation-qualification-result.json"), Json.obj(
      "sessionId" -> reviewed.sessionId.asJson, "frontier" -> "ReviewPhase".asJson,
      "phaseComplete" -> false.asJson, "controlledInputs" -> "SPECIFICATION-OWNED review and closure authority; no actual independent provider, Human or consumer evidence".asJson,
      "reviewOutput" -> output.invocation.stdout.toString.asJson, "closureOutput" -> closedoutput.invocation.stdout.toString.asJson,
      "originalFailure" -> failed.asJson, "published" -> published.asJson, "stepCommit" -> _json(closed.commit),
      "stepPublication" -> steppublication.asJson, "phasePublication" -> published.asJson,
      "ordinaryRepairCountAtStep" -> reviewed.repairProgress.get.ordinaryCount.asJson,
      "stepChecks" -> stepchecks.map(_json).asJson, "phaseChecks" -> phasechecks.map(_json).asJson,
      "calls" -> calls.asJson).noSpaces, StandardOpenOption.CREATE_NEW)
  }

  /** Passive qualification of an already reached Phase frontier. Every selected
    * completion is an actual public artifact; no successful work is resubmitted. */
  private def _collect_structural_closure(locator: Path): Unit = {
    Given("the exact unchanged structural locator and caller-selected public outputs from the already committed tiny Step")
    locator.toRealPath() shouldBe locator
    val closurelocator = _required("smworkflow.spec.generationClosureLocator")
    closurelocator shouldBe locator.getParent.resolve("structural-closure-locator.json")
    closurelocator.toRealPath() shouldBe closurelocator
    val locatorbytes = Files.readAllBytes(locator).toVector
    val closurebytes = Files.readAllBytes(closurelocator).toVector
    val saved = _read(locator)
    val closure = _read(closurelocator)
    closure.hcursor.get[String]("originalLocator") shouldBe Right(locator.toString)
    saved.hcursor.get[Boolean]("structural") shouldBe Right(true)
    val root = Path.of(saved.hcursor.get[String]("root").toOption.get)
    val host = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(saved.hcursor.get[Json]("host").toOption.get))
    val plan = saved.hcursor.get[O.Plan]("plan").toOption.get
    val session = saved.hcursor.get[String]("sessionId").toOption.get
    closure.hcursor.get[String]("sessionId") shouldBe Right(session)
    val task = Path.of(saved.hcursor.get[String]("task").toOption.get)
    task shouldBe root.resolve("target/sm-workflow/tasks/primary-generation-recovery")
    task.toRealPath() shouldBe task
    def _selected_output_(path: Path): Path = {
      path.isAbsolute shouldBe true
      path.normalize shouldBe path
      path.getParent shouldBe root.resolve("logs")
      path.getFileName.toString should fullyMatch regex "[0-9]+\\.stdout\\.json"
      Files.isRegularFile(path) shouldBe true
      path.toRealPath() shouldBe path
      path
    }
    def _selected_(key: String, filename: String): Path = {
      val path = _selected_output_(Path.of(closure.hcursor.get[String](key).toOption.get))
      path.getFileName.toString shouldBe filename
      path
    }
    val initialpath = _selected_output_(Path.of(saved.hcursor.get[String]("initialOutput").toOption.get))
    val acceptedpath = _selected_output_(Path.of(saved.hcursor.get[String]("acceptedOutput").toOption.get))
    val failedpath = _selected_output_(Path.of(saved.hcursor.get[String]("failedOutput").toOption.get))
    val repairpath = _selected_("repairOutput", "3005.stdout.json")
    val repairsubmitpath = _selected_("repairSubmitOutput", "3008.stdout.json")
    val reviewpath = _selected_("reviewOutput", "3009.stdout.json")
    val steppublicationpath = _selected_("stepPublicationOutput", "3010.stdout.json")
    val reviewsubmitpath = _selected_("reviewSubmitOutput", "3016.stdout.json")
    val waitingpath = _selected_("closureWaitOutput", "3017.stdout.json")
    val phasepath = _selected_("phaseOutput", "3020.stdout.json")
    val selectedpaths = Vector(initialpath, acceptedpath, failedpath, repairpath, repairsubmitpath, reviewpath,
      steppublicationpath, reviewsubmitpath, waitingpath, phasepath)
    selectedpaths.distinct.size shouldBe selectedpaths.size
    val preserved = selectedpaths.map(path => path -> Files.readAllBytes(path).toVector)
    val failedstderr = Path.of(saved.hcursor.get[String]("failedStderrPath").toOption.get)
    failedstderr shouldBe failedpath.resolveSibling(failedpath.getFileName.toString.stripSuffix(".stdout.json") + ".stderr")
    failedstderr.toRealPath() shouldBe failedstderr
    val resultpath = root.resolve("structural-generation-qualification-result.json")
    Files.exists(resultpath) shouldBe false

    When("the selected typed repair acceptance Step review publication closure Wait and Phase outputs are decoded without executing them")
    val initial = _business[V.GoalPhaseAdvanceResult](initialpath)
    val accepted = _business[V.GoalPhaseWorkSubmissionResult](acceptedpath)
    val failedoutput = _business[V.GoalPhaseAdvanceResult](failedpath)
    val repair = _business[V.GoalPhaseAdvanceResult](repairpath)
    val repairsubmit = _business[V.GoalPhaseWorkSubmissionResult](repairsubmitpath)
    val reviewed = _business[V.GoalPhaseAdvanceResult](reviewpath)
    val steppublication = _publication_result(_business[V.LocalPublicationResult](steppublicationpath))
    val reviewsubmit = _business[V.GoalPhaseWorkSubmissionResult](reviewsubmitpath)
    val waiting = _business[V.GoalPhaseAdvanceResult](waitingpath)
    val phase = _business[V.GoalPhaseAdvanceResult](phasepath)
    val failed = saved.hcursor.get[LocalPublication.Result]("failedPublication").toOption.get
    Then("the original accepted implementation and exact failure led to one new same-Step repair and the saved controlled closure")
    Vector(initial, failedoutput, repair, reviewed, waiting, phase).foreach(_.sessionId shouldBe session)
    Vector(accepted, repairsubmit, reviewsubmit).foreach { submission =>
      submission.sessionId shouldBe session
      submission.accepted shouldBe true
      submission.outcome shouldBe "accepted"
    }
    Files.readString(initialpath) shouldBe saved.hcursor.get[String]("initialBytes").toOption.get
    Files.readString(acceptedpath) shouldBe saved.hcursor.get[String]("acceptedBytes").toOption.get
    Files.readString(failedpath) shouldBe saved.hcursor.get[String]("failedBytes").toOption.get
    Files.readString(failedstderr) shouldBe saved.hcursor.get[String]("failedStderr").toOption.get
    failedoutput.outcome shouldBe Some("failed")
    failedoutput.validation shouldBe None
    failedoutput.repairProgress.asJson shouldBe saved.hcursor.downField("repairProgress").focus.get
    initial.work.get.kind shouldBe "ImplementStep"
    repair.kind shouldBe "Request"
    repair.work.get.kind shouldBe "RepairStep"
    repair.work.get.stepIdentity shouldBe Some("step-1")
    repair.submitSessionId shouldBe Some(session)
    repair.validation shouldBe None
    repair.repairProgress.get.ordinaryCount shouldBe 1
    repair.repairProgress.get.automaticCount shouldBe 1
    repair.repairProgress.get.limit shouldBe 2
    val failurecontext = GoalPhaseNativeMetadata.normalizePublicJson(_json(repair.work.get.publicationFailure.get)).flatMap(
      _.as[GoalPhaseNativeWork.PublicationFailureContext].left.map(_.message)).fold(fail(_), identity)
    failurecontext.publication shouldBe failed
    failurecontext.testsExecuted shouldBe false
    failurecontext.changedPaths shouldBe Vector(_source)
    failurecontext.diff shouldBe saved.hcursor.get[String]("indexDiff").toOption.get
    val failedcapture = WorkflowSbtCapture.decode(failed.process.get).fold(fail(_), identity)
    WorkflowSbtCapture.exitCode(failedcapture) shouldBe Some(1)
    (WorkflowSbtCapture.text(failedcapture.stdout).value + "\n" + WorkflowSbtCapture.text(failedcapture.stderr).value) should
      include(_structural_error)
    failed.request.target shouldBe _publication
    failed.request.input.ownerSessionId shouldBe Some(session)
    failed.request.source.head shouldBe saved.hcursor.get[String]("head").toOption.get
    Vector(repair.work.get, reviewed.work.get, phase.work.get).foreach { work =>
      work.originalBase shouldBe initial.work.get.originalBase
      work.selectedPlan shouldBe initial.work.get.selectedPlan
    }
    GoalPhaseNativeMetadata.normalizePublicJson(_json(F.publicPlan(plan))).fold(fail(_), identity) shouldBe
      GoalPhaseNativeMetadata.normalizePublicJson(_json(initial.work.get.selectedPlan)).fold(fail(_), identity)
    repair.work.get.completed shouldBe initial.work.get.completed
    reviewed.work.get.completed shouldBe initial.work.get.completed
    reviewed.work.get.kind shouldBe "ReviewStep"
    reviewed.work.get.stepIdentity shouldBe Some("step-1")
    reviewed.repairProgress shouldBe repair.repairProgress
    waiting.kind shouldBe "Wait"
    waiting.authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
    waiting.validation.get.checks shouldBe reviewed.work.get.review.get.checks
    phase.kind shouldBe "Request"
    phase.work.get.kind shouldBe "ReviewPhase"
    phase.work.get.stepIdentity shouldBe None
    phase.completion shouldBe None
    phase.repairProgress.get.ordinaryCount shouldBe 0
    phase.repairProgress.get.automaticCount shouldBe 0
    phase.repairProgress.get.limit shouldBe 2
    phase.work.get.completed.map(_.step) shouldBe Vector("step-1")
    val closed = phase.work.get.completed.head
    closed.review shouldBe Some("specification-structural-step-review")
    closed.commit.parent shouldBe saved.hcursor.get[String]("head").toOption.get
    closed.commit.identity should not be closed.commit.parent
    closed.commit.tree shouldBe reviewed.work.get.candidate.get.tree
    reviewed.work.get.candidate.get.head shouldBe closed.commit.parent
    phase.work.get.candidate.get.head shouldBe closed.commit.identity
    phase.work.get.candidate.get.tree shouldBe closed.commit.tree
    phase.issuedWorkContext should not be reviewed.issuedWorkContext
    phase.validation.get.sourceRevision should not be reviewed.validation.get.sourceRevision
    def _assert_checks_(value: V.GoalPhaseAdvanceResult, scope: String): Vector[V.GoalPhaseNativeCheckReceipt] = {
      val validation = value.validation.get
      val checks = value.work.get.review.get.checks
      validation.scopeIdentity shouldBe scope
      validation.outcome shouldBe Some("passed")
      validation.candidate shouldBe value.work.get.candidate.get
      validation.selectedChecks.map(_.identity) shouldBe _checks.map(_.identity)
      validation.checks shouldBe checks
      checks.map(_.identity) shouldBe _checks.map(_.identity)
      checks.map(_.sbt.get.kind) shouldBe Vector("compile", "test-only")
      checks.foreach { receipt =>
        receipt.termination shouldBe "exited"
        receipt.exitCode shouldBe Some(0)
        receipt.sbt.get.outcome shouldBe "succeeded"
        receipt.processObservation should not be empty
        val projection = GoalPhaseNativeMetadata.normalizePublicJson(_json(receipt.processObservation.get)).fold(fail(_), identity)
        val raw = projection.mapObject(fields => Vector("stdout", "stderr").foldLeft(fields) { (prior, key) =>
          prior.add(key, fields(key).get.mapObject(_.remove("textStatus").remove("trailingUndecodedBytes")))
        })
        val observed = WorkflowSbtCapture.decode(raw).fold(fail(_), identity)
        WorkflowSbtCapture.projection(observed) shouldBe projection
        WorkflowSbtCapture.exitCode(observed) shouldBe Some(0)
        WorkflowSbtCapture.text(observed.stdout).textStatus shouldBe "complete"
        WorkflowSbtCapture.text(observed.stderr).textStatus shouldBe "complete"
        observed.stdout.truncated shouldBe false
        observed.stderr.truncated shouldBe false
      }
      checks.last.sbt.get.selection.get.testClasses shouldBe Vector(_suite)
      checks.last.sbt.get.selection.get.completedSuites shouldBe 1
      checks.last.sbt.get.selection.get.testsRun shouldBe 1
      checks
    }
    val stepchecks = _assert_checks_(reviewed, "step-1")
    val phasechecks = _assert_checks_(phase, "phase:" + plan.phase)
    steppublication.status shouldBe "published"
    steppublication.request.target shouldBe _publication
    steppublication.request.input.ownerSessionId shouldBe Some(session)
    steppublication.request.source.head shouldBe closed.commit.parent
    steppublication.request.source.tree shouldBe closed.commit.tree
    steppublication.request.input.requestIdentity should not be failed.request.input.requestIdentity
    WorkflowSbtCapture.exitCode(WorkflowSbtCapture.decode(steppublication.process.get).fold(fail(_), identity)) shouldBe Some(0)

    Given("the same physical fixture with seven real invocation entries and the corrected source already committed")
    val logindex = sys.props.get("smworkflow.spec.generationLogIndex").map(_.toInt)
      .getOrElse(fail("Select an unused smworkflow.spec.generationLogIndex of 4000 or 5000 for passive closure collection"))
    Set(4000, 5000)(logindex) shouldBe true
    val scenario = F.reopenPrimaryGeneration(root, host, plan, logindex)
    scenario.retrieve(task) shouldBe session
    scenario.invocations shouldBe empty
    val source = Path.of(host.target.root).resolve(_source)
    val config = root.resolve(".textus/config.json")
    val planpath = root.resolve("goal-plan.json")
    val currentconfig = _read(config)
    currentconfig.hcursor.get[String]("textus.import.data.file") shouldBe Right(root.resolve("logs/3019.stdout.json").toString)
    currentconfig.mapObject(_.remove("textus.import.data.file")) shouldBe parse(saved.hcursor.get[String]("config").toOption.get)
      .fold(error => fail(error.message), identity).mapObject(_.remove("textus.import.data.file"))
    Files.readString(planpath) shouldBe plan.asJson.noSpaces
    saved.hcursor.get[String]("source") shouldBe Right(_structural_source)
    Files.readString(source) shouldBe _structural_corrected
    val head = scenario.candidateHead
    head shouldBe closed.commit.identity
    scenario.candidateParent shouldBe closed.commit.parent
    val index = scenario.candidateIndexDiff
    index shouldBe ""
    val indexpath = Path.of(host.target.root).resolve(".git/index")
    val calls = _lines(root.resolve("generation-sbt-invocations.txt"))
    val commands = calls.map(_.split("\\|").last)
    commands shouldBe Vector("publishLocal", "publishLocal", "compile", "testOnly " + _suite,
      "publishLocal", "compile", "testOnly " + _suite)
    val oldcalls = saved.hcursor.get[Vector[String]]("calls").toOption.get
    oldcalls.size shouldBe 1
    calls.take(oldcalls.size) shouldBe oldcalls
    val attempts = _lines(root.resolve("generation-publication-attempts.txt"))
    attempts shouldBe calls.filter(_.split("\\|").last == "publishLocal")
    attempts.size shouldBe 3
    val marker = root.resolve("generation-real-test-passed.txt")
    _lines(marker) shouldBe Vector.fill(2)("qualified-input")
    val stablepaths = Vector(source, indexpath, config, planpath, marker,
      root.resolve("generation-sbt-invocations.txt"), root.resolve("generation-publication-attempts.txt"), failedstderr,
      task.resolve("workflow-session.json"))
    val stablebytes = stablepaths.map(path => path -> Files.readAllBytes(path).toVector)

    When("ordinary public Advance recollects only pending ReviewPhase and public publication collectors read its three retained attempts")
    val repeatedoutput = scenario.advance(task)
    val repeated = repeatedoutput.business[V.GoalPhaseAdvanceResult]
    val published = _latest(scenario, session)
    val retainedfailure = _original(scenario, failed)
    val retainedstep = _original(scenario, steppublication)
    Then("the Phase request and distinct post-commit publication remain exact with no successful effect replay")
    repeated.work shouldBe phase.work
    repeated.issuedWorkContext shouldBe phase.issuedWorkContext
    repeated.repairProgress shouldBe phase.repairProgress
    repeated.completion shouldBe None
    repeated.validation.get.checks shouldBe phasechecks
    repeated.validation.get.sourceRevision shouldBe phase.validation.get.sourceRevision
    Set(phase.validation.get.assessmentJournal, None)(repeated.validation.get.assessmentJournal) shouldBe true
    retainedfailure shouldBe failed
    retainedstep shouldBe steppublication
    published.status shouldBe "published"
    published.request.target shouldBe _publication
    published.request.input.ownerSessionId shouldBe Some(session)
    published.request.source.head shouldBe closed.commit.identity
    published.request.source.tree shouldBe closed.commit.tree
    published.request.input.requestIdentity should not be failed.request.input.requestIdentity
    published.request.input.requestIdentity should not be steppublication.request.input.requestIdentity
    WorkflowSbtCapture.exitCode(WorkflowSbtCapture.decode(published.process.get).fold(fail(_), identity)) shouldBe Some(0)
    scenario.candidateHead shouldBe head
    scenario.candidateParent shouldBe closed.commit.parent
    scenario.candidateIndexDiff shouldBe index
    _lines(root.resolve("generation-sbt-invocations.txt")) shouldBe calls
    _lines(root.resolve("generation-publication-attempts.txt")) shouldBe attempts
    stablebytes.foreach { (path, bytes) => Files.readAllBytes(path).toVector shouldBe bytes }
    preserved.foreach { (path, bytes) => Files.readAllBytes(path).toVector shouldBe bytes }
    Files.readAllBytes(locator).toVector shouldBe locatorbytes
    Files.readAllBytes(closurelocator).toVector shouldBe closurebytes
    val component = SmWorkflowComponent.componentId.name
    val operations = Vector(component + ".GoalPhaseService.advanceGoalPhase",
      component + ".LocalPublicationService.collectLatestLocalPublication",
      component + ".LocalPublicationService.collectLocalPublication",
      component + ".LocalPublicationService.collectLocalPublication")
    scenario.invocations.flatMap(invocation => operations.distinct.filter(invocation.argv.contains)) shouldBe operations
    val publicationoperations = operations.tail.toSet
    val collectionoutputs = scenario.invocations.filter(invocation => invocation.argv.exists(publicationoperations.contains))
      .map(_.stdout.toString)
    collectionoutputs.size shouldBe 3
    Files.writeString(resultpath, Json.obj("sessionId" -> session.asJson, "frontier" -> "ReviewPhase".asJson,
      "phaseComplete" -> false.asJson,
      "controlledInputs" -> "SPECIFICATION-OWNED review and closure authority; no actual independent provider, Human or consumer evidence".asJson,
      "originalLocator" -> locator.toString.asJson, "closureLocator" -> closurelocator.toString.asJson,
      "selectedOutputs" -> selectedpaths.map(_.toString).asJson,
      "repeatedPhaseOutput" -> repeatedoutput.invocation.stdout.toString.asJson,
      "collectionOutputs" -> collectionoutputs.asJson,
      "originalFailure" -> retainedfailure.asJson, "stepPublication" -> retainedstep.asJson,
      "phasePublication" -> published.asJson, "stepCommit" -> _json(closed.commit),
      "ordinaryRepairCountAtStep" -> reviewed.repairProgress.get.ordinaryCount.asJson,
      "phaseWindowRepairCount" -> phase.repairProgress.get.ordinaryCount.asJson,
      "stepChecks" -> stepchecks.map(_json).asJson, "phaseChecks" -> phasechecks.map(_json).asJson,
      "calls" -> calls.asJson).noSpaces, StandardOpenOption.CREATE_NEW)
  }

  private def _resume_current_generation(locator: Path): Unit = {
    Given("RGEN01 E15: the same captured Session and explicit public artifacts for its later terminated INPUT-kind failure")
    val locatorbytes = Files.readAllBytes(locator).toVector
    val saved = _read(locator)
    val root = Path.of(saved.hcursor.get[String]("root").toOption.get)
    val host = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(saved.hcursor.get[Json]("host").toOption.get))
    val plan = saved.hcursor.get[O.Plan]("plan").toOption.get
    val logindex = sys.props.get("smworkflow.spec.generationLogIndex").map(_.toInt)
      .getOrElse(fail("Select unused smworkflow.spec.generationLogIndex=4000 for this retained continuation"))
    logindex shouldBe 4000
    val scenario = F.reopenPrimaryGeneration(root, host, plan, logindex)
    val task = Path.of(saved.hcursor.get[String]("task").toOption.get)
    val session = scenario.retrieve(task)
    session shouldBe saved.hcursor.get[String]("sessionId").toOption.get
    def _selected_output_(path: Path): Path = {
      path.isAbsolute shouldBe true
      path.normalize shouldBe path
      path.getParent shouldBe root.resolve("logs")
      path.getFileName.toString should fullyMatch regex "[0-9]+\\.stdout\\.json"
      Files.isRegularFile(path) shouldBe true
      path.toRealPath() shouldBe path
      path
    }
    val initialpath = _selected_output_(Path.of(saved.hcursor.get[String]("initialOutput").toOption.get))
    val acceptedpath = _selected_output_(Path.of(saved.hcursor.get[String]("acceptedOutput").toOption.get))
    val firstpath = _selected_output_(Path.of(saved.hcursor.get[String]("failedOutput").toOption.get))
    val secondpath = _selected_output_(_required("smworkflow.spec.generationFailureOutput"))
    val priorrepairpath = _selected_output_(_required("smworkflow.spec.generationRepairOutput"))
    val priorsubmitpath = _selected_output_(_required("smworkflow.spec.generationRepairSubmitOutput"))
    Vector(initialpath, acceptedpath, firstpath, secondpath, priorrepairpath, priorsubmitpath).distinct.size shouldBe 6
    val initial = _business[V.GoalPhaseAdvanceResult](initialpath)
    val accepted = _business[V.GoalPhaseWorkSubmissionResult](acceptedpath)
    val failedoutput = _business[V.GoalPhaseAdvanceResult](secondpath)
    val priorrepair = _business[V.GoalPhaseAdvanceResult](priorrepairpath)
    val priorsubmit = _business[V.GoalPhaseWorkSubmissionResult](priorsubmitpath)
    val preserved = Vector(initialpath, acceptedpath, firstpath, secondpath, priorrepairpath, priorsubmitpath)
      .map(path => path -> Files.readAllBytes(path).toVector)
    val first = saved.hcursor.get[LocalPublication.Result]("failedPublication").toOption.get
    withClue(Files.readString(initialpath)) { initial.work should not be empty }
    withClue(Files.readString(priorrepairpath)) {
      priorrepair.work should not be empty
      priorrepair.work.foreach(_.publicationFailure should not be empty)
      priorrepair.repairProgress should not be empty
    }
    withClue(Files.readString(secondpath)) { failedoutput.localPublication should not be empty }
    initial.sessionId shouldBe session
    accepted.sessionId shouldBe session
    accepted.accepted shouldBe true
    accepted.outcome shouldBe "accepted"
    priorrepair.sessionId shouldBe session
    priorrepair.work.get.kind shouldBe "RepairStep"
    priorrepair.work.get.stepIdentity shouldBe Some("step-1")
    priorrepair.work.get.completed shouldBe initial.work.get.completed
    priorrepair.work.get.originalBase shouldBe initial.work.get.originalBase
    GoalPhaseNativeMetadata.normalizePublicJson(_json(priorrepair.work.get.selectedPlan)).fold(fail(_), identity) shouldBe
      GoalPhaseNativeMetadata.normalizePublicJson(_json(initial.work.get.selectedPlan)).fold(fail(_), identity)
    priorrepair.submitSessionId shouldBe Some(session)
    priorrepair.repairProgress.get.ordinaryCount shouldBe 1
    priorrepair.repairProgress.get.limit shouldBe 2
    priorsubmit.sessionId shouldBe session
    priorsubmit.accepted shouldBe true
    priorsubmit.outcome shouldBe "accepted"
    failedoutput.sessionId shouldBe session
    failedoutput.kind shouldBe "Wait"
    failedoutput.outcome shouldBe Some("failed")
    failedoutput.validation shouldBe None
    val second = GoalPhaseNativeMetadata.normalizePublicJson(_json(failedoutput.localPublication.get)).flatMap(
      _.as[LocalPublication.Result].left.map(_.message)).fold(fail(_), identity)
    val processplan = parse(_read(root.resolve(".textus/config.json")).hcursor
      .get[String]("textus.subsystem.process-execution.plan").toOption.get).fold(error => fail(error.message), identity)
    val processpolicy = io.circe.Printer.noSpaces.copy(sortKeys = true).print(Json.obj("processPlan" -> processplan))
    second.status shouldBe "failed"
    second.request.target shouldBe _publication
    second.request.input.ownerSessionId shouldBe Some(session)
    second.request.input.requestIdentity should not be first.request.input.requestIdentity
    second.request.source.head shouldBe first.request.source.head
    second.request.source.tree should not be first.request.source.tree
    A.generationFailure(second, _publication, session, "participant", second.request.source, processpolicy) shouldBe true
    val capture = WorkflowSbtCapture.decode(second.process.get).fold(fail(_), identity)
    val text = WorkflowSbtCapture.text(capture.stdout).value + "\n" + WorkflowSbtCapture.text(capture.stderr).value
    text should include("[error] Error[org.goldenport.exception.SyntaxErrorFaultException: Operation 'echo' INPUT Value 'ProbeInput' requires input-kind=COMMAND|QUERY.]")
    val priorcontext = GoalPhaseNativeMetadata.normalizePublicJson(_json(priorrepair.work.get.publicationFailure.get)).flatMap(
      _.as[GoalPhaseNativeWork.PublicationFailureContext].left.map(_.message)).fold(fail(_), identity)
    priorcontext.publication shouldBe first
    priorcontext.testsExecuted shouldBe false
    Files.readString(firstpath) shouldBe saved.hcursor.get[String]("failedBytes").toOption.get
    Files.readString(root.resolve(".textus/config.json")) shouldBe saved.hcursor.get[String]("config").toOption.get
    scenario.candidateHead shouldBe saved.hcursor.get[String]("head").toOption.get
    val source = Path.of(host.target.root).resolve(_source)
    Files.readString(source) shouldBe _input_only
    Files.exists(root.resolve("generation-real-test-passed.txt")) shouldBe false
    val oldcalls = _lines(root.resolve("generation-sbt-invocations.txt"))
    oldcalls.size shouldBe 2
    oldcalls.count(_.contains("publishLocal")) shouldBe 2
    _lines(root.resolve("generation-publication-attempts.txt")).size shouldBe 2
    _latest(scenario, session) shouldBe second
    _original(scenario, first) shouldBe first
    _original(scenario, second) shouldBe second

    When("ordinary Advance recollects the terminated second failure and reserves the next repair under the original allowance")
    val reservationoutput = scenario.advance(task)
    val reservation = reservationoutput.business[V.GoalPhaseAdvanceResult]
    Then("the same failed publication remains actual evidence and the ordinary repair count becomes two without a reset")
    withClue(reservationoutput.invocation.logs) {
      reservation.kind shouldBe "Wait"
      reservation.outcome shouldBe Some("failed")
      reservation.localPublication should not be empty
      reservation.repairProgress should not be empty
    }
    GoalPhaseNativeMetadata.normalizePublicJson(_json(reservation.localPublication.get)).flatMap(
      _.as[LocalPublication.Result].left.map(_.message)).fold(fail(_), identity) shouldBe second
    reservation.repairProgress.get.ordinaryCount shouldBe 2
    reservation.repairProgress.get.automaticCount shouldBe 2
    reservation.repairProgress.get.limit shouldBe 2
    reservation.repairProgress shouldBe priorrepair.repairProgress.map(progress => progress.copy(
      automaticCount = progress.automaticCount + 1, ordinaryCount = progress.ordinaryCount + 1))
    When("a separate ordinary Advance issues the new RepairStep and another process recollects that pending request")
    val repairoutput = scenario.advance(task)
    val repair = repairoutput.business[V.GoalPhaseAdvanceResult]
    withClue(repairoutput.invocation.logs) {
      repair.work should not be empty
      repair.work.foreach(_.publicationFailure should not be empty)
    }
    val work = repair.work.get
    val context = GoalPhaseNativeMetadata.normalizePublicJson(_json(work.publicationFailure.get)).flatMap(
      _.as[GoalPhaseNativeWork.PublicationFailureContext].left.map(_.message)).fold(fail(_), identity)
    val repeated = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
    Then("only the second failure is the new repair cause and no previous submission publication or check is replayed")
    repair.sessionId shouldBe session
    repair should not be priorrepair
    work.kind shouldBe "RepairStep"
    work.stepIdentity shouldBe priorrepair.work.get.stepIdentity
    work.completed shouldBe initial.work.get.completed
    work.originalBase shouldBe initial.work.get.originalBase
    GoalPhaseNativeMetadata.normalizePublicJson(_json(work.selectedPlan)).fold(fail(_), identity) shouldBe
      GoalPhaseNativeMetadata.normalizePublicJson(_json(initial.work.get.selectedPlan)).fold(fail(_), identity)
    work.checkFailure shouldBe None
    work.review shouldBe None
    repair.validation shouldBe None
    repair.repairProgress shouldBe reservation.repairProgress
    context.publication shouldBe second
    context.testsExecuted shouldBe false
    context.changedPaths shouldBe Vector(_source)
    work.repairDirective.get.reason should include("generation failure")
    work.repairDirective.get.findings.mkString("\n") should include("requires input-kind=COMMAND|QUERY")
    repeated shouldBe repair
    _lines(root.resolve("generation-sbt-invocations.txt")) shouldBe oldcalls
    Files.readString(source) shouldBe _input_only

    When("the newly assigned worker adds the supported COMMAND input-kind bullet and submits only this new repair")
    Files.writeString(source, _corrected_input)
    val submitted = _submit(scenario, repair, "input-kind")
    val output = scenario.advance(task)
    val reviewed = output.business[V.GoalPhaseAdvanceResult]
    Then("the third real publication and actual Compile and focused test reach the same independent-review frontier")
    withClue(output.invocation.logs) {
      reviewed.work should not be empty
      reviewed.work.foreach(_.review should not be empty)
    }
    reviewed.sessionId shouldBe session
    reviewed.work.get.kind shouldBe "ReviewStep"
    reviewed.work.get.completed shouldBe initial.work.get.completed
    reviewed.work.get.originalBase shouldBe initial.work.get.originalBase
    GoalPhaseNativeMetadata.normalizePublicJson(_json(reviewed.work.get.selectedPlan)).fold(fail(_), identity) shouldBe
      GoalPhaseNativeMetadata.normalizePublicJson(_json(initial.work.get.selectedPlan)).fold(fail(_), identity)
    reviewed.repairProgress shouldBe repair.repairProgress
    val review = reviewed.work.get.review.get
    review.changedPaths shouldBe Vector(_source)
    review.checks.map(_.identity) shouldBe _checks.map(_.identity)
    review.checks.map(_.sbt.map(_.kind)) shouldBe Vector(Some("compile"), Some("test-only"))
    review.checks.foreach { receipt =>
      receipt.exitCode shouldBe Some(0)
      receipt.sbt.map(_.outcome) shouldBe Some("succeeded")
      receipt.processObservation should not be empty
      val projection = GoalPhaseNativeMetadata.normalizePublicJson(_json(receipt.processObservation.get)).fold(fail(_), identity)
      val raw = projection.mapObject(fields => Vector("stdout", "stderr").foldLeft(fields) { (prior, key) =>
        prior.add(key, fields(key).get.mapObject(_.remove("textStatus").remove("trailingUndecodedBytes")))
      })
      val observed = WorkflowSbtCapture.decode(raw).fold(fail(_), identity)
      WorkflowSbtCapture.projection(observed) shouldBe projection
      WorkflowSbtCapture.exitCode(observed) shouldBe Some(0)
    }
    Files.readString(root.resolve("generation-real-test-passed.txt")) shouldBe "qualified-input\n"
    val published = _latest(scenario, session)
    published.status shouldBe "published"
    WorkflowSbtCapture.exitCode(WorkflowSbtCapture.decode(published.process.get).fold(fail(_), identity)) shouldBe Some(0)
    Set(first.request.input.requestIdentity, second.request.input.requestIdentity) should not contain published.request.input.requestIdentity
    published.request.source.head shouldBe first.request.source.head
    published.request.source.tree should not be second.request.source.tree
    val calls = _lines(root.resolve("generation-sbt-invocations.txt"))
    calls.size shouldBe oldcalls.size + 3
    calls.count(_.contains("publishLocal")) shouldBe 3
    calls.count(_.contains("testOnly")) shouldBe 1
    calls.count(_.contains("compile")) shouldBe 1
    _lines(root.resolve("generation-publication-attempts.txt")).size shouldBe 3
    scenario.candidateHead shouldBe saved.hcursor.get[String]("head").toOption.get
    When("a final installed process recollects pending review and each exact historical publication")
    val recollected = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
    reviewed.validation should not be empty
    recollected.validation should not be empty
    recollected.validation.get.assessmentJournal shouldBe None
    recollected shouldBe reviewed.copy(validation = reviewed.validation.map(_.copy(assessmentJournal = None)))
    _original(scenario, first) shouldBe first
    _original(scenario, second) shouldBe second
    _latest(scenario, session) shouldBe published
    Then("both failed captures every prior accepted artifact and all successful receipts remain unchanged without replay")
    preserved.foreach { (path, bytes) => Files.readAllBytes(path).toVector shouldBe bytes }
    Files.readAllBytes(locator).toVector shouldBe locatorbytes
    _lines(root.resolve("generation-sbt-invocations.txt")) shouldBe calls
    _lines(root.resolve("generation-publication-attempts.txt")).size shouldBe 3
    Files.writeString(root.resolve("generation-continuation-result.json"), Json.obj("sessionId" -> session.asJson,
      "frontier" -> "independent-review-required".asJson, "reviewOutput" -> output.invocation.stdout.toString.asJson,
      "originalFailure" -> first.asJson, "secondFailure" -> second.asJson,
      "priorRepairOutput" -> priorrepairpath.toString.asJson, "priorRepairSubmitOutput" -> priorsubmitpath.toString.asJson,
      "currentRepairOutput" -> repairoutput.invocation.stdout.toString.asJson,
      "currentRepairSubmitOutput" -> submitted.invocation.stdout.toString.asJson,
      "published" -> published.asJson, "checks" -> review.checks.map(value => _json(value)).asJson, "calls" -> calls.asJson).noSpaces,
      StandardOpenOption.CREATE_NEW)
  }

  private def _collect_review(locator: Path): Unit = {
    Given("RGEN01 E15: explicit saved successful review and pending-review outputs from the same completed installed effects")
    val locatorbytes = Files.readAllBytes(locator).toVector
    val saved = _read(locator)
    val root = Path.of(saved.hcursor.get[String]("root").toOption.get)
    val host = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(saved.hcursor.get[Json]("host").toOption.get))
    val plan = saved.hcursor.get[O.Plan]("plan").toOption.get
    val session = saved.hcursor.get[String]("sessionId").toOption.get
    val initialpath = Path.of(saved.hcursor.get[String]("initialOutput").toOption.get)
    val failedpath = Path.of(saved.hcursor.get[String]("failedOutput").toOption.get)
    val acceptedpath = Path.of(saved.hcursor.get[String]("acceptedOutput").toOption.get)
    val reviewpath = _required("smworkflow.spec.generationReviewOutput")
    val repeatedpath = _required("smworkflow.spec.generationRepeatedReviewOutput")
    val paths = Vector(initialpath, failedpath, acceptedpath, reviewpath, repeatedpath)
    paths.distinct.size shouldBe paths.size
    paths.foreach { path =>
      path.isAbsolute shouldBe true
      path.normalize shouldBe path
      path.getParent shouldBe root.resolve("logs")
      path.getFileName.toString should fullyMatch regex "[0-9]+\\.stdout\\.json"
      Files.isRegularFile(path) shouldBe true
      path.toRealPath() shouldBe path
    }
    reviewpath.getFileName.toString shouldBe "2007.stdout.json"
    repeatedpath.getFileName.toString shouldBe "2010.stdout.json"
    val preserved = paths.map(path => path -> Files.readAllBytes(path).toVector)
    val initial = _business[V.GoalPhaseAdvanceResult](initialpath)
    val failedoutput = _business[V.GoalPhaseAdvanceResult](failedpath)
    val accepted = _business[V.GoalPhaseWorkSubmissionResult](acceptedpath)
    val reviewed = _business[V.GoalPhaseAdvanceResult](reviewpath)
    val repeated = _business[V.GoalPhaseAdvanceResult](repeatedpath)
    val first = saved.hcursor.get[LocalPublication.Result]("failedPublication").toOption.get
    withClue(Files.readString(initialpath)) { initial.work should not be empty }
    withClue(Files.readString(failedpath)) { failedoutput.localPublication should not be empty }
    withClue(Files.readString(reviewpath)) {
      reviewed.work should not be empty
      reviewed.work.foreach(_.review should not be empty)
      reviewed.work.foreach(_.candidate should not be empty)
      reviewed.validation should not be empty
      reviewed.repairProgress should not be empty
    }
    When("the complete saved public results and actual successful check captures are decoded without advancing the Session")
    initial.sessionId shouldBe session
    initial.work.get.kind shouldBe "ImplementStep"
    failedoutput.sessionId shouldBe session
    failedoutput.outcome shouldBe Some("failed")
    GoalPhaseNativeMetadata.normalizePublicJson(_json(failedoutput.localPublication.get)).flatMap(
      _.as[LocalPublication.Result].left.map(_.message)).fold(fail(_), identity) shouldBe first
    first.status shouldBe "failed"
    first.request.input.ownerSessionId shouldBe Some(session)
    accepted.sessionId shouldBe session
    accepted.accepted shouldBe true
    accepted.outcome shouldBe "accepted"
    reviewed.sessionId shouldBe session
    reviewed.kind shouldBe "Request"
    reviewed.work.get.kind shouldBe "ReviewStep"
    repeated.sessionId shouldBe session
    repeated.validation should not be empty
    repeated.validation.get.assessmentJournal shouldBe None
    repeated shouldBe reviewed.copy(validation = reviewed.validation.map(_.copy(assessmentJournal = None)))
    val work = reviewed.work.get
    work.stepIdentity shouldBe initial.work.get.stepIdentity
    work.completed shouldBe initial.work.get.completed
    work.originalBase shouldBe initial.work.get.originalBase
    GoalPhaseNativeMetadata.normalizePublicJson(_json(work.selectedPlan)).fold(fail(_), identity) shouldBe
      GoalPhaseNativeMetadata.normalizePublicJson(_json(initial.work.get.selectedPlan)).fold(fail(_), identity)
    GoalPhaseNativeMetadata.normalizePublicJson(_json(F.publicPlan(plan))).fold(fail(_), identity) shouldBe
      GoalPhaseNativeMetadata.normalizePublicJson(_json(initial.work.get.selectedPlan)).fold(fail(_), identity)
    reviewed.repairProgress.get.automaticCount shouldBe 1
    reviewed.repairProgress.get.ordinaryCount shouldBe 1
    reviewed.repairProgress.get.limit shouldBe 2
    val validation = reviewed.validation.get
    validation.assessmentJournal.map(_.status) shouldBe Some("disabled")
    validation.outcome shouldBe Some("passed")
    validation.candidate shouldBe work.candidate.get
    val review = work.review.get
    review.changedPaths shouldBe Vector(_source)
    validation.selectedChecks.map(_.identity) shouldBe _checks.map(_.identity)
    validation.checks shouldBe review.checks
    review.checks.map(_.identity) shouldBe _checks.map(_.identity)
    review.checks.map(_.sbt.map(_.kind)) shouldBe Vector(Some("compile"), Some("test-only"))
    review.checks.foreach { receipt =>
      receipt.termination shouldBe "exited"
      receipt.exitCode shouldBe Some(0)
      receipt.sbt.map(_.outcome) shouldBe Some("succeeded")
      receipt.processObservation should not be empty
      val projection = GoalPhaseNativeMetadata.normalizePublicJson(_json(receipt.processObservation.get)).fold(fail(_), identity)
      val raw = projection.mapObject(fields => Vector("stdout", "stderr").foldLeft(fields) { (prior, key) =>
        prior.add(key, fields(key).get.mapObject(_.remove("textStatus").remove("trailingUndecodedBytes")))
      })
      val observed = WorkflowSbtCapture.decode(raw).fold(fail(_), identity)
      WorkflowSbtCapture.projection(observed) shouldBe projection
      WorkflowSbtCapture.exitCode(observed) shouldBe Some(0)
      WorkflowSbtCapture.text(observed.stdout).textStatus shouldBe "complete"
      WorkflowSbtCapture.text(observed.stderr).textStatus shouldBe "complete"
      observed.stdout.truncated shouldBe false
      observed.stderr.truncated shouldBe false
    }
    val focused = review.checks.last.sbt.get.selection.get
    focused.testClasses shouldBe Vector(_suite)
    focused.completedSuites shouldBe 1
    focused.testsRun shouldBe 1
    val source = Path.of(host.target.root).resolve(_source)
    Files.readString(source) shouldBe _corrected_input
    val marker = root.resolve("generation-real-test-passed.txt")
    Files.readString(marker) shouldBe "qualified-input\n"
    val callspath = root.resolve("generation-sbt-invocations.txt")
    val callsbytes = Files.readAllBytes(callspath).toVector
    val calls = _lines(callspath)
    calls.size shouldBe 4
    calls.count(_.contains("publishLocal")) shouldBe 2
    calls.count(_.contains("compile")) shouldBe 1
    calls.count(_.contains("testOnly")) shouldBe 1
    calls.take(saved.hcursor.get[Vector[String]]("calls").toOption.get.size) shouldBe
      saved.hcursor.get[Vector[String]]("calls").toOption.get
    val attemptspath = root.resolve("generation-publication-attempts.txt")
    val attemptsbytes = Files.readAllBytes(attemptspath).toVector
    _lines(attemptspath).size shouldBe 2
    Files.readString(failedpath) shouldBe saved.hcursor.get[String]("failedBytes").toOption.get
    val resultpath = root.resolve("generation-continuation-result.json")
    Files.exists(resultpath) shouldBe false
    Then("the saved review has complete passed receipts and pending recollection differs only by the omitted optional assessment journal acknowledgement")
    repeated.work shouldBe reviewed.work

    When("the same exact fixture is reopened at unused log range 3000 solely for two read-only publication collections")
    val scenario = F.reopenPrimaryGeneration(root, host, plan, 3000)
    scenario.retrieve(Path.of(saved.hcursor.get[String]("task").toOption.get)) shouldBe session
    scenario.invocations shouldBe empty
    val original = _original(scenario, first)
    val published = _latest(scenario, session)
    Then("the original failure and successful current candidate remain actual retained results without any repeated effects")
    original shouldBe first
    published.status shouldBe "published"
    published.request.target shouldBe _publication
    published.request.input.ownerSessionId shouldBe Some(session)
    published.request.input.requestIdentity should not be first.request.input.requestIdentity
    published.request.source.head shouldBe work.candidate.get.head
    published.request.source.tree shouldBe work.candidate.get.tree
    published.request.source.head shouldBe first.request.source.head
    published.request.source.tree should not be first.request.source.tree
    val publishedcapture = WorkflowSbtCapture.decode(published.process.get).fold(fail(_), identity)
    WorkflowSbtCapture.exitCode(publishedcapture) shouldBe Some(0)
    scenario.invocations.map(_.stdout.getFileName.toString) shouldBe Vector("3000.stdout.json", "3001.stdout.json")
    preserved.foreach { (path, bytes) => Files.readAllBytes(path).toVector shouldBe bytes }
    Files.readAllBytes(locator).toVector shouldBe locatorbytes
    Files.readAllBytes(callspath).toVector shouldBe callsbytes
    Files.readAllBytes(attemptspath).toVector shouldBe attemptsbytes
    Files.readString(source) shouldBe _corrected_input
    Files.readString(marker) shouldBe "qualified-input\n"
    Files.writeString(resultpath, Json.obj("sessionId" -> session.asJson,
      "frontier" -> "independent-review-required".asJson, "reviewOutput" -> reviewpath.toString.asJson,
      "repeatedReviewOutput" -> repeatedpath.toString.asJson,
      "collectionOutputs" -> scenario.invocations.map(_.stdout.toString).asJson,
      "originalFailure" -> original.asJson, "published" -> published.asJson,
      "validation" -> _json(validation), "checks" -> review.checks.map(value => _json(value)).asJson,
      "calls" -> calls.asJson).noSpaces, StandardOpenOption.CREATE_NEW)
  }

  private def _capture_history(locator: Path): Unit = {
    Given("R5-SH01: the old normal installed runtime and a fresh specification-owned tiny generation/history subject")
    Files.exists(locator) shouldBe false
    val initialscenario = F.fresh("primary-generation-history-capture",
      sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable), repairContinuation = true)
    _build(initialscenario)
    val scenario = _configure(initialscenario, history = true)
    val repository = Path.of(scenario.host.target.root)
    val task = scenario.ownTask("primary-generation-history-recovery")
    val prepared = scenario.prepareHostInput("prepareGoalStart", scenario.selectedGoalPreparationInput.toRecord())
    scenario.selectPreparedImport(prepared)
    val started = scenario.start().business[V.GoalPhaseStartResult]
    scenario.retain(task, started)
    val issued = scenario.advance(task)
    val initial = issued.business[V.GoalPhaseAdvanceResult]
    initial.work.get.kind shouldBe "ImplementStep"
    Files.exists(repository.resolve(_history_source)) shouldBe false
    Files.writeString(repository.resolve(_source), _missing_input, StandardOpenOption.CREATE_NEW)
    val accepted = _submit(scenario, initial, "history-initial")
    When("normal installed Advance captures the candidate and its first beforeChecks publishLocal produces the actual NoInput failure")
    val failedoutput = scenario.advance(task)
    val failed = failedoutput.business[V.GoalPhaseAdvanceResult]
    val publication = _latest(scenario, started.sessionId)
    val process = WorkflowSbtCapture.decode(publication.process.get).fold(fail(_), identity)
    val text = WorkflowSbtCapture.text(process.stdout).value + "\n" + WorkflowSbtCapture.text(process.stderr).value
    Then("one actual NoInput publication fails before checks and reserves ordinary repair one under the unchanged limit")
    failed.outcome shouldBe Some("failed")
    failed.validation shouldBe None
    failed.repairProgress.get.ordinaryCount shouldBe 1
    failed.repairProgress.get.automaticCount shouldBe 1
    failed.repairProgress.get.limit shouldBe 2
    publication.status shouldBe "failed"
    WorkflowSbtCapture.exitCode(process) shouldBe Some(1)
    WorkflowSbtCapture.text(process.stdout).textStatus shouldBe "complete"
    WorkflowSbtCapture.text(process.stderr).textStatus shouldBe "complete"
    process.stdout.truncated shouldBe false
    process.stderr.truncated shouldBe false
    text should include("[error] Error[No input]")
    text should include("GENERATION_PROVENANCE_OUTPUT_TAMPERED")
    text should include("[error] (cozyGenerate) [sbt-cozy] cozy delegate failed (1) for ")
    val oldcalls = _lines(scenario.root.resolve("generation-sbt-invocations.txt"))
    oldcalls.size shouldBe 1
    oldcalls.head should include("publishLocal")
    _lines(scenario.root.resolve("generation-publication-attempts.txt")) shouldBe oldcalls
    Files.exists(scenario.root.resolve("generation-real-test-passed.txt")) shouldBe false

    When("ordinary Advance issues only the newly reserved RepairStep and the fixture supplies complete corrected CML plus the deliberate history fault")
    val repairoutput = scenario.advance(task)
    val repair = repairoutput.business[V.GoalPhaseAdvanceResult]
    repair.work.get.kind shouldBe "RepairStep"
    repair.work.get.stepIdentity shouldBe Some("step-1")
    repair.work.get.originalBase shouldBe initial.work.get.originalBase
    repair.work.get.selectedPlan shouldBe initial.work.get.selectedPlan
    repair.work.get.completed shouldBe initial.work.get.completed
    repair.repairProgress shouldBe failed.repairProgress
    val context = GoalPhaseNativeMetadata.normalizePublicJson(_json(repair.work.get.publicationFailure.get)).flatMap(
      _.as[GoalPhaseNativeWork.PublicationFailureContext].left.map(_.message)).fold(fail(_), identity)
    context.publication shouldBe publication
    context.testsExecuted shouldBe false
    val source = repository.resolve(_history_source)
    Files.createDirectories(source.getParent)
    Files.writeString(repository.resolve(_source), _corrected_input)
    Files.writeString(source, _history_duplicate, StandardOpenOption.CREATE_NEW)
    val acceptedrepair = _submit(scenario, repair, "history-corrected-cml")
    val index = Files.readAllBytes(repository.resolve(".git/index")).toVector
    val stopped = scenario.advance(task)
    Then("ordinary candidate interpretation fails on two genuine managed blocks before staging publication or checks")
    stopped.exitCode shouldBe 1
    stopped.invocation.logs should include("multiple managed block comments")
    stopped.invocation.logs should include("500")
    Files.readString(source) shouldBe _history_duplicate
    Files.readAllBytes(repository.resolve(".git/index")).toVector shouldBe index
    _lines(scenario.root.resolve("generation-sbt-invocations.txt")) shouldBe oldcalls
    _lines(scenario.root.resolve("generation-publication-attempts.txt")) shouldBe oldcalls
    val events = Files.readString(stopped.invocation.stderr).linesIterator.flatMap(line => parse(line).toOption)
      .filter(_.hcursor.get[String]("event").contains("goal-source-history-invocation")).toVector
    events.size shouldBe 1
    val invocation = events.head.hcursor.get[GoalPhaseSourceHistoryAutomatic.Invocation]("invocation").toOption.get
    invocation.sessionId shouldBe started.sessionId
    val fixture = PrimaryGenerationHistorySpecificationFixture.Implemented(scenario, task, initial, source)
    val primary = PrimaryGenerationHistorySpecificationFixture.primary(fixture)
    primary.size shouldBe 1
    val unknown = PrimaryGenerationHistorySpecificationFixture.intents(fixture).filter(_.path == _history_source)
    unknown.size shouldBe 1
    unknown.head.outcome shouldBe None
    unknown.head.invocation shouldBe Some(invocation)
    unknown.head.historyPolicy shouldBe Some(PrimaryGenerationHistorySpecificationFixture.rule)
    val before = PrimaryGenerationHistorySpecificationFixture.native(fixture)
    When("the correct host describes this stopped source through the old runtime without any retry")
    val description = scenario.call("command", R.describeOperation, Vector("--sessionId", started.sessionId))
    Then("the old exact-path policy visibly refuses while retaining the actual primary fact and unknown history intent")
    description.exitCode shouldBe 1
    description.invocation.logs should include("409")
    description.invocation.logs should include("retained candidate crosses the actual source, Step or host path policy")
    PrimaryGenerationHistorySpecificationFixture.native(fixture) shouldBe before
    Files.readAllBytes(repository.resolve(".git/index")).toVector shouldBe index
    Files.readString(source) shouldBe _history_duplicate
    _original(scenario, publication) shouldBe publication
    val outputs = Vector(issued, accepted, failedoutput, repairoutput, acceptedrepair, stopped, description)
    val retained = outputs.flatMap(result => Vector(result.invocation.stdout, result.invocation.stderr)).map(path =>
      Json.obj("path" -> path.toString.asJson, "text" -> Files.readString(path).asJson))
    val saved = Json.obj("scenario" -> "primary-generation-history".asJson,
      "root" -> scenario.root.toString.asJson, "host" -> ThreeWorkflowRuntimeParameters.encode(scenario.host),
      "plan" -> scenario.plan.asJson, "task" -> task.toString.asJson, "sessionId" -> started.sessionId.asJson,
      "initialOutput" -> issued.invocation.stdout.toString.asJson, "acceptedOutput" -> accepted.invocation.stdout.toString.asJson,
      "failedOutput" -> failedoutput.invocation.stdout.toString.asJson, "repairOutput" -> repairoutput.invocation.stdout.toString.asJson,
      "acceptedRepairOutput" -> acceptedrepair.invocation.stdout.toString.asJson,
      "failedAdvanceOutput" -> stopped.invocation.stdout.toString.asJson, "failedAdvanceStderr" -> stopped.invocation.stderr.toString.asJson,
      "descriptionOutput" -> description.invocation.stdout.toString.asJson, "retainedOutputs" -> retained.asJson,
      "failedPublication" -> publication.asJson, "primaryFact" -> A.codec.encode(primary.head.fact),
      "primaryReuse" -> primary.head.reuse.asJson, "unknownIntent" -> unknown.head.asJson,
      "invocationEvent" -> events.head, "termination" -> "exited".asJson, "exitCode" -> stopped.exitCode.asJson,
      "config" -> Files.readString(scenario.root.resolve(".textus/config.json")).asJson,
      "head" -> scenario.candidateHead.asJson, "indexDiff" -> scenario.candidateIndexDiff.asJson,
      "scope" -> Vector("src/main").asJson, "calls" -> oldcalls.asJson,
      "invocations" -> scenario.invocations.map(value => Json.obj("argv" -> value.argv.asJson,
        "pid" -> value.pid.asJson, "stdout" -> value.stdout.toString.asJson, "stderr" -> value.stderr.toString.asJson)).asJson)
    Files.writeString(locator, saved.noSpaces, StandardOpenOption.CREATE_NEW)
  }

  private def _continue_history(locator: Path): Unit = {
    Given("R5-SH01: the exact tiny history locator and newly published installed runtime, with no consumer authority implied")
    val locatorbytes = Files.readAllBytes(locator).toVector
    val saved = _read(locator)
    saved.hcursor.get[String]("scenario").toOption shouldBe Some("primary-generation-history")
    val root = Path.of(saved.hcursor.get[String]("root").toOption.get)
    val host = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(saved.hcursor.get[Json]("host").toOption.get))
    val plan = saved.hcursor.get[O.Plan]("plan").toOption.get
    plan.identity shouldBe "native-primary-generation-history-plan"
    val scenario = F.reopenPrimaryGeneration(root, host, plan, 2000)
    val repository = Path.of(host.target.root)
    val task = Path.of(saved.hcursor.get[String]("task").toOption.get)
    val session = scenario.retrieve(task)
    session shouldBe saved.hcursor.get[String]("sessionId").toOption.get
    val initial = _business[V.GoalPhaseAdvanceResult](Path.of(saved.hcursor.get[String]("initialOutput").toOption.get))
    val repair = _business[V.GoalPhaseAdvanceResult](Path.of(saved.hcursor.get[String]("repairOutput").toOption.get))
    val failed = saved.hcursor.get[LocalPublication.Result]("failedPublication").toOption.get
    val oldcalls = saved.hcursor.get[Vector[String]]("calls").toOption.get
    val source = repository.resolve(_history_source)
    val fixture = PrimaryGenerationHistorySpecificationFixture.Implemented(scenario, task, initial, source)
    val retained = saved.hcursor.get[Vector[Json]]("retainedOutputs").toOption.get.map { entry =>
      val path = Path.of(entry.hcursor.get[String]("path").toOption.get)
      path.isAbsolute shouldBe true
      path.normalize shouldBe path
      path.getParent shouldBe root.resolve("logs")
      path.toRealPath() shouldBe path
      path -> entry.hcursor.get[String]("text").toOption.get
    }
    def _preserved_(): Unit = {
      retained.foreach { (path, text) => Files.readString(path) shouldBe text }
      Files.readAllBytes(locator).toVector shouldBe locatorbytes
      scenario.candidateHead shouldBe saved.hcursor.get[String]("head").toOption.get
    }
    _preserved_()
    Files.readString(root.resolve(".textus/config.json")) shouldBe saved.hcursor.get[String]("config").toOption.get
    Files.readString(source) shouldBe _history_duplicate
    Files.readString(repository.resolve(_source)) shouldBe _corrected_input
    scenario.candidateIndexDiff shouldBe saved.hcursor.get[String]("indexDiff").toOption.get
    val index = Files.readAllBytes(repository.resolve(".git/index")).toVector
    val native = PrimaryGenerationHistorySpecificationFixture.native(fixture)
    val primary = PrimaryGenerationHistorySpecificationFixture.primary(fixture)
    primary.size shouldBe 1
    A.codec.encode(primary.head.fact) shouldBe saved.hcursor.get[Json]("primaryFact").toOption.get
    primary.head.reuse.asJson shouldBe saved.hcursor.get[Json]("primaryReuse").toOption.get
    _latest(scenario, session) shouldBe failed
    _original(scenario, failed) shouldBe failed

    When("repeated public recovery and normalization descriptions reconstruct the actual primary Saved without any effect")
    val described = scenario.call("command", R.describeOperation, Vector("--sessionId", session))
    val description = described.business[V.GoalPhaseSourceHistoryRecoveryDescriptionResult]
    val target = take(R.targetC(description.target))
    target.sessionId shouldBe session
    target.planIdentity shouldBe plan.identity
    target.planRevision shouldBe plan.revision
    target.base.head shouldBe initial.work.get.originalBase.head
    target.base.tree shouldBe initial.work.get.originalBase.tree
    target.step shouldBe "step-1"
    target.candidatePaths shouldBe Vector("src/main")
    target.ownedPaths shouldBe Vector("src/main")
    target.inspection.selectedPaths.toSet shouldBe Set(_source, _history_source)
    target.inspection.primaryPublicationRepair shouldBe Some(primary.head.reuse)
    target.historyPolicy shouldBe PrimaryGenerationHistorySpecificationFixture.rule
    target.completedPrefix shouldBe empty
    target.intentIndex shouldBe 0
    target.intent.asJson shouldBe saved.hcursor.get[Json]("unknownIntent").toOption.get
    target.intent.path shouldBe _history_source
    target.intent.outcome shouldBe None
    target.intent.invocation.map(_.asJson) shouldBe saved.hcursor.downField("invocationEvent").get[Json]("invocation").toOption
    PrimaryGenerationHistorySpecificationFixture.intent(fixture, target) shouldBe target.intent
    val normalization = scenario.call("command", N.describeOperation, Vector("--sessionId", session))
      .business[V.GoalPhaseSourceHistoryNormalizationDescriptionResult]
    take(N.targetC(normalization.target)).recovery shouldBe target
    val repeated = scenario.call("command", R.describeOperation, Vector("--sessionId", session))
      .business[V.GoalPhaseSourceHistoryRecoveryDescriptionResult]
    take(R.targetC(repeated.target)) shouldBe target
    take(N.targetC(scenario.call("command", N.describeOperation, Vector("--sessionId", session))
      .business[V.GoalPhaseSourceHistoryNormalizationDescriptionResult].target)).recovery shouldBe target
    Then("the old source intent prefix and primary fact stay exact and no file staging publication or check occurs")
    PrimaryGenerationHistorySpecificationFixture.native(fixture) shouldBe native
    Files.readString(source) shouldBe _history_duplicate
    Files.readString(repository.resolve(_source)) shouldBe _corrected_input
    Files.readAllBytes(repository.resolve(".git/index")).toVector shouldBe index
    _lines(root.resolve("generation-sbt-invocations.txt")) shouldBe oldcalls

    When("a structurally valid crossed Plan target and ordinary Advance without terminal evidence are supplied")
    val crossed = V.GoalPhaseSourceHistoryRecoveryInput(session, R.targetRecord(target.copy(planRevision = target.planRevision + "-crossed")),
      "AuthorizeNewAttempt", "SPECIFICATION-OWNED crossed target must refuse", V.WorkflowArtifactReference("history-crossed", "1"))
    val refused = scenario.call("command", R.recoveryOperation, Vector("--input", _json(crossed).noSpaces))
    refused.exitCode should not be 0
    refused.invocation.logs should include("exact current target")
    val waiting = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
    waiting.kind shouldBe "Wait"
    waiting.work shouldBe None
    Then("unavailable Human or terminal evidence cannot grant another effect or broaden ownership")
    PrimaryGenerationHistorySpecificationFixture.native(fixture) shouldBe native
    Files.readString(source) shouldBe _history_duplicate
    Files.readString(repository.resolve(_source)) shouldBe _corrected_input
    Files.readAllBytes(repository.resolve(".git/index")).toVector shouldBe index
    _lines(root.resolve("generation-sbt-invocations.txt")) shouldBe oldcalls
    _preserved_()

    When("this explicit specification-owned subject prepares fresh independent Human authority for a NEW attempt accepting old UNKNOWN")
    val directory = Files.createDirectory(root.resolve("history-fresh-human"))
    val reference = V.WorkflowArtifactReference("specification-owned-history-new-attempt", "1")
    val rationale = "SPECIFICATION-OWNED fresh independent Human accepts the old UNKNOWN and possible NEW normalization only in this tiny fixture"
    val answer = V.GoalPhaseSourceHistoryRecoveryAnswer("AuthorizeNewAttempt", rationale)
    def _input_file_(name: String, value: RecordPresentable): String = {
      val file = directory.resolve(name + ".json")
      Files.writeString(file, _json(value).noSpaces, StandardOpenOption.CREATE_NEW)
      file.toUri.toASCIIString
    }
    val preparation = V.WorkflowEvidencePreparationInput("GoalPhase", "source-history-recovery",
      described.invocation.stdout.toUri.toASCIIString, _input_file_("answer", answer), _input_file_("reference", reference),
      _input_file_("execution", V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)),
      "normal-development-host", "2026-11-06T00:00:00Z", "entity:smworkflow.cli.host_evidence", Some("participant"))
    val prepared = scenario.prepareHostInput("prepareEvidence", preparation.toRecord())
    scenario.selectPreparedImport(prepared)
    val beforedirection = PrimaryGenerationHistorySpecificationFixture.current(fixture)
    val direction = V.GoalPhaseSourceHistoryRecoveryInput(session, description.target, answer.choice, answer.rationale, reference)
    val authorized = scenario.call("command", R.recoveryOperation, Vector("--input", _json(direction).noSpaces))
    authorized.business[V.GoalPhaseSourceHistoryRecoveryResult].outcome shouldBe "new-attempt-authorized"
    val afterdirection = PrimaryGenerationHistorySpecificationFixture.current(fixture)
    Then("recovery appends only the exact independent direction and keeps the old File outcome unknown")
    afterdirection.revision shouldBe beforedirection.revision.next
    afterdirection.history.dropRight(1) shouldBe beforedirection.history
    afterdirection.causation.value shouldBe "ordered-source-history-recovery:" + target.sourceRevision
    PrimaryGenerationHistorySpecificationFixture.intent(fixture, target) shouldBe target.intent
    Files.readString(source) shouldBe _history_duplicate
    Files.readString(repository.resolve(_source)) shouldBe _corrected_input
    Files.readAllBytes(repository.resolve(".git/index")).toVector shouldBe index
    _lines(root.resolve("generation-sbt-invocations.txt")) shouldBe oldcalls

    When("the authorized new candidate consolidates file history while retaining both class histories declarations and literal values")
    _history_duplicate.linesIterator.filter(_.startsWith("object ")).toVector shouldBe
      _history_corrected.linesIterator.filter(_.startsWith("object ")).toVector
    Files.writeString(source, _history_corrected)
    val advanced = scenario.advance(task)
    val reviewed = advanced.business[V.GoalPhaseAdvanceResult]
    Then("ordinary Advance owns new capture normalization staging successful publication Compile and one real focused test")
    withClue(advanced.invocation.logs) {
      reviewed.work should not be empty
      reviewed.work.foreach(_.review should not be empty)
      reviewed.validation should not be empty
    }
    reviewed.sessionId shouldBe session
    reviewed.work.get.kind shouldBe "ReviewStep"
    reviewed.work.get.selectedPlan shouldBe initial.work.get.selectedPlan
    reviewed.work.get.originalBase shouldBe initial.work.get.originalBase
    reviewed.work.get.completed shouldBe initial.work.get.completed
    reviewed.repairProgress shouldBe repair.repairProgress
    reviewed.repairProgress.get.ordinaryCount shouldBe 1
    reviewed.repairProgress.get.automaticCount shouldBe 1
    reviewed.repairProgress.get.limit shouldBe 2
    val review = reviewed.work.get.review.get
    review.changedPaths.toSet shouldBe Set(_source, _history_source)
    review.checks.map(_.identity) shouldBe _checks.map(_.identity)
    review.checks.map(_.sbt.map(_.kind)) shouldBe Vector(Some("compile"), Some("test-only"))
    review.checks.foreach { receipt =>
      receipt.exitCode shouldBe Some(0)
      receipt.sbt.map(_.outcome) shouldBe Some("succeeded")
      receipt.processObservation should not be empty
      val projection = GoalPhaseNativeMetadata.normalizePublicJson(_json(receipt.processObservation.get)).fold(fail(_), identity)
      val raw = projection.mapObject(fields => Vector("stdout", "stderr").foldLeft(fields) { (prior, key) =>
        prior.add(key, fields(key).get.mapObject(_.remove("textStatus").remove("trailingUndecodedBytes")))
      })
      val capture = WorkflowSbtCapture.decode(raw).fold(fail(_), identity)
      WorkflowSbtCapture.projection(capture) shouldBe projection
      WorkflowSbtCapture.exitCode(capture) shouldBe Some(0)
      WorkflowSbtCapture.text(capture.stdout).textStatus shouldBe "complete"
      WorkflowSbtCapture.text(capture.stderr).textStatus shouldBe "complete"
      capture.stdout.truncated shouldBe false
      capture.stderr.truncated shouldBe false
    }
    review.checks.last.sbt.get.selection.get.testClasses shouldBe Vector(_suite)
    review.checks.last.sbt.get.selection.get.completedSuites shouldBe 1
    review.checks.last.sbt.get.selection.get.testsRun shouldBe 1
    Files.readString(source).linesIterator.filter(_.startsWith("object ")).toVector shouldBe
      _history_corrected.linesIterator.filter(_.startsWith("object ")).toVector
    Files.readString(source) should include("First executable declaration introduced and updated Nov. 6, 2026 by ASAMI, Tomoharu.")
    Files.readString(source) should include("Second executable declaration introduced and updated Nov. 6, 2026 by ASAMI, Tomoharu.")
    Files.readString(root.resolve("generation-real-test-passed.txt")) shouldBe "qualified-input\n"
    val published = _latest(scenario, session)
    published.status shouldBe "published"
    WorkflowSbtCapture.exitCode(WorkflowSbtCapture.decode(published.process.get).fold(fail(_), identity)) shouldBe Some(0)
    published.request.input.requestIdentity should not be failed.request.input.requestIdentity
    published.request.source.head shouldBe failed.request.source.head
    published.request.source.tree should not be failed.request.source.tree
    val calls = _lines(root.resolve("generation-sbt-invocations.txt"))
    calls.take(oldcalls.size) shouldBe oldcalls
    calls.size shouldBe 4
    calls.count(_.contains("publishLocal")) shouldBe 2
    calls.count(_.contains("compile")) shouldBe 1
    calls.count(_.contains("testOnly")) shouldBe 1
    _lines(root.resolve("generation-publication-attempts.txt")) shouldBe calls.filter(_.contains("publishLocal"))
    val recollectedoutput = scenario.advance(task)
    val recollected = recollectedoutput.business[V.GoalPhaseAdvanceResult]
    recollected.work shouldBe reviewed.work
    recollected.validation should not be empty
    Set(reviewed.validation.get.assessmentJournal, None)(recollected.validation.get.assessmentJournal) shouldBe true
    recollected.copy(validation = recollected.validation.map(_.copy(assessmentJournal = reviewed.validation.get.assessmentJournal))) shouldBe reviewed
    _original(scenario, failed) shouldBe failed
    _latest(scenario, session) shouldBe published
    PrimaryGenerationHistorySpecificationFixture.intent(fixture, target) shouldBe target.intent
    PrimaryGenerationHistorySpecificationFixture.primary(fixture).map(saved => A.codec.encode(saved.fact)) shouldBe
      Vector(saved.hcursor.get[Json]("primaryFact").toOption.get)
    _lines(root.resolve("generation-sbt-invocations.txt")) shouldBe calls
    _preserved_()
    Files.writeString(root.resolve("generation-history-continuation-result.json"), Json.obj(
      "sessionId" -> session.asJson, "authority" -> rationale.asJson, "target" -> target.asJson,
      "frontier" -> "independent-review-required".asJson, "directionOutput" -> authorized.invocation.stdout.toString.asJson,
      "reviewOutput" -> advanced.invocation.stdout.toString.asJson, "repeatedReviewOutput" -> recollectedoutput.invocation.stdout.toString.asJson,
      "originalFailure" -> failed.asJson, "oldUnknownIntent" -> target.intent.asJson, "published" -> published.asJson,
      "checks" -> review.checks.map(value => _json(value)).asJson, "calls" -> calls.asJson,
      "consumerRequirement" -> ("Consumer owners must collect the exact describeGoalPhaseSourceHistoryRecovery target, supply independent fresh Human " +
        "AuthorizeNewAttempt accepting old UNKNOWN or satisfy the existing terminal-observation/normalization authority contract, then call recoverGoalPhaseSourceHistory " +
        "and separate ordinary advanceGoalPhase. This test grants no consumer consent, migration, accepted-submit replay, Start, count reset or successful-effect replay.").asJson)
      .noSpaces, StandardOpenOption.CREATE_NEW)
  }

  "E15 paired installed primary generation recovery" should afterWord(
    "in spec:goal-phase-prerequisite-publication-recovery, example:E15, rules:RGEN01, phase:6.11") {
    "continue the same tiny failed cozyGenerate Session through corrected INPUT publication and real checks" in {
      Given("explicit normal installed command and caller-owned locator selections")
      val command = _required("smworkflow.spec.installedCommand")
      Files.isExecutable(command) shouldBe true
      sys.props.get("smworkflow.spec.installations") shouldBe None
      val mode = sys.props.getOrElse("smworkflow.spec.generationMode", fail("Select an explicit finite generation recovery mode"))
      val locator = _required(if (Set("capture-history-old", "continue-history-new")(mode))
        "smworkflow.spec.generationHistoryLocator" else "smworkflow.spec.generationLocator")
      When("the caller selects a historical continuation, new current qualification or saved successful result collection")
      mode match {
        case "capture-old" => _capture(locator)
        case "continue-new" => _continue(locator)
        case "resume-current-generation" => _resume_current_generation(locator)
        case "qualify-current" =>
          _capture(locator, false)
          _continue(locator, true)
        case "collect-review" => _collect_review(locator)
        case "capture-history-old" => _capture_history(locator)
        case "continue-history-new" => _continue_history(locator)
        case "capture-structural-old" => _capture(locator, structural = true)
        case "continue-structural-new" => _continue(locator, structural = true)
        case "collect-structural-closure" => _collect_structural_closure(locator)
        case _ => fail("Only capture-old, continue-new, resume-current-generation, qualify-current, collect-review, capture-history-old, continue-history-new, capture-structural-old, continue-structural-new and collect-structural-closure are supported")
      }
      Then("the selected mode retains actual outputs; only structural continuation qualifies controlled fixture Step closure, without consumer or Phase completion claims")
      Files.isRegularFile(locator) shouldBe true
    }
  }
}
