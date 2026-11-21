/*
rules = [NoOptionGet]
*/
package unavailable

/** Intentional partial access in source lacking a compiler SemanticDB document.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
object NoAnalysis {
  def unsafe(value: Option[Int]): Int = value.get
}
