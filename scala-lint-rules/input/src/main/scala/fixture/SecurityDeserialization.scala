/*
rules = [NoUncheckedJavaDeserialization]
*/
package fixture

/** Compiler-produced SemanticDB cases; fixture secrets are dummy data only.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
object SecurityDeserialization {
  def unchecked(input: java.io.ObjectInputStream): AnyRef = input.readObject() // assert: NoUncheckedJavaDeserialization
  def alias(input: java.io.ObjectInputStream): AnyRef = {
    val stream = input
    stream.readUnshared() // assert: NoUncheckedJavaDeserialization
  }
  def filtered(input: java.io.ObjectInputStream): AnyRef = {
    input.setObjectInputFilter(java.io.ObjectInputFilter.Config.createFilter("java.lang.String;!*"))
    input.readObject() // assert: NoUncheckedJavaDeserialization
  }
  def data(input: java.io.DataInputStream): Int = input.readInt()
  final class Other { def readObject(): String = "ordinary data" }
  def unrelated(value: Other): String = value.readObject()
  def qualified(input: java.io.ObjectInputStream): AnyRef =
    // reviewed trusted boundary fixture, SLP-A25
    input.readObject() // scalafix:ok NoUncheckedJavaDeserialization
}
