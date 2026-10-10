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
  * @version Oct.  8, 2026
  */
private[smworkflow] object ThreeWorkflowHostParameters extends ThreeWorkflowLauncherAssembly.HostDecoder {
  val schema = "sm-workflow.three-workflow-host.v1"
  final case class Target(root: String, localBranch: String, remote: String, remoteBranch: String, endpoint: String)
  final case class Goal(input: GoalPhaseStartInput, candidatePaths: Vector[String],
    stepValidation: GoalPhaseNativeValidation.Policy, phaseValidation: GoalPhaseNativeValidation.Policy,
    review: GoalPhaseNativeReview.Policy, closure: GoalPhaseNativeClosure.Policy)
  final case class Repository(input: RepositorySyncStartInput, closureSelection: String,
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
    Option(value).filter(_definition_shape).toRight("Three-workflow host declared fields differ")
      .flatMap(_.as[Definition].left.map(_.message))
      .flatMap(d => Either.cond(d.schemaVersion == schema, d, "Three-workflow host schema differs"))
      .fold(Consequence.configurationInvalid(_), Consequence.success)

  // Shape belongs to this input boundary; typed decoding owns field values.
  // A valid value need not be the serializer's exact presentation (e.g. Set
  // members may repeat). This does not admit host policies or command grants.
  private def _object_shape(value: Json, names: Set[String],
    nested: Map[String, Json => Boolean] = Map.empty): Boolean =
    value.asObject.exists(fields => fields.keys.toSet == names &&
      nested.forall((name, shape) => fields(name).exists(shape)))
  private def _check_array_shape(value: Json): Boolean =
    value.asArray.exists(_.forall(item => _object_shape(item, Set("identity", "ordinal"))))
  private def _validation_shape(value: Json): Boolean =
    _object_shape(value, Set("identity", "revision", "checks"), Map("checks" -> _check_array_shape))
  private def _program_array_shape(value: Json): Boolean =
    value.asArray.exists(_.forall(item => _object_shape(item, Set("step", "ordinal"))))
  private def _closure_shape(value: Json): Boolean =
    _object_shape(value, Set("identity", "revision", "programs"), Map("programs" -> _program_array_shape))
  private def _goal_shape(value: Json): Boolean =
    _object_shape(value, Set("input", "candidatePaths", "stepValidation", "phaseValidation", "review", "closure"), Map(
      "input" -> (v => _object_shape(v, Set("phaseIdentity", "repositoryIdentity"))),
      "stepValidation" -> _validation_shape, "phaseValidation" -> _validation_shape,
      "review" -> (v => _object_shape(v, Set("identity", "revision"))),
      "closure" -> _closure_shape))
  private def _repository_shape(value: Json): Boolean =
    _object_shape(value, Set("input", "closureSelection", "validation", "conflicts", "review"), Map(
      "input" -> (v => _object_shape(v, Set("repositoryIdentity", "localBranch", "trackingRemote", "trackingBranch",
        "remoteEndpointIdentity", "syncMode", "repositoryPolicyVersion"))),
      "validation" -> (v => _object_shape(v, Set("identity", "revision", "mode", "reason", "checks"), Map("checks" -> _check_array_shape))),
      "conflicts" -> (v => _object_shape(v, Set("semanticPaths", "maximumConflicts", "maximumBlobBytes"))),
      "review" -> (v => _object_shape(v, Set("identity", "revision", "maximumPaths", "maximumDiffBytes")))))
  private def _definition_shape(value: Json): Boolean =
    _object_shape(value, Set("schemaVersion", "target", "goal", "repository"), Map(
      "target" -> (v => _object_shape(v, Set("root", "localBranch", "remote", "remoteBranch", "endpoint"))),
      "goal" -> _goal_shape, "repository" -> _repository_shape))

  val definition = ComponentParameterKey.required("sm-workflow.host.definition",
    ComponentParameterDecoder[Definition] {
      case value: ConfigurationValue.ObjectValue => decodeDefinitionC(json(value))
      case ConfigurationValue.StringValue(value) => io.circe.parser.parse(value)
        .fold(_ => Consequence.configurationInvalid("Three-workflow host JSON is invalid"), decodeDefinitionC)
      case _ => Consequence.configurationInvalid("Three-workflow host requires an explicit structured definition")
    })
  val declarations: Vector[ComponentParameterKey[?]] =
    GoalPhaseRuntimeParameters.declarations ++ Vector(definition, WorkflowExecutionProfileParameters.declaration)
  lazy val factory = ThreeWorkflowLauncherAssembly.factory(declarations, Vector.empty, this)

  def decodeC(creation: ComponentCreate, parameters: ComponentInitializationParameters,
    context: ExecutionContext): Consequence[ThreeWorkflowLauncherAssembly.Host] =
    if (creation == null || parameters == null || context == null)
      Consequence.configurationInvalid("Three-workflow host requires its ordinary creation capsule")
    else for {
      resolved <- parameters.resolve(definition)
      settings <- resolved.value.map(Consequence.success).getOrElse(
        Consequence.configurationInvalid("Three-workflow host definition is not configured"))
      catalog <- GoalPhaseRuntimeCatalog.loadC()
      persistence <- GoalPhaseRuntimeParameters.decodeC(parameters, catalog)
      executionprofiles <- WorkflowExecutionProfileParameters.resolveC(parameters)
      target <- targetC(settings.target)
      evidence <- NativeHostEvidence.bindC(creation.configuration, persistence)
      repository = RepositorySyncManagedFetch.Declaration(persistence.configuration, persistence.instances,
        persistence.continuations, settings.repository.input, target)
      selections <- RepositorySyncHostSelectionReadPolicy.bindC(creation.configuration, repository)
      ports = new ThreeWorkflowHostEvidencePorts(evidence)
      closure = GoalPhaseNativeRepositoryClosure.Installation(repository, settings.repository.closureSelection,
        selections, ports.phaseClosure, settings.repository.validation, ports.convergence,
        settings.repository.conflicts, Some(RepositorySyncNativeMergeReview.Installation(settings.repository.review,
          ports.repositoryReview)), Some(RepositorySyncHumanConflict.Installation(ports.repositoryHuman)))
      goal = GoalPhaseNativeRun.Declaration(persistence.configuration, persistence.instances, persistence.continuations,
        settings.goal.input, target, settings.goal.candidatePaths, Some(settings.goal.stepValidation),
        Some(GoalPhaseNativeReview.Installation(settings.goal.review, ports.goalReview)),
        Some(GoalPhaseNativeClosure.Installation(settings.goal.closure, ports.stepClosure)),
        Some(settings.goal.phaseValidation), Some(closure), Some(GoalPhaseOrderedHumanDecision.Installation(ports.goalHuman)),
        executionprofiles)
      _ <- GoalPhaseNativeRun.validateC(goal)
      splitCatalog <- SplitPhaseRuntimeCatalog.loadC()
      split = SplitPhaseManagedStartAssembly.Declaration(persistence.configuration, persistence.instances, persistence.continuations)
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
