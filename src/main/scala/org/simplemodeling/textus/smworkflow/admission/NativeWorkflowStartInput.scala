package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.StateMachineOperationIdentity
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.DefinitionBinding
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationRequest
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.value.{GoalPhaseStartInput, SplitPhaseStartInput, RepositorySyncStartInput}

/** Business inputs and current selection lookup for the normal Start route.
  * No command echo, transport reconstruction or independent admission stamp.
  * @since   Oct. 11, 2026
  * @version Oct. 12, 2026
  */
private[smworkflow] object NativeWorkflowStartInput {
  enum Kind(val operation: StateMachineOperationIdentity, val inputType: String) {
    case GoalPhase extends Kind(StateMachineOperationIdentity("GoalPhaseService", "startGoalPhase"), "GoalPhaseStartInput")
    case SplitPhase extends Kind(StateMachineOperationIdentity("SplitPhaseService", "startSplitPhase"), "SplitPhaseStartInput")
    case RepositorySync extends Kind(StateMachineOperationIdentity("RepositorySyncService", "startRepositorySync"), "RepositorySyncStartInput")
  }

  sealed trait BusinessInput {
    def kind: Kind
  }
  final case class Goal(input: GoalPhaseStartInput) extends BusinessInput {
    val kind = Kind.GoalPhase
  }
  final case class Split(sourcePhaseIdentity: String, invocationMode: String,
    proposalReference: Option[String]) extends BusinessInput {
    val kind = Kind.SplitPhase
  }
  final case class Repository(repositoryIdentity: String, localBranch: String,
    trackingRemote: String, trackingBranch: String, remoteEndpointIdentity: String,
    syncMode: String) extends BusinessInput {
    val kind = Kind.RepositorySync
  }

  /** Package-private action construction, not an alternative public decoder. */
  trait Receiver extends spec.OperationDefinition {
    def createStartOperationRequestC(request: Request, input: BusinessInput,
      selection: ProfileStartAdmission.Selection): Consequence[OperationRequest]
  }

  def fromRecordC(kind: Kind, record: Record): Consequence[BusinessInput] = kind match {
    case Kind.GoalPhase => GoalPhaseStartInput.createC(record).map(Goal(_))
    case Kind.SplitPhase => SplitPhaseStartInput.createC(record).map(value =>
      Split(value.sourcePhaseIdentity, value.invocationMode, value.proposalReference))
    case Kind.RepositorySync => RepositorySyncStartInput.createC(record).map(value =>
      Repository(value.repositoryIdentity, value.localBranch, value.trackingRemote,
        value.trackingBranch, value.remoteEndpointIdentity, value.syncMode))
  }

  def decodeC(kind: Kind, json: Json): Consequence[BusinessInput] = {
    val decoded: Either[String, BusinessInput] = kind match {
      case Kind.GoalPhase => for {
        phase <- text(json, "phaseIdentity")
        repository <- text(json, "repositoryIdentity")
      } yield Goal(GoalPhaseStartInput(phase, repository))
      case Kind.SplitPhase => for {
        phase <- text(json, "sourcePhaseIdentity")
        mode <- text(json, "invocationMode")
        proposal <- optionalText(json, "proposalReference")
      } yield Split(phase, mode, proposal)
      case Kind.RepositorySync => for {
        repository <- text(json, "repositoryIdentity")
        branch <- text(json, "localBranch")
        remote <- text(json, "trackingRemote")
        tracking <- text(json, "trackingBranch")
        endpoint <- text(json, "remoteEndpointIdentity")
        mode <- text(json, "syncMode")
      } yield Repository(repository, branch, remote, tracking, endpoint, mode)
    }
    decoded.fold(Consequence.stateConflict(_), Consequence.success)
  }

  /** Consume the one independently stored business entry at this boundary.
    * Its actual target/type/participant are authority data, not a candidate
    * search or a whole JSON unchanged-input comparison.
    */
  def admitSelectedC(record: ProfileStartAdmission.Selection, input: BusinessInput,
    participant: String, definition: DefinitionBinding): Consequence[Unit] =
    if (record.participantIdentity == participant && record.startOperation == input.kind.operation &&
        record.workflowIdentity == definition.workflowIdentity && record.workflowRevision == definition.workflowRevision &&
        record.origin == ProfileStartAdmission.SelectionOrigin.ExplicitHuman &&
        record.selectedInput.exists(_.typeIdentity == input.kind.inputType))
      record.selectedInput.map(value => decodeC(input.kind, value.value)).get.flatMap { selected =>
        if (selected == input) Consequence.unit
        else Consequence.stateConflict("Host Start entry belongs to another business target")
      }
    else Consequence.stateConflict("Host Start entry does not authorize this workflow participant/input")

  private def text(json: Json, field: String): Either[String, String] =
    json.hcursor.get[String](field).left.map(_ => "Start requires string " + field)
      .flatMap(value => Either.cond(value.trim.nonEmpty, value, "Start requires nonempty " + field))
  private def optionalText(json: Json, field: String): Either[String, Option[String]] =
    json.hcursor.get[Option[String]](field).left.map(_ => "Start " + field + " must be a string when supplied")
      .flatMap(value => Either.cond(value.forall(_.trim.nonEmpty), value, "Start " + field + " must be nonempty when supplied"))
}
