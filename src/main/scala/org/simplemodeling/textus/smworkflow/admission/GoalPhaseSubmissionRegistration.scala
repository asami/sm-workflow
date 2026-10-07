package org.simplemodeling.textus.smworkflow.admission

import domain.statemachine.providedapi.GoalPhaseWorkflowStateMachineProvidedApi1
import io.circe.{Json, JsonObject}
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.workflow.{StateMachineRevision, WorkflowWaitJsonV1}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.GoalPhaseStartCommand

/** Closed private registration data. Preparation/decoding is not publication,
  * authenticated consent, a write grant or a successful transport receipt.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseSubmissionRegistration {
  val schemaVersion = "sm-workflow.goal-phase-submission-registration.v1"
  val storageField = "registrationPayload"
  private val prefix = "sm-workflow.goal-phase-registration-json.v1:"

  final case class Request(registrationIdentity: String, submissionIdentity: String,
    idempotencyIdentity: String, start: GoalPhaseStartCommand)

  final class Record private[GoalPhaseSubmissionRegistration] (
    val request: Request, val originalStart: WorkflowInteraction[Nothing, Nothing]
  )

  def prepareC(request: Request, retained: GoalPhaseStartExecution.RetainedStart): Consequence[Record] =
    if (retained == null || request == null || request.start != retained.admittedCommand)
      Consequence.stateConflict("Registration does not bind its admitted original Start")
    else buildC(request, retained.interaction)

  /** Retrying a registration still needs current host/actor/write admission.
    * This compares retained typed facts only; it authorizes no effect.
    */
  def matchesRetainedC(record: Record, request: Request,
    retained: GoalPhaseStartExecution.RetainedStart): Consequence[Unit] =
    prepareC(request, retained).flatMap { expected =>
      if (record != null && record.request == expected.request &&
          record.originalStart == expected.originalStart) Consequence.unit
      else Consequence.stateConflict("Retained registration belongs to another request")
    }

  def encodeStorageC(record: Record): Consequence[String] =
    if (record == null) Consequence.stateConflict("Registration record is missing")
    else for {
      valid <- buildC(record.request, record.originalStart)
      wait <- WorkflowWaitJsonV1.encodeC(valid.originalStart)
    } yield prefix + Json.obj(
      "schemaVersion" -> Json.fromString(schemaVersion),
      "request" -> requestJson(valid.request),
      "originalStart" -> Json.fromString(wait),
      "receipt" -> receiptJson(valid)).noSpaces

  def decodeStorageC(expectedSubmission: String, raw: String): Consequence[Record] = {
    val parsed = for {
      _ <- Either.cond(name(expectedSubmission) && raw != null && raw.startsWith(prefix), (),
        "Registration storage encoding is unsupported")
      json <- parse(raw.substring(prefix.length)).left.map(_ => "Registration JSON is invalid")
      root <- fields(json, Set("schemaVersion", "request", "originalStart", "receipt"))
      schema <- string(root, "schemaVersion")
      _ <- Either.cond(schema == schemaVersion, (), "Registration schema differs")
      request <- root("request").toRight("Registration request is missing")
      body <- fields(request, Set("registrationIdentity", "submissionIdentity", "idempotencyIdentity", "start"))
      registration <- string(body, "registrationIdentity")
      submission <- string(body, "submissionIdentity")
      idempotency <- string(body, "idempotencyIdentity")
      start <- body("start").toRight("Registration Start command is missing")
      wait <- string(root, "originalStart")
      receipt <- root("receipt").toRight("Registration receipt data is missing")
    } yield (registration, submission, idempotency, start, wait, receipt)
    parsed.fold(Consequence.stateConflict(_), { case (registration, submission, idempotency, start, wait, receipt) =>
      for {
        command <- GoalPhaseStartServiceOperation.decodeCommandC(start.noSpaces)
        projection <- WorkflowWaitJsonV1.decodeC(wait)
        record <- buildC(Request(registration, submission, idempotency, command), projection)
        _ <- if (submission == expectedSubmission && receipt == receiptJson(record)) Consequence.unit
          else Consequence.stateConflict("Registration lookup scope or receipt facts differ")
      } yield record
    })
  }

  /** Validate the whole immutable registration before projecting its handle.
    * A legacy association is not a registration and is never upgraded here.
    */
  def decodeHandleC(identity: String, record: org.goldenport.record.Record): Consequence[WorkflowHandle] =
    if (record == null) Consequence.stateConflict("Host submission registration record is missing")
    else record.getString(storageField) match {
      case None => Consequence.stateConflict("Host submission registration has no typed payload")
      case Some(raw) => decodeStorageC(identity, raw).map(_.originalStart.handle)
    }

  final class DataStorePort(storeName: String, collection: DataStore.CollectionId)
      extends GoalPhaseStepCloseServiceOperation.CallScopedInvocationPort {
    def resolveForCallC(identity: String, core: ActionCall.Core): Consequence[WorkflowHandle] =
      ManagedHostRecordRead.loadC(core, storeName, collection, identity,
        GoalPhaseHostRecordReadPolicy.Purpose.Association).flatMap {
        case None => Consequence.stateConflict("Host has no recorded submission registration")
        case Some(record) => decodeHandleC(identity, record)
      }
  }

  private def buildC(request: Request, start: WorkflowInteraction[Nothing, Nothing]): Consequence[Record] = {
    val generated = GoalPhaseWorkflowStateMachineProvidedApi1.workflow
    if (request == null || !name(request.registrationIdentity) || !name(request.submissionIdentity) ||
        !name(request.idempotencyIdentity) || request.start == null || request.start.input == null ||
        !Vector(request.start.selectionIdentity, request.start.participantIdentity, request.start.idempotencyKey,
          request.start.input.phaseIdentity, request.start.input.repositoryIdentity).forall(name) ||
        start == null || start.handle == null)
      Consequence.stateConflict("Registration facts are incomplete")
    else for {
      _ <- start.handle.validateC
      _ <- start.current match {
        case value: WorkflowContinuation.Wait if value.expectedRevision == StateMachineRevision("1") &&
            value.contextSnapshot == org.goldenport.cncf.workflow.ContextSnapshot(start.handle.workflowRevision.value) &&
            start.handle.componentIdentity == SmWorkflowComponent.componentId &&
            start.handle.workflowIdentity.value == generated.identity &&
            start.handle.workflowRevision.value == generated.version => Consequence.unit
        case _ => Consequence.stateConflict("Registration requires the original GoalPhase Start WAIT")
      }
      _ <- WorkflowWaitJsonV1.encodeC(start).map(_ => ())
    } yield new Record(request, start)
  }

  private def requestJson(request: Request): Json = Json.obj(
    "registrationIdentity" -> Json.fromString(request.registrationIdentity),
    "submissionIdentity" -> Json.fromString(request.submissionIdentity),
    "idempotencyIdentity" -> Json.fromString(request.idempotencyIdentity),
    "start" -> parse(GoalPhaseStartServiceOperation.encodeCommand(request.start)).toOption.get)

  /** Derived data only; callers must establish publication separately. */
  def receiptJson(record: Record): Json = Json.obj(
    "typeIdentity" -> Json.fromString("GoalPhaseSubmissionRegistrationReceipt"),
    "registrationIdentity" -> Json.fromString(record.request.registrationIdentity),
    "submissionIdentity" -> Json.fromString(record.request.submissionIdentity),
    "idempotencyIdentity" -> Json.fromString(record.request.idempotencyIdentity),
    "componentIdentity" -> Json.fromString(record.originalStart.handle.componentIdentity.name),
    "workflowIdentity" -> Json.fromString(record.originalStart.handle.workflowIdentity.value),
    "workflowRevision" -> Json.fromString(record.originalStart.handle.workflowRevision.value),
    "instanceIdentity" -> Json.fromString(record.originalStart.handle.instanceIdentity.value),
    "selectionIdentity" -> Json.fromString(record.request.start.selectionIdentity),
    "participantIdentity" -> Json.fromString(record.request.start.participantIdentity))

  private def name(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
  private def fields(value: Json, expected: Set[String]): Either[String, JsonObject] =
    value.asObject.filter(_.keys.toSet == expected).toRight("Registration fields differ from schema")
  private def string(value: JsonObject, key: String): Either[String, String] =
    value(key).flatMap(_.asString).filter(name).toRight("Registration string is missing or noncanonical")
}
