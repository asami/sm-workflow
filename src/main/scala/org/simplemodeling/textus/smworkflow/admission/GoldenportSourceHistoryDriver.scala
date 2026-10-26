package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workspace.*

/** External history convention composed with the owning call's current File UoW.
  * The caller supplies provenance and owns any durable intent and continuation.
  * @since   Oct. 17, 2026
  * @version Oct. 26, 2026
  */
private[smworkflow] object GoldenportSourceHistoryDriver {
  import WorkflowSourceHistory.{Source, Result}

  final case class Normalization(outcome: String, diagnostic: Option[String])

  /** Read/interpret failures are known to precede writes. Only a dispatched
    * replacement failure can remain unknown to its durable intent owner. */
  def normalizeC(core: ActionCall.Core, target: FileTarget,
    rule: WorkflowSourceHistoryRule.Rule, source: Source): Consequence[Normalization] = {
    val prepared = for {
      _ <- if (core != null && core.executionContext != null && target != null && source != null && source.updatedOn != null)
        Consequence.unit else Consequence.stateConflict("Normalization has no owning File call or original date")
      included <- WorkflowSourceHistoryRule.includesC(rule, source.path)
      _ <- if (included) Consequence.unit else Consequence.stateConflict("Normalization path is no longer admitted by its retained rule")
      path <- WorkspaceFilePath.parseC(source.path)
      read <- WorkflowFileExecution.readTextC(core, target, path)
      text <- read.text.map(Consequence.success).getOrElse(Consequence.stateConflict("Normalization source is missing"))
      interpreted <- WorkflowSourceHistoryRule.applyC(rule, WorkflowSourceHistoryRule.Input(
        source.path, text, source.updatedOn, source.createdOn, source.author, source.dateUncertain))
      _ <- if (interpreted.providerIdentity == rule.identity && interpreted.dateUncertain == source.dateUncertain &&
          Set("updated", "unchanged")(interpreted.outcome)) Consequence.unit
        else Consequence.stateConflict("Normalization interpretation crossed its retained rule or date")
    } yield (path, text, interpreted)
    prepared.map[Consequence[Normalization]] { (path, original, interpreted) => interpreted.outcome match {
      case "unchanged" => Consequence.success(Normalization("observed-current-postcondition", None))
      case "updated" => WorkflowFileExecution.writeTextC(core, target, path, interpreted.text,
        FileWriteMode.UpdateExisting(original)).flatMap { written => written.outcome match {
          case FileWriteOutcome.Updated => Consequence.success(Normalization("updated-current", None))
          case FileWriteOutcome.Conflict(_) => Consequence.success(Normalization("failed", Some("Normalization source changed before managed replacement")))
          case _ => Consequence.stateConflict("Normalization replacement has no attributable supported result; its writer outcome remains unknown")
        }}
      case _ => Consequence.success(Normalization("failed", Some("Normalization interpretation has no supported outcome")))
    }}.fold(error => Consequence.success(Normalization("failed", Some(error.toString.take(8192)))), identity)
  }

  def verifyNormalizedC(core: ActionCall.Core, target: FileTarget,
    rule: WorkflowSourceHistoryRule.Rule, source: Source): Consequence[Unit] = for {
    path <- WorkspaceFilePath.parseC(source.path)
    read <- WorkflowFileExecution.readTextC(core, target, path)
    text <- read.text.map(Consequence.success).getOrElse(Consequence.stateConflict("Reconciled source is now missing"))
    interpreted <- WorkflowSourceHistoryRule.applyC(rule, WorkflowSourceHistoryRule.Input(
      source.path, text, source.updatedOn, source.createdOn, source.author, source.dateUncertain))
    _ <- if (interpreted.outcome == "unchanged" && interpreted.text == text && interpreted.providerIdentity == rule.identity &&
        interpreted.dateUncertain == source.dateUncertain) Consequence.unit
      else Consequence.stateConflict("Reconciled source no longer satisfies its retained rule and original date")
  } yield ()

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
