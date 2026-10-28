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
import GoalPhaseDependencyValidation.given

/** Ordinary installed composition. These are fixed targets and policies, not
  * command grants or a declaration that any review/Human result was admitted.
  * Storage/process bootstrap has already been performed by the owning Subsystem.
  * @since   Oct.  7, 2026
  * @version Oct. 28, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object ThreeWorkflowRuntimeParameters extends ThreeWorkflowLauncherAssembly.RuntimeCompositionDecoder {
  val schema = "sm-workflow.three-workflow-runtime.v1"
  final case class Target(root: String, localBranch: String, remote: String, remoteBranch: String, endpoint: String)
  final case class Goal(input: GoalPhaseStartInput, candidatePaths: Vector[String],
    stepValidation: GoalPhaseNativeValidation.Policy, phaseValidation: GoalPhaseNativeValidation.Policy,
    review: GoalPhaseNativeReview.Policy, closure: GoalPhaseNativeClosure.Policy,
    dependencyValidation: Option[GoalPhaseDependencyValidation.Binding] = None,
    dependencySourceRevisions: Vector[GoalPhaseDependencyValidation.Binding] = Vector.empty,
    dependencySourceConsumptions: Vector[GoalPhaseDependencySourceRevision.Consumption] = Vector.empty)
  final case class Repository(input: RepositorySyncStartInput,
    validation: RepositorySyncNativeMergeValidation.Policy, conflicts: RepositorySyncNativeConflictInput.Policy,
    review: RepositorySyncNativeMergeReview.Policy)
  final case class Definition(schemaVersion: String, target: Target, goal: Goal, repository: Repository)

  // Bottom-up derivation also bounds compiler expansion for the nested policies.
  private given Encoder[Goal] = deriveEncoder[Goal].mapJson(_.mapObject(fields =>
    Vector("dependencySourceRevisions", "dependencySourceConsumptions").foldLeft(
      if (fields("dependencyValidation").exists(_.isNull)) fields.remove("dependencyValidation") else fields) { (prior, key) =>
      if (prior(key).exists(_.asArray.exists(_.isEmpty))) prior.remove(key) else prior
    }))
  private val _goal_decoder: Decoder[Goal] = deriveDecoder
  private given Decoder[Goal] = Decoder.instance { cursor =>
    if (Vector("dependencyValidation", "dependencySourceRevisions", "dependencySourceConsumptions")
        .exists(key => cursor.downField(key).focus.exists(_.isNull)))
      Left(io.circe.DecodingFailure("Dependency validation binding must be absent or a complete object", cursor.history))
    else {
      val normalized = Vector("dependencySourceRevisions", "dependencySourceConsumptions").foldLeft(cursor.value) { (prior, key) =>
        if (cursor.downField(key).focus.isEmpty) prior.mapObject(_.add(key, Json.arr())) else prior
      }
      _goal_decoder(normalized.hcursor).flatMap(value =>
      if (value.dependencyValidation != null && value.dependencyValidation.forall(GoalPhaseDependencyValidation.bindingValid) &&
          value.dependencySourceRevisions != null && value.dependencySourceRevisions.size <= 16 &&
          value.dependencySourceRevisions.forall(GoalPhaseDependencyValidation.bindingValid) &&
          value.dependencySourceRevisions.map(b => (b.identity, b.revision)).distinct.size == value.dependencySourceRevisions.size &&
          value.dependencySourceConsumptions != null && value.dependencySourceConsumptions.size <= 16 &&
          value.dependencySourceConsumptions.forall(GoalPhaseDependencySourceRevision.consumptionValid) &&
          value.dependencySourceConsumptions.distinct.size == value.dependencySourceConsumptions.size) Right(value)
      else Left(io.circe.DecodingFailure("Invalid dependency validation host binding", cursor.history)))
    }
  }
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

  val definition = ComponentParameterKey.optional("sm-workflow.runtime.definition",
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
  val validationRepairCallerSource = ComponentParameterKey.optional(
    "sm-workflow.goal-phase.validation-repair.caller-source", ComponentParameterDecoder[GoalPhaseValidationPriorInvocation.CallerSourcePolicy] {
      case value: ConfigurationValue.ObjectValue => json(value).as[GoalPhaseValidationPriorInvocation.CallerSourcePolicy]
        .fold(e => Consequence.configurationInvalid(e.message), Consequence.success)
      case _ => Consequence.configurationInvalid("Validation repair caller-source requires its exact closed structured policy")
    })
  val developmentObservation: ComponentParameterKey[DevelopmentOperationalView.Configuration] = ComponentParameterKey.optional(
    "sm-workflow.development-observation", ComponentParameterDecoder {
      case value: ConfigurationValue.ObjectValue => DevelopmentOperationalView.decodeConfigurationC(json(value))
      case ConfigurationValue.StringValue(value) => io.circe.parser.parse(value).fold(
        _ => Consequence.configurationInvalid("Development observation configuration is invalid"),
        DevelopmentOperationalView.decodeConfigurationC)
      case _ => Consequence.configurationInvalid("Development observation requires a closed structured configuration")
    })
  val declarations: Vector[ComponentParameterKey[?]] =
    GoalPhaseRuntimeParameters.declarations ++ Vector(definition, WorkflowExecutionProfileParameters.declaration,
      GoalPhaseNativeExecution.declaration, GoalPhaseAcceptanceBoundary.declaration, GoalPhaseFindingRecording.declaration,
      RepositorySyncNativeExecution.declaration, RepositorySyncDocumentInputs.declaration, RepositorySyncProgramImpact.declaration,
      RepositorySyncPurpose.declaration, SplitPhaseNativeExecution.declaration, DevelopmentJournal.declaration, CommonEvidence.declaration,
      completionPolicy, validationRepairCallerSource, developmentObservation, LocalPublicationRuntime.declaration, CommitHygiene.declaration, CompletionRecords.declaration)
  lazy val factory = new SmWorkflowConfiguredComponentFactory(declarations, Vector.empty,
    bindServicesC, ThreeWorkflowLauncherAssembly.publishedMcpOperations)

  private[admission] def bindServicesC(creation: ComponentCreate, parameters: ComponentInitializationParameters,
    context: ExecutionContext): Consequence[Vector[org.goldenport.protocol.spec.ServiceDefinition]] =
    parameters.resolve(definition).flatMap { selected =>
      selected.value match {
        case Some(_) => decodeC(creation, parameters, context).flatMap(host =>
          ThreeWorkflowLauncherAssembly.bindC(creation, host)(using context))
        case None => GoalPhaseRuntimeParameters.decodeC(parameters).map { _ =>
          DeferredWorkflowServices.bind(ThreeWorkflowLauncherAssembly.deferredDeclarations,
            (request, core) => StandardWorkflowRuntime.bindForCallC(creation, parameters, request, core)) :+
            NativeWorkflowPreparationServiceAssembly.bind
        }
      }
    }

  def decodeC(creation: ComponentCreate, parameters: ComponentInitializationParameters,
    context: ExecutionContext): Consequence[ThreeWorkflowLauncherAssembly.RuntimeComposition] =
    if (creation == null || parameters == null || context == null)
      Consequence.configurationInvalid("Three-workflow runtime requires its ordinary creation capsule")
    else for {
      resolved <- parameters.resolve(definition)
      settings <- resolved.value.map(Consequence.success).getOrElse(
        Consequence.configurationInvalid("Three-workflow runtime definition is not configured"))
      result <- decodeSettingsC(creation, parameters, context, settings)
    } yield result

  private[admission] def decodeSettingsC(creation: ComponentCreate, parameters: ComponentInitializationParameters,
    context: ExecutionContext, settings: Definition): Consequence[ThreeWorkflowLauncherAssembly.RuntimeComposition] = for {
      persistence <- GoalPhaseRuntimeParameters.decodeC(parameters)
      selectedpolicy <- parameters.resolve(completionPolicy).map(_.value.getOrElse(GoalPhasePostCompletionSync.localPolicy))
      callersource <- parameters.resolve(validationRepairCallerSource).map(_.value)
      executionprofiles <- WorkflowExecutionProfileParameters.resolveC(parameters)
      executionorigin <- GoalPhaseNativeExecution.resolveOriginC(parameters)
      repositoryclassifications <- RepositorySyncNativeExecution.resolveC(parameters)
      purposePolicy <- RepositorySyncPurpose.resolveC(parameters)
      splitclassifications <- SplitPhaseNativeExecution.resolveC(parameters)
      _ <- SplitPhaseNativeExecution.validateC(splitclassifications, executionprofiles, executionorigin)
      projectacceptance <- GoalPhaseAcceptanceBoundary.resolveProjectC(parameters)
      findingpolicy <- GoalPhaseFindingRecording.resolvePolicyC(parameters)
      journalpolicy <- DevelopmentJournal.resolvePolicyC(parameters, persistence.instances, persistence.continuations)
      evidencepolicy <- CommonEvidence.resolvePolicyC(parameters, Vector(persistence.instances, persistence.continuations) ++ journalpolicy.map(_.collection))
      observation <- parameters.resolve(developmentObservation).map(_.value)
      publication <- parameters.resolve(LocalPublicationRuntime.declaration).map(_.value)
      hygiene <- CommitHygiene.resolveC(parameters)
      records <- parameters.resolve(CompletionRecords.declaration).map(_.value)
      target <- targetC(settings.target)
      evidence <- NativeExternalEvidence.bindC(creation.configuration, persistence)
      repository = RepositorySyncManagedFetch.Declaration(persistence.configuration, persistence.instances,
        persistence.continuations, settings.repository.input, target, executionprofiles, executionorigin, repositoryclassifications, journalpolicy,
        hygiene, settings.goal.candidatePaths, records, purposePolicy = purposePolicy)
      ports = new ThreeWorkflowExternalEvidencePorts(evidence)
      closure = GoalPhaseNativeRepositoryClosure.Installation(repository,
        ports.phaseClosure, settings.repository.validation, ports.convergence,
        settings.repository.conflicts, Some(RepositorySyncNativeMergeReview.Installation(settings.repository.review,
          ports.repositoryReview)), Some(RepositorySyncHumanConflict.Installation(ports.repositoryHuman)),
        Some(RepositorySyncValidationContinuation.Installation(ports.repositoryValidationContinuation)))
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
        Some(GoalPhaseCompletionPolicyAdoption.Installation(ports.completionPolicyAdoption)), selectedpolicy,
        Some(GoalPhaseLegacyPurposeRepairContinuation.Installation(ports.reviewPurposeRepair)), settings.goal.dependencyValidation,
        Some(GoalPhaseValidationRepairAdoption.Installation(ports.validationRepairAdoption)), callersource, observation,
        Some(GoalPhaseReleaseRepairContinuation.Installation(ports.releaseRepairContinuation)),
        Some(GoalPhaseRepairPolicyAdoption.Installation(ports.repairPolicyAdoption)),
        Some(GoalPhaseSourceHistoryNormalization.Installation(ports.sourceHistoryNormalization)),
        Some(GoalPhaseDependencySourceRevision.Installation(ports.dependencySourceRevision)),
        settings.goal.dependencySourceRevisions, settings.goal.dependencySourceConsumptions, publication, hygiene, records)
      _ <- if (settings.goal.dependencyValidation.forall(GoalPhaseDependencyValidation.bindingValid)) Consequence.unit
        else Consequence.configurationInvalid("Goal dependency validation binding is invalid")
      _ <- GoalPhaseNativeRun.validateC(goal)
      splitCatalog <- SplitPhaseRuntimeCatalog.loadC()
      split = SplitPhaseManagedStartAssembly.Declaration(persistence.configuration, persistence.instances, persistence.continuations,
        executionprofiles, executionorigin, splitclassifications, journalpolicy, Some(settings.repository.input.repositoryIdentity))
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
