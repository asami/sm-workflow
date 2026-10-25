package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.processexecution.SbtOperation
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.convert.ValueReader
import org.goldenport.record.RecordPresentable
import org.goldenport.record.io.RecordDecoder
import org.scalatest.{GivenWhenThen}
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseFailedPhaseRepairAdoption as A
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeValidation as N
import GoalPhaseInterruptedValidationRepair as I
import SplitPhaseExplicitPartitionFixture.take

/** Fresh isolated real Git/Compile/Test fixture. Optional installed selection
  * drives the same public Operations through the actual installed executable.
  * Fixture evidence asserts no production Human provenance.
  * @since   Oct. 23, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalFailedPhaseRepairAdoptionCliSpec extends AnyWordSpec with GivenWhenThen
    with GoalPhaseKnownFailedPhaseValidationFixture {
  private val _product = "src/test/scala/ProductCheck.scala"
  private val _step = "failed-phase-corrective-step"
  private val _primary = Path.of("").toAbsolutePath.normalize
  private def _timeout_fixture(root: Path): Boolean = Files.isRegularFile(root.resolve("terminated-timeout-fixture"))
  override protected def _prepare_sbt_build_(root: Path, scenario: String): Unit = {
    super._prepare_sbt_build_(root, scenario)
    val build = root.resolve("repository/build.sbt")
    if (_timeout_fixture(root) && !Files.readString(build).contains("TERMINATED_TIMEOUT_FIXTURE_WAIT")) {
      // Run the ordinary real Test first, then wait finitely only on its actual
      // failure. The typed host deadline, rather than a mocked result, terminates
      // this owning SBT process. The repaired ordinary Test has no wait.
      Files.writeString(build, Files.readString(build) + """
        |Test / test := {
        |  val actual = (Test / test).result.value
        |  actual match {
        |    case Value(_) => ()
        |    case Inc(failure) =>
        |      println("TERMINATED_TIMEOUT_FIXTURE_WAIT_AFTER_REAL_TEST_FAILURE")
        |      System.out.flush()
        |      Thread.sleep(180000L)
        |      throw new MessageOnlyException(failure.toString)
        |  }
        |}
        |""".stripMargin)
    }
  }
  override protected def recoveryHost(root: Path): ThreeWorkflowRuntimeParameters.Definition = {
    val original = super.recoveryHost(root)
    original.copy(goal = original.goal.copy(candidatePaths = Vector("value.txt", _product, "src/main/scala"),
      closure = original.goal.closure.copy(programs = Vector(
        GoalPhaseNativeClosure.StepProgram("step-1", 1), GoalPhaseNativeClosure.StepProgram(_step, 2)))))
  }
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val git = TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString), goalPaths = goal.candidatePaths,
      goalMessages = goal.closure.programs.map(program => "Goal Step " + program.step),
      goalSelections = Vector(Vector("value.txt"), Vector(_product)))
    val compile = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(1), Some(git),
      executionTimeoutMillis = 1200000L, operation = SbtOperation.Compile)
    val test = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(2),
      executionTimeoutMillis = if (_timeout_fixture(root)) 120000L else 1200000L, operation = SbtOperation.Test)
    def _array_(value: Json, name: String): Vector[Json] = value.hcursor.get[Vector[Json]](name).toOption.get
    val programs = _array_(compile, "sbtPrograms") ++ _array_(test, "sbtPrograms")
    compile.mapObject(_.add("sbtPrograms", Json.fromValues(programs))
      .add("workspaces", Json.fromValues(_array_(compile, "workspaces") ++ _array_(test, "workspaces")))
      .add("workspaceGrants", Json.fromValues(_array_(compile, "workspaceGrants") ++ _array_(test, "workspaceGrants")))
      .add("grants", Json.arr(Json.obj("componentId" -> Json.fromString(SmWorkflowComponent.componentId.name),
        "capabilities" -> Json.fromValues((_array_(compile, "gitPrograms") ++ programs)
          .map(_.hcursor.downField("capability").focus.get))))))
  }
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def _read(path: Path): Json = parse(Files.readString(path)).toOption.get
  private final case class Invocation(exit: Int, stdout: Path, stderr: Path) {
    def business[A: ValueReader]: A = {
      withClue(Files.readString(stderr)) { exit shouldBe 0 }
      take(summon[ValueReader[A]].readC(take(new RecordDecoder().json(Files.readString(stdout)))))
    }
  }
  private final class Cli(root: Path, identity: String) {
    private var _ordinal = 0
    def call(service: String, operation: String, arguments: Vector[String]): Invocation = {
      val logs = Files.createDirectories(root.resolve("repair-cli-logs"))
      val index = _ordinal
      _ordinal += 1
      val stdout = logs.resolve(index + ".stdout.json")
      val stderr = logs.resolve(index + ".stderr.log")
      val launcher = sys.props.get("smworkflow.spec.installedCommand") match {
        case Some(executable) =>
          val path = Path.of(executable)
          require(path.isAbsolute && Files.isExecutable(path), "Select an absolute installed CLI")
          Vector(path.toString, "--runtime-root", root.toString)
        case None => Vector(_primary.resolve("bin/launcher").toString, "--classpath-file",
          _primary.resolve("target/cncf.d/runtime-classpath.txt").toString, "--main-class", "org.goldenport.cncf.CncfMain",
          "--", "--force-exit", "--component-dev-dir", _primary.toString,
          "--textus.assembly.descriptor=" + root.resolve(".textus/assembly.yaml"))
      }
      val builder = new ProcessBuilder((launcher ++ Vector("command",
        SmWorkflowComponent.componentId.name + "." + service + "." + operation) ++ arguments ++ Vector("--json")).asJava)
        .directory(root.toFile).redirectOutput(stdout.toFile).redirectError(stderr.toFile)
      sys.props.get("smworkflow.spec.runtimeRoot").orElse(sys.props.get("smworkflow.spec.installations"))
        .foreach(value => builder.environment().put("SM_WORKFLOW_INSTALLATIONS", value))
      val process = builder.start()
      try {
        process.getOutputStream.close()
        withClue(Files.readString(stderr)) { process.waitFor(1500L, TimeUnit.SECONDS) shouldBe true }
        Invocation(process.exitValue(), stdout, stderr)
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
    def goal(operation: String, arguments: Vector[String]): Invocation = call("GoalPhaseService", operation, arguments)
    def advance(): Invocation = goal("advanceGoalPhase", Vector("--sessionId", identity))
    def describe(proposal: GoalPhasePlanRevision.Proposal): Invocation = goal(A.describeOperation, Vector("--input",
      _json(V.GoalPhaseFailedPhaseRepairDescriptionInput(identity, GoalPhasePlanRevision.publicProposal(Some(proposal)).get))))
    def adopt(input: V.GoalPhaseFailedPhaseRepairAdoptionInput): Invocation = goal(A.adoptionOperation, Vector("--input", _json(input)))
    private def _file(name: String, value: RecordPresentable): String = {
      val path = Files.createTempDirectory(root, name + "-").resolve("input.json")
      Files.writeString(path, _json(value))
      path.toUri.toASCIIString
    }
    private def _import_prepared(invocation: Invocation): Unit = {
      withClue(Files.readString(invocation.stderr)) { invocation.exit shouldBe 0 }
      _read(invocation.stdout).hcursor.downField("datastore").focus.get.asArray.get should not be empty
      val config = root.resolve(".textus/config.json")
      Files.writeString(config, _read(config).mapObject(_.add("textus.import.data.file", Json.fromString(invocation.stdout.toString))).noSpaces)
    }
    def direction(described: Invocation, proposal: GoalPhasePlanRevision.Proposal, choice: String,
      name: String): V.GoalPhaseFailedPhaseRepairAdoptionInput = {
      val description = described.business[V.GoalPhaseFailedPhaseRepairDescriptionResult]
      val reference = V.WorkflowArtifactReference(name, "1")
      val input = V.GoalPhaseFailedPhaseRepairAdoptionInput(identity, description.target,
        GoalPhasePlanRevision.publicProposal(Some(proposal)).get, choice,
        "Fixture-owned independent Human direction for exactly one corrective Step", reference)
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
      val evidence = V.WorkflowEvidencePreparationInput("GoalPhase", "failed-phase-repair-adoption",
        described.stdout.toUri.toASCIIString, _file("repair-input", input), _file("repair-reference", reference),
        _file("repair-execution", execution), "normal-development-host", "2026-10-07T00:00:00Z",
        "entity:smworkflow.cli.host_evidence", Some("participant"))
      _import_prepared(call("NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", _json(evidence))))
      input
    }
    def limitedRepair(described: Invocation): V.GoalPhaseValidationRecoveryInput = {
      val description = described.business[V.GoalPhaseValidationRecoveryDescriptionResult]
      val answer = V.GoalPhaseValidationRecoveryAnswer("LimitedRepair",
        "Fixture Human authorizes known-error repair and evidence-based suspected-hang investigation before a concrete proposal")
      val reference = V.WorkflowArtifactReference("fixture-timeout-limited-repair-human", "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
      val evidence = V.WorkflowEvidencePreparationInput("GoalPhase", "validation-recovery",
        described.stdout.toUri.toASCIIString, _file("limited-answer", answer), _file("limited-reference", reference),
        _file("limited-execution", execution), "normal-development-host", "2026-10-07T00:00:00Z",
        "entity:smworkflow.cli.host_evidence", Some("participant"))
      _import_prepared(call("NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", _json(evidence))))
      V.GoalPhaseValidationRecoveryInput(identity, description.target, answer.choice, answer.rationale, reference)
    }
    def submit(request: V.GoalPhaseAdvanceResult, reference: V.WorkflowArtifactReference,
      result: V.GoalPhaseWorkResult, execution: Option[V.WorkflowExecutionEvidence] = None): Unit = {
      goal(request.submitOperation.get, Vector("--sessionId", request.submitSessionId.get, "--result", _json(result),
        "--resultReference", _json(reference)) ++ execution.toVector.flatMap(value => Vector("--executionEvidence", _json(value))))
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
    }
    def acceptStep(reviewcall: Invocation): Unit = {
      val review = reviewcall.business[V.GoalPhaseAdvanceResult]
      val name = review.work.get.stepIdentity.get
      val result = V.GoalPhaseWorkResult("accept", "Independent fixture reviewer accepts the exact candidate", Vector.empty,
        None, None, None, None, None, None, None, None, dependencyValidation = None)
      val reference = V.WorkflowArtifactReference("repair-fixture-review-" + name, "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("fixture-independent-reviewer"), None, None)
      val evidence = V.WorkflowEvidencePreparationInput("GoalPhase", "review", reviewcall.stdout.toUri.toASCIIString,
        _file("review", result), _file("review-reference", reference), _file("review-execution", execution),
        "normal-development-host", "2026-10-07T00:00:00Z", "entity:smworkflow.cli.host_evidence", None)
      _import_prepared(call("NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", _json(evidence))))
      submit(review, reference, result, Some(execution))
      val closing = advance()
      closing.business[V.GoalPhaseAdvanceResult].authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
      val authority = V.WorkflowAuthorityPreparationInput(closing.stdout.toUri.toASCIIString,
        _file("authority", V.WorkflowArtifactReference("repair-fixture-authority-" + name, "1")), "participant",
        "normal-development-host", "2026-10-07T00:00:00Z", "entity:smworkflow.cli.host_evidence")
      _import_prepared(call("NativeWorkflowPreparationService", "prepareAuthority", Vector("--input", _json(authority))))
    }
  }

  "E3 same-Session failed whole-Phase repair adoption" should afterWord(
    "in spec:goal-phase-failed-phase-repair-adoption, example:E3, rules:R1,R2,R3,R4,R5,R6,R7, phase:P116-CONT-01") {
    "retain real failure and accepted prefix through append-only host extension then ordinarily validate review and commit the explicitly selected repair" in {
      Given("docs/spec/goal-phase-failed-phase-repair-adoption.md R1-R7 E3: a NEW isolated native Store with the existing real Compile/Phase Test fixture")
      val root = newKnownRoot("failed-phase-repair-adoption")
      var identity = ""
      var original = Option.empty[V.GoalPhaseAdvanceResult]
      inKnownEpisode(root, initialize = true, componentInstance = "default") { episode =>
        identity = episode.start().sessionId
        original = Some(episode.advance(identity))
        episode.submitImplementation(original.get).accepted shouldBe true
      }
      val historyconfig = root.resolve(".textus/config.json")
      val historysettings = _read(_primary.resolve("examples/source-history/goldenport-config.json")).asObject.get
      Files.writeString(historyconfig, _read(historyconfig).mapObject(fields =>
        historysettings.toVector.foldLeft(fields)((selected, entry) => selected.add(entry._1, entry._2))).noSpaces)
      if (sys.props.contains("smworkflow.spec.installedCommand")) {
        val config = root.resolve(".textus/config.json")
        Files.writeString(config, Vector(1, 2).foldLeft(_read(config)) { (value, ordinal) =>
          value.mapObject(_.add("cncf.sbt.runtime-binding.sm-workflow-repository-validation-" + ordinal, Json.fromString("installed")))
        }.noSpaces)
      }
      val cli = new Cli(root, identity)
      When("ordinary CLI Compile and independently evidenced ReviewStep precede the actual local Step commit and failed Phase Test")
      val firstreview = cli.advance()
      firstreview.business[V.GoalPhaseAdvanceResult].work.get.kind shouldBe "ReviewStep"
      cli.acceptStep(firstreview)
      cli.advance().business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
      val olddescription = cli.goal(GoalPhaseValidationRecovery.describeOperation, Vector("--sessionId", identity))
        .business[V.GoalPhaseValidationRecoveryDescriptionResult]
      val recovery = take(GoalPhaseValidationRecovery.targetC(olddescription.target))
      var before = Option.empty[InstanceRecord]
      var oldfailed = Option.empty[N.Saved]
      inKnownEpisode(root, componentInstance = "default") { episode =>
        before = Some(episode.current(identity))
        oldfailed = episode.retained(identity, recovery.sourceRevision, "validation")
      }
      Then("one genuine terminated nonzero Test and one closed Step are retained before repair adoption")
      recovery.knownFailedPhase.get.exitCode shouldBe 1
      oldfailed.get.payload.receipts.head.termination shouldBe "exited"
      oldfailed.get.payload.receipts.head.exitCode shouldBe Some(1)
      oldfailed.get.payload.receipts.head.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Failed
      before.get.currentProgression shouldBe Some(ProgressionReference("phase.Decision"))
      phaseExecutions(root).size shouldBe 1
      val closedtip = Files.readString(root.resolve("repository/.git/refs/heads/main"))
      val oldplan = _recovery_plan
      val corrective = O.Step(_step, "Repair the failed product Test source", Vector(_product), oldplan.steps.map(_.identity), Vector("compile"))
      val proposed = oldplan.copy(revision = "failed-phase-repair-2", steps = oldplan.steps :+ corrective)
      val proposal = GoalPhasePlanRevision.Proposal("fixture-failed-phase-repair", "1", "Correct the source that caused the Phase Test failure", proposed)
      val product = root.resolve("repository").resolve(_product)
      val source = Files.readString(product)
      val originalpredicate = "val ready = Files.isRegularFile(root.resolve(\"phase-ready\"))"
      source should include(originalpredicate)
      Files.writeString(product, source.replace(originalpredicate, "val ready = true"))
      Files.writeString(root.resolve("repository/unrelated.txt"), "unrelated fixture dirt\n")
      When("passive description binds the complete retained Plan and proposed exact external-to-original-scope repair")
      val described = cli.describe(proposal)
      val description = described.business[V.GoalPhaseFailedPhaseRepairDescriptionResult]
      val target = take(A.targetC(description.target))
      Then("the failure, ownership policy, prefix and generation are explicit and no command or history transition occurs")
      description.choices shouldBe Vector("AdoptRepairPlan", "Defer")
      target.recovery shouldBe recovery
      target.plan shouldBe oldplan
      target.completed.size shouldBe 1
      target.completed.head.commit.identity shouldBe closedtip.trim
      target.candidatePaths shouldBe Vector("value.txt", _product, "src/main/scala")
      target.closurePolicy shouldBe recoveryHost(root).goal.closure
      target.captured.proposal shouldBe proposal
      target.captured.plan.steps.take(oldplan.steps.size) shouldBe oldplan.steps
      target.repairProgress shouldBe GoalPhaseRepairProgress.Snapshot(0, 0, 2, Some("sm-workflow.repair-trend.v1"), Some(2), Some(2))
      phaseExecutions(root).size shouldBe 1
      inKnownEpisode(root, componentInstance = "default")(episode => episode.current(identity).history shouldBe before.get.history)

      When("missing or crossed evidence, invalid scope or an exact-scope corrective identity without a fixed commit program attempt adoption")
      val input = V.GoalPhaseFailedPhaseRepairAdoptionInput(identity, description.target,
        GoalPhasePlanRevision.publicProposal(Some(proposal)).get, "AdoptRepairPlan", "Actual independent fixture repair direction",
        V.WorkflowArtifactReference("missing-fixture-direction", "1"))
      val missing = cli.adopt(input)
      val crossed = cli.adopt(input.copy(target = A.targetRecord(target.copy(generation = target.generation + 1))))
      val foreign = proposal.copy(plan = proposed.copy(steps = oldplan.steps :+ corrective.copy(ownedPaths = Vector("unrelated.txt"))))
      val directoryonly = proposal.copy(plan = proposed.copy(steps = oldplan.steps :+
        corrective.copy(ownedPaths = Vector("src/main/scala/CompileConsumer.scala"))))
      val unmapped = proposal.copy(plan = proposed.copy(steps = oldplan.steps :+
        corrective.copy(identity = "unmapped-corrective-step")))
      val denied = Vector(cli.describe(foreign), cli.describe(directoryonly), cli.describe(unmapped),
        cli.adopt(input.copy(proposal = GoalPhasePlanRevision.publicProposal(Some(unmapped)).get)),
        cli.adopt(input.copy(target = A.targetRecord(target.copy(
          closurePolicy = target.closurePolicy.copy(revision = target.closurePolicy.revision + 1))))),
        cli.describe(proposal.copy(plan = proposed.copy(phaseChecks = Vector("compile")))))
      val deferred = cli.direction(described, proposal, "Defer", "fixture-failed-phase-defer")
      val deferresult = cli.adopt(deferred).business[V.GoalPhaseFailedPhaseRepairAdoptionResult]
      Then("invalid scope, unmapped commit identity and missing/crossed evidence refuse without history writes and genuine Defer writes nothing")
      missing.exit should not be 0
      crossed.exit should not be 0
      denied.foreach(_.exit should not be 0)
      deferresult.outcome shouldBe "deferred"
      inKnownEpisode(root, componentInstance = "default")(episode => episode.current(identity).history shouldBe before.get.history)
      phaseExecutions(root).size shouldBe 1
      Files.readString(root.resolve("repository/.git/refs/heads/main")) shouldBe closedtip

      When("only the independently provisioned exact adoption direction is submitted")
      val authorized = cli.direction(described, proposal, "AdoptRepairPlan", "fixture-failed-phase-adopt")
      val adopted = cli.adopt(authorized).business[V.GoalPhaseFailedPhaseRepairAdoptionResult]
      val repeated = cli.adopt(authorized)
      var directed = Option.empty[InstanceRecord]
      inKnownEpisode(root, componentInstance = "default") { episode => directed = Some(episode.current(identity)) }
      Then("one mechanical Direction appends with no semantic Submit or Test and duplicate adoption refuses")
      adopted.sessionId shouldBe identity
      adopted.outcome shouldBe "failed-phase-repair-adopted"
      adopted.accepted shouldBe true
      repeated.exit should not be 0
      directed.get.history.take(before.get.history.size) shouldBe before.get.history
      directed.get.revision shouldBe before.get.revision.next
      directed.get.causation.value shouldBe "ordered-failed-phase-repair-adoption:" + before.get.revision.value
      directed.get.currentProgression shouldBe Some(ProgressionReference("phase.Implementation"))
      phaseExecutions(root).size shouldBe 1
      Files.readString(root.resolve("repository/.git/refs/heads/main")) shouldBe closedtip

      Given("the actual persisted Direction and its unchanged two-program host closure policy at the current isolated frontier")
      val currentconfig = _read(historyconfig)
      val hostwire = parse(currentconfig.hcursor.get[String]("sm-workflow.runtime.definition").toOption.get).toOption.get
      val currenthost = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(hostwire))
      currenthost.goal.closure shouldBe target.closurePolicy
      currenthost.goal.candidatePaths shouldBe target.candidatePaths
      val executions = phaseExecutions(root)
      def _install_closure_(policy: GoalPhaseNativeClosure.Policy,
        candidatepaths: Vector[String] = currenthost.goal.candidatePaths): Unit = {
        val selected = currenthost.copy(goal = currenthost.goal.copy(closure = policy, candidatePaths = candidatepaths))
        Files.writeString(historyconfig, currentconfig.mapObject(_.add("sm-workflow.runtime.definition",
          Json.fromString(ThreeWorkflowRuntimeParameters.encode(selected).noSpaces))).noSpaces)
      }
      val remapped = currenthost.goal.closure.copy(programs = currenthost.goal.closure.programs.map(program =>
        if (program.step == _step) program.copy(ordinal = 3) else program))
      _install_closure_(remapped)
      When("a fresh ordinary CLI Advance traverses the retained Direction under a changed corrective Step ordinal")
      val mappingrefused = cli.advance()
      Then("the mapping change refuses without appending history, rerunning the failed Test or changing the accepted commit")
      mappingrefused.exit should not be 0
      Files.readString(mappingrefused.stderr) should include("Retained failed Phase repair closure policy permits only an appended program suffix")
      inKnownEpisode(root, componentInstance = "default") { episode =>
        val observed = episode.current(identity)
        (observed.definition.workflowIdentity, observed.definition.workflowRevision,
          observed.definition.sourceCorrelation, observed.definition.producerAbiIdentity,
          observed.definition.workflowAbiIdentity, observed.definition.bootstrapAbiIdentity,
          observed.definition.producerRevision) shouldBe
          (directed.get.definition.workflowIdentity, directed.get.definition.workflowRevision,
            directed.get.definition.sourceCorrelation, directed.get.definition.producerAbiIdentity,
            directed.get.definition.workflowAbiIdentity, directed.get.definition.bootstrapAbiIdentity,
            directed.get.definition.producerRevision)
        observed.copy(definition = directed.get.definition) shouldBe directed.get
      }
      phaseExecutions(root) shouldBe executions
      Files.readString(root.resolve("repository/.git/refs/heads/main")) shouldBe closedtip

      Given("the persisted Direction with its original closure policy and a current host that reorders the recorded candidate-path prefix")
      def _repository_state_(): Vector[(String, Long, Long)] = {
        val repository = root.resolve("repository")
        val stream = Files.walk(repository)
        try stream.iterator().asScala.filter(Files.isRegularFile(_)).map(path =>
          (repository.relativize(path).toString, Files.size(path), Files.getLastModifiedTime(path).toMillis))
          .toVector.sortBy(_._1)
        finally stream.close()
      }
      val repositorystate = _repository_state_()
      _install_closure_(currenthost.goal.closure, currenthost.goal.candidatePaths.reverse)
      When("a fresh ordinary CLI Advance traverses the retained Direction under the crossed candidate-path prefix")
      val pathsrefused = cli.advance()
      Then("the path-prefix change refuses before any history write, SBT build or repository mutation")
      pathsrefused.exit should not be 0
      Files.readString(pathsrefused.stderr) should include("Retained failed Phase repair candidatePaths permit only an appended path suffix")
      inKnownEpisode(root, componentInstance = "default") { episode =>
        val observed = episode.current(identity)
        (observed.definition.workflowIdentity, observed.definition.workflowRevision,
          observed.definition.sourceCorrelation, observed.definition.producerAbiIdentity,
          observed.definition.workflowAbiIdentity, observed.definition.bootstrapAbiIdentity,
          observed.definition.producerRevision) shouldBe
          (directed.get.definition.workflowIdentity, directed.get.definition.workflowRevision,
            directed.get.definition.sourceCorrelation, directed.get.definition.producerAbiIdentity,
            directed.get.definition.workflowAbiIdentity, directed.get.definition.bootstrapAbiIdentity,
            directed.get.definition.producerRevision)
        observed.copy(definition = directed.get.definition) shouldBe directed.get
      }
      phaseExecutions(root) shouldBe executions
      _repository_state_() shouldBe repositorystate

      Given("the original path prefix plus one exact literal suffix with its fixed stage program and capability, and a future third StepProgram")
      val extendedpolicy = currenthost.goal.closure.copy(programs = currenthost.goal.closure.programs :+
        GoalPhaseNativeClosure.StepProgram("future-step", 3))
      val extendedpaths = currenthost.goal.candidatePaths :+ "src/main/scala/CompileConsumer.scala"
      val pathplan = TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main",
        root.resolve("upstream.git").toString), goalPaths = extendedpaths)
      val pathprogram = pathplan.hcursor.get[Vector[Json]]("gitPrograms").toOption.get.last
      pathprogram.hcursor.get[String]("selectionId").toOption.get shouldBe "goal-path-4"
      pathprogram.hcursor.get[String]("selectedPath").toOption.get shouldBe extendedpaths.last
      val processplan = parse(currentconfig.hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
      val extendedplan = processplan.mapObject(fields => fields
        .add("gitPrograms", Json.fromValues(processplan.hcursor.get[Vector[Json]]("gitPrograms").toOption.get :+ pathprogram))
        .add("grants", Json.fromValues(processplan.hcursor.get[Vector[Json]]("grants").toOption.get.map { grant =>
          if (grant.hcursor.get[String]("componentId").toOption.contains(SmWorkflowComponent.componentId.name))
            grant.mapObject(_.add("capabilities", Json.fromValues(grant.hcursor.get[Vector[Json]]("capabilities").toOption.get :+
              pathprogram.hcursor.downField("capability").focus.get)))
          else grant
        })))
      _install_closure_(extendedpolicy, extendedpaths)
      Files.writeString(historyconfig, _read(historyconfig).mapObject(_.add("textus.subsystem.process-execution.plan",
        Json.fromString(extendedplan.noSpaces))).noSpaces)
      When("a separate ordinary CLI Advance reopens the historical Direction under the extended current host and issues corrective implementation")
      val implementation = cli.advance().business[V.GoalPhaseAdvanceResult]
      val reopened = cli.advance().business[V.GoalPhaseAdvanceResult]
      Then("the same Session retains original base and accepted prefix and issues a new continuation for the exact corrective Step and revision")
      implementation.sessionId shouldBe identity
      implementation.work.get.kind shouldBe "ImplementStep"
      implementation.work.get.stepIdentity shouldBe Some(_step)
      implementation.work.get.selectedPlan.revision shouldBe proposed.revision
      implementation.work.get.selectedPlan.steps.last.ownedPaths shouldBe Vector(_product)
      implementation.issuedWorkContext should not be empty
      implementation.issuedWorkContext.get.continuationId should not be original.get.issuedWorkContext.get.continuationId
      implementation.work.get.originalBase shouldBe original.get.work.get.originalBase
      implementation.work.get.completed.map(_.commit.identity) shouldBe Vector(closedtip.trim)
      implementation.repairProgress shouldBe original.get.repairProgress
      reopened shouldBe implementation
      phaseExecutions(root).size shouldBe 1
      val repairedsource = Files.readString(product)
      cli.submit(implementation, V.WorkflowArtifactReference("fixture-corrective-implementation", "1"),
        V.GoalPhaseWorkResult("completed", "The explicitly selected source repair already exists", Vector.empty,
          None, None, None, None, None, None, None, None, dependencyValidation = None))
      var correctivefrontier = Option.empty[InstanceRecord]
      inKnownEpisode(root, componentInstance = "default") { episode => correctivefrontier = Some(episode.current(identity)) }
      When("ordinary Advance normalizes the corrective source through goldenport, captures only the repair scope, runs its real Compile and requests independent ReviewStep")
      val correctionreview = cli.advance()
      val review = correctionreview.business[V.GoalPhaseAdvanceResult]
      var correctivehistory = Option.empty[GoalPhaseNativeSourceHistory.Payload]
      inKnownEpisode(root, componentInstance = "default") { episode =>
        episode.prepare(call => for {
          catalog <- GoalPhaseRuntimeCatalog.loadC()
          storage <- WorkflowComponentStorage.bindC(ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin),
            SmWorkflowComponent.componentId, _configuration_, Vector(catalog.definition), _collection_("instances"), _collection_("continuations"))
          reader <- storage.bindMechanicalC(GoalPhaseNativeRun.advanceOperation, GoalPhaseNativeSourceHistory.codec)
          saved <- reader.loadRetainedC(WorkflowMechanicalTransition.Reference(identity + ":ordered-candidate:" +
            correctivefrontier.get.revision.value + ":source-history-result-0", "1"))(using call.executionContext)
          _ = correctivehistory = saved.map(_.receipt.payload.value)
        } yield ())
      }
      Then("the corrective Step has its actual selected-source history result and the new candidate excludes unrelated dirt")
      correctivehistory should not be empty
      correctivehistory.get.sourceRevision shouldBe correctivefrontier.get.revision.value.toString
      correctivehistory.get.step shouldBe _step
      correctivehistory.get.path shouldBe _product
      correctivehistory.get.style shouldBe "goldenport"
      correctivehistory.get.providerIdentity shouldBe historysettings("sm-workflow.source-history.rule").get.hcursor.get[String]("identity").toOption.get
      correctivehistory.get.outcome shouldBe Some("updated")
      review.work.get.kind shouldBe "ReviewStep"
      review.work.get.stepIdentity shouldBe Some(_step)
      review.work.get.review.get.changedPaths shouldBe Vector(_product)
      review.work.get.review.get.checks.head.exitCode shouldBe Some(0)
      review.work.get.review.get.checks.head.sbt.get.kind shouldBe "compile"
      review.work.get.review.get.diff should not include "unrelated fixture dirt"
      phaseExecutions(root).size shouldBe 1
      val normalizedsource = Files.readString(product)
      normalizedsource should include("val ready = true")
      normalizedsource.take(normalizedsource.indexOf("/**")) shouldBe repairedsource.take(repairedsource.indexOf("/**"))
      normalizedsource.substring(normalizedsource.indexOf("*/") + 2) shouldBe repairedsource.substring(repairedsource.indexOf("*/") + 2)
      cli.acceptStep(correctionreview)
      When("ordinary local Step closure and a real whole-Phase Test continue to independent Phase review")
      val phase = cli.advance().business[V.GoalPhaseAdvanceResult]
      val phaseagain = cli.advance().business[V.GoalPhaseAdvanceResult]
      Then("both accepted commits and the original failed receipt survive reopened native traversal in the same Session")
      phase.sessionId shouldBe identity
      phase.work.get.kind shouldBe "ReviewPhase"
      phase.work.get.completed.map(_.step) shouldBe Vector("step-1", _step)
      phase.work.get.completed.head.commit.identity shouldBe closedtip.trim
      phase.work.get.completed.last.commit.parent shouldBe closedtip.trim
      phase.work.get.selectedPlan.revision shouldBe proposed.revision
      phase.work.get.review.get.checks.head.exitCode shouldBe Some(0)
      phase.work.get.review.get.checks.head.sbt.get.outcome shouldBe "succeeded"
      phaseagain shouldBe phase
      phaseExecutions(root).size shouldBe 2
      Files.exists(root.resolve("phase-ready")) shouldBe false
      Files.readString(root.resolve("repository/unrelated.txt")) shouldBe "unrelated fixture dirt\n"
      inKnownEpisode(root, componentInstance = "default") { episode =>
        val current = episode.current(identity)
        current.history.take(before.get.history.size) shouldBe before.get.history
        current.history.count(_.causation.value.startsWith("ordered-failed-phase-repair-adoption:")) shouldBe 1
        val retained = episode.retained(identity, recovery.sourceRevision, "validation")
        retained should not be empty
        (retained.get.record.definition.workflowIdentity, retained.get.record.definition.workflowRevision,
          retained.get.record.definition.sourceCorrelation, retained.get.record.definition.producerAbiIdentity,
          retained.get.record.definition.workflowAbiIdentity, retained.get.record.definition.bootstrapAbiIdentity,
          retained.get.record.definition.producerRevision) shouldBe
          (oldfailed.get.record.definition.workflowIdentity, oldfailed.get.record.definition.workflowRevision,
            oldfailed.get.record.definition.sourceCorrelation, oldfailed.get.record.definition.producerAbiIdentity,
            oldfailed.get.record.definition.workflowAbiIdentity, oldfailed.get.record.definition.bootstrapAbiIdentity,
            oldfailed.get.record.definition.producerRevision)
        retained.get.record.copy(definition = oldfailed.get.record.definition) shouldBe oldfailed.get.record
        N.codec.encode(retained.get.payload) shouldBe N.codec.encode(oldfailed.get.payload)
        var direction = Option.empty[A.Direction]
        episode.prepare(call => for {
          catalog <- GoalPhaseRuntimeCatalog.loadC()
          storage <- WorkflowComponentStorage.bindC(ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin),
            SmWorkflowComponent.componentId, _configuration_, Vector(catalog.definition), _collection_("instances"), _collection_("continuations"))
          reader <- storage.bindMechanicalC(GoalPhaseNativeRun.advanceOperation, A.codec)
          saved <- reader.loadRetainedC(WorkflowMechanicalTransition.Reference(identity + ":ordered-failed-phase-repair-adoption:" +
            before.get.revision.value, "1"))(using call.executionContext)
          _ = direction = saved.map(_.receipt.payload.value)
        } yield ())
        direction.get.target shouldBe target
        direction.get.target.closurePolicy shouldBe currenthost.goal.closure
        direction.get.target.closurePolicy should not be extendedpolicy
        direction.get.target.candidatePaths shouldBe currenthost.goal.candidatePaths
        direction.get.target.candidatePaths should not be extendedpaths
        direction.get.reference shouldBe ContextReference(authorized.reference.identity, authorized.reference.revision)
        Files.writeString(root.resolve("failed-phase-repair-reopened-proof.json"), Json.obj(
          "sessionId" -> Json.fromString(identity), "originalFailure" -> N.codec.encode(oldfailed.get.payload),
          "direction" -> A.codec.encode(direction.get),
          "originalHistorySize" -> Json.fromInt(before.get.history.size),
          "retainedCausations" -> Json.fromValues(current.history.map(entry => Json.fromString(entry.causation.value))),
          "continuedPlanRevision" -> Json.fromString(phase.work.get.selectedPlan.revision)).spaces2)
      }
    }
  }

  "E5 same-Session confirmed timeout corrective adoption" should afterWord(
    "in spec:goal-phase-failed-phase-repair-adoption, example:E5, rules:R1,R2,R3,R4,R5,R6,R7,R8, phase:6.5") {
    "offer timeout recovery choices for a real terminated timeout then ordinarily implement check review commit and validate one corrective Step" in {
      Given("docs/spec/goal-phase-failed-phase-repair-adoption.md R1-R8 E5: a NEW isolated Session with real Test failure output, a finite fixture wait and a short typed execution deadline")
      val root = newKnownRoot("terminated-timeout-repair-adoption")
      Files.writeString(root.resolve("terminated-timeout-fixture"), "Only this new isolated fixture selects the finite timeout input\n")
      var identity = ""
      var original = Option.empty[V.GoalPhaseAdvanceResult]
      inKnownEpisode(root, initialize = true, componentInstance = "default") { episode =>
        identity = episode.start().sessionId
        original = Some(episode.advance(identity))
        episode.submitImplementation(original.get).accepted shouldBe true
      }
      val config = root.resolve(".textus/config.json")
      val historysettings = _read(_primary.resolve("examples/source-history/goldenport-config.json")).asObject.get
      Files.writeString(config, _read(config).mapObject(fields =>
        historysettings.toVector.foldLeft(fields)((selected, entry) => selected.add(entry._1, entry._2))).noSpaces)
      if (sys.props.contains("smworkflow.spec.installedCommand")) {
        Files.writeString(config, Vector(1, 2).foldLeft(_read(config)) { (value, ordinal) =>
          value.mapObject(_.add("cncf.sbt.runtime-binding.sm-workflow-repository-validation-" + ordinal, Json.fromString("installed")))
        }.noSpaces)
      }
      val cli = new Cli(root, identity)
      When("ordinary Compile, independent Step review and local Step closure lead to actual Test timeout")
      val firstreview = cli.advance()
      firstreview.business[V.GoalPhaseAdvanceResult].work.get.kind shouldBe "ReviewStep"
      cli.acceptStep(firstreview)
      val interruptedcall = cli.advance()
      interruptedcall.business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
      var before = Option.empty[InstanceRecord]
      inKnownEpisode(root, componentInstance = "default")(episode => before = Some(episode.current(identity)))
      val closedtip = Files.readString(root.resolve("repository/.git/refs/heads/main"))
      val product = root.resolve("repository").resolve(_product)
      val originalsource = Files.readString(product)
      val repository = root.resolve("repository")
      val preservedpaths = Vector("value.txt", "build.sbt", "project/build.properties", "project/plugins.sbt",
        "src/main/scala/CompileConsumer.scala", _product, ".git/refs/heads/main", ".git/HEAD", ".git/logs/HEAD")
      val preserved = preservedpaths.map(path => path -> Files.readAllBytes(repository.resolve(path)).toVector)
      val retrycall = cli.goal(GoalPhaseValidationRecovery.describeOperation, Vector("--sessionId", identity))
      val retrydescription = retrycall.business[V.GoalPhaseValidationRecoveryDescriptionResult]
      val retrytarget = take(GoalPhaseValidationRecovery.targetC(retrydescription.target))
      var retainedtimeout = Option.empty[N.Saved]
      inKnownEpisode(root, componentInstance = "default") { episode =>
        retainedtimeout = episode.retained(identity, retrytarget.sourceRevision, "validation")
      }
      Then("the timeout offers deadline extension limited repair and deferral while retaining actual incomplete output")
      retrydescription.choices shouldBe Vector("AuthorizeNewAttempt", "LimitedRepair", "Defer")
      retrydescription.message should include("AuthorizeNewAttempt (deadline extension)")
      retrydescription.message should include("Timeout alone is not proof of a hang")
      retrytarget.knownFailedPhase shouldBe None
      retrytarget.terminatedTimeoutPhase should not be empty

      When("actual normal preparation and import provide exact LimitedRepair Human evidence before any corrective proposal or adoption")
      val limited = cli.limitedRepair(retrycall)
      val limitedresult = cli.goal(GoalPhaseValidationRecovery.recoveryOperation, Vector("--input", _json(limited)))
        .business[V.GoalPhaseValidationRecoveryResult]
      Then("only bounded diagnosis and planning are authorized and the original Decision remains available for separate concrete proposal adoption")
      limitedresult.accepted shouldBe true
      limitedresult.outcome shouldBe "limited-repair-preparation-required"
      limitedresult.message should include("fix known errors")
      limitedresult.message should include("actual start/end observations")
      limitedresult.message should include("retained output")
      limitedresult.message should include("diagnostic stack/process evidence")
      limitedresult.message should include("last printed test may already have completed")
      limitedresult.message should include("test identity unknown or evidence missing")
      limitedresult.message should include("normal slow execution or an external wait")
      limitedresult.message should include("CNCF owns execution and result interpretation")
      limitedresult.message should include("describeGoalPhaseFailedPhaseRepair")
      limitedresult.message should include("independently authorized adoptGoalPhaseFailedPhaseRepair")
      limitedresult.message should include("separate advanceGoalPhase")
      inKnownEpisode(root, componentInstance = "default") { episode =>
        episode.current(identity).history shouldBe before.get.history
        episode.current(identity).revision shouldBe before.get.revision
        N.codec.encode(episode.retained(identity, retrytarget.sourceRevision, "validation").get.payload) shouldBe
          N.codec.encode(retainedtimeout.get.payload)
      }
      preserved.foreach((path, bytes) => Files.readAllBytes(repository.resolve(path)).toVector shouldBe bytes)
      phaseExecutions(root).size shouldBe 1

      Given("the actual retained failed Test diagnostics and finite fixture wait support a captured concrete corrective proposal without claiming a hang")
      val oldplan = _recovery_plan
      val corrective = O.Step(_step, "Correct the actual timed-out product Test source", Vector(_product),
        oldplan.steps.map(_.identity), Vector("compile"))
      val proposed = oldplan.copy(revision = "terminated-timeout-repair-2", steps = oldplan.steps :+ corrective)
      val proposal = GoalPhasePlanRevision.Proposal("fixture-timeout-repair", "1", "Correct the failing test and its finite failure wait", proposed)
      When("read-only corrective description captures that concrete proposal at the unchanged original timeout Decision")
      val described = cli.describe(proposal)
      val description = described.business[V.GoalPhaseFailedPhaseRepairDescriptionResult]
      val target = take(A.boundTargetC(description.target)).toOption.get
      var originalvalidation = Option.empty[N.Saved]
      inKnownEpisode(root, componentInstance = "default") { episode =>
        originalvalidation = episode.retained(identity, target.source.sourceRevision, "validation")
        episode.current(identity).history shouldBe before.get.history
      }
      Then("the actual timeout retains incomplete typed facts and partial diagnostics without becoming a known Failed result")
      val receipt = originalvalidation.get.payload.receipts.head
      receipt.termination shouldBe "timed-out"
      receipt.exitCode shouldBe None
      receipt.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Incomplete
      receipt.stdout should include("*** FAILED ***")
      receipt.stdout should include("TERMINATED_TIMEOUT_FIXTURE_WAIT_AFTER_REAL_TEST_FAILURE")
      Files.readString(interruptedcall.stderr) should include("TimedOut")
      N.repairable(originalvalidation.get.payload) shouldBe false
      target.source.kind shouldBe I.kind
      target.source.decisionRevision shouldBe before.get.revision.value.toString
      target.source.validation.revision shouldBe before.get.revision.value.toString
      target.source.base shouldBe O.Snapshot(original.get.work.get.originalBase.head, original.get.work.get.originalBase.tree)
      target.plan shouldBe oldplan
      target.completed.map(_.commit.identity) shouldBe Vector(closedtip.trim)
      target.repairProgress shouldBe GoalPhaseRepairProgress.Snapshot(0, 0, 2, Some("sm-workflow.repair-trend.v1"), Some(2), Some(2))
      phaseExecutions(root).size shouldBe 1
      Files.readString(product) shouldBe originalsource
      retrytarget.knownFailedPhase shouldBe None
      retrytarget.terminatedTimeoutPhase shouldBe Some(target.source)
      Files.readString(root.resolve("repository/.git/refs/heads/main")) shouldBe closedtip

      When("the actual v2 description prepares Defer then a separate new Human direction for the exact corrective proposal")
      val deferred = cli.direction(described, proposal, "Defer", "fixture-timeout-defer")
      cli.adopt(deferred).business[V.GoalPhaseFailedPhaseRepairAdoptionResult].outcome shouldBe "deferred"
      val oldhuman = cli.adopt(deferred.copy(choice = "AdoptRepairPlan"))
      val crossed = cli.adopt(deferred.copy(target = A.targetRecord(target.copy(
        source = target.source.copy(candidate = target.source.base)))))
      Then("Defer writes nothing and old or crossed Human scope grants no adoption or effect")
      oldhuman.exit should not be 0
      crossed.exit should not be 0
      inKnownEpisode(root, componentInstance = "default")(episode => episode.current(identity).history shouldBe before.get.history)
      phaseExecutions(root).size shouldBe 1
      Files.readString(product) shouldBe originalsource
      Files.readString(root.resolve("repository/.git/refs/heads/main")) shouldBe closedtip

      When("the newly prepared actual Human direction adopts the complete timeout target")
      val authorized = cli.direction(described, proposal, "AdoptRepairPlan", "fixture-timeout-adopt-new-human")
      cli.adopt(authorized).business[V.GoalPhaseFailedPhaseRepairAdoptionResult].outcome shouldBe "failed-phase-repair-adopted"
      cli.adopt(authorized).exit should not be 0
      var directed = Option.empty[InstanceRecord]
      var saveddirection = Option.empty[A.InterruptedDirection]
      inKnownEpisode(root, componentInstance = "default") { episode =>
        directed = Some(episode.current(identity))
        episode.prepare(call => for {
          catalog <- GoalPhaseRuntimeCatalog.loadC()
          storage <- WorkflowComponentStorage.bindC(ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin),
            SmWorkflowComponent.componentId, _configuration_, Vector(catalog.definition), _collection_("instances"), _collection_("continuations"))
          reader <- storage.bindMechanicalC(GoalPhaseNativeRun.advanceOperation, A.interruptedCodec)
          retained <- reader.loadRetainedC(WorkflowMechanicalTransition.Reference(identity + ":ordered-failed-phase-repair-adoption:" +
            before.get.revision.value, "1"))(using call.executionContext)
          _ = saveddirection = retained.map(_.receipt.payload.value)
        } yield ())
      }
      Then("one adjacent v2 Direction retains the original history and has no File Git or SBT effect before Advance")
      directed.get.revision shouldBe before.get.revision.next
      directed.get.history.dropRight(1) shouldBe before.get.history
      directed.get.currentProgression shouldBe Some(ProgressionReference("phase.Implementation"))
      saveddirection.get.target shouldBe target
      saveddirection.get.reference shouldBe ContextReference(authorized.reference.identity, authorized.reference.revision)
      A.interruptedCodec.encode(saveddirection.get).hcursor.get[String]("schemaVersion").toOption shouldBe Some(I.schema)
      Files.readString(product) shouldBe originalsource
      Files.readString(root.resolve("repository/.git/refs/heads/main")) shouldBe closedtip
      phaseExecutions(root).size shouldBe 1

      When("the original accepted recovery-implementation reference is resubmitted with an altered payload before any corrective assignment is issued")
      val late = cli.goal(original.get.submitOperation.get, Vector("--sessionId", original.get.submitSessionId.get,
        "--result", _json(V.GoalPhaseWorkResult("completed", "Late altered old implementation result", Vector.empty,
          None, None, None, None, None, None, None, None, dependencyValidation = None)),
        "--resultReference", _json(V.WorkflowArtifactReference("recovery-implementation", "1"))))
      Then("the historical original result has no pending assignment and refuses without changing the adopted instance or repository effects")
      late.exit should not be 0
      inKnownEpisode(root, componentInstance = "default") { episode =>
        val current = episode.current(identity)
        current.history shouldBe directed.get.history
        current.revision shouldBe directed.get.revision
      }
      Files.readString(product) shouldBe originalsource
      Files.readString(root.resolve("repository/.git/refs/heads/main")) shouldBe closedtip
      phaseExecutions(root).size shouldBe 1

      When("a separate ordinary Advance traverses v2 and issues only the corrective implementation")
      val implementation = cli.advance().business[V.GoalPhaseAdvanceResult]
      cli.advance().business[V.GoalPhaseAdvanceResult] shouldBe implementation
      Then("the original base accepted prefix and repair counts remain with exactly one new generation")
      implementation.work.get.kind shouldBe "ImplementStep"
      implementation.work.get.stepIdentity shouldBe Some(_step)
      implementation.work.get.selectedPlan.revision shouldBe proposed.revision
      implementation.work.get.completed.map(_.commit.identity) shouldBe Vector(closedtip.trim)
      implementation.work.get.originalBase shouldBe original.get.work.get.originalBase
      implementation.issuedWorkContext.get.continuationId should not be original.get.issuedWorkContext.get.continuationId
      implementation.repairProgress shouldBe original.get.repairProgress
      phaseExecutions(root).size shouldBe 1

      When("the issued corrective Step makes an actual source change and continues through ordinary checks review and local commit")
      val predicate = "val ready = Files.isRegularFile(root.resolve(\"phase-ready\"))"
      originalsource should include(predicate)
      Files.writeString(product, originalsource.replace(predicate, "val ready = true"))
      cli.submit(implementation, V.WorkflowArtifactReference("fixture-timeout-corrective-implementation", "1"),
        V.GoalPhaseWorkResult("completed", "The exact corrective test source is repaired", Vector.empty,
          None, None, None, None, None, None, None, None, dependencyValidation = None))
      val correctionreview = cli.advance()
      val review = correctionreview.business[V.GoalPhaseAdvanceResult]
      Then("the new candidate has its own real successful Compile and independently assigned ReviewStep")
      review.work.get.kind shouldBe "ReviewStep"
      review.work.get.stepIdentity shouldBe Some(_step)
      review.work.get.review.get.changedPaths shouldBe Vector(_product)
      review.work.get.review.get.checks.head.exitCode shouldBe Some(0)
      phaseExecutions(root).size shouldBe 1
      cli.acceptStep(correctionreview)
      When("ordinary Step closure then runs a new whole-Phase Test on the corrective candidate")
      val phase = cli.advance().business[V.GoalPhaseAdvanceResult]
      cli.advance().business[V.GoalPhaseAdvanceResult] shouldBe phase
      Then("the same Session retains both accepted commits and the immutable original timeout while requesting independent Phase review")
      phase.work.get.kind shouldBe "ReviewPhase"
      phase.work.get.completed.map(_.step) shouldBe Vector("step-1", _step)
      phase.work.get.completed.head.commit.identity shouldBe closedtip.trim
      phase.work.get.completed.last.commit.parent shouldBe closedtip.trim
      phase.work.get.review.get.checks.head.exitCode shouldBe Some(0)
      phase.work.get.review.get.checks.head.sbt.get.outcome shouldBe "succeeded"
      phase.repairProgress shouldBe implementation.repairProgress
      phaseExecutions(root).size shouldBe 2
      inKnownEpisode(root, componentInstance = "default") { episode =>
        val current = episode.current(identity)
        current.history.take(before.get.history.size) shouldBe before.get.history
        current.history.count(_.causation.value.startsWith("ordered-failed-phase-repair-adoption:")) shouldBe 1
        val retained = episode.retained(identity, target.source.sourceRevision, "validation").get
        N.codec.encode(retained.payload) shouldBe N.codec.encode(originalvalidation.get.payload)
        retained.record.history shouldBe originalvalidation.get.record.history
        retained.record.revision shouldBe originalvalidation.get.record.revision
        Files.writeString(root.resolve("terminated-timeout-reopened-proof.json"), Json.obj(
          "sessionId" -> Json.fromString(identity), "originalTimeout" -> N.codec.encode(retained.payload),
          "direction" -> A.interruptedCodec.encode(saveddirection.get),
          "continuedPlanRevision" -> Json.fromString(phase.work.get.selectedPlan.revision),
          "originalHistorySize" -> Json.fromInt(before.get.history.size)).spaces2)
      }
    }
  }
}
