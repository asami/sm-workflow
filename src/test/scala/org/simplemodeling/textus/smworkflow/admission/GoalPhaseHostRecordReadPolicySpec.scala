package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.simplemodeling.model.datatype.EntityCollectionId
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
final class GoalPhaseHostRecordReadPolicySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import GoalPhaseHostRecordReadPolicy.*
  private def collection(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "hostrecords", name))
  private val records = Purpose.values.toVector.map(role => role -> collection(role.toString.toLowerCase))
  private val values: Map[String, ConfigurationValue] =
    Map(prefix + "store-name" -> ConfigurationValue.StringValue("workflow")) ++ records.map { (role, identity) =>
      prefix + role.key -> ConfigurationValue.StringValue(identity.print)
    }
  private def configuration(values: Map[String, ConfigurationValue]) =
    ResolvedConfiguration(HostConfiguration(values), ConfigurationTrace.empty)

  "GoalPhase host record read policy" should {
    "admit only the exact declared purpose, logical store and collection" in {
      Given("an explicit resolved Subsystem declaration with distinct record collections")
      for ((role, identity) <- records) {
        When("each reader asks for its declared binding")
        admitC(configuration(values), role, "workflow", identity) shouldBe a[Consequence.Success[?]]
        Then("another purpose, collection or store is denied")
        records.filterNot(_._1 == role).foreach { (_, other) =>
          admitC(configuration(values), role, "workflow", other) shouldBe a[Consequence.Failure[?]]
        }
        admitC(configuration(values), role, "other", identity) shouldBe a[Consequence.Failure[?]]
        admitC(configuration(values), role, "workflow", collection("foreign")) shouldBe a[Consequence.Failure[?]]
      }
    }

    "deny missing, malformed or aliased declarations without defaults or normalization" in {
      Given("a read with no complete exact declaration")
      val identity = records.head._2
      val incomplete = values.keys.toVector.map(values.removed) :+ Map.empty[String, ConfigurationValue]
      val malformed = Vector(ConfigurationValue.StringValue(" "), ConfigurationValue.NumberValue(BigDecimal(1)),
        ConfigurationValue.BooleanValue(true), ConfigurationValue.NullValue)
        .map(value => values.updated(prefix + "store-name", value)) ++ Vector(
        values.updated(prefix + "store-name", ConfigurationValue.StringValue("Workflow")),
        values.updated(prefix + Purpose.Decision.key, values(prefix + Purpose.Selection.key)),
        values.updated(prefix + Purpose.Selection.key, ConfigurationValue.StringValue(identity.print + " ")),
        values.updated(prefix + Purpose.Selection.key, ConfigurationValue.StringValue("not a collection")))
      When("the policy is evaluated without opening any datastore")
      (incomplete ++ malformed).foreach(data => admitC(configuration(data), Purpose.Selection,
        "workflow", identity) shouldBe a[Consequence.Failure[?]])
      Then("null call scope also cannot be admitted")
      admitC(null, Purpose.Selection, "workflow", identity) shouldBe a[Consequence.Failure[?]]
      admitC(configuration(values), null, "workflow", identity) shouldBe a[Consequence.Failure[?]]
    }

    "read nested typed host configuration without consulting operation or live system settings" in {
      Given("the same explicit bindings expressed as nested host configuration")
      val children = values.map { (key, value) => key.stripPrefix(prefix) -> value }
      val nested = Map("sm-workflow" -> ConfigurationValue.ObjectValue(Map(
        "goal-phase" -> ConfigurationValue.ObjectValue(Map("host-records" -> ConfigurationValue.ObjectValue(children))))))
      When("the resolved nested snapshot admits the selected binding")
      admitC(configuration(nested), Purpose.Selection, "workflow", records.head._2) shouldBe a[Consequence.Success[?]]
      Then("an empty snapshot does not inherit that declaration")
      admitC(configuration(Map.empty), Purpose.Selection, "workflow", records.head._2) shouldBe a[Consequence.Failure[?]]
    }
  }
}
