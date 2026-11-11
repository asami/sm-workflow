package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64
import scala.util.Try
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, InstanceRecord}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.{Record, RecordPresentable}
import org.simplemodeling.textus.smworkflow.{SmWorkflowComponent, value as V}

/** Addresses existing native issued records. No response archive, allocation,
  * workflow progression or reconstruction of successful effects is involved.
  * Historical reads deliberately do not require the current suspension to match;
  * Submit continues to enforce that separate live-frontier boundary.
  * @since   Nov.  8, 2026
  * @version Nov.  8, 2026
  */
private[smworkflow] object WorkflowRetainedWork {
  val schema = "sm-workflow.retained-work-reference.v1"
  val serviceName = "WorkflowRetainedWorkService"
  val readOperation = "readWorkflowRetainedWork"
  val resourcePrefix = "sm-workflow-work:"
  final case class Reference(schemaVersion: String, componentInstance: String, owner: String, workflowRevision: String,
    sessionId: String, identity: String, revision: String, completionOperation: String, materialType: String)
  given Encoder[Reference] = deriveEncoder
  given Decoder[Reference] = deriveDecoder

  private def exact(s: String): Boolean = s != null && s.nonEmpty && s.trim == s && !s.exists(_.isControl)
  def valid(r: Reference): Boolean = r != null && r.schemaVersion == schema &&
    Vector(r.componentInstance, r.owner, r.workflowRevision, r.sessionId, r.identity, r.completionOperation, r.materialType).forall(exact) &&
    r.revision != null && r.revision.matches("[1-9][0-9]*") && Try(r.revision.toLong).isSuccess &&
    Set("GoalPhaseWorkflow", "GoalTaskWorkflow", "DelegateTaskWorkflow", "StandaloneFixWorkflow",
      "RepositorySyncWorkflow", "SplitPhaseWorkflow")(r.owner)
  def resource(r: Reference): String = resourcePrefix + Base64.getUrlEncoder.withoutPadding.encodeToString(r.asJson.noSpaces.getBytes(UTF_8))
  def decodeResourceC(text: String): Consequence[Reference] = for {
    raw <- Option(text).filter(_.startsWith(resourcePrefix)).flatMap(s =>
      Try(new String(Base64.getUrlDecoder.decode(s.stripPrefix(resourcePrefix)), UTF_8)).toOption)
      .map(Consequence.success).getOrElse(invalid("invalid retained-work resource"))
    reference <- io.circe.parser.parse(raw).left.map(_.message).flatMap(_.as[Reference].left.map(_.message))
      .fold(invalid, Consequence.success)
    _ <- if (valid(reference) && resource(reference) == text) Consequence.unit else invalid("noncanonical retained-work resource")
  } yield reference
  def publicReference(r: Reference): V.WorkflowRetainedWorkReference =
    V.WorkflowRetainedWorkReference(r.schemaVersion, r.componentInstance, r.owner, r.workflowRevision, r.sessionId, r.identity,
      r.revision, r.completionOperation, r.materialType, serviceName + "." + readOperation, resource(r))
  def fromPublicC(r: V.WorkflowRetainedWorkReference): Consequence[Reference] =
    if (r == null) invalid("reference is absent") else for {
      decoded <- decodeResourceC(r.resource)
      _ <- if (publicReference(decoded) == r) Consequence.unit else invalid("reference fields differ from the selected resource")
    } yield decoded

  def referenceC(context: V.WorkflowIssuedWorkContext, core: ActionCall.Core): Consequence[Reference] = {
    val owner = context.handle.getString("workflowIdentity")
    val operation = context.completionOperation.getString("operation")
    val value = for {
      workflow <- owner
      workflowrevision <- context.handle.getString("workflowRevision")
      completion <- operation
      material <- context.inputType
      component <- core.component
    } yield Reference(schema, component.core.instanceId.canonicalKey, workflow, workflowrevision, context.runId,
      context.continuationId, context.expectedRevision, completion, material)
    value.filter(valid).map(Consequence.success).getOrElse(invalid("issued context has no exact native owner"))
  }

  /** Reference creation confirms storage through the same resolver used by a
    * fresh public read. A fabricated or allocation-only context cannot pass. */
  def confirmedC(context: V.WorkflowIssuedWorkContext, core: ActionCall.Core): Consequence[V.WorkflowRetainedWorkReference] = for {
    reference <- referenceC(context, core)
    retained <- readC(reference, core)
    actual <- retained.getAny("issuedWorkContext").collect { case r: Record => r }
      .map(V.WorkflowIssuedWorkContext.createC).getOrElse(invalid("retained work has no issued context"))
    _ <- if (actual == context) Consequence.unit else invalid("issued context differs from its retained native record")
  } yield publicReference(reference)

  def confirmedDecisionC(response: Record, call: ActionCall.Core): Consequence[V.WorkflowRetainedWorkReference] = for {
    operation <- response.getString("submitOperation").map(Consequence.success).getOrElse(invalid("Human response has no completion operation"))
    session <- response.getString("submitSessionId").map(Consequence.success).getOrElse(invalid("Human response has no owning Session"))
    selection <- operation match {
      case "submitGoalPhaseOrderedDecision" => Consequence.success("GoalPhaseWorkflow" -> GoalPhaseOrderedHumanDecision.inputCodec.typeIdentity)
      case "submitRepositoryConflictDecision" => Consequence.success("RepositorySyncWorkflow" -> RepositorySyncHumanConflict.inputCodec.typeIdentity)
      case "submitSplitHumanDirection" => Consequence.success("SplitPhaseWorkflow" ->
        (if (response.getAny("reasoningHuman").nonEmpty) SplitPhaseReasoningHumanDirection.inputCodec.typeIdentity else SplitPhaseNativeHumanDirection.inputCodec.typeIdentity))
      case _ => invalid("Human response has no supported native decision owner")
    }
    component <- call.component.map(Consequence.success).getOrElse(invalid("Human response Component is absent"))
    p <- GoalPhaseRuntimeParameters.decodeC(component.initializationParameters)
    definition <- definitionC(selection._1)
    context <- NativeWorkflowSessionInput.contextC(call.executionContext, Some(session))
    storage <- WorkflowComponentStorage.bindC(ComponentCreate(component.subsystem.get, ComponentOrigin.Builtin),
      SmWorkflowComponent.componentId, p.configuration, Vector(definition), p.instances, p.continuations)
    instances <- storage.bindInstancesC()(using context)
    current <- instances.loadC(InstanceIdentity(session))(using context).flatMap(_.map(Consequence.success)
      .getOrElse(invalid("Human owning Session is unavailable")))
    boundary <- current.suspension.map(Consequence.success).getOrElse(invalid("Human response has no native suspension"))
    reference = Reference(schema, component.core.instanceId.canonicalKey, selection._1, current.definition.workflowRevision.value,
      session, boundary.continuationIdentity.value, boundary.expectedRevision.value.toString, operation, selection._2)
    _ <- readC(reference, call)
  } yield publicReference(reference)

  private[admission] def definitionC(owner: String): Consequence[org.goldenport.cncf.workflow.WorkflowInstancePersistence.DefinitionBinding] = owner match {
    case "GoalPhaseWorkflow" => GoalPhaseRuntimeCatalog.loadC().map(_.definition)
    case "RepositorySyncWorkflow" => RepositorySyncDefinitionBinding.bindC
    case "SplitPhaseWorkflow" => SplitPhaseRuntimeCatalog.loadC().map(_.definition)
    case "GoalTaskWorkflow" => GoalTaskRuntimeCatalog.loadC()
    case "DelegateTaskWorkflow" => DelegateTaskRuntimeCatalog.loadC()
    case "StandaloneFixWorkflow" => StandaloneFixRuntimeCatalog.loadC()
    case _ => invalid("unsupported native owner")
  }
  def readResourceC(text: String, core: ActionCall.Core): Consequence[String] =
    decodeResourceC(text).flatMap(readC(_, core)).flatMap(_.toJsonStringC)

  def readC(reference: Reference, call: ActionCall.Core): Consequence[Record] = for {
    _ <- if (valid(reference) && call.component.exists(_.core.instanceId.canonicalKey == reference.componentInstance))
      Consequence.unit else invalid("foreign Component or malformed reference")
    context <- NativeWorkflowSessionInput.contextC(call.executionContext, Some(reference.sessionId))
    core = call.copy(executionContext = context)
    component <- core.component.map(Consequence.success).getOrElse(invalid("Component is unavailable"))
    p <- GoalPhaseRuntimeParameters.decodeC(component.initializationParameters)
    definition <- definitionC(reference.owner)
    _ <- if (definition.workflowRevision.value == reference.workflowRevision) Consequence.unit
      else invalid("selected historical Workflow revision is unsupported by this installation; no current revision was substituted")
    storage <- WorkflowComponentStorage.bindC(ComponentCreate(component.subsystem.get, ComponentOrigin.Builtin),
      SmWorkflowComponent.componentId, p.configuration, Vector(definition), p.instances, p.continuations)
    instances <- storage.bindInstancesC()(using context)
    current <- instances.loadC(InstanceIdentity(reference.sessionId))(using context)
      .flatMap(_.map(Consequence.success).getOrElse(invalid("owning Session is unavailable")))
    _ <- if (current.definition == definition) Consequence.unit else invalid("Session belongs to another Workflow definition")
    _ <- participantC(reference.owner, reference.sessionId, storage, core)
    response <- renderC(reference, storage, current, core)
  } yield response

  private[admission] def participantC(owner: String, sessionId: String, storage: WorkflowComponentStorage, core: ActionCall.Core): Consequence[Unit] = owner match {
    case "GoalPhaseWorkflow" => for {
      starts <- storage.bindMechanicalC(GoalPhaseNativeRun.startOperation, GoalPhaseNativeRun.codec)
      saved <- starts.loadRetainedC(GoalPhaseNativeRun.reference(sessionId))(using core.executionContext)
        .flatMap(_.map(Consequence.success).getOrElse(invalid("owning GoalPhase Start is unavailable")))
      _ <- GoalPhaseParticipantAdmission.admitC(saved.receipt.payload.value.participantIdentity, core.executionContext)
    } yield ()
    case "RepositorySyncWorkflow" => for {
      starts <- storage.bindMechanicalC(RepositorySyncManagedFetch.startOperation, RepositorySyncManagedFetch.codec(true))
      saved <- starts.loadRetainedC(RepositorySyncManagedFetch.reference(sessionId, "start"))(using core.executionContext)
        .flatMap(_.map(Consequence.success).getOrElse(invalid("owning RepositorySync Start is unavailable")))
      participant <- saved.receipt.payload.value.context.map(_.participantIdentity).map(Consequence.success)
        .getOrElse(invalid("owning RepositorySync participant is unavailable"))
      _ <- GoalPhaseParticipantAdmission.admitC(participant, core.executionContext)
    } yield ()
    case "SplitPhaseWorkflow" => for {
      declaration <- SplitPhaseTypedStartReadPolicy.decodeC(core.component.get.subsystem.get.configuration)
      row <- ManagedInputRecordRead.loadSplitTypedStartC(core, declaration.storeName, declaration.collection, "split-start:" + sessionId)
        .flatMap(_.map(Consequence.success).getOrElse(invalid("owning SplitPhase Start is unavailable")))
      raw <- row.getString(SplitPhaseTypedStartJsonV1.storageField).map(Consequence.success)
        .getOrElse(invalid("owning SplitPhase Start payload is unavailable"))
      start <- SplitPhaseTypedStartJsonV1.decodeRoutingC(SplitPhaseTypedStartPreparation.Reference("split-start:" + sessionId, "1"), raw)
      _ <- if (start.instanceIdentity.value == sessionId) Consequence.unit else invalid("SplitPhase Start crosses its Session")
      _ <- GoalPhaseParticipantAdmission.admitC(start.participantIdentity, core.executionContext)
    } yield ()
    // Bounded work embeds its immutable participant in the actual issued input;
    // admit it before rendering or returning any selected material.
    case _ => Consequence.unit
  }

  private def renderC(r: Reference, storage: WorkflowComponentStorage, current: InstanceRecord,
    core: ActionCall.Core): Consequence[Record] = {
    def load[A](operation: StateMachineOperationIdentity, codec: WorkflowResultJsonV1.PayloadCodec[A])(
      render: WorkflowInteraction[A, Nothing] => Consequence[? <: RecordPresentable]): Consequence[Record] = for {
      _ <- if (r.completionOperation == operation.operation && r.materialType == codec.typeIdentity)
        Consequence.unit else invalid("reference crosses its completion operation or input type")
      owner <- storage.bindIssuedC(operation, codec)
      actual <- owner.loadC(ContinuationIdentity(r.identity))(using core.executionContext)
        .flatMap(_.map(Consequence.success).getOrElse(invalid("selected issued record is unavailable; no latest revision was substituted")))
      _ <- actual.current match {
        case order: WorkflowContinuation.WorkOrder[?] if actual.handle.instanceIdentity.value == r.sessionId &&
            actual.handle.workflowIdentity.value == r.owner && actual.handle.workflowRevision.value == r.workflowRevision &&
            order.request.continuationId.value == r.identity &&
            order.request.expectedRevision.value == r.revision => Consequence.unit
        case _ => invalid("issued record crosses its exact Workflow, Session, identity or revision")
      }
      response <- render(actual)
    } yield response.toRecord()
    def decision[A](operation: StateMachineOperationIdentity, codec: WorkflowResultJsonV1.PayloadCodec[A])(
      render: WorkflowInteraction[A, Nothing] => Consequence[? <: RecordPresentable]): Consequence[Record] = for {
      _ <- if (r.completionOperation == operation.operation && r.materialType == codec.typeIdentity)
        Consequence.unit else invalid("reference crosses its decision operation or input type")
      owner <- storage.bindDecisionC(operation, codec)
      actual <- owner.loadC(ContinuationIdentity(r.identity))(using core.executionContext)
        .flatMap(_.map(Consequence.success).getOrElse(invalid("selected Human decision is unavailable")))
      _ <- actual.current match {
        case selected: WorkflowContinuation.Decision[?] if actual.handle.instanceIdentity.value == r.sessionId &&
            actual.handle.workflowIdentity.value == r.owner && actual.handle.workflowRevision.value == r.workflowRevision &&
            selected.request.continuationId.value == r.identity && selected.request.expectedRevision.value == r.revision => Consequence.unit
        case _ => invalid("Human decision crosses its exact Workflow, Session, identity or revision")
      }
      response <- render(actual)
    } yield response.toRecord()
    r.owner match {
      case "GoalPhaseWorkflow" if r.completionOperation == GoalPhaseOrderedHumanDecision.completionOperation.operation =>
        decision(GoalPhaseOrderedHumanDecision.completionOperation, GoalPhaseOrderedHumanDecision.inputCodec)(NativeWorkflowOperationOutput.goalHumanC(_, core))
      case "GoalPhaseWorkflow" if r.completionOperation == GoalPhaseNativeRun.completionOperation.operation =>
        load(GoalPhaseNativeRun.completionOperation, GoalPhaseNativeWork.inputCodec)(NativeWorkflowOperationOutput.goalWorkRequestC(_, core))
      case "GoalPhaseWorkflow" if r.completionOperation == GoalPhaseNativeFindingRecording.completionOperation.operation =>
        load(GoalPhaseNativeFindingRecording.completionOperation, GoalPhaseFindingRecording.inputCodec)(work =>
          NativeWorkflowOperationOutput.goalRecordingRequestC(work, GoalPhaseNativeFindingRecording.State()))
      case "RepositorySyncWorkflow" => r.completionOperation match {
        case "submitRepositoryConflictDecision" => decision(RepositorySyncHumanConflict.completionOperation, RepositorySyncHumanConflict.inputCodec)(NativeWorkflowOperationOutput.repositoryHumanC(_, core))
        case "submitRepositoryPurposeResult" => load(RepositorySyncPurposeContinuation.completionOperation, RepositorySyncPurpose.inputCodec)(NativeWorkflowOperationOutput.repositoryPurposeRequestC)
        case "submitRepositorySyncWorkResult" => load(RepositorySyncNativeConflictContinuation.completionOperation, RepositorySyncConflictSemantic.inputCodec)(NativeWorkflowOperationOutput.repositoryConflictRequestC)
        case "submitNativeMergeReview" => load(RepositorySyncNativeMergeReviewContinuation.completionOperation, RepositorySyncNativeMergeReview.inputCodec)(NativeWorkflowOperationOutput.repositoryReviewRequestC(_, core))
        case "submitNativeMergeRepair" => load(RepositorySyncNativeMergeRepairContinuation.completionOperation, RepositorySyncNativeMergeRepair.inputCodec)(NativeWorkflowOperationOutput.repositoryRepairRequestC)
        case _ => invalid("unsupported RepositorySync retained work")
      }
      case "SplitPhaseWorkflow" => r.completionOperation match {
        case "submitSplitHumanDirection" if r.materialType == SplitPhaseReasoningHumanDirection.inputCodec.typeIdentity =>
          decision(SplitPhaseReasoningHumanDirection.completionOperation, SplitPhaseReasoningHumanDirection.inputCodec)(NativeWorkflowOperationOutput.splitReasoningHumanC(_, core))
        case "submitSplitHumanDirection" => decision(SplitPhaseNativeHumanDirection.completionOperation, SplitPhaseNativeHumanDirection.inputCodec)(NativeWorkflowOperationOutput.splitScopeHumanC(_, core))
        case "submitPartitionSemanticResult" => load(SplitPhaseExplicitPartitionContext.completionOperation, SplitPhaseExplicitPartitionSemanticJson.requestCodec)(NativeWorkflowOperationOutput.splitPartitionRequestC)
        case "submitHandoffScopeResult" => load(SplitPhaseTypedScopeWorkIssue.completionOperation, SplitPhaseScopeSemanticJson.requestCodec)(NativeWorkflowOperationOutput.splitScopeRequestC)
        case "submitReasoningResult" => load(SplitPhaseTypedReasoningWorkIssue.completionOperation, SplitPhaseExplicitReasoningSemanticJson.requestCodec)(NativeWorkflowOperationOutput.splitReasoningRequestC)
        case _ => invalid("unsupported SplitPhase retained work")
      }
      case "GoalTaskWorkflow" | "DelegateTaskWorkflow" | "StandaloneFixWorkflow" =>
        val engine = if (r.owner == "GoalTaskWorkflow") GoalTaskNativeRun else if (r.owner == "DelegateTaskWorkflow") DelegateTaskNativeRun else StandaloneFixNativeRun
        load(engine.completionOperation, engine.inputCodec) { actual => for {
          input <- NativeWorkflowOperationOutput.workInputC(actual)
          _ <- GoalPhaseParticipantAdmission.admitC(input.state.participant, core.executionContext)
          prefix <- RepositorySyncPurposeContentContinuation.prefixC(current, r.revision)
          response <- engine.requestViewC(prefix, actual, core)
        } yield response }
      case _ => invalid("unsupported retained work kind; legacy full-body records remain readable by their original route")
    }
  }

  def bind: spec.ServiceDefinition = {
    val declared = SmWorkflowComponent.WorkflowRetainedWorkServiceService
    NativeWorkflowDescriptionMetadata.service(declared, spec.OperationDefinitionGroup(NonEmptyVector.of(new spec.OperationDefinition {
      val specification = NativeWorkflowDescriptionMetadata.operation(declared.ReadWorkflowRetainedWorkOperation)
      def createOperationRequest(incoming: Request): Consequence[OperationRequest] = for {
        _ <- if (incoming != null && incoming.service.contains(serviceName) && incoming.operation == readOperation)
          Consequence.unit else invalid("read differs from its owning public operation")
        input <- NativeWorkflowOperationInput.valueC[V.WorkflowRetainedWorkReference](incoming, "input")
        reference <- fromPublicC(input)
      } yield new CommandAction {
        val request = incoming
        override def commandExecutionMode = CommandExecutionMode.Sync
        def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
          val core = c
          def execute(): Consequence[OperationResponse] = readC(reference, core)
            .map(record => NativeWorkflowOperationOutput.response(V.WorkflowRetainedWorkReadResult("available", input, record)))
        }
      }
    }, WorkflowRetainedSnapshot.operation)))
  }
  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("Retained Workflow work " + reason)
}
