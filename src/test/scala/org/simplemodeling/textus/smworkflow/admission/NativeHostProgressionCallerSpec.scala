/**
 * @since   Nov. 17, 2026
 * @version Nov. 18, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Connect the finite caller's executable specifications to normal Test discovery.
  * Subprocess responses in the Python suite are specification-owned doubles;
  * this does not qualify an installed runtime or genuine Goal continuation.
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class NativeHostProgressionCallerSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "The finite native host caller" should {
    "execute compact wait and next-operation cases with the existing caller refusal and lifecycle regressions" in {
      Given("the existing managed Python >=3.12 environment and specification-owned native responses")
      val log = Files.createTempFile("sm-workflow-host-caller-", ".log")
      val process = new ProcessBuilder("uv", "run", "--no-cache", "--managed-python", "--no-python-downloads",
        "--script", "scripts/tests/test_native_host_progression.py", "-v")
        .redirectErrorStream(true).redirectOutput(log.toFile).start()
      try {
        When("normal Scala Test selects the complete caller regression specification")
        val terminated = process.waitFor(180, TimeUnit.SECONDS)
        val output = Files.readString(log, UTF_8)
        withClue(output) {
          terminated shouldBe true
          process.exitValue() shouldBe 0
          Then("both compact boundaries and invalid presentation execute alongside the existing negative cases")
          output should include("test_compact_complete_retains_wait_boundary_and_lossless_detail_without_another_advance")
          output should include("test_compact_request_exposes_only_the_actual_issued_next_operation")
          output should include("test_invalid_presentation_refuses_before_native_execution")
          output should include("\nOK")
          output should not include "skipped="
        }
      } finally {
        if (process.isAlive) {
          process.destroyForcibly()
          process.waitFor(10, TimeUnit.SECONDS)
        }
        Files.deleteIfExists(log)
      }
    }
  }
}
