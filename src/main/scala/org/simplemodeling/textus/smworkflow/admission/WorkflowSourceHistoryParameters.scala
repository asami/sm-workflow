package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.config.ResolvedParameters
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.configuration.ConfigurationValue

/** Per-call resolved configuration for the history-comment driver internals.
  * @since   Oct. 17, 2026
  * @version Oct. 17, 2026
  */
private[smworkflow] object WorkflowSourceHistoryParameters {
  val styleKey = "sm-workflow.source-history.style"
  val ruleKey = "sm-workflow.source-history.rule"

  def resolveC(context: ExecutionContext): Consequence[WorkflowSourceHistoryRule.Selection] =
    if (context == null) _invalid("execution context is required")
    else resolveC(context.resolvedParameters)

  def resolveC(parameters: ResolvedParameters): Consequence[WorkflowSourceHistoryRule.Selection] = {
    if (parameters == null) _invalid("resolved parameters are required")
    else parameters.get(styleKey).map(_.value) match {
      case None | Some(ConfigurationValue.StringValue("none")) =>
        WorkflowSourceHistoryRule.selectionC("none", None)
      case Some(ConfigurationValue.StringValue("goldenport")) =>
        parameters.get(ruleKey).map(_.value) match {
          case Some(ConfigurationValue.StringValue(raw)) =>
            WorkflowSourceHistoryRule.decodeC(raw).flatMap(rule => WorkflowSourceHistoryRule.selectionC("goldenport", Some(rule)))
          case Some(value: ConfigurationValue.ObjectValue) =>
            _json(value).fold(_invalid, value => WorkflowSourceHistoryRule.decodeC(value)
              .flatMap(rule => WorkflowSourceHistoryRule.selectionC("goldenport", Some(rule))))
          case _ => _invalid("selected goldenport style requires an object or JSON object rule")
        }
      case _ => _invalid("style must be the exact string none or goldenport")
    }
  }

  private def _json(value: ConfigurationValue): Either[String, Json] = value match {
    case ConfigurationValue.StringValue(v) if v != null => Right(Json.fromString(v))
    case ConfigurationValue.NumberValue(v) if v != null => Right(Json.fromBigDecimal(v))
    case ConfigurationValue.BooleanValue(v) => Right(Json.fromBoolean(v))
    case ConfigurationValue.NullValue => Right(Json.Null)
    case ConfigurationValue.ListValue(vs) if vs != null =>
      vs.foldLeft[Either[String, Vector[Json]]](Right(Vector.empty)) { (prior, next) =>
        for { values <- prior; value <- _json(next) } yield values :+ value
      }.map(Json.fromValues)
    case ConfigurationValue.ObjectValue(vs) if vs != null =>
      vs.toVector.foldLeft[Either[String, Vector[(String, Json)]]](Right(Vector.empty)) {
        case (prior, (key, next)) => for {
          values <- prior
          _ <- Either.cond(key != null, (), "invalid rule object key")
          value <- _json(next)
        } yield values :+ (key -> value)
      }.map(Json.fromFields)
    case _ => Left("invalid resolved rule data")
  }

  private def _invalid[A](message: String): Consequence[A] =
    Consequence.configurationInvalid("Source history parameters: " + message)
}
