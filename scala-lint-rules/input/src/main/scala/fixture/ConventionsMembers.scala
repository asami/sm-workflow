/* rules = [GoldenportPrivateMemberNaming, GoldenportLocalHelperNaming] */
package fixture

/** Paired executable specification; assertion-marked violations are intentional.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
object ConventionsMembers {
  private def exact: Int = 1 // assert: GoldenportPrivateMemberNaming
  private val reference = 1 // assert: GoldenportPrivateMemberNaming
  private def _member_(): Int = 1 // assert: GoldenportPrivateMemberNaming
  private def _member(): Int = 1
  private[fixture] lazy val _cached_value = 1
  private object InternalObject
  private class InternalClass
  def exercise(): Int = {
    def wrongHelper(): Int = 1 // assert: GoldenportLocalHelperNaming
    def _wrong_helper(): Int = 1 // assert: GoldenportLocalHelperNaming
    def _right_helper_(): Int = 1
    class Nested {
      private def badName(): Int = 1 // assert: GoldenportPrivateMemberNaming
      private def _good_name(): Int = 1
    }
    _right_helper_()
  }
}
class ConventionsConstructor(private val wrongField: Int) // assert: GoldenportPrivateMemberNaming
class ConventionsValidConstructor(private val _valid_field: Int)
trait ConventionsContract {
  def requiredName(valueName: Int): Int
}
class ConventionsImplementation extends ConventionsContract {
  override def requiredName(valueName: Int): Int = valueName
}
