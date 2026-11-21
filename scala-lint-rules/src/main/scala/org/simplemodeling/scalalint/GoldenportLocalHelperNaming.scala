package org.simplemodeling.scalalint

import scala.meta._
import scalafix.v1._

/** Shared Goldenport convention analysis; exceptions use ordinary scoped Scalafix suppression.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
final class GoldenportLocalHelperNaming extends SyntacticRule("GoldenportLocalHelperNaming") {
  override def fix(implicit doc: SyntacticDocument): Patch = Patch.fromIterable(doc.tree.collect {
    case definition: Defn.Def if ConventionTrees.local(definition) =>
      ConventionTrees.check(definition.name, "_" + ConventionTrees.snake + "_", "Method-local helpers use _snake_case_")
  })
}
