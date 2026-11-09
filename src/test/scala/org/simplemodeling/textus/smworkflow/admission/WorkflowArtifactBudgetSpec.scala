package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.{ComponentCreate, ComponentDescriptor, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.resource.{InMemoryUrnResourceProvider, ResourceAccessTestProfile, ResourceReadLimits, ResourceReference}
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/** Configuration and complete serialized acquisition, without workflow effects.
  * @since   Nov.  5, 2026
  * @version Nov.  6, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class WorkflowArtifactBudgetSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _decode(values: Map[String, ConfigurationValue]): Consequence[ResourceReadLimits] = {
    val subsystem = RuntimeBindingAdmissionFixture.admit(Subsystem(name = "artifact-budget", version = None,
      configuration = ResolvedConfiguration(Configuration(values), ConfigurationTrace.empty),
      aliasResolver = AliasResolver.empty, runMode = RunMode.Command))
    val id = SmWorkflowComponent.componentId
    val descriptor = ComponentDescriptor(name = Some(id.name), componentName = Some(id.name),
      version = Some("0.1.0"), schemaVersion = Some(3), componentId = Some(id))
    var decoded = Option.empty[ResourceReadLimits]
    val factory = new SmWorkflowConfiguredComponentFactory(Vector(WorkflowArtifactBudget.maxBytes), Vector.empty,
      (_, parameters, _) => WorkflowArtifactBudget.resolveC(parameters).map { limits =>
        decoded = Some(limits)
        Vector(SmWorkflowComponent.GoalPhaseStepReviewServiceService)
      })
    factory.primaryFactory.createPrimaryC(ComponentCreate(subsystem, ComponentOrigin.Builtin, Vector(descriptor)))
      .flatMap(_ => decoded.map(Consequence.success).getOrElse(
        Consequence.configurationInvalid("artifact budget was not assembled")))
  }

  "Workflow artifact acquisition policy" should {
    "select sixty-four MiB only when normal Component configuration has no value" in {
      Given("normal Component initialization with absent, numeric and string artifact settings")
      val key = WorkflowArtifactBudget.maxBytes.name

      When("independent initialized components resolve their optional policy")
      val absent = _decode(Map.empty)
      val numeric = _decode(Map(key -> ConfigurationValue.NumberValue(BigDecimal(2097152))))
      val explicit = _decode(Map(key -> ConfigurationValue.StringValue("134217728")))
      val repeated = _decode(Map.empty)

      Then("each component retains its selected budget and ordinary Resource defaults stay independent")
      absent.toOption.map(_.maximumBytes) shouldBe Some(67108864)
      numeric.toOption.map(_.maximumBytes) shouldBe Some(2097152)
      explicit.toOption.map(_.maximumBytes) shouldBe Some(134217728)
      repeated.toOption.map(_.maximumBytes) shouldBe Some(67108864)
      ResourceReadLimits.default.maximumBytes shouldBe 1048576
      ThreeWorkflowRuntimeParameters.declarations.count(_.name == key) shouldBe 1
    }

    "reject malformed and CNCF-unsupported explicit values as configuration failures" in {
      Given("present malformed, noninteger, out-of-range and unsupported configuration values")
      val invalid = Vector(ConfigurationValue.StringValue("0"), ConfigurationValue.StringValue("-1"),
        ConfigurationValue.StringValue("268435457"), ConfigurationValue.StringValue("2147483648"),
        ConfigurationValue.StringValue(" 67108864"), ConfigurationValue.StringValue("64MiB"),
        ConfigurationValue.StringValue("２"), ConfigurationValue.NumberValue(BigDecimal("1.5")),
        ConfigurationValue.BooleanValue(true), ConfigurationValue.NullValue, ConfigurationValue.ObjectValue(Map.empty))

      When("normal Component initialization resolves each explicit value")
      val results = invalid.map(value => _decode(Map(WorkflowArtifactBudget.maxBytes.name -> value)))

      Then("no invalid override silently selects the absent-property default")
      results.foreach(_.isFaillure shouldBe true)
      WorkflowArtifactBudget.decodeC(ConfigurationValue.StringValue("1")).toOption.map(_.maximumBytes) shouldBe Some(1)
      WorkflowArtifactBudget.decodeC(ConfigurationValue.StringValue("268435456")).toOption.map(_.maximumBytes) shouldBe Some(268435456)
    }

    "acquire the complete serialized UTF-8 value under its inclusive purpose budget" in {
      Given("literal Unicode and escaped JSON fixtures with a smaller independent ordinary policy")
      val texts = Vector("{\"text\":\"日本\"}\n", "{\"text\":\"\\u65e5\\u672c\"}\n")
      texts.foreach { text =>
        val reference = ResourceReference.parseC("urn:example:artifact").toOption.get
        val context = ExecutionContext.withResourceAccessTestProfile(ExecutionContext.create(), ResourceAccessTestProfile(
          urnProviders = Vector(new InMemoryUrnResourceProvider("example", Map("artifact" -> text))),
          readLimits = ResourceReadLimits(1)))
        val bytes = text.getBytes(UTF_8).length

        When("the same existing Resource owner acquires at equality and one byte below")
        val equal = context.readResourceText(reference, ResourceReadLimits(bytes), Some(UTF_8))
        val excess = context.readResourceText(reference, ResourceReadLimits(bytes - 1), Some(UTF_8))
        val workflow = WorkflowArtifactBudget.readTextC(ActionCall.Core(null, context, None, None), reference)

        Then("framing and escapes count as input bytes and no partial text is exposed")
        equal.toOption shouldBe Some(text)
        workflow.toOption shouldBe Some(text)
        excess.isFaillure shouldBe true
        excess.toOption shouldBe None
        context.resources.readText(reference).isFaillure shouldBe true
      }
    }
  }
}
