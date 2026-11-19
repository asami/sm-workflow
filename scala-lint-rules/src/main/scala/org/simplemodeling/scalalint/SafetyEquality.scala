package org.simplemodeling.scalalint

import scala.meta._
import scalafix.v1._

/** Actual standard equality method families and caller operand queries.
  * @since Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author ASAMI, Tomoharu
  */
private[scalalint] object SafetyEquality {
  def direct(tree: Tree)(implicit doc: SemanticDocument): Boolean = {
    val target = SafetySemantic.method(tree)
    val invoked = tree.isInstanceOf[Term.Apply] || tree.isInstanceOf[Term.ApplyInfix]
    invoked && target.symbol.info.exists(info => Set("==", "!=", "equals")(info.displayName) &&
      Set("scala/Any#", "scala/AnyRef#", "java/lang/Object#")(info.owner.value))
  }

  val membership: Set[String] = Set("contains", "indexOf", "lastIndexOf", "containsSlice", "sameElements", "diff", "intersect", "get", "getOrElse", "apply", "removed", "updated")
  val aggregate: Set[String] = Set("distinct", "distinctBy", "groupBy", "groupMap", "groupMapReduce", "toSet", "toMap")

  def operation(tree: Tree)(implicit doc: SemanticDocument): Option[String] = {
    if (SafetySemantic.standard(tree)) SafetySemantic.info(tree).map(_.displayName).filter { operation =>
      !Set("get", "getOrElse", "apply", "removed", "updated")(operation) ||
        SafetySemantic.receiver(tree).flatMap(SafetySemantic.tpe).exists(found => SafetySemantic.bases(found).exists(base =>
          SafetySemantic.name(base) == "scala/collection/Map#" || SafetySemantic.name(base) == "scala/collection/Set#")) ||
        SafetySemantic.info(tree).exists(info => info.owner.value.contains("/Set.") || info.owner.value.contains("/Map."))
    }
    else None
  }

  def pairs(tree: Tree)(implicit doc: SemanticDocument): List[(SemanticType, SemanticType)] = {
    val args = SafetySemantic.arguments(tree)
    val receiver = SafetySemantic.receiver(tree)
    if (direct(tree)) receiver.flatMap(SafetySemantic.tpe).toList.flatMap(left => args.headOption.flatMap(SafetySemantic.tpe).map(left -> _))
    else operation(tree).filter(membership).toList.flatMap { operation =>
      val left = receiver.flatMap(SafetySemantic.element)
      val right = args.headOption.flatMap { arg =>
        if (Set("containsSlice", "sameElements", "diff", "intersect")(operation)) SafetySemantic.element(arg)
        else SafetySemantic.tpe(arg)
      }
      // Map APIs compare keys, not their (key,value) iterable element.
      val key = receiver.flatMap(SafetySemantic.tpe).toList.flatMap(SafetySemantic.bases(_)).collectFirst {
        case TypeRef(_, symbol, key :: _) if symbol.value == "scala/collection/Map#" => key
      }
      for (a <- key.orElse(left).toList; b <- right.toList) yield a -> b
    }
  }
}
