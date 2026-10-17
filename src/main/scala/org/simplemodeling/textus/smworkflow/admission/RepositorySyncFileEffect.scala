package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure
import org.goldenport.cncf.workspace.*
import RepositorySyncNativeConflictInput as I

/** Assigned semantic text effects in the existing repository workspace.
  * CNCF owns acquisition and installation; saved workflow intent owns no replay.
  * @since   Oct. 15, 2026
  * @version Oct. 15, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object RepositorySyncFileEffect {
  final case class File(path: String, expected: Option[String], replacement: String)
  final case class Request(instance: String, revision: String, files: Vector[File])
  final case class Result(instance: String, revision: String, status: String, files: Vector[File])
  private enum State { case Ready, Applied, Conflict }

  def captureC(core: ActionCall.Core, policy: I.Policy, request: Request): Consequence[Result] = for {
    _ <- validateC(policy, request, capture = true)
    current <- readC(core, policy, request)
    _ <- if (current.forall(_.nonEmpty)) Consequence.unit else invalid("capture requires present assigned text")
  } yield Result(request.instance, request.revision, "captured",
    request.files.zip(current).map((file, text) => file.copy(expected = text)))

  def observeC(core: ActionCall.Core, policy: I.Policy, request: Request): Consequence[Result] = for {
    _ <- validateC(policy, request, capture = false)
    current <- readC(core, policy, request)
    states = classify(request, current)
  } yield result(request, if (states.contains(State.Conflict)) "conflict"
    else if (states.forall(_ == State.Applied)) "applied" else "ready")

  def applyC(core: ActionCall.Core, policy: I.Policy, request: Request): Consequence[Result] = for {
    _ <- validateC(policy, request, capture = false)
    current <- readC(core, policy, request)
    states = classify(request, current)
    replay = request.files.zip(states).exists((file, state) =>
      state == State.Applied && !file.expected.contains(file.replacement))
    outcome <- if (states.contains(State.Conflict) || replay) Consequence.success(result(request, "conflict"))
      else for {
        target <- targetC
        conflict <- request.files.filterNot(file => file.expected.contains(file.replacement))
          .foldLeft(Consequence.success(false)) { (prior, file) => prior.flatMap {
            case true => Consequence.success(true)
            case false => WorkspaceFilePath.parseC(file.path).flatMap { path =>
              WorkflowFileExecution.writeTextC(core, target, path, file.replacement,
                FileWriteMode.UpdateExisting(file.expected.get)) match {
                case Consequence.Success(value) => value.outcome match {
                  case FileWriteOutcome.Updated => Consequence.success(false)
                  case FileWriteOutcome.Conflict(_) => Consequence.success(true)
                  case _ => uncertain("unexpected installation result after attempted write")
                }
                case _ => uncertain("write was attempted without an established result; no replay")
              }
            }
          }}
      } yield result(request, if (conflict) "conflict" else "applied")
  } yield outcome

  private def targetC: Consequence[FileTarget] = WorkspaceId.parseC("sm-workflow-repository").map(FileTarget.apply)

  private def validateC(policy: I.Policy, request: Request, capture: Boolean): Consequence[Unit] = for {
    _ <- if (policy != null) policy.validateC else invalid("semantic policy is absent")
    _ <- if (request != null && Vector(request.instance, request.revision)
        .forall(s => s != null && s.nonEmpty && s.trim == s) && request.files != null &&
        request.files.nonEmpty && request.files.size <= 128 &&
        request.files.forall(file => file != null && I.relative(file.path) && policy.semanticPaths(file.path) &&
          valid(file.replacement, policy.maximumBlobBytes) && file.expected != null &&
          file.expected.isEmpty == capture && file.expected.forall(valid(_, policy.maximumBlobBytes))) &&
        request.files.map(_.path).distinct.size == request.files.size) Consequence.unit
      else invalid("assigned instance/revision/paths/text intent is incomplete")
  } yield ()

  // This is the business text budget, applied after CNCF's complete bounded read.
  // A cheap length bound also keeps semantic UTF-8 counting allocation finite.
  private def valid(text: String, maximum: Int): Boolean = text != null && !text.contains('\u0000') &&
    text.length <= maximum && text.getBytes(StandardCharsets.UTF_8).length <= maximum

  private def readC(core: ActionCall.Core, policy: I.Policy, request: Request): Consequence[Vector[Option[String]]] = for {
    target <- targetC
    values <- request.files.foldLeft(Consequence.success(Vector.empty[Option[String]])) { (prior, file) => for {
      values <- prior
      path <- WorkspaceFilePath.parseC(file.path)
      value <- WorkflowFileExecution.readTextC(core, target, path)
      _ <- if (value.text.forall(valid(_, policy.maximumBlobBytes))) Consequence.unit
        else invalid("actual assigned text is binary or exceeds its semantic budget")
    } yield values :+ value.text }
  } yield values

  private def classify(request: Request, current: Vector[Option[String]]): Vector[State] =
    request.files.zip(current).map { (file, text) => text match {
      case Some(value) if value == file.replacement => State.Applied
      case Some(value) if file.expected.contains(value) => State.Ready
      case _ => State.Conflict
    }}
  private def result(request: Request, status: String): Result =
    Result(request.instance, request.revision, status, request.files)
  private def invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("RepositorySync File " + reason)
  private def uncertain[A](reason: String): Consequence[A] =
    DataStoreConditionalTransitionFailure.transactionIndeterminate("RepositorySync File " + reason)
}
