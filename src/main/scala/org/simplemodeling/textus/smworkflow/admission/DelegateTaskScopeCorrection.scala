/**
 * @since   Oct. 30, 2026
 * @version Nov. 23, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as _, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.{SmWorkflowComponent, value as V}
import BoundedWorkRuntime.*
import BoundedWorkRuntime.given
import GoalPhaseDependencyInventoryContinuation.ManagedInventory
import StandaloneFixExecution as S
import S.given

/** Same-purpose inventory correction for pending implementation or repair work.
  * Condition timing remains an initial-implementation selection only.
  * The managed supersession retires an immutable issue; only ordinary Advance
  * issues its successor. Neither retirement nor an unclaimed issue establishes
  * that external effects did not occur.
  */
private[smworkflow] object DelegateTaskScopeCorrection extends ScopeCorrectionNativeOwner(
  DelegateTaskNativeRun,
  StateMachineOperationIdentity("DelegateTaskService", "correctDelegateTaskScope"),
  "DelegateTaskScopeCorrectionInput", "delegate-task-scope-corrected:",
  "DelegateTaskScopeCorrectionCheckpoint", allowConditionTiming = true, preserveOriginalSources = false,
  acceptsInput = input => input.delegation.exists(_.role == "destination")) {
  final case class Target(componentInstance: String, sessionId: String, participant: String,
    frontierRevision: Long, oldContinuationId: String, frontier: Json, issued: Json,
    business: Payload, continuationRevision: String, additionalOwnedPaths: Vector[String],
    submissionConditions: Option[Vector[String]] = None, managedInventory: Option[ManagedInventory] = None)
  given Encoder[Target] = deriveEncoder[Target].mapJson(_.mapObject(_.filter {
    case ("submissionConditions" | "managedInventory", value) => !value.isNull
    case _ => true
  }))
  given Decoder[Target] = deriveDecoder
  final case class Checkpoint(target: Target, reference: ContextReference)

  protected def declaredOperation(correct: Boolean): spec.OperationDefinition = if (correct)
    SmWorkflowComponent.DelegateTaskServiceService.CorrectDelegateTaskScopeOperation
    else SmWorkflowComponent.DelegateTaskServiceService.DescribeDelegateTaskScopeCorrectionOperation
}

private[smworkflow] object ScopeCorrectionNativeOwner {
  import DelegateTaskScopeCorrection.{Target, given}
  /** Public operation declarations are supplied by the composition layer. */
  def goalTask(description: spec.OperationDefinition, correction: spec.OperationDefinition): ScopeCorrectionNativeOwner =
    new ScopeCorrectionNativeOwner(GoalTaskNativeRun,
      StateMachineOperationIdentity("GoalTaskService", "correctGoalTaskScope"),
      "GoalTaskScopeCorrectionInput", "goal-task-scope-corrected:",
      "GoalTaskScopeCorrectionCheckpoint", allowConditionTiming = false, preserveOriginalSources = true,
      acceptsInput = input => input.task.nonEmpty && input.delegation.isEmpty) {
      protected def declaredOperation(correct: Boolean): spec.OperationDefinition = if (correct) correction else description
      override def inputForCallC(incoming: Request, call: ActionCall.Core): Consequence[V.DelegateTaskScopeCorrectionInput] = {
        final class Reader extends ProcedureActionCall {
          val core = call
          def readC = for {
            source <- NativeWorkflowResourceInput.selectC[V.GoalTaskScopeCorrectionInput](incoming)
            input <- NativeWorkflowResourceInput.readC(source, reference => read_resource_text(reference, Some(UTF_8)))
          } yield GoalTaskScopeCorrection.internalInput(input)
          def execute(): Consequence[OperationResponse] = GoalTaskNativeRun.invalid("Scope correction input reader is not an Operation")
        }
        new Reader().readC
      }
      override protected def descriptionInputC(incoming: Request): Consequence[V.DelegateTaskScopeCorrectionDescriptionInput] =
        NativeWorkflowOperationInput.valueC[V.GoalTaskScopeCorrectionDescriptionInput](incoming, "input").map(input =>
          V.DelegateTaskScopeCorrectionDescriptionInput(input.sessionId, input.oldContinuationId, input.additionalOwnedPaths, Vector.empty, None))
      override protected def response(target: Target, outcome: String, message: String, paths: Vector[String]): OperationResponse =
        NativeWorkflowOperationOutput.response(V.GoalTaskScopeCorrectionView(target.sessionId, outcome, message, target.asJson.noSpaces, paths))
    }
}

/** The canonical managed WorkOrder supersession owner shared by bounded Tasks.
  * The caller supplies the native codec/definition family and exact input policy;
  * this owner never executes business work or writes a separate application store.
  */
private[smworkflow] abstract class ScopeCorrectionNativeOwner(
  N: BoundedWorkNativeRun, val operation: StateMachineOperationIdentity, val inputType: String,
  val resumeCause: String, checkpointType: String, allowConditionTiming: Boolean,
  preserveOriginalSources: Boolean, acceptsInput: S.Input => Boolean) {
  import DelegateTaskScopeCorrection.{Target, Checkpoint}
  import DelegateTaskScopeCorrection.given
  import io.circe.generic.auto.*
  protected def declaredOperation(correct: Boolean): spec.OperationDefinition
  protected def descriptionInputC(incoming: Request): Consequence[V.DelegateTaskScopeCorrectionDescriptionInput] =
    NativeWorkflowOperationInput.valueC[V.DelegateTaskScopeCorrectionDescriptionInput](incoming, "input")
  protected def response(target: Target, outcome: String, message: String, paths: Vector[String]): OperationResponse =
    NativeWorkflowOperationOutput.response(V.DelegateTaskScopeCorrectionView(target.sessionId, outcome, message, target.asJson.noSpaces, paths))
  lazy val checkpointCodec = N.codec[Checkpoint](checkpointType)

  def correctedInputC(input: S.Input, additional: Vector[String], submission: Option[Vector[String]] = None): Consequence[S.Input] = for {
    _ <- if (input != null && acceptsInput(input) &&
        (allowConditionTiming || submission.isEmpty) && additional != null && (additional.nonEmpty || submission.exists(next => !input.submissionConditions.contains(next))) && additional.distinct == additional &&
        !additional.exists(input.ownedPaths.contains)) Consequence.unit
      else N.invalid("Scope correction needs new literal paths or changed condition timing for the same destination Task")
    corrected = input.copy(ownedPaths = input.ownedPaths ++ additional, submissionConditions = submission.orElse(input.submissionConditions))
    _ <- S.inputC(corrected)
    _ <- if (additional.forall(S.permitted(_, corrected))) Consequence.unit
      else N.invalid("Added ownership crosses the retained delegation exclusions")
  } yield corrected

  def targetC(text: String): Consequence[Target] = for {
    json <- io.circe.parser.parse(Option(text).getOrElse("")).fold(e => N.invalid(e.message), Consequence.success)
    target <- json.as[Target].fold(e => N.invalid(e.message), Consequence.success)
    _ <- if (target.asJson == json && target.frontierRevision > 0 &&
        Vector(target.componentInstance, target.sessionId, target.participant, target.oldContinuationId,
          target.continuationRevision).forall(s => s != null && s.nonEmpty && s.trim == s) &&
        target.frontier.isObject && target.issued.isObject && target.business.state.participant == target.participant &&
        target.managedInventory.forall(inventory => N.entry == "DelegateTask" &&
          target.business.state.stage == "Implement" && target.submissionConditions.isEmpty &&
          target.business.managedDependency.exists(_.work == inventory.parentWork) &&
          target.additionalOwnedPaths == inventory.additionalPaths))
      Consequence.unit else N.invalid("Scope target is incomplete or contains unsupported data")
    _ <- correctedInputC(target.business.state.input, target.additionalOwnedPaths, target.submissionConditions)
  } yield target

  /** Identical normalization is used by passive preparation and current-call
    * admission. Selection metadata alone never authorizes different input.
    */
  def selectionInputC(input: V.DelegateTaskScopeCorrectionInput): Consequence[Json] = for {
    target <- targetC(input.target)
    _ <- if (input.sessionId == target.sessionId && input.oldContinuationId == target.oldContinuationId &&
        input.participant == target.participant && input.reference.identity.nonEmpty && input.reference.revision.nonEmpty)
      Consequence.unit else N.invalid("Scope correction crosses its exact Session, issue, participant or Human reference")
  } yield Json.obj("sessionId" -> input.sessionId.asJson, "oldContinuationId" -> input.oldContinuationId.asJson,
    "target" -> input.target.asJson, "participant" -> input.participant.asJson,
    "reference" -> Json.obj("identity" -> input.reference.identity.asJson, "revision" -> input.reference.revision.asJson))

  /** Use the ordinary authorized Resource reader for retained Repair targets
    * whose actual check captures do not fit in a command-line argument. */
  def inputForCallC(incoming: Request, call: ActionCall.Core): Consequence[V.DelegateTaskScopeCorrectionInput] = {
    final class Reader extends ProcedureActionCall {
      val core = call
      def readC: Consequence[V.DelegateTaskScopeCorrectionInput] = for {
        source <- NativeWorkflowResourceInput.selectC[V.DelegateTaskScopeCorrectionInput](incoming)
        input <- NativeWorkflowResourceInput.readC(source, reference => read_resource_text(reference, Some(UTF_8)))
      } yield input
      def execute(): Consequence[OperationResponse] = N.invalid("Scope correction input reader is not an Operation")
    }
    new Reader().readC
  }

  private final case class Observation(target: Target, current: InstanceRecord,
    entityrevision: org.simplemodeling.model.datatype.EntityRevision)
  private def _observe_c(h: Host, storage: WorkflowComponentStorage, definition: DefinitionBinding,
    session: String, old: String, additional: Vector[String], submission: Option[Vector[String]],
    supplemental: Boolean, core: ActionCall.Core): Consequence[Observation] = for {
    adapter <- storage.bindWorkOrderSupersessionC(N.completionOperation, N.inputCodec, checkpointCodec)
    pending <- adapter.inspectC(ContinuationIdentity(old), InstanceIdentity(session))(using core.executionContext)
      .flatMap(_.map(Consequence.success).getOrElse(N.invalid("Exact pending Task issue is absent or already claimed")))
    _ <- if ((!preserveOriginalSources && !supplemental) || (pending.continuation.status == ContinuationRuntimePersistence.Status.Available &&
        pending.continuation.claimId.isEmpty)) Consequence.unit
      else N.invalid("Exact pending Task issue is not available and unclaimed")
    _ <- if (pending.current.definition == definition && pending.current.lifecycle == Lifecycle.Active)
      Consequence.unit else N.invalid("Scope correction crosses the native definition or lifecycle")
    order <- pending.issued.current match {
      case value: WorkflowContinuation.WorkOrder[S.Work] => Consequence.success(value)
      case _ => N.invalid("Scope correction requires an actual pending implementation or repair WorkOrder")
    }
    work <- order.request.input.map(x => Consequence.success(x.value)).getOrElse(N.invalid("Actual business input is absent"))
    revision <- scala.util.Try(work.sourceRevision.toLong).toOption.map(Consequence.success)
      .getOrElse(N.invalid("Work source revision is invalid"))
    payload <- N.loadPayloadC(storage, session, revision, core)
    _ <- GoalPhaseParticipantAdmission.admitC(payload.state.participant, core.executionContext)
    implementation = work.kind == "Implement" && payload.state.stage == "Implement"
    repair = work.kind == "Repair" && payload.state.stage == "Repair" && additional.nonEmpty && submission.isEmpty
    // loadPayloadC has verified the external captures for this checkpoint.
    // The shared encoding compares the same exact references on both sides.
    _ <- if (work.session == session && (implementation || repair) &&
        work.state.asJson == payload.state.asJson && order.request.completionOperation == N.completionOperation &&
        payload.root == h.root && payload.branch == h.branch && payload.state.input.repositoryIdentity == h.repository)
      Consequence.unit else N.invalid("Scope correction requires the same pending implementation or a repair inventory addition without condition timing changes")
    _ <- if (!supplemental || (N.entry == "DelegateTask" && implementation && submission.isEmpty)) Consequence.unit
      else N.invalid("Supplemental managed inventory requires pending destination implementation with its retained condition timing")
    _ <- correctedInputC(payload.state.input, additional, submission)
    _ <- if (additional.forall(h.paths.contains)) Consequence.unit
      else N.invalid("Added paths exceed the configured host ownership")
    inventory <- if (supplemental)
      ManagedDevelopmentBoundedWork.supplementalScopeCorrectionC(h, payload, additional, core).map(Some(_))
      else ManagedDevelopmentBoundedWork.admitScopeCorrectionC(h, payload.managedDependency, additional, core)
        .map(_ => Option.empty[ManagedInventory])
    frontier <- WorkflowInstanceRecordJsonV1.encodeC(pending.current).flatMap(_parse_c)
    issued <- WorkflowWorkOrderJsonV1.encodeC(pending.issued, N.inputCodec).flatMap(_parse_c)
  } yield Observation(Target(core.component.get.core.instanceId.canonicalKey, session, payload.state.participant,
    pending.current.revision.value, old, frontier, issued, payload, pending.entityRevision.value.toString, additional, submission, inventory),
    pending.current, pending.entityRevision)

  private def _parse_c(text: String): Consequence[Json] =
    io.circe.parser.parse(text).fold(e => N.invalid(e.message), Consequence.success)

  private def _checkpoint_c(storage: WorkflowComponentStorage, current: InstanceRecord,
    core: ActionCall.Core): Consequence[Checkpoint] = for {
    adapter <- storage.bindWorkOrderSupersessionC(N.completionOperation, N.inputCodec, checkpointCodec)
    old = current.causation.value.stripPrefix(resumeCause)
    retained <- adapter.loadRetainedC(ContinuationIdentity(old), current.identity)(using core.executionContext)
      .flatMap(_.map(Consequence.success).getOrElse(N.invalid("Scope correction checkpoint is absent")))
    _ <- if (retained.receipt.nextRevision == current.revision &&
        retained.receipt.payload.target.oldContinuationId == old) Consequence.unit
      else N.invalid("Scope checkpoint does not bind the current frontier")
  } yield retained.receipt.payload

  def retainedPayloadC(storage: WorkflowComponentStorage, current: InstanceRecord,
    core: ActionCall.Core): Consequence[Payload] = _checkpoint_c(storage, current, core).map(_.target.business)

  def retainedInputC(storage: WorkflowComponentStorage, current: InstanceRecord,
    core: ActionCall.Core): Consequence[S.Input] = _checkpoint_c(storage, current, core).flatMap { saved =>
    projectedInputC(saved)
  }

  /** Same projection for host composition and ordinary continuation. The
    * recorded original input is never replaced or stripped of provenance.
    */
  private[admission] def projectedInputC(saved: Checkpoint): Consequence[S.Input] =
    correctedInputC(saved.target.business.state.input, saved.target.additionalOwnedPaths, saved.target.submissionConditions)
      .map { input =>
        if (preserveOriginalSources || saved.target.additionalOwnedPaths.isEmpty || input.sourceReferences.contains(saved.reference)) input
        else input.copy(sourceReferences = input.sourceReferences :+ saved.reference)
      }

  /** Prove the legacy GoalTask input through actual managed supersessions.
    * Only the already loaded native history supplies receipt addresses. A later
    * mechanical checkpoint must retain exactly the same admitted input chain.
    */
  private[admission] def effectiveValidationInputC(storage: WorkflowComponentStorage, current: InstanceRecord,
    core: ActionCall.Core, target: GoalTaskValidationContinuation.Target,
    direction: GoalTaskValidationContinuation.Direction): Consequence[S.Input] = {
    for {
    _ <- if (preserveOriginalSources && !allowConditionTiming && N.entry == "GoalTask")
      Consequence.unit else N.invalid("Validation scope lineage requires the GoalTask family and source-preserving additive policy")
    _ <- if (current.identity.value == target.sessionId) Consequence.unit
      else N.invalid("Validation scope lineage crosses its exact Session")
    _ <- if (target.componentInstance == core.component.get.core.instanceId.canonicalKey) Consequence.unit
      else N.invalid("Validation scope lineage crosses its exact component owner")
    _ <- if (GoalTaskValidationContinuation.decodeTarget(direction.target).contains(target)) Consequence.unit
      else N.invalid("Validation scope lineage differs from its decoded original target")
    _ <- current.validateC
    // The caller already admitted this binding. DefinitionBinding has reference
    // identity, so a newly resolved binding cannot prove the current frontier.
    catalog <- WorkflowInstanceRecordJsonV1.catalogC(Vector(current.definition))
    original <- WorkflowInstanceRecordJsonV1.decodeC(target.frontier.noSpaces, catalog)
    _ <- if (original.definition == current.definition && original.identity == current.identity && original.revision.value == target.frontierRevision &&
        original.revision.value < current.revision.value && original.history == current.history.take(original.history.size))
      Consequence.unit else N.invalid("Validation scope lineage crosses the original native frontier")
    business <- N.loadPayloadC(storage, target.sessionId, target.frontierRevision, core)
    _ <- if (business.asJson == target.business.asJson) Consequence.unit
      else N.invalid("Validation scope lineage differs from the original native checkpoint")
    adapter <- storage.bindWorkOrderSupersessionC(N.completionOperation, N.inputCodec, checkpointCodec)
    issued <- storage.bindIssuedC(N.completionOperation, N.inputCodec)
    entries = current.history.filter(entry => entry.revision.value > target.frontierRevision &&
      entry.revision.value <= current.revision.value && entry.causation.value.startsWith(resumeCause))
    effective <- entries.foldLeft(Consequence.success(business.state.input)) { (previous, entry) => for {
      expected <- previous
      old = entry.causation.value.stripPrefix(resumeCause)
      retained <- adapter.loadRetainedC(ContinuationIdentity(old), current.identity)(using core.executionContext)
        .flatMap(_.map(Consequence.success).getOrElse(N.invalid("Validation scope lineage receipt is absent")))
      receipt = retained.receipt
      correction <- targetC(receipt.payload.target.asJson.noSpaces)
      frontier <- WorkflowInstanceRecordJsonV1.decodeC(correction.frontier.noSpaces, catalog)
      actual <- issued.loadC(ContinuationIdentity(old))(using core.executionContext)
        .flatMap(_.map(Consequence.success).getOrElse(N.invalid("Validation scope lineage issue is absent")))
      wire <- WorkflowWorkOrderJsonV1.encodeC(actual, N.inputCodec).flatMap(_parse_c)
      work <- actual.current match {
        case order: WorkflowContinuation.WorkOrder[S.Work] if order.request.completionOperation == N.completionOperation =>
          order.request.input.map(value => Consequence.success(value.value))
            .getOrElse(N.invalid("Validation scope lineage business input is absent"))
        case _ => N.invalid("Validation scope lineage is not the original GoalTask WorkOrder")
      }
      revision <- work.sourceRevision.toLongOption.map(Consequence.success)
        .getOrElse(N.invalid("Validation scope lineage source revision is invalid"))
      checkpoint <- N.loadPayloadC(storage, current.identity.value, revision, core)
      _ <- if (retained.current == current && receipt.identity.value == old && receipt.nextRevision == entry.revision &&
          receipt.sourceRevision == frontier.revision && receipt.nextRevision == receipt.sourceRevision.next &&
          correction.frontierRevision == frontier.revision.value && frontier.identity == current.identity &&
          frontier.history == current.history.take(frontier.history.size) &&
          current.history.find(_.revision == entry.revision).contains(entry) &&
          correction.componentInstance == target.componentInstance && correction.sessionId == target.sessionId &&
          correction.participant == target.participant && correction.oldContinuationId == old && correction.issued == wire &&
          checkpoint.asJson == correction.business.asJson && work.session == target.sessionId && work.state.asJson == checkpoint.state.asJson &&
          Set("Implement", "Repair")(work.kind) && work.kind == checkpoint.state.stage &&
          checkpoint.validationContinuation.contains(direction) && checkpoint.state.input == expected &&
          checkpoint.root == target.business.root && checkpoint.branch == target.business.branch &&
          checkpoint.baseHead == target.business.baseHead && correction.submissionConditions.isEmpty)
        Consequence.unit else N.invalid("Validation scope lineage receipt crosses its native owner, issue or preceding input")
      next <- correctedInputC(expected, correction.additionalOwnedPaths)
    } yield next }
  } yield effective
  }

  def resumeC(h: Host, storage: WorkflowComponentStorage, current: InstanceRecord,
    core: ActionCall.Core): Consequence[InstanceRecord] = for {
    saved <- _checkpoint_c(storage, current, core)
    _ <- GoalPhaseParticipantAdmission.admitC(saved.target.participant, core.executionContext)
    original = saved.target.business
    _ <- saved.target.managedInventory.fold(Consequence.unit) { expected => for {
      observed <- ManagedDevelopmentBoundedWork.supplementalScopeCorrectionC(h, original,
        saved.target.additionalOwnedPaths, core)
      _ <- if (expected == observed) Consequence.unit else N.invalid("Supplemental managed inventory changed before Advance")
    } yield () }
    input <- projectedInputC(saved)
    _ <- if (input.ownedPaths.forall(h.paths.contains)) Consequence.unit else N.invalid("Corrected host ownership changed")
    // Preserve base, generation, repair counts, results and old source references.
    state = original.state.copy(input = input,
      evidenceReferences = if (!preserveOriginalSources && saved.target.submissionConditions.nonEmpty)
        (original.state.evidenceReferences :+ saved.reference).distinct else original.state.evidenceReferences)
    supplemental = saved.target.managedInventory.map(inventory =>
      (original.supplementalOwnedPaths.getOrElse(Vector.empty) ++ inventory.additionalPaths).distinct)
      .orElse(original.supplementalOwnedPaths)
    unrelated = original.unrelated.map(_.filterNot { entry =>
      val endpoints = entry.path +: entry.renamedFrom.toVector
      endpoints.forall { path =>
        if (saved.target.managedInventory.nonEmpty) saved.target.additionalOwnedPaths.contains(path)
        else S.owned(path, saved.target.additionalOwnedPaths)
      }
    })
    next <- N.saveC(h, storage, current, original.copy(state = state, unrelated = unrelated,
      supplementalOwnedPaths = supplemental), core)
  } yield next

  def operations(h: Host, definition: DefinitionBinding,
    selections: ProfileStartAdmission.CallScopedSelectionPort): Vector[spec.OperationDefinition] =
    Vector(new Operation(false, h, definition, selections), new Operation(true, h, definition, selections))

  private final class Operation(correct: Boolean, h: Host, definition: DefinitionBinding,
    selections: ProfileStartAdmission.CallScopedSelectionPort) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(declaredOperation(correct))
    def createOperationRequest(incoming: Request): Consequence[OperationRequest] = Consequence.success(new CommandAction {
      val request = incoming
      override def commandExecutionMode = CommandExecutionMode.Sync
      def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
        val core = c
        def execute(): Consequence[OperationResponse] = for {
          input <- if (correct) inputForCallC(incoming, core)
            .map[Either[V.DelegateTaskScopeCorrectionInput, V.DelegateTaskScopeCorrectionDescriptionInput]](Left(_))
            else descriptionInputC(incoming)
              .map[Either[V.DelegateTaskScopeCorrectionInput, V.DelegateTaskScopeCorrectionDescriptionInput]](Right(_))
          session = input.fold(_.sessionId, _.sessionId)
          old = input.fold(_.oldContinuationId, _.oldContinuationId)
          context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(session))
          selected = core.copy(executionContext = context)
          storage <- N.storageC(h, definition, selected)
          instances <- storage.bindInstancesC()(using context)
          current <- instances.loadC(InstanceIdentity(session))(using context)
            .flatMap(_.map(Consequence.success).getOrElse(N.invalid("Scope correction Session is absent")))
          _ <- WorkflowForceClosure.requireOpenC(current)
          adapter <- storage.bindWorkOrderSupersessionC(N.completionOperation, N.inputCodec, checkpointCodec)
          retained <- adapter.loadRetainedC(ContinuationIdentity(old), InstanceIdentity(session))(using context)
          result <- input match {
            case Right(description) =>
              val additional = description.additionalOwnedPaths.toVector
              val submission = Option.when(description.submissionConditions.nonEmpty)(description.submissionConditions)
              val supplemental = description.supplementManagedInventory.contains(true)
              retained match {
                case Some(saved) if saved.receipt.payload.target.additionalOwnedPaths == additional &&
                    saved.receipt.payload.target.submissionConditions == submission &&
                    saved.receipt.payload.target.managedInventory.nonEmpty == supplemental =>
                  GoalPhaseParticipantAdmission.admitC(saved.receipt.payload.target.participant, context)
                    .flatMap(_ => _output_c(saved.receipt.payload.target, "already-corrected"))
                case Some(_) => N.invalid("Description differs from the retained scope correction")
                case None => _observe_c(h, storage, definition, session, old, additional, submission, supplemental, selected)
                  .flatMap(value => _output_c(value.target, "decision-required"))
              }
            case Left(value) => for {
              expected <- targetC(value.target)
              normalized <- selectionInputC(value)
              chosen <- selections.loadForCallC(value.selectionId, selected)
              port = new ProfileStartAdmission.SelectionPort {
                def loadC(id: String) = if (id == value.selectionId) Consequence.success(chosen)
                  else N.invalid("Scope selection crosses its recorded identity")
              }
              _ <- GoalPhaseParticipantAdmission.admitC(value.participant, context)
              _ <- ProfileStartAdmission.admitC(ProfileStartAdmission.Binding(operation,
                definition.workflowIdentity, definition.workflowRevision, inputType), TypedValue(inputType, normalized),
                value.selectionId, value.participant, value.idempotencyKey, port)
              _ <- if (chosen.flatMap(_.selectedInput).exists(v => v.typeIdentity == inputType && v.value == normalized))
                Consequence.unit else N.invalid("Actual Human selection does not bind the complete correction")
              reference = ContextReference(value.reference.identity, value.reference.revision)
              response <- retained match {
                case Some(saved) if saved.receipt.payload == Checkpoint(expected, reference) => _output_c(expected, "already-corrected")
                case Some(_) => N.invalid("Repeated correction differs from its committed target")
                case None => for {
                  observed <- _observe_c(h, storage, definition, session, old, expected.additionalOwnedPaths,
                    expected.submissionConditions, expected.managedInventory.nonEmpty, selected)
                  _ <- if (expected.asJson == observed.target.asJson) Consequence.unit else N.invalid("Scope target changed; obtain a current description")
                  _ <- adapter.executeC(ContinuationIdentity(old), observed.current.revision, observed.entityrevision,
                    observed.current, N.history(observed.current, resumeCause + old),
                    TypedValue(checkpointCodec.typeIdentity, Checkpoint(expected, reference)))(using context)
                  output <- _output_c(expected, "corrected-awaiting-advance")
                } yield output
              }
            } yield response
          }
        } yield result
      }
    })
  }
  private def _output_c(target: Target, outcome: String): Consequence[OperationResponse] = for {
    input <- correctedInputC(target.business.state.input, target.additionalOwnedPaths, target.submissionConditions)
  } yield response(target, outcome,
    "Original issue and partial work are retained. External effects remain unchanged and unknown effects remain UNKNOWN. " +
      "All condition identities remain adopted; later acceptance conditions remain pending. Correction executes no work; ordinary Advance issues the amended same-purpose request.",
    input.ownedPaths)
}

/** Stable GoalTask owner; wire DTOs are translated only at the boundary. */
private[smworkflow] object GoalTaskScopeCorrection {
  private val owner = ScopeCorrectionNativeOwner.goalTask(
    SmWorkflowComponent.GoalTaskServiceService.DescribeGoalTaskScopeCorrectionOperation,
    SmWorkflowComponent.GoalTaskServiceService.CorrectGoalTaskScopeOperation)
  export owner.*
  def internalInput(input: V.GoalTaskScopeCorrectionInput): V.DelegateTaskScopeCorrectionInput =
    V.DelegateTaskScopeCorrectionInput(input.sessionId, input.oldContinuationId, input.target, input.selectionId,
      input.participant, input.idempotencyKey, input.reference)
}
