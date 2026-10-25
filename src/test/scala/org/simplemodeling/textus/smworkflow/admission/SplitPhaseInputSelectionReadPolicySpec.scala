package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId

/**
 * @since   Oct.  2, 2026
 * @version Oct. 24, 2026
 */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class SplitPhaseInputSelectionReadPolicySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import SplitPhaseInputSelectionReadPolicy.*
  private def collection(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "splitrecords", name))
  private val selection = collection("selections")
  private val values: Map[String, ConfigurationValue] = Map(
    prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
    prefix + "selection-collection" -> ConfigurationValue.StringValue(selection.print))
  private def host(values: Map[String, ConfigurationValue]) =
    ResolvedConfiguration(HostConfiguration(values), ConfigurationTrace.empty)
  private val runtime = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("split-records")),
    MigrationPolicy("v1"), RetentionPolicy("v1"), LeasePolicy("v1"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("split-records")))
  private val workflow = SplitPhaseManagedStartAssembly.Declaration(runtime, collection("instances"), collection("continuations"))

  "SplitPhase host selection read policy" should {
    "bind an independent read-only port without GoalPhase record declarations" in {
      Given("only SplitPhase's two exact host settings and its Workflow declaration")
      When("composition binds its selection reader without a physical store")
      val port = bindC(host(values), workflow).toOption.get
      Then("the reader still requires a current managed call and confers no context-free access")
      port.loadC("selection") shouldBe a[Consequence.Failure[?]]
      port.loadForCallC("selection", null) shouldBe a[Consequence.Failure[?]]
      admitC(host(values), "workflow", selection) shouldBe a[Consequence.Success[?]]
    }

    "refuse foreign stores and collections and Workflow collection aliases" in {
      Given("a declared SplitPhase selection binding")
      When("read or composition requests another binding")
      val refused = Vector(
        admitC(host(values), "other", selection),
        admitC(host(values), "workflow", collection("foreign")),
        bindC(host(values.updated(prefix + "store-name", ConfigurationValue.StringValue("other"))), workflow),
        bindC(host(values), null)) ++ Vector(workflow.instances, workflow.continuations).map { reserved =>
        bindC(host(values.updated(prefix + "selection-collection", ConfigurationValue.StringValue(reserved.print))), workflow)
      }
      Then("all requests fail before any datastore access")
      refused.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "deny absent or malformed settings and never fall back to GoalPhase settings" in {
      Given("incomplete, noncanonical or foreign-profile configuration snapshots")
      val invalid = values.keys.toVector.map(values.removed) ++ Vector(
        Map.empty[String, ConfigurationValue],
        values.map { (key, value) => key.replace(prefix, GoalPhaseInputRecordReadPolicy.prefix) -> value },
        values.updated(prefix + "store-name", ConfigurationValue.StringValue("Workflow")),
        values.updated(prefix + "store-name", ConfigurationValue.BooleanValue(true)),
        values.updated(prefix + "selection-collection", ConfigurationValue.StringValue(selection.print + " ")),
        values.updated(prefix + "selection-collection", ConfigurationValue.StringValue("not a collection")))
      When("the pure host policy decodes each snapshot")
      val results = invalid.map(data => decodeC(host(data))) :+ decodeC(null)
      Then("no missing setting is replaced by defaults, normalization or another profile")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "read nested typed configuration with the same exact restriction" in {
      Given("SplitPhase's declaration represented as a nested resolved snapshot")
      val children = values.map { (key, value) => key.stripPrefix(prefix) -> value }
      val nested = Map("sm-workflow" -> ConfigurationValue.ObjectValue(Map(
        "split-phase" -> ConfigurationValue.ObjectValue(Map("input-records" -> ConfigurationValue.ObjectValue(children))))))
      When("the declared logical store and collection are admitted")
      val result = admitC(host(nested), "workflow", selection)
      Then("nested configuration supplies the same restriction without live property or file reads")
      result shouldBe a[Consequence.Success[?]]
      admitC(host(nested), "workflow", collection("foreign")) shouldBe a[Consequence.Failure[?]]
    }
  }
}
