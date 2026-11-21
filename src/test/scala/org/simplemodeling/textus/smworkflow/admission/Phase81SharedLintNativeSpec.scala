package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import java.nio.file.{Files, Path}
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import org.goldenport.record.RecordPresentable
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import SplitPhaseExplicitPartitionFixture.take

/** A tiny ordinary workflow, real compiler/Scalafix and existing FIX/admission.
  * Semantic work and closure authority are specification-owned. This proves no
  * real user consent, installed image activation or full security assurance.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
// REASON: these exact native business-shape assertions intentionally fail if
// their required Work/check/input is absent; this does not exempt product code.
@SuppressWarnings(Array("scalafix:NoOptionGet"))
final class Phase81SharedLintNativeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val source = "src/main/scala/org/simplemodeling/textus/smworkflow/p62authored/LineWriter.scala"
  private val test = "src/test/scala/org/simplemodeling/textus/smworkflow/p62authored/LineWriterSpec.scala"
  private def json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def result(request: V.GoalPhaseAdvanceResult, summary: String): V.GoalPhaseWorkResult = {
    val issues = if (request.work.get.kind == "RepairStep") request.work.get.issues.map(value =>
      V.GoalPhaseIssueReport(value.issues.filter(_.status != "RESOLVED").map(issue =>
        V.GoalPhaseIssueDisposition(issue.identity, "FIXED", summary, None)))) else None
    V.GoalPhaseWorkResult("completed", summary, Vector.empty, None, None, issues, None, None, None, None, None,
      dependencyValidation = None, diagnosticRepair = None)
  }
  private def submit(s: F.Scenario, request: V.GoalPhaseAdvanceResult, name: String): Unit =
    s.call("command", request.submitOperation.get, Vector("--sessionId", request.submitSessionId.get,
      "--result", json(result(request, "SPECIFICATION-OWNED " + name)), "--resultReference",
      json(V.WorkflowArtifactReference(name, "1")))).business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
  private def executions(s: F.Scenario): Vector[String] = Files.readAllLines(s.root.resolve("sbt-executions.txt")).asScala.toVector

  "The shared Scala lint native Action" should {
    "retain actual Main/Test findings, ordinary FIX, scoped suppression, old debt and generated exclusion through admission" in {
      Given("a disposable selected shared-lint host with actual rule JAR and no project configuration")
      val s = F.freshSharedScalaLint("phase81-shared-lint", TypedSbtHostPlanFixture.normalExecutable)
      s.enablePreparedImportRetention()
      val repository = Path.of(s.host.target.root)
      // Use the existing runtime-home isolation route so a user's settings do
      // not become this fixture's live source/history configuration.
      val testhome = Files.createDirectories(s.root.resolve("test-home"))
      Files.exists(repository.resolve(".scalafix.conf")) shouldBe false
      val task = s.ownTask("shared-lint-task")
      def advance(): F.Result = s.call("command", GoalPhaseNativeRun.advanceOperation.operation,
        Vector("--sessionId", s.retrieve(task), "--textus.test.home.path=" + testhome.toString,
          "--textus.test.home.inherit.runtime=false"))
      val body = """package org.simplemodeling.textus.smworkflow.p62authored
        |import org.scalatest.wordspec.AnyWordSpec
        |import org.scalatest.matchers.should.Matchers
        |__DIRECTIVE__
        |
        |final class LineWriterSpec extends AnyWordSpec with Matchers {
        |  "LineWriter" should {
        |    "reverse nonempty text and retain LF" in { LineWriter.render("abc") shouldBe "cba\n" }
        |    "retain an empty line" in { LineWriter.render("") shouldBe "\n" }
        |  }
        |__EXCEPTION__
        |}
        |""".stripMargin
      val unsafe = "  def unsafe(value: Option[Int]): Int = value.get"
      val directive = "// scalafix:off NoOptionGet\n"
      // Before Start, commit only this disposable fixture's Test baseline.
      // Later removing the directive must expose an unchanged real violation;
      // the surviving blank separator must not erase its old-side impact.
      val baseline = body.replace("__DIRECTIVE__\n", directive).replace("__EXCEPTION__", unsafe)
      Files.createDirectories(repository.resolve(test).getParent)
      Files.writeString(repository.resolve(test), baseline)
      Vector(Vector("add", "--", test), Vector("-c", "user.name=Native Workflow Fixture",
        "-c", "user.email=native-workflow@example.invalid", "commit", "--only", "--no-verify",
        "--no-gpg-sign", "-m", "Prepare test-owned suppressed unsafe baseline", "--", test)).foreach { arguments =>
        val process = new ProcessBuilder((Vector("/usr/bin/git", "--no-optional-locks", "-C", repository.toString,
          "-c", "core.hooksPath=/dev/null", "--literal-pathspecs") ++ arguments).asJava)
          .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        try {
          process.getOutputStream.close()
          process.waitFor(10, TimeUnit.SECONDS) shouldBe true
          process.exitValue() shouldBe 0
        } finally {
          if (process.isAlive) { process.destroyForcibly(); process.waitFor() }
          process.getInputStream.close()
          process.getErrorStream.close()
        }
      }
      s.retain(task, s.start().business[V.GoalPhaseStartResult])
      val initial = s.readGoalPhaseResult(advance())
      def author(mainBad: Boolean, testBad: Boolean, suppressed: Boolean = false): Unit = {
        Files.writeString(repository.resolve(source), F.sharedLintInitialSource +
          (if (mainBad) "\nobject AddedPartial { private def _wrapCommand(value: Int): Int = value; def unsafe(value: Option[Int]): Int = value.get }\n"
           else "\nobject AddedPartial { private def _wrap_command(value: Int): Int = value; def safe(value: Option[Int]): Int = value.fold(0)(identity) }\n"))
        val exception = if (testBad) unsafe
          else "  // REASON: deliberate interop fixture; the test caller supplies Some.\n" +
            "  @SuppressWarnings(Array(\"scalafix:NoOptionGet\"))\n" +
            "  def unsafe(value: Option[Int]): Int = value.get"
        Files.createDirectories(repository.resolve(test).getParent)
        Files.writeString(repository.resolve(test), body.replace("__EXCEPTION__", exception)
          .replace("__DIRECTIVE__\n", if (suppressed) directive else ""))
      }
      When("new handwritten Main violations enter ordinary validation with the suppressed Test baseline unchanged")
      author(mainBad = true, testBad = true, suppressed = true)
      Files.readString(repository.resolve(test)) shouldBe baseline
      submit(s, initial, "shared-main-and-test-violations")
      val first = s.readGoalPhaseResult(advance())
      first.work.get.kind shouldBe "RepairStep"
      val firstLint = first.work.get.checkFailure.get.validation.checks.last
      (firstLint.stdout + firstLint.stderr) should include("NoOptionGet")
      (firstLint.stdout + firstLint.stderr) should include("GoldenportPrivateMemberNaming")
      (firstLint.stdout + firstLint.stderr) should include("LineWriter.scala")
      (firstLint.stdout + firstLint.stderr) should include("OUTSIDE_AFFECTED_SCOPE age=UNKNOWN")
      (firstLint.stdout + firstLint.stderr) should not include "value.get"
      val firstInput = firstLint.sbt.get.lintInput.get
      val config = take(firstInput.toJsonStringC)
      config should include("baseline@1")
      config should include("diffBase")
      val checked = s.candidateSource(first.work.get.checkFailure.get.validation.candidate.tree, source)
      Files.readString(repository.resolve(source)) shouldBe checked

      When("ordinary FIX removes the new Main violation and removes only the Test suppression directive")
      author(mainBad = false, testBad = true)
      Files.readString(repository.resolve(test)) shouldBe baseline.replace(directive, "")
      submit(s, first, "shared-main-fix")
      val second = s.readGoalPhaseResult(advance())
      second.work.get.kind shouldBe "RepairStep"
      val secondLint = second.work.get.checkFailure.get.validation.checks.last
      (secondLint.stdout + secondLint.stderr) should include("LineWriterSpec.scala")
      (secondLint.stdout + secondLint.stderr).linesIterator.exists(line =>
        line.contains("AFFECTED_BLOCKER") && line.contains("NoOptionGet") && line.contains("LineWriterSpec.scala")) shouldBe true
      (secondLint.stdout + secondLint.stderr) should not include "value.get"
      second.repairProgress.get.automaticCount shouldBe first.repairProgress.get.automaticCount + 1
      second.work.get.issues.get.history.take(first.work.get.issues.get.history.size) shouldBe first.work.get.issues.get.history

      When("the existing scoped-exception policy records one intentional Test definition")
      author(mainBad = false, testBad = false)
      submit(s, second, "shared-scoped-test-exception")
      val waiting = advance()
      val passed = s.readGoalPhaseResult(waiting)
      Then("native admission retains actual lint provenance without rewriting old debt or generated code")
      passed.kind shouldBe "Wait"
      passed.validation.get.outcome shouldBe Some("passed")
      passed.validation.get.checks.last.sbt.get.lintInput.nonEmpty shouldBe true
      Files.readString(repository.resolve(source)) should startWith(F.sharedLintInitialSource)
      Files.readString(repository.resolve("src/main/scala-generated/GeneratedPartial.scala")) should include("value.get")
      val count = executions(s)
      val authority = V.WorkflowAuthorityPreparationInput(waiting.invocation.stdout.toUri.toASCIIString,
        { val path = s.root.resolve("shared-lint-closure-reference.json")
          Files.writeString(path, json(V.WorkflowArtifactReference("shared-lint-specification-closure", "1")))
          path.toUri.toASCIIString }, "participant", "normal-development-host",
        Instant.now().truncatedTo(ChronoUnit.SECONDS).toString, "entity:smworkflow.cli.host_evidence")
      val prepared = s.prepareHostInput("prepareAuthority", authority.toRecord())
      prepared.exitCode shouldBe 0
      s.selectPreparedImport(prepared)
      val next = s.readGoalPhaseResult(advance())
      next.work.get.completed.size shouldBe 1
      next.work.get.completed.head.admission.nonEmpty shouldBe true
      executions(s) shouldBe count
      firstLint.sbt.get.lintInput shouldBe Some(firstInput)
    }
  }
}
