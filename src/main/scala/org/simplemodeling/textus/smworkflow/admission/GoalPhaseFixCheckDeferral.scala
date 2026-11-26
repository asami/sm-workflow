package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Codec, Json}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.Program
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.record.Record
import GoalPhaseNativeRun as F
import GoalPhaseOrderedProgression as O

/** Records the authority for deferring FixCheck at an unstarted whole-Phase candidate. */
private[smworkflow] object GoalPhaseFixCheckDeferral {
  val parameter = "sm-workflow.goal-phase.fix-check"
  val configuredReference = ContextReference(parameter, "false")
  val choice = "DeferPhaseFixCheck"
  val message = "Defer only whole-Phase FixCheck for the retained candidate. Ordinary Advance runs the remaining checks and requires independent Phase review before closure."
  val causationPrefix = "ordered-phase-fix-check-deferred:"
  private val _schema = "sm-workflow.goal-phase-fix-check-deferral.v1"

  final case class Target(schemaVersion: String, componentInstance: String, sessionId: String,
    sourceRevision: String, participant: String, planIdentity: String, planRevision: String,
    base: O.Snapshot, candidate: O.Snapshot, completed: Vector[O.ClosedStep],
    generation: Long, repairProgress: GoalPhaseRepairProgress.Snapshot, phaseChecks: Vector[String]) derives Codec.AsObject

  final case class Direction(target: Target, reference: ContextReference, rationale: String,
    configurationKey: Option[String] = None) derives Codec.AsObject

  def configuredDirection(target: Target): Direction =
    Direction(target, configuredReference, "The Phase host disabled whole-Phase FixCheck.", Some(parameter))

  val codec: WorkflowResultJsonV1.PayloadCodec[Direction] = new WorkflowResultJsonV1.PayloadCodec[Direction] {
    val typeIdentity = "GoalPhaseFixCheckDeferred"
    def encode(value: Direction): Json = value.asJson.deepDropNullValues
    def decode(value: Json): Either[String, Direction] = value.as[Direction].left.map(_.message).flatMap { direction =>
      Either.cond(direction.target.schemaVersion == _schema && encode(direction) == value, direction,
        "Invalid retained whole-Phase FixCheck direction")
    }
  }

  def decodeTarget(value: Json): Either[String, Target] = value.as[Target].left.map(_.message).flatMap { target =>
    Either.cond(target.schemaVersion == _schema && target.asJson.deepDropNullValues == value,
      target, "Whole-Phase FixCheck deferral requires its exact canonical target")
  }

  def targetC(value: Record): Consequence[Target] = if (value == null) F.invalid("FixCheck deferral target is absent")
    else value.toJsonStringC.flatMap(raw =>
    io.circe.parser.parse(raw).left.map(_.message).flatMap(GoalPhaseNativeMetadata.normalizePublicJson)
      .flatMap(decodeTarget).fold(F.invalid, Consequence.success))

  def targetRecord(value: Target): Record = GoalPhaseNativeMetadata.record(value.asJson.deepDropNullValues)

  def bindingC(component: String, target: Target, selected: String, rationale: String,
    participant: String, reference: ContextReference): Consequence[Json] =
    if (component == target.componentInstance && participant == target.participant && selected == choice &&
        GoalPhaseStepReviewRequest.validReason(rationale) && reference != null &&
        Vector(reference.identity, reference.revision).forall(value => value != null && value.nonEmpty &&
          value == value.trim && !value.exists(_.isControl)))
      Consequence.success(Json.obj("componentInstance" -> component.asJson,
        "validationRecovery" -> Json.obj("target" -> target.asJson.deepDropNullValues, "answer" -> Json.obj(
          "choice" -> selected.asJson, "rationale" -> rationale.asJson, "participant" -> participant.asJson,
          "reference" -> reference.asJson))))
    else F.invalid("Whole-Phase FixCheck deferral requires its exact Human direction")

  def targetC(session: F.Session, from: InstanceRecord, state: O.State,
    candidate: GoalPhaseNativeCandidate.Saved, core: ActionCall.Core, configured: Boolean = false,
    completedReleaseDependency: Option[InstanceRecord] = None): Consequence[Target] = for {
    snapshot <- state.candidate.filter(candidate.payload.candidate.contains).map(Consequence.success)
      .getOrElse(F.invalid("Whole-Phase FixCheck deferral requires the actual saved candidate"))
    _ <- if ((from == candidate.record || configured && completedReleaseDependency.contains(from)) &&
        from.lifecycle == Lifecycle.Active && from.suspension.isEmpty)
      Consequence.unit else F.invalid("Whole-Phase FixCheck deferral requires the original unissued candidate frontier")
    target = Target(_schema, core.component.get.core.instanceId.canonicalKey, from.identity.value,
      from.revision.value.toString, session.participantIdentity, state.plan.identity, state.plan.revision,
      state.base, snapshot, state.completed, state.generation, GoalPhaseRepairProgress.snapshot(state), state.plan.phaseChecks)
    reference = if (configured) configuredReference else ContextReference(from.identity.value, from.revision.value.toString)
    _ <- state.deferPhaseFixCheckC(reference).map(_ => ())
  } yield target

  def availableC(d: F.Declaration, state: O.State): Consequence[Vector[GoalPhaseNativeValidation.Check]] = for {
    _ <- if (d.phaseValidationPolicy.exists(p => !p.checks.exists(_.identity == "fix-check"))) Consequence.unit
      else F.invalid("Whole-Phase FixCheck deferral requires the missing fixed FixCheck binding")
    selected <- remainingChecksC(d, state)
  } yield selected

  def remainingChecksC(d: F.Declaration, state: O.State): Consequence[Vector[GoalPhaseNativeValidation.Check]] = for {
    selected <- GoalPhaseNativeValidation.selectedChecksC(d, state.plan, None, phaseFixCheckDeferred = true)
    _ <- if (selected.exists(_.kind == WorkflowSbtValidation.Kind.Compile) &&
        selected.exists(c => c.kind == WorkflowSbtValidation.Kind.Test && !c.carLint)) Consequence.unit
      else F.invalid("Whole-Phase FixCheck deferral requires retained Compile and FULL coverage")
  } yield selected

  def appendC(session: F.Session, d: F.Declaration, from: InstanceRecord, core: ActionCall.Core,
    direction: Direction): Consequence[Unit] = for {
    _ <- if (from == session.current) Consequence.unit else F.invalid("Whole-Phase FixCheck deferral frontier is stale")
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    _ <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, _reference(from), from,
      from.revision, _history(from), TypedValue(codec.typeIdentity, direction)),
      ConsequenceT.pure[[X] =>> Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
  } yield ()

  def retainedC(session: F.Session, from: InstanceRecord, state: O.State, candidate: GoalPhaseNativeCandidate.Saved,
    core: ActionCall.Core, completedReleaseDependency: Option[InstanceRecord]): Consequence[(InstanceRecord, O.State)] = for {
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    retained <- adapter.loadRetainedC(_reference(from))(using core.executionContext)
      .flatMap(_.map(Consequence.success).getOrElse(F.invalid("FixCheck deferral history has no retained direction")))
    direction = retained.receipt.payload.value
    target <- targetC(session, from, state, candidate, core, direction.configurationKey.nonEmpty, completedReleaseDependency)
    expected <- from.appendC(from.revision, _history(from))
    actual <- GoalPhaseNativeHistory.prefixC(session.current, expected.revision)
    _ <- if (actual == expected && retained.receipt.next == expected && direction.target == target) Consequence.unit
      else F.invalid("Retained FixCheck deferral crosses its actual native predecessor")
    _ <- direction.configurationKey match {
      case None => bindingC(target.componentInstance, target, choice, direction.rationale, target.participant, direction.reference).map(_ => ())
      case Some(_) if direction == configuredDirection(target) => Consequence.unit
      case Some(_) => F.invalid("Retained FixCheck configuration direction is invalid")
    }
    next <- state.deferPhaseFixCheckC(direction.reference)
  } yield actual -> next

  private def _reference(from: InstanceRecord): WorkflowMechanicalTransition.Reference =
    WorkflowMechanicalTransition.Reference(from.identity.value + ":" + causationPrefix + from.revision.value, "1")
  private def _history(from: InstanceRecord): HistoryEntry =
    F.history(from, "phase.PhaseValidation", causationPrefix + from.revision.value)
}
