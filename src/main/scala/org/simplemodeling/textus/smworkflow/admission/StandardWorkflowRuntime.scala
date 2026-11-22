package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.Json
import io.circe.syntax.*
import java.nio.file.Paths
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.config.{ComponentInitializationParameters, CncfConfigurationParameterCatalog}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.security.SecuritySubject
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.protocol.Request
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms
import org.simplemodeling.textus.smworkflow.value.{GoalPhaseDevelopmentReconstructionInput, GoalPhaseStartInput,
  RepositorySyncStartInput, RepositorySyncValidationContinuationAdoptionInput}

/** Standard composition contains policies and logical destinations, never a
  * selected Phase or Plan. Read actual Start input or the retained native run
  * only when its Operation executes, within that call's ordinary storage/UoW.
  * @since   Oct. 27, 2026
  *  version Oct. 28, 2026
  * @version Nov. 21, 2026
  */
private[smworkflow] object StandardWorkflowRuntime {
  private final case class Shared(persistence: GoalPhaseRuntimeParameters.Persistence,
    reader: NativeExternalEvidence.Reader, ports: ThreeWorkflowExternalEvidencePorts, profiles: Option[WorkflowExecutionProfileMapping],
    origin: Option[GoalPhaseNativeExecution.Origin], journal: Option[DevelopmentJournal.Policy],
    evidence: Option[CommonEvidence.Policy], operatingcontext: GoalPhaseOperatingProfiles.Context,
    publication: Option[LocalPublicationRuntime.Policy], hygienemode: String,
    completionrecords: Option[CompletionRecords.Policy], validation: WorkflowValidationInitialization.Context)

  def bindForCallC(creation: ComponentCreate, parameters: ComponentInitializationParameters,
    request: Request, core: ActionCall.Core): Consequence[Vector[spec.ServiceDefinition]] = for {
    shared <- sharedC(creation, parameters, core.executionContext)
    services <- request.service match {
      case Some("WorkflowRetainedWorkService") => Consequence.success(Vector(WorkflowRetainedWork.bind))
      case Some("SplitPhaseService") => splitC(creation, parameters, shared, core).map(Vector(_))
      case Some("GoalPhaseService") => goalC(creation, parameters, shared, request, core).map(Vector(_))
      case Some("RepositorySyncService") if RepositorySyncDirectoryList.operations(request.operation) =>
        Consequence.success(Vector(RepositorySyncDirectoryList.bind(creation, parameters, shared.persistence,
          (selectedrequest, selectedcore, selectedtarget) =>
            repositoryC(creation, parameters, shared, selectedrequest, selectedcore, Some(selectedtarget)))))
      case Some("RepositorySyncService") => repositoryC(creation, parameters, shared, request, core).map(Vector(_))
      case Some("JournalService") => Consequence.success(Vector(DevelopmentJournalService.bind(shared.journal)))
      case Some("EvidenceService") => Consequence.success(Vector(CommonEvidenceService.bind(shared.evidence, shared.journal)))
      case Some("StandaloneFixService") | Some("GoalTaskService") | Some("DelegateTaskService") | Some("DelegateTaskDeliveryService") => boundedC(creation, parameters, shared, request, core).map(Vector(_))
      case Some("ManagedDevelopmentService") => for {
        value <- parameters.resolve(ManagedDevelopmentRuntime.declaration)
        route <- parameters.resolve(ManagedDevelopmentRepositorySync.mainIntegration)
      } yield Vector(ManagedDevelopmentService.bind(value.value, Some(ProviderPhaseDelivery.Storage(shared.persistence.configuration,
        shared.persistence.instances, shared.persistence.continuations)), route.value))
      case Some("LocalPublicationService") => for {
        policy <- Consequence.success(shared.publication)
        definition <- GoalPhaseRuntimeCatalog.loadC().map(_.definition)
      } yield Vector(LocalPublicationService.bind(policy, (id, call) =>
        retainedGoalC(creation, shared.persistence, definition, id, call).flatMap(payload =>
          GoalPhaseParticipantAdmission.admitC(payload.participantIdentity, call.executionContext)), Some(shared.reader)))
      case Some("DevelopmentObservationService") => parameters.resolve(ThreeWorkflowRuntimeParameters.developmentObservation).flatMap {
        case value if value.value.isEmpty => Consequence.success(Vector(DevelopmentObservationService.bindNotConfigured))
        case value =>
          val session = value.value.get.subject.sessionId
          goalC(creation, parameters, shared, request, core, Some(session)).map(Vector(_))
      }
      case _ => Consequence.configurationInvalid("Standard runtime has no matching service")
    }
  } yield services

  private def sharedC(creation: ComponentCreate, parameters: ComponentInitializationParameters,
    context: ExecutionContext): Consequence[Shared] = for {
    persistence <- GoalPhaseRuntimeParameters.decodeC(parameters)
    reader <- NativeExternalEvidence.bindC(creation.configuration, persistence)
    profiles <- WorkflowExecutionProfileParameters.resolveC(parameters)
    origin <- GoalPhaseNativeExecution.resolveOriginC(parameters)
    operatingprofile <- GoalPhaseOperatingProfiles.configuredC(parameters)
    operatingmappings <- GoalPhaseOperatingProfiles.mappingsC(parameters)
    dispatchpolicy <- parameters.resolve(GoalPhaseDispatchPolicy.declaration).map(_.value)
    logicalorigin <- parameters.resolve(GoalPhaseNativeExecution.logicalOrigin).map(_.value)
    operatingcontext = GoalPhaseOperatingProfiles.Context(operatingprofile, operatingmappings, profiles, origin,
      logicalOrigin = logicalorigin, dispatchPolicy = dispatchpolicy)
    _ <- GoalPhaseOperatingProfiles.validateContextC(operatingcontext)
    publication <- parameters.resolve(LocalPublicationRuntime.declaration).map(_.value)
    hygiene <- CommitHygiene.resolveC(parameters)
    records <- parameters.resolve(CompletionRecords.declaration).map(_.value)
    journal <- DevelopmentJournal.resolvePolicyC(parameters, persistence.instances, persistence.continuations)
    evidence <- CommonEvidence.resolveForCallC(parameters, Vector(persistence.instances, persistence.continuations) ++ journal.map(_.collection), context, creation.configuration.configuration.values)
  } yield Shared(persistence, reader, new ThreeWorkflowExternalEvidencePorts(reader), profiles, origin, journal, evidence,
    operatingcontext, publication, hygiene, records, WorkflowValidationInitialization.Context(creation.configuration, parameters))

  private def splitC(creation: ComponentCreate, parameters: ComponentInitializationParameters,
    shared: Shared, core: ActionCall.Core): Consequence[spec.ServiceDefinition] = {
    given ExecutionContext = core.executionContext
    for {
    classifications <- SplitPhaseNativeExecution.resolveC(parameters)
    catalog <- SplitPhaseRuntimeCatalog.loadC()
    p = shared.persistence
    declaration = SplitPhaseManagedStartAssembly.Declaration(p.configuration, p.instances, p.continuations,
      shared.profiles, shared.origin, classifications, shared.journal)
    service <- SplitPhaseManagedServiceAssembly.bindRuntimeWorkflowC(creation, declaration, catalog,
      shared.ports.splitStart, shared.ports.splitAnswer)
  } yield NativeWorkflowSessionInput.split(NativeWorkflowResultInput.split(service, creation, declaration, catalog.definition),
    creation, declaration, catalog.definition)
  }

  private def goalC(creation: ComponentCreate, parameters: ComponentInitializationParameters, shared: Shared,
    request: Request, core: ActionCall.Core, ownerSession: Option[String] = None): Consequence[spec.ServiceDefinition] = for {
    catalog <- GoalPhaseRuntimeCatalog.loadC()
    reconstruction <- if (request.operation == GoalPhaseDevelopmentReconstruction.operation)
      GoalPhaseDevelopmentReconstruction.inputForCallC(request, core).map(Some(_))
      else Consequence.success(Option.empty[GoalPhaseDevelopmentReconstructionInput])
    session <- if (request.operation == GoalPhaseQualification.describeOperation)
      GoalPhaseQualification.descriptionForCallC(request, core).map(value => Some(value.sessionId))
      else if (request.operation == GoalPhaseQualification.selectOperation)
        GoalPhaseQualification.selectionForCallC(request, core).map(value => Some(value.sessionId))
      else if (request.operation == "startGoalPhase") Consequence.success(Option.empty[String])
      else if (request.operation == GoalPhasePrerequisitePublicationRecovery.describeOperation)
        GoalPhasePrerequisitePublicationRecovery.descriptionForCallC(request, core).map(value => Some(value.sessionId))
      else if (request.operation == GoalPhaseSourceHistoryNormalization.adoptionOperation)
        GoalPhaseSourceHistoryNormalization.inputForCallC(request, core).map(value => Some(value.sessionId))
      else reconstruction.map(value => Consequence.success(Some(value.sessionId))).getOrElse(
        ownerSession.map(value => Consequence.success(Some(value))).getOrElse(goalSessionC(request).map(Some(_))))
    loaded <- if (request.operation == "startGoalPhase") for {
      input <- NativeWorkflowOperationInput.recordC(request, "input")
      intent <- NativeWorkflowOperationInput.optionalValueC[org.simplemodeling.textus.smworkflow.value.GoalPhaseStartIntent](request, "intent")
      _ <- intent.fold(Consequence.unit)(GoalPhaseStartPreparation.prepareC(core, input, _))
      business <- NativeWorkflowStartInput.fromRecordC(NativeWorkflowStartInput.Kind.GoalPhase, input)
      records <- GoalPhaseInputRecordReadPolicy.decodeC(creation.configuration)
      selected <- new ProfileSelectionDataStorePort(records.storeName,
        records.collections.find(_._1 == GoalPhaseInputRecordReadPolicy.Purpose.Selection).get._2)
        .loadForBusinessC(business, SecuritySubject.from(core.executionContext.security).subjectId, core)
        .flatMap(_.map(Consequence.success).getOrElse(invalid("Start has no selected business input")))
      plans <- GoalPhaseNativeInputReadPolicy.decodeC(creation.configuration)
      plan <- new GoalPhaseSelectedPlanDataStorePort(plans.storeName, plans.plans).loadForCallC(selected.selectionId, core)
        .flatMap(_.map(Consequence.success).getOrElse(invalid("Start requires its selected complete Plan")))
    } yield (plan.plan, Some(plan), Some(selected), Option.empty[GoalPhaseNativeRun.Payload])
    else for {
      retained <- retainedGoalC(creation, shared.persistence, catalog.definition, session.get, core)
    } yield (retained.plan, Option.empty[GoalPhaseNativeRun.SelectedPlan], Option.empty[ProfileStartAdmission.Selection], Some(retained))
    development <- parameters.resolve(ManagedDevelopmentRuntime.declaration).map(_.value)
    execution <- ManagedDevelopmentRuntime.executionPlanC(core)
    managed <- ManagedDevelopmentBoundedWork.selectC(development, loaded._1.repository, execution, loaded._4.map(p => p.root -> p.branch))
    target <- managed.map(m => Consequence.success(RepositoryGitRuntimePrograms.Target(Paths.get(m.root), m.localBranch,
      m.remote, m.remoteBranch, m.endpoint))).getOrElse(targetC(creation))
    settings <- goalSettingsC(loaded._1, target)
    initial <- ThreeWorkflowRuntimeParameters.decodeSettingsC(creation, parameters, core.executionContext, settings)
    _ <- managed.fold(Consequence.unit)(m => ManagedDevelopmentBoundedWork.validationBindingsC(execution, m,
      (initial.goal.validationPolicy.toVector.flatMap(_.checks) ++ initial.goal.phaseValidationPolicy.toVector.flatMap(_.checks)).distinct))
    binding <- session match {
      case None => Consequence.success(loaded._1 -> initial.goal.candidatePaths)
      case Some(identity) => for {
        context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(identity))
        proposal <- reconstruction.map(value => GoalPhaseDevelopmentReconstruction.proposalC(value).map(Some(_)))
          .getOrElse(goalProposalC(request))
        port <- GoalPhaseNativeWorkContinuation.bindC(initial.goal, catalog.definition)
        selected <- port.runtimeBindingC(identity, core.copy(executionContext = context), proposal)
      } yield selected
    }
    current <- goalSettingsC(binding._1, target)
    composition <- ThreeWorkflowRuntimeParameters.decodeSettingsC(creation, parameters, core.executionContext,
      current.copy(goal = current.goal.copy(candidatePaths = binding._2)))
    plans = new GoalPhaseNativeRun.PlanPort {
      def loadForCallC(identity: String, call: ActionCall.Core) = Consequence.success(loaded._2)
    }
    declaration = composition.goal.copy(managedDevelopment = composition.goal.managedDevelopment.map(p =>
      loaded._4.fold(p)(retained => p.copy(delivery = retained.dependencyDelivery.map(Vector(_))))))
    service <- GoalPhaseNativeServiceAssembly.bindC(creation, declaration, plans)
    described = if (request.service.contains("DevelopmentObservationService"))
      DevelopmentObservationService.bind(declaration, catalog.definition)
    else NativeWorkflowSessionInput.goal(NativeWorkflowResultInput.goal(service, creation, declaration, catalog.definition),
      creation, declaration, catalog.definition, loaded._3)
    selected <- RuntimeGitSelections.goalC(described, binding._1, binding._2)
  } yield managed.fold(selected)(m => RepositorySyncDirectoryList.bindWorkspace(selected, m.workspace))

  private def goalSessionC(request: Request): Consequence[String] =
    NativeWorkflowOperationInput.optionalTextC(request, "sessionId").flatMap {
      case Some(value) => Consequence.success(value)
      case None => NativeWorkflowOperationInput.recordC(request, "input").flatMap(record =>
        record.getString("sessionId").map(Consequence.success).getOrElse(invalid("Goal input requires sessionId")))
    }

  private def goalProposalC(request: Request): Consequence[Option[GoalPhasePlanRevision.Proposal]] =
    if (request.operation == GoalPhaseRemainingPlanAdoption.describeOperation)
      NativeWorkflowOperationInput.valueC[org.simplemodeling.textus.smworkflow.value.GoalPhaseRemainingPlanDescriptionInput](request, "input")
        .flatMap(value => GoalPhasePlanRevision.fromPublicProposalC(Some(value.proposal)))
    else if (request.operation == GoalPhaseRemainingPlanAdoption.adoptionOperation)
      NativeWorkflowOperationInput.valueC[org.simplemodeling.textus.smworkflow.value.GoalPhaseRemainingPlanAdoptionInput](request, "input")
        .flatMap(value => GoalPhasePlanRevision.fromPublicProposalC(Some(value.proposal)))
    else Consequence.success(None)

  private def retainedGoalC(creation: ComponentCreate, p: GoalPhaseRuntimeParameters.Persistence,
    definition: DefinitionBinding, session: String, core: ActionCall.Core): Consequence[GoalPhaseNativeRun.Payload] = for {
    context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(session))
    storage <- WorkflowComponentStorage.bindC(creation, SmWorkflowComponent.componentId, p.configuration,
      Vector(definition), p.instances, p.continuations)
    starts <- storage.bindMechanicalC(GoalPhaseNativeRun.startOperation, GoalPhaseNativeRun.codec)
    retained <- starts.loadRetainedC(GoalPhaseNativeRun.reference(session))(using context)
    value <- retained.map(v => Consequence.success(v.receipt.payload.value)).getOrElse(invalid("Session has no native GoalPhase Start"))
  } yield value

  private def repositoryC(creation: ComponentCreate, parameters: ComponentInitializationParameters, shared: Shared,
    request: Request, core: ActionCall.Core,
    selectedtarget: Option[RepositoryGitRuntimePrograms.Target] = None): Consequence[spec.ServiceDefinition] = for {
    definition <- RepositorySyncDefinitionBinding.bindC
    p = shared.persistence
    adoption <- if (request.operation == RepositorySyncValidationContinuation.adoptionOperation)
      RepositorySyncValidationContinuation.routingInputC(request, core).map(Some(_))
      else Consequence.success(Option.empty[RepositorySyncValidationContinuationAdoptionInput])
    sessionid <- if (request.operation == "startRepositorySync") Consequence.success(Option.empty[String])
      else adoption.fold(_repository_session_c(request, core).map(Some(_)))(value => Consequence.success(Some(value.sessionId)))
    retainedstart <- sessionid.fold(Consequence.success(
      Option.empty[WorkflowManagedMechanicalTransition.Retained[RepositorySyncManagedFetch.Payload]])) { session =>
      for {
        context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(session))
        storage <- WorkflowComponentStorage.bindC(creation, SmWorkflowComponent.componentId, p.configuration,
          Vector(definition), p.instances, p.continuations)
        starts <- storage.bindMechanicalC(RepositorySyncManagedFetch.startOperation, RepositorySyncManagedFetch.codec(true))
        retained <- starts.loadRetainedC(RepositorySyncManagedFetch.reference(session, "start"))(using context)
        selected <- retained.map(Consequence.success).getOrElse(invalid("Session has no native RepositorySync Start"))
      } yield Some(selected)
    }
    retained = retainedstart.map(_.receipt.payload.value)
    passive <- adoption match {
      case Some(input) => RepositorySyncValidationContinuation.anyTargetC(input.target).map {
        case _: RepositorySyncValidationContinuation.PurposeTestInterruptionTarget => true
        case _ => false
      }
      case None => Consequence.success(request.operation == RepositorySyncValidationContinuation.describeOperation &&
        retainedstart.exists(value => RepositorySyncPurposeTestInterruption.descriptionFrontier(value.current)))
    }
    binding <- selectedtarget match {
      case Some(target) => Consequence.success(target -> RepositorySyncDirectoryList.workspace(core, "sm-workflow-repository"))
      case None => _repository_target_c(creation, parameters, request, retained)
    }
    sourceTarget = binding._1
    incomingDependency = RepositorySyncConsumedInputs.binding(core)
    retainedDependency = retained.flatMap(_.dependencyBinding)
    _ <- retainedDependency.fold(Consequence.unit) { value => for {
      session <- sessionid.map(Consequence.success).getOrElse(invalid("Dependency subject requires its existing child Session"))
      _ <- RepositorySyncConsumedInputs.checkBinding(value, session, binding._2)
      _ <- if (incomingDependency.forall(_ == value)) Consequence.unit
        else invalid("RepositorySync call crossed the original dependency subject")
    } yield () }
    selectedDependency = retainedDependency.orElse(incomingDependency)
    sourceCall = RepositorySyncDirectoryList.scopedCore(core, binding._2)
    selectedInput <- if (request.operation == "startRepositorySync") StandardRepositorySyncIntent.inputC(request, sourceTarget).map(_ -> true)
      else retained.flatMap(_.context).map { selected => Consequence.success((RepositorySyncStartInput(selected.repositoryIdentity,
        selected.localBranch, selected.trackingRemote, selected.trackingBranch, selected.remoteEndpointIdentity, selected.syncMode, None, None),
        StandardRepositorySyncIntent.owns(selected))) }.getOrElse(invalid("Session has no native RepositorySync Start context"))
    policies <- if (request.operation == "startRepositorySync") for {
      record <- NativeWorkflowOperationInput.recordC(request, "input")
      selected <- RepositorySyncPolicies.resolveC(parameters, record)
    } yield Some(selected) else Consequence.success(retained.flatMap(_.policies))
    participantSelection <- if (request.operation == "startRepositorySync" && policies.exists(selected =>
        RepositorySyncPolicies.route(selected, sourceTarget.localBranch, sourceTarget.remoteBranch) != "direct")) for {
      host <- RepositorySyncPurposeMaterializationContinuation.hostPlanC(sourceCall)
      source <- RepositorySyncLocalMain.participantC(host, binding._2)
      mappings <- parameters.resolve(RepositorySyncLocalMain.mappings).map(_.value.getOrElse(Map.empty))
      mapped <- mappings.get(source.workspace).map(workspace => RepositorySyncLocalMain.participantC(host, workspace).map(Some(_)))
        .getOrElse(Consequence.success(Option.empty[RepositorySyncLocalMain.Participant]))
      result <- RepositorySyncLocalMain.captureC(sourceCall, host, source, mapped)
        .flatMap { plan => RepositorySyncLocalMain.batchPlan(sourceCall) match {
          case Some(batch) if batch.source == plan.source && batch.main == plan.main && batch.transfer == plan.transfer =>
            Consequence.success(plan.copy(cohort = batch.cohort))
          case Some(_) => invalid("Shared canonical participant differs from the retained batch inventory")
          case None => Consequence.success(plan)
        }}.map(plan => (Some(plan), Option.empty[String])).recover(conclusion => (None, Some("CanonicalMainSetupRequired: " + conclusion.show)))
    } yield result else Consequence.success(retained.flatMap(_.localMain) -> retained.flatMap(_.localMainSetup))
    hop <- participantSelection._1 match {
      case None => Consequence.success(Option.empty[RepositorySyncLocalMain.Hop])
      case Some(plan) if request.operation == "startRepositorySync" =>
        Consequence.success(Some(RepositorySyncLocalMain.Hop(plan, RepositorySyncLocalMain.Cursor("source", None, None))))
      case Some(plan) => for {
        session <- sessionid.map(Consequence.success).getOrElse(invalid("Local-main Session has no canonical identity"))
        storage <- WorkflowComponentStorage.bindC(creation, SmWorkflowComponent.componentId, p.configuration,
          Vector(definition), p.instances, p.continuations)
        instances <- storage.bindInstancesC()(using core.executionContext)
        current <- instances.loadC(InstanceIdentity(session))(using core.executionContext)
          .flatMap(_.map(Consequence.success).getOrElse(invalid("Local-main Session is unavailable")))
        cursor <- RepositorySyncLocalMain.cursorC(storage, current, core)
      } yield Some(RepositorySyncLocalMain.Hop(plan, cursor))
    }
    target = hop.fold(sourceTarget)(_.participant.target)
    workspace = hop.fold(binding._2)(_.participant.workspace)
    call = RepositorySyncConsumedInputs.withBinding(
      RepositorySyncLocalMain.scopedCore(RepositorySyncDirectoryList.scopedCore(core, workspace), hop), selectedDependency)
    input = if (request.operation == "startRepositorySync") selectedInput._1 else selectedInput._1.copy(
      localBranch = target.localBranch, trackingRemote = target.remote, trackingBranch = target.remoteBranch,
      remoteEndpointIdentity = target.endpoint)
    _ <- if (selectedtarget.nonEmpty || request.operation == "startRepositorySync") for {
      development <- parameters.resolve(ManagedDevelopmentRuntime.declaration).map(_.value)
      plan <- RepositorySyncPurposeMaterializationContinuation.hostPlanC(call)
      managed <- ManagedDevelopmentRepositorySync.selectC(development, input.repositoryIdentity, plan,
        Some(target.root.toString), policies.map(_.mainIntegration))
      configured <- if (request.operation == "startRepositorySync")
        parameters.resolve(RepositorySyncPolicies.mainIntegration).map(_.value) else Consequence.success(None)
      _ <- if (managed.exists(_.remoteBranch == "main") && configured.exists(_ != "direct"))
        invalid("Managed dependency remote main conflicts with explicit main-integration; select scoped direct before effects")
        else Consequence.unit
    } yield () else Consequence.unit
    classifications <- RepositorySyncNativeExecution.resolveC(parameters)
    initialized <- if (passive) Consequence.success(WorkflowValidationInitialization.Repository(None, None))
      else shared.validation.repositoryC(target.root, call)
    documentInputs = initialized.documents
    programInputs = initialized.program
    purposePolicy <- RepositorySyncPurpose.resolveC(parameters)
    publication = shared.publication
    ordinaryValidation = if (passive) documentValidation else if (selectedInput._2) normalRepositoryValidation(programInputs, initialized.declarationReadFailure,
      initialized.projectProvidesFixCheck)
      else repositoryValidation
    validation = ordinaryValidation.copy(dependencyInputs = RepositorySyncConsumedInputs.subject(call))
    programEvidence <- if (passive) Consequence.success(Option.empty[RepositorySyncProgramImpact.EvidencePort]) else for {
      goal <- GoalPhaseRuntimeCatalog.loadC()
      storage <- WorkflowComponentStorage.bindC(creation, SmWorkflowComponent.componentId, p.configuration,
        Vector(goal.definition), p.instances, p.continuations)
    } yield Some(RepositorySyncProgramImpact.evidencePort(storage))
    declaration = RepositorySyncManagedFetch.Declaration(p.configuration, p.instances, p.continuations, input,
      target, shared.profiles, shared.origin, classifications, shared.journal, runtimeCleanSync = selectedInput._2, documentInputs = documentInputs, evidencePolicy = shared.evidence,
      programEvidence = programEvidence, purposePolicy = purposePolicy,
      localPublication = publication, policies = policies, policyHuman = Some(shared.ports.repositoryPolicyHuman),
      localMain = participantSelection._1, localMainSetup = participantSelection._2, hop = hop)
    // Before review use actual conflicts; afterwards use that issued review's
    // retained candidate paths so staging does not erase the repair scope.
    paths <- if (passive || request.operation == "startRepositorySync") Consequence.success(Set.empty[String])
      else repositoryPathsC(declaration, definition, request, call)
    installation = RepositorySyncValidationContinuation.Installation(shared.ports.repositoryValidationContinuation)
    service <- if (passive) for {
      _ <- RepositorySyncManagedFetch.validateDeclarationC(declaration)
      _ <- installation.validateC
      operation = RepositorySyncValidationContinuation.passiveOperation(declaration, definition, validation, installation, adoption)
    } yield NativeWorkflowDescriptionMetadata.service(SmWorkflowComponent.RepositorySyncServiceService,
      spec.OperationDefinitionGroup(NonEmptyVector.one(operation)))
    else RepositorySyncManagedServiceAssembly.bindRuntimeC(creation, declaration, nativeProgression = true,
      conflictPolicy = RepositorySyncNativeConflictInput.Policy(paths), validationPolicy = Some(validation),
      nativeReview = Some(RepositorySyncNativeMergeReview.Installation(repositoryReview, shared.ports.repositoryReview)),
      convergence = Some(if (selectedInput._2) StandardRepositorySyncIntent.authority else shared.ports.convergence),
      humanConflict = if (paths.isEmpty) None else Some(RepositorySyncHumanConflict.Installation(shared.ports.repositoryHuman)),
      repositoryContinuation = Some(installation),
      repositoryRepairScopeCorrection = Some(RepositorySyncRepairScopeCorrection.Installation(shared.ports.repositoryRepairScopeCorrection)),
      repairBudgetContinuation = Some(RepositorySyncRepairBudgetContinuation.Installation(shared.ports.repositoryRepairBudgetContinuation)),
      purposePreparation = Some(RepositorySyncPurposePreparationContinuation.Installation(shared.ports.repositoryPurposePreparationContinuation)),
      pushContinuation = Some(RepositorySyncPushContinuation.Installation(shared.ports.repositoryPushContinuation)))
    selected <- RuntimeGitSelections.repositoryC(NativeWorkflowSessionInput.repository(
      NativeWorkflowResultInput.repository(service, creation, declaration, definition), creation, declaration, definition, runtimeIntent = true), paths)
  } yield RepositorySyncLocalMain.bindHop(RepositorySyncDirectoryList.bindWorkspace(
    StandardRepositorySyncIntent.bindInput(selected, input), workspace, selectedDependency), hop)

  /** The same admitted host plan resolves both a directory member and the
    * ordinary lower-level repository entry. Recollection uses its original
    * native root rather than an ambient default target.
    */
  private def _repository_target_c(creation: ComponentCreate, parameters: ComponentInitializationParameters, request: Request,
    retained: Option[RepositorySyncManagedFetch.Payload]): Consequence[(RepositoryGitRuntimePrograms.Target, String)] = for {
    plan <- GoalPhaseInputRecordReadPolicy.storedValue(creation.configuration.configuration.values,
      CncfConfigurationParameterCatalog.SUBSYSTEM_PROCESS_EXECUTION_PLAN_KEY) match {
      case Some(ConfigurationValue.StringValue(value)) => io.circe.parser.parse(value).fold(e => invalid(e.message), Consequence.success)
      case _ => invalid("No managed repository execution profile is configured for this RuntimeRoot")
    }
    repository <- retained.flatMap(_.context).map(value => Consequence.success(value.repositoryIdentity))
      .getOrElse(NativeWorkflowOperationInput.recordC(request, "input").flatMap(record =>
        record.getString("repositoryIdentity").map(Consequence.success)
          .getOrElse(invalid("RepositorySync requires an explicit repositoryIdentity"))))
    development <- parameters.resolve(ManagedDevelopmentRuntime.declaration).map(_.value)
    mainRoute <- retained match {
      case Some(value) => Consequence.success(value.policies.map(_.mainIntegration))
      case None => for {
        configured <- parameters.resolve(ManagedDevelopmentRepositorySync.mainIntegration).map(_.value)
        record <- NativeWorkflowOperationInput.recordC(request, "input")
        selected <- record.getAny("mainIntegration").map(value =>
          RepositorySyncPolicies.decodeC("mainIntegration", value, RepositorySyncPolicies.mainValues).map(Some(_)))
          .getOrElse(Consequence.success(configured))
      } yield selected
    }
    managed <- ManagedDevelopmentRepositorySync.selectC(development, repository, plan, retained.map(_.repositoryRoot), mainRoute)
    root <- managed match {
      case Some(member) => Consequence.success(member.root)
      case None => retained match {
      case Some(value) => Consequence.success(value.repositoryRoot)
      case None => for {
        input <- NativeWorkflowOperationInput.recordC(request, "input")
        identity <- input.getString("repositoryIdentity").map(Consequence.success)
          .getOrElse(invalid("RepositorySync requires an explicit repositoryIdentity"))
        policies <- plan.hcursor.downField("sessionCandidateWorkspaces").focus match {
          case None => Consequence.success(Vector.empty[Json])
          case Some(value) => value.as[Vector[Json]].fold(e => invalid(e.message), Consequence.success)
        }
        owner = creation.instanceMetadata.flatMap(_.componentId).getOrElse(SmWorkflowComponent.componentId).name
        aliases <- policies.foldLeft(Consequence.success(Set.empty[String])) { (prior, policy) => for {
          values <- prior
          component <- policy.hcursor.get[String]("componentId").fold(e => invalid(e.message), Consequence.success)
          alias <- policy.hcursor.get[String]("alias").fold(e => invalid(e.message), Consequence.success)
          _ <- org.goldenport.cncf.workspace.WorkspaceId.parseC(alias)
        } yield if (component == owner) values + alias else values }
        families <- plan.hcursor.get[Vector[Json]]("gitRepositories").fold(e => invalid(e.message), Consequence.success)
        repositories = families.filterNot(_.hcursor.get[String]("target").toOption.exists(aliases))
        matching = families.filter(repository => repository.hcursor.get[String]("identity").contains(identity) ||
          repository.hcursor.get[String]("target").contains(identity))
        // A single explicitly configured legacy binding retains its caller's
        // business repository identity. Multiple bindings require an exact one.
        repository <- uniqueC(if (matching.nonEmpty) matching.filterNot(_.hcursor.get[String]("target").toOption.exists(aliases))
          else if (repositories.size == 1) repositories else Vector.empty,
          "RepositorySync requires one configured repository identity; no destination is guessed")
        workspace <- repository.hcursor.get[String]("target").fold(e => invalid(e.message), Consequence.success)
        workspaces <- plan.hcursor.get[Vector[Json]]("workspaces").fold(e => invalid(e.message), Consequence.success)
        selected <- uniqueC(workspaces.filter(_.hcursor.get[String]("identity").contains(workspace)),
          "RepositorySync requires one configured repository workspace")
        path <- selected.hcursor.get[String]("root").fold(e => invalid(e.message), Consequence.success)
      } yield path
      }
    }
    families <- plan.hcursor.get[Vector[Json]]("gitRepositories").fold(e => invalid(e.message), Consequence.success)
    preferred = families.filter(f => f.hcursor.get[String]("identity").contains(repository) ||
      f.hcursor.get[String]("target").contains(repository)).flatMap(_.hcursor.get[String]("target").toOption).toSet
    members <- RepositorySyncDirectoryList.resolveC(Vector(root), plan, "target-resolution", preferred)
  } yield members.head.target -> members.head.workspace

  private def repositoryPathsC(declaration: RepositorySyncManagedFetch.Declaration,
    definition: DefinitionBinding, request: Request, core: ActionCall.Core): Consequence[Set[String]] = for {
    identity <- _repository_session_c(request, core)
    context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(identity))
    selectedcore = core.copy(executionContext = context)
    session <- RepositorySyncManagedFetch.sessionC(identity, declaration, definition, selectedcore)
    purposestore <- RepositorySyncNativeProgressStore.bindC(session, declaration, selectedcore, _ => Consequence.unit)
    push <- RepositorySyncPushContinuation.selectedC(purposestore, session.started.current)
    boundary = repositoryReviewBoundary(session.started.current, push.map(_.stage.record.revision.value))
    purpose <- purposestore.currentPurposeC(session.started.current)
    paths <- if (purpose.nonEmpty) Consequence.success(purpose.get.payload.purposes.get.candidate.toVector.flatMap(_.changes.map(_.path)).toSet)
      else boundary match {
      case Some(value) => for {
        issued <- session.storage.bindIssuedC(RepositorySyncNativeMergeReviewContinuation.completionOperation,
          RepositorySyncNativeMergeReview.inputCodec)
        retained <- issued.loadC(org.goldenport.cncf.workflow.ContinuationIdentity(value.continuationIdentity.value))(using context)
        work <- retained.map(Consequence.success).getOrElse(invalid("Native merge review input is missing"))
        input <- NativeWorkflowOperationOutput.workInputC(work)
      } yield input.changedPaths.toSet
      case None => for {
        unmerged <- WorkflowManagedProcessExecution.executeGitC(selectedcore,
          org.goldenport.cncf.processexecution.GitOperation.UnmergedIndex).flatMap(_.valueC)
        selected <- if (unmerged.nonEmpty || !session.started.current.currentProgression.exists(p =>
            Set("sync.PendingMergePrepared", "sync.RequiredLintContinuationAdmitted", "sync.ReviewInputContinuationAdmitted", "sync.MergeCheckContinuationAdmitted", "sync.ValidationPreparationRejected", "sync.ValidationChecking", "sync.ValidationRepairRequired", "sync.MergeValidated",
              "sync.RepairBudgetContinuationAdmitted", "sync.RepairProposalAdmitted", "sync.MaterializingRepair")(p.value))) Consequence.success(unmerged.map(_.path.value).toSet)
          else for {
            store <- RepositorySyncNativeProgressStore.bindC(session, declaration, selectedcore, _ => Consequence.unit)
            candidate <- store.currentMergeCandidateC(session.started.current)
            _ <- RepositorySyncNativeMergeValidation.preconditionsC(store, candidate.pending)
            local <- org.goldenport.cncf.processexecution.GitObjectId.parseC(candidate.pending.payload.before.head)
            remote <- org.goldenport.cncf.processexecution.GitObjectId.parseC(candidate.pending.payload.mergeHead.get)
            localPaths <- WorkflowManagedProcessExecution.executeGitC(selectedcore,
              org.goldenport.cncf.processexecution.GitOperation.PendingPaths(local)).flatMap(_.valueC)
            remotePaths <- WorkflowManagedProcessExecution.executeGitC(selectedcore,
              org.goldenport.cncf.processexecution.GitOperation.PendingPaths(remote)).flatMap(_.valueC)
          } yield (localPaths ++ remotePaths).map(_.value).toSet
      } yield selected
    }
    supplied <- if (Set(RepositorySyncRepairScopeCorrection.prepareOperation,
        RepositorySyncRepairScopeCorrection.adoptionOperation)(request.operation))
      RepositorySyncRepairScopeCorrection.requestedInventoryPathsC(request, core) else Consequence.success(Vector.empty[String])
    correction = session.started.current.history.reverseIterator.find(_.causation.value.startsWith(
      RepositorySyncRepairScopeCorrection.resumeCause))
    inventory <- correction match {
      case None => Consequence.success(Vector.empty[String])
      case Some(entry) => for {
        adapter <- session.storage.bindWorkOrderSupersessionC(RepositorySyncNativeMergeRepairContinuation.completionOperation,
          RepositorySyncNativeMergeRepair.inputCodec, RepositorySyncRepairScopeCorrection.checkpointCodec)
        old = entry.causation.value.stripPrefix(RepositorySyncRepairScopeCorrection.resumeCause)
        retained <- adapter.loadRetainedC(org.goldenport.cncf.workflow.ContinuationIdentity(old),
          session.started.current.identity)(using context)
          .flatMap(_.map(Consequence.success).getOrElse(invalid("Automatic inventory has no actual native supersession")))
        target = retained.receipt.payload.direction.target
        _ <- if (target.sessionId == identity && target.context == session.context &&
            retained.receipt.nextRevision == entry.revision && RepositorySyncRepairScopeCorrection.validTarget(target))
          Consequence.unit else invalid("Automatic inventory crosses the current Session or retained history")
        latest = session.started.current.history.reverseIterator.flatMap(_.suspension).find(
          _.continuationIdentity.value.startsWith(identity + ":native-repair:"))
      } yield if (latest.exists(value => RepositorySyncRepairScopeCorrection.budgetIdentity(
        value.continuationIdentity.value) == old)) target.inventory.toVector.flatMap(_.paths) else Vector.empty[String]
    }
    normalpaths = paths ++ (if (declaration.runtimeCleanSync) supplied ++ inventory else Vector.empty[String])
    composed <- if (normalpaths.isEmpty && Set(RepositorySyncRepairBudgetContinuation.describeOperation,
        RepositorySyncRepairBudgetContinuation.adoptionOperation)(request.operation))
      _repair_budget_paths_c(purposestore, session) else Consequence.success(normalpaths)
  } yield composed

  /** Each local-main hop and authenticated push adoption starts a fresh
    * integration lineage. Earlier reviews cannot supply its conflict paths. */
  private[admission] def repositoryReviewBoundary(current: InstanceRecord,
    afterRevision: Option[Long]): Option[SuspensionBoundary] = {
    val identity = current.identity.value
    val history = RepositorySyncLocalMain.phaseHistory(current)
    history.reverseIterator.filter(entry => afterRevision.forall(entry.revision.value > _))
      .flatMap(_.suspension).find { value =>
        val name = value.continuationIdentity.value
        val prefix = Vector(identity + ":native-review:", identity + ":native-triage:").find(name.startsWith)
        val revision = prefix.map(name.stripPrefix).filter(_.matches("[1-9][0-9]*")).flatMap(_.toLongOption)
        revision.exists(source => afterRevision.forall(source > _) && history.exists(entry =>
          entry.revision.value == source && entry.currentProgression.exists(_.value == "sync.MergeValidated")))
      }
  }

  /** Retained budget paths compose only the passive budget Operations after
    * normal scope is empty. The budget handler still authenticates the full
    * direction, evidence and adjacent grant history before recollection. */
  private def _repair_budget_paths_c(store: RepositorySyncNativeProgressStore.Store,
    session: RepositorySyncManagedFetch.Session): Consequence[Set[String]] = {
    val current = session.started.current
    val prefix = "native-repository:" + store.identity + ":"
    current.history.reverseIterator.find(entry => entry.suspension.isEmpty &&
      entry.currentProgression.contains(ProgressionReference("sync.RepairBudgetContinuationAdmitted"))) match {
      case None => Consequence.success(Set.empty[String])
      case Some(entry) => for {
        _ <- if (current.identity.value == store.identity && entry.causation.value.startsWith(prefix) &&
            entry.causation.value.stripPrefix(prefix).matches("repair-budget-[1-9][0-9]*-adopted")) Consequence.unit
          else invalid("Retained budget scope has no exact this-Session native adoption reference")
        saved <- store.loadC(entry.causation.value.stripPrefix(prefix)).flatMap(_.map(Consequence.success)
          .getOrElse(invalid("Retained budget scope adoption receipt is absent")))
        actual <- RepositorySyncPurposeContentContinuation.prefixC(current, entry.revision.value.toString)
        direction <- saved.payload.repairBudget.map(Consequence.success)
          .getOrElse(invalid("Retained budget scope receipt has no direction"))
        _ <- direction.asJson.as[RepositorySyncRepairBudgetContinuation.Direction]
          .fold(error => invalid(error.message), _ => Consequence.unit)
        target = direction.target
        source <- RepositorySyncPurposeContentContinuation.prefixC(current, target.sourceRevision)
        _ <- if (saved.record == actual && saved.record.identity == current.identity &&
            saved.payload.stage == "RepairBudgetContinuationAdmitted" &&
            entry.causation.value == prefix + RepositorySyncRepairBudgetContinuation.adoptionKey(target) &&
            RepositorySyncRepairBudgetContinuation.validTarget(target) && target.sessionId == current.identity.value &&
            target.context == session.context && target.root == store.original.repositoryRoot &&
            target.review.instanceIdentity == current.identity.value && source.identity == current.identity &&
            source.causation.value == target.sourceCause) Consequence.unit
          else invalid("Retained budget composition scope crosses its actual source, Session or native history")
      } yield target.review.changedPaths.toSet
    }
  }

  private def _repository_session_c(request: Request, core: ActionCall.Core): Consequence[String] =
    if (Set(RepositorySyncRepairScopeCorrection.adoptionOperation,
        RepositorySyncRepairScopeCorrection.prepareOperation)(request.operation))
      RepositorySyncRepairScopeCorrection.sessionC(request, core)
    else if (request.operation == RepositorySyncPurposePreparationContinuation.adoptionOperation)
      RepositorySyncPurposePreparationContinuation.sessionC(request, core)
    else if (request.operation == RepositorySyncRepairBudgetContinuation.adoptionOperation)
      RepositorySyncRepairBudgetContinuation.sessionC(request, core)
    else if (request.operation == RepositorySyncValidationContinuation.adoptionOperation)
      RepositorySyncValidationContinuation.sessionC(request, core)
    else if (request.operation == RepositorySyncPushContinuation.adoptionOperation)
      RepositorySyncPushContinuation.sessionC(request, core)
    else NativeWorkflowOperationInput.textC(request, "sessionId")

  private def boundedC(creation: ComponentCreate, parameters: ComponentInitializationParameters, shared: Shared, request: Request,
    core: ActionCall.Core): Consequence[spec.ServiceDefinition] = for {
    delegation <- Consequence.success(request.service.exists(Set("DelegateTaskService", "DelegateTaskDeliveryService")))
    task = delegation || request.service.contains("GoalTaskService")
    native = if (delegation) DelegateTaskNativeRun else if (task) GoalTaskNativeRun else StandaloneFixNativeRun
    definition <- if (delegation) DelegateTaskRuntimeCatalog.loadC() else if (task) GoalTaskRuntimeCatalog.loadC() else StandaloneFixRuntimeCatalog.loadC()
    development <- parameters.resolve(ManagedDevelopmentRuntime.declaration).map(_.value)
    syncRoute <- parameters.resolve(ManagedDevelopmentRepositorySync.mainIntegration).map(_.value)
    retained <- if (request.operation.startsWith("start")) NativeWorkflowOperationInput.optionalTextC(request, "sessionId").flatMap {
      case None => Consequence.success(Option.empty[BoundedWorkRuntime.Payload])
      case Some(_) => for {
        session <- NativeWorkflowOperationInput.startSessionC(request, core.executionContext)
        context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(session))
        storage <- WorkflowComponentStorage.bindC(creation, SmWorkflowComponent.componentId, shared.persistence.configuration,
          Vector(definition), shared.persistence.instances, shared.persistence.continuations)
        instances <- storage.bindInstancesC()(using context)
        current <- instances.loadC(InstanceIdentity(session))(using context)
        value <- current match {
          case None => Consequence.success(Option.empty[BoundedWorkRuntime.Payload])
          case Some(actual) => for {
            key <- NativeWorkflowOperationInput.textC(request, "idempotencyKey")
            participant <- NativeWorkflowOperationInput.textC(request, "participant")
            requested <- if (delegation) NativeWorkflowOperationInput.valueC[org.simplemodeling.textus.smworkflow.value.DelegateTaskInput](request, "input")
              .flatMap(DelegateTask.inputC).map(Some(_)) else Consequence.success(Option.empty[StandaloneFixExecution.Input])
            _ <- if (actual.definition == definition && actual.correlation == CorrelationReference(key)) Consequence.unit
              else invalid("Redelivered Start crosses its original native definition or idempotency key")
            payload <- native.retainedPayloadC(storage, session, core.copy(executionContext = context))
            _ <- if (payload.state.participant == participant && requested.forall(_ == payload.state.input)) Consequence.unit
              else invalid("Redelivered Start changes its complete request or participant")
          } yield Some(payload)
        }
      } yield value
    } else for {
      session <- _bounded_session_c(request, core)
      storage <- WorkflowComponentStorage.bindC(creation, SmWorkflowComponent.componentId, shared.persistence.configuration,
        Vector(definition), shared.persistence.instances, shared.persistence.continuations)
      value <- native.retainedPayloadC(storage, session, core)
    } yield Some(value)
    input <- if (request.operation.startsWith("start")) NativeWorkflowOperationInput.recordC(request, "input").map(DevelopmentOperationalView.json)
      else for {
        session <- _bounded_session_c(request, core)
        storage <- WorkflowComponentStorage.bindC(creation, SmWorkflowComponent.componentId, shared.persistence.configuration,
          Vector(definition), shared.persistence.instances, shared.persistence.continuations)
        retained <- native.retainedInputC(storage, session, core)
      } yield Json.obj("repositoryIdentity" -> Json.fromString(retained.repositoryIdentity),
        "requestIdentity" -> Json.fromString(retained.requestIdentity), "objective" -> Json.fromString(retained.objective),
        "ownedPaths" -> Json.fromValues(retained.ownedPaths.map(Json.fromString)),
        "checks" -> Json.fromValues(retained.checks.map(Json.fromString)))
    repository <- input.hcursor.get[String]("repositoryIdentity").fold(e => invalid(e.message), Consequence.success)
    requestIdentity <- input.hcursor.get[String]("requestIdentity").fold(e => invalid(e.message), Consequence.success)
    objective <- input.hcursor.get[String]("objective").fold(e => invalid(e.message), Consequence.success)
    paths <- input.hcursor.get[Vector[String]]("ownedPaths").fold(e => invalid(e.message), Consequence.success)
    checkIds <- input.hcursor.get[Vector[String]]("checks").fold(e => invalid(e.message), Consequence.success)
    proposed <- if (request.service.contains("GoalTaskService")) _goal_scope_paths_c(request, core)
      else if (request.service.contains("DelegateTaskService")) _delegate_scope_paths_c(request, core)
      else Consequence.success(Vector.empty[String])
    hostPaths = (paths ++ proposed).distinct
    _ <- if (proposed.isEmpty || GoalPhasePurposeRepair.paths(hostPaths)) Consequence.unit
      else invalid("Scope inventory must contain valid repository-relative paths")
    destination = delegation && (retained.flatMap(_.state.input.delegation).exists(_.role == "destination") ||
      input.hcursor.downField("delegation").get[String]("role").contains("destination"))
    plan <- GoalPhaseInputRecordReadPolicy.storedValue(creation.configuration.configuration.values,
      CncfConfigurationParameterCatalog.SUBSYSTEM_PROCESS_EXECUTION_PLAN_KEY) match {
      case Some(ConfigurationValue.StringValue(value)) => io.circe.parser.parse(value).fold(e => invalid(e.message), Consequence.success)
      case _ => invalid("No managed repository execution profile is configured for this RuntimeRoot")
    }
    managed <- if (destination) ManagedDevelopmentBoundedWork.selectC(development, repository, plan, retained.map(p => p.root -> p.branch))
      else Consequence.success(Option.empty[RepositorySyncDirectoryList.Member])
    target <- managed.map(m => Consequence.success(RepositoryGitRuntimePrograms.Target(Paths.get(m.root), m.localBranch,
      m.remote, m.remoteBranch, m.endpoint))).getOrElse(targetC(creation))
    lintRequired = destination && retained.map(_.state.input.scalaLint == "required")
      .getOrElse(input.hcursor.get[String]("scalaLint").contains("required"))
    checks <- boundedChecksC(checkIds, lintRequired, plan, target.root.toString)
    _ <- managed.fold(Consequence.unit)(m => ManagedDevelopmentBoundedWork.validationBindingsC(plan, m, checks))
    _ <- if (retained.forall(p => p.root == target.root.toString && p.branch == target.localBranch)) Consequence.unit
      else invalid("Bounded host differs from the original native Session source")
    p = shared.persistence
    host = BoundedWorkRuntime.Host(p.configuration, p.instances, p.continuations, repository, target.root.toString,
      target.localBranch, hostPaths, checks, shared.profiles, shared.origin, Some(shared.ports.goalReview), shared.evidence, shared.journal,
      shared.publication, shared.hygienemode, shared.completionrecords, Some(shared.operatingcontext),
      development.filter(p => p.dependencyWorkEdge.exists(id => p.definition.edges.exists(e => e.identity == id && e.provider.repository == repository)))
        .map(p => if (retained.nonEmpty) p.copy(delivery = retained.get.dependencyDelivery.map(Vector(_))) else p), syncRoute)
    records <- GoalPhaseInputRecordReadPolicy.decodeC(creation.configuration)
    selections = new ProfileSelectionDataStorePort(records.storeName,
      records.collections.find(_._1 == GoalPhaseInputRecordReadPolicy.Purpose.Selection).get._2)
    service <- if (request.service.contains("DelegateTaskDeliveryService")) DelegateTaskDeliveryService.bindC(creation, host)
      else if (delegation) DelegateTaskService.bindC(creation, host, selections)
      else if (task) GoalTaskService.bindC(creation, host, selections) else StandaloneFixService.bindC(creation, host, selections)
    selected <- if (delegation) RuntimeGitSelections.boundedC(service, paths, requestIdentity, objective, "delegate-task")
      else RuntimeGitSelections.boundedC(service, paths, requestIdentity, objective)
  } yield managed.fold(selected)(m => RepositorySyncDirectoryList.bindWorkspace(selected, m.workspace))

  private def _bounded_session_c(request: Request, core: ActionCall.Core): Consequence[String] =
    if (request.service.contains("DelegateTaskDeliveryService") && request.operation == "observeDelegateTaskDelivery")
      DelegateTaskDeliveryService.inputForCallC(request, core).map(_.sessionId)
    else if (request.operation == DelegateTaskRequestReconstruction.reconstructOperation)
      DelegateTaskRequestReconstruction.inputForCallC(request, core).map(_.sessionId)
    else if (request.operation == "recoverDelegateTaskValidation") DelegateTaskValidationRecovery.inputForCallC(request, core).map(_.sessionId)
    else if (request.operation == "describeDelegateTaskValidationRecovery") DelegateTaskValidationRecovery.descriptionForCallC(request, core).map(_.sessionId)
    else if (request.operation == "adoptGoalTaskValidationContinuation") GoalTaskValidationContinuation.inputForCallC(request, core).map(_.sessionId)
    else if (request.operation == "correctDelegateTaskScope") DelegateTaskScopeCorrection.inputForCallC(request, core).map(_.sessionId)
    else if (request.operation == "correctGoalTaskScope") GoalTaskScopeCorrection.inputForCallC(request, core).map(_.sessionId)
    else NativeWorkflowOperationInput.optionalTextC(request, "sessionId").flatMap {
      case Some(value) => Consequence.success(value)
      case None => NativeWorkflowOperationInput.recordC(request, "input").flatMap(record =>
        record.getString("sessionId").map(Consequence.success).getOrElse(invalid("Bounded input requires its actual sessionId")))
    }

  /** A proposal only widens this call's inspection host. Mutation still requires
    * the independent complete-input selection and the observed native frontier;
    * Git selections continue to use the previously admitted retained inventory.
    */
  private def _goal_scope_paths_c(request: Request, core: ActionCall.Core): Consequence[Vector[String]] =
    if (request.operation == "describeGoalTaskScopeCorrection")
      NativeWorkflowOperationInput.valueC[org.simplemodeling.textus.smworkflow.value.GoalTaskScopeCorrectionDescriptionInput](request, "input")
        .map(_.additionalOwnedPaths.toVector)
    else if (request.operation == "correctGoalTaskScope") for {
      input <- GoalTaskScopeCorrection.inputForCallC(request, core)
      _ <- GoalTaskScopeCorrection.selectionInputC(input)
      target <- GoalTaskScopeCorrection.targetC(input.target)
    } yield target.additionalOwnedPaths
    else Consequence.success(Vector.empty[String])

  private def _delegate_scope_paths_c(request: Request, core: ActionCall.Core): Consequence[Vector[String]] =
    if (request.operation == "describeDelegateTaskScopeCorrection")
      NativeWorkflowOperationInput.valueC[org.simplemodeling.textus.smworkflow.value.DelegateTaskScopeCorrectionDescriptionInput](request, "input")
        .map(_.additionalOwnedPaths.toVector)
    else if (request.operation == "correctDelegateTaskScope") for {
      input <- DelegateTaskScopeCorrection.inputForCallC(request, core)
      _ <- DelegateTaskScopeCorrection.selectionInputC(input)
      target <- DelegateTaskScopeCorrection.targetC(input.target)
    } yield target.additionalOwnedPaths
    else Consequence.success(Vector.empty[String])

  /** Read the normally admitted typed host plan's repository data, not a
    * second command driver, environment override or fabricated Git target.
    */
  private def targetC(creation: ComponentCreate): Consequence[RepositoryGitRuntimePrograms.Target] = for {
    raw <- GoalPhaseInputRecordReadPolicy.storedValue(creation.configuration.configuration.values,
      CncfConfigurationParameterCatalog.SUBSYSTEM_PROCESS_EXECUTION_PLAN_KEY) match {
      case Some(ConfigurationValue.StringValue(value)) => io.circe.parser.parse(value).fold(e => invalid(e.message), Consequence.success)
      case _ => invalid("No managed repository execution profile is configured for this RuntimeRoot")
    }
    repositories <- raw.hcursor.get[Vector[Json]]("gitRepositories").fold(e => invalid(e.message), Consequence.success)
    repository <- uniqueC(repositories.filter(_.hcursor.get[String]("target").contains("sm-workflow-repository")),
      "Managed profile requires exactly one sm-workflow repository target")
    workspaces <- raw.hcursor.get[Vector[Json]]("workspaces").fold(e => invalid(e.message), Consequence.success)
    workspace <- uniqueC(workspaces.filter(_.hcursor.get[String]("identity").contains("sm-workflow-repository")),
      "Managed profile requires exactly one repository workspace")
    root <- workspace.hcursor.get[String]("root").fold(e => invalid(e.message), Consequence.success)
    branch <- repository.hcursor.get[String]("localBranch").fold(e => invalid(e.message), Consequence.success)
    remote <- repository.hcursor.get[String]("remote").fold(e => invalid(e.message), Consequence.success)
    remoteBranch <- repository.hcursor.get[String]("remoteBranch").fold(e => invalid(e.message), Consequence.success)
    endpoint <- repository.hcursor.get[String]("endpoint").fold(e => invalid(e.message), Consequence.success)
  } yield RepositoryGitRuntimePrograms.Target(Paths.get(root), branch, remote, remoteBranch, endpoint)

  /** Deferred destination assembly must expose the configured mandatory lint
    * binding even when it was not an explicit requester check. It retains the
    * actual host ordinal and never widens the original input/check inventory.
    */
  private[admission] def boundedChecksC(ids: Vector[String], lintRequired: Boolean, plan: Json,
    root: String): Consequence[Vector[GoalPhaseNativeValidation.Check]] = for {
    explicit <- standardChecksC(ids)
    // An explicit FixCheck still selects the Session's admitted workspace when
    // lint is not automatically required. Its list position is not a binding.
    checks <- if (!lintRequired && !explicit.exists(check =>
      java.util.Objects.equals(check.kind, WorkflowSbtValidation.Kind.FixCheck)))
      Consequence.success(explicit)
    else for {
      programs <- plan.hcursor.downField("sbtPrograms").as[Vector[Json]].fold(e => invalid(e.message), Consequence.success)
      workspaces <- plan.hcursor.downField("workspaces").as[Vector[Json]].fold(e => invalid(e.message), Consequence.success)
      relevant = programs.filter { program =>
        val target = program.hcursor.downField("target").as[String].toOption
        program.hcursor.downField("operation").as[String].contains("fix-check") && target.exists { alias =>
          alias.matches("sm-workflow-repository-validation-[1-9][0-9]*") &&
            workspaces.exists(w => w.hcursor.downField("identity").as[String].contains(alias) &&
              w.hcursor.downField("root").as[String].contains(root))
        }
      }
      binding <- uniqueC(relevant, "Selected Scala lint needs one unambiguous fix-check binding for the Session checkout")
      alias <- binding.hcursor.downField("target").as[String].fold(e => invalid(e.message), Consequence.success)
      _ <- if (workspaces.count(_.hcursor.downField("identity").as[String].contains(alias)).equals(1)) Consequence.unit
        else invalid("Required Scala lint workspace is ambiguous")
      ordinal <- alias.stripPrefix("sm-workflow-repository-validation-").toIntOption.filter(_ > 0)
        .map(Consequence.success).getOrElse(invalid("Required Scala lint has no bounded validation ordinal"))
      lint = GoalPhaseNativeValidation.Check("fix-check", ordinal, WorkflowSbtValidation.Kind.FixCheck)
    } yield if (explicit.exists(check => java.util.Objects.equals(check.kind, WorkflowSbtValidation.Kind.FixCheck)))
      explicit.map(check => if (java.util.Objects.equals(check.kind, WorkflowSbtValidation.Kind.FixCheck)) lint else check)
      else explicit :+ lint
  } yield checks

  private[admission] def standardChecksC(ids: Vector[String]): Consequence[Vector[GoalPhaseNativeValidation.Check]] =
    ids.distinct.zipWithIndex.foldLeft(Consequence.success(Vector.empty[GoalPhaseNativeValidation.Check])) {
      case (prior, (id, index)) => for {
        accumulated <- prior
        kind <- id match {
          case "test" => Consequence.success(WorkflowSbtValidation.Kind.Test)
          case "test-only" => Consequence.success(WorkflowSbtValidation.Kind.TestOnly)
          case "compile" => Consequence.success(WorkflowSbtValidation.Kind.Compile)
          case "fix-check" => Consequence.success(WorkflowSbtValidation.Kind.FixCheck)
          case _ => invalid("Check " + id + " needs an explicit runtime check binding")
        }
      } yield accumulated :+ GoalPhaseNativeValidation.Check(id, index + 1, kind)
    }

  private[admission] def goalSettingsC(plan: GoalPhaseOrderedProgression.Plan,
    target: RepositoryGitRuntimePrograms.Target): Consequence[ThreeWorkflowRuntimeParameters.Definition] = for {
    _ <- GoalPhaseOrderedProgression.validatePlanC(plan)
    steps <- standardChecksC(plan.steps.flatMap(_.checks).distinct)
    phase <- standardChecksC(plan.phaseChecks)
  } yield {
    val input = GoalPhaseStartInput(plan.phase, plan.repository)
    val goal = ThreeWorkflowRuntimeParameters.Goal(input, plan.phaseOwnedPaths,
      GoalPhaseNativeValidation.Policy("standard-step-checks", 1, steps),
      GoalPhaseNativeValidation.Policy("standard-phase-checks", 1, phase), GoalPhaseNativeReview.Policy("independent-review", 1),
      GoalPhaseNativeClosure.Policy("standard-step-commits", 1, plan.steps.zipWithIndex.map((step,index) =>
        GoalPhaseNativeClosure.StepProgram(step.identity, index + 1)) :+
        GoalPhaseNativeClosure.StepProgram(GoalPhaseNativeReleaseClosure.programIdentity, plan.steps.size + 1)))
    val repository = ThreeWorkflowRuntimeParameters.Repository(RepositorySyncStartInput(plan.repository,
      target.localBranch, target.remote, target.remoteBranch, target.endpoint, "FULL_STATE", None, None), repositoryValidation,
      RepositorySyncNativeConflictInput.Policy(goal.candidatePaths.toSet), repositoryReview)
    ThreeWorkflowRuntimeParameters.Definition(ThreeWorkflowRuntimeParameters.schema,
      ThreeWorkflowRuntimeParameters.Target(target.root.toString, target.localBranch, target.remote, target.remoteBranch, target.endpoint), goal, repository)
  }

  private[admission] def normalRepositoryValidation(programInputs: Option[RepositorySyncProgramImpact.Policy],
    declarationReadFailure: Option[WorkflowProjectDeclaration.ReadFailure] = None,
    projectProvidesFixCheck: Boolean = false): RepositorySyncNativeMergeValidation.Policy = {
    val validation = programInputs.fold(documentValidation.copy(setupRequired =
      declarationReadFailure.map(failure => s"ValidationSetupRequired: ${failure.diagnostic}")))(p => documentValidation.copy(
      identity = "impact-repository-sync", mode = "required", reason = p.reason, checks = p.checks, impact = Some(p),
      assessment = documentValidation.assessment.map(_.copy(lintReason = p.reason))))
    if (projectProvidesFixCheck) validation.copy(assessment = validation.assessment.map(_.copy(
      scalaLint = RepositorySyncMergeAssessment.projectScalaLint,
      lintReason = "The project's project.yaml declares validation.fixCheck for Scala changes"))) else validation
  }

  private def documentValidation = RepositorySyncNativeMergeValidation.Policy("document-repository-sync", 1,
    "explicit-no-product-tests", "Reuse applicable program validation: actual parent/index deltas contain only declared non-consumed documents; no uncovered program behavior is introduced.",
    Vector.empty, Some(RepositorySyncMergeAssessment.Policy("conditional", "not-applicable", "not-applicable",
      "Actual declared document-only changes do not consume Scala or CAR inputs", Vector.empty)))

  private def repositoryReview = RepositorySyncNativeMergeReview.Policy("merge-review", 1)
  private def repositoryValidation = RepositorySyncNativeMergeValidation.Policy("repository-checks", 1, "required",
    "Run the standard repository test before accepting a merge", Vector(RepositorySyncNativeMergeValidation.Check("test", 1)))
  private def uniqueC(values: Vector[Json], message: String): Consequence[Json] =
    if (values.size == 1) Consequence.success(values.head) else invalid(message)

  private def invalid[A](message: String): Consequence[A] = Consequence.configurationInvalid(message)
}
