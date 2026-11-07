package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import io.circe.parser.parse
import java.nio.file.{Files, Path, StandardOpenOption}
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.protocol.Request
import org.goldenport.record.RecordPresentable
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.{GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks.*
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import GoalPhaseDependencyValidation as D
import GoalPhaseDependencyValidation.given

/** Ordinary CLI specifications. All semantic submissions and Human evidence are
  * SPECIFICATION-OWNED; no production adoption, installed qualification or acceptance is claimed.
  * @since   Oct. 24, 2026
  *  version Oct. 28, 2026
  * @version Nov.  8, 2026
  * @author  ASAMI, Tomoharu
  */
@org.scalatest.DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.DependencyValidationTestDeclaration])
final class NativeGoalDependencyValidationCliSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture with NativeGoalSourceHistoryCliFixture {
  import NativeCallerExecutionFixture.json

  override protected def process_plan(root: Path, scenario: String,
    goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val config = root.resolve(".textus/config.json")
    val configured = parse(Files.readString(config)).left.map(_.message).flatMap(_.hcursor
      .get[String]("textus.subsystem.process-execution.plan").left.map(_.message))
      .flatMap(value => parse(value).left.map(_.message))
    configured.fold(reason => fail("Cannot reopen the existing process declaration: " + reason), identity)
  }

  private final case class Selected(scenario: F.Scenario, task: Path, original: V.GoalPhaseAdvanceResult,
    resumed: V.GoalPhaseAdvanceResult, selection: D.Selection)
  private def _save(scenario: F.Scenario, name: String, value: String): String = {
    val path = scenario.root.resolve(name + ".json")
    Files.writeString(path, value, StandardOpenOption.CREATE_NEW)
    path.toUri.toASCIIString
  }
  private def _executions(root: Path): Vector[String] = {
    val path = root.resolve("sbt-executions.txt")
    if (Files.exists(path)) Files.readString(path).linesIterator.toVector else Vector.empty
  }
  private def _submit(scenario: F.Scenario, request: V.GoalPhaseAdvanceResult, result: Json,
    reference: V.WorkflowArtifactReference): F.Result = scenario.call("command", request.submitOperation.get,
    Vector("--sessionId", request.submitSessionId.get, "--result", result.noSpaces,
      "--resultReference", json(reference.toRecord()).noSpaces))
  private def _refused(result: F.Result, diagnostic: String): Unit = {
    withClue(result.invocation.logs) {
      Files.readString(result.invocation.stdout).trim shouldBe empty
      val error = Files.readString(result.invocation.stderr)
      error should include("status=409")
      error should include("state.conflict-message:")
      error should include(diagnostic)
    }
  }
  private def _successful_receipt(receipt: Json): Unit = {
    receipt.hcursor.downField("sbt").get[String]("outcome").toOption shouldBe Some("succeeded")
    val termination = receipt.hcursor.downField("process").downField("termination")
    termination.get[String]("kind").toOption shouldBe Some("exited")
    termination.get[Int]("exitCode").toOption shouldBe Some(0)
  }
  private def _native_fixture[A](scenario: F.Scenario)(body: org.goldenport.cncf.action.ActionCall.Core => Consequence[A]): A = {
    val config = scenario.root.resolve(".textus/config.json")
    val assembly = scenario.root.resolve(".textus/assembly.yaml")
    val configbytes = Files.readAllBytes(config)
    val assemblybytes = Files.readAllBytes(assembly)
    val parameters = parse(Files.readString(config)).toOption.get.asObject.get.toVector.map { (key, value) =>
      key -> value.asString.getOrElse(value.noSpaces)
    }.toMap - "textus.component.file"
    val host = SplitPhaseExplicitPartitionFixture.take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(
      parse(parameters("sm-workflow.runtime.definition")).toOption.get))
    var result = Option.empty[A]
    try _with_runtime_(scenario.root, "Goal", Some(host), hostParameters = parameters, componentInstance = "default") {
      (_, _, _, prepare) => prepare(call => body(call.core).map(value => { result = Some(value); () }))
    } finally {
      Files.write(config, configbytes)
      Files.write(assembly, assemblybytes)
    }
    result.get
  }
  /** Extend only this fresh specification's business Plan and ordinary host
    * declarations before Start; no native Session or history exists yet.
    */
  private def _legacy_successor_plan(scenario: F.Scenario): Unit = {
    import io.circe.generic.auto.*
    import org.goldenport.cncf.processexecution.GitOperationKind
    val plan = scenario.plan.copy(steps = scenario.plan.steps :+ scenario.plan.steps.last.copy(
      identity = "step-3", purpose = "Integrate a fresh native-prefixed dependency successor"))
    val host = scenario.host.copy(goal = scenario.host.goal.copy(closure = scenario.host.goal.closure.copy(
      programs = scenario.host.goal.closure.programs :+ GoalPhaseNativeClosure.StepProgram("step-3", 3))))
    Files.writeString(scenario.root.resolve("goal-plan.json"), plan.asJson.noSpaces)
    val target = org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms.Target(Path.of(host.target.root),
      host.target.localBranch, host.target.remote, host.target.remoteBranch, host.target.endpoint)
    val git = TypedGitHostPlanFixture.plan(target, kinds = TypedGitHostPlanFixture.observations ++
      Vector(GitOperationKind.IndexTree, GitOperationKind.PendingPaths, GitOperationKind.PendingDiff, GitOperationKind.CommitMetadata),
      goalPaths = host.goal.candidatePaths, goalMessages = Vector("Complete selected continuation Step 1",
        "Complete selected continuation Step 2", "Complete selected continuation Step 3"),
      goalSelections = Vector(host.goal.candidatePaths, Vector("docs/phase.md"), Vector("docs/phase.md")))
    val primary = TypedSbtHostPlanFixture.plan(scenario.root, TypedSbtHostPlanFixture.normalExecutable, Vector(1), Some(git))
    val processes = TypedSbtHostPlanFixture.dependencyPlan(primary, scenario.root.resolve("dependency"),
      TypedSbtHostPlanFixture.normalExecutable, host.goal.dependencyValidation.get)
    val path = scenario.root.resolve(".textus/config.json")
    val config = parse(Files.readString(path)).toOption.get
    Files.writeString(path, config.mapObject(_.add("sm-workflow.runtime.definition",
      ThreeWorkflowRuntimeParameters.encode(host).noSpaces.asJson)
      .add("textus.subsystem.process-execution.plan", processes.noSpaces.asJson)).noSpaces)
    val prepared = SplitPhaseExplicitPartitionFixture.take(
      org.simplemodeling.textus.smworkflow.preparation.NativeGoalStartInput.prepareC(Map(
        "input" -> scenario.root.resolve("goal-input.json").toUri.toASCIIString,
        "plan" -> scenario.root.resolve("goal-plan.json").toUri.toASCIIString,
        "selection-id" -> "native-goal-selection", "participant" -> "participant", "origin" -> "ExplicitHuman",
        "idempotency-key" -> "native-specification-input", "selection-collection" -> "entity:smworkflow.cli.goal_selection",
        "plan-collection" -> "entity:smworkflow.cli.goal_plans"), reference =>
        Consequence(Files.readString(Path.of(java.net.URI.create(reference))))))
    Files.writeString(scenario.root.resolve("selected-input.json"), SplitPhaseExplicitPartitionFixture.take(prepared.toJsonStringC))
  }
  private def _selected(name: String, denial: String = "none",
    bindingtransform: D.Binding => D.Binding = value => value, legacysuccessor: Boolean = false,
    assessmentpolicy: Option[GoalPhaseStepAssessment.Policy] = None, sourcerevisionfixture: Boolean = false,
    dependencychecks: Option[Vector[GoalPhaseNativeValidation.Check]] = None,
    beforestart: F.Scenario => Unit = _ => ()): Selected = {
    val scenario = F.freshDependency(name, denial, bindingtransform, dependencyChecks = dependencychecks)
    // Both ordinary launchers select this fresh host's existing assembly. The
    // installed entry changes cwd only; host configuration owns its descriptor.
    val hostconfig = scenario.root.resolve(".textus/config.json")
    val hostparameters = parse(Files.readString(hostconfig)).toOption.get
    val selectedparameters = hostparameters.mapObject(_.add("textus.assembly.descriptor",
      scenario.root.resolve(".textus/assembly.yaml").toString.asJson))
    val componentparameters = if (sys.props.contains("smworkflow.spec.installedCommand"))
      sys.props.get("smworkflow.spec.componentFile").fold(selectedparameters)(component =>
        selectedparameters.mapObject(_.add("textus.component.file", component.asJson)))
    else selectedparameters
    Files.writeString(hostconfig, componentparameters.noSpaces)
    if (sourcerevisionfixture) {
      // Arrange this isolated primary's genuine source-project consumption before
      // Start. The later host route supplies the new root; no Session is edited.
      val repository = Path.of(scenario.host.target.root)
      Files.writeString(repository.resolve("build.sbt"), """
        |lazy val sourceDependencies = sys.env.get("TEXTUS_CNCF_SOURCE_ROOT").toSeq.map(path =>
        |  ClasspathDependency(ProjectRef(file(path), "root"), None))
        |lazy val root = project.in(file(".")).dependsOn(sourceDependencies: _*)
        |""".stripMargin, StandardOpenOption.APPEND)
      val check = repository.resolve("src/test/scala/ProductCheck.scala")
      val source = Files.readString(check).replace("    println(\"PRODUCT_CHECK_PASSED\")", """
        |    sys.env.get("TEXTUS_CNCF_SOURCE_ROOT").foreach { selected =>
        |      val marker = Class.forName("SourceRevisionMarker$")
        |      val instance = marker.getField("MODULE$").get(null)
        |      assert(marker.getMethod("source").invoke(instance) == selected, "wrong source project loaded")
        |    }
        |    println("PRODUCT_CHECK_PASSED")
        |""".stripMargin)
      Files.writeString(check, source)
      SplitPhaseExplicitPartitionFixture.take(_run_setup_command_(Vector("-C", repository.toString,
        "-c", "core.hooksPath=/dev/null", "add", "--", "build.sbt", "src/test/scala/ProductCheck.scala")))
      SplitPhaseExplicitPartitionFixture.take(_run_setup_command_(Vector("-C", repository.toString,
        "-c", "core.hooksPath=/dev/null", "-c", "user.name=Source Consumption Specification",
        "-c", "user.email=source@example.invalid", "commit", "--no-verify", "--no-gpg-sign",
        "-m", "Specification-owned primary source consumption")))
    }
    if (legacysuccessor) _legacy_successor_plan(scenario)
    assessmentpolicy.foreach { policy =>
      val planpath = scenario.root.resolve("goal-plan.json")
      val original = parse(Files.readString(planpath)).toOption.get.as[GoalPhaseOrderedProgression.Plan].toOption.get
      val plan = original.copy(steps = original.steps.updated(0, original.steps.head.copy(actionPolicy = Some(policy))))
      Files.writeString(planpath, plan.asJson.noSpaces)
      val prepared = SplitPhaseExplicitPartitionFixture.take(
        org.simplemodeling.textus.smworkflow.preparation.NativeGoalStartInput.prepareC(Map(
          "input" -> scenario.root.resolve("goal-input.json").toUri.toASCIIString,
          "plan" -> planpath.toUri.toASCIIString, "selection-id" -> "native-goal-selection",
          "participant" -> "participant", "origin" -> "ExplicitHuman", "idempotency-key" -> "native-specification-input",
          "selection-collection" -> "entity:smworkflow.cli.goal_selection", "plan-collection" -> "entity:smworkflow.cli.goal_plans"),
          reference => Consequence(Files.readString(Path.of(java.net.URI.create(reference))))))
      Files.writeString(scenario.root.resolve("selected-input.json"), SplitPhaseExplicitPartitionFixture.take(prepared.toJsonStringC))
    }
    beforestart(scenario)
    val task = scenario.ownTask("dependency-validation")
    val started = scenario.start().business[V.GoalPhaseStartResult]
    scenario.retain(task, started)
    val original = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
    val configured = parse(Files.readString(hostconfig)).toOption.get
    val actualhost = SplitPhaseExplicitPartitionFixture.take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(
      parse(configured.hcursor.get[String]("sm-workflow.runtime.definition").toOption.get).toOption.get))
    val binding = actualhost.goal.dependencyValidation.get
    val proposal = GoalPhaseDependencyScope.Proposal("specification-dependency-scope", "1", "IMPLEMENTATION",
      binding.requester, binding.dependency, ContextReference("specification-linked-api", "1"),
      "SPECIFICATION-OWNED linked dependency change", binding.paths, Vector("Validate the exact dependency candidate"),
      Vector(ContextReference(binding.step, "1")), Vector.empty)
    val result = Json.obj("outcome" -> Json.fromString("decision-needed"),
      "summary" -> Json.fromString("SPECIFICATION-OWNED dependency direction request"), "findings" -> Json.arr(),
      "dependencyProposal" -> proposal.asJson)
    _submit(scenario, original, result, V.WorkflowArtifactReference("specification-dependency-proposal", "1"))
      .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
    val output = scenario.advance(task)
    val question = output.business[V.GoalPhaseAdvanceResult]
    question.kind shouldBe "Human"
    val answer = V.GoalPhaseOrderedDecisionAnswer("ApproveDependencyScope", "SPECIFICATION-OWNED exact linked unit")
    val reference = V.WorkflowArtifactReference("specification-dependency-approval", "1")
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("specification-owned-human-answer"), None, None)
    val input = V.WorkflowEvidencePreparationInput("GoalPhase", "human",
      _save(scenario, "actual-dependency-question", Files.readString(output.invocation.stdout)),
      _save(scenario, "dependency-answer", json(answer.toRecord()).noSpaces),
      _save(scenario, "dependency-reference", json(reference.toRecord()).noSpaces),
      _save(scenario, "dependency-execution", json(execution.toRecord()).noSpaces),
      "normal-development-host", Instant.now().minusSeconds(60).toString,
      "entity:smworkflow.cli.host_evidence", Some("participant"))
    val prepared = scenario.prepareHostInput("prepareEvidence", input.toRecord())
    prepared.exitCode shouldBe 0
    scenario.selectPreparedImport(prepared)
    scenario.call("command", question.submitOperation.get,
      Vector("--sessionId", question.submitSessionId.get, "--answer", json(answer.toRecord()).noSpaces,
        "--resultReference", json(reference.toRecord()).noSpaces,
        "--executionEvidence", json(execution.toRecord()).noSpaces))
      .business[V.GoalPhaseOrderedDecisionSubmissionResult].accepted shouldBe true
    val resumed = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
    resumed.sessionId shouldBe original.sessionId
    resumed.work.get.originalBase shouldBe original.work.get.originalBase
    resumed.work.get.selectedPlan shouldBe original.work.get.selectedPlan
    resumed.repairProgress shouldBe original.repairProgress
    _executions(scenario.root) shouldBe empty
    _executions(scenario.root.resolve("dependency")) shouldBe empty
    Selected(scenario, task, original, resumed,
      D.Selection(ContextReference(binding.identity, binding.revision.toString),
        Vector(D.ApprovalReference(ContextReference(proposal.identity, proposal.revision),
          ContextReference(reference.identity, reference.revision)))))
  }
  private def _complete(selected: Selected): Unit = {
    val scenario = selected.scenario
    Files.writeString(Path.of(scenario.host.target.root).resolve("value.txt"), "expected\n")
    Files.writeString(scenario.root.resolve("dependency/repository/value.txt"), "expected\n")
    val result = Json.obj("outcome" -> Json.fromString("completed"),
      "summary" -> Json.fromString("SPECIFICATION-OWNED linked source completion"), "findings" -> Json.arr(),
      "dependencyValidation" -> selected.selection.asJson)
    _submit(scenario, selected.resumed, result, V.WorkflowArtifactReference("specification-linked-completion", "1"))
      .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
  }

  private def _interrupt(selected: Selected, point: String): Unit = {
    val scenario = selected.scenario
    val config = scenario.root.resolve(".textus/config.json")
    val assembly = scenario.root.resolve(".textus/assembly.yaml")
    val configbytes = Files.readAllBytes(config)
    val assemblybytes = Files.readAllBytes(assembly)
    val parameters = parse(Files.readString(config)).toOption.get.asObject.get.toVector.map { (key, value) =>
      key -> value.asString.getOrElse(value.noSpaces)
    }.toMap
    val installedbootstrap = Option.when(sys.props.contains("smworkflow.spec.installedCommand")) {
      InstalledBootstrapSelection(Path.of(sys.props.get("smworkflow.spec.componentFile")
        .getOrElse(fail("Installed interruption requires the normally published component CAR"))))
    }
    var observed = Vector.empty[String]
    val acknowledgement: String => Consequence[Unit] = value => {
      observed :+= value
      if (value == point) Consequence.stateConflict("SPECIFICATION-OWNED interruption at " + point) else Consequence.unit
    }
    val actualHost = SplitPhaseExplicitPartitionFixture.take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(
      parse(parameters("sm-workflow.runtime.definition")).toOption.get))
    try _with_runtime_(scenario.root, "Goal", Some(actualHost), hostParameters = parameters,
      nativeAcknowledgement = None, goalAcknowledgement = Some(acknowledgement), componentInstance = "default",
      installedBootstrap = installedbootstrap) { (owner, component, actor, _) =>
      owner.executeOperationResponseInContext(Request.of(component = component.name, service = "GoalPhaseService",
        operation = "advanceGoalPhase", properties = NativeWorkflowOperationInputFixture.advance(selected.original.sessionId)), actor)
        .isSuccess shouldBe false
      observed should contain(point)
    } finally {
      Files.write(config, configbytes)
      Files.write(assembly, assemblybytes)
    }
  }
  private def _provision(selected: Selected, described: F.Result, purpose: String, answer: RecordPresentable,
    reference: V.WorkflowArtifactReference): Unit = {
    val scenario = selected.scenario
    val directory = Files.createTempDirectory(scenario.root, "dependency-recovery-human-")
    def _file_(name: String, value: RecordPresentable): String = {
      val path = directory.resolve(name + ".json")
      Files.writeString(path, json(value.toRecord()).noSpaces)
      path.toUri.toASCIIString
    }
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
    val input = V.WorkflowEvidencePreparationInput("GoalPhase", purpose, described.invocation.stdout.toUri.toASCIIString,
      _file_("answer", answer), _file_("reference", reference), _file_("execution", execution),
      "normal-development-host", Instant.now().minusSeconds(60).toString, "entity:smworkflow.cli.host_evidence", Some("participant"))
    val prepared = scenario.prepareHostInput("prepareEvidence", input.toRecord())
    prepared.exitCode shouldBe 0
    scenario.selectPreparedImport(prepared)
  }
  private def _describe_recovery(selected: Selected): F.Result = selected.scenario.call("command",
    GoalPhaseCandidateRecovery.describeOperation, Vector("--sessionId", selected.original.sessionId))
  private def _recover(selected: Selected, described: F.Result, choice: String): F.Result = {
    val description = described.business[V.GoalPhaseCandidateRecoveryDescriptionResult]
    val reference = V.WorkflowArtifactReference("specification-dependency-recovery-" + choice, "1")
    val answer = V.GoalPhaseCandidateRecoveryAnswer(choice, "SPECIFICATION-OWNED explicit direction; all old effects remain unknown")
    _provision(selected, described, "candidate-recovery", answer, reference)
    val input = V.GoalPhaseCandidateRecoveryInput(description.sessionId, description.target, choice, answer.rationale, reference)
    selected.scenario.call("command", GoalPhaseCandidateRecovery.recoveryOperation,
      Vector("--input", json(input.toRecord()).noSpaces))
  }
  private def _describe_continuation(selected: Selected): F.Result = selected.scenario.call("command",
    GoalPhaseCandidateObservationContinuation.describeOperation, Vector("--sessionId", selected.original.sessionId))
  private def _continue_observation(selected: Selected, described: F.Result): F.Result = {
    val description = described.business[V.GoalPhaseCandidateObservationContinuationDescriptionResult]
    val reference = V.WorkflowArtifactReference("specification-fresh-dependency-observation", "1")
    val answer = V.GoalPhaseCandidateObservationContinuationAnswer("AuthorizeNewObservation",
      "SPECIFICATION-OWNED distinct observation on this snapshot; old termination remains unconfirmed")
    _provision(selected, described, "candidate-observation-continuation", answer, reference)
    val input = V.GoalPhaseCandidateObservationContinuationInput(description.sessionId, description.target,
      answer.choice, answer.rationale, reference)
    selected.scenario.call("command", GoalPhaseCandidateObservationContinuation.continuationOperation,
      Vector("--input", json(input.toRecord()).noSpaces))
  }

  private def _review(selected: Selected, output: F.Result, findings: Vector[String] = Vector.empty,
    artifact: String = "linked", issuereport: Option[V.GoalPhaseIssueReport] = None,
    acceptancereport: Option[V.GoalPhaseAcceptanceReport] = None): F.Result = {
    val scenario = selected.scenario
    val request = output.business[V.GoalPhaseAdvanceResult]
    Set("ReviewStep", "ReviewPhase") should contain(request.work.get.kind)
    val result = Json.obj("outcome" -> Json.fromString(if (findings.isEmpty) "accept" else "repair"),
      "summary" -> Json.fromString("SPECIFICATION-OWNED review of both actual candidates"),
      "findings" -> Json.fromValues(findings.map(Json.fromString))).mapObject(fields =>
        val issues = issuereport.fold(fields)(report => fields.add("issueReport", json(report.toRecord())))
        acceptancereport.fold(issues)(report => issues.add("acceptance", json(report.toRecord()))))
    val reference = V.WorkflowArtifactReference("specification-" + artifact + "-review", "1")
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("specification-independent-reviewer"), None, None)
    val input = V.WorkflowEvidencePreparationInput("GoalPhase", "review",
      _save(scenario, "actual-" + artifact + "-review", Files.readString(output.invocation.stdout)),
      _save(scenario, artifact + "-review-result", result.noSpaces),
      _save(scenario, artifact + "-review-reference", json(reference.toRecord()).noSpaces),
      _save(scenario, artifact + "-review-execution", json(execution.toRecord()).noSpaces),
      "normal-development-host", Instant.now().minusSeconds(60).toString,
      "entity:smworkflow.cli.host_evidence", None)
    val prepared = scenario.prepareHostInput("prepareEvidence", input.toRecord())
    prepared.exitCode shouldBe 0
    scenario.selectPreparedImport(prepared)
    scenario.call("command", request.submitOperation.get,
      Vector("--sessionId", request.submitSessionId.get, "--result", result.noSpaces,
        "--resultReference", json(reference.toRecord()).noSpaces,
        "--executionEvidence", json(execution.toRecord()).noSpaces))
  }

  private def _review_for_repair(selected: Selected): F.Result = {
    // A tracked mode correction changes the actual dependency tree while keeping
    // ProductCheck's exact expected input and the immutable Compile/Test selection.
    selected.scenario.root.resolve("dependency/repository/value.txt").toFile.setExecutable(true, false) shouldBe true
    _complete(selected)
    selected.scenario.advance(selected.task)
  }
  private def _capture(request: V.GoalPhaseAdvanceResult): D.Capture =
    GoalPhaseNativeMetadata.normalizePublicJson(json(request.work.get.review.get.dependencyValidations.head))
      .flatMap(_.hcursor.get[D.Capture]("capture").left.map(_.message))
      .fold(reason => fail("Cannot decode the public dependency capture: " + reason), identity)
  private def _complete_repair(selected: Selected, request: V.GoalPhaseAdvanceResult,
    selection: Option[D.Selection] = None): F.Result = {
    request.work.get.kind shouldBe "RepairStep"
    val fixed = request.work.get.issues.get.issues.filter(_.status != "RESOLVED").map(issue =>
      V.GoalPhaseIssueDisposition(issue.identity, "FIXED", "SPECIFICATION-OWNED removal of executable permission", None))
    val result = Json.obj("outcome" -> Json.fromString("completed"),
      "summary" -> Json.fromString("SPECIFICATION-OWNED unstaged dependency mode correction"), "findings" -> Json.arr(),
      "issueReport" -> json(V.GoalPhaseIssueReport(fixed).toRecord()),
      "dependencyValidation" -> selection.getOrElse(selected.selection).asJson)
    _submit(selected.scenario, request, result, V.WorkflowArtifactReference("specification-linked-repair", "1"))
  }

  /** Update only ordinary isolated host declarations, never native Session storage. */
  private def _select_binding(selected: Selected, binding: D.Binding): Unit = {
    val scenario = selected.scenario
    val file = scenario.root.resolve(".textus/config.json")
    val config = parse(Files.readString(file)).toOption.get
    val processes = parse(config.hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
    def array(value: Json, key: String): Vector[Json] = value.hcursor.get[Vector[Json]](key).toOption.get
    val workspace = binding.dependency.workspace
    val primary = Vector("gitPrograms" -> "target", "sbtPrograms" -> "target", "workspaces" -> "identity",
      "workspaceGrants" -> "workspaceId").foldLeft(processes) { case (previous, (key, field)) =>
      previous.mapObject(_.add(key, Json.fromValues(array(previous, key).filterNot(_.hcursor.get[String](field).toOption.contains(workspace)))))
    }
    val combined = TypedSbtHostPlanFixture.dependencyPlan(primary, scenario.root.resolve("dependency"),
      TypedSbtHostPlanFixture.normalExecutable, binding)
    val current = SplitPhaseExplicitPartitionFixture.take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(
      parse(config.hcursor.get[String]("sm-workflow.runtime.definition").toOption.get).toOption.get))
    val host = current.copy(goal = current.goal.copy(dependencyValidation = Some(binding)))
    Files.writeString(file, config.mapObject(_.add("sm-workflow.runtime.definition",
      ThreeWorkflowRuntimeParameters.encode(host).noSpaces.asJson)
      .add("textus.subsystem.process-execution.plan", combined.noSpaces.asJson)).noSpaces)
  }

  private def _close_step(selected: Selected, review: F.Result, artifact: String,
    issuereport: Option[V.GoalPhaseIssueReport] = None,
    acceptancereport: Option[V.GoalPhaseAcceptanceReport] = None): F.Result = {
    _review(selected, review, artifact = artifact, issuereport = issuereport, acceptancereport = acceptancereport)
      .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
    val closing = selected.scenario.advance(selected.task)
    closing.business[V.GoalPhaseAdvanceResult].authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
    val reference = V.WorkflowArtifactReference("specification-" + artifact + "-closure", "1")
    val input = V.WorkflowAuthorityPreparationInput(closing.invocation.stdout.toUri.toASCIIString,
      _save(selected.scenario, artifact + "-closure-reference", json(reference.toRecord()).noSpaces),
      "participant", "normal-development-host", Instant.now().minusSeconds(60).toString,
      "entity:smworkflow.cli.host_evidence")
    val prepared = selected.scenario.prepareHostInput("prepareAuthority", input.toRecord())
    prepared.exitCode shouldBe 0
    selected.scenario.selectPreparedImport(prepared)
    selected.scenario.advance(selected.task)
  }

  private final case class PrefixStep(selected: Selected, first: D.Capture, firstReview: F.Result,
    current: V.GoalPhaseAdvanceResult, binding: D.Binding)
  private def _accepted_prefix(name: String, overlap: Boolean, legacySuccessor: Boolean = false,
    sourceRevisionFixture: Boolean = false, beforestart: F.Scenario => Unit = _ => ()): PrefixStep = {
    val selected = _selected(name, bindingtransform = _.copy(paths = Vector("value.txt", "inherited.txt")),
      legacysuccessor = legacySuccessor, sourcerevisionfixture = sourceRevisionFixture, beforestart = beforestart)
    val binding = selected.scenario.host.goal.dependencyValidation.get
    _select_binding(selected, binding)
    Files.writeString(Path.of(binding.root).resolve("inherited.txt"), "accepted first-Step input\n")
    _complete(selected)
    val review = selected.scenario.advance(selected.task)
    val capture = _capture(review.business[V.GoalPhaseAdvanceResult])
    capture.admission.acceptedPrefix shouldBe None
    capture.changedPaths.toSet shouldBe Set("value.txt", "inherited.txt")
    val next = _close_step(selected, review, "prefix-first").business[V.GoalPhaseAdvanceResult]
    next.work.get.stepIdentity shouldBe Some("step-2")
    next.work.get.completed.map(_.step) shouldBe Vector("step-1")
    next.work.get.originalBase shouldBe selected.original.work.get.originalBase
    val current = binding.copy(identity = "specification-cncf-second-validation", step = "step-2",
      paths = (Option.when(overlap)("value.txt").toVector :+ "current.txt"),
      checks = binding.checks.zipWithIndex.map((check, index) => check.copy(identity = "second-" + check.identity, ordinal = index + 6)))
    PrefixStep(selected, capture, review, next, current)
  }

  private def _second_proposal(binding: D.Binding): GoalPhaseDependencyScope.Proposal =
    GoalPhaseDependencyScope.Proposal("specification-scope-" + binding.step, "1", "IMPLEMENTATION",
      binding.requester, binding.dependency, ContextReference("specification-linked-api", "1"),
      "SPECIFICATION-OWNED current Step delta", binding.paths, Vector("Integrate with the genuinely accepted predecessor"),
      Vector(ContextReference(binding.step, "1")), Vector.empty)
  private def _adopt_prefix(value: PrefixStep): Selected = {
    val selected = value.selected
    val scenario = selected.scenario
    _select_binding(selected, value.binding)
    val proposal = _second_proposal(value.binding)
    _submit(scenario, value.current, Json.obj("outcome" -> "decision-needed".asJson,
      "summary" -> "SPECIFICATION-OWNED new same-dependency scope".asJson, "findings" -> Json.arr(),
      "dependencyProposal" -> proposal.asJson), V.WorkflowArtifactReference("specification-proposal-" + value.binding.step, "1"))
      .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
    val output = scenario.advance(selected.task)
    val question = output.business[V.GoalPhaseAdvanceResult]
    question.kind shouldBe "Human"
    val answer = V.GoalPhaseOrderedDecisionAnswer("ApproveDependencyScope", "SPECIFICATION-OWNED current Step only")
    val reference = V.WorkflowArtifactReference("specification-approval-" + value.binding.step, "1")
    _provision(selected, output, "human", answer, reference)
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
    scenario.call("command", question.submitOperation.get, Vector("--sessionId", question.submitSessionId.get,
      "--answer", json(answer.toRecord()).noSpaces, "--resultReference", json(reference.toRecord()).noSpaces,
      "--executionEvidence", json(execution.toRecord()).noSpaces))
      .business[V.GoalPhaseOrderedDecisionSubmissionResult].accepted shouldBe true
    val resumed = scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
    resumed.work.get.stepIdentity shouldBe Some(value.binding.step)
    selected.copy(resumed = resumed, selection = D.Selection(ContextReference(value.binding.identity, "1"),
      Vector(D.ApprovalReference(ContextReference(proposal.identity, "1"), ContextReference(reference.identity, "1")))))
  }

  private def _complete_prefix(selected: Selected, overlap: Boolean, failCheck: Boolean = false): Unit = {
    Files.writeString(Path.of(selected.scenario.host.target.root).resolve("docs/phase.md"), "Accepted first Step and current integration\n")
    Files.writeString(selected.scenario.root.resolve("dependency/repository/current.txt"), "current admitted delta\n")
    if (overlap) {
      selected.scenario.root.resolve("dependency/repository/value.txt").toFile.setExecutable(true, false) shouldBe true
      if (failCheck) Files.writeString(selected.scenario.root.resolve("dependency/repository/value.txt"), "incorrect\n")
    }
    _submit(selected.scenario, selected.resumed, Json.obj("outcome" -> "completed".asJson,
      "summary" -> "SPECIFICATION-OWNED prefixed implementation".asJson, "findings" -> Json.arr(),
      "dependencyValidation" -> selected.selection.asJson), V.WorkflowArtifactReference("specification-second-completion", "1"))
      .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
  }

  private def _latest_capture(request: V.GoalPhaseAdvanceResult): D.Capture =
    GoalPhaseNativeMetadata.normalizePublicJson(json(request.work.get.review.get.dependencyValidations.last))
      .flatMap(_.hcursor.get[D.Capture]("capture").left.map(_.message)).fold(fail(_), identity)

  "Release dependency succession after actual Step closure" should {
    "retain failed-history terminal facts through new phase dependency review after three genuine source repairs" in {
      Given("a coherent disposable source with pre-adopted limit ten and a fixed Phase test requiring three actual documentation corrections")
      val frontier = ReleaseDependencyCliSpecification.setup("p69-release-dependency-failed-history", None,
        coherentSource = true, priorReleaseHistory = true, failedReleaseHistory = true)
      When("ordinary continuation authenticates failed and repair-required history before the new dependency purpose")
      ReleaseDependencyCliSpecification.resume(frontier, None, negativeChecks = true, stopAtDependencyReview = true)
      Then("the actual dependency ReviewPhase retains all three failed facts and the later passed fact without source-coherence acceptance")
      frontier.repair.repairProgress.get.limit shouldBe 10
      frontier.repair.repairProgress.get.ordinaryCount shouldBe 4
    }

    "reconstruct a passed pre-binding Release repair without replay before new phase dependency checks and closure" in {
      Given("a pre-adopted third repair allowance and a genuine earlier Release validation and independent repair review")
      val frontier = ReleaseDependencyCliSpecification.setup("p69-release-dependency-prior-history", None,
        priorReleaseHistory = true)
      When("current phase check drift is refused before ordinary resume with the exact restored declaration")
      ReleaseDependencyCliSpecification.resume(frontier, None, negativeChecks = true)
      Then("old candidate, passed checks and repair facts remain while new checks, review and normal closure complete")
      frontier.repair.repairProgress.get.ordinaryCount shouldBe 2
    }

    "capture a different phase-approved purpose while preserving the old candidate, publication and closed Step" in {
      Given("a genuinely closed dependency Step, actual Release repair and the original failed unstarted Phase frontier")
      val frontier = ReleaseDependencyCliSpecification.setup("p69-release-dependency-managed", None)
      When("ordinary Advance selects the explicit phase binding through an exact typed one-path Git target")
      ReleaseDependencyCliSpecification.resume(frontier, None, negativeChecks = true)
      Then("the same Session has completed actual new checks, independent specification review and ordinary closure")
      frontier.binding.step should startWith("phase:")
    }

    "resume retained successful staging without replaying it" in {
      Given("an isolated Release repair and an interruption after its actual selected stage result")
      val point = "goal-release-dependency-stage-recorded:release-help.txt"
      When("the next invocation reconstructs that genuine contiguous result before continuing observation")
      val result = ReleaseDependencyCliSpecification.interrupted("p69-release-dependency-known-stage", point)
      Then("the fixture has asserted exactly one selected stage result and a fresh integrated review")
      result.get.work.get.kind shouldBe "ReviewPhase"
    }

    "retain a result-less stage intent as UNKNOWN without another Git attempt" in {
      Given("an isolated Release repair and an interruption immediately after the committed stage intent")
      val point = "goal-release-dependency-stage-prepared:release-help.txt"
      When("repeated ordinary invocations encounter the same result-less native intent")
      val result = ReleaseDependencyCliSpecification.interrupted("p69-release-dependency-unknown-stage", point)
      Then("the fixture has asserted unchanged native facts, retained publication and an explicit UNKNOWN wait")
      result.get.kind shouldBe "Wait"
      result.get.message should include("UNKNOWN")
    }

    "refuse automatic succession once an ordinary Phase check intent exists" in {
      Given("the original primary candidate with an actual committed Phase check intent")
      val point = "goal-check-prepared:test"
      When("the explicit phase dependency binding is selected after that original check intent")
      val result = ReleaseDependencyCliSpecification.interrupted("p69-release-dependency-existing-check", point)
      Then("the original check remains unresolved and no Release dependency succession is manufactured")
      result shouldBe None
    }
  }

  /** Only this specification's host declaration changes. Accepted native history
    * and the old workspace/programs remain available to authenticate the prefix.
    */
  private def _source_host(selected: Selected, replacement: D.Binding,
    consumption: GoalPhaseDependencySourceRevision.Consumption): Unit =
    _configure_source_host(selected.scenario, replacement, consumption)

  private def _configure_source_host(scenario: F.Scenario, replacement: D.Binding,
    consumption: GoalPhaseDependencySourceRevision.Consumption,
    testclasses: Vector[String] = Vector.empty): Unit = {
    import org.goldenport.cncf.processexecution.GitOperationKind
    import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms.Target
    val path = scenario.root.resolve(".textus/config.json")
    val config = parse(Files.readString(path)).toOption.get
    val existing = parse(config.hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
    val host = SplitPhaseExplicitPartitionFixture.take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(
      parse(config.hcursor.get[String]("sm-workflow.runtime.definition").toOption.get).toOption.get))
    def _array_(value: Json, key: String): Vector[Json] = value.hcursor.get[Vector[Json]](key).toOption.get
    val workspace = replacement.dependency.workspace
    val dependencyPrograms = consumption.programs.filter(p => replacement.checks.exists(_.identity == p.check))
    val replacementWorkspaces = dependencyPrograms.map(_.workspace).toSet + workspace
    val retained = Vector("gitPrograms" -> "target", "sbtPrograms" -> "target", "workspaces" -> "identity",
      "workspaceGrants" -> "workspaceId").foldLeft(existing) { case (previous, (key, field)) =>
      previous.mapObject(_.add(key, Json.fromValues(_array_(previous, key)
        .filterNot(_.hcursor.get[String](field).toOption.exists(replacementWorkspaces.contains)))))
    }
    val builderbinding = replacement.copy(checks = replacement.checks.map(check =>
      if (check.kind == WorkflowSbtValidation.Kind.TestOnly) check.copy(kind = WorkflowSbtValidation.Kind.Test) else check))
    val combined = TypedSbtHostPlanFixture.dependencyPlan(retained, Path.of(replacement.root).getParent,
      TypedSbtHostPlanFixture.normalExecutable, builderbinding)
    val reads = (host.goal.dependencyValidation.toVector :+ replacement).flatMap { binding =>
      val declarations = TypedGitHostPlanFixture.plan(Target(Path.of(binding.root), binding.branch, "origin", "main",
        scenario.root.resolve("unused-source-upstream.git").toString),
        kinds = Vector(GitOperationKind.TreeChanges, GitOperationKind.BlobContent, GitOperationKind.CommitMetadata))
      def _rebind_(value: Json): Json = value.fold(Json.Null, Json.fromBoolean, Json.fromJsonNumber,
        text => Json.fromString(if (text == TypedGitHostPlanFixture.workspace) binding.dependency.workspace
          else if (text.startsWith("sm-workflow-git-")) binding.dependency.workspace + "-source-" +
            text.stripPrefix("sm-workflow-git-") else text),
        values => Json.fromValues(values.map(_rebind_)), fields => Json.fromJsonObject(fields.mapValues(_rebind_)))
      val programs = _array_(_rebind_(declarations), "gitPrograms")
      val selection = GoalPhaseDependencySourceRevision.committedInventorySelection
      val identity = binding.dependency.workspace + "-source-tree-entries"
      programs :+ programs.head.mapObject(_.add("operation", GitOperationKind.TreeEntries.wireName.asJson)
        .add("identity", identity.asJson).add("capability", identity.asJson)
        .add("selectionId", selection.asJson).add("selectedPaths", binding.paths.asJson))
    }
    val sourceGit = _array_(combined, "gitPrograms").map { program =>
      if (program.hcursor.get[String]("target").toOption.contains(workspace)) program.mapObject(fields =>
        fields.add("identity", (workspace + "-" + program.hcursor.get[String]("identity").toOption.get
          .stripPrefix("dependency-sm-workflow-git-")).asJson)
          .add("capability", (workspace + "-" + program.hcursor.get[String]("capability").toOption.get
            .stripPrefix("dependency-sm-workflow-git-")).asJson))
      else program
    }
    val primaryreads = TypedGitHostPlanFixture.plan(Target(Path.of(host.target.root), host.target.localBranch,
      host.target.remote, host.target.remoteBranch, host.target.endpoint),
      kinds = Vector(GitOperationKind.TreeChanges, GitOperationKind.BlobContent))
    val git = (sourceGit ++ reads ++ _array_(primaryreads, "gitPrograms")).groupBy(program => (program.hcursor.get[String]("target").toOption.get,
      program.hcursor.get[String]("operation").toOption.get, program.hcursor.get[String]("selectionId").toOption))
      .values.map(_.last).toVector.sortBy(_.hcursor.get[String]("identity").toOption.get)
    val sbt = _array_(combined, "sbtPrograms").map { original =>
      val routed = if (original.hcursor.get[String]("target").toOption.contains(workspace))
        dependencyPrograms.find(p => original.hcursor.get[String]("operation").toOption.contains(
          if (p.kind == "test-only") "test" else p.kind)) else None
      val program = routed.fold(original) { p =>
        val selected = original.mapObject(_.add("target", p.workspace.asJson).add("identity", p.program.asJson)
          .add("operation", p.kind.asJson))
        if (p.kind != "test-only") selected else {
          require(testclasses.nonEmpty, "Select the fixed small dependency TestOnly suite")
          selected.mapObject(_.add("testClasses", testclasses.asJson))
        }
      }
      if (consumption.programs.exists(p => program.hcursor.get[String]("target").toOption.contains(p.workspace) &&
          program.hcursor.get[String]("identity").toOption.contains(p.program))) {
        val environment = program.hcursor.get[Json]("environment").toOption.get.mapObject(_.add("TEXTUS_CNCF_SOURCE_ROOT",
          replacement.root.asJson))
        program.mapObject(_.add("environment", environment))
      } else program
    }
    val capabilities = (git ++ sbt).map(_.hcursor.get[String]("capability").toOption.get).distinct
    val aliases = dependencyPrograms.map(_.workspace).distinct.filterNot(_ == workspace)
    val processes = combined.mapObject(_.add("gitPrograms", Json.fromValues(git)).add("sbtPrograms", Json.fromValues(sbt))
      .add("workspaces", Json.fromValues(_array_(combined, "workspaces") ++ aliases.map(alias =>
        Json.obj("identity" -> alias.asJson, "root" -> replacement.root.asJson))))
      .add("workspaceGrants", Json.fromValues(_array_(combined, "workspaceGrants") ++ aliases.map(alias =>
        Json.obj("componentId" -> org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId.name.asJson,
          "workspaceId" -> alias.asJson, "access" -> "manage".asJson))))
      .add("grants", Json.arr(Json.obj(
      "componentId" -> org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId.name.asJson,
      "capabilities" -> capabilities.asJson))))
    val revisions = host.goal.dependencySourceRevisions.filterNot(_.identity == replacement.identity) :+ replacement
    val revised = host.copy(goal = host.goal.copy(dependencySourceRevisions = revisions,
      dependencySourceConsumptions = Vector(consumption)))
    SplitPhaseExplicitPartitionFixture.take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(
      ThreeWorkflowRuntimeParameters.encode(revised))) shouldBe revised
    Files.writeString(path, config.mapObject(_.add("sm-workflow.runtime.definition",
      ThreeWorkflowRuntimeParameters.encode(revised).noSpaces.asJson)
      .add("textus.subsystem.process-execution.plan", processes.noSpaces.asJson)).noSpaces)
  }

  /** A small ordinary build in each of two repositories. No producer sources or
    * native records are copied into the dependency fixture. */
  private def _initial_source_build(scenario: F.Scenario): Unit = {
    import io.circe.generic.auto.*
    import org.goldenport.cncf.processexecution.GitOperationKind
    import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms.Target
    def _setup_(repository: Path, arguments: String*): Unit = {
      SplitPhaseExplicitPartitionFixture.take(_run_setup_command_(Vector("-C", repository.toString,
        "-c", "core.hooksPath=/dev/null", "-c", "user.name=Initial Source Specification",
        "-c", "user.email=initial-source@example.invalid") ++ arguments))
    }
    val dependency = scenario.root.resolve("dependency/repository")
    Files.writeString(dependency.resolve("build.sbt"), """scalaVersion := "2.12.18"
      |lazy val root = project.in(file("."))
      |libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % Test
      |Compile / sourceGenerators += Def.task {
      |  val generated = (Compile / sourceManaged).value / "SourceRevisionMarker.scala"
      |  IO.write(generated, "object SourceRevisionMarker { def source: String = " +
      |    "\"" + baseDirectory.value.getCanonicalPath.replace("\\", "\\\\") + "\" }\n")
      |  IO.append(baseDirectory.value.getParentFile / "sbt-compile-executions.txt", "compile\n")
      |  Seq(generated)
      |}.taskValue
      |""".stripMargin)
    Files.createDirectories(dependency.resolve("src/test/scala/fixture"))
    Files.writeString(dependency.resolve("src/test/scala/fixture/SelectedDependencyCheck.scala"), """package fixture
      |class SelectedDependencyCheck extends org.scalatest.wordspec.AnyWordSpec
      |    with org.scalatest.matchers.should.Matchers with org.scalatest.GivenWhenThen {
      |  "The selected dependency source" should {
      |    "compile and consume its pending input" in {
      |      Given("the actual dependency workspace, generated source marker and pending input")
      |      val root = java.nio.file.Paths.get("").toAbsolutePath.normalize
      |      When("the fixed TestOnly suite checks the selected source and input")
      |      java.nio.file.Files.write(root.getParent.resolve("sbt-executions.txt"), "selected-test-only\n".getBytes("UTF-8"),
      |        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)
      |      Then("the pending input is expected and the compiled source marker identifies this workspace")
      |      java.nio.file.Files.readString(root.resolve("value.txt")) shouldBe "expected\n"
      |      val marker = Class.forName("SourceRevisionMarker$").getField("MODULE$").get(null)
      |      marker.getClass.getMethod("source").invoke(marker) shouldBe root.toString
      |    }
      |  }
      |}
      |""".stripMargin)
    _setup_(dependency, "add", "--", "build.sbt", "src/test/scala/fixture/SelectedDependencyCheck.scala")
    _setup_(dependency, "commit", "--no-verify", "--no-gpg-sign", "-m", "Small fixed dependency TestOnly fixture")
    val primary = Path.of(scenario.host.target.root)
    Files.createDirectories(primary.resolve(sourcePath).getParent)
    Files.writeString(primary.resolve(sourcePath), "/**\n * @since   Oct.  1, 2026\n * @version Oct.  2, 2026\n */\nobject HistoryExample { val value = 0 }\n")
    _setup_(primary, "add", "--", sourcePath)
    _setup_(primary, "commit", "--no-verify", "--no-gpg-sign", "-m", "Primary normalization fixture baseline")
    val file = scenario.root.resolve(".textus/config.json")
    val config = parse(Files.readString(file)).toOption.get
    val host = SplitPhaseExplicitPartitionFixture.take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(
      parse(config.hcursor.get[String]("sm-workflow.runtime.definition").toOption.get).toOption.get))
    val observed = _native_fixture(scenario)(RepositorySyncManagedGitObservation.observeInWorkspaceC(_,
      host.goal.dependencyValidation.get.dependency.workspace))
    val binding = host.goal.dependencyValidation.get.copy(base = GoalPhaseOrderedProgression.Snapshot(observed.head, observed.tree),
      checks = host.goal.dependencyValidation.get.checks.map(check =>
        if (check.kind == WorkflowSbtValidation.Kind.Test) check.copy(kind = WorkflowSbtValidation.Kind.TestOnly) else check))
    val paths = host.goal.candidatePaths :+ sourcePath
    val planpath = scenario.root.resolve("goal-plan.json")
    val oldplan = parse(Files.readString(planpath)).toOption.get.as[GoalPhaseOrderedProgression.Plan].toOption.get
    val plan = oldplan.copy(steps = oldplan.steps.updated(0, oldplan.steps.head.copy(ownedPaths = paths)))
    Files.writeString(planpath, plan.asJson.noSpaces)
    val revised = host.copy(goal = host.goal.copy(candidatePaths = paths, dependencyValidation = Some(binding)))
    val current = parse(config.hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
    def _array_(value: Json, name: String): Vector[Json] = value.hcursor.get[Vector[Json]](name).toOption.get
    val git = TypedGitHostPlanFixture.plan(Target(primary, host.target.localBranch, host.target.remote,
      host.target.remoteBranch, host.target.endpoint), kinds = TypedGitHostPlanFixture.observations ++ Vector(
      GitOperationKind.IndexTree, GitOperationKind.PendingPaths, GitOperationKind.PendingDiff,
      GitOperationKind.CommitMetadata, GitOperationKind.TreeChanges, GitOperationKind.BlobContent), goalPaths = paths,
      goalMessages = Vector("Complete selected continuation Step 1", "Complete selected continuation Step 2"),
      goalSelections = Vector(paths, Vector("docs/phase.md")))
    val programs = _array_(current, "gitPrograms").filterNot(_.hcursor.get[String]("target").toOption
      .contains(TypedGitHostPlanFixture.workspace)) ++ _array_(git, "gitPrograms")
    val process = current.mapObject(_.add("gitPrograms", programs.asJson).add("grants", Json.arr(Json.obj(
      "componentId" -> org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId.name.asJson,
      "capabilities" -> (programs ++ _array_(current, "sbtPrograms")).map(_.hcursor.get[String]("capability").toOption.get).distinct.asJson))))
    Files.writeString(file, config.mapObject(_.add("sm-workflow.runtime.definition",
      ThreeWorkflowRuntimeParameters.encode(revised).noSpaces.asJson).add("textus.subsystem.process-execution.plan",
      process.noSpaces.asJson)).noSpaces)
    configure(scenario, enabledSettings)
    val consumption = _initial_consumption(binding)
    _configure_source_host(scenario, binding.copy(revision = 2), consumption, Vector("fixture.SelectedDependencyCheck"))
    val prepared = SplitPhaseExplicitPartitionFixture.take(org.simplemodeling.textus.smworkflow.preparation.NativeGoalStartInput.prepareC(Map(
      "input" -> scenario.root.resolve("goal-input.json").toUri.toASCIIString, "plan" -> planpath.toUri.toASCIIString,
      "selection-id" -> "native-goal-selection", "participant" -> "participant", "origin" -> "ExplicitHuman",
      "idempotency-key" -> "native-specification-input", "selection-collection" -> "entity:smworkflow.cli.goal_selection",
      "plan-collection" -> "entity:smworkflow.cli.goal_plans"), reference => Consequence(Files.readString(Path.of(java.net.URI.create(reference))))))
    Files.writeString(scenario.root.resolve("selected-input.json"), SplitPhaseExplicitPartitionFixture.take(prepared.toJsonStringC))
  }

  private def _initial_consumption(binding: D.Binding): GoalPhaseDependencySourceRevision.Consumption = {
    import GoalPhaseDependencySourceRevision as R
    R.Consumption(binding.requester.workspace, binding.root, Vector.empty, binding.paths.map(R.ExpectedMode(_, "100644")),
      Vector(R.CheckProgram("test", "sm-workflow-repository-validation-1", "test", "sm-workflow-repository-validation-1")) ++
        binding.checks.map(check => R.CheckProgram(check.identity, binding.dependency.workspace, check.kind.wire,
          binding.dependency.workspace + "-" + (if (check.kind == WorkflowSbtValidation.Kind.TestOnly) "test" else check.kind.wire))))
  }

  /** One small ordinary current-source case. All Human/review evidence belongs
    * to this disposable specification, never to a consumer's authority. */
  def qualifyKeepCurrentRequirements(reporter: GivenWhenThen): Path = {
    import GoalPhaseDevelopmentReconstruction as C
    require(sys.props.contains("smworkflow.spec.installedCommand") && sys.props.contains("smworkflow.spec.componentFile"),
      "Select the actual normally published component and installed executable")
    reporter.Given("a tiny original Plan with accepted dependency direction and completed current implementation")
    val selected = _selected("common-keep-current-requirements")
    val scenario = selected.scenario
    _complete(selected)
    val primary = Path.of(scenario.host.target.root)
    Files.writeString(primary.resolve("docs/phase.md"), "# Proposed document projection\nClarify the current usage without changing actual source.\n")
    Files.writeString(primary.resolve("docs/keep-current-checklist.md"), "- [x] current: Retain original checks and requirements\n")
    val projected = scenario.plan.copy(revision = "keep-rejected-projection", acceptance = Some(
      GoalPhaseAcceptanceBoundary.Adoption("SPECIFICATION-OWNED proposed usage clarification", Vector.empty)), effectiveAcceptance = None)
    val input = V.GoalPhaseDevelopmentReconstructionInput(selected.original.sessionId, "docs/phase.md", "docs/keep-current-checklist.md",
      GoalPhaseNativeMetadata.record(GoalPhasePlanRevision.Proposal("keep-projection", "1", "Fixture document-only choice", projected).asJson),
      "SPECIFICATION-OWNED keep original requirements against actual current source")
    def _reconstruct_(): F.Result = scenario.call("command", C.operation, Vector("--inputResource",
      _save(scenario, "keep-input-" + scenario.invocations.size, json(input.toRecord()).noSpaces)))
    val originalrows = _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, selected.original.sessionId)).toMap
    reporter.When("the actual common operation presents the document projection to an independently evidenced fixture Human")
    val described = _reconstruct_()
    val question = described.business[V.GoalPhaseAdvanceResult]
    reporter.Then("the document-only ambiguity offers Keep without replacing original requirements or running checks")
    question.kind shouldBe "Human"
    question.human.get.choices shouldBe C.choices
    question.human.get.work.selectedPlan shouldBe selected.resumed.work.get.selectedPlan
    _executions(scenario.root) shouldBe empty
    _executions(scenario.root.resolve("dependency")) shouldBe empty
    val answer = V.GoalPhaseOrderedDecisionAnswer("KeepCurrentRequirements", "SPECIFICATION-OWNED actual original-requirements selection")
    val reference = V.WorkflowArtifactReference("keep-original-human", "1")
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
    reporter.Given("separately prepared independent host evidence for the actual Keep answer and public Human context")
    _provision(selected, described, "human", answer, reference)
    reporter.When("the true Human completion retains Keep and reconstruction recollects it before ordinary Advance")
    val submitted = scenario.call("command", question.submitOperation.get, Vector("--sessionId", question.submitSessionId.get,
      "--answer", json(answer.toRecord()).noSpaces, "--resultReference", json(reference.toRecord()).noSpaces,
      "--executionEvidence", json(execution.toRecord()).noSpaces)).business[V.GoalPhaseOrderedDecisionSubmissionResult]
    val answeredrows = _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, selected.original.sessionId)).toMap
    val recollected = _reconstruct_().business[V.GoalPhaseAdvanceResult]
    reporter.Then("the actual answer remains pending common selection without another Human completion or effect")
    submitted.accepted shouldBe true
    recollected.kind shouldBe "Wait"
    _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, selected.original.sessionId)).toMap shouldBe answeredrows
    reporter.When("a separate ordinary Advance applies common Keep and repeated reconstruction replays that native selection")
    val kept = scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
    val keptrows = _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, selected.original.sessionId)).toMap
    val repeated = _reconstruct_().business[V.GoalPhaseAdvanceResult]
    reporter.Then("same Session original base counters requirements and old records survive at the unstarted current capture")
    kept.kind shouldBe "Wait"
    kept.sessionId shouldBe selected.original.sessionId
    kept.repairProgress shouldBe selected.resumed.repairProgress
    repeated.kind shouldBe "Wait"
    repeated.sessionId shouldBe kept.sessionId
    _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, selected.original.sessionId)).toMap shouldBe keptrows
    originalrows.filterNot(_._1 == "instance").foreach { (key, bytes) => keptrows.get(key) shouldBe Some(bytes) }
    _executions(scenario.root) shouldBe empty
    _executions(scenario.root.resolve("dependency")) shouldBe empty
    reporter.When("ordinary Advance captures the actual current source and naturally runs only this tiny fixture's selected checks")
    val captured = scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
    val runs = _executions(scenario.root)
    val dependencyruns = _executions(scenario.root.resolve("dependency"))
    val pending = _reconstruct_().business[V.GoalPhaseAdvanceResult]
    val replayed = scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
    reporter.Then("current Review work replaces the former pause with original requirements and repeated collection duplicates no effect")
    captured.work.get.kind shouldBe "ReviewStep"
    captured.work.get.selectedPlan shouldBe selected.resumed.work.get.selectedPlan
    captured.work.get.originalBase shouldBe selected.resumed.work.get.originalBase
    captured.work.get.completed shouldBe selected.resumed.work.get.completed
    captured.repairProgress shouldBe selected.resumed.repairProgress
    captured.sessionId shouldBe kept.sessionId
    pending.issuedWorkContext shouldBe captured.issuedWorkContext
    replayed.issuedWorkContext shouldBe captured.issuedWorkContext
    _executions(scenario.root) shouldBe runs
    _executions(scenario.root.resolve("dependency")) shouldBe dependencyruns
    Files.readString(primary.resolve("value.txt")) shouldBe "expected\n"
    val receipt = scenario.root.resolve("common-keep-current-result.json")
    Files.writeString(receipt, Json.obj("sessionId" -> captured.sessionId.asJson,
      "retainedPlan" -> captured.work.get.selectedPlan.revision.asJson, "workKind" -> captured.work.get.kind.asJson,
      "humanAnswer" -> reference.identity.asJson).noSpaces, StandardOpenOption.CREATE_NEW)
    receipt
  }

  def qualifyDevelopmentReconstruction(reporter: GivenWhenThen): Path = {
    import GoalPhaseDependencySourceRevision as R
    import GoalPhaseDevelopmentReconstruction as C
    require(sys.props.contains("smworkflow.spec.installedCommand") && sys.props.contains("smworkflow.spec.componentFile"),
      "Select the actual normally published component and installed executable")
    reporter.Given("one tiny primary/dependency fixture with a real accepted source Direction and retained old effects")
    val selected = _selected("common-development-reconstruction", sourcerevisionfixture = true, beforestart = _initial_source_build)
    val scenario = selected.scenario
    def _host_(): ThreeWorkflowRuntimeParameters.Definition = {
      val configuration = parse(Files.readString(scenario.root.resolve(".textus/config.json"))).toOption.get
      SplitPhaseExplicitPartitionFixture.take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(
        parse(configuration.hcursor.get[String]("sm-workflow.runtime.definition").toOption.get).toOption.get))
    }
    val primary = Path.of(_host_().target.root)
    Files.writeString(primary.resolve(sourcePath),
      "/**\n * @since   Oct.  1, 2026\n * @version Oct.  2, 2026\n */\nobject HistoryExample { val value = 42 }\n")
    _complete(selected)
    val before = _host_().goal.dependencyValidation.get
    val dependency = Path.of(before.root)
    Files.writeString(dependency.resolve("unrelated-base.txt"), "Independent dependency base advancement\n")
    def _setup_(arguments: String*): Unit = SplitPhaseExplicitPartitionFixture.take(_run_setup_command_(
      Vector("-C", dependency.toString, "-c", "core.hooksPath=/dev/null", "-c", "user.name=Common Reconstruction Specification",
        "-c", "user.email=common@example.invalid") ++ arguments))
    _setup_("add", "--", "unrelated-base.txt")
    _setup_("commit", "--no-verify", "--no-gpg-sign", "-m", "Specification-owned dependency source base")
    scenario.advance(selected.task).exitCode should not be 0
    val describedold = _describe_recovery(selected)
    val old = SplitPhaseExplicitPartitionFixture.take(GoalPhaseCandidateRecovery.targetC(
      describedold.business[V.GoalPhaseCandidateRecoveryDescriptionResult].target))
    _recover(selected, describedold, "ReviseWork").business[V.GoalPhaseCandidateRecoveryResult].accepted shouldBe true
    val revision = scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
    _submit(scenario, revision, Json.obj("outcome" -> "decision-needed".asJson,
      "summary" -> "SPECIFICATION-OWNED select current dependency source".asJson, "findings" -> Json.arr()),
      V.WorkflowArtifactReference("common-source-diagnostic", "1")).business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
    scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult].kind shouldBe "Human"
    val actual = _native_fixture(scenario)(RepositorySyncManagedGitObservation.observeInWorkspaceC(_, before.dependency.workspace))
    val replacement = before.copy(revision = 2, base = GoalPhaseOrderedProgression.Snapshot(actual.head, actual.tree))
    _configure_source_host(scenario, replacement, _initial_consumption(replacement), Vector("fixture.SelectedDependencyCheck"))
    val proposal = R.Proposal(R.sourceSchema, ContextReference(replacement.identity, replacement.revision.toString),
      "selected-changes", Vector.empty, Vector(R.Coverage(selected.selection.approvals.head.decision,
        Vector("Validate the exact dependency candidate"), replacement.checks.map(_.identity))), _initial_consumption(replacement))
    val sourceoutput = scenario.call("command", R.describeOperation, Vector("--input", json(
      V.GoalPhaseDependencySourceRevisionDescriptionInput(selected.original.sessionId,
        GoalPhaseNativeMetadata.record(proposal.asJson)).toRecord()).noSpaces))
    val description = sourceoutput.business[V.GoalPhaseDependencySourceRevisionDescriptionResult]
    val target = SplitPhaseExplicitPartitionFixture.take(R.targetC(description.target))
    target.initialOrigin should not be empty
    val reference = V.WorkflowArtifactReference("common-original-source-direction", "1")
    val adoption = V.GoalPhaseDependencySourceRevisionAdoptionInput(target.sessionId, description.target, R.initialChoice,
      "SPECIFICATION-OWNED current source selection retaining historical UNKNOWN", reference)
    _provision(selected, sourceoutput, "dependency-source-revision", adoption, reference)
    scenario.call("command", R.adoptionOperation, Vector("--input", json(adoption.toRecord()).noSpaces))
      .business[V.GoalPhaseDependencySourceRevisionAdoptionResult].accepted shouldBe true
    val implementation = scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]

    reporter.When("new implementation changes current source and the common operation recollects its genuine still-pending work")
    Files.writeString(primary.resolve(sourcePath),
      "/**\n * @since   Oct.  1, 2026\n * @version Oct.  2, 2026\n */\nobject HistoryExample { val value = 43 }\n")
    Files.writeString(primary.resolve("value.txt"), "genuine failing primary input\n")
    Files.writeString(primary.resolve("docs/phase.md"), "# Current common development\nContinue actual current source within the same Component.\n")
    Files.writeString(primary.resolve("docs/reconstruction-checklist.md"), "- [x] current: Preserve original checks and repair accounting\n")
    val projected = target.plan.copy(revision = "common-current", acceptance = Some(
      GoalPhaseAcceptanceBoundary.Adoption("SPECIFICATION-OWNED current semantic projection", Vector.empty)), effectiveAcceptance = None)
    val input = V.GoalPhaseDevelopmentReconstructionInput(target.sessionId, "docs/phase.md", "docs/reconstruction-checklist.md",
      GoalPhaseNativeMetadata.record(GoalPhasePlanRevision.Proposal("common-current-projection", "1",
        "Current actual Phase/checklist/source projection", projected).asJson), "Continue the actual current development")
    def _reconstruct_(): F.Result = scenario.call("command", C.operation,
      Vector("--inputResource", _save(scenario, "common-reconstruction-input-" + scenario.invocations.size, json(input.toRecord()).noSpaces)))
    val pending = _reconstruct_().business[V.GoalPhaseAdvanceResult]
    reporter.Then("the active worker is preserved and no pending work is superseded or submitted twice")
    pending.kind shouldBe "Request"
    pending.issuedWorkContext shouldBe implementation.issuedWorkContext
    _submit(scenario, implementation, Json.obj("outcome" -> "completed".asJson,
      "summary" -> "SPECIFICATION-OWNED accepted later current source".asJson, "findings" -> Json.arr()),
      V.WorkflowArtifactReference("common-current-implementation", "1")).business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
    val stale = scenario.advance(selected.task)
    stale.exitCode should not be 0
    Files.readString(stale.invocation.stderr) should include("completed candidate history no longer satisfies its saved rule and date")
    _executions(scenario.root) shouldBe empty

    val originalrows = _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, target.sessionId)).toMap

    reporter.When("the one common route presents the concrete semantic projection choice to an independently evidenced fixture Human")
    val questionoutput = _reconstruct_()
    val question = questionoutput.business[V.GoalPhaseAdvanceResult]
    question.kind shouldBe "Human"
    question.human.get.choices shouldBe C.choices
    question.human.get.rationale should include("Usage Contract")
    val publiccontext = question.human.get.developmentReconstruction.get
    val contextwire = GoalPhaseNativeMetadata.normalizePublicJson(json(publiccontext)).fold(fail(_), identity)
    val capturedcontext = C.decodeContext(contextwire).fold(fail(_), identity)
    capturedcontext.semanticResult.map(_.identity) shouldBe Some("common-current-implementation")
    C.codec.decode(C.codec.encode(capturedcontext)) shouldBe Right(capturedcontext)
    val answer = V.GoalPhaseOrderedDecisionAnswer("AdoptDevelopmentReconstruction", "SPECIFICATION-OWNED actual document projection selection")
    val answerreference = V.WorkflowArtifactReference("common-current-human", "1")
    val execution = V.WorkflowExecutionEvidence(Vector(answerreference), Some("participant"), None, None)
    val actualcontext = question.human.get.humanDecisionContext.get
    val questionwire = GoalPhaseNativeMetadata.normalizePublicJson(json(actualcontext.question)).fold(fail(_), identity)
    val tampered = contextwire.mapObject(_.add("unknown", Json.True))
    val crossed = contextwire.mapObject(fields => fields.add("payload", fields("payload").get.mapObject(
      _.add("sessionId", "another-session".asJson))))
    val originalstate = _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, target.sessionId))
    Vector(tampered, crossed, Json.Null).zipWithIndex.foreach { (wire, index) =>
      val changed = questionwire.mapObject(fields => fields.add("source", fields("source").get.mapObject(
        _.add("developmentReconstruction", wire))))
      val alteredcontext = actualcontext.copy(question = GoalPhaseNativeMetadata.record(changed))
      val alteredquestion = question.copy(human = Some(question.human.get.copy(humanDecisionContext = Some(alteredcontext))))
      val preparation = V.WorkflowEvidencePreparationInput("GoalPhase", "human",
        _save(scenario, "common-tampered-question-" + index, json(alteredquestion.toRecord()).noSpaces),
        _save(scenario, "common-tampered-answer-" + index, json(answer.toRecord()).noSpaces),
        _save(scenario, "common-tampered-reference-" + index, json(answerreference.toRecord()).noSpaces),
        _save(scenario, "common-tampered-execution-" + index, json(execution.toRecord()).noSpaces),
        "normal-development-host", Instant.now().minusSeconds(60).toString, "entity:smworkflow.cli.host_evidence", Some("participant"))
      scenario.prepareHostInput("prepareEvidence", preparation.toRecord()).exitCode should not be 0
    }
    _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, target.sessionId)) shouldBe originalstate
    _provision(selected, questionoutput, "human", answer, answerreference)
    scenario.call("command", question.submitOperation.get, Vector("--sessionId", question.submitSessionId.get,
      "--answer", json(answer.toRecord()).noSpaces, "--resultReference", json(answerreference.toRecord()).noSpaces,
      "--executionEvidence", json(execution.toRecord()).noSpaces))
      .business[V.GoalPhaseOrderedDecisionSubmissionResult].accepted shouldBe true
    scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
    val reconstructed = _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, target.sessionId)).toMap
    val repeated = _reconstruct_().business[V.GoalPhaseAdvanceResult]
    reporter.Then("the same Session/generation and all original non-instance records remain while the new capture stays unstarted")
    repeated.sessionId shouldBe target.sessionId
    _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, target.sessionId)).toMap shouldBe reconstructed
    originalrows.filterNot(_._1 == "instance").foreach { (key, bytes) => reconstructed.get(key) shouldBe Some(bytes) }

    reporter.When("separate ordinary Advance captures current source and an actual failing check issues normal RepairStep")
    val failed = scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
    failed.work.get.kind shouldBe "RepairStep"
    failed.repairProgress.get.automaticCount should be > 0
    failed.work.get.originalBase shouldBe implementation.work.get.originalBase
    Files.writeString(primary.resolve("value.txt"), "expected\n")
    val fixed = failed.work.get.issues.toVector.flatMap(_.issues).filter(_.status != "RESOLVED").map(issue =>
      V.GoalPhaseIssueDisposition(issue.identity, "FIXED", "SPECIFICATION-OWNED correction of the actual primary input", None))
    val report = V.GoalPhaseAcceptanceReport(Vector.empty, Vector.empty)
    _submit(scenario, failed, Json.obj("outcome" -> "completed".asJson,
      "summary" -> "SPECIFICATION-OWNED ordinary repair".asJson, "findings" -> Json.arr(),
      "issueReport" -> json(V.GoalPhaseIssueReport(fixed).toRecord()), "acceptance" -> json(report.toRecord())),
      V.WorkflowArtifactReference("common-real-repair", "1")).business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
    val failedrows = _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, target.sessionId)).toMap
    val review = scenario.advance(selected.task)
    val passed = review.business[V.GoalPhaseAdvanceResult]
    reporter.Then("fresh checks pass with the charged repair and actual common Plan provenance, while failure history stays intact")
    passed.work.get.kind shouldBe "ReviewStep"
    passed.repairProgress shouldBe failed.repairProgress
    val validation = GoalPhaseNativeMetadata.normalizePublicJson(json(passed.work.get.review.get.dependencyValidations.last))
      .fold(fail(_), identity)
    val rawcapture = validation.hcursor.get[Json]("capture").fold(error => fail(error.message), identity)
    val direction = rawcapture.hcursor.get[Json]("direction").fold(error => fail(error.message), identity)
    val rawtarget = direction.hcursor.get[Json]("target").fold(error => fail(error.message), identity)
    val publictarget = SplitPhaseExplicitPartitionFixture.take(R.targetC(GoalPhaseNativeMetadata.record(rawtarget)))
    val source = rawcapture.mapObject(_.add("direction", direction.mapObject(_.add("target", publictarget.asJson))))
      .as[R.Capture].fold(error => fail(error.message), identity)
    source.schemaVersion shouldBe R.developmentCaptureSchema
    source.planSelection should not be empty
    source.direction.target shouldBe target
    source.plan.revision shouldBe projected.revision
    source.generation should be > (target.generation + 1)
    val passedrows = _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, target.sessionId)).toMap
    val suppliedselection = json(input.toRecord()).mapObject(_.add("planSelection", Json.obj(
      "identity" -> (target.sessionId + ":ordered-development-reconstruction:999999").asJson, "revision" -> "1".asJson)))
    scenario.call("command", C.operation, Vector("--input", suppliedselection.noSpaces)).exitCode should not be 0
    _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, target.sessionId)).toMap shouldBe passedrows
    failedrows.filterNot(_._1 == "instance").foreach { (key, bytes) => passedrows.get(key) shouldBe Some(bytes) }
    val runs = _executions(scenario.root)
    val pendingreview = _reconstruct_().business[V.GoalPhaseAdvanceResult]
    pendingreview.issuedWorkContext shouldBe passed.issuedWorkContext
    _executions(scenario.root) shouldBe runs
    _review(selected, review, artifact = "common-first", acceptancereport = Some(report))
      .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true

    reporter.When("one ordinary further same-Step source edit uses the common route before normal final closure")
    Files.writeString(primary.resolve(sourcePath), "// further same-Step source edit\n", StandardOpenOption.APPEND)
    _reconstruct_().business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
    val finalreview = scenario.advance(selected.task)
    finalreview.business[V.GoalPhaseAdvanceResult].work.get.kind shouldBe "ReviewStep"
    _executions(scenario.root).size should be > runs.size
    val closed = _close_step(selected, finalreview, "common-final", acceptancereport = Some(report))
      .business[V.GoalPhaseAdvanceResult]
    reporter.Then("one real local Step commit closes the corrected current source and original UNKNOWN/base/counters/history remain")
    closed.sessionId shouldBe target.sessionId
    closed.work.get.stepIdentity shouldBe Some("step-2")
    closed.work.get.completed.map(_.step) shouldBe Vector("step-1")
    closed.work.get.originalBase shouldBe implementation.work.get.originalBase
    scenario.candidateHead shouldBe closed.work.get.completed.last.commit.identity
    val finalrows = _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, target.sessionId)).toMap
    originalrows.filterNot(_._1 == "instance").foreach { (key, bytes) => finalrows.get(key) shouldBe Some(bytes) }
    old.oldObservationOutcome shouldBe "UNKNOWN"
    old.oldObservationTermination shouldBe "termination-unconfirmed"
    val receipt = scenario.root.resolve("common-development-reconstruction-result.json")
    Files.writeString(receipt, Json.obj("sessionId" -> target.sessionId.asJson,
      "stepCommit" -> closed.work.get.completed.last.commit.identity.asJson,
      "oldUnknown" -> old.oldObservationOutcome.asJson, "currentPlan" -> projected.revision.asJson,
      "planSelection" -> source.planSelection.map(reference => Json.obj(
        "identity" -> reference.identity.asJson, "revision" -> reference.revision.asJson)).getOrElse(Json.Null)).noSpaces, StandardOpenOption.CREATE_NEW)
    receipt
  }

  /** One explicit old-product/current-product episode over the existing tiny
    * accepted-prefix and checked-Repair operation fixtures. All evidence is
    * specification-owned; no real consumer artifact adoption is performed. */
  /** Two independently directed committed source choices are intentional here:
    * the first establishes the actual old-root Direction, the second selects
    * the new root while leaving only the fixed primary publisher at the old one.
    * No supplied JSON or storage seeding manufactures that native lineage.
    */
  def qualifyCanonicalPrimaryHostCorrection(reporter: GivenWhenThen): Path = {
    import io.circe.generic.auto.*
    import scala.jdk.CollectionConverters.*
    import GoalPhaseCanonicalDependencySourceSuccession as C
    import GoalPhaseDependencySourceRevision as R
    import GoalPhasePublishedDependencySourceAdoption as P
    import GoalPhasePrerequisitePublicationHostRecovery as H
    import GoalPhaseCanonicalPublicationProgramCorrection as K
    import GoalPhaseOrderedProgression as O
    import org.goldenport.cncf.processexecution.SbtOperation
    import org.goldenport.convert.ValueReader
    import org.goldenport.record.io.RecordDecoder
    import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
    val mode = sys.props.getOrElse("smworkflow.spec.canonicalHostCorrectionMode", cancel("Select PREPARE_OLD or RESUME_CURRENT"))
    require(Set("PREPARE_OLD", "RESUME_CURRENT")(mode), "Select an explicit canonical host correction stage")
    val pendingroot = sys.props.get("smworkflow.spec.canonicalHostCorrectionPendingRoot").map(Path.of(_))
    require(pendingroot.isEmpty || mode == "PREPARE_OLD", "A retained preparation suffix belongs only to PREPARE_OLD")
    val episode = Path.of(sys.props.getOrElse("smworkflow.spec.canonicalHostCorrectionEpisode",
      cancel("Select the absolute test-owned canonicalHostCorrectionEpisode locator")))
    require(episode.isAbsolute && episode.normalize == episode, "Select one exact absolute episode path")
    val executable = Path.of(sys.props.getOrElse("smworkflow.spec.installedCommand", cancel("Select the normally installed CLI")))
    val component = sys.props.getOrElse("smworkflow.spec.componentFile", cancel("Select the normally published component CAR"))
    require(executable.isAbsolute, "Select the absolute normal installed command")
    val selector = "TEXTUS_CNCF_SOURCE_ROOT" // Fixture-owned name; production derives its key from captured routes.
    val publicationidentity = "specification-canonical-primary-publisher"
    def _array_(value: Json, key: String): Vector[Json] = value.hcursor.get[Vector[Json]](key).toOption.get
    def _host_(root: Path): ThreeWorkflowRuntimeParameters.Definition = SplitPhaseExplicitPartitionFixture.take(
      ThreeWorkflowRuntimeParameters.decodeDefinitionC(parse(parse(Files.readString(root.resolve(".textus/config.json")))
        .toOption.get.hcursor.get[String]("sm-workflow.runtime.definition").toOption.get).toOption.get))
    def _advance_(value: Json): V.GoalPhaseAdvanceResult = SplitPhaseExplicitPartitionFixture.take(
      summon[ValueReader[V.GoalPhaseAdvanceResult]].readC(SplitPhaseExplicitPartitionFixture.take(new RecordDecoder().json(value.noSpaces))))
    def _setup_(repository: Path, arguments: String*): Unit = SplitPhaseExplicitPartitionFixture.take(_run_setup_command_(
      Vector("--no-optional-locks", "--no-replace-objects", "-C", repository.toString,
        "-c", "core.hooksPath=/dev/null", "-c", "core.fsmonitor=false", "-c", "gc.auto=0",
        "-c", "user.name=Canonical Publisher Specification", "-c", "user.email=canonical-publisher@example.invalid") ++ arguments))
    def _save_once_(path: Path, value: Json): Unit = {
      if (Files.exists(path)) parse(Files.readString(path)).toOption.get shouldBe value
      else Files.writeString(path, value.noSpaces, StandardOpenOption.CREATE_NEW)
    }
    def _counts_(root: Path): Vector[String] = {
      val path = root.resolve("publication-count.txt")
      if (Files.exists(path)) Files.readString(path).linesIterator.toVector else Vector.empty
    }
    def _register_(scenario: F.Scenario, binding: D.Binding, stage: String): LocalPublication.Target = {
      val file = scenario.root.resolve(".textus/config.json")
      val config = parse(Files.readString(file)).toOption.get
      val process = parse(config.hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
      val producer = LocalPublication.Target("specification-canonical-" + stage + "-producer", binding.dependency.identity,
        binding.dependency.workspace)
      val primary = LocalPublication.Target(publicationidentity, binding.requester.identity, binding.requester.workspace,
        beforeChecks = true)
      val template = _array_(TypedSbtHostPlanFixture.plan(scenario.root, TypedSbtHostPlanFixture.normalExecutable,
        Vector(1), operation = SbtOperation.PublishLocal, stdoutBytes = 1048576, stderrBytes = 131072), "sbtPrograms").head
      val publisher = template.mapObject(fields => fields.add("identity", publicationidentity.asJson)
        .add("target", primary.workspace.asJson).add("capability", publicationidentity.asJson)
        .add("environment", fields("environment").get.mapObject(_.add(selector,
          scenario.root.resolve("dependency/repository").toRealPath().toString.asJson))))
      val producerprogram = template.mapObject(_.add("identity", (producer.identity + "-program").asJson)
        .add("target", producer.workspace.asJson).add("capability", (producer.identity + "-program").asJson))
      val sbt = _array_(process, "sbtPrograms") ++ Option.when(!_array_(process, "sbtPrograms").exists(
        _.hcursor.get[String]("identity").contains(publicationidentity)))(publisher).toVector :+ producerprogram
      val processes = process.mapObject(_.add("sbtPrograms", sbt.asJson).add("grants", Json.arr(Json.obj(
        "componentId" -> SmWorkflowComponent.componentId.name.asJson,
        "capabilities" -> (_array_(process, "gitPrograms") ++ sbt).map(_.hcursor.get[String]("capability").toOption.get).distinct.asJson))))
      val collection = ThreeWorkflowNormalHostFixture.collection("canonical_primary_publication")
      val storage = parse(config.hcursor.get[String]("textus.subsystem.storage.plan").toOption.get).toOption.get
      val storageplan = storage.mapObject(_.add("records", (_array_(storage, "records") :+ Json.obj(
        "componentId" -> SmWorkflowComponent.componentId.name.asJson, "storeName" -> "workflow".asJson,
        "collection" -> collection.asJson)).distinct.asJson))
      val existing = config.hcursor.get[String](LocalPublicationRuntime.declaration.name).toOption
        .flatMap(raw => parse(raw).toOption).map(_array_(_, "targets")).getOrElse(Vector.empty)
      val policy = Json.obj("storeName" -> "workflow".asJson, "collection" -> collection.asJson,
        "targets" -> (existing.filterNot(_.hcursor.get[String]("identity").contains(publicationidentity)) :+
          producer.asJson :+ primary.asJson).asJson)
      Files.writeString(file, config.mapObject(_.add("textus.subsystem.process-execution.plan", processes.noSpaces.asJson)
        .add("textus.subsystem.storage.plan", storageplan.noSpaces.asJson)
        .add(LocalPublicationRuntime.declaration.name, policy.noSpaces.asJson)).noSpaces)
      producer
    }
    def _producer_(scenario: F.Scenario, binding: D.Binding, stage: String, module: String): P.PublicationObservation = {
      val target = _register_(scenario, binding, stage)
      val input = V.LocalPublicationInput("specification-canonical-" + stage + "-publication", target.identity, None, true, None)
      scenario.callInstalled(executable, "LocalPublicationService", "ensureLocalPublication",
        Vector("--input", json(input.toRecord()).noSpaces), assemblyDescriptor = Some(scenario.root.resolve(".textus/assembly.yaml")))
        .business[V.LocalPublicationResult].status shouldBe "published"
      val collected = scenario.callInstalled(executable, "LocalPublicationService", "collectLocalPublication",
        Vector("--input", json(V.LocalPublicationCollectionInput(input.requestIdentity, target.identity, None, true).toRecord()).noSpaces),
        assemblyDescriptor = Some(scenario.root.resolve(".textus/assembly.yaml")))
      val result = collected.business[V.LocalPublicationResult]
      result.status shouldBe "published"
      val publication = SplitPhaseExplicitPartitionFixture.take(GoalPhasePrerequisitePublicationUnknownRecovery.jsonC(result.publication.get))
        .as[LocalPublication.Result].toOption.get
      val stdout = WorkflowSbtCapture.text(WorkflowSbtCapture.decode(publication.process.get).toOption.get.stdout).value
        .replaceAll("\u001b\\[[0-9;]*m", "")
      val prefix = "published " + module + "_2.12 to "
      val artifacts = stdout.linesIterator.map(_.trim.stripPrefix("[info] ").trim).filter(line =>
        line.startsWith(prefix) && line.endsWith("/jars/" + module + "_2.12.jar"))
        .map(line => Path.of(line.stripPrefix(prefix)).toAbsolutePath.normalize).toVector.distinct
      artifacts should have size 1
      Files.isRegularFile(artifacts.head) shouldBe true
      val config = parse(Files.readString(scenario.root.resolve(".textus/config.json"))).toOption.get
      val processes = parse(config.hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
      val processpolicy = _native_fixture(scenario) { core => org.goldenport.cncf.processexecution.GitDiffParameters.resolveC(core.executionContext)
        .map(maximum => io.circe.Printer.noSpaces.copy(sortKeys = true).print(Json.obj("processPlan" -> processes,
          "maximumDiffBytes" -> maximum.asJson, "validation" -> _host_(scenario.root).goal.stepValidation.asJson))) }
      P.PublicationObservation(P.CollectionResult(result.status, result.reason, publication), P.collectOperation,
        ContextReference(collected.invocation.stdout.toUri.toASCIIString, "1"), scenario.root.toUri.toASCIIString,
        Instant.now().toString, processpolicy, artifacts.head.getParent.getParent.toUri.toASCIIString,
        ContextReference(artifacts.head.toUri.toASCIIString, "0.0.1-SNAPSHOT"))
    }
    def _adopt_(selected: Selected, binding: D.Binding, consumption: R.Consumption,
      observation: P.PublicationObservation, stage: String): V.GoalPhaseAdvanceResult = {
      val scenario = selected.scenario
      val source = R.Proposal(R.sourceSchema, ContextReference(binding.identity, binding.revision.toString), "committed", Vector.empty,
        Vector(R.Coverage(ContextReference("specification-dependency-approval", "1"),
          Vector("Validate the exact dependency candidate"), binding.checks.map(_.identity)),
          R.Coverage(ContextReference("specification-approval-step-2", "1"),
            Vector("Integrate with the genuinely accepted predecessor"), binding.checks.map(_.identity))), consumption)
      val delivery = ContextReference(episode.toUri.toASCIIString + "#" + stage, "1")
      val proposal = C.Proposal(C.proposalSchema, source, observation, delivery, Some(C.WorkerLifecycle(true, true)))
      val described = scenario.call("command", R.describeOperation, Vector("--input", json(
        V.GoalPhaseDependencySourceRevisionDescriptionInput(selected.original.sessionId,
          GoalPhaseNativeMetadata.record(proposal.asJson)).toRecord()).noSpaces))
      val description = described.business[V.GoalPhaseDependencySourceRevisionDescriptionResult]
      val reference = V.WorkflowArtifactReference("specification-canonical-" + stage + "-source-human", "1")
      val input = V.GoalPhaseDependencySourceRevisionAdoptionInput(selected.original.sessionId, description.target, C.pendingChoice,
        "SPECIFICATION-ONLY prior worker ended; select the exact committed " + stage + " source accepting prior unknown effects", reference)
      val execution = V.WorkflowExecutionEvidence(Vector(reference,
        V.WorkflowArtifactReference(observation.recollectionReference.identity, observation.recollectionReference.revision),
        V.WorkflowArtifactReference(observation.artifact.identity, observation.artifact.revision),
        V.WorkflowArtifactReference(delivery.identity, delivery.revision)), Some("participant"), None, None)
      val evidence = V.WorkflowEvidencePreparationInput("GoalPhase", "dependency-source-revision", described.invocation.stdout.toUri.toASCIIString,
        _save(scenario, stage + "-source-answer", json(input.toRecord()).noSpaces),
        _save(scenario, stage + "-source-reference", json(reference.toRecord()).noSpaces),
        _save(scenario, stage + "-source-execution", json(execution.toRecord()).noSpaces), "normal-development-host",
        Instant.now().toString, "entity:smworkflow.cli.host_evidence", Some("participant"))
      val prepared = scenario.prepareHostInput("prepareEvidence", evidence.toRecord())
      prepared.exitCode shouldBe 0
      scenario.selectPreparedImport(prepared)
      scenario.call("command", R.adoptionOperation, Vector("--input", json(input.toRecord()).noSpaces))
        .business[V.GoalPhaseDependencySourceRevisionAdoptionResult].accepted shouldBe true
      scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
    }
    if (mode == "PREPARE_OLD") {
      require(!Files.exists(episode), "Retain an existing episode and collect its suffix; never repeat PREPARE effects")
      reporter.Given("R6: a tiny ordinary primary and dependency with actual accepted Step1 and retained Step2 failed Repair")
      val (selected, oldrepair, initialbinding, firstcapture) = pendingroot match {
        case None =>
          val prefix = _accepted_prefix("canonical-primary-host-correction", overlap = true, sourceRevisionFixture = true, beforestart = scenario => {
            val primary = Path.of(scenario.host.target.root)
            Files.writeString(primary.resolve("build.sbt"), s"""
              |lazy val validateCanonicalPublisherSource = taskKey[Unit]("Specification-only exact publisher source selection")
              |validateCanonicalPublisherSource := {
              |  IO.append(file(${scenario.root.resolve("primary-publication-count.txt").toString.asJson.noSpaces}), "attempt\\n")
              |  val expected = IO.read(file(${scenario.root.resolve("expected-canonical-source-root.txt").toString.asJson.noSpaces})).trim
              |  require(sys.env.get("$selector").contains(expected), "fixed primary publisher selected the previous source root")
              |}
              |publishLocal := publishLocal.dependsOn(validateCanonicalPublisherSource).value
              |""".stripMargin, StandardOpenOption.APPEND)
            _setup_(primary, "add", "--", "build.sbt")
            _setup_(primary, "commit", "--no-verify", "--no-gpg-sign", "-m", "Declare specification-only publisher source guard")
          })
          val selected = _adopt_prefix(prefix)
          _complete_prefix(selected, overlap = true, failCheck = true)
          val repair = selected.scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
          (selected, repair, prefix.binding, prefix.first)
        case Some(root) =>
          val retainedroot = Path.of("").toAbsolutePath.normalize.resolve(
            "target/sm-workflow/work/native-cli/canonical-primary-host-correction-9937187872082694368")
          require(root.isAbsolute && root.normalize == root && root == retainedroot && root.toRealPath() == root,
            "Select only the explicitly retained canonical primary preparation root")
          def _retained_(index: Int): V.GoalPhaseAdvanceResult =
            _advance_(parse(Files.readString(root.resolve("logs/" + index + ".stdout.json"))).toOption.get)
          val original = _retained_(101)
          val resumed = _retained_(118)
          val repair = _retained_(120)
          val first = _capture(_retained_(108))
          val currentcapture = GoalPhaseNativeMetadata.normalizePublicJson(json(
            repair.work.get.checkFailure.get.validation.dependencyValidation.get))
            .flatMap(_.hcursor.get[D.Capture]("capture").left.map(_.message)).fold(fail(_), identity)
          val binding = currentcapture.admission.binding
          val selection = D.Selection(ContextReference(binding.identity, binding.revision.toString),
            currentcapture.admission.approvals.map(approval => D.ApprovalReference(
              ContextReference(approval.proposal.identity, approval.proposal.revision), approval.decision)))
          val task = root.resolve("target/sm-workflow/tasks/dependency-validation")
          val route = parse(Files.readString(task.resolve("workflow-session.json"))).toOption.get
          route.hcursor.get[String]("family").toOption shouldBe Some("GoalPhase")
          route.hcursor.get[String]("sessionId").toOption shouldBe Some(repair.sessionId)
          original.sessionId shouldBe repair.sessionId
          resumed.sessionId shouldBe repair.sessionId
          first.session shouldBe repair.sessionId
          currentcapture.session shouldBe repair.sessionId
          repair.work.get.kind shouldBe "RepairStep"
          repair.work.get.stepIdentity shouldBe Some("step-2")
          repair.work.get.completed.map(_.step) shouldBe Vector("step-1")
          repair.work.get.originalBase shouldBe original.work.get.originalBase
          repair.work.get.originalBase shouldBe resumed.work.get.originalBase
          repair.work.get.selectedPlan shouldBe original.work.get.selectedPlan
          repair.work.get.selectedPlan shouldBe resumed.work.get.selectedPlan
          val configfile = root.resolve(".textus/config.json")
          val config = parse(Files.readString(configfile)).toOption.get
          def _shorten_(value: Json): Json = value.fold(Json.Null, Json.fromBoolean, Json.fromJsonNumber,
            text => Json.fromString(if (text == "specification-committed-old-source" ||
                text.startsWith("specification-committed-old-source-"))
              "canonical-old-source" + text.stripPrefix("specification-committed-old-source") else text),
            values => Json.fromValues(values.map(_shorten_)), fields => Json.fromJsonObject(fields.mapValues(_shorten_)))
          val shortened = Vector("sm-workflow.runtime.definition", "textus.subsystem.process-execution.plan")
            .foldLeft(config) { (previous, key) =>
              val declared = parse(previous.hcursor.get[String](key).toOption.get).toOption.get
              previous.mapObject(_.add(key, _shorten_(declared).noSpaces.asJson))
            }
          Files.writeString(configfile, shortened.noSpaces)
          val host = _host_(root)
          val plan = parse(Files.readString(root.resolve("goal-plan.json"))).toOption.get.as[O.Plan].toOption.get
          repair.work.get.selectedPlan shouldBe F.publicPlan(plan)
          val entries = Files.list(root.resolve("logs"))
          val index = try entries.iterator.asScala.map(_.getFileName.toString.takeWhile(_.isDigit)).flatMap(_.toIntOption)
            .foldLeft(0)(math.max) + 1 finally entries.close()
          val scenario = F.reopenCanonicalSource(root, host, plan, index)
          (Selected(scenario, task, original, resumed, selection), repair, binding, first)
      }
      oldrepair.work.get.kind shouldBe "RepairStep"
      oldrepair.work.get.completed.map(_.step) shouldBe Vector("step-1")
      val scenario = selected.scenario
      val oldroot = Path.of(initialbinding.root)
      val orderedpaths = (firstcapture.changedPaths ++ initialbinding.paths).distinct
      val oldmodule = "canonical-host-old-" + scenario.root.getFileName.toString
      if (pendingroot.isEmpty) {
        Files.writeString(oldroot.resolve("value.txt"), "expected\n")
        oldroot.resolve("value.txt").toFile.setExecutable(false, false) shouldBe true
        Files.createDirectories(oldroot.resolve("src/main/scala"))
        Files.writeString(oldroot.resolve("src/main/scala/SourceRevisionMarker.scala"),
          "object SourceRevisionMarker { def source: String = " + oldroot.toRealPath().toString.asJson.noSpaces + " }\n")
        Files.writeString(oldroot.resolve("build.sbt"), "\norganization := \"org.example.smworkflow.specification\"\n" +
          "name := " + oldmodule.asJson.noSpaces + "\nversion := \"0.0.1-SNAPSHOT\"\n" +
          "lazy val root = project.in(file(\".\"))\n" +
          "publishLocal := { val result = publishLocal.value; IO.append(baseDirectory.value.getParentFile / \"publication-count.txt\", \"published\\n\"); result }\n",
          StandardOpenOption.APPEND)
        _setup_(oldroot, "add", "--", "build.sbt", "value.txt", "current.txt", "inherited.txt", "src/main/scala/SourceRevisionMarker.scala")
        _setup_(oldroot, "commit", "--no-verify", "--no-gpg-sign", "-m", "Commit specification-only old-root qualification source")
      } else {
        oldroot.toRealPath() shouldBe scenario.root.resolve("dependency/repository").toRealPath()
        Files.readString(oldroot.resolve("value.txt")) shouldBe "expected\n"
        Files.isExecutable(oldroot.resolve("value.txt")) shouldBe false
        Files.readString(oldroot.resolve("src/main/scala/SourceRevisionMarker.scala")) shouldBe
          "object SourceRevisionMarker { def source: String = " + oldroot.toRealPath().toString.asJson.noSpaces + " }\n"
        Files.readString(oldroot.resolve("build.sbt")) should include("name := " + oldmodule.asJson.noSpaces)
        Files.readString(oldroot.resolve("build.sbt")) should include("version := \"0.0.1-SNAPSHOT\"")
        _counts_(scenario.root.resolve("dependency")) shouldBe empty
      }
      val provisionalold = initialbinding.copy(revision = initialbinding.revision + 1, paths = orderedpaths,
        dependency = initialbinding.dependency.copy(workspace = "canonical-old-source"))
      _configure_source_host(scenario, provisionalold, _initial_consumption(provisionalold))
      val oldnative = _native_fixture(scenario)(RepositorySyncManagedGitObservation.observeInWorkspaceC(_, provisionalold.dependency.workspace))
      oldnative.dirty shouldBe empty
      val oldbinding = provisionalold.copy(base = O.Snapshot(oldnative.head, oldnative.tree))
      _configure_source_host(scenario, oldbinding, _initial_consumption(oldbinding))
      val oldobservation = _producer_(scenario, oldbinding, "old", oldmodule)
      reporter.When("the old installed product independently adopts committed old-root source, then new-root source in the same pending Step")
      val oldwork = _adopt_(selected, oldbinding, _initial_consumption(oldbinding), oldobservation, "old")
      oldwork.work.get.kind shouldBe "RepairStep"
      val canonical = Files.createDirectories(scenario.root.resolve("canonical-dependency"))
      val newroot = canonical.resolve("repository")
      _setup_(scenario.root, "clone", "--no-local", "--", oldroot.toString, newroot.toString)
      _setup_(newroot, "checkout", "-b", "canonical")
      Files.writeString(newroot.resolve("src/main/scala/SourceRevisionMarker.scala"),
        "object SourceRevisionMarker { def source: String = " + newroot.toRealPath().toString.asJson.noSpaces + " }\n")
      val newmodule = "canonical-host-new-" + scenario.root.getFileName.toString
      Files.writeString(newroot.resolve("build.sbt"), "\nname := " + newmodule.asJson.noSpaces + "\n", StandardOpenOption.APPEND)
      _setup_(newroot, "add", "--", "build.sbt", "src/main/scala/SourceRevisionMarker.scala")
      _setup_(newroot, "commit", "--no-verify", "--no-gpg-sign", "-m", "Commit specification-only canonical successor source")
      val provisional = oldbinding.copy(revision = oldbinding.revision + 1, root = newroot.toRealPath().toString, branch = "canonical",
        dependency = oldbinding.dependency.copy(workspace = "canonical-new-source"))
      _configure_source_host(scenario, provisional, _initial_consumption(provisional))
      val native = _native_fixture(scenario)(RepositorySyncManagedGitObservation.observeInWorkspaceC(_, provisional.dependency.workspace))
      val binding = provisional.copy(base = O.Snapshot(native.head, native.tree))
      val consumption = _initial_consumption(binding)
      _configure_source_host(scenario, binding, consumption)
      val observation = _producer_(scenario, binding, "new", newmodule)
      val current = _adopt_(selected, binding, consumption, observation, "new")
      current.work.get.kind shouldBe "RepairStep"
      current.work.get.completed shouldBe oldrepair.work.get.completed
      current.repairProgress shouldBe oldrepair.repairProgress
      Files.writeString(scenario.root.resolve("expected-canonical-source-root.txt"), binding.root + "\n", StandardOpenOption.CREATE_NEW)
      val dispositions = current.work.get.issues.toVector.flatMap(_.issues).filter(_.status != "RESOLVED").map(issue =>
        V.GoalPhaseIssueDisposition(issue.identity, "FIXED", "SPECIFICATION-ONLY committed canonical source", None))
      _submit(scenario, current, Json.obj("outcome" -> "completed".asJson, "summary" -> "SPECIFICATION-ONLY canonical repair".asJson,
        "findings" -> Json.arr(), "issueReport" -> json(V.GoalPhaseIssueReport(dispositions).toRecord())),
        V.WorkflowArtifactReference("specification-canonical-primary-completion", "1"))
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val failedoutput = scenario.advance(selected.task)
      val failed = failedoutput.business[V.GoalPhaseAdvanceResult]
      failed.kind shouldBe "Wait"
      val publication = SplitPhaseExplicitPartitionFixture.take(GoalPhasePrerequisitePublicationUnknownRecovery.jsonC(failed.localPublication.get))
        .as[LocalPublication.Result].toOption.get
      publication.status shouldBe "failed"
      WorkflowSbtCapture.exitCode(WorkflowSbtCapture.decode(publication.process.get).toOption.get) shouldBe Some(1)
      val refusal = scenario.call("command", "describeGoalPhasePrerequisitePublicationRecovery", Vector("--sessionId", current.sessionId))
      val retained = Json.obj("schemaVersion" -> "specification.canonical-primary-host-correction.v1".asJson,
        "root" -> scenario.root.toString.asJson, "task" -> selected.task.toString.asJson,
        "original" -> json(selected.original.toRecord()), "resumed" -> json(selected.resumed.toRecord()), "selection" -> selected.selection.asJson,
        "oldRepair" -> json(oldrepair.toRecord()), "currentWork" -> json(current.toRecord()), "failed" -> publication.asJson,
        "failedOutput" -> failedoutput.invocation.stdout.toString.asJson, "oldRefusalStdout" -> refusal.invocation.stdout.toString.asJson,
        "oldRefusalStderr" -> refusal.invocation.stderr.toString.asJson, "oldRefusalBytes" -> Files.readString(refusal.invocation.stderr).asJson,
        "oldPrimaryExecutions" -> _executions(scenario.root).asJson,
        "oldDependencyExecutions" -> _executions(scenario.root.resolve("dependency")).asJson,
        "newDependencyExecutions" -> _executions(canonical).asJson,
        "oldProducerCount" -> _counts_(scenario.root.resolve("dependency")).asJson,
        "newProducerCount" -> _counts_(canonical).asJson,
        "oldComponent" -> component.asJson)
      _save_once_(episode, retained)
      reporter.Then("the exact old formal failure and public 409 remain with the physical native Session for later product replacement")
      _refused(refusal, "Unsupported primary publication failure")
      _counts_(scenario.root.resolve("dependency")) shouldBe Vector("published")
      _counts_(canonical) shouldBe Vector("published")
    } else {
      reporter.Given("R6: the exact physical PREPARE_OLD Session, retained failure, accepted prefix and actual old/new source Directions")
      val saved = parse(Files.readString(episode)).toOption.get
      saved.hcursor.get[String]("schemaVersion").toOption.get shouldBe "specification.canonical-primary-host-correction.v1"
      val root = Path.of(saved.hcursor.get[String]("root").toOption.get)
      val configfile = root.resolve(".textus/config.json")
      val config = parse(Files.readString(configfile)).toOption.get
      val host = _host_(root)
      val plan = parse(Files.readString(root.resolve("goal-plan.json"))).toOption.get.as[O.Plan].toOption.get
      val entries = Files.list(root.resolve("logs"))
      val index = try entries.iterator.asScala.map(_.getFileName.toString.takeWhile(_.isDigit)).flatMap(_.toIntOption)
        .foldLeft(0)(math.max) + 1 finally entries.close()
      val scenario = F.reopenCanonicalSource(root, host, plan, index)
      Files.writeString(configfile, config.mapObject(_.add("textus.component.file", component.asJson)
        .add("textus.resource.read.max-bytes", "16777216".asJson)).noSpaces)
      val original = _advance_(saved.hcursor.get[Json]("original").toOption.get)
      val current = _advance_(saved.hcursor.get[Json]("currentWork").toOption.get)
      val selected = Selected(scenario, Path.of(saved.hcursor.get[String]("task").toOption.get), original,
        _advance_(saved.hcursor.get[Json]("resumed").toOption.get), saved.hcursor.get[D.Selection]("selection").toOption.get)
      val failed = saved.hcursor.get[LocalPublication.Result]("failed").toOption.get
      val directedpath = episode.resolveSibling(episode.getFileName.toString + ".directed.json")
      val reviewedpath = episode.resolveSibling(episode.getFileName.toString + ".review.json")
      val closedpath = episode.resolveSibling(episode.getFileName.toString + ".closed.json")
      if (!Files.exists(directedpath) && !Files.exists(reviewedpath) && !Files.exists(closedpath)) {
        reporter.When("the new product describes v3 before the external host owner changes only its one declared publisher value")
        val described = scenario.call("command", "describeGoalPhasePrerequisitePublicationRecovery", Vector("--sessionId", current.sessionId))
        val description = described.business[V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult]
        val target = SplitPhaseExplicitPartitionFixture.take(H.targetC(description.target))
        target.schemaVersion shouldBe H.schemaV3
        target.failed shouldBe failed
        target.completed.map(_.step) shouldBe Vector("step-1")
        val correction = target.canonicalProgramCorrection.get
        K.matches(correction, target.candidate, failed.request.target) shouldBe true
        val deferreference = V.WorkflowArtifactReference("specification-canonical-primary-host-defer-" + index, "1")
        val deferinput = V.GoalPhasePrerequisitePublicationRecoveryInput(current.sessionId, description.target, "Defer",
          "SPECIFICATION-ONLY retain the old host declaration without any publication grant", deferreference, None)
        val deferexecution = V.WorkflowExecutionEvidence(Vector(deferreference), Some("participant"), None, None)
        val deferevidence = V.WorkflowEvidencePreparationInput("GoalPhase", GoalPhasePrerequisitePublicationRecovery.purpose,
          described.invocation.stdout.toUri.toASCIIString,
          _save(scenario, index + "-canonical-host-defer-answer", json(deferinput.toRecord()).noSpaces),
          _save(scenario, index + "-canonical-host-defer-reference", json(deferreference.toRecord()).noSpaces),
          _save(scenario, index + "-canonical-host-defer-execution", json(deferexecution.toRecord()).noSpaces),
          "normal-development-host", Instant.now().toString, "entity:smworkflow.cli.host_evidence", Some("participant"))
        val deferprepared = scenario.prepareHostInput("prepareEvidence", deferevidence.toRecord())
        deferprepared.exitCode shouldBe 0
        scenario.selectPreparedImport(deferprepared)
        scenario.call("command", GoalPhasePrerequisitePublicationRecovery.recoveryOperation,
          Vector("--inputResource", deferevidence.result)).business[V.GoalPhasePrerequisitePublicationRecoveryResult].accepted shouldBe false
        val deferred = scenario.call("command", "describeGoalPhasePrerequisitePublicationRecovery", Vector("--sessionId", current.sessionId))
        SplitPhaseExplicitPartitionFixture.take(H.targetC(deferred.business[V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult].target)) shouldBe target
        Files.readString(root.resolve("primary-publication-count.txt")).linesIterator.toVector shouldBe Vector("attempt")
        val before = parse(Files.readString(configfile)).toOption.get
        val beforepolicy = _native_fixture(scenario) { core => org.goldenport.cncf.processexecution.GitDiffParameters.resolveC(core.executionContext)
          .map(maximum => io.circe.Printer.noSpaces.copy(sortKeys = true).print(Json.obj("processPlan" -> parse(before.hcursor
            .get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get,
            "maximumDiffBytes" -> maximum.asJson, "validation" -> _host_(root).goal.stepValidation.asJson))) }
        Set(correction.oldProcessPolicy, correction.correctedProcessPolicy) should contain (beforepolicy)
        val correctedplan = parse(correction.correctedProcessPolicy).toOption.get.hcursor.get[Json]("processPlan").toOption.get
        Files.writeString(configfile, before.mapObject(_.add("textus.subsystem.process-execution.plan", correctedplan.noSpaces.asJson)).noSpaces)
        val afterdescription = scenario.call("command", "describeGoalPhasePrerequisitePublicationRecovery", Vector("--sessionId", current.sessionId))
        SplitPhaseExplicitPartitionFixture.take(H.targetC(afterdescription.business[V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult].target)) shouldBe target
        val reference = V.WorkflowArtifactReference("specification-canonical-primary-host-human", "1")
        val observation = V.WorkflowArtifactReference("specification-canonical-primary-host-correction", "1")
        val input = V.GoalPhasePrerequisitePublicationRecoveryInput(current.sessionId, description.target, H.authorize,
          "SPECIFICATION-ONLY authorize exactly one corrected whole-primary successor accepting possible partial old effects", reference,
          Some(V.GoalPhasePublicationHostCorrection(failed.request.input.requestIdentity, correction.programIdentity, observation,
            Instant.now().minusSeconds(1).toString, "SPECIFICATION-ONLY external host owner corrected only the described publisher source value")))
        scenario.call("command", GoalPhasePrerequisitePublicationRecovery.recoveryOperation,
          Vector("--inputResource", _save(scenario, index + "-canonical-host-unauthorized-answer", json(input.toRecord()).noSpaces)))
          .exitCode should not be 0
        val execution = V.WorkflowExecutionEvidence(Vector(reference, observation), Some("participant"), None, None)
        val evidence = V.WorkflowEvidencePreparationInput("GoalPhase", GoalPhasePrerequisitePublicationRecovery.purpose, described.invocation.stdout.toUri.toASCIIString,
          _save(scenario, index + "-canonical-host-answer", json(input.toRecord()).noSpaces),
          _save(scenario, index + "-canonical-host-reference", json(reference.toRecord()).noSpaces),
          _save(scenario, index + "-canonical-host-execution", json(execution.toRecord()).noSpaces), "normal-development-host",
          Instant.now().toString, "entity:smworkflow.cli.host_evidence", Some("participant"))
        val prepared = scenario.prepareHostInput("prepareEvidence", evidence.toRecord())
        prepared.exitCode shouldBe 0
        scenario.selectPreparedImport(prepared)
        val recovered = scenario.call("command", GoalPhasePrerequisitePublicationRecovery.recoveryOperation,
          Vector("--inputResource", evidence.result))
        val recovery = recovered.business[V.GoalPhasePrerequisitePublicationRecoveryResult]
        _save_once_(directedpath, Json.obj("target" -> target.asJson, "recovered" -> json(recovery.toRecord())))
        recovery.accepted shouldBe true
        Files.readString(root.resolve("primary-publication-count.txt")).linesIterator.toVector shouldBe Vector("attempt")
      }
      if (!Files.exists(reviewedpath) && !Files.exists(closedpath)) {
        reporter.When("a separate ordinary Advance executes one corrected successor and real selected checks")
        val reviewoutput = scenario.advance(selected.task)
        val review = reviewoutput.business[V.GoalPhaseAdvanceResult]
        _save_once_(reviewedpath, Json.obj("review" -> json(review.toRecord()), "stdout" -> reviewoutput.invocation.stdout.toString.asJson,
          "stderr" -> reviewoutput.invocation.stderr.toString.asJson, "argv" -> reviewoutput.invocation.argv.asJson,
          "pid" -> reviewoutput.invocation.pid.asJson, "exitCode" -> reviewoutput.exitCode.asJson))
        reporter.Then("ordinary ReviewStep retains Plan, base, prefix, original failure and accounting with no dependency replay")
        review.work.get.kind shouldBe "ReviewStep"
        review.validation.get.outcome shouldBe Some("passed")
        review.work.get.selectedPlan shouldBe current.work.get.selectedPlan
        review.work.get.originalBase shouldBe current.work.get.originalBase
        review.work.get.completed shouldBe current.work.get.completed
        review.repairProgress shouldBe current.repairProgress
        Files.readString(root.resolve("primary-publication-count.txt")).linesIterator.toVector shouldBe Vector("attempt", "attempt")
        val primaryruns = _executions(root)
        val dependencyruns = _executions(root.resolve("canonical-dependency"))
        scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult].issuedWorkContext shouldBe review.issuedWorkContext
        _executions(root) shouldBe primaryruns
        _executions(root.resolve("canonical-dependency")) shouldBe dependencyruns
        Files.readString(root.resolve("primary-publication-count.txt")).linesIterator.toVector shouldBe Vector("attempt", "attempt")
      }
      if (!Files.exists(closedpath)) {
        reporter.When("independent specification review and ordinary Step2 closure continue under the saved one-value policy relation")
        val review = parse(Files.readString(reviewedpath)).toOption.get
        val reviewoutput = F.Result(review.hcursor.get[Int]("exitCode").toOption.get,
          F.Invocation(review.hcursor.get[Vector[String]]("argv").toOption.get, review.hcursor.get[Long]("pid").toOption.get,
            Path.of(review.hcursor.get[String]("stdout").toOption.get), Path.of(review.hcursor.get[String]("stderr").toOption.get)))
        val closed = _close_step(selected, reviewoutput, "canonical-primary-host-current").business[V.GoalPhaseAdvanceResult]
        _save_once_(closedpath, json(closed.toRecord()))
      }
      val closed = _advance_(parse(Files.readString(closedpath)).toOption.get)
      reporter.Then("the same Session has two committed Steps and subsequent ReviewPhase; no terminal Phase or real consumer consent is claimed")
      closed.sessionId shouldBe current.sessionId
      closed.work.get.kind shouldBe "ReviewPhase"
      closed.work.get.completed.map(_.step) shouldBe Vector("step-1", "step-2")
      closed.work.get.originalBase shouldBe current.work.get.originalBase
      _counts_(root.resolve("dependency")) shouldBe saved.hcursor.get[Vector[String]]("oldProducerCount").toOption.get
      _counts_(root.resolve("canonical-dependency")) shouldBe saved.hcursor.get[Vector[String]]("newProducerCount").toOption.get
      _executions(root.resolve("dependency")) shouldBe saved.hcursor.get[Vector[String]]("oldDependencyExecutions").toOption.get
      Files.readString(Path.of(saved.hcursor.get[String]("oldRefusalStderr").toOption.get)) shouldBe
        saved.hcursor.get[String]("oldRefusalBytes").toOption.get
      val retainedfailure = scenario.callInstalled(executable, "LocalPublicationService", "collectLocalPublication", Vector("--input", json(
        V.LocalPublicationCollectionInput(failed.request.input.requestIdentity, failed.request.target.identity,
          failed.request.input.ownerSessionId, failed.request.input.explicit).toRecord()).noSpaces),
        assemblyDescriptor = Some(root.resolve(".textus/assembly.yaml")))
      val retained = retainedfailure.business[V.LocalPublicationResult]
      SplitPhaseExplicitPartitionFixture.take(GoalPhasePrerequisitePublicationUnknownRecovery.jsonC(retained.publication.get))
        .as[LocalPublication.Result].toOption.get shouldBe failed
    }
    episode
  }

  def qualifyCanonicalSource(reporter: GivenWhenThen): Path = {
    import io.circe.generic.auto.*
    import GoalPhaseCanonicalDependencySourceSuccession as C
    import GoalPhaseDependencySourceRevision as R
    import GoalPhasePublishedDependencySourceAdoption as P
    import GoalPhaseOrderedProgression as O
    import org.goldenport.cncf.processexecution.{GitOperationKind, SbtOperation}
    import org.goldenport.convert.ValueReader
    import org.goldenport.record.io.RecordDecoder
    import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
    val mode = sys.props.getOrElse("smworkflow.spec.canonicalSourceMode", cancel("Select PREPARE_OLD or RESUME_CURRENT"))
    require(Set("PREPARE_OLD", "RESUME_CURRENT")(mode), "Select the explicit canonical cutover stage")
    val episode = Path.of(sys.props.getOrElse("smworkflow.spec.canonicalSourceFixture",
      cancel("Select an absolute test-owned canonicalSourceFixture JSON locator")))
    require(episode.isAbsolute && episode.normalize == episode, "The episode must be one explicit absolute path")
    val executable = Path.of(sys.props.getOrElse("smworkflow.spec.installedCommand", cancel("Select the installed CLI")))
    val component = sys.props.getOrElse("smworkflow.spec.componentFile", cancel("Select the normally published component CAR"))
    def _host_(scenario: F.Scenario): ThreeWorkflowRuntimeParameters.Definition = {
      val config = parse(Files.readString(scenario.root.resolve(".textus/config.json"))).toOption.get
      SplitPhaseExplicitPartitionFixture.take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(
        parse(config.hcursor.get[String]("sm-workflow.runtime.definition").toOption.get).toOption.get))
    }
    def _advance_(value: Json): V.GoalPhaseAdvanceResult = SplitPhaseExplicitPartitionFixture.take(
      summon[ValueReader[V.GoalPhaseAdvanceResult]].readC(SplitPhaseExplicitPartitionFixture.take(new RecordDecoder().json(value.noSpaces))))
    def _array_(value: Json, key: String): Vector[Json] = value.hcursor.get[Vector[Json]](key).toOption.get
    def _setup_(arguments: String*): Unit = SplitPhaseExplicitPartitionFixture.take(_run_setup_command_(
      Vector("--no-optional-locks", "--no-replace-objects", "-c", "core.hooksPath=/dev/null",
        "-c", "core.fsmonitor=false", "-c", "gc.auto=0") ++ arguments))
    if (mode == "PREPARE_OLD") {
      require(!Files.exists(episode), "Preserve any existing cutover episode; never replay old successes")
      reporter.Given("specification-only tiny primary and dependency with accepted Step1, then real Step2 check failure")
      val prefix = _accepted_prefix("canonical-source-cutover", overlap = true, sourceRevisionFixture = true)
      val selected = _adopt_prefix(prefix)
      _complete_prefix(selected, overlap = true, failCheck = true)
      val repairoutput = selected.scenario.advance(selected.task)
      val repair = repairoutput.business[V.GoalPhaseAdvanceResult]
      repair.work.get.kind shouldBe "RepairStep"
      repair.work.get.checkFailure.get.validation.outcome shouldBe Some("repair-required")
      repair.work.get.completed.map(_.step) shouldBe Vector("step-1")
      val scenario = selected.scenario
      val canonicalroot = Files.createDirectories(scenario.root.resolve("canonical-dependency"))
      val repository = canonicalroot.resolve("repository")
      _setup_("clone", "--no-local", "--", prefix.binding.root, repository.toString)
      TypedSbtHostPlanFixture.build(canonicalroot, true)
      _setup_("-C", repository.toString, "checkout", "-b", "canonical")
      Files.writeString(repository.resolve("value.txt"), "expected\n")
      Files.writeString(repository.resolve("inherited.txt"), "accepted first-Step input\n")
      Files.writeString(repository.resolve("current.txt"), "qualified canonical current input\n")
      Files.createDirectories(repository.resolve("src/main/scala"))
      Files.writeString(repository.resolve("src/main/scala/SourceRevisionMarker.scala"),
        "object SourceRevisionMarker { def source: String = " + repository.toRealPath().toString.asJson.noSpaces + " }\n")
      val module = "canonical-source-" + scenario.root.getFileName.toString
      Files.writeString(repository.resolve("build.sbt"), "\norganization := \"org.example.smworkflow.specification\"\n" +
        "name := " + module.asJson.noSpaces + "\nversion := \"0.0.1-SNAPSHOT\"\n" +
        "lazy val root = project.in(file(\".\"))\n" +
        "publishLocal := { val result = publishLocal.value; IO.append(file(\"../publication-count.txt\"), \"published\\n\"); result }\n",
        StandardOpenOption.APPEND)
      _setup_("-C", repository.toString, "add", "--", "build.sbt", "value.txt", "inherited.txt", "current.txt", "src/main/scala/SourceRevisionMarker.scala")
      _setup_("-C", repository.toString, "-c", "user.name=Canonical Source Specification", "-c", "user.email=canonical@example.invalid",
        "commit", "--no-verify", "--no-gpg-sign", "-m", "Specification-only committed canonical dependency")
      val provisional = prefix.binding.copy(revision = prefix.binding.revision + 1, root = repository.toRealPath().toString,
        branch = "canonical", dependency = prefix.binding.dependency.copy(workspace = "canonical-cncf-source"),
        paths = (prefix.first.changedPaths ++ prefix.binding.paths).distinct)
      val consumption = _initial_consumption(provisional)
      _configure_source_host(scenario, provisional, consumption)
      val native = _native_fixture(scenario)(RepositorySyncManagedGitObservation.observeInWorkspaceC(_,
        provisional.dependency.workspace))
      val binding = provisional.copy(base = O.Snapshot(native.head, native.tree))
      _configure_source_host(scenario, binding, consumption)
      val configfile = scenario.root.resolve(".textus/config.json")
      val config = parse(Files.readString(configfile)).toOption.get
      val process = parse(config.hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
      val publicationprogram = _array_(TypedSbtHostPlanFixture.plan(canonicalroot, TypedSbtHostPlanFixture.normalExecutable,
        Vector(1), operation = SbtOperation.PublishLocal, stdoutBytes = 1048576, stderrBytes = 131072), "sbtPrograms").head
        .mapObject(_.add("target", binding.dependency.workspace.asJson).add("identity", "specification-canonical-publish".asJson)
          .add("capability", "specification-canonical-publish".asJson))
      val sbt = _array_(process, "sbtPrograms") :+ publicationprogram
      val processes = process.mapObject(_.add("sbtPrograms", sbt.asJson).add("grants", Json.arr(Json.obj(
        "componentId" -> SmWorkflowComponent.componentId.name.asJson,
        "capabilities" -> (_array_(process, "gitPrograms") ++ sbt).map(_.hcursor.get[String]("capability").toOption.get).distinct.asJson))))
      val publicationtarget = LocalPublication.Target("specification-canonical-producer", binding.dependency.identity,
        binding.dependency.workspace)
      val collection = ThreeWorkflowNormalHostFixture.collection("local_publication")
      val storage = parse(config.hcursor.get[String]("textus.subsystem.storage.plan").toOption.get).toOption.get
      val storageplan = storage.mapObject(_.add("records", (_array_(storage, "records") :+ Json.obj(
        "componentId" -> SmWorkflowComponent.componentId.name.asJson, "storeName" -> "workflow".asJson,
        "collection" -> collection.asJson)).distinct.asJson))
      val publicationpolicy = Json.obj("storeName" -> "workflow".asJson, "collection" -> collection.asJson,
        "targets" -> Vector(publicationtarget).asJson)
      Files.writeString(configfile, config.mapObject(_.add("textus.subsystem.process-execution.plan", processes.noSpaces.asJson)
        .add("textus.subsystem.storage.plan", storageplan.noSpaces.asJson)
        .add(LocalPublicationRuntime.declaration.name, publicationpolicy.noSpaces.asJson)).noSpaces)
      reporter.When("the old installed product normally publishes and collects the independently committed canonical producer")
      val publicationinput = V.LocalPublicationInput("specification-canonical-published", publicationtarget.identity, None, true, None)
      scenario.callInstalled(executable, "LocalPublicationService", "ensureLocalPublication",
        Vector("--input", json(publicationinput.toRecord()).noSpaces), assemblyDescriptor = Some(scenario.root.resolve(".textus/assembly.yaml")))
        .business[V.LocalPublicationResult].status shouldBe "published"
      val collectedoutput = scenario.callInstalled(executable, "LocalPublicationService", "collectLocalPublication",
        Vector("--input", json(V.LocalPublicationCollectionInput(publicationinput.requestIdentity, publicationtarget.identity, None, true).toRecord()).noSpaces),
        assemblyDescriptor = Some(scenario.root.resolve(".textus/assembly.yaml")))
      val collected = collectedoutput.business[V.LocalPublicationResult]
      collected.status shouldBe "published"
      val publication = SplitPhaseExplicitPartitionFixture.take(GoalPhasePrerequisitePublicationUnknownRecovery.jsonC(collected.publication.get))
        .as[LocalPublication.Result].toOption.get
      val processresult = WorkflowSbtCapture.decode(publication.process.get).toOption.get
      val output = WorkflowSbtCapture.text(processresult.stdout).value.replaceAll("\u001b\\[[0-9;]*m", "")
      val jarprefix = "published " + module + "_2.12 to "
      val jars = output.linesIterator.map(_.trim.stripPrefix("[info] ").trim).filter(line =>
        line.startsWith(jarprefix) && line.endsWith("/jars/" + module + "_2.12.jar"))
        .map(line => Path.of(line.stripPrefix(jarprefix)).toAbsolutePath.normalize).toVector.distinct
      jars.size shouldBe 1
      Files.isRegularFile(jars.head) shouldBe true
      val publisherpolicy = _native_fixture(scenario) { core => org.goldenport.cncf.processexecution.GitDiffParameters.resolveC(core.executionContext)
        .map(maximum => io.circe.Printer.noSpaces.copy(sortKeys = true).print(Json.obj("processPlan" -> processes,
          "maximumDiffBytes" -> maximum.asJson, "validation" -> _host_(scenario).goal.stepValidation.asJson))) }
      reporter.Then("the actual old Repair and normal collect outputs are retained before source choice and product replacement")
      Files.writeString(episode, Json.obj("schemaVersion" -> "specification.canonical-source-episode.v1".asJson,
        "root" -> scenario.root.toString.asJson, "task" -> selected.task.toString.asJson,
        "original" -> json(selected.original.toRecord()), "resumed" -> json(selected.resumed.toRecord()),
        "repair" -> json(repair.toRecord()), "selection" -> selected.selection.asJson,
        "binding" -> binding.asJson, "consumption" -> consumption.asJson,
        "collectOutput" -> collectedoutput.invocation.stdout.toString.asJson, "artifact" -> jars.head.toString.asJson,
        "publisherProcessPolicy" -> publisherpolicy.asJson, "publicationObservedAt" -> Instant.now().toString.asJson,
        "oldPrimaryExecutions" -> _executions(scenario.root).asJson,
        "oldDependencyExecutions" -> _executions(scenario.root.resolve("dependency")).asJson,
        "oldComponent" -> component.asJson).noSpaces, StandardOpenOption.CREATE_NEW)
    } else {
      val saved = parse(Files.readString(episode)).toOption.get
      saved.hcursor.get[String]("schemaVersion").toOption.get shouldBe "specification.canonical-source-episode.v1"
      val root = Path.of(saved.hcursor.get[String]("root").toOption.get)
      val configfile = root.resolve(".textus/config.json")
      val config = parse(Files.readString(configfile)).toOption.get
      val retainedhost = SplitPhaseExplicitPartitionFixture.take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(
        parse(config.hcursor.get[String]("sm-workflow.runtime.definition").toOption.get).toOption.get))
      val plan = parse(Files.readString(root.resolve("goal-plan.json"))).toOption.get.as[O.Plan].toOption.get
      val logindex = sys.props.get("smworkflow.spec.canonicalResumeLogIndex") match {
        case None => 10000
        case Some(value) => value.toIntOption.filter(_ > 0).getOrElse(
          fail("canonicalResumeLogIndex must be an actual positive Int"))
      }
      val scenario = F.reopenCanonicalSource(root, retainedhost, plan, logindex)
      Files.writeString(configfile, config.mapObject(_.add("textus.component.file", component.asJson)).noSpaces)
      val task = Path.of(saved.hcursor.get[String]("task").toOption.get)
      val original = _advance_(saved.hcursor.get[Json]("original").toOption.get)
      val oldrepair = _advance_(saved.hcursor.get[Json]("repair").toOption.get)
      val selected = Selected(scenario, task, original, _advance_(saved.hcursor.get[Json]("resumed").toOption.get),
        saved.hcursor.get[D.Selection]("selection").toOption.get)
      val savedbinding = saved.hcursor.get[D.Binding]("binding").toOption.get
      val savedconsumption = saved.hcursor.get[R.Consumption]("consumption").toOption.get
      val orderedpaths = sys.props.get("smworkflow.spec.canonicalAcceptedPrefixReview").fold(savedbinding.paths) { value =>
        val reviewpath = Path.of(value)
        require(reviewpath.isAbsolute && reviewpath.normalize == reviewpath && Files.isRegularFile(reviewpath) &&
          reviewpath.toRealPath().getParent == root.resolve("logs").toRealPath(),
          "Select the actual public accepted-prefix Review file within this episode's logs")
        val firstreview = _advance_(parse(Files.readString(reviewpath)).toOption.get)
        val firstwork = firstreview.work.get
        firstreview.sessionId shouldBe original.sessionId
        firstreview.sessionId shouldBe oldrepair.sessionId
        firstwork.kind shouldBe "ReviewStep"
        firstwork.stepIdentity shouldBe Some("step-1")
        firstwork.selectedPlan shouldBe oldrepair.work.get.selectedPlan
        firstwork.originalBase shouldBe oldrepair.work.get.originalBase
        firstreview.validation.get.outcome shouldBe Some("passed")
        val first = _capture(firstreview)
        val currentcapture = GoalPhaseNativeMetadata.normalizePublicJson(json(
          oldrepair.work.get.checkFailure.get.validation.dependencyValidation.get))
          .flatMap(_.hcursor.get[D.Capture]("capture").left.map(_.message)).fold(fail(_), identity)
        val currentbinding = currentcapture.admission.binding
        val accepted = currentcapture.admission.acceptedPrefix.get
        first.session shouldBe original.sessionId
        first.step shouldBe "step-1"
        first.plan shouldBe ContextReference(firstwork.selectedPlan.identity, firstwork.selectedPlan.revision)
        first.primaryBase shouldBe O.Snapshot(firstwork.originalBase.head, firstwork.originalBase.tree)
        first.admission.acceptedPrefix shouldBe None
        first.admission.binding.step shouldBe first.step
        first.admission.binding.root shouldBe root.resolve("dependency/repository").toRealPath().toString
        first.admission.binding.root shouldBe currentbinding.root
        first.admission.binding.branch shouldBe currentbinding.branch
        first.admission.binding.base shouldBe currentbinding.base
        first.admission.binding.requester shouldBe currentbinding.requester
        first.admission.binding.dependency shouldBe currentbinding.dependency
        first.changedPaths.distinct shouldBe first.changedPaths
        first.changedPaths.toSet shouldBe first.admission.binding.paths.toSet
        val completed = oldrepair.work.get.completed.find(_.step == first.step).get
        completed.commit.parent shouldBe firstwork.candidate.get.head
        completed.commit.tree shouldBe firstwork.candidate.get.tree
        currentcapture.session shouldBe original.sessionId
        currentcapture.step shouldBe oldrepair.work.get.stepIdentity.get
        currentcapture.plan shouldBe first.plan
        currentcapture.primaryBase shouldBe first.primaryBase
        accepted.sessionId shouldBe first.session
        accepted.step shouldBe first.step
        accepted.plan shouldBe first.plan
        accepted.candidate shouldBe first.candidate
        accepted.paths shouldBe first.changedPaths
        currentbinding.identity shouldBe savedbinding.identity
        currentbinding.revision + 1 shouldBe savedbinding.revision
        currentbinding.step shouldBe savedbinding.step
        currentbinding.requester shouldBe savedbinding.requester
        currentbinding.dependency.identity shouldBe savedbinding.dependency.identity
        currentbinding.dependency.repository shouldBe savedbinding.dependency.repository
        currentbinding.checks shouldBe savedbinding.checks
        savedbinding.root shouldBe root.resolve("canonical-dependency/repository").toRealPath().toString
        (first.changedPaths ++ currentbinding.paths).distinct
      }
      orderedpaths.toSet shouldBe savedbinding.paths.toSet
      orderedpaths.distinct shouldBe orderedpaths
      savedconsumption.expectedModes.map(_.path).distinct shouldBe savedconsumption.expectedModes.map(_.path)
      savedconsumption.expectedModes.map(_.path).toSet shouldBe orderedpaths.toSet
      val binding = savedbinding.copy(paths = orderedpaths)
      val consumption = savedconsumption.copy(expectedModes = orderedpaths.map(path =>
        savedconsumption.expectedModes.find(_.path == path).get))
      consumption.expectedModes.toSet shouldBe savedconsumption.expectedModes.toSet
      _configure_source_host(scenario, binding, consumption)
      val collectedpath = Path.of(saved.hcursor.get[String]("collectOutput").toOption.get)
      val collected = SplitPhaseExplicitPartitionFixture.take(summon[ValueReader[V.LocalPublicationResult]].readC(
        SplitPhaseExplicitPartitionFixture.take(new RecordDecoder().json(Files.readString(collectedpath)))))
      val publication = SplitPhaseExplicitPartitionFixture.take(GoalPhasePrerequisitePublicationUnknownRecovery.jsonC(collected.publication.get))
        .as[LocalPublication.Result].toOption.get
      val current = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      current.issuedWorkContext shouldBe oldrepair.issuedWorkContext
      current.repairProgress shouldBe oldrepair.repairProgress
      val processpolicy = saved.hcursor.get[String]("publisherProcessPolicy").toOption.get
      val artifact = Path.of(saved.hcursor.get[String]("artifact").toOption.get)
      val observation = P.PublicationObservation(P.CollectionResult(collected.status, collected.reason, publication), P.collectOperation,
        ContextReference(collectedpath.toUri.toASCIIString, "1"), root.toUri.toASCIIString,
        saved.hcursor.get[String]("publicationObservedAt").toOption.get,
        processpolicy, artifact.getParent.getParent.toUri.toASCIIString, ContextReference(artifact.toUri.toASCIIString, "0.0.1-SNAPSHOT"))
      val source = R.Proposal(R.sourceSchema, ContextReference(binding.identity, binding.revision.toString), "committed", Vector.empty,
        Vector(R.Coverage(ContextReference("specification-dependency-approval", "1"), Vector("Validate the exact dependency candidate"), binding.checks.map(_.identity)),
          R.Coverage(ContextReference("specification-approval-step-2", "1"), Vector("Integrate with the genuinely accepted predecessor"), binding.checks.map(_.identity))), consumption)
      val delivery = ContextReference(episode.toUri.toASCIIString, "1")
      val proposal = C.Proposal(C.proposalSchema, source, observation, delivery, Some(C.WorkerLifecycle(true, true)))
      val descriptioninput = V.GoalPhaseDependencySourceRevisionDescriptionInput(original.sessionId, GoalPhaseNativeMetadata.record(proposal.asJson))
      reporter.When("the current product describes the exact same native Repair using independently declared committed canonical source")
      val described = scenario.call("command", R.describeOperation, Vector("--input", json(descriptioninput.toRecord()).noSpaces))
      val description = described.business[V.GoalPhaseDependencySourceRevisionDescriptionResult]
      val target = SplitPhaseExplicitPartitionFixture.take(R.targetC(description.target))
      target.completed.map(_.step) shouldBe Vector("step-1")
      target.canonicalOrigin.get.historicalFailure should not be empty
      val reference = V.WorkflowArtifactReference("specification-canonical-source-human", "1")
      val input = V.GoalPhaseDependencySourceRevisionAdoptionInput(original.sessionId, description.target, C.pendingChoice,
        "SPECIFICATION-ONLY prior worker ended; accept unknown prior external effects and select this exact canonical source", reference)
      def _adopt_(): F.Result = scenario.call("command", R.adoptionOperation, Vector("--input", json(input.toRecord()).noSpaces))
      _adopt_().exitCode should not be 0
      val execution = V.WorkflowExecutionEvidence(Vector(reference,
        V.WorkflowArtifactReference(observation.recollectionReference.identity, observation.recollectionReference.revision),
        V.WorkflowArtifactReference(observation.artifact.identity, observation.artifact.revision),
        V.WorkflowArtifactReference(delivery.identity, delivery.revision)), Some("participant"), None, None)
      val evidence = V.WorkflowEvidencePreparationInput("GoalPhase", "dependency-source-revision", described.invocation.stdout.toUri.toASCIIString,
        _save(scenario, "canonical-human-answer", json(input.toRecord()).noSpaces),
        _save(scenario, "canonical-human-reference", json(reference.toRecord()).noSpaces),
        _save(scenario, "canonical-human-execution", json(execution.toRecord()).noSpaces), "normal-development-host",
        Instant.now().toString, "entity:smworkflow.cli.host_evidence", Some("participant"))
      val prepared = scenario.prepareHostInput("prepareEvidence", evidence.toRecord())
      prepared.exitCode shouldBe 0
      scenario.selectPreparedImport(prepared)
      _adopt_().business[V.GoalPhaseDependencySourceRevisionAdoptionResult].accepted shouldBe true
      _adopt_().business[V.GoalPhaseDependencySourceRevisionAdoptionResult].accepted shouldBe true
      reporter.Then("source selection is passive and old checks are retained without replay")
      _executions(root) shouldBe saved.hcursor.get[Vector[String]]("oldPrimaryExecutions").toOption.get
      _executions(root.resolve("dependency")) shouldBe saved.hcursor.get[Vector[String]]("oldDependencyExecutions").toOption.get
      val next = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      next.work.get.kind shouldBe "RepairStep"
      next.issuedWorkContext.get.continuationId should not be oldrepair.issuedWorkContext.get.continuationId
      next.issuedWorkContext.get.expectedRevision.toLong should be > oldrepair.issuedWorkContext.get.expectedRevision.toLong
      next.work.get.selectedPlan shouldBe oldrepair.work.get.selectedPlan
      next.work.get.originalBase shouldBe oldrepair.work.get.originalBase
      next.repairProgress shouldBe oldrepair.repairProgress
      val dispositions = next.work.get.issues.toVector.flatMap(_.issues).filter(_.status != "RESOLVED").map(issue =>
        V.GoalPhaseIssueDisposition(issue.identity, "FIXED", "SPECIFICATION-ONLY committed canonical correction", None))
      _submit(scenario, next, Json.obj("outcome" -> "completed".asJson, "summary" -> "SPECIFICATION-ONLY canonical repair".asJson,
        "findings" -> Json.arr(), "issueReport" -> json(V.GoalPhaseIssueReport(dispositions).toRecord())),
        V.WorkflowArtifactReference("specification-canonical-repair-result", "1")).business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val reviewed = scenario.advance(task)
      val review = reviewed.business[V.GoalPhaseAdvanceResult]
      review.work.get.kind shouldBe "ReviewStep"
      review.validation.get.outcome shouldBe Some("passed")
      review.repairProgress shouldBe oldrepair.repairProgress
      val validation = GoalPhaseNativeMetadata.normalizePublicJson(json(review.work.get.review.get.dependencyValidations.last)).toOption.get
      val capture = validation.hcursor.get[R.Capture]("capture").toOption.get
      capture.schemaVersion shouldBe R.canonicalCaptureSchema
      capture.stagedPaths shouldBe empty
      R.bindingOf(capture) shouldBe binding
      capture.direction.target shouldBe target
      _executions(root.resolve("dependency")) shouldBe saved.hcursor.get[Vector[String]]("oldDependencyExecutions").toOption.get
      Files.readString(root.resolve("canonical-dependency/publication-count.txt")).linesIterator.toVector shouldBe Vector("published")
      reporter.When("a separate independent fixture review and ordinary Step closure accept the new checked candidate")
      val closed = _close_step(selected, reviewed, "canonical-source-current").business[V.GoalPhaseAdvanceResult]
      reporter.Then("both genuine accepted Steps and original base survive; source choice has adopted no consumer artifact")
      closed.sessionId shouldBe original.sessionId
      closed.work.get.completed.map(_.step) shouldBe Vector("step-1", "step-2")
      closed.work.get.originalBase shouldBe original.work.get.originalBase
      _submit(scenario, oldrepair, Json.obj("outcome" -> "completed".asJson,
        "summary" -> "SPECIFICATION-ONLY late obsolete result".asJson, "findings" -> Json.arr()),
        V.WorkflowArtifactReference("specification-late-old-repair", "1")).exitCode should not be 0
      Files.writeString(episode.resolveSibling(episode.getFileName.toString + ".result.json"), Json.obj(
        "sessionId" -> original.sessionId.asJson, "sourceDirection" -> reference.identity.asJson,
        "captureSchema" -> capture.schemaVersion.asJson, "closed" -> json(closed.toRecord()),
        "consumerArtifactAdoption" -> "not-performed".asJson).noSpaces, StandardOpenOption.CREATE_NEW)
    }
    reporter.Then("the explicit test-owned cutover locator is " + episode.toUri.toASCIIString)
    episode
  }

  def qualifyInitialSource(reporter: GivenWhenThen): Path = {
      import GoalPhaseDependencySourceRevision as R
      import org.goldenport.convert.ValueReader
      import org.goldenport.record.io.RecordDecoder
      val mode = sys.props.getOrElse("smworkflow.spec.initialSourceMode", "continue-new")
      val episode = Path.of(sys.props.getOrElse("smworkflow.spec.initialSourceEpisode",
        cancel("Select the explicit caller-owned smworkflow.spec.initialSourceEpisode locator file"))).toAbsolutePath.normalize
      val checkpoint = episode.resolveSibling(episode.getFileName.toString + ".capture.json")
      require(Set("capture-old", "capture-old-resume", "continue-new", "continue-new-review", "continue-new-closed")(mode),
        "Select capture-old, capture-old-resume, continue-new, continue-new-review or continue-new-closed")
      require(sys.props.contains("smworkflow.spec.installedCommand") && sys.props.contains("smworkflow.spec.componentFile"),
        "This before/after leaf requires the actual installed executable and its normally published component, with no source fallback")
      def _host_(scenario: F.Scenario): ThreeWorkflowRuntimeParameters.Definition = {
        val config = parse(Files.readString(scenario.root.resolve(".textus/config.json"))).toOption.get
        SplitPhaseExplicitPartitionFixture.take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(
          parse(config.hcursor.get[String]("sm-workflow.runtime.definition").toOption.get).toOption.get))
      }
      def _advance_(value: Json): V.GoalPhaseAdvanceResult = SplitPhaseExplicitPartitionFixture.take(
        summon[ValueReader[V.GoalPhaseAdvanceResult]].readC(SplitPhaseExplicitPartitionFixture.take(new RecordDecoder().json(value.noSpaces))))
      def _proposal_(selected: Selected, binding: D.Binding): R.Proposal = R.Proposal(R.sourceSchema,
        ContextReference(binding.identity, binding.revision.toString), "selected-changes", Vector.empty,
        Vector(R.Coverage(selected.selection.approvals.head.decision, Vector("Validate the exact dependency candidate"),
          binding.checks.map(_.identity))), _initial_consumption(binding))
      def _describe_(selected: Selected, value: R.Proposal): F.Result = {
        val input = V.GoalPhaseDependencySourceRevisionDescriptionInput(selected.original.sessionId, GoalPhaseNativeMetadata.record(value.asJson))
        selected.scenario.call("command", R.describeOperation, Vector("--input", json(input.toRecord()).noSpaces))
      }
      def _read_public_capture_(request: V.GoalPhaseAdvanceResult): (R.Capture, Vector[D.Execution]) = {
        val review = request.work.flatMap(_.review).getOrElse(fail("Actual ReviewStep has no public review context"))
        val actual = review.dependencyValidations.lastOption.getOrElse(fail("Actual ReviewStep has no dependency validation record"))
        val validation = GoalPhaseNativeMetadata.normalizePublicJson(json(actual))
          .fold(reason => fail("Actual public dependency validation: " + reason), identity)
        val rawcapture = validation.hcursor.get[Json]("capture")
          .fold(error => fail("Actual public Capture: " + error.message), identity)
        val direction = rawcapture.hcursor.get[Json]("direction")
          .fold(error => fail("Actual public Capture Direction: " + error.message), identity)
        val fragment = direction.hcursor.get[Json]("target")
          .fold(error => fail("Actual public Capture target: " + error.message), identity)
        val target = R.targetC(GoalPhaseNativeMetadata.record(fragment))
          .fold(error => fail("Actual public Capture target: " + error.toString), identity)
        val canonical = rawcapture.mapObject(_.add("direction", direction.mapObject(_.add("target", target.asJson))))
        val capture = canonical.as[R.Capture].fold(error => fail("Actual Capture decoder: " + error.message), identity)
        val executions = validation.hcursor.get[Vector[D.Execution]]("executions")
          .fold(error => fail("Actual Capture executions: " + error.message), identity)
        (capture, executions)
      }
      def _post_closure_audit_(selected: Selected, closed: V.GoalPhaseAdvanceResult, capture: R.Capture,
        old: GoalPhaseCandidateRecovery.RecoveryTarget, oldsnapshot: Map[String, String],
        previous: V.GoalPhaseAdvanceResult, primarybytes: Vector[Byte], bytecontext: String): Unit = {
        import org.goldenport.cncf.workflow.{ContinuationRecordJsonV1, ContinuationRuntimePersistence}
        val scenario = selected.scenario
        val target = capture.direction.target
        val primary = Path.of(_host_(scenario).target.root)
        reporter.Then("the actual closed Step retains its original base, counters and primary bytes from the " + bytecontext + " observation")
        closed.kind shouldBe "Request"
        closed.sessionId shouldBe target.sessionId
        closed.work.get.kind shouldBe "ImplementStep"
        closed.work.get.stepIdentity shouldBe Some("step-2")
        closed.work.get.completed.map(_.step) shouldBe Vector("step-1")
        closed.work.get.originalBase shouldBe selected.original.work.get.originalBase
        closed.repairProgress shouldBe previous.repairProgress
        Files.readAllBytes(primary.resolve(sourcePath)).toVector shouldBe primarybytes
        val (git, index) = _native_fixture(scenario) { core => for {
          observed <- RepositorySyncManagedGitObservation.observeC(core)
          tree <- RepositorySyncManagedGitObservation.indexTreeC(core)
        } yield (observed, tree) }
        val commit = closed.work.get.completed.last.commit
        git.head shouldBe commit.identity
        git.tree shouldBe commit.tree
        index shouldBe commit.tree
        _executions(scenario.root) should have size 1
        _executions(scenario.root.resolve("dependency")) shouldBe Vector("selected-test-only")
        val after = _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, target.sessionId)).toMap
        reporter.Then("only the exact source-frontier Human is superseded while its issued meaning and all other original rows stay unchanged")
        def _continuation_row_(bytes: String): (Json, ContinuationRuntimePersistence.Record) = {
          val record = SplitPhaseExplicitPartitionFixture.take(new RecordDecoder().json(bytes))
          val payload = record.getString("continuationPayload").getOrElse(fail("Native Human row lacks its continuation payload"))
          payload should startWith("cncf.workflow-json.v1:")
          val decoded = ContinuationRecordJsonV1.decodeC(payload.stripPrefix("cncf.workflow-json.v1:"))
            .fold(error => fail("Native Human continuation decoder: " + error.toString), identity)
          val row = parse(bytes).fold(error => fail("Native Human row JSON: " + error.message), identity)
          (row, decoded)
        }
        val question = target.questionIdentity
        val (originalrow, original) = _continuation_row_(oldsnapshot.getOrElse(question,
          fail("Original snapshot lacks the exact source-frontier Human: " + question)))
        val (currentrow, current) = _continuation_row_(after.getOrElse(question,
          fail("Current snapshot lacks the exact source-frontier Human: " + question)))
        original.continuation.continuationId.value shouldBe question
        original.continuation.runId.value shouldBe target.sessionId
        original.status shouldBe ContinuationRuntimePersistence.Status.Available
        current.status shouldBe ContinuationRuntimePersistence.Status.Superseded
        current.continuation shouldBe original.continuation
        current.claimId shouldBe original.claimId
        current.copy(status = ContinuationRuntimePersistence.Status.Available) shouldBe original
        val revision = originalrow.hcursor.get[Long]("cncf_revision")
          .fold(error => fail("Original Human concurrency revision: " + error.message), identity)
        val superseded = ContinuationRecordJsonV1.encodeC(original.copy(status = ContinuationRuntimePersistence.Status.Superseded))
          .fold(error => fail("Superseded Human continuation encoder: " + error.toString), identity)
        val expectedrow = originalrow.mapObject(_.add("cncf_revision", (revision + 1L).asJson)
          .add("continuationPayload", ("cncf.workflow-json.v1:" + superseded).asJson))
        reporter.Then("the appended native decision-supersession receipt binds only this exact source Direction and contiguous transition")
        originalrow.hcursor.downField("decisionSupersessionPayload").focus shouldBe None
        val payload = currentrow.hcursor.get[String]("decisionSupersessionPayload")
          .fold(error => fail("Native decision-supersession receipt: " + error.message), identity)
        payload should startWith("cncf.workflow-json.v1:")
        val envelope = parse(payload.stripPrefix("cncf.workflow-json.v1:"))
          .fold(error => fail("Native decision-supersession envelope: " + error.message), identity)
        envelope.asObject.map(_.keys.toSet) shouldBe Some(Set("schemaVersion", "identity", "sourceRevision",
          "nextRevision", "typeIdentity", "payload"))
        envelope.hcursor.get[String]("schemaVersion")
          .fold(error => fail("Native decision-supersession schema: " + error.message), identity) shouldBe
          "cncf.workflow-decision-supersession.v1"
        envelope.hcursor.get[String]("identity")
          .fold(error => fail("Native decision-supersession identity: " + error.message), identity) shouldBe question
        envelope.hcursor.get[String]("sourceRevision")
          .fold(error => fail("Native decision-supersession source revision: " + error.message), identity) shouldBe target.pendingRevision
        val nextrevision = envelope.hcursor.get[String]("nextRevision")
          .fold(error => fail("Native decision-supersession next revision: " + error.message), identity)
        nextrevision.toLongOption.exists(value => value > 0 && value.toString == nextrevision) shouldBe true
        nextrevision shouldBe (BigInt(target.pendingRevision) + 1).toString
        envelope.hcursor.get[String]("typeIdentity")
          .fold(error => fail("Native decision-supersession payload type: " + error.message), identity) shouldBe
          GoalPhaseLegacyPurposeRepairContinuation.storageCodec.typeIdentity
        val actualpayload = envelope.hcursor.get[Json]("payload")
          .fold(error => fail("Native decision-supersession payload: " + error.message), identity)
        val disposition = GoalPhaseLegacyPurposeRepairContinuation.storageCodec.decode(actualpayload)
          .fold(reason => fail("Native decision-supersession disposition: " + reason), identity)
        disposition shouldBe GoalPhaseLegacyPurposeRepairContinuation.DependencySourceRevision(capture.direction)
        currentrow.mapObject(_.remove("decisionSupersessionPayload")) shouldBe expectedrow
        oldsnapshot.filterNot((key, _) => key == "instance" || key == question)
          .foreach { (key, row) => after.get(key) shouldBe Some(row) }
        def _candidate_key_(key: String): String = SplitPhaseExplicitPartitionFixture.take(org.goldenport.cncf.workflow.WorkflowMechanicalTransition.keyC(
          org.goldenport.cncf.workflow.WorkflowMechanicalTransition.Reference(target.sessionId + ":ordered-candidate:" + capture.sourceRevision + ":" + key, "1")))
        after.get(_candidate_key_("initial-source-primary-effects")) should not be empty
        after.get(_candidate_key_("stage-intent-0")) shouldBe None
        after.get(_candidate_key_("stage-result-0")) shouldBe None
        after.get(_candidate_key_("source-history-intent-0")) shouldBe None
        after.get(_candidate_key_("source-history-result-0")) shouldBe None
        old.oldObservationOutcome shouldBe "UNKNOWN"
        old.oldObservationTermination shouldBe "termination-unconfirmed"
      }
      def _finish_review_(selected: Selected, reviewed: F.Result, target: R.Target,
        old: GoalPhaseCandidateRecovery.RecoveryTarget, oldsnapshot: Map[String, String],
        previous: V.GoalPhaseAdvanceResult, primarybytes: Vector[Byte], bytecontext: String): Unit = {
        val scenario = selected.scenario
        val root = scenario.root
        val primary = Path.of(_host_(scenario).target.root)
        val request = reviewed.business[V.GoalPhaseAdvanceResult]
        reporter.Then("the actual pending Step review contains passed fresh checks for the exact adopted v8 capture")
        request.kind shouldBe "Request"
        request.work.get.kind shouldBe "ReviewStep"
        request.validation.get.outcome shouldBe Some("passed")
        val (capture, executions) = _read_public_capture_(request)
        capture.schemaVersion shouldBe R.initialCaptureSchema
        capture.direction.target shouldBe target
        R.bindingOf(capture) shouldBe target.binding
        executions.map(_.operation) shouldBe Vector("compile", "test-only")
        _executions(root) should have size 1
        _executions(root.resolve("dependency")) shouldBe Vector("selected-test-only")
        reporter.Then("current primary bytes match the actual " + bytecontext + " observation before closure")
        Files.readAllBytes(primary.resolve(sourcePath)).toVector shouldBe primarybytes
        reporter.When("the independent fixture reviewer accepts the new candidate and ordinary Step closure commits it")
        val closed = _close_step(selected, reviewed, "initial-source-current-candidate").business[V.GoalPhaseAdvanceResult]
        _post_closure_audit_(selected, closed, capture, old, oldsnapshot, previous, primarybytes, bytecontext)
      }
      def _next_log_index_(root: Path, minimum: Int): Int = {
        import scala.jdk.CollectionConverters.*
        val pattern = "([0-9]+)\\.(?:stdout\\.json|stderr)".r
        val entries = Files.list(root.resolve("logs"))
        try {
          val maximum = entries.iterator.asScala.map(_.getFileName.toString).collect {
            case pattern(index) => index
          }.foldLeft(minimum - 1) { (previous, index) =>
            val actual = index.toIntOption.getOrElse(fail("Owned fixture log index exceeds the Int allocation API"))
            math.max(previous, actual)
          }
          require(maximum < Int.MaxValue, "Owned fixture log index cannot advance beyond the Int allocation API")
          maximum + 1
        } finally entries.close()
      }
      def _finish_capture_(selected: Selected, recovery: GoalPhaseCandidateRecovery.RecoveryTarget,
        revised: V.GoalPhaseAdvanceResult, oldstdout: String, oldstderr: String): Unit = {
        val scenario = selected.scenario
        val summary = "SPECIFICATION-OWNED diagnostic: preserve current primary and choose a higher dependency base"
        reporter.Given("the retained actual ReviseWork request and its completed old primary effects, with no dependency prefix")
        reporter.Then("the retained UNKNOWN prefix and check logs remain unchanged before the remaining capture actions")
        recovery.dependencyPrefix shouldBe empty
        recovery.historyPrefix should have size 1
        recovery.stagingPrefix.map(_.result.stagedPaths.size) shouldBe Vector(1, 2)
        recovery.oldObservationOutcome shouldBe "UNKNOWN"
        recovery.oldObservationTermination shouldBe "termination-unconfirmed"
        _executions(scenario.root) shouldBe empty
        _executions(scenario.root.resolve("dependency")) shouldBe empty
        reporter.When("one ordinary installed Advance retrieves the retained revision request or its exact diagnostic Human frontier")
        val current = scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
        reporter.Then("the current public result belongs to the exact retained fixture Session")
        current.sessionId shouldBe revised.sessionId
        val human = current.kind match {
          case "Request" =>
            reporter.Then("the retained implementation request has the saved Plan, Step and issued-work key")
            current.work.get.kind shouldBe "ImplementStep"
            current.work.get.selectedPlan shouldBe revised.work.get.selectedPlan
            current.work.get.originalBase shouldBe revised.work.get.originalBase
            current.work.get.stepIdentity shouldBe revised.work.get.stepIdentity
            current.submitSessionId shouldBe revised.submitSessionId
            current.submitOperation shouldBe revised.submitOperation
            current.issuedWorkContext.get.continuationId shouldBe revised.issuedWorkContext.get.continuationId
            current.issuedWorkContext.get.expectedRevision shouldBe revised.issuedWorkContext.get.expectedRevision
            val diagnostic = Json.obj("outcome" -> "decision-needed".asJson, "summary" -> summary.asJson,
              "findings" -> Json.arr())
            reporter.When("the retained issued worker submits the corrected fixture diagnostic once, omitting completed-work dependencyValidation")
            val submitted = _submit(scenario, current, diagnostic, V.WorkflowArtifactReference("initial-source-diagnostic", "1"))
              .business[V.GoalPhaseWorkSubmissionResult]
            reporter.Then("the diagnostic semantic result is accepted while the native owner retains the standing admission")
            submitted.accepted shouldBe true
            reporter.When("a separate installed Advance exposes the accepted diagnostic's unanswered frontier")
            scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
          case "Human" =>
            reporter.Then("the already issued Human frontier is the exact fixture diagnostic, so Submit is not repeated")
            current.human.get.requestingResult.outcome shouldBe "decision-needed"
            current.human.get.requestingResult.summary shouldBe summary
            current.human.get.work.selectedPlan shouldBe revised.work.get.selectedPlan
            current.human.get.work.originalBase shouldBe revised.work.get.originalBase
            current.human.get.work.stepIdentity shouldBe revised.work.get.stepIdentity
            current
          case other => fail("Initial source capture cannot resume the unexpected public frontier: " + other)
        }
        reporter.Then("the latest unanswered frontier requires a Human Decision for that exact diagnostic")
        human.kind shouldBe "Human"
        human.sessionId shouldBe selected.original.sessionId
        human.human.get.requestingResult.summary shouldBe summary
        val before = recovery.inspection.dependencyAdmission.get.binding
        val actual = _native_fixture(scenario)(RepositorySyncManagedGitObservation.observeInWorkspaceC(_, before.dependency.workspace))
        val replacement = before.copy(revision = 2, base = GoalPhaseOrderedProgression.Snapshot(actual.head, actual.tree))
        _configure_source_host(scenario, replacement, _initial_consumption(replacement), Vector("fixture.SelectedDependencyCheck"))
        reporter.When("the old installed CLI describes the independently declared successor at that frontier")
        val faileddescription = _describe_(selected, _proposal_(selected, replacement))
        reporter.Then("the old description refuses instead of inventing a predecessor Guard")
        faileddescription.exitCode should not be 0
        val snapshot = _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, selected.original.sessionId))
        reporter.Then("the actual old failed episode, exact unanswered frontier and immutable native records are retained for the runtime switch")
        _executions(scenario.root) shouldBe empty
        _executions(scenario.root.resolve("dependency")) shouldBe empty
        val receipt = Json.obj("root" -> scenario.root.toString.asJson, "task" -> selected.task.toString.asJson,
          "original" -> json(selected.original.toRecord()), "resumed" -> json(selected.resumed.toRecord()),
          "selection" -> selected.selection.asJson, "human" -> json(human.toRecord()), "recovery" -> recovery.asJson,
          "proposal" -> _proposal_(selected, replacement).asJson,
          "oldFailureStdout" -> oldstdout.asJson, "oldFailureStderr" -> oldstderr.asJson,
          "oldDescriptionStderr" -> faileddescription.invocation.stderr.toString.asJson,
          "snapshot" -> Json.obj(snapshot.map((key, value) => key -> value.asJson)*))
        reporter.When("the caller retains the actual old-runtime episode only after the remaining capture actions succeed")
        Files.writeString(episode, receipt.noSpaces, StandardOpenOption.CREATE_NEW)
      }
      if (mode == "capture-old") {
        reporter.Given("an explicit old installed CLI and two small specification-owned repositories, before any Session exists")
        reporter.Then("the caller-selected episode locator has not been used")
        Files.exists(episode) shouldBe false
        Files.exists(checkpoint) shouldBe false
        reporter.When("a fresh installed Start selects the small Plan and independently approved dependency work")
        val selected = _selected("p611-initial-pre-capture-source", sourcerevisionfixture = true, beforestart = _initial_source_build)
        val scenario = selected.scenario
        val primary = Path.of(_host_(scenario).target.root)
        Files.writeString(primary.resolve(sourcePath), "/**\n * @since   Oct.  1, 2026\n * @version Oct.  2, 2026\n */\nobject HistoryExample { val value = 42 }\n")
        _complete(selected)
        val before = _host_(scenario).goal.dependencyValidation.get
        val dependency = Path.of(before.root)
        Files.writeString(dependency.resolve("unrelated-base.txt"), "Independent committed input outside the selected pending paths\n")
        def _setup_(arguments: String*): Unit = SplitPhaseExplicitPartitionFixture.take(_run_setup_command_(Vector("-C", dependency.toString,
          "-c", "core.hooksPath=/dev/null", "-c", "user.name=Independent Fixture Owner",
          "-c", "user.email=independent@example.invalid") ++ arguments))
        _setup_("add", "--", "unrelated-base.txt")
        _setup_("commit", "--no-verify", "--no-gpg-sign", "-m", "Advance unrelated dependency base")
        reporter.When("the old installed Advance reaches the changed base after completing primary normalization and staging")
        val failed = scenario.advance(selected.task)
        reporter.Then("the changed old dependency base refuses the candidate attempt")
        failed.exitCode should not be 0
        reporter.When("the old installed recovery operation describes the actual failed candidate prefix")
        val described = _describe_recovery(selected)
        val recoverydescription = described.business[V.GoalPhaseCandidateRecoveryDescriptionResult]
        val recovery = SplitPhaseExplicitPartitionFixture.take(GoalPhaseCandidateRecovery.targetC(recoverydescription.target))
        reporter.Then("primary normalization and staging are complete and no dependency effect has begun")
        recovery.dependencyPrefix shouldBe empty
        recovery.historyPrefix should have size 1
        recovery.stagingPrefix.map(_.result.stagedPaths.size) shouldBe Vector(1, 2)
        reporter.When("the independent fixture Human selects ReviseWork for that exact failed prefix")
        val recoveryoutput = _recover(selected, described, "ReviseWork")
        val recovered = recoveryoutput.business[V.GoalPhaseCandidateRecoveryResult]
        reporter.Then("the original recovery direction is accepted")
        recovered.accepted shouldBe true
        reporter.When("a separate installed Advance issues the same-Step revision work")
        val revised = scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
        reporter.Then("the revision remains ordinary implementation work")
        revised.work.get.kind shouldBe "ImplementStep"
        val capture = Json.obj("schemaVersion" -> "p611-initial-source-fixture-capture.v1".asJson,
          "stage" -> "after-revise-before-diagnostic".asJson, "root" -> scenario.root.toString.asJson,
          "task" -> selected.task.toString.asJson, "original" -> json(selected.original.toRecord()),
          "resumed" -> json(selected.resumed.toRecord()), "revised" -> json(revised.toRecord()),
          "recovery" -> json(recoverydescription.target),
          "oldFailureStdout" -> failed.invocation.stdout.toString.asJson,
          "oldFailureStderr" -> failed.invocation.stderr.toString.asJson,
          "reviseAcceptedResponse" -> recoveryoutput.invocation.stdout.toString.asJson)
        reporter.When("the actual public revision request is checkpointed before diagnostic submission")
        Files.writeString(checkpoint, capture.noSpaces, StandardOpenOption.CREATE_NEW)
        _finish_capture_(selected, recovery, revised, failed.invocation.stdout.toString, failed.invocation.stderr.toString)
      } else if (mode == "capture-old-resume") {
        reporter.Given("the caller-selected public checkpoint of the same physical old-runtime fixture after accepted ReviseWork")
        require(Files.isRegularFile(checkpoint), "Select the actual retained .capture.json public-response checkpoint")
        require(!Files.exists(episode), "A completed episode must use continue-new instead of repeating capture")
        val capture = parse(Files.readString(checkpoint)).fold(error => fail(error.message), identity)
        require(capture.hcursor.get[String]("schemaVersion").contains("p611-initial-source-fixture-capture.v1") &&
          capture.hcursor.get[String]("stage").contains("after-revise-before-diagnostic"), "Checkpoint schema or capture stage differs")
        val root = Path.of(capture.hcursor.get[String]("root").toOption.get)
        val task = Path.of(capture.hcursor.get[String]("task").toOption.get)
        val config = parse(Files.readString(root.resolve(".textus/config.json"))).toOption.get
        val definition = SplitPhaseExplicitPartitionFixture.take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(
          parse(config.hcursor.get[String]("sm-workflow.runtime.definition").toOption.get).toOption.get))
        val plan = parse(Files.readString(root.resolve("goal-plan.json"))).toOption.get.as[GoalPhaseOrderedProgression.Plan].toOption.get
        val recovery = SplitPhaseExplicitPartitionFixture.take(GoalPhaseCandidateRecovery.targetC(
          GoalPhaseNativeMetadata.record(capture.hcursor.downField("recovery").focus.get)))
        val original = _advance_(capture.hcursor.downField("original").focus.get)
        val resumed = _advance_(capture.hcursor.downField("resumed").focus.get)
        val revised = _advance_(capture.hcursor.downField("revised").focus.get)
        require(recovery.sessionId == original.sessionId && resumed.sessionId == original.sessionId &&
          revised.sessionId == original.sessionId && recovery.controller.plan == plan && recovery.root == definition.target.root &&
          original.kind == "Request" && resumed.kind == "Request" &&
          original.work.exists(_.selectedPlan == F.publicPlan(plan)) &&
          resumed.work.exists(_.selectedPlan == F.publicPlan(plan)) && revised.work.exists(work =>
            work.kind == "ImplementStep" && work.selectedPlan == F.publicPlan(plan) &&
              work.originalBase == original.work.get.originalBase && work.stepIdentity == resumed.work.get.stepIdentity) &&
          revised.kind == "Request" && revised.issuedWorkContext.nonEmpty &&
          recovery.controller.base.head == original.work.get.originalBase.head &&
          recovery.controller.base.tree == original.work.get.originalBase.tree,
          "Checkpoint public Session, Plan, primary root/base or revision-work linkage differs")
        val admission = recovery.inspection.dependencyAdmission.get
        val selection = D.Selection(ContextReference(admission.binding.identity, admission.binding.revision.toString),
          admission.approvals.map(approval => D.ApprovalReference(
            ContextReference(approval.proposal.identity, approval.proposal.revision), approval.decision)))
        val scenario = F.reopenInstalledRepository(root, definition, plan, _next_log_index_(root, 1000))
        require(scenario.retrieve(task) == recovery.sessionId, "Checkpoint task routes to another actual Session")
        val selected = Selected(scenario, task, original, resumed, selection)
        reporter.Then("the checkpoint reopens the same actual Session and Plan without Start, prefix effects or ReviseWork replay")
        scenario.root shouldBe root
        _finish_capture_(selected, recovery, revised, capture.hcursor.get[String]("oldFailureStdout").toOption.get,
          capture.hcursor.get[String]("oldFailureStderr").toOption.get)
      } else {
        reporter.Given("the same retained old-runtime Session and an explicitly selected new normally published installed CLI")
        val receipt = parse(Files.readString(episode)).toOption.get
        val root = Path.of(receipt.hcursor.get[String]("root").toOption.get)
        val configfile = root.resolve(".textus/config.json")
        val config = parse(Files.readString(configfile)).toOption.get
        val definition = SplitPhaseExplicitPartitionFixture.take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(
          parse(config.hcursor.get[String]("sm-workflow.runtime.definition").toOption.get).toOption.get))
        val plan = parse(Files.readString(root.resolve("goal-plan.json"))).toOption.get.as[GoalPhaseOrderedProgression.Plan].toOption.get
        val scenario = F.reopenInstalledRepository(root, definition, plan, _next_log_index_(root, 10000))
        // Only the normal component loading selection changes at the runtime switch.
        Files.writeString(configfile, config.mapObject(_.add("textus.component.file", sys.props("smworkflow.spec.componentFile").asJson)).noSpaces)
        val selected = Selected(scenario, Path.of(receipt.hcursor.get[String]("task").toOption.get),
          _advance_(receipt.hcursor.downField("original").focus.get), _advance_(receipt.hcursor.downField("resumed").focus.get),
          receipt.hcursor.get[D.Selection]("selection").toOption.get)
        val old = receipt.hcursor.get[GoalPhaseCandidateRecovery.RecoveryTarget]("recovery").toOption.get
        val value = receipt.hcursor.get[R.Proposal]("proposal").toOption.get
        val oldsnapshot = receipt.hcursor.get[Map[String, String]]("snapshot").toOption.get
        if (mode == "continue-new-review") {
          import org.goldenport.cncf.workflow.WorkflowInstanceRecordJsonV1
          reporter.Given("the actual saved public ReviewStep response for this same fixture after successful native v8 checks")
          val reviewpath = Path.of(sys.props.getOrElse("smworkflow.spec.initialSourceReview",
            fail("Select the actual saved public ReviewStep response with smworkflow.spec.initialSourceReview")))
          require(reviewpath.isAbsolute && reviewpath.normalize == reviewpath &&
            reviewpath.getParent == root.resolve("logs") && Files.isRegularFile(reviewpath) && reviewpath.toRealPath() == reviewpath,
            "Select an actual regular absolute ReviewStep response directly under this same fixture's logs")
          val saved = _advance_(parse(Files.readString(reviewpath)).fold(error => fail(error.message), identity))
          reporter.Then("the saved response identifies the passed pending review for the original Session, Plan, primary base and Step")
          saved.kind shouldBe "Request"
          saved.sessionId shouldBe selected.original.sessionId
          saved.work.get.kind shouldBe "ReviewStep"
          saved.work.get.selectedPlan shouldBe F.publicPlan(plan)
          saved.work.get.originalBase shouldBe selected.original.work.get.originalBase
          saved.work.get.stepIdentity shouldBe Some(old.inspection.step)
          saved.validation.get.outcome shouldBe Some("passed")
          saved.issuedWorkContext should not be empty
          val issued = saved.issuedWorkContext.get
          issued.runId shouldBe saved.sessionId
          val (capture, _) = _read_public_capture_(saved)
          val target = capture.direction.target
          capture.schemaVersion shouldBe R.initialCaptureSchema
          target.schemaVersion shouldBe R.initialSchema
          target.sessionId shouldBe saved.sessionId
          target.plan shouldBe plan
          target.primaryBase shouldBe old.controller.base
          target.step shouldBe old.inspection.step
          target.initialOrigin.get.lineage.recovery shouldBe old
          target.initialOrigin.get.lineage.continuation shouldBe None
          target.proposal shouldBe value
          definition.goal.dependencySourceRevisions should contain(R.bindingOf(capture))
          definition.goal.dependencySourceConsumptions should contain(R.consumptionOf(capture))
          reporter.When("the fixture reads only the current native instance to authenticate this exact pending ReviewStep")
          val pending = _native_fixture(scenario) { core => for {
            snapshot <- LegacyDependencyHistoryFixture.snapshotC(core, saved.sessionId)
            catalog <- GoalPhaseRuntimeCatalog.loadC()
            definitions <- WorkflowInstanceRecordJsonV1.catalogC(Vector(catalog.definition))
            instance <- WorkflowInstanceRecordJsonV1.decodeC(snapshot.toMap.apply("instance"), definitions)
          } yield instance }
          reporter.Then("the native identity, current revision and suspension match the saved issued-work context before any Advance")
          pending.identity.value shouldBe saved.sessionId
          pending.revision.value.toString shouldBe issued.expectedRevision
          pending.suspension.map(_.continuationIdentity.value) shouldBe Some(issued.continuationId)
          scenario.retrieve(selected.task) shouldBe saved.sessionId
          val primary = Path.of(definition.target.root)
          // This is a current recollection checkpoint; the earlier process's pre-adoption byte vector is unavailable.
          val primarybytes = Files.readAllBytes(primary.resolve(sourcePath)).toVector
          val primarychecks = _executions(root)
          val dependencychecks = _executions(root.resolve("dependency"))
          val compilepath = root.resolve("dependency/sbt-compile-executions.txt")
          val compilebytes = Option.when(Files.exists(compilepath))(Files.readAllBytes(compilepath).toVector)
          primarychecks should have size 1
          dependencychecks shouldBe Vector("selected-test-only")
          reporter.When("one ordinary installed Advance recollects only that exact pending ReviewStep")
          val recollected = scenario.advance(selected.task)
          val current = recollected.business[V.GoalPhaseAdvanceResult]
          reporter.Then("recollection retains the actual issued context and passed candidate without replaying checks")
          current.kind shouldBe "Request"
          current.work.get.kind shouldBe "ReviewStep"
          current.issuedWorkContext shouldBe saved.issuedWorkContext
          current.sessionId shouldBe saved.sessionId
          current.work.get.selectedPlan shouldBe saved.work.get.selectedPlan
          current.work.get.originalBase shouldBe saved.work.get.originalBase
          current.work.get.stepIdentity shouldBe saved.work.get.stepIdentity
          current.validation.get.outcome shouldBe Some("passed")
          _executions(root) shouldBe primarychecks
          _executions(root.resolve("dependency")) shouldBe dependencychecks
          Option.when(Files.exists(compilepath))(Files.readAllBytes(compilepath).toVector) shouldBe compilebytes
          _finish_review_(selected, recollected, target, old, oldsnapshot, saved, primarybytes, "current review-recollection checkpoint")
        } else if (mode == "continue-new-closed") {
          import org.goldenport.cncf.workflow.WorkflowInstanceRecordJsonV1
          reporter.Given("the actual saved Step-closure result and earlier passed ReviewStep capture for this same physical fixture")
          def _saved_log_(property: String): V.GoalPhaseAdvanceResult = {
            val path = Path.of(sys.props.getOrElse(property, fail("Select the actual saved public response with " + property)))
            require(path.isAbsolute && path.normalize == path && path.getParent == root.resolve("logs") &&
              Files.isRegularFile(path) && path.toRealPath() == path,
              "Select an actual regular absolute public response directly under this same fixture's logs")
            _advance_(parse(Files.readString(path)).fold(error => fail(error.message), identity))
          }
          val saved = _saved_log_("smworkflow.spec.initialSourceClosed")
          val review = _saved_log_("smworkflow.spec.initialSourceReview")
          reporter.Then("the saved closure has completed Step 1 and issued Step 2 in the original Session and Plan with preserved counters")
          saved.kind shouldBe "Request"
          saved.sessionId shouldBe selected.original.sessionId
          saved.work.get.kind shouldBe "ImplementStep"
          saved.work.get.stepIdentity shouldBe Some("step-2")
          saved.work.get.selectedPlan shouldBe F.publicPlan(plan)
          saved.work.get.originalBase shouldBe selected.original.work.get.originalBase
          saved.work.get.completed.map(_.step) shouldBe Vector("step-1")
          saved.repairProgress shouldBe review.repairProgress
          saved.issuedWorkContext should not be empty
          val issued = saved.issuedWorkContext.get
          issued.runId shouldBe saved.sessionId
          review.kind shouldBe "Request"
          review.sessionId shouldBe saved.sessionId
          review.work.get.kind shouldBe "ReviewStep"
          review.work.get.stepIdentity shouldBe Some(old.inspection.step)
          review.work.get.selectedPlan shouldBe saved.work.get.selectedPlan
          review.work.get.originalBase shouldBe saved.work.get.originalBase
          review.validation.get.outcome shouldBe Some("passed")
          val (capture, executions) = _read_public_capture_(review)
          val target = capture.direction.target
          capture.schemaVersion shouldBe R.initialCaptureSchema
          target.schemaVersion shouldBe R.initialSchema
          target.sessionId shouldBe saved.sessionId
          target.plan shouldBe plan
          target.primaryBase shouldBe old.controller.base
          target.step shouldBe old.inspection.step
          target.initialOrigin.get.lineage.recovery shouldBe old
          target.initialOrigin.get.lineage.continuation shouldBe None
          target.proposal shouldBe value
          definition.goal.dependencySourceRevisions should contain(R.bindingOf(capture))
          definition.goal.dependencySourceConsumptions should contain(R.consumptionOf(capture))
          executions.map(_.operation) shouldBe Vector("compile", "test-only")
          reporter.When("the fixture reads the current native pending request and typed Git state for the already completed closure")
          val (pending, git, index) = _native_fixture(scenario) { core => for {
            snapshot <- LegacyDependencyHistoryFixture.snapshotC(core, saved.sessionId)
            catalog <- GoalPhaseRuntimeCatalog.loadC()
            definitions <- WorkflowInstanceRecordJsonV1.catalogC(Vector(catalog.definition))
            instance <- WorkflowInstanceRecordJsonV1.decodeC(snapshot.toMap.apply("instance"), definitions)
            observed <- RepositorySyncManagedGitObservation.observeC(core)
            tree <- RepositorySyncManagedGitObservation.indexTreeC(core)
          } yield (instance, observed, tree) }
          reporter.Then("the current native identity, revision, suspension and task route identify only the saved pending Step 2 request")
          pending.identity.value shouldBe saved.sessionId
          pending.revision.value.toString shouldBe issued.expectedRevision
          pending.suspension.map(_.continuationIdentity.value) shouldBe Some(issued.continuationId)
          scenario.retrieve(selected.task) shouldBe saved.sessionId
          val commit = saved.work.get.completed.last.commit
          git.head shouldBe commit.identity
          git.tree shouldBe commit.tree
          index shouldBe commit.tree
          val primary = Path.of(definition.target.root)
          // Observe current bytes only for this already-closed evidence-collection checkpoint.
          val primarybytes = Files.readAllBytes(primary.resolve(sourcePath)).toVector
          val primarychecks = _executions(root)
          val dependencychecks = _executions(root.resolve("dependency"))
          val compilepath = root.resolve("dependency/sbt-compile-executions.txt")
          val compilebytes = Option.when(Files.exists(compilepath))(Files.readAllBytes(compilepath).toVector)
          primarychecks should have size 1
          dependencychecks shouldBe Vector("selected-test-only")
          reporter.When("one ordinary installed Advance recollects the already pending Step 2 request for evidence collection only")
          val collected = scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
          reporter.Then("collection preserves the saved run, issued context, completed commit and counters without new checks or closure")
          collected.kind shouldBe "Request"
          collected.sessionId shouldBe saved.sessionId
          collected.work.get.kind shouldBe "ImplementStep"
          collected.work.get.stepIdentity shouldBe saved.work.get.stepIdentity
          collected.work.get.selectedPlan shouldBe saved.work.get.selectedPlan
          collected.work.get.originalBase shouldBe saved.work.get.originalBase
          collected.work.get.completed shouldBe saved.work.get.completed
          collected.repairProgress shouldBe saved.repairProgress
          collected.issuedWorkContext shouldBe saved.issuedWorkContext
          _executions(root) shouldBe primarychecks
          _executions(root.resolve("dependency")) shouldBe dependencychecks
          Option.when(Files.exists(compilepath))(Files.readAllBytes(compilepath).toVector) shouldBe compilebytes
          _post_closure_audit_(selected, collected, capture, old, oldsnapshot, review, primarybytes,
            "current already-closed evidence-collection checkpoint")
        } else {
        reporter.Then("the retained episode has executed no primary or dependency check")
        _executions(root) shouldBe empty
        _executions(root.resolve("dependency")) shouldBe empty
        reporter.When("the new installed CLI describes the initial source target without requiring an invented old dependency Guard")
        val described = _describe_(selected, value)
        val description = described.business[V.GoalPhaseDependencySourceRevisionDescriptionResult]
        val target = SplitPhaseExplicitPartitionFixture.take(R.targetC(description.target))
        reporter.Then("v8 retains the exact direct recovery origin, current-primary observation and unrelated committed delta")
        target.schemaVersion shouldBe R.initialSchema
        target.predecessorGuard shouldBe None
        target.initialOrigin.get.lineage.recovery shouldBe old
        target.initialOrigin.get.lineage.continuation shouldBe None
        target.initialOrigin.get.currentPrimary.observed shouldBe "current-at-description"
        target.initialOrigin.get.baseChanges.map(_.path) shouldBe Vector("unrelated-base.txt")
        description.choices shouldBe Vector(R.initialChoice, "Defer")
        reporter.Given("the exact current-target answer, initially without independently provisioned Human evidence")
        val reference = V.WorkflowArtifactReference("fixture-current-primary-and-initial-source-direction", "1")
        val adoption = V.GoalPhaseDependencySourceRevisionAdoptionInput(target.sessionId, description.target, R.initialChoice,
          "SPECIFICATION-OWNED exact current-primary/source selection accepting prior UNKNOWN effects", reference)
        val primary = Path.of(definition.target.root)
        val dependency = Path.of(target.binding.root)
        val originalprimary = Files.readAllBytes(primary.resolve(sourcePath))
        val originaldependency = Files.readAllBytes(dependency.resolve("value.txt"))
        def _effect_facts_(): Vector[Option[Vector[Byte]]] = Vector(root.resolve("sbt-executions.txt"),
          root.resolve("dependency/sbt-executions.txt"), root.resolve("dependency/sbt-compile-executions.txt"),
          primary.resolve(sourcePath), dependency.resolve("value.txt"),
          primary.resolve(".git/refs/heads/main"), dependency.resolve(".git/refs/heads/main"))
          .map(path => Option.when(Files.exists(path))(Files.readAllBytes(path).toVector))
        def _refused_without_effects_(action: String, expectation: String, diagnostic: Option[(Int, String)])
          (invoke: => F.Result): Unit = {
          val before = _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, target.sessionId))
          val effects = _effect_facts_()
          val primarychecks = _executions(root)
          val dependencychecks = _executions(root.resolve("dependency"))
          reporter.When(action)
          val refused = invoke
          reporter.Then(expectation)
          withClue(refused.invocation.logs) {
            refused.exitCode should not be 0
            Files.readString(refused.invocation.stdout).trim shouldBe empty
            diagnostic.foreach { (status, message) =>
              val error = Files.readString(refused.invocation.stderr)
              error should include("status=" + status)
              error should include(message)
            }
          }
          _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, target.sessionId)) shouldBe before
          _effect_facts_() shouldBe effects
          _executions(root) shouldBe primarychecks
          _executions(root.resolve("dependency")) shouldBe dependencychecks
        }
        _refused_without_effects_("the unprovisioned answer is submitted to the installed adoption operation",
          "missing independent Human evidence refuses with no native frontier or effect change",
          Some(403 -> "Action has no independently issued host evidence for its actual target")) {
          scenario.call("command", R.adoptionOperation, Vector("--input", json(adoption.toRecord()).noSpaces))
        }
        val beforeprovision = _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, target.sessionId))
        reporter.When("the independent trusted fixture provisions the exact valid current-target Human answer once")
        _provision(selected, described, "dependency-source-revision", adoption, reference)
        reporter.Then("evidence provisioning leaves the native Phase frontier and both check logs unchanged")
        _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, target.sessionId)) shouldBe beforeprovision
        _executions(root) shouldBe empty
        _executions(root.resolve("dependency")) shouldBe empty
        reporter.Given("valid exact Human evidence and a primary worktree edit made after target description")
        Files.writeString(primary.resolve(sourcePath), "// changed after target\n", StandardOpenOption.APPEND)
        try _refused_without_effects_("the exact provisioned answer is adopted with unstaged current-primary drift",
          "current-primary stage readiness refuses without a native frontier or effect change",
          Some(409 -> "current primary requires completed scoped staging without new effects")) {
          scenario.call("command", R.adoptionOperation, Vector("--input", json(adoption.toRecord()).noSpaces))
        } finally Files.write(primary.resolve(sourcePath), originalprimary)
        reporter.Given("the restored primary, valid exact Human evidence and dependency bytes changed after description")
        Files.writeString(dependency.resolve("value.txt"), "foreign post-target bytes\n")
        try _refused_without_effects_("the exact provisioned answer is adopted with changed successor bytes",
          "successor target drift refuses without a native frontier or effect change",
          Some(409 -> "dependency source direction target drifted")) {
          scenario.call("command", R.adoptionOperation, Vector("--input", json(adoption.toRecord()).noSpaces))
        } finally Files.write(dependency.resolve("value.txt"), originaldependency)
        val exactconfig = Files.readAllBytes(configfile)
        val parsedconfig = parse(Files.readString(configfile)).toOption.get
        val processes = parse(parsedconfig.hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
        val programs = processes.hcursor.get[Vector[Json]]("sbtPrograms").toOption.get
        val crossedprograms = programs.map(program => if (program.hcursor.get[String]("operation").contains("test-only"))
          program.mapObject(_.add("identity", "foreign-fixed-check".asJson)) else program)
        reporter.Given("restored source bytes, valid exact Human evidence and a crossed fixed-check program identity")
        Files.writeString(configfile, parsedconfig.mapObject(_.add("textus.subsystem.process-execution.plan",
          processes.mapObject(_.add("sbtPrograms", crossedprograms.asJson)).noSpaces.asJson)).noSpaces)
        try _refused_without_effects_("the exact provisioned answer is adopted under the crossed check declaration",
          "the inherited source contract refuses without a native frontier or effect change",
          Some(409 -> "source revision crosses inherited source, runtime or acceptance obligations")) {
          scenario.call("command", R.adoptionOperation, Vector("--input", json(adoption.toRecord()).noSpaces))
        } finally Files.write(configfile, exactconfig)
        reporter.Given("the restored check declaration and valid exact Human evidence with all process capability grants removed")
        Files.writeString(configfile, parsedconfig.mapObject(_.add("textus.subsystem.process-execution.plan",
          processes.mapObject(_.add("grants", Json.arr())).noSpaces.asJson)).noSpaces)
        try _refused_without_effects_("the exact provisioned answer is adopted without its typed Git component capability grant",
          "typed capability admission refuses with Argument Invalid status before any frontier or effect change",
          Some(400 -> "git.execution.component-grant")) {
          scenario.call("command", R.adoptionOperation, Vector("--input", json(adoption.toRecord()).noSpaces))
        } finally Files.write(configfile, exactconfig)
        reporter.Given("the exact restored host and an obsolete worker completion from before ReviseWork")
        _refused_without_effects_("the late original worker result is submitted to its old issued work",
          "the obsolete completion refuses without a native frontier or effect change", None) {
          _submit(scenario, selected.resumed, Json.obj("outcome" -> "completed".asJson,
          "summary" -> "SPECIFICATION-OWNED late old completion".asJson, "findings" -> Json.arr(),
            "dependencyValidation" -> selected.selection.asJson), V.WorkflowArtifactReference("late-original-result", "1"))
        }
        reporter.When("the exact provisioned current-target answer is adopted under the restored host and bytes")
        val adopted = scenario.call("command", R.adoptionOperation, Vector("--input", json(adoption.toRecord()).noSpaces))
          .business[V.GoalPhaseDependencySourceRevisionAdoptionResult]
        reporter.Then("the exact independent source and current-primary Direction is accepted")
        adopted.accepted shouldBe true
        val stale = adoption.copy(reference = V.WorkflowArtifactReference("unadmitted-stale-source-answer", "1"))
        _refused_without_effects_("a different unadmitted reference tries to reuse the consumed Human frontier",
          "the stale source answer refuses without a native frontier or effect change", None) {
          scenario.call("command", R.adoptionOperation, Vector("--input", json(stale.toRecord()).noSpaces))
        }
        reporter.When("the same exact authenticated adoption is repeated")
        val repeated = scenario.call("command", R.adoptionOperation, Vector("--input", json(adoption.toRecord()).noSpaces))
          .business[V.GoalPhaseDependencySourceRevisionAdoptionResult]
        reporter.Then("idempotent adoption is accepted and neither check log has changed")
        repeated.accepted shouldBe true
        _executions(root) shouldBe empty
        _executions(root.resolve("dependency")) shouldBe empty
        reporter.When("a separate ordinary installed Advance issues implementation under the adopted current-primary/source Direction")
        val next = scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
        reporter.Then("the same-Step continuation remains ordinary implementation work")
        next.work.get.kind shouldBe "ImplementStep"
        val completion = Json.obj("outcome" -> "completed".asJson,
          "summary" -> "SPECIFICATION-OWNED current-primary selection under the newly admitted source Direction".asJson,
          "findings" -> Json.arr())
        reporter.When("the newly issued worker completes its semantic work under the adopted Direction")
        val completed = _submit(scenario, next, completion, V.WorkflowArtifactReference("current-initial-source-completion", "1"))
          .business[V.GoalPhaseWorkSubmissionResult]
        reporter.Then("the new semantic result is accepted without replaying the original Submit")
        completed.accepted shouldBe true
        reporter.When("a separate installed Advance captures the selected source and current primary and runs the fresh required checks")
        val reviewed = scenario.advance(selected.task)
        _finish_review_(selected, reviewed, target, old, oldsnapshot, next, originalprimary.toVector, "pre-adoption primary-byte")
        }
      }
      episode
  }

  "A new dependency source through ordinary public operations" should {
    for ((mode, extendscope, exclusionappend, committedbase) <- Vector(("selected-changes", false, false, false), ("committed", false, false, false),
        ("selected-changes", true, false, false), ("selected-changes", true, true, false),
        ("selected-changes", true, true, true))) {
      s"freshly qualify $mode in a different clone and preserve the accepted prefix and unrelated index${if (extendscope) " with an approved scope successor" else ""}${if (exclusionappend) " and a non-input exclusion succession" else ""}${if (committedbase) " and an exact committed dependency base V4 with ordinary primary closure" else ""}" in {
        import GoalPhaseDependencySourceRevision as R
        import org.goldenport.cncf.processexecution.GitOperation
        Given("a genuinely accepted first dependency Step and a retained failed check of the admitted second Step")
        val prefix = _accepted_prefix("dependency-source-revision-" + mode, overlap = true, sourceRevisionFixture = true)
        val implementation = _adopt_prefix(prefix)
        _complete_prefix(implementation, overlap = true, failCheck = true)
        val repair = implementation.scenario.advance(implementation.task).business[V.GoalPhaseAdvanceResult]
        repair.work.get.kind shouldBe "RepairStep"
        json(repair.work.get.checkFailure.get.toRecord()).noSpaces should include("second-cncf-test")
        val originalTestIssue = repair.work.get.issues.get.issues.find(issue => issue.source == "TEST" &&
          issue.repository == prefix.binding.dependency.repository).get
        val selected = implementation.copy(resumed = repair)
        val scenario = selected.scenario
        val originalFirstReview = Files.readAllBytes(prefix.firstReview.invocation.stdout).toVector
        val unchanged = _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, selected.original.sessionId))
        val blocked = repair.work.get.issues.get.issues.filter(_.status != "RESOLVED").map(issue =>
          V.GoalPhaseIssueDisposition(issue.identity, "BLOCKED", "SPECIFICATION-OWNED request for a new source", None))
        val decisionrequest = Json.obj("outcome" -> "decision-needed".asJson,
          "summary" -> "SPECIFICATION-OWNED request for a new source qualification".asJson,
          "findings" -> Json.arr(), "issueReport" -> json(V.GoalPhaseIssueReport(blocked).toRecord()))
        When("a decision-needed reply incorrectly carries a completion-only dependency selection")
        val refused = _submit(scenario, selected.resumed,
          decisionrequest.mapObject(_.add("dependencyValidation", selected.selection.asJson)),
          V.WorkflowArtifactReference("source-revision-invalid-selection", "1"))
        Then("the invalid result is refused before native state is changed")
        _refused(refused, "Dependency validation selection requires completed work")
        _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, selected.original.sessionId)) shouldBe unchanged
        When("the same issued work requests the source decision without a completion selection")
        val submitted = _submit(scenario, selected.resumed, decisionrequest, V.WorkflowArtifactReference("source-revision-request", "1"))
          .business[V.GoalPhaseWorkSubmissionResult]
        Then("the decision-needed completion is accepted")
        submitted.accepted shouldBe true
        When("the separately permitted ordinary Advance consumes that completion")
        val human = scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
        Then("the ordinary new unanswered Human Decision is issued")
        human.kind shouldBe "Human"

        val root = Files.createDirectories(scenario.root.resolve("revised-source"))
        val repository = root.resolve("repository")
        val exclusion = "unrelated.md"
        val exclusions = if (exclusionappend) Vector(exclusion, "docs/old-tracked.md", "docs/old-untracked.md", "docs/old-partial.md")
          else Vector(exclusion)
        val additions = Vector("docs/design/configuration-binding.md", "docs/phase/README.md",
          "docs/journal/startup-handoff.md", "docs/phase/startup-correction.md", "docs/spec/standalone-startup.md")
        def setup(arguments: String*): Unit = SplitPhaseExplicitPartitionFixture.take(_run_setup_command_(
          Vector("--no-optional-locks", "--no-replace-objects", "-c", "core.hooksPath=/dev/null",
            "-c", "core.fsmonitor=false", "-c", "gc.auto=0") ++ arguments))
        setup("clone", "--no-local", "--", prefix.binding.root, repository.toString)
        TypedSbtHostPlanFixture.build(root, true)
        Files.writeString(repository.resolve("build.sbt"), "\nlazy val root = project.in(file(\".\"))\n",
          StandardOpenOption.APPEND)
        Files.createDirectories(repository.resolve("src/main/scala"))
        Files.writeString(repository.resolve("src/main/scala/SourceRevisionMarker.scala"),
          "object SourceRevisionMarker { def source: String = " + repository.toRealPath().toString.asJson.noSpaces + " }\n")
        Files.writeString(repository.resolve("inherited.txt"), "accepted first-Step input\n")
        if (committedbase) repository.resolve("inherited.txt").toFile.setExecutable(true, false) shouldBe true
        Files.writeString(repository.resolve("current.txt"), "new source baseline\n")
        if (exclusionappend) {
          Given("private fixture documents with retained and future tracked baselines independent of source ownership")
          Vector(exclusions(1), exclusions(3), additions(0), additions(1)).foreach { path =>
            Files.createDirectories(repository.resolve(path).getParent)
            Files.writeString(repository.resolve(path), "other owner baseline\n")
            setup("-C", repository.toString, "add", "--", path)
          }
        }
        setup("-C", repository.toString, "add", "--", "build.sbt", "value.txt", "inherited.txt", "current.txt", "src/main/scala/SourceRevisionMarker.scala")
        setup("-C", repository.toString, "-c", "user.name=Source Revision Specification",
          "-c", "user.email=source@example.invalid", "commit", "--no-verify", "--no-gpg-sign",
          "-m", "Specification-owned different source base")
        Files.writeString(repository.resolve(exclusion), "another owner's staged document\n")
        setup("-C", repository.toString, "add", "--", exclusion)
        if (exclusionappend) {
          Files.writeString(repository.resolve(exclusions(1)), "other owner tracked edit\n")
          Files.writeString(repository.resolve(exclusions(2)), "other owner untracked document\n")
          Files.writeString(repository.resolve(exclusions(3)), "other owner staged portion\n")
          setup("-C", repository.toString, "add", "--", exclusions(3))
          Files.writeString(repository.resolve(exclusions(3)), "other owner unstaged portion\n")
        }
        if (mode == "selected-changes") Files.writeString(repository.resolve("current.txt"), "new selected source delta\n")

        val provisional = prefix.binding.copy(revision = 2, root = repository.toRealPath().toString,
          dependency = prefix.binding.dependency.copy(workspace = "cncf-revised-source"),
          paths = Vector("value.txt", "inherited.txt", "current.txt"))
        val consumption = R.Consumption(provisional.requester.workspace, provisional.root,
          exclusions, provisional.paths.map(path => R.ExpectedMode(path,
            if (committedbase && path == "inherited.txt") "100755" else "100644")),
          (scenario.host.goal.stepValidation.checks ++ scenario.host.goal.phaseValidation.checks).distinct.map(check =>
            R.CheckProgram(check.identity, "sm-workflow-repository-validation-" + check.ordinal, check.kind.wire,
              "sm-workflow-repository-validation-" + check.ordinal)) ++ provisional.checks.map(check =>
            R.CheckProgram(check.identity, provisional.dependency.workspace + "-" + check.kind.wire + "-route",
              check.kind.wire, provisional.dependency.workspace + "-" + check.kind.wire)))
        _source_host(selected, provisional, consumption)
        val observed = _native_fixture(scenario)(RepositorySyncManagedGitObservation.observeInWorkspaceC(_,
          provisional.dependency.workspace))
        val replacement = provisional.copy(base = GoalPhaseOrderedProgression.Snapshot(observed.head, observed.tree))
        replacement.base should not be prefix.binding.base
        _source_host(selected, replacement, consumption)
        val currentApproval = R.Coverage(selected.selection.approvals.head.decision,
          _second_proposal(prefix.binding).acceptance, replacement.checks.map(_.identity))
        val coverage = (prefix.first.admission.approvals.map(a => R.Coverage(a.decision, a.proposal.acceptance,
          replacement.checks.map(_.identity))) :+ currentApproval).distinct
        val proposal = R.Proposal(R.sourceSchema, ContextReference(replacement.identity, "2"), mode,
          exclusions, coverage, consumption)
        val build = repository.resolve("build.sbt")
        val buildBytes = Files.readAllBytes(build)
        Files.writeString(build, "\n// caller-owned dirty build input\n", StandardOpenOption.APPEND)
        val forbidden = V.GoalPhaseDependencySourceRevisionDescriptionInput(human.sessionId,
          GoalPhaseNativeMetadata.record(proposal.copy(excludedPaths = Vector(exclusion, "build.sbt")).asJson))
        val excludedBuild = scenario.call("command", R.describeOperation, Vector("--input", json(forbidden.toRecord()).noSpaces))
        excludedBuild.exitCode should not be 0
        Files.write(build, buildBytes)
        val describeInput = V.GoalPhaseDependencySourceRevisionDescriptionInput(human.sessionId,
          GoalPhaseNativeMetadata.record(proposal.asJson))
        val described = scenario.call("command", R.describeOperation, Vector("--input", json(describeInput.toRecord()).noSpaces))
        val description = described.business[V.GoalPhaseDependencySourceRevisionDescriptionResult]
        val target = SplitPhaseExplicitPartitionFixture.take(R.targetC(description.target))
        target.completed.map(_.step) shouldBe Vector("step-1")
        target.primaryBase shouldBe prefix.first.primaryBase
        target.binding.root should not be target.predecessor.binding.root
        target.ancestors.map(_.capture) should contain(prefix.first)

        When("a byte edit preserves the same Git status after description")
        val selectedBytes = Files.readAllBytes(repository.resolve("current.txt"))
        Files.writeString(repository.resolve("current.txt"), "substituted bytes\n")
        val reference = V.WorkflowArtifactReference("specification-source-direction", "1")
        val adoption = V.GoalPhaseDependencySourceRevisionAdoptionInput(human.sessionId, description.target,
          "AdoptDependencySourceRevision", "SPECIFICATION-OWNED new-source direction", reference)
        scenario.call("command", R.adoptionOperation, Vector("--input", json(adoption.toRecord()).noSpaces))
          .exitCode should not be 0
        Files.write(repository.resolve("current.txt"), selectedBytes)
        _provision(selected, described, "dependency-source-revision", adoption, reference)
        scenario.call("command", R.adoptionOperation, Vector("--input", json(adoption.toRecord()).noSpaces))
          .business[V.GoalPhaseDependencySourceRevisionAdoptionResult].accepted shouldBe true
        val resumed = scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
        resumed.sessionId shouldBe selected.original.sessionId
        resumed.work.get.completed.map(_.step) shouldBe Vector("step-1")
        resumed.work.get.originalBase shouldBe selected.original.work.get.originalBase
        resumed.repairProgress shouldBe human.repairProgress
        resumed.work.get.checkFailure shouldBe repair.work.get.checkFailure
        resumed.validation shouldBe None
        Files.writeString(Path.of(scenario.host.target.root).resolve("docs/phase.md"), "New source integration\n")
        def completeSource(work: V.GoalPhaseAdvanceResult, reference: String): Unit = {
          val fixed = work.work.get.issues.toVector.flatMap(_.issues).filter(_.status != "RESOLVED").map(issue =>
            V.GoalPhaseIssueDisposition(issue.identity, "FIXED", "SPECIFICATION-OWNED fresh source integration", None))
          val result = Json.obj("outcome" -> "completed".asJson,
            "summary" -> "SPECIFICATION-OWNED integration using adopted native direction".asJson,
            "findings" -> Json.arr()).mapObject(fields => if (work.work.get.kind == "RepairStep")
              fields.add("issueReport", json(V.GoalPhaseIssueReport(fixed).toRecord())) else fields)
          _submit(scenario, work, result, V.WorkflowArtifactReference(reference, "1"))
            .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
        }
        var oldunknown = Option.empty[(String, String)]
        if (mode == "selected-changes" && (!extendscope || committedbase)) {
          // The same bytes and M status cannot authorize a different executable
          // mode. Its completed Git effect has no accepted source result.
          repository.resolve("current.txt").toFile.setExecutable(true, false) shouldBe true
          completeSource(resumed, "source-mode-refused-completion")
          val refused = scenario.advance(selected.task)
          refused.exitCode should not be 0
          _executions(root) shouldBe empty
          val describedRecovery = _describe_recovery(selected)
          val recovery = describedRecovery.business[V.GoalPhaseCandidateRecoveryDescriptionResult]
          recovery.choices shouldBe Vector("ReviseWork", "Defer")
          val interrupted = SplitPhaseExplicitPartitionFixture.take(GoalPhaseCandidateRecovery.targetC(recovery.target))
          interrupted.dependencyPrefix.last.key shouldBe "dependency-source-stage-intent-2"
          val unknownkey = SplitPhaseExplicitPartitionFixture.take(org.goldenport.cncf.workflow.WorkflowMechanicalTransition.keyC(
            org.goldenport.cncf.workflow.WorkflowMechanicalTransition.Reference(interrupted.dependencyPrefix.last.identity, "1")))
          oldunknown = Some(unknownkey -> _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_,
            selected.original.sessionId)).toMap.apply(unknownkey))
          _recover(selected, describedRecovery, "ReviseWork").business[V.GoalPhaseCandidateRecoveryResult].accepted shouldBe true
          val revised = scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
          revised.repairProgress shouldBe resumed.repairProgress
          revised.work.get.completed shouldBe resumed.work.get.completed
          revised.work.get.kind shouldBe "ImplementStep"
          revised.work.get.checkFailure shouldBe None
          revised.validation shouldBe None
          val retainedIndex = _native_fixture(scenario)(R.observeC(_, replacement, exclusions))
          retainedIndex.selectedEntries.find(_.path == "current.txt").map(_.mode) shouldBe Some("100755")
          When("the newly issued implementation explicitly reconciles both the worktree mode and actual index")
          repository.resolve("current.txt").toFile.setExecutable(false, false) shouldBe true
          setup("-C", repository.toString, "add", "--", "current.txt")
          val reconciled = _native_fixture(scenario)(R.observeC(_, replacement, exclusions))
          Then("the new work restores the declared mode without changing excluded entries or settling the old intent")
          reconciled.selectedEntries.find(_.path == "current.txt").map(_.mode) shouldBe Some("100644")
          reconciled.excludedFiles shouldBe retainedIndex.excludedFiles
          reconciled.excludedEntries shouldBe retainedIndex.excludedEntries
          completeSource(revised, "source-revision-completion")
        } else completeSource(resumed, "source-revision-completion")
        if (exclusionappend) {
          When("the ordinary candidate result is saved but its acknowledgement is interrupted")
          _interrupt(selected, "goal-candidate-observed")
          Then("the next public Advance collects the completed original source prefix rather than replaying staging")
          _executions(root) shouldBe empty
        }
        val review = scenario.advance(selected.task)
        val request = review.business[V.GoalPhaseAdvanceResult]
        request.work.get.kind shouldBe "ReviewStep"
        request.work.get.issues.get.issues.find(_.identity == originalTestIssue.identity).get.status shouldBe "RESOLVED"
        request.repairProgress shouldBe human.repairProgress
        val validation = GoalPhaseNativeMetadata.normalizePublicJson(json(request.work.get.review.get.dependencyValidations.last))
          .fold(fail(_), identity)
        val capture = validation.hcursor.get[R.Capture]("capture").fold(error => fail(error.message), identity)
        val executions = validation.hcursor.get[Vector[D.Execution]]("executions").fold(error => fail(error.message), identity)
        Then("fresh native validation qualifies the new source without forging a legacy capture or consuming the old results")
        request.validation.get.outcome shouldBe Some("passed")
        validation.hcursor.get[D.Capture]("capture").isLeft shouldBe true
        executions.map(_.workspace) shouldBe replacement.checks.map(check =>
          consumption.programs.find(_.check == check.identity).get.workspace)
        capture.direction.target shouldBe target
        capture.postStageGuard.excludedFiles shouldBe target.preStageGuard.excludedFiles
        capture.postStageGuard.excludedEntries shouldBe target.preStageGuard.excludedEntries
        capture.stagedPaths shouldBe (if (mode == "committed") Vector.empty else replacement.paths)
        _executions(root).size shouldBe 1
        Files.readAllBytes(prefix.firstReview.invocation.stdout).toVector shouldBe originalFirstReview
        var finalreview = review
        var finalcapture = capture
        if (extendscope) {
          When("ordinary independent review requests additional source work in the adopted dependency")
          _review(selected, review, Vector("Qualify the required additional dependency source"), artifact = "source-scope")
            .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
          val additionalrepair = scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
          additionalrepair.work.get.kind shouldBe "RepairStep"
          val additionalpath = "src/main/scala/AdditionalSource.scala"
          val absentpath = "src/main/scala/AbsentSource.scala"
          val additionalpaths = if (committedbase) Vector(additionalpath, absentpath) else Vector(additionalpath)
          val additionalproposal = _second_proposal(prefix.binding).copy(identity = "source-additional-approved-proposal",
            discovery = "REPAIR", paths = additionalpaths,
            reason = "SPECIFICATION-OWNED required source in the already adopted dependency",
            acceptance = Vector("Validate the additional file through the same dependency checks"))
          val pending = additionalrepair.work.get.issues.get.issues.filter(_.status != "RESOLVED").map(issue =>
            V.GoalPhaseIssueDisposition(issue.identity, "BLOCKED", "SPECIFICATION-OWNED additional scope request", None))
          _submit(scenario, additionalrepair, Json.obj("outcome" -> "decision-needed".asJson,
            "summary" -> "SPECIFICATION-OWNED extra dependency source scope".asJson, "findings" -> Json.arr(),
            "dependencyProposal" -> additionalproposal.asJson,
            "issueReport" -> json(V.GoalPhaseIssueReport(pending).toRecord())),
            V.WorkflowArtifactReference("source-additional-scope-request", "1"))
            .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
          val questionoutput = scenario.advance(selected.task)
          val question = questionoutput.business[V.GoalPhaseAdvanceResult]
          question.kind shouldBe "Human"
          val answer = V.GoalPhaseOrderedDecisionAnswer("ApproveDependencyScope", "SPECIFICATION-OWNED actual additional file")
          val reference = V.WorkflowArtifactReference("source-additional-actual-approval", "1")
          _provision(selected, questionoutput, "human", answer, reference)
          val evidence = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
          scenario.call("command", question.submitOperation.get, Vector("--sessionId", question.submitSessionId.get,
            "--answer", json(answer.toRecord()).noSpaces, "--resultReference", json(reference.toRecord()).noSpaces,
            "--executionEvidence", json(evidence.toRecord()).noSpaces))
            .business[V.GoalPhaseOrderedDecisionSubmissionResult].accepted shouldBe true
          val scopedrepair = scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
          Files.writeString(repository.resolve(additionalpath), "object AdditionalSource\n")
          var scopebinding = replacement.copy(revision = replacement.revision + 1, paths = replacement.paths ++ additionalpaths)
          var scopeconsumption = consumption.copy(expectedModes = consumption.expectedModes ++ additionalpaths.map(path =>
            R.ExpectedMode(path, if (path == absentpath) "000000" else "100644")))
          val scopecoverage = capture.direction.target.proposal.coverage :+ R.Coverage(
            ContextReference(reference.identity, reference.revision), additionalproposal.acceptance, scopebinding.checks.map(_.identity))
          def _proposal_(): R.Proposal = proposal.copy(binding = ContextReference(scopebinding.identity, scopebinding.revision.toString),
            mode = if (committedbase) "committed" else proposal.mode,
            excludedPaths = scopeconsumption.nonInputExclusions, coverage = scopecoverage, consumption = scopeconsumption)
          _source_host(selected, scopebinding, scopeconsumption)
          val nativebefore = _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, scopedrepair.sessionId))
          When("the caller requests another source direction without a real unanswered Human frontier")
          val premature = scenario.call("command", R.describeOperation, Vector("--input",
            json(V.GoalPhaseDependencySourceRevisionDescriptionInput(scopedrepair.sessionId,
              GoalPhaseNativeMetadata.record(_proposal_().asJson)).toRecord()).noSpaces))
          Then("the existing public route refuses without inventing a Human or replacing the original direction")
          premature.exitCode should not be 0
          _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, scopedrepair.sessionId)) shouldBe nativebefore
          var scopedwork = scopedrepair
          var actualruntime = Option.empty[R.Direction]
          var effectiveguard = capture.postStageGuard
          locally {
            Given("an active source consumption on the selected ordinary or installed route")
            val blocked = scopedrepair.work.get.issues.toVector.flatMap(_.issues).filter(_.status != "RESOLVED").map(issue =>
              V.GoalPhaseIssueDisposition(issue.identity, "BLOCKED", "SPECIFICATION-OWNED installed consumption direction", None))
            When("the actual issued work requests a decision and ordinary Advance issues its new Human")
            val requested = _submit(scenario, scopedrepair, Json.obj("outcome" -> "decision-needed".asJson,
              "summary" -> "SPECIFICATION-OWNED bounded source consumption succession".asJson, "findings" -> Json.arr(),
              "issueReport" -> json(V.GoalPhaseIssueReport(blocked).toRecord())),
              V.WorkflowArtifactReference("runtime-consumption-request", "1"))
            Then("the semantic request is accepted through its existing completion owner")
            requested.business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
            val runtimehuman = scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
            runtimehuman.kind shouldBe "Human"
            if (committedbase) {
              Given("real selected and background producer changes committed between the two native dependency bases")
              val baselinepath = "src/main/scala/BackgroundBaseline.scala"
              val previousbytes = Files.readAllBytes(repository.resolve(exclusions(1)))
              Files.writeString(repository.resolve(baselinepath), "object BackgroundBaseline\n")
              Files.writeString(repository.resolve(exclusions(1)), "new committed previous exclusion baseline\n")
              Files.writeString(repository.resolve(additions.head), "new committed additional exclusion baseline\n")
              val commitpaths = (replacement.paths :+ additionalpath) ++ Vector(baselinepath, exclusions(1), additions.head)
              setup((Vector("-C", repository.toString, "add", "--") ++ commitpaths)*)
              setup((Vector("-C", repository.toString, "-c", "user.name=Source Revision Specification",
                "-c", "user.email=source@example.invalid", "commit", "--no-verify", "--no-gpg-sign", "--only",
                "-m", "Specification-owned committed dependency baseline", "--") ++ commitpaths)*)
              Files.write(repository.resolve(exclusions(1)), previousbytes)
              val observed = _native_fixture(scenario)(RepositorySyncManagedGitObservation.observeInWorkspaceC(_,
                scopebinding.dependency.workspace))
              scopebinding = scopebinding.copy(base = GoalPhaseOrderedProgression.Snapshot(observed.head, observed.tree))
              scopebinding.base should not be replacement.base
              Files.writeString(Path.of(scenario.host.target.root).resolve("docs/phase.md"), "Committed dependency baseline integration\n")
            }
            if (exclusionappend) {
              Given("five additional non-input documents declared only by the independent fixture host")
              additions.foreach(path => Files.createDirectories(repository.resolve(path).getParent))
              Files.writeString(repository.resolve(additions(0)), "other owner tracked addition\n")
              Files.writeString(repository.resolve(additions(1)), "other owner staged addition\n")
              setup("-C", repository.toString, "add", "--", additions(1))
              Files.writeString(repository.resolve(additions(1)), "other owner unstaged addition\n")
              additions.drop(2).foreach(path => Files.writeString(repository.resolve(path), "other owner untracked addition\n"))
              scopeconsumption = scopeconsumption.copy(nonInputExclusions = exclusions ++ additions)
              When("the exact non-input exclusion consumption has not been independently registered")
              val unregistered = scenario.call("command", R.describeOperation, Vector("--input",
                json(V.GoalPhaseDependencySourceRevisionDescriptionInput(runtimehuman.sessionId,
                  GoalPhaseNativeMetadata.record(_proposal_().asJson)).toRecord()).noSpaces))
              Then("the public description refuses without treating document suffixes as qualification")
              unregistered.exitCode should not be 0
              _source_host(selected, scopebinding, scopeconsumption)
              val foreignpath = repository.resolve("unowned-build-input.txt")
              When("a path outside selected ownership and E1 enters the actual index")
              Files.writeString(foreignpath, "unowned fixture input\n")
              setup("-C", repository.toString, "add", "--", "unowned-build-input.txt")
              val foreign = scenario.call("command", R.describeOperation, Vector("--input",
                json(V.GoalPhaseDependencySourceRevisionDescriptionInput(runtimehuman.sessionId,
                  GoalPhaseNativeMetadata.record(_proposal_().asJson)).toRecord()).noSpaces))
              Then("the real Git and File UoW guards refuse an unbounded index addition")
              foreign.exitCode should not be 0
              setup("-C", repository.toString, "reset", "HEAD", "--", "unowned-build-input.txt")
              Files.delete(foreignpath)
              When("the registered non-input exclusion proposal removes an original exclusion")
              val removed = _proposal_().copy(excludedPaths = scopeconsumption.nonInputExclusions.drop(1),
                consumption = scopeconsumption.copy(nonInputExclusions = scopeconsumption.nonInputExclusions.drop(1)))
              val invalid = scenario.call("command", R.describeOperation, Vector("--input",
                json(V.GoalPhaseDependencySourceRevisionDescriptionInput(runtimehuman.sessionId,
                  GoalPhaseNativeMetadata.record(removed.asJson)).toRecord()).noSpaces))
              Then("a caller cannot silently reinterpret the original exclusion guard")
              invalid.exitCode should not be 0
            }
            val describedruntime = scenario.call("command", R.describeOperation, Vector("--input",
              json(V.GoalPhaseDependencySourceRevisionDescriptionInput(runtimehuman.sessionId,
                GoalPhaseNativeMetadata.record(_proposal_().asJson)).toRecord()).noSpaces))
            val runtimeDescription = describedruntime.business[V.GoalPhaseDependencySourceRevisionDescriptionResult]
            val runtimeTarget = SplitPhaseExplicitPartitionFixture.take(R.targetC(runtimeDescription.target))
            runtimeTarget.schemaVersion shouldBe R.sourceSchema
            if (committedbase) {
              Then("the complete actual delta and every selected committed mode blob and absence are observed")
              val committed = runtimeTarget.committedBaseSuccession.get
              committed.previousBase shouldBe replacement.base
              committed.nextBase shouldBe scopebinding.base
              committed.phaseChangedPaths.toSet shouldBe Set("current.txt", additionalpath)
              committed.previousExcludedChangedPaths shouldBe Vector(exclusions(1))
              committed.addedExcludedChangedPaths shouldBe Vector(additions.head)
              committed.baselineChangedPaths shouldBe Vector("src/main/scala/BackgroundBaseline.scala")
              committed.committedEntries.map(_.path) shouldBe scopebinding.paths
              committed.committedEntries.find(_.path == "inherited.txt").get.entry.get.mode shouldBe "100755"
              committed.committedEntries.find(_.path == absentpath).get.entry shouldBe None
              committed.phaseChangedPaths should not contain "inherited.txt"
            }
            runtimeTarget.runtime shouldBe None
            runtimeTarget.sourceSuccession.get.parent shouldBe capture.direction.directive
            runtimeTarget.sourceSuccession.get.previousDirection shouldBe capture.direction.directive
            val runtimeReference = V.WorkflowArtifactReference("runtime-consumption-independent-direction", "1")
            val runtimeInput = V.GoalPhaseDependencySourceRevisionAdoptionInput(runtimehuman.sessionId,
              runtimeDescription.target, "AdoptDependencySourceRevision", "SPECIFICATION-OWNED actual installed route", runtimeReference)
            val unadmitted = _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, runtimehuman.sessionId))
            if (committedbase) {
              Given("a clean selected index delta whose untouched selected tree entry is still executable")
              runtimeTarget.preStageGuard.selectedEntries shouldBe empty
              val bytes = Files.readAllBytes(repository.resolve("inherited.txt"))
              When("untouched selected bytes or their executable mode change after the committed description")
              Files.writeString(repository.resolve("inherited.txt"), "unadmitted untouched source bytes\n")
              val bytedrift = scenario.call("command", R.adoptionOperation, Vector("--input", json(runtimeInput.toRecord()).noSpaces))
              Files.write(repository.resolve("inherited.txt"), bytes)
              repository.resolve("inherited.txt").toFile.setExecutable(false, false) shouldBe true
              val modedrift = scenario.call("command", R.adoptionOperation, Vector("--input", json(runtimeInput.toRecord()).noSpaces))
              repository.resolve("inherited.txt").toFile.setExecutable(true, false) shouldBe true
              val succession = runtimeTarget.committedBaseSuccession.get
              val index = succession.committedEntries.indexWhere(_.path == "inherited.txt")
              val entry = succession.committedEntries(index)
              val blob = if (entry.entry.get.blob == "a" * 40) "b" * 40 else "a" * 40
              val substituted = runtimeTarget.copy(committedBaseSuccession = Some(succession.copy(
                committedEntries = succession.committedEntries.updated(index, entry.copy(entry = entry.entry.map(_.copy(blob = blob)))))))
              val forged = scenario.call("command", R.adoptionOperation, Vector("--input",
                json(runtimeInput.copy(target = R.targetRecord(substituted)).toRecord()).noSpaces))
              Then("neither empty index changes nor the original declaration certifies those altered actual files")
              bytedrift.exitCode should not be 0
              modedrift.exitCode should not be 0
              forged.exitCode should not be 0
              _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, runtimehuman.sessionId)) shouldBe unadmitted
            }
            if (exclusionappend) {
              runtimeTarget.exclusionSuccession.get.previousExclusions shouldBe exclusions
              runtimeTarget.exclusionSuccession.get.addedPaths shouldBe additions
              runtimeTarget.exclusionSuccession.get.parent shouldBe capture.direction.directive
              effectiveguard = runtimeTarget.preStageGuard
              val savedbytes = Files.readAllBytes(repository.resolve(additions.head))
              When("an added excluded document changes bytes after the exact description")
              Files.writeString(repository.resolve(additions.head), "same status different owner bytes\n")
              val drifted = scenario.call("command", R.adoptionOperation, Vector("--input", json(runtimeInput.toRecord()).noSpaces))
              Then("adoption re-observes E1 and refuses without changing native history or the index")
              drifted.exitCode should not be 0
              _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, runtimehuman.sessionId)) shouldBe unadmitted
              Files.write(repository.resolve(additions.head), savedbytes)
            }
            When("matching new source declarations have no independently provisioned exact-target evidence")
            val refused = scenario.call("command", R.adoptionOperation, Vector("--input", json(runtimeInput.toRecord()).noSpaces))
            Then("adoption refuses and retains the actual native frontier")
            refused.exitCode should not be 0
            _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, runtimehuman.sessionId)) shouldBe unadmitted
            When("the specification's ordinary independent host provisions the exact public target")
            _provision(selected, describedruntime, "dependency-source-revision", runtimeInput, runtimeReference)
            val adopted = scenario.call("command", R.adoptionOperation, Vector("--input", json(runtimeInput.toRecord()).noSpaces))
            Then("only normal independent admission records the actual direction")
            adopted.business[V.GoalPhaseDependencySourceRevisionAdoptionResult].accepted shouldBe true
            actualruntime = Some(R.Direction(runtimeTarget, ContextReference(runtimeReference.identity, runtimeReference.revision),
              "participant", runtimeInput.rationale))
            if (committedbase) {
              val config = parse(Files.readString(scenario.root.resolve(".textus/config.json"))).toOption.get
              val process = parse(config.hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
              val entries = process.hcursor.get[Vector[Json]]("gitPrograms").toOption.get.filter(program =>
                program.hcursor.get[String]("target").toOption.contains(scopebinding.dependency.workspace) &&
                  program.hcursor.get[String]("operation").toOption.contains("tree-entries"))
              Then("only one explicit finite TreeEntries host registration selects the exact effective inventory")
              entries should have size 1
              entries.head.hcursor.get[String]("selectionId").toOption shouldBe Some(R.committedInventorySelection)
              entries.head.hcursor.get[Vector[String]]("selectedPaths").toOption shouldBe Some(scopebinding.paths)
            }
            scopedwork = scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
            scopedwork.sessionId shouldBe scopedrepair.sessionId
            scopedwork.repairProgress shouldBe scopedrepair.repairProgress
            scopedwork.work.get.originalBase shouldBe scopedrepair.work.get.originalBase
            scopedwork.work.get.completed shouldBe scopedrepair.work.get.completed
            if (exclusionappend) {
              Then("ordinary separate Advance displays the effective inherited non-input exclusion inventory")
              val instructions = json(scopedwork.issuedWorkContext.get.context).noSpaces
              (exclusions ++ additions).foreach(path => instructions should include(path))
              val repeated = scenario.call("command", R.adoptionOperation, Vector("--input", json(runtimeInput.toRecord()).noSpaces))
              repeated.exitCode should not be 0
            }
          }
          completeSource(scopedwork, "source-additional-completed-repair")
          val configpath = scenario.root.resolve(".textus/config.json")
          val config = parse(Files.readString(configpath)).toOption.get
          val plan = parse(config.hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
          val programs = plan.hcursor.get[Vector[Json]]("gitPrograms").toOption.get
          val missing = programs.filter(program =>
            program.hcursor.get[String]("target").toOption.contains(scopebinding.dependency.workspace) &&
              (if (committedbase) program.hcursor.get[String]("operation").toOption.contains("stage-selected-all")
                else program.hcursor.get[String]("selectionId").toOption.contains("goal-path-4")))
          missing should have size (if (committedbase) scopebinding.paths.size else 1)
          val missingcapabilities = missing.map(_.hcursor.get[String]("capability").toOption.get).toSet
          val grants = plan.hcursor.get[Vector[Json]]("grants").toOption.get.map(grant => grant.mapObject(_.add(
            "capabilities", Json.fromValues(grant.hcursor.get[Vector[String]]("capabilities").toOption.get
              .filterNot(missingcapabilities).map(_.asJson)))))
          val incomplete = plan.mapObject(_.add("gitPrograms", Json.fromValues(programs.filterNot(missing.contains)))
            .add("grants", Json.fromValues(grants)))
          Files.writeString(configpath, config.mapObject(_.add("textus.subsystem.process-execution.plan", incomplete.noSpaces.asJson)).noSpaces)
          val untouched = _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, scopedrepair.sessionId))
          val primaryroot = Path.of(scenario.host.target.root)
          val guarded = Vector(primaryroot.resolve(".git/index"), primaryroot.resolve(".git/refs/heads/main"),
            primaryroot.resolve("docs/phase.md"), repository.resolve(additionalpath))
          val guardbytes = guarded.map(path => Files.readAllBytes(path).toVector)
          if (!committedbase) {
            When("the successor is declared but its actual last selected-stage capability is absent")
            scenario.advance(selected.task).exitCode should not be 0
            Then("framework admission refuses before new primary history or staging receipts")
            _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, scopedrepair.sessionId)) shouldBe untouched
            guarded.map(path => Files.readAllBytes(path).toVector) shouldBe guardbytes
            _executions(root).size shouldBe 1
            When("the specification host restores its exact selected-stage registration")
            _source_host(selected, scopebinding, scopeconsumption)
          }
          var interruptednative = Option.empty[Map[String, String]]
          var interruptedguard = Option.empty[R.Guard]
          var interruptedindices = Option.empty[Vector[Vector[Byte]]]
          val indexpaths = Vector(primaryroot.resolve(".git/index"), repository.resolve(".git/index"))
          if (exclusionappend && actualruntime.nonEmpty) {
            Given(if (committedbase) "completed V4 work and the active source consumption without any source-stage capability"
              else "the completed V3 semantic work and active source consumption with all selected-stage capabilities restored")
            val excludedbytes = (exclusions ++ additions).map(path => Files.readAllBytes(repository.resolve(path)).toVector)
            val priorchecks = _executions(root)
            When("the actual adopted source candidate is saved and its ordinary acknowledgement is interrupted")
            _interrupt(selected, "goal-candidate-observed")
            val observed = _native_fixture(scenario)(R.observeC(_, scopebinding, exclusions ++ additions))
            interruptedguard = Some(observed)
            interruptedindices = Some(indexpaths.map(path => Files.readAllBytes(path).toVector))
            interruptednative = Some(_native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, scopedrepair.sessionId)).toMap)
            Then("the saved result retains all nine exclusions and no fresh checks have run before collection")
            observed.excludedFiles shouldBe effectiveguard.excludedFiles
            observed.excludedEntries shouldBe effectiveguard.excludedEntries
            (exclusions ++ additions).map(path => Files.readAllBytes(repository.resolve(path)).toVector) shouldBe excludedbytes
            _executions(root) shouldBe priorchecks
            When("ordinary installed public Advance collects that actual saved source candidate")
          }
          finalreview = scenario.advance(selected.task)
          val scopedreview = finalreview.business[V.GoalPhaseAdvanceResult]
          Then("ordinary continuation reaches fresh checks and ReviewStep without another source-adoption Human")
          scopedreview.work.get.kind shouldBe "ReviewStep"
          scopedreview.repairProgress shouldBe scopedrepair.repairProgress
          scopedreview.work.get.originalBase shouldBe scopedwork.work.get.originalBase
          scopedreview.work.get.completed shouldBe scopedwork.work.get.completed
          val scopedvalidation = GoalPhaseNativeMetadata.normalizePublicJson(json(scopedreview.work.get.review.get.dependencyValidations.last))
            .fold(fail(_), identity)
          finalcapture = scopedvalidation.hcursor.get[R.Capture]("capture").fold(error => fail(error.message), identity)
          finalcapture.direction shouldBe capture.direction
          finalcapture.scope.get.consumptionDirection shouldBe actualruntime
          R.consumptionOf(finalcapture) shouldBe scopeconsumption
          finalcapture.scope.get.approvals.map(_.decision) shouldBe Vector(ContextReference(reference.identity, reference.revision))
          R.bindingOf(finalcapture) shouldBe scopebinding
          finalcapture.stagedPaths shouldBe (if (committedbase) Vector.empty else scopebinding.paths)
          finalcapture.postStageGuard.excludedFiles shouldBe effectiveguard.excludedFiles
          finalcapture.postStageGuard.excludedEntries shouldBe effectiveguard.excludedEntries
          if (exclusionappend && actualruntime.nonEmpty) {
            import org.goldenport.cncf.workflow.{WorkflowInstanceRecordJsonV1, WorkflowMechanicalTransition, WorkflowMechanicalTransitionJsonV1}
            finalcapture.schemaVersion shouldBe R.sourceCaptureSchema
            finalcapture.scope.get.schemaVersion shouldBe R.sourceScopeSchema
            finalcapture.postStageGuard.excludedFiles.map(_.path) shouldBe (exclusions ++ additions)
            Then("collection preserves the actual source capture index and every retained source fact")
            interruptedguard shouldBe Some(finalcapture.postStageGuard)
            indexpaths.map(path => Files.readAllBytes(path).toVector) shouldBe interruptedindices.get
            val beforecollection = interruptednative.get
            val aftercollection = _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, scopedrepair.sessionId)).toMap
            val stagepaths = if (committedbase) Vector.empty else scopebinding.paths
            val sourcekeys = Vector("dependency-source-inspection") ++ stagepaths.indices.flatMap(index =>
              Vector("dependency-source-stage-intent-" + index, "dependency-source-stage-result-" + index)) ++
              Vector("dependency-source-observation-intent", "dependency-source-observation-result", "candidate")
            def _source_identity_(key: String): String = SplitPhaseExplicitPartitionFixture.take(WorkflowMechanicalTransition.keyC(
              WorkflowMechanicalTransition.Reference(scopedrepair.sessionId + ":ordered-candidate:" + finalcapture.sourceRevision + ":" + key, "1")))
            sourcekeys.foreach { key =>
              val identity = _source_identity_(key)
              beforecollection.get(identity) should not be empty
              aftercollection.get(identity) shouldBe beforecollection.get(identity)
            }
            val candidaterow = parse(beforecollection(_source_identity_("candidate"))).fold(error => fail(error.message), identity)
            val candidatebytes = candidaterow.hcursor.get[String]("mechanicalPayload").fold(error => fail(error.message), identity)
            candidatebytes should startWith("cncf.workflow-json.v1:")
            val catalog = SplitPhaseExplicitPartitionFixture.take(GoalPhaseRuntimeCatalog.loadC())
            val definitions = SplitPhaseExplicitPartitionFixture.take(WorkflowInstanceRecordJsonV1.catalogC(Vector(catalog.definition)))
            val savedcandidate = SplitPhaseExplicitPartitionFixture.take(WorkflowMechanicalTransitionJsonV1.decodeC(
              candidatebytes.stripPrefix("cncf.workflow-json.v1:"), definitions, GoalPhaseNativeCandidate.codec)).payload.value
            savedcandidate.dependencySourceDirection shouldBe Some(capture.direction)
            savedcandidate.dependencySourceScope shouldBe finalcapture.scope
            savedcandidate.dependencySourceCapture shouldBe Some(finalcapture)
            savedcandidate.dependencySourceStages.map(_.path) shouldBe stagepaths
            if (committedbase) {
              R.modeOf(finalcapture) shouldBe "committed"
              finalcapture.preStageGuard shouldBe finalcapture.postStageGuard
              val committed = actualruntime.get.target.committedBaseSuccession.get
              committed.nextBase shouldBe R.bindingOf(finalcapture).base
              val unknown = oldunknown.get
              aftercollection.get(unknown._1) shouldBe Some(unknown._2)
              scopebinding.paths.indices.foreach { index =>
                Vector("dependency-source-stage-intent-", "dependency-source-stage-result-").foreach { prefix =>
                  aftercollection.get(_source_identity_(prefix + index)) shouldBe None
                }
              }
            }
            savedcandidate.dependencySourceStages.foreach { fact =>
              R.stageFactValid(fact) shouldBe true
              fact.before.excludedFiles shouldBe effectiveguard.excludedFiles
              fact.after.excludedFiles shouldBe effectiveguard.excludedFiles
              fact.before.excludedEntries shouldBe effectiveguard.excludedEntries
              fact.after.excludedEntries shouldBe effectiveguard.excludedEntries
            }
            _executions(root).size shouldBe 2
            val ownerbytes = Files.readAllBytes(repository.resolve(additions.head))
            When("an added excluded document drifts before independent review completion")
            Files.writeString(repository.resolve(additions.head), "other owner review-time byte drift\n")
            val refusedreview = _review(selected, finalreview, artifact = "noninput-exclusion-drift")
            Then("freshness refuses that completion without rerunning checks or absorbing the owner edit")
            refusedreview.exitCode should not be 0
            _executions(root).size shouldBe 2
            Files.write(repository.resolve(additions.head), ownerbytes)
            val retained = _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, scopedrepair.sessionId))
            When("the retained review is collected again through ordinary native re-entry")
            val collected = scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
            Then("the same issued work and complete source facts are retained without duplicate staging or adoption")
            collected.issuedWorkContext shouldBe scopedreview.issuedWorkContext
            _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, scopedrepair.sessionId)) shouldBe retained
            _executions(root).size shouldBe 2
          }
          _executions(root).size shouldBe 2
          Files.readAllBytes(prefix.firstReview.invocation.stdout).toVector shouldBe originalFirstReview
        }
        val finalrequest = finalreview.business[V.GoalPhaseAdvanceResult].work.get
        val reviewissues = finalrequest.issues.toVector.flatMap(_.issues).filter(issue =>
          issue.source == "REVIEW" && finalrequest.stepIdentity.contains(issue.step) &&
            Set(finalrequest.selectedPlan.repository, replacement.dependency.repository)(issue.repository))
        reviewissues.map(_.summary) shouldBe (if (extendscope)
          Vector("Qualify the required additional dependency source") else Vector.empty)
        val resolvedreport = Option.when(extendscope)(V.GoalPhaseIssueReport(reviewissues.map(issue =>
          V.GoalPhaseIssueDisposition(issue.identity, "RESOLVED",
            "SPECIFICATION-OWNED independent confirmation of the newly qualified additional source", None))))
        When("ordinary independent review explicitly resolves its actual issue and Step closure advances to whole-Phase validation")
        val phaseReview = _close_step(selected, finalreview, "source-revision-second", resolvedreport)
          .business[V.GoalPhaseAdvanceResult]
        val finalIndex = _native_fixture(scenario)(core => WorkflowManagedProcessExecution.executeGitC(core,
          GitOperation.IndexTree, replacement.dependency.workspace).flatMap(_.valueC))
        Then("the freshly qualified source route remains in use and unrelated index content is unchanged")
        phaseReview.work.get.kind shouldBe "ReviewPhase"
        if (committedbase) {
          Then("ordinary primary closure creates a real next primary commit while the dependency base stays fixed")
          val closed = phaseReview.work.get.completed.last.commit
          closed.identity should not be phaseReview.work.get.completed.head.commit.identity
          closed.parent shouldBe phaseReview.work.get.completed.head.commit.identity
          val actual = _native_fixture(scenario)(RepositorySyncManagedGitObservation.observeInWorkspaceC(_,
            TypedGitHostPlanFixture.workspace))
          actual.head shouldBe closed.identity
          actual.tree shouldBe closed.tree
          val unknown = oldunknown.get
          _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, selected.original.sessionId)).toMap
            .get(unknown._1) shouldBe Some(unknown._2)
        }
        reviewissues.foreach(issue => phaseReview.work.get.issues.get.issues.find(_.identity == issue.identity)
          .map(_.status) shouldBe Some("RESOLVED"))
        Files.readString(repository.resolve(exclusion)) shouldBe "another owner's staged document\n"
        finalIndex.value shouldBe finalcapture.postStageGuard.indexTree
      }
    }
  }

  "Linked dependency and conditional triage through public completion" should {
    "refuse a triage dependency selection and retain the standing dependency through CLEAN and closure" in {
      Given("a fresh business Plan with documentation assessment, immediate triage and an ordinarily admitted linked dependency")
      val policy = GoalPhaseStepAssessment.Policy("linked-document-contract", "documentation", "conditional", "off", "off",
        Vector(GoalPhaseStepAssessment.Observation("linked-meaning", ContextReference("selected-contract", "1"),
          "Does this selected input preserve its linked guarantee?", "immediate", "The guarantee is needed before closure", None)),
        semanticTriage = "conditional")
      val selected = _selected("dependency-conditional-triage", assessmentpolicy = Some(policy))
      _complete(selected)
      val scenario = selected.scenario
      val triage = scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
      triage.work.get.kind shouldBe "TriageStep"
      val context = GoalPhaseNativeMetadata.normalizePublicJson(json(triage.work.get.triage.get))
        .flatMap(_.as[GoalPhaseStepTriage.Context].left.map(_.message)).toOption.get
      context.checks.map(_.identity) shouldBe context.assessment.checks
      context.checks should not be empty
      context.checks.map(_.identity) should not contain "cncf-compile"
      context.checks.map(_.identity) should not contain "cncf-test"
      val saved = _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, triage.sessionId))
      val executed = _executions(scenario.root.resolve("dependency"))
      val clean = Json.obj("outcome" -> "clean".asJson,
        "summary" -> "SPECIFICATION-OWNED selected guarantee is retained".asJson, "findings" -> Json.arr())
      When("the public triage reply attempts to select the already standing dependency as new completion authority")
      val refused = _submit(scenario, triage, clean.mapObject(_.add("dependencyValidation", selected.selection.asJson)),
        V.WorkflowArtifactReference("specification-triage-forbidden-selection", "1"))
      Then("the exact pending native prefix and every dependency execution remain unchanged")
      refused.exitCode should not be 0
      _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, triage.sessionId)) shouldBe saved
      _executions(scenario.root.resolve("dependency")) shouldBe executed
      When("ordinary Advance reads the same issued triage and its retained passed validation")
      val repeated = scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
      Then("the full issued request and validation remain identical apart from the transient disabled journal observation")
      triage.validation should not be empty
      triage.validation.flatMap(_.assessmentJournal).map(_.status) shouldBe Some("disabled")
      repeated shouldBe triage.copy(validation = triage.validation.map(_.copy(assessmentJournal = None)),
        repairProgress = Some(V.GoalPhaseRepairProgress(0, 0, 2,
        Some("sm-workflow.repair-trend.v1"), Some(2), Some(2))))
      _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, triage.sessionId)) shouldBe saved
      _executions(scenario.root.resolve("dependency")) shouldBe executed

      Given("that same pending triage with only its bounded CLEAN disposition")
      _submit(scenario, triage, clean, V.WorkflowArtifactReference("specification-triage-clean", "1"))
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val closing = scenario.advance(selected.task)
      closing.business[V.GoalPhaseAdvanceResult].authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
      val authority = V.WorkflowAuthorityPreparationInput(closing.invocation.stdout.toUri.toASCIIString,
        _save(scenario, "triage-closure-reference", json(V.WorkflowArtifactReference("specification-triage-closure", "1").toRecord()).noSpaces),
        "participant", "normal-development-host", Instant.now().minusSeconds(60).toString,
        "entity:smworkflow.cli.host_evidence")
      val prepared = scenario.prepareHostInput("prepareAuthority", authority.toRecord())
      prepared.exitCode shouldBe 0
      scenario.selectPreparedImport(prepared)
      val head = scenario.candidateHead
      Files.writeString(scenario.root.resolve("dependency/repository/value.txt"), "changed-after-triage\n")
      When("the independently admitted primary closure encounters drift in the retained dependency")
      val drift = scenario.advance(selected.task)
      Then("the standing cursor obligation still prevents a commit without replaying validation or changing Session")
      drift.exitCode should not be 0
      scenario.candidateHead shouldBe head
      scenario.retrieve(selected.task) shouldBe selected.original.sessionId
      _executions(scenario.root.resolve("dependency")) shouldBe executed
    }
  }

  "DV-09..12 accepted single-dependency prefix through ordinary public operations" should {
    "reconstruct two canonical legacy Steps unchanged, refuse crossed history and qualify a genuine prefixed successor" in {
      Given("a specification-owned three-Step Plan and a genuinely completed, captured, checked, reviewed and closed first legacy Step")
      val value = _accepted_prefix("dependency-two-legacy-successor", overlap = true, legacySuccessor = true)
      val binding = value.binding.copy(paths = value.first.admission.binding.paths)
      val selected = _adopt_prefix(value.copy(binding = binding))
      val expected = D.Admission(binding, Vector(D.Approval("step-2", _second_proposal(binding),
        selected.selection.approvals.head.decision)))
      val oldfirst = Files.readString(value.firstReview.invocation.stdout)
      Files.writeString(Path.of(selected.scenario.host.target.root).resolve("docs/phase.md"), "Second legacy Step\n")
      When("the test-owned historical producer selects an absent-prefix typed native completion before any second candidate or check")
      _submit(selected.scenario, selected.resumed, Json.obj("outcome" -> "completed".asJson,
        "summary" -> "SPECIFICATION-OWNED legacy second completion".asJson, "findings" -> Json.arr(),
        "dependencyValidation" -> selected.selection.asJson), V.WorkflowArtifactReference("legacy-second-completion", "1"))
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val continuation = selected.resumed.issuedWorkContext.get.continuationId
      val legacybytes = _native_fixture(selected.scenario)(LegacyDependencyHistoryFixture.legacyCompletionC(_, continuation, expected))
      val second = selected.scenario.advance(selected.task)
      val secondrequest = second.business[V.GoalPhaseAdvanceResult]
      val secondcapture = _latest_capture(secondrequest)
      Then("ordinary native staging, observation and checks retain the original full candidate and original-base diff")
      secondrequest.work.get.kind shouldBe "ReviewStep"
      secondcapture.admission shouldBe expected
      secondcapture.candidate shouldBe value.first.candidate
      secondcapture.changedPaths shouldBe value.first.changedPaths
      secondcapture.diff shouldBe value.first.diff
      secondcapture.primaryBase shouldBe value.first.primaryBase
      legacybytes should not include "acceptedPrefix"
      Files.readString(value.firstReview.invocation.stdout) shouldBe oldfirst

      Given("that standing authenticated legacy admission and an independent primary-document repair verdict")
      _review(selected, second, Vector("Clarify the second Step guide"), artifact = "legacy-primary-repair")
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val repair = selected.scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
      val charges = repair.repairProgress
      Files.writeString(Path.of(selected.scenario.host.target.root).resolve("docs/phase.md"), "Clarified second legacy Step\n")
      When("same-Step repair submits the exact standing selection and ordinary native checks capture the unchanged dependency again")
      val fixed = repair.work.get.issues.get.issues.filter(_.status != "RESOLVED").map(issue =>
        V.GoalPhaseIssueDisposition(issue.identity, "FIXED", "SPECIFICATION-OWNED clarification of the primary guide", None))
      _submit(selected.scenario, repair, Json.obj("outcome" -> "completed".asJson,
        "summary" -> "SPECIFICATION-OWNED primary guide repair retaining the dependency candidate".asJson,
        "findings" -> Json.arr(), "issueReport" -> json(V.GoalPhaseIssueReport(fixed).toRecord()),
        "dependencyValidation" -> selected.selection.asJson), V.WorkflowArtifactReference("legacy-primary-repair-completion", "1"))
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val repaired = selected.scenario.advance(selected.task)
      val repairedrequest = repaired.business[V.GoalPhaseAdvanceResult]
      val repairedcapture = _latest_capture(repairedrequest)
      Then("the repaired generation inherits the exact absent-prefix admission without refunding history or repair charges")
      repairedcapture.admission shouldBe expected
      repairedcapture.generation should be > secondcapture.generation
      repairedcapture.candidate shouldBe value.first.candidate
      repairedcapture.changedPaths shouldBe value.first.changedPaths
      repairedcapture.diff shouldBe value.first.diff
      repairedrequest.repairProgress shouldBe charges

      Given("the repaired ReviewStep's actual tracked primary REVIEW issue for its current repository and Step")
      val reviewwork = repairedrequest.work.get
      val primaryissues = reviewwork.issues.toVector.flatMap(_.issues).filter(issue => issue.source == "REVIEW" &&
        issue.repository == reviewwork.selectedPlan.repository && reviewwork.stepIdentity.contains(issue.step))
      When("the independent reviewer prepares explicit resolution of the actual primary guide repair")
      val resolvedreport = V.GoalPhaseIssueReport(primaryissues.map(issue => V.GoalPhaseIssueDisposition(issue.identity,
        "RESOLVED", "SPECIFICATION-OWNED independent confirmation that the primary guide clarification is repaired", None)))
      Then("exactly the original guide review issue is covered using its actual tracked identity")
      primaryissues.map(_.summary) shouldBe Vector("Clarify the second Step guide")

      Given("two coherent legacy captures at the actual pending second ReviewStep completion gate")
      val file = selected.scenario.root.resolve("dependency/repository/inherited.txt")
      val originalbytes = Files.readAllBytes(file)
      val legacyexecutions = _executions(selected.scenario.root.resolve("dependency"))
      val legacyhead = selected.scenario.candidateHead
      val legacycharges = repairedrequest.repairProgress
      When("live dependency bytes drift before the second legacy review completes")
      Files.writeString(file, "unqualified legacy live drift\n")
      val drift = _review(selected, repaired, artifact = "legacy-live-drift")
      val standingreview = selected.scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
      Then("the actual completion freshness gate refuses without changing executions, primary HEAD or repair charges")
      _refused(drift, "GoalPhase dependency validation:")
      _executions(selected.scenario.root.resolve("dependency")) shouldBe legacyexecutions
      selected.scenario.candidateHead shouldBe legacyhead
      standingreview.repairProgress shouldBe legacycharges
      Files.write(file, originalbytes)

      When("the independently reviewed second legacy Step closes and its pending primary successor is redisplayed")
      val third = _close_step(selected, repaired, "legacy-second", issuereport = Some(resolvedreport))
        .business[V.GoalPhaseAdvanceResult]
      val snapshot = _native_fixture(selected.scenario)(LegacyDependencyHistoryFixture.snapshotC(_, value.first.session))
      val executions = _executions(selected.scenario.root.resolve("dependency"))
      val head = selected.scenario.candidateHead
      val repeated = selected.scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
      Then("both legacy completions, native intent/result/check/review/closure bytes and original bases reconstruct without mutation")
      third.work.get.stepIdentity shouldBe Some("step-3")
      third.work.get.completed.map(_.step) shouldBe Vector("step-1", "step-2")
      val successorcontinuation = third.issuedWorkContext.get.continuationId
      def successorrecord(rows: Map[String, String]): (Json, Json) = {
        val bytes = rows.getOrElse(successorcontinuation,
          fail("Missing authenticated successor continuation row: " + successorcontinuation))
        val row = parse(bytes).fold(error => fail("Malformed successor row JSON: " + error.message), identity)
        row.hcursor.get[String]("id").fold(error => fail("Missing successor row identity: " + error.message), identity) shouldBe successorcontinuation
        val payload = row.hcursor.get[String]("continuationPayload")
          .fold(error => fail("Missing successor continuation payload: " + error.message), identity)
        payload should startWith("cncf.workflow-json.v1:")
        val envelope = parse(payload.stripPrefix("cncf.workflow-json.v1:"))
          .fold(error => fail("Malformed successor continuation envelope: " + error.message), identity)
        envelope.hcursor.downField("continuation").get[String]("continuationId")
          .fold(error => fail("Missing native successor continuation identity: " + error.message), identity) shouldBe successorcontinuation
        val status = envelope.hcursor.get[String]("status")
          .fold(error => fail("Missing native successor continuation status: " + error.message), identity)
        Set("Available", "Completed") should contain(status)
        (row, envelope)
      }
      val pending = successorrecord(snapshot.toMap)
      pending._2.hcursor.get[String]("status").toOption shouldBe Some("Available")
      repeated.copy(message = third.message) shouldBe third
      third.message shouldBe repeated.message + " Commit hygiene mode=auto-fix; clean: Selected index is clean."
      _native_fixture(selected.scenario)(LegacyDependencyHistoryFixture.snapshotC(_, value.first.session)) shouldBe snapshot
      snapshot.exists { (_, bytes) => parse(bytes).toOption.flatMap(_.hcursor.get[String]("completionPayload").toOption)
        .contains("cncf.workflow-json.v1:" + legacybytes) } shouldBe true
      snapshot.count(_._1.startsWith("mechanical:")) should be > 20
      _executions(selected.scenario.root.resolve("dependency")) shouldBe executions

      for (variant <- Vector("session", "plan", "step", "generation", "admission", "tree")) {
        Given("one crossed typed observation result in the otherwise unchanged canonical legacy prefix: " + variant)
        val altered = _native_fixture(selected.scenario)(LegacyDependencyHistoryFixture.captureVariantC(_, value.first, variant))
        When("ordinary native reconstruction consumes that exact retained counterexample")
        val refused = selected.scenario.advance(selected.task)
        Then("the crossed Session, Plan, Step, generation, Admission or predecessor tree refuses without further effects")
        _refused(refused, "GoalPhase dependency validation:")
        _executions(selected.scenario.root.resolve("dependency")) shouldBe executions
        selected.scenario.candidateHead shouldBe head
        _native_fixture(selected.scenario)(LegacyDependencyHistoryFixture.restoreCaptureC(_, altered))
        _native_fixture(selected.scenario)(LegacyDependencyHistoryFixture.snapshotC(_, value.first.session)) shouldBe snapshot
      }

      Given("a codec-valid retained legacy completion whose claimed revision is not its actual adjacent native completion")
      val crossedboundary = _native_fixture(selected.scenario)(LegacyDependencyHistoryFixture.completionBoundaryVariantC(_, continuation))
      When("reconstruction reaches that real native result with a crossed completion boundary")
      val boundaryrefusal = selected.scenario.advance(selected.task)
      Then("provisional authentication cannot escape failed boundary verification or authorize later effects")
      _refused(boundaryrefusal, "GoalPhase native run:")
      _executions(selected.scenario.root.resolve("dependency")) shouldBe executions
      selected.scenario.candidateHead shouldBe head
      _native_fixture(selected.scenario)(LegacyDependencyHistoryFixture.restoreCompletionC(_, crossedboundary))
      _native_fixture(selected.scenario)(LegacyDependencyHistoryFixture.snapshotC(_, value.first.session)) shouldBe snapshot

      Given("the same two legacy ancestors and a newly approved third-Step dependency delta")
      val successor = binding.copy(identity = "specification-cncf-third-validation", step = "step-3", paths = Vector("successor.txt"),
        checks = binding.checks.zipWithIndex.map((check, index) => check.copy(identity = "third-" + check.identity, ordinal = index + 10)))
      val adopted = _adopt_prefix(PrefixStep(selected, repairedcapture, repaired, third, successor))
      Files.writeString(Path.of(selected.scenario.host.target.root).resolve("docs/phase.md"), "Native-prefixed third Step\n")
      Files.writeString(selected.scenario.root.resolve("dependency/repository/successor.txt"), "Fresh successor integration\n")
      When("a new public reference-only Submit is captured by the native owner")
      _submit(selected.scenario, adopted.resumed, Json.obj("outcome" -> "completed".asJson,
        "summary" -> "SPECIFICATION-OWNED fresh native successor".asJson, "findings" -> Json.arr(),
        "dependencyValidation" -> adopted.selection.asJson), V.WorkflowArtifactReference("native-third-completion", "1"))
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val fresh = selected.scenario.advance(selected.task)
      val freshcapture = _latest_capture(fresh.business[V.GoalPhaseAdvanceResult])
      Then("only that genuine successor receives an exact prefix and switches comparison to the newly checked latest tip")
      freshcapture.admission.acceptedPrefix.get.step shouldBe "step-2"
      freshcapture.admission.acceptedPrefix.get.candidate shouldBe repairedcapture.candidate
      freshcapture.admission.binding.base shouldBe value.first.admission.binding.base
      freshcapture.changedPaths shouldBe Vector("successor.txt")
      freshcapture.candidate.tree should not be repairedcapture.candidate.tree
      freshcapture.diff should not include "accepted first-Step input"
      val phaseoutput = _close_step(adopted, fresh, "native-third")
      val phase = phaseoutput.business[V.GoalPhaseAdvanceResult]
      phase.work.get.kind shouldBe "ReviewPhase"
      phase.work.get.completed.map(_.step) shouldBe Vector("step-1", "step-2", "step-3")
      _latest_capture(phase) shouldBe freshcapture
      val qualified = _executions(selected.scenario.root.resolve("dependency"))
      qualified.size shouldBe executions.size + 1
      Then("every legacy row remains byte-equal while only the authenticated successor completes with unchanged continuation context")
      val retained = _native_fixture(selected.scenario)(LegacyDependencyHistoryFixture.snapshotC(_, value.first.session)).toMap
      snapshot.filterNot { (id, _) => id == "instance" || id == successorcontinuation }
        .foreach { (id, bytes) => retained.get(id) shouldBe Some(bytes) }
      val completed = successorrecord(retained)
      completed._2.hcursor.get[String]("status").toOption shouldBe Some("Completed")
      completed._2.hcursor.downField("continuation").focus shouldBe pending._2.hcursor.downField("continuation").focus
      val completionpayload = completed._1.hcursor.get[String]("completionPayload")
        .fold(error => fail("Missing completed successor completion payload: " + error.message), identity)
      completionpayload should startWith("cncf.workflow-json.v1:")

      Given("successful primary-only integrated Phase checks on the latest dependency tip")
      Files.writeString(file, "drift before integrated legacy-successor review\n")
      When("integrated review attempts to complete against changed inherited dependency bytes")
      val reviewdrift = _review(adopted, phaseoutput, artifact = "legacy-successor-drift")
      Then("the latest-tip precondition refuses while all dependency executions remain retained")
      _refused(reviewdrift, "GoalPhase dependency validation:")
      _executions(selected.scenario.root.resolve("dependency")) shouldBe qualified
      Files.writeString(file, "accepted first-Step input\n")
      Given("the integrated Phase review's actual primary REVIEW issues and the earlier explicit guide resolution")
      val phaseprimaryissues = phase.work.get.issues.toVector.flatMap(_.issues).filter(issue =>
        issue.source == "REVIEW" && issue.repository == phase.work.get.selectedPlan.repository)
      Then("the Phase review covers exactly the previously authenticated primary issue identities")
      phaseprimaryissues.map(_.identity) shouldBe primaryissues.map(_.identity)
      When("the current integrated review reuses that exact explicit resolution report")
      val currentreview = _review(adopted, phaseoutput, artifact = "legacy-successor-current",
        issuereport = Some(resolvedreport))
      Then("the current integrated review accepts the explicitly covered primary issues")
      currentreview.business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      When("dependency bytes drift after integrated review and before Phase closing")
      Files.writeString(file, "drift before legacy-successor Phase closing\n")
      val phasehead = selected.scenario.candidateHead
      val closingdrift = selected.scenario.advance(selected.task)
      Then("Phase closing still refuses at the latest tip without changing candidate history or counters")
      _refused(closingdrift, "GoalPhase dependency validation:")
      selected.scenario.candidateHead shouldBe phasehead
      _executions(selected.scenario.root.resolve("dependency")) shouldBe qualified
    }

    "qualify disjoint and overlapping current deltas without rewriting the real legacy first Step" in {
      for (overlap <- Vector(false, true)) {
        Given("a genuinely captured, checked, independently reviewed and closed legacy first Step")
        val value = _accepted_prefix("dependency-prefix-" + overlap, overlap)
        val oldBytes = Files.readString(value.firstReview.invocation.stdout)
        val selected = _adopt_prefix(value)
        val before = _executions(selected.scenario.root.resolve("dependency"))
        When("the current pending second Step adopts a fresh host binding by reference-only Submit and Advance")
        _complete_prefix(selected, overlap)
        val output = selected.scenario.advance(selected.task)
        val request = output.business[V.GoalPhaseAdvanceResult]
        val capture = _latest_capture(request)
        Then("the whole candidate and incremental delta have distinct correct meanings and original provenance survives")
        request.work.get.kind shouldBe "ReviewStep"
        request.work.get.review.get.dependencyValidations.size shouldBe 2
        _capture(request) shouldBe value.first
        capture.admission.binding shouldBe value.binding
        capture.admission.approvals.map(_.step) shouldBe Vector("step-2")
        val prefix = capture.admission.acceptedPrefix.get
        prefix.schemaVersion shouldBe D.acceptedPrefixSchema
        prefix.sessionId shouldBe value.first.session
        prefix.plan shouldBe value.first.plan
        prefix.step shouldBe "step-1"
        prefix.paths.toSet shouldBe value.first.changedPaths.toSet
        prefix.candidate shouldBe value.first.candidate
        prefix.closureRevision.toLong should be < capture.sourceRevision.toLong
        capture.changedPaths.toSet shouldBe value.binding.paths.toSet
        capture.changedPaths should not contain "inherited.txt"
        capture.diff should not include "accepted first-Step input"
        if (overlap) capture.diff should include("new mode 100755")
        capture.admission.binding.base shouldBe value.first.admission.binding.base
        capture.primaryBase shouldBe value.first.primaryBase
        capture.candidate.head shouldBe value.first.candidate.head
        capture.candidate.tree should not be value.first.candidate.tree
        val actual = GoalPhaseNativeMetadata.normalizePublicJson(json(request.work.get.review.get.dependencyValidations.last))
          .fold(fail(_), identity)
        actual.hcursor.get[Vector[Json]]("selectedChecks").toOption.get.map(_.hcursor.get[Int]("ordinal").toOption.get) shouldBe Vector(6, 7)
        actual.hcursor.get[Vector[Json]]("checks").toOption.get.foreach(_successful_receipt)
        request.work.get.completed shouldBe value.current.work.get.completed
        request.repairProgress shouldBe selected.resumed.repairProgress
        _executions(selected.scenario.root.resolve("dependency")).size shouldBe before.size + 1
        Files.readString(value.firstReview.invocation.stdout) shouldBe oldBytes

        When("the second Step closes and primary-only integrated Phase checks run against the inherited tip")
        val phase = _close_step(selected, output, "prefix-second").business[V.GoalPhaseAdvanceResult]
        Then("integrated review retains both native ancestors while the primary Phase capture has no invented new dependency check")
        phase.work.get.kind shouldBe "ReviewPhase"
        phase.work.get.completed.map(_.step) shouldBe Vector("step-1", "step-2")
        phase.work.get.review.get.dependencyValidations.size shouldBe 2
        _latest_capture(phase) shouldBe capture
        _executions(selected.scenario.root.resolve("dependency")).size shouldBe before.size + 1
      }
    }

    "reject inherited-only edits, unowned additions and renamed-source escapes before current qualification" in {
      for (change <- Vector("inherited", "unowned", "rename")) {
        Given("a real accepted predecessor and a current disjoint admission")
        val value = _accepted_prefix("dependency-prefix-escape-" + change, overlap = false)
        val selected = _adopt_prefix(value)
        _complete_prefix(selected, overlap = false)
        val root = selected.scenario.root.resolve("dependency/repository")
        val executed = _executions(selected.scenario.root.resolve("dependency"))
        When("current work changes a historical-only path or leaves the current ownership boundary")
        change match {
          case "inherited" => Files.writeString(root.resolve("inherited.txt"), "unauthorized new inherited edit\n")
          case "unowned" => Files.writeString(root.resolve("outside.txt"), "unowned\n")
          case "rename" => Files.move(root.resolve("inherited.txt"), root.resolve("current.txt"),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        val rejected = selected.scenario.advance(selected.task)
        Then("ordinary Advance refuses without restaging inherited paths or borrowing prior success")
        _refused(rejected, "GoalPhase dependency validation:")
        _executions(selected.scenario.root.resolve("dependency")) shouldBe executed
        _capture(value.firstReview.business[V.GoalPhaseAdvanceResult]) shouldBe value.first
        selected.scenario.retrieve(selected.task) shouldBe value.first.session
      }
    }

    "reject caller-authored anchors and a same-Step binding replacement while keeping the issued frontier" in {
      Given("an admitted second Step whose predecessor can only be reconstructed by the native owner")
      val value = _accepted_prefix("dependency-prefix-forged-submit", overlap = true)
      val selected = _adopt_prefix(value)
      val forged = D.AcceptedPrefix(D.acceptedPrefixSchema, value.first.session, value.first.plan, "step-1", "1",
        value.first.candidate, value.first.changedPaths)
      val plain = Json.obj("outcome" -> "completed".asJson, "summary" -> "SPECIFICATION-OWNED forged submit".asJson,
        "findings" -> Json.arr(), "dependencyValidation" -> selected.selection.asJson)
      Given("caller-authored but codec-valid enriched native data and a crossed binding revision")
      val supplied = D.Admission(value.binding, Vector(D.Approval("step-2", _second_proposal(value.binding),
        selected.selection.approvals.head.decision)), Some(forged))
      D.admissionValid(supplied) shouldBe true
      val enriched = plain.mapObject(_.add("dependencyAdmission", supplied.asJson))
      val crossed = plain.mapObject(_.add("dependencyValidation", selected.selection.copy(binding =
        ContextReference(value.binding.identity, "2")).asJson))
      When("the actual Submit boundary receives both public counterexamples")
      val forgedresult = _submit(selected.scenario, selected.resumed, enriched, V.WorkflowArtifactReference("forged-prefix", "1"))
      val crossedresult = _submit(selected.scenario, selected.resumed, crossed, V.WorkflowArtifactReference("crossed-binding", "1"))
      Then("neither decoder-shaped native data nor a replacement reference can adopt lineage")
      _refused(forgedresult, "GoalPhase dependency validation:")
      _refused(crossedresult, "GoalPhase dependency validation:")
      selected.scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult].work.get.stepIdentity shouldBe Some("step-2")
      _complete_prefix(selected, overlap = true)
      _latest_capture(selected.scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]).admission.acceptedPrefix.get
        .candidate shouldBe value.first.candidate
    }

    "retain an actual failed current check and repair the same prefixed admission without refunding history" in {
      Given("overlapping current work whose actual normal Test fails after Compile")
      val value = _accepted_prefix("dependency-prefix-failed-test", overlap = true)
      val selected = _adopt_prefix(value)
      _complete_prefix(selected, overlap = true, failCheck = true)
      val output = selected.scenario.advance(selected.task)
      val repair = output.business[V.GoalPhaseAdvanceResult]
      repair.work.get.kind shouldBe "RepairStep"
      val executions = _executions(selected.scenario.root.resolve("dependency"))
      val failed = repair.work.get.checkFailure.get
      json(failed.toRecord()).noSpaces should include("second-cncf-test")
      When("same-Step repair corrects the input and returns the standing selection")
      Files.writeString(selected.scenario.root.resolve("dependency/repository/value.txt"), "expected\n")
      val replacement = _complete_repair(selected, repair, Some(selected.selection.copy(binding =
        ContextReference(value.binding.identity, "2"))))
      Then("a same-Step replacement refuses at the exact standing binding boundary")
      _refused(replacement, "GoalPhase dependency validation:")
      When("the valid standing selection completes and normal native checks run")
      _complete_repair(selected, repair).business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val fresh = selected.scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
      Then("fresh current checks qualify new bytes and every old accepted fact and repair charge remains")
      fresh.work.get.kind shouldBe "ReviewStep"
      val capture = _latest_capture(fresh)
      capture.admission.acceptedPrefix.get.candidate shouldBe value.first.candidate
      capture.admission.binding shouldBe value.binding
      capture.diff should include("new mode 100755")
      fresh.repairProgress shouldBe repair.repairProgress
      fresh.repairProgress.get.automaticCount should be > 0
      _executions(selected.scenario.root.resolve("dependency")).take(executions.size) shouldBe executions
      _executions(selected.scenario.root.resolve("dependency")).size shouldBe executions.size + 1
      _capture(fresh) shouldBe value.first
    }

    "keep completed prefixed stages and unknown observations attributable through existing public recovery" in {
      Given("real accepted first-Step provenance and fully recorded current stages interrupted before observation")
      val value = _accepted_prefix("dependency-prefix-observation-recovery", overlap = true)
      val selected = _adopt_prefix(value)
      _complete_prefix(selected, overlap = true)
      _interrupt(selected, "goal-dependency-stage-recorded:current.txt")
      val described = _describe_recovery(selected)
      val target = SplitPhaseExplicitPartitionFixture.take(GoalPhaseCandidateRecovery.targetC(
        described.business[V.GoalPhaseCandidateRecoveryDescriptionResult].target))
      target.dependencyPrefix.last.key shouldBe "dependency-stage-result-1"
      target.inspection.dependencyAdmission.get.acceptedPrefix.get.candidate shouldBe value.first.candidate
      _recover(selected, described, "AuthorizeNewObservation").business[V.GoalPhaseCandidateRecoveryResult].accepted shouldBe true
      When("a new dependency observation is itself interrupted and independently continued on its exact snapshot")
      _interrupt(selected, "goal-dependency-observation-prepared")
      val continuation = _describe_continuation(selected)
      val current = SplitPhaseExplicitPartitionFixture.take(GoalPhaseCandidateObservationContinuation.targetC(
        continuation.business[V.GoalPhaseCandidateObservationContinuationDescriptionResult].target))
      current.priorObservations.last.dependencyPrefix.last.key shouldBe "dependency-observation-intent"
      _continue_observation(selected, continuation).business[V.GoalPhaseCandidateObservationContinuationResult].accepted shouldBe true
      val review = selected.scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
      Then("the existing owner uses new observation provenance and never replays historical or completed stages")
      review.work.get.kind shouldBe "ReviewStep"
      val capture = _latest_capture(review)
      capture.admission shouldBe target.inspection.dependencyAdmission.get
      capture.admission.acceptedPrefix.get.candidate shouldBe value.first.candidate
      _capture(review) shouldBe value.first
      current.original.dependencyPrefix shouldBe target.dependencyPrefix
      current.priorObservations.foreach(_.termination shouldBe "termination-unconfirmed")
      current.dependencyRepository.get.dirty.map(_.path) should contain("inherited.txt")
    }

    "refuse drift at five freshness gates while preserving unchanged pending review redisplay" in {
      for (boundary <- Vector("before-check", "after-check", "review-issue", "review-completion", "closure")) {
        Given("a separately admitted current delta on a genuinely accepted prefix at the actual " + boundary + " boundary")
        val value = _accepted_prefix("dependency-prefix-drift-" + boundary, overlap = true)
        val selected = _adopt_prefix(value)
        _complete_prefix(selected, overlap = true)
        var closing = Option.empty[F.Result]
        var completion = Option.empty[F.Result]
        boundary match {
          case "before-check" => _interrupt(selected, "goal-candidate-observed")
          case "after-check" => _interrupt(selected, "goal-check-recorded:second-cncf-test")
          case "review-issue" => _interrupt(selected, "goal-checks-admitted")
          case "review-completion" => completion = Some(selected.scenario.advance(selected.task))
          case "closure" =>
            val review = selected.scenario.advance(selected.task)
            _review(selected, review, artifact = "prefix-drift").business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
            closing = Some(selected.scenario.advance(selected.task))
        }
        val before = _executions(selected.scenario.root.resolve("dependency"))
        val head = selected.scenario.candidateHead
        When("dependency bytes change before the selected freshness transition or pending review completion")
        Files.writeString(selected.scenario.root.resolve("dependency/repository/current.txt"), "stale current candidate\n")
        val completionrefusals = completion.map(output => _review(selected, output, artifact = "prefix-review-drift"))
        val advanced = selected.scenario.advance(selected.task)
        Then("the consuming gate refuses and any pending review redisplay preserves the original request")
        completionrefusals.foreach(_refused(_, "GoalPhase dependency validation:"))
        if (boundary == "review-completion") {
          val original = completion.get.business[V.GoalPhaseAdvanceResult]
          val pending = advanced.business[V.GoalPhaseAdvanceResult]
          pending.work.get.kind shouldBe "ReviewStep"
          pending.work.get.review shouldBe original.work.get.review
          pending.repairProgress shouldBe original.repairProgress
          pending.issuedWorkContext shouldBe original.issuedWorkContext
        } else _refused(advanced, "GoalPhase dependency validation:")
        _executions(selected.scenario.root.resolve("dependency")) shouldBe before
        selected.scenario.candidateHead shouldBe head
        closing.foreach(_.business[V.GoalPhaseAdvanceResult].authorityContext should not be empty)
      }
    }

    "keep a primary-only successor and integrated Phase review and closing bound to the latest qualified dependency" in {
      Given("a closed legacy dependency Step and a genuinely primary-only second Step")
      val value = _accepted_prefix("dependency-prefix-primary-only", overlap = false)
      val selected = value.selected
      Files.writeString(Path.of(selected.scenario.host.target.root).resolve("docs/phase.md"), "Primary-only next Step\n")
      _submit(selected.scenario, value.current, Json.obj("outcome" -> "completed".asJson,
        "summary" -> "SPECIFICATION-OWNED primary-only completion".asJson, "findings" -> Json.arr()),
        V.WorkflowArtifactReference("specification-primary-only", "1"))
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val executions = _executions(selected.scenario.root.resolve("dependency"))
      val file = selected.scenario.root.resolve("dependency/repository/inherited.txt")
      When("dependency drift is hidden behind omission of dependencyValidation")
      Files.writeString(file, "unqualified hidden dependency edit\n")
      val primaryrefusal = selected.scenario.advance(selected.task)
      Then("even primary-only native validation refuses the stale accepted tip")
      _refused(primaryrefusal, "GoalPhase dependency validation:")
      _executions(selected.scenario.root.resolve("dependency")) shouldBe executions
      Files.writeString(file, "accepted first-Step input\n")
      val second = selected.scenario.advance(selected.task)
      val review = second.business[V.GoalPhaseAdvanceResult]
      review.work.get.kind shouldBe "ReviewStep"
      _latest_capture(review) shouldBe value.first
      val phaseOutput = _close_step(selected, second, "primary-only-second")
      val phase = phaseOutput.business[V.GoalPhaseAdvanceResult]
      phase.work.get.kind shouldBe "ReviewPhase"
      _latest_capture(phase) shouldBe value.first
      When("dependency bytes change before integrated review completion")
      Files.writeString(file, "drift before integrated review completion\n")
      val phaserefusal = _review(selected, phaseOutput, artifact = "phase-drift")
      Then("the integrated review refuses the changed dependency tip")
      _refused(phaserefusal, "GoalPhase dependency validation:")
      Files.writeString(file, "accepted first-Step input\n")
      _review(selected, phaseOutput, artifact = "phase-current").business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      When("the same dependency changes after accepted integrated review and before Phase closing")
      Files.writeString(file, "drift before Phase closing\n")
      val head = selected.scenario.candidateHead
      val closingrefusal = selected.scenario.advance(selected.task)
      Then("Phase closure refuses while both real Step commits and earlier dependency receipts remain")
      _refused(closingrefusal, "GoalPhase dependency validation:")
      selected.scenario.candidateHead shouldBe head
      _executions(selected.scenario.root.resolve("dependency")) shouldBe executions
    }
  }

  "DV-01..08 ordinary two-repository CLI continuation" should {
    "capture and check a fresh dependency after submitting an unstaged same-Step review repair" in {
      Given("passed native checks and a specification-owned independent review of an executable dependency data file")
      val selected = _selected("dependency-review-repair-submit")
      val output = _review_for_repair(selected)
      val originalreview = output.business[V.GoalPhaseAdvanceResult]
      val oldcapture = _capture(originalreview)
      oldcapture.diff should include("new mode 100755")
      _review(selected, output, Vector("Remove executable permission from dependency value.txt"))
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val repair = selected.scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
      repair.work.get.kind shouldBe "RepairStep"
      repair.work.get.review.get.dependencyValidations.map(json) shouldBe originalreview.work.get.review.get.dependencyValidations.map(json)
      val prior = repair.work.get.review.get.priorReviews.map(json)
      prior.last.hcursor.downField("result").get[String]("outcome").toOption shouldBe Some("repair")
      repair.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(1, 1, 2, Some("sm-workflow.repair-trend.v1"), Some(2), Some(2)))
      val primarychecks = _executions(selected.scenario.root)
      val dependencychecks = _executions(selected.scenario.root.resolve("dependency"))

      When("the issued repair removes that permission without staging and submits its actual FIXED issue report")
      selected.scenario.root.resolve("dependency/repository/value.txt").toFile.setExecutable(false, false) shouldBe true
      val submitted = _complete_repair(selected, repair).business[V.GoalPhaseWorkSubmissionResult]
      Then("Submit accepts the repair without running checks or demanding the old staged candidate")
      submitted.accepted shouldBe true
      submitted.outcome shouldBe "accepted"
      submitted.sessionId shouldBe selected.original.sessionId
      _executions(selected.scenario.root) shouldBe primarychecks
      _executions(selected.scenario.root.resolve("dependency")) shouldBe dependencychecks

      When("ordinary Advance stages the admitted correction and runs the unchanged selected checks")
      val fresh = selected.scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
      val capture = _capture(fresh)
      val validation = GoalPhaseNativeMetadata.normalizePublicJson(json(fresh.work.get.review.get.dependencyValidations.head))
        .fold(reason => fail("Cannot decode the actual dependency validation: " + reason), identity)
      Then("fresh review receives a new actual dependency capture while binding, base, history and repair charges remain")
      fresh.work.get.kind shouldBe "ReviewStep"
      capture.candidate.tree should not be oldcapture.candidate.tree
      capture.diff should not be oldcapture.diff
      capture.diff should not include "new mode 100755"
      capture.diff should include("+expected")
      capture.sourceRevision should not be oldcapture.sourceRevision
      capture.generation should be > oldcapture.generation
      capture.admission shouldBe oldcapture.admission
      capture.primaryBase shouldBe oldcapture.primaryBase
      capture.session shouldBe oldcapture.session
      capture.plan shouldBe oldcapture.plan
      capture.step shouldBe oldcapture.step
      fresh.sessionId shouldBe selected.original.sessionId
      fresh.work.get.originalBase shouldBe selected.original.work.get.originalBase
      fresh.work.get.selectedPlan shouldBe selected.original.work.get.selectedPlan
      fresh.work.get.completed shouldBe repair.work.get.completed
      fresh.repairProgress shouldBe repair.repairProgress
      fresh.work.get.review.get.priorReviews.map(json) shouldBe prior
      fresh.work.get.issues.get.issues.map(_.identity) shouldBe repair.work.get.issues.get.issues.map(_.identity)
      fresh.work.get.issues.get.issues.map(_.latestDisposition) should contain(Some("FIXED"))
      val oldvalidation = GoalPhaseNativeMetadata.normalizePublicJson(json(originalreview.work.get.review.get.dependencyValidations.head))
        .fold(reason => fail("Cannot decode the original dependency validation: " + reason), identity)
      val selectedchecks = validation.hcursor.get[Vector[Json]]("selectedChecks")
        .fold(error => fail("Actual selected checks are required: " + error.message), identity)
      val oldselectedchecks = oldvalidation.hcursor.get[Vector[Json]]("selectedChecks")
        .fold(error => fail("Original selected checks are required: " + error.message), identity)
      selectedchecks shouldBe oldselectedchecks
      val checks = validation.hcursor.get[Vector[Json]]("checks").toOption.get
      checks.map(_.hcursor.get[String]("identity").toOption.get) shouldBe Vector("cncf-compile", "cncf-test")
      checks.foreach(_successful_receipt)
      validation.hcursor.get[Vector[Json]]("executions").toOption.get.map(
        _.hcursor.get[String]("workspace").toOption.get).distinct shouldBe Vector(capture.admission.binding.dependency.workspace)
      _executions(selected.scenario.root).size shouldBe primarychecks.size + 1
      _executions(selected.scenario.root.resolve("dependency")).size shouldBe dependencychecks.size + 1
      _executions(selected.scenario.root).take(primarychecks.size) shouldBe primarychecks
      _executions(selected.scenario.root.resolve("dependency")).take(dependencychecks.size) shouldBe dependencychecks
      fresh.completion shouldBe None
    }

    "issue RepairStep after an admitted unstaged correction without reusing the old candidate as a freshness target" in {
      Given("an accepted independent repair verdict whose original dependency capture remains historical context")
      val selected = _selected("dependency-review-repair-issue")
      val output = _review_for_repair(selected)
      val originalreview = output.business[V.GoalPhaseAdvanceResult]
      _review(selected, output, Vector("Remove executable permission from dependency value.txt"))
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val executed = _executions(selected.scenario.root.resolve("dependency"))
      selected.scenario.root.resolve("dependency/repository/value.txt").toFile.setExecutable(false, false) shouldBe true
      When("ordinary Advance issues the next repair before any new candidate staging")
      val repair = selected.scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
      Then("the same Step retains old capture, review verdict, Plan and base without rerunning old checks")
      repair.work.get.kind shouldBe "RepairStep"
      repair.sessionId shouldBe originalreview.sessionId
      repair.work.get.stepIdentity shouldBe originalreview.work.get.stepIdentity
      repair.work.get.originalBase shouldBe originalreview.work.get.originalBase
      repair.work.get.selectedPlan shouldBe originalreview.work.get.selectedPlan
      _capture(repair) shouldBe _capture(originalreview)
      repair.work.get.repairDirective.get.findings shouldBe Vector("Remove executable permission from dependency value.txt")
      repair.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(1, 1, 2, Some("sm-workflow.repair-trend.v1"), Some(2), Some(2)))
      _executions(selected.scenario.root.resolve("dependency")) shouldBe executed
      When("that issued repair completes and ordinary Advance captures its current admitted state")
      _complete_repair(selected, repair).business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val review = selected.scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
      Then("new review carries fresh dependency proof under the unchanged approval and repair window")
      review.work.get.kind shouldBe "ReviewStep"
      _capture(review).candidate.tree should not be _capture(originalreview).candidate.tree
      _capture(review).admission shouldBe _capture(originalreview).admission
      review.repairProgress shouldBe repair.repairProgress
      _executions(selected.scenario.root.resolve("dependency")).size shouldBe executed.size + 1
    }

    "refuse an unapproved dependency path during fresh capture after review repair" in {
      Given("an issued same-Step repair retaining its original exact dependency approval")
      val selected = _selected("dependency-review-repair-foreign-path")
      val output = _review_for_repair(selected)
      _review(selected, output, Vector("Remove executable permission from dependency value.txt"))
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val repair = selected.scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
      val dependency = selected.scenario.root.resolve("dependency/repository")
      dependency.resolve("value.txt").toFile.setExecutable(false, false) shouldBe true
      Files.writeString(dependency.resolve("outside.txt"), "SPECIFICATION-OWNED unapproved change\n")
      val primarychecks = _executions(selected.scenario.root)
      val dependencychecks = _executions(selected.scenario.root.resolve("dependency"))
      When("repair Submit is followed by the ordinary fresh candidate owner")
      _complete_repair(selected, repair).business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val refused = selected.scenario.advance(selected.task)
      Then("historical review is not a freshness target but fresh ownership still refuses before any new checks")
      refused.exitCode should not be 0
      _executions(selected.scenario.root) shouldBe primarychecks
      _executions(selected.scenario.root.resolve("dependency")) shouldBe dependencychecks
      selected.scenario.retrieve(selected.task) shouldBe selected.original.sessionId
      Files.readString(dependency.resolve("outside.txt")) shouldBe "SPECIFICATION-OWNED unapproved change\n"
    }

    "refuse replacement of the adopted dependency selection at repair completion" in {
      Given("an issued repair with an immutable dependency binding and approval history")
      val selected = _selected("dependency-review-repair-selection")
      val output = _review_for_repair(selected)
      _review(selected, output, Vector("Remove executable permission from dependency value.txt"))
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val repair = selected.scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
      selected.scenario.root.resolve("dependency/repository/value.txt").toFile.setExecutable(false, false) shouldBe true
      val executed = _executions(selected.scenario.root.resolve("dependency"))
      When("the correction attempts to name another binding revision instead of the adopted selection")
      val replacement = selected.selection.copy(binding = selected.selection.binding.copy(revision = "2"))
      val refused = _complete_repair(selected, repair, Some(replacement))
      Then("ordinary completion refuses without checks, a new Session or replacement approval")
      refused.exitCode should not be 0
      _executions(selected.scenario.root.resolve("dependency")) shouldBe executed
      selected.scenario.retrieve(selected.task) shouldBe selected.original.sessionId
      _capture(repair).admission.approvals.map(_.decision) shouldBe selected.selection.approvals.map(_.decision)
    }

    "preserve a completed dependency capture across an interrupted remaining primary observation" in {
      Given("the ordinary native pipeline saves its actual dependency capture before primary observation")
      val selected = _selected("dependency-primary-recovery")
      _complete(selected)
      _interrupt(selected, "goal-dependency-observation-recorded")
      val described = _describe_recovery(selected)
      val target = GoalPhaseCandidateRecovery.targetC(described.business[V.GoalPhaseCandidateRecoveryDescriptionResult].target).toOption.get
      val capture = GoalPhaseCandidateRecovery.preparedPayload(target).dependency.get
      _recover(selected, described, "AuthorizeNewObservation").business[V.GoalPhaseCandidateRecoveryResult].accepted shouldBe true
      _recover(selected, described, "AuthorizeNewObservation").exitCode should not be 0
      _executions(selected.scenario.root) shouldBe empty
      _executions(selected.scenario.root.resolve("dependency")) shouldBe empty
      _interrupt(selected, "goal-candidate-observation-prepared")
      val continuation = _describe_continuation(selected)
      val continued = GoalPhaseCandidateObservationContinuation.targetC(
        continuation.business[V.GoalPhaseCandidateObservationContinuationDescriptionResult].target).toOption.get
      continued.original shouldBe target
      GoalPhaseCandidateObservationContinuation.preparedPayload(continued).dependency shouldBe Some(capture)
      _continue_observation(selected, continuation).business[V.GoalPhaseCandidateObservationContinuationResult].accepted shouldBe true
      When("the separately admitted ordinary Advance observes only the remaining primary candidate")
      val review = selected.scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
      Then("review receives the original dependency source, both checks and unchanged Session/base/counters")
      review.work.get.kind shouldBe "ReviewStep"
      _capture(review).sourceRevision shouldBe capture.sourceRevision
      review.sessionId shouldBe selected.original.sessionId
      review.work.get.originalBase shouldBe selected.original.work.get.originalBase
      review.repairProgress shouldBe selected.original.repairProgress
      _executions(selected.scenario.root.resolve("dependency")).size shouldBe 1
      selected.scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult] shouldBe review
    }

    "retain an unknown dependency stage and use only an explicit same-Step semantic revision" in {
      Given("a durable dependency stage intent with no authoritative result")
      val selected = _selected("dependency-stage-revision")
      _complete(selected)
      _interrupt(selected, "goal-dependency-stage-prepared:value.txt")
      val described = _describe_recovery(selected)
      val description = described.business[V.GoalPhaseCandidateRecoveryDescriptionResult]
      val target = GoalPhaseCandidateRecovery.targetC(description.target).toOption.get
      description.choices shouldBe Vector("ReviseWork", "Defer")
      target.dependencyPrefix.last.key shouldBe "dependency-stage-intent-0"
      _executions(selected.scenario.root.resolve("dependency")) shouldBe empty
      _recover(selected, described, "ReviseWork").business[V.GoalPhaseCandidateRecoveryResult].outcome shouldBe "revision-work-authorized"
      When("a separate Advance issues new work rather than replaying the old stage intent")
      val work = selected.scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
      Then("the same Plan, base, approvals and repair counters continue in the next semantic generation")
      work.work.get.kind shouldBe "ImplementStep"
      // The public WorkView intentionally omits the internal generation number;
      // prove a genuinely new issued work frontier through its public identity.
      work.issuedWorkContext.get.continuationId should not be selected.resumed.issuedWorkContext.get.continuationId
      work.issuedWorkContext.get.expectedRevision.toLong should be > selected.resumed.issuedWorkContext.get.expectedRevision.toLong
      work.work.get.selectedPlan shouldBe selected.original.work.get.selectedPlan
      work.work.get.originalBase shouldBe selected.original.work.get.originalBase
      work.repairProgress shouldBe selected.original.repairProgress
      _executions(selected.scenario.root.resolve("dependency")) shouldBe empty
      _complete(selected.copy(resumed = work))
      selected.scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult].work.get.kind shouldBe "ReviewStep"
      target.oldObservationOutcome shouldBe "UNKNOWN"
    }

    "retain both unknown dependency observations and require a distinct independently bound continuation" in {
      Given("completed stages followed by an unknown dependency observation in the original namespace")
      val selected = _selected("dependency-observation-recovery")
      _complete(selected)
      _interrupt(selected, "goal-dependency-observation-prepared")
      val described = _describe_recovery(selected)
      val original = GoalPhaseCandidateRecovery.targetC(described.business[V.GoalPhaseCandidateRecoveryDescriptionResult].target).toOption.get
      original.dependencyPrefix.last.key shouldBe "dependency-observation-intent"
      _recover(selected, described, "AuthorizeNewObservation").business[V.GoalPhaseCandidateRecoveryResult].accepted shouldBe true
      _interrupt(selected, "goal-dependency-observation-prepared")
      val continuation = _describe_continuation(selected)
      val target = GoalPhaseCandidateObservationContinuation.targetC(
        continuation.business[V.GoalPhaseCandidateObservationContinuationDescriptionResult].target).toOption.get
      target.original shouldBe original
      target.priorObservations.last.dependencyPrefix.last.key shouldBe "dependency-observation-intent"
      target.priorObservations.last.outcome shouldBe "UNKNOWN"
      _continue_observation(selected, continuation).business[V.GoalPhaseCandidateObservationContinuationResult].accepted shouldBe true
      When("ordinary Advance captures the dependency in that distinct direction namespace and then the primary")
      val review = selected.scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
      Then("both checks are attributable without staging replay or retrospective success for either old intent")
      review.work.get.kind shouldBe "ReviewStep"
      _capture(review).sourceRevision shouldBe (target.frontierRevision.toLong + 1).toString
      review.sessionId shouldBe selected.original.sessionId
      review.repairProgress shouldBe selected.original.repairProgress
      _executions(selected.scenario.root.resolve("dependency")).size shouldBe 1
    }

    "M3Lint retain an old-shaped unknown intent through public recovery, failed FixCheck repair and fresh review" in {
      import org.goldenport.cncf.workflow.*
      import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
      import GoalPhaseNativeValidation as N
      import GoalPhaseValidationRecovery as R
      import SplitPhaseExplicitPartitionFixture.take

      Given("an isolated host with an actual primary Test and dependency Compile/FixCheck, using the selected ordinary launcher")
      val selected = _selected("dependency-M3Lint-persistence", dependencychecks = Some(Vector(
        N.Check("cncf-compile", 2, WorkflowSbtValidation.Kind.Compile),
        N.Check("cncf-lint", 3, WorkflowSbtValidation.Kind.FixCheck))))
      val scenario = selected.scenario
      val dependencyroot = scenario.root.resolve("dependency")
      val binding = scenario.host.goal.dependencyValidation.get
      val input = Path.of(binding.root).resolve("value.txt")
      val catalog = take(GoalPhaseRuntimeCatalog.loadC())
      val definitions = take(WorkflowInstanceRecordJsonV1.catalogC(Vector(catalog.definition)))
      def _snapshot_(): Map[String, String] =
        _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, selected.original.sessionId)).toMap
      def _key_(source: String, key: String): String = take(WorkflowMechanicalTransition.keyC(
        WorkflowMechanicalTransition.Reference(selected.original.sessionId + ":ordered-validation:" + source + ":" + key, "1")))
      def _validation_(rows: Map[String, String], source: String, key: String): N.Payload = {
        val row = parse(rows(_key_(source, key))).fold(error => fail(error.message), identity)
        val bytes = row.hcursor.get[String]("mechanicalPayload").fold(error => fail(error.message), identity)
        bytes should startWith("cncf.workflow-json.v1:")
        take(WorkflowMechanicalTransitionJsonV1.decodeC(bytes.stripPrefix("cncf.workflow-json.v1:"), definitions, N.codec)).payload.value
      }
      def _history_(rows: Map[String, String]) = take(WorkflowInstanceRecordJsonV1.decodeC(rows("instance"), definitions)).history
      def _count_(name: String): Vector[String] = {
        val path = dependencyroot.resolve(name)
        if (Files.exists(path)) Files.readString(path).linesIterator.toVector else Vector.empty
      }
      def _recover_(input: V.GoalPhaseValidationRecoveryInput): F.Result = scenario.call("command",
        R.recoveryOperation, Vector("--input", json(input.toRecord()).noSpaces))
      def _direction_(described: F.Result, choice: String, name: String): V.GoalPhaseValidationRecoveryInput = {
        val description = described.business[V.GoalPhaseValidationRecoveryDescriptionResult]
        val answer = V.GoalPhaseValidationRecoveryAnswer(choice,
          "SPECIFICATION-OWNED direction for this exact dependency lint attempt; old effects remain UNKNOWN")
        val reference = V.WorkflowArtifactReference(name, "1")
        _provision(selected, described, "validation-recovery", answer, reference)
        V.GoalPhaseValidationRecoveryInput(description.sessionId, description.target, choice, answer.rationale, reference)
      }

      When("semantic completion is followed by an actual failing lint candidate and interruption after its process but before result save")
      _complete(selected)
      Files.writeString(input, "incorrect\n")
      _interrupt(selected, "goal-check-executed:cncf-lint")
      val described = scenario.call("command", R.describeOperation, Vector("--sessionId", selected.original.sessionId))
      val description = described.business[V.GoalPhaseValidationRecoveryDescriptionResult]
      val target = take(R.targetC(description.target))
      val original = _snapshot_()
      val history = _history_(original)
      val prefix = original - "instance"
      val primaryruns = _executions(scenario.root)
      val compileruns = _count_("sbt-compile-executions.txt")
      val lintruns = _count_("sbt-fix-check-executions.txt")
      val intentkey = _key_(target.sourceRevision, "check-intent-2")
      val intent = _validation_(original, target.sourceRevision, "check-intent-2")
      Then("the exact resultless lint intent retains the actual successful prefix and its independent dependency target")
      target.sessionId shouldBe selected.original.sessionId
      target.step shouldBe binding.step
      target.checkIdentity shouldBe "cncf-lint"
      target.checkOrdinal shouldBe 3
      target.attempt shouldBe 0
      target.intentRevision shouldBe target.frontierRevision
      target.dependency shouldBe Some(D.RecoverySubject(ContextReference(binding.identity, binding.revision.toString),
        binding.dependency.repository, binding.dependency.workspace, binding.base, intent.dependency.get.candidate))
      target.base shouldBe intent.dependency.get.primaryBase
      target.candidate shouldBe intent.candidate
      target.planIdentity shouldBe intent.dependency.get.plan.identity
      target.planRevision shouldBe intent.dependency.get.plan.revision
      intent.receipts.map(_.identity) shouldBe Vector("test", "cncf-compile")
      intent.receipts.foreach(receipt => receipt.exitCode shouldBe Some(0))
      intent.dependencyExecutions shouldBe Vector(D.Execution("cncf-compile", 0, binding.dependency.workspace, "compile"))
      original.contains(_key_(target.sourceRevision, "check-result-2")) shouldBe false
      primaryruns.size shouldBe 1
      compileruns.size shouldBe 1
      lintruns.size shouldBe 1
      description.choices shouldBe Vector("AuthorizeNewAttempt", "Defer")

      for (_ <- 1 to 2) {
        When("ordinary installed collection encounters the same resultless intent")
        val waiting = scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
        Then("the same Session remains UNKNOWN with unchanged repair progress")
        waiting.sessionId shouldBe selected.original.sessionId
        waiting.message should include("non-replayable")
        waiting.repairProgress shouldBe selected.original.repairProgress
        When("the public recovery description is read again")
        val repeateddescription = scenario.call("command", R.describeOperation, Vector("--sessionId", selected.original.sessionId))
          .business[V.GoalPhaseValidationRecoveryDescriptionResult]
        Then("the exact recovery target and choices remain unchanged")
        repeateddescription shouldBe description
      }
      Then("decoding and collection confer no new authority, history, receipt or process")
      _snapshot_() shouldBe original
      _executions(scenario.root) shouldBe primaryruns
      _count_("sbt-compile-executions.txt") shouldBe compileruns
      _count_("sbt-fix-check-executions.txt") shouldBe lintruns

      Given("missing evidence, an explicitly prepared Defer and a stale target for this same dependency")
      val missing = V.GoalPhaseValidationRecoveryInput(description.sessionId, description.target, "AuthorizeNewAttempt",
        "SPECIFICATION-OWNED unbound direction", V.WorkflowArtifactReference("M3Lint-missing", "1"))
      When("public recovery receives unbound direction")
      val missingresult = _recover_(missing)
      Then("the missing independent evidence refuses the recovery request")
      missingresult.exitCode should not be 0
      Given("independently prepared specification-owned Defer evidence")
      val deferred = _direction_(described, "Defer", "M3Lint-defer")
      When("public recovery receives that exact Defer direction")
      val deferredresult = _recover_(deferred).business[V.GoalPhaseValidationRecoveryResult]
      Then("neither recovery input adds an effect or changes the unknown native prefix")
      deferredresult.outcome shouldBe "deferred"
      _snapshot_() shouldBe original
      _count_("sbt-fix-check-executions.txt") shouldBe lintruns

      Given("separately prepared specification-owned Human evidence bound to the actual public target and AuthorizeNewAttempt answer")
      val authorized = _direction_(described, "AuthorizeNewAttempt", "M3Lint-authorize")
      When("public recovery checks direction for a stale target")
      val staleresult = _recover_(authorized.copy(target = R.targetRecord(target.copy(planRevision = "stale-plan"))))
      Then("the stale direction refuses without changing the original prefix")
      staleresult.exitCode should not be 0
      _snapshot_() shouldBe original
      When("public recovery receives the exact independently bound direction")
      val accepted = _recover_(authorized).business[V.GoalPhaseValidationRecoveryResult]
      val directed = _snapshot_()
      Then("one direction is appended for a new attempt while every old intent and successful prefix byte survives")
      accepted.accepted shouldBe true
      accepted.outcome shouldBe "new-attempt-authorized"
      _history_(directed).dropRight(1) shouldBe history
      prefix.foreach { (key, bytes) => directed.get(key) shouldBe Some(bytes) }
      When("the already consumed authorization is submitted again")
      val duplicateresult = _recover_(authorized)
      Then("the duplicate refuses without another direction or process")
      duplicateresult.exitCode should not be 0
      _snapshot_() shouldBe directed
      _executions(scenario.root) shouldBe primaryruns
      _count_("sbt-compile-executions.txt") shouldBe compileruns
      _count_("sbt-fix-check-executions.txt") shouldBe lintruns

      When("a separate ordinary Advance executes only the authorized missing lint on the unchanged failing candidate")
      val failedoutput = scenario.advance(selected.task)
      val repair = failedoutput.business[V.GoalPhaseAdvanceResult]
      val failedrows = _snapshot_()
      val failed = _validation_(failedrows, target.sourceRevision, "check-result-2-retry-1")
      val outcome = _validation_(failedrows, target.sourceRevision, "validation")
      Then("the real nonzero FixCheck result persists and ordinary validation issues RepairStep instead of a save conflict")
      repair.sessionId shouldBe selected.original.sessionId
      repair.work.get.kind shouldBe "RepairStep"
      json(repair.work.get.checkFailure.get.toRecord()).noSpaces should include("cncf-lint")
      failed.receipts.last.identity shouldBe "cncf-lint"
      failed.receipts.dropRight(1) shouldBe intent.receipts
      failed.receipts.last.exitCode shouldBe Some(1)
      (failed.receipts.last.stdout + failed.receipts.last.stderr) should include("SPECIFICATION-OWNED FixCheck")
      failed.dependencyExecutions.last shouldBe D.Execution("cncf-lint", 1, binding.dependency.workspace, "fix-check")
      N.successful(failed.receipts, failed.selected) shouldBe false
      N.codec.decode(N.codec.encode(failed)) shouldBe Right(failed)
      outcome.outcome shouldBe Some("repair-required")
      failed.newAttempts.map(_.checkIdentity) shouldBe Vector("cncf-lint")
      failed.newAttempts.head.attempt shouldBe 0
      failed.newAttempts.head.intentRevision shouldBe target.intentRevision
      failedrows(intentkey) shouldBe original(intentkey)
      failedrows.contains(_key_(target.sourceRevision, "check-result-2")) shouldBe false
      prefix.foreach { (key, bytes) => failedrows.get(key) shouldBe Some(bytes) }
      _history_(failedrows).take(history.size) shouldBe history
      _executions(scenario.root) shouldBe primaryruns
      _count_("sbt-compile-executions.txt") shouldBe compileruns
      _count_("sbt-fix-check-executions.txt").size shouldBe lintruns.size + 1
      val failedcounts = _count_("sbt-fix-check-executions.txt")
      When("ordinary collection repeats the standing failed-check RepairStep")
      val repeatedrepair = scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
      Then("the same repair is returned without another check or native record")
      repeatedrepair shouldBe repair
      _snapshot_() shouldBe failedrows
      _count_("sbt-fix-check-executions.txt") shouldBe failedcounts

      When("the actual issued RepairStep corrects the same isolated dependency value and submits the standing binding")
      Files.writeString(input, "expected\n")
      val submittedrepair = _complete_repair(selected, repair).business[V.GoalPhaseWorkSubmissionResult]
      Then("the actual issued repair submission is accepted")
      submittedrepair.accepted shouldBe true
      When("ordinary Advance validates the corrected candidate for independent review")
      val review = scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
      val reviewed = _snapshot_()
      val receipt = review.work.get.checksReceipt.get
      val receiptprefix = selected.original.sessionId + ":ordered-validation:"
      Then("fresh actual FixCheck success reaches ReviewStep with the original unknown and failed history, Plan, base and charged repair intact")
      receipt should startWith(receiptprefix)
      receipt should endWith(":validation")
      val freshsource = receipt.stripPrefix(receiptprefix).stripSuffix(":validation")
      val passed = _validation_(reviewed, freshsource, "validation")
      review.work.get.kind shouldBe "ReviewStep"
      review.sessionId shouldBe selected.original.sessionId
      review.work.get.originalBase shouldBe selected.original.work.get.originalBase
      review.work.get.selectedPlan shouldBe selected.original.work.get.selectedPlan
      review.repairProgress shouldBe repair.repairProgress
      review.repairProgress.get.automaticCount should be > 0
      _latest_capture(review).admission.binding shouldBe binding
      passed.outcome shouldBe Some("passed")
      passed.receipts.last.identity shouldBe "cncf-lint"
      passed.receipts.last.exitCode shouldBe Some(0)
      passed.dependencyExecutions.last.operation shouldBe "fix-check"
      N.successful(passed.receipts, passed.selected) shouldBe true
      N.codec.decode(N.codec.encode(passed)) shouldBe Right(passed)
      reviewed(intentkey) shouldBe original(intentkey)
      reviewed.contains(_key_(target.sourceRevision, "check-result-2")) shouldBe false
      prefix.foreach { (key, bytes) => reviewed.get(key) shouldBe Some(bytes) }
      failedrows.filter(_._1.startsWith("mechanical:")).foreach { (key, bytes) => reviewed.get(key) shouldBe Some(bytes) }
      _history_(reviewed).take(_history_(failedrows).size) shouldBe _history_(failedrows)
      _executions(scenario.root).take(primaryruns.size) shouldBe primaryruns
      _executions(scenario.root).size shouldBe primaryruns.size + 1
      _count_("sbt-compile-executions.txt").take(compileruns.size) shouldBe compileruns
      _count_("sbt-compile-executions.txt").size shouldBe compileruns.size + 1
      _count_("sbt-fix-check-executions.txt").size shouldBe failedcounts.size + 1
      val finalprimary = _executions(scenario.root)
      val finalcompile = _count_("sbt-compile-executions.txt")
      val finallint = _count_("sbt-fix-check-executions.txt")
      When("ordinary collection redisplays the standing successful review")
      val repeatedreview = scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
      Then("collection retains all receipts and adds no process or native history")
      repeatedreview shouldBe review
      _snapshot_() shouldBe reviewed
      _executions(scenario.root) shouldBe finalprimary
      _count_("sbt-compile-executions.txt") shouldBe finalcompile
      _count_("sbt-fix-check-executions.txt") shouldBe finallint
    }

    "observe and validate the dependency independently while preserving the original Session and primary base" in {
      Given("two isolated actual Git repositories and prepared SPECIFICATION-OWNED scope direction")
      val selected = _selected("dependency-validation-success")
      When("semantic Submit selects references and ordinary Advance owns actual candidate capture and SBT")
      _complete(selected)
      val review = selected.scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
      Then("independent review receives the actual dependency candidate, diff and both typed check results")
      review.sessionId shouldBe selected.original.sessionId
      review.work.get.kind shouldBe "ReviewStep"
      review.work.get.originalBase shouldBe selected.original.work.get.originalBase
      review.work.get.selectedPlan shouldBe selected.original.work.get.selectedPlan
      val context = review.work.get.review.get
      context.checks.map(_.identity) shouldBe Vector("test")
      context.dependencyValidations.size shouldBe 1
      val dependency = json(context.dependencyValidations.head)
      dependency.hcursor.downField("capture").downField("admission").downField("binding")
        .downField("dependency").get[String]("repository").toOption shouldBe Some("goldenport-cncf")
      dependency.hcursor.downField("capture").get[String]("session").toOption shouldBe Some(review.sessionId)
      dependency.hcursor.get[Vector[Json]]("checks").toOption.get.size shouldBe 2
      _executions(selected.scenario.root).size shouldBe 1
      _executions(selected.scenario.root.resolve("dependency")).size shouldBe 1
      selected.scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult] shouldBe review
      _executions(selected.scenario.root.resolve("dependency")).size shouldBe 1
      review.completion shouldBe None
    }

    "refuse an unapproved dependency path before any product check" in {
      Given("the same admitted binding with an additional unapproved worktree file")
      val selected = _selected("dependency-validation-foreign-path")
      _complete(selected)
      Files.writeString(Path.of(selected.scenario.host.goal.dependencyValidation.get.root).resolve("outside.txt"),
        "SPECIFICATION-OWNED foreign change\n")
      When("ordinary Advance captures the actual dependency repository")
      val refused = selected.scenario.advance(selected.task)
      Then("scope cannot widen and no primary or dependency test result is invented")
      refused.exitCode should not be 0
      _executions(selected.scenario.root) shouldBe empty
      _executions(selected.scenario.root.resolve("dependency")) shouldBe empty
    }

    "preserve the same Session when the dependency's ordinary SBT permission is absent" in {
      Given("an isolated two-repository host whose dependency SBT capability is explicitly denied")
      val selected = _selected("dependency-validation-denied-sbt", "sbt-capability")
      _complete(selected)
      When("ordinary Advance reaches the existing typed SBT admission owner")
      val stopped = selected.scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
      Then("the admitted scope and original base do not become dependency success or a new Session")
      stopped.sessionId shouldBe selected.original.sessionId
      stopped.completion shouldBe None
      stopped.validation.get.outcome should not be Some("passed")
      stopped.validation.get.dependencyValidation should not be empty
      _executions(selected.scenario.root.resolve("dependency")) shouldBe empty
      stopped.repairProgress shouldBe selected.original.repairProgress
    }

    "refuse wrong actual root, branch, HEAD or base tree before dependency validation" in {
      Given("fixed actual Git programs and valid-shaped specification declarations naming different facts")
      val cases = Table(("name", "change"),
        ("root", (value: D.Binding) => value.copy(root = value.root + "-wrong")),
        ("branch", (value: D.Binding) => value.copy(branch = "wrong-branch")),
        ("head", (value: D.Binding) => value.copy(base = value.base.copy(head = "0" * 40))),
        ("tree", (value: D.Binding) => value.copy(base = value.base.copy(tree = "0" * 40))))
      When("each independently prepared CLI scenario reaches actual native candidate capture")
      Then("the property holds that declarations cannot substitute for matching Git objects and root")
      forAll(cases) { (name, change) =>
        val selected = _selected("dependency-validation-wrong-" + name, bindingtransform = change)
        _complete(selected)
        selected.scenario.advance(selected.task).exitCode should not be 0
        _executions(selected.scenario.root.resolve("dependency")) shouldBe empty
      }
    }

    "refuse missing dependency workspace management without a candidate or test success" in {
      Given("dependency process capabilities without the required workspace manage grant")
      val scenario = F.freshDependency("dependency-validation-denied-workspace", "workspace-manage")
      When("Start admits the actual host process declaration")
      val refused = scenario.start()
      Then("configuration admission rejects the incoherent grants before either repository can run SBT")
      refused.exitCode should not be 0
      refused.invocation.logs should include("Invalid runtime process declaration")
      _executions(scenario.root) shouldBe empty
      _executions(scenario.root.resolve("dependency")) shouldBe empty
    }

    "refuse dependency drift at independent review completion while retaining the earlier actual checks" in {
      Given("both actual check sets and an issued ReviewStep in the same isolated Session")
      val selected = _selected("dependency-validation-review-drift")
      _complete(selected)
      val review = selected.scenario.advance(selected.task)
      review.business[V.GoalPhaseAdvanceResult].work.get.kind shouldBe "ReviewStep"
      val executed = _executions(selected.scenario.root.resolve("dependency"))
      Files.writeString(selected.scenario.root.resolve("dependency/repository/value.txt"), "changed-after-checks\n")
      When("prepared SPECIFICATION-OWNED review evidence attempts completion for the old candidate")
      val refused = _review(selected, review)
      Then("completion refuses the changed bytes without rerunning, discarding the old checks or accepting closure")
      refused.exitCode should not be 0
      _executions(selected.scenario.root.resolve("dependency")) shouldBe executed
      selected.scenario.retrieve(selected.task) shouldBe selected.original.sessionId
    }

    "recheck the same dependency before an independently admitted primary closure" in {
      Given("a completed SPECIFICATION-OWNED independent review and the ordinary primary closure question")
      val selected = _selected("dependency-validation-closure-drift")
      _complete(selected)
      val review = selected.scenario.advance(selected.task)
      _review(selected, review).business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val closing = selected.scenario.advance(selected.task)
      closing.business[V.GoalPhaseAdvanceResult].authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
      val scenario = selected.scenario
      val authority = V.WorkflowAuthorityPreparationInput(closing.invocation.stdout.toUri.toASCIIString,
        _save(scenario, "linked-closure-reference", json(V.WorkflowArtifactReference("specification-linked-closure", "1").toRecord()).noSpaces),
        "participant", "normal-development-host", Instant.now().minusSeconds(60).toString,
        "entity:smworkflow.cli.host_evidence")
      val prepared = scenario.prepareHostInput("prepareAuthority", authority.toRecord())
      prepared.exitCode shouldBe 0
      scenario.selectPreparedImport(prepared)
      val head = scenario.candidateHead
      val executed = _executions(scenario.root.resolve("dependency"))
      Files.writeString(scenario.root.resolve("dependency/repository/value.txt"), "changed-before-closure\n")
      When("ordinary Advance reaches the existing primary closure owner")
      val refused = scenario.advance(selected.task)
      Then("the changed dependency prevents the commit without resetting Session history or claiming dependency publication")
      refused.exitCode should not be 0
      scenario.candidateHead shouldBe head
      scenario.retrieve(selected.task) shouldBe selected.original.sessionId
      _executions(scenario.root.resolve("dependency")) shouldBe executed
    }
  }
}

/** One normal installed initial-source scenario, with fixture-owned evidence.
  * @since   Nov.  5, 2026
  * @version Nov.  8, 2026
  * @author  ASAMI, Tomoharu
  */
@org.scalatest.DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.DependencyValidationTestDeclaration])
final class NativeInitialDependencySourceRevisionCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Installed initial dependency source revision" should {
    "retain an old installed episode and resume initial pre-capture source succession through fresh checks review and Step commit" in {
      Given("the actual normal installed mode and caller-selected initial-source episode fixture")
      val support = new NativeGoalDependencyValidationCliSpec
      When("the existing fixture implementation qualifies that exact initial-source episode")
      val episode = support.qualifyInitialSource(this)
      Then("the actual episode locator is retained as a regular file")
      Files.isRegularFile(episode) shouldBe true
    }
  }
}
