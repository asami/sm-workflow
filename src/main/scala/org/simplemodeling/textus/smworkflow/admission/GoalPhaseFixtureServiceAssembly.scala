package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant

import cats.data.NonEmptyVector
import domain.statemachine.providedapi.GoalPhaseWorkflowStateMachineProvidedApi1
import org.goldenport.Consequence
import org.goldenport.cncf.unitofwork.UnitOfWork
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{Configuration, DefinitionBinding}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.{ExecutionRequirement, MinimalPresentation}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.value.StepCloseRequest

/** Explicit fixture-only composition. Start and all completion Operations use
  * the same host stores and ports. The host, not the public request, associates
  * a Step-close submission with a Handle returned by Start. This does not
  * register a production runtime or provide shared transaction semantics.
  * @since   Sep. 28, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseFixtureServiceAssembly {
  final case class Ports(
    selection: ProfileStartAdmission.SelectionPort,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence,
    invocation: GoalPhaseStepCloseServiceOperation.InvocationPort,
    artifact: CandidateAdmissionProducerAbi.Artifact,
    asOf: () => Instant,
    runtime: ContinuationRuntime,
    issued: IssuedWorkOrderPersistence[StepCloseRequest],
    freshUnitOfWork: () => UnitOfWork,
    snapshot: GoalPhaseStepClosureBoundary.SnapshotPort,
    commit: GoalPhaseStepClosureBoundary.CommitPort,
    intents: GoalPhaseRepairLoop.IntentPort,
    review: StateMachineRequiredOperation,
    repair: StateMachineRequiredOperation,
    reviewRequirement: ExecutionRequirement,
    repairRequirement: ExecutionRequirement,
    closureAuthority: Option[GoalPhaseReviewedClosure.Ports] = None
  )

  def bindC(ports: Ports): Consequence[spec.ServiceDefinition] = {
    val generated = GoalPhaseWorkflowStateMachineProvidedApi1.workflow
    // The current declaration includes Human and ordered-Phase operations. This review/repair
    // fixture deliberately advertises only its four actually bound Operations.
    val expected = Vector("startGoalPhase", "requestStepClose", "submitStepReview", "submitStepRepair",
      "submitGoalPhaseHumanDecision", "advanceGoalPhase", "submitGoalPhaseWorkResult", "submitGoalPhaseOrderedDecision")
    if (ports == null || generated.identity != "GoalPhaseWorkflow" ||
        generated.providedOperations.map(_.service).distinct != Vector("GoalPhaseService") ||
        generated.providedOperations.map(_.name) != expected || ports.closureAuthority == null ||
        ports.closureAuthority.exists(value => value == null || value.validateC.toOption.isEmpty))
      Consequence.configurationInvalid("GoalPhase fixture Service differs from generated ABI")
    else for {
      start <- GoalPhaseStartServiceOperation.bindC(ports.selection, ports.definition,
        ports.configuration, ports.instances)
      loop <- GoalPhaseRepairLoop.bindC(ports.artifact, ports.asOf, ports.runtime,
        ports.issued, ports.instances, ports.configuration, ports.freshUnitOfWork,
        ports.snapshot, ports.intents, ports.review, ports.repair,
        ports.reviewRequirement, ports.repairRequirement)
      close <- GoalPhaseStepCloseServiceOperation.bindC(ports.asOf, ports.invocation,
        ports.review, ports.instances, ports.configuration, ports.runtime, ports.issued,
        ports.reviewRequirement, MinimalPresentation("Review Step", "Full-Step review"),
        ports.snapshot, ports.commit, ports.freshUnitOfWork,
        automaticAuthority = ports.closureAuthority.map(_.authority))
      review <- GoalPhaseReviewCompletionOperation.bindRepairLoopC(ports.artifact,
        ports.asOf, ports.runtime, ports.issued, ports.instances, ports.configuration,
        ports.freshUnitOfWork,
        GoalPhaseReviewCompletionOperation.AutoClosePorts(ports.snapshot, ports.commit,
          ports.closureAuthority), loop)
      repair <- GoalPhaseRepairCompletionOperation.bindC(loop, ports.runtime,
        ports.issued, ports.instances, ports.configuration, ports.freshUnitOfWork)
    } yield spec.ServiceDefinition("GoalPhaseService",
      spec.OperationDefinitionGroup(NonEmptyVector.of(start, close, review, repair)))
  }
}
