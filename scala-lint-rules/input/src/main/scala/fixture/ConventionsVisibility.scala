/* rules = [GoldenportVisibilityNaming] */
package fixture

/** Paired executable specification; assertion-marked violations are intentional.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
object ConventionsVisibility {
  def public_api(inputName: String): String = inputName // assert: GoldenportVisibilityNaming
  def validApi(input_name: String): String = input_name // assert: GoldenportVisibilityNaming
  protected def wrongProtected(): Int = 1 // assert: GoldenportVisibilityNaming
  protected val correct_value = 1
  protected def create_Service(): Int = 1
  private class _Wrong // assert: GoldenportVisibilityNaming
  private object _wrong_object // assert: GoldenportVisibilityNaming
  type wrongAlias = String // assert: GoldenportVisibilityNaming
  type CorrectAlias = String
  private class InternalType
  private object InternalObject
}
class ConventionsCompanion
object ConventionsCompanion
trait ConventionsOverrideContract {
  def external_method(external_name: String): String // assert: GoldenportVisibilityNaming
}
class ConventionsOverride extends ConventionsOverrideContract {
  override def external_method(external_name: String): String = external_name
}
