package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.cli.{CncfRuntime, RunMode}
import org.goldenport.cncf.component.{Component, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.cncf.workflow.{CandidateAdmissionProducerAbi, CandidateWorkflowAbi, StateMachineOperationIdentity, WorkflowInstancePersistence}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowContinuation
import org.goldenport.cncf.workflow.WorkflowWaitJsonV1
import org.goldenport.configuration.{Configuration as GenericConfiguration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.protocol.{Argument, Protocol, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec as spec
import org.scalatest.matchers.should.Matchers
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{GoalPhaseStartCommand, GoalPhaseStartInput}

/**
 * @since   Oct.  6, 2026
 * @version Oct.  8, 2026
 */
final class GoalPhaseStartExecutionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import ProfileStartAdmission.*

  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"GoalPhase Start fixture failed: $failure")
  }

  private def resource(path: String): String = {
    val source = scala.io.Source.fromInputStream(getClass.getResourceAsStream(path), "UTF-8")
    try source.mkString.stripSuffix("\n") finally source.close()
  }

  private val sidecar = take(CandidateAdmissionProducerAbi.parseC(
    resource("/workflow/goal-phase-candidate-admission-producer-abi.json")))
  private val workflow = take(CandidateWorkflowAbi.parseC(
    resource("/workflow/goal-phase-workflow-abi.json"), sidecar, "src/main/cozy/sm-workflow.cml"))
  private val definition = take(WorkflowInstancePersistence.bindCandidateDefinitionC(workflow))
  private val configuration = Configuration(
    NamedWorkflowStore("workflow-primary", WorkflowStoreIdentity("workflow-store")),
    MigrationPolicy("fixture-v1"), RetentionPolicy("fixture-v1"), LeasePolicy("fixture-v1"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("workflow-store"))
  )
  private val command = GoalPhaseStartCommand(
    GoalPhaseStartInput("77.1", "cncf"), "selection-1", "participant-1", "start-1")
  private val human = Selection(
    "selection-1", StateMachineOperationIdentity("GoalPhaseService", "startGoalPhase"),
    WorkflowDefinitionIdentity("GoalPhaseWorkflow"),
    WorkflowDefinitionRevision("goal-phase-ordered-run-v4"),
    "participant-1", SelectionOrigin.ExplicitHuman, "start-1", None,
    Some(GoalPhaseStartCommandAdapter.selectedInput(command.input))
  )

  private final class Fixture {
    var selected: Option[Selection] = Some(human)
    var stored: Option[InstanceRecord] = None
    var creates = 0
    var appends = 0
    var failAppendOnce = false
    var hideActiveReadback = false
    var divergeActiveReadback = false
    var failActiveReadback = false

    val selections = new SelectionPort {
      def loadC(selectionId: String): Consequence[Option[Selection]] =
        Consequence.success(selected.filter(_.selectionId == selectionId))
    }
    val instances = new WorkflowInstancePersistence {
      def create(config: Configuration, record: InstanceRecord): Consequence[InstanceRecord] =
        if (config != configuration || stored.nonEmpty)
          Consequence.stateConflict("GoalPhase fixture create conflict")
        else record.validateC.map { valid =>
          creates += 1
          stored = Some(valid)
          valid
        }

      def load(config: Configuration, identity: InstanceIdentity): Consequence[Option[InstanceRecord]] =
        if (config != configuration) Consequence.stateConflict("GoalPhase fixture store mismatch")
        else if (appends > 0 && failActiveReadback)
          Consequence.stateConflict("GoalPhase fixture readback failure")
        else if (appends > 0 && hideActiveReadback) Consequence.success(None)
        else if (appends > 0 && divergeActiveReadback)
          Consequence.success(stored.map(_.copy(correlation = CorrelationReference("foreign"))))
        else Consequence.success(stored.filter(_.identity == identity))

      def append(config: Configuration, identity: InstanceIdentity,
        expectedRevision: InstanceRevision, entry: HistoryEntry): Consequence[InstanceRecord] =
        if (config != configuration || failAppendOnce) {
          failAppendOnce = false
          Consequence.stateConflict("GoalPhase fixture planned append failure")
        } else stored match {
          case Some(current) if current.identity == identity =>
            current.appendC(expectedRevision, entry).map { next =>
              appends += 1
              stored = Some(next)
              next
            }
          case _ => Consequence.stateConflict("GoalPhase fixture instance is missing")
        }
    }

    def start(input: GoalPhaseStartCommand = command) =
      GoalPhaseStartExecution.startC(input, selections, definition, configuration, instances)
  }

  "GoalPhase Start execution" should {
    "prepare one closed registration record only from the admitted original Start" in {
      Given("an accepted Start and its read-only retained provenance")
      val fixture = new Fixture
      take(fixture.start())
      val retained = take(GoalPhaseStartExecution.loadRetainedStartC(
        command, fixture.selections, definition, configuration, fixture.instances))
      val request = GoalPhaseSubmissionRegistration.Request("registration-1", "submission-1", "register-key", command)

      When("Workflow prepares and reloads the single association/receipt record")
      val record = take(GoalPhaseSubmissionRegistration.prepareC(request, retained))
      val wire = take(GoalPhaseSubmissionRegistration.encodeStorageC(record))
      val reloaded = take(GoalPhaseSubmissionRegistration.decodeStorageC(request.submissionIdentity, wire))

      Then("exact typed input and host-derived Start/receipt facts survive without writes")
      reloaded.request shouldBe request
      reloaded.originalStart shouldBe retained.interaction
      take(GoalPhaseSubmissionRegistration.decodeHandleC(request.submissionIdentity,
        org.goldenport.record.Record.data(GoalPhaseSubmissionRegistration.storageField -> wire))) shouldBe
        retained.interaction.handle
      GoalPhaseSubmissionRegistration.matchesRetainedC(reloaded, request, retained).isSuccess shouldBe true
      fixture.creates shouldBe 1
      fixture.appends shouldBe 1

      When("a caller pairs another command or key with those retained facts")
      val foreign = request.copy(start = command.copy(participantIdentity = "foreign"))
      val changedKey = request.copy(idempotencyIdentity = "other-key")

      Then("preparation and retry comparison reject the substitutions")
      GoalPhaseSubmissionRegistration.prepareC(foreign, retained) shouldBe a[Consequence.Failure[?]]
      GoalPhaseSubmissionRegistration.matchesRetainedC(reloaded, changedKey, retained) shouldBe a[Consequence.Failure[?]]
    }

    "reject legacy associations, foreign lookups and caller-modified registration receipts" in {
      Given("a prepared registration with its original Start projection")
      val fixture = new Fixture
      val started = take(fixture.start())
      val retained = take(GoalPhaseStartExecution.loadRetainedStartC(
        command, fixture.selections, definition, configuration, fixture.instances))
      val request = GoalPhaseSubmissionRegistration.Request("registration-1", "submission-1", "register-key", command)
      val wire = take(GoalPhaseSubmissionRegistration.encodeStorageC(
        take(GoalPhaseSubmissionRegistration.prepareC(request, retained))))
      val prefix = wire.substring(0, wire.indexOf('{'))
      val json = io.circe.parser.parse(wire.substring(wire.indexOf('{'))).toOption.get
      val changedReceipt = json.mapObject(root => root.add("receipt",
        root("receipt").get.mapObject(_.add("participantIdentity", io.circe.Json.fromString("foreign")))))
      val extra = json.mapObject(_.add("approved", io.circe.Json.True))
      val legacy = take(GoalPhaseSubmissionAssociation.encodeStorageC(request.submissionIdentity,
        take(WorkflowWaitJsonV1.encodeC(started))))

      When("the closed reader encounters another lookup, altered receipt, extra authority or legacy bytes")
      val rejected = Vector(
        GoalPhaseSubmissionRegistration.decodeStorageC("foreign", wire),
        GoalPhaseSubmissionRegistration.decodeStorageC(request.submissionIdentity, prefix + changedReceipt.noSpaces),
        GoalPhaseSubmissionRegistration.decodeStorageC(request.submissionIdentity, prefix + extra.noSpaces),
        GoalPhaseSubmissionRegistration.decodeStorageC(request.submissionIdentity, legacy))
      val rejectedHandles = Vector(
        GoalPhaseSubmissionRegistration.decodeHandleC("foreign",
          org.goldenport.record.Record.data(GoalPhaseSubmissionRegistration.storageField -> wire)),
        GoalPhaseSubmissionRegistration.decodeHandleC(request.submissionIdentity,
          org.goldenport.record.Record.data(GoalPhaseSubmissionRegistration.storageField -> (prefix + changedReceipt.noSpaces))),
        GoalPhaseSubmissionRegistration.decodeHandleC(request.submissionIdentity,
          org.goldenport.record.Record.data(GoalPhaseSubmissionRegistration.storageField -> legacy)),
        GoalPhaseSubmissionRegistration.decodeHandleC(request.submissionIdentity,
          org.goldenport.record.Record.data(GoalPhaseSubmissionAssociation.storageField -> legacy)),
        GoalPhaseSubmissionRegistration.decodeHandleC(request.submissionIdentity, null))

      Then("none is treated as accepted registration or upgraded authority")
      rejected.foreach(_ shouldBe a[Consequence.Failure[?]])
      rejectedHandles.foreach(_ shouldBe a[Consequence.Failure[?]])
      fixture.creates shouldBe 1
      fixture.appends shouldBe 1
    }

    "publish registration only after exact readback and never overwrite an uncertain or conflicting row" in {
      Given("a closed record prepared from an admitted retained Start")
      val fixture = new Fixture
      take(fixture.start())
      val retained = take(GoalPhaseStartExecution.loadRetainedStartC(
        command, fixture.selections, definition, configuration, fixture.instances))
      val request = GoalPhaseSubmissionRegistration.Request("registration-1", "submission-1", "register-key", command)
      val expected = take(GoalPhaseSubmissionRegistration.prepareC(request, retained))
      def row(value: GoalPhaseSubmissionRegistration.Record) = org.goldenport.record.Record.data(
        GoalPhaseSubmissionRegistration.storageField -> take(GoalPhaseSubmissionRegistration.encodeStorageC(value)))
      val exact = row(expected)
      val divergent = row(take(GoalPhaseSubmissionRegistration.prepareC(
        request.copy(idempotencyIdentity = "other-key"), retained)))
      def uncertain[A](result: Consequence[A]): Unit = result match {
        case Consequence.Failure(conclusion) =>
          conclusion.observation.cause.descriptor.facets.collect {
            case org.goldenport.observation.Descriptor.Facet.Reason(name) => name
          } should contain("transaction-indeterminate")
        case _ => fail("Uncertain registration published a successful record")
      }
      final class Probe(initial: Consequence[Option[org.goldenport.record.Record]],
        after: Consequence[Option[org.goldenport.record.Record]], createResult: Consequence[Unit])
          extends GoalPhaseRegistrationRetention.Port {
        var reads = 0
        var creates = 0
        def loadC() = { reads += 1; if (reads == 1) initial else after }
        def createC(record: org.goldenport.record.Record) = {
          creates += 1
          record.getString(GoalPhaseSubmissionRegistration.storageField) shouldBe
            exact.getString(GoalPhaseSubmissionRegistration.storageField)
          createResult
        }
        def run() = GoalPhaseRegistrationRetention.retainC(expected, this)
      }
      When("the initial load fails or an existing row is malformed or belongs to another request")
      Vector(Consequence.stateConflict("initial load failure"), Consequence.success(Some(divergent)),
        Consequence.success(Some(org.goldenport.record.Record.data("legacy" -> "row")))).foreach { initial =>
        val probe = new Probe(initial, Consequence.success(Some(exact)), Consequence.success(()))
        probe.run() shouldBe a[Consequence.Failure[?]]
        probe.creates shouldBe 0
        probe.reads shouldBe 1
      }
      Then("failure is never interpreted as absence and retained rows are never replaced")

      When("Create succeeds but its observation is absent, failing, malformed or divergent")
      Vector(Consequence.success(None), Consequence.stateConflict("readback failure"),
        Consequence.success(Some(divergent)),
        Consequence.success(Some(org.goldenport.record.Record.data("legacy" -> "row")))).foreach { after =>
        val probe = new Probe(Consequence.success(None), after, Consequence.success(()))
        uncertain(probe.run())
        probe.creates shouldBe 1
        probe.reads shouldBe 2
      }
      Then("all four outcomes are typed transaction-indeterminate without successful receipt")
      val failedCreate = new Probe(Consequence.success(None), Consequence.success(Some(exact)),
        Consequence.stateConflict("unclassified Create failure"))
      uncertain(failedCreate.run())
      failedCreate.creates shouldBe 1
      failedCreate.reads shouldBe 1

      When("exact authoritative readback or an already-retained equal row is available")
      val created = new Probe(Consequence.success(None), Consequence.success(Some(exact)), Consequence.success(()))
      val retry = new Probe(Consequence.success(Some(exact)), Consequence.stateConflict("must not reread"),
        Consequence.stateConflict("must not Create"))
      Then("only the observed closed record is returned and equal retry performs no Create")
      take(created.run()).request shouldBe request
      take(retry.run()).request shouldBe request
      created.creates shouldBe 1
      retry.creates shouldBe 0
    }

    "recover only the historical Start after later progression without resetting current state" in {
      Given("an admitted Start followed by a later retained Workflow entry")
      val fixture = new Fixture
      val started = take(fixture.start())
      val first = fixture.stored.get
      take(fixture.instances.append(configuration, first.identity, first.revision, HistoryEntry(
        HistorySequence(2L), InstanceRevision(2L), Lifecycle.Active,
        Some(ProgressionReference("step.Reviewing")), first.correlation,
        CausationReference("later-work"), None, None, None)))
      val before = fixture.stored

      When("registration reads the retained original Start using its recorded selection")
      val retained = take(GoalPhaseStartExecution.loadRetainedStartC(
        command, fixture.selections, definition, configuration, fixture.instances))

      Then("the historical projection is exact and distinct from current progression")
      retained.interaction shouldBe started
      retained.observedRevision shouldBe InstanceRevision(2L)
      fixture.stored shouldBe before
      fixture.creates shouldBe 1
      fixture.appends shouldBe 2
    }

    "reject missing or unactivated Start and altered original history without creating records" in {
      Given("a valid selection but no retained Start")
      val fixture = new Fixture
      def recover() = GoalPhaseStartExecution.loadRetainedStartC(
        command, fixture.selections, definition, configuration, fixture.instances)

      When("registration attempts read-only recovery")
      val absent = recover()

      Then("absence is rejected without starting the Workflow")
      absent shouldBe a[Consequence.Failure[?]]
      fixture.creates shouldBe 0
      fixture.appends shouldBe 0

      When("initial creation succeeds but activation has failed")
      fixture.failAppendOnce = true
      fixture.start() shouldBe a[Consequence.Failure[?]]
      val unactivated = recover()

      Then("the NotStarted record does not establish an accepted Start")
      unactivated shouldBe a[Consequence.Failure[?]]
      fixture.creates shouldBe 1
      fixture.appends shouldBe 0

      When("the retained activation is replaced with another invocation correlation")
      take(fixture.start())
      val valid = fixture.stored.get
      val foreign = CorrelationReference("foreign-selection")
      fixture.stored = Some(valid.copy(correlation = foreign,
        history = Vector(valid.history.head.copy(correlation = foreign))))
      val changed = recover()

      Then("the foreign history is rejected without repair or a new write")
      changed shouldBe a[Consequence.Failure[?]]
      fixture.creates shouldBe 1
      fixture.appends shouldBe 1
    }

    "withhold WAIT for absent, divergent or failed committed readback and recover without rewriting Start" in {
      for (fault <- Vector("absent", "divergent", "failed")) {
        Given(s"a recorded human selection and $fault Active readback")
        val fixture = new Fixture
        fixture.hideActiveReadback = fault == "absent"
        fixture.divergeActiveReadback = fault == "divergent"
        fixture.failActiveReadback = fault == "failed"

        When("Start commits activation but cannot verify its retained record")
        val response = fixture.start()

        Then("no WAIT is returned and the committed Start is not compensated")
        response shouldBe a[Consequence.Failure[?]]
        fixture.stored.map(_.lifecycle) shouldBe Some(Lifecycle.Active)
        fixture.creates shouldBe 1
        fixture.appends shouldBe 1

        When("readback recovers and the exact same Start is retried")
        fixture.hideActiveReadback = false
        fixture.divergeActiveReadback = false
        fixture.failActiveReadback = false
        val recovered = take(fixture.start())

        Then("the original handle is returned with no additional create or append")
        recovered.handle.instanceIdentity shouldBe fixture.stored.get.identity
        recovered.current shouldBe a[WorkflowContinuation.Wait]
        fixture.creates shouldBe 1
        fixture.appends shouldBe 1
      }
    }

    "reject foreign or unrecorded input before creating the first instance" in {
      val fixture = new Fixture
      fixture.start(command.copy(input = command.input.copy(phaseIdentity = "foreign"))) shouldBe
        a[Consequence.Failure[?]]
      fixture.start(command.copy(input = command.input.copy(repositoryIdentity = "foreign"))) shouldBe
        a[Consequence.Failure[?]]
      fixture.selected = Some(human.copy(selectedInput = None))
      fixture.start() shouldBe a[Consequence.Failure[?]]
      fixture.stored shouldBe None
      fixture.creates shouldBe 0
      fixture.appends shouldBe 0
      fixture.selected = Some(human)
      take(fixture.start())
      fixture.creates shouldBe 1
      fixture.appends shouldBe 1
    }

    "create one durable Active instance and return its framework Handle" in {
      val fixture = new Fixture
      val started = take(fixture.start())
      started.handle.workflowIdentity shouldBe definition.workflowIdentity
      started.handle.workflowRevision shouldBe definition.workflowRevision
      started.handle.instanceIdentity shouldBe fixture.stored.get.identity
      started.current shouldBe a[WorkflowContinuation.Wait]
      take(WorkflowWaitJsonV1.decodeC(take(WorkflowWaitJsonV1.encodeC(started)))) shouldBe started
      fixture.stored.get.lifecycle shouldBe Lifecycle.Active
      fixture.stored.get.currentProgression shouldBe Some(ProgressionReference("step.Awaiting"))
      fixture.creates shouldBe 1
      fixture.appends shouldBe 1

      take(fixture.start()) shouldBe started
      fixture.creates shouldBe 1
      fixture.appends shouldBe 1
    }

    "reject a missing human selection and a changed input under the same idempotency key" in {
      val fixture = new Fixture
      fixture.selected = None
      fixture.start() shouldBe a[Consequence.Failure[?]]
      fixture.creates shouldBe 0
      fixture.selected = Some(human)
      take(fixture.start())
      fixture.start(command.copy(input = command.input.copy(phaseIdentity = "77.2"))) shouldBe
        a[Consequence.Failure[?]]
      fixture.creates shouldBe 1
      fixture.appends shouldBe 1
    }

    "resume the same Start after the initial record was created but Active append failed" in {
      val fixture = new Fixture
      fixture.failAppendOnce = true
      fixture.start() shouldBe a[Consequence.Failure[?]]
      fixture.stored.map(_.lifecycle) shouldBe Some(Lifecycle.NotStarted)
      fixture.creates shouldBe 1
      take(fixture.start()).current shouldBe a[WorkflowContinuation.Wait]
      fixture.creates shouldBe 1
      fixture.appends shouldBe 1
    }

    "invoke the generated Provided Operation through the public Service transport" in {
      val fixture = new Fixture
      val operation = take(GoalPhaseStartServiceOperation.bindC(
        fixture.selections, definition, configuration, fixture.instances))
      val wire = GoalPhaseStartServiceOperation.encodeCommand(command)
      take(GoalPhaseStartServiceOperation.decodeCommandC(wire)) shouldBe command
      val request = Request.of(
        component = SmWorkflowComponent.name, service = "GoalPhaseService",
        operation = "startGoalPhase", arguments = List(Argument("arg1", wire))
      )
      operation.createOperationRequest(request).isSuccess shouldBe true
      operation.createOperationRequest(request.copy(operation = "requestStepClose")) shouldBe
        a[Consequence.Failure[?]]
      operation.createOperationRequest(request.copy(arguments = List(Argument(
        "arg1", wire.replace("\"phaseIdentity\":\"77.1\"",
          "\"phaseIdentity\":\"77.1\",\"extra\":1")
      )))) shouldBe a[Consequence.Failure[?]]

      val service = spec.ServiceDefinition(
        name = "GoalPhaseService",
        operations = spec.OperationDefinitionGroup(NonEmptyVector.of(operation))
      )
      val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(service)))
      val subsystem = RuntimeBindingAdmissionFixture.admit(Subsystem(
        name = "sm-workflow-goal-phase-start",
        version = None,
        configuration = ResolvedConfiguration(GenericConfiguration.empty, ConfigurationTrace.empty),
        aliasResolver = AliasResolver.empty,
        runMode = RunMode.Command
      ))
      try {
        val component = new Component() {}
        component.initialize(ComponentInit(
          subsystem,
          Component.Core.create(SmWorkflowComponent.name, SmWorkflowComponent.componentId,
            ComponentInstanceId.default(SmWorkflowComponent.componentId), protocol),
          ComponentOrigin.Builtin
        ))
        subsystem.add(component)
        val cli = new CncfRuntime()
        val selector = s"${SmWorkflowComponent.name}/GoalPhaseService/startGoalPhase"
        for (origin <- Vector(
            SelectionOrigin.Recommendation, SelectionOrigin.Skill,
            SelectionOrigin.Workflow, SelectionOrigin.TerminalResult)) {
          fixture.selected = Some(human.copy(origin = origin))
          cli.executeCommandResponse(subsystem,
            Array(selector, "--json", wire)) shouldBe a[Consequence.Failure[?]]
        }
        fixture.selected = Some(human.copy(
          startOperation = StateMachineOperationIdentity("SplitPhaseService", "startSplitPhase")))
        cli.executeCommandResponse(subsystem,
          Array(selector, "--json", wire)) shouldBe a[Consequence.Failure[?]]
        fixture.creates shouldBe 0
        fixture.appends shouldBe 0
        fixture.selected = Some(human)
        val response = take(subsystem.executeOperationResponse(request))
        val wait = response match {
          case OperationResponse.Scalar(value: String) => take(WorkflowWaitJsonV1.decodeC(value))
          case other => fail(s"Expected public WAIT response, found $other")
        }
        wait.handle.instanceIdentity shouldBe fixture.stored.get.identity
        wait.current shouldBe a[WorkflowContinuation.Wait]
        fixture.stored.map(_.lifecycle) shouldBe Some(Lifecycle.Active)
        take(subsystem.executeOperationResponse(request)) shouldBe response
        val directWire = response match {
          case OperationResponse.Scalar(value: String) => value
          case other => fail(s"Expected public WAIT response, found $other")
        }
        take(cli.executeCommandResponse(subsystem,
          Array(selector, "--json", wire))).print shouldBe directWire
        cli.executeCommandResponse(subsystem,
          Array(selector, "--json", "{")) shouldBe a[Consequence.Failure[?]]
        fixture.creates shouldBe 1
        fixture.appends shouldBe 1
      } finally subsystem.shutdown()
    }
  }
}
