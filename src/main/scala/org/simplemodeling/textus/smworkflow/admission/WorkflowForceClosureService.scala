/**
 * @since   Oct. 30, 2026
 * @version Oct. 30, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.Json
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.datastore.{ComponentRecordStorage, DataStore}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.goldenport.configuration.{ConfigurationValue, ResolvedConfiguration}
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.{SmWorkflowComponent, value as V}
import WorkflowForceClosure as C

/** Only the native terminal accepts closure. A create-only, workflow-owned
  * admitted direction is inert until that terminal references it. Collection
  * never re-authorizes the original mutable host input. No completion effect runs.
  */
private[smworkflow] object WorkflowForceClosureService {
  val collectionKey = "sm-workflow.force-closure.record-collection"
  private val declared = SmWorkflowComponent.WorkflowForceClosureServiceService
  private def invalid[A](message: String): Consequence[A] = Consequence.stateConflict(message)
  def directionC(input: V.WorkflowForceClosureAnswer): Consequence[C.Direction] = for {
    target <- C.targetC(input.target)
    direction = C.Direction(target, input.choice, input.reason, input.unfinished.toVector, input.acceptance,
      input.acknowledgeUncertainEffects, input.participant, ContextReference(input.reference.identity, input.reference.revision))
    _ <- C.validateC(direction)
  } yield direction

  def bindC(creation: ComponentCreate, d: GoalPhaseNativeRun.Declaration): Consequence[spec.ServiceDefinition] = for {
    evidence <- NativeExternalEvidence.bindBoundedC(creation.configuration, BoundedWorkRuntime.host(d))
    collection <- collectionC(creation.configuration, d, evidence.declaration.collection)
  } yield NativeWorkflowDescriptionMetadata.service(declared,
    spec.OperationDefinitionGroup(NonEmptyVector.of(new Operation(true, creation, d, evidence, collection),
      new Operation(false, creation, d, evidence, collection))))

  /** Separate application-owned facts, never Workflow infrastructure or mutable
    * external input. Configuration selects a destination; normal host storage
    * admission must independently grant this exact component/collection.
    */
  private def collectionC(host: ResolvedConfiguration, d: GoalPhaseNativeRun.Declaration,
    input: DataStore.CollectionId): Consequence[Option[DataStore.CollectionId]] =
    GoalPhaseInputRecordReadPolicy.storedValue(host.configuration.values, collectionKey) match {
      case None => Consequence.success(None)
      case Some(ConfigurationValue.StringValue(value)) => for {
        id <- EntityCollectionId.parse(value)
        collection = DataStore.CollectionId.EntityStore(id)
        _ <- if (id.print == value && !Vector(d.instances, d.continuations, input).contains(collection)) Consequence.unit
          else Consequence.configurationInvalid("Force closure requires a separate canonical admitted-direction collection")
      } yield Some(collection)
      case _ => Consequence.configurationInvalid("Force closure admitted-direction collection must be an exact Entity collection")
    }

  private def definitionC(family: String): Consequence[DefinitionBinding] = family match {
    case "GoalPhase" => GoalPhaseRuntimeCatalog.loadC().map(_.definition)
    case "GoalTask" => GoalTaskRuntimeCatalog.loadC()
    case "DelegateTask" => DelegateTaskRuntimeCatalog.loadC()
    case _ => invalid("Force closure family must be GoalPhase, GoalTask or DelegateTask")
  }
  private def participantC(family: String, storage: WorkflowComponentStorage, current: InstanceRecord,
    core: ActionCall.Core): Consequence[String] = if (family == "GoalPhase") for {
    starts <- storage.bindMechanicalC(GoalPhaseNativeRun.startOperation, GoalPhaseNativeRun.codec)
    start <- starts.loadRetainedC(GoalPhaseNativeRun.reference(current.identity.value))(using core.executionContext)
      .flatMap(_.map(Consequence.success).getOrElse(invalid("Original Phase Start is absent")))
  } yield start.receipt.payload.value.participantIdentity
  else {
    val runtime = if (family == "DelegateTask") DelegateTaskNativeRun else GoalTaskNativeRun
    current.history.find(_.causation.value.startsWith("standalone-state:")).map { first =>
      runtime.loadPayloadC(storage, current.identity.value, first.revision.value, core).map(_.state.participant)
    }.getOrElse(invalid("Original Task checkpoint is absent"))
  }
  private def originalC(current: InstanceRecord): Consequence[InstanceRecord] =
    current.history.dropRight(1).lastOption.map(entry => GoalPhaseNativeHistory.prefixC(current, entry.revision))
      .getOrElse(invalid("Forced closure has no original native frontier"))
  private def view(target: C.Target, direction: Option[C.Direction]): V.WorkflowForceClosureView =
    V.WorkflowForceClosureView(target.sessionId, target.family, if (direction.nonEmpty) "ForcedClosed" else "Description",
      target.asJson.noSpaces, false, direction.map(_.asJson.noSpaces), C.limitation)

  private def admittedStoreC(core: ActionCall.Core, d: GoalPhaseNativeRun.Declaration, target: C.Target,
    source: InstanceRecord, reference: ContextReference, collection: Option[DataStore.CollectionId]): Consequence[ComponentRecordStorage] =
    collection.map(selected => ComponentRecordStorage.bindForCallC(core, d.configuration.workflowStore.name, selected,
      DataStore.StringEntryId(C.admittedKey(target, source.revision.value, reference))))
      .getOrElse(Consequence.configurationInvalid("Force closure admitted-direction storage is not configured"))
  private def retainedC(store: ComponentRecordStorage): Consequence[Option[C.Admitted]] = store.loadC().flatMap {
    case None => Consequence.success(None)
    case Some(row) => row.getString(C.admittedField).map(C.admittedC(_).map(Some(_)))
      .getOrElse(invalid("Workflow-owned force closure direction has no typed payload"))
  }
  private def admitC(store: ComponentRecordStorage, reader: NativeExternalEvidence.Reader,
    core: ActionCall.Core, direction: C.Direction): Consequence[C.Admitted] = for {
    existing <- retainedC(store)
    result <- existing match {
      case Some(value) if value.direction == direction => Consequence.success(value)
      case Some(_) => invalid("Force closure reference already names a different admitted direction")
      case None => for {
        source <- reader.forceClosureEvidenceC(core, direction.reference)
        actual <- source.binding.hcursor.get[C.Direction]("workflowForceClosure")
          .fold(e => invalid(e.message), Consequence.success)
        _ <- if (actual == direction) Consequence.unit else Consequence.securityPermissionDenied("Exact Human force closure direction is absent")
        admitted = C.Admitted(C.admittedSchema, direction, source.issuer, source.occurredAt,
          core.executionContext.clock.instant().toString)
        // A duplicate/concurrent Create fails safely. No overwrite or readback
        // publication test; this fact alone never establishes a closed Session.
        _ <- store.createC(Record.data(C.admittedField -> admitted.asJson.noSpaces))
      } yield admitted
    }
  } yield result

  private final class Operation(describe: Boolean, creation: ComponentCreate, d: GoalPhaseNativeRun.Declaration,
    evidence: NativeExternalEvidence.Reader, collection: Option[DataStore.CollectionId]) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (describe)
      declared.DescribeWorkflowForceClosureOperation else declared.ForceCloseWorkflowOperation)
    def createOperationRequest(incoming: Request): Consequence[OperationRequest] = {
      val selected: Consequence[Either[V.WorkflowForceClosureDescriptionInput, C.Direction]] = if (describe)
        NativeWorkflowOperationInput.valueC[V.WorkflowForceClosureDescriptionInput](incoming, "input").map(Left(_))
      else NativeWorkflowOperationInput.valueC[V.WorkflowForceClosureAnswer](incoming, "input").flatMap(directionC).map(Right(_))
      selected.map { input => new CommandAction {
        val request = incoming
        override def commandExecutionMode = CommandExecutionMode.Sync
        def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
          val core = c
          def execute(): Consequence[OperationResponse] = {
            val family = input.fold(_.family, _.target.family)
            val session = input.fold(_.sessionId, _.target.sessionId)
            for {
              context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(session))
              call = core.copy(executionContext = context)
              definition <- definitionC(family)
              storage <- WorkflowComponentStorage.bindC(creation, SmWorkflowComponent.componentId, d.configuration,
                Vector(definition), d.instances, d.continuations)
              instances <- storage.bindInstancesC()(using context)
              current <- instances.loadC(InstanceIdentity(session))(using context).flatMap(
                _.map(Consequence.success).getOrElse(invalid("Native force closure Session is absent")))
              _ <- if (current.definition == definition) Consequence.unit else invalid("Force closure crosses the native family")
              participant <- participantC(family, storage, current, call)
              _ <- GoalPhaseParticipantAdmission.admitC(participant, context)
              source <- if (C.isClosed(current)) originalC(current) else Consequence.success(current)
              target <- C.targetC(core.component.get.core.instanceId.canonicalKey, family, participant, source)
              result <- if (C.isClosed(current)) for {
                reference <- C.referenceC(current)
                factStore <- admittedStoreC(call, d, target, source, reference, collection)
                fact <- retainedC(factStore).flatMap(_.map(Consequence.success)
                  .getOrElse(invalid("Native terminal has no retained admitted direction")))
                retained = fact.direction
                _ <- if (retained.target == target && retained.reference == reference && input.fold(_ => true, _ == retained)) Consequence.unit
                  else invalid("Retained forced closure differs from the actual Human direction")
              } yield view(target, Some(retained))
              else input match {
                case Left(_) => Consequence.success(view(target, None))
                case Right(direction) => for {
                  _ <- if (direction.target == target) Consequence.unit else invalid("Force closure description is stale")
                  factStore <- admittedStoreC(call, d, target, source, direction.reference, collection)
                  _ <- admitC(factStore, evidence, call, direction)
                  next <- C.appendC(current, direction, target)
                  _ <- instances.appendC(current.identity, current.revision, next.history.last)(using context)
                } yield view(target, Some(direction))
              }
            } yield NativeWorkflowOperationOutput.response(result)
          }
        }
      } }
    }
  }
}
