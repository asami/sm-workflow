package org.simplemodeling.textus.smworkflow.admission

import java.time.LocalDate
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workspace.FileTarget

/** Per-call source-history selection using CNCF's public resolved configuration.
  * Results describe effects only; they grant no acceptance or commit authority.
  * @since   Oct. 17, 2026
  * @version Oct. 17, 2026
  */
private[smworkflow] object WorkflowSourceHistory {
  final case class Source(path: String, updatedOn: LocalDate, createdOn: Option[LocalDate],
    author: Option[String], dateUncertain: Boolean)
  final case class Result(style: String, outcome: String, providerIdentity: Option[String], dateUncertain: Boolean)

  def applyC(core: ActionCall.Core, target: FileTarget, source: Source): Consequence[Result] =
    if (core == null || core.executionContext == null)
      Consequence.stateConflict("Source history requires its actual owning call")
    else WorkflowSourceHistoryParameters.resolveC(core.executionContext).flatMap { selection =>
      selection.style match {
        case "none" => Consequence.success(Result("none", "disabled", None, false))
        case "goldenport" => selection.rule match {
          case Some(rule) => GoldenportSourceHistoryDriver.applyC(core, target, rule, source)
          case _ => _invalid("selected goldenport rule is absent")
        }
        case _ => _invalid("unsupported selected style")
      }
    }

  private def _invalid[A](message: String): Consequence[A] =
    Consequence.configurationInvalid("Source history: " + message)
}
