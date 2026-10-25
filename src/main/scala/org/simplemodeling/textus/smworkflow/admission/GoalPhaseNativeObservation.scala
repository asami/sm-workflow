package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import DevelopmentOperationalView as V

/** Reads only canonical retained records. No progression, Git, acknowledgement
  * or provider port is reachable from this adapter.
  * @since Oct. 25, 2026
  * @version Oct. 25, 2026
  * @author ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseNativeObservation {
  final case class Observation(status: String, state: Option[V.State] = None)
  def projectC(record: InstanceRecord): Consequence[V.State] = for {
    _ <- record.validateC
    stage <- record.currentProgression match {
      case None => Consequence.success(Option.empty[String])
      case Some(value) if value.value.startsWith("phase.") &&
          GoalPhaseOrderedProgression.Stage.values.exists(_.toString == value.value.drop(6)) =>
        Consequence.success(Some(value.value.drop(6)))
      case _ => Consequence.stateConflict("Unknown native progression")
    }
  } yield V.State(record.revision.value.toString, record.lifecycle.value, stage,
    if (record.suspension.exists(_.completion.value == "goal-phase-ordered-human-completion-v1")) "human-required"
    else if (stage.contains("Repair")) "repair-required"
    else if (record.suspension.nonEmpty) "unknown" else "none")

  def readC(subject: V.Subject, declaration: GoalPhaseNativeRun.Declaration,
    definition: DefinitionBinding, core: ActionCall.Core): Consequence[Observation] = for {
    retained <- GoalPhaseNativeRun.retainedSessionOptionC(subject.sessionId, declaration, definition, core)
    result <- retained match {
      case None => Consequence.success(Observation("not-found"))
      case Some(session) => for {
        _ <- if (session.current.identity.value == subject.sessionId &&
            session.started.receipt.payload.value.plan.repository == subject.repositoryIdentity &&
            session.started.receipt.payload.value.plan.phase == subject.phaseIdentity) Consequence.unit
          else Consequence.stateConflict("Native observation subject differs")
        state <- projectC(session.current)
        instances <- session.storage.bindInstancesC()(using core.executionContext)
        finalrecord <- instances.loadC(session.current.identity)(using core.executionContext)
      } yield consistent(session.current, finalrecord, state)
    }
  } yield result

  /** One recheck only; a concurrent change never leaks the stale projection. */
  private[admission] def consistent(selected: InstanceRecord, finalrecord: Option[InstanceRecord],
    state: V.State): Observation =
    if (finalrecord.contains(selected)) Observation("observed", Some(state)) else Observation("changing")
}
