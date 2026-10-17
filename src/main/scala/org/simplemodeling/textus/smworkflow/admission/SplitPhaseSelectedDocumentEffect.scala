package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import SplitPhaseManagedPlanningFrontier as F
import WorkflowSelectedDocumentFiles as Files

/** Connects an actual native selected APPLY source to current-UoW File effects.
  * This supplies no intent publication or native completion; the owning advance
  * must publish its durable ApplyingDocuments preparation before applyC.
  * @since   Oct.  6, 2026
  * @version Oct. 15, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object SplitPhaseSelectedDocumentEffect {
  val applyingProgression = "entry.ApplyingDocuments"
  enum Status { case Ready, Partial, Applied, Conflict }
  final case class Observation(status: Status, payload: Json)
  final class Prepared private[SplitPhaseSelectedDocumentEffect] (
    val source: F.Observed, val request: Files.Request, val snapshot: SplitPhasePlanningSnapshot.Record, val repository: String,
    val instance: String, val context: Json, val projection: SplitPhaseSelectedDocuments.Projection)

  def prepareC(source: F.Observed, context: String, revision: String): Consequence[Prepared] =
    if (source == null) Consequence.stateConflict("Selected document effect requires its actual native source")
    else for {
      _ <- if (source.invocationMode == "APPLY") Consequence.unit
        else Consequence.stateConflict("Selected PREVIEW supplies no document application authority")
      projection <- SplitPhaseSelectedDocuments.projectC(source.snapshot)
      repositories = projection.documents.map(_.contract.hcursor.downField("validation")
        .downField("repository").get[String]("value").toOption).distinct
      repository <- repositories match {
        case Vector(Some(value)) => Consequence.success(value)
        case _ => Consequence.serviceUnavailable("Selected document effect requires one configured repository")
      }
      request = Files.Request(repository, source.current.identity.value, context, revision, source.snapshot)
      _ <- Files.validateC(request)
    } yield new Prepared(source, request, source.snapshot, repository, source.current.identity.value,
      Json.obj("identity" -> Json.fromString(context), "revision" -> Json.fromString(revision)), projection)

  def observeC(prepared: Prepared, core: ActionCall.Core): Consequence[Observation] =
    if (prepared == null) Consequence.stateConflict("Selected document effect preparation is absent")
    else Files.observeC(core, prepared.request)

  def applyC(prepared: Prepared, core: ActionCall.Core): Consequence[Observation] =
    if (prepared == null || !prepared.source.current.currentProgression.exists(_.value == applyingProgression))
      Consequence.stateConflict("Selected document application requires its actual native ApplyingDocuments intent")
    else Files.applyC(core, prepared.request)
}
