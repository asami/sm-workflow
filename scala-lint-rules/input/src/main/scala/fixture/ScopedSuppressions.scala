/*
rules = [NoOptionGet, NoTryGet]
*/
package fixture

/** Standard Scalafix suppression is specific to one rule and one definition.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
object ScopedSuppressions {
  // REASON: this fixture models an Option precondition guaranteed by its caller.
  @SuppressWarnings(Array("scalafix:NoOptionGet"))
  def scoped(option: Option[Int], attempted: scala.util.Try[Int]): (Int, Int) = (option.get, attempted.get) // assert: NoTryGet
  def outside(option: Option[Int]): Int = option.get // assert: NoOptionGet
  def failed(attempted: scala.util.Try[Int]): Int = attempted.get // assert: NoTryGet
  def total(option: Option[Int]): Int = option.getOrElse(0)
}
