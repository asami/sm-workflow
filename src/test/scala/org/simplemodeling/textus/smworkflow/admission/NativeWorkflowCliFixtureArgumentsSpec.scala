package org.simplemodeling.textus.smworkflow.admission

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class NativeWorkflowCliFixtureArgumentsSpec extends AnyWordSpec with Matchers {
  "The packaged CNCF runtime selector" should {
    "pass the option and version as separate arguments when selected" in {
      NativeWorkflowCliFixture.runtimeArguments(Some("0.5.6-SNAPSHOT")) shouldBe
        Vector("--runtime", "0.5.6-SNAPSHOT")
    }

    "omit the selector when no version is selected" in {
      NativeWorkflowCliFixture.runtimeArguments(None) shouldBe Vector.empty
    }
  }
}
