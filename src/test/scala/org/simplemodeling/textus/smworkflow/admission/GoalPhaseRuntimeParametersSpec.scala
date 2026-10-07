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
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
final class GoalPhaseRuntimeParametersSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"runtime parameters failed: $other")
  }
  private def resource(path: String): String = {
    val source = scala.io.Source.fromInputStream(getClass.getResourceAsStream(path), "UTF-8")
    try source.mkString finally source.close()
  }
  private val catalog = take(GoalPhaseRuntimeCatalog.parseC(
    resource("/workflow/goal-phase-workflow-abi.json"), resource("/workflow/goal-phase-candidate-admission-producer-abi.json")))
  private val root = EntityCollectionId("test", "configuredgoal", "instances")
  private val delivery = EntityCollectionId("test", "configuredgoal", "continuations")
  private val valid = Map(
    GoalPhaseRuntimeParameters.storeName.name -> "workflow",
    GoalPhaseRuntimeParameters.storeIdentity.name -> "configured-goal-store",
    GoalPhaseRuntimeParameters.migrationPolicy.name -> "migration-v2",
    GoalPhaseRuntimeParameters.retentionPolicy.name -> "retention-v3",
    GoalPhaseRuntimeParameters.leasePolicy.name -> "lease-v4",
    GoalPhaseRuntimeParameters.instances.name -> root.print,
    GoalPhaseRuntimeParameters.continuations.name -> delivery.print
  ).view.mapValues(ConfigurationValue.StringValue.apply).toMap

  private def decode(values: Map[String, ConfigurationValue]): Consequence[GoalPhaseManagedStartAssembly.Declaration] = {
    val subsystem = RuntimeBindingAdmissionFixture.admit(Subsystem(name = "runtime-parameters", version = None,
      configuration = ResolvedConfiguration(Configuration(values), ConfigurationTrace.empty),
      aliasResolver = AliasResolver.empty, runMode = RunMode.Command))
    val id = SmWorkflowComponent.componentId
    val descriptor = ComponentDescriptor(name = Some(id.name), componentName = Some(id.name),
      version = Some("0.1.0"), schemaVersion = Some(3), componentId = Some(id))
    var decoded: Option[GoalPhaseManagedStartAssembly.Declaration] = None
    val factory = new GoalPhaseConfiguredComponentFactory(GoalPhaseRuntimeParameters.declarations, Vector.empty,
      (_, parameters, _) => GoalPhaseRuntimeParameters.decodeC(parameters, catalog).map { declaration =>
        decoded = Some(declaration)
        SmWorkflowComponent.StepReviewServiceService
      })
    factory.primaryFactory.createPrimaryC(ComponentCreate(subsystem, ComponentOrigin.Builtin, Vector(descriptor)))
      .flatMap(_ => decoded match {
        case Some(value) => Consequence.success(value)
        case None => Consequence.configurationInvalid("configured declaration was not assembled")
      })
  }

  "GoalPhase runtime persistence parameters" should {
    "retain explicit policies and canonical collections without acquiring private storage" in {
      Given("complete canonical logical settings and no physical datastore configuration")
      When("canonical resolution feeds the post-resolution factory binder")
      val result = take(decode(valid))
      Then("the declaration retains the configured versions and admitted ABI with same-store delivery")
      result.configuration.workflowStore shouldBe NamedWorkflowStore("workflow", WorkflowStoreIdentity("configured-goal-store"))
      result.configuration.migrationPolicy shouldBe MigrationPolicy("migration-v2")
      result.configuration.retentionPolicy shouldBe RetentionPolicy("retention-v3")
      result.configuration.leasePolicy shouldBe LeasePolicy("lease-v4")
      result.configuration.delivery shouldBe DeliveryConfiguration.SameStore(WorkflowStoreIdentity("configured-goal-store"))
      result.instances shouldBe org.goldenport.cncf.datastore.DataStore.CollectionId.EntityStore(root)
      result.continuations shouldBe org.goldenport.cncf.datastore.DataStore.CollectionId.EntityStore(delivery)
      GoalPhaseRuntimeCatalog.admitBindingC(catalog, result, catalog.producer) shouldBe a[Consequence.Success[?]]
    }

    "reject every missing required setting instead of choosing defaults" in {
      Given("otherwise complete settings with each required key separately absent")
      val variants = valid.keys.toVector.map(name => name -> (valid - name))
      When("each incomplete configuration is assembled")
      val results = variants.map { case (name, values) => name -> decode(values) }
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
      val results = for { name <- valid.keys.toVector; value <- invalid } yield decode(valid.updated(name, value))
      Then("no replacement is normalized or accepted as a persistence fact")
      results.size shouldBe 42
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "reject malformed or aliased collections and noncanonical logical store names" in {
      Given("complete settings with invalid collection identity or ambiguous storage roles")
      val variants = Vector(
        valid.updated(GoalPhaseRuntimeParameters.instances.name, ConfigurationValue.StringValue("instances")),
        valid.updated(GoalPhaseRuntimeParameters.continuations.name, ConfigurationValue.StringValue("foreign")),
        valid.updated(GoalPhaseRuntimeParameters.continuations.name, ConfigurationValue.StringValue(root.print)),
        valid.updated(GoalPhaseRuntimeParameters.storeName.name, ConfigurationValue.StringValue(" workflow")),
        valid.updated(GoalPhaseRuntimeParameters.storeName.name, ConfigurationValue.StringValue("Workflow")))
      When("configuration is decoded without touching a DataStore or minting a grant")
      val results = variants.map(decode)
      Then("none can establish the required two distinct canonical collections")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "reject missing snapshots or catalogs without reconstructing authority" in {
      Given("null or undeclared snapshots and a missing admitted catalog")
      When("logical persistence declaration decoding is requested")
      val results = Vector(GoalPhaseRuntimeParameters.decodeC(null, catalog),
        GoalPhaseRuntimeParameters.decodeC(ComponentInitializationParameters.empty, catalog),
        GoalPhaseRuntimeParameters.decodeC(ComponentInitializationParameters.empty, null))
      Then("all remain configuration failures rather than default or human-authorized starts")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }
  }
}
