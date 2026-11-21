/*
rules = [NoImplicitArrayEqualityInCollections]
*/
package fixture

/** Intentional compiler-qualified positive and negative rule examples.
  * @since Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author ASAMI, Tomoharu
  */
object EqualityCollectionArrays {
  def membership(values: List[Array[Int]], key: Array[Int]): Boolean = values.contains(key) // assert: NoImplicitArrayEqualityInCollections
  def distinct(values: List[Array[Int]]): List[Array[Int]] = values.distinct // assert: NoImplicitArrayEqualityInCollections
  def distinctBy(values: List[Array[Int]]): List[Array[Int]] = values.distinctBy(value => value) // assert: NoImplicitArrayEqualityInCollections
  def grouping(values: List[Array[Int]]): Map[Array[Int], List[Array[Int]]] = values.groupBy(value => value) // assert: NoImplicitArrayEqualityInCollections
  def groupMap(values: List[Array[Int]]): Map[Array[Int], List[Int]] = values.groupMap(value => value)(_.length) // assert: NoImplicitArrayEqualityInCollections
  def groupReduce(values: List[Array[Int]]): Map[Array[Int], Int] = values.groupMapReduce(value => value)(_ => 1)(_ + _) // assert: NoImplicitArrayEqualityInCollections
  def toSet(values: List[Array[Int]]): Set[Array[Int]] = values.toSet // assert: NoImplicitArrayEqualityInCollections
  def toMap(values: List[(Array[Int], Int)]): Map[Array[Int], Int] = values.toMap // assert: NoImplicitArrayEqualityInCollections
  def mapLookup(values: Map[Array[Int], Int], key: Array[Int]): Option[Int] = values.get(key) // assert: NoImplicitArrayEqualityInCollections
  def setLookup(values: Set[Array[Int]], key: Array[Int]): Boolean = values.contains(key) // assert: NoImplicitArrayEqualityInCollections
  def projectedValue(values: List[Array[Int]]): List[Array[Int]] = values.distinctBy(_.toVector)
  def valueElements(values: List[Vector[Int]], key: Vector[Int]): Boolean = values.contains(key)
  def explicitIdentity(values: List[Array[Int]], key: Array[Int]): Boolean = values.exists(_.eq(key))
}
