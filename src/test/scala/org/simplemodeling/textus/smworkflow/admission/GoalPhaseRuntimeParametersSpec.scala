package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.cncf.component.{ComponentCreate, ComponentDescriptor, ComponentOrigin}
import org.goldenport.cncf.config.ComponentInitializationParameters
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowMechanicalReceiptParameters
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct.  1, 2026
 * @version Oct. 21, 2026
 */
final class GoalPhaseRuntimeParametersSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"runtime parameters failed: $other")
  }
  private val _root = EntityCollectionId("test", "configuredgoal", "instances")
  private val _delivery = EntityCollectionId("test", "configuredgoal", "continuations")
  private val _valid = Map(
    GoalPhaseRuntimeParameters.storeName.name -> "workflow",
    GoalPhaseRuntimeParameters.storeIdentity.name -> "configured-goal-store",
    GoalPhaseRuntimeParameters.migrationPolicy.name -> "migration-v2",
    GoalPhaseRuntimeParameters.retentionPolicy.name -> "retention-v3",
    GoalPhaseRuntimeParameters.leasePolicy.name -> "lease-v4",
    GoalPhaseRuntimeParameters.instances.name -> _root.print,
    GoalPhaseRuntimeParameters.continuations.name -> _delivery.print
  ).view.mapValues(ConfigurationValue.StringValue.apply).toMap

  private val _capacity_metadata = afterWord("in spec:workflow-mechanical-receipt-capacity, example:E1, rules:R1,R2,R3, phase:capacity-repair")

  private def _decode(values: Map[String, ConfigurationValue]): Consequence[GoalPhaseRuntimeParameters.Persistence] = {
    val subsystem = RuntimeBindingAdmissionFixture.admit(Subsystem(name = "runtime-parameters", version = None,
      configuration = ResolvedConfiguration(Configuration(values), ConfigurationTrace.empty),
      aliasResolver = AliasResolver.empty, runMode = RunMode.Command))
    val id = SmWorkflowComponent.componentId
    val descriptor = ComponentDescriptor(name = Some(id.name), componentName = Some(id.name),
      version = Some("0.1.0"), schemaVersion = Some(3), componentId = Some(id))
    var decoded: Option[GoalPhaseRuntimeParameters.Persistence] = None
    val factory = new SmWorkflowConfiguredComponentFactory(GoalPhaseRuntimeParameters.declarations, Vector.empty,
      (_, parameters, _) => (GoalPhaseRuntimeParameters.decodeC(parameters).map { declaration =>
        decoded = Some(declaration)
        SmWorkflowComponent.GoalPhaseStepReviewServiceService
      }).map(service => Vector(service)))
    factory.primaryFactory.createPrimaryC(ComponentCreate(subsystem, ComponentOrigin.Builtin, Vector(descriptor)))
      .flatMap(_ => decoded match {
        case Some(value) => Consequence.success(value)
        case None => Consequence.configurationInvalid("configured declaration was not assembled")
      })
  }

  "GoalPhase runtime persistence parameters" should {
    "E1 choose sixteen MiB only when the optional CNCF declaration is absent" must _capacity_metadata {
      "when independent configured factories resolve absent and explicit policies" in {
        Given("Spec: docs/spec/workflow-mechanical-receipt-capacity.md; Rules: R1,R2,R3; Example: E1; normal complete logical settings")
        val name = WorkflowMechanicalReceiptParameters.maxBytes.name
        When("separate normal configured factories resolve default, explicit two MiB and independent default settings")
        val first = _take(_decode(_valid))
        val explicit = _take(_decode(_valid.updated(name, ConfigurationValue.StringValue("2097152"))))
        val numeric = _take(_decode(_valid.updated(name, ConfigurationValue.NumberValue(BigDecimal(2097152)))))
        val second = _take(_decode(_valid))
        Then("absence selects the application default and a present value selects only that factory's policy")
        first.configuration.mechanicalReceiptMaxBytes shouldBe Some(16777216)
        explicit.configuration.mechanicalReceiptMaxBytes shouldBe Some(2097152)
        numeric.configuration.mechanicalReceiptMaxBytes shouldBe Some(2097152)
        second.configuration.mechanicalReceiptMaxBytes shouldBe Some(16777216)
        _take(first.configuration.mechanicalReceiptLimitsC).maxBytes shouldBe 16777216
        _take(explicit.configuration.mechanicalReceiptLimitsC).maxBytes shouldBe 2097152
        GoalPhaseRuntimeParameters.declarations.count(_.name == name) shouldBe 1
      }
    }

    "E1 reject invalid present capacity values without selecting the application default" must _capacity_metadata {
      "when normal configuration contains a malformed or out-of-range override" in {
        Given("Spec: docs/spec/workflow-mechanical-receipt-capacity.md; Rules: R1,R2; Example: E1; complete logical settings and invalid optional values")
        val invalid = Vector(ConfigurationValue.StringValue("0"), ConfigurationValue.StringValue("-1"),
          ConfigurationValue.StringValue("67108865"), ConfigurationValue.StringValue("2147483648"),
          ConfigurationValue.StringValue(" 2097152"), ConfigurationValue.StringValue("2.0"),
          ConfigurationValue.StringValue("２"), ConfigurationValue.NumberValue(BigDecimal("1.5")),
          ConfigurationValue.NumberValue(BigDecimal(67108865)), ConfigurationValue.BooleanValue(true),
          ConfigurationValue.NullValue, ConfigurationValue.ObjectValue(Map.empty))
        When("the existing configured factory resolves the optional CNCF declaration")
        val results = invalid.map(value => _decode(_valid.updated(WorkflowMechanicalReceiptParameters.maxBytes.name, value)))
        Then("every present invalid policy fails instead of falling back to sixteen MiB")
        results.foreach(_ shouldBe a[Consequence.Failure[?]])
      }
    }

    "retain explicit policies and canonical collections without acquiring private storage" in {
      Given("complete canonical logical settings and no physical datastore configuration")
      When("canonical resolution feeds the post-resolution factory binder")
      val result = _take(_decode(_valid))
      Then("logical configuration retains its actual policies and same-store destinations without a catalog prerequisite")
      result.configuration.workflowStore shouldBe NamedWorkflowStore("workflow", WorkflowStoreIdentity("configured-goal-store"))
      result.configuration.migrationPolicy shouldBe MigrationPolicy("migration-v2")
      result.configuration.retentionPolicy shouldBe RetentionPolicy("retention-v3")
      result.configuration.leasePolicy shouldBe LeasePolicy("lease-v4")
      result.configuration.delivery shouldBe DeliveryConfiguration.SameStore(WorkflowStoreIdentity("configured-goal-store"))
      result.instances shouldBe org.goldenport.cncf.datastore.DataStore.CollectionId.EntityStore(_root)
      result.continuations shouldBe org.goldenport.cncf.datastore.DataStore.CollectionId.EntityStore(_delivery)
    }

    "reject every missing required setting instead of choosing defaults" in {
      Given("otherwise complete settings with each required key separately absent")
      val variants = _valid.keys.toVector.map(name => name -> (_valid - name))
      When("each incomplete configuration is assembled")
      val results = variants.map { case (name, values) => name -> _decode(values) }
      Then("all seven omissions fail before a configured component is returned")
      results.size shouldBe 7
      results.foreach { case (name, result) => withClue(name)(result shouldBe a[Consequence.Failure[?]]) }
    }

    "reject blanks whitespace and nonstring values without coercion" in {
      Given("each required key replaced by invalid string or nonstring values")
      val invalid = Vector(ConfigurationValue.StringValue(""), ConfigurationValue.StringValue(" "),
        ConfigurationValue.StringValue(" padded "), ConfigurationValue.NumberValue(BigDecimal(3)),
        ConfigurationValue.BooleanValue(true), ConfigurationValue.NullValue)
      When("canonical parameter resolution decodes the typed declarations")
      val results = for { name <- _valid.keys.toVector; value <- invalid } yield _decode(_valid.updated(name, value))
      Then("no replacement is normalized or accepted as a persistence fact")
      results.size shouldBe 42
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "reject malformed or aliased collections and noncanonical logical store names" in {
      Given("complete settings with invalid collection identity or ambiguous storage roles")
      val variants = Vector(
        _valid.updated(GoalPhaseRuntimeParameters.instances.name, ConfigurationValue.StringValue("instances")),
        _valid.updated(GoalPhaseRuntimeParameters.continuations.name, ConfigurationValue.StringValue("foreign")),
        _valid.updated(GoalPhaseRuntimeParameters.continuations.name, ConfigurationValue.StringValue(_root.print)),
        _valid.updated(GoalPhaseRuntimeParameters.storeName.name, ConfigurationValue.StringValue(" workflow")),
        _valid.updated(GoalPhaseRuntimeParameters.storeName.name, ConfigurationValue.StringValue("Workflow")))
      When("configuration is decoded without touching a DataStore or minting a grant")
      val results = variants.map(_decode)
      Then("none can establish the required two distinct canonical collections")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "reject missing parameter snapshots without reconstructing authority" in {
      Given("null or undeclared logical parameter snapshots")
      When("logical persistence declaration decoding is requested")
      val results = Vector(GoalPhaseRuntimeParameters.decodeC(null),
        GoalPhaseRuntimeParameters.decodeC(ComponentInitializationParameters.empty))
      Then("all remain configuration failures rather than default or human-authorized starts")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }
  }
}
