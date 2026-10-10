package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowResultJsonV1
import org.goldenport.cncf.workflow.WorkflowProtocolV1.ContinuationResult

/** Projects existing specification-owned replies into the public submission.
  * No caller handle, revision, run, snapshot or result-type echo is transmitted.
  * Actual meaning and executor evidence are preserved unchanged. Specs may omit
  * only a result destination already assigned in the stored WorkOrder.
  * @since   Oct.  9, 2026
  * @version Oct. 10, 2026
  */
private[smworkflow] object NativeWorkflowResultSubmissionFixture {
  def encodeC[R](result: ContinuationResult[R], codec: WorkflowResultJsonV1.PayloadCodec[R],
    omitReference: Boolean = false): Consequence[String] =
    WorkflowResultJsonV1.encodeC(result, codec).flatMap(wire => io.circe.parser.parse(wire)
      .fold(_ => Consequence.stateConflict("invalid specification-owned reply JSON"), json => {
        val cursor = json.hcursor
        Consequence.success(Json.obj(
          "schemaVersion" -> Json.fromString(NativeWorkflowResultInput.schema),
          "requestIdentity" -> Json.fromString(result.continuationId.value),
          "result" -> cursor.downField("result").focus.get.mapObject { fields =>
            val selected = fields.remove("typeIdentity")
            if (omitReference) selected.remove("reference") else selected
          },
          "completionFacts" -> cursor.downField("completionFacts").focus.get,
          "executionEvidence" -> cursor.downField("executionEvidence").focus.get
        ).noSpaces)
      }))
}
