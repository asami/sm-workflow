package org.simplemodeling.textus.smworkflow.admission

import cats.syntax.all.*
import io.circe.{Json, parser}
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.{ActionCall, CommandAction}
import org.goldenport.cncf.datastore.{ComponentRecordStorage, DataStore, DataStoreConditionalTransitionFailure}
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{CausationReference, HistoryEntry, HistorySequence, InstanceRecord, Lifecycle, ProgressionReference}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseReasoningHumanDirection as H,
  SplitPhaseHumanDirectionAnswerContract as A, SplitPhaseHumanAnswerVerification as V,
  SplitPhaseHumanReasoningResolution as R, SplitPhasePlanningSnapshot as S,
  SplitPhasePlanningSnapshotJsonV1 as J, SplitPhasePlanningCheckpointPublication as U}

/** Same public answer Operation, selected by its actual persisted question.
  * Canonical answer/native state and the complete deterministic successor are
  * one managed UoW; no decoded question/result is execution authority.
  * @since Oct. 7, 2026
  * @version Oct. 12, 2026
  */
private[smworkflow] object SplitPhaseManagedReasoningHumanCompletion {
  final class Observed private[SplitPhaseManagedReasoningHumanCompletion] (
    val plan: H.Plan, val completion: WorkflowCompletionRecordJsonV1.Record[A.Answer],
    val resolution: Option[R.Record], val downstream: Option[S.Record],
    val next: InstanceRecord, val current: InstanceRecord)
  private val _field = "splitPhaseReasoningHumanSuccessorPayload"
  private val _schema = "sm-workflow.reasoning-human-successor.v1"

  def isReasoning(identity: ContinuationIdentity): Boolean =
    identity != null && identity.value.startsWith("split-reasoning-human:")

  def executeForCallC(submitted: ContinuationResult[A.Answer],
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    evidence: V.EvidencePort, core: ActionCall.Core, observation: () => Consequence[Unit],
    delegate: ActionCall => Unit): Consequence[OperationResponse] = for {
    _ <- SplitPhaseReasoningInvocation.actorC(core, None)
    issued <- _issued_c(submitted.continuationId, declaration, catalog, core)
    command <- H.commandC(issued)
    plan <- _plan_c(command, declaration, catalog, core, Some(issued))
    storage <- H.storageC(declaration, catalog, core)
    native <- _native_c(storage)
    response <- for {
        resolution <- submitted.result.value match {
          case A.Answer.ProvideResolution(reference) => R.loadForCallC(reference, declaration, core).map(Some(_))
          case _ => Consequence.success(None)
        }
        calculated <- _calculate_c(plan, submitted, resolution)
        _ <- V.admitForCallC(V.AnswerContext(core.component.get.core.instanceId, plan.question, submitted,
          plan.source.source.checkpoint.originalStart.participantIdentity), core, evidence).map(_ => ())
        operation <- SplitPhaseDefinitionBinding.providedDefinition.providedOperations.find(o =>
          o.service == H.completionOperation.service && o.name == H.completionOperation.operation)
          .map(Consequence.success).getOrElse(_invalid("Provided answer Operation is absent"))
        bound <- _fresh_c(plan, submitted, resolution, calculated._1, calculated._2,
          operation, declaration, catalog, storage, native, core, observation)
        action <- bound.createOperationRequest(core.action.request).flatMap {
          case value: CommandAction => Consequence.success(value)
          case _ => _invalid("canonical answer declaration is not a command")
        }
        call = action.createCall(core.copy(action = action))
        response <- { delegate(call); call.execute() }
      } yield response
  } yield response

  /** Read query from the actual issued reasoning question. Never substitutes
    * an advance Core, a request-decoded source or an arbitrary locator.
    */
  def completionQueryC(identity: ContinuationIdentity,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    core: ActionCall.Core): Consequence[SplitPhaseAdvanceTarget] = for {
    question <- _issued_c(identity, declaration, catalog, core)
    command <- H.commandC(question)
  } yield command

  private def _issued_c(identity: ContinuationIdentity,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    core: ActionCall.Core): Consequence[WorkflowInteraction[H.Input, Nothing]] = for {
    _ <- if (core.action.request.service.contains(H.completionOperation.service) &&
        core.action.request.operation == H.completionOperation.operation && isReasoning(identity))
      Consequence.unit else _invalid("query does not belong to reasoning Human completion")
    storage <- H.storageC(declaration, catalog, core)
    questions <- storage.bindDecisionC(H.completionOperation, H.inputCodec)
    question <- questions.loadC(identity)(using core.executionContext)
      .flatMap(_.map(Consequence.success).getOrElse(_invalid("actual issued reasoning Human question is absent")))
  } yield question

  def observeForAdvanceC(command: SplitPhaseAdvanceTarget, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, core: ActionCall.Core): Consequence[Observed] = for {
    plan <- _plan_c(command, declaration, catalog, core)
    storage <- H.storageC(declaration, catalog, core)
    native <- _native_c(storage)
    identity = plan.question.current.asInstanceOf[WorkflowContinuation.Decision[H.Input]].request.continuationId
    completion <- native.loadRetainedC(identity)(using core.executionContext)
      .flatMap(_.map(v => Consequence.success(v.completion)).getOrElse(_invalid("canonical Human answer is absent")))
    observed <- _observe_c(plan, completion, completion.result, declaration, core)
  } yield observed

  def terminalC(observed: Observed): Consequence[String] = for {
    outcome <- observed.completion.result.result.value match {
      case A.Answer.Defer => Consequence.success(SplitPhaseHumanTerminal.Outcome.Blocked)
      case A.Answer.Cancel => Consequence.success(SplitPhaseHumanTerminal.Outcome.Cancelled)
      case _ => _invalid("resolved interpretation is not a stopped terminal")
    }
    answer = observed.completion.result
    result = SplitPhaseHumanTerminal.Result(outcome, observed.plan.source.request.context,
      answer.continuationId, answer.resultReference)
    handle <- WorkflowHandle.fromRecordC(answer.handle.componentIdentity, observed.current)
    terminal <- WorkflowProtocolV1.projectTerminalC(handle, observed.current,
      TypedValue(SplitPhaseHumanTerminal.payloadCodec.typeIdentity, result),
      MinimalPresentation("SplitPhase " + outcome.toString, "The recorded Human direction ended the reasoning split run.",
        reason = Some(outcome.toString)))
    wire <- WorkflowTerminalJsonV1.encodeC(terminal, SplitPhaseHumanTerminal.payloadCodec)
  } yield wire

  private def _fresh_c(plan: H.Plan, submitted: ContinuationResult[A.Answer], resolution: Option[R.Record],
    downstream: Option[S.Record], next: InstanceRecord,
    operation: GeneratedProvidedApiAbi.Operation, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, storage: WorkflowComponentStorage,
    native: WorkflowManagedDecisionCompletion[A.Answer], core: ActionCall.Core,
    observation: () => Consequence[Unit]): Consequence[spec.OperationDefinition] = for {
    questions <- storage.bindDecisionC(H.completionOperation, H.inputCodec)
    instances <- storage.bindInstancesC()(using core.executionContext)
    targets <- U.bindTargetsC(core.component.get.subsystem.get.configuration, declaration)
    row <- _row_c(submitted, resolution, downstream, targets)
    target <- ComponentRecordStorage.bindForCallC(core, targets.storeName, row.collection, DataStore.StringEntryId(row.identity))
    staged <- target.stageCreateC(row.payload)
    bound <- {
      val codec = new WorkflowCompletionServiceOperation.Codec[A.Answer] {
        val resultTypeIdentity = A.resultType
        def decodeC(request: Request) = Consequence.success(submitted)
        def encodeC(resume: ContinuationRuntime.Resume) = _invalid("answer acknowledgement needs committed readback")
        override def encodeAfterCommitC(resume: ContinuationRuntime.Resume, result: ContinuationResult[A.Answer],
          completed: Option[InstanceRecord]): Consequence[OperationResponse] = {
          val readback = for {
            actual <- completed.map(Consequence.success).getOrElse(_uncertain("canonical committed instance is absent"))
            _ <- if (resume != null && resume.result != null) Consequence.unit
              else _uncertain("canonical committed closing result is absent")
            _ <- observation()
            // The successor row is staged with the native completion in the
            // same managed program. Project the owning commit, not a second
            // load/recalculation and equality proof of the emitted bodies.
            retained = WorkflowCompletionRecordJsonV1.Record(actual.revision, result, Some(resume.result))
            wire <- _ack_c(new Observed(plan, retained, resolution, downstream, actual, actual))
          } yield OperationResponse.Scalar(wire)
          readback match {
            case Consequence.Success(value) => Consequence.success(value)
            case _ => _uncertain("committed answer readback is not established; observe later without resubmitting")
          }
        }
      }
      val closing = new WorkflowCompletionServiceOperation.ClosingProgram[A.Answer] {
        def selectC(result: ContinuationResult[A.Answer], current: InstanceRecord) = {
          // Native selection and managed completion own the actual issued
          // result, predecessor, suspension and CAS. This owning call already
          // admitted independent Human direction; staging is not publication.
          val program = staged.flatMap(_ => ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
            ActionExecution.Completed(StateMachineOperationResult(H.closingType, result.resultReference))))
          Consequence.success(WorkflowCompletionServiceOperation.SelectedClosing(program, Some(next)))
        }
      }
      WorkflowDecisionCompletionServiceOperation.bindC(SplitPhaseDefinitionBinding.providedDefinition, operation,
        WorkflowJsonCommandRequest.definition, spec.ResponseDefinition.void, codec, questions, instances, closing, native)
    }
  } yield bound

  private def _plan_c(command: SplitPhaseAdvanceTarget, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, core: ActionCall.Core,
    selected: Option[WorkflowInteraction[H.Input, Nothing]] = None): Consequence[H.Plan] = for {
    plan <- H.sourceC(command, declaration, catalog, core)
    issued <- selected match {
      case Some(value) => Consequence.success(value)
      case None => for {
        storage <- H.storageC(declaration, catalog, core)
        questions <- storage.bindDecisionC(H.completionOperation, H.inputCodec)
        actual <- questions.loadC(plan.question.current.asInstanceOf[WorkflowContinuation.Decision[H.Input]].request.continuationId)(using core.executionContext)
          .flatMap(_.map(Consequence.success).getOrElse(_invalid("actual issued reasoning Human question is absent")))
      } yield actual
    }
    resolved <- H.consumeIssuedC(plan, issued)
  } yield resolved

  private def _calculate_c(plan: H.Plan, submitted: ContinuationResult[A.Answer], resolution: Option[R.Record]):
      Consequence[(Option[S.Record], InstanceRecord)] = for {
    downstream <- (submitted.result.value, resolution) match {
      case (_: A.Answer.ProvideResolution, Some(record)) => R.calculateC(plan, submitted, record).map(Some(_))
      case (A.Answer.Defer | A.Answer.Cancel, None) => Consequence.success(None)
      case _ => _invalid("answer choice and complete separate interpretation differ")
    }
    stopped = submitted.result.value == A.Answer.Defer || submitted.result.value == A.Answer.Cancel
    current = plan.source.current
    entry = HistoryEntry(HistorySequence(current.history.size.toLong + 1L), current.revision.next,
      if (stopped) Lifecycle.Completed else Lifecycle.Active,
      if (stopped) None else Some(ProgressionReference("HumanAnswered")), current.correlation,
      CausationReference("split-reasoning-human-result:" + submitted.continuationId.value),
      current.derivedCompositeOccurrence, current.committedPredecessor, None)
    next <- current.appendC(current.revision, entry)
  } yield downstream -> next

  private def _observe_c(plan: H.Plan, completion: WorkflowCompletionRecordJsonV1.Record[A.Answer],
    submitted: ContinuationResult[A.Answer], declaration: SplitPhaseManagedStartAssembly.Declaration,
    core: ActionCall.Core): Consequence[Observed] = for {
    targets <- U.bindTargetsC(core.component.get.subsystem.get.configuration, declaration)
    key <- _key_c(submitted)
    target <- ComponentRecordStorage.bindForCallC(core, targets.storeName, targets.publication, DataStore.StringEntryId(key))
    row <- target.loadC().flatMap(_.map(Consequence.success).getOrElse(_invalid("completed Human answer lacks its atomic successor")))
    wire <- row.getString(_field).filter(_.startsWith(_schema + ":")).map(Consequence.success).getOrElse(_invalid("successor frame differs"))
    json <- parser.parse(wire.substring(_schema.length + 1)).fold(_ => _invalid("successor JSON differs"), Consequence.success)
    resolution <- _optional_c(json.hcursor.downField("resolution").focus, R.decodeC)
    downstream <- _optional_c(json.hcursor.downField("downstream").focus, J.decodeC)
    next = plan.source.current
  } yield new Observed(plan, completion, resolution, downstream, next, plan.source.current)

  private def _row_c(submitted: ContinuationResult[A.Answer], resolution: Option[R.Record],
    downstream: Option[S.Record], targets: U.Targets): Consequence[U.Row] = for {
    key <- _key_c(submitted)
    human <- resolution.fold[Consequence[Json]](Consequence.success(Json.Null))(v => R.encodeC(v).map(Json.fromString))
    snapshot <- downstream.fold[Consequence[Json]](Consequence.success(Json.Null))(v => J.encodeC(v).map(Json.fromString))
    wire = _schema + ":" + Json.obj("schema" -> Json.fromString(_schema),
      "resolution" -> human, "downstream" -> snapshot).noSpaces
    row = U.Row(targets.publication, key, Record.data(_field -> wire))
  } yield row
  private def _optional_c[T](json: Option[Json], decode: String => Consequence[T]): Consequence[Option[T]] = json match {
    case Some(value) if value.isNull => Consequence.success(None)
    case Some(value) if value.asString.nonEmpty => decode(value.asString.get).map(Some(_))
    case _ => _invalid("complete optional successor data is absent or malformed")
  }
  private def _key_c(submitted: ContinuationResult[A.Answer]): Consequence[String] =
    WorkflowMechanicalTransition.keyC(WorkflowMechanicalTransition.Reference(submitted.continuationId.value,
      submitted.expectedRevision.value)).map("reasoning-human-successor:" + _)
  private def _native_c(storage: WorkflowComponentStorage): Consequence[WorkflowManagedDecisionCompletion[A.Answer]] =
    storage.bindDecisionCompletionC(H.completionOperation, H.inputCodec, A.payloadCodec, H.closingType,
      (_, _) => Consequence.success(Vector.empty))
  private def _ack_c(observed: Observed): Consequence[String] = for {
    completion <- WorkflowCompletionRecordJsonV1.encodeC(observed.completion, A.payloadCodec)
    question <- WorkflowDecisionJsonV1.encodeC(observed.plan.question, H.inputCodec)
  } yield Json.obj("schemaVersion" -> Json.fromString(SplitPhaseManagedHumanCompletion.responseSchema),
    "completingRevision" -> Json.fromLong(observed.completion.instanceRevision.value),
    "currentRevision" -> Json.fromLong(observed.current.revision.value),
    "issuedDecision" -> Json.fromString(question), "completion" -> Json.fromString(completion)).noSpaces
  private def _invalid[T](reason: String): Consequence[T] = Consequence.stateConflict("SplitPhase managed reasoning Human " + reason)
  private def _uncertain[T](reason: String): Consequence[T] = DataStoreConditionalTransitionFailure.transactionIndeterminate(reason)
}
