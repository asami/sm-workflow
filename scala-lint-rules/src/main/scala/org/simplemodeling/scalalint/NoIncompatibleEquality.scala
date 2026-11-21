package org.simplemodeling.scalalint

import scala.meta._
import scalafix.v1._

/** Compare original caller operands before library generic widening.
  * @since Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author ASAMI, Tomoharu
  */
final class NoIncompatibleEquality extends SemanticRule("NoIncompatibleEquality") {
  override def fix(implicit doc: SemanticDocument): Patch = Patch.fromIterable(doc.tree.collect {
    case term: Term if SafetySemantic.call(term) &&
      (SafetyEquality.direct(term) || SafetyEquality.operation(term).exists(SafetyEquality.membership)) &&
      !SafetySemantic.info(term).exists(info => info.displayName == "apply" &&
        (info.owner.value.contains("/Set.") || info.owner.value.contains("/Map."))) =>
      val pairs = SafetyEquality.pairs(term)
      if (pairs.exists { case (left, right) => SafetySemantic.incompatible(left, right) })
        SafetySemantic.finding(term, "Equality compares provably incompatible original operand/element/key types")
      else if (pairs.isEmpty && SafetySemantic.arguments(term).nonEmpty)
        SafetySemantic.unresolved(term, "original equality operand/element/key")
      else Patch.empty
  })
}
