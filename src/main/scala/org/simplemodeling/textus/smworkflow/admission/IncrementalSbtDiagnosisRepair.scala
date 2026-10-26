package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.value as V

/** One semantic Fix Manifest inside the existing assigned repair. Admission
  * checks scope and retained evidence, not causal truth or test success. Actual
  * candidate inspection, checks, review and common repair control keep ownership.
  * @since   Oct. 25, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object IncrementalSbtDiagnosisRepair {
  val schema = "sm-workflow.diagnostic-repair.v1"
  final case class Locator(check: String, stream: String, firstLine: Int, lastLine: Int, rationale: String)
  final case class Manifest(schemaVersion: String, identity: String, sourceRevision: String,
    candidate: GoalPhaseOrderedProgression.Snapshot, causeHypothesis: String,
    evidence: Vector[Locator], information: Vector[ContextReference], ownedPaths: Vector[String],
    directions: Vector[String], dependencies: Vector[String], checks: Vector[String], risks: Vector[String])
  final case class Prior(step: Option[String], generation: Long, reference: ContextReference, manifest: Manifest)
  private given Encoder[ContextReference] = deriveEncoder
  private given Decoder[ContextReference] = deriveDecoder
  given Encoder[Locator] = deriveEncoder
  given Decoder[Locator] = _strict(deriveDecoder[Locator])
  given Encoder[Manifest] = deriveEncoder
  given Decoder[Manifest] = _strict(deriveDecoder[Manifest])
  given Encoder[Prior] = deriveEncoder
  given Decoder[Prior] = _strict(deriveDecoder[Prior])
  private def _strict[A: Encoder](decoder: Decoder[A]): Decoder[A] = Decoder.instance(c =>
    decoder(c).flatMap(v => if (v.asJson == c.value) Right(v)
      else Left(DecodingFailure("Diagnostic repair requires its exact typed shape", c.history))))
  private def _text(v: String): Boolean = v != null && v.trim.nonEmpty && !v.contains('\u0000') &&
    java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(v)
  private def _texts(v: Vector[String], nonempty: Boolean = false): Boolean =
    v != null && (!nonempty || v.nonEmpty) && v.distinct == v && v.forall(_text)
  private def _reference(v: ContextReference): Boolean = v != null && _text(v.identity) && _text(v.revision)
  def valid(v: Manifest): Boolean = try {
    v != null && v.schemaVersion == schema && _text(v.identity) && _text(v.sourceRevision) &&
      v.candidate != null && _text(v.candidate.head) && _text(v.candidate.tree) && _text(v.causeHypothesis) &&
      v.evidence != null && v.evidence.nonEmpty && v.evidence.forall(e => e != null && _text(e.check) &&
        Set("stdout", "stderr")(e.stream) && e.firstLine > 0 && e.lastLine >= e.firstLine && _text(e.rationale)) &&
      v.information != null && v.information.distinct == v.information && v.information.forall(_reference) &&
      _texts(v.ownedPaths, true) && v.ownedPaths.forall(GoalPhasePurposeRepair.path) &&
      _texts(v.directions, true) && _texts(v.dependencies) && _texts(v.checks, true) && _texts(v.risks)
  } catch { case scala.util.control.NonFatal(_) => false }
  def validateOptionC(v: Option[Manifest]): Consequence[Unit] =
    if (v != null && v.forall(valid)) Consequence.unit else Consequence.stateConflict("Diagnostic Fix Manifest is invalid")
  def historyValid(v: Option[Vector[Prior]]): Boolean = v != null && v.forall(history => history != null && history.nonEmpty &&
    history.forall(p => p != null && p.step != null && p.step.forall(_text) && p.generation > 0 && _reference(p.reference) && valid(p.manifest)))
  def validatePublicResultC(value: Record): Consequence[Unit] =
    if (value == null) Consequence.stateConflict("Diagnostic repair result is null")
    else value.toJsonStringC.flatMap(text => io.circe.parser.parse(text).left.map(_.message)
      .fold(Consequence.stateConflict(_), validatePublicResultJsonC))
  def validatePublicResultJsonC(value: Json): Consequence[Unit] =
    GoalPhaseNativeMetadata.normalizePublicJson(value).fold(Consequence.stateConflict(_), json =>
      json.hcursor.downField("diagnosticRepair").focus match {
        case None => Consequence.unit
        case Some(manifest) => manifest.as[Manifest].left.map(_.message)
          .fold(Consequence.stateConflict(_), m => validateOptionC(Some(m)))
      })
  def fromPublicC(v: Option[Record]): Consequence[Option[Manifest]] = if (v == null)
    Consequence.stateConflict("Diagnostic Fix Manifest option is null")
  else v match {
    case None => Consequence.success(None)
    case Some(record) => for {
      text <- record.toJsonStringC
      json <- io.circe.parser.parse(text).left.map(_.message).flatMap(GoalPhaseNativeMetadata.normalizePublicJson)
        .fold(Consequence.stateConflict(_), Consequence.success)
      manifest <- json.as[Manifest].left.map(_.message).fold(Consequence.stateConflict(_), Consequence.success)
      _ <- validateOptionC(Some(manifest))
    } yield Some(manifest)
  }

  def admitC(work: GoalPhaseNativeWork.Request, result: GoalPhaseNativeWork.Result): Consequence[Unit] = for {
    _ <- validateOptionC(result.diagnosticRepair)
    _ <- result.diagnosticRepair match {
      case None => Consequence.unit // Existing accepted results are never upgraded or replayed.
      case Some(manifest) =>
        val eligible = work.kind == "RepairStep" && result.outcome == "completed" && work.checkFailure.nonEmpty
        val validcase = work.checkFailure.exists { failure =>
          val validation = failure.validation
          val stepchecks = work.stepIdentity.flatMap(id => work.selectedPlan.steps.find(_.identity == id)).map(_.checks)
            .getOrElse(work.selectedPlan.phaseChecks)
          val owners = work.stepIdentity.flatMap(id => work.selectedPlan.steps.find(_.identity == id)).map(_.ownedPaths)
            .getOrElse(work.selectedPlan.steps.flatMap(_.ownedPaths))
          val kinds = validation.selected.map(c => c.identity -> c.kind).toMap
          manifest.sourceRevision == validation.sourceRevision && manifest.candidate == validation.candidate &&
            manifest.ownedPaths.forall(path => owners.exists(p => path == p || path.startsWith(p + "/"))) &&
            manifest.checks.forall(stepchecks.contains) && manifest.checks.exists(id => kinds.get(id).exists(k =>
              k == WorkflowSbtValidation.Kind.Test || k == WorkflowSbtValidation.Kind.TestOnly)) &&
            validation.selected.filter(_.kind == WorkflowSbtValidation.Kind.Compile).forall(c => manifest.checks.contains(c.identity)) &&
            manifest.evidence.forall { locator =>
              validation.receipts.find(_.identity == locator.check).exists { receipt =>
                val failed = validation.selected.find(_.identity == receipt.identity).exists(c =>
                  !RepositorySyncNativeMergeValidation.productSuccessful(receipt, c.kind))
                val capture = receipt.processObservation.map(p => if (locator.stream == "stdout") p.stdout else p.stderr)
                val text = if (locator.stream == "stdout") receipt.stdout else receipt.stderr
                failed && capture.forall(c => WorkflowSbtCapture.text(c).textStatus != "unavailable") &&
                  text.nonEmpty && locator.lastLine <= text.split("\\r?\\n", -1).length
              }
            }
        }
        if (eligible && validcase) Consequence.unit else Consequence.stateConflict(
          "Diagnostic Fix Manifest differs from assigned failure, ownership, available evidence or selected behavioral verification")
    }
  } yield ()

  /** References are existing confirmed delivery observations for this exact
    * outstanding work. They do not become proof of the provider's hypothesis.
    */
  def admitInformationC(d: GoalPhaseNativeRun.Declaration, core: ActionCall.Core,
    actual: V.WorkflowIssuedWorkContext, work: GoalPhaseNativeWork.Request,
    manifest: Option[Manifest]): Consequence[Unit] = manifest.toVector.flatMap(_.information)
    .foldLeft(Consequence.unit) { (prior, reference) => prior.flatMap { _ =>
      d.evidencePolicy.map { policy =>
        CommonEvidence.loadedC(CommonEvidence.storage(core, policy), reference.identity).flatMap {
          case Some((a, Some(o))) if reference.revision == "1" && o.status == "stored" && a.host == policy.hostIdentity &&
              a.source.repository == d.input.repositoryIdentity && a.source.phase == d.input.phaseIdentity &&
              a.source.session == work.instanceIdentity && a.source.step == work.stepIdentity &&
              a.source.reference == ContextReference(actual.continuationId, actual.expectedRevision) &&
              a.source.output.startsWith("diagnostic-information:") => Consequence.unit
          case _ => Consequence.stateConflict("Diagnostic repair information is not a confirmed delivery for this work")
        }
      }.getOrElse(Consequence.stateConflict("Diagnostic repair information has no enabled Common Evidence owner"))
    }}

  def append(history: Vector[Prior], work: GoalPhaseNativeWork.Request,
    result: GoalPhaseNativeWork.Result, reference: ContextReference): Vector[Prior] =
    history ++ result.diagnosticRepair.map(m => Prior(work.stepIdentity, work.generation, reference, m))
  def selected(history: Vector[Prior], step: Option[String]): Option[Vector[Prior]] =
    Option.when(history.exists(_.step == step))(history.filter(_.step == step))
}
