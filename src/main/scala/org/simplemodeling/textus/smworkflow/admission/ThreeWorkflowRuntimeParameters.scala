package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.nio.file.Paths
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.config.{ComponentInitializationParameters, ComponentParameterDecoder, ComponentParameterKey}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.workflow.WorkflowExecutionProfileParameters
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms
import org.simplemodeling.textus.smworkflow.value.{GoalPhaseStartInput, RepositorySyncStartInput}

/** Ordinary installed composition. These are fixed targets and policies, not
  * command grants or a declaration that any review/Human result was admitted.
  * Storage/process bootstrap has already been performed by the owning Subsystem.
  * @since   Oct.  7, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object ThreeWorkflowRuntimeParameters extends ThreeWorkflowLauncherAssembly.RuntimeCompositionDecoder {
  val schema = "sm-workflow.three-workflow-runtime.v1"
  final case class Target(root: String, localBranch: String, remote: String, remoteBranch: String, endpoint: String)
  final case class Goal(input: GoalPhaseStartInput, candidatePaths: Vector[String],
    stepValidation: GoalPhaseNativeValidation.Policy, phaseValidation: GoalPhaseNativeValidation.Policy,
    review: GoalPhaseNativeReview.Policy, closure: GoalPhaseNativeClosure.Policy)
  final case class Repository(input: RepositorySyncStartInput,
    validation: RepositorySyncNativeMergeValidation.Policy, conflicts: RepositorySyncNativeConflictInput.Policy,
    review: RepositorySyncNativeMergeReview.Policy)
  final case class Definition(schemaVersion: String, target: Target, goal: Goal, repository: Repository)

  // Bottom-up derivation also bounds compiler expansion for the nested policies.
  private given Encoder[Goal] = deriveEncoder
  private given Decoder[Goal] = deriveDecoder
  private given Encoder[Repository] = deriveEncoder
  private given Decoder[Repository] = deriveDecoder

  private def json(value: ConfigurationValue): Json = value match {
    case ConfigurationValue.StringValue(v) => Json.fromString(v)
    case ConfigurationValue.NumberValue(v) => Json.fromBigDecimal(v)
    case ConfigurationValue.BooleanValue(v) => Json.fromBoolean(v)
    case ConfigurationValue.ListValue(v) => Json.fromValues(v.map(json))
    case ConfigurationValue.ObjectValue(v) => Json.obj(v.toVector.map((k, v) => k -> json(v))*)
    case ConfigurationValue.NullValue => Json.Null
  }
  def encode(value: Definition): Json = value.asJson
  def decodeDefinitionC(value: Json): Consequence[Definition] =
    Option(value).toRight("Three-workflow runtime definition is absent")
      .flatMap(_.as[Definition].left.map(_.message))
      .flatMap(d => Either.cond(d.schemaVersion == schema, d, "Three-workflow runtime schema differs"))
      .fold(Consequence.configurationInvalid(_), Consequence.success)

  val definition = ComponentParameterKey.required("sm-workflow.runtime.definition",
    ComponentParameterDecoder[Definition] {
      case value: ConfigurationValue.ObjectValue => decodeDefinitionC(json(value))
      case ConfigurationValue.StringValue(value) => io.circe.parser.parse(value)
        .fold(_ => Consequence.configurationInvalid("Three-workflow runtime JSON is invalid"), decodeDefinitionC)
      case _ => Consequence.configurationInvalid("Three-workflow runtime requires an explicit structured definition")
    })
  val completionPolicy: ComponentParameterKey[String] = ComponentParameterKey.optional(
    "sm-workflow.goal-phase.completion-policy", ComponentParameterDecoder {
      case ConfigurationValue.StringValue(value) if GoalPhasePostCompletionSync.policies.contains(value) =>
        Consequence.success(value)
      case _ => Consequence.configurationInvalid("GoalPhase completion policy is unsupported")
    })
  val declarations: Vector[ComponentParameterKey[?]] =
    GoalPhaseRuntimeParameters.declarations ++ Vector(definition, WorkflowExecutionProfileParameters.declaration,
      GoalPhaseNativeExecution.declaration, GoalPhaseAcceptanceBoundary.declaration, GoalPhaseFindingRecording.declaration,
      RepositorySyncNativeExecution.declaration, SplitPhaseNativeExecution.declaration, DevelopmentJournal.declaration, CommonEvidence.declaration,
      completionPolicy)
  lazy val factory = ThreeWorkflowLauncherAssembly.factory(declarations, Vector.empty, this)

  def decodeC(creation: ComponentCreate, parameters: ComponentInitializationParameters,
    context: ExecutionContext): Consequence[ThreeWorkflowLauncherAssembly.RuntimeComposition] =
    if (creation == null || parameters == null || context == null)
      Consequence.configurationInvalid("Three-workflow runtime requires its ordinary creation capsule")
    else for {
      resolved <- parameters.resolve(definition)
      settings <- resolved.value.map(Consequence.success).getOrElse(
        Consequence.configurationInvalid("Three-workflow runtime definition is not configured"))
      persistence <- GoalPhaseRuntimeParameters.decodeC(parameters)
      selectedpolicy <- parameters.resolve(completionPolicy).map(_.value.getOrElse(GoalPhasePostCompletionSync.localPolicy))
      executionprofiles <- WorkflowExecutionProfileParameters.resolveC(parameters)
      executionorigin <- GoalPhaseNativeExecution.resolveOriginC(parameters)
      repositoryclassifications <- RepositorySyncNativeExecution.resolveC(parameters)
      splitclassifications <- SplitPhaseNativeExecution.resolveC(parameters)
      _ <- SplitPhaseNativeExecution.validateC(splitclassifications, executionprofiles, executionorigin)
      projectacceptance <- GoalPhaseAcceptanceBoundary.resolveProjectC(parameters)
      findingpolicy <- GoalPhaseFindingRecording.resolvePolicyC(parameters)
      journalpolicy <- DevelopmentJournal.resolvePolicyC(parameters, persistence.instances, persistence.continuations)
      evidencepolicy <- CommonEvidence.resolvePolicyC(parameters, Vector(persistence.instances, persistence.continuations) ++ journalpolicy.map(_.collection))
      target <- targetC(settings.target)
      evidence <- NativeExternalEvidence.bindC(creation.configuration, persistence)
      repository = RepositorySyncManagedFetch.Declaration(persistence.configuration, persistence.instances,
        persistence.continuations, settings.repository.input, target, executionprofiles, executionorigin, repositoryclassifications)
      ports = new ThreeWorkflowExternalEvidencePorts(evidence)
      closure = GoalPhaseNativeRepositoryClosure.Installation(repository,
        ports.phaseClosure, settings.repository.validation, ports.convergence,
        settings.repository.conflicts, Some(RepositorySyncNativeMergeReview.Installation(settings.repository.review,
          ports.repositoryReview)), Some(RepositorySyncHumanConflict.Installation(ports.repositoryHuman)))
      goal = GoalPhaseNativeRun.Declaration(persistence.configuration, persistence.instances, persistence.continuations,
        settings.goal.input, target, settings.goal.candidatePaths, Some(settings.goal.stepValidation),
        Some(GoalPhaseNativeReview.Installation(settings.goal.review, ports.goalReview)),
        Some(GoalPhaseNativeClosure.Installation(settings.goal.closure, ports.stepClosure)),
        Some(settings.goal.phaseValidation), Some(closure), Some(GoalPhaseOrderedHumanDecision.Installation(ports.goalHuman)),
        executionprofiles, executionorigin, projectacceptance, findingpolicy,
        Some(GoalPhaseValidationRecovery.Installation(ports.validationRecovery)), journalpolicy, evidencepolicy,
        Some(GoalPhaseRemainingPlanAdoption.Installation(ports.remainingPlanAdoption)),
        Some(GoalPhaseSourceHistoryRecovery.Installation(ports.sourceHistoryRecovery)),
        Some(GoalPhaseClosureRecovery.Installation(ports.closureRecovery)),
        Some(GoalPhaseCandidateRecovery.Installation(ports.candidateRecovery)),
        Some(GoalPhaseCandidateObservationContinuation.Installation(ports.candidateObservationContinuation)),
        Some(GoalPhaseWholePhaseCandidateRecovery.Installation(ports.wholePhaseCandidateRecovery)),
        Some(GoalPhaseFailedPhaseRepairAdoption.Installation(ports.failedPhaseRepairAdoption)),
        Some(GoalPhaseCompletionPolicyAdoption.Installation(ports.completionPolicyAdoption)), selectedpolicy)
      _ <- GoalPhaseNativeRun.validateC(goal)
      splitCatalog <- SplitPhaseRuntimeCatalog.loadC()
      split = SplitPhaseManagedStartAssembly.Declaration(persistence.configuration, persistence.instances, persistence.continuations,
        executionprofiles, executionorigin, splitclassifications)
    } yield ThreeWorkflowLauncherAssembly.RuntimeComposition(goal, split, splitCatalog, ports.splitStart, ports.splitAnswer)

  private def targetC(value: Target): Consequence[RepositoryGitRuntimePrograms.Target] =
    try {
      val root = Paths.get(value.root)
      if (!root.isAbsolute || root.normalize != root)
        Consequence.configurationInvalid("Three-workflow repository root must be absolute and normalized")
      else Consequence.success(RepositoryGitRuntimePrograms.Target(root, value.localBranch, value.remote,
        value.remoteBranch, value.endpoint))
    } catch { case NonFatal(_) => Consequence.configurationInvalid("Three-workflow repository target is invalid") }
}
