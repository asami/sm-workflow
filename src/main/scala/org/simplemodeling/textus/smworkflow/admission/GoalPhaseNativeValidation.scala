package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import GoalPhaseDependencyValidation.given
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.processexecution.{SbtExecutionAttempt, SbtOperation, SbtTarget}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import GoalPhaseNativeRun as F
import GoalPhaseOrderedProgression as O
import RepositorySyncManagedGitObservation as G
import WorkflowManagedProcessExecution as E
import RepositorySyncNativeMergeValidation as V
import GoalPhaseNativeCandidateBoundary as B
import org.goldenport.cncf.processexecution.{GitObjectId, GitOperation}
import GoalPhaseNativeMetadata as N
import GoalPhaseSliceValidationPlan as L
import org.goldenport.cncf.validation.ValidationDiscoveryV1 as D

/** Selected native checks only. Executables/argv are fixed in runtime programs;
  * plan check identities select host bindings, never commands from AI. Intent
  * commits precede each external effect. Unknown results are not replayed.
  * @since   Oct.  6, 2026
  * @version Oct. 27, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseNativeValidation {
  final case class Check(identity: String, ordinal: Int, kind: WorkflowSbtValidation.Kind = WorkflowSbtValidation.Kind.Test,
    metadata: Option[N.Profile] = None, hostIdentity: Option[String] = None,
    operation: Option[D.OperationReference] = None,
    dependency: Option[String] = None)
  object Check {
    import N.given
    given Encoder[Check] = Encoder.instance(value => {
      val fields = Json.obj("identity" -> value.identity.asJson, "ordinal" -> value.ordinal.asJson)
      val kind = if (value.kind == WorkflowSbtValidation.Kind.Test) fields else fields.mapObject(_.add("kind", value.kind.asJson))
      val metadata = value.metadata.fold(kind)(m => kind.mapObject(_.add("metadata", m.asJson)))
      val host = value.hostIdentity.fold(metadata)(h => metadata.mapObject(_.add("hostIdentity", h.asJson)))
      val operation = value.operation.fold(host)(o => host.mapObject(_.add("operation", o.asJson)))
      value.dependency.fold(operation)(d => operation.mapObject(_.add("dependency", d.asJson)))
    })
    given Decoder[Check] = Decoder.instance(cursor => for {
      identity <- cursor.get[String]("identity")
      ordinal <- cursor.get[Int]("ordinal")
      kind <- WorkflowSbtValidation.checkKind(cursor)
      metadata <- N.optional[N.Profile](cursor, "metadata")
      host <- N.optional[String](cursor, "hostIdentity")
      operation <- N.optional[D.OperationReference](cursor, "operation")
      dependency <- N.optional[String](cursor, "dependency")
    } yield Check(identity, ordinal, kind, metadata, host, operation, dependency))
  }
  final case class Policy(identity: String, revision: Int, checks: Vector[Check]) {
    def validateC: Consequence[Unit] =
      if (id(identity) && revision > 0 && checks != null && checks.nonEmpty && checks.size <= 16 &&
          checks.forall(c => c != null && id(c.identity) && c.kind != null && c.ordinal > 0 && c.ordinal <= 16 &&
            c.metadata != null && c.hostIdentity != null && c.operation != null && c.dependency != null && c.dependency.isEmpty && c.hostIdentity.isEmpty && c.operation.isEmpty &&
            c.metadata.forall(p => c.kind == WorkflowSbtValidation.Kind.TestOnly && N.profileValid(p))) &&
          checks.map(_.identity).distinct.size == checks.size && checks.map(_.ordinal).distinct.size == checks.size)
        Consequence.unit else F.invalid("native validation host bindings are incomplete")
  }
  final case class AdmissionFailure(checkIdentity: String, attempt: Int, diagnostic: String)
  final case class Uncertainty(checkIdentity: String, attempt: Int, diagnostic: String)
  final case class NewAttempt(checkIdentity: String, attempt: Int, intentRevision: String,
    evidenceIdentity: String, evidenceRevision: String, participant: String, rationale: String)
  final case class Payload(sourceRevision: String, step: String, candidate: O.Snapshot,
    selected: Vector[Check], receipts: Vector[V.Receipt], outcome: Option[String], metadata: Vector[N.Batch] = Vector.empty,
    stopped: Vector[V.Receipt] = Vector.empty, admissionFailures: Vector[AdmissionFailure] = Vector.empty,
    uncertainties: Vector[Uncertainty] = Vector.empty, newAttempts: Vector[NewAttempt] = Vector.empty,
    dependency: Option[GoalPhaseDependencyValidation.Capture] = None,
    dependencyExecutions: Vector[GoalPhaseDependencyValidation.Execution] = Vector.empty,
    assessment: Option[GoalPhaseStepAssessment.Result] = None,
    dependencySource: Option[GoalPhaseDependencySourceRevision.Capture] = None,
    sourceConsumption: Option[GoalPhaseDependencySourceRevision.Consumption] = None)
  object Payload {
    given Encoder[Payload] = deriveEncoder[Payload].mapJson(value => value.mapObject(fields =>
      Vector("metadata", "stopped", "admissionFailures", "uncertainties", "newAttempts", "dependencyExecutions").foldLeft(fields) { (prior, key) =>
        if (prior(key).exists(_.asArray.exists(_.isEmpty))) prior.remove(key) else prior
      }.filter { case (key, value) => !Set("dependency", "assessment", "dependencySource", "sourceConsumption")(key) || !value.isNull }))
    private val _decoder: Decoder[Payload] = deriveDecoder
    given Decoder[Payload] = Decoder.instance { cursor =>
      val normalized = Vector("metadata", "stopped", "admissionFailures", "uncertainties", "newAttempts", "dependencyExecutions").foldLeft(cursor.value) { (prior, key) =>
        if (cursor.downField(key).focus.isEmpty) prior.mapObject(_.add(key, Json.arr())) else prior
      }
      _decoder.decodeJson(normalized).flatMap(value => if (!Vector("dependency", "dependencySource", "sourceConsumption").exists(name =>
          cursor.downField(name).focus.exists(_.isNull)) && _valid_payload(value)) Right(value)
        else Left(DecodingFailure("Invalid native validation payload", cursor.history)))
    }
  }
  private[admission] val codec = new WorkflowResultJsonV1.PayloadCodec[Payload] {
    val typeIdentity = "GoalPhaseNativeValidationReceipt"
    def encode(value: Payload): Json = Json.obj("schemaVersion" -> Json.fromString("sm-workflow.goal-phase-native-validation.v2"),
      "payload" -> value.asJson)
    def decode(value: Json): Either[String, Payload] = for {
      wire <- Option(value).toRight("native Goal validation is missing")
      schema <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(schema == "sm-workflow.goal-phase-native-validation.v2", (), "native Goal validation schema differs")
      payload <- wire.hcursor.get[Payload]("payload").left.map(_.message)
      _ <- Either.cond(_valid_payload(payload), (), "native Goal validation payload is invalid")
    } yield payload
  }
  final case class Saved(record: InstanceRecord, payload: Payload)
  private[admission] final case class Attempt(intent: Option[Saved], result: Option[Saved], recovery: Option[Saved] = None)
  private[admission] final case class CheckProgress(saved: Saved, nextAttempt: Option[Int])
  private[admission] final case class TerminalFailedAttempt(attempt: Int, intent: Saved, result: Saved, validation: Saved)
  private[admission] final case class OutputLimitedAttempt(attempt: Int, intent: Saved, result: Saved, validation: Saved)
  private[admission] enum TerminalOutcome {
    case Failed, TerminatedTimeout
  }
  private[admission] final case class TerminalAttempt(outcome: TerminalOutcome, attempt: Int,
    intent: Saved, result: Saved, validation: Saved)

  /** Same host-policy selection for fresh Validation and issued semantic context. */
  private[admission] def selectedChecksC(d: F.Declaration, plan: O.Plan,
    step: Option[String]): Consequence[Vector[Check]] = for {
    policy <- (if (step.isEmpty) d.phaseValidationPolicy else d.validationPolicy)
      .fold[Consequence[Policy]](F.invalid("selected host validation policy is absent"))(Consequence.success)
    _ <- policy.validateC
    identities <- step match {
      case None => Consequence.success(plan.phaseChecks)
      case Some(identity) => plan.steps.find(_.identity == identity).map(s => Consequence.success(s.checks))
        .getOrElse(F.invalid("selected validation Step is absent from the actual Plan"))
    }
    selected <- identities.foldLeft(Consequence.success(Vector.empty[Check])) { (prior, identity) => prior.flatMap { values =>
      policy.checks.find(_.identity == identity).fold[Consequence[Vector[Check]]](
        F.invalid("required plan check has no fixed host program: " + identity))(c => Consequence.success(values :+ c))
    }}
    _ <- if (step.flatMap(id => plan.steps.find(_.identity == id)).flatMap(_.actionPolicy).exists(_.kind == "code") &&
        !(selected.exists(_.kind == WorkflowSbtValidation.Kind.Compile) &&
          selected.exists(_.kind == WorkflowSbtValidation.Kind.TestOnly)))
      F.invalid("code Step policy requires fixed Compile and focused TestOnly programs before execution") else Consequence.unit
    _ <- if (step.flatMap(id => plan.steps.find(_.identity == id)).flatMap(_.actionPolicy).exists(_.scalaLint == "required") &&
        !selected.exists(_.kind == WorkflowSbtValidation.Kind.FixCheck))
      F.invalid("required Scala lint has no fixed FixCheck program before execution") else Consequence.unit
  } yield selected

  private[admission] def sourceInstructionsC(d: F.Declaration, plan: O.Plan,
    step: Option[String]): Consequence[String] = {
    val policy = if (step.isEmpty) d.phaseValidationPolicy else d.validationPolicy
    if (!policy.exists(_.checks.exists(_.metadata.exists(_.source.nonEmpty)))) Consequence.success("")
    else for {
      selected <- selectedChecksC(d, plan, step)
      owned = step.fold(plan.steps.flatMap(_.ownedPaths).distinct)(identity => plan.steps.find(_.identity == identity).get.ownedPaths)
      instructions <- L.instructionsC(selected, step.getOrElse("phase:" + plan.phase), owned, d.candidatePaths)
    } yield instructions
  }

  def retainedC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    candidate: GoalPhaseNativeCandidate.Saved, controller: O.State,
    from: Option[InstanceRecord] = None,
    documentPolicy: Option[GoalPhaseDocumentProgress.Policy] = None,
    recordingPaths: Vector[String] = Vector.empty,
    allowRepairDirection: Boolean = false,
    dependencyChain: Option[GoalPhaseNativeWorkContinuation.DependencyChain] = None): Consequence[Option[Saved]] = for {
    store <- _store_c(session, d, core, candidate, controller, _ => Consequence.unit, from, documentPolicy, recordingPaths,
      dependencyChain)
    retained <- store.loadC("validation")
    actual <- retained.fold(if (allowRepairDirection) store.pendingRepairSelectionC else store.pendingSelectionC)(x => store.completedC(x).map(Some(_)))
  } yield actual

  def preconditionsC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    candidate: GoalPhaseNativeCandidate.Saved, controller: O.State,
    from: Option[InstanceRecord] = None,
    documentPolicy: Option[GoalPhaseDocumentProgress.Policy] = None,
    recordingPaths: Vector[String] = Vector.empty,
    dependencyChain: Option[GoalPhaseNativeWorkContinuation.DependencyChain] = None): Consequence[Unit] =
    _store_c(session, d, core, candidate, controller, _ => Consequence.unit, from, documentPolicy, recordingPaths,
      dependencyChain).flatMap(_.preconditionsC)

  def advanceC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    candidate: GoalPhaseNativeCandidate.Saved, controller: O.State, acknowledgement: String => Consequence[Unit],
    from: Option[InstanceRecord] = None,
    documentPolicy: Option[GoalPhaseDocumentProgress.Policy] = None,
    recordingPaths: Vector[String] = Vector.empty,
    dependencyChain: Option[GoalPhaseNativeWorkContinuation.DependencyChain] = None): Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult] = for {
    store <- _store_c(session, d, core, candidate, controller, acknowledgement, from, documentPolicy, recordingPaths, dependencyChain)
    retained <- store.loadC("validation")
    existingintent <- store.loadC("check-intent-0")
    prerequisite <- if (retained.isEmpty && existingintent.isEmpty) LocalPublicationRuntime.beforeChecksC(session, d, core, candidate)
      else Consequence.success(None)
    response <- prerequisite match {
      case Some(waiting) => Consequence.success(waiting)
      case None => for {
    actual <- retained match {
      case Some(value) => store.completedC(value)
      case None => _checks_c(store)
    }
    evidence <- store.evidenceC(actual.payload)
    repairevidence <- if (controller.repairControl.nonEmpty && repairable(actual.payload) &&
        actual.payload.outcome.exists(Set("repair-required", "repair-limit-reached"))) for {
      inspected <- if (controller.candidate.contains(actual.payload.candidate)) Consequence.success(controller)
        else GoalPhaseNativeCandidate.observedStateC(controller, candidate.payload, documentPolicy, recordingPaths)
      reference = session.current.identity.value + ":ordered-validation:" + actual.payload.sourceRevision + ":validation"
      state <- observedStateC(inspected, actual.payload, reference, candidate.payload.changedPaths)
      recorded <- GoalPhaseRuntimeRepairControl.recordC(d, core, session.current.identity.value,
        ContextReference(reference, "1"), "failed", GoalPhaseRuntimeRepairControl.facts(state))
    } yield recorded else Consequence.success(Vector.empty[org.simplemodeling.textus.smworkflow.value.EvidenceRecordingState])
    journal <- DevelopmentJournalAssessment.recordC(d, core, session.current.identity.value, actual.payload)
    wire <- F.waitC(actual.record, if (controller.cursor == controller.plan.steps.size)
      "Whole-Phase native validation" else "Ordered Step native validation", actual.payload.outcome match {
      case Some("passed") if actual.payload.assessment.exists(_.branch == "admit") =>
        "Actual checks passed and formal Step admission is retained. Triage and detailed review were not executed; native commit authority and closure remain separate."
      case Some("passed") if actual.payload.assessment.exists(_.branch == "triage") =>
        "Actual required native checks passed; selected semantic uncertainty requires bounded triage. Independent review and closure are not accepted."
      case Some("passed") if actual.payload.assessment.exists(_.branch == "decision") =>
        "Actual checks passed; the retained immediate observation requires judgment before continuation."
      case Some("passed") => "Actual required native checks passed. Independent review work, acceptance and Step/Phase closure remain unfinished."
      case Some("repair-required") => "Actual terminated selected Step check failure is retained for assigned semantic repair. Fresh native inspection, checks and independent review remain unfinished."
      case Some("repair-limit-reached") => "Actual terminated selected Step check failure exhausted the positive repair window. Only verified Human ContinueRepairs can renew that window; the original diagnostics remain retained."
      case Some("failed") => "Actual failed or indeterminate checks are retained at Decision. No automatic replay or review acceptance."
      case Some("incomplete") => "Actual output-limited incomplete checks and retained raw prefixes are saved at Decision. Describe validation repair for a concrete independently admitted corrective Plan."
      case _ if continuable(actual) && actual.payload.stopped.lastOption.exists(r =>
          actual.payload.selected.lift(actual.payload.receipts.size).exists(_.identity == r.identity)) =>
        "The selected native check is confirmed not-executed after bounded runtime startup recovery; no product result exists. After preparation correction, the next ordinary Advance uses the standing selected scope."
      case _ if actual.payload.admissionFailures.lastOption.exists(f =>
          actual.payload.selected.lift(actual.payload.receipts.size).exists(_.identity == f.checkIdentity)) =>
        "The owning UoW rejected admission before execution: " + actual.payload.admissionFailures.last.diagnostic +
          ". After host preparation correction, the next ordinary Advance creates a new selected check attempt."
      case _ if actual.payload.uncertainties.nonEmpty =>
        "The owning UoW retained an uncertain check: " + actual.payload.uncertainties.last.diagnostic +
          ". No automatic replay. Describe validation recovery for an explicit independent Human decision about a NEW attempt."
      case _ => "A native check intent has no recorded result. Observe or request a genuine decision; checks are not automatically replayed."
    })
  } yield wire.copy(validation = Some(NativeWorkflowOperationOutput.goalValidation(actual.payload)
    .copy(evidenceRecording = evidence ++ repairevidence, assessmentJournal = journal)))
    }
  } yield response

  private[admission] def recoveryTargetC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    candidate: GoalPhaseNativeCandidate.Saved, controller: O.State, from: InstanceRecord,
    documentPolicy: Option[GoalPhaseDocumentProgress.Policy], recordingPaths: Vector[String],
    dependencyChain: Option[GoalPhaseNativeWorkContinuation.DependencyChain] = None):
    Consequence[GoalPhaseValidationRecovery.RecoveryTarget] =
    _store_c(session, d, core, candidate, controller, _ => Consequence.unit, Some(from), documentPolicy, recordingPaths, dependencyChain)
      .flatMap(_.recoveryTargetC)

  private[admission] def repairRecoveryTargetC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    candidate: GoalPhaseNativeCandidate.Saved, controller: O.State, from: InstanceRecord,
    documentPolicy: Option[GoalPhaseDocumentProgress.Policy], recordingPaths: Vector[String],
    dependencyChain: Option[GoalPhaseNativeWorkContinuation.DependencyChain] = None):
    Consequence[GoalPhaseValidationRecovery.RecoveryTarget] =
    _store_c(session, d, core, candidate, controller, _ => Consequence.unit, Some(from), documentPolicy, recordingPaths, dependencyChain)
      .flatMap(_.repairRecoveryTargetC)

  private[admission] def appendRecoveryC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    candidate: GoalPhaseNativeCandidate.Saved, controller: O.State, from: InstanceRecord,
    documentPolicy: Option[GoalPhaseDocumentProgress.Policy], recordingPaths: Vector[String],
    expected: GoalPhaseValidationRecovery.RecoveryTarget, authorization: NewAttempt,
    dependencyChain: Option[GoalPhaseNativeWorkContinuation.DependencyChain] = None): Consequence[Saved] =
    _store_c(session, d, core, candidate, controller, _ => Consequence.unit, Some(from), documentPolicy, recordingPaths, dependencyChain)
      .flatMap(_.appendRecoveryC(expected, authorization))

  /** Read-only proof for the original Decision, including every retained prior attempt. */
  private[admission] def terminalFailedAttemptC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    candidate: GoalPhaseNativeCandidate.Saved, controller: O.State, failed: Saved,
    dependencyChain: Option[GoalPhaseNativeWorkContinuation.DependencyChain] = None): Consequence[TerminalFailedAttempt] =
    terminalAttemptC(session, d, core, candidate, controller, failed, TerminalOutcome.Failed, dependencyChain)
      .map(value => TerminalFailedAttempt(value.attempt, value.intent, value.result, value.validation))

  /** Both repair sources use the ordinary retained intent/result reconciliation.
    * The closed outcome is checked before loading and never inferred from exit 143.
    */
  private[admission] def terminalAttemptC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    candidate: GoalPhaseNativeCandidate.Saved, controller: O.State, failed: Saved,
    expected: TerminalOutcome,
    dependencyChain: Option[GoalPhaseNativeWorkContinuation.DependencyChain] = None): Consequence[TerminalAttempt] = for {
    entry <- session.current.history.filter(_.revision.value.toString == failed.payload.sourceRevision) match {
      case Vector(value) => Consequence.success(value)
      case _ => F.invalid("known failed validation source is absent or ambiguous")
    }
    source <- GoalPhaseNativeHistory.prefixC(session.current, entry.revision)
    store <- _store_c(session, d, core, candidate, controller, _ => Consequence.unit, Some(source), None, Vector.empty, dependencyChain)
    terminal <- store.terminalAttemptC(failed, expected)
  } yield terminal

  private[admission] def outputLimitedAttemptC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    candidate: GoalPhaseNativeCandidate.Saved, controller: O.State, limited: Saved,
    dependencyChain: Option[GoalPhaseNativeWorkContinuation.DependencyChain] = None): Consequence[OutputLimitedAttempt] = for {
    entry <- session.current.history.filter(_.revision.value.toString == limited.payload.sourceRevision) match {
      case Vector(value) => Consequence.success(value)
      case _ => F.invalid("output-limited validation source is absent or ambiguous")
    }
    source <- GoalPhaseNativeHistory.prefixC(session.current, entry.revision)
    store <- _store_c(session, d, core, candidate, controller, _ => Consequence.unit, Some(source), None, Vector.empty, dependencyChain)
    actual <- store.actualTerminalAttemptC(limited, "incomplete", outputLimited(limited.payload))
  } yield OutputLimitedAttempt(actual._1, actual._2, actual._3, actual._4)

  private def _store_c(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    candidate: GoalPhaseNativeCandidate.Saved, controller: O.State, acknowledgement: String => Consequence[Unit],
    from: Option[InstanceRecord], documentpolicy: Option[GoalPhaseDocumentProgress.Policy], recordingpaths: Vector[String],
    dependencyChain: Option[GoalPhaseNativeWorkContinuation.DependencyChain] = None): Consequence[Store] = for {
    whole <- Consequence.success(controller.cursor == controller.plan.steps.size)
    _ <- documentpolicy.fold(Consequence.unit)(_.validateC(controller.plan, d.candidatePaths))
    _ <- GoalPhaseNativeFindingRecording.validatePathsC(recordingpaths, controller.plan, d.candidatePaths)
    step = if (whole) None else Some(controller.plan.steps(controller.cursor).identity)
    primary <- selectedChecksC(d, controller.plan, step)
    consumptions = (dependencyChain.toVector.flatMap(_.acceptedSources.map(entry => GoalPhaseDependencySourceRevision.consumptionOf(entry.capture))) ++
      candidate.payload.dependencySourceCapture.map(GoalPhaseDependencySourceRevision.consumptionOf)).distinct
    _ <- if (consumptions.size <= 1 && primary.forall(c => consumptions.forall(route =>
        route.programs.exists(p => p.check == c.identity && p.kind == c.kind.wire)))) Consequence.unit
      else F.invalid("primary checks have no unambiguous admitted source-consumption programs")
    selected <- GoalPhaseNativeDependencyValidation.checksC(primary, candidate.payload.dependency, candidate.payload.dependencySourceCapture)
    scope = step.getOrElse("phase:" + controller.plan.phase)
    owned = if (whole) controller.plan.steps.flatMap(_.ownedPaths).distinct else controller.plan.steps(controller.cursor).ownedPaths
    _ <- L.bindingsC(selected, scope, owned, d.candidatePaths)
    frontier = from.getOrElse(candidate.record)
    _ <- frontier.validateC
    _ <- candidate.record.validateC
    instances <- session.storage.bindInstancesC()(using core.executionContext)
    current <- instances.loadC(session.current.identity)(using core.executionContext).flatMap(
      _.map(Consequence.success).getOrElse(F.invalid("native Validation instance is absent")))
    admitted <- GoalPhaseNativeHistory.prefixC(current, frontier.revision)
    prior <- GoalPhaseNativeHistory.prefixC(current, candidate.record.revision)
    _ <- if (frontier.identity == current.identity &&
        frontier.definition.workflowIdentity == current.definition.workflowIdentity &&
        frontier.definition.workflowRevision == current.definition.workflowRevision &&
        candidate.record.identity == frontier.identity &&
        candidate.record.definition.workflowIdentity == frontier.definition.workflowIdentity &&
        candidate.record.definition.workflowRevision == frontier.definition.workflowRevision &&
        admitted == frontier && admitted.suspension.isEmpty &&
        frontier.suspension.isEmpty && frontier.revision.value >= candidate.record.revision.value &&
        prior == candidate.record &&
        frontier.currentProgression.contains(ProgressionReference(if (whole) "phase.PhaseValidation" else "phase.Validation")))
      Consequence.unit else F.invalid("native checks require their actual admitted Validation source")
    _ <- if (candidate.record.currentProgression.contains(ProgressionReference(
        if (whole) "phase.PhaseValidation" else "phase.Validation")) &&
        candidate.payload.candidate.nonEmpty && candidate.payload.diff.nonEmpty) Consequence.unit
      else F.invalid("native checks require the actual saved candidate")
    prefix = if (selected.exists(_.metadata.nonEmpty)) selected.takeWhile(_.metadata.isEmpty) else Vector.empty
    initial = Payload(frontier.revision.value.toString, scope, candidate.payload.candidate.get,
      if (prefix.nonEmpty) prefix else selected, Vector.empty, None, dependency = candidate.payload.dependency,
      dependencySource = candidate.payload.dependencySourceCapture, sourceConsumption = consumptions.headOption)
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    store = new Store(adapter, session, d, core, candidate, frontier, controller, selected, initial,
      None, acknowledgement, documentpolicy, recordingpaths, dependencyChain)
    retained <- store.loadC("selection")
    actual <- retained match {
      case Some(saved) => store.retainedSelectionC(saved)
      case None if selected.exists(_.metadata.nonEmpty) && prefix.isEmpty => for {
        validation <- store.loadC("validation")
        intent <- store.loadC("check-intent-0")
        _ <- if (validation.isEmpty && intent.isEmpty) Consequence.unit
          else F.invalid("Retained metadata validation has no actual saved selection")
      } yield store
      case None => Consequence.success(store)
    }
  } yield actual

  private def _checks_c(unprepared: Store): Consequence[Saved] = for {
    prepared <- unprepared.prepareSelectionC
    (store, initial) = prepared
    _ <- store.pendingSelectionC
    current <- _check_loop_c(store, initial)
    selected <- if (store._selection_pending && current.payload.receipts.size == store.initial.selected.size &&
        successful(current.payload.receipts, store.initial.selected)) for {
      prepared <- store.prepareSelectionC
      (selectedstore, source) = prepared
      checked <- _check_loop_c(selectedstore, source)
    } yield (selectedstore, checked) else Consequence.success((store, current))
    saved <- _outcome_c(selected._1, selected._2)
  } yield saved

  private def _check_loop_c(store: Store, initial: Saved): Consequence[Saved] =
    store.initial.selected.zipWithIndex.foldLeft(Consequence.success(initial)) { case (prior, (check, index)) => prior.flatMap { current =>
      if (current.payload.receipts.size != index || !successful(current.payload.receipts, store.initial.selected)) Consequence.success(current)
      else runCheckC(check, index, store.initial.dependency.nonEmpty || store.initial.dependencySource.nonEmpty || store.dependencyChain.exists(_.accepted.nonEmpty))(
        () => store._check_progress_c(current, index), () => store.preconditionsC,
        store.saveC, store.acknowledgeC,
        selected => if (selected.operation.isEmpty) _workspace_c(selected, store.initial)
          .flatMap(workspace => E.repositoryValidationAttemptC(store.core, selected.ordinal, selected.kind, workspace))
          else N.operationC(selected, store.initial.metadata).flatMap(E.validationOperationAttemptC(store.core, _)),
        payload => store.evidenceC(payload).map(_ => ()))
    }}

  /** The production single-check program. Every dispatch reconciles the same
    * admitted prefix, then commits a fresh intent before its owning typed UoW.
    * Only a newly saved actual NotStarted observation permits one fresh dispatch.
    */
  private[admission] def runCheckC(check: Check, index: Int, verifyAfter: Boolean = false)(
    progressC: () => Consequence[CheckProgress], preconditionsC: () => Consequence[Unit],
    saveC: (String, InstanceRecord, Payload) => Consequence[Saved],
    acknowledgeC: String => Consequence[Unit], runC: Check => Consequence[SbtExecutionAttempt],
    evidenceC: Payload => Consequence[Unit]): Consequence[Saved] = {
    def _dispatch_(remaining: Int): Consequence[Saved] = progressC().flatMap { progress => progress.nextAttempt match {
          case None => Consequence.success(progress.saved)
          case Some(attempt) => for {
            _ <- preconditionsC()
            prepared <- saveC(_attempt_key("intent", index, attempt), progress.saved.record, progress.saved.payload)
            _ <- acknowledgeC("goal-check-prepared:" + check.identity)
            actual <- runC(check)
            _ <- acknowledgeC("goal-check-executed:" + check.identity)
            payload <- attemptPayloadC(progress.saved.payload, check, attempt, actual)
            saved <- saveC(_attempt_key("result", index, attempt), prepared.record, payload)
            _ <- acknowledgeC("goal-check-recorded:" + check.identity)
            _ <- evidenceC(saved.payload)
            _ <- if ((verifyAfter || check.dependency.nonEmpty) && actual.isInstanceOf[SbtExecutionAttempt.Observed]) preconditionsC()
              else Consequence.unit
            next <- actual match {
              case SbtExecutionAttempt.Observed(_) if remaining > 0 &&
                  saved.payload.stopped.size == progress.saved.payload.stopped.size + 1 &&
                  saved.payload.stopped.dropRight(1) == progress.saved.payload.stopped &&
                  saved.payload.stopped.lastOption.exists(r => r.identity == check.identity && _not_executed(r)) =>
                _dispatch_(remaining - 1)
              case _ => Consequence.success(saved)
            }
          } yield next
    }}
    _dispatch_(1)
  }

  private[admission] def attemptPayloadC(previous: Payload, check: Check, attempt: Int,
    actual: SbtExecutionAttempt): Consequence[Payload] = actual match {
    case SbtExecutionAttempt.Observed(result) => for {
      workspace <- _workspace_c(check, previous)
      _ <- _attempt_target_c(check, workspace, result.target, result.operation)
      _ <- if (_source_program(check, previous).forall(p => result.processResult != null &&
          p.program == result.processResult.safeProgramIdentity)) Consequence.unit
        else F.invalid("source-consumption check used a different admitted program")
      receipt <- V.sbtReceiptC(check.identity, check.kind, result)
      execution = workspace.filter(_ => check.dependency.nonEmpty).map(w => GoalPhaseDependencyValidation.Execution(check.identity, attempt, w, check.kind.wire))
      executions = previous.dependencyExecutions ++ execution
    } yield if (_not_executed(receipt)) previous.copy(stopped = previous.stopped :+ receipt, dependencyExecutions = executions)
      else previous.copy(receipts = previous.receipts :+ receipt, dependencyExecutions = executions)
    case SbtExecutionAttempt.AdmissionRejected(target, operation, conclusion) => for {
      workspace <- _workspace_c(check, previous)
      _ <- _attempt_target_c(check, workspace, target, operation)
      diagnostic <- _diagnostic_c(conclusion.show)
    } yield previous.copy(admissionFailures = previous.admissionFailures :+ AdmissionFailure(check.identity, attempt, diagnostic))
    case SbtExecutionAttempt.Uncertain(target, operation, conclusion) => for {
      workspace <- _workspace_c(check, previous)
      _ <- _attempt_target_c(check, workspace, target, operation)
      diagnostic <- _diagnostic_c(conclusion.show)
    } yield previous.copy(uncertainties = previous.uncertainties :+ Uncertainty(check.identity, attempt, diagnostic))
    case _ => F.invalid("native check has no typed UoW attempt")
  }

  private def _workspace_c(check: Check, payload: Payload): Consequence[Option[String]] =
    if (check.dependency.isEmpty && payload.sourceConsumption.nonEmpty)
      payload.sourceConsumption.get.programs.find(p => p.check == check.identity && p.kind == check.kind.wire)
        .map(p => Consequence.success(Some(p.workspace)))
        .getOrElse(F.invalid("primary check has no admitted source-consumption route"))
    else GoalPhaseNativeDependencyValidation.workspaceC(check, payload.dependency, payload.dependencySource)

  private def _source_program(check: Check, payload: Payload): Option[GoalPhaseDependencySourceRevision.CheckProgram] =
    (if (check.dependency.isEmpty) payload.sourceConsumption
      else payload.dependencySource.map(GoalPhaseDependencySourceRevision.consumptionOf))
      .flatMap(_.programs.find(p => p.check == check.identity && p.kind == check.kind.wire))

  private def _attempt_target_c(check: Check, workspace: Option[String], target: SbtTarget,
    operation: SbtOperation): Consequence[Unit] =
    if (target != null && operation != null && workspace.forall(w => target.workspaceId.value == w) &&
        (operation match {
          case SbtOperation.Compile => check.kind == WorkflowSbtValidation.Kind.Compile
          case SbtOperation.Test => check.kind == WorkflowSbtValidation.Kind.Test
          case SbtOperation.TestOnly => check.kind == WorkflowSbtValidation.Kind.TestOnly
          case SbtOperation.FixCheck => check.kind == WorkflowSbtValidation.Kind.FixCheck
          case SbtOperation.PublishLocal => false
        })) Consequence.unit
    else F.invalid("native check attempt differs from its exact target or operation")

  private def _diagnostic_c(value: String): Consequence[String] =
    if (_stored_text(value) && value.trim.nonEmpty) Consequence.success(value)
    else F.invalid("native check diagnostic exceeds the retained text policy")

  private def _outcome_c(store: Store, current: Saved): Consequence[Saved] =
      if (current.payload.receipts.size == store.initial.selected.size || !successful(current.payload.receipts, store.initial.selected)) for {
        // A returned interruption settles the already admitted candidate. Runtime
        // stop closes process admission, including Git, before this durable result.
        _ <- if (store.compatible(current.payload) && _returned_interruption(current.payload)) Consequence.unit
          else store.preconditionsC
        passed = successful(current.payload.receipts, store.initial.selected)
        inspected <- if (store.controller.stage.toString == store.validationStage &&
            store.controller.candidate.contains(store.initial.candidate)) Consequence.success(store.controller)
          else GoalPhaseNativeCandidate.observedStateC(store.controller, store.candidate.payload, store.documentPolicy, store.recordingPaths)
        outcome = if (passed) "passed" else if (outputLimited(current.payload)) "incomplete" else if ((!store.whole || inspected.releaseRepairActive) && inspected.effectiveRepairLimit > 0 &&
          inspected.generation < Long.MaxValue && repairable(current.payload)) {
          if (inspected.repairs < inspected.effectiveRepairLimit) "repair-required" else "repair-limit-reached"
        } else "failed"
        completed = current.payload.copy(outcome = Some(outcome))
        assessment <- if (passed && !store.whole && inspected.plan.steps(inspected.cursor).actionPolicy.nonEmpty)
          GoalPhaseStepAssessment.assessC(inspected, completed, store.reference("validation").identity, store.candidate.payload.changedPaths).map(Some(_))
          else Consequence.success(Option.empty[GoalPhaseStepAssessment.Result])
        payload = completed.copy(assessment = assessment)
        validated <- observedStateC(inspected, payload, store.reference("validation").identity, store.candidate.payload.changedPaths)
        target = validated.stage.toString
        event = (if (store.whole) "phase-" else "") + (outcome match {
          case "passed" if assessment.exists(_.branch == "admit") => "checks-admitted"
          case "passed" if assessment.exists(_.branch == "triage") => "checks-triage-required"
          case "passed" if assessment.exists(_.branch == "decision") => "checks-judgment-required"
          case "passed" => "checks-passed"
          case "incomplete" => "checks-incomplete"
          case "repair-required" if validated.stage == O.Stage.Repair => "checks-repair-required"
          case _ => "checks-failed"
        })
        _ <- if (validated.stage.toString == target && domain.statemachine.GoalPhaseRunLifecycle.permits(store.validationStage, event, target))
          Consequence.unit else F.invalid("native check outcome differs from CML/controller")
        saved <- store.saveC("validation", current.record, payload)
        _ <- store.acknowledgeC("goal-checks-admitted")
      } yield saved else Consequence.success(current)

  private final class Store(adapter: WorkflowManagedMechanicalTransition[Payload], session: F.Session,
    val d: F.Declaration, val core: ActionCall.Core, val candidate: GoalPhaseNativeCandidate.Saved,
    val frontier: InstanceRecord,
    val controller: O.State, val requested: Vector[Check], val initial: Payload,
    val selectionRecord: Option[InstanceRecord], acknowledgement: String => Consequence[Unit],
    val documentPolicy: Option[GoalPhaseDocumentProgress.Policy], val recordingPaths: Vector[String],
    val dependencyChain: Option[GoalPhaseNativeWorkContinuation.DependencyChain]) {
    val plan = controller.plan
    val base = candidate.payload.continuation.map(_.parent).getOrElse(controller.candidateBase)
    val whole = controller.cursor == plan.steps.size
    val checks = if (whole) plan.phaseChecks else plan.steps(controller.cursor).checks
    val ownedPaths = if (whole) plan.steps.flatMap(_.ownedPaths).distinct else plan.steps(controller.cursor).ownedPaths
    val managementPaths = (documentPolicy.toVector.flatMap(_.paths) ++ recordingPaths).distinct
    val validationStage = if (whole) "PhaseValidation" else "Validation"
    val reviewStage = if (whole) "PhaseReview" else "Review"
    private var _evidence = Option.empty[Vector[org.simplemodeling.textus.smworkflow.value.EvidenceRecordingState]]
    def evidenceC(payload: Payload): Consequence[Vector[org.simplemodeling.textus.smworkflow.value.EvidenceRecordingState]] =
      _evidence.fold(GoalPhaseNativeEvidence.recordC(d, core, session.current.identity.value, payload).map { value =>
        if (value.exists(_.evidence.nonEmpty)) _evidence = Some(value)
        value
      })(Consequence.success)
    private val _prefix = if (requested.exists(_.metadata.nonEmpty)) requested.takeWhile(_.metadata.isEmpty) else Vector.empty
    def _selection_pending: Boolean = selectionRecord.isEmpty && requested.exists(_.metadata.nonEmpty)
    private def _prefix_saved_c(complete: Boolean): Consequence[Saved] =
      if (_prefix.isEmpty) Consequence.success(Saved(frontier, initial))
      else for {
        saved <- _chain_saved_c(Saved(frontier, initial.copy(selected = _prefix, receipts = Vector.empty,
          outcome = None, metadata = Vector.empty, stopped = Vector.empty, admissionFailures = Vector.empty,
          uncertainties = Vector.empty, newAttempts = Vector.empty)))
        _ <- if (!complete || (saved.payload.receipts.size == _prefix.size && successful(saved.payload.receipts, _prefix)))
          Consequence.unit else F.invalid("Metadata selection requires the actual complete successful ordinary prefix")
      } yield saved
    def retainedSelectionC(saved: Saved): Consequence[Store] = for {
      prefix <- _prefix_saved_c(true)
      _ <- if (requested.exists(_.metadata.nonEmpty) && saved != null && saved.record != null && saved.payload != null &&
          saved.payload.sourceRevision == frontier.revision.value.toString && saved.payload.step == initial.step &&
          saved.payload.candidate == initial.candidate && saved.payload.receipts == prefix.payload.receipts &&
          _same_attempt_facts(saved.payload, prefix.payload) && saved.payload.outcome.isEmpty)
        Consequence.unit else F.invalid("Retained native metadata selection crosses its Validation frontier")
      _ <- _adjacent_record_c(prefix.record, saved.record, "ordered-validation:selection", frontier.currentProgression)
      _ <- N.validateRetainedC(requested, saved.payload.selected, saved.payload.metadata)
    } yield new Store(adapter, session, d, core, candidate, frontier, controller, requested,
      saved.payload, Some(saved.record), acknowledgement, documentPolicy, recordingPaths, dependencyChain)
    def prepareSelectionC: Consequence[(Store, Saved)] =
      if (selectionRecord.nonEmpty || !requested.exists(_.metadata.nonEmpty))
        Consequence.success((this, Saved(selectionRecord.getOrElse(frontier), initial)))
      else for {
        retained <- _prefix_saved_c(false)
        _ <- requested.indices.drop(_prefix.size).foldLeft(Consequence.unit) { (prior, index) => for {
          _ <- prior
          intent <- loadC(_attempt_key("intent", index, 0))
          result <- loadC(_attempt_key("result", index, 0))
          recovery <- loadC(_attempt_key("recovery", index, 0))
          _ <- if (intent.isEmpty && result.isEmpty && recovery.isEmpty) Consequence.unit
            else F.invalid("Metadata checks precede their actual retained selection")
        } yield () }
        prepared <- if (_prefix.nonEmpty && (retained.payload.receipts.size != _prefix.size ||
            !successful(retained.payload.receipts, _prefix))) Consequence.success((this, Saved(frontier, initial)))
          else for {
            _ <- _latest_c(retained)
            _ <- preconditionsC
            parent <- Option(classOf[D.Inventory].getClassLoader).map(Consequence.success)
              .getOrElse(F.invalid("Native metadata selection requires the CNCF discovery ABI ClassLoader"))
            captured <- L.captureC(requested, core, initial.step, ownedPaths, d.candidatePaths)
            selected <- N.prepareC(requested, parent, captured)
            payload = retained.payload.copy(selected = selected._1, metadata = selected._2)
            saved <- saveC("selection", retained.record, payload)
            store <- retainedSelectionC(saved)
          } yield (store, saved)
      } yield prepared
    private def _chain_saved_c(source: Saved): Consequence[Saved] =
      source.payload.selected.indices.drop(source.payload.receipts.size).foldLeft(Consequence.success(source)) { (prior, index) =>
        prior.flatMap { current =>
          if (current.payload.receipts.size == index && successful(current.payload.receipts, source.payload.selected))
            _check_progress_c(current, index, source.payload).map(_.saved)
          else for {
            intent <- loadC(_attempt_key("intent", index, 0))
            result <- loadC(_attempt_key("result", index, 0))
            recovery <- loadC(_attempt_key("recovery", index, 0))
            _ <- if (intent.isEmpty && result.isEmpty && recovery.isEmpty) Consequence.unit
              else F.invalid("Retained checks have a future noncontiguous original attempt")
          } yield current
        }
      }
    def _check_progress_c(previous: Saved, index: Int, selection: Payload = initial): Consequence[CheckProgress] =
      _verified_attempts_c(previous, index, selection).map(_._1)
    private def _verified_attempts_c(previous: Saved, index: Int, selection: Payload):
      Consequence[(CheckProgress, Vector[Attempt])] = {
      def _load_(attempt: Int, attempts: Vector[Attempt]): Consequence[(CheckProgress, Vector[Attempt])] = for {
        intent <- loadC(_attempt_key("intent", index, attempt))
        result <- loadC(_attempt_key("result", index, attempt))
        recovery <- loadC(_attempt_key("recovery", index, attempt))
        pairs = attempts :+ Attempt(intent, result, recovery)
        progress <- reconcileAttemptsC(previous, selection, index, pairs)
        latest <- if (intent.nonEmpty && (result.nonEmpty || recovery.nonEmpty) && progress.nextAttempt.nonEmpty)
          _load_(progress.nextAttempt.get, pairs) else Consequence.success((progress, pairs))
      } yield latest
      _load_(0, Vector.empty)
    }
    def terminalAttemptC(failed: Saved, expected: TerminalOutcome): Consequence[TerminalAttempt] = for {
      actual <- actualTerminalAttemptC(failed, "failed", terminalOutcome(failed.payload).contains(expected))
    } yield TerminalAttempt(expected, actual._1, actual._2, actual._3, actual._4)
    def actualTerminalAttemptC(failed: Saved, expectedOutcome: String, eligible: Boolean):
      Consequence[(Int, Saved, Saved, Saved)] = for {
      _ <- if (whole && initial.selected.size == 1 && failed.payload.outcome.contains(expectedOutcome) &&
          eligible && failed.payload.receipts.size == 1) Consequence.unit
        else F.invalid("Phase repair requires its exact single typed terminal check")
      verified <- _verified_attempts_c(Saved(selectionRecord.getOrElse(frontier), initial), 0, initial)
      (progress, attempts) = verified
      pair <- attempts.lastOption match {
        case Some(Attempt(Some(intent), Some(result), None)) if progress.nextAttempt.isEmpty && progress.saved == result =>
          Consequence.success((intent, result))
        case _ => F.invalid("known failed validation has no final actual intent/result pair")
      }
      (intent, result) = pair
      saved <- loadC("validation").flatMap(_.map(Consequence.success)
        .getOrElse(F.invalid("original failed validation receipt is absent")))
      _ <- if (saved == failed && compatible(saved.payload) &&
          saved.payload == result.payload.copy(outcome = Some(expectedOutcome))) Consequence.unit
        else F.invalid("known failed validation differs from its complete terminal result")
      _ <- _adjacent_record_c(result.record, saved.record, "ordered-validation:validation",
        Some(ProgressionReference("phase.Decision")))
    } yield (attempts.size - 1, intent, result, saved)
    def pendingSelectionC: Consequence[Option[Saved]] =
      for {
        saved <- _chain_saved_c(Saved(selectionRecord.getOrElse(frontier), initial))
        _ <- _latest_c(saved)
      } yield Option.when(saved.record.revision.value > frontier.revision.value)(saved)
    def pendingRepairSelectionC: Consequence[Option[Saved]] = for {
      saved <- _chain_saved_c(Saved(selectionRecord.getOrElse(frontier), initial))
      current <- _actual_record_c
      next <- GoalPhaseNativeHistory.nextC(current, saved.record)
      _ <- if (current == saved.record || next.exists(_.causation.value ==
          "ordered-validation-repair-adoption:" + saved.record.revision.value)) Consequence.unit
        else F.invalid("Retained pending check has an unrelated later native record")
    } yield Option.when(saved.record.revision.value > frontier.revision.value)(saved)
    private def _unresolved_c(strict: Boolean = true): Consequence[(Saved, Saved, Int, Int)] = for {
      completed <- loadC("validation")
      _ <- if (completed.isEmpty) Consequence.unit else F.invalid("completed validation has no recovery decision")
      saved <- (if (strict) pendingSelectionC else pendingRepairSelectionC).flatMap(_.map(Consequence.success)
        .getOrElse(F.invalid("validation recovery has no actual unresolved saved intent")))
      index = saved.payload.receipts.size
      _ <- if (index < saved.payload.selected.size && saved.payload.outcome.isEmpty &&
          successful(saved.payload.receipts, saved.payload.selected) && !continuable(saved)) Consequence.unit
        else F.invalid("validation recovery is not at an unresolved selected check")
      selected = saved.payload.selected(index)
      uncertain = saved.payload.uncertainties.lastOption.filter(f => f.checkIdentity == selected.identity &&
        saved.record.causation.value == "ordered-validation:" + _attempt_key("result", index, f.attempt))
      intentPrefix = "ordered-validation:check-intent-" + index
      ordinal <- uncertain.map(f => Consequence.success(f.attempt)).getOrElse {
        val cause = saved.record.causation.value
        if (cause == intentPrefix) Consequence.success(0)
        else if (cause.startsWith(intentPrefix + "-retry-")) {
          val suffix = cause.substring((intentPrefix + "-retry-").length)
          suffix.toIntOption.filter(n => n > 0 && n < Int.MaxValue && n.toString == suffix)
            .map(Consequence.success).getOrElse(F.invalid("validation recovery intent ordinal is invalid"))
        } else F.invalid("validation recovery has no actual unresolved intent or uncertain result")
      }
      intent <- loadC(_attempt_key("intent", index, ordinal)).flatMap(_.map(Consequence.success)
        .getOrElse(F.invalid("validation recovery original intent is absent")))
      result <- loadC(_attempt_key("result", index, ordinal))
      recovery <- loadC(_attempt_key("recovery", index, ordinal))
      _ <- if (recovery.isEmpty && (if (uncertain.nonEmpty) result.contains(saved)
          else result.isEmpty && intent == saved)) Consequence.unit
        else F.invalid("validation recovery is stale or already authorized")
    } yield (saved, intent, index, ordinal)
    def recoveryTargetC: Consequence[GoalPhaseValidationRecovery.RecoveryTarget] = _recovery_target_c(true)
    def repairRecoveryTargetC: Consequence[GoalPhaseValidationRecovery.RecoveryTarget] = _recovery_target_c(false)
    private def _recovery_target_c(strict: Boolean): Consequence[GoalPhaseValidationRecovery.RecoveryTarget] = _unresolved_c(strict).map {
      case (saved, intent, index, ordinal) =>
        val check = saved.payload.selected(index)
        GoalPhaseValidationRecovery.RecoveryTarget(frontier.identity.value, plan.identity, plan.revision,
          session.started.receipt.payload.value.base, initial.candidate, initial.sourceRevision,
          saved.record.revision.value.toString, initial.step, check.identity, check.ordinal, ordinal,
          intent.record.revision.value.toString, dependency = check.dependency.flatMap(_ => initial.dependency).map(GoalPhaseDependencyValidation.subject))
    }
    def appendRecoveryC(expected: GoalPhaseValidationRecovery.RecoveryTarget, authorization: NewAttempt): Consequence[Saved] = for {
      actual <- recoveryTargetC
      _ <- if (actual == expected && authorization != null && authorization.checkIdentity == actual.checkIdentity &&
          authorization.attempt == actual.attempt && authorization.intentRevision == actual.intentRevision)
        Consequence.unit else F.invalid("new-attempt authorization crosses its complete current recovery target")
      unresolved <- _unresolved_c()
      (saved, _, index, ordinal) = unresolved
      payload = saved.payload.copy(newAttempts = saved.payload.newAttempts :+ authorization)
      next <- saveC(_attempt_key("recovery", index, ordinal), saved.record, payload)
    } yield next
    private def _actual_record_c: Consequence[InstanceRecord] = for {
      instances <- session.storage.bindInstancesC()(using core.executionContext)
      current <- instances.loadC(frontier.identity)(using core.executionContext).flatMap(
        _.map(Consequence.success).getOrElse(F.invalid("Retained validation instance is absent")))
      _ <- current.validateC
    } yield current
    private def _latest_c(saved: Saved): Consequence[Unit] = _actual_record_c.flatMap { current =>
      if (current == saved.record) Consequence.unit
      else F.invalid("Retained validation chain omits an actual later native record")
    }
    def reference(key: String) = WorkflowMechanicalTransition.Reference(frontier.identity.value +
      ":ordered-validation:" + frontier.revision.value + ":" + key, "1")
    def loadC(key: String): Consequence[Option[Saved]] = adapter.loadRetainedC(reference(key))(using core.executionContext).flatMap {
      case None => Consequence.success(None)
      case Some(value) =>
        val p = value.receipt.payload.value
        for {
          current <- _actual_record_c
          admitted <- GoalPhaseNativeHistory.prefixC(current, value.receipt.next.revision)
          _ <- if (admitted == value.receipt.next && admitted.identity == frontier.identity &&
              admitted.definition == frontier.definition) Consequence.unit
            else F.invalid("Retained validation record is not its complete actual history prefix")
        } yield Some(Saved(value.receipt.next, p))
    }
    def saveC(key: String, from: InstanceRecord, payload: Payload): Consequence[Saved] = for {
      _ <- if (_valid_payload(payload) && from.suspension.isEmpty &&
          from.currentProgression.contains(ProgressionReference("phase." + validationStage))) Consequence.unit
        else F.invalid("check intent/result has no actual unsuspended Validation frontier")
      target = payload.outcome.map {
        case "passed" if payload.assessment.exists(_.branch == "admit") => "Closing"
        case "passed" if payload.assessment.exists(_.branch == "triage") => "Triage"
        case "passed" if payload.assessment.exists(_.branch == "decision") => "Decision"
        case "passed" => reviewStage
        case "repair-required" => "Repair"
        case _ => "Decision"
      }.getOrElse(validationStage)
      saved <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, reference(key), from, from.revision,
        F.history(from, "phase." + target, "ordered-validation:" + key), TypedValue(codec.typeIdentity, payload)),
        ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
    } yield Saved(saved.receipt.next, saved.receipt.payload.value)
    def compatible(payload: Payload): Boolean =
      _compatible_payload(initial, payload)
    def completedC(saved: Saved): Consequence[Saved] = for {
      prefix <- _chain_saved_c(Saved(selectionRecord.getOrElse(frontier), initial))
      _ <- if (saved != null && compatible(saved.payload)) Consequence.unit
        else F.invalid("retained check outcome crosses its selected candidate or binding")
      _ <- if (saved.payload.receipts == prefix.payload.receipts && _same_attempt_facts(saved.payload, prefix.payload) &&
          saved.payload.dependencyExecutions == prefix.payload.dependencyExecutions &&
          (prefix.payload.receipts.size == initial.selected.size || !successful(prefix.payload.receipts, initial.selected)))
        Consequence.unit else F.invalid("retained outcome has no actual complete or failed check chain")
      inspected <- if (controller.stage.toString == validationStage && controller.candidate.contains(initial.candidate))
        Consequence.success(controller)
        else GoalPhaseNativeCandidate.observedStateC(controller, candidate.payload, documentPolicy, recordingPaths)
      state <- observedStateC(inspected, saved.payload, reference("validation").identity, candidate.payload.changedPaths)
      _ <- _adjacent_record_c(prefix.record, saved.record, "ordered-validation:validation",
        Some(ProgressionReference("phase." + state.stage.toString)))
    } yield saved
    def preconditionsC: Consequence[Unit] = for {
      _ <- GoalPhaseDependencySourceRevision.chainFreshnessC(core, dependencyChain,
        initial.dependency.toVector, initial.dependencySource.toVector)
      parent <- B.parentC(controller.tipSnapshot, candidate.payload.continuation)
      _ <- if (parent == base && (controller.candidateParent.isEmpty || controller.candidateBase == parent)) Consequence.unit
        else F.invalid("native validation candidate parent differs from its retained proof")
      root <- G.rootC(core)
      actual <- G.observeC(core)
      tree <- G.indexTreeC(core)
      scoped = if (whole) candidate.payload.selectedPaths else ownedPaths
      _ <- if (root == d.target.root.toString && actual.branch == d.target.localBranch &&
          actual.head == parent.head && actual.head == initial.candidate.head && actual.tree == parent.tree &&
          tree == initial.candidate.tree && candidate.payload.selectedPaths == candidate.payload.stagedPaths &&
          (!whole || (if (controller.releaseRepairActive) B.pathsOwned(candidate.payload.selectedPaths, ownedPaths)
            else candidate.payload.selectedPaths.forall(managementPaths.contains))) &&
          B.scopedDirty(actual.dirty, scoped, true)) Consequence.unit
        else F.invalid("actual native candidate cannot establish the selected check preconditions")
      oid <- GitObjectId.parseC(parent.head)
      paths <- E.executeGitC(core, GitOperation.PendingPaths(oid)).flatMap(_.valueC)
      _ <- if (B.pathsOwned(paths.map(_.value), scoped)) Consequence.unit
        else F.invalid("native validation includes an unrelated index delta")
      after <- G.observeC(core)
      index <- G.indexTreeC(core)
      afterroot <- G.rootC(core)
      _ <- if (afterroot == root && after.branch == actual.branch && after.head == parent.head && after.tree == parent.tree &&
          index == initial.candidate.tree && B.scopedDirty(after.dirty, scoped, true)) Consequence.unit
        else F.invalid("native validation frontier drifted during observation")
    } yield ()
    def acknowledgeC(stage: String): Consequence[Unit] = acknowledgement(stage) match {
      case Consequence.Success(_) => Consequence.unit
      case _ => org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate(
        "GoalPhase validation acknowledgement interrupted; retained intent is not replayed: " + stage)
    }
  }
  /** Reconcile actual immutable attempts only; missing results never establish execution facts. */
  private[admission] def reconcileAttemptsC(previous: Saved, initial: Payload, index: Int,
    attempts: Vector[Attempt]): Consequence[CheckProgress] = for {
    _ <- if (previous != null && previous.record != null && _valid_payload(initial) &&
        _compatible_payload(initial, previous.payload) && initial.outcome.isEmpty && previous.payload.outcome.isEmpty &&
        index >= 0 && index < initial.selected.size && previous.payload.receipts.size == index &&
        successful(previous.payload.receipts, initial.selected) && initial.receipts.size <= index &&
        previous.payload.receipts.take(initial.receipts.size) == initial.receipts &&
        _attempt_facts_prefix(initial, previous.payload) &&
        previous.payload.stopped.forall(_.identity != initial.selected(index).identity) &&
        previous.payload.admissionFailures.forall(_.checkIdentity != initial.selected(index).identity) &&
        previous.payload.uncertainties.forall(_.checkIdentity != initial.selected(index).identity) &&
        previous.payload.newAttempts.forall(_.checkIdentity != initial.selected(index).identity) &&
        previous.record.suspension.isEmpty && previous.record.currentProgression.exists(p =>
          p == ProgressionReference("phase.Validation") || p == ProgressionReference("phase.PhaseValidation")) &&
        initial.sourceRevision.toLongOption.exists(v => v > 0 && v.toString == initial.sourceRevision &&
          v <= previous.record.revision.value) &&
        attempts != null) Consequence.unit else F.invalid("Retained attempts have no exact selected Validation input")
    _ <- previous.record.validateC
    progress <- attempts.zipWithIndex.foldLeft(Consequence.success(CheckProgress(previous, Some(0)))) {
      case (prior, (attempt, ordinal)) => prior.flatMap { current =>
        if (attempt == null || attempt.intent == null || attempt.result == null || attempt.recovery == null || !current.nextAttempt.contains(ordinal))
          F.invalid("Retained check attempts follow an unresolved or completed attempt")
        else attempt match {
          case Attempt(None, None, None) =>
            if (ordinal == attempts.size - 1) Consequence.success(current)
            else F.invalid("Retained check attempts have a missing pair before a later attempt")
          case Attempt(None, _, _) => F.invalid("Retained check result or recovery has no matching intent")
          case Attempt(Some(intent), result, recovery) => for {
            _ <- if (intent != null && intent.payload == current.saved.payload) Consequence.unit
              else F.invalid("Retained intent changes its actual prior payload")
            _ <- _adjacent_record_c(current.saved.record, intent.record,
              "ordered-validation:" + _attempt_key("intent", index, ordinal), current.saved.record.currentProgression)
            observed <- result match {
              case None => Consequence.success(CheckProgress(intent, None))
              case Some(saved) => for {
                _ <- if (saved != null && _compatible_payload(initial, saved.payload) && saved.payload.outcome.isEmpty)
                  Consequence.unit else F.invalid("Retained result crosses its selected metadata or candidate")
                _ <- _adjacent_record_c(intent.record, saved.record,
                  "ordered-validation:" + _attempt_key("result", index, ordinal), intent.record.currentProgression)
                stopped = saved.payload.stopped.size == current.saved.payload.stopped.size + 1 &&
                  saved.payload.stopped.dropRight(1) == current.saved.payload.stopped &&
                  saved.payload.receipts == current.saved.payload.receipts &&
                  saved.payload.admissionFailures == current.saved.payload.admissionFailures &&
                  saved.payload.uncertainties == current.saved.payload.uncertainties &&
                  saved.payload.newAttempts == current.saved.payload.newAttempts &&
                  saved.payload.stopped.lastOption.exists(r => r.identity == initial.selected(index).identity && _not_executed(r))
                completed = saved.payload.receipts.size == index + 1 &&
                  saved.payload.receipts.dropRight(1) == current.saved.payload.receipts &&
                  _same_attempt_facts(saved.payload, current.saved.payload)
                rejected = saved.payload.admissionFailures.size == current.saved.payload.admissionFailures.size + 1 &&
                  saved.payload.admissionFailures.dropRight(1) == current.saved.payload.admissionFailures &&
                  saved.payload.admissionFailures.lastOption.exists(f => f.checkIdentity == initial.selected(index).identity && f.attempt == ordinal) &&
                  saved.payload.copy(admissionFailures = current.saved.payload.admissionFailures) == current.saved.payload
                uncertain = saved.payload.uncertainties.size == current.saved.payload.uncertainties.size + 1 &&
                  saved.payload.uncertainties.dropRight(1) == current.saved.payload.uncertainties &&
                  saved.payload.uncertainties.lastOption.exists(f => f.checkIdentity == initial.selected(index).identity && f.attempt == ordinal) &&
                  saved.payload.copy(uncertainties = current.saved.payload.uncertainties) == current.saved.payload
                workspace <- _workspace_c(initial.selected(index), initial)
                executions = if (initial.selected(index).dependency.nonEmpty && (stopped || completed))
                  current.saved.payload.dependencyExecutions :+ GoalPhaseDependencyValidation.Execution(
                    initial.selected(index).identity, ordinal, workspace.get,
                    initial.selected(index).kind.wire) else current.saved.payload.dependencyExecutions
                _ <- if (Vector(stopped, completed, rejected, uncertain).count(identity) == 1 &&
                    saved.payload.dependencyExecutions == executions) Consequence.unit
                  else F.invalid("Retained result rewrites its prefix or contradicts its single terminal receipt")
                next <- if (!stopped && !rejected) Consequence.success(Option.empty[Int])
                  else if (ordinal == Int.MaxValue) F.invalid("Retained check attempt ordinal is exhausted")
                  else Consequence.success(Some(ordinal + 1))
              } yield CheckProgress(saved, next)
            }
            latest <- recovery match {
              case None => Consequence.success(observed)
              case Some(saved) => for {
                _ <- if (observed.nextAttempt.isEmpty && observed.saved.payload.receipts.size == index &&
                    (result.isEmpty || observed.saved.payload.uncertainties.lastOption.exists(f =>
                      f.checkIdentity == initial.selected(index).identity && f.attempt == ordinal)) &&
                    saved != null && _compatible_payload(initial, saved.payload) &&
                    saved.payload.newAttempts.size == observed.saved.payload.newAttempts.size + 1 &&
                    saved.payload.newAttempts.dropRight(1) == observed.saved.payload.newAttempts &&
                    saved.payload.newAttempts.lastOption.exists(f => f.checkIdentity == initial.selected(index).identity &&
                      f.attempt == ordinal && f.intentRevision == intent.record.revision.value.toString) &&
                    saved.payload.copy(newAttempts = observed.saved.payload.newAttempts) == observed.saved.payload)
                  Consequence.unit else F.invalid("Retained new-attempt authorization has no exact unresolved selected intent")
                _ <- _adjacent_record_c(observed.saved.record, saved.record,
                  "ordered-validation:" + _attempt_key("recovery", index, ordinal), observed.saved.record.currentProgression)
                _ <- if (ordinal < Int.MaxValue) Consequence.unit else F.invalid("Retained check attempt ordinal is exhausted")
              } yield CheckProgress(saved, Some(ordinal + 1))
            }
          } yield latest
        }
      }
    }
  } yield progress

  private def _attempt_key(role: String, index: Int, attempt: Int): String =
    "check-" + role + "-" + index + (if (attempt == 0) "" else "-retry-" + attempt)

  private def _adjacent_record_c(previous: InstanceRecord, saved: InstanceRecord, cause: String,
    progression: Option[ProgressionReference]): Consequence[Unit] = for {
    _ <- if (previous != null && saved != null && previous.revision.value < Long.MaxValue &&
        saved.identity == previous.identity && saved.definition == previous.definition &&
        saved.revision.value == previous.revision.value + 1 && saved.history.size == previous.history.size + 1 &&
        saved.history.dropRight(1) == previous.history && saved.suspension.isEmpty &&
        saved.currentProgression == progression && saved.causation == CausationReference(cause) &&
        saved.lifecycle == Lifecycle.Active && saved.correlation == previous.correlation &&
        saved.derivedCompositeOccurrence == previous.derivedCompositeOccurrence &&
        saved.committedPredecessor == previous.committedPredecessor) Consequence.unit
      else F.invalid("Retained validation record is not its canonical contiguous native successor")
    _ <- previous.validateC
    _ <- saved.validateC
  } yield ()

  /** The Store first admits actual history. This classification supplies no execution authority. */
  private[admission] def continuable(saved: Saved): Boolean =
    saved != null && saved.record != null && _valid_payload(saved.payload) && saved.payload.outcome.isEmpty &&
      saved.record.validateC.isSuccess && saved.record.suspension.isEmpty &&
      saved.payload.sourceRevision.toLongOption.exists(v => v > 0 && v.toString == saved.payload.sourceRevision &&
        v < saved.record.revision.value && saved.record.history.exists(entry => entry.revision.value == v &&
          entry.currentProgression == saved.record.currentProgression && entry.suspension.isEmpty)) &&
      saved.record.currentProgression.exists(p => p == ProgressionReference("phase.Validation") ||
        p == ProgressionReference("phase.PhaseValidation")) && successful(saved.payload.receipts, saved.payload.selected) && {
        val payload = saved.payload
        val index = payload.receipts.size
        val pending = payload.selected.lift(index).map(_.identity)
        val recovered = payload.newAttempts.lastOption.exists(f => pending.contains(f.checkIdentity) &&
          saved.record.causation.value == "ordered-validation:" + _attempt_key("recovery", index, f.attempt))
        val rejected = payload.admissionFailures.lastOption.exists(f => pending.contains(f.checkIdentity) &&
          saved.record.causation.value == "ordered-validation:" + _attempt_key("result", index, f.attempt))
        val stopped = pending.exists { selected =>
          val count = payload.stopped.count(_.identity == selected)
          val attempt = count - 1 + payload.admissionFailures.count(_.checkIdentity == selected) +
            payload.newAttempts.count(_.checkIdentity == selected)
          count > 0 && saved.record.causation.value == "ordered-validation:" + _attempt_key("result", index, attempt)
        }
        val completed = payload.selected.lift(index - 1).exists { check =>
          val attempt = payload.stopped.count(_.identity == check.identity) +
            payload.admissionFailures.count(_.checkIdentity == check.identity) + payload.newAttempts.count(_.checkIdentity == check.identity)
          saved.record.causation.value == "ordered-validation:" + _attempt_key("result", index - 1, attempt)
        }
        recovered || rejected || stopped || completed
      }

  /** The Store has admitted the actual chain; only its missing mechanical outcome may settle. */
  private[admission] def completedFailurePending(saved: Saved): Boolean =
    saved != null && saved.record != null && _valid_payload(saved.payload) && saved.payload.outcome.isEmpty &&
      saved.record.validateC.isSuccess && saved.record.suspension.isEmpty &&
      saved.record.currentProgression.contains(ProgressionReference("phase.PhaseValidation")) &&
      saved.payload.sourceRevision.toLongOption.exists(v => v > 0 && v.toString == saved.payload.sourceRevision &&
        v < saved.record.revision.value && saved.record.history.exists(entry => entry.revision.value == v &&
          entry.currentProgression == saved.record.currentProgression && entry.suspension.isEmpty)) &&
      repairable(saved.payload) && {
        val payload = saved.payload
        val index = payload.receipts.size - 1
        index >= 0 && payload.selected.lift(index).exists { check =>
          val attempt = payload.stopped.count(_.identity == check.identity) +
            payload.admissionFailures.count(_.checkIdentity == check.identity) + payload.newAttempts.count(_.checkIdentity == check.identity)
          saved.record.causation.value == "ordered-validation:" + _attempt_key("result", index, attempt)
        }
      }

  private def _not_executed(receipt: V.Receipt): Boolean = receipt != null && receipt.termination == "not-started" &&
    receipt.exitCode != null && receipt.exitCode.isEmpty && receipt.stdout == "" && receipt.stderr == "" &&
    receipt.sbt != null && receipt.sbt.exists(f => f != null && f.valid && f.outcome == WorkflowSbtValidation.Outcome.NotExecuted)

  /** Reconcile ordinary records only; this never discovers metadata or executes a check. */
  private[admission] def reconcilePrefixC(frontier: InstanceRecord, initial: Payload,
    records: Vector[(Option[Saved], Option[Saved])], complete: Boolean): Consequence[Saved] = for {
    _ <- frontier.validateC
    _ <- if (_valid_payload(initial) && frontier.suspension.isEmpty &&
        frontier.currentProgression.exists(p => p == ProgressionReference("phase.Validation") ||
          p == ProgressionReference("phase.PhaseValidation")) && initial.sourceRevision == frontier.revision.value.toString &&
        initial.receipts.isEmpty && initial.stopped.isEmpty && initial.outcome.isEmpty && initial.metadata.isEmpty &&
        initial.admissionFailures.isEmpty && initial.uncertainties.isEmpty && initial.newAttempts.isEmpty &&
        initial.selected.forall(c => c.metadata.isEmpty && c.hostIdentity.isEmpty && c.operation.isEmpty) &&
        records != null && records.size == initial.selected.size) Consequence.unit
      else F.invalid("Retained ordinary prefix has no exact Validation input")
    reconciled <- records.zipWithIndex.foldLeft(Consequence.success((Saved(frontier, initial), false))) {
      case (prior, ((intent, result), index)) => prior.flatMap { case (current, stopped) =>
        if (intent.isEmpty && result.isEmpty) Consequence.success((current, true))
        else if (stopped || intent.isEmpty || current.payload.receipts.size != index ||
            !successful(current.payload.receipts, initial.selected))
          F.invalid("Retained ordinary prefix has a missing or noncontiguous intent/result pair")
        else for {
          prepared <- _prefix_record_c(current, intent.get, initial, current.payload.receipts)
          actual <- result match {
            case None => Consequence.success((prepared, true))
            case Some(saved) =>
              if (saved == null || saved.payload == null || saved.payload.receipts == null ||
                  saved.payload.receipts.size != index + 1 || saved.payload.receipts.dropRight(1) != current.payload.receipts)
                F.invalid("Retained ordinary result replaces its actual prior receipts")
              else _prefix_record_c(prepared, saved, initial, saved.payload.receipts).map(value =>
                (value, !successful(value.payload.receipts, initial.selected)))
          }
        } yield actual
      }
    }
    saved = reconciled._1
    _ <- if (!complete || (saved.payload.receipts.size == initial.selected.size &&
        successful(saved.payload.receipts, initial.selected))) Consequence.unit
      else F.invalid("Metadata selection requires the actual complete successful ordinary prefix")
  } yield saved

  private def _prefix_record_c(previous: Saved, saved: Saved, initial: Payload,
    receipts: Vector[V.Receipt]): Consequence[Saved] = for {
    _ <- if (saved != null && saved.record != null && _compatible_payload(initial, saved.payload) &&
        saved.payload.outcome.isEmpty && saved.payload.receipts == receipts && _same_attempt_facts(saved.payload, previous.payload) &&
        saved.record.identity == previous.record.identity && saved.record.definition == previous.record.definition &&
        saved.record.revision.value == previous.record.revision.value + 1 &&
        saved.record.currentProgression == previous.record.currentProgression && saved.record.suspension.isEmpty &&
        saved.record.history.size == previous.record.history.size + 1 &&
        saved.record.history.dropRight(1) == previous.record.history) Consequence.unit
      else F.invalid("Retained ordinary prefix crosses its actual source, candidate or native record")
    _ <- saved.record.validateC
  } yield saved

  private def _compatible_payload(initial: Payload, payload: Payload): Boolean =
    _valid_payload(payload) && payload.sourceRevision == initial.sourceRevision && payload.step == initial.step &&
      payload.candidate == initial.candidate && payload.selected == initial.selected && payload.metadata == initial.metadata &&
      payload.dependency == initial.dependency && payload.dependencySource == initial.dependencySource &&
      payload.sourceConsumption == initial.sourceConsumption

  private def _same_attempt_facts(left: Payload, right: Payload): Boolean =
    left.stopped == right.stopped && left.admissionFailures == right.admissionFailures &&
      left.uncertainties == right.uncertainties && left.newAttempts == right.newAttempts

  private def _attempt_facts_prefix(prefix: Payload, value: Payload): Boolean =
    value.dependencyExecutions.take(prefix.dependencyExecutions.size) == prefix.dependencyExecutions &&
    value.stopped.take(prefix.stopped.size) == prefix.stopped &&
      value.admissionFailures.take(prefix.admissionFailures.size) == prefix.admissionFailures &&
      value.uncertainties.take(prefix.uncertainties.size) == prefix.uncertainties &&
      value.newAttempts.take(prefix.newAttempts.size) == prefix.newAttempts

  /** Only an actual typed interrupted prefix can settle without a fresh host process. */
  private def _returned_interruption(payload: Payload): Boolean =
    _returned_failure(payload) && payload.outcome.isEmpty && payload.receipts.lastOption.exists { last =>
      Set("cancelled", "timed-out", "termination-unconfirmed", "output-limit-exceeded")(last.termination) &&
        last.exitCode.isEmpty && last.sbt.exists(_.outcome == WorkflowSbtValidation.Outcome.Incomplete)
    }

  private def _returned_failure(payload: Payload): Boolean =
    _valid_payload(payload) && payload.receipts.nonEmpty &&
      payload.receipts.forall(_.sbt.nonEmpty) && successful(payload.receipts.dropRight(1), payload.selected) && {
        val last = payload.receipts.last
        val facts = last.sbt.get
        (last.termination, last.exitCode, facts.outcome) match {
          case ("exited", Some(exit), WorkflowSbtValidation.Outcome.Failed) => exit != 0
          case ("indeterminate" | "cancelled" | "timed-out" | "termination-unconfirmed" | "output-limit-exceeded" | "launch-failed" | "artifact-limit-exceeded", None, WorkflowSbtValidation.Outcome.Incomplete) => true
          case ("exited", Some(0), WorkflowSbtValidation.Outcome.Unverified) => facts.kind == WorkflowSbtValidation.Kind.TestOnly
          case _ => false
        }
      }

  /** Classification of retained typed facts only; this grants no effect or retry authority. */
  private[admission] def repairable(payload: Payload): Boolean =
    _valid_payload(payload) && payload.receipts.nonEmpty &&
      payload.receipts.zip(payload.selected).forall { (receipt, check) =>
        receipt.sbt.exists(facts => facts != null && facts.valid && facts.kind == check.kind)
      } && successful(payload.receipts.dropRight(1), payload.selected) && {
        val last = payload.receipts.last
        last.termination == "exited" && last.exitCode.exists(_ != 0) &&
          last.sbt.exists(_.outcome == WorkflowSbtValidation.Outcome.Failed)
      }

  /** TimedOut is the producer's confirmed termination variant. Other incomplete
    * observations grant neither corrective adoption nor same-candidate retry.
    */
  private[admission] def terminalOutcome(payload: Payload): Option[TerminalOutcome] =
    if (payload == null) None
    else if (repairable(payload)) Some(TerminalOutcome.Failed)
    else Option.when(_valid_payload(payload) && payload.receipts.nonEmpty &&
      successful(payload.receipts.dropRight(1), payload.selected) &&
      payload.receipts.zip(payload.selected).forall { (receipt, check) =>
        receipt.sbt.exists(facts => facts != null && facts.valid && facts.kind == check.kind)
      } && payload.receipts.last.termination == "timed-out" && payload.receipts.last.exitCode.isEmpty &&
      payload.receipts.last.sbt.exists(_.outcome == WorkflowSbtValidation.Outcome.Incomplete))(
        TerminalOutcome.TerminatedTimeout)

  private[admission] def outputLimited(payload: Payload): Boolean = _valid_payload(payload) &&
    payload.receipts.nonEmpty && successful(payload.receipts.dropRight(1), payload.selected) &&
    payload.receipts.last.sbt.exists(_.outcome == WorkflowSbtValidation.Outcome.Incomplete) &&
    payload.receipts.last.processObservation.exists(_.termination match {
      case org.goldenport.cncf.processexecution.ProcessExecutionTermination.OutputLimitExceeded(_) => true
      case _ => false
    })

  /** One owner reconstructs new and retained outcomes; old failed records stay Decision. */
  private[admission] def observedStateC(controller: O.State, payload: Payload,
    receiptIdentity: String, changedPaths: Vector[String] = Vector.empty): Consequence[O.State] = {
    if (controller == null || !_valid_payload(payload) || !_text(receiptIdentity) ||
        !(controller.stage == O.Stage.Validation || controller.stage == O.Stage.PhaseValidation) ||
        payload.step != (if (controller.cursor == controller.plan.steps.size) "phase:" + controller.plan.phase
          else controller.plan.steps(controller.cursor).identity))
      F.invalid("native check outcome has no matching selected Validation frontier")
    else {
      val required = if (controller.cursor == controller.plan.steps.size) controller.plan.phaseChecks
        else controller.plan.steps(controller.cursor).checks
      val declared = N.declaredIdentities(payload.selected.filter(_.dependency.isEmpty))
      val full = declared == required
      val failurecoverage = full || (declared.nonEmpty && declared == required.take(declared.size) &&
        payload.selected.forall(c => c.metadata.isEmpty && c.hostIdentity.isEmpty && c.operation.isEmpty) && _returned_failure(payload))
      payload.outcome match {
      case Some("passed") if full && payload.receipts.size == payload.selected.size && successful(payload.receipts, payload.selected) =>
        if (controller.cursor < controller.plan.steps.size && controller.plan.steps(controller.cursor).actionPolicy.nonEmpty)
          GoalPhaseStepAssessment.assessC(controller, payload, receiptIdentity, changedPaths).flatMap { expected =>
            if (payload.assessment.contains(expected)) controller.acceptC(O.StepAssessed(expected))
            else F.invalid("formal Step assessment differs from the saved Plan and actual validation facts")
          }
        else if (payload.assessment.isEmpty) controller.acceptC(O.ChecksObserved(payload.candidate, required, receiptIdentity, true))
        else F.invalid("unselected assessment cannot replace the saved required-review contract")
      case Some("incomplete") if failurecoverage && outputLimited(payload) =>
        controller.acceptC(O.IncompleteChecksObserved(payload.candidate, required, receiptIdentity))
      case Some("failed") if failurecoverage && payload.receipts.nonEmpty && !successful(payload.receipts, payload.selected) =>
        controller.acceptC(O.ChecksObserved(payload.candidate, required, receiptIdentity, false))
      case Some(outcome) if failurecoverage && repairable(payload) &&
          (controller.cursor < controller.plan.steps.size || controller.releaseRepairActive) &&
          controller.effectiveRepairLimit > 0 && controller.generation < Long.MaxValue &&
          ((outcome == "repair-required" && controller.repairs < controller.effectiveRepairLimit) ||
            (outcome == "repair-limit-reached" && controller.repairs >= controller.effectiveRepairLimit)) =>
        val failed = payload.selected(payload.receipts.size - 1)
        val exit = payload.receipts.last.exitCode.get
        val scope = if (controller.cursor == controller.plan.steps.size) "Release" else "Step"
        val ownership = failed.dependency.flatMap(_ => GoalPhaseDependencySourceRevision.binding(
          payload.dependency, payload.dependencySource)).map(b =>
          "approved linked dependency " + b.dependency.repository + " at " +
            b.paths.mkString(", ")).getOrElse("selected " + scope + " " + payload.step)
        val directive = O.RepairDirective("Correct selected " + scope + " check " + failed.identity + " (" + failed.kind.wire +
          ") after actual exit " + exit,
          Vector(if (controller.repairControl.nonEmpty) "Native check " + failed.identity + " (" + failed.kind.wire +
            ") failed with actual exit " + exit + " within " + ownership
          else "Correct semantic source within " + ownership + " using the retained actual diagnostics for " +
            failed.identity + "; runtime must inspect the fresh candidate and rerun selected checks before independent review."))
        // A failed check identity and raw output do not provide a complete typed
        // unresolved-finding catalog. Budget applies; convergence stays unknown.
        controller.acceptC(O.RepairableChecksObserved(payload.candidate, required, receiptIdentity, directive,
          comparableFindings = false))
      case _ => F.invalid("native check outcome is incomplete or contradicts its actual selected receipts")
      }
    }
  }

  private def _valid_payload(payload: Payload): Boolean = payload != null && _text(payload.sourceRevision) &&
    _text(payload.step) && payload.candidate != null &&
    Vector(payload.candidate.head, payload.candidate.tree).forall(value => value != null && value.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")) &&
    payload.selected != null && payload.selected.nonEmpty && payload.selected.size <= 16 &&
    N.validateRuntimeC(payload.selected, payload.metadata).isSuccess &&
    payload.receipts != null && payload.stopped != null &&
    payload.dependency != null && payload.dependency.forall(GoalPhaseDependencyValidation.captureValid) &&
    payload.dependencySource != null && payload.dependencySource.forall(GoalPhaseDependencySourceRevision.captureValid) &&
    payload.sourceConsumption != null && payload.sourceConsumption.forall(GoalPhaseDependencySourceRevision.consumptionValid) &&
    payload.dependencySource.forall(c => payload.sourceConsumption.contains(GoalPhaseDependencySourceRevision.consumptionOf(c))) &&
    payload.sourceConsumption.forall(route => payload.selected.filter(_.dependency.isEmpty).forall(check =>
      route.programs.exists(p => p.check == check.identity && p.kind == check.kind.wire) &&
      (payload.receipts ++ payload.stopped).filter(_.identity == check.identity).forall(receipt =>
        receipt.processObservation.exists(process => route.programs.exists(p =>
          p.check == check.identity && p.kind == check.kind.wire && p.program == process.safeProgramIdentity))))) &&
    payload.selected.forall(check => _source_program(check, payload).forall(program =>
      (payload.receipts ++ payload.stopped).filter(_.identity == check.identity).forall(receipt =>
        receipt.processObservation.exists(_.safeProgramIdentity == program.program)))) &&
    !(payload.dependency.nonEmpty && payload.dependencySource.nonEmpty) &&
    payload.selected.forall(c => c.dependency != null && c.dependency.forall(identity =>
      GoalPhaseDependencySourceRevision.binding(payload.dependency, payload.dependencySource).exists(b =>
      b.identity == identity && b.checks.exists(_.copy(dependency = Some(identity)) == c)))) &&
    GoalPhaseDependencySourceRevision.binding(payload.dependency, payload.dependencySource).forall(b =>
      payload.selected.filter(_.dependency.nonEmpty) == b.checks.map(_.copy(dependency = Some(b.identity)))) &&
    payload.dependencyExecutions != null && payload.dependencyExecutions.forall(e =>
      GoalPhaseDependencyValidation.executionValid(e) && GoalPhaseDependencySourceRevision.binding(
        payload.dependency, payload.dependencySource).exists(b =>
        payload.selected.exists(c => c.dependency.nonEmpty && c.identity == e.check && c.kind.wire == e.operation &&
          _workspace_c(c, payload).toOption.contains(Some(e.workspace))))) &&
    payload.dependencyExecutions.map(e => e.check -> e.attempt).distinct.size == payload.dependencyExecutions.size &&
    payload.dependencyExecutions.size == (payload.receipts ++ payload.stopped).count(r =>
      payload.selected.exists(c => c.dependency.nonEmpty && c.identity == r.identity)) &&
    payload.receipts != null && payload.receipts.size <= payload.selected.size && payload.outcome != null &&
    payload.assessment != null && (payload.assessment.isEmpty || payload.outcome.contains("passed")) &&
    payload.outcome.forall(Set("passed", "failed", "incomplete", "repair-required", "repair-limit-reached")) &&
    payload.receipts.zip(payload.selected).forall { (receipt, check) =>
      receipt != null && receipt.identity == check.identity && receipt.exitCode != null && receipt.sbt != null &&
        V.storedProductOutputValid(receipt) &&
        receipt.termination != "not-started" &&
        receipt.sbt.forall(facts => facts != null && facts.valid && facts.kind == check.kind &&
          facts.outcome != WorkflowSbtValidation.Outcome.NotExecuted) &&
        (receipt.sbt.nonEmpty || check.kind == WorkflowSbtValidation.Kind.Test)
    } && payload.stopped != null && {
      val indices = payload.stopped.map(receipt =>
        if (receipt == null) -1 else payload.selected.indexWhere(_.identity == receipt.identity))
      indices.forall(index => index >= 0 && index <= payload.receipts.size &&
        (index < payload.receipts.size || (payload.outcome.isEmpty && successful(payload.receipts, payload.selected)))) &&
        indices.sliding(2).forall(pair => pair.size < 2 || pair.head <= pair.last) &&
        payload.stopped.zip(indices).forall { (receipt, index) => _not_executed(receipt) &&
          receipt.sbt.exists(_.kind == payload.selected(index).kind) }
    } && payload.admissionFailures != null && payload.uncertainties != null && payload.newAttempts != null && {
      def ordered(keys: Vector[(String, Int)]): Boolean = {
        val indices = keys.map((identity, attempt) => (payload.selected.indexWhere(_.identity == identity), attempt))
        keys.distinct.size == keys.size && indices.forall((index, attempt) => index >= 0 &&
          index <= payload.receipts.size && attempt >= 0 && attempt < Int.MaxValue) &&
          indices.sliding(2).forall(pair => pair.size < 2 || pair.head._1 < pair.last._1 ||
            (pair.head._1 == pair.last._1 && pair.head._2 < pair.last._2)) &&
          indices.forall((index, _) => index < payload.receipts.size ||
            (payload.outcome.isEmpty && successful(payload.receipts, payload.selected)))
      }
      val admission = payload.admissionFailures.map(f => if (f == null) ("", -1) else (f.checkIdentity, f.attempt))
      val uncertain = payload.uncertainties.map(f => if (f == null) ("", -1) else (f.checkIdentity, f.attempt))
      val recovery = payload.newAttempts.map(f => if (f == null) ("", -1) else (f.checkIdentity, f.attempt))
      ordered(admission) && ordered(uncertain) && ordered(recovery) && admission.intersect(uncertain).isEmpty &&
        admission.intersect(recovery).isEmpty &&
        payload.admissionFailures.forall(f => f != null && _stored_text(f.diagnostic) && f.diagnostic.trim.nonEmpty) &&
        payload.uncertainties.forall(f => f != null && _stored_text(f.diagnostic) && f.diagnostic.trim.nonEmpty) &&
        payload.newAttempts.forall(f => f != null && f.intentRevision != null &&
          f.intentRevision.toLongOption.exists(v => v > 0 && v.toString == f.intentRevision) &&
          Vector(f.evidenceIdentity, f.evidenceRevision, f.participant).forall(_text) &&
          _stored_text(f.rationale) && f.rationale.trim.nonEmpty)
    }
  private def _text(value: String): Boolean = value != null && value.trim.nonEmpty &&
    !value.exists(c => c == '\u0000' || c == '\r' || c == '\n')
  private def _stored_text(value: String): Boolean = value != null && !value.contains('\u0000') &&
    java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(value) &&
    value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 65536
  private[admission] def successful(receipts: Vector[V.Receipt], selected: Vector[Check]): Boolean =
    receipts != null && selected != null && receipts.size <= selected.size && receipts.zip(selected).forall((receipt, check) =>
      check != null && receipt != null && receipt.identity == check.identity && V.productSuccessful(receipt, check.kind))
  private def covers(owner: String, path: String): Boolean = path == owner || path.startsWith(owner + "/")
  private def id(value: String): Boolean = value != null && value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
}
