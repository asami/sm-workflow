package org.simplemodeling.textus.smworkflow.admission

import org.simplemodeling.textus.smworkflow.admission.StandaloneFixExecution.given

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.processexecution.{GitObjectId, GitOperation, GitSelectionId, GitExecutionOutcome, SbtExecutionAttempt}
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as NativeIdentity, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import StandaloneFixExecution as S
import RepositorySyncManagedGitObservation as G
import WorkflowManagedProcessExecution as E

/** Shared retained runtime data. Existing standalone identity and schema stay unchanged. */
private[smworkflow] object BoundedWorkRuntime {
  final case class Host(configuration: Configuration, instances: DataStore.CollectionId,
    continuations: DataStore.CollectionId, repository: String, root: String, branch: String,
    paths: Vector[String], checks: Vector[GoalPhaseNativeValidation.Check],
    profiles: Option[WorkflowExecutionProfileMapping], origin: Option[GoalPhaseNativeExecution.Origin],
    review: Option[GoalPhaseNativeReview.EvidencePort],
    evidence: Option[CommonEvidence.Policy], journal: Option[DevelopmentJournal.Policy],
    publication: Option[LocalPublicationRuntime.Policy] = None, hygieneMode: String = CommitHygiene.autoFix,
    completionRecords: Option[CompletionRecords.Policy] = None)
  def host(d: GoalPhaseNativeRun.Declaration): Host = Host(d.configuration, d.instances, d.continuations,
    d.input.repositoryIdentity, d.target.root.toString, d.target.localBranch, d.candidatePaths,
    d.validationPolicy.toVector.flatMap(_.checks), d.executionProfiles, d.executionOrigin, d.reviewInstallation.map(_.evidence), d.evidencePolicy, d.journalPolicy, d.localPublication, d.commitHygieneMode, d.completionRecords)
  final case class Payload(state: S.State, root: String, branch: String, baseHead: String,
    implementationWorker: Option[String] = None, unrelated: Option[Vector[G.DirtyPath]] = None, implementationWorkers: Option[Vector[String]] = None,
    supersededWork: Option[String] = None, hygiene: Option[CommitHygiene.Record] = None,
    records: Option[CompletionRecords.Plan] = None)
  private given Encoder[G.DirtyPath] = deriveEncoder
  private given Decoder[G.DirtyPath] = deriveDecoder
  given Encoder[Payload] = deriveEncoder[Payload].mapJson(_.mapObject(_.filter {
    case ("unrelated" | "implementationWorkers" | "supersededWork" | "hygiene" | "records", value) => !value.isNull
    case _ => true
  }))
  given Decoder[Payload] = deriveDecoder
}

/** Standalone business ownership over canonical CNCF persistence and execution.
  * No Phase Start, Plan, Step acceptance, commit or private command driver.
  * @since   Oct. 26, 2026
  * @version Oct. 28, 2026
  */
private[smworkflow] object StandaloneFixNativeRun extends BoundedWorkNativeRun(false) {
  type Host = BoundedWorkRuntime.Host
  val Host = BoundedWorkRuntime.Host
  type Payload = BoundedWorkRuntime.Payload
  val Payload = BoundedWorkRuntime.Payload
  def host(d: GoalPhaseNativeRun.Declaration): Host = BoundedWorkRuntime.host(d)
}
private[smworkflow] object GoalTaskNativeRun extends BoundedWorkNativeRun(true)
private[smworkflow] object DelegateTaskNativeRun extends BoundedWorkNativeRun(true, true)

/** Both entries use one native progression owner and the same typed executor. */
private[smworkflow] class BoundedWorkNativeRun(val task: Boolean, val delegation: Boolean = false) {
  import BoundedWorkRuntime.*
  import BoundedWorkRuntime.given
  val entry = if (delegation) "DelegateTask" else if (task) "GoalTask" else "StandaloneFix"
  val wire = if (delegation) "delegate-task" else if (task) "goal-task" else "standalone-fix"
  val startOperation = StateMachineOperationIdentity(entry + "Service", "start" + entry)
  val reviewStartOperation = StateMachineOperationIdentity("StandaloneFixService", "startStandaloneReviewFix")
  val advanceOperation = StateMachineOperationIdentity(entry + "Service", "advance" + entry)
  val completionOperation = StateMachineOperationIdentity(entry + "Service", "submit" + entry + "WorkResult")
  val closingType = StateMachineResultTypeReference(entry + "View")
  def codec[A: Encoder: Decoder](identity: String): WorkflowResultJsonV1.PayloadCodec[A] =
    new WorkflowResultJsonV1.PayloadCodec[A] {
      val typeIdentity = identity
      def encode(value: A) = Json.obj("schemaVersion" -> Json.fromString(("sm-workflow." + wire + ".v1")), "payload" -> value.asJson)
      def decode(value: Json): Either[String, A] = for {
        version <- value.hcursor.get[String]("schemaVersion").left.map(_.message)
        _ <- Either.cond(version == ("sm-workflow." + wire + ".v1"), (), "Standalone schema differs")
        payload <- value.hcursor.get[A]("payload").left.map(_.message)
      } yield payload
    }
  lazy val stateCodec = codec[Payload](entry + "NativeState")
  lazy val inputCodec = codec[S.Work](entry + "WorkRequest")
  lazy val resultCodec = codec[S.Result](entry + "WorkResult")
  private def terminal(stage: String): Boolean = if (delegation) DelegateTask.terminal(stage) else if (task) Set("Completed", "NoChange")(stage) else Set("Accepted", "NoChange")(stage)
  private def reference(id: String, revision: Long) = WorkflowMechanicalTransition.Reference(id + ":standalone-state:" + revision, "1")
  private def empty = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](())
  def storageC(h: Host, definition: DefinitionBinding, core: ActionCall.Core): Consequence[WorkflowComponentStorage] =
    WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
      SmWorkflowComponent.componentId, h.configuration, Vector(definition), h.instances, h.continuations)
  def history(from: InstanceRecord, cause: String, accepted: Boolean = false): HistoryEntry =
    HistoryEntry(HistorySequence(from.history.size.toLong + 1), from.revision.next, if (accepted) Lifecycle.Completed else Lifecycle.Active,
      if (accepted) None else Some(ProgressionReference(if (task) "task.Running" else "fix.Running")), from.correlation,
      CausationReference(cause), from.derivedCompositeOccurrence, from.committedPredecessor, None)
  private def recordPaths(h: Host, payload: Payload): Vector[String] = if (!task || payload.state.input.delegation.exists(_.role == "requester")) Vector.empty
    else CompletionRecords.selected(h.completionRecords, wire, payload.state.input.requestIdentity)
  private def closureComplete(h: Host, payload: Payload): Boolean = terminal(payload.state.stage) &&
    !(payload.state.stage == "NoChange" && recordPaths(h, payload).nonEmpty && payload.records.isEmpty)
  def saveC(h: Host, storage: WorkflowComponentStorage, from: InstanceRecord, payload: Payload,
    core: ActionCall.Core): Consequence[InstanceRecord] = for {
    projection <- if (closureComplete(h, payload)) recordEvidenceObservedC(h, core,
      from.identity.value, from.revision.next.value,
      Json.obj("state" -> payload.state.asJson, "workflowRevision" -> Json.fromLong(from.revision.next.value)),
      "completion", "passed").map(Some(_)) else Consequence.success(None)
    selected = projection.fold(payload)(p => payload.copy(state = payload.state.copy(
      evidenceReferences = (payload.state.evidenceReferences ++ p.references).distinct, projectionWarning = p.warning)))
    mechanical <- storage.bindMechanicalC(advanceOperation, stateCodec)
    saved <- mechanical.executeC(WorkflowMechanicalTransition.Intent(h.configuration,
      reference(from.identity.value, from.revision.next.value), from, from.revision,
      history(from, "standalone-state:" + from.revision.next.value, closureComplete(h, payload)),
      TypedValue(stateCodec.typeIdentity, selected)), empty)(using core.executionContext)
  } yield saved.receipt.next
  def loadPayloadC(storage: WorkflowComponentStorage, id: String, revision: Long,
    core: ActionCall.Core): Consequence[Payload] = for {
    port <- storage.bindMechanicalC(advanceOperation, stateCodec)
    saved <- port.loadRetainedC(reference(id, revision))(using core.executionContext)
    payload <- saved.map(x => Consequence.success(x.receipt.payload.value))
      .getOrElse(invalid("Current frontier has no canonical standalone checkpoint"))
  } yield payload

  /** Recompose a standard host from its actual native input, without selecting
    * a new Task/Phase or requiring a globally configured business request.
    */
  private[admission] def retainedInputC(storage: WorkflowComponentStorage, id: String,
    core: ActionCall.Core): Consequence[S.Input] = for {
    context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(id))
    selected = core.copy(executionContext = context)
    instances <- storage.bindInstancesC()(using context)
    current <- instances.loadC(InstanceIdentity(id))(using context).flatMap(
      _.map(Consequence.success).getOrElse(invalid("Session has no retained bounded work")))
    _ <- WorkflowForceClosure.requireOpenC(current)
    input <- current.suspension match {
      case Some(suspension) => for {
        issued <- storage.bindIssuedC(completionOperation, inputCodec)
        work <- issued.loadC(ContinuationIdentity(suspension.continuationIdentity.value))(using context)
          .flatMap(_.map(Consequence.success).getOrElse(invalid("Retained bounded work input is absent")))
        value <- NativeWorkflowOperationOutput.workInputC(work)
      } yield value.state.input
      case None if task && current.causation.value.startsWith(GoalTaskMalformedIssuedWorkRecovery.resumeCause) =>
        GoalTaskMalformedIssuedWorkRecovery.retainedInputC(storage, current, selected)
      case None =>
        val revision = if (current.causation.value.startsWith("standalone-completed:"))
          scala.util.Try(current.causation.value.stripPrefix("standalone-completed:").takeWhile(_ != ':').toLong).toOption
        else Some(current.revision.value)
        revision.map(value => loadPayloadC(storage, id, value, selected).map(_.state.input))
          .getOrElse(invalid("Retained bounded work revision is malformed"))
    }
  } yield input

  /** Semantic intent only; the configured runtime owns concrete profile selection. */
  private[admission] def executionRequirementC(input: S.Input, stage: String): Consequence[ExecutionRequirement] = {
    import GoalPhaseWorkClassification.*
    for {
      _ <- if (Set("Implement", "Repair", "Review")(stage) && Set("code", "documentation")(input.kind))
        Consequence.unit else invalid("Fresh work has no supported semantic classification")
      required <- requiredC(stage)
      kind = if (stage == "Review") Kind.REVIEW else if (input.kind == "code") Kind.CODING else Kind.DESIGN
      requirement <- Classification(kind, Complexity.STANDARD, Domain.PROGRAMMING, Footprint.SMALL).requirementC(
        ExecutionRequirement(Vector(CapabilityRequirement(required.identity.capability)), RiskLevel("SOURCE_IMPLEMENTATION"),
          ReasoningLevel.Standard, stage == "Review"))
    } yield requirement
  }

  /** New Starts must support both repair/implementation and possible independent review.
    * Resolve classes without manufacturing an issued WorkOrder or execution observation.
    */
  private[admission] def preflightExecutionC(input: S.Input, profiles: Option[WorkflowExecutionProfileMapping],
    origin: Option[GoalPhaseNativeExecution.Origin]): Consequence[Unit] = for {
    _ <- GoalPhaseNativeExecution.validatePairC(profiles, origin)
    mapping <- profiles.map(Consequence.success).getOrElse(invalid("New Start requires execution profiles and origin"))
    current <- origin.map(Consequence.success).getOrElse(invalid("New Start requires execution profiles and origin"))
    implementation <- executionRequirementC(input, if (task) "Implement" else "Repair")
    review <- executionRequirementC(input, "Review")
    _ <- mapping.resolveClassC(implementation.reasoningClass.get)
    _ <- mapping.resolveClassC(review.reasoningClass.get)
    _ <- mapping.resolveC(implementation, current.current, current.preference)
    _ <- mapping.resolveC(review, current.current, current.preference)
  } yield ()

  def startC(input: S.Input, participant: String, key: String, selection: String,
    selections: ProfileStartAdmission.CallScopedSelectionPort, h: Host, definition: DefinitionBinding,
    core: ActionCall.Core): Consequence[V.StandaloneFixView] = for {
    _ <- S.inputC(input)
    _ <- if (input.task.nonEmpty == task && input.delegation.nonEmpty == delegation) Consequence.unit else invalid("Entry completion policy differs from the adopted input")
    _ <- preflightExecutionC(input, h.profiles, h.origin)
    _ <- input.delegation.flatMap(_.publicationTarget).fold(Consequence.unit) { target =>
      if (h.publication.exists(p => LocalPublicationRuntime.validateC(p).isSuccess && p.targets.exists(t =>
          t.identity == target && t.component == input.delegation.get.componentIdentity && t.workspace == "sm-workflow-repository")))
        Consequence.unit else invalid("Required delegated publication target is not installed for this Component/workspace")
    }
    _ <- GoalPhaseParticipantAdmission.admitC(participant, core.executionContext)
    _ <- if (input.repositoryIdentity == h.repository && input.ownedPaths.forall(p => h.paths.contains(p))) Consequence.unit
      else invalid("Requested repository/paths exceed the configured host bindings")
    selected <- if (delegation) DelegateTask.selectChecksC(input, h.checks) else Consequence.success(input.checks.flatMap(id => h.checks.find(_.identity == id)))
    initialstate <- S.startC(input, participant, selected)
    state = if (input.delegation.exists(_.role == "requester")) initialstate.copy(stage = "DispatchReady", delivery = Some(DelegateTask.Delivery()),
      message = "One authorized existing-chat dispatch is ready for the caller; no transport or destination work is implied") else initialstate
    admissionoperation = if (input.reviewOrigin.nonEmpty) reviewStartOperation else startOperation
    inputtype = if (input.reviewOrigin.nonEmpty) "StandaloneReviewFixInput" else entry + "Input"
    binding = ProfileStartAdmission.Binding(admissionoperation, definition.workflowIdentity, definition.workflowRevision, inputtype)
    // Freeze one actual managed read, so metadata admission and complete-input
    // equality cannot accidentally consume two different selection observations.
    chosen <- selections.loadForCallC(selection, core)
    actualSelection = new ProfileStartAdmission.SelectionPort {
      def loadC(id: String) = if (id == selection) Consequence.success(chosen)
        else invalid("Selection read crossed its actual managed identity")
    }
    _ <- ProfileStartAdmission.admitC(binding, TypedValue(inputtype, input.asJson), selection, participant, key, actualSelection)
    _ <- if (chosen.flatMap(_.selectedInput).exists(v => v.typeIdentity == inputtype && v.value == input.asJson))
      Consequence.unit else invalid("Actual human selection does not bind this complete standalone request")
    identity <- core.executionContext.security.session.flatMap(_.sessionId).map(x => Consequence.success(InstanceIdentity(x)))
      .getOrElse(invalid("Standalone Start requires its canonical session"))
    store <- storageC(h, definition, core)
    instances <- store.bindInstancesC()(using core.executionContext)
    prior <- if (delegation) instances.loadC(identity)(using core.executionContext) else Consequence.success(Option.empty[InstanceRecord])
    _ <- prior.fold(Consequence.unit)(WorkflowForceClosure.requireOpenC)
    root <- G.rootC(core)
    observed <- G.observeC(core)
    _ <- if (root == h.root && observed.branch == h.branch && (prior.nonEmpty || observed.dirty.forall(d =>
      (S.owned(d.path, input.ownedPaths) && d.renamedFrom.forall(S.owned(_, input.ownedPaths))) ||
      (task && Set(' ', '?')(d.index) && !d.renamedFrom.exists(S.owned(_, input.ownedPaths)))))) Consequence.unit
      else invalid("Start preserves unrelated work by refusing a crossed repository or dirty scope")
    _ <- if (prior.nonEmpty || !task || !observed.dirty.exists(d => S.owned(d.path, input.ownedPaths))) Consequence.unit
      else invalid("Task Start requires clean selected ownership so pre-existing edits cannot be silently captured")
    retainedStart <- prior match {
      case Some(current) => for {
        _ <- if (current.definition == definition && current.correlation == CorrelationReference(key)) Consequence.unit
          else invalid("Delegation request identity belongs to a different native definition or original idempotency key")
        checkpoint <- if (current.causation.value.startsWith("standalone-completed:"))
          current.causation.value.stripPrefix("standalone-completed:").takeWhile(_ != ':').toLongOption.map(Consequence.success)
            .getOrElse(invalid("Redelivery completion has no actual source checkpoint"))
          else Consequence.success(if (current.suspension.nonEmpty) current.revision.value - 1 else current.revision.value)
        retained <- loadPayloadC(store, current.identity.value, checkpoint, core)
        _ <- if (retained.state.input == input && retained.state.participant == participant && retained.root == root && retained.branch == observed.branch)
          Consequence.unit else invalid("Redelivery changes the existing delegation request or owners")
      } yield current -> retained.state
      case None => for {
        initial <- InstanceRecord(identity, definition, initialRevision, Lifecycle.NotStarted, None,
          CorrelationReference(key), CausationReference("standalone-started"), None, None, None, Vector.empty).validateC
        _ <- instances.createC(initial)(using core.executionContext)
        active <- instances.appendC(identity, initial.revision, history(initial, "standalone-started"))(using core.executionContext)
        saved <- saveC(h, store, active, Payload(state, root, observed.branch, observed.head, unrelated = if (task) Some(observed.dirty.filterNot(d => S.owned(d.path, input.ownedPaths))) else None), core)
      } yield saved -> state
    }
  } yield view(retainedStart._1, retainedStart._2, if (prior.nonEmpty) "Retained" else "Started", if (prior.nonEmpty)
    "Redelivery retains the original native Session and actual frontier; ordinary Advance collects its work/result without a new Start"
    else if (task) "Task request retained; normal Advance owns implementation and selected checks"
    else if (input.reviewOrigin.nonEmpty) "Original review findings retained; normal Advance owns bounded repair and selected checks"
    else "Standalone request retained; normal Advance owns the selected checks")

  def advanceC(id: String, h: Host, definition: DefinitionBinding, core: ActionCall.Core): Consequence[V.StandaloneFixView] = for {
    storage <- storageC(h, definition, core)
    instances <- storage.bindInstancesC()(using core.executionContext)
    current <- instances.loadC(InstanceIdentity(id))(using core.executionContext).flatMap {
      case Some(value) if value.definition == definition => Consequence.success(value)
      case _ => invalid("No standalone instance belongs to this exact definition")
    }
    _ <- WorkflowForceClosure.requireOpenC(current)
    closure <- if (task) CompletionRecordContinuation.retainedC(storage, current, core) else Consequence.success(None)
    result <- closure match {
      case Some(value) => for {
        raw <- value.target.toJsonStringC.flatMap(s => io.circe.parser.parse(s).fold(e => invalid(e.message), Consequence.success))
        source <- raw.hcursor.downField("source").downField("accepted").downField("payload").get[Json]("state").fold(e => invalid(e.message), Consequence.success)
        state <- recordC(source.deepMerge(Json.obj("localClosure" -> raw)))
      } yield V.StandaloneFixView(id, value.kind, "Completion scope " + value.scope + " " + value.identity + "; " + value.message,
        Some(state), None, None, None, None, None, Vector.empty, None)
      case None => current.suspension match {
      case Some(suspension) => for {
        issued <- storage.bindIssuedC(completionOperation, inputCodec)
        actual <- issued.loadC(ContinuationIdentity(suspension.continuationIdentity.value))(using core.executionContext)
          .flatMap(_.map(Consequence.success).getOrElse(invalid("Pending work is not retained")))
        response <- requestViewC(current, actual, core)
      } yield response
      case None => for {
        state <- if (current.causation.value.startsWith("standalone-completed:")) {
          val parts = current.causation.value.stripPrefix("standalone-completed:").split(":", 2)
          for {
            revision <- scala.util.Try(parts(0).toLong).toOption.map(Consequence.success)
              .getOrElse(invalid("Completion source revision is malformed"))
            payload <- loadPayloadC(storage, id, revision, core)
            _ <- GoalPhaseParticipantAdmission.admitC(payload.state.participant, core.executionContext)
            completions <- storage.bindCompletionC(completionOperation, inputCodec, resultCodec, closingType,
              (_, _) => Consequence.success(Vector.empty))
            done <- completions.loadRetainedC(ContinuationIdentity(parts(1)))(using core.executionContext)
              .flatMap(_.map(x => Consequence.success(x.completion.result)).getOrElse(invalid("Actual completion is missing")))
            next <- S.completedC(S.Work(id, revision.toString, payload.state.stage, payload.state), done.result.value, done.resultReference)
            references <- recordSemanticEvidenceC(h, core, id, current.revision.value,
              payload.state.stage, done.result.value)
            retained = next.copy(evidenceReferences = next.evidenceReferences ++ references)
            saved <- saveC(h, storage, current, payload.copy(state = retained, supersededWork = None,
              implementationWorker = if (Set("Repair", "Implement")(payload.state.stage)) done.evidence.workerIdentity else payload.implementationWorker,
              implementationWorkers = if ((task || payload.state.input.reviewOrigin.nonEmpty) && Set("Repair", "Implement")(payload.state.stage))
                Some((payload.implementationWorkers.getOrElse(Vector.empty) ++ done.evidence.workerIdentity.toVector).distinct)
                else payload.implementationWorkers), core)
          } yield saved
        } else if (task && current.causation.value.startsWith(GoalTaskMalformedIssuedWorkRecovery.resumeCause))
          GoalTaskMalformedIssuedWorkRecovery.resumeC(h, storage, current, core)
        else Consequence.success(current)
        payload <- loadPayloadC(storage, id, state.revision.value, core)
        _ <- GoalPhaseParticipantAdmission.admitC(payload.state.participant, core.executionContext)
        response <- progressC(h, definition, storage, state, payload, core, 0)
      } yield response
    }
    }
  } yield result

  private def progressC(h: Host, definition: DefinitionBinding, store: WorkflowComponentStorage,
    record: InstanceRecord, payload: Payload, core: ActionCall.Core, depth: Int): Consequence[V.StandaloneFixView] = {
    val s = payload.state
    if (depth > 36) invalid("Bounded native progress exceeded its finite check batch")
    else s.stage match {
      case "Inspect" => for {
        // Persist before any staging/read effect. A loaded intent cannot replay it.
        intent <- saveC(h, store, record, payload.copy(state = s.copy(stage = "CandidateIntent")), core)
        candidate <- captureC(h, payload, core)
        next <- S.candidateC(s, candidate)
        saved <- saveC(h, store, intent, payload.copy(state = next), core)
        result <- progressC(h, definition, store, saved, payload.copy(state = next), core, depth + 1)
      } yield result
      case "Validate" => for {
        _ <- freshC(payload, core)
        intent <- S.intentC(s)
        savedIntent <- saveC(h, store, record, payload.copy(state = intent), core)
        check = s.selected(s.receipts.size)
        actual <- E.repositoryValidationAttemptC(core, check.ordinal, check.kind)
        response <- actual match {
          case SbtExecutionAttempt.Observed(result) => for {
            facts <- WorkflowSbtValidation.factsC(check.kind, result)
            p = result.processResult
            receipt = RepositorySyncNativeMergeValidation.Receipt(check.identity, WorkflowSbtCapture.termination(p),
              WorkflowSbtCapture.exitCode(p), WorkflowSbtCapture.text(p.stdout).value, WorkflowSbtCapture.text(p.stderr).value,
              Some(facts), Some(p))
            _ <- freshC(payload, core)
            observed <- S.observedC(intent, receipt)
            references <- recordCheckEvidenceC(h, core, record.identity.value, savedIntent.revision.value, receipt)
            next = observed.copy(evidenceReferences = observed.evidenceReferences ++ references)
            saved <- saveC(h, store, savedIntent, payload.copy(state = next), core)
            retained <- loadPayloadC(store, saved.identity.value, saved.revision.value, core)
            response <- progressC(h, definition, store, saved, retained, core, depth + 1)
          } yield response
          case SbtExecutionAttempt.AdmissionRejected(_, _, conclusion) =>
            val blocked = intent.copy(stage = "AdmissionBlocked", pendingCheck = None,
              message = "Confirmed SBT nonexecution; original selection and unconsumed authority retained: " + conclusion.toString)
            saveC(h, store, savedIntent, payload.copy(state = blocked), core)
              .map(saved => view(saved, blocked, "Wait", blocked.message))
          case SbtExecutionAttempt.Uncertain(_, _, conclusion) => Consequence.success(view(savedIntent, intent, "Wait",
            "Managed SBT effects remain uncertain: " + conclusion.toString + "; retain intent and observe before any new attempt"))
        }
      } yield response
      case "AdmissionBlocked" => for {
        _ <- freshC(payload, core)
        next = s.copy(stage = "Validate", message = "Ordinary preparation continuation of confirmed unstarted selected work")
        saved <- saveC(h, store, record, payload.copy(state = next), core)
        response <- progressC(h, definition, store, saved, payload.copy(state = next), core, depth + 1)
      } yield response
      case "Repair" | "Review" | "Implement" => issueC(h, store, record, payload, core)
      case "RecordsReady" if task => recordsC(h, store, record, payload, core)
      case "NoChange" if task && recordPaths(h, payload).nonEmpty && payload.records.isEmpty => commitC(h, store, record, payload, core)
      case "CommitReady" => commitC(h, store, record, payload, core)
      case "CommitIntent" | "CommitUnknown" => reconcileC(h, store, record, payload, core)
      // A failed process may already have committed (for example a later hook
      // failed). Observe its actual effect; a nonzero exit is no retry authority.
      case "CommitFailed" if task => reconcileC(h, store, record, payload, core)
      case "CommitFailed" => Consequence.success(view(record, s, "Incomplete", s.message))
      case "Completed" | "NoChange" if delegation => deliverC(h, store, record, payload, core)
      case "HygieneReady" if delegation => hygieneC(h, store, record, payload, core)
      case "PublicationWait" if delegation => deliverC(h, store, record, payload, core)
      case "DispatchReady" => Consequence.success(view(record, s, "Dispatch", s.message))
      case "ReturnReady" => Consequence.success(view(record, s, "Return", s.message))
      case "Returned" | "Adopted" if delegation => Consequence.success(view(record, s, "Terminal", s.message))
      case "Completed" | "Accepted" | "NoChange" => Consequence.success(view(record, s, "Terminal", s.message))
      case "Human" => Consequence.success(view(record, s, "Human", s.message))
      case _ => Consequence.success(view(record, s, "Wait", s.message + "; retained intent is not executable permission"))
    }
  }

  private def captureC(h: Host, payload: Payload, core: ActionCall.Core): Consequence[S.Candidate] = for {
    before <- G.observeC(core)
    root <- G.rootC(core)
    _ <- if (root == payload.root && before.branch == payload.branch && before.head == payload.baseHead &&
      dirtyCovers(payload, before.dirty))
      Consequence.unit else invalid("Candidate observation crosses the adopted root, HEAD or ownership")
    _ <- payload.state.input.ownedPaths.foldLeft(Consequence.unit) { (previous, path) => previous.flatMap(_ =>
      E.stageGoalCandidateC(core, h.paths.indexOf(path) + 1).flatMap(_.valueC).map(_ => ())) }
    head <- GitObjectId.parseC(payload.baseHead)
    selected <- E.executeGitC(core, GitOperation.PendingPaths(head)).flatMap(_.valueC)
    diff <- E.executeGitC(core, GitOperation.PendingDiff(head)).flatMap(_.valueC)
    tree <- G.indexTreeC(core)
    after <- G.observeC(core)
    _ <- if (after.head == before.head && dirtyCovers(payload, after.dirty) && after.dirty.filter(d => S.owned(d.path, payload.state.input.ownedPaths)).forall(_.worktree == ' ') &&
      selected.forall(p => S.owned(p.value, payload.state.input.ownedPaths))) Consequence.unit
      else invalid("Candidate changed during observation or contains unrelated staged work")
  } yield S.Candidate(before.head, tree, selected.map(_.value), diff.text)

  private[admission] def freshC(payload: Payload, core: ActionCall.Core): Consequence[Unit] = for {
    root <- G.rootC(core)
    current <- G.observeC(core)
    tree <- G.indexTreeC(core)
    _ <- if (root == payload.root && current.branch == payload.branch && current.head == payload.baseHead &&
      payload.state.candidate.exists(c => c.head == current.head && c.tree == tree) &&
      dirtyCovers(payload, current.dirty) && current.dirty.filter(d => S.owned(d.path, payload.state.input.ownedPaths)).forall(_.worktree == ' ')) Consequence.unit
      else invalid("Actual candidate changed; previous checks cannot be accepted for different bytes")
  } yield ()

  private def dirtyCovers(payload: Payload, dirty: Vector[G.DirtyPath]): Boolean = {
    val owners = payload.state.input.ownedPaths
    dirty.filterNot(d => S.owned(d.path, owners)) == payload.unrelated.getOrElse(Vector.empty) &&
      dirty.filter(d => S.owned(d.path, owners)).forall(d => !d.unmerged && S.permitted(d.path, payload.state.input) &&
      d.renamedFrom.forall(S.permitted(_, payload.state.input)))
  }
  private[admission] def implementationFreshC(payload: Payload, core: ActionCall.Core): Consequence[Unit] = for {
    root <- G.rootC(core)
    actual <- G.observeC(core)
    _ <- if (root == payload.root && actual.branch == payload.branch && actual.head == payload.baseHead &&
      dirtyCovers(payload, actual.dirty)) Consequence.unit else invalid("Implementation crossed the retained target or unrelated work")
  } yield ()

  /** The logical selection must be registered in the actual CNCF host. The
    * Task never substitutes a Phase Step's commit selection or invents consent. */
  private def commitC(h: Host, store: WorkflowComponentStorage, from: InstanceRecord, payload: Payload,
    core: ActionCall.Core): Consequence[V.StandaloneFixView] = for {
    result <- if (recordPaths(h, payload).nonEmpty && payload.records.isEmpty) for {
      _ <- freshC(payload, core)
      selection <- GitSelectionId.parseC(wire)
      candidate <- payload.state.candidate.map(Consequence.success).getOrElse(invalid("Record preparation has no accepted candidate"))
      plan <- CompletionRecords.captureC(core, from.identity.value, wire, payload.state.input.requestIdentity, candidate.tree,
        (payload.state.receipts.map(r => "validation:" + r.identity) ++ payload.state.reviewReference.toVector.map(r => r.identity + "@" + r.revision) ++ payload.state.evidenceReferences.map(r => r.identity + "@" + r.revision)).distinct,
        recordPaths(h, payload), payload.state.input.ownedPaths, h.paths, selection)
      retained = payload.copy(records = Some(plan), state = payload.state.copy(stage = "RecordsReady", message = "Accepted work retained; exact completion records prepared before local closure"))
      prepared <- saveC(h, store, from, retained, core)
      result <- recordsC(h, store, prepared, retained, core)
    } yield result else commitPreparedC(h, store, from, payload, core)
  } yield result

  private def recordsC(h: Host, store: WorkflowComponentStorage, from: InstanceRecord, payload: Payload,
    core: ActionCall.Core): Consequence[V.StandaloneFixView] = for {
    plan <- payload.records.filter(p => CompletionRecords.valid(p) && p.session == from.identity.value && p.family == wire &&
      p.scope == payload.state.input.requestIdentity && p.files.map(_.path) == recordPaths(h, payload))
      .map(Consequence.success).getOrElse(invalid("Record application has no exact retained owner/selection"))
    _ <- implementationFreshC(payload, core)
    _ <- CompletionRecords.applyC(core, plan)
    tree <- G.indexTreeC(core)
    changes <- CompletionRecords.deltaC(core, plan, tree)
    _ <- CompletionRecords.admitActualC(core, plan, changes)
    candidate <- payload.state.candidate.filter(_.tree == plan.acceptedTree).map(Consequence.success)
      .getOrElse(invalid("Record application changed the accepted candidate"))
    next = payload.state.copy(stage = "CommitReady", candidate = Some(candidate.copy(tree = tree,
      paths = (candidate.paths ++ changes.map(_.path.value)).distinct)), message = "Exact completion records applied; acceptance and successful checks reused")
    retained = payload.copy(state = next)
    saved <- saveC(h, store, from, retained, core)
    result <- if (next.candidate.exists(_.paths.isEmpty)) {
      val unchanged = next.copy(stage = "NoChange")
      saveC(h, store, saved, retained.copy(state = unchanged), core).flatMap(r =>
        if (delegation) progressC(h, from.definition, store, r, retained.copy(state = unchanged), core, 0)
        else Consequence.success(view(r, unchanged, "Terminal", "Existing committed records and accepted no-change work retained")))
    } else commitPreparedC(h, store, saved, retained, core)
  } yield result

  private def commitPreparedC(h: Host, store: WorkflowComponentStorage, from: InstanceRecord, payload: Payload,
    core: ActionCall.Core): Consequence[V.StandaloneFixView] = for {
    _ <- freshC(payload, core)
    selection <- GitSelectionId.parseC(if (delegation) "delegate-task" else "goal-task")
    parent <- GitObjectId.parseC(payload.baseHead)
    checked <- E.executeGitC(core, GitOperation.CheckSelectedIndex(selection, parent)).flatMap(_.valueC)
    changes <- E.executeGitC(core, GitOperation.SelectedIndexChanges(selection, parent)).flatMap(_.valueC)
    plan <- if (delegation) CommitHygiene.planC(core, h.hygieneMode, checked,
      payload.state.input.ownedPaths, h.paths, payload.state.candidate.get.head, payload.hygiene.nonEmpty)
      else Consequence.success(CommitHygiene.Record(CommitHygiene.error, "clean", Vector.empty, Vector.empty, "Existing Task commit policy"))
    response <- if (delegation && checked.findingKeys.nonEmpty) for {
      prepared <- saveC(h, store, from, payload.copy(hygiene = Some(plan), state = payload.state.copy(
        stage = if (plan.outcome == "planned") "HygieneReady" else "CommitFailed", message = plan.diagnostic)), core)
      result <- if (plan.outcome == "planned") hygieneC(h, store, prepared, payload.copy(hygiene = Some(plan),
        state = payload.state.copy(stage = "HygieneReady")), core)
        else Consequence.success(view(prepared, payload.state, "Incomplete", plan.diagnostic))
    } yield result
    else for {
    _ <- if (checked.findingKeys.isEmpty && changes.map(_.path.value).toSet == payload.state.candidate.toVector.flatMap(_.paths).toSet &&
      changes.nonEmpty) Consequence.unit else invalid("Task commit selection differs from the reviewed actual delta")
    _ <- freshC(payload, core)
    next = payload.state.copy(stage = "CommitIntent", message = "Typed selected commit intent retained; observe before any retry")
    intent <- saveC(h, store, from, payload.copy(state = next), core)
    _ <- freshC(payload, core)
    actual <- E.executeGitC(core, GitOperation.CommitSelected(selection))
    p = actual.processResult
    observed = S.CommitExecution(actual.outcome.toString, actual.selectedPaths.map(_.value),
      WorkflowSbtCapture.termination(p), WorkflowSbtCapture.exitCode(p),
      WorkflowSbtCapture.text(p.stdout).value, WorkflowSbtCapture.text(p.stderr).value)
    state = next.copy(stage = actual.outcome match {
      case GitExecutionOutcome.Failed(_) => "CommitFailed"
      case _ => "CommitUnknown"
    }, commitExecution = Some(observed), message = "Actual typed commit result retained: " + actual.outcome.toString)
    saved <- saveC(h, store, intent, payload.copy(state = state), core)
    covered = if (delegation) actual.selectedPaths.nonEmpty &&
      actual.selectedPaths.forall(p => S.owned(p.value, payload.state.input.ownedPaths)) &&
      payload.state.candidate.toVector.flatMap(_.paths).forall(path => S.owned(path, actual.selectedPaths.map(_.value)))
      else actual.selectedPaths.map(_.value).toSet == payload.state.candidate.toVector.flatMap(_.paths).toSet
    response <- if (actual.outcome == GitExecutionOutcome.Succeeded && covered) reconcileC(h, store, saved, payload.copy(state = state), core)
      else Consequence.success(view(saved, state, "Incomplete", state.message))
    } yield response
  } yield response

  /** Reconcile a matching actual commit; never rerun a retained uncertain effect. */
  private def reconcileC(h: Host, store: WorkflowComponentStorage, from: InstanceRecord, payload: Payload,
    core: ActionCall.Core): Consequence[V.StandaloneFixView] = for {
    root <- G.rootC(core)
    observed <- G.observeC(core)
    response <- if (root == payload.root && observed.branch == payload.branch && observed.head != payload.baseHead &&
      payload.state.candidate.exists(_.tree == observed.tree) &&
      observed.dirty == payload.unrelated.getOrElse(Vector.empty)) for {
      commit <- G.commitC(core, observed.head)
      _ <- if (commit.parents == Vector(payload.baseHead)) Consequence.unit else invalid("Observed Task commit has different ancestry")
      next <- S.committedC(payload.state, S.Commit(commit.identity, payload.baseHead, commit.tree))
      saved <- saveC(h, store, from, payload.copy(state = next), core)
      retained <- loadPayloadC(store, saved.identity.value, saved.revision.value, core)
      response <- if (delegation) progressC(h, from.definition, store, saved, retained, core, 0)
        else Consequence.success(view(saved, retained.state, "Terminal", retained.state.message))
    } yield response
    else Consequence.success(view(from, payload.state, "Incomplete",
      "Task commit remains unresolved; actual failed/UNKNOWN execution, accepted implementation, checks and review are retained without replay"))
  } yield response

  private def hygieneC(h: Host, store: WorkflowComponentStorage, from: InstanceRecord, payload: Payload,
    core: ActionCall.Core): Consequence[V.StandaloneFixView] = for {
    plan <- payload.hygiene.filter(CommitHygiene.valid).map(Consequence.success).getOrElse(invalid("Missing retained hygiene plan"))
    _ <- implementationFreshC(payload, core)
    _ <- CommitHygiene.applyC(core, plan)
    tree <- G.indexTreeC(core)
    candidate <- payload.state.candidate.map(Consequence.success).getOrElse(invalid("Hygiene has no reviewed candidate"))
    next = payload.state.copy(stage = "CommitReady", candidate = Some(candidate.copy(tree = tree)), message = "Bounded CML correction prepared; one recheck before commit")
    retained = payload.copy(state = next, hygiene = Some(plan.copy(outcome = "corrected")))
    saved <- saveC(h, store, from, retained, core)
    result <- commitC(h, store, saved, retained, core)
  } yield result

  private def deliverC(h: Host, store: WorkflowComponentStorage, from: InstanceRecord, payload: Payload,
    core: ActionCall.Core): Consequence[V.StandaloneFixView] = for {
    contract <- payload.state.input.delegation.filter(DelegateTask.valid).map(Consequence.success).getOrElse(invalid("Delivery has no admitted contract"))
    observed <- G.observeC(core)
    _ <- if (observed.branch == payload.branch && observed.dirty == payload.unrelated.getOrElse(Vector.empty) &&
      payload.state.commit.fold(observed.head == payload.baseHead)(_.identity == observed.head)) Consequence.unit
      else invalid("Delivery source changed after accepted local closure")
    existing <- G.commitC(core, observed.head)
    commit = payload.state.commit.getOrElse(S.Commit(existing.identity, existing.parents.headOption.getOrElse(existing.identity), existing.tree))
    _ <- if (payload.state.candidate.exists(_.tree == commit.tree)) Consequence.unit else invalid("No-change source is not the checked existing commit")
    delivery = payload.state.delivery.getOrElse(DelegateTask.Delivery())
    publication <- contract.publicationTarget match {
      case None => Consequence.success(Option.empty[LocalPublication.Result])
      case Some(target) => h.publication.map { policy => for {
        selected <- policy.targets.find(t => t.identity == target && t.component == contract.componentIdentity && t.workspace == "sm-workflow-repository")
          .map(Consequence.success).getOrElse(invalid("Publication target does not own this delegated Component/workspace"))
        result <- LocalPublicationRuntime.ensureDeliveryC(core, policy, DelegateTask.publicationInput(from.identity.value, payload.state))
      } yield Some(result) }.getOrElse(invalid("Required runtime-owned publication capability is not configured"))
    }
    ready = publication.forall(_.ready)
    next = payload.state.copy(stage = if (ready) "ReturnReady" else "PublicationWait", commit = Some(commit),
      delivery = Some(delivery.copy(publication = publication, destinationSessionId = Some(from.identity.value))), message = if (ready)
        "Accepted checks and local commit retained; actual required publication ready. Return this result to the named requester; adoption is separate"
        else "Local commit retained; publication unfinished: " + publication.get.status + "; " + publication.get.reason)
    saved <- if (next == payload.state) Consequence.success(from) else saveC(h, store, from, payload.copy(state = next), core)
  } yield view(saved, next, if (ready) "Return" else "Wait", next.message)

  private def requiredC(kind: String): Consequence[StateMachineRequiredOperation] = {
    val capability = wire + (if (kind == "Review") "-review-capability" else if (kind == "Implement") "-implementation-capability" else "-repair-capability")
    val operations = if (delegation) domain.statemachine.workflow.DelegateTaskWorkflowStateMachineWorkflow6.workflow.requiredOperations
      else if (task) domain.statemachine.workflow.GoalTaskWorkflowStateMachineWorkflow5.workflow.requiredOperations
      else domain.statemachine.workflow.StandaloneFixWorkflowStateMachineWorkflow4.workflow.requiredOperations
    val found = operations.filter(_.identity.capability == capability)
    if (found.size != 1) invalid("Standalone Required SPI is not uniquely derived")
    else {
      val spi = found.head
      Consequence.success(StateMachineRequiredOperation(StateMachineRequiredOperationIdentity(capability), spi.actionIdentity,
        StateMachineOperationIdentity(spi.operation.service, spi.operation.operation),
        spi.inputType.map(v => StateMachineInputTypeReference(v.value)), spi.resultType.map(v => StateMachineResultTypeReference(v.value)),
        StateMachineRequiredOperationMetadata(ContextContract(capability + ":context", Vector.empty, Vector.empty),
          CompletionContract(capability + ":completion", Vector.empty), EvidenceContract(capability + ":evidence", Vector.empty), Vector.empty)))
    }
  }
  private def issueC(h: Host, store: WorkflowComponentStorage, from: InstanceRecord, payload: Payload,
    core: ActionCall.Core): Consequence[V.StandaloneFixView] = for {
    _ <- payload.supersededWork.fold(Consequence.unit)(old =>
      GoalTaskMalformedIssuedWorkRecovery.correctionCurrentC(h, store, old, from.identity.value, core))
    _ <- if (payload.state.stage == "Implement") implementationFreshC(payload, core) else freshC(payload, core)
    required <- requiredC(payload.state.stage)
    work = S.Work(from.identity.value, from.revision.value.toString, payload.state.stage, payload.state)
    bundle = ContextBundle("Return only the assigned semantic result within the adopted objective and ownership. Native runtime owns validation, limits and acceptance. Review is independent and read-only; never execute commands or commit. If a prior review exists, judge only its retained findings, repair delta against priorReviewCandidate and affected meaning; preserve settled scope and applicable successful checks. Review-origin input retains the original review/context/findings: repair only these findings and affected meaning, return exact reviewDispositions and never repeat the original whole-repository review.",
      (payload.state.input.sourceReferences ++ payload.state.input.reviewOrigin.toVector.flatMap(StandaloneReviewFix.references) ++ payload.state.evidenceReferences).distinct,
      payload.supersededWork.toVector.map(id => ContextReference(id, "1")),
      ContextSnapshot(from.definition.workflowRevision.value, evidenceRevision = Some(work.sourceRevision)))
    continuation = Continuation(StateMachineRunIdentity(from.identity.value),
      ContinuationIdentity(from.identity.value + ":standalone:" + from.revision.next.value), StateMachineRevision(from.revision.next.value.toString), required, bundle)
    historyentry = HistoryEntry(HistorySequence(from.history.size.toLong + 1), from.revision.next, Lifecycle.Active,
      from.currentProgression, from.correlation, CausationReference("standalone-issued:" + work.kind),
      from.derivedCompositeOccurrence, from.committedPredecessor,
      Some(SuspensionBoundary(NativeIdentity(continuation.continuationId.value), from.revision.next,
        ContextSnapshotReference(bundle.snapshot.workflowRevision), CompletionReference(required.metadata.completionContract.identity),
        EvidenceReference(required.metadata.evidenceContract.identity), from.correlation)))
    handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, from)
    requirement <- executionRequirementC(payload.state.input, work.kind)
    interaction = WorkflowInteraction[S.Work, Nothing](handle, WorkflowContinuation.WorkOrder(
      ContinuationRequest(continuation.runId, continuation.continuationId, continuation.expectedRevision, required.identity,
        required.operation, Some(TypedValue(inputCodec.typeIdentity, work)), required.resultType, bundle,
        required.metadata.completionContract, required.metadata.evidenceContract, completionOperation),
      requirement, MinimalPresentation(entry + " " + work.kind, payload.state.input.objective)))
    selected <- GoalPhaseNativeExecution.bindIssuedC(interaction, h.profiles, h.origin)
    issued <- store.bindIssuedC(completionOperation, inputCodec)
    _ <- issued.suspendC(WorkflowInstanceAtomicTransitionV1.SuspensionIntent(WorkflowInstanceAtomicTransitionV1.schemaVersion,
      h.configuration, from, from.revision, historyentry, ContinuationRuntimePersistence.Record(continuation, ContinuationRuntimePersistence.Status.Available)), selected)(using core.executionContext)
    pending <- from.appendC(from.revision, historyentry)
    response <- requestViewC(pending, selected, core)
  } yield response

  /** Adapt only confirmed native outcomes to the existing Evidence vocabulary.
    * The immutable raw receipt/result remains the archived content. Incomplete,
    * unverified and nonexecuted checks have no passed/failed projection.
    */
  private[admission] def recordCheckEvidenceC(h: Host, core: ActionCall.Core, session: String,
    revision: Long, receipt: RepositorySyncNativeMergeValidation.Receipt): Consequence[Vector[ContextReference]] = {
    val outcome = receipt.sbt.flatMap { facts => facts.outcome match {
      case WorkflowSbtValidation.Outcome.Succeeded if
          RepositorySyncNativeMergeValidation.productSuccessful(receipt, facts.kind) => Some("passed")
      case WorkflowSbtValidation.Outcome.Failed if receipt.termination == "exited" && receipt.exitCode.exists(_ != 0) => Some("failed")
      case _ => None
    }}
    outcome.fold(Consequence.success(Vector.empty[ContextReference]))(value =>
      recordEvidenceC(h, core, session, revision, receipt.asJson, receipt.identity, value))
  }

  private[admission] def recordSemanticEvidenceC(h: Host, core: ActionCall.Core, session: String,
    revision: Long, kind: String, result: S.Result): Consequence[Vector[ContextReference]] = {
    val outcome = result.outcome match {
      case "completed" | "accept" => Some("passed")
      case "repair" => Some("failed")
      case _ => None // An unresolved decision does not establish success or failure.
    }
    outcome.fold(Consequence.success(Vector.empty[ContextReference]))(value =>
      recordEvidenceC(h, core, session, revision, result.asJson, "semantic-" + kind, value))
  }

  /** Allocation identity is retained independently of File availability. A
    * Journal row can faithfully record conflict/uncertainty without proving that
    * the captured content was stored. Idempotent Journal collection is success.
    */
  private[admission] def projectionWarning(storageStatus: String, journalStatus: Option[String]): Option[String] = {
    val storage = storageStatus match {
      case "stored" => None
      case "conflict" => Some("Common Evidence storage conflict; captured content is not confirmed stored")
      case "indeterminate" => Some("Common Evidence storage is indeterminate; captured content is not confirmed stored")
      case _ => Some("Common Evidence storage outcome is unrecognized; captured content is not confirmed stored")
    }
    val journal = journalStatus match {
      case Some("recorded" | "alreadyRecorded") => None
      case None => Some("Common Journal projection is not installed")
      case Some(status) => Some("Journal projection is " + status)
    }
    (storage, journal) match {
      case (None, None) => None
      case (None, Some(message)) if journalStatus.isEmpty => Some(message)
      case (None, Some(message)) => Some("Common Evidence stored; " + message)
      case (Some(message), None) => Some(message)
      case (Some(message), Some(detail)) => Some(message + "; " + detail)
    }
  }

  private final case class Projection(references: Vector[ContextReference], warning: Option[String])
  private def recordEvidenceC(h: Host, core: ActionCall.Core, session: String, revision: Long,
    facts: Json, output: String, outcome: String): Consequence[Vector[ContextReference]] =
    recordEvidenceObservedC(h, core, session, revision, facts, output, outcome).map(_.references)

  private def recordEvidenceObservedC(h: Host, core: ActionCall.Core, session: String, revision: Long,
    facts: Json, output: String, outcome: String): Consequence[Projection] = h.evidence match {
    case None => Consequence.success(Projection(Vector.empty, Some("Common Evidence projection is not installed")))
    case Some(policy) => try {
      val source = CommonEvidence.Source(ContextReference(session + ":standalone:" + output + ":" + revision, "1"),
        h.repository, wire, None, session, output, outcome)
      CommonEvidence.archiveManagedC(CommonEvidence.storage(core, policy), policy, source,
        facts.noSpaces, "identity", core.executionContext.clock).flatMap {
        case (_, Some((allocation, observation))) =>
          DevelopmentJournalEvidence.recordC(h.journal, core, allocation, observation)
            .map(journal => Projection(Vector(ContextReference(allocation.identity, "1")),
              projectionWarning(observation.status, Option.when(h.journal.nonEmpty)(journal.status))))
        case _ => Consequence.success(Projection(Vector.empty, Some("Common Evidence archive returned no allocation")))
      }.recoverWith(_ => Consequence.success(Projection(Vector.empty,
        Some("Common Evidence or Journal projection failed; actual execution and commit remain retained"))))
    } catch { case scala.util.control.NonFatal(_) => Consequence.success(Projection(Vector.empty,
      Some("Common Evidence or Journal projection failed; actual execution and commit remain retained"))) }
  }

  def requestViewC(record: InstanceRecord, actual: WorkflowInteraction[S.Work, Nothing], core: ActionCall.Core): Consequence[V.StandaloneFixView] = actual.current match {
    case order: WorkflowContinuation.WorkOrder[S.Work] => for {
      work <- order.request.input.map(v => Consequence.success(v.value)).getOrElse(invalid("Actual work input missing"))
      _ <- GoalPhaseParticipantAdmission.admitC(work.state.participant, core.executionContext)
      context <- NativeWorkflowIssuedWorkContext.fromIssuedC(actual,
        if (work.kind == "Review") NativeWorkflowIssuedWorkContext.Mode.Review else NativeWorkflowIssuedWorkContext.Mode.Implementation, inputCodec)
      review <- NativeReviewContext.standaloneC(core, actual)
      raw <- recordC(work.asJson)
    } yield view(record, work.state, "Request", work.state.message).copy(work = Some(raw), issuedWorkContext = Some(context),
      submitOperation = Some(completionOperation.operation), submitSessionId = Some(record.identity.value), resultType = Some(resultCodec.typeIdentity), reviewContext = review)
    case _ => invalid("Native pending continuation is not standalone semantic work")
  }
  def view(record: InstanceRecord, state: S.State, kind: String, message: String): V.StandaloneFixView =
    V.StandaloneFixView(record.identity.value, kind, message, recordC(state.asJson).toOption, None, None, None, None, None,
      S.diagnostics(state, record.revision.value.toString).flatMap(d => recordC(d.asJson).toOption), None)
  def recordC(json: Json): Consequence[org.goldenport.record.Record] = new org.goldenport.record.io.RecordDecoder().json(json.noSpaces)
  def invalid[A](message: String): Consequence[A] = Consequence.stateConflict((if (task) "GoalTask native work: " else "Standalone native fix: ") + message)
}
