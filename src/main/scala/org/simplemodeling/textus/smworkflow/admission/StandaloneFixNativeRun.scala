package org.simplemodeling.textus.smworkflow.admission

import org.simplemodeling.textus.smworkflow.admission.StandaloneFixExecution.given

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.processexecution.{GitObjectId, GitOperation, SbtExecutionAttempt}
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as NativeIdentity, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import StandaloneFixExecution as S
import RepositorySyncManagedGitObservation as G
import WorkflowManagedProcessExecution as E

/** Standalone business ownership over canonical CNCF persistence and execution.
  * No Phase Start, Plan, Step acceptance, commit or private command driver.
  * @since   Oct. 26, 2026
  * @version Oct. 26, 2026
  */
private[smworkflow] object StandaloneFixNativeRun {
  val startOperation = StateMachineOperationIdentity("StandaloneFixService", "startStandaloneFix")
  val advanceOperation = StateMachineOperationIdentity("StandaloneFixService", "advanceStandaloneFix")
  val completionOperation = StateMachineOperationIdentity("StandaloneFixService", "submitStandaloneFixWorkResult")
  val closingType = StateMachineResultTypeReference("StandaloneFixView")
  final case class Host(configuration: Configuration, instances: DataStore.CollectionId,
    continuations: DataStore.CollectionId, repository: String, root: String, branch: String,
    paths: Vector[String], checks: Vector[GoalPhaseNativeValidation.Check],
    profiles: Option[WorkflowExecutionProfileMapping], origin: Option[GoalPhaseNativeExecution.Origin],
    review: Option[GoalPhaseNativeReview.EvidencePort],
    evidence: Option[CommonEvidence.Policy], journal: Option[DevelopmentJournal.Policy])
  def host(d: GoalPhaseNativeRun.Declaration): Host = Host(d.configuration, d.instances, d.continuations,
    d.input.repositoryIdentity, d.target.root.toString, d.target.localBranch, d.candidatePaths,
    d.validationPolicy.toVector.flatMap(_.checks), d.executionProfiles, d.executionOrigin, d.reviewInstallation.map(_.evidence), d.evidencePolicy, d.journalPolicy)
  final case class Payload(state: S.State, root: String, branch: String, baseHead: String,
    implementationWorker: Option[String] = None)
  given Encoder[Payload] = deriveEncoder
  given Decoder[Payload] = deriveDecoder
  def codec[A: Encoder: Decoder](identity: String): WorkflowResultJsonV1.PayloadCodec[A] =
    new WorkflowResultJsonV1.PayloadCodec[A] {
      val typeIdentity = identity
      def encode(value: A) = Json.obj("schemaVersion" -> Json.fromString("sm-workflow.standalone-fix.v1"), "payload" -> value.asJson)
      def decode(value: Json): Either[String, A] = for {
        version <- value.hcursor.get[String]("schemaVersion").left.map(_.message)
        _ <- Either.cond(version == "sm-workflow.standalone-fix.v1", (), "Standalone schema differs")
        payload <- value.hcursor.get[A]("payload").left.map(_.message)
      } yield payload
    }
  val stateCodec = codec[Payload]("StandaloneFixNativeState")
  val inputCodec = codec[S.Work]("StandaloneFixWorkRequest")
  val resultCodec = codec[S.Result]("StandaloneFixWorkResult")
  private def reference(id: String, revision: Long) = WorkflowMechanicalTransition.Reference(id + ":standalone-state:" + revision, "1")
  private def empty = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](())
  def storageC(h: Host, definition: DefinitionBinding, core: ActionCall.Core): Consequence[WorkflowComponentStorage] =
    WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
      SmWorkflowComponent.componentId, h.configuration, Vector(definition), h.instances, h.continuations)
  def history(from: InstanceRecord, cause: String, accepted: Boolean = false): HistoryEntry =
    HistoryEntry(HistorySequence(from.history.size.toLong + 1), from.revision.next, if (accepted) Lifecycle.Completed else Lifecycle.Active,
      if (accepted) None else Some(ProgressionReference("fix.Running")), from.correlation,
      CausationReference(cause), from.derivedCompositeOccurrence, from.committedPredecessor, None)
  def saveC(h: Host, storage: WorkflowComponentStorage, from: InstanceRecord, payload: Payload,
    core: ActionCall.Core): Consequence[InstanceRecord] = for {
    mechanical <- storage.bindMechanicalC(advanceOperation, stateCodec)
    saved <- mechanical.executeC(WorkflowMechanicalTransition.Intent(h.configuration,
      reference(from.identity.value, from.revision.next.value), from, from.revision,
      history(from, "standalone-state:" + from.revision.next.value, Set("Accepted", "NoChange")(payload.state.stage)),
      TypedValue(stateCodec.typeIdentity, payload)), empty)(using core.executionContext)
    _ <- if (Set("Accepted", "NoChange")(payload.state.stage)) recordEvidenceC(h, core,
      saved.receipt.next.identity.value, saved.receipt.next.revision.value,
      Json.obj("state" -> payload.state.asJson, "workflowRevision" -> Json.fromLong(saved.receipt.next.revision.value)),
      "completion", payload.state.stage).map(_ => ()) else Consequence.unit
  } yield saved.receipt.next
  def loadPayloadC(storage: WorkflowComponentStorage, id: String, revision: Long,
    core: ActionCall.Core): Consequence[Payload] = for {
    port <- storage.bindMechanicalC(advanceOperation, stateCodec)
    saved <- port.loadRetainedC(reference(id, revision))(using core.executionContext)
    payload <- saved.map(x => Consequence.success(x.receipt.payload.value))
      .getOrElse(invalid("Current frontier has no canonical standalone checkpoint"))
  } yield payload

  def startC(input: S.Input, participant: String, key: String, selection: String,
    selections: ProfileStartAdmission.CallScopedSelectionPort, h: Host, definition: DefinitionBinding,
    core: ActionCall.Core): Consequence[V.StandaloneFixView] = for {
    _ <- S.inputC(input)
    _ <- GoalPhaseParticipantAdmission.admitC(participant, core.executionContext)
    _ <- if (input.repositoryIdentity == h.repository && input.ownedPaths.forall(p => h.paths.contains(p))) Consequence.unit
      else invalid("Requested repository/paths exceed the configured host bindings")
    selected = input.checks.flatMap(id => h.checks.find(_.identity == id))
    state <- S.startC(input, participant, selected)
    binding = ProfileStartAdmission.Binding(startOperation, definition.workflowIdentity, definition.workflowRevision, "StandaloneFixInput")
    // Freeze one actual managed read, so metadata admission and complete-input
    // equality cannot accidentally consume two different selection observations.
    chosen <- selections.loadForCallC(selection, core)
    actualSelection = new ProfileStartAdmission.SelectionPort {
      def loadC(id: String) = if (id == selection) Consequence.success(chosen)
        else invalid("Selection read crossed its actual managed identity")
    }
    _ <- ProfileStartAdmission.admitC(binding, TypedValue("StandaloneFixInput", input.asJson), selection, participant, key, actualSelection)
    _ <- if (chosen.flatMap(_.selectedInput).exists(v => v.typeIdentity == "StandaloneFixInput" && v.value == input.asJson))
      Consequence.unit else invalid("Actual human selection does not bind this complete standalone request")
    root <- G.rootC(core)
    observed <- G.observeC(core)
    _ <- if (root == h.root && observed.branch == h.branch && observed.dirty.forall(d =>
      S.owned(d.path, input.ownedPaths) && d.renamedFrom.forall(S.owned(_, input.ownedPaths)))) Consequence.unit
      else invalid("Start preserves unrelated work by refusing a crossed repository or dirty scope")
    identity <- core.executionContext.security.session.flatMap(_.sessionId).map(x => Consequence.success(InstanceIdentity(x)))
      .getOrElse(invalid("Standalone Start requires its canonical session"))
    store <- storageC(h, definition, core)
    instances <- store.bindInstancesC()(using core.executionContext)
    initial <- InstanceRecord(identity, definition, initialRevision, Lifecycle.NotStarted, None,
      CorrelationReference(key), CausationReference("standalone-started"), None, None, None, Vector.empty).validateC
    _ <- instances.createC(initial)(using core.executionContext)
    active <- instances.appendC(identity, initial.revision, history(initial, "standalone-started"))(using core.executionContext)
    saved <- saveC(h, store, active, Payload(state, root, observed.branch, observed.head), core)
  } yield view(saved, state, "Started", "Standalone request retained; normal Advance owns the selected checks")

  def advanceC(id: String, h: Host, definition: DefinitionBinding, core: ActionCall.Core): Consequence[V.StandaloneFixView] = for {
    storage <- storageC(h, definition, core)
    instances <- storage.bindInstancesC()(using core.executionContext)
    current <- instances.loadC(InstanceIdentity(id))(using core.executionContext).flatMap {
      case Some(value) if value.definition == definition => Consequence.success(value)
      case _ => invalid("No standalone instance belongs to this exact definition")
    }
    result <- current.suspension match {
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
            references <- recordEvidenceC(h, core, id, current.revision.value, done.result.value.asJson,
              "semantic-" + payload.state.stage, done.result.value.outcome)
            retained = next.copy(evidenceReferences = next.evidenceReferences ++ references)
            saved <- saveC(h, storage, current, payload.copy(state = retained,
              implementationWorker = if (payload.state.stage == "Repair") done.evidence.workerIdentity else payload.implementationWorker), core)
          } yield saved
        } else Consequence.success(current)
        payload <- loadPayloadC(storage, id, state.revision.value, core)
        _ <- GoalPhaseParticipantAdmission.admitC(payload.state.participant, core.executionContext)
        response <- progressC(h, definition, storage, state, payload, core, 0)
      } yield response
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
            references <- recordEvidenceC(h, core, record.identity.value, savedIntent.revision.value, receipt.asJson,
              check.identity, facts.outcome.wire)
            next = observed.copy(evidenceReferences = observed.evidenceReferences ++ references)
            saved <- saveC(h, store, savedIntent, payload.copy(state = next), core)
            response <- progressC(h, definition, store, saved, payload.copy(state = next), core, depth + 1)
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
      case "Repair" | "Review" => issueC(h, store, record, payload, core)
      case "Accepted" | "NoChange" => Consequence.success(view(record, s, "Terminal", s.message))
      case "Human" => Consequence.success(view(record, s, "Human", s.message))
      case _ => Consequence.success(view(record, s, "Wait", s.message + "; retained intent is not executable permission"))
    }
  }

  private def captureC(h: Host, payload: Payload, core: ActionCall.Core): Consequence[S.Candidate] = for {
    before <- G.observeC(core)
    root <- G.rootC(core)
    _ <- if (root == payload.root && before.branch == payload.branch && before.head == payload.baseHead &&
      before.dirty.forall(d => S.owned(d.path, payload.state.input.ownedPaths) && d.renamedFrom.forall(S.owned(_, payload.state.input.ownedPaths))))
      Consequence.unit else invalid("Candidate observation crosses the adopted root, HEAD or ownership")
    _ <- payload.state.input.ownedPaths.foldLeft(Consequence.unit) { (previous, path) => previous.flatMap(_ =>
      E.stageGoalCandidateC(core, h.paths.indexOf(path) + 1).flatMap(_.valueC).map(_ => ())) }
    head <- GitObjectId.parseC(payload.baseHead)
    selected <- E.executeGitC(core, GitOperation.PendingPaths(head)).flatMap(_.valueC)
    diff <- E.executeGitC(core, GitOperation.PendingDiff(head)).flatMap(_.valueC)
    tree <- G.indexTreeC(core)
    after <- G.observeC(core)
    _ <- if (after.head == before.head && after.dirty.forall(_.worktree == ' ') &&
      selected.forall(p => S.owned(p.value, payload.state.input.ownedPaths))) Consequence.unit
      else invalid("Candidate changed during observation or contains unrelated staged work")
  } yield S.Candidate(before.head, tree, selected.map(_.value), diff.text)

  private[admission] def freshC(payload: Payload, core: ActionCall.Core): Consequence[Unit] = for {
    root <- G.rootC(core)
    current <- G.observeC(core)
    tree <- G.indexTreeC(core)
    _ <- if (root == payload.root && current.branch == payload.branch && current.head == payload.baseHead &&
      payload.state.candidate.exists(c => c.head == current.head && c.tree == tree) &&
      current.dirty.forall(d => d.worktree == ' ' && S.owned(d.path, payload.state.input.ownedPaths))) Consequence.unit
      else invalid("Actual candidate changed; previous checks cannot be accepted for different bytes")
  } yield ()

  private def requiredC(kind: String): Consequence[StateMachineRequiredOperation] = {
    val capability = if (kind == "Review") "standalone-fix-review-capability" else "standalone-fix-repair-capability"
    val found = domain.statemachine.workflow.StandaloneFixWorkflowStateMachineWorkflow4.workflow.requiredOperations.filter(_.identity.capability == capability)
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
    _ <- freshC(payload, core)
    required <- requiredC(payload.state.stage)
    work = S.Work(from.identity.value, from.revision.value.toString, payload.state.stage, payload.state)
    bundle = ContextBundle("Return only the assigned semantic result within the adopted objective and ownership. Native runtime owns validation, limits and acceptance. Review is independent and read-only; never execute commands or commit. If a prior review exists, judge only its retained findings, repair delta against priorReviewCandidate and affected meaning; preserve settled scope and applicable successful checks.",
      (payload.state.input.sourceReferences ++ payload.state.evidenceReferences).distinct, Vector.empty,
      ContextSnapshot(from.definition.workflowRevision.value, evidenceRevision = Some(work.sourceRevision)))
    continuation = Continuation(StateMachineRunIdentity(from.identity.value),
      ContinuationIdentity(from.identity.value + ":standalone:" + from.revision.next.value), StateMachineRevision(from.revision.next.value.toString), required, bundle)
    entry = HistoryEntry(HistorySequence(from.history.size.toLong + 1), from.revision.next, Lifecycle.Active,
      from.currentProgression, from.correlation, CausationReference("standalone-issued:" + work.kind),
      from.derivedCompositeOccurrence, from.committedPredecessor,
      Some(SuspensionBoundary(NativeIdentity(continuation.continuationId.value), from.revision.next,
        ContextSnapshotReference(bundle.snapshot.workflowRevision), CompletionReference(required.metadata.completionContract.identity),
        EvidenceReference(required.metadata.evidenceContract.identity), from.correlation)))
    handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, from)
    interaction = WorkflowInteraction[S.Work, Nothing](handle, WorkflowContinuation.WorkOrder(
      ContinuationRequest(continuation.runId, continuation.continuationId, continuation.expectedRevision, required.identity,
        required.operation, Some(TypedValue(inputCodec.typeIdentity, work)), required.resultType, bundle,
        required.metadata.completionContract, required.metadata.evidenceContract, completionOperation),
      ExecutionRequirement(Vector(CapabilityRequirement(required.identity.capability)), RiskLevel("SOURCE_IMPLEMENTATION"),
        ReasoningLevel.Standard, work.kind == "Review"), MinimalPresentation("Standalone " + work.kind, payload.state.input.objective)))
    selected <- GoalPhaseNativeExecution.bindIssuedC(interaction, h.profiles, h.origin)
    issued <- store.bindIssuedC(completionOperation, inputCodec)
    _ <- issued.suspendC(WorkflowInstanceAtomicTransitionV1.SuspensionIntent(WorkflowInstanceAtomicTransitionV1.schemaVersion,
      h.configuration, from, from.revision, entry, ContinuationRuntimePersistence.Record(continuation, ContinuationRuntimePersistence.Status.Available)), selected)(using core.executionContext)
    pending <- from.appendC(from.revision, entry)
    response <- requestViewC(pending, selected, core)
  } yield response

  private def recordEvidenceC(h: Host, core: ActionCall.Core, session: String, revision: Long,
    facts: Json, output: String, outcome: String): Consequence[Vector[ContextReference]] = h.evidence match {
    case None => Consequence.success(Vector.empty)
    case Some(policy) => try {
      val source = CommonEvidence.Source(ContextReference(session + ":standalone:" + output + ":" + revision, "1"),
        h.repository, "standalone-fix", None, session, output, outcome)
      CommonEvidence.archiveManagedC(CommonEvidence.storage(core, policy), policy, source,
        facts.noSpaces, "identity", core.executionContext.clock).flatMap {
        case (_, Some((allocation, observation))) =>
          DevelopmentJournalEvidence.recordC(h.journal, core, allocation, observation)
            .map(_ => Vector(ContextReference(allocation.identity, "1")))
        case _ => Consequence.success(Vector.empty)
      }.recoverWith(_ => Consequence.success(Vector.empty))
    } catch { case scala.util.control.NonFatal(_) => Consequence.success(Vector.empty) }
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
  def invalid[A](message: String): Consequence[A] = Consequence.stateConflict("Standalone native fix: " + message)
}
