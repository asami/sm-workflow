package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import StandaloneFixExecution as S
import StandaloneFixExecution.given

/** A per-chat aggregate in the existing canonical Workflow instance/continuation
  * store. Native expected-revision transitions order enqueue/claim/control.
  * No provider work, transport, polling, timers or machine-wide exclusion.
  * A conflicting call fails instead of receiving a speculative execution permit.
  * @since   Nov. 12, 2026
  * @version Nov. 18, 2026
  */
private[smworkflow] object AssignedDevelopmentChatQueue {
  final case class Entry(session: String, request: String, input: Json, status: String,
    phaseStartRevision: Option[String] = None, phaseLocator: Option[ProviderPhaseDelivery.Locator] = None)
  object Entry {
    given Encoder[Entry] = deriveEncoder
    given Decoder[Entry] = deriveDecoder
  }
  final case class Event(session: String, action: String, source: String, revision: Long)
  object Event {
    given Encoder[Event] = deriveEncoder
    given Decoder[Event] = deriveDecoder
  }
  final case class Control(identity: String, session: String, observation: DelegateTask.Observation,
    status: String, order: Vector[String], revision: Long)
  object Control {
    given Encoder[Control] = deriveEncoder
    given Decoder[Control] = deriveDecoder
  }
  final case class State(chat: String, entries: Vector[Entry], order: Vector[String], events: Vector[Event],
    controls: Option[Vector[Control]] = None)
  object State {
    given Encoder[State] = deriveEncoder
    given Decoder[State] = deriveDecoder
  }
  private val statuses = Set("queued", "active", "held", "cancelled", "delivered")
  def valid(state: State): Boolean = state != null && exact(state.chat) && state.entries != null &&
    state.entries.forall(e => e != null && exact(e.session) && exact(e.request) && e.input != null &&
      e.input.isObject && statuses(e.status) && e.phaseStartRevision != null &&
      e.phaseStartRevision.forall(_.toLongOption.exists(_ > 0)) && e.phaseLocator != null &&
      e.phaseLocator.forall(p => ProviderPhaseDelivery.valid(p) && p.sessionId == e.session && e.phaseStartRevision.contains(p.startRevision))) && state.entries.map(_.session).distinct.size == state.entries.size &&
    state.entries.map(_.request).distinct.size == state.entries.size && state.entries.count(_.status == "active") <= 1 &&
    state.order != null && state.order.distinct == state.order &&
    state.order.toSet == state.entries.filter(e => Set("queued", "active")(e.status)).map(_.session).toSet &&
    state.entries.find(_.status == "active").forall(e => state.order.headOption.contains(e.session)) &&
    state.events != null && state.events.forall(e => e != null && exact(e.session) && exact(e.action) &&
      exact(e.source) && e.revision > 0) && state.controls != null && state.controls.forall(rows =>
      rows != null && rows.forall(_ != null) && rows.map(_.identity).distinct.size == rows.size && rows.forall(c => exact(c.identity) &&
        state.entries.exists(_.session == c.session) && c.observation != null && c.observation.commandIdentity.contains(c.identity) &&
        statuses(c.status) && c.order != null && c.order.distinct == c.order && c.revision > 0))

  def enqueueC(state: State, session: String, input: S.Input, source: String, revision: Long): Consequence[State] = for {
    _ <- check(valid(state) && exact(session) && exact(source) && revision > 0 && input != null &&
      input.delegation.exists(c => c.role == "destination" && c.destinationIdentity == state.chat), "crossed queue admission")
    original <- state.entries.find(e => e.session == session || e.request == input.requestIdentity) match {
      case Some(entry) if entry.session == session && entry.request == input.requestIdentity && entry.input == input.asJson =>
        Consequence.success(Some(entry))
      case Some(_) => invalid("redelivery changes the retained request or native Session")
      case None => Consequence.success(Option.empty[Entry])
    }
    _ <- check(original.nonEmpty || !input.delegation.flatMap(_.prerequisiteFor).exists(parent =>
      state.entries.exists(e => e.request == parent && !Set("delivered", "cancelled")(e.status))),
      "dependency-cycle: prerequisite cannot queue behind its own unfinished request in the same chat")
  } yield if (original.nonEmpty) state else state.copy(
    entries = state.entries :+ Entry(session, input.requestIdentity, input.asJson, "queued"),
    order = state.order :+ session, events = state.events :+ Event(session, "enqueue", source, revision))

  def transitionC(state: State, session: String, action: String, source: String, revision: Long): Consequence[State] = for {
    _ <- check(valid(state) && exact(source) && revision > 0, "invalid native queue or source")
    entry <- state.entries.find(_.session == session).map(Consequence.success).getOrElse(invalid("request is not admitted"))
    status <- action match {
      case "claim" if entry.status == "active" => Consequence.success("active")
      case "claim" if entry.status == "queued" && state.order.headOption.contains(session) && !state.entries.exists(_.status == "active") =>
        Consequence.success("active")
      case "deliver" if Set("active", "delivered")(entry.status) => Consequence.success("delivered")
      case "hold" if Set("queued", "active", "held")(entry.status) => Consequence.success("held")
      case "cancel" if Set("queued", "active", "held", "cancelled")(entry.status) => Consequence.success("cancelled")
      case "resume" if Set("held", "queued")(entry.status) => Consequence.success("queued")
      case _ => invalid("queue transition crosses its actual frontier")
    }
    next = if (status == entry.status) state else state.copy(
      entries = state.entries.map(e => if (e.session == session) e.copy(status = status) else e),
      order = if (action == "resume") state.order :+ session
        else if (Set("held", "cancelled", "delivered")(status)) state.order.filterNot(_ == session) else state.order,
      events = state.events :+ Event(session, action, source, revision))
    _ <- check(valid(next), "invalid queue successor")
  } yield next

  private final case class Saved(record: InstanceRecord, state: State)
  private val codec = new WorkflowResultJsonV1.PayloadCodec[State] {
    val typeIdentity = "AssignedDevelopmentChatQueue"
    def encode(value: State): Json = value.asJson
    def decode(value: Json): Either[String, State] = value.as[State].left.map(_.message)
      .flatMap(state => Either.cond(valid(state), state, "invalid assigned-chat native queue"))
  }
  private def identity(chat: String): InstanceIdentity = InstanceIdentity("delegate-chat:" + java.net.URLEncoder.encode(chat, "UTF-8"))
  private def reference(record: InstanceRecord) = WorkflowMechanicalTransition.Reference(
    record.identity.value + ":chat-queue:" + record.revision.value, "1")
  private val empty = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](())
  private def event(record: InstanceRecord, cause: String): HistoryEntry = HistoryEntry(
    HistorySequence(record.history.size.toLong + 1), record.revision.next, Lifecycle.Active,
    Some(ProgressionReference("task.Running")), record.correlation, CausationReference(cause), None, None, None)

  private def loadC(configuration: Configuration, definition: DefinitionBinding, store: WorkflowComponentStorage,
    chat: String, core: ActionCall.Core): Consequence[Saved] = for {
    instances <- store.bindInstancesC()(using core.executionContext)
    current <- instances.loadC(identity(chat))(using core.executionContext)
    saved <- current match {
      case Some(record) => for {
        _ <- check(record.definition == definition && record.lifecycle == Lifecycle.Active && record.suspension.isEmpty,
          "queue identity belongs to another definition or frontier")
        mechanical <- store.bindMechanicalC(DelegateTaskNativeRun.advanceOperation, codec)
        value <- mechanical.loadRetainedC(reference(record))(using core.executionContext)
          .flatMap(_.map(v => Consequence.success(v.receipt.payload.value)).getOrElse(invalid("queue checkpoint is unavailable; no execution permitted")))
        _ <- check(value.chat == chat, "queue chat changed")
      } yield Saved(record, value)
      case None => for {
        initial <- InstanceRecord(identity(chat), definition, initialRevision, Lifecycle.NotStarted, None,
          CorrelationReference(identity(chat).value), CausationReference("assigned-chat-queue"), None, None, None, Vector.empty).validateC
        _ <- instances.createC(initial)(using core.executionContext)
        active <- instances.appendC(initial.identity, initial.revision, event(initial, "assigned-chat-queue-started"))(using core.executionContext)
        saved <- saveC(configuration, store, Saved(active, State(chat, Vector.empty, Vector.empty, Vector.empty)),
          State(chat, Vector.empty, Vector.empty, Vector.empty), core)
      } yield saved
    }
  } yield saved

  private def saveC(configuration: Configuration, store: WorkflowComponentStorage, saved: Saved,
    next: State, core: ActionCall.Core): Consequence[Saved] = for {
    _ <- check(valid(next), "invalid queue checkpoint")
    mechanical <- store.bindMechanicalC(DelegateTaskNativeRun.advanceOperation, codec)
    predicted <- saved.record.appendC(saved.record.revision, event(saved.record, "assigned-chat-queue-transition"))
    retained <- mechanical.executeC(WorkflowMechanicalTransition.Intent(configuration, reference(predicted),
      saved.record, saved.record.revision, event(saved.record, "assigned-chat-queue-transition"),
      TypedValue(codec.typeIdentity, next)), empty)(using core.executionContext)
    _ <- check(retained.receipt.payload.value == next, "concurrent queue transition retained a different payload; retry observation without execution")
  } yield Saved(retained.receipt.next, retained.receipt.payload.value)

  def enqueueC(h: BoundedWorkRuntime.Host, definition: DefinitionBinding, store: WorkflowComponentStorage,
    request: InstanceRecord, input: S.Input, core: ActionCall.Core): Consequence[State] = for {
    saved <- loadC(h.configuration, definition, store, input.delegation.get.destinationIdentity, core)
    next <- enqueueC(saved.state, request.identity.value, input, request.identity.value, request.revision.value)
    updated <- if (next == saved.state) Consequence.success(saved) else saveC(h.configuration, store, saved, next, core)
  } yield updated.state

  def permitC(h: BoundedWorkRuntime.Host, definition: DefinitionBinding, store: WorkflowComponentStorage,
    request: InstanceRecord, state: S.State, core: ActionCall.Core): Consequence[Boolean] = for {
    // Reconnection also completes an enqueue interrupted after the request's Start.
    _ <- enqueueC(h, definition, store, request, state.input, core)
    saved <- loadC(h.configuration, definition, store, state.input.delegation.get.destinationIdentity, core)
    entry = saved.state.entries.find(_.session == request.identity.value).get
    delivered = Set("ReturnReady", "Returned")(state.stage)
    eligible = if (delivered) Set("active", "delivered")(entry.status)
      else entry.status == "active" || (entry.status == "queued" && saved.state.order.headOption.contains(entry.session))
    next <- if (!eligible) Consequence.success(saved.state)
      else transitionC(saved.state, entry.session, if (delivered) "deliver" else "claim", request.identity.value, request.revision.value)
    _ <- if (next == saved.state) Consequence.unit else saveC(h.configuration, store, saved, next, core).map(_ => ())
  } yield eligible

  /** The delivery Operation has already checked participant, absence of pending
    * semantic work and an explicit original control grant. Unknown effect stages
    * never release the active queue place.
    */
  def controlC(h: BoundedWorkRuntime.Host, definition: DefinitionBinding, store: WorkflowComponentStorage,
    request: InstanceRecord, state: S.State, observation: DelegateTask.Observation, core: ActionCall.Core): Consequence[State] = for {
    _ <- check(DependencyDevelopmentDelivery.queued(h.managedDevelopment, state.input) && request.suspension.isEmpty &&
      Set("Implement", "Human", "ReturnReady", "Returned")(state.stage), "queue control requires a safe bounded frontier without unresolved effects")
    contract = state.input.delegation.get
    action = observation.event.stripPrefix("queue-")
    _ <- check(Set("hold", "cancel", "resume")(action) && contract.permittedOperations.contains("queue-" + action) &&
      contract.authorizationReferences.contains(observation.sourceReference) && observation.result.isEmpty &&
      observation.artifactSelection.isEmpty && observation.destinationSessionId.contains(request.identity.value),
      "queue control requires its original explicit operation/authorization grant and exact destination Session")
    saved <- loadC(h.configuration, definition, store, contract.destinationIdentity, core)
    next <- controlC(saved.state, request.identity.value, observation, request.revision.value)
    _ <- if (next == saved.state) Consequence.unit else saveC(h.configuration, store, saved, next, core).map(_ => ())
  } yield next


  /** A command identity denotes its complete immutable body, independently of
    * the reusable grant. Replays expose the retained outcome without applying
    * an old transition over a newer explicit direction. */
  def controlC(state: State, session: String, observation: DelegateTask.Observation, revision: Long): Consequence[State] = for {
    _ <- check(valid(state) && observation != null && observation.commandIdentity.exists(exact),
      "queue control requires a distinct immutable commandIdentity")
    identity = observation.commandIdentity.get
    prior = state.controls.toVector.flatten.find(_.identity == identity)
    _ <- check(prior.forall(c => c.session == session && c.observation == observation),
      "control command identity was already bound to another complete body")
    next <- if (prior.nonEmpty) Consequence.success(state) else for {
      changed <- transitionC(state, session, observation.event.stripPrefix("queue-"), identity, revision)
      outcome = Control(identity, session, observation, changed.entries.find(_.session == session).get.status, changed.order, revision)
    } yield changed.copy(controls = Some(changed.controls.getOrElse(Vector.empty) :+ outcome))
    _ <- check(valid(next), "invalid retained command outcome")
  } yield next

  private def phaseStoreC(s: ProviderPhaseDelivery.Storage, core: ActionCall.Core): Consequence[(DefinitionBinding, WorkflowComponentStorage)] = for {
    definition <- DelegateTaskRuntimeCatalog.loadC()
    store <- WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
      SmWorkflowComponent.componentId, s.configuration, Vector(definition), s.instances, s.continuations)
  } yield definition -> store

  private def phaseBinding(start: GoalPhaseNativeRun.Payload): Option[DependencyDevelopmentDelivery.Binding] =
    start.dependencyDelivery.filter(_.mode == DependencyDevelopmentDelivery.assignedChat)

  /** A genuine Phase Start is the admission, using its own native immutable
    * input and identity. The chat aggregate still belongs to DelegateTask's
    * existing definition; it never acquires a second execution lifecycle. */
  def enqueuePhaseC(state: State, session: String, revision: String, start: GoalPhaseNativeRun.Payload): Consequence[State] = for {
    _ <- check(valid(state) && exact(session) && revision.toLongOption.exists(_ > 0) &&
      phaseBinding(start).exists(_.chat.contains(state.chat)), "crossed provider Phase admission")
    wire = GoalPhaseNativeRun.codec.encode(start)
    prior = state.entries.find(_.session == session)
    _ <- check(prior.forall(e => e.request == "provider-phase:" + session && e.input == wire &&
      e.phaseStartRevision.contains(revision)), "provider Phase redelivery changes its actual Start")
  } yield if (prior.nonEmpty) state else state.copy(entries = state.entries :+
    Entry(session, "provider-phase:" + session, wire, "queued", Some(revision)), order = state.order :+ session,
    events = state.events :+ Event(session, "enqueue", session + ":ordered-start", revision.toLong))

  def enqueuePhaseC(s: ProviderPhaseDelivery.Storage, session: String, revision: String,
    start: GoalPhaseNativeRun.Payload, core: ActionCall.Core): Consequence[Unit] = phaseBinding(start).fold(Consequence.unit) { binding => for {
    _ <- GoalPhaseParticipantAdmission.admitC(start.participantIdentity, core.executionContext)
    bound <- phaseStoreC(s, core)
    saved <- loadC(s.configuration, bound._1, bound._2, binding.chat.get, core)
    next <- enqueuePhaseC(saved.state, session, revision, start)
    _ <- if (next == saved.state) Consequence.unit else saveC(s.configuration, bound._2, saved, next, core).map(_ => ())
  } yield () }

  def permitPhaseC(s: ProviderPhaseDelivery.Storage, source: GoalPhaseNativeRun.Session,
    core: ActionCall.Core, requireActive: Boolean = false): Consequence[Boolean] = {
    val start = source.started.receipt.payload.value
    phaseBinding(start).fold(Consequence.success(true)) { binding => for {
      _ <- enqueuePhaseC(s, source.current.identity.value, source.started.receipt.next.revision.value.toString, start, core)
      bound <- phaseStoreC(s, core)
      saved <- loadC(s.configuration, bound._1, bound._2, binding.chat.get, core)
      entry = saved.state.entries.find(_.session == source.current.identity.value).get
      eligible = entry.status == "active" || (!requireActive && entry.status == "queued" && saved.state.order.headOption.contains(entry.session))
      next <- if (!eligible) Consequence.success(saved.state)
        else transitionC(saved.state, entry.session, "claim", source.current.identity.value, source.current.revision.value)
      _ <- if (next == saved.state) Consequence.unit else saveC(s.configuration, bound._2, saved, next, core).map(_ => ())
    } yield eligible }
  }

  def bindPhaseC(s: ProviderPhaseDelivery.Storage, source: GoalPhaseNativeRun.Session,
    locator: ProviderPhaseDelivery.Locator, core: ActionCall.Core): Consequence[Unit] = {
    val start = source.started.receipt.payload.value
    phaseBinding(start).fold(Consequence.unit) { binding => for {
      _ <- check(ProviderPhaseDelivery.valid(locator) && locator.sessionId == source.current.identity.value &&
        locator.startRevision == source.started.receipt.next.revision.value.toString, "crossed provider delivery locator")
      _ <- enqueuePhaseC(s, locator.sessionId, locator.startRevision, start, core)
      bound <- phaseStoreC(s, core)
      saved <- loadC(s.configuration, bound._1, bound._2, binding.chat.get, core)
      entry = saved.state.entries.find(_.session == locator.sessionId).get
      _ <- check(entry.phaseLocator.forall(_ == locator), "provider Phase delivery locator is already frozen")
      next = saved.state.copy(entries = saved.state.entries.map(e => if (e.session == locator.sessionId)
        e.copy(phaseLocator = Some(locator)) else e))
      _ <- if (next == saved.state) Consequence.unit else saveC(s.configuration, bound._2, saved, next, core).map(_ => ())
    } yield () }
  }

  /** Bind and release only after the provider owner's actual accepted source and
    * immutable scoped publication have been checked by ProviderPhaseDelivery. */
  def publishedPhaseC(s: ProviderPhaseDelivery.Storage, start: GoalPhaseNativeRun.Payload,
    accepted: ProviderPhaseDelivery.Accepted, publication: ManagedDevelopment.Publication, core: ActionCall.Core): Consequence[Unit] =
    phaseBinding(start).fold(Consequence.unit) { binding => for {
      _ <- check(publication.identity == accepted.locator.publication && publication.producerSelection == accepted.locator.producerSelection &&
        publication.edge.provider.repository == start.plan.repository &&
        ManagedDevelopmentBoundedWork.acceptedPublicationSource(publication.source, accepted.input.candidate.head, accepted.input.candidate.tree),
        "provider release requires its actual accepted scoped publication")
      _ <- enqueuePhaseC(s, accepted.locator.sessionId, accepted.locator.startRevision, start, core)
      bound <- phaseStoreC(s, core)
      saved <- loadC(s.configuration, bound._1, bound._2, binding.chat.get, core)
      entry <- saved.state.entries.find(_.session == accepted.locator.sessionId).map(Consequence.success).getOrElse(invalid("provider Phase was not admitted"))
      _ <- check(entry.phaseLocator.forall(_ == accepted.locator), "provider publication locator changed")
      attached = saved.state.copy(entries = saved.state.entries.map(e => if (e.session == entry.session)
        e.copy(phaseLocator = Some(accepted.locator)) else e))
      next <- transitionC(attached, entry.session, "deliver", "provider-publication:" + publication.identity, accepted.sourceRevision.toLong)
      _ <- if (next == saved.state) Consequence.unit else saveC(s.configuration, bound._2, saved, next, core).map(_ => ())
    } yield () }

  private def exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  private def check(value: Boolean, reason: String): Consequence[Unit] = if (value) Consequence.unit else invalid(reason)
  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("Assigned development chat: " + reason)
}
