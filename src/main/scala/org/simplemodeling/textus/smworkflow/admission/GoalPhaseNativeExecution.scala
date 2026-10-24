package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.config.{ComponentInitializationParameters, ComponentParameterDecoder, ComponentParameterKey}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*

/** Trusted operational input for fresh native Goal issuance. Configuration is
  * neither an executor observation nor permission to execute or close work.
  * @since   Oct. 14, 2026
  * @version Oct. 24, 2026
  */
private[smworkflow] object GoalPhaseNativeExecution {
  final case class Origin(current: WorkflowExecutorDescriptor, preference: WorkflowExecutionPreference)
  val schemaVersion = "sm-workflow.execution-origin.v1"
  val declaration: ComponentParameterKey[Origin] = ComponentParameterKey.optional(
    "sm-workflow.execution.origin", ComponentParameterDecoder {
      case value: ConfigurationValue.ObjectValue => decodeC(_json(value))
      case ConfigurationValue.StringValue(value) => io.circe.parser.parse(value)
        .fold(_ => invalid("origin JSON is invalid"), decodeC)
      case _ => invalid("origin requires an explicit structured definition")
    })

  def decodeC(value: Json): Consequence[Origin] = {
    val decoded = for {
      root <- Option(value).toRight("origin is absent")
      schema <- root.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(schema == schemaVersion, (), "origin schema differs")
      context <- root.hcursor.downField("current").get[String]("contextIdentity").left.map(_.message)
      profile = root.hcursor.downField("current").downField("profile")
      identity <- profile.get[String]("identity").left.map(_.message)
      provider <- profile.get[String]("provider").left.map(_.message)
      model <- profile.get[String]("model").left.map(_.message)
      controls <- profile.get[Map[String, String]]("controls").left.map(_.message)
      forceinline <- root.hcursor.downField("preference").get[Boolean]("forceInline").left.map(_.message)
      isolate <- root.hcursor.downField("preference").get[Boolean]("isolateContext").left.map(_.message)
    } yield Origin(WorkflowExecutorDescriptor(context,
      WorkflowConcreteExecutionProfile(identity, provider, model, controls)), WorkflowExecutionPreference(forceinline, isolate))
    decoded.fold(invalid, origin => _validate_origin_c(origin).map(_ => origin))
  }

  def resolveOriginC(parameters: ComponentInitializationParameters): Consequence[Option[Origin]] =
    if (parameters == null) invalid("Component initialization parameters are absent")
    else parameters.resolve(declaration).map(_.value)

  def validatePairC(mapping: Option[WorkflowExecutionProfileMapping], origin: Option[Origin]): Consequence[Unit] =
    if (mapping == null || origin == null || mapping.exists(_ == null) || origin.exists(_ == null))
      invalid("profile/origin configuration is missing")
    else (mapping, origin) match {
      case (None, None) => Consequence.unit
      case (Some(_), Some(value)) => _validate_origin_c(value)
      case _ => invalid("mapped execution requires both profiles and origin")
    }

  /** Only the owning missing-issued-row branch calls this adapter. CNCF owns
    * resolution and the stored WorkOrder owns its selected disposition thereafter.
    */
  def bindIssuedC[W](work: WorkflowInteraction[W, Nothing], mapping: Option[WorkflowExecutionProfileMapping],
    origin: Option[Origin], isolateContext: Boolean = false): Consequence[WorkflowInteraction[W, Nothing]] = for {
    _ <- validatePairC(mapping, origin)
    order <- Option(work).map(_.current) match {
      case Some(value: WorkflowContinuation.WorkOrder[W]) => Consequence.success(value)
      case _ => invalid("fresh execution requires an actual WorkOrder")
    }
    _ <- if (order.requirement == null) invalid("WorkOrder requirement is absent") else order.requirement.validateC.map(_ => ())
    selected <- (mapping, origin) match {
      case (Some(profiles), Some(current)) => profiles.resolveC(order.requirement, current.current,
        if (isolateContext) current.preference.copy(isolateContext = true) else current.preference)
        .map(resolved => work.copy(current = order.copy(requirement = resolved.requirement)))
      case _ => Consequence.success(work)
    }
  } yield selected

  private def _validate_origin_c(origin: Origin): Consequence[Unit] =
    if (origin == null || origin.current == null || origin.preference == null) invalid("current descriptor/preference is absent")
    else origin.current.validateC.map(_ => ())
  private def invalid[A](reason: String): Consequence[A] = Consequence.configurationInvalid("GoalPhase execution: " + reason)
  private def _json(value: ConfigurationValue): Json = value match {
    case ConfigurationValue.StringValue(v) => Json.fromString(v)
    case ConfigurationValue.NumberValue(v) => Json.fromBigDecimal(v)
    case ConfigurationValue.BooleanValue(v) => Json.fromBoolean(v)
    case ConfigurationValue.ListValue(v) => Json.fromValues(v.map(_json))
    case ConfigurationValue.ObjectValue(v) => Json.obj(v.toVector.map((k, v) => k -> _json(v))*)
    case ConfigurationValue.NullValue => Json.Null
  }
}
