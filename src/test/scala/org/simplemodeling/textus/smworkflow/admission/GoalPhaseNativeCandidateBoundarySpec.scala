package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeCandidateBoundary as B
import RepositorySyncManagedGitObservation as G

/** Pure retained proof, compatibility and ownership evidence; no Git effect authority.
  * @since   Oct. 21, 2026
  * @version Oct. 24, 2026
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
