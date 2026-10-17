package org.simplemodeling.textus.smworkflow.host

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.value as V
import WorkflowExecutionHarness as H

/** Host library bridge over the actual public Request context. Operational
  * completion supplies observations only; normal Submit owns semantic admission.
  * @since   Oct. 16, 2026
  * @version Oct. 16, 2026
  * @author  ASAMI, Tomoharu
  */
object NativeWorkflowHostExecution {
  final class Prepared private[NativeWorkflowHostExecution] (
    val issued: V.WorkflowIssuedWorkContext, val session: H.Session
  ) {
    def recordC(selection: H.Selection, outcome: H.Outcome): Consequence[Prepared] =
      session.recordC(selection, outcome).map(value => new Prepared(issued, value))

    def completionEvidenceC(references: Vector[V.WorkflowArtifactReference]): Consequence[V.WorkflowExecutionEvidence] =
      if (references == null || references.isEmpty || references.exists(x => x == null || !_exact(x.identity) || !_exact(x.revision)))
        Consequence.stateConflict("Host completion requires genuine artifact references")
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
    session <- H.prepareIssuedJsonC(reference, requirement, origin, mapping)
  } yield new Prepared(issued, session)

  /** Restore using the original issued DTO and the retained host artifact, never
    * a later mapping. Only consumed reference/requirement contradictions reject.
    */
  def restoreC(issued: V.WorkflowIssuedWorkContext, session: H.Session): Consequence[Prepared] =
    if (session == null) Consequence.stateConflict("Original host execution session is unavailable")
    else for {
      reference <- _reference_c(issued)
      _ <- if (reference == session.workReference) Consequence.unit
        else Consequence.stateConflict("Restored host session belongs to a different issued work reference")
      requirement <- _json_c(issued.executionRequirement)
      checked <- H.decodeIssuedRequirementC(requirement)
      _ <- if (checked == session.requirement) Consequence.unit
        else Consequence.stateConflict("Restored host session contradicts its issued execution requirement")
    } yield new Prepared(issued, session)

  private def _reference_c(issued: V.WorkflowIssuedWorkContext): Consequence[H.WorkReference] =
    if (issued == null || issued.schemaVersion != WorkflowProtocolV1.schemaVersion ||
        !Set("analysis", "design", "implementation", "review")(issued.thinkingMode) ||
        !_exact(issued.runId) || !_exact(issued.continuationId) || !_exact(issued.expectedRevision))
      Consequence.stateConflict("Host execution requires an actual Request with current protocol, finite mode and native reference")
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
      Consequence.stateConflict("Host work reference has contradictory names for " + declaration)
    else left.orElse(right).flatMap(_.asString).filter(_exact).map(Consequence.success).getOrElse(
      Consequence.stateConflict("Host work reference is missing " + declaration))
  }
  private def _json_c(value: Record): Consequence[Json] =
    if (value == null) Consequence.stateConflict("Host execution requires its actual structured issued field")
    else value.toJsonStringC.flatMap(text => io.circe.parser.parse(text).fold(error =>
      Consequence.stateConflict(error.message), Consequence.success))
  private def _exact(value: String): Boolean =
    value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
}
