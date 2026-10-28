package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.file.{Files, Path, StandardOpenOption}
import java.time.Instant
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.{GitOperationKind, SbtOperation}
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.protocol.Request
import org.goldenport.record.RecordPresentable
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import GoalPhaseDependencyValidation as D
import GoalPhaseReleaseDependencyValidation as R
import SplitPhaseExplicitPartitionFixture.take
import D.given

/** Fresh disposable fixtures only. Human answers, authority and independent
  * review are SPECIFICATION-OWNED, never actual user/provider acceptance.
  * SQLite is read only; all progression uses the ordinary public lifecycle.
  * @since   Oct. 28, 2026
  * @version Oct. 28, 2026
  * @author  ASAMI, Tomoharu
  */
private[admission] object ReleaseDependencyCliSpecification extends ThreeWorkflowManagedRepositoryFixture {
  final case class Installation(executable: Path, images: Path)
  final case class Scope(root: Path, host: ThreeWorkflowRuntimeParameters.Definition,
    plan: GoalPhaseOrderedProgression.Plan, managed: Option[F.Scenario]) {
    def input: Json = NativeWorkflowOperationInputFixture.selectedGoalInput(host.goal.input).value
    def candidateHead: String = _git(Path.of(host.target.root), Vector("rev-parse", "HEAD")).trim
    def candidateIndexDiff: String = _git(Path.of(host.target.root), Vector("diff", "--cached", "--binary"))
  }
  final case class Frontier(scenario: Scope, session: String, original: V.GoalPhaseAdvanceResult,
    repair: V.GoalPhaseAdvanceResult, binding: D.Binding, oldCapture: D.Capture, snapshot: Json)
  private val _source_paths = Vector("GoalPhaseNativeRun.scala", "ThreeWorkflowRuntimeParameters.scala",
    "GoalPhaseNativeWorkContinuation.scala", "GoalPhaseNativeValidation.scala", "GoalPhaseNativeDependencyValidation.scala",
    "GoalPhaseDependencySourceRevision.scala", "GoalPhaseDependencyValidation.scala", "GoalPhaseReleaseDependencyValidation.scala",
    "WorkflowManagedProcessExecution.scala", "LocalPublication.scala", "LocalPublicationRuntime.scala")
      .map("src/main/scala/org/simplemodeling/textus/smworkflow/admission/" + _) ++
    Vector("GoalPhaseReleaseDependencyValidationSpec.scala", "NativeGoalDependencyValidationCliSpec.scala",
      "NativeGoalReleaseDependencyValidationCliSpec.scala", "LocalPublicationSpec.scala", "NativeLocalPublicationCliSpec.scala")
      .map("src/test/scala/org/simplemodeling/textus/smworkflow/admission/" + _) ++
    Vector("docs/spec/goal-phase-release-dependency-validation.md", "docs/spec/runtime-owned-local-publication.md")
  private def _selected_plan(scenario: Scope): GoalPhaseOrderedProgression.Plan =
    _read(scenario.root.resolve("goal-plan.json")).as[GoalPhaseOrderedProgression.Plan].toOption.get
  private def _coherent(scenario: Scope): Boolean = Files.isRegularFile(scenario.root.resolve("release-source-fixture.json"))
  private def _prior_release_history(scenario: Scope): Boolean =
    Files.isRegularFile(scenario.root.resolve("prior-release-history"))
  private def _failed_release_history(scenario: Scope): Boolean =
    Files.isRegularFile(scenario.root.resolve("failed-release-history"))
  private def _source_integrated(scenario: Scope): Boolean = {
    val root = Path.of(scenario.host.target.root)
    val owner = root.resolve("src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseReleaseDependencyValidation.scala")
    val chain = root.resolve("src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseNativeWorkContinuation.scala")
    Files.isRegularFile(owner) && Files.readString(owner).contains("object GoalPhaseReleaseDependencyValidation") &&
      Files.readString(chain).contains("def historicalPhaseC")
  }
  private def _read(path: Path): Json = parse(Files.readString(path)).fold(e => fail(e.message), identity)
  protected override def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    require(scenario == "Goal")
    val declaration = _read(root.resolve(".textus/config.json")).hcursor
      .get[String]("textus.subsystem.process-execution.plan").fold(e => fail(e.message), identity)
    parse(declaration).fold(e => fail(e.message), identity)
  }
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def _array(value: Json, key: String): Vector[Json] = value.hcursor.get[Vector[Json]](key).toOption.get
  private def _config(scenario: Scope): Json = _read(scenario.root.resolve(".textus/config.json"))
  private def _write_config(scenario: Scope, value: Json): Unit =
    Files.writeString(scenario.root.resolve(".textus/config.json"), value.noSpaces)
  private def _host(scenario: Scope): ThreeWorkflowRuntimeParameters.Definition =
    take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(parse(_config(scenario).hcursor
      .get[String]("sm-workflow.runtime.definition").toOption.get).toOption.get))
  private def _save(scenario: Scope, name: String, text: String): String = {
    val path = scenario.root.resolve(name + ".json")
    Files.writeString(path, text, StandardOpenOption.CREATE_NEW)
    path.toUri.toASCIIString
  }
  private def _git(root: Path, arguments: Vector[String]): String = {
    require(root.isAbsolute && root.normalize == root && root.toRealPath() == root &&
      root.startsWith(Path.of("").toAbsolutePath.normalize.resolve("target/sm-workflow/work/native-cli")),
      "Only disposable specification Git arrangements are owned")
    val argv = Vector("/usr/bin/git", "--no-optional-locks", "--no-replace-objects", "-C", root.toString,
      "-c", "core.hooksPath=/dev/null", "-c", "user.name=Release Dependency Specification",
      "-c", "user.email=release-dependency@example.invalid", "-c", "gc.auto=0") ++ arguments
    val log = Files.createTempFile(root.getParent, "release-dependency-git-", ".log")
    val stdout = Files.createTempFile(root.getParent, "release-dependency-git-", ".stdout")
    val process = new ProcessBuilder(argv.asJava).redirectOutput(stdout.toFile).redirectError(log.toFile).start()
    process.getOutputStream.close()
    try {
      process.waitFor(30L, TimeUnit.SECONDS) shouldBe true
      withClue(Files.readString(log)) { process.exitValue() shouldBe 0 }
      Files.readString(stdout, java.nio.charset.StandardCharsets.UTF_8)
    } finally {
      if (process.isAlive) { process.destroyForcibly(); process.waitFor() }
      process.getInputStream.close(); process.getErrorStream.close()
    }
  }
  private final class Caller(val scenario: Scope, installation: Option[Installation]) {
    private var _owned_invocations = Vector.empty[F.Invocation]
    private def _installed_call(value: Installation, service: String, operation: String, parameters: Vector[String]): F.Result = {
      require(Set("GoalPhaseService", "NativeWorkflowPreparationService")(service) &&
        Set("startGoalPhase", "advanceGoalPhase", "submitGoalPhaseWorkResult", "submitGoalPhaseOrderedDecision",
          "prepareEvidence", "prepareAuthority")(operation), "Only the selected public fixture lifecycle is supported")
      require(value.executable.isAbsolute && Files.isExecutable(value.executable) && Files.isDirectory(value.images))
      val logs = Files.createTempDirectory(Files.createDirectories(scenario.root.resolve("release-installed-cli")), "call-")
      val stdout = logs.resolve("stdout.json")
      val stderr = logs.resolve("stderr.log")
      val argv = Vector(value.executable.toString, "--runtime-root", scenario.root.toString,
        "--textus.assembly.descriptor=" + scenario.root.resolve(".textus/assembly.yaml"), "command",
        SmWorkflowComponent.componentId.name + "." + service + "." + operation) ++ parameters ++ Vector("--json")
      val builder = new ProcessBuilder(argv.asJava).directory(scenario.root.toFile)
        .redirectOutput(stdout.toFile).redirectError(stderr.toFile)
      builder.environment().put("SM_WORKFLOW_INSTALLATIONS", value.images.toString)
      builder.environment().remove("TEXTUS_SM_WORKFLOW_CNCF_SOURCE_ROOT")
      val process = builder.start()
      process.getOutputStream.close()
      val invocation = F.Invocation(argv, process.pid(), stdout, stderr)
      _owned_invocations :+= invocation
      try {
        withClue(invocation.logs) { process.waitFor(7200L, TimeUnit.SECONDS) shouldBe true }
        F.Result(process.exitValue(), invocation)
      } finally {
        if (process.isAlive) {
          val stream = process.toHandle.descendants()
          val descendants = try stream.iterator().asScala.toVector finally stream.close()
          descendants.filter(_.isAlive).foreach(_.destroy())
          process.destroy()
          if (!process.waitFor(10L, TimeUnit.SECONDS)) process.destroyForcibly()
          descendants.filter(_.isAlive).foreach(_.destroyForcibly())
          process.waitFor()
        }
        process.getInputStream.close(); process.getErrorStream.close()
      }
    }
    def call(service: String, operation: String, parameters: Vector[String]): F.Result = installation match {
      case Some(value) if _coherent(scenario) => _installed_call(value, service, operation, parameters)
      case Some(value) => scenario.managed.get.callInstalled(value.executable, service, operation, parameters, Some(value.images),
        Some(scenario.root.resolve(".textus/assembly.yaml")))
      case None if service == "GoalPhaseService" => scenario.managed.get.call("command", operation, parameters)
      case None if service == "NativeWorkflowPreparationService" =>
        val input = take(new org.goldenport.record.io.RecordDecoder().json(parameters(1)))
        scenario.managed.get.prepareHostInput(operation, input)
      case _ => fail("Unsupported specification service")
    }
    def goal(operation: String, parameters: Vector[String]): F.Result = call("GoalPhaseService", operation, parameters)
    def advance(session: String): F.Result = goal("advanceGoalPhase", Vector("--sessionId", session))
    def prepare(operation: String, input: RecordPresentable): Unit = {
      val result = call("NativeWorkflowPreparationService", operation, Vector("--input", _json(input)))
      withClue(result.invocation.logs) { result.exitCode shouldBe 0 }
      if (_owned_invocations.contains(result.invocation)) {
        require(result.exitCode == 0 && result.invocation.pid > 0 && result.invocation.stdout.isAbsolute &&
          result.invocation.stdout.normalize == result.invocation.stdout && result.invocation.stdout.startsWith(scenario.root) &&
          Files.isRegularFile(result.invocation.stdout) && !Files.isSymbolicLink(result.invocation.stdout),
          "Import only an actual successful specification-owned invocation")
        _write_config(scenario, _config(scenario).mapObject(_.add("textus.import.data.file", result.invocation.stdout.toString.asJson)))
      } else scenario.managed.get.selectPreparedImport(result)
    }
    def submit(request: V.GoalPhaseAdvanceResult, result: Json, name: String,
      execution: Option[V.WorkflowExecutionEvidence] = None): Unit = {
      val actual = goal(request.submitOperation.get, Vector("--sessionId", request.submitSessionId.get,
        "--result", result.noSpaces, "--resultReference", _json(V.WorkflowArtifactReference(name, "1"))) ++
        execution.toVector.flatMap(e => Vector("--executionEvidence", _json(e))))
      withClue(actual.invocation.logs) { actual.business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true }
    }
    def evidence(actual: F.Result, purpose: String, answer: String, name: String,
      participant: Option[String] = None): V.WorkflowExecutionEvidence = {
      val reference = V.WorkflowArtifactReference(name, "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some(participant.getOrElse("specification-independent-reviewer")), None, None)
      prepare("prepareEvidence", V.WorkflowEvidencePreparationInput("GoalPhase", purpose,
        actual.invocation.stdout.toUri.toASCIIString, _save(scenario, name + "-answer", answer),
        _save(scenario, name + "-reference", _json(reference)), _save(scenario, name + "-execution", _json(execution)),
        "normal-development-host", Instant.now().minusSeconds(60).toString, "entity:smworkflow.cli.host_evidence", participant))
      execution
    }
    def approve(actual: F.Result, name: String): Unit = {
      val question = actual.business[V.GoalPhaseAdvanceResult]
      question.kind shouldBe "Human"
      val answer = V.GoalPhaseOrderedDecisionAnswer("ApproveDependencyScope", "SPECIFICATION-OWNED exact purpose and path union")
      val execution = evidence(actual, "human", _json(answer), name, Some("participant"))
      goal(question.submitOperation.get, Vector("--sessionId", question.submitSessionId.get, "--answer", _json(answer),
        "--resultReference", _json(V.WorkflowArtifactReference(name, "1")), "--executionEvidence", _json(execution)))
        .business[V.GoalPhaseOrderedDecisionSubmissionResult].accepted shouldBe true
    }
    def review(actual: F.Result, outcome: String, name: String, issues: Option[V.GoalPhaseIssueReport] = None): Unit = {
      val request = actual.business[V.GoalPhaseAdvanceResult]
      val report = result(request, outcome, issues)
      val execution = evidence(actual, "review", report.noSpaces, name)
      submit(request, report, name, Some(execution))
    }
    def authority(actual: F.Result, name: String): Unit = prepare("prepareAuthority", V.WorkflowAuthorityPreparationInput(
      actual.invocation.stdout.toUri.toASCIIString, _save(scenario, name + "-reference", _json(V.WorkflowArtifactReference(name, "1"))),
      "participant", "normal-development-host", Instant.now().minusSeconds(60).toString, "entity:smworkflow.cli.host_evidence"))
    def result(request: V.GoalPhaseAdvanceResult, outcome: String,
      issues: Option[V.GoalPhaseIssueReport] = None): Json = {
      val conditions = take(GoalPhaseAcceptanceBoundary.captureC(None, _selected_plan(scenario))).effectiveAcceptance.toVector
        .flatMap(GoalPhaseAcceptanceBoundary.applicable(_, request.work.get.kind))
      val acceptance = conditions.map { condition =>
        require(request.work.get.kind == "ReviewPhase", "This fixture's adopted conditions are Phase scoped")
        val phasecapture = request.work.get.review.toVector.flatMap(_.dependencyValidations).exists { evidence =>
          GoalPhaseNativeMetadata.normalizePublicJson(NativeCallerExecutionFixture.json(evidence)).toOption
            .flatMap(_.hcursor.get[D.Capture]("capture").toOption).exists(_.step.startsWith("phase:"))
        }
        val (satisfied, detail) = condition.identity match {
          case "usage-contract" =>
            val usage = Path.of(scenario.host.target.root).resolve("docs/usage.md")
            val expected = "The accepted input is exactly expected followed by LF.\n"
            (Files.isRegularFile(usage) && (if (_prior_release_history(scenario)) Files.readString(usage).startsWith(expected)
              else Files.readString(usage) == expected),
              "docs/usage.md must describe exactly expected followed by LF")
          case "release-utf8-line" if _prior_release_history(scenario) =>
            val usage = Path.of(scenario.host.target.root).resolve("docs/usage.md")
            val applicable = request.repairProgress.exists(_.ordinaryCount >= 1)
            (!applicable || (Files.isRegularFile(usage) &&
              Files.readString(usage).split("\n", -1).dropRight(1).contains("The encoding is UTF-8.")),
              "After the first Release repair, docs/usage.md must contain The encoding is UTF-8. as its own LF line")
          case "release-source-coherence" if _coherent(scenario) =>
            (!phasecapture || _source_integrated(scenario),
              "The declared C source must retain its qualified Release dependency continuation for its next normal publication")
          case _ => fail("No observation contract for adopted condition " + condition.identity)
        }
        V.GoalPhaseAcceptanceObservation(condition.identity, if (satisfied) "satisfied" else "unsatisfied",
          if (satisfied) "SPECIFICATION-OWNED observed satisfaction: " + condition.description else detail)
      }
      val failures = acceptance.filter(o => conditions.exists(c => c.identity == o.identity && c.required) &&
        o.status == "unsatisfied").map(_.detail)
      if (Set("accept", "completed")(outcome)) failures shouldBe empty
      if (outcome == "repair") failures should not be empty
      val findings = if (outcome == "repair") failures else Vector.empty
      val value = V.GoalPhaseWorkResult(outcome, "SPECIFICATION-OWNED semantic result", findings,
        Some(V.GoalPhaseAcceptanceReport(acceptance, Vector.empty)), None, issues, None, None, None, None,
        repairFindingSources = None, dependencyValidation = None, diagnosticRepair = None)
      NativeCallerExecutionFixture.json(value.toRecord())
    }
  }

  private def _proposal(binding: D.Binding, name: String): GoalPhaseDependencyScope.Proposal =
    GoalPhaseDependencyScope.Proposal(name, "1", if (binding.step.startsWith("phase:")) "REPAIR" else "IMPLEMENTATION", binding.requester, binding.dependency,
      ContextReference(name + "-usage", "1"), "SPECIFICATION-OWNED separate Release purpose", binding.paths,
      Vector("Validate only this exact new purpose while retaining the old accepted prefix"),
      Vector(ContextReference(binding.step, "1")), Vector.empty)
  private def _selection(binding: D.Binding, proposal: String, answer: String): D.Selection =
    D.Selection(ContextReference(binding.identity, binding.revision.toString),
      Vector(D.ApprovalReference(ContextReference(proposal, "1"), ContextReference(answer, "1"))))

  private def _explicit_path(property: String): Path = {
    val path = Path.of(sys.props.getOrElse(property, throw new IllegalArgumentException("Missing " + property)))
    require(path.isAbsolute && path.normalize == path && path.toRealPath() == path && !Files.isSymbolicLink(path),
      "Select an actual explicit canonical source/patch/updater")
    path
  }
  private def _prepare_release_history(scenario: Scope): Unit = {
    Files.writeString(scenario.root.resolve("prior-release-history"), "SPECIFICATION-OWNED pre-adopted Release history\n",
      StandardOpenOption.CREATE_NEW)
    val previous = _selected_plan(scenario)
    previous.repairLimit shouldBe (if (_failed_release_history(scenario)) 10 else 3)
    val adopted = previous.acceptance.get
    val plan = previous.copy(acceptance = Some(adopted.copy(conditions = adopted.conditions :+
      GoalPhaseAcceptanceBoundary.Condition("release-utf8-line", "phase", true,
        "After the first Release repair, docs/usage.md contains The encoding is UTF-8. as its own LF line; before that repair this condition is not applicable"))))
    Files.writeString(scenario.root.resolve("goal-plan.json"), plan.asJson.noSpaces)
    val selected = take(org.simplemodeling.textus.smworkflow.preparation.NativeGoalStartInput.prepareC(Map(
      "input" -> scenario.root.resolve("goal-input.json").toUri.toASCIIString,
      "plan" -> scenario.root.resolve("goal-plan.json").toUri.toASCIIString, "selection-id" -> "native-goal-selection",
      "participant" -> "participant", "origin" -> "ExplicitHuman", "idempotency-key" -> "native-specification-input",
      "selection-collection" -> "entity:smworkflow.cli.goal_selection", "plan-collection" -> "entity:smworkflow.cli.goal_plans"),
      reference => Consequence(Files.readString(Path.of(java.net.URI.create(reference))))))
    Files.writeString(scenario.root.resolve("selected-input.json"), take(selected.toJsonStringC))
  }
  private def _prepare_source(scenario: Scope): Unit = {
    val manifestpath = _explicit_path("smworkflow.spec.releaseDependencySourceManifest")
    val template = _explicit_path("smworkflow.spec.releaseDependencySourceTemplate")
    val patch = _explicit_path("smworkflow.spec.releaseDependencyQualifiedPatch")
    val updater = _explicit_path("smworkflow.spec.releaseDependencyUpdater")
    val manifest = _read(manifestpath)
    manifest.hcursor.get[String]("template") shouldBe Right(template.toString)
    require(Files.isRegularFile(patch) && Files.isExecutable(updater))
    val files = manifest.hcursor.get[Vector[String]]("files").toOption.get
    require(files.nonEmpty && files.distinct == files && files.forall { name =>
      val path = Path.of(name)
      !path.isAbsolute && path.normalize == path && !path.iterator().asScala.exists(p =>
        Set(".git", "target", ".codex-workflow", ".bsp", ".idea")(p.toString)) &&
        !name.endsWith(".class") && !name.endsWith(".jar")
    }, "Only the parent's finite semantic project source list is copied")
    val primary = Path.of(scenario.host.target.root)
    Files.deleteIfExists(primary.resolve("src/test/scala/ProductCheck.scala"))
    files.foreach { name =>
      val source = template.resolve(name)
      require(Files.isRegularFile(source) && source.toRealPath() == source && source.startsWith(template))
      val target = primary.resolve(name)
      Files.createDirectories(target.getParent)
      Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }
    val placeholders = _source_paths.filterNot(files.contains)
    placeholders.foreach { name =>
      val target = primary.resolve(name)
      require(!Files.exists(target), "Only an actually absent old source receives an empty placeholder")
      Files.createDirectories(target.getParent); Files.writeString(target, "", StandardOpenOption.CREATE_NEW)
    }
    val product = primary.resolve("src/test/scala/org/simplemodeling/textus/smworkflow/releasefixture/ProductCheckSpec.scala")
    Files.createDirectories(product.getParent)
    Files.writeString(product, """package org.simplemodeling.textus.smworkflow.releasefixture
      |import org.scalatest.wordspec.AnyWordSpec
      |import org.scalatest.matchers.should.Matchers
      |import org.scalatest.GivenWhenThen
      |@org.goldenport.cncf.validation.annotation.ValidationSpecification(
      |  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
      |final class ProductCheckSpec extends AnyWordSpec with Matchers with GivenWhenThen {
      |  "The specification-owned product" should {
      |    "retain its accepted input" in {
      |      Given("the specification-owned product with its accepted input")
      |      val root = java.nio.file.Path.of("").toAbsolutePath.normalize
      |      java.nio.file.Files.writeString(root.getParent.resolve("sbt-executions.txt"),
      |        java.lang.ProcessHandle.current().pid().toString + "\n",
      |        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)
      |      When("the actual product input is read")
      |      val actual = java.nio.file.Files.readString(root.resolve("value.txt"))
      |      Then("it retains exactly expected followed by LF")
      |      actual shouldBe "expected\n"
      |    }
      |  }
      |}
      |""".stripMargin, StandardOpenOption.CREATE_NEW)
    if (_failed_release_history(scenario)) {
      Files.writeString(product.resolveSibling("PhaseHistoryCheckSpec.scala"), """package org.simplemodeling.textus.smworkflow.releasefixture
        |import org.scalatest.wordspec.AnyWordSpec
        |import org.scalatest.matchers.should.Matchers
        |import org.scalatest.GivenWhenThen
        |@org.goldenport.cncf.validation.annotation.ValidationSpecification(
        |  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
        |final class PhaseHistoryCheckSpec extends AnyWordSpec with Matchers with GivenWhenThen {
        |  "The specification-owned Phase input contract" should {
        |    "retain the exact first sentence and both complete LF requirement lines" in {
        |      Given("the Phase usage document and its requirements adopted before Start")
        |      val root = java.nio.file.Path.of("").toAbsolutePath.normalize
        |      java.nio.file.Files.writeString(root.getParent.resolve("sbt-executions.txt"),
        |        java.lang.ProcessHandle.current().pid().toString + "\n",
        |        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)
        |      When("the actual current usage document is read")
        |      val usage = java.nio.file.Files.readString(root.resolve("docs/usage.md"))
        |      val lines = usage.split("\n", -1).dropRight(1).toVector
        |      Then("the accepted first sentence and both distinct LF lines are present")
        |      usage should startWith("The accepted input is exactly expected followed by LF.\n")
        |      lines should contain("Line endings are LF.")
        |      lines should contain("Input has one record.")
        |    }
        |  }
        |}
        |""".stripMargin, StandardOpenOption.CREATE_NEW)
    }
    val images = Files.createDirectories(scenario.root.resolve("produced-installations")).toRealPath()
    val registration = "  local_application_update:\n    enabled: true\n    application: sm-workflow\n" +
      "    producer: " + primary.toString.asJson.noSpaces + "\n    root: " + images.toString.asJson.noSpaces +
      "\n    updater: " + updater.toString.asJson.noSpaces + "\n    protocol: cncf.local-application-update.v1\n" +
      "    root_coordinate: org.goldenport:goldenport-cncf_3:0.5.3-SNAPSHOT\n" +
      "    repositories:\n      - ivy2Local\n      - central\n      - https://www.simplemodeling.org/repository/maven\n" +
      "    admission_host: " + scenario.root.toString.asJson.noSpaces + "\n    timeout_seconds: 1800\n"
    val cozy = Files.createDirectories(primary.resolve(".cozy"))
    Files.writeString(cozy.resolve("config.yaml"), "publication:\n" + registration, StandardOpenOption.CREATE_NEW)
    Files.writeString(primary.resolve("build.sbt"),
      "\npublishLocal := { val actual = publishLocal.value; IO.append(file(\"../publication-count.txt\"), \"published\\n\"); actual }\n",
      StandardOpenOption.APPEND)
    Files.writeString(scenario.root.resolve("release-source-fixture.json"), Json.obj(
      "manifest" -> manifestpath.toString.asJson, "template" -> template.toString.asJson, "patch" -> patch.toString.asJson,
      "updater" -> updater.toString.asJson, "images" -> images.toString.asJson, "placeholders" -> placeholders.asJson).noSpaces,
      StandardOpenOption.CREATE_NEW)
    val paths = Vector("value.txt", "docs/usage.md") ++ _source_paths
    val previous = _selected_plan(scenario)
    val adopted = previous.acceptance.get
    val plan = previous.copy(steps = previous.steps.map(_.copy(ownedPaths = paths)),
      acceptance = Some(adopted.copy(conditions = adopted.conditions :+ GoalPhaseAcceptanceBoundary.Condition(
        "release-source-coherence", "phase", true,
        "After phase dependency validation, declared C source and its next publication retain the qualified dependency continuation"))))
    Files.writeString(scenario.root.resolve("goal-plan.json"), plan.asJson.noSpaces)
    val selected = take(org.simplemodeling.textus.smworkflow.preparation.NativeGoalStartInput.prepareC(Map(
      "input" -> scenario.root.resolve("goal-input.json").toUri.toASCIIString,
      "plan" -> scenario.root.resolve("goal-plan.json").toUri.toASCIIString, "selection-id" -> "native-goal-selection",
      "participant" -> "participant", "origin" -> "ExplicitHuman", "idempotency-key" -> "native-specification-input",
      "selection-collection" -> "entity:smworkflow.cli.goal_selection", "plan-collection" -> "entity:smworkflow.cli.goal_plans"),
      reference => Consequence(Files.readString(Path.of(java.net.URI.create(reference))))))
    Files.writeString(scenario.root.resolve("selected-input.json"), take(selected.toJsonStringC))
    val host = _host(scenario)
    val check = GoalPhaseNativeValidation.Check("test", 1, WorkflowSbtValidation.Kind.TestOnly)
    val phasecheck = if (_failed_release_history(scenario)) check.copy(ordinal = 2) else check
    val goal = host.goal.copy(candidatePaths = paths, stepValidation = host.goal.stepValidation.copy(checks = Vector(check)),
      phaseValidation = host.goal.phaseValidation.copy(checks = Vector(phasecheck)))
    val git = TypedGitHostPlanFixture.plan(org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms.Target(
      primary, host.target.localBranch, host.target.remote, host.target.remoteBranch, host.target.endpoint),
      kinds = TypedGitHostPlanFixture.observations ++ Vector(GitOperationKind.IndexTree, GitOperationKind.PendingPaths,
        GitOperationKind.PendingDiff, GitOperationKind.CommitMetadata), goalPaths = paths,
      goalMessages = Vector("Complete selected Step", "Complete accepted Release FIX"),
      goalSelections = Vector(paths, paths.filterNot(_ == "value.txt")))
    val qualifiedgit = git.mapObject(_.add("gitPrograms", _array(git, "gitPrograms").map(program =>
      program.mapObject(_.add("limits", program.hcursor.downField("limits").focus.get
        .mapObject(_.add("stdoutBytes", 1048576L.asJson))))).asJson))
    val stepplan = TypedSbtHostPlanFixture.plan(scenario.root, TypedSbtHostPlanFixture.normalExecutable, Vector(1), Some(qualifiedgit),
      executionTimeoutMillis = 3600000L, operation = SbtOperation.TestOnly,
      testClasses = Vector("org.simplemodeling.textus.smworkflow.admission.GoalPhaseDependencyValidationSpec", "org.simplemodeling.textus.smworkflow.releasefixture.ProductCheckSpec"),
      stdoutBytes = 2097152, stderrBytes = 131072)
    val process = if (!_failed_release_history(scenario)) stepplan else {
      val phaseplan = TypedSbtHostPlanFixture.plan(scenario.root, TypedSbtHostPlanFixture.normalExecutable, Vector(2),
        executionTimeoutMillis = 3600000L, operation = SbtOperation.TestOnly,
        testClasses = Vector("org.simplemodeling.textus.smworkflow.releasefixture.PhaseHistoryCheckSpec"),
        stdoutBytes = 2097152, stderrBytes = 131072)
      val programs = _array(stepplan, "sbtPrograms") ++ _array(phaseplan, "sbtPrograms")
      val capabilities = (_array(stepplan, "gitPrograms") ++ programs).map(_.hcursor.get[Json]("capability").toOption.get).distinct
      stepplan.mapObject(_.add("sbtPrograms", programs.asJson)
        .add("workspaces", (_array(stepplan, "workspaces") ++ _array(phaseplan, "workspaces")).asJson)
        .add("workspaceGrants", (_array(stepplan, "workspaceGrants") ++ _array(phaseplan, "workspaceGrants")).asJson)
        .add("grants", Json.arr(Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson,
          "capabilities" -> capabilities.asJson))))
    }
    _write_config(scenario, _config(scenario).mapObject(_.add("sm-workflow.runtime.definition",
      ThreeWorkflowRuntimeParameters.encode(host.copy(goal = goal)).noSpaces.asJson)
      .add("textus.subsystem.process-execution.plan", process.noSpaces.asJson)))
    _git(primary, Vector("add", "--all", "--", "."))
    _git(primary, Vector("commit", "--no-verify", "--no-gpg-sign", "-m", "Specification operational C source baseline"))
    _source_integrated(scenario) shouldBe false
  }

  private def _prepare_dependency(scenario: Scope): D.Binding = {
    val root = scenario.root.resolve("dependency")
    TypedSbtHostPlanFixture.build(root, true)
    val repository = root.resolve("repository").toRealPath()
    if (_coherent(scenario)) {
      val product = repository.resolve("src/test/scala/ProductCheck.scala")
      val previous = Files.readString(product)
      val marker = "    println(\"PRODUCT_CHECK_PASSED\")"
      require(previous.contains(marker))
      Files.writeString(product, previous.replace(marker,
        "    val help = root.resolve(\"release-help.txt\")\n" +
        "    if (java.nio.file.Files.exists(help)) assert(java.nio.file.Files.readString(help) == " +
        "\"New separately approved Help purpose\\n\", \"actual new Help input differs\")\n" + marker))
    }
    Files.writeString(repository.resolve("value.txt"), "initial\n")
    _git(repository, Vector("init", "-b", "main"))
    _git(repository, Vector("add", "--", "."))
    _git(repository, Vector("commit", "--no-verify", "--no-gpg-sign", "-m", "Specification dependency base"))
    val binding = D.Binding("release-old-step-validation", 1, "step-1",
      GoalPhaseDependencyScope.Component("org.simplemodeling.control", scenario.plan.repository, "sm-workflow-repository"),
      GoalPhaseDependencyScope.Component("org.goldenport.cncf", "goldenport-cncf", "release-dependency-source"),
      repository.toString, "main", Vector("value.txt"),
      GoalPhaseOrderedProgression.Snapshot(_git(repository, Vector("rev-parse", "HEAD")).trim,
        _git(repository, Vector("rev-parse", "HEAD^{tree}")).trim),
      "SPECIFICATION-OWNED actual initial base", Vector(GoalPhaseNativeValidation.Check("old-cncf-test", 3)))
    val config = _config(scenario)
    val primary = parse(config.hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
    val processes = TypedSbtHostPlanFixture.dependencyPlan(primary, root, TypedSbtHostPlanFixture.normalExecutable, binding)
    val host = _host(scenario)
    _write_config(scenario, config.mapObject(_.add("sm-workflow.runtime.definition",
      ThreeWorkflowRuntimeParameters.encode(host.copy(goal = host.goal.copy(dependencyValidation = Some(binding)))).noSpaces.asJson)
      .add("textus.subsystem.process-execution.plan", processes.noSpaces.asJson)))
    val build = Path.of(scenario.host.target.root).resolve("build.sbt")
    if (!_coherent(scenario)) Files.writeString(build, "\norganization := \"org.example.smworkflow.release-dependency-spec\"\n" +
      "name := \"" + scenario.root.getFileName.toString + "\"\nversion := \"0.0.1-SNAPSHOT\"\n" +
      "publishLocal := { val actual = publishLocal.value; IO.append(file(\"../publication-count.txt\"), \"published\\n\"); actual }\n",
      StandardOpenOption.APPEND)
    val primaryroot = Path.of(scenario.host.target.root)
    if (!_coherent(scenario)) {
      _git(primaryroot, Vector("add", "--", "build.sbt"))
      _git(primaryroot, Vector("commit", "--no-verify", "--no-gpg-sign", "-m", "Specification publication configuration"))
    }
    binding
  }

  /** Capture evidence only; native storage is never changed by this helper. */
  def native(scenario: Scope): Vector[Json] = {
    val connection = new org.sqlite.JDBC().connect("jdbc:sqlite:" + scenario.root.resolve("workflow.sqlite").toUri.toASCIIString +
      "?mode=ro", new java.util.Properties())
    try {
      val statement = connection.createStatement()
      try {
        val tables = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")
        val names = Vector.newBuilder[String]
        try { while (tables.next()) names += tables.getString(1) } finally tables.close()
        names.result().flatMap { name =>
          val rows = statement.executeQuery("SELECT * FROM \"" + name.replace("\"", "\"\"") + "\"")
          val values = Vector.newBuilder[Json]
          try { while (rows.next()) (1 to rows.getMetaData.getColumnCount).foreach { column =>
            Option(rows.getString(column)).foreach { raw =>
              if (raw.startsWith("cncf.workflow-json.v1:"))
                values += parse(raw.stripPrefix("cncf.workflow-json.v1:")).toOption.get
              else if (rows.getMetaData.getColumnName(column) == "localPublicationPayload")
                values += parse(raw).toOption.get
            }
          } } finally rows.close()
          values.result()
        }
      } finally statement.close()
    } finally connection.close()
  }
  private def _instance(values: Vector[Json], session: String): Json = values.filter(v =>
    v.hcursor.get[String]("schemaVersion").toOption.contains("cncf.workflow-instance-record.v1") &&
      v.hcursor.get[String]("identity").toOption.contains(session)) match {
        case Vector(value) => value
        case _ => fail("Missing or ambiguous native instance")
      }
  private def _issued_request(scenario: Scope, value: V.GoalPhaseAdvanceResult): GoalPhaseNativeWork.Request = {
    val context = value.issuedWorkContext.getOrElse(fail("Missing actual issued work context"))
    val matches = native(scenario).filter { record =>
      val request = record.hcursor.downField("request")
      record.hcursor.get[String]("schemaVersion").toOption.contains(org.goldenport.cncf.workflow.WorkflowProtocolV1.schemaVersion) &&
        record.hcursor.get[String]("kind").toOption.contains("WORK_ORDER") &&
        request.get[String]("runId").toOption.contains(context.runId) &&
        request.get[String]("continuationId").toOption.contains(context.continuationId) &&
        request.get[String]("expectedRevision").toOption.contains(context.expectedRevision)
    }
    val order = matches match {
      case Vector(record) => record
      case _ => fail("Missing or ambiguous canonical WorkOrder for the exact issued run, continuation and revision")
    }
    val input = order.hcursor.downField("request").downField("input")
    input.get[String]("typeIdentity") shouldBe Right(GoalPhaseNativeWork.inputCodec.typeIdentity)
    val payload = input.downField("value").focus.getOrElse(fail("Canonical WorkOrder input value is missing"))
    val decoded = GoalPhaseNativeWork.inputCodec.decode(payload).fold(error => fail(error), identity)
    decoded.instanceIdentity shouldBe value.sessionId
    decoded.kind shouldBe value.work.get.kind
    decoded
  }
  private def _generation(scenario: Scope, value: V.GoalPhaseAdvanceResult): Long =
    _issued_request(scenario, value).generation
  private def _causes(values: Vector[Json], session: String): Vector[String] =
    _instance(values, session).hcursor.get[Vector[Json]]("history").toOption.get.map(_.hcursor.get[String]("causation").toOption.get)
  private def _checks(root: Path): Vector[String] = {
    val path = root.resolve("sbt-executions.txt")
    if (Files.exists(path)) Files.readString(path).linesIterator.toVector else Vector.empty
  }
  private def _publication(scenario: Scope): String = Files.readString(scenario.root.resolve("publication-count.txt"))
  private def _pending_index_program(scenario: Scope): Json = {
    val target = _host(scenario).target
    val pending = TypedGitHostPlanFixture.plan(org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms.Target(
      Path.of(target.root), target.localBranch, target.remote, target.remoteBranch, target.endpoint),
      kinds = Vector(GitOperationKind.CheckPendingIndex))
    _array(pending, "gitPrograms") match {
      case Vector(program) => program
      case _ => fail("The fixed primary pending-index fixture must generate exactly one typed declaration")
    }
  }
  private def _pending_index_entries(programs: Vector[Json], pending: Json): Vector[Json] = programs.filter(program =>
    program.hcursor.get[String]("identity").toOption == pending.hcursor.get[String]("identity").toOption ||
      program.hcursor.get[String]("capability").toOption == pending.hcursor.get[String]("capability").toOption ||
      (program.hcursor.get[String]("target").toOption.contains(TypedGitHostPlanFixture.workspace) &&
        program.hcursor.get[String]("operation").toOption.contains(GitOperationKind.CheckPendingIndex.wireName)))
  private def _prepare_pending_index(scenario: Scope): Unit = {
    require(_prior_release_history(scenario), "Only the pre-adopted historical Release fixture needs this early prerequisite")
    val config = _config(scenario)
    val processes = parse(config.hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
    val pending = _pending_index_program(scenario)
    val programs = _array(processes, "gitPrograms")
    require(_pending_index_entries(programs, pending).isEmpty, "The fixed primary pending-index declaration must be fresh")
    val capability = pending.hcursor.get[Json]("capability").toOption.get
    val owner = SmWorkflowComponent.componentId.name
    val grants = _array(processes, "grants")
    require(grants.count(_.hcursor.get[String]("componentId").toOption.contains(owner)) == 1,
      "The existing primary Component grant must be unambiguous")
    val granted = grants.map { grant =>
      if (!grant.hcursor.get[String]("componentId").toOption.contains(owner)) grant
      else {
        val capabilities = _array(grant, "capabilities")
        require(!capabilities.contains(capability), "The fixed pending-index capability must be fresh")
        grant.mapObject(_.add("capabilities", (capabilities :+ capability).asJson))
      }
    }
    val selected = processes.mapObject(_.add("gitPrograms", (programs :+ pending).asJson).add("grants", granted.asJson))
    _write_config(scenario, config.mapObject(_.add("textus.subsystem.process-execution.plan", selected.noSpaces.asJson)))
  }
  private def _publication_config(scenario: Scope): Unit = {
    val config = _config(scenario)
    val processes = parse(config.hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
    val existinggit = _array(processes, "gitPrograms")
    val pending = _pending_index_program(scenario)
    val git = if (_prior_release_history(scenario)) {
      require(_pending_index_entries(existinggit, pending) == Vector(pending),
        "Historical Release must retain exactly its original fixed primary pending-index declaration")
      val grants = _array(processes, "grants").filter(_.hcursor.get[String]("componentId").toOption
        .contains(SmWorkflowComponent.componentId.name))
      require(grants.size == 1 && _array(grants.head, "capabilities").count(_ == pending.hcursor.get[Json]("capability").toOption.get) == 1,
        "Historical Release must retain its exact pending-index capability grant")
      existinggit
    } else {
      require(!existinggit.exists(program => program.hcursor.get[String]("target").toOption.contains(TypedGitHostPlanFixture.workspace) &&
        program.hcursor.get[String]("operation").toOption.contains(GitOperationKind.CheckPendingIndex.wireName)),
        "The primary pending-index check must have exactly one typed declaration")
      existinggit :+ pending
    }
    val publication = _array(TypedSbtHostPlanFixture.plan(scenario.root, TypedSbtHostPlanFixture.normalExecutable, Vector(12),
      operation = SbtOperation.PublishLocal, executionTimeoutMillis = if (_coherent(scenario)) 5400000L else 180000L,
      stdoutBytes = 2097152, stderrBytes = 131072), "sbtPrograms").head.mapObject(_.add("target", "sm-workflow-repository".asJson)
        .add("identity", "release-primary-publication".asJson).add("capability", "release-primary-publication".asJson))
    val programs = _array(processes, "sbtPrograms") :+ publication
    val owner = SmWorkflowComponent.componentId.name
    val selected = processes.mapObject(_.add("gitPrograms", git.asJson).add("sbtPrograms", programs.asJson).add("grants", Json.arr(Json.obj(
      "componentId" -> owner.asJson, "capabilities" -> (git ++ programs)
        .map(_.hcursor.get[Json]("capability").toOption.get).distinct.asJson))))
    val collection = ThreeWorkflowNormalHostFixture.collection("release_publications")
    val storage = parse(config.hcursor.get[String]("textus.subsystem.storage.plan").toOption.get).toOption.get
    val granted = storage.mapObject(_.add("records", (_array(storage, "records") :+ Json.obj(
      "componentId" -> owner.asJson, "storeName" -> "workflow".asJson, "collection" -> collection.asJson)).asJson))
    _write_config(scenario, config.mapObject(_.add("textus.subsystem.process-execution.plan", selected.noSpaces.asJson)
      .add("textus.subsystem.storage.plan", granted.noSpaces.asJson).add("sm-workflow.local-publication", Json.obj(
        "storeName" -> "workflow".asJson, "collection" -> collection.asJson, "targets" -> Json.arr(Json.obj(
          "identity" -> "release-primary".asJson,
          "component" -> (if (_coherent(scenario)) SmWorkflowComponent.componentId.name else "specification.primary").asJson,
          "workspace" -> "sm-workflow-repository".asJson, "beforeChecks" -> true.asJson))).noSpaces.asJson)))
  }

  def selectPhase(frontier: Frontier, grant: Boolean = true, binding: Option[D.Binding] = None): Unit = {
    val scenario = frontier.scenario
    val selected = binding.getOrElse(frontier.binding)
    val config = _config(scenario)
    val host = _host(scenario)
    val processes = parse(config.hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
    val old = _array(processes, "gitPrograms").find(p => p.hcursor.get[String]("target").toOption.contains(selected.dependency.workspace) &&
      p.hcursor.get[String]("operation").toOption.contains("stage-selected-all")).get
    // A fixed specification-owned Git shim observes real argv, then execs the
    // ordinary Git executable. Only this base definition selects it; it is not
    // an alternate process route or a selected-path claim in native storage.
    val observer = scenario.root.resolve("release-stage-git")
    if (!Files.exists(observer)) {
      val log = "'" + scenario.root.resolve("release-stage-argv").toString.replace("'", "'\"'\"'") + "'"
      Files.writeString(observer, "#!/bin/sh\nprintf '%s\\0' \"$@\" >> " + log + "\nexec /usr/bin/git \"$@\"\n", StandardOpenOption.CREATE_NEW)
      observer.toFile.setExecutable(true, false) shouldBe true
    }
    val base = old.mapObject(_.add("operation", "stage-full-state".asJson).add("selectionId", Json.Null)
      .add("selectedPath", Json.Null).add("identity", "release-dependency-stage-base".asJson)
      .add("capability", "release-dependency-stage-base".asJson).add("gitExecutable", observer.toString.asJson))
    // The exact existing workspace Test program can serve the explicitly new
    // business check identity; its capability, definition and old receipts stay.
    val sbt = _array(processes, "sbtPrograms")
    val git = _array(processes, "gitPrograms").filterNot(_.hcursor.get[String]("identity").toOption.contains("release-dependency-stage-base")) :+ base
    val capabilities = (git ++ sbt).map(_.hcursor.get[Json]("capability").toOption.get)
      .filterNot(value => !grant && value.asString.contains("release-dependency-stage-base"))
    val process = processes.mapObject(_.add("gitPrograms", git.asJson).add("sbtPrograms", sbt.asJson)
      .add("grants", Json.arr(Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson, "capabilities" -> capabilities.asJson))))
    _write_config(scenario, config.mapObject(_.add("sm-workflow.runtime.definition",
      ThreeWorkflowRuntimeParameters.encode(host.copy(goal = host.goal.copy(phaseDependencyValidation = Some(selected)))).noSpaces.asJson)
      .add("textus.subsystem.process-execution.plan", process.noSpaces.asJson)))
  }

  private def _validation_fact(scenario: Scope, request: V.GoalPhaseAdvanceResult): Json = {
    val validation = request.validation.get
    val reference = request.sessionId + ":ordered-validation:" + validation.sourceRevision + ":validation"
    val matches = native(scenario).filter(value => value.hcursor.get[String]("schemaVersion").toOption
      .contains("cncf.workflow-mechanical-transition.v1") && value.hcursor.downField("reference")
      .get[String]("identity").toOption.contains(reference))
    matches.size shouldBe 1
    val payload = GoalPhaseNativeValidation.codec.decode(matches.head.hcursor.downField("payload").downField("value").focus.get)
      .fold(error => fail(error), identity)
    payload.sourceRevision shouldBe validation.sourceRevision
    payload.outcome shouldBe validation.outcome
    payload.candidate.head shouldBe validation.candidate.head
    payload.candidate.tree shouldBe validation.candidate.tree
    payload.dependency shouldBe None
    payload.dependencySource shouldBe None
    matches.head
  }
  private def _complete_failed_release_history(scenario: Scope, cli: Caller, first: F.Result,
    session: String): (V.GoalPhaseAdvanceResult, F.Result) = {
    var current = first
    var requests = Vector.empty[V.GoalPhaseAdvanceResult]
    var facts = Vector.empty[Json]
    val usage = Path.of(scenario.host.target.root).resolve("docs/usage.md")
    val firstline = "The accepted input is exactly expected followed by LF.\n"
    (1 to 3).foreach { count =>
      val repair = current.business[V.GoalPhaseAdvanceResult]
      repair.work.get.kind shouldBe "RepairStep"
      repair.work.get.stepIdentity shouldBe None
      repair.work.get.review shouldBe None
      repair.repairProgress.get.limit shouldBe 10
      repair.repairProgress.get.ordinaryCount shouldBe count
      repair.repairProgress.get.automaticCount shouldBe count
      val failure = repair.work.get.checkFailure.get
      failure.initialPhaseRepair should not be empty
      failure.validation shouldBe repair.validation.get
      failure.validation.outcome shouldBe Some(if (count == 1) "failed" else "repair-required")
      failure.validation.scopeIdentity shouldBe "phase:" + scenario.plan.phase
      failure.validation.checks.last.termination shouldBe "exited"
      failure.validation.checks.last.exitCode.exists(_ != 0) shouldBe true
      failure.validation.checks.last.sbt.get.kind shouldBe "test-only"
      val fact = _validation_fact(scenario, repair)
      val payload = GoalPhaseNativeValidation.codec.decode(fact.hcursor.downField("payload").downField("value").focus.get)
        .fold(error => fail(error), identity)
      GoalPhaseNativeValidation.repairable(payload) shouldBe true
      requests.headOption.foreach { original =>
        repair.work.get.selectedPlan shouldBe original.work.get.selectedPlan
        repair.work.get.originalBase shouldBe original.work.get.originalBase
        repair.work.get.completed shouldBe original.work.get.completed
        failure.initialPhaseRepair shouldBe original.work.get.checkFailure.get.initialPhaseRepair
      }
      requests.lastOption.foreach { previous =>
        _generation(scenario, repair) should be > _generation(scenario, previous)
        failure.validation.sourceRevision.toLong should be > previous.validation.get.sourceRevision.toLong
      }
      repair.work.get.completed.map(_.step) shouldBe Vector("step-1")
      requests :+= repair
      facts :+= fact
      val fixed = V.GoalPhaseIssueReport(repair.work.get.issues.get.issues.filter(_.status != "RESOLVED").map(issue =>
        V.GoalPhaseIssueDisposition(issue.identity, "FIXED", "SPECIFICATION-OWNED actual docs source correction for issued batch " + count, None)))
      Files.writeString(usage, firstline + (if (count >= 2) "Line endings are LF.\n" else "") +
        (if (count >= 3) "Input has one record.\n" else ""))
      cli.submit(repair, cli.result(repair, "completed", Some(fixed)), "failed-history-source-repair-" + count)
      val checks = _checks(scenario.root)
      current = cli.advance(session)
      _checks(scenario.root).size shouldBe checks.size + 1
    }
    val passed = current.business[V.GoalPhaseAdvanceResult]
    passed.work.get.kind shouldBe "ReviewPhase"
    passed.validation.get.outcome shouldBe Some("passed")
    passed.repairProgress shouldBe requests.last.repairProgress
    _generation(scenario, passed) shouldBe _generation(scenario, requests.last)
    passed.work.get.selectedPlan shouldBe requests.head.work.get.selectedPlan
    passed.work.get.originalBase shouldBe requests.head.work.get.originalBase
    passed.work.get.completed shouldBe requests.head.work.get.completed
    val passedfact = _validation_fact(scenario, passed)
    _causes(native(scenario), session).count(_ == GoalPhaseInitialPhaseCheckRepair.causation) shouldBe 1
    _save(scenario, "failed-release-completed-history", Json.obj("failures" -> facts.asJson,
      "repairs" -> requests.map(_json).asJson, "passed" -> passedfact,
      "review" -> _json(passed).asJson, "primaryChecks" -> _checks(scenario.root).asJson).noSpaces)
    (requests.last, current)
  }

  def setup(name: String, installation: Option[Installation], coherentSource: Boolean = false,
    priorReleaseHistory: Boolean = false, failedReleaseHistory: Boolean = false): Frontier = {
    require(!failedReleaseHistory || (coherentSource && priorReleaseHistory),
      "Failed Release history requires the explicit coherent source and prior-history fixture contracts")
    val original = F.freshReleaseFix(name, TypedSbtHostPlanFixture.normalExecutable, requiredCondition = true,
      repairLimit = if (failedReleaseHistory) 10 else if (priorReleaseHistory) 3 else 2)
    val scenario = Scope(original.root, original.host, original.plan, Some(original))
    if (failedReleaseHistory) Files.writeString(scenario.root.resolve("failed-release-history"),
      "SPECIFICATION-OWNED pre-adopted failed Release history\n", StandardOpenOption.CREATE_NEW)
    if (priorReleaseHistory) _prepare_release_history(scenario)
    if (coherentSource) _prepare_source(scenario)
    val old = _prepare_dependency(scenario)
    if (priorReleaseHistory) _prepare_pending_index(scenario)
    val cli = new Caller(scenario, installation)
    val session = cli.goal("startGoalPhase", Vector("--input", scenario.input.noSpaces)).business[V.GoalPhaseStartResult].sessionId
    val implementation = cli.advance(session).business[V.GoalPhaseAdvanceResult]
    val proposal = _proposal(old, "old-step-proposal")
    cli.submit(implementation, cli.result(implementation, "decision-needed").mapObject(_.add(
      "dependencyProposal", proposal.asJson)), "old-step-proposal-result")
    cli.approve(cli.advance(session), "old-step-approval")
    val resumed = cli.advance(session).business[V.GoalPhaseAdvanceResult]
    Files.writeString(Path.of(scenario.host.target.root).resolve("value.txt"), "expected\n")
    Files.createDirectories(Path.of(scenario.host.target.root).resolve("docs"))
    Files.writeString(Path.of(scenario.host.target.root).resolve("docs/usage.md"), "The accepted input is arbitrary text.\n")
    Files.writeString(Path.of(old.root).resolve("value.txt"), "expected\n")
    cli.submit(resumed, cli.result(resumed, "completed").mapObject(_.add("dependencyValidation",
      _selection(old, "old-step-proposal", "old-step-approval").asJson)), "old-step-completed")
    val review = cli.advance(session)
    val step = review.business[V.GoalPhaseAdvanceResult]
    step.work.get.kind shouldBe "ReviewStep"
    val captured = take(GoalPhaseNativeMetadata.normalizePublicJson(NativeCallerExecutionFixture.json(
      step.work.get.review.get.dependencyValidations.last)).flatMap(_.hcursor.get[D.Capture]("capture").left.map(_.message))
      .fold(Consequence.stateConflict(_), Consequence.success))
    cli.review(review, "accept", "old-step-independent-review")
    cli.authority(cli.advance(session), "old-step-authority")
    val phase = cli.advance(session)
    val failedhistory = if (failedReleaseHistory) Some(_complete_failed_release_history(scenario, cli, phase, session)) else None
    if (!failedReleaseHistory) {
      phase.business[V.GoalPhaseAdvanceResult].work.get.kind shouldBe "ReviewPhase"
      cli.review(phase, "repair", "old-phase-independent-repair-review")
    }
    val firstrepair = failedhistory.map(_._1).getOrElse(cli.advance(session).business[V.GoalPhaseAdvanceResult])
    val repair = if (priorReleaseHistory) {
      firstrepair.work.get.kind shouldBe "RepairStep"
      firstrepair.work.get.stepIdentity shouldBe None
      val earlier = failedhistory.map(_._2).getOrElse {
        val fixed = V.GoalPhaseIssueReport(firstrepair.work.get.issues.get.issues.filter(_.status != "RESOLVED").map(i =>
          V.GoalPhaseIssueDisposition(i.identity, "FIXED", "SPECIFICATION-OWNED actual first-sentence correction only", None)))
        Files.writeString(Path.of(scenario.host.target.root).resolve("docs/usage.md"),
          "The accepted input is exactly expected followed by LF.\n")
        cli.submit(firstrepair, cli.result(firstrepair, "completed", Some(fixed)), "first-release-sentence-repair")
        cli.advance(session)
      }
      val request = earlier.business[V.GoalPhaseAdvanceResult]
      request.work.get.kind shouldBe "ReviewPhase"
      request.repairProgress shouldBe firstrepair.repairProgress
      val values = native(scenario)
      val issued = _issued_request(scenario, request)
      val passed = values.filter(v => v.hcursor.get[String]("schemaVersion").toOption
        .contains("cncf.workflow-mechanical-transition.v1") && v.hcursor.downField("reference")
        .get[String]("identity").toOption == issued.checksReceipt)
      passed.size shouldBe 1
      val validation = GoalPhaseNativeValidation.codec.decode(passed.head.hcursor.downField("payload").downField("value").focus.get)
        .fold(error => fail(error), identity)
      validation.outcome shouldBe Some("passed")
      issued.candidate shouldBe Some(validation.candidate)
      validation.step shouldBe "phase:" + scenario.plan.phase
      validation.dependency shouldBe None
      validation.dependencySource shouldBe None
      validation.receipts.size shouldBe validation.selected.size
      GoalPhaseNativeValidation.successful(validation.receipts, validation.selected) shouldBe true
      val historical = values.filter(v => v.hcursor.get[String]("schemaVersion").toOption.exists(
        Set("cncf.workflow-mechanical-transition.v1", "cncf.workflow-completion-record.v1", "cncf.workflow-completion-record.v2")) ||
        v.hcursor.get[String]("kind").toOption.contains("CONTINUATION_RESULT"))
      _save(scenario, "prior-release-completed-history", Json.obj("native" -> historical.asJson,
        "passed" -> passed.asJson, "review" -> _json(request).asJson,
        "primaryChecks" -> _checks(scenario.root).asJson, "repair" -> _json(firstrepair).asJson).noSpaces)
      val resolved = V.GoalPhaseIssueReport(request.work.get.issues.get.issues
        .filter(i => i.source == "REVIEW" || i.status != "RESOLVED").map(i =>
          V.GoalPhaseIssueDisposition(i.identity, "RESOLVED", "SPECIFICATION-OWNED independent confirmation of the corrected first sentence", None)))
      val report = cli.result(request, "repair", Some(resolved))
      report.hcursor.get[Vector[String]]("findings").toOption.get shouldBe Vector(
        "After the first Release repair, docs/usage.md must contain The encoding is UTF-8. as its own LF line")
      cli.review(earlier, "repair", "prior-release-utf8-independent-repair-review", Some(resolved))
      val next = cli.advance(session).business[V.GoalPhaseAdvanceResult]
      next.repairProgress.get.ordinaryCount shouldBe firstrepair.repairProgress.get.ordinaryCount + 1
      next.repairProgress.get.automaticCount shouldBe firstrepair.repairProgress.get.automaticCount + 1
      next
    } else firstrepair
    repair.work.get.kind shouldBe "RepairStep"
    repair.work.get.stepIdentity shouldBe None
    val binding = old.copy(identity = "release-new-purpose-validation", step = "phase:" + scenario.plan.phase,
      paths = Vector("release-help.txt"), checks = Vector(GoalPhaseNativeValidation.Check("release-cncf-test", 5)))
    val pendingissues = V.GoalPhaseIssueReport(repair.work.get.issues.get.issues.filter(_.status != "RESOLVED").map(i =>
      V.GoalPhaseIssueDisposition(i.identity, "UNRESOLVED", "SPECIFICATION-OWNED usage correction remains pending the actual dependency decision", None)))
    cli.submit(repair, cli.result(repair, "decision-needed", Some(pendingissues)).mapObject(_.add(
      "dependencyProposal", _proposal(binding, "new-phase-proposal").asJson)), "new-phase-proposal-result")
    cli.approve(cli.advance(session), "new-phase-approval")
    val assigned = cli.advance(session).business[V.GoalPhaseAdvanceResult]
    assigned.work.get.kind shouldBe "RepairStep"
    val fixed = V.GoalPhaseIssueReport(assigned.work.get.issues.get.issues.filter(_.status != "RESOLVED").map(i =>
      V.GoalPhaseIssueDisposition(i.identity, "FIXED", "SPECIFICATION-OWNED actual usage correction and approved separate dependency purpose", None)))
    Files.writeString(Path.of(scenario.host.target.root).resolve("docs/usage.md"),
      "The accepted input is exactly expected followed by LF.\n" +
        (if (failedReleaseHistory) "Line endings are LF.\nInput has one record.\n" else "") +
        (if (priorReleaseHistory) "The encoding is UTF-8.\n" else ""))
    Files.writeString(Path.of(old.root).resolve(binding.paths.head), "New separately approved Help purpose\n")
    cli.submit(assigned, cli.result(assigned, "completed", Some(fixed)), "accepted-release-repair")
    _publication_config(scenario)
    val failed = cli.advance(session)
    withClue(failed.invocation.logs) { failed.exitCode should not be 0 }
    Files.readString(failed.invocation.stderr) should include("GoalPhase dependency validation:")
    _publication(scenario) shouldBe "published\n"
    val initial = Frontier(scenario, session, implementation, assigned, binding, captured, Json.Null)
    initial.copy(snapshot = snapshot(initial))
  }

  def snapshot(frontier: Frontier): Json = {
    val scenario = frontier.scenario
    val values = native(scenario)
    Json.obj("native" -> values.asJson, "instance" -> _instance(values, frontier.session),
      "causes" -> _causes(values, frontier.session).asJson,
      "primaryHead" -> scenario.candidateHead.asJson, "primaryIndex" -> scenario.candidateIndexDiff.asJson,
      "oldDependencyIndex" -> _git(Path.of(frontier.binding.root), Vector("diff", "--cached", "--binary", "--", "value.txt")).asJson,
      "oldDependencyWorktree" -> Files.readString(Path.of(frontier.binding.root).resolve("value.txt")).asJson,
      "primaryChecks" -> _checks(scenario.root).asJson, "dependencyChecks" -> _checks(scenario.root.resolve("dependency")).asJson,
      "stageArgv" -> Option.when(Files.exists(scenario.root.resolve("release-stage-argv")))(
        Files.readString(scenario.root.resolve("release-stage-argv"))).asJson,
      "publication" -> _publication(scenario).asJson)
  }

  private def _unchanged(frontier: Frontier): Unit = {
    val current = snapshot(frontier)
    current shouldBe frontier.snapshot
  }
  private def _catalog_output_config(scenario: Scope): Unit = {
    val path = "src/main/catalog/car/org/simplemodeling/textus/textus-sm-workflow.yaml"
    val config = _config(scenario)
    val processes = parse(config.hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
    val programs = _array(processes, "gitPrograms")
    require(!programs.exists(_.hcursor.get[String]("selectionId").toOption.contains("publication-catalog")))
    val original = programs.find(p => p.hcursor.get[String]("target").toOption.contains(TypedGitHostPlanFixture.workspace) &&
      p.hcursor.get[String]("operation").toOption.contains(GitOperationKind.TreeEntries.wireName)).get
    val capability = "release-publication-catalog"
    val catalog = original.mapObject(_.add("selectionId", "publication-catalog".asJson)
      .add("selectedPath", Json.Null).add("selectedPaths", Vector(path).asJson)
      .add("identity", capability.asJson).add("capability", capability.asJson))
    require(_array(processes, "workspaceGrants").exists(g =>
      g.hcursor.get[String]("componentId").toOption.contains(SmWorkflowComponent.componentId.name) &&
        g.hcursor.get[String]("workspaceId").toOption.contains(TypedGitHostPlanFixture.workspace) &&
        g.hcursor.get[String]("access").toOption.contains("manage")), "The ordinary primary workspace must admit File reads")
    val grants = _array(processes, "grants").map { grant =>
      if (grant.hcursor.get[String]("componentId").toOption.contains(SmWorkflowComponent.componentId.name)) {
        val capabilities = _array(grant, "capabilities")
        require(!capabilities.contains(capability.asJson))
        grant.mapObject(_.add("capabilities", (capabilities :+ capability.asJson).asJson))
      } else grant
    }
    require(grants.exists(_.hcursor.get[String]("componentId").toOption.contains(SmWorkflowComponent.componentId.name)))
    val selected = processes.mapObject(_.add("gitPrograms", (programs :+ catalog).asJson).add("grants", grants.asJson))
    val policy = parse(config.hcursor.get[String]("sm-workflow.local-publication").toOption.get).toOption.get
    require(policy.hcursor.downField("catalogOutputs").focus.isEmpty)
    val bound = policy.mapObject(_.add("catalogOutputs", Json.arr(Json.obj("targetIdentity" -> "release-primary".asJson,
      "path" -> path.asJson, "selection" -> "publication-catalog".asJson))))
    _write_config(scenario, config.mapObject(_.add("textus.subsystem.process-execution.plan", selected.noSpaces.asJson)
      .add("sm-workflow.local-publication", bound.noSpaces.asJson)))
  }
  private def _refuse(frontier: Frontier, cli: Caller): Unit = {
    val before = snapshot(frontier)
    val refused = cli.advance(frontier.session)
    withClue(refused.invocation.logs) { refused.exitCode should not be 0 }
    snapshot(frontier) shouldBe before
  }
  private def _preserved_release_history(frontier: Frontier): Unit = if (_prior_release_history(frontier.scenario)) {
    val scenario = frontier.scenario
    val saved = _read(scenario.root.resolve("prior-release-completed-history.json"))
    val values = native(scenario)
    saved.hcursor.get[Vector[Json]]("native").toOption.get.foreach(fact => values should contain(fact))
    saved.hcursor.get[Vector[Json]]("passed").toOption.get.foreach(fact => values should contain(fact))
    val checks = saved.hcursor.get[Vector[String]]("primaryChecks").toOption.get
    _checks(scenario.root).take(checks.size) shouldBe checks
    def _saved_work_(key: String): V.GoalPhaseAdvanceResult = {
      val record = take(new org.goldenport.record.io.RecordDecoder().json(saved.hcursor.get[String](key).toOption.get))
      take(summon[org.goldenport.convert.ValueReader[V.GoalPhaseAdvanceResult]].readC(record))
    }
    val review = _saved_work_("review")
    val repair = _saved_work_("repair")
    review.repairProgress shouldBe repair.repairProgress
    frontier.repair.repairProgress.get.ordinaryCount shouldBe repair.repairProgress.get.ordinaryCount + 1
    frontier.repair.repairProgress.get.automaticCount shouldBe repair.repairProgress.get.automaticCount + 1
    frontier.repair.work.get.selectedPlan shouldBe review.work.get.selectedPlan
    frontier.repair.work.get.originalBase shouldBe review.work.get.originalBase
    frontier.repair.work.get.completed shouldBe review.work.get.completed
    if (_failed_release_history(scenario)) {
      val failed = _read(scenario.root.resolve("failed-release-completed-history.json"))
      val failures = failed.hcursor.get[Vector[Json]]("failures").toOption.get
      failures.size shouldBe 3
      failures.zip(Vector("failed", "repair-required", "repair-required")).foreach { (fact, outcome) =>
        values should contain(fact)
        val payload = GoalPhaseNativeValidation.codec.decode(fact.hcursor.downField("payload").downField("value").focus.get)
          .fold(error => fail(error), identity)
        payload.outcome shouldBe Some(outcome)
        GoalPhaseNativeValidation.repairable(payload) shouldBe true
      }
      values should contain(failed.hcursor.downField("passed").focus.get)
      failed.hcursor.get[String]("review") shouldBe saved.hcursor.get[String]("review")
      val failedchecks = failed.hcursor.get[Vector[String]]("primaryChecks").toOption.get
      _checks(scenario.root).take(failedchecks.size) shouldBe failedchecks
      val repairs = failed.hcursor.get[Vector[String]]("repairs").toOption.get.map { text =>
        val record = take(new org.goldenport.record.io.RecordDecoder().json(text))
        take(summon[org.goldenport.convert.ValueReader[V.GoalPhaseAdvanceResult]].readC(record))
      }
      repairs.map(_.repairProgress.get.ordinaryCount) shouldBe Vector(1, 2, 3)
      repairs.map(_.repairProgress.get.automaticCount) shouldBe Vector(1, 2, 3)
      repair.repairProgress shouldBe repairs.last.repairProgress
      _causes(values, frontier.session).count(_ == GoalPhaseInitialPhaseCheckRepair.causation) shouldBe 1
    }
  }

  def resume(frontier: Frontier, installation: Option[Installation], negativeChecks: Boolean,
    coherentDelivery: Boolean = false, stopAtDependencyReview: Boolean = false): Unit = {
    require(!stopAtDependencyReview || (_failed_release_history(frontier.scenario) && !coherentDelivery),
      "Only the focused failed-history source case stops at its actual dependency ReviewPhase")
    val cli = new Caller(frontier.scenario, installation)
    _unchanged(frontier)
    _preserved_release_history(frontier)
    if (coherentDelivery) _catalog_output_config(frontier.scenario)
    if (_prior_release_history(frontier.scenario)) {
      selectPhase(frontier)
      val scenario = frontier.scenario
      val configpath = scenario.root.resolve(".textus/config.json")
      val originalconfig = Files.readAllBytes(configpath)
      val host = _host(scenario)
      val policy = host.goal.phaseValidation
      Vector(policy.copy(checks = policy.checks.map(_.copy(identity = "changed-phase-check"))),
        policy.copy(checks = policy.checks.map(_.copy(kind = WorkflowSbtValidation.Kind.Compile)))).foreach { changed =>
        try {
          _write_config(scenario, _config(scenario).mapObject(_.add("sm-workflow.runtime.definition",
            ThreeWorkflowRuntimeParameters.encode(host.copy(goal = host.goal.copy(phaseValidation = changed))).noSpaces.asJson)))
          _refuse(frontier, cli)
        } finally Files.write(configpath, originalconfig)
      }
    }
    if (negativeChecks) {
      selectPhase(frontier, grant = false)
      _refuse(frontier, cli)
      selectPhase(frontier)
      val dependency = Path.of(frontier.binding.root)
      Files.writeString(dependency.resolve("value.txt"), "changed protected old owner\n")
      _refuse(frontier, cli)
      Files.writeString(dependency.resolve("value.txt"), "expected\n")
      Files.writeString(dependency.resolve("unapproved.txt"), "Unapproved source\n")
      _refuse(frontier, cli)
      Files.delete(dependency.resolve("unapproved.txt"))
      Files.move(dependency.resolve("release-help.txt"), dependency.resolve("unapproved-rename.txt"))
      _refuse(frontier, cli)
      Files.move(dependency.resolve("unapproved-rename.txt"), dependency.resolve("release-help.txt"))
      Vector(frontier.binding.copy(root = frontier.scenario.root.toString),
        frontier.binding.copy(base = frontier.binding.base.copy(tree = "f" * 40)),
        frontier.binding.copy(dependency = frontier.binding.dependency.copy(identity = "wrong-component"))).foreach { wrong =>
        selectPhase(frontier, binding = Some(wrong))
        _refuse(frontier, cli)
      }
    }
    selectPhase(frontier)
    val result = cli.advance(frontier.session)
    val request = result.business[V.GoalPhaseAdvanceResult]
    request.work.get.kind shouldBe "ReviewPhase"
    request.sessionId shouldBe frontier.session
    request.work.get.originalBase shouldBe frontier.repair.work.get.originalBase
    request.work.get.selectedPlan shouldBe frontier.repair.work.get.selectedPlan
    request.work.get.completed shouldBe frontier.repair.work.get.completed
    request.repairProgress shouldBe frontier.repair.repairProgress
    val validations = request.work.get.review.get.dependencyValidations
    val capture = take(GoalPhaseNativeMetadata.normalizePublicJson(NativeCallerExecutionFixture.json(validations.last))
      .flatMap(_.hcursor.get[D.Capture]("capture").left.map(_.message)).fold(Consequence.stateConflict(_), Consequence.success))
    capture.step shouldBe frontier.binding.step
    capture.admission.binding shouldBe frontier.binding
    capture.admission.approvals.map(_.proposal.identity) shouldBe Vector("new-phase-proposal")
    capture.admission.acceptedPrefix.get.candidate shouldBe frontier.oldCapture.candidate
    capture.admission.acceptedPrefix.get.step shouldBe "step-1"
    capture.changedPaths shouldBe frontier.binding.paths
    capture.candidate.tree should not be frontier.oldCapture.candidate.tree
    capture.diff should not include "+expected"
    val argv = Files.readString(frontier.scenario.root.resolve("release-stage-argv")).split("\u0000", -1).toVector.dropRight(1)
    argv.takeRight(5) shouldBe Vector("--literal-pathspecs", "add", "--all", "--", "release-help.txt")
    argv.count(_ == "add") shouldBe 1
    val after = snapshot(frontier)
    Vector("primaryHead", "primaryIndex", "oldDependencyIndex", "oldDependencyWorktree", "publication").foreach(key =>
      after.hcursor.downField(key).focus shouldBe frontier.snapshot.hcursor.downField(key).focus)
    _checks(frontier.scenario.root.resolve("dependency")).size shouldBe
      frontier.snapshot.hcursor.get[Vector[String]]("dependencyChecks").toOption.get.size + 1
    val beforecauses = frontier.snapshot.hcursor.get[Vector[String]]("causes").toOption.get
    val causes = _causes(native(frontier.scenario), frontier.session)
    causes.take(beforecauses.size) shouldBe beforecauses
    val added = causes.drop(beforecauses.size)
    added.count(_ == R.causationPrefix + "stage-intent-0") shouldBe 1
    added.count(_ == R.causationPrefix + "stage-result-0") shouldBe 1
    added.count(_.startsWith("ordered-candidate:")) shouldBe 0
    val mechanical = frontier.snapshot.hcursor.get[Vector[Json]]("native").toOption.get.filter(_.hcursor
      .get[String]("schemaVersion").toOption.contains("cncf.workflow-mechanical-transition.v1"))
    mechanical.foreach(old => native(frontier.scenario) should contain(old))
    _preserved_release_history(frontier)
    _checks(frontier.scenario.root).size shouldBe frontier.snapshot.hcursor.get[Vector[String]]("primaryChecks").toOption.get.size + 1
    if (stopAtDependencyReview) {
      _source_integrated(frontier.scenario) shouldBe false
      request.repairProgress.get.ordinaryCount shouldBe 4
      return
    }
    if (coherentDelivery) {
      _continue_source(frontier, cli, result, capture, installation.get)
      return
    }
    val reference = V.WorkflowArtifactReference("release-dependency-review", "1")
    val resolved = V.GoalPhaseIssueReport(request.work.get.issues.get.issues.filter(i => i.source == "REVIEW" || i.status != "RESOLVED").map(i =>
      V.GoalPhaseIssueDisposition(i.identity, "RESOLVED", "SPECIFICATION-OWNED independent review confirms the actual new checks and immutable old prefix", None)))
    val report = cli.result(request, "accept", Some(resolved))
    val execution = cli.evidence(result, "review", report.noSpaces, reference.identity)
    if (negativeChecks) {
      Files.writeString(Path.of(frontier.binding.root).resolve("release-help.txt"), "changed after capture\n")
      val before = native(frontier.scenario)
      val refused = cli.goal(request.submitOperation.get, Vector("--sessionId", request.submitSessionId.get,
        "--result", report.noSpaces, "--resultReference", _json(reference), "--executionEvidence", _json(execution)))
      refused.exitCode should not be 0
      native(frontier.scenario) should contain theSameElementsAs before
      Files.writeString(Path.of(frontier.binding.root).resolve("release-help.txt"), "New separately approved Help purpose\n")
    }
    cli.submit(request, report, reference.identity, Some(execution))
    val closing = cli.advance(frontier.session)
    closing.business[V.GoalPhaseAdvanceResult].authorityContext should not be empty
    cli.authority(closing, "release-dependency-final-authority")
    val terminal = cli.advance(frontier.session).business[V.GoalPhaseAdvanceResult]
    terminal.kind shouldBe "Terminal"
    _publication(frontier.scenario) shouldBe "published\n"
    val before = native(frontier.scenario)
    cli.advance(frontier.session).business[V.GoalPhaseAdvanceResult].kind shouldBe "Terminal"
    native(frontier.scenario) should contain theSameElementsAs before
    _preserved_release_history(frontier)
  }

  /** This specification qualifies a standalone N check whose actual product
    * inputs do not consume C source, artifact, publication or installation. */
  private def _dependency_inputs(frontier: Frontier): Json = {
    val scenario = frontier.scenario
    val root = Path.of(frontier.binding.root)
    val build = Files.readString(root.resolve("build.sbt"))
    Files.exists(root.resolve("project/plugins.sbt")) shouldBe false
    Vector("textus-sm-workflow", "goldenport", "unmanagedClasspath", "cozyCheckInstalledRuntime").foreach(text =>
      build should not include text)
    build should include("Test / runMain")
    val program = Files.readString(root.resolve("src/test/scala/ProductCheck.scala"))
    program should include("root.resolve(\"value.txt\")")
    program should include("root.resolve(\"release-help.txt\")")
    program should not include "release-source"
    val config = _config(scenario)
    val processes = parse(config.hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
    val workspace = frontier.binding.dependency.workspace
    config.hcursor.get[String]("cncf.sbt.runtime-binding." + workspace).toOption.foreach(_ shouldBe "project")
    Json.obj("binding" -> frontier.binding.asJson, "build" -> build.asJson, "product" -> program.asJson,
      "sbtVersion" -> Files.readString(root.resolve("project/build.properties")).asJson,
      "programs" -> _array(processes, "sbtPrograms").filter(_.hcursor.get[String]("target").toOption.contains(workspace)).asJson,
      "workspaces" -> _array(processes, "workspaces").filter(_.hcursor.get[String]("identity").toOption.contains(workspace)).asJson,
      "grants" -> _array(processes, "workspaceGrants").filter(_.hcursor.get[String]("workspaceId").toOption.contains(workspace)).asJson,
      "runtimeBinding" -> config.hcursor.downField("cncf.sbt.runtime-binding." + workspace).focus.getOrElse(Json.Null),
      "index" -> _git(root, Vector("diff", "--cached", "--binary")).asJson,
      "status" -> _git(root, Vector("status", "--porcelain=v1", "--untracked-files=all")).asJson)
  }
  private def _publication_updates(scenario: Scope): Vector[(Json, Json)] = {
    val directory = Path.of(scenario.host.target.root).resolve("target/cozy/local-application-update")
    val stream = Files.list(directory)
    val roots = try stream.iterator().asScala.toVector.filter(Files.isDirectory(_)) finally stream.close()
    roots.map { root =>
      val request = _read(root.resolve("request.json"))
      val publication = _read(root.resolve("publication.json"))
      request.hcursor.get[String]("producer") shouldBe Right(scenario.host.target.root)
      request.hcursor.get[String]("admission_host") shouldBe Right(scenario.root.toString)
      request.hcursor.get[String]("component_identity") shouldBe Right(SmWorkflowComponent.componentId.name)
      request.hcursor.get[String]("component_coordinate") shouldBe Right("org.simplemodeling.textus:textus-sm-workflow:0.1.0-SNAPSHOT")
      val archive = Path.of(request.hcursor.get[String]("archive").toOption.get)
      archive.startsWith(Path.of(scenario.host.target.root).resolve("target")) shouldBe true
      Files.isRegularFile(archive) shouldBe true
      publication.hcursor.downField("publication").get[String]("state") shouldBe Right("Completed")
      publication.hcursor.downField("runtime_update").get[String]("state") shouldBe Right("Activated")
      (request, publication)
    }
  }

  private def _continue_source(frontier: Frontier, cli: Caller, reviewed: F.Result, capture: D.Capture,
    producer: Installation): Unit = {
    val scenario = frontier.scenario
    _coherent(scenario) shouldBe true
    _source_integrated(scenario) shouldBe false
    val original = reviewed.business[V.GoalPhaseAdvanceResult]
    val inputs = _dependency_inputs(frontier)
    val dependencychecks = _checks(scenario.root.resolve("dependency"))
    _save(scenario, "release-independent-dependency-inputs", Json.obj("inputs" -> inputs,
      "checks" -> dependencychecks.asJson).noSpaces)
    val primarychecks = _checks(scenario.root)
    val oldfacts = native(scenario).filter(value => value.hcursor.get[String]("schemaVersion").toOption.exists(
      Set("cncf.workflow-mechanical-transition.v1", "cncf.workflow-completion-record.v1", "cncf.workflow-completion-record.v2")) ||
      value.hcursor.get[String]("kind").toOption.contains("CONTINUATION_RESULT") ||
      value.hcursor.get[String]("status").toOption.contains("published"))
    oldfacts should not be empty
    val oldcauses = _causes(native(scenario), frontier.session)
    val firstpublication = _publication_updates(scenario)
    firstpublication.size shouldBe 1
    val resolvedusage = V.GoalPhaseIssueReport(original.work.get.issues.get.issues.filter(i => i.source == "REVIEW" || i.status != "RESOLVED").map(i =>
      V.GoalPhaseIssueDisposition(i.identity, "RESOLVED", "SPECIFICATION-OWNED independent review confirms the actual corrected usage document", None)))
    val report = cli.result(original, "repair", Some(resolvedusage))
    report.hcursor.get[Vector[String]]("findings").toOption.get shouldBe Vector(
      "The declared C source must retain its qualified Release dependency continuation for its next normal publication")
    val evidence = cli.evidence(reviewed, "review", report.noSpaces, "source-coherence-independent-repair")
    cli.submit(original, report, "source-coherence-independent-repair", Some(evidence))
    val repairresult = cli.advance(frontier.session)
    val repair = repairresult.business[V.GoalPhaseAdvanceResult]
    repair.work.get.kind shouldBe "RepairStep"
    repair.work.get.stepIdentity shouldBe None
    repair.work.get.selectedPlan shouldBe original.work.get.selectedPlan
    repair.work.get.originalBase shouldBe original.work.get.originalBase
    repair.work.get.completed shouldBe original.work.get.completed
    repair.repairProgress.get.automaticCount shouldBe original.repairProgress.get.automaticCount + 1
    repair.repairProgress.get.ordinaryCount shouldBe original.repairProgress.get.ordinaryCount + 1
    _generation(scenario, repair) should be > _generation(scenario, original)
    // These refusals precede the genuinely new source edit and every later effect.
    val hostpath = scenario.root.resolve(".textus/config.json")
    val originalconfig = Files.readAllBytes(hostpath)
    def _refuse_(): Unit = {
      val before = native(scenario)
      cli.advance(frontier.session).exitCode should not be 0
      native(scenario) should contain theSameElementsAs before
      _checks(scenario.root.resolve("dependency")) shouldBe dependencychecks
      _publication(scenario) shouldBe "published\n"
    }
    val host = _host(scenario)
    Vector(None, Some(frontier.binding.copy(revision = frontier.binding.revision + 1))).foreach { selected =>
      _write_config(scenario, _config(scenario).mapObject(_.add("sm-workflow.runtime.definition",
        ThreeWorkflowRuntimeParameters.encode(host.copy(goal = host.goal.copy(phaseDependencyValidation = selected))).noSpaces.asJson)))
      _refuse_(); Files.write(hostpath, originalconfig)
    }
    val dependency = Path.of(frontier.binding.root)
    val bytes = Files.readAllBytes(dependency.resolve("release-help.txt"))
    Files.writeString(dependency.resolve("release-help.txt"), "changed historical dependency input\n")
    _refuse_(); Files.write(dependency.resolve("release-help.txt"), bytes)
    Files.move(dependency.resolve("release-help.txt"), dependency.resolve("unapproved-rename.txt"))
    _refuse_(); Files.move(dependency.resolve("unapproved-rename.txt"), dependency.resolve("release-help.txt"))
    Files.writeString(dependency.resolve("unapproved.txt"), "unapproved historical input\n")
    _refuse_(); Files.delete(dependency.resolve("unapproved.txt"))
    val source = _read(scenario.root.resolve("release-source-fixture.json"))
    val patch = Path.of(source.hcursor.get[String]("patch").toOption.get)
    val patchpaths = Files.readString(patch).linesIterator.filter(_.startsWith("+++ b/")).map(_.stripPrefix("+++ b/")).toVector
    patchpaths.toSet shouldBe _source_paths.toSet
    patchpaths.distinct shouldBe patchpaths
    val primary = Path.of(scenario.host.target.root)
    source.hcursor.get[Vector[String]]("placeholders").toOption.get.foreach { name =>
      val file = primary.resolve(name)
      Files.size(file) shouldBe 0L
      Files.delete(file)
    }
    _git(primary, Vector("apply", "--", patch.toString))
    _source_integrated(scenario) shouldBe true
    _dependency_inputs(frontier) shouldBe inputs
    val fixed = V.GoalPhaseIssueReport(repair.work.get.issues.get.issues.filter(_.status != "RESOLVED").map(i =>
      V.GoalPhaseIssueDisposition(i.identity, "FIXED", "SPECIFICATION-OWNED exact qualified C patch integrated only after new Release work issuance", None)))
    cli.submit(repair, cli.result(repair, "completed", Some(fixed)), "accepted-new-source-repair")
    val freshresult = cli.advance(frontier.session)
    val fresh = freshresult.business[V.GoalPhaseAdvanceResult]
    fresh.work.get.kind shouldBe "ReviewPhase"
    fresh.work.get.candidate should not be original.work.get.candidate
    _generation(scenario, fresh) shouldBe _generation(scenario, repair)
    fresh.work.get.selectedPlan shouldBe original.work.get.selectedPlan
    fresh.work.get.originalBase shouldBe original.work.get.originalBase
    fresh.work.get.completed shouldBe original.work.get.completed
    fresh.repairProgress shouldBe repair.repairProgress
    _checks(scenario.root).size shouldBe primarychecks.size + 1
    _checks(scenario.root.resolve("dependency")) shouldBe dependencychecks
    _dependency_inputs(frontier) shouldBe inputs
    val freshcapture = fresh.work.get.review.get.dependencyValidations.flatMap { value =>
      GoalPhaseNativeMetadata.normalizePublicJson(NativeCallerExecutionFixture.json(value)).toOption
        .flatMap(_.hcursor.get[D.Capture]("capture").toOption)
    }.filter(_.step.startsWith("phase:"))
    freshcapture shouldBe Vector(capture)
    _publication(scenario) shouldBe "published\npublished\n"
    val publications = _publication_updates(scenario)
    publications.size shouldBe 2
    publications should contain(firstpublication.head)
    publications.map(_._2.hcursor.downField("runtime_update").get[String]("installation_id").toOption.get)
      .distinct.size shouldBe 2
    val produced = Path.of(source.hcursor.get[String]("images").toOption.get)
    publications.foreach(_._1.hcursor.get[String]("root") shouldBe Right(produced.toString))
    val delivered = new Caller(scenario, Some(Installation(producer.executable, produced)))
    val next = delivered.advance(frontier.session)
    next.invocation.argv.head shouldBe producer.executable.toString
    val retained = next.business[V.GoalPhaseAdvanceResult]
    retained.sessionId shouldBe frontier.session
    retained.work shouldBe fresh.work
    retained.issuedWorkContext shouldBe fresh.issuedWorkContext
    val resolved = V.GoalPhaseIssueReport(retained.work.get.issues.get.issues.filter(i => i.source == "REVIEW" || i.status != "RESOLVED").map(i =>
      V.GoalPhaseIssueDisposition(i.identity, "RESOLVED", "SPECIFICATION-OWNED independent review confirms actual corrected C source, new publication and fresh primary checks", None)))
    delivered.review(next, "accept", "delivered-source-independent-acceptance", Some(resolved))
    val closing = delivered.advance(frontier.session)
    closing.business[V.GoalPhaseAdvanceResult].authorityContext should not be empty
    delivered.authority(closing, "delivered-source-final-authority")
    delivered.advance(frontier.session).business[V.GoalPhaseAdvanceResult].kind shouldBe "Terminal"
    val after = native(scenario)
    oldfacts.foreach(fact => after should contain(fact))
    _causes(after, frontier.session).take(oldcauses.size) shouldBe oldcauses
    _causes(after, frontier.session).count(_ == R.causationPrefix + "stage-result-0") shouldBe 1
    _causes(after, frontier.session).count(_ == R.causationPrefix + "observation-result") shouldBe 1
    _checks(scenario.root.resolve("dependency")) shouldBe dependencychecks
    _dependency_inputs(frontier) shouldBe inputs
    delivered.advance(frontier.session).business[V.GoalPhaseAdvanceResult].kind shouldBe "Terminal"
    native(scenario) should contain theSameElementsAs after
    _publication(scenario) shouldBe "published\npublished\n"
    _preserved_release_history(frontier)
  }

  def interrupted(name: String, point: String): Option[V.GoalPhaseAdvanceResult] = {
    val frontier = setup(name, None)
    val scenario = frontier.scenario
    val existingcheck = point == "goal-check-prepared:test"
    val newpath = Path.of(frontier.binding.root).resolve("release-help.txt")
    val newbytes = Files.readAllBytes(newpath)
    if (existingcheck) Files.delete(newpath) else selectPhase(frontier)
    val config = scenario.root.resolve(".textus/config.json")
    val assembly = scenario.root.resolve(".textus/assembly.yaml")
    val configbytes = Files.readAllBytes(config)
    val assemblybytes = Files.readAllBytes(assembly)
    val parameters = _config(scenario).asObject.get.toVector.map((key, value) => key -> value.asString.getOrElse(value.noSpaces)).toMap
    var observed = Vector.empty[String]
    val acknowledgement: String => Consequence[Unit] = value => {
      observed :+= value
      if (value == point) Consequence.stateConflict("SPECIFICATION-OWNED interruption after committed intent/result") else Consequence.unit
    }
    try _with_runtime_(scenario.root, "Goal", Some(_host(scenario)), hostParameters = parameters,
      goalAcknowledgement = Some(acknowledgement), componentInstance = "default") { (owner, component, actor, _) =>
      owner.executeOperationResponseInContext(Request.of(component = component.name, service = "GoalPhaseService",
        operation = "advanceGoalPhase", properties = NativeWorkflowOperationInputFixture.advance(frontier.session)), actor)
        .isSuccess shouldBe false
      observed should contain(point)
    } finally {
      Files.write(config, configbytes); Files.write(assembly, assemblybytes)
      if (existingcheck) Files.write(newpath, newbytes)
    }
    if (existingcheck) selectPhase(frontier)
    val cli = new Caller(scenario, None)
    if (point == "goal-release-dependency-stage-recorded:release-help.txt") {
      val before = native(scenario)
      val changed = frontier.binding.copy(revision = frontier.binding.revision + 1)
      selectPhase(frontier, binding = Some(changed))
      cli.advance(frontier.session).exitCode should not be 0
      native(scenario) should contain theSameElementsAs before
      selectPhase(frontier)
      val reviewed = cli.advance(frontier.session).business[V.GoalPhaseAdvanceResult]
      reviewed.work.get.kind shouldBe "ReviewPhase"
      _causes(native(scenario), frontier.session).count(_ == R.causationPrefix + "stage-result-0") shouldBe 1
      Some(reviewed)
    } else if (existingcheck) {
      val before = native(scenario)
      val first = cli.advance(frontier.session)
      first.exitCode should not be 0
      Files.readString(first.invocation.stderr) should include("existing check or metadata intent cannot be superseded")
      cli.advance(frontier.session).exitCode should not be 0
      native(scenario) should contain theSameElementsAs before
      _causes(before, frontier.session).exists(_.startsWith(R.causationPrefix)) shouldBe false
      _publication(scenario) shouldBe "published\n"
      None
    } else {
      val before = native(scenario)
      val first = cli.advance(frontier.session).business[V.GoalPhaseAdvanceResult]
      first.kind shouldBe "Wait"
      first.message should include("UNKNOWN")
      cli.advance(frontier.session).business[V.GoalPhaseAdvanceResult] shouldBe first
      native(scenario) should contain theSameElementsAs before
      _publication(scenario) shouldBe "published\n"
      Some(first)
    }
  }

  def installed(stage: String, locator: Path, old: Installation, selected: Installation): Unit = {
    require(Set("setup", "resume", "verify")(stage) && old.executable == selected.executable && old.images != selected.images,
      "Select explicit distinct OLD and NEW installed images")
    val mode = sys.props.getOrElse("smworkflow.spec.releaseDependencyMode", "bounded")
    require(Set("bounded", "coherent-source", "coherent-source-release-history", "coherent-source-failed-release-history")(mode),
      "Select an explicit supported fixture contract")
    val failedhistory = mode == "coherent-source-failed-release-history"
    val priorhistory = mode == "coherent-source-release-history" || failedhistory
    val coherentsource = mode == "coherent-source" || priorhistory
    val requestedbaseline = sys.props.getOrElse("smworkflow.spec.releaseDependencyHistoricalGuardBaseline", "false") match {
      case "true" => true
      case "false" => false
      case _ => fail("Select true or false for the optional OLD historical guard baseline")
    }
    val baseline = failedhistory || requestedbaseline
    val repairlimit = if (failedhistory) 10 else if (priorhistory) 3 else 2
    if (baseline) {
      require(priorhistory, "The OLD historical guard baseline belongs only to the pre-adopted history scenario")
      val selection = _read(old.images.resolve("selected"))
      selection.hcursor.get[String]("kind") shouldBe Right("cncf.installed-runtime-selection.v1")
      selection.hcursor.get[String]("installation_id") shouldBe Right(if (failedhistory)
        "45204b91-ec9e-482c-924b-527c9f37596b" else "81a4f26f-733f-49ea-81f8-1023055be13d")
    }
    if (stage == "setup") {
      Files.exists(locator) shouldBe false
      val frontier = setup("p69-release-dependency-installed", Some(old), coherentSource = coherentsource,
        priorReleaseHistory = priorhistory, failedReleaseHistory = failedhistory)
      if (baseline) {
        val scenario = frontier.scenario
        val configpath = scenario.root.resolve(".textus/config.json")
        val configbytes = Files.readAllBytes(configpath)
        val before = snapshot(frontier)
        try {
          selectPhase(frontier)
          val refused = new Caller(scenario, Some(old)).advance(frontier.session)
          withClue(refused.invocation.logs) { refused.exitCode should not be 0 }
          Files.readString(refused.invocation.stdout).trim shouldBe empty
          val error = Files.readString(refused.invocation.stderr)
          error should include("status=409")
          error should include("explicit phase dependency requires its authenticated successor before validation; an existing check or metadata intent cannot be superseded")
          snapshot(frontier) shouldBe before
        } finally Files.write(configpath, configbytes)
        _unchanged(frontier)
      }
      Files.writeString(locator, Json.obj("schemaVersion" -> "release-dependency-installed-fixture.v1".asJson,
        "root" -> frontier.scenario.root.toString.asJson, "session" -> frontier.session.asJson,
        "oldImages" -> old.images.toString.asJson, "newImages" -> selected.images.toString.asJson, "mode" -> mode.asJson,
        "repairLimit" -> repairlimit.asJson,
        "failedHistory" -> (if (failedhistory) _read(frontier.scenario.root.resolve("failed-release-completed-history.json")) else Json.Null),
        "snapshot" -> frontier.snapshot, "binding" -> frontier.binding.asJson, "oldCapture" -> frontier.oldCapture.asJson,
        "original" -> _json(frontier.original).asJson, "repair" -> _json(frontier.repair).asJson).noSpaces,
        StandardOpenOption.CREATE_NEW)
    } else {
      val saved = _read(locator)
      saved.hcursor.get[String]("schemaVersion") shouldBe Right("release-dependency-installed-fixture.v1")
      saved.hcursor.get[String]("oldImages") shouldBe Right(old.images.toString)
      saved.hcursor.get[String]("newImages") shouldBe Right(selected.images.toString)
      saved.hcursor.get[String]("mode").toOption.getOrElse("bounded") shouldBe mode
      saved.hcursor.get[Int]("repairLimit").toOption.getOrElse(2) shouldBe repairlimit
      val root = Path.of(saved.hcursor.get[String]("root").toOption.get)
      root.getFileName.toString should startWith("p69-release-dependency-installed-")
      require(root.isAbsolute && root.normalize == root && root.toRealPath() == root &&
        root.getParent == Path.of("").toAbsolutePath.normalize.resolve("target/sm-workflow/work/native-cli"),
        "Only this isolated installed fixture can be reopened")
      val scenario = if (coherentsource) {
        val config = _read(root.resolve(".textus/config.json"))
        val host = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(parse(config.hcursor
          .get[String]("sm-workflow.runtime.definition").toOption.get).toOption.get))
        val plan = _read(root.resolve("goal-plan.json")).as[GoalPhaseOrderedProgression.Plan].toOption.get
        host.target.root shouldBe root.resolve("repository").toString
        host.goal.candidatePaths shouldBe Vector("value.txt", "docs/usage.md") ++ _source_paths
        plan.steps.size shouldBe 1
        plan.steps.head.ownedPaths shouldBe host.goal.candidatePaths
        plan.repairLimit shouldBe repairlimit
        _prior_release_history(Scope(root, host, plan, None)) shouldBe priorhistory
        _failed_release_history(Scope(root, host, plan, None)) shouldBe failedhistory
        if (priorhistory) plan.acceptance.get.conditions.exists(_.identity == "release-utf8-line") shouldBe true
        if (failedhistory) {
          saved.hcursor.downField("failedHistory").focus shouldBe Some(_read(root.resolve("failed-release-completed-history.json")))
          host.goal.phaseValidation.checks shouldBe Vector(GoalPhaseNativeValidation.Check("test", 2, WorkflowSbtValidation.Kind.TestOnly))
          host.goal.stepValidation.checks shouldBe Vector(GoalPhaseNativeValidation.Check("test", 1, WorkflowSbtValidation.Kind.TestOnly))
          Files.isRegularFile(root.resolve("repository/src/test/scala/org/simplemodeling/textus/smworkflow/releasefixture/PhaseHistoryCheckSpec.scala")) shouldBe true
        }
        Files.isRegularFile(root.resolve("release-source-fixture.json")) shouldBe true
        Scope(root, host, plan, None)
      } else {
        val managed = F.reopenReleaseFix(root, TypedSbtHostPlanFixture.normalExecutable, if (stage == "verify") 3000 else 2000)
        Scope(managed.root, managed.host, managed.plan, Some(managed))
      }
      def _advance_(key: String): V.GoalPhaseAdvanceResult = {
        val record = take(new org.goldenport.record.io.RecordDecoder().json(saved.hcursor.get[String](key).toOption.get))
        take(summon[org.goldenport.convert.ValueReader[V.GoalPhaseAdvanceResult]].readC(record))
      }
      val frontier = Frontier(scenario, saved.hcursor.get[String]("session").toOption.get, _advance_("original"), _advance_("repair"),
        saved.hcursor.get[D.Binding]("binding").toOption.get, saved.hcursor.get[D.Capture]("oldCapture").toOption.get,
        saved.hcursor.downField("snapshot").focus.get)
      if (stage == "resume") resume(frontier, Some(selected), negativeChecks = true, coherentDelivery = coherentsource)
      else {
        val before = native(scenario)
        val delivery = if (coherentsource) Installation(selected.executable, Path.of(_read(root.resolve("release-source-fixture.json"))
          .hcursor.get[String]("images").toOption.get)) else selected
        val result = new Caller(scenario, Some(delivery)).advance(frontier.session).business[V.GoalPhaseAdvanceResult]
        result.kind shouldBe "Terminal"
        native(scenario) should contain theSameElementsAs before
        _preserved_release_history(frontier)
        _causes(before, frontier.session).count(_ == R.causationPrefix + "observation-result") shouldBe 1
        _publication(scenario) shouldBe (if (coherentsource) "published\npublished\n" else "published\n")
        if (coherentsource) {
          _source_integrated(scenario) shouldBe true
          _publication_updates(scenario).size shouldBe 2
          val inputs = _read(root.resolve("release-independent-dependency-inputs.json"))
          _dependency_inputs(frontier) shouldBe inputs.hcursor.downField("inputs").focus.get
          _checks(root.resolve("dependency")) shouldBe inputs.hcursor.get[Vector[String]]("checks").toOption.get
        }
      }
    }
  }
}

@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalReleaseDependencyValidationCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _required(name: String, existing: Boolean = true): Path = {
    val value = Path.of(sys.props.getOrElse(name, throw new IllegalArgumentException("Missing " + name)))
    require(value.isAbsolute && value.normalize == value && !Files.isSymbolicLink(value) &&
      value.getParent.toRealPath() == value.getParent, "Select explicit absolute nonsymlink fixture paths")
    if (existing) value.toRealPath() else value
  }
  "Installed same-Session Release dependency succession" should {
    "set up OLD, resume NEW and verify the same public lifecycle with its immutable accepted prefix" in {
      Given("explicit installed runtime selections and an isolated setup/resume/verify locator")
      val executable = _required("smworkflow.spec.installedCommand")
      val old = ReleaseDependencyCliSpecification.Installation(executable, _required("smworkflow.spec.releaseDependencyOldInstallations"))
      val selected = ReleaseDependencyCliSpecification.Installation(executable, _required("smworkflow.spec.releaseDependencyNewInstallations"))
      val locator = _required("smworkflow.spec.releaseDependencyLocator", existing = false)
      val stage = sys.props.getOrElse("smworkflow.spec.releaseDependencyStage", throw new IllegalArgumentException("Select releaseDependencyStage"))
      When("the selected installed image advances only the fresh fixture retained by that locator")
      ReleaseDependencyCliSpecification.installed(stage, locator, old, selected)
      Then("the helper's actual capture/check/review/closure and no-replay assertions have completed")
      Files.isRegularFile(locator) shouldBe true
    }
  }
}
