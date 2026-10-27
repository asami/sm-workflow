package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.subsystem.GenericSubsystemDescriptor
import org.goldenport.cncf.workflow.*
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.{SmWorkflowComponent, value as V}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitPartitionFixture.take

/** A managed host may have storage/process policy without pinning any Phase
  * or Plan. Business selection below is specification input, not live Human
  * evidence. Subject calls are the actual public Start/Advance Operations.
  * @since Oct. 27, 2026
  * @version Oct. 27, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class StandardWorkflowStartOperationSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  override protected def standard_runtime: Boolean = true
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val descriptor = take(GenericSubsystemDescriptor.loadComponentArchive(Path.of("src/main/car").toAbsolutePath))
    val template = io.circe.parser.parse(descriptor.config("textus.subsystem.process-execution.plan")).toOption.get
    val sbt = sys.props.get("smworkflow.spec.sbt").orElse(sys.env.get("SM_WORKFLOW_SPEC_SBT")).map(Path.of(_))
      .orElse(sys.env.getOrElse("PATH", "").split(java.io.File.pathSeparator).iterator.filter(_.nonEmpty)
        .map(value => Path.of(value).resolve("sbt")).find(path => Files.isRegularFile(path) && Files.isExecutable(path)))
      .getOrElse(fail("The standard profile requires an installed SBT executable"))
    val values = Map("repository.root" -> root.resolve("repository").toString, "git.branch" -> "main", "git.remote" -> "origin",
      "git.remote-branch" -> "main", "git.endpoint" -> root.resolve("upstream.git").toString,
      "git.user-name" -> "Fixture", "git.user-email" -> "fixture@example.invalid",
      "executable.sbt" -> sbt.toRealPath().toString,
      "environment.path" -> sys.env.getOrElse("PATH", ""), "environment.java-home" -> sys.env.getOrElse("JAVA_HOME", ""),
      "environment.ssh-auth-sock" -> "")
    def expand(value: Json): Json = value.mapString(text => values.foldLeft(text) { case (prior,(key,value)) =>
      prior.replace("${" + key + "}", value) }).mapArray(_.map(expand)).mapObject(_.mapValues(expand))
    expand(template)
  }
  private def savedC(call: Setup, session: String): Consequence[GoalPhaseNativeRun.Payload] = for {
    catalog <- GoalPhaseRuntimeCatalog.loadC()
    storage <- WorkflowComponentStorage.bindC(ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin),
      SmWorkflowComponent.componentId, _configuration_, Vector(catalog.definition), _collection_("instances"), _collection_("continuations"))
    starts <- storage.bindMechanicalC(GoalPhaseNativeRun.startOperation, GoalPhaseNativeRun.codec)
    retained <- starts.loadRetainedC(GoalPhaseNativeRun.reference(session))(using call.executionContext)
    payload <- retained.map(value => Consequence.success(value.receipt.payload.value))
      .getOrElse(Consequence.stateConflict("No Operation-created native Start"))
  } yield payload

  "Standard runtime public Goal Operations" should {
    "reopen the same native Phase repair with its actual inventory and stage the necessary source file" in {
      object Fixture extends GoalPhaseKnownFailedPhaseValidationFixture {
        override protected def standard_runtime = true
        override protected def known_phase_checks = Vector("test")
        override protected def known_repair_limit = 1
        def plan = _recovery_plan
        override protected def goal_completion_policy = GoalPhasePostCompletionSync.localPolicy
        override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json =
          StandardWorkflowStartOperationSpec.this.process_plan(root, scenario, goal)
        def submit(episode: RecoveryEpisode, work: V.GoalPhaseAdvanceResult, value: V.GoalPhaseWorkResult, name: String): Unit =
          episode.invoke[V.GoalPhaseWorkSubmissionResult](work.submitOperation.get, List(
            Property("sessionId", work.submitSessionId.get, None), Property("result", value, None),
            Property("resultReference", V.WorkflowArtifactReference(name, "1"), None))).accepted shouldBe true
      }
      val root = Fixture.newKnownRoot("standard-inventory")
      val product = "src/test/scala/ProductCheck.scala"
      var identity = ""
      var original = Option.empty[V.GoalPhaseAdvanceResult]
      var repair = Option.empty[V.GoalPhaseAdvanceResult]
      var history = Option.empty[WorkflowInstancePersistence.InstanceRecord]
      def result(work: V.GoalPhaseAdvanceResult, outcome: String,
        proposal: Option[GoalPhasePlanRevision.Proposal] = None): V.GoalPhaseWorkResult = {
        val assigned = work.work.get.issues.toVector.flatMap(_.issues).filter(issue => issue.status != "RESOLVED")
        V.GoalPhaseWorkResult(outcome, "SPECIFICATION-OWNED necessary Phase repair", Vector.empty,
          None, None, None, None, None, None, None, None, dependencyValidation = None, diagnosticRepair = None)
          .copy(planRevision = GoalPhasePlanRevision.publicProposal(proposal), issueReport = Some(V.GoalPhaseIssueReport(
            assigned.map(issue => V.GoalPhaseIssueDisposition(issue.identity,
              if (outcome == "decision-needed") "BLOCKED" else "FIXED", "Exact assigned test correction", None)))))
      }
      Given("standard public Start, an accepted Step and a real failed whole-Phase Test in an isolated repository")
      Fixture.inKnownEpisode(root, initialize = true) { episode =>
        identity = episode.start().sessionId
        original = Some(episode.advance(identity))
        episode.submitImplementation(original.get).accepted shouldBe true
        episode.acceptStep(episode.advance(identity))
        repair = Some(episode.advance(identity))
        repair.get.work.get.kind shouldBe "RepairStep"
        repair.get.work.get.completed.map(_.step) shouldBe Vector("step-1")
        history = Some(episode.current(identity))
        val proposal = GoalPhasePlanRevision.Proposal("standard-inventory", "1", "Correct the existing product test",
          Fixture.plan.copy(
            revision = "inventory-2", phaseRepairInventory = Vector(product)))
        When("the actual assigned repair returns its exact finite inventory proposal")
        Fixture.submit(episode, repair.get, result(repair.get, "decision-needed", Some(proposal)), "standard-inventory-proposal")
      }
      Fixture.inKnownEpisode(root) { episode =>
        val continued = episode.advance(identity)
        Then("the reopened ordinary operation adopts the inventory without another Start or a new repair batch")
        continued.work.get.selectedPlan.phaseRepairInventory shouldBe Vector(product)
        continued.work.get.selectedPlan.steps shouldBe repair.get.work.get.selectedPlan.steps
        continued.work.get.originalBase shouldBe original.get.work.get.originalBase
        continued.work.get.completed shouldBe repair.get.work.get.completed
        continued.repairProgress shouldBe repair.get.repairProgress
        episode.advance(identity) shouldBe continued
        val source = root.resolve("repository").resolve(product)
        val predicate = "val ready = Files.isRegularFile(root.resolve(\"phase-ready\"))"
        Files.readString(source) should include(predicate)
        Files.writeString(source, Files.readString(source).replace(predicate, "val ready = true"))
        Fixture.submit(episode, continued, result(continued, "completed"), "standard-inventory-correction")
      }
      Fixture.inKnownEpisode(root) { episode =>
        val review = episode.advance(identity)
        Then("fresh native Test and candidate staging include the admitted file while retaining the original history and counts")
        review.work.get.kind shouldBe "ReviewPhase"
        review.work.get.review.get.changedPaths should contain(product)
        review.work.get.review.get.checks.head.exitCode shouldBe Some(0)
        review.work.get.originalBase shouldBe original.get.work.get.originalBase
        review.work.get.completed shouldBe repair.get.work.get.completed
        review.repairProgress shouldBe repair.get.repairProgress
        episode.current(identity).history.take(history.get.history.size) shouldBe history.get.history
        Fixture.phaseExecutions(root).size shouldBe 2
      }
    }

    "accept two actual Plans at Start and resume the original native run without a global runtime definition" in {
      Given("managed storage/process policy with no selected Phase or Plan at component bootstrap")
      val directory = Files.createTempDirectory(Files.createDirectories(Path.of("target/standard-start-operation").toAbsolutePath), "case-")
      val first = GoalPhaseOrderedProgression.Plan("selected-plan-7", "1", "PHASE-7", "sm-workflow", Vector(
        GoalPhaseOrderedProgression.Step("S1", "Implement the actual first behavior", Vector("src/First.scala"), Vector.empty, Vector("test"))), Vector("test"))
      val second = first.copy(identity = "selected-plan-8", phase = "PHASE-8", steps = Vector(
        first.steps.head.copy(purpose = "Implement the actual next behavior", ownedPaths = Vector("src/Next.scala"))))
      val sessions = Vector("standard-session-7", "standard-session-8")
      When("the ordinary public Start Operation receives each specification-selected business input and complete Plan")
      _with_runtime_(directory, "Goal") { (owner, component, actor, prepare) =>
        take(component.initializationParameters.resolve(ThreeWorkflowRuntimeParameters.definition)).value shouldBe None
        prepare(call => call.setupC(_setup_commands_(directory, "Goal").size, scenario = "Goal"))
        Vector(first,second).zip(sessions).foreach { (plan,session) =>
          val input = V.GoalPhaseStartInput(plan.phase, plan.repository)
          val selection = _goal_selection_(input, plan.identity, "start-" + plan.identity)
          prepare(call => for {
            selectionWire <- ProfileSelectionJsonV1.encodeStorageC(selection)
            planWire <- GoalPhaseSelectedPlanJsonV1.encodeStorageC(GoalPhaseNativeRun.SelectedPlan(plan.identity, "participant", plan))
            _ <- call.createC("goal_selection", NativeWorkflowStartBusinessKey(NativeWorkflowStartInput.Goal(input), "participant"),
              Record.data(ProfileSelectionJsonV1.storageField -> selectionWire))
            _ <- call.createC("goal_plans", plan.identity, Record.data(GoalPhaseSelectedPlanJsonV1.storageField -> planWire))
          } yield ())
          val result = read_value[V.GoalPhaseStartResult](take(owner.executeOperationResponseInContext(Request.of(
            component = component.name, service = "GoalPhaseService", operation = "startGoalPhase",
            properties = List(Property("input", input, None), Property("sessionId", session, None))), actor)))
          result.sessionId shouldBe session
          result.outcome shouldBe "started"
        }
      }
      Then("the reopened runtime retains both actual Plans and Advance issues only the first run's assigned work")
      _with_runtime_(directory, "Goal") { (owner, component, actor, prepare) =>
        Vector(first,second).zip(sessions).foreach { (plan,session) => prepare(call => savedC(call,session).map { payload =>
          payload.plan shouldBe plan
        }) }
        val next = read_value[V.GoalPhaseAdvanceResult](take(owner.executeOperationResponseInContext(Request.of(
          component = component.name, service = "GoalPhaseService", operation = "advanceGoalPhase",
          properties = List(Property("sessionId", sessions.head, None))), actor)))
        next.kind shouldBe "Request"
        next.work.get.selectedPlan.phase shouldBe first.phase
        next.work.get.selectedPlan.steps.map(_.identity) shouldBe first.steps.map(_.identity)
      }
    }
  }
}
