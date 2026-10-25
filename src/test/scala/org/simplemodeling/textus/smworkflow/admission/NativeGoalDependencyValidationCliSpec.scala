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
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.DependencyValidationTestDeclaration])
final class NativeGoalDependencyValidationCliSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
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
    }.toMap
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
    bindingTransform: D.Binding => D.Binding = value => value, legacySuccessor: Boolean = false,
    assessmentpolicy: Option[GoalPhaseStepAssessment.Policy] = None): Selected = {
    val scenario = F.freshDependency(name, denial, bindingTransform)
    if (legacySuccessor) _legacy_successor_plan(scenario)
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
    val task = scenario.ownTask("dependency-validation")
    val started = scenario.start().business[V.GoalPhaseStartResult]
    scenario.retain(task, started)
    val original = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
    val binding = scenario.host.goal.dependencyValidation.get
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
    var observed = Vector.empty[String]
    val acknowledgement: String => Consequence[Unit] = value => {
      observed :+= value
      if (value == point) Consequence.stateConflict("SPECIFICATION-OWNED interruption at " + point) else Consequence.unit
    }
    val actualHost = SplitPhaseExplicitPartitionFixture.take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(
      parse(parameters("sm-workflow.runtime.definition")).toOption.get))
    try _with_runtime_(scenario.root, "Goal", Some(actualHost), hostParameters = parameters,
      goalAcknowledgement = Some(acknowledgement), componentInstance = "default") { (owner, component, actor, _) =>
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
    artifact: String = "linked", issuereport: Option[V.GoalPhaseIssueReport] = None): F.Result = {
    val scenario = selected.scenario
    val request = output.business[V.GoalPhaseAdvanceResult]
    Set("ReviewStep", "ReviewPhase") should contain(request.work.get.kind)
    val result = Json.obj("outcome" -> Json.fromString(if (findings.isEmpty) "accept" else "repair"),
      "summary" -> Json.fromString("SPECIFICATION-OWNED review of both actual candidates"),
      "findings" -> Json.fromValues(findings.map(Json.fromString))).mapObject(fields =>
        issuereport.fold(fields)(report => fields.add("issueReport", json(report.toRecord()))))
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
    issuereport: Option[V.GoalPhaseIssueReport] = None): F.Result = {
    _review(selected, review, artifact = artifact, issuereport = issuereport)
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
  private def _accepted_prefix(name: String, overlap: Boolean, legacySuccessor: Boolean = false): PrefixStep = {
    val selected = _selected(name, bindingTransform = _.copy(paths = Vector("value.txt", "inherited.txt")),
      legacySuccessor = legacySuccessor)
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
      When("ordinary Advance reads the same issued triage after the transition-only validation observation")
      val repeated = scenario.advance(selected.task).business[V.GoalPhaseAdvanceResult]
      Then("the full issued request remains identical without replaying checks or retaining a transient response observation")
      repeated shouldBe triage.copy(validation = None)
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
      repeated shouldBe third
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
        val selected = _selected("dependency-validation-wrong-" + name, bindingTransform = change)
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
