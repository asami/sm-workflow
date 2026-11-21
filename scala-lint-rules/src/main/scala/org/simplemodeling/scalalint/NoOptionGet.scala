package org.simplemodeling.scalalint

import scala.meta._
import scalafix.v1._

/** Requires real SemanticDB. Missing documents are rejected by Scalafix itself.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
final class NoOptionGet extends SemanticRule("NoOptionGet") {
  private val _option_get = SymbolMatcher.normalized("scala/Option#get().", "scala/Some#get().", "scala/None.get().")

  override def fix(implicit doc: SemanticDocument): Patch = Patch.fromIterable(doc.tree.collect {
    case name: Term.Name if _option_get.matches(name) =>
      Patch.lint(Diagnostic("", "Handle None explicitly with fold, pattern matching or a typed error result", name.pos))
    case name: Term.Name if name.value == "get" && name.symbol.isNone =>
      Patch.lint(Diagnostic("AnalysisUnavailable", "Cannot resolve get; required Option.get analysis is unavailable", name.pos))
  })
}
