package org.simplemodeling.scalalint

import scala.meta.io.AbsolutePath
import scalafix.testkit.{AbstractSemanticRuleSuite, TestkitProperties}
import scalafix.v1.SemanticDocument
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Required analysis must fail when compiler SemanticDB is unavailable.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
final class UnavailableAnalysisSpec extends AnyWordSpec with Matchers {
  "Required Option analysis" should {
    "fail when the actual source has no compiler-produced SemanticDB document" in {
      val compiled = TestkitProperties.loadFromResources()
      val source = AbsolutePath(java.nio.file.Paths.get(getClass.getResource("/analysis-unavailable").toURI))
      val props = new TestkitProperties(compiled.inputClasspath, List(source), Nil,
        compiled.sourceroot, compiled.scalaVersion, compiled.scalacOptions)
      val suite = new AbstractSemanticRuleSuite(props) with org.scalatest.funsuite.AnyFunSuiteLike
      intercept[SemanticDocument.Error.MissingSemanticdb] {
        suite.testsToRun.head.run.apply()
      }
    }
  }
}
