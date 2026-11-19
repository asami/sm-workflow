/*
rules = [NoUnimplementedPlaceholder]
*/
package fixture

import scala.Predef.{??? => missing}

/** Intentional compiler-qualified positive and negative rule examples.
  * @since Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author ASAMI, Tomoharu
  */
object SafetyPlaceholder {
  def unfinished: Int = scala.Predef.??? // assert: NoUnimplementedPlaceholder
  def alias: Int = missing // assert: NoUnimplementedPlaceholder
  val example: String = "scala.Predef.???"
  // scala.Predef.??? is documentation here.
  object Other { def ??? : Int = 7 }
  def unrelated: Int = Other.???
}
