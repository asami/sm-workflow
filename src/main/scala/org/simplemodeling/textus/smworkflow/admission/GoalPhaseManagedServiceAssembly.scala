package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import domain.statemachine.providedapi.GoalPhaseWorkflowStateMachineProvidedApi1
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.event.EventRecord
import org.goldenport.cncf.workflow.{CandidateAdmissionProducerAbi, WorkflowCompletionServiceOperation}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.ContinuationResult
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{RepairStepResult, ReviewStepResult}

/** Main-code Service composition. The review slice does not advertise repair
  * until its managed runtime is connected; it is not full service acceptance.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseManagedServiceAssembly {
  final case class Ports(
    selection: ProfileStartAdmission.SelectionPort,
    issue: GoalPhaseManagedIssueAssembly.Ports,
    artifact: CandidateAdmissionProducerAbi.Artifact,
    events: ((ContinuationResult[ReviewStepResult], WorkflowCompletionServiceOperation.SelectedClosing) =>
      Consequence[Vector[EventRecord]]),
    authorityProjection: Option[GoalPhaseManagedReviewAssembly.AuthorityProjection] = None
  )

  final case class RepairPorts(
    publication: GoalPhaseManagedRepairPublication.Ports,
    events: (ContinuationResult[RepairStepResult], WorkflowCompletionServiceOperation.SelectedClosing) =>
      Consequence[Vector[EventRecord]]
  )

  def bindReviewSliceC(params: ComponentCreate, declaration: GoalPhaseManagedStartAssembly.Declaration,
    ports: Ports
  )(using context: ExecutionContext): Consequence[spec.ServiceDefinition] =
    _bindC(params, declaration, ports, None)

  def bindRepairSliceC(params: ComponentCreate, declaration: GoalPhaseManagedStartAssembly.Declaration,
    ports: Ports, repair: RepairPorts
  )(using context: ExecutionContext): Consequence[spec.ServiceDefinition] =
    if (repair == null || repair.publication == null || repair.events == null || ports == null ||
        repair.publication.artifact != ports.artifact)
      Consequence.configurationInvalid("GoalPhase managed repair Service ports or producer differ")
    else _bindC(params, declaration, ports, Some(repair))

  private def _bindC(params: ComponentCreate, declaration: GoalPhaseManagedStartAssembly.Declaration,
    ports: Ports, repair: Option[RepairPorts]
  )(using context: ExecutionContext): Consequence[spec.ServiceDefinition] = {
    val generated = GoalPhaseWorkflowStateMachineProvidedApi1.workflow
    val names = Vector("startGoalPhase", "requestStepClose", "submitStepReview") ++
      repair.toVector.map(_ => "submitStepRepair")
    if (params == null || declaration == null || ports == null || context == null ||
        ports.selection == null || ports.artifact == null || ports.events == null || ports.issue == null ||
        ports.authorityProjection == null || ports.authorityProjection.exists(value => value == null ||
          value.snapshot == null || value.authority == null || value.authority.validateC.toOption.isEmpty) ||
        ports.issue.asOf == null || ports.issue.invocation == null || ports.issue.review == null ||
        ports.issue.requirement == null || ports.issue.presentation == null ||
        ports.issue.snapshot == null || ports.issue.commit == null || ports.issue.authority == null ||
        generated.identity != "GoalPhaseWorkflow" || names.exists(name =>
          generated.providedOperations.count(operation => operation.service == "GoalPhaseService" &&
            operation.name == name) != 1))
      Consequence.configurationInvalid("GoalPhase managed review Service composition is incomplete")
    else for {
      _ <- ports.issue.authority.validateC
      _ <- ports.issue.requirement.validateC
      _ <- ports.issue.presentation.validateC
      _ <- CandidateAdmissionProducerAbi.admitC(ports.artifact)
      _ <- GoalPhaseManagedRepairPublication.admitReviewC(declaration, ports.artifact,
        ports.issue.review, ports.issue.requirement)
      start <- GoalPhaseManagedStartAssembly.bindC(params, declaration, ports.selection)
      issue <- GoalPhaseManagedIssueAssembly.bindC(params, declaration, ports.issue)
      publication <- repair match {
        case None => Consequence.success(None)
        case Some(value) => GoalPhaseManagedRepairPublication.bindC(params, declaration, value.publication).map(Some(_))
      }
      review <- GoalPhaseManagedReviewAssembly.bindC(params,
        GoalPhaseManagedReviewAssembly.Declaration(declaration.configuration, declaration.definition,
          declaration.instances, declaration.continuations),
        GoalPhaseManagedReviewAssembly.Ports(ports.artifact, ports.issue.asOf, ports.events, publication,
          ports.authorityProjection))
      repairOperation <- repair match {
        case None => Consequence.success(Vector.empty[spec.OperationDefinition])
        case Some(value) => GoalPhaseManagedRepairCompletionAssembly.bindC(params, declaration,
          value.publication, value.events, Some(GoalPhaseRepairCompletionOperation.ReReviewPorts(
            ports.issue.review, ports.issue.requirement, ports.issue.presentation, ports.issue.snapshot))).map(Vector(_))
      }
      _ <- if ((Vector(start, issue, review) ++ repairOperation).map(_.specification.name) == names) Consequence.unit
        else Consequence.configurationInvalid("GoalPhase managed review Service differs from declared Operation order")
    } yield NativeWorkflowDescriptionMetadata.service(SmWorkflowComponent.GoalPhaseServiceService,
      spec.OperationDefinitionGroup(NonEmptyVector(start, Vector(issue, review) ++ repairOperation)))
  }
}
