package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import RepositorySyncPurposeContentContinuation as N
import RepositorySyncPurpose as P

private[admission] object RepositorySyncPurposeContentContinuationFixture {
  val original = P.Request("repository", "/specification/repository", "main", "a" * 40, "b" * 40,
    (1 to 36).toVector.map(n => P.File(f"docs/file-$n%02d.md", " ", "M", Some("base\n"), Some("base\n"), Some("old\n"), None)),
    Vector(P.SourceRecord(P.Source("Task", "actual-task", "1"), "observed", Json.obj(
      "purpose" -> Json.fromString("retain intent"), "optionalSourceFact" -> Json.Null))))
  val current = original.copy(files = original.files.zipWithIndex.map((file, n) =>
    if (n < 3) file.copy(worktree = Some("current ordinary content\n")) else file))
  val target = N.Target("component", "session", RepositorySyncValidationContinuationFixture.context,
    original.root, "/specification/upstream.git", "8", "native-purpose-issued:session", "purpose-inventory", "7",
    "session:native-purpose:7", "8", "1", original, current, N.policy)
  val reference = ContextReference("actual-human-direction", "1")
}

/** Pure content and exact evidence contracts; native lifecycle is exercised by
  * NativeRepositoryPurposeContentContinuationCliSpec.
  * @since Nov. 3, 2026
  * @version Nov. 3, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class RepositorySyncPurposeContentContinuationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import RepositorySyncPurposeContentContinuationFixture.*
  "RS-PURPOSE-03 immutable content refresh" should {
    "retain all 36 paths and bind exactly the three changed complete contents" in {
      Given("the actual original inventory and three changed ordinary worktree texts")
      When("the strict target and independent binding are encoded")
      val binding = N.bindingC("component", target, N.choices.head, "The prior semantic worker ended; retain its unknown effects",
        "participant", reference, true, true).toOption.get
      Then("the complete old and current inventories remain distinct with identical metadata")
      N.contentOnly(original, current) shouldBe true
      original.files.zip(current.files).count((before, after) => before.worktree != after.worktree) shouldBe 3
      N.decodeTarget(target.asJson) shouldBe Right(target)
      RepositorySyncValidationContinuation.decodeAnyTarget(target.asJson) shouldBe
        Right(RepositorySyncValidationContinuation.PurposeContentTarget(target))
      RepositorySyncValidationContinuation.selectBinding(binding) shouldBe Right(binding)
      original.files.forall(_.worktree.contains("old\n")) shouldBe true
    }
    "refuse added missing changed-status indexed provenance and unexamined content" in {
      Given("an exact original inventory whose path authority must remain fixed")
      val changes = Vector(current.copy(files = current.files.tail),
        current.copy(files = current.files :+ current.files.head.copy(path = "extra.md")),
        current.copy(files = current.files.updated(0, current.files.head.copy(indexCode = "M"))),
        current.copy(files = current.files.updated(0, current.files.head.copy(indexed = Some("new index")))),
        current.copy(files = current.files.updated(0, current.files.head.copy(unexamined = Some("binary")))),
        current.copy(records = Vector.empty), current.copy(parent = "c" * 40), original)
      When("a proposed refresh crosses a meaning-bearing invariant")
      val accepted = changes.map(N.contentOnly(original, _))
      Then("none can silently establish a new purpose, file selection or ordinary-byte claim")
      accepted shouldBe Vector.fill(changes.size)(false)
    }
    "require an exact answer and independent ended-worker uncertainty acceptance" in {
      Given("an unanswered available WorkOrder which might have produced external effects")
      val answer = RepositorySyncValidationContinuation.Answer(N.choices.head, "Host observed the old worker end", true, true)
      When("an assertion, participant, reference, or target field is absent or crossed")
      val wire = target.asJson
      val malformed = wire.asObject.get.keys.toVector.flatMap(key => Vector(wire.mapObject(_.remove(key)),
        wire.mapObject(_.add(key, Json.Null)))) :+ wire.mapObject(_.add("silentConsent", Json.True))
      Then("strict decoding and evidence binding refuse while passive Defer remains available")
      malformed.foreach(value => N.decodeTarget(value).isLeft shouldBe true)
      N.decodeAnswer(answer.asJson) shouldBe Right(answer)
      N.decodeAnswer(answer.copy(priorExecutionEnded = false).asJson).isLeft shouldBe true
      N.decodeAnswer(answer.asJson.mapObject(_.add("noExternalEffects", Json.True))).isLeft shouldBe true
      N.bindingC("component", target, answer.choice, answer.rationale, "participant", reference, false, true).isSuccess shouldBe false
      N.bindingC("component", target, answer.choice, answer.rationale, "foreign", reference, true, true).isSuccess shouldBe false
      N.decodeAnswer(answer.copy(choice = "Defer", priorExecutionEnded = false, acceptOriginalUncertainty = false).asJson).isRight shouldBe true
    }
  }
}
