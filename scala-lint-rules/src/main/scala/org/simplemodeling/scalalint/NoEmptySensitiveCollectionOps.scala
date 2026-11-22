package org.simplemodeling.scalalint

import scala.meta._
import scalafix.v1._

/** Resolved semantic safety check.
  * @since Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author ASAMI, Tomoharu
  */
final class NoEmptySensitiveCollectionOps extends SemanticRule("NoEmptySensitiveCollectionOps") {
  private val _partial = Set("head", "last", "tail", "init", "reduce", "reduceLeft", "reduceRight", "min", "minBy", "max", "maxBy")
  override def fix(implicit doc: SemanticDocument): Patch = Patch.fromIterable(doc.tree.collect {
    case name: Term.Name if _partial(name.value) && SafetySemantic.standard(name) =>
      SafetySemantic.finding(name, "Standard collection operation may throw on empty input; handle emptiness explicitly")
    case name: Term.Name if _partial(name.value) && name.symbol.info.isEmpty && name.symbol.isGlobal &&
        name.symbol.owner.value.startsWith("scala/collection/") =>
      SafetySemantic.finding(name, "Standard collection operation may throw on empty input; handle emptiness explicitly")
    case name: Term.Name if _partial(name.value) && name.symbol.info.isEmpty && !name.symbol.isGlobal => SafetySemantic.unresolved(name, "empty-sensitive operation")
  })
}
