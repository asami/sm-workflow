package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{Configuration, DefinitionBinding}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/** Normal result ingress resolves issued control context, not caller echoes.
  * A request identity is needed only to distinguish actual issued generations.
  * Existing completion owns result meaning, state, scope, authority and CAS.
  * Actual executor invocation remains separate work.
  * @since   Oct.  9, 2026
  * @version Oct. 10, 2026
  */
private[smworkflow] object NativeWorkflowResultInput {
  val schema = "sm-workflow.result-submission.v1"
  private final case class Submission(identity: Option[String], session: Option[String], value: Json,
    reference: Option[Json], facts: Json, evidence: Json)
  private final case class Assigned(handle: WorkflowHandle, request: ContinuationRequest[?])
  private final case class Target(operation: StateMachineOperationIdentity,
    contracts: () => Consequence[Vector[String]],
    load: (WorkflowComponentStorage, ContinuationIdentity, org.goldenport.cncf.context.ExecutionContext) => Consequence[Assigned])

  def goal(service: spec.ServiceDefinition, creation: ComponentCreate,
    declaration: GoalPhaseNativeRun.Declaration, definition: DefinitionBinding): spec.ServiceDefinition =
    _service(service, creation, declaration.configuration, declaration.instances, declaration.continuations, definition,
      Vector(_work(GoalPhaseNativeRun.completionOperation, GoalPhaseNativeWork.inputCodec, () =>
        _contracts_c(Vector("ImplementStep", "ReviewStep", "RepairStep", "ReviewPhase").map(kind =>
          () => GoalPhaseNativeWork.requiredC(kind)))),
        _decision(GoalPhaseOrderedHumanDecision.completionOperation, GoalPhaseOrderedHumanDecision.inputCodec,
          () => _contracts_c(Vector(() => GoalPhaseOrderedHumanDecision.requiredC)))))

  def repository(service: spec.ServiceDefinition, creation: ComponentCreate,
    declaration: RepositorySyncManagedFetch.Declaration, definition: DefinitionBinding): spec.ServiceDefinition =
    _service(service, creation, declaration.configuration, declaration.instances, declaration.continuations, definition,
      Vector(_work(RepositorySyncNativeConflictContinuation.completionOperation, RepositorySyncConflictSemantic.inputCodec,
          () => _contracts_c(Vector(() => RepositorySyncDefinitionBinding.conflictOperationC))),
        _work(RepositorySyncNativeMergeReviewContinuation.completionOperation, RepositorySyncNativeMergeReview.inputCodec,
          () => _contracts_c(Vector(() => RepositorySyncDefinitionBinding.nativeReviewOperationC))),
        _decision(RepositorySyncHumanConflict.completionOperation, RepositorySyncHumanConflict.inputCodec,
          () => _contracts_c(Vector(() => RepositorySyncDefinitionBinding.humanConflictOperationC)))))

  def split(service: spec.ServiceDefinition, creation: ComponentCreate,
    declaration: SplitPhaseManagedStartAssembly.Declaration, definition: DefinitionBinding): spec.ServiceDefinition = {
    val humancontracts = () => _contracts_c(Vector(() => SplitPhaseDefinitionBinding.humanDirectionOperationC,
      () => SplitPhaseDefinitionBinding.reasoningHumanDirectionOperationC))
    val human = Target(SplitPhaseNativeHumanDirection.completionOperation, humancontracts, (storage, identity, context) =>
      if (SplitPhaseManagedReasoningHumanCompletion.isReasoning(identity))
        _decision(SplitPhaseNativeHumanDirection.completionOperation, SplitPhaseReasoningHumanDirection.inputCodec, humancontracts)
          .load(storage, identity, context)
      else _decision(SplitPhaseNativeHumanDirection.completionOperation, SplitPhaseNativeHumanDirection.inputCodec, humancontracts)
        .load(storage, identity, context))
    _service(service, creation, declaration.configuration, declaration.instances, declaration.continuations, definition,
      Vector(_work(SplitPhaseExplicitPartitionContext.completionOperation, SplitPhaseExplicitPartitionSemanticJson.requestCodec,
          () => _contracts_c(Vector(() => SplitPhaseDefinitionBinding.partitionSemanticOperationC))),
        _work(SplitPhaseTypedScopeWorkIssue.completionOperation, SplitPhaseScopeSemanticJson.requestCodec,
          () => _contracts_c(Vector(() => SplitPhaseDefinitionBinding.scopeSemanticOperationC))),
        _work(SplitPhaseTypedReasoningWorkIssue.completionOperation, SplitPhaseExplicitReasoningSemanticJson.requestCodec,
          () => _contracts_c(Vector(() => SplitPhaseDefinitionBinding.reasoningSemanticOperationC))), human))
  }

  private def _contracts_c(required: Vector[() => Consequence[StateMachineRequiredOperation]]): Consequence[Vector[String]] =
    required.foldLeft(Consequence.success(Vector.empty[String])) { (acc, operation) =>
      for {
        prior <- acc
        current <- operation()
      } yield prior :+ current.metadata.completionContract.identity
    }

  private def _work[W](operation: StateMachineOperationIdentity,
    codec: WorkflowResultJsonV1.PayloadCodec[W], contracts: () => Consequence[Vector[String]]): Target =
      Target(operation, contracts, (storage, identity, context) =>
      storage.bindIssuedC(operation, codec).flatMap(_.loadC(identity)(using context)).flatMap {
        case Some(value) => value.current match {
          case order: WorkflowContinuation.WorkOrder[?] => Consequence.success(Assigned(value.handle, order.request))
          case _ => _invalid("result target is not issued semantic work")
        }
        case None => _invalid("result target has no actual issued work")
      })

  private def _decision[W](operation: StateMachineOperationIdentity,
    codec: WorkflowResultJsonV1.PayloadCodec[W], contracts: () => Consequence[Vector[String]]): Target =
      Target(operation, contracts, (storage, identity, context) =>
      storage.bindDecisionC(operation, codec).flatMap(_.loadC(identity)(using context)).flatMap {
        case Some(value) => value.current match {
          case question: WorkflowContinuation.Decision[?] => Consequence.success(Assigned(value.handle, question.request))
          case _ => _invalid("result target is not an issued Human question")
        }
        case None => _invalid("result target has no actual issued question")
      })

  private def _service(service: spec.ServiceDefinition, creation: ComponentCreate, configuration: Configuration,
    instances: DataStore.CollectionId, continuations: DataStore.CollectionId, definition: DefinitionBinding,
    targets: Vector[Target]): spec.ServiceDefinition = {
    val operations = service.operations.operations.toVector.map(operation =>
      targets.find(_.operation.operation == operation.name).fold(operation)(target =>
        new Operation(operation, creation, configuration, instances, continuations, definition, target)))
    NativeWorkflowDescriptionMetadata.service(service,
      spec.OperationDefinitionGroup(NonEmptyVector.fromVectorUnsafe(operations)))
  }

  private final class Operation(original: spec.OperationDefinition, creation: ComponentCreate, configuration: Configuration,
    instances: DataStore.CollectionId, continuations: DataStore.CollectionId, definition: DefinitionBinding,
    target: Target) extends spec.OperationDefinition {
    val specification = original.specification
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      WorkflowJsonCommandRequest.commandArguments(request) match {
        case List(Argument("arg1", raw: String, _)) => io.circe.parser.parse(raw) match {
          case Right(json) if json.hcursor.get[String]("schemaVersion").contains(schema) =>
            _submission_c(json).map(value => BoundAction(request, value, original, creation, configuration,
              instances, continuations, definition, target))
          case _ => original.createOperationRequest(request) // Historical complete-envelope input.
        }
        case _ => original.createOperationRequest(request)
      }
  }

  private def _submission_c(json: Json): Consequence[Submission] = {
    val decoded = for {
      identity <- json.hcursor.get[Option[String]]("requestIdentity").left.map(_.message)
      session <- json.hcursor.get[Option[String]]("sessionIdentity").left.map(_.message)
      result <- json.hcursor.get[Json]("result").left.map(_.message)
      value <- result.hcursor.get[Json]("value").left.map(_.message)
    } yield Submission(identity, session, value, result.hcursor.downField("reference").focus,
      json.hcursor.downField("completionFacts").focus.getOrElse(Json.arr()),
      json.hcursor.downField("executionEvidence").focus.getOrElse(Json.obj(
        "references" -> Json.arr(), "workerIdentity" -> Json.Null,
        "modelIdentity" -> Json.Null, "skillDispatch" -> Json.Null)))
    decoded.fold(_invalid(_), Consequence.success)
  }

  private final case class BoundAction(request: Request, submission: Submission, original: spec.OperationDefinition,
    creation: ComponentCreate, configuration: Configuration, instances: DataStore.CollectionId,
    continuations: DataStore.CollectionId, definition: DefinitionBinding, target: Target) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      private var _delegate: Option[ActionCall] = None
      def execute(): Consequence[OperationResponse] =
        for {
          context <- if (submission.session.nonEmpty || submission.identity.isEmpty ||
              executionContext.security.session.flatMap(_.sessionId).nonEmpty)
            NativeWorkflowSessionInput.contextC(executionContext, submission.session)
          else Consequence.success(executionContext) // Supported old identity-only input.
          storage <- WorkflowComponentStorage.bindC(creation, SmWorkflowComponent.componentId, configuration,
            Vector(definition), instances, continuations)
          identity <- _identity_c(storage, definition, target, submission, context)
          assigned <- target.load(storage, identity, context)
          wire <- _expand_c(assigned, submission)
          adapted = BoundAction.this.request.copy(arguments = List(Argument("arg1", wire)),
            properties = BoundAction.this.request.properties.filter(_.name == "textus.format"))
          operation <- original.createOperationRequest(adapted)
          result <- operation match {
            case command: CommandAction =>
              val call = command.createCall(core.copy(action = command, executionContext = context))
              _delegate = Some(call)
              call.execute()
            case _ => _invalid("result completion is not a command Operation")
          }
        } yield result
      override def afterCommitResponseC(response: OperationResponse): Consequence[OperationResponse] = {
        val selected = _delegate
        _delegate = None
        selected.map(_.afterCommitResponseC(response)).getOrElse(_invalid("result completion was not executed"))
      }
    }
  }

  private def _identity_c(storage: WorkflowComponentStorage, definition: DefinitionBinding, target: Target,
    submission: Submission, context: ExecutionContext): Consequence[ContinuationIdentity] =
    context.security.session.flatMap(_.sessionId) match {
      case None => submission.identity.map(value => Consequence.success(ContinuationIdentity(value)))
        .getOrElse(_invalid("result has no session or selected issued request"))
      case Some(session) => for {
        binding <- storage.bindInstancesC()(using context)
        instances <- binding.findBySessionC(session, definition.workflowIdentity)(using context)
        contracts <- target.contracts()
        // Completed generations remain candidates: a delayed answer may refer
        // to one of them even if a newer request is the sole current pending.
        identities = instances.flatMap(_.history.flatMap(_.suspension.toVector))
          .filter(boundary => contracts.contains(boundary.completion.value))
          .map(_.continuationIdentity.value).distinct
        selected <- NativeWorkflowSessionInput.selectC(identities, submission.identity, identity => identity, "issued request")
      } yield ContinuationIdentity(selected)
    }

  private def _expand_c(assigned: Assigned, submission: Submission): Consequence[String] = {
    val request = assigned.request
    request.resultType.map(_.value).map { resulttype =>
      val codec = new WorkflowResultJsonV1.PayloadCodec[Json] {
        val typeIdentity = resulttype
        def encode(value: Json) = value
        def decode(value: Json) = Right(value)
      }
      _result_reference_c(request, submission).flatMap { reference =>
        val result = ContinuationResult(assigned.handle, request.runId, request.continuationId,
          request.expectedRevision, request.context.snapshot, TypedValue(resulttype, submission.value),
          reference, Vector.empty, ExecutionEvidence(Vector.empty))
        WorkflowResultJsonV1.encodeC(result, codec).flatMap(wire => io.circe.parser.parse(wire)
          .fold(_ => _invalid("issued result projection is invalid"), json => Consequence.success(json.deepMerge(Json.obj(
            "completionFacts" -> submission.facts, "executionEvidence" -> submission.evidence)).noSpaces)))
      }
    }.getOrElse(_invalid("issued result has no declared result type"))
  }

  /** The actual stored assignment owns these three result destinations. An old
    * echoed reference is unused input, not a selector or an equality certificate.
    * Other completions consume a caller's real result/artifact/evidence locator.
    */
  private def _result_reference_c(request: ContinuationRequest[?], submission: Submission): Consequence[ContextReference] =
    request.input.map(_.value) match {
      case Some(value: SplitPhaseExplicitPartitionSemantic.Request) =>
        Consequence.success(ContextReference(value.reply.result.identity, value.reply.result.revision))
      case Some(value: SplitPhaseScopeSemantic.Request) =>
        Consequence.success(ContextReference(value.result.identity, value.result.revision))
      case Some(value: SplitPhaseExplicitReasoningSemantic.Request) =>
        Consequence.success(ContextReference(value.result.identity, value.result.revision))
      case _ => submission.reference match {
        case Some(reference) =>
          val decoded = for {
            identity <- reference.hcursor.get[String]("identity").left.map(_.message)
            revision <- reference.hcursor.get[String]("revision").left.map(_.message)
          } yield ContextReference(identity, revision)
          decoded.fold(_invalid(_), Consequence.success)
        case None => _invalid("completion requires its actual result or evidence reference")
      }
    }

  private def _invalid[A](message: String): Consequence[A] = Consequence.stateConflict(message)
}
