package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.file.{Files, Path, StandardOpenOption}
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.processexecution.{GitObjectId, GitOperation, GitOperationKind, GitPath, GitSelectionId, GitSelectionParameters, ProcessExecutionResult, SbtExecutionAttempt, SbtOperation}
import org.goldenport.cncf.workflow.{ContextReference, WorkflowInstanceRecordJsonV1, WorkflowMechanicalReceiptLimits, WorkflowMechanicalReceiptParameters, WorkflowMechanicalTransition, WorkflowMechanicalTransitionJsonV1}
import org.goldenport.protocol.Request
import org.goldenport.record.RecordPresentable
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseDependencyValidation as D
import GoalPhaseDependencyValidation.given
import GoalPhasePrerequisitePublicationRecovery as R
import GoalPhasePrerequisitePublicationUnknownRecovery as U
import GoalPhasePrerequisitePrimaryRepair as P
import GoalPhaseCurrentSourceArtifactAdoption as A
import GoalPhaseDependencySourceRevision as S
import RepositorySyncManagedGitObservation as G
import NativeWorkflowCliFixture as F
import NativeCallerExecutionFixture.json
import SplitPhaseExplicitPartitionFixture.take

/** Explicit installed qualification on tiny disposable projects. Human answers
  * and host provisioning are SPECIFICATION-OWNED. No consumer store is read or
  * seeded, and no full sm-workflow source tree is copied.
  * @since   Nov.  1, 2026
  * @version Nov.  2, 2026
  * @author  ASAMI, Tomoharu
  */
@org.scalatest.DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.DependencyValidationTestDeclaration])
final class NativeGoalPrerequisitePublicationRecoveryCliSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  private val _source = "src/main/scala/RecoveryMarker.scala"
  private val _consumer = "src/test/scala/ProductCheck.scala"
  private val _primary_source = "src/main/scala/PrimaryProduct.scala"
  private val _primary_sentinel = "docs/unselected-primary-sentinel.md"
  private val _primary_note = "docs/primary-repair-note.md"
  private val _primary_note_text = "The primary Compile repair supplies the required qualified-primary-api.\n"
  private val _primary_renamed_phase = "docs/primary-repair-phase.md"
  private val _primary_suite = "fixture.PrimaryProductCheckSpec"
  private val _primary_checks = Vector(
    GoalPhaseNativeValidation.Check("primary-compile", 14, WorkflowSbtValidation.Kind.Compile),
    GoalPhaseNativeValidation.Check("primary-focused", 15, WorkflowSbtValidation.Kind.TestOnly))
  private val _receipt_limits = take(WorkflowMechanicalReceiptLimits.createC(16 * 1024 * 1024))
  private val _corrected = "class RecoveryMarker { def value: String = \"approved-correction\" }\n"
  private def _array(value: Json, key: String): Vector[Json] = value.hcursor.get[Vector[Json]](key).toOption.get
  private def _lines(path: Path): Vector[String] = if (Files.exists(path)) Files.readString(path).linesIterator.toVector else Vector.empty
  private def _stages(scenario: F.Scenario, sessionid: String): Int = {
    val snapshot = _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, sessionid)).toMap
    val catalog = take(GoalPhaseRuntimeCatalog.loadC())
    val definitions = take(WorkflowInstanceRecordJsonV1.catalogC(Vector(catalog.definition)))
    val record = take(WorkflowInstanceRecordJsonV1.decodeC(snapshot("instance"), definitions))
    record.history.count { entry =>
      val cause = entry.causation.value
      cause.startsWith(R.causationPrefix) && cause.drop(R.causationPrefix.length).matches("[0-9]+:stage-[0-9]+-result")
    }
  }
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json =
    parse(Files.readString(root.resolve(".textus/config.json"))).toOption.get.hcursor
      .get[String]("textus.subsystem.process-execution.plan").toOption.flatMap(value => parse(value).toOption).get

  /** The existing specification legacy producer and bounded read-only observations use this fixture runtime. */
  private def _native_fixture[A](scenario: F.Scenario)(body: org.goldenport.cncf.action.ActionCall.Core => Consequence[A]): A = {
    val config = scenario.root.resolve(".textus/config.json")
    val assembly = scenario.root.resolve(".textus/assembly.yaml")
    val configbytes = Files.readAllBytes(config)
    val assemblybytes = Files.readAllBytes(assembly)
    val parameters = parse(Files.readString(config)).toOption.get.asObject.get.toVector.map { (key, value) =>
      key -> value.asString.getOrElse(value.noSpaces)
    }.toMap - "textus.component.file"
    val host = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(parse(parameters("sm-workflow.runtime.definition")).toOption.get))
    var result = Option.empty[A]
    try _with_runtime_(scenario.root, "Goal", Some(host), hostParameters = parameters, componentInstance = "default") {
      (_, _, _, prepare) => prepare(call => body(call.core).map(value => { result = Some(value); () }))
    } finally {
      Files.write(config, configbytes)
      Files.write(assembly, assemblybytes)
    }
    result.get
  }

  private def _primary_snapshot(scenario: F.Scenario): (G.Observed, String) = _native_fixture(scenario) { core => for {
    observed <- G.observeC(core)
    index <- G.indexTreeC(core)
  } yield (observed, index) }

  private def _primary_candidates(scenario: F.Scenario, session: String): Vector[GoalPhaseNativeCandidate.Payload] =
    _native_fixture(scenario) { core => for {
      snapshot <- LegacyDependencyHistoryFixture.snapshotC(core, session)
      catalog <- GoalPhaseRuntimeCatalog.loadC()
      definitions <- WorkflowInstanceRecordJsonV1.catalogC(Vector(catalog.definition))
    } yield snapshot.flatMap { case (_, bytes) =>
      parse(bytes).toOption.flatMap(_.hcursor.get[String]("mechanicalPayload").toOption)
        .filter(_.startsWith("cncf.workflow-json.v1:")).flatMap(value =>
          WorkflowMechanicalTransitionJsonV1.decodeC(value.stripPrefix("cncf.workflow-json.v1:"),
            definitions, GoalPhaseNativeCandidate.codec).toOption.map(_.payload.value))
    } }

  private def _history(scenario: F.Scenario, session: String): String =
    _native_fixture(scenario) { core => for {
      snapshot <- LegacyDependencyHistoryFixture.snapshotC(core, session)
      catalog <- GoalPhaseRuntimeCatalog.loadC()
      definitions <- WorkflowInstanceRecordJsonV1.catalogC(Vector(catalog.definition))
      actual <- WorkflowInstanceRecordJsonV1.decodeC(snapshot.toMap.apply("instance"), definitions)
      persisted <- WorkflowInstanceRecordJsonV1.encodeC(actual)
    } yield persisted }

  /** Same paths and typed routes; retain every earlier workspace/program/grant. */
  private def _select_binding(scenario: F.Scenario, binding: D.Binding): Unit = {
    val file = scenario.root.resolve(".textus/config.json")
    val config = parse(Files.readString(file)).toOption.get
    val current = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(
      parse(config.hcursor.get[String]("sm-workflow.runtime.definition").toOption.get).toOption.get))
    val host = current.copy(goal = current.goal.copy(dependencyValidation = Some(binding)))
    Files.writeString(file, config.mapObject(_.add("sm-workflow.runtime.definition",
      ThreeWorkflowRuntimeParameters.encode(host).noSpaces.asJson)).noSpaces)
  }
  private def _capture(request: V.GoalPhaseAdvanceResult, last: Boolean = false): D.Capture = {
    val values = request.work.get.review.get.dependencyValidations
    GoalPhaseNativeMetadata.normalizePublicJson(json(if (last) values.last else values.head))
      .flatMap(_.hcursor.get[D.Capture]("capture").left.map(_.message)).fold(fail(_), identity)
  }
  private def _close_step(scenario: F.Scenario, task: Path, output: F.Result, label: String = "first"): F.Result = {
    val request = output.business[V.GoalPhaseAdvanceResult]
    val result = Json.obj("outcome" -> "accept".asJson, "summary" -> s"SPECIFICATION-OWNED independent $label-Step review".asJson,
      "findings" -> Json.arr())
    val reference = V.WorkflowArtifactReference("fixture-" + label + "-step-review", "1")
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("specification-independent-reviewer"), None, None)
    val input = V.WorkflowEvidencePreparationInput("GoalPhase", "review", output.invocation.stdout.toUri.toASCIIString,
      _save(scenario, label + "-review-result", result.noSpaces),
      _save(scenario, label + "-review-reference", json(reference.toRecord()).noSpaces),
      _save(scenario, label + "-review-execution", json(execution.toRecord()).noSpaces),
      "normal-development-host", Instant.now().minusSeconds(60).toString, "entity:smworkflow.cli.host_evidence", None)
    val prepared = scenario.prepareHostInput("prepareEvidence", input.toRecord())
    prepared.exitCode shouldBe 0
    scenario.selectPreparedImport(prepared)
    scenario.call("command", request.submitOperation.get, Vector("--sessionId", request.submitSessionId.get,
      "--result", result.noSpaces, "--resultReference", json(reference.toRecord()).noSpaces,
      "--executionEvidence", json(execution.toRecord()).noSpaces)).business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
    val closing = scenario.advance(task)
    closing.business[V.GoalPhaseAdvanceResult].authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
    val authority = V.WorkflowAuthorityPreparationInput(closing.invocation.stdout.toUri.toASCIIString,
      _save(scenario, label + "-closure-reference", json(V.WorkflowArtifactReference("fixture-" + label + "-step-closure", "1").toRecord()).noSpaces),
      "participant", "normal-development-host", Instant.now().minusSeconds(60).toString, "entity:smworkflow.cli.host_evidence")
    val admitted = scenario.prepareHostInput("prepareAuthority", authority.toRecord())
    admitted.exitCode shouldBe 0
    scenario.selectPreparedImport(admitted)
    scenario.advance(task)
  }
  private def _refused(result: F.Result): Unit = withClue(result.invocation.logs) {
    Files.readString(result.invocation.stdout).trim shouldBe empty
    Files.readString(result.invocation.stderr) should include("status=")
  }
  private def _save(scenario: F.Scenario, name: String, value: String): String = {
    val path = scenario.root.resolve(name + ".json")
    Files.writeString(path, value, StandardOpenOption.CREATE_NEW)
    path.toUri.toASCIIString
  }
  private def _submit(scenario: F.Scenario, request: V.GoalPhaseAdvanceResult, result: Json, identity: String): Unit = {
    scenario.call("command", request.submitOperation.get, Vector("--sessionId", request.submitSessionId.get,
      "--result", result.noSpaces, "--resultReference", json(V.WorkflowArtifactReference(identity, "1").toRecord()).noSpaces))
      .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
  }
  private def _provision(scenario: F.Scenario, description: F.Result, kind: String,
    answer: RecordPresentable, reference: V.WorkflowArtifactReference, label: String): V.WorkflowExecutionEvidence = {
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
    val input = V.WorkflowEvidencePreparationInput("GoalPhase", kind, description.invocation.stdout.toUri.toASCIIString,
      _save(scenario, label + "-answer", json(answer.toRecord()).noSpaces),
      _save(scenario, label + "-reference", json(reference.toRecord()).noSpaces),
      _save(scenario, label + "-execution", json(execution.toRecord()).noSpaces),
      "normal-development-host", Instant.now().minusSeconds(60).toString, "entity:smworkflow.cli.host_evidence", Some("participant"))
    val prepared = scenario.prepareHostInput("prepareEvidence", input.toRecord())
    prepared.exitCode shouldBe 0
    scenario.selectPreparedImport(prepared)
    execution
  }

  private def _adopt_prefix(scenario: F.Scenario, task: Path, request: V.GoalPhaseAdvanceResult,
    binding: D.Binding): (V.GoalPhaseAdvanceResult, D.Selection, D.Admission) = {
    _select_binding(scenario, binding)
    val proposal = GoalPhaseDependencyScope.Proposal("fixture-second-scope", "1", "IMPLEMENTATION",
      binding.requester, binding.dependency, ContextReference("fixture-linked-contract", "1"),
      "SPECIFICATION-OWNED second-Step same-source scope", binding.paths, Vector("Use the accepted native prefix"),
      Vector(ContextReference(binding.step, "1")), Vector.empty)
    _submit(scenario, request, Json.obj("outcome" -> "decision-needed".asJson,
      "summary" -> "SPECIFICATION-OWNED second scope".asJson, "findings" -> Json.arr(),
      "dependencyProposal" -> proposal.asJson), "fixture-second-proposal")
    val output = scenario.advance(task)
    val question = output.business[V.GoalPhaseAdvanceResult]
    val answer = V.GoalPhaseOrderedDecisionAnswer("ApproveDependencyScope", "SPECIFICATION-OWNED second-Step scope")
    val reference = V.WorkflowArtifactReference("fixture-second-approval", "1")
    val execution = _provision(scenario, output, "human", answer, reference, "second-scope")
    scenario.call("command", question.submitOperation.get, Vector("--sessionId", question.submitSessionId.get,
      "--answer", json(answer.toRecord()).noSpaces, "--resultReference", json(reference.toRecord()).noSpaces,
      "--executionEvidence", json(execution.toRecord()).noSpaces)).business[V.GoalPhaseOrderedDecisionSubmissionResult].accepted shouldBe true
    val resumed = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
    val selection = D.Selection(ContextReference(binding.identity, binding.revision.toString),
      Vector(D.ApprovalReference(ContextReference(proposal.identity, proposal.revision), ContextReference(reference.identity, reference.revision))))
    val admission = D.Admission(binding, Vector(D.Approval(binding.step, proposal, ContextReference(reference.identity, reference.revision))))
    (resumed, selection, admission)
  }

  /** All grants and policies are fixture host inputs selected before Start.
    * Existing public typed Git/SBT plans own every actual effect. */
  private def _configure(scenario: F.Scenario, publicationdependency: String, unknown: Boolean = false,
    primaryfailure: Boolean = false): LocalPublication.Target = {
    val path = scenario.root.resolve(".textus/config.json")
    val config = parse(Files.readString(path)).toOption.get
    val existing = parse(config.hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
    val current = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(
      parse(config.hcursor.get[String]("sm-workflow.runtime.definition").toOption.get).toOption.get))
    val host = current.copy(goal = current.goal.copy(candidatePaths =
      (current.goal.candidatePaths ++ Vector("docs/preserved.md") ++ Option.when(unknown)(_consumer) ++
        Option.when(primaryfailure)(_primary_source)).distinct,
      stepValidation = if (primaryfailure) current.goal.stepValidation.copy(
        checks = current.goal.stepValidation.checks ++ _primary_checks) else current.goal.stepValidation))
    val binding = scenario.host.goal.dependencyValidation.get
    val workspace = binding.dependency.workspace
    val target = RepositoryGitRuntimePrograms.Target(Path.of(binding.root), binding.branch, "origin", "main",
      scenario.root.resolve("unused-upstream.git").toString)
    val observations = Vector(GitOperationKind.Root, GitOperationKind.Branch, GitOperationKind.Head, GitOperationKind.Tree,
      GitOperationKind.Status, GitOperationKind.Shallow, GitOperationKind.Replacements, GitOperationKind.MergeHead,
      GitOperationKind.CommitMetadata, GitOperationKind.IndexTree, GitOperationKind.PendingPaths,
      GitOperationKind.PendingDiff, GitOperationKind.TreeChanges, GitOperationKind.BlobContent)
    val declaration = TypedGitHostPlanFixture.plan(target, kinds = observations, goalPaths = binding.paths)
    def _rebind_(value: Json): Json = value.fold(Json.Null, Json.fromBoolean, Json.fromJsonNumber,
      text => Json.fromString(if (text == TypedGitHostPlanFixture.workspace) workspace
        else if (text.startsWith("sm-workflow-git-")) "prerequisite-" + text else text),
      values => Json.fromValues(values.map(_rebind_)), fields => Json.fromJsonObject(fields.mapValues(_rebind_)))
    val primary = _array(existing, "gitPrograms").find(program =>
      program.hcursor.get[String]("target").contains(TypedGitHostPlanFixture.workspace) &&
        program.hcursor.get[String]("operation").contains(GitOperationKind.StageSelectedAll.wireName) &&
        program.hcursor.get[String]("selectionId").contains("goal-path-2")).get
    val capability = TypedGitHostPlanFixture.capability(GitOperationKind.StageSelectedAll, Some("goal-path-3"))
    val preserved = primary.mapObject(_.add("identity", capability.asJson).add("capability", capability.asJson)
      .add("selectionId", "goal-path-3".asJson).add("selectedPath", "docs/preserved.md".asJson))
    val consumer = Option.when(unknown) {
      val selected = TypedGitHostPlanFixture.capability(GitOperationKind.StageSelectedAll, Some("goal-path-4"))
      primary.mapObject(_.add("identity", selected.asJson).add("capability", selected.asJson)
        .add("selectionId", "goal-path-4".asJson).add("selectedPath", _consumer.asJson))
    }.toVector
    val product = Option.when(primaryfailure) {
      val selected = TypedGitHostPlanFixture.capability(GitOperationKind.StageSelectedAll, Some("goal-path-5"))
      primary.mapObject(_.add("identity", selected.asJson).add("capability", selected.asJson)
        .add("selectionId", "goal-path-5".asJson).add("selectedPath", _primary_source.asJson))
    }.toVector
    val git = _array(existing, "gitPrograms").filterNot(_.hcursor.get[String]("target").contains(workspace)).map { program =>
      if (primaryfailure && program.hcursor.get[String]("target").contains("sm-workflow-repository") &&
          program.hcursor.get[String]("selectionId").contains("goal-step-2"))
        program.mapObject(_.add("selectedPaths", Vector("docs/phase.md", _consumer, _primary_source).asJson))
      else program
    } ++
      _array(_rebind_(declaration), "gitPrograms") ++ Vector(preserved) ++ consumer ++ product
    val publication = _array(TypedSbtHostPlanFixture.plan(scenario.root.resolve("dependency"),
      TypedSbtHostPlanFixture.normalExecutable, Vector(4), operation = SbtOperation.PublishLocal), "sbtPrograms").map(
      _.mapObject(_.add("target", workspace.asJson).add("identity", "prerequisite-fixture-publication".asJson)
        .add("capability", "prerequisite-fixture-publication".asJson)))
    val primaryplans = if (!primaryfailure) Vector.empty else {
      current.goal.stepValidation.checks.map(_.ordinal).intersect(_primary_checks.map(_.ordinal)) shouldBe empty
      Vector(
        TypedSbtHostPlanFixture.plan(scenario.root, TypedSbtHostPlanFixture.normalExecutable,
          Vector(_primary_checks.head.ordinal), operation = SbtOperation.Compile),
        TypedSbtHostPlanFixture.plan(scenario.root, TypedSbtHostPlanFixture.normalExecutable,
          Vector(_primary_checks.last.ordinal), operation = SbtOperation.TestOnly, testClasses = Vector(_primary_suite)))
    }
    val sbt = _array(existing, "sbtPrograms") ++ publication ++ primaryplans.flatMap(_array(_, "sbtPrograms"))
    val ordinaryprocess = existing.mapObject(_.add("gitPrograms", git.asJson).add("sbtPrograms", sbt.asJson)
      .add("workspaces", (_array(existing, "workspaces") ++ primaryplans.flatMap(_array(_, "workspaces"))).asJson)
      .add("workspaceGrants", (_array(existing, "workspaceGrants") ++ primaryplans.flatMap(_array(_, "workspaceGrants"))).asJson)
      .add("grants", Json.arr(Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson,
        "capabilities" -> (git ++ sbt).map(_.hcursor.get[String]("capability").toOption.get).distinct.asJson))))
    val process = if (!unknown) ordinaryprocess else {
      val status = git.find(program => program.hcursor.get[String]("target").contains(workspace) &&
        program.hcursor.get[String]("operation").contains(GitOperationKind.Status.wireName)).get
      val aliases = (1 to 96).toVector.map(index => f"prerequisite-read-status-$index%03d")
      def _with_status_aliases_(count: Int): Json = {
        val selected = aliases.take(count)
        val reads = selected.map(alias => status.mapObject(_.add("target", alias.asJson)
          .add("identity", alias.asJson).add("capability", alias.asJson)))
        val capabilities = (git ++ sbt ++ reads).map(_.hcursor.get[String]("capability").toOption.get)
        val owner = SmWorkflowComponent.componentId.name
        val originalgrants = _array(ordinaryprocess, "grants")
        val grants = originalgrants.map { grant =>
          if (grant.hcursor.get[String]("componentId").contains(owner)) grant.mapObject(_.add("capabilities",
            (_array(grant, "capabilities") ++ capabilities.map(_.asJson)).distinct.asJson)) else grant
        } ++ Option.when(!originalgrants.exists(_.hcursor.get[String]("componentId").contains(owner)))(
          Json.obj("componentId" -> owner.asJson, "capabilities" -> capabilities.distinct.asJson))
        ordinaryprocess.mapObject(_.add("gitPrograms", (git ++ reads).asJson).add("grants", grants.asJson)
          .add("workspaces", (_array(ordinaryprocess, "workspaces") ++ selected.map(alias =>
            Json.obj("identity" -> alias.asJson, "root" -> Path.of(binding.root).toRealPath().toString.asJson))).asJson)
          .add("workspaceGrants", (_array(ordinaryprocess, "workspaceGrants") ++ selected.map(alias =>
            Json.obj("componentId" -> owner.asJson, "workspaceId" -> alias.asJson, "access" -> "read-only".asJson))).asJson))
      }
      (0 to aliases.size).iterator.map(_with_status_aliases_).find(
        _.noSpaces.getBytes(java.nio.charset.StandardCharsets.UTF_8).length >= 77521)
        .getOrElse(fail("The declared read-only Status plan must reach the observed resolved-policy scale within 96 aliases"))
    }
    val storage = parse(config.hcursor.get[String]("textus.subsystem.storage.plan").toOption.get).toOption.get
    val collection = ThreeWorkflowNormalHostFixture.collection("prerequisite_publications")
    val publications = LocalPublication.Target("fixture-dependency-publication", "org.example.RecoveryProducer", workspace,
      Some(publicationdependency), beforeChecks = true)
    val selectedstorage = storage.mapObject(_.add("records", (_array(storage, "records") :+
      Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson, "storeName" -> "workflow".asJson,
        "collection" -> collection.asJson)).asJson))
    val policy = Json.obj("storeName" -> "workflow".asJson, "collection" -> collection.asJson,
      "targets" -> Vector(publications).asJson)
    val selected = config.mapObject(_.add("textus.subsystem.process-execution.plan", process.noSpaces.asJson)
      .add(WorkflowMechanicalReceiptParameters.maxBytes.name, _receipt_limits.maxBytes.toString.asJson)
      .add("sm-workflow.runtime.definition", ThreeWorkflowRuntimeParameters.encode(host).noSpaces.asJson)
      .add("textus.subsystem.storage.plan", selectedstorage.noSpaces.asJson)
      .add(LocalPublicationRuntime.declaration.name, policy.noSpaces.asJson)
      .add("textus.assembly.descriptor", scenario.root.resolve(".textus/assembly.yaml").toString.asJson))
    val component = sys.props.get("smworkflow.spec.componentFile").fold(selected)(value =>
      selected.mapObject(_.add("textus.component.file", value.asJson)))
    Files.writeString(path, component.noSpaces)
    publications
  }

  /** This is selected primary Step-2 product code, captured before the UNKNOWN.
    * The ordinary primary Test consumes the JAR made by the real publication. */
  private def _write_consumer(scenario: F.Scenario, producer: Path): Unit = {
    val directory = Json.fromString(producer.resolve("target/scala-2.12").toString).noSpaces
    val marker = Json.fromString(scenario.root.resolve("consumer-corrected-api.txt").toString).noSpaces
    Files.writeString(Path.of(scenario.host.target.root).resolve(_consumer), s"""object ProductCheck {
      |  def main(args: Array[String]): Unit = {
      |    val root = java.nio.file.Paths.get("").toAbsolutePath.normalize
      |    println("Given the published producer and the retained primary candidate")
      |    assert(java.nio.file.Files.readString(root.resolve("value.txt")) == "expected\\n")
      |    val jars = java.nio.file.Files.list(java.nio.file.Paths.get($directory))
      |    val jar = try jars.filter(new java.util.function.Predicate[java.nio.file.Path] {
      |      def test(path: java.nio.file.Path): Boolean = {
      |        val name = path.getFileName.toString
      |        name.endsWith(".jar") && !name.endsWith("-sources.jar") && !name.endsWith("-javadoc.jar") && !name.endsWith("-tests.jar")
      |      }
      |    }).findFirst().get() finally jars.close()
      |    println("When the ordinary consumer Test calls the corrected producer API")
      |    val loader = new java.net.URLClassLoader(Array(jar.toUri.toURL), getClass.getClassLoader)
      |    try {
      |      val clazz = loader.loadClass("RecoveryMarker")
      |      assert(clazz.getMethod("correctedApi").invoke(clazz.getConstructor().newInstance()) == "qualified-successor-api")
      |      java.nio.file.Files.write(java.nio.file.Paths.get($marker), "qualified-successor-api\\n".getBytes("UTF-8"))
      |    } finally loader.close()
      |    println("Then the actual corrected API is available to the consumer")
      |  }
      |}
      |""".stripMargin)
  }

  /** Interrupt only after the actual prerequisite source capture was saved. */
  private def _interrupt_capture(scenario: F.Scenario, session: String): Unit = {
    val config = scenario.root.resolve(".textus/config.json")
    val assembly = scenario.root.resolve(".textus/assembly.yaml")
    val configbytes = Files.readAllBytes(config)
    val assemblybytes = Files.readAllBytes(assembly)
    val parameters = parse(Files.readString(config)).toOption.get.asObject.get.toVector.map { (key, value) =>
      key -> value.asString.getOrElse(value.noSpaces)
    }.toMap
    val host = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(parse(parameters("sm-workflow.runtime.definition")).toOption.get))
    val point = "goal-prerequisite-recovery-observation-recorded"
    var observed = false
    val acknowledgement: String => Consequence[Unit] = value => if (value == point) {
      observed = true
      Consequence.stateConflict("SPECIFICATION-OWNED pause after retained prerequisite capture")
    } else Consequence.unit
    val bootstrap = InstalledBootstrapSelection(Path.of(sys.props.getOrElse("smworkflow.spec.componentFile",
      fail("Installed UNKNOWN fixture requires its normally published component CAR"))))
    try _with_runtime_(scenario.root, "Goal", Some(host), hostParameters = parameters,
      nativeAcknowledgement = None, goalAcknowledgement = Some(acknowledgement), componentInstance = "default",
      installedBootstrap = Some(bootstrap)) { (owner, component, actor, _) =>
      owner.executeOperationResponseInContext(Request.of(component = component.name, service = "GoalPhaseService",
        operation = "advanceGoalPhase", properties = NativeWorkflowOperationInputFixture.advance(session)), actor)
        .isSuccess shouldBe false
      observed shouldBe true
    } finally {
      Files.write(config, configbytes)
      Files.write(assembly, assemblybytes)
    }
  }

  /** No native record is seeded. All Port operations delegate to the production
    * adapter. Only this exact sentinel interrupts the terminal receipt save,
    * after its genuinely owned CNCF writer has confirmed its exit. */
  private def _leave_unknown(scenario: F.Scenario, session: String, target: R.Target): ProcessExecutionResult =
    _native_fixture(scenario) { core =>
      val sentinel = new scala.util.control.ControlThrowable {}
      var exited = Option.empty[ProcessExecutionResult]
      for {
        snapshot <- LegacyDependencyHistoryFixture.snapshotC(core, session)
        catalog <- GoalPhaseRuntimeCatalog.loadC()
        definitions <- WorkflowInstanceRecordJsonV1.catalogC(Vector(catalog.definition))
        actual <- WorkflowInstanceRecordJsonV1.decodeC(snapshot.toMap.apply("instance"), definitions)
        direction <- actual.history.find(_.causation.value == R.causationPrefix + target.frontierRevision)
          .map(Consequence.success).getOrElse(Consequence.stateConflict("No actual prerequisite correction Direction"))
        policy = LocalPublicationRuntime.Policy("workflow", DataStore.CollectionId.EntityStore(
          org.simplemodeling.model.datatype.EntityCollectionId.parse(ThreeWorkflowNormalHostFixture.collection("prerequisite_publications")).toOption.get),
          Vector(target.publicationTarget))
        delegate = LocalPublicationRuntime._port(core, policy)
        port = new LocalPublication.Port {
          def loadC(key: String): Consequence[Option[Json]] = delegate.loadC(key)
          def createC(key: String, value: Json): Consequence[Unit] = delegate.createC(key, value)
          def sourceC(value: LocalPublication.Target): Consequence[LocalPublication.Source] = delegate.sourceC(value)
          def newIdentity(): String = delegate.newIdentity()
          def executeC(value: LocalPublication.Target): Consequence[SbtExecutionAttempt] = delegate.executeC(value).map {
            case SbtExecutionAttempt.Observed(result) if result.operation == SbtOperation.PublishLocal &&
                result.target.workspaceId.value == value.workspace && WorkflowSbtCapture.exitCode(result.processResult).contains(0) =>
              exited = Some(result.processResult)
              Files.writeString(scenario.root.resolve("owned-publication-writer-exited.json"),
                WorkflowSbtCapture.encode(result.processResult).noSpaces, StandardOpenOption.CREATE_NEW)
              throw sentinel
            case other => fail("The actual fixture publication writer did not exit successfully: " + other)
          }
        }
        input = LocalPublicationRuntime.prerequisiteInput(session,
          target.candidate.copy(sourceRevision = direction.revision.value.toString), target.publicationTarget)
        retained <- try LocalPublication.ensurePrerequisiteC(input, target.publicationTarget, target.participant, port)
          .flatMap(_ => Consequence.stateConflict[ProcessExecutionResult]("Fixture sentinel was not reached")) catch {
          case error if error eq sentinel => Consequence.success(exited.get)
        }
      } yield retained
    }

  private def _observation(scenario: F.Scenario, described: F.Result, target: U.Target,
    process: ProcessExecutionResult, consumption: Option[S.Consumption], label: String): Unit = {
    val reference = V.WorkflowArtifactReference("fixture-independent-" + label, "1")
    val source = LocalPublication.Source(target.guard.head, target.guard.indexTree,
      target.guard.dirty.map(d => LocalPublication.Change(d.path, d.index, d.worktree, d.renamedFrom)))
    val value = U.Observation(U.observationSchema, ContextReference(reference.identity, reference.revision), U.scopeOf(target),
      U.WriterLifecycle("cncf-writer-exited", ContextReference("fixture-owned-publication-invocation", "1"),
        ContextReference("fixture-owned-publication-writer-exited", "1"), "publication-writer-lifecycle-complete",
        io.circe.Printer.noSpaces.copy(sortKeys = true).print(WorkflowSbtCapture.encode(process))),
      consumption.map(c => U.Qualification(ContextReference("fixture-independent-source-qualification", "1"),
        ContextReference("fixture-source-capture", target.candidateRecordRevision), c, source)))
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
    val input = V.WorkflowEvidencePreparationInput("GoalPhase", "goal-phase-prerequisite-publication-observation",
      described.invocation.stdout.toUri.toASCIIString, _save(scenario, label + "-observation", value.asJson.noSpaces),
      _save(scenario, label + "-reference", json(reference.toRecord()).noSpaces),
      _save(scenario, label + "-execution", json(execution.toRecord()).noSpaces),
      "normal-development-host", Instant.now().minusSeconds(60).toString, "entity:smworkflow.cli.host_evidence", Some("participant"))
    val prepared = scenario.prepareHostInput("prepareEvidence", input.toRecord())
    prepared.exitCode shouldBe 0
    scenario.selectPreparedImport(prepared)
  }

  private def _source_selection(scenario: F.Scenario, original: U.Target,
    replacement: Option[D.Binding] = None, mode: String = "selected-changes",
    scopeapprovals: Vector[D.Approval] = Vector.empty): S.Proposal = {
    val configfile = scenario.root.resolve(".textus/config.json")
    val config = parse(Files.readString(configfile)).toOption.get
    val host = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(
      parse(config.hcursor.get[String]("sm-workflow.runtime.definition").toOption.get).toOption.get))
    val binding = replacement.getOrElse(original.admission.binding.copy(revision = original.admission.binding.revision + 1))
    val checks = (host.goal.stepValidation.checks ++ host.goal.phaseValidation.checks).distinct
    val consumption = S.Consumption(binding.requester.workspace, binding.root, Vector.empty,
      binding.paths.map(path => S.ExpectedMode(path, "100644")), checks.map(check => S.CheckProgram(check.identity,
        "sm-workflow-repository-validation-" + check.ordinal, check.kind.wire, "sm-workflow-repository-validation-" + check.ordinal)) ++
        binding.checks.map(check => S.CheckProgram(check.identity, binding.dependency.workspace, check.kind.wire,
          binding.dependency.workspace + "-" + check.kind.wire)))
    val revised = host.copy(goal = host.goal.copy(dependencySourceRevisions = Vector(binding),
      dependencySourceConsumptions = Vector(consumption)))
    Files.writeString(configfile, config.mapObject(_.add("sm-workflow.runtime.definition",
      ThreeWorkflowRuntimeParameters.encode(revised).noSpaces.asJson)).noSpaces)
    val approvals = (original.admission.approvals ++ original.ancestors.flatMap(_.capture.admission.approvals) ++
      original.scopeApprovals ++ scopeapprovals).foldLeft(Vector.empty[D.Approval])((saved, next) =>
        if (saved.exists(_.decision == next.decision)) saved else saved :+ next)
    S.Proposal(S.sourceSchema, ContextReference(binding.identity, binding.revision.toString), mode, Vector.empty,
      approvals.map(a => S.Coverage(a.decision, a.proposal.acceptance, binding.checks.map(_.identity))), consumption)
  }

  /** Rebind the fixture's declared logical source routes while retaining the
    * exact original programs, capabilities, limits and grants. */
  private def _source_successor_host(scenario: F.Scenario, original: D.Binding, replacement: D.Binding): Unit = {
    val file = scenario.root.resolve(".textus/config.json")
    val config = parse(Files.readString(file)).toOption.get
    val process = parse(config.hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
    val workspaces = _array(process, "workspaces")
    val aliases = workspaces.filter(_.hcursor.get[String]("root").contains(original.root))
      .map(_.hcursor.get[String]("identity").toOption.get).toSet
    aliases should contain(original.dependency.workspace)
    val reboundgit = _array(process, "gitPrograms").map { program =>
      if (program.hcursor.get[String]("target").toOption.exists(aliases.contains))
        program.mapObject(_.add("localBranch", replacement.branch.asJson))
      else program
    }
    val template = reboundgit.find(program => program.hcursor.get[String]("target").contains(replacement.dependency.workspace) &&
      program.hcursor.get[String]("operation").contains(GitOperationKind.CommitMetadata.wireName)).get
    val capability = replacement.dependency.workspace + "-source-committed-tree-entries"
    val inventory = template.mapObject(_.add("operation", GitOperationKind.TreeEntries.wireName.asJson)
      .add("identity", capability.asJson).add("capability", capability.asJson)
      .add("selectionId", S.committedInventorySelection.asJson).add("selectedPaths", replacement.paths.asJson))
    val sbt = _array(process, "sbtPrograms").map { program =>
      val environment = program.hcursor.get[Json]("environment").toOption.get
      if (environment.hcursor.get[String]("TEXTUS_CNCF_SOURCE_ROOT").contains(original.root))
        program.mapObject(_.add("environment", environment.mapObject(_.add("TEXTUS_CNCF_SOURCE_ROOT", replacement.root.asJson))))
      else program
    }
    val selected = process.mapObject(_.add("gitPrograms", (reboundgit :+ inventory).asJson).add("sbtPrograms", sbt.asJson)
      .add("workspaces", workspaces.map { workspace =>
        if (workspace.hcursor.get[String]("identity").toOption.exists(aliases.contains))
          workspace.mapObject(_.add("root", replacement.root.asJson)) else workspace
      }.asJson).add("grants", _array(process, "grants").map { grant =>
        if (grant.hcursor.get[String]("componentId").contains(SmWorkflowComponent.componentId.name))
          grant.mapObject(_.add("capabilities", (_array(grant, "capabilities") :+ capability.asJson).distinct.asJson))
        else grant
      }.asJson))
    Files.writeString(file, config.mapObject(_.add("textus.subsystem.process-execution.plan", selected.noSpaces.asJson)).noSpaces)
  }

  private def _source_stages(scenario: F.Scenario, session: String): Int = _native_fixture(scenario) { core => for {
    snapshot <- LegacyDependencyHistoryFixture.snapshotC(core, session)
    catalog <- GoalPhaseRuntimeCatalog.loadC()
    definitions <- WorkflowInstanceRecordJsonV1.catalogC(Vector(catalog.definition))
    actual <- WorkflowInstanceRecordJsonV1.decodeC(snapshot.toMap.apply("instance"), definitions)
  } yield actual.history.count(entry => entry.causation.value.startsWith(U.sourceCausationPrefix) &&
    entry.causation.value.contains(":dependency-source-stage-result-")) }

  /** Read only the exact mechanical receipt created by public recovery in this
    * disposable fixture; no history or receipt is manufactured or changed. */
  private def _unknown_direction(scenario: F.Scenario, session: String, frontier: String): (String, U.Direction) =
    _native_fixture(scenario) { callcore =>
      final class ReceiptReader extends org.goldenport.cncf.action.ProcedureActionCall {
        val core: org.goldenport.cncf.action.ActionCall.Core = callcore
        def readC(key: String): Consequence[org.goldenport.record.Record] = {
          given org.goldenport.cncf.context.ExecutionContext = executionContext
          val space = executionContext.dataStoreSpace
          val previous = space.captureBinding()
          try bind_component_application_datastore_c("workflow").flatMap { _ =>
            val collection = DataStore.CollectionId.EntityStore(
              org.simplemodeling.model.datatype.EntityCollectionId("smworkflow", "cli", "continuations"))
            space.dataStore(collection).flatMap(_.load(collection, DataStore.StringEntryId(key))).flatMap(
              _.map(Consequence.success).getOrElse(Consequence.stateConflict("The actual UNKNOWN Direction receipt is absent")))
          } finally space.restoreBinding(previous)
        }
        def execute(): Consequence[org.goldenport.protocol.operation.OperationResponse] =
          Consequence.stateConflict("Specification-only exact receipt reader is not an executable operation")
      }
      val reader = new ReceiptReader
      for {
        snapshot <- LegacyDependencyHistoryFixture.snapshotC(callcore, session)
        catalog <- GoalPhaseRuntimeCatalog.loadC()
        definitions <- WorkflowInstanceRecordJsonV1.catalogC(Vector(catalog.definition))
        current <- WorkflowInstanceRecordJsonV1.decodeC(snapshot.toMap.apply("instance"), definitions)
        reference = WorkflowMechanicalTransition.Reference(session + ":" + U.causationPrefix + frontier + ":direction", "1")
        key <- WorkflowMechanicalTransition.keyC(reference)
        row <- reader.readC(key)
        bytes <- row.getString("mechanicalPayload").filter(_.startsWith("cncf.workflow-json.v1:"))
          .map(value => Consequence.success(value.drop("cncf.workflow-json.v1:".length)))
          .getOrElse(Consequence.stateConflict("The actual UNKNOWN Direction payload is absent"))
        receipt <- WorkflowMechanicalTransitionJsonV1.decodeC(bytes, definitions, U.codec, _receipt_limits)
        actual <- GoalPhaseNativeHistory.prefixC(current, receipt.next.revision)
        actualbytes <- WorkflowInstanceRecordJsonV1.encodeC(actual)
        receiptbytes <- WorkflowInstanceRecordJsonV1.encodeC(receipt.next)
        _ <- if (row.getString("id").contains(key) && receipt.reference == reference && actualbytes == receiptbytes &&
            receipt.component == SmWorkflowComponent.componentId && receipt.operation == GoalPhaseNativeRun.advanceOperation)
          Consequence.unit else Consequence.stateConflict("The actual UNKNOWN receipt crosses its key, owner or native history")
      } yield bytes -> receipt.payload.value
    }

  /** Arrange tiny source-project consumption before Start, with a real Git
    * baseline. Later candidate capture and closure retain their ordinary owners. */
  private def _prepare_primary_project(scenario: F.Scenario, publicationsentinel: Boolean = false): Unit = {
    val repository = Path.of(scenario.host.target.root)
    val attempts = Json.fromString(scenario.root.resolve("primary-publication-attempts.txt").toString).noSpaces
    val invocations = Json.fromString(scenario.root.resolve("primary-all-sbt-invocations.txt").toString).noSpaces
    val sentinel = Json.fromString(repository.resolve(_primary_sentinel).toString).noSpaces
    val sentinelhook = if (!publicationsentinel) "" else
      s"if (!file($sentinel).exists) IO.write(file($sentinel), \"SPECIFICATION-OWNED unselected sentinel\\n\")"
    Files.writeString(repository.resolve("build.sbt"), s"""
      |name := "primary-recovery-fixture"
      |organization := "org.example.smworkflow.fixture"
      |version := "0.0.0-${scenario.root.getFileName}-SNAPSHOT"
      |libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % Test
      |lazy val sourceDependencies = sys.env.get("TEXTUS_CNCF_SOURCE_ROOT").toSeq.map(path =>
      |  ClasspathDependency(RootProject(file(path)), None))
      |lazy val root = project.in(file(".")).dependsOn(sourceDependencies: _*)
      |Global / onLoad := {
      |  val previous = (Global / onLoad).value
      |  state => {
      |    val commands = state.remainingCommands.map(_.commandLine).mkString("|")
      |    IO.append(file($invocations), commands + "\\n")
      |    if (commands.contains("publishLocal")) {
      |      IO.append(file($attempts), commands + "\\n")
      |      $sentinelhook
      |    }
      |    previous(state)
      |  }
      |}
      |""".stripMargin, StandardOpenOption.APPEND)
    take(_run_setup_command_(Vector("-C", repository.toString, "-c", "core.hooksPath=/dev/null",
      "add", "--", "build.sbt")))
    take(_run_setup_command_(Vector("-C", repository.toString, "-c", "core.hooksPath=/dev/null",
      "-c", "user.name=Primary Recovery Specification", "-c", "user.email=primary@example.invalid",
      "commit", "--no-verify", "--no-gpg-sign", "-m", "Specification-owned primary source consumption")))
    val file = scenario.root.resolve(".textus/config.json")
    val config = parse(Files.readString(file)).toOption.get
    Files.writeString(file, config.mapObject(_.add("textus.resource.read.max-bytes", "16777216".asJson)).noSpaces)
  }

  /** Both targets and the exact fixed producer environment are declared before
    * the Step-2 candidate and its first genuine primary failure exist. */
  private def _enable_primary_publication(scenario: F.Scenario, dependency: LocalPublication.Target,
    component: String = "org.example.PrimaryProduct"): Unit = {
    val file = scenario.root.resolve(".textus/config.json")
    val config = parse(Files.readString(file)).toOption.get
    val process = parse(config.hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
    val binding = scenario.host.goal.dependencyValidation.get
    val workspace = binding.requester.workspace
    val publication = _array(TypedSbtHostPlanFixture.plan(scenario.root,
      TypedSbtHostPlanFixture.normalExecutable, Vector(5), operation = SbtOperation.PublishLocal), "sbtPrograms").map(
      _.mapObject(_.add("target", workspace.asJson).add("identity", "fixture-primary-publication".asJson)
        .add("capability", "fixture-primary-publication".asJson)))
    val sbt = (_array(process, "sbtPrograms") ++ publication).map(_.mapObject(fields =>
      fields.add("environment", fields("environment").get.mapObject(_.add("TEXTUS_CNCF_SOURCE_ROOT", binding.root.asJson)))))
    val capabilities = (_array(process, "gitPrograms") ++ sbt).map(_.hcursor.get[String]("capability").toOption.get).distinct
    val selected = process.mapObject(_.add("sbtPrograms", sbt.asJson).add("grants", Json.arr(Json.obj(
      "componentId" -> SmWorkflowComponent.componentId.name.asJson, "capabilities" -> capabilities.asJson))))
    val policy = parse(config.hcursor.get[String](LocalPublicationRuntime.declaration.name).toOption.get).toOption.get
      .mapObject(_.add("targets", Vector(dependency, LocalPublication.Target("fixture-primary-publication",
        component, workspace, None, beforeChecks = true)).asJson))
    Files.writeString(file, config.mapObject(_.add("textus.subsystem.process-execution.plan", selected.noSpaces.asJson)
      .add(LocalPublicationRuntime.declaration.name, policy.noSpaces.asJson)).noSpaces)
  }

  private def _write_primary_product(scenario: F.Scenario, repairprimary: Boolean = false,
    requiredocument: Boolean = false): Unit = {
    val repository = Path.of(scenario.host.target.root)
    Files.createDirectories(repository.resolve(_primary_source).getParent)
    Files.writeString(repository.resolve(_primary_source),
      "package fixture\nobject PrimaryProduct { def value: String = new RecoveryMarker().correctedApi" +
        (if (repairprimary) "; def primaryRepairMarker: String = missingPrimaryApi" else "") + " }\n", StandardOpenOption.CREATE_NEW)
    val marker = Json.fromString(scenario.root.resolve("primary-real-check-passed.txt").toString).noSpaces
    val documentcheck = if (requiredocument)
      "withClue(\"Required selected dependency integration document is missing: docs/additional-dependency-01.md: \") { " +
        "java.nio.file.Files.exists(java.nio.file.Paths.get(sys.env(\"TEXTUS_CNCF_SOURCE_ROOT\"), " +
        "\"docs/additional-dependency-01.md\")) shouldBe true }" else ""
    Files.writeString(repository.resolve(_consumer), s"""package fixture
      |
      |import org.scalatest.GivenWhenThen
      |import org.scalatest.matchers.should.Matchers
      |import org.scalatest.wordspec.AnyWordSpec
      |
      |final class PrimaryProductCheckSpec extends AnyWordSpec with Matchers with GivenWhenThen {
      |  "E1 selected primary source consumption" must afterWord(
      |    "in spec:primary-source-consumption, example:E1, rules:exact-input,corrected-api, phase:6.10") {
      |    "call the real corrected dependency through the primary API" in {
      |      Given("the selected primary and its real corrected source ProjectRef")
      |      java.nio.file.Files.readString(java.nio.file.Paths.get("value.txt")) shouldBe "expected\\n"
      |      When("the focused TestOnly calls the compiled primary API")
      |      val actual = PrimaryProduct.value
      |      Then("the real selected dependency API returns the required value")
      |      actual shouldBe "qualified-primary-api"
      |      $documentcheck
      |      java.nio.file.Files.write(java.nio.file.Paths.get($marker), "qualified-primary-api\\n".getBytes("UTF-8"))
      |    }
      |  }
      |}
      |""".stripMargin)
  }

  /** Compare the complete persisted native prefix through the normal codec.
    * DefinitionBinding object identity is not native-history evidence. */
  private def _history_extension(before: String, after: String): Unit = {
    val prefix = _array(parse(before).toOption.get, "history")
    val current = _array(parse(after).toOption.get, "history")
    current.take(prefix.size) shouldBe prefix
    current.size should be > prefix.size
  }

  /** Decode actual issued work from the existing read-only managed snapshot. */
  private def _issued_request(scenario: F.Scenario, session: String): GoalPhaseNativeWork.Request =
    _issued_requests(scenario, session).maxBy(_.sourceRevision.toLong)

  private def _issued_requests(scenario: F.Scenario, session: String): Vector[GoalPhaseNativeWork.Request] = {
    def _strings_(value: Json): Vector[String] = value.fold(Vector.empty, _ => Vector.empty, _ => Vector.empty,
      text => Vector(text), values => values.flatMap(_strings_).toVector,
      fields => fields.values.toVector.flatMap(_strings_))
    val snapshot = _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, session))
    val requests = snapshot.flatMap { case (_, bytes) =>
      parse(bytes).toOption.toVector.flatMap(_strings_).filter(_.startsWith("cncf.workflow-json.v1:"))
        .flatMap(raw => org.goldenport.cncf.workflow.WorkflowWorkOrderJsonV1.decodeC(
          raw.stripPrefix("cncf.workflow-json.v1:"), GoalPhaseNativeWork.inputCodec).toOption)
        .map(_.current.asInstanceOf[org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowContinuation.WorkOrder[GoalPhaseNativeWork.Request]]
          .request.input.get.value)
    }.filter(_.instanceIdentity == session)
    requests should not be empty
    requests
  }

  /** Read the actual immutable primary repair receipt through its native key. */
  private def _primary_repair_fact(scenario: F.Scenario, session: String): P.Fact =
    _native_fixture(scenario) { callcore =>
      final class RepairReceiptReader extends org.goldenport.cncf.action.ProcedureActionCall {
        val core: org.goldenport.cncf.action.ActionCall.Core = callcore
        def readC(key: String): Consequence[org.goldenport.record.Record] = {
          given org.goldenport.cncf.context.ExecutionContext = executionContext
          val space = executionContext.dataStoreSpace
          val previous = space.captureBinding()
          try bind_component_application_datastore_c("workflow").flatMap { _ =>
            val collection = DataStore.CollectionId.EntityStore(
              org.simplemodeling.model.datatype.EntityCollectionId("smworkflow", "cli", "continuations"))
            space.dataStore(collection).flatMap(_.load(collection, DataStore.StringEntryId(key))).flatMap(
              _.map(Consequence.success).getOrElse(Consequence.stateConflict("The actual primary repair receipt is absent")))
          } finally space.restoreBinding(previous)
        }
        def execute(): Consequence[org.goldenport.protocol.operation.OperationResponse] =
          Consequence.stateConflict("Specification-only primary repair receipt reader is not an executable operation")
      }
      val reader = new RepairReceiptReader
      for {
        snapshot <- LegacyDependencyHistoryFixture.snapshotC(callcore, session)
        catalog <- GoalPhaseRuntimeCatalog.loadC()
        definitions <- WorkflowInstanceRecordJsonV1.catalogC(Vector(catalog.definition))
        current <- WorkflowInstanceRecordJsonV1.decodeC(snapshot.toMap.apply("instance"), definitions)
        entry <- current.history.findLast(_.causation.value.startsWith(P.causationPrefix))
          .map(Consequence.success).getOrElse(Consequence.stateConflict("The actual primary repair history is absent"))
        reference = WorkflowMechanicalTransition.Reference(session + ":" + entry.causation.value, "1")
        key <- WorkflowMechanicalTransition.keyC(reference)
        row <- reader.readC(key)
        bytes <- row.getString("mechanicalPayload").filter(_.startsWith("cncf.workflow-json.v1:"))
          .map(value => Consequence.success(value.stripPrefix("cncf.workflow-json.v1:")))
          .getOrElse(Consequence.stateConflict("The actual primary repair payload is absent"))
        receipt <- WorkflowMechanicalTransitionJsonV1.decodeC(bytes, definitions, P.codec, _receipt_limits)
        actual <- GoalPhaseNativeHistory.prefixC(current, receipt.next.revision)
        actualbytes <- WorkflowInstanceRecordJsonV1.encodeC(actual)
        receiptbytes <- WorkflowInstanceRecordJsonV1.encodeC(receipt.next)
        _ <- if (row.getString("id").contains(key) && receipt.reference == reference && actualbytes == receiptbytes &&
            receipt.next.revision == entry.revision && receipt.component == SmWorkflowComponent.componentId &&
            receipt.operation == GoalPhaseNativeRun.advanceOperation && receipt.payload.value.failure.sessionId == session)
          Consequence.unit else Consequence.stateConflict("Primary repair receipt crosses its actual native prefix or owner")
      } yield receipt.payload.value
    }

  /** E11 retains an actual failed TestOnly before any Step-2 independent review.
    * Human direction is independently provisioned SPECIFICATION-OWNED evidence;
    * current-source qualification follows the recollected ordinary Repair. */
  private def _adopt_failed_check_source(scenario: F.Scenario, executable: Path, task: Path,
    repairoutput: F.Result, capture: S.Capture, origin: U.Target): Unit = {
    Given("E11 the real selected primary TestOnly failed because an integration document is absent, before Step-2 Review")
    val repair = repairoutput.business[V.GoalPhaseAdvanceResult]
    val session = repair.sessionId
    def _artifact_direction_(): A.Saved = _native_fixture(scenario) { callcore =>
      final class ArtifactReceiptReader extends org.goldenport.cncf.action.ProcedureActionCall {
        val core: org.goldenport.cncf.action.ActionCall.Core = callcore
        def readC(key: String): Consequence[org.goldenport.record.Record] = {
          given org.goldenport.cncf.context.ExecutionContext = executionContext
          val space = executionContext.dataStoreSpace
          val previous = space.captureBinding()
          try bind_component_application_datastore_c("workflow").flatMap { _ =>
            val collection = DataStore.CollectionId.EntityStore(
              org.simplemodeling.model.datatype.EntityCollectionId("smworkflow", "cli", "continuations"))
            space.dataStore(collection).flatMap(_.load(collection, DataStore.StringEntryId(key))).flatMap(
              _.map(Consequence.success).getOrElse(Consequence.stateConflict("E11 actual current artifact receipt is absent")))
          } finally space.restoreBinding(previous)
        }
        def execute(): Consequence[org.goldenport.protocol.operation.OperationResponse] =
          Consequence.stateConflict("Specification-only current artifact reader is not an executable operation")
      }
      val reader = new ArtifactReceiptReader
      for {
        snapshot <- LegacyDependencyHistoryFixture.snapshotC(callcore, session)
        catalog <- GoalPhaseRuntimeCatalog.loadC()
        definitions <- WorkflowInstanceRecordJsonV1.catalogC(Vector(catalog.definition))
        current <- WorkflowInstanceRecordJsonV1.decodeC(snapshot.toMap.apply("instance"), definitions)
        entry <- current.history.findLast(_.causation.value.startsWith(A.causationPrefix))
          .map(Consequence.success).getOrElse(Consequence.stateConflict("E11 current artifact native history is absent"))
        reference = WorkflowMechanicalTransition.Reference(session + ":" + entry.causation.value, "1")
        key <- WorkflowMechanicalTransition.keyC(reference)
        row <- reader.readC(key)
        bytes <- row.getString("mechanicalPayload").filter(_.startsWith("cncf.workflow-json.v1:"))
          .map(value => Consequence.success(value.stripPrefix("cncf.workflow-json.v1:")))
          .getOrElse(Consequence.stateConflict("E11 current artifact mechanical payload is absent"))
        receipt <- WorkflowMechanicalTransitionJsonV1.decodeC(bytes, definitions, A.codec, _receipt_limits)
        actual <- GoalPhaseNativeHistory.prefixC(current, receipt.next.revision)
        actualbytes <- WorkflowInstanceRecordJsonV1.encodeC(actual)
        receiptbytes <- WorkflowInstanceRecordJsonV1.encodeC(receipt.next)
        _ <- if (row.getString("id").contains(key) && receipt.reference == reference && actualbytes == receiptbytes &&
            receipt.next.revision == entry.revision && receipt.component == SmWorkflowComponent.componentId &&
            receipt.operation == GoalPhaseNativeRun.advanceOperation && receipt.payload.value.target.sessionId == session)
          Consequence.unit else Consequence.stateConflict("E11 artifact direction crosses its actual native prefix or owner")
      } yield A.Saved(receipt.next, receipt.payload.value)
    }
    def _candidate_(): GoalPhaseNativeCandidate.Payload = _native_fixture(scenario) { core => for {
      snapshot <- LegacyDependencyHistoryFixture.snapshotC(core, session)
      catalog <- GoalPhaseRuntimeCatalog.loadC()
      definitions <- WorkflowInstanceRecordJsonV1.catalogC(Vector(catalog.definition))
      current <- WorkflowInstanceRecordJsonV1.decodeC(snapshot.toMap.apply("instance"), definitions)
      candidates = snapshot.flatMap { case (_, bytes) =>
        parse(bytes).toOption.flatMap(_.hcursor.get[String]("mechanicalPayload").toOption)
          .filter(_.startsWith("cncf.workflow-json.v1:")).flatMap(raw =>
            WorkflowMechanicalTransitionJsonV1.decodeC(raw.stripPrefix("cncf.workflow-json.v1:"), definitions,
              GoalPhaseNativeCandidate.codec, _receipt_limits).toOption).filter(receipt =>
            receipt.component == SmWorkflowComponent.componentId && receipt.operation == GoalPhaseNativeRun.advanceOperation &&
              receipt.next.identity == current.identity && receipt.next.causation.value == "ordered-candidate:candidate" &&
              current.history.take(receipt.next.history.size) == receipt.next.history)
      }
      selected <- candidates.sortBy(_.next.revision.value).lastOption.map(Consequence.success)
        .getOrElse(Consequence.stateConflict("E11 actual completed candidate receipt is absent"))
    } yield selected.payload.value }
    val primaryfact = _primary_repair_fact(scenario, session)
    val primarybytes = primaryfact.asJson.noSpaces
    val previouscandidate = _candidate_()
    previouscandidate.primaryPublicationRepair should not be empty
    val request = _issued_request(scenario, session)
    val failure = request.checkFailure.get
    val binding = capture.direction.target.binding
    val repository = Path.of(binding.root)
    val additions = Vector.tabulate(31)(index => f"docs/additional-dependency-${index + 1}%02d.md")
    val failurebytes = failure.asJson.noSpaces
    val capturebytes = capture.asJson.noSpaces
    val originalfacts = _primary_facts(scenario, session)
    val originalhistory = _history(scenario, session)
    val originalsnapshot = _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, session)).filter { case (_, bytes) =>
      parse(bytes).toOption.exists(_.hcursor.get[String]("mechanicalPayload").isRight)
    }
    val sourcecount = _source_stages(scenario, session)
    val recoverycount = _stages(scenario, session)
    val logpaths = Vector("primary-all-sbt-invocations.txt", "primary-publication-attempts.txt",
      "dependency/all-sbt-invocations.txt", "dependency/publication-attempts.txt",
      "dependency-successor/all-sbt-invocations.txt", "dependency-successor/publication-attempts.txt")
    val logs = logpaths.map(path => path -> _lines(scenario.root.resolve(path)))
    def _no_review_(): Unit = _issued_requests(scenario, session).exists(input =>
      input.stepIdentity.contains(binding.step) && input.kind == "ReviewStep") shouldBe false
    def _preserved_(): Unit = {
      val retained = _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, session)).toMap
      originalsnapshot.foreach { case (key, bytes) => retained.get(key) shouldBe Some(bytes) }
      _primary_facts(scenario, session) shouldBe originalfacts
      _primary_repair_fact(scenario, session).asJson.noSpaces shouldBe primarybytes
      _source_stages(scenario, session) shouldBe sourcecount
      _stages(scenario, session) shouldBe recoverycount
      logs.foreach { case (path, lines) => _lines(scenario.root.resolve(path)) shouldBe lines }
      _primary_facts(scenario, session).exists(fact =>
        fact.hcursor.downField("target").get[S.Capture]("sourceCapture").contains(capture)) shouldBe true
      capture.asJson.noSpaces shouldBe capturebytes
      failure.asJson.noSpaces shouldBe failurebytes
      Files.exists(scenario.root.resolve("primary-real-check-passed.txt")) shouldBe false
      _no_review_()
    }
    repair.work.get.kind shouldBe "RepairStep"
    request.review shouldBe None
    request.publicationFailure shouldBe None
    request.candidate shouldBe None
    request.checksReceipt shouldBe None
    GoalPhaseNativeWork.admitCheckFailureContextC(failure).isSuccess shouldBe true
    failure.validation.dependency shouldBe None
    failure.validation.dependencySource shouldBe Some(capture)
    capture.schemaVersion shouldBe S.primaryCaptureSchema
    failure.validation.outcome shouldBe Some("repair-required")
    val failedcheck = failure.validation.receipts.last
    failedcheck.identity shouldBe "primary-focused"
    failedcheck.exitCode shouldBe Some(1)
    failedcheck.sbt.get.kind shouldBe WorkflowSbtValidation.Kind.TestOnly
    failedcheck.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Failed
    failedcheck.processObservation should not be empty
    failedcheck.stdout should include("Required selected dependency integration document is missing: docs/additional-dependency-01.md")
    val testissues = request.issues.get.issues.filter(_.source == "TEST")
    testissues should not be empty
    testissues.exists(issue => issue.step == binding.step && issue.status != "RESOLVED") shouldBe true
    request.issues.get.issues.exists(_.source == "REVIEW") shouldBe false
    _no_review_()
    additions.foreach(path => Files.exists(repository.resolve(path)) shouldBe false)

    val scopeproposal = capture.direction.target.predecessor.approvals.head.proposal.copy(
      identity = "failed-check-additional-scope", discovery = "REPAIR", paths = additions,
      reason = "SPECIFICATION-OWNED required tiny dependency integration documents",
      acceptance = Vector("Qualify the additional documents through the same dependency checks"))
    val pending = repair.work.get.issues.toVector.flatMap(_.issues).filter(_.status != "RESOLVED").map(issue =>
      V.GoalPhaseIssueDisposition(issue.identity, "BLOCKED", "SPECIFICATION-OWNED additional dependency scope is required", None))
    When("the actual failed-check Repair requests the 31 missing documents through the ordinary scope Human")
    _submit(scenario, repair, Json.obj("outcome" -> "decision-needed".asJson,
      "summary" -> "SPECIFICATION-OWNED missing dependency document scope".asJson, "findings" -> Json.arr(),
      "dependencyProposal" -> scopeproposal.asJson, "issueReport" -> json(V.GoalPhaseIssueReport(pending).toRecord())),
      "failed-check-scope-request")
    val questionoutput = scenario.advance(task)
    val question = questionoutput.business[V.GoalPhaseAdvanceResult]
    Then("the scope decision is genuinely pending and no independent Review has been issued")
    question.kind shouldBe "Human"
    _no_review_()
    val answer = V.GoalPhaseOrderedDecisionAnswer("ApproveDependencyScope", "SPECIFICATION-OWNED required dependency documents")
    val reference = V.WorkflowArtifactReference("failed-check-additional-approval", "1")
    val execution = _provision(scenario, questionoutput, "human", answer, reference, "failed-check-scope")
    When("independent fixture evidence and public Submit approve that exact scope")
    scenario.call("command", question.submitOperation.get, Vector("--sessionId", question.submitSessionId.get,
      "--answer", json(answer.toRecord()).noSpaces, "--resultReference", json(reference.toRecord()).noSpaces,
      "--executionEvidence", json(execution.toRecord()).noSpaces))
      .business[V.GoalPhaseOrderedDecisionSubmissionResult].accepted shouldBe true
    val resumed = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
    val resumedrequest = _issued_request(scenario, session)
    Then("ordinary Repair retains the exact failed checks and issue identities after scope approval")
    resumed.work.get.kind shouldBe "RepairStep"
    resumedrequest.checkFailure shouldBe Some(failure)
    resumedrequest.review shouldBe None
    resumedrequest.repairProgress shouldBe request.repairProgress
    resumedrequest.issues.get.issues.map(_.identity) shouldBe request.issues.get.issues.map(_.identity)
    val blocked = resumed.work.get.issues.toVector.flatMap(_.issues).filter(_.status != "RESOLVED").map(issue =>
      V.GoalPhaseIssueDisposition(issue.identity, "BLOCKED", "SPECIFICATION-OWNED committed source integration requires direction", None))
    When("that actual Repair requests source integration and normal Advance reaches an unanswered Human")
    _submit(scenario, resumed, Json.obj("outcome" -> "decision-needed".asJson,
      "summary" -> "SPECIFICATION-OWNED committed dependency source integration requires direction".asJson,
      "findings" -> Json.arr(), "issueReport" -> json(V.GoalPhaseIssueReport(blocked).toRecord())),
      "failed-check-source-request")
    val human = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
    Then("the source successor starts from a real pending Human and preserves prior failed facts")
    human.kind shouldBe "Human"
    val pendingrequest = _issued_request(scenario, session)
    _preserved_()

    Given("a clean same-root descendant that adds the approved 31 documents and one unrelated baseline")
    val beforeguard = _native_fixture(scenario)(S.observeC(_, binding, Vector.empty))
    beforeguard.head shouldBe binding.base.head
    beforeguard.dirty shouldBe empty
    val originalfiles = binding.paths.map(path => path -> Files.readAllBytes(repository.resolve(path)).toVector)
    additions.foreach { path =>
      Files.createDirectories(repository.resolve(path).getParent)
      Files.writeString(repository.resolve(path), "SPECIFICATION-OWNED dependency integration document " + path + "\n",
        StandardOpenOption.CREATE_NEW)
    }
    val baseline = "background-baseline.txt"
    Files.writeString(repository.resolve(baseline), "SPECIFICATION-OWNED committed background baseline\n", StandardOpenOption.CREATE_NEW)
    val paths = binding.paths ++ additions
    binding.paths.size shouldBe 3
    paths.size shouldBe 34
    def _setup_(arguments: Vector[String]): Unit = take(_run_setup_command_(
      Vector("--no-optional-locks", "--no-replace-objects", "-c", "core.fsmonitor=false", "-c", "gc.auto=0",
        "-C", repository.toString) ++ arguments))
    When("fixture setup commits only that same-root descendant while keeping the old three selected files exact")
    _setup_(Vector("add", "--") ++ paths :+ baseline)
    _setup_(Vector("-c", "user.name=Failed Check Source Specification", "-c", "user.email=failed-check@example.invalid",
      "commit", "--no-verify", "--no-gpg-sign", "--only", "-m", "Specification-owned failed-check source integration", "--") ++ paths :+ baseline)
    _setup_(Vector("merge-base", "--is-ancestor", binding.base.head, "HEAD^"))
    val committed = _native_fixture(scenario)(G.observeInWorkspaceC(_, binding.dependency.workspace))
    Then("the committed source preserves its original branch and all old selected bytes")
    committed.branch shouldBe binding.branch
    committed.head should not be binding.base.head
    committed.dirty shouldBe empty
    originalfiles.foreach { case (path, bytes) => Files.readAllBytes(repository.resolve(path)).toVector shouldBe bytes }
    val replacement = binding.copy(revision = binding.revision + 1, paths = paths,
      base = GoalPhaseOrderedProgression.Snapshot(committed.head, committed.tree))
    val approval = D.Approval(binding.step, scopeproposal, ContextReference(reference.identity, reference.revision))
    val proposal = _source_selection(scenario, origin, Some(replacement), "committed", Vector(approval))
    proposal.coverage shouldBe capture.direction.target.proposal.coverage :+
      S.Coverage(approval.decision, scopeproposal.acceptance, replacement.checks.map(_.identity))
    val configfile = scenario.root.resolve(".textus/config.json")
    val config = parse(Files.readString(configfile)).toOption.get
    val process = parse(config.hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
    val programs = _array(process, "gitPrograms")
    def _inventory_(program: Json): Boolean =
      program.hcursor.get[String]("target").contains(binding.dependency.workspace) &&
        program.hcursor.get[String]("operation").contains(GitOperationKind.TreeEntries.wireName) &&
        program.hcursor.get[String]("selectionId").contains(S.committedInventorySelection)
    programs.count(_inventory_) shouldBe 1
    programs.find(_inventory_).get.hcursor.get[Vector[String]]("selectedPaths") shouldBe Right(binding.paths)
    val revised = process.mapObject(_.add("gitPrograms", programs.map { program =>
      if (_inventory_(program)) program.mapObject(_.add("selectedPaths", paths.asJson)) else program
    }.asJson))
    When("the existing finite TreeEntries route selects exactly 34 approved paths and the installed CLI describes them")
    Files.writeString(configfile, config.mapObject(_.add("textus.subsystem.process-execution.plan", revised.noSpaces.asJson)).noSpaces)
    val describedoutput = scenario.callInstalled(executable, "GoalPhaseService", S.describeOperation,
      Vector("--inputResource", _save(scenario, "failed-check-source-description", json(
        V.GoalPhaseDependencySourceRevisionDescriptionInput(session, GoalPhaseNativeMetadata.record(proposal.asJson)).toRecord()).noSpaces)),
      assemblyDescriptor = Some(scenario.root.resolve(".textus/assembly.yaml")))
    describedoutput.exitCode shouldBe 0
    val description = describedoutput.business[V.GoalPhaseDependencySourceRevisionDescriptionResult]
    val target = take(S.targetC(description.target))
    Then("the source-only successor covers the actual scope approval and partitions the unrelated baseline")
    target.binding shouldBe replacement
    target.plan shouldBe request.selectedPlan
    target.primaryBase shouldBe request.originalBase
    target.completed shouldBe request.completed
    target.repairProgress shouldBe request.repairProgress.get
    target.sourceSuccession.get.parent shouldBe capture.direction.directive
    target.sourceSuccession.get.previousDirection shouldBe capture.direction.directive
    target.sourceSuccession.get.approvals shouldBe Vector(approval)
    target.committedBaseSuccession.get.phaseChangedPaths shouldBe additions
    target.committedBaseSuccession.get.baselineChangedPaths shouldBe Vector(baseline)
    target.committedBaseSuccession.get.committedEntries.map(_.path) shouldBe paths
    _preserved_()
    val adoptionreference = V.WorkflowArtifactReference("failed-check-independent-source-human", "1")
    val adoption = V.GoalPhaseDependencySourceRevisionAdoptionInput(session, description.target,
      "AdoptDependencySourceRevision", "SPECIFICATION-OWNED independent Human selects only this committed source for fresh qualification",
      adoptionreference)
    _provision(scenario, describedoutput, "dependency-source-revision", adoption,
      adoptionreference, "failed-check-source-human")
    When("the independently provisioned Human adopts the source and separate ordinary Advance issues Repair")
    scenario.callInstalled(executable, "GoalPhaseService", S.adoptionOperation,
      Vector("--inputResource", _save(scenario, "failed-check-source-adoption", json(adoption.toRecord()).noSpaces)),
      assemblyDescriptor = Some(scenario.root.resolve(".textus/assembly.yaml")))
      .business[V.GoalPhaseDependencySourceRevisionAdoptionResult].accepted shouldBe true
    val nextoutput = scenario.advance(task)
    val next = nextoutput.business[V.GoalPhaseAdvanceResult]
    val nextrequest = _issued_request(scenario, session)
    Then("genuine Repair retains the exact failed-check context and TEST identity without validating the new source or inventing review")
    next.work.get.kind shouldBe "RepairStep"
    next.sessionId shouldBe session
    nextrequest.checkFailure shouldBe Some(failure)
    nextrequest.checkFailure.get.asJson.noSpaces shouldBe failurebytes
    nextrequest.review shouldBe None
    nextrequest.publicationFailure shouldBe None
    nextrequest.candidate shouldBe None
    nextrequest.checksReceipt shouldBe None
    nextrequest.selectedPlan shouldBe request.selectedPlan
    nextrequest.originalBase shouldBe request.originalBase
    nextrequest.completed shouldBe request.completed
    nextrequest.repairProgress shouldBe request.repairProgress
    nextrequest.issues.get.issues shouldBe pendingrequest.issues.get.issues
    nextrequest.issues.get.history.take(pendingrequest.issues.get.history.size) shouldBe pendingrequest.issues.get.history
    nextrequest.issues.get.issues.filter(_.source == "TEST").map(_.identity) shouldBe testissues.map(_.identity)
    next.validation shouldBe None
    _history_extension(originalhistory, _history(scenario, session))
    _preserved_()
    val finalhistory = _history(scenario, session)
    val finalsnapshot = _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, session))
    val finalrequests = _issued_requests(scenario, session)
    When("ordinary Advance recollects that same pending Repair once")
    val recollected = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
    Then("there is no duplicate semantic issuance, issue, process or publication and the new source is still unvalidated")
    recollected shouldBe next
    _issued_request(scenario, session) shouldBe nextrequest
    _issued_requests(scenario, session) shouldBe finalrequests
    _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, session)) shouldBe finalsnapshot
    _history(scenario, session) shouldBe finalhistory
    _preserved_()

    Given("the actual issued Repair and its TEST issues with the approved committed dependency left exact and clean")
    val currentfiles = paths.map(path => path -> Files.readAllBytes(repository.resolve(path)).toVector)
    val primarysource = Path.of(scenario.host.target.root).resolve(_primary_source)
    val primarybefore = Files.readString(primarysource)
    primarybefore should include("\"qualified-primary-api\"")
    val dispositions = next.work.get.issues.toVector.flatMap(_.issues).filter(_.status != "RESOLVED").map(issue =>
      V.GoalPhaseIssueDisposition(issue.identity, "FIXED",
        "SPECIFICATION-OWNED selected committed integration document is present; fresh runtime checks remain required", None))
    When("the worker makes the owned primary correction and submits truthful completion through normal Submit")
    Files.writeString(primarysource, primarybefore + "// The selected committed dependency includes the required integration document.\n")
    _submit(scenario, next, Json.obj("outcome" -> "completed".asJson,
      "summary" -> "SPECIFICATION-OWNED primary consumes the formally adopted committed integration source; runtime qualification remains required".asJson,
      "findings" -> Json.arr(), "issueReport" -> json(V.GoalPhaseIssueReport(dispositions).toRecord())),
      "failed-check-current-source-completion")
    val callsbefore = _lines(scenario.root.resolve("primary-all-sbt-invocations.txt"))
    val publicationsbefore = _lines(scenario.root.resolve("primary-publication-attempts.txt"))
    When("ordinary installed Advance captures the actual current source before requesting its separate artifact decision")
    val candidateoutput = scenario.advance(task)
    val candidate = _candidate_()
    val currentcapture = candidate.dependencySourceCapture.get
    Then("the v4 candidate retains primary failure provenance while recording a new current-generation 34-path source observation")
    candidate.primaryPublicationRepair shouldBe previouscandidate.primaryPublicationRepair
    candidate.sourceRevision should not be previouscandidate.sourceRevision
    candidate.dependencySourceDirection shouldBe Some(capture.direction)
    candidate.dependencyAdmission shouldBe None
    candidate.dependency shouldBe None
    candidate.dependencyOrigin shouldBe None
    candidate.dependencyStaged shouldBe empty
    candidate.dependencySourceStages shouldBe empty
    candidate.dependencySourceGuard shouldBe Some(currentcapture.preStageGuard)
    currentcapture.sourceRevision shouldBe candidate.sourceRevision
    currentcapture.generation shouldBe nextrequest.generation
    currentcapture.scope.get.binding shouldBe replacement
    currentcapture.scope.get.consumptionDirection.get.target shouldBe target
    S.bindingOf(currentcapture).paths shouldBe paths
    S.modeOf(currentcapture) shouldBe "committed"
    currentcapture should not be capture
    P.currentSourceSuccessorValid(primaryfact.failure.candidate, candidate) shouldBe true
    GoalPhaseNativeCandidate.codec.decode(GoalPhaseNativeCandidate.codec.encode(candidate)) shouldBe Right(candidate)
    _primary_repair_fact(scenario, session).asJson.noSpaces shouldBe primarybytes

    val advanced = candidateoutput.business[V.GoalPhaseAdvanceResult]
    Then("the honest current-source artifact Wait starts no SBT or publication and issues no Review")
    advanced.work shouldBe None
    advanced.validation shouldBe None
    advanced.message should include("fresh independent Human artifact decision")
    _lines(scenario.root.resolve("primary-all-sbt-invocations.txt")) shouldBe callsbefore
    _lines(scenario.root.resolve("primary-publication-attempts.txt")) shouldBe publicationsbefore
    _no_review_()
    When("the installed public describe operation reports the exact current-source artifact target")
    val artifactoutput = scenario.call("command", R.describeOperation, Vector("--sessionId", session))
    val artifactdescription = artifactoutput.business[V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult]
    val artifacttarget = take(A.targetC(artifactdescription.target))
    Then("the two choices bind the current candidate and capture together with the unchanged original failure and UNKNOWN")
    artifactdescription.choices shouldBe Vector("AcceptExistingArtifactsWithUnknownEffects", "Defer")
    artifacttarget.publicationTarget.candidate shouldBe candidate
    artifacttarget.publicationTarget.failure shouldBe primaryfact.failure
    artifacttarget.previous shouldBe primaryfact.failure.artifactAdoption.get
    artifacttarget.previous.previous shouldBe origin.unknown
    artifacttarget.guard shouldBe currentcapture.postStageGuard
    artifacttarget.source shouldBe A.sourceOf(currentcapture.postStageGuard)
    val artifacthistory = _history(scenario, session)
    val artifactsnapshot = _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, session))
    val artifactrequests = _issued_requests(scenario, session)
    val deferreference = V.WorkflowArtifactReference("failed-check-current-artifact-defer-human", "1")
    val deferred = V.GoalPhasePrerequisitePublicationRecoveryInput(session, artifactdescription.target, "Defer",
      "SPECIFICATION-OWNED independent Human defers this exact current source artifact choice", deferreference)
    When("independent specification-owned Human evidence defers the current-source artifact decision")
    _provision(scenario, artifactoutput, R.purpose, deferred, deferreference, "failed-check-current-artifact-defer")
    val deferresult = scenario.call("command", R.recoveryOperation, Vector("--inputResource",
      _save(scenario, "failed-check-current-artifact-defer-input", json(deferred.toRecord()).noSpaces)))
      .business[V.GoalPhasePrerequisitePublicationRecoveryResult]
    Then("Defer has no native history, counters, semantic work, publication or check effects")
    deferresult.accepted shouldBe false
    deferresult.outcome shouldBe "deferred"
    _history(scenario, session) shouldBe artifacthistory
    _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, session)) shouldBe artifactsnapshot
    _issued_requests(scenario, session) shouldBe artifactrequests
    _lines(scenario.root.resolve("primary-all-sbt-invocations.txt")) shouldBe callsbefore
    _lines(scenario.root.resolve("primary-publication-attempts.txt")) shouldBe publicationsbefore
    val artifactreference = V.WorkflowArtifactReference("failed-check-current-artifact-accept-human", "1")
    val artifactanswer = V.GoalPhasePrerequisitePublicationRecoveryInput(session, artifactdescription.target,
      "AcceptExistingArtifactsWithUnknownEffects",
      "SPECIFICATION-OWNED independent Human accepts existing artifacts at this exact current source; original effects remain UNKNOWN",
      artifactreference)
    When("independent specification-owned Human evidence accepts only the described current-source artifacts")
    _provision(scenario, artifactoutput, R.purpose, artifactanswer, artifactreference, "failed-check-current-artifact-accept")
    val artifactaccepted = scenario.call("command", R.recoveryOperation, Vector("--inputResource",
      _save(scenario, "failed-check-current-artifact-accept-input", json(artifactanswer.toRecord()).noSpaces)))
      .business[V.GoalPhasePrerequisitePublicationRecoveryResult]
    Then("one separate native direction is additive and admission starts no publication or SBT")
    artifactaccepted.accepted shouldBe true
    artifactaccepted.outcome shouldBe "current-source-artifacts-adopted"
    val artifactacceptedhistory = _history(scenario, session)
    _history_extension(artifacthistory, artifactacceptedhistory)
    val artifactentries = _array(parse(artifactacceptedhistory).toOption.get, "history")
      .filter(_.hcursor.get[String]("causation").exists(_.startsWith(A.causationPrefix)))
    artifactentries.size shouldBe 1
    val artifactrecord = _artifact_direction_()
    artifactrecord.direction.target shouldBe artifacttarget
    artifactrecord.direction.reference shouldBe ContextReference(artifactreference.identity, artifactreference.revision)
    artifactrecord.reference should not be artifactrecord.direction.reference
    artifactrecord.adoption.direction shouldBe artifactrecord.reference
    artifactrecord.adoption.previous shouldBe origin.unknown
    artifactrecord.adoption.guard shouldBe currentcapture.postStageGuard
    artifactrecord.adoption.source shouldBe A.sourceOf(currentcapture.postStageGuard)
    val artifactbytes = A.codec.encode(artifactrecord.direction).noSpaces
    _lines(scenario.root.resolve("primary-all-sbt-invocations.txt")) shouldBe callsbefore
    _lines(scenario.root.resolve("primary-publication-attempts.txt")) shouldBe publicationsbefore
    val artifactacceptedsnapshot = _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, session))
    When("the old exact artifact answer is submitted again at the newer native frontier")
    val duplicatedartifact = scenario.call("command", R.recoveryOperation, Vector("--inputResource",
      _save(scenario, "failed-check-current-artifact-duplicate-input", json(artifactanswer.toRecord()).noSpaces)))
    Then("the stale answer is refused without a duplicate direction or effect")
    _refused(duplicatedartifact)
    _history(scenario, session) shouldBe artifactacceptedhistory
    _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, session)) shouldBe artifactacceptedsnapshot
    _primary_repair_fact(scenario, session).asJson.noSpaces shouldBe primarybytes
    _primary_facts(scenario, session) shouldBe originalfacts
    A.codec.encode(_artifact_direction_().direction).noSpaces shouldBe artifactbytes
    When("a separate ordinary Advance consumes the artifact direction and checks primary publication authority")
    val publicationoutput = scenario.advance(task)
    val publicationadvance = publicationoutput.business[V.GoalPhaseAdvanceResult]
    val reviewoutput = if (publicationadvance.work.exists(_.kind == "ReviewStep")) publicationoutput else {
      Then("any current publication boundary is an actual existing target with no inferred Human authority")
      _lines(scenario.root.resolve("primary-all-sbt-invocations.txt")) shouldBe callsbefore
      _lines(scenario.root.resolve("primary-publication-attempts.txt")) shouldBe publicationsbefore
      val describedoutput = scenario.call("command", R.describeOperation, Vector("--sessionId", session))
      val described = describedoutput.business[V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult]
      val publicationtarget = take(P.targetC(described.target))
      publicationtarget.candidate shouldBe candidate
      publicationtarget.failure shouldBe primaryfact.failure
      publicationtarget.missingAuthority shouldBe "new-primary-publication"
      val reference = V.WorkflowArtifactReference("failed-check-current-publication-human", "1")
      val answer = V.GoalPhasePrerequisitePublicationRecoveryInput(session, described.target, "AuthorizePrimaryPublicationOnce",
        "SPECIFICATION-OWNED independent Human covers only this current candidate's existing publication", reference)
      When("independent specification-owned Human evidence covers that offered current publication and Advance continues")
      _provision(scenario, describedoutput, R.purpose, answer, reference, "failed-check-current-publication-human")
      scenario.call("command", R.recoveryOperation, Vector("--inputResource",
        _save(scenario, "failed-check-current-publication-answer", json(answer.toRecord()).noSpaces)))
        .business[V.GoalPhasePrerequisitePublicationRecoveryResult].accepted shouldBe true
      scenario.advance(task)
    }
    val review = reviewoutput.business[V.GoalPhaseAdvanceResult]
    val reviewrequest = _issued_request(scenario, session)
    Then("fresh Compile and document-dependent TestOnly qualify the new source and issue genuine independent ReviewStep")
    review.work.get.kind shouldBe "ReviewStep"
    review.validation.get.outcome shouldBe Some("passed")
    review.validation.get.selectedChecks.map(_.identity) shouldBe _primary_checks.map(_.identity)
    review.validation.get.checks.foreach(_.exitCode shouldBe Some(0))
    reviewrequest.review.get.dependencyValidations.exists(value => value.dependencySource.contains(currentcapture) &&
      value.sourceConsumption.contains(S.consumptionOf(currentcapture)) && value.outcome.contains("passed")) shouldBe true
    reviewrequest.selectedPlan shouldBe nextrequest.selectedPlan
    reviewrequest.originalBase shouldBe nextrequest.originalBase
    reviewrequest.completed shouldBe nextrequest.completed
    review.repairProgress shouldBe next.repairProgress
    reviewrequest.issues.get.issues.filter(_.source == "TEST").map(_.identity) shouldBe testissues.map(_.identity)
    reviewrequest.issues.get.issues.filter(_.source == "TEST").foreach(_.status shouldBe "RESOLVED")
    _lines(scenario.root.resolve("primary-publication-attempts.txt")).size shouldBe publicationsbefore.size + 1
    Files.readString(scenario.root.resolve("primary-real-check-passed.txt")) shouldBe "qualified-primary-api\n"
    _native_fixture(scenario)(G.observeInWorkspaceC(_, binding.dependency.workspace)) shouldBe committed
    currentfiles.foreach { case (path, bytes) => Files.readAllBytes(repository.resolve(path)).toVector shouldBe bytes }
    val retained = _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, session)).toMap
    originalsnapshot.foreach { case (key, bytes) => retained.get(key) shouldBe Some(bytes) }
    _primary_repair_fact(scenario, session).asJson.noSpaces shouldBe primarybytes
    _primary_facts(scenario, session) shouldBe originalfacts
    A.codec.encode(_artifact_direction_().direction).noSpaces shouldBe artifactbytes
    capture.asJson.noSpaces shouldBe capturebytes
    failure.asJson.noSpaces shouldBe failurebytes
    val reviewhistory = _history(scenario, session)
    val reviewrequests = _issued_requests(scenario, session)
    val reviewlogs = logpaths.map(path => path -> _lines(scenario.root.resolve(path)))
    When("ordinary Advance recollects the completed current candidate and pending independent review once")
    val repeated = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
    Then("no candidate, publication, check, source observation or semantic issuance repeats")
    repeated shouldBe review.copy(validation = review.validation.map(_.copy(assessmentJournal = None)))
    _history(scenario, session) shouldBe reviewhistory
    _issued_requests(scenario, session) shouldBe reviewrequests
    _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, session)).toMap shouldBe retained
    reviewlogs.foreach { case (path, lines) => _lines(scenario.root.resolve(path)) shouldBe lines }

    When("the independent fixture reviewer provides its actual review and normal authority closes the qualified Step")
    val closedoutput = _close_step(scenario, task, reviewoutput, "failed-check-current-source")
    val closed = closedoutput.business[V.GoalPhaseAdvanceResult]
    Then("the accepted first Step remains exact and the qualified second Step has its own independent review and real commit")
    closed.work.get.completed.dropRight(1) shouldBe next.work.get.completed
    closed.work.get.completed.map(_.step) shouldBe Vector("step-1", "step-2")
    closed.work.get.completed.last.review shouldBe Some("fixture-failed-check-current-source-step-review")
    closed.work.get.completed.last.commit.identity shouldBe scenario.candidateHead
    closed.work.get.kind shouldBe "ImplementStep"
    closed.work.get.stepIdentity shouldBe Some("step-3")
    closed.repairProgress shouldBe nextrequest.repairProgress.map(value => GoalPhaseRepairProgress.publicSnapshot(
      value.copy(automaticCount = 0, ordinaryCount = 0)))
    _primary_repair_fact(scenario, session).asJson.noSpaces shouldBe primarybytes
    _primary_facts(scenario, session) shouldBe originalfacts
    _source_stages(scenario, session) shouldBe sourcecount
    _stages(scenario, session) shouldBe recoverycount
    reviewlogs.foreach { case (path, lines) => _lines(scenario.root.resolve(path)) shouldBe lines }
    val closedhistory = _history(scenario, session)
    val closedrequests = _issued_requests(scenario, session)
    When("the closed candidate is recollected once through ordinary Advance")
    val repeatedclosed = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
    Then("closure and the next semantic issuance introduce no duplicate publication, check or source observation")
    repeatedclosed shouldBe closed.copy(message = closed.work.get.purpose, validation = None)
    _history(scenario, session) shouldBe closedhistory
    _issued_requests(scenario, session) shouldBe closedrequests
    reviewlogs.foreach { case (path, lines) => _lines(scenario.root.resolve(path)) shouldBe lines }
  }

  /** E10 stops at description: genuine scope and source questions precede the
    * single finite-inventory change; no source Direction is adopted. */
  private def _describe_primary_history_source(scenario: F.Scenario, executable: Path, task: Path,
    reviewoutput: F.Result, capture: S.Capture, origin: U.Target): Unit = {
    val review = reviewoutput.business[V.GoalPhaseAdvanceResult]
    val session = review.sessionId
    val originalfact = _primary_repair_fact(scenario, session)
    val originalbytes = originalfact.asJson.noSpaces
    val originalpolicy = originalfact.failure.processPolicy
    val originalcapture = capture.asJson.noSpaces
    val originaldirections = _primary_facts(scenario, session)
    val binding = capture.direction.target.binding
    val repository = Path.of(binding.root)
    val additions = Vector.tabulate(31)(index => f"docs/additional-dependency-${index + 1}%02d.md")
    Given("E10 the actual primary correction has passed its selected checks and issued independent ReviewStep")
    review.work.get.kind shouldBe "ReviewStep"
    review.validation.get.outcome shouldBe Some("passed")
    review.validation.get.selectedChecks.map(_.identity) shouldBe _primary_checks.map(_.identity)
    review.validation.get.checks.map(_.identity) shouldBe _primary_checks.map(_.identity)
    review.validation.get.checks.foreach(_.exitCode shouldBe Some(0))
    originalfact.failure.candidate.dependencySourceCapture shouldBe Some(capture)
    originalfact.failure.failed.request.target.dependency shouldBe None
    binding.paths.size shouldBe 3
    additions.foreach(path => Files.exists(repository.resolve(path)) shouldBe false)
    val finding = "The required selected dependency integration documents are missing"
    val reviewresult = Json.obj("outcome" -> "repair".asJson,
      "summary" -> "SPECIFICATION-OWNED independent review requires the missing dependency integration documents".asJson,
      "findings" -> Vector(finding).asJson)
    val reviewreference = V.WorkflowArtifactReference("primary-history-independent-repair-review", "1")
    val reviewexecution = V.WorkflowExecutionEvidence(Vector(reviewreference), Some("specification-independent-reviewer"), None, None)
    val reviewinput = V.WorkflowEvidencePreparationInput("GoalPhase", "review", reviewoutput.invocation.stdout.toUri.toASCIIString,
      _save(scenario, "primary-history-review-result", reviewresult.noSpaces),
      _save(scenario, "primary-history-review-reference", json(reviewreference.toRecord()).noSpaces),
      _save(scenario, "primary-history-review-execution", json(reviewexecution.toRecord()).noSpaces),
      "normal-development-host", Instant.now().minusSeconds(60).toString, "entity:smworkflow.cli.host_evidence", None)
    When("public evidence preparation and independent Review Submit request repair of the actual qualified candidate")
    val preparedreview = scenario.prepareHostInput("prepareEvidence", reviewinput.toRecord())
    preparedreview.exitCode shouldBe 0
    scenario.selectPreparedImport(preparedreview)
    scenario.call("command", review.submitOperation.get, Vector("--sessionId", review.submitSessionId.get,
      "--result", reviewresult.noSpaces, "--resultReference", json(reviewreference.toRecord()).noSpaces,
      "--executionEvidence", json(reviewexecution.toRecord()).noSpaces))
      .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
    val repair = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
    val repairrequest = _issued_request(scenario, session)
    Then("ordinary RepairStep retains its actual review cause and repair counts without a stale publication failure")
    repair.work.get.kind shouldBe "RepairStep"
    repairrequest.publicationFailure shouldBe None
    repairrequest.review should not be empty
    repairrequest.review.get.priorReviews.last.resultIdentity shouldBe reviewreference.identity
    repairrequest.review.get.priorReviews.last.result.findings shouldBe Vector(finding)
    repairrequest.repairDirective.get.findings shouldBe Vector(finding)
    repairrequest.repairProgress should not be empty
    repair.repairProgress shouldBe repairrequest.repairProgress.map(GoalPhaseRepairProgress.publicSnapshot)
    val scopeproposal = capture.direction.target.predecessor.approvals.head.proposal.copy(
      identity = "primary-history-additional-scope", discovery = "REPAIR", paths = additions,
      reason = "SPECIFICATION-OWNED required tiny dependency integration documents",
      acceptance = Vector("Qualify the additional documents through the same dependency checks"))
    val pending = repair.work.get.issues.toVector.flatMap(_.issues).filter(_.status != "RESOLVED").map(issue =>
      V.GoalPhaseIssueDisposition(issue.identity, "BLOCKED", "SPECIFICATION-OWNED additional dependency scope is required", None))
    When("the actual RepairStep requests additional dependency scope while retaining every unresolved issue as BLOCKED")
    _submit(scenario, repair, Json.obj("outcome" -> "decision-needed".asJson,
      "summary" -> "SPECIFICATION-OWNED additional dependency document scope".asJson, "findings" -> Json.arr(),
      "dependencyProposal" -> scopeproposal.asJson, "issueReport" -> json(V.GoalPhaseIssueReport(pending).toRecord())),
      "primary-history-scope-request")
    val questionoutput = scenario.advance(task)
    val question = questionoutput.business[V.GoalPhaseAdvanceResult]
    Then("normal Advance emits a genuine scope Human")
    question.kind shouldBe "Human"
    val answer = V.GoalPhaseOrderedDecisionAnswer("ApproveDependencyScope", "SPECIFICATION-OWNED required dependency documents")
    val reference = V.WorkflowArtifactReference("primary-history-additional-approval", "1")
    val execution = _provision(scenario, questionoutput, "human", answer, reference, "primary-history-scope")
    When("independent fixture evidence and public submit accept that actual scope answer")
    scenario.call("command", question.submitOperation.get, Vector("--sessionId", question.submitSessionId.get,
      "--answer", json(answer.toRecord()).noSpaces, "--resultReference", json(reference.toRecord()).noSpaces,
      "--executionEvidence", json(execution.toRecord()).noSpaces))
      .business[V.GoalPhaseOrderedDecisionSubmissionResult].accepted shouldBe true
    val resumed = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
    Then("the approved scope resumes the ordinary RepairStep")
    resumed.work.get.kind shouldBe "RepairStep"
    val resumedrequest = _issued_request(scenario, session)
    resumedrequest.review shouldBe repairrequest.review
    resumedrequest.publicationFailure shouldBe None
    resumedrequest.repairProgress shouldBe repairrequest.repairProgress
    resumed.repairProgress shouldBe repair.repairProgress
    resumedrequest.generation should be > repairrequest.generation
    val blocked = resumed.work.get.issues.toVector.flatMap(_.issues).filter(_.status != "RESOLVED").map(issue =>
      V.GoalPhaseIssueDisposition(issue.identity, "BLOCKED", "SPECIFICATION-OWNED committed source integration requires direction", None))
    When("that issued RepairStep honestly requests source integration and Advance emits an unanswered Human")
    _submit(scenario, resumed, Json.obj("outcome" -> "decision-needed".asJson,
      "summary" -> "SPECIFICATION-OWNED committed dependency source integration requires direction".asJson,
      "findings" -> Json.arr(), "issueReport" -> json(V.GoalPhaseIssueReport(blocked).toRecord())),
      "primary-history-source-request")
    val human = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
    Then("the source description has a real pending Human frontier")
    human.kind shouldBe "Human"

    Given("a same-root same-branch descendant commits the three original selections, 31 approved documents and a background baseline")
    val beforeguard = _native_fixture(scenario)(S.observeC(_, binding, Vector.empty))
    beforeguard.head shouldBe binding.base.head
    beforeguard.dirty shouldBe empty
    val originalfiles = binding.paths.map(path => path -> Files.readAllBytes(repository.resolve(path)).toVector)
    additions.foreach { path =>
      Files.createDirectories(repository.resolve(path).getParent)
      Files.writeString(repository.resolve(path), "SPECIFICATION-OWNED dependency integration document " + path + "\n",
        StandardOpenOption.CREATE_NEW)
    }
    val baseline = "background-baseline.txt"
    Files.writeString(repository.resolve(baseline), "SPECIFICATION-OWNED committed background baseline\n", StandardOpenOption.CREATE_NEW)
    val paths = binding.paths ++ additions
    paths.size shouldBe 34
    paths should not contain baseline
    def _setup_(arguments: Vector[String]): Unit = take(_run_setup_command_(
      Vector("--no-optional-locks", "--no-replace-objects", "-c", "core.fsmonitor=false", "-c", "gc.auto=0",
        "-C", repository.toString) ++ arguments))
    _setup_(Vector("add", "--") ++ paths :+ baseline)
    _setup_(Vector("-c", "user.name=Primary History Specification", "-c", "user.email=primary-history@example.invalid",
      "commit", "--no-verify", "--no-gpg-sign", "--only", "-m", "Specification-owned committed dependency integration", "--") ++ paths :+ baseline)
    _setup_(Vector("merge-base", "--is-ancestor", binding.base.head, "HEAD^"))
    val committed = _native_fixture(scenario)(G.observeInWorkspaceC(_, binding.dependency.workspace))
    committed.branch shouldBe binding.branch
    committed.head should not be binding.base.head
    committed.dirty shouldBe empty
    originalfiles.foreach { case (path, bytes) => Files.readAllBytes(repository.resolve(path)).toVector shouldBe bytes }
    val replacement = binding.copy(revision = binding.revision + 1, paths = paths,
      base = GoalPhaseOrderedProgression.Snapshot(committed.head, committed.tree))
    val approval = D.Approval(binding.step, scopeproposal, ContextReference(reference.identity, reference.revision))
    val proposal = _source_selection(scenario, origin, Some(replacement), "committed", Vector(approval))
    proposal.coverage shouldBe capture.direction.target.proposal.coverage :+
      S.Coverage(approval.decision, scopeproposal.acceptance, replacement.checks.map(_.identity))
    proposal.consumption.expectedModes shouldBe paths.map(S.ExpectedMode(_, "100644"))
    val configfile = scenario.root.resolve(".textus/config.json")
    val config = parse(Files.readString(configfile)).toOption.get
    val process = parse(config.hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
    val programs = _array(process, "gitPrograms")
    def _inventory_(program: Json): Boolean =
      program.hcursor.get[String]("target").contains(binding.dependency.workspace) &&
        program.hcursor.get[String]("operation").contains(GitOperationKind.TreeEntries.wireName) &&
        program.hcursor.get[String]("selectionId").contains(S.committedInventorySelection)
    programs.count(_inventory_) shouldBe 1
    programs.find(_inventory_).get.hcursor.get[Vector[String]]("selectedPaths") shouldBe Right(binding.paths)
    def _select_inventory_(selection: Vector[String]): Unit = {
      val revised = process.mapObject(_.add("gitPrograms", programs.map { program =>
        if (_inventory_(program)) program.mapObject(_.add("selectedPaths", selection.asJson)) else program
      }.asJson))
      Files.writeString(configfile, config.mapObject(_.add("textus.subsystem.process-execution.plan", revised.noSpaces.asJson)).noSpaces)
    }
    def _describe_(label: String): F.Result = scenario.callInstalled(executable, "GoalPhaseService", S.describeOperation,
      Vector("--inputResource", _save(scenario, label, json(V.GoalPhaseDependencySourceRevisionDescriptionInput(session,
        GoalPhaseNativeMetadata.record(proposal.asJson)).toRecord()).noSpaces)),
      assemblyDescriptor = Some(scenario.root.resolve(".textus/assembly.yaml")))
    val nativebefore = _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, session))
    val historybefore = _history(scenario, session)
    val stagecount = _source_stages(scenario, session)
    val recoverycount = _stages(scenario, session)
    val logpaths = Vector("primary-all-sbt-invocations.txt", "primary-publication-attempts.txt",
      "dependency/all-sbt-invocations.txt", "dependency/publication-attempts.txt",
      "dependency-successor/all-sbt-invocations.txt", "dependency-successor/publication-attempts.txt")
    val logs = logpaths.map(path => path -> _lines(scenario.root.resolve(path)))
    def _unchanged_(): Unit = {
      _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, session)) shouldBe nativebefore
      _history(scenario, session) shouldBe historybefore
      _primary_repair_fact(scenario, session).asJson.noSpaces shouldBe originalbytes
      _primary_repair_fact(scenario, session).failure.processPolicy shouldBe originalpolicy
      _primary_facts(scenario, session) shouldBe originaldirections
      _source_stages(scenario, session) shouldBe stagecount
      _stages(scenario, session) shouldBe recoverycount
      logs.foreach { case (path, lines) => _lines(scenario.root.resolve(path)) shouldBe lines }
      Files.readString(scenario.root.resolve("primary-real-check-passed.txt")) shouldBe "qualified-primary-api\n"
    }
    When("a fresh description uses a denied incomplete finite inventory despite readable historical policy")
    _select_inventory_(paths.dropRight(1))
    val invalid = _describe_("primary-history-invalid-inventory")
    Then("current inventory checks refuse without changing native history, counters, failures or effects")
    invalid.exitCode should not be 0
    _refused(invalid)
    _unchanged_()
    When("only the existing committed TreeEntries selection is replaced with the exact approved 34 paths")
    _select_inventory_(paths)
    val describedoutput = _describe_("primary-history-finite-description")
    Then("the selected installed CLI describes the actual committed successor and every selected and baseline partition")
    describedoutput.exitCode shouldBe 0
    val description = describedoutput.business[V.GoalPhaseDependencySourceRevisionDescriptionResult]
    val target = take(S.targetC(description.target))
    target.binding shouldBe replacement
    target.proposal shouldBe proposal
    target.sessionId shouldBe session
    target.step shouldBe binding.step
    target.plan shouldBe originalfact.failure.plan
    target.primaryBase shouldBe originalfact.failure.base
    target.completed shouldBe originalfact.failure.completed
    target.repairProgress shouldBe _issued_request(scenario, session).repairProgress.get
    target.sourceSuccession.get.parent shouldBe capture.direction.directive
    target.sourceSuccession.get.previousDirection shouldBe capture.direction.directive
    target.sourceSuccession.get.approvals shouldBe Vector(approval)
    val succession = target.committedBaseSuccession.get
    succession.previousBase shouldBe binding.base
    succession.nextBase shouldBe replacement.base
    succession.phaseChangedPaths shouldBe additions
    succession.baselineChangedPaths shouldBe Vector(baseline)
    succession.previousExcludedChangedPaths shouldBe empty
    succession.addedExcludedChangedPaths shouldBe empty
    succession.changes.map(_.path).toSet shouldBe (additions :+ baseline).toSet
    succession.changes.foreach { change =>
      change.status shouldBe "A"
      change.oldMode shouldBe "000000"
      change.newMode shouldBe "100644"
      change.oldBlob shouldBe None
      change.newBlob should not be empty
    }
    succession.committedEntries.map(_.path) shouldBe paths
    succession.committedEntries.foreach(_.entry.map(_.mode) shouldBe Some("100644"))
    target.preStageGuard shouldBe _native_fixture(scenario)(S.observeC(_, replacement, Vector.empty))
    target.preStageGuard.root shouldBe binding.root
    target.preStageGuard.branch shouldBe binding.branch
    target.preStageGuard.head shouldBe replacement.base.head
    target.preStageGuard.committedTree shouldBe replacement.base.tree
    target.preStageGuard.indexTree shouldBe replacement.base.tree
    target.preStageGuard.dirty shouldBe empty
    target.preStageGuard.selectedEntries shouldBe empty
    target.preStageGuard.selectedFiles.map(_.path) shouldBe paths
    target.preStageGuard.excludedFiles shouldBe empty
    target.preStageGuard.excludedEntries shouldBe empty
    capture.asJson.noSpaces shouldBe originalcapture
    val currentprocess = parse(parse(Files.readString(configfile)).toOption.get.hcursor
      .get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
    currentprocess shouldBe process.mapObject(_.add("gitPrograms", programs.map { program =>
      if (_inventory_(program)) program.mapObject(_.add("selectedPaths", paths.asJson)) else program
    }.asJson))
    _unchanged_()
  }

  /** The failed publication and subsequent semantic repair are both genuine
    * public operations. Native observations only qualify their retained facts. */
  private def _repair_primary_candidate(scenario: F.Scenario, task: Path, failedoutput: F.Result,
    expectedprogress: GoalPhaseRepairProgress.Snapshot, expectedgeneration: Long,
    narrowauthority: Boolean, retainedcapture: Option[S.Capture] = None, correctivedoc: Boolean = false,
    expectcheckfailure: Boolean = false): F.Result = {
    val failed = failedoutput.business[V.GoalPhaseAdvanceResult]
    val session = failed.sessionId
    val failedbytes = Files.readAllBytes(failedoutput.invocation.stdout).toVector
    val publication = GoalPhaseNativeMetadata.normalizePublicJson(json(failed.localPublication.get))
      .flatMap(_.as[LocalPublication.Result].left.map(_.message)).fold(fail(_), identity)
    val calls = _lines(scenario.root.resolve("primary-all-sbt-invocations.txt"))
    val publications = _lines(scenario.root.resolve("primary-publication-attempts.txt"))
    val beforehistory = _history(scenario, session)
    Given("a retained actual primary Compile failure, with no TestOnly receipt or new Human authority")
    failed.outcome shouldBe Some("failed")
    failed.validation shouldBe None
    R.confirmedFailure(publication) shouldBe true
    Files.exists(scenario.root.resolve("primary-real-check-passed.txt")) shouldBe false
    When("a separate ordinary Advance automatically reserves and issues the primary RepairStep")
    val repairoutput = scenario.advance(task)
    val repair = repairoutput.business[V.GoalPhaseAdvanceResult]
    val request = _issued_request(scenario, session)
    Then("one ordinary repair and generation increment retain the complete publication diagnostics without synthetic validation")
    repair.work.get.kind shouldBe "RepairStep"
    repair.human shouldBe None
    repair.authorityContext shouldBe None
    repair.validation shouldBe None
    repair.work.get.checkFailure shouldBe None
    request.generation shouldBe expectedgeneration + 1
    request.repairProgress shouldBe Some(expectedprogress.copy(
      automaticCount = expectedprogress.automaticCount + 1, ordinaryCount = expectedprogress.ordinaryCount + 1))
    repair.repairProgress shouldBe request.repairProgress.map(GoalPhaseRepairProgress.publicSnapshot)
    val context = request.publicationFailure.get
    val primaryfact = Option.when(expectcheckfailure)(_primary_repair_fact(scenario, session))
    context.publication shouldBe publication
    context.testsExecuted shouldBe false
    context.changedPaths should contain(_primary_source)
    context.diff should include("PrimaryProduct")
    request.checkFailure shouldBe None
    request.repairDirective.get.reason should include("tests were not executed")
    request.repairDirective.get.findings.mkString("\n") should include("missingPrimaryApi")
    val publiccontext = parse(Files.readString(repairoutput.invocation.stdout)).toOption.get.hcursor
      .downField("work").get[Json]("publication_failure").toOption.get
    GoalPhaseNativeMetadata.normalizePublicJson(publiccontext).fold(fail(_), identity).deepDropNullValues shouldBe
      context.asJson.deepDropNullValues
    _history_extension(beforehistory, _history(scenario, session))
    val repairhistory = _history(scenario, session)
    When("Advance recollects that same issued request before any repair completion")
    scenario.advance(task).business[V.GoalPhaseAdvanceResult] shouldBe repair
    Then("no additional repair reservation, publication or checks occur")
    _issued_request(scenario, session) shouldBe request
    _history(scenario, session) shouldBe repairhistory
    _lines(scenario.root.resolve("primary-all-sbt-invocations.txt")) shouldBe calls
    _lines(scenario.root.resolve("primary-publication-attempts.txt")) shouldBe publications

    When("the assigned worker corrects only its owned primary source and submits the actual issued semantic completion")
    val source = Path.of(scenario.host.target.root).resolve(_primary_source)
    val original = Files.readString(source)
    original should include("missingPrimaryApi")
    Files.writeString(source, original.replace("missingPrimaryApi", "\"qualified-primary-api\""))
    if (correctivedoc) {
      When("the assigned RepairStep worker records the necessary corrective document in its owned docs scope")
      val repository = Path.of(scenario.host.target.root)
      Files.writeString(repository.resolve(_primary_note), _primary_note_text, StandardOpenOption.CREATE_NEW)
      val phase = repository.resolve("docs/phase.md")
      val phasebytes = Files.readAllBytes(phase).toVector
      val sentinelbytes = Files.readAllBytes(repository.resolve(_primary_sentinel)).toVector
      val sentinelstatus = _primary_snapshot(scenario)._1.dirty.find(_.path == _primary_sentinel)
      When("the E9 RepairStep worker renames the tracked phase document and stages each literal endpoint under the existing docs selection")
      Files.move(phase, repository.resolve(_primary_renamed_phase))
      val staged = _native_fixture(scenario) { core => for {
        identity <- GitSelectionId.parseC("goal-path-2")
        oldpath <- GitPath.parseC("docs/phase.md")
        newpath <- GitPath.parseC(_primary_renamed_phase)
        _ <- WorkflowManagedProcessExecution.executeSelectedGitC(core, GitOperation.StageSelectedAll(identity),
          GitSelectionParameters(identity, Vector(oldpath), None), TypedGitHostPlanFixture.workspace).flatMap(_.valueC)
        _ <- WorkflowManagedProcessExecution.executeSelectedGitC(core, GitOperation.StageSelectedAll(identity),
          GitSelectionParameters(identity, Vector(newpath), None), TypedGitHostPlanFixture.workspace).flatMap(_.valueC)
        observed <- G.observeC(core)
      } yield observed }
      Then("E9 the real index records the staged rename before semantic completion and keeps the excluded sentinel untracked")
      staged.dirty.find(_.path == _primary_renamed_phase) shouldBe
        Some(G.DirtyPath('R', ' ', _primary_renamed_phase, Some("docs/phase.md")))
      staged.dirty.exists(_.path == "docs/phase.md") shouldBe false
      staged.dirty.find(_.path == _primary_sentinel) shouldBe sentinelstatus
      Files.exists(phase) shouldBe false
      Files.readAllBytes(repository.resolve(_primary_renamed_phase)).toVector shouldBe phasebytes
      Files.readAllBytes(repository.resolve(_primary_sentinel)).toVector shouldBe sentinelbytes
    }
    _submit(scenario, repair, Json.obj("outcome" -> "completed".asJson,
      "summary" -> "SPECIFICATION-OWNED corrected primary Compile source within the issued Step".asJson,
      "findings" -> Json.arr()), "fixture-primary-repair-completion")
    val candidateoutput = scenario.advance(task)
    val reviewoutput = if (!narrowauthority) candidateoutput else {
      Then("the historical one-shot authority pauses the new candidate before publication and checks")
      _lines(scenario.root.resolve("primary-all-sbt-invocations.txt")) shouldBe calls
      _lines(scenario.root.resolve("primary-publication-attempts.txt")) shouldBe publications
      Files.exists(scenario.root.resolve("primary-real-check-passed.txt")) shouldBe false
      def _describe_(): F.Result = scenario.call("command", R.describeOperation, Vector("--sessionId", session))
      val describedoutput = _describe_()
      val described = describedoutput.business[V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult]
      val target = take(P.targetC(described.target))
      target.schemaVersion shouldBe P.targetSchema
      target.repair shouldBe Some(context.repair)
      target.missingAuthority shouldBe "new-primary-publication"
      target.failure.failed shouldBe publication
      target.failure.generation shouldBe expectedgeneration
      target.generation shouldBe expectedgeneration + 1
      target.candidate.dependencySourceCapture shouldBe retainedcapture
      val reuse = target.candidate.primaryPublicationRepair.get
      reuse.repair shouldBe context.repair
      reuse.generation shouldBe expectedgeneration
      reuse.candidateRecordRevision shouldBe target.failure.candidateRecordRevision
      reuse.sourceRevision shouldBe target.failure.candidate.sourceRevision
      target.candidate.sourceRevision should not be reuse.sourceRevision
      retainedcapture.foreach(capture => {
        target.candidate.dependencySourceCapture.get.generation shouldBe capture.generation
        target.candidate.dependencySourceCapture.get.sourceRevision shouldBe capture.sourceRevision
      })
      val choice = "AuthorizePrimaryPublicationAndSameScopeRepairs"
      described.choices shouldBe Vector("AuthorizePrimaryPublicationOnce", choice, "Defer")
      val reference = V.WorkflowArtifactReference("fixture-primary-repair-same-scope-human", "1")
      val answer = V.GoalPhasePrerequisitePublicationRecoveryInput(session, described.target, choice,
        "SPECIFICATION-OWNED independent Human covers the actual primary publication and future repairs in this exact Step scope", reference)
      def _recover_(input: V.GoalPhasePrerequisitePublicationRecoveryInput, label: String): F.Result =
        scenario.call("command", R.recoveryOperation, Vector("--inputResource",
          _save(scenario, label, json(input.toRecord()).noSpaces)))
      When("the broader exact answer is submitted without independent Human evidence or with a crossed target or Session")
      _refused(_recover_(answer, "primary-repair-unproved-authority"))
      val crossed = target.copy(frontierRevision = (target.frontierRevision.toLong + 1).toString)
      _refused(_recover_(answer.copy(target = GoalPhaseNativeMetadata.record(crossed.asJson)), "primary-repair-crossed-target"))
      _refused(_recover_(answer.copy(sessionId = session + "-crossed"), "primary-repair-crossed-authority"))
      Then("the original candidate and absence of new effects remain exact")
      take(P.targetC(_describe_().business[V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult].target)) shouldBe target
      _lines(scenario.root.resolve("primary-all-sbt-invocations.txt")) shouldBe calls
      When("an independently provisioned Human adopts the actual offered same-scope choice through the existing recovery operation")
      _provision(scenario, describedoutput, R.purpose, answer, reference, "primary-repair-same-scope-human")
      _recover_(answer, "primary-repair-covered-authority").business[V.GoalPhasePrerequisitePublicationRecoveryResult].accepted shouldBe true
      Then("adoption retains authority without performing publication or checks")
      _lines(scenario.root.resolve("primary-all-sbt-invocations.txt")) shouldBe calls
      _lines(scenario.root.resolve("primary-publication-attempts.txt")) shouldBe publications
      When("the next ordinary Advance consumes that exact retained authority")
      scenario.advance(task)
    }
    val review = reviewoutput.business[V.GoalPhaseAdvanceResult]
    if (expectcheckfailure) {
      Then("E11 the real primary correction publishes once, then the actual missing-document TestOnly issues check-failure Repair")
      review.work.get.kind shouldBe "RepairStep"
      val current = _issued_request(scenario, session)
      current.review shouldBe None
      current.publicationFailure shouldBe None
      current.checkFailure.get.validation.outcome shouldBe Some("repair-required")
      current.checkFailure.get.validation.dependencySource shouldBe retainedcapture
      current.checkFailure.get.validation.receipts.last.identity shouldBe "primary-focused"
      current.checkFailure.get.validation.receipts.last.exitCode shouldBe Some(1)
      current.checkFailure.get.validation.receipts.last.stdout should include(
        "Required selected dependency integration document is missing: docs/additional-dependency-01.md")
      current.repairProgress shouldBe request.repairProgress.map(value => value.copy(
        automaticCount = value.automaticCount + 1, ordinaryCount = value.ordinaryCount + 1))
      current.completed shouldBe request.completed
      current.originalBase shouldBe request.originalBase
      current.selectedPlan shouldBe request.selectedPlan
      _primary_repair_fact(scenario, session) shouldBe primaryfact.get
      _lines(scenario.root.resolve("primary-publication-attempts.txt")).size shouldBe publications.size + 1
      Files.exists(scenario.root.resolve("primary-real-check-passed.txt")) shouldBe false
      Files.readAllBytes(failedoutput.invocation.stdout).toVector shouldBe failedbytes
      _history_extension(repairhistory, _history(scenario, session))
      return reviewoutput
    }
    Then("one repaired primary publication and the selected real Compile and TestOnly checks lead to independent Step review")
    review.work.get.kind shouldBe "ReviewStep"
    review.validation.get.outcome shouldBe Some("passed")
    review.validation.get.selectedChecks.map(_.identity) shouldBe _primary_checks.map(_.identity)
    review.validation.get.checks.map(_.identity) shouldBe _primary_checks.map(_.identity)
    review.validation.get.checks.foreach(_.exitCode shouldBe Some(0))
    retainedcapture.foreach { capture =>
      val actual = review.work.get.review.get.dependencyValidations.map(value =>
        GoalPhaseNativeMetadata.normalizePublicJson(json(value)).fold(fail(_), identity))
      actual.exists(_.hcursor.get[Json]("capture").contains(capture.asJson.deepDropNullValues)) shouldBe true
    }
    review.repairProgress shouldBe repair.repairProgress
    _lines(scenario.root.resolve("primary-publication-attempts.txt")).size shouldBe publications.size + 1
    Files.readString(scenario.root.resolve("primary-real-check-passed.txt")) shouldBe "qualified-primary-api\n"
    Files.readAllBytes(failedoutput.invocation.stdout).toVector shouldBe failedbytes
    _history_extension(repairhistory, _history(scenario, session))
    reviewoutput
  }

  /** Read only the two production-keyed receipts named by actual native
    * primary causations. The production codec validates their complete facts. */
  private def _primary_facts(scenario: F.Scenario, session: String): Vector[Json] =
    _native_fixture(scenario) { callcore =>
      final class PrimaryReceiptReader extends org.goldenport.cncf.action.ProcedureActionCall {
        val core: org.goldenport.cncf.action.ActionCall.Core = callcore
        def readC(key: String): Consequence[org.goldenport.record.Record] = {
          given org.goldenport.cncf.context.ExecutionContext = executionContext
          val space = executionContext.dataStoreSpace
          val previous = space.captureBinding()
          try bind_component_application_datastore_c("workflow").flatMap { _ =>
            val collection = DataStore.CollectionId.EntityStore(
              org.simplemodeling.model.datatype.EntityCollectionId("smworkflow", "cli", "continuations"))
            space.dataStore(collection).flatMap(_.load(collection, DataStore.StringEntryId(key))).flatMap(
              _.map(Consequence.success).getOrElse(Consequence.stateConflict("The actual primary operation receipt is absent")))
          } finally space.restoreBinding(previous)
        }
        def execute(): Consequence[org.goldenport.protocol.operation.OperationResponse] =
          Consequence.stateConflict("Specification-only exact primary receipt reader is not an executable operation")
      }
      val reader = new PrimaryReceiptReader
      for {
        snapshot <- LegacyDependencyHistoryFixture.snapshotC(callcore, session)
        catalog <- GoalPhaseRuntimeCatalog.loadC()
        definitions <- WorkflowInstanceRecordJsonV1.catalogC(Vector(catalog.definition))
        current <- WorkflowInstanceRecordJsonV1.decodeC(snapshot.toMap.apply("instance"), definitions)
        entries = current.history.filter(_.causation.value.startsWith(R.primaryCausationPrefix))
        facts <- entries.foldLeft(Consequence.success(Vector.empty[Json])) { (saved, entry) => for {
          prior <- saved
          revision = entry.causation.value.drop(R.primaryCausationPrefix.length)
          _ <- if (revision.matches("[0-9]+")) Consequence.unit
            else Consequence.stateConflict("Primary causation is not the production predecessor revision")
          reference = WorkflowMechanicalTransition.Reference(session + ":" + R.primaryCausationPrefix + revision + ":direction", "1")
          key <- WorkflowMechanicalTransition.keyC(reference)
          row <- reader.readC(key)
          bytes <- row.getString("mechanicalPayload").filter(_.startsWith("cncf.workflow-json.v1:"))
            .map(value => Consequence.success(value.drop("cncf.workflow-json.v1:".length)))
            .getOrElse(Consequence.stateConflict("The actual primary operation payload is absent"))
          receipt <- WorkflowMechanicalTransitionJsonV1.decodeC(bytes, definitions, R.primaryCodec, _receipt_limits)
          actual <- GoalPhaseNativeHistory.prefixC(current, receipt.next.revision)
          actualbytes <- WorkflowInstanceRecordJsonV1.encodeC(actual)
          receiptbytes <- WorkflowInstanceRecordJsonV1.encodeC(receipt.next)
          _ <- if (row.getString("id").contains(key) && receipt.reference == reference && actualbytes == receiptbytes &&
              receipt.component == SmWorkflowComponent.componentId && receipt.operation == GoalPhaseNativeRun.advanceOperation &&
              receipt.payload.value.reference == ContextReference(session, receipt.next.revision.value.toString))
            Consequence.unit else Consequence.stateConflict("Primary operation receipt crosses its actual native prefix or owner")
        } yield prior :+ receipt.payload.value.asJson }
      } yield facts
    }

  private def _primary_failure_continuation(scenario: F.Scenario, executable: Path, task: Path, session: String,
    publicationtarget: LocalPublication.Target, original: R.Target, first: D.Capture,
    firstoutput: F.Result, firstbytes: String, preserved: Path, preservedbytes: Vector[Byte],
    oldstages: Int, firstpublications: Int, committedsource: Boolean = false, repairprimary: Boolean = false,
    historicalpolicy: Boolean = false, sourcecheckfailure: Boolean = false): Unit = {
    val expectedstages = if (committedsource) 0 else 1
    val expectedprimarypublications = if (repairprimary) 3 else 2
    Given((if (committedsource) "E6 M3-SOURCE-SUCCESSOR-409: " else "E5 M3-PRIMARY-CONTINUATION-409: ") +
      "docs/spec/goal-phase-prerequisite-publication-recovery.md; " +
      "rules: ordered operation authority, exact source capture, no replay; a genuinely retained dependency UNKNOWN")
    val preservedstatus = Option.when(repairprimary)(
      _primary_snapshot(scenario)._1.dirty.find(_.path == "docs/preserved.md").get)
    preservedstatus.foreach { status =>
      status.index shouldBe '?'
      status.worktree shouldBe '?'
    }
    _interrupt_capture(scenario, session)
    WorkflowSbtCapture.exitCode(_leave_unknown(scenario, session, original)) shouldBe Some(0)
    val unknownoutput = scenario.advance(task)
    unknownoutput.business[V.GoalPhaseAdvanceResult].outcome shouldBe Some("uncertain")
    val unknownbytes = Files.readAllBytes(unknownoutput.invocation.stdout).toVector
    def _describe_(): F.Result = scenario.call("command", R.describeOperation, Vector("--sessionId", session))
    def _resource_(operation: String, input: RecordPresentable, name: String): F.Result = {
      val bytes = json(input.toRecord()).noSpaces
      Files.writeString(scenario.root.resolve("primary-input-resource-sizes.jsonl"), Json.obj(
        "operation" -> operation.asJson, "input" -> name.asJson,
        "utf8Bytes" -> bytes.getBytes(java.nio.charset.StandardCharsets.UTF_8).length.asJson).noSpaces + "\n",
        StandardOpenOption.CREATE, StandardOpenOption.APPEND)
      val result = scenario.call("command", operation, Vector("--inputResource", _save(scenario, name, bytes)))
      result.invocation.argv should not contain bytes
      result
    }
    def _publication_(input: LocalPublication.Input): LocalPublication.Result = {
      val output = scenario.callInstalled(executable, "LocalPublicationService", "collectLocalPublication",
        Vector("--input", input.asJson.mapObject(_.remove("answer")).noSpaces),
        assemblyDescriptor = Some(scenario.root.resolve(".textus/assembly.yaml"))).business[V.LocalPublicationResult]
      GoalPhaseNativeMetadata.normalizePublicJson(json(output.publication.get))
        .flatMap(_.as[LocalPublication.Result].left.map(_.message)).fold(fail(_), identity)
    }
    val unknowndescriptionoutput = _describe_()
    val unknowndescription = unknowndescriptionoutput.business[V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult]
    val unknown = take(U.targetC(unknowndescription.target))
    val calls = _lines(scenario.root.resolve("dependency/all-sbt-invocations.txt"))
    val oldhistory = _history(scenario, session)
    val oldreference = V.WorkflowArtifactReference("fixture-original-unknown-artifact-human", "1")
    val oldanswer = V.GoalPhasePrerequisitePublicationRecoveryInput(session, unknowndescription.target,
      "AcceptExistingArtifactsWithUnknownEffects", "SPECIFICATION-OWNED accept only the original source's unknown effects", oldreference)
    _provision(scenario, unknowndescriptionoutput, R.purpose, oldanswer, oldreference, "primary-origin-artifacts")
    _resource_(R.recoveryOperation, oldanswer, "primary-origin-recovery-input")
      .business[V.GoalPhasePrerequisitePublicationRecoveryResult].accepted shouldBe true
    val acceptedhistory = _history(scenario, session)
    _history_extension(oldhistory, acceptedhistory)
    val olddirection = _unknown_direction(scenario, session, unknown.frontierRevision)
    olddirection._2.choice shouldBe Some("AcceptExistingArtifactsWithUnknownEffects")
    _lines(scenario.root.resolve("dependency/all-sbt-invocations.txt")) shouldBe calls
    _lines(scenario.root.resolve("primary-publication-attempts.txt")) shouldBe empty

    When("ordinary continuation accepts that dependency operation and the primary really fails compiling its missing API")
    val failedoutput = scenario.advance(task)
    val failed = failedoutput.business[V.GoalPhaseAdvanceResult]
    failed.outcome shouldBe Some("failed")
    val failedbytes = Files.readAllBytes(failedoutput.invocation.stdout).toVector
    val primaryfailed = GoalPhaseNativeMetadata.normalizePublicJson(json(failed.localPublication.get))
      .flatMap(_.as[LocalPublication.Result].left.map(_.message)).fold(fail(_), identity)
    Then("a terminal primary failure is retained separately from the original dependency uncertainty, with no checks")
    primaryfailed.request.target.dependency shouldBe None
    R.confirmedFailure(primaryfailed) shouldBe true
    val failedprocess = WorkflowSbtCapture.decode(primaryfailed.process.get).fold(fail(_), identity)
    WorkflowSbtCapture.text(failedprocess.stdout).value should include("correctedApi")
    _lines(scenario.root.resolve("primary-publication-attempts.txt")).size shouldBe 1
    _lines(scenario.root.resolve("dependency/publication-attempts.txt")).size shouldBe firstpublications + 2
    Files.exists(scenario.root.resolve("primary-real-check-passed.txt")) shouldBe false
    val failurehistory = _history(scenario, session)
    // A failed process result can live outside native history; the accepted prefix is still exact.
    _array(parse(failurehistory).toOption.get, "history").take(_array(parse(acceptedhistory).toOption.get, "history").size) shouldBe
      _array(parse(acceptedhistory).toOption.get, "history")
    val failureoutput = _describe_()
    val failuredescription = failureoutput.business[V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult]
    val failuretarget = GoalPhaseNativeMetadata.normalizePublicJson(json(failuredescription.target)).fold(fail(_), identity)
    failuretarget.hcursor.get[String]("schemaVersion") shouldBe Right("sm-workflow.goal-phase-prerequisite-publication-recovery.v2")
    failuretarget.hcursor.get[String]("phase") shouldBe Right("SourceRequired")
    failuredescription.choices shouldBe Vector("Defer")
    failuretarget.hcursor.get[Json]("failed") shouldBe Right(primaryfailed.asJson.deepDropNullValues)
    failuretarget.hcursor.get[Json]("originalDirection") shouldBe Right(olddirection._2.asJson.deepDropNullValues)
    _array(failuretarget, "targets").flatMap(_.hcursor.get[String]("identity").toOption) shouldBe
      Vector(publicationtarget.identity, primaryfailed.request.target.identity)
    scenario.advance(task).business[V.GoalPhaseAdvanceResult] shouldBe failed
    _history(scenario, session) shouldBe failurehistory
    _lines(scenario.root.resolve("primary-publication-attempts.txt")).size shouldBe 1
    _publication_(unknown.unknown.request.input) shouldBe unknown.unknown
    _publication_(primaryfailed.request.input) shouldBe primaryfailed

    Given("one independently provisioned explicit combined Human answer for the concrete corrected-source continuation plan")
    val oldbinding = original.admission.binding
    val oldroot = Path.of(oldbinding.root)
    val oldguard = Option.when(committedsource)(_native_fixture(scenario)(S.observeC(_, oldbinding, Vector.empty)))
    val oldselected = Option.when(committedsource)(oldbinding.paths.map(path => path -> Files.readAllBytes(oldroot.resolve(path)).toVector))
    val oldhead = Option.when(committedsource)(Files.readAllBytes(oldroot.resolve(".git/HEAD")).toVector)
    val oldref = Option.when(committedsource) {
      val path = oldroot.resolve(".git/refs/heads/" + oldguard.get.branch)
      path -> Files.readAllBytes(path).toVector
    }
    val oldpublications = _lines(scenario.root.resolve("dependency/publication-attempts.txt"))
    val oldcalls = _lines(scenario.root.resolve("dependency/all-sbt-invocations.txt"))
    val correctedsource = "package fixture\nclass RecoveryMarker { def value: String = \"approved-correction\"; def correctedApi: String = \"qualified-primary-api\" }\n"
    if (!committedsource) Files.writeString(oldroot.resolve(_source), correctedsource)
    val selectedorigin = GoalPhaseNativeMetadata.normalizePublicJson(json(
      _describe_().business[V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult].target)).fold(fail(_), identity)
    val replacement = Option.when(committedsource) {
      Given("E6 a new tiny clone keeps the original ancestry and commits only the three accepted source inputs")
      val root = Files.createDirectories(scenario.root.resolve("dependency-successor"))
      val repository = root.resolve("repository")
      def _setup_(arguments: String*): Unit = take(_run_setup_command_(
        Vector("--no-optional-locks", "--no-replace-objects", "-c", "core.fsmonitor=false", "-c", "gc.auto=0") ++ arguments))
      _setup_("clone", "--no-local", "--", oldbinding.root, repository.toString)
      _setup_("-C", repository.toString, "checkout", "-b", "spec/source-successor")
      oldselected.get.foreach { case (path, bytes) =>
        Files.createDirectories(repository.resolve(path).getParent)
        Files.write(repository.resolve(path), bytes.toArray)
      }
      Files.writeString(repository.resolve(_source), correctedsource)
      take(_run_setup_command_(Vector("-C", repository.toString, "add", "--") ++ oldbinding.paths))
      _setup_("-C", repository.toString, "-c", "user.name=Source Successor Specification",
        "-c", "user.email=source-successor@example.invalid", "commit", "--no-verify", "--no-gpg-sign",
        "-m", "Specification-owned committed source successor")
      _setup_("-C", repository.toString, "merge-base", "--is-ancestor", oldguard.get.head, "HEAD^")
      val provisional = oldbinding.copy(revision = oldbinding.revision + 1,
        root = repository.toRealPath().toString, branch = "spec/source-successor")
      _source_successor_host(scenario, oldbinding, provisional)
      val observed = _native_fixture(scenario)(G.observeInWorkspaceC(_, provisional.dependency.workspace))
      observed.branch shouldBe provisional.branch
      observed.dirty shouldBe empty
      observed.head should not be oldguard.get.head
      provisional.copy(base = GoalPhaseOrderedProgression.Snapshot(observed.head, observed.tree))
    }
    val proposal = _source_selection(scenario, unknown, replacement, if (committedsource) "committed" else "selected-changes")
    val sourceinput = V.GoalPhaseDependencySourceRevisionDescriptionInput(session, GoalPhaseNativeMetadata.record(proposal.asJson))
    val sourceoutput = _resource_(S.describeOperation, sourceinput, "primary-source-description-input")
    val description = sourceoutput.business[V.GoalPhaseDependencySourceRevisionDescriptionResult]
    val selectedsource = take(S.targetC(description.target))
    val sourcetarget = GoalPhaseNativeMetadata.normalizePublicJson(json(description.target)).fold(fail(_), identity)
    sourcetarget.hcursor.get[String]("schemaVersion") shouldBe Right("sm-workflow.goal-phase-dependency-source-revision.v7")
    if (!committedsource) sourcetarget.hcursor.get[Json]("primaryFailureOrigin") shouldBe Right(selectedorigin)
    else {
      Then("the source target authenticates the old prefix separately from the new committed root and current process policy")
      val origin = sourcetarget.hcursor.get[Json]("primaryFailureOrigin").toOption.get
      origin.hcursor.get[String]("phase") shouldBe Right("SourceRequired")
      selectedorigin.asObject.get.keys.filterNot(Set("processPolicy", "dependencyGuard")).foreach(key =>
        origin.hcursor.get[Json](key) shouldBe selectedorigin.hcursor.get[Json](key))
      selectedsource.primaryFailureOrigin.get.originalDirection shouldBe olddirection._2
      selectedsource.primaryFailureOrigin.get.dependencyGuard shouldBe olddirection._2.target.guard
      selectedsource.primaryFailureOrigin.get.failed shouldBe primaryfailed
      selectedsource.primaryFailureOrigin.get.processPolicy should not be
        selectedorigin.hcursor.get[String]("processPolicy").toOption.get
      val currentpolicy = parse(selectedsource.primaryFailureOrigin.get.processPolicy).toOption.get.hcursor.get[Json]("processPlan").toOption.get
      _array(currentpolicy, "workspaces").filter(_.hcursor.get[String]("identity").contains(oldbinding.dependency.workspace))
        .map(_.hcursor.get[String]("root").toOption.get) shouldBe Vector(replacement.get.root)
      _array(currentpolicy, "sbtPrograms").filter(_.hcursor.get[String]("identity").contains("fixture-primary-publication"))
        .map(_.hcursor.downField("environment").get[String]("TEXTUS_CNCF_SOURCE_ROOT").toOption.get) shouldBe Vector(replacement.get.root)
      selectedsource.binding shouldBe replacement.get
      selectedsource.proposal.mode shouldBe "committed"
      selectedsource.proposal.consumption.sourceRoot shouldBe replacement.get.root
      selectedsource.proposal.consumption.expectedModes shouldBe oldbinding.paths.map(S.ExpectedMode(_, "100644"))
      selectedsource.preStageGuard.root shouldBe replacement.get.root
      selectedsource.preStageGuard.branch shouldBe replacement.get.branch
      selectedsource.preStageGuard.head shouldBe replacement.get.base.head
      selectedsource.preStageGuard.committedTree shouldBe replacement.get.base.tree
      selectedsource.preStageGuard.indexTree shouldBe replacement.get.base.tree
      selectedsource.preStageGuard.dirty shouldBe empty
      selectedsource.preStageGuard.selectedEntries shouldBe empty
      selectedsource.preStageGuard.selectedFiles.map(_.path) shouldBe oldbinding.paths
      selectedsource.committedBaseSuccession.get.committedEntries.map(_.path) shouldBe oldbinding.paths
      selectedsource.committedBaseSuccession.get.committedEntries.map(_.entry.map(_.mode)) shouldBe
        Vector.fill(oldbinding.paths.size)(Some("100644"))
    }
    selectedorigin.hcursor.get[Json]("failed") shouldBe Right(primaryfailed.asJson.deepDropNullValues)
    val choice = "AdoptDependencySourceRevisionAndContinuePublicationWithUnknownEffects"
    description.choices should contain allOf (choice, "AdoptDependencySourceRevisionWithUnknownEffects", "Defer")
    val reference = V.WorkflowArtifactReference("fixture-combined-primary-source-human", "1")
    val rationale = (("SPECIFICATION-OWNED explicit ordered source adoption, changed-source existing artifact acceptance, " +
      "and one fixed primary publication despite original UNKNOWN effects. ") * 2100).trim
    val answer = V.GoalPhaseDependencySourceRevisionAdoptionInput(session, description.target, choice, rationale, reference)
    val completeinput = json(answer.toRecord()).noSpaces
    completeinput.getBytes(java.nio.charset.StandardCharsets.UTF_8).length should be > 270 * 1024
    _refused(_resource_(S.adoptionOperation, answer, "primary-unproved-combined-input"))
    _provision(scenario, sourceoutput, "dependency-source-revision", answer, reference, "primary-combined-source-human")
    val beforeadoption = _history(scenario, session)
    val beforecalls = _lines(scenario.root.resolve("dependency/all-sbt-invocations.txt"))
    val beforeprimarycalls = _lines(scenario.root.resolve("primary-all-sbt-invocations.txt"))
    When("the complete resource input admits that one authenticated source Direction without executing its effects")
    _resource_(S.adoptionOperation, answer, "primary-combined-adoption-input")
      .business[V.GoalPhaseDependencySourceRevisionAdoptionResult].accepted shouldBe true
    val adoptedhistory = _history(scenario, session)
    _history_extension(beforeadoption, adoptedhistory)
    _array(parse(adoptedhistory).toOption.get, "history").size shouldBe
      _array(parse(beforeadoption).toOption.get, "history").size + 1
    _source_stages(scenario, session) shouldBe 0
    _lines(scenario.root.resolve("dependency/all-sbt-invocations.txt")) shouldBe beforecalls
    _lines(scenario.root.resolve("primary-all-sbt-invocations.txt")) shouldBe beforeprimarycalls
    _lines(scenario.root.resolve("primary-publication-attempts.txt")).size shouldBe 1

    When("separate ordinary Advance captures only the selected source, then derives each covered operation in order")
    val captureoutput = scenario.advance(task)
    captureoutput.business[V.GoalPhaseAdvanceResult].sessionId shouldBe session
    val capturehistory = _history(scenario, session)
    _history_extension(adoptedhistory, capturehistory)
    _source_stages(scenario, session) shouldBe expectedstages
    val capturedoutput = _describe_()
    val captured = capturedoutput.business[V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult]
    val capturetarget = GoalPhaseNativeMetadata.normalizePublicJson(json(captured.target)).fold(fail(_), identity)
    capturetarget.hcursor.get[String]("phase") shouldBe Right("ArtifactDecision")
    capturetarget.hcursor.get[Long]("generation") shouldBe failuretarget.hcursor.get[Long]("generation").map(_ + 1)
    Vector("plan", "base", "completed", "repairProgress", "requestingResult", "primarySource").foreach(key =>
      capturetarget.hcursor.get[Json](key) shouldBe failuretarget.hcursor.get[Json](key))
    val candidatebefore = failuretarget.hcursor.downField("candidate")
    val candidateafter = capturetarget.hcursor.downField("candidate")
    Vector("step", "candidate", "selectedPaths", "stagedPaths", "changedPaths", "diff", "hygiene", "stepEvidence", "continuation")
      .foreach(key => candidateafter.downField(key).focus shouldBe candidatebefore.downField(key).focus)
    val sourcecapture = capturetarget.hcursor.get[Json]("sourceCapture").toOption.get
    sourcecapture.hcursor.get[Vector[String]]("stagedPaths") shouldBe Right(if (committedsource) Vector.empty else Vector(_source))
    if (committedsource) {
      val actualcapture = take(R.primaryTargetC(captured.target)).sourceCapture.get
      actualcapture.preStageGuard shouldBe selectedsource.preStageGuard
      actualcapture.postStageGuard shouldBe selectedsource.preStageGuard
      S.modeOf(actualcapture) shouldBe "committed"
    }
    _lines(scenario.root.resolve("dependency/all-sbt-invocations.txt")) shouldBe beforecalls
    _lines(scenario.root.resolve("primary-all-sbt-invocations.txt")) shouldBe beforeprimarycalls
    _refused(_resource_(R.recoveryOperation, oldanswer, "primary-stale-original-artifact-input"))
    _refused(_resource_(S.adoptionOperation, answer, "primary-repeated-combined-input"))
    _history(scenario, session) shouldBe capturehistory

    scenario.advance(task)
    val artifacthistory = _history(scenario, session)
    _history_extension(capturehistory, artifacthistory)
    val primaryoutput = _describe_()
    val primarydescription = primaryoutput.business[V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult]
    val primarytarget = GoalPhaseNativeMetadata.normalizePublicJson(json(primarydescription.target)).fold(fail(_), identity)
    primarytarget.hcursor.get[String]("phase") shouldBe Right("PrimaryDecision")
    _array(primarytarget, "priorDirections").size shouldBe 1
    val artifactfact = _array(primarytarget, "priorDirections").head
    artifactfact.hcursor.downField("target").get[String]("phase") shouldBe Right("ArtifactDecision")
    artifactfact.hcursor.get[String]("choice") shouldBe Right("AcceptExistingArtifactsWithUnknownEffects")
    artifactfact.hcursor.get[Json]("inheritedFrom") shouldBe Right(Json.obj("identity" -> reference.identity.asJson,
      "revision" -> reference.revision.asJson))
    _source_stages(scenario, session) shouldBe expectedstages
    _lines(scenario.root.resolve("dependency/all-sbt-invocations.txt")) shouldBe beforecalls
    _lines(scenario.root.resolve("primary-all-sbt-invocations.txt")) shouldBe beforeprimarycalls
    _lines(scenario.root.resolve("primary-publication-attempts.txt")).size shouldBe 1

    scenario.advance(task)
    val authorizedhistory = _history(scenario, session)
    _history_extension(artifacthistory, authorizedhistory)
    val facts = _primary_facts(scenario, session)
    facts.map(_.hcursor.downField("target").get[String]("phase").toOption.get) shouldBe
      Vector("ArtifactDecision", "PrimaryDecision")
    facts.map(_.hcursor.get[String]("choice").toOption.get) shouldBe
      Vector("AcceptExistingArtifactsWithUnknownEffects", "AuthorizePrimaryPublicationOnce")
    facts.foreach(fact => fact.hcursor.get[Json]("inheritedFrom") shouldBe artifactfact.hcursor.get[Json]("inheritedFrom"))
    facts.map(_.hcursor.get[Json]("reference").toOption.get).distinct.size shouldBe 2
    _lines(scenario.root.resolve("dependency/all-sbt-invocations.txt")) shouldBe beforecalls
    _lines(scenario.root.resolve("primary-all-sbt-invocations.txt")) shouldBe beforeprimarycalls
    _lines(scenario.root.resolve("primary-publication-attempts.txt")).size shouldBe 1

    When(if (repairprimary) "the next ordinary Advance uses the retained one-shot primary fact and reaches the separate primary Compile error"
      else "the next ordinary Advance uses the retained one-shot primary fact and performs real validation")
    val continuedoutput = scenario.advance(task)
    if (sourcecheckfailure) {
      val retainedcapture = take(R.primaryTargetC(captured.target)).sourceCapture.get
      Given("E11 the current dependency capture precedes a separate real primary Compile failure")
      val laterfailure = continuedoutput.business[V.GoalPhaseAdvanceResult]
      laterfailure.outcome shouldBe Some("failed")
      val laterpublication = GoalPhaseNativeMetadata.normalizePublicJson(json(laterfailure.localPublication.get))
        .flatMap(_.as[LocalPublication.Result].left.map(_.message)).fold(fail(_), identity)
      WorkflowSbtCapture.text(WorkflowSbtCapture.decode(laterpublication.process.get).fold(fail(_), identity).stdout)
        .value should include("missingPrimaryApi")
      When("ordinary primary repair and independently covered publication reach the genuine missing-document check failure")
      val failedchecks = _repair_primary_candidate(scenario, task, continuedoutput, original.repairProgress,
        take(R.primaryTargetC(captured.target)).generation, narrowauthority = true, retainedcapture = Some(retainedcapture),
        expectcheckfailure = true)
      val primaryfact = _primary_repair_fact(scenario, session)
      _adopt_failed_check_source(scenario, executable, task, failedchecks, retainedcapture, unknown)
      Then("E11 preserves original UNKNOWN, primary failures and accepted prefix after fresh qualification and independent Step closure")
      _publication_(unknown.unknown.request.input) shouldBe unknown.unknown
      _publication_(primaryfailed.request.input) shouldBe primaryfailed
      _publication_(laterpublication.request.input) shouldBe laterpublication
      _primary_repair_fact(scenario, session) shouldBe primaryfact
      _unknown_direction(scenario, session, unknown.frontierRevision) shouldBe olddirection
      _primary_facts(scenario, session) shouldBe facts
      _source_stages(scenario, session) shouldBe expectedstages
      _stages(scenario, session) shouldBe oldstages + 1
      _lines(scenario.root.resolve("primary-publication-attempts.txt")).size shouldBe expectedprimarypublications + 1
      _lines(scenario.root.resolve("dependency/publication-attempts.txt")) shouldBe oldpublications
      _lines(scenario.root.resolve("dependency/all-sbt-invocations.txt")) shouldBe oldcalls
      Files.readAllBytes(unknownoutput.invocation.stdout).toVector shouldBe unknownbytes
      Files.readAllBytes(failedoutput.invocation.stdout).toVector shouldBe failedbytes
      Files.readString(firstoutput.invocation.stdout) shouldBe firstbytes
      Files.readAllBytes(preserved).toVector shouldBe preservedbytes
      oldselected.get.foreach { case (path, bytes) => Files.readAllBytes(oldroot.resolve(path)).toVector shouldBe bytes }
      Files.readAllBytes(oldroot.resolve(".git/HEAD")).toVector shouldBe oldhead.get
      Files.readAllBytes(oldref.get._1).toVector shouldBe oldref.get._2
      return
    }
    val reviewoutput = if (!repairprimary) continuedoutput else {
      Given("E8 the completed committed source successor leaves a separate Compile error in the owned primary source")
      val retainedcapture = take(R.primaryTargetC(captured.target)).sourceCapture.get
      val capturebytes = retainedcapture.asJson.noSpaces
      val laterfailure = continuedoutput.business[V.GoalPhaseAdvanceResult]
      Then("the later failure belongs to the primary and preserves the original dependency UNKNOWN and successful capture")
      laterfailure.outcome shouldBe Some("failed")
      val laterpublication = GoalPhaseNativeMetadata.normalizePublicJson(json(laterfailure.localPublication.get))
        .flatMap(_.as[LocalPublication.Result].left.map(_.message)).fold(fail(_), identity)
      WorkflowSbtCapture.text(WorkflowSbtCapture.decode(laterpublication.process.get).fold(fail(_), identity).stdout)
        .value should include("missingPrimaryApi")
      _lines(scenario.root.resolve("primary-publication-attempts.txt")).size shouldBe 2
      val result = _repair_primary_candidate(scenario, task, continuedoutput, original.repairProgress,
        take(R.primaryTargetC(captured.target)).generation, narrowauthority = true, retainedcapture = Some(retainedcapture))
      Then("repair reuses the exact historical source capture without another dependency acquisition or publication")
      retainedcapture.asJson.noSpaces shouldBe capturebytes
      _publication_(unknown.unknown.request.input) shouldBe unknown.unknown
      _publication_(primaryfailed.request.input) shouldBe primaryfailed
      _publication_(laterpublication.request.input) shouldBe laterpublication
      _unknown_direction(scenario, session, unknown.frontierRevision) shouldBe olddirection
      Files.readAllBytes(unknownoutput.invocation.stdout).toVector shouldBe unknownbytes
      _source_stages(scenario, session) shouldBe expectedstages
      _lines(scenario.root.resolve("dependency/publication-attempts.txt")) shouldBe oldpublications
      _lines(scenario.root.resolve("dependency/all-sbt-invocations.txt")) shouldBe oldcalls
      result
    }
    val review = reviewoutput.business[V.GoalPhaseAdvanceResult]
    Then("one genuine primary successor succeeds, actual checks run, and required independent review remains pending")
    review.work.get.kind shouldBe "ReviewStep"
    if (repairprimary) {
      Then("E8 the repaired candidate leaves the original untracked sentinel unselected and unstaged")
      _primary_snapshot(scenario)._1.dirty.find(_.path == "docs/preserved.md") shouldBe preservedstatus
      review.work.get.review.get.changedPaths should not contain "docs/preserved.md"
    }
    review.sessionId shouldBe session
    review.work.get.completed.map(_.step) shouldBe Vector("step-1")
    GoalPhaseNativeMetadata.normalizePublicJson(json(review.work.get.selectedPlan.toRecord())).fold(fail(_), identity) shouldBe
      GoalPhaseNativeMetadata.normalizePublicJson(json(F.publicPlan(original.plan).toRecord())).fold(fail(_), identity)
    review.work.get.originalBase.head shouldBe original.base.head
    review.work.get.originalBase.tree shouldBe original.base.tree
    val expectedprogress = if (!repairprimary) original.repairProgress else original.repairProgress.copy(
      automaticCount = original.repairProgress.automaticCount + 1, ordinaryCount = original.repairProgress.ordinaryCount + 1)
    review.repairProgress shouldBe Some(GoalPhaseRepairProgress.publicSnapshot(expectedprogress))
    _capture(review) shouldBe first
    Files.readString(scenario.root.resolve("primary-real-check-passed.txt")) shouldBe "qualified-primary-api\n"
    _lines(scenario.root.resolve("primary-publication-attempts.txt")).size shouldBe expectedprimarypublications
    _lines(scenario.root.resolve("dependency/publication-attempts.txt")).size shouldBe firstpublications + 2
    _source_stages(scenario, session) shouldBe expectedstages
    _stages(scenario, session) shouldBe oldstages + 1
    val reviewhistory = _history(scenario, session)
    _history_extension(authorizedhistory, reviewhistory)
    val completedcalls = _lines(scenario.root.resolve("primary-all-sbt-invocations.txt"))
    val completeddependencycalls = _lines(scenario.root.resolve("dependency/all-sbt-invocations.txt"))
    review.validation.get.assessmentJournal.map(_.status) shouldBe Some("disabled")
    val recollected = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
    recollected.validation.get.assessmentJournal shouldBe None
    recollected shouldBe review.copy(validation = review.validation.map(_.copy(assessmentJournal = None)))
    _history(scenario, session) shouldBe reviewhistory
    _primary_facts(scenario, session) shouldBe facts
    _lines(scenario.root.resolve("primary-all-sbt-invocations.txt")) shouldBe completedcalls
    _lines(scenario.root.resolve("dependency/all-sbt-invocations.txt")) shouldBe completeddependencycalls
    _publication_(unknown.unknown.request.input) shouldBe unknown.unknown
    _publication_(primaryfailed.request.input) shouldBe primaryfailed
    _unknown_direction(scenario, session, unknown.frontierRevision) shouldBe olddirection
    Files.readAllBytes(unknownoutput.invocation.stdout).toVector shouldBe unknownbytes
    Files.readAllBytes(failedoutput.invocation.stdout).toVector shouldBe failedbytes
    Files.readString(firstoutput.invocation.stdout) shouldBe firstbytes
    Files.readAllBytes(preserved).toVector shouldBe preservedbytes

    if (historicalpolicy) {
      val retainedcapture = take(R.primaryTargetC(captured.target)).sourceCapture.get
      val capturebytes = retainedcapture.asJson.noSpaces
      val laterpublication = _primary_repair_fact(scenario, session).failure.failed
      _describe_primary_history_source(scenario, executable, task, reviewoutput, retainedcapture, unknown)
      Then("E10 retains every original failed and UNKNOWN publication, capture, snapshot and invocation log after description")
      retainedcapture.asJson.noSpaces shouldBe capturebytes
      _publication_(unknown.unknown.request.input) shouldBe unknown.unknown
      _publication_(primaryfailed.request.input) shouldBe primaryfailed
      _publication_(laterpublication.request.input) shouldBe laterpublication
      _unknown_direction(scenario, session, unknown.frontierRevision) shouldBe olddirection
      _primary_facts(scenario, session) shouldBe facts
      _source_stages(scenario, session) shouldBe expectedstages
      _stages(scenario, session) shouldBe oldstages + 1
      _lines(scenario.root.resolve("primary-all-sbt-invocations.txt")) shouldBe completedcalls
      _lines(scenario.root.resolve("dependency/all-sbt-invocations.txt")) shouldBe completeddependencycalls
      _lines(scenario.root.resolve("primary-publication-attempts.txt")).size shouldBe expectedprimarypublications
      _lines(scenario.root.resolve("dependency/publication-attempts.txt")) shouldBe oldpublications
      Files.readAllBytes(unknownoutput.invocation.stdout).toVector shouldBe unknownbytes
      Files.readAllBytes(failedoutput.invocation.stdout).toVector shouldBe failedbytes
      Files.readString(firstoutput.invocation.stdout) shouldBe firstbytes
      Files.readAllBytes(preserved).toVector shouldBe preservedbytes
      return
    }

    When("the independent fixture reviewer supplies its actual answer and ordinary authority closes the Step")
    val beforecommit = scenario.candidateHead
    val closed = _close_step(scenario, task, reviewoutput, "primary-second").business[V.GoalPhaseAdvanceResult]
    Then("normal native closure retains a real second Step commit and both distinct ordered operation facts")
    closed.work.get.completed.map(_.step) shouldBe Vector("step-1", "step-2")
    closed.work.get.kind shouldBe "ImplementStep"
    closed.work.get.stepIdentity shouldBe Some("step-3")
    closed.work.get.completed.last.review shouldBe Some("fixture-primary-second-step-review")
    val aftercommit = scenario.candidateHead
    aftercommit should not be beforecommit
    closed.work.get.completed.last.commit.identity shouldBe aftercommit
    if (repairprimary) {
      Then("E8 the real Step commit retains the sentinel's exact original untracked dirty status")
      _primary_snapshot(scenario)._1.dirty.find(_.path == "docs/preserved.md") shouldBe preservedstatus
    }
    _history_extension(reviewhistory, _history(scenario, session))
    _primary_facts(scenario, session) shouldBe facts
    _lines(scenario.root.resolve("primary-publication-attempts.txt")).size shouldBe expectedprimarypublications
    _lines(scenario.root.resolve("dependency/publication-attempts.txt")).size shouldBe firstpublications + 2
    _publication_(unknown.unknown.request.input) shouldBe unknown.unknown
    _publication_(primaryfailed.request.input) shouldBe primaryfailed
    Files.readAllBytes(preserved).toVector shouldBe preservedbytes
    val closedhistory = _history(scenario, session)
    val closedcalls = _lines(scenario.root.resolve("primary-all-sbt-invocations.txt"))
    closed.message shouldBe closed.work.get.purpose + " Commit hygiene mode=auto-fix; clean: Selected index is clean."
    closed.validation.get.outcome shouldBe Some("passed")
    val recollectedclosed = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
    recollectedclosed.message shouldBe closed.work.get.purpose
    recollectedclosed.validation shouldBe None
    recollectedclosed shouldBe closed.copy(message = closed.work.get.purpose, validation = None)
    _history(scenario, session) shouldBe closedhistory
    _lines(scenario.root.resolve("primary-all-sbt-invocations.txt")) shouldBe closedcalls
    _lines(scenario.root.resolve("primary-publication-attempts.txt")).size shouldBe expectedprimarypublications
    if (committedsource) {
      Then("E6 the new source remains committed while the original source, index, branch and publication log remain exact")
      oldselected.get.foreach { case (path, bytes) => Files.readAllBytes(oldroot.resolve(path)).toVector shouldBe bytes }
      Files.readAllBytes(oldroot.resolve(".git/HEAD")).toVector shouldBe oldhead.get
      Files.readAllBytes(oldref.get._1).toVector shouldBe oldref.get._2
      new String(oldref.get._2.toArray, java.nio.charset.StandardCharsets.UTF_8).trim shouldBe oldguard.get.head
      take(_run_setup_command_(Vector("--no-optional-locks", "-C", oldbinding.root,
        "diff", "--cached", "--exit-code", oldguard.get.indexTree, "--")))
      _lines(scenario.root.resolve("dependency/publication-attempts.txt")) shouldBe oldpublications
      _lines(scenario.root.resolve("dependency/all-sbt-invocations.txt")) shouldBe oldcalls
      _lines(scenario.root.resolve("dependency-successor/publication-attempts.txt")) shouldBe empty
      _lines(scenario.root.resolve("dependency-successor/all-sbt-invocations.txt")) should not be empty
      val finalguard = _native_fixture(scenario)(S.observeC(_, replacement.get, Vector.empty))
      finalguard shouldBe selectedsource.preStageGuard
      finalguard.dirty shouldBe empty
    }
  }

  private def _unknown_continuation(scenario: F.Scenario, executable: Path, task: Path, session: String,
    publicationtarget: LocalPublication.Target, original: R.Target, first: D.Capture,
    firstoutput: F.Result, firstbytes: String, preserved: Path, preservedbytes: Vector[Byte],
    oldstages: Int, firstpublications: Int, mode: String): Unit = {
    val observed = mode == "qualified"
    val artifacts = mode == "existing-artifacts"
    Given("M3-UNKNOWN323-NATIVE: retained prerequisite correction and a genuinely exited writer whose native terminal receipt was interrupted")
    _interrupt_capture(scenario, session)
    val process = _leave_unknown(scenario, session, original)
    WorkflowSbtCapture.exitCode(process) shouldBe Some(0)
    val unknownoutput = scenario.advance(task)
    val unknown = unknownoutput.business[V.GoalPhaseAdvanceResult]
    unknown.outcome shouldBe Some("uncertain")
    _stages(scenario, session) shouldBe oldstages + 1
    _source_stages(scenario, session) shouldBe 0
    _lines(scenario.root.resolve("dependency/publication-attempts.txt")).size shouldBe firstpublications + 2
    val retainedunknown = GoalPhaseNativeMetadata.normalizePublicJson(json(unknown.localPublication.get))
      .flatMap(_.as[LocalPublication.Result].left.map(_.message)).fold(fail(_), identity)
    retainedunknown.status shouldBe "uncertain"
    retainedunknown.request.input.requestIdentity should endWith(":prerequisite:0")
    val originalunknownbytes = Files.readAllBytes(unknownoutput.invocation.stdout).toVector
    val initialcalls = _lines(scenario.root.resolve("dependency/all-sbt-invocations.txt"))
    def _describe_(): F.Result = scenario.call("command", R.describeOperation, Vector("--sessionId", session))
    def _recover_(input: V.GoalPhasePrerequisitePublicationRecoveryInput): F.Result =
      scenario.call("command", R.recoveryOperation, Vector("--input", json(input.toRecord()).noSpaces))

    When("the producer gains the missing API while the real old request remains UNKNOWN")
    Files.writeString(Path.of(original.admission.binding.root).resolve(_source),
      "class RecoveryMarker { def value: String = \"approved-correction\"; def correctedApi: String = \"qualified-successor-api\" }\n")
    val blockedoutput = _describe_()
    val blocked = blockedoutput.business[V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult]
    val before = take(U.targetC(blocked.target))
    val reference = V.WorkflowArtifactReference("fixture-unknown-successor-human", "1")
    val publicationchoice = if (observed) "AuthorizeQualifiedSuccessorPublication" else "AuthorizeNewPublicationWithUnknownEffects"
    val unproved = V.GoalPhasePrerequisitePublicationRecoveryInput(session, blocked.target, publicationchoice,
      "SPECIFICATION-OWNED accept unknown old publication effects and authorize one new publication; no old exit or qualification is proved", reference)
    Then("description offers explicit Human uncertainty choices without fabricating lifecycle or qualification")
    blocked.outcome shouldBe "direction-required"
    blocked.choices shouldBe U.choicesFor(before)
    blocked.choices should contain allOf ("AcceptExistingArtifactsWithUnknownEffects", "AuthorizeNewPublicationWithUnknownEffects", "Defer")
    blocked.choices should not contain "AuthorizeQualifiedSuccessorPublication"
    before.processPolicy.getBytes(java.nio.charset.StandardCharsets.UTF_8).length should be > 65536
    before.processPolicy shouldBe original.processPolicy
    val declaredplan = parse(parse(Files.readString(scenario.root.resolve(".textus/config.json"))).toOption.get.hcursor
      .get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
    val resolvedpolicy = parse(before.processPolicy).toOption.get
    io.circe.Printer.noSpaces.copy(sortKeys = true).print(resolvedpolicy) shouldBe before.processPolicy
    resolvedpolicy.hcursor.get[Json]("processPlan") shouldBe Right(declaredplan)
    before.observation shouldBe None
    before.unknown.request shouldBe retainedunknown.request
    before.unknown.status shouldBe retainedunknown.status
    before.unknown.process shouldBe retainedunknown.process
    before.unknown.nextPreparation shouldBe retainedunknown.nextPreparation
    before.retainedGuard should not be empty
    before.retainedGuard.get.selectedFiles should not be before.guard.selectedFiles
    before.ancestors.map(_.capture) shouldBe Vector(first)
    before.requestingResult.identity shouldBe "fixture-second-source-completion"
    before.completed.map(_.step) shouldBe Vector("step-1")
    val initialhistory = _history(scenario, session)
    _refused(_recover_(unproved))
    _recover_(unproved.copy(choice = "Defer", reference = V.WorkflowArtifactReference("fixture-unknown-defer", "1")))
      .business[V.GoalPhasePrerequisitePublicationRecoveryResult].outcome shouldBe "deferred"
    _lines(scenario.root.resolve("dependency/all-sbt-invocations.txt")) shouldBe initialcalls
    _source_stages(scenario, session) shouldBe 0
    _history(scenario, session) shouldBe initialhistory

    Given(if (observed) "an independently installed actual writer observation and declared source-only consumption"
      else "a declared source-only consumption with no provisioned writer lifecycle or qualification")
    val proposal = _source_selection(scenario, before)
    val sourceinput = V.GoalPhaseDependencySourceRevisionDescriptionInput(session, GoalPhaseNativeMetadata.record(proposal.asJson))
    val unobservedsource = scenario.call("command", S.describeOperation, Vector("--input", json(sourceinput.toRecord()).noSpaces))
    val unobservedorigin = take(S.targetC(unobservedsource.business[V.GoalPhaseDependencySourceRevisionDescriptionResult].target)).waitOrigin.get
    unobservedorigin.guard shouldBe before.retainedGuard.get
    unobservedorigin.observation shouldBe None
    if (observed) _observation(scenario, unobservedsource, unobservedorigin, process, None, "writer-lifecycle")
    val sourceoutput = if (observed) scenario.call("command", S.describeOperation,
      Vector("--input", json(sourceinput.toRecord()).noSpaces)) else unobservedsource
    val sourcedescription = sourceoutput.business[V.GoalPhaseDependencySourceRevisionDescriptionResult]
    val sourcetarget = take(S.targetC(sourcedescription.target))
    Then("the public Wait source target names the actual request and old retained guard without a fabricated question")
    sourcetarget.schemaVersion shouldBe S.waitSchema
    sourcetarget.questionIdentity shouldBe empty
    sourcetarget.waitOrigin.get.unknown shouldBe before.unknown
    sourcetarget.waitOrigin.get.observation.nonEmpty shouldBe observed
    sourcetarget.waitOrigin.get.observation.flatMap(_.qualification) shouldBe None
    U.qualified(sourcetarget.waitOrigin.get) shouldBe false
    sourcetarget.waitOrigin.get.guard shouldBe before.retainedGuard.get
    sourcetarget.predecessorGuard shouldBe before.retainedGuard.get
    sourcetarget.preStageGuard shouldBe before.guard
    sourcetarget.plan shouldBe original.plan
    sourcetarget.primaryBase shouldBe original.base
    sourcetarget.completed shouldBe original.completed
    sourcetarget.repairProgress shouldBe original.repairProgress
    val sourcereference = V.WorkflowArtifactReference("fixture-wait-source-human", "1")
    sourcedescription.choices shouldBe S.choicesFor(sourcetarget)
    val sourcechoice = if (observed) "AdoptDependencySourceRevision" else "AdoptDependencySourceRevisionWithUnknownEffects"
    sourcedescription.choices should contain(sourcechoice)
    val sourceanswer = V.GoalPhaseDependencySourceRevisionAdoptionInput(session, sourcedescription.target, sourcechoice,
      "SPECIFICATION-OWNED source-only adoption accepting unknown old effects; publication and validation are separately authorized", sourcereference)
    def _adopt_(input: V.GoalPhaseDependencySourceRevisionAdoptionInput) = scenario.call("command", S.adoptionOperation,
      Vector("--input", json(input.toRecord()).noSpaces))
    _refused(_adopt_(sourceanswer))
    _provision(scenario, sourceoutput, "dependency-source-revision", sourceanswer, sourcereference, "wait-source-human")
    val beforesource = _history(scenario, session)
    When("the independently provisioned exact Human source answer is admitted through the public operation")
    _refused(_adopt_(sourceanswer.copy(sessionId = session + "-crossed")))
    _refused(_adopt_(sourceanswer.copy(choice = if (observed) "AdoptDependencySourceRevisionWithUnknownEffects"
      else "AdoptDependencySourceRevision")))
    _refused(_adopt_(sourceanswer.copy(target = S.targetRecord(sourcetarget.copy(generation = sourcetarget.generation + 1)))))
    _adopt_(sourceanswer).business[V.GoalPhaseDependencySourceRevisionAdoptionResult].accepted shouldBe true
    Then("adoption appends its Direction while every publication, stage and check count stays unchanged")
    _lines(scenario.root.resolve("dependency/all-sbt-invocations.txt")) shouldBe initialcalls
    _source_stages(scenario, session) shouldBe 0
    val aftersource = _history(scenario, session)
    val sourcehistory = _array(parse(aftersource).toOption.get, "history")
    sourcehistory.dropRight(1) shouldBe _array(parse(beforesource).toOption.get, "history")
    sourcehistory.last.hcursor.get[String]("causation") shouldBe Right(U.sourceCausationPrefix + before.frontierRevision)
    _refused(_adopt_(sourceanswer))
    When("a separate ordinary Advance captures the new dependency source")
    val captureoutput = scenario.advance(task)
    val capturewait = captureoutput.business[V.GoalPhaseAdvanceResult]
    val capturecalls = _lines(scenario.root.resolve("dependency/all-sbt-invocations.txt"))
    val afteroutput = _describe_()
    val afterdescription = afteroutput.business[V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult]
    val after = take(U.targetC(afterdescription.target))
    val capture = after.candidate.dependencySourceCapture.get
    Then("only the new unstaged source path is staged, accounting and accepted semantics remain exact, and UNKNOWN still gates publication")
    capturewait.outcome shouldBe Some("uncertain")
    capturecalls shouldBe initialcalls
    _source_stages(scenario, session) shouldBe 1
    _stages(scenario, session) shouldBe oldstages + 1
    capture.stagedPaths shouldBe Vector(_source)
    capture.generation shouldBe before.generation + 1
    after.generation shouldBe before.generation + 1
    after.plan shouldBe before.plan
    after.base shouldBe before.base
    after.completed shouldBe before.completed
    after.repairProgress shouldBe before.repairProgress
    after.requestingResult shouldBe before.requestingResult
    after.candidate.candidate shouldBe before.candidate.candidate
    after.candidate.selectedPaths shouldBe before.candidate.selectedPaths
    after.candidate.stagedPaths shouldBe before.candidate.stagedPaths
    after.candidate.changedPaths shouldBe before.candidate.changedPaths
    after.candidate.diff shouldBe before.candidate.diff
    after.candidate.hygiene shouldBe before.candidate.hygiene
    val sourcedirection = after.candidate.dependencySourceDirection.get
    sourcedirection.choice shouldBe Option.when(!observed)(sourcechoice)
    U.sourceCodec.decode(U.sourceCodec.encode(sourcedirection)) shouldBe Right(sourcedirection)
    U.sourceCodec.encode(sourcedirection).hcursor.downField("payload").downField("choice").focus shouldBe
      Option.when(!observed)(sourcechoice.asJson)
    after.observation shouldBe None
    after.unknown shouldBe before.unknown
    Files.readAllBytes(preserved).toVector shouldBe preservedbytes
    Files.readString(firstoutput.invocation.stdout) shouldBe firstbytes
    _refused(_recover_(unproved))
    _refused(_adopt_(sourceanswer))
    scenario.advance(task).business[V.GoalPhaseAdvanceResult] shouldBe capturewait
    _source_stages(scenario, session) shouldBe 1
    _lines(scenario.root.resolve("dependency/all-sbt-invocations.txt")) shouldBe capturecalls

    Given(if (observed) "a separate independent qualification of the exact captured source and selected programs"
      else "the captured source with old lifecycle and qualification still absent and a separate actual Human answer")
    if (observed) _observation(scenario, afteroutput, after, process,
      Some(proposal.consumption.asInstanceOf[S.Consumption]), "qualified-capture")
    val qualifiedoutput = if (observed) _describe_() else afteroutput
    val qualifieddescription = qualifiedoutput.business[V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult]
    val qualified = take(U.targetC(qualifieddescription.target))
    qualifieddescription.outcome shouldBe "direction-required"
    U.qualified(qualified) shouldBe observed
    qualified.observation.nonEmpty shouldBe observed
    qualifieddescription.choices shouldBe U.choicesFor(qualified)
    val successorrationale = ((if (observed) "独立した後続公開の判断理由"
      else "Unknown old effects accepted by Human; this is no writer exit or qualification proof. ") * 1800).trim
    successorrationale.getBytes(java.nio.charset.StandardCharsets.UTF_8).length should be > 65536
    val selectedchoice = if (artifacts) "AcceptExistingArtifactsWithUnknownEffects" else publicationchoice
    val successor = unproved.copy(target = qualifieddescription.target, choice = selectedchoice, rationale = successorrationale)
    _refused(_recover_(successor))
    _provision(scenario, qualifiedoutput, R.purpose, successor, reference, "qualified-successor-human")
    val beforedirection = _history(scenario, session)
    When("crossed answers are refused and the exact independent Human Direction is admitted")
    _refused(_recover_(successor.copy(sessionId = session + "-crossed")))
    _refused(_recover_(successor.copy(choice = if (artifacts) "AuthorizeNewPublicationWithUnknownEffects"
      else "AcceptExistingArtifactsWithUnknownEffects")))
    _refused(_recover_(successor.copy(target = U.targetRecord(qualified.copy(generation = qualified.generation + 1)))))
    val successoroutput = _recover_(successor)
    val successorresult = successoroutput.business[V.GoalPhasePrerequisitePublicationRecoveryResult]
    Then("the exact full Direction rationale and binding are retained without publication, while stale and repeated Directions refuse")
    successorresult.accepted shouldBe true
    successorresult.outcome shouldBe (if (observed) "qualified-successor-authorized"
      else if (artifacts) "existing-artifacts-accepted-with-unknown-effects" else "new-publication-authorized-with-unknown-effects")
    val successorbytes = Files.readAllBytes(successoroutput.invocation.stdout).toVector
    val retaineddirection = _unknown_direction(scenario, session, qualified.frontierRevision)
    retaineddirection._2 shouldBe U.Direction(qualified, ContextReference(reference.identity, reference.revision),
      successorrationale, Option.when(!observed)(selectedchoice))
    U.codec.decode(U.codec.encode(retaineddirection._2)) shouldBe Right(retaineddirection._2)
    U.codec.encode(retaineddirection._2).hcursor.downField("payload").downField("choice").focus shouldBe
      Option.when(!observed)(selectedchoice.asJson)
    val afterdirection = _history(scenario, session)
    val directionhistory = _array(parse(afterdirection).toOption.get, "history")
    directionhistory.dropRight(1) shouldBe _array(parse(beforedirection).toOption.get, "history")
    directionhistory.last.hcursor.get[String]("causation") shouldBe Right(U.causationPrefix + qualified.frontierRevision)
    take(U.bindingC(qualified.componentInstance, retaineddirection._2.target, successor.choice, retaineddirection._2.rationale,
      qualified.participant, retaineddirection._2.reference)) shouldBe take(U.bindingC(qualified.componentInstance, qualified,
      successor.choice, successorrationale, qualified.participant, ContextReference(reference.identity, reference.revision)))
    _lines(scenario.root.resolve("dependency/all-sbt-invocations.txt")) shouldBe capturecalls
    _refused(_recover_(successor))
    _refused(_recover_(unproved))
    When(if (artifacts) "normal installed Advance accepts existing artifacts and performs ordinary consumer checks"
      else "normal installed Advance performs the one authorized successor publication and ordinary consumer checks")
    val reviewed = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
    val completedcalls = _lines(scenario.root.resolve("dependency/all-sbt-invocations.txt"))
    Then(if (artifacts) "the real consumer check fails on the unpublished API without a fabricated publication success"
      else "one real successor publishes and the actual primary consumer Test calls the corrected API")
    reviewed.work.get.kind shouldBe (if (artifacts) "RepairStep" else "ReviewStep")
    reviewed.sessionId shouldBe session
    reviewed.work.get.completed.map(_.step) shouldBe Vector("step-1")
    reviewed.work.get.originalBase.head shouldBe before.base.head
    reviewed.work.get.originalBase.tree shouldBe before.base.tree
    if (artifacts) {
      val failure = reviewed.work.get.checkFailure.get
      failure.validation.outcome shouldBe Some("repair-required")
      failure.validation.checks.last.exitCode shouldBe Some(1)
      failure.validation.checks.last.stdout should include("When the ordinary consumer Test calls the corrected producer API")
      Files.exists(scenario.root.resolve("consumer-corrected-api.txt")) shouldBe false
    } else {
      reviewed.repairProgress shouldBe Some(GoalPhaseRepairProgress.publicSnapshot(before.repairProgress))
      Files.readString(scenario.root.resolve("consumer-corrected-api.txt")) shouldBe "qualified-successor-api\n"
    }
    val expectedpublications = firstpublications + (if (artifacts) 2 else 3)
    _lines(scenario.root.resolve("dependency/publication-attempts.txt")).size shouldBe expectedpublications
    _source_stages(scenario, session) shouldBe 1
    _stages(scenario, session) shouldBe oldstages + 1
    val assembly = Some(scenario.root.resolve(".textus/assembly.yaml"))
    val collected = scenario.callInstalled(executable, "LocalPublicationService", "collectLocalPublication",
      Vector("--input", retainedunknown.request.input.asJson.mapObject(_.remove("answer")).noSpaces), assemblyDescriptor = assembly)
      .business[V.LocalPublicationResult]
    collected.status shouldBe "uncertain"
    val originalcollection = GoalPhaseNativeMetadata.normalizePublicJson(json(collected.publication.get))
      .flatMap(_.as[LocalPublication.Result].left.map(_.message)).fold(fail(_), identity)
    originalcollection shouldBe before.unknown
    val latest = scenario.callInstalled(executable, "LocalPublicationService", "collectLatestLocalPublication",
      Vector("--input", json(V.LocalPublicationLatestCollectionInput(publicationtarget.identity, session).toRecord()).noSpaces),
      assemblyDescriptor = assembly).business[V.LocalPublicationResult]
    latest.status shouldBe (if (artifacts) "uncertain" else "published")
    val saved = GoalPhaseNativeMetadata.normalizePublicJson(json(latest.publication.get))
      .flatMap(_.as[LocalPublication.Result].left.map(_.message)).fold(fail(_), identity)
    if (artifacts) saved shouldBe before.unknown
    else {
      saved.request.input.requestIdentity should include(":qualified:")
      saved.request.input.requestIdentity should not be retainedunknown.request.input.requestIdentity
    }
    Files.readAllBytes(unknownoutput.invocation.stdout).toVector shouldBe originalunknownbytes
    When("the installed caller repeats Advance after the completed successor")
    scenario.advance(task).business[V.GoalPhaseAdvanceResult] shouldBe reviewed
    Then("the native stage prefix, publication and checks are recollected without another effect")
    _source_stages(scenario, session) shouldBe 1
    _lines(scenario.root.resolve("dependency/all-sbt-invocations.txt")) shouldBe completedcalls
    _lines(scenario.root.resolve("dependency/publication-attempts.txt")).size shouldBe expectedpublications
    Files.readAllBytes(preserved).toVector shouldBe preservedbytes
    Files.readAllBytes(successoroutput.invocation.stdout).toVector shouldBe successorbytes
    _unknown_direction(scenario, session, qualified.frontierRevision) shouldBe retaineddirection
  }

  "Installed Session-only prerequisite description" should {
    "E12 reach business admission with bare sessionId while retaining explicit credential rejection" in {
      Given("an explicitly installed command, its normal component file and a fresh fixed-subject host without native Start")
      val executable = Path.of(sys.props.getOrElse("smworkflow.spec.installedCommand",
        cancel("Explicitly select smworkflow.spec.installedCommand for this installed-only scenario")))
      val componentfile = Path.of(sys.props.getOrElse("smworkflow.spec.componentFile",
        cancel("Explicitly select smworkflow.spec.componentFile for this installed-only scenario")))
      executable.isAbsolute shouldBe true
      componentfile.isAbsolute shouldBe true
      Files.isRegularFile(componentfile) shouldBe true
      sys.props.get("smworkflow.spec.installations") shouldBe None
      val scenario = F.fresh("session-only-prerequisite-description", bootstrapSelectedInput = false)
      val configfile = scenario.root.resolve(".textus/config.json")
      val config = parse(Files.readString(configfile)).toOption.get
      Files.writeString(configfile, config.mapObject(_.add("textus.component.file", componentfile.toString.asJson)).noSpaces)
      val session = "description-absent-session"
      def _describe_(parameters: Vector[String]): F.Result = scenario.callInstalled(executable,
        "GoalPhaseService", R.describeOperation, parameters,
        assemblyDescriptor = Some(scenario.root.resolve(".textus/assembly.yaml")))
      def _response_(result: F.Result): String =
        Files.readString(result.invocation.stdout) + "\n" + Files.readString(result.invocation.stderr)

      When("the installed public description receives its declared bare Session business parameter")
      val described = _describe_(Vector("--sessionId", session))
      Then("the owning business admission reports the absent Start without a provider denial")
      withClue(described.invocation.logs) {
        described.exitCode should not be 0
        _response_(described) should include ("no actual native ordered Start")
        _response_(described) should not include ("403")
        _response_(described) should not include ("requires a service authentication provider")
      }

      When("the installed description receives missing Session input or the old input-only wrapper")
      val missing = _describe_(Vector.empty)
      val oldinput = _describe_(Vector("--input",
        json(V.GoalPhasePrerequisitePublicationRecoveryDescriptionInput(session).toRecord()).noSpaces))
      Then("both invalid requests refuse before business Start admission without acting as aliases")
      Vector(missing, oldinput).foreach { refused =>
        withClue(refused.invocation.logs) {
          refused.exitCode should not be 0
          _response_(refused) should not include ("no actual native ordered Start")
          _response_(refused) should not include ("403")
          _response_(refused) should not include ("requires a service authentication provider")
        }
      }

      When("an explicit authorization credential accompanies the declared Session business parameter")
      val credential = _describe_(Vector("--sessionId", session,
        "--authorization", "Bearer specification-owned-invalid-credential"))
      Then("the fixed-subject provider still rejects real credential material with its existing 403")
      withClue(credential.invocation.logs) {
        credential.exitCode should not be 0
        _response_(credential) should include ("403")
        _response_(credential) should include ("Fixed user profile requires a service authentication provider")
        _response_(credential) should not include ("no actual native ordered Start")
      }
    }
  }

  "Installed ordinary prerequisite publication recovery" should {
    Vector(false, true).foreach { broadselectors =>
    val example = if (broadselectors) "E9" else "E7"
    val title = if (broadselectors)
      "E9 refine broad owned selectors to the corrective primary source and document while preserving excluded dirt"
    else "E7 automatically repair a covered primary compile failure through ordinary publication and Step closure"
    title must afterWord(
      s"in spec:goal-phase-prerequisite-publication-recovery, example:$example, rules:ordinary-repair,publication-authority,no-replay" +
        (if (broadselectors) ",R6" else "") + ", phase:6.10") {
      "retain the genuine failed publication and close the corrected candidate through independent review" in {
        Given("an installed CLI and a tiny ordinary project whose host covers primary publication, Compile, TestOnly and independent Step review")
        if (broadselectors) Given("docs/spec/goal-phase-prerequisite-publication-recovery.md R6 E9: broad src/docs scope retains an originally unselected sentinel")
        val executable = Path.of(sys.props.getOrElse("smworkflow.spec.installedCommand",
          cancel("Explicitly select smworkflow.spec.installedCommand for this installed-only scenario")))
        executable.isAbsolute shouldBe true
        sys.props.get("smworkflow.spec.installations") shouldBe None
        val scenario = F.fresh(if (broadselectors) "primary-publication-exact-refinement" else "primary-publication-ordinary-repair",
          sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable),
          repairContinuation = true)
        scenario.host.goal.dependencyValidation shouldBe None
        _prepare_primary_project(scenario, publicationsentinel = broadselectors)
        val paths = if (broadselectors) Vector("value.txt", "docs", "src/main/scala", "src/test/scala")
          else Vector("value.txt", "docs/phase.md", _primary_source, _consumer)
        val file = scenario.root.resolve(".textus/config.json")
        val config = parse(Files.readString(file)).toOption.get
        val current = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(
          parse(config.hcursor.get[String]("sm-workflow.runtime.definition").toOption.get).toOption.get))
        val host = current.copy(goal = current.goal.copy(candidatePaths = paths,
          stepValidation = current.goal.stepValidation.copy(checks = _primary_checks),
          phaseValidation = current.goal.phaseValidation.copy(checks = _primary_checks)))
        val plan = scenario.plan.copy(steps = Vector(
          scenario.plan.steps.head.copy(ownedPaths = paths, checks = _primary_checks.map(_.identity),
            actionPolicy = Some(GoalPhaseStepAssessment.Policy("fixture-required-independent-review", "code", "required",
              "off", "off", Vector.empty))),
          scenario.plan.steps.last.copy(purpose = "Retain the sentinel next Step after the repaired primary Step closes",
            checks = _primary_checks.map(_.identity))), phaseChecks = _primary_checks.map(_.identity))
        Files.writeString(scenario.root.resolve("goal-plan.json"), plan.asJson.noSpaces)
        val target = RepositoryGitRuntimePrograms.Target(Path.of(host.target.root), host.target.localBranch,
          host.target.remote, host.target.remoteBranch, host.target.endpoint)
        val git = TypedGitHostPlanFixture.plan(target, kinds = TypedGitHostPlanFixture.observations ++ Vector(
          GitOperationKind.IndexTree, GitOperationKind.PendingPaths, GitOperationKind.PendingDiff, GitOperationKind.CommitMetadata),
          goalPaths = paths, goalMessages = Vector("Complete selected continuation Step 1", "Complete selected continuation Step 2"),
          goalSelections = Vector(paths, Vector("docs/phase.md")))
        val checks = Vector(
          TypedSbtHostPlanFixture.plan(scenario.root, TypedSbtHostPlanFixture.normalExecutable,
            Vector(_primary_checks.head.ordinal), operation = SbtOperation.Compile),
          TypedSbtHostPlanFixture.plan(scenario.root, TypedSbtHostPlanFixture.normalExecutable,
            Vector(_primary_checks.last.ordinal), operation = SbtOperation.TestOnly, testClasses = Vector(_primary_suite)))
        val publication = _array(TypedSbtHostPlanFixture.plan(scenario.root,
          TypedSbtHostPlanFixture.normalExecutable, Vector(5), operation = SbtOperation.PublishLocal), "sbtPrograms").map(
          _.mapObject(_.add("target", TypedGitHostPlanFixture.workspace.asJson)
            .add("identity", "fixture-primary-publication".asJson).add("capability", "fixture-primary-publication".asJson)))
        val sbt = checks.flatMap(_array(_, "sbtPrograms")) ++ publication
        val process = git.mapObject(_.add("sbtPrograms", sbt.asJson)
          .add("workspaces", (_array(git, "workspaces") ++ checks.flatMap(_array(_, "workspaces"))).asJson)
          .add("workspaceGrants", (_array(git, "workspaceGrants") ++ checks.flatMap(_array(_, "workspaceGrants"))).asJson)
          .add("grants", Json.arr(Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson,
            "capabilities" -> (_array(git, "gitPrograms") ++ sbt).map(_.hcursor.get[String]("capability").toOption.get).distinct.asJson))))
        if (broadselectors) {
          Then("E9 the declared host uses its existing fixed selected-directory programs without a StageFullState program or grant")
          _array(process, "gitPrograms").map(_.hcursor.get[String]("operation").toOption.get) should not contain
            GitOperationKind.StageFullState.wireName
          _array(process, "grants").flatMap(_array(_, "capabilities")).flatMap(_.asString) should not contain
            TypedGitHostPlanFixture.capability(GitOperationKind.StageFullState)
        }
        val storage = parse(config.hcursor.get[String]("textus.subsystem.storage.plan").toOption.get).toOption.get
        val collection = ThreeWorkflowNormalHostFixture.collection("prerequisite_publications")
        val publicationtarget = LocalPublication.Target("fixture-primary-publication", "org.example.PrimaryProduct",
          TypedGitHostPlanFixture.workspace, None, beforeChecks = true)
        val selectedstorage = storage.mapObject(_.add("records", (_array(storage, "records") :+
          Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson, "storeName" -> "workflow".asJson,
            "collection" -> collection.asJson)).asJson))
        val policy = Json.obj("storeName" -> "workflow".asJson, "collection" -> collection.asJson,
          "targets" -> Vector(publicationtarget).asJson)
        val selected = config.mapObject(_.add("sm-workflow.runtime.definition", ThreeWorkflowRuntimeParameters.encode(host).noSpaces.asJson)
          .add("textus.assembly.descriptor", scenario.root.resolve(".textus/assembly.yaml").toString.asJson)
          .add("textus.subsystem.process-execution.plan", process.noSpaces.asJson)
          .add("textus.subsystem.storage.plan", selectedstorage.noSpaces.asJson)
          .add(LocalPublicationRuntime.declaration.name, policy.noSpaces.asJson)
          .add(WorkflowMechanicalReceiptParameters.maxBytes.name, _receipt_limits.maxBytes.toString.asJson))
        val component = sys.props.get("smworkflow.spec.componentFile").fold(selected)(value =>
          selected.mapObject(_.add("textus.component.file", value.asJson)))
        Files.writeString(file, component.noSpaces)
        val prepared = scenario.prepareHostInput("prepareGoalStart", scenario.selectedGoalPreparationInput.toRecord())
        prepared.exitCode shouldBe 0
        scenario.selectPreparedImport(prepared)
        val task = scenario.ownTask("ordinary-primary-repair")
        val started = scenario.start().business[V.GoalPhaseStartResult]
        scenario.retain(task, started)
        val initial = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
        val initialrequest = _issued_request(scenario, started.sessionId)
        initial.work.get.kind shouldBe "ImplementStep"
        _write_primary_product(scenario)
        Files.writeString(Path.of(host.target.root).resolve(_primary_source),
          "package fixture\nobject PrimaryProduct { def value: String = missingPrimaryApi }\n")
        Files.writeString(Path.of(host.target.root).resolve("value.txt"), "expected\n")
        _submit(scenario, initial, Json.obj("outcome" -> "completed".asJson,
          "summary" -> "SPECIFICATION-OWNED initial primary candidate with a deliberate Compile error".asJson,
          "findings" -> Json.arr()), "fixture-ordinary-primary-initial-completion")
        When("ordinary Advance captures the initial primary candidate and really attempts its beforeChecks publication")
        val failedoutput = scenario.advance(task)
        val failed = failedoutput.business[V.GoalPhaseAdvanceResult]
        Then("one actual failed Compile is retained and the selected checks have not run")
        failed.outcome shouldBe Some("failed")
        _lines(scenario.root.resolve("primary-publication-attempts.txt")).size shouldBe 1
        val failedpublication = GoalPhaseNativeMetadata.normalizePublicJson(json(failed.localPublication.get))
          .flatMap(_.as[LocalPublication.Result].left.map(_.message)).fold(fail(_), identity)
        val failedbytes = Files.readAllBytes(failedoutput.invocation.stdout).toVector
        val sentinel = Path.of(host.target.root).resolve(_primary_sentinel)
        if (!broadselectors)
          Files.writeString(sentinel, "SPECIFICATION-OWNED unselected sentinel\n", StandardOpenOption.CREATE_NEW)
        val sentinelbytes = Files.readAllBytes(sentinel).toVector
        val failedsnapshot = if (broadselectors) Some(_primary_snapshot(scenario)) else None
        val sentinelstatus = failedsnapshot.flatMap(_._1.dirty.find(_.path == _primary_sentinel))
        val originalphase = Option.when(broadselectors)(Files.readString(Path.of(host.target.root).resolve("docs/phase.md")))
        if (broadselectors) {
          Then("E9 the failed publication itself introduced the exact untracked sentinel after candidate staging")
          Files.readString(sentinel) shouldBe "SPECIFICATION-OWNED unselected sentinel\n"
          sentinelstatus shouldBe Some(G.DirtyPath('?', '?', _primary_sentinel, None))
          Files.exists(Path.of(host.target.root).resolve(_primary_note)) shouldBe false
        }
        val initialsnapshot = _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, started.sessionId))
        val reviewoutput = _repair_primary_candidate(scenario, task, failedoutput,
          initialrequest.repairProgress.get, initialrequest.generation, narrowauthority = false, correctivedoc = broadselectors)
        val review = reviewoutput.business[V.GoalPhaseAdvanceResult]
        Then("the covering ordinary host needs no per-candidate Human and retains the original Plan, base and initial evidence")
        GoalPhaseNativeMetadata.normalizePublicJson(json(review.work.get.selectedPlan.toRecord())).fold(fail(_), identity) shouldBe
          GoalPhaseNativeMetadata.normalizePublicJson(json(F.publicPlan(plan).toRecord())).fold(fail(_), identity)
        review.work.get.originalBase shouldBe initial.work.get.originalBase
        review.work.get.completed shouldBe empty
        val aftersnapshot = _native_fixture(scenario)(LegacyDependencyHistoryFixture.snapshotC(_, started.sessionId)).toMap
        initialsnapshot.filterNot(_._1 == "instance").foreach { case (key, bytes) => aftersnapshot(key) shouldBe bytes }
        Files.readAllBytes(failedoutput.invocation.stdout).toVector shouldBe failedbytes
        Files.readAllBytes(sentinel).toVector shouldBe sentinelbytes
        if (broadselectors) {
          Then("E9 the actual repaired candidate selects the new corrective document and source without staging the excluded sentinel")
          val candidates = _primary_candidates(scenario, started.sessionId)
          candidates should not be empty
          candidates.foreach { candidate =>
            candidate.selectedPaths should not contain _primary_sentinel
            candidate.stagedPaths should not contain _primary_sentinel
            candidate.changedPaths should not contain _primary_sentinel
          }
          val repaired = candidates.filter(candidate => candidate.primaryPublicationRepair.nonEmpty && candidate.candidate.nonEmpty).maxBy(_.sourceRevision.toLong)
          repaired.selectedPaths should contain allOf (_primary_source, _primary_note)
          repaired.selectedPaths should contain allOf ("docs/phase.md", _primary_renamed_phase)
          repaired.selectedPaths should not contain "docs"
          repaired.selectedPaths should not contain "src/main/scala"
          repaired.selectedPaths.distinct shouldBe repaired.selectedPaths
          repaired.stagedPaths shouldBe repaired.selectedPaths
          review.work.get.review.get.changedPaths should contain allOf (_primary_source, _primary_note)
          review.work.get.review.get.changedPaths should not contain _primary_sentinel
          _primary_snapshot(scenario)._1.dirty.find(_.path == _primary_sentinel) shouldBe sentinelstatus
          val pending = _native_fixture(scenario) { core => for {
            head <- GitObjectId.parseC(failedsnapshot.get._1.head)
            paths <- WorkflowManagedProcessExecution.executeGitC(core, GitOperation.PendingPaths(head)).flatMap(_.valueC)
          } yield paths.map(_.value) }
          pending should contain allOf (_primary_source, _primary_note)
          pending should not contain _primary_sentinel
        }
        When("the fixture-owned independent reviewer accepts the actual repaired candidate and normal Step authority closes it")
        val beforecommit = scenario.candidateHead
        val closed = _close_step(scenario, task, reviewoutput, "ordinary-primary").business[V.GoalPhaseAdvanceResult]
        Then("a real local Step commit leads to the sentinel next Step and repeat collection performs no effects")
        closed.work.get.kind shouldBe "ImplementStep"
        closed.work.get.stepIdentity shouldBe Some("step-2")
        closed.work.get.completed.map(_.step) shouldBe Vector("step-1")
        closed.work.get.completed.last.review shouldBe Some("fixture-ordinary-primary-step-review")
        scenario.candidateHead should not be beforecommit
        closed.work.get.completed.last.commit.identity shouldBe scenario.candidateHead
        Files.readAllBytes(sentinel).toVector shouldBe sentinelbytes
        _primary_snapshot(scenario)._1.dirty.map(_.path) should contain("docs/unselected-primary-sentinel.md")
        if (broadselectors) {
          Then("E9 the actual Step commit contains the corrective source and document while the sentinel stays untracked and outside the index")
          _primary_snapshot(scenario)._1.dirty.find(_.path == _primary_sentinel) shouldBe sentinelstatus
          val committed = _native_fixture(scenario) { core => for {
            before <- GitObjectId.parseC(failedsnapshot.get._1.tree)
            after <- GitObjectId.parseC(closed.work.get.completed.last.commit.tree)
            changes <- WorkflowManagedProcessExecution.executeGitC(core, GitOperation.TreeChanges(before, after)).flatMap(_.valueC)
            contents <- changes.filter(change => Vector(_primary_source, _primary_note, _primary_renamed_phase).contains(change.path.value))
              .foldLeft(Consequence.success(Vector.empty[(String, String)])) { (prior, change) => for {
                values <- prior
                blob <- change.newBlob.map(Consequence.success).getOrElse(Consequence.stateConflict("Committed corrective content has no blob"))
                content <- WorkflowManagedProcessExecution.executeGitC(core, GitOperation.BlobContent(blob)).flatMap(_.valueC)
              } yield values :+ (change.path.value -> content.text.getOrElse(fail("Corrective blob must contain text"))) }
            head <- GitObjectId.parseC(closed.work.get.completed.last.commit.identity)
            pending <- WorkflowManagedProcessExecution.executeGitC(core, GitOperation.PendingPaths(head)).flatMap(_.valueC)
          } yield (changes.map(_.path.value), contents.toMap, pending.map(_.value),
            changes.map(change => change.path.value -> change.status).toMap) }
          committed._1 should contain allOf (_primary_source, _primary_note)
          committed._1 should not contain _primary_sentinel
          committed._2(_primary_note) shouldBe _primary_note_text
          committed._2(_primary_source) shouldBe Files.readString(Path.of(host.target.root).resolve(_primary_source))
          committed._2(_primary_source) should include("\"qualified-primary-api\"")
          committed._3 should not contain _primary_sentinel
          Then("E9 the committed rename removes the old phase path and retains its complete original content at the new path")
          committed._1 should contain allOf ("docs/phase.md", _primary_renamed_phase)
          committed._4("docs/phase.md") shouldBe "D"
          committed._4(_primary_renamed_phase) shouldBe "A"
          committed._2(_primary_renamed_phase) shouldBe originalphase.get
          Files.exists(Path.of(host.target.root).resolve("docs/phase.md")) shouldBe false
          Files.readString(Path.of(host.target.root).resolve(_primary_renamed_phase)) shouldBe originalphase.get
        }
        val history = _history(scenario, started.sessionId)
        val calls = _lines(scenario.root.resolve("primary-all-sbt-invocations.txt"))
        val recollected = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
        recollected shouldBe closed.copy(message = closed.work.get.purpose, validation = None)
        _history(scenario, started.sessionId) shouldBe history
        _lines(scenario.root.resolve("primary-all-sbt-invocations.txt")) shouldBe calls
        _lines(scenario.root.resolve("primary-publication-attempts.txt")).size shouldBe 2
        When("the installed read-only collector recollects the original failed publication twice after Step closure")
        def _collect_failed_(): LocalPublication.Result = {
          val collected = scenario.callInstalled(executable, "LocalPublicationService", "collectLocalPublication",
            Vector("--input", failedpublication.request.input.asJson.mapObject(_.remove("answer")).noSpaces),
            assemblyDescriptor = Some(scenario.root.resolve(".textus/assembly.yaml"))).business[V.LocalPublicationResult]
          GoalPhaseNativeMetadata.normalizePublicJson(json(collected.publication.get))
            .flatMap(_.as[LocalPublication.Result].left.map(_.message)).fold(fail(_), identity)
        }
        val collected = _collect_failed_()
        val collectedagain = _collect_failed_()
        Then("the exact original failure remains immutable and collection replays no primary effect")
        collected shouldBe failedpublication
        collectedagain shouldBe failedpublication
        _lines(scenario.root.resolve("primary-all-sbt-invocations.txt")) shouldBe calls
        _lines(scenario.root.resolve("primary-publication-attempts.txt")).size shouldBe 2
        Files.readAllBytes(failedoutput.invocation.stdout).toVector shouldBe failedbytes
        Files.readAllBytes(sentinel).toVector shouldBe sentinelbytes
        if (broadselectors)
          _primary_snapshot(scenario)._1.dirty.find(_.path == _primary_sentinel) shouldBe sentinelstatus
      }
    }
    }
    Vector("failed", "qualified", "historical-gap", "existing-artifacts", "primary-failure", "primary-source-successor",
      "primary-source-successor-repair", "primary-history-source-description", "primary-source-check-failure").foreach { mode =>
    val unknown = mode != "failed"
    val historicalpolicy = mode == "primary-history-source-description"
    val sourcecheckfailure = mode == "primary-source-check-failure"
    val repairprimary = mode == "primary-source-successor-repair" || historicalpolicy || sourcecheckfailure
    val committedsource = mode == "primary-source-successor" || repairprimary || sourcecheckfailure
    val primaryfailure = mode == "primary-failure" || committedsource
    val title = mode match {
      case "primary-source-check-failure" => "E11 qualify the current committed successor after genuine primary and check repairs"
      case "primary-history-source-description" => "E10 authenticate historical primary repair while describing a finite committed dependency successor"
      case "primary-source-successor-repair" => "E8 retain completed source succession while repairing a later primary compile failure"
      case "primary-source-successor" => "E6 continue an authenticated old correction prefix through a new committed source root"
      case "primary-failure" => "E5 continue a real primary compile failure with one combined Human source decision and separate ordered publication facts"
      case "qualified" => "continue an UNKNOWN prerequisite through native Wait source adoption and one qualified successor"
      case "historical-gap" => "continue a historical evidence gap through Human source adoption and one authorized new publication"
      case "existing-artifacts" => "accept existing artifacts with unknown effects and retain a real failed consumer check without publication"
      case _ => "adopt a corrected source once and retain the same Session, primary candidate, prefix and repair accounting"
    }
    def _run_scenario_(): Unit = {
      Given("M3R-CLI-01: a selected installed CLI and tiny repositories with an accepted first Step and a real later publication failure")
      val executable = Path.of(sys.props.getOrElse("smworkflow.spec.installedCommand",
        cancel("Explicitly select smworkflow.spec.installedCommand for this installed-only scenario")))
      executable.isAbsolute shouldBe true
      sys.props.get("smworkflow.spec.installations") shouldBe None
      val scenario = F.freshDependency("prerequisite-publication-" + mode, bindingTransform = binding =>
        binding.copy(paths = Vector("build.sbt", _source, "value.txt")))
      val firstbinding = scenario.host.goal.dependencyValidation.get
      val binding = firstbinding.copy(identity = "fixture-prerequisite-second", step = "step-2",
        checks = firstbinding.checks.zipWithIndex.map((check, index) => check.copy(identity = "second-" + check.identity, ordinal = index + 6)))
      if (primaryfailure) {
        val existingchecks = firstbinding.checks ++ binding.checks ++ scenario.host.goal.stepValidation.checks
        _primary_checks.map(_.identity).intersect(existingchecks.map(_.identity)) shouldBe empty
        _primary_checks.map(_.ordinal).intersect(existingchecks.map(_.ordinal)) shouldBe empty
      }
      val publicationtarget = _configure(scenario, binding.identity, unknown, primaryfailure)
      if (primaryfailure) _prepare_primary_project(scenario)
      val repository = Path.of(firstbinding.root)
      val sourcepackage = if (primaryfailure) "package fixture\n" else ""
      val correction = sourcepackage + _corrected
      Files.createDirectories(repository.resolve("src/main/scala"))
      Files.writeString(repository.resolve(_source), sourcepackage + "class RecoveryMarker { def value: String = \"accepted-prefix\" }\n")
      Files.writeString(repository.resolve("build.sbt"), """
        |name := "prerequisite-recovery-fixture"
        |organization := "org.example.smworkflow.fixture"
        |version := "0.0.0-__VERSION__-SNAPSHOT"
        |publishLocal := publishLocal.dependsOn(Def.task {
        |  require(IO.read(file("src/main/scala/RecoveryMarker.scala")).contains("approved-correction"),
        |    "Selected source is not publication-ready")
        |}).value
        |Global / onLoad := {
        |  val previous = (Global / onLoad).value
        |  state => {
        |    val commands = state.remainingCommands.map(_.commandLine).mkString("|")
        |    IO.append(file("../all-sbt-invocations.txt"), commands + "\n")
        |    if (commands.contains("publishLocal")) IO.append(file("../publication-attempts.txt"), commands + "\n")
        |    previous(state)
        |  }
        |}
        |""".stripMargin.replace("__VERSION__", scenario.root.getFileName.toString), StandardOpenOption.APPEND)
      val widened = scenario.plan.copy(steps = scenario.plan.steps.map(step =>
        step.copy(ownedPaths = (step.ownedPaths ++ Vector("docs/preserved.md") ++ Option.when(unknown)(_consumer) ++
          Option.when(primaryfailure)(_primary_source)).distinct,
          checks = if (primaryfailure && step.identity == "step-2") _primary_checks.map(_.identity) else step.checks,
          actionPolicy = if (primaryfailure && step.identity == "step-2") Some(GoalPhaseStepAssessment.Policy(
            "fixture-required-independent-review", "code", "required", "off", "off", Vector.empty)) else step.actionPolicy)) ++
          Option.when(primaryfailure)(GoalPhaseOrderedProgression.Step("step-3",
            "Leave the isolated qualification open after the real repaired Step closure", Vector("docs/phase.md"),
            Vector.empty, Vector("test"))))
      Files.writeString(scenario.root.resolve("goal-plan.json"), widened.asJson.noSpaces)
      val preparedplan = scenario.prepareHostInput("prepareGoalStart", scenario.selectedGoalPreparationInput.toRecord())
      preparedplan.exitCode shouldBe 0
      scenario.selectPreparedImport(preparedplan)
      val task = scenario.ownTask("prerequisite-recovery")
      val started = scenario.start().business[V.GoalPhaseStartResult]
      scenario.retain(task, started)
      val implementation = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      val proposal = GoalPhaseDependencyScope.Proposal("fixture-prerequisite-scope", "1", "IMPLEMENTATION",
        firstbinding.requester, firstbinding.dependency, ContextReference("fixture-linked-contract", "1"),
        "SPECIFICATION-OWNED exact producer scope", firstbinding.paths, Vector("Compile and check the actual producer"),
        Vector(ContextReference(firstbinding.step, "1")), Vector.empty)
      _submit(scenario, implementation, Json.obj("outcome" -> "decision-needed".asJson,
        "summary" -> "SPECIFICATION-OWNED dependency scope question".asJson, "findings" -> Json.arr(),
        "dependencyProposal" -> proposal.asJson), "fixture-scope-proposal")
      val questionoutput = scenario.advance(task)
      val question = questionoutput.business[V.GoalPhaseAdvanceResult]
      val answer = V.GoalPhaseOrderedDecisionAnswer("ApproveDependencyScope", "SPECIFICATION-OWNED producer scope")
      val approval = V.WorkflowArtifactReference("fixture-prerequisite-approval", "1")
      val execution = _provision(scenario, questionoutput, "human", answer, approval, "scope")
      scenario.call("command", question.submitOperation.get, Vector("--sessionId", started.sessionId,
        "--answer", json(answer.toRecord()).noSpaces, "--resultReference", json(approval.toRecord()).noSpaces,
        "--executionEvidence", json(execution.toRecord()).noSpaces))
        .business[V.GoalPhaseOrderedDecisionSubmissionResult].accepted shouldBe true
      val resumed = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      Files.writeString(Path.of(scenario.host.target.root).resolve("value.txt"), "expected\n")
      Files.writeString(repository.resolve("value.txt"), "expected\n")
      val selection = D.Selection(ContextReference(firstbinding.identity, firstbinding.revision.toString),
        Vector(D.ApprovalReference(ContextReference(proposal.identity, proposal.revision), ContextReference(approval.identity, approval.revision))))
      _submit(scenario, resumed, Json.obj("outcome" -> "completed".asJson,
        "summary" -> "SPECIFICATION-OWNED captured producer candidate".asJson, "findings" -> Json.arr(),
        "dependencyValidation" -> selection.asJson), "fixture-source-completion")

      When("the valid first source completes native checks, independent review and ordinary Step closure")
      val firstoutput = scenario.advance(task)
      val firstreview = firstoutput.business[V.GoalPhaseAdvanceResult]
      val firstcapture = _capture(firstreview)
      val firstbytes = Files.readString(firstoutput.invocation.stdout)
      val next = _close_step(scenario, task, firstoutput).business[V.GoalPhaseAdvanceResult]
      val firstpublications = _lines(scenario.root.resolve("dependency/publication-attempts.txt")).size
      val firststages = _stages(scenario, started.sessionId)
      Then("the second Step retains the genuine first closure and original primary base")
      firstcapture.admission.acceptedPrefix shouldBe None
      next.work.get.stepIdentity shouldBe Some("step-2")
      next.work.get.completed.map(_.step) shouldBe Vector("step-1")
      next.work.get.originalBase shouldBe implementation.work.get.originalBase
      firstpublications shouldBe 0
      firststages shouldBe 0

      Given("the same dependency and accepted tree under a distinct second-Step binding and genuine scope answer")
      if (primaryfailure) {
        if (repairprimary) _enable_primary_publication(scenario, publicationtarget, binding.requester.identity)
        else _enable_primary_publication(scenario, publicationtarget)
      }
      val (second, secondselection, legacyadmission) = _adopt_prefix(scenario, task, next, binding)
      Files.writeString(Path.of(scenario.host.target.root).resolve("docs/phase.md"), "Accepted first Step and current integration\n")
      if (primaryfailure) _write_primary_product(scenario, repairprimary, requiredocument = sourcecheckfailure)
      else if (unknown) _write_consumer(scenario, repository)
      _submit(scenario, second, Json.obj("outcome" -> "completed".asJson,
        "summary" -> "SPECIFICATION-OWNED unchanged legacy second dependency source".asJson, "findings" -> Json.arr(),
        "dependencyValidation" -> secondselection.asJson), "fixture-second-source-completion")
      When("the existing specification-owned legacy producer emits that actual completion before any second candidate exists")
      val legacybytes = _native_fixture(scenario)(LegacyDependencyHistoryFixture.legacyCompletionC(_,
        second.issuedWorkContext.get.continuationId, legacyadmission))
      Then("only that historical completion omits the prefix and its original accepted dependency tree remains unchanged")
      legacybytes should not include "acceptedPrefix"

      When("ordinary Advance first attempts the actual prerequisite publication")
      val failedoutput = scenario.advance(task)
      val failed = failedoutput.business[V.GoalPhaseAdvanceResult]
      val oldreport = Files.readString(failedoutput.invocation.stdout)
      val oldstages = _stages(scenario, started.sessionId)
      Then("the old no-prefix second candidate retains an actual failed prerequisite without replaying the accepted first Step")
      failed.outcome shouldBe Some("failed")
      failed.localPublication should not be empty
      oldstages shouldBe firststages
      _lines(scenario.root.resolve("dependency/publication-attempts.txt")).size shouldBe firstpublications + 1

      Given("M3-PC-001: a later untracked primary document lies inside the widened Step ownership but outside the captured selection")
      val preserved = Path.of(scenario.host.target.root).resolve("docs/preserved.md")
      Files.writeString(preserved, "SPECIFICATION-OWNED unselected primary worktree bytes\n", StandardOpenOption.CREATE_NEW)
      val preservedbytes = Files.readAllBytes(preserved).toVector
      val (preservedfrontier, preservedindex) = _primary_snapshot(scenario)
      val preservedstatus = preservedfrontier.dirty.find(_.path == "docs/preserved.md").get

      When("one selected source file is corrected and the public recovery target is described")
      Files.writeString(repository.resolve(_source), correction)
      val descriptionoutput = scenario.call("command", R.describeOperation, Vector("--sessionId", started.sessionId))
      val description = descriptionoutput.business[V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult]
      val target = take(R.targetC(description.target))
      val old = target.failed
      Then("the target binds the real first native capture and closure while preserving the historical absent-prefix admission")
      R.confirmedFailure(old) shouldBe true
      target.plan shouldBe widened
      target.plan.steps.find(_.identity == binding.step).get.ownedPaths should contain("docs/preserved.md")
      GoalPhaseNativeCandidateBoundary.pathsOwned(Vector("docs/preserved.md"), target.candidate.selectedPaths) shouldBe false
      target.candidate.selectedPaths shouldBe target.candidate.stagedPaths
      preservedstatus.index shouldBe '?'
      preservedstatus.worktree shouldBe '?'
      preservedfrontier.head shouldBe target.candidate.candidate.get.head
      preservedindex shouldBe target.candidate.candidate.get.tree
      target.publicationTarget shouldBe publicationtarget
      target.admission shouldBe legacyadmission
      target.admission.acceptedPrefix shouldBe None
      target.candidate.dependency.get.candidate shouldBe firstcapture.candidate
      target.candidate.dependency.get.diff shouldBe firstcapture.diff
      target.ancestors.map(_.capture) shouldBe Vector(firstcapture)
      val anchor = target.anchoredAdmission.acceptedPrefix.get
      anchor.candidate shouldBe firstcapture.candidate
      anchor.sessionId shouldBe firstcapture.session
      anchor.plan shouldBe firstcapture.plan
      anchor.step shouldBe "step-1"
      anchor.paths shouldBe firstcapture.changedPaths
      anchor.closureRevision shouldBe target.ancestors.head.closureRevision
      target.anchoredAdmission shouldBe legacyadmission.copy(acceptedPrefix = Some(anchor))
      val crossedtargets = Vector(anchor.copy(step = "step-2"), anchor.copy(closureRevision = "1"),
        anchor.copy(candidate = anchor.candidate.copy(tree = "9" * 40))).map(crossed =>
        target.copy(anchoredAdmission = legacyadmission.copy(acceptedPrefix = Some(crossed))))
      old.request.source.tree should not be binding.base.tree
      R.correctionPaths(target) shouldBe Vector(_source)
      target.failed shouldBe old
      target.plan.identity shouldBe implementation.work.get.selectedPlan.identity
      val reference = V.WorkflowArtifactReference("fixture-independent-prerequisite-recovery", "1")
      val recovery = V.GoalPhasePrerequisitePublicationRecoveryInput(started.sessionId, description.target,
        R.choices.head, "SPECIFICATION-OWNED independently supplied correction direction", reference)
      def _recover_(value: V.GoalPhasePrerequisitePublicationRecoveryInput) = scenario.call("command", R.recoveryOperation,
        Vector("--input", json(value.toRecord()).noSpaces))
      When("the recovery is submitted without independent Human evidence")
      val unproved = _recover_(recovery)
      Then("the public operation refuses without authorizing an effect")
      _refused(unproved)
      val deferreference = V.WorkflowArtifactReference("fixture-independent-prerequisite-defer", "1")
      val deferred = recovery.copy(choice = "Defer", rationale = "SPECIFICATION-OWNED defer", reference = deferreference)
      val beforedefer = _lines(scenario.root.resolve("dependency/all-sbt-invocations.txt"))
      _provision(scenario, descriptionoutput, R.purpose, deferred, deferreference, "defer")
      When("an independently supplied Defer answer is admitted")
      val deferral = _recover_(deferred).business[V.GoalPhasePrerequisitePublicationRecoveryResult]
      Then("defer creates no publication or retained recovery stage proof")
      deferral.outcome shouldBe "deferred"
      _lines(scenario.root.resolve("dependency/all-sbt-invocations.txt")) shouldBe beforedefer
      _stages(scenario, started.sessionId) shouldBe oldstages
      _provision(scenario, descriptionoutput, R.purpose, recovery, reference, "recovery")
      When("the independent answer is reused with crossed or skipped native prefix facts")
      val crossedresults = crossedtargets.map(value => _recover_(recovery.copy(target = R.targetRecord(value))))
      Then("the public recovery refuses every supplied substitute for its actual accepted native chain")
      crossedresults.foreach(_refused)
      _stages(scenario, started.sessionId) shouldBe oldstages
      When("the approved source drifts before adoption")
      Files.writeString(repository.resolve(_source), correction.replace("approved-correction", "unapproved-drift"))
      val drifted = _recover_(recovery)
      Then("the stale target refuses")
      _refused(drifted)
      When("the exact approved bytes are restored and the independent recovery is adopted")
      Files.writeString(repository.resolve(_source), correction)
      val invocations = _lines(scenario.root.resolve("dependency/all-sbt-invocations.txt"))
      val adopted = _recover_(recovery).business[V.GoalPhasePrerequisitePublicationRecoveryResult]
      Then("only the mechanical direction is appended and duplicate adoption refuses")
      adopted.outcome shouldBe "corrected-source-adopted"
      _lines(scenario.root.resolve("dependency/all-sbt-invocations.txt")) shouldBe invocations
      _stages(scenario, started.sessionId) shouldBe oldstages
      _refused(_recover_(recovery))

      if (primaryfailure) {
        _primary_failure_continuation(scenario, executable, task, started.sessionId, publicationtarget, target,
          firstcapture, firstoutput, firstbytes, preserved, preservedbytes, oldstages, firstpublications, committedsource, repairprimary,
          historicalpolicy, sourcecheckfailure)
      } else if (unknown) {
        _unknown_continuation(scenario, executable, task, started.sessionId, publicationtarget, target,
          firstcapture, firstoutput, firstbytes, preserved, preservedbytes, oldstages, firstpublications, mode)
      } else {
      When("separate ordinary Advance stages only the correction, republishes and runs the fixed native checks")
      val reviewed = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      val (afterprimary, afterindex) = _primary_snapshot(scenario)
      Then("the corrected class is published once while the unselected primary worktree, candidate index and original repair facts remain exact")
      Files.readAllBytes(preserved).toVector shouldBe preservedbytes
      afterprimary.dirty.find(_.path == "docs/preserved.md") shouldBe Some(preservedstatus)
      afterprimary.head shouldBe preservedfrontier.head
      afterprimary.tree shouldBe preservedfrontier.tree
      afterindex shouldBe preservedindex
      reviewed.sessionId shouldBe started.sessionId
      reviewed.work.get.kind shouldBe "ReviewStep"
      reviewed.work.get.selectedPlan shouldBe implementation.work.get.selectedPlan
      reviewed.work.get.originalBase shouldBe implementation.work.get.originalBase
      reviewed.repairProgress shouldBe Some(GoalPhaseRepairProgress.publicSnapshot(target.repairProgress))
      val capture = take(GoalPhaseNativeMetadata.normalizePublicJson(json(reviewed.work.get.review.get.dependencyValidations.last))
        .flatMap(_.hcursor.get[D.Capture]("capture").left.map(_.message)).fold(ConsequenceFailure.apply, org.goldenport.Consequence.success))
      capture.admission.binding shouldBe binding
      capture.admission shouldBe target.anchoredAdmission
      capture.changedPaths shouldBe Vector(_source)
      capture.diff should include("approved-correction")
      capture.diff should not include "prerequisite-recovery-fixture"
      capture.primaryBase shouldBe firstcapture.primaryBase
      capture.candidate.head shouldBe firstcapture.candidate.head
      _capture(reviewed) shouldBe firstcapture
      reviewed.work.get.review.get.dependencyValidations.head shouldBe firstreview.work.get.review.get.dependencyValidations.head
      reviewed.work.get.completed shouldBe next.work.get.completed
      reviewed.work.get.completed.head.commit shouldBe next.work.get.completed.head.commit
      Files.readString(firstoutput.invocation.stdout) shouldBe firstbytes
      capture.candidate.tree should not be target.candidate.dependency.get.candidate.tree
      capture.sourceRevision.toLong should be > target.frontierRevision.toLong
      reviewed.work.get.candidate.get.head shouldBe target.candidate.candidate.get.head
      reviewed.work.get.candidate.get.tree shouldBe target.candidate.candidate.get.tree
      val jars = Files.list(repository.resolve("target/scala-2.12"))
      val jar = try jars.filter(path => path.getFileName.toString.endsWith(".jar") &&
        !path.getFileName.toString.endsWith("-sources.jar") && !path.getFileName.toString.endsWith("-javadoc.jar") && !path.getFileName.toString.endsWith("-tests.jar"))
        .findFirst().orElseThrow() finally jars.close()
      val loader = new java.net.URLClassLoader(Array(jar.toUri.toURL), getClass.getClassLoader)
      try {
        val clazz = loader.loadClass("RecoveryMarker")
        clazz.getMethod("value").invoke(clazz.getConstructor().newInstance()) shouldBe "approved-correction"
      } finally loader.close()
      _lines(scenario.root.resolve("dependency/publication-attempts.txt")).size shouldBe firstpublications + 2
      _stages(scenario, started.sessionId) shouldBe oldstages + 1
      val completed = _lines(scenario.root.resolve("dependency/all-sbt-invocations.txt"))
      When("ordinary Advance recollects the completed recovery and the original failed publication is collected")
      val repeated = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      val (repeatedprimary, repeatedindex) = _primary_snapshot(scenario)
      Then("the accepted prefix and retained recovery stage proof stay unchanged, and publication or checks do not replay")
      repeated shouldBe reviewed
      Files.readAllBytes(preserved).toVector shouldBe preservedbytes
      repeatedprimary.dirty.find(_.path == "docs/preserved.md") shouldBe Some(preservedstatus)
      repeatedprimary.head shouldBe preservedfrontier.head
      repeatedprimary.tree shouldBe preservedfrontier.tree
      repeatedindex shouldBe preservedindex
      _lines(scenario.root.resolve("dependency/all-sbt-invocations.txt")) shouldBe completed
      _stages(scenario, started.sessionId) shouldBe oldstages + 1
      val collected = scenario.callInstalled(executable, "LocalPublicationService", "collectLocalPublication",
        Vector("--input", old.request.input.asJson.mapObject(_.remove("answer")).noSpaces), assemblyDescriptor = Some(scenario.root.resolve(".textus/assembly.yaml")))
        .business[V.LocalPublicationResult]
      collected.status shouldBe "failed"
      Files.readString(failedoutput.invocation.stdout) shouldBe oldreport
      _lines(scenario.root.resolve("dependency/all-sbt-invocations.txt")) shouldBe completed

      Given("M3-PUB-COLLECT: the same real test Session after successful prerequisite recovery and its immutable failed predecessor")
      val publicationcount = _lines(scenario.root.resolve("dependency/publication-attempts.txt"))
      val stages = _stages(scenario, started.sessionId)
      val latestinput = V.LocalPublicationLatestCollectionInput(publicationtarget.identity, started.sessionId)
      def _latest_(input: V.LocalPublicationLatestCollectionInput) =
        scenario.callInstalled(executable, "LocalPublicationService", "collectLatestLocalPublication",
          Vector("--input", json(input.toRecord()).noSpaces),
          assemblyDescriptor = Some(scenario.root.resolve(".textus/assembly.yaml")))
      When("the installed read-only latest operation is called twice without guessing the recovery request identity")
      val latest = _latest_(latestinput).business[V.LocalPublicationResult]
      val latestagain = _latest_(latestinput).business[V.LocalPublicationResult]
      val saved = GoalPhaseNativeMetadata.normalizePublicJson(json(latest.publication.get))
        .flatMap(_.as[LocalPublication.Result].left.map(_.message)).fold(fail(_), identity)
      val exact = scenario.callInstalled(executable, "LocalPublicationService", "collectLocalPublication",
        Vector("--input", saved.request.input.asJson.mapObject(_.remove("answer")).noSpaces),
        assemblyDescriptor = Some(scenario.root.resolve(".textus/assembly.yaml")))
        .business[V.LocalPublicationResult]
      Then("the latest saved request and successful result match the original collector and no publication or recovery stage replays")
      latest.status shouldBe "published"
      latestagain shouldBe latest
      exact shouldBe latest
      saved.request.input.ownerSessionId shouldBe Some(started.sessionId)
      saved.request.target shouldBe publicationtarget
      saved.request.input.requestIdentity should not be old.request.input.requestIdentity
      _lines(scenario.root.resolve("dependency/all-sbt-invocations.txt")) shouldBe completed
      _lines(scenario.root.resolve("dependency/publication-attempts.txt")) shouldBe publicationcount
      _stages(scenario, started.sessionId) shouldBe stages
      When("a nonexistent owning Session is supplied for that same latest target")
      val wrongowner = _latest_(latestinput.copy(ownerSessionId = started.sessionId + "-wrong-owner"))
      Then("the installed operation rejects it without revealing or replaying the latest publication")
      _refused(wrongowner)
      _lines(scenario.root.resolve("dependency/all-sbt-invocations.txt")) shouldBe completed
      _lines(scenario.root.resolve("dependency/publication-attempts.txt")) shouldBe publicationcount
      _stages(scenario, started.sessionId) shouldBe stages
      Files.readString(failedoutput.invocation.stdout) shouldBe oldreport
      }
    }
    if (primaryfailure) title must afterWord(
      "in spec:goal-phase-prerequisite-publication-recovery, example:" + (if (sourcecheckfailure) "E11" else if (historicalpolicy) "E10" else if (repairprimary) "E8" else if (committedsource) "E6" else "E5") +
        ", rules:ordered-authority,source-capture,no-replay, phase:6.10") {
      (if (sourcecheckfailure) "retain old failures while capturing and checking the current source through independent review and Step closure"
        else if (historicalpolicy) "describe the approved committed successor without adopting it or changing history and effects"
        else "retain the real failures through mandatory review and genuine Step closure") in { _run_scenario_() }
    } else title in { _run_scenario_() }
    }
  }

  private object ConsequenceFailure {
    def apply[A](message: String): org.goldenport.Consequence[A] = org.goldenport.Consequence.stateConflict(message)
  }
}
