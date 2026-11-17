/**
 * @since   Nov. 17, 2026
 * @version Nov. 17, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.goldenport.Consequence
import org.goldenport.cncf.config.{ComponentInitializationParameters, ComponentParameterDecoder, ComponentParameterKey}
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.record.Record

/** Ordinary configuration selection is captured once by the owning Session.
  * Absence on historical Starts retains historical single-checkout semantics.
  */
private[smworkflow] object RepositorySyncPolicies {
  val mainValues = Set("local-first", "direct", "confirm")
  val dirtyValues = Set("auto-commit", "confirm", "error")
  final case class Selection(mainIntegration: String, uncommittedPolicy: String)
  object Selection {
    given Encoder[Selection] = deriveEncoder
    private val raw: Decoder[Selection] = deriveDecoder
    given Decoder[Selection] = Decoder.instance(c => raw(c).flatMap(value =>
      if (valid(value)) Right(value) else Left(io.circe.DecodingFailure("Invalid retained RepositorySync policies", c.history))))
  }
  def valid(value: Selection): Boolean = value != null && mainValues(value.mainIntegration) && dirtyValues(value.uncommittedPolicy)
  def decodeC(name: String, value: Any, allowed: Set[String]): Consequence[String] = value match {
    case text: String if allowed(text) => Consequence.success(text)
    case _ => Consequence.configurationInvalid(name + " requires one of " + allowed.toVector.sorted.mkString(", "))
  }
  private def key(name: String, allowed: Set[String]) = ComponentParameterKey.optional(
    "sm-workflow.repository-sync." + name, ComponentParameterDecoder[String] {
      case ConfigurationValue.StringValue(value) => decodeC(name, value, allowed)
      case _ => Consequence.configurationInvalid(name + " requires a String")
    })
  val mainIntegration = key("main-integration", mainValues)
  val uncommittedPolicy = key("uncommitted-policy", dirtyValues)
  def selectC(input: Record, configuredMain: Option[String], configuredDirty: Option[String]): Consequence[Selection] = for {
    _ <- if (input != null && !Vector("mainIntegration", "uncommittedPolicy").exists(name =>
        DevelopmentOperationalView.json(input).hcursor.downField(name).focus.exists(_.isNull))) Consequence.unit
      else Consequence.configurationInvalid("RepositorySync policy selections cannot be null")
    main <- input.getAny("mainIntegration").map(decodeC("mainIntegration", _, mainValues))
      .getOrElse(decodeC("main-integration", configuredMain.getOrElse("local-first"), mainValues))
    dirty <- input.getAny("uncommittedPolicy").map(decodeC("uncommittedPolicy", _, dirtyValues))
      .getOrElse(decodeC("uncommitted-policy", configuredDirty.getOrElse("auto-commit"), dirtyValues))
  } yield Selection(main, dirty)
  def resolveC(parameters: ComponentInitializationParameters, input: Record): Consequence[Selection] = for {
    main <- parameters.resolve(mainIntegration).map(_.value)
    dirty <- parameters.resolve(uncommittedPolicy).map(_.value)
    selected <- selectC(input, main, dirty)
  } yield selected

  /** A remote non-main target keeps its own meaning. A request already on main
    * has one remote hop, so confirm has no distinct route effect to approve.
    */
  def route(selection: Selection, localBranch: String, remoteBranch: String): String =
    if (remoteBranch != "main" || localBranch == "main") "direct" else selection.mainIntegration
}
