package org.simplemodeling.scalalint

import scala.meta._
import scalafix.v1._

/** Warn on bounded binding inference, preserving explicit heterogeneous types.
  * @since Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author ASAMI, Tomoharu
  */
final class NoInferredAny extends SemanticRule("NoInferredAny") {
  private def _any(tpe: SemanticType): Boolean = tpe match {
    case TypeRef(_, symbol, args) => symbol.value == "scala/Any#" || args.exists(_any)
    case UnionType(types) => types.exists(_any)
    case _ => false
  }

  private def _explicitAny(tree: Term)(implicit doc: SemanticDocument): Boolean = tree match {
    case Term.Apply(fun, _) => _explicitAny(fun)
    case Term.ApplyType(_, args) => args.flatMap(SafetySemantic.tpe).exists(_any)
    case _ => false
  }

  override def fix(implicit doc: SemanticDocument): Patch = Patch.fromIterable(doc.tree.collect {
    case value: Defn.Val if value.decltpe.isEmpty && !_explicitAny(value.rhs) =>
      val inferred = value.pats.flatMap(SafetySemantic.tpe)
      val alternatives = value.rhs match {
        case Term.If(_, left, right) => List(left, right)
        case Term.Match(_, cases) => cases.map(_.body)
        case application: Term.Apply => SafetySemantic.arguments(application)
        case _ => Nil
      }
      val original = alternatives.flatMap(SafetySemantic.tpe)
      if (inferred.exists(_any) && alternatives.size >= 2 && original.size == alternatives.size &&
        !original.exists(_any) && !original.forall(SafetySemantic.numeric))
        SafetySemantic.finding(value, "Inference widened original alternatives to Any: " + original.map(_.toString).distinct.mkString(", "), warning = true)
      else if (inferred.exists(_any) && alternatives.size >= 2 && original.size != alternatives.size)
        SafetySemantic.unresolved(value, "inferred Any original alternatives")
      else Patch.empty
  })
}
