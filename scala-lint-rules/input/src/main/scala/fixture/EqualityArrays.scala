/*
rules = [NoArrayValueEquality]
*/
package fixture

/** Intentional compiler-qualified positive and negative rule examples.
  * @since Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author ASAMI, Tomoharu
  */
object EqualityArrays {
  def equal(left: Array[Int], right: Array[Int]): Boolean = left == right // assert: NoArrayValueEquality
  def unequal(left: Array[Int], right: Array[Int]): Boolean = left != right // assert: NoArrayValueEquality
  def equalsCall(left: Array[Int], right: Array[Int]): Boolean = left.equals(right) // assert: NoArrayValueEquality
  def reverse(left: Any, right: Array[Int]): Boolean = left == right // assert: NoArrayValueEquality
  def explicitIdentity(left: Array[Int], right: Array[Int]): Boolean = left.eq(right)
  def elements(left: Array[Int], right: Array[Int]): Boolean = left.sameElements(right)
  def nested(left: Array[Array[Int]], right: Array[Array[Int]]): Boolean = left.sameElements(right) // assert: NoArrayValueEquality
  def nestedElements(left: Array[Array[Int]], right: Array[Array[Int]]): Boolean = left.length == right.length && left.zip(right).forall { case (a, b) => a.sameElements(b) }
  def ordinary(left: List[Int], right: List[Int]): Boolean = left == right
}
