package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.{Json, parser}
import java.nio.file.{Files, Path}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceIdentity
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.goldenport.record.io.RecordDecoder
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import StandaloneFixExecution as S
import StandaloneFixExecution.given
import SplitPhaseExplicitPartitionFixture.take

/** Shared request data only; every observation in these suites is
  * SPECIFICATION-OWNED and establishes no actual chat delivery.
  */
private[admission] object DelegateTaskRequesterRoutingFixture extends Matchers {
  def input(requestIdentity: String): V.DelegateTaskInput = V.DelegateTaskInput(
    "goldenport-cncf", requestIdentity, "Retain the authorized destination repair and its real delivery requirements",
    NonEmptyVector.of("src/main/scala/destination", "src/test/scala/destination"),
    NonEmptyVector.of("destination-compile", "destination-focused"), NonEmptyVector.of("destination-behavior"),
    NonEmptyVector.of(V.WorkflowArtifactReference("SPECIFICATION-OWNED-source", "1")), true, "code", "required", 7,
    "direct-instruction", Vector("existing-destination-dependency"),
    V.DelegateTaskContract("SPECIFICATION-OWNED-requester", "SPECIFICATION-OWNED-destination", "SPECIFICATION-OWNED-requester",
      "org.goldenport.Cncf", "requester", "localized",
      NonEmptyVector.of("dispatch", "implement", "validate", "commit", "review", "return", "adopt", "publishLocal"),
      Vector("remote-push", "consumer-activation"),
      NonEmptyVector.of(V.WorkflowArtifactReference("SPECIFICATION-OWNED-authorization", "1")),
      Some("destination-only-publication"), Vector("org.goldenport:goldenport-cncf_3:0.1.0-SNAPSHOT"),
      "Return actual destination checks, review, commit and publication; consumer activation remains separate"), Vector.empty)

  def state(view: V.DelegateTaskView): S.State = {
    val json = parser.parse(take(view.state.get.toJsonStringC)).toOption.get
    GoalPhaseNativeMetadata.normalizePublicJson(json).toOption.get.as[S.State].toOption.get
  }

  def assertNoExecution(state: S.State): Unit = {
    state.selected shouldBe Vector.empty
    state.candidate shouldBe None
    state.receipts shouldBe Vector.empty
    state.pendingCheck shouldBe None
    state.semanticReference shouldBe None
    state.reviewReference shouldBe None
    state.priorReview shouldBe None
    state.conditionReport shouldBe Vector.empty
    state.commit shouldBe None
    state.commitExecution shouldBe None
    state.commitReconciliation shouldBe None
    state.delivery.flatMap(_.publication) shouldBe None
    state.automaticCount shouldBe 0
    state.ordinaryCount shouldBe 0
  }
}

/** Cheap pure admission and managed public operations; no destination program
  * or SBT program is installed or executed by this specification.
  * @since   Nov.  5, 2026
  * @version Nov.  6, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class DelegateTaskRequesterRoutingSpec extends AnyWordSpec with GivenWhenThen with ThreeWorkflowManagedRepositoryFixture {
  import DelegateTaskRequesterRoutingFixture.{input, state, assertNoExecution}

  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json =
    TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString), kinds = TypedGitHostPlanFixture.observations)

  "Requester role admission" should {
    "retain complete foreign destination requirements without resolving or inventing execution facts" in {
      Given("an exact requester code contract naming checks and publication absent from its caller")
      val request = take(DelegateTask.inputC(input("pure-requester")))
      When("the existing delegate owner admits metadata with no concrete execution mapping or origin")
      val preflight = DelegateTaskNativeRun.preflightExecutionC(request, None, None)
      val retained = take(DelegateTask.startRequesterC(request, "participant"))
      Then("the existing State retains all original obligations and only the dispatch frontier")
      preflight.isSuccess shouldBe true
      retained.input shouldBe request
      retained.participant shouldBe "participant"
      retained.stage shouldBe "DispatchReady"
      retained.generation shouldBe 1L
      retained.repairControl shouldBe GoalPhaseRuntimeRepairControl.Context(GoalPhaseRuntimeRepairControl.version, 7)
      retained.delivery shouldBe Some(DelegateTask.Delivery())
      assertNoExecution(retained)
      DelegateTask.selectChecksC(request, Vector.empty).isSuccess shouldBe false
      S.startC(request, "participant", Vector.empty).isSuccess shouldBe false
    }

    "keep concrete execution admission strict for destination and sibling entries and refuse malformed requester inputs" in {
      Given("the same real code request and an existing valid profile/origin pair")
      val request = take(DelegateTask.inputC(input("strict-role")))
      val mapping = take(BoundedWorkExecutionAdmissionFixture.mappingC())
      val origin = BoundedWorkExecutionAdmissionFixture.origin
      When("a role or configuration cannot satisfy the exact requester-only admission")
      val destination = request.copy(delegation = request.delegation.map(_.copy(role = "destination")))
      val goal = request.copy(delegation = None)
      val fix = goal.copy(task = None)
      Then("destination and ordinary code entries still require concrete mappings and optional requester configuration remains paired")
      DelegateTaskNativeRun.preflightExecutionC(destination, None, None).isSuccess shouldBe false
      GoalTaskNativeRun.preflightExecutionC(goal, None, None).isSuccess shouldBe false
      StandaloneFixNativeRun.preflightExecutionC(fix, None, None).isSuccess shouldBe false
      GoalTaskNativeRun.preflightExecutionC(request, None, None).isSuccess shouldBe false
      StandaloneFixNativeRun.preflightExecutionC(request, None, None).isSuccess shouldBe false
      DelegateTaskNativeRun.preflightExecutionC(request, Some(mapping), None).isSuccess shouldBe false
      DelegateTaskNativeRun.preflightExecutionC(request, None, Some(origin)).isSuccess shouldBe false
      DelegateTaskNativeRun.preflightExecutionC(request, Some(mapping), Some(origin.copy(current = null))).isSuccess shouldBe false
      DelegateTaskNativeRun.preflightExecutionC(request, Some(mapping), Some(origin)).isSuccess shouldBe true
      DelegateTask.startRequesterC(destination, "participant").isSuccess shouldBe false
      Vector("", " participant", "participant\n").foreach { participant =>
        DelegateTask.startRequesterC(request, participant).isSuccess shouldBe false
      }
      val malformed = Vector(
        request.copy(delegation = request.delegation.map(_.copy(role = "requester "))),
        request.copy(delegation = request.delegation.map(c => c.copy(destinationIdentity = c.requesterIdentity))),
        request.copy(delegation = request.delegation.map(_.copy(authorizationReferences = Vector.empty))),
        request.copy(conditions = Vector.empty))
      malformed.foreach { value =>
        DelegateTask.startRequesterC(value, "participant").isSuccess shouldBe false
        DelegateTaskNativeRun.preflightExecutionC(value, None, None).isSuccess shouldBe false
      }

      Given("requester requests whose documentation kind and validation scope disagree in either direction")
      val mismatches = Vector(
        request.copy(delegation = request.delegation.map(_.copy(validationScope = "documentation"))),
        request.copy(kind = "documentation"))
      When("requester Start and passive preflight assess their existing kind and scope policy")
      val refusals = mismatches.map(value =>
        DelegateTask.startRequesterC(value, "participant").isSuccess ->
          DelegateTaskNativeRun.preflightExecutionC(value, None, None).isSuccess)
      Then("both admission paths refuse each contradictory request without destination check resolution")
      refusals shouldBe Vector(false -> false, false -> false)

      Given("a requester documentation request with the matching documentation validation scope")
      val documentation = request.copy(kind = "documentation",
        delegation = request.delegation.map(_.copy(validationScope = "documentation")))
      When("the same requester admission paths assess that consistent request without execution mapping")
      val documentationpreflight = DelegateTaskNativeRun.preflightExecutionC(documentation, None, None)
      val documentationstate = take(DelegateTask.startRequesterC(documentation, "participant"))
      Then("both paths admit its complete metadata and retain a dispatch state with no execution facts")
      documentationpreflight.isSuccess shouldBe true
      documentationstate.input shouldBe documentation
      documentationstate.stage shouldBe "DispatchReady"
      assertNoExecution(documentationstate)
    }
  }

  "Managed requester public operations" should {
    "retain dispatch and wait with caller-only Git facts while preserving unrelated staged and unstaged work" in {
      Given("a SPECIFICATION-OWNED caller repository with no destination checkout, mapping, checks or publication target")
      val root = Files.createTempDirectory(Files.createDirectories(
        Path.of("target/sm-workflow/work/delegate-requester").toAbsolutePath), "caller-").toRealPath()
      val request = input("managed-requester")
      val adopted = take(DelegateTask.inputC(request))
      val inputfile = root.resolve("request.json")
      Files.writeString(inputfile, take(request.toRecord().toJsonStringC))
      _with_runtime_(root, "UpToDate", hostParameters = Map("textus.resource.url.file.roots" -> root.toString)) {
        (owner, component, actor, prepare) =>
        prepare(_.setupC(_setup_commands_(root, "UpToDate").size))
        val repository = root.resolve("repository")
        val stagedfile = repository.resolve("unrelated-staged.txt")
        val dirtyfile = repository.resolve("unrelated-unstaged.txt")
        Files.writeString(stagedfile, "SPECIFICATION-OWNED staged bytes\n")
        take(_run_setup_command_(Vector("-C", repository.toString, "add", "--", "unrelated-staged.txt")))
        Files.writeString(stagedfile, "SPECIFICATION-OWNED additional unstaged bytes\n")
        Files.writeString(dirtyfile, "SPECIFICATION-OWNED untracked bytes\n")
        val indexbytes = Files.readAllBytes(repository.resolve(".git/index")).toVector
        var before = Option.empty[RepositorySyncManagedGitObservation.Observed]
        prepare(_.observeC().map(value => { before = Some(value); () }))
        def _invoke_(service: String, operation: String, properties: List[Property]) =
          owner.executeOperationResponseInContext(Request.of(component = component.name, service = service,
            operation = operation, properties = properties), actor)
        def _prepare_(selection: String, value: V.DelegateTaskInput): Unit = {
          Files.writeString(inputfile, take(value.toRecord().toJsonStringC))
          val response = take(_invoke_("NativeWorkflowPreparationService", "prepareDelegateTaskStart",
            List(Property("input", V.WorkflowDelegateTaskStartPreparationInput(inputfile.toUri.toASCIIString,
              selection, "participant", "ExplicitHuman", "requester-key", "entity:smworkflow.cli.goal_selection"), None))))
          val prepared = response match {
            case OperationResponse.Json(value) => take(new RecordDecoder().json(value.noSpaces))
            case other => fail("Expected passive preparation import: " + other)
          }
          val row = prepared.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
          prepare(_.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id"))))
        }
        def _start_(value: V.DelegateTaskInput = request, selection: String = "requester-selection", key: String = "requester-key") =
          _invoke_("DelegateTaskService", "startDelegateTask", List(Property("input", value, None),
            Property("selectionId", selection, None), Property("participant", "participant", None), Property("idempotencyKey", key, None)))
        def _advance_(session: String) = read_value[V.DelegateTaskView](take(_invoke_("DelegateTaskService", "advanceDelegateTask",
          List(Property("sessionId", session, None)))))
        def _observe_(session: String, event: String, result: Option[Record] = None, artifact: Option[Record] = None) =
          _invoke_("DelegateTaskDeliveryService", "observeDelegateTaskDelivery", List(Property("input",
            V.DelegateTaskObservationInput(session, event, V.WorkflowArtifactReference("SPECIFICATION-OWNED-" + event, "1"),
              Some("SPECIFICATION-OWNED-destination-session"), result, artifact,
              "SPECIFICATION-OWNED observation; no genuine chat delivery or execution is claimed"), None)))

        When("passive preparation is independently provisioned and normal Start admits the exact requester selection")
        _prepare_("requester-selection", request)
        val started = read_value[V.DelegateTaskView](take(_start_()))
        Then("the requester has its canonical Started Session")
        started.kind shouldBe "Started"
        When("normal Advance collects that requester's dispatch frontier")
        val dispatched = _advance_(started.sessionId)
        Then("Dispatch retains the complete destination request without issuing semantic work")
        dispatched.kind shouldBe "Dispatch"
        state(dispatched).input shouldBe adopted
        assertNoExecution(state(dispatched))
        dispatched.work shouldBe None
        dispatched.issuedWorkContext shouldBe None
        When("the exact requester Start is redelivered")
        val redelivered = read_value[V.DelegateTaskView](take(_start_()))
        Then("the original Session and dispatch state are retained")
        redelivered.sessionId shouldBe started.sessionId
        redelivered.kind shouldBe "Retained"
        state(redelivered) shouldBe state(dispatched)
        When("Start is attempted with a different idempotency key")
        val changedkey = _start_(key = "changed-idempotency-key")
        Then("the changed key cannot replace the retained request")
        changedkey.isSuccess shouldBe false
        Given("a separately prepared selection that changes the original destination owner")
        val crossed = request.copy(delegation = request.delegation.copy(destinationIdentity = "SPECIFICATION-OWNED-other-destination"))
        _prepare_("crossed-selection", crossed)
        When("that crossed selection is submitted to Start")
        val crossedstart = _start_(crossed, "crossed-selection")
        Then("the changed owner is refused")
        crossedstart.isSuccess shouldBe false
        When("adoption is attempted before any destination result")
        val prematureadoption = _observe_(started.sessionId, "adopt", artifact = Some(Record.data("sourceCommit" -> "unverified")))
        Then("the premature adoption is refused")
        prematureadoption.isSuccess shouldBe false
        When("the specification-owned caller records its attributable dispatch observation")
        val waiting = read_value[V.DelegateTaskView](take(_observe_(started.sessionId, "dispatch-observed")))
        Then("the requester waits for an actual destination result")
        state(waiting).stage shouldBe "AwaitResult"
        When("Advance collects the observed dispatch")
        val waitcollection = _advance_(started.sessionId)
        Then("collection returns Wait without destination execution")
        waitcollection.kind shouldBe "Wait"
        When("an unfinished requester state is offered as a producer result and adoption description is requested")
        val prematureresult = _observe_(started.sessionId, "result-received", result = waiting.state)
        val prematuredescription = _invoke_("DelegateTaskDeliveryService", "describeDelegateTaskAdoption", List(Property("sessionId", started.sessionId, None)))
        Then("neither operation accepts that missing producer result")
        prematureresult.isSuccess shouldBe false
        prematuredescription.isSuccess shouldBe false
        When("Advance recollects the requester after those refusals")
        val retained = _advance_(started.sessionId)
        Then("the original waiting state is unchanged")
        state(retained) shouldBe state(waiting)
        When("the original requester Start is redelivered while waiting")
        val waitingredelivery = read_value[V.DelegateTaskView](take(_start_()))
        Then("redelivery preserves the waiting state and its empty execution facts")
        state(waitingredelivery) shouldBe state(waiting)
        assertNoExecution(state(retained))
        retained.work shouldBe None
        retained.issuedWorkContext shouldBe None

        When("the native payload and caller repository are read through their managed owners")
        prepare { call => for {
          definition <- DelegateTaskRuntimeCatalog.loadC()
          h = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
            "sm-workflow", repository.toString, "main", Vector("docs/phase.md"), Vector.empty, None, None, None, None, None)
          store <- DelegateTaskNativeRun.storageC(h, definition, call.core)
          current <- call.delegateCurrentC(InstanceIdentity(started.sessionId))
          payload <- DelegateTaskNativeRun.loadPayloadC(store, started.sessionId, current.revision.value, call.core)
          observed <- call.observeC()
        } yield {
          Then("the payload contains only caller Git facts and the caller files and index remain unchanged")
          current.suspension shouldBe None
          payload.root shouldBe repository.toString
          payload.branch shouldBe before.get.branch
          payload.baseHead shouldBe before.get.head
          payload.unrelated shouldBe Some(before.get.dirty)
          payload.implementationWorker shouldBe None
          payload.implementationWorkers shouldBe None
          payload.hygiene shouldBe None
          payload.records shouldBe None
          observed shouldBe before.get
          ()
        }}
        Files.readString(stagedfile) shouldBe "SPECIFICATION-OWNED additional unstaged bytes\n"
        Files.readString(dirtyfile) shouldBe "SPECIFICATION-OWNED untracked bytes\n"
        Files.readAllBytes(repository.resolve(".git/index")).toVector shouldBe indexbytes
        Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
        Files.exists(root.resolve("publication-count.txt")) shouldBe false
      }
    }
  }
}
