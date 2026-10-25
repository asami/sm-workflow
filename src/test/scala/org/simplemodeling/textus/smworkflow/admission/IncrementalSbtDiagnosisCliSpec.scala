package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import org.goldenport.record.{Record, RecordPresentable}
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import IncrementalSbtDiagnosisRepair as D
import D.given
import SplitPhaseExplicitPartitionFixture.take

/** Real normal CLI and typed SBT in isolated repositories. Semantic judgments
  * and effect authority are specification-owned, never live Human/review proof.
  * @since   Oct. 26, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class IncrementalSbtDiagnosisCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val library = "src/main/scala/org/simplemodeling/textus/smworkflow/p62authored/LineWriter.scala"
  private val behavior = F.authoringPaths.head
  private def json(v: RecordPresentable): String = take(v.toRecord().toJsonStringC)
  private def record(v: Record): Json = take(GoalPhaseNativeMetadata.normalizePublicJson(parse(take(v.toJsonStringC)).toOption.get)
    .fold(org.goldenport.Consequence.stateConflict(_), org.goldenport.Consequence.success))
  private def executions(s: F.Scenario): Vector[String] = {
    val p = s.root.resolve("sbt-executions.txt")
    if (Files.exists(p)) Files.readAllLines(p).asScala.toVector else Vector.empty
  }
  private def source(s: F.Scenario, text: String): Unit = Files.writeString(Path.of(s.host.target.root).resolve(library),
    "package org.simplemodeling.textus.smworkflow.p62authored\n" +
      "object LineWriter { def render(value: String): String = " + text + " }\n")
  private def tests(s: F.Scenario, broken: Boolean): Unit = {
    val p = Path.of(s.host.target.root).resolve(behavior)
    Files.createDirectories(p.getParent)
    Files.writeString(p, """package org.simplemodeling.textus.smworkflow.p62authored
      |import org.scalatest.wordspec.AnyWordSpec
      |import org.scalatest.matchers.should.Matchers
      |final class LineWriterSpec extends AnyWordSpec with Matchers {
      |  __DECLARATION__
      |  "LineWriter" should {
      |    "retain text and append one LF" in { LineWriter.render("abc") shouldBe "abc\n" }
      |    "retain Unicode and append one LF" in { LineWriter.render("日本語") shouldBe "日本語\n" }
      |    "retain an empty line" in { LineWriter.render("") shouldBe "\n" }
      |  }
      |}
      |""".stripMargin.replace("  __DECLARATION__\n",
        if (broken) "  private val mismatch: Int = \"not an Int\"\n" else ""))
  }
  private def enableEvidence(s: F.Scenario): Unit = {
    val p = s.root.resolve(".textus/config.json")
    val config = parse(Files.readString(p)).toOption.get
    val archive = Files.createDirectories(s.root.resolve("evidence/logs")).getParent.toRealPath()
    val collection = ThreeWorkflowNormalHostFixture.collection("diagnostic_evidence")
    val declared = Json.obj("schemaVersion" -> Json.fromString(CommonEvidence.schemaVersion),
      "storeName" -> Json.fromString("workflow"), "collection" -> Json.fromString(collection),
      "hostIdentity" -> Json.fromString("diagnosis-cli-spec"), "workspaceIdentity" -> Json.fromString("diagnosis-archive"),
      "directory" -> Json.fromString("logs"))
    def configured(name: String): Json = parse(config.hcursor.get[String](name).toOption.get).toOption.get
    val owner = Json.fromString(SmWorkflowComponent.componentId.name)
    val storage = configured("textus.subsystem.storage.plan").mapObject(f => f.add("records",
      Json.fromValues(f("records").get.asArray.get :+ Json.obj("componentId" -> owner,
        "storeName" -> Json.fromString("workflow"), "collection" -> Json.fromString(collection)))))
    val process = configured("textus.subsystem.process-execution.plan").mapObject(f => f
      .add("workspaces", Json.fromValues(f("workspaces").get.asArray.get :+ Json.obj(
        "identity" -> Json.fromString("diagnosis-archive"), "root" -> Json.fromString(archive.toString))))
      .add("workspaceGrants", Json.fromValues(f("workspaceGrants").get.asArray.get :+ Json.obj(
        "componentId" -> owner, "workspaceId" -> Json.fromString("diagnosis-archive"), "access" -> Json.fromString("manage")))))
    Files.writeString(p, config.mapObject(_.add(CommonEvidence.declaration.name, Json.fromString(declared.noSpaces))
      .add("textus.subsystem.storage.plan", Json.fromString(storage.noSpaces))
      .add("textus.subsystem.process-execution.plan", Json.fromString(process.noSpaces))).noSpaces)
  }
  private def submit(s: F.Scenario, request: V.GoalPhaseAdvanceResult, name: String,
    manifest: Option[D.Manifest] = None): V.GoalPhaseWorkSubmissionResult = {
    val dispositions = request.work.get.issues.toVector.flatMap(_.issues).filter(_.status != "RESOLVED").map(i =>
      V.GoalPhaseIssueDisposition(i.identity, "FIXED", "Specification-owned correction within the assigned diagnostic scope", None))
    val result = V.GoalPhaseWorkResult("completed", "Specification-owned diagnostic correction and actual behavioral tests",
      Vector.empty, None, None, Option.when(dispositions.nonEmpty)(V.GoalPhaseIssueReport(dispositions)),
      None, None, None, None, None, dependencyValidation = None,
      diagnosticRepair = manifest.map(m => GoalPhaseNativeMetadata.record(m.asJson)))
    s.call("command", request.submitOperation.get, Vector("--sessionId", request.submitSessionId.get,
      "--result", json(result), "--resultReference", json(V.WorkflowArtifactReference(name, "1"))))
      .business[V.GoalPhaseWorkSubmissionResult]
  }
  private def information(s: F.Scenario, input: V.GoalPhaseDiagnosticInformationInput): F.Result =
    s.call("command", IncrementalSbtDiagnosisInformation.operation, Vector("--input", json(input)))
  private def manifest(request: V.GoalPhaseAdvanceResult, name: String,
    information: Vector[ContextReference]): D.Manifest = {
    val failure = request.work.get.checkFailure.get.validation
    val failed = failure.checks.find(_.exitCode.exists(_ != 0)).get
    val stream = if (failed.stdout.nonEmpty) "stdout" else "stderr"
    D.Manifest(D.schema, name, failure.sourceRevision,
      GoalPhaseOrderedProgression.Snapshot(failure.candidate.head, failure.candidate.tree),
      "Correct the actual typed declaration first, then verify the specified line-writing behavior",
      Vector(D.Locator(failed.identity, stream, 1, 1, "Actual retained failed invocation; expanded context is linked below")),
      information, Vector(library, behavior), Vector("Retain input text and append exactly one LF"),
      Vector("The declaration and its selected behavioral tests must agree"), Vector("compile", "focused"),
      Vector("A compiler success alone cannot establish the required behavior"))
  }

  "Connected incremental SBT diagnosis" should {
    Vector("Compile", "Test").foreach { configuration =>
      s"retain a real $configuration compilation failure and continue information, repair and nonempty behavioral admission" in {
        Given("a fresh isolated normal host with the delivered runtime repair policy, managed Evidence and Journal")
        val s = F.freshStepAssessment("p66-diagnosis-" + configuration.toLowerCase,
          TypedSbtHostPlanFixture.normalExecutable, repairLimit = Some(10), focusedFirst = true)
        enableEvidence(s)
        val task = s.ownTask("diagnosis-task")
        val start = s.start().business[V.GoalPhaseStartResult]
        s.retain(task, start)
        val implementation = s.advance(task).business[V.GoalPhaseAdvanceResult]
        if (configuration == "Compile") source(s, "42")
        tests(s, configuration == "Test")
        submit(s, implementation, "p66-initial-" + configuration).accepted shouldBe true

        When("ordinary Advance retains the actual failed compiler capture and issues the same Step repair")
        val repair = s.advance(task).business[V.GoalPhaseAdvanceResult]
        repair.work.get.kind shouldBe "RepairStep"
        repair.sessionId shouldBe start.sessionId
        repair.work.get.selectedPlan shouldBe implementation.work.get.selectedPlan
        repair.work.get.originalBase shouldBe implementation.work.get.originalBase
        repair.repairProgress.get.automaticCount shouldBe 1
        repair.repairProgress.get.runtimePolicy shouldBe Some(GoalPhaseRuntimeRepairControl.version)
        val failed = repair.work.get.checkFailure.get.validation
        failed.outcome shouldBe Some("repair-required")
        val failedCheck = failed.checks.find(_.exitCode.exists(_ != 0)).get
        val raw = failedCheck.stdout + failedCheck.stderr
        raw should include("Type Mismatch")
        raw should include("Compilation failed")
        raw should include(if (configuration == "Compile") "LineWriter.scala" else "LineWriterSpec.scala")
        failed.diagnostics should not be empty
        val views = failed.diagnostics.map(record)
        views.exists(_.noSpaces.contains("compiler-error")) shouldBe true
        views.exists(_.noSpaces.contains("not-executed-because-compilation-failed")) shouldBe true
        val before = executions(s)
        before.size shouldBe 1
        failedCheck.sbt.get.kind shouldBe "test-only"
        val context = repair.issuedWorkContext.get
        val transported = take(NativeWorkflowOperationInput.decodeValueC[V.WorkflowIssuedWorkContext](
          parse(json(context)).toOption.get, "issuedWorkContext"))
        withClue("The actual issued public context must survive the operation input codec: ") {
          IncrementalSbtDiagnosisInformation.sameWorkC(context, transported).isSuccess shouldBe true
        }
        val stream = if (failedCheck.stdout.nonEmpty) "stdout" else "stderr"
        val occurrence = views.flatMap(_.hcursor.downField("groups").focus.toVector.flatMap(_.asArray.toVector.flatten))
          .flatMap(_.hcursor.downField("occurrences").focus.toVector.flatMap(_.asArray.toVector.flatten))
          .find(_.hcursor.get[String]("stream").toOption.contains(stream)).get
        val selected = Vector(
          V.GoalPhaseDiagnosticSelection("full", "full-output", "Inspect the complete retained compiler output", Some(failedCheck.identity), Some(stream), None, None),
          V.GoalPhaseDiagnosticSelection("declaration", "source-file", "Inspect the related declaration", None, None, None, Some(library)),
          V.GoalPhaseDiagnosticSelection("test", "source-file", "Inspect the behavioral call sites", None, None, None, Some(behavior)),
          V.GoalPhaseDiagnosticSelection("broader", "suite-context", "Inspect broader setup while retaining unknown suite attribution", Some(failedCheck.identity), Some(stream),
            Some(occurrence.hcursor.get[Int]("firstLine").toOption.get), None),
          V.GoalPhaseDiagnosticSelection("missing", "source-file", "Establish that a related optional source is unavailable", None, None, None, Some("src/main/scala/Absent.scala")))
        val input = V.GoalPhaseDiagnosticInformationInput(context, "compiler-context", selected, Vector.empty, None)
        val delivered = information(s, input).business[V.GoalPhaseDiagnosticInformationResult]
        delivered.status shouldBe "delivered"
        delivered.issuedWorkContext shouldBe context
        delivered.items.map(record).map(_.hcursor.get[String]("status").toOption.get).count(_ == "available") shouldBe 3
        record(delivered.items(3)).hcursor.get[String]("status").toOption.get shouldBe "partial"
        record(delivered.items.last).hcursor.get[String]("status").toOption.get shouldBe "unavailable"
        delivered.recording.get.status shouldBe "stored"
        delivered.recording.get.journal.get.status shouldBe "recorded"
        val evidence = delivered.recording.get.evidence.get
        val first = manifest(repair, "correct-compiler", Vector(ContextReference(evidence.identity, evidence.revision)))

        Then("information is immutable, resumable and does not spend a repair or replay a check")
        val repeated = information(s, input.copy(knownEvidence = Vector(evidence))).business[V.GoalPhaseDiagnosticInformationResult]
        repeated.recording.get.evidence shouldBe Some(evidence)
        repeated.items.map(record).foreach(_.hcursor.downField("text").focus shouldBe None)
        source(s, "value.reverse + \"\\n\"")
        tests(s, false)
        val retained = information(s, input).business[V.GoalPhaseDiagnosticInformationResult]
        retained.items shouldBe delivered.items
        val refreshed = information(s, input.copy(requestIdentity = "updated-source", selections = Vector(selected(1))))
          .business[V.GoalPhaseDiagnosticInformationResult]
        record(refreshed.items.head).noSpaces should include("reverse")
        refreshed.recording.get.evidence should not be Some(evidence)
        val unavailable = information(s, input.copy(requestIdentity = "unavailable-context", selections = Vector(selected.last)))
          .business[V.GoalPhaseDiagnosticInformationResult]
        unavailable.status shouldBe "no-new-information"
        unavailable.recording.get.status shouldBe "stored"
        val history = information(s, input.copy(requestIdentity = "replacement-history", selections = Vector.empty))
          .business[V.GoalPhaseDiagnosticInformationResult]
        history.status shouldBe "history"
        history.historyStatus shouldBe "found"
        history.history.exists(r => record(r).noSpaces.contains(evidence.identity)) shouldBe true
        val read = s.readEvidence(V.EvidenceReadInput(evidence.identity, Some(evidence.revision))).business[V.EvidenceReadResult]
        read.status shouldBe "found"
        val foreign = input.copy(issuedWorkContext = context.copy(expectedRevision = "999999"))
        information(s, foreign).exitCode should not be 0
        val unchanged = s.advance(task).business[V.GoalPhaseAdvanceResult]
        unchanged.issuedWorkContext shouldBe repair.issuedWorkContext
        unchanged.repairProgress shouldBe repair.repairProgress
        executions(s) shouldBe before

        When("one concrete manifest uses that delivered context and ordinary validation finds the remaining behavioral defect")
        submit(s, repair, "p66-compiler-repair-" + configuration, Some(first)).accepted shouldBe true
        val second = s.advance(task).business[V.GoalPhaseAdvanceResult]
        second.work.get.kind shouldBe "RepairStep"
        second.repairProgress.get.automaticCount shouldBe 2
        second.work.get.diagnosticHistory.map(record).exists(_.noSpaces.contains(first.identity)) shouldBe true
        second.work.get.checkFailure.get.validation.checks.head.sbt.get.kind shouldBe "test-only"
        second.work.get.checkFailure.get.validation.checks.last.stdout should include("FAILED")
        second.work.get.checkFailure.get.validation.candidate should not be failed.candidate

        When("the revised diagnosis repairs behavior and the runtime executes Compile plus actual focused tests")
        val revised = manifest(second, "correct-behavior", Vector.empty)
        source(s, "value + \"\\n\"")
        submit(s, second, "p66-behavior-repair-" + configuration,
          Some(revised)).accepted shouldBe true
        val closingCall = s.advance(task)
        val closing = closingCall.business[V.GoalPhaseAdvanceResult]
        closing.kind shouldBe "Wait"
        closing.validation.get.outcome shouldBe Some("passed")
        closing.validation.get.checks.map(_.sbt.get.kind) shouldBe Vector("test-only", "compile")
        closing.validation.get.checks.head.sbt.get.selection.get.testsRun shouldBe 3L
        record(closing.validation.get.assessment.get).hcursor.get[String]("branch").toOption.get shouldBe "admit"
        closing.repairProgress.get.automaticCount shouldBe 2
        val checked = executions(s)
        checked.size shouldBe before.size + 3
        checked.distinct.size shouldBe checked.size

        Given("specification-owned Step commit authority through the normal preparation route")
        val referenceFile = s.root.resolve("diagnosis-closure-reference.json")
        Files.writeString(referenceFile, json(V.WorkflowArtifactReference("p66-diagnosis-closure-" + configuration, "1")))
        val authority = V.WorkflowAuthorityPreparationInput(closingCall.invocation.stdout.toUri.toASCIIString,
          referenceFile.toUri.toASCIIString, "participant", "normal-development-host", java.time.Instant.now().toString, "entity:smworkflow.cli.host_evidence")
        s.selectPreparedImport(s.prepareHostInput("prepareAuthority", authority.toRecord()))
        val next = s.advance(task).business[V.GoalPhaseAdvanceResult]
        withClue(next.message) { next.kind shouldBe "Request" }
        next.work.get.completed.size shouldBe 1
        record(next.work.get.completed.head.admission.get).hcursor.downField("assessment").get[String]("branch").toOption.get shouldBe "admit"
        next.work.get.originalBase shouldBe implementation.work.get.originalBase
        next.sessionId shouldBe start.sessionId
        executions(s) shouldBe checked
        information(s, input).exitCode should not be 0
      }
    }
  }
}
