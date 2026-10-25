package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.ContextReference
import org.simplemodeling.textus.smworkflow.value as V

/** Bounded context reconstructed by the ordered controller from admitted failures.
  * No observation, direction or context value grants execution authority.
  * @since   Oct. 25, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseRuntimeRepairControl {
  val version = "sm-workflow.repair-trend.v1"
  val ceiling = 10
  val window = 4
  final case class RestorationLink(producerSession: String, producerRepository: String,
    consumerSession: String, consumerRepository: String, purpose: String)
  object RestorationLink {
    given Encoder[RestorationLink] = deriveEncoder
    given Decoder[RestorationLink] = deriveDecoder
  }
  final case class Observation(scope: String, reference: String, findings: Set[String])
  object Observation {
    given Encoder[Observation] = deriveEncoder
    given Decoder[Observation] = deriveDecoder
  }
  final case class Context(policy: String, selectedLimit: Int, observations: Vector[Observation] = Vector.empty,
    restoration: Option[RestorationLink] = None) {
    def effectiveLimit: Int = math.min(selectedLimit, ceiling)
    def observe(scope: String, reference: String, findings: Vector[String]): Context = {
      val comparable = observations.filter(_.scope == scope)
      copy(observations = (comparable :+ Observation(scope, reference, findings.toSet)).takeRight(window))
    }
    def stop: Option[String] = {
      val sets = observations.map(_.findings)
      if (sets.size != window || sets.exists(_.isEmpty)) None
      else {
        val reason = if (sets.distinct.size == 1) Some("sustained recurrence/stall")
          else if (sets(0) == sets(2) && sets(1) == sets(3) && sets(0) != sets(1)) Some("oscillation")
          else if (sets.sliding(2).forall(pair => pair(0).subsetOf(pair(1)) && pair(0) != pair(1)))
            Some("sustained regression")
          else None
        reason.map(value => policy + ": " + value + "; evidence=" + observations.map(_.reference).mkString(", "))
      }
    }
  }
  def exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  def validLink(value: RestorationLink): Boolean = value != null &&
    Vector(value.producerSession, value.producerRepository, value.consumerSession, value.consumerRepository, value.purpose).forall(exact) &&
      value.producerSession != value.consumerSession

  /** Called after actual effects/directions only, never by retained traversal.
    * Recording outcome is independent of the already saved business outcome. */
  def recordC(d: GoalPhaseNativeRun.Declaration, core: ActionCall.Core, session: String,
    reference: ContextReference, outcome: String, facts: Json): Consequence[Vector[V.EvidenceRecordingState]] =
    d.evidencePolicy match {
      case None => Consequence.success(Vector.empty)
      case Some(policy) =>
        val source = CommonEvidence.Source(reference, d.input.repositoryIdentity, d.input.phaseIdentity,
          None, session, "repair-control", outcome)
        try CommonEvidence.archiveManagedC(CommonEvidence.storage(core, policy), policy, source,
          facts.deepMerge(Json.obj("callerOrigin" -> Json.fromString("unknown"),
            "causeResolution" -> Json.fromString("unassessed"))).noSpaces, "identity", core.executionContext.clock).map {
          case (state, Some((allocation, observation))) =>
            val unavailable = V.JournalRecordingState("unavailable", "RRC Evidence Journal recording unavailable; business outcome retained",
              None, V.WorkflowArtifactReference(observation.identity + ":storage", "1"))
            val journal = try DevelopmentJournalEvidence.recordC(d.journalPolicy, core, allocation, observation)
              .fold(_ => unavailable, identity) catch { case scala.util.control.NonFatal(_) => unavailable }
            Vector(state.copy(journal = Some(journal)))
          case (state, None) => Vector(state)
        }.recoverWith(_ => Consequence.success(Vector(V.EvidenceRecordingState("indeterminate",
          "RRC Evidence recording did not confirm; business outcome retained", None, None, None))))
        catch { case scala.util.control.NonFatal(_) => Consequence.success(Vector(V.EvidenceRecordingState("indeterminate",
          "RRC Evidence recording interrupted; business outcome retained", None, None, None))) }
    }

  def facts(controller: GoalPhaseOrderedProgression.State): Json = Json.obj(
    "policy" -> controller.repairControl.map(c => Json.fromString(c.policy)).getOrElse(Json.Null),
    "originalSelectedLimit" -> Json.fromInt(controller.plan.repairLimit),
    "selectedLimit" -> Json.fromInt(controller.selectedRepairLimit),
    "effectiveLimit" -> Json.fromInt(controller.effectiveRepairLimit),
    "automaticCount" -> Json.fromInt(controller.repairs), "ordinaryCount" -> Json.fromInt(controller.ordinaryRepairs),
    "stage" -> Json.fromString(controller.stage.toString),
    "decision" -> controller.decision.map(Json.fromString).getOrElse(Json.Null),
    "observations" -> controller.repairControl.toVector.flatMap(_.observations).asJson,
    "restoration" -> controller.repairControl.flatMap(_.restoration).asJson)
}
