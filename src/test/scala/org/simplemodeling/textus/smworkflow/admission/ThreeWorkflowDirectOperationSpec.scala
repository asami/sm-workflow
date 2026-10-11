package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit
import io.circe.Json
import org.goldenport.cncf.bootstrap.{BootstrapConfig, CncfHandle}
import org.goldenport.cncf.cli.CncfRuntime
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.component.{ComponentCreate, ComponentDescriptor, ComponentInstanceMetadata, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.datastore.{ComponentRecordStorage, ComponentRecordStorageDeclaration, DataStore}
import org.goldenport.cncf.event.*
import org.goldenport.cncf.security.IngressSecurityResolver
import org.goldenport.cncf.subsystem.*
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.cncf.workflow.{ContextReference, StateMachineRevision, WorkflowComponentStorage, WorkflowMechanicalTransition, WorkflowDecisionJsonV1, WorkflowInstanceRecordJsonV1, WorkflowWaitJsonV1, WorkflowWorkOrderJsonV1, WorkflowResultJsonV1, WorkflowTerminalJsonV1}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{SplitPhaseAdvanceCommand, SplitPhasePartitionContextReference}
import SplitPhaseExplicitPartitionFixture.take
import SplitPhaseTypedStartFixture as F

/** Direct public Operation calls on the production three-service composition.
  * An extra specification-only service prepares input and observes stored state;
  * it replaces no public Operation implementation, evidence adapter or Workflow.
  * An optional test observer runs within the actual owning Human retry dispatch,
  * forwarding the production action and its afterCommit response unchanged.
  * Input/evidence rows are test data, not a production human interaction channel.
  * No launcher, CLI command, fabricated history or direct SQLite write is used.
  * @since   Oct.  7, 2026
  * @version Oct. 11, 2026
  */
final class ThreeWorkflowDirectOperationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def collection(name: String) = DataStore.CollectionId.EntityStore(
    EntityCollectionId("smworkflow", "cli", name))
  private val declaration = SplitPhaseManagedStartAssembly.Declaration(F.configuration,
    collection("instances"), collection("continuations"))
  private val instances = CallScopedWorkflowInstances.managed(F.configuration, F.catalog.definition,
    declaration.instances, declaration.continuations)
  // Public Start selects its allocation by selectionIdentity, unlike the pure
  // preparation fixture's independently named reference.
  private val typedStart = F.record.copy(reference = F.record.reference.copy(identity = F.command.selectionIdentity))
  private val startWire = SplitPhaseStartServiceOperation.encodeCommand(F.command)
  private def scalar(value: OperationResponse): String = value match {
    case OperationResponse.Scalar(value: String) => value
    case other => fail(s"Expected Operation JSON: $other")
  }
  private def _with_unused_fields(value: Json): Json = value.asObject match {
    case Some(fields) => Json.obj(fields.toVector.map((key, child) => key -> _with_unused_fields(child))*)
      .mapObject(_.add("unusedCommandGrant", Json.obj("execute" -> Json.True)))
    case None => value.asArray.map(items => Json.arr(items.map(_with_unused_fields)*)).getOrElse(value)
  }

  private final class Setup(val core: ActionCall.Core, task: Setup => Consequence[Unit]) extends ProcedureActionCall {
    /** Selected row data in this fresh private specification database only.
      * Native-state variants use supported native encoding of an existing
      * entry; no fabricated append, completion or independent consent is made.
      */
    def rewriteFixtureRowC(collection: DataStore.CollectionId, identity: String, field: String,
      prefix: String, annotateRow: Boolean = false)(change: Json => Json): Consequence[Unit] = {
      given ExecutionContext = executionContext
      val space = executionContext.dataStoreSpace
      val prior = space.captureBinding()
      try for {
        _ <- bind_component_application_datastore_c("workflow")
        store <- space.dataStore(collection)
        row <- store.load(collection, DataStore.StringEntryId(identity)).flatMap(
          _.map(Consequence.success).getOrElse(Consequence.stateConflict("Selected fixture row is absent")))
        wire <- row.getString(field).map(Consequence.success)
          .getOrElse(Consequence.stateConflict("Selected fixture payload is absent"))
        json <- io.circe.parser.parse(wire.substring(prefix.length)).fold(
          _ => Consequence.stateConflict("Selected fixture JSON differs"), Consequence.success)
        payload = if (annotateRow) Record.data(field -> (prefix + change(json).noSpaces), "unusedDiagnostic" -> "inert annotation")
          else Record.data(field -> (prefix + change(json).noSpaces))
        _ <- store.update(collection, DataStore.StringEntryId(identity), payload)
      } yield ()
      finally space.restoreBinding(prior)
    }
    def createC(collection: DataStore.CollectionId, identity: String, record: Record): Consequence[Unit] =
      ComponentRecordStorage.bindForCallC(core, "workflow", collection, DataStore.StringEntryId(identity))
        .flatMap(_.createC(record))
    def currentC(identity: InstanceIdentity = F.record.instanceIdentity): Consequence[InstanceRecord] =
      instances.loadForCallC(F.configuration, identity, core).flatMap {
        case Some(record) => Consequence.success(record)
        case None => Consequence.stateConflict("Specification has no Operation-created instance")
      }
    def fixtureRowExistsC(collection: DataStore.CollectionId, identity: String): Consequence[Boolean] =
      ComponentRecordStorage.bindForCallC(core, "workflow", collection, DataStore.StringEntryId(identity))
        .flatMap(_.loadC()).map(_.nonEmpty)
    def reasoningHumanCompletionExistsC(identity: org.goldenport.cncf.workflow.ContinuationIdentity): Consequence[Boolean] = {
      val direction = SplitPhaseReasoningHumanDirection
      val answer = SplitPhaseHumanDirectionAnswerContract
      WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
        SmWorkflowComponent.componentId, declaration.configuration, Vector(F.catalog.definition),
        declaration.instances, declaration.continuations).flatMap(_.bindDecisionCompletionC(
          direction.completionOperation, direction.inputCodec, answer.payloadCodec, direction.closingType,
          (_, _) => Consequence.success(Vector.empty)))
        .flatMap(_.loadRetainedC(identity)(using executionContext)).map(_.nonEmpty)
    }
    def applicationReceiptExistsC(reference: String): Consequence[Boolean] =
      WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
        SmWorkflowComponent.componentId, declaration.configuration, Vector(F.catalog.definition),
        declaration.instances, declaration.continuations).flatMap(_.bindMechanicalC(
          SplitPhasePlanningCheckpointPreparation.advanceOperation, SplitPhaseManagedSelectedApply.payloadCodec))
        .flatMap(_.loadRetainedC(WorkflowMechanicalTransition.Reference(reference, "1"))(using executionContext)).map(_.nonEmpty)
    def humanEvidenceC(question: WorkflowInteraction[?, Nothing],
      result: ContinuationResult[SplitPhaseHumanDirectionAnswerContract.Answer]): Consequence[Unit] = for {
      binding <- ThreeWorkflowHostEvidencePorts.splitAnswerBindingC(SplitPhaseHumanAnswerVerification.AnswerContext(
        core.component.get.core.instanceId, question, result, F.command.participantIdentity))
      now = executionContext.clock.instant()
      reference = result.resultReference
      identity = ThreeWorkflowHostEvidencePorts.key(reference.identity, reference.revision)
      evidence = NativeHostEvidence.Evidence(NativeHostEvidence.schema, NativeHostEvidence.Purpose.SplitAnswer.identity,
        identity, reference.identity, reference.revision, "normal-development-host", F.command.participantIdentity,
        binding, now.minusSeconds(60).toString)
      _ <- createC(collection("host_evidence"), NativeHostEvidence.recordIdentity(NativeHostEvidence.Purpose.SplitAnswer, identity),
        Record.data(NativeHostEvidence.storageField -> take(NativeHostEvidence.encodeStorageC(evidence))))
    } yield ()
    def execute(): Consequence[OperationResponse] = task(this).map(_ => OperationResponse.Scalar("specification-setup"))
  }

  private def _observe_advance_operation(operation: spec.OperationDefinition,
    observation: ActionCall.Core => Consequence[Unit]): spec.OperationDefinition = new spec.OperationDefinition {
    val specification = operation.specification
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      operation.createOperationRequest(request).map {
        case command: CommandAction => new CommandAction {
          val request = command.request
          override def commandExecutionMode = command.commandExecutionMode
          def createCall(capsule: ActionCall.Core): ActionCall = new ProcedureActionCall {
            val core = capsule
            private val _delegate = command.createCall(core)
            def execute(): Consequence[OperationResponse] = observation(core).flatMap(_ => _delegate.execute())
            override def afterCommitResponseC(response: OperationResponse): Consequence[OperationResponse] =
              _delegate.afterCommitResponseC(response)
          }
        }
        case other => other
      }
  }

  private def withRuntime(root: Path, processplan: Option[Json] = None,
    advanceobservation: Option[ActionCall.Core => Consequence[Unit]] = None,
    completionobservation: Option[ActionCall.Core => Consequence[Unit]] = None)(task: (Subsystem, org.goldenport.cncf.component.Component,
    ExecutionContext, (Setup => Consequence[Unit]) => Unit) => Unit): Unit = {
    val raw = ThreeWorkflowNormalHostFixture.values(root) ++ Map(
      "textus.subsystem.user-mode" -> "standalone", "textus.datastore.kind" -> "sqlite",
      "textus.datastore.path" -> root.resolve("operation.sqlite").toString) ++
      Map("store-name" -> "workflow", "store-identity" -> "partition-context", "migration-policy" -> "v1",
        "retention-policy" -> "v1", "lease-policy" -> "v1", "instance-collection" -> collection("instances").print,
        "continuation-collection" -> collection("continuations").print)
        .map((key, value) => ("sm-workflow.goal-phase.persistence." + key) -> value)
    val config = ResolvedConfiguration(HostConfiguration(raw.map((key, value) =>
      key -> ConfigurationValue.StringValue(value))), ConfigurationTrace.empty)
    val context = ExecutionContext.create()
    val policy = take(EventPersistencePolicy.createC("direct-operation-spec-v1", Vector(
      EventPersistencePolicy.Rule(EventTypeId("transient"), EventPersistencePolicy.Mode.Transient))))
    val persistence = take(EventStorePersistenceDeclaration.createC("events", collection("cursor"), collection("events"),
      collection("indexes"), Vector(EventStorePersistenceDeclaration.WorkflowGrant(SmWorkflowComponent.componentId,
        "workflow", declaration.instances, declaration.continuations))))
    val grants = take(ComponentRecordStorageDeclaration.createC(ThreeWorkflowNormalHostFixture.recordCollections.map { name =>
      ComponentRecordStorageDeclaration.CreateGrant(SmWorkflowComponent.componentId, "workflow",
        DataStore.CollectionId.EntityStore(take(EntityCollectionId.parse(name))))
    }))
    val bootstrap = if (processplan.isEmpty) Some(take(SubsystemEventStoreBootstrap.createC(policy, persistence,
      ScopeContext(ScopeKind.Subsystem, "direct-operation-spec", None, context.observability), config, None,
      org.goldenport.cncf.path.AliasResolver.empty, grants))) else None
    val embedded = processplan.map { plan =>
      val directory = Files.createDirectories(root.resolve(".textus"))
      val host = raw ++ Map("textus.subsystem.storage.plan" -> ThreeWorkflowNormalHostFixture.storagePlan.noSpaces,
        "textus.subsystem.process-execution.plan" -> plan.noSpaces)
      Files.writeString(directory.resolve("config.json"), Json.obj(host.toVector.map((key, value) => key -> Json.fromString(value))*).noSpaces)
      Files.writeString(directory.resolve("assembly.yaml"),
        s"""subsystem: direct-operation-spec
           |components: []
           |security:
           |  authentication:
           |    convention: enabled
           |    fallback_privilege: disabled
           |    local_subject:
           |      id: ${F.command.participantIdentity}
           |      roles: [user]
           |      capabilities: [user]
           |      security_level: user
           |config:
           |  textus.subsystem.user-mode: standalone
           |""".stripMargin)
      // Explicit fixture execution profile, not production user/reviewer
      // issuance. Storage, Workflow Operations and process effects stay real.
      Files.writeString(directory.resolve("test.yaml"),
        """kind: test-descriptor
          |execution:
          |  profile: controlled
          |  key: direct-operation-spec
          |  time:
          |    mode: manual
          |    start-at: 2026-10-07T00:00:00Z
          |  random:
          |    mode: seeded
          |    seed: direct-operation-spec
          |  ids:
          |    mode: deterministic
          |  scheduler:
          |    mode: manual
          |  ordering:
          |    mode: deterministic
          |""".stripMargin)
      new CncfRuntime()
    }
    var embeddedhandle = Option.empty[CncfHandle]
    try {
      val selectedowner = embedded match {
        case Some(runtime) =>
          val handle = take(runtime.initializeHandle(BootstrapConfig(cwd = root,
            args = Array(s"--textus.assembly.descriptor=${root.resolve(".textus/assembly.yaml")}",
              s"--textus.test.descriptor=${root.resolve(".textus/test.yaml")}"))))
          embeddedhandle = Some(handle)
          handle.subsystem
        case None => bootstrap.get.subsystem
      }
      val owner = RuntimeBindingAdmissionFixture.admit(selectedowner)
      var setup: Setup => Consequence[Unit] = _ => Consequence.unit
      val probe = new spec.OperationDefinition {
        val specification = spec.OperationDefinition.Specification(name = "prepareInput",
          request = WorkflowJsonCommandRequest.definition, response = spec.ResponseDefinition.void)
        def createOperationRequest(r: Request): Consequence[OperationRequest] = Consequence.success(new CommandAction {
          val request = r
          override def commandExecutionMode = CommandExecutionMode.Sync
          def createCall(core: ActionCall.Core): ActionCall = new Setup(core, setup)
        })
      }
      // All public Operations and ports come from the normal decoder/binder.
      // Optional spec-owned observations surround only genuine actions, keeping
      // their full specification, command mode and afterCommit handling. The
      // completion observation may invoke a separate normal competing Operation.
      val factory = new SmWorkflowConfiguredComponentFactory(ThreeWorkflowHostParameters.declarations, Vector.empty,
        (creation, parameters, resolved) => for {
          host <- ThreeWorkflowHostParameters.decodeC(creation, parameters, resolved)
          services <- ThreeWorkflowLauncherAssembly.bindC(creation, host)(using resolved)
        } yield services.map { service =>
          if (service.name == "SplitPhaseService" && (advanceobservation.nonEmpty || completionobservation.nonEmpty)) {
              val operations = service.operations.operations.toVector.map { operation =>
                if (operation.name == "advanceSplitPhase" && advanceobservation.nonEmpty)
                  _observe_advance_operation(operation, advanceobservation.get)
                else if (operation.name == "submitPartitionSemanticResult" && completionobservation.nonEmpty)
                  new spec.OperationDefinition {
                    val specification = operation.specification
                    def createOperationRequest(request: Request): Consequence[OperationRequest] =
                      operation.createOperationRequest(request).map {
                        case command: CommandAction => new CommandAction {
                          val request = command.request
                          override def commandExecutionMode = command.commandExecutionMode
                          def createCall(capsule: ActionCall.Core): ActionCall = new ProcedureActionCall {
                            val core = capsule
                            private val _delegate = command.createCall(core)
                            def execute(): Consequence[OperationResponse] = _delegate.execute().flatMap(response =>
                              completionobservation.get(core).map(_ => response))
                            override def afterCommitResponseC(response: OperationResponse): Consequence[OperationResponse] =
                              _delegate.afterCommitResponseC(response)
                          }
                        }
                        case other => other
                      }
                  }
                else operation
              }
              spec.ServiceDefinition.Instance(service.specification.copy(operations =
                spec.OperationDefinitionGroup(NonEmptyVector.fromVectorUnsafe(operations))))
          } else service
        } :+ spec.ServiceDefinition("SpecificationSetup", spec.OperationDefinitionGroup(NonEmptyVector.of(probe))),
        ThreeWorkflowLauncherAssembly.publishedMcpOperations).primaryFactory
      val componentId = SmWorkflowComponent.componentId
      val creation = ComponentCreate(owner, ComponentOrigin.Builtin, Vector(ComponentDescriptor(
        name = Some(componentId.name), componentName = Some(componentId.name),
        version = Some("0.1.0-SNAPSHOT"), schemaVersion = Some(3), componentId = Some(componentId), config = raw)))
        .withInstanceMetadata(ComponentInstanceMetadata(componentId.name, "direct-spec", componentId = Some(componentId)))
      val component = take(factory.createPrimaryC(creation))
      owner.add(component)
      owner.withDescriptor(GenericSubsystemDescriptor(path = root, subsystemName = "direct-operation-spec",
        componentBindings = Vector(GenericSubsystemComponentBinding(component.name)), security = Some(GenericSubsystemSecurityBinding(
          authentication = Some(GenericSubsystemAuthenticationBinding(convention = Some("enabled"), fallbackPrivilege = Some("disabled"),
            localSubject = Some(GenericSubsystemLocalSubjectBinding(id = F.command.participantIdentity,
              roles = Vector("user"), capabilities = Vector("user"), attributes = Map.empty, securityLevel = Some("user")))))))))
      val actor = take(IngressSecurityResolver.resolve(SubsystemExecutionProfile.Fixed,
        component.logic.executionContext(), Map.empty)).executionContext
      def prepare(run: Setup => Consequence[Unit]): Unit = {
        setup = run
        take(owner.executeOperationResponseInContext(Request.of(component = component.name,
          service = "SpecificationSetup", operation = "prepareInput"), actor))
      }
      component.mcpReadyOperations shouldBe ThreeWorkflowLauncherAssembly.publishedMcpOperations
      task(owner, component, actor, prepare)
    } finally {
      bootstrap.foreach(value => take(value.shutdownC()))
      if (embeddedhandle.nonEmpty) embeddedhandle.foreach(_.close())
      else embedded.foreach(_.closeEmbedding())
    }
  }

  private def prepareInputs(call: Setup): Consequence[Unit] = {
    prepareInputsWithFacts(call, F.facts)
  }

  private def prepareInputsWithFacts(call: Setup, facts: SplitPhaseExplicitPartitionContext.Facts,
    start: SplitPhaseTypedStartPreparation.Record = typedStart, legacyevidence: Boolean = false): Consequence[Unit] = {
    import SplitPhaseExplicitPlanningInputJsonV1 as J
    import J.Purpose
    val inputs = Vector(
      (Purpose.Source, start.source.reference.identity, take(J.encodeSourceC(start.source))),
      (Purpose.Inventory, facts.proposal.inventory.reference.identity, take(J.encodeInventoryC(facts.proposal.inventory))),
      (Purpose.Proposal, facts.proposal.proposal.reference.identity, take(J.encodeProposalC(facts.proposal.proposal))),
      (Purpose.Boundary, facts.boundary.boundary.reference.identity, take(J.encodeBoundaryC(facts.boundary.boundary))),
      (Purpose.Ownership, facts.boundary.ownership.reference.identity, take(J.encodeOwnershipC(facts.boundary.ownership))),
      (Purpose.Handoffs, facts.boundary.handoffs.reference.identity, take(J.encodeHandoffsC(facts.boundary.handoffs))),
      (Purpose.Estimates, facts.partition.estimates.reference.identity, take(J.encodeEstimatesC(facts.partition.estimates))),
      (Purpose.PartitionPolicy, facts.partition.policy.reference.identity, take(J.encodePartitionPolicyC(facts.partition.policy))),
      (Purpose.Planning, facts.planning.reference.identity, take(J.encodePlanningC(facts.planning))))
    val now = call.executionContext.clock.instant()
    val identity = ThreeWorkflowHostEvidencePorts.key(start.reference.identity, start.reference.revision)
    val binding = take(ThreeWorkflowHostEvidencePorts.splitStartBindingC(
      SplitPhaseTypedStartHumanVerification.StartContext(call.core.component.get.core.instanceId, start)))
    val evidence = NativeHostEvidence.Evidence(NativeHostEvidence.schema, NativeHostEvidence.Purpose.SplitStart.identity,
      identity, "specification-start-selection", "1", "normal-development-host", start.command.participantIdentity,
      binding, now.minusSeconds(60).toString)
    val currentwire = take(NativeHostEvidence.encodeStorageC(evidence))
    val evidencewire = if (legacyevidence) {
      val version = "sm-workflow.native-host-evidence.v1"
      val payload = io.circe.parser.parse(currentwire.substring(NativeHostEvidence.schema.length + 1)).toOption.get
        .mapObject(_.add("schemaVersion", Json.fromString(version))
          .add("validUntil", Json.fromString(now.minusSeconds(1).toString)))
      version + ":" + payload.noSpaces
    } else currentwire
    val rows = inputs.map((purpose, id, text) => (collection("split_" + purpose.toString.toLowerCase), id, Record.data(J.storageField -> text))) ++ Vector(
      (collection("split_starts"), start.reference.identity,
        Record.data(SplitPhaseTypedStartJsonV1.storageField -> take(SplitPhaseTypedStartJsonV1.encodeAllocationC(start)))),
      (collection("split_selections"), start.selection.selectionId,
        Record.data(ProfileSelectionJsonV1.storageField -> take(ProfileSelectionJsonV1.encodeStorageC(start.selection)))),
      (collection("host_evidence"), NativeHostEvidence.recordIdentity(NativeHostEvidence.Purpose.SplitStart, identity),
        Record.data(NativeHostEvidence.storageField -> evidencewire)))
    rows.foldLeft(Consequence.unit) { case (before, (collection, id, row)) => before.flatMap(_ => call.createC(collection, id, row)) }
  }

  // Literal host-file configuration consumed by the canonical runtime. The
  // component gets only observe/apply capabilities, never executable/argv data.
  private def _document_process_plan(root: Path, recordEffects: Boolean = false, interrupt: Boolean = false): Json = {
    import scala.jdk.CollectionConverters.*
    val classpath = if (recordEffects) _operation_classpath()
      else Files.readAllLines(Path.of("target/cncf.d/runtime-classpath.txt")).asScala
        .map(_.trim).filter(_.nonEmpty).mkString(java.io.File.pathSeparator)
    val fixed = if (recordEffects) Vector("-cp", classpath,
      "org.simplemodeling.textus.smworkflow.admission.ThreeWorkflowDocumentInterruption", root.toString,
      root.getParent.toString, interrupt.toString, "repo")
      else Vector("-cp", classpath, "org.simplemodeling.textus.smworkflow.runtime.SelectedPhaseDocumentProgram",
        root.toString, "repo")
    val limits = Vector("launchTimeoutMillis" -> 10000L, "executionTimeoutMillis" -> 30000L,
      "terminationGraceMillis" -> 1000L, "stdinBytes" -> 2097152L, "stdoutBytes" -> 131072L,
      "stderrBytes" -> 16384L, "argumentCount" -> 16L, "argumentBytes" -> 131072L,
      "artifactCount" -> 1L, "artifactBytes" -> 131072L, "workAreaBytes" -> 4194304L)
    val capability = "sm-workflow-selected-documents"
    val program = Json.obj("capability" -> Json.fromString(capability), "identity" -> Json.fromString(capability),
      "executable" -> Json.fromString(Path.of(System.getProperty("java.home"), "bin/java").toString),
      "fixedArguments" -> Json.fromValues(fixed.map(Json.fromString)),
      "argumentTemplates" -> Json.fromValues(Vector("observe", "apply").map(value =>
        Json.arr(Json.obj("literal" -> Json.fromString(value))))),
      "limits" -> Json.obj(limits.map((key, value) => key -> Json.fromLong(value))*),
      "environment" -> Json.obj(), "allowedArtifacts" -> Json.arr(), "allowedInputFiles" -> Json.obj(),
      "allowsWorkingDirectory" -> Json.fromBoolean(false))
    Json.obj("schema" -> Json.fromString("cncf.runtime-process-execution.v1"), "programs" -> Json.arr(program),
      "grants" -> Json.arr(Json.obj("componentId" -> Json.fromString(SmWorkflowComponent.componentId.name),
        "capabilities" -> Json.arr(Json.fromString(capability)))))
  }

  /** A specification-owned caller in another JVM, not a product CLI adapter.
    * It only reopens stored state and calls the fixed public Operations. No
    * initial input, context registration or Workflow history is supplied here.
    */
  private[admission] def _observe_separate_process(root: Path, input: Json): Json = {
    val mode = input.hcursor.get[String]("mode").fold(error => fail(error.message), identity)
    Vector("APPLY", "PREVIEW", "PENDING", "RESUME") should contain(mode)
    val advance = input.hcursor.get[String]("advance").fold(error => fail(error.message), identity)
    val completion = input.hcursor.get[Option[String]]("completion").fold(error => fail(error.message), identity)
    val completionoperation = input.hcursor.get[String]("completionOperation").getOrElse("submitPartitionSemanticResult")
    Vector("submitPartitionSemanticResult", "submitHandoffScopeResult", "submitReasoningResult", "submitSplitHumanDirection") should
      contain(completionoperation)
    if (mode == "RESUME") completion should not be empty
    val recordEffects = input.hcursor.get[Boolean]("recordDocumentEffects").getOrElse(false)
    val interrupt = input.hcursor.get[Boolean]("interruptDocumentEffect").getOrElse(false)
    if (interrupt) { mode shouldBe "APPLY"; recordEffects shouldBe true }
    var observation = Option.empty[Json]
    withRuntime(root, if (mode == "APPLY") Some(_document_process_plan(root, recordEffects, interrupt)) else None) {
      (owner, component, actor, prepare) =>
      def _invoke_c_(operation: String, wire: String) = owner.executeOperationResponseInContext(
        Request.of(component = component.name, service = "SplitPhaseService", operation = operation,
          arguments = List(Argument("arg1", wire))), actor)
      def _current_json_(): String = {
        var state = Option.empty[InstanceRecord]
        prepare(call => call.currentC().map(value => { state = Some(value); () }))
        take(WorkflowInstanceRecordJsonV1.encodeC(state.get))
      }
      def _response_(value: Consequence[OperationResponse]): Json = value match {
        case Consequence.Success(response) => Json.obj("success" -> Json.True, "wire" -> Json.fromString(scalar(response)))
        case other => Json.obj("success" -> Json.False, "error" -> Json.fromString(other.toString))
      }
      val before = _current_json_()
      val result = _response_(_invoke_c_("advanceSplitPhase", advance))
      val acknowledgement = completion.map(wire => _response_(_invoke_c_(completionoperation, wire)))
      val completionstate = _current_json_()
      val resumed = if (mode == "RESUME") Some(_response_(_invoke_c_("advanceSplitPhase", advance))) else None
      observation = Some(Json.obj("pid" -> Json.fromLong(ProcessHandle.current().pid()),
        "before" -> Json.fromString(before), "result" -> result,
        "acknowledgement" -> acknowledgement.getOrElse(Json.Null),
        "completionState" -> Json.fromString(completionstate), "resumed" -> resumed.getOrElse(Json.Null),
        "after" -> Json.fromString(_current_json_())))
    }
    observation.get
  }

  private def _operation_classpath(): String = {
    import scala.jdk.CollectionConverters.*
    // The non-forked SBT test loader owns the actual compiled test classes and
    // ScalaTest dependencies; the official runtime file supplies production jars.
    val testpaths = Iterator.iterate(Option(this.getClass.getClassLoader))(_.flatMap(loader => Option(loader.getParent)))
      .takeWhile(_.nonEmpty).flatMap(_.iterator).collect {
        case loader: java.net.URLClassLoader => loader.getURLs.toVector
      }.flatten.filter(_.getProtocol == "file").map(url => Path.of(url.toURI).toString).toVector
    val runtimepaths = Files.readAllLines(Path.of("target/cncf.d/runtime-classpath.txt")).asScala
      .map(_.trim).filter(_.nonEmpty).toVector
    (Vector(Path.of(this.getClass.getProtectionDomain.getCodeSource.getLocation.toURI).toString) ++
      testpaths ++ runtimepaths).distinct.mkString(java.io.File.pathSeparator)
  }

  private def _separate_process_observation(root: Path, mode: String, advance: String,
    completion: Option[String] = None, allowProgress: Boolean = false,
    recordEffects: Boolean = false, interrupt: Boolean = false,
    completionoperation: String = "submitPartitionSemanticResult"): Json = {
    import scala.jdk.CollectionConverters.*
    val directory = Files.createTempDirectory(root, "operation-observation-")
    val input = directory.resolve("input.json")
    val output = directory.resolve("output.json")
    val log = directory.resolve("process.log")
    val completionfield = if (completionoperation == "submitPartitionSemanticResult") Json.obj()
      else Json.obj("completionOperation" -> Json.fromString(completionoperation))
    Files.writeString(input, Json.obj("mode" -> Json.fromString(mode), "advance" -> Json.fromString(advance),
      "completion" -> completion.map(Json.fromString).getOrElse(Json.Null),
      "recordDocumentEffects" -> Json.fromBoolean(recordEffects), "interruptDocumentEffect" -> Json.fromBoolean(interrupt))
      .deepMerge(completionfield).noSpaces)
    val classpath = _operation_classpath()
    val command = Vector(Path.of(System.getProperty("java.home"), "bin/java").toString, "-cp", classpath,
      "org.simplemodeling.textus.smworkflow.admission.ThreeWorkflowSeparateProcessObservation",
      root.toAbsolutePath.toString, input.toString, output.toString)
    val process = new ProcessBuilder(command*).redirectErrorStream(true).redirectOutput(log.toFile).start()
    var ownedPrograms = Vector.empty[ProcessHandle]
    try {
      if (interrupt) {
        mode shouldBe "APPLY"
        recordEffects shouldBe true
        val marker = root.resolve("document-applied-before-result.txt")
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25)
        while (!Files.exists(marker) && process.isAlive && System.nanoTime() < deadline) Thread.sleep(25)
        withClue(Files.readString(log))(Files.exists(marker) shouldBe true)
        val effectPid = Files.readString(marker).trim.toLong
        ownedPrograms = process.descendants().iterator().asScala.toVector
        Then("the reported effect program belongs to this live specification-owned caller")
        ownedPrograms.map(_.pid()) should contain(effectPid)
        process.pid() should not be ProcessHandle.current().pid()
        process.destroyForcibly()
        process.waitFor(10, TimeUnit.SECONDS) shouldBe true
        ownedPrograms.foreach(_.destroyForcibly())
        ownedPrograms.foreach { handle =>
          if (handle.isAlive) handle.onExit().get(10, TimeUnit.SECONDS)
          handle.isAlive shouldBe false
        }
        return Json.obj("pid" -> Json.fromLong(process.pid()), "effectPid" -> Json.fromLong(effectPid),
          "exit" -> Json.fromInt(process.exitValue()))
      }
      if (!process.waitFor(30, TimeUnit.SECONDS)) {
        ownedPrograms = process.descendants().iterator().asScala.toVector
        process.destroyForcibly()
        process.waitFor(10, TimeUnit.SECONDS)
        fail(s"Owned direct-Operation observer timed out; diagnostic retained at $log")
      }
      Files.size(log) should be <= 131072L
      withClue(Files.readString(log))(process.exitValue() shouldBe 0)
      Files.size(output) should be <= 2097152L
      val result = io.circe.parser.parse(Files.readString(output)).fold(error => fail(error.message), identity)
      result.hcursor.get[Long]("pid").toOption.get shouldBe process.pid()
      process.pid() should not be ProcessHandle.current().pid()
      if (mode != "RESUME" && !allowProgress)
        result.hcursor.get[String]("before").toOption shouldBe result.hcursor.get[String]("after").toOption
      result
    } finally {
      if (process.isAlive) {
        ownedPrograms = process.descendants().iterator().asScala.toVector
        process.destroyForcibly()
        process.waitFor(10, TimeUnit.SECONDS)
      }
      ownedPrograms.filter(_.isAlive).foreach(_.destroyForcibly())
    }
  }

  private def _semantic_preview_facts(): SplitPhaseExplicitPartitionContext.Facts = {
    import SplitPhaseExplicitObjectiveSelection as O
    val objectives = O.Records(
      Some(O.CostPolicy(O.CostPolicyReference("direct-cost", "1"), F.source.reference, F.source.authority,
        O.ProfileIdentity("expensive"), O.ProfileIdentity("lower"), 2, 1, 1)),
      Some(O.DemandRecord(O.DemandReference("direct-demand", "1"), F.source.reference, F.source.authority,
        F.facts.proposal.inventory.orderedUnfinishedItems.map(id => O.ItemDemand(id, O.Demand.SettledExecution)))),
      Some(O.OverheadPolicy(O.OverheadPolicyReference("direct-overhead", "1"), F.source.reference, F.source.authority,
        1, 0, 0, 0, 0)))
    F.facts.copy(planning = F.facts.planning.copy(objectives = objectives))
  }

  "Public Operations on the production three-workflow composition" should {
    "session-associated targets" which {
      "E7 advance only the explicitly selected Split instance when a session has two real Starts" in {
        Given("Spec: docs/spec/session-workflow-context-resolution.md; Rule: R3; Example: E7; two explicit input allocations and one opaque session on production managed SQLite")
        val root = Files.createTempDirectory("smwf-split-session-targets-")
        val secondcommand = F.command.copy(selectionIdentity = "second-session-split", idempotencyKey = "second-session-split-start")
        val secondstart = typedStart.copy(reference = typedStart.reference.copy(identity = secondcommand.selectionIdentity),
          instanceIdentity = InstanceIdentity("second-session-split-instance"), command = secondcommand,
          selection = F.selectionFor(secondcommand))
        val starts = Vector(typedStart, secondstart)
        val identities = starts.map(_.instanceIdentity)
        var selectedwire = Option.empty[String]
        for (episode <- Vector("publish", "reopen")) withRuntime(root) {
          (owner, component, actor, prepare) =>
          def _session_(foreign: Boolean = false): Json = Json.obj(
            "schemaVersion" -> Json.fromString(NativeWorkflowSessionInput.schema),
            "sessionIdentity" -> Json.fromString(if (foreign) "unassociated-split-session" else "two-split-targets"))
          def _advance_(target: Option[String], foreign: Boolean = false) = {
            val query = target.fold(_session_(foreign))(value => _session_(foreign).mapObject(
              _.add("instanceIdentity", Json.fromString(value))))
            owner.executeOperationResponseInContext(Request.of(component = component.name, service = "SplitPhaseService",
              operation = "advanceSplitPhase", arguments = List(Argument("arg1", query.noSpaces))), actor)
          }
          def _states_(): Vector[InstanceRecord] = {
            var records = Vector.empty[InstanceRecord]
            prepare(call => identities.foldLeft(Consequence.unit)((prior, identity) =>
              prior.flatMap(_ => call.currentC(identity).map(value => { records = records :+ value; () }))))
            records
          }
          if (episode == "publish") {
            When("the fixture prepares only source, selection, allocation and explicitly specification-owned Human evidence for public Start")
            prepare(prepareInputs)
            prepare { call =>
              val identity = ThreeWorkflowHostEvidencePorts.key(secondstart.reference.identity, secondstart.reference.revision)
              val binding = take(ThreeWorkflowHostEvidencePorts.splitStartBindingC(
                SplitPhaseTypedStartHumanVerification.StartContext(call.core.component.get.core.instanceId, secondstart)))
              val evidence = NativeHostEvidence.Evidence(NativeHostEvidence.schema, NativeHostEvidence.Purpose.SplitStart.identity,
                identity, "second-specification-start-selection", "1", "normal-development-host",
                secondcommand.participantIdentity, binding, call.executionContext.clock.instant().minusSeconds(60).toString)
              for {
                _ <- call.createC(collection("split_starts"), secondstart.reference.identity,
                  Record.data(SplitPhaseTypedStartJsonV1.storageField -> take(SplitPhaseTypedStartJsonV1.encodeAllocationC(secondstart))))
                _ <- call.createC(collection("split_selections"), secondstart.selection.selectionId,
                  Record.data(ProfileSelectionJsonV1.storageField -> take(ProfileSelectionJsonV1.encodeStorageC(secondstart.selection))))
                _ <- call.createC(collection("host_evidence"), NativeHostEvidence.recordIdentity(NativeHostEvidence.Purpose.SplitStart, identity),
                  Record.data(NativeHostEvidence.storageField -> take(NativeHostEvidence.encodeStorageC(evidence))))
              } yield ()
            }
            When("the two public Starts publish the actual instances and their same-session associations")
            val started = starts.map(start => take(WorkflowWaitJsonV1.decodeC(scalar(take(
              owner.executeOperationResponseInContext(Request.of(component = component.name, service = "SplitPhaseService",
                operation = "startSplitPhase", arguments = List(Argument("arg1", _session_().deepMerge(Json.obj(
                  "input" -> Json.fromString(SplitPhaseStartServiceOperation.encodeCommand(start.command)))).noSpaces))), actor))))).handle.instanceIdentity)
            Then("both distinct targets have owning Start receipts without fixture-native history or an association side-write")
            started shouldBe identities
            started.distinct.size shouldBe 2
            When("the existing managed registration associates the selected first target's actual partition context")
            prepare(call => call.currentC().flatMap { current =>
              val context = F.context.copy(owner = F.context.owner.copy(start = typedStart.reference,
                handle = take(WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, current)), instanceRevision = current.revision))
              take(SplitPhaseManagedPartitionContextRegistration.bindC(owner.configuration, declaration, F.catalog))
                .registerForCallC(context, call.core).map(_ => ())
            })
          }
          val before = _states_()
          When("advance omits its necessary selector while two real associated targets exist, including after persisted reopen")
          val ambiguous = _advance_(None)
          val afterambiguous = _states_()
          Then("ambiguity prevents progression of either target instead of choosing the newest or first row")
          ambiguous.isSuccess shouldBe false
          ambiguous.toString should include("ambiguous instance")
          afterambiguous shouldBe before
          When("another explicit session names an existing but unassociated target")
          val foreign = _advance_(Some(identities.head.value), foreign = true)
          val afterforeign = _states_()
          Then("the selector does not confer that association or mutate either original target")
          foreign.isSuccess shouldBe false
          foreign.toString should include("no associated instance")
          afterforeign shouldBe before
          When("the owning session supplies only the first target selector, without echoing partition context or revisions")
          val wire = scalar(take(_advance_(Some(identities.head.value))))
          val work = take(WorkflowWorkOrderJsonV1.decodeC(wire, SplitPhaseExplicitPartitionSemanticJson.requestCodec))
          val afterselected = _states_()
          Then("Workflow resolves the actual partition and issues or retains only that target's next semantic work")
          work.handle.instanceIdentity shouldBe identities.head
          work.current shouldBe a[WorkflowContinuation.WorkOrder[?]]
          afterselected(1) shouldBe before(1)
          selectedwire.foreach(_ shouldBe wire)
          selectedwire = Some(wire)
        }
      }
    }
    "start a new execution from the beginning after abandoning pending work without damaging existing files" in {
      Given("an owned directory, an existing file and one selected Split execution")
      val root = Files.createTempDirectory("smwf-fresh-start-")
      val sentinel = root.resolve("existing.txt")
      Files.writeString(sentinel, "Existing user content\n")
      val nextcommand = F.command.copy(selectionIdentity = "fresh-start-selection", idempotencyKey = "fresh-start-invocation")
      val nextstart = typedStart.copy(reference = typedStart.reference.copy(identity = nextcommand.selectionIdentity),
        instanceIdentity = InstanceIdentity("fresh-start-instance"), command = nextcommand, selection = F.selectionFor(nextcommand))
      withRuntime(root) { (owner, component, actor, prepare) =>
        def _invoke_(operation: String, wire: String): String = scalar(take(owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "SplitPhaseService", operation = operation,
            arguments = List(Argument("arg1", wire))), actor)))
        prepare(prepareInputs)
        take(WorkflowWaitJsonV1.decodeC(_invoke_("startSplitPhase", startWire)))
        prepare(call => call.currentC().flatMap { current =>
          val context = F.context.copy(owner = F.context.owner.copy(start = typedStart.reference,
            handle = take(WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, current)), instanceRevision = current.revision))
          take(SplitPhaseManagedPartitionContextRegistration.bindC(owner.configuration, declaration, F.catalog))
            .registerForCallC(context, call.core).map(_ => ())
        })
        val advance = take(SplitPhaseAdvanceCommandJsonV1.encodeC(SplitPhaseAdvanceCommand(typedStart.instanceIdentity.value,
          SplitPhasePartitionContextReference(F.context.reference.identity, F.context.reference.revision))))
        take(WorkflowWorkOrderJsonV1.decodeC(_invoke_("advanceSplitPhase", advance), SplitPhaseExplicitPartitionSemanticJson.requestCodec))
        When("the caller abandons that work and selects a NEW invocation rather than resuming or answering it")
        prepare { call =>
          val key = ThreeWorkflowHostEvidencePorts.key(nextstart.reference.identity, nextstart.reference.revision)
          val binding = take(ThreeWorkflowHostEvidencePorts.splitStartBindingC(
            SplitPhaseTypedStartHumanVerification.StartContext(call.core.component.get.core.instanceId, nextstart)))
          val evidence = NativeHostEvidence.Evidence(NativeHostEvidence.schema, NativeHostEvidence.Purpose.SplitStart.identity,
            key, "fresh-start-evidence", "1", "normal-development-host", nextcommand.participantIdentity, binding,
            call.executionContext.clock.instant().minusSeconds(60).toString)
          for {
            _ <- call.createC(collection("split_starts"), nextstart.reference.identity,
              Record.data(SplitPhaseTypedStartJsonV1.storageField -> take(SplitPhaseTypedStartJsonV1.encodeAllocationC(nextstart))))
            _ <- call.createC(collection("split_selections"), nextstart.selection.selectionId,
              Record.data(ProfileSelectionJsonV1.storageField -> take(ProfileSelectionJsonV1.encodeStorageC(nextstart.selection))))
            _ <- call.createC(collection("host_evidence"), NativeHostEvidence.recordIdentity(NativeHostEvidence.Purpose.SplitStart, key),
              Record.data(NativeHostEvidence.storageField -> take(NativeHostEvidence.encodeStorageC(evidence))))
          } yield ()
        }
        val started = take(WorkflowWaitJsonV1.decodeC(_invoke_("startSplitPhase", SplitPhaseStartServiceOperation.encodeCommand(nextcommand))))
        Then("public Start creates the new run at its initial boundary, with no intermediate recovery or existing-file overwrite")
        started.handle.instanceIdentity shouldBe nextstart.instanceIdentity
        started.handle.instanceIdentity should not be typedStart.instanceIdentity
        prepare(call => call.currentC(nextstart.instanceIdentity).map { current =>
          current.lifecycle shouldBe Lifecycle.Active
          current.suspension shouldBe empty
          current.history.size shouldBe 1
          ()
        })
        Files.readString(sentinel) shouldBe "Existing user content\n"
        Files.exists(root.resolve("docs/phase/phase-77.md")) shouldBe false
      }
    }
    "admit stored Human evidence at the host boundary using current authority rather than a copied issuer receipt" in {
      Given("normal three-workflow storage and a Reader bound before an issuer withdrawal, without fabricated Workflow state")
      val root = Files.createTempDirectory("smwf-host-evidence-boundary-")
      withRuntime(root) { (_, _, _, prepare) =>
        prepare { call =>
          val configuration = call.core.component.get.subsystem.get.configuration
          val old = ResolvedConfiguration(HostConfiguration(configuration.configuration.values.updated(
            NativeHostEvidence.prefix + "issuer", ConfigurationValue.StringValue("withdrawn-host"))), ConfigurationTrace.empty)
          val storage = GoalPhaseManagedStartAssembly.Declaration(F.configuration, F.catalog.definition,
            declaration.instances, declaration.continuations)
          val reader = take(NativeHostEvidence.bindC(old, storage))
          val ports = new ThreeWorkflowHostEvidencePorts(reader)
          val target = take(ThreeWorkflowHostEvidencePorts.splitStartBindingC(
            SplitPhaseTypedStartHumanVerification.StartContext(call.core.component.get.core.instanceId, typedStart)))
          val now = call.executionContext.clock.instant()
          val selected = NativeHostEvidence.Purpose.SplitStart
          val variants = Vector("current", "withdrawn", "future", "foreign-target", "absent")
          variants.foreach { variant =>
            Given(s"a separately provisioned $variant host input for this actual invocation")
            val lookup = ThreeWorkflowHostEvidencePorts.key("boundary-" + variant, "1")
            val issued = NativeHostEvidence.Evidence(NativeHostEvidence.schema, selected.identity, lookup,
              "independent-start-consent", "1", "normal-development-host", "separate-human", target, now.minusSeconds(86400).toString)
            val observed = variant match {
              case "withdrawn" => issued.copy(issuer = "withdrawn-host")
              case "future" => issued.copy(occurredAt = now.plusSeconds(1).toString)
              case "foreign-target" => issued.copy(binding = target.mapObject(_.add("componentInstance", Json.fromString("another-owner"))))
              case _ => issued
            }
            if (variant != "absent") take(call.createC(collection("host_evidence"), NativeHostEvidence.recordIdentity(selected, lookup),
              Record.data(NativeHostEvidence.storageField -> take(NativeHostEvidence.encodeStorageC(observed)))))
            When("the production host EvidencePort reads the physical record under the current host declaration and call clock")
            val admitted = ports.splitStart.admitC(call.core, lookup, target)
            Then("only currently authorized applicable evidence is admitted; no copied construction-time issuer restores withdrawn authority")
            admitted.isSuccess shouldBe (variant == "current")
          }
          Consequence.unit
        }
      }
    }
    for (legacy <- Vector(false, true)) {
      s"admit SplitPhase Start with ${if (legacy) "expired legacy" else "expiry-free current"} host evidence" in {
        Given("the production three-service composition with independently provisioned input and host evidence")
        val root = Files.createTempDirectory("smwf-no-internal-expiry-")
        var original = Option.empty[String]
        for (first <- Vector(true)) withRuntime(root) { (owner, component, actor, prepare) =>
          if (first) prepare(call => prepareInputsWithFacts(call, F.facts, legacyevidence = legacy))
          When("the real public Start is invoked initially or after reopening the same SQLite runtime")
          val response = owner.executeOperationResponseInContext(Request.of(component = component.name,
            service = "SplitPhaseService", operation = "startSplitPhase",
            arguments = List(Argument("arg1", startWire))), actor)
          Then("no timestamp renewal is required and the existing original Start remains the same invocation")
          val wire = scalar(take(response))
          take(WorkflowWaitJsonV1.decodeC(wire)).handle.instanceIdentity shouldBe F.record.instanceIdentity
          original.foreach(_ shouldBe wire)
          original = Some(wire)
          prepare(call => call.currentC().map { current =>
            current.history.size shouldBe 1
            ()
          })
        }
      }
    }
    for (direction <- Vector("enriched", "resolved", "defer", "cancel", "reasoning-resolved", "reasoning-defer", "reasoning-cancel")) {
      s"E12 session result consumption: continue actual scope through $direction and any assigned reasoning to its complete outcome" in {
        import SplitPhaseExplicitPartitionFixture.{base, enumerate, proof}
        import SplitPhaseExplicitObjectiveSelection as O
        import SplitPhaseScopeSemantic as S
        import SplitPhaseScopeSemanticJson as J
        import SplitPhaseExplicitReasoningSemantic as R
        import SplitPhaseExplicitReasoningSemanticJson as RJ
        import SplitPhaseHumanDirectionAnswerContract as H
        val scopeenriched = direction == "enriched" || direction.startsWith("reasoning-")
        Given("Spec: docs/spec/session-workflow-context-resolution.md; Rule: R5; Example: E12; owned complete source/handoff data and the production three-service factory on managed SQLite")
        val root = Files.createTempDirectory("smwf-direct-scope-")
        val source = base(Vector(260, 180, 260))
        val boundary = proof(source, enumerate(source).candidates.find(_.cutAfterIndices == Vector(0)).get,
          "direct-scope-proposal", handoffPairs = Vector(0 -> 1))
        val objectives = O.Records(
          Some(O.CostPolicy(O.CostPolicyReference("direct-scope-cost", "1"), source.source.reference, source.source.authority,
            O.ProfileIdentity("expensive"), O.ProfileIdentity("lower"), 2, 1, 1)),
          Some(O.DemandRecord(O.DemandReference("direct-scope-demand", "1"), source.source.reference, source.source.authority,
            source.inventory.orderedUnfinishedItems.zipWithIndex.map { (id, index) =>
              O.ItemDemand(id, if (index == 0) O.Demand.Unresolved("one unknown scope-derived classification")
                else O.Demand.SettledExecution)
            })),
          Some(O.OverheadPolicy(O.OverheadPolicyReference("direct-scope-overhead", "1"), source.source.reference,
            source.source.authority, 1, 1, 0, 0, 0)))
        val facts = SplitPhaseExplicitPartitionContext.Facts(boundary.shape.records, boundary.records, source.records,
          F.facts.planning.copy(objectives = objectives))
        val command = F.command.copy(input = F.command.input.copy(proposalReference = Some(boundary.shape.records.proposal.reference.identity)))
        val start = typedStart.copy(command = command, selection = F.selectionFor(command),
          source = source.source, proposal = Some(boundary.shape.records.proposal))
        val advance = take(SplitPhaseAdvanceCommandJsonV1.encodeC(SplitPhaseAdvanceCommand(start.instanceIdentity.value,
          SplitPhasePartitionContextReference(F.context.reference.identity, F.context.reference.revision))))
        var pending = Option.empty[String]
        var scopereply = Option.empty[ContinuationResult[S.Judgment]]
        var scopeack = Option.empty[String]
        var question = Option.empty[WorkflowInteraction[SplitPhaseNativeHumanDirection.Input, Nothing]]
        var humanreply = Option.empty[ContinuationResult[H.Answer]]
        var completed = Option.empty[InstanceRecord]
        var terminal = Option.empty[String]
        def resultFor[A, B](work: WorkflowInteraction[A, Nothing], value: B, typeIdentity: String,
          reference: ContextReference, evidence: ContextReference): ContinuationResult[B] = {
          val order = work.current match {
            case value: WorkflowContinuation.WorkOrder[A] => value.request
            case value: WorkflowContinuation.Decision[A] => value.request
            case other => fail("Expected assigned work or decision: " + other)
          }
          ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision, order.context.snapshot,
            TypedValue(typeIdentity, value), reference, Vector.empty,
            ExecutionEvidence(Vector(evidence), Some(F.command.participantIdentity)))
        }
        withRuntime(root) { (owner, component, actor, prepare) =>
          def invoke(operation: String, wire: String) = scalar(take(owner.executeOperationResponseInContext(
            Request.of(component = component.name, service = "SplitPhaseService", operation = operation,
              arguments = List(Argument("arg1", wire))), actor)))
          prepare(call => prepareInputsWithFacts(call, facts, start))
          When("public Start records the selected source and public advance asks for the actual handoff scope")
          take(WorkflowWaitJsonV1.decodeC(invoke("startSplitPhase", SplitPhaseStartServiceOperation.encodeCommand(command))))
          prepare(call => call.currentC().flatMap { state =>
            val context = F.context.copy(facts = facts, owner = F.context.owner.copy(start = start.reference,
              handle = take(WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, state)), instanceRevision = state.revision))
            take(SplitPhaseManagedPartitionContextRegistration.bindC(owner.configuration, declaration, F.catalog))
              .registerForCallC(context, call.core).map(_ => ())
          })
          val wire = invoke("advanceSplitPhase", advance)
          val issued = take(WorkflowWorkOrderJsonV1.decodeC(wire, J.requestCodec))
          val input = issued.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value
          Then("scope is assigned from the complete source frontier rather than a caller-selected winner")
          input.gaps.map(_.original.identity.value) shouldBe Vector("handoff-0")
          input.source shouldBe source.source.reference
          val evidence = SplitPhaseExplicitPartitionSemantic.EvidenceReference("direct-scope-evidence", "1")
          val judgment = S.Judgment(
            if (scopeenriched) S.Decision.Enriched else S.Decision.DecisionNeeded,
            "Specification interpretation of the assigned transfer",
            if (scopeenriched) input.gaps.map(gap => S.Scope(gap.original.identity,
              gap.producingItems.takeRight(1), gap.receivingItems.take(1), "original transfer ranges")) else Vector.empty,
            Vector(evidence))
          scopereply = Some(resultFor(issued, judgment, J.resultCodec.typeIdentity,
            ContextReference(input.result.identity, input.result.revision), ContextReference(evidence.identity, evidence.revision)))
          pending = Some(wire)
          prepare(call => call.currentC().map(state => { completed = Some(state); () }))
        }
        When("a separate JVM observes the scope WorkOrder before any result is supplied")
        var observesuccessor: ActionCall.Core => Consequence[Unit] = _ => Consequence.unit
        withRuntime(root) { (owner, component, actor, prepare) =>
          def invokeC(operation: String, wire: String) = owner.executeOperationResponseInContext(
            Request.of(component = component.name, service = "SplitPhaseService", operation = operation,
              arguments = List(Argument("arg1", wire))), actor)
          def invoke(operation: String, wire: String) = scalar(take(invokeC(operation, wire)))
          def current(): InstanceRecord = {
            var state = Option.empty[InstanceRecord]
            prepare(call => call.currentC().map(value => { state = Some(value); () }))
            state.get
          }
          When("a crossed scope result reference is returned through its public completion")
          val before = current()
          val crossed = scopereply.get.copy(resultReference = ContextReference("foreign-scope-result", "1"))
          invokeC("submitHandoffScopeResult", take(WorkflowResultJsonV1.encodeC(crossed, J.resultCodec))).isSuccess shouldBe false
          Then("the saved assignment and complete native state remain unchanged")
          current() shouldBe before
          invoke("advanceSplitPhase", advance) shouldBe pending.get
          When("only the semantic scope result is returned without destination, context or checkpoint echoes")
          val reply = take(NativeWorkflowResultSubmissionFixture.encodeC(scopereply.get, J.resultCodec, omitReference = true))
          val replyjson = take(io.circe.parser.parse(reply).fold(_ => Consequence.stateConflict("invalid reply"), Consequence.success))
          replyjson.hcursor.downField("result").focus.get.asObject.get.keys.toSet shouldBe Set("value")
          replyjson.hcursor.downField("result").downField("value").downField("payload").focus.get.asObject.get.keys.toSet shouldBe
            Set("decision", "rationale", "scopes", "evidence")
          val legacyreply = replyjson.hcursor.downField("result").downField("value").downField("payload")
            .withFocus(_.mapObject(_.add("context", Json.fromString("obsolete-context"))
              .add("checkpoint", Json.Null).add("nextOperation", Json.fromString("apply")))).top.get.noSpaces
          val acknowledgement = invoke("submitHandoffScopeResult", legacyreply)
          scopeack = Some(acknowledgement)
          val after = current()
          if (!scopeenriched) {
            When("owning advance asks Human direction for the saved DecisionNeeded frontier")
            val wire = invoke("advanceSplitPhase", advance)
            question = Some(take(WorkflowDecisionJsonV1.decodeC(wire, SplitPhaseNativeHumanDirection.inputCodec)))
            val input = question.get.current.asInstanceOf[WorkflowContinuation.Decision[SplitPhaseNativeHumanDirection.Input]]
              .request.input.get.value
            val frontier = take(SplitPhaseNativeHumanDirection.frontierC(input))
            Then("the question retains the complete original source and published frontier")
            frontier.first.originalContext.facts shouldBe facts
            val answer = direction match {
              case "resolved" => H.Answer.ProvideResolution(SplitPhaseExplicitPartitionSemantic.ContextReference("direct-human-resolution", "1"))
              case "defer" => H.Answer.Defer
              case "cancel" => H.Answer.Cancel
            }
            humanreply = Some(resultFor(question.get, answer, H.resultType,
              ContextReference("direct-human-evidence", "1"), ContextReference("direct-human-evidence", "1")))
            pending = Some(wire)
            completed = Some(current())
          }
        }
        if (question.nonEmpty) {
          When("another JVM observes the original saved Human question without issuer or answer reconstruction")
          withRuntime(root) { (owner, component, actor, prepare) =>
            def invokeC(operation: String, wire: String) = owner.executeOperationResponseInContext(
              Request.of(component = component.name, service = "SplitPhaseService", operation = operation,
                arguments = List(Argument("arg1", wire))), actor)
            def current(): InstanceRecord = {
              var state = Option.empty[InstanceRecord]
              prepare(call => call.currentC().map(value => { state = Some(value); () }))
              state.get
            }
            val reply = take(NativeWorkflowResultSubmissionFixture.encodeC(humanreply.get, H.payloadCodec))
            val before = current()
            When("an answer is submitted without separately supplied host evidence")
            invokeC("submitSplitHumanDirection", reply).isSuccess shouldBe false
            Then("neither the native question nor state is changed")
            current() shouldBe before
            Given("specification-owned host evidence bound to this exact Human question and answer")
            prepare(_.humanEvidenceC(question.get, humanreply.get))
            When("the verified answer is returned through its public completion")
            val acknowledgement = scalar(take(invokeC("submitSplitHumanDirection", reply)))
            val after = current()
            Then("direction is saved once without generating a plan, reasoning result or document effect")
            after.history.dropRight(1) shouldBe before.history
            after.lifecycle shouldBe Lifecycle.Active
            after.currentProgression shouldBe Some(ProgressionReference("HumanAnswered"))
            if (direction == "resolved") {
              Given("separate passive Human resolution data for exactly the answered whole frontier")
              val input = question.get.current.asInstanceOf[WorkflowContinuation.Decision[SplitPhaseNativeHumanDirection.Input]]
                .request.input.get.value
              val request = take(S.requestC(take(SplitPhaseNativeHumanDirection.frontierC(input)).first))
              val resolution = SplitPhaseHumanScopeResolution.Record(
                SplitPhaseHumanScopeResolution.Reference("direct-human-resolution", "1"), request.context, request.checkpoint,
                input.scopeCheckpoint, SplitPhaseHumanScopeResolution.DecisionReference(humanreply.get.continuationId.value),
                request.gaps.map(gap => S.Scope(gap.original.identity, gap.producingItems.takeRight(1),
                  gap.receivingItems.take(1), "Specification Human interpretation of original ranges")),
                "Complete assigned scope only", Vector(SplitPhaseExplicitPartitionSemantic.EvidenceReference("direct-human-evidence", "1")))
              prepare(_.createC(collection("split_humanresolutions"), resolution.reference.identity,
                Record.data(SplitPhaseHumanScopeResolutionJsonV1.storageField ->
                  take(SplitPhaseHumanScopeResolutionJsonV1.encodeStorageC(resolution)))))
              current() shouldBe after
            }
          }
        }
        withRuntime(root, advanceobservation = Some(core => observesuccessor(core))) { (owner, component, actor, prepare) =>
          def invokeC(operation: String, wire: String) = owner.executeOperationResponseInContext(
            Request.of(component = component.name, service = "SplitPhaseService", operation = operation,
              arguments = List(Argument("arg1", wire))), actor)
          def invoke(operation: String, wire: String) = scalar(take(invokeC(operation, wire)))
          def current(): InstanceRecord = {
            var state = Option.empty[InstanceRecord]
            prepare(call => call.currentC().map(value => { state = Some(value); () }))
            state.get
          }
          if (scopeenriched || direction == "resolved") {
            When("owning advance derives only unknown reasoning work from the completed scope frontier")
            val wire = invoke("advanceSplitPhase", advance)
            val issued = take(WorkflowWorkOrderJsonV1.decodeC(wire, RJ.requestCodec))
            val input = issued.current.asInstanceOf[WorkflowContinuation.WorkOrder[R.Request]].request.input.get.value
            Then("known work and winning-partition selection are absent from the issued judgment task")
            input.items.map(_.identity) shouldBe Vector(source.inventory.orderedUnfinishedItems.head)
            input.items.head.closure shouldBe source.sourceBoundary.items.head
            input.authority shouldBe source.source.authority
            val evidence = SplitPhaseExplicitPartitionSemantic.EvidenceReference("direct-reasoning-evidence", "1")
            val judgment = R.Judgment(if (direction.startsWith("reasoning-")) R.Decision.DecisionNeeded else R.Decision.Enriched, "Classify only assigned unknown work",
              if (direction.startsWith("reasoning-")) Vector.empty else input.items.map(item => R.Assignment(item.identity, R.Classification.SettledExecution, "bounded implementation")),
              Vector(evidence))
            val result = resultFor(issued, judgment, RJ.resultCodec.typeIdentity,
              ContextReference(input.result.identity, input.result.revision), ContextReference(evidence.identity, evidence.revision))
            val before = current()
            When("the reasoning completion crosses the Workflow-assigned result reference")
            invokeC("submitReasoningResult", take(WorkflowResultJsonV1.encodeC(result.copy(
              resultReference = ContextReference("foreign-reasoning-result", "1")), RJ.resultCodec))).isSuccess shouldBe false
            Then("the exact pending assignment and native state are retained")
            current() shouldBe before
            When("classification is returned without destination or context echoes and deterministic progression resumes")
            val reply = take(NativeWorkflowResultSubmissionFixture.encodeC(result, RJ.resultCodec, omitReference = true))
            val replyjson = take(io.circe.parser.parse(reply).fold(_ => Consequence.stateConflict("invalid reply"), Consequence.success))
            replyjson.hcursor.downField("result").focus.get.asObject.get.keys.toSet shouldBe Set("value")
            replyjson.hcursor.downField("result").downField("value").downField("payload").focus.get.asObject.get.keys.toSet shouldBe
              Set("decision", "rationale", "assignments", "evidence")
            val legacyreply = replyjson.hcursor.downField("result").downField("value").downField("payload")
              .withFocus(_.mapObject(_.add("context", Json.fromBoolean(false))
                .add("nextOperation", Json.fromString("apply")))).top.get.noSpaces
            val acknowledgement = invoke("submitReasoningResult", legacyreply)
            val after = current()
            if (direction.startsWith("reasoning-")) {
              import SplitPhaseReasoningHumanDirection as RH
              import SplitPhaseHumanReasoningResolution as HR
              When("advance presents a genuine reasoning DecisionNeeded through its separate Required Operation")
              val questionwire = invoke("advanceSplitPhase", advance)
              val reasoningquestion = take(WorkflowDecisionJsonV1.decodeC(questionwire, RH.inputCodec))
              val decision = reasoningquestion.current.asInstanceOf[WorkflowContinuation.Decision[RH.Input]]
              val questioninput = decision.request.input.get.value
              Then("the complete original reasoning WorkOrder, saved AI result and planning frontier survive the Human question")
              decision.request.requiredOperation.capability shouldBe "reasoning-human-direction-capability"
              take(WorkflowWorkOrderJsonV1.decodeC(questioninput.reasoningWork, RJ.requestCodec)) shouldBe issued
              take(WorkflowResultJsonV1.decodeC(questioninput.reasoningResult, RJ.resultCodec)) shouldBe result
              take(SplitPhasePlanningSnapshotJsonV1.decodeC(questioninput.frontier)).selection.proposal.source.reference shouldBe source.source.reference
              Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R4; Example: E12; a genuine reasoning Human input")
              val encoded = RH.inputCodec.encode(questioninput)
              val body = encoded.hcursor.downField("payload").focus.get
              val malformed = Vector(body.mapObject(_.remove("reasoningWork")), body.mapObject(_.add("reasoningResult", Json.True)),
                body.mapObject(fields => fields.add("context", fields("context").get.mapObject(_.remove("revision")))))
              When("the public input loses consumed work or context fields or has wrong nested types")
              val refusedInputs = malformed.map(value => RH.inputCodec.decode(encoded.mapObject(_.add("payload", value))))
              Then("typed input decoding rejects missing or malformed consumed data without re-admitting complete saved copies")
              refusedInputs.foreach(_.isLeft shouldBe true)
              Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R18; Example: E38; inert all-depth annotations on the actual saved reasoning question")
              When("the private fixture annotates only the application input in the stored Decision")
              prepare(_.rewriteFixtureRowC(declaration.continuations, decision.request.continuationId.value,
                "issuedWorkPayload", "cncf.workflow-json.v1:")(json =>
                json.hcursor.downField("request").downField("input").downField("value")
                  .withFocus(_with_unused_fields).top.get))
              Then("typed data and the genuine owning pending advance remain identical without annotation authority")
              RH.inputCodec.decode(_with_unused_fields(encoded)) shouldBe Right(questioninput)
              invoke("advanceSplitPhase", advance) shouldBe questionwire
              val interpretation = R.Judgment(R.Decision.Enriched, "Human interpretation of only the unknown items",
                input.items.map(item => R.Assignment(item.identity, R.Classification.SettledExecution, "Human bounded execution")),
                Vector(SplitPhaseExplicitPartitionSemantic.EvidenceReference("reasoning-human-interpretation", "1")))
              val resolutionreference = SplitPhaseExplicitPartitionSemantic.ContextReference("reasoning-human-resolution", "1")
              val answer = direction match {
                case "reasoning-resolved" => H.Answer.ProvideResolution(resolutionreference)
                case "reasoning-defer" => H.Answer.Defer
                case "reasoning-cancel" => H.Answer.Cancel
              }
              def _answer_(answer: H.Answer, reference: String) = {
                val value = resultFor(reasoningquestion, answer, H.resultType,
                  ContextReference(reference, "1"), ContextReference("reasoning-human-interpretation", "1"))
                value
              }
              val humananswer = _answer_(answer, "reasoning-human-answer")
              val answerjson = io.circe.parser.parse(take(WorkflowResultJsonV1.encodeC(humananswer, H.payloadCodec))).toOption.get
              val answerwire = answerjson.hcursor.downField("result").downField("value")
                .withFocus(_with_unused_fields).top.get.noSpaces
              val humanbefore = current()
              When("a reasoning answer has no independent host evidence")
              invokeC("submitSplitHumanDirection", answerwire).isSuccess shouldBe false
              Then("the exact saved question/native history remain pending")
              current() shouldBe humanbefore
              Given("host evidence for a different answer rather than consent for the submitted complete answer")
              prepare(_.humanEvidenceC(reasoningquestion, _answer_(
                if (answer == H.Answer.Cancel) H.Answer.Defer else H.Answer.Cancel, "reasoning-human-answer")))
              When("the original answer is submitted against crossed evidence")
              invokeC("submitSplitHumanDirection", answerwire).isSuccess shouldBe false
              Then("crossed evidence creates neither a result nor partial planning data")
              current() shouldBe humanbefore
              // Correct evidence uses a new independently issued result reference;
              // the existing crossed host record is not overwritten or reinterpreted.
              val admittedanswer = _answer_(answer, "reasoning-human-admitted-answer")
              val admittedjson = io.circe.parser.parse(take(NativeWorkflowResultSubmissionFixture.encodeC(admittedanswer, H.payloadCodec))).toOption.get
              val admittedwire = admittedjson.hcursor.downField("result").downField("value")
                .withFocus(_with_unused_fields).top.get.noSpaces
              if (direction == "reasoning-resolved") {
                val invalids = Vector(
                  "incomplete" -> interpretation.copy(assignments = Vector.empty),
                  "crossed-item" -> interpretation.copy(assignments = interpretation.assignments.map(_.copy(
                    identity = SplitPhaseExplicitProposalPrecheck.WorkItemIdentity("foreign-item")))),
                  "repeated-item" -> interpretation.copy(assignments = interpretation.assignments ++ interpretation.assignments),
                  "crossed-evidence" -> interpretation.copy(evidence = Vector(
                    SplitPhaseExplicitPartitionSemantic.EvidenceReference("foreign-human-evidence", "1"))))
                for ((episode, data) <- invalids) {
                  Given("separate passive but invalid " + episode + " interpretation data for the actual question")
                  val reference = SplitPhaseExplicitPartitionSemantic.ContextReference("reasoning-bad-" + episode, "1")
                  val record = HR.Record(reference, decision.request.continuationId.value, result.resultReference, data)
                  prepare(_.createC(collection("split_humanresolutions"), reference.identity,
                    Record.data(HR.storageField -> take(HR.encodeC(record)))))
                  val submitted = _answer_(H.Answer.ProvideResolution(reference), "reasoning-bad-answer-" + episode)
                  prepare(_.humanEvidenceC(reasoningquestion, submitted))
                  When("the independently evidenced answer selects incomplete or crossed reasoning classifications")
                  invokeC("submitSplitHumanDirection", take(WorkflowResultJsonV1.encodeC(submitted, H.payloadCodec))).isSuccess shouldBe false
                  Then("validation refuses completion without partial plan or native append")
                  current() shouldBe humanbefore
                  invoke("advanceSplitPhase", advance) shouldBe questionwire
                }
                for (episode <- Vector("crossed-decision", "crossed-reasoning-result")) {
                  Given("a complete interpretation that names a foreign " + episode)
                  val reference = SplitPhaseExplicitPartitionSemantic.ContextReference("reasoning-bad-" + episode, "1")
                  val record = HR.Record(reference,
                    if (episode == "crossed-decision") "foreign-decision" else decision.request.continuationId.value,
                    if (episode == "crossed-reasoning-result") ContextReference("foreign-ai-result", "1") else result.resultReference,
                    interpretation)
                  prepare(_.createC(collection("split_humanresolutions"), reference.identity,
                    Record.data(HR.storageField -> take(HR.encodeC(record)))))
                  val submitted = _answer_(H.Answer.ProvideResolution(reference), "reasoning-bad-answer-" + episode)
                  prepare(_.humanEvidenceC(reasoningquestion, submitted))
                  When("the verified Human answer selects the crossed interpretation")
                  invokeC("submitSplitHumanDirection", take(WorkflowResultJsonV1.encodeC(submitted, H.payloadCodec))).isSuccess shouldBe false
                  Then("question and AI DecisionNeeded remain canonical and no partial plan is selected")
                  current() shouldBe humanbefore
                }
                Given("complete ordered Human interpretation linked to this exact Decision and retained reasoning result")
                val record = HR.Record(resolutionreference, decision.request.continuationId.value, result.resultReference, interpretation)
                val resolutionjson = io.circe.parser.parse(take(HR.encodeC(record)).substring(HR.schema.length + 1)).toOption.get
                prepare(_.createC(collection("split_humanresolutions"), record.reference.identity,
                  Record.data(HR.storageField -> (HR.schema + ":" + _with_unused_fields(resolutionjson).noSpaces),
                    "unusedDiagnostic" -> "inert annotation")))
              }
              Given("independently issued evidence for the exact genuine reasoning question and complete answer")
              prepare(_.humanEvidenceC(reasoningquestion, admittedanswer))
              Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R19; Example: E40; the actual pending entry with a supported non-scope causation difference")
              val diagnosticcausation = CausationReference("reasoning-human-pending-diagnostic")
              val pendingdiagnostic = humanbefore.copy(causation = diagnosticcausation,
                history = humanbefore.history.init :+ humanbefore.history.last.copy(causation = diagnosticcausation))
              def _store_current_(value: InstanceRecord): Unit = {
                val wire = take(WorkflowInstanceRecordJsonV1.encodeC(value))
                val json = io.circe.parser.parse(wire).toOption.get
                prepare(_.rewriteFixtureRowC(declaration.instances, value.identity.value,
                  "recordPayload", "cncf.workflow-json.v1:")(_ => json))
              }
              When("the private fixture stores that existing valid entry without changing its revision or suspension target")
              _store_current_(pendingdiagnostic)
              Then("the actual owning native reader accepts it without substituting the reconstructed issue state")
              current() shouldBe pendingdiagnostic
              val successorkey = "reasoning-human-successor:" + take(WorkflowMechanicalTransition.keyC(
                WorkflowMechanicalTransition.Reference(admittedanswer.continuationId.value, admittedanswer.expectedRevision.value)))
              val suspension = pendingdiagnostic.suspension.get
              val crossedboundaries = Vector(
                suspension.copy(continuationIdentity = org.goldenport.cncf.workflow.WorkflowInstancePersistence.ContinuationIdentity(
                  "foreign-reasoning-human-suspension")),
                suspension.copy(contextSnapshot = ContextSnapshotReference("foreign-reasoning-human-context")))
              val nativeanswerwire = take(WorkflowResultJsonV1.encodeC(admittedanswer, H.payloadCodec))
              for (boundary <- crossedboundaries) {
                Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R19; Example: E41; the actual evidenced answer but a crossed native suspension target")
                val crossed = pendingdiagnostic.copy(suspension = Some(boundary),
                  history = pendingdiagnostic.history.init :+ pendingdiagnostic.history.last.copy(suspension = Some(boundary)))
                _store_current_(crossed)
                When("the real public Operation receives the full actual answer and attempts managed completion against that incompatible native target")
                val refused = invokeC("submitSplitHumanDirection", nativeanswerwire)
                Then("native suspension admission refuses it before publishing either completion or staged successor")
                refused.isSuccess shouldBe false
                refused.toString should include("current suspension boundary")
                current() shouldBe crossed
                prepare { setup => for {
                  completed <- setup.reasoningHumanCompletionExistsC(admittedanswer.continuationId)
                  successor <- setup.fixtureRowExistsC(collection("split_checkpoints"), successorkey)
                } yield {
                  completed shouldBe false
                  successor shouldBe false
                }}
                When("the private fixture restores only the original pending suspension, not a copied issue certificate")
                _store_current_(pendingdiagnostic)
                Then("actual pending causation and original history are retained")
                current() shouldBe pendingdiagnostic
              }
              When("the unchanged public Human completion receives the assigned answer")
              val humanack = invoke("submitSplitHumanDirection", admittedwire)
              val humanafter = current()
              Then("one managed completion saves the answer and successor, preserving the original AI DecisionNeeded")
              humanafter.history.dropRight(1) shouldBe pendingdiagnostic.history
              Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R18; Example: E38; the actual atomic successor with inert row and body annotations")
              When("the private fixture annotates the retained successor without changing its actual answer or downstream data")
              prepare(_.rewriteFixtureRowC(collection("split_checkpoints"), successorkey,
                "splitPhaseReasoningHumanSuccessorPayload", "sm-workflow.reasoning-human-successor.v1:", annotateRow = true)(_with_unused_fields))
              Then("the native completed state is unaffected before owning observation and physical reopen")
              current() shouldBe humanafter
              Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R6; Example: E18; the actual managed Reasoning Human successor")
              val humanquery = take(RH.commandC(reasoningquestion))
              var retainedobservations = 0
              observesuccessor = core => SplitPhaseManagedReasoningHumanCompletion.observeForAdvanceC(humanquery,
                declaration, F.catalog, core).map { saved =>
                retainedobservations += 1
                Then("the actual adjacent native state is selected for both resolved and stopped directions")
                saved.next shouldBe humanafter
                ()
              }
              When("the actual public advance observes and consumes the retained Human successor with its own Core")
              val advancedwire = invoke("advanceSplitPhase", advance)
              observesuccessor = _ => Consequence.unit
              Then("exactly one observation precedes the genuine owning advance")
              retainedobservations shouldBe 1
              val savedwire = advancedwire
              if (direction == "reasoning-resolved") {
                val preview = take(WorkflowTerminalJsonV1.decodeC(savedwire, SplitPhaseManagedSelectedPreview.payloadCodec))
                val payload = preview.current.asInstanceOf[WorkflowContinuation.Terminal[Json]].result.value
                Then("the deterministic complete selected Preview contains every original item and writes no documents")
                payload.hcursor.get[Vector[Json]]("children").toOption.get
                  .flatMap(_.hcursor.get[Vector[Json]]("orderedWorkItems").toOption.get)
                  .map(_.hcursor.get[String]("identity").toOption.get) shouldBe source.inventory.orderedUnfinishedItems.map(_.value)
              } else {
                val stopped = take(WorkflowTerminalJsonV1.decodeC(savedwire, SplitPhaseHumanTerminal.payloadCodec))
                val payload = stopped.current.asInstanceOf[WorkflowContinuation.Terminal[SplitPhaseHumanTerminal.Result]].result.value
                Then("Defer or Cancel retains the exact Human direction in a typed stopped terminal")
                payload.outcome shouldBe (if (direction == "reasoning-defer") SplitPhaseHumanTerminal.Outcome.Blocked
                  else SplitPhaseHumanTerminal.Outcome.Cancelled)
                payload.decision shouldBe admittedanswer.continuationId
                payload.answer shouldBe admittedanswer.resultReference
                payload.context shouldBe input.context
                val terminaljson = SplitPhaseHumanTerminal.payloadCodec.encode(payload)
                When("the stopped-terminal reader consumes inert fields beside the actual native observation")
                val annotated = SplitPhaseHumanTerminal.payloadCodec.decode(_with_unused_fields(terminaljson))
                Then("the passive stopped data preserves the selected direction without another transition")
                annotated shouldBe Right(payload)
              }
              terminal = Some(savedwire)
              Files.exists(root.resolve("docs/phase/phase-77.md")) shouldBe false
            } else {
            val previewwire = invoke("advanceSplitPhase", advance)
            val preview = take(WorkflowTerminalJsonV1.decodeC(previewwire, SplitPhaseManagedSelectedPreview.payloadCodec))

            val payload = preview.current.asInstanceOf[WorkflowContinuation.Terminal[Json]].result.value
            Then("the selected complete Preview contains every original work item without applying documents")
            payload.hcursor.get[String]("mode").toOption shouldBe Some("PREVIEW")
            payload.hcursor.get[Vector[Json]]("children").toOption.get
              .flatMap(_.hcursor.get[Vector[Json]]("orderedWorkItems").toOption.get)
              .map(_.hcursor.get[String]("identity").toOption.get) shouldBe source.inventory.orderedUnfinishedItems.map(_.value)
            Files.exists(root.resolve("docs/phase/phase-77.md")) shouldBe false
            terminal = Some(previewwire)
            }
          } else {
            When("owning advance applies only the retained Human Defer or Cancel direction")
            val wire = invoke("advanceSplitPhase", advance)
            val stopped = take(WorkflowTerminalJsonV1.decodeC(wire, SplitPhaseHumanTerminal.payloadCodec))
            val payload = stopped.current.asInstanceOf[WorkflowContinuation.Terminal[SplitPhaseHumanTerminal.Result]].result.value
            Then("the terminal retains its original context, Decision and answer without issuing reasoning or writing documents")
            payload.outcome shouldBe (if (direction == "defer") SplitPhaseHumanTerminal.Outcome.Blocked else SplitPhaseHumanTerminal.Outcome.Cancelled)
            payload.context shouldBe F.context.reference
            payload.decision shouldBe humanreply.get.continuationId
            payload.answer shouldBe humanreply.get.resultReference
            terminal = Some(wire)
          }
          completed = Some(current())
          completed.get.lifecycle shouldBe Lifecycle.Completed
          completed.get.suspension shouldBe None
        }
      }
    }
    for (episode <- Vector("normal", "occupied child")) {
      s"apply SplitPhase selected documents safely through direct Operations for $episode" in {
        import SplitPhaseExplicitPartitionRawCodecs.given
        import io.circe.syntax.*
        import org.simplemodeling.textus.smworkflow.runtime.SelectedPhaseDocumentProgram as Program
        Given("a spec-owned Phase document and an APPLY selection, with a file-configured canonical runtime process capability")
        val source = SplitPhaseExplicitPartitionFixture.base(Vector(360, 360))
        val command = F.command.copy(input = F.command.input.copy(invocationMode = "APPLY",
          proposalReference = Some(source.initial.shape.records.proposal.reference.identity)))
        val start = typedStart.copy(command = command, selection = F.selectionFor(command), source = source.source,
          proposal = Some(source.initial.shape.records.proposal))
        val facts = SplitPhaseExplicitPartitionContext.Facts(source.initial.shape.records, source.initial.records, source.records,
          SplitPhasePlanningCheckpointFixture.knownFacts.planning.copy(source = source.source.reference,
            authority = source.source.authority, objectives = SplitPhaseExplicitObjectiveSelection.Records(None, None, None)))
        val work = Files.createDirectories(Path.of("target/sm-workflow/work/direct-operation-apply").toAbsolutePath)
        // Retained as diagnostic evidence; only this spec-owned directory is used.
        val root = Files.createTempDirectory(work, "repository-")
        Files.createDirectories(root.resolve("docs/phase"))
        val phase = root.resolve("docs/phase/phase-77.md")
        val child = root.resolve("docs/phase/phase-77.1.md")
        val original = (Vector("# Phase 77", "", "## Scope", "", "| ID | Outcome | Status |", "| --- | --- | --- |") ++
          source.inventory.orderedUnfinishedItems.map(id => s"| ${id.value} | original work | planned |") ++
          Vector("| old-completed | accepted work | completed |", "", "## History", "", "Keep unrelated history.", "",
            "## Source boundary", "", "```" + Program.sourceSchema, source.sourceBoundary.asJson.noSpaces, "```", "")).mkString("\n")
        Files.writeString(phase, original)
        if (episode == "occupied child") Files.writeString(child, "Independent existing child\n")
        val advance = take(SplitPhaseAdvanceCommandJsonV1.encodeC(SplitPhaseAdvanceCommand(start.instanceIdentity.value,
          SplitPhasePartitionContextReference(F.context.reference.identity, F.context.reference.revision))))
        var outcome = Option.empty[String]
        var native = Option.empty[InstanceRecord]
        var times = Option.empty[(java.nio.file.attribute.FileTime, java.nio.file.attribute.FileTime)]
        for (session <- Vector("apply")) withRuntime(root, Some(_document_process_plan(root))) {
          (owner, component, actor, prepare) =>
          def _invoke_c_(operation: String, wire: String) = owner.executeOperationResponseInContext(
            Request.of(component = component.name, service = "SplitPhaseService", operation = operation,
              arguments = List(Argument("arg1", wire))), actor)
          def _current_(): InstanceRecord = {
            var state = Option.empty[InstanceRecord]
            prepare(call => call.currentC().map(value => { state = Some(value); () }))
            state.get
          }
          if (session == "apply") {
            prepare(call => prepareInputsWithFacts(call, facts, start))
            When("the public Start creates its own APPLY instance from the selected input")
            take(WorkflowWaitJsonV1.decodeC(scalar(take(_invoke_c_("startSplitPhase", SplitPhaseStartServiceOperation.encodeCommand(command))))))
            prepare { call => call.currentC().flatMap { state =>
              val context = F.context.copy(facts = facts, owner = F.context.owner.copy(start = start.reference,
                handle = take(WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, state)), instanceRevision = state.revision))
              take(SplitPhaseManagedPartitionContextRegistration.bindC(owner.configuration, declaration, F.catalog))
                .registerForCallC(context, call.core).map(_ => ())
            } }
            Then("only Start's actual native history exists before the Workflow progresses")
            _current_().history.size shouldBe 1
          }
          When("public advance owns selection and actual document effects for " + session)
          val result = _invoke_c_("advanceSplitPhase", advance)
          if (episode == "normal") {
            val wire = scalar(take(result))
            val terminal = take(WorkflowTerminalJsonV1.decodeC(wire, SplitPhaseManagedSelectedApply.payloadCodec))
            val payload = terminal.current.asInstanceOf[WorkflowContinuation.Terminal[Json]].result.value
            val state = _current_()
            Then("the actual applied receipt and document content agree with the durable selected plan")
            payload.hcursor.get[String]("mode").toOption shouldBe Some("APPLY")
            payload.hcursor.downField("effect").get[String]("status").toOption shouldBe Some("applied")
            val documents = payload.hcursor.downField("documentProjection").get[Vector[Json]]("documents").toOption.get
            documents.flatMap(_.hcursor.get[Vector[String]]("ownedItems").toOption.get) shouldBe source.inventory.orderedUnfinishedItems.map(_.value)
            documents.tail.foreach { document =>
              Files.readString(root.resolve(document.hcursor.get[String]("relativePath").toOption.get)) shouldBe
                document.hcursor.get[String]("childDocument").toOption.get
            }
            Files.readString(phase) should include("Keep unrelated history.")
            Files.readString(phase) should include("| old-completed | accepted work | completed |")
            state.lifecycle shouldBe Lifecycle.Completed
            state.suspension shouldBe None
            terminal.handle shouldBe take(WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, state))
            outcome.foreach(_ shouldBe wire)
            native.foreach(value => SplitPhaseDefinitionBinding.sameRecord(state, value) shouldBe true)
            times.foreach { case (sourcetime, childtime) =>
              Files.getLastModifiedTime(phase) shouldBe sourcetime
              Files.getLastModifiedTime(child) shouldBe childtime
            }
            outcome = Some(wire)
            native = Some(state)
            times = Some(Files.getLastModifiedTime(phase) -> Files.getLastModifiedTime(child))
          } else {
            Then("an occupied child remains a conflict and is not overwritten or called successful")
            result.isSuccess shouldBe false
            result.toString should include("actual documents conflict with the saved selection")
            Files.readString(phase) shouldBe original
            Files.readString(child) shouldBe "Independent existing child\n"
            val state = _current_()
            state.lifecycle shouldBe Lifecycle.Active
            state.currentProgression.map(_.value) shouldBe Some(SplitPhaseSelectedDocumentEffect.applyingProgression)
            native.foreach(value => SplitPhaseDefinitionBinding.sameRecord(state, value) shouldBe true)
            native = Some(state)
          }
        }
      }
    }
    "E12 session result consumption: complete SplitPhase semantic work to Preview through direct Operations" in {
      import SplitPhaseExplicitPartitionSemantic as S
      import SplitPhaseExplicitPartitionSemanticJson as J
      Given("Spec: docs/spec/session-workflow-context-resolution.md; Rule: R5; Example: E12; specification-owned objective data and production Operations on the same managed SQLite store")
      val root = Files.createTempDirectory("smwf-direct-preview-")
      val facts = _semantic_preview_facts()
      val advance = take(SplitPhaseAdvanceCommandJsonV1.encodeC(SplitPhaseAdvanceCommand(typedStart.instanceIdentity.value,
        SplitPhasePartitionContextReference(F.context.reference.identity, F.context.reference.revision))))
      var pending = Option.empty[String]
      var terminal = Option.empty[String]
      var finalState = Option.empty[InstanceRecord]
      var completedReply = Option.empty[(String, String)]
      for (session <- Vector("issue", "reply after reopen")) withRuntime(root) {
        (owner, component, actor, prepare) =>
        val sessioninput = Json.obj("schemaVersion" -> Json.fromString(NativeWorkflowSessionInput.schema),
          "sessionIdentity" -> Json.fromString("split-preview-session"))
        def _session_wire_(operation: String, wire: String, preserveselector: Boolean): String =
          if (operation == "startSplitPhase") sessioninput.deepMerge(Json.obj("input" -> Json.fromString(wire))).noSpaces
          else if (operation == "advanceSplitPhase" && wire == advance) sessioninput.noSpaces
          else io.circe.parser.parse(wire) match {
            case Right(json) if json.hcursor.get[String]("schemaVersion").contains(NativeWorkflowResultInput.schema) =>
              val selected = if (preserveselector) json else json.mapObject(_.remove("requestIdentity"))
              selected.mapObject(_.add("sessionIdentity", Json.fromString("split-preview-session"))).noSpaces
            case _ => wire
          }
        Given("explicit session resolves the managed instance, saved partition context and sole issued result generation")
        def invokeC(operation: String, wire: String, preserveselector: Boolean = false) = owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "SplitPhaseService", operation = operation,
            arguments = List(Argument("arg1", _session_wire_(operation, wire, preserveselector)))), actor)
        def invoke(operation: String, wire: String) = scalar(take(invokeC(operation, wire)))
        def current(): InstanceRecord = {
          var state = Option.empty[InstanceRecord]
          prepare(call => call.currentC().map(value => { state = Some(value); () }))
          state.get
        }
        if (session == "issue") {
          prepare(call => prepareInputsWithFacts(call, facts))
          When("the public Start creates the instance from the selected input")
          take(WorkflowWaitJsonV1.decodeC(invoke("startSplitPhase", startWire)))
          prepare { call => call.currentC().flatMap { state =>
            val context = F.context.copy(facts = facts, owner = F.context.owner.copy(start = typedStart.reference,
              handle = take(WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, state)), instanceRevision = state.revision))
            take(SplitPhaseManagedPartitionContextRegistration.bindC(owner.configuration, declaration, F.catalog))
              .registerForCallC(context, call.core).map(_ => ())
          } }
          When("advance asks only for the unresolved estimate and semantic boundary")
          val wire = invoke("advanceSplitPhase", advance)
          val work = take(WorkflowWorkOrderJsonV1.decodeC(wire, J.requestCodec))
          val request = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value
          Then("known work is excluded from the requested semantic result")
          request.items.map(_.identity.value) shouldBe Vector("item-1")
          request.boundaries.map(_.edge.afterItem.value) shouldBe Vector("item-0")
          pending = Some(wire)
        } else if (session == "reply after reopen") {
          When("a fresh runtime directly advances the saved pending instance")
          val wire = invoke("advanceSplitPhase", advance)
          Then("the original WorkOrder is returned without a replacement issue")
          wire shouldBe pending.get
          val work = take(WorkflowWorkOrderJsonV1.decodeC(wire, J.requestCodec))
          val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request
          val input = order.input.get.value
          Given("a spec-owned semantic reply to exactly the issued unknown items, not a stage or winner selector")
          val evidence = S.EvidenceReference("direct-semantic-evidence", "1")
          val judgment = S.Judgment(S.Decision.Enriched, "bounded independent semantic closures",
            input.items.map(item => S.NovelEstimate(item.identity, 120, 180, "bounded work", "novel closure")),
            input.boundaries.map(boundary => S.BoundaryJudgment(boundary.edge.afterItem,
              SplitPhasePartitionEnumeration.EdgeDisposition.Either, "independent closure")), Vector(evidence))
          val result = ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision,
            order.context.snapshot, TypedValue(J.resultCodec.typeIdentity, judgment),
            ContextReference(input.reply.result.identity, input.reply.result.revision), Vector.empty,
            ExecutionEvidence(Vector(ContextReference(evidence.identity, evidence.revision)), Some(F.command.participantIdentity)))
          val before = current()
          When("a reply crosses the Workflow-assigned result reference")
          val crossed = result.copy(resultReference = ContextReference("unassigned-result", "1"))
          val rejected = invokeC("submitPartitionSemanticResult", take(WorkflowResultJsonV1.encodeC(crossed, J.resultCodec)))
          Then("the public completion rejects it and preserves native state and the original pending work")
          rejected.isSuccess shouldBe false
          SplitPhaseDefinitionBinding.sameRecord(current(), before) shouldBe true
          invoke("advanceSplitPhase", advance) shouldBe pending.get
          When("a delayed result instead names a different issued request")
          val wrongrequest = result.copy(continuationId = org.goldenport.cncf.workflow.ContinuationIdentity("unissued-request"))
          invokeC("submitPartitionSemanticResult", take(NativeWorkflowResultSubmissionFixture.encodeC(wrongrequest,
            J.resultCodec)), preserveselector = true).isSuccess shouldBe false
          Then("no native state or saved work is changed")
          SplitPhaseDefinitionBinding.sameRecord(current(), before) shouldBe true
          invoke("advanceSplitPhase", advance) shouldBe pending.get
          When("the semantic answer is returned without echoing its assigned destination")
          val resultWire = take(NativeWorkflowResultSubmissionFixture.encodeC(result, J.resultCodec, omitReference = true))
          val submittedfields = take(io.circe.parser.parse(resultWire).left.map(_.message)
            .fold(Consequence.stateConflict(_), Consequence.success)).asObject.get.keys.toSet
          submittedfields shouldBe Set("schemaVersion", "requestIdentity", "result", "completionFacts", "executionEvidence")
          val submittedjson = take(io.circe.parser.parse(resultWire).fold(_ => Consequence.stateConflict("invalid reply"), Consequence.success))
          submittedjson.hcursor.downField("result").focus.get.asObject.get.keys.toSet shouldBe Set("value")
          submittedjson.hcursor.downField("result").downField("value").downField("payload").focus.get.asObject.get.keys.toSet shouldBe
            Set("decision", "rationale", "estimates", "boundaries", "evidence")
          val ack = invoke("submitPartitionSemanticResult", resultWire)
          val completed = current()
          Then("completion commits one adjacent native history entry and is not the final Preview")
          take(WorkflowWaitJsonV1.decodeC(ack)).current shouldBe a[WorkflowContinuation.Wait]
          completed.history.dropRight(1) shouldBe before.history
          completed.revision shouldBe before.revision.next
          completed.lifecycle shouldBe Lifecycle.Active
          completed.suspension shouldBe None
          Given("the actual completion captured PREVIEW while the passive allocation now says APPLY with unreadable unused targets")
          prepare(call => call.rewriteFixtureRowC(collection("split_starts"), typedStart.reference.identity,
            SplitPhaseTypedStartJsonV1.storageField, SplitPhaseTypedStartJsonV1.allocationSchema + ":") { root =>
            root.hcursor.downField("payload").withFocus(_.mapObject { payload =>
              payload.add("command", payload("command").get.hcursor.downField("input")
                .withFocus(_.mapObject(_.add("invocationMode", Json.fromString("APPLY")))).top.get)
                .add("source", Json.Null).add("proposal", Json.False)
            }).top.get
          })
          And("independent current proposal and recommendation data changed after the genuine native completion")
          prepare(call => call.rewriteFixtureRowC(collection("split_proposal"), facts.proposal.proposal.reference.identity,
            SplitPhaseExplicitPlanningInputJsonV1.storageField, SplitPhaseExplicitPlanningInputJsonV1.schema + ":") { root =>
            root.hcursor.downField("payload").withFocus(_.mapObject(payload => payload.add("children",
              Json.fromValues(payload("children").get.asArray.get.map(_.mapObject(_.add("expectedMinutes", Json.fromInt(361)))))))).top.get
          })
          prepare(call => call.rewriteFixtureRowC(collection("split_selections"), typedStart.selection.selectionId,
            ProfileSelectionJsonV1.storageField, "sm-workflow.profile-selection-json.v1:")(
            _.mapObject(_.add("recommendationReference", Json.fromString("changed-current-recommendation")))))
          When("advance absorbs deterministic candidate enumeration, selection and Preview publication")
          val previewWire = invoke("advanceSplitPhase", advance)
          val preview = take(WorkflowTerminalJsonV1.decodeC(previewWire, SplitPhaseManagedSelectedPreview.payloadCodec))
          val payload = preview.current.asInstanceOf[WorkflowContinuation.Terminal[io.circe.Json]].result.value
          val state = current()
          var effectreceipt = true
          prepare(call => call.applicationReceiptExistsC(typedStart.instanceIdentity.value + ":selected-application-effect")
            .map(value => { effectreceipt = value; () }))
          Then("the final Preview covers all source work and carries the actual completed native handle without APPLY effects")
          payload.hcursor.get[String]("mode").toOption.get shouldBe "PREVIEW"
          effectreceipt shouldBe false
          payload.hcursor.get[Vector[io.circe.Json]]("children").toOption.get
            .flatMap(_.hcursor.get[Vector[io.circe.Json]]("orderedWorkItems").toOption.get)
            .map(_.hcursor.get[String]("identity").toOption.get) shouldBe facts.proposal.inventory.orderedUnfinishedItems.map(_.value)
          state.history.dropRight(2) shouldBe completed.history
          state.lifecycle shouldBe Lifecycle.Completed
          state.suspension shouldBe None
          preview.handle shouldBe take(WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, state))
          terminal = Some(previewWire)
          finalState = Some(state)
        } else {
          When("another fresh runtime directly advances the completed instance")
          val observed = invoke("advanceSplitPhase", advance)
          Then("the exact retained Preview and native state return without another append")
          observed shouldBe terminal.get
          SplitPhaseDefinitionBinding.sameRecord(current(), finalState.get) shouldBe true
        }
      }
    }
    "acknowledge actual completion without rereading unused context presentation after closing selection" in {
      import SplitPhaseExplicitPartitionSemantic as S
      import SplitPhaseExplicitPartitionSemanticJson as J
      import SplitPhaseExplicitPartitionContextJsonV1 as C
      Given("the existing Phase5.1 unnecessary-copy policy and one real public Start with managed partition work")
      val root = Files.createTempDirectory("smwf-completion-authoritative-response-")
      val facts = _semantic_preview_facts()
      val advance = take(SplitPhaseAdvanceCommandJsonV1.encodeC(SplitPhaseAdvanceCommand(typedStart.instanceIdentity.value,
        SplitPhasePartitionContextReference(F.context.reference.identity, F.context.reference.revision))))
      var pending = Option.empty[String]
      var pendingstate = Option.empty[InstanceRecord]
      var completionstate = Option.empty[InstanceRecord]
      var acknowledgement = Option.empty[String]
      var changed = 0
      def _request_(component: org.goldenport.cncf.component.Component, operation: String, wire: String): Request =
        Request.of(component = component.name, service = "SplitPhaseService", operation = operation,
          arguments = List(Argument("arg1", wire)))
      def _display_(call: Setup, title: String): Consequence[Unit] =
        call.rewriteFixtureRowC(collection("split_contexts"), F.context.reference.identity, C.storageField, C.schema + ":")(
          _.mapObject(root => root.add("payload", root("payload").get.mapObject(payload =>
            payload.add("owner", payload("owner").get.mapObject(owner => owner.add("presentation",
              owner("presentation").get.mapObject(_.add("title", Json.fromString(title))))))))))
      withRuntime(root) { (owner, component, actor, prepare) =>
        def _invoke_(operation: String, wire: String): String =
          scalar(take(owner.executeOperationResponseInContext(_request_(component, operation, wire), actor)))
        prepare(call => prepareInputsWithFacts(call, facts))
        take(WorkflowWaitJsonV1.decodeC(_invoke_("startSplitPhase", startWire)))
        prepare(call => call.currentC().flatMap { state =>
          val context = F.context.copy(facts = facts, owner = F.context.owner.copy(start = typedStart.reference,
            handle = take(WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, state)), instanceRevision = state.revision))
          take(SplitPhaseManagedPartitionContextRegistration.bindC(owner.configuration, declaration, F.catalog))
            .registerForCallC(context, call.core).map(_ => ())
        })
        When("normal public advance issues the actual semantic assignment")
        pending = Some(_invoke_("advanceSplitPhase", advance))
        prepare(call => call.currentC().map(value => { pendingstate = Some(value); () }))
        Then("native work remains suspended awaiting the actual answer")
        pendingstate.get.suspension should not be empty
      }
      val work = take(WorkflowWorkOrderJsonV1.decodeC(pending.get, J.requestCodec))
      val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request
      val input = order.input.get.value
      val evidence = S.EvidenceReference("completion-response-semantic-evidence", "1")
      val judgment = S.Judgment(S.Decision.Enriched, "actual requested closures",
        input.items.map(item => S.NovelEstimate(item.identity, 120, 180, "bounded work", "novel closure")),
        input.boundaries.map(boundary => S.BoundaryJudgment(boundary.edge.afterItem,
          SplitPhasePartitionEnumeration.EdgeDisposition.Either, "independent closure")), Vector(evidence))
      val result = ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision,
        order.context.snapshot, TypedValue(J.resultCodec.typeIdentity, judgment),
        ContextReference(input.reply.result.identity, input.reply.result.revision), Vector.empty,
        ExecutionEvidence(Vector(ContextReference(evidence.identity, evidence.revision)), Some(F.command.participantIdentity)))
      val reply = take(WorkflowResultJsonV1.encodeC(result, J.resultCodec))
      def _change_display_(core: ActionCall.Core): Consequence[Unit] = {
        Given("the normal completion has selected its genuine closing Plan, but has not committed it")
        val call = new Setup(core, _ => Consequence.unit)
        val before = take(call.currentC())
        When("the specification changes only an unused stored context presentation before the owning commit")
        take(_display_(call, "Compatible updated display"))
        val context = take(SplitPhasePartitionContextReadPolicy.bindC(core.component.get.subsystem.get.configuration, declaration))
        val observed = take(context.loadForCallC(F.context.reference, core)).get
        val after = take(call.currentC())
        Then("real stored display changed without modifying issued work, actual scope or native pending history")
        observed.owner.presentation.title shouldBe "Compatible updated display"
        after shouldBe before
        before shouldBe pendingstate.get
        changed += 1
        Consequence.unit
      }
      withRuntime(root, completionobservation = Some(_change_display_)) { (owner, component, actor, prepare) =>
        When("the real managed completion commits its selected closing and returns its response")
        acknowledgement = Some(scalar(take(owner.executeOperationResponseInContext(
          _request_(component, "submitPartitionSemanticResult", reply), actor))))
        prepare(call => call.currentC().map(value => { completionstate = Some(value); () }))
        val wait = take(WorkflowWaitJsonV1.decodeC(acknowledgement.get)).current.asInstanceOf[WorkflowContinuation.Wait]
        Then("success consumes actual native completion instead of failing a post-success unchanged-context proof")
        changed shouldBe 1
        completionstate.get.history.dropRight(1) shouldBe pendingstate.get.history
        completionstate.get.revision shouldBe pendingstate.get.revision.next
        completionstate.get.suspension shouldBe None
        wait.expectedRevision shouldBe StateMachineRevision(completionstate.get.revision.value.toString)
      }
      withRuntime(root) { (owner, component, actor, prepare) =>
        Given("the later selected advance frontier whose separate Context-copy gate remains outside this increment")
        When("the fixture display is restored before that separate advance consumer")
        prepare(call => _display_(call, F.context.owner.presentation.title))
        val terminal = scalar(take(owner.executeOperationResponseInContext(_request_(component, "advanceSplitPhase", advance), actor)))
        val preview = take(WorkflowTerminalJsonV1.decodeC(terminal, SplitPhaseManagedSelectedPreview.payloadCodec))
        val payload = preview.current.asInstanceOf[WorkflowContinuation.Terminal[Json]].result.value
        var finalstate = Option.empty[InstanceRecord]
        prepare(call => call.currentC().map(value => { finalstate = Some(value); () }))
        Then("normal progression publishes the complete Preview")
        payload.hcursor.get[Vector[Json]]("children").toOption.get
          .flatMap(_.hcursor.get[Vector[Json]]("orderedWorkItems").toOption.get)
          .map(_.hcursor.get[String]("identity").toOption.get) shouldBe facts.proposal.inventory.orderedUnfinishedItems.map(_.value)
        finalstate.get.history.dropRight(2) shouldBe completionstate.get.history
        finalstate.get.lifecycle shouldBe Lifecycle.Completed
      }
    }

  }
}

/** Test-only process entry, not a published sm-workflow command or Operation. */
object ThreeWorkflowSeparateProcessObservation {
  def main(args: Array[String]): Unit = {
    require(args.length == 3, "Expected the specification root, selected input and output paths")
    val root = Path.of(args(0)).toAbsolutePath.normalize
    val input = Path.of(args(1)).toAbsolutePath.normalize
    val output = Path.of(args(2)).toAbsolutePath.normalize
    require(input.startsWith(root) && output.startsWith(root), "Observation files must stay inside the owned specification root")
    require(Files.size(input) <= 131072L, "Observation input exceeds its finite bound")
    val request = io.circe.parser.parse(Files.readString(input)).fold(error => throw new IllegalArgumentException(error.message), identity)
    val observed = new ThreeWorkflowDirectOperationSpec()._observe_separate_process(root, request)
    Files.writeString(output, observed.noSpaces)
  }
}
