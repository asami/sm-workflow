package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall}
import org.goldenport.cncf.resource.ResourceReference
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.io.RecordDecoder

/** Read ordinary project declarations through the owning runtime's resource reader.
  * Optional discovery grants no file access and never executes a build definition.
  * @since   Nov.  9, 2026
  * @version Nov.  9, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object WorkflowProjectDeclaration {
  final case class Project(inputs: Vector[String], documents: Vector[String], reason: String)
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
            "unmapped inputs require FULL coverage, not an inferred no-change result"))
      })
    }

  def readC(root: Path, core: ActionCall.Core): Consequence[Option[Project]] = {
    val reader = Read(core)
    (for {
      build <- reader.optionalC(root.resolve("build.sbt"))
      declaration <- if (build.nonEmpty) reader.optionalC(root.resolve("project.yaml"))
        else Consequence.success(None)
      project <- fromC(build, declaration)
    } yield project).recover(_ => None) // Unknown discovery is needed only at the actual merge boundary.
  }

  private final case class Read(core: ActionCall.Core) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = Consequence.success(OperationResponse.Scalar("project-declaration-reader"))
    def optionalC(path: Path): Consequence[Option[String]] =
      ResourceReference.parseC(path.toUri.toString).flatMap(reference => read_resource_text(reference, Some(UTF_8)))
        .map(value => Option(value)).recoverWith {
          // Discovery is optional. Missing or unavailable read authority leaves the
          // project unknown; it cannot produce a document exemption or a receipt.
          case failure if failure.status.webCode.code == 404 => Consequence.success(None)
          case failure => Consequence.Failure(failure)
        }
  }
}
