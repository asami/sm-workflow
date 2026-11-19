package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import java.nio.file.Files
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V

/** Current-key adoption through ordinary commands in specification-owned hosts.
  * This is history/settings coverage, not shared lint Action qualification or
  * proof that an installed publication contains this source revision.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class Phase81RuntimeSettingsAdoptionSpec extends AnyWordSpec with NativeGoalSourceHistoryCliFixture {
  private val _source_key = "sm-workflow.source.style"

  "Phase 8.1 current runtime settings" should {
    "inherit history from source style and preserve issued review and successful checks after settings change" in {
      Given("canonical source style and history rule settings with the history style absent")
      val settings = (enabledSettings - styleKey).updated(_source_key, Json.fromString("goldenport"))
      val fixture = implemented("phase81-current-settings", settings)
      val configuration = parse(Files.readString(fixture.scenario.root.resolve(".textus/config.json")))
        .fold(error => fail(error.message), identity)
      configuration.hcursor.get[String](_source_key) shouldBe Right("goldenport")
      configuration.hcursor.downField(styleKey).succeeded shouldBe false
      configuration.hcursor.downField(ruleKey).succeeded shouldBe true

      When("ordinary Advance resolves absence-only inheritance before staging and validation")
      val review = assertReview(fixture, advance(fixture))
      val text = Files.readString(fixture.source)
      text should startWith("/**\n * @since ")
      text should include (" * @version ")
      text should endWith(" */\n" + changedText)
      val sourcebytes = fileBytes(fixture.source)
      val indexbytes = indexBytes(fixture.scenario)
      val pids = executions(fixture.scenario)

      Given("an already issued independent review, its successful check and changed current settings")
      configure(fixture.scenario, Map(_source_key -> Json.fromString("none"),
        styleKey -> Json.fromString("none"), ruleKey -> Json.fromString("{invalid")))
      When("a subsequent ordinary Advance reads that same pending work")
      val repeated = fixture.scenario.readGoalPhaseResult(advance(fixture))
      Then("the exact issued context, Session, Plan, base, history, repair counts and successful receipt remain intact")
      repeated shouldBe review
      repeated.sessionId shouldBe fixture.request.sessionId
      repeated.issuedWorkContext shouldBe review.issuedWorkContext
      repeated.repairProgress shouldBe review.repairProgress
      fileBytes(fixture.source) shouldBe sourcebytes
      indexBytes(fixture.scenario) shouldBe indexbytes
      executions(fixture.scenario) shouldBe pids
    }

    "reject obsolete settings at ordinary candidate inspection without effects or implicit conversion" in {
      val obsolete = Vector("sm-workflow.source-history.style", "sm-workflow.source-history.rule")
      for ((key, ordinal) <- obsolete.zipWithIndex; mixed <- Vector(false, true)) {
        Given("an obsolete key in " + (if (mixed) "mixed current/obsolete" else "obsolete-only") + " settings")
        val current = if (mixed) Map(styleKey -> Json.fromString("none"), _source_key -> Json.fromString("none"))
          else Map.empty[String, Json]
        val fixture = implemented("phase81-obsolete-settings-" + ordinal + "-" + mixed,
          current.updated(key, Json.Null))
        val sourcebytes = fileBytes(fixture.source)
        val indexbytes = indexBytes(fixture.scenario)
        val configpath = fixture.scenario.root.resolve(".textus/config.json")
        val configbytes = fileBytes(configpath)

        When("ordinary Advance reaches resolved source/history admission")
        val failed = advance(fixture)
        Then("the obsolete spelling fails explicitly before history, staging or product-check execution")
        failed.exitCode should not be 0
        failed.invocation.logs should include (key)
        failed.invocation.logs should include (key.replace("source-history", "source.history"))
        fileBytes(fixture.source) shouldBe sourcebytes
        indexBytes(fixture.scenario) shouldBe indexbytes
        fileBytes(configpath) shouldBe configbytes
        executions(fixture.scenario) shouldBe empty
      }
    }
  }
}
