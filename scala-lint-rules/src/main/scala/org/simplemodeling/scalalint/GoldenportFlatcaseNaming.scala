package org.simplemodeling.scalalint

import scala.meta._
import scalafix.v1._

/** Shared Goldenport convention analysis; exceptions use ordinary scoped Scalafix suppression.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
final class GoldenportFlatcaseNaming extends SyntacticRule("GoldenportFlatcaseNaming") {
  private def _check(name: Name): Patch =
    ConventionTrees.check(name, ConventionTrees.flat, "Private parameters, ordinary locals and internal case fields use flatcase")
  private def _params(params: List[List[Term.Param]]): Patch =
    Patch.fromIterable(params.flatten.filterNot(p => ConventionTrees.overridden(p.mods)).map(p => _check(p.name)))

  override def fix(implicit doc: SyntacticDocument): Patch = Patch.fromIterable(doc.tree.collect {
    case definition: Defn.Def if !ConventionTrees.overridden(definition.mods) &&
        (ConventionTrees.privateMember(definition.mods) || ConventionTrees.local(definition)) => _params(definition.paramss)
    case definition: Decl.Def if !ConventionTrees.overridden(definition.mods) && ConventionTrees.privateMember(definition.mods) =>
      _params(definition.paramss)
    case definition: Defn.Val if ConventionTrees.local(definition) =>
      Patch.fromIterable(ConventionTrees.names(definition.pats).map(_check))
    case definition: Defn.Var if ConventionTrees.local(definition) =>
      Patch.fromIterable(ConventionTrees.names(definition.pats).map(_check))
    case branch: Case => Patch.fromIterable(ConventionTrees.names(List(branch.pat)).map(_check))
    case binding: Enumerator.Generator => Patch.fromIterable(ConventionTrees.names(List(binding.pat)).map(_check))
    case binding: Enumerator.Val => Patch.fromIterable(ConventionTrees.names(List(binding.pat)).map(_check))
    case function: Term.Function => Patch.fromIterable(function.params.map(p => _check(p.name)))
    case definition: Defn.Class if definition.mods.exists(_.isInstanceOf[Mod.Case]) =>
      // Serialization/schema/API contracts cannot be proven syntactically: use a justified scoped suppression.
      val params = if (ConventionTrees.internal(definition)) definition.ctor.paramss
        else definition.ctor.paramss.map(_.filter(p => ConventionTrees.privateMember(p.mods)))
      _params(params)
  })
}

/** Shared Goldenport convention analysis; exceptions use ordinary scoped Scalafix suppression.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
final class GoldenportVisibilityNaming extends SyntacticRule("GoldenportVisibilityNaming") {
  private def _term(name: Name, mods: List[Mod]): Patch = {
    if (ConventionTrees.privateMember(mods) || ConventionTrees.overridden(mods)) Patch.empty
    else {
      // Protected override roles permit xxx_Yyy hooks as prescribed in RULE.md.
      val pattern = if (mods.exists(_.isInstanceOf[Mod.Protected]))
        ConventionTrees.snake + "(?:_[A-Z][a-zA-Z0-9]*)?" else ConventionTrees.camel
      ConventionTrees.check(name, pattern, "Public members use camelCase; protected members use snake_case or the documented override-hook form")
    }
  }
  private def _params(params: List[List[Term.Param]], mods: List[Mod]): Patch =
    if (ConventionTrees.privateMember(mods) || ConventionTrees.overridden(mods)) Patch.empty
    else Patch.fromIterable(params.flatten.map(p => ConventionTrees.check(p.name, ConventionTrees.camel, "Public/protected parameters use camelCase")))
  private def _type(name: Name): Patch =
    ConventionTrees.check(name, ConventionTrees.upper, "Structural/type declarations use UpperCamelCase, at every visibility")

  override def fix(implicit doc: SyntacticDocument): Patch = Patch.fromIterable(doc.tree.collect {
    case definition: Defn.Def if !ConventionTrees.local(definition) =>
      _term(definition.name, definition.mods) + _params(definition.paramss, definition.mods)
    case definition: Decl.Def => _term(definition.name, definition.mods) + _params(definition.paramss, definition.mods)
    case definition: Defn.Val if !ConventionTrees.local(definition) =>
      Patch.fromIterable(ConventionTrees.names(definition.pats).map(n => _term(n, definition.mods)))
    case definition: Defn.Var if !ConventionTrees.local(definition) =>
      Patch.fromIterable(ConventionTrees.names(definition.pats).map(n => _term(n, definition.mods)))
    case definition: Decl.Val => Patch.fromIterable(ConventionTrees.names(definition.pats).map(n => _term(n, definition.mods)))
    case definition: Decl.Var => Patch.fromIterable(ConventionTrees.names(definition.pats).map(n => _term(n, definition.mods)))
    case definition: Defn.Class => _type(definition.name)
    case definition: Defn.Trait => _type(definition.name)
    case definition: Defn.Object => _type(definition.name)
    case definition: Defn.Enum => _type(definition.name)
    case definition: Defn.Type if !ConventionTrees.overridden(definition.mods) => _type(definition.name)
    case definition: Decl.Type if !ConventionTrees.overridden(definition.mods) => _type(definition.name)
  })
}
