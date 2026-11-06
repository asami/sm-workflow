package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.{Charset, StandardCharsets}
import scala.util.Try
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.config.{ComponentInitializationParameters, ComponentParameterDecoder, ComponentParameterKey}
import org.goldenport.cncf.resource.{ResourceReadLimits, ResourceReference}
import org.goldenport.configuration.ConfigurationValue

/** Workflow input acquisition policy; the executing Resource owner retains all
  * provider, authorization, decoding and observation responsibilities.
  * @since   Nov.  5, 2026
  * @version Nov.  6, 2026
  */
private[smworkflow] object WorkflowArtifactBudget {
  val defaultMaximumBytes: Int = 67108864
  val maxBytes: ComponentParameterKey[ResourceReadLimits] = ComponentParameterKey.optional(
    "workflow.artifact.max-bytes", ComponentParameterDecoder(decodeC))

  def decodeC(value: ConfigurationValue): Consequence[ResourceReadLimits] = {
    val number = value match {
      case ConfigurationValue.StringValue(text) if text != null && text.matches("[1-9][0-9]*") =>
        Try(BigInt(text)).toOption.filter(_.isValidInt).map(_.toInt)
      case ConfigurationValue.NumberValue(number) if number.isWhole && number.isValidInt => Some(number.toInt)
      case _ => None
    }
    number match {
      case Some(bytes) => ResourceReadLimits.createC(bytes) match {
        case Consequence.Success(limits) => Consequence.success(limits)
        case _ => _invalid
      }
      case None => _invalid
    }
  }

  def resolveC(parameters: ComponentInitializationParameters): Consequence[ResourceReadLimits] =
    parameters.resolve(maxBytes).map(_.value.getOrElse(ResourceReadLimits(defaultMaximumBytes)))

  /** Only operations classifying their input as a Workflow artifact call here.
    * This does not replace generic Resource or independent capture budgets.
    */
  def readTextC(core: ActionCall.Core, reference: ResourceReference,
    charset: Option[Charset] = Some(StandardCharsets.UTF_8)): Consequence[String] = for {
    limits <- core.component match {
      case Some(component) => resolveC(component.initializationParameters)
      case None => Consequence.success(ResourceReadLimits(defaultMaximumBytes))
    }
    text <- core.executionContext.readResourceText(reference, limits, charset)
  } yield text

  private def _invalid: Consequence[ResourceReadLimits] =
    Consequence.configurationInvalid("workflow.artifact.max-bytes requires an integer supported by CNCF ResourceReadLimits (1..268435456 bytes)")
}
