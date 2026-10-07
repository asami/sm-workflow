package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import cats.data.NonEmptyVector
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.cli.{CncfRuntime, RunMode}
import org.goldenport.cncf.component.{Component, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.EventEngine
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.cncf.unitofwork.UnitOfWork
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.configuration.{Configuration as GenericConfiguration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.protocol.{Argument, Protocol, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec as spec
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.GivenWhenThen
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{GoalPhaseStartCommand, GoalPhaseStartInput, StepCloseRequest, StepReviewEvidence}

/**
 * @since   Sep. 29, 2026
 * @version Oct.  8, 2026
 */
final class GoalPhaseClosureAuthorityExecutionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import GoalPhaseClosureAuthority.*
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case failure => fail(s"closure authority rejected: $failure")
  }
  private def resource(path: String): String = {
    val source = scala.io.Source.fromInputStream(getClass.getResourceAsStream(path), "UTF-8")
    try source.mkString.stripSuffix("\n") finally source.close()
  }
  private def digest(bytes: Vector[Byte]): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes.toArray).map(byte => f"${byte & 0xff}%02x").mkString
  private val now = Instant.parse("2026-09-29T00:00:00Z")
  private val sidecar = take(CandidateAdmissionProducerAbi.parseC(
    resource("/workflow/goal-phase-candidate-admission-producer-abi.json")))
  private def definition(suffix: String = ""): DefinitionBinding = take(WorkflowInstancePersistence.bindCandidateDefinitionC(take(
    CandidateWorkflowAbi.parseC(resource("/workflow/goal-phase-workflow-abi.json") + suffix, sidecar,
      "src/main/cozy/sm-workflow.cml"))))

  private final class Fixture(val viaCli: Boolean = false, val rehydrate: Boolean = false,
    val callScoped: Boolean = false) {
    val componentId = SmWorkflowComponent.componentId
    val binding = definition()
    val configuration = Configuration(NamedWorkflowStore("workflow-primary", WorkflowStoreIdentity("workflow-store")),
      MigrationPolicy("fixture-v1"), RetentionPolicy("fixture-v1"), LeasePolicy("fixture-v1"),
      DeliveryConfiguration.SameStore(WorkflowStoreIdentity("workflow-store")))
    var program = "authorized program\n".getBytes(StandardCharsets.UTF_8).toVector
    val planning = "authorized planning\n".getBytes(StandardCharsets.UTF_8).toVector
    val base = StepCloseRequest("close-authority", "step-authority", digest(program), digest(planning), Vector.empty)
    val revision = take(StepClosureAdmission.candidateRefC(
      StepClosureAdmission.StepSnapshot(base.stepIdentity, base.programDigest, base.planningDigest))).revision
    val request = base.copy(reviewEvidence = Vector(StepReviewEvidence("full-review", revision,
      "2026-09-28T23:59:00Z", "2026-09-29T01:00:00Z", "full-step", "reviewed", "fixture-reviewer")))
    var stored: Option[InstanceRecord] = None
    var creates = 0
    var appends = 0
    var commits = Vector.empty[GoalPhaseStepClosureBoundary.CommitRequest]
    var associated: Option[WorkflowHandle] = None
    val instances = new WorkflowInstancePersistence {
      def create(config: Configuration, record: InstanceRecord): Consequence[InstanceRecord] =
        if (config != configuration || stored.nonEmpty) Consequence.stateConflict("create conflict")
        else record.validateC.map(value => { creates += 1; stored = Some(value); value })
      def load(config: Configuration, id: InstanceIdentity): Consequence[Option[InstanceRecord]] =
        if (config != configuration) Consequence.stateConflict("load mismatch")
        else Consequence.success(stored.filter(_.identity == id).map(value =>
          if (rehydrate) value.copy(definition = definition()) else value))
      def append(config: Configuration, id: InstanceIdentity, expected: InstanceRevision,
        entry: HistoryEntry): Consequence[InstanceRecord] = stored match {
        case Some(value) if config == configuration && value.identity == id =>
          value.appendC(expected, entry).map(next => { appends += 1; stored = Some(next); next })
        case _ => Consequence.stateConflict("append mismatch")
      }
    }
    val snapshot = new GoalPhaseStepClosureBoundary.SnapshotPort {
      def loadC(step: String): Consequence[GoalPhaseStepClosureBoundary.ArtifactSnapshot] =
        if (step != request.stepIdentity) Consequence.stateConflict("unknown snapshot")
        else Consequence.success(GoalPhaseStepClosureBoundary.ArtifactSnapshot(program, planning))
    }
    val commit = new GoalPhaseStepClosureBoundary.CommitPort {
      def commitC(value: GoalPhaseStepClosureBoundary.CommitRequest,
        bytes: GoalPhaseStepClosureBoundary.ArtifactSnapshot): Consequence[GoalPhaseStepClosureBoundary.CommitReceipt] = {
        commits :+= value
        Consequence.success(GoalPhaseStepClosureBoundary.CommitReceipt("f" * 40,
          value.idempotencyKey, value.stepIdentity, value.candidateRevision, value.programDigest,
          value.planningDigest, value.reviewEvidenceIdentity, value.fromState, value.event, value.toState))
      }
    }
    val spi = sidecar.models.head.requiredSpi.find(_.identity.value == "review-step-capability").get
    val review = StateMachineRequiredOperation(StateMachineRequiredOperationIdentity(spi.identity.value),
      spi.actionIdentity.value, StateMachineOperationIdentity(spi.operation.service.value, spi.operation.name.value),
      spi.operation.inputType.map(value => StateMachineInputTypeReference(value.value)),
      spi.operation.resultType.map(value => StateMachineResultTypeReference(value.value)),
      StateMachineRequiredOperationMetadata(ContextContract("review-context", Vector.empty, Vector.empty),
        CompletionContract("review-completion", Vector.empty), EvidenceContract("review-evidence", Vector.empty), Vector.empty))
    val startCommand = GoalPhaseStartCommand(GoalPhaseStartInput("77.1", "cncf"), "selection", "human", "start-authority")
    val selection = new ProfileStartAdmission.SelectionPort {
      def loadC(id: String) = Consequence.success(Some(ProfileStartAdmission.Selection(
        "selection", StateMachineOperationIdentity("GoalPhaseService", "startGoalPhase"),
        binding.workflowIdentity, binding.workflowRevision, "human",
        ProfileStartAdmission.SelectionOrigin.ExplicitHuman, "start-authority", None,
        Some(GoalPhaseStartCommandAdapter.selectedInput(startCommand.input)))))
    }
    val invocation = new GoalPhaseStepCloseServiceOperation.InvocationPort {
      def resolveC(id: String): Consequence[WorkflowHandle] = associated match {
        case Some(handle) if id == request.submissionIdentity => Consequence.success(handle)
        case _ => Consequence.stateConflict("no Start association")
      }
    }
    var authorityCalls = 0
    var decisionCalls = 0
    var captured: Option[Intent] = None
    var authorization: Option[Authorization] = None
    var onAuthority: () => Unit = () => ()
    var transform: ContinuationRequest[Input] => ContinuationRequest[Input] = value => value
    val authority = new AuthorizationPort {
      def loadC(intent: Intent): Consequence[Option[Authorization]] = {
        authorityCalls += 1; captured = Some(intent); onAuthority(); Consequence.success(authorization)
      }
    }
    val decisions = new DecisionPort {
      def resolveC(intent: Intent): Consequence[ContinuationRequest[Input]] = {
        decisionCalls += 1
        Consequence.success(transform(ContinuationRequest[Input](
          StateMachineRunIdentity(intent.handle.instanceIdentity.value),
          org.goldenport.cncf.workflow.ContinuationIdentity(s"authority-decision:${intent.idempotencyKey}"),
          intent.expectedRevision, StateMachineRequiredOperationIdentity("host.step-close-authority"),
          StateMachineOperationIdentity("HumanBoundaryFixture", "decideStepClose"),
          Some(TypedValue(inputCodec.typeIdentity, intent.input)), None,
          ContextBundle("Resolve exact local commit authority",
            Vector(ContextReference(intent.input.candidate.stepIdentity, intent.input.candidateRevision)),
            Vector(ContextReference(intent.input.candidate.submissionIdentity, intent.input.requestDigest)), intent.snapshot),
          CompletionContract("human-stop-only", Vector.empty), EvidenceContract("human-evidence", Vector.empty),
          StateMachineOperationIdentity("HumanBoundaryFixture", "recordStepCloseAuthority"))))
      }
    }
    var authorityCores = Vector.empty[ActionCall.Core]
    var decisionCores = Vector.empty[ActionCall.Core]
    val ports = if (!callScoped) Ports(authority, decisions) else Ports(
      new CallScopedAuthorizationPort {
        def loadForCallC(intent: Intent, core: ActionCall.Core): Consequence[Option[Authorization]] = {
          authorityCores :+= core
          authority.loadC(intent)
        }
      },
      new CallScopedDecisionPort {
        def resolveForCallC(intent: Intent, core: ActionCall.Core): Consequence[ContinuationRequest[Input]] = {
          decisionCores :+= core
          decisions.resolveC(intent)
        }
      })
    val runtime = new PersistentContinuationRuntime(new ContinuationRuntimePersistence.InMemory)
    val issued = new IssuedWorkOrderPersistence.InMemory[StepCloseRequest]
    def fresh(): UnitOfWork = new UnitOfWork(ExecutionContext.create(), EventEngine.noop(DataStore.noop()))
    val requirement = ExecutionRequirement(Vector(CapabilityRequirement("review-step-capability")),
      RiskLevel("standard"), ReasoningLevel.Deep, true)
    val startOp = take(GoalPhaseStartServiceOperation.bindC(selection, binding, configuration, instances))
    def closeBinding(value: Option[Ports]) = GoalPhaseStepCloseServiceOperation.bindC(() => now,
      invocation, review, instances, configuration, runtime, issued, requirement,
      MinimalPresentation("Review", "Full-Step review"), snapshot, commit, () => fresh(), value)
    val closeOp = take(closeBinding(Some(ports)))
    val service = spec.ServiceDefinition("GoalPhaseService", spec.OperationDefinitionGroup(NonEmptyVector.of(startOp, closeOp)))
    val subsystem = RuntimeBindingAdmissionFixture.admit(Subsystem(name = "closure-authority", version = None,
      configuration = ResolvedConfiguration(GenericConfiguration.empty, ConfigurationTrace.empty),
      aliasResolver = AliasResolver.empty, runMode = RunMode.Command))
    val component = new Component() {}
    component.initialize(ComponentInit(subsystem, Component.Core.create(componentId.name, componentId,
      ComponentInstanceId.default(componentId), Protocol(services = spec.ServiceDefinitionGroup(Vector(service)))), ComponentOrigin.Builtin))
    subsystem.add(component)
    val cli = new CncfRuntime()
    def invoke(operation: String, wire: String): Consequence[String] =
      if (viaCli) cli.executeCommandResponse(subsystem,
        Array(s"${componentId.name}/GoalPhaseService/$operation", "--json", wire)).map(_.print)
      else subsystem.executeOperationResponse(Request.of(component = componentId.name,
        service = "GoalPhaseService", operation = operation, arguments = List(Argument("arg1", wire)))).flatMap {
          case OperationResponse.Scalar(value: String) => Consequence.success(value)
          case _ => Consequence.stateConflict("not a common envelope")
        }
    def start(): WorkflowHandle = {
      val result = take(WorkflowWaitJsonV1.decodeC(take(invoke("startGoalPhase", GoalPhaseStartServiceOperation.encodeCommand(startCommand)))))
      associated = Some(result.handle); result.handle
    }
    def close(input: StepCloseRequest = request): Consequence[String] = invoke("requestStepClose", GoalPhaseStepCloseJson.encode(input))
    def grant(): Authorization = {
      val intent = captured.get
      Authorization(intent.handle, intent.expectedRevision, intent.snapshot, intent.input.requestDigest,
        "recorded-local-authority", "human", now.minusSeconds(60), now.plusSeconds(3600))
    }
    def assess(validateRetained: () => Consequence[Unit] = () => Consequence.unit): Consequence[Assessment] =
      for {
        plan <- GoalPhaseStepClosureBoundary.planPreReviewedC(request, review, now, associated.get, stored.get)
        result <- assessPlanC(request, now, associated.get, plan, instances, configuration,
          snapshot, authority, validateRetained)
      } yield result
  }

  "Read-only closure authority assessment" should {
    "distinguish missing and separately recorded authority without creating a question or executing a commit" in {
      Given("a started pre-reviewed fixture and no recorded local-commit authorization")
      val f = new Fixture()
      try {
        val handle = f.start()
        val before = f.stored
        var retainedChecks = 0
        val retained = () => { retainedChecks += 1; Consequence.unit }
        When("the shared read-only guard assesses the admitted plan")
        val missing = take(f.assess(retained))
        Then("a typed missing result retains the exact intent and changes no durable state or command")
        missing.status shouldBe AuthorityStatus.Missing
        missing.intent.handle shouldBe handle
        missing.intent.input.candidate shouldBe f.request
        retainedChecks shouldBe 2
        f.authorityCalls shouldBe 2
        f.decisionCalls shouldBe 0
        f.stored shouldBe before
        f.commits shouldBe empty
        Given("separately recorded authorization matching that exact intent")
        f.authorization = Some(f.grant())
        When("the same admitted plan is assessed again")
        val recorded = take(f.assess(retained))
        Then("Recorded is only an observation and does not itself authorize or perform progression")
        recorded.status shouldBe AuthorityStatus.Recorded
        recorded.intent shouldBe missing.intent
        retainedChecks shouldBe 4
        f.authorityCalls shouldBe 4
        f.decisionCalls shouldBe 0
        f.stored shouldBe before
        f.commits shouldBe empty
      } finally f.subsystem.shutdown()
    }

    "refuse instance snapshot authority and retained-proof drift before returning an assessment" in {
      for (drift <- Vector("instance", "snapshot", "authority", "retained")) {
        Given(s"an admitted plan with a $drift change during its read-only observation")
        val f = new Fixture()
        try {
          f.start()
          var retainedChecks = 0
          val retained = () => {
            retainedChecks += 1
            if (drift == "retained" && retainedChecks == 2)
              Consequence.stateConflict("fixture retained proof changed")
            else Consequence.unit
          }
          f.onAuthority = () => {
            if (f.authorityCalls == 1 && drift == "instance")
              f.stored = f.stored.map(record => take(record.appendC(record.revision,
                HistoryEntry(HistorySequence(record.history.size + 1L), record.revision.next,
                  Lifecycle.Active, record.currentProgression, record.correlation, CausationReference("concurrent"),
                  record.derivedCompositeOccurrence, record.committedPredecessor, None))))
            if (f.authorityCalls == 1 && drift == "snapshot") f.program = Vector(1.toByte)
            if (f.authorityCalls == 2 && drift == "authority") f.authorization = Some(f.grant())
          }
          When("the shared guard re-reads all current evidence")
          val result = f.assess(retained)
          Then("the conflicting observation cannot become a question or a commit")
          result shouldBe a[Consequence.Failure[?]]
          f.decisionCalls shouldBe 0
          f.commits shouldBe empty
          f.appends shouldBe 1
        } finally f.subsystem.shutdown()
      }
    }

    "refuse stale authority and unadmitted reviewed input instead of reporting a missing grant" in {
      Given("an admitted fixture plan with an expired separately recorded authorization")
      val f = new Fixture()
      try {
        f.start()
        take(f.assess())
        f.authorization = Some(f.grant().copy(validUntil = now.minusSeconds(1)))
        val before = f.stored
        When("authority assessment receives stale evidence or lacks an issued accepted review")
        val stale = f.assess()
        val unreviewed = assessReviewedC(sidecar, null, null, now, f.stored.get,
          f.instances, f.configuration, f.snapshot, f.authority, () => Consequence.unit)
        Then("both refuse without a legacy Decision fallback or state mutation")
        stale shouldBe a[Consequence.Failure[?]]
        unreviewed shouldBe a[Consequence.Failure[?]]
        f.decisionCalls shouldBe 0
        f.stored shouldBe before
        f.commits shouldBe empty
      } finally f.subsystem.shutdown()
    }
  }

  "GoalPhase pre-reviewed closure authority" should {
    "retain an exact stop-only Decision without reconstructing approval or advancing the Step" in {
      Given("an unapproved Step and its host-routed canonical Decision")
      val f = new Fixture()
      try {
        val handle = f.start()
        val wire = take(f.close())
        val intent = f.captured.get
        val original = take(WorkflowDecisionJsonV1.decodeC(wire, inputCodec)).current match {
          case WorkflowContinuation.Decision(request, _) => request
          case other => fail(s"Expected Decision: $other")
        }
        val before = f.stored
        When("the host serializes and reads the recorded Decision for the same intent")
        val raw = take(GoalPhaseClosureDecisionRecord.encodeStorageC(intent, wire))
        val restored = take(GoalPhaseClosureDecisionRecord.decodeStorageC(intent, raw))
        Then("the exact host request survives without granting or changing the instance")
        restored shouldBe original
        restored.runId.value shouldBe handle.instanceIdentity.value
        restored.resultType shouldBe None
        f.stored shouldBe before
        f.commits shouldBe empty
        val reader = new GoalPhaseClosureDecisionRecord.DataStorePort("workflow-primary", null)
        reader.resolveC(intent) shouldBe a[Consequence.Failure[?]]
        reader.resolveForCallC(intent, null) shouldBe a[Consequence.Failure[?]]
      } finally f.subsystem.shutdown()
    }

    "reject foreign Decision scope and malformed storage instead of rebuilding the current request" in {
      Given("one exact recorded Decision and an intent with immutable scope")
      val f = new Fixture()
      try {
        f.start()
        val wire = take(f.close())
        val intent = f.captured.get
        val raw = take(GoalPhaseClosureDecisionRecord.encodeStorageC(intent, wire))
        val offset = raw.indexOf('{')
        val prefix = raw.substring(0, offset)
        val json = io.circe.parser.parse(raw.substring(offset)).toOption.get
        When("stored shape or current lookup, instance, revision, snapshot or candidate differs")
        val malformed = Vector(null, "", wire, prefix + "[]",
          prefix + json.mapObject(_.remove("decision")).noSpaces,
          prefix + json.mapObject(_.add("extra", Json.True)).noSpaces,
          prefix + json.mapObject(_.add("schemaVersion", Json.fromString("foreign"))).noSpaces,
          prefix + json.mapObject(_.add("decision", Json.fromBoolean(true))).noSpaces,
          prefix + json.mapObject(_.add("decision", Json.fromString("{}"))).noSpaces)
        val foreign = Vector(intent.copy(idempotencyKey = "foreign"),
          intent.copy(handle = intent.handle.copy(instanceIdentity = InstanceIdentity("foreign"))),
          intent.copy(expectedRevision = StateMachineRevision("999")),
          intent.copy(snapshot = intent.snapshot.copy(workspaceRevision = Some("foreign"))),
          intent.copy(input = intent.input.copy(reason = "foreign")))
        Then("every incompatible read fails without defaults, grants or state changes")
        malformed.foreach(value => GoalPhaseClosureDecisionRecord.decodeStorageC(intent, value)
          shouldBe a[Consequence.Failure[?]])
        foreign.foreach(value => {
          GoalPhaseClosureDecisionRecord.decodeStorageC(value, raw) shouldBe a[Consequence.Failure[?]]
          if (value.idempotencyKey == intent.idempotencyKey)
            GoalPhaseClosureDecisionRecord.encodeStorageC(value, wire) shouldBe a[Consequence.Failure[?]]
        })
        GoalPhaseClosureDecisionRecord.encodeStorageC(null, wire) shouldBe a[Consequence.Failure[?]]
        f.commits shouldBe empty
      } finally f.subsystem.shutdown()
    }

    "round-trip recorded authority scope without refreshing expiry or granting closure" in {
      Given("a stopped Step and separately recorded exact authorization facts")
      val f = new Fixture()
      try {
        f.start()
        take(f.close())
        val authorization = f.grant()
        val identity = f.captured.get.idempotencyKey
        val history = f.stored.get.history
        When("the host encodes and decodes its immutable authorization record")
        val wire = take(GoalPhaseClosureAuthorizationRecord.encodeStorageC(identity, authorization))
        val restored = take(GoalPhaseClosureAuthorizationRecord.decodeStorageC(identity, wire))
        Then("all recorded scope and timestamps survive without granting or changing the Step")
        restored shouldBe authorization
        f.stored.get.history shouldBe history
        f.commits shouldBe empty
        When("the host loads a faithfully preserved expired record")
        val expired = authorization.copy(validUntil = now.minusSeconds(1))
        f.authorization = Some(take(GoalPhaseClosureAuthorizationRecord.decodeStorageC(identity,
          take(GoalPhaseClosureAuthorizationRecord.encodeStorageC(identity, expired)))))
        Then("the existing gate rejects expiry rather than refreshing or approving it")
        f.close() shouldBe a[Consequence.Failure[?]]
        f.commits shouldBe empty
      } finally f.subsystem.shutdown()
    }

    "reject foreign lookup identities and malformed authorization records without fallback" in {
      Given("a complete recorded authorization envelope")
      val f = new Fixture()
      try {
        f.start(); take(f.close())
        val identity = f.captured.get.idempotencyKey
        val authorization = f.grant()
        val wire = take(GoalPhaseClosureAuthorizationRecord.encodeStorageC(identity, authorization))
        val separator = wire.indexOf('{')
        val prefix = wire.substring(0, separator)
        val json = io.circe.parser.parse(wire.substring(separator)).toOption.get
        When("lookup identity, schema, types, scope or timestamps are incompatible")
        val invalid = Vector(null, "", wire.substring(separator),
          prefix + json.mapObject(_.add("extra", Json.True)).noSpaces,
          prefix + json.mapObject(_.remove("authorityReference")).noSpaces,
          prefix + json.mapObject(_.add("schemaVersion", Json.fromString("foreign"))).noSpaces,
          prefix + json.mapObject(_.add("participantIdentity", Json.fromInt(1))).noSpaces,
          prefix + json.mapObject(_.add("scopeWait", Json.fromString("{}"))).noSpaces,
          prefix + json.mapObject(_.add("grantedAt", Json.fromString("invalid"))).noSpaces,
          prefix + json.mapObject(_.add("validUntil", Json.fromString(now.minusSeconds(120).toString))).noSpaces)
        Then("decoding fails and neither a grant nor a default record is manufactured")
        invalid.foreach(raw => GoalPhaseClosureAuthorizationRecord.decodeStorageC(identity, raw)
          shouldBe a[Consequence.Failure[?]])
        GoalPhaseClosureAuthorizationRecord.decodeStorageC("foreign", wire) shouldBe a[Consequence.Failure[?]]
        Vector(null, authorization.copy(handle = null), authorization.copy(contextSnapshot = null),
          authorization.copy(authorityReference = " "), authorization.copy(grantedAt = null),
          authorization.copy(validUntil = now.minusSeconds(120))).foreach(value =>
          GoalPhaseClosureAuthorizationRecord.encodeStorageC(identity, value) shouldBe a[Consequence.Failure[?]])
        f.commits shouldBe empty
      } finally f.subsystem.shutdown()
    }

    "bind authority and Decision lookups to each current Service or CLI call without granting authority" in {
      for (cli <- Vector(false, true)) {
        Given("a started Workflow with call-scoped host ports and no recorded commit authority")
        val f = new Fixture(viaCli = cli, callScoped = true)
        try {
          val handle = f.start()
          val history = f.stored.get.history
          When("Step-close requests authority through the registered Operation")
          val decision = take(WorkflowDecisionJsonV1.decodeC(take(f.close()), inputCodec))
          Then("both lookups receive the same current core and Step remains unchanged")
          decision.handle shouldBe handle
          f.authorityCores.size shouldBe 2
          f.decisionCores.size shouldBe 1
          f.authorityCores.foreach(core => (core eq f.decisionCores.head) shouldBe true)
          f.stored.get.history shouldBe history
          f.commits shouldBe empty
          f.ports.authorizations.loadC(f.captured.get) shouldBe a[Consequence.Failure[?]]
          f.ports.decisions.resolveC(f.captured.get) shouldBe a[Consequence.Failure[?]]
          Given("separately retained exact authority for the reviewed candidate")
          f.authorization = Some(f.grant())
          val previousCore = f.authorityCores.head
          When("a new Step-close Operation executes")
          take(WorkflowTerminalJsonV1.decodeC(take(f.close()), GoalPhaseStepCloseServiceOperation.terminalCodec))
          Then("the new call supplies a fresh core and commits exactly once")
          f.authorityCores.size shouldBe 4
          (f.authorityCores.last eq previousCore) shouldBe false
          f.decisionCores.size shouldBe 1
          f.commits.size shouldBe 1
        } finally f.subsystem.shutdown()
      }
    }

    "return a stable typed Decision on the Start-created instance through Service and CLI without granting authority" in {
      for (cli <- Vector(false, true)) {
        val f = new Fixture(cli, rehydrate = true)
        try {
          val handle = f.start()
          val history = f.stored.get.history
          val wire = take(f.close())
          val interaction = take(WorkflowDecisionJsonV1.decodeC(wire, inputCodec))
          interaction.handle shouldBe handle
          take(f.close()) shouldBe wire
          f.captured.get.input.candidate shouldBe f.request
          f.stored.get.history shouldBe history
          f.stored.get.lifecycle shouldBe Lifecycle.Active
          f.stored.get.suspension shouldBe None
          f.creates shouldBe 1
          f.appends shouldBe 1
          f.commits shouldBe empty
          WorkflowResultJsonV1.decodeC(wire, inputCodec) shouldBe a[Consequence.Failure[?]]
          f.invoke("recordStepCloseAuthority", wire) shouldBe a[Consequence.Failure[?]]
        } finally f.subsystem.shutdown()
      }
    }

    "commit exactly once only after separately retained exact authority, without treating Decision as approval" in {
      for (cli <- Vector(false, true); rehydrate <- Vector(false, true)) {
        val f = new Fixture(cli, rehydrate)
        try {
        val handle = f.start()
        take(f.close())
        f.commits shouldBe empty
        f.authorization = Some(f.grant())
        val terminal = take(WorkflowTerminalJsonV1.decodeC(take(f.close()), GoalPhaseStepCloseServiceOperation.terminalCodec))
        terminal.handle shouldBe handle
        f.commits.size shouldBe 1
        f.commits.head.programDigest shouldBe f.request.programDigest
        f.commits.head.planningDigest shouldBe f.request.planningDigest
        f.commits.head.reviewEvidenceIdentity shouldBe "full-review"
        f.stored.get.lifecycle shouldBe Lifecycle.Completed
        f.close() shouldBe a[Consequence.Failure[?]]
        f.commits.size shouldBe 1
        f.decisionCalls shouldBe 1
        } finally f.subsystem.shutdown()
      }
    }

    "reject incomplete ports, unreviewed requests, stale reviews and changed bytes before authority lookup" in {
      val f = new Fixture
      try {
        f.closeBinding(null) shouldBe a[Consequence.Failure[?]]
        f.closeBinding(Some(null)) shouldBe a[Consequence.Failure[?]]
        f.closeBinding(Some(Ports(null, f.decisions))) shouldBe a[Consequence.Failure[?]]
        f.closeBinding(Some(Ports(f.authority, null))) shouldBe a[Consequence.Failure[?]]
        f.start()
        f.close(f.base) shouldBe a[Consequence.Failure[?]]
        f.close(f.request.copy(reviewEvidence = f.request.reviewEvidence.map(_.copy(validUntil = "2026-09-28T23:59:01Z")))) shouldBe a[Consequence.Failure[?]]
        f.program = "changed".getBytes(StandardCharsets.UTF_8).toVector
        f.close() shouldBe a[Consequence.Failure[?]]
        f.authorityCalls shouldBe 0
        f.decisionCalls shouldBe 0
        f.appends shouldBe 1
        f.commits shouldBe empty
      } finally f.subsystem.shutdown()
    }

    "reject stale, foreign and malformed host authority rather than converting it into a Decision" in {
      val changes: Vector[Authorization => Authorization] = Vector(
        value => value.copy(requestDigest = "f" * 64),
        value => value.copy(expectedRevision = StateMachineRevision("9")),
        value => value.copy(contextSnapshot = ContextSnapshot("foreign")),
        value => value.copy(handle = value.handle.copy(instanceIdentity = InstanceIdentity("foreign"))),
        value => value.copy(participantIdentity = ""),
        value => value.copy(authorityReference = ""),
        value => value.copy(validUntil = now.minusSeconds(1)),
        value => value.copy(grantedAt = now.plusSeconds(1)))
      for (change <- changes) {
        val f = new Fixture
        try {
          f.start(); take(f.close()); f.authorization = Some(change(f.grant())); f.decisionCalls = 0
          f.close() shouldBe a[Consequence.Failure[?]]
          f.decisionCalls shouldBe 0
          f.commits shouldBe empty
          f.appends shouldBe 1
        } finally f.subsystem.shutdown()
      }
    }

    "reject changed host Decision input, context, semantic SPI reuse and callable application completion identities" in {
      type R = ContinuationRequest[Input]
      val changes: Vector[R => R] = Vector(
        value => value.copy(runId = StateMachineRunIdentity("foreign")),
        value => value.copy(expectedRevision = StateMachineRevision("9")),
        value => value.copy(input = value.input.map(input => input.copy(value = input.value.copy(requestDigest = "f" * 64)))),
        value => value.copy(context = value.context.copy(snapshot = ContextSnapshot("foreign"))),
        value => value.copy(context = value.context.copy(requiredFacts = Vector.empty)),
        value => value.copy(context = value.context.copy(references = Vector.empty)),
        value => value.copy(requiredOperation = StateMachineRequiredOperationIdentity("review-step-capability")),
        value => value.copy(requiredOperation = StateMachineRequiredOperationIdentity("repair-step-capability")),
        value => value.copy(operation = StateMachineOperationIdentity("GoalPhaseService", "requestStepClose")),
        value => value.copy(completionOperation = StateMachineOperationIdentity("GoalPhaseService", "submitStepReview")),
        value => value.copy(resultType = Some(StateMachineResultTypeReference("Approval"))),
        value => value.copy(continuationId = null))
      for (change <- changes) {
        val f = new Fixture
        try {
          f.start(); f.transform = change
          f.close() shouldBe a[Consequence.Failure[?]]
          f.decisionCalls shouldBe 1
          f.commits shouldBe empty
          f.appends shouldBe 1
        } finally f.subsystem.shutdown()
      }
    }

    "reject instance, snapshot and authorization drift during host lookup without returning a stale Decision" in {
      for (drift <- Vector("instance", "definition", "snapshot", "authorization")) {
        val f = new Fixture
        try {
          f.start()
          f.transform = value => {
            drift match {
              case "instance" => f.stored = f.stored.map(record => take(record.appendC(record.revision,
                HistoryEntry(HistorySequence(record.history.size + 1L), record.revision.next, Lifecycle.Active,
                  record.currentProgression, record.correlation, CausationReference("concurrent"),
                  record.derivedCompositeOccurrence, record.committedPredecessor, None))))
              case "definition" => f.stored = f.stored.map(_.copy(definition = definition(" ")))
              case "snapshot" => f.program = Vector(1.toByte)
              case _ => f.authorization = Some(f.grant())
            }; value
          }
          f.close() shouldBe a[Consequence.Failure[?]]
          f.decisionCalls shouldBe 1
          f.commits shouldBe empty
        } finally f.subsystem.shutdown()
      }
    }

    "strictly decode full candidate evidence and reject approval, selector and substituted digest fields" in {
      val f = new Fixture
      try {
        f.start(); take(f.close())
        val input = f.captured.get.input
        val json = inputCodec.encode(input)
        inputCodec.decode(json) shouldBe Right(input)
        val bad = Vector(json.mapObject(_.add("approved", Json.True)),
          json.mapObject(_.add("reason", Json.fromString("approved"))),
          json.mapObject(_.add("candidateRevision", Json.fromString("f" * 64))),
          json.mapObject(_.add("requestDigest", Json.fromString("f" * 64))),
          json.mapObject(_.add("candidate", json.hcursor.downField("candidate").focus.get.mapObject(_.add("state", Json.fromString("Closed"))))))
        bad.foreach(value => inputCodec.decode(value).isLeft shouldBe true)
      } finally f.subsystem.shutdown()
    }
  }
}
