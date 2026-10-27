/**
 * @since   Oct. 27, 2026
 * @version Oct. 27, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.config.{ComponentParameterDecoder, ComponentParameterKey}
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workspace.*
import org.goldenport.configuration.ConfigurationValue
import RepositorySyncManagedGitObservation as G
import WorkflowManagedProcessExecution as E

/** Exact configured destinations and evidence-backed clerical projection. Plans
  * live in the owning native checkpoint, never in a separate closure ledger.
  * Callers persist a Plan before applyC, and commit with their existing owner.
  */
private[smworkflow] object CompletionRecords {
  final case class Target(family: String, scope: String, paths: Vector[String])
  object Target { given Encoder[Target] = deriveEncoder; given Decoder[Target] = deriveDecoder }
  final case class Policy(targets: Vector[Target])
  object Policy { given Encoder[Policy] = deriveEncoder; given Decoder[Policy] = deriveDecoder }
  final case class File(path: String, mode: String, originalBlob: String, before: String, after: String, stagingOrdinal: Int)
  object File { given Encoder[File] = deriveEncoder; given Decoder[File] = deriveDecoder }
  final case class Plan(session: String, family: String, scope: String, acceptedTree: String, evidence: Vector[String], files: Vector[File])
  object Plan { given Encoder[Plan] = deriveEncoder; given Decoder[Plan] = deriveDecoder }
  val declaration: ComponentParameterKey[Policy] = ComponentParameterKey.optional("sm-workflow.completion.records",
    ComponentParameterDecoder {
      case ConfigurationValue.StringValue(raw) => io.circe.parser.parse(raw).fold(e => invalid(e.message), decodeC)
      case value: ConfigurationValue.ObjectValue => decodeC(json(value))
      case _ => invalid("requires an explicit structured policy")
    })
  def deltaC(core: ActionCall.Core, p: Plan, tree: String): Consequence[Vector[GitTreeChange]] = for {
    before <- GitObjectId.parseC(p.acceptedTree)
    after <- GitObjectId.parseC(tree)
    changes <- E.executeGitC(core, GitOperation.TreeChanges(before, after)).flatMap(_.valueC)
  } yield changes
  def admitActualC(core: ActionCall.Core, p: Plan, changes: Vector[GitTreeChange]): Consequence[Vector[GoalPhaseNativeClosure.Change]] = for {
    blobs <- changes.flatMap(c => c.oldBlob.toVector ++ c.newBlob.toVector).distinct.foldLeft(Consequence.success(Map.empty[String, GitBlobContent])) { (prior, id) => for {
      saved <- prior
      blob <- E.executeGitC(core, GitOperation.BlobContent(id)).flatMap(_.valueC)
    } yield saved.updated(id.value, blob) }
    admitted <- admitC(p, changes, blobs)
  } yield admitted
  def decodeC(value: Json): Consequence[Policy] = value.as[Policy].fold(e => invalid(e.message), p => validateC(p).map(_ => p))
  def validateC(p: Policy): Consequence[Unit] = if (p != null && p.targets != null && p.targets.size <= 128 &&
    p.targets.forall(_ != null) && p.targets.map(t => (t.family, t.scope)).distinct.size == p.targets.size && p.targets.forall(t => t != null &&
      Set("step", "phase", "goal-task", "delegate-task", "repository-sync")(t.family) && exact(t.scope) &&
      t.paths != null && t.paths.nonEmpty && t.paths.size <= 128 && t.paths.distinct == t.paths && t.paths.forall(path))) Consequence.unit
    else invalid("requires distinct explicit owner/scope and bounded Markdown destinations")
  def selected(policy: Option[Policy], family: String, scope: String): Vector[String] =
    policy.toVector.flatMap(_.targets.filter(t => t.family == family && t.scope == scope).flatMap(_.paths))
  def valid(p: Plan): Boolean = p != null && exact(p.session) && exact(p.scope) && exact(p.family) &&
    GitObjectId.parseC(p.acceptedTree).isSuccess && p.evidence != null && p.evidence.nonEmpty && p.evidence.forall(exact) &&
    p.files != null && p.files.size <= 128 && p.files.forall(_ != null) && p.files.map(_.path).distinct.size == p.files.size && p.files.map(_.stagingOrdinal).distinct.size == p.files.size && p.files.forall(f =>
      f != null && path(f.path) && Set("100644", "100755")(f.mode) && GitObjectId.parseC(f.originalBlob).isSuccess &&
      f.before != null && f.after != null && f.after == projection(f.before, p.session, p.family, p.scope, p.evidence) &&
      f.stagingOrdinal > 0 && f.stagingOrdinal <= 256)
  def projection(before: String, session: String, family: String, scope: String, evidence: Vector[String]): String = {
    val marker = "<!-- sm-workflow completion " + session + " " + family + " " + scope + " -->"
    val block = marker + "\n" + "Accepted " + family + " " + scope + ".\n" +
      evidence.map(e => "Evidence: " + e + "\n").mkString +
      "Local record/commit closure is runtime-owned; later publication, return and synchronization outcomes are recorded separately.\n"
    // A prior conflicting record is refused by capture rather than overwritten.
    if (before.endsWith(block)) before else before + (if (before.endsWith("\n")) "\n" else "\n\n") + block
  }
  def captureC(core: ActionCall.Core, session: String, family: String, scope: String, acceptedTree: String,
    evidence: Vector[String], paths: Vector[String], owned: Vector[String], staging: Vector[String], selection: GitSelectionId, allowApplied: Boolean = false): Consequence[Plan] = for {
    _ <- if (paths != null && owned != null && staging != null && selection != null && evidence != null && paths.forall(p => path(p) && StandaloneFixExecution.owned(p, owned) && staging.contains(p)) && evidence.nonEmpty && evidence.forall(exact))
      Consequence.unit else invalid("destinations must be explicitly owned and registered for selected staging; acceptance evidence is required")
    native <- G.inspectC(core)
    _ <- if (allowApplied || !native.observed.dirty.exists(d => paths.contains(d.path) && d.worktree != ' ')) Consequence.unit
      else invalid("affected completion record is partially staged; no record write or staging performed")
    index <- G.indexTreeC(core)
    _ <- if (index == acceptedTree || allowApplied) Consequence.unit else invalid("record preparation requires the actual accepted index")
    tree <- GitObjectId.parseC(acceptedTree)
    entries <- E.executeGitC(core, GitOperation.TreeEntries(tree, selection)).flatMap(_.valueC)
    files <- paths.foldLeft(Consequence.success(Vector.empty[File])) { (prior, p) => for {
      saved <- prior
      entry <- entries.find(_.path.value == p).flatMap(_.entry).filter(e => Set("100644", "100755")(e.mode))
        .map(Consequence.success).getOrElse(invalid("selected record must already be tracked regular text: " + p))
      blob <- E.executeGitC(core, GitOperation.BlobContent(entry.blob)).flatMap(_.valueC)
      text <- blob.text.filter(t => t.getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector == blob.bytes &&
        t.length <= 262144 && !t.contains('\u0000')).map(Consequence.success).getOrElse(invalid("record requires complete bounded UTF-8 text"))
      file <- WorkspaceFilePath.parseC(p)
      workspace <- WorkspaceId.parseC("sm-workflow-repository")
      actual <- WorkflowFileExecution.readTextC(core, FileTarget(workspace), file)
      _ <- if (actual.text.contains(text) || (allowApplied && actual.text.contains(projection(text, session, family, scope, evidence)))) Consequence.unit else invalid("selected record worktree differs from its accepted index or exact projection")
      marker = "<!-- sm-workflow completion " + session + " " + family + " " + scope + " -->"
      after = projection(text, session, family, scope, evidence)
      _ <- if (!text.contains(marker) || after == text) Consequence.unit else invalid("a different completion section already exists; semantic correction requires issued work")
    } yield saved :+ File(p, entry.mode, entry.blob.value, text, after, staging.indexOf(p) + 1) }
    result = Plan(session, family, scope, acceptedTree, evidence, files)
    _ <- if (valid(result)) Consequence.unit else invalid("record projection has invalid native facts")
  } yield result
  def fileState(file: File, actual: Option[String]): String =
    if (actual.contains(file.after)) "applied" else if (actual.contains(file.before)) "ready" else "conflict"
  /** Observe exact before or after on re-entry; a third value is never overwritten.
    * File CAS is ordinary managed CNCF file execution; no product FIX is charged.
    */
  def applyC(core: ActionCall.Core, p: Plan): Consequence[Unit] = for {
    _ <- if (valid(p)) Consequence.unit else invalid("application has no retained exact plan")
    workspace <- WorkspaceId.parseC("sm-workflow-repository")
    _ <- p.files.foldLeft(Consequence.unit) { (prior, f) => prior.flatMap { _ => for {
      file <- WorkspaceFilePath.parseC(f.path)
      actual <- WorkflowFileExecution.readTextC(core, FileTarget(workspace), file)
      _ <- if (actual.text.contains(f.after)) Consequence.unit else if (actual.text.contains(f.before))
        WorkflowFileExecution.writeTextC(core, FileTarget(workspace), file, f.after, FileWriteMode.UpdateExisting(f.before)).flatMap { r =>
          if (r.outcome == FileWriteOutcome.Updated) Consequence.unit else invalid("record write conflicted; closure remains unfinished") }
        else invalid("record changed after preparation; no overwrite")
      _ <- E.stageGoalCandidateC(core, f.stagingOrdinal).flatMap(_.valueC)
    } yield () } }
  } yield ()
  def admitC(p: Plan, changes: Vector[GitTreeChange], blobs: Map[String, GitBlobContent]): Consequence[Vector[GoalPhaseNativeClosure.Change]] = {
    if (!valid(p) || changes == null || changes.exists(_ == null) || blobs == null) return invalid("delta requires complete retained plan and actual typed facts")
    val values = changes.map { c => for {
      f <- p.files.find(_.path == c.path.value)
      before <- c.oldBlob.flatMap(id => blobs.get(id.value))
      after <- c.newBlob.flatMap(id => blobs.get(id.value))
      if c.status == "M" && c.oldMode == f.mode && c.newMode == f.mode && c.oldBlob.exists(_.value == f.originalBlob) &&
        before.bytes == f.before.getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector &&
        after.bytes == f.after.getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector
    } yield GoalPhaseNativeClosure.Change(f.path, f.mode, c.oldBlob.get.value, c.newBlob.get.value) }
    if (valid(p) && values.forall(_.nonEmpty) && values.flatten.map(_.path).toSet == p.files.filter(f => f.before != f.after).map(_.path).toSet)
      Consequence.success(values.flatten.sortBy(_.path)) else invalid("record delta differs from the retained exact projection or mode")
  }
  private def exact(s: String): Boolean = s != null && s.trim.nonEmpty && s.trim == s && !s.exists(_.isControl)
  private def path(s: String): Boolean = s != null && s.endsWith(".md") && WorkspaceFilePath.parseC(s).isSuccess
  private def invalid[A](s: String): Consequence[A] = Consequence.stateConflict("Completion records: " + s)
  private def json(v: ConfigurationValue): Json = v match {
    case ConfigurationValue.StringValue(x) => Json.fromString(x)
    case ConfigurationValue.NumberValue(x) => Json.fromBigDecimal(x)
    case ConfigurationValue.BooleanValue(x) => Json.fromBoolean(x)
    case ConfigurationValue.ListValue(x) => Json.fromValues(x.map(json))
    case ConfigurationValue.ObjectValue(x) => Json.obj(x.toVector.map((k, v) => k -> json(v))*)
    case ConfigurationValue.NullValue => Json.Null
  }
}
