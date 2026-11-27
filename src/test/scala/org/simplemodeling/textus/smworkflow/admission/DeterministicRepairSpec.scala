package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentOrigin, ComponentId, ComponentInstanceId}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.spi.{ResolvedSpiMember, SpiMemberMetadata}
import org.goldenport.cncf.spi.ai.runner.{AiChatRequest, AiChatResponse, AiGenerateRequest, AiGenerateResponse,
  AiRecordRequest, AiRecordResponse, AiRunner, AiRunnerSocketSet}
import org.goldenport.cncf.workflow.WorkflowComponentStorage
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceIdentity
import org.goldenport.convert.ValueReader
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import org.goldenport.cncf.processexecution.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.DeterministicRepair.*
import SplitPhaseExplicitPartitionFixture.take

/** Executable specification for bounded diagnostic proposals.
  * @since   Nov. 22, 2026
  * @version Nov. 27, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.PackagedRuntimeTestDeclaration])
final class DeterministicRepairSpec extends AnyWordSpec with Matchers with GivenWhenThen with ThreeWorkflowManagedRepositoryFixture {
  private val _compiler_path = "src/main/scala/RepairExample.scala"

  override protected def _prepare_sbt_build_(root: Path, scenario: String): Unit = {
    super._prepare_sbt_build_(root, scenario)
    if (Files.exists(root.resolve("format-fixture"))) {
      val build = root.resolve("repository/build.sbt")
      if (Files.readString(root.resolve("format-fixture")) == "lint") Files.writeString(build, """scalaVersion := "2.12.18"
        |scalafixScalaBinaryVersion := "2.13"
        |scalafixConfig := sys.props.get("cncf.sbt.lint.input").map { encoded =>
        |  val text = new String(java.util.Base64.getUrlDecoder.decode(encoded), "UTF-8")
        |  val selected = com.typesafe.config.ConfigFactory.parseString(text).getConfig("config")
        |  val destination = target.value / "selected-lint.conf"
        |  IO.write(destination, selected.root.render())
        |  destination
        |}.orElse(Some(baseDirectory.value / ".scalafix.conf"))
        |addCommandAlias("fixCheck", "Compile / scalafix --check")
        |""".stripMargin)
      else if (Files.exists(root.resolve("format-rollback"))) Files.writeString(build, Files.readString(build) + """
        |Compile / compile := {
        |  val result = (Compile / compile).value
        |  if (!IO.read(baseDirectory.value / "src/main/scala/RepairExample.scala").endsWith("\n\n"))
        |    sys.error("Independent fixture revalidation failure after LF correction")
        |  result
        |}
        |""".stripMargin)
    }
    if (Files.exists(root.resolve("scala3-fixture"))) {
      val build = root.resolve("repository/build.sbt")
      Files.writeString(build, Files.readString(build).replace("2.12.18", "3.3.8"))
    }
  }

  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val plan = super.process_plan(root, scenario, goal).mapObject(fields => fields.add("sbtPrograms",
      fields("sbtPrograms").getOrElse(fail("sbtPrograms absent")).mapArray(_.map { program =>
        val target = program.hcursor.get[String]("target").toOption.getOrElse(fail("SBT target absent"))
        val operation = if (Files.exists(root.resolve("format-fixture")))
          (goal.stepValidation.checks ++ goal.phaseValidation.checks).find(check => target.endsWith("-" + check.ordinal))
            .map(_.kind.wire).getOrElse("compile") else "compile"
        program.mapObject(_.add("operation", Json.fromString(operation)))
      })))
    val server = root.resolve("metals-fixture-server")
    if (!Files.isRegularFile(server)) plan else {
      val companion = Path.of("scripts/sm-workflow-metals-bridge.py").toRealPath()
      Files.isExecutable(companion) shouldBe true
      val limits = Json.obj(Vector("launchTimeoutMillis" -> 5000L, "executionTimeoutMillis" -> 70000L,
        "terminationGraceMillis" -> 1000L, "stdinBytes" -> 1048576L, "stdoutBytes" -> 1048576L,
        "stderrBytes" -> 65536L, "argumentCount" -> 16L, "argumentBytes" -> 4096L,
        "artifactCount" -> 1L, "artifactBytes" -> 1L, "workAreaBytes" -> 4194304L).map((key, value) => key -> Json.fromLong(value))*)
      val program = Json.obj("capability" -> Json.fromString(ScalaMetalsProcess.capability),
        "identity" -> Json.fromString("controlled-metals-companion"), "executable" -> Json.fromString(companion.toString),
        "fixedArguments" -> Json.arr(),
        "argumentTemplates" -> Json.arr(Json.arr()), "limits" -> limits,
        "environment" -> Json.obj("PATH" -> Json.fromString("/usr/bin:/bin"),
          "SM_WORKFLOW_METALS_SERVER" -> Json.fromString(server.toString),
          "METALS_FIXTURE_TRACE" -> Json.fromString(root.resolve("metals.trace").toString),
          "METALS_FIXTURE_MODE" -> Json.fromString(Files.readString(root.resolve("metals.mode")))),
        "allowedArtifacts" -> Json.arr(), "allowedInputFiles" -> Json.obj(), "allowsWorkingDirectory" -> Json.False)
      val programs = plan.hcursor.get[Vector[Json]]("programs").toOption.getOrElse(Vector.empty)
      val grants = plan.hcursor.get[Vector[Json]]("grants").toOption.getOrElse(fail("grants absent")).map { grant =>
        val capabilities = grant.hcursor.get[Vector[Json]]("capabilities").toOption.getOrElse(fail("capabilities absent"))
        grant.mapObject(_.add("capabilities", Json.fromValues(capabilities :+ Json.fromString(ScalaMetalsProcess.capability))))
      }
      plan.mapObject(_.add("programs", Json.fromValues(programs :+ program)).add("grants", Json.fromValues(grants)))
    }
  }

  private def _format_fixture(lint: Boolean, rollback: Boolean)(body: (Path, () => V.GoalPhaseAdvanceResult,
    (V.GoalPhaseWorkResult, V.GoalPhaseAdvanceResult) => Unit, () => Vector[String]) => Unit): Unit = {
    val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/sm-workflow/work/format-native").toAbsolutePath),
      (if (lint) "scalafix-" else "terminal-lf-") + (if (rollback) "rollback-" else "pass-")).toRealPath()
    Files.writeString(root.resolve("format-fixture"), if (lint) "lint" else "terminal")
    if (rollback) Files.writeString(root.resolve("format-rollback"), "true")
    val input = V.GoalPhaseStartInput("native-format-fixture", "sm-workflow")
    val checks = if (lint) Vector(GoalPhaseNativeValidation.Check("fix-check", 1, WorkflowSbtValidation.Kind.FixCheck),
      GoalPhaseNativeValidation.Check("compile", 2, WorkflowSbtValidation.Kind.Compile))
      else Vector(GoalPhaseNativeValidation.Check("compile", 1, WorkflowSbtValidation.Kind.Compile))
    val paths = Vector(_compiler_path, "project.yaml", ".scalafix.conf", "project/plugins.sbt")
    val normal = ThreeWorkflowNormalHostFixture.definition(root)
    val host = normal.copy(goal = normal.goal.copy(input = input, candidatePaths = paths,
      stepValidation = normal.goal.stepValidation.copy(checks = checks), phaseValidation = normal.goal.phaseValidation.copy(checks = checks)))
    val plan = GoalPhaseOrderedProgression.Plan("fixture-format", "1", input.phaseIdentity, input.repositoryIdentity,
      Vector(GoalPhaseOrderedProgression.Step("step-1", "Apply one bounded Scala source correction", paths, Vector.empty,
        checks.map(_.identity))), checks.map(_.identity), repairLimit = 4)
    _with_runtime_(root, "Goal", Some(host)) { (owner, component, actor, prepare) =>
      prepare(call => for {
        _ <- call.setupC(_setup_commands_(root, "Goal").size)
        selection = _goal_selection_(input, "format-selection", "format-start")
        _ <- call.createC("goal_selection", NativeWorkflowStartBusinessKey(NativeWorkflowStartInput.Goal(input), selection.participantIdentity),
          Record.data(ProfileSelectionJsonV1.storageField -> take(ProfileSelectionJsonV1.encodeStorageC(selection))))
        _ <- call.createC("goal_plans", selection.selectionId, Record.data(GoalPhaseSelectedPlanJsonV1.storageField ->
          take(GoalPhaseSelectedPlanJsonV1.encodeStorageC(GoalPhaseNativeRun.SelectedPlan(selection.selectionId, selection.participantIdentity, plan)))))
      } yield ())
      def _invoke_[A: ValueReader](operation: String, properties: List[Property]): A = read_value[A](take(
        owner.executeOperationResponseInContext(Request.of(component = component.name, service = "GoalPhaseService", operation = operation,
          properties = properties), actor)))
      val started = _invoke_[V.GoalPhaseStartResult]("startGoalPhase", NativeWorkflowOperationInputFixture.goal(input))
      def _advance_(): V.GoalPhaseAdvanceResult = _invoke_[V.GoalPhaseAdvanceResult]("advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(started.sessionId))
      def _submit_(value: V.GoalPhaseWorkResult, display: V.GoalPhaseAdvanceResult): Unit = {
        var pending = Option.empty[org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowInteraction[GoalPhaseNativeWork.Request, Nothing]]
        prepare(call => call.goalWorkC(InstanceIdentity(started.sessionId)).map(work => { pending = Some(work); () }))
        val work = pending.getOrElse(fail("format fixture has no actual issued work"))
        val order = work.current.asInstanceOf[org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowContinuation.WorkOrder[GoalPhaseNativeWork.Request]].request
        val reference = org.goldenport.cncf.workflow.ContextReference("format-" + order.continuationId.value, "1")
        val internal = GoalPhaseNativeWork.Result(value.outcome, value.summary, value.findings,
          completionConditions = take(GoalPhaseNativeReviewCompletion.fromPublicConditionsC(value.completionConditions)))
        val evidence = org.goldenport.cncf.workflow.WorkflowProtocolV1.ExecutionEvidence(Vector(reference),
          Some(if (display.work.exists(work => java.util.Objects.equals(work.kind, "ReviewStep"))) "specification-reviewer" else "participant"))
        val result = org.goldenport.cncf.workflow.WorkflowProtocolV1.ContinuationResult(work.handle, order.runId, order.continuationId,
          order.expectedRevision, order.context.snapshot, org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue(
            GoalPhaseNativeWork.resultCodec.typeIdentity, internal), reference, Vector.empty, evidence)
        if (display.work.exists(work => java.util.Objects.equals(work.kind, "ReviewStep"))) prepare(call => for {
          prepared <- org.simplemodeling.textus.smworkflow.preparation.NativeReviewPreparation.goalC(
            display.work.flatMap(_.review).flatMap(_.reviewContext).getOrElse(fail("review context missing")), value, V.WorkflowArtifactReference(reference.identity, reference.revision),
            V.WorkflowExecutionEvidence(Vector(V.WorkflowArtifactReference(reference.identity, reference.revision)), evidence.workerIdentity, None, None),
            "normal-development-host", call.executionContext.clock.instant().minusSeconds(60))
          _ <- call.createC("host_evidence", prepared.recordIdentity, prepared.record)
        } yield ())
        _invoke_[V.GoalPhaseWorkSubmissionResult]("submitGoalPhaseWorkResult", take(NativeWorkflowOperationInputFixture.submissionC(result))).accepted shouldBe true
      }
      def _history_(): Vector[String] = {
        var causes = Vector.empty[String]
        prepare(call => call.goalCurrentC(InstanceIdentity(started.sessionId)).map(current => { causes = current.history.map(_.causation.value); () }))
        causes
      }
      val implementation = _advance_()
      implementation.work.map(_.kind) shouldBe Some("ImplementStep")
      val source = root.resolve("repository").resolve(_compiler_path)
      Files.createDirectories(source.getParent)
      Files.writeString(source, if (lint) (if (rollback) "final object RepairExample {\n  val value: Int = \"broken\"\n}\n" else "final object RepairExample {}\n")
        else "object RepairExample {}\n\n\n")
      Files.writeString(root.resolve("repository/project.yaml"), "validation:\n  metals:\n    enabled: false\n  sharedScalaLint: true\n")
      Files.writeString(root.resolve("repository/.scalafix.conf"), "rules = []\n\n\n")
      if (lint) {
        val baseline = take(WorkflowScalaLintParameters.resolveSelectionC(org.simplemodeling.scalalint.ScalaLintProfiles.Selection("none", "none"), None)).rules
        Files.writeString(root.resolve("repository/.scalafix.conf"), "rules = [RedundantSyntax]\nRedundantSyntax.finalObject = true\nRedundantSyntax.stringInterpolator = false\n" +
          "sm-workflow.scala-lint.remove-rules = " + Json.fromValues(baseline.map(Json.fromString)).noSpaces + "\n")
        Files.writeString(root.resolve("repository/project/plugins.sbt"), "addSbtPlugin(\"ch.epfl.scala\" % \"sbt-scalafix\" % \"0.12.1\")\n")
      }
      _submit_(V.GoalPhaseWorkResult("completed", "Specification-owned formatting fixture", Vector.empty, None, None, None, None, None,
        None, None, None, dependencyValidation = None, diagnosticRepair = None), implementation)
      body(source, _advance_, _submit_, _history_)
    }
  }

  private def _metals_material(column: Int = 7): CompilerMaterial = {
    val source = "import scala.DoesNotExist\nobject A\n"
    val uri = "file:///work/src/main/scala/A.scala"
    val point = Position(0, column)
    val message = s"[error] /work/src/main/scala/A.scala:1:${column + 1}: object DoesNotExist is not a member of package scala\n" +
      "[error] import scala.DoesNotExist\n[error] " + (" " * column) + "^"
    val document = Document(uri, "a" * 40, 0, source)
    CompilerMaterial("fixture-session", "12", document.revision, "11:compile", "src/main/scala/A.scala",
      "sm-workflow-repository", document, Diagnostic("scala-compiler", uri, document.revision, 0,
        "compiler-error", Range(point, point), message))
  }

  private[admission] val metalsServerSource = """#!/usr/bin/python3
import json, os, sys, time
from pathlib import Path
trace = Path(os.environ["METALS_FIXTURE_TRACE"])
mode = os.environ["METALS_FIXTURE_MODE"]
def log(value):
    with trace.open("a") as stream:
        stream.write(value + "\n")
log("pid:" + str(os.getpid()))
log("cwd:" + str(Path.cwd()))
uri = None
version = None
missing = "DoesNotExist"
while True:
    length = None
    while True:
        line = sys.stdin.buffer.readline()
        if not line:
            sys.exit(0)
        if line == b"\r\n":
            break
        if line.lower().startswith(b"content-length:"):
            length = int(line.split(b":", 1)[1])
    message = json.loads(sys.stdin.buffer.read(length))
    method = message["method"]
    log(method)
    if method == "exit":
        sys.exit(0)
    if method == "textDocument/didOpen":
        document = message["params"]["textDocument"]
        uri, version = document["uri"], document["version"]
        missing = "MissingPackage" if "import MissingPackage.Member" in document["text"] else "DoesNotExist"
        if document["text"] != Path(uri.removeprefix("file://")).read_text():
            sys.exit(2)
    if "id" not in message:
        continue
    if method == "initialize":
        encoding = "utf-8" if mode == "encoding" else "utf-16"
        result = {"capabilities": {"codeActionProvider": True, "positionEncoding": encoding}}
    elif method == "shutdown":
        result = None
    elif method == "textDocument/codeAction":
        log("only:" + json.dumps(message["params"]["context"].get("only")))
        if mode == "timeout":
            time.sleep(10)
        if mode == "framing":
            sys.stdout.buffer.write(b"Content-Length: 99999999\r\n\r\n")
            sys.stdout.buffer.flush()
            continue
        target = uri if mode != "unowned" else "file:///other.scala"
        if mode in ("original-uri", "original-legacy-uri"):
            target = "file:///work/src/main/scala/A.scala"
        row = message["params"]["range"]["start"]["line"]
        edit = {"range": {"start": {"line": row, "character": 0}, "end": {"line": row + 1, "character": 0}}, "newText": ""}
        action = {"title": "Remove invalid import of '" + missing + "'", "kind": "quickfix",
                  "edit": {"documentChanges": [{"textDocument": {"uri": target, "version": version}, "edits": [edit]}]}}
        if mode in ("refactor", "source"):
            action["kind"] = mode + ".rewrite"
        if mode == "original-legacy-uri":
            action["edit"] = {"changes": {target: [edit]}}
        if mode == "command":
            action["command"] = {"command": "execute-arbitrary"}
        result = [action, action] if mode == "ambiguous" else [action]
    else:
        sys.exit(3)
    body = json.dumps({"jsonrpc": "2.0", "id": message["id"], "result": result}).encode()
    sys.stdout.buffer.write(b"Content-Length: " + str(len(body)).encode() + b"\r\n\r\n" + body)
    sys.stdout.buffer.flush()
"""

  "Ordinary formatting repair" should {
    "derive an exact token deletion from real pinned Scalafix and revalidate or roll back through native admission" in {
      Vector(false, true).foreach { rollback =>
        Given("an isolated ordinary Phase using typed FixCheck and sbt-scalafix 0.12.1 RedundantSyntax.finalObject")
        _format_fixture(lint = true, rollback = rollback) { (source, advance, _, history) =>
          When("ordinary Advance consumes the real check's proposed diff")
          advance()
          val result = advance()
          Then("exactly one checked edit is observed without a compiler provider or semantic repair")
          history().count(value => java.util.Objects.equals(value, DeterministicRepair.applyIntentCause)) shouldBe 1
          history() should not contain ScalaMetalsProcess.intentCause
          history() should not contain "ordered-bounded-compiler:intent"
          if (rollback) {
            history().count(value => java.util.Objects.equals(value, DeterministicRepair.rollbackIntentCause)) shouldBe 1
            Files.readString(source) should include("final object RepairExample")
            result.work.map(_.kind) shouldBe Some("RepairStep")
            val repeated = advance()
            repeated.work shouldBe result.work
            history().count(value => java.util.Objects.equals(value, DeterministicRepair.applyIntentCause)) shouldBe 1
            history().count(value => java.util.Objects.equals(value, DeterministicRepair.rollbackIntentCause)) shouldBe 1
          } else {
            result.work.map(_.kind) shouldBe Some("ReviewStep")
            Files.readString(source) should not include "final object RepairExample"
            Files.readString(source) should endWith("object RepairExample {}\n")
            val repeated = advance()
            repeated.work.map(_.kind) shouldBe Some("ReviewStep")
            history().count(value => java.util.Objects.equals(value, DeterministicRepair.applyIntentCause)) shouldBe 1
          }
        }
      }
    }

    "use one original independent terminal-LF verdict then require fresh checks and an independent review" in {
      Vector(false, true).foreach { rollback =>
        Given("one owned handwritten source and an independently admitted single terminal-LF review finding")
        _format_fixture(lint = false, rollback = rollback) { (source, advance, submit, history) =>
          val review = advance()
          review.work.map(_.kind) shouldBe Some("ReviewStep")
          val conditions = Record.data("kind" -> "terminal-lf", "paths" -> Vector(_compiler_path))
          val verdict = V.GoalPhaseWorkResult("repair", "Remove only the extra terminal LF", Vector("Remove extra terminal LF in " + _compiler_path),
            None, None, None, None, None, None, Some(conditions), None, dependencyValidation = None, diagnosticRepair = None)
          submit(verdict, review)
          When("normal Advance reconstructs the retained verdict and its original passed candidate")
          advance()
          val next = advance()
          Then("the single exact edit uses the normal transaction and cannot bypass revalidation or independent review")
          history().count(value => java.util.Objects.equals(value, DeterministicRepair.applyIntentCause)) shouldBe 1
          if (rollback) {
            history().count(value => java.util.Objects.equals(value, DeterministicRepair.rollbackIntentCause)) shouldBe 1
            Files.readString(source) should endWith("object RepairExample {}\n\n\n")
            next.work.map(_.kind) shouldBe Some("RepairStep")
            val repeated = advance()
            repeated.work shouldBe next.work
            history().count(value => java.util.Objects.equals(value, DeterministicRepair.applyIntentCause)) shouldBe 1
            history().count(value => java.util.Objects.equals(value, DeterministicRepair.rollbackIntentCause)) shouldBe 1
          } else {
            Files.readString(source) should endWith("object RepairExample {}\n")
            Files.readString(source) should not endWith "\n\n"
            next.work.map(_.kind) shouldBe Some("ReviewStep")
          }
        }
      }
    }

    "leave a multi-file terminal-LF review on its ordinary semantic repair path" in {
      Given("an original independent verdict with two owned terminal-LF conditions")
      _format_fixture(lint = false, rollback = false) { (source, advance, submit, history) =>
        val review = advance()
        review.work.map(_.kind) shouldBe Some("ReviewStep")
        val conditions = Record.data("kind" -> "terminal-lf", "paths" -> Vector(_compiler_path, ".scalafix.conf"))
        val verdict = V.GoalPhaseWorkResult("repair", "Remove only the two extra terminal LF sequences",
          Vector("Remove extra terminal LF in " + _compiler_path, "Remove extra terminal LF in .scalafix.conf"),
          None, None, None, None, None, None, Some(conditions), None, dependencyValidation = None, diagnosticRepair = None)
        submit(verdict, review)
        When("ordinary Advance encounters the retained multi-file contract")
        val next = advance()
        Then("no single-file shortcut consumes that verdict")
        next.work.map(_.kind) shouldBe Some("RepairStep")
        Files.readString(source) should endWith("object RepairExample {}\n\n\n")
        history() should not contain DeterministicRepair.applyIntentCause
      }
    }

    "refuse clipped, additional, stale or unconfigured lint proposals" in {
      Given("one complete actual-shaped FixCheck capture with the configured allowlisted rule")
      val source = "final object A {}\n"
      val document = Document("file:///work/src/main/scala/A.scala", "b" * 40, 0, source)
      val check = GoalPhaseNativeValidation.Check("fix-check", 1, WorkflowSbtValidation.Kind.FixCheck)
      val input = org.goldenport.cncf.processexecution.SbtLintInput("sm-workflow-shared-scala-lint", "1",
        "{\"rules\":[\"RedundantSyntax\"],\"RedundantSyntax\":{\"finalObject\":true,\"stringInterpolator\":false}," +
          "\"sm-workflow\":{\"scala-lint\":{\"context\":{\"diffBase\":\"" + ("a" * 40) + "\"}}}}")
      val proof = ScalaLintRepair.attest(check.identity, input).getOrElse(fail("configured fixture attestation absent"))
      val diff = "--- /work/src/main/scala/A.scala\n+++ <expected fix>\n@@ -1,1 +1,1 @@\n-final object A {}\n+object A {}\n"
      def _payload_(output: String, clipped: Boolean = false): GoalPhaseNativeValidation.Payload = {
        val bytes = output.getBytes(UTF_8).toVector
        val process = ProcessExecutionResult(ProcessExecutionTermination.Exited(1), ProcessExecutionCapture(bytes, bytes.size.toLong, clipped),
          ProcessExecutionCapture(Vector.empty, 0L, false), Vector.empty, 1L, "fixture-fix-check")
        val receipt = RepositorySyncNativeMergeValidation.Receipt(check.identity, "exited", Some(1), output, "",
          Some(WorkflowSbtValidation.Facts(check.kind, WorkflowSbtValidation.Outcome.Failed, None, None)), Some(process))
        GoalPhaseNativeValidation.Payload("1", "step-1", GoalPhaseOrderedProgression.Snapshot("a" * 40, document.revision),
          Vector(check), Vector(receipt), Some("repair-required"), lintRepairs = Vector(proof))
      }
      When("the candidate-bound parser admits only the complete single tool correction")
      val finding = ScalaLintRepair.finding(_payload_(diff + "[error] (Compile / scalafix) TestError\n")).getOrElse(fail("fixture diff absent"))
      Then("only the supported final token is removed and uncertain observations stay on ordinary repair")
      ScalaLintRepair.rendered(finding, document) shouldBe Some("object A {}\n")
      ScalaLintRepair.finding(_payload_(diff, clipped = true)) shouldBe None
      ScalaLintRepair.finding(_payload_(diff + diff)) shouldBe None
      ScalaLintRepair.finding(_payload_(diff + "[error] unrelated failure\n")) shouldBe None
      ScalaLintRepair.finding(_payload_(diff).copy(lintRepairs = Vector.empty)) shouldBe None
      ScalaLintRepair.finding(_payload_(diff).copy(lintRepairs = Vector(proof.copy(diffBase = "c" * 40)))) shouldBe None
      ScalaLintRepair.rendered(finding, document.copy(text = "final object B {}\n")) shouldBe None
      ScalaLintRepair.rendered(finding.copy(diff = finding.diff.updated(2, "+object B {}")), document) shouldBe None
      ScalaLintRepair.attest(check.identity, input.copy(configJson = input.configJson.replace("true", "false"))) shouldBe None
      ScalaLintRepair.appendValid(Vector(proof), Vector(proof.copy(recipe = "unregistered")), check.identity) shouldBe false
      GoalPhaseNativeValidation.codec.decode(GoalPhaseNativeValidation.codec.encode(_payload_(diff))) shouldBe Right(_payload_(diff))
      val legacy = _payload_(diff).copy(lintRepairs = Vector.empty)
      GoalPhaseNativeValidation.codec.decode(GoalPhaseNativeValidation.codec.encode(legacy)) shouldBe Right(legacy)
    }
  }

  "The fixed Metals production route" should {
    "apply the exact subprocess proposal through ordinary Step admission and real Scala 2 and 3 revalidation without AI" in {
      Vector(false, true).foreach { scala3 =>
      Given("an isolated native Phase with a configured fixed Metals recipe and one invalid import")
      val parent = Files.createDirectories(Path.of("target/sm-workflow/work/metals-native").toAbsolutePath)
      val root = Files.createTempDirectory(parent, if (scala3) "scala3-" else "scala2-").toRealPath()
      if (scala3) Files.writeString(root.resolve("scala3-fixture"), "3.3.8")
      val server = root.resolve("metals-fixture-server")
      Files.writeString(server, metalsServerSource)
      server.toFile.setExecutable(true) shouldBe true
      Files.writeString(root.resolve("metals.mode"), "completed")
      val input = V.GoalPhaseStartInput("native-metals-fixture", "sm-workflow")
      val check = GoalPhaseNativeValidation.Check("compile", 1, WorkflowSbtValidation.Kind.Compile)
      val paths = Vector(_compiler_path, "project.yaml")
      val normal = ThreeWorkflowNormalHostFixture.definition(root)
      val host = normal.copy(goal = normal.goal.copy(input = input, candidatePaths = paths,
        stepValidation = normal.goal.stepValidation.copy(checks = Vector(check)),
        phaseValidation = normal.goal.phaseValidation.copy(checks = Vector(check))))
      val plan = GoalPhaseOrderedProgression.Plan("fixture-native-metals", "1", input.phaseIdentity, input.repositoryIdentity,
        Vector(GoalPhaseOrderedProgression.Step("step-1", "Remove one invalid literal Scala import", paths, Vector.empty,
          Vector(check.identity))), Vector(check.identity), repairLimit = 4)
      _with_runtime_(root, "Goal", Some(host)) { (owner, component, actor, prepare) =>
        prepare(call => for {
          _ <- call.setupC(_setup_commands_(root, "Goal").size)
          selection = _goal_selection_(input, "fixture-native-metals-selection", "fixture-native-metals-start")
          _ <- call.createC("goal_selection", NativeWorkflowStartBusinessKey(NativeWorkflowStartInput.Goal(input),
            selection.participantIdentity), Record.data(ProfileSelectionJsonV1.storageField -> take(ProfileSelectionJsonV1.encodeStorageC(selection))))
          _ <- call.createC("goal_plans", selection.selectionId, Record.data(GoalPhaseSelectedPlanJsonV1.storageField ->
            take(GoalPhaseSelectedPlanJsonV1.encodeStorageC(GoalPhaseNativeRun.SelectedPlan(selection.selectionId, selection.participantIdentity, plan)))))
        } yield ())
        def _invoke_[A: ValueReader](operation: String, properties: List[Property]): A =
          read_value[A](take(owner.executeOperationResponseInContext(
            Request.of(component = component.name, service = "GoalPhaseService", operation = operation, properties = properties), actor)))
        val started = _invoke_[V.GoalPhaseStartResult]("startGoalPhase", NativeWorkflowOperationInputFixture.goal(input))
        def _advance_(): V.GoalPhaseAdvanceResult = _invoke_[V.GoalPhaseAdvanceResult]("advanceGoalPhase",
          NativeWorkflowOperationInputFixture.advance(started.sessionId))
        _advance_().work.map(_.kind) shouldBe Some("ImplementStep")
        val source = root.resolve("repository").resolve(_compiler_path)
        Files.createDirectories(source.getParent)
        val invalidImport = if (scala3) "import MissingPackage.Member" else "import scala.DoesNotExist"
        Files.writeString(source, invalidImport + "\nobject RepairExample {}\n")
        Files.writeString(root.resolve("repository/project.yaml"),
          "validation:\n  metals:\n    enabled: true\n    serverIdentity: scala-metals-1-5-3\n    timeoutMillis: 1000\n")
        val result = V.GoalPhaseWorkResult("completed", "Fixture invalid import", Vector.empty,
          None, None, None, None, None, None, None, None, dependencyValidation = None, diagnosticRepair = None)
        val reference = V.WorkflowArtifactReference("native-metals-implementation", "1")
        _invoke_[V.GoalPhaseWorkSubmissionResult]("submitGoalPhaseWorkResult", List(Property("sessionId", started.sessionId, None),
          Property("result", result.toRecord(), None), Property("resultReference", reference.toRecord(), None))).accepted shouldBe true
        When("ordinary Advance authenticates the compiler error and uses the production process transport")
        _advance_()
        val resumed = _advance_()
        Then("the native file update is revalidated and the same Step reaches independent review")
        resumed.work.map(_.kind) shouldBe Some("ReviewStep")
        Files.readString(source) should not include invalidImport
        Files.readString(source).linesIterator.toVector.lastOption shouldBe Some("object RepairExample {}")
        var causes = Vector.empty[String]
        prepare(call => call.goalCurrentC(InstanceIdentity(started.sessionId)).map(current =>
          { causes = current.history.map(_.causation.value); () }))
        causes.count(value => java.util.Objects.equals(value, ScalaMetalsProcess.intentCause)) shouldBe 1
        causes.count(value => java.util.Objects.equals(value, "ordered-scala-metals:result")) shouldBe 1
        causes.count(value => java.util.Objects.equals(value, DeterministicRepair.applyIntentCause)) shouldBe 1
        val composition = take(ThreeWorkflowRuntimeParameters.decodeC(
          ComponentCreate(owner, ComponentOrigin.Builtin), component.initializationParameters, actor))
        val port = take(GoalPhaseNativeWorkContinuation.bindC(composition.goal, take(GoalPhaseRuntimeCatalog.loadC()).definition))
        var observedMetrics = Option.empty[GoalPhasePhaseMetrics.Repairs]
        prepare(call => for {
          catalog <- GoalPhaseRuntimeCatalog.loadC()
          storage <- WorkflowComponentStorage.bindC(ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin),
            SmWorkflowComponent.componentId, composition.goal.configuration, Vector(catalog.definition),
            composition.goal.instances, composition.goal.continuations)
          reader <- storage.bindMechanicalC(GoalPhaseNativeRun.startOperation, GoalPhaseNativeRun.codec)
          retained <- reader.loadRetainedC(GoalPhaseNativeRun.reference(started.sessionId))(using call.core.executionContext)
            .flatMap(_.map(Consequence.success).getOrElse(Consequence.stateConflict("Specification Goal start is absent")))
          session = GoalPhaseNativeRun.Session(storage, retained, retained.receipt.payload.value.participantIdentity, retained.current)
          metrics <- port.deterministicRepairMetricsC(session, call.core)
        } yield { observedMetrics = Some(metrics); () })
        val metrics = observedMetrics.getOrElse(fail("native repair metrics unavailable"))
        val metals = metrics.observations.filter(_.tool.contains("metals"))
        metals.size shouldBe 1
        metals.head.changed shouldBe Some(true)
        metals.head.revalidated shouldBe Some(true)
        metals.head.validation should not be empty
        metals.head.retained shouldBe None
        metals.head.sources should not be empty
        metrics.tools.find(_.tool == "metals").flatMap(_.count) shouldBe None
        causes should not contain "ordered-bounded-compiler:intent"
        Files.readString(root.resolve("metals.trace")).linesIterator.count(value => java.util.Objects.equals(value, "textDocument/codeAction")) shouldBe 1
        Files.readString(root.resolve("metals.trace")) should include ("only:[\"quickfix\"]")
      }
      }
    }

    "admit only the authenticated whole-line invalid-import deletion" in {
      Given("a persisted Scala 2 compiler diagnostic and the exact staged import")
      val material = _metals_material()
      val output = material.diagnostic.message + "\n[error] one error found\n[error] (Compile / compileIncremental) Compilation failed\n"
      val bytes = output.getBytes(UTF_8).toVector
      val check = GoalPhaseNativeValidation.Check("compile", 1, WorkflowSbtValidation.Kind.Compile)
      val process = ProcessExecutionResult(ProcessExecutionTermination.Exited(1),
        ProcessExecutionCapture(bytes, bytes.size.toLong, false), ProcessExecutionCapture(Vector.empty, 0L, false),
        Vector.empty, 1L, "controlled-scala2-compile")
      val receipt = RepositorySyncNativeMergeValidation.Receipt(check.identity, "exited", Some(1), output, "",
        Some(WorkflowSbtValidation.Facts(check.kind, WorkflowSbtValidation.Outcome.Failed, None, None)), Some(process))
      val payload = GoalPhaseNativeValidation.Payload("1", "step-1", GoalPhaseOrderedProgression.Snapshot("b" * 40, material.candidate),
        Vector(check), Vector(receipt), Some("repair-required"))
      compilerLocator(payload).map(finding => (finding.path, finding.line, finding.column)) shouldBe
        Some(("/work/src/main/scala/A.scala", 1, material.diagnostic.range.start.character + 1))
      val diagnostic = ScalaMetalsRepair.productionDiagnostic(material).getOrElse(fail("expected supported compiler evidence"))
      val range = Json.obj("start" -> Json.obj("line" -> Json.fromInt(0), "character" -> Json.fromInt(0)),
        "end" -> Json.obj("line" -> Json.fromInt(1), "character" -> Json.fromInt(0)))
      val action = Json.obj("title" -> Json.fromString("Remove invalid import of 'DoesNotExist'"), "kind" -> Json.fromString("quickfix"),
        "edit" -> Json.obj("changes" -> Json.obj(material.document.uri -> Json.arr(Json.obj("range" -> range, "newText" -> Json.fromString(""))))))
      When("the real allowlist receives the exact upstream action shape")
      val proposal = ScalaMetalsRepair.productionTransaction(material, ScalaMetalsRepair.Policy(), Json.arr(action))
      Then("the pure replay transformation deletes exactly the import and preserves all remaining bytes")
      proposal.map(_.patched) shouldBe Some("object A\n")
      ScalaMetalsRepair.productionRendered(material) shouldBe proposal.map(_.patched)
      diagnostic.message shouldBe "object DoesNotExist is not a member of package scala"
      val selector = material.document.text.indexOf("DoesNotExist")
      diagnostic.range shouldBe Range(Position(0, selector), Position(0, selector + "DoesNotExist".length))
      ScalaMetalsRepair.productionRendered(_metals_material(selector)) shouldBe proposal.map(_.patched)
      When("the title, kind, source, diagnostic or number of eligible actions differs")
      Then("the production gate returns no transaction")
      for (column <- Vector(0, 6, 8, 12, 14, 23)) {
        ScalaMetalsRepair.productionRendered(_metals_material(column)) shouldBe None
      }
      ScalaMetalsRepair.productionRendered(material.copy(diagnostic = material.diagnostic.copy(
        range = Range(Position(0, selector), Position(0, selector))))) shouldBe None
      ScalaMetalsRepair.productionTransaction(material, ScalaMetalsRepair.Policy(), Json.arr(action, action)) shouldBe None
      for ((field, value) <- Vector("title" -> Json.fromString("other"), "kind" -> Json.fromString("refactor.rewrite"),
          "kind" -> Json.fromString("source.organizeImports"), "command" -> Json.obj())) {
        ScalaMetalsRepair.productionTransaction(material, ScalaMetalsRepair.Policy(), Json.arr(action.mapObject(_.add(field, value)))) shouldBe None
      }
      for (source <- Vector(" " + material.document.text, material.document.text.replace("DoesNotExist", "Other"),
          material.document.text.replace("DoesNotExist\n", "DoesNotExist // comment\n"), "/*\n" + material.document.text + "*/\n")) {
        ScalaMetalsRepair.productionRendered(material.copy(document = material.document.copy(text = source))) shouldBe None
      }
      ScalaMetalsRepair.productionRendered(material.copy(diagnostic = material.diagnostic.copy(message =
        material.diagnostic.message.replace("package scala", "package other")))) shouldBe None
      ScalaMetalsRepair.productionRendered(material.copy(document = material.document.copy(revision = "b" * 40))) shouldBe None
    }

    "use the admitted finite-input companion with a controlled LSP process and confirmed cleanup" in {
      for (mode <- Vector("completed", "timeout", "encoding", "framing", "original-uri", "original-legacy-uri", "unowned", "command", "ambiguous", "refactor", "source", "unavailable")) {
        Given("one fixture-owned fixed capability and an isolated subprocess with bounded stdio")
        val parent = Files.createDirectories(Path.of("target/sm-workflow/work/metals-process").toAbsolutePath)
        val root = Files.createTempDirectory(parent, mode + "-").toRealPath()
        val server = root.resolve("metals-fixture-server")
        Files.writeString(server, metalsServerSource)
        server.toFile.setExecutable(true) shouldBe true
        Files.writeString(root.resolve("metals.mode"), mode)
        val material = _metals_material()
        val policy = ScalaMetalsRepair.Policy(timeoutMillis = 1000, serverIdentity = Some(ScalaMetalsProcess.serverIdentity))
        val request = take(ScalaMetalsProcess.requestC(material, policy))
        request.arguments shouldBe empty
        request.workingDirectory shouldBe None
        request.resourceTrees shouldBe empty
        request.inputFiles shouldBe empty
        request.validateC.isSuccess shouldBe true
        var observation = Option.empty[ProcessExecutionResult]
        _with_runtime_(root, "Goal") { (_, _, _, prepare) =>
          if (mode == "unavailable") Files.delete(server)
          When("the owning UoW invokes the production protected process route")
          prepare(call => ScalaMetalsProcess.metalsAttemptC(call.core, request).map {
            case ScalaMetalsProcess.MetalsAttempt.Observed(result) => observation = Some(result)
            case other => fail(other.toString)
          })
        }
        val actual = observation.getOrElse(fail("expected actual process observation"))
        actual.termination shouldBe ProcessExecutionTermination.Exited(0)
        val initial = ScalaMetalsProcess.Attempt(material.session, material.sourceRevision, "step-1", material.candidate,
          material.validationReference, None, None, None, None)
        val outcome = ScalaMetalsProcess.complete(initial, material, policy, actual)
        Then("the retained typed result reports the actual outcome and never admits unsupported edits")
        val expected = mode match {
          case "completed" => "completed"
          case "timeout" => "timed-out"
          case "encoding" | "framing" | "original-uri" | "original-legacy-uri" => "incompatible"
          case "unavailable" => "unavailable"
          case _ => "no-action"
        }
        outcome.outcome shouldBe Some(expected)
        outcome.proposal.map(_.patched) shouldBe Option.when(mode == "completed")("object A\n")
        ScalaMetalsProcess.codec.decode(ScalaMetalsProcess.codec.encode(outcome)) shouldBe Right(outcome)
        if (mode != "unavailable") {
          val trace = Files.readString(root.resolve("metals.trace")).linesIterator.toVector
          val pid = trace.collectFirst { case value if value.startsWith("pid:") => value.stripPrefix("pid:").toLong }
            .getOrElse(fail("expected child pid observation"))
          java.lang.ProcessHandle.of(pid).isPresent && java.lang.ProcessHandle.of(pid).get.isAlive shouldBe false
          val cwd = trace.collectFirst { case value if value.startsWith("cwd:") => Path.of(value.stripPrefix("cwd:")) }
            .getOrElse(fail("expected isolated cwd"))
          cwd should not be root.resolve("repository")
          Files.exists(cwd) shouldBe false
          if (Set("completed", "unowned", "command", "ambiguous", "refactor", "source")(mode)) {
            trace should contain ("only:[\"quickfix\"]")
            trace.takeRight(3) shouldBe Vector("textDocument/didClose", "shutdown", "exit")
          }
        }
      }
    }
  }

  private def _compiler_fixture_reply(request: AiRecordRequest, crossSource: Boolean = false): Record = {
    val packet = io.circe.parser.parse(request.prompt.substring(request.prompt.lastIndexOf('\n') + 1))
      .fold(error => fail(error.message), identity).hcursor
    def _text_(key: String): String = packet.get[String](key).fold(error => fail(error.message), identity)
    packet.get[String]("selectedSource").toOption match {
      case Some(selected) => Record.dataAuto("replacement" -> (if (crossSource) selected else "42"))
      case None =>
        val expected = "\"bad\""
        val (line, row) = _text_("source").linesIterator.zipWithIndex.find((value, _) => value.contains(expected))
          .getOrElse(fail("Expected the failing staged source in the bounded task"))
        val start = line.indexOf(expected)
        def _position_(column: Int): Record = Record.dataAuto("line" -> row, "character" -> column)
        Record.dataAuto("candidate" -> _text_("candidate"), "validationReference" -> _text_("validationReference"),
          "path" -> _text_("path"), "range" -> Record.dataAuto("start" -> _position_(start), "end" -> _position_(start + expected.length)),
          "expectedSource" -> (if (crossSource) "crossed" else expected), "replacement" -> "42")
    }
  }

  "The ordinary bounded compiler repair" should {
    "apply a bound proposal once, hand off a known refusal, and keep uncertainty on the original attempt" in {
      for (mode <- Vector("completed", "blocked", "declined", "rejected", "unbound")) {
        Given("an isolated Phase, a real Compile check, and a fixture provider on the bounded runner socket")
        val parent = Files.createDirectories(Path.of("target/sm-workflow/work/bounded-compiler").toAbsolutePath)
        val root = Files.createTempDirectory(parent, mode + "-").toRealPath()
        val input = V.GoalPhaseStartInput("bounded-compiler-fixture", "sm-workflow")
        val check = GoalPhaseNativeValidation.Check("compile", 1, WorkflowSbtValidation.Kind.Compile)
        val normal = ThreeWorkflowNormalHostFixture.definition(root)
        val host = normal.copy(goal = normal.goal.copy(input = input, candidatePaths = Vector(_compiler_path),
          stepValidation = normal.goal.stepValidation.copy(checks = Vector(check)),
          phaseValidation = normal.goal.phaseValidation.copy(checks = Vector(check))))
        val plan = GoalPhaseOrderedProgression.Plan("fixture-bounded-compiler", "1", input.phaseIdentity,
          input.repositoryIdentity, Vector(GoalPhaseOrderedProgression.Step("step-1", "Compile the isolated Scala source",
            Vector(_compiler_path), Vector.empty, Vector(check.identity))), Vector(check.identity), repairLimit = 4)
        val original = "object RepairExample { val value: Int = \"bad\" }\n"
        val source = root.resolve("repository").resolve(_compiler_path)
        val providerid = ComponentId("org.example.BoundedCompilerFixture")
        var calls = 0
        val runner = new AiRunner {
          def generate(req: AiGenerateRequest)(using ExecutionContext): Consequence[AiGenerateResponse] =
            Consequence.operationInvalid("Unexpected generate")
          def chat(req: AiChatRequest)(using ExecutionContext): Consequence[AiChatResponse] =
            Consequence.operationInvalid("Unexpected chat")
          def generateRecord(req: AiRecordRequest)(using ExecutionContext): Consequence[AiRecordResponse] = {
            calls += 1
            req.metadata.get("ai.task.kind") shouldBe Some("compiler-repair")
            req.requirement.tools shouldBe empty
            if (mode == "blocked") Consequence.operationIllegal("ai.cost-budget", "Fixture declined before dispatch")
            else if (mode == "declined") Consequence.operationIllegal("ai.task.declined", "Fixture has no eligible candidate")
            else {
              val metadata = req.metadata ++ Map("ai.execution.provider" -> "fixture-provider", "ai.usage.total_tokens" -> "21",
                "ai.usage.total_tokens_source" -> "observed", "ai.provider.http.request-id" -> "req_native_compiler_fixture")
              val binding = if (mode == "unbound") metadata.updated("ai.task.id", "other-attempt") else metadata
              val output = _compiler_fixture_reply(req, crossSource = mode == "rejected")
              Consequence.success(AiRecordResponse(output, Some("fixture-model"), binding))
            }
          }
        }
        _with_runtime_(root, "Goal", Some(host)) { (owner, component, actor, prepare) => {
          val socket = component.port.entries.collectFirst { case value: AiRunnerSocketSet => value }.getOrElse {
            val created = new AiRunnerSocketSet {}
            component.withPort(Component.Port.input(created).orElse(component.port))
            created
          }
          socket.installSpiMembers(Vector(ResolvedSpiMember[AiRunner](runner,
            SpiMemberMetadata("ai-runner", providerid.name, ComponentInstanceId.default(providerid),
              capabilities = Set("textus-ai.bounded-task.v1"), isDefault = true))))
          prepare(call => for {
            _ <- call.setupC(_setup_commands_(root, "Goal").size)
            selection = _goal_selection_(input, "fixture-bounded-compiler-selection", "fixture-bounded-compiler-start")
            _ <- call.createC("goal_selection", NativeWorkflowStartBusinessKey(NativeWorkflowStartInput.Goal(input),
              selection.participantIdentity), Record.data(ProfileSelectionJsonV1.storageField ->
              take(ProfileSelectionJsonV1.encodeStorageC(selection))))
            _ <- call.createC("goal_plans", selection.selectionId, Record.data(GoalPhaseSelectedPlanJsonV1.storageField ->
              take(GoalPhaseSelectedPlanJsonV1.encodeStorageC(GoalPhaseNativeRun.SelectedPlan(selection.selectionId,
                selection.participantIdentity, plan)))))
          } yield ())
          def _invoke_[A: ValueReader](operation: String, properties: List[Property]): A =
            read_value[A](take(owner.executeOperationResponseInContext(
              Request.of(component = component.name, service = "GoalPhaseService", operation = operation, properties = properties), actor)))
          val started = _invoke_[V.GoalPhaseStartResult]("startGoalPhase", NativeWorkflowOperationInputFixture.goal(input))
          def _advance_(): V.GoalPhaseAdvanceResult = _invoke_[V.GoalPhaseAdvanceResult]("advanceGoalPhase",
            NativeWorkflowOperationInputFixture.advance(started.sessionId))
          val implementation = _advance_()
          implementation.work.map(_.kind) shouldBe Some("ImplementStep")
          Files.createDirectories(source.getParent)
          Files.writeString(source, original)
          val result = V.GoalPhaseWorkResult("completed", "Fixture failing Scala source", Vector.empty,
            None, None, None, None, None, None, None, None, dependencyValidation = None, diagnosticRepair = None)
          val reference = V.WorkflowArtifactReference("bounded-compiler-fixture-implementation", "1")
          _invoke_[V.GoalPhaseWorkSubmissionResult]("submitGoalPhaseWorkResult", List(Property("sessionId", started.sessionId, None),
            Property("result", result.toRecord(), None), Property("resultReference", reference.toRecord(), None))).accepted shouldBe true
          When("ordinary Advance handles the retained compiler failure and is then called again")
          val first = _advance_()
          val second = _advance_()
          Then("the fixture producer is invoked once and the same Phase retains the actual outcome")
          calls shouldBe 1
          second.sessionId shouldBe started.sessionId
          def _history_(): Vector[String] = {
            var causes = Vector.empty[String]
            prepare(call => call.goalCurrentC(InstanceIdentity(started.sessionId)).map(current =>
              { causes = current.history.map(_.causation.value); () }))
            causes
          }
          _history_().count((cause: String) => (cause: String) == ("ordered-bounded-compiler:intent": String)) shouldBe 1
          _history_().count((cause: String) => (cause: String) == ("ordered-bounded-compiler:result": String)) shouldBe 1
          if (mode == "completed") {
            second.work.map(_.kind) shouldBe Some("ReviewStep")
            Files.readString(source).linesIterator.toVector.lastOption shouldBe Some(original.trim.replace("\"bad\"", "42"))
            _history_().count((cause: String) => (cause: String) == (DeterministicRepair.applyIntentCause: String)) shouldBe 1
          } else {
            Files.readString(source).linesIterator.toVector.lastOption shouldBe Some(original.trim)
            _history_() should not contain (DeterministicRepair.applyIntentCause)
            if (Set("blocked", "declined", "rejected")(mode)) second.work.map(_.kind) shouldBe Some("RepairStep")
            else {
              first.kind shouldBe "Wait"
              second.kind shouldBe "Wait"
              second.work shouldBe None
            }
          }
        }}
      }
    }
  }

  "The native Circe repair" should {
    "revalidate a real compiler correction and restore it after an independent Test failure" in {
      Vector(true, false).foreach { passes =>
        val scenario = F.fresh("circe-native-" + (if (passes) "pass" else "rollback"),
          sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable),
          repairContinuation = true,
          selectedGoalPaths = Some(Vector("src/test/scala/RepairExample.scala", "build.sbt", "value.txt", "docs/phase.md")))
        val repository = Path.of(scenario.host.target.root)
        val source = repository.resolve("src/test/scala/RepairExample.scala")
        val original = """object RepairExample {
          |  def fail(message: String): String = throw new IllegalArgumentException(message)
          |  def render(value: Either[io.circe.Error, String]): String = value
          |    .fold(error => fail(error.message), identity)
          |}
          |""".stripMargin
        val circe = Path.of(classOf[io.circe.Error].getProtectionDomain.getCodeSource.getLocation.toURI).toRealPath()
        val quoted = io.circe.Json.fromString(circe.toString).noSpaces
        val task = scenario.ownTask("circe-repair-task")
        val started = scenario.start().business[V.GoalPhaseStartResult]
        scenario.retain(task, started)
        val implementation = scenario.readGoalPhaseResult(scenario.advance(task))
        implementation.work.getOrElse(fail("expected implementation work")).kind shouldBe "ImplementStep"
        Files.writeString(source, original)
        Files.writeString(repository.resolve("build.sbt"), s"""scalaVersion := "3.3.8"
          |Test / unmanagedClasspath += Attributed.blank(file($quoted))
          |Test / test := (Test / runMain).toTask(" ProductCheck").value
          |""".stripMargin)
        Files.writeString(repository.resolve("value.txt"), if (passes) "expected\n" else "incorrect\n")
        val result = V.GoalPhaseWorkResult("completed", "Fixture inputs for actual native validation", Vector.empty,
          None, None, None, None, None, None, None, None, dependencyValidation = None, diagnosticRepair = None)
        val reference = V.WorkflowArtifactReference("circe-fixture-implementation", "1")
        scenario.call("command", implementation.submitOperation.getOrElse(fail("expected submit operation")), Vector("--sessionId", implementation.submitSessionId.getOrElse(fail("expected submit session")),
          "--result", NativeCallerExecutionFixture.json(result.toRecord()).noSpaces,
          "--resultReference", NativeCallerExecutionFixture.json(reference.toRecord()).noSpaces))
          .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
        val advance = scenario.advance(task)
        val control = advance.business[V.GoalPhaseAdvanceResult]
        val next = scenario.readGoalPhaseResult(advance)
        def authoredSource: String = {
          val managedHeader = raw"(?s)\A/\*\*\n \* @since   ([A-Z][a-z]{2}\.\s+\d{1,2}, \d{4})\n \* @version \1\n \*/\n(.*)\z".r
          Files.readString(source) match {
            case managedHeader(_, body) => body
            case _ => fail("expected the canonical initial managed source history header")
          }
        }
        if (passes) {
          next.work.getOrElse(fail("expected review work")).kind shouldBe "ReviewStep"
          val validation = control.controlSummary.flatMap(_.getRecord("validation"))
          validation.flatMap(_.getString("outcome")) shouldBe Some("passed")
          val sourceRevision = validation.flatMap(_.getString("source_revision"))
          sourceRevision should not be empty
          next.work.getOrElse(fail("expected review work")).checksReceipt shouldBe sourceRevision.map { revision =>
            s"${started.sessionId}:ordered-validation:$revision:validation"
          }
          authoredSource shouldBe original.replace("error.message", "error.getMessage")
          Files.readString(scenario.root.resolve("sbt-executions.txt")).linesIterator.size shouldBe 1
        } else {
          (next.work.exists(work => "RepairStep".equals(work.kind)) || "Human".equals(next.kind)) shouldBe true
          authoredSource shouldBe original
          Files.readString(scenario.root.resolve("sbt-executions.txt")).linesIterator.size shouldBe 1
        }
      }
    }
  }
  "Metals proposal admission" should {
    "project only one exact complete typed compiler failure" in {
      val source = "            .fold(error => fail(error.message), identity)"
      val column = source.indexOf(".message") + 1
      val output = Vector(
        s"[error] -- [E008] Not Found Error: /work/src/A.scala:395:$column ",
        s"[error] 395 |$source", "[error]     |                                ^^^^^^^^^^^^^",
        "[error]     |                       value message is not a member of io.circe.Error",
        "[error] one error found", "[error] (Test / compileIncremental) Compilation failed").mkString("\n") + "\n"
      val check = GoalPhaseNativeValidation.Check("compile", 1, WorkflowSbtValidation.Kind.Compile)
      def payload(text: String, truncated: Boolean = false): GoalPhaseNativeValidation.Payload = {
        val bytes = text.getBytes(UTF_8).toVector
        val process = ProcessExecutionResult(ProcessExecutionTermination.Exited(1),
          ProcessExecutionCapture(bytes, bytes.size.toLong, truncated), ProcessExecutionCapture(Vector.empty, 0L, false),
          Vector.empty, 1L, "fixture-compile")
        val receipt = RepositorySyncNativeMergeValidation.Receipt(check.identity, "exited", Some(1), text, "",
          Some(WorkflowSbtValidation.Facts(check.kind, WorkflowSbtValidation.Outcome.Failed, None, None)), Some(process))
        GoalPhaseNativeValidation.Payload("1", "step", GoalPhaseOrderedProgression.Snapshot("a" * 40, "b" * 40),
          Vector(check), Vector(receipt), Some("repair-required"))
      }
      val original = payload(output)
      val expected = DeterministicCompilerFinding("compile", "/work/src/A.scala", 395, column, source)
      deterministicCompilerFinding(original) shouldBe Some(expected)
      compilerPatternDisposition(original) shouldBe Some(true)
      compilerPatternDisposition(payload(output, truncated = true)) shouldBe None
      compilerPatternDisposition(payload(output.replace(s":395:$column", s":395:${column + 1}"))) shouldBe Some(false)
      compilerLocator(original).map(x => (x.checkIdentity, x.path, x.line, x.column)) shouldBe
        Some(("compile", "/work/src/A.scala", 395, column))
      compilerLocator(original).map(_.message.contains("value message is not a member")) shouldBe Some(true)
      compilerLocator(original.copy(outcome = Some("repair-limit-reached"))) shouldBe None
      compilerLocator(payload(output, truncated = true)) shouldBe None
      compilerLocator(payload(output + output)) shouldBe None
      compilerLocator(original.copy(receipts = original.receipts.map(_.copy(identity = "another-check")))) shouldBe None
      val footer = "[error] Total time: 1 s, completed 2026/11/22 20:11:04\n"
      deterministicCompilerFinding(payload(output + footer)) shouldBe Some(expected)
      deterministicCompilerFinding(payload(output + footer + "[error] another error\n")) shouldBe None
      deterministicCompilerFinding(payload(output + footer + footer)) shouldBe None
      deterministicCompilerFinding(payload(footer + output)) shouldBe None
      deterministicCompilerFinding(original.copy(outcome = Some("repair-limit-reached"))) shouldBe Some(expected)
      deterministicCompilerFinding(payload(output, truncated = true)) shouldBe None
      deterministicCompilerFinding(payload(output + "[error] another error\n")) shouldBe None
      deterministicCompilerFinding(payload(output + "[info] - other *** FAILED ***\n")) shouldBe None
      deterministicCompilerFinding(payload(output.replace("one error found", "two errors found"))) shouldBe None
      deterministicCompilerFinding(payload(output.replace(s":395:$column", s":395:${column + 1}"))) shouldBe None
      deterministicCompilerFinding(original.copy(outcome = Some("passed"))) shouldBe None
      val receipt = original.receipts match {
        case Vector(value) => value
        case _ => fail("expected exactly one fixture receipt")
      }
      val changed = receipt.copy(stdout = output + "changed")
      deterministicCompilerFinding(original.copy(receipts = Vector(changed))) shouldBe None
    }
    "bind a closed Record replacement to the trusted compiler material" in {
      Given("an owned source and the diagnostic from its failed check")
      val document = Document("file:///work/src/main/scala/A.scala", "b" * 40, 0, "object A { val x = missing }\n")
      val diagnostic = Diagnostic("scala-compiler", document.uri, document.revision, 0, "compiler-error",
        Range(Position(0, 19), Position(0, 19)), "Not found: missing")
      val material = CompilerMaterial("session", "12", document.revision, "11:compile", "src/main/scala/A.scala",
        "sm-workflow-repository", document, diagnostic)
      val task = GoalPhaseApiReview.compilerTask(material, "attempt", "continuation", "retained:validation", 8192)
        .getOrElse(fail("Expected a bounded compiler task"))
      task.kind shouldBe "compiler-repair"
      task.binding("ai.task.input-reference") shouldBe "retained:validation"
      task.prompt should include ("Not found: missing")
      task.prompt should not include document.uri
      GoalPhaseApiReview.compilerTask(material, "attempt", "continuation", "retained:validation", 64) shouldBe None
      val reply = Json.obj("candidate" -> Json.fromString(material.candidate),
        "validationReference" -> Json.fromString(material.validationReference), "path" -> Json.fromString(material.path),
        "range" -> Json.obj("start" -> Json.obj("line" -> Json.fromInt(0), "character" -> Json.fromInt(19)),
          "end" -> Json.obj("line" -> Json.fromInt(0), "character" -> Json.fromInt(26))),
        "expectedSource" -> Json.fromString("missing"), "replacement" -> Json.fromString("present"))
      When("the closed response is projected without applying it")
      val proposed = compilerReplyC(material, GoalPhaseNativeMetadata.record(reply)).toOption
      Then("the exact preimage and rendered replacement are retained")
      proposed.map(_.expected) shouldBe Some(document.text)
      proposed.map(_.patched) shouldBe Some("object A { val x = present }\n")
      compilerReply(material, reply.deepMerge(Json.obj("path" -> Json.fromString("src/main/scala/B.scala")))).isLeft shouldBe true
      compilerReply(material, reply.deepMerge(Json.obj("candidate" -> Json.fromString("c" * 40)))).isLeft shouldBe true
      compilerReply(material, reply.deepMerge(Json.obj("validationReference" -> Json.fromString("other")))).isLeft shouldBe true
      compilerReply(material, reply.deepMerge(Json.obj("expectedSource" -> Json.fromString("other")))).isLeft shouldBe true
      compilerReply(material, reply.deepMerge(Json.obj("extra" -> Json.fromBoolean(true)))).isLeft shouldBe true
    }
    "use a compiler-selected span only when the location, source echo and caret agree" in {
      val source = "object A { val x = missing }"
      val document = Document("file:///work/src/main/scala/A.scala", "b" * 40, 0, source + "\n")
      val start = Position(0, source.indexOf("missing"))
      val span = Range(start, Position(0, start.character + "missing".length))
      val header = s"[error] -- [E006] Not Found Error: /work/src/main/scala/A.scala:1:${start.character}"
      val caret = "[error]   |" + (" " * start.character) + "^^^^^^^"
      val point = start.copy(character = start.character - 1)
      val diagnostic = Diagnostic("scala-compiler", document.uri, document.revision, 0, "compiler-error",
        Range(point, point), Vector(header, "[error] 1 |" + source, caret).mkString("\n"))
      val material = CompilerMaterial("session", "12", document.revision, "11:compile", "src/main/scala/A.scala",
        "sm-workflow-repository", document, diagnostic)
      compilerDiagnosticSelection(material) shouldBe Some(span -> "missing")
      val reply = Json.obj("replacement" -> Json.fromString("present"))
      compilerReply(material, reply).toOption.map(_.patched) shouldBe Some("object A { val x = present }\n")
      compilerReply(material, reply.mapObject(_.add("path", Json.fromString("other.scala")))).isLeft shouldBe true
      compilerReply(material, Json.obj("replacement" -> Json.fromString("missing"))).isLeft shouldBe true
      val shifted = diagnostic.copy(message = diagnostic.message.replace(caret, caret.replace("|", "| ")))
      compilerDiagnosticSelection(material.copy(diagnostic = shifted)) shouldBe None
      val crossed = material.copy(document = document.copy(text = source.replace("missing", "changed") + "\n"))
      compilerDiagnosticSelection(crossed) shouldBe None
      compilerReply(crossed, reply).isLeft shouldBe true
    }
    "retain only bounded exact transaction records without inferring a result" in {
      val transaction = Transaction("session", "12", "a" * 40, "validation:1", "src/A.scala", "old", "new", "sm-workflow-repository")
      val intent = TransactionRecord(transaction, "apply", None)
      transactionCodec.decode(transactionCodec.encode(intent)) shouldBe Right(intent)
      val applied = intent.copy(outcome = Some("updated"))
      transactionCodec.decode(transactionCodec.encode(applied)) shouldBe Right(applied)
      val conflict = intent.copy(outcome = Some("conflict"))
      transactionCodec.decode(transactionCodec.encode(conflict)) shouldBe Right(conflict)
      transactionCodec.decode(transactionCodec.encode(intent.copy(outcome = Some("success")))).isLeft shouldBe true
      validTransaction(transaction.copy(candidate = "c" * 64)) shouldBe true
      Vector("", "a" * 39, "A" * 40, "g" * 40, "a" * 65).foreach { invalid =>
        validTransaction(transaction.copy(candidate = invalid)) shouldBe false
        transactionCodec.decode(transactionCodec.encode(intent.copy(transaction = transaction.copy(candidate = invalid)))).isLeft shouldBe true
      }
      validTransaction(transaction.copy(path = "../outside.scala")) shouldBe false
      validTransaction(transaction.copy(expected = "new")) shouldBe false
      validTransaction(transaction.copy(expected = "x" * (1024 * 1024 + 1))) shouldBe false
      validTransaction(transaction.copy(sourceRevision = "0")) shouldBe false
      validTransaction(transaction.copy(workspace = " sm-workflow-repository")) shouldBe false
      val rollback = Rollback("28", "b" * 64, "28:test-only")
      val reverse = TransactionRecord(transaction, "rollback", None, Some(rollback))
      transactionCodec.decode(transactionCodec.encode(reverse)) shouldBe Right(reverse)
      Vector("", "b" * 39, "B" * 40, "g" * 40, "b" * 65).foreach { invalid =>
        transactionCodec.decode(transactionCodec.encode(reverse.copy(rollback = Some(rollback.copy(
          failedCandidate = invalid))))).isLeft shouldBe true
      }
      transactionCodec.decode(transactionCodec.encode(reverse.copy(rollback = Some(rollback.copy(
        failedCandidate = "b" * 40))))).isRight shouldBe true
      transactionCodec.decode(transactionCodec.encode(reverse.copy(rollback = None))).isLeft shouldBe true
      transactionCodec.decode(transactionCodec.encode(reverse.copy(rollback = Some(rollback.copy(
        sourceRevision = transaction.sourceRevision))))).isLeft shouldBe true
      transactionCodec.decode(transactionCodec.encode(reverse.copy(rollback = Some(rollback.copy(
        failedCandidate = transaction.candidate))))).isLeft shouldBe true
    }
    "admit only an owned candidate-bound compiler replacement near its diagnostic" in {
      Given("a candidate document and one compiler diagnostic")
      val document = Document("file:///work/A.scala", "tree-1", 7, "val name = old\n" + ("x" * 5000))
      val range = Range(Position(0, 11), Position(0, 14))
      val diagnostic = Diagnostic("scala-compiler", document.uri, document.revision, document.version,
        "E1", range, "invalid name")
      val action = Action("scala-compiler", "replacement", diagnostic,
        Vector(Edit(document.uri, document.version, range, "new")))
      When("the exact observed source is replaced")
      val result = compilerReplacement(action, document, Set(document.uri), "old")
      Then("the pure rendering changes only that span")
      result.flatMap(render(_, document)) shouldBe Right(document.text.replace("old", "new"))
      When("the candidate, expected source, ownership, or edit is invalid")
      Then("no replacement is admitted")
      compilerReplacement(action, document.copy(revision = "tree-2"), Set(document.uri), "old").isLeft shouldBe true
      compilerReplacement(action, document.copy(version = 8), Set(document.uri), "old").isLeft shouldBe true
      compilerReplacement(action, document.copy(uri = "file:///work/B.scala"), Set(document.uri), "old").isLeft shouldBe true
      compilerReplacement(action, document, Set.empty, "old").isLeft shouldBe true
      compilerReplacement(action, document, Set(document.uri), "other").isLeft shouldBe true
      compilerReplacement(action, document, Set(document.uri), "").isLeft shouldBe true
      compilerReplacement(action.copy(edits = Vector(action.edits.headOption.getOrElse(fail("expected one edit")).copy(replacement = "old"))), document, Set(document.uri), "old").isLeft shouldBe true
      compilerReplacement(action.copy(edits = Vector(action.edits.headOption.getOrElse(fail("expected one edit")).copy(replacement = "\u0000"))), document, Set(document.uri), "old").isLeft shouldBe true
      compilerReplacement(action.copy(edits = Vector(action.edits.headOption.getOrElse(fail("expected one edit")).copy(replacement = 0xd800.toChar.toString))), document, Set(document.uri), "old").isLeft shouldBe true
      val outside = Range(Position(0, 11), Position(0, 100))
      compilerReplacement(action.copy(edits = Vector(action.edits.headOption.getOrElse(fail("expected one edit")).copy(range = outside))), document, Set(document.uri), "old").isLeft shouldBe true
      val unrelated = Range(Position(1, 4990), Position(1, 4991))
      compilerReplacement(action.copy(edits = Vector(action.edits.headOption.getOrElse(fail("expected one edit")).copy(range = unrelated))), document, Set(document.uri), "x").isLeft shouldBe true
      compilerReplacement(action.copy(edits = action.edits ++ action.edits), document, Set(document.uri), "old").isLeft shouldBe true
    }
    "propose the observed Circe compiler correction only for the exact bound shape" in {
      Given("the Scala 3 E008 receiver evidence and a handwritten snapshot")
      val line = "  .fold(error => fail(error.message), identity)"
      val column = line.indexOf("message")
      val document = Document("file:///work/A.scala", "r1", 7, line + "\n")
      val range = Range(Position(0, column), Position(0, column + 7))
      val diagnostic = Diagnostic("scala-compiler", document.uri, "r1", 7, "E008", range,
        "value message is not a member of io.circe.Error")
      val finding = CirceMessageFinding(diagnostic, "receipt:compiler", 3, "io.circe.Error", handwritten = true)
      When("the recipe admits the exact member replacement")
      val result = circeErrorMessage(finding, document, Set(document.uri))
      Then("the patch retains the compiler finding and distinguishes native patch provenance")
      result.map(_.action.edits) shouldBe Right(Vector(Edit(document.uri, 7, range, "getMessage")))
      result.flatMap(render(_, document)) shouldBe Right(line.replace("error.message", "error.getMessage") + "\n")
      result.flatMap(render(_, document.copy(text = document.text + " "))).isLeft shouldBe true
      result.flatMap(render(_, document.copy(revision = "r2"))).isLeft shouldBe true
      result.toOption.flatMap(_.action.nativeProvenance) shouldBe Some(NativeProvenance(
        "circe-error-get-message-v1", "receipt:compiler", Some(3), Some("io.circe.Error")))
      When("the compiler, source, range or authority is not the one observed")
      Then("the recipe fails closed")
      circeErrorMessage(finding, document, Set.empty).isLeft shouldBe true
      circeErrorMessage(finding.copy(handwritten = false), document, Set(document.uri)).isLeft shouldBe true
      circeErrorMessage(finding.copy(scalaMajor = 2), document, Set(document.uri)).isLeft shouldBe true
      circeErrorMessage(finding.copy(receiverType = "unknown"), document, Set(document.uri)).isLeft shouldBe true
      circeErrorMessage(finding.copy(diagnostic = diagnostic.copy(code = "E007")), document, Set(document.uri)).isLeft shouldBe true
      circeErrorMessage(finding.copy(diagnostic = diagnostic.copy(producer = "metals")), document, Set(document.uri)).isLeft shouldBe true
      circeErrorMessage(finding, document.copy(revision = "r2"), Set(document.uri)).isLeft shouldBe true
      circeErrorMessage(finding.copy(diagnostic = diagnostic.copy(range = Range(Position(0, 0), Position(0, 7)))), document, Set(document.uri)).isLeft shouldBe true
      circeErrorMessage(finding, document.copy(text = line.replace("error.message", "other.message") + "\n"), Set(document.uri)).isLeft shouldBe true
      val comment = document.copy(text = "/*\n" + line + "\n*/")
      val embedded = finding.copy(diagnostic = diagnostic.copy(range = Range(Position(1, column), Position(1, column + 7))))
      circeErrorMessage(embedded, comment, Set(document.uri)).isLeft shouldBe true
      val literal = document.copy(text = "\"\"\"\n" + line + "\n\"\"\"")
      circeErrorMessage(embedded, literal, Set(document.uri)).isLeft shouldBe true
      And("the current Circe API exposes getMessage but not message")
      assertDoesNotCompile("""val error: io.circe.Error = ???; error.message""")
      assertCompiles("""val error: io.circe.Error = ???; error.getMessage""")
    }
    "propose only the extra terminal LF identified by a trusted native finding" in {
      Given("an owned handwritten Scala snapshot and a revision-bound review finding")
      val document = Document("file:///work/A.scala", "r1", 7, "object A\n\n\n")
      val range = Range(Position(1, 0), Position(3, 0))
      val diagnostic = Diagnostic("phase-review", document.uri, document.revision, document.version,
        "terminal-lf", range, "extra terminal LF")
      val finding = TerminalLfFinding(diagnostic, "receipt:1", handwritten = true)
      When("the native recipe constructs and admits its bounded proposal")
      val result = terminalLf(finding, document, Set(document.uri))
      Then("the patch preserves the diagnostic and attributes the built-in recipe separately")
      result.map(_.action.edits) shouldBe Right(Vector(Edit(document.uri, 7, range, "")))
      result.flatMap(render(_, document)) shouldBe Right("object A\n")
      result.toOption.flatMap(_.action.nativeProvenance) shouldBe Some(NativeProvenance("terminal-lf-v1", "receipt:1"))
      When("ownership, provenance, source shape or diagnostic binding differs")
      Then("no patch is admitted")
      terminalLf(finding, document, Set.empty).isLeft shouldBe true
      terminalLf(finding.copy(handwritten = false), document, Set(document.uri)).isLeft shouldBe true
      terminalLf(finding.copy(evidenceReference = ""), document, Set(document.uri)).isLeft shouldBe true
      terminalLf(finding.copy(diagnostic = diagnostic.copy(producer = "metals")), document, Set(document.uri)).isLeft shouldBe true
      terminalLf(finding.copy(diagnostic = diagnostic.copy(range = Range(Position(0, 0), Position(0, 1)))), document, Set(document.uri)).isLeft shouldBe true
      terminalLf(finding, document.copy(revision = "r2"), Set(document.uri)).isLeft shouldBe true
      terminalLf(finding, document.copy(text = "\n\n"), Set(document.uri)).isLeft shouldBe true
      terminalLf(finding, document.copy(text = "object A\u0000\n\n\n"), Set(document.uri)).isLeft shouldBe true
      terminalLf(finding, document.copy(text = "object A\n"), Set(document.uri)).isLeft shouldBe true
    }
    "exchange and attest a versioned edit while ignoring command alternatives" in {
      Given("an owned snapshot and a fixture-only rule for an exact diagnostic and edit")
      val document = Document("file:///work/A.scala", "r1", 7, "abc")
      val range = Range(Position(0, 0), Position(0, 1))
      val diagnostic = Diagnostic("metals", document.uri, "r1", 7, "E1", range, "example")
      val rule = new Rule {
        def accepts(document: Document, diagnostic: Diagnostic, action: Action): Boolean =
          diagnostic.code.equals("E1") && diagnostic.message.equals("example") && document.text.equals("abc") &&
            action.producer.equals("metals") && action.edits.equals(Vector(Edit(document.uri, 7, range, "x")))
      }
      val jsonrange = Json.obj("start" -> Json.obj("line" -> Json.fromInt(0), "character" -> Json.fromInt(0)),
        "end" -> Json.obj("line" -> Json.fromInt(0), "character" -> Json.fromInt(1)))
      val edit = Json.obj("range" -> jsonrange, "newText" -> Json.fromString("x"))
      val action = Json.obj("title" -> Json.fromString("fix"), "edit" -> Json.obj("documentChanges" -> Json.arr(Json.obj(
        "textDocument" -> Json.obj("uri" -> Json.fromString(document.uri), "version" -> Json.fromInt(7)), "edits" -> Json.arr(edit)))))
      var calls = Vector.empty[String]
      var response = Json.arr(Json.obj("title" -> Json.fromString("command"), "command" -> Json.obj()), action)
      val channel = new ScalaMetalsRepair.Transport {
        def request(method: String, params: Json, timeoutMillis: Int): Either[String, Json] = {
          calls = calls :+ method
          method match {
            case "initialize" => Right(Json.obj("capabilities" -> Json.obj("codeActionProvider" -> Json.True)))
            case "textDocument/codeAction" => Right(response)
            case "shutdown" => Right(Json.Null)
            case _ => Left("unexpected")
          }
        }
        def notify(method: String, params: Json): Either[String, Unit] = { calls = calls :+ method; Right(()) }
      }
      When("the adapter negotiates and receives the proposals")
      val result = ScalaMetalsRepair.actions(ScalaMetalsRepair.Policy(), Some(channel), "file:///work", document, diagnostic, Set(document.uri), Vector(rule))
      Then("only the attested snapshot-bound patch is returned and the lifecycle is closed")
      result match {
        case ScalaMetalsRepair.Available(patch) => patch.document shouldBe document
        case other => fail(other.toString)
      }
      calls shouldBe Vector("initialize", "initialized", "textDocument/didOpen", "textDocument/codeAction", "textDocument/didClose", "shutdown", "exit")
      When("competing actions, a crossed version, or a projection limit is returned")
      response = Json.arr(action, action)
      ScalaMetalsRepair.actions(ScalaMetalsRepair.Policy(), Some(channel), "file:///work", document, diagnostic, Set(document.uri), Vector(rule)) shouldBe ScalaMetalsRepair.Unavailable("competing admissible actions")
      ScalaMetalsRepair.actions(ScalaMetalsRepair.Policy(maxActions = 1), Some(channel), "file:///work", document, diagnostic, Set(document.uri), Vector(rule)).isInstanceOf[ScalaMetalsRepair.Malformed] shouldBe true
      response = Json.arr(action.deepMerge(Json.obj("edit" -> Json.obj("documentChanges" -> Json.arr(Json.obj(
        "textDocument" -> Json.obj("uri" -> Json.fromString(document.uri), "version" -> Json.fromInt(8)), "edits" -> Json.arr(edit)))))))
      ScalaMetalsRepair.actions(ScalaMetalsRepair.Policy(), Some(channel), "file:///work", document, diagnostic, Set(document.uri), Vector(rule)) shouldBe ScalaMetalsRepair.NoAction
      Then("none produces a patch")
      When("the owned selection is stale")
      calls = Vector.empty
      ScalaMetalsRepair.actions(ScalaMetalsRepair.Policy(), Some(channel), "file:///work", document.copy(revision = "r2"), diagnostic, Set(document.uri), Vector(rule)).isInstanceOf[ScalaMetalsRepair.Malformed] shouldBe true
      Then("no transport call occurs")
      calls shouldBe empty
    }
    "reject stale, unowned, overlapping and invalid UTF-16 proposals" in {
      Given("a selected document and an attesting fixture rule")
      val document = Document("file:///work/A.scala", "r", 1, "😀")
      val range = Range(Position(0, 0), Position(0, 2))
      val diagnostic = Diagnostic("metals", document.uri, "r", 1, "E", range, "m")
      val action = Action("metals", "fixture", diagnostic, Vector(Edit(document.uri, 1, range, "x")))
      val rule = new Rule { def accepts(document: Document, diagnostic: Diagnostic, action: Action): Boolean = true }
      When("the ownership, revision or ranges are invalid")
      Then("admission fails")
      admit(action, document, Set.empty, Vector(rule), Limits(2, 10)).isLeft shouldBe true
      admit(action, document.copy(revision = "other"), Set(document.uri), Vector(rule), Limits(2, 10)).isLeft shouldBe true
      admit(action.copy(edits = action.edits ++ action.edits), document, Set(document.uri), Vector(rule), Limits(2, 10)).isLeft shouldBe true
      val split = Range(Position(0, 1), Position(0, 2))
      admit(action.copy(edits = Vector(Edit(document.uri, 1, split, "x"))), document, Set(document.uri), Vector(rule), Limits(2, 10)).isLeft shouldBe true
    }
    "report unavailable and reject malformed configuration" in {
      Given("a selected document without a transport")
      val document = Document("file:///work/A.scala", "r", 1, "a")
      val diagnostic = Diagnostic("metals", document.uri, "r", 1, "E", Range(Position(0, 0), Position(0, 1)), "m")
      When("the service is absent or disabled")
      Then("neither is an actionable result")
      ScalaMetalsRepair.actions(ScalaMetalsRepair.Policy(), None, "file:///work", document, diagnostic, Set(document.uri), Vector.empty) shouldBe ScalaMetalsRepair.Unavailable("transport unavailable")
      ScalaMetalsRepair.actions(ScalaMetalsRepair.Policy(enabled = false), None, "file:///work", document, diagnostic, Set(document.uri), Vector.empty) shouldBe ScalaMetalsRepair.Unavailable("disabled")
      WorkflowProjectDeclaration.metalsPolicyC(Json.obj()).toOption.nonEmpty shouldBe true
      WorkflowProjectDeclaration.metalsPolicyC(Json.obj("validation" -> Json.obj("metals" -> Json.Null))).toOption.nonEmpty shouldBe false
      WorkflowProjectDeclaration.metalsPolicyC(Json.obj("validation" -> Json.obj("metals" -> Json.obj("unexpected" -> Json.True)))).toOption.nonEmpty shouldBe false
    }
  }

  "Packaged Metals repair" should {
    "repair real Scala 2 and 3 invalid imports and reject an ineligible action" in {
      val car = Path.of(Files.readString(Path.of("target/sm-workflow/packaged-test-car.txt")).trim).toAbsolutePath.normalize
      val command = F.candidateCommand(car)
      val installed = F.PackagedInstalled(Path.of(command.headOption.getOrElse(fail("Candidate command is empty"))), car, Some(command(2)))
      Vector((false, "completed"), (true, "completed"), (false, "refactor"),
        (false, "source"), (false, "unavailable")).foreach { (scala3, mode) =>
        val scenario = F.freshPackagedMetals(car, scala3, metalsServerSource, mode)
        scenario.selectPackagedInstalled(installed)
        val prepared = scenario.prepareHostInput("prepareGoalStart", scenario.selectedGoalPreparationInput.toRecord())
        withClue(prepared.invocation.logs) { prepared.exitCode shouldBe 0 }
        scenario.selectPreparedImport(prepared)
        val task = scenario.ownTask("packaged-metals")
        val started = scenario.start().business[V.GoalPhaseStartResult]
        scenario.retain(task, started)
        val issued = scenario.readGoalPhaseResult(scenario.advance(task))
        issued.work.map(_.kind) shouldBe Some("ImplementStep")
        val source = Path.of(scenario.host.target.root).resolve("src/main/scala/RepairExample.scala")
        Files.createDirectories(source.getParent)
        val invalid = if (scala3) "import MissingPackage.Member" else "import scala.DoesNotExist"
        Files.writeString(source, invalid + "\nobject RepairExample {}\n")
        Files.writeString(Path.of(scenario.host.target.root).resolve("project.yaml"),
          "validation:\n  metals:\n    enabled: true\n    serverIdentity: scala-metals-1-5-3\n    timeoutMillis: 1000\n")
        val result = V.GoalPhaseWorkResult("completed", "Fixture invalid import", Vector.empty,
          None, None, None, None, None, None, None, None, dependencyValidation = None, diagnosticRepair = None)
        val reference = V.WorkflowArtifactReference("packaged-metals-implementation", "1")
        val operation = issued.submitOperation.getOrElse(fail("Issued work has no submission operation"))
        val session = issued.submitSessionId.getOrElse(fail("Issued work has no submission session"))
        val submitted = scenario.call("command", operation, Vector("--sessionId", session,
          "--result", NativeCallerExecutionFixture.json(result.toRecord()).noSpaces,
          "--resultReference", NativeCallerExecutionFixture.json(reference.toRecord()).noSpaces))
        submitted.business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
        val first = scenario.readGoalPhaseResult(scenario.advance(task))
        val finalResult = if (first.work.exists(_.kind == "ReviewStep") || first.work.exists(_.kind == "RepairStep")) first
          else scenario.readGoalPhaseResult(scenario.advance(task))
        val trace = scenario.root.resolve("metals.trace")
        if (mode == "unavailable") Files.exists(trace) shouldBe false
        else Files.readString(trace) should include ("only:[\"quickfix\"]")
        if (mode == "completed") {
          finalResult.work.map(_.kind) shouldBe Some("ReviewStep")
          Files.readString(source) shouldBe "object RepairExample {}\n"
        } else {
          finalResult.work.map(_.kind) shouldBe Some("RepairStep")
          Files.readString(source) should include (invalid)
        }
      }
    }
  }

  /** The fixture supplies the same ordinary review and closure evidence that
    * an installed caller receives; no private workflow state is written. */
  private def _terminal_scalafix_fixture(scenario: F.Scenario, task: Path): V.GoalPhaseAdvanceResult = {
    def save_(label: String, record: Record): String = {
      val path = Files.createTempFile(scenario.root, label, ".json")
      Files.writeString(path, take(record.toJsonStringC))
      path.toUri.toASCIIString
    }
    def import_(operation: String, input: Record): Unit = {
      val prepared = scenario.prepareHostInput(operation, input)
      withClue(prepared.invocation.logs) { prepared.exitCode shouldBe 0 }
      scenario.selectPreparedImport(prepared)
    }
    var terminal = Option.empty[V.GoalPhaseAdvanceResult]
    var iteration = 0
    val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(15)
    while (terminal.isEmpty && iteration < 20 && System.nanoTime() < deadline) {
      val output = scenario.advance(task)
      val state = scenario.readGoalPhaseResult(output)
      state.work.map(_.kind) match {
        case Some(kind) if Set("ReviewStep", "ReviewPhase")(kind) =>
          val result = V.GoalPhaseWorkResult("accept", "Independent fixture accepts the actual candidate", Vector.empty,
            None, None, None, None, None, None, None, None, dependencyValidation = None, diagnosticRepair = None)
          val reference = V.WorkflowArtifactReference("scalafix-terminal-" + kind + "-" + iteration, "1")
          val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("scalafix-independent-reviewer"), None, None)
          val actual = output.business[V.GoalPhaseAdvanceResult]
          val request = actual.retainedWork.map(_.resource).getOrElse(output.invocation.stdout.toUri.toASCIIString)
          val preparation = V.WorkflowEvidencePreparationInput("GoalPhase", "review", request,
            save_("review", result.toRecord()), save_("review-reference", reference.toRecord()),
            save_("review-execution", execution.toRecord()), "normal-development-host", java.time.Instant.now().toString,
            "entity:smworkflow.cli.host_evidence", None)
          import_("prepareEvidence", preparation.toRecord())
          val submitted = scenario.call("command", state.submitOperation.getOrElse(fail("Missing review submit")), Vector(
            "--sessionId", state.submitSessionId.getOrElse(fail("Missing review session")),
            "--result", NativeCallerExecutionFixture.json(result.toRecord()).noSpaces,
            "--resultReference", NativeCallerExecutionFixture.json(reference.toRecord()).noSpaces,
            "--executionEvidence", NativeCallerExecutionFixture.json(execution.toRecord()).noSpaces))
          submitted.business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
        case Some(other) => fail("Unexpected terminal fixture work: " + other)
        case None if state.kind == "Terminal" => terminal = Some(state)
        case None if state.authorityContext.nonEmpty =>
          val reference = V.WorkflowArtifactReference("scalafix-terminal-authority-" + iteration, "1")
          val authority = V.WorkflowAuthorityPreparationInput(output.invocation.stdout.toUri.toASCIIString,
            save_("authority-reference", reference.toRecord()), "participant", "normal-development-host",
            java.time.Instant.now().toString, "entity:smworkflow.cli.host_evidence")
          import_("prepareAuthority", authority.toRecord())
        case None if state.kind == "Wait" => ()
        case _ => fail("Unexpected terminal fixture state: " + state.kind)
      }
      iteration += 1
    }
    terminal.getOrElse(fail("Packaged Scalafix fixture did not reach terminal completion"))
  }

  "Packaged Scalafix completion metrics" should {
    "retain one accepted revalidated edit and an independently observed unchanged pass" in {
      Vector(true, false).foreach { changed =>
        val car = Path.of(Files.readString(Path.of("target/sm-workflow/packaged-test-car.txt")).trim).toAbsolutePath.normalize
        val command = F.candidateCommand(car)
        val scenario = F.freshPackagedMetals(car, false, metalsServerSource, "completed",
          scalafixRule = Some("RedundantSyntax"), terminalMetrics = true)
        scenario.selectPackagedInstalled(F.PackagedInstalled(Path.of(command.head), car, Some(command(2))))
        scenario.enablePreparedImportRetention()
        val preparation = scenario.prepareHostInput("prepareGoalStart", scenario.selectedGoalPreparationInput.toRecord())
        withClue(preparation.invocation.logs) { preparation.exitCode shouldBe 0 }
        scenario.selectPreparedImport(preparation)
        val task = scenario.ownTask("packaged-scalafix-metrics")
        val started = scenario.start().business[V.GoalPhaseStartResult]
        scenario.retain(task, started)
        val issued = scenario.readGoalPhaseResult(scenario.advance(task))
        issued.work.map(_.kind) shouldBe Some("ImplementStep")
        val source = Path.of(scenario.host.target.root).resolve(_compiler_path)
        Files.createDirectories(source.getParent)
        Files.writeString(source, (if (changed) "final " else "") + "object RepairExample {}\n")
        val result = V.GoalPhaseWorkResult("completed", "Fixture selected source", Vector.empty,
          None, None, None, None, None, None, None, None, dependencyValidation = None, diagnosticRepair = None)
        val reference = V.WorkflowArtifactReference("scalafix-metrics-implementation", "1")
        val submitted = scenario.call("command", issued.submitOperation.get, Vector("--sessionId", issued.submitSessionId.get,
          "--result", NativeCallerExecutionFixture.json(result.toRecord()).noSpaces,
          "--resultReference", NativeCallerExecutionFixture.json(reference.toRecord()).noSpaces))
        submitted.business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
        val terminal = _terminal_scalafix_fixture(scenario, task)
        terminal.outcome shouldBe Some("completed")
        val json = parse(take(terminal.completion.get.metrics.get.toJsonStringC)).fold(throw _, identity)
        val metrics = GoalPhaseNativeMetadata.normalizePublicJson(json).toOption.get.as[GoalPhasePhaseMetrics.Summary].toOption.get
        val tool = metrics.deterministicRepairs.get.tools.find(_.tool == "scalafix").get
        if (changed) {
          tool.count shouldBe Some(1)
          tool.coverage shouldBe "partial"
          metrics.deterministicRepairs.get.observations.exists(row => row.tool.contains("scalafix") &&
            row.changed.contains(true) && row.revalidated.contains(true) && row.retained.contains(true) &&
            row.rolledBack.contains(false) && row.validation.nonEmpty) shouldBe true
        } else {
          tool.count shouldBe None
          tool.coverage shouldBe "unavailable"
          val pass = metrics.deterministicRepairs.get.zeroPasses.toVector.flatten.find(_.tool == "scalafix").get
          pass.step shouldBe "step-1"
          pass.paths should contain (_compiler_path)
          pass.validation.nonEmpty shouldBe true
          pass.commit.nonEmpty shouldBe true
          val row = metrics.selectionCounts.get.find(_.identity == "repair:scalafix:pass:" + pass.identity).get
          row.completed shouldBe Some(0)
          row.coverage shouldBe "complete"
        }
        Files.readString(source) shouldBe "object RepairExample {}\n"
      }
    }
  }

  "Packaged Scala formatter" should {
    Vector((false, false), (true, false), (false, true)).foreach { (scala3, excluded) =>
      s"format a compile-clean candidate (scala3=$scala3, excluded=$excluded)" in {
      Given("an explicitly registered formatter and an isolated project")
      val car = Path.of(Files.readString(Path.of("target/sm-workflow/packaged-test-car.txt")).trim).toAbsolutePath.normalize
      val command = F.candidateCommand(car)
      val installed = F.PackagedInstalled(Path.of(command.headOption.getOrElse(fail("Candidate command is empty"))), car, Some(command(2)))
        val cleanup = !scala3 && !excluded
        val scenario = F.freshPackagedMetals(car, scala3, metalsServerSource, "completed", formatter = true,
          excludeFormatter = excluded, scalafixRule = Option.when(cleanup)("RedundantSyntax"), history = cleanup,
          structuralLint = scala3)
        scenario.selectPackagedInstalled(installed)
        val prepared = scenario.prepareHostInput("prepareGoalStart", scenario.selectedGoalPreparationInput.toRecord())
        withClue(prepared.invocation.logs) { prepared.exitCode shouldBe 0 }
        scenario.selectPreparedImport(prepared)
        val task = scenario.ownTask("packaged-formatter")
        val started = scenario.start().business[V.GoalPhaseStartResult]
        scenario.retain(task, started)
        val issued = scenario.readGoalPhaseResult(scenario.advance(task))
        issued.work.map(_.kind) shouldBe Some("ImplementStep")
        val source = Path.of(scenario.host.target.root).resolve("src/main/scala/RepairExample.scala")
        Files.createDirectories(source.getParent)
        val header = if (cleanup || excluded)
          "/**\n  * @since   Nov. 1, 2025\n  * @version Nov. 1, 2025\n  * @author  Fixture\n  */\n" else ""
        val original = if (scala3) """package example
          |object RepairExample {
          |  def compose(body: => Int): Int = body
          |  def compute(n: Int): Int =
          |    if n>0 then
          |      n+1
          |    else
          |      0
          |  val value=compose {
          |    val input=2
          |    compute(input)
          |  }
          |}
          |""".stripMargin else header + (if (cleanup) "final " else "") + "object RepairExample {\n  val value=1\n}\n"
        Files.writeString(source, original)
        val result = V.GoalPhaseWorkResult("completed", "Fixture compile-clean candidate", Vector.empty,
          None, None, None, None, None, None, None, None, dependencyValidation = None, diagnosticRepair = None)
        val reference = V.WorkflowArtifactReference("packaged-formatter-implementation", "1")
        val operation = issued.submitOperation.getOrElse(fail("Issued work has no submission operation"))
        val session = issued.submitSessionId.getOrElse(fail("Issued work has no submission session"))
        When("the ordinary workflow inspects and validates the candidate")
        val submitted = scenario.call("command", operation, Vector("--sessionId", session,
          "--result", NativeCallerExecutionFixture.json(result.toRecord()).noSpaces,
          "--resultReference", NativeCallerExecutionFixture.json(reference.toRecord()).noSpaces))
        submitted.business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
        val next = scenario.readGoalPhaseResult(scenario.advance(task))
        Then("the included candidate is formatted and the excluded candidate keeps its history under explicit none")
        next.work.map(_.kind) shouldBe Some("ReviewStep")
        val expectedChecks = Vector("compile" -> Some(0)) ++
          Option.when(scala3)("scala-lint" -> Some(0))
        next.work.getOrElse(fail("Missing work")).review.getOrElse(fail("Missing review")).checks.map(check => check.identity -> check.exitCode) shouldBe expectedChecks
        if (excluded) Files.readString(source) shouldBe original
        else {
          val formatted = Files.readString(source)
          formatted should not be original
          formatted should include ("object RepairExample {")
          if (scala3) {
            formatted should include ("val value = compose {")
            formatted should include ("def compute(n: Int): Int =\n")
            formatted should include ("if n > 0 then")
            val actual = parse(Files.readString(Path.of(scenario.host.target.root).resolve("target/scala-lint/actual.conf"))).fold(throw _, identity)
            actual.hcursor.get[Vector[String]]("rules").fold(throw _, identity) should contain ("NoStructuralIndentation")
            actual.hcursor.downField("NoStructuralIndentation").get[Vector[String]]("structuralDslSymbols").fold(throw _, identity) should
              contain ("example/RepairExample.compose().")
          }
          if (cleanup) {
            Files.readString(source) should not include ("final object")
            formatted should include ("@since   Nov.  1, 2025")
            formatted should include ("@author  Fixture")
            formatted should not include ("@version Nov.  1, 2025")
          }
        }
      }
    }
  }

  "Packaged Scalafix compiler repair" should {
    "repair a real Scala 3 migration diagnostic using the pinned Scala 2 parser" in {
      Given("an isolated Scala 3.3.8 compiler and only the fixed ProcedureSyntax provider")
      val car = Path.of(Files.readString(Path.of("target/sm-workflow/packaged-test-car.txt")).trim).toAbsolutePath.normalize
      val command = F.candidateCommand(car)
      val scenario = F.freshPackagedMetals(car, true, metalsServerSource, "completed", scalafixRule = Some("ProcedureSyntax"))
      scenario.selectPackagedInstalled(F.PackagedInstalled(Path.of(command.headOption.getOrElse(fail("Candidate command is empty"))), car, Some(command(2))))
      val prepared = scenario.prepareHostInput("prepareGoalStart", scenario.selectedGoalPreparationInput.toRecord())
      withClue(prepared.invocation.logs) { prepared.exitCode shouldBe 0 }
      scenario.selectPreparedImport(prepared)
      val task = scenario.ownTask("packaged-procedure-syntax")
      val started = scenario.start().business[V.GoalPhaseStartResult]
      scenario.retain(task, started)
      val issued = scenario.readGoalPhaseResult(scenario.advance(task))
      issued.work.map(_.kind) shouldBe Some("ImplementStep")
      val source = Path.of(scenario.host.target.root).resolve("src/main/scala/RepairExample.scala")
      Files.createDirectories(source.getParent)
      Files.writeString(source, "object RepairExample {\n  def run { println(\"ok\") }\n}\n")
      val result = V.GoalPhaseWorkResult("completed", "Fixture procedure syntax", Vector.empty,
        None, None, None, None, None, None, None, None, dependencyValidation = None, diagnosticRepair = None)
      val reference = V.WorkflowArtifactReference("packaged-procedure-implementation", "1")
      val operation = issued.submitOperation.getOrElse(fail("Issued work has no submission operation"))
      val session = issued.submitSessionId.getOrElse(fail("Issued work has no submission session"))
      When("the ordinary workflow observes and repairs the compiler diagnostic")
      val submitted = scenario.call("command", operation, Vector("--sessionId", session,
        "--result", NativeCallerExecutionFixture.json(result.toRecord()).noSpaces,
        "--resultReference", NativeCallerExecutionFixture.json(reference.toRecord()).noSpaces))
      submitted.business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val first = scenario.readGoalPhaseResult(scenario.advance(task))
      val next = if (first.work.exists(_.kind == "ReviewStep") || first.work.exists(_.kind == "RepairStep")) first
        else scenario.readGoalPhaseResult(scenario.advance(task))
      Then("the corrected candidate recompiles without AI")
      next.work.map(_.kind) shouldBe Some("ReviewStep")
      Files.readString(source) should include (": Unit =")
    }

    "restore the original file after diagnostic repair and formatting fail revalidation" in {
      Given("one packaged workspace with the pinned diagnostic rewrite and formatter")
      val car = Path.of(Files.readString(Path.of("target/sm-workflow/packaged-test-car.txt")).trim).toAbsolutePath.normalize
      val command = F.candidateCommand(car)
      val scenario = F.freshPackagedMetals(car, true, metalsServerSource, "completed", formatter = true,
        scalafixRule = Some("ProcedureSyntax"), rejectFormattedCandidate = true)
      scenario.selectPackagedInstalled(F.PackagedInstalled(Path.of(command.headOption.getOrElse(fail("Candidate command is empty"))), car, Some(command(2))))
      val prepared = scenario.prepareHostInput("prepareGoalStart", scenario.selectedGoalPreparationInput.toRecord())
      withClue(prepared.invocation.logs) { prepared.exitCode shouldBe 0 }
      scenario.selectPreparedImport(prepared)
      val task = scenario.ownTask("packaged-combined-rollback")
      val started = scenario.start().business[V.GoalPhaseStartResult]
      scenario.retain(task, started)
      val work = scenario.readGoalPhaseResult(scenario.advance(task))
      work.work.map(_.kind) shouldBe Some("ImplementStep")
      val repository = Path.of(scenario.host.target.root)
      val source = repository.resolve(_compiler_path)
      Files.createDirectories(source.getParent)
      val original = "object RepairExample {\n  def run { val value=1; println(value) }\n}\n"
      Files.writeString(source, original)
      val result = V.GoalPhaseWorkResult("completed", "Fixture diagnostic and formatter revalidation", Vector.empty,
        None, None, None, None, None, None, None, None, dependencyValidation = None, diagnosticRepair = None)
      val reference = V.WorkflowArtifactReference("packaged-combined-rollback-implementation", "1")
      val submitted = scenario.call("command", work.submitOperation.getOrElse(fail("Missing submit operation")), Vector("--sessionId", work.submitSessionId.getOrElse(fail("Missing submit session")),
        "--result", NativeCallerExecutionFixture.json(result.toRecord()).noSpaces,
        "--resultReference", NativeCallerExecutionFixture.json(reference.toRecord()).noSpaces))
      submitted.business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      When("the native Compile check rejects the formatted successor")
      val next = Iterator.continually(scenario.readGoalPhaseResult(scenario.advance(task))).take(5)
        .find(_.work.exists(result => Set("ReviewStep", "RepairStep")(result.kind))).getOrElse(fail("No successor was issued"))
      Then("the fallback receives a candidate restored to its original source")
      next.work.map(_.kind) shouldBe Some("RepairStep")
      val compiled = Files.readString(repository.resolve("target/observed-formatted-candidate.txt"))
      compiled should include (": Unit =")
      compiled should include ("val value = 1")
      Files.readString(source) shouldBe original
    }
  }

  "Packaged combined semantic cleanup" should {
    "repair an invalid import, refresh SemanticDB, organize imports, normalize history, and format one candidate" in {
      Given("an isolated Scala 3.3.8 project with explicitly selected providers and semantic configuration")
      val car = Path.of(Files.readString(Path.of("target/sm-workflow/packaged-test-car.txt")).trim).toAbsolutePath.normalize
      val command = F.candidateCommand(car)
      val scenario = F.freshPackagedMetals(car, true, metalsServerSource, "completed", formatter = true,
        scalafixRule = Some("OrganizeImports"), history = true)
      scenario.selectPackagedInstalled(F.PackagedInstalled(Path.of(command.headOption.getOrElse(fail("Candidate command is empty"))), car, Some(command(2))))
      val prepared = scenario.prepareHostInput("prepareGoalStart", scenario.selectedGoalPreparationInput.toRecord())
      withClue(prepared.invocation.logs) { prepared.exitCode shouldBe 0 }
      scenario.selectPreparedImport(prepared)
      val task = scenario.ownTask("packaged-combined-semantic")
      val started = scenario.start().business[V.GoalPhaseStartResult]
      scenario.retain(task, started)
      val issued = scenario.readGoalPhaseResult(scenario.advance(task))
      issued.work.map(_.kind) shouldBe Some("ImplementStep")
      val repository = Path.of(scenario.host.target.root)
      val source = repository.resolve(_compiler_path)
      val original = "/**\n  * @since   Nov. 1, 2025\n  * @version Nov. 1, 2025\n  * @author  Fixture\n  */\n" +
        "import MissingPackage.Member\nimport scala.util.Try\nimport scala.util.Success\n" +
        "object RepairExample { val value=Try(1).map(Success(_)) }\n"
      Files.writeString(source, original)
      val result = V.GoalPhaseWorkResult("completed", "Fixture combined semantic candidate", Vector.empty,
        None, None, None, None, None, None, None, None, dependencyValidation = None, diagnosticRepair = None)
      val reference = V.WorkflowArtifactReference("packaged-combined-semantic-implementation", "1")
      When("the actual packaged workflow handles the diagnostic and final candidate")
      val submitted = scenario.call("command", issued.submitOperation.getOrElse(fail("Missing submit operation")), Vector("--sessionId", issued.submitSessionId.getOrElse(fail("Missing submit session")),
        "--result", NativeCallerExecutionFixture.json(result.toRecord()).noSpaces,
        "--resultReference", NativeCallerExecutionFixture.json(reference.toRecord()).noSpaces))
      submitted.business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val next = Iterator.continually(scenario.readGoalPhaseResult(scenario.advance(task))).take(12)
        .find(_.work.exists(result => Set("ReviewStep", "RepairStep")(result.kind))).getOrElse(fail("No successor was issued"))
      Then("the final candidate retains history, sorted imports and formatting after successful compilation")
      next.work.map(_.kind) shouldBe Some("ReviewStep")
      next.work.getOrElse(fail("Missing work")).review.getOrElse(fail("Missing review")).checks.map(check => check.identity -> check.exitCode) shouldBe Vector("compile" -> Some(0))
      val finaltext = Files.readString(source)
      finaltext should not include ("MissingPackage")
      finaltext.indexOf("import scala.util.Success") should be < finaltext.indexOf("import scala.util.Try")
      finaltext should include ("val value =")
      finaltext should include ("@since   Nov.  1, 2025")
      finaltext should include ("@author  Fixture")
      finaltext should not include ("@version Nov.  1, 2025")
      Files.isRegularFile(repository.resolve("target/semanticdb/META-INF/semanticdb/" + _compiler_path + ".semanticdb")) shouldBe true
      val snapshots = scenario.root.resolve("compiler-inputs")
      val inputs = Files.list(snapshots)
      val captured = try inputs.toArray.map(_.asInstanceOf[Path]).toVector
        .sortBy(_.getFileName.toString.stripSuffix(".scala").toInt).map(Files.readString(_)) finally inputs.close()
      val initial = captured.find(_.contains("import MissingPackage.Member")).getOrElse(fail("Missing failed compiler input"))
      val repaired = initial.replace("import MissingPackage.Member\n", "")
      captured should contain (repaired)
      captured should contain (finaltext)
      Files.readString(scenario.root.resolve("successful-compiles.txt")).linesIterator.toVector shouldBe Vector(
        GoalPhaseNativeScalaCleanup.hashText(repaired), GoalPhaseNativeScalaCleanup.hashText(finaltext))
      Files.readString(scenario.root.resolve("metals.trace")) should include ("only:[\"quickfix\"]")
    }
  }

  "Scala formatter preview admission" should {
    "bind a finite source to a literal path and fixed capability" in {
      Given("a bounded source and selected formatter configuration")
      val input = WorkflowScalaToolProcess.Input("src/main/scala/Example.scala", "object Example {}\n",
        Map(".scalafmt.conf" -> "version = 3.8.6\n"))
      When("the fixed preview request is admitted")
      val request = WorkflowScalaToolProcess.requestC(input).toOption.getOrElse(fail("Invalid preview input"))
      Then("the request retains its fixed boundary and rejects invalid inputs")
      request.capability.value shouldBe WorkflowScalaToolProcess.capability
      request.arguments shouldBe empty
      request.workingDirectory shouldBe empty
      WorkflowScalaToolProcess.requestC(input.copy(path = "../Example.scala")).isSuccess shouldBe false
      WorkflowScalaToolProcess.requestC(input.copy(configFiles = Map.empty)).isSuccess shouldBe false
      WorkflowScalaToolProcess.requestC(input.copy(source = "x" * 1048577)).isSuccess shouldBe false
      WorkflowProjectDeclaration.scalaVersionTextC(Some("build:\n  scalaVersion: \"3.3.8\"\n")).toOption shouldBe Some(Some("3.3.8"))
      WorkflowProjectDeclaration.scalaVersionTextC(Some("build:\n  scalaVersion: null\n")).toOption shouldBe Some(None)
      WorkflowProjectDeclaration.scalaVersionTextC(Some("build:\n  scalaVersion: [3, 3, 8]\n")).isSuccess shouldBe false
    }
  }

}
