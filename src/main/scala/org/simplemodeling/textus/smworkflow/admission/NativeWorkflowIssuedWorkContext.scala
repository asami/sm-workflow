package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.{WorkflowResultJsonV1, WorkflowWorkOrderJsonV1}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.{WorkflowInteraction, WorkflowContinuation}
import org.goldenport.record.Record
import org.goldenport.record.io.RecordDecoder
import org.simplemodeling.textus.smworkflow.value as V

/** Selected public context of the actual owning WorkOrder. Canonical CNCF
  * encoding owns the generic contracts; this view grants no execution authority.
  * @since   Oct. 14, 2026
  * @version Oct. 24, 2026
  */
private[smworkflow] object NativeWorkflowIssuedWorkContext {
  enum Mode(val value: String) {
    case Analysis extends Mode("analysis")
    case Design extends Mode("design")
    case Implementation extends Mode("implementation")
    case Review extends Mode("review")
  }

  def goalModeC(kind: String): Consequence[Mode] = kind match {
    case "TriageStep" => Consequence.success(Mode.Analysis)
    case "ImplementStep" | "RepairStep" => Consequence.success(Mode.Implementation)
    case "ReviewStep" | "ReviewPhase" => Consequence.success(Mode.Review)
    case _ => Consequence.stateConflict("Issued Goal work has no supported semantic mode")
  }

  def fromIssuedC[W](work: WorkflowInteraction[W, Nothing], mode: Mode,
    inputCodec: WorkflowResultJsonV1.PayloadCodec[W]): Consequence[V.WorkflowIssuedWorkContext] =
    try {
      if (work == null || mode == null || inputCodec == null || work.current == null)
        Consequence.stateConflict("Issued work context requires an interaction, mode and owning codec")
      else work.current match {
        case order: WorkflowContinuation.WorkOrder[?] =>
          if (order.request == null || order.request.input == null ||
              order.request.input.exists(x => x == null || x.typeIdentity != inputCodec.typeIdentity))
            Consequence.stateConflict("Issued work context has an absent or incompatible request input")
          else for {
            encoded <- WorkflowWorkOrderJsonV1.encodeC(work, inputCodec)
            root <- parse(encoded).fold(_ => Consequence.stateConflict("Issued WorkOrder encoding is not JSON"), Consequence.success)
            request <- _object_c(root, "request")
            version <- _string_c(root, "schemaVersion")
            handle <- _record_c(root, "handle")
            run <- _string_c(request, "runId")
            continuation <- _string_c(request, "continuationId")
            revision <- _string_c(request, "expectedRevision")
            required <- _string_c(request, "requiredOperation")
            operation <- _record_c(request, "operation")
            input <- _input_type_c(request)
            result <- _optional_string_c(request, "resultType")
            context <- _record_c(request, "context")
            completion <- _record_c(request, "completion")
            evidence <- _record_c(request, "evidence")
            completionOperation <- _record_c(request, "completionOperation")
            requirement <- _record_c(root, "requirement")
          } yield V.WorkflowIssuedWorkContext(mode.value, version, handle, run, continuation, revision,
            required, operation, input, result, context, completion, evidence, completionOperation, requirement)
        case _ => Consequence.stateConflict("Issued work context requires WORK_ORDER")
      }
    } catch {
      case NonFatal(_) => Consequence.stateConflict("Issued work context cannot consume the owning WorkOrder encoding")
    }

  private def _object_c(parent: Json, name: String): Consequence[Json] =
    parent.hcursor.downField(name).focus.filter(_.isObject).map(Consequence.success).getOrElse(
      Consequence.stateConflict("Issued work context requires object " + name))

  private def _record_c(parent: Json, name: String): Consequence[Record] =
    _object_c(parent, name).flatMap(value => new RecordDecoder().json(value.noSpaces))

  private def _string_c(parent: Json, name: String): Consequence[String] =
    parent.hcursor.downField(name).focus.flatMap(_.asString).filter(_.trim.nonEmpty)
      .map(Consequence.success).getOrElse(Consequence.stateConflict("Issued work context requires string " + name))

  private def _optional_string_c(parent: Json, name: String): Consequence[Option[String]] =
    parent.hcursor.downField(name).focus match {
      case Some(value) if value.isNull => Consequence.success(None)
      case Some(_) => _string_c(parent, name).map(Some(_))
      case None => Consequence.stateConflict("Issued work context is missing optional field " + name)
    }

  private def _input_type_c(request: Json): Consequence[Option[String]] =
    request.hcursor.downField("input").focus match {
      case Some(value) if value.isNull => Consequence.success(None)
      case Some(_) => _object_c(request, "input").flatMap(value => _string_c(value, "typeIdentity")).map(Some(_))
      case None => Consequence.stateConflict("Issued work context is missing input type")
    }
}
