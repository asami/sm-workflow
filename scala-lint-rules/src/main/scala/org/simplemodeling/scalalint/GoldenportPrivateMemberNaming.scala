package org.simplemodeling.scalalint

import scala.meta._
import scalafix.v1._

/** SLP-1 member subset. Local helper, visibility and history rules follow in SLP-2.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
final class GoldenportPrivateMemberNaming extends SyntacticRule("GoldenportPrivateMemberNaming") {
  private def _selected(mods: List[Mod]): Boolean =
    mods.exists(_.isInstanceOf[Mod.Private]) && !mods.exists(_.isInstanceOf[Mod.Override])

  private def _check(name: Name): Patch =
    if (name.value.matches("_[a-z][a-z0-9]*(?:_[a-z0-9]+)*")) Patch.empty
    else Patch.lint(Diagnostic("", "Private members use _snake_case", name.pos))

  override def fix(implicit doc: SyntacticDocument): Patch = Patch.fromIterable(doc.tree.collect {
    case definition: Defn.Def if _selected(definition.mods) => _check(definition.name)
    case definition: Defn.Val if _selected(definition.mods) =>
      Patch.fromIterable(definition.pats.collect { case Pat.Var(name) => _check(name) })
    case definition: Defn.Var if _selected(definition.mods) =>
      Patch.fromIterable(definition.pats.collect { case Pat.Var(name) => _check(name) })
  })
}
