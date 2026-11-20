package org.simplemodeling.textus.smworkflow.admission {

import scala.language.strictEquality
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Representative source-selection presentation with structural braces and
  * a closed, total terminal expression.
  */
private object StrictAuthoringSelection {
  trait Label {
    def render(selected: Option[String]): String
  }

  given Label with {
    override def render(selected: Option[String]): String =
      selected match
        case Some(path) => path
        case None => "No source selected"
  }

  extension (selected: Option[String]) {
    def label(using renderer: Label): String = renderer.render(selected)
  }
}

/** Executes the representative AI-authored Scala 3 presentation behavior.
  * @since   Nov. 20, 2026
  * @version Nov. 20, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class WorkflowStrictAuthoringSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import StrictAuthoringSelection.*
  import StrictAuthoringSelection.given

  "Strict Scala 3 source selection" should {
    "render a selected path and an absent selection through the same total interface" in {
      Given("a selected Scala source or no selected source")
      val selections: Vector[(Option[String], String)] = Vector(
        Some("Consumer.scala") -> "Consumer.scala",
        None -> "No source selected")

      When("the extension delegates to its explicitly braced given implementation")
      selections.foreach { case (selected, expected) =>
        Then("the terminal match preserves the selected path and handles absence")
        assert(selected.label == expected)
      }
    }
  }
}

}
