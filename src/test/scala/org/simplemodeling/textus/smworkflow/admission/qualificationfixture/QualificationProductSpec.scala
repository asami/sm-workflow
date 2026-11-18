/**
 * @since   Nov. 17, 2026
 * @version Nov. 18, 2026
 */
package org.simplemodeling.textus.smworkflow.admission.qualificationfixture

import java.nio.file.{Files, Path}
import org.scalatest.DoNotDiscover
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Explicit-only product used by the native qualification specification. */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class QualificationProductSpec extends AnyWordSpec with Matchers {
  "The isolated qualification product" should {
    "retain the selected semantic content" in {
      Files.readString(Path.of("value.txt")) shouldBe sys.env.getOrElse(
        "SM_WORKFLOW_QUALIFICATION_EXPECTED", "selected semantic change\n")
    }
  }
}
