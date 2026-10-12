package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workflow.WorkflowResultJsonV1
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.value.{GoalPhaseStartInput, SplitPhaseStartInput, RepositorySyncStartInput}

/** Construct public business inputs directly, without legacy wire projection.
  * @since   Oct. 11, 2026
  * @version Oct. 11, 2026
  */
private[smworkflow] object NativeWorkflowOperationInputFixture {
  def selectedGoalInput(input: GoalPhaseStartInput): TypedValue[Json] = TypedValue("GoalPhaseStartInput", Json.obj(
    "phaseIdentity" -> Json.fromString(input.phaseIdentity), "repositoryIdentity" -> Json.fromString(input.repositoryIdentity)))

  def goal(input: GoalPhaseStartInput, selector: Option[String] = None): String =
    start(NativeWorkflowStartInput.Goal(input), selector)

  def split(input: SplitPhaseStartInput, selector: Option[String] = None): String =
    start(NativeWorkflowStartInput.Split(input.sourcePhaseIdentity, input.invocationMode,
      input.proposalReference), selector)

  def repository(input: RepositorySyncStartInput, selector: Option[String] = None): String =
    start(NativeWorkflowStartInput.Repository(input.repositoryIdentity, input.localBranch,
      input.trackingRemote, input.trackingBranch, input.remoteEndpointIdentity, input.syncMode), selector)

  def start(input: NativeWorkflowStartInput.BusinessInput, selector: Option[String] = None): String = {
    val business = input match {
      case NativeWorkflowStartInput.Goal(value) => selectedGoalInput(value).value
      case NativeWorkflowStartInput.Split(phase, mode, proposal) => Json.obj((Vector(
        "sourcePhaseIdentity" -> Json.fromString(phase), "invocationMode" -> Json.fromString(mode)) ++
        proposal.toVector.map(value => "proposalReference" -> Json.fromString(value)))*)
      case value: NativeWorkflowStartInput.Repository => Json.obj(
        "repositoryIdentity" -> Json.fromString(value.repositoryIdentity), "localBranch" -> Json.fromString(value.localBranch),
        "trackingRemote" -> Json.fromString(value.trackingRemote), "trackingBranch" -> Json.fromString(value.trackingBranch),
        "remoteEndpointIdentity" -> Json.fromString(value.remoteEndpointIdentity), "syncMode" -> Json.fromString(value.syncMode))
    }
    Json.obj((Vector("input" -> business) ++ selector.toVector.map(value =>
      "selectionIdentity" -> Json.fromString(value))) *).noSpaces
  }

  def advance(instance: Option[String] = None, context: Option[String] = None): String =
    Json.obj((instance.toVector.map(value => "instanceIdentity" -> Json.fromString(value)) ++
      context.toVector.map(value => "contextIdentity" -> Json.fromString(value))) *).noSpaces

  def encodeC[A](result: ContinuationResult[A], codec: WorkflowResultJsonV1.PayloadCodec[A],
    omitReference: Boolean = false, includeRequestSelector: Boolean = false): Consequence[String] = {
    val resultfields = Vector("value" -> codec.encode(result.result.value)) ++
      (if (omitReference) Vector.empty else Vector("reference" -> reference(result.resultReference)))
    val evidence = Vector("references" -> Json.fromValues(result.evidence.references.map(reference))) ++
      result.evidence.workerIdentity.toVector.map(value => "workerIdentity" -> Json.fromString(value)) ++
      result.evidence.execution.toVector.map(value => "execution" -> Json.obj(
        "placement" -> Json.fromString(value.placement.value), "originContextIdentity" -> Json.fromString(value.originContextIdentity),
        "executorContextIdentity" -> Json.fromString(value.executorContextIdentity), "profileIdentity" -> Json.fromString(value.profileIdentity)))
    Consequence.success(Json.obj((Vector(
      "result" -> Json.obj(resultfields*), "completionFacts" -> Json.fromValues(result.completionFacts.map(reference)),
      "executionEvidence" -> Json.obj(evidence*)) ++ (if (includeRequestSelector)
        Vector("requestIdentity" -> Json.fromString(result.continuationId.value)) else Vector.empty)) *).noSpaces)
  }

  private def reference(value: ContextReference): Json = Json.obj(
    "identity" -> Json.fromString(value.identity), "revision" -> Json.fromString(value.revision))
}
