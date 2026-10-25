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

/** Q4 public native CLI integration on actual isolated normal hosts. Each call
  * is a separate process with retained argv, PID, exit and logs. Test-owned
  * Start consent, semantic answers and effect authority do not qualify an
  * external AI/Human provider or a newly published installed generation.
  * @since   Oct. 24, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class Phase65WorkflowConsumerCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def runs(root: Path): Int = Files.readAllLines(root.resolve("sbt-executions.txt")).size
  private def read(client: F.ProducerClient, state: V.JournalRecordingState): V.JournalEntry = {
    val ref = state.entry.get
    val result = client.call("JournalService", "readJournalEntry", Vector("--input",
      json(V.JournalReadInput(ref.identity, Some(ref.revision))))).business[V.JournalReadResult]
    result.status shouldBe "found"
    result.entry.get
  }

  "Q4 three-workflow native client integration" should {
    "continue a normal Goal code Step through checks, Journal, native commit and stable next work with no Step AI assignment" in {
      Given("a new isolated normal public Goal Start with a selected code assessment and planned later obligation")
      val s = F.freshStepAssessment("p65-integrated-goal", TypedSbtHostPlanFixture.normalExecutable)
      val task = s.ownTask("integrated-client-task")
      val started = s.start().business[V.GoalPhaseStartResult]
      s.retain(task, started)
      val before = s.candidateHead
      val issued = s.advance(task).business[V.GoalPhaseAdvanceResult]
      issued.work.get.kind shouldBe "ImplementStep"
      val repo = Path.of(s.host.target.root)
      Files.writeString(repo.resolve("src/main/scala/org/simplemodeling/textus/smworkflow/p62authored/LineWriter.scala"),
        "package org.simplemodeling.textus.smworkflow.p62authored\nobject LineWriter { def render(v: String): String = v.reverse + \"\\n\" }\n")
      val test = repo.resolve(F.authoringPaths.head)
      Files.createDirectories(test.getParent)
      Files.writeString(test, """package org.simplemodeling.textus.smworkflow.p62authored
        |import org.scalatest.wordspec.AnyWordSpec
        |import org.scalatest.matchers.should.Matchers
        |final class LineWriterSpec extends AnyWordSpec with Matchers {
        |  "LineWriter" should {
        |    "reverse the selected line" in { LineWriter.render("abc") shouldBe "cba\n" }
        |    "preserve an empty line" in { LineWriter.render("") shouldBe "\n" }
        |  }
        |}
        |""".stripMargin)
      val implementation = V.GoalPhaseWorkResult("completed", "Specification-owned bounded code and behavior", Vector.empty,
        None, None, None, None, None, None, None, None, dependencyValidation = None)
      s.call("command", issued.submitOperation.get, Vector("--sessionId", issued.submitSessionId.get,
        "--result", json(implementation), "--resultReference", json(V.WorkflowArtifactReference("p65-q4-goal-answer", "1"))))
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      When("a new CLI process executes ordinary Advance")
      val closingCall = s.advance(task)
      val closing = closingCall.business[V.GoalPhaseAdvanceResult]
      closing.kind shouldBe "Wait"
      closing.work shouldBe None
      closing.authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
      closing.validation.get.outcome shouldBe Some("passed")
      closing.validation.get.checks.last.sbt.get.selection.get.testsRun shouldBe 2L
      runs(s.root) shouldBe 2
      val observation = closing.validation.get.assessmentJournal.get
      observation.status shouldBe "recorded"
      val ref = observation.entry.get
      val entry = s.readJournalEntry(V.JournalReadInput(ref.identity, Some(ref.revision))).business[V.JournalReadResult]
      entry.status shouldBe "found"
      entry.entry.get.kind shouldBe "StepAssessment"
      take(entry.entry.get.assessment.get.toJsonStringC) should include("later-integration")
      val reference = s.root.resolve("q4-closure-reference.json")
      Files.writeString(reference, json(V.WorkflowArtifactReference("p65-q4-goal-closure", "1")))
      val authority = V.WorkflowAuthorityPreparationInput(closingCall.invocation.stdout.toUri.toASCIIString,
        reference.toUri.toASCIIString, "participant", "normal-development-host", "2026-10-07T00:00:00Z",
        "entity:smworkflow.cli.host_evidence")
      val prepared = s.prepareHostInput("prepareAuthority", authority.toRecord())
      withClue(prepared.invocation.logs) { prepared.exitCode shouldBe 0 }
      s.selectPreparedImport(prepared)
      val next = s.advance(task).business[V.GoalPhaseAdvanceResult]
      Then("the actual accepted Step commit preserves the deferred Journal and successful checks")
      val completed = next.work.get.completed.head
      completed.review shouldBe None
      completed.admission.nonEmpty shouldBe true
      completed.commit.parent shouldBe before
      completed.commit.identity shouldBe s.candidateHead
      val pending = s.advance(task).business[V.GoalPhaseAdvanceResult]
      pending.work shouldBe next.work
      pending.issuedWorkContext shouldBe next.issuedWorkContext
      s.candidateHead shouldBe completed.commit.identity
      s.candidateIndex shouldBe ""
      runs(s.root) shouldBe 2
    }

    "handoff a real diverged Repository Start to public CLI admission, native merge and retained convergence" in {
      Given("normal Start, explicit Journal grants and a real disjoint diverged repository before any validation")
      val (root, id) = new Phase65RepositoryAdmissionSpec().prepareClientScenario()
      val client = new F.ProducerClient(root)
      def advance() = client.call("RepositorySyncService", "advanceRepositorySync", Vector("--sessionId", id))
        .business[V.RepositorySyncAdvanceResult]
      When("a separate CLI process owns the actual validation and merge")
      val done = advance()
      Then("it converges without an issued semantic assignment and a common Journal entry is readable")
      done.kind shouldBe "Terminal"
      done.issuedWorkContext shouldBe None
      done.review shouldBe None
      done.convergence.nonEmpty shouldBe true
      done.journalRecording.get.status shouldBe "recorded"
      read(client, done.journalRecording.get).kind shouldBe "MergeAssessment"
      runs(root) shouldBe 1
      val git = new ProcessBuilder("/usr/bin/git", "-C", root.resolve("repository").toString,
        "show", "--format=%H %P", "--no-patch", "HEAD").redirectErrorStream(true).start()
      val actual = new String(git.getInputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim
      git.waitFor() shouldBe 0
      actual.split(" ").length shouldBe 3
      val again = advance()
      again.convergence shouldBe done.convergence
      again.issuedWorkContext shouldBe None
      again.journalRecording.get.status shouldBe "alreadyRecorded"
      again.journalRecording.get.entry shouldBe done.journalRecording.get.entry
      runs(root) shouldBe 1
    }

    "continue known Split inputs with no semantic work and read the retained result from another CLI process" in {
      Given("a normal Split Start with complete known input and explicit normal Journal grants")
      val (root, id) = new Phase65SplitReuseSpec().prepareClientScenario(unknown = false)
      val client = new F.ProducerClient(root)
      def advance() = client.call("SplitPhaseService", "advanceSplitPhase", Vector("--sessionId", id))
        .business[V.SplitPhaseAdvanceResult]
      When("the native client advances then reopens the completed proposal")
      val done = advance()
      done.kind shouldBe "Terminal"
      done.issuedWorkContext shouldBe None
      done.submitOperation shouldBe None
      done.journalRecording.get.status shouldBe "recorded"
      read(client, done.journalRecording.get).kind shouldBe "SplitRouting"
      val again = advance()
      Then("the same proposal and Journal source are reused without another semantic request")
      again.selectedPlan shouldBe done.selectedPlan
      again.issuedWorkContext shouldBe None
      again.journalRecording.get.status shouldBe "alreadyRecorded"
      again.journalRecording.get.entry shouldBe done.journalRecording.get.entry
    }

    "submit only the public unknown Split inputs once and reuse the actual saved answer through proposal admission" in {
      Given("a normal Split Start with one unknown item and one independent boundary")
      val (root, id) = new Phase65SplitReuseSpec().prepareClientScenario(unknown = true)
      val client = new F.ProducerClient(root)
      def advance() = client.call("SplitPhaseService", "advanceSplitPhase", Vector("--sessionId", id))
        .business[V.SplitPhaseAdvanceResult]
      val issued = advance()
      issued.kind shouldBe "Request"
      val partition = issued.partition.get
      partition.items.map(_.identity.value) shouldBe Vector("item-1")
      partition.boundaries.map(_.edge.afterItem.value) shouldBe Vector("item-0")
      val pending = advance()
      pending.partition shouldBe issued.partition
      pending.issuedWorkContext shouldBe issued.issuedWorkContext
      val answer = V.SplitPhasePartitionSemanticResult("Enriched", "Specification-owned independent bounded closures",
        partition.items.map(i => V.SplitPhasePartitionNovelEstimate(i.identity, 120, 180, "bounded work", "novel closure")),
        partition.boundaries.map(b => V.SplitPhasePartitionBoundaryJudgment(b.edge.afterItem, "Either", "independent closure")),
        Vector(V.SplitPhasePartitionEvidenceReference("p65-q4-specification-answer", "1")))
      When("the client submits that one test-owned answer through the declared operation")
      client.call("SplitPhaseService", issued.submitOperation.get, Vector("--sessionId", issued.submitSessionId.get,
        "--result", json(answer), "--completionFacts", "[]", "--executionEvidence",
        "{\"references\":[],\"workerIdentity\":\"participant\"}"))
        .business[V.SplitPhaseSemanticSubmissionResult].accepted shouldBe true
      val done = advance()
      Then("normal runtime partitioning admits a proposal without a mandatory second semantic review")
      done.kind shouldBe "Terminal"
      done.issuedWorkContext shouldBe None
      done.selectedPlan.get.children.flatMap(_.orderedWorkItems.map(_.identity)) shouldBe Vector("item-0", "item-1", "item-2")
      val again = advance()
      again.selectedPlan shouldBe done.selectedPlan
      again.issuedWorkContext shouldBe None
      again.journalRecording.get.status shouldBe "alreadyRecorded"
      read(client, again.journalRecording.get).kind shouldBe "SplitRouting"
    }
  }
}
