package org.simplemodeling.textus.smworkflow.admission

import cats.free.Free
import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall}
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.unitofwork.{ExecUowM, UnitOfWorkOp}
import org.goldenport.cncf.workspace.*
import org.goldenport.protocol.operation.OperationResponse
import RepositorySyncNativeProgressStore as N
import RepositorySyncNativeMergeValidation as C
import RepositorySyncPurpose as P
import WorkflowManagedProcessExecution as E

/** Logical Session candidate selection and actual native materialization facts.
  * CNCF owns allocation, paths, executable admission and scoped reacquisition.
  * @since   Nov. 12, 2026
  * @version Nov. 12, 2026
  */
private[smworkflow] object RepositorySyncPurposeWorkspace {
  final case class Materialization(source: String, destination: String, candidate: String,
    selection: String, paths: Vector[String], message: Option[String], selectedPaths: Vector[String],
    outcome: String, unitValue: Boolean, process: Json)
  given Encoder[Materialization] = deriveEncoder
  given Decoder[Materialization] = deriveDecoder
  given Encoder[SessionCandidateWorkspaceRecord] = deriveEncoder
  given Decoder[SessionCandidateWorkspaceRecord] = deriveDecoder
  final case class Binding(reference: SessionCandidateWorkspaceRecord, slot: P.Slot,
    materialization: Option[Materialization] = None)
  given Encoder[Binding] = deriveEncoder[Binding].mapJson(_.mapObject(_.filter {
    case ("materialization", value) => !value.isNull
    case _ => true
  }))
  private val _binding_decoder: Decoder[Binding] = deriveDecoder
  given Decoder[Binding] = Decoder.instance(cursor =>
    if (cursor.downField("materialization").focus.exists(_.isNull))
      Left(DecodingFailure("Managed materialization cannot be explicitly null", cursor.history))
    else _binding_decoder(cursor).flatMap(value =>
      if (SessionCandidateWorkspaceReference.fromRecordC(value.reference).isSuccess &&
          value.slot.workspace == value.reference.alias &&
          GoalPhaseStepAssessment.validatePurposePolicyC(value.slot.actionPolicy).isSuccess) Right(value)
      else Left(DecodingFailure("Managed Purpose binding is incomplete or crosses its alias", cursor.history))))

  private val _setup_message = "Purpose candidate setup requires one ordinary v7 sessionCandidateWorkspaces entry for the actual component, disjoint admitted parent and alias Manage grants, source Manage grant, candidate alias Git Root/Head/index/status/check programs and ordinary typed validation programs"
  private def _rows_c(plan: Json, name: String): Consequence[Vector[Json]] =
    plan.hcursor.get[Vector[Json]](name).fold(_ => N.invalid(_setup_message + ": missing " + name), Consequence.success)
  private def _component_c(core: ActionCall.Core): Consequence[String] =
    core.component.map(value => Consequence.success(value.componentId.name))
      .getOrElse(N.invalid("Managed Purpose has no actual owning component"))

  /** Opaque native Session data is never split on colons or treated as a path. */
  private[admission] def candidateIdentity(store: N.Store, progress: P.Progress): String =
    Json.arr(Json.fromString(store.namespace), Json.fromInt(progress.cursor + 1), Json.fromInt(progress.attempt),
      progress.preparationNamespace.fold(Json.Null)(Json.fromString),
      Json.fromString(progress.candidate.get.commit)).noSpaces

  private def _expected_c(store: N.Store, progress: P.Progress, plan: Json): Consequence[SessionCandidateWorkspaceRecord] = for {
    _ <- if (progress != null && progress.candidate.nonEmpty && progress.plan.exists(_.groups.lift(progress.cursor).nonEmpty))
      Consequence.unit else N.invalid("Managed Purpose reference has no actual current prepared candidate and accepted group")
    component <- _component_c(store.core)
    _ <- if (plan.hcursor.get[String]("schema").contains("cncf.runtime-process-execution.v7")) Consequence.unit
      else N.invalid(_setup_message)
    policies <- _rows_c(plan, "sessionCandidateWorkspaces")
    selected = policies.filter(_.hcursor.get[String]("componentId").contains(component))
    row <- if (selected.size == 1) Consequence.success(selected.head) else N.invalid(_setup_message + ": missing or ambiguous component policy")
    parent <- row.hcursor.get[String]("parent").fold(_ => N.invalid(_setup_message), Consequence.success)
    alias <- row.hcursor.get[String]("alias").fold(_ => N.invalid(_setup_message), Consequence.success)
    source = RepositorySyncDirectoryList.workspace(store.core, "sm-workflow-repository")
    record = SessionCandidateWorkspaceRecord(component, source, parent, alias, store.identity, candidateIdentity(store, progress))
    _ <- SessionCandidateWorkspaceReference.fromRecordC(record)
  } yield record

  private def _selection_c(progress: P.Progress): Consequence[GitSelectionParameters] = for {
    group <- progress.plan.flatMap(_.groups.lift(progress.cursor)).map(value =>
      Consequence.success(RepositorySyncPurposeOwnership.effectiveGroup(progress, value)))
      .getOrElse(N.invalid("Managed Purpose has no current accepted group"))
    identity <- GitSelectionId.parseC(group.identity)
    paths <- group.content.foldLeft(Consequence.success(Vector.empty[GitPath])) { (prior, content) => for {
      values <- prior
      path <- GitPath.parseC(content.path)
    } yield values :+ path }
  } yield GitSelectionParameters(identity, paths, Some(group.message))

  /** Only current ordinary policies and actual prepared tree changes select work. */
  def preflightC(store: N.Store, progress: P.Progress, validation: Option[C.Policy]): Consequence[Binding] = for {
    plan <- RepositorySyncPurposeMaterializationContinuation.resolvedHostC(store.core)
    expected <- _expected_c(store, progress, plan)
    workspaces <- _rows_c(plan, "workspaces")
    grants <- _rows_c(plan, "workspaceGrants")
    _ <- if (Vector(expected.source, expected.parent, expected.alias).forall(identity =>
        workspaces.count(_.hcursor.get[String]("identity").contains(identity)) == 1 &&
        grants.exists(row => row.hcursor.get[String]("componentId").contains(expected.component) &&
          row.hcursor.get[String]("workspaceId").contains(identity) && row.hcursor.get[String]("access").contains("manage"))))
      Consequence.unit else N.invalid(_setup_message + ": missing workspace or Manage grant")
    root <- RepositorySyncManagedGitObservation.rootC(store.core)
    _ <- if (root == store.original.repositoryRoot && root == store.declaration.target.root.toString && root == progress.request.root &&
        workspaces.find(_.hcursor.get[String]("identity").contains(expected.source)).exists(_.hcursor.get[String]("root").contains(root)))
      Consequence.unit else N.invalid("Managed Purpose source root changed from its original admitted checkout")
    candidate <- progress.candidate.map(P.producerC).getOrElse(N.invalid("Managed Purpose has no actual prepared candidate"))
    parent <- GitObjectId.parseC(progress.candidate.get.parent)
    delta <- E.executeGitC(store.core, GitOperation.TreeChanges(parent, candidate.tree)).flatMap(_.valueC)
    changed = delta.map(_.path.value)
    document = store.declaration.documentInputs.exists(p => changed.forall(path =>
      RepositorySyncProgramImpact.covers(p.documentPaths, path) && !RepositorySyncProgramImpact.covers(p.consumedPaths, path)))
    policy <- validation.map(Consequence.success).getOrElse(N.invalid("Managed Purpose requires its actual ordinary validation and assessment policy"))
    _ <- policy.validateC
    assessment <- policy.assessment.map(Consequence.success).getOrElse(N.invalid("Managed Purpose ordinary assessment policy is absent"))
    _ <- RepositorySyncMergeAssessment.validateC(assessment)
    _ <- policy.setupRequired.fold(Consequence.unit)(N.invalid[Unit])
    checks <- if (document) Consequence.success(Vector.empty[C.Check]) else for {
      impact <- policy.impact.map(Consequence.success).getOrElse(N.invalid("Managed Purpose program inputs require ordinary declared coverage; no FULL is inferred"))
      _ <- RepositorySyncProgramImpact.validateC(impact)
      selected = RepositorySyncProgramImpact.select(impact, RepositorySyncDocumentInputs.Assessment(changed,
        changed.filter(path => store.declaration.documentInputs.exists(p => RepositorySyncProgramImpact.covers(p.consumedPaths, path))),
        changed.filterNot(path => store.declaration.documentInputs.exists(p =>
          RepositorySyncProgramImpact.covers(p.documentPaths, path) || RepositorySyncProgramImpact.covers(p.consumedPaths, path))),
        Vector.empty, "Actual prepared Purpose tree changes"))
      _ <- if (selected.checks.nonEmpty) Consequence.unit else N.invalid("Managed Purpose program inputs have no covered ordinary checks")
    } yield selected.checks
    _ <- if (checks.exists(check => check.ordinaryDiscovery || check.metadata.nonEmpty || check.invocation.nonEmpty))
      N.invalid("ValidationSetupRequired: this Purpose candidate route requires an actual statically selected ordinary typed check; metadata/discovery coverage and invocation execution are unavailable")
      else Consequence.unit
    programs <- _rows_c(plan, "sbtPrograms")
    processgrants <- _rows_c(plan, "grants")
    capabilities = processgrants.filter(_.hcursor.get[String]("componentId").contains(expected.component))
      .flatMap(_.hcursor.get[Vector[String]]("capabilities").getOrElse(Vector.empty))
    _ <- checks.foldLeft(Consequence.unit) { (prior, check) => prior.flatMap { _ =>
      val matches = programs.filter(row => row.hcursor.get[String]("target").contains(expected.alias) &&
        row.hcursor.get[String]("operation").contains(check.kind.wire))
      if (check.setupRequired.isEmpty && matches.size == 1 &&
          matches.head.hcursor.get[String]("capability").toOption.exists(capabilities.contains)) Consequence.unit
      else N.invalid(_setup_message + ": candidate alias has no eligible ordinary typed program/grant for " + check.identity)
    } }
    action = GoalPhaseStepAssessment.Policy(policy.identity, if (document) "documentation" else "code",
      assessment.detailedReview, if (assessment.scalaLint == "required") "required" else "off", "off", Vector.empty)
    _ <- GoalPhaseStepAssessment.validatePurposePolicyC(action)
    alias <- WorkspaceId.parseC(expected.alias)
    _ <- Vector[GitOperation[?]](GitOperation.Root, GitOperation.Head, GitOperation.IndexTree, GitOperation.Status,
      GitOperation.CheckPendingIndex(parent), GitOperation.PendingDiff(parent)).foldLeft(Consequence.unit) { (prior, operation) =>
      prior.flatMap(_ => GitExecutionPreflight.validateC(store.core.executionContext, GitTarget(alias), operation))
    }
    selection <- _selection_c(progress)
    source <- WorkspaceId.parseC(expected.source)
    _ <- GitExecutionPreflight.validateC(store.core.executionContext, GitTarget(source, Some(selection)),
      GitOperation.MaterializePurposeCandidate(selection.identity, candidate, alias))
  } yield Binding(expected, P.Slot(expected.alias, checks.map(_.copy(workspace = Some(expected.alias))), action,
    checks.map(_.identity -> expected.alias).toMap, Some(store.context.repositoryIdentity)))

  private final class Allocate(val core: ActionCall.Core) extends ProcedureActionCall {
    def runC(record: SessionCandidateWorkspaceRecord): Consequence[SessionCandidateWorkspaceSelection] = for {
      source <- WorkspaceId.parseC(record.source)
      parent <- WorkspaceId.parseC(record.parent)
      alias <- WorkspaceId.parseC(record.alias)
      program: ExecUowM[SessionCandidateWorkspaceSelection] = ConsequenceT.liftF(Free.liftF(
        UnitOfWorkOp.SessionCandidatePrepare(source, parent, alias, record.session, record.candidate)))
      result <- executeProgram(program)
    } yield result
    def execute(): Consequence[OperationResponse] = N.invalid("Managed Purpose allocation is not a public Operation")
  }
  def allocateC(core: ActionCall.Core, binding: Binding): Consequence[ActionCall.Core] =
    new Allocate(core).runC(binding.reference).flatMap { selected =>
      if (selected.reference.record == binding.reference && selected.alias.value == binding.slot.workspace)
        Consequence.success(core.copy(executionContext = selected.context))
      else N.uncertain("Managed Purpose allocation returned a foreign selection")
    }

  def materializedC(progress: P.Progress, binding: Binding, result: GitExecutionResult[Unit]): Consequence[Binding] = for {
    candidate <- P.producerC(progress.candidate.get)
    selection <- _selection_c(progress)
    source <- WorkspaceId.parseC(binding.reference.source)
    alias <- WorkspaceId.parseC(binding.reference.alias)
    _ <- if (result.target == GitTarget(source, Some(selection)) &&
        result.operation == GitOperation.MaterializePurposeCandidate(selection.identity, candidate, alias) &&
        result.outcome == GitExecutionOutcome.Succeeded && result.value.contains(()) &&
        result.processResult.termination == ProcessExecutionTermination.Exited(0)) Consequence.unit
      else N.uncertain("Managed Purpose has no matching actual successful materialization result")
  } yield binding.copy(materialization = Some(Materialization(result.target.workspaceId.value, alias.value,
    candidate.commit.value, selection.identity.value, selection.paths.map(_.value), selection.message,
    result.selectedPaths.map(_.value), result.outcome.toString, result.value.contains(()), WorkflowSbtCapture.encode(result.processResult))))

  private def _retained_result_c(progress: P.Progress, binding: Binding): Consequence[GitExecutionResult[Unit]] = for {
    evidence <- binding.materialization.map(Consequence.success).getOrElse(N.uncertain("Managed Purpose materialization remains UNKNOWN"))
    candidate <- progress.candidate.map(P.producerC).getOrElse(N.invalid("Managed Purpose candidate is absent"))
    selection <- _selection_c(progress)
    source <- WorkspaceId.parseC(binding.reference.source)
    alias <- WorkspaceId.parseC(binding.reference.alias)
    process <- WorkflowSbtCapture.decode(evidence.process).fold(N.invalid, Consequence.success)
    paths <- evidence.selectedPaths.foldLeft(Consequence.success(Vector.empty[GitPath])) { (prior, text) => for {
      values <- prior
      path <- GitPath.parseC(text)
    } yield values :+ path }
    _ <- if (evidence.source == source.value && evidence.destination == alias.value && evidence.candidate == candidate.commit.value &&
        evidence.selection == selection.identity.value && evidence.paths == selection.paths.map(_.value) && evidence.message == selection.message &&
        evidence.outcome == "Succeeded" && evidence.unitValue && process.termination == ProcessExecutionTermination.Exited(0) &&
        process.safeProgramIdentity != null && process.safeProgramIdentity.nonEmpty)
      Consequence.unit else N.invalid("Managed Purpose materialization evidence crosses its actual canonical candidate or provider result")
  } yield GitExecutionResult(GitTarget(source, Some(selection)),
    GitOperation.MaterializePurposeCandidate(selection.identity, candidate, alias), GitExecutionOutcome.Succeeded, Some(()), process, paths)

  private[admission] def requireCurrentBindingC(store: N.Store, progress: P.Progress): Consequence[Binding] = for {
    retained <- progress.managedWorkspace.map(Consequence.success)
      .getOrElse(N.invalid("Managed Purpose binding is absent"))
    proposed <- preflightC(store, progress, store.declaration.purposeValidation)
    _ <- if (proposed.reference == retained.reference && proposed.slot == retained.slot) Consequence.unit
      else N.invalid("Managed Purpose binding differs from current ordinary validation, checks or assessment policy; retained evidence is preserved")
  } yield proposed

  /** Known materialization is rechecked through current policy and actual Git calls. */
  def scopedStoreC(store: N.Store, progress: P.Progress): Consequence[N.Store] = progress.managedWorkspace match {
    case None => Consequence.success(store)
    case Some(binding) => for {
      proposed <- requireCurrentBindingC(store, progress)
      expected = proposed.reference
      result <- _retained_result_c(progress, binding)
      reference <- SessionCandidateWorkspaceReference.fromRecordC(binding.reference)
      observation <- SessionCandidateWorkspace.observationContextC(store.core.executionContext, reference, expected)
      observedcore = store.core.copy(executionContext = observation)
      root <- E.executeGitC(observedcore, GitOperation.Root, expected.alias)
      head <- E.executeGitC(observedcore, GitOperation.Head, expected.alias)
      selected <- SessionCandidateWorkspace.reacquireC(store.core.executionContext, reference, expected,
        SessionCandidateWorkspaceState.Materialized(result, root, head))
    } yield store.withCore(store.core.copy(executionContext = selected.context))
  }
}
