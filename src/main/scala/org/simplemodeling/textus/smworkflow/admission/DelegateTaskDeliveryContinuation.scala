package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as _, *}
import org.goldenport.protocol.operation.OperationResponse
import org.simplemodeling.textus.smworkflow.value as V
import BoundedWorkRuntime.*
import DelegateTaskNativeRun as N
import StandaloneFixExecution as S

/** Re-evaluate a stopped read-only delivery judgment against admitted new evidence. */
private[smworkflow] object DelegateTaskDeliveryContinuation {
  private val schema = "delegate-task-delivery-continuation-target.v1"
  private val prefix = "standalone-completed:"
  val choices = Vector("ReevaluateDelivery", "Defer")
  private given Encoder[ContextReference] = deriveEncoder
  private given Decoder[ContextReference] = deriveDecoder

  final case class Target(schema: String, componentInstance: String, sessionId: String, participant: String,
    frontierRevision: Long, continuationId: String, sourceRevision: Long, resultReference: ContextReference,
    originalWorkerIdentity: String, sourceCommit: String, sourceTree: String, pendingConditions: Vector[String], artifactCoordinates: Vector[String],
    artifactSha256: Option[String])
  given Encoder[Target] = deriveEncoder
  given Decoder[Target] = deriveDecoder
  final case class Qualification(sourceCommit: String, sourceTree: String, conditionIds: Vector[String],
    artifactCoordinates: Vector[String], artifactSha256: Option[String])
  given Encoder[Qualification] = deriveEncoder
  given Decoder[Qualification] = deriveDecoder
  final case class Admission(target: Target, choice: String, rationale: String, reference: ContextReference,
    upstream: Vector[ContextReference], qualification: Option[Qualification])
  given Encoder[Admission] = deriveEncoder
  given Decoder[Admission] = deriveDecoder

  private def exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
  private def reference(value: ContextReference): Boolean = value != null && exact(value.identity) && exact(value.revision)
  private def objectId(value: String): Boolean = value != null && value.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")
  private def exactList(values: Vector[String]): Boolean =
    values != null && values.distinct == values && values.forall(exact)

  def recognizes(value: Json): Boolean = value.hcursor.get[String]("schema").contains(schema)
  def fromText(value: String): Option[Json] = io.circe.parser.parse(value).toOption.filter(recognizes)

  def targetC(value: Json): Consequence[Target] = value.as[Target].fold(e => N.invalid(e.message), target =>
    if (target.asJson == value && recognizes(value) &&
        Vector(target.componentInstance, target.sessionId, target.participant, target.continuationId).forall(exact) &&
        target.sourceRevision > 0 && target.sourceRevision < target.frontierRevision &&
        reference(target.resultReference) && exact(target.originalWorkerIdentity) &&
        objectId(target.sourceCommit) && objectId(target.sourceTree) &&
        exactList(target.pendingConditions) && target.pendingConditions.nonEmpty && exactList(target.artifactCoordinates))
      Consequence.success(target)
    else N.invalid("Delivery continuation target is incomplete or crossed"))

  private def bindingC(value: Admission): Consequence[Json] = {
    val target = value.target
    val advises = value.choice == "ReevaluateDelivery" && value.qualification.exists(q =>
      q.sourceCommit == target.sourceCommit && q.sourceTree == target.sourceTree &&
      exactList(q.conditionIds) && q.conditionIds.nonEmpty &&
      target.pendingConditions.filter(q.conditionIds.contains) == q.conditionIds) && value.upstream.nonEmpty
    val defers = value.choice == "Defer" && value.qualification.isEmpty && value.upstream.isEmpty
    if (exact(value.rationale) && reference(value.reference) && value.upstream.forall(reference) &&
        value.upstream.distinct == value.upstream && !value.upstream.contains(value.reference) &&
        !value.upstream.contains(target.resultReference) && (advises || defers))
      Consequence.success(Json.obj("componentInstance" -> target.componentInstance.asJson,
        "delegateTaskSemanticContinuation" -> Json.obj("target" -> target.asJson,
          "answer" -> Json.obj("source" -> "Human".asJson, "choice" -> value.choice.asJson,
            "rationale" -> value.rationale.asJson, "participant" -> target.participant.asJson,
            "reference" -> value.reference.asJson, "upstream" -> value.upstream.asJson,
            "deliveryQualification" -> value.qualification.asJson))))
    else N.invalid("Delivery continuation requires independently attributable evidence for unsettled conditions and the retained source")
  }

  def inputC(target: Target, input: V.DelegateTaskSemanticContinuationInput): Consequence[(Admission, Json)] = for {
    _ <- if (input.sessionId == target.sessionId && input.participant == target.participant &&
        fromText(input.target).contains(target.asJson)) Consequence.unit
      else N.invalid("Delivery continuation crosses its owner or Session")
    value = Admission(target, input.choice, input.rationale, ContextReference(input.reference.identity, input.reference.revision),
      input.upstream.map(r => ContextReference(r.identity, r.revision)),
      input.deliveryQualification.map(q =>
        Qualification(q.sourceCommit, q.sourceTree, q.conditionIds.toVector, q.artifactCoordinates, q.artifactSha256)))
    binding <- bindingC(value)
  } yield (value, binding)

  def selectBinding(value: Json): Either[String, Json] = for {
    owner <- value.hcursor.get[String]("componentInstance").left.map(_.message)
    entry = value.hcursor.downField("delegateTaskSemanticContinuation")
    raw <- entry.get[Json]("target").left.map(_.message)
    target <- targetC(raw).toOption.toRight("Invalid delivery continuation target")
    answer = entry.downField("answer")
    choice <- answer.get[String]("choice").left.map(_.message)
    rationale <- answer.get[String]("rationale").left.map(_.message)
    ref <- answer.get[ContextReference]("reference").left.map(_.message)
    upstream <- answer.get[Vector[ContextReference]]("upstream").left.map(_.message)
    qualification <- answer.get[Option[Qualification]]("deliveryQualification").left.map(_.message)
    normalized <- bindingC(Admission(target, choice, rationale, ref, upstream, qualification)).toOption
      .toRight("Invalid delivery continuation evidence")
    _ <- Either.cond(owner == target.componentInstance && normalized == value, (), "Crossed delivery continuation evidence")
  } yield normalized

  private def _cause(current: InstanceRecord) =
    current.history.dropRight(1).lastOption.filter(entry =>
      entry.revision.next == current.revision && entry.causation.value.startsWith(prefix))

  private def _original_c(storage: WorkflowComponentStorage, current: InstanceRecord,
    core: ActionCall.Core): Consequence[Option[(Long, String, S.Work)]] = _cause(current) match {
    case None => Consequence.success(None)
    case Some(entry) => for {
      parts <- entry.causation.value.stripPrefix(prefix).split(":", 2).toVector match {
        case Vector(revision, id) if revision.toLongOption.exists(_ > 0) && exact(id) =>
          Consequence.success(revision.toLong -> id)
        case _ => N.invalid("Native delivery completion causation is malformed")
      }
      issued <- storage.bindIssuedC(N.completionOperation, N.inputCodec)
      interaction <- issued.loadC(ContinuationIdentity(parts._2))(using core.executionContext)
        .flatMap(_.map(Consequence.success).getOrElse(N.invalid("Native delivery issue is absent")))
      work <- NativeWorkflowOperationOutput.workInputC(interaction)
    } yield Option.when(work.kind == "DeliveryAcceptance")((parts._1, parts._2, work))
  }

  def observeC(storage: WorkflowComponentStorage, current: InstanceRecord, payload: Payload,
    owner: String, core: ActionCall.Core): Consequence[Option[Target]] =
    if (current.suspension.nonEmpty || payload.state.stage != "Human") Consequence.success(None)
    else _original_c(storage, current, core).flatMap {
      case None => Consequence.success(None)
      case Some((revision, id, raw)) => for {
        source <- N.loadPayloadC(storage, current.identity.value, revision, core)
        state <- N.hydrateStateC(raw.state, current.identity.value, core, Some(source))
        _ <- if (raw.session == current.identity.value && raw.sourceRevision == revision.toString &&
            state.stage == "DeliveryAcceptance" && state == source.state &&
            payload.copy(state = source.state) == source.copy(supersededWork = None))
          Consequence.unit else N.invalid("Delivery continuation crosses its original issued work or retained effects")
        completions <- storage.bindCompletionC(N.completionOperation, N.inputCodec, N.resultCodec, N.closingType,
          (_, _) => Consequence.success(Vector.empty))
        retained <- completions.loadRetainedC(ContinuationIdentity(id))(using core.executionContext)
          .flatMap(_.map(Consequence.success).getOrElse(N.invalid("Actual DeliveryAcceptance completion is absent")))
        done = retained.completion.result
        worker <- done.evidence.workerIdentity.filter(exact).map(Consequence.success)
          .getOrElse(N.invalid("Original delivery judgment worker is absent"))
        _ <- if (done.result.value.outcome == "decision-needed" &&
          payload.state.input.delegation.exists(_.role == "destination")) Consequence.unit
          else N.invalid("Delivery continuation requires an accepted decision-needed destination judgment")
        stopped <- S.completedC(raw.copy(state = state), done.result.value, done.resultReference)
        _ <- if (payload.state.copy(evidenceReferences = stopped.evidenceReferences) == stopped)
          Consequence.unit else N.invalid("Current Human frontier differs from the actual DeliveryAcceptance completion")
        commit <- state.commit.map(Consequence.success).getOrElse(N.invalid("Accepted delivery commit is absent"))
        coordinates = state.input.delegation.toVector.flatMap(_.artifactCoordinates)
        published = state.delivery.flatMap(_.publication).flatMap(_.artifact).map(_.sha256)
        result = Target(schema, owner, current.identity.value, state.participant, current.revision.value, id, revision,
          done.resultReference, worker, commit.identity, commit.tree, done.result.value.conditions.filterNot(_.satisfied).map(_.identity),
          coordinates, published)
        validated <- targetC(result.asJson)
      } yield Some(validated)
    }

  def output(target: Target, outcome: String, message: String): OperationResponse =
    NativeWorkflowOperationOutput.response(V.DelegateTaskSemanticContinuationDescriptionResult(target.sessionId,
      target.componentInstance, outcome, message, target.asJson.noSpaces, choices, DelegateTaskSemanticContinuation.resumeOperation))

  def workC(storage: WorkflowComponentStorage, payload: Payload,
    core: ActionCall.Core): Consequence[Option[S.DeliveryReevaluation]] = {
    val admissions = payload.deliveryContinuations.getOrElse(Vector.empty)
    admissions.lastOption match {
      case None => Consequence.success(None)
      case Some(previous) => for {
        completions <- storage.bindCompletionC(N.completionOperation, N.inputCodec, N.resultCodec, N.closingType,
          (_, _) => Consequence.success(Vector.empty))
        retained <- completions.loadRetainedC(ContinuationIdentity(previous.target.continuationId))(using core.executionContext)
          .flatMap(_.map(Consequence.success).getOrElse(N.invalid("Retained original DeliveryAcceptance is absent")))
        done = retained.completion.result
        _ <- if (done.resultReference == previous.target.resultReference && done.result.value.outcome == "decision-needed" &&
          done.evidence.workerIdentity.contains(previous.target.originalWorkerIdentity)) Consequence.unit
          else N.invalid("Retained original DeliveryAcceptance result differs from the admitted continuation")
      } yield Some(S.DeliveryReevaluation(admissions, done.result.value))
    }
  }
}
