package org.simplemodeling.textus.smworkflow.admission

import com.typesafe.config.{Config, ConfigFactory, ConfigIncludeContext, ConfigIncluder, ConfigIncluderFile,
  ConfigIncluderURL, ConfigIncluderClasspath, ConfigObject, ConfigParseOptions, ConfigRenderOptions, ConfigValueFactory}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall}
import org.goldenport.cncf.config.{CncfConfigurationParameterCatalog, ResolvedParameters}
import org.goldenport.cncf.processexecution.{GitObjectId, GitOperation, SbtLintInput}
import org.goldenport.cncf.resource.{ResourceReadLimits, ResourceReference}
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.protocol.operation.OperationResponse
import org.simplemodeling.scalalint.{ScalaLintDelta, ScalaLintInput, ScalaLintProfiles}

/** Prepare immutable data for the existing typed FixCheck. The trusted host
  * opts into input transport; old fixed programs and issued results stay legacy.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object WorkflowScalaLintAction {
  private def invalid[A](message: String): Consequence[A] =
    Consequence.configurationInvalid("Shared Scala lint Action: " + message)

  def inputC(parameters: ResolvedParameters, project: Option[Config], base: String,
    delta: Vector[ScalaLintDelta.File] = Vector.empty): Consequence[SbtLintInput] = for {
    _ <- if (base != null && base.matches("[0-9a-f]{40}|[0-9a-f]{64}")) Consequence.unit
      else invalid("selected lint needs its actual candidate/release Git base")
    _ <- if (project.forall(!_.hasPathOrNull(ScalaLintInput.contextKey))) Consequence.unit
      else invalid("project configuration cannot override native lint context")
    effective <- WorkflowScalaLintParameters.resolveC(parameters, project)
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
    root <- if (!selected.headOption.exists(_.hcursor.get[Boolean]("lintInput").contains(true)))
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

  def prepareC(core: ActionCall.Core, workspace: String, base: Option[String]): Consequence[Option[SbtLintInput]] = for {
    _ <- if (core != null && core.executionContext != null) Consequence.unit else invalid("owning call is absent")
    raw = core.component.flatMap(_.subsystem).flatMap(host =>
      GoalPhaseInputRecordReadPolicy.storedValue(host.configuration.configuration.values,
        CncfConfigurationParameterCatalog.SUBSYSTEM_PROCESS_EXECUTION_PLAN_KEY))
    root <- raw match {
      case None => Consequence.success(Option.empty[Path])
      case Some(ConfigurationValue.StringValue(text)) => io.circe.parser.parse(text).fold(e => invalid(e.message),
        json => rootC(json, workspace))
      case _ => invalid("typed host plan is not JSON text")
    }
    input <- root match {
      case None => Consequence.success(Option.empty[SbtLintInput])
      case Some(path) => for {
        selectedbase <- base.map(Consequence.success).getOrElse(invalid("selected lint has no captured Git base"))
        actualroot <- RepositorySyncManagedGitObservation.rootC(core)
        _ <- if (Path.of(actualroot).normalize == path) Consequence.unit
          else invalid("selected lint and native delta must share the actual owning source workspace")
        oid <- GitObjectId.parseC(selectedbase)
        captured <- WorkflowManagedProcessExecution.executeGitC(core, GitOperation.PendingDiff(oid)).flatMap(_.valueC)
        _ <- if (!captured.isBinary) Consequence.unit else invalid("binary native delta cannot establish Scala lint attribution")
        delta <- scala.util.Try(ScalaLintDelta.fromDiff(captured.text)).fold(e => invalid(e.getMessage), Consequence.success)
        project <- new Reader(core).projectC(path.resolve(".scalafix.conf"))
        input <- inputC(core.executionContext.resolvedParameters, project, selectedbase, delta)
      } yield Some(input)
    }
  } yield input

  /** HOCON includes use the same authorized bounded Resource reader as their
    * owning project file. They cannot escape into ConfigFactory's filesystem,
    * URL or ambient classloader loaders.
    */
  private final class Reader(val core: ActionCall.Core) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = invalid("configuration reader is not an Operation")
    private var remaining = 65536
    private def textC(path: Path): Consequence[Option[String]] =
      ResourceReference.parseC(path.toUri.toASCIIString).flatMap(reference =>
        read_resource_text(reference, ResourceReadLimits(65536), Some(UTF_8)))
        .map(Some(_)).recoverWith {
          case failure if failure.status.webCode.code == 404 => Consequence.success(None)
          case failure => Consequence.Failure(failure)
        }
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
