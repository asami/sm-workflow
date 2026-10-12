package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.SubjectKind
import org.goldenport.cncf.security.SecuritySubject
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.Argument
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitReasoningSemantic as R,
  SplitPhaseExplicitReasoningSemanticJson as J, SplitPhaseTypedReasoningWorkIssue as I}

/** The real result Operation derives its read query from native issued work.
  * It never substitutes an advance Core or grants advance's write authority.
  * @since   Oct.  6, 2026
  * @version Oct. 12, 2026
  */
private[smworkflow] object SplitPhaseReasoningInvocation {
  def actorC(core: ActionCall.Core, participant: Option[String]): Consequence[Unit] =
    if (core == null || core.action == null || core.action.request == null || core.executionContext == null ||
        core.executionContext.security == null || !core.component.exists(c =>
          c.componentId == SmWorkflowComponent.componentId && c.subsystem.nonEmpty))
      Consequence.securityPermissionDenied("Reasoning requires its actual authenticated owning call")
    else {
      val security = core.executionContext.security
      val subject = SecuritySubject.from(security)
      if (subject.isAuthenticated && (subject.isProviderAuthenticated || subject.isTrustedLocalAuthenticated) &&
          (security.subjectKind == SubjectKind.User || security.subjectKind == SubjectKind.Service) &&
          participant.forall(_ == subject.subjectId)) Consequence.unit
      else Consequence.securityPermissionDenied("Reasoning requires the original authenticated participant")
    }

  def queryC(work: WorkflowInteraction[R.Request, Nothing]): Consequence[SplitPhaseAdvanceTarget] =
    if (work == null) Consequence.stateConflict("Actual reasoning work is absent")
    else work.current match {
      case order: WorkflowContinuation.WorkOrder[?] if order.request.completionOperation == I.completionOperation &&
          order.request.input.exists(v => v.typeIdentity == J.requestCodec.typeIdentity && v.value.isInstanceOf[R.Request]) =>
        val input = order.request.input.get.value.asInstanceOf[R.Request]
        Consequence.success(SplitPhaseAdvanceTarget(work.handle.instanceIdentity.value,
          org.simplemodeling.textus.smworkflow.value.SplitPhasePartitionContextReference(input.context.identity, input.context.revision)))
      case _ => Consequence.stateConflict("Actual work is not the assigned typed reasoning request")
    }

  /** Read-only selection. Native completion separately claims the result and
    * original participant. None/error never enables a fresh issue or planning.
    */
  def completionQueryC(core: ActionCall.Core, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog): Consequence[SplitPhaseAdvanceTarget] =
    if (core != null && core.action != null && core.action.request != null &&
        core.action.request.operation == SplitPhaseReasoningHumanDirection.completionOperation.operation) for {
      _ <- actorC(core, None)
      submitted <- NativeWorkflowResultInput.resolvedC(core)
      command <- SplitPhaseManagedReasoningHumanCompletion.completionQueryC(submitted.continuationId, declaration, catalog, core)
    } yield command
    else for {
    _ <- actorC(core, None)
    request = core.action.request
    _ <- if (request.service.contains(I.completionOperation.service) && request.operation == I.completionOperation.operation)
      Consequence.unit else Consequence.securityPermissionDenied("Reasoning observation requires its exact result Operation")
    submitted <- NativeWorkflowResultInput.resolvedC(core)
    storage <- WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
      SmWorkflowComponent.componentId, declaration.configuration, Vector(catalog.definition), declaration.instances, declaration.continuations)
    native <- storage.bindIssuedC(I.completionOperation, J.requestCodec)
    work <- native.loadC(submitted.continuationId)(using core.executionContext).flatMap {
      case Some(value) => Consequence.success(value)
      case None => Consequence.stateConflict("Reasoning result has no actual native issued work")
    }
    command <- queryC(work)
  } yield command
}
