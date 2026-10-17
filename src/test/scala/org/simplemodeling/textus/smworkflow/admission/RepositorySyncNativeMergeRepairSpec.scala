package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.record.io.RecordDecoder
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as B
import RepositorySyncNativeMergeRepair as M
import RepositorySyncNativeMergeReview as R
import RepositorySyncNativeMergeValidation as V
import RepositorySyncNativeConflictInput as I

/** Specification-owned business fixtures exercise pure intake and meaning only;
  * they are not native issued work or independent reviewer/effect evidence.
  * @since   Oct. 15, 2026
  * @version Oct. 15, 2026
  */
final class RepositorySyncNativeMergeRepairSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _paths = Vector("docs/a.md", "src/b.scala")
  private val _policy = I.Policy(_paths.toSet, maximumConflicts = 2, maximumBlobBytes = 32)
  private val _review = R.Request("fixture-instance", "7", "a" * 40, "b" * 40, "c" * 40,
    _paths, "Local changed meaning", "Remote changed meaning", "required", "Affected source check",
    Vector("focused"), Vector(V.Receipt("focused", "exited", Some(0), "checked", "")))
  private val _findings = Vector(R.Finding("repair-a", Vector(_paths(1), _paths(0)), "Align the source and explanation"),
    R.Finding("repair-b", Vector(_paths(0)), "Clarify the explanation"))
  private val _files = Vector(M.File(_paths(0), "Original explanation"), M.File(_paths(1), "original source"))
  private val _request = M.Request(_review, _findings, _files)
  private val _result = M.Result("repaired", "Aligned the affected explanation", _files.updated(0, M.File(_paths(0), "Changed explanation")))

  private def _take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail("Business fixture intake failed: " + other)
  }
  private def _body(wire: Json): Json = wire.hcursor.downField("payload").focus.get
  private def _payload(wire: Json, body: Json): Json = wire.mapObject(_.add("payload", body))
  private def _field(body: Json, name: String)(change: Json => Json): Json =
    body.mapObject(obj => obj.add(name, change(obj(name).get)))
  private def _snake(value: Json): Json = value.arrayOrObject(value,
    values => Json.fromValues(values.map(_snake)),
    obj => Json.fromFields(obj.toVector.map { case (key, nested) =>
      key.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase -> _snake(nested)
    }))
  private def _annotate(value: Json): Json = value.arrayOrObject(value,
    values => Json.fromValues(values.map(_annotate)),
    obj => Json.fromFields(obj.toVector.map { case (key, nested) => key -> _annotate(nested) })
      .mapObject(_.add("inertDiagnostic", Json.True)))

  "E1 generated normal business intake" must afterWord(
    "in spec:repository-sync-native-merge-repair, example:E1, rules:R1,R2,R3,R5"
  ) {
    "retain nested review, findings, files and replacements from camel and snake JSON Records" in {
      Given("specification-owned affected text proposals and normal camel/snake business JSON, without an issued native action")
      val requestbody = _body(M.inputCodec.encode(_request))
      val resultbody = _body(M.resultCodec.encode(_result))
      val inputs = Vector(requestbody, _snake(requestbody))
      val outputs = Vector(resultbody, _snake(resultbody))

      When("RecordDecoder and the compiled generated createC readers consume each business Record")
      val requests = inputs.map(json => _take(B.RepositorySyncNativeMergeRepairRequest.createC(
        _take(new RecordDecoder().json(json.noSpaces)))))
      val results = outputs.map(json => _take(B.RepositorySyncNativeMergeRepairResult.createC(
        _take(new RecordDecoder().json(json.noSpaces)))))

      Then("all selected nested business values and order survive ordinary generated intake")
      requests.foreach { value =>
        value.review.instanceIdentity shouldBe _review.instanceIdentity
        value.review.validatedRevision shouldBe _review.validatedRevision
        value.review.localTip shouldBe _review.localTip
        value.review.remoteTip shouldBe _review.remoteTip
        value.review.indexTree shouldBe _review.indexTree
        value.review.changedPaths shouldBe _paths
        value.review.localDiff shouldBe _review.localDiff
        value.review.remoteDiff shouldBe _review.remoteDiff
        value.review.validationMode shouldBe _review.validationMode
        value.review.validationReason shouldBe _review.validationReason
        value.review.selectedChecks shouldBe Vector("focused")
        value.review.checks.map(c => (c.identity, c.termination, c.exitCode, c.stdout, c.stderr)) shouldBe
          Vector(("focused", "exited", 0, "checked", ""))
        value.findings.map(f => (f.identity, f.affectedPaths, f.rationale)) shouldBe
          _findings.map(f => (f.identity, f.affectedPaths, f.rationale))
        value.files.map(f => (f.path, f.text)) shouldBe _files.map(f => (f.path, f.text))
      }
      results.foreach { value =>
        value.decision shouldBe _result.decision
        value.rationale shouldBe _result.rationale
        value.replacements.map(f => (f.path, f.text)) shouldBe _result.replacements.map(f => (f.path, f.text))
      }
      requests.distinct.size shouldBe 1
      results.distinct.size shouldBe 1
    }
  }

  "E2 complete changed text proposals" must afterWord(
    "in spec:repository-sync-native-merge-repair, example:E2, rules:R1,R2,R3,R4,R5"
  ) {
    "admit actual complete in-policy replacements including an empty ordinary file" in {
      Given("meaningful findings whose sorted union covers two explicit reviewed text paths")
      val emptyinput = _request.copy(files = _files.updated(0, M.File(_paths(0), "")))
      val emptyreplacement = _result.copy(replacements = _files.updated(0, M.File(_paths(0), "")))

      When("pure request and repaired-result admission consume the text proposals")
      val requests = Vector(_request, emptyinput).map(M.admitRequestC(_, _policy))
      val results = Vector(M.admitC(_request, _result, _policy), M.admitC(emptyinput, _result, _policy),
        M.admitC(_request, emptyreplacement, _policy))

      Then("complete changed text is admitted and empty text is an ordinary file value")
      requests.foreach(_take(_) shouldBe ())
      results.foreach(_take(_) shouldBe ())
    }
    "consume only affected business facts without recertifying review state or history" in {
      Given("a supplied review whose affected changed paths are retained while unconsumed state diagnostics differ")
      val request = _request.copy(review = _review.copy(instanceIdentity = "different diagnostic", validatedRevision = "",
        localTip = "not an oid", indexTree = "", localDiff = "Different supplied presentation",
        validationMode = "different presentation", checks = Vector.empty))

      When("the primitive admits the same selected affected text meaning")
      val actual = M.admitC(request, _result, _policy)

      Then("business scope is admitted without treating this fixture as provenance authentication")
      _take(actual) shouldBe ()
    }
  }

  "E3 decision-needed meaning" must afterWord(
    "in spec:repository-sync-native-merge-repair, example:E3, rules:R2,R5"
  ) {
    "admit a meaningful decision-needed result with no replacements" in {
      Given("the assigned affected files and an unresolved semantic decision")
      val result = M.Result("decision-needed", "The intended public wording needs a decision", Vector.empty)

      When("pure result admission consumes that disposition")
      val actual = M.admitC(_request, result, _policy)

      Then("the disposition is admitted without supplying a file proposal")
      _take(actual) shouldBe ()
      result.replacements shouldBe empty
    }
    "refuse proposals with decision-needed and every unsupported decision" in {
      Given("a decision-needed proposal and decisions outside the two exact repair meanings")
      val candidates = Vector(_result.copy(decision = "decision-needed")) ++
        Vector("accept", "reject", "resolved", "repair", "", " ", "REPAIRED", null).map(decision => _result.copy(decision = decision))

      When("result admission consumes each disposition")
      val actual = candidates.map(M.admitC(_request, _, _policy))

      Then("none becomes repair admission or Human consent")
      actual.foreach(_ shouldBe a[Consequence.Failure[?]])
    }
  }

  "E4 request scope and exact coverage" must afterWord(
    "in spec:repository-sync-native-merge-repair, example:E4, rules:R1,R3,R4"
  ) {
    "refuse foreign, out-of-policy and traversal affected paths" in {
      Given("findings outside the reviewed changed paths, outside selected text policy or outside ordinary relative paths")
      val foreign = "docs/foreign.md"
      val traversal = "../outside.md"
      val candidates = Vector(
        _request.copy(findings = Vector(R.Finding("foreign", Vector(foreign), "Fix foreign text"))),
        _request.copy(review = _review.copy(changedPaths = _paths :+ traversal),
          findings = Vector(R.Finding("traversal", Vector(traversal), "Fix traversal text"))))

      When("request admission consumes those findings and a narrower policy")
      val actual = candidates.map(M.admitRequestC(_, _policy)) :+
        M.admitRequestC(_request, _policy.copy(semanticPaths = Set(_paths(0))))

      Then("every scope violation fails")
      actual.foreach(_ shouldBe a[Consequence.Failure[?]])
    }
    "refuse incomplete findings, invalid or duplicated identities and incomplete file coverage" in {
      Given("empty or malformed findings and omitted, repeated, reordered or foreign assigned files")
      val badfindings = Vector(Vector.empty[R.Finding], Vector(null),
        Vector(_findings.head.copy(identity = "bad identity")), Vector(_findings.head.copy(identity = "x" * 129)),
        Vector(_findings.head, _findings.head), Vector(_findings.head.copy(affectedPaths = Vector.empty)),
        Vector(_findings.head.copy(affectedPaths = Vector(_paths(0), _paths(0)))))
      val badfiles = Vector(Vector.empty[M.File], _files.take(1), _files.reverse, _files :+ _files.head,
        _files.updated(1, M.File("foreign.md", "Foreign")))

      When("request admission consumes all incomplete finding and file candidates")
      val actual = badfindings.map(f => M.admitRequestC(_request.copy(findings = f), _policy)) ++
        badfiles.map(f => M.admitRequestC(_request.copy(files = f), _policy)) :+
        M.admitRequestC(_request, _policy.copy(maximumConflicts = 1))

      Then("only exact bounded sorted affected-file coverage can be admitted")
      actual.foreach(_ shouldBe a[Consequence.Failure[?]])
    }
  }

  "E5 repaired coverage and changed meaning" must afterWord(
    "in spec:repository-sync-native-merge-repair, example:E5, rules:R2,R3"
  ) {
    "refuse partial, reordered, repeated or foreign replacements and all-no-op repair" in {
      Given("repaired results whose file assignments or actual text changes are incomplete")
      val replacements = _result.replacements
      val candidates = Vector(Vector.empty[M.File], replacements.take(1), replacements.reverse,
        replacements :+ replacements.head, replacements.updated(1, M.File("foreign.md", "Foreign")), _files)

      When("result admission compares replacement assignments and actual text meaning")
      val actual = candidates.map(files => M.admitC(_request, _result.copy(replacements = files), _policy))

      Then("each incomplete or all-no-op repaired result fails")
      actual.foreach(_ shouldBe a[Consequence.Failure[?]])
    }
    "admit one changed file while another assigned file remains unchanged" in {
      Given("complete ordered replacements with one actual text change")
      val result = _result

      When("result admission consumes the complete candidate")
      val actual = M.admitC(_request, result, _policy)

      Then("one meaningful text change satisfies repaired without requiring every file to change")
      _take(actual) shouldBe ()
      result.replacements(1) shouldBe _files(1)
    }
  }

  "E6 required values and UTF8 limits" must afterWord(
    "in spec:repository-sync-native-merge-repair, example:E6, rules:R1,R4"
  ) {
    "refuse missing consumed request, review, finding, file and policy values" in {
      Given("null consumed objects or collections and an absent or invalid selected policy")
      val candidates = Vector(null, _request.copy(review = null),
        _request.copy(review = _review.copy(changedPaths = null)), _request.copy(findings = null),
        _request.copy(files = null), _request.copy(findings = Vector(_findings.head.copy(affectedPaths = null))),
        _request.copy(files = Vector(null, _files(1))), _request.copy(files = _files.updated(0, M.File(null, "text"))))

      When("request admission consumes each missing business value")
      val actual = candidates.map(M.admitRequestC(_, _policy)) ++ Vector(
        M.admitRequestC(_request, null), M.admitRequestC(_request, _policy.copy(semanticPaths = null)),
        M.admitRequestC(_request, _policy.copy(maximumConflicts = 0)),
        M.admitRequestC(_request, _policy.copy(maximumBlobBytes = 0)))

      Then("every absent consumed value or invalid policy fails without throwing")
      actual.foreach(_ shouldBe a[Consequence.Failure[?]])
    }
    "refuse absent, blank, NUL or oversized finding and result rationale" in {
      Given("rationale that is null, blank, binary or beyond the 16384-byte UTF8 limit")
      val bad = Vector(null, "", " \n\t", "reason\u0000", "界" * 5462)

      When("finding and result admission consume each rationale")
      val actual = bad.flatMap(reason => Vector(
        M.admitRequestC(_request.copy(findings = Vector(_findings.head.copy(rationale = reason))), _policy),
        M.admitC(_request, _result.copy(rationale = reason), _policy)))

      Then("each invalid rationale fails and a multibyte rationale just below the byte limit succeeds")
      actual.foreach(_ shouldBe a[Consequence.Failure[?]])
      _take(M.admitC(_request, _result.copy(rationale = "界" * 5461), _policy)) shouldBe ()
      _take(M.admitRequestC(_request.copy(findings = Vector(_findings.head.copy(rationale = "界" * 5461))), _policy)) shouldBe ()
    }
    "measure input and replacement text in UTF8 bytes and refuse null or NUL text" in {
      Given("a six-byte explicit policy and two-character multibyte ordinary text")
      val policy = _policy.copy(maximumBlobBytes = 6)
      val request = _request.copy(files = _files.map(_.copy(text = "界界")))
      val result = _result.copy(replacements = _files.map(_.copy(text = "語語")))
      val bad = Vector(null, "\u0000", "界界界")

      When("request and result admission consume exact-limit and over-limit or binary texts")
      val accepted = M.admitC(request, result, policy)
      val refused = bad.flatMap(text => Vector(
        M.admitRequestC(request.copy(files = request.files.updated(0, request.files.head.copy(text = text))), policy),
        M.admitC(request, result.copy(replacements = result.replacements.updated(0,
          result.replacements.head.copy(text = text))), policy)))

      Then("six UTF8 bytes succeed while missing, NUL and nine-byte text fail")
      _take(accepted) shouldBe ()
      refused.foreach(_ shouldBe a[Consequence.Failure[?]])
    }
    "refuse absent results, replacement collections and replacement entries" in {
      Given("a missing result or missing consumed replacement data")
      val candidates = Vector(null, _result.copy(replacements = null), _result.copy(replacements = Vector(null, _files(1))))

      When("result admission consumes each missing candidate")
      val actual = candidates.map(M.admitC(_request, _, _policy))

      Then("missing result data cannot become repair admission")
      actual.foreach(_ shouldBe a[Consequence.Failure[?]])
    }
  }

  "E7 current typed semantic codecs" must afterWord(
    "in spec:repository-sync-native-merge-repair, example:E7, rules:R1,R2,R3,R4,R5"
  ) {
    "preserve consumed review, receipt, finding and file meaning with inert root and nested annotations" in {
      Given("current typed request/result wire values decorated with inert root and nested annotations")
      val input = _annotate(M.inputCodec.encode(_request))
      val result = _annotate(M.resultCodec.encode(_result))

      When("the current request and result codecs consume annotated wire values")
      val decodedinput = M.inputCodec.decode(input)
      val decodedresult = M.resultCodec.decode(result)

      Then("all actual typed semantic fields survive without annotations becoming authority")
      decodedinput shouldBe Right(_request)
      decodedresult shouldBe Right(_result)
    }
    "refuse missing or mistyped consumed request fields and unsupported request schema" in {
      Given("wire requests with missing or mistyped root and nested consumed business fields")
      val wire = M.inputCodec.encode(_request)
      val body = _body(wire)
      val missing = Vector("review", "findings", "files").map(name => body.mapObject(_.remove(name)))
      val wrong = Vector("review", "findings", "files").flatMap(name =>
        Vector(body.mapObject(_.add(name, Json.True)), body.mapObject(_.add(name, Json.Null))))
      val nested = Vector(
        _field(body, "review")(_.mapObject(_.remove("changedPaths"))),
        _field(body, "review")(review => _field(review, "checks")(_.mapArray(_.map(_.mapObject(_.add("identity", Json.True)))))),
        _field(body, "findings")(_.mapArray(_.map(_.mapObject(_.remove("rationale"))))),
        _field(body, "findings")(_.mapArray(_.map(_.mapObject(_.add("affectedPaths", Json.True))))),
        _field(body, "files")(_.mapArray(_.map(_.mapObject(_.remove("text"))))),
        _field(body, "files")(_.mapArray(_.map(_.mapObject(_.add("path", Json.True))))))
      val candidates = (missing ++ wrong ++ nested).map(_payload(wire, _)) ++ Vector(
        wire.mapObject(_.add("schemaVersion", Json.fromString("unsupported"))), null)

      When("the current typed request codec decodes those business candidates")
      val actual = candidates.map(M.inputCodec.decode)

      Then("missing data, wrong consumed types and unsupported schema fail")
      actual.foreach(_.isLeft shouldBe true)
    }
    "refuse missing or mistyped consumed result fields and unsupported result schema" in {
      Given("wire results with absent or mistyped decision, rationale or replacement meaning")
      val wire = M.resultCodec.encode(_result)
      val body = _body(wire)
      val missing = Vector("decision", "rationale", "replacements").map(name => body.mapObject(_.remove(name)))
      val wrong = Vector("decision", "rationale", "replacements").flatMap(name =>
        Vector(body.mapObject(_.add(name, Json.True)), body.mapObject(_.add(name, Json.Null))))
      val nested = Vector(
        _field(body, "replacements")(_.mapArray(_.map(_.mapObject(_.remove("path"))))),
        _field(body, "replacements")(_.mapArray(_.map(_.mapObject(_.add("text", Json.True))))))
      val candidates = (missing ++ wrong ++ nested).map(_payload(wire, _)) ++ Vector(
        wire.mapObject(_.add("schemaVersion", Json.fromString("unsupported"))), null)

      When("the current typed result codec decodes those business candidates")
      val actual = candidates.map(M.resultCodec.decode)

      Then("required business fields and the selected schema own refusal")
      actual.foreach(_.isLeft shouldBe true)
    }
  }
}
