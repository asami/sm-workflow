package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.security.SecuritySubject
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.DefinitionBinding
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.operation.OperationRequest
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.value.RepositorySyncStartInput
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms.Target

/** The authenticated public Start is the sync request. Record its correlation
  * in the existing native Start, rather than requiring caller-built selection
  * and per-effect evidence rows. This projection grants no CNCF capability:
  * every Git operation still passes the configured managed execution boundary.
  * @since   Oct. 28, 2026
  * @version Oct. 27, 2026
  */
private[smworkflow] object StandardRepositorySyncIntent {
  private val prefix = "runtime-repository-sync:"

  def inputC(request: Request, target: Target): Consequence[RepositorySyncStartInput] = for {
    record <- NativeWorkflowOperationInput.recordC(request, "input")
    repository <- record.getString("repositoryIdentity").filter(value => value.nonEmpty && value.trim == value)
      .map(Consequence.success).getOrElse(Consequence.stateConflict("RepositorySync requires an explicit repositoryIdentity"))
    mode <- record.getString("syncMode").filter(_ == "FULL_STATE").map(Consequence.success)
      .getOrElse(Consequence.stateConflict("RepositorySync requires explicit FULL_STATE sync intent"))
    _ <- Vector("localBranch" -> target.localBranch, "trackingRemote" -> target.remote,
      "trackingBranch" -> target.remoteBranch).foldLeft(Consequence.unit) { case (prior, (name, expected)) =>
      prior.flatMap(_ => record.getAny(name) match {
        case None => Consequence.unit
        case Some(value: String) if value == expected => Consequence.unit
        case _ => Consequence.stateConflict("RepositorySync host storage/tracking declaration differs: " + name)
      })
    }
    endpoint <- record.getAny("remoteEndpointIdentity") match {
      case None => Consequence.success(target.endpoint)
      case Some(value: String) if value == target.endpoint => Consequence.success(value)
      case _ => Consequence.stateConflict("RepositorySync endpoint identity differs from the configured endpoint")
    }
  } yield RepositorySyncStartInput(repository, target.localBranch, target.remote, target.remoteBranch, endpoint, mode)

  /** Supply the resolved business record to the existing owning receiver. */
  def bindInput(service: spec.ServiceDefinition, input: RepositorySyncStartInput): spec.ServiceDefinition =
    NativeWorkflowDescriptionMetadata.service(service, spec.OperationDefinitionGroup(NonEmptyVector.fromVectorUnsafe(
      service.operations.operations.toVector.map { operation =>
        if (operation.name != "startRepositorySync") operation
        else new spec.OperationDefinition {
          val specification = operation.specification
          def createOperationRequest(request: Request): Consequence[OperationRequest] =
            operation.createOperationRequest(request.copy(properties = request.properties.filterNot(_.name == "input") :+
              Property("input", input, None)))
        }
      })))

  def selectionC(business: NativeWorkflowStartInput.BusinessInput, session: String,
    definition: DefinitionBinding, core: ActionCall.Core): Consequence[ProfileStartAdmission.Selection] = business match {
    case input: NativeWorkflowStartInput.Repository => for {
      participant <- Consequence.success(SecuritySubject.from(core.executionContext.security).subjectId)
      _ <- GoalPhaseParticipantAdmission.admitC(participant, core.executionContext)
      _ <- if (session.nonEmpty && input.syncMode == "FULL_STATE") Consequence.unit
        else Consequence.stateConflict("Standard RepositorySync requires a canonical session and explicit FULL_STATE sync intent")
      json = Json.obj("repositoryIdentity" -> Json.fromString(input.repositoryIdentity),
        "localBranch" -> Json.fromString(input.localBranch), "trackingRemote" -> Json.fromString(input.trackingRemote),
        "trackingBranch" -> Json.fromString(input.trackingBranch),
        "remoteEndpointIdentity" -> Json.fromString(input.remoteEndpointIdentity), "syncMode" -> Json.fromString(input.syncMode))
    } yield ProfileStartAdmission.Selection(prefix + session, RepositorySyncManagedFetch.startOperation,
      definition.workflowIdentity, definition.workflowRevision, participant,
      ProfileStartAdmission.SelectionOrigin.Workflow, prefix + session, None,
      Some(TypedValue("RepositorySyncStartInput", json)))
    case _ => Consequence.stateConflict("Standard RepositorySync requires its declared repository sync intent")
  }

  /** Runtime derivation from this actual call is not an independently issued
    * Human answer. Keep the legacy explicit-Human record gate unchanged. */
  def admitC(selected: ProfileStartAdmission.Selection, business: NativeWorkflowStartInput.BusinessInput,
    session: String, definition: DefinitionBinding, core: ActionCall.Core): Consequence[Unit] =
    selectionC(business, session, definition, core).flatMap { expected =>
      if (selected == expected) Consequence.unit
      else Consequence.securityPermissionDenied("Repository sync selection differs from the actual authenticated request")
    }

  def owns(context: RepositorySyncManagedFetch.StartContext): Boolean =
    context.selectionIdentity.startsWith(prefix) && context.selectionIdentity == context.invocationIdentity

  val authority: RepositorySyncNativeConvergence.AuthorityPort = new RepositorySyncNativeConvergence.AuthorityPort {
    def admitC(core: ActionCall.Core, context: RepositorySyncManagedFetch.StartContext, target: Target,
      effect: RepositorySyncNativeConvergence.Effect): Consequence[Unit] = for {
      _ <- GoalPhaseParticipantAdmission.admitC(context.participantIdentity, core.executionContext)
      session <- core.executionContext.security.session.flatMap(_.sessionId).map(Consequence.success)
        .getOrElse(Consequence.stateConflict("Repository sync effect requires its owning session"))
      _ <- if (context.selectionIdentity == prefix + session && context.invocationIdentity == prefix + session &&
          context.syncMode == "FULL_STATE" && context.localBranch == target.localBranch &&
          context.trackingRemote == target.remote && context.trackingBranch == target.remoteBranch &&
          Set(RepositorySyncNativeConvergence.Effect.Push, RepositorySyncNativeConvergence.Effect.FinalFetch)(effect))
        Consequence.unit
        else Consequence.securityPermissionDenied("Repository effect is outside the retained clean sync request; separate merge/commit admission is required")
    } yield ()
  }
}
