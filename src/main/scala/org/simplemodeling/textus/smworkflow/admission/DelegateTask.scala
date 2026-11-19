/**
 * @since   Oct. 27, 2026
 * @version Nov. 19, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.syntax.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.simplemodeling.textus.smworkflow.value as V
import StandaloneFixExecution as S
import StandaloneFixExecution.given

/** Delegation business data only. The bounded owner remains the sole executor,
  * repair counter and native continuation/commit owner. Caller transport facts
  * remain attributable observations, never certificates or executor authority.
  */
private[smworkflow] object DelegateTask {
  final case class Contract(requesterIdentity: String, destinationIdentity: String, returnDestination: String,
    componentIdentity: String, role: String, validationScope: String, permittedOperations: Vector[String],
    exclusions: Vector[String], authorizationReferences: Vector[ContextReference], publicationTarget: Option[String],
    artifactCoordinates: Vector[String], consumerInstructions: String)
  final case class Observation(event: String, sourceReference: ContextReference, destinationSessionId: Option[String],
    result: Option[Json], artifactSelection: Option[Json], rationale: String)
  final case class ArtifactSelection(sourceCommit: String, coordinates: Vector[String], selectionReference: ContextReference,
    activationReference: Option[ContextReference], consumerCheckReferences: Vector[ContextReference])
  final case class Delivery(destinationSessionId: Option[String] = None, observations: Vector[Observation] = Vector.empty,
    publication: Option[LocalPublication.Result] = None, returned: Option[Json] = None,
    artifactSelection: Option[Json] = None, adoptionScope: Option[String] = None,
    managedPublication: Option[ManagedDevelopment.Publication] = None,
    managedConsumer: Option[ManagedDevelopment.Result] = None,
    managedContinuation: Option[GoalPhaseDependencyValidation.ManagedCapture] = None)
  private given Encoder[ContextReference] = deriveEncoder
  private given Decoder[ContextReference] = deriveDecoder
  given Encoder[ArtifactSelection] = deriveEncoder
  given Decoder[ArtifactSelection] = deriveDecoder
  given Encoder[Contract] = deriveEncoder
  given Decoder[Contract] = deriveDecoder
  given Encoder[Observation] = deriveEncoder
  given Decoder[Observation] = deriveDecoder
  given Encoder[Delivery] = deriveEncoder[Delivery].mapJson(_.mapObject(_.filter {
    case ("managedPublication" | "managedConsumer" | "managedContinuation", value) => !value.isNull
    case _ => true
  }))
  given Decoder[Delivery] = deriveDecoder

  final case class AdoptionTarget(componentInstance: String, sessionId: String, sourceRevision: String, participant: String,
    input: S.Input, returned: Json, destinationSessionId: String)
  // returned is an opaque native checkpoint, not another public Record. Record
  // decoding omits null object members, which would alter the exact target used
  // by independent adoption evidence. Carry its complete JSON through that
  // boundary as text; do not relax the evidence-to-current-target comparison.
  private val adoptionTargetEncoder: Encoder[AdoptionTarget] = deriveEncoder
  private val adoptionTargetDecoder: Decoder[AdoptionTarget] = deriveDecoder
  given Encoder[AdoptionTarget] = Encoder.instance(value => adoptionTargetEncoder(value)
    .mapObject(_.add("returned", Json.fromString(value.returned.noSpaces))))
  given Decoder[AdoptionTarget] = Decoder.instance { cursor =>
    cursor.get[Json]("returned").flatMap { returned =>
      returned.asString match {
        case Some(raw) => io.circe.parser.parse(raw).left.map(error => io.circe.DecodingFailure(error.message, cursor.history))
          .flatMap { parsed =>
            if (parsed.isObject) adoptionTargetDecoder.decodeJson(cursor.value.mapObject(_.add("returned", parsed)))
            else Left(io.circe.DecodingFailure("Adoption target requires the complete returned checkpoint object", cursor.history))
          }
        // Retained pre-text descriptions remain readable without changing their
        // original content or inventing omitted checkpoint fields.
        case None => adoptionTargetDecoder.tryDecode(cursor)
      }
    }
  }
  def adoptionBinding(target: AdoptionTarget, observation: Observation): Json = Json.obj(
    "componentInstance" -> target.componentInstance.asJson, "delegateAdoption" -> Json.obj(
      "target" -> target.asJson, "observation" -> observation.asJson))
  def selectAdoptionBinding(json: Json): Either[String, Json] = for {
    normalized <- GoalPhaseNativeMetadata.normalizePublicJson(json)
    target <- normalized.hcursor.downField("delegateAdoption").get[AdoptionTarget]("target").left.map(_.message)
    observation <- normalized.hcursor.downField("delegateAdoption").get[Observation]("observation").left.map(_.message)
    owner <- normalized.hcursor.get[String]("componentInstance").left.map(_.message)
    _ <- Either.cond(owner == target.componentInstance && exact(owner) && exact(target.sessionId) &&
      target.sourceRevision.toLongOption.exists(_ > 0) && exact(target.participant) && target.input.delegation.exists(c => valid(c) && c.role == "requester") &&
      observation.event == "adopt" && observation.destinationSessionId.contains(target.destinationSessionId) &&
      reference(observation.sourceReference) && observation.artifactSelection.nonEmpty, (), "Malformed or crossed requester adoption target")
  } yield adoptionBinding(target, observation)

  private def exact(s: String): Boolean = s != null && s.trim.nonEmpty && s.trim == s && !s.exists(_.isControl)
  private def reference(r: ContextReference): Boolean = r != null && exact(r.identity) && exact(r.revision)
  def valid(c: Contract): Boolean = c != null && Vector(c.requesterIdentity, c.destinationIdentity, c.returnDestination,
    c.componentIdentity, c.consumerInstructions).forall(exact) && c.requesterIdentity != c.destinationIdentity &&
    Set("requester", "destination")(c.role) && Set("localized", "broad", "documentation")(c.validationScope) &&
    c.permittedOperations != null && c.permittedOperations.distinct == c.permittedOperations &&
    Set("dispatch", "implement", "validate", "commit", "return", "adopt", "review").subsetOf(c.permittedOperations.toSet) &&
    c.permittedOperations.forall(Set("dispatch", "implement", "validate", "commit", "return", "adopt", "publishLocal", "review")) &&
    c.exclusions != null && c.exclusions.forall(exact) && !c.exclusions.exists(c.permittedOperations.contains) && c.authorizationReferences != null &&
    c.authorizationReferences.nonEmpty && c.authorizationReferences.forall(reference) &&
    c.publicationTarget != null && c.publicationTarget.forall(exact) &&
    (c.publicationTarget.isEmpty || c.permittedOperations.contains("publishLocal")) &&
    c.artifactCoordinates != null && c.artifactCoordinates.forall(exact) &&
    (c.publicationTarget.isEmpty || c.artifactCoordinates.nonEmpty)

  def inputC(v: V.DelegateTaskInput): Consequence[S.Input] = {
    val c = v.delegation
    val contract = Contract(c.requesterIdentity, c.destinationIdentity, c.returnDestination, c.componentIdentity,
      c.role, c.validationScope, c.permittedOperations.toVector, c.exclusions,
      c.authorizationReferences.toVector.map(r => ContextReference(r.identity, r.revision)), c.publicationTarget,
      c.artifactCoordinates, c.consumerInstructions)
    val value = S.Input(v.repositoryIdentity, v.requestIdentity, v.objective, v.ownedPaths.toVector, v.checks.toVector,
      v.conditions.toVector, v.sourceReferences.toVector.map(r => ContextReference(r.identity, r.revision)),
      v.reviewRequired, v.kind, v.scalaLint, v.repairLimit, Some(S.Task(v.sourceKind, v.dependencies)), delegation = Some(contract),
      submissionConditions = Some(v.submissionConditions.toVector))
    S.inputC(value).map(_ => value)
  }

  def requester(input: S.Input): Boolean = input != null && input.delegation != null &&
    input.delegation.exists(c => valid(c) && c.role == "requester")

  /** Current canonical request with exactly the execution role exchanged. */
  def destinationInputC(input: S.Input): Consequence[V.DelegateTaskInput] = for {
    _ <- requesterInputC(input)
    c = input.delegation.get.copy(role = "destination")
    task = input.task.get
  } yield V.DelegateTaskInput(input.repositoryIdentity, input.requestIdentity, input.objective,
    NonEmptyVector.fromVectorUnsafe(input.ownedPaths), NonEmptyVector.fromVectorUnsafe(input.checks),
    NonEmptyVector.fromVectorUnsafe(input.conditions),
    NonEmptyVector.fromVectorUnsafe(input.sourceReferences.map(r => V.WorkflowArtifactReference(r.identity, r.revision))),
    input.reviewRequired, input.kind, input.scalaLint, input.repairLimit, task.sourceKind, task.dependencies,
    V.DelegateTaskContract(c.requesterIdentity, c.destinationIdentity, c.returnDestination, c.componentIdentity,
      c.role, c.validationScope, NonEmptyVector.fromVectorUnsafe(c.permittedOperations), c.exclusions,
      NonEmptyVector.fromVectorUnsafe(c.authorizationReferences.map(r => V.WorkflowArtifactReference(r.identity, r.revision))),
      c.publicationTarget, c.artifactCoordinates, c.consumerInstructions), input.submissionConditions.getOrElse(Vector.empty))

  /** Passive request policy remains applicable without destination check bindings. */
  def requesterInputC(input: S.Input): Consequence[Unit] = for {
    _ <- S.inputC(input)
    _ <- if (requester(input)) Consequence.unit else invalid("requester admission requires an exact requester contract")
    _ <- if ((input.kind == "documentation") == (input.delegation.get.validationScope == "documentation"))
      Consequence.unit else invalid("incomplete owners, authority, delivery or impact policy")
  } yield ()

  /** Requester admission retains destination requirements as request data.
    * No destination check binding or execution fact belongs to this role.
    */
  def startRequesterC(input: S.Input, participant: String): Consequence[S.State] = for {
    _ <- requesterInputC(input)
    _ <- if (exact(participant)) Consequence.unit
      else invalid("requester Start requires an exact requester contract and actual participant")
  } yield S.State(input, participant, "DispatchReady", 1, Vector.empty, None, Vector.empty, 0, 0,
    GoalPhaseRuntimeRepairControl.Context(GoalPhaseRuntimeRepairControl.version, input.repairLimit),
    message = "One authorized existing-chat dispatch is ready for the caller; no transport or destination work is implied",
    delivery = Some(Delivery()))

  /** Deterministic scope admission independent of sibling skills. Broad/full
    * requests retain FULL as well as focused behavior; no HEAVY substitution.
    */
  def selectChecksC(input: S.Input, available: Vector[GoalPhaseNativeValidation.Check]): Consequence[Vector[GoalPhaseNativeValidation.Check]] = {
    val explicit = input.checks.flatMap(id => available.find(_.identity == id))
    val lint = available.filter(_.kind == WorkflowSbtValidation.Kind.FixCheck)
    if (input.checks.exists(id => available.count(_.identity == id) != 1)) invalid("required check has no unambiguous installed binding")
    else if (input.scalaLint == "required" && !explicit.exists(_.kind == WorkflowSbtValidation.Kind.FixCheck) && lint.size != 1)
      invalid("required Scala lint needs one unambiguous installed fix-check binding")
    else {
      val required = if (input.scalaLint == "required" && !explicit.exists(_.kind == WorkflowSbtValidation.Kind.FixCheck)) explicit ++ lint else explicit
      if (input.delegation.exists(_.validationScope == "broad") && !required.exists(_.kind == WorkflowSbtValidation.Kind.Test)) {
      val full = available.filter(_.kind == WorkflowSbtValidation.Kind.Test)
      if (full.size != 1) invalid("broad uncovered impact requires one unambiguous installed FULL binding")
      else Consequence.success(required ++ full)
      } else Consequence.success(required)
    }
  }
  def selectedChecksValid(input: S.Input, selected: Vector[GoalPhaseNativeValidation.Check]): Boolean =
    selected.filter(c => input.checks.contains(c.identity)).map(_.identity) == input.checks &&
      selected.filterNot(c => input.checks.contains(c.identity)).forall(c =>
        !selected.exists(e => input.checks.contains(e.identity) && e.kind == c.kind) &&
          ((input.scalaLint == "required" && c.kind == WorkflowSbtValidation.Kind.FixCheck) ||
          (input.delegation.exists(_.validationScope == "broad") && c.kind == WorkflowSbtValidation.Kind.Test))) &&
      selected.filterNot(c => input.checks.contains(c.identity)).map(_.kind).distinct.size == selected.count(c => !input.checks.contains(c.identity)) &&
      (input.scalaLint != "required" || selected.exists(_.kind == WorkflowSbtValidation.Kind.FixCheck)) &&
      selected.map(_.identity).distinct.size == selected.size
  def checksC(input: S.Input, selected: Vector[GoalPhaseNativeValidation.Check]): Consequence[Unit] = {
    val contract = input.delegation.get
    val kinds = selected.map(_.kind).toSet
    if (!valid(contract) || (input.kind == "documentation") != (contract.validationScope == "documentation"))
      invalid("incomplete owners, authority, delivery or impact policy")
    else if (input.reviewRequired && !contract.permittedOperations.contains("review")) invalid("required review operation is outside authorization")
    else if (input.kind == "code" && (!kinds(WorkflowSbtValidation.Kind.Compile) || !kinds(WorkflowSbtValidation.Kind.TestOnly)))
      invalid("code delivery requires compile and applicable executed focused behavioral tests")
    else if (contract.validationScope == "broad" && !kinds(WorkflowSbtValidation.Kind.Test)) invalid("broad or explicit FULL impact requires a selected FULL check")
    else Consequence.unit
  }

  /** Stable native identity for one request/role, not a queue or separate index.
    * Redelivered Start addresses the existing instance rather than a repair Session.
    */
  def sessionIdentity(input: S.Input): String = {
    val c = input.delegation.get
    "delegate:" + Vector(input.repositoryIdentity, c.componentIdentity, input.requestIdentity, c.role,
      if (c.role == "requester") c.requesterIdentity else c.destinationIdentity)
      .map(java.net.URLEncoder.encode(_, "UTF-8")).mkString(":")
  }

  def publicationInput(session: String, state: S.State): LocalPublication.Input =
    LocalPublication.Input(state.input.requestIdentity + ":delegate-publication:" + session,
      state.input.delegation.get.publicationTarget.get, None)

  /** Validate producer facts at their demonstrated scope. The source locator is
    * retained by receipt; this does not authenticate another runtime's storage.
    */
  def returnedC(expected: S.Input, json: Json,
    hydrate: S.State => Consequence[S.State] = (s: S.State) => Consequence.success(s)): Consequence[S.State] = for {
    normalized <- GoalPhaseNativeMetadata.normalizePublicJson(json).fold(invalid(_), Consequence.success)
    decoded <- normalized.as[S.State].fold(e => invalid(e.message), Consequence.success)
    state <- hydrate(decoded)
    _ <- S.inputC(state.input)
    contract <- state.input.delegation.map(Consequence.success).getOrElse(invalid("returned result is not delegation"))
    _ <- if (state.stage == "ReturnReady" || state.stage == "Returned") Consequence.unit else invalid("producer delivery is unfinished")
    _ <- if (returnedInventoryValid(expected, state.input) &&
      state.input.copy(ownedPaths = expected.ownedPaths, sourceReferences = expected.sourceReferences,
        delegation = expected.delegation, submissionConditions = expected.submissionConditions) == expected &&
      expected.delegation.exists(c => c.copy(role = "destination") == contract)) Consequence.unit
      else invalid("returned request, owners, scope or authorization differs")
    _ <- if (state.input.submissionConditions == expected.submissionConditions || expected.submissionConditions.isEmpty)
      Consequence.unit else invalid("returned condition timing differs from the original explicit request")
    _ <- WorkflowRepairAssessment.checksC(state.input.kind, state.input.scalaLint, state.selected, state.receipts)
    _ <- checksC(state.input, state.selected)
    _ <- if (state.conditionReport.map(_.identity) == state.input.conditions && state.conditionReport.forall(_.satisfied) &&
      state.unresolvedQuestions.isEmpty && state.findings.isEmpty && S.committedResultValid(state) &&
      (!state.input.reviewRequired || state.reviewReference.nonEmpty) && state.delivery.exists(d =>
        contract.publicationTarget.isEmpty || d.publication.exists(p => p.ready &&
          p.request.target.identity == contract.publicationTarget.get && p.request.target.component == contract.componentIdentity &&
          p.request.source.head == state.commit.get.identity) || d.managedPublication.exists(p =>
            contract.publicationTarget.contains(p.edge.identity) && p.edge.provider.identity == contract.componentIdentity &&
            ManagedDevelopmentBoundedWork.acceptedPublicationSource(p.source, state.commit.get) &&
            contract.artifactCoordinates == Vector(p.selected.organization + ":" + p.selected.module + ":" + p.selected.version)))) Consequence.unit
      else invalid("returned result lacks accepted conditions, local commit or required actual publication")
  } yield state

  /** The retained reference identifies a correction; it does not authenticate
    * another runtime's storage. The caller/effect boundary owns attribution. */
  private def returnedInventoryValid(expected: S.Input, actual: S.Input): Boolean = {
    val paths = actual.ownedPaths
    val refs = actual.sourceReferences
    val added = paths.drop(expected.ownedPaths.size)
    val addedRefs = refs.drop(expected.sourceReferences.size)
    paths.startsWith(expected.ownedPaths) && refs.startsWith(expected.sourceReferences) &&
      addedRefs.distinct == addedRefs && !addedRefs.exists(expected.sourceReferences.contains) &&
      added.forall(path => S.permitted(path, actual)) && (added.isEmpty || addedRefs.nonEmpty)
  }

  def observeC(state: S.State, observation: Observation,
    hydrate: S.State => Consequence[S.State] = (s: S.State) => Consequence.success(s)): Consequence[S.State] = for {
    c <- state.input.delegation.filter(valid).map(Consequence.success).getOrElse(invalid("observation has no admitted delegation"))
    _ <- if (observation != null && reference(observation.sourceReference) && exact(observation.rationale) &&
      observation.destinationSessionId != null && observation.destinationSessionId.forall(exact) && observation.result != null &&
      observation.artifactSelection != null && observation.artifactSelection.forall(_.asObject.exists(_.nonEmpty))) Consequence.unit
      else invalid("observation requires actual source, exact Session and rationale")
    d = state.delivery.getOrElse(Delivery())
    prior = d.observations.find(_.event == observation.event)
    next <- prior match {
      case Some(value) if value == observation => Consequence.success(state)
      case Some(_) => invalid("retained transport/adoption observation is immutable")
      case None => observation.event match {
        case "dispatch-observed" if c.role == "requester" && state.stage == "DispatchReady" &&
            observation.destinationSessionId.nonEmpty && observation.result.isEmpty && observation.artifactSelection.isEmpty =>
          Consequence.success(state.copy(stage = "AwaitResult", delivery = Some(d.copy(destinationSessionId = observation.destinationSessionId,
            observations = d.observations :+ observation)), message = "Dispatch observed; no destination acceptance or work result is implied"))
        case "result-received" if c.role == "requester" && state.stage == "AwaitResult" &&
            observation.destinationSessionId == d.destinationSessionId && observation.result.nonEmpty && observation.artifactSelection.isEmpty => for {
          producer <- returnedC(state.input, observation.result.get, hydrate)
          _ <- if (producer.delivery.flatMap(_.destinationSessionId) == d.destinationSessionId) Consequence.unit
            else invalid("returned producer Session differs from the observed dispatch")
        } yield state.copy(stage = "ResultReceived", delivery = Some(d.copy(returned = observation.result,
          observations = d.observations :+ observation)), message = "Attributable producer result attached; requester integration/adoption remains distinct")
        case "return-observed" if c.role == "destination" && state.stage == "ReturnReady" &&
            observation.result.isEmpty && observation.artifactSelection.isEmpty =>
          Consequence.success(state.copy(stage = "Returned", delivery = Some(d.copy(observations = d.observations :+ observation)),
            message = "Destination result return observed; consumer adoption and activation are separate"))
        case "adopt" if c.role == "requester" && state.stage == "ResultReceived" && observation.result.isEmpty &&
            observation.destinationSessionId == d.destinationSessionId && observation.artifactSelection.nonEmpty => for {
          producer <- d.returned.map(value => returnedC(state.input, value, hydrate)).getOrElse(invalid("Adoption has no actual received producer result"))
          selected <- observation.artifactSelection.get.as[ArtifactSelection].fold(e => invalid(e.message), Consequence.success)
          _ <- if (selected.sourceCommit == producer.commit.get.identity && selected.coordinates == c.artifactCoordinates &&
            reference(selected.selectionReference) && selected.activationReference != null && selected.activationReference.forall(reference) &&
            selected.consumerCheckReferences != null && selected.consumerCheckReferences.forall(reference)) Consequence.unit
            else invalid("Artifact selection crosses the returned source/coordinates or lacks attributable selection references")
        } yield state.copy(stage = "Adopted", delivery = Some(d.copy(artifactSelection = observation.artifactSelection, adoptionScope = Some("result-and-selection-attachment-only"),
            observations = d.observations :+ observation)), message = "Requester adopted the attributed result/selection attachment; this does not execute or certify consumer activation or checks")
        case "defer" if c.role == "requester" && Set("AwaitResult", "ResultReceived")(state.stage) =>
          Consequence.success(state.copy(delivery = Some(d.copy(observations = d.observations :+ observation)), message = "Requester deferred adoption; producer work is retained"))
        case _ => invalid("event crosses role, retained request or delivery frontier")
      }
    }
  } yield next

  def terminal(stage: String): Boolean = Set("Returned", "Adopted")(stage)
  private def invalid[A](message: String): Consequence[A] = Consequence.stateConflict("DelegateTask: " + message)
}
