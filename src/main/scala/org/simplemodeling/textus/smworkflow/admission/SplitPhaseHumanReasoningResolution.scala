package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, parser}
import java.nio.charset.StandardCharsets.UTF_8
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.{ContextReference, WorkflowProtocolV1}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseReasoningHumanDirection as H,
  SplitPhaseHumanDirectionAnswerContract as A, SplitPhaseExplicitReasoningSemantic as S,
  SplitPhaseExplicitReasoningSemanticJson as J, SplitPhaseExplicitPartitionSemantic as T,
  SplitPhaseExplicitPlanningAdvance as W, SplitPhaseExplicitObjectiveSelection as O,
  SplitPhasePlanningSnapshot as R}

/** Passive Human interpretation, separately joined to the canonical Decision.
  * Reuses the semantic calculation kernel, never synthesizes an AI completion.
  * @since Oct. 7, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseHumanReasoningResolution {
  final case class Record(reference: T.ContextReference, decision: String,
    reasoningResult: ContextReference, interpretation: S.Judgment)
  val schema = "sm-workflow.split-human-reasoning-resolution.v1"
  val storageField = "splitPhaseHumanReasoningResolutionPayload"

  def encodeC(record: Record): Consequence[String] = for {
    _ <- if (record != null && record.reference != null && record.reasoningResult != null &&
        Vector(record.reference.identity, record.reference.revision, record.decision,
          record.reasoningResult.identity, record.reasoningResult.revision).forall(_canonical) &&
        record.interpretation != null && record.interpretation.decision == S.Decision.Enriched)
      Consequence.unit else _invalid("complete Human interpretation references differ")
    judgment <- J.resultCodec.encodeC(record.interpretation)
    json = Json.obj("schemaVersion" -> Json.fromString(schema), "reference" -> _reference(record.reference.identity, record.reference.revision),
      "decision" -> Json.fromString(record.decision), "reasoningResult" -> _reference(record.reasoningResult.identity, record.reasoningResult.revision),
      "interpretation" -> judgment)
    wire = schema + ":" + json.noSpaces
    _ <- if (wire.getBytes(UTF_8).length <= 1024 * 1024) Consequence.unit else _invalid("interpretation exceeds its UTF-8 bound")
  } yield wire

  def decodeC(wire: String): Consequence[Record] = for {
    _ <- if (wire != null && wire.startsWith(schema + ":") && wire.getBytes(UTF_8).length <= 1024 * 1024)
      Consequence.unit else _invalid("interpretation frame differs")
    json <- parser.parse(wire.substring(schema.length + 1)).fold(_ => _invalid("interpretation JSON differs"), Consequence.success)
    fields <- json.asObject.filter(_.keys.toSet == Set("schemaVersion", "reference", "decision", "reasoningResult", "interpretation"))
      .map(Consequence.success).getOrElse(_invalid("interpretation fields differ"))
    _ <- if (fields("schemaVersion").flatMap(_.asString).contains(schema)) Consequence.unit else _invalid("interpretation schema differs")
    reference <- _decode_reference_c(fields("reference").get)
    result <- _decode_reference_c(fields("reasoningResult").get)
    decision <- fields("decision").flatMap(_.asString).filter(_canonical).map(Consequence.success).getOrElse(_invalid("Decision identity differs"))
    judgment <- J.resultCodec.decode(fields("interpretation").get).fold(_ => _invalid("interpretation data differs"), Consequence.success)
    record = Record(T.ContextReference(reference.identity, reference.revision), decision, result, judgment)
    canonical <- encodeC(record)
    _ <- if (parser.parse(canonical.substring(schema.length + 1)).contains(json)) Consequence.unit else _invalid("nested interpretation fields differ")
  } yield record

  def loadForCallC(reference: T.ContextReference, declaration: SplitPhaseManagedStartAssembly.Declaration,
    core: ActionCall.Core): Consequence[Record] = for {
    // The existing policy validates separation from Start, host selection,
    // workflow/native and every planning-data collection before this read.
    _ <- SplitPhaseHumanResolutionReadPolicy.bindC(core.component.get.subsystem.get.configuration, declaration)
    purpose <- SplitPhaseHumanResolutionReadPolicy.decodeC(core.component.get.subsystem.get.configuration)
    row <- ManagedHostRecordRead.loadSplitHumanResolutionC(core, purpose.storeName, purpose.collection, reference.identity)
      .flatMap(_.map(Consequence.success).getOrElse(_invalid("selected separate interpretation is absent")))
    wire <- row.getString(storageField).map(Consequence.success).getOrElse(_invalid("selected reasoning interpretation payload is absent"))
    record <- decodeC(wire)
    _ <- if (record.reference == reference && row.getString("id").contains(reference.identity) &&
        row.fields.map(_.key).toSet == Set("id", storageField)) Consequence.unit else _invalid("selected physical identity or revision differs")
  } yield record

  def calculateC(plan: H.Plan, submitted: ContinuationResult[A.Answer], record: Record): Consequence[R.Record] = for {
    _ <- encodeC(record)
    _ <- WorkflowProtocolV1.admitDecisionResultC(plan.question, submitted).map(_ => ())
    selected <- submitted.result.value match {
      case A.Answer.ProvideResolution(reference) => Consequence.success(reference)
      case _ => _invalid("canonical Human answer did not select interpretation")
    }
    prepared = plan.source.plan.pending.issue.prepared
    _ <- if (record.reference == selected && submitted.resultReference != ContextReference(selected.identity, selected.revision) &&
        record.decision == submitted.continuationId.value && record.reasoningResult == plan.source.plan.submitted.resultReference &&
        record.interpretation.context == prepared.request.context && record.interpretation.evidence.forall(e =>
          submitted.evidence.references.contains(ContextReference(e.identity, e.revision)))) Consequence.unit
      else _invalid("Human interpretation crosses its selected Decision, saved AI result or answer evidence")
    references = S.ResolutionReferences(
      Some(W.DataReference(submitted.continuationId.value + ":human-planning", submitted.expectedRevision.value)),
      Some(O.DemandReference(submitted.continuationId.value + ":human-demands", submitted.expectedRevision.value)))
    outcome <- S.admitC(prepared, prepared.pending.selection, prepared.request.result, references, record.interpretation)
    admitted <- outcome match {
      case value: S.Admitted => Consequence.success(value)
      case _ => _invalid("Human interpretation did not provide exact ordered unknown-item coverage")
    }
    continued <- W.continueReasoningC(admitted)
    projected <- R.projectC(continued.outcome)
    _ <- R.dispositionC(projected.record).flatMap {
      case R.Disposition.Planned => Consequence.unit
      case _ => _invalid("complete Human interpretation did not produce a selected planning successor")
    }
  } yield projected.record

  private def _reference(identity: String, revision: String): Json =
    Json.obj("identity" -> Json.fromString(identity), "revision" -> Json.fromString(revision))
  private def _decode_reference_c(json: Json): Consequence[ContextReference] =
    json.asObject.filter(_.keys.toSet == Set("identity", "revision")) match {
      case Some(fields) => (fields("identity").flatMap(_.asString).filter(_canonical), fields("revision").flatMap(_.asString).filter(_canonical)) match {
        case (Some(identity), Some(revision)) => Consequence.success(ContextReference(identity, revision))
        case _ => _invalid("interpretation reference differs")
      }
      case _ => _invalid("interpretation reference fields differ")
    }
  private def _canonical(value: String): Boolean = value != null && value.nonEmpty && value.trim == value &&
    value.length <= 1024 && !value.exists(_.isControl) && UTF_8.newEncoder().canEncode(value)
  private def _invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("SplitPhase Human reasoning resolution " + reason)
}
