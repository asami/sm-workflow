package org.simplemodeling.scalalint

import scalafix.v1._

/** Generic Either/Try result handling; ordinary mutation results remain valid.
  * @since Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author ASAMI, Tomoharu
  */
final class NoDiscardedFailureResult extends SemanticRule("NoDiscardedFailureResult") {
  override def fix(implicit doc: SemanticDocument): Patch = SafetyDiscarded.patches(Set("scala/util/Either#", "scala/util/Try#"))
}
