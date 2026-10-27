package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.file.{Files, Path, StandardOpenOption}
import java.time.Instant
import java.util.concurrent.TimeUnit
import org.goldenport.record.RecordPresentable
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseClosureRecovery as R
import GoalPhaseNativeClosure as K
import GoalPhasePartialCommitClosure as P
import GoalPhaseOrderedProgression as O
import NativeWorkflowCliFixture as F
import SplitPhaseExplicitPartitionFixture.take

/** Installed-image qualification only. All semantic/review/Human/authority
  * evidence belongs to these isolated fixture Sessions. A finite fixture Git
  * executable executes a real successful selected partial commit; no Workflow
  * row or execution receipt is seeded, edited or manufactured.
  * @since   Oct. 26, 2026
  * @version Oct. 27, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalPartialCommitClosureCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _required(name: String, entry: Boolean = false): Path = {
    val path = Path.of(sys.props.getOrElse(name, throw new IllegalArgumentException("Missing " + name)))
    require(path.isAbsolute && path.normalize == path, name + " requires an absolute normalized path")
    val actual = path.toRealPath()
    if (entry) path else actual
  }
  private lazy val _executable = _required("smworkflow.spec.installedCommand", entry = true)
  private def _selected_image(property: String): Path = {
    val root = _required(property)
    val selected = parse(Files.readString(root.resolve("selected"))).toOption.get
    selected.hcursor.get[String]("kind") shouldBe Right("cncf.installed-runtime-selection.v1")
    val directory = root.resolve(selected.hcursor.get[String]("installation_id").toOption.get)
    val manifest = parse(Files.readString(directory.resolve("runtime-installation.json"))).toOption.get
    manifest.hcursor.get[String]("kind") shouldBe Right("cncf.installed-runtime.v1")
    val modules = manifest.hcursor.get[Vector[Json]]("modules").toOption.get
    modules should not be empty
    modules.foreach { module =>
      val file = directory.resolve(module.hcursor.get[String]("path").toOption.get).normalize
      file.startsWith(directory.resolve("runtime")) shouldBe true
      Files.isRegularFile(file) shouldBe true
      Files.isSymbolicLink(file) shouldBe false
      file.getFileName.toString should endWith(".jar")
    }
    root
  }
  private lazy val _image = _selected_image("smworkflow.spec.partialCompletionInstallations")
  private val _paths = Vector("value.txt", "docs/one.md", "docs/two.md")
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def _result(outcome: String, observations: Boolean = false): V.GoalPhaseWorkResult =
    V.GoalPhaseWorkResult(outcome, "SPECIFICATION-OWNED exact accepted fixture result", Vector.empty,
      None, None, None, None, if (observations) Some(V.GoalPhaseReviewObservations(Vector(V.GoalPhaseReviewObservation("partial-note", "HYGIENE",
        "Retain the actual chain in the immutable journal", "Separate nonblocking specification observation")))) else None,
      None, None, None, dependencyValidation = None, diagnosticRepair = None)

  private final class Episode(val scenario: F.Scenario, val plan: O.Plan,
    val host: ThreeWorkflowRuntimeParameters.Definition, val wrapper: Path, var installations: Option[Path] = None) {
    val repository: Path = Path.of(host.target.root)
    val partialMarker: Path = scenario.root.resolve("inject-partial")
    var session = ""
    private var _sequence = 0
    def call(service: String, operation: String, arguments: Vector[String]): F.Result =
      scenario.callInstalled(_executable, service, operation, arguments, Some(installations.getOrElse(_image)),
        assemblyDescriptor = Some(scenario.root.resolve(".textus/assembly.yaml")))
    def goal(operation: String, arguments: Vector[String]): F.Result = call("GoalPhaseService", operation, arguments)
    def advance(): F.Result = goal("advanceGoalPhase", Vector("--sessionId", session))
    def file(name: String, value: RecordPresentable): String = {
      _sequence += 1
      val path = scenario.root.resolve(name + "-" + _sequence + ".json")
      Files.writeString(path, _json(value), StandardOpenOption.CREATE_NEW)
      path.toUri.toASCIIString
    }
    def prepare(operation: String, value: RecordPresentable): Unit = {
      val result = call("NativeWorkflowPreparationService", operation, Vector("--input", _json(value)))
      withClue(result.invocation.logs) { result.exitCode shouldBe 0 }
      scenario.selectPreparedImport(result)
    }
    def submit(request: V.GoalPhaseAdvanceResult, value: V.GoalPhaseWorkResult, name: String,
      execution: Option[V.WorkflowExecutionEvidence] = None): Unit = {
      val reference = V.WorkflowArtifactReference(name, "1")
      goal(request.submitOperation.get, Vector("--sessionId", request.submitSessionId.get, "--result", _json(value),
        "--resultReference", _json(reference)) ++ execution.toVector.flatMap(e => Vector("--executionEvidence", _json(e))))
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
    }
    def review(request: F.Result, name: String, observations: Boolean = false): Unit = {
      val value = _result("accept", observations)
      val reference = V.WorkflowArtifactReference(name, "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("independent-fixture-reviewer"), None, None)
      prepare("prepareEvidence", V.WorkflowEvidencePreparationInput("GoalPhase", "review", request.invocation.stdout.toUri.toASCIIString,
        file(name + "-result", value), file(name + "-reference", reference), file(name + "-execution", execution),
        "normal-development-host", Instant.now().toString, "entity:smworkflow.cli.host_evidence", None))
      submit(request.business[V.GoalPhaseAdvanceResult], value, name, Some(execution))
    }
    def authority(request: F.Result, name: String): Unit = prepare("prepareAuthority", V.WorkflowAuthorityPreparationInput(
      request.invocation.stdout.toUri.toASCIIString, file(name, V.WorkflowArtifactReference(name, "1")), "participant",
      "normal-development-host", Instant.now().toString, "entity:smworkflow.cli.host_evidence"))
    def select(paths: Vector[String], denyCommit: Boolean = false, candidatePaths: Vector[String] = _paths,
      secondSelection: Vector[String] = Vector("docs/two.md")): Unit = {
      val target = org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms.Target(repository,
        host.target.localBranch, host.target.remote, host.target.remoteBranch, host.target.endpoint)
      val denied = if (denyCommit) Set(TypedGitHostPlanFixture.capability(
        org.goldenport.cncf.processexecution.GitOperationKind.CommitSelected, Some("goal-step-1"))) else Set.empty[String]
      val git = TypedGitHostPlanFixture.plan(target, goalPaths = candidatePaths,
        goalMessages = Vector("Complete first accepted fixture Step", "Complete second accepted fixture Step"),
        goalSelections = Vector(paths, secondSelection), denied = denied)
        .mapObject(fields => fields.add("gitPrograms", fields("gitPrograms").get.mapArray(_.map(_.mapObject(
          _.add("gitExecutable", Json.fromString(wrapper.toString)))))))
      val combined = TypedSbtHostPlanFixture.plan(scenario.root, TypedSbtHostPlanFixture.normalExecutable, Vector(1), Some(git))
      val process = if (!denyCommit) combined else combined.mapObject(fields => fields.add("grants",
        fields("grants").get.mapArray(_.map(_.mapObject(grant => grant.add("capabilities",
          grant("capabilities").get.mapArray(_.filterNot(value => value.asString.exists(denied)))))))))
      val configfile = scenario.root.resolve(".textus/config.json")
      val config = parse(Files.readString(configfile)).toOption.get
      Files.writeString(configfile, config.mapObject(_.add("textus.subsystem.process-execution.plan", Json.fromString(process.noSpaces))).noSpaces)
    }
    /** Bounded real Git observation; setup mutations occur only before Start. */
    def git(arguments: Vector[String]): String = {
      _sequence += 1
      val stdout = scenario.root.resolve("git-" + _sequence + ".stdout")
      val stderr = scenario.root.resolve("git-" + _sequence + ".stderr")
      val command = Vector("/usr/bin/git", "--no-optional-locks", "--no-replace-objects", "-c", "core.hooksPath=/dev/null",
        "-c", "user.name=Partial Fixture", "-c", "user.email=partial@example.invalid", "-C", repository.toString) ++ arguments
      val process = new ProcessBuilder(command*).redirectOutput(stdout.toFile).redirectError(stderr.toFile).start()
      val ended = process.waitFor(30, TimeUnit.SECONDS)
      if (!ended) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); fail("Fixture Git observation did not terminate") }
      withClue(Files.readString(stderr)) { process.exitValue() shouldBe 0 }
      Files.readString(stdout).trim
    }
    def cells: Vector[String] = {
      val connection = new org.sqlite.JDBC().connect("jdbc:sqlite:" + scenario.root.resolve("workflow.sqlite").toUri.toASCIIString + "?mode=ro",
        new java.util.Properties())
      try {
        val statement = connection.createStatement()
        try {
          val rows = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")
          val names = Vector.newBuilder[String]
          try { while (rows.next()) names += rows.getString(1) } finally rows.close()
          names.result().flatMap { name =>
            val values = statement.executeQuery("SELECT * FROM \"" + name.replace("\"", "\"\"") + "\"")
            val result = Vector.newBuilder[String]
            try { while (values.next()) (1 to values.getMetaData.getColumnCount).foreach(i =>
              Option(values.getString(i)).foreach(result += _)) } finally values.close()
            result.result()
          }
        } finally statement.close()
      } finally connection.close()
    }
    def envelopes: Vector[Json] = cells.filter(_.startsWith("cncf.workflow-json.v1:")).map(raw =>
      parse(raw.stripPrefix("cncf.workflow-json.v1:")).toOption.get)
    def receipts: Vector[Json] = envelopes.filter(_.hcursor.get[String]("schemaVersion").contains("cncf.workflow-mechanical-transition.v1"))
    def payloads(schema: String): Vector[Json] = receipts.flatMap(_.hcursor.downField("payload").downField("value").focus)
      .filter(_.hcursor.get[String]("schemaVersion").contains(schema))
    def checks: Vector[String] = Files.readString(scenario.root.resolve("sbt-executions.txt")).linesIterator.toVector
    def description(): (F.Result, V.GoalPhaseClosureRecoveryDescriptionResult, R.RecoveryTarget) = {
      val call = goal(R.describeOperation, Vector("--sessionId", session))
      val description = call.business[V.GoalPhaseClosureRecoveryDescriptionResult]
      (call, description, take(R.targetC(description.target)))
    }
    def recover(): R.RecoveryTarget = {
      val (call, description, target) = this.description()
      description.choices should contain("CompletePartialCommit")
      R.choiceC(target, "AuthorizeNewAttempt").isSuccess shouldBe false
      R.choiceC(target, "ReconcileCommit").isSuccess shouldBe false
      val reference = V.WorkflowArtifactReference("fixture-partial-human", "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
      val answer = V.GoalPhaseClosureRecoveryAnswer("CompletePartialCommit", "SPECIFICATION-OWNED owner ended; finish the exact accepted remainder", true)
      prepare("prepareEvidence", V.WorkflowEvidencePreparationInput("GoalPhase", "closure-recovery", call.invocation.stdout.toUri.toASCIIString,
        file("human-answer", answer), file("human-reference", reference), file("human-execution", execution),
        "normal-development-host", Instant.now().toString, "entity:smworkflow.cli.host_evidence", Some("participant")))
      val before = git(Vector("rev-parse", "HEAD"))
      goal(R.recoveryOperation, Vector("--input", _json(V.GoalPhaseClosureRecoveryInput(session, description.target,
        answer.choice, answer.rationale, reference, true)))).business[V.GoalPhaseClosureRecoveryResult].accepted shouldBe true
      git(Vector("rev-parse", "HEAD")) shouldBe before
      target
    }
  }

  private def _ready(name: String, partial: Boolean, installations: Option[Path] = None): Episode = {
    val scenario = F.fresh("p67-partial-" + name, bootstrapSelectedInput = false,
      sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable))
    val host = scenario.host.copy(goal = scenario.host.goal.copy(candidatePaths = _paths,
      closure = scenario.host.goal.closure.copy(programs = Vector(K.StepProgram("step-1", 1), K.StepProgram("step-2", 2)))))
    val first = scenario.plan.steps.head.copy(ownedPaths = _paths)
    val plan = scenario.plan.copy(steps = Vector(first, first.copy(identity = "step-2", purpose = "Append a later accepted document",
      ownedPaths = Vector("docs/two.md"), dependencies = Vector(first.identity))))
    val wrapper = scenario.root.resolve("fixture-git")
    val marker = scenario.root.resolve("inject-partial")
    val script = """#!/bin/bash
      |set -e
      |commit=false
      |for arg in "$@"; do if [[ "$arg" == commit ]]; then commit=true; fi; done
      |if $commit && [[ -f __MARKER__ ]]; then
      |  args=()
      |  for arg in "$@"; do
      |    case "$arg" in docs/one.md|docs/two.md) ;; *) args+=("$arg");; esac
      |  done
      |  exec /usr/bin/git "${args[@]}"
      |fi
      |exec /usr/bin/git "$@"
      |""".stripMargin.replace("__MARKER__", "'" + marker.toString.replace("'", "'\\''") + "'")
    Files.writeString(wrapper, script, StandardOpenOption.CREATE_NEW)
    wrapper.toFile.setExecutable(true, true) shouldBe true
    val episode = new Episode(scenario, plan, host, wrapper, installations)
    Files.writeString(episode.repository.resolve("docs/one.md"), "initial one\n")
    Files.writeString(episode.repository.resolve("docs/two.md"), "initial two\n")
    episode.git(Vector("add", "--", "docs/one.md", "docs/two.md"))
    episode.git(Vector("commit", "--no-verify", "--no-gpg-sign", "-m", "Isolated document baseline before Start"))
    Files.writeString(scenario.root.resolve("goal-plan.json"), plan.asJson.noSpaces)
    val configfile = scenario.root.resolve(".textus/config.json")
    val config = parse(Files.readString(configfile)).toOption.get
    val journal = F.JournalHost(DevelopmentJournal.Policy("workflow", org.goldenport.cncf.datastore.DataStore.CollectionId.EntityStore(
      org.simplemodeling.model.datatype.EntityCollectionId("smworkflow", "cli", "development_journal"))))
    Files.writeString(configfile, config.mapObject(fields => journal.values.foldLeft(fields
      .add("sm-workflow.runtime.definition", Json.fromString(ThreeWorkflowRuntimeParameters.encode(host).noSpaces))
      .add("textus.resource.url.file.roots", Json.fromString(scenario.root.toString))) {
      case (current, (key, value)) => current.add(key, Json.fromString(value))
    }).noSpaces)
    episode.select(_paths)
    scenario.selectCompletionPolicy(GoalPhasePostCompletionSync.localPolicy)
    episode.prepare("prepareGoalStart", scenario.selectedGoalPreparationInput)
    val started = episode.goal("startGoalPhase", Vector("--input", scenario.input.noSpaces)).business[V.GoalPhaseStartResult]
    episode.session = started.sessionId
    val implementation = episode.advance().business[V.GoalPhaseAdvanceResult]
    Files.writeString(episode.repository.resolve("value.txt"), "expected\n")
    Files.writeString(episode.repository.resolve("docs/one.md"), "accepted one\n")
    Files.writeString(episode.repository.resolve("docs/two.md"), "accepted two\n")
    episode.submit(implementation, _result("completed"), name + "-implementation")
    val review = episode.advance()
    review.business[V.GoalPhaseAdvanceResult].work.get.kind shouldBe "ReviewStep"
    episode.review(review, name + "-review", observations = true)
    val closing = episode.advance()
    closing.business[V.GoalPhaseAdvanceResult].authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
    episode.authority(closing, name + "-authority")
    if (partial) Files.writeString(marker, "single original effect only\n", StandardOpenOption.CREATE_NEW)
    episode
  }
  private def _partial(name: String, installations: Option[Path] = None): Episode = {
    val episode = _ready(name, partial = true, installations = installations)
    val parent = episode.git(Vector("rev-parse", "HEAD"))
    val before = episode.checks
    val attempted = try episode.advance() finally Files.delete(episode.partialMarker)
    attempted.exitCode should not be 0
    val (_, description, target) = episode.description()
    description.outcome shouldBe "decision-required"
    target.execution.get.outcome shouldBe "succeeded"
    target.execution.get.termination shouldBe Some("Exited(0)")
    target.execution.get.exitCode shouldBe Some(0)
    target.parent.head shouldBe parent
    target.partialCompletion.get.remainder.map(_.path) shouldBe Vector("docs/one.md", "docs/two.md")
    episode.git(Vector("rev-list", "--count", parent + "..HEAD")) shouldBe "1"
    episode.git(Vector("diff", "--cached", "--name-only")) shouldBe "docs/one.md\ndocs/two.md"
    episode.checks shouldBe before
    episode
  }

  "E3 installed current inventory old to new continuation" should afterWord(
    "in spec:goal-phase-remaining-plan-adoption, example:E3, rules:R13,R14,R15,R16, phase:P67-T4-INV01") {
    "retain an old image partial chain while explicit new host registration and atomic adoption enable the current ABI path" in {
      Given("docs/spec/goal-phase-remaining-plan-adoption.md R13–R16 E3: explicit old/new installation roots and an actual old-image P→C→D episode; every semantic, review and Human input is SPECIFICATION-OWNED")
      val previous = _selected_image("smworkflow.spec.inventoryPreviousInstallations")
      val qualified = _selected_image("smworkflow.spec.inventoryInstallations")
      qualified should not be previous
      val episode = _partial("inventory-old-new", Some(previous))
      val recovery = episode.recover()
      val oldnextcall = episode.advance()
      val oldnext = oldnextcall.business[V.GoalPhaseAdvanceResult]
      oldnext.work.get.kind shouldBe "ImplementStep"
      val original = oldnext.work.get
      val chain = original.completed.head.partialCompletion.get
      chain.originalParent shouldBe V.GoalPhaseNativeSnapshot(recovery.parent.head, recovery.parent.tree)
      chain.partial.identity shouldBe recovery.observation.head
      val oldclosures = episode.payloads("sm-workflow.goal-phase-native-step-closure.v4")
      oldclosures.size shouldBe 1
      val oldcandidates = episode.payloads("sm-workflow.goal-phase-native-candidate.v2")
      val originalreceipts = episode.receipts.toSet
      val inventory = "src/main/car/abi-manifest.json"
      original.selectedPlan.steps(1).ownedPaths should not contain inventory
      When("the old issued implementation reports the exact scope gap and ordinary Advance issues an unanswered question")
      episode.submit(oldnext, V.GoalPhaseWorkResult("decision-needed", "SPECIFICATION-OWNED ABI inventory is outside the current Step", Vector.empty,
        None, None, None, None, None, None, None, None, dependencyValidation = None, diagnosticRepair = None), "inventory-scope-gap")
      val oldquestioncall = episode.advance()
      val oldquestion = oldquestioncall.business[V.GoalPhaseAdvanceResult]
      Then("the old image retains a real unanswered Human without adding file ownership or running another check")
      oldquestion.kind shouldBe "Human"
      val beforechecks = episode.checks
      val oldhead = episode.git(Vector("rev-parse", "HEAD"))
      val beforeadoption = episode.receipts.toSet
      When("a separate process selects the explicit new image and the fixture host appends candidate staging and current-Step Git registration")
      episode.installations = Some(qualified)
      val updatedhost = episode.host.copy(goal = episode.host.goal.copy(candidatePaths = _paths :+ inventory))
      val configfile = episode.scenario.root.resolve(".textus/config.json")
      val priorconfig = parse(Files.readString(configfile)).toOption.get
      Files.writeString(configfile, priorconfig.mapObject(_.add("sm-workflow.runtime.definition",
        Json.fromString(ThreeWorkflowRuntimeParameters.encode(updatedhost).noSpaces))).noSpaces)
      episode.select(_paths, candidatePaths = _paths :+ inventory, secondSelection = Vector("docs/two.md", inventory))
      val nextplan = episode.plan.copy(revision = "inventory-2", effectiveAcceptance = None,
        steps = episode.plan.steps.updated(1, episode.plan.steps(1).copy(
          ownedPaths = episode.plan.steps(1).ownedPaths :+ inventory)))
      val proposal = GoalPhasePlanRevision.publicProposal(Some(GoalPhasePlanRevision.Proposal("spec-inventory", "1",
        "SPECIFICATION-OWNED explicitly add the ABI inventory in the same Component", nextplan))).get
      val describedcall = episode.goal(GoalPhaseRemainingPlanAdoption.describeOperation, Vector("--input", _json(
        V.GoalPhaseRemainingPlanDescriptionInput(episode.session, proposal))))
      val description = describedcall.business[V.GoalPhaseRemainingPlanDescriptionResult]
      val target = take(GoalPhaseRemainingPlanAdoption.targetC(description.target))
      def publicPlan(value: O.Plan): V.GoalPhaseOrderedPlan = V.GoalPhaseOrderedPlan(
        value.identity, value.revision, value.phase, value.repository,
        value.steps.map(step => V.GoalPhaseOrderedStep(step.identity, step.purpose, step.ownedPaths,
          step.dependencies, step.checks, step.actionPolicy.map(policy => GoalPhaseNativeMetadata.record(policy.asJson)))),
        value.phaseChecks, value.repairLimit, GoalPhaseAcceptanceBoundary.publicAdoption(value.acceptance),
        GoalPhaseAcceptanceBoundary.publicEffective(value.effectiveAcceptance))
      Then("the new description reconstructs the exact old base, chain, counts and Plan while binding only the explicit inventory delta")
      describedcall.invocation.pid should not be oldquestioncall.invocation.pid
      target.plan.copy(effectiveAcceptance = None) shouldBe episode.plan
      publicPlan(target.plan) shouldBe original.selectedPlan
      V.GoalPhaseNativeSnapshot(target.base.head, target.base.tree) shouldBe original.originalBase
      target.completed.map(NativeWorkflowOperationOutput.goalClosedStep) shouldBe original.completed
      Some(GoalPhaseRepairProgress.publicSnapshot(target.repairProgress)) shouldBe oldquestion.repairProgress
      oldquestion.repairProgress shouldBe oldnext.repairProgress
      target.currentInventory.get.appendedPaths shouldBe Vector(inventory)
      target.currentInventory.get.candidatePaths shouldBe (_paths :+ inventory)
      target.currentInventory.get.stepIdentity shouldBe "step-2"
      target.currentInventory.get.componentInstance shouldBe description.componentInstance
      target.captured.plan.effectiveAcceptance shouldBe target.plan.effectiveAcceptance
      episode.payloads("sm-workflow.goal-phase-native-step-closure.v4") shouldBe oldclosures
      episode.payloads("sm-workflow.goal-phase-native-candidate.v2") shouldBe oldcandidates
      episode.checks shouldBe beforechecks
      val reference = V.WorkflowArtifactReference("spec-inventory-human", "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
      val input = V.GoalPhaseRemainingPlanAdoptionInput(episode.session, description.target, proposal,
        "SPECIFICATION-OWNED independently observed new inventory direction; the old Human remains unanswered", reference)
      def adopt(value: V.GoalPhaseRemainingPlanAdoptionInput): F.Result = episode.goal(
        GoalPhaseRemainingPlanAdoption.adoptionOperation, Vector("--input", _json(value)))
      When("public adoption is attempted before separate fixture-owned Human evidence admission")
      val missing = adopt(input)
      Then("the exact target alone grants no adoption")
      missing.exitCode should not be 0
      episode.receipts.toSet shouldBe beforeadoption
      When("passive evidence preparation and normal import bind the independently observed Human target, then public adoption runs atomically")
      episode.prepare("prepareEvidence", V.WorkflowEvidencePreparationInput("GoalPhase", "remaining-plan-adoption",
        describedcall.invocation.stdout.toUri.toASCIIString, episode.file("inventory-direction", input),
        episode.file("inventory-reference", reference), episode.file("inventory-execution", execution),
        "normal-development-host", Instant.now().toString, "entity:smworkflow.cli.host_evidence", Some("participant")))
      adopt(input).business[V.GoalPhaseRemainingPlanAdoptionResult].accepted shouldBe true
      Then("adoption retains every old receipt and performs no implementation, check or commit")
      episode.receipts.toSet should contain allElementsOf originalreceipts
      episode.git(Vector("rev-parse", "HEAD")) shouldBe oldhead
      episode.checks shouldBe beforechecks
      adopt(input).exitCode should not be 0
      When("a separate new-image Advance reenters and issues ordinary implementation with the appended inventory")
      val newcall = episode.advance()
      val next = newcall.business[V.GoalPhaseAdvanceResult]
      Then("the original completed contract and chain stay exact and the new path belongs only to current work")
      newcall.invocation.pid should not be describedcall.invocation.pid
      next.work.get.kind shouldBe "ImplementStep"
      next.work.get.stepIdentity shouldBe Some("step-2")
      next.work.get.selectedPlan shouldBe publicPlan(target.captured.plan)
      next.work.get.originalBase shouldBe original.originalBase
      next.work.get.completed shouldBe original.completed
      next.repairProgress shouldBe oldnext.repairProgress
      // WorkView does not publish generation; pure E3 asserts the exact +1 transition.
      episode.advance().business[V.GoalPhaseAdvanceResult] shouldBe next
      episode.payloads("sm-workflow.goal-phase-native-step-closure.v4") shouldBe oldclosures
      episode.payloads("sm-workflow.goal-phase-native-candidate.v2") shouldBe oldcandidates
      When("the assigned fixture work changes exactly its current document and ABI, then ordinary checks, review and scoped commit run")
      Files.createDirectories(episode.repository.resolve("src/main/car"))
      Files.writeString(episode.repository.resolve(inventory), "{\"specificationOwned\":true}\n")
      Files.writeString(episode.repository.resolve("docs/two.md"), "accepted two\nABI inventory consumed\n")
      episode.submit(next, _result("completed"), "inventory-implementation")
      val review = episode.advance()
      review.business[V.GoalPhaseAdvanceResult].work.get.kind shouldBe "ReviewStep"
      episode.checks.size should be > beforechecks.size
      episode.review(review, "inventory-review")
      val closing = episode.advance()
      episode.authority(closing, "inventory-authority")
      val phase = episode.advance().business[V.GoalPhaseAdvanceResult]
      Then("the new path is actually committed and fresh installed reentry retains the old partial chain and original base")
      phase.work.get.kind shouldBe "ReviewPhase"
      phase.work.get.completed.head.partialCompletion shouldBe Some(chain)
      phase.work.get.completed(1).commit.parent shouldBe oldhead
      phase.work.get.originalBase shouldBe original.originalBase
      episode.git(Vector("show", "HEAD:" + inventory)) shouldBe "{\"specificationOwned\":true}"
      episode.advance().business[V.GoalPhaseAdvanceResult] shouldBe phase
      episode.payloads("sm-workflow.goal-phase-native-step-closure.v4") shouldBe oldclosures
      episode.payloads("sm-workflow.goal-phase-native-candidate.v2").toSet should contain allElementsOf oldcandidates
    }
  }

  "E1 installed bounded partial completion" should afterWord(
    "in spec:goal-phase-closure-recovery, example:E1, rules:R7,R8, phase:P67-T1-CB01") {
    "retain one actual partial append one completion and preserve the chain through reentry journal next Step and Phase" in {
      Given("docs/spec/goal-phase-closure-recovery.md R7,R8 E1: the explicit new installed image and a real successful partial effect with two accepted staged documents")
      val episode = _partial("complete")
      val originals = episode.receipts.toSet
      val checks = episode.checks
      val target = episode.recover()
      val partial = target.observation.head
      When("a separate normal installed CLI Advance consumes only the retained bound completion direction")
      val call = episode.advance()
      val next = call.business[V.GoalPhaseAdvanceResult]
      Then("one D directly follows actual C with expected T and the same accepted work and old execution records")
      next.work.get.stepIdentity shouldBe Some("step-2")
      val closed = next.work.get.completed.head
      val chain = closed.partialCompletion.get
      chain.originalParent.head shouldBe target.parent.head
      chain.partial.identity shouldBe partial
      chain.completion shouldBe closed.commit
      closed.commit.parent shouldBe partial
      episode.git(Vector("rev-parse", "HEAD^")) shouldBe partial
      episode.git(Vector("rev-parse", "HEAD^{tree}")) shouldBe target.partialCompletion.get.expectedTree
      episode.git(Vector("rev-list", "--count", target.parent.head + "..HEAD")) shouldBe "2"
      episode.git(Vector("show", "HEAD:docs/one.md")) shouldBe "accepted one"
      episode.git(Vector("show", "HEAD:docs/two.md")) shouldBe "accepted two"
      episode.git(Vector("diff", "--cached", "--name-only")) shouldBe ""
      episode.receipts.toSet should contain allElementsOf originals
      episode.checks shouldBe checks
      episode.payloads("sm-workflow.goal-phase-partial-completion-intent.v1").size shouldBe 1
      episode.payloads("sm-workflow.goal-phase-partial-completion-execution.v1").size shouldBe 1
      val retained = episode.payloads("sm-workflow.goal-phase-native-step-closure.v4").map(K.codec.decode(_).toOption.get)
      retained.size shouldBe 1
      retained.head.partialCompletion.get.completion.parent shouldBe partial
      val completedhead = episode.git(Vector("rev-parse", "HEAD"))
      When("a second CLI process collects the retained Step and exposes the same next Step")
      val repeatedcall = episode.advance()
      val repeated = repeatedcall.business[V.GoalPhaseAdvanceResult]
      Then("reentry cannot replay the completion and the automatic journal keeps the actual chain")
      repeatedcall.invocation.pid should not be call.invocation.pid
      repeated shouldBe next
      episode.git(Vector("rev-parse", "HEAD")) shouldBe completedhead
      episode.checks shouldBe checks
      val found = episode.call("JournalService", "findJournalEntries", Vector("--input", _json(V.JournalFindInput(
        episode.plan.repository, Some(episode.plan.phase), Some(episode.session), Some("Finding"), None, None, None, Some(10)))))
        .business[V.JournalFindResult]
      found.entries should have size 1
      val journal = episode.call("JournalService", "readJournalEntry", Vector("--input", _json(V.JournalReadInput(
        found.entries.head.entry.identity, Some(found.entries.head.entry.revision))))).business[V.JournalReadResult]
      journal.entry.get.finding.get.completed.head.partialCompletion shouldBe Some(chain)
      When("ordinary fixture work then closes the next Step and completes required Phase validation and review")
      Files.writeString(episode.repository.resolve("docs/two.md"), "accepted two\nlater accepted entry\n")
      episode.submit(next, _result("completed"), "next-implementation")
      val review = episode.advance()
      episode.review(review, "next-review")
      val closing = episode.advance()
      episode.authority(closing, "next-authority")
      val phase = episode.advance()
      val phasework = phase.business[V.GoalPhaseAdvanceResult]
      phasework.work.get.kind shouldBe "ReviewPhase"
      phasework.work.get.completed.head.partialCompletion shouldBe Some(chain)
      phasework.work.get.completed(1).commit.parent shouldBe completedhead
      episode.review(phase, "phase-review")
      val terminal = episode.advance().business[V.GoalPhaseAdvanceResult]
      Then("Phase completion and a fresh passive process retain the exact chain and actual final native tips")
      terminal.completion.get.completed.head.partialCompletion shouldBe Some(chain)
      val replayed = episode.advance().business[V.GoalPhaseAdvanceResult]
      replayed.completion.get.completed shouldBe terminal.completion.get.completed
      episode.payloads("sm-workflow.goal-phase-partial-completion-execution.v1").size shouldBe 1
      val whole = episode.payloads("sm-workflow.goal-phase-native-candidate.v2").flatMap(value =>
        GoalPhaseNativeCandidate.codec.decode(value).toOption).flatMap(_.stepEvidence.toVector.flatten)
      whole.find(_.step == "step-1").get.partialCompletion shouldBe retained.head.partialCompletion
      episode.git(Vector("rev-list", "--count", target.parent.head + "..HEAD")) shouldBe "3"
    }
  }

  "E2 installed ordinary compatibility and refusal" should afterWord(
    "in spec:goal-phase-closure-recovery, example:E2, rules:R7,R8, phase:P67-T1-CB01") {
    "preserve ordinary direct-parent v3 closure and omit absent new fields" in {
      Given("docs/spec/goal-phase-closure-recovery.md R8 E2: a fresh ordinary fixture on exactly the selected installed image")
      val episode = _ready("ordinary", partial = false)
      val parent = episode.git(Vector("rev-parse", "HEAD"))
      When("ordinary owning Advance closes the complete physical selection once")
      val next = episode.advance().business[V.GoalPhaseAdvanceResult]
      Then("the ordinary commit is one native direct child and its v3 receipt contains no inferred partial chain")
      val closed = next.work.get.completed.head
      closed.commit.parent shouldBe parent
      closed.partialCompletion shouldBe None
      episode.git(Vector("rev-parse", "HEAD^")) shouldBe parent
      episode.git(Vector("rev-list", "--count", parent + "..HEAD")) shouldBe "1"
      episode.payloads("sm-workflow.goal-phase-native-step-closure.v4") shouldBe empty
      episode.payloads("sm-workflow.goal-phase-native-step-closure.v3").foreach(wire =>
        wire.hcursor.downField("payload").downField("partialCompletion").focus shouldBe None)
    }

    "stop incomplete ordinary selection before intent and commit" in {
      Given("docs/spec/goal-phase-closure-recovery.md R7 E2: a complete accepted candidate whose host omits both documents")
      val episode = _ready("coverage", partial = false)
      val parent = episode.git(Vector("rev-parse", "HEAD"))
      episode.select(Vector("value.txt"))
      When("ordinary Advance checks physical coverage before saving an effect intent")
      val refused = episode.advance()
      Then("no selected commit intent or execution is retained and the accepted documents remain staged")
      refused.exitCode should not be 0
      episode.git(Vector("rev-parse", "HEAD")) shouldBe parent
      episode.payloads("sm-workflow.goal-phase-closure-execution.v1") shouldBe empty
      episode.receipts.flatMap(_.hcursor.downField("reference").get[String]("identity").toOption).exists(_.endsWith(":intent")) shouldBe false
    }

    "reject selection drift after bound direction without a completion effect" in {
      Given("docs/spec/goal-phase-closure-recovery.md R7 E2: a real known partial and independently bound fixture direction")
      val episode = _partial("selection-drift")
      val target = episode.recover()
      episode.select(Vector("docs/one.md"))
      When("a separate installed Advance reobserves a changed selection")
      val refused = episode.advance()
      Then("the direction does not authorize a different remainder or physical selection")
      refused.exitCode should not be 0
      episode.git(Vector("rev-parse", "HEAD")) shouldBe target.observation.head
      episode.payloads("sm-workflow.goal-phase-partial-completion-intent.v1") shouldBe empty
      episode.payloads("sm-workflow.goal-phase-partial-completion-execution.v1") shouldBe empty
    }

    "retain an actual resultless completion intent as UNKNOWN after capability restoration without replay" in {
      Given("docs/spec/goal-phase-closure-recovery.md R8 E2: a known partial with exact bound Human direction and a withheld completion capability")
      val episode = _partial("unknown")
      val target = episode.recover()
      episode.select(_paths, denyCommit = true)
      When("Advance saves a real completion intent but the native effect has no execution result")
      val interrupted = episode.advance()
      Then("the resultless intent and original success remain separate immutable facts")
      interrupted.exitCode should not be 0
      val intents = episode.payloads("sm-workflow.goal-phase-partial-completion-intent.v1")
      intents.size shouldBe 1
      episode.payloads("sm-workflow.goal-phase-partial-completion-execution.v1") shouldBe empty
      episode.select(_paths)
      When("the capability returns and two later installed CLI processes perform ordinary Advance")
      val first = episode.advance().business[V.GoalPhaseAdvanceResult]
      val second = episode.advance().business[V.GoalPhaseAdvanceResult]
      Then("both report honest UNKNOWN Wait without executing or inventing a completion result")
      first.kind shouldBe "Wait"
      first.message should include("UNKNOWN")
      second.kind shouldBe "Wait"
      episode.git(Vector("rev-parse", "HEAD")) shouldBe target.observation.head
      episode.payloads("sm-workflow.goal-phase-partial-completion-intent.v1") shouldBe intents
      episode.payloads("sm-workflow.goal-phase-partial-completion-execution.v1") shouldBe empty
    }
  }
}
