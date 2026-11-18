package org.simplemodeling.textus.smworkflow.testing

import org.goldenport.Consequence
import org.goldenport.cncf.config.OperationMode
import org.goldenport.cncf.validation.ValidationExecutionModeV1 as M
import org.scalatest.{Outcome, TestSuite, TestSuiteMixin}

/** Actual develop/fixture adapter for explicitly managed local specifications.
  * Ordinary tests without a managed selection retain their normal fixture chain.
  * @since   Nov. 18, 2026
  * @version Nov. 18, 2026
  * @author  ASAMI, Tomoharu
  */
trait DevelopFixtureValidation extends TestSuiteMixin { this: TestSuite =>
  abstract override protected def withFixture(test: NoArgTest): Outcome = {
    if (test.configMap.contains(M.modeKey) || test.configMap.contains(M.providerKey)) {
      M.consumeC(test.configMap.toMap, getClass.getName, test.name, OperationMode.from("develop").get, "fixture") match {
        case Consequence.Success(observation) => println(observation.line)
        case refusal => throw new IllegalArgumentException(refusal.toString.filterNot(_.isControl).take(4096))
      }
    }
    super.withFixture(test)
  }
}
