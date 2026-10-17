package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import GoalPhaseOrderedProgression as O
import GoalPhaseDocumentSource as D

/** Pure document compatibility policy; no acquisition, issue, decision or writeback.
  * @since   Oct. 17, 2026
  * @version Oct. 17, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseDocumentRefresh {
  final case class Adopted(controller: O.State, documents: D.Bound)

  def evaluateC(controller: O.State, previous: D.Bound, proposed: O.Plan,
    documents: D.Bound, issued: Boolean): Consequence[Either[Vector[String], Adopted]] = for {
    _ <- if (controller != null) Consequence.unit else _invalid("controller is absent")
    _ <- D.instructionsC(Some(previous), None)
    _ <- D.instructionsC(Some(documents), None)
    refreshed <- controller.refreshedC(proposed, issued)
    _ <- if (previous.bindings.map(_.step) == controller.plan.steps.map(_.identity) &&
        documents.bindings.map(_.step) == proposed.steps.map(_.identity)) Consequence.unit
      else _invalid("document Step bindings differ from their selected Plan")
    _ <- if (documents.source == previous.source) Consequence.unit
      else _invalid("document source differs from the original selection")
  } yield {
    val protectedsteps = if (controller.phaseAcceptanceLocked(issued)) controller.plan.steps
      else controller.plan.steps.take(controller.protectedStepCount(issued))
    val documentconflicts = protectedsteps.filter { step =>
      _acceptance(previous, step.identity) != _acceptance(documents, step.identity)
    }.map(step => "document-acceptance:" + step.identity)
    val conflicts = (refreshed.fold(identity, _ => Vector.empty) ++ documentconflicts).distinct
    if (conflicts.nonEmpty) Left(conflicts)
    else refreshed.map(state => Adopted(state, documents))
  }

  private def _acceptance(value: D.Bound, step: String): Option[Map[String, String]] =
    value.bindings.find(_.step == step).map { binding =>
      val items = value.items.map(item => item.identity -> item.acceptance).toMap
      binding.items.map(id => id -> items(id)).toMap
    }
  private def _invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("GoalPhase document refresh: " + reason)
}
