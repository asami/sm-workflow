package org.simplemodeling.textus.smworkflow.admission

import com.typesafe.config.ConfigFactory
import org.goldenport.Consequence
import org.goldenport.cncf.config.{ResolvedParameter, ResolvedParameters}
import org.goldenport.configuration.{Configuration, ConfigurationOrigin, ConfigurationResolution, ConfigurationTrace,
  ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.Property
import org.simplemodeling.scalalint.ScalaLintProfiles
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

    "resolve each key through real CNCF layers before applying all six inheritance combinations" in {
      Given("conflicting component and operation source values, with independent history overrides")
      val cases = Vector(
        ("none", None, "none"), ("none", Some("none"), "none"),
        ("none", Some("goldenport"), "goldenport"),
        ("goldenport", None, "goldenport"), ("goldenport", Some("none"), "none"),
        ("goldenport", Some("goldenport"), "goldenport"))
      cases.foreach { (source, history, expected) =>
        val opposite = if (source == "none") "goldenport" else "none"
        val componentValues = Map(_source -> StringValue(opposite)) ++ history.map { value =>
          _history -> StringValue(if (value == "none") "goldenport" else "none")
        }
        val common = _parameters(_source -> StringValue(source))
        val component = ResolvedParameters.fromComponentConfiguration(Configuration(componentValues), "lint-test", Some(common))
        val properties = List(Property(_source, source, None)) ++ history.map(v => Property(_history, v, None))
        val parameters = ResolvedParameters.forOperation(Nil, Nil, properties, Some(component))
        When("the published CNCF resolver composes each key before source/history selection")
        val selected = _take(WorkflowScalaLintParameters.selectionC(parameters))
        Then("operation values win independently and absence alone inherits the resolved source")
        selected.source shouldBe source
        selected.history shouldBe expected
        component.usedEntries shouldBe empty
        parameters.usedEntries.map(_.source).toSet shouldBe Set(ResolvedParameter.Source.Operation("property"))
      }
    }

    "retain a component history override when only source is overridden by the operation" in {
      Given("common history goldenport, component history none and operation source goldenport")
      val common = _parameters(_source -> StringValue("none"), _history -> StringValue("goldenport"))
      val component = ResolvedParameters.fromComponentConfiguration(
        Configuration(Map(_history -> StringValue("none"))), "lint-test", Some(common))
      val parameters = ResolvedParameters.forOperation(Nil, Nil,
        List(Property(_source, "goldenport", None)), Some(component))
      When("independent per-key lookup precedes history inheritance")
      val selected = _take(WorkflowScalaLintParameters.selectionC(parameters))
      Then("source comes from the operation and explicit history none comes from the component")
      selected.source shouldBe "goldenport"
      selected.history shouldBe "none"
      parameters.usedEntries.find(_.key == _history).get.source shouldBe ResolvedParameter.Source.Component("lint-test")
      common.usedEntries shouldBe empty
    }

    "reject obsolete keys before inheritance and explicit none including null and mixed configuration" in {
      Given("each obsolete key alone or alongside current source and disabled history values")
      ScalaLintProfiles.obsoleteHistoryKeys.foreach { (old, replacement) =>
        Vector(StringValue("goldenport"), NullValue).foreach { value =>
          Vector(Vector(old -> value), Vector(old -> value, _source -> StringValue("goldenport"),
            _history -> StringValue("none"))).foreach { entries =>
            When("the resolved parameter admission checks key presence")
            val parameters = _parameters(entries*)
            val result = WorkflowScalaLintParameters.selectionC(parameters)
            Then("the obsolete spelling fails with its replacement before source or current history is used")
            result shouldBe a[Consequence.Failure[?]]
            result.toString should include (old)
            result.toString should include (replacement)
            parameters.usedEntries.map(_.key) should not contain _source
            parameters.usedEntries.map(_.key) should not contain _history
          }
        }
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

    "reject malformed source styles even when an explicit valid history override is supplied" in {
      Given("invalid empty, padded, unknown, null and mistyped source values with history none")
      val invalid = Vector(StringValue(""), StringValue(" goldenport"), StringValue("unknown"), NullValue,
        BooleanValue(false), NumberValue(BigDecimal(1)), ListValue(Nil), ObjectValue(Map.empty))
      When("resolved source selection is admitted before history inheritance")
      val results = invalid.map(value => WorkflowScalaLintParameters.selectionC(
        _parameters(_source -> value, _history -> StringValue("none"))))
      Then("all malformed source styles fail instead of selecting a fallback")
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
