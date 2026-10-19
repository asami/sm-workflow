package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.net.URLClassLoader
import java.nio.file.Path
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.TimeUnit.NANOSECONDS
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.context.RuntimeContext
import org.goldenport.cncf.processexecution.SbtScenarioParameters.Value
import org.goldenport.cncf.validation.{ValidationDiscoveryV1 as D, ValidationMetadataV1 as M,
  ValidationOperationBindingV1 as B, ValidationInputsV1 as I}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workspace.WorkspaceId
import org.goldenport.record.Record
import GoalPhaseValidationMetadata as F
import GoalPhaseNativeValidation.Check
import GoalPhaseSliceValidationPlan as L

/** Private configuration and retained selection codecs. CNCF owns discovery,
  * metadata interpretation, query semantics and exact operation binding.
  * @since   Oct. 18, 2026
  * @version Oct. 19, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseNativeMetadata {
  final case class Profile(scope: D.DiscoveryScope, query: D.SelectionQuery,
    supplemental: Vector[D.OperationReference], source: Option[L.Source] = None)
  final case class Batch(checkIdentity: String, ordinal: Int, profile: Profile,
    inventory: D.Inventory, references: Vector[D.OperationReference], plan: Option[L.Plan] = None)

  // Exact finite wire shapes, bottom-up. Optional absence is distinct from null.
  private def _strict[A](decoder: Decoder[A], keys: Set[String], optional: Set[String] = Set.empty)
      (valid: A => Boolean = (_: A) => true): Decoder[A] = Decoder.instance { cursor =>
    cursor.value.asObject match {
      case Some(fields) if fields.keys.forall(keys) && !optional.exists(k => fields(k).exists(_.isNull)) =>
        decoder.tryDecode(cursor).flatMap(value =>
          if (valid(value)) Right(value) else Left(DecodingFailure("Invalid validation metadata value", cursor.history)))
      case _ => Left(DecodingFailure("Validation metadata requires an exact non-null typed object", cursor.history))
    }
  }
  private def _enum_decoder[A](values: Vector[A], name: A => String): Decoder[A] = Decoder.decodeString.emap(value =>
    values.find(x => name(x) == value).toRight("Unknown validation metadata enum: " + value))
  private def _omit(value: Json, keys: String*): Json = value.mapObject(fields =>
    keys.foldLeft(fields)((result, key) => if (result(key).exists(_.isNull)) result.remove(key) else result))

  given Encoder[D.Completeness] = Encoder.encodeString.contramap(_.toString)
  given Decoder[D.Completeness] = _enum_decoder(D.Completeness.values.toVector, _.toString)
  given Encoder[M.Purpose] = Encoder.encodeString.contramap(_.toString)
  given Decoder[M.Purpose] = _enum_decoder(M.Purpose.values.toVector, _.toString)
  given Encoder[M.DurationClass] = Encoder.encodeString.contramap(_.toString)
  given Decoder[M.DurationClass] = _enum_decoder(M.DurationClass.values.toVector, _.toString)
  given Encoder[ReasoningLevel] = Encoder.encodeString.contramap(_.value)
  given Decoder[ReasoningLevel] = _enum_decoder(ReasoningLevel.values.toVector, _.value)
  given Encoder[ExecutionPlacement] = Encoder.encodeString.contramap(_.value)
  given Decoder[ExecutionPlacement] = _enum_decoder(ExecutionPlacement.values.toVector, _.value)
  given Encoder[ExecutionIndependence] = Encoder.encodeString.contramap(_.value)
  given Decoder[ExecutionIndependence] = _enum_decoder(ExecutionIndependence.values.toVector, _.value)
  given Encoder[Path] = Encoder.encodeString.contramap(_.toString)
  given Decoder[Path] = Decoder.decodeString.emap { value =>
    try {
      val path = Path.of(value)
      Either.cond(path.isAbsolute && path.normalize == path && !value.exists(_.isControl), path,
        "Validation artifact root must be absolute and normalized")
    } catch { case NonFatal(_) => Left("Invalid validation artifact root") }
  }
  given Encoder[CapabilityRequirement] = deriveEncoder
  given Decoder[CapabilityRequirement] = _strict(deriveDecoder[CapabilityRequirement], Set("identity"))()
  given Encoder[RiskLevel] = deriveEncoder
  given Decoder[RiskLevel] = _strict(deriveDecoder[RiskLevel], Set("value"))()
  given Encoder[ReasoningClassReference] = deriveEncoder
  given Decoder[ReasoningClassReference] = _strict(deriveDecoder[ReasoningClassReference], Set("value"))()
  given Encoder[ExecutionDisposition] = deriveEncoder
  given Decoder[ExecutionDisposition] = _strict(deriveDecoder[ExecutionDisposition],
    Set("placement", "independence", "originContextIdentity"))(_.validateC.isSuccess)
  given Encoder[ExecutionRequirement] = deriveEncoder[ExecutionRequirement].mapJson(_omit(_, "disposition", "reasoningClass"))
  given Decoder[ExecutionRequirement] = _strict(deriveDecoder[ExecutionRequirement],
    Set("capabilities", "risk", "reasoning", "reviewRequired", "disposition", "reasoningClass"),
    Set("disposition", "reasoningClass"))(_.validateC.isSuccess)
  given Encoder[M.FeatureReference] = deriveEncoder
  given Decoder[M.FeatureReference] = _strict(deriveDecoder[M.FeatureReference], Set("scope", "identity"))(_.validateC.isSuccess)
  given Encoder[M.Origins] = deriveEncoder
  given Decoder[M.Origins] = _strict(deriveDecoder[M.Origins], Set("specification", "operation"))()
  given Encoder[I.Kind] = Encoder.encodeString.contramap(_.toString)
  given Decoder[I.Kind] = _enum_decoder(I.Kind.values.toVector, _.toString)
  given Encoder[I.Parameter] = Encoder.instance { value =>
    val fields = Json.obj("name" -> value.name.asJson, "kind" -> value.kind.asJson, "required" -> value.required.asJson)
    value.defaultValue.fold(fields) { default =>
      val json = default match {
        case Value.Text(text) => Json.fromString(text)
        case Value.BooleanValue(boolean) => Json.fromBoolean(boolean)
        case Value.Integer(integer) => Json.fromLong(integer)
      }
      fields.mapObject(_.add("defaultValue", json))
    }
  }
  private val _parameter_decoder: Decoder[I.Parameter] = Decoder.instance { cursor => for {
    name <- cursor.get[String]("name")
    kind <- cursor.get[I.Kind]("kind")
    required <- cursor.get[Boolean]("required")
    raw <- optional[Json](cursor, "defaultValue")
    default <- raw match {
      case None => Right(None)
      case Some(json) =>
        val decoded: Option[Value] = kind match {
          case I.Kind.Text => json.asString.map(Value.Text(_))
          case I.Kind.BooleanValue => json.asBoolean.map(Value.BooleanValue(_))
          case I.Kind.Integer => json.asNumber.flatMap(_.toBigDecimal)
            .filter(value => value.isWhole && value.isValidLong).map(value => Value.Integer(value.toLong))
        }
        decoded.map(value => Right(Some(value))).getOrElse(
          Left(DecodingFailure("Input default must have its exact declared primitive kind", cursor.history)))
    }
  } yield I.Parameter(name, kind, required, default) }
  given Decoder[I.Parameter] = _strict(_parameter_decoder, Set("name", "kind", "required", "defaultValue"),
    Set("defaultValue"))(value => I.validateSchemaC(Vector(value)).isSuccess)
  given Encoder[M.Metadata] = Encoder.instance { value =>
    val fields = Json.obj("purposes" -> value.purposes.toVector.sortBy(_.toString).asJson,
      "features" -> value.features.toVector.sortBy(x => (x.scope, x.identity)).asJson,
      "durationClass" -> value.durationClass.asJson, "origins" -> value.origins.asJson)
    val duration = value.expectedDuration.fold(fields)(d => fields.mapObject(_.add("expectedDurationNanos",
      Json.fromBigInt(BigInt(d.length) * BigInt(d.unit.toNanos(1L))))))
    val requirement = value.executionRequirement.fold(duration)(r => duration.mapObject(_.add("executionRequirement", r.asJson)))
    if (value.inputSchema.isEmpty) requirement else requirement.mapObject(_.add("inputSchema", value.inputSchema.asJson))
  }
  private val _metadata_decoder: Decoder[M.Metadata] = Decoder.instance { cursor => for {
    purposes <- cursor.get[Set[M.Purpose]]("purposes")
    features <- cursor.get[Set[M.FeatureReference]]("features")
    duration <- optional[Long](cursor, "expectedDurationNanos")
    durationclass <- cursor.get[M.DurationClass]("durationClass")
    requirement <- optional[ExecutionRequirement](cursor, "executionRequirement")
    origins <- cursor.get[M.Origins]("origins")
    schema <- optional[Vector[I.Parameter]](cursor, "inputSchema").map(_.getOrElse(Vector.empty))
    _ <- Either.cond(I.validateSchemaC(schema).toOption.contains(schema), (),
      DecodingFailure("Input schema must be producer-valid and canonical", cursor.history))
    _ <- Either.cond(duration.forall(_ > 0), (), DecodingFailure("Expected duration must be positive integral nanoseconds", cursor.history))
  } yield M.Metadata(purposes, features, duration.map(n => FiniteDuration(n, NANOSECONDS)), durationclass, requirement, origins, schema) }
  given Decoder[M.Metadata] = _strict(_metadata_decoder,
    Set("purposes", "features", "expectedDurationNanos", "durationClass", "executionRequirement", "origins", "inputSchema"),
    Set("expectedDurationNanos", "executionRequirement", "inputSchema"))()
  given Encoder[D.OperationReference] = deriveEncoder
  given Decoder[D.OperationReference] = _strict(deriveDecoder[D.OperationReference],
    Set("component", "specification", "operation"))(referenceValid)
  given Encoder[D.Provenance] = deriveEncoder
  given Decoder[D.Provenance] = _strict(deriveDecoder[D.Provenance],
    Set("artifactIdentity", "artifactRevision", "sourceRevision", "operationRevision", "abiVersion"))()
  given Encoder[D.ScalaTestSelector] = deriveEncoder
  given Decoder[D.ScalaTestSelector] = _strict(deriveDecoder[D.ScalaTestSelector], Set("suiteClassName", "testName"))()
  given Encoder[D.ArtifactScope] = deriveEncoder
  given Decoder[D.ArtifactScope] = _strict(deriveDecoder[D.ArtifactScope],
    Set("componentIdentity", "artifactIdentity", "artifactRevision", "sourceRevision", "root", "packagePrefix"))()
  given Encoder[D.DiscoveryScope] = deriveEncoder
  given Decoder[D.DiscoveryScope] = _strict(deriveDecoder[D.DiscoveryScope], Set("identity", "revision", "artifacts"))(_scope_valid)
  given Encoder[D.Diagnostic] = deriveEncoder
  given Decoder[D.Diagnostic] = _strict(deriveDecoder[D.Diagnostic], Set("code", "source", "message"))()
  given Encoder[D.Operation] = deriveEncoder[D.Operation].mapJson(_omit(_, "metadata"))
  given Decoder[D.Operation] = _strict(deriveDecoder[D.Operation],
    Set("reference", "provenance", "selector", "metadata", "diagnostics"), Set("metadata"))()
  given Encoder[D.Inventory] = deriveEncoder
  given Decoder[D.Inventory] = _strict(deriveDecoder[D.Inventory], Set("scope", "completeness", "operations", "diagnostics"))(
    value => D.selectC(value, D.SelectionQuery()).isSuccess)
  given Encoder[D.SelectionQuery] = Encoder.instance(value => Json.obj(
    "purposes" -> value.purposes.toVector.sortBy(_.toString).asJson,
    "features" -> value.features.toVector.sortBy(x => (x.scope, x.identity)).asJson))
  given Decoder[D.SelectionQuery] = _strict(deriveDecoder[D.SelectionQuery], Set("purposes", "features"))(queryValid)

  object Profile {
    given Encoder[Profile] = deriveEncoder[Profile].mapJson(_omit(_, "source"))
    private val _decoder: Decoder[Profile] = deriveDecoder
    given Decoder[Profile] = _strict(Decoder.instance(cursor => for {
      value <- _decoder.tryDecode(cursor)
      source <- optional[L.Source](cursor, "source")
    } yield value.copy(source = source)), Set("scope", "query", "supplemental", "source"), Set("source"))(profileValid)
  }
  object Batch {
    given Encoder[Batch] = deriveEncoder[Batch].mapJson(_omit(_, "plan"))
    private val _decoder: Decoder[Batch] = deriveDecoder
    given Decoder[Batch] = _strict(Decoder.instance(cursor => for {
      value <- _decoder.tryDecode(cursor)
      plan <- optional[L.Plan](cursor, "plan")
    } yield value.copy(plan = plan)),
      Set("checkIdentity", "ordinal", "profile", "inventory", "references", "plan"), Set("plan"))(value => bindC(value).isSuccess)
  }

  private[admission] def optional[A: Decoder](cursor: HCursor, key: String): Decoder.Result[Option[A]] =
    cursor.downField(key).focus match {
      case None => Right(None)
      case Some(value) if value.isNull => Left(DecodingFailure(key + " must be absent or non-null", cursor.history))
      case Some(_) => cursor.get[A](key).map(Some(_))
    }
  private def _text(value: String): Boolean = value != null && value.nonEmpty && !value.exists(_.isControl)
  def hostIdentityValid(value: String): Boolean = value != null && value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
  def runtimeIdentityValid(value: String): Boolean = _text(value) && UTF_8.newEncoder().canEncode(value) && value.getBytes(UTF_8).length <= 4096
  def referenceValid(value: D.OperationReference): Boolean = value != null &&
    Vector(value.component, value.specification, value.operation).forall(_text)
  private def _json_valid(value: Json): Boolean = value != null && value.fold(true, _ => true, _ != null,
    _ != null, values => values != null && values.forall(_json_valid),
    fields => fields != null && fields.toVector.forall((key, nested) => key != null && _json_valid(nested)))
  private[admission] def parametersValid(values: Map[String, Json]): Boolean = values != null && values.size <= 16 &&
    values.forall((key, value) => key != null && _json_valid(value))
  private def _scope_valid(value: D.DiscoveryScope): Boolean = value != null && _text(value.identity) && _text(value.revision) &&
    value.artifacts != null && value.artifacts.nonEmpty && value.artifacts.size <= 16 && value.artifacts.forall(a =>
      a != null && Vector(a.componentIdentity, a.artifactIdentity, a.artifactRevision, a.sourceRevision, a.packagePrefix).forall(_text) &&
        a.root != null && a.root.isAbsolute && a.root.normalize == a.root && !a.root.toString.exists(_.isControl))
  private[admission] def queryValid(value: D.SelectionQuery): Boolean = value != null && value.purposes != null &&
    value.features != null && !value.purposes.contains(null) && value.features.forall(f => f != null && f.validateC.isSuccess)
  def profileValid(value: Profile): Boolean = value != null && _scope_valid(value.scope) && queryValid(value.query) &&
    value.supplemental != null && value.supplemental.size <= 16 && value.supplemental.forall(referenceValid) &&
    value.source != null && value.source.forall(source => L.sourceValid(source) &&
      value.query == D.SelectionQuery() && value.supplemental.isEmpty)
  def logicalIdentity(host: String, reference: D.OperationReference): String = "metadata:" +
    Vector(host, reference.component, reference.specification, reference.operation).map(value => value.length + ":" + value).mkString
  def declaredIdentities(checks: Vector[Check]): Vector[String] = checks.map(c => c.hostIdentity.getOrElse(c.identity)).distinct
  private def _workspace_c(ordinal: Int): Consequence[WorkspaceId] = WorkspaceId.parseC("sm-workflow-repository-validation-" + ordinal)
  private def _request_c(profile: Profile, ordinal: Int, plan: Option[L.Plan]): Consequence[F.Request] =
    if (!profileValid(profile) || ordinal < 1 || ordinal > 16 || plan == null)
      _invalid("Invalid native metadata profile, ordinal or Slice plan")
    else (profile.source, plan) match {
      case (None, None) => _workspace_c(ordinal).map(F.Request(profile.scope, profile.query, profile.supplemental, _))
      case (Some(source), Some(selected)) => for {
        admitted <- L.admitC(source, selected)
        workspace <- _workspace_c(ordinal)
      } yield F.Request(profile.scope, admitted.metadataSelection, Vector.empty, workspace,
        admitted.additionalOperations.map(i => F.Invocation(i.reference, i.parameters)), admitted.executionParameters)
      case _ => _invalid("Source-bound profiles require their exact captured Slice plan; embedded profiles forbid one")
    }

  def bindC(batch: Batch): Consequence[Vector[B.BoundOperation]] =
    if (batch == null || !hostIdentityValid(batch.checkIdentity) || batch.references == null ||
        batch.references.isEmpty || batch.references.size > 16 || !batch.references.forall(referenceValid))
      _invalid("Invalid retained native metadata batch")
    else for {
      request <- _request_c(batch.profile, batch.ordinal, batch.plan)
      selected <- F.selectC(request, batch.inventory).flatMap(_.requireRunnableC)
      _ <- batch.plan.fold(Consequence.unit)(L.coverageC(_, selected.inventory, selected.operations.map(_.operation.reference)))
      _ <- if (selected.operations.map(_.operation.reference) == batch.references) Consequence.unit
        else _invalid("Retained metadata references differ from the exact query and supplemental union")
    } yield selected.operations

  private def _expanded(batch: Batch): Vector[Check] = batch.references.zipWithIndex.map { (reference, index) =>
    val base = logicalIdentity(batch.checkIdentity, reference)
    val identity = if (batch.references.count(_ == reference) == 1) base
      else base + ":invocation:" + batch.references.take(index + 1).count(_ == reference)
    Check(identity, batch.ordinal, WorkflowSbtValidation.Kind.TestOnly, None, Some(batch.checkIdentity), Some(reference))
  }

  /** Only the fresh Validation boundary calls this loader-owning entry point. */
  def prepareC(requested: Vector[Check], parent: ClassLoader,
    captured: Map[String, L.Plan] = Map.empty): Consequence[(Vector[Check], Vector[Batch])] =
    if (parent == null || !GoalPhaseNativeValidation.Policy("metadata-selection", 1, requested).validateC.isSuccess)
      _invalid("Native metadata requires literal host bindings and the actual component ClassLoader")
    else if (captured == null || captured.keySet != requested.filter(_.metadata.exists(_.source.nonEmpty)).map(_.identity).toSet)
      _invalid("Captured Slice plans must exactly match the selected source-bound host checks")
    else requested.foldLeft(Consequence.success((Vector.empty[Check], Vector.empty[Batch]))) { (prior, check) => prior.flatMap {
      case (checks, batches) => check.metadata match {
        case None => Consequence.success((checks :+ check, batches))
        case Some(profile) => for {
          request <- _request_c(profile, check.ordinal, captured.get(check.identity))
          selection <- {
            try {
              val loader = new URLClassLoader(profile.scope.artifacts.map(_.root.toUri.toURL).toArray, parent)
              try F.discoverC(request, loader).flatMap(_.requireRunnableC)
              finally loader.close()
            } catch { case NonFatal(error) => _invalid("Native metadata discovery loader failed: " + error.getClass.getSimpleName) }
          }
          _ <- captured.get(check.identity).fold(Consequence.unit)(L.coverageC(_, selection.inventory,
            selection.operations.map(_.operation.reference)))
          batch = Batch(check.identity, check.ordinal, profile, selection.inventory,
            selection.operations.map(_.operation.reference), captured.get(check.identity))
        } yield (checks ++ _expanded(batch), batches :+ batch)
      }
    }}.flatMap { case (checks, batches) => validateRetainedC(requested, checks, batches).map(_ => (checks, batches)) }

  /** Pure exact re-selection and binding; no loader or artifact access on resume. */
  def validateRetainedC(requested: Vector[Check], selected: Vector[Check], batches: Vector[Batch]): Consequence[Unit] =
    if (requested == null || requested.isEmpty || requested.size > 16 || requested.exists(c => c == null ||
        !hostIdentityValid(c.identity) || c.kind == null || c.ordinal < 1 || c.ordinal > 16 ||
        c.metadata == null || c.hostIdentity == null || c.operation == null || c.hostIdentity.nonEmpty || c.operation.nonEmpty ||
        c.metadata.exists(p => c.kind != WorkflowSbtValidation.Kind.TestOnly || !profileValid(p))) ||
        requested.map(_.identity).distinct.size != requested.size || requested.map(_.ordinal).distinct.size != requested.size ||
        selected == null || selected.isEmpty || selected.size > 16 || batches == null ||
        selected.exists(c => c == null || !runtimeIdentityValid(c.identity)) ||
        selected.map(_.identity).distinct.size != selected.size ||
        batches.map(b => Option(b).map(_.checkIdentity)) != requested.filter(_.metadata.nonEmpty).map(c => Some(c.identity)))
      _invalid("Native metadata selection differs from its fixed host groups or bounds")
    else requested.foldLeft(Consequence.success(Vector.empty[Check])) { (prior, check) => prior.flatMap { checks =>
      check.metadata match {
        case None => Consequence.success(checks :+ check)
        case Some(profile) =>
          val batch = batches.find(_.checkIdentity == check.identity).get
          if (batch.ordinal != check.ordinal || batch.profile != profile) _invalid("Retained metadata crosses its host profile or ordinal")
          else bindC(batch).map(_ => checks ++ _expanded(batch))
      }
    }}.flatMap(expected => if (expected == selected) Consequence.unit else _invalid("Retained metadata expanded checks differ from exact selected operations"))

  def validateRuntimeC(selected: Vector[Check], batches: Vector[Batch]): Consequence[Unit] =
    if (selected == null || batches == null || selected.exists(c => c == null || c.metadata == null ||
        c.hostIdentity == null || c.operation == null || c.metadata.nonEmpty) || batches.contains(null))
      _invalid("Native metadata runtime fields are incomplete")
    else {
      val requested = selected.map { check => check.hostIdentity match {
        case None => check
        case Some(host) => batches.find(_.checkIdentity == host).map(batch =>
          Check(host, batch.ordinal, WorkflowSbtValidation.Kind.TestOnly, Some(batch.profile))).getOrElse(check)
      }}.distinct
      validateRetainedC(requested, selected, batches)
    }

  def operationC(check: Check, batches: Vector[Batch]): Consequence[B.BoundOperation] = for {
    _ <- validateRuntimeC(batches.flatMap(_expanded), batches)
    batch <- batches.find(b => check.hostIdentity.contains(b.checkIdentity)).map(Consequence.success)
      .getOrElse(_invalid("Selected metadata operation has no exact retained batch"))
    operations <- bindC(batch)
    operation <- operations.zip(_expanded(batch)).find { (bound, expandedcheck) =>
      expandedcheck == check && check.operation.contains(bound.operation.reference)
    }.map(pair => Consequence.success(pair._1))
      .getOrElse(_invalid("Selected metadata operation differs from its retained exact identity"))
  } yield operation

  /** Structured projection only; callers supply already validated internal JSON. */
  def record(value: Json): Record = {
    def _field_(json: Json, path: Vector[String]): Any = json.fold[Any](null, identity,
      number => number.toBigDecimal.get, identity, array => array.map(_field_(_, path)), fields =>
        if (_parameter_path(path) && fields.nonEmpty) fields.toVector.sortBy(_._1).map { (name, nested) =>
          Record.createFull(Vector("name" -> name, "value" -> _field_(nested, path :+ "value")))
        } else Record.createFull(fields.toVector.map((key, nested) => key -> _field_(nested, path :+ key))))
    _field_(value, Vector.empty).asInstanceOf[Record]
  }
  /** Public Record naming projection followed by the unchanged exact domain codec. */
  def normalizeBatches(value: Json): Either[String, Json] =
    _normalize_public_json(value).flatMap(_.as[Vector[Batch]].left.map(_.message)).map(_.asJson)

  def normalizePublicJson(value: Json): Either[String, Json] =
    _normalize_public_json(value)

  private def _parameter_path(path: Vector[String]): Boolean =
    path == Vector("plan", "executionParameters") || path == Vector("plan", "additionalOperations", "parameters")

  private def _normalize_parameters(value: Json): Either[String, Json] = value.asObject match {
    case Some(_) => Right(value)
    case None => value.asArray match {
      case Some(entries) if entries.nonEmpty =>
        entries.foldLeft[Either[String, Vector[(String, Json)]]](Right(Vector.empty)) { (result, entry) => for {
          accumulated <- result
          fields <- entry.asObject.toRight("Public validation parameter entry must be an exact name/value object")
          _ <- Either.cond(fields.keys.toSet == Set("name", "value"), (),
            "Public validation parameter entry requires only name and value")
          name <- fields("name").flatMap(_.asString).filter(_.nonEmpty)
            .toRight("Public validation parameter entry requires a nonempty string name")
          nested <- fields("value").filter(json => !json.isNull && !json.isObject && !json.isArray)
            .toRight("Public validation parameter entry requires a non-null primitive value")
          _ <- Either.cond(!accumulated.exists(_._1 == name), (), "Duplicate public validation parameter name: " + name)
        } yield accumulated :+ (name -> nested) }.map(fields => Json.obj(fields.sortBy(_._1)*))
      case _ => Left("Public validation parameters require a canonical object or nonempty name/value entries")
    }
  }

  private def _normalize_public_json(value: Json, path: Vector[String] = Vector.empty): Either[String, Json] =
    if (_parameter_path(path)) _normalize_parameters(value) else value.asObject match {
    case Some(fields) =>
      val names = fields.toVector.map { (key, nested) =>
        (key, RuntimeContext.PropertyNameContext.default.normalizeInputName(key), nested)
      }
      names.groupBy(_._2).find(_._2.size > 1) match {
        case Some((key, originals)) => Left("Ambiguous validation metadata fields normalize to " + key +
          ": " + originals.map(_._1).mkString(", "))
        case None => names.foldLeft[Either[String, Vector[(String, Json)]]](Right(Vector.empty)) {
          case (result, (_, key, nested)) => for {
            accumulated <- result
            normalized <- _normalize_public_json(nested, path :+ key)
          } yield accumulated :+ (key -> normalized)
        }.map(fields => Json.obj(fields*))
      }
    case None => value.asArray match {
      case Some(values) => values.foldLeft[Either[String, Vector[Json]]](Right(Vector.empty)) { (result, nested) =>
        for {
          accumulated <- result
          normalized <- _normalize_public_json(nested, path)
        } yield accumulated :+ normalized
      }.map(Json.fromValues)
      case None => Right(value)
    }
  }
  private def _invalid[A](message: String): Consequence[A] = Consequence.stateConflict(message)
}
