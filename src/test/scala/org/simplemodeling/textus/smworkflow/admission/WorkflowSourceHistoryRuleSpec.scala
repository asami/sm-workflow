package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.time.LocalDate
import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Executable C1 contract; fixtures supply external data without file access.
  * @since   Oct. 17, 2026
  * @version Oct. 17, 2026
  */
final class WorkflowSourceHistoryRuleSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import WorkflowSourceHistoryRule.*

  private val _document = Json.obj(
    "schemaVersion" -> Json.fromString("sm-workflow.source-history-rule.v1"),
    "identity" -> Json.fromString("external-fixture-v1"),
    "style" -> Json.fromString("goldenport"),
    "format" -> Json.fromString("scala-block-history-v1"),
    "sourceRoots" -> Json.arr(Json.fromString("src/main/scala"), Json.fromString("src/test/scala")),
    "extensions" -> Json.arr(Json.fromString("scala")),
    "excludedSegments" -> Json.fromValues(Vector("generated", "vendor", "target", "sample", "samples").map(Json.fromString)),
    "tags" -> Json.obj("since" -> Json.fromString("@since"), "version" -> Json.fromString("@version"),
      "history" -> Json.fromString("version"), "author" -> Json.fromString("@author")),
    "datePattern" -> Json.fromString("MMM. ppd, uuuu"),
    "locale" -> Json.fromString("en-US"),
    "rollover" -> Json.fromString("month"),
    "removeHistoryAtSince" -> Json.True,
    "removeHistoryInCurrentPeriod" -> Json.True,
    "header" -> Json.obj("open" -> Json.fromString("/**"), "linePrefix" -> Json.fromString(" * "),
      "close" -> Json.fromString(" */")))

  private def _take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail(s"Source history operation failed: $other")
  }
  private def _rule: Rule = _take(decodeC(_document.noSpaces))
  private def _date(value: String): LocalDate = LocalDate.parse(value)
  private def _input(text: String, day: String = "2026-04-09"): Input =
    Input("src/main/scala/Example.scala", text, _date(day), Some(_date("2026-01-06")), None, false)
  private val _code = "package example\nobject Example { val answer = 42 }\n"
  private def _header(lines: String*): String = (Vector("/**") ++ lines.map(" * " + _) ++ Vector(" */", "")).mkString("\n")
  private def _apply_text(text: String, selected: Rule = _rule, day: String = "2026-04-09"): Result =
    _take(applyC(selected, _input(text, day)))
  private def _field(name: String, value: Json): Json = _document.mapObject(_.add(name, value))
  private def _rejected(values: Seq[String]): Unit =
    values.foreach(raw => decodeC(raw) shouldBe a[Consequence.Failure[?]])

  "External source-history selection" should {
    "ignore an irrelevant rule when disabled" in {
      Given("none and an invalid typed rule")
      val irrelevant = Some(_rule.copy(style = "unsupported"))
      When("the pure selection boundary receives the disabled style")
      val selected = _take(selectionC("none", irrelevant))
      Then("the selection succeeds without retaining or validating the irrelevant rule")
      selected shouldBe Selection("none", None)
      _take(selectionC("none", None)) shouldBe Selection("none", None)
      _take(selectionC("none", Some(null))) shouldBe Selection("none", None)
    }

    "admit an enabled matching external rule" in {
      Given("a decoded user-supplied goldenport rule")
      val external = Some(_rule)
      When("goldenport is selected")
      val selected = selectionC("goldenport", external)
      Then("the exact valid rule is retained without IO")
      _take(selected) shouldBe Selection("goldenport", external)
    }

    "reject unsupported styles and missing or incompatible enabled rules" in {
      Given("unsupported styles and missing null or incompatible rules")
      val rules = Vector(None, Some(null), Some(_rule.copy(style = "asami")),
        Some(_rule.copy(tags = _rule.tags.copy(version = _rule.tags.since))))
      When("selection consumes those settings")
      val invalidstyles = Vector(null, "", "asami", "unknown", " goldenport").map(selectionC(_, None))
      val invalidrules = rules.map(selectionC("goldenport", _))
      Then("each fails through the typed configuration boundary")
      (invalidstyles ++ invalidrules).foreach(_ shouldBe a[Consequence.Failure[?]])
    }
  }

  "External source-history rule decoding" should {
    "retain the declared external contract" in {
      Given("an explicit complete JSON rule document")
      When("the document is decoded")
      val selected = _take(decodeC(_document.noSpaces))
      Then("identity, tags, date pattern, locale, layout and cleanup policy remain external data")
      selected.identity shouldBe "external-fixture-v1"
      selected.tags shouldBe Tags("@since", "@version", "version", "@author")
      selected.datePattern shouldBe "MMM. ppd, uuuu"
      selected.locale shouldBe "en-US"
      selected.header shouldBe Header("/**", " * ", " */")
      selected.rollover shouldBe "month"
      selected.removeHistoryAtSince shouldBe true
      selected.removeHistoryInCurrentPeriod shouldBe true
    }

    "reject missing unknown mistyped and duplicate JSON members" in {
      Given("a closed root and closed tags and header records")
      val root = _document.asObject.get
      val missing = root.keys.toVector.map(key => Json.fromJsonObject(root.remove(key)).noSpaces)
      val extra = _field("regex", Json.fromString(".*"))
      val extratags = _field("tags", _document.hcursor.downField("tags").focus.get.mapObject(_.add("other", Json.True)))
      val extraheader = _field("header", _document.hcursor.downField("header").focus.get.mapObject(_.add("other", Json.True)))
      val duplicated = _document.noSpaces.stripSuffix("}") + ",\"identity\":\"other\"}"
      val escapedduplicate = _document.noSpaces.replace("\"identity\":", "\"ident\\u0069ty\":\"other\",\"identity\":")
      When("the interpreter decodes incomplete or ambiguous data")
      val values = missing ++ Vector(extra.noSpaces, extratags.noSpaces, extraheader.noSpaces, duplicated, escapedduplicate,
        _field("removeHistoryAtSince", Json.fromString("true")).noSpaces, "null", "[]", "{", null)
      Then("every invalid shape is rejected without a fallback rule")
      _rejected(values)
    }

    "reject unsupported contracts unsafe tags and ambiguous source selection" in {
      Given("documents with unsupported capabilities or unsafe or overlapping declarations")
      val variants = Vector(
        _field("schemaVersion", Json.fromString("foreign")), _field("identity", Json.fromString(" ")),
        _field("style", Json.fromString("asami")), _field("format", Json.fromString("shell")),
        _field("extensions", Json.arr(Json.fromString("java"))), _field("rollover", Json.fromString("year")),
        _field("sourceRoots", Json.arr(Json.fromString("src"), Json.fromString("src/main"))),
        _field("sourceRoots", Json.arr(Json.fromString("../src"))),
        _field("excludedSegments", Json.arr(Json.fromString("a/b"))),
        _field("tags", Json.obj("since" -> Json.fromString("@since"), "version" -> Json.fromString("@since"),
          "history" -> Json.fromString("version"), "author" -> Json.fromString("@author"))),
        _field("tags", _document.hcursor.downField("tags").focus.get.mapObject(_.add("since", Json.fromString("@since\n*/")))))
      When("the external contract is decoded")
      val values = variants.map(_.noSpaces)
      Then("unsupported or ambiguous values cannot select an implicit convention")
      _rejected(values)
    }

    "reject invalid date patterns locales and comment delimiters" in {
      Given("rules with invalid or lossy dates and unsafe comment layouts")
      val variants = Vector(_field("datePattern", Json.fromString("not-a-pattern")),
        _field("datePattern", Json.fromString("MMM d")), _field("datePattern", Json.fromString("uuuu-MM-dd'\n'")),
        _field("locale", Json.fromString("not_a_locale")), _field("locale", Json.fromString("und")),
        _field("header", Json.obj("open" -> Json.fromString("//"), "linePrefix" -> Json.fromString(" * "), "close" -> Json.fromString(" */"))),
        _field("header", Json.obj("open" -> Json.fromString("/**"), "linePrefix" -> Json.fromString(" */ "), "close" -> Json.fromString(" */"))))
      When("the interpreter decodes their formatting capabilities")
      val values = variants.map(_.noSpaces)
      Then("each fails before any source can be rewritten")
      _rejected(values)
    }
  }

  "External source-history interpretation" should {
    "exclude unselected roots extensions and configured segments before parsing source" in {
      Given("malformed source dates in excluded or unselected paths")
      val source = "/* @version not-a-date */\n\"unterminated"
      val paths = Vector("other/Example.scala", "src/main/scala/Example.java", "src/main/scala2/Example.scala") ++
        Vector("generated", "vendor", "target", "sample", "samples").map(s => s"src/test/scala/$s/Example.scala")
      When("the rule receives those paths")
      val results = paths.map(path => _take(applyC(_rule, _input(source).copy(path = path))))
      Then("each is unchanged and excluded without source-date or literal parsing")
      results.foreach { result =>
        result.outcome shouldBe "excluded"
        result.text shouldBe source
        result.providerIdentity shouldBe "external-fixture-v1"
      }
    }

    "compress the current month while preserving narrative provenance and historical order" in {
      Given("a since date, unsorted old histories, current-month history and known author")
      val original = _header("Narrative and origin: migrated source.", "@since   Jan.  6, 2026",
        "version Mar. 20, 2026", "version Jan. 21, 2026", "version Apr.  1, 2026",
        "@version Apr.  3, 2026", "@author Existing Author", "@deprecated retained metadata") + _code
      When("a later date in the current month is supplied")
      val result = _apply_text(original)
      Then("only declared cleanup changes the version block and all other source remains exact")
      result.text shouldBe _header("Narrative and origin: migrated source.", "@since   Jan.  6, 2026",
        "version Mar. 20, 2026", "version Jan. 21, 2026", "@version Apr.  9, 2026",
        "@author Existing Author", "@deprecated retained metadata") + _code
      result.outcome shouldBe "updated"
      result.dateUncertain shouldBe false
    }

    "convert a cross-month latest version in its original history position" in {
      Given("an old latest before a later-positioned history fact")
      val original = _header("@since Jan.  6, 2026", "version Feb. 19, 2026", "@version Mar. 24, 2026",
        "version Jan. 21, 2026", "@author Known Author") + _code
      When("the update crosses the month boundary")
      val result = _apply_text(original)
      Then("existing relative history order survives and the single latest follows all histories")
      result.text shouldBe _header("@since Jan.  6, 2026", "version Feb. 19, 2026", "version Mar. 24, 2026",
        "version Jan. 21, 2026", "@version Apr.  9, 2026", "@author Known Author") + _code
    }

    "remove exact since overlap but retain a different day in the since month" in {
      Given("an exact since duplicate and a different day in that same old month")
      val original = _header("@since Mar. 21, 2026", "version Mar. 21, 2026", "version Mar. 22, 2026",
        "@version Mar. 23, 2026") + _code
      When("the next month is supplied")
      val result = _apply_text(original)
      Then("only the exact since overlap is removed")
      result.text shouldBe _header("@since Mar. 21, 2026", "version Mar. 22, 2026", "version Mar. 23, 2026",
        "@version Apr.  9, 2026") + _code
    }

    "obey disabled cleanup flags in the external document" in {
      Given("an external rule that retains since overlap and current-period histories")
      val selected = _take(decodeC(_document.mapObject(_.add("removeHistoryAtSince", Json.False)
        .add("removeHistoryInCurrentPeriod", Json.False)).noSpaces))
      val original = _header("@since Jan.  6, 2026", "version Jan.  6, 2026", "version Apr.  1, 2026",
        "@version Apr.  3, 2026") + _code
      When("the rule is applied in the same month")
      val result = _apply_text(original, selected)
      Then("existing histories follow both external flags while the old latest is compressed")
      result.text shouldBe _header("@since Jan.  6, 2026", "version Jan.  6, 2026", "version Apr.  1, 2026",
        "@version Apr.  9, 2026") + _code
    }

    "use external day and never rollover policies" in {
      Given("a previous latest in the same month and caller-selected day or never rollover")
      val original = _header("@since Jan.  6, 2026", "version Apr.  1, 2026", "@version Apr.  3, 2026") + _code
      val rules = Vector("day", "never").map(value => _take(decodeC(_field("rollover", Json.fromString(value)).noSpaces)))
      When("a different update day is supplied")
      val results = rules.map(selected => _apply_text(original, selected))
      Then("the different-day latest is retained as history under both external policies")
      results.foreach(_.text shouldBe _header("@since Jan.  6, 2026", "version Apr.  1, 2026",
        "version Apr.  3, 2026", "@version Apr.  9, 2026") + _code)
    }

    "use changed external tags dates roots exclusions and layout" in {
      Given("a user document with alternative tags, ISO dates and a different source root and prefix")
      val custom = _document.mapObject(_.add("sourceRoots", Json.arr(Json.fromString("product")))
        .add("excludedSegments", Json.arr(Json.fromString("skip")))
        .add("tags", Json.obj("since" -> Json.fromString("born"), "version" -> Json.fromString("latest"),
          "history" -> Json.fromString("prior"), "author" -> Json.fromString("writer")))
        .add("datePattern", Json.fromString("uuuu-MM-dd"))
        .add("header", Json.obj("open" -> Json.fromString("/*"), "linePrefix" -> Json.fromString("  *  "),
          "close" -> Json.fromString("  */"))))
      val selected = _take(decodeC(custom.noSpaces))
      When("an included file without history is supplied")
      val result = _take(applyC(selected, _input(_code).copy(path = "product/Example.scala", author = Some("Known Writer"))))
      Then("new history uses the external values and the original source is preserved")
      result.text shouldBe "/*\n  *  born 2026-01-06\n  *  latest 2026-04-09\n  *  writer Known Writer\n  */\n" + _code
      _take(applyC(selected, _input("/* latest invalid */").copy(path = "product/skip/Example.scala"))).outcome shouldBe "excluded"
    }

    "recognize changed external date tags in existing comments" in {
      Given("a decoded rule whose tags differ from the example convention")
      val selected = _rule.copy(tags = Tags("born", "latest", "prior", "writer"), rollover = "day")
      val original = _header("born Jan.  6, 2026", "latest Apr.  3, 2026", "writer Known Writer") + _code
      When("that external rule interprets an existing managed comment")
      val result = _apply_text(original, selected)
      Then("the changed tags and rollover govern the actual update")
      result.text shouldBe _header("born Jan.  6, 2026", "prior Apr.  3, 2026", "latest Apr.  9, 2026",
        "writer Known Writer") + _code
    }

    "find a long managed comment after long package and import preambles" in {
      Given("a header located far beyond the file beginning and containing a long narrative")
      val preamble = "package example\n" + Vector.fill(180)("import example.Dependency\n").mkString
      val narrative = Vector.tabulate(240)(i => s"Narrative line $i retained exactly.")
      val original = preamble + _header((narrative ++ Vector("@since Jan.  6, 2026", "@version Mar. 24, 2026"))*) + _code
      When("the complete file is interpreted")
      val result = _apply_text(original)
      Then("the existing comment is updated in place with all narrative and preamble preserved")
      result.text shouldBe preamble + _header((narrative ++ Vector("@since Jan.  6, 2026",
        "version Mar. 24, 2026", "@version Apr.  9, 2026"))*) + _code
    }

    "ignore markers in strings triple strings characters backticks and line comments" in {
      Given("all noncomment Scala literal forms containing comment-looking text")
      val literals = "val ordinary = \"/* @version invalid */\"\n" +
        "val escaped = \"\\\"/* @since invalid */\"\n" +
        "val triple = \"\"\"/*\n * @version invalid\n */\"\"\"\n" +
        "val slash = '/'\nval quote = '\\''\nval `/* @version invalid */` = 1\n" +
        "// /* @version invalid */\n"
      val original = literals + _header("@since Jan.  6, 2026", "@version Mar. 24, 2026") + _code
      When("the lexical scanner locates actual block comments")
      val result = _apply_text(original)
      Then("only the genuine managed comment changes")
      result.text shouldBe literals + _header("@since Jan.  6, 2026", "version Mar. 24, 2026",
        "@version Apr.  9, 2026") + _code
    }

    "preserve nested quoted interpolation literals while updating only the genuine header" in {
      Given("a genuine header and quoted interpolation expressions containing a comment opener and apparent managed block")
      val apparent = _header("@since Jan.  6, 2026", "@version Mar. 24, 2026").replace("\n", "\\n")
      val body = "object Example { val text = s\"${\"/*\"} ${\"" + apparent + "\"}\" }\n"
      val original = _header("@since Jan.  6, 2026", "@version Mar. 24, 2026") + body
      When("the rule interprets the quoted interpolation")
      val result = _apply_text(original)
      Then("the genuine header alone changes and every body byte survives")
      result.text shouldBe _header("@since Jan.  6, 2026", "version Mar. 24, 2026", "@version Apr.  9, 2026") + body
      When("the same date is reapplied")
      val repeated = _apply_text(result.text)
      Then("the entire result is unchanged")
      repeated.text shouldBe result.text
      repeated.outcome shouldBe "unchanged"
    }

    "balance nested interpolation around expression braces comments characters and backticks" in {
      Given("nested interpolations with braces hidden in line comments nested block comments character tokens and identifiers")
      val body = """object Example {
  val text = s"${{
    val `}` = '}'
    val brace = '{'
    /* { /* } */ } */ // }
    s"${{ "/*" }}"
  }}"
}
"""
      val original = _header("@since Jan.  6, 2026", "@version Mar. 24, 2026") + body
      When("the rule interprets all nested expression boundaries")
      val result = _apply_text(original)
      Then("only genuine code braces balance expressions and the complete body remains exact")
      result.text shouldBe _header("@since Jan.  6, 2026", "version Mar. 24, 2026", "@version Apr.  9, 2026") + body
      When("the same date is reapplied")
      val repeated = _apply_text(result.text)
      Then("the complete result stays unchanged")
      repeated.text shouldBe result.text
      repeated.outcome shouldBe "unchanged"
    }

    "preserve outer and inner triple quoted interpolation literals" in {
      Given("genuine headers paired with outer triple strings inner triple strings and nested triple interpolations")
      val quotes = "\"\"\""
      val apparent = _header("@since Jan.  6, 2026", "@version Mar. 24, 2026")
      val bodies = Vector(
        "val text = s" + quotes + "${\"/*\"}\n" + apparent + quotes + "\n",
        "val text = s\"${" + quotes + apparent + quotes + "}\"\n",
        "val text = s" + quotes + "${s" + quotes + "${\"/*\"}\n" + apparent + quotes + "}" + quotes + "\n")
      val header = _header("@since Jan.  6, 2026", "@version Mar. 24, 2026")
      When("the rule interprets each combination of triple and quoted literals")
      val results = bodies.map(body => _apply_text(header + body))
      Then("each result updates only its genuine header and preserves the complete body")
      results.zip(bodies).foreach { (result, body) =>
        result.text shouldBe _header("@since Jan.  6, 2026", "version Mar. 24, 2026", "@version Apr.  9, 2026") + body
      }
      When("the same date is reapplied to every result")
      val repeated = results.map(result => _apply_text(result.text))
      Then("every triple quoted combination stays byte identical")
      repeated.zip(results).foreach { (second, first) =>
        second.text shouldBe first.text
        second.outcome shouldBe "unchanged"
      }
    }

    "recognize raw qualified and backtick interpolators while preserving escaped dollars" in {
      Given("raw custom qualified and backtick interpolators with nested literals and escaped literal interpolation openers")
      val bodies = Vector("raw", "custom", "strings.custom", "`custom-name`").map { prefix =>
        "val text = " + prefix + "\"$${ literal /* @version invalid */ ${\"/*\"}\"\n"
      }
      val header = _header("@since Jan.  6, 2026", "@version Mar. 24, 2026")
      When("the rule interprets each directly prefixed literal")
      val results = bodies.map(body => _apply_text(header + body))
      Then("escaped dollars remain text and nested literal contents never become managed comments")
      results.zip(bodies).foreach { (result, body) =>
        result.text shouldBe _header("@since Jan.  6, 2026", "version Mar. 24, 2026", "@version Apr.  9, 2026") + body
      }
      When("the same date is reapplied to every custom literal")
      val repeated = results.map(result => _apply_text(result.text))
      Then("all interpolator forms retain their exact text")
      repeated.zip(results).foreach { (second, first) =>
        second.text shouldBe first.text
        second.outcome shouldBe "unchanged"
      }
    }

    "preserve raw backslashes before escaped dollars while updating only the genuine header" in {
      Given("a genuine header and a raw literal with a backslash before escaped dollars and an apparent managed comment")
      val body = """object Example { val text = raw"\$${ /* @version Mar. 24, 2026 */ }" }""" + "\n"
      val original = _header("@since Jan.  6, 2026", "@version Mar. 24, 2026") + body
      When("the source-history rule interprets the complete source")
      val result = _apply_text(original)
      Then("only the genuine header changes and the entire raw literal remains exact")
      result.text shouldBe _header("@since Jan.  6, 2026", "version Mar. 24, 2026", "@version Apr.  9, 2026") + body
      When("the same date is reapplied")
      val repeated = _apply_text(result.text)
      Then("the complete source remains unchanged")
      repeated.text shouldBe result.text
      repeated.outcome shouldBe "unchanged"
    }

    "insert history before a raw escaped-dollar literal without changing its apparent managed comment" in {
      Given("source without a genuine header containing a raw backslash and escaped dollars before an apparent managed comment")
      val body = """object Example { val text = raw"\$${ /* @version Mar. 24, 2026 */ }" }""" + "\n"
      When("the source-history rule supplies the missing header")
      val result = _apply_text(body)
      Then("the deterministic initial header precedes the complete original body")
      result.text shouldBe _header("@since Jan.  6, 2026", "@version Apr.  9, 2026") + body
      When("the same date is reapplied")
      val repeated = _apply_text(result.text)
      Then("the inserted header and original body remain unchanged")
      repeated.text shouldBe result.text
      repeated.outcome shouldBe "unchanged"
    }

    "retain actual raw interpolation after a backslash around nested apparent comment strings" in {
      Given("a genuine header and raw interpolation expressions preceded by backslashes with nested apparent comment strings")
      val body = """object Example { val text = raw"\${"/*"} \${"/* @version Mar. 24, 2026 */"}" }""" + "\n"
      val original = _header("@since Jan.  6, 2026", "@version Mar. 24, 2026") + body
      When("the source-history rule interprets the interpolation expressions")
      val result = _apply_text(original)
      Then("only the genuine header changes and every expression and literal byte survives")
      result.text shouldBe _header("@since Jan.  6, 2026", "version Mar. 24, 2026", "@version Apr.  9, 2026") + body
      When("the same date is reapplied")
      val repeated = _apply_text(result.text)
      Then("the complete nested source remains unchanged")
      repeated.text shouldBe result.text
      repeated.outcome shouldBe "unchanged"
    }

    "preserve consecutive raw backslashes before escaped dollars and apparent managed comments" in {
      Given("genuine headers and raw literals with consecutive backslashes before escaped dollars")
      val bodies = Vector(2, 3, 4).map { count =>
        "object Example { val text = raw\"" + ("\\" * count) + "$${ /* @version Mar. 24, 2026 */ }\" }\n"
      }
      val header = _header("@since Jan.  6, 2026", "@version Mar. 24, 2026")
      When("the source-history rule interprets each complete source")
      val results = bodies.map(body => _apply_text(header + body))
      Then("each result updates only the genuine header and retains every consecutive backslash")
      results.zip(bodies).foreach { (result, body) =>
        result.text shouldBe _header("@since Jan.  6, 2026", "version Mar. 24, 2026", "@version Apr.  9, 2026") + body
      }
      When("the same date is reapplied to every result")
      val repeated = results.map(result => _apply_text(result.text))
      Then("every complete source remains byte identical")
      repeated.zip(results).foreach { (second, first) =>
        second.text shouldBe first.text
        second.outcome shouldBe "unchanged"
      }
    }

    "leave interpolation looking text in ordinary noninterpolated literals unchanged" in {
      Given("ordinary quoted and triple strings with literal interpolation openers and apparent managed tags")
      val body = "val quoted = \"${ /* @version invalid */\"\n" +
        "val triple = \"\"\"${\n" + _header("@since Jan.  6, 2026", "@version Mar. 24, 2026") + "\"\"\"\n"
      val original = _header("@since Jan.  6, 2026", "@version Mar. 24, 2026") + body
      When("the rule interprets strings without a directly preceding interpolator")
      val result = _apply_text(original)
      Then("the ordinary literal contents stay exact while the genuine header changes")
      result.text shouldBe _header("@since Jan.  6, 2026", "version Mar. 24, 2026", "@version Apr.  9, 2026") + body
      When("the same date is reapplied")
      val repeated = _apply_text(result.text)
      Then("the source remains byte identical")
      repeated.text shouldBe result.text
      repeated.outcome shouldBe "unchanged"
    }

    "reject unclosed interpolation expressions and their nested literals" in {
      Given("a genuine header before an unfinished interpolation or nested quoted or triple literal")
      val header = _header("@since Jan.  6, 2026", "@version Mar. 24, 2026")
      val bodies = Vector("val text = s\"${{ 1 }", "val text = s\"${\"unfinished",
        "val text = s\"\"\"${\"\"\"unfinished", "val text = s\"${\"/*\"}")
      When("the rule reaches the incomplete lexical boundary")
      val results = bodies.map(body => applyC(_rule, _input(header + body)))
      Then("each source returns a typed failure without a partial rewritten result")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "retain nested comment narrative and recognize Scala 3 quotes as code" in {
      Given("Scala 3 quote syntax before a managed block containing a nested narrative comment")
      val quoted = "val quoted = '{ 1 + 2 }\nval quotedType = '[List[Int]]\n"
      val original = quoted + _header("Origin /* nested /* deeper */ narrative */ retained.",
        "@since Jan.  6, 2026", "@version Mar. 24, 2026") + _code
      When("the whole source is scanned with nested block delimiters")
      val result = _apply_text(original)
      Then("quote syntax cannot swallow the header and nested narrative remains exact")
      result.text shouldBe quoted + _header("Origin /* nested /* deeper */ narrative */ retained.",
        "@since Jan.  6, 2026", "version Mar. 24, 2026", "@version Apr.  9, 2026") + _code
    }

    "locate a nested managed block and reject competing outer managed dates" in {
      Given("a nested managed comment, with or without competing outer date fields")
      val nested = _header("@since Jan.  6, 2026", "@version Mar. 24, 2026")
      val original = "/* Outer origin retained.\n" + nested + "End outer narrative. */\n" + _code
      val competing = "/*\n * @since Jan.  6, 2026\n" + nested + " */\n" + _code
      When("the scanner respects each actual nested block boundary")
      val result = _apply_text(original)
      val ambiguous = applyC(_rule, _input(competing))
      Then("the single nested managed comment changes in place and competing blocks fail")
      result.text shouldBe "/* Outer origin retained.\n" + _header("@since Jan.  6, 2026",
        "version Mar. 24, 2026", "@version Apr.  9, 2026") + "End outer narrative. */\n" + _code
      ambiguous shouldBe a[Consequence.Failure[?]]
    }

    "create an initial comment using reliable creation and supplied author facts" in {
      Given("source without managed history and reliable creation and author information")
      val original = _input(_code).copy(author = Some("Known Author"))
      When("the external rule adds its declared header")
      val result = _take(applyC(_rule, original))
      Then("creation and author facts are used and every original source character follows unchanged")
      result.text shouldBe _header("@since Jan.  6, 2026", "@version Apr.  9, 2026", "@author Known Author") + _code
      result.dateUncertain shouldBe false
    }

    "mark unknown creation explicitly without inventing authorship" in {
      Given("source without managed history or reliable creation or author facts")
      val original = _input(_code).copy(createdOn = None)
      When("the supplied update day is used for the initial since field")
      val result = _take(applyC(_rule, original))
      Then("the fallback day is explicitly uncertain and author metadata is absent")
      result.text shouldBe _header("@since Apr.  9, 2026", "@version Apr.  9, 2026") + _code
      result.dateUncertain shouldBe true
      _take(applyC(_rule, _input(result.text).copy(dateUncertain = true))).dateUncertain shouldBe true
    }

    "retain CRLF in existing and newly inserted history comments" in {
      Given("source and managed history using CRLF newlines")
      val original = (_header("@since Jan.  6, 2026", "@version Mar. 24, 2026") + _code).replace("\n", "\r\n")
      val bare = _code.replace("\n", "\r\n")
      When("existing history is updated and missing history is added")
      val updated = _apply_text(original)
      val inserted = _apply_text(bare)
      Then("both outputs use the source newline and preserve source bytes")
      updated.text shouldBe (_header("@since Jan.  6, 2026", "version Mar. 24, 2026", "@version Apr.  9, 2026") + _code).replace("\n", "\r\n")
      inserted.text shouldBe (_header("@since Jan.  6, 2026", "@version Apr.  9, 2026") + _code).replace("\n", "\r\n")
    }

    "insert a missing latest field into an existing since-only header" in {
      Given("an existing since and known author without a latest date")
      val original = _header("Narrative retained.", "@since Jan.  6, 2026", "@author Known Author") + _code
      When("the rule supplies the latest date")
      val result = _apply_text(original)
      Then("the existing origin author narrative and delimiters remain in place")
      result.text shouldBe _header("Narrative retained.", "@since Jan.  6, 2026", "@version Apr.  9, 2026",
        "@author Known Author") + _code
    }

    "complete missing origin from reliable creation without changing narrative or authorship" in {
      Given("an existing latest-only header and reliable creation evidence")
      val original = _header("Origin narrative.", "@version Mar. 24, 2026", "@author Existing Author") + _code
      When("the rule completes the managed date block")
      val result = _apply_text(original)
      Then("since precedes history and latest while narrative and known author remain exact")
      result.text shouldBe _header("Origin narrative.", "@since Jan.  6, 2026", "version Mar. 24, 2026",
        "@version Apr.  9, 2026", "@author Existing Author") + _code
      result.dateUncertain shouldBe false
    }

    "complete missing origin with an explicitly uncertain update-day fallback" in {
      Given("an existing managed block without since or reliable creation evidence")
      val original = _input(_header("Origin narrative.", "@version Mar. 24, 2026") + _code).copy(createdOn = None)
      When("the rule supplies the absent origin from updatedOn")
      val result = _take(applyC(_rule, original))
      Then("the fallback is explicit and no author is invented")
      result.text shouldBe _header("Origin narrative.", "@since Apr.  9, 2026", "version Mar. 24, 2026",
        "@version Apr.  9, 2026") + _code
      result.dateUncertain shouldBe true
      val repeated = _take(applyC(_rule, original.copy(text = result.text, dateUncertain = result.dateUncertain)))
      repeated.text shouldBe result.text
      repeated.outcome shouldBe "unchanged"
      repeated.dateUncertain shouldBe true
    }

    "place an existing since before history while retaining its exact spelling" in {
      Given("a managed block with since after historical dates and interleaved narrative")
      val original = _header("version Feb. 19, 2026", "Narrative retained.", "@version Mar. 24, 2026",
        "@since   Jan.  6, 2026", "@author Existing Author") + _code
      When("the managed date fields are normalized")
      val result = _apply_text(original)
      Then("since keeps its exact text and precedes the preserved history order and latest")
      result.text shouldBe _header("@since   Jan.  6, 2026", "version Feb. 19, 2026", "version Mar. 24, 2026",
        "@version Apr.  9, 2026", "Narrative retained.", "@author Existing Author") + _code
    }

    "reject malformed dates duplicate managed fields and multiple managed blocks" in {
      Given("invalid dates, duplicate since or latest fields, and ambiguous managed comments")
      val originals = Vector(_header("@version invalid"), _header("@version Feb. 30, 2026"),
        _header("@since Jan.  6, 2026", "version invalid", "@version Mar. 24, 2026"),
        _header("@since Jan.  6, 2026", "@since Jan.  6, 2026"),
        _header("@version Mar. 24, 2026", "@version Apr.  3, 2026"),
        _header("@since Jan.  6, 2026") + _header("@version Mar. 24, 2026"))
      When("the pure interpreter receives the original immutable texts")
      val results = originals.map(text => applyC(_rule, _input(text + _code)))
      Then("all fail without producing a partially changed output")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "reject unterminated lexical forms and invalid provenance or copied rules" in {
      Given("unterminated comments and literals or unsafe caller-supplied typed data")
      val broken = Vector("/* unfinished", "val x = \"unfinished", "val x = \"\"\"unfinished", "val `unfinished = 1")
      val invalidinputs = Vector(_input(_code).copy(path = "../Example.scala"), _input(_code).copy(updatedOn = null),
        _input(_code).copy(author = Some("Author */ injected")), _input(_code).copy(createdOn = Some(null)))
      When("the interpreter admits the input and scans its source")
      val failures = broken.map(text => applyC(_rule, _input(text))) ++ invalidinputs.map(applyC(_rule, _)) ++
        Vector(applyC(_rule.copy(tags = _rule.tags.copy(version = _rule.tags.since)), _input(_code)))
      Then("typed failures replace no source and cannot bypass decoding invariants")
      failures.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "remain byte-identical when reapplied on the same day" in {
      Given("existing history and absent history each updated once by the external rule")
      val originals = Vector(_header("Narrative.", "@since Jan.  6, 2026", "@version Mar. 24, 2026") + _code, _code)
      val once = originals.map(text => _apply_text(text))
      When("the same rule and supplied update date are applied again")
      val twice = once.map(result => _apply_text(result.text))
      Then("all subsequent outputs are unchanged and byte-identical")
      twice.zip(once).foreach { (second, first) =>
        second.outcome shouldBe "unchanged"
        second.text shouldBe first.text
        second.text.endsWith(_code) shouldBe true
      }
    }
  }
}
