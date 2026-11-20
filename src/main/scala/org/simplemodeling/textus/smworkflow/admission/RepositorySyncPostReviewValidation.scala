package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import java.nio.file.{Path, Paths}
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.{GitExecutionOutcome, GitIgnoreDisposition, GitIgnoreObservation,
  GitOperation, GitOperationKind, GitPath, GitSelectionId, ProcessExecutionTermination, SbtExecutionAttempt, SbtOperation, SbtTarget}
import org.goldenport.cncf.workspace.WorkspaceId
import scala.util.Try
import RepositorySyncNativeMergeValidation as C
import RepositorySyncNativeProgressStore as N
import RepositorySyncProgramImpact as I

/** Definition-owned input obligations and actual observations for post-review validation.
  * @since   Nov. 20, 2026
  * @version Nov. 20, 2026
  */
private[smworkflow] object RepositorySyncPostReviewValidation {
  enum Attempt[+A] {
    case Observed(value: A)
    case NotDispatched(detail: String)
  }

  final case class Binding(ignoreSelection: String, ignoreFile: String, lintInputs: Vector[String], lintCheckIdentity: String) {
    def valid: Boolean =
      GitSelectionId.parseC(ignoreSelection).isSuccess && GitPath.parseC(ignoreFile).isSuccess &&
        ignoreFile.split("/", -1).last == ".gitignore" && lintInputs != null && lintInputs.nonEmpty &&
        lintInputs.distinct == lintInputs && lintInputs.forall(path => path != ignoreFile && GitPath.parseC(path).isSuccess) &&
        GoalPhaseNativeMetadata.hostIdentityValid(lintCheckIdentity)
  }
  object Binding {
    given Encoder[Binding] = deriveEncoder
    private val _decoder: Decoder[Binding] = deriveDecoder
    given Decoder[Binding] = Decoder.instance(cursor => for {
      _ <- Either.cond(cursor.keys.exists(_.toSet == Set("ignoreSelection", "ignoreFile", "lintInputs", "lintCheckIdentity")),
        (), DecodingFailure("post-review validation requires exact binding fields", cursor.history))
      binding <- _decoder(cursor)
      _ <- Either.cond(binding.valid, (), DecodingFailure("invalid post-review validation binding", cursor.history))
    } yield binding)
  }

  final case class Plan(binding: Binding, check: C.Check) {
    def valid: Boolean = binding != null && binding.valid && check != null &&
      check.kind == WorkflowSbtValidation.Kind.FixCheck && check.identity == binding.lintCheckIdentity &&
      check.setupRequired == None && check.invocation == None &&
      WorkspaceId.parseC(check.workspaceIdentity).isSuccess &&
      C.Policy("post-review-validation", 1, "required", "Validate the added lint inputs", Vector(check)).validateC.isSuccess
  }
  object Plan {
    given Encoder[Plan] = deriveEncoder
    given Decoder[Plan] = deriveDecoder[Plan].emap(plan => Either.cond(plan.valid, plan, "invalid post-review validation plan"))
  }

  def selectC(policy: C.Policy, oldChecks: Vector[C.Check], newUncovered: Vector[String]): Consequence[Option[Plan]] = {
    val selected = for {
      impact <- policy.impact
      binding <- impact.postReviewValidation
      check <- policy.checks.lastOption
      plan = Plan(binding, check)
      if plan.valid && policy.validateC.isSuccess && !oldChecks.contains(check) && policy.checks == (oldChecks :+ check)
      if newUncovered.toSet == (binding.ignoreFile +: binding.lintInputs).toSet
      coverage = impact.coverage.filter(_.check == check)
      if binding.lintInputs.forall(path => coverage.exists(entry =>
        I.covers(entry.affectedPaths, path) && I.covers(entry.inputPaths, path)))
    } yield plan
    Consequence.success(selected)
  }

  final case class Ignore(path: String, disposition: String, source: Option[String], line: Option[Int], pattern: Option[String])
  object Ignore {
    given Encoder[Ignore] = deriveEncoder[Ignore].mapJson(_.mapObject(_.filter {
      case ("source" | "line" | "pattern", value) => !value.isNull
      case _ => true
    }))
    given Decoder[Ignore] = deriveDecoder
  }

  final case class GitReceipt(termination: String, exitCode: Option[Int], observations: Vector[Ignore]) {
    def accepted(plan: Plan): Boolean = plan.valid && termination == "exited" && exitCode.contains(0) &&
      observations.map(_.path) == plan.binding.lintInputs && observations.forall { observation =>
        observation.disposition == "included-by-exception" &&
          observation.source.exists(_.stripPrefix("./") == plan.binding.ignoreFile) &&
          observation.line.exists(_ > 0) && observation.pattern.exists(pattern =>
            pattern.startsWith("!") && pattern.drop(1).trim.nonEmpty && !pattern.contains('\u0000'))
      }
  }
  object GitReceipt {
    given Encoder[GitReceipt] = deriveEncoder[GitReceipt].mapJson(_.mapObject(_.filter {
      case ("exitCode", value) => !value.isNull
      case _ => true
    }))
    given Decoder[GitReceipt] = deriveDecoder
  }

  def gitBindingMatches(process: Json, workspace: String, binding: Binding): Boolean =
    binding != null && binding.valid && WorkspaceId.parseC(workspace).isSuccess &&
      Option(process).flatMap(_.hcursor.get[Vector[Json]]("gitPrograms").toOption).exists { programs =>
        programs.map(_.hcursor).filter(cursor => cursor.get[String]("target").contains(workspace) &&
          cursor.get[String]("operation").contains(GitOperationKind.CheckIgnore.wireName) &&
          cursor.get[String]("selectionId").contains(binding.ignoreSelection)) match {
          case Vector(selected) => selected.get[Vector[String]]("selectedPaths").contains(binding.lintInputs)
          case _ => false
        }
      }

  /** Qualify the ordinary host declarations before recording an effect intent. */
  def qualifyLintWorkspaceC(store: N.Store, plan: Plan): Consequence[Unit] = for {
    _ <- if (plan.valid) Consequence.unit else N.invalid("invalid post-review lint plan")
    configuration <- Option(store.core.executionContext.resolvedParameters)
      .flatMap(_.get(GoalPhaseWholePhaseCandidateRecovery.processPlanKey)).map(_.value).map(Consequence.success)
      .getOrElse(N.invalid("post-review lint requires the ordinary resolved process plan"))
    host <- RepositorySyncMergeCheckContinuation.HostPlanDecoder.decode(configuration)
    workspaces <- host.hcursor.get[Vector[Json]]("workspaces").fold(error => N.invalid(error.message), Consequence.success)
    programs <- host.hcursor.get[Vector[Json]]("sbtPrograms").fold(error => N.invalid(error.message), Consequence.success)
    identity = plan.check.workspaceIdentity
    root = store.declaration.target.root
    _ <- if (_workspace_matches(workspaces, identity, root)) Consequence.unit
      else N.invalid("post-review lint workspace must be declared exactly once at the candidate repository root")
    _ <- if (programs.count(program => program.hcursor.get[String]("target").contains(identity) &&
          program.hcursor.get[String]("operation").contains(WorkflowSbtValidation.Kind.FixCheck.wire)) == 1) Consequence.unit
      else N.invalid("post-review lint requires one declared ordinary FixCheck at the selected workspace")
    gitworkspace = RepositorySyncDirectoryList.workspace(store.core, "sm-workflow-repository")
    _ <- if (_workspace_matches(workspaces, gitworkspace, root)) Consequence.unit
      else N.invalid("post-review Git ignore workspace must be declared exactly once at the candidate repository root")
    _ <- if (gitBindingMatches(host, gitworkspace, plan.binding)) Consequence.unit
      else N.invalid("post-review Git ignore requires one declared CheckIgnore with the exact selection and ordered lint paths")
  } yield ()

  def observeGitC(store: N.Store, plan: Plan): Consequence[GitReceipt] = for {
    _ <- if (plan.valid) Consequence.unit else N.invalid("invalid post-review Git ignore plan")
    selection <- GitSelectionId.parseC(plan.binding.ignoreSelection)
    result <- WorkflowManagedProcessExecution.executeGitC(store.core, GitOperation.CheckIgnore(selection))
    process <- Option(result.processResult).filter(p => p.termination != null &&
      p.termination != ProcessExecutionTermination.TerminationUnconfirmed).map(Consequence.success)
      .getOrElse(N.uncertain("post-review Git ignore observation has no attributable termination"))
    observations <- result.outcome match {
      case GitExecutionOutcome.Succeeded => result.valueC.map(_.map(_ignore))
      case GitExecutionOutcome.Failed(_) | GitExecutionOutcome.Incomplete(_) | GitExecutionOutcome.InvalidOutput(_) =>
        Consequence.success(Vector.empty[Ignore])
      case _ => N.uncertain("post-review Git ignore observation has no attributable outcome")
    }
  } yield GitReceipt(WorkflowSbtCapture.termination(process), WorkflowSbtCapture.exitCode(process), observations)

  def observeLintC(store: N.Store, plan: Plan): Consequence[Attempt[C.Receipt]] = for {
    _ <- if (plan.valid) Consequence.unit else N.invalid("invalid post-review lint plan")
    check = plan.check
    workspace <- WorkspaceId.parseC(check.workspaceIdentity)
    attempt <- WorkflowManagedProcessExecution.repositoryValidationAttemptC(store.core, check.ordinal, check.kind, check.workspace)
    receipt <- attempt match {
      case SbtExecutionAttempt.Observed(result) if result != null && result.target == SbtTarget(workspace) &&
          result.operation == SbtOperation.FixCheck && result.processResult != null &&
          result.processResult.termination != ProcessExecutionTermination.TerminationUnconfirmed =>
        C.sbtReceiptC(check.identity, check.kind, result).map[Attempt[C.Receipt]](Attempt.Observed(_))
      case SbtExecutionAttempt.AdmissionRejected(_, _, conclusion) =>
        Consequence.success[Attempt[C.Receipt]](Attempt.NotDispatched(conclusion.show))
      case SbtExecutionAttempt.Uncertain(_, _, conclusion) =>
        N.uncertain("post-review lint outcome remains unknown: " + conclusion.show)
      case _ => N.uncertain("post-review lint has no attributable outcome for the selected FixCheck")
    }
  } yield receipt

  private def _workspace_matches(workspaces: Vector[Json], identity: String, root: Path): Boolean =
    workspaces.filter(_.hcursor.get[String]("identity").contains(identity)) match {
      case Vector(binding) => binding.hcursor.get[String]("root").toOption
        .flatMap(path => Try(Paths.get(path).normalize()).toOption).contains(root)
      case _ => false
    }

  private def _ignore(observation: GitIgnoreObservation): Ignore = observation.disposition match {
    case GitIgnoreDisposition.NoRule => Ignore(observation.path.value, "no-rule", None, None, None)
    case GitIgnoreDisposition.IncludedByException(rule) =>
      Ignore(observation.path.value, "included-by-exception", Some(rule.source), Some(rule.line), Some(rule.pattern))
    case GitIgnoreDisposition.Excluded(rule) =>
      Ignore(observation.path.value, "excluded", Some(rule.source), Some(rule.line), Some(rule.pattern))
  }
}
