package fixture

/** Compiled qualification input; methods are never executed.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
object ConsumerScopedExceptions {
  // REASON: this fixture models an Option precondition guaranteed by its caller.
  @SuppressWarnings(Array("scalafix:NoOptionGet"))
  def scoped(option: Option[Int], attempted: scala.util.Try[Int]): (Int, Int) = (option.get, attempted.get)
  def outside(option: Option[Int]): Int = option.get
  def failed(attempted: scala.util.Try[Int]): Int = attempted.get
  def total(option: Option[Int]): Int = option.getOrElse(0)
  def configuredVariable(value: Int): Int = {
    var current = value
    current += 1
    current
  }
  private def _projectHelper(value: Int): Int = value
}

final class ConsumerConfiguredFinalizer {
  override def finalize(): Unit = ()
}
