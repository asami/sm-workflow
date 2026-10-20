package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentCreate}
import org.goldenport.cncf.config.{ComponentInitializationParameters, ComponentParameterKey, ComponentParameterPathRoute}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.protocol.spec.ServiceDefinition

/** Native three-service host composition, not a compatibility or unavailable-port fallback.
  * The standard launcher uses ThreeWorkflowHostParameters after CNCF bootstrap.
  * Parameter data selects storage/targets/policies, never independent authority ports.
  * @since   Oct.  6, 2026
  *  version Oct. 15, 2026
  * @version Oct. 20, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object ThreeWorkflowLauncherAssembly {
  final case class Host(
    goal: GoalPhaseNativeRun.Declaration,
    split: SplitPhaseManagedStartAssembly.Declaration,
    splitCatalog: SplitPhaseRuntimeCatalog.Catalog,
    splitStartHuman: SplitPhaseTypedStartHumanVerification.EvidencePort,
    splitAnswerHuman: SplitPhaseHumanAnswerVerification.EvidencePort
  )

  trait HostDecoder {
    /** Trusted assembly only. Receive the single canonical parameter snapshot and
      * this component's unauthenticated composition context, not an incoming actor.
      * Evidence admission and effect authority ports belong to the owning host.
      */
    def decodeC(creation: ComponentCreate, parameters: ComponentInitializationParameters,
      context: ExecutionContext): Consequence[Host]
  }

  val publishedMcpOperations: Set[String] = Set(
    "EvidenceService.readEvidence", "EvidenceService.recordEvidenceJournal",
    "JournalService.readJournalEntry",
    "GoalPhaseService.startGoalPhase", "GoalPhaseService.advanceGoalPhase", "GoalPhaseService.submitGoalPhaseWorkResult",
    "GoalPhaseService.submitGoalPhaseOrderedDecision",
    "GoalPhaseService.submitGoalPhaseFindingRecording",
    "GoalPhaseService.describeGoalPhaseValidationRecovery", "GoalPhaseService.recoverGoalPhaseValidation",
    "GoalPhaseService.describeGoalPhaseRemainingPlan", "GoalPhaseService.adoptGoalPhaseRemainingPlan",
    "SplitPhaseService.startSplitPhase", "SplitPhaseService.advanceSplitPhase", "SplitPhaseService.submitPartitionSemanticResult",
    "SplitPhaseService.submitHandoffScopeResult", "SplitPhaseService.submitSplitHumanDirection", "SplitPhaseService.submitReasoningResult",
    "RepositorySyncService.startRepositorySync", "RepositorySyncService.advanceRepositorySync",
    "RepositorySyncService.submitRepositorySyncWorkResult", "RepositorySyncService.submitNativeMergeReview",
    "RepositorySyncService.submitRepositoryConflictDecision", "RepositorySyncService.submitNativeMergeRepair")

  def factory(declarations: Vector[ComponentParameterKey[?]], routes: Vector[ComponentParameterPathRoute],
    host: HostDecoder): Component.BundleFactory = {
    require(host != null, "Three-workflow factory requires a trusted host decoder")
    new SmWorkflowConfiguredComponentFactory(declarations, routes, (creation, parameters, context) => for {
      selected <- host.decodeC(creation, parameters, context)
      services <- bindC(creation, selected)(using context)
    } yield services, publishedMcpOperations)
  }

  def bindC(creation: ComponentCreate, host: Host)(using context: ExecutionContext): Consequence[Vector[ServiceDefinition]] =
    if (creation == null || host == null || host.goal == null || host.split == null || host.splitCatalog == null ||
        host.splitStartHuman == null || host.splitAnswerHuman == null || context == null || context.clock == null)
      Consequence.configurationInvalid("Three-workflow assembly requires its owning host and independent Human evidence ports")
    else for {
      _ <- GoalPhaseNativeRun.validateC(host.goal)
      _ <- if (host.goal.validationPolicy.nonEmpty && host.goal.reviewInstallation.nonEmpty &&
          host.goal.closureInstallation.nonEmpty && host.goal.phaseValidationPolicy.nonEmpty && host.goal.repositoryClosure.nonEmpty &&
          host.goal.humanDecision.nonEmpty)
        Consequence.unit else Consequence.configurationInvalid("Three-workflow GoalPhase requires native Step/Phase checks, review and closure installations")
      repository = host.goal.repositoryClosure.get
      _ <- if (repository.review.nonEmpty && repository.humanConflict.nonEmpty && repository.conflicts.semanticPaths.nonEmpty)
        Consequence.unit else Consequence.configurationInvalid("Three-workflow RepositorySync requires paired semantic conflict, independent review and Human handling")
      goal <- GoalPhaseNativeServiceAssembly.bindHostC(creation, host.goal)
      sync <- RepositorySyncManagedServiceAssembly.bindHostC(creation, repository.declaration,
        nativeProgression = true, conflictPolicy = repository.conflicts,
        validationPolicy = Some(repository.validation), nativeReview = repository.review,
        convergence = Some(repository.convergence), humanConflict = repository.humanConflict)
      split <- SplitPhaseManagedServiceAssembly.bindHostWorkflowC(creation, host.split, host.splitCatalog,
        host.splitStartHuman, host.splitAnswerHuman)
      goalCatalog <- GoalPhaseRuntimeCatalog.loadC()
      repositoryDefinition <- RepositorySyncDefinitionBinding.bindC
    } yield Vector(
      NativeWorkflowSessionInput.goal(NativeWorkflowResultInput.goal(goal, creation, host.goal, goalCatalog.definition),
        creation, host.goal, goalCatalog.definition),
      NativeWorkflowSessionInput.split(NativeWorkflowResultInput.split(split, creation, host.split, host.splitCatalog.definition),
        creation, host.split, host.splitCatalog.definition),
      NativeWorkflowSessionInput.repository(NativeWorkflowResultInput.repository(sync, creation, repository.declaration, repositoryDefinition),
        creation, repository.declaration, repositoryDefinition),
      NativeHostPreparationServiceAssembly.bind,
      DevelopmentJournalService.bind(host.goal.journalPolicy),
      CommonEvidenceService.bind(host.goal.evidencePolicy, host.goal.journalPolicy))
}
