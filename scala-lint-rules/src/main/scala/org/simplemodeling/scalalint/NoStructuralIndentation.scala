package org.simplemodeling.scalalint

import scala.meta._
import scalafix.v1._
import metaconfig.{Conf, ConfError, Configured}

/** Structural regions use source braces; terminal computation may use indentation.
  * DSL contracts are exact SemanticDB symbols, never unqualified method names.
  * structuralDslSymbols declares hierarchy builders; terminalDslSymbols declares
  * terminal operations within dslOwnerPrefixes. Unknown relevant owners report
  * AnalysisUnavailable rather than being classified as terminal computation.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
final class NoStructuralIndentation(
  private val _structural_symbols: List[String],
  private val _terminal_symbols: List[String],
  private val _owner_prefixes: List[String]
) extends SemanticRule("NoStructuralIndentation") {
  def this() = this(Nil, Nil, Nil)

  override def withConfiguration(config: Configuration): Configured[Rule] =
    // getOrElse's additional keys are aliases, not a nested configuration path.
    config.conf.getOrElse[Conf]("NoStructuralIndentation")(Conf.Obj(Nil)).andThen {
      case settings: Conf.Obj =>
        settings.getOrElse[List[String]]("structuralDslSymbols")(_structural_symbols).andThen { structural =>
          settings.getOrElse[List[String]]("terminalDslSymbols")(_terminal_symbols).andThen { terminal =>
            settings.getOrElse[List[String]]("dslOwnerPrefixes")(_owner_prefixes).map { owners =>
              new NoStructuralIndentation(structural, terminal, owners)
            }
          }
        }
      case other => ConfError.typeMismatch("NoStructuralIndentation object", other).notOk
    }

  private def _tokens(tree: Tree): List[Token] =
    tree.tokens.filterNot(t => t.isInstanceOf[Token.Comment] || t.text.forall(_.isWhitespace)).toList
  private def _braced(tree: Tree): Boolean = _tokens(tree).headOption.exists(_.isInstanceOf[Token.LeftBrace])
  private def _report(tree: Tree): Patch =
    Patch.lint(Diagnostic("", "Structural regions require explicit braces; an end marker does not replace braces", tree.pos))
  private def _callee(term: Term): Term = term match {
    case application: Term.Apply => _callee(application.fun)
    case application: Term.ApplyType => _callee(application.fun)
    case select: Term.Select => select.name
    case other => other
  }
  private def _structural_call(term: Term)(implicit doc: SemanticDocument): Boolean = {
    val symbol = _callee(term).symbol
    !symbol.isNone && _structural_symbols.exists(s => SymbolMatcher.normalized(s).matches(_callee(term)))
  }
  private def _contains_structure(tree: Tree)(implicit doc: SemanticDocument): Boolean = tree.collect {
    case _: Template => true
    case _: Defn.Class | _: Defn.Trait | _: Defn.Object | _: Defn.Enum | _: Defn.Given | _: Defn.ExtensionGroup => true
    case application: Term.Apply if _structural_call(application.fun) => true
  }.nonEmpty

  private def _template(template: Template): Patch = {
    // Exclude parent constructor/type arguments: their braces/colons are not body delimiters.
    val parentend = (template.inits.map(_.pos.end) ++ template.derives.map(_.pos.end)).foldLeft(template.pos.start)(math.max)
    val bodytokens = _tokens(template).filter(_.pos.start >= parentend)
    val firststat = (template.stats.map(_.pos.start) ++
      (if (template.self.pos.text.nonEmpty) List(template.self.pos.start) else Nil)).sorted.headOption
    val prefix = bodytokens.takeWhile(t => firststat.forall(t.pos.start < _))
    val delimiter = prefix.find(t => t.isInstanceOf[Token.LeftBrace] || t.isInstanceOf[Token.Colon])
    if (delimiter.exists(_.isInstanceOf[Token.LeftBrace])) Patch.empty
    else if (delimiter.nonEmpty || template.stats.nonEmpty || template.self.pos.text.nonEmpty) _report(template)
    else Patch.empty // Bodyless declarations and aliases have no structural region.
  }

  private def _dsl(application: Term.Apply)(implicit doc: SemanticDocument): Patch = {
    val callee = _callee(application.fun)
    val symbol = callee.symbol
    val regions = application.args.collect {
      case function: Term.Function => function
      case block: Term.Block => block
    }
    val indented = regions.filterNot(_braced)
    if (indented.isEmpty) Patch.empty
    else if (_structural_call(application.fun)) Patch.fromIterable(indented.map(_report))
    else if (symbol.isNone ||
      (_owner_prefixes.exists(symbol.value.startsWith) &&
        !_terminal_symbols.exists(s => SymbolMatcher.normalized(s).matches(callee)))) {
      Patch.lint(Diagnostic("AnalysisUnavailable", "Cannot classify this DSL region; supply the qualified structural/terminal DSL contract and required semantic analysis", callee.pos))
    } else Patch.empty
  }

  override def fix(implicit doc: SemanticDocument): Patch = Patch.fromIterable(doc.tree.collect {
    case template: Template => _template(template)
    case pack: Pkg =>
      val boundary = doc.tokens.filter(t => t.pos.start >= pack.ref.pos.end &&
        t.pos.end <= pack.stats.headOption.map(_.pos.start).getOrElse(pack.pos.end))
      if (boundary.exists(_.isInstanceOf[Token.Colon])) _report(pack) else Patch.empty
    case extension: Defn.ExtensionGroup if !_braced(extension.body) => _report(extension.body)
    case block: Term.Block if !_braced(block) &&
        !block.parent.exists(p => p.isInstanceOf[Term.Function] && _braced(p)) &&
        _contains_structure(block) => _report(block)
    case definition: Defn.Def if !definition.body.isInstanceOf[Term.Block] &&
        !_braced(definition.body) && _contains_structure(definition.body) => _report(definition.body)
    case application: Term.Apply => _dsl(application)
  })
}
