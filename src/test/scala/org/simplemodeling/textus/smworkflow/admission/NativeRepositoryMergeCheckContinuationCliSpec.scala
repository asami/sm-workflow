package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import java.nio.file.attribute.PosixFilePermissions
import org.goldenport.cncf.processexecution.SbtOperation
import org.goldenport.cncf.workflow.WorkflowInstanceRecordJsonV1
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import RepositorySyncNativeMergeValidation as C
import RepositorySyncNativeProgressStore as N
import RepositorySyncPurpose as P
import RepositorySyncPushContinuation as U
import RepositorySyncMergeCheckContinuation as M
import SplitPhaseExplicitPartitionFixture.take

/** One real resultless merge-check episode retained across normal publication.
  * Select mergeCheckPhase=before|after and an absolute mergeCheckEpisode JSON
  * path, the normal installed command and the normal SBT executable. The after
  * phase reopens the same physical repository, host, SQLite and Session.
  * Human directions and reviews are explicitly authored fixture inputs; they
  * attest no real consumer approval or independent source review.
  * @since   Nov. 4, 2026
  * @version Nov. 4, 2026
  * @author  ASAMI, Tomoharu
  */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeRepositoryMergeCheckContinuationCliSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with NativeRepositorySelectionCorrectionFixture {
  private val _checks = Vector(C.Check("merge-compile", 1, WorkflowSbtValidation.Kind.Compile),
    C.Check("merge-test", 2, WorkflowSbtValidation.Kind.Test),
    C.Check("merge-fix-check", 3, WorkflowSbtValidation.Kind.FixCheck),
    C.Check("merge-test-only", 4, WorkflowSbtValidation.Kind.TestOnly))
  private val _documents = Vector("docs/merge-a.md", "docs/merge-b.md")
  private val _source = "src/main/scala/fixture/MergeInput.scala"
  private val _baseline = "package fixture\nobject MergeInput { val value = \"baseline\"; val remote = \"baseline\" }\n"
  private val _ours = "package fixture\nobject MergeInput { val value = \"expected\"; val remote = \"baseline\" }\n"
  private val _peer = "package fixture\nobject MergeInput { val value = \"baseline\"; val remote = \"upstream\" }\n"
  private val _resolved = "package fixture\nobject MergeInput { val value = \"expected\"; val remote = \"upstream\" }\n"
  private val _messages = Vector("docs: retain merge purpose A", "docs: retain merge purpose B", "fix: retain merge product input")

  private def _phase: String = {
    val phase = sys.props.getOrElse("smworkflow.spec.mergeCheckPhase", fail("Select explicit mergeCheckPhase=before|after"))
    Set("before", "after") should contain(phase)
    phase
  }
  private def _mode: String = {
    val mode = sys.props.getOrElse("smworkflow.spec.mergeCheckMode", "retained")
    Set("retained", "refusal") should contain(mode)
    if (mode == "refusal") _phase shouldBe "after"
    mode
  }
  private def _episode_path: Path = {
    val path = Path.of(sys.props.getOrElse("smworkflow.spec.mergeCheckEpisode", fail("Select an absolute mergeCheckEpisode JSON path")))
    path.isAbsolute shouldBe true
    path.normalize shouldBe path
    Files.isDirectory(path.getParent) shouldBe true
    path.getFileName.toString should endWith(".json")
    path
  }
  private def _array(value: Json, key: String): Vector[Json] = value.hcursor.get[Vector[Json]](key).toOption.get
  private def _repository(scenario: F.Scenario): Path = Path.of(scenario.host.target.root)
  private def _head(scenario: F.Scenario): String = git(scenario.root, "-C", _repository(scenario).toString, "rev-parse", "HEAD")
  private def _tree(scenario: F.Scenario): String = git(scenario.root, "-C", _repository(scenario).toString, "write-tree")
  private def _remote(scenario: F.Scenario): String = git(scenario.root, "--git-dir=" + scenario.host.target.endpoint,
    "rev-parse", "refs/heads/main")
  private def _record(scenario: F.Scenario, session: String): String = take(WorkflowInstanceRecordJsonV1.encodeC(current(scenario, session)))
  private def _invocations(scenario: F.Scenario): Vector[String] = {
    val path = scenario.root.resolve("merge-sbt-invocations.txt")
    if (Files.exists(path)) Files.readString(path).linesIterator.toVector else Vector.empty
  }
  private def _formal(scenario: F.Scenario, session: String): Vector[(String, C.Receipt)] = receipts(scenario, session)
    .flatMap { receipt =>
      val key = receipt.reference.identity
      if (key.matches(".*-check-[0-9]+-result") || key.contains("validation-result-"))
        receipt.payload.value.validation.map(_.receipts).orElse(receipt.payload.value.purposes.map(_.receipts))
          .flatMap(_.lastOption).filter(_.sbt.nonEmpty).map(key -> _)
      else None
    }.sortBy(_._1)
  private def _commits(scenario: F.Scenario, session: String): Vector[String] = receipts(scenario, session)
    .filter(_.payload.value.stage == "PurposeInstalled").sortBy(_.next.revision.value)
    .flatMap(_.payload.value.purposes.toVector.flatMap(_.commits)).distinct
  private def _native(scenario: F.Scenario, session: String): Vector[Json] = receipts(scenario, session).map(receipt => Json.obj(
    "reference" -> receipt.reference.identity.asJson,
    "record" -> take(WorkflowInstanceRecordJsonV1.encodeC(receipt.next)).asJson,
    "payload" -> N.codec.encode(receipt.payload.value))).sortBy(_.hcursor.get[String]("reference").toOption.get)
  private def _snapshot(scenario: F.Scenario, session: String): Json = Json.obj(
    "record" -> _record(scenario, session).asJson, "native" -> _native(scenario, session).asJson,
    "head" -> _head(scenario).asJson, "indexTree" -> _tree(scenario).asJson, "remote" -> _remote(scenario).asJson,
    "trackingTip" -> git(scenario.root, "-C", _repository(scenario).toString, "rev-parse", "refs/remotes/origin/main").asJson,
    "mergeHead" -> Files.readString(_repository(scenario).resolve(".git/MERGE_HEAD")).asJson,
    "status" -> git(scenario.root, "-C", _repository(scenario).toString, "status", "--porcelain").asJson,
    "source" -> Files.readString(_repository(scenario).resolve(_source)).asJson,
    "indexSource" -> git(scenario.root, "-C", _repository(scenario).toString, "show", ":" + _source).asJson,
    "indexBytes" -> java.util.Base64.getEncoder.encodeToString(Files.readAllBytes(_repository(scenario).resolve(".git/index"))).asJson,
    "commits" -> _commits(scenario, session).asJson, "formal" -> _formal(scenario, session).asJson,
    "invocations" -> _invocations(scenario).asJson)
  private def _capture(result: F.Result): Json = _capture_invocation(result.invocation)
    .mapObject(_.add("exitCode", result.exitCode.asJson))
  private def _capture_invocation(invocation: F.Invocation): Json = Json.obj("argv" -> invocation.argv.asJson,
    "pid" -> invocation.pid.asJson, "stdoutPath" -> invocation.stdout.toString.asJson,
    "stderrPath" -> invocation.stderr.toString.asJson, "stdout" -> Files.readString(invocation.stdout).asJson,
    "stderr" -> Files.readString(invocation.stderr).asJson)
  private def _save_new(root: Path, name: String, text: String): String = {
    val path = root.resolve(name)
    Files.writeString(path, text, UTF_8, StandardOpenOption.CREATE_NEW)
    path.toUri.toASCIIString
  }
  /** Observations are durable before a mutating operation. They are never
    * imported as native receipts, execution evidence, or replay instructions.
    */
  private def _checkpoint(scenario: F.Scenario, session: Option[String], stage: String, detail: Json = Json.obj()): Unit = {
    val directory = Files.createTempDirectory(scenario.root, "fixture-merge-check-" + stage + "-")
    Files.writeString(directory.resolve("checkpoint.json"), Json.obj(
      "episode" -> _episode_path.toString.asJson, "root" -> scenario.root.toString.asJson,
      "session" -> session.asJson, "stage" -> stage.asJson, "detail" -> detail,
      "record" -> session.map(_record(scenario, _)).asJson,
      "native" -> session.toVector.flatMap(_native(scenario, _)).asJson,
      "head" -> _head(scenario).asJson, "remote" -> _remote(scenario).asJson,
      "status" -> git(scenario.root, "-C", _repository(scenario).toString, "status", "--porcelain").asJson,
      "logs" -> scenario.invocations.map(_capture_invocation).asJson).noSpaces, UTF_8, StandardOpenOption.CREATE_NEW)
  }
  private def _advance(scenario: F.Scenario, session: String, stage: String): F.Result = {
    _checkpoint(scenario, Some(session), stage)
    advance(scenario, session)
  }

  private def _plan(scenario: F.Scenario, corrected: Boolean): Json = {
    val base = process_plan(scenario.root, "Equal", scenario.host.goal)
    val owner = org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId.name
    def _clone_(program: Json, slot: Int): Json = program.mapObject(fields => fields
      .add("target", ("purpose-slot-" + slot).asJson)
      .add("identity", (fields("identity").get.asString.get + "-merge-slot-" + slot).asJson)
      .add("capability", (fields("capability").get.asString.get + "-merge-slot-" + slot).asJson))
    val gitprograms = _array(base, "gitPrograms") ++ (2 to 3).toVector.flatMap(slot =>
      _array(base, "gitPrograms").filter(_.hcursor.get[String]("target").toOption.contains("purpose-slot-1"))
        .map(_clone_(_, slot)))
    val plans = Vector(SbtOperation.Compile, SbtOperation.Test, SbtOperation.FixCheck, SbtOperation.TestOnly)
      .zipWithIndex.map { (operation, index) => TypedSbtHostPlanFixture.plan(scenario.root,
        TypedSbtHostPlanFixture.normalExecutable, Vector(index + 1), operation = operation,
        testClasses = if (operation == SbtOperation.TestOnly) Vector(selectedClass) else Vector.empty) }
    val purposeprograms = plans.flatMap(_array(_, "sbtPrograms")).map(_clone_(_, 3))
    val mergeprograms = if (corrected) plans.flatMap(_array(_, "sbtPrograms")) else Vector.empty
    val sbtprograms = purposeprograms ++ mergeprograms
    val slots = (2 to 3).toVector.map { slot =>
      val name = "purpose-slot-" + slot
      Files.createDirectories(scenario.root.resolve(name))
      Json.obj("identity" -> name.asJson, "root" -> scenario.root.resolve(name).toString.asJson)
    }
    val slotgrants = slots.map(slot => Json.obj("componentId" -> owner.asJson,
      "workspaceId" -> slot.hcursor.get[Json]("identity").toOption.get, "access" -> "manage".asJson))
    def _root_workspace_(value: Json, field: String): Boolean = value.hcursor.get[String](field).toOption
      .exists(_.startsWith("sm-workflow-repository-validation-"))
    val workspaces = _array(base, "workspaces").filterNot(_root_workspace_(_, "identity")) ++ slots ++
      (if (corrected) plans.flatMap(_array(_, "workspaces")) else Vector.empty)
    val workspacegrants = _array(base, "workspaceGrants").filterNot(_root_workspace_(_, "workspaceId")) ++ slotgrants ++
      (if (corrected) plans.flatMap(_array(_, "workspaceGrants")) else Vector.empty)
    val capabilities = (gitprograms ++ sbtprograms).map(_.hcursor.get[Json]("capability").toOption.get)
    base.mapObject(fields => fields.add("gitPrograms", gitprograms.asJson).add("sbtPrograms", sbtprograms.asJson)
      .add("workspaces", workspaces.distinct.asJson).add("workspaceGrants", workspacegrants.distinct.asJson)
      .add("grants", fields("grants").get.mapArray(_.map(_.mapObject(grant => grant.add("capabilities",
        grant("capabilities").get.mapArray(values => (values.filterNot(value =>
          value.asString.exists(_.startsWith("sm-workflow-repository-validation-"))) ++ capabilities).distinct)))))))
  }
  private def _parameters: Map[String, String] = {
    val paths = Vector("src", "build.sbt", "project", "value.txt", ".scalafix.conf")
    val impact = RepositorySyncProgramImpact.Policy(_checks.map(check => RepositorySyncProgramImpact.Coverage(
      check, paths, paths, "The actual Scala candidate requires this fixed product check")), _checks,
      paths, "The overlapping real Scala behavior requires all four original checks", Vector.empty)
    val slots = (1 to 3).toVector.map(slot => P.Slot("purpose-slot-" + slot,
      if (slot == 3) _checks else Vector.empty, GoalPhaseStepAssessment.Policy("merge-purpose-" + slot,
        if (slot == 3) "code" else "documentation", "required", "off", "off", Vector.empty)))
    maintenance_parameters(review = true) ++ Map(
      "sm-workflow.repository-sync.program-inputs" -> impact.asJson.noSpaces,
      "sm-workflow.repository-sync.dirty-purposes" -> P.Policy(slots, Vector.empty).asJson.noSpaces,
      "cncf.resource.read.max-bytes" -> "67108864", "cncf.resource.write.max-bytes" -> "67108864")
  }
  private def _fresh(): F.Scenario = {
    val scenario = fresh("c01-merge-check-continuation", checked = true)
    val repository = _repository(scenario)
    val descriptor = scenario.root.resolve(".textus/assembly.yaml")
    val assembly = Files.readString(descriptor, UTF_8)
    assembly should include("      id: participant\n")
    Files.writeString(descriptor, assembly.replace("      id: participant\n", "      id: sm-workflow-local\n"), UTF_8)
    Files.createDirectories(repository.resolve(_source).getParent)
    Files.createDirectories(repository.resolve("docs"))
    Files.writeString(repository.resolve(_source), _baseline)
    Files.writeString(repository.resolve("value.txt"), "expected\n")
    _documents.foreach(path => Files.writeString(repository.resolve(path), "baseline document\n"))
    Files.writeString(repository.resolve("project/plugins.sbt"),
      "addSbtPlugin(\"ch.epfl.scala\" % \"sbt-scalafix\" % \"0.12.1\")\n")
    Files.writeString(repository.resolve(".scalafix.conf"), "rules = [DisableSyntax]\nDisableSyntax.noFinalize = true\n")
    Files.writeString(repository.resolve("build.sbt"), """scalaVersion := "2.12.18"
      |libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % Test
      |addCommandAlias("fixCheck", "Compile / scalafix --check")
      |Global / onLoad := {
      |  val original = (Global / onLoad).value
      |  state => {
      |    val loaded = original(state)
      |    IO.append(file(__MARKER__), java.lang.ProcessHandle.current().pid().toString + "\n")
      |    loaded
      |  }
      |}
      |""".stripMargin.replace("__MARKER__", scenario.root.resolve("merge-sbt-invocations.txt").toString.asJson.noSpaces))
    val test = repository.resolve("src/test/scala/fixture/SelectedInputSpec.scala")
    Files.createDirectories(test.getParent)
    Files.writeString(test, """package fixture
      |class SelectedInputSpec extends org.scalatest.wordspec.AnyWordSpec
      |    with org.scalatest.GivenWhenThen with org.scalatest.matchers.should.Matchers {
      |  "the real merge input" should {
      |    "retain the intended local value and one complete remote revision" in {
      |      Given("the actual Scala candidate compiled in this workspace")
      |      When("both values are read from the compiled product")
      |      val observed = (MergeInput.value, MergeInput.remote)
      |      Then("the accepted local behavior and complete remote input agree")
      |      observed._1 shouldBe "expected"
      |      Vector("baseline", "upstream") should contain(observed._2)
      |    }
      |  }
      |}
      |""".stripMargin)
    git(scenario.root, "-C", repository.toString, "add", "--", "src", "docs", "value.txt", "build.sbt", "project", ".scalafix.conf")
    git(scenario.root, "-C", repository.toString, "commit", "-m", "Tiny real Scala merge continuation baseline")
    git(scenario.root, "-C", repository.toString, "push", scenario.host.target.endpoint, "HEAD:refs/heads/main")
    val initialplan = _plan(scenario, corrected = false)
    _array(initialplan, "sbtPrograms").foreach(_.hcursor.get[String]("target").toOption.get shouldBe "purpose-slot-3")
    _array(initialplan, "workspaces").exists(_.hcursor.get[String]("identity").toOption
      .exists(_.startsWith("sm-workflow-repository-validation-"))) shouldBe false
    _array(initialplan, "workspaceGrants").exists(_.hcursor.get[String]("workspaceId").toOption
      .exists(_.startsWith("sm-workflow-repository-validation-"))) shouldBe false
    val grantedSbt = _array(initialplan, "grants").flatMap(_array(_, "capabilities")).filter(_.asString
      .exists(_.startsWith("sm-workflow-repository-validation-"))).toSet
    grantedSbt shouldBe _array(initialplan, "sbtPrograms").map(_.hcursor.get[Json]("capability").toOption.get).toSet
    configure(scenario, _parameters + ("textus.subsystem.process-execution.plan" -> initialplan.noSpaces))
    _checkpoint(scenario, None, "before-dirty-inputs")
    _documents.zipWithIndex.foreach((path, index) => Files.writeString(repository.resolve(path), "accepted document " + (index + 1) + "\n"))
    Files.writeString(repository.resolve(_source), _ours)
    scenario
  }
  private def _review(scenario: F.Scenario, session: String, issued: F.Result, suffix: String): Unit = {
    successful(issued).business[V.RepositorySyncAdvanceResult].submitOperation shouldBe Some("submitNativeMergeReview")
    val directory = Files.createTempDirectory(scenario.root, "fixture-independent-merge-review-" + suffix + "-")
    val reference = V.WorkflowArtifactReference(directory.getFileName.toString, "1")
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("fixture-independent-reviewer"), None, None)
    val result = V.RepositorySyncNativeMergeReviewResult("accept",
      "Fixture-authored independent review of this actual candidate and its selected product checks; no real consumer or source review approval", Vector.empty)
    val input = V.WorkflowEvidencePreparationInput("RepositorySync", "review", issued.invocation.stdout.toUri.toASCIIString,
      _save_new(directory, "answer.json", json(result)), _save_new(directory, "reference.json", json(reference)),
      _save_new(directory, "execution.json", json(execution)), "normal-development-host", java.time.Instant.now().toString,
      "entity:smworkflow.cli.host_evidence", None)
    _checkpoint(scenario, Some(session), "before-review-preparation-" + suffix)
    val prepared = successful(scenario.prepareHostInput("prepareEvidence", take(new org.goldenport.record.io.RecordDecoder().json(json(input)))))
    scenario.selectPreparedImport(prepared)
    _checkpoint(scenario, Some(session), "before-review-submit-" + suffix)
    successful(scenario.repositoryCall("submitNativeMergeReview", Vector("--sessionId", session, "--result", json(result),
      "--resultReference", json(reference), "--executionEvidence", json(execution))))
      .business[V.RepositorySyncNativeMergeReviewSubmissionResult].accepted shouldBe true
  }
  private def _push_direction(scenario: F.Scenario, session: String, described: F.Result): V.RepositorySyncPushContinuationAdoptionInput = {
    val description = described.business[V.RepositorySyncPushContinuationDescriptionResult]
    val target = take(U.targetC(description.target))
    val participant = target.context.participantIdentity
    val directory = Files.createTempDirectory(scenario.root, "fixture-independent-merge-push-human-")
    val reference = V.WorkflowArtifactReference(directory.getFileName.toString, "1")
    val answer = V.RepositorySyncPushContinuationAnswer("RefreshRemoteAndContinue",
      "Fixture Human: prior push worker ended; accept the original UNKNOWN and the original finite fresh integration allowance", true, true)
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some(participant), None, None)
    val input = V.WorkflowEvidencePreparationInput("RepositorySync", "push-continuation", described.invocation.stdout.toUri.toASCIIString,
      _save_new(directory, "answer.json", json(answer)), _save_new(directory, "reference.json", json(reference)),
      _save_new(directory, "execution.json", json(execution)), "normal-development-host", java.time.Instant.now().toString,
      "entity:smworkflow.cli.host_evidence", Some(participant))
    _checkpoint(scenario, Some(session), "before-push-human-preparation")
    val prepared = successful(scenario.prepareHostInput("prepareEvidence", take(new org.goldenport.record.io.RecordDecoder().json(json(input)))))
    scenario.selectPreparedImport(prepared)
    V.RepositorySyncPushContinuationAdoptionInput(session, description.target, answer.choice, answer.rationale,
      reference, answer.priorExecutionEnded, answer.acceptOriginalUncertainty, execution)
  }
  private def _merge_direction(scenario: F.Scenario, session: String, described: F.Result,
    choice: String): V.RepositorySyncValidationContinuationAdoptionInput = {
    val description = described.business[V.RepositorySyncValidationContinuationDescriptionResult]
    val participant = take(M.targetC(description.target)).context.participantIdentity
    val directory = Files.createTempDirectory(scenario.root, "fixture-independent-merge-check-human-")
    val reference = V.WorkflowArtifactReference(directory.getFileName.toString, "1")
    val answer = V.RepositorySyncValidationContinuationAnswer(choice,
      "Fixture Human: the original CLI worker ended; accept original check execution uncertainty and authorize only the same merge candidate's remaining original validation", true, true)
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some(participant), None, None)
    val input = V.WorkflowEvidencePreparationInput("RepositorySync", "validation-continuation", described.invocation.stdout.toUri.toASCIIString,
      _save_new(directory, "answer.json", json(answer)), _save_new(directory, "reference.json", json(reference)),
      _save_new(directory, "execution.json", json(execution)), "normal-development-host", java.time.Instant.now().toString,
      "entity:smworkflow.cli.host_evidence", Some(participant))
    _checkpoint(scenario, Some(session), "before-merge-human-preparation")
    val prepared = successful(scenario.prepareHostInput("prepareEvidence", take(new org.goldenport.record.io.RecordDecoder().json(json(input)))))
    scenario.selectPreparedImport(prepared)
    V.RepositorySyncValidationContinuationAdoptionInput(session, description.target, answer.choice, answer.rationale,
      reference, answer.priorExecutionEnded, answer.acceptOriginalUncertainty, execution)
  }
  private def _reopen(episode: Json): F.Scenario = {
    val root = Path.of(episode.hcursor.get[String]("root").toOption.get)
    val stream = Files.list(root.resolve("logs"))
    val ordinal = try math.max(1000, stream.filter(path => path.getFileName.toString.matches("[0-9]+\\.(stdout\\.json|stderr)"))
      .mapToInt(path => path.getFileName.toString.takeWhile(_.isDigit).toInt).max().orElse(999) + 1) finally stream.close()
    F.reopenInstalledRepository(root, take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(episode.hcursor.get[Json]("host").toOption.get)),
      episode.hcursor.get[GoalPhaseOrderedProgression.Plan]("plan").toOption.get, ordinal)
  }

  "C01 retained merge check preparation" should {
    "preserve the genuine old-runtime episode across publication" which {
      if (_phase == "before" && _mode == "retained") {
        "retain one resultless original check after a real conflict and two successful structural checks" in {
          Given("the old normally installed ID11 runtime and a fresh isolated real Scala host")
          installed()
          Files.exists(_episode_path) shouldBe false
          val scenario = _fresh()
          val baseline = _head(scenario)
          _checkpoint(scenario, None, "before-start")
          val session = start(scenario)
          val issued = successful(_advance(scenario, session, "before-purpose-issue"))
          issued.business[V.RepositorySyncAdvanceResult].submitOperation shouldBe Some("submitRepositoryPurposeResult")
          purpose_inputs(scenario).head.files.map(_.path).toSet shouldBe (_documents :+ _source).toSet
          val groups = (_documents :+ _source).zipWithIndex.map { (path, index) => P.Group("merge-purpose-" + (index + 1),
            "Retain accepted purpose " + (index + 1), _messages(index),
            if (index == 0) Vector.empty else Vector("merge-purpose-" + index),
            Vector(P.Content(path, Some(Files.readString(_repository(scenario).resolve(path))))), Vector.empty,
            "Preserve the preceding independently reviewed purpose as an ancestor") }
          _checkpoint(scenario, Some(session), "before-purpose-submit")
          successful(submit_purpose(scenario, session, P.Result("resolved", "Three ordered fixture purposes", groups, Vector.empty)))
            .business[V.RepositorySyncConflictSubmissionResult].accepted shouldBe true
          (1 to 3).foreach { index =>
            When("ordinary Advance validates Purpose " + index + " and issues its required fixture review")
            _review(scenario, session, successful(_advance(scenario, session, "before-purpose-" + index)), "purpose-" + index)
            Then("only earlier accepted Purpose commits are installed")
            _commits(scenario, session).size shouldBe index - 1
          }
          val originalchecks = _formal(scenario, session)
          originalchecks.size shouldBe 4
          originalchecks.map(_._2.identity).toSet shouldBe _checks.map(_.identity).toSet
          originalchecks.foreach(_._2.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded)
          _invocations(scenario).size shouldBe 4

          When("an owned bare-remote pre-receive hook rejects the actual nonforce push while remote equals tracking")
          val hook = Path.of(scenario.host.target.endpoint).resolve("hooks/pre-receive")
          Files.exists(hook) shouldBe false
          _checkpoint(scenario, Some(session), "before-owned-reject-hook")
          Files.writeString(hook, "#!/bin/sh\nprintf '%s\\n' 'C01_FIXTURE_REJECT_NONFORCE_PUSH' >&2\nexit 1\n", UTF_8, StandardOpenOption.CREATE_NEW)
          Files.setPosixFilePermissions(hook, PosixFilePermissions.fromString("rwx------"))
          _remote(scenario) shouldBe git(scenario.root, "-C", _repository(scenario).toString, "rev-parse", "refs/remotes/origin/main")
          val failedpush = _advance(scenario, session, "before-genuine-failed-push")
          failedpush.exitCode should not be 0
          _commits(scenario, session).size shouldBe 3
          val oldpush = receipts(scenario, session).filter(_.reference.identity.endsWith(":native-push-intent"))
          oldpush.size shouldBe 1
          oldpush.head.payload.value.pushObservation shouldBe None
          val pushfailure = receipts(scenario, session).filter(_.payload.value.stage == "NativePushFailed")
          pushfailure.size shouldBe 1
          val observation = pushfailure.head.payload.value.pushObservation.get
          U.failed(observation) shouldBe true
          WorkflowSbtCapture.exitCode(observation.process).get should not be 0
          WorkflowSbtCapture.text(observation.process.stderr).value should include("C01_FIXTURE_REJECT_NONFORCE_PUSH")
          _checkpoint(scenario, Some(session), "before-owned-hook-removal", _capture(failedpush))
          Files.delete(hook)

          When("the independent peer advances the same source line after that failed push")
          _checkpoint(scenario, Some(session), "before-peer-advance")
          val peer = scenario.root.resolve("independent-peer")
          git(scenario.root, "clone", "--branch", "main", "--", scenario.host.target.endpoint, peer.toString)
          Files.writeString(peer.resolve(_source), _peer)
          git(scenario.root, "-C", peer.toString, "add", "--", _source)
          _checkpoint(scenario, Some(session), "before-peer-commit")
          git(scenario.root, "-C", peer.toString, "commit", "-m", "Independent overlapping upstream Scala input")
          _checkpoint(scenario, Some(session), "before-peer-push")
          git(scenario.root, "-C", peer.toString, "push", scenario.host.target.endpoint, "HEAD:refs/heads/main")
          val remotetip = _remote(scenario)
          val described = successful(scenario.repositoryCall(U.describeOperation, Vector("--sessionId", session)))
          val pushinput = _push_direction(scenario, session, described)
          _checkpoint(scenario, Some(session), "before-original-push-direction", parse(json(pushinput)).toOption.get)
          successful(scenario.repositoryCall(U.adoptionOperation, Vector("--input", json(pushinput))))
            .business[V.RepositorySyncPushContinuationAdoptionResult].accepted shouldBe true
          val pushdirection = receipts(scenario, session).filter(_.payload.value.stage == "PushContinuationAdmitted")
            .map(_.payload.value.pushContinuation.get).head
          val pushnamespace = U.namespace(pushdirection.target)

          When("ordinary continuation fetches once and issues the genuine source conflict")
          var conflictcall = successful(_advance(scenario, session, "before-refresh-and-conflict"))
          if (conflictcall.business[V.RepositorySyncAdvanceResult].submitOperation.isEmpty)
            conflictcall = successful(_advance(scenario, session, "before-conflict-issue"))
          val conflict = conflictcall.business[V.RepositorySyncAdvanceResult]
          conflict.submitOperation shouldBe Some("submitRepositorySyncWorkResult")
          conflict.conflict.get.conflicts.map(_.path) shouldBe Vector(_source)
          val resolution = V.RepositorySyncConflictResult("resolved", conflict.conflict.get.conflicts.map(value =>
            V.RepositorySyncConflictResolution(value.identity, _resolved)), "Fixture semantic fulltext combines expected local value and upstream remote value")
          val reference = V.WorkflowArtifactReference("fixture-c01-fulltext-resolution", "1")
          val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("fixture-conflict-author"), None, None)
          _checkpoint(scenario, Some(session), "before-accepted-fulltext-submit", parse(json(resolution)).toOption.get)
          successful(scenario.repositoryCall("submitRepositorySyncWorkResult", Vector("--sessionId", session,
            "--result", json(resolution), "--resultReference", json(reference), "--executionEvidence", json(execution))))
            .business[V.RepositorySyncConflictSubmissionResult].accepted shouldBe true
          val unknown = _advance(scenario, session, "before-materialization-and-old-check-admission")
          Then("the old CLI returns an actual workspace failure with no fabricated failed receipt or blind replay")
          unknown.exitCode should not be 0
          unknown.invocation.logs should include("workspaceId")
          unknown.invocation.logs should include("workspace.execution.binding")
          Files.readString(_repository(scenario).resolve(_source)) shouldBe _resolved
          git(scenario.root, "-C", _repository(scenario).toString, "show", ":" + _source) shouldBe _resolved.stripTrailing()
          val native = receipts(scenario, session)
          val intents = native.filter(_.reference.identity.endsWith("-validation-intent-2"))
          intents.size shouldBe 1
          val oldintent = intents.head
          oldintent.payload.value.stage shouldBe "ValidationChecking"
          val prefix = oldintent.payload.value.validation.get.receipts
          oldintent.payload.value.validation.get.selected shouldBe _checks
          prefix.map(_.identity) shouldBe Vector("index-local", "index-remote")
          prefix.foreach { receipt => receipt.exitCode shouldBe Some(0); receipt.sbt shouldBe None }
          native.exists(_.reference.identity == oldintent.reference.identity.replace("validation-intent-2", "validation-result-2")) shouldBe false
          _formal(scenario, session) shouldBe originalchecks
          _invocations(scenario).size shouldBe 4
          val state = _snapshot(scenario, session)

          When("normal host configuration adds exactly the four originally selected merge registrations")
          _checkpoint(scenario, Some(session), "before-exact-host-registration", _capture(unknown))
          val correctedplan = _plan(scenario, corrected = true)
          configure(scenario, Map("textus.subsystem.process-execution.plan" -> correctedplan.noSpaces))
          _snapshot(scenario, session) shouldBe state
          val refused = describe(scenario, session)
          Then("old public describe refuses to invent a genuine formal failure and preserves the old unknown intent")
          refused.exitCode should not be 0
          _snapshot(scenario, session) shouldBe state
          val episode = Json.obj("request" -> "SYNC-20261104-11-C01-MERGE-CHECK-PREPARATION-CONTINUATION".asJson,
            "root" -> scenario.root.toString.asJson, "physicalRoot" -> scenario.root.toRealPath().toString.asJson,
            "database" -> scenario.root.resolve("workflow.sqlite").toRealPath().toString.asJson,
            "session" -> session.asJson, "host" -> ThreeWorkflowRuntimeParameters.encode(scenario.host), "plan" -> scenario.plan.asJson,
            "oldInstalledCommand" -> installed().toString.asJson, "baseline" -> baseline.asJson, "remoteTip" -> remotetip.asJson,
            "failedPush" -> _capture(failedpush), "unknownCheck" -> _capture(unknown), "oldDescribeRefusal" -> _capture(refused),
            "originalCheckExecution" -> "UNKNOWN".asJson, "intentReference" -> oldintent.reference.identity.asJson,
            "successfulPrefix" -> prefix.asJson, "pushDirection" -> pushdirection.asJson, "pushNamespace" -> pushnamespace.asJson,
            "acceptedResolution" -> parse(json(resolution)).toOption.get, "fixedHostPlan" -> correctedplan,
            "state" -> state, "logs" -> scenario.invocations.map(_capture_invocation).asJson)
          Files.writeString(_episode_path, episode.noSpaces, UTF_8, StandardOpenOption.CREATE_NEW)
        }
      }
    }

    "continue only the same checked candidate in the retained physical Session" which {
      if (_phase == "after" && _mode == "retained") {
        "adopt a new Human direction passively then run remaining checks required review and finite nonforce convergence" in {
          Given("the normally published new installed runtime and the original physical old episode")
          installed()
          val bytes = Files.readString(_episode_path)
          val episode = parse(bytes).toOption.get
          val scenario = _reopen(episode)
          val session = episode.hcursor.get[String]("session").toOption.get
          scenario.root.toRealPath().toString shouldBe episode.hcursor.get[String]("physicalRoot").toOption.get
          scenario.root.resolve("workflow.sqlite").toRealPath().toString shouldBe episode.hcursor.get[String]("database").toOption.get
          episode.hcursor.get[String]("originalCheckExecution").toOption.get shouldBe "UNKNOWN"
          val original = episode.hcursor.get[Json]("state").toOption.get
          if (sys.props.get("smworkflow.spec.mergeCheckCollectOnly").contains("true")) {
            When("the explicit saved public review observation is decoded without reissuing work or replaying checks")
            val observationpath = Path.of(sys.props.getOrElse("smworkflow.spec.mergeCheckReviewObservation",
              fail("Select the actual saved absolute mergeCheckReviewObservation JSON path")))
            observationpath.isAbsolute shouldBe true
            observationpath.normalize shouldBe observationpath
            Files.isRegularFile(observationpath) shouldBe true
            observationpath.toRealPath().getParent shouldBe scenario.root.resolve("logs").toRealPath()
            observationpath.getFileName.toString shouldBe "1011.stdout.json"
            val observationbytes = Files.readString(observationpath)
            val reviewwork = take(summon[org.goldenport.convert.ValueReader[V.RepositorySyncAdvanceResult]].readC(
              take(new org.goldenport.record.io.RecordDecoder().json(observationbytes))))
            Then("the actual retained review binds the same checked candidate and requires review before convergence")
            _assert_review_work(scenario, session, episode, reviewwork)
            _assert_review_journal(scenario, reviewwork)
            When("read-only collection observes the completed episode without calling any mutating operation")
            current(scenario, session).lifecycle shouldBe Lifecycle.Completed
            _assert_completed(scenario, session, episode)
            Then("collection preserves every original CLI log and the immutable before episode")
            _assert_logs(episode)
            Files.readString(observationpath) shouldBe observationbytes
            Files.readString(_episode_path) shouldBe bytes
          } else {
            _snapshot(scenario, session) shouldBe original
            val oldchecks = _formal(scenario, session)
            val oldcommits = _commits(scenario, session)
            val oldinvocations = _invocations(scenario)
            val predecessor = current(scenario, session)
            When("public describe and independent fixture Human preparation choose Defer")
            val described = successful(describe(scenario, session))
            val description = described.business[V.RepositorySyncValidationContinuationDescriptionResult]
            description.choices should contain allElementsOf Vector("AuthorizeNewMergeValidation", "Defer")
            val target = take(M.targetC(description.target))
            target.sourceNamespace shouldBe episode.hcursor.get[String]("pushNamespace").toOption.get
            target.checkIndex shouldBe 2
            target.check shouldBe _checks.head
            target.selectedChecks shouldBe _checks
            target.pushDirection.map(_.asJson) shouldBe Some(episode.hcursor.get[Json]("pushDirection").toOption.get)
            target.successfulPrefix shouldBe episode.hcursor.get[Vector[C.Receipt]]("successfulPrefix").toOption.get
            val deferred = _merge_direction(scenario, session, described, "Defer")
            _checkpoint(scenario, Some(session), "before-defer")
            successful(adopt(scenario, deferred)).business[V.RepositorySyncValidationContinuationAdoptionResult].accepted shouldBe false
            Then("description preparation and Defer preserve Git checks and every native source byte")
            _snapshot(scenario, session) shouldBe original

            When("one independently prepared exact Human accepts prior execution uncertainty")
            val input = _merge_direction(scenario, session, described, "AuthorizeNewMergeValidation")
            val passive = _snapshot(scenario, session)
            Vector(input.copy(priorExecutionEnded = false), input.copy(acceptOriginalUncertainty = false),
              input.copy(sessionId = "foreign-merge-check-session"),
              input.copy(executionEvidence = input.executionEvidence.copy(references = Vector.empty))).foreach { invalid =>
              _checkpoint(scenario, Some(session), "before-refused-human-input")
              adopt(scenario, invalid).exitCode should not be 0
              _snapshot(scenario, session) shouldBe passive
            }
            _checkpoint(scenario, Some(session), "before-merge-check-adoption", parse(json(input)).toOption.get)
            successful(adopt(scenario, input)).business[V.RepositorySyncValidationContinuationAdoptionResult].accepted shouldBe true
            Then("adoption appends one direction without materialization staging merge fetch or SBT execution")
            val admitted = current(scenario, session)
            admitted.revision shouldBe predecessor.revision.next
            admitted.history.take(predecessor.history.size) shouldBe predecessor.history
            val afteradoption = _snapshot(scenario, session)
            afteradoption.mapObject(_.remove("record").remove("native")) shouldBe original.mapObject(_.remove("record").remove("native"))
            _array(original, "native").foreach(value => _native(scenario, session) should contain(value))
            receipts(scenario, session).count(_.payload.value.stage == "MergeCheckContinuationAdmitted") shouldBe 1
            val saved = _snapshot(scenario, session)
            _checkpoint(scenario, Some(session), "before-identical-adoption")
            successful(adopt(scenario, input))
            _checkpoint(scenario, Some(session), "before-contradictory-adoption")
            adopt(scenario, input.copy(rationale = "Contradictory fixture rationale")).exitCode should not be 0
            _snapshot(scenario, session) shouldBe saved

            When("separate ordinary Advance prepares the same candidate and executes the four remaining original checks")
            var issued = successful(_advance(scenario, session, "before-continued-checks"))
            if (issued.business[V.RepositorySyncAdvanceResult].submitOperation.isEmpty)
              issued = successful(_advance(scenario, session, "before-required-merge-review-issue"))
            Then("the genuine conflict candidate requires fresh review after successful original validation")
            val reviewwork = issued.business[V.RepositorySyncAdvanceResult]
            _assert_review_work(scenario, session, episode, reviewwork)
            _assert_review_journal(scenario, reviewwork)
            val freshchecks = _formal(scenario, session).filterNot(oldchecks.contains)
            freshchecks.size shouldBe 4
            freshchecks.map(_._2.identity).toSet shouldBe _checks.map(_.identity).toSet
            freshchecks.foreach(_._2.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded)
            val selected = freshchecks.find(_._2.identity == "merge-test-only").get._2.sbt.get.selection.get
            selected.testClasses shouldBe Vector(selectedClass)
            selected.completedSuites shouldBe 1
            selected.testsRun shouldBe 1L
            _invocations(scenario).size shouldBe oldinvocations.size + 4
            _invocations(scenario).take(oldinvocations.size) shouldBe oldinvocations
            _commits(scenario, session) shouldBe oldcommits
            _head(scenario) shouldBe original.hcursor.get[String]("head").toOption.get
            _tree(scenario) shouldBe original.hcursor.get[String]("indexTree").toOption.get
            val continuation = receipts(scenario, session).filter(_.reference.identity.startsWith(session + ":native-" + M.namespace(target) + "-"))
            continuation.count(_.payload.value.stage == "PendingMergePrepared") shouldBe 1
            val validated = continuation.find(_.payload.value.stage == "MergeValidated").get.payload.value.validation.get
            validated.receipts.take(2) shouldBe episode.hcursor.get[Vector[C.Receipt]]("successfulPrefix").toOption.get
            continuation.filter(_.reference.identity.endsWith("-validation-result-0")) shouldBe empty
            continuation.filter(_.reference.identity.endsWith("-validation-result-1")) shouldBe empty
            _assert_retained(scenario, session, episode)
            _review(scenario, session, issued, "continued-merge")
            val terminal = successful(_advance(scenario, session, "before-merge-commit-push-final-fetch"))
              .business[V.RepositorySyncAdvanceResult]
            terminal.kind shouldBe "Terminal"
            _assert_completed(scenario, session, episode)
            _assert_logs(episode)
            Files.readString(_episode_path) shouldBe bytes
            _checkpoint(scenario, Some(session), "before-progressed-idempotent-replay")
            val completed = _record(scenario, session)
            successful(adopt(scenario, input))
            _record(scenario, session) shouldBe completed
            Files.writeString(_episode_path.resolveSibling(_episode_path.getFileName.toString.stripSuffix(".json") + "-after.json"),
              Json.obj("originalEpisode" -> _episode_path.toString.asJson, "session" -> session.asJson,
                "acceptedInput" -> parse(json(input)).toOption.get, "terminal" -> parse(json(terminal)).toOption.get,
                "record" -> completed.asJson, "native" -> _native(scenario, session).asJson,
                "originalCheckExecution" -> "UNKNOWN".asJson, "logs" -> scenario.invocations.map(_capture_invocation).asJson).noSpaces,
              UTF_8, StandardOpenOption.CREATE_NEW)
          }
        }
      }
    }

    "retain an actual new-runtime preparation refusal without launching SBT" which {
      if (_phase == "after" && _mode == "refusal") {
        "record the typed rejection once and describe its exact unchanged candidate and successful structural prefix" in {
          Given("a separate new-runtime tiny divergent repository with all four original root registrations absent")
          installed()
          val evidencepath = _episode_path.resolveSibling(_episode_path.getFileName.toString.stripSuffix(".json") + "-refusal.json")
          Files.exists(evidencepath) shouldBe false
          val mainbytes = Option.when(Files.exists(_episode_path))(Files.readString(_episode_path))
          val scenario = _fresh()
          var session = Option.empty[String]
          var captures = Vector.empty[Json]
          var identified = Json.obj()
          var completedassertions = false
          try {
            _checkpoint(scenario, None, "before-refusal-local-stage")
            git(scenario.root, "-C", _repository(scenario).toString, "add", "--", "docs", _source)
            _checkpoint(scenario, None, "before-refusal-local-commit")
            git(scenario.root, "-C", _repository(scenario).toString, "commit", "-m", "Fixture setup local divergent Scala baseline")
            val localtip = _head(scenario)
            val peer = scenario.root.resolve("refusal-independent-peer")
            val peerpath = "src/main/scala/fixture/PeerInput.scala"
            val peertext = "package fixture\nobject PeerInput { val upstream = \"independent\" }\n"
            _checkpoint(scenario, None, "before-refusal-peer-clone")
            git(scenario.root, "clone", "--branch", "main", "--", scenario.host.target.endpoint, peer.toString)
            _checkpoint(scenario, None, "before-refusal-peer-input")
            Files.writeString(peer.resolve(peerpath), peertext, UTF_8, StandardOpenOption.CREATE_NEW)
            _checkpoint(scenario, None, "before-refusal-peer-stage")
            git(scenario.root, "-C", peer.toString, "add", "--", peerpath)
            _checkpoint(scenario, None, "before-refusal-peer-commit")
            git(scenario.root, "-C", peer.toString, "commit", "-m", "Fixture setup independent upstream Scala input")
            _checkpoint(scenario, None, "before-refusal-peer-push")
            git(scenario.root, "-C", peer.toString, "push", scenario.host.target.endpoint, "HEAD:refs/heads/main")
            val remotetip = _remote(scenario)
            localtip should not be remotetip
            git(scenario.root, "-C", _repository(scenario).toString, "status", "--porcelain") shouldBe ""
            _invocations(scenario) shouldBe empty
            val configuredplan = parse(parse(Files.readString(scenario.root.resolve(".textus/config.json"))).toOption.get
              .hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
            identified = Json.obj("localTip" -> localtip.asJson, "remoteTip" -> remotetip.asJson,
              "fixedHostPlan" -> configuredplan)

            When("normal Start and one ordinary Advance reach a real nonconflicting pending merge and typed SBT admission")
            _checkpoint(scenario, None, "before-refusal-start")
            val started = scenario.repositoryCall("startRepositorySync", Vector("--input",
              "{\"repositoryIdentity\":\"continuation-fixture\",\"syncMode\":\"FULL_STATE\"}"))
            captures :+= _capture(started)
            val identity = successful(started).business[V.RepositorySyncStartResult].sessionId
            session = Some(identity)
            val attempted = _advance(scenario, identity, "before-typed-preparation-refusal")
            captures :+= _capture(attempted)
            val waiting = successful(attempted).business[V.RepositorySyncAdvanceResult]
            Then("the actual AdmissionRejected persists as a distinct preparation refusal rather than a process result")
            waiting.kind shouldBe "Wait"
            waiting.condition shouldBe Some("ValidationPreparationRejected")
            waiting.submitOperation shouldBe None
            val native = receipts(scenario, identity)
            val candidates = native.filter(_.payload.value.stage == "PendingMergePrepared")
            candidates.size shouldBe 1
            val candidate = candidates.head
            val intents = native.filter(_.reference.identity == identity + ":native-validation-intent-2")
            intents.size shouldBe 1
            val intent = intents.head
            val rejections = native.filter(_.payload.value.stage == "ValidationPreparationRejected")
            rejections.size shouldBe 1
            val rejected = rejections.head
            rejected.reference.identity shouldBe identity + ":native-validation-preparation-rejected-2"
            rejected.next.revision shouldBe intent.next.revision.next
            rejected.next.history.dropRight(1) shouldBe intent.next.history
            val refusal = rejected.payload.value.validationPreparation.get
            refusal.workspaceId shouldBe "sm-workflow-repository-validation-1"
            refusal.kind shouldBe WorkflowSbtValidation.Kind.Compile
            refusal.diagnostic should include("workspaceId")
            refusal.diagnostic should include("workspace.execution.binding")
            refusal.asJson.asObject.get.keys.toSet shouldBe Set("workspaceId", "kind", "diagnostic")
            rejected.payload.value shouldBe intent.payload.value.copy(stage = "ValidationPreparationRejected",
              validationPreparation = Some(refusal))
            val prefix = rejected.payload.value.validation.get.receipts
            prefix.map(_.identity) shouldBe Vector("index-local", "index-remote")
            prefix.foreach { receipt => receipt.exitCode shouldBe Some(0); receipt.sbt shouldBe None }
            intent.payload.value.validation.get.receipts shouldBe prefix
            rejected.payload.value.validation.get.selected shouldBe _checks
            candidate.payload.value.before.head shouldBe localtip
            candidate.payload.value.mergeHead shouldBe Some(remotetip)
            _head(scenario) shouldBe localtip
            _tree(scenario) shouldBe candidate.payload.value.indexTree.get
            Files.readString(_repository(scenario).resolve(_source)) shouldBe _ours
            Files.readString(_repository(scenario).resolve(peerpath)) shouldBe peertext
            _formal(scenario, identity) shouldBe empty
            _invocations(scenario) shouldBe empty
            native.filter(_.payload.value.stage == "PurposeInstalled") shouldBe empty
            purpose_inputs(scenario) shouldBe empty
            native.exists(_.reference.identity == identity + ":native-validation-result-2") shouldBe false
            (3 to 5).foreach { index =>
              native.exists(_.reference.identity == identity + ":native-validation-intent-" + index) shouldBe false
              native.exists(_.reference.identity == identity + ":native-validation-result-" + index) shouldBe false
            }
            identified = identified.deepMerge(Json.obj("candidate" -> N.codec.encode(candidate.payload.value),
              "intentReference" -> intent.reference.identity.asJson, "intentRecord" -> take(WorkflowInstanceRecordJsonV1.encodeC(intent.next)).asJson,
              "intent" -> N.codec.encode(intent.payload.value), "refusalReference" -> rejected.reference.identity.asJson,
              "refusal" -> refusal.asJson, "successfulPrefix" -> prefix.asJson))
            val retained = _snapshot(scenario, identity)

            When("another ordinary Advance observes the retained exact rejection without a new check attempt")
            val repeated = _advance(scenario, identity, "before-refusal-repeat-observation")
            captures :+= _capture(repeated)
            val repeatedwait = successful(repeated).business[V.RepositorySyncAdvanceResult]
            Then("the same frontier source index and native rows remain with zero SBT launches and no later check")
            repeatedwait.kind shouldBe "Wait"
            repeatedwait.condition shouldBe Some("ValidationPreparationRejected")
            _snapshot(scenario, identity) shouldBe retained

            When("existing public describe projects the exact actual typed refusal and complete fixed host declaration")
            val described = describe(scenario, identity)
            captures :+= _capture(described)
            val description = successful(described).business[V.RepositorySyncValidationContinuationDescriptionResult]
            val target = take(M.targetC(description.target))
            Then("the target attributes preparation rejection to the adjacent original intent without manufacturing a failure receipt or uncertainty fact")
            target.preparationRefusal shouldBe Some(refusal)
            target.frontierRevision shouldBe rejected.next.revision.value.toString
            target.sourceNamespace shouldBe ""
            target.candidatePayload shouldBe N.codec.encode(candidate.payload.value)
            target.intentPayload shouldBe N.codec.encode(intent.payload.value)
            target.intentReference.identity shouldBe intent.reference.identity
            target.checkIndex shouldBe 2
            target.check shouldBe _checks.head
            target.selectedChecks shouldBe _checks
            target.successfulPrefix shouldBe prefix
            target.indexTree shouldBe candidate.payload.value.indexTree.get
            target.processPlan shouldBe configuredplan
            target.pushDirection shouldBe None
            _snapshot(scenario, identity) shouldBe retained
            _invocations(scenario) shouldBe empty
            mainbytes.foreach(value => Files.readString(_episode_path) shouldBe value)
            completedassertions = true
          } finally {
            val observations = session.map { identity => Json.obj("record" -> _record(scenario, identity).asJson,
              "native" -> _native(scenario, identity).asJson,
              "pendingMerge" -> Option.when(Files.isRegularFile(_repository(scenario).resolve(".git/MERGE_HEAD")))(
                _snapshot(scenario, identity)).asJson) }.getOrElse(Json.obj())
            Files.writeString(evidencepath, Json.obj("mode" -> "refusal".asJson,
              "request" -> "SYNC-20261104-11-C01-MERGE-CHECK-PREPARATION-CONTINUATION".asJson,
              "episodeLocator" -> _episode_path.toString.asJson, "root" -> scenario.root.toString.asJson,
              "session" -> session.asJson, "assertionsCompleted" -> completedassertions.asJson,
              "identified" -> identified, "observations" -> observations,
              "sbtInvocations" -> _invocations(scenario).asJson, "calls" -> captures.asJson,
              "logs" -> scenario.invocations.map(_capture_invocation).asJson).noSpaces,
              UTF_8, StandardOpenOption.CREATE_NEW)
          }
        }
      }
    }
  }

  private def _assert_logs(episode: Json): Unit = _array(episode, "logs").foreach { log =>
    Files.readString(Path.of(log.hcursor.get[String]("stdoutPath").toOption.get)) shouldBe log.hcursor.get[String]("stdout").toOption.get
    Files.readString(Path.of(log.hcursor.get[String]("stderrPath").toOption.get)) shouldBe log.hcursor.get[String]("stderr").toOption.get
  }
  private def _assert_review_work(scenario: F.Scenario, session: String, episode: Json,
    reviewwork: V.RepositorySyncAdvanceResult): Unit = {
    val original = episode.hcursor.get[Json]("state").toOption.get
    val native = receipts(scenario, session)
    val directions = native.filter(_.payload.value.stage == "MergeCheckContinuationAdmitted")
    directions.size shouldBe 1
    val target = directions.head.payload.value.mergeCheckContinuation.get.target
    val continuation = native.filter(_.reference.identity.startsWith(session + ":native-" + M.namespace(target) + "-"))
    continuation.count(_.payload.value.stage == "PendingMergePrepared") shouldBe 1
    val validated = continuation.filter(_.payload.value.stage == "MergeValidated")
    validated.size shouldBe 1
    val validation = validated.head.payload.value.validation.get
    validation.receipts.take(2) shouldBe episode.hcursor.get[Vector[C.Receipt]]("successfulPrefix").toOption.get
    validation.selected shouldBe _checks
    validation.receipts.drop(2).map(_.identity) shouldBe _checks.map(_.identity)
    validation.receipts.drop(2).zip(_checks).foreach { (receipt, check) =>
      receipt.sbt.get.kind shouldBe check.kind
      receipt.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded
      receipt.exitCode shouldBe Some(0)
    }
    continuation.filter(_.reference.identity.endsWith("-validation-result-0")) shouldBe empty
    continuation.filter(_.reference.identity.endsWith("-validation-result-1")) shouldBe empty
    reviewwork.sessionId shouldBe session
    reviewwork.kind shouldBe "Request"
    reviewwork.submitOperation shouldBe Some("submitNativeMergeReview")
    reviewwork.submitSessionId shouldBe Some(session)
    reviewwork.resultType shouldBe Some("RepositorySyncNativeMergeReviewResult")
    reviewwork.convergence shouldBe None
    val review = reviewwork.review.get
    review.judgmentMode shouldBe None
    review.localTip shouldBe original.hcursor.get[String]("head").toOption.get
    review.remoteTip shouldBe episode.hcursor.get[String]("remoteTip").toOption.get
    review.indexTree shouldBe original.hcursor.get[String]("indexTree").toOption.get
    review.changedPaths shouldBe (_documents :+ _source).sorted
    review.selectedChecks shouldBe _checks.map(_.identity)
    review.checks.size shouldBe validation.receipts.size
    review.checks.zip(validation.receipts).foreach { (observed, actual) =>
      observed.identity shouldBe actual.identity
      observed.termination shouldBe actual.termination
      Some(observed.exitCode) shouldBe actual.exitCode
      observed.stdout shouldBe actual.stdout
      observed.stderr shouldBe actual.stderr
    }
    val issuedcontext = reviewwork.issuedWorkContext.get
    issuedcontext.thinkingMode shouldBe "review"
    issuedcontext.requiredOperation shouldBe "native-merge-review-capability"
    issuedcontext.runId shouldBe session
    issuedcontext.continuationId shouldBe session + ":native-review:" + validated.head.next.revision.value
    val execution = GoalPhaseNativeMetadata.normalizePublicJson(
      parse(take(issuedcontext.executionRequirement.toJsonStringC)).toOption.get).fold(fail(_), identity)
    execution.hcursor.get[Boolean]("reviewRequired") shouldBe Right(true)
    issuedcontext.completionOperation.getString("service") shouldBe Some("RepositorySyncService")
    issuedcontext.completionOperation.getString("operation") shouldBe Some("submitNativeMergeReview")
    val reviewcontext = review.reviewContext.get
    reviewcontext.purpose shouldBe "repository-sync-independent-review"
    reviewcontext.completion.continuationId shouldBe issuedcontext.continuationId
    val scopetarget = GoalPhaseNativeMetadata.normalizePublicJson(
      parse(take(reviewcontext.target.toJsonStringC)).toOption.get).fold(fail(_), identity)
    scopetarget.hcursor.get[String]("instance") shouldBe Right(session)
    scopetarget.hcursor.get[String]("validatedRevision") shouldBe Right(validated.head.next.revision.value.toString)
    scopetarget.hcursor.get[String]("localTip") shouldBe Right(review.localTip)
    scopetarget.hcursor.get[String]("remoteTip") shouldBe Right(review.remoteTip)
    scopetarget.hcursor.get[String]("indexTree") shouldBe Right(review.indexTree)
    scopetarget.hcursor.get[Vector[String]]("selectedChecks") shouldBe Right(_checks.map(_.identity))
  }
  private def _assert_review_journal(scenario: F.Scenario, reviewwork: V.RepositorySyncAdvanceResult): Unit =
    reviewwork.journalRecording.foreach { recording =>
      if (recording.status == "disabled") recording.entry shouldBe None
      else {
        Set("recorded", "alreadyRecorded") should contain(recording.status)
        val assessmententry = successful(scenario.readJournalEntry(V.JournalReadInput(recording.entry.get.identity,
          Some(recording.entry.get.revision)))).business[V.JournalReadResult]
        assessmententry.status shouldBe "found"
        val assessment = GoalPhaseNativeMetadata.normalizePublicJson(
          parse(take(assessmententry.entry.get.assessment.get.toJsonStringC)).toOption.get).fold(fail(_), identity)
          .hcursor.get[RepositorySyncMergeAssessment.Result]("assessment").toOption.get
        assessment.policy.detailedReview shouldBe "conditional"
        assessment.conflictResolution shouldBe true
        assessment.branch shouldBe "detailed-review"
      }
    }
  private def _assert_retained(scenario: F.Scenario, session: String, episode: Json): Unit = {
    val original = episode.hcursor.get[Json]("state").toOption.get
    _array(original, "native").foreach(value => _native(scenario, session) should contain(value))
    val oldchecks = original.hcursor.get[Vector[(String, C.Receipt)]]("formal").toOption.get
    _formal(scenario, session).filter(oldchecks.contains) shouldBe oldchecks
    _commits(scenario, session) shouldBe original.hcursor.get[Vector[String]]("commits").toOption.get
    val native = receipts(scenario, session)
    val oldintent = native.find(_.reference.identity == episode.hcursor.get[String]("intentReference").toOption.get).get
    oldintent.payload.value.validation.get.receipts shouldBe episode.hcursor.get[Vector[C.Receipt]]("successfulPrefix").toOption.get
    native.exists(_.reference.identity == oldintent.reference.identity.replace("validation-intent-2", "validation-result-2")) shouldBe false
    val directions = native.filter(_.payload.value.stage == "PushContinuationAdmitted")
    directions.size shouldBe 1
    directions.head.payload.value.pushContinuation.get.asJson shouldBe episode.hcursor.get[Json]("pushDirection").toOption.get
    val pushfailure = native.filter(_.payload.value.stage == "NativePushFailed")
    pushfailure.size shouldBe 1
    U.failed(pushfailure.head.payload.value.pushObservation.get) shouldBe true
    native.count(_.payload.value.stage == "PurposeInstalled") shouldBe 3
    purpose_inputs(scenario).size shouldBe 1
    native.count(_.reference.identity.endsWith("-refresh-fetch-intent")) shouldBe 1
    native.count(_.reference.identity.endsWith("-refresh-fetch-result")) shouldBe 1
    native.count(_.payload.value.stage == "MaterializingConflict") shouldBe 1
    native.count(_.payload.value.stage == "MergeConflicted") shouldBe 1
    current(scenario, session).history.count(_.currentProgression.contains(ProgressionReference("sync.MergeResolutionAdmitted"))) shouldBe 1
  }
  private def _assert_completed(scenario: F.Scenario, session: String, episode: Json): Unit = {
    _assert_retained(scenario, session, episode)
    val original = episode.hcursor.get[Json]("state").toOption.get
    val oldchecks = original.hcursor.get[Vector[(String, C.Receipt)]]("formal").toOption.get
    val freshchecks = _formal(scenario, session).filterNot(oldchecks.contains)
    freshchecks.size shouldBe 4
    freshchecks.map(_._2.identity).toSet shouldBe _checks.map(_.identity).toSet
    freshchecks.foreach(_._2.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded)
    _invocations(scenario).size shouldBe original.hcursor.get[Vector[String]]("invocations").toOption.get.size + 4
    current(scenario, session).lifecycle shouldBe Lifecycle.Completed
    val head = _head(scenario)
    _remote(scenario) shouldBe head
    git(scenario.root, "-C", _repository(scenario).toString, "rev-parse", "refs/remotes/origin/main") shouldBe head
    git(scenario.root, "-C", _repository(scenario).toString, "show", "-s", "--format=%P", "HEAD").split(" ").toVector shouldBe
      Vector(original.hcursor.get[String]("head").toOption.get, episode.hcursor.get[String]("remoteTip").toOption.get)
    Files.readString(_repository(scenario).resolve(_source)) shouldBe _resolved
    git(scenario.root, "-C", _repository(scenario).toString, "status", "--porcelain") shouldBe ""
    Files.exists(_repository(scenario).resolve(".git/MERGE_HEAD")) shouldBe false
    val namespace = episode.hcursor.get[String]("pushNamespace").toOption.get
    val native = receipts(scenario, session)
    val keys = native.map(_.reference.identity)
    Vector("push-intent", "push-observed", "final-fetch-intent", "converged").foreach { suffix =>
      keys.count(_ == session + ":native-" + namespace + "-" + suffix) shouldBe 1
    }
    keys.count(_.endsWith(":native-push-intent")) shouldBe 1
    native.count(_.payload.value.stage == "MergeCheckContinuationAdmitted") shouldBe 1
  }
}
