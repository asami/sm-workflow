package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeCandidateBoundary as B
import RepositorySyncManagedGitObservation as G

/** Pure retained proof, compatibility and ownership evidence; no Git effect authority.
  * @since   Oct. 21, 2026
  * @version Oct. 28, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseNativeCandidateBoundarySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _oid(n: Int): String = f"$n%040x"
  private val _anchor = O.Snapshot(_oid(1), _oid(2))
  private val _parent = O.Snapshot(_oid(3), _oid(4))
  private val _proof = O.CandidateContinuation(_parent, Vector(
    O.HistoryCommit(_parent.head, _parent.tree, Vector(_oid(5), _anchor.head)),
    O.HistoryCommit(_anchor.head, _anchor.tree, Vector.empty)))
  private val _owners = Vector("src", "docs/phase.md")
  private def _dirty(index: Char, worktree: Char, path: String = "notes/user.md", from: Option[String] = None) =
    G.DirtyPath(index, worktree, path, from)
  private def _historical_correction(path: String = "src/model.cml", blob: String = _oid(11), ordinal: Int = 1): Json =
    Json.obj("path" -> Json.fromString(path), "mode" -> Json.fromString("100644"),
      "originalBlob" -> Json.fromString(blob), "stagingOrdinal" -> Json.fromInt(ordinal))
  private def _historical_hygiene(outcome: String = "clean", mode: String = "auto-fix",
    corrections: Vector[Json] = Vector.empty, originalindex: String = ""): Json =
    Json.obj("mode" -> Json.fromString(mode), "outcome" -> Json.fromString(outcome),
      "findings" -> Json.arr(), "corrections" -> Json.fromValues(corrections),
      "diagnostic" -> Json.fromString(""), "originalIndex" -> Json.fromString(originalindex))
  private def _historical_wire(hygiene: Json): Json = {
    val original = GoalPhaseNativeCandidate.codec.encode(GoalPhaseNativeCandidate.Payload("1", "S1",
      Vector("src"), Vector("src"), Some(_anchor), Vector("src/model.cml"), Some("selected diff")))
    original.mapObject(fields => fields.add("payload", fields("payload").get.mapObject(_.add("hygiene", hygiene))))
  }

  "Retained descendant proof" should {
    "admit either actual merge parent and preserve the exact original anchor" in {
      Given("a merge whose second parent is the accepted Step tip and an equivalent first-parent path")
      val first = _proof.copy(lineage = _proof.lineage.updated(0, _proof.lineage.head.copy(parents = Vector(_anchor.head, _oid(5)))))
      When("pure admission checks both complete proofs")
      val results = Vector(_proof, first).map(O.continuationValid(_anchor, _))
      Then("each proves the same actual descendant parent without rewriting the anchor")
      results shouldBe Vector(true, true)
      B.parentC(_anchor, Some(_proof)).toOption shouldBe Some(_parent)
      B.parentC(_anchor, None).toOption shouldBe Some(_anchor)
    }

    "reject unrelated incomplete cyclic duplicate malformed and oversized proof facts" in {
      Given("a valid retained proof and variants that violate one exact ancestry fact")
      val head = _proof.lineage.head
      val anchor = _proof.lineage.last
      val long = (1 to 257).map(n => O.HistoryCommit(_oid(n), _oid(n + 1000), Vector(_oid(n + 1)))).toVector
      val invalid = Vector(
        _proof.copy(parent = _anchor),
        _proof.copy(parent = _parent.copy(tree = _oid(99))),
        _proof.copy(lineage = Vector(head)),
        _proof.copy(lineage = Vector(head.copy(parents = Vector(_oid(5))), anchor)),
        _proof.copy(lineage = Vector(head, anchor.copy(tree = _oid(99)))),
        _proof.copy(lineage = Vector(head, anchor.copy(parents = Vector(head.identity)))),
        _proof.copy(lineage = Vector(head, head, anchor)),
        _proof.copy(lineage = Vector(head.copy(identity = "not-an-oid"), anchor)),
        _proof.copy(lineage = Vector(head.copy(parents = Vector(_anchor.head, _anchor.head)), anchor)),
        _proof.copy(lineage = Vector(head.copy(parents = Vector("bad-parent")), anchor)),
        _proof.copy(lineage = long), _proof.copy(lineage = Vector.empty))
      When("pure admission checks the altered retained facts")
      val results = invalid.map(O.continuationValid(_anchor, _))
      Then("none changes the immutable anchor or supplies a candidate parent")
      results shouldBe Vector.fill(invalid.size)(false)
      invalid.foreach(value => B.parentC(_anchor, Some(value)).isSuccess shouldBe false)
    }
  }

  "Candidate v2 compatibility" should {
    "retain absent extensions as strict old-tip semantics while decoding explicit valid proofs" in {
      Given("an old v2 candidate wire encoded without either optional extension")
      val payload = GoalPhaseNativeCandidate.Payload("1", "S1", Vector("src"), Vector("src"),
        Some(O.Snapshot(_anchor.head, _oid(9))), Vector("src/one.scala"), Some("selected diff"))
      val legacy = GoalPhaseNativeCandidate.codec.encode(payload)
      val integrated = payload.copy(candidate = Some(O.Snapshot(_parent.head, _oid(9))), continuation = Some(_proof))
      When("the same v2 codec reads legacy and newly extended receipts")
      val old = GoalPhaseNativeCandidate.codec.decode(legacy).toOption.get
      val fresh = GoalPhaseNativeCandidate.codec.decode(GoalPhaseNativeCandidate.codec.encode(integrated)).toOption.get
      Then("absence remains absence and no continuation is inferred during retained replay")
      legacy.hcursor.downField("payload").downField("continuation").focus shouldBe None
      legacy.hcursor.downField("payload").downField("stepEvidence").focus shouldBe None
      old shouldBe payload
      old.continuation shouldBe None
      fresh shouldBe integrated
      B.parentC(_anchor, old.continuation).toOption shouldBe Some(_anchor)
      Given("a current inspection controller and an old receipt whose HEAD differs from its accepted anchor")
      val plan = O.Plan("compatibility", "1", "P1", "repository", Vector(
        O.Step("S1", "Selected source change", Vector("src"), Vector.empty, Vector("test"))), Vector("test"))
      val initial = O.startC(plan, _anchor).toOption.get
      val ready = initial.acceptC(O.SemanticCompleted(initial.next.asInstanceOf[O.Semantic].key, "Ready")).toOption.get
      When("the adapter admits the legacy and explicitly proven candidate observations")
      val legacyobservation = GoalPhaseNativeCandidate.observedStateC(ready, old.copy(candidate = fresh.candidate), None)
      val provenobservation = GoalPhaseNativeCandidate.observedStateC(ready, fresh, None)
      Then("only the explicit proof can change the candidate parent during replay")
      legacyobservation.isSuccess shouldBe false
      provenobservation.isSuccess shouldBe true
    }

    "reject malformed explicit extensions before retained admission" in {
      Given("a v2 candidate with an explicit continuation or Step evidence extension")
      val original = GoalPhaseNativeCandidate.codec.encode(GoalPhaseNativeCandidate.Payload("1", "S1",
        Vector.empty, Vector.empty, None, Vector.empty, None))
      def _wire_(name: String, value: Json) = original.mapObject(fields => fields.add("payload",
        fields("payload").get.mapObject(_.add(name, value))))
      val malformed = Vector(_wire_("continuation", Json.fromString("descendant")),
        _wire_("continuation", Json.obj("parent" -> Json.obj(), "lineage" -> Json.arr())),
        _wire_("continuation", Json.Null), _wire_("stepEvidence", Json.Null),
        _wire_("stepEvidence", Json.arr()), _wire_("stepEvidence", Json.fromString("all Steps")))
      When("the compatibility codec decodes the malformed new values")
      val results = malformed.map(GoalPhaseNativeCandidate.codec.decode)
      Then("every malformed extension is rejected instead of falling back to legacy inference")
      results.foreach(_.isLeft shouldBe true)
    }
  }

  "Historical terminal candidate hygiene compatibility" should {
    "retain active hygiene preparation without treating progress as a completed candidate" in {
      Given("a bounded pending correction and the diagnostic results used by the active hygiene runtime")
      val plan = CommitHygiene.Record("auto-fix", "planned", Vector("eof-blank-lines"),
        Vector(CommitHygiene.Correction("src/model.cml", "100644", _oid(11), 1)), "pending", _oid(12))
      val records = Vector(plan, plan.copy(outcome = "remaining-findings"),
        plan.copy(outcome = "diagnostic", corrections = Vector.empty, originalIndex = ""))
      When("the merged codec decodes active records alongside historical terminal receipts")
      val payload = GoalPhaseNativeCandidate.Payload("1", "S1", Vector.empty, Vector.empty, None, Vector.empty, None)
      val decoded = records.map(record => GoalPhaseNativeCandidate.codec.decode(
        GoalPhaseNativeCandidate.codec.encode(payload.copy(hygiene = Some(record)))))
      Then("progress remains typed and round trips without inventing a candidate or losing the bounded plan")
      decoded.map(_.toOption.get.hygiene) shouldBe records.map(Some(_))
      decoded.foreach(_.toOption.get.candidate shouldBe None)
    }

    "round trip clean receipts in either historical mode without changing their raw evidence" in {
      Given("completed clean metadata with empty corrections and supported optional original index facts")
      val receipts = Vector(
        _historical_hygiene(), _historical_hygiene(mode = "error", originalindex = _oid(12)),
        _historical_hygiene(originalindex = "a" * 64).mapObject(_.add("diagnostic", Json.fromString("historical clean check"))))
      val wires = receipts.map(_historical_wire)
      When("the actual candidate codec decodes and re-encodes each historical receipt")
      val decoded = wires.map(GoalPhaseNativeCandidate.codec.decode(_).toOption.get)
      val encoded = decoded.map(GoalPhaseNativeCandidate.codec.encode)
      Then("the complete wire and exact historical JSON remain unchanged, including through payload copies")
      encoded shouldBe wires
      decoded.map(_.hygiene.map(_.asJson)) shouldBe receipts.map(Some(_))
      decoded.map(_.copy(sourceRevision = "2").hygiene.map(_.asJson)) shouldBe receipts.map(Some(_))
    }

    "round trip corrected receipts with both Git object lengths and the finite correction limit" in {
      Given("terminal auto-fix metadata with distinct production CML paths and supported historical staging facts")
      val executable = _historical_correction("src/executable.cml", "b" * 64, 256)
        .mapObject(_.add("mode", Json.fromString("100755")))
      val maximum = (1 to 256).map(n => _historical_correction(s"src/model$n.cml", _oid(n), n)).toVector
      val receipts = Vector(
        _historical_hygiene("corrected", corrections = Vector(_historical_correction(), executable), originalindex = _oid(12)),
        _historical_hygiene("corrected", corrections = maximum, originalindex = "c" * 64))
      val wires = receipts.map(_historical_wire)
      When("the codec reads and writes completed historical correction evidence")
      val decoded = wires.map(GoalPhaseNativeCandidate.codec.decode(_).toOption.get)
      Then("raw paths, modes, object IDs and ordinals survive without granting or executing an operation")
      decoded.map(_.hygiene.map(_.asJson)) shouldBe receipts.map(Some(_))
      decoded.map(GoalPhaseNativeCandidate.codec.encode) shouldBe wires
    }

    "preserve current absence and refuse unrelated unknown candidate fields" in {
      Given("a current candidate created without historical metadata and an unrelated unknown payload extension")
      val payload = GoalPhaseNativeCandidate.Payload("1", "S1", Vector.empty, Vector.empty, None, Vector.empty, None)
      val wire = GoalPhaseNativeCandidate.codec.encode(payload)
      val unknown = wire.mapObject(fields => fields.add("payload",
        fields("payload").get.mapObject(_.add("unknownHistoricalField", Json.obj()))))
      When("the codec decodes both complete candidate wires")
      val current = GoalPhaseNativeCandidate.codec.decode(wire)
      val rejected = GoalPhaseNativeCandidate.codec.decode(unknown)
      Then("absence remains omitted and strict unknown-field admission remains intact")
      wire.hcursor.downField("payload").downField("hygiene").focus shouldBe None
      current shouldBe Right(payload)
      current.toOption.get.hygiene shouldBe None
      GoalPhaseNativeCandidate.codec.encode(current.toOption.get) shouldBe wire
      rejected.isLeft shouldBe true
    }

    "reject null missing mistyped and extra historical fields instead of ignoring malformed evidence" in {
      Given("the exact historical object and variants missing or corrupting its required typed fields")
      val clean = _historical_hygiene()
      val keys = Vector("mode", "outcome", "findings", "corrections", "diagnostic", "originalIndex")
      val malformed = Vector(Json.Null, Json.arr(), Json.fromString("clean"),
        clean.mapObject(_.add("unknown", Json.fromBoolean(true)))) ++
        keys.map(key => clean.mapObject(_.remove(key))) ++
        keys.map(key => clean.mapObject(_.add(key, Json.Null))) ++
        keys.map(key => clean.mapObject(_.add(key, Json.fromInt(1)))) ++ Vector(
          clean.mapObject(_.add("findings", Json.arr(Json.fromInt(1)))),
          clean.mapObject(_.add("corrections", Json.arr(Json.fromString("src/model.cml")))))
      When("the actual candidate codec reads each malformed historical extension")
      val results = malformed.map(value => GoalPhaseNativeCandidate.codec.decode(_historical_wire(value)))
      Then("every malformed record fails closed without a legacy fallback")
      results.foreach(_.isLeft shouldBe true)
    }

    "reject pending diagnostic unknown and contradictory terminal outcomes without replay" in {
      Given("pending outcomes and terminal records that contradict the completed clean or corrected contract")
      val correction = Vector(_historical_correction())
      val invalid = Vector("planned", "diagnostic", "remaining-findings", "unknown").map(outcome =>
        _historical_hygiene(outcome, corrections = correction, originalindex = _oid(12))) ++ Vector(
        _historical_hygiene(mode = "unknown"),
        _historical_hygiene(corrections = correction),
        _historical_hygiene("corrected", originalindex = _oid(12)),
        _historical_hygiene("corrected", mode = "error", corrections = correction, originalindex = _oid(12)),
        _historical_hygiene("corrected", corrections = correction),
        _historical_hygiene(originalindex = "not-an-object-id"),
        _historical_hygiene().mapObject(_.add("findings", Json.arr(Json.fromString("remaining finding")))),
        _historical_hygiene("corrected", corrections = correction, originalindex = _oid(12))
          .mapObject(_.add("findings", Json.arr(Json.fromString("remaining finding")))))
      When("the codec checks whether each historical record is a completed terminal receipt")
      val results = invalid.map(value => GoalPhaseNativeCandidate.codec.decode(_historical_wire(value)))
      Then("none is converted to a completed result or accepted as permission to replay old work")
      results.foreach(_.isLeft shouldBe true)
    }

    "reject malformed duplicate oversized and out-of-bound historical correction facts" in {
      Given("one valid historical correction and mutations violating its closed finite metadata contract")
      val correction = _historical_correction()
      val keys = Vector("path", "mode", "originalBlob", "stagingOrdinal")
      val badpaths = Vector("", "/src/model.cml", "../model.cml", "src/../model.cml", "src/model.scala") ++
        Vector("test", "tests", "fixtures", "expected", "golden", "snapshots").flatMap(segment =>
          Vector(s"src/$segment/model.cml", s"src/${segment.toUpperCase(java.util.Locale.ROOT)}/model.cml"))
      val badids = Vector("a" * 39, "a" * 41, "a" * 63, "a" * 65, "g" * 40)
      val malformed = Vector(Json.Null, Json.arr(), Json.fromString("src/model.cml"),
        correction.mapObject(_.add("unknown", Json.fromBoolean(true)))) ++
        keys.map(key => correction.mapObject(_.remove(key))) ++
        keys.map(key => correction.mapObject(_.add(key, Json.Null))) ++
        badpaths.map(path => correction.mapObject(_.add("path", Json.fromString(path)))) ++
        badids.map(blob => correction.mapObject(_.add("originalBlob", Json.fromString(blob)))) ++
        Vector("100600", "120000", "160000").map(mode => correction.mapObject(_.add("mode", Json.fromString(mode)))) ++
        Vector(Json.fromInt(-1), Json.fromInt(0), Json.fromInt(257), Json.fromLong(2147483648L),
          Json.fromBigDecimal(BigDecimal("1.5")), Json.fromString("1")).map(ordinal =>
          correction.mapObject(_.add("stagingOrdinal", ordinal)))
      val invalid = malformed.map(value => _historical_hygiene("corrected", corrections = Vector(value), originalindex = _oid(12))) ++
        badids.map(index => _historical_hygiene("corrected", corrections = Vector(correction), originalindex = index)) ++ Vector(
          _historical_hygiene("corrected", corrections = Vector(correction, correction), originalindex = _oid(12)),
          _historical_hygiene("corrected", corrections = (1 to 257).map(n =>
            _historical_correction(s"src/model$n.cml", _oid(n), 1)).toVector, originalindex = _oid(12)))
      When("the codec validates the full historical correction list")
      val results = invalid.map(value => GoalPhaseNativeCandidate.codec.decode(_historical_wire(value)))
      Then("bad paths, modes, IDs, ordinals, duplicate paths and excess corrections all refuse")
      invalid.zip(results).foreach { case (value, result) =>
        withClue(s"Historical hygiene ${value.noSpaces}: $result") {
          result.isLeft shouldBe true
        }
      }
    }
  }

  "Scoped candidate ownership" should {
    "permit unrelated unstaged tracked untracked and ignored paths while requiring selected staging" in {
      Given("owned changes and unrelated modified untracked and ignored user documents")
      val foreign = Vector(_dirty(' ', 'M'), _dirty('?', '?', "notes/new.md"), _dirty('!', '!', "notes/cache"))
      val selected = Vector(_dirty(' ', 'M', "src/one.scala"))
      When("the boundary admits pre-stage and post-stage observations")
      val before = B.scopedDirty(selected ++ foreign, _owners, false)
      val after = B.scopedDirty(Vector(_dirty('M', ' ', "src/one.scala")) ++ foreign, _owners, true)
      Then("only the owned entries must become fully staged and all unrelated bytes can remain untouched")
      before shouldBe true
      after shouldBe true
      B.scopedDirty(selected ++ foreign, _owners, true) shouldBe false
      B.pathsOwned(Vector("src/one.scala"), _owners) shouldBe true
      B.pathsOwned(Vector("src/one.scala", "notes/user.md"), _owners) shouldBe false
      B.pathsOwned(Vector("src/one.scala", "src/one.scala"), _owners) shouldBe false
    }

    "reject foreign index changes cross-boundary renames unmerged and unsupported entries before staging" in {
      Given("every foreign staged change kind and selected or unrelated conflict and rename cases")
      val entries = "MADRCT".toVector.map(code => _dirty(code, ' ')) ++ Vector(
        _dirty('R', ' ', "notes/new.md", Some("notes/old.md")),
        _dirty('R', ' ', "src/new.scala", Some("notes/old.md")),
        _dirty('R', ' ', "notes/new.md", Some("src/old.scala")),
        _dirty('U', 'U'), _dirty('U', 'U', "src/one.scala"),
        _dirty('A', 'A'), _dirty('D', 'D'), _dirty('X', ' '), _dirty(' ', 'X'))
      When("pre-stage ownership inspects each complete dirty entry")
      val results = entries.map(entry => B.scopedDirty(Vector(entry), _owners, false))
      Then("none is silently filtered from the global index")
      results shouldBe Vector.fill(entries.size)(false)
      B.scopedDirty(Vector(_dirty('R', ' ', "src/new.scala", Some("src/old.scala"))), _owners, true) shouldBe true
    }
  }
}
