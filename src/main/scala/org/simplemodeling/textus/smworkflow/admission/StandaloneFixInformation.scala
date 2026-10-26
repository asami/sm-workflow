package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workspace.{FileTarget, WorkspaceId, WorkspaceFilePath}
import org.simplemodeling.textus.smworkflow.value as V
import IncrementalSbtDiagnosisInformation as I
import IncrementalSbtDiagnosisInformation.given
import StandaloneFixNativeRun as N

/** Common batched NeedInformation delivery for an actual standalone repair.
  * Delivery neither completes WorkOrder nor consumes a repair batch.
  * @since   Oct. 26, 2026
  * @version Oct. 26, 2026
  */
private[smworkflow] object StandaloneFixInformation {
  def resolveC(h: N.Host, definition: WorkflowInstancePersistence.DefinitionBinding, core: ActionCall.Core,
    input: V.GoalPhaseDiagnosticInformationInput): Consequence[V.GoalPhaseDiagnosticInformationResult] = for {
    _ <- I.validateC(input)
    store <- N.storageC(h, definition, core)
    issued <- store.bindIssuedC(N.completionOperation, N.inputCodec)
    actual <- issued.loadC(ContinuationIdentity(input.issuedWorkContext.continuationId))(using core.executionContext)
      .flatMap(_.map(Consequence.success).getOrElse(N.invalid("Information requires actual issued standalone work")))
    work <- NativeWorkflowOperationOutput.workInputC(actual)
    _ <- GoalPhaseParticipantAdmission.admitC(work.state.participant, core.executionContext)
    _ <- if (work.kind == "Repair" && work.session == input.issuedWorkContext.runId) Consequence.unit
      else N.invalid("Information selection is not the current standalone repair")
    instances <- store.bindInstancesC()(using core.executionContext)
    current <- instances.loadC(WorkflowInstancePersistence.InstanceIdentity(work.session))(using core.executionContext)
      .flatMap(_.map(Consequence.success).getOrElse(N.invalid("Owning standalone instance is absent")))
    _ <- if (current.suspension.exists(_.continuationIdentity.value == input.issuedWorkContext.continuationId)) Consequence.unit
      else N.invalid("Information cannot refresh closed or superseded work")
    context <- NativeWorkflowIssuedWorkContext.fromIssuedC(actual, NativeWorkflowIssuedWorkContext.Mode.Implementation, N.inputCodec)
    _ <- I.sameWorkC(context, input.issuedWorkContext)
    source = CommonEvidence.Source(ContextReference(context.continuationId, context.expectedRevision),
      h.repository, "standalone-fix", None, work.session, "diagnostic-information:" + input.requestIdentity, "failed")
    history <- DevelopmentJournalEvidence.producerPageC(h.journal, core, h.repository, work.session, source.reference, input.historyAfter)
    delivery <- if (input.selections.isEmpty || h.evidence.isEmpty) Consequence.success(None)
      else {
        val candidate = work.state.candidate.map(StandaloneFixExecution.snapshot)
        val failure = candidate.map(c => I.FailureContext(work.sourceRevision, c, work.state.selected, work.state.receipts))
        val selected = I.WorkContext(work.state.input.ownedPaths, failure, candidate)
        I.deliverC(CommonEvidence.storage(core, h.evidence.get), h.evidence.get, source, selected, input, new I.SourceReader {
          def readC(path: String) = for {
            workspace <- WorkspaceId.parseC("sm-workflow-repository")
            file <- WorkspaceFilePath.parseC(path)
            observation <- WorkflowFileExecution.readTextC(core, FileTarget(workspace), file)
          } yield observation.text
        }, core.executionContext.clock, (a, o) => DevelopmentJournalEvidence.recordC(h.journal, core, a, o)).map(Some(_))
      }
    result <- {
      val known = delivery.flatMap(_._2.evidence).exists(input.knownEvidence.contains)
      val items = delivery.toVector.flatMap(_._1.items).map { item =>
        val json = item.asJson.deepMerge(Json.obj("candidateApplicability" -> Json.fromString(
          if (item.selection.kind == "source-file") "unverified" else "retained-check"),
          "delivery" -> Json.fromString(if (known) "reference-already-known" else "payload")))
        N.recordC(if (known) json.mapObject(_.remove("text")) else json)
      }
      items.foldLeft(Consequence.success(Vector.empty[org.goldenport.record.Record]))((acc, item) => for {
        prior <- acc
        value <- item
      } yield prior :+ value).map(records => V.GoalPhaseDiagnosticInformationResult(context, input.requestIdentity,
        if (input.selections.isEmpty) "history" else if (delivery.isEmpty) "unavailable" else "delivered",
        "Information only; standalone checks, WorkOrder and repair counts are unchanged", records,
        delivery.map(_._2), history.entries.map(e => GoalPhaseNativeMetadata.record(Json.obj(
          "evidence" -> Json.obj("identity" -> Json.fromString(e.metadata.evidence.identity), "revision" -> Json.fromString("1")),
          "storageStatus" -> Json.fromString(e.observation.status)))), history.status, history.hasMore, history.next))
    }
  } yield result
}
