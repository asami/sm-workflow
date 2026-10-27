/**
 * @since   Oct. 27, 2026
 * @version Oct. 27, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.config.{ComponentInitializationParameters, ComponentParameterDecoder, ComponentParameterKey}
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workspace.*
import org.goldenport.configuration.ConfigurationValue
import WorkflowManagedProcessExecution as E
import RepositorySyncManagedGitObservation as G

/** One bounded preparation pass. The caller persists Planned before applying it;
  * original Git blobs identify both the expected source and exact postcondition.
  * This owner neither admits product work nor changes any repair counter.
  */
private[smworkflow] object CommitHygiene {
  val autoFix = "auto-fix"
  val error = "error"
  val declaration: ComponentParameterKey[String] = ComponentParameterKey.optional(
    "sm-workflow.commit.hygiene.mode", ComponentParameterDecoder {
      case ConfigurationValue.StringValue(value) => decodeC(value)
      case _ => Consequence.configurationInvalid("sm-workflow.commit.hygiene.mode requires auto-fix or error")
    })
  def decodeC(value: String): Consequence[String] = if (Set(autoFix, error)(value)) Consequence.success(value)
    else Consequence.configurationInvalid("sm-workflow.commit.hygiene.mode requires auto-fix or error")
  def resolveC(parameters: ComponentInitializationParameters): Consequence[String] =
    parameters.resolve(declaration).map(_.value.getOrElse(autoFix))

  final case class Correction(path: String, mode: String, originalBlob: String, stagingOrdinal: Int)
  object Correction {
    given Encoder[Correction] = deriveEncoder
    given Decoder[Correction] = deriveDecoder
  }
  final case class Record(mode: String, outcome: String, findings: Vector[String], corrections: Vector[Correction], diagnostic: String,
    originalIndex: String = "")
  object Record {
    given Encoder[Record] = deriveEncoder
    given Decoder[Record] = deriveDecoder
  }
  def valid(value: Record): Boolean = value != null && Set(autoFix, error)(value.mode) &&
    Set("clean", "planned", "corrected", "diagnostic", "remaining-findings")(value.outcome) &&
    value.findings != null && value.corrections != null && value.diagnostic != null && value.corrections.size <= 256 &&
    value.corrections.map(_.path).distinct.size == value.corrections.size && value.corrections.forall(c => c != null &&
      supported(c.path) && Set("100644", "100755")(c.mode) && GitObjectId.parseC(c.originalBlob).isSuccess &&
      c.stagingOrdinal > 0 && c.stagingOrdinal <= 256) &&
    (value.outcome != "planned" || (value.mode == autoFix && value.corrections.nonEmpty && GitObjectId.parseC(value.originalIndex).isSuccess))

  def supported(path: String): Boolean = path != null && path.endsWith(".cml") &&
    !path.split("/").exists(part => Set("test", "tests", "fixtures", "expected", "golden", "snapshots")(part.toLowerCase(java.util.Locale.ROOT))) &&
    WorkspaceFilePath.parseC(path).isSuccess
  def corrected(text: String): Option[String] = Option(text).filter(t => t.endsWith("\n\n") &&
    !t.contains('\r') && !t.contains('\u0000')).flatMap { original =>
      val body = original.reverse.dropWhile(_ == '\n').reverse
      Option.when(body.nonEmpty)(body + "\n")
    }
  def partialDiagnostic(dirty: Vector[G.DirtyPath], paths: Vector[String]): Option[String] =
    Option.when(dirty.exists(d => paths.contains(d.path) && d.worktree != ' '))(
      "Commit hygiene preserves partial staging: the affected CML worktree differs from its index; typed Git cannot update only its index suffix. No hygiene write or staging was performed.")

  def planC(core: ActionCall.Core, mode: String, check: GitIndexCheck, owned: Vector[String],
    staging: Vector[String], parentTree: String, alreadyCorrected: Boolean = false): Consequence[Record] = {
    def diagnostic(message: String, outcome: String = "diagnostic") = Record(mode, outcome,
      check.findingKeys.toVector.sorted, Vector.empty, message)
    for {
      _ <- decodeC(mode)
      native <- G.inspectC(core)
      record <- if (check.findingKeys.isEmpty) Consequence.success(Record(mode, "clean", Vector.empty, Vector.empty, "Selected index is clean"))
        else if (mode == error) Consequence.success(diagnostic("Commit hygiene mode=error; findings retained without edits or commit"))
        else if (alreadyCorrected) Consequence.success(diagnostic("Commit hygiene performed one pass; remaining findings require ordinary handling", "remaining-findings"))
        else if (!check.onlyEofBlankLines || check.eofBlankLinePaths.isEmpty || !check.eofBlankLinePaths.forall(p =>
          supported(p.value) && owned.exists(owner => p.value == owner || p.value.startsWith(owner + "/")) && staging.contains(p.value)))
          Consequence.success(diagnostic("Only EOF-only findings on selected registered CML files support automatic correction"))
        else partialDiagnostic(native.observed.dirty, check.eofBlankLinePaths.toVector.map(_.value)) match {
          case Some(message) => Consequence.success(diagnostic(message))
          case None => for {
            before <- GitObjectId.parseC(parentTree)
            index <- G.indexTreeC(core).flatMap(GitObjectId.parseC)
            changes <- E.executeGitC(core, GitOperation.TreeChanges(before, index)).flatMap(_.valueC)
            planned <- check.eofBlankLinePaths.toVector.sortBy(_.value).foldLeft(Consequence.success(Vector.empty[Correction])) { (prior, path) => for {
              saved <- prior
              change <- changes.find(c => c.path == path && Set("100644", "100755")(c.newMode) && c.newBlob.nonEmpty)
                .map(Consequence.success).getOrElse(Consequence.stateConflict("EOF finding has no complete regular index blob"))
              blob <- E.executeGitC(core, GitOperation.BlobContent(change.newBlob.get)).flatMap(_.valueC)
              text <- blob.text.filter(t => t.getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector == blob.bytes && corrected(t).nonEmpty)
                .map(Consequence.success).getOrElse(Consequence.stateConflict("CML EOF correction requires complete UTF-8 LF text; original bytes retained"))
              file <- WorkspaceFilePath.parseC(path.value)
              workspace <- WorkspaceId.parseC("sm-workflow-repository")
              actual <- WorkflowFileExecution.readTextC(core, FileTarget(workspace), file)
              _ <- if (actual.text.contains(text)) Consequence.unit else Consequence.stateConflict("CML worktree differs from selected index; no hygiene write")
            } yield saved :+ Correction(path.value, change.newMode, change.newBlob.get.value, staging.indexOf(path.value) + 1) }
          } yield Record(mode, "planned", check.findingKeys.toVector.sorted, planned, "Bounded CML terminal-LF preparation", index.value)
        }
    } yield record
  }

  /** Re-entry observes either exact before or exact after. A changed third value
    * refuses; clean postconditions are staged without rewriting the file again.
    */
  def applyC(core: ActionCall.Core, record: Record): Consequence[Unit] = for {
    _ <- if (valid(record) && record.outcome == "planned") Consequence.unit
      else Consequence.stateConflict("Hygiene application has no retained bounded plan")
    indexed <- indexCorrectionsC(core, record)
    prepared <- record.corrections.foldLeft(Consequence.success(Vector.empty[(Correction, WorkspaceFilePath, String, String)])) { (prior, c) => for {
      saved <- prior
      id <- GitObjectId.parseC(c.originalBlob)
      blob <- E.executeGitC(core, GitOperation.BlobContent(id)).flatMap(_.valueC)
      original <- blob.text.filter(t => t.getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector == blob.bytes)
        .map(Consequence.success).getOrElse(Consequence.stateConflict("Original hygiene blob is not complete text"))
      next <- corrected(original).map(Consequence.success).getOrElse(Consequence.stateConflict("Retained hygiene blob is not supported"))
      path <- WorkspaceFilePath.parseC(c.path)
      workspace <- WorkspaceId.parseC("sm-workflow-repository")
      actual <- WorkflowFileExecution.readTextC(core, FileTarget(workspace), path)
      _ <- if (actual.text.contains(original) || actual.text.contains(next)) Consequence.unit
        else Consequence.stateConflict("Hygiene source changed; no overwrite or staging")
    } yield saved :+ (c, path, original, next) }
    _ <- prepared.foldLeft(Consequence.unit) { case (prior, (c, path, original, next)) => prior.flatMap { _ => for {
      workspace <- WorkspaceId.parseC("sm-workflow-repository")
      actual <- WorkflowFileExecution.readTextC(core, FileTarget(workspace), path)
      _ <- if (actual.text.contains(next)) Consequence.unit else WorkflowFileExecution.writeTextC(core, FileTarget(workspace), path,
        next, FileWriteMode.UpdateExisting(original)).flatMap { written => if (written.outcome == FileWriteOutcome.Updated)
          Consequence.unit else Consequence.stateConflict("Hygiene replacement conflicted; no commit") }
      _ <- if (indexed.contains(c.path)) Consequence.unit else E.stageGoalCandidateC(core, c.stagingOrdinal).flatMap(_.valueC)
    } yield () } }
    after <- indexCorrectionsC(core, record)
    _ <- if (after == record.corrections.map(_.path).toSet) Consequence.unit
      else Consequence.stateConflict("Hygiene staging did not establish every exact selected postcondition")
  } yield ()

  private def indexCorrectionsC(core: ActionCall.Core, record: Record): Consequence[Set[String]] = for {
    original <- GitObjectId.parseC(record.originalIndex)
    current <- G.indexTreeC(core).flatMap(GitObjectId.parseC)
    changes <- E.executeGitC(core, GitOperation.TreeChanges(original, current)).flatMap(_.valueC)
    _ <- changes.foldLeft(Consequence.unit) { (prior, change) => prior.flatMap { _ => for {
      correction <- record.corrections.find(c => c.path == change.path.value && change.oldBlob.exists(_.value == c.originalBlob) &&
        change.oldMode == c.mode && change.newMode == c.mode && change.status == "M" && change.newBlob.nonEmpty)
        .map(Consequence.success).getOrElse(Consequence.stateConflict("Hygiene index changed outside its exact planned suffix corrections"))
      before <- E.executeGitC(core, GitOperation.BlobContent(change.oldBlob.get)).flatMap(_.valueC)
      after <- E.executeGitC(core, GitOperation.BlobContent(change.newBlob.get)).flatMap(_.valueC)
      _ <- if (before.text.flatMap(corrected).exists(text => after.bytes == text.getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector))
        Consequence.unit else Consequence.stateConflict("Hygiene index postcondition contains other bytes")
    } yield () } }
  } yield changes.map(_.path.value).toSet
}
