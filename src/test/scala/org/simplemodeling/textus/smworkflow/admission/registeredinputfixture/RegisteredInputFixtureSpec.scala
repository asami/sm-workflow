package org.simplemodeling.textus.smworkflow.admission.registeredinputfixture

import org.goldenport.cncf.validation.annotation.ValidationSpecification
import org.scalatest.DoNotDiscover
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration

/** One source of actual registered cases and their typed body inputs.
  * Input descriptions are evidence, never additional execution arguments.
  * @since Oct. 30, 2026
  */
object RegisteredInputFixtureCases {
  val counts = Vector(1, 2)
  def name(count: Int): String = s"registered input should execute count=$count"
  def countFor(name: String): Option[Int] = counts.find(count => this.name(count) == name)
}

@DoNotDiscover
@ValidationSpecification(declaration = classOf[LocalTestDeclaration])
final class RegisteredInputFixtureSpec extends AnyWordSpec with Matchers {
  "registered input" should {
    RegisteredInputFixtureCases.counts.foreach { count =>
      s"execute count=$count" in {
        Vector.fill(count)(3).sum shouldBe 3 * count
        info(s"REGISTERED_TYPED_INPUT count=$count")
      }
    }
  }
}
