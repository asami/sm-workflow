package org.simplemodeling.scalalint

import scala.meta._
import scalafix.v1._

/** Resolved semantic safety check.
  * @since Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author ASAMI, Tomoharu
  */
final class NoEitherProjectionGet extends SemanticRule("NoEitherProjectionGet") {
  private val _partial = SymbolMatcher.normalized("scala/util/Either.LeftProjection#get().", "scala/util/Either.RightProjection#get().")
  override def fix(implicit doc: SemanticDocument): Patch = Patch.fromIterable(doc.tree.collect {
    case name: Term.Name if _partial.matches(name) => SafetySemantic.finding(name, "Handle both Either alternatives explicitly instead of partial projection get")
    case name: Term.Name if name.value == "get" && name.symbol.info.isEmpty => SafetySemantic.unresolved(name, "partial extraction")
  })
}
