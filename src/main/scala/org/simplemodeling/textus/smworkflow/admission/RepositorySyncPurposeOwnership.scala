package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import org.goldenport.Consequence
import RepositorySyncPurpose as P

/** Exact later-owner projections; the captured request and original later groups
  * remain provenance. No text merge or semantic acceptance is inferred here.
  */
private[smworkflow] object RepositorySyncPurposeOwnership {
  def assigned(progress: P.Progress, paths: Vector[String]): Vector[P.OwnershipGroup] =
    progress.plan.toVector.flatMap(_.groups.drop(progress.cursor + 1)).flatMap { group =>
      val content = group.content.filter(value => paths.contains(value.path) &&
        (!progress.plan.get.groups(progress.cursor).content.exists(_.path == value.path) ||
          progress.ownership.toVector.flatMap(_.groups).exists(owner => owner.identity == group.identity &&
            owner.content.exists(_.path == value.path))))
      Option.when(content.nonEmpty)(P.OwnershipGroup(group.identity, content))
    }

  def expected(progress: P.Progress, path: String): Option[P.Content] =
    progress.projectedWorktree.toVector.flatten.find(_.path == path)
      .orElse(progress.request.files.find(_.path == path).map(file => P.Content(path, file.worktree)))

  /** Qualified baselines describe candidate text. Retained root expectations,
    * including explicit deletion, take precedence over a newly captured file. */
  def baselineRootExpectationsC(progress: P.Progress, baselines: Vector[P.File]): Consequence[Vector[P.Content]] =
    if (!validProgress(progress) || baselines == null || !baselines.forall(file => file != null &&
        RepositorySyncNativeConflictInput.relative(file.path) && file.indexCode == " " && file.worktreeCode == " " &&
        file.unexamined != null && file.unexamined.isEmpty && file.original != null && file.original.exists(ordinary) &&
        file.original == file.indexed && file.indexed == file.worktree) ||
        baselines.map(_.path).distinct.size != baselines.size)
      RepositorySyncNativeProgressStore.invalid("purpose baselines require unique safe ordinary unchanged candidate files and valid retained ownership")
    else Consequence.success(baselines.map(file => expected(progress, file.path).getOrElse(P.Content(file.path, file.worktree))))

  def effectiveGroup(progress: P.Progress, group: P.Group): P.Group = {
    val projections = progress.ownership.toVector.flatMap(_.groups).filter(_.identity == group.identity).flatMap(_.content)
    group.copy(content = group.content.map(content => projections.find(_.path == content.path).getOrElse(content)))
  }

  /** Later-owner text stays distinct from the current commit. Qualified clean
    * additions with no later owner project their admitted current content. */
  def worktreeProjectionC(progress: P.Progress, group: P.Group): Consequence[Vector[P.Content]] =
    if (!validProgress(progress)) RepositorySyncNativeProgressStore.invalid("purpose ownership is malformed")
    else if (progress.baselineTree.isEmpty) Consequence.success(Vector.empty)
    else group.content.foldLeft(Consequence.success(Vector.empty[P.Content])) { (prior, content) => for {
      values <- prior
      later = progress.plan.toVector.flatMap(_.groups.drop(progress.cursor + 1)).filter(_.content.exists(_.path == content.path))
      supplied = progress.ownership.toVector.flatMap(_.groups).filter(owner => later.exists(_.identity == owner.identity))
        .flatMap(_.content.filter(_.path == content.path))
      texts = supplied.map(_.text).distinct
      _ <- if (texts.size <= 1) Consequence.unit else RepositorySyncNativeProgressStore.invalid(
        "Actual semantic/Human resolution is required for conflicting later owners of " + content.path)
      projection = texts.headOption.map(text => P.Content(content.path, text)).orElse(Option.when(
        later.isEmpty && progress.projectedWorktree.toVector.flatten.exists(_.path == content.path) &&
          !progress.request.files.exists(_.path == content.path))(content))
    } yield values ++ projection }

  def ordinary(text: String): Boolean = text != null && !text.contains('\u0000') && UTF_8.newEncoder().canEncode(text)
  def valid(value: P.OwnershipContinuation): Boolean = value != null && value.groups != null && value.groups.nonEmpty &&
    ordinary(value.rationale) && value.rationale.trim.nonEmpty &&
    value.groups.forall(group => group != null && group.identity != null && group.identity.matches("[a-z][a-z0-9-]{0,63}") &&
      group.content != null && group.content.nonEmpty && group.content.forall(content => content != null &&
        RepositorySyncNativeConflictInput.relative(content.path) && content.text != null && content.text.forall(ordinary)) &&
      group.content.map(_.path).distinct.size == group.content.size) &&
    value.groups.map(_.identity).distinct.size == value.groups.size

  def covers(value: P.OwnershipContinuation, groups: Vector[P.OwnershipGroup]): Boolean = valid(value) &&
    value.groups.map(group => group.identity -> group.content.map(_.path)) ==
      groups.map(group => group.identity -> group.content.map(_.path))

  def validProgress(progress: P.Progress): Boolean = progress.ownership != null && progress.projectedWorktree != null &&
    progress.preparationNamespace != null && progress.preparationNamespace.forall(_.matches("prepare-continuation-[1-9][0-9]*")) &&
    progress.ownership.forall(value => valid(value) && progress.plan.exists(plan =>
      value.groups.forall(group => plan.groups.exists(original => original.identity == group.identity &&
        group.content.forall(content => original.content.exists(_.path == content.path)))))) &&
    progress.projectedWorktree.forall(values => values != null && values.forall(content => content != null &&
      RepositorySyncNativeConflictInput.relative(content.path) && content.text != null && content.text.forall(ordinary) &&
      (progress.request.files.exists(_.path == content.path) ||
        progress.plan.exists(_.groups.exists(_.content.exists(_.path == content.path))))) && values.map(_.path).distinct.size == values.size)

  def merge(progress: P.Progress, value: P.OwnershipContinuation): P.OwnershipContinuation = {
    val existing = progress.ownership.toVector.flatMap(_.groups)
    val groups = progress.plan.toVector.flatMap(_.groups).flatMap { group =>
      val prior = existing.find(_.identity == group.identity).toVector.flatMap(_.content)
      val assigned = value.groups.find(_.identity == group.identity).toVector.flatMap(_.content)
      val merged = group.content.flatMap(content => assigned.find(_.path == content.path).orElse(prior.find(_.path == content.path)))
      Option.when(merged.nonEmpty)(P.OwnershipGroup(group.identity, merged))
    }
    P.OwnershipContinuation(groups, value.rationale)
  }

  def project(progress: P.Progress, actual: Vector[P.Content]): Vector[P.Content] =
    progress.projectedWorktree.toVector.flatten.filterNot(prior => actual.exists(_.path == prior.path)) ++ actual

  def repaired(progress: P.Progress, current: P.Group,
    continuation: Option[P.OwnershipContinuation]): Option[P.OwnershipContinuation] =
    continuation.map(merge(progress, _)).orElse(progress.ownership).map(value => value.copy(groups = value.groups.map(group =>
      if (group.identity != current.identity) group else group.copy(content = group.content.map(content =>
        current.content.find(_.path == content.path).getOrElse(content))))))

  def work(request: RepositorySyncNativeMergeRepair.Request): Option[Json] = request.admittedInventory.flatMap(_.purpose).flatMap { inventory =>
    val progress = inventory.snapshot.progress
    val groups = assigned(progress, request.files.map(_.path))
    Option.when(groups.nonEmpty)(Json.obj(
      "instruction" -> Json.fromString("Repair the current candidate and explicitly integrate each later owner's intent with that correction. Return purposeContinuation with every required owner/path pair and complete ordinary integrated text. Preserve all original later content and metadata. If both meanings cannot safely be combined, return decision-needed with precise owner/path rationale and no replacements or continuation; actual semantic/Human resolution is required."),
      "candidateBaseline" -> request.files.asJson,
      "owners" -> Json.fromValues(groups.map(group => Json.obj(
        "identity" -> group.identity.asJson,
        "originalGroup" -> progress.plan.get.groups.find(_.identity == group.identity).get.asJson,
        "effectiveGroup" -> effectiveGroup(progress, progress.plan.get.groups.find(_.identity == group.identity).get).asJson,
        "originalFiles" -> progress.request.files.filter(file => group.content.exists(_.path == file.path)).asJson,
        "capturedWorktree" -> group.content.flatMap(content => expected(progress, content.path)).asJson))),
      "requiredResultPairs" -> Json.fromValues(groups.flatMap(group => group.content.map(content =>
        Json.obj("identity" -> group.identity.asJson, "path" -> content.path.asJson))))))
  }

  def decisionMessage(request: RepositorySyncNativeMergeRepair.Request, rationale: String): String = {
    val pairs = request.admittedInventory.flatMap(_.purpose).toVector.flatMap(inventory =>
      assigned(inventory.snapshot.progress, request.files.map(_.path))).flatMap(group =>
      group.content.map(content => group.identity + ":" + content.path))
    "Actual semantic/Human resolution is required" + (if (pairs.isEmpty) "" else " for " + pairs.mkString(", ")) +
      ": " + rationale + ". The retained decision executes no candidate, check or commit."
  }
}
