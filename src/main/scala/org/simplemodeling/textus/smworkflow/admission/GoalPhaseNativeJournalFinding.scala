package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import DevelopmentJournal as J
import DevelopmentJournalFinding as K
import GoalPhaseNativeRun as F
import GoalPhaseNativeFindingRecording as N

/** Native intent/result owner for automatic immutable Finding recording.
  * A missing terminal receipt is unknown and never authorizes another append.
  * @since   Oct. 20, 2026
  * @version Oct. 20, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseNativeJournalFinding {
  final case class Policy(storeName: String, collection: String)
  final case class Payload(sourceRevision: String, context: K.Context, policy: Policy,
    pending: Vector[N.Pending], outcomes: Option[Vector[K.RecordingState]])
  object Payload {
    import K.given
    private given Encoder[ContextReference] = deriveEncoder
    private given Decoder[ContextReference] = deriveDecoder
    private given Encoder[N.Pending] = deriveEncoder
    private given Decoder[N.Pending] = deriveDecoder
    private given Encoder[Policy] = deriveEncoder
    private given Decoder[Policy] = deriveDecoder
    given Encoder[Payload] = deriveEncoder
    given Decoder[Payload] = deriveDecoder
  }
  final case class Saved(record: InstanceRecord, payload: Payload, policyMatches: Boolean)
  private[admission] val codec = new WorkflowResultJsonV1.PayloadCodec[Payload] {
    val typeIdentity = "GoalPhaseNativeJournalFinding"
    def encode(value: Payload): Json = Json.obj("schemaVersion" ->
      Json.fromString("sm-workflow.goal-phase-native-journal-finding.v1"), "payload" -> value.asJson)
    def decode(value: Json): Either[String, Payload] = for {
      wire <- Option(value).toRight("native Journal Finding is missing")
      schema <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(schema == "sm-workflow.goal-phase-native-journal-finding.v1", (), "native Journal Finding schema differs")
      payload <- wire.hcursor.get[Payload]("payload").left.map(_.message)
      _ <- Either.cond(encode(payload) == wire && _valid(payload), (), "native Journal Finding payload is invalid or noncanonical")
    } yield payload
  }
  def reference(from: InstanceRecord, key: String): WorkflowMechanicalTransition.Reference =
    WorkflowMechanicalTransition.Reference(from.identity.value + ":ordered-journal-findings:" + from.revision.value + ":" + key, "1")

  def retainedC(source: N.Source, d: F.Declaration, core: ActionCall.Core): Consequence[Option[Saved]] = for {
    _ <- _frontier_c(source)
    adapter <- source.session.storage.bindMechanicalC(F.advanceOperation, codec)
    intent <- adapter.loadRetainedC(reference(source.from, "intent"))(using core.executionContext)
    retained <- intent match {
      case None => Consequence.success(Option.empty[Saved])
      case Some(value) => for {
        payload <- Consequence.success(value.receipt.payload.value)
        _ <- if (_valid(payload) && payload.outcomes.isEmpty && payload.sourceRevision == source.from.revision.value.toString &&
            payload.context == K.context(source) && payload.pending == source.findings.pending) Consequence.unit
          else F.invalid("retained Journal Finding differs from its actual eligible source and complete pending tuple")
        prepared <- _entry_c(source.session, source.from, value.receipt.next, "ordered-journal-findings-intent")
        result <- adapter.loadRetainedC(reference(source.from, "result"))(using core.executionContext)
        saved <- result match {
          case None => Consequence.success(Saved(prepared, payload, _matches(payload.policy, d.journalPolicy)))
          case Some(recorded) => for {
            terminal <- Consequence.success(recorded.receipt.payload.value)
            _ <- if (_valid(terminal) && terminal.outcomes.nonEmpty && terminal.copy(outcomes = None) == payload)
              Consequence.unit else F.invalid("retained Journal Finding result differs from its prepared intent")
            current <- _entry_c(source.session, prepared, recorded.receipt.next, "ordered-journal-findings-result")
          } yield Saved(current, terminal, _matches(payload.policy, d.journalPolicy))
        }
      } yield Some(saved)
    }
  } yield retained

  def advanceC(source: N.Source, d: F.Declaration, core: ActionCall.Core,
    acknowledge: String => Consequence[Unit]): Consequence[Saved] = for {
    _ <- _frontier_c(source)
    policy <- d.journalPolicy.map(Consequence.success).getOrElse(F.invalid("automatic Finding requires the declared Journal"))
    retained <- retainedC(source, d, core)
    saved <- retained match {
      case Some(value) => Consequence.success(value)
      case None => for {
        adapter <- source.session.storage.bindMechanicalC(F.advanceOperation, codec)
        payload = Payload(source.from.revision.value.toString, K.context(source),
          Policy(policy.storeName, policy.collection.print), source.findings.pending, None)
        _ <- if (_valid(payload)) Consequence.unit else F.invalid("automatic Finding intent is invalid")
        prepared <- _save_c(adapter, d, core, source.from, source.from, "intent", payload)
        _ <- _ack_c(acknowledge, "goal-journal-findings-prepared")
        outcomes <- payload.pending.foldLeft(Consequence.success(Vector.empty[K.RecordingState])) { (previous, pending) =>
          previous.flatMap(values => K.recordC(policy, core, payload.context, pending).map(values :+ _))
        }
        terminal = payload.copy(outcomes = Some(outcomes))
        _ <- if (_valid(terminal)) Consequence.unit else _uncertain("Journal Finding results lack a complete known bounded outcome")
        recorded <- _save_c(adapter, d, core, source.from, prepared.record, "result", terminal)
        _ <- _ack_c(acknowledge, "goal-journal-findings-recorded")
      } yield recorded
    }
  } yield saved

  def completed(saved: Saved): N.Journaled = N.Journaled(saved.payload.sourceRevision,
    saved.record.revision.value.toString, saved.payload.pending, saved.payload.outcomes.get)

  private def _matches(policy: Policy, current: Option[J.Policy]): Boolean =
    current.exists(p => policy == Policy(p.storeName, p.collection.print))
  private def _valid(payload: Payload): Boolean = try {
    payload != null && payload.sourceRevision == payload.context.sourceRevision && K.validateContextC(payload.context).isSuccess &&
      payload.policy != null && J.decodePolicyC(Json.obj("schemaVersion" -> Json.fromString(J.schemaVersion),
        "storeName" -> Json.fromString(payload.policy.storeName), "collection" -> Json.fromString(payload.policy.collection))).isSuccess &&
      payload.pending != null && payload.pending.nonEmpty && payload.pending.size <= 256 &&
      payload.pending.forall(p => p != null && p.stepIdentity == payload.context.stepIdentity &&
        K.entryC(payload.context, p, java.time.Instant.EPOCH).isSuccess) &&
      payload.pending.map(p => (p.sourceReference, p.observation.identity)).distinct.size == payload.pending.size &&
      payload.outcomes != null && payload.outcomes.forall(results => results != null && results.size == payload.pending.size &&
        results.zip(payload.pending).forall { (result, pending) => result != null && result.sourceReference == pending.sourceReference &&
          result.message != null && result.message.trim.nonEmpty && result.entry != null && (result.status match {
            case "recorded" | "alreadyRecorded" => result.entry.contains(K.EntryReference(
              K.identity(pending.sourceReference, pending.observation.identity), "1"))
            case "unavailable" | "conflict" | "indeterminate" => result.entry.isEmpty
            case _ => false
          })
        })
  } catch { case scala.util.control.NonFatal(_) => false }
  private def _frontier_c(source: N.Source): Consequence[Unit] =
    if (N.eligible(source) && source.findings.pending.size <= 256 && source.from.currentProgression.contains(
        ProgressionReference("phase." + source.controller.stage.toString))) Consequence.unit
    else F.invalid("automatic Finding is outside the bounded actual eligible native frontier")
  private def _entry_c(session: F.Session, from: InstanceRecord, saved: InstanceRecord,
    cause: String): Consequence[InstanceRecord] = for {
    following <- GoalPhaseNativeHistory.nextC(session.current, from)
    _ <- if (saved.identity == from.identity && saved.definition.workflowIdentity == from.definition.workflowIdentity &&
        saved.definition.workflowRevision == from.definition.workflowRevision && following.exists(entry =>
          entry.revision == saved.revision && entry.revision == from.revision.next && entry.causation.value == cause &&
          entry.suspension.isEmpty && entry.currentProgression == from.currentProgression)) Consequence.unit
      else F.invalid("Journal Finding has no exact contiguous actual native successor")
    actual <- GoalPhaseNativeHistory.prefixC(session.current, saved.revision)
    _ <- if (actual.history == saved.history && actual.suspension.isEmpty && actual.currentProgression == from.currentProgression)
      Consequence.unit else F.invalid("Journal Finding receipt differs from actual native history")
  } yield actual
  private def _save_c(adapter: WorkflowManagedMechanicalTransition[Payload], d: F.Declaration, core: ActionCall.Core,
    source: InstanceRecord, from: InstanceRecord, key: String, payload: Payload): Consequence[Saved] = for {
    _ <- if (domain.statemachine.GoalPhaseRunLifecycle.permits(from.currentProgression.get.value.stripPrefix("phase."),
      "journal-findings-" + key, from.currentProgression.get.value.stripPrefix("phase."))) Consequence.unit
      else F.invalid("Journal Finding same-stage edge is absent from CML")
    saved <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, reference(source, key), from, from.revision,
      F.history(from, from.currentProgression.get.value, "ordered-journal-findings-" + key), TypedValue(codec.typeIdentity, payload)),
      ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
  } yield Saved(saved.receipt.next, saved.receipt.payload.value, true)
  private def _ack_c(acknowledge: String => Consequence[Unit], point: String): Consequence[Unit] = acknowledge(point) match {
    case Consequence.Success(_) => Consequence.unit
    case _ => _uncertain("Journal Finding acknowledgement interrupted; retained intent is not replayed: " + point)
  }
  private def _uncertain[A](reason: String): Consequence[A] =
    org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate(reason)
}
