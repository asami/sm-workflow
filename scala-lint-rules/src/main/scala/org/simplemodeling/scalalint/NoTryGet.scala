package org.simplemodeling.scalalint

import scala.meta._
import scalafix.v1._

/** Resolved semantic safety check.
  * @since Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author ASAMI, Tomoharu
  */
final class NoTryGet extends SemanticRule("NoTryGet") {
  private val _partial = SymbolMatcher.normalized("scala/util/Try#get().", "scala/util/Success#get().", "scala/util/Failure#get().")
  override def fix(implicit doc: SemanticDocument): Patch = Patch.fromIterable(doc.tree.collect {
    case name: Term.Name if _partial.matches(name) => SafetySemantic.finding(name, "Handle Try success and failure explicitly; partial get may throw")
    case name: Term.Name if name.value == "get" && name.symbol.info.isEmpty && !name.symbol.isGlobal => SafetySemantic.unresolved(name, "partial extraction")
  })
}
