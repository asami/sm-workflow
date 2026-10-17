package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.config.{ComponentInitializationParameters, ComponentParameterDecoder, ComponentParameterKey}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import GoalPhaseWorkClassification.{Classification, Kind}

/** Explicit semantic input for the three fresh Split work boundaries.
  * CNCF owns concrete execution selection; configuration grants no authority.
  * @since   Oct. 16, 2026
  * @version Oct. 16, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object SplitPhaseNativeExecution {
  val schemaVersion = "sm-workflow.split-execution-work.v1"
  enum Work { case Partition, Scope, Reasoning }
  final case class Classifications(partition: Classification, scope: Classification, reasoning: Classification) {
    def validateC: Consequence[Unit] =
      if (partition == null || scope == null || reasoning == null ||
          partition.kind != Kind.DESIGN || scope.kind != Kind.DESIGN || reasoning.kind != Kind.ANALYSIS)
        _invalid("requires explicit DESIGN, DESIGN and ANALYSIS classifications")
      else for { _ <- partition.validateC; _ <- scope.validateC; _ <- reasoning.validateC } yield ()
  }
  val declaration: ComponentParameterKey[Classifications] = ComponentParameterKey.optional(
    "sm-workflow.execution.split-work", ComponentParameterDecoder {
      case value: ConfigurationValue.ObjectValue => decodeC(_json(value))
      case ConfigurationValue.StringValue(value) => io.circe.parser.parse(value)
        .fold(_ => _invalid("JSON is invalid"), decodeC)
      case _ => _invalid("requires an explicit structured definition")
    })

  def decodeC(value: Json): Consequence[Classifications] = {
    val decoded = for {
      root <- Option(value).toRight("definition is absent")
      schema <- root.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(schema == schemaVersion, (), "schema differs")
      partition <- root.hcursor.get[Classification]("partition").left.map(_.message)
      scope <- root.hcursor.get[Classification]("scope").left.map(_.message)
      reasoning <- root.hcursor.get[Classification]("reasoning").left.map(_.message)
    } yield Classifications(partition, scope, reasoning)
    decoded.fold(_invalid, value => value.validateC.map(_ => value))
  }

  def resolveC(parameters: ComponentInitializationParameters): Consequence[Option[Classifications]] =
    if (parameters == null) _invalid("Component initialization parameters are absent")
    else parameters.resolve(declaration).map(_.value)

  def validateC(classifications: Option[Classifications], profiles: Option[WorkflowExecutionProfileMapping],
    origin: Option[GoalPhaseNativeExecution.Origin]): Consequence[Unit] =
    if (classifications == null || classifications.exists(_ == null)) _invalid("classifications are absent")
    else for {
      _ <- classifications.map(_.validateC).getOrElse(Consequence.unit)
      _ <- GoalPhaseNativeExecution.validatePairC(profiles, origin)
    } yield ()

  /** Called only before suspension in the owning missing-issued-row branch. */
  def bindIssuedC[W](work: WorkflowInteraction[W, Nothing], kind: Work,
    classifications: Option[Classifications], profiles: Option[WorkflowExecutionProfileMapping],
    origin: Option[GoalPhaseNativeExecution.Origin]): Consequence[WorkflowInteraction[W, Nothing]] = for {
    _ <- validateC(classifications, profiles, origin)
    _ <- if (kind == null) _invalid("work kind is absent") else Consequence.unit
    order <- Option(work).map(_.current) match {
      case Some(value: WorkflowContinuation.WorkOrder[W]) => Consequence.success(value)
      case _ => _invalid("fresh execution requires an actual WorkOrder")
    }
    base <- if (order.requirement == null) _invalid("requirement is absent") else order.requirement.validateC
    _ <- if (base.disposition.nonEmpty) _invalid("selected work cannot be rebound") else Consequence.unit
    requirement <- classifications match {
      case Some(value) => (kind match {
        case Work.Partition => value.partition
        case Work.Scope => value.scope
        case Work.Reasoning => value.reasoning
      }).semanticRequirementC(base)
      case None if profiles.nonEmpty => _invalid("mapped fresh work requires explicit split classifications")
      case None => Consequence.success(base)
    }
    selected <- GoalPhaseNativeExecution.bindIssuedC(work.copy(current = order.copy(requirement = requirement)), profiles, origin)
  } yield selected

  private def _json(value: ConfigurationValue): Json = value match {
    case ConfigurationValue.StringValue(v) => Json.fromString(v)
    case ConfigurationValue.NumberValue(v) => Json.fromBigDecimal(v)
    case ConfigurationValue.BooleanValue(v) => Json.fromBoolean(v)
    case ConfigurationValue.ListValue(v) => Json.fromValues(v.map(_json))
    case ConfigurationValue.ObjectValue(v) => Json.obj(v.toVector.map((k, v) => k -> _json(v))*)
    case ConfigurationValue.NullValue => Json.Null
  }
  private def _invalid[A](reason: String): Consequence[A] =
    Consequence.configurationInvalid("Split execution work " + reason)
}
