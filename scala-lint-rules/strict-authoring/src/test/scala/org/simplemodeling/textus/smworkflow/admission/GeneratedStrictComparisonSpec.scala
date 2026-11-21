package org.simplemodeling.textus.smworkflow.admission {

import scala.language.strictEquality
import scala.compiletime.testing.typeCheckErrors
import domain.value.{CountryCode, RegionCode}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Consumer contracts for selected output of the real CML library generator.
  * @since   Nov. 20, 2026
  * @version Nov. 20, 2026
  * @author  ASAMI, Tomoharu
  */
final class GeneratedStrictComparisonSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "The generated strict Scala 3 consumer" should {
    "preserve same-type generated value comparisons and collection predicates" in {
      Given("two generated library value types from the CML fixture")
      val values = Vector(CountryCode("JP"), CountryCode("GB"))
      val selected = CountryCode("GB")

      When("the consumer compares generated values directly and in collection predicates")
      val same = CountryCode("JP") == CountryCode("JP")
      val regionsame = RegionCode("GB") == RegionCode("GB")
      val exists = values.exists(_ == selected)
      val found = values.find(_ == selected)
      val filtered = values.filter(_ == selected)
      val distinct = values.forall(_ != CountryCode("US"))

      Then("the generator's bounded contract compiles and retains the intended values")
      val _ = same shouldBe true
      val _ = regionsame shouldBe true
      val _ = exists shouldBe true
      val _ = found shouldBe Some(selected)
      val _ = filtered shouldBe Vector(selected)
      val _ = distinct shouldBe true
      RegionCode("GB").value shouldBe "GB"
    }

    "reject cross-type generated comparisons directly and inside predicates" in {
      Given("different generated value types with the same String representation")

      When("the actual strict compiler typechecks direct equality and collection predicates")
      val direct = typeCheckErrors("""
        import scala.language.strictEquality
        import domain.value.{CountryCode, RegionCode}
        CountryCode("same") == RegionCode("same")
      """)
      val inequality = typeCheckErrors("""
        import scala.language.strictEquality
        import domain.value.{CountryCode, RegionCode}
        CountryCode("same") != RegionCode("same")
      """)
      val exists = typeCheckErrors("""
        import scala.language.strictEquality
        import domain.value.{CountryCode, RegionCode}
        Vector(CountryCode("same")).exists(_ == RegionCode("same"))
      """)
      val find = typeCheckErrors("""
        import scala.language.strictEquality
        import domain.value.{CountryCode, RegionCode}
        Vector(CountryCode("same")).find(_ == RegionCode("same"))
      """)
      val filter = typeCheckErrors("""
        import scala.language.strictEquality
        import domain.value.{CountryCode, RegionCode}
        Vector(CountryCode("same")).filter(_ == RegionCode("same"))
      """)

      Then("every rejection identifies the incompatible generated types rather than an unrelated error")
      Vector(direct, inequality, exists, find, filter).foreach { errors =>
        val _ = errors should not be empty
        val messages = errors.map(_.message).mkString("\n")
        val _ = messages should include ("cannot be compared")
        val _ = messages should include ("CountryCode")
        val _ = messages should include ("RegionCode")
      }
    }

    "retain the precompiled contains limitation for the actual generated types" in {
      Given("a strict consumer and the precompiled Vector.contains signature")

      When("the query uses the other generated value type")
      val errors = typeCheckErrors("""
        import scala.language.strictEquality
        import domain.value.{CountryCode, RegionCode}
        Vector(CountryCode("same")).contains(RegionCode("same"))
      """)
      // REASON: retain the precompiled library gap; direct and predicate comparisons above must reject these types.
      val result = Vector(CountryCode("same")).contains(RegionCode("same")) // scalafix:ok NoIncompatibleEquality

      Then("contains still compiles and returns false without granting application comparison permission")
      val _ = errors shouldBe empty
      result shouldBe false
    }
  }
}

}
