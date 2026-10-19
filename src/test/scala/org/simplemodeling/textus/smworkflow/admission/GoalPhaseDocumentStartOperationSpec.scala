package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.nio.file.{Files, Path, StandardOpenOption}
import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowContinuation
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.host.NativeHostGoalStartInput
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseDocumentSource as D
import GoalPhaseOrderedProgression as O
import GoalPhaseAcceptanceBoundary as B
import SplitPhaseExplicitPartitionFixture.take

/** Isolated selected documents and fixed repository setup; public Start/Advance
  * own admission, document acquisition, history and the first work issue.
  * @since   Oct. 17, 2026
  * @version Oct. 17, 2026
  * @author  ASAMI, Tomoharu
  */
final class GoalPhaseDocumentStartOperationSpec extends AnyWordSpec with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  private val _source = D.Source("verification-inputs/phase.md", "verification-inputs/checklist.md")
  private val _input = V.GoalPhaseStartInput("verification-p61-document-start", "sm-workflow")

  // Only the Git observations used by Start and initial issue are installed.
  // This episode never installs or invokes an SBT program.
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowHostParameters.Goal): Json = {
    val target = Programs.Target(root.resolve("repository"), "main", "origin", "main", root.resolve("upstream.git").toString)
    val plan = TypedGitHostPlanFixture.plan(target, kinds = if (scenario == "DocumentDenied") Vector.empty else TypedGitHostPlanFixture.observations)
    if (scenario == "DocumentDenied") plan.mapObject(_.add("workspaceGrants", Json.arr())) else plan
  }

  private def _root(): Path = Files.createTempDirectory(Files.createDirectories(
    Path.of("target/sm-workflow/work/document-start").toAbsolutePath), "episode-").toRealPath()
  private def _host(root: Path): ThreeWorkflowHostParameters.Definition = {
    val host = ThreeWorkflowNormalHostFixture.definition(root)
    host.copy(goal = host.goal.copy(input = _input))
  }
  private def _plan(purpose: String): O.Plan = O.Plan("verification-document-plan", "2", _input.phaseIdentity,
    _input.repositoryIdentity, Vector(O.Step("step-1", purpose, Vector("docs/phase.md"), Vector.empty, Vector("test"))),
    Vector("test"), acceptance = Some(B.Adoption("verification-only", Vector(
      B.Condition("selected-acceptance", "step", true, "Retain the selected semantic boundary")))))
  private def _phase(plan: O.Plan): String = "# Isolated verification Phase\n```sm-workflow-plan\n" +
    Json.obj("schemaVersion" -> Json.fromString("sm-workflow.phase-plan.v1"), "plan" -> plan.asJson,
      "bindings" -> Json.arr(Json.obj("step" -> Json.fromString("step-1"),
        "items" -> Json.arr(Json.fromString("VERIFY-1"))))).spaces2 + "\n```\n"
  private def _documents(root: Path, phase: String): Unit = {
    val repository = root.resolve("repository")
    Files.writeString(repository.resolve(".git/info/exclude"), "\n/verification-inputs/\n", StandardOpenOption.APPEND)
    Files.createDirectories(repository.resolve("verification-inputs"))
    Files.writeString(repository.resolve(_source.phasePath), phase)
    Files.writeString(repository.resolve(_source.checklistPath),
      "# Verification checklist\n- [x] VERIFY-1: Retain this semantic acceptance\n  with stable membership.\n")
  }
  private def _prepared(): Record = {
    val declaration = Json.obj("schemaVersion" -> Json.fromString(D.declarationSchema),
      "phasePath" -> Json.fromString(_source.phasePath), "checklistPath" -> Json.fromString(_source.checklistPath))
    val files = Map("input.json" -> _input.asJson.noSpaces, "plan.json" -> declaration.noSpaces)
    take(NativeHostGoalStartInput.prepareC(Map("input" -> "input.json", "plan" -> "plan.json",
      "selection-id" -> "verification-document-selection", "participant" -> "participant", "origin" -> "ExplicitHuman",
      "idempotency-key" -> "verification-document-start", "selection-collection" -> "entity:smworkflow.cli.goal_selection",
      "plan-collection" -> "entity:smworkflow.cli.goal_plans"), path =>
      files.get(path).map(Consequence.success).getOrElse(Consequence.stateConflict("Unselected verification file"))))
  }
  private def _install_c(call: Setup, prepared: Record): Consequence[Unit] = {
    val entries = prepared.getVector("datastore").get.map(_.asInstanceOf[Record])
    entries.zip(Vector("goal_selection", "goal_plans")).foldLeft(Consequence.unit) { case (prior, (entry, name)) =>
      val row = entry.getVector("records").get.head.asInstanceOf[Record]
      prior.flatMap(_ => call.createC(name, row.getString("id").get, row.removeKeys(Set("id"))))
    }
  }
  private def _saved_c(call: Setup, identity: String): Consequence[GoalPhaseNativeRun.Payload] = for {
    catalog <- GoalPhaseRuntimeCatalog.loadC()
    storage <- WorkflowComponentStorage.bindC(ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin),
      SmWorkflowComponent.componentId, _configuration_, Vector(catalog.definition), _collection_("instances"), _collection_("continuations"))
    starts <- storage.bindMechanicalC(GoalPhaseNativeRun.startOperation, GoalPhaseNativeRun.codec)
    retained <- starts.loadRetainedC(WorkflowMechanicalTransition.Reference(identity + ":ordered-start", "1"))(
      using call.executionContext)
    saved <- retained.map(value => Consequence.success(value.receipt.payload.value))
      .getOrElse(Consequence.stateConflict("No Operation-created verification Start"))
  } yield saved

  "Normal GoalPhase Start with explicitly selected documents" should {
    "acquire the latest selected text and capture bindings and acceptance before issuing initial work" in {
      Given("an isolated verification Phase, passive preparation and a normal current-UoW repository fixture")
      val root = _root()
      val host = _host(root)
      val prepared = _prepared()
      val currentplan = _plan("Implement the current document text")
      _with_runtime_(root, "DocumentStart", Some(host)) { (owner, component, actor, prepare) =>
        def _invoke_[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
          read_value[A](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
            service = "GoalPhaseService", operation = operation, properties = properties), actor)))
        prepare(call => call.setupC(_setup_commands_(root, "DocumentStart").size).flatMap { _ =>
          _documents(root, _phase(_plan("Earlier passive document text")))
          _install_c(call, prepared)
        })
        var original = Option.empty[RepositorySyncManagedGitObservation.Observed]
        prepare(call => call.observeC().map(value => { original = Some(value); () }))

        When("the declared Phase document changes after preparation and the normal public Start and Advance run")
        Files.writeString(root.resolve("repository").resolve(_source.phasePath), _phase(currentplan))
        val started = _invoke_[V.GoalPhaseStartResult]("startGoalPhase", NativeWorkflowOperationInputFixture.goal(_input))
        val issued = _invoke_[V.GoalPhaseAdvanceResult]("advanceGoalPhase",
          NativeWorkflowOperationInputFixture.advance(started.sessionId))
        var saved = Option.empty[GoalPhaseNativeRun.Payload]
        var work = Option.empty[GoalPhaseNativeWork.Request]
        var instructions = Option.empty[String]
        var observed = Option.empty[RepositorySyncManagedGitObservation.Observed]
        prepare(call => for {
          payload <- _saved_c(call, started.sessionId)
          assignment <- call.goalWorkC(InstanceIdentity(started.sessionId))
          state <- call.observeC()
        } yield {
          saved = Some(payload)
          work = Some(assignment.current.asInstanceOf[WorkflowContinuation.WorkOrder[GoalPhaseNativeWork.Request]]
            .request.input.get.value)
          instructions = Some(assignment.current.asInstanceOf[WorkflowContinuation.WorkOrder[GoalPhaseNativeWork.Request]]
            .request.context.summary)
          observed = Some(state)
          ()
        })

        Then("the real issue uses the fresh Plan and original clean base, with captured acceptance in its input and context")
        issued.kind shouldBe "Request"
        work.get.purpose shouldBe "Implement the current document text"
        work.get.selectedPlan shouldBe take(B.captureC(None, currentplan))
        work.get.completed shouldBe empty
        work.get.originalBase shouldBe O.Snapshot(original.get.head, original.get.tree)
        saved.get.base shouldBe work.get.originalBase
        saved.get.plan shouldBe work.get.selectedPlan
        work.get.documents shouldBe saved.get.documents
        instructions.get should include("VERIFY-1")
        instructions.get should include("Retain this semantic acceptance with stable membership.")
        saved.get.documents shouldBe Some(D.Bound(_source,
          Vector(GoalPhaseDocumentPlan.Binding("step-1", Vector("VERIFY-1"))),
          Vector(GoalPhaseDocumentPlan.Item("VERIFY-1", "Retain this semantic acceptance with stable membership.", true))))
        saved.get.plan.effectiveAcceptance.get.conditions.head.description shouldBe "Retain the selected semantic boundary"
        observed.get.head shouldBe original.get.head
        observed.get.tree shouldBe original.get.tree
        observed.get.dirty shouldBe empty
        original.get.dirty shouldBe empty
      }
    }

    for ((scenario, failure) <- Vector("DocumentMalformed" -> "document plan", "DocumentMissing" -> "document is missing",
        "DocumentDenied" -> "workspace")) {
      s"refuse public Start for $scenario without creating a native instance" in {
        Given("only a passive verification document selection and a malformed, missing or unreadable declared source")
        val root = _root()
        val host = _host(root)
        val prepared = _prepared()
        _with_runtime_(root, scenario, Some(host)) { (owner, component, actor, prepare) =>
          prepare(call => call.setupC(_setup_commands_(root, scenario).size).flatMap { _ =>
            _documents(root, if (scenario == "DocumentMalformed") "```sm-workflow-plan\n{\n```\n" else _phase(_plan("Verification only")))
            if (scenario == "DocumentMissing") Files.delete(root.resolve("repository").resolve(_source.checklistPath))
            _install_c(call, prepared)
          })

          When("the normal public Start acquires the selected document Plan under its actual authority")
          val result = owner.executeOperationResponseInContext(Request.of(component = component.name,
            service = "GoalPhaseService", operation = "startGoalPhase",
            properties = NativeWorkflowOperationInputFixture.goal(_input)), actor)

          Then("Start reports the actual document or authority failure and yields no started session")
          result.isSuccess shouldBe false
          result.toString.toLowerCase(java.util.Locale.ROOT) should include(failure)
        }
      }
    }
  }
}
