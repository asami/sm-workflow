package org.simplemodeling.textus.smworkflow.caller

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.value as V
import org.simplemodeling.textus.smworkflow.admission.GoalPhaseOperatingProfiles
import WorkflowExecutionHarness as H

/** Caller library bridge over the actual public Request context. Operational
  * completion supplies observations only; normal Submit owns semantic admission.
  * @since   Oct. 16, 2026
  *  version Oct. 29, 2026
  * @version Nov.  2, 2026
  * @author  ASAMI, Tomoharu
  */
object NativeWorkflowCallerExecution {
  /** Validate an actual issued binding without selecting or launching an executor. */
  def validateIssuedC(issued: V.WorkflowIssuedWorkContext): Consequence[Unit] = for {
    _ <- _reference_c(issued)
    requirement <- _json_c(issued.executionRequirement)
    _ <- H.decodeIssuedRequirementC(requirement)
    _ <- if (_exact(issued.requiredOperation)) Consequence.unit
      else Consequence.stateConflict("Caller information requires the original completion operation")
  } yield ()
  final class Prepared private[NativeWorkflowCallerExecution] (
    val issued: V.WorkflowIssuedWorkContext, val session: H.Session
  ) {
    def recordC(selection: H.Selection, outcome: H.Outcome): Consequence[Prepared] =
      session.recordC(selection, outcome).map(value => new Prepared(issued, value))

    /** An already-returned Phase completion plus caller-retained resources only.
      * This optional projection launches nothing and does not re-complete work.
      */
    def phaseCompletionMetrics(completion: V.GoalPhaseCompletionResult,
      retained: Vector[NativeCallerPhaseMetrics.RetainedExecution]): V.GoalPhaseCompletionResult =
      NativeCallerPhaseMetrics.project(session.workReference.handle.instanceIdentity.value, completion, retained)

    def completionEvidenceC(references: Vector[V.WorkflowArtifactReference]): Consequence[V.WorkflowExecutionEvidence] =
      if (references == null || references.isEmpty || references.exists(x => x == null || !_exact(x.identity) || !_exact(x.revision)))
        Consequence.stateConflict("Caller completion requires genuine artifact references")
      else session.completionObservationC.map { observation =>
        val executor = session.attempts.last.outcome.executor.get
        V.WorkflowExecutionEvidence(references, Some(executor.contextIdentity), Some(executor.profile.model),
          Some(V.WorkflowExecutionObservation(observation.placement.value, observation.originContextIdentity,
            observation.executorContextIdentity, observation.profileIdentity)))
      }
  }

  def prepareC(issued: V.WorkflowIssuedWorkContext, mapping: WorkflowExecutionProfileMapping,
    origin: WorkflowExecutorDescriptor): Consequence[Prepared] = for {
    reference <- _reference_c(issued)
    requirement <- _json_c(issued.executionRequirement)
    capture <- _capture_c(issued)
    retained <- capture.filter(_.configured.nonEmpty).map(GoalPhaseOperatingProfiles.retainedMappingC)
      .getOrElse(Consequence.success(mapping))
    session <- H.prepareIssuedJsonC(reference, requirement, origin, retained)
    _ <- capture.filter(_.selected.nonEmpty).fold(Consequence.unit)(GoalPhaseOperatingProfiles.admitCallerSessionC(_, session))
  } yield new Prepared(issued, session)

  /** Restore using the original issued DTO and the retained caller artifact, never
    * a later mapping. Only consumed reference/requirement contradictions reject.
    */
  def restoreC(issued: V.WorkflowIssuedWorkContext, session: H.Session): Consequence[Prepared] =
    if (session == null) Consequence.stateConflict("Original caller execution session is unavailable")
    else for {
      reference <- _reference_c(issued)
      _ <- if (reference == session.workReference) Consequence.unit
        else Consequence.stateConflict("Restored caller execution session belongs to a different issued work reference")
      requirement <- _json_c(issued.executionRequirement)
      checked <- H.decodeIssuedRequirementC(requirement)
      capture <- _capture_c(issued)
      _ <- capture.filter(_.selected.nonEmpty).fold(Consequence.unit)(GoalPhaseOperatingProfiles.admitCallerSessionC(_, session))
      _ <- if (checked == session.requirement) Consequence.unit
        else Consequence.stateConflict("Restored caller execution session contradicts its issued execution requirement")
    } yield new Prepared(issued, session)

  private def _capture_c(issued: V.WorkflowIssuedWorkContext): Consequence[Option[GoalPhaseOperatingProfiles.Capture]] =
    if (issued == null || issued.context == null) Consequence.stateConflict("Caller requires its issued context")
    else _json_c(issued.context).flatMap { context =>
      val left = context.hcursor.downField("operatingProfile").focus
      val right = context.hcursor.downField("operating_profile").focus
      if (left.nonEmpty && right.nonEmpty && left != right)
        Consequence.stateConflict("Caller operating profile has contradictory aliases")
      else left.orElse(right) match {
        case None => Consequence.success(None)
        case Some(value) =>
          val normalized = Vector("reasoningClass" -> "reasoning_class", "mappingIdentity" -> "mapping_identity",
            "mappingRevision" -> "mapping_revision").foldLeft[Either[String, Json]](Right(value)) {
            case (prior, (name, wire)) => prior.flatMap { json =>
              val a = json.hcursor.downField(name).focus
              val b = json.hcursor.downField(wire).focus
              if (a.nonEmpty && b.nonEmpty && a != b) Left("Caller profile has contradictory " + name)
              else Right(json.mapObject(fields => b.fold(fields)(v => fields.remove(wire).add(name, v))))
            }
          }
          normalized.flatMap(_.as[GoalPhaseOperatingProfiles.Capture].left.map(_.message)).fold(
            Consequence.stateConflict(_), capture =>
              if (GoalPhaseOperatingProfiles.captureValid(capture)) Consequence.success(Some(capture))
              else Consequence.stateConflict("Caller operating profile capture is invalid"))
      }
    }

  private def _reference_c(issued: V.WorkflowIssuedWorkContext): Consequence[H.WorkReference] =
    if (issued == null || issued.schemaVersion != WorkflowProtocolV1.schemaVersion ||
        !Set("analysis", "design", "implementation", "review")(issued.thinkingMode) ||
        !_exact(issued.runId) || !_exact(issued.continuationId) || !_exact(issued.expectedRevision))
      Consequence.stateConflict("Caller execution requires an actual Request with current protocol, finite mode and native reference")
    else for {
      handle <- _json_c(issued.handle)
      component <- _field_c(handle, "componentIdentity", "component_identity")
      workflow <- _field_c(handle, "workflowIdentity", "workflow_identity")
      revision <- _field_c(handle, "workflowRevision", "workflow_revision")
      instance <- _field_c(handle, "instanceIdentity", "instance_identity")
      protocol <- _field_c(handle, "protocolVersion", "protocol_version")
      reference <- H.WorkReference(WorkflowHandle(ComponentId(component), WorkflowDefinitionIdentity(workflow),
        WorkflowDefinitionRevision(revision), InstanceIdentity(instance), protocol), StateMachineRunIdentity(issued.runId),
        ContinuationIdentity(issued.continuationId), StateMachineRevision(issued.expectedRevision)).validateC
    } yield reference

  private def _field_c(value: Json, declaration: String, wire: String): Consequence[String] = {
    val left = value.hcursor.downField(declaration).focus
    val right = value.hcursor.downField(wire).focus
    if (left.nonEmpty && right.nonEmpty && left != right)
      Consequence.stateConflict("Caller work reference has contradictory names for " + declaration)
    else left.orElse(right).flatMap(_.asString).filter(_exact).map(Consequence.success).getOrElse(
      Consequence.stateConflict("Caller work reference is missing " + declaration))
  }
  private def _json_c(value: Record): Consequence[Json] =
    if (value == null) Consequence.stateConflict("Caller execution requires its actual structured issued field")
    else value.toJsonStringC.flatMap(text => io.circe.parser.parse(text).fold(error =>
      Consequence.stateConflict(error.message), Consequence.success))
  private def _exact(value: String): Boolean =
    value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
}
