/*
rules = [NoStructuralIndentation]
NoStructuralIndentation.structuralDslSymbols = ["fixture/StructuralDomain.component()."]
NoStructuralIndentation.terminalDslSymbols = ["fixture/StructuralDomain.terminal()."]
NoStructuralIndentation.dslOwnerPrefixes = ["fixture/StructuralDomain."]
*/
package fixture

/** Paired structural policy specification; marked findings are intentional.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
object StructuralDomain {
  // This fake domain contract declares a component hierarchy. Its exact qualified
  // SemanticDB symbol is configured above; its method name alone grants no role.
  final class Scope
  def component(body: Scope => Int): Int = body(new Scope)
  def terminal(body: Scope => Int): Int = body(new Scope)
  def unclassified(body: Scope => Int): Int = body(new Scope)
}
object StructuralDsl {
  val invalid = StructuralDomain.component: scope => // assert: NoStructuralIndentation
    1
  val valid = StructuralDomain.component { scope =>
    1
  }
  val terminal = StructuralDomain.terminal: scope =>
    if true then 1 else 0
  val unknown = StructuralDomain.unclassified: scope => // assert: NoStructuralIndentation.AnalysisUnavailable
    1
  // An unrelated API named component has no hierarchy contract.
  object Ordinary {
    def component(body: Int => Int): Int = body(1)
  }
  val ordinary = Ordinary.component: value =>
    value + 1
}
