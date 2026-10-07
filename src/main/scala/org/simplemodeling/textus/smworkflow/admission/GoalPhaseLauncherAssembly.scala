package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.config.{ComponentInitializationParameters, ComponentParameterDecoder, ComponentParameterKey}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.spec.{OperationDefinitionGroup, ServiceDefinition}

/** Standard bootstrap reuses the managed service and its owning Subsystem.
  * Configuration selects execution policy, never actor/human/command authority.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseLauncherAssembly {
  val prefix = "sm-workflow.goal-phase.execution."
  private val _exact_string = ComponentParameterDecoder[String] {
    case ConfigurationValue.StringValue(value) if value != null && value.nonEmpty && value.trim == value =>
      Consequence.success(value)
    case _ => Consequence.configurationInvalid("GoalPhase execution policy requires an exact string")
  }
  private def _text_key(name: String) = ComponentParameterKey.required(prefix + name, _exact_string)
  private def _reasoning_key(name: String) = ComponentParameterKey.required(prefix + name,
    ComponentParameterDecoder[ReasoningLevel](value => _exact_string.decode(value).flatMap { text =>
      ReasoningLevel.values.find(_.value == text).map(Consequence.success(_)).getOrElse(
        Consequence.configurationInvalid("GoalPhase execution reasoning level is not declared"))
    }))
  val reviewRisk = _text_key("review-risk")
  val reviewReasoning = _reasoning_key("review-reasoning")
  val repairRisk = _text_key("repair-risk")
  val repairReasoning = _reasoning_key("repair-reasoning")
  val maximumRepairs = ComponentParameterKey.requiredInt(prefix + "maximum-repairs")
  val maximumArtifactBytes = ComponentParameterKey.requiredInt(prefix + "maximum-artifact-bytes")
  val declarations: Vector[ComponentParameterKey[?]] = Vector(reviewRisk, reviewReasoning,
    repairRisk, repairReasoning, maximumRepairs, maximumArtifactBytes)
  final case class Policy(review: ExecutionRequirement, repair: ExecutionRequirement,
    maximumRepairs: Int, maximumArtifactBytes: Int)

  def policyC(parameters: ComponentInitializationParameters): Consequence[Policy] =
    if (parameters == null) Consequence.configurationInvalid("GoalPhase execution policy is missing")
    else for {
      reviewriskvalue <- _required_c(parameters, reviewRisk)
      reviewlevel <- _required_c(parameters, reviewReasoning)
      repairriskvalue <- _required_c(parameters, repairRisk)
      repairlevel <- _required_c(parameters, repairReasoning)
      repairs <- _required_c(parameters, maximumRepairs)
      bytes <- _required_c(parameters, maximumArtifactBytes)
      _ <- if (repairs > 0 && repairs < Int.MaxValue && bytes > 0 && bytes < Int.MaxValue) Consequence.unit
        else Consequence.configurationInvalid("GoalPhase execution limits must be positive and bounded")
      review <- ExecutionRequirement(Vector(CapabilityRequirement("review-step-capability")),
        RiskLevel(reviewriskvalue), reviewlevel, true).validateC
      repair <- ExecutionRequirement(Vector(CapabilityRequirement("repair-step-capability")),
        RiskLevel(repairriskvalue), repairlevel, false).validateC
    } yield Policy(review, repair, repairs, bytes)

  private def _required_c[A](parameters: ComponentInitializationParameters, key: ComponentParameterKey[A]): Consequence[A] =
    parameters.resolve(key).flatMap(_.value.map(Consequence.success(_)).getOrElse(
      Consequence.configurationInvalid("GoalPhase execution policy is incomplete")))

  private[admission] val unavailableCommit: GoalPhaseStepClosureBoundary.CommitPort =
    new GoalPhaseStepClosureBoundary.CommitPort {
      def commitC(request: GoalPhaseStepClosureBoundary.CommitRequest,
        snapshot: GoalPhaseStepClosureBoundary.ArtifactSnapshot) =
        Consequence.stateConflict("GoalPhase managed Git authority and commit adapter are not configured")
    }
  private val _unavailable_snapshot = new GoalPhaseStepClosureBoundary.SnapshotPort {
    def loadC(stepIdentity: String) =
      Consequence.stateConflict("GoalPhase requires its current-call managed Blob snapshot")
  }

  private def _required_operation_c(catalog: GoalPhaseRuntimeCatalog.Catalog, action: String):
      Consequence[StateMachineRequiredOperation] = {
    val selected = catalog.producer.models.filter(_.model.workflow.exists(value =>
      value.identity.value == catalog.definition.workflowIdentity.value &&
        value.version.value == catalog.definition.workflowRevision.value))
    val spi = selected.flatMap(_.requiredSpi).filter(_.actionIdentity.value == action)
    val judgments = selected.flatMap(_.judgments).filter(_.identity.value == "ReviewStep")
    if (spi.size != 1 || judgments.size != 1)
      Consequence.configurationInvalid("GoalPhase launcher requires its exact compiled Required SPI")
    else {
      val operation = spi.head
      val judgment = judgments.head
      // Descriptive contracts only. Actual evidence/authority admission remains
      // the existing typed review/result/current-call boundary.
      val metadata = StateMachineRequiredOperationMetadata(
        ContextContract(judgment.context.value, Vector.empty, Vector.empty),
        CompletionContract(operation.operation.resultType.map(_.value).getOrElse(action), Vector.empty),
        EvidenceContract(judgment.evidence.value, Vector.empty), Vector.empty)
      Consequence.success(StateMachineRequiredOperation(StateMachineRequiredOperationIdentity(operation.identity.value),
        operation.actionIdentity.value, StateMachineOperationIdentity(operation.operation.service.value,
          operation.operation.name.value), operation.operation.inputType.map(value => StateMachineInputTypeReference(value.value)),
        operation.operation.resultType.map(value => StateMachineResultTypeReference(value.value)), metadata))
    }
  }

  private val _published_mcp_operations = Set("startGoalPhase", "requestStepClose", "submitStepReview",
    "submitStepRepair", "registerGoalPhaseSubmission", "recoverGoalPhaseReview", "registerGoalPhaseArtifacts",
    "getGoalPhaseHumanDecisionResult", "advanceGoalPhaseHumanDecision").map("GoalPhaseService." + _)

  /** Initialization validates declarations only. Execution rebuilds callbacks
    * with the incoming capsule; no initialization clock reaches semantic work.
    */
  private[admission] def _bind_for_calls_c(composition: ExecutionContext,
    bind: ExecutionContext => Consequence[ServiceDefinition]): Consequence[ServiceDefinition] =
    if (composition == null || bind == null)
      Consequence.configurationInvalid("GoalPhase call binding requires its initialization context")
    else bind(composition).flatMap { declared =>
      if (declared == null)
        Consequence.configurationInvalid("GoalPhase call binding requires its declared service")
      else {
        val operations = declared.operations.operations.map { operation =>
          CallScopedOperationBinding.wrap(operation, core => bind(core.executionContext).flatMap { current =>
            if (current == null || current.name != declared.name)
              Consequence.stateConflict("GoalPhase current-call service differs from its declaration")
            else {
              val selected = current.operations.operations.toVector.filter(_.name == operation.name)
              if (selected.size == 1 && selected.head.specification == operation.specification)
                Consequence.success(selected.head)
              else Consequence.stateConflict("GoalPhase current-call operation differs from its declaration")
            }
          })
        }
        Consequence.success(ServiceDefinition.Instance(declared.specification.copy(
          operations = OperationDefinitionGroup(operations))))
      }
    }

  private def _bind_service_c(creation: ComponentCreate, parameters: ComponentInitializationParameters,
    catalog: GoalPhaseRuntimeCatalog.Catalog, policy: Policy,
    context: ExecutionContext): Consequence[ServiceDefinition] =
    if (context == null || context.clock == null)
      Consequence.stateConflict("GoalPhase service requires its owning execution clock")
    else {
      given ExecutionContext = context
      for {
        review <- _required_operation_c(catalog, "ReviewStep")
        repair <- _required_operation_c(catalog, "RepairStep")
        clock = () => context.clock.instant()
        host = GoalPhaseManagedComponentFactory.HostPorts(clock, review, policy.review,
          MinimalPresentation("Review Step", "Awaiting full Step review"), _unavailable_snapshot,
          unavailableCommit, catalog.producer, (_, _) => Consequence.success(Vector.empty),
          initialArtifactMaximumBytes = Some(policy.maximumArtifactBytes),
          acceptedReviewFollowup = GoalPhaseManagedComponentFactory.AcceptedReviewFollowup.NativeHuman)
        repairs = GoalPhaseManagedServiceAssembly.RepairPorts(GoalPhaseManagedRepairPublication.Ports(
          catalog.producer, clock, repair, policy.repair, policy.maximumRepairs),
          (_, _) => Consequence.success(Vector.empty))
        service <- GoalPhaseManagedComponentFactory.bindHostServiceC(creation, parameters, catalog, host, repairs)
      } yield service
    }

  val factory: GoalPhaseConfiguredComponentFactory = new GoalPhaseConfiguredComponentFactory(
    GoalPhaseRuntimeParameters.declarations ++ declarations, Vector.empty, (creation, parameters, composition) =>
      for {
        catalog <- GoalPhaseRuntimeCatalog.loadC()
        policy <- policyC(parameters)
        service <- _bind_for_calls_c(composition, context =>
          _bind_service_c(creation, parameters, catalog, policy, context))
      } yield service, _published_mcp_operations)
}
