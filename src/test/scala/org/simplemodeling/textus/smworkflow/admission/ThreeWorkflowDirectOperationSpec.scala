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
import org.goldenport.cncf.workflow.{ContextReference, WorkflowComponentStorage, WorkflowMechanicalTransition, WorkflowDecisionJsonV1, WorkflowInstanceRecordJsonV1, WorkflowWaitJsonV1, WorkflowWorkOrderJsonV1, WorkflowResultJsonV1, WorkflowTerminalJsonV1}
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
  * it replaces no public Operation, evidence adapter or Workflow implementation.
  * Input/evidence rows are test data, not a production human interaction channel.
  * No launcher, CLI command, fabricated history or direct SQLite write is used.
  * @since   Oct.  7, 2026
  * @version Oct.  8, 2026
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

  private final class Setup(val core: ActionCall.Core, task: Setup => Consequence[Unit]) extends ProcedureActionCall {
    def createC(collection: DataStore.CollectionId, identity: String, record: Record): Consequence[Unit] =
      ComponentRecordStorage.bindForCallC(core, "workflow", collection, DataStore.StringEntryId(identity))
        .flatMap(_.createC(record))
    def currentC(): Consequence[InstanceRecord] =
      instances.loadForCallC(F.configuration, F.record.instanceIdentity, core).flatMap {
        case Some(record) => Consequence.success(record)
        case None => Consequence.stateConflict("Specification has no Operation-created instance")
      }
    def applicationReceiptExistsC(reference: String): Consequence[Boolean] =
      WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
        SmWorkflowComponent.componentId, declaration.configuration, Vector(F.catalog.definition),
        declaration.instances, declaration.continuations).flatMap(_.bindMechanicalC(
          SplitPhasePlanningCheckpointPreparation.advanceOperation, SplitPhaseManagedSelectedApply.payloadCodec))
        .flatMap(_.loadRetainedC(WorkflowMechanicalTransition.Reference(reference, "1"))(using executionContext)).map(_.nonEmpty)
    def humanEvidenceC(question: WorkflowInteraction[?, Nothing],
      result: ContinuationResult[SplitPhaseHumanDirectionAnswerContract.Answer]): Consequence[Unit] = for {
      binding <- ThreeWorkflowHostEvidencePorts.splitAnswerBindingC(SplitPhaseHumanAnswerVerification.Binding(
        core.component.get.core.instanceId, question, result, F.command.participantIdentity))
      now = executionContext.clock.instant()
      reference = result.resultReference
      identity = ThreeWorkflowHostEvidencePorts.key(reference.identity, reference.revision)
      evidence = NativeHostEvidence.Evidence(NativeHostEvidence.schema, NativeHostEvidence.Purpose.SplitAnswer.identity,
        identity, reference.identity, reference.revision, "normal-development-host", F.command.participantIdentity,
        binding, now.minusSeconds(60).toString, now.plusSeconds(3600).toString)
      _ <- createC(collection("host_evidence"), NativeHostEvidence.recordIdentity(NativeHostEvidence.Purpose.SplitAnswer, identity),
        Record.data(NativeHostEvidence.storageField -> take(NativeHostEvidence.encodeStorageC(evidence))))
    } yield ()
    def execute(): Consequence[OperationResponse] = task(this).map(_ => OperationResponse.Scalar("specification-setup"))
  }

  private def withRuntime(root: Path, processplan: Option[Json] = None)(task: (Subsystem, org.goldenport.cncf.component.Component,
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
      // Only add the setup service. All fifteen public Operations and all their
      // ports are produced by the same decoder/binder as the default factory.
      val factory = new SmWorkflowConfiguredComponentFactory(ThreeWorkflowHostParameters.declarations, Vector.empty,
        (creation, parameters, resolved) => for {
          host <- ThreeWorkflowHostParameters.decodeC(creation, parameters, resolved)
          services <- ThreeWorkflowLauncherAssembly.bindC(creation, host)(using resolved)
        } yield services :+ spec.ServiceDefinition("SpecificationSetup", spec.OperationDefinitionGroup(NonEmptyVector.of(probe))),
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
    start: SplitPhaseTypedStartPreparation.Record = typedStart): Consequence[Unit] = {
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
      SplitPhaseTypedStartHumanVerification.Binding(call.core.component.get.core.instanceId, start)))
    val evidence = NativeHostEvidence.Evidence(NativeHostEvidence.schema, NativeHostEvidence.Purpose.SplitStart.identity,
      identity, "specification-start-selection", "1", "normal-development-host", start.command.participantIdentity,
      binding, now.minusSeconds(60).toString, now.plusSeconds(3600).toString)
    val rows = inputs.map((purpose, id, text) => (collection("split_" + purpose.toString.toLowerCase), id, Record.data(J.storageField -> text))) ++ Vector(
      (collection("split_starts"), start.reference.identity,
        Record.data(SplitPhaseTypedStartJsonV1.storageField -> take(SplitPhaseTypedStartJsonV1.encodeC(start)))),
      (collection("split_selections"), start.selection.selectionId,
        Record.data(ProfileSelectionJsonV1.storageField -> take(ProfileSelectionJsonV1.encodeStorageC(start.selection)))),
      (collection("host_evidence"), NativeHostEvidence.recordIdentity(NativeHostEvidence.Purpose.SplitStart, identity),
        Record.data(NativeHostEvidence.storageField -> take(NativeHostEvidence.encodeStorageC(evidence)))))
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
      val acknowledgement = completion.map(wire => _response_(_invoke_c_("submitPartitionSemanticResult", wire)))
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
    recordEffects: Boolean = false, interrupt: Boolean = false): Json = {
    import scala.jdk.CollectionConverters.*
    val directory = Files.createTempDirectory(root, "operation-observation-")
    val input = directory.resolve("input.json")
    val output = directory.resolve("output.json")
    val log = directory.resolve("process.log")
    Files.writeString(input, Json.obj("mode" -> Json.fromString(mode), "advance" -> Json.fromString(advance),
      "completion" -> completion.map(Json.fromString).getOrElse(Json.Null),
      "recordDocumentEffects" -> Json.fromBoolean(recordEffects), "interruptDocumentEffect" -> Json.fromBoolean(interrupt)).noSpaces)
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
      s"continue actual scope through $direction and any assigned reasoning to its complete outcome" in {
        import SplitPhaseExplicitPartitionFixture.{base, enumerate, proof}
        import SplitPhaseExplicitObjectiveSelection as O
        import SplitPhaseScopeSemantic as S
        import SplitPhaseScopeSemanticJson as J
        import SplitPhaseExplicitReasoningSemantic as R
        import SplitPhaseExplicitReasoningSemanticJson as RJ
        import SplitPhaseHumanDirectionAnswerContract as H
        val scopeenriched = direction == "enriched" || direction.startsWith("reasoning-")
        Given("owned complete source/handoff data and the production three-service factory on managed SQLite")
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
          val judgment = S.Judgment(input.context, input.checkpoint,
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
          When("only the assigned semantic scope result is returned")
          val reply = take(WorkflowResultJsonV1.encodeC(scopereply.get, J.resultCodec))
          val acknowledgement = invoke("submitHandoffScopeResult", reply)
          scopeack = Some(acknowledgement)
          val after = current()
          Then("one adjacent completion is saved and a duplicate does not append")
          after.history.dropRight(1) shouldBe before.history
          invoke("submitHandoffScopeResult", reply) shouldBe acknowledgement
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
            val reply = take(WorkflowResultJsonV1.encodeC(humanreply.get, H.payloadCodec))
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
            val judgment = R.Judgment(input.context, if (direction.startsWith("reasoning-")) R.Decision.DecisionNeeded else R.Decision.Enriched, "Classify only assigned unknown work",
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
            When("the assigned classification is returned and deterministic progression resumes")
            val reply = take(WorkflowResultJsonV1.encodeC(result, RJ.resultCodec))
            val acknowledgement = invoke("submitReasoningResult", reply)
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
              invoke("advanceSplitPhase", advance) shouldBe questionwire
              observe(questionwire, current())
              val interpretation = R.Judgment(input.context, R.Decision.Enriched, "Human interpretation of only the unknown items",
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
              val answerwire = take(WorkflowResultJsonV1.encodeC(humananswer, H.payloadCodec))
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
              val admittedwire = take(WorkflowResultJsonV1.encodeC(admittedanswer, H.payloadCodec))
              if (direction == "reasoning-resolved") {
                val invalids = Vector(
                  "incomplete" -> interpretation.copy(assignments = Vector.empty),
                  "crossed-item" -> interpretation.copy(assignments = interpretation.assignments.map(_.copy(
                    identity = SplitPhaseExplicitProposalPrecheck.WorkItemIdentity("foreign-item")))),
                  "crossed-context" -> interpretation.copy(context = input.context.copy(identity = "foreign-context")),
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
                prepare(_.createC(collection("split_humanresolutions"), record.reference.identity,
                  Record.data(HR.storageField -> take(HR.encodeC(record)))))
              }
              Given("independently issued evidence for the exact genuine reasoning question and complete answer")
              prepare(_.humanEvidenceC(reasoningquestion, admittedanswer))
              When("the unchanged public Human completion receives the assigned answer")
              val humanack = invoke("submitSplitHumanDirection", admittedwire)
              val humanafter = current()
              Then("one managed completion saves the answer and successor, preserving the original AI DecisionNeeded")
              humanafter.history.dropRight(1) shouldBe humanbefore.history
              When("the exact completed Human answer is repeated")
              invoke("submitSplitHumanDirection", admittedwire) shouldBe humanack
              Then("the original acknowledgement is observed without another append")
              current() shouldBe humanafter
              val changed = admittedanswer.copy(result = admittedanswer.result.copy(value =
                if (answer == H.Answer.Cancel) H.Answer.Defer else H.Answer.Cancel))
              When("a changed Human answer attempts to replace the completed result")
              invokeC("submitSplitHumanDirection", take(WorkflowResultJsonV1.encodeC(changed, H.payloadCodec))).isSuccess shouldBe false
              Then("the completed native state and canonical answer remain unchanged")
              current() shouldBe humanafter
              When("the original AI DecisionNeeded is submitted again after Human completion")
              invoke("submitReasoningResult", reply) shouldBe acknowledgement
              Then("the original AI acknowledgement remains canonical without another native append")
              current() shouldBe humanafter
              When("a separate JVM advances from the saved Human completion, without resubmitting AI or Human answers")
              val reopened = _separate_process_observation(root, "PREVIEW", advance,
                allowProgress = direction == "reasoning-resolved")
              Then("the owning reopened Operation successfully observes or progresses the saved successor")
              reopened.hcursor.downField("result").get[Boolean]("success").toOption shouldBe Some(true)
              val savedwire = reopened.hcursor.downField("result").get[String]("wire").toOption.get
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
      for (session <- Vector("first execution", "fresh runtime")) withRuntime(root) { (owner, component, actor, prepare) =>
        def invoke(operation: String, wire: String) = scalar(take(owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "SplitPhaseService", operation = operation,
            arguments = List(Argument("arg1", wire))), actor)))
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
        When("the actual public advance Operation is called directly for " + session)
        val issued = invoke("advanceSplitPhase", advance)
        Then("the semantic WorkOrder is durable and the caller does not choose a stage or issue a replacement")
        val work = take(WorkflowWorkOrderJsonV1.decodeC(issued, SplitPhaseExplicitPartitionSemanticJson.requestCodec))
        work.handle.instanceIdentity shouldBe F.record.instanceIdentity
        work.current shouldBe a[WorkflowContinuation.WorkOrder[?]]
        pending.foreach(_ shouldBe issued)
        pending = Some(issued)
      }
      When("a separate JVM advances the same saved pending instance without input or context re-registration")
      val reopened = _separate_process_observation(root, "PENDING", advance)
      Then("the original WorkOrder returns without a history append in that process")
      reopened.hcursor.downField("result").get[Boolean]("success").toOption shouldBe Some(true)
      reopened.hcursor.downField("result").get[String]("wire").toOption shouldBe pending
    }
    "complete SplitPhase semantic work to Preview and reopen its final outcome through direct Operations" in {
      import SplitPhaseExplicitPartitionSemantic as S
      import SplitPhaseExplicitPartitionSemanticJson as J
      Given("specification-owned objective data and production Operations on the same managed SQLite store")
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
        def invokeC(operation: String, wire: String) = owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "SplitPhaseService", operation = operation,
            arguments = List(Argument("arg1", wire))), actor)
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
          val judgment = S.Judgment(input.context, S.Decision.Enriched, "bounded independent semantic closures",
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
          When("the exact assigned result is returned through the public completion Operation")
          val resultWire = take(WorkflowResultJsonV1.encodeC(result, J.resultCodec))
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
          When("a different semantic judgment is returned for the already completed assignment")
          val changed = result.copy(result = result.result.copy(value = judgment.copy(rationale = "different reply")))
          invokeC("submitPartitionSemanticResult", take(WorkflowResultJsonV1.encodeC(changed, J.resultCodec))).isSuccess shouldBe false
          Then("the committed result is not replaced and native history is unchanged")
          SplitPhaseDefinitionBinding.sameRecord(current(), completed) shouldBe true
          completedReply = Some(resultWire -> ack)
          When("advance absorbs deterministic candidate enumeration, selection and Preview publication")
          val previewWire = invoke("advanceSplitPhase", advance)
          val preview = take(WorkflowTerminalJsonV1.decodeC(previewWire, SplitPhaseManagedSelectedPreview.payloadCodec))
          val payload = preview.current.asInstanceOf[WorkflowContinuation.Terminal[io.circe.Json]].result.value
          val state = current()
          Then("the final Preview covers all source work and carries the actual completed native handle")
          payload.hcursor.get[String]("mode").toOption.get shouldBe "PREVIEW"
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
      val judgment = S.Judgment(input.context, S.Decision.Enriched, "bounded independent semantic closures",
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
