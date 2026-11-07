package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import java.time.{Instant, LocalDate}
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall}
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.cncf.workspace.{FileTarget, WorkspaceId}
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse
import GoalPhaseNativeRun as F
import GoalPhaseOrderedHumanDecision as H
import GoalPhaseOrderedProgression as O
import GoalPhaseSourceHistoryNormalization as N
import GoalPhaseSourceHistoryRecovery as R

/** Same-authority mechanical selection has its own provenance. A caller's
  * finite transport observation is a fact, never a Human answer or File result.
  * @since   Nov.  6, 2026
  * @version Nov.  8, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseSourceHistoryAutomatic {
  val observationSchema = "sm-workflow.goal-phase-source-history-caller-observation.v1"
  val selectionSchema = "sm-workflow.goal-phase-source-history-automatic-selection.v1"
  val reconciliationSchema = "sm-workflow.goal-phase-source-history-automatic-reconciliation.v1"
  val causePrefix = "ordered-source-history-automatic:"
  final case class Invocation(componentInstance: String, sessionId: String, operation: String, identity: String)
  final case class CallerObservation(schemaVersion: String, target: N.Target, invocation: Invocation,
    sourceReference: ContextReference, observedAt: String, termination: String, exitCode: Option[Int])
  object CallerObservation {
    given Encoder[CallerObservation] = deriveEncoder[CallerObservation].mapJson(_.mapObject(fields =>
      if (fields("exitCode").exists(_.isNull)) fields.remove("exitCode") else fields))
    given Decoder[CallerObservation] = deriveDecoder
  }
  final case class Selection(target: N.Target, invocation: Invocation, observation: CallerObservation)
  final case class Saved(record: InstanceRecord, selection: Selection)
  final case class Reconciliation(selection: Selection, outcome: Option[String], diagnostic: Option[String])
  final case class Observed(record: InstanceRecord, reconciliation: Reconciliation) {
    def succeeded: Boolean = reconciliation.outcome.exists(Set("observed-current-postcondition", "updated-current"))
  }
  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  def validInvocation(value: Invocation): Boolean = value != null && _exact(value.componentInstance) &&
    _exact(value.sessionId) && value.operation == "GoalPhaseService.advanceGoalPhase" &&
    Option(value.identity).exists(_.matches("[0-9a-f]{32}"))
  def validObservation(value: CallerObservation): Boolean = value != null && value.schemaVersion == observationSchema &&
    N.validTarget(value.target) && value.target.resumeFrom.isEmpty && validInvocation(value.invocation) &&
    value.invocation.sessionId == value.target.recovery.sessionId && value.sourceReference != null &&
    _exact(value.sourceReference.identity) && _exact(value.sourceReference.revision) &&
    Option(value.observedAt).exists(at => scala.util.Try(Instant.parse(at)).toOption.exists(_.toString == at)) &&
    value.exitCode != null && (value.termination match {
      case "exited" => value.exitCode.nonEmpty
      case "live" | "unknown" => value.exitCode.isEmpty
      case _ => false
    })
  def decodeObservation(value: Json): Either[String, CallerObservation] = for {
    wire <- Option(value).toRight("Source-history caller observation is absent")
    publictarget <- wire.hcursor.get[Json]("target").left.map(_.message)
    target <- N.decodePublicTarget(publictarget)
    canonical = wire.mapObject(_.add("target", target.asJson))
    observation <- canonical.as[CallerObservation].left.map(_.message)
    _ <- Either.cond(validObservation(observation) && observation.asJson == canonical, (),
      "Source-history caller observation requires its complete closed canonical finite contract")
  } yield observation
  def validSelection(value: Selection): Boolean = value != null && validObservation(value.observation) &&
    value.target == value.observation.target && value.invocation == value.observation.invocation &&
    value.observation.termination == "exited" && value.target.recovery.intent.invocation.contains(value.invocation) &&
    value.target.recovery.intent.historyPolicy.contains(value.target.recovery.historyPolicy)

  /** One reference/event for the actual Advance call, shared by every file. */
  def invocationForCallC(core: ActionCall.Core, session: String): Consequence[Invocation] = for {
    _ <- if (core != null && core.executionContext != null && core.component.nonEmpty && _exact(session) &&
        core.executionContext.security.session.flatMap(_.sessionId).contains(session) &&
        core.action.request.service.contains("GoalPhaseService") && core.action.request.operation == "advanceGoalPhase")
      Consequence.unit else F.invalid("Source-history invocation requires the actual owning Advance and Session")
    invocation = Invocation(core.component.get.core.instanceId.canonicalKey, session,
      "GoalPhaseService.advanceGoalPhase", core.executionContext.idGeneration.opaqueId("sm-workflow-source-history-invocation"))
    _ <- Consequence { System.err.println(Json.obj("event" -> "goal-source-history-invocation".asJson,
      "invocation" -> invocation.asJson).noSpaces) }
  } yield invocation

  /** Resource acquisition stays in the executing Action and its ordinary UoW. */
  def inputForCallC(incoming: Request, call: ActionCall.Core): Consequence[Option[CallerObservation]] = {
    final class Reader extends ProcedureActionCall {
      val core = call
      def readC: Consequence[Option[CallerObservation]] = for {
        resource <- NativeWorkflowOperationInput.optionalTextC(incoming, "sourceHistoryObservationResource")
        observation <- resource match {
          case None => Consequence.success(Option.empty[CallerObservation])
          case Some(value) => for {
            reference <- NativeWorkflowResourceInput.referenceC(value)
            raw <- read_resource_text(reference, Some(UTF_8))
            parsed <- io.circe.parser.parse(raw).left.map(_.message).flatMap(decodeObservation)
              .fold(F.invalid, Consequence.success)
          } yield Some(parsed)
        }
      } yield observation
      def execute(): Consequence[OperationResponse] = F.invalid("Source-history observation reader is not an Operation")
    }
    new Reader().readC
  }
  def matchObservationC(component: String, target: N.Target, observation: CallerObservation): Consequence[Unit] =
    if (!N.validTarget(target) || target.resumeFrom.nonEmpty) F.invalid("Automatic source-history target is not an original retained frontier")
    else target.recovery.intent.invocation match {
      case None => F.invalid("Legacy source-history UNKNOWN has no retained invocation association; prior termination cannot be established")
      case Some(invocation) => if (validObservation(observation) && observation.target == target &&
          observation.invocation == invocation && invocation.componentInstance == component &&
          invocation.sessionId == target.recovery.sessionId &&
          target.recovery.intent.historyPolicy.contains(target.recovery.historyPolicy) && observation.termination == "exited")
        Consequence.unit else F.invalid("Source-history observation is live, unknown, stale or crosses its exact Component, Session, intent, invocation or retained rule")
    }
  def reference(target: N.Target): WorkflowMechanicalTransition.Reference = WorkflowMechanicalTransition.Reference(
    target.recovery.sessionId + ":ordered-source-history-automatic:" + target.recovery.sourceRevision, "1")
  def cause(target: N.Target): String = causePrefix + target.recovery.sourceRevision +
    (if (target.pendingQuestion.nonEmpty) ":superseded" else ":selected")
  val codec: WorkflowResultJsonV1.PayloadCodec[Selection] = new WorkflowResultJsonV1.PayloadCodec[Selection] {
    val typeIdentity = "GoalPhaseSourceHistoryAutomaticSelection"
    def encode(value: Selection): Json = Json.obj("schemaVersion" -> selectionSchema.asJson, "payload" -> value.asJson)
    def decode(value: Json): Either[String, Selection] = for {
      wire <- Option(value).toRight("Automatic source-history selection is absent")
      selected <- wire.hcursor.get[Selection]("payload").left.map(_.message)
      _ <- Either.cond(validSelection(selected) && encode(selected) == wire, (), "Automatic source-history selection is invalid or crossed")
    } yield selected
  }
  val reconciliationCodec: WorkflowResultJsonV1.PayloadCodec[Reconciliation] = new WorkflowResultJsonV1.PayloadCodec[Reconciliation] {
    val typeIdentity = "GoalPhaseSourceHistoryAutomaticReconciliation"
    def encode(value: Reconciliation): Json = Json.obj("schemaVersion" -> reconciliationSchema.asJson, "payload" -> value.asJson)
    def decode(value: Json): Either[String, Reconciliation] = for {
      wire <- Option(value).toRight("Automatic reconciliation is absent")
      result <- wire.hcursor.get[Reconciliation]("payload").left.map(_.message)
      _ <- Either.cond(validSelection(result.selection) && result.outcome != null && result.diagnostic != null &&
        result.outcome.forall(Set("observed-current-postcondition", "updated-current", "failed")) &&
        (if (result.outcome.contains("failed")) result.diagnostic.exists(_exact) else result.diagnostic.isEmpty) &&
        encode(result) == wire, (), "Automatic reconciliation is invalid")
    } yield result
  }
  private def _pure = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](())
  def selectC(source: N.Source, d: F.Declaration, core: ActionCall.Core,
    observation: CallerObservation): Consequence[Saved] = for {
    target <- N.currentTargetC(source, core)
    _ <- N.requireQuestionC(source.session, core, target, source.human)
    _ <- matchObservationC(core.component.get.core.instanceId.canonicalKey, target, observation)
    selected = Selection(target, observation.invocation, observation)
    _ <- codec.decode(codec.encode(selected)).fold(F.invalid, _ => Consequence.unit)
    _ <- if (domain.statemachine.GoalPhaseRunLifecycle.permits("InspectCandidate", "source-history-automatic-selected", "InspectCandidate"))
      Consequence.unit else F.invalid("Automatic source-history selection is absent from CML")
    next <- source.human match {
      case Some(human) => for {
        adapter <- source.session.storage.bindDecisionSupersessionC(H.completionOperation, H.inputCodec, codec)
        result <- adapter.executeC(H.identity(human), source.session.current.revision, source.session.current,
          F.history(source.session.current, "phase.InspectCandidate", cause(target)), TypedValue(codec.typeIdentity, selected))(using core.executionContext)
      } yield result.current
      case None => for {
        adapter <- source.session.storage.bindMechanicalC(F.advanceOperation, codec)
        result <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, reference(target), source.session.current,
          source.session.current.revision, F.history(source.session.current, "phase.InspectCandidate", cause(target)),
          TypedValue(codec.typeIdentity, selected)), _pure)(using core.executionContext)
      } yield result.receipt.next
    }
  } yield Saved(next, selected)

  /** No loose receipt lookup supplies authority: reconstruct the original
    * inspection/intent, exact pending question and immediate native edge. */
  def retainedC(session: F.Session, d: F.Declaration, core: ActionCall.Core, original: InstanceRecord,
    controller: O.State, documentpolicy: Option[GoalPhaseDocumentProgress.Policy], recordingpaths: Vector[String],
    completedcandidates: Vector[GoalPhaseNativeCandidate.Payload], human: Option[H.Source] = None,
    primaryRepair: Option[GoalPhasePrerequisitePrimaryRepair.Saved] = None): Consequence[Option[Saved]] = for {
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    mechanical <- adapter.loadRetainedC(WorkflowMechanicalTransition.Reference(original.identity.value +
      ":ordered-source-history-automatic:" + original.revision.value, "1"))(using core.executionContext)
    retained <- human match {
      case None => Consequence.success(mechanical.map(value => (value.receipt.next, value.receipt.payload.value)))
      case Some(question) => for {
        supersession <- session.storage.bindDecisionSupersessionC(H.completionOperation, H.inputCodec, codec)
        saved <- supersession.loadRetainedC(H.identity(question), session.current.identity)(using core.executionContext)
        result <- saved match {
          case None => Consequence.success(Option.empty[(InstanceRecord, Selection)])
          case Some(value) => for {
            next <- GoalPhaseNativeHistory.prefixC(session.current, value.receipt.nextRevision)
            _ <- if (value.receipt.sourceRevision.next == value.receipt.nextRevision) Consequence.unit
              else F.invalid("Automatic supersession is not adjacent to its pending question")
          } yield Some(next -> value.receipt.payload)
        }
      } yield result
    }
    result <- retained match {
      case None => Consequence.success(Option.empty[Saved])
      case Some((next, payload)) => for {
        selected <- codec.decode(codec.encode(payload)).fold(F.invalid, Consequence.success)
        recovery <- R._captured_target_c(session, d, core, original, controller, documentpolicy, recordingpaths,
          completedcandidates, selected.target.recovery, primaryRepair)
        previous <- GoalPhaseNativeHistory.prefixC(session.current, InstanceRevision(selected.target.frontierRevision.toLong))
        expected = N.Target(recovery, previous.revision.value.toString, previous.causation.value, N.policy,
          human.map(question => N.Question(H.identity(question).value, previous.revision.value.toString)))
        _ <- N.requireQuestionC(session, core, expected, human)
        _ <- matchObservationC(core.component.get.core.instanceId.canonicalKey, expected, selected.observation)
        actual <- R._adjacent_c(session.current, previous, next, cause(expected))
        _ <- if (selected.target == expected && actual.lifecycle == Lifecycle.Active && actual.suspension.isEmpty &&
            previous.identity == original.identity && actual.correlation == previous.correlation &&
            actual.derivedCompositeOccurrence == previous.derivedCompositeOccurrence &&
            actual.committedPredecessor == previous.committedPredecessor) Consequence.unit
          else F.invalid("Automatic selection crosses its actual native predecessor, target or authority")
      } yield Some(Saved(actual, selected))
    }
  } yield result
  private def _reconciliation_reference(saved: Saved, kind: String): WorkflowMechanicalTransition.Reference =
    WorkflowMechanicalTransition.Reference(reference(saved.selection.target).identity + ":reconciliation-" + kind, "1")
  private def _reconciliation_cause(kind: String): String = causePrefix + "reconciliation:" + kind
  def retainedObservationC(session: F.Session, core: ActionCall.Core, saved: Saved): Consequence[Option[Observed]] = for {
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, reconciliationCodec)
    intent <- adapter.loadRetainedC(_reconciliation_reference(saved, "intent"))(using core.executionContext)
    observed <- intent match {
      case None => Consequence.success(Option.empty[Observed])
      case Some(value) => for {
        expected <- Consequence.success(Reconciliation(saved.selection, None, None))
        _ <- if (value.receipt.payload.value == expected) Consequence.unit else F.invalid("Automatic reconciliation intent differs")
        intended <- R._adjacent_c(session.current, saved.record, value.receipt.next, _reconciliation_cause("intent"))
        result <- adapter.loadRetainedC(_reconciliation_reference(saved, "result"))(using core.executionContext)
        actual <- result match {
          case None => Consequence.success(Observed(intended, expected))
          case Some(value) => for {
            payload <- reconciliationCodec.decode(reconciliationCodec.encode(value.receipt.payload.value)).fold(F.invalid, Consequence.success)
            _ <- if (payload.copy(outcome = None, diagnostic = None) == expected && payload.outcome.nonEmpty) Consequence.unit
              else F.invalid("Automatic reconciliation result crosses its exact new intent")
            next <- R._adjacent_c(session.current, intended, value.receipt.next, _reconciliation_cause("result"))
          } yield Observed(next, payload)
        }
      } yield Some(actual)
    }
  } yield observed
  def reconcileC(session: F.Session, d: F.Declaration, core: ActionCall.Core, saved: Saved,
    acknowledgement: String => Consequence[Unit]): Consequence[Observed] = for {
    retained <- retainedObservationC(session, core, saved)
    result <- retained match {
      case Some(value) => Consequence.success(value)
      case None => for {
        _ <- if (session.current == saved.record && validSelection(saved.selection)) Consequence.unit
          else F.invalid("Automatic reconciliation requires its exact selected terminal-observation frontier")
        adapter <- session.storage.bindMechanicalC(F.advanceOperation, reconciliationCodec)
        initial = Reconciliation(saved.selection, None, None)
        intended <- _save_c(adapter, d, core, saved, saved.record, "intent", initial)
        _ <- acknowledgement("goal-source-history-automatic-prepared")
        actual <- GoalPhaseNativeSourceHistory.verifyCompletedPrefixC(core, saved.selection.target.recovery)
          .map[Consequence[GoldenportSourceHistoryDriver.Normalization]](_ => N.normalizeC(core, saved.selection.target))
          .fold(error => Consequence.success(GoldenportSourceHistoryDriver.Normalization("failed", Some(error.toString.take(8192)))), identity)
        _ <- acknowledgement("goal-source-history-automatic-executed")
        payload = initial.copy(outcome = Some(actual.outcome), diagnostic = actual.diagnostic)
        recorded <- _save_c(adapter, d, core, saved, intended, "result", payload)
        _ <- acknowledgement("goal-source-history-automatic-recorded")
      } yield Observed(recorded, payload)
    }
  } yield result
  private def _save_c(adapter: WorkflowManagedMechanicalTransition[Reconciliation], d: F.Declaration, core: ActionCall.Core,
    saved: Saved, from: InstanceRecord, kind: String, payload: Reconciliation): Consequence[InstanceRecord] =
    adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, _reconciliation_reference(saved, kind), from,
      from.revision, F.history(from, "phase.InspectCandidate", _reconciliation_cause(kind)),
      TypedValue(reconciliationCodec.typeIdentity, payload)), _pure)(using core.executionContext).map(_.receipt.next)

  def currentObservationC(core: ActionCall.Core, target: N.Target): Consequence[String] = for {
    rule <- WorkflowSourceHistoryRule.decodeC(target.recovery.historyPolicy)
    workspace <- WorkspaceId.parseC("sm-workflow-repository")
    observed <- GoldenportSourceHistoryDriver.observeC(core, FileTarget(workspace), rule,
      WorkflowSourceHistory.Source(target.recovery.intent.path, LocalDate.parse(target.recovery.intent.updatedOn),
        None, None, target.recovery.intent.dateUncertain))
  } yield "Read-only current managed postcondition: " + observed.outcome + observed.diagnostic.fold("")("; " + _) +
    ". This does not prove the old File outcome or invocation termination."
}
