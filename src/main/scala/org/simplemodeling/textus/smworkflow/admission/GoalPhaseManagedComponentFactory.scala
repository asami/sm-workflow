package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentCreate}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.config.ComponentInitializationParameters
import org.goldenport.cncf.event.EventRecord
import org.goldenport.cncf.workflow.{CandidateAdmissionProducerAbi, StateMachineRequiredOperation, WorkflowCompletionServiceOperation}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.{ContinuationResult, ExecutionRequirement, MinimalPresentation}
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.impl.{SmWorkflowParticipantFactoryBase, SmWorkflowPrimaryComponent}
import org.goldenport.protocol.spec.ServiceDefinition
import org.simplemodeling.textus.smworkflow.value.ReviewStepResult

/** Canonical Component construction with explicit host-owned dependencies.
  * This does not synthesize configuration or human authority for the launcher.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] final class GoalPhaseManagedComponentFactory(
  catalog: GoalPhaseRuntimeCatalog.Catalog,
  declaration: GoalPhaseManagedStartAssembly.Declaration,
  ports: GoalPhaseManagedServiceAssembly.Ports,
  repair: GoalPhaseManagedServiceAssembly.RepairPorts
)(using context: ExecutionContext) extends Component.BundleFactory {
  def primaryFactory: Component.PrimaryComponentFactory = new Primary

  override def componentletFactories: Vector[Component.ComponentletFactory] = Vector.empty

  private final class Primary extends SmWorkflowParticipantFactoryBase with Component.PrimaryComponentFactory {
    override protected def create_Component(params: ComponentCreate): Component = new SmWorkflowPrimaryComponent()

    override protected def create_Core(params: ComponentCreate, comp: Component): Component.Core =
      GoalPhaseManagedComponentFactory.bindServiceC(params, catalog, declaration, ports, repair) match {
        case Consequence.Success(service) =>
          spec_create(SmWorkflowComponent.name, SmWorkflowComponent.componentId, service)
        case Consequence.Failure(conclusion) =>
          throw conclusion.getException.getOrElse(new IllegalStateException(conclusion.display))
      }
  }
}

private[smworkflow] object GoalPhaseManagedComponentFactory {
  enum AcceptedReviewFollowup {
    case StopOnly, NativeHuman
  }
  /** Non-record application dependencies; record readers cannot be supplied
    * by a skill, conversation Map or fixture authority port on this path.
    */
  final case class HostPorts(asOf: () => Instant,
    review: StateMachineRequiredOperation,
    requirement: ExecutionRequirement,
    presentation: MinimalPresentation,
    snapshot: GoalPhaseStepClosureBoundary.SnapshotPort,
    commit: GoalPhaseStepClosureBoundary.CommitPort,
    artifact: CandidateAdmissionProducerAbi.Artifact,
    events: ((ContinuationResult[ReviewStepResult], WorkflowCompletionServiceOperation.SelectedClosing) =>
      Consequence[Vector[EventRecord]]), initialArtifactMaximumBytes: Option[Int] = None,
    closureDecisionTemplate: Option[GoalPhaseClosureDecisionTemplate] = None,
    humanDecision: Option[GoalPhaseHumanDecisionServiceOperation.Host] = None,
    nativeHumanVerifier: Option[GoalPhaseHumanInteractionVerification.Verifier] = None,
    acceptedReviewFollowup: AcceptedReviewFollowup = AcceptedReviewFollowup.StopOnly)

  def configuredHostRecordsC(catalog: GoalPhaseRuntimeCatalog.Catalog, host: HostPorts,
    repair: GoalPhaseManagedServiceAssembly.RepairPorts
  ): Consequence[GoalPhaseConfiguredComponentFactory] =
    validateHostC(catalog, host, repair).map { _ =>
      new GoalPhaseConfiguredComponentFactory(GoalPhaseRuntimeParameters.declarations,
        Vector.empty, (creation, parameters, composition) =>
          bindHostServiceC(creation, parameters, catalog, host, repair)(using composition))
    }

  private def validateHostC(catalog: GoalPhaseRuntimeCatalog.Catalog, host: HostPorts,
    repair: GoalPhaseManagedServiceAssembly.RepairPorts): Consequence[Unit] =
    if (catalog == null || host == null || host.asOf == null || host.review == null ||
        host.requirement == null || host.presentation == null || host.snapshot == null || host.commit == null ||
        host.events == null || host.artifact == null || host.artifact != catalog.producer || repair == null ||
        repair.publication == null || repair.publication.artifact != catalog.producer ||
        host.initialArtifactMaximumBytes == null || host.initialArtifactMaximumBytes.exists(value => value <= 0 || value == Int.MaxValue) ||
        host.closureDecisionTemplate == null || host.closureDecisionTemplate.exists(value => value == null || value.validateC.toOption.isEmpty) ||
        host.humanDecision == null || host.humanDecision.exists(value => value == null || value.validateC.toOption.isEmpty) ||
        host.nativeHumanVerifier == null || host.nativeHumanVerifier.exists(_ == null) ||
        host.acceptedReviewFollowup == null ||
        (host.acceptedReviewFollowup == AcceptedReviewFollowup.NativeHuman && host.closureDecisionTemplate.nonEmpty))
      Consequence.configurationInvalid("GoalPhase host-record Factory requires its catalog and application dependencies")
    else Consequence.unit

  def bindHostServiceC(creation: ComponentCreate, parameters: ComponentInitializationParameters,
    catalog: GoalPhaseRuntimeCatalog.Catalog, host: HostPorts,
    repair: GoalPhaseManagedServiceAssembly.RepairPorts
  )(using ExecutionContext): Consequence[ServiceDefinition] =
    validateHostC(catalog, host, repair).flatMap { _ =>
      for {
        declaration <- GoalPhaseRuntimeParameters.decodeC(parameters, catalog)
        nativeQuestion = Option.when(host.acceptedReviewFollowup == AcceptedReviewFollowup.NativeHuman)(declaration)
        readers <- GoalPhaseHostRecordReaders.bindC(creation.configuration, declaration)
        records <- GoalPhaseHostRecordReadPolicy.decodeC(creation.configuration)
        automaticPreparation <- host.closureDecisionTemplate match {
          case None => Consequence.success(None)
          case Some(template) => records.collections.find(_._1 == GoalPhaseHostRecordReadPolicy.Purpose.Decision)
            .map(value => Consequence.success(Some(GoalPhaseReviewDecisionPreparation.Configuration(declaration, template, value._2))))
            .getOrElse(Consequence.configurationInvalid("Closure question collection is missing"))
        }
        artifactDeclaration <- host.initialArtifactMaximumBytes match {
          case None => Consequence.success(None)
          case Some(_) => GoalPhaseArtifactReadPolicy.decodeC(creation.configuration).map(Some(_))
        }
        snapshot <- artifactDeclaration match {
          case None => Consequence.success(host.snapshot)
          case Some(artifacts) => GoalPhaseBlobArtifactSnapshot.bindC(artifacts.storeName,
            new GoalPhaseArtifactReferenceCatalog.DataStorePort(artifacts.storeName, artifacts.collection,
              host.initialArtifactMaximumBytes.get), host.initialArtifactMaximumBytes.get)
            .map(value => value: GoalPhaseStepClosureBoundary.SnapshotPort)
        }
        issue = GoalPhaseManagedIssueAssembly.Ports(host.asOf, readers.invocation, host.review,
          host.requirement, host.presentation, snapshot, host.commit, readers.authority)
        ports = GoalPhaseManagedServiceAssembly.Ports(readers.selection, issue, host.artifact, host.events,
          Some(GoalPhaseManagedReviewAssembly.AuthorityProjection(snapshot, readers.authority, automaticPreparation, nativeQuestion)))
        service <- bindServiceC(creation, catalog, declaration, ports, repair)
        collection <- records.collections.find(_._1 == GoalPhaseHostRecordReadPolicy.Purpose.Association)
          .map(value => Consequence.success(value._2)).getOrElse(
            Consequence.configurationInvalid("GoalPhase registration collection is missing"))
        registration <- GoalPhaseRegistrationServiceOperation.bindC(declaration, readers.selection, collection)
        recovery <- GoalPhaseReviewRecoveryServiceOperation.bindC(creation, declaration, readers.selection,
          host.artifact, host.asOf, snapshot, readers.authority, host.events, automaticPreparation = automaticPreparation,
          nativeQuestion = nativeQuestion)
        preparation <- host.closureDecisionTemplate match {
          case None => Consequence.success(None)
          case Some(template) => for {
            destination <- records.collections.find(_._1 == GoalPhaseHostRecordReadPolicy.Purpose.Decision)
              .map(value => Consequence.success(value._2)).getOrElse(
                Consequence.configurationInvalid("Closure question collection is missing"))
            operation <- GoalPhaseReviewRecoveryServiceOperation.bindC(creation, declaration, readers.selection,
              host.artifact, host.asOf, snapshot, readers.authority, host.events,
              Some(GoalPhaseReviewRecoveryServiceOperation.Preparation(template, destination)))
          } yield Some(operation)
        }
        artifactRegistration <- host.initialArtifactMaximumBytes match {
          case None => Consequence.success(None)
          case Some(maximumBytes) => for {
            artifacts <- artifactDeclaration.map(Consequence.success(_)).getOrElse(
              Consequence.configurationInvalid("Artifact registration requires its resolved catalog declaration"))
            bound <- GoalPhaseArtifactRegistrationServiceOperation.bindC(declaration, readers.selection,
              collection, artifacts, maximumBytes)
          } yield Some(bound)
        }
        humanOperations <- host.humanDecision match {
          case None => Consequence.success(Vector.empty)
          case Some(value) => for {
            _ <- GoalPhaseHumanQuestionStoragePolicy.decodeC(creation.configuration, declaration)
            _ <- GoalPhaseHumanAnswerStoragePolicy.decodeC(creation.configuration, declaration)
            operations <- GoalPhaseHumanDecisionServiceOperation.bindC(declaration, host.asOf, value)
          } yield operations
        }
        nativeHumanOperation <- host.nativeHumanVerifier match {
          case None => Consequence.success(None)
          case Some(verifier) => GoalPhaseManagedNativeHumanCompletion.bindC(creation, declaration,
            host.asOf, verifier).map(Some(_))
        }
        nativeHumanQuery <- GoalPhaseHumanCompletionQueryOperation.bindC(creation, declaration)
        nativeHumanAdvance <- GoalPhaseNativeHumanAdvanceOperation.bindC(creation, declaration,
          readers.selection, host.artifact, host.asOf, snapshot, readers.authority, host.events)
      } yield {
        val registered = service.operations.add(registration).add(recovery)
        val withArtifacts = artifactRegistration.fold(registered)(registered.add(_))
        val prepared = preparation.fold(withArtifacts)(withArtifacts.add(_))
        val withHuman = humanOperations.foldLeft(prepared)((current, operation) => current.add(operation))
        val operations = nativeHumanOperation.fold(withHuman)(withHuman.add(_)).add(nativeHumanQuery).add(nativeHumanAdvance)
        org.goldenport.protocol.spec.ServiceDefinition.Instance(service.specification.copy(operations = operations))
      }
    }

  /** Compose the user-selected post-resolution hook with an admitted catalog.
    * Host authority ports remain explicit dependencies, never parameter values.
    */
  def configuredC(catalog: GoalPhaseRuntimeCatalog.Catalog,
    ports: GoalPhaseManagedServiceAssembly.Ports, repair: GoalPhaseManagedServiceAssembly.RepairPorts
  ): Consequence[GoalPhaseConfiguredComponentFactory] =
    if (catalog == null || ports == null || repair == null ||
        ports.artifact == null || ports.artifact != catalog.producer ||
        repair.publication == null || repair.publication.artifact != catalog.producer)
      Consequence.configurationInvalid("GoalPhase configured Factory requires its admitted catalog and host ports")
    else Consequence.success(new GoalPhaseConfiguredComponentFactory(
      GoalPhaseRuntimeParameters.declarations, Vector.empty, (creation, parameters, composition) => {
        given ExecutionContext = composition
        GoalPhaseRuntimeParameters.decodeC(parameters, catalog).flatMap { declaration =>
          bindServiceC(creation, catalog, declaration, ports, repair)
        }}))

  def bindServiceC(params: ComponentCreate, catalog: GoalPhaseRuntimeCatalog.Catalog,
    declaration: GoalPhaseManagedStartAssembly.Declaration,
    ports: GoalPhaseManagedServiceAssembly.Ports, repair: GoalPhaseManagedServiceAssembly.RepairPorts
  )(using ExecutionContext): Consequence[ServiceDefinition] =
    for {
      _ <- GoalPhaseRuntimeCatalog.admitBindingC(catalog, declaration, Option(ports).map(_.artifact).orNull)
      service <- GoalPhaseManagedServiceAssembly.bindRepairSliceC(params, declaration, ports, repair)
    } yield service
}
