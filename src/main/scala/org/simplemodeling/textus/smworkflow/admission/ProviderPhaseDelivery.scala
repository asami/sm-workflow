package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import GoalPhaseNativeRun as F

/** Adapter of an existing provider Phase's canonical accepted completion.
  * No provider Start, bounded S.State, semantic execution or accepted Submit is
  * synthesized. Publication is explicitly invoked by the provider owner.
  * @since   Nov. 12, 2026
  * @version Nov. 18, 2026
  */
private[smworkflow] object ProviderPhaseDelivery {
  final case class Locator(sessionId: String, startRevision: String, publication: String, producerSelection: String)
  object Locator {
    given Encoder[Locator] = deriveEncoder
    given Decoder[Locator] = deriveDecoder
  }
  final case class Storage(configuration: Configuration, instances: DataStore.CollectionId, continuations: DataStore.CollectionId)
  final case class Accepted(locator: Locator, sourceRevision: String, root: String, branch: String, participant: String,
    input: GoalPhaseNativeRepositoryClosure.Input, synchronization: GoalPhasePostCompletionSync.Synchronization,
    declaredSynchronization: Option[String])
  object Accepted {
    given Encoder[Accepted] = deriveEncoder
    given Decoder[Accepted] = deriveDecoder
  }
  final case class Delivery(phase: Accepted, publication: ManagedDevelopment.Publication,
    capture: GoalPhaseDependencyValidation.ManagedCapture, synchronization: Option[GoalPhasePostCompletionSync.Synchronization] = None)
  object Delivery {
    given Encoder[Delivery] = deriveEncoder
    given Decoder[Delivery] = deriveDecoder
  }
  def valid(locator: Locator): Boolean = locator != null && exact(locator.sessionId) &&
    locator.startRevision != null && locator.startRevision.toLongOption.exists(_ > 0) &&
    ManagedDevelopment.id(locator.publication) && ManagedDevelopment.id(locator.producerSelection)

  def storage(h: BoundedWorkRuntime.Host): Storage = Storage(h.configuration, h.instances, h.continuations)
  private def bindC(s: Storage, core: ActionCall.Core): Consequence[WorkflowComponentStorage] = for {
    definition <- GoalPhaseRuntimeCatalog.loadC()
    bound <- WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
      SmWorkflowComponent.componentId, s.configuration, Vector(definition.definition), s.instances, s.continuations)
  } yield bound

  private def sourceC(s: Storage, locator: Locator, core: ActionCall.Core): Consequence[F.Session] = for {
    _ <- if (valid(locator)) Consequence.unit else invalid("invalid actual provider locator")
    store <- bindC(s, core)
    starts <- store.bindMechanicalC(F.startOperation, F.codec)
    start <- starts.loadRetainedC(F.reference(locator.sessionId))(using core.executionContext)
      .flatMap(_.map(Consequence.success).getOrElse(invalid("provider has no canonical GoalPhase Start")))
    _ <- if (start.receipt.next.revision.value.toString == locator.startRevision) Consequence.unit
      else invalid("provider locator crosses its actual original Start revision")
    _ <- GoalPhaseParticipantAdmission.admitC(start.receipt.payload.value.participantIdentity, core.executionContext)
    instances <- store.bindInstancesC()(using core.executionContext)
    current <- instances.loadC(InstanceIdentity(locator.sessionId))(using core.executionContext)
      .flatMap(_.map(Consequence.success).getOrElse(invalid("provider Phase is unavailable")))
    _ <- WorkflowForceClosure.requireOpenC(current)
  } yield F.Session(store, start, start.receipt.payload.value.participantIdentity, current)

  /** Admission attaches a real provider Start, never a substitute destination.
    * An old Start without a captured assigned-chat contract is not silently
    * migrated into a new mode by today's requester configuration. */
  def admitC(h: BoundedWorkRuntime.Host, input: StandaloneFixExecution.Input, core: ActionCall.Core): Consequence[Unit] =
    input.delegation.flatMap(_.providerPhase).fold(Consequence.unit) { locator => for {
      policy <- h.managedDevelopment.map(Consequence.success).getOrElse(invalid("no managed provider scope is configured"))
      edge <- ManagedDevelopmentBoundedWork.edgeC(policy)
      root <- ManagedDevelopmentRuntime.workspaceRootC(core, edge.provider.workspace)
      source <- sourceC(storage(h), locator, core)
      start = source.started.receipt.payload.value
      _ <- if (start.plan.repository == edge.provider.repository && start.root == root) Consequence.unit
        else invalid("provider Start crosses the declared dependency source")
      _ <- if (DependencyDevelopmentDelivery.binding(policy).filter(_.mode == DependencyDevelopmentDelivery.assignedChat)
        .forall(binding => start.dependencyDelivery.contains(binding))) Consequence.unit
        else invalid("existing provider Start has no matching captured assigned-chat binding; explicit owner direction is required")
      _ <- AssignedDevelopmentChatQueue.bindPhaseC(storage(h), source, locator, core)
    } yield () }

  def afterPublishC(s: Storage, accepted: Accepted, publication: ManagedDevelopment.Publication,
    core: ActionCall.Core): Consequence[Unit] = for {
    source <- sourceC(s, accepted.locator, core)
    _ <- AssignedDevelopmentChatQueue.publishedPhaseC(s, source.started.receipt.payload.value, accepted, publication, core)
  } yield ()

  def readC(s: Storage, locator: Locator, core: ActionCall.Core, expected: Option[(String, String)] = None): Consequence[Option[Accepted]] = for {
    _ <- if (valid(locator)) Consequence.unit else invalid("exact original provider Phase and publication locators are required")
    store <- bindC(s, core)
    starts <- store.bindMechanicalC(F.startOperation, F.codec)
    start <- starts.loadRetainedC(F.reference(locator.sessionId))(using core.executionContext)
      .flatMap(_.map(Consequence.success).getOrElse(invalid("provider has no canonical GoalPhase Start")))
    _ <- if (start.receipt.next.revision.value.toString == locator.startRevision && expected.forall { case (repository, root) =>
      start.receipt.payload.value.plan.repository == repository && start.receipt.payload.value.root == root
    }) Consequence.unit
      else invalid("provider locator crosses its actual original Start revision")
    instances <- store.bindInstancesC()(using core.executionContext)
    current <- instances.loadC(InstanceIdentity(locator.sessionId))(using core.executionContext)
      .flatMap(_.map(Consequence.success).getOrElse(invalid("provider Phase is unavailable")))
    _ <- WorkflowForceClosure.requireOpenC(current)
    result <- if (current.lifecycle != Lifecycle.Completed || current.suspension.nonEmpty) Consequence.success(Option.empty[Accepted])
      else for {
        _ <- GoalPhaseParticipantAdmission.admitC(start.receipt.payload.value.participantIdentity, core.executionContext)
        local <- GoalPhasePostCompletionSync.acceptedDeliveryC(store, start, current, core)
        strict <- if (local.isEmpty) GoalPhaseNativeRepositoryClosure.acceptedDeliveryC(store, current, core)
          else Consequence.success(Option.empty[GoalPhaseNativeRepositoryClosure.Payload])
        input <- local.map(_.input).orElse(strict.map(_.input)).map(Consequence.success)
          .getOrElse(invalid("terminal provider has no actual accepted Phase completion"))
        _ <- if (input.instance == locator.sessionId && input.plan.identity == start.receipt.payload.value.plan.identity &&
          input.plan.phase == start.receipt.payload.value.plan.phase && input.plan.repository == start.receipt.payload.value.plan.repository &&
          input.completed.map(_.step) == input.plan.steps.map(_.identity) && input.completed.forall(GoalPhaseOrderedProgression.validStepAcceptance) &&
          input.review.nonEmpty && input.candidate != null && Vector(input.candidate.head, input.candidate.tree).forall(_.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})"))) Consequence.unit
          else invalid("provider completion crosses its selected Plan, accepted prefix or actual committed candidate")
        sync = local.map(_.synchronization).getOrElse(GoalPhasePostCompletionSync.Synchronization("succeeded",
          "Actual strict provider Phase synchronization is already retained", strict.map(_.childSessionId), None))
      } yield Some(Accepted(locator, current.revision.value.toString, start.receipt.payload.value.root,
        start.receipt.payload.value.branch, start.receipt.payload.value.participantIdentity, input, sync,
        start.receipt.payload.value.plan.deliverySynchronization))
  } yield result

  def operation(policy: ManagedDevelopmentRuntime.Policy, locator: Locator, requester: String): Consequence[ManagedDevelopment.Input] = for {
    _ <- if (valid(locator)) Consequence.unit else invalid("invalid provider Phase locator")
    edge <- ManagedDevelopmentBoundedWork.edgeC(policy)
  } yield ManagedDevelopment.Input(edge.identity, requester, locator.publication, locator.producerSelection)

  def publicationC(s: Storage, policy: ManagedDevelopmentRuntime.Policy, locator: Locator, core: ActionCall.Core):
    Consequence[Option[(Accepted, ManagedDevelopment.Publication)]] = for {
    edge <- ManagedDevelopmentBoundedWork.edgeC(policy)
    root <- ManagedDevelopmentRuntime.workspaceRootC(core, edge.provider.workspace)
    accepted <- readC(s, locator, core, Some(edge.provider.repository -> root))
    result <- accepted match {
      case None => Consequence.success(None)
      case Some(phase) => for {
        input <- operation(policy, locator, locator.publication)
        publication <- ManagedDevelopmentRuntime.publicationC(core, policy, input)
        _ <- publication.fold(Consequence.unit)(p => if (p.identity == locator.publication &&
          p.producerSelection == locator.producerSelection && p.edge.provider.repository == phase.input.plan.repository &&
          ManagedDevelopmentBoundedWork.acceptedPublicationSource(p.source, phase.input.candidate.head, phase.input.candidate.tree)) Consequence.unit
          else invalid("scoped provider publication differs from the actual accepted Phase commit/tree"))
        _ <- publication.fold(Consequence.unit)(p => afterPublishC(s, phase, p, core))
      } yield publication.map(phase -> _)
    }
  } yield result

  def collectC(h: BoundedWorkRuntime.Host, state: StandaloneFixExecution.State, core: ActionCall.Core):
    Consequence[StandaloneFixExecution.State] =
    if (state.stage != "AwaitResult" || state.delivery.exists(_.observations.exists(_.event == "defer"))) Consequence.success(state)
    else for {
      policy <- h.managedDevelopment.map(Consequence.success).getOrElse(invalid("no managed dependency scope is configured"))
      locator <- state.input.delegation.flatMap(_.providerPhase).map(Consequence.success).getOrElse(invalid("no declared Phase locator"))
      _ <- if (state.delivery.flatMap(_.destinationSessionId).contains(locator.sessionId)) Consequence.unit
        else invalid("observed destination differs from the original provider Phase")
      actual <- publicationC(storage(h), policy, locator, core)
      next <- actual match {
        case None => Consequence.success(state.copy(message = "Provider Phase or its explicitly invoked scoped publication remains unfinished; original work and counts are retained"))
        case Some((phase, publication)) => for {
          capture <- state.delivery.flatMap(_.managedContinuation).map(Consequence.success)
            .getOrElse(invalid("requester has no original actual A capture"))
          _ <- if (capture.session != locator.sessionId) Consequence.unit else invalid("provider Phase cannot wait on itself as its consumer")
          _ <- ManagedDevelopmentBoundedWork.requesterC(h, capture, core)
          _ <- if (publication.edge == capture.edge && state.input.delegation.exists(c =>
            c.componentIdentity == publication.edge.provider.identity && c.artifactCoordinates == Vector(
              publication.selected.organization + ":" + publication.selected.module + ":" + publication.selected.version)))
            Consequence.unit else invalid("Phase delivery crosses the exact declared Component or coordinates")
          synchronization <- DevelopmentDeliverySynchronization.readC(core, policy, publication)
          delivered = Delivery(phase, publication, capture, synchronization)
          observation = DelegateTask.Observation("result-received", ContextReference(locator.sessionId +
            ":provider-phase-completion:" + phase.sourceRevision, "1"), Some(locator.sessionId), Some(delivered.asJson), None,
            "Collected an actual canonical provider Phase completion and exact immutable scoped publication")
        } yield state.copy(stage = "ResultReceived", delivery = state.delivery.map(d => d.copy(returned = Some(delivered.asJson),
          phaseDelivery = Some(delivered), managedPublication = Some(publication), synchronization = synchronization.orElse(Some(phase.synchronization)),
          observations = d.observations :+ observation)), message = "Accepted provider Phase and scope-private artifact attached; consumer adoption remains separate")
      }
    } yield next

  def receivedC(h: BoundedWorkRuntime.Host, state: StandaloneFixExecution.State, core: ActionCall.Core): Consequence[Delivery] = for {
    retained <- state.delivery.flatMap(_.phaseDelivery).map(Consequence.success).getOrElse(invalid("no typed Phase delivery is retained"))
    policy <- h.managedDevelopment.map(Consequence.success).getOrElse(invalid("no managed scope"))
    actual <- publicationC(storage(h), policy, retained.phase.locator, core).flatMap(_.map(Consequence.success)
      .getOrElse(invalid("actual provider Phase/publication is unavailable")))
    _ <- if (actual == (retained.phase -> retained.publication) && state.delivery.flatMap(_.returned).contains(retained.asJson) &&
      state.input.delegation.flatMap(_.providerPhase).contains(retained.phase.locator)) Consequence.unit
      else invalid("Phase attachment differs from its canonical accepted source/publication")
    _ <- ManagedDevelopmentBoundedWork.requesterC(h, retained.capture, core)
  } yield retained

  /** Provider operation preflight; requester collection cannot call this path. */
  def beforePublishC(s: Storage, policy: ManagedDevelopmentRuntime.Policy, input: ManagedDevelopment.Input,
    core: ActionCall.Core): Consequence[Option[Accepted]] = input.providerPhase match {
    case None => Consequence.success(None)
    case Some(locator) => for {
      edge <- ManagedDevelopmentBoundedWork.edgeC(policy)
      expectedRoot <- ManagedDevelopmentRuntime.workspaceRootC(core, edge.provider.workspace)
      accepted <- readC(s, locator, core, Some(edge.provider.repository -> expectedRoot)).flatMap(_.map(Consequence.success)
        .getOrElse(invalid("provider Phase is not accepted; source/semantic work is not replayed")))
      expected <- operation(policy, locator, input.requestIdentity)
      _ <- if (input.copy(providerPhase = None, synchronization = None) == expected) Consequence.unit
        else invalid("provider publication input crosses its frozen Phase/publication locators")
      _ <- if (accepted.input.plan.repository == edge.provider.repository) Consequence.unit
        else invalid("provider Phase belongs to another dependency")
      published <- ManagedDevelopmentRuntime.publicationC(core, policy, input)
      source <- sourceC(s, locator, core)
      _ <- AssignedDevelopmentChatQueue.bindPhaseC(s, source, locator, core)
      permitted <- if (published.nonEmpty) Consequence.success(true)
        else AssignedDevelopmentChatQueue.permitPhaseC(s, source, core, requireActive = true)
      _ <- if (permitted) Consequence.unit else invalid("provider Phase does not own its assigned-chat execution place")
      _ <- published match {
        case Some(p) => if (ManagedDevelopmentBoundedWork.acceptedPublicationSource(p.source, accepted.input.candidate.head, accepted.input.candidate.tree)) Consequence.unit
          else invalid("retained Phase publication differs from the accepted source")
        case None => for {
          actual <- RepositorySyncManagedGitObservation.observeInWorkspaceC(core, edge.provider.workspace)
          root <- ManagedDevelopmentRuntime.workspaceRootC(core, edge.provider.workspace)
          _ <- if (root == accepted.root && actual.branch == accepted.branch && actual.head == accepted.input.candidate.head &&
            actual.tree == accepted.input.candidate.tree && actual.dirty.isEmpty) Consequence.unit
            else invalid("provider publication source differs from the accepted native Phase source")
        } yield ()
      }
    } yield Some(accepted)
  }

  private def exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  private def invalid[A](reason: String): Consequence[A] = F.invalid("Provider Phase delivery: " + reason)
}
