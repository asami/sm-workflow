package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json

/** Address one admitted host business entry. This is not a candidate index,
  * a content hash, an execution ID or a proof of unchanged input.
  * @since   Oct. 12, 2026
  * @version Oct. 12, 2026
  */
private[smworkflow] object NativeWorkflowStartBusinessKey {
  def apply(input: NativeWorkflowStartInput.BusinessInput, participant: String): String = {
    val target = input match {
      case NativeWorkflowStartInput.Goal(value) =>
        Vector(Json.fromString(value.phaseIdentity), Json.fromString(value.repositoryIdentity))
      case NativeWorkflowStartInput.Split(phase, mode, proposal) =>
        Vector(Json.fromString(phase), Json.fromString(mode), proposal.fold(Json.Null)(Json.fromString))
      case value: NativeWorkflowStartInput.Repository => Vector(value.repositoryIdentity, value.localBranch,
        value.trackingRemote, value.trackingBranch, value.remoteEndpointIdentity, value.syncMode).map(Json.fromString)
    }
    "business-start:" + Json.fromValues(Vector(Json.fromString(input.kind.operation.service),
      Json.fromString(input.kind.operation.operation), Json.fromString(participant)) ++ target).noSpaces
  }
}
