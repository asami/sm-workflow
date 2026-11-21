package org.simplemodeling.scalalint

import scala.meta._
import scalafix.v1._

/** Shared Goldenport convention analysis; exceptions use ordinary scoped Scalafix suppression.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
final class GoldenportPrivateMemberNaming extends SyntacticRule("GoldenportPrivateMemberNaming") {
  private def _selected(mods: List[Mod]): Boolean =
    ConventionTrees.privateMember(mods) && !ConventionTrees.overridden(mods)
  private def _check(name: Name): Patch =
    ConventionTrees.check(name, "_" + ConventionTrees.snake, "Private members use _snake_case")

  private def _case_field(tree: Tree): Boolean = tree.parent match {
    case Some(definition: Defn.Class) => definition.mods.exists(_.isInstanceOf[Mod.Case])
    case Some(_: Template) | None => false
    case Some(parent) => _case_field(parent)
  }

  override def fix(implicit doc: SyntacticDocument): Patch = Patch.fromIterable(doc.tree.collect {
    case parameter: Term.Param if _selected(parameter.mods) && !_case_field(parameter) &&
        parameter.mods.exists(m => m.isInstanceOf[Mod.ValParam] || m.isInstanceOf[Mod.VarParam]) => _check(parameter.name)
    case definition: Defn.Def if _selected(definition.mods) && !ConventionTrees.local(definition) => _check(definition.name)
    case definition: Decl.Def if _selected(definition.mods) => _check(definition.name)
    case definition: Defn.Val if _selected(definition.mods) && !ConventionTrees.local(definition) =>
      Patch.fromIterable(ConventionTrees.names(definition.pats).map(_check))
    case definition: Defn.Var if _selected(definition.mods) && !ConventionTrees.local(definition) =>
      Patch.fromIterable(ConventionTrees.names(definition.pats).map(_check))
    case definition: Decl.Val if _selected(definition.mods) =>
      Patch.fromIterable(ConventionTrees.names(definition.pats).map(_check))
    case definition: Decl.Var if _selected(definition.mods) =>
      Patch.fromIterable(ConventionTrees.names(definition.pats).map(_check))
  })
}
