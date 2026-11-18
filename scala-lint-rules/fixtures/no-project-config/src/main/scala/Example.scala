package fixture

/** No-project-config input with intentional expected naming/partial-access violations.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
object Example {
  private def _wrapCommand(value: String): String = value // expected: GoldenportPrivateMemberNaming
  private def _wrap_command(value: String): String = value
  def unsafe(value: Option[String]): String = value.get // expected: NoOptionGet
  def chained(value: Either[String, String]): String = value.toOption.get // expected: NoOptionGet
  def total(value: Option[String]): String = value.fold("missing")(identity)
  def lookup(values: Map[String, String]): Option[String] = values.get("key")
  final class Other { def get: String = "unrelated" }
  def unrelated(value: Other): String = value.get
}

final class ConsumerFinalizer {
  override def finalize(): Unit = () // expected: DisableSyntax.noFinalize
}
