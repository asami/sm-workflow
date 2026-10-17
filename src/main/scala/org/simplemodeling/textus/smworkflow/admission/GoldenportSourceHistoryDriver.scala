package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workspace.*

/** External history convention composed with the owning call's current File UoW.
  * The caller supplies provenance and owns any durable intent and continuation.
  * @since   Oct. 17, 2026
  * @version Oct. 17, 2026
  */
private[smworkflow] object GoldenportSourceHistoryDriver {
  import WorkflowSourceHistory.{Source, Result}

  def applyC(core: ActionCall.Core, target: FileTarget,
    rule: WorkflowSourceHistoryRule.Rule, source: Source): Consequence[Result] =
    if (core == null || core.executionContext == null || target == null || source == null)
      Consequence.stateConflict("Goldenport source history requires an owning call, target and source")
    else WorkflowSourceHistoryRule.includesC(rule, source.path).flatMap {
      case false => Consequence.success(Result("goldenport", "excluded", Some(rule.identity), source.dateUncertain))
      case true => for {
        path <- WorkspaceFilePath.parseC(source.path)
        read <- WorkflowFileExecution.readTextC(core, target, path)
        result <- read.text match {
          case None => Consequence.success(Result("goldenport", "missing", Some(rule.identity), source.dateUncertain))
          case Some(text) => WorkflowSourceHistoryRule.applyC(rule, WorkflowSourceHistoryRule.Input(
            source.path, text, source.updatedOn, source.createdOn, source.author, source.dateUncertain)).flatMap { interpreted =>
            interpreted.outcome match {
              case "unchanged" => Consequence.success(_result_of(interpreted))
              case "updated" => WorkflowFileExecution.writeTextC(core, target, path, interpreted.text,
                FileWriteMode.UpdateExisting(text)).flatMap { written => written.outcome match {
                  case FileWriteOutcome.Updated => Consequence.success(_result_of(interpreted))
                  case FileWriteOutcome.Conflict(_) =>
                    Consequence.stateConflict("Goldenport source history source changed before installation")
                  case _ => Consequence.stateConflict("Goldenport source history unexpected File update outcome")
                }}
              case _ => Consequence.stateConflict("Goldenport source history unexpected interpretation outcome")
            }
          }
        }
      } yield result
    }

  private def _result_of(value: WorkflowSourceHistoryRule.Result): Result =
    Result("goldenport", value.outcome, Some(value.providerIdentity), value.dateUncertain)
}
