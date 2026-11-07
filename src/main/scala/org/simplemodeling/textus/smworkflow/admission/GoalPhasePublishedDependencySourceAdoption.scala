package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json, Printer}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.time.Instant
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseNativeRun as F
import GoalPhaseNativeCandidate as C
import GoalPhaseDependencySourceRevision as R
import GoalPhaseDependencyValidation as D
import GoalPhaseDependencyValidation.given
import GoalPhasePrerequisitePublicationRecovery as B
import GoalPhasePrerequisitePublicationHostRecovery as H
import GoalPhasePrerequisitePublicationUnknownRecovery as W

/** Explicit adoption of repaired source and an independently recollected normal
  * publication. The historical successful consumer request is never replaced.
  * @since   Nov.  7, 2026
  * @version Nov.  7, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhasePublishedDependencySourceAdoption {
  val proposalSchema = "sm-workflow.goal-phase-published-dependency-source-proposal.v1"
  val artifactSchema = "sm-workflow.goal-phase-published-dependency-artifact-adoption.v1"
  val sourceChoice = "AdoptPublishedDependencySourceRevision"
  val artifactChoice = "AdoptRecollectedPublishedDependencyArtifact"
  val sourceChoices = Vector(sourceChoice, "Defer")
  val artifactChoices = Vector(artifactChoice, "Defer")
  val collectOperation = "org.simplemodeling.textus.SmWorkflow.LocalPublicationService.collectLocalPublication"
  val sourceCausationPrefix = "ordered-published-dependency-source:"
  val artifactCausationPrefix = "ordered-published-dependency-artifact:"
  val message = "Select the exact repaired dependency source, then separately adopt its actual independently recollected publication. " +
    "The original success and primary failure remain history. Source adoption stages and publishes nothing; ordinary Advance captures source. " +
    "Artifact adoption publishes nothing and grants no primary attempt. The later concrete host-correction target requires genuine Human evidence."

  /** The exact successful normal collect DTO, not an ensure/publication claim. */
  final case class CollectionResult(status: String, reason: String, publication: LocalPublication.Result)
  object CollectionResult {
    given Encoder[CollectionResult] = deriveEncoder[CollectionResult].mapJson(publicJson)
    given Decoder[CollectionResult] = _closed(deriveDecoder, collectionValid)
  }
  final case class PublicationObservation(result: CollectionResult, collectOperation: String,
    recollectionReference: ContextReference, publicationOwner: String, observedAt: String,
    publisherProcessPolicy: String, destination: String, artifact: ContextReference)
  object PublicationObservation {
    given Encoder[PublicationObservation] = deriveEncoder[PublicationObservation].mapJson(publicJson)
    given Decoder[PublicationObservation] = _closed(deriveDecoder, observationValid)
  }
  final case class SourceProposal(schemaVersion: String, source: R.Proposal, publication: PublicationObservation)
  object SourceProposal {
    given Encoder[SourceProposal] = deriveEncoder
    given Decoder[SourceProposal] = _closed(deriveDecoder, proposalValid)
  }
  final case class SourceOrigin(historical: H.Target, publication: PublicationObservation,
    selectedTarget: LocalPublication.Target, predecessor: D.Admission, ancestors: Vector[R.Ancestor],
    addedPaths: Vector[String])
  object SourceOrigin {
    given Encoder[SourceOrigin] = deriveEncoder[SourceOrigin].mapJson(publicJson)
    given Decoder[SourceOrigin] = _closed(deriveDecoder, originValid)
  }
  final case class ArtifactTarget(schemaVersion: String, componentInstance: String, participant: String,
    sessionId: String, frontierRevision: String, controller: String, candidateRecordRevision: String,
    candidate: C.Payload, sourceDirection: ContextReference, origin: SourceOrigin,
    publication: PublicationObservation, guard: R.Guard, processPolicy: String, publicationPolicy: String)
  object ArtifactTarget {
    given Encoder[ArtifactTarget] = deriveEncoder[ArtifactTarget].mapJson(publicJson)
    given Decoder[ArtifactTarget] = _closed(deriveDecoder, artifactTargetValid)
  }
  final case class ArtifactDirection(target: ArtifactTarget, choice: String, rationale: String,
    participant: String, reference: ContextReference)
  object ArtifactDirection {
    given Encoder[ArtifactDirection] = deriveEncoder[ArtifactDirection].mapJson(publicJson)
    given Decoder[ArtifactDirection] = _closed(deriveDecoder, directionValid)
  }
  final case class ArtifactFact(direction: ArtifactDirection, record: ContextReference)
  object ArtifactFact {
    given Encoder[ArtifactFact] = deriveEncoder
    given Decoder[ArtifactFact] = _closed(deriveDecoder, factValid)
  }
  final case class Saved(record: InstanceRecord, direction: ArtifactDirection) {
    def fact: ArtifactFact = ArtifactFact(direction, ContextReference(record.identity.value, record.revision.value.toString))
  }

  def publicJson(value: Json): Json = value.arrayOrObject(value,
    xs => Json.fromValues(xs.map(publicJson)), fields => Json.fromFields(fields.toVector.collect {
      case (key, item) if !item.isNull => key -> publicJson(item)
    }))
  private def _closed[A: Encoder](decoder: Decoder[A], valid: A => Boolean): Decoder[A] =
    Decoder.instance(cursor => decoder(cursor).flatMap(value =>
      if (valid(value) && value.asJson == cursor.value) Right(value)
      else Left(DecodingFailure("Published dependency value is incomplete, crossed or noncanonical", cursor.history))))
  private def _text(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  private def _rationale(value: String): Boolean = value != null && value.trim.nonEmpty && !value.contains('\u0000') &&
    java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(value)
  private def _revision(value: String): Boolean = value != null && value.toLongOption.exists(n => n > 0 && n.toString == value)
  private def _sorted(value: Json): String = Printer.noSpaces.copy(sortKeys = true).print(value)
  private def _policy(value: String): Option[Json] = Option(value).flatMap(io.circe.parser.parse(_).toOption)
    .filter(j => j.asObject.exists(_.nonEmpty) && _sorted(j) == value)
  def sourceOf(guard: R.Guard): LocalPublication.Source = GoalPhaseCurrentSourceArtifactAdoption.sourceOf(guard)
  def collectionValid(value: CollectionResult): Boolean = value != null && value.status == "published" &&
    value.publication != null && value.publication.status == value.status && value.reason == value.publication.reason &&
    value.publication.ready && value.publication.request != null && value.publication.request.target != null &&
    value.publication.request.input != null && value.publication.request.source != null &&
    _text(value.publication.request.participant) && _text(value.publication.request.input.requestIdentity) &&
    value.publication.request.input.targetIdentity == value.publication.request.target.identity &&
    value.publication.process != null && value.publication.nextPreparation != null && value.publication.nextPreparation.isEmpty &&
    value.publication.process.exists(raw => WorkflowSbtCapture.decode(raw).toOption.exists(p =>
      WorkflowSbtCapture.exitCode(p).contains(0) && !p.stdout.truncated && !p.stderr.truncated &&
        WorkflowSbtCapture.text(p.stdout).textStatus == "complete" && WorkflowSbtCapture.text(p.stderr).textStatus == "complete"))
  def observationValid(value: PublicationObservation): Boolean = value != null && collectionValid(value.result) &&
    value.collectOperation == collectOperation && R.referenceValid(value.recollectionReference) &&
    _text(value.publicationOwner) && _text(value.observedAt) && scala.util.Try(Instant.parse(value.observedAt)).isSuccess &&
    _text(value.destination) && scala.util.Try(new java.net.URI(value.destination)).toOption.exists(_.isAbsolute) &&
    R.referenceValid(value.artifact) && _policy(value.publisherProcessPolicy).exists { policy =>
      val target = value.result.publication.request.target
      val programs = policy.hcursor.downField("processPlan").get[Vector[Json]]("sbtPrograms").toOption
        .getOrElse(Vector.empty).filter(p => p.hcursor.get[String]("target").contains(target.workspace) &&
          p.hcursor.get[String]("operation").contains("publish-local"))
      programs.size == 1 && value.result.publication.process.flatMap(WorkflowSbtCapture.decode(_).toOption)
        .exists(p => programs.head.hcursor.get[String]("identity").contains(p.safeProgramIdentity))
    }
  def proposalValid(value: SourceProposal): Boolean = value != null && value.schemaVersion == proposalSchema &&
    R.proposalValid(value.source) && value.source.schemaVersion == R.sourceSchema &&
    value.source.consumption.isInstanceOf[R.Consumption] && observationValid(value.publication)
  def predecessorOf(candidate: C.Payload): Option[D.Admission] = candidate.dependencySourceCapture
    .map(_.direction.target.predecessor).orElse(candidate.dependency.map(_.admission)).orElse(candidate.dependencyAdmission)
  def originValid(value: SourceOrigin): Boolean = value != null && H.targetValid(value.historical) &&
    value.historical.schemaVersion == H.schema && value.historical.publishedDependencyAdoptions.isEmpty &&
    observationValid(value.publication) && value.selectedTarget != null && value.selectedTarget.dependency.nonEmpty &&
    value.historical.dependencyPrefix.exists(_.request.target == value.selectedTarget) &&
    value.publication.result.publication.request.target == value.selectedTarget && D.admissionValid(value.predecessor) &&
    predecessorOf(value.historical.candidate).contains(value.predecessor) &&
    value.selectedTarget.dependency.contains(value.predecessor.binding.identity) &&
    value.selectedTarget.component == value.predecessor.binding.dependency.identity &&
    value.selectedTarget.workspace == value.predecessor.binding.dependency.workspace &&
    _policy(value.publication.publisherProcessPolicy).exists(policy =>
      policy.hcursor.downField("processPlan").get[Vector[Json]]("workspaces").toOption.exists(workspaces => {
        val selected = workspaces.filter(_.hcursor.get[String]("identity").contains(value.selectedTarget.workspace))
        selected.size == 1 && selected.head.hcursor.get[String]("root").contains(value.predecessor.binding.root)
      })) &&
    value.ancestors != null && value.ancestors.forall(R.ancestorValid) && value.addedPaths != null &&
    value.addedPaths.distinct == value.addedPaths && value.addedPaths.forall(p => RepositorySyncNativeConflictInput.relative(p) && !value.predecessor.binding.paths.contains(p)) && {
      val candidate = value.historical.candidate
      val old = value.historical.dependencyPrefix.find(_.request.target == value.selectedTarget).get
      candidate.dependencySourceCapture.exists(c => R.captureValid(c) && old.request.source == sourceOf(c.postStageGuard)) ||
        candidate.dependency.exists(c => D.captureValid(c) && c.session == value.historical.sessionId &&
          c.plan == ContextReference(value.historical.plan.identity, value.historical.plan.revision) &&
          c.primaryBase == value.historical.base && c.generation == value.historical.generation &&
          c.sourceRevision == candidate.sourceRevision && c.admission == value.predecessor &&
          old.request.source.head == c.candidate.head && old.request.source.tree == c.candidate.tree)
    }
  def sourceTargetValid(value: R.Target): Boolean = value.publishedOrigin.exists { origin =>
    val old = origin.historical
    originValid(origin) && value.sessionId == old.sessionId && value.sourceRevision == old.frontierRevision &&
      value.plan == old.plan && value.primaryBase == old.base && value.generation == old.generation &&
      value.completed == old.completed && value.repairProgress == old.repairProgress &&
      value.requestingResult == old.requestingResult && value.step == old.candidate.step &&
      value.predecessor == origin.predecessor && value.ancestors == origin.ancestors &&
      value.predecessorGuard == old.candidate.dependencySourceCapture.map(_.postStageGuard) &&
      value.binding.root == value.predecessor.binding.root && value.binding.branch == value.predecessor.binding.branch &&
      value.binding.paths == value.predecessor.binding.paths ++ origin.addedPaths &&
      value.binding.copy(revision = value.predecessor.binding.revision, paths = value.predecessor.binding.paths,
        base = value.predecessor.binding.base) == value.predecessor.binding &&
      (value.binding.base == value.predecessor.binding.base || value.committedBaseSuccession.exists(_.previousBase == value.predecessor.binding.base)) &&
      origin.publication.result.publication.request.source == sourceOf(value.preStageGuard) &&
      B.primaryProgramMatches(old.processPolicy, old.failed.request.target, value.proposal.consumption)
  }
  def candidatePreserved(origin: SourceOrigin, candidate: C.Payload): Boolean = {
    val old = origin.historical.candidate
    candidate.copy(sourceRevision = old.sourceRevision, dependencyAdmission = old.dependencyAdmission,
      dependencyStaged = old.dependencyStaged, dependency = old.dependency, dependencyOrigin = old.dependencyOrigin,
      dependencySourceDirection = old.dependencySourceDirection, dependencySourceGuard = old.dependencySourceGuard,
      dependencySourceStages = old.dependencySourceStages, dependencySourceCapture = old.dependencySourceCapture,
      dependencySourceScope = old.dependencySourceScope) == old
  }
  def artifactTargetValid(value: ArtifactTarget): Boolean = value != null && value.schemaVersion == artifactSchema &&
    originValid(value.origin) && value.publication == value.origin.publication &&
    value.componentInstance == value.origin.historical.componentInstance && value.participant == value.origin.historical.participant &&
    value.sessionId == value.origin.historical.sessionId && _revision(value.frontierRevision) &&
    _revision(value.candidateRecordRevision) && BigInt(value.candidateRecordRevision) <= BigInt(value.frontierRevision) &&
    R.referenceValid(value.sourceDirection) && _revision(value.sourceDirection.revision) && value.sourceDirection.identity == value.sessionId &&
    value.candidate != null && R.candidateValid(value.candidate) && candidatePreserved(value.origin, value.candidate) &&
    value.candidate.dependencySourceCapture.exists(c => R.captureValid(c) && c.direction.target.publishedOrigin.contains(value.origin) &&
      c.sessionId == value.sessionId && c.sourceRevision == value.sourceDirection.revision &&
      c.direction.target.pendingRevision.toLong + 1 == value.sourceDirection.revision.toLong &&
      c.generation == value.origin.historical.generation + 1 && c.preStageGuard == c.direction.target.preStageGuard &&
      c.postStageGuard == value.guard && sourceOf(c.preStageGuard) == value.publication.result.publication.request.source) &&
    value.processPolicy == value.origin.historical.processPolicy && value.publicationPolicy == value.origin.historical.publicationPolicy &&
    _policy(value.controller).exists { raw =>
      val old = io.circe.parser.parse(value.origin.historical.controller).toOption.get
      // Source capture changes the dependency generation once, not primary work,
      // repair accounting, Plan, completed prefix or original primary snapshot.
      raw == old.mapObject(_.add("generation", (value.origin.historical.generation + 1).asJson))
    }
  def directionValid(value: ArtifactDirection): Boolean = value != null && artifactTargetValid(value.target) &&
    value.choice == artifactChoice && value.participant == value.target.participant && _rationale(value.rationale) &&
    R.referenceValid(value.reference)
  def factValid(value: ArtifactFact): Boolean = value != null && directionValid(value.direction) &&
    R.referenceValid(value.record) && value.record != value.direction.reference && value.record.identity == value.direction.target.sessionId &&
    value.record.revision == (BigInt(value.direction.target.frontierRevision) + 1).toString
  def proposalC(value: Record): Consequence[SourceProposal] = W.jsonC(value).flatMap(_.as[SourceProposal].left.map(_.message).fold(F.invalid, Consequence.success))
  def isProposalC(value: Record): Consequence[Boolean] = W.jsonC(value).map(_.hcursor.get[String]("schemaVersion").contains(proposalSchema))
  def artifactTargetC(value: Record): Consequence[ArtifactTarget] = W.jsonC(value).flatMap(_.as[ArtifactTarget].left.map(_.message).fold(F.invalid, Consequence.success))
  def isArtifactTargetC(value: Record): Consequence[Boolean] = W.jsonC(value).map(_.hcursor.get[String]("schemaVersion").contains(artifactSchema))

  def sourceTargetC(source: W.Source, d: F.Declaration, core: ActionCall.Core, proposal: SourceProposal): Consequence[R.Target] = for {
    _ <- if (proposalValid(proposal) && source.native.from == source.native.session.current) Consequence.unit else F.invalid("Published source proposal/frontier is invalid")
    host <- source.hostSource.map(Consequence.success).getOrElse(F.invalid("Published source selection has no actual native host origin"))
    policy <- d.localPublication.map(Consequence.success).getOrElse(F.invalid("Publication policy is absent"))
    selectedtarget = proposal.publication.result.publication.request.target
    _ <- if (policy.targets.contains(selectedtarget)) Consequence.unit else F.invalid("Recollection target is not declared")
    primary <- policy.targets.filter(t => t.beforeChecks && t.dependency.isEmpty) match {
      case Vector(value) => Consequence.success(value)
      case _ => F.invalid("Published source continuation requires its exact primary target")
    }
    observedfailure <- host.directions.lastOption.filter(H.applicable(_, host)) match {
      case Some(saved) => H.collectC(saved.permit, LocalPublicationRuntime._port(core, policy))
      case None => LocalPublication.collectPrerequisiteC(LocalPublicationRuntime.prerequisiteInput(source.native.from.identity.value,
        source.native.candidate.payload, primary), primary, source.native.session.participantIdentity, LocalPublicationRuntime._port(core, policy))
    }
    failed <- observedfailure.map(Consequence.success).getOrElse(F.invalid("Historical primary failure is absent"))
    historical <- H.originForSourceC(host, failed, selectedtarget, d, core)
    predecessor <- predecessorOf(historical.candidate).map(Consequence.success).getOrElse(F.invalid("Historical dependency admission is absent"))
    _ <- source.native.chain.resolveC(predecessor)
    selected <- d.dependencySourceRevisions.find(b => ContextReference(b.identity, b.revision.toString) == proposal.source.binding)
      .map(Consequence.success).getOrElse(F.invalid("The new source binding is not declared"))
    _ <- if (d.dependencySourceConsumptions.contains(proposal.source.consumption)) Consequence.unit else F.invalid("The new source consumption is not declared")
    guard <- R.observeC(core, selected, proposal.source.excludedPaths)
    committed <- if (selected.base == predecessor.binding.base) Consequence.success(Option.empty[R.CommittedBaseSuccession])
      else if (proposal.source.mode == "committed") R.waitCommittedObservationC(core, selected, proposal.source.excludedPaths,
        historical.requestingResult, predecessor.binding.base).map(Some(_)) else F.invalid("A new base requires explicit committed succession")
    origin = SourceOrigin(historical, proposal.publication, selectedtarget, predecessor,
      source.native.chain.accepted.map(a => R.Ancestor(a.capture, a.closureRevision)), selected.paths.filterNot(predecessor.binding.paths.contains))
    target = R.Target(R.publishedSchema, historical.sessionId, "", historical.frontierRevision, historical.frontierRevision,
      historical.plan, historical.candidate.step, historical.generation, historical.base, historical.completed,
      historical.repairProgress, historical.requestingResult, predecessor, origin.ancestors, proposal.source, selected,
      historical.candidate.dependencySourceCapture.map(_.postStageGuard), guard, committedBaseSuccession = committed,
      publishedOrigin = Some(origin))
    _ <- if (R.targetValid(target)) Consequence.unit else F.invalid("Published source selection crosses its source, original history, program or coverage")
  } yield target
  def describeSourceC(source: W.Source, d: F.Declaration, core: ActionCall.Core, raw: Record): Consequence[V.GoalPhaseDependencySourceRevisionDescriptionResult] = for {
    proposal <- proposalC(raw)
    target <- sourceTargetC(source, d, core, proposal)
  } yield V.GoalPhaseDependencySourceRevisionDescriptionResult(target.sessionId, "direction-required", message,
    target.publishedOrigin.get.historical.componentInstance, R.targetRecord(target), sourceChoices, R.adoptionOperation)
  def adoptSourceC(source: W.Source, d: F.Declaration, core: ActionCall.Core,
    input: V.GoalPhaseDependencySourceRevisionAdoptionInput): Consequence[V.GoalPhaseDependencySourceRevisionAdoptionResult] = for {
    supplied <- R.targetC(input.target)
    origin <- supplied.publishedOrigin.map(Consequence.success).getOrElse(F.invalid("Published source origin is absent"))
    actual <- sourceTargetC(source, d, core, SourceProposal(proposalSchema, supplied.proposal, origin.publication))
    _ <- if (actual == supplied && input.sessionId == actual.sessionId && input.reference != null && sourceChoices.contains(input.choice))
      Consequence.unit else F.invalid("Published source target is stale or crossed")
    _ <- if (input.choice == "Defer") Consequence.unit else for {
      provider <- d.dependencySourceRevision.map(Consequence.success).getOrElse(F.invalid("Independent source Human provider is absent"))
      reference = ContextReference(input.reference.identity, input.reference.revision)
      binding <- R.bindingC(origin.historical.componentInstance, actual, input.choice, input.rationale,
        source.native.session.participantIdentity, reference)
      _ <- provider.evidence.admitC(core, reference, binding)
      _ <- W.savePublishedSourceC(source, d, core, R.Direction(actual, reference, source.native.session.participantIdentity,
        input.rationale, Some(sourceChoice)))
    } yield ()
  } yield V.GoalPhaseDependencySourceRevisionAdoptionResult(input.sessionId, input.choice != "Defer",
    if (input.choice == "Defer") "deferred" else "published-dependency-source-directed", message)
  def historicalSourceC(source: W.Source, d: F.Declaration, core: ActionCall.Core, target: R.Target): Consequence[Unit] = for {
    origin <- target.publishedOrigin.filter(originValid).map(Consequence.success).getOrElse(F.invalid("Published source origin is invalid"))
    host <- source.hostSource.map(Consequence.success).getOrElse(F.invalid("Historical native host source is absent"))
    actual <- H.originForSourceC(host, origin.historical.failed, origin.selectedTarget, d, core, Some(origin.historical))
    _ <- if (actual == origin.historical && R.targetValid(target) &&
        origin.ancestors == source.native.chain.accepted.map(a => R.Ancestor(a.capture, a.closureRevision))) Consequence.unit
      else F.invalid("Published source history changed its actual native predecessor")
    _ <- source.native.chain.resolveC(origin.predecessor)
  } yield ()

  def artifactTargetC(native: B.Source, d: F.Declaration, core: ActionCall.Core,
    observe: Boolean = true, retained: Option[ArtifactTarget] = None): Consequence[ArtifactTarget] = for {
    capture <- native.candidate.payload.dependencySourceCapture.filter(c => R.captureValid(c) && c.direction.target.publishedOrigin.nonEmpty)
      .map(Consequence.success).getOrElse(F.invalid("Published artifact requires the actual completed source capture"))
    origin = capture.direction.target.publishedOrigin.get
    process <- if (observe) B.processPolicyC(d, core) else retained.map(t => Consequence.success(t.processPolicy)).getOrElse(F.invalid("Retained process policy is absent"))
    policy <- d.localPublication.map(Consequence.success).getOrElse(F.invalid("Publication policy is absent"))
    target = ArtifactTarget(artifactSchema, origin.historical.componentInstance, native.session.participantIdentity,
      native.from.identity.value, native.from.revision.value.toString, _sorted(B.controller(native.controller)),
      native.candidate.record.revision.value.toString, native.candidate.payload,
      ContextReference(native.from.identity.value, capture.sourceRevision), origin, origin.publication, capture.postStageGuard,
      process, B.publicationPolicy(policy))
    _ <- if (artifactTargetValid(target) && core.component.get.core.instanceId.canonicalKey == target.componentInstance)
      Consequence.unit else F.invalid("Published artifact target crosses actual source capture, native controller or policy")
    _ <- if (observe) currentArtifactC(target, d, core) else Consequence.unit
  } yield target
  def currentArtifactC(target: ArtifactTarget, d: F.Declaration, core: ActionCall.Core): Consequence[Unit] = for {
    process <- B.processPolicyC(d, core)
    _ <- if (artifactTargetValid(target) && process == target.processPolicy && d.localPublication.exists(p => B.publicationPolicy(p) == target.publicationPolicy))
      Consequence.unit else F.invalid("Published artifact policy changed")
    _ <- R.freshnessC(core, target.candidate.dependencySourceCapture.get)
    _ <- H.checkOriginPublicationsC(target.origin.historical, target.origin.selectedTarget, LocalPublicationRuntime._port(core, d.localPublication.get))
  } yield ()
  def artifactDescription(target: ArtifactTarget): V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult =
    V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult(target.sessionId, "direction-required", message,
      target.componentInstance, GoalPhaseNativeMetadata.record(target.asJson), artifactChoices, B.recoveryOperation)
  def bindingC(component: String, target: ArtifactTarget, choice: String, rationale: String,
    participant: String, reference: ContextReference): Consequence[Json] =
    if (artifactTargetValid(target) && component == target.componentInstance && participant == target.participant &&
        artifactChoices.contains(choice) && _rationale(rationale) && R.referenceValid(reference)) Consequence.success(Json.obj(
      "componentInstance" -> component.asJson, "prerequisitePublicationRecovery" -> Json.obj("target" -> target.asJson,
        "answer" -> Json.obj("choice" -> choice.asJson, "rationale" -> rationale.asJson,
          "participant" -> participant.asJson, "reference" -> reference.asJson))))
    else F.invalid("Published artifact Human binding is incomplete or crossed")
  def selectBinding(value: Json): Either[String, Json] = for {
    component <- value.hcursor.get[String]("componentInstance").left.map(_.message)
    body = value.hcursor.downField("prerequisitePublicationRecovery")
    target <- body.get[ArtifactTarget]("target").left.map(_.message)
    answer = body.downField("answer")
    choice <- answer.get[String]("choice").left.map(_.message)
    rationale <- answer.get[String]("rationale").left.map(_.message)
    participant <- answer.get[String]("participant").left.map(_.message)
    reference <- answer.get[ContextReference]("reference").left.map(_.message)
    selected <- bindingC(component, target, choice, rationale, participant, reference).map[Either[String, Json]](Right(_)).fold(e => Left(e.toString), identity)
    _ <- Either.cond(selected == value, (), "Published artifact evidence binding is noncanonical")
  } yield selected
  val artifactCodec: WorkflowResultJsonV1.PayloadCodec[ArtifactDirection] = new WorkflowResultJsonV1.PayloadCodec[ArtifactDirection] {
    val typeIdentity = "GoalPhasePublishedDependencyArtifactAdoption"
    def encode(value: ArtifactDirection): Json = Json.obj("schemaVersion" -> artifactSchema.asJson, "payload" -> value.asJson)
    def decode(value: Json): Either[String, ArtifactDirection] = value.hcursor.get[ArtifactDirection]("payload").left.map(_.message)
      .flatMap(d => Either.cond(encode(d) == value, d, "Published artifact envelope is noncanonical"))
  }
  private def _reference(from: InstanceRecord): WorkflowMechanicalTransition.Reference =
    WorkflowMechanicalTransition.Reference(from.identity.value + ":" + artifactCausationPrefix + from.revision.value, "1")
  def recoverArtifactC(native: B.Source, d: F.Declaration, core: ActionCall.Core,
    input: V.GoalPhasePrerequisitePublicationRecoveryInput): Consequence[V.GoalPhasePrerequisitePublicationRecoveryResult] = for {
    actual <- artifactTargetC(native, d, core)
    supplied <- artifactTargetC(input.target)
    _ <- if (actual == supplied && input.sessionId == actual.sessionId && input.reference != null && input.hostCorrection != null && input.hostCorrection.isEmpty)
      Consequence.unit else F.invalid("Published artifact target is stale or crossed")
    reference = ContextReference(input.reference.identity, input.reference.revision)
    binding <- bindingC(actual.componentInstance, actual, input.choice, input.rationale, actual.participant, reference)
    _ <- if (input.choice == "Defer") Consequence.unit else for {
      installation <- d.prerequisitePublicationRecovery.map(Consequence.success).getOrElse(F.invalid("Independent artifact Human provider is absent"))
      _ <- installation.validateC
      _ <- installation.evidence.admitC(core, reference, binding)
      adapter <- native.session.storage.bindMechanicalC(F.advanceOperation, artifactCodec)
      existing <- adapter.loadRetainedC(_reference(native.from))(using core.executionContext)
      _ <- if (existing.isEmpty) Consequence.unit else F.invalid("Published artifact fact already exists")
      saved <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, _reference(native.from), native.from,
        native.from.revision, F.history(native.from, "phase.Validation", artifactCausationPrefix + native.from.revision.value),
        TypedValue(artifactCodec.typeIdentity, ArtifactDirection(actual, input.choice, input.rationale, actual.participant, reference))),
        ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
    } yield ()
  } yield V.GoalPhasePrerequisitePublicationRecoveryResult(input.sessionId, input.choice != "Defer",
    if (input.choice == "Defer") "deferred" else "published-dependency-artifact-adopted", message)
  def retainedArtifactC(native: B.Source, d: F.Declaration, core: ActionCall.Core): Consequence[Saved] = for {
    adapter <- native.session.storage.bindMechanicalC(F.advanceOperation, artifactCodec)
    saved <- adapter.loadRetainedC(_reference(native.from))(using core.executionContext).flatMap(_.map(r =>
      Consequence.success(Saved(r.receipt.next, r.receipt.payload.value))).getOrElse(F.invalid("Published artifact history omitted its fact")))
    target <- artifactTargetC(native, d, core, observe = false, retained = Some(saved.direction.target))
    actual <- GoalPhaseNativeHistory.prefixC(native.session.current, saved.record.revision)
    _ <- if (saved.direction.target == target && factValid(saved.fact) && actual == saved.record &&
        saved.record.identity == native.from.identity && saved.record.definition == native.from.definition &&
        saved.record.revision == native.from.revision.next && saved.record.history.dropRight(1) == native.from.history &&
        saved.record.currentProgression == native.from.currentProgression && saved.record.suspension.isEmpty &&
        saved.record.causation.value == artifactCausationPrefix + native.from.revision.value) Consequence.unit
      else F.invalid("Published artifact fact is not its actual adjacent native transition")
  } yield saved
}
