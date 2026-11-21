package org.simplemodeling.scalalint

import scala.meta._
import scalafix.v1._

/** Shared Goldenport convention analysis; exceptions use ordinary scoped Scalafix suppression.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
private[scalalint] object ConventionTrees {
  def overridden(mods: List[Mod]): Boolean = mods.exists(_.isInstanceOf[Mod.Override])
  def privateMember(mods: List[Mod]): Boolean = mods.exists(_.isInstanceOf[Mod.Private])
  def internal(tree: Tree): Boolean = {
    val current = tree match {
      case member: Stat.WithMods => privateMember(member.mods)
      case _ => false
    }
    current || tree.parent.exists(internal)
  }
  def local(tree: Tree): Boolean = {
    def _within_(current: Option[Tree]): Boolean = current match {
      case Some(_: Template) | Some(_: Pkg) | None => false
      case Some(_: Defn.Def) | Some(_: Defn.Macro) | Some(_: Term.Block) | Some(_: Term.Function) => true
      case Some(parent) => _within_(parent.parent)
    }
    _within_(tree.parent)
  }
  def names(pats: List[Pat]): List[Term.Name] =
    pats.flatMap(_.collect { case Pat.Var(name) => name })
  def check(name: Name, pattern: String, message: String): Patch =
    if (name.value.isEmpty || name.value == "_" || name.value.matches(pattern)) Patch.empty
    else Patch.lint(Diagnostic("", message, name.pos))
  val snake = "[a-z][a-z0-9]*(?:_[a-z0-9]+)*"
  val flat = "[a-z][a-z0-9]*"
  val camel = "[a-z][a-zA-Z0-9]*"
  val upper = "[A-Z][a-zA-Z0-9]*"
}
