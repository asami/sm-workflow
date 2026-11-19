package fixture

/** Handwritten Test input with an intentional expected partial-access violation.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
object ExampleTestSource {
  def unsafe(value: Option[String]): String = value.get // expected: NoOptionGet in handwritten Test
  def total(value: Option[String]): String = value.getOrElse("missing")
  def failed(value: scala.util.Try[String]): String = value.get
  def projected(value: Either[String, Int]): String = value.left.get
  def first(values: List[Int]): Int = values.head
  def unfinished: String = scala.Predef.???
  def embeddedKey: javax.crypto.spec.SecretKeySpec = new javax.crypto.spec.SecretKeySpec("fixture-only-dummy".getBytes(java.nio.charset.StandardCharsets.UTF_8), "AES")
  def discard(value: Either[String, Int]): Unit = { value; () }
  def same(left: Array[Int], right: Array[Int]): Boolean = left == right
  private def _testHelper(value: Int): Int = value
}
