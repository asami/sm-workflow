/*
rules = [NoMutableEqualityKey]
*/
package fixture

import scala.collection.mutable

/** Intentional compiler-qualified positive and negative rule examples.
  * @since Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author ASAMI, Tomoharu
  */
object EqualityMutable {
  final case class Key(var identity: Int)
  final class Stable(val identity: Int, var note: Int) {
    override def equals(other: Any): Boolean = other match { case that: Stable => identity == that.identity; case _ => false }
    override def hashCode: Int = identity
  }
  def resident: Unit = {
    val key = Key(1)
    val keys = mutable.Set(key)
    key.identity = 2 // assert: NoMutableEqualityKey
    ()
  }
  def mapResident: Unit = {
    val key = Key(1)
    val keys = mutable.Map(key -> "value")
    key.identity = 2 // assert: NoMutableEqualityKey
    ()
  }
  def aliasResident: Unit = {
    val key = Key(1)
    val alias = key
    val keys = mutable.Set(alias)
    key.identity = 2 // assert: NoMutableEqualityKey
    ()
  }
  def conditionalRemoval(flag: Boolean): Unit = {
    val key = Key(1)
    val keys = mutable.Set.empty[Key]
    keys.add(key)
    if (flag) keys.remove(key)
    key.identity = 2 // assert: NoMutableEqualityKey
    ()
  }
  def deferredClear: Unit = {
    val key = Key(1)
    val keys = mutable.Set.empty[Key]
    keys.add(key)
    val clear = () => keys.clear()
    key.identity = 2 // assert: NoMutableEqualityKey
    ()
  }
  def unconditionalRemoval: Unit = {
    val key = Key(1)
    val keys = mutable.Set.empty[Key]
    keys.add(key)
    keys.remove(key)
    key.identity = 2
    ()
  }
  def unconditionalSubtract: Unit = {
    val key = Key(1)
    val keys = mutable.Set.empty[Key]
    keys.add(key)
    keys.subtractOne(key)
    key.identity = 2
    ()
  }
  def unconditionalMinus: Unit = {
    val key = Key(1)
    val keys = mutable.Set.empty[Key]
    keys.add(key)
    keys -= key
    key.identity = 2
    ()
  }
  def unconditionalClear: Unit = {
    val key = Key(1)
    val keys = mutable.Set.empty[Key]
    keys.add(key)
    keys.clear()
    key.identity = 2
    ()
  }
  def otherCollectionRemoval: Unit = {
    val key = Key(1)
    val keys = mutable.Set.empty[Key]
    val others = mutable.Set.empty[Key]
    keys.add(key)
    others.remove(key)
    key.identity = 2 // assert: NoMutableEqualityKey
    ()
  }
  def beforeInsertion: Unit = {
    val key = Key(1)
    key.identity = 2
    val keys = mutable.Set(key)
    ()
  }
  def unrelatedField: Unit = {
    val key = new Stable(1, 0)
    val keys = mutable.Set(key)
    key.note = 2
    ()
  }
  def sortedResident: Unit = {
    given ordering: Ordering[Key] = Ordering.by(_.identity)
    val key = Key(1)
    val keys = mutable.TreeSet(key)
    key.identity = 2 // assert: NoMutableEqualityKey
    ()
  }
  def sortedUnresolved(ordering: Ordering[Key]): Unit = {
    given selected: Ordering[Key] = ordering
    val key = Key(1)
    val keys = mutable.TreeSet(key)
    key.identity = 2 // assert: NoMutableEqualityKey.AnalysisUnavailable
    ()
  }
}
