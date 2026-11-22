package org.simplemodeling.textus.smworkflow.admission.phase7workspacefixture

/** Inputs for a suite compiled only in the selected native consumer build.
  * Keeping the suite and its declaration out of the parent loader preserves
  * discovery's exact compiled-artifact provenance check.
  * @since   Nov. 22, 2026
  * @version Nov. 21, 2026
  * @author  ASAMI, Tomoharu
  */
private[admission] object Phase7WorkspaceConsumerFixtureSource {
  val packageName = "org.simplemodeling.textus.smworkflow.admission.phase7workspacefixture"
  val suiteName = packageName + ".Phase7NativeScopedConsumerSpec"
  val source = """package org.simplemodeling.textus.smworkflow.admission.phase7workspacefixture

import org.goldenport.cncf.config.OperationMode
import org.goldenport.cncf.validation.{ValidationDeclarationProvider, ValidationExecutionModeV1 as M}
import org.goldenport.cncf.validation.ValidationMetadataV1.*
import org.goldenport.cncf.validation.annotation.ValidationSpecification
import org.scalatest.{DoNotDiscover, GivenWhenThen, Outcome}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class Phase7NativeScopedConsumerDeclaration extends ValidationDeclarationProvider {
  def declaration: Declaration = Declaration(purposes = Some(Set(Purpose.FOCUSED)))
}

@DoNotDiscover
@ValidationSpecification(identity = "phase7-native-consumer", revision = "1",
  declaration = classOf[Phase7NativeScopedConsumerDeclaration])
class Phase7NativeScopedConsumerSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "the native selected consumer" should {
    "execute its compiled provider input after independent providers finish" in {
      Given("the actually compiled consumer and its selected private provider")
      val consumer = Class.forName("example.Consumer$")
      val module = consumer.getField("MODULE$").get(null)
      When("the consumer evaluates its real provider-dependent answer")
      val answer = consumer.getMethod("answer").invoke(module)
      Then("the remote consumer source and selected provider agree")
      answer shouldBe Integer.valueOf(43)
    }
  }
  override protected def withFixture(test: NoArgTest): Outcome = {
    val observed = M.consumeC(test.configMap.toMap, getClass.getName, test.name, OperationMode.Develop, "fixture").toOption.get
    println(observed.line)
    super.withFixture(test)
  }
}
"""
}
