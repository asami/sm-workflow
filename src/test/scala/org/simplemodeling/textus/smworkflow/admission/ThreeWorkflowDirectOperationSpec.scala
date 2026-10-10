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
      s"admit SplitPhase Start and reopen it with ${if (legacy) "expired legacy" else "expiry-free current"} host evidence" in {
        Given("the production three-service composition with independently provisioned input and host evidence")
        val root = Files.createTempDirectory("smwf-no-internal-expiry-")
        var original = Option.empty[String]
        for (first <- Vector(true, false)) withRuntime(root) { (owner, component, actor, prepare) =>
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
    for (edit <- Vector("none", "missing-contract", "child-criterion", "child-status",
        "source-criterion", "source-status", "unrelated-history")) {
      s"observe interrupted actual Apply without replaying document writes after $edit" in {
        import SplitPhaseExplicitPartitionRawCodecs.given
        import io.circe.syntax.*
        import org.simplemodeling.textus.smworkflow.runtime.SelectedPhaseDocumentProgram as Program
        Given("owned source documents and a normal managed host with an actual production document program")
        val source = SplitPhaseExplicitPartitionFixture.base(Vector(360, 360))
        val command = F.command.copy(input = F.command.input.copy(invocationMode = "APPLY",
          proposalReference = Some(source.initial.shape.records.proposal.reference.identity)))
        val start = typedStart.copy(command = command, selection = F.selectionFor(command), source = source.source,
          proposal = Some(source.initial.shape.records.proposal))
        val facts = SplitPhaseExplicitPartitionContext.Facts(source.initial.shape.records, source.initial.records, source.records,
          SplitPhasePlanningCheckpointFixture.knownFacts.planning.copy(source = source.source.reference,
            authority = source.source.authority, objectives = SplitPhaseExplicitObjectiveSelection.Records(None, None, None)))
        val work = Files.createDirectories(Path.of("target/sm-workflow/work/direct-operation-apply").toAbsolutePath)
        val root = Files.createTempDirectory(work, "interrupted-repository-")
        Files.createDirectories(root.resolve("docs/phase"))
        val phase = root.resolve("docs/phase/phase-77.md")
        val child = root.resolve("docs/phase/phase-77.1.md")
        Files.writeString(phase, (Vector("# Phase 77", "", "## Scope", "",
          "| ID | Outcome | Status |", "| --- | --- | --- |") ++
          source.inventory.orderedUnfinishedItems.map(id => s"| ${id.value} | original work | planned |") ++
          Vector("| old-completed | accepted work | completed |", "", "## History", "", "Keep unrelated history.", "",
            "## Source boundary", "", "```" + Program.sourceSchema, source.sourceBoundary.asJson.noSpaces, "```", "")).mkString("\n"))
        val advance = take(SplitPhaseAdvanceCommandJsonV1.encodeC(SplitPhaseAdvanceCommand(start.instanceIdentity.value,
          SplitPhasePartitionContextReference(F.context.reference.identity, F.context.reference.revision))))
        withRuntime(root, Some(_document_process_plan(root, recordEffects = true))) { (owner, component, actor, prepare) =>
          prepare(call => prepareInputsWithFacts(call, facts, start))
          When("public Start creates the real invocation before the separate caller performs Apply")
          take(WorkflowWaitJsonV1.decodeC(scalar(take(owner.executeOperationResponseInContext(
            Request.of(component = component.name, service = "SplitPhaseService", operation = "startSplitPhase",
              arguments = List(Argument("arg1", SplitPhaseStartServiceOperation.encodeCommand(command)))), actor)))))
          prepare(call => call.currentC().flatMap { state =>
            val context = F.context.copy(facts = facts, owner = F.context.owner.copy(start = start.reference,
              handle = take(WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, state)), instanceRevision = state.revision))
            take(SplitPhaseManagedPartitionContextRegistration.bindC(owner.configuration, declaration, F.catalog))
              .registerForCallC(context, call.core).map(_ => ())
          })
        }
        When("the owned caller and managed program are stopped after actual writes/readback but before a result returns")
        val stopped = _separate_process_observation(root, "APPLY", advance, recordEffects = true, interrupt = true)
        stopped.hcursor.get[Int]("exit").toOption.get should not be 0
        val countPath = root.resolve("document-apply-invocations.txt")
        val count = Files.readString(countPath)
        count.linesIterator.size shouldBe 1
        count.trim.toLong shouldBe stopped.hcursor.get[Long]("effectPid").toOption.get
        var pending = Option.empty[InstanceRecord]
        withRuntime(root, Some(_document_process_plan(root, recordEffects = true))) { (_, _, _, prepare) =>
          prepare(call => for {
            _ <- call.currentC().map(value => { pending = Some(value); () })
            saved <- call.applicationReceiptExistsC(start.instanceIdentity.value + ":selected-application-effect")
          } yield {
            Then("the real durable intent remains active without a saved successful effect receipt")
            saved shouldBe false
          })
          pending.get.lifecycle shouldBe Lifecycle.Active
          pending.get.currentProgression.map(_.value) shouldBe Some(SplitPhaseSelectedDocumentEffect.applyingProgression)
        }
        Files.exists(child) shouldBe true
        Files.readString(phase) should include("Keep unrelated history.")
        Files.readString(phase) should include("| old-completed | accepted work | completed |")
        val changed = edit != "none" && edit != "unrelated-history"
        if (edit != "none") {
          Given("a separate post-interruption document edit, not a saved Workflow result")
          val editedpath = if (edit.startsWith("source-") || edit == "unrelated-history") phase else child
          val original = Files.readString(editedpath)
          val edited = edit match {
            case "missing-contract" => "Independent post-interruption child edit\n"
            case "child-criterion" | "source-criterion" =>
              val lines = original.split("\n", -1).toVector
              val scope = lines.indexOf("## Scope")
              val row = lines.indexWhere(line => line.startsWith("|") && !line.contains("---") &&
                !line.matches("\\|\\s*ID\\s*\\|.*"), scope + 1)
              val cells = lines(row).split("\\|", -1).toVector
              lines.updated(row, cells.updated(2, " Independently changed criterion ").mkString("|")).mkString("\n")
            case "child-status" | "source-status" =>
              original.replaceFirst("\\| planned \\|", "| completed |")
            case "unrelated-history" => original.replace("Keep unrelated history.", "Preserve this independent history edit.")
          }
          edited should not be original
          Files.writeString(editedpath, edited)
          if (edit != "missing-contract") {
            // The business criterion/status edit must leave the actual embedded
            // selected contract intact; no file hash or copied-content seal.
            val contract = "```" + SplitPhaseSelectedDocuments.childSchema
            val originalstart = original.indexOf(contract)
            val editedstart = edited.indexOf(contract)
            val originalend = original.indexOf("\n```", originalstart + contract.length)
            val editedend = edited.indexOf("\n```", editedstart + contract.length)
            originalstart should be >= 0
            editedstart should be >= 0
            originalend should be > originalstart
            editedend should be > editedstart
            edited.substring(editedstart, editedend) shouldBe original.substring(originalstart, originalend)
          }
        }
        val texts = Files.readString(phase) -> Files.readString(child)
        val times = Files.getLastModifiedTime(phase) -> Files.getLastModifiedTime(child)
        When("another JVM's public advance observes the saved intent and actual document postimage")
        val recovered = _separate_process_observation(root, "APPLY", advance, allowProgress = true, recordEffects = true)
        Then("only an unchanged actual applied postimage permits completion, with no second document apply")
        recovered.hcursor.get[String]("before").toOption shouldBe Some(take(WorkflowInstanceRecordJsonV1.encodeC(pending.get)))
        recovered.hcursor.downField("result").get[Boolean]("success").toOption shouldBe Some(!changed)
        val catalog = take(WorkflowInstanceRecordJsonV1.catalogC(Vector(F.catalog.definition)))
        val current = take(WorkflowInstanceRecordJsonV1.decodeC(recovered.hcursor.get[String]("after").toOption.get, catalog))
        current.history.take(pending.get.history.size) shouldBe pending.get.history
        if (changed) {
          recovered.hcursor.downField("result").get[String]("error").toOption.get should include("actual documents conflict with the saved selection")
          current shouldBe pending.get
        } else {
          val terminal = take(WorkflowTerminalJsonV1.decodeC(recovered.hcursor.downField("result").get[String]("wire").toOption.get,
            SplitPhaseManagedSelectedApply.payloadCodec))
          val payload = terminal.current.asInstanceOf[WorkflowContinuation.Terminal[Json]].result.value
          payload.hcursor.downField("effect").get[String]("status").toOption shouldBe Some("applied")
          current.lifecycle shouldBe Lifecycle.Completed
          terminal.handle shouldBe take(WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, current))
        }
        Files.readString(countPath) shouldBe count
        Files.readString(phase) shouldBe texts._1
        Files.readString(child) shouldBe texts._2
        Files.getLastModifiedTime(phase) shouldBe times._1
        Files.getLastModifiedTime(child) shouldBe times._2
        When("a further JVM reopens the exact terminal or retained conflict without reinstalling inputs")
        val reopened = _separate_process_observation(root, "APPLY", advance, recordEffects = true)
        Then("the response/history/files remain exact and the one actual Apply execution is retained")
        reopened.hcursor.downField("result").focus shouldBe recovered.hcursor.downField("result").focus
        reopened.hcursor.get[String]("before").toOption shouldBe recovered.hcursor.get[String]("after").toOption
        Files.readString(countPath) shouldBe count
        Files.readString(phase) shouldBe texts._1
        Files.readString(child) shouldBe texts._2
        Files.getLastModifiedTime(phase) shouldBe times._1
        Files.getLastModifiedTime(child) shouldBe times._2
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
        def observe(wire: String, expected: InstanceRecord): Unit = {
          val actual = _separate_process_observation(root, "PREVIEW", advance)
          actual.hcursor.downField("result").get[Boolean]("success").toOption shouldBe Some(true)
          actual.hcursor.downField("result").get[String]("wire").toOption shouldBe Some(wire)
          actual.hcursor.get[String]("before").toOption shouldBe Some(take(WorkflowInstanceRecordJsonV1.encodeC(expected)))
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
        observe(pending.get, completed.get)
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
          Then("one adjacent completion is saved and a duplicate does not append")
          after.history.dropRight(1) shouldBe before.history
          invoke("submitHandoffScopeResult", reply) shouldBe acknowledgement
          invoke("submitHandoffScopeResult", legacyreply) shouldBe acknowledgement
          current() shouldBe after
          val changedScope = scopereply.get.copy(result = scopereply.get.result.copy(value =
            scopereply.get.result.value.copy(rationale = "Changed already committed interpretation")))
          invokeC("submitHandoffScopeResult", take(WorkflowResultJsonV1.encodeC(changedScope, J.resultCodec))).isSuccess shouldBe false
          current() shouldBe after
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
          observe(pending.get, completed.get)
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
            scalar(take(invokeC("submitSplitHumanDirection", reply))) shouldBe acknowledgement
            current() shouldBe after
            val changed = humanreply.get.copy(result = humanreply.get.result.copy(value =
              if (direction == "cancel") H.Answer.Defer else H.Answer.Cancel))
            invokeC("submitSplitHumanDirection", take(WorkflowResultJsonV1.encodeC(changed, H.payloadCodec))).isSuccess shouldBe false
            current() shouldBe after
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
            observe(wire, current())
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
            Then("one canonical reasoning completion is retained unchanged by an identical answer")
            after.history.dropRight(1) shouldBe before.history
            invoke("submitReasoningResult", reply) shouldBe acknowledgement
            current() shouldBe after
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
              observe(questionwire, current())
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
                if (direction == "reasoning-resolved") {
                  Given("a structurally valid saved frontier with different enumeration diagnostics but the same actual planning target")
                  val snapshot = saved.downstream.get
                  val partition = snapshot.partition.asInstanceOf[SplitPhasePlanningSnapshot.PartitionView.Enumerated]
                  partition.evaluatedCount should be < snapshot.selection.partition.policy.maximumCombinations
                  val diagnostics = snapshot.copy(partition = partition.copy(
                    evaluatedCount = partition.evaluatedCount + 1, rejectedByCeiling = partition.rejectedByCeiling + 1))
                  val selected = SplitPhaseManagedReasoningHumanCompletion.selectRetainedSuccessorC
                  take(SplitPhasePlanningSnapshotJsonV1.encodeC(diagnostics))
                  When("the retained selector consumes saved diagnostics rather than comparing a recalculated whole snapshot")
                  val actual = take(selected(saved.plan, saved.completion, saved.resolution, Some(diagnostics)))
                  Then("its selected native successor is independent of diagnostic-body equality")
                  actual shouldBe humanafter
                  Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R6; Example: E19; actual conflicting successor assignments")
                  val foreign = snapshot.copy(selection = snapshot.selection.copy(planning = snapshot.selection.planning.copy(
                    source = snapshot.selection.planning.source.copy(identity = "foreign-planning-source"))))
                  When("the saved data names a foreign planning source reasoning result missing successor or native revision")
                  val refused = Vector(
                    selected(saved.plan, saved.completion, saved.resolution, Some(foreign)),
                    selected(saved.plan, saved.completion, saved.resolution.map(_.copy(reasoningResult = ContextReference("foreign-reasoning-result", "1"))), saved.downstream),
                    selected(saved.plan, saved.completion, saved.resolution, None),
                    selected(saved.plan, saved.completion.copy(instanceRevision = humanbefore.revision), saved.resolution, saved.downstream))
                  Then("real target and native-state conflicts fail without changing stored answers or executing planning again")
                  refused.foreach(_.isSuccess shouldBe false)
                  saved.current shouldBe humanafter
                }
                ()
              }
              When("the exact completed Human answer is repeated through the reduced public submission")
              invoke("submitSplitHumanDirection", admittedwire) shouldBe humanack
              Then("the original acknowledgement is observed without another append")
              current() shouldBe humanafter
              Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R2; Example: E13; retained reasoning Human direction with different transport diagnostics")
              val diagnostic = admittedanswer.copy(contextSnapshot = admittedanswer.contextSnapshot.copy(
                workspaceRevision = Some("new-workspace-diagnostic"), evidenceRevision = Some("new-evidence-diagnostic")),
                evidence = admittedanswer.evidence.copy(workerIdentity = Some("different-diagnostic-relay")))
              When("the same actual answer target and direction are repeated without new Human evidence")
              val originalAnswer = invoke("submitSplitHumanDirection", take(WorkflowResultJsonV1.encodeC(diagnostic, H.payloadCodec)))
              Then("the original committed answer is returned without another native append or successor calculation publication")
              originalAnswer shouldBe humanack
              current() shouldBe humanafter
              val changed = admittedanswer.copy(result = admittedanswer.result.copy(value =
                if (answer == H.Answer.Cancel) H.Answer.Defer else H.Answer.Cancel))
              When("a changed Human answer attempts to replace the completed result")
              invokeC("submitSplitHumanDirection", take(WorkflowResultJsonV1.encodeC(changed, H.payloadCodec))).isSuccess shouldBe false
              Then("the completed native state and canonical answer remain unchanged")
              current() shouldBe humanafter
              When("the retained answer target is offered a foreign resolution reference model or answer slot")
              val foreignAnswers = Vector(
                admittedanswer.copy(result = admittedanswer.result.copy(value = H.Answer.ProvideResolution(
                  SplitPhaseExplicitPartitionSemantic.ContextReference("foreign-resolution", "1")))),
                admittedanswer.copy(resultReference = ContextReference("foreign-human-answer", "1")),
                admittedanswer.copy(contextSnapshot = admittedanswer.contextSnapshot.copy(modelRevision = Some("foreign-model"))))
              foreignAnswers.foreach { foreign =>
                val refused = invokeC("submitSplitHumanDirection", take(WorkflowResultJsonV1.encodeC(foreign, H.payloadCodec)))
                Then("actual incompatible scope or semantic direction is rejected without modifying the committed successor")
                refused.isSuccess shouldBe false
                current() shouldBe humanafter
              }
              When("the original AI DecisionNeeded is submitted again after Human completion")
              invoke("submitReasoningResult", reply) shouldBe acknowledgement
              Then("the original AI acknowledgement remains canonical without another native append")
              current() shouldBe humanafter
              When("the actual public advance observes and consumes the retained Human successor with its own Core")
              val advancedwire = invoke("advanceSplitPhase", advance)
              observesuccessor = _ => Consequence.unit
              Then("exactly one observation precedes the genuine owning advance")
              retainedobservations shouldBe 1
              When("a separate JVM reopens the advanced successor, without resubmitting AI or Human answers")
              val reopened = _separate_process_observation(root, "PREVIEW", advance)
              Then("the owning reopened Operation observes the saved terminal without changing its native state")
              reopened.hcursor.downField("result").get[Boolean]("success").toOption shouldBe Some(true)
              val savedwire = reopened.hcursor.downField("result").get[String]("wire").toOption.get
              savedwire shouldBe advancedwire
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
              invoke("advanceSplitPhase", advance) shouldBe savedwire
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
          invoke("advanceSplitPhase", advance) shouldBe terminal.get
          current() shouldBe completed.get
          When("the original scope result is observed after terminal progression in the fresh runtime")
          invoke("submitHandoffScopeResult", take(WorkflowResultJsonV1.encodeC(scopereply.get, J.resultCodec))) shouldBe scopeack.get
          Then("its historical acknowledgement does not regress or append completed state")
          current() shouldBe completed.get
        }
        When("a further JVM reopens the complete Preview or stopped terminal from saved state")
        observe(terminal.get, completed.get)
        Then("the exact response and complete history survive without renewing allocation, judgment or Human evidence")
      }
    }
    for (episode <- Vector("normal", "occupied child")) {
      s"apply SplitPhase selected documents and reopen actual effects through direct Operations for $episode" in {
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
        for (session <- Vector("apply", "fresh runtime")) withRuntime(root, Some(_document_process_plan(root))) {
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
        When("a separate JVM reopens the saved Apply outcome through the public Operation without re-preparing inputs")
        val reopened = _separate_process_observation(root, "APPLY", advance)
        Then("the actual saved state and document effects are retained across the process boundary")
        reopened.hcursor.get[String]("after").toOption.get shouldBe take(WorkflowInstanceRecordJsonV1.encodeC(native.get))
        reopened.hcursor.downField("result").get[Boolean]("success").toOption shouldBe Some(episode == "normal")
        if (episode == "normal") {
          reopened.hcursor.downField("result").get[String]("wire").toOption shouldBe outcome
          Files.getLastModifiedTime(phase) shouldBe times.get._1
          Files.getLastModifiedTime(child) shouldBe times.get._2
        } else {
          reopened.hcursor.downField("result").get[String]("error").toOption.get should include("actual documents conflict with the saved selection")
          Files.readString(phase) shouldBe original
          Files.readString(child) shouldBe "Independent existing child\n"
        }
      }
    }
    "start SplitPhase issue work and retain the same pending work after a fresh SQLite runtime without commands" in {
      Given("one declared SQLite store, production ports and explicitly specification-owned selected input")
      val root = Files.createTempDirectory("smwf-direct-operations-")
      var start = Option.empty[String]
      var pending = Option.empty[String]
      val advance = take(SplitPhaseAdvanceCommandJsonV1.encodeC(SplitPhaseAdvanceCommand(F.record.instanceIdentity.value,
        SplitPhasePartitionContextReference(F.context.reference.identity, F.context.reference.revision))))
      val advanceprefix = SplitPhaseAdvanceCommandJsonV1.schema + ":"
      val advancejson = io.circe.parser.parse(advance.substring(advanceprefix.length))
        .fold(error => fail(error.message), identity)
      val decoratedcontext = advancejson.hcursor.downField("payload").downField("context").focus.get
        .mapObject(_.add("obsoleteProof", Json.False))
      val decoratedpayload = advancejson.hcursor.downField("payload").focus.get
        .mapObject(_.add("stage", Json.fromString("APPLY"))
          .add("argv", Json.arr(Json.fromString("must-not-execute")))
          .add("context", decoratedcontext))
      val decoratedjson = advancejson.mapObject(_.add("diagnostics", Json.obj(
        "expectedRevision" -> Json.fromInt(999), "contextSnapshot" -> Json.Null,
        "unusedTrace" -> Json.fromString("診断" * 4000))).add("payload", decoratedpayload))
      val decoratedadvance = advanceprefix + decoratedjson.spaces2
      val foreigntarget = advanceprefix + decoratedjson.hcursor.downField("payload").downField("context")
        .downField("identity").withFocus(_ => Json.fromString("unrelated-context")).top.get.noSpaces
      val foreignlongtarget = take(SplitPhaseAdvanceCommandJsonV1.encodeC(SplitPhaseAdvanceCommand(
        F.record.instanceIdentity.value, SplitPhasePartitionContextReference("unrelated-" + ("実" * 600), "1"))))
      val sessionschema = Json.obj("schemaVersion" -> Json.fromString(NativeWorkflowSessionInput.schema),
        "sessionIdentity" -> Json.fromString("split-operation-session"))
      for (session <- Vector("first execution", "fresh runtime")) withRuntime(root) { (owner, component, actor, prepare) =>
        def _wire_(operation: String, wire: String): String =
          if (operation == "startSplitPhase") sessionschema.deepMerge(Json.obj("input" -> Json.fromString(wire))).noSpaces
          else if (operation == "advanceSplitPhase" && wire == advance) sessionschema.noSpaces
          else wire
        Given("Start and normal advance use the same explicit opaque session, without echoed instance or partition context")
        def invoke(operation: String, wire: String) = scalar(take(owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "SplitPhaseService", operation = operation,
            arguments = List(Argument("arg1", _wire_(operation, wire)))), actor)))
        if (session == "first execution") prepare(prepareInputs)
        When("the actual public Start Operation is called directly for " + session)
        val accepted = invoke("startSplitPhase", startWire)
        Then("Start creates its own instance once and reopens the retained original receipt")
        take(WorkflowWaitJsonV1.decodeC(accepted)).handle.instanceIdentity shouldBe F.record.instanceIdentity
        start.foreach(_ shouldBe accepted)
        start = Some(accepted)
        if (session == "first execution") prepare { call =>
          call.currentC().flatMap { current =>
            current.history.size shouldBe 1
            val context = F.context.copy(owner = F.context.owner.copy(
              start = typedStart.reference,
              handle = take(WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, current)), instanceRevision = current.revision))
            take(SplitPhaseManagedPartitionContextRegistration.bindC(owner.configuration, declaration, F.catalog))
              .registerForCallC(context, call.core).map(_ => ())
          }
        }
        if (session == "first execution") {
          Given("an unrelated partition row whose interpretation data is malformed for another instance")
          val unrelated = SplitPhaseExplicitPartitionContextJsonV1.schema + ":" + Json.obj(
            "schemaVersion" -> Json.fromString(SplitPhaseExplicitPartitionContextJsonV1.schema),
            "payload" -> Json.obj("owner" -> Json.obj("handle" -> Json.obj(
              "instanceIdentity" -> Json.obj("value" -> Json.fromString("foreign-instance")))),
              "facts" -> Json.fromInt(99))).noSpaces
          prepare(call => call.createC(collection("split_contexts"), "foreign-context",
            Record.data(SplitPhaseExplicitPartitionContextJsonV1.storageField -> unrelated)))
        }
        When("the actual public advance Operation is called directly for " + session)
        val issued = invoke("advanceSplitPhase", advance)
        Then("the semantic WorkOrder is durable and the caller does not choose a stage or issue a replacement")
        val work = take(WorkflowWorkOrderJsonV1.decodeC(issued, SplitPhaseExplicitPartitionSemanticJson.requestCodec))
        work.handle.instanceIdentity shouldBe F.record.instanceIdentity
        work.current shouldBe a[WorkflowContinuation.WorkOrder[?]]
        pending.foreach(_ shouldBe issued)
        pending = Some(issued)
        var before = Option.empty[InstanceRecord]
        prepare(call => call.currentC().map(value => { before = Some(value); () }))
        When("unused diagnostics and caller stage or argv fields reach the genuine public advance Operation")
        val observed = invoke("advanceSplitPhase", decoratedadvance)
        Then("Workflow returns its actual pending work without accepting caller controls or appending history")
        observed shouldBe issued
        prepare(call => call.currentC().map(value => {
          SplitPhaseDefinitionBinding.sameRecord(value, before.get) shouldBe true
          ()
        }))
        When("the actual context target is changed while those same diagnostics are retained")
        val rejected = owner.executeOperationResponseInContext(Request.of(component = component.name,
          service = "SplitPhaseService", operation = "advanceSplitPhase",
          arguments = List(Argument("arg1", foreigntarget))), actor)
        Then("the owning context lookup rejects that target without changing the original pending instance")
        rejected.isSuccess shouldBe false
        prepare(call => call.currentC().map(value => {
          SplitPhaseDefinitionBinding.sameRecord(value, before.get) shouldBe true
          ()
        }))
        When("a nonexistent context with a future excessive planning key reaches the same public Operation")
        val missing = owner.executeOperationResponseInContext(Request.of(component = component.name,
          service = "SplitPhaseService", operation = "advanceSplitPhase",
          arguments = List(Argument("arg1", foreignlongtarget))), actor)
        Then("the actual context consumer rejects absence rather than a codec physical-key precheck and preserves pending state")
        missing.isSuccess shouldBe false
        missing.toString should include("current-state original context is absent")
        prepare(call => call.currentC().map(value => {
          SplitPhaseDefinitionBinding.sameRecord(value, before.get) shouldBe true
          ()
        }))
        invoke("advanceSplitPhase", advance) shouldBe issued
      }
      When("a separate JVM advances the same saved pending instance without input or context re-registration")
      val reopened = _separate_process_observation(root, "PENDING", decoratedadvance)
      Then("the original WorkOrder returns without a history append in that process")
      reopened.hcursor.downField("result").get[Boolean]("success").toOption shouldBe Some(true)
      reopened.hcursor.downField("result").get[String]("wire").toOption shouldBe pending
    }
    "E12 session result consumption: complete SplitPhase semantic work to Preview and reopen its final outcome through direct Operations" in {
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
      for (session <- Vector("issue", "reply after reopen", "terminal after reopen")) withRuntime(root) {
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
          When("the same result is observed again before further progression")
          invoke("submitPartitionSemanticResult", resultWire) shouldBe ack
          Then("no duplicate completion history is added")
          SplitPhaseDefinitionBinding.sameRecord(current(), completed) shouldBe true
          When("an obsolete malformed destination echo and harmless diagnostics accompany the same completed answer")
          val obsoletepayload = submittedjson.hcursor.downField("result").downField("value").downField("payload")
            .withFocus(_.mapObject(_.add("context", Json.fromString("obsolete-context"))
              .add("contextDigest", Json.Null).add("nextOperation", Json.fromString("apply")))).top.get
          val obsolete = obsoletepayload.mapObject(_.add("result", obsoletepayload.hcursor.downField("result").focus.get
            .mapObject(_.add("reference", Json.fromString("obsolete unused destination copy"))))
            .add("diagnostics", Json.obj("oldSourceLine" -> Json.fromInt(-1)))).noSpaces
          invoke("submitPartitionSemanticResult", obsolete) shouldBe ack
          Then("Workflow resolves its actual assigned destination without a new append or an unused-input refusal")
          SplitPhaseDefinitionBinding.sameRecord(current(), completed) shouldBe true
          When("a different semantic judgment is returned for the already completed assignment")
          val changed = result.copy(result = result.result.copy(value = judgment.copy(rationale = "different reply")))
          invokeC("submitPartitionSemanticResult", take(WorkflowResultJsonV1.encodeC(changed, J.resultCodec))).isSuccess shouldBe false
          Then("the committed result is not replaced and native history is unchanged")
          SplitPhaseDefinitionBinding.sameRecord(current(), completed) shouldBe true
          completedReply = Some(resultWire -> ack)
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
          When("the already completed semantic reply is observed with those unused current input changes")
          val retainedack = invoke("submitPartitionSemanticResult", resultWire)
          Then("the genuine native acknowledgement remains available with no additional history")
          retainedack shouldBe ack
          SplitPhaseDefinitionBinding.sameRecord(current(), completed) shouldBe true
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
          When("the exact semantic result is returned again after terminal progression and runtime reopening")
          invoke("submitPartitionSemanticResult", completedReply.get._1) shouldBe completedReply.get._2
          Then("observation returns the original acknowledgement without regressing or appending terminal history")
          SplitPhaseDefinitionBinding.sameRecord(current(), finalState.get) shouldBe true
        }
      }
      When("a separate JVM observes the terminal and exact completed semantic reply through public Operations")
      val reopened = _separate_process_observation(root, "PREVIEW", advance, Some(completedReply.get._1))
      Then("both retained responses and the original completed native history survive the process boundary")
      reopened.hcursor.downField("result").get[Boolean]("success").toOption shouldBe Some(true)
      reopened.hcursor.downField("result").get[String]("wire").toOption shouldBe terminal
      reopened.hcursor.downField("acknowledgement").get[Boolean]("success").toOption shouldBe Some(true)
      reopened.hcursor.downField("acknowledgement").get[String]("wire").toOption shouldBe Some(completedReply.get._2)
      reopened.hcursor.get[String]("after").toOption.get shouldBe take(WorkflowInstanceRecordJsonV1.encodeC(finalState.get))
    }
    "E8 E9 E10 retain Split scope reasoning and both Human generations while genuine newer work is pending" in {
      import SplitPhaseExplicitPartitionFixture.{base, enumerate, proof}
      import SplitPhaseExplicitObjectiveSelection as O
      import SplitPhaseScopeSemantic as S
      import SplitPhaseScopeSemanticJson as J
      import SplitPhaseExplicitReasoningSemantic as R
      import SplitPhaseExplicitReasoningSemanticJson as RJ
      import SplitPhaseHumanDirectionAnswerContract as H
      import SplitPhaseReasoningHumanDirection as RH
      import SplitPhaseHumanReasoningResolution as HR
      Given("Spec: docs/spec/session-workflow-context-resolution.md; Rule: R4; Examples: E8 E9 E10; real scope and reasoning gaps require two different Human questions in each genuine Split run")
      val root = Files.createTempDirectory("smwf-split-delayed-scope-reasoning-human-")
      val source = base(Vector(260, 180, 260))
      val boundary = proof(source, enumerate(source).candidates.find(_.cutAfterIndices == Vector(0)).get,
        "delayed-scope-proposal", handoffPairs = Vector(0 -> 1))
      val objectives = O.Records(
        Some(O.CostPolicy(O.CostPolicyReference("delayed-scope-cost", "1"), source.source.reference, source.source.authority,
          O.ProfileIdentity("expensive"), O.ProfileIdentity("lower"), 2, 1, 1)),
        Some(O.DemandRecord(O.DemandReference("delayed-scope-demand", "1"), source.source.reference, source.source.authority,
          source.inventory.orderedUnfinishedItems.zipWithIndex.map { (id, index) =>
            O.ItemDemand(id, if (index == 0) O.Demand.Unresolved("scope-derived classification remains unknown") else O.Demand.SettledExecution)
          })),
        Some(O.OverheadPolicy(O.OverheadPolicyReference("delayed-scope-overhead", "1"), source.source.reference,
          source.source.authority, 1, 1, 0, 0, 0)))
      val facts = SplitPhaseExplicitPartitionContext.Facts(boundary.shape.records, boundary.records, source.records,
        F.facts.planning.copy(objectives = objectives))
      val firstcommand = F.command.copy(input = F.command.input.copy(proposalReference = Some(boundary.shape.records.proposal.reference.identity)))
      val firststart = typedStart.copy(command = firstcommand, selection = F.selectionFor(firstcommand),
        source = source.source, proposal = Some(boundary.shape.records.proposal))
      val secondcommand = firstcommand.copy(selectionIdentity = "delayed-scope-second-selection", idempotencyKey = "delayed-scope-second-start")
      val secondstart = firststart.copy(reference = firststart.reference.copy(identity = secondcommand.selectionIdentity),
        instanceIdentity = InstanceIdentity("delayed-scope-second-instance"), command = secondcommand, selection = F.selectionFor(secondcommand))
      val starts = Vector(firststart, secondstart)
      val sessioninput = Json.obj("schemaVersion" -> Json.fromString(NativeWorkflowSessionInput.schema),
        "sessionIdentity" -> Json.fromString("split-delayed-scope-reasoning-human-session"))
      val firstadvance = take(SplitPhaseAdvanceCommandJsonV1.encodeC(SplitPhaseAdvanceCommand(firststart.instanceIdentity.value,
        SplitPhasePartitionContextReference(F.context.reference.identity, F.context.reference.revision))))
      var answers = Vector.empty[(String, String, String, String, InstanceIdentity)]
      var terminals = Vector.empty[String]
      var finalstates = Vector.empty[InstanceRecord]
      var delayedcounts = Map.empty[String, Int]
      def _reply_wire_(wire: String, selected: Boolean): String = {
        val json = io.circe.parser.parse(wire).fold(error => fail(error.message), identity)
        val fields = if (selected) json else json.mapObject(_.remove("requestIdentity"))
        fields.mapObject(_.add("sessionIdentity", Json.fromString("split-delayed-scope-reasoning-human-session"))).noSpaces
      }
      def _result_[A, B](work: WorkflowInteraction[A, Nothing], value: B, typeidentity: String,
        reference: ContextReference, evidence: ContextReference): ContinuationResult[B] = {
        val order = work.current match {
          case value: WorkflowContinuation.WorkOrder[A] => value.request
          case value: WorkflowContinuation.Decision[A] => value.request
          case other => fail("Expected actual issued work or question: " + other)
        }
        ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision, order.context.snapshot,
          TypedValue(typeidentity, value), reference, Vector.empty, ExecutionEvidence(Vector(evidence), Some(firstcommand.participantIdentity)))
      }
      def _unchanged_(actual: Vector[InstanceRecord], expected: Vector[InstanceRecord]): Unit = {
        actual.size shouldBe expected.size
        actual.zip(expected).foreach { (observed, original) =>
          SplitPhaseDefinitionBinding.sameRecord(observed, original) shouldBe true
          observed.history shouldBe original.history
        }
      }
      def _acknowledgement_(operation: String, wire: String, original: String, issuer: InstanceRecord): Unit = {
        import org.goldenport.cncf.workflow.WorkflowCompletionRecordJsonV1
        if (operation != "submitSplitHumanDirection") wire shouldBe original
        else {
          val observed = io.circe.parser.parse(wire).fold(error => fail(error.message), identity).hcursor
          val previous = io.circe.parser.parse(original).fold(error => fail(error.message), identity).hcursor
          val completion = take(WorkflowCompletionRecordJsonV1.decodeC(observed.get[String]("completion").toOption.get, H.payloadCodec))
          val retained = take(WorkflowCompletionRecordJsonV1.decodeC(previous.get[String]("completion").toOption.get, H.payloadCodec))
          Then("the old Human response retains its answered target and meaning while projecting the issuer's actual current revision")
          observed.get[String]("schemaVersion").toOption shouldBe Some(SplitPhaseManagedHumanCompletion.responseSchema)
          observed.get[Long]("currentRevision").toOption shouldBe Some(issuer.revision.value)
          observed.get[Long]("completingRevision").toOption shouldBe Some(completion.instanceRevision.value)
          completion.instanceRevision shouldBe retained.instanceRevision
          completion.result.continuationId shouldBe retained.result.continuationId
          completion.result.handle shouldBe retained.result.handle
          completion.result.result shouldBe retained.result.result
          completion.result.resultReference shouldBe retained.result.resultReference
          completion.closingResult shouldBe retained.closingResult
          issuer.history.exists(_.revision == completion.instanceRevision) shouldBe true
          val questionwire = observed.get[String]("issuedDecision").toOption.get
          val question: WorkflowInteraction[?, Nothing] =
            if (completion.result.continuationId.value.startsWith("split-reasoning-human:"))
              take(WorkflowDecisionJsonV1.decodeC(questionwire, RH.inputCodec))
            else take(WorkflowDecisionJsonV1.decodeC(questionwire, SplitPhaseNativeHumanDirection.inputCodec))
          question.handle.instanceIdentity shouldBe issuer.identity
          question.current match {
            case decision: WorkflowContinuation.Decision[?] => decision.request.continuationId shouldBe completion.result.continuationId
            case other => fail("Expected actual retained Human Decision: " + other)
          }
        }
      }
      for ((start, generation) <- starts.zipWithIndex) {
        var scopework = Option.empty[WorkflowInteraction[S.Request, Nothing]]
        var scopequestion = Option.empty[WorkflowInteraction[SplitPhaseNativeHumanDirection.Input, Nothing]]
        var reasoningwork = Option.empty[WorkflowInteraction[R.Request, Nothing]]
        var reasoningresult = Option.empty[ContinuationResult[R.Judgment]]
        var reasoningquestion = Option.empty[WorkflowInteraction[RH.Input, Nothing]]
        for (episode <- Vector("issue scope", "complete scope", "complete scope Human", "complete reasoning", "complete reasoning Human", "reopen Preview"))
          withRuntime(root) { (owner, component, actor, prepare) =>
            val advance = if (generation == 0) sessioninput.noSpaces
              else sessioninput.mapObject(_.add("instanceIdentity", Json.fromString(start.instanceIdentity.value))).noSpaces
            def _invoke_c_(operation: String, wire: String) = owner.executeOperationResponseInContext(
              Request.of(component = component.name, service = "SplitPhaseService", operation = operation,
                arguments = List(Argument("arg1", wire))), actor)
            def _invoke_(operation: String, wire: String): String = scalar(take(_invoke_c_(operation, wire)))
            def _states_(): Vector[InstanceRecord] = {
              var states = Vector.empty[InstanceRecord]
              starts.take(generation + 1).foreach(selected => prepare(call => call.currentC(selected.instanceIdentity)
                .map(value => { states = states :+ value; () })))
              states
            }
            def _current_(): InstanceRecord = _states_().last
            def _old_answers_(operation: String, currentrequest: String, pendingwire: String): Unit =
              answers.filter(_._1 == operation).foreach { (_, oldwire, ack, requestid, instanceid) =>
                Given("a real successfully answered historical request and a genuinely different current pending request of the same completion")
                val before = _states_()
                val issuer = before.find(_.identity == instanceid).get
                issuer.history.exists(_.suspension.exists(_.continuationIdentity.value == requestid)) shouldBe true
                before.last.suspension.get.continuationIdentity.value shouldBe currentrequest
                requestid should not be currentrequest
                When("the old answer omits its necessary selector instead of choosing the newest or sole pending request")
                val unselected = _invoke_c_(operation, _reply_wire_(oldwire, selected = false))
                Then("actual issued-history ambiguity preserves every associated native record and history")
                unselected.isSuccess shouldBe false
                unselected.toString should include("ambiguous issued request")
                _unchanged_(_states_(), before)
                When("the same old answer explicitly selects its real historical request")
                val selected = _invoke_c_(operation, oldwire)
                Then("only its retained acknowledgement or owning refusal is possible, never a change to newer work")
                if (selected.isSuccess) _acknowledgement_(operation, scalar(take(selected)), ack, issuer)
                _unchanged_(_states_(), before)
                When("both forms of that historical answer are submitted by separate JVMs using the same public completion")
                val children = Vector(false, true).map { selected =>
                  _separate_process_observation(root, "PREVIEW", firstadvance,
                    Some(_reply_wire_(oldwire, selected)), completionoperation = operation)
                }
                Then("the child boundaries preserve the first actual target and every physical native history")
                children.head.hcursor.downField("acknowledgement").get[Boolean]("success").toOption shouldBe Some(false)
                children.head.hcursor.downField("acknowledgement").get[String]("error").toOption.get should include("ambiguous issued request")
                children.foreach { observed =>
                  observed.hcursor.downField("result").get[Boolean]("success").toOption shouldBe Some(true)
                  observed.hcursor.downField("result").get[String]("wire").toOption shouldBe
                    Some(if (generation == 0) pendingwire else terminals.head)
                  observed.hcursor.get[String]("after").toOption.get shouldBe take(WorkflowInstanceRecordJsonV1.encodeC(before.head))
                }
                if (children(1).hcursor.downField("acknowledgement").get[Boolean]("success").toOption.contains(true))
                  _acknowledgement_(operation, children(1).hcursor.downField("acknowledgement").get[String]("wire").toOption.get, ack, issuer)
                _unchanged_(_states_(), before)
                _invoke_("advanceSplitPhase", advance) shouldBe pendingwire
                delayedcounts = delayedcounts.updated(operation, delayedcounts.getOrElse(operation, 0) + 1)
              }
            def _submit_[A](operation: String, result: ContinuationResult[A],
              codec: org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec[A]): String = {
              val pendingwire = _invoke_("advanceSplitPhase", advance)
              _old_answers_(operation, result.continuationId.value, pendingwire)
              val wire = _reply_wire_(take(NativeWorkflowResultSubmissionFixture.encodeC(result, codec,
                omitReference = operation != "submitSplitHumanDirection")), selected = true)
              val before = _current_()
              val uniquelyissued = !answers.exists(_._1 == operation)
              When("the genuine current answer supplies only semantic data and the selector needed by actual issued generations")
              val ack = _invoke_(operation, _reply_wire_(wire, selected = !uniquelyissued))
              val after = _current_()
              Then("exactly one adjacent native completion is committed without replacing an older response")
              after.history.dropRight(1) shouldBe before.history
              after.revision shouldBe before.revision.next
              answers = answers :+ (operation, wire, ack, result.continuationId.value, start.instanceIdentity)
              ack
            }
            if (episode == "issue scope") {
              Given(s"generation $generation has complete original inputs and an independent Start allocation, not an installed Workflow history")
              if (generation == 0) prepare(call => prepareInputsWithFacts(call, facts, start))
              else prepare { call =>
                val identity = ThreeWorkflowHostEvidencePorts.key(start.reference.identity, start.reference.revision)
                val binding = take(ThreeWorkflowHostEvidencePorts.splitStartBindingC(
                  SplitPhaseTypedStartHumanVerification.StartContext(call.core.component.get.core.instanceId, start)))
                val evidence = NativeHostEvidence.Evidence(NativeHostEvidence.schema, NativeHostEvidence.Purpose.SplitStart.identity,
                  identity, "delayed-scope-second-consent", "1", "normal-development-host", start.command.participantIdentity,
                  binding, call.executionContext.clock.instant().minusSeconds(60).toString)
                for {
                  _ <- call.createC(collection("split_starts"), start.reference.identity,
                    Record.data(SplitPhaseTypedStartJsonV1.storageField -> take(SplitPhaseTypedStartJsonV1.encodeAllocationC(start))))
                  _ <- call.createC(collection("split_selections"), start.selection.selectionId,
                    Record.data(ProfileSelectionJsonV1.storageField -> take(ProfileSelectionJsonV1.encodeStorageC(start.selection))))
                  _ <- call.createC(collection("host_evidence"), NativeHostEvidence.recordIdentity(NativeHostEvidence.Purpose.SplitStart, identity),
                    Record.data(NativeHostEvidence.storageField -> take(NativeHostEvidence.encodeStorageC(evidence))))
                } yield ()
              }
              When("public Start publishes the actual native instance and its session association")
              val started = take(WorkflowWaitJsonV1.decodeC(_invoke_("startSplitPhase", sessioninput.deepMerge(Json.obj(
                "input" -> Json.fromString(SplitPhaseStartServiceOperation.encodeCommand(start.command)))).noSpaces)))
              Then("the selected allocation receives its genuine native handle")
              started.handle.instanceIdentity shouldBe start.instanceIdentity
              prepare(call => call.currentC(start.instanceIdentity).flatMap { state =>
                val reference = if (generation == 0) F.context.reference else SplitPhaseExplicitPartitionSemantic.ContextReference("delayed-scope-second-context", "1")
                val continuation = if (generation == 0) F.context.owner.continuation
                  else org.goldenport.cncf.workflow.ContinuationIdentity("delayed-scope-second-continuation")
                val context = F.context.copy(reference = reference, facts = facts, owner = F.context.owner.copy(
                  start = start.reference, continuation = continuation,
                  handle = take(WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, state)), instanceRevision = state.revision))
                take(SplitPhaseManagedPartitionContextRegistration.bindC(owner.configuration, declaration, F.catalog))
                  .registerForCallC(context, call.core).map(_ => ())
              })
              When("advance derives the actual unresolved scope from the original whole frontier")
              scopework = Some(take(WorkflowWorkOrderJsonV1.decodeC(_invoke_("advanceSplitPhase", advance), J.requestCodec)))
              val input = scopework.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value
              Then("only the original unknown handoff is requested under this real instance")
              scopework.get.handle.instanceIdentity shouldBe start.instanceIdentity
              input.gaps.map(_.original.identity.value) shouldBe Vector("handoff-0")
            } else if (episode == "complete scope") {
              Given("an actual issued scope request whose interpretation needs Human direction")
              val input = scopework.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value
              val evidence = ContextReference(s"delayed-scope-evidence-$generation", "1")
              val judgment = S.Judgment(S.Decision.DecisionNeeded, "Original transfer scope needs Human interpretation", Vector.empty,
                Vector(SplitPhaseExplicitPartitionSemantic.EvidenceReference(evidence.identity, evidence.revision)))
              val result = _result_(scopework.get, judgment, J.resultCodec.typeIdentity,
                ContextReference(input.result.identity, input.result.revision), evidence)
              _submit_("submitHandoffScopeResult", result, J.resultCodec)
              When("owning advance presents the genuine scope Human question")
              scopequestion = Some(take(WorkflowDecisionJsonV1.decodeC(_invoke_("advanceSplitPhase", advance), SplitPhaseNativeHumanDirection.inputCodec)))
              Then("it belongs to this native run and retains the complete source frontier")
              scopequestion.get.handle.instanceIdentity shouldBe start.instanceIdentity
              val questioninput = scopequestion.get.current.asInstanceOf[WorkflowContinuation.Decision[SplitPhaseNativeHumanDirection.Input]].request.input.get.value
              take(SplitPhaseNativeHumanDirection.frontierC(questioninput)).first.originalContext.facts.proposal.source.reference shouldBe source.source.reference
            } else if (episode == "complete scope Human") {
              Given("a passive complete scope interpretation and specification-owned consent for this actual Human question")
              val decision = scopequestion.get.current.asInstanceOf[WorkflowContinuation.Decision[SplitPhaseNativeHumanDirection.Input]].request
              val input = decision.input.get.value
              val request = take(S.requestC(take(SplitPhaseNativeHumanDirection.frontierC(input)).first))
              val reference = SplitPhaseExplicitPartitionSemantic.ContextReference(s"delayed-scope-human-resolution-$generation", "1")
              val evidence = ContextReference(s"delayed-scope-human-answer-$generation", "1")
              val result = _result_[SplitPhaseNativeHumanDirection.Input, H.Answer](
                scopequestion.get, H.Answer.ProvideResolution(reference), H.resultType, evidence, evidence)
              val resolution = SplitPhaseHumanScopeResolution.Record(SplitPhaseHumanScopeResolution.Reference(reference.identity, reference.revision),
                request.context, request.checkpoint, input.scopeCheckpoint, SplitPhaseHumanScopeResolution.DecisionReference(decision.continuationId.value),
                request.gaps.map(gap => S.Scope(gap.original.identity, gap.producingItems.takeRight(1), gap.receivingItems.take(1), "Original transfer ranges")),
                "Resolve only assigned transfer scopes", Vector(SplitPhaseExplicitPartitionSemantic.EvidenceReference(evidence.identity, evidence.revision)))
              prepare(_.createC(collection("split_humanresolutions"), reference.identity,
                Record.data(SplitPhaseHumanScopeResolutionJsonV1.storageField -> take(SplitPhaseHumanScopeResolutionJsonV1.encodeStorageC(resolution)))))
              prepare(_.humanEvidenceC(scopequestion.get, result))
              _submit_("submitSplitHumanDirection", result, H.payloadCodec)
              When("owning advance consumes resolved scope and issues only unknown reasoning work")
              reasoningwork = Some(take(WorkflowWorkOrderJsonV1.decodeC(_invoke_("advanceSplitPhase", advance), RJ.requestCodec)))
              val reasoninginput = reasoningwork.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[R.Request]].request.input.get.value
              Then("known items and winning-partition selection are not delegated to the semantic responder")
              reasoninginput.items.map(_.identity) shouldBe Vector(source.inventory.orderedUnfinishedItems.head)
              reasoningwork.get.handle.instanceIdentity shouldBe start.instanceIdentity
            } else if (episode == "complete reasoning") {
              Given("the genuine issued reasoning request needs a separate Human classification")
              val input = reasoningwork.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[R.Request]].request.input.get.value
              val evidence = ContextReference(s"delayed-reasoning-evidence-$generation", "1")
              val judgment = R.Judgment(R.Decision.DecisionNeeded, "Unknown reasoning classification needs Human direction", Vector.empty,
                Vector(SplitPhaseExplicitPartitionSemantic.EvidenceReference(evidence.identity, evidence.revision)))
              val result = _result_(reasoningwork.get, judgment, RJ.resultCodec.typeIdentity,
                ContextReference(input.result.identity, input.result.revision), evidence)
              reasoningresult = Some(result)
              _submit_("submitReasoningResult", result, RJ.resultCodec)
              When("advance issues the distinct reasoning Human Required Operation")
              reasoningquestion = Some(take(WorkflowDecisionJsonV1.decodeC(_invoke_("advanceSplitPhase", advance), RH.inputCodec)))
              val question = reasoningquestion.get.current.asInstanceOf[WorkflowContinuation.Decision[RH.Input]].request
              Then("the question retains its actual reasoning purpose and native target rather than reusing scope Human work")
              question.requiredOperation.capability shouldBe "reasoning-human-direction-capability"
              reasoningquestion.get.handle.instanceIdentity shouldBe start.instanceIdentity
              question.continuationId should not be scopequestion.get.current.asInstanceOf[WorkflowContinuation.Decision[SplitPhaseNativeHumanDirection.Input]].request.continuationId
            } else if (episode == "complete reasoning Human") {
              Given("complete ordered classifications bound to this real reasoning Decision and saved AI result, with independent specification-owned consent")
              val decision = reasoningquestion.get.current.asInstanceOf[WorkflowContinuation.Decision[RH.Input]].request
              val input = reasoningwork.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[R.Request]].request.input.get.value
              val evidence = ContextReference(s"delayed-reasoning-human-interpretation-$generation", "1")
              val reference = SplitPhaseExplicitPartitionSemantic.ContextReference(s"delayed-reasoning-human-resolution-$generation", "1")
              val interpretation = R.Judgment(R.Decision.Enriched, "Human classifies only the original unknown work",
                input.items.map(item => R.Assignment(item.identity, R.Classification.SettledExecution, "Bounded original implementation")),
                Vector(SplitPhaseExplicitPartitionSemantic.EvidenceReference(evidence.identity, evidence.revision)))
              val resolution = HR.Record(reference, decision.continuationId.value, reasoningresult.get.resultReference, interpretation)
              val result = _result_[RH.Input, H.Answer](reasoningquestion.get, H.Answer.ProvideResolution(reference), H.resultType,
                ContextReference(s"delayed-reasoning-human-answer-$generation", "1"), evidence)
              prepare(_.createC(collection("split_humanresolutions"), reference.identity, Record.data(HR.storageField -> take(HR.encodeC(resolution)))))
              prepare(_.humanEvidenceC(reasoningquestion.get, result))
              _submit_("submitSplitHumanDirection", result, H.payloadCodec)
              When("owning advance absorbs the resolved classification and publishes its complete selected Preview")
              val wire = _invoke_("advanceSplitPhase", advance)
              val terminal = take(WorkflowTerminalJsonV1.decodeC(wire, SplitPhaseManagedSelectedPreview.payloadCodec))
              val payload = terminal.current.asInstanceOf[WorkflowContinuation.Terminal[Json]].result.value
              val state = _current_()
              Then("every original source item and the actual Completed native handle survive both Human resolutions")
              payload.hcursor.get[String]("mode").toOption shouldBe Some("PREVIEW")
              payload.hcursor.get[Vector[Json]]("children").toOption.get.flatMap(_.hcursor.get[Vector[Json]]("orderedWorkItems").toOption.get)
                .map(_.hcursor.get[String]("identity").toOption.get) shouldBe source.inventory.orderedUnfinishedItems.map(_.value)
              state.lifecycle shouldBe Lifecycle.Completed
              state.suspension shouldBe None
              terminal.handle shouldBe take(WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, state))
              if (generation == 1) _unchanged_(_states_().take(1), finalstates)
              terminals = terminals :+ wire
              finalstates = finalstates :+ state
            } else {
              When("another fresh runtime reopens the complete Preview without resubmitting any answer")
              val observed = _invoke_("advanceSplitPhase", advance)
              Then("the exact retained outcome and all completed native histories remain unchanged")
              observed shouldBe terminals(generation)
              _unchanged_(_states_(), finalstates)
            }
          }
      }
      When("a final fresh runtime observes both completed runs and explicitly addressed historical responses")
      withRuntime(root) { (owner, component, actor, prepare) =>
        def _invoke_c_(operation: String, wire: String) = owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "SplitPhaseService", operation = operation, arguments = List(Argument("arg1", wire))), actor)
        starts.zipWithIndex.foreach { (start, generation) =>
          val advance = sessioninput.mapObject(_.add("instanceIdentity", Json.fromString(start.instanceIdentity.value))).noSpaces
          Then("each original Preview returns without selecting the other native instance")
          scalar(take(_invoke_c_("advanceSplitPhase", advance))) shouldBe terminals(generation)
        }
        answers.foreach { (operation, wire, ack, _, instanceid) =>
          When("an explicitly selected old completed answer is observed after both full Previews")
          val observed = _invoke_c_(operation, wire)
          Then("the owning completion only acknowledges or refuses; both actual native histories remain completed")
          if (observed.isSuccess) _acknowledgement_(operation, scalar(take(observed)), ack, finalstates.find(_.identity == instanceid).get)
          var states = Vector.empty[InstanceRecord]
          starts.foreach(start => prepare(call => call.currentC(start.instanceIdentity).map(value => { states = states :+ value; () })))
          _unchanged_(states, finalstates)
        }
      }
      Then("eight actual delayed episodes cover scope reasoning and both Human roles within and across two whole runs")
      delayedcounts shouldBe Map("submitHandoffScopeResult" -> 1, "submitReasoningResult" -> 1, "submitSplitHumanDirection" -> 6)
      answers.size shouldBe 8
      answers.map(_._4).distinct.size shouldBe 8
      finalstates.size shouldBe 2
    }
    "E8 E9 E10 preserve both Split instances when an old partition answer arrives during a new issued generation" in {
      import SplitPhaseExplicitPartitionSemantic as S
      import SplitPhaseExplicitPartitionSemanticJson as J
      Given("Spec: docs/spec/session-workflow-context-resolution.md; Rule: R4; Examples: E8 E9 E10; two genuine public Starts share one session and managed store")
      val root = Files.createTempDirectory("smwf-split-delayed-partition-")
      val facts = _semantic_preview_facts()
      val secondcommand = F.command.copy(selectionIdentity = "split-delayed-second-selection", idempotencyKey = "split-delayed-second-start")
      val secondstart = typedStart.copy(reference = typedStart.reference.copy(identity = secondcommand.selectionIdentity),
        instanceIdentity = InstanceIdentity("split-delayed-second-instance"), command = secondcommand,
        selection = F.selectionFor(secondcommand))
      val starts = Vector(typedStart, secondstart)
      val sessioninput = Json.obj("schemaVersion" -> Json.fromString(NativeWorkflowSessionInput.schema),
        "sessionIdentity" -> Json.fromString("split-delayed-partition-session"))
      var replies = Vector.empty[(String, String)]
      var terminals = Vector.empty[String]
      var finalstates = Vector.empty[InstanceRecord]
      var issuedidentities = Vector.empty[String]
      var delayedepisodes = 0
      def _reply_wire_(wire: String, selected: Boolean): String = {
        val json = io.circe.parser.parse(wire).fold(error => fail(error.message), identity)
        val fields = if (selected) json else json.mapObject(_.remove("requestIdentity"))
        fields.mapObject(_.add("sessionIdentity", Json.fromString("split-delayed-partition-session"))).noSpaces
      }
      def _states_(prepare: (Setup => Consequence[Unit]) => Unit): Vector[InstanceRecord] = {
        var states = Vector.empty[InstanceRecord]
        starts.foreach(start => prepare(call => call.currentC(start.instanceIdentity).map(value => { states = states :+ value; () })))
        states
      }
      def _unchanged_(actual: Vector[InstanceRecord], expected: Vector[InstanceRecord]): Unit = {
        actual.size shouldBe expected.size
        actual.zip(expected).foreach { (observed, original) =>
          SplitPhaseDefinitionBinding.sameRecord(observed, original) shouldBe true
          observed.history shouldBe original.history
        }
      }
      for ((start, generation) <- starts.zipWithIndex) {
        var pending = Option.empty[String]
        for (episode <- Vector("issue", "complete after reopen")) withRuntime(root) { (owner, component, actor, prepare) =>
          def _invoke_c_(operation: String, wire: String) = owner.executeOperationResponseInContext(
            Request.of(component = component.name, service = "SplitPhaseService", operation = operation,
              arguments = List(Argument("arg1", wire))), actor)
          def _invoke_(operation: String, wire: String): String = scalar(take(_invoke_c_(operation, wire)))
          def _current_(): InstanceRecord = {
            var state = Option.empty[InstanceRecord]
            prepare(call => call.currentC(start.instanceIdentity).map(value => { state = Some(value); () }))
            state.get
          }
          val advance = if (generation == 0) sessioninput.noSpaces
            else sessioninput.mapObject(_.add("instanceIdentity", Json.fromString(start.instanceIdentity.value))).noSpaces
          if (episode == "issue") {
            Given(s"generation $generation selects real source inputs and a separate Start allocation, not an installed native record")
            if (generation == 0) prepare(call => prepareInputsWithFacts(call, facts))
            else prepare { call =>
              val identity = ThreeWorkflowHostEvidencePorts.key(start.reference.identity, start.reference.revision)
              val binding = take(ThreeWorkflowHostEvidencePorts.splitStartBindingC(
                SplitPhaseTypedStartHumanVerification.StartContext(call.core.component.get.core.instanceId, start)))
              val evidence = NativeHostEvidence.Evidence(NativeHostEvidence.schema, NativeHostEvidence.Purpose.SplitStart.identity,
                identity, "split-delayed-second-consent", "1", "normal-development-host", start.command.participantIdentity,
                binding, call.executionContext.clock.instant().minusSeconds(60).toString)
              for {
                _ <- call.createC(collection("split_starts"), start.reference.identity,
                  Record.data(SplitPhaseTypedStartJsonV1.storageField -> take(SplitPhaseTypedStartJsonV1.encodeAllocationC(start))))
                _ <- call.createC(collection("split_selections"), start.selection.selectionId,
                  Record.data(ProfileSelectionJsonV1.storageField -> take(ProfileSelectionJsonV1.encodeStorageC(start.selection))))
                _ <- call.createC(collection("host_evidence"), NativeHostEvidence.recordIdentity(NativeHostEvidence.Purpose.SplitStart, identity),
                  Record.data(NativeHostEvidence.storageField -> take(NativeHostEvidence.encodeStorageC(evidence))))
              } yield ()
            }
            When("public Start publishes the selected instance and its actual same-session association")
            val started = take(WorkflowWaitJsonV1.decodeC(_invoke_("startSplitPhase", sessioninput.deepMerge(Json.obj(
              "input" -> Json.fromString(SplitPhaseStartServiceOperation.encodeCommand(start.command)))).noSpaces)))
            Then("its native handle identifies this distinct allocation")
            started.handle.instanceIdentity shouldBe start.instanceIdentity
            prepare(call => call.currentC(start.instanceIdentity).flatMap { state =>
              val reference = if (generation == 0) F.context.reference else S.ContextReference("split-delayed-second-context", "1")
              val continuation = if (generation == 0) F.context.owner.continuation
                else org.goldenport.cncf.workflow.ContinuationIdentity("split-delayed-second-partition-request")
              val context = F.context.copy(reference = reference, facts = facts, owner = F.context.owner.copy(
                start = start.reference, continuation = continuation,
                handle = take(WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, state)), instanceRevision = state.revision))
              take(SplitPhaseManagedPartitionContextRegistration.bindC(owner.configuration, declaration, F.catalog))
                .registerForCallC(context, call.core).map(_ => ())
            })
            When("owning advance issues only the actual unresolved partition work")
            val wire = _invoke_("advanceSplitPhase", advance)
            val work = take(WorkflowWorkOrderJsonV1.decodeC(wire, J.requestCodec))
            val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request
            Then("the request belongs to this actual instance and has the original semantic gaps")
            work.handle.instanceIdentity shouldBe start.instanceIdentity
            order.input.get.value.items.map(_.identity.value) shouldBe Vector("item-1")
            order.input.get.value.boundaries.map(_.edge.afterItem.value) shouldBe Vector("item-0")
            issuedidentities = issuedidentities :+ order.continuationId.value
            pending = Some(wire)
          } else {
            When("a fresh runtime reopens this actual pending generation")
            val wire = _invoke_("advanceSplitPhase", advance)
            Then("it observes the same issued WorkOrder without a replacement")
            wire shouldBe pending.get
            val work = take(WorkflowWorkOrderJsonV1.decodeC(wire, J.requestCodec))
            val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request
            if (generation == 1) {
              Given("an old successful reduced reply and a genuinely different new pending partition request")
              val before = _states_(prepare)
              before.head.lifecycle shouldBe Lifecycle.Completed
              before.head.history.exists(_.suspension.exists(_.continuationIdentity.value == issuedidentities.head)) shouldBe true
              before(1).suspension.get.continuationIdentity.value shouldBe issuedidentities(1)
              issuedidentities.distinct.size shouldBe 2
              When("the delayed answer omits its now-necessary request selector")
              val ambiguous = _invoke_c_("submitPartitionSemanticResult", _reply_wire_(replies.head._1, selected = false))
              Then("all issued generations cause ambiguity instead of selecting the sole pending request")
              ambiguous.isSuccess shouldBe false
              ambiguous.toString should include("ambiguous issued request")
              _unchanged_(_states_(prepare), before)
              When("the old answer explicitly selects its actual completed request")
              val old = _invoke_c_("submitPartitionSemanticResult", replies.head._1)
              Then("retained acknowledgement or owning refusal changes neither native instance nor history")
              if (old.isSuccess) scalar(take(old)) shouldBe replies.head._2
              _unchanged_(_states_(prepare), before)
              val firstadvance = take(SplitPhaseAdvanceCommandJsonV1.encodeC(SplitPhaseAdvanceCommand(typedStart.instanceIdentity.value,
                SplitPhasePartitionContextReference(F.context.reference.identity, F.context.reference.revision))))
              When("separate JVMs send both forms of the old answer while the second request remains pending")
              val unselected = _separate_process_observation(root, "PREVIEW", firstadvance,
                Some(_reply_wire_(replies.head._1, selected = false)))
              val selected = _separate_process_observation(root, "PREVIEW", firstadvance, Some(replies.head._1))
              Then("both process boundaries preserve the old terminal and both physical native histories")
              unselected.hcursor.downField("acknowledgement").get[Boolean]("success").toOption shouldBe Some(false)
              unselected.hcursor.downField("acknowledgement").get[String]("error").toOption.get should include("ambiguous issued request")
              for (observed <- Vector(unselected, selected)) {
                observed.hcursor.downField("result").get[Boolean]("success").toOption shouldBe Some(true)
                observed.hcursor.downField("result").get[String]("wire").toOption shouldBe Some(terminals.head)
                observed.hcursor.get[String]("after").toOption.get shouldBe take(WorkflowInstanceRecordJsonV1.encodeC(before.head))
              }
              if (selected.hcursor.downField("acknowledgement").get[Boolean]("success").toOption.contains(true))
                selected.hcursor.downField("acknowledgement").get[String]("wire").toOption shouldBe Some(replies.head._2)
              _unchanged_(_states_(prepare), before)
              _invoke_("advanceSplitPhase", advance) shouldBe pending.get
              delayedepisodes += 1
            }
            val input = order.input.get.value
            val evidence = S.EvidenceReference(s"split-delayed-semantic-evidence-$generation", "1")
            val judgment = S.Judgment(S.Decision.Enriched, "bounded independent semantic closures",
              input.items.map(item => S.NovelEstimate(item.identity, 120, 180, "bounded work", "novel closure")),
              input.boundaries.map(boundary => S.BoundaryJudgment(boundary.edge.afterItem,
                SplitPhasePartitionEnumeration.EdgeDisposition.Either, "independent closure")), Vector(evidence))
            val result = ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision,
              order.context.snapshot, TypedValue(J.resultCodec.typeIdentity, judgment),
              ContextReference(input.reply.result.identity, input.reply.result.revision), Vector.empty,
              ExecutionEvidence(Vector(ContextReference(evidence.identity, evidence.revision)), Some(start.command.participantIdentity)))
            val resultwire = _reply_wire_(take(NativeWorkflowResultSubmissionFixture.encodeC(result, J.resultCodec, omitReference = true)), selected = true)
            val before = _current_()
            When("the current answer omits the selector only for the first uniquely issued generation")
            val ack = _invoke_("submitPartitionSemanticResult", _reply_wire_(resultwire, selected = generation != 0))
            val completed = _current_()
            Then("the owning completion commits exactly one adjacent history entry")
            completed.history.dropRight(1) shouldBe before.history
            completed.revision shouldBe before.revision.next
            replies = replies :+ (resultwire -> ack)
            When("advance absorbs deterministic enumeration and selection into a complete Preview")
            val terminalwire = _invoke_("advanceSplitPhase", advance)
            val terminal = take(WorkflowTerminalJsonV1.decodeC(terminalwire, SplitPhaseManagedSelectedPreview.payloadCodec))
            val payload = terminal.current.asInstanceOf[WorkflowContinuation.Terminal[Json]].result.value
            val state = _current_()
            Then("every source item and the actual completed native handle belong to this generation")
            payload.hcursor.get[String]("mode").toOption shouldBe Some("PREVIEW")
            payload.hcursor.get[Vector[Json]]("children").toOption.get
              .flatMap(_.hcursor.get[Vector[Json]]("orderedWorkItems").toOption.get)
              .map(_.hcursor.get[String]("identity").toOption.get) shouldBe facts.proposal.inventory.orderedUnfinishedItems.map(_.value)
            state.lifecycle shouldBe Lifecycle.Completed
            state.suspension shouldBe None
            terminal.handle shouldBe take(WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, state))
            if (generation == 1) _unchanged_(_states_(prepare).take(1), finalstates)
            terminals = terminals :+ terminalwire
            finalstates = finalstates :+ state
          }
        }
      }
      When("a final fresh runtime explicitly observes both completed generations and their retained replies")
      withRuntime(root) { (owner, component, actor, prepare) =>
        starts.zipWithIndex.foreach { (start, generation) =>
          val advance = sessioninput.mapObject(_.add("instanceIdentity", Json.fromString(start.instanceIdentity.value))).noSpaces
          def _invoke_(operation: String, wire: String): String = scalar(take(owner.executeOperationResponseInContext(
            Request.of(component = component.name, service = "SplitPhaseService", operation = operation,
              arguments = List(Argument("arg1", wire))), actor)))
          Then("each original Preview and acknowledgement returns without appending either history")
          _invoke_("advanceSplitPhase", advance) shouldBe terminals(generation)
          _invoke_("submitPartitionSemanticResult", replies(generation)._1) shouldBe replies(generation)._2
        }
        _unchanged_(_states_(prepare), finalstates)
      }
      Then("one real delayed episode and two full distinct Preview runs make the coverage nonvacuous")
      delayedepisodes shouldBe 1
      issuedidentities.distinct.size shouldBe 2
      finalstates.size shouldBe 2
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
        Given("a fresh runtime with the unused Context presentation still changed after actual completion")
        When("the public completion operation observes the saved answer")
        val retained = scalar(take(owner.executeOperationResponseInContext(
          _request_(component, "submitPartitionSemanticResult", reply), actor)))
        var retainedstate = Option.empty[InstanceRecord]
        var retainedtitle = Option.empty[String]
        prepare(call => {
          retainedstate = Some(take(call.currentC()))
          val contexts = take(SplitPhasePartitionContextReadPolicy.bindC(owner.configuration, declaration))
          retainedtitle = Some(take(contexts.loadForCallC(F.context.reference, call.core)).get.owner.presentation.title)
          Consequence.unit
        })
        Then("physical reopen preserves the same acknowledgement without a Context unchanged proof or transition")
        retained shouldBe acknowledgement.get
        retainedtitle shouldBe Some("Compatible updated display")
        retainedstate shouldBe completionstate
        Given("the later selected advance frontier whose separate Context-copy gate remains outside this increment")
        When("the fixture display is restored before that separate advance consumer")
        prepare(call => _display_(call, F.context.owner.presentation.title))
        val terminal = scalar(take(owner.executeOperationResponseInContext(_request_(component, "advanceSplitPhase", advance), actor)))
        val preview = take(WorkflowTerminalJsonV1.decodeC(terminal, SplitPhaseManagedSelectedPreview.payloadCodec))
        val payload = preview.current.asInstanceOf[WorkflowContinuation.Terminal[Json]].result.value
        var finalstate = Option.empty[InstanceRecord]
        prepare(call => call.currentC().map(value => { finalstate = Some(value); () }))
        Then("original acknowledgement, all-source Preview and actual completion history survive physical reopen")
        retained shouldBe acknowledgement.get
        payload.hcursor.get[Vector[Json]]("children").toOption.get
          .flatMap(_.hcursor.get[Vector[Json]]("orderedWorkItems").toOption.get)
          .map(_.hcursor.get[String]("identity").toOption.get) shouldBe facts.proposal.inventory.orderedUnfinishedItems.map(_.value)
        finalstate.get.history.dropRight(2) shouldBe completionstate.get.history
        finalstate.get.lifecycle shouldBe Lifecycle.Completed
      }
    }

    "E11 retain the actual competing public completion after a different answer was resolved but not committed" in {
      import SplitPhaseExplicitPartitionSemantic as S
      import SplitPhaseExplicitPartitionSemanticJson as J
      Given("Spec: docs/spec/session-workflow-context-resolution.md; Rule: R4; Example: E11; one real public Start and issued partition request in managed SQLite")
      val root = Files.createTempDirectory("smwf-split-completion-race-")
      val facts = _semantic_preview_facts()
      val session = "split-completion-race-session"
      val advance = Json.obj("schemaVersion" -> Json.fromString(NativeWorkflowSessionInput.schema),
        "sessionIdentity" -> Json.fromString(session)).noSpaces
      var pending = Option.empty[String]
      var pendingstate = Option.empty[InstanceRecord]
      var winnerstate = Option.empty[InstanceRecord]
      var winnerack = Option.empty[String]
      var observations = 0
      def _request_(component: org.goldenport.cncf.component.Component, operation: String, wire: String): Request =
        Request.of(component = component.name, service = "SplitPhaseService", operation = operation,
          arguments = List(Argument("arg1", wire)))
      withRuntime(root) { (owner, component, actor, prepare) =>
        val context = take(ExecutionContext.withRoutingSessionIdentity(actor, session))
        def _invoke_(operation: String, wire: String): String =
          scalar(take(owner.executeOperationResponseInContext(_request_(component, operation, wire), context)))
        prepare(call => prepareInputsWithFacts(call, facts))
        take(WorkflowWaitJsonV1.decodeC(_invoke_("startSplitPhase", startWire)))
        prepare(call => call.currentC().flatMap { state =>
          val context = F.context.copy(facts = facts, owner = F.context.owner.copy(start = typedStart.reference,
            handle = take(WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, state)), instanceRevision = state.revision))
          take(SplitPhaseManagedPartitionContextRegistration.bindC(owner.configuration, declaration, F.catalog))
            .registerForCallC(context, call.core).map(_ => ())
        })
        When("the owning public advance commits the actual semantic suspension")
        pending = Some(_invoke_("advanceSplitPhase", advance))
        prepare(call => call.currentC().map(value => { pendingstate = Some(value); () }))
        Then("the issued request and original history are available after the issuing runtime closes")
        pendingstate.get.suspension should not be empty
      }
      val work = take(WorkflowWorkOrderJsonV1.decodeC(pending.get, J.requestCodec))
      val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request
      val input = order.input.get.value
      def _answer_(minutes: Int): String = {
        val evidence = S.EvidenceReference("split-race-semantic-evidence-" + minutes, "1")
        val judgment = S.Judgment(S.Decision.Enriched, "Actual answer with estimate " + minutes,
          input.items.map(item => S.NovelEstimate(item.identity, minutes, minutes + 60, "bounded work", "novel closure")),
          input.boundaries.map(boundary => S.BoundaryJudgment(boundary.edge.afterItem,
            SplitPhasePartitionEnumeration.EdgeDisposition.Either, "independent closure")), Vector(evidence))
        val result = ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision,
          order.context.snapshot, TypedValue(J.resultCodec.typeIdentity, judgment),
          ContextReference(input.reply.result.identity, input.reply.result.revision), Vector.empty,
          ExecutionEvidence(Vector(ContextReference(evidence.identity, evidence.revision)), Some(F.command.participantIdentity)))
        val json = io.circe.parser.parse(take(NativeWorkflowResultSubmissionFixture.encodeC(result, J.resultCodec,
          omitReference = true))).toOption.get
        json.mapObject(_.remove("requestIdentity").add("sessionIdentity", Json.fromString(session))).noSpaces
      }
      val original = _answer_(120)
      val winner = _answer_(121)
      def _compete_(core: ActionCall.Core): Consequence[Unit] = {
        observations += 1
        observations shouldBe 1
        Given("the original public action has actually resolved this uniquely issued request and selected its closing action")
        SplitPhaseDefinitionBinding.sameRecord(take(new Setup(core, _ => Consequence.unit).currentC()), pendingstate.get) shouldBe true
        When("another independently assembled runtime completes a different valid answer through the SAME normal public Operation before the original afterCommit")
        withRuntime(root) { (owner, component, actor, prepare) =>
          val context = take(ExecutionContext.withRoutingSessionIdentity(actor, session))
          winnerack = Some(scalar(take(owner.executeOperationResponseInContext(
            _request_(component, "submitPartitionSemanticResult", winner), context))))
          prepare(call => call.currentC().map(value => { winnerstate = Some(value); () }))
          Then("the competing Operation owns exactly one real adjacent completion, not a fixture-installed native append")
          winnerstate.get.history.dropRight(1) shouldBe pendingstate.get.history
          winnerstate.get.revision shouldBe pendingstate.get.revision.next
          winnerstate.get.suspension shouldBe None
        }
        Consequence.unit
      }
      withRuntime(root, completionobservation = Some(_compete_)) { (owner, component, actor, prepare) =>
        val context = take(ExecutionContext.withRoutingSessionIdentity(actor, session))
        When("the original already-resolved answer proceeds to its real owning completion boundary")
        val rejected = owner.executeOperationResponseInContext(_request_(component, "submitPartitionSemanticResult", original), context)
        var observed = Option.empty[InstanceRecord]
        prepare(call => call.currentC().map(value => { observed = Some(value); () }))
        Then("actual retained result and native state refuse replacement of the winner without caller revision or renewed authority echoes")
        rejected.isSuccess shouldBe false
        observations shouldBe 1
        SplitPhaseDefinitionBinding.sameRecord(observed.get, winnerstate.get) shouldBe true
        observed.get.history shouldBe winnerstate.get.history
      }
      var terminal = Option.empty[String]
      var finalstate = Option.empty[InstanceRecord]
      withRuntime(root) { (owner, component, actor, prepare) =>
        val context = take(ExecutionContext.withRoutingSessionIdentity(actor, session))
        def _call_(operation: String, wire: String) = owner.executeOperationResponseInContext(_request_(component, operation, wire), context)
        When("a fresh runtime observes the actual winner and refuses the losing answer, then advances the saved result")
        scalar(take(_call_("submitPartitionSemanticResult", winner))) shouldBe winnerack.get
        _call_("submitPartitionSemanticResult", original).isSuccess shouldBe false
        terminal = Some(scalar(take(_call_("advanceSplitPhase", advance))))
        prepare(call => call.currentC().map(value => { finalstate = Some(value); () }))
        val preview = take(WorkflowTerminalJsonV1.decodeC(terminal.get, SplitPhaseManagedSelectedPreview.payloadCodec))
        val payload = preview.current.asInstanceOf[WorkflowContinuation.Terminal[Json]].result.value
        Then("the retained winner produces a complete all-source Preview and real Completed native state")
        payload.hcursor.get[Vector[Json]]("children").toOption.get
          .flatMap(_.hcursor.get[Vector[Json]]("orderedWorkItems").toOption.get)
          .map(_.hcursor.get[String]("identity").toOption.get) shouldBe facts.proposal.inventory.orderedUnfinishedItems.map(_.value)
        finalstate.get.history.dropRight(2) shouldBe winnerstate.get.history
        finalstate.get.lifecycle shouldBe Lifecycle.Completed
        finalstate.get.suspension shouldBe None
        preview.handle shouldBe take(WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, finalstate.get))
      }
      val explicitadvance = take(SplitPhaseAdvanceCommandJsonV1.encodeC(SplitPhaseAdvanceCommand(typedStart.instanceIdentity.value,
        SplitPhasePartitionContextReference(F.context.reference.identity, F.context.reference.revision))))
      When("a separate JVM observes the actual winner and complete Preview without replaying the losing prepared action")
      val reopened = _separate_process_observation(root, "PREVIEW", explicitadvance, Some(winner))
      Then("retained acknowledgement, full terminal and physical native history survive the process boundary unchanged")
      reopened.hcursor.downField("acknowledgement").get[Boolean]("success").toOption shouldBe Some(true)
      reopened.hcursor.downField("acknowledgement").get[String]("wire").toOption shouldBe winnerack
      reopened.hcursor.downField("result").get[String]("wire").toOption shouldBe terminal
      reopened.hcursor.get[String]("after").toOption shouldBe Some(take(WorkflowInstanceRecordJsonV1.encodeC(finalstate.get)))
    }
    "complete saved SplitPhase semantic work in another JVM and retain its Preview and acknowledgement" in {
      import SplitPhaseExplicitPartitionSemantic as S
      import SplitPhaseExplicitPartitionSemanticJson as J
      Given("a public Operation-issued WorkOrder committed before the original runtime closes")
      val root = Files.createTempDirectory("smwf-direct-resume-")
      val facts = _semantic_preview_facts()
      val advance = take(SplitPhaseAdvanceCommandJsonV1.encodeC(SplitPhaseAdvanceCommand(typedStart.instanceIdentity.value,
        SplitPhasePartitionContextReference(F.context.reference.identity, F.context.reference.revision))))
      var pending = Option.empty[String]
      var pendingstate = Option.empty[InstanceRecord]
      withRuntime(root) { (owner, component, actor, prepare) =>
        def _invoke_(operation: String, wire: String): String = scalar(take(owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "SplitPhaseService", operation = operation,
            arguments = List(Argument("arg1", wire))), actor)))
        prepare(call => prepareInputsWithFacts(call, facts))
        take(WorkflowWaitJsonV1.decodeC(_invoke_("startSplitPhase", startWire)))
        prepare { call => call.currentC().flatMap { state =>
          val context = F.context.copy(facts = facts, owner = F.context.owner.copy(start = typedStart.reference,
            handle = take(WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, state)), instanceRevision = state.revision))
          take(SplitPhaseManagedPartitionContextRegistration.bindC(owner.configuration, declaration, F.catalog))
            .registerForCallC(context, call.core).map(_ => ())
        } }
        When("advance durably issues only the unresolved semantic work")
        pending = Some(_invoke_("advanceSplitPhase", advance))
        prepare(call => call.currentC().map(state => { pendingstate = Some(state); () }))
      }
      val work = take(WorkflowWorkOrderJsonV1.decodeC(pending.get, J.requestCodec))
      val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request
      val input = order.input.get.value
      Then("the saved assignment names the unknown item and boundary, not a caller-selected stage")
      input.items.map(_.identity.value) shouldBe Vector("item-1")
      input.boundaries.map(_.edge.afterItem.value) shouldBe Vector("item-0")
      Given("a specification-owned semantic result responding only to that issued assignment")
      val evidence = S.EvidenceReference("direct-process-semantic-evidence", "1")
      val judgment = S.Judgment(S.Decision.Enriched, "bounded independent semantic closures",
        input.items.map(item => S.NovelEstimate(item.identity, 120, 180, "bounded work", "novel closure")),
        input.boundaries.map(boundary => S.BoundaryJudgment(boundary.edge.afterItem,
          SplitPhasePartitionEnumeration.EdgeDisposition.Either, "independent closure")), Vector(evidence))
      val result = ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision,
        order.context.snapshot, TypedValue(J.resultCodec.typeIdentity, judgment),
        ContextReference(input.reply.result.identity, input.reply.result.revision), Vector.empty,
        ExecutionEvidence(Vector(ContextReference(evidence.identity, evidence.revision)), Some(F.command.participantIdentity)))
      val reply = take(WorkflowResultJsonV1.encodeC(result, J.resultCodec))
      When("a separate JVM reopens pending work, returns the assigned result and advances through the deterministic work")
      val completed = _separate_process_observation(root, "RESUME", advance, Some(reply))
      def _wire_(field: String): String = {
        completed.hcursor.downField(field).get[Boolean]("success").toOption shouldBe Some(true)
        completed.hcursor.downField(field).get[String]("wire").toOption.get
      }
      def _state_(field: String): InstanceRecord = take(WorkflowInstanceRecordJsonV1.decodeC(
        completed.hcursor.get[String](field).toOption.get,
        take(WorkflowInstanceRecordJsonV1.catalogC(Vector(F.catalog.definition)))))
      Then("the process receives the original WorkOrder and commits one adjacent semantic completion entry")
      _wire_("result") shouldBe pending.get
      SplitPhaseDefinitionBinding.sameRecord(_state_("before"), pendingstate.get) shouldBe true
      val acknowledgement = _wire_("acknowledgement")
      take(WorkflowWaitJsonV1.decodeC(acknowledgement)).current shouldBe a[WorkflowContinuation.Wait]
      val completionstate = _state_("completionState")
      completionstate.history.dropRight(1) shouldBe pendingstate.get.history
      completionstate.revision shouldBe pendingstate.get.revision.next
      completionstate.lifecycle shouldBe Lifecycle.Active
      completionstate.suspension shouldBe None
      Then("Workflow-selected Preview covers the source and its real completed native state")
      val terminal = _wire_("resumed")
      val preview = take(WorkflowTerminalJsonV1.decodeC(terminal, SplitPhaseManagedSelectedPreview.payloadCodec))
      val payload = preview.current.asInstanceOf[WorkflowContinuation.Terminal[Json]].result.value
      payload.hcursor.get[String]("mode").toOption shouldBe Some("PREVIEW")
      payload.hcursor.get[Vector[Json]]("children").toOption.get
        .flatMap(_.hcursor.get[Vector[Json]]("orderedWorkItems").toOption.get)
        .map(_.hcursor.get[String]("identity").toOption.get) shouldBe facts.proposal.inventory.orderedUnfinishedItems.map(_.value)
      val finalstate = _state_("after")
      finalstate.history.dropRight(2) shouldBe completionstate.history
      finalstate.lifecycle shouldBe Lifecycle.Completed
      finalstate.suspension shouldBe None
      preview.handle shouldBe take(WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, finalstate))
      When("another JVM observes the exact result and terminal after that completing process exits")
      val reopened = _separate_process_observation(root, "PREVIEW", advance, Some(reply))
      Then("both responses and the completed history survive without duplicate entries")
      reopened.hcursor.downField("result").get[String]("wire").toOption shouldBe Some(terminal)
      reopened.hcursor.downField("acknowledgement").get[String]("wire").toOption shouldBe Some(acknowledgement)
      reopened.hcursor.get[String]("after").toOption.get shouldBe take(WorkflowInstanceRecordJsonV1.encodeC(finalstate))
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
