package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import io.circe.Json
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall}
import org.goldenport.cncf.resource.ResourceReference
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.io.RecordDecoder
import org.simplemodeling.scalalint.ScalaLintProfiles

/** Read ordinary project declarations through the owning runtime's resource reader.
  * Optional discovery grants no file access and never executes a build definition.
  * @since   Nov.  9, 2026
  * @version Nov. 24, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object WorkflowProjectDeclaration {
  final case class LintPolicy(enabled: Boolean, selection: ScalaLintProfiles.Selection)
  object LintPolicy {
    val default = LintPolicy(true, ScalaLintProfiles.selection(None, None))
  }

  final case class Project(inputs: Vector[String], documents: Vector[String], reason: String, providesFixCheck: Boolean,
    lint: LintPolicy = LintPolicy.default, metals: ScalaMetalsRepair.Policy = ScalaMetalsRepair.Policy()) {
    def requiresSharedScalaLint: Boolean = providesFixCheck && lint.enabled
  }
  final case class ReadFailure(path: Path, cause: Conclusion) {
    def diagnostic: String =
      s"Could not read project declaration $path (${cause.show})"
        .map(character => if (character.isControl) ' ' else character)
  }
  private val inputs = Vector("src", "src_managed", "build.sbt", "project", "project.yaml",
    "generator", "dependencies", "runtime", "scripts", "ai")
  private val documents = Vector("README.md", "LICENSE", "CHANGELOG.md", "docs/notes", "docs/journal")

  def fromC(build: Option[String], declaration: Option[String]): Consequence[Option[Project]] =
    if (build.isEmpty) Consequence.success(None)
    else {
      val json = declaration.map(raw => _decode_yaml_c(raw).toOption).getOrElse(Some(Json.obj()))
      json.fold(Consequence.success(Option.empty[Project])) { value =>
        val source = value.hcursor.downField("cml").downField("source").as[String].toOption
        val selected = source.filter(RepositorySyncNativeConflictInput.relative)
          .filterNot(inputs.contains).fold(inputs)(path => inputs :+ path)
        val sourceUnknown = value.hcursor.downField("cml").downField("source").focus.exists(_ =>
          !source.exists(RepositorySyncNativeConflictInput.relative))
        if (sourceUnknown) Consequence.success(None) else for {
          lint <- lintPolicyC(value)
          metals <- metalsPolicyC(value)
        } yield Some(Project(selected, documents,
          "Existing build.sbt project; conventional source/build inputs and declared CML source; " +
            "unmapped inputs require FULL coverage, not an inferred no-change result",
          value.hcursor.downField("validation").downField("fixCheck").as[Boolean].contains(true),
          lint, metals))
      }
    }

  private[admission] def lintPolicyC(declaration: Json): Consequence[LintPolicy] = {
    def _mapping_(value: Option[Json], name: String) =
      value.getOrElse(Json.obj()).asObject.toRight(name + " must be a mapping")
    val parsed = for {
      validation <- _mapping_(declaration.hcursor.downField("validation").focus, "validation")
      enabled <- validation("sharedScalaLint").fold(Right(true): Either[String, Boolean])(
        _.as[Boolean].left.map(_ => "sharedScalaLint must be true or false"))
      profile <- _mapping_(validation("scalaLint"), "scalaLint")
      cursor = Json.fromJsonObject(profile).hcursor
      source <- cursor.downField("sourceStyle").as[Option[String]].left.map(_.message)
      history <- cursor.downField("historyStyle").as[Option[String]].left.map(_.message)
    } yield (enabled, source, history)
    for {
      fields <- parsed.fold(_lint_configuration_c(_), Consequence.success)
      (enabled, source, history) = fields
      selection <- scala.util.Try(ScalaLintProfiles.selection(source, history))
        .fold(error => _lint_configuration_c(error.getMessage), Consequence.success)
    } yield LintPolicy(enabled, selection)
  }

  def metalsPolicyC(declaration: Json): Consequence[ScalaMetalsRepair.Policy] = {
    val validation = declaration.hcursor.downField("validation").focus.getOrElse(Json.obj())
    val value = validation.hcursor.downField("metals").focus.getOrElse(Json.obj())
    val parsed = for {
      _ <- Either.cond(validation.isObject && value.isObject, (), "metals must be a mapping")
      _ <- Either.cond(value.asObject.exists(_.keys.forall(Set("enabled", "timeoutMillis", "maxEdits", "maxReplacementChars", "maxActions", "serverIdentity").contains)), (), "unknown metals field")
      _ <- Either.cond(!value.asObject.exists(_.values.exists(_.isNull)), (), "metals fields cannot be null")
      enabled <- value.hcursor.downField("enabled").as[Option[Boolean]].left.map(_.message)
      timeout <- value.hcursor.downField("timeoutMillis").as[Option[Int]].left.map(_.message)
      edits <- value.hcursor.downField("maxEdits").as[Option[Int]].left.map(_.message)
      chars <- value.hcursor.downField("maxReplacementChars").as[Option[Int]].left.map(_.message)
      actions <- value.hcursor.downField("maxActions").as[Option[Int]].left.map(_.message)
      server <- value.hcursor.downField("serverIdentity").as[Option[String]].left.map(_.message)
      policy = ScalaMetalsRepair.Policy(enabled.getOrElse(true), timeout.getOrElse(10000),
        edits.getOrElse(32), chars.getOrElse(65536), actions.getOrElse(64), server)
      _ <- Either.cond(policy.timeoutMillis > 0 && policy.timeoutMillis <= 60000 && policy.maxEdits > 0 &&
        policy.maxEdits <= 256 && policy.maxReplacementChars >= 0 && policy.maxReplacementChars <= 1048576 &&
        policy.maxActions > 0 && policy.maxActions <= 256 && policy.serverIdentity.forall(identity =>
          identity.nonEmpty && identity.length <= 128 && identity.forall(character =>
            character.isLetterOrDigit || character == '.' || character == '-' || character == '_')),
        (), "metals limits outside permitted bounds")
    } yield policy
    parsed.fold(message => Consequence.configurationInvalid("project.yaml Metals policy: " + message), Consequence.success)
  }

  /** The declared compiler version is data, never inferred from build.sbt. */
  def scalaVersionTextC(value: Option[String]): Consequence[Option[String]] = value match {
    case None => Consequence.success(None)
    case Some(raw) => _decode_yaml_c(raw).flatMap { project =>
      val build = project.hcursor.downField("build").focus
      val version = build.flatMap(_.hcursor.downField("scalaVersion").focus)
      if (build.exists(!_.isObject) || version.exists(_.isNull))
        Consequence.configurationInvalid("project.yaml build.scalaVersion must be a version string")
      else version match {
        case None => Consequence.success(None)
        case Some(json) => json.asString.filter(text => text.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) match {
          case Some(text) => Consequence.success(Some(text))
          case None => Consequence.configurationInvalid("project.yaml build.scalaVersion must be a version string")
        }
      }
    }
  }

  /** The roots locate compiler outputs in the owning workspace. They do not
    * select a compiler or expand the File read grant. */
  private[admission] def semanticdbRootsTextC(value: Option[String]): Consequence[Map[String, String]] =
    value.fold(Consequence.success(Map.empty[String, String])) { raw => _decode_yaml_c(raw).flatMap { project =>
      val roots = project.hcursor.downField("validation").downField("scalaLint").downField("semanticdbRoots")
      if (!roots.succeeded) Consequence.success(Map.empty[String, String])
      else roots.as[Map[String, String]].fold(
        _ => _lint_configuration_c("semanticdbRoots must map compile or test to a literal relative directory"),
        selected => if (selected.forall { case (scope, root) =>
          Set("compile", "test")(scope) && GoalPhasePurposeRepair.path(root) &&
            org.goldenport.cncf.workspace.WorkspaceFilePath.parseC(root).isSuccess
        }) Consequence.success(selected)
        else _lint_configuration_c("semanticdbRoots must map compile or test to a literal relative directory"))
    }}

  def metalsTextC(value: Option[String]): Consequence[ScalaMetalsRepair.Policy] =
    value.fold(Consequence.success(ScalaMetalsRepair.Policy()))(_decode_yaml_c(_).flatMap(metalsPolicyC))

  def lintTextC(value: Option[String]): Consequence[LintPolicy] =
    value.fold(Consequence.success(LintPolicy.default))(_decode_yaml_c(_).flatMap(lintPolicyC))

  def requiresSharedLintTextC(value: Option[String]): Consequence[Boolean] = value match {
    case None => Consequence.success(false)
    case Some(raw) => _decode_yaml_c(raw).flatMap { project =>
      if (project.hcursor.downField("validation").downField("fixCheck").as[Boolean].contains(true))
        lintPolicyC(project).map(_.enabled)
      else Consequence.success(false)
    }
  }

  private def _decode_yaml_c(raw: String): Consequence[Json] = for {
    record <- new RecordDecoder().yaml(raw)
    text <- record.toJsonStringC
    json <- io.circe.parser.parse(text).fold(error => _lint_configuration_c(error.message), Consequence.success)
  } yield json

  private def _lint_configuration_c[A](message: String): Consequence[A] =
    Consequence.configurationInvalid("project.yaml Scala lint policy: " + message)

  def readC(root: Path, core: ActionCall.Core): Consequence[Either[ReadFailure, Option[Project]]] = {
    val reader = Read(core)
    val declarations = for {
      build <- reader.optional(root.resolve("build.sbt"))
      declaration <- if (build.nonEmpty) reader.optional(root.resolve("project.yaml")) else Right(None)
    } yield (build, declaration)
    declarations match {
      case Left(failure) => Consequence.success(Left(failure))
      case Right((build, declaration)) => fromC(build, declaration).map(Right(_))
    }
  }

  private final case class Read(core: ActionCall.Core) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = Consequence.success(OperationResponse.Scalar("project-declaration-reader"))
    def optional(path: Path): Either[ReadFailure, Option[String]] =
      ResourceReference.parseC(path.toUri.toString).flatMap(reference => read_resource_text(reference, Some(UTF_8))) match {
        case Consequence.Success(value) => Right(Option(value))
        case Consequence.Failure(cause) if cause.status.webCode.code == 404 => Right(None)
        case Consequence.Failure(cause) => Left(ReadFailure(path, cause))
      }
  }
}
