/*
rules = [NoIncompatibleEquality]
*/
package fixture

/** Intentional compiler-qualified positive and negative rule examples.
  * @since Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author ASAMI, Tomoharu
  */
object EqualityMembership {
  final case class CustomerId(value: String)
  final case class OrderId(value: String)
  def contains(values: List[CustomerId], wrong: OrderId): Boolean = values.contains(wrong) // assert: NoIncompatibleEquality
  def index(values: Vector[CustomerId], wrong: OrderId): Int = values.indexOf(wrong) // assert: NoIncompatibleEquality
  def lastIndex(values: Vector[CustomerId], wrong: OrderId): Int = values.lastIndexOf(wrong) // assert: NoIncompatibleEquality
  def difference(values: List[CustomerId], wrong: List[OrderId]): List[CustomerId] = values.diff(wrong) // assert: NoIncompatibleEquality
  def intersection(values: List[CustomerId], wrong: List[OrderId]): List[CustomerId] = values.intersect(wrong) // assert: NoIncompatibleEquality
  def slices(values: List[CustomerId], wrong: List[OrderId]): Boolean = values.containsSlice(wrong) // assert: NoIncompatibleEquality
  def same(values: List[CustomerId], wrong: List[OrderId]): Boolean = values.sameElements(wrong) // assert: NoIncompatibleEquality
  def predicate(values: List[CustomerId], wrong: OrderId): Boolean = values.exists(value => value == wrong) // assert: NoIncompatibleEquality
  def find(values: List[CustomerId], wrong: OrderId): Option[CustomerId] = values.find(value => value == wrong) // assert: NoIncompatibleEquality
  def filter(values: List[CustomerId], wrong: OrderId): List[CustomerId] = values.filter(value => value == wrong) // assert: NoIncompatibleEquality
  def filterNot(values: List[CustomerId], wrong: OrderId): List[CustomerId] = values.filterNot(value => value == wrong) // assert: NoIncompatibleEquality
  def forall(values: List[CustomerId], wrong: OrderId): Boolean = values.forall(value => value == wrong) // assert: NoIncompatibleEquality
  def count(values: List[CustomerId], wrong: OrderId): Int = values.count(value => value == wrong) // assert: NoIncompatibleEquality
  def indexWhere(values: List[CustomerId], wrong: OrderId): Int = values.indexWhere(value => value == wrong) // assert: NoIncompatibleEquality
  def alias(values: List[CustomerId], wrong: OrderId): Boolean = {
    val test: CustomerId => Boolean = value => value == wrong // assert: NoIncompatibleEquality
    values.exists(test)
  }
  def map(values: Map[CustomerId, Int], key: CustomerId): Option[Int] = values.get(key)
  def numeric(values: List[Int], value: Long): Boolean = values.contains(value)
  def heterogeneous(values: List[Any], value: OrderId): Boolean = values.contains(value)
  def compatible(values: Set[CustomerId], value: CustomerId): Boolean = values.contains(value)
  final class Other { def contains(value: OrderId): Boolean = true }
  def unrelated(value: Other, key: OrderId): Boolean = value.contains(key)
}
