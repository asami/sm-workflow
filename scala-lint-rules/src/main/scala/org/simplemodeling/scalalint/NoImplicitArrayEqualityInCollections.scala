package org.simplemodeling.scalalint

import scala.meta._
import scalafix.v1._

/** Warn when a standard collection's default comparator encounters arrays.
  * @since Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author ASAMI, Tomoharu
  */
final class NoImplicitArrayEqualityInCollections extends SemanticRule("NoImplicitArrayEqualityInCollections") {
  private def _array(tpe: SemanticType): Boolean = SafetySemantic.name(tpe) == "scala/Array#"

  override def fix(implicit doc: SemanticDocument): Patch = Patch.fromIterable(doc.tree.collect {
    case term: Term if SafetySemantic.call(term) && SafetyEquality.operation(term).exists(operation =>
      SafetyEquality.membership(operation) || SafetyEquality.aggregate(operation)) =>
      val operation = SafetyEquality.operation(term).get
      val receiver = SafetySemantic.receiver(term)
      val projected = Set("distinctBy", "groupBy", "groupMap", "groupMapReduce")(operation)
      val key = if (projected) SafetySemantic.argumentLists(term).headOption.toList.flatten.headOption.flatMap {
        case function: Term.Function => SafetySemantic.tpe(function.body)
        case function: Term.AnonymousFunction => SafetySemantic.tpe(function.body)
        case alias => SafetySemantic.tpe(alias).collect {
          case TypeRef(_, symbol, args) if symbol.value.startsWith("scala/Function") && args.nonEmpty => args.last
        }
      } else if (operation == "toMap") receiver.flatMap(SafetySemantic.element).collect {
        case TypeRef(_, symbol, key :: _) if symbol.value == "scala/Tuple2#" => key
      } else receiver.flatMap(SafetySemantic.element)
      val mapkey = receiver.flatMap(SafetySemantic.tpe).toList.flatMap(SafetySemantic.bases(_)).collectFirst {
        case TypeRef(_, symbol, key :: _) if symbol.value == "scala/collection/Map#" => key
      }
      val constructor = SafetySemantic.info(term).exists(info => info.displayName == "apply" &&
        (info.owner.value.contains("/Set.") || info.owner.value.contains("/Map.")))
      val arrays = key.exists(_array) || mapkey.exists(_array) || (constructor && SafetySemantic.arguments(term).exists(arg =>
        SafetySemantic.tpe(arg).exists(_array) || (arg match {
          case Term.Tuple(first :: _) => SafetySemantic.tpe(first).exists(_array)
          case _ => false
        })))
      if (arrays) SafetySemantic.finding(term, "Standard collection default array equality uses reference identity; make element/key identity intent explicit", warning = true)
      else if (key.isEmpty && !constructor && receiver.nonEmpty) SafetySemantic.unresolved(term, "collection equality key")
      else Patch.empty
  })
}
