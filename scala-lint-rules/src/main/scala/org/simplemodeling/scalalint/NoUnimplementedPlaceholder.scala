package org.simplemodeling.scalalint

import scala.meta._
import scalafix.v1._

/** Resolved semantic safety check.
  * @since Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author ASAMI, Tomoharu
  */
final class NoUnimplementedPlaceholder extends SemanticRule("NoUnimplementedPlaceholder") {
  override def fix(implicit doc: SemanticDocument): Patch = Patch.fromIterable(doc.tree.collect {
    case name: Term.Name if !name.parent.exists(_.isInstanceOf[Importee]) && name.symbol.info.exists(info =>
      info.owner.value == "scala/Predef." && info.displayName == "???") =>
      SafetySemantic.finding(name, "Executable Predef.??? must be implemented")
    case name: Term.Name if name.value == "???" && name.symbol.info.isEmpty => SafetySemantic.unresolved(name, "placeholder")
  })
}
