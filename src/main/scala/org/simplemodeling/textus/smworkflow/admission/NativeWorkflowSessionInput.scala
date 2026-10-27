package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.security.SecuritySubject
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.SplitPhasePartitionContextReference

/** One public execution per canonical session. Start reads its actual business
  * entry; Advance addresses its native state and owning context directly.
  * Actor/capabilities/UoW and the shared storage owner are not replaced.
  * @since   Oct. 10, 2026
  * @version Oct. 27, 2026
  */
private[smworkflow] object NativeWorkflowSessionInput {
  private type Advance = InstanceRecord => NativeWorkflowAdvanceInput.Resolved
  private type Selections = ActionCall.Core => Consequence[ProfileSelectionDataStorePort]

  def goal(service: spec.ServiceDefinition, creation: ComponentCreate,
    declaration: GoalPhaseNativeRun.Declaration, definition: DefinitionBinding,
    selectedStart: Option[ProfileStartAdmission.Selection] = None): spec.ServiceDefinition =
    _service(service, creation, declaration.configuration, declaration.instances, declaration.continuations, definition,
      "startGoalPhase", "advanceGoalPhase", instance => NativeWorkflowAdvanceInput.Goal(instance.identity.value),
      NativeWorkflowStartInput.Kind.GoalPhase, _ =>
        GoalPhaseNativeInputReadPolicy.bindC(creation.configuration, declaration).map(_.selections), selectedStart)

  def repository(service: spec.ServiceDefinition, creation: ComponentCreate,
    declaration: RepositorySyncManagedFetch.Declaration, definition: DefinitionBinding,
    runtimeIntent: Boolean = false): spec.ServiceDefinition =
    _service(service, creation, declaration.configuration, declaration.instances, declaration.continuations, definition,
      "startRepositorySync", "advanceRepositorySync", instance => NativeWorkflowAdvanceInput.Repository(instance.identity.value),
      NativeWorkflowStartInput.Kind.RepositorySync, _ =>
        RepositorySyncInputSelectionReadPolicy.bindC(creation.configuration, declaration), runtimeRepositoryIntent = runtimeIntent)

  def split(service: spec.ServiceDefinition, creation: ComponentCreate,
    declaration: SplitPhaseManagedStartAssembly.Declaration, definition: DefinitionBinding): spec.ServiceDefinition =
    _service(service, creation, declaration.configuration, declaration.instances, declaration.continuations, definition,
      "startSplitPhase", "advanceSplitPhase", instance => NativeWorkflowAdvanceInput.Split(
        SplitPhaseAdvanceTarget(instance.identity.value,
          SplitPhasePartitionContextReference("split-context:" + instance.identity.value, "1")), instance),
      NativeWorkflowStartInput.Kind.SplitPhase, _ =>
        SplitPhaseInputSelectionReadPolicy.bindC(creation.configuration, declaration))

  /** Change routing alone inside the owning call, never actor/authority/UoW. */
  def contextC(context: ExecutionContext, explicit: Option[String]): Consequence[ExecutionContext] =
    explicit match {
      case Some(identity) => ExecutionContext.withRoutingSessionIdentity(context, identity)
      case None => Consequence.stateConflict("Workflow requires its canonical sessionId")
    }

  private def _service(service: spec.ServiceDefinition, creation: ComponentCreate, configuration: Configuration,
    instances: DataStore.CollectionId, continuations: DataStore.CollectionId, definition: DefinitionBinding,
    start: String, advance: String, resolve: Advance, kind: NativeWorkflowStartInput.Kind,
    selections: Selections, selectedStart: Option[ProfileStartAdmission.Selection] = None,
    runtimeRepositoryIntent: Boolean = false): spec.ServiceDefinition = {
    val operations = service.operations.operations.toVector.map { operation =>
      if (operation.name == start || operation.name == advance)
        new Operation(operation, creation, configuration, instances, continuations, definition,
          operation.name == start, resolve, kind, selections, selectedStart, runtimeRepositoryIntent)
      else operation
    }
    NativeWorkflowDescriptionMetadata.service(service,
      spec.OperationDefinitionGroup(NonEmptyVector.fromVectorUnsafe(operations)))
  }

  private final class Operation(original: spec.OperationDefinition, creation: ComponentCreate,
    configuration: Configuration, instances: DataStore.CollectionId, continuations: DataStore.CollectionId,
    definition: DefinitionBinding, start: Boolean, resolve: Advance, kind: NativeWorkflowStartInput.Kind,
    selections: Selections, selectedStart: Option[ProfileStartAdmission.Selection], runtimeRepositoryIntent: Boolean) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(original)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || request.operation != original.name || !request.service.contains(kind.operation.service))
        Consequence.stateConflict("Workflow request differs from its owning Operation")
      else {
        val input = if (start) NativeWorkflowOperationInput.recordC(request, "input")
          .flatMap(NativeWorkflowStartInput.fromRecordC(kind, _)).map(Some(_))
        else Consequence.success(Option.empty[NativeWorkflowStartInput.BusinessInput])
        input.map(value => BoundAction(request, value, original, creation, configuration, instances,
          continuations, definition, resolve, kind, selections, selectedStart, runtimeRepositoryIntent))
      }
  }

  private final case class BoundAction(request: Request, input: Option[NativeWorkflowStartInput.BusinessInput],
    original: spec.OperationDefinition, creation: ComponentCreate, configuration: Configuration,
    instances: DataStore.CollectionId, continuations: DataStore.CollectionId, definition: DefinitionBinding,
    resolve: Advance, kind: NativeWorkflowStartInput.Kind, selections: Selections,
    selectedStart: Option[ProfileStartAdmission.Selection], runtimeRepositoryIntent: Boolean) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      private var _delegate: Option[ActionCall] = None
      def execute(): Consequence[OperationResponse] = for {
        session <- if (input.nonEmpty) NativeWorkflowOperationInput.startSessionC(BoundAction.this.request, executionContext)
          else NativeWorkflowOperationInput.textC(BoundAction.this.request, "sessionId")
        context <- contextC(executionContext, Some(session))
        selectedcore = core.copy(executionContext = context)
        operation <- input match {
          case Some(business) =>
            val participant = SecuritySubject.from(context.security).subjectId
            for {
            selected <- (if (runtimeRepositoryIntent)
              StandardRepositorySyncIntent.selectionC(business, session, definition, selectedcore).map(Some(_))
            else selectedStart.map(value => Consequence.success(Some(value))).getOrElse(
              selections(selectedcore).flatMap(_.loadForBusinessC(business, participant, selectedcore)))).flatMap {
              case Some(value) => Consequence.success(value)
              case None => Consequence.stateConflict("Workflow Start has no admitted host business entry")
            }
            _ <- if (runtimeRepositoryIntent) StandardRepositorySyncIntent.admitC(selected, business, session, definition, selectedcore)
              else NativeWorkflowStartInput.admitSelectedC(selected, business, participant, definition)
            action <- original match {
              case receiver: NativeWorkflowStartInput.Receiver =>
                receiver.createStartOperationRequestC(BoundAction.this.request, business, selected)
              case _ => Consequence.configurationInvalid("Workflow Start has no typed business receiver")
            }
          } yield action
          case None => for {
            target <- kind match {
              // These owning Start readers already resolve the exact typed
              // execution. A prior native status/family read is redundant.
              case NativeWorkflowStartInput.Kind.GoalPhase => Consequence.success(NativeWorkflowAdvanceInput.Goal(session))
              case NativeWorkflowStartInput.Kind.RepositorySync => Consequence.success(NativeWorkflowAdvanceInput.Repository(session))
              case NativeWorkflowStartInput.Kind.SplitPhase => for {
                storage <- WorkflowComponentStorage.bindC(creation, SmWorkflowComponent.componentId, configuration,
                  Vector(definition), instances, continuations)
                binding <- storage.bindInstancesC()(using context)
                current <- binding.loadC(InstanceIdentity(session))(using context).flatMap {
                  case Some(value) => Consequence.success(value)
                  case None => Consequence.stateConflict("Workflow session has no native execution")
                }
              } yield resolve(current)
            }
            action <- original match {
              case receiver: NativeWorkflowAdvanceInput.Receiver =>
                receiver.createAdvanceOperationRequestC(BoundAction.this.request, target)
              case _ => Consequence.configurationInvalid("Workflow Advance has no direct native receiver")
            }
          } yield action
        }
        result <- operation match {
          case command: CommandAction =>
            val call = command.createCall(selectedcore.copy(action = command))
            _delegate = Some(call)
            call.execute()
          case _ => Consequence.stateConflict("Workflow target is not a command Operation")
        }
      } yield result
      override def afterCommitResponseC(response: OperationResponse): Consequence[OperationResponse] = {
        val selected = _delegate
        _delegate = None
        selected.map(_.afterCommitResponseC(response))
          .getOrElse(Consequence.stateConflict("Workflow target was not executed"))
      }
    }
  }
}
