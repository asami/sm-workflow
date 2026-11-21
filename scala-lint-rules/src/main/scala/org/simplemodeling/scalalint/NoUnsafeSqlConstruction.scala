package org.simplemodeling.scalalint

import scala.meta._
import scalafix.v1._

/** Bounded JDK security analysis; never includes source values in diagnostics.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
final class NoUnsafeSqlConstruction extends SemanticRule("NoUnsafeSqlConstruction") {
  private val _methods = Set("execute", "executeQuery", "executeUpdate", "executeLargeUpdate", "addBatch", "prepareStatement", "prepareCall", "nativeSQL")
  override def fix(implicit doc: SemanticDocument): Patch = {
    val flow = new SecurityFlow
    def _dynamic_(tree: Tree): Boolean = flow.resolve(tree) match {
      case Term.ApplyInfix(left, Term.Name("+"), _, List(right)) => !flow.constant(left) || !flow.constant(right)
      case Term.Interpolate(Term.Name("s"), _, args) => args.exists(value => !flow.constant(value))
      case _ => false
    }
    def _structure_(tree: Tree): Boolean = flow.resolve(tree) match {
      case Term.If(_, left, right) => _structure_(left) && _structure_(right)
      case other => flow.constant(other)
    }
    Patch.fromIterable(flow.calls.collect {
      case call if (flow.api(call.fun, "java/sql/Statement#", _methods) || flow.api(call.fun, "java/sql/Connection#", _methods)) && call.args.nonEmpty =>
        val value = call.args.head
        if (_dynamic_(value)) flow.diagnostic(value, "Dynamic data construction at raw JDBC SQL API; use bound parameters")
        else if (_structure_(value)) Patch.empty else flow.unresolved(value)
      case call if flow.unavailable(call.fun, _methods) => flow.missing(call.fun)
    })
  }
}
