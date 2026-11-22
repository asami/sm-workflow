package org.simplemodeling.scalalint

import scala.meta._
import scalafix.lint.LintSeverity
import scalafix.v1._

/** Bounded verification of declared nullable method-parameter boundaries.
  * Human review remains responsible for whether the declared boundary is real.
  */
final class NullableBoundarySemantics extends SemanticRule("NullableBoundarySemantics") {
  private val _objects_non_null = SymbolMatcher.normalized("java/util/Objects.nonNull().")
  private val _prefix = "// nullable-boundary:"
  private val _marker = ("// nullable-boundary: rule=NullableBoundarySemantics target=([A-Za-z_][A-Za-z0-9_]*)" +
    " boundary=interop normalization=reject-null justification=([^\\s]+)").r

  private def _finding(category: String, message: String, position: Position): Patch =
    Patch.lint(Diagnostic(category, message, position, severity = LintSeverity.Warning))

  private def _guard(term: Term)(implicit doc: SemanticDocument): Option[(Term, Boolean, Boolean)] = term match {
    case Term.ApplyInfix(name: Term, Term.Name("=="), _, List(Lit.Null())) => Some((name, true, true))
    case Term.ApplyInfix(name: Term, Term.Name("!="), _, List(Lit.Null())) => Some((name, false, true))
    case Term.ApplyInfix(Lit.Null(), Term.Name("=="), _, List(name: Term)) => Some((name, true, true))
    case Term.ApplyInfix(Lit.Null(), Term.Name("!="), _, List(name: Term)) => Some((name, false, true))
    case Term.Apply(Term.Select(_, name), List(value: Term)) if _objects_non_null.matches(name) =>
      Some((value, false, true))
    case Term.Apply(name: Term.Name, List(value: Term)) if _objects_non_null.matches(name) =>
      Some((value, false, true))
    case _ => None
  }

  private def _rejects(term: Term): Boolean = term match {
    case _: Term.Throw => true
    case Term.Block(List(_: Term.Throw)) => true
    case _ => false
  }

  override def fix(implicit doc: SemanticDocument): Patch = {
    val markers = doc.tokens.collect { case comment: Token.Comment if comment.text.startsWith(_prefix) => comment }.toList
    val methods = doc.tree.collect { case method: Defn.Def => method }
    val allGuards = doc.tree.collect { case term: Term if _guard(term).nonEmpty => term }
    def owner(guard: Term): Option[Defn.Def] = methods.filter { method =>
      method.body.pos.start <= guard.pos.start && method.body.pos.end >= guard.pos.end
    }.sortBy(method => method.body.pos.end - method.body.pos.start).headOption
    val byMethod = methods.map { method =>
      method -> markers.filter(_.pos.endLine + 1 == method.pos.startLine)
    }.toMap
    val used = byMethod.values.flatten.toSet
    val orphaned = markers.filterNot(used).map(m => _finding("Misplaced", "Nullable boundary marker must immediately precede its method", m.pos))
    val outsideMethods = allGuards.filter(guard => owner(guard).isEmpty).map { guard =>
      _finding("UnresolvedBoundary", "Null guard needs a qualified nullable parameter, immediate boundary marker and explicit null rejection; review the contract", guard.pos)
    }
    val findings = methods.flatMap { method =>
      val attached = byMethod(method)
      val guards = allGuards.filter(guard => owner(guard).contains(method))
      val branches = method.body.collect { case branch: Term.If => branch }
      val parsed = attached match {
        case List(comment) => comment.text match {
          case _marker(target, reason) if reason.nonEmpty => Some(target)
          case _ => None
        }
        case _ => None
      }
      val invalid = if (attached.nonEmpty && parsed.isEmpty)
        attached.map(m => _finding("InvalidMarker", "Expected one complete nullable-boundary marker", m.pos))
      else Nil
      val stale = if (parsed.nonEmpty && guards.isEmpty)
        attached.map(m => _finding("StaleMarker", "Nullable boundary marker has no supported null guard", m.pos))
      else Nil
      val guardFindings = guards.flatMap { guard =>
        val (receiver, nullWhenTrue, resolvedPredicate) = _guard(guard).get
        val branch = branches.find(_.cond eq guard)
        val name = receiver match {
          case value: Term.Name => Some(value)
          case _ => None
        }
        val parameter = name.flatMap(value => method.paramss.flatten.find(p =>
          !p.name.symbol.isNone && p.name.symbol == value.symbol))
        val nullable = parameter.flatMap(_.decltpe).exists(_.syntax.matches("[A-Za-z_][A-Za-z0-9_.]*\\s*\\|\\s*(scala\\.)?Null"))
        val reject = branch.exists(b => if (nullWhenTrue) _rejects(b.thenp) else _rejects(b.elsep))
        val accepted = resolvedPredicate && name.exists(value => parsed.contains(value.value)) && nullable && reject &&
          branch.exists(b => method.body eq b) && guards.size == 1
        if (accepted) List(Patch.lint(Diagnostic("VerifiedBoundary",
          "Declared nullable parameter has a supported null-rejection shape; boundary and rationale require human judgment",
          guard.pos, severity = LintSeverity.Info))) else List(_finding("UnresolvedBoundary",
          "Null guard needs a qualified nullable parameter, immediate boundary marker and explicit null rejection; review the contract", guard.pos))
      }
      invalid ++ stale ++ guardFindings
    }
    Patch.fromIterable(orphaned ++ outsideMethods ++ findings)
  }
}

/** Detect ordinary rule-specific suppressions independently of the semantic rule.
  * Global engine disablement still requires admission to establish lint coverage.
  */
final class NullableBoundarySuppression extends SyntacticRule("NullableBoundarySuppression") {
  override def fix(implicit doc: SyntacticDocument): Patch = {
    val annotations = doc.tree.collect {
      case annotation: Mod.Annot if annotation.syntax.contains("scalafix:NullableBoundarySemantics") => annotation.pos
    }
    val directives = doc.tokens.collect {
      case comment: Token.Comment if comment.text.contains("scalafix:off") &&
          comment.text.contains("NullableBoundarySemantics") => comment.pos
    }
    Patch.fromIterable((annotations ++ directives).map { position =>
      Patch.lint(Diagnostic("SuppressionOnly", "A suppression cannot establish a nullable boundary contract", position,
        severity = LintSeverity.Warning))
    })
  }
}
