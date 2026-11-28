/**
 * @since   Oct. 27, 2026
 * @version Nov. 27, 2026
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
    artifactCoordinates: Vector[String], consumerInstructions: String, prerequisiteFor: Option[String] = None,
    providerPhase: Option[ProviderPhaseDelivery.Locator] = None,
    synchronization: Option[DevelopmentDeliverySynchronization.Selection] = None)
  final case class Observation(event: String, sourceReference: ContextReference, destinationSessionId: Option[String],
    result: Option[Json], artifactSelection: Option[Json], rationale: String, commandIdentity: Option[String] = None)
  final case class ArtifactSelection(sourceCommit: String, coordinates: Vector[String], selectionReference: ContextReference,
    activationReference: Option[ContextReference], consumerCheckReferences: Vector[ContextReference])
  final case class Delivery(destinationSessionId: Option[String] = None, observations: Vector[Observation] = Vector.empty,
    publication: Option[LocalPublication.Result] = None, returned: Option[Json] = None,
    artifactSelection: Option[Json] = None, adoptionScope: Option[String] = None,
    managedPublication: Option[ManagedDevelopment.Publication] = None,
    managedConsumer: Option[ManagedDevelopment.Result] = None,
    managedContinuation: Option[GoalPhaseDependencyValidation.ManagedCapture] = None,
    phaseDelivery: Option[ProviderPhaseDelivery.Delivery] = None,
    synchronization: Option[GoalPhasePostCompletionSync.Synchronization] = None)
  private given Encoder[ContextReference] = deriveEncoder
  private given Decoder[ContextReference] = deriveDecoder
  given Encoder[ArtifactSelection] = deriveEncoder
  given Decoder[ArtifactSelection] = deriveDecoder
  given Encoder[Contract] = deriveEncoder[Contract].mapJson(_.mapObject(_.filter {
    case ("prerequisiteFor" | "providerPhase" | "synchronization", value) => !value.isNull
    case _ => true
  }))
  given Decoder[Contract] = deriveDecoder
  given Encoder[Observation] = deriveEncoder[Observation].mapJson(_.mapObject(fields =>
    if (fields("commandIdentity").contains(Json.Null)) fields.remove("commandIdentity") else fields))
  given Decoder[Observation] = deriveDecoder
  given Encoder[Delivery] = deriveEncoder[Delivery].mapJson(_.mapObject(_.filter {
    case ("managedPublication" | "managedConsumer" | "managedContinuation" | "phaseDelivery" | "synchronization", value) => !value.isNull
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
  private val _focused_test_prefix = "test-only."
  private val _scala_test_root = "src/test/scala/"
  private val _suite_name = "[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*"

  def focusedTestSuite(identity: String): Option[String] =
    Option(identity).filter(id => id.startsWith(_focused_test_prefix) && id.length <= 128)
      .map(_.stripPrefix(_focused_test_prefix)).filter(_.matches(_suite_name))

  /** An existing full test and one explicitly owned conventional spec give the
    * canonical focused selection. The caller does not choose an extra suite. */
  def approvedFocusedChecks(input: S.Input): Option[Vector[String]] = {
    val checks = input.checks
    val paths = input.ownedPaths.filter(path => path.startsWith(_scala_test_root) && path.endsWith("Spec.scala"))
    if (input.kind == "code" && checks.contains("compile") && checks.contains("test") &&
      !checks.exists(id => id == "test-only" || id.startsWith(_focused_test_prefix))) paths match {
      case Vector(path) =>
        val identity = _focused_test_prefix + path.stripPrefix(_scala_test_root).stripSuffix(".scala").replace('/', '.')
        focusedTestSuite(identity).filter(suite => path == _scala_test_root + suite.replace('.', '/') + ".scala")
          .map(_ => checks.patch(checks.indexOf("test"), Vector(identity), 0))
      case _ => None
    } else None
  }

  /** Require the declared selection at the ordinal and root the runner will
    * consume. The CNCF program still owns admission and test execution. */
  def focusedTestProgramC(plan: Json, root: String,
    check: GoalPhaseNativeValidation.Check): Consequence[Unit] = focusedTestSuite(check.identity) match {
    case None => Consequence.unit
    case Some(suite) =>
      val workspace = "sm-workflow-repository-validation-" + check.ordinal
      val roots = plan.hcursor.get[Vector[Json]]("workspaces").getOrElse(Vector.empty)
        .filter(_.hcursor.get[String]("identity").contains(workspace))
      val programs = plan.hcursor.get[Vector[Json]]("sbtPrograms").getOrElse(Vector.empty).filter(program =>
        program.hcursor.get[String]("target").contains(workspace) &&
          program.hcursor.get[String]("operation").contains("test-only"))
      if (check.kind == WorkflowSbtValidation.Kind.TestOnly &&
        roots.size == 1 && roots.head.hcursor.get[String]("root").contains(root) &&
        programs.size == 1 && programs.head.hcursor.get[Vector[String]]("testClasses").contains(Vector(suite)) &&
        !programs.head.hcursor.get[Boolean]("discoveredTests").contains(true))
        Consequence.unit
      else invalid("approved focused test requires its exact fixed TestOnly binding in the destination checkout: " + suite)
  }

  def valid(c: Contract): Boolean = c != null && Vector(c.requesterIdentity, c.destinationIdentity, c.returnDestination,
    c.componentIdentity, c.consumerInstructions).forall(exact) && c.requesterIdentity != c.destinationIdentity &&
    Set("requester", "destination")(c.role) && Set("localized", "broad", "documentation")(c.validationScope) &&
    c.permittedOperations != null && c.permittedOperations.distinct == c.permittedOperations &&
    Set("dispatch", "implement", "validate", "commit", "return", "adopt", "review").subsetOf(c.permittedOperations.toSet) &&
    c.permittedOperations.forall(Set("dispatch", "implement", "validate", "commit", "return", "adopt", "publishLocal", "review", "queue-hold", "queue-cancel", "queue-resume", "repository-sync")) &&
    c.exclusions != null && c.exclusions.forall(exact) && !c.exclusions.exists(c.permittedOperations.contains) && c.authorizationReferences != null &&
    c.authorizationReferences.nonEmpty && c.authorizationReferences.forall(reference) &&
    c.publicationTarget != null && c.publicationTarget.forall(exact) &&
    (c.publicationTarget.isEmpty || c.permittedOperations.contains("publishLocal")) &&
    c.prerequisiteFor != null && c.prerequisiteFor.forall(exact) &&
    c.providerPhase != null && c.providerPhase.forall(ProviderPhaseDelivery.valid) &&
    c.synchronization != null && c.synchronization.forall(DevelopmentDeliverySynchronization.valid) &&
    (c.synchronization.isEmpty || c.permittedOperations.contains("repository-sync")) &&
    c.artifactCoordinates != null && c.artifactCoordinates.forall(exact) &&
    (c.publicationTarget.isEmpty || c.artifactCoordinates.nonEmpty)

  def inputC(v: V.DelegateTaskInput): Consequence[S.Input] = for {
    classification <- GoalPhaseWorkClassification.taskInputC(v.workClassification)
    c = v.delegation
    contract = Contract(c.requesterIdentity, c.destinationIdentity, c.returnDestination, c.componentIdentity,
      c.role, c.validationScope, c.permittedOperations.toVector, c.exclusions,
      c.authorizationReferences.toVector.map(r => ContextReference(r.identity, r.revision)), c.publicationTarget,
      c.artifactCoordinates, c.consumerInstructions, c.prerequisiteFor,
      c.providerPhase.map(p => ProviderPhaseDelivery.Locator(p.sessionId, p.startRevision, p.publication, p.producerSelection)),
      c.synchronization.map(p => DevelopmentDeliverySynchronization.Selection(p.repositoryIdentity, p.localBranch,
        p.trackingRemote, p.trackingBranch, p.remoteEndpointIdentity, p.syncMode)))
    value = S.Input(v.repositoryIdentity, v.requestIdentity, v.objective, v.ownedPaths.toVector, v.checks.toVector,
      v.conditions.toVector, v.sourceReferences.toVector.map(r => ContextReference(r.identity, r.revision)),
      v.reviewRequired, v.kind, v.scalaLint, v.repairLimit, Some(S.Task(v.sourceKind, v.dependencies)), delegation = Some(contract),
      submissionConditions = Some(v.submissionConditions.toVector), workClassification = classification)
    _ <- S.inputC(value)
    _ <- if (!value.checks.exists(_.startsWith(_focused_test_prefix)) ||
        approvedFocusedChecks(value.copy(checks = value.checks.filterNot(_.startsWith(_focused_test_prefix)))).contains(value.checks))
      Consequence.unit else invalid("scoped TestOnly differs from the approved owned spec and original full test")
  } yield value

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
      c.publicationTarget, c.artifactCoordinates, c.consumerInstructions, c.prerequisiteFor,
      c.providerPhase.map(p => V.DelegateProviderPhaseLocator(p.sessionId, p.startRevision, p.publication, p.producerSelection)),
      c.synchronization.map(s => V.DelegateDeliverySynchronization(s.repositoryIdentity, s.localBranch,
        s.trackingRemote, s.trackingBranch, s.remoteEndpointIdentity, s.syncMode))),
    input.submissionConditions.getOrElse(Vector.empty), input.workClassification.map(GoalPhaseWorkClassification.taskValue))

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

  /** Qualify the effects retained by the destination owner before judgment or return. */
  def deliveryEffectsC(state: S.State): Consequence[Unit] = for {
    contract <- state.input.delegation.filter(c => valid(c) && c.role == "destination")
      .map(Consequence.success).getOrElse(invalid("delivery effects require an admitted destination contract"))
    _ <- WorkflowRepairAssessment.checksC(state.input.kind, state.input.scalaLint, state.selected, state.receipts)
    _ <- checksC(state.input, state.selected)
    _ <- if ((!state.input.reviewRequired || state.reviewReference.nonEmpty) && S.committedResultValid(state)) Consequence.unit
      else invalid("delivery effects require the retained required review and accepted local commit")
    _ <- if (state.delivery.exists(d => _publication_ready(state, contract, state.commit.get, d))) Consequence.unit
      else invalid("delivery effects lack the required publication for the accepted source and route")
  } yield ()

  private def _publication_ready(state: S.State, contract: Contract, commit: S.Commit, delivery: Delivery): Boolean =
    (contract.publicationTarget, delivery.publication, delivery.managedPublication) match {
      case (None, None, None) => delivery.managedContinuation.isEmpty
      case (Some(target), Some(publication), None) =>
        delivery.managedContinuation match {
          case None => _local_publication_valid(publication) && publication.request.target.identity == target &&
            publication.request.target.component == contract.componentIdentity && publication.request.source.head == commit.identity &&
            publication.request.source.tree == commit.tree
          case Some(capture) => machinePublicationValid(state, commit, capture, publication) &&
            delivery.destinationSessionId.exists(session => publication.request.input == publicationInput(session, state).copy(explicit = true))
        }
      case (Some(target), None, Some(publication)) =>
        publication.edge.localJar && publication.edge.identity == target && publication.edge.provider.identity == contract.componentIdentity &&
          delivery.managedContinuation.forall(_.edge == publication.edge) &&
          ManagedDevelopmentBoundedWork.acceptedPublicationSource(publication.source, commit) &&
          contract.artifactCoordinates == Vector(publication.selected.organization + ":" + publication.selected.module + ":" + publication.selected.version) &&
          ManagedDevelopmentRuntime.retainedPublicationValid(publication)
      case _ => false
    }

  def machinePublicationValid(state: S.State, commit: S.Commit,
    capture: GoalPhaseDependencyValidation.ManagedCapture, publication: LocalPublication.Result,
    program: Option[String] = None): Boolean = {
    val edge = capture.edge
    val target = publication.request.target
    ManagedDevelopment.machineJarValid(edge) && publication.status == "published" && _local_publication_valid(publication) &&
      state.input.repositoryIdentity == edge.provider.repository && publication.request.participant == state.participant &&
      target.workspace == edge.provider.workspace && target.component == edge.provider.identity &&
      edge.machineLocalJar.exists(jar => jar.publicationTarget == target.identity &&
        publication.artifact.exists(a => a.valid && a.path == jar.consumerJar)) &&
      publication.request.source == LocalPublication.Source(commit.identity, commit.tree, Vector.empty) &&
      state.input.delegation.exists(c => c.componentIdentity == target.component && c.publicationTarget.contains(target.identity) &&
        c.artifactCoordinates == Vector(ManagedDevelopment.coordinateString(edge.requested)) && c.providerPhase.isEmpty &&
        c.synchronization.isEmpty && !c.permittedOperations.contains("repository-sync")) &&
      program.forall(name => publication.process.flatMap(WorkflowSbtCapture.decode(_).toOption).exists(_.safeProgramIdentity == name))
  }

  def machineSelectionValid(selected: ArtifactSelection, publication: LocalPublication.Result): Boolean =
    selected.selectionReference == ContextReference(publication.request.input.requestIdentity, "1") &&
      selected.sourceCommit == publication.request.source.head && selected.activationReference.isEmpty &&
      selected.consumerCheckReferences.isEmpty

  private def _local_publication_valid(publication: LocalPublication.Result): Boolean =
    publication.nextPreparation.isEmpty && (publication.status match {
      case "published" => publication.applicability.isEmpty && publication.process.exists(raw =>
        WorkflowSbtCapture.decode(raw).toOption.exists(p => WorkflowSbtCapture.exitCode(p).contains(0)))
      case "unnecessary" => publication.process.isEmpty
      case _ => false
    })

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
    _ <- deliveryEffectsC(state)
    _ <- if (state.conditionReport.map(_.identity) == state.input.conditions && state.conditionReport.forall(_.satisfied) &&
      state.unresolvedQuestions.isEmpty && state.findings.isEmpty) Consequence.unit
      else invalid("returned result lacks accepted conditions or retains unresolved questions or findings")
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
    hydrate: S.State => Consequence[S.State] = (s: S.State) => Consequence.success(s),
    chatCompletion: Boolean = false): Consequence[S.State] = for {
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
        case "completion-received" if chatCompletion && c.role == "requester" &&
            Set("DispatchReady", "AwaitResult", "ResultReceived", "Adopted")(state.stage) &&
            observation.destinationSessionId.forall(id => d.destinationSessionId.forall(_ == id)) &&
            observation.result.isEmpty && observation.artifactSelection.isEmpty && observation.commandIdentity.isEmpty &&
            !d.observations.exists(_.event == "defer") =>
          Consequence.success(state.copy(stage = "Adopted", delivery = Some(d.copy(
            destinationSessionId = d.destinationSessionId.orElse(observation.destinationSessionId),
            observations = d.observations :+ observation)),
            message = "Development chat reported completion; ordinary continuation is ready"))
        case "dispatch-observed" if c.role == "requester" && state.stage == "DispatchReady" &&
            observation.destinationSessionId.nonEmpty && c.providerPhase.forall(p => observation.destinationSessionId.contains(p.sessionId)) && observation.result.isEmpty && observation.artifactSelection.isEmpty =>
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
          producer <- if (d.phaseDelivery.nonEmpty) Consequence.success(Option.empty[S.State])
            else d.returned.map(value => returnedC(state.input, value, hydrate).map(Some(_)))
              .getOrElse(invalid("Adoption has no actual received producer result"))
          selected <- observation.artifactSelection.get.as[ArtifactSelection].fold(e => invalid(e.message), Consequence.success)
          _ <- if (selected.sourceCommit == d.phaseDelivery.map(_.phase.input.candidate.head).getOrElse(producer.get.commit.get.identity) && selected.coordinates == c.artifactCoordinates &&
            reference(selected.selectionReference) && selected.activationReference != null && selected.activationReference.forall(reference) &&
            selected.consumerCheckReferences != null && selected.consumerCheckReferences.forall(reference)) Consequence.unit
            else invalid("Artifact selection crosses the returned source/coordinates or lacks attributable selection references")
          _ <- if (producer.flatMap(_.delivery).forall(delivery =>
            !delivery.managedContinuation.exists(_.edge.machineLocalJar.nonEmpty) ||
              delivery.publication.exists(machineSelectionValid(selected, _)))) Consequence.unit
            else invalid("Machine-common adoption requires the actual ordinary publication request reference and no preclaimed consumer effects")
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
