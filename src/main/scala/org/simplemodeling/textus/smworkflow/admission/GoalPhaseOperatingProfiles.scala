package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.config.{ComponentInitializationParameters, ComponentParameterDecoder, ComponentParameterKey}
import org.goldenport.cncf.workflow.WorkflowExecutionProfileMapping
import org.goldenport.protocol.Request

/** Logical operating selection, separate from concrete executor observations.
  * Standard borrows the existing execution mapping and origin without overrides.
  * Resolution here creates no saved work, permissions or execution evidence.
  * @since   Oct. 29, 2026
  * @version Oct. 29, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseOperatingProfiles {
  val standard = "standard"
  val revision = "1"
  final case class Selection(identity: String, revision: String, source: String)
  final case class Execution(selection: Selection, profiles: Option[WorkflowExecutionProfileMapping],
    origin: Option[GoalPhaseNativeExecution.Origin])

  val declaration: ComponentParameterKey[String] = ComponentParameterKey.optional(
    "sm-workflow.goal-phase.profile", ComponentParameterDecoder {
      case ConfigurationValue.StringValue(value) => validateNameC(value).map(_ => value)
      case _ => invalid("configured profile must be a registered name")
    })

  def configuredC(parameters: ComponentInitializationParameters): Consequence[Option[String]] =
    if (parameters == null) invalid("Component initialization parameters are absent")
    else parameters.resolve(declaration).map(_.value)

  def validateNameC(value: String): Consequence[Unit] =
    if (value == standard) Consequence.unit
    else invalid("unregistered profile: " + Option(value).getOrElse("<null>"))

  def resolveC(explicit: Option[String], configured: Option[String]): Consequence[Selection] =
    if (explicit == null || configured == null) invalid("profile selection is absent")
    else for {
      _ <- configured.fold(Consequence.unit)(validateNameC)
      _ <- explicit.fold(Consequence.unit)(validateNameC)
      identity = explicit.orElse(configured).getOrElse(standard)
    } yield Selection(identity, revision, if (explicit.nonEmpty) "explicit" else if (configured.nonEmpty) "config" else "default")

  /** The public generated Operation owns the optional profile parameter; the
    * unchanged GoalPhaseStartInput remains the exact business admission target.
    * M2 can capture this resolved selection at fresh work issuance.
    */
  def forStartC(request: Request, d: GoalPhaseNativeRun.Declaration): Consequence[Execution] = for {
    explicit <- NativeWorkflowOperationInput.optionalTextC(request, "profile")
    selected <- resolveC(explicit, d.operatingProfile)
    _ <- GoalPhaseNativeExecution.validatePairC(d.executionProfiles, d.executionOrigin)
  } yield Execution(selected, d.executionProfiles, d.executionOrigin)

  private def invalid[A](reason: String): Consequence[A] =
    Consequence.configurationInvalid("GoalPhase operating profile: " + reason)
}
