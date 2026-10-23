package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import io.circe.{Decoder, DecodingFailure, Encoder, Json, JsonObject}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.config.{ComponentInitializationParameters, ComponentParameterDecoder, ComponentParameterKey}
import org.goldenport.cncf.context.RuntimeContext.PropertyNameContext
import org.goldenport.cncf.workflow.{ContextReference, WorkflowResultJsonV1}
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseOrderedProgression as O

/** Finite nonblocking review observations and assigned recording replies.
  * Source references and these pure values grant no native-history or effect authority.
  * @since   Oct. 18, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseFindingRecording {
  // Bottom-up codecs reuse the owning Plan, issue and progress contracts.
  private given Encoder[ContextReference] = deriveEncoder
  private given Decoder[ContextReference] = _decoder(deriveDecoder[ContextReference], Set("identity", "revision"))(_reference)
  private given Encoder[O.Snapshot] = deriveEncoder
  private given Decoder[O.Snapshot] = deriveDecoder
  private given Encoder[O.Commit] = deriveEncoder
  private given Decoder[O.Commit] = deriveDecoder

  final case class Observation(identity: String, category: String, summary: String, rationale: String)
  object Observation {
    given Encoder[Observation] = deriveEncoder
    given Decoder[Observation] = _decoder(deriveDecoder[Observation],
      Set("identity", "category", "summary", "rationale"))(_observation)
  }
  final case class Observations(items: Vector[Observation])
  object Observations {
    given Encoder[Observations] = deriveEncoder
    given Decoder[Observations] = _decoder(deriveDecoder[Observations], Set("items"))(_observations)
  }
  final case class Item(sourceReference: ContextReference, sourceKind: String,
    stepIdentity: Option[String], observation: Observation, destination: String)
  object Item {
    given Encoder[Item] = deriveEncoder[Item].mapJson(_omit(_, Set("stepIdentity")))
    given Decoder[Item] = _decoder(deriveDecoder[Item],
      Set("sourceReference", "sourceKind", "stepIdentity", "observation", "destination"), Set("stepIdentity"))(_item)
  }
  final case class Request(instanceIdentity: String, sourceRevision: String, batchIdentity: String,
    selectedPlan: O.Plan, originalBase: O.Snapshot, completed: Vector[O.ClosedStep],
    stepIdentity: Option[String], items: Vector[Item], requiredIssues: Option[GoalPhaseFixIssues.State],
    repairProgress: Option[GoalPhaseRepairProgress.Snapshot])
  object Request {
    given Encoder[Request] = deriveEncoder[Request].mapJson(_omit(_, Set("stepIdentity", "requiredIssues", "repairProgress")))
    given Decoder[Request] = _decoder(deriveDecoder[Request], Set("instanceIdentity", "sourceRevision", "batchIdentity",
      "selectedPlan", "originalBase", "completed", "stepIdentity", "items", "requiredIssues", "repairProgress"),
      Set("stepIdentity", "requiredIssues", "repairProgress"))(validateRequestC(_).isSuccess)
  }
  final case class SavedSection(file: String, section: String)
  object SavedSection {
    given Encoder[SavedSection] = deriveEncoder
    given Decoder[SavedSection] = _decoder(deriveDecoder[SavedSection], Set("file", "section"))(_section)
  }
  final case class ItemResult(sourceReference: ContextReference, identity: String, outcome: String,
    records: Vector[SavedSection], rationale: String)
  object ItemResult {
    given Encoder[ItemResult] = deriveEncoder
    given Decoder[ItemResult] = _decoder(deriveDecoder[ItemResult],
      Set("sourceReference", "identity", "outcome", "records", "rationale"))(_item_result)
  }
  final case class Result(outcome: String, summary: String, items: Vector[ItemResult])
  object Result {
    given Encoder[Result] = deriveEncoder
    given Decoder[Result] = _decoder(deriveDecoder[Result], Set("outcome", "summary", "items"))(_result)
  }
  final case class Policy(hygiene: String, developmentCandidate: String) {
    def validateC(plan: O.Plan, fixedCandidatePaths: Vector[String]): Consequence[Unit] = for {
      _ <- _check(_policy(this), "policy destinations are invalid")
      _ <- O.validatePlanC(plan)
      _ <- _check(fixedCandidatePaths != null && Vector(hygiene, developmentCandidate).forall(p =>
        fixedCandidatePaths.contains(p) && plan.steps.forall(_.ownedPaths.contains(p))),
        "destinations must be explicit entries of every Step and the host candidate paths")
    } yield ()
  }

  def validateObservationsOptionC(value: Option[Observations]): Consequence[Unit] =
    _check(value != null && value.forall(_observations), "review observations are invalid")

  def admitObservationsC(kind: String, value: Option[Observations]): Consequence[Unit] = for {
    _ <- validateObservationsOptionC(value)
    _ <- _check(value.isEmpty || Set("ReviewStep", "ReviewPhase")(kind),
      "only actual Step or Phase review may return observations")
  } yield ()

  def validateRequestC(value: Request): Consequence[Unit] = for {
    _ <- _check(value != null && Vector(value.instanceIdentity, value.sourceRevision, value.batchIdentity).forall(_exact) &&
      value.stepIdentity != null && value.stepIdentity.forall(_exact) && value.selectedPlan != null,
      "request metadata or options are invalid")
    _ <- O.validatePlanC(value.selectedPlan)
    _ <- GoalPhaseAcceptanceBoundary.savedPlanC(value.selectedPlan)
    _ <- GoalPhaseFixIssues.validateOptionC(value.requiredIssues)
    _ <- GoalPhaseRepairProgress.validateOptionC(value.repairProgress)
    _ <- _check(value.originalBase != null && _oid(value.originalBase.head) && _oid(value.originalBase.tree) &&
      value.completed != null && value.completed.size <= value.selectedPlan.steps.size &&
      value.completed.forall(c => c != null && c.commit != null && _exact(c.step) && O.validStepAcceptance(c) &&
        Vector(c.commit.identity, c.commit.parent, c.commit.tree).forall(_oid)) &&
      value.completed.map(_.step) == value.selectedPlan.steps.take(value.completed.size).map(_.identity) &&
      value.completed.map(_.commit.identity).distinct.size == value.completed.size &&
      value.completed.map(_.commit.parent) == (Vector(value.originalBase.head) ++ value.completed.map(_.commit.identity)).take(value.completed.size),
      "completed Steps must form the selected ordered commit prefix")
    _ <- _check(value.items != null && value.items.nonEmpty && value.items.size <= 256 && value.items.forall(_item) &&
      value.items.map(i => _key(i.sourceReference, i.observation.identity)).distinct.size == value.items.size,
      "assigned items must be finite nonempty and uniquely source-keyed")
    _ <- _check(value.stepIdentity match {
      case Some(step) => value.completed.lastOption.exists(_.step == step) &&
        value.items.forall(i => i.sourceKind == "ReviewStep" && i.stepIdentity.contains(step))
      case None => value.completed.size == value.selectedPlan.steps.size &&
        value.items.forall(i => i.sourceKind == "ReviewPhase" && i.stepIdentity.isEmpty)
    }, "recording scope differs from the completed Step or Phase input")
  } yield ()

  def admitResultC(request: Request, result: Result): Consequence[Unit] = for {
    _ <- validateRequestC(request)
    _ <- _check(_result(result), "finite recording result is invalid")
    _ <- _check(result.items.map(i => _key(i.sourceReference, i.identity)) ==
      request.items.map(i => _key(i.sourceReference, i.observation.identity)),
      "result keys must exactly match assigned source keys in order")
    _ <- _check(request.items.zip(result.items).forall { (assigned, returned) =>
      returned.records.forall(_.file == assigned.destination)
    }, "saved records must name the exact assigned destination")
  } yield ()

  val schemaVersion = "sm-workflow.finding-recording-policy.v1"
  val declaration: ComponentParameterKey[Policy] = ComponentParameterKey.optional(
    "sm-workflow.findings.recording", ComponentParameterDecoder {
      case value: ConfigurationValue.ObjectValue => decodePolicyC(_json(value))
      case ConfigurationValue.StringValue(value) => io.circe.parser.parse(value)
        .fold(_ => _invalid("policy JSON is invalid"), decodePolicyC)
      case _ => _invalid("policy requires an explicit structured definition")
    })

  def decodePolicyC(value: Json): Consequence[Policy] = {
    val parsed = for {
      fields <- Option(value).flatMap(_.asObject).toRight("policy must be an object")
      _ <- Either.cond(fields.keys.toSet == Set("schemaVersion", "hygiene", "developmentCandidate"), (), "policy keys differ")
      schema <- value.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(schema == schemaVersion, (), "policy schema differs")
      hygiene <- value.hcursor.get[String]("hygiene").left.map(_.message)
      candidate <- value.hcursor.get[String]("developmentCandidate").left.map(_.message)
      policy = Policy(hygiene, candidate)
      _ <- Either.cond(_policy(policy), (), "policy paths are invalid")
    } yield policy
    parsed.fold(_invalid, Consequence.success)
  }
  def resolvePolicyC(parameters: ComponentInitializationParameters): Consequence[Option[Policy]] =
    if (parameters == null) _invalid("Component initialization parameters are absent")
    else parameters.resolve(declaration).flatMap(p =>
      _check(p.value != null && p.value.forall(_policy), "policy option is invalid").map(_ => p.value))

  private def _map[A, B](values: Vector[A])(f: A => B): Vector[B] =
    if (values == null) null else values.map(v => if (v == null) null.asInstanceOf[B] else f(v))
  private def _option[A, B](value: Option[A])(f: A => B): Option[B] =
    if (value == null) null else value.map(v => if (v == null) null.asInstanceOf[B] else f(v))
  def publicObservations(value: Option[Observations]): Option[V.GoalPhaseReviewObservations] = _option(value)(o =>
    V.GoalPhaseReviewObservations(_map(o.items)(i => V.GoalPhaseReviewObservation(i.identity, i.category, i.summary, i.rationale))))
  def publicObservationsC(value: Option[Observations]): Consequence[Option[V.GoalPhaseReviewObservations]] =
    validateObservationsOptionC(value).map(_ => publicObservations(value))
  def internalObservationsC(value: Option[V.GoalPhaseReviewObservations]): Consequence[Option[Observations]] = {
    val mapped = _option(value)(o => Observations(_map(o.items)(i => Observation(i.identity, i.category, i.summary, i.rationale))))
    validateObservationsOptionC(mapped).map(_ => mapped)
  }

  /** Only CNCF declaration-wire spelling normalization; strict business decoding follows. */
  def normalizeObservations(value: Json): Either[String, Json] = {
    def _named_(value: Json): Either[String, JsonObject] = for {
      fields <- Option(value).flatMap(_.asObject).toRight("review observations must be an object")
      pairs = fields.toVector.map((key, value) => PropertyNameContext.default.normalizeInputName(key) -> value)
      _ <- Either.cond(pairs.map(_._1).distinct.size == pairs.size, (), "review observation names collide")
    } yield JsonObject.fromIterable(pairs)
    for {
      fields <- _named_(value)
      rows <- fields("items").flatMap(_.asArray).toRight("review observation items must be an array")
      items <- rows.foldLeft[Either[String, Vector[Json]]](Right(Vector.empty))((prior, row) => for {
        values <- prior
        item <- _named_(row)
      } yield values :+ Json.fromJsonObject(item))
      parsed <- Json.fromJsonObject(fields.add("items", Json.fromValues(items))).as[Observations].left.map(_.message)
    } yield parsed.asJson
  }

  private def _identity(value: String): Boolean = value != null && value.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
  private def _text(value: String, maximum: Int): Boolean = value != null && value.trim.nonEmpty &&
    !value.contains('\u0000') && UTF_8.newEncoder().canEncode(value) && value.getBytes(UTF_8).length <= maximum
  private def _exact(value: String): Boolean = _text(value, 1024) && value.trim == value && !value.exists(_.isControl)
  private def _oid(value: String): Boolean = value != null && value.matches("[0-9a-f]{40}")
  private def _reference(value: ContextReference): Boolean = value != null && _exact(value.identity) && _exact(value.revision)
  private def _key(reference: ContextReference, identity: String): (String, String, String) =
    (reference.identity, reference.revision, identity)
  private def _observation(value: Observation): Boolean = value != null && _identity(value.identity) &&
    Set("HYGIENE", "DEVELOPMENT_CANDIDATE")(value.category) && _text(value.summary, 16384) && _text(value.rationale, 16384)
  private def _observations(value: Observations): Boolean = value != null && value.items != null && value.items.size <= 128 &&
    value.items.forall(_observation) && value.items.map(_.identity).distinct.size == value.items.size
  private def _item(value: Item): Boolean = value != null && _reference(value.sourceReference) &&
    value.stepIdentity != null && _observation(value.observation) && RepositorySyncNativeConflictInput.relative(value.destination) &&
    (value.sourceKind match {
      case "ReviewStep" => value.stepIdentity.exists(_exact)
      case "ReviewPhase" => value.stepIdentity.isEmpty
      case _ => false
    })
  private def _section(value: SavedSection): Boolean = value != null && RepositorySyncNativeConflictInput.relative(value.file) && _exact(value.section)
  private def _item_result(value: ItemResult): Boolean = value != null && _reference(value.sourceReference) && _identity(value.identity) &&
    value.records != null && value.records.size <= 128 && value.records.forall(_section) &&
    value.records.map(r => (r.file, r.section)).distinct.size == value.records.size && _text(value.rationale, 16384) &&
    (value.outcome match {
      case "recorded" | "partial" => value.records.nonEmpty
      case "failed" => value.records.isEmpty
      case _ => false
    })
  private def _result(value: Result): Boolean = value != null && _text(value.summary, 16384) && value.items != null &&
    value.items.nonEmpty && value.items.size <= 256 && value.items.forall(_item_result) &&
    value.items.map(i => _key(i.sourceReference, i.identity)).distinct.size == value.items.size &&
    value.outcome == (if (value.items.forall(_.outcome == "recorded")) "completed"
      else if (value.items.forall(_.outcome == "failed")) "failed" else "partial")
  private def _policy(value: Policy): Boolean = value != null &&
    RepositorySyncNativeConflictInput.relative(value.hygiene) && RepositorySyncNativeConflictInput.relative(value.developmentCandidate)
  private def _check(value: Boolean, reason: String): Consequence[Unit] = if (value) Consequence.unit else _invalid(reason)
  private def _invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("GoalPhase finding recording: " + reason)
  private def _omit(value: Json, optional: Set[String]): Json = value.mapObject(_.filter((key, value) => !optional(key) || !value.isNull))
  private def _null_optional(value: Json): Boolean = value.asObject.exists(_.toVector.exists { (key, nested) =>
    (Set("stepIdentity", "requiredIssues", "repairProgress", "workClassification", "phaseReviewClassification",
      "acceptance", "effectiveAcceptance", "usage", "sourceIdentity", "latestDisposition", "movement", "previousStatus")(key) && nested.isNull) ||
      _null_optional(nested)
  }) || value.asArray.exists(_.exists(_null_optional))
  private def _decoder[A](decoder: Decoder[A], keys: Set[String], optional: Set[String] = Set.empty)
    (valid: A => Boolean): Decoder[A] = Decoder.instance { cursor =>
      val fields = cursor.value.asObject
      if (!fields.exists(f => f.keys.forall(keys) && (keys -- optional).forall(f.contains)) || _null_optional(cursor.value))
        Left(DecodingFailure("GoalPhase finding recording: invalid keys or explicit null option", cursor.history))
      else decoder.tryDecode(cursor).flatMap(value => if (valid(value)) Right(value)
        else Left(DecodingFailure("GoalPhase finding recording: invalid business value", cursor.history)))
    }
  private class Codec[A](val typeIdentity: String, schema: String)(using Encoder[A], Decoder[A])
      extends WorkflowResultJsonV1.PayloadCodec[A] {
    def encode(value: A): Json = Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> value.asJson)
    def decode(value: Json): Either[String, A] = for {
      wire <- Option(value).toRight("GoalPhase finding recording payload is missing")
      version <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(version == schema, (), "GoalPhase finding recording schema differs")
      parsed <- wire.hcursor.get[A]("payload").left.map(_.message)
    } yield parsed
  }
  val inputCodec: WorkflowResultJsonV1.PayloadCodec[Request] =
    new Codec[Request]("GoalPhaseFindingRecordingRequest", "sm-workflow.goal-phase-finding-recording-request.v1")
  val resultCodec: WorkflowResultJsonV1.PayloadCodec[Result] =
    new Codec[Result]("GoalPhaseFindingRecordingResult", "sm-workflow.goal-phase-finding-recording-result.v1")
  private def _json(value: ConfigurationValue): Json = value match {
    case ConfigurationValue.StringValue(v) => Json.fromString(v)
    case ConfigurationValue.NumberValue(v) => Json.fromBigDecimal(v)
    case ConfigurationValue.BooleanValue(v) => Json.fromBoolean(v)
    case ConfigurationValue.ListValue(v) => Json.fromValues(v.map(_json))
    case ConfigurationValue.ObjectValue(v) => Json.obj(v.toVector.map((k, v) => k -> _json(v))*)
    case ConfigurationValue.NullValue => Json.Null
  }
}
