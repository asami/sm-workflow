package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import scala.reflect.ClassTag
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{Configuration, DefinitionBinding, InstanceIdentity}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V

/** Named business results address one execution's current assignment directly.
  * Native completion consumes the typed result and owns its state, scope,
  * participant and update boundary. Public callers supply no control envelope.
  * @since   Oct.  9, 2026
  *  version Oct. 15, 2026
  * @version Oct. 16, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object NativeWorkflowResultInput {
  type Value = GoalPhaseNativeWork.Result | GoalPhaseOrderedHumanDecision.Answer |
    SplitPhaseExplicitPartitionSemantic.Judgment | SplitPhaseScopeSemantic.Judgment |
    SplitPhaseExplicitReasoningSemantic.Judgment | SplitPhaseHumanDirectionAnswerContract.Answer |
    RepositorySyncConflictSemantic.Result | RepositorySyncNativeMergeReview.Result |
    RepositorySyncHumanConflict.Answer | RepositorySyncNativeMergeRepair.Result


  trait Receiver extends spec.OperationDefinition {
    final def createOperationRequest(request: Request): Consequence[OperationRequest] =
      _invalid("completion requires its resolved result input")
    def createResultOperationRequestC(request: Request,
      result: ContinuationResult[Value]): Consequence[OperationRequest]
  }

  private trait ResolvedAction extends CommandAction {
    def resolvedResult: ContinuationResult[Value]
  }
  def resolvedC(core: ActionCall.Core): Consequence[ContinuationResult[Value]] = core.action match {
    case action: ResolvedAction => Consequence.success(action.resolvedResult)
    case _ => _invalid("completion query has no resolved result input")
  }

  // The concrete completion consumes its business type without serialization
  // or a second JSON decoder. This is the private heterogeneous callback bridge.
  def typedC[A: ClassTag](result: ContinuationResult[Value], codec: WorkflowResultJsonV1.PayloadCodec[A]):
      Consequence[ContinuationResult[A]] =
    summon[ClassTag[A]].unapply(result.result.value) match {
      case Some(value) => Consequence.success(ContinuationResult(result.handle, result.runId, result.continuationId,
        result.expectedRevision, result.contextSnapshot, TypedValue(result.result.typeIdentity, value),
        result.resultReference, result.completionFacts, result.evidence))
      case None => _invalid("completion requires business value " + codec.typeIdentity)
    }

  private def _resolved_action(command: CommandAction, result: ContinuationResult[Value]): CommandAction =
    new ResolvedAction {
      val request = command.request
      val resolvedResult = result
      override def commandExecutionMode = command.commandExecutionMode
      def createCall(core: ActionCall.Core): ActionCall = command.createCall(core)
    }
  private final case class Submission(session: String, value: Value,
    reference: Option[ContextReference], facts: Vector[ContextReference], evidence: ExecutionEvidence)
  private final case class Assigned(handle: WorkflowHandle, request: ContinuationRequest[?])
  private final case class Target(operation: StateMachineOperationIdentity,
    readC: Request => Consequence[Value],
    load: (WorkflowComponentStorage, ContinuationIdentity, ExecutionContext) => Consequence[Assigned])

  def goal(service: spec.ServiceDefinition, creation: ComponentCreate,
    declaration: GoalPhaseNativeRun.Declaration, definition: DefinitionBinding): spec.ServiceDefinition =
    _service(service, creation, declaration.configuration, declaration.instances, declaration.continuations, definition,
      Vector(
        _work(GoalPhaseNativeRun.completionOperation, GoalPhaseNativeWork.inputCodec, request =>
          NativeWorkflowOperationInput.valueC[V.GoalPhaseWorkResult](request, "result").map[Value](value =>
            GoalPhaseNativeWork.Result(value.outcome, value.summary, value.findings,
              GoalPhaseAcceptanceBoundary.internalReport(value.acceptance)))),
        _decision(GoalPhaseOrderedHumanDecision.completionOperation, GoalPhaseOrderedHumanDecision.inputCodec, request =>
          NativeWorkflowOperationInput.valueC[V.GoalPhaseOrderedDecisionAnswer](request, "answer").map[Value](value =>
            GoalPhaseOrderedHumanDecision.Answer(value.choice, value.rationale)))))

  def repository(service: spec.ServiceDefinition, creation: ComponentCreate,
    declaration: RepositorySyncManagedFetch.Declaration, definition: DefinitionBinding): spec.ServiceDefinition =
    _service(service, creation, declaration.configuration, declaration.instances, declaration.continuations, definition,
      Vector(
        _work(RepositorySyncNativeConflictContinuation.completionOperation, RepositorySyncConflictSemantic.inputCodec, request =>
          NativeWorkflowOperationInput.valueC[V.RepositorySyncConflictResult](request, "result").map[Value](value =>
            RepositorySyncConflictSemantic.Result(value.decision, value.resolutions.map(_resolution), value.rationale))),
        _work(RepositorySyncNativeMergeReviewContinuation.completionOperation, RepositorySyncNativeMergeReview.inputCodec, request =>
          NativeWorkflowOperationInput.valueC[V.RepositorySyncNativeMergeReviewResult](request, "result").map[Value](value =>
            RepositorySyncNativeMergeReview.Result(value.decision, value.rationale, value.findings.map(f =>
              RepositorySyncNativeMergeReview.Finding(f.identity, f.affectedPaths, f.rationale))))),
        _work(RepositorySyncNativeMergeRepairContinuation.completionOperation, RepositorySyncNativeMergeRepair.inputCodec, request =>
          NativeWorkflowOperationInput.valueC[V.RepositorySyncNativeMergeRepairResult](request, "result").map[Value](value =>
            RepositorySyncNativeMergeRepair.Result(value.decision, value.rationale, value.replacements.map(file =>
              RepositorySyncNativeMergeRepair.File(file.path, file.text))))),
        _decision(RepositorySyncHumanConflict.completionOperation, RepositorySyncHumanConflict.inputCodec, request =>
          NativeWorkflowOperationInput.valueC[V.RepositorySyncHumanConflictAnswer](request, "answer").map[Value](value =>
            RepositorySyncHumanConflict.Answer(value.choice, value.resolutions.map(_resolution), value.rationale)))))

  def split(service: spec.ServiceDefinition, creation: ComponentCreate,
    declaration: SplitPhaseManagedStartAssembly.Declaration, definition: DefinitionBinding): spec.ServiceDefinition = {
    val human = Target(SplitPhaseNativeHumanDirection.completionOperation, _human_c, (storage, identity, context) =>
      if (SplitPhaseManagedReasoningHumanCompletion.isReasoning(identity))
        _decision(SplitPhaseNativeHumanDirection.completionOperation, SplitPhaseReasoningHumanDirection.inputCodec, _human_c)
          .load(storage, identity, context)
      else _decision(SplitPhaseNativeHumanDirection.completionOperation, SplitPhaseNativeHumanDirection.inputCodec, _human_c)
        .load(storage, identity, context))
    _service(service, creation, declaration.configuration, declaration.instances, declaration.continuations, definition,
      Vector(
        _work(SplitPhaseExplicitPartitionContext.completionOperation, SplitPhaseExplicitPartitionSemanticJson.requestCodec, _partition_c),
        _work(SplitPhaseTypedScopeWorkIssue.completionOperation, SplitPhaseScopeSemanticJson.requestCodec, _scope_c),
        _work(SplitPhaseTypedReasoningWorkIssue.completionOperation, SplitPhaseExplicitReasoningSemanticJson.requestCodec, _reasoning_c),
        human))
  }

  private def _work[W](operation: StateMachineOperationIdentity,
    codec: WorkflowResultJsonV1.PayloadCodec[W], read: Request => Consequence[Value]): Target =
    Target(operation, read, (storage, identity, context) =>
      storage.bindIssuedC(operation, codec).flatMap(_.loadC(identity)(using context)).flatMap {
        case Some(value) => value.current match {
          case order: WorkflowContinuation.WorkOrder[?] => Consequence.success(Assigned(value.handle, order.request))
          case _ => _invalid("result target is not issued semantic work")
        }
        case None => _invalid("result target has no actual issued work")
      })

  private def _decision[W](operation: StateMachineOperationIdentity,
    codec: WorkflowResultJsonV1.PayloadCodec[W], read: Request => Consequence[Value]): Target =
    Target(operation, read, (storage, identity, context) =>
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
    val specification = NativeWorkflowDescriptionMetadata.operation(original)
    def createOperationRequest(request: Request): Consequence[OperationRequest] = for {
      session <- NativeWorkflowOperationInput.textC(request, "sessionId")
      value <- target.readC(request)
      reference <- NativeWorkflowOperationInput.optionalValueC[V.WorkflowArtifactReference](request, "resultReference")
      facts <- NativeWorkflowOperationInput.vectorC[V.WorkflowArtifactReference](request, "completionFacts")
      evidence <- NativeWorkflowOperationInput.optionalValueC[V.WorkflowExecutionEvidence](request, "executionEvidence")
        .flatMap(_evidence_c)
    } yield BoundAction(request, Submission(session, value, reference.map(_reference), facts.map(_reference), evidence),
      original, creation, configuration, instances, continuations, definition, target)
  }

  private final case class BoundAction(request: Request, submission: Submission, original: spec.OperationDefinition,
    creation: ComponentCreate, configuration: Configuration, instances: DataStore.CollectionId,
    continuations: DataStore.CollectionId, definition: DefinitionBinding, target: Target) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      private var _delegate: Option[ActionCall] = None
      def execute(): Consequence[OperationResponse] = for {
        context <- NativeWorkflowSessionInput.contextC(executionContext, Some(submission.session))
        storage <- WorkflowComponentStorage.bindC(creation, SmWorkflowComponent.componentId, configuration,
          Vector(definition), instances, continuations)
        binding <- storage.bindInstancesC()(using context)
        current <- binding.loadC(InstanceIdentity(submission.session))(using context).flatMap {
          case Some(value) => Consequence.success(value)
          case None => _invalid("result session has no native execution")
        }
        identity <- current.suspension.map(value => Consequence.success(
          ContinuationIdentity(value.continuationIdentity.value)))
          .getOrElse(_invalid("result session has no pending assignment"))
        assigned <- target.load(storage, identity, context)
        submitted <- _result_c(assigned, submission)
        operation <- original match {
          case receiver: Receiver => receiver.createResultOperationRequestC(BoundAction.this.request, submitted)
          case _ => _invalid("completion has no resolved result receiver")
        }
        result <- operation match {
          case command: CommandAction =>
            val action = _resolved_action(command, submitted)
            val call = action.createCall(core.copy(action = action, executionContext = context))
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

  private def _result_c(assigned: Assigned, submission: Submission): Consequence[ContinuationResult[Value]] = {
    val request = assigned.request
    request.resultType.map(_.value).map { resulttype =>
      _result_reference_c(request, submission.reference).map(reference =>
        ContinuationResult(assigned.handle, request.runId, request.continuationId,
          request.expectedRevision, request.context.snapshot, TypedValue(resulttype, submission.value),
          reference, submission.facts, submission.evidence))
    }.getOrElse(_invalid("issued result has no declared result type"))
  }

  private def _result_reference_c(request: ContinuationRequest[?],
    supplied: Option[ContextReference]): Consequence[ContextReference] = request.input.map(_.value) match {
    case Some(value: SplitPhaseExplicitPartitionSemantic.Request) =>
      Consequence.success(ContextReference(value.reply.result.identity, value.reply.result.revision))
    case Some(value: SplitPhaseScopeSemantic.Request) =>
      Consequence.success(ContextReference(value.result.identity, value.result.revision))
    case Some(value: SplitPhaseExplicitReasoningSemantic.Request) =>
      Consequence.success(ContextReference(value.result.identity, value.result.revision))
    case _ => supplied.map(Consequence.success).getOrElse(
      _invalid("completion requires its actual result or evidence reference"))
  }

  private def _reference(value: V.WorkflowArtifactReference): ContextReference =
    ContextReference(value.identity, value.revision)

  private def _evidence_c(value: Option[V.WorkflowExecutionEvidence]): Consequence[ExecutionEvidence] = value match {
    case None => Consequence.success(ExecutionEvidence(Vector.empty, None, None))
    case Some(value) =>
      val observation = value.execution match {
        case None => Consequence.success(Option.empty[ExecutionObservation])
        case Some(actual) => ExecutionPlacement.values.find(_.value == actual.placement)
          .map(placement => Consequence.success(Some(ExecutionObservation(placement,
            actual.originContextIdentity, actual.executorContextIdentity, actual.profileIdentity))))
          .getOrElse(_invalid("unsupported execution placement"))
      }
      observation.map(execution => ExecutionEvidence(value.references.map(_reference),
        value.workerIdentity, value.modelIdentity, execution = execution))
  }

  private def _resolution(value: V.RepositorySyncConflictResolution): RepositorySyncConflictSemantic.Resolution =
    RepositorySyncConflictSemantic.Resolution(value.conflictIdentity, value.text)

  private def _enum_c[A](values: Array[A], value: String): Consequence[A] =
    values.find(_.toString == value).map(Consequence.success)
      .getOrElse(_invalid("unknown business alternative " + value))

  private def _partition_c(request: Request): Consequence[Value] = {
    import SplitPhaseExplicitPartitionSemantic as S
    import SplitPhaseExplicitProposalPrecheck as P
    for {
      value <- NativeWorkflowOperationInput.valueC[V.SplitPhasePartitionSemanticResult](request, "result")
      decision <- _enum_c(S.Decision.values, value.decision)
      boundaries <- value.boundaries.foldLeft(Consequence.success(Vector.empty[S.BoundaryJudgment])) { (prior, boundary) =>
        for {
          accumulated <- prior
          disposition <- _enum_c(SplitPhasePartitionEnumeration.EdgeDisposition.values, boundary.disposition)
        } yield accumulated :+ S.BoundaryJudgment(P.WorkItemIdentity(boundary.afterItem.value), disposition, boundary.rationale)
      }
    } yield S.Judgment(decision, value.rationale, value.estimates.map(e =>
      S.NovelEstimate(P.WorkItemIdentity(e.identity.value), e.minimumMinutes, e.maximumMinutes,
        e.assumptions, e.noveltyBasis)), boundaries, value.evidence.map(e => S.EvidenceReference(e.identity, e.revision)))
  }

  private def _scope_c(request: Request): Consequence[Value] = {
    import SplitPhaseScopeSemantic as S
    import SplitPhaseExplicitProposalPrecheck as P
    for {
      value <- NativeWorkflowOperationInput.valueC[V.SplitPhaseScopeSemanticResult](request, "result")
      decision <- _enum_c(S.Decision.values, value.decision)
    } yield S.Judgment(decision, value.rationale, value.scopes.map(scope =>
      S.Scope(SplitPhaseExplicitBoundaryValidation.HandoffIdentity(scope.handoff.value),
        scope.producingItems.toVector.map(i => P.WorkItemIdentity(i.value)),
        scope.receivingItems.toVector.map(i => P.WorkItemIdentity(i.value)), scope.rationale)),
      value.evidence.toVector.map(e => SplitPhaseExplicitPartitionSemantic.EvidenceReference(e.identity, e.revision)))
  }

  private def _reasoning_c(request: Request): Consequence[Value] = {
    import SplitPhaseExplicitReasoningSemantic as S
    import SplitPhaseExplicitProposalPrecheck as P
    for {
      value <- NativeWorkflowOperationInput.valueC[V.SplitPhaseReasoningSemanticResult](request, "result")
      decision <- _enum_c(S.Decision.values, value.decision)
      assignments <- value.assignments.foldLeft(Consequence.success(Vector.empty[S.Assignment])) { (prior, item) =>
        for {
          accumulated <- prior
          classification <- _enum_c(S.Classification.values, item.classification)
        } yield accumulated :+ S.Assignment(P.WorkItemIdentity(item.identity.value), classification, item.rationale)
      }
    } yield S.Judgment(decision, value.rationale, assignments,
      value.evidence.map(e => SplitPhaseExplicitPartitionSemantic.EvidenceReference(e.identity, e.revision)))
  }

  private def _human_c(request: Request): Consequence[Value] =
    NativeWorkflowOperationInput.valueC[V.SplitPhaseHumanDirectionAnswer](request, "answer").flatMap { value =>
      import SplitPhaseHumanDirectionAnswerContract.Answer
      value.choice.value match {
        case "ProvideResolution" => value.resolution.map(reference => Consequence.success[Value](
          Answer.ProvideResolution(SplitPhaseExplicitPartitionSemantic.ContextReference(reference.identity, reference.revision))))
          .getOrElse(_invalid("Human ProvideResolution requires its resolution reference"))
        case "Defer" => Consequence.success(Answer.Defer)
        case "Cancel" => Consequence.success(Answer.Cancel)
        case _ => _invalid("Human answer is not a declared choice")
      }
    }

  private def _invalid[A](message: String): Consequence[A] = Consequence.stateConflict(message)
}
