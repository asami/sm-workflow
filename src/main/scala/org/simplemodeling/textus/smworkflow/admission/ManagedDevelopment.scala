package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence

/** A dependency edge selects source, JAR and project independently. Publications
  * and selections are immutable Component records, never a work.d JSON ledger.
  * @since   Nov. 12, 2026
  * @version Nov. 12, 2026
  * @author ASAMI, Tomoharu
  */
private[smworkflow] object ManagedDevelopment {
  final case class Coordinate(organization: String, module: String, version: String)
  final case class Edge(identity: String, provider: GoalPhaseDependencyScope.Component, consumer: GoalPhaseDependencyScope.Component,
    localRepository: String, localJar: Boolean, localProject: Option[String], requested: Coordinate)
  final case class Definition(scope: String, ownerWorkspace: String, repository: String,
    staging: String, edges: Vector[Edge])
  final case class Input(edgeIdentity: String, requestIdentity: String, publication: String, selection: String)
  final case class Publication(edge: Edge, scope: String, identity: String, producerSelection: String,
    selected: Coordinate, jar: String, ivy: String, source: Json, report: Json, completion: Json)
  final case class Selection(identity: String, publication: Publication)
  final case class Result(status: String, detail: Json)
  final case class Execution(status: String, detail: Json)

  trait Port {
    def loadC(key: String): Consequence[Option[Json]]
    def createC(key: String, value: Json): Consequence[Unit]
    def sourceC(edge: Edge): Consequence[Json]
    def consumerSourceC(edge: Edge): Consequence[Json] = sourceC(edge)
    def executeC(edge: Edge, input: Input, selected: Option[Publication]): Consequence[Execution]
    def publicationC(edge: Edge, input: Input, source: Json): Consequence[Publication]
    def consumerReportC(edge: Edge, input: Input, selected: Publication): Consequence[Json]
    def participant: String
  }

  def id(value: String): Boolean = value != null && value != "." && value != ".." && value.matches("[A-Za-z0-9_.-]{1,128}")
  private def coordinate(value: Coordinate): Boolean = value != null &&
    Vector(value.organization, value.module, value.version).forall(id)
  def validateC(d: Definition): Consequence[Unit] =
    if (d != null && id(d.scope) && workspace(d.ownerWorkspace) && relative(d.repository) && relative(d.staging) &&
      d.repository != d.staging && !d.repository.startsWith(d.staging + "/") && !d.staging.startsWith(d.repository + "/") &&
      d.edges != null && d.edges.nonEmpty && d.edges.map(_.identity).distinct.size == d.edges.size &&
      d.edges.forall(e => e != null && id(e.identity) && component(e.provider) && component(e.consumer) &&
        e.provider.workspace != e.consumer.workspace && e.localRepository == d.ownerWorkspace &&
        e.localProject != null && e.localProject.forall(workspace) && coordinate(e.requested))) Consequence.unit
    else invalid("explicit scope, independent dependency bindings or resource membership is invalid")

  def edgeC(d: Definition, input: Input): Consequence[Edge] = for {
    _ <- validateC(d)
    _ <- if (input != null && Vector(input.edgeIdentity, input.requestIdentity, input.publication, input.selection).forall(id))
      Consequence.unit else invalid("invalid explicit operation identities")
    edge <- d.edges.find(_.identity == input.edgeIdentity).map(Consequence.success)
      .getOrElse(invalid("dependency edge is not declared"))
    _ <- if (edge.localJar) Consequence.unit else invalid("dependency edge has no local JAR binding")
  } yield edge

  def version(original: String, scope: String, publication: String): String =
    original + "-smdev-" + scope.length + "-" + scope + "-" + publication.length + "-" + publication
  def base(d: Definition, e: Edge, publication: String): String = d.repository + "/" +
    e.requested.organization + "/" + e.requested.module + "/" + version(e.requested.version, d.scope, publication)
  def key(d: Definition, e: Edge, kind: String, identity: String): String =
    "managed-development:" + d.scope + ":" + e.identity + ":" + kind + ":" + identity
  private def component(value: GoalPhaseDependencyScope.Component): Boolean = value != null &&
    id(value.identity) && id(value.repository) && workspace(value.workspace)
  private def workspace(value: String): Boolean = org.goldenport.cncf.workspace.WorkspaceId.parseC(value).isSuccess
  private def relative(value: String): Boolean = org.goldenport.cncf.workspace.WorkspaceRelativePath.parseC(value).isSuccess
  private def decodeC[A: io.circe.Decoder](value: Json): Consequence[A] =
    value.as[A].fold(e => invalid(e.message), Consequence.success)
  private def selectionC(d: Definition, e: Edge, input: Input, p: Port): Consequence[Selection] = for {
    row <- p.loadC(key(d, e, "selection", input.selection))
    s <- row.map(decodeC[Selection]).getOrElse(invalid("selection is unavailable; ordinary fallback is forbidden"))
    _ <- if (s.identity == input.selection && s.publication.edge == e && s.publication.scope == d.scope &&
      s.publication.identity == input.publication) Consequence.unit else invalid("selection crosses its dependency edge or publication")
  } yield s

  def runC(operation: String, d: Definition, input: Input, p: Port): Consequence[Result] = for {
    e <- edgeC(d, input)
    result <- operation match {
      case "publish" => publishC(d, e, input, p)
      case "select" => for {
        raw <- p.loadC(key(d, e, "publication", input.publication))
        pub <- raw.map(decodeC[Publication]).getOrElse(invalid("only completed publications can be selected"))
        _ <- if (pub.edge == e && pub.scope == d.scope && pub.identity == input.publication)
          Consequence.unit else invalid("publication crosses its declared binding")
        s = Selection(input.selection, pub)
        _ <- immutableC(p, key(d, e, "selection", input.selection), s.asJson)
      } yield Result("selected-unverified", s.asJson)
      case "compile" => for {
        selection <- selectionC(d, e, input, p)
        result <- attemptC(d, e, input, "compile", p) {
          p.consumerSourceC(e).flatMap { before => p.executeC(e, input, Some(selection.publication)).flatMap { actual =>
            if (actual.status != "succeeded") Consequence.success(Result(actual.status, actual.detail))
            else p.consumerSourceC(e).flatMap { after =>
              if (before != after) Consequence.success(Result("unverified", Json.obj(
                "execution" -> actual.detail, "reason" -> "consumer source changed during compilation".asJson)))
              else p.consumerReportC(e, input, selection.publication).transform(
              report => Consequence.success(Result("verified",
                Json.obj("selection" -> selection.asJson, "execution" -> actual.detail, "resolution" -> report,
                  "consumerSource" -> after))),
              error => Consequence.success(Result("unverified", Json.obj("execution" -> actual.detail, "reason" -> error.show.asJson))))
            }
          }}
        }
      } yield result
      case "status" => for {
        selection <- p.loadC(key(d, e, "selection", input.selection))
        publication <- p.loadC(key(d, e, "publication", input.publication))
        compiled <- p.loadC(key(d, e, "compile-result", input.requestIdentity))
        published <- p.loadC(key(d, e, "publish-result", input.requestIdentity))
        compileIntent <- p.loadC(key(d, e, "compile-intent", input.requestIdentity))
        publishIntent <- p.loadC(key(d, e, "publish-intent", input.requestIdentity))
        intent = if (compiled.nonEmpty || compileIntent.nonEmpty) compileIntent else publishIntent
        _ <- if (intent.forall(_ == intentJson(input, p))) Consequence.unit
          else invalid("status request crosses its retained participant or immutable input")
        result <- compiled.orElse(published).map(decodeC[Result]).getOrElse(Consequence.success(Result(
          if (intent.nonEmpty) "unknown" else if (selection.nonEmpty) "selected-unverified"
          else if (publication.nonEmpty) "published" else "not-found",
          Json.obj("publication" -> publication.asJson, "selection" -> selection.asJson))))
      } yield result
      case _ => invalid("operation is not implemented")
    }
  } yield result

  private def publishC(d: Definition, e: Edge, input: Input, p: Port): Consequence[Result] =
    attemptC(d, e, input, "publish", p) {
      for {
        source <- p.sourceC(e)
        actual <- p.executeC(e, input, None)
        result <- if (actual.status != "succeeded") Consequence.success(Result(actual.status, actual.detail))
          else for {
            after <- p.sourceC(e)
            // Native source evidence includes dirty-file bytes, not only Git path/status metadata.
            result <- if (source != after) Consequence.success(Result("unverified", Json.obj(
              "execution" -> actual.detail, "reason" -> "producer source changed during publication; artifact remains unselected".asJson)))
              else p.publicationC(e, input, source).transform(
                pub => immutableC(p, key(d, e, "publication", input.publication), pub.asJson)
                  .map(_ => Result("published", Json.obj("publication" -> pub.asJson, "execution" -> actual.detail))),
                error => Consequence.success(Result("unverified", Json.obj("execution" -> actual.detail, "reason" -> error.show.asJson))))
          } yield result
      } yield result
    }

  /** An interrupted effect is observable UNKNOWN, never an automatic rerun. */
  private def attemptC(d: Definition, e: Edge, input: Input, kind: String, p: Port)
    (execute: => Consequence[Result]): Consequence[Result] = for {
    previous <- p.loadC(key(d, e, kind + "-result", input.requestIdentity))
    result <- previous match {
      case Some(raw) => for {
        intent <- p.loadC(key(d, e, kind + "-intent", input.requestIdentity))
        _ <- if (intent.contains(intentJson(input, p))) Consequence.unit else invalid("request redelivery crosses its participant or immutable input")
        result <- decodeC[Result](raw)
      } yield result
      case None => for {
        intent <- p.loadC(key(d, e, kind + "-intent", input.requestIdentity))
        result <- intent match {
          case Some(raw) => if (raw == intentJson(input, p)) Consequence.success(Result("unknown", raw))
            else invalid("request identity is already owned by another input or participant")
          case None => for {
            _ <- p.createC(key(d, e, kind + "-intent", input.requestIdentity), intentJson(input, p))
            result <- execute
            _ <- p.createC(key(d, e, kind + "-result", input.requestIdentity), result.asJson)
          } yield result
        }
      } yield result
    }
  } yield result
  private def intentJson(input: Input, p: Port): Json =
    Json.obj("input" -> input.asJson, "participant" -> p.participant.asJson)
  private def immutableC(p: Port, key: String, value: Json): Consequence[Unit] = p.loadC(key).flatMap {
    case Some(old) if old == value => Consequence.unit
    case Some(_) => invalid("immutable publication or selection already has another value")
    case None => p.createC(key, value)
  }
  def invalid[A](detail: String): Consequence[A] = Consequence.stateConflict("Managed development: " + detail)
}
