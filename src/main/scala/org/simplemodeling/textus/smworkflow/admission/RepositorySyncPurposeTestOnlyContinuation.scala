package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json, Printer}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.processexecution.SbtTestReassessment
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{DefinitionBinding, InstanceRecord, Lifecycle, ProgressionReference}
import org.simplemodeling.textus.smworkflow.value as V
import RepositorySyncManagedFetch as F
import RepositorySyncNativeMergeValidation as C
import RepositorySyncNativeProgressStore as N
import RepositorySyncPurpose as P
import RepositorySyncValidationContinuation as A
import WorkflowSbtValidation.Kind

/** Append one fixed behavioral check after the original complete process batch.
  * @since   Nov. 21, 2026
  * @version Nov. 21, 2026
  */
private[smworkflow] object RepositorySyncPurposeTestOnlyContinuation {
  val policy = "sm-workflow.repository-sync-purpose-additional-test-only.v1"
  val authorize = "AuthorizeAdditionalPurposeTestOnly"
  val choices = Vector(authorize, "Defer")
  val message = "The original candidate, structural result, Compile and full Test are retained, including " +
    "the canceled tests. The fixed host TestOnly is the only added check. Adoption records the selection. " +
    "Run ordinary advanceRepositorySync to execute the new check and apply the existing acceptance rule."

  final case class Target(continuationPolicy: String, sessionId: String, context: F.StartContext, repositoryRoot: String,
    frontierRevision: String, frontierCause: String, attempt: String, commit: String, tree: String,
    workspace: String, previousChecks: Vector[C.Check], check: C.Check, program: String, capability: String,
    testClasses: Vector[String], programDeclaration: String)
  object Target {
    given Encoder[Target] = deriveEncoder
    given Decoder[Target] = deriveDecoder
  }
  final case class Direction(target: Target, answer: A.Answer, reference: ContextReference)
  object Direction {
    given Encoder[Direction] = deriveEncoder
    given Decoder[Direction] = deriveDecoder
  }
  private final case class Observed(source: N.Stage, progress: P.Progress, workspace: P.SessionWorkspace,
    target: Target)

  private def _exact(value: String): Boolean =
    value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)

  private def _fixed_check(check: C.Check): Boolean =
    check.kind == Kind.TestOnly && !check.ordinaryDiscovery && check.setupRequired.isEmpty &&
      check.metadata.isEmpty && check.invocation.isEmpty

  private def _valid_target(target: Target): Boolean =
    target.continuationPolicy == policy &&
      Vector(target.sessionId, target.attempt, target.repositoryRoot, target.frontierCause,
        target.workspace, target.program, target.capability).forall(_exact) &&
      _exact(target.context.participantIdentity) && target.frontierRevision.matches("[1-9][0-9]*") &&
      Vector(target.commit, target.tree).forall(_.matches("[0-9a-f]{40}|[0-9a-f]{64}")) &&
      io.circe.parser.parse(target.programDeclaration).toOption.exists(program =>
        program.asObject.exists(_.nonEmpty) && _program_json(program) == target.programDeclaration) &&
      target.frontierCause.startsWith("native-repository:" + target.sessionId + ":") &&
      target.previousChecks.exists(_.kind == Kind.Compile) && target.previousChecks.exists(_.kind == Kind.Test) &&
      !target.previousChecks.exists(check => check.kind == Kind.TestOnly || check.identity == target.check.identity) &&
      _fixed_check(target.check) && target.testClasses.nonEmpty && target.testClasses.distinct == target.testClasses &&
      target.testClasses.forall(_exact)

  def decodeTarget(value: Json): Either[String, Target] = value.as[Target].left.map(_.message).flatMap(target =>
    Either.cond(_valid_target(target) && target.asJson == value, target,
      "Additional Purpose TestOnly requires the exact native candidate and fixed check target"))

  def decodeAnswer(value: Json): Either[String, A.Answer] = value.as[A.Answer].left.map(_.message).flatMap(answer =>
    Either.cond(answer.asJson == value && _valid_answer(answer), answer,
      "Additional Purpose TestOnly requires an exact choice and reason without an uncertainty assertion"))

  private def _valid_answer(answer: A.Answer): Boolean =
    choices.contains(answer.choice) && _exact(answer.rationale) &&
      !answer.priorExecutionEnded && !answer.acceptOriginalUncertainty

  def bindingC(component: String, target: Target, choice: String, rationale: String, participant: String,
    reference: ContextReference, ended: Boolean, accepted: Boolean): Consequence[Json] =
    if (!_exact(component) || !_valid_target(target) || participant != target.context.participantIdentity ||
        reference == null || !_exact(reference.identity) || !_exact(reference.revision) ||
        !_valid_answer(A.Answer(choice, rationale, ended, accepted)))
      N.invalid("Additional Purpose TestOnly has invalid target, participant or Human direction")
    else Consequence.success(Json.obj("componentInstance" -> component.asJson,
      "repositoryValidationContinuation" -> Json.obj("target" -> target.asJson,
        "answer" -> Json.obj("choice" -> choice.asJson, "rationale" -> rationale.asJson,
          "participant" -> participant.asJson, "reference" -> reference.asJson,
          "priorExecutionEnded" -> ended.asJson, "acceptOriginalUncertainty" -> accepted.asJson))))

  private def _key(target: Target): String = "purpose-additional-test-only-" + target.frontierRevision

  private def _load_c(store: N.Store, current: InstanceRecord, key: String): Consequence[N.Stage] = for {
    stage <- store.scoped("").loadC(key).flatMap(_.map(Consequence.success)
      .getOrElse(N.invalid("Additional Purpose TestOnly lacks native receipt " + key)))
    actual <- RepositorySyncPurposeContentContinuation.prefixC(current, stage.record.revision.value.toString)
    _ <- if (stage.record == actual &&
        actual.causation.value == "native-repository:" + store.identity + ":" + store.scoped("").key(key))
      Consequence.unit else N.invalid("Additional Purpose TestOnly receipt crosses its native history")
  } yield stage

  private def _current_c(store: N.Store, current: InstanceRecord): Consequence[N.Stage] = {
    val prefix = "native-repository:" + store.identity + ":"
    if (current.lifecycle == Lifecycle.Active && current.suspension.isEmpty &&
        current.currentProgression.contains(ProgressionReference("sync.PurposeMaterialized")) &&
        current.causation.value.startsWith(prefix))
      _load_c(store, current, current.causation.value.stripPrefix(prefix))
    else N.invalid("Additional Purpose TestOnly requires the actual materialized Purpose frontier")
  }

  private[admission] def prefixEligible(receipts: Vector[C.Receipt], checks: Vector[C.Check]): Boolean = {
    val full = checks.zip(receipts.drop(1)).collect { case (check, receipt) if check.kind == Kind.Test => receipt }
    receipts.size == checks.size + 1 &&
      receipts.headOption.exists(r => r.identity == "index-local" && r.exitCode.contains(0)) &&
      checks.exists(_.kind == Kind.Compile) && !checks.exists(_.kind == Kind.TestOnly) && full.nonEmpty &&
      C.successful(receipts, merge = false, selected = checks) &&
      receipts.drop(1).forall(_.processObservation.nonEmpty) && full.forall(_.processObservation.exists { process =>
        val report = SbtTestReassessment.assess(Vector.empty, process).observation
        report.captureComplete && report.completedSuites.exists(_ > 0) &&
          report.testsRun.exists(total => total > 0 && report.succeeded.contains(total)) &&
          report.abortedSuites.contains(0L) && report.failed.contains(0L) &&
          report.canceled.exists(_ > 0) && report.failureDetails.isEmpty
      })
  }

  private def _authenticate_batch_c(store: N.Store, source: N.Stage, progress: P.Progress): Consequence[Unit] = {
    val prefix = RepositorySyncPurposeRuntime.attemptPrefix(progress)
    val keys = Vector("structural-intent" -> "structural") ++ progress.selection.get.checks.indices.map { index =>
      val name = "check-" + index
      (name + "-intent") -> (name + "-result")
    }
    keys.zipWithIndex.foldLeft(Consequence.success(Option.empty[N.Stage])) { case (prior, ((intentkey, resultkey), index)) =>
      for {
        previous <- prior
        intent <- _load_c(store, source.record, store.key(prefix + "-" + intentkey))
        result <- _load_c(store, source.record, store.key(prefix + "-" + resultkey))
        _ <- if (intent.payload.stage == "PurposeChecking" && result.payload.stage == "PurposeMaterialized" &&
            result.record.revision == intent.record.revision.next &&
            previous.forall(_.record.revision.next == intent.record.revision) &&
            intent.payload.purposes.exists(_.copy(receipts = progress.receipts, reusable = progress.reusable) == progress) &&
            result.payload.purposes.contains(progress.copy(receipts = progress.receipts.take(index + 1))) &&
            intent.payload.purposes.exists(_.receipts == progress.receipts.take(index)))
          Consequence.unit else N.invalid("Additional Purpose TestOnly has a noncontiguous original check intent or result")
      } yield Some(result)
    }.flatMap(last => if (last.contains(source)) Consequence.unit
      else N.invalid("Additional Purpose TestOnly does not follow the exact final original check receipt"))
  }

  // Keep logical environment keys outside public Record naming normalization.
  private def _program_json(program: Json): String =
    Printer.noSpaces.copy(sortKeys = true).print(program)

  private def _fixed_program_c(store: N.Store, workspace: P.SessionWorkspace, check: C.Check):
      Consequence[(String, String, Vector[String], Json)] = for {
    _ <- if (_fixed_check(check) && workspace.slot.checkWorkspaces.getOrElse(check.identity, workspace.alias) == workspace.alias)
      Consequence.unit else N.invalid("Additional TestOnly must target the original Session candidate's fixed alias")
    plan <- RepositorySyncPurposeMaterializationContinuation.hostPlanC(store.core)
    programs <- plan.hcursor.get[Vector[Json]]("sbtPrograms").fold(e => N.invalid(e.message), Consequence.success)
    matches = programs.filter(row => row.hcursor.get[String]("operation").contains("test-only") &&
      row.hcursor.get[String]("target").contains(workspace.alias))
    program <- if (matches.size == 1) Consequence.success(matches.head)
      else N.invalid("Configure one fixed SBT TestOnly for the original Session candidate alias: " + workspace.alias)
    identity <- program.hcursor.get[String]("identity").fold(e => N.invalid(e.message), Consequence.success)
    capability <- program.hcursor.get[String]("capability").fold(e => N.invalid(e.message), Consequence.success)
    classes <- program.hcursor.get[Vector[String]]("testClasses").fold(e => N.invalid(e.message), Consequence.success)
    discovery <- program.hcursor.get[Option[Boolean]]("discoveredTests").fold(e => N.invalid(e.message), Consequence.success)
    grants <- plan.hcursor.get[Vector[Json]]("grants").fold(e => N.invalid(e.message), Consequence.success)
    _ <- if (_exact(identity) && _exact(capability) && !discovery.contains(true) && classes.nonEmpty &&
        classes.distinct == classes && classes.forall(_exact) &&
        grants.exists(row => row.hcursor.get[String]("componentId").contains(workspace.component) &&
          row.hcursor.get[Vector[String]]("capabilities").toOption.exists(_.contains(capability))))
      Consequence.unit else N.invalid("Additional Purpose TestOnly requires a granted fixed TestOnly with explicit test classes")
  } yield (identity, capability, classes, program)

  private def _observe_c(store: N.Store, source: N.Stage, validation: C.Policy): Consequence[Observed] = for {
    progress <- source.payload.purposes.filter(p => p.assessment.isEmpty && p.review.isEmpty).map(Consequence.success)
      .getOrElse(N.invalid("An unassessed materialized Purpose is required"))
    candidate <- progress.candidate.map(Consequence.success).getOrElse(N.invalid("Purpose candidate is absent"))
    retained <- progress.sessionWorkspace.filter(_.root.nonEmpty).map(Consequence.success)
      .getOrElse(N.invalid("The materialized Session candidate binding is absent"))
    selection <- progress.selection.filter(_.depth == "FULL").map(Consequence.success)
      .getOrElse(N.invalid("A retained FULL Purpose selection is required"))
    _ <- if (retained.slot.actionPolicy.kind == "code" && progress.interruption.isEmpty &&
        progress.hostContinuation.forall(direction => !direction.target.purpose.exists(p =>
          p.cursor == progress.cursor && p.attempt == progress.attempt && p.slotCursor == progress.slotCursor)) &&
        _original_gate_requires_test_only(progress, selection, retained.slot))
      Consequence.unit else N.invalid("The original Purpose batch must be complete with only its behavioral acceptance missing")
    _ <- _authenticate_batch_c(store, source, progress)
    resolved <- RepositorySyncSessionWorkspace.resolveC(store, progress, Some(validation))
    check <- RepositorySyncFormalFailureContinuation.proposedChecks(selection.checks, validation, Some(resolved.slot))
      .map(_.drop(selection.checks.size)).collect { case Vector(value) => value }
      .filter(_fixed_check).map(Consequence.success)
      .getOrElse(N.invalid("Declare one missing fixed TestOnly in the current ordinary Purpose validation selection"))
    updated = selection.checks :+ check
    _ <- if (validation.impact.exists(_.fullChecks == updated) &&
        (retained.slot == resolved.slot || retained.slot.copy(checks = resolved.slot.checks) == resolved.slot &&
          resolved.slot.checks.filterNot(_ == check) == retained.slot.checks) &&
        RepositorySyncSessionWorkspace.record(retained) == RepositorySyncSessionWorkspace.record(resolved))
      Consequence.unit else N.invalid("Additional Purpose TestOnly cannot change the original workspace, order or any other check")
    program <- _fixed_program_c(store, retained, check)
    _ <- RepositorySyncPurposeRuntime.candidateWorkspaceC(store, retained.slot, candidate, Some(retained))
    target = Target(policy, store.identity, store.context, source.payload.root, source.record.revision.value.toString,
      source.record.causation.value, RepositorySyncPurposeRuntime.attemptPrefix(progress), candidate.commit, candidate.tree,
      retained.alias, selection.checks, check, program._1, program._2, program._3, _program_json(program._4))
  } yield Observed(source, progress, retained.copy(slot = resolved.slot), target)

  private def _original_gate_requires_test_only(progress: P.Progress, selection: RepositorySyncProgramImpact.Selection,
    slot: P.Slot): Boolean =
    selection.reused.isEmpty && progress.reusable.isEmpty && prefixEligible(progress.receipts, selection.checks) &&
      (slot.actionPolicy.scalaLint != "required" || selection.checks.exists(_.kind == Kind.FixCheck))

  private def _description(target: Target, component: String, adopted: Boolean):
      V.RepositorySyncValidationContinuationDescriptionResult =
    V.RepositorySyncValidationContinuationDescriptionResult(target.sessionId,
      if (adopted) "already-adopted" else "adoption-required", message, component,
      GoalPhaseNativeMetadata.record(target.asJson), if (adopted) Vector.empty[String] else choices, A.adoptionOperation)

  def describeC(session: F.Session, declaration: F.Declaration, core: ActionCall.Core, validation: C.Policy,
    acknowledge: String => Consequence[Unit]): Consequence[V.RepositorySyncValidationContinuationDescriptionResult] = for {
    store <- N.bindC(session, declaration, core, acknowledge)
    source <- _current_c(store, session.started.current)
    component = core.component.get.core.instanceId.canonicalKey
    result <- source.payload.purposeAdditionalTestOnly match {
      case Some(direction) if source.record.causation.value == "native-repository:" + store.identity + ":" + store.key(_key(direction.target)) =>
        authenticateC(store, source).map(_ => _description(direction.target, component, adopted = true))
      case None => _observe_c(store, source, validation).map(value => _description(value.target, component, adopted = false))
      case _ => N.invalid("The additional Purpose TestOnly already has an actual check result")
    }
  } yield result

  def adoptC(input: V.RepositorySyncValidationContinuationAdoptionInput, target: Target, declaration: F.Declaration,
    definition: DefinitionBinding, core: ActionCall.Core, validation: C.Policy, installation: A.Installation,
    acknowledge: String => Consequence[Unit]): Consequence[V.RepositorySyncValidationContinuationAdoptionResult] = for {
    _ <- if (input.sessionId == target.sessionId && input.reference != null && input.executionEvidence != null &&
        input.executionEvidence.workerIdentity != null &&
        input.executionEvidence.workerIdentity.contains(target.context.participantIdentity) &&
        input.executionEvidence.references != null && input.executionEvidence.references.exists(ref =>
          ref != null && ref.identity == input.reference.identity && ref.revision == input.reference.revision))
      Consequence.unit else N.invalid("Additional Purpose TestOnly evidence has a foreign Session or participant")
    reference = ContextReference(input.reference.identity, input.reference.revision)
    answer = A.Answer(input.choice, input.rationale, input.priorExecutionEnded, input.acceptOriginalUncertainty)
    binding <- bindingC(core.component.get.core.instanceId.canonicalKey, target, answer.choice, answer.rationale,
      target.context.participantIdentity, reference, answer.priorExecutionEnded, answer.acceptOriginalUncertainty)
    _ <- installation.evidence.admitC(core, reference, binding)
    session <- F.sessionC(input.sessionId, declaration, definition, core)
    store <- N.bindC(session, declaration, core, acknowledge)
    source <- _current_c(store, session.started.current)
    outcome <- source.payload.purposeAdditionalTestOnly match {
      case Some(direction) if direction.target == target && direction.answer == answer && direction.reference == reference &&
          source.record.causation.value == "native-repository:" + store.identity + ":" + store.key(_key(target)) =>
        authenticateC(store, source).map(_ => "already-adopted")
      case Some(_) => N.invalid("The additional Purpose TestOnly direction is stale or conflicting")
      case None => for {
        actual <- _observe_c(store, source, validation)
        _ <- if (actual.target == target) Consequence.unit
          else N.invalid("Additional Purpose TestOnly target drifted; describe again")
        result <- if (answer.choice == "Defer") Consequence.success("deferred") else {
          val selection = actual.progress.selection.get
          val progress = actual.progress.copy(selection = Some(selection.copy(checks = selection.checks :+ target.check)),
            sessionWorkspace = Some(actual.workspace))
          store.saveC(_key(target), "purpose-progress", source.record, source.payload.copy(
            purposes = Some(progress), purposeAdditionalTestOnly = Some(Direction(target, answer, reference))))
            .map(_ => "adopted")
        }
      } yield result
    }
  } yield V.RepositorySyncValidationContinuationAdoptionResult(input.sessionId, outcome != "deferred", outcome, message)

  def authenticateC(store: N.Store, stage: N.Stage): Consequence[Unit] = stage.payload.purposeAdditionalTestOnly match {
    case None => Consequence.unit
    case Some(direction) => for {
      current <- stage.payload.purposes.map(Consequence.success).getOrElse(N.invalid("The extended Purpose is absent"))
      target = direction.target
      _ <- if (_valid_target(target) && direction.answer.choice == authorize && _valid_answer(direction.answer) &&
          target.sessionId == store.identity && target.context == store.context && target.repositoryRoot == stage.payload.root)
        Consequence.unit else N.invalid("Additional Purpose TestOnly direction has a foreign Session or scope")
      adopted <- _load_c(store, stage.record, store.key(_key(target)))
      predecessor <- RepositorySyncPurposeContentContinuation.prefixC(stage.record, target.frontierRevision)
      prefix = "native-repository:" + store.identity + ":"
      source <- _load_c(store, stage.record, target.frontierCause.stripPrefix(prefix))
      original <- source.payload.purposes.map(Consequence.success).getOrElse(N.invalid("The original Purpose check batch is absent"))
      selected <- adopted.payload.purposes.map(Consequence.success).getOrElse(N.invalid("The adopted Purpose selection is absent"))
      workspace <- current.sessionWorkspace.map(Consequence.success).getOrElse(N.invalid("The extended Purpose workspace is absent"))
      program <- _fixed_program_c(store, workspace, target.check)
      _ <- if (source.record == predecessor && adopted.record.revision == predecessor.revision.next &&
          adopted.payload.purposeAdditionalTestOnly.contains(direction) &&
          RepositorySyncPurposeRuntime.attemptPrefix(current) == target.attempt &&
          original.candidate.exists(c => c.commit == target.commit && c.tree == target.tree) &&
          current.candidate == original.candidate && selected.sessionWorkspace.contains(workspace) &&
          workspace.alias == target.workspace && program._1 == target.program && program._2 == target.capability &&
          program._3 == target.testClasses && _program_json(program._4) == target.programDeclaration &&
          current.selection == selected.selection &&
          selected.selection.exists(_.checks == (target.previousChecks :+ target.check)) &&
          selected.receipts == original.receipts && current.receipts.startsWith(original.receipts) &&
          current.receipts.size <= original.receipts.size + 1 &&
          current.reusable == original.reusable)
        Consequence.unit else N.invalid("Additional Purpose TestOnly no longer binds the exact original batch, candidate or host")
      _ <- if (current.receipts.size == original.receipts.size) Consequence.unit
        else _authenticate_added_c(store, stage, adopted, selected, current, target)
    } yield ()
  }

  private def _authenticate_added_c(store: N.Store, stage: N.Stage, adopted: N.Stage, selected: P.Progress,
    current: P.Progress, target: Target): Consequence[Unit] = {
    val key = target.attempt + "-check-" + target.previousChecks.size
    for {
      intent <- _load_c(store, stage.record, store.key(key + "-intent"))
      result <- _load_c(store, stage.record, store.key(key + "-result"))
      _ <- if (intent.payload.stage == "PurposeChecking" && result.payload.stage == "PurposeMaterialized" &&
          adopted.record.revision.next == intent.record.revision && intent.record.revision.next == result.record.revision &&
          intent.payload.purposes.contains(selected) &&
          result.payload.purposes.contains(selected.copy(receipts = current.receipts)) &&
          current.receipts.last.identity == target.check.identity)
        Consequence.unit else N.invalid("Additional Purpose TestOnly has no exact adjacent native check result")
    } yield ()
  }
}
