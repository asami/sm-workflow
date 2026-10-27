package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import java.nio.charset.StandardCharsets.UTF_8
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.resource.ResourceReference
import org.goldenport.convert.ValueReader
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.preparation.*
import org.simplemodeling.textus.smworkflow.caller.*
import org.simplemodeling.textus.smworkflow.value as V

/** Passive normal Operations borrow only the current executing call's resource context.
  * @since   Oct. 15, 2026
  * @version Oct. 27, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object NativeWorkflowPreparationServiceAssembly {
  private val _declared = SmWorkflowComponent.NativeWorkflowPreparationServiceService
  private type Reader = String => Consequence[String]

  def bind: spec.ServiceDefinition = NativeWorkflowDescriptionMetadata.service(_declared,
    spec.OperationDefinitionGroup(NonEmptyVector.of(
      new BoundOperation[V.WorkflowGoalStartPreparationInput](_declared.PrepareGoalStartOperation,
        (input, reader) => NativeGoalStartInput.prepareC(Map(
          "input" -> input.input,
          "plan" -> input.plan,
          "selection-id" -> input.selectionId,
          "participant" -> input.participant,
          "origin" -> input.origin,
          "idempotency-key" -> input.idempotencyKey,
          "selection-collection" -> input.selectionCollection,
          "plan-collection" -> input.planCollection), reader)),
      new BoundOperation[V.WorkflowStandaloneFixStartPreparationInput](_declared.PrepareStandaloneFixStartOperation,
        (input, reader) => NativeStandaloneFixStartInput.prepareC(Map(
          "input" -> input.input, "selection-id" -> input.selectionId, "participant" -> input.participant,
          "origin" -> input.origin, "idempotency-key" -> input.idempotencyKey,
          "selection-collection" -> input.selectionCollection), reader)),
      new BoundOperation[V.WorkflowStandaloneReviewFixStartPreparationInput](_declared.PrepareStandaloneReviewFixStartOperation,
        (input, reader) => NativeStandaloneReviewFixStartInput.prepareC(Map(
          "input" -> input.input, "selection-id" -> input.selectionId, "participant" -> input.participant,
          "origin" -> input.origin, "idempotency-key" -> input.idempotencyKey,
          "selection-collection" -> input.selectionCollection), reader)),
      new BoundOperation[V.WorkflowGoalTaskStartPreparationInput](_declared.PrepareGoalTaskStartOperation,
        (input, reader) => NativeGoalTaskStartInput.prepareC(Map(
          "input" -> input.input, "selection-id" -> input.selectionId, "participant" -> input.participant,
          "origin" -> input.origin, "idempotency-key" -> input.idempotencyKey,
          "selection-collection" -> input.selectionCollection), reader)),
      new BoundOperation[V.WorkflowTaskFixDeliveryPreparationInput](_declared.PrepareTaskFixDeliveryOperation,
        (input, reader) => NativeTaskFixDelivery.prepareC(input, reader).map(_.toRecord())),
      new BoundOperation[V.WorkflowTaskFixAdoptionPreparationInput](_declared.PrepareTaskFixAdoptionOperation,
        (input, reader) => NativeTaskFixDelivery.adoptC(input, reader).map(_.toRecord())),
      new BoundOperation[V.WorkflowRepositoryStartPreparationInput](_declared.PrepareRepositoryStartOperation,
        (input, reader) => NativeRepositoryStartInput.prepareC(Map(
          "input" -> input.input,
          "selection-id" -> input.selectionId,
          "participant" -> input.participant,
          "origin" -> input.origin,
          "idempotency-key" -> input.idempotencyKey,
          "selection-collection" -> input.selectionCollection), reader)),
      new BoundOperation[V.WorkflowSplitStartPreparationInput](_declared.PrepareSplitStartOperation,
        (input, reader) => NativeSplitStartInput.prepareC(Map(
          "component-instance" -> input.componentInstance,
          "allocation-reference" -> input.allocationReference,
          "allocation" -> input.allocation,
          "source" -> input.source,
          "reference" -> input.reference,
          "participant" -> input.participant,
          "issuer" -> input.issuer,
          "occurred-at" -> input.occurredAt,
          "collection" -> input.collection), reader)),
      new BoundOperation[V.WorkflowRepositoryEffectPreparationInput](_declared.PrepareRepositoryEffectOperation,
        (input, reader) => NativeRepositoryEffectInput.prepareC(Map(
          "component-instance" -> input.componentInstance,
          "selection" -> input.selection,
          "target" -> input.target,
          "effect" -> input.effect,
          "reference" -> input.reference,
          "participant" -> input.participant,
          "issuer" -> input.issuer,
          "occurred-at" -> input.occurredAt,
          "collection" -> input.collection), reader)),
      new BoundOperation[V.WorkflowAuthorityPreparationInput](_declared.PrepareAuthorityOperation,
        (input, reader) => NativeAuthorityInput.prepareC(Map(
          "request" -> input.request,
          "reference" -> input.reference,
          "participant" -> input.participant,
          "issuer" -> input.issuer,
          "occurred-at" -> input.occurredAt,
          "collection" -> input.collection), reader)),
      new BoundOperation[V.WorkflowEvidencePreparationInput](_declared.PrepareEvidenceOperation,
        (input, reader) => NativeExternalEvidenceInput.prepareC(Map(
          "family" -> input.family,
          "kind" -> input.kind,
          "request" -> input.request,
          "result" -> input.result,
          "reference" -> input.reference,
          "execution-evidence" -> input.executionEvidence,
          "issuer" -> input.issuer,
          "occurred-at" -> input.occurredAt,
          "collection" -> input.collection) ++ input.participant.map(value => "participant" -> value), reader)),
      new BoundOperation[V.WorkflowCallerExecutionPrepareInput](_declared.PrepareExecutionOperation,
        (input, reader) => NativeCallerExecutionInput.prepareC(input, reader).map(_.toRecord())),
      new BoundOperation[V.WorkflowCallerExecutionRecordInput](_declared.RecordExecutionOperation,
        (input, reader) => NativeCallerExecutionInput.recordC(input, reader).map(_.toRecord())),
      new BoundOperation[V.WorkflowCallerInformationPrepareInput](_declared.PrepareCallerInformationOperation,
        (input, _) => NativeCallerInformation.prepareC(input).map(_.toRecord())),
      new BoundOperation[V.WorkflowCallerInformationRecordInput](_declared.RecordCallerInformationOperation,
        (input, reader) => NativeCallerInformation.recordC(input, reader).map(_.toRecord()))
    )))

  private final class BoundOperation[A: ValueReader](original: spec.OperationDefinition,
    prepare: (A, Reader) => Consequence[Record]) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(original)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains(_declared.name) || request.operation != original.name)
        Consequence.stateConflict("Workflow preparation requires its exact owning service and Operation")
      else NativeWorkflowOperationInput.recordC(request, "input")
        .flatMap(summon[ValueReader[A]].readC).map(input => BoundAction(request, input, prepare))
  }

  private final case class BoundAction[A](request: Request, input: A,
    prepare: (A, Reader) => Consequence[Record]) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(core: ActionCall.Core): ActionCall = BoundCall(core, input, prepare)
  }

  private final case class BoundCall[A](core: ActionCall.Core, input: A,
    prepare: (A, Reader) => Consequence[Record]) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = for {
      prepared <- prepare(input, _read_c)
      text <- prepared.toJsonStringC
      json <- io.circe.parser.parse(text).fold(error => Consequence.stateConflict(error.message), Consequence.success)
    } yield OperationResponse.Json(json)

    private def _read_c(value: String): Consequence[String] =
      if (!NativePreparationInput.exact(value))
        Consequence.stateConflict("Workflow preparation requires an exact nonempty resource reference")
      else ResourceReference.parseC(value).flatMap {
        case reference: ResourceReference.Url if reference.scheme == "file" =>
          read_resource_text(reference, Some(UTF_8))
        case _ => Consequence.stateConflict("Workflow preparation requires an absolute local file: URI reference")
      }
  }
}
