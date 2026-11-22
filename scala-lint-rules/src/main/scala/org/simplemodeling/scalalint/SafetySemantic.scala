package org.simplemodeling.scalalint

import scala.meta._
import scalafix.lint.LintSeverity
import scalafix.v1._

/** Small shared signature queries; no independent analysis runtime.
  * @since Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author ASAMI, Tomoharu
  */
private[scalalint] object SafetySemantic {
  private def _identifierInfo(tree: Tree)(implicit doc: SemanticDocument): Option[SymbolInformation] = tree match {
    case name: Term.Name => name.origin match {
      case parsed: scala.meta.trees.Origin.Parsed =>
        // Pinned Scalameta 4.9.3 can include trailing comment trivia in a
        // callback's bare name range. Trim only a lookup copy's parsed origin;
        // Scalafix still resolves the exact compiler occurrence in its original
        // input. Unsupported origins/token shapes retain unavailable analysis.
        val significant = parsed.allInputTokens.slice(parsed.begTokenIdx, parsed.endTokenIdx)
          .zipWithIndex.filterNot { case (token, _) =>
            token.isInstanceOf[Token.Comment] || token.text.forall(_.isWhitespace)
          }.toList
        significant match {
          case (token: Token.Ident, offset) :: Nil if token.pos.input == name.pos.input =>
            val index = parsed.begTokenIdx + offset
            scala.meta.internal.simplemodelinglint.OriginCompatibility.identifier(name, parsed, index).symbol.info
          case _ => None
        }
      case _ => None
    }
    case _ => None
  }

  // Scala 3 records parameterless defs as ValueSignature(ByNameType(result)).
  private def _resultType(tpe: SemanticType): SemanticType = tpe match {
    case ByNameType(found) => _resultType(found)
    case _ => tpe
  }

  def result(signature: Signature): Option[SemanticType] = signature match {
    case ValueSignature(tpe) => Some(_resultType(tpe))
    case MethodSignature(_, _, tpe) => Some(_resultType(tpe))
    case _ => None
  }

  def substitute(tpe: SemanticType, bindings: List[(Symbol, SemanticType)]): SemanticType = tpe match {
    case TypeRef(prefix, symbol, args) => bindings.find(_._1 == symbol).map(_._2)
      .getOrElse(TypeRef(prefix, symbol, args.map(substitute(_, bindings))))
    case _ => tpe
  }

  def method(tree: Tree): Tree = tree match {
    case Term.Apply(fun, _) => method(fun)
    case Term.ApplyType(fun, _) => method(fun)
    case Term.ApplyInfix(_, op, _, _) => op
    case Term.Select(_, name) => name
    case _ => tree
  }

  private def _synthetics(tree: SemanticTree): List[SemanticTree] = tree :: (tree match {
    case ApplyTree(fun, args) => _synthetics(fun) ++ args.flatMap(_synthetics)
    case TypeApplyTree(fun, _) => _synthetics(fun)
    case SelectTree(qual, _) => _synthetics(qual)
    case FunctionTree(_, body) => _synthetics(body)
    case _ => Nil
  })

  private def _related(synthetic: SemanticTree, target: Tree): Boolean = _synthetics(synthetic).exists {
    case OriginalTree(original) => original.pos != Position.None && target.pos != Position.None &&
      original.pos.start <= target.pos.start && original.pos.end >= target.pos.end
    case _ => false
  }

  private def _invocation(tree: Tree)(implicit doc: SemanticDocument): Option[(SymbolInformation, List[SemanticType])] = {
    val target = method(tree)
    val inferred = if (tree.isInstanceOf[Term.Apply] || tree.isInstanceOf[Term.ApplyType])
      doc.synthetics.filter(_related(_, target)).flatMap(_synthetics).collectFirst {
        // Factory apply synthetics place the OriginalTree beside the selected
        // method, in the enclosing ApplyTree, rather than inside SelectTree.
        case TypeApplyTree(SelectTree(qual, id), args) if
          (!target.symbol.isNone && id.info.symbol == target.symbol) ||
            (id.info.displayName == "apply" && !target.symbol.isNone && _synthetics(qual).exists {
              case OriginalTree(original) => original.pos == target.pos && original.symbol == target.symbol
              case _ => false
            }) => id.info -> args
        case TypeApplyTree(fun @ OriginalTree(original), args) if
          _related(fun, target) && !target.symbol.isNone && original.symbol == target.symbol &&
            original.symbol.info.nonEmpty => original.symbol.info.get -> args
      } else None
    inferred.orElse(target.symbol.info.orElse(_identifierInfo(target)).map(_ -> Nil))
  }

  def info(tree: Tree)(implicit doc: SemanticDocument): Option[SymbolInformation] = _invocation(tree).map(_._1)

  def implicitValues(tree: Tree)(implicit doc: SemanticDocument): List[SymbolInformation] =
    doc.synthetics.filter(synthetic => _related(synthetic, tree)).flatMap(_synthetics).collect {
      case IdTree(info) => info
    }.toList

  def tpe(tree: Tree)(implicit doc: SemanticDocument): Option[SemanticType] = tree match {
    case term: Term if term.parent.exists {
      case value: Defn.Val => (value.rhs eq term) && value.pats.exists { case Pat.Var(name) => name.symbol.info.nonEmpty; case _ => false }
      case _ => false
    } => term.parent.toList.collect { case value: Defn.Val => value }.flatMap(_.pats).collectFirst {
      case Pat.Var(name) => name.symbol.info.flatMap(info => result(info.signature))
    }.flatten
    case Pat.Var(name) => tpe(name)
    case Lit.Null() => Some(TypeRef(NoType, Symbol("scala/Null#"), Nil))
    case Lit.Char(_) => Some(TypeRef(NoType, Symbol("scala/Char#"), Nil))
    case Lit.String(_) => Some(TypeRef(NoType, Symbol("java/lang/String#"), Nil))
    case Lit.Int(_) => Some(TypeRef(NoType, Symbol("scala/Int#"), Nil))
    case Lit.Long(_) => Some(TypeRef(NoType, Symbol("scala/Long#"), Nil))
    case Lit.Double(_) => Some(TypeRef(NoType, Symbol("scala/Double#"), Nil))
    case Lit.Float(_) => Some(TypeRef(NoType, Symbol("scala/Float#"), Nil))
    case Lit.Boolean(_) => Some(TypeRef(NoType, Symbol("scala/Boolean#"), Nil))
    case Term.Ascribe(_, declared) => tpe(declared)
    case Type.Apply(base, args) => tpe(base).map {
      case TypeRef(prefix, symbol, _) => TypeRef(prefix, symbol, args.flatMap(tpe))
      case other => other
    }
    case name: Type.Name if !name.symbol.isNone => Some(TypeRef(NoType, name.symbol, Nil))
    case selected: Type.Select if !selected.symbol.isNone => Some(TypeRef(NoType, selected.symbol, Nil))
    case Term.New(init) => tpe(init.tpe)
    case Term.Block(stats) => stats.lastOption.flatMap(tpe)
    case _ =>
      val target = method(tree)
      _invocation(tree).flatMap { case (info, inferredargs) => result(info.signature).map(found => (info, inferredargs, found)) }.map { case (info, inferredargs, found) =>
        val explicit = (tree, Some(info.signature)) match {
          case (Term.ApplyType(_, args), Some(MethodSignature(params, _, _))) => params.map(_.symbol).zip(args.flatMap(tpe))
          case (Term.Apply(Term.ApplyType(_, args), _), Some(MethodSignature(params, _, _))) => params.map(_.symbol).zip(args.flatMap(tpe))
          case _ => Nil
        }
        val bindings = if (explicit.nonEmpty) explicit else info.signature match {
          case MethodSignature(params, _, _) => params.map(_.symbol).zip(inferredargs)
          case _ => Nil
        }
        val receiver = tree match {
          case Term.Select(qual, _) => Some(qual)
          case Term.Apply(Term.Select(qual, _), _) => Some(qual)
          case Term.ApplyInfix(qual, _, _, _) => Some(qual)
          case _ => None
        }
        val ownerbindings = receiver.flatMap(tpe).toList.flatMap {
          case TypeRef(_, symbol, args) => symbol.info.toList.flatMap(_.signature match {
            case ClassSignature(params, _, _, _) => params.map(_.symbol).zip(args)
            case _ => Nil
          })
          case _ => Nil
        }
        substitute(found, bindings ++ ownerbindings)
      }
  }

  def name(tpe: SemanticType): String = tpe match {
    case TypeRef(_, symbol, _) => symbol.value
    case _ => ""
  }

  def bases(tpe: SemanticType, depth: Int = 0)(implicit doc: SemanticDocument): List[SemanticType] = {
    if (depth >= 8) List(tpe)
    else tpe :: (tpe match {
      case TypeRef(_, symbol, args) => symbol.info.toList.flatMap(_.signature match {
        case ClassSignature(params, parents, _, _) => parents.flatMap(parent => bases(substitute(parent, params.map(_.symbol).zip(args)), depth + 1))
        case TypeSignature(params, _, upper) if upper != tpe => bases(substitute(upper, params.map(_.symbol).zip(args)), depth + 1)
        case _ => Nil
      })
      case SingleType(_, symbol) => symbol.info.flatMap(info => result(info.signature)).toList.flatMap(bases(_, depth + 1))
      case _ => Nil
    })
  }

  def hasType(tpe: SemanticType, names: Set[String])(implicit doc: SemanticDocument): Boolean = bases(tpe).exists(base => names(name(base)))

  def element(tree: Tree)(implicit doc: SemanticDocument): Option[SemanticType] = tpe(tree).flatMap { found =>
    bases(found).collectFirst {
      case TypeRef(NoType, symbol, Nil) if Set("java/lang/String#", "scala/collection/StringOps#")(symbol.value) =>
        TypeRef(NoType, Symbol("scala/Char#"), Nil)
      case TypeRef(_, symbol, args) if args.nonEmpty &&
        (symbol.value == "scala/Array#" || symbol.value == "scala/collection/IterableOnce#") => args.head
    }
  }

  def standard(tree: Tree)(implicit doc: SemanticDocument): Boolean = {
    // Inherited methods retain their library owner. A user override can define
    // a distinct nonempty/comparison contract and is not a library operation.
    info(tree).exists(_.owner.value.startsWith("scala/collection/"))
  }

  def unresolved(tree: Tree, purpose: String): Patch = Patch.lint(Diagnostic("AnalysisUnavailable", "Required " + purpose + " type/signature analysis is unavailable", tree.pos))
  def finding(tree: Tree, message: String, warning: Boolean = false): Patch = Patch.lint(Diagnostic("", message, tree.pos,
    severity = if (warning) LintSeverity.Warning else LintSeverity.Error))

  def arguments(tree: Tree): List[Term] = tree match {
    case Term.Apply(_, args) => args
    case Term.ApplyInfix(_, _, _, args) => args
    case _ => Nil
  }

  def call(tree: Tree): Boolean = tree.isInstanceOf[Term] && !tree.parent.exists {
    case Term.Apply(fun, _) => fun eq tree
    case Term.ApplyType(fun, _) => fun eq tree
    case _ => false
  }

  def argumentLists(tree: Tree): List[List[Term]] = tree match {
    case Term.Apply(fun, args) => argumentLists(fun) :+ args
    case Term.ApplyInfix(_, _, _, args) => List(args)
    case _ => Nil
  }

  def keyArgument(tree: Term)(implicit doc: SemanticDocument): Term = tree match {
    case Term.Tuple(first :: _) => first
    case Term.ApplyInfix(key, op, _, _) if op.symbol.info.exists(info =>
      info.owner.value == "scala/Predef.ArrowAssoc#" && info.displayName == "->") => key
    case _ => tree
  }

  def receiver(tree: Tree): Option[Term] = tree match {
    case Term.Select(qual, _) => Some(qual)
    case Term.Apply(fun, _) => receiver(fun)
    case Term.ApplyType(fun, _) => receiver(fun)
    case Term.ApplyInfix(qual, _, _, _) => Some(qual)
    case _ => None
  }

  def numeric(tpe: SemanticType): Boolean = Set("scala/Byte#", "scala/Short#", "scala/Int#", "scala/Long#", "scala/Float#", "scala/Double#", "scala/Char#")(name(tpe))

  /** Proves disjointness only for known final classes; open types remain unknown. */
  def incompatible(left: SemanticType, right: SemanticType)(implicit doc: SemanticDocument): Boolean = {
    def _closed_(tpe: SemanticType): Boolean = tpe match {
      case TypeRef(_, symbol, _) => symbol.info.exists(info => info.isFinal && (info.isCase ||
        symbol.value == "java/lang/String#" || numeric(tpe) || (info.signature match {
          case ClassSignature(_, _, _, declarations) => !declarations.exists(_.displayName == "equals")
          case _ => false
        })))
      case _ => false
    }
    val leftname = name(left)
    val rightname = name(right)
    val broad = Set("scala/Any#", "scala/AnyRef#", "java/lang/Object#", "scala/Matchable#", "scala/Nothing#", "scala/Null#", "")
    !broad(leftname) && !broad(rightname) && leftname != rightname && !(numeric(left) && numeric(right)) &&
      !bases(left).exists(base => name(base) == rightname) && !bases(right).exists(base => name(base) == leftname) &&
      _closed_(left) && _closed_(right)
  }
}
