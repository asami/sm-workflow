package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.config.{ComponentParameterDecoder, ComponentParameterKey}
import org.goldenport.cncf.security.SecuritySubject
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workspace.FileWritePolicy
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.value as V

/** Derived importer retention uses CommonEvidence's collection, File UoW and
  * confirmed storage observation. Attribution never authenticates imported facts.
  * @since   Nov.  9, 2026
  * @version Nov.  9, 2026
  */
private[smworkflow] object WorkflowPreparedImport {
  val schema = "sm-workflow.prepared-import.v1"
  private val prefix = "prepared-import:"
  // Independently selected write capacity. A read policy grants no write capacity.
  val maxWriteBytes = ComponentParameterKey.optional("workflow.preparation.max-write-bytes",
    ComponentParameterDecoder(value => WorkflowArtifactBudget.decodeC(value).recoverWith(
      _ => Consequence.configurationInvalid("workflow.preparation.max-write-bytes requires an integer supported by CNCF FileWritePolicy (1..268435456 bytes)"))))

  private def invalid[A](message: String): Consequence[A] =
    Consequence.stateConflict("Prepared import " + message)
  private def canonical(value: Json): Json = value.arrayOrObject(value,
    values => Json.fromValues(values.map(canonical)),
    fields => Json.fromFields(fields.toVector.sortBy(_._1).map((key, value) => key -> canonical(value))))
  private def publicFieldC(value: Option[Json], camel: String, snake: String): Consequence[Option[Json]] = {
    val first = value.flatMap(_.hcursor.downField(camel).focus).filterNot(_.isNull)
    val second = value.flatMap(_.hcursor.downField(snake).focus).filterNot(_.isNull)
    if (first.nonEmpty && second.nonEmpty && first != second) invalid("contradictory public field aliases: " + camel)
    else Consequence.success(first.orElse(second))
  }
  private def sourceReferenceC(value: Option[Json]): Consequence[Option[Json]] = value match {
    case None => Consequence.success(None)
    case Some(json) => (json.hcursor.get[String]("identity").toOption, json.hcursor.get[String]("revision").toOption) match {
      case (Some(identity), Some(revision)) if identity.nonEmpty && revision.nonEmpty =>
        Consequence.success(Some(Json.obj("identity" -> Json.fromString(identity), "revision" -> Json.fromString(revision))))
      case _ => invalid("supplied business source reference has no exact identity/revision")
    }
  }

  /** Actual business references and native source revisions are identities.
    * Observer timestamps, issuers and generated answer filenames are not. */
  def associationC(component: String, participant: String, operation: String, input: Json,
    request: Option[Json], suppliedReference: Option[Json]): Consequence[CommonEvidence.Source] = for {
    context <- publicFieldC(request, "issuedWorkContext", "issued_work_context")
    target = request.flatMap(_.hcursor.downField("target").focus)
    directSession <- publicFieldC(request, "sessionId", "session_id")
    contextSession <- publicFieldC(context, "runId", "run_id")
    targetSession <- publicFieldC(target, "sessionId", "session_id")
    sourceRevision <- publicFieldC(target, "sourceRevision", "source_revision")
    expectedRevision <- publicFieldC(context, "expectedRevision", "expected_revision")
    targetRevision <- publicFieldC(target, "expectedRevision", "expected_revision")
    continuation <- publicFieldC(context, "continuationId", "continuation_id")
    targetContinuation <- publicFieldC(target, "oldContinuationId", "old_continuation_id")
    handle = context.flatMap(_.hcursor.downField("handle").focus)
    workflow <- publicFieldC(handle, "workflowIdentity", "workflow_identity")
    actualReference <- sourceReferenceC(suppliedReference)
    session = targetSession.orElse(contextSession).orElse(directSession)
    _ <- if (session.forall(_.asString.exists(_.nonEmpty))) Consequence.unit else invalid("native Session is not a literal string")
    // A pre-Start input may have only its exact selected input resource. There
    // is no invented native Session/revision or guessed snapshot filename.
    selectors = Set("family", "kind", "selectionId", "idempotencyKey", "collection", "selectionCollection", "planCollection")
    selected = input.asObject.toVector.flatMap(_.toVector).filter((key, _) => selectors(key))
    fallback = if (actualReference.nonEmpty || session.nonEmpty) Json.Null else Json.fromFields(
      input.asObject.toVector.flatMap(_.toVector).filter((key, _) => Set("input", "plan", "source", "allocationReference", "request")(key)))
    scope = Json.obj("sourceReference" -> actualReference.getOrElse(Json.Null),
      "selectedInputResources" -> fallback, "selectors" -> Json.fromFields(selected),
      "sourceRevision" -> sourceRevision.orElse(expectedRevision).orElse(targetRevision).getOrElse(Json.Null),
      "continuation" -> continuation.orElse(targetContinuation).getOrElse(Json.Null),
      "envelopeSession" -> directSession.getOrElse(Json.Null), "issuedSession" -> contextSession.getOrElse(Json.Null),
      "targetSession" -> targetSession.getOrElse(Json.Null),
      "workflow" -> workflow.getOrElse(Json.Null))
    binding = canonical(Json.obj("schemaVersion" -> Json.fromString(schema),
      "componentInstance" -> Json.fromString(component), "participantIdentity" -> Json.fromString(participant),
      "operation" -> Json.fromString(operation), "scope" -> scope, "nativeSession" -> session.getOrElse(Json.Null)))
    source = CommonEvidence.Source(ContextReference(prefix + Base64.getUrlEncoder.withoutPadding
      .encodeToString(binding.noSpaces.getBytes(UTF_8)), "1"), "preparation", "preparation", None,
      session.flatMap(_.asString).getOrElse("host-preparation"), operation, "prepared")
    _ <- if (CommonEvidence.validSource(source) && CommonEvidence.validIdentity(CommonEvidence.identity(source))) Consequence.unit else invalid("association exceeds the supported literal reference budget")
  } yield source

  private def bindingC(source: CommonEvidence.Source): Consequence[Json] = try {
    if (!source.reference.identity.startsWith(prefix)) invalid("source has no preparation association")
    else {
      val encoded = source.reference.identity.drop(prefix.length)
      val bytes = Base64.getUrlDecoder.decode(encoded)
      val text = UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString
      io.circe.parser.parse(text).fold(error => invalid(error.message), value =>
        if (source.reference.revision == "1" && source.actionOutcome == "prepared" &&
          value.asObject.exists(_.keys.toSet == Set("schemaVersion", "componentInstance", "participantIdentity", "operation", "scope", "nativeSession")) &&
          source.repository == "preparation" && source.phase == "preparation" && source.step.isEmpty &&
          source.session == value.hcursor.get[String]("nativeSession").toOption.getOrElse("host-preparation") &&
          value.hcursor.get[String]("schemaVersion").contains(schema) &&
          canonical(value).noSpaces == text &&
          Base64.getUrlEncoder.withoutPadding.encodeToString(bytes) == encoded)
          Consequence.success(value)
        else invalid("source association is noncanonical"))
    }
  } catch { case scala.util.control.NonFatal(_) => invalid("source association is invalid") }

  def admitSourceC(source: CommonEvidence.Source, core: ActionCall.Core): Consequence[Unit] =
    if (!source.reference.identity.startsWith(prefix) && source.actionOutcome != "prepared") Consequence.unit
    else for {
      binding <- bindingC(source)
      component <- core.component.map(Consequence.success).getOrElse(invalid("Component is unavailable"))
      participant <- binding.hcursor.get[String]("participantIdentity").fold(error => invalid(error.message), Consequence.success)
      _ <- GoalPhaseParticipantAdmission.admitC(participant, core.executionContext)
      _ <- if (binding.hcursor.get[String]("componentInstance").contains(component.core.instanceId.canonicalKey) &&
        binding.hcursor.get[String]("operation").contains(source.output)) Consequence.unit
        else invalid("belongs to another Component or preparation purpose")
    } yield ()

  def retainC(core: ActionCall.Core, operation: String, input: Record, prepared: Record,
    reads: Map[String, String]): Consequence[Record] = for {
    participant <- Consequence.success(SecuritySubject.from(core.executionContext.security).subjectId)
    _ <- GoalPhaseParticipantAdmission.admitC(participant, core.executionContext)
    _ <- input.getString("participant") match {
      case Some(value) => GoalPhaseParticipantAdmission.admitC(value, core.executionContext)
      case None => Consequence.unit
    }
    component <- core.component.map(Consequence.success).getOrElse(invalid("Component is unavailable"))
    raw <- input.toJsonStringC
    inputjson <- io.circe.parser.parse(raw).fold(error => invalid(error.message), Consequence.success)
    request = input.getString("request").flatMap(reads.get).flatMap(io.circe.parser.parse(_).toOption)
    actualReference = input.getString("reference").flatMap(reads.get).flatMap(io.circe.parser.parse(_).toOption)
    source <- associationC(component.core.instanceId.canonicalKey, participant, operation, inputjson, request, actualReference)
    text <- prepared.toJsonStringC
    policy <- CommonEvidence.resolvePolicyC(component.initializationParameters, Vector.empty)
    result <- policy match {
      case None => Consequence.success(resultRecord("setup-required", "Derived import retention requires the configured CommonEvidence policy; select explicit legacy-full for the historical complete output", None))
      case Some(p) => for {
        selected <- component.initializationParameters.resolve(maxWriteBytes)
        write = FileWritePolicy(selected.value.getOrElse(org.goldenport.cncf.resource.ResourceReadLimits(67108864)))
        retained <- retainWithStorageC(CommonEvidence.storage(core, p, Some(write)), p, source, text, core.executionContext.clock)
      } yield retained
    }
  } yield result

  /** Recollection compares complete bytes under the existing owner. It is not a
    * digest certificate, source authentication or permission to retry UNKNOWN. */
  def retainWithStorageC(store: CommonEvidence.Storage, policy: CommonEvidence.Policy,
    source: CommonEvidence.Source, text: String, clock: java.time.Clock): Consequence[Record] = {
    val result = for {
      _ <- store.admitSourceC(source)
      existing <- CommonEvidence.loadedC(store, CommonEvidence.identity(source))
      compatible <- existing match {
        case Some((allocation, _)) if allocation.source != source || allocation.host != policy.hostIdentity ||
            allocation.workspace != policy.workspaceIdentity => Consequence.success(false)
        case Some((allocation, Some(observation))) if observation.status == "stored" =>
          CommonEvidence.payloadC(store, allocation).map(_.contains(text))
        case _ => Consequence.success(true)
      }
      result <- if (!compatible) Consequence.success(resultRecord("conflict",
        "The same source association has contradictory or unavailable content; retained bytes preserved", None))
      else CommonEvidence.archiveManagedC(store, policy, source, text, "identity", clock).map {
        case (state, Some((allocation, observation))) if state.status == "stored" && observation.status == "stored" =>
          resultRecord("prepared", "Derived importer retained; no check acceptance, origin authentication, provisioning or Workflow transition", Some(reference(allocation)))
        case (state, _) => resultRecord(state.status, "Derived import storage is " + state.status +
          "; no full-body fallback or automatic replay", None)
      }
    } yield result
    result.recover(_ => resultRecord("unavailable", "Derived import association or storage unavailable; no write retry or full-body fallback", None))
  }

  private def reference(a: CommonEvidence.Allocation): V.WorkflowPreparedImportReference = {
    val binding = bindingC(a.source).toOption.get
    V.WorkflowPreparedImportReference(schema, binding.hcursor.get[String]("componentInstance").toOption.get,
      binding.hcursor.get[String]("participantIdentity").toOption.get, a.source.output,
      V.WorkflowArtifactReference(a.source.reference.identity, a.source.reference.revision), V.EvidenceReference(a.identity, "1"))
  }
  private def resultRecord(status: String, message: String, reference: Option[V.WorkflowPreparedImportReference]): Record =
    V.WorkflowPreparationResult(Some(status), Some(message), reference, Vector.empty).toRecord()

  def readC(core: ActionCall.Core, input: V.WorkflowPreparedImportReference): Consequence[Record] = for {
    _ <- if (input != null && input.schemaVersion == schema && input.evidence != null &&
      CommonEvidence.validIdentity(input.evidence.identity) && input.evidence.revision == "1") Consequence.unit
      else invalid("detail requires the exact supported reference and revision")
    _ <- GoalPhaseParticipantAdmission.admitC(input.participantIdentity, core.executionContext)
    component <- core.component.map(Consequence.success).getOrElse(invalid("Component is unavailable"))
    _ <- if (component.core.instanceId.canonicalKey == input.componentInstance) Consequence.unit else invalid("detail crosses its Component")
    policy <- CommonEvidence.resolvePolicyC(component.initializationParameters, Vector.empty)
    p <- policy.map(Consequence.success).getOrElse(invalid("detail requires the owning CommonEvidence policy"))
    store = CommonEvidence.storage(core, p)
    retained <- CommonEvidence.loadedC(store, input.evidence.identity)
      .flatMap(_.map(Consequence.success).getOrElse(invalid("selected association is absent")))
    (allocation, observation) = retained
    _ <- store.admitSourceC(allocation.source)
    _ <- if (allocation.host == p.hostIdentity && allocation.workspace == p.workspaceIdentity &&
      observation.exists(_.status == "stored") && reference(allocation) == input) Consequence.unit
      else invalid("detail differs from the exact confirmed owner, purpose or revision")
    text <- CommonEvidence.payloadC(store, allocation).flatMap(_.map(Consequence.success)
      .getOrElse(invalid("selected payload is missing")))
    record <- new org.goldenport.record.io.RecordDecoder().json(text)
  } yield record
}
