package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Path
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms
import org.simplemodeling.textus.smworkflow.value.RepositorySyncStartInput

/**
 * @since   Oct.  6, 2026
 * @version Oct. 12, 2026
 */
final class RepositorySyncHostSelectionReadPolicySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import RepositorySyncHostSelectionReadPolicy.*
  private def collection(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "repositoryrecords", name))
  private val selections = collection("selections")
  private val values: Map[String, ConfigurationValue] = Map(
    prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
    prefix + "selection-collection" -> ConfigurationValue.StringValue(selections.print))
  private def host(data: Map[String, ConfigurationValue]) = ResolvedConfiguration(HostConfiguration(data), ConfigurationTrace.empty)
  private val runtime = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("repository-records")),
    MigrationPolicy("v1"), RetentionPolicy("v1"), LeasePolicy("v1"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("repository-records")))
  private val workflow = RepositorySyncManagedFetch.Declaration(runtime, collection("instances"), collection("continuations"),
    RepositorySyncStartInput("repository", "main", "origin", "main", "remote", "FULL_STATE"),
    RepositoryGitRuntimePrograms.Target(Path.of("/owned/repository"), "main", "origin", "main", "/owned/remote.git"))

  "RepositorySync host selection composition" should {
    "bind only a same-store separate collection and retain no execution context" in {
      Given("RepositorySync's own resolved host configuration and logical Workflow declaration")
      When("the selection reader is constructed without opening storage")
      val reader = bindC(host(values), workflow).toOption.get
      val foreignStore = bindC(host(values.updated(prefix + "store-name", ConfigurationValue.StringValue("other"))), workflow)
      val aliases = Vector(workflow.instances, workflow.continuations).map(c => bindC(host(values.updated(
        prefix + "selection-collection", ConfigurationValue.StringValue(c.print))), workflow))
      Then("composition grants no context-free read and refuses cross-purpose storage")
      reader.loadC("selection").isSuccess shouldBe false
      reader.loadForCallC("selection", null).isSuccess shouldBe false
      foreignStore.isSuccess shouldBe false
      aliases.foreach(_.isSuccess shouldBe false)
    }

    "use the current RepositorySync declaration rather than another profile or normalization" in {
      Given("absent, foreign-profile, malformed and nested resolved snapshots")
      val invalid = values.keys.toVector.map(values.removed) ++ Vector(
        values.map { (key, value) => key.replace(prefix, SplitPhaseHostSelectionReadPolicy.prefix) -> value },
        values.map { (key, value) => key.replace(prefix, GoalPhaseHostRecordReadPolicy.prefix) -> value },
        values.updated(prefix + "store-name", ConfigurationValue.StringValue("Workflow")),
        values.updated(prefix + "selection-collection", ConfigurationValue.StringValue(selections.print + " ")))
      val children = values.map { (key, value) => key.stripPrefix(prefix) -> value }
      val nested = Map("sm-workflow" -> ConfigurationValue.ObjectValue(Map("repository-sync" -> ConfigurationValue.ObjectValue(
        Map("host-records" -> ConfigurationValue.ObjectValue(children))))))
      When("the read restriction is evaluated for the requested logical binding")
      val rejected = invalid.map(data => decodeC(host(data)))
      val accepted = admitC(host(nested), "workflow", selections)
      val crossed = admitC(host(values), "workflow", collection("foreign"))
      Then("only the explicit RepositorySync binding supplies a read restriction, not a framework grant")
      rejected.foreach(_.isSuccess shouldBe false)
      accepted shouldBe a[Consequence.Success[?]]
      crossed.isSuccess shouldBe false
    }
  }
}
