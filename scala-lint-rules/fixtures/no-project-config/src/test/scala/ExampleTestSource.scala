package fixture

/** Handwritten Test input with an intentional expected partial-access violation.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
object ExampleTestSource {
  def unsafe(value: Option[String]): String = value.get // expected: NoOptionGet in handwritten Test
  def total(value: Option[String]): String = value.getOrElse("missing")
}
