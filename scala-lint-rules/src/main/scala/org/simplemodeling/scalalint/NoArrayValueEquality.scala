package org.simplemodeling.scalalint

import scala.meta._
import scalafix.v1._

/** Array reference equality must not masquerade as element equality.
  * @since Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author ASAMI, Tomoharu
  */
final class NoArrayValueEquality extends SemanticRule("NoArrayValueEquality") {
  override def fix(implicit doc: SemanticDocument): Patch = Patch.fromIterable(doc.tree.collect {
    case term: Term if SafetyEquality.direct(term) =>
      val operands = SafetySemantic.receiver(term).toList ++ SafetySemantic.arguments(term)
      if (operands.exists(operand => SafetySemantic.tpe(operand).exists(tpe => SafetySemantic.name(tpe) == "scala/Array#")))
        SafetySemantic.finding(term, "Array universal equality compares references; choose explicit identity or the intended element equality")
      else if (operands.exists(operand => SafetySemantic.tpe(operand).isEmpty)) SafetySemantic.unresolved(term, "array equality")
      else Patch.empty
    case term: Term if SafetySemantic.call(term) && SafetySemantic.method(term).symbol.info.exists(_.displayName == "sameElements") && SafetySemantic.standard(term) =>
      val nested = SafetySemantic.receiver(term).flatMap(SafetySemantic.element).exists {
        case TypeRef(_, symbol, args) => symbol.value == "scala/Array#" && args.nonEmpty
        case _ => false
      }
      if (nested) SafetySemantic.finding(term, "sameElements is shallow for nested arrays; choose the intended nested element comparison", warning = true)
      else Patch.empty
  })
}
