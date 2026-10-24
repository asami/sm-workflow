package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseNativeWork as W
import GoalPhaseOrderedProgression as O

/** Explicit reviewer-owned original Step purpose judgments. These types carry
  * no execution permission and never reinterpret a saved untyped verdict.
  * @since   Oct. 24, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhasePurposeRepair {
  val identity = "sm-workflow.step-purpose-repair.v1"
  final case class Contract(identity: String, stepIdentity: String, purpose: String, ownedPaths: Vector[String])
  object Contract {
    given Encoder[Contract] = Encoder.instance(value => Json.obj("identity" -> value.identity.asJson,
      "stepIdentity" -> value.stepIdentity.asJson, "purpose" -> value.purpose.asJson, "ownedPaths" -> value.ownedPaths.asJson))
    given Decoder[Contract] = Decoder.instance { cursor => for {
      _ <- Either.cond(cursor.value.asObject.exists(_.keys.toSet == Set("identity", "stepIdentity", "purpose", "ownedPaths")),
        (), DecodingFailure("Purpose contract must have exactly its declared fields", cursor.history))
      id <- cursor.get[String]("identity")
      step <- cursor.get[String]("stepIdentity")
      purpose <- cursor.get[String]("purpose")
      paths <- cursor.get[Vector[String]]("ownedPaths")
      value = Contract(id, step, purpose, paths)
      _ <- Either.cond(validContract(value), (), DecodingFailure("Invalid Step purpose contract", cursor.history))
    } yield value }
  }
  sealed trait Source { def findingOrdinal: Int }
  final case class RequiredCondition(findingOrdinal: Int, conditionIdentity: String) extends Source
  final case class StepPurpose(findingOrdinal: Int, stepIdentity: String, rationale: String,
    affectedPaths: Vector[String]) extends Source
  object Source {
    given Encoder[Source] = Encoder.instance {
      case value: RequiredCondition => Json.obj("kind" -> "required-condition".asJson,
        "findingOrdinal" -> value.findingOrdinal.asJson, "conditionIdentity" -> value.conditionIdentity.asJson)
      case value: StepPurpose => Json.obj("kind" -> "step-purpose".asJson,
        "findingOrdinal" -> value.findingOrdinal.asJson, "stepIdentity" -> value.stepIdentity.asJson,
        "rationale" -> value.rationale.asJson, "affectedPaths" -> value.affectedPaths.asJson)
    }
    given Decoder[Source] = Decoder.instance { cursor => for {
      kind <- cursor.get[String]("kind")
      ordinal <- cursor.get[Int]("findingOrdinal")
      value <- kind match {
        case "required-condition" if cursor.value.asObject.exists(_.keys.toSet == Set("kind", "findingOrdinal", "conditionIdentity")) =>
          cursor.get[String]("conditionIdentity").map(RequiredCondition(ordinal, _))
        case "step-purpose" if cursor.value.asObject.exists(_.keys.toSet == Set("kind", "findingOrdinal", "stepIdentity", "rationale", "affectedPaths")) => for {
          step <- cursor.get[String]("stepIdentity")
          rationale <- cursor.get[String]("rationale")
          paths <- cursor.get[Vector[String]]("affectedPaths")
        } yield StepPurpose(ordinal, step, rationale, paths)
        case _ => Left(DecodingFailure("Unknown or contradictory repair finding source", cursor.history))
      }
      _ <- Either.cond(_valid_source(value), (), DecodingFailure("Invalid repair finding source", cursor.history))
    } yield value }
  }

  def capture(kind: String, stepIdentity: Option[String], plan: O.Plan): Option[Contract] =
    if (kind == "ReviewStep") stepIdentity.flatMap(id => plan.steps.find(_.identity == id))
      .map(step => Contract(identity, step.identity, step.purpose, step.ownedPaths)) else None

  def validContract(value: Contract): Boolean = value != null && value.identity == identity &&
    text(value.stepIdentity) && text(value.purpose) && paths(value.ownedPaths)

  def validateRequestC(request: W.Request): Consequence[Unit] = _check(request != null && request.purposeRepair != null &&
    request.purposeRepair.forall(value => validContract(value) && request.kind == "ReviewStep" &&
      request.stepIdentity != null && request.stepIdentity.contains(value.stepIdentity) && request.purpose == value.purpose &&
      request.selectedPlan != null && request.selectedPlan.steps != null &&
      request.selectedPlan.steps.find(_.identity == value.stepIdentity).exists(step =>
        step.purpose == value.purpose && step.ownedPaths == value.ownedPaths)),
    "purpose contract differs from the actual issued selected Step")

  def validateSourcesC(value: Option[Vector[Source]]): Consequence[Unit] = _check(value != null && value.forall(sources =>
    sources != null && sources.nonEmpty && sources.size <= 128 && sources.forall(_valid_source) &&
      sources.map(_.findingOrdinal) == (1 to sources.size).toVector), "repair sources must be a complete bounded ordinal sequence")

  def publicSources(value: Option[Vector[Source]]): Option[V.GoalPhaseRepairFindingSources] =
    value.map(sources => V.GoalPhaseRepairFindingSources(sources.map(source => GoalPhaseNativeMetadata.record(source.asJson))))

  def internalSourcesC(value: Option[V.GoalPhaseRepairFindingSources]): Consequence[Option[Vector[Source]]] =
    if (value == null || value.exists(wrapper => wrapper == null || wrapper.sources == null || wrapper.sources.exists(_ == null)))
      Consequence.stateConflict("Public repair sources must be absent or a valid non-null wrapper")
    else value.fold(Consequence.success(Option.empty[Vector[Source]])) { wrapper => for {
      sources <- wrapper.sources.foldLeft(Consequence.success(Vector.empty[Source])) { (prior, record) => for {
        values <- prior
        json <- _record_json_c(record)
        source <- json.as[Source].fold(error => Consequence.stateConflict(error.message), Consequence.success)
      } yield values :+ source }
      _ <- validateSourcesC(Some(sources))
    } yield Some(sources) }

  /** Raw text adapters validate before Record can discard explicit null.
    * Existing Record callers still reject unknown wrapper/source fields.
    */
  def validatePublicResultC(value: Record): Consequence[Unit] = for {
    json <- _record_json_c(value)
    _ <- validatePublicResultJsonC(json)
  } yield ()

  def validatePublicResultJsonC(value: Json): Consequence[Unit] = for {
    _ <- _check(value != null && value.isObject, "public repair result must be an object")
    normalized <- GoalPhaseNativeMetadata.normalizePublicJson(value).fold(Consequence.stateConflict(_), Consequence.success)
    _ <- normalized.hcursor.downField("repairFindingSources").focus match {
      case None => Consequence.unit
      case Some(wrapper) => for {
        _ <- _check(wrapper.asObject.exists(_.keys.toSet == Set("sources")), "public repair source wrapper must contain only sources")
        sources <- wrapper.hcursor.get[Vector[Source]]("sources").fold(error => Consequence.stateConflict(error.message), Consequence.success)
        _ <- validateSourcesC(Some(sources))
      } yield ()
    }
  } yield ()

  private def _record_json_c(value: Record): Consequence[Json] =
    if (value == null) Consequence.stateConflict("Public repair Record is null")
    else value.toJsonStringC.flatMap(text => io.circe.parser.parse(text).left.map(_.message)
      .flatMap(GoalPhaseNativeMetadata.normalizePublicJson).fold(Consequence.stateConflict(_), Consequence.success))

  def admitC(request: W.Request, result: W.Result): Consequence[Unit] = for {
    _ <- validateRequestC(request)
    _ <- _check(result != null, "repair result is absent")
    _ <- validateSourcesC(result.repairFindingSources)
    _ <- result.repairFindingSources match {
      case None => Consequence.unit
      case Some(sources) => for {
        _ <- _check(request.kind == "ReviewStep" && request.purposeRepair.nonEmpty && result.outcome == "repair" &&
          result.findings != null && result.findings.size == sources.size && result.findings.forall(text) &&
          result.planRevision.isEmpty && result.dependencyProposal.isEmpty,
          "typed sources require an actual newly issued Step review repair")
        contract = request.purposeRepair.get
        _ <- _check(sources.collect { case value: StepPurpose => value }.forall(value =>
          value.stepIdentity == contract.stepIdentity && value.affectedPaths.forall(path =>
            contract.ownedPaths.exists(owner => path == owner || path.startsWith(owner + "/")))),
          "purpose finding crosses the actual issued Step ownership")
        required = request.selectedPlan.effectiveAcceptance.toVector.flatMap(boundary =>
          GoalPhaseAcceptanceBoundary.applicable(boundary, request.kind)).filter(_.required).map(_.identity)
        observations = result.acceptance.toVector.flatMap(_.conditions)
        _ <- _check(required.forall(id => observations.exists(value => value.identity == id && value.status != "not-evaluated")),
          "typed repair requires every applicable required condition to be evaluated")
        failures = observations.filter(value => required.contains(value.identity) && value.status == "unsatisfied")
        supplied = sources.collect { case value: RequiredCondition => value.conditionIdentity -> result.findings(value.findingOrdinal - 1) }
        _ <- _check(supplied == failures.map(value => value.identity -> value.detail),
          "typed required sources must cover exactly the ordered required failures and original details")
      } yield ()
    }
  } yield ()

  def text(value: String): Boolean = value != null && value.trim.nonEmpty && !value.contains('\u0000') &&
    value.getBytes(UTF_8).length <= 16384
  def path(value: String): Boolean = text(value) && !value.exists(_.isControl) && !value.startsWith("/") &&
    !value.contains("\\") && value.split("/", -1).forall(part => part.nonEmpty && part != "." && part != ".." && part != ".git")
  def paths(value: Vector[String]): Boolean = value != null && value.nonEmpty && value.size <= 256 &&
    value.distinct == value && value.forall(path)
  private def _valid_source(value: Source): Boolean = value != null && value.findingOrdinal >= 1 && value.findingOrdinal <= 128 && (value match {
    case source: RequiredCondition => source.conditionIdentity != null && source.conditionIdentity.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
    case source: StepPurpose => text(source.stepIdentity) && text(source.rationale) && paths(source.affectedPaths)
  })
  private def _check(valid: Boolean, reason: String): Consequence[Unit] =
    if (valid) Consequence.unit else Consequence.stateConflict("GoalPhase purpose repair: " + reason)
}
