/* rules = [NoStructuralIndentation] */
package fixture

/** Paired structural policy specification; marked findings are intentional.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
class StructuralIndented: // assert: NoStructuralIndentation
  def value: Int = 1
end StructuralIndented
trait StructuralIndentedTrait: // assert: NoStructuralIndentation
  def value: Int
object StructuralIndentedObject: // assert: NoStructuralIndentation
  val value = 1
enum StructuralIndentedEnum: // assert: NoStructuralIndentation
  case First, Second

trait StructuralShow {
  def show: String
}
object StructuralGiven {
  given StructuralShow with // assert: NoStructuralIndentation
    def show: String = "show"
  extension (value: Int)
    def doubled: Int = value * 2 // assert: NoStructuralIndentation
}
class StructuralBraced {
  def leaf(value: Int): Int =
    val increment = 1
    if value > 0 then
      value + increment
    else
      value
  def ordinaryLambda: List[Int] = List(1).map: value =>
    value + 1
  def typeColon(value: String): String =
    val text: String = "class Fake: // not a token delimiter"
    text + value
  def nested(): Int =
    class Nested {} // assert: NoStructuralIndentation
    1
  def nestedBraced(): Int = {
    class Nested {
      def leaf: Int =
        if true then 1 else 0
    }
    1
  }
  def nestedIndented(): Int = {
    class Nested: // assert: NoStructuralIndentation
      val value = 1
    1
  }
}
trait StructuralBase {
  def value: Int
}
object StructuralAnonymous {
  val value = new StructuralBase: // assert: NoStructuralIndentation
    def value: Int = 1
  val good = new StructuralBase {
    def value: Int =
      if true then 1 else 0
  }
}
object StructuralBracedGiven {
  given StructuralShow with {
    def show: String = "show"
  }
  extension (value: Int) {
    def doubled: Int = value * 2
  }
}
class StructuralBodyless
trait StructuralBodylessTrait
