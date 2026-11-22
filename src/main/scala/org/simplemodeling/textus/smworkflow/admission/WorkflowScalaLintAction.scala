package org.simplemodeling.textus.smworkflow.admission

import com.typesafe.config.{Config, ConfigFactory, ConfigIncludeContext, ConfigIncluder, ConfigIncluderFile,
  ConfigIncluderURL, ConfigIncluderClasspath, ConfigObject, ConfigParseOptions, ConfigRenderOptions, ConfigValueFactory}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.config.{CncfConfigurationParameterCatalog, ResolvedParameters}
import org.goldenport.cncf.processexecution.{GitBlobContent, GitObjectId, GitOperation, SbtLintInput}
import org.goldenport.cncf.workspace.{FileReadPolicy, FileTarget, WorkspaceFilePath, WorkspaceId}
import org.goldenport.cncf.resource.ResourceReadLimits
import org.goldenport.configuration.ConfigurationValue
import org.simplemodeling.scalalint.{ScalaLintDelta, ScalaLintInput, ScalaLintProfiles}

/** Prepare optional typed FixCheck input from the Session candidate's lint policy.
  * @since   Nov. 19, 2026
  * @version Nov. 22, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object WorkflowScalaLintAction {
  private def invalid[A](message: String): Consequence[A] =
    Consequence.configurationInvalid("Shared Scala lint Action: " + message)

  def inputC(parameters: ResolvedParameters, project: Option[Config], base: String,
    delta: Vector[ScalaLintDelta.File] = Vector.empty): Consequence[SbtLintInput] =
    WorkflowScalaLintParameters.selectionC(parameters).flatMap(inputForSelectionC(_, project, base, delta))

  private[admission] def inputForSelectionC(selection: ScalaLintProfiles.Selection, project: Option[Config], base: String,
    delta: Vector[ScalaLintDelta.File] = Vector.empty): Consequence[SbtLintInput] = for {
    _ <- if (base != null && base.matches("[0-9a-f]{40}|[0-9a-f]{64}")) Consequence.unit
      else invalid("selected lint needs its actual candidate/release Git base")
    _ <- if (project.forall(!_.hasPathOrNull(ScalaLintInput.contextKey))) Consequence.unit
      else invalid("project configuration cannot override native lint context")
    effective <- WorkflowScalaLintParameters.resolveSelectionC(selection, project)
    context = effective.snapshot.withoutPath("configuration")
      .withValue("diffBase", ConfigValueFactory.fromAnyRef(base))
    config = ScalaLintDelta.add(effective.config.withValue(ScalaLintInput.contextKey, context.root), delta)
    json = config.root.render(ConfigRenderOptions.concise().setJson(true))
    _ <- if (json.getBytes(UTF_8).length <= 65536) Consequence.unit
      else invalid("effective configuration exceeds typed lint input bounds")
  } yield SbtLintInput("sm-workflow-shared-scala-lint", ScalaLintProfiles.version, json)

  /** Select only a host-fixed program. No caller filename, argv or engine. */
  private[admission] def rootC(plan: Json, workspace: String): Consequence[Option[Path]] = for {
    programs <- plan.hcursor.get[Vector[Json]]("sbtPrograms").fold(e => invalid(e.message), Consequence.success)
    selected = programs.filter(p => p.hcursor.get[String]("target").contains(workspace) &&
      p.hcursor.get[String]("operation").contains("fix-check"))
    _ <- if (selected.size <= 1) Consequence.unit else invalid("ambiguous host FixCheck binding")
    root <- if (selected.isEmpty)
      Consequence.success(Option.empty[Path])
      else for {
        workspaces <- plan.hcursor.get[Vector[Json]]("workspaces").fold(e => invalid(e.message), Consequence.success)
        values = workspaces.filter(_.hcursor.get[String]("identity").contains(workspace))
        _ <- if (values.size == 1) Consequence.unit else invalid("selected lint workspace is missing or ambiguous")
        selected <- values.headOption.map(Consequence.success).getOrElse(invalid("selected lint workspace is absent"))
        text <- selected.hcursor.get[String]("root").fold(e => invalid(e.message), Consequence.success)
        path <- scala.util.Try(Path.of(text)).fold(e => invalid(e.getMessage), Consequence.success)
        _ <- if (path.isAbsolute && path.normalize == path) Consequence.unit else invalid("selected lint root is not canonical")
      } yield Some(path)
  } yield root

  def prepareC(core: ActionCall.Core, workspace: String, base: Option[String],
    candidateGitWorkspace: Option[String] = None): Consequence[Option[SbtLintInput]] = for {
    _ <- if (core != null && core.executionContext != null) Consequence.unit else invalid("owning call is absent")
    input <- base.fold(Consequence.success(Option.empty[SbtLintInput])) { selectedbase =>
      val git = candidateGitWorkspace.getOrElse(RepositorySyncDirectoryList.workspace(core, "sm-workflow-repository"))
      for {
        actual <- WorkflowManagedProcessExecution.executeGitC(core, GitOperation.Root, git).flatMap(_.valueC)
        path <- scala.util.Try(Path.of(actual).normalize).fold(e => invalid(e.getMessage), Consequence.success)
        _ <- if (candidateGitWorkspace.nonEmpty) Consequence.unit else _verify_root_c(core, workspace, path)
        reader = new Reader(core, path, git)
        lint <- reader.textC(path.resolve("project.yaml")).flatMap(WorkflowProjectDeclaration.lintTextC)
        input <- if (!lint.enabled) Consequence.success(Option.empty[SbtLintInput]) else for {
          oid <- GitObjectId.parseC(selectedbase)
          captured <- WorkflowManagedProcessExecution.executeGitC(core, GitOperation.PendingDiff(oid), git).flatMap(_.valueC)
          _ <- if (!captured.isBinary) Consequence.unit else invalid("binary native delta cannot establish Scala lint attribution")
          diff = _history_delta(core, git, oid, captured.text)
          delta <- scala.util.Try(ScalaLintDelta.fromDiff(diff)).fold(e => invalid(e.getMessage), Consequence.success)
          project <- reader.projectC(path.resolve(".scalafix.conf"))
          input <- inputForSelectionC(lint.selection, project, selectedbase, delta)
        } yield Some(input)
      } yield input
    }
  } yield input

  private def _history_delta(core: ActionCall.Core, workspace: String, base: GitObjectId, diff: String): String =
    WorkflowSourceHistoryParameters.resolveC(core.executionContext).toOption.flatMap(_.rule).fold(diff) { rule =>
      lazy val changes = for {
        index <- WorkflowManagedProcessExecution.executeGitC(core, GitOperation.IndexTree, workspace).flatMap(_.valueC)
        delta <- WorkflowManagedProcessExecution.executeGitC(core, GitOperation.TreeChanges(base, index), workspace).flatMap(_.valueC)
      } yield delta
      def _read_blobs_(path: String): Option[(GitBlobContent, GitBlobContent)] = for {
        delta <- changes.toOption
        change <- delta.filter(_.path.value == path) match {
          case Vector(value) if value.status == "M" && value.oldMode == value.newMode &&
              (value.oldMode == "100644" || value.oldMode == "100755") => Some(value)
          case _ => None
        }
        oldblob <- change.oldBlob
        newblob <- change.newBlob
        before <- WorkflowManagedProcessExecution.executeGitC(core, GitOperation.BlobContent(oldblob), workspace).flatMap(_.valueC).toOption
        after <- WorkflowManagedProcessExecution.executeGitC(core, GitOperation.BlobContent(newblob), workspace).flatMap(_.valueC).toOption
      } yield (before, after)
      WorkflowScalaLintHistoryDelta.withoutProvenUpdates(diff, rule, _read_blobs_)
    }

  def requiresPhaseCheckC(core: ActionCall.Core, checkout: Path): Consequence[Boolean] = {
    val workspace = RepositorySyncDirectoryList.workspace(core, "sm-workflow-repository")
    new Reader(core, checkout, workspace).textC(checkout.resolve("project.yaml"))
      .flatMap(WorkflowProjectDeclaration.requiresSharedLintTextC)
  }

  private def _verify_root_c(core: ActionCall.Core, workspace: String, candidate: Path): Consequence[Unit] = {
    val raw = core.component.flatMap(_.subsystem).flatMap(host =>
      GoalPhaseInputRecordReadPolicy.storedValue(host.configuration.configuration.values,
        CncfConfigurationParameterCatalog.SUBSYSTEM_PROCESS_EXECUTION_PLAN_KEY))
    raw match {
      case Some(ConfigurationValue.StringValue(text)) => for {
        plan <- io.circe.parser.parse(text).fold(e => invalid(e.message), Consequence.success)
        binding <- rootC(plan, workspace)
        _ <- if (binding.contains(candidate)) Consequence.unit
          else invalid("selected FixCheck binding does not match the Session candidate checkout")
      } yield ()
      case _ => invalid("typed FixCheck execution binding is unavailable")
    }
  }

  /** HOCON includes use the same authorized bounded workspace as their
    * owning project file. They cannot escape into ConfigFactory's filesystem,
    * URL or ambient classloader loaders.
    */
  private final class Reader(core: ActionCall.Core, root: Path, identity: String) {
    private var remaining = 65536
    def textC(path: Path): Consequence[Option[String]] =
      if (!path.startsWith(root)) invalid("project lint configuration crosses the Session candidate checkout")
      else for {
        workspace <- WorkspaceId.parseC(identity)
        relative <- WorkspaceFilePath.parseC(root.relativize(path).toString)
        result <- WorkflowFileExecution.readTextC(core, FileTarget(workspace), relative, FileReadPolicy(ResourceReadLimits(65536)))
      } yield result.text
    private def parseC(path: Path, depth: Int, optional: Boolean): Consequence[Option[Config]] = for {
      _ <- if (depth <= 16 && remaining > 0) Consequence.unit else invalid("project include bounds exceeded")
      text <- textC(path)
      parsed <- text match {
        case None if optional => Consequence.success(Option.empty[Config])
        case None => invalid("required project include is absent")
        case Some(raw) =>
          remaining -= raw.getBytes(UTF_8).length
          if (remaining < 0) invalid("project include bytes exceed the input bound")
          else scala.util.Try {
            val includer = new ConfigIncluder with ConfigIncluderFile with ConfigIncluderURL with ConfigIncluderClasspath {
              def withFallback(other: ConfigIncluder): ConfigIncluder = this
              def include(context: ConfigIncludeContext, what: String): ConfigObject =
                includeFile(context, new java.io.File(what))
              def includeFile(context: ConfigIncludeContext, file: java.io.File): ConfigObject = {
                val next = if (file.isAbsolute) file.toPath else path.getParent.resolve(file.toPath)
                parseC(next.normalize, depth + 1, context.parseOptions.getAllowMissing) match {
                  case Consequence.Success(value) => value.getOrElse(ConfigFactory.empty()).root
                  case _ => throw new IllegalArgumentException("Project include acquisition or parsing failed")
                }
              }
              def includeURL(context: ConfigIncludeContext, url: java.net.URL): ConfigObject =
                throw new IllegalArgumentException("URL project includes require an explicit supported Resource contract")
              def includeResources(context: ConfigIncludeContext, what: String): ConfigObject =
                throw new IllegalArgumentException("Classpath project includes require an explicit supported Resource contract")
            }
            ConfigFactory.parseString(raw, ConfigParseOptions.defaults().setOriginDescription(path.toString).setIncluder(includer))
          }.fold(e => invalid(e.getMessage), value => Consequence.success(Some(value)))
      }
    } yield parsed
    def projectC(path: Path): Consequence[Option[Config]] = parseC(path, 0, optional = true)
  }
}
