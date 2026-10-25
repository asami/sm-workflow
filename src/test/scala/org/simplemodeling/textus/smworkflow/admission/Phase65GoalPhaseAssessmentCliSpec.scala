package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import org.goldenport.record.RecordPresentable
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import SplitPhaseExplicitPartitionFixture.take

/** Actual public CLI, SBT and Git in a fresh isolated repository. Semantic input
  * and authority are explicitly specification-owned; no real Human or AI proof.
  * @since   Oct. 23, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class Phase65GoalPhaseAssessmentCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def file(s: F.Scenario, name: String, value: RecordPresentable): String = {
    val p = s.root.resolve(name + ".json")
    Files.writeString(p, json(value))
    p.toUri.toASCIIString
  }
  private def executions(s: F.Scenario): Vector[String] = Files.readAllLines(s.root.resolve("sbt-executions.txt")).asScala.toVector

  "P65-Q1 actual local code admission" should {
    "commit with zero Step AI requests and retain a readable assessment through normal continuation" in {
      Given("a fresh isolated code Step with selected conditional policy and normal typed Compile TestOnly and Git programs")
      val scenario = F.freshStepAssessment("p65-code-assessment", TypedSbtHostPlanFixture.normalExecutable)
      val task = scenario.ownTask("code-assessment-task")
      val started = scenario.start().business[V.GoalPhaseStartResult]
      scenario.retain(task, started)
      val originalHead = scenario.candidateHead
      val request = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      request.kind shouldBe "Request"
      request.work.get.kind shouldBe "ImplementStep"
      request.work.get.stepIdentity shouldBe Some("step-1")
      val repo = Path.of(scenario.host.target.root)
      Files.writeString(repo.resolve("src/main/scala/org/simplemodeling/textus/smworkflow/p62authored/LineWriter.scala"),
        "package org.simplemodeling.textus.smworkflow.p62authored\n" +
          "object LineWriter { def render(value: String): String = value.reverse + \"\\n\" }\n")
      val test = repo.resolve(F.authoringPaths.head)
      Files.createDirectories(test.getParent)
      Files.writeString(test, """package org.simplemodeling.textus.smworkflow.p62authored
        |import org.scalatest.wordspec.AnyWordSpec
        |import org.scalatest.matchers.should.Matchers
        |final class LineWriterSpec extends AnyWordSpec with Matchers {
        |  "LineWriter" should {
        |    "reverse text before writing one line" in { LineWriter.render("abc") shouldBe "cba\n" }
        |    "preserve an empty line" in { LineWriter.render("") shouldBe "\n" }
        |  }
        |}
        |""".stripMargin)
      val semantic = V.GoalPhaseWorkResult("completed", "Specification-owned local code and behavioral tests",
        Vector.empty, None, None, None, None, None, None, None, None, dependencyValidation = None)
      val reference = V.WorkflowArtifactReference("p65-assessment-implementation", "1")
      scenario.call("command", request.submitOperation.get, Vector("--sessionId", request.submitSessionId.get,
        "--result", json(semantic), "--resultReference", json(reference)))
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true

      When("ordinary Advance executes actual selected checks and applies deterministic admission")
      val closingCall = scenario.advance(task)
      val closing = closingCall.business[V.GoalPhaseAdvanceResult]
      Then("the Step reaches ordinary authority Wait without an AI triage review or waiver request")
      closing.kind shouldBe "Wait"
      closing.authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
      closing.work shouldBe None
      closing.validation.get.outcome shouldBe Some("passed")
      closing.validation.get.assessment.nonEmpty shouldBe true
      closing.validation.get.checks.map(_.sbt.get.kind) shouldBe Vector("compile", "test-only")
      closing.validation.get.checks.last.sbt.get.selection.get.testsRun shouldBe 2L
      executions(scenario).size shouldBe 2
      closing.validation.get.assessmentJournal.get.status shouldBe "recorded"
      val entry = closing.validation.get.assessmentJournal.get.entry.get
      val read = scenario.readJournalEntry(V.JournalReadInput(entry.identity, Some(entry.revision)))
        .business[V.JournalReadResult]
      read.status shouldBe "found"
      read.entry.get.kind shouldBe "StepAssessment"
      read.entry.get.assessment shouldBe closing.validation.get.assessment
      take(read.entry.get.assessment.get.toJsonStringC) should include("Confirm the planned later integration at Release")
      take(read.entry.get.assessment.get.toJsonStringC) should include("release")
      take(read.entry.get.assessment.get.toJsonStringC) should include("step-2")
      val found = scenario.findJournalEntries(V.JournalFindInput(scenario.plan.repository, Some(scenario.plan.phase),
        Some(started.sessionId), Some("StepAssessment"), None, None, None, Some(20))).business[V.JournalFindResult]
      found.status shouldBe "found"
      found.entries.map(_.entry.identity) shouldBe Vector(entry.identity)

      Given("test-owned independent effect authority prepared through the actual public operation")
      val authority = V.WorkflowAuthorityPreparationInput(closingCall.invocation.stdout.toUri.toASCIIString,
        file(scenario, "closure-reference", V.WorkflowArtifactReference("p65-assessment-closure", "1")),
        "participant", "normal-development-host", "2026-10-07T00:00:00Z", "entity:smworkflow.cli.host_evidence")
      val prepared = scenario.prepareHostInput("prepareAuthority", authority.toRecord())
      withClue(prepared.invocation.logs) { prepared.exitCode shouldBe 0 }
      scenario.selectPreparedImport(prepared)
      When("a separate ordinary Advance performs the native selected commit and continues")
      val next = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      Then("the next implementation carries the actual commit and admission without rerunning successful checks")
      next.kind shouldBe "Request"
      next.work.get.kind shouldBe "ImplementStep"
      next.work.get.stepIdentity shouldBe Some("step-2")
      next.work.get.completed.size shouldBe 1
      val closed = next.work.get.completed.head
      closed.review shouldBe None
      closed.admission.nonEmpty shouldBe true
      closed.commit.identity shouldBe scenario.candidateHead
      closed.commit.parent shouldBe originalHead
      scenario.candidateParent shouldBe originalHead
      scenario.candidateIndex shouldBe ""
      executions(scenario).size shouldBe 2
      val pending = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      pending.kind shouldBe "Request"
      pending.work shouldBe next.work
      pending.issuedWorkContext shouldBe next.issuedWorkContext
      pending.repairProgress shouldBe next.repairProgress
      scenario.candidateHead shouldBe closed.commit.identity
      scenario.candidateIndex shouldBe ""
      executions(scenario).size shouldBe 2
    }
  }
}
