/**
 * @since   Nov. 21, 2026
 * @version Nov. 21, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.file.Path
import java.nio.charset.StandardCharsets.UTF_8
import org.goldenport.cncf.processexecution.{ProcessExecutionCapture, ProcessExecutionResult, ProcessExecutionTermination,
  ValidationCheckExecutionAttempt, ValidationCheckExecutionResult, ValidationCheckId, ValidationCheckOperation,
  ValidationCheckPurpose, ValidationCheckTarget}
import org.goldenport.cncf.workspace.WorkspaceId
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseCarLintSpec extends AnyWordSpec with Matchers {
  import GoalPhaseNativeValidation as N
  import RepositorySyncNativeMergeValidation as V
  import WorkflowSbtValidation.Kind

  private val _root = Path.of("/fixture/repository")
  private val _compile = N.Check("compile", 1, Kind.Compile)
  private val _test = N.Check("behavior", 2, Kind.TestOnly)
  private val _lint = N.Check("car-conformance", 3, carLint = true)
  private val _workspace = WorkspaceId.parseC("sm-workflow-repository-validation-3").toOption.get
  private val _target = ValidationCheckTarget(_workspace, ValidationCheckId.parseC(_lint.identity).toOption.get)

  "The native Cozy CAR lint report" should {
    "preserve report locations and distinguish warnings from failing rules" in {
      val report = """{"findings":[
        {"level":"WARN","category":"documentation","code":"guide","message":"Optional guide","path":"docs/guide.md","line":1},
        {"level":"FAIL","category":"cml","code":"contract","message":"Contract required","path":"/fixture/repository/src/main/cozy/main.cml","line":13}
      ]}"""
      val checked = GoalPhaseCarLint.reportC(_root, report, 1).toOption.get
      checked.findings.size shouldBe 2
      checked.blockers.map(_.identity) shouldBe Vector("cml:contract:src/main/cozy/main.cml:13")
      GoalPhaseCarLint.reportC(_root, report, 0).isSuccess shouldBe false
      GoalPhaseCarLint.reportC(_root, """{"findings":[]}""", 0).toOption.get.blockers shouldBe empty
    }

    "refuse crossed, malformed or contradictory reports" in {
      val crossed = """{"findings":[
        {"level":"FAIL","category":"cml","code":"contract","message":"Contract required","path":"../elsewhere/main.cml","line":1}
      ]}"""
      GoalPhaseCarLint.reportC(_root, crossed, 1).isSuccess shouldBe false
      GoalPhaseCarLint.reportC(_root, """{"findings":[],"findings":[]}""", 0).isSuccess shouldBe false
      GoalPhaseCarLint.reportC(_root, """{"findings":[]}""", 1).isSuccess shouldBe false
      GoalPhaseCarLint.reportC(_root, """{"findings":[]}""", 2).isSuccess shouldBe false
    }
  }

  "The Goal Phase CAR lint gate" should {
    "require a final check after compilation and behavior, including when assessment is enabled" in {
      val required = GoalPhaseStepAssessment.Policy("code-review", "code", "required", "off", "required", Vector.empty)
      N.validateCarLintSelectionC(Vector(_compile, _test, _lint), Some(required)).isSuccess shouldBe true
      N.validateCarLintSelectionC(Vector(_compile, _lint, _test), Some(required)).isSuccess shouldBe false
      N.validateCarLintSelectionC(Vector(_compile, _lint), Some(required)).isSuccess shouldBe false
      N.validateCarLintSelectionC(Vector(_compile, _test), Some(required)).isSuccess shouldBe false
      N.validateCarLintSelectionC(Vector(_compile, _test, _lint), Some(required.copy(carLint = "off"))).isSuccess shouldBe false
      _lint.asJson.as[N.Check] shouldBe Right(_lint)
      _lint.asJson.hcursor.get[String]("kind") shouldBe Right("car-lint")
      N.Check("old-test", 1).asJson.hcursor.downField("kind").succeeded shouldBe false
    }

    "skip the late gate if ordinary validation is incomplete or failed" in {
      val checks = Vector(_compile, _test, _lint)
      val compiled = V.Receipt(_compile.identity, "exited", Some(0), "", "",
        sbt = Some(WorkflowSbtValidation.Facts(Kind.Compile, WorkflowSbtValidation.Outcome.Succeeded, None, None)))
      val failed = V.Receipt(_test.identity, "exited", Some(1), "", "",
        sbt = Some(WorkflowSbtValidation.Facts(Kind.TestOnly, WorkflowSbtValidation.Outcome.Failed, None, None)))
      N.mayRunCheck(Vector(compiled), checks, 2) shouldBe false
      N.mayRunCheck(Vector(compiled, failed), checks, 2) shouldBe false
      val passed = failed.copy(exitCode = Some(0), sbt = Some(WorkflowSbtValidation.Facts(
        Kind.TestOnly, WorkflowSbtValidation.Outcome.Succeeded,
        Some(WorkflowSbtValidation.Selection(Vector("fixture.ExampleSpec"), 1, 1)), None)))
      N.mayRunCheck(Vector(compiled, passed), checks, 2) shouldBe true
    }

    "resolve the fixed JSON check only in the selected repository and with a unique match" in {
      val plan = parse(s"""{
        "workspaces":[{"identity":"${_workspace.value}","root":"${_root.toString}"}],
        "validationChecks":[{
          "target":"${_workspace.value}","checkIdentity":"${_lint.identity}","purpose":"lint",
          "fixedArguments":["car","lint",".","--format","json"],"identity":"cozy-car-lint"
        }]
      }""").toOption.get
      GoalPhaseCarLint.hostProgramC(plan, _target, _root).toOption shouldBe Some("cozy-car-lint")
      GoalPhaseCarLint.hostProgramC(plan, _target, _root.resolve("other")).isSuccess shouldBe false
      val crossed = plan.mapObject(o => o.add("validationChecks", o("validationChecks").get.mapArray(a => a ++ a)))
      GoalPhaseCarLint.hostProgramC(crossed, _target, _root).isSuccess shouldBe false
    }

    "retain the actual process report without treating it as a passing SBT test" in {
      val snapshot = GoalPhaseOrderedProgression.Snapshot("a" * 40, "b" * 40)
      val before = N.Payload("1", "S1", snapshot, Vector(_compile, _test, _lint), Vector.empty, None)
      def attempt(output: String, purpose: ValidationCheckPurpose = ValidationCheckPurpose.Lint) = {
        val bytes = output.getBytes(UTF_8).toVector
        val process = ProcessExecutionResult(ProcessExecutionTermination.Exited(0),
          ProcessExecutionCapture(bytes, bytes.size.toLong, false), ProcessExecutionCapture(Vector.empty, 0, false),
          Vector.empty, 1, "cozy-car-lint")
        ValidationCheckExecutionAttempt.Observed(ValidationCheckExecutionResult(_target, ValidationCheckOperation.Run, process, purpose))
      }
      val success = N.carLintAttemptPayloadC(before, _lint, 0, _root, attempt("""{"findings":[]}""")).toOption.get
      val receipt = success.receipts.last
      receipt.asJson.as[V.Receipt] shouldBe Right(receipt)
      N.successful(Vector(receipt), Vector(_lint)) shouldBe true
      N.successful(Vector(receipt), Vector(N.Check(_lint.identity, _lint.ordinal))) shouldBe false
      val malformed = N.carLintAttemptPayloadC(before, _lint, 0, _root, attempt("broken")).toOption.get
      malformed.receipts shouldBe empty
      malformed.uncertainties.map(_.checkIdentity) shouldBe Vector(_lint.identity)
      N.carLintAttemptPayloadC(before, _lint, 0, _root, attempt("""{"findings":[]}""", ValidationCheckPurpose.Behavior))
        .isSuccess shouldBe false
    }
  }
}
