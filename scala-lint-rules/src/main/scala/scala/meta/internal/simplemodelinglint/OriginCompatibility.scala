package scala.meta.internal.simplemodelinglint

import scala.meta.Term
import scala.meta.trees.Origin

/** Narrow parsed-origin compatibility bridge for pinned Scalameta 4.9.3.
  * @since Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author ASAMI, Tomoharu
  */
object OriginCompatibility extends scala.meta.internal.trees.InternalTreeXtensions {
  // The caller proves a sole identifier token in this original parsed input.
  // Scalameta's withOrigin conversion is private[meta], so only this adapter
  // lives in its package scope; symbol lookup stays in the semantic rule.
  def identifier(name: Term.Name, parsed: Origin.Parsed, tokenIndex: Int): Term.Name =
    name.withOrigin(parsed.copy(begTokenIdx = tokenIndex, endTokenIdx = tokenIndex + 1))
}
