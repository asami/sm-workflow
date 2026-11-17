package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workspace.*
import RepositorySyncNativeProgressStore as N
import RepositorySyncNativeMergeValidation as V
import RepositorySyncPurpose as P

/** Resolves only the existing host's unique admitted candidate policy.
  * @since   Nov. 17, 2026
  * @version Nov. 17, 2026
  */
private[smworkflow] object RepositorySyncSessionWorkspace {
  def resolveC(store: N.Store, progress: P.Progress, policy: Option[V.Policy]): Consequence[P.SessionWorkspace] = for {
    plan <- RepositorySyncPurposeMaterializationContinuation.hostPlanC(store.core)
    rows <- plan.hcursor.get[Option[Vector[io.circe.Json]]]("sessionCandidateWorkspaces")
      .fold(e => N.invalid(e.message), values => Consequence.success(values.getOrElse(Vector.empty)))
    component <- store.core.component.map(c => Consequence.success(c.componentId.name))
      .getOrElse(N.invalid("Candidate host component is unavailable"))
    selected = rows.filter(_.hcursor.get[String]("componentId").contains(component))
    row <- if (selected.size == 1) Consequence.success(selected.head)
      else N.invalid("Configure one admitted Session candidate workspace policy for this component")
    parent <- row.hcursor.get[String]("parent").fold(e => N.invalid(e.message), Consequence.success)
    alias <- row.hcursor.get[String]("alias").fold(e => N.invalid(e.message), Consequence.success)
    source = RepositorySyncDirectoryList.workspace(store.core, "sm-workflow-repository")
    _ <- WorkspaceId.parseC(source); _ <- WorkspaceId.parseC(parent); _ <- WorkspaceId.parseC(alias)
    workspaces <- plan.hcursor.get[Vector[io.circe.Json]]("workspaces").fold(e => N.invalid(e.message), Consequence.success)
    grants <- plan.hcursor.get[Vector[io.circe.Json]]("workspaceGrants").fold(e => N.invalid(e.message), Consequence.success)
    _ <- Vector(source, parent, alias).foldLeft(Consequence.unit) { (prior, name) => prior.flatMap { _ =>
      val bindings = workspaces.count(_.hcursor.get[String]("identity").contains(name))
      val permissions = grants.count(g => g.hcursor.get[String]("componentId").contains(component) &&
        g.hcursor.get[String]("workspaceId").contains(name) && g.hcursor.get[String]("access").contains("manage"))
      if (bindings == 1 && permissions == 1) Consequence.unit
      else N.invalid("Session candidate setup requires one workspace binding and manage grant for " + name)
    } }
    candidate <- progress.candidate.map(Consequence.success).getOrElse(N.invalid("Purpose candidate is absent"))
    before <- GitObjectId.parseC(candidate.parent); after <- GitObjectId.parseC(candidate.tree)
    changed <- WorkflowManagedProcessExecution.executeGitC(store.core, GitOperation.TreeChanges(before, after)).flatMap(_.valueC)
    document = store.declaration.documentInputs.exists(p => changed.forall(row =>
      RepositorySyncProgramImpact.covers(p.documentPaths, row.path.value) &&
        !RepositorySyncProgramImpact.covers(p.consumedPaths, row.path.value)))
    selectedPolicy <- policy.map(Consequence.success).getOrElse(N.invalid("Ordinary repository validation is unavailable"))
    assessment <- selectedPolicy.assessment.map(Consequence.success)
      .getOrElse(N.invalid("Ordinary repository assessment policy is unavailable"))
    action = GoalPhaseStepAssessment.Policy("repository-sync-purpose", if (document) "documentation" else "code",
      assessment.detailedReview, if (assessment.scalaLint == "required") "required" else "off", "off", Vector.empty)
    _ <- GoalPhaseStepAssessment.validatePurposePolicyC(action)
    slot = P.Slot(alias, selectedPolicy.checks, action)
    result = P.SessionWorkspace(component, source, parent, alias, store.identity,
      RepositorySyncPurposeRuntime.allocationIdentity(progress), slot)
    _ <- SessionCandidateWorkspaceReference.fromRecordC(record(result))
  } yield result

  def record(value: P.SessionWorkspace): SessionCandidateWorkspaceRecord =
    SessionCandidateWorkspaceRecord(value.component, value.source, value.parent, value.alias, value.session, value.candidate)

  /** Reopening keeps the current ordinary validation contract, as the historic
    * managed-candidate owner did. A saved slot cannot waive a new requirement. */
  def requireCurrentBindingC(store: N.Store, progress: P.Progress): Consequence[P.SessionWorkspace] = for {
    retained <- progress.sessionWorkspace.map(Consequence.success)
      .getOrElse(N.invalid("Retained Session candidate binding is absent"))
    selected <- resolveC(store, progress, store.declaration.purposeValidation)
    _ <- if (record(selected) == record(retained) && selected.slot == retained.slot) Consequence.unit
      else N.invalid("Retained Session candidate differs from current ordinary validation or host policy")
  } yield retained

  def emptyCoreC(core: ActionCall.Core, value: P.SessionWorkspace): Consequence[ActionCall.Core] = for {
    reference <- SessionCandidateWorkspaceReference.fromRecordC(record(value))
    selected <- SessionCandidateWorkspace.reacquireC(core.executionContext, reference, record(value), SessionCandidateWorkspaceState.Empty)
  } yield core.copy(executionContext = selected.context)

  def observationCoreC(core: ActionCall.Core, value: P.SessionWorkspace): Consequence[ActionCall.Core] = for {
    reference <- SessionCandidateWorkspaceReference.fromRecordC(record(value))
    context <- SessionCandidateWorkspace.observationContextC(core.executionContext, reference, record(value))
  } yield core.copy(executionContext = context)

  def materializedCoreC(core: ActionCall.Core, value: P.SessionWorkspace, candidate: P.Candidate): Consequence[ActionCall.Core] = for {
    _ <- if (value.materialCandidate.contains(candidate)) Consequence.unit else N.invalid("Retained materialization candidate changed")
    material <- value.materialization.map(Consequence.success).getOrElse(N.invalid("Actual materialization receipt is absent"))
    root <- value.root.map(Consequence.success).getOrElse(N.invalid("Actual Root receipt is absent"))
    head <- value.head.map(Consequence.success).getOrElse(N.invalid("Actual Head receipt is absent"))
    restored <- RepositorySyncSessionWorkspaceReceipt.restoreC(
      RepositorySyncSessionWorkspaceReceipt.Materialized(candidate, material, root, head), value.alias)
    reference <- SessionCandidateWorkspaceReference.fromRecordC(record(value))
    selected <- SessionCandidateWorkspace.reacquireC(core.executionContext, reference, record(value),
      SessionCandidateWorkspaceState.Materialized(restored._1, restored._2, restored._3))
  } yield core.copy(executionContext = selected.context)
}
