package org.simplemodeling.textus.smworkflow.admission

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalacheck.{Gen, Prop, Test}
import SplitPhaseExplicitPartitionFixture.take
import DevelopmentOperationalView as V

/** @since Oct. 25, 2026
  * @version Oct. 25, 2026
  * @author ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class DevelopmentEventCorrelationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _subject = V.Subject(V.Car("org.example", "car", "1-SNAPSHOT"), "org.simplemodeling.textus.SmWorkflow",
    "actual-component-instance", "GoalPhase", "session", "repository", "phase")
  "Application development correlation" should {
    "depend on every exact ordered subject field" in {
      Given("one full subject and one-field alternatives")
      val alternatives = Vector(_subject.copy(car = _subject.car.copy(group = "other")),
        _subject.copy(car = _subject.car.copy(artifact = "other")), _subject.copy(car = _subject.car.copy(version = "other")),
        _subject.copy(producerComponentId = "other"), _subject.copy(producerInstanceId = "other"),
        _subject.copy(workflow = "other"), _subject.copy(sessionId = "other"),
        _subject.copy(repositoryIdentity = "other"), _subject.copy(phaseIdentity = "other"))
      When("the opaque lookup is derived")
      val original = take(DevelopmentEventCorrelation.keyC(_subject))
      Then("the key has the fixed namespace and every exact identity affects it")
      original should fullyMatch regex "[0-9a-f]{64}"
      alternatives.map(s => take(DevelopmentEventCorrelation.keyC(s))).foreach(_ should not be original)
      take(DevelopmentEventCorrelation.attributesC(_subject)) shouldBe Map(
        "history.namespace" -> "org.simplemodeling.textus.development.v1", "history.key" -> original)
    }
    "remain deterministic for arbitrary bounded valid opaque session identities" in {
      Given("a nonempty alphanumeric generator bounded to one hundred opaque session characters")
      val identities = Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.take(100).mkString)
      val property = Prop.forAll(identities) { session =>
        val value = _subject.copy(sessionId = session)
        val first = take(DevelopmentEventCorrelation.keyC(value))
        val repeated = take(DevelopmentEventCorrelation.keyC(value.copy()))
        first == repeated
      }
      When("each generated exact subject and its unchanged copy are independently correlated")
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
      Then("both computations retain the same deterministic key for at least one hundred successful trials")
      result.passed shouldBe true
    }
    "refuse malformed and oversized identities without replacement or normalization" in {
      Given("empty, padded, control, oversized and unpaired-surrogate session identities")
      val malformed = Vector("", " padded", "padded ", "\u2000padded", "padded\u00a0", "\u2000", "control\n", "x" * 1025, "\ud800")
      When("the exact-identity boundary attempts correlation without repairing the supplied strings")
      val rejected = malformed.map(value => DevelopmentEventCorrelation.keyC(_subject.copy(sessionId = value)))
      Then("every malformed identity is refused")
      rejected.foreach(_.isSuccess shouldBe false)
      When("two valid but differently encoded Unicode identities are correlated")
      val composed = take(DevelopmentEventCorrelation.keyC(_subject.copy(sessionId = "é")))
      val decomposed = take(DevelopmentEventCorrelation.keyC(_subject.copy(sessionId = "e\u0301")))
      Then("normalization does not collapse their distinct exact bytes")
      composed should not be decomposed
    }
  }
}
