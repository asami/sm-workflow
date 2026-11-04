package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Codec, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.config.{ComponentInitializationParameters, ComponentParameterDecoder, ComponentParameterKey}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.Request

/** Logical operating selection, separate from concrete executor observations.
  * Standard borrows the existing mapping. Opt-in mappings are host declarations,
  * never a model table, observed availability or permission to execute.
  * @since   Oct. 29, 2026
  * @version Nov.  2, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseOperatingProfiles {
  val standard = "standard"
  val revision = "1"
  val localExperimentName = "local-experiment"
  val optInNames = Set("cost", "quality", localExperimentName)
  val registered = optInNames + standard
  final case class Selection(identity: String, revision: String, source: String) derives Codec.AsObject
  final case class Concrete(identity: String, provider: String, model: String, controls: Map[String, String]) derives Codec.AsObject
  final case class Capture(selection: Selection, reasoningClass: Option[String], mappingIdentity: Option[String],
    mappingRevision: Option[String], configured: Option[Concrete], selected: Option[Concrete], fallback: Option[Concrete] = None,
    dispatch: Option[GoalPhaseDispatchPolicy.Capture] = None)
  object Capture {
    private val _decoder = deriveDecoder[Capture]
    given Codec.AsObject[Capture] = Codec.AsObject.from(io.circe.Decoder.instance(cursor =>
      if (cursor.downField("dispatch").focus.contains(Json.Null)) Left(io.circe.DecodingFailure("dispatch must be absent or a retained policy", cursor.history))
      else _decoder(cursor)),
      deriveEncoder[Capture].mapJsonObject(_.filter { case (_, value) => !value.isNull }))
  }
  final case class Execution(selection: Selection, profiles: Option[WorkflowExecutionProfileMapping],
    origin: Option[GoalPhaseNativeExecution.Origin])

  val declaration: ComponentParameterKey[String] = ComponentParameterKey.optional(
    "sm-workflow.goal-phase.profile", ComponentParameterDecoder {
      case ConfigurationValue.StringValue(value) => validateNameC(value).map(_ => value)
      case _ => invalid("configured profile must be a registered name")
    })
  private def _mapping(name: String): ComponentParameterKey[WorkflowExecutionProfileMapping] =
    ComponentParameterKey.optional("sm-workflow.goal-phase.profiles." + name, ComponentParameterDecoder {
      case ConfigurationValue.StringValue(value) => io.circe.parser.parse(value).fold(
        _ => invalid("profile mapping JSON is invalid"), WorkflowExecutionProfileParameters.decodeC)
      case value => WorkflowExecutionProfileParameters.decodeC(_json(value))
    })
  val cost = _mapping("cost")
  val quality = _mapping("quality")
  val localExperiment = _mapping(localExperimentName)
  val declarations: Vector[ComponentParameterKey[?]] = Vector(declaration, cost, quality, localExperiment,
    GoalPhaseDispatchPolicy.declaration, GoalPhaseNativeExecution.logicalOrigin, GoalPhaseRetainedObservations.declaration)

  def configuredC(parameters: ComponentInitializationParameters): Consequence[Option[String]] =
    if (parameters == null) invalid("Component initialization parameters are absent")
    else parameters.resolve(declaration).map(_.value)

  def mappingsC(parameters: ComponentInitializationParameters): Consequence[Map[String, WorkflowExecutionProfileMapping]] =
    if (parameters == null) invalid("Component initialization parameters are absent")
    else for {
      c <- parameters.resolve(cost).map(_.value)
      q <- parameters.resolve(quality).map(_.value)
      local <- parameters.resolve(localExperiment).map(_.value)
    } yield c.map("cost" -> _).toMap ++ q.map("quality" -> _).toMap ++
      local.map(localExperimentName -> _).toMap

  def validateNameC(value: String): Consequence[Unit] =
    if (registered.contains(value)) Consequence.unit
    else invalid("unregistered profile: " + Option(value).getOrElse("<null>"))

  def selectionValid(value: Selection): Boolean = value != null && registered(value.identity) &&
    value.revision == revision && Set("explicit", "config", "default")(value.source)

  def resolveC(explicit: Option[String], configured: Option[String]): Consequence[Selection] =
    if (explicit == null || configured == null) invalid("profile selection is absent")
    else for {
      _ <- configured.fold(Consequence.unit)(validateNameC)
      _ <- explicit.fold(Consequence.unit)(validateNameC)
      identity = explicit.orElse(configured).getOrElse(standard)
    } yield Selection(identity, revision, if (explicit.nonEmpty) "explicit" else if (configured.nonEmpty) "config" else "default")

  def invocationC(request: Request): Consequence[Option[Selection]] =
    NativeWorkflowOperationInput.optionalTextC(request, "profile").flatMap {
      case None => Consequence.success(None)
      case Some(value) => validateNameC(value).map(_ => Some(Selection(value, revision, "explicit")))
    }

  def forStartC(request: Request, d: GoalPhaseNativeRun.Declaration): Consequence[Execution] = for {
    explicit <- NativeWorkflowOperationInput.optionalTextC(request, "profile")
    selected <- resolveC(explicit, d.operatingProfile)
    execution <- executionC(selected, d)
  } yield execution

  /** A saved explicit invocation remains intent until an explicit resume
    * parameter supersedes it for new work. Config is read only for fresh work.
    */
  def forIssueC(d: GoalPhaseNativeRun.Declaration, start: Option[Selection]): Consequence[Execution] = for {
    _ <- if (start != null && start.forall(selectionValid)) Consequence.unit else invalid("saved Start profile is invalid")
    selected <- d.invocationOperatingProfile.orElse(start.filter(_.source == "explicit"))
      .map(Consequence.success).getOrElse(resolveC(None, d.operatingProfile))
    execution <- executionC(selected, d)
  } yield execution

  def executionC(selected: Selection, d: GoalPhaseNativeRun.Declaration): Consequence[Execution] = for {
    _ <- if (selectionValid(selected)) Consequence.unit else invalid("logical selection is invalid")
    _ <- GoalPhaseNativeExecution.validatePairC(d.executionProfiles, d.executionOrigin)
    _ <- if (d.logicalOrigin != null && d.logicalOrigin.forall(GoalPhaseNativeExecution.contextValid) &&
        !d.logicalOrigin.exists(value => d.executionOrigin.exists(_.current.contextIdentity != value))) Consequence.unit
      else invalid("logical origin is invalid or differs from the actual concrete origin context")
    mapping <- if (selected.identity == standard) Consequence.success(d.executionProfiles)
      else d.operatingMappings.get(selected.identity).map(value => Consequence.success(Some(value))).getOrElse(
        invalid("profile unavailable: " + selected.identity + " has no host execution mapping; no fallback selected"))
    _ <- if (selected.identity == standard || d.executionOrigin.nonEmpty) Consequence.unit
      else invalid("profile unavailable: " + selected.identity + " requires a configured execution origin")
  } yield Execution(selected, mapping, d.executionOrigin)

  /** Called only by the missing-issued-row branch. Persist the same selected
    * requirement and capture together through the normal issued-work codec.
    * Actual model/effort remains with later execution evidence, never this record.
    */
  def bindFreshC(work: WorkflowInteraction[GoalPhaseNativeWork.Request, Nothing], d: GoalPhaseNativeRun.Declaration,
    start: Option[Selection], isolateContext: Boolean = false): Consequence[WorkflowInteraction[GoalPhaseNativeWork.Request, Nothing]] = for {
    order <- Option(work).map(_.current) match {
      case Some(value: WorkflowContinuation.WorkOrder[GoalPhaseNativeWork.Request]) => Consequence.success(value)
      case _ => invalid("fresh profile capture requires a WorkOrder")
    }
    _ <- if (order.requirement == null) invalid("WorkOrder requirement is absent") else order.requirement.validateC.map(_ => ())
    input <- order.request.input.map(value => Consequence.success(value.value)).getOrElse(invalid("issued business input is absent"))
    _ <- if (input.operatingProfile.isEmpty && order.requirement.disposition.isEmpty) Consequence.unit
      else invalid("already issued work must retain its profile and disposition")
    execution <- forIssueC(d, start)
    configured <- if (d.dispatchPolicy.nonEmpty && execution.selection.identity == standard)
      Consequence.success(Option.empty[WorkflowConcreteExecutionProfile]) else execution.profiles match {
      case None => Consequence.success(Option.empty[WorkflowConcreteExecutionProfile])
      case Some(mapping) => order.requirement.reasoningClass.map(mapping.resolveClassC).getOrElse(
        invalid("profile selection requires a semantic reasoning class")).map(Some(_))
    }
    logical <- if (d.dispatchPolicy.nonEmpty && execution.selection.identity == standard)
      GoalPhaseDispatchPolicy.captureC(input.kind, GoalPhaseDispatchPolicy.classification(input), order.requirement, isolateContext, d.dispatchPolicy.get).map(Some(_))
      else Consequence.success(Option.empty[GoalPhaseDispatchPolicy.Capture])
    _ <- if (logical.exists(_.placement == "RUNTIME")) invalid("Deterministic work belongs to its existing runtime operation, not a semantic worker") else Consequence.unit
    resolved <- if (logical.nonEmpty) d.logicalOrigin.orElse(execution.origin.map(_.current.contextIdentity)).map { origin =>
      val placement = if (logical.get.placement == "INLINE") ExecutionPlacement.Inline else ExecutionPlacement.Delegated
      val independence = if (order.requirement.reviewRequired || isolateContext) ExecutionIndependence.Required else ExecutionIndependence.Optional
      Consequence.success((order.requirement.copy(disposition = Some(ExecutionDisposition(placement, independence, origin))), Option.empty[WorkflowConcreteExecutionProfile]))
    }.getOrElse(invalid("Logical dispatch requires the actual origin context")) else (execution.profiles, execution.origin) match {
      case (Some(mapping), Some(origin)) => mapping.resolveC(order.requirement, origin.current,
        if (isolateContext) origin.preference.copy(isolateContext = true) else origin.preference).flatMap { selected =>
        if (execution.selection.identity != standard && !configured.exists(_.matches(selected.profile)))
          invalid("profile unavailable: forced inline selection differs from requested mapping; no fallback selected")
        else Consequence.success((selected.requirement, Some(selected.profile)))
      }
      case _ => Consequence.success((order.requirement, Option.empty[WorkflowConcreteExecutionProfile]))
    }
    fallback <- if (logical.isEmpty && execution.selection.identity == standard &&
        resolved._1.disposition.exists(_.placement == ExecutionPlacement.Delegated)) execution.profiles match {
      case Some(mapping) => order.requirement.reasoningClass.map(mapping.fallbackClassC).getOrElse(Consequence.success(None))
      case None => Consequence.success(Option.empty[WorkflowConcreteExecutionProfile])
    } else Consequence.success(Option.empty[WorkflowConcreteExecutionProfile])
    capture = Capture(execution.selection, order.requirement.reasoningClass.map(_.value),
      if (logical.nonEmpty) Some("caller-standard") else execution.profiles.map(_.identity),
      logical.map(_.revision).orElse(execution.profiles.map(_.revision)), configured.map(_concrete), resolved._2.map(_concrete), fallback.map(_concrete), logical)
    captured = input.copy(operatingProfile = Some(capture))
  } yield work.copy(current = order.copy(requirement = resolved._1,
    request = order.request.copy(input = order.request.input.map(_.copy(value = captured)))))

  // Logical dispatch preserves optional classification; concrete mappings require it.
  def captureValid(value: Capture): Boolean = value != null && selectionValid(value.selection) &&
    value.dispatch != null && value.dispatch.forall(GoalPhaseDispatchPolicy.valid) &&
    value.reasoningClass != null && value.reasoningClass.forall(_name) && value.mappingIdentity != null &&
    value.mappingRevision != null && value.configured != null && value.selected != null &&
    value.mappingIdentity.forall(_name) && value.mappingRevision.forall(_name) &&
    value.configured.forall(_validConcrete) && value.selected.forall(_validConcrete) && value.fallback != null &&
    value.fallback.forall(_validConcrete) && (value.fallback.isEmpty || value.selection.identity == standard) &&
    (if (value.dispatch.nonEmpty) value.selection.identity == standard && value.fallback.isEmpty
     else if (value.mappingIdentity.isEmpty) value.mappingRevision.isEmpty && value.configured.isEmpty && value.selected.isEmpty && value.fallback.isEmpty &&
      value.selection.identity == standard
     else value.mappingRevision.nonEmpty && value.configured.nonEmpty && value.selected.nonEmpty && value.reasoningClass.nonEmpty)

  def admitExecutionC(capture: Option[Capture], evidence: ExecutionEvidence): Consequence[Unit] =
    capture.flatMap(_.selected) match {
      case None if capture.flatMap(_.dispatch).nonEmpty =>
        if (evidence != null && evidence.execution.exists(o => capture.get.dispatch.get.candidates.contains(o.profileIdentity))) Consequence.unit
        else invalid("execution observation is outside the retained logical candidate chain")
      case None => Consequence.unit // Historical or unconfigured standard has no invented concrete selection.
      case Some(selected) =>
        val permitted = Vector(selected) ++ capture.toVector.flatMap(_.fallback)
        if (evidence != null && permitted.exists(profile => evidence.execution.exists(_.profileIdentity == profile.identity) &&
            evidence.modelIdentity.forall(_ == profile.model))) Consequence.unit
        else invalid("execution observation differs from the retained selected profile; no fallback admitted")
    }

  /** Rebuild only the already captured route using CNCF's normal mapping. This
    * is not resolution from current config or a newly invented model assignment.
    */
  def retainedMappingC(capture: Capture): Consequence[WorkflowExecutionProfileMapping] =
    if (!captureValid(capture) || capture.configured.isEmpty) invalid("captured execution mapping is unavailable")
    else {
      val primary = capture.configured.get
      val fallback = capture.fallback.toVector
      WorkflowExecutionProfileMapping.createC(capture.mappingIdentity.get, capture.mappingRevision.get,
        (Vector(primary) ++ fallback).map(_profile),
        Vector(WorkflowExecutionProfileMapping.ClassBinding(capture.reasoningClass.get, primary.identity)),
        fallback.map(value => WorkflowExecutionProfileMapping.ClassBinding(capture.reasoningClass.get, value.identity)))
    }

  def admitCallerSessionC(capture: Capture, session: WorkflowExecutionHarness.Session): Consequence[Unit] =
    if (!captureValid(capture) || session == null || !capture.selected.exists(value =>
        value.identity == session.primary.identity && _profile(value).matches(session.primary)) ||
        !capture.mappingIdentity.contains(session.mappingIdentity) || !capture.mappingRevision.contains(session.mappingRevision) ||
        session.alternate.map(_concrete) != capture.fallback)
      invalid("caller execution session differs from the retained profile capture")
    else Consequence.unit

  private def _profile(value: Concrete): WorkflowConcreteExecutionProfile =
    WorkflowConcreteExecutionProfile(value.identity, value.provider, value.model, value.controls)
  private def _concrete(value: WorkflowConcreteExecutionProfile): Concrete =
    Concrete(value.identity, value.provider, value.model, value.controls)
  private def _validConcrete(value: Concrete): Boolean = value != null &&
    WorkflowConcreteExecutionProfile(value.identity, value.provider, value.model, value.controls).validateC.isSuccess
  private def _name(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  private def invalid[A](reason: String): Consequence[A] =
    Consequence.configurationInvalid("GoalPhase operating profile: " + reason)
  private def _json(value: ConfigurationValue): Json = value match {
    case ConfigurationValue.StringValue(v) => Json.fromString(v)
    case ConfigurationValue.NumberValue(v) => Json.fromBigDecimal(v)
    case ConfigurationValue.BooleanValue(v) => Json.fromBoolean(v)
    case ConfigurationValue.ListValue(v) => Json.fromValues(v.map(_json))
    case ConfigurationValue.ObjectValue(v) => Json.obj(v.toVector.map((k, v) => k -> _json(v))*)
    case ConfigurationValue.NullValue => Json.Null
  }
}
