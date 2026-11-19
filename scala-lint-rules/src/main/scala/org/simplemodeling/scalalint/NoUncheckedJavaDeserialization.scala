package org.simplemodeling.scalalint

import scala.meta._
import scalafix.v1._

/** Bounded JDK security analysis; never includes source values in diagnostics.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
final class NoUncheckedJavaDeserialization extends SemanticRule("NoUncheckedJavaDeserialization") {
  override def fix(implicit doc: SemanticDocument): Patch = {
    val flow = new SecurityFlow
    val methods = Set("readObject", "readUnshared")
    Patch.fromIterable(doc.tree.collect {
      case name: Term.Name if flow.api(name, "java/io/ObjectInputStream#", methods) =>
        flow.diagnostic(name, "Java object deserialization requires an explicitly reviewed boundary and scoped exception; a filter alone does not establish safety")
      case name: Term.Name if methods(name.value) && name.symbol.isNone => flow.missing(name)
    })
  }
}
