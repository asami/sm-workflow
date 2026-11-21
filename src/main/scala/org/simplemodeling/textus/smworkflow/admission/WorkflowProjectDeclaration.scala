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
  * @version Nov. 22, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object WorkflowProjectDeclaration {
  final case class LintPolicy(enabled: Boolean, selection: ScalaLintProfiles.Selection)
  object LintPolicy {
    val default = LintPolicy(true, ScalaLintProfiles.selection(None, None))
  }

  final case class Project(inputs: Vector[String], documents: Vector[String], reason: String, providesFixCheck: Boolean,
    lint: LintPolicy = LintPolicy.default) {
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
        val source = value.hcursor.downField("cml").get[String]("source").toOption
        val selected = (inputs ++ source.filter(RepositorySyncNativeConflictInput.relative)).distinct
        val sourceUnknown = value.hcursor.downField("cml").downField("source").focus.exists(_ =>
          !source.exists(RepositorySyncNativeConflictInput.relative))
        if (sourceUnknown) Consequence.success(None) else lintPolicyC(value).map(lint => Some(Project(selected, documents,
          "Existing build.sbt project; conventional source/build inputs and declared CML source; " +
            "unmapped inputs require FULL coverage, not an inferred no-change result",
          value.hcursor.downField("validation").get[Boolean]("fixCheck").contains(true),
          lint)))
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
      source <- cursor.get[Option[String]]("sourceStyle").left.map(_.message)
      history <- cursor.get[Option[String]]("historyStyle").left.map(_.message)
    } yield (enabled, source, history)
    for {
      fields <- parsed.fold(_lint_configuration_c(_), Consequence.success)
      (enabled, source, history) = fields
      selection <- scala.util.Try(ScalaLintProfiles.selection(source, history))
        .fold(error => _lint_configuration_c(error.getMessage), Consequence.success)
    } yield LintPolicy(enabled, selection)
  }

  def lintTextC(value: Option[String]): Consequence[LintPolicy] =
    value.fold(Consequence.success(LintPolicy.default))(_decode_yaml_c(_).flatMap(lintPolicyC))

  def requiresSharedLintTextC(value: Option[String]): Consequence[Boolean] = value match {
    case None => Consequence.success(false)
    case Some(raw) => _decode_yaml_c(raw).flatMap { project =>
      if (project.hcursor.downField("validation").get[Boolean]("fixCheck").contains(true))
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
