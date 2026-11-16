package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workspace.WorkspaceId
import RepositorySyncNativeProgressStore as N
import RepositorySyncPurpose as P
import RepositorySyncPurpose.given

/** Finite persisted facts from the three actual Git results needed to
  * reacquire a materialized Session candidate. No later observation can turn
  * a missing materialization receipt into a successful one. */
private[smworkflow] object RepositorySyncSessionWorkspaceReceipt {
  final case class Selection(identity: String, paths: Vector[String], message: Option[String])
  object Selection {
    given Encoder[Selection] = deriveEncoder
    given Decoder[Selection] = deriveDecoder
  }
  final case class Result(workspace: String, selection: Option[Selection], selectedPaths: Vector[String],
    operation: String, value: String, process: Json)
  object Result {
    given Encoder[Result] = deriveEncoder
    given Decoder[Result] = deriveDecoder
  }
  final case class Materialized(candidate: P.Candidate, materialization: Result, root: Result, head: Result)
  object Materialized {
    given Encoder[Materialized] = deriveEncoder
    given Decoder[Materialized] = deriveDecoder
  }

  private def selection(value: GitSelectionParameters): Selection =
    Selection(value.identity.value, value.paths.map(_.value), value.message)

  private def capture[A](actual: GitExecutionResult[A], operation: String, value: String): Consequence[Result] =
    if (actual == null || actual.target == null || actual.operation == null ||
        actual.outcome != GitExecutionOutcome.Succeeded || actual.value.isEmpty || actual.processResult == null ||
        actual.processResult.termination != ProcessExecutionTermination.Exited(0) ||
        actual.processResult.artifacts == null || actual.processResult.artifacts.nonEmpty ||
        actual.selectedPaths == null || actual.target.selection == null)
      N.invalid("Session candidate requires an actual successful typed Git receipt without unretained artifacts")
    else {
      val process = WorkflowSbtCapture.encode(actual.processResult)
      WorkflowSbtCapture.decode(process).fold(N.invalid, decoded =>
        if (decoded != actual.processResult) N.invalid("Session candidate process receipt is not lossless")
        else Consequence.success(Result(actual.target.workspaceId.value, actual.target.selection.map(selection),
          actual.selectedPaths.map(_.value), operation, value, process)))
    }

  def materializationC(candidate: P.Candidate, materialization: GitExecutionResult[Unit]): Consequence[Result] = for {
    producer <- P.producerC(candidate)
    destination <- materialization.operation match {
      case GitOperation.MaterializePurposeCandidate(id, actual, workspace) if actual == producer &&
          materialization.target.selection.exists(_.identity == id) => Consequence.success(workspace.value)
      case _ => N.invalid("Actual materialization operation crosses the retained candidate")
    }
    material <- capture(materialization, "materialize", destination)
  } yield material
  def rootC(root: GitExecutionResult[String]): Consequence[Result] =
    if (root != null && root.operation == GitOperation.Root && root.value.nonEmpty)
      capture(root, "root", root.value.get) else N.invalid("Actual Root receipt is absent")
  def headC(head: GitExecutionResult[GitObjectId]): Consequence[Result] =
    if (head != null && head.operation == GitOperation.Head && head.value.nonEmpty)
      capture(head, "head", head.value.get.value) else N.invalid("Actual Head receipt is absent")

  private def targetC(value: Result): Consequence[GitTarget] = for {
    workspace <- WorkspaceId.parseC(value.workspace)
    selection <- value.selection.fold(Consequence.success(Option.empty[GitSelectionParameters]))(s => for {
      identity <- GitSelectionId.parseC(s.identity)
      paths <- s.paths.foldLeft(Consequence.success(Vector.empty[GitPath]))((prior, text) => for {
        values <- prior; path <- GitPath.parseC(text)
      } yield values :+ path)
    } yield Some(GitSelectionParameters(identity, paths, s.message)))
  } yield GitTarget(workspace, selection)

  private def pathsC(value: Result): Consequence[Vector[GitPath]] =
    value.selectedPaths.foldLeft(Consequence.success(Vector.empty[GitPath]))((prior, text) => for {
      paths <- prior; path <- GitPath.parseC(text)
    } yield paths :+ path)

  private def processC(value: Result): Consequence[ProcessExecutionResult] =
    WorkflowSbtCapture.decode(value.process).fold(N.invalid, result =>
      if (result.termination == ProcessExecutionTermination.Exited(0)) Consequence.success(result)
      else N.invalid("Retained Git receipt is not a successful terminated execution"))

  def restoreC(value: Materialized, alias: String): Consequence[(GitExecutionResult[Unit], GitExecutionResult[String], GitExecutionResult[GitObjectId])] = for {
    _ <- if (value != null && value.materialization != null && value.root != null && value.head != null &&
      value.materialization.operation == "materialize" && value.materialization.value == alias &&
      value.root.operation == "root" && value.head.operation == "head" &&
      value.root.workspace == alias && value.head.workspace == alias) Consequence.unit
      else N.invalid("Retained candidate Git receipt has crossed operations or workspaces")
    producer <- P.producerC(value.candidate)
    destination <- WorkspaceId.parseC(alias)
    mt <- targetC(value.materialization); rt <- targetC(value.root); ht <- targetC(value.head)
    id <- mt.selection.map(_.identity).map(Consequence.success).getOrElse(N.invalid("Materialization selection is absent"))
    mp <- processC(value.materialization); rp <- processC(value.root); hp <- processC(value.head)
    mpaths <- pathsC(value.materialization); rpaths <- pathsC(value.root); hpaths <- pathsC(value.head)
    oid <- GitObjectId.parseC(value.head.value)
  } yield (GitExecutionResult(mt, GitOperation.MaterializePurposeCandidate(id, producer, destination),
      GitExecutionOutcome.Succeeded, Some(()), mp, mpaths),
    GitExecutionResult(rt, GitOperation.Root, GitExecutionOutcome.Succeeded, Some(value.root.value), rp, rpaths),
    GitExecutionResult(ht, GitOperation.Head, GitExecutionOutcome.Succeeded, Some(oid), hp, hpaths))
}
