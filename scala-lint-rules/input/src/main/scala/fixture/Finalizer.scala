/*
rules = [DisableSyntax]
DisableSyntax.noFinalize = true
*/
package fixture

/** Intentional finalizer violation for baseline retention qualification.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
final class Finalizer {
  override def finalize(): Unit = () // assert: DisableSyntax.noFinalize
}
