package org.simplemodeling.textus.smworkflow.admission {

import scala.language.strictEquality
import scala.compiletime.testing.typeCheckErrors
import cats.Eq
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Executable comparison contracts compiled under the full strict profile.
  * @since   Nov. 20, 2026
  * @version Nov. 27, 2026
  * @author  ASAMI, Tomoharu
  */
final class WorkflowStrictComparisonSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import StrictComparisonExamples.*

  "The authored strict Scala 3 comparison contract" should {
    "preserve intended same-type equality through direct and bounded comparisons" in {
      Given("record-shaped IDs with a derived same-type contract and a value-bounded revision contract")
      val values = Vector("", "source", "session", "日本語", "same:value")

      When("each pair of values is compared directly and through its revision")
      val comparisons = for (left <- values; right <- values) yield {
        val expected = left == right
        val source = SourceId(left) == SourceId(right)
        val session = SessionId(left) == SessionId(right)
        val revision = sameRevision(Revision(SourceId(left)), Revision(SourceId(right)))
        (expected, source, session, revision)
      }

      Then("only the wrapped value determines equality within each intended type")
      comparisons.foreach { case (expected, source, session, revision) =>
        val _ = source shouldBe expected
        val _ = session shouldBe expected
        val _ = revision shouldBe expected
      }
    }

    "distinguish explicit value equality from source identity" in {
      Given("snapshots of one source at different payloads")
      given Eq[SourceSnapshot] = Eq.fromUniversalEquals[SourceSnapshot]
      val first = SourceSnapshot(SourceId("one"), "before")
      val changed = SourceSnapshot(SourceId("one"), "after")
      val same = SourceSnapshot(SourceId("one"), "before")
      val other = SourceSnapshot(SourceId("two"), "before")

      Then("full values and the declared source ID answer separate questions")
      val _ = sameValue(first, same) shouldBe true
      val _ = sameValue(first, changed) shouldBe false
      val _ = sameSource(first, changed) shouldBe true
      sameSource(first, other) shouldBe false
    }

    "distinguish byte contents from array reference identity" in {
      Given("two byte arrays with equal contents and a different byte array")
      val first = Array[Byte](1, 2)
      val copy = Array[Byte](1, 2)
      val different = Array[Byte](1, 3)

      Then("element comparison and reference comparison keep their distinct meanings")
      val _ = sameBytes(first, copy) shouldBe true
      val _ = sameBytes(first, different) shouldBe false
      val _ = sameArray(first, copy) shouldBe false
      sameArray(first, first) shouldBe true
    }

    "keep intended comparisons available inside collection predicates" in {
      Given("distinct source IDs and a same-type query")
      val values = Vector(SourceId("first"), SourceId("second"))
      val selected = SourceId("second")

      When("the query appears in exists, find, filter and forall predicates")
      val exists = values.exists(_ == selected)
      val found = values.find(_ == selected)
      val filtered = values.filter(_ == selected)
      val distinct = values.forall(_ != SourceId("absent"))
      val revised = values.map(value => Revision(value)).exists(_ == Revision(selected))

      Then("the same-type contract compiles and retains the ordinary collection behavior")
      val _ = exists shouldBe true
      val _ = found shouldBe Some(selected)
      val _ = filtered shouldBe Vector(selected)
      val _ = distinct shouldBe true
      revised shouldBe true
    }

    "reject direct comparison between distinct ID types with equal representations" in {
      Given("two different derived ID types whose underlying String is the same")

      When("the compiler typechecks equality and inequality in an actively strict scope")
      val equality = typeCheckErrors("""
        import scala.language.strictEquality
        import org.simplemodeling.textus.smworkflow.admission.StrictComparisonExamples.*
        SourceId("same") == SessionId("same")
      """)
      val inequality = typeCheckErrors("""
        import scala.language.strictEquality
        import org.simplemodeling.textus.smworkflow.admission.StrictComparisonExamples.*
        SourceId("same") != SessionId("same")
      """)

      Then("both snippets fail specifically at the incompatible comparison")
      Vector(equality, inequality).foreach { errors =>
        val _ = errors should not be empty
        val messages = errors.map(_.message).mkString("\n")
        val _ = messages should include ("cannot be compared")
        val _ = messages should include ("SourceId")
        val _ = messages should include ("SessionId")
      }
    }

    "retain distinct-ID rejection through the explicitly bounded revision instance" in {
      Given("the revision instance requires comparability of the stored value types")

      When("distinct ID revisions are compared directly or through the generic operation")
      val direct = typeCheckErrors("""
        import scala.language.strictEquality
        import org.simplemodeling.textus.smworkflow.admission.StrictComparisonExamples.*
        Revision(SourceId("same")) == Revision(SessionId("same"))
      """)
      val generic = typeCheckErrors("""
        import scala.language.strictEquality
        import org.simplemodeling.textus.smworkflow.admission.StrictComparisonExamples.*
        sameRevision(Revision(SourceId("same")), Revision(SessionId("same")))
      """)

      Then("neither type inference nor the bounded instance supplies cross-ID permission")
      val _ = direct should not be empty
      val _ = direct.map(_.message).mkString("\n") should include ("cannot be compared")
      val _ = generic should not be empty
      val messages = generic.map(_.message).mkString("\n")
      val _ = messages should include ("cannot be compared with == or !=")
      val _ = messages should include ("SourceId")
      messages should include ("SessionId")
    }

    "reject distinct-ID equality inside higher-order collection predicates" in {
      Given("a source-ID receiver and a session-ID query")

      When("the compiler typechecks exists, find and filter predicate bodies")
      val exists = typeCheckErrors("""
        import scala.language.strictEquality
        import org.simplemodeling.textus.smworkflow.admission.StrictComparisonExamples.*
        Vector(SourceId("same")).exists(_ == SessionId("same"))
      """)
      val find = typeCheckErrors("""
        import scala.language.strictEquality
        import org.simplemodeling.textus.smworkflow.admission.StrictComparisonExamples.*
        Vector(SourceId("same")).find(_ == SessionId("same"))
      """)
      val filter = typeCheckErrors("""
        import scala.language.strictEquality
        import org.simplemodeling.textus.smworkflow.admission.StrictComparisonExamples.*
        Vector(SourceId("same")).filter(_ == SessionId("same"))
      """)
      val revised = typeCheckErrors("""
        import scala.language.strictEquality
        import org.simplemodeling.textus.smworkflow.admission.StrictComparisonExamples.*
        Vector(Revision(SourceId("same"))).exists(_ == Revision(SessionId("same")))
      """)

      Then("every predicate remains subject to the distinct-ID comparison contract")
      Vector(exists, find, filter, revised).foreach { errors =>
        val _ = errors should not be empty
        val messages = errors.map(_.message).mkString("\n")
        val _ = messages should include ("cannot be compared")
        val _ = messages should include ("SourceId")
        val _ = messages should include ("SessionId")
      }
    }

    "normalize nullable Java properties before constructing a comparison ID" in {
      Given("a Java properties boundary with present, empty and absent values")
      val properties = new java.util.Properties()
      val _ = properties.setProperty("present", "source")
      val _ = properties.setProperty("empty", "")

      When("the authored boundary converts each result into a typed optional ID")
      val values = Vector("present", "empty", "absent").map(key => sourceIdFromProperties(properties, key))
      val unhandled = typeCheckErrors("""
        new java.util.Properties().getProperty("absent").length
      """)

      Then("absence stays optional, an empty String stays present and unchecked nullable access is rejected")
      val _ = values shouldBe Vector(Some(SourceId("source")), Some(SourceId("")), None)
      val _ = unhandled should not be empty
      unhandled.map(_.message).mkString("\n") should include ("Null")
    }

    "retain the precompiled library contains gap as a successful compile case" in {
      Given("an actively strict caller using the standard precompiled Vector.contains API")

      When("contains receives another ID type without an application-side comparison bypass")
      val errors = typeCheckErrors("""
        import scala.language.strictEquality
        import org.simplemodeling.textus.smworkflow.admission.StrictComparisonExamples.*
        Vector(SourceId("same")).contains(SessionId("same"))
      """)
      // REASON: this case documents the precompiled library gap; rejection is asserted separately for direct/predicate comparisons.
      val result = Vector(SourceId("same")).contains(SessionId("same")) // scalafix:ok NoIncompatibleEquality

      Then("the library call still compiles and returns false for these distinct case classes")
      val _ = errors shouldBe empty
      result shouldBe false
    }
  }
}

}
