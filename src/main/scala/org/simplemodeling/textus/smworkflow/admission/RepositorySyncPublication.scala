package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workspace.{FileTarget, WorkspaceFilePath, WorkspaceId}
import org.simplemodeling.textus.smworkflow.value as V
import RepositorySyncNativeProgressStore as N
import RepositorySyncManagedGitObservation as G
import WorkflowManagedProcessExecution as E

/** Publication precedes the final metadata commit and push. Publication itself
  * never commits. Every effect uses the owning call's typed Git/SBT/File UoW;
  * retained intents are observed rather than replayed.
  * @since   Oct. 30, 2026
  * @version Oct. 30, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object RepositorySyncPublication {
  final case class State(target: String, request: String,
    outputs: Vector[RepositorySyncPurpose.Content] = Vector.empty)

  private given Encoder[RepositorySyncPurpose.Content] = deriveEncoder
  private given Decoder[RepositorySyncPurpose.Content] = deriveDecoder
  given stateEncoder: Encoder[State] = deriveEncoder
  given stateDecoder: Decoder[State] = deriveDecoder

  private val workspace = "sm-workflow-repository"
  private val selectionIdentity = "repository-publication"

  /** Only the ordinary generated CAR catalogs are publication outputs. Other
    * source/ongoing changes remain unfinished and are never swept into a commit. */
  def outputPathsC(dirty: Vector[G.DirtyPath]): Consequence[Vector[String]] = {
    val paths = dirty.map(_.path)
    if (paths.distinct == paths && dirty.forall(d => d.renamedFrom.isEmpty &&
        ((d.index == ' ' && d.worktree == 'M') || (d.index == '?' && d.worktree == '?')) &&
        d.path.startsWith("src/main/catalog/car/") && d.path.endsWith(".yaml") && GitPath.parseC(d.path).isSuccess))
      Consequence.success(paths)
    else N.invalid("publication left non-catalog, staged or renamed changes; preserve them without commit or push")
  }

  /** Resume from the publication frontier before touching the older commit
    * receipt, whose source may now have an actual metadata child commit. */
  def retainedBaseC(store: N.Store): Consequence[Option[N.Stage]] = store.loadC("publication-prepared")

  def advanceC(store: N.Store, committed: N.Stage, authority: RepositorySyncNativeConvergence.AuthorityPort):
    Consequence[Either[V.RepositorySyncAdvanceResult, N.Stage]] = {
    val targets = store.declaration.localPublication.toVector.flatMap(_.targets).filter(t => t.workspace == workspace && t.beforeSync)
    if (targets.isEmpty) return Consequence.success(Right(committed))
    if (targets.size != 1) return N.invalid("repository publication requires exactly one target for its workspace")
    val policy = store.declaration.localPublication.get
    val target = targets.head
    for {
      completed <- store.loadC("publication-commit-observed")
      result <- completed match {
        case Some(value) => cleanC(store, value).map(_ => Right(value))
        case None => for {
          retained <- retainedBaseC(store)
          prepared <- retained match {
            case Some(value) => Consequence.success(value)
            case None => for {
              _ <- cleanC(store, committed)
              value <- store.saveC("publication-prepared", "native-publication-prepared", committed.record,
                committed.payload.copy(stage = "PublicationPreparing", publication = Some(State(target.identity,
                  store.identity + ":" + store.namespace + ":final-publication"))))
            } yield value
          }
          state <- prepared.payload.publication.filter(_.target == target.identity).map(Consequence.success)
            .getOrElse(N.invalid("retained publication crosses its configured target"))
          published <- LocalPublicationRuntime.ensureC(store.core, policy,
            LocalPublication.Input(state.request, target.identity, Some(store.identity)))
          result <- if (!published.ready) Consequence.success(Left(NativeWorkflowOperationOutput.repositoryWait(store.identity,
            "Final local publication: " + published.reason, "local-publication-" + published.status)))
          else for {
            existing <- store.loadC("publication-observed")
            observed <- existing match {
              case Some(value) => Consequence.success(value)
              case None => for {
                actual <- G.inspectC(store.core)
                _ <- store.scopeC(actual.observed)
                _ <- if (actual.mergeHead.isEmpty && prepared.payload.finalTip.contains(actual.observed.head)) Consequence.unit
                  else N.invalid("repository HEAD changed during local publication")
                paths <- outputPathsC(actual.observed.dirty)
                values <- paths.foldLeft(Consequence.success(Vector.empty[RepositorySyncPurpose.Content])) { (prior, name) => for {
                  all <- prior
                  path <- WorkspaceFilePath.parseC(name)
                  id <- WorkspaceId.parseC(workspace)
                  content <- WorkflowFileExecution.readTextC(store.core, FileTarget(id), path)
                  text <- content.text.filterNot(_.contains('\u0000')).map(Consequence.success)
                    .getOrElse(N.invalid("publication output is not complete ordinary text"))
                } yield all :+ RepositorySyncPurpose.Content(name, Some(text)) }
                value <- store.saveC("publication-observed", "native-publication-observed", prepared.record,
                  prepared.payload.copy(stage = "PublicationObserved", publication = Some(state.copy(outputs = values))))
              } yield value
            }
            value <- if (observed.payload.publication.get.outputs.isEmpty) cleanC(store, observed).map(_ => observed)
              else metadataCommitC(store, observed, authority)
          } yield Right(value)
        } yield result
      }
    } yield result
  }

  private def selectionC(stage: N.Stage): Consequence[GitSelectionParameters] = for {
    id <- GitSelectionId.parseC(selectionIdentity)
    paths <- stage.payload.publication.get.outputs.foldLeft(Consequence.success(Vector.empty[GitPath]))((prior, output) =>
      for { all <- prior; path <- GitPath.parseC(output.path) } yield all :+ path)
  } yield GitSelectionParameters(id, paths, None)

  private def metadataCommitC(store: N.Store, observed: N.Stage, authority: RepositorySyncNativeConvergence.AuthorityPort):
    Consequence[N.Stage] = for {
    selection <- selectionC(observed)
    staged <- store.loadC("publication-staged").flatMap {
      case Some(value) => Consequence.success(value)
      case None => for {
        pending <- store.loadC("publication-stage-intent")
        intent <- pending match {
          case Some(value) => Consequence.success(value)
          case None => for {
            _ <- authority.admitC(store.core, store.context, store.declaration.target,
              RepositorySyncNativeConvergence.Effect.PurposeCommit)
            _ <- outputsUnchangedC(store, observed)
            value <- store.saveC("publication-stage-intent", "native-publication-stage-prepared", observed.record,
              observed.payload.copy(stage = "PublicationStaging"))
            _ <- E.executePurposeC(store.core, GitOperation.StageSelectedAll(selection.identity), selection).flatMap(_.valueC)
          } yield value
        }
        tree <- stagedTreeC(store, intent, selection)
        value <- store.saveC("publication-staged", "native-publication-staged", intent.record,
          intent.payload.copy(stage = "PublicationStaged", indexTree = Some(tree)))
      } yield value
    }
    retained <- store.loadC("publication-commit-intent")
    intent <- retained match {
      case Some(value) => Consequence.success(value)
      case None => for {
        _ <- authority.admitC(store.core, store.context, store.declaration.target,
          RepositorySyncNativeConvergence.Effect.PurposeCommit)
        tree <- stagedTreeC(store, staged, selection)
        _ <- if (staged.payload.indexTree.contains(tree)) Consequence.unit else N.invalid("publication index changed before commit")
        value <- store.saveC("publication-commit-intent", "native-publication-commit-prepared", staged.record,
          staged.payload.copy(stage = "PublicationCommitPreparing"))
        _ <- E.executePurposeC(store.core, GitOperation.CommitSelected(selection.identity), selection.copy(message = Some("Update local publication metadata"))).flatMap(_.valueC)
      } yield value
    }
    actual <- G.inspectC(store.core)
    _ <- store.scopeC(actual.observed)
    commit <- G.commitC(store.core, actual.observed.head)
    _ <- if (actual.mergeHead.isEmpty && actual.observed.dirty.isEmpty &&
        commit.parents == Vector(intent.payload.finalTip.get) && intent.payload.indexTree.contains(commit.tree)) Consequence.unit
      else N.uncertain("publication metadata commit is unfinished or UNKNOWN; do not replay its intent")
    saved <- store.saveC("publication-commit-observed", "native-publication-commit-observed", intent.record,
      intent.payload.copy(stage = "PublicationCommitObserved", finalTip = Some(commit.identity), after = Some(actual.observed)))
  } yield saved

  private def outputsUnchangedC(store: N.Store, stage: N.Stage): Consequence[Unit] = for {
    actual <- G.inspectC(store.core)
    _ <- store.scopeC(actual.observed)
    paths <- outputPathsC(actual.observed.dirty)
    _ <- if (actual.mergeHead.isEmpty && stage.payload.finalTip.contains(actual.observed.head) &&
        paths.toSet == stage.payload.publication.get.outputs.map(_.path).toSet) Consequence.unit
      else N.invalid("publication outputs changed before staging")
    _ <- stage.payload.publication.get.outputs.foldLeft(Consequence.unit) { (prior, output) => for {
      _ <- prior
      workspaceId <- WorkspaceId.parseC(workspace)
      path <- WorkspaceFilePath.parseC(output.path)
      value <- WorkflowFileExecution.readTextC(store.core, FileTarget(workspaceId), path)
      _ <- if (value.text == output.text) Consequence.unit else N.invalid("publication output text changed before staging")
    } yield () }
  } yield ()

  private def stagedTreeC(store: N.Store, stage: N.Stage, selection: GitSelectionParameters): Consequence[String] = for {
    actual <- G.inspectC(store.core)
    _ <- store.scopeC(actual.observed)
    _ <- if (actual.mergeHead.isEmpty && stage.payload.finalTip.contains(actual.observed.head) &&
        actual.observed.dirty.forall(d => d.worktree == ' ' && d.index != ' ' && d.renamedFrom.isEmpty) &&
        actual.observed.dirty.map(_.path).toSet == selection.paths.map(_.value).toSet) Consequence.unit
      else N.uncertain("publication staging is unfinished or crossed; preserve its intent without replay")
    tree <- G.indexTreeC(store.core)
    treeId <- GitObjectId.parseC(tree)
    entries <- E.executePurposeC(store.core, GitOperation.TreeEntries(treeId, selection.identity), selection).flatMap(_.valueC)
    _ <- if (entries.size == stage.payload.publication.get.outputs.size) Consequence.unit
      else N.invalid("publication tree entries differ from the selected output count")
    _ <- stage.payload.publication.get.outputs.zip(entries).foldLeft(Consequence.unit) { case (prior, (output, entry)) => for {
      _ <- prior
      regular <- entry.entry.map(Consequence.success).getOrElse(N.invalid("publication output is absent from the staged tree"))
      _ <- if (Set("100644", "100755")(regular.mode)) Consequence.unit
        else N.invalid("publication output is not a regular file")
      blob <- E.executeGitC(store.core, GitOperation.BlobContent(regular.blob)).flatMap(_.valueC)
      _ <- if (entry.path.value == output.path && blob.text == output.text) Consequence.unit
        else N.invalid("staged publication text differs from the retained output")
    } yield () }
    parent <- GitObjectId.parseC(stage.payload.finalTip.get)
    checked <- E.executePurposeC(store.core, GitOperation.CheckSelectedIndex(selection.identity, parent), selection).flatMap(_.valueC)
    _ <- if (checked.findingKeys.isEmpty) Consequence.unit else N.invalid("publication metadata hygiene is unfinished")
  } yield tree

  private def cleanC(store: N.Store, stage: N.Stage): Consequence[Unit] = for {
    actual <- G.inspectC(store.core)
    _ <- store.scopeC(actual.observed)
    _ <- if (actual.mergeHead.isEmpty && actual.observed.dirty.isEmpty && stage.payload.finalTip.contains(actual.observed.head) &&
        stage.payload.indexTree.contains(actual.observed.tree)) Consequence.unit
      else N.invalid("repository is not clean at the retained publication frontier")
  } yield ()
}
