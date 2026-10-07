package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Path

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.simplemodeling.textus.smworkflow.value.SplitPhaseStartCommand

/** Host-only APPLY document boundary. It verifies a distinct APPLY Start,
  * a proposal-assessed instance, and current host-owned admission before
  * producing a write set. This does not complete the Workflow instance or
  * expose a public APPLY Operation.
  * @since   Sep. 27, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseApplyDocuments {
  final case class Command(startCommand: SplitPhaseStartCommand,
                           expectedRevision: String, idempotencyKey: String)

  final class Prepared private[SplitPhaseApplyDocuments] (
      val repositoryRoot: Path,
      val instanceIdentity: InstanceIdentity,
      val startCommand: SplitPhaseStartCommand,
      val plan: SplitPhaseDocumentProjection.Plan)

  def prepareC(command: Command,
               selectionPort: ProfileStartAdmission.SelectionPort,
               entryPort: SplitPhaseEntryAssessment.Port,
               proposalPort: SplitPhaseProposalAssessment.Port,
               catalogPort: SplitPhasePreviewExecution.CatalogPort,
               definition: DefinitionBinding,
               configuration: Configuration,
               instances: WorkflowInstancePersistence): Consequence[Prepared] =
    if (command == null || command.startCommand == null ||
        command.startCommand.input == null ||
        command.startCommand.input.invocationMode != "APPLY" ||
        command.expectedRevision != "2" || command.idempotencyKey == null ||
        command.idempotencyKey.trim.isEmpty || proposalPort == null ||
        catalogPort == null || instances == null)
      Consequence.stateConflict("SplitPhase document APPLY command is incomplete or not APPLY")
    else for {
      prepared <- SplitPhaseStartExecution.prepareC(command.startCommand,
        selectionPort, entryPort, definition, configuration)
      loaded <- instances.load(configuration, prepared.initial.identity)
      current <- loaded match {
        case Some(record) => SplitPhasePreviewExecution.readyC(record, prepared.initial)
        case None => Consequence.stateConflict("SplitPhase APPLY Start instance is missing")
      }
      catalog <- catalogPort.loadC(command.startCommand)
      _ <- if (catalog != null && catalog.repositoryRoot != null &&
          catalog.snapshotIdentity != null && catalog.snapshotIdentity.trim.nonEmpty &&
          catalog.enumerated != null && catalog.policy != null &&
          catalog.evidences != null && catalog.admitted != null) Consequence.unit
        else Consequence.stateConflict("SplitPhase APPLY catalog is incomplete")
      plan <- SplitPhaseDocumentProjection.projectC(catalog, prepared.assessment,
        proposalPort)
      port = SplitPhaseDocumentFilePort(catalog.repositoryRoot)
      classified <- SplitPhaseDocumentConflict.classifyC(plan, port)
      _ <- classified match {
        case _: SplitPhaseDocumentConflict.Outcome.Ready => Consequence.unit
        case _ => Consequence.stateConflict("SplitPhase APPLY documents are not at the expected base")
      }
    } yield new Prepared(catalog.repositoryRoot.toAbsolutePath.normalize,
      current.identity, command.startCommand, plan)

  /** Executes only the document portion; caller must not report Workflow
    * completion or expose this as a public APPLY terminal.
    */
  def applyDocumentsC(prepared: Prepared): Consequence[SplitPhaseDocumentCas.Outcome] =
    if (prepared == null || prepared.repositoryRoot == null ||
        prepared.instanceIdentity == null || prepared.startCommand == null ||
        prepared.plan == null)
      Consequence.stateConflict("SplitPhase APPLY document preparation is missing")
    else SplitPhaseApplyPlanStore.saveC(prepared.repositoryRoot,
      prepared.instanceIdentity, prepared.startCommand, prepared.plan).flatMap(record =>
      SplitPhaseDocumentCas.executeC(record.plan,
        SplitPhaseDocumentFilePort(prepared.repositoryRoot)))

  /** Replays the host's immutable plan after the source has already changed.
    * A future Workflow executor must verify its instance history before use.
    */
  def resumeDocumentsC(repositoryRoot: Path, instanceIdentity: InstanceIdentity,
                       startCommand: SplitPhaseStartCommand):
      Consequence[SplitPhaseDocumentCas.Outcome] =
    SplitPhaseApplyPlanStore.loadC(repositoryRoot, instanceIdentity,
      startCommand).flatMap {
      case Some(record) => SplitPhaseDocumentCas.executeC(record.plan,
        SplitPhaseDocumentFilePort(repositoryRoot))
      case None => Consequence.stateConflict("SplitPhase APPLY plan is not persisted")
    }
}
