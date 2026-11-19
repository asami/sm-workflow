package org.simplemodeling.textus.smworkflow.admission

import com.typesafe.config.Config
import org.goldenport.Consequence
import org.goldenport.cncf.config.ResolvedParameters
import org.goldenport.configuration.ConfigurationValue
import org.simplemodeling.scalalint.ScalaLintProfiles

/** Per-call source selection consumes CNCF's resolved values; it does not execute lint.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object WorkflowScalaLintParameters {
  val sourceKey = ScalaLintProfiles.sourceKey

  def selectionC(parameters: ResolvedParameters): Consequence[ScalaLintProfiles.Selection] = for {
    _ <- if (parameters != null) Consequence.unit else _invalid("resolved parameters are required")
    _ <- scala.util.Try(ScalaLintProfiles.rejectObsoleteHistoryKeys(key => parameters.get(key).nonEmpty)).fold(
      error => _invalid(error.getMessage), _ => Consequence.unit)
    source <- _string_c(parameters, sourceKey)
    history <- _string_c(parameters, ScalaLintProfiles.historyKey)
    selected <- scala.util.Try(ScalaLintProfiles.selection(source, history)).fold(
      error => _invalid(error.getMessage), Consequence.success)
  } yield selected

  /** Caller supplies project configuration only after its ordinary authorized read.
    * The resulting data still needs the native SLP-4 validation binding/retention.
    */
  def resolveC(parameters: ResolvedParameters, project: Option[Config]): Consequence[ScalaLintProfiles.Effective] =
    selectionC(parameters).flatMap(selected => scala.util.Try(ScalaLintProfiles.resolve(selected, project,
      ScalaLintProfiles.getClass.getClassLoader)).fold(error => _invalid(error.getMessage), Consequence.success))

  private def _string_c(parameters: ResolvedParameters, key: String): Consequence[Option[String]] =
    parameters.get(key).map(_.value) match {
      case None => Consequence.success(None)
      case Some(ConfigurationValue.StringValue(value)) if value != null => Consequence.success(Some(value))
      case _ => _invalid(key + " must be an exact string style")
    }

  private def _invalid[A](message: String): Consequence[A] =
    Consequence.configurationInvalid("Scala lint parameters: " + message)
}
