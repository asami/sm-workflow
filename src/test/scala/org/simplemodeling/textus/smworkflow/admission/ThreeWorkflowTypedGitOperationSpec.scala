package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path}
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.GitOperationKind
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.protocol.Request
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms.Target
import org.simplemodeling.textus.smworkflow.value.{RepositorySyncStartInput, RepositorySyncStartResult, RepositorySyncAdvanceResult}
import RepositorySyncManagedFetch as F
import RepositorySyncManagedGitObservation as G
import RepositorySyncNativeProgressStore as N
import RepositorySyncNativeMergeValidation as V
import SplitPhaseExplicitPartitionFixture.take

/** Public Start/Advance with actual Git facts, normal V3 bootstrap and storage.
  * The explicitly selected Git-only policy has no product-test assurance.
  * Fixture authority proves behavior, not final operator/reviewer acceptance.
  * @since   Oct. 15, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class ThreeWorkflowTypedGitOperationSpec extends AnyWordSpec with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  private val _git_only = V.Policy("typed-git-only", 1, "explicit-no-product-tests",
    "This selected fixture qualifies typed Git workflow composition only; product tests remain outside its assurance.", Vector.empty)
  private def _fresh(name: String): Path = Files.createTempDirectory(Files.createDirectories(
    Path.of("target/sm-workflow/work/normal-typed-git").toAbsolutePath.normalize), name + "-")
  private def host(root: Path): ThreeWorkflowRuntimeParameters.Definition = {
    val original = ThreeWorkflowNormalHostFixture.definition(root)
    original.copy(repository = original.repository.copy(validation = _git_only))
  }
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val denied = scenario match {
      case "MissingFetchGrant" => Set(TypedGitHostPlanFixture.capability(GitOperationKind.Fetch))
      case "UnknownDenied" => Set(TypedGitHostPlanFixture.capability(GitOperationKind.CheckPendingIndex))
      case _ => Set.empty[String]
    }
    TypedGitHostPlanFixture.plan(Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString),
      kinds = TypedGitHostPlanFixture.integration :+ GitOperationKind.Push, denied = denied)
  }
  private def _fetched_c(call: Setup, input: RepositorySyncStartInput, identity: InstanceIdentity,
    root: Path, key: String = "fetch-observed"): Consequence[Option[F.Payload]] = for {
    definition <- RepositorySyncDefinitionBinding.bindC
    declaration = F.Declaration(_configuration_, _collection_("instances"), _collection_("continuations"), input,
      Target(root.resolve("repository"), "main", "origin", "main", root.resolve("upstream.git").toString))
    session <- F.sessionC(identity.value, declaration, definition, call.core)
    reader <- session.storage.bindMechanicalC(F.advanceOperation, F.codec(false))
    saved <- reader.loadRetainedC(F.reference(identity.value, key))(using call.executionContext)
  } yield saved.map(_.receipt.payload.value)

  private final case class Facts(current: InstanceRecord, fetched: F.Payload,
    integrated: N.Stage, validated: N.Stage, converged: N.Stage, observed: G.Observed, remote: String)
  private def _same_record(actual: InstanceRecord, expected: InstanceRecord): Unit = {
    // DefinitionBinding is an admitted runtime object, so compare its business
    // fields separately from the persisted record/history, not object identity.
    actual.copy(definition = expected.definition) shouldBe expected
    Vector(actual.definition.workflowIdentity, actual.definition.workflowRevision, actual.definition.sourceCorrelation,
      actual.definition.producerAbiIdentity, actual.definition.workflowAbiIdentity, actual.definition.bootstrapAbiIdentity,
      actual.definition.producerRevision) shouldBe Vector(expected.definition.workflowIdentity,
      expected.definition.workflowRevision, expected.definition.sourceCorrelation, expected.definition.producerAbiIdentity,
      expected.definition.workflowAbiIdentity, expected.definition.bootstrapAbiIdentity, expected.definition.producerRevision)
  }
  private def _same_stage(actual: N.Stage, expected: N.Stage): Unit = {
    actual.payload shouldBe expected.payload
    _same_record(actual.record, expected.record)
  }
  private def _facts_c(call: Setup, input: RepositorySyncStartInput, identity: InstanceIdentity, root: Path): Consequence[Facts] = for {
    current <- call.currentC(identity)
    fetched <- _fetched_c(call, input, identity, root).flatMap(_.map(Consequence.success)
      .getOrElse(Consequence.stateConflict("Actual public fetch result is absent")))
    integrated <- call.nativeStageC(input, identity, root, "integrated").flatMap(_.map(Consequence.success)
      .getOrElse(Consequence.stateConflict("Actual public integration is absent")))
    validated <- call.nativeStageC(input, identity, root, "integration-validated").flatMap(_.map(Consequence.success)
      .getOrElse(Consequence.stateConflict("Actual public validation is absent")))
    converged <- call.nativeStageC(input, identity, root, "converged").flatMap(_.map(Consequence.success)
      .getOrElse(Consequence.stateConflict("Actual public convergence is absent")))
    observed <- call.observeC()
    remote <- G.remoteTipC(call.core, "main")
  } yield Facts(current, fetched, integrated, validated, converged, observed, remote)

  "Public typed Git workflow composition under an explicit Git-only policy" should {
    for ((scenario, counts, method) <- Vector(("Equal", G.Counts(0, 0), "equal"),
        ("LocalAhead", G.Counts(1, 0), "local-ahead"), ("RemoteAhead", G.Counts(0, 1), "fast-forward"))) {
      s"retain actual $scenario fetch integration and validation through normal reopen without another effect" in {
        Given("G1-" + scenario + ": owned repositories with a tracked build, explicit no-product-tests policy selected before boot, and separately prepared test-owned effect authority")
        val root = _fresh(scenario.toLowerCase)
        val selectedhost = host(root)
        val input = selectedhost.repository.input
        val selection = _repository_selection_(input, "typed-git-selection", "typed-git-start")
        var identity = Option.empty[InstanceIdentity]
        var result = Option.empty[RepositorySyncAdvanceResult]
        var retained = Option.empty[Facts]
        _with_runtime_(root, scenario, Some(selectedhost)) { (owner, component, actor, prepare) =>
          prepare(call => call.setupC(_setup_commands_(root, scenario).size).flatMap(_ =>
            _prepare_input_(call, input, selection, root)))
          When("normal public Start and its separate Advance execute the real typed Git loop")
          val start = read_value[RepositorySyncStartResult](take(owner.executeOperationResponseInContext(
            Request.of(component = component.name, service = "RepositorySyncService", operation = "startRepositorySync",
              properties = NativeWorkflowOperationInputFixture.repository(input)), actor)))
          identity = Some(InstanceIdentity(start.sessionId))
          result = Some(read_value[RepositorySyncAdvanceResult](take(owner.executeOperationResponseInContext(
            Request.of(component = component.name, service = "RepositorySyncService", operation = "advanceRepositorySync",
              properties = NativeWorkflowOperationInputFixture.advance(start.sessionId)), actor))))
          prepare(call => _facts_c(call, input, identity.get, root).map(value => { retained = Some(value); () }))
          Then("the same workflow stores actual Git facts and reaches authorized convergence without executing the tracked SBT build")
          Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
          val facts = retained.get
          facts.fetched.stage shouldBe "Fetched"
          facts.fetched.after.get.counts shouldBe Some(counts)
          facts.fetched.effect shouldBe Some("executed")
          facts.integrated.payload.stage shouldBe "Integrated"
          facts.integrated.payload.method shouldBe Some(method)
          facts.validated.payload.stage shouldBe "IntegrationValidated"
          val state = facts.validated.payload.validation.get
          state.mode shouldBe "explicit-no-product-tests"
          state.reason shouldBe _git_only.reason
          state.selected shouldBe empty
          state.receipts shouldBe Vector(V.Receipt("index-local", "exited", Some(0), "", ""))
          facts.converged.payload.stage shouldBe "Converged"
          facts.current.lifecycle shouldBe Lifecycle.Completed
          facts.observed.counts shouldBe Some(G.Counts(0, 0))
          facts.observed.dirty shouldBe empty
          facts.observed.trackingTip shouldBe Some(facts.observed.head)
          facts.remote shouldBe facts.observed.head
          if (scenario == "RemoteAhead") facts.integrated.payload.after.get.head shouldBe facts.fetched.after.get.trackingTip.get
          else facts.integrated.payload.after.get.head shouldBe facts.fetched.after.get.head
          result.get.kind shouldBe "Terminal"
          result.get.convergence.get.tip shouldBe facts.observed.head
          result.get.convergence.get.tree shouldBe facts.observed.tree
        }
        val headlog = Files.readString(root.resolve("repository/.git/logs/HEAD"))
        Given("the original ordinary runtime has closed with its real result and history retained")
        _with_runtime_(root, scenario, Some(selectedhost)) { (owner, component, actor, prepare) =>
          When("public Advance runs again in a fresh normal runtime without setup or new authority preparation")
          val reopened = read_value[RepositorySyncAdvanceResult](take(owner.executeOperationResponseInContext(
            Request.of(component = component.name, service = "RepositorySyncService", operation = "advanceRepositorySync",
              properties = NativeWorkflowOperationInputFixture.advance(identity.get.value)), actor)))
          var observed = Option.empty[Facts]
          prepare(call => _facts_c(call, input, identity.get, root).map(value => { observed = Some(value); () }))
          Then("the public result, durable stages, history and Git state are unchanged without repeated effects or an SBT launch")
          reopened shouldBe result.get
          val actual = observed.get
          val previous = retained.get
          _same_record(actual.current, previous.current)
          actual.fetched shouldBe previous.fetched
          _same_stage(actual.integrated, previous.integrated)
          _same_stage(actual.validated, previous.validated)
          _same_stage(actual.converged, previous.converged)
          actual.observed shouldBe previous.observed
          actual.remote shouldBe previous.remote
          Files.readString(root.resolve("repository/.git/logs/HEAD")) shouldBe headlog
          Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
        }
      }
    }

    "refuse missing current Fetch process authority before mutation and confirmed progress" in {
      Given("G2: a normal Git-only host with a tracked build, workspace Manage and no current Fetch process grant")
      val root = _fresh("missing-grant")
      val selectedhost = host(root)
      val input = selectedhost.repository.input
      val selection = _repository_selection_(input, "typed-git-missing-grant", "typed-git-missing-start")
      _with_runtime_(root, "MissingFetchGrant", Some(selectedhost)) { (owner, component, actor, prepare) =>
        prepare(call => call.setupC(4).flatMap(_ => _prepare_input_(call, input, selection, root, provisionEffects = false)))
        var before = Option.empty[G.Observed]
        prepare(call => call.observeC().map(value => { before = Some(value); () }))
        val start = read_value[RepositorySyncStartResult](take(owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "RepositorySyncService", operation = "startRepositorySync",
            properties = NativeWorkflowOperationInputFixture.repository(input)), actor)))
        When("public Advance reaches the saved fetch intent under the current denied grant")
        val refused = owner.executeOperationResponseInContext(Request.of(component = component.name,
          service = "RepositorySyncService", operation = "advanceRepositorySync",
          properties = NativeWorkflowOperationInputFixture.advance(start.sessionId)), actor)
        Then("the call refuses without an SBT launch or confirmed Fetch, integration or validation")
        refused.isSuccess shouldBe false
        val identity = InstanceIdentity(start.sessionId)
        prepare(call => for {
          after <- call.observeC()
          current <- call.currentC(identity)
          completed <- _fetched_c(call, input, identity, root)
          integrated <- call.nativeStageC(input, identity, root, "integrated")
          validated <- call.nativeStageC(input, identity, root, "integration-validated")
          _ = {
            after shouldBe before.get
            after.trackingTip shouldBe None
            current.lifecycle shouldBe Lifecycle.Active
            completed shouldBe None
            integrated shouldBe None
            validated shouldBe None
          }
        } yield ())
        Files.exists(root.resolve("repository/.git/FETCH_HEAD")) shouldBe false
        Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
      }
    }

    "retain an interrupted actual Git check intent across reopen without replay or a fabricated receipt" in {
      Given("G3: a public Git-only workflow with a tracked build and the existing completion-window hook interrupting after the structural intent")
      val root = _fresh("unknown")
      val selectedhost = host(root)
      val input = selectedhost.repository.input
      val selection = _repository_selection_(input, "typed-git-unknown", "typed-git-unknown-start")
      var identity = Option.empty[InstanceIdentity]
      var retained = Option.empty[N.Stage]
      var current = Option.empty[InstanceRecord]
      var observed = Option.empty[G.Observed]
      val interrupt: String => Consequence[Unit] = stage =>
        if (stage == "merge-check-prepared:index-local") Consequence.stateConflict("Specification interrupts the actual completion window")
        else Consequence.unit
      _with_runtime_(root, "Unknown", Some(selectedhost), nativeAcknowledgement = Some(interrupt)) {
        (owner, component, actor, prepare) =>
        prepare(call => call.setupC(4).flatMap(_ => _prepare_input_(call, input, selection, root)))
        val start = read_value[RepositorySyncStartResult](take(owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "RepositorySyncService", operation = "startRepositorySync",
            properties = NativeWorkflowOperationInputFixture.repository(input)), actor)))
        identity = Some(InstanceIdentity(start.sessionId))
        When("public Advance saves its genuine check intent and is interrupted before check completion")
        val outcome = owner.executeOperationResponseInContext(Request.of(component = component.name,
          service = "RepositorySyncService", operation = "advanceRepositorySync",
          properties = NativeWorkflowOperationInputFixture.advance(start.sessionId)), actor)
        Then("the real saved intent has no result, validation completion or SBT launch and remains uncertain")
        outcome.isSuccess shouldBe false
        Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
        prepare(call => for {
          intent <- call.nativeStageC(input, identity.get, root, "validation-intent-0")
          result <- call.nativeStageC(input, identity.get, root, "validation-result-0")
          validated <- call.nativeStageC(input, identity.get, root, "integration-validated")
          record <- call.currentC(identity.get)
          actual <- call.observeC()
          _ = {
            intent should not be empty
            intent.get.payload.validation.get.receipts shouldBe empty
            result shouldBe None
            validated shouldBe None
            retained = intent
            current = Some(record)
            observed = Some(actual)
          }
        } yield ())
      }
      Given("a fresh normal runtime with the completion hook removed and the check process grant now absent")
      _with_runtime_(root, "UnknownDenied", Some(selectedhost)) { (owner, component, actor, prepare) =>
        When("public Advance encounters that saved actual intent rather than launching a check again")
        val wait = read_value[RepositorySyncAdvanceResult](take(owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "RepositorySyncService", operation = "advanceRepositorySync",
            properties = NativeWorkflowOperationInputFixture.advance(identity.get.value)), actor)))
        Then("uncertainty remains Wait with the same history facts and no receipt, confirmed validation or SBT launch")
        wait.kind shouldBe "Wait"
        Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
        prepare(call => for {
          intent <- call.nativeStageC(input, identity.get, root, "validation-intent-0")
          result <- call.nativeStageC(input, identity.get, root, "validation-result-0")
          validated <- call.nativeStageC(input, identity.get, root, "integration-validated")
          record <- call.currentC(identity.get)
          actual <- call.observeC()
          _ = {
            intent should not be empty
            _same_stage(intent.get, retained.get)
            result shouldBe None
            validated shouldBe None
            _same_record(record, current.get)
            actual shouldBe observed.get
          }
        } yield ())
      }
    }
  }
}
