package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Path
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms
import org.simplemodeling.textus.smworkflow.value.GoalPhaseStartInput

/**
 * @since   Oct.  6, 2026
 * @version Oct. 23, 2026
 */
final class GoalPhaseNativeInputReadPolicySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def collection(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "goalinputs", name))
  private val prefix = GoalPhaseInputRecordReadPolicy.prefix
  private val values: Map[String, ConfigurationValue] = Map(prefix + "store-name" -> ConfigurationValue.StringValue("workflow")) ++
    GoalPhaseInputRecordReadPolicy.Purpose.values.map(p => prefix + p.key -> ConfigurationValue.StringValue(collection(p.toString.toLowerCase).print)) ++
    Map(GoalPhaseNativeInputReadPolicy.planKey -> ConfigurationValue.StringValue(collection("plans").print))
  private def host(data: Map[String, ConfigurationValue]) = ResolvedConfiguration(HostConfiguration(data), ConfigurationTrace.empty)
  private val input = GoalPhaseStartInput("PHASE-2", "repository")
  private val configuration = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("goal-inputs")),
    MigrationPolicy("v1"), RetentionPolicy("v1"), LeasePolicy("v1"), DeliveryConfiguration.SameStore(WorkflowStoreIdentity("goal-inputs")))
  private val workflow = GoalPhaseNativeRun.Declaration(configuration, collection("instances"), collection("continuations"), input,
    RepositoryGitRuntimePrograms.Target(Path.of("/owned/repository"), "main", "origin", "main", "/owned/remote.git"))
  private val plan = GoalPhaseOrderedProgression.Plan("plan", "1", input.phaseIdentity, input.repositoryIdentity,
    Vector(GoalPhaseOrderedProgression.Step("S1", "Implement the selected boundary", Vector("one.scala"), Vector.empty, Vector("focused"))),
    Vector("whole-phase"))
  private val selected = GoalPhaseNativeRun.SelectedPlan("selection", "participant", plan)

  "Native GoalPhase selected input composition" should {
    "read explicit same-store separate purposes without granting context-free access" in {
      Given("the resolved host declarations and actual logical native Workflow declaration")
      When("normal input readers are bound without opening a physical store")
      val readers = GoalPhaseNativeInputReadPolicy.bindC(host(values), workflow).toOption.get
      val aliases = Vector(workflow.instances, workflow.continuations, collection("selection"))
        .map(c => GoalPhaseNativeInputReadPolicy.bindC(host(values.updated(GoalPhaseNativeInputReadPolicy.planKey,
          ConfigurationValue.StringValue(c.print))), workflow))
      val foreign = GoalPhaseNativeInputReadPolicy.bindC(host(values.updated(prefix + "store-name", ConfigurationValue.StringValue("other"))), workflow)
      Then("no reader retains a call or opens a pool, and crossed purposes cannot supply input")
      readers.selections.loadC("selection").isSuccess shouldBe false
      readers.plans.loadForCallC("selection", null).isSuccess shouldBe false
      aliases.foreach(_.isSuccess shouldBe false)
      foreign.isSuccess shouldBe false
    }

    "require only the additional native Plan setting while preserving the legacy declaration" in {
      Given("legacy host settings, malformed native settings and an equivalent nested snapshot")
      val legacy = values.removed(GoalPhaseNativeInputReadPolicy.planKey)
      val nested = Map("sm-workflow" -> ConfigurationValue.ObjectValue(Map("goal-phase" -> ConfigurationValue.ObjectValue(
        Map("input-records" -> ConfigurationValue.ObjectValue(values.map((key, value) => key.stripPrefix(prefix) -> value)))))))
      When("legacy and native read contracts consume their selected declarations")
      val legacyResult = GoalPhaseInputRecordReadPolicy.decodeC(host(legacy))
      val absent = GoalPhaseNativeInputReadPolicy.decodeC(host(legacy))
      val malformed = GoalPhaseNativeInputReadPolicy.decodeC(host(values.updated(GoalPhaseNativeInputReadPolicy.planKey,
        ConfigurationValue.StringValue(collection("plans").print + " "))))
      val accepted = GoalPhaseNativeInputReadPolicy.decodeC(host(nested))
      Then("legacy inputs remain compatible, but native Start has no implicit collection fallback")
      legacyResult shouldBe a[Consequence.Success[?]]
      absent.isSuccess shouldBe false
      malformed.isSuccess shouldBe false
      accepted.toOption.get.plans shouldBe collection("plans")
    }

    "decode consumed selected data without a wire certificate and leave Plan meaning to progression" in {
      Given("selected data with inert annotations, equivalent optional presentation and a crossed dependency")
      val raw = GoalPhaseSelectedPlanJsonV1.encodeC(selected).toOption.get
      val json = io.circe.parser.parse(raw).toOption.get
      val payload = json.hcursor.downField("payload").focus.get
      val extra = json.mapObject(_.add("diagnostic", Json.True).add("payload",
        payload.mapObject(_.add("command", Json.fromString("sbt")).add("input", Json.True))))
      val planJson = payload.hcursor.downField("plan").focus.get
      val optional = json.mapObject(_.add("payload", payload.mapObject(_.add("plan",
        planJson.mapObject(_.add("phaseReviewClassification", Json.Null).add("note", Json.True))))))
      val incomplete = json.mapObject(_.add("payload", payload.mapObject(_.add("plan", planJson.mapObject(_.remove("repairLimit"))))))
      val wrongtype = json.mapObject(_.add("payload", payload.mapObject(_.add("plan", Json.True))))
      val crossed = selected.copy(plan = plan.copy(steps = plan.steps.map(_.copy(dependencies = Vector("S2")))))
      When("typed data is decoded once and the actual progression consumer interprets its Plan")
      val reformatted = GoalPhaseSelectedPlanJsonV1.decodeC(json.spaces2)
      val stored = GoalPhaseSelectedPlanJsonV1.decodeStorageC(GoalPhaseSelectedPlanJsonV1.encodeStorageC(selected).toOption.get)
      val annotated = Vector(extra, optional).map(value => GoalPhaseSelectedPlanJsonV1.decodeC(value.noSpaces))
      val refused = Vector(incomplete.noSpaces, wrongtype.noSpaces, "not-json").map(GoalPhaseSelectedPlanJsonV1.decodeC)
      val crosseddata = GoalPhaseSelectedPlanJsonV1.decodeC(GoalPhaseSelectedPlanJsonV1.encodeC(crossed).toOption.get)
      val frontier = GoalPhaseOrderedProgression.startC(crosseddata.toOption.get.plan,
        GoalPhaseOrderedProgression.Snapshot("a" * 40, "b" * 40))
      Then("unused fields give no control, required data is decoded, and invalid dependencies cannot issue work")
      reformatted.toOption.get shouldBe selected
      stored.toOption.get shouldBe selected
      annotated.foreach(_.toOption.get shouldBe selected)
      refused.foreach(_.isSuccess shouldBe false)
      crosseddata.toOption.get shouldBe crossed
      frontier.isSuccess shouldBe false
      GoalPhaseSelectedPlanJsonV1.decodeStorageC(raw).isSuccess shouldBe false
    }
  }
}
