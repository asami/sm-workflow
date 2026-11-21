package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import io.circe.Json
import org.goldenport.{Conclusion, Consequence}
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall}
import org.goldenport.cncf.resource.ResourceReference
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.io.RecordDecoder

/** Read ordinary project declarations through the owning runtime's resource reader.
  * Optional discovery grants no file access and never executes a build definition.
  * @since   Nov.  9, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object WorkflowProjectDeclaration {
  final case class Project(inputs: Vector[String], documents: Vector[String], reason: String, providesFixCheck: Boolean,
    sharedScalaLint: Boolean = false)
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
      val json = declaration.map(raw => new RecordDecoder().yaml(raw).flatMap(_.toJsonStringC).toOption
        .flatMap(text => io.circe.parser.parse(text).toOption)).getOrElse(Some(Json.obj()))
      Consequence.success(json.flatMap { value =>
        val source = value.hcursor.downField("cml").get[String]("source").toOption
        val selected = (inputs ++ source.filter(RepositorySyncNativeConflictInput.relative)).distinct
        val sourceUnknown = value.hcursor.downField("cml").downField("source").focus.exists(_ =>
          !source.exists(RepositorySyncNativeConflictInput.relative))
        if (sourceUnknown) None else Some(Project(selected, documents,
          "Existing build.sbt project; conventional source/build inputs and declared CML source; " +
            "unmapped inputs require FULL coverage, not an inferred no-change result",
          value.hcursor.downField("validation").get[Boolean]("fixCheck").contains(true),
          value.hcursor.downField("validation").get[Boolean]("sharedScalaLint").contains(true)))
      })
    }

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
