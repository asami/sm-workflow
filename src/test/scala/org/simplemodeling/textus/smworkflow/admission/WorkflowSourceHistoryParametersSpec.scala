package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.config.{ResolvedParameter, ResolvedParameters}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.configuration.{Configuration, ConfigurationOrigin, ConfigurationResolution, ConfigurationTrace,
  ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.Property
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Per-call parameter composition contract; normal CLI syntax belongs to C2.
  * @since   Oct. 17, 2026
  * @version Oct. 17, 2026
  */
final class WorkflowSourceHistoryParametersSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import ConfigurationValue.*
  import WorkflowSourceHistoryParameters.*
  import WorkflowSourceHistoryRule.{Selection, Tags}

  private val _raw = """{
    "schemaVersion":"sm-workflow.source-history-rule.v1",
    "identity":"resolved-external-history-v1", "style":"goldenport", "format":"scala-block-history-v1",
    "sourceRoots":["product"], "extensions":["scala"], "excludedSegments":["skip"],
    "tags":{"since":"born", "version":"latest", "history":"prior", "author":"writer"},
    "datePattern":"uuuu-MM-dd", "locale":"en-US", "rollover":"day",
    "removeHistoryAtSince":false, "removeHistoryInCurrentPeriod":false,
    "header":{"open":"/*", "linePrefix":"  *  ", "close":"  */"}
  }"""
  private val _document = io.circe.parser.parse(_raw).toOption.get
  private def _value(json: Json): ConfigurationValue = json.fold(
    NullValue, BooleanValue.apply, n => NumberValue(n.toBigDecimal.get), StringValue.apply,
    vs => ListValue(vs.map(_value).toList), vs => ObjectValue(vs.toMap.view.mapValues(_value).toMap))
  private def _common(entries: (String, ConfigurationValue)*): ResolvedParameters = {
    val values = entries.toMap
    val trace = ConfigurationTrace(values.map { (key, v) =>
      key -> ConfigurationResolution(key, v, ConfigurationOrigin.Project, Nil)
    })
    ResolvedParameters.fromResolvedConfiguration(ResolvedConfiguration(Configuration(values), trace))
  }
  private def _operation(parent: ResolvedParameters, properties: Property*): ResolvedParameters =
    ResolvedParameters.forOperation(Nil, Nil, properties.toList, Some(parent))
  private def _take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(v) => v
    case other => fail(s"Source history parameters failed: $other")
  }

  "Source-history resolved parameters" should {
    "default absent style to none without accessing an invalid or missing rule" in {
      Given("common configuration with no canonical style and missing or invalid rule data")
      val absent = _common()
      val invalid = _common(ruleKey -> StringValue("{invalid"))
      When("the application consumes the resolved parameters")
      val results = Vector(absent, invalid).map(p => _take(resolveC(p)))
      Then("both select none and configuration-origin rule entries remain unused")
      results.foreach(_ shouldBe Selection("none", None))
      absent.usedEntries shouldBe empty
      invalid.usedEntries shouldBe empty
    }

    "ignore missing and invalid rules for explicit none without recording rule access" in {
      Given("explicit none in common configuration and unavailable malformed or mistyped rule values")
      val parameters = Vector(_common(styleKey -> StringValue("none")),
        _common(styleKey -> StringValue("none"), ruleKey -> StringValue("{invalid")),
        _common(styleKey -> StringValue("none"), ruleKey -> NullValue),
        _common(styleKey -> StringValue("none"), ruleKey -> BooleanValue(true)))
      When("the disabled driver selection is resolved")
      val results = parameters.map(p => _take(resolveC(p)))
      Then("only style is accessed and no rule is decoded or retained")
      results.foreach(_ shouldBe Selection("none", None))
      parameters.foreach { p =>
        p.usedEntries.map(_.key) shouldBe Vector(styleKey)
        p.usedEntries.head.source shouldBe ResolvedParameter.Source.Configuration(ConfigurationOrigin.Project)
      }
    }

    "decode common configuration structured and JSON-string external rules" in {
      Given("goldenport common configuration with external values in both accepted representations")
      val parameters = Vector(_value(_document), StringValue(_raw)).map(v =>
        _common(styleKey -> StringValue("goldenport"), ruleKey -> v))
      When("the real closed rule decoder consumes the resolved data")
      val selected = parameters.map(p => _take(resolveC(p)))
      Then("both select identical external tags dates roots layout and rollover")
      selected.head shouldBe selected.last
      selected.foreach { selection =>
        selection.style shouldBe "goldenport"
        val rule = selection.rule.get
        rule.identity shouldBe "resolved-external-history-v1"
        rule.tags shouldBe Tags("born", "latest", "prior", "writer")
        rule.sourceRoots shouldBe Vector("product")
        rule.excludedSegments shouldBe Vector("skip")
        rule.datePattern shouldBe "uuuu-MM-dd"
        rule.rollover shouldBe "day"
        rule.header.linePrefix shouldBe "  *  "
        rule.removeHistoryAtSince shouldBe false
        rule.removeHistoryInCurrentPeriod shouldBe false
      }
      parameters.foreach(_.usedEntries.map(_.key) shouldBe Vector(styleKey, ruleKey))
    }

    "consume CNCF operation none overriding common goldenport without touching its malformed rule" in {
      Given("a goldenport parent with malformed rule and the same canonical style property set to none")
      val parent = _common(styleKey -> StringValue("goldenport"), ruleKey -> StringValue("{invalid"))
      val parameters = _operation(parent, Property(styleKey, "none", None))
      When("the application consumes CNCF's already-composed operation parameters")
      val selected = _take(resolveC(parameters))
      Then("CNCF's operation value wins and the parent rule is not accessed")
      selected shouldBe Selection("none", None)
      parent.usedEntries shouldBe empty
      parameters.usedEntries.map(_.key) shouldBe Vector(styleKey)
      parameters.usedEntries.head.source shouldBe ResolvedParameter.Source.Operation("property")
    }

    "consume CNCF operation goldenport and external rule overriding common none" in {
      Given("a none parent with invalid rule and canonical operation properties selecting a valid external rule")
      val parent = _common(styleKey -> StringValue("none"), ruleKey -> NullValue)
      val parameters = _operation(parent, Property(styleKey, "goldenport", None), Property(ruleKey, _raw, None))
      When("the application consumes the composed parameters without merging sources")
      val selected = _take(resolveC(parameters))
      Then("the operation-supplied external rule is selected without reading parent values")
      selected.style shouldBe "goldenport"
      selected.rule.get.identity shouldBe "resolved-external-history-v1"
      selected.rule.get.tags.since shouldBe "born"
      parent.usedEntries shouldBe empty
      parameters.usedEntries.map(_.source).toSet shouldBe Set(ResolvedParameter.Source.Operation("property"))
    }

    "consume current resolved values on each call through the same execution context" in {
      Given("one execution context initially carrying resolved none configuration")
      val context = ExecutionContext.create()
      val firstparameters = _common(styleKey -> StringValue("none"), ruleKey -> StringValue("invalid"))
      RuntimeBindingAdmissionFixture.withResolvedParameters(context, firstparameters)
      When("the public context is consumed before and after the controlled fixture replaces its resolved values")
      val first = _take(resolveC(context))
      val secondparameters = _common(styleKey -> StringValue("goldenport"), ruleKey -> _value(_document))
      val same = RuntimeBindingAdmissionFixture.withResolvedParameters(context, secondparameters)
      val second = _take(resolveC(context))
      RuntimeBindingAdmissionFixture.withResolvedParameters(context, _common())
      val third = _take(resolveC(context))
      Then("each call uses the current public projection without a startup cache")
      same should be theSameInstanceAs context
      first shouldBe Selection("none", None)
      second.rule.get.identity shouldBe "resolved-external-history-v1"
      third shouldBe Selection("none", None)
      firstparameters.usedEntries.map(_.key) shouldBe Vector(styleKey)
      secondparameters.usedEntries.map(_.key) shouldBe Vector(styleKey, ruleKey)
    }

    "reject unsupported styles and non-string style values as configuration failures" in {
      Given("asami unknown padded empty and mistyped canonical style values")
      val styles = Vector(StringValue("asami"), StringValue("unknown"), StringValue(" goldenport"),
        StringValue(""), BooleanValue(true), NumberValue(BigDecimal(1)), NullValue,
        ListValue(List(StringValue("none"))), ObjectValue(Map.empty))
      val parameters = styles.map(style => _common(styleKey -> style, ruleKey -> _value(_document)))
      When("the exact typed style boundary consumes those values")
      val results = parameters.map(resolveC)
      Then("each fails without reading a rule or choosing a fallback")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
      parameters.foreach(_.usedEntries.map(_.key) shouldBe Vector(styleKey))
    }

    "reject missing malformed scalar list and incompatible selected rules" in {
      Given("selected goldenport with absent or invalid external rule data")
      val invalidrules = Vector(NullValue, BooleanValue(true), NumberValue(BigDecimal(1)),
        ListValue(Nil), StringValue("{invalid"), StringValue("null"), StringValue("[]"),
        StringValue("rules/history.json"), ObjectValue(Map.empty),
        _value(_document.mapObject(_.add("style", Json.fromString("asami")))),
        _value(_document.mapObject(_.add("rollover", Json.fromString("year")))),
        _value(_document.mapObject(_.add("tags", Json.arr()))))
      val parameters = _common(styleKey -> StringValue("goldenport")) +:
        invalidrules.map(v => _common(styleKey -> StringValue("goldenport"), ruleKey -> v))
      When("the selected rule is decoded without a file loader or fallback")
      val results = parameters.map(resolveC)
      Then("all unsupported representations and contracts fail through configuration admission")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "ignore abbreviated style and rule keys instead of adding compatibility aliases" in {
      Given("only the prior explanatory camel-case spelling and abbreviated rule key")
      val parameters = _common("sourceHistory.style" -> StringValue("goldenport"),
        "sourceHistory.rule" -> StringValue("invalid"))
      When("the canonical parameter boundary is resolved")
      val selected = _take(resolveC(parameters))
      Then("the absent canonical style defaults to none without reading aliases")
      selected shouldBe Selection("none", None)
      parameters.usedEntries shouldBe empty
    }

    "reject missing context or resolved-parameter authority" in {
      Given("a null execution context and null resolved parameters")
      When("either public overload is invoked without its authority")
      val results = Vector(resolveC(null: ExecutionContext), resolveC(null: ResolvedParameters))
      Then("both return typed configuration failures")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }
  }
}
