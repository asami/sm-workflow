package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import org.goldenport.cncf.processexecution.{SbtOperation, SbtTestReassessment}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.Lifecycle
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import RepositorySyncPurpose as P
import RepositorySyncPurposeTestOnlyContinuation as A
import RepositorySyncValidationContinuation as R
import SplitPhaseExplicitPartitionFixture.take

/** Actual installed commands and one isolated repository and Session across publication.
  * @since   Nov. 21, 2026
  * @version Nov. 21, 2026
  */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeRepositoryPurposeAdditionalTestOnlyCliSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with NativeRepositorySelectionCorrectionFixture {
  private val _route = "purpose-additional-test-only"
  private val _kind = "purposeAdditionalTestOnly"
  private val _alias = "fixture-candidate"
  private val _parent = "fixture-candidate-parent"
  private val _mode = sys.props.getOrElse("smworkflow.spec." + _kind + "EpisodeStage", "normal")

  private def _array(value: Json, key: String): Vector[Json] = value.hcursor.get[Vector[Json]](key).toOption.get

  private def _configure(scenario: F.Scenario, append: Boolean, fixed: Boolean = true, changedProgram: Boolean = false): Unit = {
    val raw = parse(Files.readString(scenario.root.resolve(".textus/config.json"))).toOption.get
    val prior = parse(raw.hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
    val parent = Files.createDirectories(scenario.root.resolve(_parent))
    val alias = Files.createDirectories(scenario.root.resolve(_alias))
    val component = SmWorkflowComponent.componentId.name
    val candidategit = _array(prior, "gitRepositories").find(_.hcursor.get[String]("target")
      .contains("sm-workflow-repository")).get.mapObject(_.add("target", _alias.asJson)
      .add("capability", "fixture-candidate-git".asJson).add("identity", "fixture-candidate-git".asJson))
    val operations = Vector(SbtOperation.Compile, SbtOperation.Test) ++ Option.when(append && fixed)(SbtOperation.TestOnly)
    val programs = operations.zipWithIndex.flatMap { (operation, index) =>
      val plan = TypedSbtHostPlanFixture.plan(scenario.root, TypedSbtHostPlanFixture.normalExecutable, Vector(index + 1),
        operation = operation, testClasses = Option.when(operation == SbtOperation.TestOnly)(selectedClass).toVector)
      _array(plan, "sbtPrograms").map(_.mapObject { fields =>
        val bound = fields.add("target", _alias.asJson)
        if (changedProgram && operation == SbtOperation.TestOnly) bound.add("environment",
          bound("environment").get.mapObject(_.add("PURPOSE_ADDITIONAL_TEST_FIXTURE", "changed".asJson)))
        else bound
      })
    }
    val capabilities = (programs :+ candidategit).map(_.hcursor.get[Json]("capability").toOption.get)
    val plan = prior.mapObject(fields => fields.add("schema", "cncf.runtime-process-execution.v7".asJson)
      .add("sessionCandidateWorkspaces", Json.arr(Json.obj("componentId" -> component.asJson,
        "parent" -> _parent.asJson, "alias" -> _alias.asJson)))
      .add("workspaces", (fields("workspaces").get.asArray.get.filterNot(row =>
        Set(_alias, _parent)(row.hcursor.get[String]("identity").toOption.get)) ++ Vector(
        Json.obj("identity" -> _parent.asJson, "root" -> parent.toString.asJson),
        Json.obj("identity" -> _alias.asJson, "root" -> alias.toString.asJson))).asJson)
      .add("workspaceGrants", (fields("workspaceGrants").get.asArray.get.filterNot(row =>
        Set(_alias, _parent)(row.hcursor.get[String]("workspaceId").toOption.get)) ++ Vector(_parent, _alias).map(name =>
        Json.obj("componentId" -> component.asJson, "workspaceId" -> name.asJson, "access" -> "manage".asJson))).asJson)
      .add("gitRepositories", (_array(prior, "gitRepositories").filterNot(_.hcursor.get[String]("target").contains(_alias)) :+ candidategit).asJson)
      .add("sbtPrograms", programs.asJson)
      .add("grants", fields("grants").get.mapArray(_.map(_.mapObject(grant => grant.add("capabilities",
        grant("capabilities").get.mapArray(values => (values ++ capabilities).distinct)))))))
    val checks = originalChecks ++ Option.when(append)(appendedCheck)
    val paths = Vector("value.txt", "build.sbt", "project", "src")
    val impact = RepositorySyncProgramImpact.Policy(checks.map(check => RepositorySyncProgramImpact.Coverage(
      check, paths, paths, "Checks on the actual candidate product")), checks, paths, "Full fixture validation", Vector.empty)
    val config = scenario.root.resolve(".textus/config.json")
    Files.writeString(config, raw.mapObject(_.remove("sm-workflow.repository-sync.dirty-purposes")).noSpaces)
    configure(scenario, Map("textus.subsystem.process-execution.plan" -> plan.noSpaces,
      "sm-workflow.repository-sync.program-inputs" -> impact.asJson.noSpaces))
  }

  private def _target(result: F.Result): A.Target = take(R.anyTargetC(
    successful(result).business[V.RepositorySyncValidationContinuationDescriptionResult].target)) match {
    case R.PurposeAdditionalTestOnlyTarget(value) => value
    case _ => fail("Expected the additional Purpose TestOnly target")
  }

  private def _direction(scenario: F.Scenario, session: String, described: F.Result, choice: String):
      V.RepositorySyncValidationContinuationAdoptionInput = {
    val target = _target(described)
    val reference = V.WorkflowArtifactReference("fixture-additional-test-only-" + choice, "1")
    val answer = V.RepositorySyncValidationContinuationAnswer(choice, "Retain the full Test and run the declared behavioral suite",
      false, false)
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some(target.context.participantIdentity), None, None)
    val preparation = V.WorkflowEvidencePreparationInput("RepositorySync", "validation-continuation",
      described.invocation.stdout.toUri.toASCIIString, save(scenario.root, choice + "-answer.json", json(answer)),
      save(scenario.root, choice + "-reference.json", json(reference)), save(scenario.root, choice + "-execution.json", json(execution)),
      "normal-development-host", java.time.Instant.now().toString, "entity:smworkflow.cli.host_evidence",
      Some(target.context.participantIdentity))
    val evidence = successful(scenario.prepareHostInput("prepareEvidence",
      take(new org.goldenport.record.io.RecordDecoder().json(json(preparation)))))
    scenario.selectPreparedImport(evidence)
    V.RepositorySyncValidationContinuationAdoptionInput(session,
      described.business[V.RepositorySyncValidationContinuationDescriptionResult].target, choice, answer.rationale,
      reference, false, false, execution)
  }

  private def _capture(): (F.Scenario, String, F.Result) = {
    val scenario = fresh(_route, checked = true)
    val descriptor = scenario.root.resolve(".textus/assembly.yaml")
    Files.writeString(descriptor, Files.readString(descriptor).replace("      id: participant\n", "      id: sm-workflow-local\n"))
    val repository = Path.of(scenario.host.target.root)
    val build = repository.resolve("build.sbt")
    val overrideLine = "Test / test := (Test / runMain).toTask(\" ProductCheck\").value\n"
    val original = Files.readString(build)
    original should include(overrideLine)
    Files.writeString(build, original.replace(overrideLine, "") +
      "\nlibraryDependencies += \"org.scalatest\" %% \"scalatest\" % \"3.2.19\" % Test\n")
    val tests = repository.resolve("src/test/scala/fixture")
    Files.createDirectories(tests)
    Files.writeString(tests.resolve("SelectedInputSpec.scala"), """package fixture
      |class SelectedInputSpec extends org.scalatest.funsuite.AnyFunSuite {
      |  test("the actual selected candidate has the expected input") {
      |    assert(java.nio.file.Files.readString(java.nio.file.Path.of("value.txt")) == "expected\n")
      |  }
      |}
      |class OptionalSpec extends org.scalatest.funsuite.AnyFunSuite {
      |  test("an optional integration environment is absent") { cancel("fixture optional environment") }
      |}
      |""".stripMargin)
    git(scenario.root, "-C", repository.toString, "add", "--", "build.sbt", "src/test/scala/fixture/SelectedInputSpec.scala")
    git(scenario.root, "-C", repository.toString, "commit", "-m", "Add actual selected and canceled fixture tests")
    git(scenario.root, "-C", repository.toString, "push", scenario.host.target.endpoint, "HEAD:refs/heads/main")
    _configure(scenario, append = false)
    Files.writeString(repository.resolve("value.txt"), "expected\n")
    val session = start(scenario)
    successful(advance(scenario, session)).business[V.RepositorySyncAdvanceResult].submitOperation shouldBe Some("submitRepositoryPurposeResult")
    val request = purpose_inputs(scenario).head
    val result = P.Result("resolved", "One actual code Purpose", Vector(P.Group("selected-input", "Correct the product input",
      "fix: correct selected input", Vector.empty, Vector(P.Content("value.txt", Some("expected\n"))), Vector.empty,
      "The selected test reads the corrected product input")), request.files.map(_.path).filterNot(_ == "value.txt"))
    successful(submit_purpose(scenario, session, result)).business[V.RepositorySyncConflictSubmissionResult].accepted shouldBe true
    val stopped = advance(scenario, session)
    stopped.exitCode should not be 0
    stopped.invocation.logs should include("Purpose code assessment requires compilation")
    val originalchecks = formalReceipts(scenario, session)
    originalchecks.map(_._2.identity) shouldBe originalChecks.map(_.identity)
    val full = originalchecks.last._2.processObservation.get
    val report = SbtTestReassessment.assess(Vector.empty, full).observation
    originalchecks.last._2.exitCode shouldBe Some(0)
    report.succeeded shouldBe Some(1L)
    report.canceled shouldBe Some(1L)
    (scenario, session, stopped)
  }

  private def _review(scenario: F.Scenario, session: String, issued: F.Result): Unit = {
    val reference = V.WorkflowArtifactReference("fixture-independent-additional-test-only-review", "1")
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("fixture-independent-reviewer"), None, None)
    val result = V.RepositorySyncNativeMergeReviewResult("accept",
      "The isolated fixture authorizes the checked input change using its actual issued review evidence",
      Vector.empty, None)
    val request = issued.business[V.RepositorySyncAdvanceResult].retainedWork.map(_.resource)
      .getOrElse(issued.invocation.stdout.toUri.toASCIIString)
    val evidence = V.WorkflowEvidencePreparationInput("RepositorySync", "review", request,
      save(scenario.root, "review-answer.json", json(result)), save(scenario.root, "review-reference.json", json(reference)),
      save(scenario.root, "review-execution.json", json(execution)), "normal-development-host", java.time.Instant.now().toString,
      "entity:smworkflow.cli.host_evidence", None)
    val prepared = successful(scenario.prepareHostInput("prepareEvidence",
      take(new org.goldenport.record.io.RecordDecoder().json(json(evidence)))))
    scenario.selectPreparedImport(prepared)
    successful(scenario.repositoryCall("submitNativeMergeReview", Vector("--sessionId", session, "--result", json(result),
      "--resultReference", json(reference), "--executionEvidence", json(execution))))
      .business[V.RepositorySyncNativeMergeReviewSubmissionResult].accepted shouldBe true
  }

  "Additional Purpose TestOnly on the installed CLI" should {
    if (_mode == "before") {
      "capture the old materialized frontier after real Compile and Test with cancellation" in {
        Given("the old installed CLI and an isolated candidate with a real optional test")
        installed()
        val (scenario, session, stopped) = _capture()
        Then("the original receipts remain at the old assessment stop")
        describe(scenario, session).exitCode should not be 0
        retain_episode(_kind, _route, scenario, session, stopped.invocation.stdout)
      }
    } else {
      "run only the newly admitted fixed check and finish the same candidate through review and commit" in {
        Given("the same installed-command Session and original retained candidate and typed results")
        installed()
        val (scenario, session) = if (_mode == "after") {
          val (reopened, sessionid, _) = reopen_episode(_kind, _route)
          reopened -> sessionid
        } else {
          val (created, sessionid, _) = _capture()
          created -> sessionid
        }
        val original = current(scenario, session)
        val checks = formalReceipts(scenario, session)
        _configure(scenario, append = true, fixed = false)
        describe(scenario, session).exitCode should not be 0
        current(scenario, session) shouldBe original
        _configure(scenario, append = true)
        When("the participant adopts the exact fixed check reported by ordinary describe")
        val described = describe(scenario, session)
        val target = _target(described)
        target.testClasses shouldBe Vector(selectedClass)
        target.previousChecks shouldBe originalChecks
        target.check shouldBe appendedCheck
        val defer = _direction(scenario, session, described, "Defer")
        successful(adopt(scenario, defer)).business[V.RepositorySyncValidationContinuationAdoptionResult].outcome shouldBe "deferred"
        current(scenario, session) shouldBe original
        val input = _direction(scenario, session, described, A.authorize)
        val foreign = Vector(target.copy(commit = "a" * 40), target.copy(workspace = "foreign"),
          target.copy(frontierRevision = "1"), target.copy(program = "foreign"))
        foreign.foreach { value =>
          adopt(scenario, input.copy(target = GoalPhaseNativeMetadata.record(value.asJson))).exitCode should not be 0
        }
        _configure(scenario, append = true, changedProgram = true)
        adopt(scenario, input).exitCode should not be 0
        current(scenario, session) shouldBe original
        _configure(scenario, append = true)
        successful(adopt(scenario, input)).business[V.RepositorySyncValidationContinuationAdoptionResult].outcome shouldBe "adopted"
        val admitted = current(scenario, session)
        successful(adopt(scenario, input)).business[V.RepositorySyncValidationContinuationAdoptionResult].outcome shouldBe "already-adopted"
        current(scenario, session) shouldBe admitted
        admitted.revision shouldBe original.revision.next
        formalReceipts(scenario, session) shouldBe checks
        Then("separate Advance runs only TestOnly and uses the original assessment and review policy")
        val issued = successful(advance(scenario, session))
        val progression = issued.business[V.RepositorySyncAdvanceResult]
        val assessment = receipts(scenario, session).flatMap(_.payload.value.purposes.flatMap(_.assessment)).head
        assessment.checks shouldBe (originalChecks :+ appendedCheck).map(_.identity)
        val result = formalReceipts(scenario, session)
        result.take(2) shouldBe checks
        result.size shouldBe 3
        val facts = result.last._2.sbt.get
        facts.selection.get.testClasses shouldBe target.testClasses
        facts.selection.get.testsRun shouldBe 1L
        adopt(scenario, input).exitCode should not be 0
        formalReceipts(scenario, session) shouldBe result
        if (assessment.branch == "detailed-review") {
          progression.submitOperation shouldBe Some("submitNativeMergeReview")
          _review(scenario, session, issued)
        } else assessment.policy.detailedReview shouldBe "conditional"
        if (current(scenario, session).lifecycle != Lifecycle.Completed) successful(advance(scenario, session))
        current(scenario, session).lifecycle shouldBe Lifecycle.Completed
        current(scenario, session).history.take(original.history.size) shouldBe original.history
        git(scenario.root, "-C", scenario.host.target.root, "rev-parse", "HEAD") shouldBe target.commit
        formalReceipts(scenario, session) shouldBe result
      }
    }
  }
}
