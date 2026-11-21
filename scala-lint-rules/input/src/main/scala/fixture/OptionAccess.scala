/*
rules = [NoOptionGet]
*/
package fixture

/** Compiler input with intentional assertion-marked partial accesses.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
object OptionAccess {
  final case class Evidence(workerIdentity: Option[String])
  def worker(value: Evidence): String = value.workerIdentity.get // assert: NoOptionGet
  def chained(value: Either[String, String]): String = value.toOption.get // assert: NoOptionGet
  def direct(value: Option[Int]): Int = value.get // assert: NoOptionGet
  def concreteSome: Int = Some(1).get // assert: NoOptionGet
  def lookup(values: Map[String, Int]): Option[Int] = values.get("key")
  final class Other { def get: Int = 1 }
  def unrelated(value: Other): Int = value.get
  def total(value: Option[Int]): Int = value.fold(0)(identity)
}
