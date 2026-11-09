package org.simplemodeling.textus.smworkflow.admission

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
import org.simplemodeling.textus.smworkflow.value.{GoalPhaseDevelopmentReconstructionInput, GoalPhaseStartInput, RepositorySyncStartInput}

/** Standard composition contains policies and logical destinations, never a
  * selected Phase or Plan. Read actual Start input or the retained native run
  * only when its Operation executes, within that call's ordinary storage/UoW.
  * @since   Oct. 27, 2026
  *  version Oct. 28, 2026
  * @version Nov.  5, 2026
  */
private[smworkflow] object StandardWorkflowRuntime {
  private final case class Shared(persistence: GoalPhaseRuntimeParameters.Persistence,
    ports: ThreeWorkflowExternalEvidencePorts, profiles: Option[WorkflowExecutionProfileMapping],
    origin: Option[GoalPhaseNativeExecution.Origin], journal: Option[DevelopmentJournal.Policy],
    evidence: Option[CommonEvidence.Policy], operatingcontext: GoalPhaseOperatingProfiles.Context,
    publication: Option[LocalPublicationRuntime.Policy], hygienemode: String,
    completionrecords: Option[CompletionRecords.Policy])

  def bindForCallC(creation: ComponentCreate, parameters: ComponentInitializationParameters,
    request: Request, core: ActionCall.Core): Consequence[Vector[spec.ServiceDefinition]] = for {
    shared <- sharedC(creation, parameters)
    services <- request.service match {
      case Some("SplitPhaseService") => splitC(creation, parameters, shared, core).map(Vector(_))
      case Some("GoalPhaseService") => goalC(creation, parameters, shared, request, core).map(Vector(_))
      case Some("RepositorySyncService") if RepositorySyncDirectoryList.operations(request.operation) =>
        Consequence.success(Vector(RepositorySyncDirectoryList.bind(creation, parameters, shared.persistence,
          (selectedrequest, selectedcore, selectedtarget) =>
            repositoryC(creation, parameters, shared, selectedrequest, selectedcore, Some(selectedtarget)))))
      case Some("RepositorySyncService") => repositoryC(creation, parameters, shared, request, core).map(Vector(_))
      case Some("JournalService") => Consequence.success(Vector(DevelopmentJournalService.bind(shared.journal)))
      case Some("EvidenceService") => Consequence.success(Vector(CommonEvidenceService.bind(shared.evidence, shared.journal)))
      case Some("StandaloneFixService") | Some("GoalTaskService") | Some("DelegateTaskService") | Some("DelegateTaskDeliveryService") => boundedC(creation, shared, request, core).map(Vector(_))
      case Some("LocalPublicationService") => for {
        policy <- Consequence.success(shared.publication)
        definition <- GoalPhaseRuntimeCatalog.loadC().map(_.definition)
      } yield Vector(LocalPublicationService.bind(policy, (id, call) =>
        retainedGoalC(creation, shared.persistence, definition, id, call).flatMap(payload =>
          GoalPhaseParticipantAdmission.admitC(payload.participantIdentity, call.executionContext))))
      case Some("DevelopmentObservationService") => parameters.resolve(ThreeWorkflowRuntimeParameters.developmentObservation).flatMap {
        case value if value.value.isEmpty => Consequence.success(Vector(DevelopmentObservationService.bindNotConfigured))
        case value =>
          val session = value.value.get.subject.sessionId
          goalC(creation, parameters, shared, request, core, Some(session)).map(Vector(_))
      }
      case _ => Consequence.configurationInvalid("Standard runtime has no matching service")
    }
  } yield services

  private def sharedC(creation: ComponentCreate, parameters: ComponentInitializationParameters): Consequence[Shared] = for {
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
    evidence <- CommonEvidence.resolvePolicyC(parameters, Vector(persistence.instances, persistence.continuations) ++ journal.map(_.collection))
  } yield Shared(persistence, new ThreeWorkflowExternalEvidencePorts(reader), profiles, origin, journal, evidence,
    operatingcontext, publication, hygiene, records)

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
    session <- if (request.operation == "startGoalPhase") Consequence.success(Option.empty[String])
      else if (request.operation == GoalPhaseSourceHistoryNormalization.adoptionOperation)
        GoalPhaseSourceHistoryNormalization.inputForCallC(request, core).map(value => Some(value.sessionId))
      else reconstruction.map(value => Consequence.success(Some(value.sessionId))).getOrElse(
        ownerSession.map(value => Consequence.success(Some(value))).getOrElse(goalSessionC(request).map(Some(_))))
    loaded <- if (request.operation == "startGoalPhase") for {
      business <- NativeWorkflowOperationInput.recordC(request, "input").flatMap(
        NativeWorkflowStartInput.fromRecordC(NativeWorkflowStartInput.Kind.GoalPhase, _))
      records <- GoalPhaseInputRecordReadPolicy.decodeC(creation.configuration)
      selected <- new ProfileSelectionDataStorePort(records.storeName,
        records.collections.find(_._1 == GoalPhaseInputRecordReadPolicy.Purpose.Selection).get._2)
        .loadForBusinessC(business, SecuritySubject.from(core.executionContext.security).subjectId, core)
        .flatMap(_.map(Consequence.success).getOrElse(invalid("Start has no selected business input")))
      plans <- GoalPhaseNativeInputReadPolicy.decodeC(creation.configuration)
      plan <- new GoalPhaseSelectedPlanDataStorePort(plans.storeName, plans.plans).loadForCallC(selected.selectionId, core)
        .flatMap(_.map(Consequence.success).getOrElse(invalid("Start requires its selected complete Plan")))
    } yield (plan.plan, Some(plan), Some(selected))
    else for {
      retained <- retainedGoalC(creation, shared.persistence, catalog.definition, session.get, core)
    } yield (retained.plan, Option.empty[GoalPhaseNativeRun.SelectedPlan], Option.empty[ProfileStartAdmission.Selection])
    target <- targetC(creation)
    settings <- goalSettingsC(loaded._1, target)
    initial <- ThreeWorkflowRuntimeParameters.decodeSettingsC(creation, parameters, core.executionContext, settings)
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
    service <- GoalPhaseNativeServiceAssembly.bindC(creation, composition.goal, plans)
    described = if (request.service.contains("DevelopmentObservationService"))
      DevelopmentObservationService.bind(composition.goal, catalog.definition)
    else NativeWorkflowSessionInput.goal(NativeWorkflowResultInput.goal(service, creation, composition.goal, catalog.definition),
      creation, composition.goal, catalog.definition, loaded._3)
    selected <- RuntimeGitSelections.goalC(described, binding._1, binding._2)
  } yield selected

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
    retained <- if (request.operation == "startRepositorySync") Consequence.success(Option.empty[RepositorySyncManagedFetch.Payload])
      else for {
        session <- _repository_session_c(request, core)
        context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(session))
        storage <- WorkflowComponentStorage.bindC(creation, SmWorkflowComponent.componentId, p.configuration,
          Vector(definition), p.instances, p.continuations)
        starts <- storage.bindMechanicalC(RepositorySyncManagedFetch.startOperation, RepositorySyncManagedFetch.codec(true))
        retained <- starts.loadRetainedC(RepositorySyncManagedFetch.reference(session, "start"))(using context)
        selected <- retained.map(value => Consequence.success(value.receipt.payload.value))
          .getOrElse(invalid("Session has no native RepositorySync Start"))
      } yield Some(selected)
    binding <- selectedtarget match {
      case Some(target) => Consequence.success(target -> RepositorySyncDirectoryList.workspace(core, "sm-workflow-repository"))
      case None => _repository_target_c(creation, request, retained)
    }
    target = binding._1
    call = RepositorySyncDirectoryList.scopedCore(core, binding._2)
    selectedInput <- if (request.operation == "startRepositorySync") StandardRepositorySyncIntent.inputC(request, target).map(_ -> true)
      else retained.flatMap(_.context).map { selected => Consequence.success((RepositorySyncStartInput(selected.repositoryIdentity,
        selected.localBranch, selected.trackingRemote, selected.trackingBranch, selected.remoteEndpointIdentity, selected.syncMode),
        StandardRepositorySyncIntent.owns(selected))) }.getOrElse(invalid("Session has no native RepositorySync Start context"))
    input = selectedInput._1
    classifications <- RepositorySyncNativeExecution.resolveC(parameters)
    documentInputs <- RepositorySyncDocumentInputs.resolveC(parameters)
    programInputs <- RepositorySyncProgramImpact.resolveC(parameters)
    purposePolicy <- RepositorySyncPurpose.resolveC(parameters)
    publication = shared.publication
    validation = if (selectedInput._2) programInputs.fold(documentValidation)(p => documentValidation.copy(
      identity = "impact-repository-sync", mode = "required", reason = p.reason, checks = p.checks, impact = Some(p),
      assessment = documentValidation.assessment.map(_.copy(
        lintReason = p.reason)))) else repositoryValidation
    goalDefinition <- GoalPhaseRuntimeCatalog.loadC()
    evidenceStorage <- WorkflowComponentStorage.bindC(creation, SmWorkflowComponent.componentId, p.configuration,
      Vector(goalDefinition.definition), p.instances, p.continuations)
    declaration = RepositorySyncManagedFetch.Declaration(p.configuration, p.instances, p.continuations, input,
      target, shared.profiles, shared.origin, classifications, shared.journal, runtimeCleanSync = selectedInput._2, documentInputs = documentInputs, evidencePolicy = shared.evidence,
      programEvidence = Some(RepositorySyncProgramImpact.evidencePort(evidenceStorage)), purposePolicy = purposePolicy,
      localPublication = publication)
    // Before review use actual conflicts; afterwards use that issued review's
    // retained candidate paths so staging does not erase the repair scope.
    paths <- if (request.operation == "startRepositorySync") Consequence.success(Set.empty[String])
      else repositoryPathsC(declaration, definition, request, call)
    service <- RepositorySyncManagedServiceAssembly.bindRuntimeC(creation, declaration, nativeProgression = true,
      conflictPolicy = RepositorySyncNativeConflictInput.Policy(paths), validationPolicy = Some(validation),
      nativeReview = Some(RepositorySyncNativeMergeReview.Installation(repositoryReview, shared.ports.repositoryReview)),
      convergence = Some(if (selectedInput._2) StandardRepositorySyncIntent.authority else shared.ports.convergence),
      humanConflict = if (paths.isEmpty) None else Some(RepositorySyncHumanConflict.Installation(shared.ports.repositoryHuman)),
      repositoryContinuation = Some(RepositorySyncValidationContinuation.Installation(shared.ports.repositoryValidationContinuation)),
      repositoryRepairScopeCorrection = Some(RepositorySyncRepairScopeCorrection.Installation(shared.ports.repositoryRepairScopeCorrection)),
      repairBudgetContinuation = Some(RepositorySyncRepairBudgetContinuation.Installation(shared.ports.repositoryRepairBudgetContinuation)),
      purposePreparation = Some(RepositorySyncPurposePreparationContinuation.Installation(shared.ports.repositoryPurposePreparationContinuation)),
      pushContinuation = Some(RepositorySyncPushContinuation.Installation(shared.ports.repositoryPushContinuation)))
    selected <- RuntimeGitSelections.repositoryC(NativeWorkflowSessionInput.repository(
      NativeWorkflowResultInput.repository(service, creation, declaration, definition), creation, declaration, definition, runtimeIntent = true), paths)
  } yield RepositorySyncDirectoryList.bindWorkspace(StandardRepositorySyncIntent.bindInput(selected, input), binding._2)

  /** The same admitted host plan resolves both a directory member and the
    * ordinary lower-level repository entry. Recollection uses its original
    * native root rather than an ambient default target.
    */
  private def _repository_target_c(creation: ComponentCreate, request: Request,
    retained: Option[RepositorySyncManagedFetch.Payload]): Consequence[(RepositoryGitRuntimePrograms.Target, String)] = for {
    plan <- GoalPhaseInputRecordReadPolicy.storedValue(creation.configuration.configuration.values,
      CncfConfigurationParameterCatalog.SUBSYSTEM_PROCESS_EXECUTION_PLAN_KEY) match {
      case Some(ConfigurationValue.StringValue(value)) => io.circe.parser.parse(value).fold(e => invalid(e.message), Consequence.success)
      case _ => invalid("No managed repository execution profile is configured for this RuntimeRoot")
    }
    root <- retained match {
      case Some(value) => Consequence.success(value.repositoryRoot)
      case None => for {
        input <- NativeWorkflowOperationInput.recordC(request, "input")
        identity <- input.getString("repositoryIdentity").map(Consequence.success)
          .getOrElse(invalid("RepositorySync requires an explicit repositoryIdentity"))
        repositories <- plan.hcursor.get[Vector[Json]]("gitRepositories").fold(e => invalid(e.message), Consequence.success)
        matching = repositories.filter(repository => repository.hcursor.get[String]("identity").contains(identity) ||
          repository.hcursor.get[String]("target").contains(identity))
        // A single explicitly configured legacy binding retains its caller's
        // business repository identity. Multiple bindings require an exact one.
        repository <- uniqueC(if (matching.nonEmpty) matching else if (repositories.size == 1) repositories else Vector.empty,
          "RepositorySync requires one configured repository identity; no destination is guessed")
        workspace <- repository.hcursor.get[String]("target").fold(e => invalid(e.message), Consequence.success)
        workspaces <- plan.hcursor.get[Vector[Json]]("workspaces").fold(e => invalid(e.message), Consequence.success)
        selected <- uniqueC(workspaces.filter(_.hcursor.get[String]("identity").contains(workspace)),
          "RepositorySync requires one configured repository workspace")
        path <- selected.hcursor.get[String]("root").fold(e => invalid(e.message), Consequence.success)
      } yield path
    }
    members <- RepositorySyncDirectoryList.resolveC(Vector(root), plan, "target-resolution")
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

  /** An authenticated push adoption starts a fresh integration lineage. Earlier
    * reviews retain their evidence but cannot supply the new conflict paths. */
  private[admission] def repositoryReviewBoundary(current: InstanceRecord,
    afterRevision: Option[Long]): Option[SuspensionBoundary] = {
    val identity = current.identity.value
    current.history.reverseIterator.filter(entry => afterRevision.forall(entry.revision.value > _))
      .flatMap(_.suspension).find { value =>
        val name = value.continuationIdentity.value
        val prefix = Vector(identity + ":native-review:", identity + ":native-triage:").find(name.startsWith)
        val revision = prefix.map(name.stripPrefix).filter(_.matches("[1-9][0-9]*")).flatMap(_.toLongOption)
        revision.exists(source => afterRevision.forall(source > _) && current.history.exists(entry =>
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

  private def boundedC(creation: ComponentCreate, shared: Shared, request: Request,
    core: ActionCall.Core): Consequence[spec.ServiceDefinition] = for {
    target <- targetC(creation)
    delegation = request.service.exists(Set("DelegateTaskService", "DelegateTaskDeliveryService"))
    task = delegation || request.service.contains("GoalTaskService")
    native = if (delegation) DelegateTaskNativeRun else if (task) GoalTaskNativeRun else StandaloneFixNativeRun
    definition <- if (delegation) DelegateTaskRuntimeCatalog.loadC() else if (task) GoalTaskRuntimeCatalog.loadC() else StandaloneFixRuntimeCatalog.loadC()
    input <- if (request.operation.startsWith("start")) NativeWorkflowOperationInput.recordC(request, "input").map(DevelopmentOperationalView.json)
      else for {
        session <- _bounded_session_c(request)
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
    checks <- standardChecksC(checkIds)
    p = shared.persistence
    host = BoundedWorkRuntime.Host(p.configuration, p.instances, p.continuations, repository, target.root.toString,
      target.localBranch, paths, checks, shared.profiles, shared.origin, Some(shared.ports.goalReview), shared.evidence, shared.journal,
      shared.publication, shared.hygienemode, shared.completionrecords, Some(shared.operatingcontext))
    records <- GoalPhaseInputRecordReadPolicy.decodeC(creation.configuration)
    selections = new ProfileSelectionDataStorePort(records.storeName,
      records.collections.find(_._1 == GoalPhaseInputRecordReadPolicy.Purpose.Selection).get._2)
    service <- if (request.service.contains("DelegateTaskDeliveryService")) DelegateTaskDeliveryService.bindC(creation, host)
      else if (delegation) DelegateTaskService.bindC(creation, host, selections)
      else if (task) GoalTaskService.bindC(creation, host, selections) else StandaloneFixService.bindC(creation, host, selections)
    selected <- if (delegation) RuntimeGitSelections.boundedC(service, paths, requestIdentity, objective, "delegate-task")
      else RuntimeGitSelections.boundedC(service, paths, requestIdentity, objective)
  } yield selected

  private def _bounded_session_c(request: Request): Consequence[String] =
    NativeWorkflowOperationInput.optionalTextC(request, "sessionId").flatMap {
      case Some(value) => Consequence.success(value)
      case None => NativeWorkflowOperationInput.recordC(request, "input").flatMap(record =>
        record.getString("sessionId").map(Consequence.success).getOrElse(invalid("Bounded input requires its actual sessionId")))
    }

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
      target.localBranch, target.remote, target.remoteBranch, target.endpoint, "FULL_STATE"), repositoryValidation,
      RepositorySyncNativeConflictInput.Policy(goal.candidatePaths.toSet), repositoryReview)
    ThreeWorkflowRuntimeParameters.Definition(ThreeWorkflowRuntimeParameters.schema,
      ThreeWorkflowRuntimeParameters.Target(target.root.toString, target.localBranch, target.remote, target.remoteBranch, target.endpoint), goal, repository)
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
