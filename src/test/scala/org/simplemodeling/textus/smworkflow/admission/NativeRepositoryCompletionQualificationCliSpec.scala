package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import org.goldenport.cncf.processexecution.SbtOperation
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowContinuation
import org.goldenport.cncf.workflow.WorkflowWorkOrderJsonV1
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import RepositorySyncFormalFailureContinuation as H
import RepositorySyncNativeMergeRepair as M
import RepositorySyncNativeMergeValidation as C
import RepositorySyncPurpose as P
import SplitPhaseExplicitPartitionFixture.take

/** Fresh tiny repositories qualify the selected installed command only.
  * Proposals, prior-worker directions and independent reviews are authored
  * specification inputs, never evidence of live Human or provider acceptance.
  * Existing consumer Sessions and previously retained episodes are not used.
  * @since Nov. 3, 2026
  * @version Nov. 3, 2026
  */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeRepositoryCompletionQualificationCliSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with NativeRepositoryContinuationCliFixture {
  private val _suite = "qualification.ProductInputSpec"
  private val _purpose_checks = Vector(C.Check("formal-compile", 1, WorkflowSbtValidation.Kind.Compile),
    C.Check("formal-test", 2, WorkflowSbtValidation.Kind.TestOnly),
    C.Check("formal-lint", 3, WorkflowSbtValidation.Kind.FixCheck))
  private val _merge_checks = Vector(C.Check("formal-compile", 1, WorkflowSbtValidation.Kind.Compile),
    C.Check("formal-test", 2, WorkflowSbtValidation.Kind.Test))

  private def _installed: Path = {
    val property = sys.props.get("smworkflow.spec.installedCommand")
    val executable = Path.of(property.orElse(sys.env.get("SM_WORKFLOW_SPEC_INSTALLED_COMMAND"))
      .getOrElse(fail("Select the actual installed command with smworkflow.spec.installedCommand or SM_WORKFLOW_SPEC_INSTALLED_COMMAND")))
    executable.isAbsolute shouldBe true
    executable.normalize shouldBe executable
    Files.isRegularFile(executable) shouldBe true
    Files.isExecutable(executable) shouldBe true
    if (property.isEmpty) System.setProperty("smworkflow.spec.installedCommand", executable.toString)
    executable
  }
  private def _success(result: F.Result): F.Result = {
    withClue(result.invocation.logs) { result.exitCode shouldBe 0 }
    result.invocation.argv.head shouldBe _installed.toString
    result
  }
  private def _fresh(name: String): F.Scenario = {
    _installed
    val scenario = fresh(name, checked = true)
    val descriptor = scenario.root.resolve(".textus/assembly.yaml")
    val original = Files.readString(descriptor, UTF_8)
    original should include("      id: participant\n")
    Files.writeString(descriptor, original.replace("      id: participant\n", "      id: sm-workflow-local\n"), UTF_8)
    scenario
  }
  private def _array(value: Json, name: String): Vector[Json] = value.hcursor.get[Vector[Json]](name).toOption.get
  private def _host_plan(scenario: F.Scenario, checks: Vector[C.Check], purpose: Boolean, broken: Boolean): Json = {
    val base = process_plan(scenario.root, "Equal", scenario.host.goal)
    val selections = checks.map { check =>
      val operation = check.kind match {
        case WorkflowSbtValidation.Kind.Compile => SbtOperation.Compile
        case WorkflowSbtValidation.Kind.Test => SbtOperation.Test
        case WorkflowSbtValidation.Kind.TestOnly => SbtOperation.TestOnly
        case WorkflowSbtValidation.Kind.FixCheck => SbtOperation.FixCheck
      }
      TypedSbtHostPlanFixture.plan(scenario.root, TypedSbtHostPlanFixture.normalExecutable, Vector(check.ordinal),
        operation = operation, testClasses = if (operation == SbtOperation.TestOnly) Vector(_suite) else Vector.empty)
    }
    val programs = selections.zip(checks).flatMap { (selection, check) => _array(selection, "sbtPrograms").map { program =>
      val selected = if (purpose) program.mapObject(_.add("target", "purpose-slot-1".asJson)) else program
      if (broken && check.kind == WorkflowSbtValidation.Kind.TestOnly) selected.mapObject(fields => fields.add("environment",
        fields("environment").get.mapObject(_.add("JAVA_TOOL_OPTIONS", "-XX:SmWorkflowInvalidFixtureOption".asJson))))
      else selected
    }}
    val workspaces = if (purpose) _array(base, "workspaces") else
      (_array(base, "workspaces") ++ selections.flatMap(_array(_, "workspaces"))).distinct
    val workspacegrants = if (purpose) _array(base, "workspaceGrants") else
      (_array(base, "workspaceGrants") ++ selections.flatMap(_array(_, "workspaceGrants"))).distinct
    base.mapObject(fields => fields.add("sbtPrograms", programs.asJson).add("workspaces", workspaces.asJson)
      .add("workspaceGrants", workspacegrants.asJson).add("grants", fields("grants").get.mapArray(_.map(_.mapObject(grant =>
        grant.add("capabilities", grant("capabilities").get.mapArray(existing =>
          (existing ++ programs.map(_.hcursor.get[Json]("capability").toOption.get)).distinct)))))))
  }
  private def _parameters(checks: Vector[C.Check]): Map[String, String] = {
    val paths = Vector("value.txt", "src", "docs", "build.sbt", "project")
    val impact = RepositorySyncProgramImpact.Policy(checks.map(check => RepositorySyncProgramImpact.Coverage(check,
      paths, paths, "The actual product inputs require these fixed checks")), checks, paths,
      "Complete the selected tiny product checks", Vector.empty)
    val slot = P.Slot("purpose-slot-1", checks,
      GoalPhaseStepAssessment.Policy("completion-purpose", "code", "required", "off", "off", Vector.empty))
    maintenance_parameters() ++ Map("sm-workflow.repository-sync.program-inputs" -> impact.asJson.noSpaces,
      "sm-workflow.repository-sync.dirty-purposes" -> P.Policy(Vector(slot), Vector.empty).asJson.noSpaces)
  }
  private def _start(scenario: F.Scenario): F.Result = _success(scenario.repositoryCall("startRepositorySync", Vector("--input",
    "{\"repositoryIdentity\":\"completion-qualification-fixture\",\"syncMode\":\"FULL_STATE\"}")))
  private def _formal(scenario: F.Scenario, session: String): Vector[(String, C.Receipt)] =
    receipts(scenario, session).flatMap(receipt => {
      val payload = receipt.payload.value
      val last = if (receipt.reference.identity.contains("validation-result-") ||
          receipt.reference.identity.matches(".*-check-[0-9]+-result"))
        payload.validation.map(_.receipts).orElse(payload.purposes.map(_.receipts)).flatMap(_.lastOption)
        else None
      last.filter(_.sbt.nonEmpty).map(receipt.reference.identity -> _)
    }).sortBy(_._1)
  private def _repair_count(record: InstanceRecord): Int =
    record.history.flatMap(_.suspension).count(_.continuationIdentity.value.contains(":native-repair:"))
  private def _retain(name: String, scenario: F.Scenario, session: String, calls: Vector[F.Result],
    before: Vector[(String, C.Receipt)], after: Vector[(String, C.Receipt)], kind: String, details: Json): Unit =
    sys.props.get("smworkflow.spec.completionEvidenceDirectory")
      .orElse(sys.env.get("SM_WORKFLOW_SPEC_COMPLETION_EVIDENCE_DIRECTORY")).foreach { selected =>
      val directory = Path.of(selected)
      directory.isAbsolute shouldBe true
      directory.normalize shouldBe directory
      Files.isDirectory(directory) shouldBe true
      val summary = Json.obj("qualification" -> name.asJson, "root" -> scenario.root.toString.asJson,
        "session" -> session.asJson, "installedCommand" -> _installed.toString.asJson,
        "oldFormalIdentities" -> before.map(_._1).asJson, "newFormalIdentities" -> after.map(_._1).filterNot(before.map(_._1).contains).asJson,
        "terminalKind" -> kind.asJson, "details" -> details,
        "calls" -> calls.map(result => Json.obj("argv" -> result.invocation.argv.asJson, "pid" -> result.invocation.pid.asJson,
          "exitCode" -> result.exitCode.asJson, "stdout" -> result.invocation.stdout.toString.asJson,
          "stderr" -> result.invocation.stderr.toString.asJson)).asJson)
      Files.writeString(directory.resolve(name + ".json"), summary.noSpaces, UTF_8, StandardOpenOption.CREATE_NEW)
    }

  "ID03 installed completion qualification" should {
    "continue Purpose through focused checks and ordinary completion" in {
      Given("a fresh tiny ScalaTest product with a real focused suite and a broken TestOnly launcher only")
      val scenario = _fresh("completion-purpose-focused")
      val repository = Path.of(scenario.host.target.root)
      Files.writeString(repository.resolve("build.sbt"), """scalaVersion := "2.12.18"
        |libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % Test
        |addCommandAlias("fixCheck", "Test / scalafix --check")
        |""".stripMargin, UTF_8)
      Files.writeString(repository.resolve("project/plugins.sbt"),
        "addSbtPlugin(\"ch.epfl.scala\" % \"sbt-scalafix\" % \"0.12.1\")\n", UTF_8)
      Files.copy(Path.of(".scalafix.conf").toAbsolutePath, repository.resolve(".scalafix.conf"))
      Files.writeString(repository.resolve("src/test/scala/ProductInputSpec.scala"), """package qualification
        |
        |import java.nio.file.{Files, Paths}
        |import org.scalatest.GivenWhenThen
        |import org.scalatest.matchers.should.Matchers
        |import org.scalatest.wordspec.AnyWordSpec
        |
        |final class ProductInputSpec extends AnyWordSpec with Matchers with GivenWhenThen {
        |  "The product input" should {
        |    "retain the expected complete line" in {
        |      Given("the current product workspace")
        |      val input = Paths.get("value.txt")
        |      When("the product reads its selected input")
        |      val actual = Files.readString(input)
        |      Then("the complete expected line including LF is present")
        |      actual shouldBe "expected\n"
        |    }
        |  }
        |}
        |""".stripMargin, UTF_8)
      git(scenario.root, "-C", repository.toString, "add", "--", "build.sbt", "project/plugins.sbt", ".scalafix.conf",
        "src/test/scala/ProductInputSpec.scala")
      git(scenario.root, "-C", repository.toString, "commit", "-m", "Tiny focused product and ordinary lint inputs")
      git(scenario.root, "-C", repository.toString, "push", scenario.host.target.endpoint, "HEAD:refs/heads/main")
      val broken = _host_plan(scenario, _purpose_checks, purpose = true, broken = true)
      val corrected = broken.mapObject(fields => fields.add("sbtPrograms", fields("sbtPrograms").get.mapArray(_.map(
        _.mapObject(program => program.add("environment", program("environment").get.mapObject(_.remove("JAVA_TOOL_OPTIONS"))))))))
      configure(scenario, _parameters(_purpose_checks) + ("textus.subsystem.process-execution.plan" -> broken.noSpaces))
      Files.writeString(repository.resolve("value.txt"), "expected\n", UTF_8)
      When("one installed Start and ordinary Purpose submission check that actual candidate under the broken host")
      val started = _start(scenario)
      val session = started.business[V.RepositorySyncStartResult].sessionId
      val issued = _success(advance(scenario, session))
      issued.business[V.RepositorySyncAdvanceResult].submitOperation shouldBe Some("submitRepositoryPurposeResult")
      val request = purpose_inputs(scenario).head
      val proposal = P.Result("resolved", "Specification-owned expected product input purpose",
        Vector(P.Group("product-input", "Retain the expected product input", "fix: retain expected product input", Vector.empty,
          Vector(P.Content("value.txt", Some("expected\n"))), Vector.empty, "The focused product consumes value.txt")),
        request.files.map(_.path).filterNot(_ == "value.txt"))
      val submitted = _success(submit_purpose(scenario, session, proposal))
      submitted.business[V.RepositorySyncConflictSubmissionResult].accepted shouldBe true
      val failedcall = _success(advance(scenario, session))
      failedcall.business[V.RepositorySyncAdvanceResult].sessionId shouldBe session
      val original = _formal(scenario, session)
      val old = current(scenario, session)
      val repaircount = _repair_count(old)
      val head = git(scenario.root, "-C", repository.toString, "rev-parse", "HEAD")
      val index = git(scenario.root, "-C", repository.toString, "write-tree")
      Then("the retained actual Compile succeeded and the following TestOnly launcher formally failed")
      original.map(_._2.identity).toSet shouldBe Set("formal-compile", "formal-test")
      original.size shouldBe 2
      original.find(_._2.identity == "formal-compile").get._2.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded
      val oldfailure = original.find(_._2.identity == "formal-test").get._2
      oldfailure.sbt.get.kind shouldBe WorkflowSbtValidation.Kind.TestOnly
      oldfailure.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Failed
      oldfailure.exitCode.exists(_ != 0) shouldBe true
      When("only the bad host launcher option is removed and the fixture supplies its explicit new-attempt direction")
      configure(scenario, Map("textus.subsystem.process-execution.plan" -> corrected.noSpaces))
      val described = _success(describe(scenario, session))
      val description = described.business[V.RepositorySyncValidationContinuationDescriptionResult]
      val target = take(RepositorySyncValidationContinuation.anyTargetC(description.target)) match {
        case RepositorySyncValidationContinuation.FormalFailureTarget(value) => value
        case _ => fail("The actual formal failed TestOnly target is required")
      }
      target.failed shouldBe oldfailure
      target.successfulPrefix.last.identity shouldBe "formal-compile"
      val input = direction(scenario, session, described, H.choices.head, target.pending.nonEmpty, target.pending.nonEmpty)
      val adoption = _success(adopt(scenario, input))
      adoption.business[V.RepositorySyncValidationContinuationAdoptionResult].outcome shouldBe "adopted"
      val adopted = current(scenario, session)
      Then("adoption retains the old history full receipts candidate and repair accounting without executing checks")
      adopted.history.take(old.history.size) shouldBe old.history
      _formal(scenario, session) shouldBe original
      _repair_count(adopted) shouldBe repaircount
      git(scenario.root, "-C", repository.toString, "rev-parse", "HEAD") shouldBe head
      git(scenario.root, "-C", repository.toString, "write-tree") shouldBe index
      When("separate ordinary Advance runs the focused failed check and the remaining genuine lint check")
      val reviewcall = _success(advance(scenario, session))
      val review = reviewcall.business[V.RepositorySyncAdvanceResult]
      val after = _formal(scenario, session)
      Then("the original failure and Compile survive while only the failed check receives a new successful host attempt")
      original.foreach(value => after should contain(value))
      after.count(_._2.identity == "formal-compile") shouldBe 1
      after.count(_._2.identity == "formal-test") shouldBe 2
      after.count(_._2.identity == "formal-lint") shouldBe 1
      val newtest = after.find(value => value._2.identity == "formal-test" && !original.exists(_._1 == value._1)).get
      newtest._1 should include("-host-" + target.frontierRevision + "-check-1-result")
      newtest._2.sbt.get.kind shouldBe WorkflowSbtValidation.Kind.TestOnly
      newtest._2.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded
      val selection = newtest._2.sbt.get.selection.get
      selection.testClasses shouldBe Vector(_suite)
      selection.completedSuites should be > 0
      selection.testsRun should be > 0L
      after.find(_._2.identity == "formal-lint").get._2.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded
      review.kind shouldBe "Request"
      review.review should not be empty
      review.submitOperation shouldBe Some("submitNativeMergeReview")
      _repair_count(current(scenario, session)) shouldBe repaircount
      When("the fixture supplies a separate specification-owned independent review through ordinary evidence preparation and Submit")
      val verdict = V.RepositorySyncNativeMergeReviewResult("accept",
        "Specification-owned independent review of the actual focused product candidate; no live provider acceptance claimed", Vector.empty)
      val reference = V.WorkflowArtifactReference("completion-focused-independent-review", "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("specification-independent-reviewer"), None, None)
      val directory = Files.createTempDirectory(scenario.root, "completion-focused-review-")
      val preparation = V.WorkflowEvidencePreparationInput("RepositorySync", "review", reviewcall.invocation.stdout.toUri.toASCIIString,
        save(directory, "result.json", json(verdict)), save(directory, "reference.json", json(reference)),
        save(directory, "execution.json", json(execution)), "normal-development-host", "2026-11-03T00:00:00Z",
        "entity:smworkflow.cli.host_evidence", None)
      val prepared = _success(scenario.prepareHostInput("prepareEvidence",
        take(new org.goldenport.record.io.RecordDecoder().json(json(preparation)))))
      scenario.selectPreparedImport(prepared)
      val reviewed = _success(scenario.repositoryCall("submitNativeMergeReview", Vector("--sessionId", session,
        "--result", json(verdict), "--resultReference", json(reference), "--executionEvidence", json(execution))))
      reviewed.business[V.RepositorySyncNativeMergeReviewSubmissionResult].accepted shouldBe true
      val terminalcall = _success(advance(scenario, session))
      val terminal = terminalcall.business[V.RepositorySyncAdvanceResult]
      Then("the same fresh Session reaches ordinary Terminal without replaying the successful prefix or changing its repair count")
      terminal.kind shouldBe "Terminal"
      val completed = current(scenario, session)
      completed.lifecycle shouldBe Lifecycle.Completed
      completed.history.take(old.history.size) shouldBe old.history
      _repair_count(completed) shouldBe repaircount
      _formal(scenario, session) shouldBe after
      Files.readString(repository.resolve("value.txt"), UTF_8) shouldBe "expected\n"
      _retain("purpose-focused-completion", scenario, session,
        Vector(started, issued, submitted, failedcall, described, adoption, reviewcall, prepared, reviewed, terminalcall),
        original, after, terminal.kind, Json.obj("originalHead" -> head.asJson, "originalIndexTree" -> index.asJson,
          "newSuccessfulTestIdentity" -> newtest._1.asJson, "completedSuites" -> selection.completedSuites.asJson,
          "testsRun" -> selection.testsRun.asJson, "repairCount" -> repaircount.asJson))
    }

    "issue a complete 251-file repair after an actual failed merge check" in {
      Given("a fresh tiny checked repository with 250 tracked documents and diverged local and remote product inputs")
      val scenario = _fresh("completion-251-file-issuance")
      val repository = Path.of(scenario.host.target.root)
      val documents = (1 to 250).map(ordinal => f"docs/bulk-$ordinal%03d.md").toVector
      val paths = (documents :+ "value.txt").sorted
      Files.createDirectories(repository.resolve("docs"))
      documents.foreach(path => Files.writeString(repository.resolve(path), "baseline " + path + "\n", UTF_8))
      git(scenario.root, "-C", repository.toString, "add", "--", "docs")
      git(scenario.root, "-C", repository.toString, "commit", "-m", "Tiny 250-document baseline")
      git(scenario.root, "-C", repository.toString, "push", scenario.host.target.endpoint, "HEAD:refs/heads/main")
      val peer = scenario.root.resolve("bulk-peer")
      git(scenario.root, "clone", "--branch", "main", "--", scenario.host.target.endpoint, peer.toString)
      Files.writeString(repository.resolve("value.txt"), "local product input\n", UTF_8)
      git(scenario.root, "-C", repository.toString, "add", "--", "value.txt")
      git(scenario.root, "-C", repository.toString, "commit", "-m", "Local conflicting product input")
      val head = git(scenario.root, "-C", repository.toString, "rev-parse", "HEAD")
      documents.foreach(path => Files.writeString(peer.resolve(path), "remote " + path + "\n", UTF_8))
      Files.writeString(peer.resolve("value.txt"), "remote product input\n", UTF_8)
      git(scenario.root, "-C", peer.toString, "add", "--", "docs", "value.txt")
      git(scenario.root, "-C", peer.toString, "commit", "-m", "Remote 250 documents and conflicting product input")
      git(scenario.root, "-C", peer.toString, "push", scenario.host.target.endpoint, "HEAD:refs/heads/main")
      val remote = git(scenario.root, "-C", peer.toString, "rev-parse", "HEAD")
      configure(scenario, _parameters(_merge_checks) + ("textus.subsystem.process-execution.plan" ->
        _host_plan(scenario, _merge_checks, purpose = false, broken = false).noSpaces))
      When("one installed Start and ordinary Advance observe the real pending merge conflict")
      val started = _start(scenario)
      val session = started.business[V.RepositorySyncStartResult].sessionId
      val conflicted = _success(advance(scenario, session))
      conflicted.business[V.RepositorySyncAdvanceResult].kind shouldBe "Wait"
      current(scenario, session).currentProgression shouldBe Some(ProgressionReference("sync.MergeConflicted"))
      val conflictcall = _success(advance(scenario, session))
      val conflict = conflictcall.business[V.RepositorySyncAdvanceResult]
      Then("the genuine conflict WorkOrder assigns only the one colliding value path")
      conflict.conflict.get.conflicts.map(_.path) shouldBe Vector("value.txt")
      conflict.submitOperation shouldBe Some("submitRepositorySyncWorkResult")
      When("the fixture resolves the actual conflict to deliberately failing product input and ordinary Advance runs real checks")
      val resolution = "resolved but deliberately incorrect\n"
      val result = V.RepositorySyncConflictResult("resolved", conflict.conflict.get.conflicts.map(value =>
        V.RepositorySyncConflictResolution(value.identity, resolution)), "Specification-owned deliberately failing product resolution")
      val reference = V.WorkflowArtifactReference("completion-251-conflict-proposal", "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("specification-conflict-author"), None, None)
      val submitted = _success(scenario.repositoryCall("submitRepositorySyncWorkResult", Vector("--sessionId", session,
        "--result", json(result), "--resultReference", json(reference), "--executionEvidence", json(execution))))
      submitted.business[V.RepositorySyncConflictSubmissionResult].accepted shouldBe true
      val repaircall = _success(advance(scenario, session))
      val issued = repaircall.business[V.RepositorySyncAdvanceResult]
      val original = _formal(scenario, session)
      val active = current(scenario, session)
      val identity = active.suspension.get.continuationIdentity.value
      val retained = stored(scenario).flatMap(raw => WorkflowWorkOrderJsonV1.decodeC(raw, M.inputCodec).toOption)
        .flatMap(_.current match {
          case order: WorkflowContinuation.WorkOrder[M.Request] if order.request.continuationId.value == identity =>
            order.request.input.map(_.value)
          case _ => None
        })
      Then("the same active Session retains the actual failed check and issues every one of its 251 exact ordinary files")
      original.size shouldBe 2
      original.map(_._2.identity).toSet shouldBe Set("formal-compile", "formal-test")
      original.find(_._2.identity == "formal-compile").get._2.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded
      original.find(_._2.identity == "formal-test").get._2.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Failed
      original.find(_._2.identity == "formal-test").get._2.exitCode.exists(_ != 0) shouldBe true
      original.foreach { (_, receipt) =>
        receipt.processObservation should not be empty
        receipt.processObservation.get.stdout.truncated shouldBe false
        receipt.processObservation.get.stderr.truncated shouldBe false
      }
      issued.kind shouldBe "Request"
      issued.sessionId shouldBe session
      issued.submitOperation shouldBe Some("submitNativeMergeRepair")
      issued.repair.get.validationFailure shouldBe Some("formal-test")
      issued.repair.get.files.map(_.path) shouldBe paths
      issued.repair.get.review.changedPaths shouldBe paths
      retained.size shouldBe 1
      val input = retained.head
      input.validationFailure shouldBe Some("formal-test")
      input.files.size shouldBe 251
      input.files.map(_.path) shouldBe paths
      input.review.changedPaths shouldBe paths
      input.files.map(file => file.path -> file.text) shouldBe paths.map(path =>
        path -> (if (path == "value.txt") resolution else "remote " + path + "\n"))
      input.files.foreach { file =>
        file.text shouldBe Files.readString(repository.resolve(file.path), UTF_8)
        file.text.contains('\u0000') shouldBe false
        UTF_8.newEncoder().canEncode(file.text) shouldBe true
      }
      issued.repair.get.files.map(file => file.path -> file.text) shouldBe input.files.map(file => file.path -> file.text)
      active.identity.value shouldBe session
      active.lifecycle shouldBe Lifecycle.Active
      identity should startWith(session + ":native-repair:")
      active.history.exists(_.currentProgression.contains(ProgressionReference("sync.RepairProposalAdmitted"))) shouldBe false
      git(scenario.root, "-C", repository.toString, "rev-parse", "HEAD") shouldBe head
      git(scenario.root, "-C", repository.toString, "rev-parse", "MERGE_HEAD") shouldBe remote
      val tree = git(scenario.root, "-C", repository.toString, "write-tree")
      input.review.localTip shouldBe head
      input.review.remoteTip shouldBe remote
      input.review.indexTree shouldBe tree
      _retain("merge-251-file-issuance", scenario, session, Vector(started, conflicted, conflictcall, submitted, repaircall),
        original, original, issued.kind, Json.obj("qualificationScope" -> "issuance-only; repair proposal remains pending".asJson,
          "head" -> head.asJson, "indexTree" -> tree.asJson, "remoteTip" -> remote.asJson,
          "continuationId" -> identity.asJson, "fileCount" -> input.files.size.asJson))
    }
  }
}
