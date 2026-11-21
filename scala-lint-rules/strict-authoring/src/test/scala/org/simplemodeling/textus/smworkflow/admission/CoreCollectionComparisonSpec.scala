package org.simplemodeling.textus.smworkflow.admission {

import scala.language.strictEquality
import scala.compiletime.testing.typeCheckErrors
import org.goldenport.observation.TraceId
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Consumer-level equality and explicit identity contracts for Core C04.
  * @since   Nov. 21, 2026
  * @version Nov. 21, 2026
  * @author  ASAMI, Tomoharu
  */
final class CoreCollectionComparisonSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import CoreCollectionComparisonExamples.*
  import StrictComparisonExamples.SourceId

  "The authored Core collection consumer" should {
    "preserve SourceId value membership for every selected value and empty receiver" in {
      Given("the authored source model and an explicitly same-type value query")
      val values = Vector("", "source", "session", "日本語", "same:value")

      When("the main consumer uses the delivered containsEq with its declared value equality")
      val comparisons = for (left <- values; right <- values) yield {
        (containsSource(Vector(SourceId(left)), SourceId(right)), left == right)
      }

      Then("membership follows the complete source value and absence stays false")
      comparisons.foreach { case (actual, expected) => val _ = actual shouldBe expected }
      containsSource(Vector.empty[SourceId], SourceId("source")) shouldBe false
    }

    "preserve explicit trace identity, first representatives and unidentified occurrences" in {
      Given("one repeated trace with conflicting payloads and a repeated unidentified observation")
      val trace = TraceId("major", "minor")
      val first = Observation(Some(trace), "first")
      val conflict = Observation(Some(trace), "conflict")
      val absent = Observation(None, "unidentified")
      val values = Vector(absent, first, conflict, absent)

      When("the authored consumer explicitly chooses the optional trace relation")
      val found = findTrace(values, trace)
      val distinct = distinctTraces(values)

      Then("lookup keeps the first payload, does not resolve conflicts and never collapses absent IDs")
      val _ = containsTrace(values, trace) shouldBe true
      val _ = found shouldBe Some(first)
      val _ = distinct shouldBe Vector(absent, first, absent)
      val _ = findTrace(Vector(absent), trace) shouldBe None
      val _ = containsTrace(Vector.empty[Observation], trace) shouldBe false
      distinctTraces(Vector.empty[Observation]) shouldBe Vector.empty[Observation]
    }

    "reject an incompatible value even when a common-parent Eq is available" in {
      Given("captured and directly inferred SourceId receivers and a sibling SessionId")

      When("the selected compiler checks both calls against the actual Core API")
      val captured = typeCheckErrors("""
        import org.goldenport.util.TypeSafeCollections.*
        import org.simplemodeling.textus.smworkflow.admission.StrictComparisonExamples.*
        given cats.Eq[Any] = cats.Eq.instance[Any]((_, _) => true)
        given cats.Eq[SourceId] = cats.Eq.instance((left, right) => left.value == right.value)
        val values = Vector(SourceId("same")).typeSafe
        values.containsEq(SessionId("same"))
      """)
      val inferred = typeCheckErrors("""
        import org.goldenport.util.TypeSafeCollections.*
        import org.simplemodeling.textus.smworkflow.admission.StrictComparisonExamples.*
        given cats.Eq[Any] = cats.Eq.instance[Any]((_, _) => true)
        given cats.Eq[SourceId] = cats.Eq.instance((left, right) => left.value == right.value)
        Vector(SourceId("same")).typeSafe.containsEq(SessionId("same"))
      """)

      Then("each error is attributable to the incompatible containsEq argument")
      Vector(captured, inferred).foreach { errors =>
        val _ = errors should have size 1
        errors.foreach { error =>
          val _ = error.lineContent should include ("containsEq(SessionId")
          val _ = error.message should include ("=:=" )
          val _ = error.message should include ("SourceId")
          val _ = error.message should include ("SessionId")
        }
      }
    }

    "reject a sibling identity without broadening the selected trace relation" in {
      Given("the main consumer's TraceId lookup and a SpanId with the same string fields")

      When("the compiler checks the authored consumer boundary")
      val errors = typeCheckErrors("""
        import org.goldenport.observation.SpanId
        import org.simplemodeling.textus.smworkflow.admission.CoreCollectionComparisonExamples.*
        containsTrace(Vector.empty[Observation], SpanId("major", "minor"))
      """)

      Then("the rejection names both identity types at the attempted lookup")
      val _ = errors should have size 1
      errors.foreach { error =>
        val _ = error.lineContent should include ("containsTrace")
        val _ = error.message should include ("SpanId")
        val _ = error.message should include ("TraceId")
      }
    }
  }
}

}
