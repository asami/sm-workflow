/* rules = [GoldenportFlatcaseNaming] */
package fixture

/** Paired executable specification; assertion-marked violations are intentional.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
object ConventionsFlatcase {
  private def _parse(wrongName: String): String = wrongName // assert: GoldenportFlatcaseNaming
  private def _good(inputvalue: String): String = {
    val localName = inputvalue // assert: GoldenportFlatcaseNaming
    val (firstName, second) = (inputvalue, inputvalue) // assert: GoldenportFlatcaseNaming
    var local_count = 0 // assert: GoldenportFlatcaseNaming
    val localvalue = inputvalue
    List(localvalue).map { itemValue => itemValue } // assert: GoldenportFlatcaseNaming
    localvalue
  }
  def patternBinding(inputValue: Option[String]): String = inputValue match {
    case Some(wrongName) => wrongName // assert: GoldenportFlatcaseNaming
    case None => ""
  }
  def publicApi(inputValue: String): String = inputValue
  protected def protected_api(inputValue: String): String = inputValue
  private case class InternalField(fieldName: String) // assert: GoldenportFlatcaseNaming
  private case class InternalValue(fieldname: String)
  // scalafix:off GoldenportFlatcaseNaming
  // Required external serialization label; the syntax cannot establish this contract.
  private case class SerializedField(externalName: String)
  // scalafix:on GoldenportFlatcaseNaming
}
