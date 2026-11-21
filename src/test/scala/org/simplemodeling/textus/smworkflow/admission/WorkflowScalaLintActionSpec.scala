package org.simplemodeling.textus.smworkflow.admission

import com.typesafe.config.{ConfigFactory, ConfigRenderOptions}
import io.circe.Json
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64
import scala.jdk.CollectionConverters.*
import org.goldenport.Consequence
import org.goldenport.cncf.config.ResolvedParameters
import org.goldenport.configuration.{Configuration, ConfigurationValue}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.scalalint.{ScalaLintInput, ScalaLintProfiles}

/** Exact immutable producer input and fixed-build decoding, without executing
  * SBT or claiming an installed consumer acceptance.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
// REASON: fixture construction/assertions require the deliberately selected
// Some/valid JSON shapes; a missing fixture value must fail this specification.
@SuppressWarnings(Array("scalafix:NoOptionGet"))
final class WorkflowScalaLintActionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val base = "a" * 40
  private def take[A](value: Consequence[A]): A = value.toOption.getOrElse(fail(value.toString))
  private def parameters(source: String) = ResolvedParameters.fromComponentConfiguration(
    Configuration(Map(ScalaLintProfiles.sourceKey -> ConfigurationValue.StringValue(source),
      ScalaLintProfiles.historyKey -> ConfigurationValue.StringValue("none"))), "specification", None)
  private def envelope(input: org.goldenport.cncf.processexecution.SbtLintInput): String = {
    val config = io.circe.parser.parse(input.configJson).toOption.get
    val json = Json.obj("identity" -> Json.fromString(input.identity), "revision" -> Json.fromString(input.revision), "config" -> config)
    Base64.getUrlEncoder.withoutPadding.encodeToString(json.noSpaces.getBytes(UTF_8))
  }

  "The typed shared lint Action" should {
    "bind the same fully merged config, native delta base and source provenance to the fixed build" in {
      Given("goldenport source, independent history none and additive project differences")
      val project = Some(ConfigFactory.parseString("""rules = [NoOptionGet]
        |DisableSyntax.noFinalize = true
        |""".stripMargin))
      val input = take(WorkflowScalaLintAction.inputC(parameters("goldenport"), project, base))
      When("the trusted build decodes CNCF's exact transport envelope")
      val decoded = ScalaLintInput.decode(envelope(input))
      val effective = take(WorkflowScalaLintParameters.resolveC(parameters("goldenport"), project))
      Then("the actual config, ordered rules, project overrides and profiles stay immutable")
      decoded.identity shouldBe input.identity
      decoded.revision shouldBe input.revision
      decoded.diffBase shouldBe base
      effective.config.entrySet().asScala.foreach { entry =>
        decoded.config.getValue(entry.getKey) shouldBe entry.getValue
      }
      decoded.config.getStringList(ScalaLintInput.contextKey + ".profiles") shouldBe effective.snapshot.getStringList("profiles")
      decoded.config.getString(ScalaLintInput.contextKey + ".sourceStyle") shouldBe "goldenport"
      decoded.config.getString(ScalaLintInput.contextKey + ".historyStyle") shouldBe "none"
      take(WorkflowScalaLintAction.inputC(parameters("none"), None, "b" * 40)) should not be input
      ScalaLintInput.decode(envelope(input)) shouldBe decoded
      io.circe.parser.parse(input.configJson).toOption.get.isObject shouldBe true
      decoded.config.getBoolean("DisableSyntax.noFinalize") shouldBe true
    }

    "fail selected invalid input instead of falling back to ambient .scalafix.conf" in {
      val input = take(WorkflowScalaLintAction.inputC(parameters("none"), None, base))
      ScalaLintInput.selected(Map.empty) shouldBe None
      Vector("", "=", envelope(input) + "=", "e30").foreach { malformed =>
        intercept[Exception](ScalaLintInput.selected(Map(ScalaLintInput.property -> malformed)))
      }
      WorkflowScalaLintAction.inputC(parameters("none"), None, "HEAD").isSuccess shouldBe false
      WorkflowScalaLintAction.inputC(parameters("none"),
        Some(ConfigFactory.parseString(ScalaLintInput.contextKey + ".diffBase = pretend")), base).isSuccess shouldBe false
      WorkflowScalaLintAction.inputC(parameters("bogus"), None, base).isSuccess shouldBe false
    }

    "activate only the unique host-declared FixCheck and retain old program behavior" in {
      def plan(enabled: Boolean, duplicate: Boolean = false): Json = {
        val program = Json.obj("target" -> Json.fromString("lint-workspace"), "operation" -> Json.fromString("fix-check"),
          "lintInput" -> Json.fromBoolean(enabled))
        Json.obj("sbtPrograms" -> Json.fromValues(if (duplicate) Vector(program, program) else Vector(program)),
          "workspaces" -> Json.arr(Json.obj("identity" -> Json.fromString("lint-workspace"), "root" -> Json.fromString("/specification/repository"))))
      }
      take(WorkflowScalaLintAction.rootC(plan(false), "lint-workspace")) shouldBe None
      take(WorkflowScalaLintAction.rootC(plan(true), "other-workspace")) shouldBe None
      take(WorkflowScalaLintAction.rootC(plan(true), "lint-workspace")).map(_.toString) shouldBe Some("/specification/repository")
      WorkflowScalaLintAction.rootC(plan(true, true), "lint-workspace").isSuccess shouldBe false
    }
  }
}
