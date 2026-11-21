package org.simplemodeling.scalalint

import scala.meta._
import scalafix.v1._

/** Bounded local collection residency and resolved participating-field mutation.
  * @since Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author ASAMI, Tomoharu
  */
final class NoMutableEqualityKey extends SemanticRule("NoMutableEqualityKey") {
  private def _alias(tree: Term, depth: Int = 0)(implicit doc: SemanticDocument): Symbol = {
    if (depth >= 8) Symbol.None
    else tree match {
      case name: Term.Name =>
        doc.tree.collect {
          case value: Defn.Val if value.pats.exists { case Pat.Var(bound) => bound.symbol == name.symbol; case _ => false } && value.rhs.isInstanceOf[Term.Name] => value.rhs
        }.headOption match {
          case Some(term) => _alias(term, depth + 1)
          case _ => name.symbol
        }
      case _ => tree.symbol
    }
  }

  private def _fields(key: Term, sorted: Boolean, insertion: Term)(implicit doc: SemanticDocument): Option[Set[String]] = {
    SafetySemantic.tpe(key).flatMap { found =>
      val keytype = SafetySemantic.name(found)
      doc.tree.collect {
        case definition: Defn.Class if definition.name.symbol.value == keytype =>
          val constructorvars = definition.ctor.paramss.flatten.filter(_.mods.exists(_.isInstanceOf[Mod.VarParam])).map(_.name.value).toSet
          val bodyvars = definition.templ.stats.collect { case value: Defn.Var => value.pats.collect { case Pat.Var(name) => name.value } }.flatten.toSet
          val mutable = constructorvars ++ bodyvars
          val stable = definition.ctor.paramss.flatten.filter(param => param.mods.exists(_.isInstanceOf[Mod.ValParam]) || definition.mods.exists(_.isInstanceOf[Mod.Case])).map(_.name.value).toSet -- mutable
          val methods = definition.templ.stats.collect {
            case method: Defn.Def if Set("equals", "hashCode", "compare", "compareTo")(method.name.value) => method
          }
          val participating = methods.flatMap(_.body.collect {
            case name: Term.Name if name.symbol.info.exists(info => info.owner.value == keytype) && mutable(name.value) => name.value
          }).toSet
          if (sorted) {
            // Require the actual compiler-inserted Ordering value, not merely an available Eq or Ordering.
            val selected = SafetySemantic.implicitValues(insertion).filter(info => SafetySemantic.result(info.signature).exists {
              case TypeRef(_, symbol, arg :: _) => symbol.value == "scala/math/Ordering#" && SafetySemantic.name(arg) == keytype
              case _ => false
            }).map(_.symbol)
            val comparator = doc.tree.collect {
              case givenvalue: Defn.GivenAlias if selected.contains(givenvalue.name.symbol) => givenvalue.body
              case value: Defn.Val if value.pats.exists { case Pat.Var(name) => selected.contains(name.symbol); case _ => false } => value.rhs
            }.headOption
            comparator.filter(body => SafetySemantic.info(body).exists(info => info.owner.value == "scala/math/Ordering." && info.displayName == "by"))
              .flatMap(body => SafetySemantic.arguments(body).headOption).flatMap {
                case function: Term.Function => Some(function.body)
                case function: Term.AnonymousFunction => Some(function.body)
                case _ => None
              }.flatMap {
                case Term.Select(_, field) if field.symbol.info.exists(info => info.owner.value == keytype) &&
                  (mutable(field.value) || stable(field.value)) => Some(if (mutable(field.value)) Set(field.value) else Set.empty[String])
                case _ => None
              }
          }
          else if (definition.mods.exists(_.isInstanceOf[Mod.Case])) Some(participating ++ constructorvars)
          else if (methods.nonEmpty) Some(participating)
          else None
      }.headOption.flatten
    }
  }

  private def _collection(tree: Tree)(implicit doc: SemanticDocument): Boolean = SafetySemantic.tpe(tree).exists { found =>
    SafetySemantic.bases(found).exists(base => Set("scala/collection/Set#", "scala/collection/Map#")(SafetySemantic.name(base)))
  }

  override def fix(implicit doc: SemanticDocument): Patch = Patch.fromIterable(doc.tree.collect {
    case mutation @ Term.Assign(Term.Select(key, field), _) =>
      // Stay inside the containing lexical block. Nested/escaped lifetimes are not proved.
      val block = Iterator.iterate(mutation.parent)(_.flatMap(_.parent)).takeWhile(_.nonEmpty).flatten.collectFirst { case block: Term.Block => block }
      val identity = _alias(key)
      val insertions = block.toList.flatMap(_.stats).filter(_.pos.end < mutation.pos.start).flatMap { statement =>
        statement.collect {
          case call: Term if SafetySemantic.call(call) && SafetySemantic.standard(call) &&
            SafetySemantic.info(call).exists(info => Set("apply", "add", "addOne", "+=", "put", "update", "updated", "+")(info.displayName)) &&
            (SafetySemantic.receiver(call).exists(_collection) || _collection(call)) &&
            SafetySemantic.arguments(call).exists(arg => _alias(SafetySemantic.keyArgument(arg)) == identity) => call
        }
      }
      if (identity.isNone || insertions.isEmpty) Patch.empty
      else {
        val insertion = insertions.last
        val sorted = SafetySemantic.receiver(insertion).flatMap(SafetySemantic.tpe).orElse(SafetySemantic.tpe(insertion)).exists(found =>
          SafetySemantic.bases(found).exists(base => SafetySemantic.name(base).contains("Sorted")))
        val collection = SafetySemantic.receiver(insertion).filter(_collection).map(_.symbol).filterNot(_.isNone)
        val removed = block.toList.flatMap(_.stats).filter(statement => statement.pos.start > insertion.pos.end && statement.pos.end < mutation.pos.start).exists {
          // Only a direct statement establishes execution here. A call nested in
          // a conditional, lambda or local definition cannot prove removal.
          case call: Term if SafetySemantic.call(call) && SafetySemantic.standard(call) &&
            SafetySemantic.method(call).symbol.info.exists(info => Set("remove", "subtractOne", "-=", "clear")(info.displayName)) &&
            collection.exists(symbol => SafetySemantic.receiver(call).exists(_.symbol == symbol)) &&
            (SafetySemantic.arguments(call).isEmpty || SafetySemantic.arguments(call).exists(arg => _alias(arg) == identity)) => true
          case _ => false
        }
        if (removed) Patch.empty
        else _fields(key, sorted, insertion) match {
          case Some(fields) if fields(field.value) => SafetySemantic.finding(mutation, "Mutation changes a proven equality/hash participating field while the key is resident in a local collection")
          case Some(_) => Patch.empty
          case None => SafetySemantic.unresolved(mutation, "resident mutable key equality/Ordering participation")
        }
      }
  })
}
