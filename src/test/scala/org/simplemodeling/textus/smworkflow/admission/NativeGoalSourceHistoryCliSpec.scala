package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.scalatest.{GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F

/** Ordinary commands connect current resolved settings to candidate history and checks.
  * Semantic edits are controlled fixture input, not genuine AI or acceptance evidence.
  * @since   Oct. 17, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
private[admission] trait NativeGoalSourceHistoryCliFixture extends Matchers with GivenWhenThen { this: AnyWordSpec =>
  val sourcePath = "src/main/scala/HistoryExample.scala"
  val initialText = "object HistoryExample { val value: Int = 0 }\n"
  val changedText = "object HistoryExample { val value: Int = 42 }\n"
  val styleKey = "sm-workflow.source-history.style"
  val ruleKey = "sm-workflow.source-history.rule"
  val historyFormatter = DateTimeFormatter.ofPattern("MMM. ppd, uuuu", Locale.forLanguageTag("en-US"))
  private val _document = Json.obj(
    "schemaVersion" -> Json.fromString("sm-workflow.source-history-rule.v1"),
    "identity" -> Json.fromString("native-cli-external-goldenport-v1"),
    "style" -> Json.fromString("goldenport"), "format" -> Json.fromString("scala-block-history-v1"),
    "sourceRoots" -> Json.arr(Json.fromString("src/main/scala"), Json.fromString("src/test/scala")),
    "extensions" -> Json.arr(Json.fromString("scala")),
    "excludedSegments" -> Json.fromValues(Vector("generated", "vendor", "target", "sample", "samples").map(Json.fromString)),
    "tags" -> Json.obj("since" -> Json.fromString("@since"), "version" -> Json.fromString("@version"),
      "history" -> Json.fromString("version"), "author" -> Json.fromString("@author")),
    "datePattern" -> Json.fromString("MMM. ppd, uuuu"), "locale" -> Json.fromString("en-US"),
    "rollover" -> Json.fromString("month"), "removeHistoryAtSince" -> Json.True,
    "removeHistoryInCurrentPeriod" -> Json.True,
    "header" -> Json.obj("open" -> Json.fromString("/**"), "linePrefix" -> Json.fromString(" * "),
      "close" -> Json.fromString(" */")))
  val enabledSettings = Map(styleKey -> Json.fromString("goldenport"), ruleKey -> _document)
  final case class Implemented(scenario: F.Scenario, task: Path,
    request: V.GoalPhaseAdvanceResult, source: Path)

  def configure(scenario: F.Scenario, settings: Map[String, Json]): Unit = {
    val file = scenario.root.resolve(".textus/config.json")
    val current = parse(Files.readString(file)).fold(error => throw new AssertionError(error.message), identity)
    Files.writeString(file, current.mapObject(value => settings.foldLeft(value.remove(styleKey).remove(ruleKey)) {
      case (fields, (key, setting)) => fields.add(key, setting)
    }).noSpaces)
  }
  def executions(scenario: F.Scenario): Vector[Long] = {
    val marker = scenario.root.resolve("sbt-executions.txt")
    if (Files.exists(marker)) Files.readString(marker).linesIterator.map(_.toLong).toVector else Vector.empty
  }
  def fileBytes(path: Path): Vector[Byte] = Files.readAllBytes(path).toVector
  def indexBytes(scenario: F.Scenario): Vector[Byte] = fileBytes(Path.of(scenario.host.target.root).resolve(".git/index"))
  def implemented(name: String, settings: Map[String, Json], text: String = changedText,
    command: Option[(F.Scenario, String, Vector[String]) => F.Result] = None): Implemented = {
    Given("a fresh isolated Phase 1, one owned Scala source, external common settings and ordinary Test policy")
    val scenario = F.fresh(name, sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable), sourceHistory = true)
    configure(scenario, settings)
    val task = scenario.ownTask("source-history-task")
    val source = Path.of(scenario.host.target.root).resolve(sourcePath)
    def _call_(operation: String, arguments: Vector[String]): F.Result = command match {
      case Some(invoke) => invoke(scenario, operation, arguments)
      case None => scenario.call("command", operation, arguments)
    }
    When("a separate normal Start selects that finite Goal and a normal Advance requests implementation")
    val started = _call_("startGoalPhase", Vector("--input", scenario.input.noSpaces)).business[V.GoalPhaseStartResult]
    scenario.retain(task, started)
    val request = _call_("advanceGoalPhase", Vector("--sessionId", scenario.retrieve(task))).business[V.GoalPhaseAdvanceResult]
    Then("the task retains actual implementation work for exactly the selected source without a product check")
    scenario.host.goal.input.phaseIdentity shouldBe "1"
    scenario.host.goal.input.repositoryIdentity shouldBe "smwf-p61-cli-source-history"
    scenario.host.repository.input.repositoryIdentity shouldBe scenario.host.goal.input.repositoryIdentity
    scenario.host.goal.candidatePaths shouldBe Vector(sourcePath)
    scenario.plan.steps.map(_.identity) shouldBe Vector("step-1")
    scenario.plan.steps.head.ownedPaths shouldBe Vector(sourcePath)
    scenario.plan.steps.head.checks shouldBe Vector("test")
    request.kind shouldBe "Request"
    request.sessionId shouldBe started.sessionId
    request.work.get.kind shouldBe "ImplementStep"
    request.work.get.stepIdentity shouldBe Some("step-1")
    request.issuedWorkContext should not be empty
    Files.readString(source) shouldBe initialText
    executions(scenario) shouldBe empty

    Given("the issued implementation and fixture-authored semantic code without any claimed check or review evidence")
    When("the fixture writes the compiled value and a separate normal Submit completes only semantic work")
    Files.writeString(source, text, UTF_8)
    val result = V.GoalPhaseWorkResult("completed", "Fixture-owned semantic edit of HistoryExample value", Vector.empty, None, None, None, None, None, None, None, None, dependencyValidation = None, diagnosticRepair = None)
    val reference = V.WorkflowArtifactReference("fixture-cli-source-history-implementation", "1")
    val submitted = _call_(request.submitOperation.get,
      Vector("--sessionId", request.submitSessionId.get,
        "--result", NativeCallerExecutionFixture.json(result.toRecord()).noSpaces,
        "--resultReference", NativeCallerExecutionFixture.json(reference.toRecord()).noSpaces))
      .business[V.GoalPhaseWorkSubmissionResult]
    Then("the semantic result is acknowledged while the actual edited bytes stay intact and Test has not run")
    submitted.accepted shouldBe true
    submitted.outcome shouldBe "accepted"
    submitted.sessionId shouldBe started.sessionId
    fileBytes(source) shouldBe text.getBytes(UTF_8).toVector
    executions(scenario) shouldBe empty
    Implemented(scenario, task, request, source)
  }
  def advance(fixture: Implemented, properties: Vector[String] = Vector.empty): F.Result = {
    val testhome = Files.createDirectories(fixture.scenario.root.resolve("test-home"))
    fixture.scenario.call("command", GoalPhaseNativeRun.advanceOperation.operation,
      Vector("--sessionId", fixture.scenario.retrieve(fixture.task)) ++ properties ++
        Vector("--textus.test.home.path=" + testhome.toString, "--textus.test.home.inherit.runtime=false"))
  }

  def assertReview(fixture: Implemented, result: F.Result): V.GoalPhaseAdvanceResult = {
    val review = result.business[V.GoalPhaseAdvanceResult]
    val work = review.work.get
    val context = work.review.get
    val check = context.checks.head
    val pids = executions(fixture.scenario)
    Then("pending independent ReviewStep carries the actual staged source, semantic diff and a positive typed Test receipt")
    review.kind shouldBe "Request"
    review.sessionId shouldBe fixture.request.sessionId
    work.kind shouldBe "ReviewStep"
    work.stepIdentity shouldBe Some("step-1")
    work.originalBase shouldBe fixture.request.work.get.originalBase
    work.selectedPlan shouldBe fixture.request.work.get.selectedPlan
    work.completed shouldBe empty
    work.checkFailure shouldBe None
    work.candidate.get.head shouldBe work.originalBase.head
    work.candidate.get.tree should not be work.originalBase.tree
    context.changedPaths shouldBe Vector(sourcePath)
    context.diff should include("+++ b/" + sourcePath)
    context.diff should include("+object HistoryExample { val value: Int = 42 }")
    context.checks.size shouldBe 1
    check.identity shouldBe "test"
    check.termination shouldBe "exited"
    check.exitCode shouldBe Some(0)
    check.sbt.get.kind shouldBe "test"
    check.sbt.get.outcome shouldBe "succeeded"
    check.stdout should include("PRODUCT_CHECK_ENTERED " + fixture.source.getParent.getParent.getParent.getParent)
    check.stdout should include("PRODUCT_CHECK_PASSED")
    review.issuedWorkContext should not be fixture.request.issuedWorkContext
    pids.size shouldBe 1
    pids.distinct.size shouldBe 1
    pids should not contain ProcessHandle.current().pid()
    pids should not contain result.invocation.pid
    fixture.scenario.invocations.map(_.pid).distinct.size shouldBe fixture.scenario.invocations.size
    fixture.scenario.invocations.foreach { invocation =>
      sys.props.get("smworkflow.spec.installedCommand") match {
        case Some(executable) if invocation.argv.headOption.contains(executable) =>
          invocation.argv.take(4) shouldBe Vector(executable, "--runtime-root", fixture.scenario.root.toString, "command")
        case _ => invocation.argv should contain("org.goldenport.cncf.CncfMain")
      }
      invocation.argv should contain("command")
      invocation.pid should not be ProcessHandle.current().pid()
    }
    review
  }

}

@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalSourceHistoryCliSpec extends AnyWordSpec with NativeGoalSourceHistoryCliFixture {
  "Ordinary GoalPhase source-history commands" should afterWord(
    "in spec:source-history-resolved-configuration, example:P61-COMMAND-C3, rule:owning-workflow") {
    "maintain history before actual staging and Test and retain the same pending review without replay" in {
      val fixture = implemented("goal-source-history-enabled", enabledSettings)
      Given("a submitted bare Scala semantic edit and the externally supplied goldenport rule")
      When("a separate normal Advance inspects the actual candidate")
      val result = advance(fixture)
      val text = Files.readString(fixture.source, UTF_8)
      val review = assertReview(fixture, result)
      Then("runtime-authored since and latest version are in the same source and review diff with no invented author")
      text should startWith("/**\n * @since ")
      text should endWith(" */\n" + changedText)
      text should not include "@author"
      text.linesIterator.count(_.contains("@since")) shouldBe 1
      text.linesIterator.count(_.contains("@version")) shouldBe 1
      val since = text.linesIterator.find(_.contains("@since")).get.split("@since", 2).last.trim
      val version = text.linesIterator.find(_.contains("@version")).get.split("@version", 2).last.trim
      historyFormatter.format(LocalDate.parse(since, historyFormatter)) shouldBe since
      version shouldBe since
      text shouldBe "/**\n * @since   " + since + "\n * @version " + version + "\n */\n" + changedText
      text.linesIterator.filter(_.contains("version")).toVector shouldBe Vector(" * @version " + version)
      review.work.get.review.get.diff should include("+ * @since   " + since)
      review.work.get.review.get.diff should include("+ * @version " + version)
      val bytes = fileBytes(fixture.source)
      val pids = executions(fixture.scenario)

      Given("the actual independent ReviewStep is pending and no review result or commit has been supplied")
      When("another separate normal Advance rereads the pending work")
      val repeated = advance(fixture).business[V.GoalPhaseAdvanceResult]
      Then("the identical public work and issued context retain the same bytes and single product-check PID")
      repeated shouldBe review
      repeated.issuedWorkContext shouldBe review.issuedWorkContext
      fileBytes(fixture.source) shouldBe bytes
      executions(fixture.scenario) shouldBe pids
      fixture.scenario.invocations.map(_.pid).distinct.size shouldBe fixture.scenario.invocations.size
    }

    "apply the declared same-key none operation override before decoding a malformed common rule" in {
      val fixture = implemented("goal-source-history-override", enabledSettings.updated(ruleKey, Json.fromString("{invalid")))
      Given("common goldenport and a malformed rule with the canonical style property explicitly overridden to none")
      When("a separate normal Advance passes the declared same-key operation property")
      val result = advance(fixture, Vector("--" + styleKey, "none"))
      val review = assertReview(fixture, result)
      Then("ordinary staging and Test pass while actual source and diff contain only the bare semantic change")
      fileBytes(fixture.source) shouldBe changedText.getBytes(UTF_8).toVector
      review.work.get.review.get.diff should not include "@since"
      review.work.get.review.get.diff should not include "@version"
      result.invocation.argv should contain("--" + styleKey)
      Files.readString(result.invocation.stderr) should not include "Source history"
    }

    "default absent style to none while preserving ordinary meaning comments and ignoring malformed rule data" in {
      val text = "// This value is the selected semantic result.\n" + changedText
      val fixture = implemented("goal-source-history-default", Map(ruleKey -> Json.fromString("{invalid")), text)
      Given("no selected style, malformed unused rule data and an ordinary meaning comment")
      When("a separate normal Advance uses the default resolved style")
      val result = advance(fixture)
      val review = assertReview(fixture, result)
      Then("ordinary staging and Test pass without changing the semantic comment or adding a history header")
      fileBytes(fixture.source) shouldBe text.getBytes(UTF_8).toVector
      review.work.get.review.get.diff should include("+// This value is the selected semantic result.")
      review.work.get.review.get.diff should not include "@version"
      Files.readString(result.invocation.stderr) should not include "Source history"
    }

    "reject invalid selection before inspection and allow explicit none on the same untouched frontier" in {
      val cases = Vector("invalid-style" -> Map(styleKey -> Json.fromString("asami")),
        "missing-rule" -> Map(styleKey -> Json.fromString("goldenport")))
      cases.foreach { (name, settings) =>
        val fixture = implemented("goal-source-history-" + name, settings)
        Given("the submitted semantic candidate and invalid style or an absent selected goldenport rule")
        val index = indexBytes(fixture.scenario)
        When("a separate normal Advance preflights the current resolved configuration")
        val failed = advance(fixture)
        Then("the actual configuration failure precedes source mutation, Git staging and any product check")
        failed.exitCode should not be 0
        failed.invocation.logs should include("Source history parameters")
        fileBytes(fixture.source) shouldBe changedText.getBytes(UTF_8).toVector
        indexBytes(fixture.scenario) shouldBe index
        executions(fixture.scenario) shouldBe empty

        Given("the same current candidate frontier with no saved inspection or history effect")
        When("the fixture corrects actual common settings to explicit none and a separate Advance proceeds")
        configure(fixture.scenario, Map(styleKey -> Json.fromString("none")))
        val result = advance(fixture)
        val review = assertReview(fixture, result)
        Then("normal ReviewStep is reachable with bare source and no semantic RepairStep or replay waiver")
        review.work.get.kind shouldBe "ReviewStep"
        review.work.get.checkFailure shouldBe None
        fileBytes(fixture.source) shouldBe changedText.getBytes(UTF_8).toVector
      }
    }

    "retain a prepared intent on malformed managed dates and issue the same recovery Human without replay" in {
      val text = "/**\n * @version impossible-date\n */\n" + changedText
      val fixture = implemented("goal-source-history-partial", enabledSettings, text)
      Given("a valid external rule and compilable semantic code with a malformed managed version date")
      val index = indexBytes(fixture.scenario)
      When("a separate normal Advance saves its intent and the actual File interpretation fails")
      val failed = advance(fixture)
      Then("the typed rule failure preserves the source and index and starts no product check")
      failed.exitCode should not be 0
      failed.invocation.logs should include("malformed managed date")
      fileBytes(fixture.source) shouldBe text.getBytes(UTF_8).toVector
      indexBytes(fixture.scenario) shouldBe index
      executions(fixture.scenario) shouldBe empty

      Given("the actual partial history intent without a final candidate, recovery decision or semantic repair")
      When("another separate normal Advance observes the retained native history")
      val waiting = advance(fixture).business[V.GoalPhaseAdvanceResult]
      Then("existing continuation issues a durable recovery Human for the unknown effect without automatic replay or RepairStep")
      waiting.kind shouldBe "Human"
      waiting.message should include("UNKNOWN")
      waiting.human.get.choices shouldBe Vector("AuthorizeNewAttempt", "Defer")
      waiting.human.get.sourceHistoryRecovery.nonEmpty shouldBe true
      waiting.work shouldBe None
      waiting.validation shouldBe None
      fileBytes(fixture.source) shouldBe text.getBytes(UTF_8).toVector
      indexBytes(fixture.scenario) shouldBe index
      executions(fixture.scenario) shouldBe empty
      When("a separate Advance reopens the unanswered recovery question")
      val repeated = advance(fixture).business[V.GoalPhaseAdvanceResult]
      Then("the saved Human question remains identical without another source intent or effect")
      repeated shouldBe waiting
      fileBytes(fixture.source) shouldBe text.getBytes(UTF_8).toVector
      indexBytes(fixture.scenario) shouldBe index
      executions(fixture.scenario) shouldBe empty
      fixture.scenario.invocations.map(_.pid).distinct.size shouldBe fixture.scenario.invocations.size
    }
  }
}
