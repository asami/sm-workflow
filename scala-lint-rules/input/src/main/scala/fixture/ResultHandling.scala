/*
rules = [NoDiscardedFailureResult]
*/
package fixture

import scala.util.Try

/** Intentional compiler-qualified positive and negative rule examples.
  * @since Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author ASAMI, Tomoharu
  */
object ResultHandling {
  def attempt(value: Int): Try[Int] = Try(value + 1)
  def either(value: Int): Either[String, Int] = Right(value)
  def discardedStatement: Int = {
    attempt(1) // assert: NoDiscardedFailureResult
    either(2) // assert: NoDiscardedFailureResult
    0
  }
  def discardedUnit: Unit = attempt(1) // assert: NoDiscardedFailureResult
  def callback(values: List[Int]): Unit = values.foreach { value =>
    attempt(value) // assert: NoDiscardedFailureResult
  }
  def methodCallback(values: List[Int]): Unit = values.foreach(attempt) // assert: NoDiscardedFailureResult
  def aliasedCallback(values: List[Int]): Unit = {
    val callback: Int => Try[Int] = attempt
    values.foreach(callback) // assert: NoDiscardedFailureResult
  }
  val unitCallback: Int => Unit = value => either(value) // assert: NoDiscardedFailureResult
  def consume(callback: Int => Unit): Unit = callback(1)
  def explicitCallback: Unit = consume(value => attempt(value)) // assert: NoDiscardedFailureResult
  def retain(callback: Int => Try[Int]): Try[Int] = callback(1)
  def retainedCallback: Try[Int] = retain(value => attempt(value))
  def mappedCallback(values: List[Int]): List[Try[Int]] = values.map { value => attempt(value) }
  def returned: Try[Int] = attempt(1)
  def composed: Try[Int] = attempt(1).map(_ + 1)
  val stored: Either[String, Int] = either(1)
  def passed: List[Try[Int]] = List(1).map(attempt)
  def mutation(buffer: scala.collection.mutable.Buffer[Int]): Unit = {
    buffer.append(1)
    buffer.addOne(2)
    ()
  }
}
