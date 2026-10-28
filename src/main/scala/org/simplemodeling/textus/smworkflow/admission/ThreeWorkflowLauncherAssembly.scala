package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentCreate}
import org.goldenport.cncf.config.{ComponentInitializationParameters, ComponentParameterKey, ComponentParameterPathRoute}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.protocol.spec.ServiceDefinition
import org.goldenport.protocol.spec as spec
import cats.data.NonEmptyVector
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/** Native three-service host composition, not a compatibility or unavailable-port fallback.
  * The standard launcher uses ThreeWorkflowRuntimeParameters after CNCF bootstrap.
  * Parameter data selects storage/targets/policies, never independent authority ports.
  * @since   Oct.  6, 2026
  * @version Oct. 28, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object ThreeWorkflowLauncherAssembly {
  final case class RuntimeComposition(
    goal: GoalPhaseNativeRun.Declaration,
    split: SplitPhaseManagedStartAssembly.Declaration,
    splitCatalog: SplitPhaseRuntimeCatalog.Catalog,
    splitStartHuman: SplitPhaseTypedStartHumanVerification.EvidencePort,
    splitAnswerHuman: SplitPhaseHumanAnswerVerification.EvidencePort
  )

  trait RuntimeCompositionDecoder {
    /** Trusted assembly only. Receive the single canonical parameter snapshot and
      * this component's unauthenticated composition context, not an incoming actor.
      * Evidence admission and effect authority ports belong to the owning host.
      */
    def decodeC(creation: ComponentCreate, parameters: ComponentInitializationParameters,
      context: ExecutionContext): Consequence[RuntimeComposition]
  }

  val publishedMcpOperations: Set[String] = Set(
    "DelegateTaskService.startDelegateTask", "DelegateTaskService.advanceDelegateTask",
    "DelegateTaskService.submitDelegateTaskWorkResult", "DelegateTaskService.resolveDelegateTaskDiagnosticInformation",
    "CompletionRecordClosureService.describeCompletionRecordClosure", "CompletionRecordClosureService.advanceCompletionRecordClosure",
    "DelegateTaskDeliveryService.observeDelegateTaskDelivery", "DelegateTaskDeliveryService.describeDelegateTaskAdoption",
    "GoalTaskService.startGoalTask", "GoalTaskService.advanceGoalTask",
    "GoalTaskService.submitGoalTaskWorkResult", "GoalTaskService.resolveGoalTaskDiagnosticInformation",
    "GoalTaskService.describeGoalTaskMalformedIssuedWorkRecovery", "GoalTaskService.recoverGoalTaskMalformedIssuedWork",
    "StandaloneFixService.startStandaloneReviewFix",
    "StandaloneFixService.startStandaloneFix", "StandaloneFixService.advanceStandaloneFix",
    "StandaloneFixService.submitStandaloneFixWorkResult", "StandaloneFixService.resolveStandaloneFixDiagnosticInformation",
    "LocalPublicationService.ensureLocalPublication", "LocalPublicationService.collectLocalPublication",
    "DevelopmentObservationService.readDevelopmentState", "DevelopmentObservationService.readDevelopmentHistory",
    "EvidenceService.readEvidence", "EvidenceService.readEvidenceRange", "EvidenceService.recordEvidenceJournal",
    "EvidenceService.archiveEvidenceBatch", "EvidenceService.describeEvidence",
    "EvidenceService.manageEvidenceRetention", "EvidenceService.findDueEvidence",
    "JournalService.readJournalEntry",
    "GoalPhaseService.startGoalPhase", "GoalPhaseService.advanceGoalPhase", "GoalPhaseService.submitGoalPhaseWorkResult",
    "GoalPhaseService.submitGoalPhaseOrderedDecision",
    "GoalPhaseService.submitGoalPhaseFindingRecording",
    "GoalPhaseService.describeGoalPhaseValidationRecovery", "GoalPhaseService.recoverGoalPhaseValidation",
    "GoalPhaseService.describeGoalPhaseReleaseRepairContinuation",
    "GoalPhaseService.recoverGoalPhaseReleaseRepairContinuation",
    "GoalPhaseService.describeGoalPhaseRemainingPlan", "GoalPhaseService.adoptGoalPhaseRemainingPlan",
    "GoalPhaseService.describeGoalPhaseFailedPhaseRepair", "GoalPhaseService.adoptGoalPhaseFailedPhaseRepair",
    "GoalPhaseService.describeGoalPhaseValidationRepair", "GoalPhaseService.adoptGoalPhaseValidationRepair",
    "GoalPhaseService.describeGoalPhaseCompletionPolicy", "GoalPhaseService.adoptGoalPhaseCompletionPolicy",
    "GoalPhaseService.describeGoalPhaseStepReview", "GoalPhaseService.requestGoalPhaseStepReview",
    "SplitPhaseService.startSplitPhase", "SplitPhaseService.advanceSplitPhase", "SplitPhaseService.submitPartitionSemanticResult",
    "SplitPhaseService.submitHandoffScopeResult", "SplitPhaseService.submitSplitHumanDirection", "SplitPhaseService.submitReasoningResult",
    "RepositorySyncService.startRepositorySync", "RepositorySyncService.advanceRepositorySync",
    "RepositorySyncService.submitRepositorySyncWorkResult", "RepositorySyncService.submitRepositoryPurposeResult", "RepositorySyncService.submitNativeMergeReview",
    "RepositorySyncService.submitRepositoryConflictDecision", "RepositorySyncService.submitNativeMergeRepair")

  /** Only implemented public Operations, using their generated CML contracts.
    * Their handlers are composed from the real run when invoked.
    */
  def deferredDeclarations: Vector[ServiceDefinition] = Vector(
    SmWorkflowComponent.DelegateTaskServiceService, SmWorkflowComponent.DelegateTaskDeliveryServiceService,
    SmWorkflowComponent.CompletionRecordClosureServiceService,
    SmWorkflowComponent.StandaloneFixServiceService, SmWorkflowComponent.GoalTaskServiceService,
    SmWorkflowComponent.GoalPhaseServiceService, SmWorkflowComponent.SplitPhaseServiceService,
    SmWorkflowComponent.RepositorySyncServiceService, SmWorkflowComponent.JournalServiceService,
    SmWorkflowComponent.EvidenceServiceService, SmWorkflowComponent.DevelopmentObservationServiceService,
    SmWorkflowComponent.LocalPublicationServiceService).map { service =>
    NativeWorkflowDescriptionMetadata.service(service, spec.OperationDefinitionGroup(NonEmptyVector.fromVectorUnsafe(
      service.operations.operations.toVector.filter(operation => publishedMcpOperations(service.name + "." + operation.name)))))
  }

  def factory(declarations: Vector[ComponentParameterKey[?]], routes: Vector[ComponentParameterPathRoute],
    host: RuntimeCompositionDecoder): Component.BundleFactory = {
    require(host != null, "Three-workflow factory requires a trusted host decoder")
    new SmWorkflowConfiguredComponentFactory(declarations, routes, (creation, parameters, context) => for {
      selected <- host.decodeC(creation, parameters, context)
      services <- bindC(creation, selected)(using context)
    } yield services, publishedMcpOperations)
  }

  def bindC(creation: ComponentCreate, host: RuntimeComposition)(using context: ExecutionContext): Consequence[Vector[ServiceDefinition]] =
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
      goal <- GoalPhaseNativeServiceAssembly.bindRuntimeC(creation, host.goal)
      standalone <- StandaloneFixService.bindC(creation, host.goal)
      task <- GoalTaskService.bindC(creation, host.goal)
      delegate <- DelegateTaskService.bindC(creation, host.goal)
      delivery <- DelegateTaskDeliveryService.bindC(creation, host.goal)
      records <- CompletionRecordContinuation.bindC(creation, host.goal)
      sync <- RepositorySyncManagedServiceAssembly.bindRuntimeC(creation, repository.declaration.copy(journalPolicy = host.goal.journalPolicy),
        nativeProgression = true, conflictPolicy = repository.conflicts,
        validationPolicy = Some(repository.validation), nativeReview = repository.review,
        convergence = Some(repository.convergence), humanConflict = repository.humanConflict,
        repositoryContinuation = repository.repositoryContinuation)
      split <- SplitPhaseManagedServiceAssembly.bindRuntimeWorkflowC(creation, host.split.copy(journalPolicy = host.goal.journalPolicy,
        journalRepository = Some(repository.declaration.input.repositoryIdentity)), host.splitCatalog,
        host.splitStartHuman, host.splitAnswerHuman)
      goalCatalog <- GoalPhaseRuntimeCatalog.loadC()
      repositoryDefinition <- RepositorySyncDefinitionBinding.bindC
    } yield Vector(
      standalone, task, delegate, delivery, records,
      NativeWorkflowSessionInput.goal(NativeWorkflowResultInput.goal(goal, creation, host.goal, goalCatalog.definition),
        creation, host.goal, goalCatalog.definition),
      NativeWorkflowSessionInput.split(NativeWorkflowResultInput.split(split, creation, host.split, host.splitCatalog.definition),
        creation, host.split, host.splitCatalog.definition),
      NativeWorkflowSessionInput.repository(NativeWorkflowResultInput.repository(sync, creation, repository.declaration, repositoryDefinition),
        creation, repository.declaration, repositoryDefinition),
      NativeWorkflowPreparationServiceAssembly.bind,
      DevelopmentJournalService.bind(host.goal.journalPolicy),
      CommonEvidenceService.bind(host.goal.evidencePolicy, host.goal.journalPolicy),
      DevelopmentObservationService.bind(host.goal, goalCatalog.definition),
      LocalPublicationService.bind(host.goal, goalCatalog.definition))
}
