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

/** Composition over existing native WorkOrders, bounded checkpoints and scoped
  * artifact operations. This owns no queue, semantic executor or repair loop.
  * @since   Nov. 12, 2026
  * @version Nov. 18, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object ManagedDevelopmentBoundedWork {
  import ManagedDevelopment.*
  import GoalPhaseDependencyValidation.ManagedCapture

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
      contract.artifactCoordinates == Vector(coordinate.organization + ":" + coordinate.module + ":" + coordinate.version))
      Consequence.unit else invalid("managed destination requires its exact scoped publication target and development coordinates")
  } yield operation

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
    h.managedDevelopment.filter(_.dependencyWorkEdge.nonEmpty) match {
      case None => Consequence.success(None)
      case Some(policy) if input.delegation.exists(_.role == "destination") => for {
        _ <- publicationInputC(policy, input)
        edge <- edgeC(policy)
        storage <- goalStorageC(h, core)
        issued <- storage.bindIssuedC(F.completionOperation, A.inputCodec)
        found <- input.sourceReferences.foldLeft(Consequence.success(Vector.empty[(ContextReference, WorkflowInteraction[A.Request, Nothing])])) {
          (prior, reference) => for {
            all <- prior
            value <- issued.loadC(ContinuationIdentity(reference.identity))(using core.executionContext)
          } yield all ++ value.toVector.map(reference -> _)
        }
        _ <- if (found.size == 1) Consequence.unit else invalid("managed B requires one explicit actual pending A WorkOrder reference")
        pair = found.head
        capture <- captureIssuedC(h, storage, edge, pair._1, pair._2, core)
      } yield Some(capture)
      case _ => Consequence.success(None)
    }

  private def captureIssuedC(h: Host, storage: WorkflowComponentStorage, edge: Edge, reference: ContextReference,
    issued: WorkflowInteraction[A.Request, Nothing], core: ActionCall.Core): Consequence[ManagedCapture] = for {
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
    participant = started.receipt.payload.value.participantIdentity
    _ <- GoalPhaseParticipantAdmission.admitC(participant, core.executionContext)
  } yield ManagedCapture(core.component.get.core.instanceId.canonicalKey, participant, current.identity.value,
    ContextReference(work.selectedPlan.identity, work.selectedPlan.revision), work.stepIdentity.get, work.generation,
    reference, edge)

  /** Recollect A's unchanged WorkOrder. This never supplies its semantic result
    * or bypasses managed atomic completion with a loose resume call. */
  def requesterC(h: Host, capture: ManagedCapture, core: ActionCall.Core): Consequence[WorkflowInteraction[A.Request, Nothing]] = for {
    _ <- if (capture.componentInstance == core.component.get.core.instanceId.canonicalKey) Consequence.unit
      else invalid("managed dependency crosses Component instances")
    storage <- goalStorageC(h, core)
    issued <- storage.bindIssuedC(F.completionOperation, A.inputCodec)
    work <- issued.loadC(ContinuationIdentity(capture.work.identity))(using core.executionContext)
      .flatMap(_.map(Consequence.success).getOrElse(invalid("original requester WorkOrder is unavailable")))
    actual <- captureIssuedC(h, storage, capture.edge, capture.work, work, core)
    _ <- if (actual == capture) Consequence.unit else invalid("managed dependency callback crosses A's retained frontier")
  } yield work

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

  /** Adoption attaches results only. Consumer selection/compilation must already
    * have been explicitly executed through the existing artifact Operations.
    * Advance reads their retained result and returns the real original A Request
    * to the typed caller; it never completes A with an invented semantic result. */
  def continuationC(h: Host, storage: WorkflowComponentStorage, definition: DefinitionBinding, state: S.State,
    core: ActionCall.Core): Consequence[(S.State, Option[WorkflowInteraction[A.Request, Nothing]])] = for {
    policy <- h.managedDevelopment.filter(_.dependencyWorkEdge.nonEmpty).map(Consequence.success)
      .getOrElse(invalid("No managed dependency scope is configured"))
    phase <- if (state.delivery.exists(_.phaseDelivery.nonEmpty)) ProviderPhaseDelivery.receivedC(h, state, core).map(Some(_))
      else Consequence.success(Option.empty[ProviderPhaseDelivery.Delivery])
    producer <- if (phase.isEmpty) receivedC(storage, definition, state, core).map(Some(_))
      else Consequence.success(Option.empty[BoundedWorkRuntime.Payload])
    capture <- phase.map(_.capture).orElse(producer.flatMap(_.managedDependency)).map(Consequence.success)
      .getOrElse(invalid("B has no actual retained A capture"))
    publication <- phase.map(_.publication).orElse(producer.flatMap(_.state.delivery.flatMap(_.managedPublication))).map(Consequence.success)
      .getOrElse(invalid("B has no completed scoped publication"))
    expected <- phase.map(p => ProviderPhaseDelivery.operation(policy, p.phase.locator, state.input.requestIdentity))
      .getOrElse(publicationInputC(policy, producer.get.state.input))
    selected <- state.delivery.flatMap(_.artifactSelection).map(_.as[DelegateTask.ArtifactSelection]
      .fold(e => invalid(e.message), Consequence.success)).getOrElse(invalid("No exact artifact selection was supplied"))
    input = expected.copy(requestIdentity = state.input.requestIdentity, selection = state.input.requestIdentity)
    edge <- ManagedDevelopment.edgeC(policy.definition, input)
    _ <- if (capture.edge == edge && publication.edge == edge && publication.scope == policy.definition.scope &&
      publication.identity == input.publication && selected.selectionReference ==
        ContextReference(key(policy.definition, edge, "selection", input.selection), "1")) Consequence.unit
      else invalid("Consumer selection crosses the native B publication or exact retained A/B edge")
    original <- requesterC(h, capture, core)
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
    _ <- requesterC(h, capture, core)
    next = if (selection.nonEmpty) state.copy(delivery = state.delivery.map(_.copy(
      managedPublication = Some(publication), managedConsumer = Some(compiled), managedContinuation = Some(capture))),
      message = "Exact native B publication and explicit consumer verification retained; dispatch the returned original A Request through its existing typed caller")
      else state.copy(delivery = state.delivery.map(_.copy(managedPublication = Some(publication),
        managedConsumer = Some(compiled), managedContinuation = Some(capture))),
        message = "Explicit native consumer integration is " + compiled.status + "; A's original WorkOrder remains suspended")
  } yield next -> Option.when(selection.nonEmpty)(original)

}
