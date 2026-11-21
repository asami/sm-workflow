package org.simplemodeling.scalalint

import scala.meta._
import scalafix.v1._

/** Bounded statement and Unit callback adaptation, shared by both profiles.
  * @since Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author ASAMI, Tomoharu
  */
private[scalalint] object SafetyDiscarded {
  private def _unit(tpe: SemanticType): Boolean = SafetySemantic.name(tpe) == "scala/Unit#"
  private def _unit_function(tpe: SemanticType): Boolean = tpe match {
    case TypeRef(_, symbol, args) if symbol.value.startsWith("scala/Function") => args.lastOption.exists(_unit)
    case _ => false
  }

  private def _argument(function: Term, application: Term.Apply)(implicit doc: SemanticDocument): Boolean = {
    val Term.Apply(fun, args) = application
    val index = args.indexWhere(_ eq function)
    val foreach = SafetySemantic.method(fun).symbol.info.exists(_.displayName == "foreach") && SafetySemantic.standard(fun)
    index >= 0 && (foreach || SafetySemantic.method(fun).symbol.info.exists(_.signature match {
      case MethodSignature(_, lists, _) => lists.headOption.flatMap(_.lift(index)).flatMap(info => SafetySemantic.result(info.signature)).exists(_unit_function)
      case _ => false
    }))
  }

  private def _callback(function: Term)(implicit doc: SemanticDocument): Boolean = function.parent.exists {
    case block @ Term.Block(List(only)) if only eq function => _callback(block)
    case value: Defn.Val => value.pats.exists(pat => SafetySemantic.tpe(pat).exists(_unit_function))
    // Scalameta 4.9 represents the argument list as a parent node of its own.
    // Keep the actual argument and call so Unit adaptation still needs proof.
    case clause: Term.ArgClause => clause.parent.exists {
      case application: Term.Apply if application.argClause eq clause => _argument(function, application)
      case _ => false
    }
    case application: Term.Apply => _argument(function, application)
    case _ => false
  }

  def discarded(tree: Tree)(implicit doc: SemanticDocument): Boolean = tree.parent.exists {
    case Term.Block(stats) => stats.lastOption.exists(last => !(last eq tree)) || discarded(tree.parent.get)
    case definition: Defn.Def => (definition.body eq tree) && definition.name.symbol.info
      .flatMap(info => SafetySemantic.result(info.signature)).exists(_unit)
    case function: Term.Function => (function.body eq tree) && _callback(function)
    case function: Term.AnonymousFunction => (function.body eq tree) && _callback(function)
    case Term.If(_, thenp, elsep) if (thenp eq tree) || (elsep eq tree) => discarded(tree.parent.get)
    case clause: Case => (clause.body eq tree) && clause.parent.exists(discarded)
    case Term.Match(_, _) => discarded(tree.parent.get)
    case _ => false
  }

  def value(tree: Term): Boolean = tree match {
    case _: Term.Block | _: Term.If | _: Term.Match | _: Term.Function | _: Term.AnonymousFunction => false
    case _ => true
  }

  private def _lambda(tree: Term): Boolean = tree match {
    case _: Term.Function | _: Term.AnonymousFunction => true
    case Term.Block(List(only: Term)) => _lambda(only)
    case _ => false
  }

  def callbacks(accept: SemanticType => Boolean)(implicit doc: SemanticDocument): Patch = Patch.fromIterable(doc.tree.collect {
    case application @ Term.Apply(fun, args) =>
      val signature = SafetySemantic.info(fun).map(_.signature)
      val foreach = SafetySemantic.info(fun).exists(_.displayName == "foreach") && SafetySemantic.standard(fun)
      Patch.fromIterable(args.zipWithIndex.collect {
        case (arg, index) if !_lambda(arg) &&
          (foreach || signature.exists {
            case MethodSignature(_, lists, _) => lists.headOption.flatMap(_.lift(index)).flatMap(info => SafetySemantic.result(info.signature)).exists(_unit_function)
            case _ => false
          }) =>
          val returned = SafetySemantic.tpe(arg).map {
            case TypeRef(_, symbol, types) if symbol.value.startsWith("scala/Function") && types.nonEmpty => types.last
            case other => other
          }
          if (returned.exists(accept)) SafetySemantic.finding(arg, "Failure-bearing callback result is discarded by Unit adaptation; handle or propagate it")
          else if (returned.isEmpty) SafetySemantic.unresolved(arg, "Unit-adapted callback result")
          else Patch.empty
      })
  })

  def patches(types: Set[String])(implicit doc: SemanticDocument): Patch = Patch.fromIterable(doc.tree.collect {
    case term: Term if value(term) && discarded(term) => SafetySemantic.tpe(term) match {
      case Some(found) if SafetySemantic.hasType(found, types) =>
        SafetySemantic.finding(term, "Failure-bearing result is discarded; handle, return, compose or hand it to its owner")
      case None if term.isInstanceOf[Term.Name] && SafetySemantic.info(term).isEmpty =>
        SafetySemantic.unresolved(term, "discarded result")
      case None if SafetySemantic.method(term).symbol.isNone && (term.isInstanceOf[Term.Apply] || term.isInstanceOf[Term.Select]) =>
        SafetySemantic.unresolved(term, "discarded result")
      case _ => Patch.empty
    }
  }) + callbacks(found => SafetySemantic.hasType(found, types))
}
