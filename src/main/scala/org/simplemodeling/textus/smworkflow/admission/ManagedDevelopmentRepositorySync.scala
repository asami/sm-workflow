package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence

/** Narrow the existing native RepositorySync to the admitted dependency source.
  * This is a selection of existing CNCF resources, not another workspace profile,
  * synchronization workflow, source acquisition or producer build/publication.
  * @since   Nov. 12, 2026
  * @version Nov. 17, 2026
  * @author ASAMI, Tomoharu
  */
private[smworkflow] object ManagedDevelopmentRepositorySync {
  val mainIntegration = RepositorySyncPolicies.mainIntegration

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

  private def oneC[A](values: Vector[A], message: String): Consequence[A] =
    if (values.size == 1) Consequence.success(values.head) else invalid(message)
  private def invalid[A](message: String): Consequence[A] =
    Consequence.configurationInvalid("Managed dependency RepositorySync " + message)
}
