package org.simplemodeling.scalalint

import scala.meta._
import scalafix.v1._

/** Shared Goldenport convention analysis; exceptions use ordinary scoped Scalafix suppression.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
final class GoldenportHistoryCommentPlacement extends SyntacticRule("GoldenportHistoryCommentPlacement") {
  private def _top(tree: Tree): Boolean = tree.parent.exists {
    case _: Source | _: Pkg => true
    case _ => false
  }

  override def fix(implicit doc: SyntacticDocument): Patch = {
    val definitions = doc.tree.collect { case definition: Defn if _top(definition) => definition }
    val comments = doc.tokens.collect { case comment: Token.Comment => comment }
    val metadata = comments.filter(c => c.text.contains("@version") || c.text.contains("@since"))
    Patch.fromIterable(metadata.map { comment =>
      // A metadata comment must be the documentation immediately attached to a top-level definition.
      val attached = comment.text.startsWith("/**") && definitions.exists { definition =>
        comment.pos.end <= definition.pos.start &&
          doc.tokens.filter(t => t.pos.start >= comment.pos.end && t.pos.end <= definition.pos.start)
            .forall(t => t.text.forall(_.isWhitespace))
      }
      if (attached) Patch.empty
      else Patch.lint(Diagnostic("", "Version metadata belongs to the relevant top-level definition documentation after package/imports", comment.pos))
    })
  }
}
