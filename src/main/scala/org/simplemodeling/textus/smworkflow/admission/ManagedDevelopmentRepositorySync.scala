package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.config.{ComponentParameterDecoder, ComponentParameterKey}
import org.goldenport.configuration.ConfigurationValue

/** Narrow the existing native RepositorySync to the admitted dependency source.
  * This is a selection of existing CNCF resources, not another workspace profile,
  * synchronization workflow, source acquisition or producer build/publication.
  * @since   Nov. 12, 2026
  * @version Nov. 21, 2026
  * @author ASAMI, Tomoharu
  */
private[smworkflow] object ManagedDevelopmentRepositorySync {
  val mainIntegration = ComponentParameterKey.optional("sm-workflow.repository-sync.main-integration",
    ComponentParameterDecoder[String] {
      case ConfigurationValue.StringValue(value) if Set("direct", "local-first")(value) => Consequence.success(value)
      case _ => invalid("main-integration must be direct or local-first")
    })

  /** No edge means the ordinary resolver still owns the request. A selected edge
    * binds only its provider repository, independently of localJar/localProject.
    * Reconnect must preserve the native Start's actual source root; a changed
    * configuration never moves an existing Session to a different B checkout.
    */
  def selectC(policy: Option[ManagedDevelopmentRuntime.Policy], repository: String, plan: Json,
    retainedRoot: Option[String], mainRoute: Option[String]): Consequence[Option[RepositorySyncDirectoryList.Member]] =
    policy.flatMap(p => p.repositorySyncEdge.map(id => p -> id)) match {
      case None => Consequence.success(None)
      case Some((p, id)) => for {
        _ <- ManagedDevelopment.validateC(p.definition)
        edge <- oneC(p.definition.edges.filter(_.identity == id), "selected dependency edge is absent or ambiguous")
        selected <- if (edge.provider.repository != repository) Consequence.success(None) else for {
          workspaces <- plan.hcursor.get[Vector[Json]]("workspaces").fold(e => invalid(e.message), Consequence.success)
          workspace <- oneC(workspaces.filter(_.hcursor.get[String]("identity").contains(edge.provider.workspace)),
            "selected provider workspace is absent or ambiguous")
          root <- workspace.hcursor.get[String]("root").fold(e => invalid(e.message), Consequence.success)
          members <- RepositorySyncDirectoryList.resolveC(Vector(root), plan, "managed-dependency")
          member <- oneC(members, "selected provider has no unique native Git binding")
          _ <- if (member.workspace == edge.provider.workspace && member.repositoryIdentity == edge.provider.repository)
            Consequence.unit else invalid("dependency source differs from the configured native repository binding")
          _ <- if (retainedRoot.forall(_ == member.root)) Consequence.unit
            else invalid("selected dependency source differs from the original RepositorySync Session")
          _ <- if (member.remoteBranch != "main" || mainRoute.contains("direct")) Consequence.unit
            else invalid("managed dependency remote main requires scoped main-integration=direct")
        } yield Some(member)
      } yield selected
    }

  /** Reuse the admitted edge graph for selected consumers.
    * A batch never discovers or incorporates an unselected checkout. */
  def orderC(policy: Option[ManagedDevelopmentRuntime.Policy], members: Vector[RepositorySyncDirectoryList.Member]):
      Consequence[(Vector[RepositorySyncDirectoryList.Member], Map[String, Vector[String]])] = {
    val selected = members.map(m => m.workspace -> m).toMap
    val edges = policy.toVector.flatMap(_.definition.edges).filter(e => selected.contains(e.consumer.workspace))
    for {
      _ <- policy.fold(Consequence.unit)(p => ManagedDevelopment.validateC(p.definition))
      _ <- if (edges.forall(e => selected.contains(e.provider.workspace))) Consequence.unit
        else invalid("selected consumer requires its explicitly registered provider in the directory selection")
      _ <- if (edges.forall(e => selected(e.provider.workspace).repositoryIdentity == e.provider.repository &&
          selected(e.consumer.workspace).repositoryIdentity == e.consumer.repository)) Consequence.unit
        else invalid("selected edge endpoint differs from its native repository binding")
      dependencies = edges.groupBy(e => selected(e.consumer.workspace).sessionId).map { (consumer, incoming) =>
        consumer -> incoming.map(e => selected(e.provider.workspace).sessionId).distinct }
      ordered <- {
        @scala.annotation.tailrec
        def loop(done: Vector[RepositorySyncDirectoryList.Member], remaining: Vector[RepositorySyncDirectoryList.Member]):
            Consequence[Vector[RepositorySyncDirectoryList.Member]] = {
          val ready = remaining.filter(m => dependencies.getOrElse(m.sessionId, Vector.empty).forall(id => done.exists(_.sessionId == id)))
          if (remaining.isEmpty) Consequence.success(done)
          else if (ready.isEmpty) invalid("selected workspace dependencies contain a cycle")
          else loop(done ++ ready, remaining.filterNot(ready.contains))
        }
        loop(Vector.empty, members)
      }
    } yield ordered -> dependencies
  }

  private def oneC[A](values: Vector[A], message: String): Consequence[A] =
    if (values.size == 1) Consequence.success(values.head) else invalid(message)
  private def invalid[A](message: String): Consequence[A] =
    Consequence.configurationInvalid("Managed dependency RepositorySync " + message)
}
