/*
rules = [NoTryGet, NoEitherProjectionGet, NoEmptySensitiveCollectionOps]
*/
package fixture

import scala.util.Try
import scala.collection.mutable

/** Intentional compiler-qualified positive and negative rule examples.
  * @since Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author ASAMI, Tomoharu
  */
object SafetyPartial {
  def attempted(value: Try[Int]): Int = value.get // assert: NoTryGet
  def success: Int = scala.util.Success(1).get // assert: NoTryGet
  def left(value: Either[String, Int]): String = value.left.get // assert: NoEitherProjectionGet
  def right(value: Either[String, Int]): Int = value.right.get // assert: NoEitherProjectionGet
  def total(value: Try[Int]): Int = value.fold(_ => 0, identity)
  def folded(value: Either[String, Int]): Int = value.fold(_.length, identity)
  def mapLookup(value: Map[String, Int]): Option[Int] = value.get("key")
  final class NonEmpty {
    def head: Int = 1
    def last: Int = 1
    def tail: List[Int] = Nil
    def get: Int = 1
  }
  def guaranteed(value: NonEmpty): Int = value.head + value.last + value.get
  def immutableHead(value: List[Int]): Int = value.head // assert: NoEmptySensitiveCollectionOps
  def mutableLast(value: mutable.Buffer[Int]): Int = value.last // assert: NoEmptySensitiveCollectionOps
  def inheritedTail(value: Vector[Int]): Vector[Int] = value.tail // assert: NoEmptySensitiveCollectionOps
  def initial(value: Seq[Int]): Seq[Int] = value.init // assert: NoEmptySensitiveCollectionOps
  def reduced(value: List[Int]): Int = value.reduce(_ + _) // assert: NoEmptySensitiveCollectionOps
  def reducedLeft(value: List[Int]): Int = value.reduceLeft(_ + _) // assert: NoEmptySensitiveCollectionOps
  def reducedRight(value: List[Int]): Int = value.reduceRight(_ + _) // assert: NoEmptySensitiveCollectionOps
  def minimum(value: List[Int]): Int = value.min // assert: NoEmptySensitiveCollectionOps
  def maximum(value: mutable.Buffer[Int]): Int = value.max // assert: NoEmptySensitiveCollectionOps
  def minimumBy(value: List[String]): String = value.minBy(_.length) // assert: NoEmptySensitiveCollectionOps
  def maximumBy(value: List[String]): String = value.maxBy(_.length) // assert: NoEmptySensitiveCollectionOps
  def optional(value: List[Int]): Option[Int] = value.headOption
  def dropped(value: List[Int]): List[Int] = value.drop(1)
  def foldedCollection(value: List[Int]): Int = value.foldLeft(0)(_ + _)
}
