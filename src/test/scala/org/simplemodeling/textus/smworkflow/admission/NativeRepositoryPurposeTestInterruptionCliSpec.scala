package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.file.{Files, LinkOption, Path, StandardOpenOption}
import org.goldenport.cncf.processexecution.SbtOperation
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import RepositorySyncNativeMergeValidation as C
import RepositorySyncPurpose as P
import RepositorySyncPurposeTestInterruption as T
import SplitPhaseExplicitPartitionFixture.take

/** Selected typed SBT and passive trusted fixture ingress on the same native Session.
  * @since   Nov. 20, 2026
  * @version Nov. 20, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeRepositoryPurposeTestInterruptionCliSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with NativeRepositoryContinuationCliFixture {
  private val _episode = "purposeTestInterruption"
  private val _route = "direct-test"
  private val _legacy_route = "legacy-direct-test"
  private val _advance = "advanceRepositorySync"
  private val _host_key = GoalPhaseWholePhaseCandidateRecovery.processPlanKey
  private val _program_key = "sm-workflow.repository-sync.program-inputs"
  private val _checks = Vector(C.Check("purpose-compile", 1, WorkflowSbtValidation.Kind.Compile),
    C.Check("purpose-test", 2, WorkflowSbtValidation.Kind.Test))
  private val _slot_one = "purpose-slot-1"
  private val _slot_two = "purpose-slot-2"
  private val _observer = "fixture-independent-cncf-observer"
  private val _flood = "flood\n"
  private val _fixed = "expected\n"

  "A resultless direct Purpose Test" should {
    "retain independently observed termination and continue the same Session through ordinary scoped Repair" in {
      Given("an isolated ordinary typed Compile and Test, where the first candidate's real Test emits output beyond both capture and receipt limits")
      val stage = episode_stage(_episode)
      val legacy = sys.props.get("smworkflow.spec.purposeTestInterruptionLegacy").contains("true")
      require(!legacy || (stage != "normal" && sys.props.contains("smworkflow.spec.installedCommand")),
        "Select the installed before or after stage for a legacy Purpose Test episode")
      val route = if (legacy) _legacy_route else _route
      val reopened = Option.when(stage == "after")(reopen_episode(_episode, route))
      val scenario = reopened.fold(_fresh())(_._1)
      if (stage == "after" || legacy) _bind_selected_component(scenario)
      val session = reopened.fold(start(scenario))(_._2)

      When("the original Purpose is checked through ordinary Advance")
      val failed = Option.when(stage != "after")(_interrupt(scenario, session, legacy))
      val original = current(scenario, session)
      val intent = _original_intent(scenario, session, original)
      val oldprogress = intent.payload.value.purposes.get
      val trace = _trace(scenario)
      val events = _events(trace)
      val started = _test_event(events, "started")
      val terminal = _test_event(events, "terminal")
      Then("the genuine CNCF evidence has one started Test and one confirmed output-limit termination, with a durable Compile prefix only")
      original.lifecycle shouldBe Lifecycle.Active
      original.suspension shouldBe None
      original.currentProgression.map(_.value) shouldBe Some("sync.PurposeChecking")
      oldprogress.receipts.map(_.identity) shouldBe Vector("index-local", _checks.head.identity)
      if (stage != "after") oldprogress.selection.get.depth shouldBe "FULL"
      C.productSuccessful(oldprogress.receipts.last, WorkflowSbtValidation.Kind.Compile) shouldBe true
      terminal.hcursor.get[String]("termination").toOption shouldBe Some("OutputLimitExceeded(Stdout)")
      terminal.hcursor.get[Int]("exitCode").toOption should not be empty
      started.hcursor.get[Long]("pid").toOption shouldBe terminal.hcursor.get[Long]("pid").toOption
      _test_results(scenario, session) shouldBe empty
      if (legacy) {
        intent.payload.value.purposeTestEvidenceRequirement shouldBe None
        _assert_original_plan_absent(scenario)
        events.filter(_.hcursor.get[String]("state").contains("started"))
          .map(event => event.hcursor.get[String]("operation").toOption.get ->
            event.hcursor.get[String]("workspaceId").toOption.get) shouldBe Vector("compile" -> _slot_one, "test" -> _slot_one)
        reopened.foreach { (_, _, stdout) =>
          Files.readString(Path.of(_read(_invocation_path(scenario)).hcursor.get[String]("stdout").toOption.get)) shouldBe stdout
        }
      }
      if (stage == "normal") intent.payload.value.purposeTestEvidenceRequirement shouldBe Some("strict-as-run-plan")
      if (stage == "before") {
        When("the old installed command's actual invocation, trace and native Session are retained for the second stage")
        retain_episode(_episode, route, scenario, session, failed.get.invocation.stdout)
        Then("the retained episode still names the original unsettled Test")
        Files.isRegularFile(trace) shouldBe true
        if (legacy) Files.notExists(_legacy_binding_path(scenario), LinkOption.NOFOLLOW_LINKS) shouldBe true
        current(scenario, session) shouldBe original
      } else _continue(scenario, session, original, oldprogress, started, terminal, legacy, stage == "normal")
    }
  }

  private def _read(path: Path): Json = parse(Files.readString(path)).fold(error => fail(error.message), identity)
  private def _array(value: Json, field: String): Vector[Json] = value.hcursor.get[Vector[Json]](field).toOption.get
  private def _plan(scenario: F.Scenario): Json =
    parse(_read(scenario.root.resolve(".textus/config.json")).hcursor.get[String](_host_key).toOption.get).toOption.get
  private def _success(result: F.Result): F.Result = {
    withClue(result.boundedDiagnostic) { result.exitCode shouldBe 0 }
    result
  }
  private def _repository(scenario: F.Scenario): Path = Path.of(scenario.host.target.root)
  private def _invocation_path(scenario: F.Scenario): Path = scenario.root.resolve("purpose-test-interruption-invocation.json")
  private def _host_plan_path(scenario: F.Scenario): Path = scenario.root.resolve("purpose-test-interruption-host-plan.json")
  private def _as_run_plan_path(scenario: F.Scenario): Path = scenario.root.resolve("purpose-test-interruption-as-run-plan.json")
  private def _legacy_binding_path(scenario: F.Scenario): Path = scenario.root.resolve("purpose-test-interruption-legacy-run.json")
  private def _assert_original_plan_absent(scenario: F.Scenario): Unit = {
    Files.notExists(_as_run_plan_path(scenario), LinkOption.NOFOLLOW_LINKS) shouldBe true
    Files.notExists(_host_plan_path(scenario), LinkOption.NOFOLLOW_LINKS) shouldBe true
  }
  private def _file_reference(path: Path): ContextReference = ContextReference(path.toUri.toASCIIString, "1")
  private def _trace(scenario: F.Scenario): Path = Path.of(_read(_invocation_path(scenario)).hcursor.get[String]("stderr").toOption.get)
  private def _events(trace: Path): Vector[Json] = Files.readString(trace).linesIterator
    .filter(_.startsWith("[cncf-sbt-event] "))
    .map(line => parse(line.stripPrefix("[cncf-sbt-event] ")).fold(error => fail(error.message), identity)).toVector
  private def _test_event(events: Vector[Json], state: String): Json = {
    val selected = events.filter(event => event.hcursor.get[String]("operation").contains("test") &&
      event.hcursor.get[String]("state").contains(state) && event.hcursor.get[String]("workspaceId").contains(_slot_one))
    selected.size shouldBe 1
    selected.head
  }
  private def _test_results(scenario: F.Scenario, session: String) = receipts(scenario, session).filter(receipt =>
    receipt.reference.identity.matches(".*-check-1-result"))
  private def _original_intent(scenario: F.Scenario, session: String, original: InstanceRecord) = {
    val intent = receipts(scenario, session).filter(_.next == original)
    intent.size shouldBe 1
    intent.head.reference.identity should endWith("-check-1-intent")
    intent.head
  }
  private def _bind_selected_component(scenario: F.Scenario): Unit = {
    val path = Path.of(sys.props.getOrElse("smworkflow.spec.componentFile", fail("Select the installed CAR for this stage")))
    require(path.isAbsolute && path.normalize == path && Files.isRegularFile(path), "Select the actual absolute CAR for this stage")
    configure(scenario, Map("textus.component.file" -> path.toString))
  }

  private def _host_plan(scenario: F.Scenario): Json = {
    val base = _plan(scenario)
    val firstgit = _array(base, "gitPrograms")
    val secondgit = firstgit.filter(_.hcursor.get[String]("target").contains(_slot_one)).map(_.mapObject(fields => fields
      .add("target", _slot_two.asJson)
      .add("identity", (fields("identity").get.asString.get + "-second-slot").asJson)
      .add("capability", (fields("capability").get.asString.get + "-second-slot").asJson)))
    val slots = Vector(_slot_one, _slot_two)
    val programs = slots.zipWithIndex.flatMap { (slot, index) =>
      Vector(SbtOperation.Compile, SbtOperation.Test).zipWithIndex.flatMap { (operation, position) =>
        val ordinal = index * _checks.size + position + 1
        val capture = if (slot == _slot_one && operation == SbtOperation.Test) 1048576 else 131072
        _array(TypedSbtHostPlanFixture.plan(scenario.root, TypedSbtHostPlanFixture.normalExecutable,
          Vector(ordinal), operation = operation, stdoutBytes = capture), "sbtPrograms")
          .map(_.mapObject(_.add("target", slot.asJson)))
      }
    }
    val workspace = Json.obj("identity" -> _slot_two.asJson, "root" -> scenario.root.resolve(_slot_two).toString.asJson)
    val grant = _array(base, "workspaceGrants").find(_.hcursor.get[String]("workspaceId").contains(_slot_one)).get
      .mapObject(_.add("workspaceId", _slot_two.asJson))
    val additions = (secondgit ++ programs).map(_.hcursor.get[Json]("capability").toOption.get)
    base.mapObject(fields => fields.add("gitPrograms", (firstgit ++ secondgit).asJson)
      .add("sbtPrograms", programs.asJson)
      .add("workspaces", (_array(base, "workspaces") :+ workspace).asJson)
      .add("workspaceGrants", (_array(base, "workspaceGrants") :+ grant).asJson)
      .add("grants", fields("grants").get.mapArray(_.map(_.mapObject(row => row.add("capabilities",
        row("capabilities").get.mapArray(values => (values ++ additions).distinct)))))))
  }

  private def _fresh(): F.Scenario = {
    val scenario = fresh("purpose-test-interruption", checked = true)
    val repository = _repository(scenario)
    val source = repository.resolve("src/test/scala/ProductCheck.scala")
    val original = Files.readString(source)
    val assertion = "    assert(java.nio.file.Files.readString(root.resolve(\"value.txt\"))"
    original should include(assertion)
    val flooding = """    if (java.nio.file.Files.readString(root.resolve("value.txt")) == "flood\n")
      |      (1 to 2048).foreach(_ => println("SPEC-PURPOSE-TEST-OUTPUT " + ("X" * 1024)))
      |""".stripMargin
    Files.writeString(source, original.replace(assertion, flooding + assertion))
    val compile = Files.createDirectories(repository.resolve("src/main/scala")).resolve("ProductValue.scala")
    Files.writeString(compile, "object ProductValue { val expected = \"expected\" }\n")
    git(scenario.root, "-C", repository.toString, "add", "--", "src/test/scala/ProductCheck.scala", "src/main/scala/ProductValue.scala")
    git(scenario.root, "-C", repository.toString, "commit", "--no-verify", "--no-gpg-sign", "-m", "Retain the isolated typed SBT build and Test")
    git(scenario.root, "-C", repository.toString, "push", "--no-verify", "--", scenario.host.target.endpoint, "HEAD:refs/heads/main")
    Files.writeString(repository.resolve("value.txt"), _flood)
    Files.createDirectories(scenario.root.resolve(_slot_two))
    val slot = P.Slot(_slot_one, _checks,
      GoalPhaseStepAssessment.Policy("purpose-test-interruption", "code", "required", "off", "off", Vector.empty))
    val paths = Vector("value.txt", "src", "build.sbt", "project")
    val impact = RepositorySyncProgramImpact.Policy(_checks.reverse.map(check => RepositorySyncProgramImpact.Coverage(check,
      paths, paths, "The ordinary product build and Test consume the selected source")), _checks,
      paths, "The candidate changes an input that requires full ordered validation", Vector.empty)
    configure(scenario, maintenance_parameters(review = true) ++ Map(
      "workflow.mechanical-receipt.max-bytes" -> "1048576",
      _program_key -> impact.asJson.noSpaces,
      "sm-workflow.repository-sync.dirty-purposes" -> P.Policy(Vector(slot, slot.copy(workspace = _slot_two)), Vector.empty).asJson.noSpaces,
      _host_key -> _host_plan(scenario).noSpaces))
    scenario
  }

  private def _interrupt(scenario: F.Scenario, session: String, legacy: Boolean): F.Result = {
    _success(advance(scenario, session)).business[V.RepositorySyncAdvanceResult].submitOperation shouldBe
      Some("submitRepositoryPurposeResult")
    val inventory = purpose_inputs(scenario).head
    val group = P.Group("isolated-product", "Change the selected product input", "fix: update the product input",
      Vector.empty, Vector(P.Content("value.txt", Some(_flood))), Vector.empty, "The ordinary Test consumes this input")
    val result = P.Result("resolved", "The selected regular product file is the only Purpose input", Vector(group),
      inventory.files.map(_.path).filterNot(_ == "value.txt"))
    _success(submit_purpose(scenario, session, result)).business[V.RepositorySyncConflictSubmissionResult].accepted shouldBe true
    val plan = Option.when(!legacy)(_plan(scenario))
    plan.foreach(value => Files.writeString(_as_run_plan_path(scenario), value.noSpaces, StandardOpenOption.CREATE_NEW))
    val failed = advance(scenario, session)
    withClue(failed.boundedDiagnostic) { failed.exitCode should not be 0 }
    failed.boundedDiagnostic.toLowerCase should include("mechanical")
    val invocation = failed.invocation
    val metadata = Json.obj("sessionId" -> session.asJson, "argv" -> invocation.argv.asJson, "pid" -> invocation.pid.asJson,
      "exitCode" -> failed.exitCode.asJson, "stdout" -> invocation.stdout.toString.asJson, "stderr" -> invocation.stderr.toString.asJson)
    Files.writeString(_invocation_path(scenario), metadata.noSpaces, StandardOpenOption.CREATE_NEW)
    plan.foreach { value =>
      val pid = _test_event(_events(invocation.stderr), "started").hcursor.get[Long]("pid").toOption.get
      val archive = T.AsRunPlan(_file_reference(_invocation_path(scenario)), _file_reference(invocation.stderr), pid, value)
      Files.writeString(_host_plan_path(scenario), archive.asJson.noSpaces, StandardOpenOption.CREATE_NEW)
    }
    failed
  }

  private def _public_reference(value: ContextReference): V.WorkflowArtifactReference =
    V.WorkflowArtifactReference(value.identity, value.revision)
  private def _prepare_observation(scenario: F.Scenario, described: F.Result, observation: T.Observation, kind: String): F.Result = {
    val directory = Files.createTempDirectory(scenario.root, "fixture-purpose-termination-")
    val references = observation.references.map(_public_reference)
    val execution = V.WorkflowExecutionEvidence(references, Some(observation.observer), None, None)
    val input = V.WorkflowEvidencePreparationInput("RepositorySync", kind,
      described.invocation.stdout.toUri.toASCIIString, save(directory, "observation.json", observation.asJson.noSpaces),
      save(directory, "reference.json", json(references.head)), save(directory, "execution.json", json(execution)),
      "normal-development-host", "2026-11-20T00:00:00Z", "entity:smworkflow.cli.host_evidence",
      Some(observation.target.context.participantIdentity))
    scenario.prepareHostInput("prepareEvidence", take(new org.goldenport.record.io.RecordDecoder().json(json(input))))
  }
  private def _target(described: F.Result): T.Target = {
    val description = described.business[V.RepositorySyncValidationContinuationDescriptionResult]
    take(RepositorySyncValidationContinuation.anyTargetC(description.target)) match {
      case RepositorySyncValidationContinuation.PurposeTestInterruptionTarget(value) => value
      case _ => fail("Expected the direct Purpose Test interruption policy")
    }
  }

  private def _describe_interruption(scenario: F.Scenario, session: String, legacy: Boolean): F.Result =
    if (legacy) scenario.repositoryCall(RepositorySyncValidationContinuation.describeOperation,
      Vector("--sessionId", session, "--purposeTestEvidencePolicy", "legacy-missing-plan"))
    else describe(scenario, session)

  private def _legacy_run(scenario: F.Scenario, session: String, original: InstanceRecord, target: T.Target,
    started: T.Started): T.LegacyRun = {
    _assert_original_plan_absent(scenario)
    val invocation = _read(_invocation_path(scenario)).hcursor
    val argv = invocation.get[Vector[String]]("argv").toOption.get
    val exitcode = invocation.get[Int]("exitCode").toOption.get
    invocation.get[String]("sessionId").toOption shouldBe Some(session)
    argv.count(_.endsWith("." + _advance)) shouldBe 1
    argv.sliding(2).toVector should contain (Vector("--sessionId", session))
    exitcode should not be 0
    val intent = _original_intent(scenario, session, original)
    val candidate = receipts(scenario, session).filter(_.reference.identity == target.candidateReference.identity)
    candidate.size shouldBe 1
    candidate.head.payload.value.stage shouldBe "PurposePrepared"
    candidate.head.payload.value.purposes.get.candidate.get.commit shouldBe target.candidate.commit
    candidate.head.next.revision.value.toString shouldBe target.candidateRevision
    val intentref = ContextReference(intent.reference.identity, intent.reference.revision)
    val candidateref = ContextReference(candidate.head.reference.identity, candidate.head.reference.revision)
    val revision = intent.next.revision.value.toString
    revision shouldBe target.frontierRevision
    intentref shouldBe target.intentReference
    candidateref shouldBe target.candidateReference
    T.LegacyRun(session, revision, intentref, candidateref, _file_reference(_invocation_path(scenario)),
      _file_reference(_trace(scenario)), _advance, exitcode, started.pid, "not-retained")
  }

  private def _write_legacy_binding(path: Path, binding: T.LegacyRun): ContextReference = {
    Files.writeString(path, binding.asJson.noSpaces, StandardOpenOption.CREATE_NEW)
    _file_reference(path)
  }

  private def _reject_legacy_crossings(scenario: F.Scenario, described: F.Result, default: F.Result,
    observation: T.Observation, binding: T.LegacyRun): Unit = {
    val directory = Files.createTempDirectory(scenario.root, "fixture-crossed-legacy-purpose-test-")
    val missingtrace = _file_reference(directory.resolve("missing-trace.stderr"))
    val crossedtrace = _file_reference(described.invocation.stderr)
    val foreignpid = observation.started.pid + 1
    Given("copies of the authentic trace with an extra selected Test event and their own matching invocation bindings")
    val frame = "[cncf-sbt-event] "
    val original = Files.readString(_trace(scenario)).linesIterator.toVector
    val started = observation.started.asJson
    val terminal = observation.terminal.asJson
    val waiting = started.mapObject(_.add("state", "waiting".asJson).add("pid", foreignpid.asJson))
    val launching = started.mapObject(_.remove("pid").add("state", "launching".asJson))
    val interrupted = Vector(("crossed-waiting", started, waiting), ("late-launching", terminal, launching)).map {
      (name, boundary, event) =>
        val index = original.indexWhere(line => line.startsWith(frame) && parse(line.stripPrefix(frame)).contains(boundary))
        index should be >= 0
        val path = directory.resolve(name + ".stderr")
        Files.writeString(path, original.patch(index + 1, Vector(frame + event.noSpaces), 0).mkString("\n") + "\n",
          StandardOpenOption.CREATE_NEW)
        val trace = _file_reference(path)
        val reference = _write_legacy_binding(directory.resolve(name + ".json"), binding.copy(traceReference = trace))
        observation.copy(traceReference = trace, legacyBindingReference = Some(reference))
    }
    When("the host imports these independently bound traces")
    val refusedtraces = interrupted.map(_prepare_observation(scenario, described, _, T.legacyObservationKind))
    Then("trace validation refuses a different waiting process or a relaunch after termination")
    refusedtraces.foreach { refused =>
      refused.exitCode should not be 0
      Files.readString(refused.invocation.stderr) should include("Legacy Purpose Test trace")
    }
    val cases = Vector(
      ("missing-trace", observation.copy(traceReference = missingtrace), binding.copy(traceReference = missingtrace)),
      ("crossed-trace", observation.copy(traceReference = crossedtrace), binding.copy(traceReference = crossedtrace)),
      ("foreign-process", observation.copy(started = observation.started.copy(pid = foreignpid),
        terminal = observation.terminal.copy(pid = foreignpid)), binding.copy(pid = foreignpid)),
      ("binding-pid", observation, binding.copy(pid = foreignpid)),
      ("binding-source", observation, binding.copy(frontierRevision = observation.target.candidateRevision)),
      ("binding-candidate", observation, binding.copy(candidateReference = binding.intentReference)))
    cases.foreach { (name, value, run) =>
      val reference = _write_legacy_binding(directory.resolve(name + ".json"), run)
      val refused = _prepare_observation(scenario, described, value.copy(legacyBindingReference = Some(reference)), T.legacyObservationKind)
      withClue(name) { refused.exitCode should not be 0 }
    }
    val missingbinding = observation.copy(legacyBindingReference = Some(_file_reference(directory.resolve("missing-binding.json"))))
    val suppliedplan = observation.copy(hostPlanReference = Some(_file_reference(_host_plan_path(scenario))))
    Vector(observation.copy(legacyBindingReference = None), missingbinding, suppliedplan).foreach { value =>
      _prepare_observation(scenario, described, value, T.legacyObservationKind).exitCode should not be 0
    }
    _prepare_observation(scenario, described, observation, T.observationKind).exitCode should not be 0
    _prepare_observation(scenario, default, observation, T.legacyObservationKind).exitCode should not be 0
    _prepare_observation(scenario, default, observation.copy(schemaVersion = T.observationSchema,
      target = _target(default), legacyBindingReference = None), T.observationKind).exitCode should not be 0
  }

  private def _continue(scenario: F.Scenario, session: String, original: InstanceRecord, oldprogress: P.Progress,
    started: Json, terminal: Json, legacy: Boolean, strictmarked: Boolean): Unit = {
    val repository = _repository(scenario)
    val head = git(scenario.root, "-C", repository.toString, "rev-parse", "HEAD")
    val index = git(scenario.root, "-C", repository.toString, "write-tree")
    val host = _read(scenario.root.resolve(".textus/config.json")).hcursor.get[String](_host_key).toOption.get
    val continuationstart = scenario.invocations.size
    Given("the isolated source repository's Git metadata is unavailable during passive recovery")
    val (target, recovered, drift) = _without_source_git(scenario, host) {
      _adopt_interruption(scenario, session, original, oldprogress, started, terminal, host, legacy, strictmarked)
    }
    git(scenario.root, "-C", repository.toString, "rev-parse", "HEAD") shouldBe head
    git(scenario.root, "-C", repository.toString, "write-tree") shouldBe index
    _repair_candidate(scenario, session, recovered, oldprogress, target, host, drift)
    if (legacy) {
      Then("recovery and ordinary Repair preserve the old episode without replaying its start or checks")
      val calls = scenario.invocations.drop(continuationstart)
      calls.flatMap(_.argv).exists(_.endsWith(".startRepositorySync")) shouldBe false
      calls.flatMap(call => _events(call.stderr)).filter(_.hcursor.get[String]("workspaceId").contains(_slot_one)) shouldBe empty
      _assert_original_plan_absent(scenario)
    }
  }

  private def _without_source_git[A](scenario: F.Scenario, host: String)(body: => A): A = {
    val metadata = _repository(scenario).resolve(".git")
    val held = scenario.root.resolve("purpose-test-interruption-held-git")
    val missing = scenario.root.resolve("purpose-test-interruption-missing-gitdir")
    require(Files.exists(metadata, LinkOption.NOFOLLOW_LINKS), "The isolated source repository must own its Git metadata")
    require(Files.notExists(missing, LinkOption.NOFOLLOW_LINKS), "The fixture Git pointer must refer to an absent path")
    Files.move(metadata, held)
    try {
      Files.createFile(metadata)
      try {
        Files.writeString(metadata, "gitdir: " + missing.toString + "\n", StandardOpenOption.WRITE)
        body
      } finally Files.delete(metadata)
    } finally {
      try Files.move(held, metadata)
      finally configure(scenario, Map(_host_key -> host))
    }
  }

  private def _adopt_interruption(scenario: F.Scenario, session: String, original: InstanceRecord, oldprogress: P.Progress,
    started: Json, terminal: Json, host: String, legacy: Boolean, strictmarked: Boolean): (T.Target, InstanceRecord, Json) = {
    val baseline = receipts(scenario, session).toSet
    val choice = if (legacy) T.legacyChoice else T.choice
    val kind = if (legacy) T.legacyObservationKind else T.observationKind
    val schema = if (legacy) T.legacyObservationSchema else T.observationSchema
    if (strictmarked) {
      When("the host asks for missing-plan recovery of a newly retained strict Test intent")
      val refused = _describe_interruption(scenario, session, legacy = true)
      Then("the marked intent rejects that route with a conflict")
      refused.exitCode should not be 0
      Files.readString(refused.invocation.stderr) should include("status=409")
      Files.readString(refused.invocation.stderr) should include("requires strict as-run plan evidence")
      current(scenario, session) shouldBe original
    }
    When("the public operation describes the unresolved direct Test")
    val default = _success(describe(scenario, session))
    val described = if (legacy) _success(_describe_interruption(scenario, session, legacy)) else default
    val description = described.business[V.RepositorySyncValidationContinuationDescriptionResult]
    val target = _target(described)
    Then("it binds the original selected candidate, check, ordered successful receipts and host program")
    description.choices shouldBe Vector(choice, "Defer")
    target.continuationPolicy shouldBe (if (legacy) T.legacyPolicy else T.policy)
    if (legacy) {
      default.business[V.RepositorySyncValidationContinuationDescriptionResult].choices shouldBe T.choices
      _target(default) shouldBe target.copy(continuationPolicy = T.policy)
    }
    target.check shouldBe _checks.last
    target.selectedChecks shouldBe _checks
    target.successfulPrefix.map(_.identity) shouldBe oldprogress.receipts.map(_.identity)
    target.candidate.commit shouldBe oldprogress.candidate.get.commit
    target.checkWorkspace shouldBe _slot_one
    current(scenario, session) shouldBe original

    When("the independently prepared Human defers or attempts repair without a provisioned termination observation")
    val deferred = direction(scenario, session, described, "Defer", false, false)
    _success(adopt(scenario, deferred)).business[V.RepositorySyncValidationContinuationAdoptionResult].outcome shouldBe "deferred"
    current(scenario, session) shouldBe original
    receipts(scenario, session).toSet shouldBe baseline
    val human = direction(scenario, session, described, choice, true, true)
    val observationref = ContextReference("fixture-purpose-test-termination-" + session, "1")
    val adoption = human.copy(executionEvidence = human.executionEvidence.copy(
      references = human.executionEvidence.references :+ _public_reference(observationref)))
    adopt(scenario, human).exitCode should not be 0
    adopt(scenario, adoption).exitCode should not be 0
    Then("the Human assertions alone leave the same unresolved native frontier")
    current(scenario, session) shouldBe original

    val startedevent = started.as[T.Started].fold(error => fail(error.message), identity)
    val terminalevent = terminal.as[T.Terminal].fold(error => fail(error.message), identity)
    val binding = Option.when(legacy)(_legacy_run(scenario, session, original, target, startedevent))
    val observation = T.Observation(schema, target, observationref, _observer,
      _file_reference(_invocation_path(scenario)), _file_reference(_trace(scenario)),
      Option.when(!legacy)(_file_reference(_host_plan_path(scenario))), startedevent, terminalevent,
      legacyBindingReference = binding.map(_write_legacy_binding(_legacy_binding_path(scenario), _)))
    When("the host attempts to import live or crossed process and target facts")
    val foreigncheck = target.check.copy(identity = "foreign-test")
    val invalid = Vector(
      observation.copy(terminal = observation.terminal.copy(state = "waiting")),
      observation.copy(terminal = observation.terminal.copy(pid = observation.terminal.pid + 1)),
      observation.copy(target = target.copy(check = foreigncheck, selectedChecks = target.selectedChecks.updated(target.checkIndex, foreigncheck))),
      observation.copy(target = target.copy(candidate = target.candidate.copy(commit = "a" * 40))))
    invalid.foreach(value => _prepare_observation(scenario, described, value, kind).exitCode should not be 0)
    binding.foreach(_reject_legacy_crossings(scenario, described, default, observation, _))
    Then("passive preparation refuses the altered observations without adopting anything")
    current(scenario, session) shouldBe original
    receipts(scenario, session).toSet shouldBe baseline

    val originalplan = parse(host).fold(error => fail(error.message), identity)
    val drift = originalplan.mapObject(fields => fields.add("sbtPrograms", fields("sbtPrograms").get.mapArray(_.map { program =>
      if (program.hcursor.get[String]("identity").contains(target.programIdentity))
        program.mapObject(row => row.add("limits", row("limits").get.mapObject(_.add("executionTimeoutMillis", 179999L.asJson))))
      else program
    })))
    When("the same program and workspace are described after a launch-plan option changes")
    configure(scenario, Map(_host_key -> drift.noSpaces))
    val changed = _success(_describe_interruption(scenario, session, legacy))
    val changedtarget = _target(changed)
    changedtarget.programIdentity shouldBe target.programIdentity
    changedtarget.checkWorkspace shouldBe target.checkWorkspace
    changedtarget.processPlan should not be target.processPlan
    if (!legacy) {
      val crossed = _prepare_observation(scenario, changed, observation.copy(target = changedtarget), kind)
      Then("the original invocation's independently archived plan refuses that new target")
      crossed.exitCode should not be 0
      Files.readString(crossed.invocation.stderr) should include("independently recorded as-run plan")
    }
    current(scenario, session) shouldBe original

    When("the host restores its original plan and separately provisions the observation")
    configure(scenario, Map(_host_key -> host))
    scenario.selectPreparedImport(_success(_prepare_observation(scenario, described, observation, kind)))
    if (legacy) {
      adopt(scenario, adoption.copy(target = default.business[V.RepositorySyncValidationContinuationDescriptionResult].target)).exitCode should not be 0
      adopt(scenario, adoption.copy(choice = T.choice)).exitCode should not be 0
      adopt(scenario, adoption.copy(priorExecutionEnded = false)).exitCode should not be 0
      adopt(scenario, adoption.copy(acceptOriginalUncertainty = false)).exitCode should not be 0
    }
    configure(scenario, Map(_host_key -> drift.noSpaces))
    adopt(scenario, adoption).exitCode should not be 0
    Then("a changed host cannot admit the observed prior invocation")
    current(scenario, session) shouldBe original
    receipts(scenario, session).toSet shouldBe baseline

    When("the original host is restored and the independent pair is adopted from a Resource while source Git is still unavailable")
    configure(scenario, Map(_host_key -> host))
    val resource = save(scenario.root, "purpose-test-interruption-adoption.json", json(adoption))
    val admitted = _success(scenario.repositoryCall(RepositorySyncValidationContinuation.adoptionOperation,
      Vector("--inputResource", resource)))
    Then("exact repeat and description are passive and only one direction retains original UNKNOWN effects")
    admitted.business[V.RepositorySyncValidationContinuationAdoptionResult].outcome shouldBe "adopted"
    _success(adopt(scenario, adoption)).business[V.RepositorySyncValidationContinuationAdoptionResult].outcome shouldBe "already-adopted"
    val repeated = _success(describe(scenario, session))
    repeated.business[V.RepositorySyncValidationContinuationDescriptionResult].outcome shouldBe "already-adopted"
    _target(repeated) shouldBe target
    val recovered = current(scenario, session)
    recovered.history.dropRight(1) shouldBe original.history
    recovered.currentProgression.map(_.value) shouldBe Some("sync.PurposeRepairRequired")
    recovered.suspension shouldBe None
    val (retained, history) = receipts(scenario, session).partition(_.next == recovered)
    retained.size shouldBe 1
    history.toSet shouldBe baseline
    val progress = retained.head.payload.value.purposes.get
    val retaineddirection = progress.interruption.get
    retaineddirection.originalEffects shouldBe "UNKNOWN"
    retaineddirection.observation.schemaVersion shouldBe schema
    retaineddirection.observation shouldBe observation
    progress.copy(interruption = None) shouldBe oldprogress
    _test_results(scenario, session) shouldBe empty
    (target, recovered, drift)
  }

  private def _repair_candidate(scenario: F.Scenario, session: String, recovered: InstanceRecord, oldprogress: P.Progress,
    target: T.Target, host: String, drift: Json): Unit = {
    When("separate ordinary Advance under the same host issues the scoped Repair")
    configure(scenario, Map(_host_key -> drift.noSpaces))
    try {
      advance(scenario, session).exitCode should not be 0
      current(scenario, session) shouldBe recovered
    } finally configure(scenario, Map(_host_key -> host))
    val issued = _success(advance(scenario, session))
    Then("the semantic assignment identifies the interrupted Test and its exact original candidate with no synthetic Test receipt")
    val result = issued.business[V.RepositorySyncAdvanceResult]
    result.message should include("UNKNOWN")
    result.submitOperation shouldBe Some("submitNativeMergeRepair")
    result.repair shouldBe None
    val retainedwork = result.retainedWork.getOrElse(fail("The issued Repair has no retained work reference"))
    val retaineddetail = _success(scenario.readRetainedWork(retainedwork))
    val detail = retaineddetail.businessBounded[V.WorkflowRetainedWorkReadResult]
    detail.availability shouldBe "available"
    val full = take(V.RepositorySyncAdvanceResult.createC(detail.response))
    full.issuedWorkContext shouldBe result.issuedWorkContext
    val repair = full.repair.getOrElse(fail("The retained response has no Repair assignment"))
    repair.validationFailure shouldBe Some(_checks.last.identity)
    repair.review.indexTree shouldBe target.candidate.tree
    repair.review.checks.map(_.identity) shouldBe Vector(_checks.head.identity)
    repair.files.map(_.path) shouldBe Vector("value.txt")
    repair.files.map(_.text) shouldBe Vector(_flood)
    val proof = take(full.controlSummary.get.toJsonStringC)
    proof should include("UNKNOWN")
    proof should include(target.intentReference.identity)
    val provenance = _read(retaineddetail.invocation.stdout).hcursor.downField("response")
      .downField("control_summary").downField("purpose_test_interruption").downField("original_plan_status").focus
    provenance shouldBe Option.when(target.continuationPolicy == T.legacyPolicy)("not-retained".asJson)
    _events(issued.invocation.stderr) shouldBe empty
    _test_results(scenario, session) shouldBe empty

    When("an ordinary Repair corrects the assigned product input and the next Advance runs the new candidate's selected checks")
    val replacement = V.RepositorySyncNativeMergeRepairResult("repaired", "Use the expected product input without diagnostic flooding",
      Vector(V.RepositorySyncNativeMergeRepairFile("value.txt", _fixed)), None)
    val reference = V.WorkflowArtifactReference("fixture-purpose-test-ordinary-repair", "1")
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("fixture-purpose-test-repairer"), None, None)
    _success(scenario.repositoryCall("submitNativeMergeRepair", Vector("--sessionId", session, "--result", json(replacement),
      "--resultReference", json(reference), "--executionEvidence", json(execution))))
      .business[V.RepositorySyncNativeMergeRepairSubmissionResult].accepted shouldBe true
    _success(advance(scenario, session))
    val validated = advance(scenario, session)
    Then("both selected checks succeed and the existing focused-test requirement still governs product acceptance")
    withClue(validated.boundedDiagnostic) { validated.exitCode should not be 0 }
    Files.readString(validated.invocation.stderr) should
      include("Code admission requires compilation and actually executed nonempty focused behavioral tests")
    val tests = _test_results(scenario, session)
    tests.size shouldBe 1
    tests.head.next.identity.value shouldBe session
    tests.head.reference.identity should include("-repair-1-")
    tests.map(_.reference.identity) should not contain (target.intentReference.identity.stripSuffix("-intent") + "-result")
    val repaired = tests.head.payload.value.purposes.get
    repaired.candidate.get.commit should not be target.candidate.commit
    repaired.attempt shouldBe oldprogress.attempt + 1
    repaired.slotCursor shouldBe oldprogress.slotCursor + 1
    repaired.interruption shouldBe None
    repaired.receipts.map(_.identity) shouldBe Vector("index-local", _checks.head.identity, _checks.last.identity)
    _checks.zip(repaired.receipts.drop(1)).foreach((check, receipt) => C.productSuccessful(receipt, check.kind) shouldBe true)
    _events(validated.invocation.stderr).filter(_.hcursor.get[String]("state").contains("started"))
      .map(event => event.hcursor.get[String]("operation").toOption.get ->
        event.hcursor.get[String]("workspaceId").toOption.get) shouldBe Vector("compile" -> _slot_two, "test" -> _slot_two)
  }
}
