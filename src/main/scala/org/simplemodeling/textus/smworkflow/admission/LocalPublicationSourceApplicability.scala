package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workflow.ContextReference
import LocalPublication as L
import GoalPhaseDependencySourceRevision as R
import GoalPhaseOrderedProgression as O

/** Producer-owned applicability of one immutable actual publication to a clean
  * committed descendant. This fact grants no publication or workflow transition.
  * @since   Nov.  9, 2026
  * @version Nov.  9, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object LocalPublicationSourceApplicability {
  val schema = "sm-workflow.local-publication-source-applicability.v1"
  val declarationSchema = "sm-workflow.local-publication-artifact-inputs.v1"
  final case class Declaration(schemaVersion: String, identity: String, revision: String,
    targetIdentity: String, destination: String, artifact: ContextReference, nonInputPaths: Vector[String])
  object Declaration {
    given Encoder[Declaration] = deriveEncoder
    given Decoder[Declaration] = _closed(deriveDecoder, declarationValid)
  }
  final case class Fact(schemaVersion: String, original: L.Request, current: L.Source,
    root: String, branch: String, declaration: Declaration, program: Json,
    lineage: Vector[O.HistoryCommit], changes: Vector[R.StageChange])
  object Fact {
    private val _encoder: Encoder[Fact] = deriveEncoder
    private val _decoder: Decoder[Fact] = deriveDecoder
    // As in retained processPolicy, opaque JSON text preserves host environment
    // map keys and explicit nulls across public Record normalization.
    given Encoder[Fact] = Encoder.instance(value => GoalPhasePublishedDependencySourceAdoption.publicJson(
      _encoder(value).mapObject(_.add("program", value.program.noSpaces.asJson))))
    given Decoder[Fact] = _closed(Decoder.instance(c => for {
      raw <- c.get[String]("program")
      program <- io.circe.parser.parse(raw).left.map(e => DecodingFailure(e.message, c.history))
      value <- _decoder.decodeJson(c.value.mapObject(_.add("program", program)))
    } yield value), valid)
  }
  private def _closed[A: Encoder](decoder: Decoder[A], predicate: A => Boolean): Decoder[A] =
    Decoder.instance(c => decoder(c).flatMap(v =>
      if (predicate(v) && v.asJson == c.value) Right(v)
      else Left(DecodingFailure("Invalid publication source applicability", c.history))))
  private def _text(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  private def _oid(value: String): Boolean = value != null && GitObjectId.parseC(value).isSuccess
  private def _source(value: L.Source): Boolean = value != null && value.dirty != null && value.clean &&
    _oid(value.head) && _oid(value.tree)
  private def _root(value: String): Boolean = _text(value) && scala.util.Try(java.nio.file.Path.of(value)).toOption
    .exists(p => p.isAbsolute && p.normalize.toString == value)
  def nonInputPath(value: String): Boolean = RepositorySyncNativeConflictInput.relative(value) &&
    GitPath.parseC(value).isSuccess && !value.exists(c => "*?[]{}".contains(c)) &&
    !value.endsWith("/") && !Vector("src", "project", ".cozy", ".textus").exists(p => value == p || value.startsWith(p + "/")) &&
    !value.split('/').exists(_.startsWith(".git")) &&
    !Set("build.sbt", "version.sbt", "project.yaml", ".jvmopts", ".sbtopts")(value)
  def declarationValid(value: Declaration): Boolean = value != null && value.schemaVersion == declarationSchema &&
    _text(value.identity) && value.revision != null && value.revision.toLongOption.exists(n => n > 0 && n.toString == value.revision) &&
    _text(value.targetIdentity) && _text(value.destination) &&
    scala.util.Try(new java.net.URI(value.destination)).toOption.exists(_.isAbsolute) &&
    R.referenceValid(value.artifact) && value.nonInputPaths != null && value.nonInputPaths.nonEmpty &&
    value.nonInputPaths.distinct == value.nonInputPaths && value.nonInputPaths.forall(nonInputPath)
  def programValid(value: Json, target: L.Target): Boolean = value != null && value.isObject && target != null &&
    value.hcursor.get[String]("target").contains(target.workspace) &&
    value.hcursor.get[String]("operation").contains("publish-local") && value.hcursor.get[String]("identity").exists(_text)
  def successful(value: L.Result): Boolean = value != null && value.status == "published" && value.request != null &&
    value.applicability != null && value.applicability.isEmpty && value.nextPreparation != null && value.nextPreparation.isEmpty &&
    value.process != null && value.process.exists(raw => WorkflowSbtCapture.decode(raw).toOption.exists(p =>
      WorkflowSbtCapture.exitCode(p).contains(0) && !p.stdout.truncated && !p.stderr.truncated &&
      WorkflowSbtCapture.text(p.stdout).textStatus == "complete" && WorkflowSbtCapture.text(p.stderr).textStatus == "complete"))
  private def _change(value: R.StageChange): Boolean = R.changeValid(value) && (value.status match {
    case "A" => value.oldMode == "000000" && Set("100644", "100755")(value.newMode)
    case "D" => Set("100644", "100755")(value.oldMode) && value.newMode == "000000"
    case "M" => value.oldMode == value.newMode && Set("100644", "100755")(value.oldMode) && value.oldBlob != value.newBlob
    case _ => false
  })
  def valid(value: Fact): Boolean = value != null && value.schemaVersion == schema && value.original != null &&
    value.original.input != null && value.original.target != null &&
    _text(value.original.participant) && _text(value.original.input.requestIdentity) &&
    value.original.input.ownerSessionId != null && value.original.input.ownerSessionId.forall(_text) &&
    value.original.input.targetIdentity == value.original.target.identity &&
    _source(value.original.source) && _source(value.current) && value.original.source.head != value.current.head &&
    _root(value.root) && value.branch != null && GitBranchName.parseC(value.branch).isSuccess &&
    declarationValid(value.declaration) && value.declaration.targetIdentity == value.original.target.identity &&
    programValid(value.program, value.original.target) && value.lineage != null && value.lineage.size >= 2 &&
    value.lineage.forall(c => c != null && _oid(c.identity) && _oid(c.tree) && c.parents != null &&
      c.parents.forall(_oid) && c.parents.distinct == c.parents && !c.parents.contains(c.identity)) &&
    value.lineage.map(_.identity).distinct.size == value.lineage.size &&
    value.lineage.head.identity == value.current.head && value.lineage.head.tree == value.current.tree &&
    value.lineage.last.identity == value.original.source.head && value.lineage.last.tree == value.original.source.tree &&
    value.lineage.sliding(2).forall(p => p.size == 1 || p.head.parents.contains(p.last.identity)) &&
    value.changes != null && value.changes.map(_.path).distinct.size == value.changes.size &&
    value.changes.forall(c => _change(c) && value.declaration.nonInputPaths.contains(c.path))
  def binds(value: Fact, previous: L.Result, current: L.Source): Boolean = valid(value) && successful(previous) &&
    value.original == previous.request && value.current == current &&
    previous.process.flatMap(WorkflowSbtCapture.decode(_).toOption).exists(p =>
      value.program.hcursor.get[String]("identity").contains(p.safeProgramIdentity))
  def matches(value: Fact, observation: GoalPhasePublishedDependencySourceAdoption.PublicationObservation,
    current: L.Source, root: String, branch: String, component: String): Boolean = observation != null &&
    observation.result != null && binds(value, observation.result.publication, current) &&
    value.root == root && value.branch == branch && value.original.target.component == component &&
    value.declaration.destination == observation.destination && value.declaration.artifact == observation.artifact &&
    io.circe.parser.parse(observation.publisherProcessPolicy).toOption.exists(policy =>
      selectedProgram(policy.hcursor.downField("processPlan").focus.getOrElse(Json.Null), value.original.target).contains(value.program) &&
        selectedRoot(policy.hcursor.downField("processPlan").focus.getOrElse(Json.Null), value.original.target).contains(root))
  def selectedProgram(plan: Json, target: L.Target): Option[Json] = plan.hcursor.get[Vector[Json]]("sbtPrograms").toOption
    .flatMap(_.filter(p => programValid(p, target)) match { case Vector(p) => Some(p); case _ => None })
  def selectedRoot(plan: Json, target: L.Target): Option[String] = plan.hcursor.get[Vector[Json]]("workspaces").toOption
    .flatMap(_.filter(_.hcursor.get[String]("identity").contains(target.workspace)) match {
      case Vector(p) => p.hcursor.get[String]("root").toOption.filter(_root)
      case _ => None
    })
  def processPlanC(core: ActionCall.Core): Consequence[Json] = Option(core.executionContext.resolvedParameters)
    .flatMap(_.get(GoalPhaseWholePhaseCandidateRecovery.processPlanKey)).map(_.value)
    .map {
      case ConfigurationValue.StringValue(text) => io.circe.parser.parse(text).left.map(_.message)
      case value => GoalPhaseWholePhaseCandidateRecovery.configurationJson(value)
    }.getOrElse(Left("Ordinary process plan is absent")).fold(Consequence.configurationInvalid(_), Consequence.success)

  /** Internal read-only observation seam. The production adapter uses only the
    * existing typed Git/UoW operations; specifications supply deterministic data.
    */
  private[admission] trait Observation {
    def observeC: Consequence[RepositorySyncManagedGitObservation.Observed]
    def rootC: Consequence[String]
    def indexTreeC: Consequence[GitObjectId]
    def metadataC(commit: GitObjectId): Consequence[GitCommitMetadata]
    def changesC(before: GitObjectId, after: GitObjectId): Consequence[Vector[GitTreeChange]]
    def blobC(blob: GitObjectId): Consequence[GitBlobContent]
  }

  /** Complete native observations, including raw status before any timestamp
    * projection. Exhausting ordinary ancestry is inapplicability; Git failures
    * retain their typed failure and never authorize an execution fallback.
    */
  def assessC(core: ActionCall.Core, workspace: String, previous: L.Result, current: L.Source,
    declaration: Declaration, program: Json, root: String, branch: Option[String] = None): Consequence[Option[Fact]] = {
    val observation = new Observation {
      private def _git_c[A](operation: GitOperation[A]): Consequence[A] = WorkflowManagedProcessExecution
        .executeGitC(core, operation, workspace).flatMap(_.valueC)
      def observeC = RepositorySyncManagedGitObservation.observeInWorkspaceC(core, workspace)
      def rootC = _git_c(GitOperation.Root)
      def indexTreeC = _git_c(GitOperation.IndexTree)
      def metadataC(commit: GitObjectId) = _git_c(GitOperation.CommitMetadata(commit))
      def changesC(before: GitObjectId, after: GitObjectId) = _git_c(GitOperation.TreeChanges(before, after))
      def blobC(blob: GitObjectId) = _git_c(GitOperation.BlobContent(blob))
    }
    assessObservedC(observation, previous, current, declaration, program, root, branch)
  }

  private[admission] def assessObservedC(observation: Observation, previous: L.Result, current: L.Source,
    declaration: Declaration, program: Json, root: String, branch: Option[String] = None): Consequence[Option[Fact]] = {
    def _check_(condition: Boolean, message: String): Consequence[Unit] =
      if (condition) Consequence.unit else Consequence.stateConflict(message)
    def _walk_(queue: Vector[Vector[String]], seen: Map[String, O.HistoryCommit]): Consequence[Option[Vector[O.HistoryCommit]]] =
      queue.headOption match {
        case None => Consequence.success(None)
        case Some(path) => for {
          id <- GitObjectId.parseC(path.last)
          metadata <- observation.metadataC(id)
          _ <- _check_(metadata.commit == id, "Applicability ancestry returned a different commit")
          commit = O.HistoryCommit(id.value, metadata.tree.value, metadata.parents.map(_.value))
          cache = seen.updated(commit.identity, commit)
          result <- if (commit.identity == previous.request.source.head) Consequence.success(Some(path.map(cache)))
            else _walk_(queue.tail ++ commit.parents.filterNot(p => cache.contains(p) || path.contains(p) ||
              queue.tail.exists(_.last == p)).map(path :+ _), cache)
        } yield result
      }
    if (!successful(previous) || !_source(previous.request.source) || !_source(current) ||
        previous.request.source.head == current.head || !declarationValid(declaration) ||
        declaration.targetIdentity != previous.request.target.identity || !programValid(program, previous.request.target))
      Consequence.success(None)
    else for {
      actualroot <- observation.rootC
      before <- observation.observeC
      index <- observation.indexTreeC
      result <- if (actualroot != root || before.dirty.nonEmpty || index.value != before.tree ||
          before.head != current.head || before.tree != current.tree || branch.exists(_ != before.branch)) Consequence.success(None)
        else for {
          lineage <- _walk_(Vector(Vector(current.head)), Map.empty)
          fact <- lineage match {
            case None => Consequence.success(Option.empty[Fact])
            case Some(history) => for {
              _ <- _check_(history.head.tree == current.tree && history.last.tree == previous.request.source.tree,
                "Applicability ancestry contradicts the original or current native tree")
              oldtree <- GitObjectId.parseC(previous.request.source.tree)
              newtree <- GitObjectId.parseC(current.tree)
              changes <- observation.changesC(oldtree, newtree)
              candidate = Fact(schema, previous.request, current, root, before.branch, declaration, program, history, changes.map(R.change))
              text <- if (!valid(candidate)) Consequence.success(false) else changes.flatMap(c => c.oldBlob.toVector ++ c.newBlob.toVector)
                .distinct.foldLeft(Consequence.success(true)) { (prior, blob) => for {
                  accepted <- prior
                  content <- observation.blobC(blob)
                } yield accepted && content.text.exists(t => !t.contains('\u0000') &&
                  t.getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector == content.bytes) }
            } yield Option.when(text && binds(candidate, previous, current))(candidate)
          }
        } yield fact
      after <- observation.observeC
      afterroot <- observation.rootC
      afterindex <- observation.indexTreeC
      _ <- _check_(before == after && actualroot == afterroot && index == afterindex,
        "Publication source changed during applicability observation")
    } yield result
  }
  def currentC(core: ActionCall.Core, observation: GoalPhasePublishedDependencySourceAdoption.PublicationObservation,
    binding: GoalPhaseDependencyValidation.Binding, owned: Option[LocalPublicationRuntime.Policy]): Consequence[Unit] =
    observation.result.applicability match {
      case None => Consequence.unit
      case Some(fact) => for {
        _ <- if (matches(fact, observation, L.Source(binding.base.head, binding.base.tree, Vector.empty), binding.root,
            binding.branch, binding.dependency.identity) && owned.forall(p =>
              !p.targets.exists(L.sameHistoricalTarget(_, fact.original.target)) || p.artifactInputs.contains(fact.declaration))) Consequence.unit
          else Consequence.stateConflict("Current publication applicability crosses its declaration or source")
        _ <- owned.filter(_.targets.exists(L.sameHistoricalTarget(_, fact.original.target))).fold(Consequence.unit) { policy => for {
          plan <- processPlanC(core)
          _ <- if (selectedProgram(plan, fact.original.target).contains(fact.program) &&
              selectedRoot(plan, fact.original.target).contains(fact.root)) Consequence.unit
            else Consequence.stateConflict("Current producer program or workspace changed")
          latest <- L.collectApplicabilityC(observation.result.publication, LocalPublicationRuntime._port(core, policy))
          _ <- if (latest.contains(fact)) Consequence.unit
            else Consequence.stateConflict("A later actual producer attempt superseded this applicability")
        } yield () }
        assessed <- assessC(core, binding.dependency.workspace, observation.result.publication, fact.current,
          fact.declaration, fact.program, binding.root, Some(binding.branch))
        _ <- if (assessed.contains(fact)) Consequence.unit
          else Consequence.stateConflict("Current native publication applicability changed")
      } yield ()
    }
}
