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
  *  version Oct. 14, 2026
  *  version Oct. 16, 2026
  * @version Oct. 18, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object ThreeWorkflowHostParameters extends ThreeWorkflowLauncherAssembly.HostDecoder {
  val schema = "sm-workflow.three-workflow-host.v1"
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
    Option(value).toRight("Three-workflow host definition is absent")
      .flatMap(_.as[Definition].left.map(_.message))
      .flatMap(d => Either.cond(d.schemaVersion == schema, d, "Three-workflow host schema differs"))
      .fold(Consequence.configurationInvalid(_), Consequence.success)

  val definition = ComponentParameterKey.required("sm-workflow.host.definition",
    ComponentParameterDecoder[Definition] {
      case value: ConfigurationValue.ObjectValue => decodeDefinitionC(json(value))
      case ConfigurationValue.StringValue(value) => io.circe.parser.parse(value)
        .fold(_ => Consequence.configurationInvalid("Three-workflow host JSON is invalid"), decodeDefinitionC)
      case _ => Consequence.configurationInvalid("Three-workflow host requires an explicit structured definition")
    })
  val declarations: Vector[ComponentParameterKey[?]] =
    GoalPhaseRuntimeParameters.declarations ++ Vector(definition, WorkflowExecutionProfileParameters.declaration,
      GoalPhaseNativeExecution.declaration, GoalPhaseAcceptanceBoundary.declaration, GoalPhaseFindingRecording.declaration,
      RepositorySyncNativeExecution.declaration, SplitPhaseNativeExecution.declaration)
  lazy val factory = ThreeWorkflowLauncherAssembly.factory(declarations, Vector.empty, this)

  def decodeC(creation: ComponentCreate, parameters: ComponentInitializationParameters,
    context: ExecutionContext): Consequence[ThreeWorkflowLauncherAssembly.Host] =
    if (creation == null || parameters == null || context == null)
      Consequence.configurationInvalid("Three-workflow host requires its ordinary creation capsule")
    else for {
      resolved <- parameters.resolve(definition)
      settings <- resolved.value.map(Consequence.success).getOrElse(
        Consequence.configurationInvalid("Three-workflow host definition is not configured"))
      persistence <- GoalPhaseRuntimeParameters.decodeC(parameters)
      executionprofiles <- WorkflowExecutionProfileParameters.resolveC(parameters)
      executionorigin <- GoalPhaseNativeExecution.resolveOriginC(parameters)
      repositoryclassifications <- RepositorySyncNativeExecution.resolveC(parameters)
      splitclassifications <- SplitPhaseNativeExecution.resolveC(parameters)
      _ <- SplitPhaseNativeExecution.validateC(splitclassifications, executionprofiles, executionorigin)
      projectacceptance <- GoalPhaseAcceptanceBoundary.resolveProjectC(parameters)
      findingpolicy <- GoalPhaseFindingRecording.resolvePolicyC(parameters)
      target <- targetC(settings.target)
      evidence <- NativeHostEvidence.bindC(creation.configuration, persistence)
      repository = RepositorySyncManagedFetch.Declaration(persistence.configuration, persistence.instances,
        persistence.continuations, settings.repository.input, target, executionprofiles, executionorigin, repositoryclassifications)
      ports = new ThreeWorkflowHostEvidencePorts(evidence)
      closure = GoalPhaseNativeRepositoryClosure.Installation(repository,
        ports.phaseClosure, settings.repository.validation, ports.convergence,
        settings.repository.conflicts, Some(RepositorySyncNativeMergeReview.Installation(settings.repository.review,
          ports.repositoryReview)), Some(RepositorySyncHumanConflict.Installation(ports.repositoryHuman)))
      goal = GoalPhaseNativeRun.Declaration(persistence.configuration, persistence.instances, persistence.continuations,
        settings.goal.input, target, settings.goal.candidatePaths, Some(settings.goal.stepValidation),
        Some(GoalPhaseNativeReview.Installation(settings.goal.review, ports.goalReview)),
        Some(GoalPhaseNativeClosure.Installation(settings.goal.closure, ports.stepClosure)),
        Some(settings.goal.phaseValidation), Some(closure), Some(GoalPhaseOrderedHumanDecision.Installation(ports.goalHuman)),
        executionprofiles, executionorigin, projectacceptance, findingpolicy)
      _ <- GoalPhaseNativeRun.validateC(goal)
      splitCatalog <- SplitPhaseRuntimeCatalog.loadC()
      split = SplitPhaseManagedStartAssembly.Declaration(persistence.configuration, persistence.instances, persistence.continuations,
        executionprofiles, executionorigin, splitclassifications)
    } yield ThreeWorkflowLauncherAssembly.Host(goal, split, splitCatalog, ports.splitStart, ports.splitAnswer)

  private def targetC(value: Target): Consequence[RepositoryGitRuntimePrograms.Target] =
    try {
      val root = Paths.get(value.root)
      if (!root.isAbsolute || root.normalize != root)
        Consequence.configurationInvalid("Three-workflow repository root must be absolute and normalized")
      else Consequence.success(RepositoryGitRuntimePrograms.Target(root, value.localBranch, value.remote,
        value.remoteBranch, value.endpoint))
    } catch { case NonFatal(_) => Consequence.configurationInvalid("Three-workflow repository target is invalid") }
}
