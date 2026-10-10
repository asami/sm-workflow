package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{SplitPhaseAdvanceCommand, SplitPhasePartitionContextReference}

/** Public Start/advance context resolution, not a second Workflow engine.
  * Actual calls retain actor, capabilities, UoW and managed storage ownership.
  * @since   Oct. 10, 2026
  * @version Oct. 10, 2026
  */
private[smworkflow] object NativeWorkflowSessionInput {
  val schema = "sm-workflow.session-operation.v1"
  private final case class Invocation(json: Json, session: Option[String], instance: Option[String])
  private type Advance = (InstanceRecord, Json, ActionCall.Core) => Consequence[String]

  def goal(service: spec.ServiceDefinition, creation: ComponentCreate,
    declaration: GoalPhaseNativeRun.Declaration, definition: DefinitionBinding): spec.ServiceDefinition =
    _service(service, creation, declaration.configuration, declaration.instances, declaration.continuations, definition,
      "startGoalPhase", "advanceGoalPhase", (instance, _, _) =>
        Consequence.success(GoalPhaseNativeServiceAssembly.encodeAdvance(instance.identity.value)))

  def repository(service: spec.ServiceDefinition, creation: ComponentCreate,
    declaration: RepositorySyncManagedFetch.Declaration, definition: DefinitionBinding): spec.ServiceDefinition =
    _service(service, creation, declaration.configuration, declaration.instances, declaration.continuations, definition,
      "startRepositorySync", "advanceRepositorySync", (instance, _, _) =>
        Consequence.success(RepositorySyncManagedServiceAssembly.encodeAdvance(instance.identity.value)))

  def split(service: spec.ServiceDefinition, creation: ComponentCreate,
    declaration: SplitPhaseManagedStartAssembly.Declaration, definition: DefinitionBinding): spec.ServiceDefinition =
    _service(service, creation, declaration.configuration, declaration.instances, declaration.continuations, definition,
      "startSplitPhase", "advanceSplitPhase", (instance, json, core) => for {
        port <- SplitPhasePartitionContextReadPolicy.bindC(creation.configuration, declaration)
        contexts <- port.findForCallC(instance.identity, core)
        selector <- _optional_string_c(json, "contextIdentity")
        selected <- selectC(contexts, selector, _.reference.identity, "partition context")
        command = SplitPhaseAdvanceCommand(instance.identity.value,
          SplitPhasePartitionContextReference(selected.reference.identity, selected.reference.revision))
        wire <- SplitPhaseAdvanceCommandJsonV1.encodeC(command)
      } yield wire)

  /** Reuse actual host context when no explicit identifier is needed. An
    * explicit identifier changes only routing context, not actor/authority/UoW.
    * General withSecurityContext creates a rebound UoW and is inappropriate
    * inside an already executing Operation with pending effects.
    */
  def contextC(context: ExecutionContext, explicit: Option[String]): Consequence[ExecutionContext] =
    explicit match {
      case None if context.security.session.flatMap(_.sessionId).nonEmpty => Consequence.success(context)
      case None => Consequence.stateConflict("Workflow operation requires session context")
      case Some(identity) =>
        ExecutionContext.withRoutingSessionIdentity(context, identity)
    }

  def selectC[A](candidates: Vector[A], selector: Option[String], identity: A => String,
    kind: String): Consequence[A] = {
    val selected = selector.fold(candidates)(target => candidates.filter(value => identity(value) == target))
    selected match {
      case Vector(value) => Consequence.success(value)
      case Vector() => Consequence.stateConflict("Workflow session has no associated " + kind)
      case _ => Consequence.stateConflict("Workflow session has ambiguous " + kind + "; supply its target selector")
    }
  }

  private def _service(service: spec.ServiceDefinition, creation: ComponentCreate, configuration: Configuration,
    instances: DataStore.CollectionId, continuations: DataStore.CollectionId, definition: DefinitionBinding,
    start: String, advance: String, resolve: Advance): spec.ServiceDefinition = {
    val operations = service.operations.operations.toVector.map { operation =>
      if (operation.name == start || operation.name == advance)
        new Operation(operation, creation, configuration, instances, continuations, definition,
          operation.name == start, resolve)
      else operation
    }
    NativeWorkflowDescriptionMetadata.service(service,
      spec.OperationDefinitionGroup(NonEmptyVector.fromVectorUnsafe(operations)))
  }

  private final class Operation(original: spec.OperationDefinition, creation: ComponentCreate,
    configuration: Configuration, instances: DataStore.CollectionId, continuations: DataStore.CollectionId,
    definition: DefinitionBinding, start: Boolean, resolve: Advance) extends spec.OperationDefinition {
    val specification = original.specification
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      WorkflowJsonCommandRequest.commandArguments(request) match {
        case Nil if !start => _action_c(request, Json.obj())
        case List(Argument("arg1", raw: String, _)) => io.circe.parser.parse(raw) match {
          case Right(json) if json.hcursor.get[String]("schemaVersion").contains(schema) => _action_c(request, json)
          case _ => original.createOperationRequest(request)
        }
        case _ => original.createOperationRequest(request)
      }

    private def _action_c(request: Request, json: Json): Consequence[OperationRequest] = for {
      session <- _optional_string_c(json, "sessionIdentity")
      instance <- _optional_string_c(json, "instanceIdentity")
    } yield BoundAction(request, Invocation(json, session, instance), original, creation, configuration,
      instances, continuations, definition, start, resolve)
  }

  private def _optional_string_c(json: Json, field: String): Consequence[Option[String]] =
    json.hcursor.get[Option[String]](field).fold(
      _ => Consequence.stateConflict("Workflow " + field + " must be a string when supplied"), Consequence.success)

  private final case class BoundAction(request: Request, invocation: Invocation, original: spec.OperationDefinition,
    creation: ComponentCreate, configuration: Configuration, instances: DataStore.CollectionId,
    continuations: DataStore.CollectionId, definition: DefinitionBinding, start: Boolean, resolve: Advance) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      private var _delegate: Option[ActionCall] = None
      def execute(): Consequence[OperationResponse] = for {
        context <- contextC(executionContext, invocation.session)
        selectedcore = core.copy(executionContext = context)
        wire <- if (start) invocation.json.hcursor.get[Json]("input").fold(
          _ => Consequence.stateConflict("Workflow Start requires its business input"),
          input => Consequence.success(input.asString.getOrElse(input.noSpaces)))
        else for {
          storage <- WorkflowComponentStorage.bindC(creation, SmWorkflowComponent.componentId, configuration,
            Vector(definition), instances, continuations)
          binding <- storage.bindInstancesC()(using context)
          candidates <- binding.findBySessionC(context.security.session.get.sessionId.get,
            definition.workflowIdentity)(using context)
          selected <- selectC(candidates, invocation.instance, _.identity.value, "instance")
          resolved <- resolve(selected, invocation.json, selectedcore)
        } yield resolved
        operation <- original.createOperationRequest(BoundAction.this.request.copy(arguments = List(Argument("arg1", wire)),
          properties = BoundAction.this.request.properties.filter(_.name == "textus.format")))
        result <- operation match {
          case command: CommandAction =>
            val call = command.createCall(selectedcore.copy(action = command))
            _delegate = Some(call)
            call.execute()
          case _ => Consequence.stateConflict("Workflow session target is not a command Operation")
        }
      } yield result
      override def afterCommitResponseC(response: OperationResponse): Consequence[OperationResponse] = {
        val selected = _delegate
        _delegate = None
        selected.map(_.afterCommitResponseC(response))
          .getOrElse(Consequence.stateConflict("Workflow session target was not executed"))
      }
    }
  }
}
