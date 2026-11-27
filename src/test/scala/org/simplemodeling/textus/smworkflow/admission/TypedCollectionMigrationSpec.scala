/**
 * @since   Nov. 27, 2026
 * @version Nov. 27, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import cats.Eq
import org.goldenport.util.TypeSafeCollections.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.compiletime.testing.typeCheckErrors

private[admission] object TypedCollectionMigrationFixture {
  private val pathEquality: Eq[String] = Eq.fromUniversalEquals[String]
  def candidatePathSelected(paths: Vector[String], path: String): Boolean =
    paths.typeSafe.containsEq(path)(using pathEquality)
}

@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class TypedCollectionMigrationSpec extends AnyWordSpec with Matchers {
  private val equality: Eq[String] = Eq.fromUniversalEquals[String]

  "The Core Vector comparison consumer" should {
    "preserve String membership and first-index semantics" in {
      val inputs = Vector(Vector.empty[String], Vector("a", "b", "a"), Vector("A", "a"), Vector(null, "a"))
      val queries: Vector[String] = Vector("a", "b", "missing", null)
      for (values <- inputs; query <- queries) {
        values.typeSafe.containsEq(query)(using equality) shouldBe values.contains(query)
        values.typeSafe.indexOfEq(query)(using equality) shouldBe values.indexOf(query)
        TypedCollectionMigrationFixture.candidatePathSelected(values, query) shouldBe values.contains(query)
      }
    }

    "reject incompatible queries even with a wider Eq and reject an unsupported start position" in {
      val contains = typeCheckErrors("""
        import org.goldenport.util.TypeSafeCollections.*
        given cats.Eq[String] = cats.Eq.fromUniversalEquals[String]
        given cats.Eq[Any] = cats.Eq.fromUniversalEquals[Any]
        Vector("a").typeSafe.containsEq(1)
      """)
      val index = typeCheckErrors("""
        import org.goldenport.util.TypeSafeCollections.*
        given cats.Eq[String] = cats.Eq.fromUniversalEquals[String]
        given cats.Eq[Any] = cats.Eq.fromUniversalEquals[Any]
        Vector("a").typeSafe.indexOfEq(1)
      """)
      val from = typeCheckErrors("""
        import org.goldenport.util.TypeSafeCollections.*
        given cats.Eq[String] = cats.Eq.fromUniversalEquals[String]
        Vector("a").typeSafe.indexOfEq("a", 1)
      """)
      contains should not be empty
      index should not be empty
      from should not be empty
    }

    "retain staging ordinal and partial staging behavior at actual consumer sites" in {
      val selected = Vector("docs", "docs/a.md", "docs/a.md")
      CommitHygiene.stagingOrdinal(selected, "docs/a.md") shouldBe 2
      CommitHygiene.stagingOrdinal(selected, "docs/b.md") shouldBe 1
      CommitHygiene.stagingOrdinal(selected, "other") shouldBe 0
      CommitHygiene.stagingOrdinal(Vector.empty, "other") shouldBe 0
      val dirty = Vector(RepositorySyncManagedGitObservation.DirtyPath('M', 'M', "docs/a.md", None))
      CommitHygiene.partialDiagnostic(dirty, Vector("docs/a.md")) should not be empty
      CommitHygiene.partialDiagnostic(dirty, Vector("docs/b.md")) shouldBe empty
      CommitHygiene.partialDiagnostic(dirty.map(_.copy(worktree = ' ')), Vector("docs/a.md")) shouldBe empty
    }
  }
}
