package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as NativeIdentity, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import BoundedWorkRuntime.Host
import GoalPhaseNativeRun as F
import GoalPhaseNativeWork as A
import StandaloneFixExecution as S

/** Composition over existing native WorkOrders, bounded checkpoints and local
  * artifact operations. This owns no queue, semantic executor or repair loop.
  * @since   Nov. 12, 2026
  * @version Nov. 23, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object ManagedDevelopmentBoundedWork {
  import ManagedDevelopment.*
  import GoalPhaseDependencyValidation.ManagedCapture
  import DependencyDevelopmentDelivery as D

  final case class Captured(capture: ManagedCapture, route: Option[D.Route])

  def edgeC(policy: ManagedDevelopmentRuntime.Policy): Consequence[Edge] = for {
    _ <- validateC(policy.definition)
    identity <- policy.dependencyWorkEdge.map(Consequence.success).getOrElse(invalid("no dependency-work edge is selected"))
    edge <- policy.definition.edges.find(_.identity == identity).map(Consequence.success)
      .getOrElse(invalid("dependency-work edge is not declared"))
  } yield edge

  /** Source/JAR/project bindings remain independent. This selects the existing
    * destination owner, never the requester or another repository's Task. */
  def selectC(policy: Option[ManagedDevelopmentRuntime.Policy], repository: String, plan: Json,
    retained: Option[(String, String)]): Consequence[Option[RepositorySyncDirectoryList.Member]] =
    policy.filter(_.dependencyWorkEdge.nonEmpty) match {
      case None => Consequence.success(None)
      case Some(p) => for {
        edge <- edgeC(p)
        member <- if (edge.provider.repository != repository) Consequence.success(None) else for {
          workspaces <- plan.hcursor.get[Vector[Json]]("workspaces").fold(e => invalid(e.message), Consequence.success)
          selected = workspaces.filter(_.hcursor.get[String]("identity").contains(edge.provider.workspace))
          _ <- if (selected.size == 1) Consequence.unit else invalid("provider workspace is missing or ambiguous")
          root <- selected.head.hcursor.get[String]("root").fold(e => invalid(e.message), Consequence.success)
          members <- RepositorySyncDirectoryList.resolveC(Vector(root), plan, "managed-dependency-work")
          member <- members.headOption.filter(_ => members.size == 1).map(Consequence.success)
            .getOrElse(invalid("provider has no unique native repository binding"))
          _ <- if (member.workspace == edge.provider.workspace && member.repositoryIdentity == edge.provider.repository &&
            retained.forall(_ == (member.root -> member.localBranch))) Consequence.unit
            else invalid("provider differs from the admitted edge or original bounded Session source")
        } yield Some(member)
      } yield member
    }

  def publicationInputC(policy: ManagedDevelopmentRuntime.Policy, input: S.Input): Consequence[Input] = for {
    edge <- edgeC(policy)
    contract <- input.delegation.filter(c => DelegateTask.valid(c) && c.role == "destination")
      .map(Consequence.success).getOrElse(invalid("scoped delivery requires its destination contract"))
    operation = contract.providerPhase.map(p => Input(edge.identity, input.requestIdentity, p.publication, p.producerSelection))
      .getOrElse(Input(edge.identity, input.requestIdentity, input.requestIdentity, input.requestIdentity))
    _ <- ManagedDevelopment.edgeC(policy.definition, operation)
    coordinate = edge.requested.copy(version = version(edge.requested.version, policy.definition.scope, operation.publication))
    _ <- if (input.repositoryIdentity == edge.provider.repository && contract.componentIdentity == edge.provider.identity &&
      contract.publicationTarget.contains(edge.identity) &&
      contract.artifactCoordinates == Vector(coordinateString(coordinate)))
      Consequence.unit else invalid("managed destination requires its exact scoped publication target and development coordinates")
  } yield operation

  private def _admit_publication_c(host: Host, policy: ManagedDevelopmentRuntime.Policy, edge: Edge,
    input: S.Input): Consequence[Unit] = edge.machineLocalJar match {
    case None => publicationInputC(policy, input).map(_ => ())
    case Some(jar) => for {
      _ <- if (id(input.requestIdentity) && input.repositoryIdentity == edge.provider.repository &&
        input.delegation.exists(c => DelegateTask.valid(c) && c.role == "destination" &&
          c.componentIdentity == edge.provider.identity && c.publicationTarget.contains(jar.publicationTarget) &&
          c.artifactCoordinates == Vector(coordinateString(edge.requested)) && c.providerPhase.isEmpty &&
          c.synchronization.isEmpty && !c.permittedOperations.contains("repository-sync"))) Consequence.unit
        else invalid("machine-common delivery requires its exact ordinary publication target and original coordinates")
      _ <- ManagedDevelopmentRuntime.machineTargetC(edge, host.publication)
    } yield ()
  }

  /** Validate the actual ordinal aliases consumed by bounded validation, including
    * TestCompile/inventory preparation. Selecting B's Git workspace alone cannot
    * retarget these aliases. An unavailable binding is a configuration failure. */
  def validationBindingsC(plan: Json, member: RepositorySyncDirectoryList.Member,
    checks: Vector[GoalPhaseNativeValidation.Check]): Consequence[Unit] = for {
    workspaces <- plan.hcursor.get[Vector[Json]]("workspaces").fold(e => invalid(e.message), Consequence.success)
    programs <- plan.hcursor.get[Vector[Json]]("sbtPrograms").fold(e => invalid(e.message), Consequence.success)
    _ <- checks.foldLeft(Consequence.unit) { (prior, check) => prior.flatMap { _ =>
      val alias = "sm-workflow-repository-validation-" + check.ordinal
      val roots = workspaces.filter(_.hcursor.get[String]("identity").contains(alias))
      val bindings = programs.filter(p => p.hcursor.get[String]("target").contains(alias) &&
        p.hcursor.get[String]("operation").contains(check.kind.wire))
      if (roots.size == 1 && roots.head.hcursor.get[String]("root").contains(member.root) && bindings.size == 1)
        Consequence.unit
      else invalid("Managed B validation " + alias + "/" + check.kind.wire +
        " does not uniquely cover selected worktree " + member.root)
    }}
  } yield ()

  private def consumerSourceC(edge: Edge, root: String,
    core: ActionCall.Core): Consequence[Unit] = for {
    raw <- core.executionContext.resolvedParameters.get(
      org.goldenport.cncf.config.CncfConfigurationParameterCatalog.SUBSYSTEM_PROCESS_EXECUTION_PLAN_KEY).map(_.value) match {
      case Some(org.goldenport.configuration.ConfigurationValue.StringValue(value)) =>
        io.circe.parser.parse(value).fold(e => invalid(e.message), Consequence.success)
      case _ => invalid("No actual consumer workspace execution plan is configured")
    }
    workspaces <- raw.hcursor.get[Vector[Json]]("workspaces").fold(e => invalid(e.message), Consequence.success)
    selected = workspaces.filter(_.hcursor.get[String]("identity").contains(edge.consumer.workspace))
    _ <- if (selected.size == 1 && selected.head.hcursor.get[String]("root").contains(root))
      Consequence.unit else invalid("Requester native Start crosses its configured consumer worktree/root")
  } yield ()

  def acceptedPublicationSource(source: Json, commit: S.Commit): Boolean =
    acceptedPublicationSource(source, commit.identity, commit.tree)
  def acceptedPublicationSource(source: Json, head: String, tree: String): Boolean =
    source.hcursor.get[String]("head").contains(head) &&
      source.hcursor.get[String]("tree").contains(tree) &&
      source.hcursor.get[Vector[Json]]("dirty").contains(Vector.empty) &&
      source.hcursor.get[Vector[Json]]("dirtyContents").contains(Vector.empty)

  private def goalStorageC(h: Host, core: ActionCall.Core): Consequence[WorkflowComponentStorage] = for {
    catalog <- GoalPhaseRuntimeCatalog.loadC()
    storage <- WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
      SmWorkflowComponent.componentId, h.configuration, Vector(catalog.definition), h.instances, h.continuations)
  } yield storage

  /** The sole A locator is an explicit sourceReference to the actual pending
    * native WorkOrder, with its expected revision. No chat/path proximity. */
  def captureC(h: Host, input: S.Input, core: ActionCall.Core): Consequence[Option[ManagedCapture]] =
    if (h.managedDevelopment.exists(_.dependencyWorkEdge.nonEmpty))
      captureSelectedC(h, input, core).map(_.map(_.capture))
    else Consequence.success(None)

  def requesterRouteC(h: Host, input: S.Input, core: ActionCall.Core): Consequence[Option[Captured]] =
    if (!input.delegation.exists(contract => contract.role == "requester" && contract.providerPhase.isEmpty))
      Consequence.success(None)
    else _selected_reference_c(h, input.sourceReferences, core).flatMap {
      case None =>
        val machine = h.managedDevelopment.toVector.flatMap(p => p.definition.edges.filter(e =>
          p.dependencyWorkEdge.contains(e.identity) && e.machineLocalJar.exists(jar => input.delegation.exists(c =>
            c.publicationTarget.contains(jar.publicationTarget) || (input.repositoryIdentity == e.provider.repository &&
              c.componentIdentity == e.provider.identity)))))
        if (machine.isEmpty) Consequence.success(None)
        else invalid("machine-common delivery requires the exact selected route on the actual pending A WorkOrder")
      case Some((storage, reference, issued)) => for {
        policy <- h.managedDevelopment.filter(_.dependencyWorkEdge.nonEmpty).map(Consequence.success)
          .getOrElse(invalid("selected dependency delivery requires its registered managed destination"))
        edge <- edgeC(policy)
        captured <- captureIssuedC(h, storage, edge, reference, issued, core)
        route <- captured._2.decisionContext.flatMap(_.deliveryRoute).map(Consequence.success)
          .getOrElse(invalid("requester has no selected route on its actual pending WorkOrder"))
        // Reuse the existing target/coordinate validator without starting the destination.
        _ <- _admit_publication_c(h, policy, edge, input.copy(delegation = input.delegation.map(_.copy(role = "destination"))))
        _ <- _admit_selected_route_c(h, policy, input, captured._2, route, None, core)
      } yield Some(Captured(captured._1, Some(route)))
    }

  def captureSelectedC(h: Host, input: S.Input, core: ActionCall.Core): Consequence[Option[Captured]] =
    h.managedDevelopment.filter(_.dependencyWorkEdge.nonEmpty) match {
      case None if input.delegation.exists(_.role == "destination") =>
        _selected_reference_c(h, input.sourceReferences, core).flatMap {
          case None => Consequence.success(None)
          case Some(_) => invalid("selected dependency delivery requires its registered managed destination")
        }
      case Some(policy) if input.delegation.exists(_.role == "destination") => for {
        edge <- edgeC(policy)
        _ <- _admit_publication_c(h, policy, edge, input)
        storage <- goalStorageC(h, core)
        found <- _find_issued_c(storage, input.sourceReferences, core)
        _ <- if (found.size == 1) Consequence.unit else invalid("managed B requires one explicit actual pending A WorkOrder reference")
        pair = found.head
        captured <- captureIssuedC(h, storage, edge, pair._1, pair._2, core)
        route <- captured._2.decisionContext.flatMap(_.deliveryRoute) match {
          case None if edge.machineLocalJar.nonEmpty => invalid("machine-common delivery requires the exact selected route on the actual pending A WorkOrder")
          case None => Consequence.success(Option.empty[D.Route])
          case Some(route) => _admit_selected_route_c(h, policy, input, captured._2, route, Some(h.root -> h.branch), core)
            .map(_ => Some(route))
        }
      } yield Some(Captured(captured._1, route))
      case _ => Consequence.success(None)
    }

  private def _selected_reference_c(host: Host, references: Vector[ContextReference], core: ActionCall.Core):
      Consequence[Option[(WorkflowComponentStorage, ContextReference, WorkflowInteraction[A.Request, Nothing])]] = {
    val candidates = references.filter(reference =>
      reference.identity.contains(":ordered-implementation:") || reference.identity.contains(":ordered-repair:"))
    if (candidates.isEmpty) Consequence.success(None) else for {
      storage <- goalStorageC(host, core)
      found <- _find_issued_c(storage, candidates, core)
      selected <- found.foldLeft(Consequence.success(Vector.empty[(ContextReference, WorkflowInteraction[A.Request, Nothing])])) {
        (prior, pair) => for {
          all <- prior
          work <- NativeWorkflowOperationOutput.workInputC(pair._2)
        } yield if (work.decisionContext.exists(_.deliveryRoute.nonEmpty)) all :+ pair else all
      }
      result <- selected match {
        case Vector() => Consequence.success(None)
        case Vector((reference, issued)) if found.size == 1 => Consequence.success(Some((storage, reference, issued)))
        case _ => invalid("selected dependency delivery requires one explicit actual pending GoalPhase WorkOrder")
      }
    } yield result
  }

  private def _find_issued_c(storage: WorkflowComponentStorage, references: Vector[ContextReference], core: ActionCall.Core):
      Consequence[Vector[(ContextReference, WorkflowInteraction[A.Request, Nothing])]] = for {
    issued <- storage.bindIssuedC(F.completionOperation, A.inputCodec)
    found <- references.foldLeft(Consequence.success(Vector.empty[(ContextReference, WorkflowInteraction[A.Request, Nothing])])) {
      (prior, reference) => for {
        all <- prior
        value <- issued.loadC(ContinuationIdentity(reference.identity))(using core.executionContext)
      } yield all ++ value.toVector.map(reference -> _)
    }
  } yield found

  private def _admit_selected_route_c(host: Host, policy: ManagedDevelopmentRuntime.Policy, input: S.Input,
    work: A.Request, route: D.Route, providersource: Option[(String, String)], core: ActionCall.Core,
    checkinventory: Boolean = true): Consequence[Unit] = for {
    context <- work.decisionContext.map(Consequence.success)
      .getOrElse(invalid("selected dependency route has no actual issued approval"))
    proposal <- context.requestingResult.dependencyProposal.map(Consequence.success)
      .getOrElse(invalid("selected dependency route has no actual approved proposal"))
    effective = context.inventoryContinuation.map(_.proposal).getOrElse(proposal)
    contract <- input.delegation.map(Consequence.success)
      .getOrElse(invalid("selected dependency route requires its exact destination contract"))
    _ <- if (contract.providerPhase.nonEmpty)
      invalid("selected bounded route cannot start or replace a provider Phase")
      else if (contract.synchronization.nonEmpty || contract.permittedOperations.contains("repository-sync"))
        invalid("selected bounded route allows local delivery only; repository synchronization needs separate authority")
      else if (D.routeValid(route, context.choice, effective) &&
        providersource.forall(_ == (route.sourceRoot -> route.sourceBranch)) && route.destinationIdentity == contract.destinationIdentity &&
        context.decisionReference.exists(contract.authorizationReferences.contains) &&
        (!checkinventory || input.ownedPaths.forall(effective.paths.contains))) Consequence.unit
      else invalid("destination crosses the selected route, source, approval or approved paths")
    _ <- D.readmittedC(route, effective, policy, core, host.publication)
  } yield ()

  private def captureIssuedC(h: Host, storage: WorkflowComponentStorage, edge: Edge, reference: ContextReference,
    issued: WorkflowInteraction[A.Request, Nothing], core: ActionCall.Core): Consequence[(ManagedCapture, A.Request)] = for {
    order <- issued.current match {
      case value: WorkflowContinuation.WorkOrder[A.Request] => Consequence.success(value)
      case _ => invalid("managed requester reference is not an issued WorkOrder")
    }
    work <- NativeWorkflowOperationOutput.workInputC(issued)
    instances <- storage.bindInstancesC()(using core.executionContext)
    current <- instances.loadC(issued.handle.instanceIdentity)(using core.executionContext)
      .flatMap(_.map(Consequence.success).getOrElse(invalid("requester Session is absent")))
    expected <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, current)
    _ <- if (expected == issued.handle && current.lifecycle == Lifecycle.Active &&
      current.revision.value.toString == reference.revision && order.request.expectedRevision.value == reference.revision &&
      order.request.continuationId.value == reference.identity && current.suspension.exists(s =>
        s.continuationIdentity.value == reference.identity && s.completion.value == order.request.completion.identity &&
        s.evidence.value == order.request.evidence.identity) && order.request.completionOperation == F.completionOperation &&
      Set("ImplementStep", "RepairStep")(work.kind) && work.instanceIdentity == current.identity.value &&
      work.stepIdentity.nonEmpty && work.selectedPlan.repository == edge.consumer.repository)
      Consequence.unit else invalid("requester reference crosses its current native suspension, revision or consumer edge")
    starts <- storage.bindMechanicalC(F.startOperation, F.codec)
    started <- starts.loadRetainedC(F.reference(current.identity.value))(using core.executionContext)
      .flatMap(_.map(Consequence.success).getOrElse(invalid("requester has no actual native Start")))
    _ <- consumerSourceC(edge, started.receipt.payload.value.root, core)
    _ <- if (edge.machineLocalJar.isEmpty) Consequence.unit else for {
      observed <- RepositorySyncManagedGitObservation.observeInWorkspaceC(core, edge.consumer.workspace)
      _ <- if (observed.branch == started.receipt.payload.value.branch && work.decisionContext.flatMap(_.deliveryRoute).exists(_.edge == edge))
        Consequence.unit else invalid("machine-common route crosses the exact consumer branch or original A WorkOrder")
    } yield ()
    participant = started.receipt.payload.value.participantIdentity
    _ <- GoalPhaseParticipantAdmission.admitC(participant, core.executionContext)
  } yield ManagedCapture(core.component.get.core.instanceId.canonicalKey, participant, current.identity.value,
    ContextReference(work.selectedPlan.identity, work.selectedPlan.revision), work.stepIdentity.get, work.generation,
    reference, edge) -> work

  /** Recollect A's unchanged WorkOrder. This never supplies its semantic result
    * or bypasses managed atomic completion with a loose resume call. */
  def requesterC(h: Host, capture: ManagedCapture, core: ActionCall.Core): Consequence[WorkflowInteraction[A.Request, Nothing]] = for {
    original <- _requester_work_c(h, capture, core)
    _ <- _admit_requester_c(h, capture, original._1, original._2, core)
  } yield original._2

  def admitScopeCorrectionC(h: Host, capture: Option[ManagedCapture], additional: Vector[String],
    core: ActionCall.Core): Consequence[Unit] =
    if (additional.isEmpty) Consequence.unit
    else capture.fold(Consequence.unit) { selected => for {
      original <- _requester_work_c(h, selected, core)
      work <- NativeWorkflowOperationOutput.workInputC(original._2)
      _ <- work.decisionContext.filter(_.deliveryRoute.nonEmpty).fold(Consequence.unit) { context => for {
        _ <- _admit_requester_c(h, selected, original._1, original._2, core)
        effective <- context.inventoryContinuation.map(_.proposal).orElse(context.requestingResult.dependencyProposal)
          .map(Consequence.success).getOrElse(invalid("selected delivery has no approved GoalPhase dependency inventory"))
        _ <- if (context.deliveryRoute.exists(route => route.edge == selected.edge && D.routeValid(route, context.choice, effective)) &&
            additional.forall(effective.paths.contains)) Consequence.unit
          else invalid("scope correction crosses the exact approved GoalPhase dependency inventory")
      } yield () }
    } yield () }

  def supplementalScopeCorrectionC(h: Host, payload: BoundedWorkRuntime.Payload, additional: Vector[String],
    core: ActionCall.Core): Consequence[GoalPhaseDependencyInventoryContinuation.ManagedInventory] = for {
    capture <- payload.managedDependency.map(Consequence.success)
      .getOrElse(invalid("Supplemental inventory requires B's retained managed requester"))
    input = payload.state.input
    _ <- if (payload.state.participant == capture.participant && input.sourceReferences.contains(capture.work) &&
      payload.root == h.root && payload.branch == h.branch && input.repositoryIdentity == h.repository)
      Consequence.unit else invalid("Supplemental inventory crosses the retained participant or provider source")
    policy <- h.managedDevelopment.map(Consequence.success)
      .getOrElse(invalid("Supplemental inventory requires the registered managed provider"))
    edge <- edgeC(policy)
    _ <- if (edge == capture.edge && edge.localJar) Consequence.unit
      else invalid("Supplemental inventory requires the same project-private local JAR edge; machine-common verification cannot use this correction")
    original <- _requester_work_c(h, capture, core)
    _ <- _admit_requester_c(h, capture, original._1, original._2, core)
    work <- NativeWorkflowOperationOutput.workInputC(original._2)
    route <- work.decisionContext.flatMap(_.deliveryRoute).filter(value => value.edge == edge &&
      value.mode == D.subtree && payload.dependencyDelivery.contains(value.binding)).map(Consequence.success)
      .getOrElse(invalid("Supplemental inventory requires the same retained local worktree delivery"))
    _ <- _admit_publication_c(h, policy, edge, input)
    // The checkpoint already owns B's admitted inventory, including paths from
    // previous corrections. Only fresh B Starts are bounded by A's initial paths.
    _ <- _admit_selected_route_c(h, policy, input, work, route, Some(payload.root -> payload.branch), core,
      checkinventory = false)
    inventory <- GoalPhaseDependencyInventoryContinuation.supplementalManagedInventoryC(work, capture.work, additional)
  } yield inventory

  private def _requester_work_c(host: Host, capture: ManagedCapture, core: ActionCall.Core):
      Consequence[(WorkflowComponentStorage, WorkflowInteraction[A.Request, Nothing])] = for {
    _ <- if (capture.componentInstance == core.component.get.core.instanceId.canonicalKey) Consequence.unit
      else invalid("managed dependency crosses Component instances")
    storage <- goalStorageC(host, core)
    issued <- storage.bindIssuedC(F.completionOperation, A.inputCodec)
    work <- issued.loadC(ContinuationIdentity(capture.work.identity))(using core.executionContext)
      .flatMap(_.map(Consequence.success).getOrElse(invalid("original requester WorkOrder is unavailable")))
  } yield storage -> work

  private def _admit_requester_c(host: Host, capture: ManagedCapture, storage: WorkflowComponentStorage,
    work: WorkflowInteraction[A.Request, Nothing], core: ActionCall.Core): Consequence[Unit] = for {
    actual <- captureIssuedC(host, storage, capture.edge, capture.work, work, core)
    _ <- if (actual._1 == capture) Consequence.unit else invalid("managed dependency callback crosses A's retained frontier")
  } yield ()

  private def _machine_route_c(host: Host, policy: ManagedDevelopmentRuntime.Policy, capture: ManagedCapture,
    input: S.Input, providersource: Option[(String, String)], core: ActionCall.Core):
    Consequence[(WorkflowInteraction[A.Request, Nothing], ManagedDevelopmentRuntime.MachinePrograms)] = for {
    edge <- edgeC(policy)
    _ <- if (edge == capture.edge && machineJarValid(edge)) Consequence.unit
      else invalid("machine-common delivery changed its retained A/B edge or consumption mode")
    original <- requesterC(host, capture, core)
    work <- NativeWorkflowOperationOutput.workInputC(original)
    route <- work.decisionContext.flatMap(_.deliveryRoute).map(Consequence.success)
      .getOrElse(invalid("machine-common delivery has no actual selected A route"))
    programs <- route.machinePrograms.filter(_ => route.edge == edge).map(Consequence.success)
      .getOrElse(invalid("selected A route has a different machine-common publication or consumer recipe"))
    _ <- _admit_publication_c(host, policy, edge, input.copy(delegation = input.delegation.map(_.copy(role = "destination"))))
    _ <- _admit_selected_route_c(host, policy, input, work, route, providersource, core)
  } yield original -> programs

  def machinePublicationC(h: Host, payload: BoundedWorkRuntime.Payload, commit: S.Commit, session: String,
    core: ActionCall.Core): Consequence[LocalPublication.Result] = for {
    capture <- payload.managedDependency.map(Consequence.success).getOrElse(invalid("machine-common delivery has no original A capture"))
    policy <- h.managedDevelopment.map(Consequence.success).getOrElse(invalid("machine-common dependency scope is unavailable"))
    selected <- _machine_route_c(h, policy, capture, payload.state.input, Some(payload.root -> payload.branch), core)
    publication <- h.publication.map(Consequence.success).getOrElse(invalid("machine-common local publication policy is unavailable"))
    target <- ManagedDevelopmentRuntime.machineTargetC(capture.edge, Some(publication))
    input = DelegateTask.publicationInput(session, payload.state).copy(explicit = true)
    source = LocalPublication.Source(commit.identity, commit.tree, Vector.empty)
    machine = LocalPublicationRuntime.MachinePublication(source, capture.edge, selected._2)
    result <- LocalPublicationRuntime.ensureC(core, publication, input, Some(machine))
    _ <- if (result.request.input == input && result.request.target == target && result.request.source == source &&
      (result.status != "published" || DelegateTask.machinePublicationValid(payload.state, commit, capture, result, Some(selected._2.publication))))
      Consequence.unit else invalid("ordinary publication differs from B's exact target, recipe or clean accepted source")
  } yield result

  /** Same-store result collection authenticates the native B checkpoint. A
    * pending/failed/decision-needed B remains a wait, without invoking B again. */
  def collectC(h: Host, storage: WorkflowComponentStorage, definition: DefinitionBinding,
    state: S.State, core: ActionCall.Core): Consequence[S.State] =
    if (state.input.delegation.exists(_.providerPhase.nonEmpty)) ProviderPhaseDelivery.collectC(h, state, core)
    else if (state.stage != "AwaitResult" || state.delivery.exists(_.observations.exists(_.event == "defer")))
      Consequence.success(state) else for {
      session <- state.delivery.flatMap(_.destinationSessionId).map(Consequence.success)
        .getOrElse(invalid("managed requester has no observed destination Session"))
      instances <- storage.bindInstancesC()(using core.executionContext)
      current <- instances.loadC(InstanceIdentity(session))(using core.executionContext)
      result <- current match {
        case None => Consequence.success(state)
        case Some(current) if current.suspension.nonEmpty => Consequence.success(state)
        case Some(current) => for {
          // Instance decoding resolves to this storage's admitted binding.
          // Reloading the catalog would create a distinct capability object.
          _ <- if (current.definition == definition) Consequence.unit
            else invalid("destination Session belongs to another native workflow")
          payload <- DelegateTaskNativeRun.loadPayloadC(storage, session, current.revision.value, core)
          _ <- GoalPhaseParticipantAdmission.admitC(payload.state.participant, core.executionContext)
          next <- if (!Set("ReturnReady", "Returned")(payload.state.stage)) Consequence.success(state)
            else for {
              _ <- DelegateTask.returnedC(state.input, payload.state.asJson, _ => Consequence.success(payload.state))
              _ <- if (payload.managedDependency.nonEmpty) Consequence.unit
                else invalid("managed B has no retained A WorkOrder capture")
              next <- DelegateTask.observeC(state, DelegateTask.Observation("result-received",
                ContextReference(session + ":standalone-state:" + current.revision.value, "1"), Some(session),
                Some(payload.state.asJson), None, "Collected the exact same-store native destination checkpoint"),
                _ => Consequence.success(payload.state))
            } yield next
        } yield next
      }
    } yield result
  /** Authenticate the already received immutable native result before a
    * requester operation can activate its artifact. Public JSON is no witness. */
  private[admission] def receivedC(storage: WorkflowComponentStorage, definition: DefinitionBinding,
    state: S.State, core: ActionCall.Core):
    Consequence[BoundedWorkRuntime.Payload] = for {
    delivery <- state.delivery.map(Consequence.success).getOrElse(invalid("No requester delivery is retained"))
    session <- delivery.destinationSessionId.map(Consequence.success).getOrElse(invalid("No destination Session is retained"))
    receipt <- delivery.observations.find(_.event == "result-received").map(Consequence.success)
      .getOrElse(invalid("No actual result receipt is retained"))
    prefix = session + ":standalone-state:"
    revision <- Option.when(receipt.sourceReference.identity.startsWith(prefix) && receipt.sourceReference.revision == "1")(
      receipt.sourceReference.identity.stripPrefix(prefix)).flatMap(_.toLongOption).filter(_ > 0)
      .map(Consequence.success).getOrElse(invalid("Result receipt is not an exact native destination checkpoint"))
    instances <- storage.bindInstancesC()(using core.executionContext)
    current <- instances.loadC(InstanceIdentity(session))(using core.executionContext)
      .flatMap(_.map(Consequence.success).getOrElse(invalid("Destination Session is unavailable")))
    _ <- if (current.definition == definition && current.revision.value >= revision) Consequence.unit
      else invalid("Result receipt crosses its native destination history")
    payload <- DelegateTaskNativeRun.loadPayloadC(storage, session, revision, core)
    _ <- GoalPhaseParticipantAdmission.admitC(payload.state.participant, core.executionContext)
    _ <- if (delivery.returned.nonEmpty && receipt.result.nonEmpty &&
      receipt.destinationSessionId.contains(session)) Consequence.unit
      else invalid("Result receipt has no producer result or crosses its destination Session")
    _ <- DelegateTask.returnedC(state.input, payload.state.asJson, _ => Consequence.success(payload.state))
  } yield payload

  /** Adoption attaches results only. Advance reads scoped selection/verification
    * or owns the explicit machine-common verification, then returns the original A Request. */
  def continuationC(h: Host, storage: WorkflowComponentStorage, definition: DefinitionBinding, state: S.State,
    core: ActionCall.Core, requesterCapture: Option[ManagedCapture] = None):
    Consequence[(S.State, Option[WorkflowInteraction[A.Request, Nothing]])] = for {
    policy <- h.managedDevelopment.filter(_.dependencyWorkEdge.nonEmpty).map(Consequence.success)
      .getOrElse(invalid("No managed dependency scope is configured"))
    phase <- if (state.delivery.exists(_.phaseDelivery.nonEmpty)) ProviderPhaseDelivery.receivedC(h, state, core).map(Some(_))
      else Consequence.success(Option.empty[ProviderPhaseDelivery.Delivery])
    producer <- if (phase.isEmpty) receivedC(storage, definition, state, core).map(Some(_))
      else Consequence.success(Option.empty[BoundedWorkRuntime.Payload])
    capture <- phase.map(_.capture).orElse(producer.flatMap(_.managedDependency)).map(Consequence.success)
      .getOrElse(invalid("B has no actual retained A capture"))
    selected <- state.delivery.flatMap(_.artifactSelection).map(_.as[DelegateTask.ArtifactSelection]
      .fold(e => invalid(e.message), Consequence.success)).getOrElse(invalid("No exact artifact selection was supplied"))
    result <- if (capture.edge.machineLocalJar.nonEmpty || requesterCapture.exists(_.edge.machineLocalJar.nonEmpty)) for {
      _ <- if (requesterCapture.contains(capture) && phase.isEmpty && machineJarValid(capture.edge)) Consequence.unit
        else invalid("machine-common verification requires matching original A captures on the requester and bounded producer")
      native <- producer.map(Consequence.success).getOrElse(invalid("machine-common delivery has no actual bounded producer"))
      checked <- _machine_continuation_c(h, policy, state, native, capture, selected, core)
    } yield checked else _scoped_continuation_c(h, policy, state, phase, producer, capture, selected, core)
  } yield result

  private def _machine_continuation_c(host: Host, policy: ManagedDevelopmentRuntime.Policy, state: S.State,
    producer: BoundedWorkRuntime.Payload, capture: ManagedCapture, selected: DelegateTask.ArtifactSelection,
    core: ActionCall.Core): Consequence[(S.State, Option[WorkflowInteraction[A.Request, Nothing]])] = for {
    native <- producer.state.delivery.filter(_.managedContinuation.contains(capture)).map(Consequence.success)
      .getOrElse(invalid("B has no exact retained ordinary publication attachment"))
    session <- native.destinationSessionId.filter(id => state.delivery.flatMap(_.destinationSessionId).contains(id))
      .map(Consequence.success).getOrElse(invalid("ordinary publication crosses its actual bounded B Session"))
    published <- native.publication.map(Consequence.success).getOrElse(invalid("B has no actual ordinary publication receipt"))
    commit <- producer.state.commit.map(Consequence.success).getOrElse(invalid("B has no accepted native commit"))
    admitted <- _machine_route_c(host, policy, capture, state.input, Some(producer.root -> producer.branch), core)
    publication <- host.publication.map(Consequence.success).getOrElse(invalid("machine-common local publication policy is unavailable"))
    input = DelegateTask.publicationInput(session, producer.state).copy(explicit = true)
    actual <- LocalPublicationRuntime.collectC(core, publication, input)
    _ <- if (actual.contains(published) && DelegateTask.machinePublicationValid(producer.state, commit, capture,
      published, Some(admitted._2.publication)) && DelegateTask.machineSelectionValid(selected, published) &&
      selected.coordinates == Vector(coordinateString(capture.edge.requested))) Consequence.unit
      else invalid("Selection crosses B's exact ordinary publication, source, coordinates or consumer effect frontier")
    compiled <- ManagedDevelopmentRuntime.machineConsumerC(core, policy, capture, admitted._2, published, state.input.requestIdentity)
    _ <- requesterC(host, capture, core)
    verified = compiled.status == "verified"
    next = state.copy(delivery = state.delivery.map(_.copy(publication = Some(published), managedConsumer = Some(compiled),
      managedContinuation = Some(capture))), message = if (verified)
      "Ordinary consumer compilation observed the machine-common JAR; dispatch the original A Request through its existing typed caller"
      else "Ordinary consumer verification is " + compiled.status + "; A's original WorkOrder remains suspended")
  } yield next -> Option.when(verified)(admitted._1)

  private def _scoped_continuation_c(host: Host, policy: ManagedDevelopmentRuntime.Policy, state: S.State,
    phase: Option[ProviderPhaseDelivery.Delivery], producer: Option[BoundedWorkRuntime.Payload], capture: ManagedCapture,
    selected: DelegateTask.ArtifactSelection, core: ActionCall.Core):
    Consequence[(S.State, Option[WorkflowInteraction[A.Request, Nothing]])] = for {
    publication <- phase.map(_.publication).orElse(producer.flatMap(_.state.delivery.flatMap(_.managedPublication))).map(Consequence.success)
      .getOrElse(invalid("B has no completed scoped publication"))
    expected <- phase.map(p => ProviderPhaseDelivery.operation(policy, p.phase.locator, state.input.requestIdentity))
      .getOrElse(publicationInputC(policy, producer.get.state.input))
    input = expected.copy(requestIdentity = state.input.requestIdentity, selection = state.input.requestIdentity)
    edge <- ManagedDevelopment.edgeC(policy.definition, input)
    _ <- if (capture.edge == edge && publication.edge == edge && publication.scope == policy.definition.scope &&
      publication.identity == input.publication && selected.selectionReference ==
        ContextReference(key(policy.definition, edge, "selection", input.selection), "1")) Consequence.unit
      else invalid("Consumer selection crosses the native B publication or exact retained A/B edge")
    original <- requesterC(host, capture, core)
    compiled <- ManagedDevelopmentRuntime.runC(core, policy, "status", input)
    // Decode the complete selection, not a status string from caller-supplied JSON.
    selection <- if (compiled.status == "verified") compiled.detail.hcursor.get[Selection]("selection")
      .fold(e => invalid(e.message), s => if (s == Selection(input.selection, publication)) Consequence.success(Some(s))
        else invalid("Consumer verification belongs to another exact publication"))
      else Consequence.success(Option.empty[Selection])
    source <- if (selection.nonEmpty) ManagedDevelopmentRuntime.consumerSourceC(core, policy, edge).map(Some(_))
      else Consequence.success(Option.empty[Json])
    _ <- if (source.forall(actual => compiled.detail.hcursor.downField("consumerSource").focus.contains(actual)))
      Consequence.unit else invalid("Retained consumer verification is stale for A's actual current source")
    _ <- requesterC(host, capture, core)
    next = if (selection.nonEmpty) state.copy(delivery = state.delivery.map(_.copy(
      managedPublication = Some(publication), managedConsumer = Some(compiled), managedContinuation = Some(capture))),
      message = "Exact native B publication and explicit consumer verification retained; dispatch the returned original A Request through its existing typed caller")
      else state.copy(delivery = state.delivery.map(_.copy(managedPublication = Some(publication),
        managedConsumer = Some(compiled), managedContinuation = Some(capture))),
        message = "Explicit native consumer integration is " + compiled.status + "; A's original WorkOrder remains suspended")
  } yield next -> Option.when(selection.nonEmpty)(original)

}
