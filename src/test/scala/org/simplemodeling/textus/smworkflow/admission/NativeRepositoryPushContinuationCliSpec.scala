package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import scala.jdk.CollectionConverters.*
import org.goldenport.cncf.processexecution.{GitBranchName, GitExecutionOutcome, GitObjectId, GitOperation, SbtOperation}
import org.goldenport.cncf.workflow.WorkflowInstanceRecordJsonV1
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import RepositorySyncNativeMergeValidation as C
import RepositorySyncNativeProgressStore as N
import RepositorySyncPurpose as P
import RepositorySyncPushContinuation as U
import SplitPhaseExplicitPartitionFixture.take

/** One isolated, real failed non-forcing push retained across publication.
  * Select the actual installed executable, normal SBT executable, explicit
  * smworkflow.spec.pushPhase=before|after and a fresh absolute JSON path in
  * smworkflow.spec.pushEpisode. The after phase reopens the original physical
  * host, SQLite provider, repository and Session; it never seeds native state.
  * Fixture-authored independent Human/review inputs attest this fixture only.
  * @since Nov. 4, 2026
  * @version Nov. 4, 2026
  */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeRepositoryPushContinuationCliSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with NativeRepositorySelectionCorrectionFixture {
  private val _checks = originalChecks :+ appendedCheck
  private val _documents = Vector("docs/push-a.md", "docs/push-b.md")
  private val _remote_path = "src/main/resources/remote.txt"
  private val _messages = Vector("docs: retain push purpose A", "docs: retain push purpose B", "fix: retain push product input")

  private def _phase: String = {
    val phase = sys.props.getOrElse("smworkflow.spec.pushPhase", fail("Select explicit pushPhase=before|after"))
    Set("before", "after") should contain(phase)
    phase
  }
  private def _episode_path: Path = {
    val path = Path.of(sys.props.getOrElse("smworkflow.spec.pushEpisode", fail("Select a fresh absolute pushEpisode JSON path")))
    path.isAbsolute shouldBe true
    path.normalize shouldBe path
    Files.isDirectory(path.getParent) shouldBe true
    path.getFileName.toString should endWith(".json")
    path
  }
  private def _array(value: Json, key: String): Vector[Json] = value.hcursor.get[Vector[Json]](key).toOption.get
  private def _repository(scenario: F.Scenario): Path = Path.of(scenario.host.target.root)
  private def _record(scenario: F.Scenario, session: String): String = take(WorkflowInstanceRecordJsonV1.encodeC(current(scenario, session)))
  private def _head(scenario: F.Scenario): String = git(scenario.root, "-C", _repository(scenario).toString, "rev-parse", "HEAD")
  private def _tree(scenario: F.Scenario): String = git(scenario.root, "-C", _repository(scenario).toString, "write-tree")
  private def _remote(scenario: F.Scenario): String = git(scenario.root, "--git-dir=" + scenario.host.target.endpoint,
    "rev-parse", "refs/heads/main")
  private def _invocations(scenario: F.Scenario): Vector[String] = {
    val path = scenario.root.resolve("push-sbt-invocations.txt")
    if (Files.exists(path)) Files.readString(path).linesIterator.toVector else Vector.empty
  }
  private def _native(scenario: F.Scenario, session: String): Vector[Json] = receipts(scenario, session).map(receipt => Json.obj(
    "reference" -> receipt.reference.identity.asJson,
    "record" -> take(WorkflowInstanceRecordJsonV1.encodeC(receipt.next)).asJson,
    "payload" -> N.codec.encode(receipt.payload.value))).sortBy(_.hcursor.get[String]("reference").toOption.get)
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
  private def _snapshot(scenario: F.Scenario, session: String): Json = Json.obj(
    "record" -> _record(scenario, session).asJson, "head" -> _head(scenario).asJson,
    "indexTree" -> _tree(scenario).asJson, "remote" -> _remote(scenario).asJson,
    "status" -> git(scenario.root, "-C", _repository(scenario).toString, "status", "--porcelain").asJson,
    "commits" -> _commits(scenario, session).asJson, "formal" -> _formal(scenario, session).asJson,
    "invocations" -> _invocations(scenario).asJson, "native" -> _native(scenario, session).asJson)
  private def _capture(result: F.Result): Json = Json.obj("exitCode" -> result.exitCode.asJson,
    "argv" -> result.invocation.argv.asJson, "pid" -> result.invocation.pid.asJson,
    "stdoutPath" -> result.invocation.stdout.toString.asJson, "stderrPath" -> result.invocation.stderr.toString.asJson,
    "stdout" -> Files.readString(result.invocation.stdout).asJson, "stderr" -> Files.readString(result.invocation.stderr).asJson)
  private def _save_new(root: Path, name: String, text: String): String = {
    val path = root.resolve(name)
    Files.writeString(path, text, UTF_8, StandardOpenOption.CREATE_NEW)
    path.toUri.toASCIIString
  }
  private def _read_value[A: org.goldenport.convert.ValueReader](path: Path): A =
    take(summon[org.goldenport.convert.ValueReader[A]].readC(take(new org.goldenport.record.io.RecordDecoder().json(Files.readString(path)))))
  private def _scope(condition: Boolean, dependency: String): Unit =
    if (!condition) fail("SCOPE_MISMATCH: retained push fixture -> " + dependency)
  /** Fixture observations only. A checkpoint grants no operation authority and
    * never supplies a native effect result or an automatic replay instruction.
    */
  private def _checkpoint(scenario: F.Scenario, session: String, stage: String, detail: Json): Path = {
    val directory = Files.createTempDirectory(scenario.root, "fixture-push-progress-" + stage + "-")
    val path = directory.resolve("checkpoint.json")
    Files.writeString(path, Json.obj("originalEpisode" -> _episode_path.toString.asJson,
      "session" -> session.asJson, "stage" -> stage.asJson, "detail" -> detail,
      "state" -> _snapshot(scenario, session), "logs" -> scenario.invocations.map(_capture_invocation).asJson).noSpaces,
      UTF_8, StandardOpenOption.CREATE_NEW)
    path
  }
  /** The sole admitted partial episode is the actual accepted-empty-references
    * failure. No other direction, new effect or new Session is a resume input.
    */
  private def _partial_direction(scenario: F.Scenario, session: String, episode: Json): Option[U.Direction] = {
    val original = episode.hcursor.get[Json]("state").toOption.get
    val actual = _snapshot(scenario, session)
    if (actual == original) None
    else {
      _scope(session == "c895225f3e7596c812221a52991a51ec" &&
        scenario.root.getFileName.toString == "id11-push-continuation-8854132415819711112",
        "only the identified failed-after physical episode is admitted")
      val native = receipts(scenario, session)
      val adoptions = native.filter(_.payload.value.stage == "PushContinuationAdmitted")
      val oldnative = _array(original, "native")
      _scope(oldnative.size == 45 && _array(original, "formal").size == 3 && _array(original, "commits").size == 3 &&
        native.size == 46 && adoptions.size == 1 &&
        oldnative.forall(_array(actual, "native").contains) &&
        actual.mapObject(_.remove("record").remove("native")) == original.mapObject(_.remove("record").remove("native")),
        "exact old 45 native receipts plus one adoption, three checks/commits and no new Git effects")
      val adoption = adoptions.head
      _scope(adoption.payload.value.pushContinuation.nonEmpty, "the single adoption has its canonical actual direction")
      val direction = adoption.payload.value.pushContinuation.get
      val catalog = take(WorkflowInstanceRecordJsonV1.catalogC(Vector(take(RepositorySyncDefinitionBinding.bindC))))
      val old = take(WorkflowInstanceRecordJsonV1.decodeC(original.hcursor.get[String]("record").toOption.get, catalog))
      val currentrecord = current(scenario, session)
      _scope((1000 to 1010).forall(ordinal => Files.isRegularFile(scenario.root.resolve(s"logs/$ordinal.stdout.json")) &&
        Files.isRegularFile(scenario.root.resolve(s"logs/$ordinal.stderr"))), "all literal logs of the actual failed after qualification")
      val described = _read_value[V.RepositorySyncPushContinuationDescriptionResult](scenario.root.resolve("logs/1000.stdout.json"))
      val target = take(U.targetC(described.target))
      val failed = _read_value[V.RepositorySyncPushContinuationAdoptionResult](scenario.root.resolve("logs/1010.stdout.json"))
      val directory = scenario.root.resolve(direction.reference.identity)
      _scope(directory.getParent == scenario.root && Files.isDirectory(directory) &&
        Vector("answer.json", "reference.json", "execution.json").forall(name => Files.isRegularFile(directory.resolve(name))),
        "the actual independently prepared Human answer directory")
      val answer = _read_value[V.RepositorySyncPushContinuationAnswer](directory.resolve("answer.json"))
      val reference = _read_value[V.WorkflowArtifactReference](directory.resolve("reference.json"))
      val execution = _read_value[V.WorkflowExecutionEvidence](directory.resolve("execution.json"))
      _scope(failed.sessionId == session && failed.accepted && failed.outcome == "adopted" &&
        direction.asJson.as[U.Direction] == Right(direction) && direction.target == target &&
        direction.target.sessionId == session && direction.target.sourceNamespace.isEmpty &&
        direction.target.frontierRevision == old.revision.value.toString &&
        direction.target.retainedIntent.exists(intent => intent.kind == "push" &&
          intent.reference.identity == session + ":native-push-intent") && direction.target.nativeObservation.isEmpty &&
        direction.choice == answer.choice && direction.rationale == answer.rationale &&
        direction.priorExecutionEnded == answer.priorExecutionEnded && direction.acceptOriginalUncertainty == answer.acceptOriginalUncertainty &&
        direction.reference.identity == reference.identity && direction.reference.revision == reference.revision &&
        execution.references.contains(reference) && execution.workerIdentity.contains(direction.participant) &&
        take(WorkflowInstanceRecordJsonV1.encodeC(currentrecord)) == take(WorkflowInstanceRecordJsonV1.encodeC(adoption.next)) &&
        currentrecord.revision == old.revision.next &&
        currentrecord.history.take(old.history.size) == old.history,
        "the exact saved direction, original source intent, actual accepted 1010 response and original prepared evidence")
      _checkpoint(scenario, session, "accepted-empty-references-failure", Json.obj(
        "actualFailure" -> "Empty executionEvidence.references was accepted; this is not a rejected negative case".asJson,
        "direction" -> direction.asJson,
        "failedCallStdout" -> Files.readString(scenario.root.resolve("logs/1010.stdout.json")).asJson,
        "failedCallStderr" -> Files.readString(scenario.root.resolve("logs/1010.stderr")).asJson,
        "preparedAnswer" -> Files.readString(directory.resolve("answer.json")).asJson,
        "preparedReference" -> Files.readString(directory.resolve("reference.json")).asJson,
        "preparedExecution" -> Files.readString(directory.resolve("execution.json")).asJson,
        "retainedFailedAfterLogs" -> (1000 to 1010).toVector.map(ordinal => Json.obj(
          "stdoutPath" -> scenario.root.resolve(s"logs/$ordinal.stdout.json").toString.asJson,
          "stderrPath" -> scenario.root.resolve(s"logs/$ordinal.stderr").toString.asJson,
          "stdout" -> Files.readString(scenario.root.resolve(s"logs/$ordinal.stdout.json")).asJson,
          "stderr" -> Files.readString(scenario.root.resolve(s"logs/$ordinal.stderr")).asJson)).asJson))
      Some(direction)
    }
  }

  private def _plan(scenario: F.Scenario): Json = {
    val base = selectionPlan(scenario, broken = false, corrected = true)
    def _clone_(program: Json, slot: Int): Json = program.mapObject(fields => fields
      .add("target", ("purpose-slot-" + slot).asJson)
      .add("identity", (fields("identity").get.asString.get + "-push-slot-" + slot).asJson)
      .add("capability", (fields("capability").get.asString.get + "-push-slot-" + slot).asJson))
    val gitprograms = _array(base, "gitPrograms") ++ (2 to 3).toVector.flatMap(slot =>
      _array(base, "gitPrograms").filter(_.hcursor.get[String]("target").toOption.contains("purpose-slot-1"))
        .map(_clone_(_, slot)))
    val purposeprograms = _array(base, "sbtPrograms").map(_clone_(_, 3))
    val mergeplans = Vector(SbtOperation.Compile, SbtOperation.Test, SbtOperation.TestOnly).zipWithIndex.map { (kind, index) =>
      TypedSbtHostPlanFixture.plan(scenario.root, TypedSbtHostPlanFixture.normalExecutable, Vector(index + 1),
        operation = kind, testClasses = if (kind == SbtOperation.TestOnly) Vector(selectedClass) else Vector.empty)
    }
    val sbtprograms = purposeprograms ++ mergeplans.flatMap(_array(_, "sbtPrograms"))
    val slots = (2 to 3).toVector.map { slot =>
      val identity = "purpose-slot-" + slot
      Files.createDirectories(scenario.root.resolve(identity))
      Json.obj("identity" -> identity.asJson, "root" -> scenario.root.resolve(identity).toString.asJson)
    }
    val slotgrants = slots.map(slot => Json.obj("componentId" -> org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId.name.asJson,
      "workspaceId" -> slot.hcursor.get[Json]("identity").toOption.get, "access" -> "manage".asJson))
    base.mapObject(fields => fields.add("gitPrograms", gitprograms.asJson).add("sbtPrograms", sbtprograms.asJson)
      .add("workspaces", (_array(base, "workspaces") ++ slots ++ mergeplans.flatMap(_array(_, "workspaces"))).distinct.asJson)
      .add("workspaceGrants", (_array(base, "workspaceGrants") ++ slotgrants ++ mergeplans.flatMap(_array(_, "workspaceGrants"))).distinct.asJson)
      .add("grants", fields("grants").get.mapArray(_.map(_.mapObject(grant => grant.add("capabilities",
        grant("capabilities").get.mapArray(existing => (existing ++ (gitprograms ++ sbtprograms)
          .map(_.hcursor.get[Json]("capability").toOption.get)).distinct)))))))
  }
  private def _fresh(): F.Scenario = {
    val scenario = fresh("id11-push-continuation", checked = true)
    val repository = _repository(scenario)
    val descriptor = scenario.root.resolve(".textus/assembly.yaml")
    val assembly = Files.readString(descriptor, UTF_8)
    assembly should include("      id: participant\n")
    Files.writeString(descriptor, assembly.replace("      id: participant\n", "      id: sm-workflow-local\n"), UTF_8)
    val marker = Json.fromString(scenario.root.resolve("push-sbt-invocations.txt").toString).noSpaces
    val build = """scalaVersion := "2.12.18"
      |libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % Test
      |Global / onLoad := {
      |  val original = (Global / onLoad).value
      |  state => {
      |    val loaded = original(state)
      |    IO.append(file(__MARKER__), java.lang.ProcessHandle.current().pid().toString + "\n")
      |    loaded
      |  }
      |}
      |""".stripMargin.replace("__MARKER__", marker)
    Files.writeString(repository.resolve("build.sbt"), build)
    // ProductCheck belongs to the common tiny fixture; ordinary ScalaTest is
    // selected here so mandatory TestOnly discovers a real nonempty suite.
    val test = repository.resolve("src/test/scala/fixture/SelectedInputSpec.scala")
    Files.createDirectories(test.getParent)
    Files.writeString(test, """package fixture
      |class SelectedInputSpec extends org.scalatest.wordspec.AnyWordSpec
      |    with org.scalatest.GivenWhenThen with org.scalatest.matchers.should.Matchers {
      |  "the push candidate's product inputs" should {
      |    "retain the intended value and complete tracked remote input" in {
      |      Given("the actual materialized candidate workspace")
      |      val root = java.nio.file.Paths.get("").toAbsolutePath.normalize
      |      When("the product reads its value and independent remote resource")
      |      val value = java.nio.file.Files.readString(root.resolve("value.txt"))
      |      val remote = java.nio.file.Files.readString(root.resolve("src/main/resources/remote.txt"))
      |      Then("the accepted product value and one complete known resource revision are available")
      |      value shouldBe "expected\n"
      |      Vector("baseline remote input\n", "remote before failed push\n", "remote after description\n") should contain(remote)
      |    }
      |  }
      |}
      |""".stripMargin)
    Files.createDirectories(repository.resolve(_remote_path).getParent)
    Files.writeString(repository.resolve(_remote_path), "baseline remote input\n")
    _documents.foreach(path => Files.writeString(repository.resolve(path), "baseline document\n"))
    git(scenario.root, "-C", repository.toString, "add", "--", "build.sbt", "src", "docs")
    git(scenario.root, "-C", repository.toString, "commit", "-m", "Retain tiny push continuation fixture inputs")
    git(scenario.root, "-C", repository.toString, "push", scenario.host.target.endpoint, "HEAD:refs/heads/main")
    val slots = (1 to 3).toVector.map(slot => P.Slot("purpose-slot-" + slot,
      if (slot == 3) _checks else Vector.empty, GoalPhaseStepAssessment.Policy("push-purpose-" + slot,
        if (slot == 3) "code" else "documentation", "required", "off", "off", Vector.empty)))
    configure(scenario, selectionParameters(corrected = true) ++ Map(
      "sm-workflow.repository-sync.dirty-purposes" -> P.Policy(slots, Vector.empty).asJson.noSpaces,
      "textus.subsystem.process-execution.plan" -> _plan(scenario).noSpaces))
    _documents.zipWithIndex.foreach((path, index) => Files.writeString(repository.resolve(path), "accepted document " + (index + 1) + "\n"))
    Files.writeString(repository.resolve("value.txt"), "expected\n")
    scenario
  }
  private def _move_remote(scenario: F.Scenario, suffix: String, text: String): String = {
    val peer = scenario.root.resolve("independent-remote-" + suffix)
    Files.exists(peer) shouldBe false
    git(scenario.root, "clone", "--branch", "main", "--", scenario.host.target.endpoint, peer.toString)
    Files.writeString(peer.resolve(_remote_path), text)
    git(scenario.root, "-C", peer.toString, "add", "--", _remote_path)
    git(scenario.root, "-C", peer.toString, "commit", "-m", "Independent remote fixture progress " + suffix)
    git(scenario.root, "-C", peer.toString, "push", scenario.host.target.endpoint, "HEAD:refs/heads/main")
    _remote(scenario)
  }
  private def _review(scenario: F.Scenario, session: String, issued: F.Result, suffix: String): Unit = {
    successful(issued).business[V.RepositorySyncAdvanceResult].submitOperation shouldBe Some("submitNativeMergeReview")
    val directory = Files.createTempDirectory(scenario.root, "fixture-independent-push-review-" + suffix + "-")
    val reference = V.WorkflowArtifactReference(directory.getFileName.toString, "1")
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("fixture-independent-reviewer"), None, None)
    val verdict = V.RepositorySyncNativeMergeReviewResult("accept",
      "Independent fixture review of this actual candidate, selected formal checks and preserved intent; no consumer approval is asserted", Vector.empty)
    val input = V.WorkflowEvidencePreparationInput("RepositorySync", "review", issued.invocation.stdout.toUri.toASCIIString,
      _save_new(directory, "answer.json", json(verdict)), _save_new(directory, "reference.json", json(reference)),
      _save_new(directory, "execution.json", json(execution)), "normal-development-host", java.time.Instant.now().toString,
      "entity:smworkflow.cli.host_evidence", None)
    val prepared = successful(scenario.prepareHostInput("prepareEvidence", take(new org.goldenport.record.io.RecordDecoder().json(json(input)))))
    scenario.selectPreparedImport(prepared)
    successful(scenario.repositoryCall("submitNativeMergeReview", Vector("--sessionId", session, "--result", json(verdict),
      "--resultReference", json(reference), "--executionEvidence", json(execution))))
      .business[V.RepositorySyncNativeMergeReviewSubmissionResult].accepted shouldBe true
  }
  private def _describe(scenario: F.Scenario, session: String): F.Result =
    successful(scenario.repositoryCall(U.describeOperation, Vector("--sessionId", session)))
  private def _direction(scenario: F.Scenario, session: String, described: F.Result, choice: String): V.RepositorySyncPushContinuationAdoptionInput = {
    val description = described.business[V.RepositorySyncPushContinuationDescriptionResult]
    val target = take(U.targetC(description.target))
    val participant = target.context.participantIdentity
    val directory = Files.createTempDirectory(scenario.root, "fixture-independent-push-human-")
    val reference = V.WorkflowArtifactReference(directory.getFileName.toString, "1")
    val answer = V.RepositorySyncPushContinuationAnswer(choice,
      "Fixture-owned independent Human direction: the prior worker ended; accept original UNKNOWN effects and the finite fresh remote integration plan. This is not consumer approval.", true, true)
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some(participant), None, None)
    val input = V.WorkflowEvidencePreparationInput("RepositorySync", "push-continuation", described.invocation.stdout.toUri.toASCIIString,
      _save_new(directory, "answer.json", json(answer)), _save_new(directory, "reference.json", json(reference)),
      _save_new(directory, "execution.json", json(execution)), "normal-development-host", java.time.Instant.now().toString,
      "entity:smworkflow.cli.host_evidence", Some(participant))
    val prepared = successful(scenario.prepareHostInput("prepareEvidence", take(new org.goldenport.record.io.RecordDecoder().json(json(input)))))
    scenario.selectPreparedImport(prepared)
    V.RepositorySyncPushContinuationAdoptionInput(session, description.target, answer.choice, answer.rationale,
      reference, answer.priorExecutionEnded, answer.acceptOriginalUncertainty, execution)
  }
  private def _adopt(scenario: F.Scenario, input: V.RepositorySyncPushContinuationAdoptionInput): F.Result =
    scenario.repositoryCall(U.adoptionOperation, Vector("--input", json(input)))
  private def _reopen(episode: Json): F.Scenario = {
    val root = Path.of(episode.hcursor.get[String]("root").toOption.get)
    val stream = Files.list(root.resolve("logs"))
    val ordinal = try math.max(1000, stream.filter(path => path.getFileName.toString.matches("[0-9]+\\.(stdout\\.json|stderr)"))
      .mapToInt(path => path.getFileName.toString.takeWhile(_.isDigit).toInt).max().orElse(999) + 1) finally stream.close()
    F.reopenInstalledRepository(root, take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(episode.hcursor.get[Json]("host").toOption.get)),
      episode.hcursor.get[GoalPhaseOrderedProgression.Plan]("plan").toOption.get, ordinal)
  }

  "ID11 retained push remote continuation" should {
    "preserve the original failed episode across publication" which {
      if (_phase == "before") {
        "retains real failed nonforce push and observation-only UNKNOWN before publication" in {
          Given("the explicit before phase and a fresh absolute checkpoint destination")
          installed()
          val path = _episode_path
          Files.exists(path) shouldBe false
          val scenario = _fresh()
          val baseline = _head(scenario)
          val session = start(scenario)
          When("ordinary Advance captures three actual dirty Purposes and their ordered grouping is submitted")
          successful(advance(scenario, session)).business[V.RepositorySyncAdvanceResult].submitOperation shouldBe Some("submitRepositoryPurposeResult")
          val request = purpose_inputs(scenario).head
          request.files.map(_.path).toSet shouldBe (_documents :+ "value.txt").toSet
          val groups = (_documents :+ "value.txt").zipWithIndex.map { (path, index) =>
            P.Group("push-purpose-" + (index + 1), "Retain independently accepted purpose " + (index + 1), _messages(index),
              if (index == 0) Vector.empty else Vector("push-purpose-" + index),
              Vector(P.Content(path, Some(Files.readString(_repository(scenario).resolve(path))))), Vector.empty,
              "The next purpose retains the previous accepted intent as an ancestor")
          }
          successful(submit_purpose(scenario, session, P.Result("resolved", "Three ordered fixture-owned purposes", groups, Vector.empty)))
            .business[V.RepositorySyncConflictSubmissionResult].accepted shouldBe true
          (1 to 3).foreach { index =>
            When("ordinary Advance prepares Purpose " + index + " and its independent fixture review accepts it")
            val issued = successful(advance(scenario, session))
            _review(scenario, session, issued, "purpose-" + index)
            Then("earlier Purpose commits remain installed while the just-reviewed candidate awaits its owning Advance")
            _commits(scenario, session).size shouldBe index - 1
          }
          val accepted = _record(scenario, session)
          val checks = _formal(scenario, session)
          Then("the original code candidate has actual successful Compile Test and mandatory nonempty TestOnly receipts")
          checks.map(_._2.identity).toSet shouldBe _checks.map(_.identity).toSet
          checks.size shouldBe 3
          checks.foreach(_._2.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded)
          val selected = checks.find(_._2.identity == appendedCheck.identity).get._2.sbt.get
          selected.selection.get.testClasses shouldBe Vector(selectedClass)
          selected.selection.get.testsRun should be > 0L
          _invocations(scenario).size shouldBe 3

          When("an independent fixture checkout advances the actual bare remote after the final review and before the ordinary push")
          val remotetip = _move_remote(scenario, "before-failure", "remote before failed push\n")
          _record(scenario, session) shouldBe accepted
          val failed = advance(scenario, session)
          Then("the old runtime's real nonforce push fails after installing all three accepted Purpose commits")
          withClue(failed.invocation.logs) { failed.exitCode should not be 0 }
          _remote(scenario) shouldBe remotetip
          git(scenario.root, "-C", _repository(scenario).toString, "status", "--porcelain") shouldBe ""
          val commits = _commits(scenario, session)
          commits.size shouldBe 3
          git(scenario.root, "-C", _repository(scenario).toString, "rev-list", "--reverse", baseline + "..HEAD").linesIterator.toVector shouldBe commits
          commits.zipWithIndex.foreach { (commit, index) =>
            git(scenario.root, "-C", _repository(scenario).toString, "show", "-s", "--format=%s", commit) shouldBe _messages(index)
          }
          val intents = receipts(scenario, session).filter(_.reference.identity.endsWith(":native-push-intent"))
          intents.size shouldBe 1
          intents.head.payload.value.stage shouldBe "NativePushPreparing"
          intents.head.payload.value.pushObservation shouldBe None
          receipts(scenario, session).exists(_.reference.identity.endsWith(":native-push-observed")) shouldBe false
          val failedstate = _snapshot(scenario, session)
          When("one later old ordinary Advance observes the unresolved intent")
          val unknown = advance(scenario, session)
          Then("UNKNOWN 503 refuses a retry and preserves all accepted commits checks native intent and remote state")
          unknown.exitCode should not be 0
          unknown.invocation.logs should include("503")
          unknown.invocation.logs should include("Push intent remains unresolved")
          _snapshot(scenario, session) shouldBe failedstate
          _formal(scenario, session) shouldBe checks
          val logs = scenario.invocations.map(_capture_invocation)
          val episode = Json.obj("request" -> "SYNC-20261104-11-RETAINED-PUSH-REMOTE-ADVANCE-CONTINUATION".asJson,
            "root" -> scenario.root.toString.asJson, "physicalRoot" -> scenario.root.toRealPath().toString.asJson,
            "database" -> scenario.root.resolve("workflow.sqlite").toRealPath().toString.asJson,
            "session" -> session.asJson, "host" -> ThreeWorkflowRuntimeParameters.encode(scenario.host), "plan" -> scenario.plan.asJson,
            "oldInstalledCommand" -> installed().toString.asJson, "baseline" -> baseline.asJson,
            "acceptedBeforePushRecord" -> accepted.asJson, "failedPush" -> _capture(failed), "unknownObservation" -> _capture(unknown),
            "originalWorkerEffects" -> "UNKNOWN".asJson, "state" -> failedstate, "logs" -> logs.asJson)
          Files.writeString(path, episode.noSpaces, UTF_8, StandardOpenOption.CREATE_NEW)
        }
      }
    }

    "continue only from independently adopted current remote inputs" which {
      if (_phase == "after") {
        "rejects passive invalid crossed and stale directions then converges through one fresh normal integration" in {
          Given("the explicit after phase and the exact original retained physical host database repository and Session")
          installed()
          val path = _episode_path
          val bytes = Files.readString(path)
          val episode = parse(bytes).toOption.get
          val scenario = _reopen(episode)
          val session = episode.hcursor.get[String]("session").toOption.get
          scenario.root.toRealPath().toString shouldBe episode.hcursor.get[String]("physicalRoot").toOption.get
          scenario.root.resolve("workflow.sqlite").toRealPath().toString shouldBe episode.hcursor.get[String]("database").toOption.get
          if (current(scenario, session).lifecycle == Lifecycle.Completed) {
            _collect_completed(scenario, session, episode, bytes)
          } else {
          val original = episode.hcursor.get[Json]("state").toOption.get
          val partial = _partial_direction(scenario, session, episode)
          episode.hcursor.get[String]("originalWorkerEffects").toOption.get shouldBe "UNKNOWN"
          episode.hcursor.downField("logs").as[Vector[Json]].toOption.get.foreach { log =>
            Files.readString(Path.of(log.hcursor.get[String]("stdoutPath").toOption.get)) shouldBe log.hcursor.get[String]("stdout").toOption.get
            Files.readString(Path.of(log.hcursor.get[String]("stderrPath").toOption.get)) shouldBe log.hcursor.get[String]("stderr").toOption.get
          }
          val catalog = take(WorkflowInstanceRecordJsonV1.catalogC(Vector(take(RepositorySyncDefinitionBinding.bindC))))
          val old = take(WorkflowInstanceRecordJsonV1.decodeC(original.hcursor.get[String]("record").toOption.get, catalog))
          val oldnative = _array(original, "native")
          val oldintents = receipts(scenario, session).filter(_.reference.identity.endsWith(":native-push-intent"))
          oldintents.size shouldBe 1
          val oldintent = oldintents.head
          oldintent.payload.value.pushObservation shouldBe None
          val oldintentobservation = oldnative.filter(_.hcursor.get[String]("reference").toOption.contains(oldintent.reference.identity))
          oldintentobservation.size shouldBe 1
          _native(scenario, session).filter(_.hcursor.get[String]("reference").toOption.contains(oldintent.reference.identity)) shouldBe oldintentobservation
          val oldchecks = original.hcursor.get[Vector[(String, C.Receipt)]]("formal").toOption.get
          val oldcommits = original.hcursor.get[Vector[String]]("commits").toOption.get
          val oldhead = original.hcursor.get[String]("head").toOption.get
          val oldinvocations = original.hcursor.get[Vector[String]]("invocations").toOption.get
          _formal(scenario, session) shouldBe oldchecks
          _commits(scenario, session) shouldBe oldcommits
          _invocations(scenario) shouldBe oldinvocations
          val described = partial match {
            case None =>
              val before = _snapshot(scenario, session)
              before shouldBe original
              When("public description is collected twice and independent fixture Human evidence chooses Defer")
              val described = _describe(scenario, session)
              val description = described.business[V.RepositorySyncPushContinuationDescriptionResult]
              take(U.targetC(_describe(scenario, session).business[V.RepositorySyncPushContinuationDescriptionResult].target)) shouldBe
                take(U.targetC(description.target))
              description.availableChoices should contain("RefreshRemoteAndContinue")
              description.availableChoices should contain("Defer")
              val deferred = _direction(scenario, session, described, "Defer")
              val deferresult = successful(_adopt(scenario, deferred)).business[V.RepositorySyncPushContinuationAdoptionResult]
              Then("description preparation and Defer preserve the complete native episode with no mutating Git effects or checks")
              deferresult.accepted shouldBe false
              _snapshot(scenario, session) shouldBe before

              When("the current description receives missing-ended missing-uncertainty invalid-choice crossed-session or missing-evidence answers")
              val testedinput = _direction(scenario, session, described, "RefreshRemoteAndContinue")
              val invalid = Vector(testedinput.copy(priorExecutionEnded = false), testedinput.copy(acceptOriginalUncertainty = false),
                testedinput.copy(choice = "RetryPush"), testedinput.copy(sessionId = session + "-crossed"),
                testedinput.copy(executionEvidence = V.WorkflowExecutionEvidence(Vector(testedinput.directionReference),
                  Some("crossed-fixture-human"), None, None)),
                testedinput.copy(executionEvidence = V.WorkflowExecutionEvidence(Vector.empty,
                  Some(take(U.targetC(description.target)).context.participantIdentity), None, None)),
                testedinput.copy(directionReference = V.WorkflowArtifactReference("foreign-push-direction", "1")))
              invalid.foreach { input =>
                _adopt(scenario, input).exitCode should not be 0
                _snapshot(scenario, session) shouldBe before
              }
              described
            case Some(saved) =>
              Given("the exact actual partial failure with one accepted direction and no later fetch push or checks")
              val directory = scenario.root.resolve(saved.reference.identity)
              val reference = _read_value[V.WorkflowArtifactReference](directory.resolve("reference.json"))
              val execution = _read_value[V.WorkflowExecutionEvidence](directory.resolve("execution.json"))
              val input = V.RepositorySyncPushContinuationAdoptionInput(session, U.targetRecord(saved.target), saved.choice,
                saved.rationale, reference, saved.priorExecutionEnded, saved.acceptOriginalUncertainty, execution)
              val before = _snapshot(scenario, session)
              When("the corrected runtime receives the previously accepted missing-reference answer even at duplicate adoption")
              val rejected = _adopt(scenario, input.copy(executionEvidence = execution.copy(references = Vector.empty)))
              Then("the corrected guard now rejects missing references without rewriting the earlier actual acceptance or current state")
              rejected.exitCode should not be 0
              _snapshot(scenario, session) shouldBe before
              _checkpoint(scenario, session, "missing-references-now-rejected", Json.obj(
                "direction" -> saved.asJson, "correctedRefusal" -> _capture(rejected)))

              When("independent actual remote progress supersedes the unused previously accepted direction")
              _checkpoint(scenario, session, "before-partial-remote-progress", Json.obj("direction" -> saved.asJson))
              _move_remote(scenario, "after-partial-adoption", "baseline remote input\n")
              val moved = _snapshot(scenario, session)
              _checkpoint(scenario, session, "before-refresh-required-observation", Json.obj("direction" -> saved.asJson))
              val waiting = successful(advance(scenario, session)).business[V.RepositorySyncAdvanceResult]
              Then("ordinary Advance retains RefreshRequired Wait without any fresh fetch push or check execution")
              waiting.kind shouldBe "Wait"
              waiting.condition shouldBe Some("NativePushRefreshRequired")
              _assert_unused_direction(scenario, session, saved.target)
              _formal(scenario, session) shouldBe oldchecks
              _invocations(scenario) shouldBe oldinvocations
              _head(scenario) shouldBe oldhead
              _snapshot(scenario, session).hcursor.get[String]("remote").toOption.get shouldBe moved.hcursor.get[String]("remote").toOption.get
              _checkpoint(scenario, session, "refresh-required-observed", Json.obj("direction" -> saved.asJson))
              _describe(scenario, session)
          }
          val description = described.business[V.RepositorySyncPushContinuationDescriptionResult]
          val staleinput = _direction(scenario, session, described, "RefreshRemoteAndContinue")
          When("the actual remote advances after description and the formerly exact prepared answer is submitted")
          _checkpoint(scenario, session, "before-stale-remote-progress", Json.obj("unadoptedInput" -> parse(json(staleinput)).toOption.get))
          val newremote = _move_remote(scenario, "after-description", "remote after description\n")
          val moved = _snapshot(scenario, session)
          val refused = _adopt(scenario, staleinput)
          Then("stale remote facts refuse adoption without a direction or any product effect")
          refused.exitCode should not be 0
          _snapshot(scenario, session) shouldBe moved

          When("a new public description is independently prepared and its exact current target is adopted once")
          val freshdescription = _describe(scenario, session)
          val target = take(U.targetC(freshdescription.business[V.RepositorySyncPushContinuationDescriptionResult].target))
          target should not be take(U.targetC(description.target))
          val input = _direction(scenario, session, freshdescription, "RefreshRemoteAndContinue")
          val predecessor = current(scenario, session)
          _checkpoint(scenario, session, "before-current-direction-adoption", Json.obj("input" -> parse(json(input)).toOption.get))
          val accepted = successful(_adopt(scenario, input)).business[V.RepositorySyncPushContinuationAdoptionResult]
          val admitted = current(scenario, session)
          Then("one passive direction preserves original UNKNOWN all old work and the exact existing fixed check selection")
          accepted.accepted shouldBe true
          accepted.outcome shouldBe "adopted"
          admitted.revision shouldBe predecessor.revision.next
          admitted.history.take(old.history.size) shouldBe old.history
          _head(scenario) shouldBe oldhead
          _remote(scenario) shouldBe newremote
          _formal(scenario, session) shouldBe oldchecks
          _commits(scenario, session) shouldBe oldcommits
          _invocations(scenario) shouldBe oldinvocations
          oldnative.foreach(value => _native(scenario, session) should contain(value))
          _native(scenario, session).filter(_.hcursor.get[String]("reference").toOption.contains(oldintent.reference.identity)) shouldBe oldintentobservation
          oldintent.payload.value.pushObservation shouldBe None
          val direction = receipts(scenario, session).filter(_.payload.value.stage == "PushContinuationAdmitted")
            .maxBy(_.next.revision.value).payload.value.pushContinuation.get
          direction.priorExecutionEnded shouldBe true
          direction.acceptOriginalUncertainty shouldBe true
          direction.target shouldBe target
          partial match {
            case None =>
              direction.target.sourceNamespace shouldBe ""
              direction.target.retainedIntent.map(_.kind) shouldBe Some("push")
              direction.target.retainedIntent.map(_.reference.identity) shouldBe Some(oldintent.reference.identity)
            case Some(previous) =>
              direction.target.sourceNamespace shouldBe U.namespace(previous.target)
              direction.target.retainedIntent shouldBe None
              receipts(scenario, session).exists(_.payload.value.pushContinuation.contains(previous)) shouldBe true
              _assert_unused_direction(scenario, session, previous.target)
          }
          direction.target.nativeObservation shouldBe None
          val passive = _snapshot(scenario, session)
          When("the identical answer is repeated and a contradictory answer reuses its evidence")
          successful(_adopt(scenario, input)).business[V.RepositorySyncPushContinuationAdoptionResult].outcome shouldBe "already-adopted"
          _adopt(scenario, input.copy(rationale = "Contradictory fixture rationale")).exitCode should not be 0
          Then("the duplicate remains idempotent and contradictory evidence cannot append another direction")
          _snapshot(scenario, session) shouldBe passive

          When("separate ordinary Advance owns the finite fresh fetch actual integration and new original checks")
          _checkpoint(scenario, session, "before-fresh-integration", Json.obj("acceptedInput" -> parse(json(input)).toOption.get))
          val issued = _advance_to_boundary(scenario, session)
          val freshchecks = _formal(scenario, session).filterNot(oldchecks.contains)
          Then("the fresh candidate obtains exactly one successful original Compile Test TestOnly set and its selected admission boundary")
          freshchecks.size shouldBe 3
          freshchecks.map(_._2.identity).toSet shouldBe _checks.map(_.identity).toSet
          freshchecks.foreach(_._2.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded)
          val focused = freshchecks.find(_._2.identity == appendedCheck.identity).get._2.sbt.get
          focused.selection.get.testClasses shouldBe Vector(selectedClass)
          focused.selection.get.testsRun should be > 0L
          _invocations(scenario).size shouldBe oldinvocations.size + 3
          _invocations(scenario).take(oldinvocations.size) shouldBe oldinvocations
          _formal(scenario, session).filter(oldchecks.contains) shouldBe oldchecks
          _commits(scenario, session) shouldBe oldcommits
          val boundary = issued.business[V.RepositorySyncAdvanceResult]
          val terminal = if (boundary.kind == "Terminal") {
            Then("the unchanged conditional policy admits the actually nonoverlapping candidate without a new independent review")
            _assert_conditional_admission(scenario, session, target)
            boundary
          } else {
            boundary.submitOperation shouldBe Some("submitNativeMergeReview")
            val waiting = _snapshot(scenario, session)
            When("Advance recollects the pending new independent review and the same accepted adoption answer is resubmitted")
            successful(advance(scenario, session)).business[V.RepositorySyncAdvanceResult].issuedWorkContext shouldBe boundary.issuedWorkContext
            successful(_adopt(scenario, input)).business[V.RepositorySyncPushContinuationAdoptionResult].outcome shouldBe "already-adopted"
            Then("the pending review and repeated accepted answer are passive and preserve the same native snapshot")
            _snapshot(scenario, session) shouldBe waiting
            When("the selected fresh independent fixture review accepts and normal Advance owns nonforce push final fetch and convergence")
            _checkpoint(scenario, session, "before-fresh-review", Json.obj("issued" -> _capture(issued), "acceptedInput" -> parse(json(input)).toOption.get))
            _review(scenario, session, issued, "fresh-integration")
            _checkpoint(scenario, session, "before-final-convergence", Json.obj("acceptedInput" -> parse(json(input)).toOption.get))
            successful(advance(scenario, session)).business[V.RepositorySyncAdvanceResult]
          }
          Then("the same Session completes cleanly with all three original commits and actual new remote work as ancestors")
          terminal.kind shouldBe "Terminal"
          terminal.sessionId shouldBe session
          val completed = current(scenario, session)
          completed.lifecycle shouldBe Lifecycle.Completed
          completed.history.take(old.history.size) shouldBe old.history
          val tip = _head(scenario)
          _remote(scenario) shouldBe tip
          tip should not be oldhead
          val ancestry = git(scenario.root, "-C", _repository(scenario).toString, "rev-list", "HEAD").linesIterator.toSet
          (oldcommits :+ newremote).foreach(commit => ancestry should contain(commit))
          _commits(scenario, session) shouldBe oldcommits
          Files.readString(_repository(scenario).resolve("value.txt")) shouldBe "expected\n"
          Files.readString(_repository(scenario).resolve(_remote_path)) shouldBe "remote after description\n"
          _documents.zipWithIndex.foreach((path, index) => Files.readString(_repository(scenario).resolve(path)) shouldBe "accepted document " + (index + 1) + "\n")
          git(scenario.root, "-C", _repository(scenario).toString, "status", "--porcelain") shouldBe ""
          Files.exists(_repository(scenario).resolve(".git/MERGE_HEAD")) shouldBe false
          oldnative.foreach(value => _native(scenario, session) should contain(value))
          _assert_finite_effects(scenario, session, target)
          partial.foreach(previous => _assert_unused_direction(scenario, session, previous.target))
          _invocations(scenario).size shouldBe oldinvocations.size + 3
          val finalstate = _snapshot(scenario, session)
          When("ordinary Advance recollects completed convergence")
          successful(advance(scenario, session)).business[V.RepositorySyncAdvanceResult] shouldBe terminal
          Then("completed recollection is read-only and the original checkpoint and literal old failure captures remain byte-exact")
          _snapshot(scenario, session) shouldBe finalstate
          Files.readString(path) shouldBe bytes
          val resultpath = path.resolveSibling(path.getFileName.toString.stripSuffix(".json") + "-after.json")
          Files.writeString(resultpath, Json.obj("originalEpisode" -> path.toString.asJson, "session" -> session.asJson,
            "target" -> parse(take(input.target.toJsonStringC)).toOption.get, "adoption" -> parse(json(input)).toOption.get,
            "originalFailure" -> episode.hcursor.get[Json]("failedPush").toOption.get,
            "originalUnknownObservation" -> episode.hcursor.get[Json]("unknownObservation").toOption.get,
            "freshReviewPerformed" -> (boundary.kind != "Terminal").asJson,
            "state" -> finalstate, "terminal" -> parse(json(terminal)).toOption.get,
            "newLogs" -> scenario.invocations.map(_capture_invocation).asJson).noSpaces, UTF_8, StandardOpenOption.CREATE_NEW)
          }
        }
      }
    }
  }

  private def _assert_conditional_admission(scenario: F.Scenario, session: String, target: U.Target): Json = {
    val prefix = session + ":native-" + U.namespace(target) + "-"
    val native = receipts(scenario, session).filter(_.reference.identity.startsWith(prefix))
    val admitted = native.filter(_.reference.identity == prefix + "merge-admitted")
    val validated = native.filter(_.reference.identity == prefix + "merge-validated")
    admitted.size shouldBe 1
    validated.size shouldBe 1
    val payload = admitted.head.payload.value
    payload.stage shouldBe "MergeAdmitted"
    payload.assessment should not be empty
    val assessment = payload.assessment.get
    target.validationPolicy.assessment shouldBe Some(assessment.policy)
    assessment.policy.detailedReview shouldBe "conditional"
    assessment.policy.contractPaths shouldBe empty
    assessment.branch shouldBe "admit"
    assessment.sourceRevision shouldBe validated.head.next.revision.value.toString
    assessment.overlappingPaths shouldBe empty
    assessment.contractPaths shouldBe empty
    assessment.conflictResolution shouldBe false
    assessment.triageReference shouldBe None
    payload.repairContext shouldBe None
    val validation = payload.validation.get
    validation.selected shouldBe target.validationPolicy.checks
    validation.selected shouldBe _checks
    validation.inputs.get.unknownPaths shouldBe empty
    validation.inputs.get.binaryPaths shouldBe empty
    val formal = validation.receipts.filter(_.sbt.nonEmpty)
    formal.map(_.identity) shouldBe _checks.map(_.identity)
    formal.foreach { receipt =>
      receipt.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded
      receipt.termination shouldBe "exited"
      receipt.exitCode shouldBe Some(0)
    }
    native.filter(_.payload.value.stage == "ReviewAccepted") shouldBe empty
    Json.obj("disposition" -> "conditional-native-admission".asJson,
      "freshReviewPerformed" -> false.asJson, "assessment" -> assessment.asJson,
      "formal" -> formal.asJson)
  }
  private def _literal_file(path: Path): Json = Json.obj("path" -> path.toString.asJson, "text" -> Files.readString(path).asJson)
  /** Collect the actual already-completed known qualification. This branch
    * performs one public completed Advance only; no proposal, adoption, remote
    * movement, check, review, commit or push is replayed.
    */
  private def _collect_completed(scenario: F.Scenario, session: String, episode: Json, bytes: String): Unit = {
    Given("the exact completed Session and the retained actual Terminal from the second failed fixture qualification")
    _scope(session == "c895225f3e7596c812221a52991a51ec" &&
      scenario.root.getFileName.toString == "id11-push-continuation-8854132415819711112",
      "only the identified actually completed fixture is admitted to read-only collection")
    val checkpointpath = scenario.root.resolve("fixture-push-progress-before-fresh-integration-9682477229265565026/checkpoint.json")
    val refusalpath = scenario.root.resolve("fixture-push-progress-missing-references-now-rejected-5220799205742964932/checkpoint.json")
    val terminalpath = scenario.root.resolve("logs/1021.stdout.json")
    val required = Vector(checkpointpath, refusalpath, terminalpath, scenario.root.resolve("logs/1021.stderr"),
      scenario.root.resolve("logs/1010.stdout.json"), scenario.root.resolve("logs/1011.stderr"))
    _scope(required.forall(path => Files.isRegularFile(path)) && (1000 to 1021).forall(ordinal =>
      Files.isRegularFile(scenario.root.resolve(s"logs/$ordinal.stdout.json")) && Files.isRegularFile(scenario.root.resolve(s"logs/$ordinal.stderr"))),
      "the exact accepted-input checkpoint, corrected refusal and all actual qualification CLI logs")
    val checkpoint = parse(Files.readString(checkpointpath)).toOption.get
    _scope(checkpoint.hcursor.get[String]("originalEpisode").toOption.contains(_episode_path.toString) &&
      checkpoint.hcursor.get[String]("session").toOption.contains(session) &&
      checkpoint.hcursor.get[String]("stage").toOption.contains("before-fresh-integration"),
      "the fixture checkpoint belongs to this original episode and Session")
    val inputjson = checkpoint.hcursor.downField("detail").get[Json]("acceptedInput").toOption.get
    val input = take(U.adoptionInputC(take(new org.goldenport.record.io.RecordDecoder().json(inputjson.noSpaces))))
    val target = take(U.targetC(input.target))
    _scope(input.sessionId == session && target.sessionId == session && target.frontierRevision == "59" &&
      target.sourceNamespace == "push-continuation-57" && target.retainedIntent.isEmpty && target.nativeObservation.isEmpty,
      "the actual latest frontier59 target preserves the preceding UNKNOWN direction")
    When("the retained native directions and checked conditional admission are read without executing effects")
    val native = receipts(scenario, session)
    val adoptions = native.filter(_.payload.value.stage == "PushContinuationAdmitted")
    Then("the saved directions and complete original history agree with the exact retained checkpoint")
    _scope(adoptions.size == 2 && adoptions.exists(_.reference.identity == session + ":native-push-continuation-57-adopted"),
      "only the original unused direction and the actual latest adopted direction exist")
    val latest = adoptions.find(_.reference.identity == session + ":native-" + U.adoptionKey(target))
    _scope(latest.nonEmpty && latest.get.payload.value.pushContinuation.nonEmpty, "the exact latest saved direction")
    val expected = U.Direction(target, input.choice, input.rationale, input.executionEvidence.workerIdentity.get,
      ContextReference(input.directionReference.identity, input.directionReference.revision), input.priorExecutionEnded,
      input.acceptOriginalUncertainty)
    _scope(latest.get.payload.value.pushContinuation.contains(expected) &&
      take(WorkflowInstanceRecordJsonV1.encodeC(latest.get.next)) == checkpoint.hcursor.downField("state").get[String]("record").toOption.get,
      "the complete latest canonical Direction and admission record match the retained accepted input")
    val previous = adoptions.find(_.reference.identity == session + ":native-push-continuation-57-adopted").get
      .payload.value.pushContinuation.get
    val originaltarget = take(U.targetC(_read_value[V.RepositorySyncPushContinuationDescriptionResult](scenario.root.resolve("logs/1000.stdout.json")).target))
    previous.target shouldBe originaltarget
    previous.priorExecutionEnded shouldBe true
    previous.acceptOriginalUncertainty shouldBe true
    previous.target.nativeObservation shouldBe None
    previous.target.retainedIntent.map(_.reference.identity) shouldBe Some(session + ":native-push-intent")
    val acceptedempty = _read_value[V.RepositorySyncPushContinuationAdoptionResult](scenario.root.resolve("logs/1010.stdout.json"))
    acceptedempty.accepted shouldBe true
    acceptedempty.outcome shouldBe "adopted"
    val refusal = parse(Files.readString(refusalpath)).toOption.get
    val refusalcapture = refusal.hcursor.downField("detail").get[Json]("correctedRefusal").toOption.get
    refusalcapture.hcursor.get[Int]("exitCode").toOption.get should not be 0
    refusalcapture.hcursor.get[String]("stderr").toOption.get shouldBe Files.readString(scenario.root.resolve("logs/1011.stderr"))
    refusal.hcursor.downField("detail").get[U.Direction]("direction").toOption.get shouldBe previous

    val original = episode.hcursor.get[Json]("state").toOption.get
    val oldnative = _array(original, "native")
    oldnative.size shouldBe 45
    oldnative.foreach(value => _native(scenario, session) should contain(value))
    _array(checkpoint.hcursor.get[Json]("state").toOption.get, "native").foreach(value => _native(scenario, session) should contain(value))
    val oldchecks = original.hcursor.get[Vector[(String, C.Receipt)]]("formal").toOption.get
    val oldcommits = original.hcursor.get[Vector[String]]("commits").toOption.get
    val oldhead = original.hcursor.get[String]("head").toOption.get
    oldchecks.size shouldBe 3
    oldcommits.size shouldBe 3
    _formal(scenario, session).filter(oldchecks.contains) shouldBe oldchecks
    _commits(scenario, session) shouldBe oldcommits
    val oldintent = native.filter(_.reference.identity == session + ":native-push-intent")
    oldintent.size shouldBe 1
    oldintent.head.payload.value.pushObservation shouldBe None
    episode.hcursor.get[String]("originalWorkerEffects").toOption.get shouldBe "UNKNOWN"
    episode.hcursor.get[Json]("unknownObservation").toOption.get.hcursor.get[Int]("exitCode").toOption.get should not be 0
    _array(episode, "logs").foreach { log =>
      Files.readString(Path.of(log.hcursor.get[String]("stdoutPath").toOption.get)) shouldBe log.hcursor.get[String]("stdout").toOption.get
      Files.readString(Path.of(log.hcursor.get[String]("stderrPath").toOption.get)) shouldBe log.hcursor.get[String]("stderr").toOption.get
    }
    val catalog = take(WorkflowInstanceRecordJsonV1.catalogC(Vector(take(RepositorySyncDefinitionBinding.bindC))))
    val old = take(WorkflowInstanceRecordJsonV1.decodeC(original.hcursor.get[String]("record").toOption.get, catalog))
    val completed = current(scenario, session)
    completed.lifecycle shouldBe Lifecycle.Completed
    completed.history.take(old.history.size) shouldBe old.history
    val fresh = _formal(scenario, session).filterNot(oldchecks.contains)
    fresh.size shouldBe 3
    fresh.map(_._2.identity).toSet shouldBe _checks.map(_.identity).toSet
    fresh.foreach(_._2.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded)
    val focused = fresh.find(_._2.identity == appendedCheck.identity).get._2.sbt.get
    focused.selection.get.testClasses shouldBe Vector(selectedClass)
    focused.selection.get.completedSuites shouldBe 1
    focused.selection.get.testsRun shouldBe 1L
    val oldinvocations = original.hcursor.get[Vector[String]]("invocations").toOption.get
    _invocations(scenario).take(oldinvocations.size) shouldBe oldinvocations
    _invocations(scenario).size shouldBe oldinvocations.size + 3
    Then("the actual conditional policy admitted the nonoverlapping checked integration without a new fixture review")
    val admission = _assert_conditional_admission(scenario, session, target)
    target.validationPolicy.assessment shouldBe previous.target.validationPolicy.assessment
    _assert_finite_effects(scenario, session, target)
    _assert_unused_direction(scenario, session, previous.target)
    val tip = _head(scenario)
    val tree = _tree(scenario)
    _scope(tip == "bd175765e933701b380cdc3f0f97b14c45e49869" && tree == "f073e4b1382a128eda534fc1ba5db401f29fe6f2",
      "the exact actual completed tip and tree from log1021")
    _remote(scenario) shouldBe tip
    git(scenario.root, "-C", _repository(scenario).toString, "branch", "--show-current") shouldBe "main"
    git(scenario.root, "-C", _repository(scenario).toString, "rev-parse", "refs/remotes/origin/main") shouldBe tip
    val ancestry = git(scenario.root, "-C", _repository(scenario).toString, "rev-list", "HEAD").linesIterator.toSet
    (oldcommits ++ Vector(oldhead, target.advertisedRemoteTip)).foreach(commit => ancestry should contain(commit))
    Files.readString(_repository(scenario).resolve("value.txt")) shouldBe "expected\n"
    Files.readString(_repository(scenario).resolve(_remote_path)) shouldBe "remote after description\n"
    _documents.zipWithIndex.foreach((path, index) => Files.readString(_repository(scenario).resolve(path)) shouldBe "accepted document " + (index + 1) + "\n")
    git(scenario.root, "-C", _repository(scenario).toString, "status", "--porcelain") shouldBe ""
    Files.exists(_repository(scenario).resolve(".git/MERGE_HEAD")) shouldBe false
    val terminal = _read_value[V.RepositorySyncAdvanceResult](terminalpath)
    terminal.sessionId shouldBe session
    terminal.kind shouldBe "Terminal"
    val terminaljson = GoalPhaseNativeMetadata.normalizePublicJson(parse(Files.readString(terminalpath)).toOption.get).fold(fail(_), identity)
    val convergence = terminaljson.hcursor.downField("convergence")
    convergence.get[String]("repositoryRoot").toOption.get shouldBe _repository(scenario).toString
    convergence.get[String]("tip").toOption.get shouldBe tip
    convergence.get[String]("tree").toOption.get shouldBe tree
    val qualificationroot = _episode_path.getParent.getParent.resolve("id11-push-after")
    val failurepaths = Vector("advance01-1-stdout.log", "advance01-1-stderr.log", "advance02-1-stdout.log", "advance02-1-stderr.log")
      .map(name => qualificationroot.resolve(name))
    _scope(failurepaths.forall(path => Files.isRegularFile(path)), "both original failed qualification outputs remain available")
    val failures = failurepaths.map(_literal_file)
    val retainedlogs = (1000 to 1021).toVector.flatMap(ordinal => Vector(
      scenario.root.resolve(s"logs/$ordinal.stdout.json"), scenario.root.resolve(s"logs/$ordinal.stderr"))).map(_literal_file)
    val before = _snapshot(scenario, session)
    When("one separate ordinary completed Advance recollects the already converged Session")
    successful(advance(scenario, session)).business[V.RepositorySyncAdvanceResult] shouldBe terminal
    Then("recollection changes no native record Git state checks or original failure evidence")
    _snapshot(scenario, session) shouldBe before
    Files.readString(_episode_path) shouldBe bytes
    failures.foreach(value => Files.readString(Path.of(value.hcursor.get[String]("path").toOption.get)) shouldBe value.hcursor.get[String]("text").toOption.get)
    retainedlogs.foreach(value => Files.readString(Path.of(value.hcursor.get[String]("path").toOption.get)) shouldBe value.hcursor.get[String]("text").toOption.get)
    val resultpath = _episode_path.resolveSibling(_episode_path.getFileName.toString.stripSuffix(".json") + "-after.json")
    Files.writeString(resultpath, Json.obj("originalEpisode" -> _episode_path.toString.asJson, "session" -> session.asJson,
      "collection" -> "read-only-completed-session".asJson, "acceptedInput" -> inputjson,
      "actualConditionalAdmission" -> admission, "freshReviewPerformed" -> false.asJson,
      "originalFailure" -> episode.hcursor.get[Json]("failedPush").toOption.get,
      "originalUnknownObservation" -> episode.hcursor.get[Json]("unknownObservation").toOption.get,
      "acceptedEmptyReferencesFailure" -> _literal_file(scenario.root.resolve("logs/1010.stdout.json")),
      "correctedMissingReferenceRefusal" -> refusalcapture, "qualificationFailures" -> failures.asJson,
      "retainedCliLogs" -> retainedlogs.asJson, "actualTerminal" -> terminaljson, "state" -> before,
      "recollectionLogs" -> scenario.invocations.map(_capture_invocation).asJson).noSpaces,
      UTF_8, StandardOpenOption.CREATE_NEW)
  }

  private def _capture_invocation(invocation: F.Invocation): Json = Json.obj("argv" -> invocation.argv.asJson,
    "pid" -> invocation.pid.asJson, "stdoutPath" -> invocation.stdout.toString.asJson,
    "stderrPath" -> invocation.stderr.toString.asJson, "stdout" -> Files.readString(invocation.stdout).asJson,
    "stderr" -> Files.readString(invocation.stderr).asJson)
  private def _advance_to_boundary(scenario: F.Scenario, session: String): F.Result = {
    var result = successful(advance(scenario, session))
    var count = 1
    while (result.business[V.RepositorySyncAdvanceResult].kind != "Terminal" &&
        result.business[V.RepositorySyncAdvanceResult].submitOperation.isEmpty && count < 8) {
      result.business[V.RepositorySyncAdvanceResult].kind shouldBe "Wait"
      _checkpoint(scenario, session, "before-integration-advance-" + (count + 1),
        Json.obj("previousAdvance" -> _capture(result)))
      result = successful(advance(scenario, session))
      count += 1
    }
    val boundary = result.business[V.RepositorySyncAdvanceResult]
    if (boundary.kind != "Terminal") boundary.submitOperation shouldBe Some("submitNativeMergeReview")
    result
  }
  private def _assert_unused_direction(scenario: F.Scenario, session: String, target: U.Target): Unit = {
    val prefix = session + ":native-" + U.namespace(target) + "-"
    val keys = receipts(scenario, session).map(_.reference.identity).filter(_.startsWith(prefix))
    val effects = Vector("refresh-fetch-intent", "refresh-fetch-result", "refresh-fetch-observed", "push-intent",
      "push-result", "push-observed", "final-fetch-intent", "converged")
    keys.filter(key => effects.exists(suffix => key == prefix + suffix)) shouldBe empty
  }
  private def _assert_finite_effects(scenario: F.Scenario, session: String, target: U.Target): Unit = {
    val native = receipts(scenario, session)
    val prefix = session + ":native-" + U.namespace(target) + "-"
    val continuation = native.filter(_.reference.identity.startsWith(prefix))
    continuation should not be empty
    val keys = continuation.map(_.reference.identity)
    keys.filter(_.endsWith("-refresh-fetch-intent")).size shouldBe 1
    keys.filter(_.endsWith("-refresh-fetch-result")).size shouldBe 1
    keys.filter(_.endsWith("-refresh-fetch-observed")).size shouldBe 1
    continuation.find(_.reference.identity.endsWith("-refresh-fetch-observed")).get.payload.value.stage shouldBe "PushRefreshFetched"
    val fetched = continuation.find(_.reference.identity.endsWith("-refresh-fetch-result")).get.payload.value
    fetched.stage shouldBe "PushRefreshFetched"
    fetched.pushObservation should not be empty
    val fetch = fetched.pushObservation.get
    fetch.operation shouldBe GitOperation.Fetch
    fetch.outcome shouldBe GitExecutionOutcome.Succeeded
    fetch.valueObserved shouldBe true
    WorkflowSbtCapture.termination(fetch.process) shouldBe "exited"
    WorkflowSbtCapture.exitCode(fetch.process) shouldBe Some(0)
    keys.filter(_.endsWith("-push-intent")).size shouldBe 1
    keys.filter(_.endsWith("-push-observed")).size shouldBe 1
    val pushed = continuation.find(_.reference.identity.endsWith("-push-observed")).get.payload.value
    pushed.pushObservation should not be empty
    val push = pushed.pushObservation.get
    push.operation shouldBe GitOperation.Push(take(GitObjectId.parseC(_head(scenario))),
      take(GitBranchName.parseC(scenario.host.target.remoteBranch)))
    push.outcome shouldBe GitExecutionOutcome.Succeeded
    push.valueObserved shouldBe true
    WorkflowSbtCapture.termination(push.process) shouldBe "exited"
    WorkflowSbtCapture.exitCode(push.process) shouldBe Some(0)
    keys.filter(_.endsWith("-final-fetch-intent")).size shouldBe 1
    keys.filter(_.endsWith("-converged")).size shouldBe 1
    native.count(_.reference.identity.endsWith(":native-push-intent")) shouldBe 1
    native.count(_.payload.value.stage == "PurposeInstalled") shouldBe 3
    purpose_inputs(scenario).size shouldBe 1
  }
}
