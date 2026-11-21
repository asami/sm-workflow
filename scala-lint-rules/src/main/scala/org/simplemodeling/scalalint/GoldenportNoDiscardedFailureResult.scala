package org.simplemodeling.scalalint

import scala.meta._
import scalafix.v1._

/** Goldenport result and declarative CNCF program handling, without linking it.
  * @since Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author ASAMI, Tomoharu
  */
final class GoldenportNoDiscardedFailureResult extends SemanticRule("GoldenportNoDiscardedFailureResult") {
  private def _algebra(found: SemanticType): Boolean = found match {
    // Scala 3 eta-expands UnitOfWorkOp to [A] =>> UnitOfWorkOp[A].
    case LambdaType(_, body) => _algebra(body)
    case TypeRef(_, symbol, _) => symbol.value == "org/goldenport/cncf/unitofwork/UnitOfWorkOp#"
    case _ => false
  }
  private def _program(found: SemanticType)(implicit doc: SemanticDocument): Boolean = SafetySemantic.bases(found).exists {
    case TypeRef(_, symbol, algebra :: _) if symbol.value == "cats/free/Free#" =>
      _algebra(algebra)
    case _ => false
  }
  override def fix(implicit doc: SemanticDocument): Patch =
    SafetyDiscarded.patches(Set("org/goldenport/Consequence#")) + Patch.fromIterable(doc.tree.collect {
      case term: Term if SafetyDiscarded.value(term) && SafetyDiscarded.discarded(term) => SafetySemantic.tpe(term) match {
        case Some(found) if _program(found) => SafetySemantic.finding(term, "Declarative UnitOfWork Program is discarded; compose or hand it to its managed owner, do not execute it as a lint repair")
        case _ => Patch.empty
      }
    }) + SafetyDiscarded.callbacks(found => _program(found))
}
