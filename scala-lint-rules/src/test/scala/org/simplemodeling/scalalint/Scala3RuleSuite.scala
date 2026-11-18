package org.simplemodeling.scalalint

/** Inputs are compiled by the actual pinned Scala 3 compiler, not semantic mocks.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
final class Scala3RuleSuite extends scalafix.testkit.AbstractSemanticRuleSuite with org.scalatest.funsuite.AnyFunSuiteLike {
  runAllTests()
}
