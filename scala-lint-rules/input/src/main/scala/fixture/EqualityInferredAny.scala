/*
rules = [NoInferredAny]
*/
package fixture

/** Intentional compiler-qualified positive and negative rule examples.
  * @since Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author ASAMI, Tomoharu
  */
object EqualityInferredAny {
  def select[A](left: A, right: A): A = left
  // This API's lower bound forces inference to Any even for precise inputs.
  def joinErased[A >: Any](left: A, right: A): A = left
  def branch(flag: Boolean): Any = {
    // Scala 3 retains Int | Object; no alternatives are lost to Any.
    val widened = if (flag) 1 else new Object
    widened
  }
  def generic: Any = {
    val widened = select(1, new Object)
    widened
  }
  def collection: List[Any] = {
    val widened = List(1, new Object)
    widened
  }
  def erasedGeneric: Any = {
    val widened = joinErased(1, new Object) // assert: NoInferredAny
    widened
  }
  val explicitAnyTypeargs = select[Any](1, new Object)
  val explicit: List[Any] = List(1, "two")
  val numeric = List(1, 2L)
  val union: List[Int | String] = List(1, "two")
  val existingAny: Any = 1
  val intentional = List(existingAny, "two")
}
