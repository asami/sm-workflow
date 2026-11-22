package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import org.goldenport.cncf.processexecution.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.DeterministicRepair.*

/** Executable specification for bounded diagnostic proposals.
  * @since   Nov. 22, 2026
  * @version Nov. 22, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class DeterministicRepairSpec extends AnyWordSpec with Matchers with GivenWhenThen {
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
}
