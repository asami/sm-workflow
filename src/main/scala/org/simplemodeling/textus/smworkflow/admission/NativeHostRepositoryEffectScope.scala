package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms

/** The unchanged native RepositoryEffect target, shared with passive input.
  * Projection grants no authority and does not admit Start or Git semantics.
  * @since   Oct. 14, 2026
  * @version Oct. 14, 2026
  */
private[smworkflow] object NativeHostRepositoryEffectScope {
  def bindingFromInputsC(componentInstance: String, context: RepositorySyncManagedFetch.StartContext,
    target: RepositoryGitRuntimePrograms.Target, effect: RepositorySyncNativeConvergence.Effect): Consequence[Json] = {
    if (componentInstance == null || context == null || target == null || target.root == null || effect == null ||
        Vector(context.selectionIdentity, context.invocationIdentity, context.participantIdentity,
          context.repositoryIdentity, context.remoteEndpointIdentity, context.syncMode,
          target.localBranch, target.remote, target.remoteBranch, target.endpoint).exists(_ == null)) {
      Consequence.stateConflict("Repository effect selected input or physical target is absent")
    } else Consequence.success(Json.obj("componentInstance" -> Json.fromString(componentInstance),
      "scope" -> Json.obj("kind" -> Json.fromString("repository-effect"),
        "selection" -> Json.fromString(context.selectionIdentity), "invocation" -> Json.fromString(context.invocationIdentity),
        "participant" -> Json.fromString(context.participantIdentity), "repository" -> Json.fromString(context.repositoryIdentity),
        "endpointIdentity" -> Json.fromString(context.remoteEndpointIdentity), "mode" -> Json.fromString(context.syncMode),
        "effect" -> Json.fromString(effect.toString), "target" -> Json.obj(
          "root" -> Json.fromString(target.root.toString), "localBranch" -> Json.fromString(target.localBranch),
          "remote" -> Json.fromString(target.remote), "remoteBranch" -> Json.fromString(target.remoteBranch),
          "endpoint" -> Json.fromString(target.endpoint)))))
  }
}
