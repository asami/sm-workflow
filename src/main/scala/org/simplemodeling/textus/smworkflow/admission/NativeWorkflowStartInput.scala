package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.StateMachineOperationIdentity
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.DefinitionBinding
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationRequest
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.value.GoalPhaseStartInput

/** Business inputs and current selection lookup for the normal Start route.
  * No command echo, transport reconstruction or independent admission stamp.
  * @since   Oct. 11, 2026
  * @version Oct. 11, 2026
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

  /** Match actual requested business targets, not whole saved JSON or copies.
    * The consuming Start still owns authentication, consent and effect scope.
    */
  def selectC(records: Vector[ProfileStartAdmission.Selection], input: BusinessInput,
    participant: String, definition: DefinitionBinding, selector: Option[String]):
    Consequence[ProfileStartAdmission.Selection] = {
    val candidates = records.filter { record =>
      record.participantIdentity == participant && record.startOperation == input.kind.operation &&
        record.workflowIdentity == definition.workflowIdentity &&
        record.origin == ProfileStartAdmission.SelectionOrigin.ExplicitHuman &&
        record.selectedInput.exists(value => value.typeIdentity == input.kind.inputType &&
          decodeC(input.kind, value.value).toOption.contains(input))
    }
    NativeWorkflowSessionInput.selectC(candidates, selector, _.selectionId, "Start selection")
  }

  private def text(json: Json, field: String): Either[String, String] =
    json.hcursor.get[String](field).left.map(_ => "Start requires string " + field)
      .flatMap(value => Either.cond(value.trim.nonEmpty, value, "Start requires nonempty " + field))
  private def optionalText(json: Json, field: String): Either[String, Option[String]] =
    json.hcursor.get[Option[String]](field).left.map(_ => "Start " + field + " must be a string when supplied")
      .flatMap(value => Either.cond(value.forall(_.trim.nonEmpty), value, "Start " + field + " must be nonempty when supplied"))
}
