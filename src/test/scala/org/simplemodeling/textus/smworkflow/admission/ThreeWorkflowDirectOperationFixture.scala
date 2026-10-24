package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import java.nio.file.{Files, Path}
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
import org.goldenport.cncf.workflow.{ContextReference, StateMachineOperationIdentity, WorkflowComponentStorage, WorkflowResultJsonV1, WorkflowWorkOrderJsonV1, WorkflowExecutionHarness}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.{Property, Request}
import org.goldenport.schema.Multiplicity
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.goldenport.record.io.RecordDecoder
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import org.simplemodeling.textus.smworkflow.caller.NativeWorkflowCallerExecution
import org.simplemodeling.textus.smworkflow.preparation.{NativeHumanDecisionPreparation, NativeStartPreparation}
import SplitPhaseExplicitPartitionFixture.take
import SplitPhaseTypedStartFixture as F

/** Shared actual public-operation fixture; no semantic/provider or Human authority.
  * @since   Oct. 24, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
private[admission] trait ThreeWorkflowDirectOperationFixture extends Matchers with GivenWhenThen {
  self: org.scalatest.Informing =>
  protected def _assert_issued_context[A](value: Option[V.WorkflowIssuedWorkContext],
    issued: WorkflowInteraction[A, Nothing], mode: String,
    codec: WorkflowResultJsonV1.PayloadCodec[A]): Unit = {
    value should not be empty
    val view = value.get
    val order = issued.current.asInstanceOf[WorkflowContinuation.WorkOrder[A]]
    val request = order.request
    val canonical = take(new RecordDecoder().json(take(WorkflowWorkOrderJsonV1.encodeC(issued, codec))))
    val native = canonical.getRecord("request").get
    view.thinkingMode shouldBe mode
    view.schemaVersion shouldBe "cncf.workflow-protocol.v1"
    view.handle.getString("componentIdentity") shouldBe Some(issued.handle.componentIdentity.name)
    view.handle.getString("workflowIdentity") shouldBe Some(issued.handle.workflowIdentity.value)
    view.handle.getString("workflowRevision") shouldBe Some(issued.handle.workflowRevision.value)
    view.handle.getString("instanceIdentity") shouldBe Some(issued.handle.instanceIdentity.value)
    view.runId shouldBe request.runId.value
    view.continuationId shouldBe request.continuationId.value
    view.expectedRevision shouldBe request.expectedRevision.value
    view.requiredOperation shouldBe request.requiredOperation.capability
    view.inputType shouldBe request.input.map(_.typeIdentity)
    view.resultType shouldBe request.resultType.map(_.value)
    view.operation.getString("service") shouldBe Some(request.operation.service)
    view.operation.getString("operation") shouldBe Some(request.operation.operation)
    view.completionOperation.getString("service") shouldBe Some(request.completionOperation.service)
    view.completionOperation.getString("operation") shouldBe Some(request.completionOperation.operation)
    view.context shouldBe native.getRecord("context").get
    view.completion shouldBe native.getRecord("completion").get
    view.evidence shouldBe native.getRecord("evidence").get
    view.executionRequirement shouldBe canonical.getRecord("requirement").get
    view.executionRequirement.getString("reasoning") shouldBe Some(order.requirement.reasoning.value)
    view.executionRequirement.getString("reasoningClass") shouldBe order.requirement.reasoningClass.map(_.value)
    view.executionRequirement.getBoolean("reviewRequired") shouldBe Some(order.requirement.reviewRequired)
  }

  protected def _scope_frontier_(input: SplitPhaseNativeHumanDirection.Input): SplitPhaseScopeProjectionSnapshot.Record =
    input.frontier
  protected def collection(name: String) = DataStore.CollectionId.EntityStore(
    EntityCollectionId("smworkflow", "cli", name))
  protected val declaration = SplitPhaseManagedStartAssembly.Declaration(F.configuration,
    collection("instances"), collection("continuations"))
  protected val instances = CallScopedWorkflowInstances.managed(F.configuration, F.catalog.definition,
    declaration.instances, declaration.continuations)
  protected val typedStart = F.record
  protected val startWire = NativeWorkflowOperationInputFixture.start(F.input)
  protected def _business_value[A: org.goldenport.convert.ValueReader](value: OperationResponse): A = value match {
    case OperationResponse.RecordResponse(record) => take(summon[org.goldenport.convert.ValueReader[A]].readC(record))
    case other => fail(s"Expected declared public business value: $other")
  }
  protected final class Setup(val core: ActionCall.Core, task: Setup => Consequence[Unit]) extends ProcedureActionCall {
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
    /** Read genuine assignments for independent host evidence and passive
      * resolution data, never reconstruct public input from a native wire. */
    private def _storage_c() = WorkflowComponentStorage.bindC(
      ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin), SmWorkflowComponent.componentId,
      declaration.configuration, Vector(F.catalog.definition), declaration.instances, declaration.continuations)
    private def _pending_c(identity: InstanceIdentity) = currentC(identity).flatMap(_.suspension
      .map(value => Consequence.success(org.goldenport.cncf.workflow.ContinuationIdentity(value.continuationIdentity.value)))
      .getOrElse(Consequence.stateConflict("Specification Split has no pending assignment")))
    def workC[A](identity: InstanceIdentity, operation: StateMachineOperationIdentity,
      codec: WorkflowResultJsonV1.PayloadCodec[A]): Consequence[WorkflowInteraction[A, Nothing]] = for {
      continuation <- _pending_c(identity)
      storage <- _storage_c()
      reader <- storage.bindIssuedC(operation, codec)
      work <- reader.loadC(continuation)(using executionContext).flatMap(_.map(Consequence.success)
        .getOrElse(Consequence.stateConflict("Specification Split work is absent")))
    } yield work
    def questionC[A](identity: InstanceIdentity, operation: StateMachineOperationIdentity,
      codec: WorkflowResultJsonV1.PayloadCodec[A]): Consequence[WorkflowInteraction[A, Nothing]] = for {
      continuation <- _pending_c(identity)
      storage <- _storage_c()
      reader <- storage.bindDecisionC(operation, codec)
      question <- reader.loadC(continuation)(using executionContext).flatMap(_.map(Consequence.success)
        .getOrElse(Consequence.stateConflict("Specification Split question is absent")))
    } yield question
    def humanEvidenceC(context: V.WorkflowHumanDecisionContext,
      result: ContinuationResult[SplitPhaseHumanDirectionAnswerContract.Answer]): Consequence[Unit] = for {
      body <- SplitPhaseHumanDirectionAnswerContract.encodeC(result.result.value)
      answer <- new org.goldenport.record.io.RecordDecoder().json(body.hcursor.downField("payload").focus.get.noSpaces)
        .flatMap(V.SplitPhaseHumanDirectionAnswer.createC)
      prepared <- NativeHumanDecisionPreparation.splitC(context, answer,
        V.WorkflowArtifactReference(result.resultReference.identity, result.resultReference.revision),
        V.WorkflowExecutionEvidence(Vector.empty, result.evidence.workerIdentity, None, None),
        F.participantIdentity, "normal-development-host", executionContext.clock.instant().minusSeconds(60))
      _ <- createC(collection("host_evidence"), prepared.recordIdentity, prepared.record)
    } yield ()
    def privateNativeHumanEvidenceC(question: WorkflowInteraction[?, Nothing],
      result: ContinuationResult[SplitPhaseHumanDirectionAnswerContract.Answer]): Consequence[Unit] = for {
      binding <- ThreeWorkflowExternalEvidencePorts.splitAnswerBindingC(SplitPhaseHumanAnswerVerification.AnswerContext(
        core.component.get.core.instanceId, question, result, F.participantIdentity))
      now = executionContext.clock.instant()
      reference = result.resultReference
      identity = ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision)
      evidence = NativeExternalEvidence.Evidence(NativeExternalEvidence.schema, NativeExternalEvidence.Purpose.SplitAnswer.identity,
        identity, reference.identity, reference.revision, "normal-development-host", F.participantIdentity,
        binding, now.minusSeconds(60).toString)
      _ <- createC(collection("host_evidence"), NativeExternalEvidence.recordIdentity(NativeExternalEvidence.Purpose.SplitAnswer, identity),
        Record.data(NativeExternalEvidence.storageField -> take(NativeExternalEvidence.encodeStorageC(evidence))))
    } yield ()
    def execute(): Consequence[OperationResponse] = task(this).map(_ => OperationResponse.Scalar("specification-setup"))
  }

  protected def withRuntime(root: Path, processplan: Option[Json] = None,
    completionobservation: Option[ActionCall.Core => Consequence[Unit]] = None,
    executionParameters: Map[String, String] = Map.empty)(task: (Subsystem, org.goldenport.cncf.component.Component,
    ExecutionContext, (Setup => Consequence[Unit]) => Unit) => Unit): Unit = {
    val raw = ThreeWorkflowNormalHostFixture.values(root) ++ Map(
      "textus.subsystem.user-mode" -> "standalone", "textus.datastore.kind" -> "sqlite",
      "textus.datastore.path" -> root.resolve("operation.sqlite").toString) ++
      Map("store-name" -> "workflow", "store-identity" -> "partition-context", "migration-policy" -> "v1",
        "retention-policy" -> "v1", "lease-policy" -> "v1", "instance-collection" -> collection("instances").print,
        "continuation-collection" -> collection("continuations").print)
        .map((key, value) => ("sm-workflow.goal-phase.persistence." + key) -> value) ++ executionParameters
    val config = ResolvedConfiguration(HostConfiguration(raw.map((key, value) =>
      key -> ConfigurationValue.StringValue(value))), ConfigurationTrace.empty)
    val context = ExecutionContext.create()
    val policy = take(EventPersistencePolicy.createC("direct-operation-spec-v1", Vector(
      EventPersistencePolicy.Rule(EventTypeId("transient"), EventPersistencePolicy.Mode.Transient))))
    val persistence = take(EventStorePersistenceDeclaration.createC("events", collection("cursor"), collection("events"),
      collection("indexes"), Vector(EventStorePersistenceDeclaration.WorkflowGrant(SmWorkflowComponent.componentId,
        "workflow", declaration.instances, declaration.continuations))))
    val grants = take(ComponentRecordStorageDeclaration.createC((ThreeWorkflowNormalHostFixture.recordCollections ++
      executionParameters.get("sm-workflow.journal").toVector.flatMap(raw =>
        io.circe.parser.parse(raw).toOption.flatMap(_.hcursor.get[String]("collection").toOption))).distinct.map { name =>
      ComponentRecordStorageDeclaration.CreateGrant(SmWorkflowComponent.componentId, "workflow",
        DataStore.CollectionId.EntityStore(take(EntityCollectionId.parse(name))))
    }))
    val bootstrap = if (processplan.isEmpty) Some(take(SubsystemEventStoreBootstrap.createC(policy, persistence,
      ScopeContext(ScopeKind.Subsystem, "direct-operation-spec", None, context.observability), config, None,
      org.goldenport.cncf.path.AliasResolver.empty, grants))) else None
    val embedded = processplan.map { plan =>
      val directory = Files.createDirectories(root.resolve(".textus"))
      val host = raw ++ Map("textus.subsystem.storage.plan" -> ThreeWorkflowNormalHostFixture.storagePlan.noSpaces,
        "textus.subsystem.process-execution.plan" -> plan.noSpaces,
        WorkflowSelectedDocumentFiles.repositoryKey -> "repo",
        WorkflowSelectedDocumentFiles.workspaceKey -> WorkflowFileRuntimeFixture.documentsWorkspace)
      Files.writeString(directory.resolve("config.json"), Json.obj(host.toVector.map((key, value) => key -> Json.fromString(value))*).noSpaces)
      Files.writeString(directory.resolve("assembly.yaml"),
        s"""subsystem: direct-operation-spec
           |components: []
           |security:
           |  authentication:
           |    convention: enabled
           |    fallback_privilege: disabled
           |    local_subject:
           |      id: ${F.participantIdentity}
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
          |    start-at: '2026-10-07T00:00:00Z'
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
          request = spec.RequestDefinition.empty, response = spec.ResponseDefinition.void)
        def createOperationRequest(r: Request): Consequence[OperationRequest] = Consequence.success(new CommandAction {
          val request = r
          override def commandExecutionMode = CommandExecutionMode.Sync
          def createCall(core: ActionCall.Core): ActionCall = new Setup(core, setup)
        })
      }
      // All public Operations and ports come from the normal decoder/binder.
      // Optional spec-owned observations surround only genuine actions, keeping
      // their full specification, command mode and afterCommit handling. The
      // completion observation may change only the selected fixture presentation.
      val factory = new SmWorkflowConfiguredComponentFactory(ThreeWorkflowRuntimeParameters.declarations, Vector.empty,
        (creation, parameters, resolved) => for {
          host <- ThreeWorkflowRuntimeParameters.decodeC(creation, parameters, resolved)
          services <- ThreeWorkflowLauncherAssembly.bindC(creation, host)(using resolved)
        } yield services.map { service =>
          if (service.name == "SplitPhaseService" && completionobservation.nonEmpty) {
              val operations = service.operations.operations.toVector.map { operation =>
                if (operation.name == "submitPartitionSemanticResult")
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
            localSubject = Some(GenericSubsystemLocalSubjectBinding(id = F.participantIdentity,
              roles = Vector("user"), capabilities = Vector("user"), attributes = Map.empty, securityLevel = Some("user")))))))))
      val authenticated = take(IngressSecurityResolver.resolve(SubsystemExecutionProfile.Fixed,
        component.logic.executionContext(), Map.empty)).executionContext
      val actor = take(ExecutionContext.withRoutingSessionIdentity(authenticated,
        "specification-operation:" + root.toAbsolutePath.toString))
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

  protected def prepareInputs(call: Setup): Consequence[Unit] = {
    prepareInputsWithFacts(call, F.facts)
  }

  protected def prepareInputsWithFacts(call: Setup, facts: SplitPhaseExplicitPartitionContext.Facts,
    start: SplitPhaseTypedStartPreparation.Record = typedStart, provisionConsent: Boolean = true): Consequence[Unit] = {
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
    val selected = F.selectionFor(start.input, start.reference.identity, start.participantIdentity)
    val allocationrow = Record.data(SplitPhaseTypedStartJsonV1.storageField ->
      take(SplitPhaseTypedStartJsonV1.encodeAllocationC(F.allocationFor(start))))
    val sourcerow = Record.data(J.storageField -> inputs.head._3)
    val prepared = take(_prepare_start_c(call, start, allocationrow, sourcerow))
    val rows = inputs.map((purpose, id, text) => (collection("split_" + purpose.toString.toLowerCase), id,
      if (purpose == Purpose.Source) sourcerow else Record.data(J.storageField -> text))) ++ Vector(
      (collection("split_starts"), start.reference.identity,
        allocationrow),
      (collection("split_contexts"), F.original.record.reference.identity,
        Record.data(SplitPhaseExplicitPartitionContextJsonV1.storageField -> take(SplitPhaseExplicitPartitionContextJsonV1.encodeTemplateC(
          SplitPhaseExplicitPartitionContext.Template(F.original.record.reference,
            SplitPhaseExplicitPartitionContext.TemplateOwner(F.original.record.owner.requirement,
              F.original.record.owner.presentation), facts))))),
      (collection("split_selections"), NativeWorkflowStartBusinessKey(start.input, start.participantIdentity),
        Record.data(ProfileSelectionJsonV1.storageField -> take(ProfileSelectionJsonV1.encodeStorageC(selected))))) ++
      (if (provisionConsent) Vector((collection("host_evidence"), prepared.recordIdentity, prepared.record)) else Vector.empty)
    rows.foldLeft(Consequence.unit) { case (before, (collection, id, row)) => before.flatMap(_ => call.createC(collection, id, row)) }
  }

  protected def _prepare_start_c(call: Setup, start: SplitPhaseTypedStartPreparation.Record,
    allocationRow: Record, sourceRow: Record): Consequence[NativeStartPreparation.Prepared] = {
    NativeStartPreparation.splitC(call.core.component.get.core.instanceId.canonicalKey,
      V.WorkflowArtifactReference(start.reference.identity, start.reference.revision), allocationRow, sourceRow,
      V.WorkflowArtifactReference("specification-start-selection", "1"), start.participantIdentity,
      "normal-development-host", call.executionContext.clock.instant().minusSeconds(60))
  }

  // Workspace-only public V3 host configuration; no executable or File launch route.
  protected def _document_workspace_plan(root: Path): Json = WorkflowFileRuntimeFixture.plan(root)


  protected def _semantic_preview_facts(): SplitPhaseExplicitPartitionContext.Facts = {
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

}
