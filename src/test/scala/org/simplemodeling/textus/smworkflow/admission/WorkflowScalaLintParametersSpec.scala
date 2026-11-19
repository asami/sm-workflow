package org.simplemodeling.textus.smworkflow.admission

import com.typesafe.config.ConfigFactory
import org.goldenport.Consequence
import org.goldenport.cncf.config.ResolvedParameters
import org.goldenport.configuration.{Configuration, ConfigurationOrigin, ConfigurationResolution, ConfigurationTrace,
  ConfigurationValue, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Resolved source/profile and history-inheritance specifications.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class WorkflowScalaLintParametersSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import ConfigurationValue.*
  private def _parameters(entries: (String, ConfigurationValue)*): ResolvedParameters = {
    val values = entries.toMap
    val trace = ConfigurationTrace(values.map { (key, value) =>
      key -> ConfigurationResolution(key, value, ConfigurationOrigin.Project, Nil)
    })
    ResolvedParameters.fromResolvedConfiguration(ResolvedConfiguration(Configuration(values), trace))
  }
  private def _take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"Expected resolved lint data: $other")
  }
  private val _source = WorkflowScalaLintParameters.sourceKey
  private val _history = WorkflowSourceHistoryParameters.styleKey

  "Shared Scala lint parameters" should {
    "retain nonempty generic defaults and noFinalize without project configuration" in {
      Given("no source, history or project configuration")
      When("the normal resolved parameter adapter selects installed shared data")
      val effective = _take(WorkflowScalaLintParameters.resolveC(_parameters(), None))
      Then("baseline safety applies independently of team selection")
      effective.rules.take(2) shouldBe Vector("DisableSyntax", "NoOptionGet")
      effective.rules should contain allOf ("NoTryGet", "NoDiscardedFailureResult", "NoInferredAny",
        "NoMutableEqualityKey", "NoEmbeddedCredentials", "NoStructuralIndentation")
      effective.rules.distinct shouldBe effective.rules
      effective.config.getBoolean("DisableSyntax.noFinalize") shouldBe true
      effective.selection.source shouldBe "none"
      effective.selection.history shouldBe "none"
    }

    "inherit history only on absence and keep explicit none independent of source naming" in {
      Given("the six valid source and history selections")
      val cases = Vector(
        ("none", None, "none"), ("none", Some("none"), "none"),
        ("none", Some("goldenport"), "goldenport"),
        ("goldenport", None, "goldenport"), ("goldenport", Some("none"), "none"),
        ("goldenport", Some("goldenport"), "goldenport"))
      When("already resolved values enter the shared resolver")
      val results = cases.map { (source, history, expected) =>
        val entries = Vector(_source -> StringValue(source)) ++ history.map(v => _history -> StringValue(v))
        val effective = _take(WorkflowScalaLintParameters.resolveC(_parameters(entries*), None))
        (source, expected, effective)
      }
      Then("only source selects naming, and all cases retain the baseline")
      results.foreach { (source, history, effective) =>
        effective.selection.history shouldBe history
        effective.rules.contains("GoldenportPrivateMemberNaming") shouldBe (source == "goldenport")
        effective.rules.contains("GoldenportHistoryCommentPlacement") shouldBe (history == "goldenport")
        effective.rules should contain ("NoOptionGet")
        effective.config.getBoolean("DisableSyntax.noFinalize") shouldBe true
      }
    }

    "preserve baseline union and noFinalize when a project adds a rule" in {
      Given("Goldenport naming with history disabled and an additive project rule")
      val project = ConfigFactory.parseString("rules = [RedundantSyntax, NoOptionGet]")
      val parameters = _parameters(_source -> StringValue("goldenport"), _history -> StringValue("none"))
      val baseline = _take(WorkflowScalaLintParameters.resolveC(_parameters(), None))
      val inherited = _take(WorkflowScalaLintParameters.resolveC(parameters, None))
      When("project rules are merged by the shared resolver")
      val effective = _take(WorkflowScalaLintParameters.resolveC(parameters, Some(project)))
      Then("inherited rules survive in order without duplication")
      effective.rules.take(baseline.rules.size) shouldBe baseline.rules
      inherited.rules.drop(baseline.rules.size) shouldBe Vector("GoldenportPrivateMemberNaming",
        "GoldenportLocalHelperNaming", "GoldenportFlatcaseNaming", "GoldenportVisibilityNaming",
        "GoldenportNoDiscardedFailureResult")
      effective.rules shouldBe (inherited.rules :+ "RedundantSyntax")
      effective.rules.distinct shouldBe effective.rules
      effective.rules should not contain "GoldenportHistoryCommentPlacement"
      effective.config.getBoolean("DisableSyntax.noFinalize") shouldBe true
    }

    "reject malformed present styles without treating them as absence" in {
      Given("invalid empty, padded, null and mistyped explicit history overrides")
      val invalid = Vector(StringValue(""), StringValue(" none"), StringValue("unknown"), NullValue, BooleanValue(false))
      When("source convention would otherwise supply a Goldenport default")
      val results = invalid.map(value => WorkflowScalaLintParameters.selectionC(
        _parameters(_source -> StringValue("goldenport"), _history -> value)))
      Then("each returns a configuration failure")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "leave the history rule untouched for explicit none while requiring it for inherited Goldenport history" in {
      Given("an invalid history rule and source Goldenport")
      val key = WorkflowSourceHistoryParameters.ruleKey
      val disabled = _parameters(_source -> StringValue("goldenport"), _history -> StringValue("none"), key -> StringValue("invalid"))
      val inherited = _parameters(_source -> StringValue("goldenport"), key -> StringValue("invalid"))
      When("the existing history driver resolves its effective style")
      val none = _take(WorkflowSourceHistoryParameters.resolveC(disabled))
      val missing = WorkflowSourceHistoryParameters.resolveC(inherited)
      Then("none disables history without reading its rule; inherited history fails on invalid data")
      none.style shouldBe "none"
      disabled.usedEntries.map(_.key) should not contain key
      missing shouldBe a[Consequence.Failure[?]]
      inherited.usedEntries.map(_.key) should contain (key)
    }
  }
}
