/*
rules = [GoldenportPrivateMemberNaming]
*/
package fixture

/** Paired naming input; assertion-marked nonconforming names are intentional violations.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
object PrivateMembers {
  private def _wrapCommand(value: String): String = value // assert: GoldenportPrivateMemberNaming
  private def _wrap_command(value: String): String = value
  private val _workerIdentity = "dummy" // assert: GoldenportPrivateMemberNaming
  private val _worker_identity = "dummy"
  private lazy val _lazyValue = "dummy" // assert: GoldenportPrivateMemberNaming
  private lazy val _lazy_value = "dummy"
  private var _mutableValue = 0 // assert: GoldenportPrivateMemberNaming
  private var _mutable_value = 0
  private[fixture] def _qualifiedName: Int = 1 // assert: GoldenportPrivateMemberNaming
  private[fixture] def _qualified_name: Int = 1
  private val `_escapedValue` = "dummy" // assert: GoldenportPrivateMemberNaming
  private val `_escaped_value` = "dummy"
  private class InternalType
  private object InternalObject
  def publicApiName(value: String): String = value
}
