package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import RepositorySyncNativeConflictInput as I
import RepositorySyncConflictSemantic as S
import SplitPhaseExplicitPartitionFixture.take

/**
 * @since   Oct.  6, 2026
 * @version Oct. 11, 2026
 */
final class RepositorySyncNativeConflictInputSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Native conflict input and closed proposal contract" should {
    "retain tab and newline path bytes while refusing malformed or duplicate index stages" in {
      Given("actual ls-files unmerged NUL framing with a newline and tab in the relative path")
      val oid = "a" * 40; val path = "dir/name\nwith\ttab"
      val raw = s"100644 $oid 1\t$path\u0000"
      When("the fixed index response is decoded")
      val decoded = take(I.parseC(raw))
      Then("path bytes and native IDs survive and duplicate, partial or unsafe records are refused explicitly")
      decoded shouldBe Vector(I.Entry("100644", oid, 1, path))
      I.parseC(raw + raw).isSuccess shouldBe false
      I.parseC(raw.dropRight(1)).isSuccess shouldBe false
      I.parseC(s"100644 $oid 0\tx\u0000").isSuccess shouldBe false
      I.parseC(s"100644 $oid 1\t../foreign\u0000").isSuccess shouldBe false
    }
    "admit only complete assigned conflict-ID resolutions or an explicit non-consenting DecisionNeeded" in {
      Given("a selected native request and finite host path policy")
      val blob = I.Blob("b" * 40, "source\n")
      val request = I.Request("instance", "8", "c" * 40, "d" * 40,
        Vector(I.Conflict("conflict-1", "shared.txt", blob, blob, blob)))
      val policy = I.Policy(Set("shared.txt"), maximumBlobBytes = 32)
      val result = S.Result("resolved", Vector(S.Resolution("conflict-1", "resolved\n")), "assigned resolution")
      When("the semantic outcome is deterministically admitted against the actual assigned input")
      val admitted = S.admitC(request, result, policy)
      Then("ordered exact coverage and finite bytes are required; DecisionNeeded cannot also choose a solution")
      admitted.isSuccess shouldBe true
      S.admitC(request, result.copy(resolutions = Vector.empty), policy).isSuccess shouldBe false
      S.admitC(request, result.copy(resolutions = Vector(S.Resolution("other", "x"))), policy).isSuccess shouldBe false
      S.admitC(request, result.copy(resolutions = Vector(S.Resolution("conflict-1", "x" * 33))), policy).isSuccess shouldBe false
      S.admitC(request, result.copy(decision = "decision-needed", resolutions = Vector.empty), policy).isSuccess shouldBe true
      S.admitC(request, result.copy(decision = "decision-needed"), policy).isSuccess shouldBe false
      S.admitC(request, result, I.noSemanticPaths).isSuccess shouldBe false
    }
    "consume actual semantic results without granting unused process annotations authority" in {
      Given("a valid typed semantic result with no command or transition authority")
      val result = S.Result("decision-needed", Vector.empty, "Human direction required")
      val wire = S.resultCodec.encode(result)
      When("unused next-state and executable diagnostics accompany the semantic result")
      val next = wire.mapObject(_.add("payload", wire.hcursor.downField("payload").focus.get
        .mapObject(_.add("nextState", Json.fromString("Converged")))))
      val argv = wire.mapObject(_.add("payload", wire.hcursor.downField("payload").focus.get
        .mapObject(_.add("argv", Json.arr(Json.fromString("push"))))))
      Then("only the same business result is decoded and no command or transition is selected")
      S.resultCodec.decode(wire) shouldBe Right(result)
      S.resultCodec.decode(next) shouldBe Right(result)
      S.resultCodec.decode(argv) shouldBe Right(result)
    }
  }
}
