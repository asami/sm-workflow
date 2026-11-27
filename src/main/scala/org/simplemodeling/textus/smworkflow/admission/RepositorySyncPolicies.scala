/**
 * @since   Nov. 17, 2026
 * @version Nov. 28, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.goldenport.Consequence
import org.goldenport.cncf.config.{ComponentInitializationParameters, ComponentParameterDecoder, ComponentParameterKey}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.protocol.Property
import org.goldenport.record.{Record, RecordPresentable}

/** Ordinary configuration selection is captured once by the owning Session.
  * Absence on historical Starts retains historical single-checkout semantics.
  */
private[smworkflow] object RepositorySyncPolicies {
  val mainValues = Set("local-first", "direct", "confirm")
  val dirtyValues = Set("auto-commit", "confirm", "error")
  final case class Selection(mainIntegration: String, uncommittedPolicy: String, fixCheck: Option[Boolean] = None) {
    def fixCheckEnabled: Boolean = fixCheck.getOrElse(true)
  }
  object Selection {
    given Encoder[Selection] = deriveEncoder[Selection].mapJson(_.mapObject(_.filter {
      case ("fixCheck", value) => !value.isNull
      case _ => true
    }))
    private val raw: Decoder[Selection] = deriveDecoder
    given Decoder[Selection] = Decoder.instance(c => raw(c).flatMap(value =>
      if (valid(value)) Right(value) else Left(io.circe.DecodingFailure("Invalid retained RepositorySync policies", c.history))))
  }
  def valid(value: Selection): Boolean =
    value != null && mainValues(value.mainIntegration) && dirtyValues(value.uncommittedPolicy) && value.fixCheck != null
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
  val fixCheck = ComponentParameterKey.optional("sm-workflow.repository-sync.fix-check",
    ComponentParameterDecoder[Boolean] {
      case ConfigurationValue.BooleanValue(value) => Consequence.success(value)
      case _ => Consequence.configurationInvalid("RepositorySync FixCheck requires a Boolean host setting")
    })
  private final case class BatchSelection(selection: Selection) extends RecordPresentable {
    def toRecord(): Record = Record.data("mainIntegration" -> selection.mainIntegration,
      "uncommittedPolicy" -> selection.uncommittedPolicy, "fixCheck" -> selection.fixCheckEnabled)
  }
  private val _batch_key = "sm-workflow.repository-sync.batch-policies"
  private[admission] def batchProperty(selection: Selection): Property =
    Property(_batch_key, BatchSelection(selection), None)
  private[admission] def batchSelection(core: ActionCall.Core): Option[Selection] =
    core.action.request.properties.find(_.name == _batch_key).flatMap(_.value match {
      case value: BatchSelection => Some(value.selection)
      case _ => None
    })
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
    lint <- parameters.resolve(fixCheck).map(_.value)
    selected <- selectC(input, main, dirty)
  } yield selected.copy(fixCheck = lint)

  /** A remote non-main target keeps its own meaning. A request already on main
    * has one remote hop, so confirm has no distinct route effect to approve.
    */
  def route(selection: Selection, localBranch: String, remoteBranch: String): String =
    if (remoteBranch != "main" || localBranch == "main") "direct" else selection.mainIntegration
}
