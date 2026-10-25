package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Path
import io.circe.Json
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{GoalPhaseStartInput, RepositorySyncStartInput}

/** Normal host-file data only; no private runtime enrollment or injected ports.
  * Real-effects scenarios must additionally declare their owned process plan.
  * @since   Oct.  7, 2026
  * @version Oct. 23, 2026
  */
private[admission] object ThreeWorkflowNormalHostFixture {
  def collection(name: String): String = EntityCollectionId("smworkflow", "cli", name).print
  // Declared input and checkpoint destinations only. This array grants no
  // issuer/participant authority and installs no row or Workflow history.
  val recordCollections: Vector[String] = (Vector("goal_plans", "host_evidence", "repository_selections",
    "split_selections", "split_contexts", "split_starts", "split_checkpoints",
    "split_humanresolutions", "split_humancheckpoints") ++
    GoalPhaseInputRecordReadPolicy.Purpose.values.map(p => "goal_" + p.toString.toLowerCase) ++
    SplitPhaseExplicitPlanningInputJsonV1.Purpose.values.map(p => "split_" + p.toString.toLowerCase))
    .map(collection)
  val storagePlan: Json = {
    val owner = SmWorkflowComponent.componentId.name
    Json.obj("schema" -> Json.fromString("cncf.subsystem-storage.v1"),
      "policy" -> Json.obj("revision" -> Json.fromString("normal-cli-v1"),
        "rules" -> Json.arr(Json.obj("eventType" -> Json.fromString("retained"),
          "mode" -> Json.fromString("persist")))),
      "persistence" -> Json.obj("storeName" -> Json.fromString("events"),
        "cursorCollection" -> Json.fromString(collection("cursor")),
        "eventCollection" -> Json.fromString(collection("events")),
        "deliveryIndexCollection" -> Json.fromString(collection("indexes")),
        "cursorEntryId" -> Json.fromString("global"),
        "workflowGrants" -> Json.arr(Json.obj("componentId" -> Json.fromString(owner),
          "storeName" -> Json.fromString("workflow"), "rootCollection" -> Json.fromString(collection("instances")),
          "continuationCollection" -> Json.fromString(collection("continuations"))))),
      "records" -> Json.fromValues(recordCollections.map(c => Json.obj("componentId" -> Json.fromString(owner),
        "storeName" -> Json.fromString("workflow"), "collection" -> Json.fromString(c)))))
  }
  def definition(root: Path): ThreeWorkflowRuntimeParameters.Definition = {
    import ThreeWorkflowRuntimeParameters.*
    Definition(schema, Target(root.toString, "main", "origin", "main", root.resolve("upstream.git").toString),
      Goal(GoalPhaseStartInput("phase-2", "sm-workflow"), Vector("docs/phase.md"),
        GoalPhaseNativeValidation.Policy("step-checks", 1, Vector(GoalPhaseNativeValidation.Check("test", 1))),
        GoalPhaseNativeValidation.Policy("phase-checks", 1, Vector(GoalPhaseNativeValidation.Check("test", 1))),
        GoalPhaseNativeReview.Policy("independent-review", 1),
        GoalPhaseNativeClosure.Policy("step-commits", 1, Vector(GoalPhaseNativeClosure.StepProgram("step-1", 1)))),
      Repository(RepositorySyncStartInput("sm-workflow", "main", "origin", "main", "owned-upstream", "FULL_STATE"),
        RepositorySyncNativeMergeValidation.Policy("repository-checks", 1,
          "required", "Run the selected product check", Vector(RepositorySyncNativeMergeValidation.Check("test", 1))),
        RepositorySyncNativeConflictInput.Policy(Set("docs/phase.md")), RepositorySyncNativeMergeReview.Policy("merge-review", 1)))
  }
  def values(root: Path): Map[String, String] = {
    val goal = GoalPhaseInputRecordReadPolicy.prefix
    val input = SplitPhasePlanningInputReadPolicy.prefix
    val split = SplitPhaseInputSelectionReadPolicy.prefix
    val context = SplitPhasePartitionContextReadPolicy.prefix
    val starts = SplitPhaseTypedStartReadPolicy.prefix
    val checkpoint = SplitPhasePartitionEnrichmentStorage.prefix
    val evidence = NativeExternalEvidence.prefix
    Map("sm-workflow.runtime.definition" -> ThreeWorkflowRuntimeParameters.encode(definition(root)).noSpaces,
      goal + "store-name" -> "workflow", goal + "plan-collection" -> collection("goal_plans"),
      evidence + "store-name" -> "workflow", evidence + "record-collection" -> collection("host_evidence"),
      evidence + "issuer" -> "normal-development-host",
      RepositorySyncInputSelectionReadPolicy.prefix + "store-name" -> "workflow",
      RepositorySyncInputSelectionReadPolicy.prefix + "selection-collection" -> collection("repository_selections"),
      input + "store-name" -> "workflow", split + "store-name" -> "workflow",
      split + "selection-collection" -> collection("split_selections"), context + "store-name" -> "workflow",
      context + "record-collection" -> collection("split_contexts"), starts + "store-name" -> "workflow",
      starts + "record-collection" -> collection("split_starts"),
      SplitPhaseManagedTypedStartAssembly.revisionKey -> "1", checkpoint + "store-name" -> "workflow",
      checkpoint + "record-collection" -> collection("split_checkpoints"),
      SplitPhaseHumanResolutionReadPolicy.prefix + "store-name" -> "workflow",
      SplitPhaseHumanResolutionReadPolicy.prefix + "record-collection" -> collection("split_humanresolutions"),
      SplitPhaseHumanResolutionCheckpointPublication.prefix + "store-name" -> "workflow",
      SplitPhaseHumanResolutionCheckpointPublication.prefix + "record-collection" -> collection("split_humancheckpoints")) ++
      GoalPhaseInputRecordReadPolicy.Purpose.values.map(p => (goal + p.key) -> collection("goal_" + p.toString.toLowerCase)) ++
      SplitPhaseExplicitPlanningInputJsonV1.Purpose.values.map(p => (input + p.key) -> collection("split_" + p.toString.toLowerCase)) ++
      Vector(SplitPhaseCandidateAllocationPolicy.prefix, SplitPhasePartitionReplyAllocationPolicy.prefix).flatMap(p =>
        Vector(p + "policy-identity" -> "normal-host", p + "policy-revision" -> "1", p + "namespace" -> "cli"))
  }

  /** Public normal-host options for an isolated Repository continuation clone.
    * A missing typed binding leaves a real dispatched validation intent without
    * manufacturing a process result; all Git programs remain ordinary typed ones.
    */
  def repositoryValidationContinuationValues(root: Path, host: ThreeWorkflowRuntimeParameters.Definition,
    executable: Path, missingBinding: Boolean): Map[String, String] = {
    val selected = host.target
    val git = TypedGitHostPlanFixture.plan(org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms.Target(
      Path.of(selected.root), selected.localBranch, selected.remote, selected.remoteBranch, selected.endpoint))
    val full = TypedSbtHostPlanFixture.plan(root, executable, Vector(1), Some(git))
      .mapObject(fields => fields.add("workspaces", fields("workspaces").get.mapArray(_.map(
        _.mapObject(_.add("root", Json.fromString(selected.root)))))))
    val plan = if (missingBinding) {
      val removedcapabilities = full.hcursor.get[Vector[Json]]("sbtPrograms").toOption.get
        .map(_.hcursor.get[Json]("capability").toOption.get).toSet
      full.mapObject(fields => fields.add("sbtPrograms", Json.arr()).add("grants",
        fields("grants").get.mapArray(_.map(_.mapObject(grant => grant.add("capabilities",
          grant("capabilities").get.mapArray(_.filterNot(removedcapabilities))))))))
    } else full
    Map("sm-workflow.runtime.definition" -> ThreeWorkflowRuntimeParameters.encode(host).noSpaces,
      "textus.subsystem.process-execution.plan" -> plan.noSpaces,
      "textus.resource.url.file.roots" -> root.toString)
  }
}
