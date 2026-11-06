package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.file.{Files, Path, StandardOpenOption}
import java.time.Instant
import org.goldenport.record.RecordPresentable
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import GoalPhaseOrderedProgression as O
import IncrementalSbtDiagnosisRepair as D
import D.given
import SplitPhaseExplicitPartitionFixture.take

/** Normal installed public operations on one fresh isolated repair lifecycle.
  * Reviews and authority are SPECIFICATION-OWNED fixture inputs. No production
  * Session or native row is edited and no source-classpath CLI is substituted.
  * @since   Oct. 30, 2026
  * @version Oct. 30, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalPostRepairDescendantCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _required(name: String): Path = {
    val path = Path.of(sys.props.getOrElse(name, throw new IllegalArgumentException("Missing " + name)))
    require(path.isAbsolute && path.normalize == path, name + " must be absolute and normalized")
    path.toRealPath()
  }
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def _save(s: F.Scenario, name: String, value: RecordPresentable): String = {
    val path = s.root.resolve(name + ".json")
    Files.writeString(path, _json(value), StandardOpenOption.CREATE_NEW)
    path.toUri.toASCIIString
  }
  private def _result(outcome: String, issues: Option[V.GoalPhaseIssueReport] = None,
    manifest: Option[D.Manifest] = None): V.GoalPhaseWorkResult = V.GoalPhaseWorkResult(outcome,
    "SPECIFICATION-OWNED assigned source correction and independent review", Vector.empty, None, None, issues,
    None, None, None, None, None, dependencyValidation = None,
    diagnosticRepair = manifest.map(value => GoalPhaseNativeMetadata.record(value.asJson)))
  private final class Cli(s: F.Scenario, executable: Path, image: Path) {
    def call(service: String, operation: String, parameters: Vector[String]): F.Result =
      s.callInstalled(executable, service, operation, parameters, Some(image), Some(s.root.resolve(".textus/assembly.yaml")))
    def goal(operation: String, parameters: Vector[String]): F.Result = call("GoalPhaseService", operation, parameters)
    def advance(session: String): F.Result = goal("advanceGoalPhase", Vector("--sessionId", session))
    def prepare(operation: String, value: RecordPresentable): Unit = {
      val response = call("NativeWorkflowPreparationService", operation, Vector("--input", _json(value)))
      withClue(response.invocation.logs) { response.exitCode shouldBe 0 }
      s.selectPreparedImport(response)
    }
    def submit(request: V.GoalPhaseAdvanceResult, result: V.GoalPhaseWorkResult, name: String,
      execution: Option[V.WorkflowExecutionEvidence] = None): Unit = {
      val response = goal(request.submitOperation.get, Vector("--sessionId", request.submitSessionId.get,
        "--result", _json(result), "--resultReference", _json(V.WorkflowArtifactReference(name, "1"))) ++
        execution.toVector.flatMap(value => Vector("--executionEvidence", _json(value))))
      response.business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
    }
    def review(actual: F.Result, name: String): Unit = {
      val work = actual.business[V.GoalPhaseAdvanceResult]
      val unresolved = work.work.get.issues.toVector.flatMap(_.issues).filter(_.status != "RESOLVED")
      val issues = Option.when(unresolved.nonEmpty)(V.GoalPhaseIssueReport(unresolved.map(issue =>
        V.GoalPhaseIssueDisposition(issue.identity, "RESOLVED", "Independent fixture review confirms the assigned correction", None))))
      val result = _result("accept", issues)
      val reference = V.WorkflowArtifactReference(name, "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("specification-independent-reviewer"), None, None)
      prepare("prepareEvidence", V.WorkflowEvidencePreparationInput("GoalPhase", "review", actual.invocation.stdout.toUri.toASCIIString,
        _save(s, name + "-result", result), _save(s, name + "-reference", reference), _save(s, name + "-execution", execution),
        "normal-development-host", Instant.now().toString, "entity:smworkflow.cli.host_evidence", None))
      submit(work, result, name, Some(execution))
    }
    def authority(actual: F.Result, name: String): Unit = prepare("prepareAuthority", V.WorkflowAuthorityPreparationInput(
      actual.invocation.stdout.toUri.toASCIIString, _save(s, name, V.WorkflowArtifactReference(name, "1")),
      "participant", "normal-development-host", Instant.now().toString, "entity:smworkflow.cli.host_evidence"))
  }

  "Installed post-repair descendant continuation" should {
    "describe an unstarted frontier and run fresh Phase checks review and normal Final from the actual descendant" in {
      Given("one isolated normal installed host with actual Compile and a failing actual Phase Test")
      val executable = _required("smworkflow.spec.installedCommand")
      val image = _required("smworkflow.spec.installations")
      val s = F.freshReleaseHistoryRecovery("p99-post-repair-descendant", TypedSbtHostPlanFixture.normalExecutable)
      F.allowReleaseHistoryPendingIndex(s)
      s.selectCompletionPolicy(GoalPhasePostCompletionSync.localPolicy)
      val cli = new Cli(s, executable, image)
      val start = cli.goal("startGoalPhase", Vector("--input", s.input.noSpaces)).business[V.GoalPhaseStartResult]
      val implementation = cli.advance(start.sessionId).business[V.GoalPhaseAdvanceResult]
      val repository = Path.of(s.host.target.root)
      val source = repository.resolve("src/main/scala/HistoryExample.scala")
      Files.writeString(source, "object HistoryExample { val value: Int = 1 }\n")
      cli.submit(implementation, _result("completed"), "descendant-implementation")
      val step = cli.advance(start.sessionId)
      step.business[V.GoalPhaseAdvanceResult].work.get.kind shouldBe "ReviewStep"
      cli.review(step, "descendant-step-review")
      val stepwait = cli.advance(start.sessionId)
      cli.authority(stepwait, "descendant-step-authority")
      val repair = cli.advance(start.sessionId).business[V.GoalPhaseAdvanceResult]
      repair.work.get.kind shouldBe "RepairStep"
      repair.work.get.stepIdentity shouldBe None
      val completed = repair.work.get.completed
      completed.size shouldBe 1
      val acceptedstep = completed.head.commit.identity
      val failed = repair.work.get.checkFailure.get.validation
      failed.outcome shouldBe Some("failed")
      val receipt = failed.checks.find(_.exitCode.exists(_ != 0)).get
      val manifest = D.Manifest(D.schema, "descendant-actual-phase-repair", failed.sourceRevision,
        O.Snapshot(failed.candidate.head, failed.candidate.tree), "The actual ProductCheck requires value 42",
        Vector(D.Locator(receipt.identity, if (receipt.stdout.nonEmpty) "stdout" else "stderr", 1, 1,
          "Actual failed Phase Test retained by the runtime")), Vector.empty,
        Vector("src/main/scala/HistoryExample.scala"), Vector("Set the assigned source value to 42"),
        Vector.empty, Vector("test"), Vector("Fresh Phase Test is required"))
      val open = repair.work.get.issues.get.issues.filter(_.status != "RESOLVED")
      open should not be empty
      val fixed = V.GoalPhaseIssueReport(open.map(issue => V.GoalPhaseIssueDisposition(issue.identity, "FIXED",
        "Assigned source now returns 42", None)))
      Files.writeString(source, "object HistoryExample { val value: Int = 42 }\n")
      cli.submit(repair, _result("completed", Some(fixed), Some(manifest)), "descendant-accepted-release-repair")

      When("a separate owner commits another file after the accepted repair and public description observes the unchanged Session")
      val descendant = s.commitPostRepairDescendant()
      descendant should not be acceptedstep
      s.candidateParent shouldBe acceptedstep
      val external = Files.readString(repository.resolve("independent-owner.txt"))
      val checksbefore = Files.readString(s.root.resolve("sbt-executions.txt"))
      val description = cli.goal(GoalPhaseWholePhaseCandidateRecovery.describeOperation, Vector("--sessionId", start.sessionId))
        .business[V.GoalPhaseWholePhaseCandidateRecoveryDescriptionResult]
      description.outcome shouldBe "ready"
      description.choices shouldBe empty
      description.recoveryOperation shouldBe "advanceGoalPhase"
      val target = GoalPhaseNativeMetadata.normalizePublicJson(parse(take(description.target.toJsonStringC)).toOption.get)
        .fold(error => fail(error), identity)
      target.hcursor.get[String]("schemaVersion") shouldBe Right("sm-workflow.goal-phase-whole-phase-ready.v1")
      target.hcursor.downField("repository").get[String]("head") shouldBe Right(descendant)
      target.hcursor.downField("controller").downField("tip").get[String]("head") shouldBe Right(acceptedstep)
      Files.readString(s.root.resolve("sbt-executions.txt")) shouldBe checksbefore
      s.candidateHead shouldBe descendant
      s.candidateIndex shouldBe ""

      val phasecall = cli.advance(start.sessionId)
      val phase = phasecall.business[V.GoalPhaseAdvanceResult]
      Then("fresh checks and independent integrated review concern the actual tree while Step evidence excludes imported work")
      phase.work.get.kind shouldBe "ReviewPhase"
      phase.work.get.completed shouldBe completed
      phase.work.get.originalBase shouldBe repair.work.get.originalBase
      phase.work.get.selectedPlan shouldBe repair.work.get.selectedPlan
      phase.repairProgress.get.automaticCount shouldBe repair.repairProgress.get.automaticCount
      phase.repairProgress.get.ordinaryCount shouldBe repair.repairProgress.get.ordinaryCount
      phase.repairProgress.get.limit shouldBe repair.repairProgress.get.limit
      phase.work.get.candidate.get.head shouldBe descendant
      phase.work.get.review.get.checks.forall(_.exitCode.contains(0)) shouldBe true
      phase.work.get.review.get.diff should not include "Independent owner's committed work"
      phase.work.get.review.get.changedPaths should not contain "independent-owner.txt"
      Files.readString(s.root.resolve("sbt-executions.txt")).linesIterator.size shouldBe checksbefore.linesIterator.size + 1
      cli.review(phasecall, "descendant-fresh-phase-review")
      val finalwait = cli.advance(start.sessionId)
      val finalview = finalwait.business[V.GoalPhaseAdvanceResult]
      finalview.kind shouldBe "Wait"
      finalview.repairProgress.get.automaticCount shouldBe phase.repairProgress.get.automaticCount
      finalview.repairProgress.get.ordinaryCount shouldBe phase.repairProgress.get.ordinaryCount
      cli.authority(finalwait, "descendant-final-authority")
      val terminal = cli.advance(start.sessionId).business[V.GoalPhaseAdvanceResult]
      terminal.kind shouldBe "Terminal"
      terminal.outcome shouldBe Some("completed")
      terminal.completion.get.completed shouldBe completed
      terminal.completion.get.originalBase shouldBe repair.work.get.originalBase
      s.candidateParent shouldBe descendant
      s.candidateIndex shouldBe ""
      Files.readString(repository.resolve("independent-owner.txt")) shouldBe external
      Files.readString(s.root.resolve("sbt-executions.txt")).linesIterator.size shouldBe checksbefore.linesIterator.size + 1
      Files.writeString(s.root.resolve("post-repair-descendant-result.json"), Json.obj(
        "sessionId" -> start.sessionId.asJson, "acceptedStep" -> acceptedstep.asJson,
        "descendant" -> descendant.asJson, "releaseCommit" -> s.candidateHead.asJson,
        "description" -> parse(_json(description)).toOption.get,
        "terminal" -> parse(_json(terminal)).toOption.get).noSpaces, StandardOpenOption.CREATE_NEW)
    }
  }
}
