package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Encoder, Decoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeRun as F

/** Assigned semantic payload only. Native receipts and permissions are never
  * decoded from an AI reply. Other work kinds will be selected by owning advance
  * only after their actual native predecessor has been connected.
  * @since   Oct.  6, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseNativeWork {
  final case class Request(instanceIdentity: String, sourceRevision: String, kind: String,
    stepIdentity: Option[String], generation: Long, purpose: String, selectedPlan: O.Plan,
    originalBase: O.Snapshot, completed: Vector[O.ClosedStep], candidate: Option[O.Snapshot],
    checksReceipt: Option[String], repairDirective: Option[O.RepairDirective],
    review: Option[GoalPhaseNativeReview.Context] = None,
    decisionContext: Option[DecisionContext] = None)
  final case class Result(outcome: String, summary: String, findings: Vector[String])
  final case class DecisionContext(requestingResult: Result, choice: String, rationale: String)

  // Derive the nested review payload bottom-up, as for native merge review.
  // The wire fields remain unchanged; whole-Request auto derivation otherwise
  // exceeds Scala's inline depth when reaching the vector of actual receipts.
  private given Encoder[RepositorySyncNativeMergeValidation.Receipt] = deriveEncoder
  private given Decoder[RepositorySyncNativeMergeValidation.Receipt] = deriveDecoder
  private given Encoder[GoalPhaseNativeReview.Context] = deriveEncoder
  private given Decoder[GoalPhaseNativeReview.Context] = deriveDecoder
  private given Encoder[DecisionContext] = deriveEncoder
  private given Decoder[DecisionContext] = deriveDecoder

  def initialC(source: F.Session): Consequence[Request] = for {
    state <- O.startC(source.started.receipt.payload.value.plan, source.started.receipt.payload.value.base)
    semantic <- state.next match {
      case value: O.Semantic => Consequence.success(value)
      case _ => F.invalid("original selected plan has no initial semantic work")
    }
  } yield Request(source.current.identity.value, source.started.receipt.next.revision.value.toString,
    semantic.key.kind.toString, semantic.key.step, semantic.key.generation, semantic.purpose,
    semantic.selectedPlan, semantic.originalBase, semantic.completed, semantic.candidate,
    semantic.checksReceipt, semantic.repairDirective)

  def requiredC: Consequence[StateMachineRequiredOperation] = requiredC("ImplementStep")
  def requiredC(kind: String): Consequence[StateMachineRequiredOperation] = {
    val (capability, action, service, operation, contract) = kind match {
      case "ImplementStep" => ("goal-phase-implementation-capability", "ImplementOrderedStep",
        "GoalPhaseImplementationService", "implementGoalPhaseStep", "implementation")
      case "ReviewStep" => ("goal-phase-step-review-capability", "ReviewOrderedStep",
        "GoalPhaseStepReviewService", "reviewGoalPhaseStep", "step-review")
      case "RepairStep" => ("goal-phase-repair-capability", "RepairOrderedStep",
        "GoalPhaseRepairWorkService", "repairGoalPhaseStep", "repair")
      case "ReviewPhase" => ("goal-phase-review-capability", "ReviewWholePhase",
        "GoalPhaseReviewService", "reviewGoalPhase", "phase-review")
      case _ => return F.invalid("semantic work kind has no connected Required SPI")
    }
    val found = domain.statemachine.workflow.GoalPhaseWorkflowStateMachineWorkflow1.workflow.requiredOperations
      .filter(_.identity.capability == capability)
    if (found.size == 1 && found.head.actionIdentity == action &&
        found.head.operation.service == service && found.head.operation.operation == operation &&
        found.head.inputType.map(_.value).contains(inputCodec.typeIdentity) &&
        found.head.resultType.map(_.value).contains(resultCodec.typeIdentity)) {
      val spi = found.head
      Consequence.success(StateMachineRequiredOperation(StateMachineRequiredOperationIdentity(spi.identity.capability),
        spi.actionIdentity, StateMachineOperationIdentity(spi.operation.service, spi.operation.operation),
        spi.inputType.map(v => StateMachineInputTypeReference(v.value)),
        spi.resultType.map(v => StateMachineResultTypeReference(v.value)),
        StateMachineRequiredOperationMetadata(
          ContextContract("goal-phase-ordered-" + contract + "-context-v1", Vector.empty, Vector.empty),
          CompletionContract("goal-phase-ordered-" + contract + "-completion-v1", Vector.empty),
          EvidenceContract("goal-phase-ordered-" + contract + "-evidence-v1", Vector.empty), Vector.empty)))
    }
    else F.invalid("initial work differs from actual CML Required SPI")
  }
  def admitInitialResultC(request: Request, result: Result): Consequence[Unit] =
    if (request == null || result == null || request.kind != "ImplementStep" || request.review.nonEmpty || result.summary == null ||
        result.summary.trim.isEmpty || result.findings == null ||
        !result.findings.forall(f => f != null && f.trim.nonEmpty) ||
        !Set("completed", "decision-needed")(result.outcome) ||
        (result.outcome == "completed" && result.findings.nonEmpty))
      F.invalid("initial assigned result must report completion or an explicit decision, never review/command/closure")
    else Consequence.unit

  def admitSemanticResultC(request: Request, result: Result): Consequence[Unit] =
    if (request.kind == "ImplementStep") admitInitialResultC(request, result)
    else if (request.kind != "RepairStep" || request.repairDirective.isEmpty || request.review.isEmpty ||
        request.candidate.nonEmpty || request.checksReceipt.nonEmpty || result == null ||
        result.summary == null || result.summary.trim.isEmpty || result.findings == null ||
        !result.findings.forall(f => f != null && f.trim.nonEmpty) ||
        !Set("completed", "decision-needed")(result.outcome) ||
        (result.outcome == "completed" && result.findings.nonEmpty))
      F.invalid("assigned repair must return semantics or a decision, not review, checks, commands or closure")
    else Consequence.unit

  private class Codec[A](val typeIdentity: String, schema: String)(using Encoder[A], Decoder[A])
      extends WorkflowResultJsonV1.PayloadCodec[A] {
    def encode(value: A) = Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> value.asJson)
    def decode(value: Json): Either[String, A] = for {
      root <- Option(value).flatMap(_.asObject).filter(_.keys.toSet == Set("schemaVersion", "payload"))
        .toRight("native ordered work envelope differs")
      _ <- Either.cond(root("schemaVersion").flatMap(_.asString).contains(schema), (), "native ordered work schema differs")
      parsed <- root("payload").get.as[A].left.map(_.message)
      _ <- Either.cond(encode(parsed) == value, (), "native ordered work contains undeclared fields")
    } yield parsed
  }
  val inputCodec: WorkflowResultJsonV1.PayloadCodec[Request] =
    new Codec[Request]("GoalPhaseWorkRequest", "sm-workflow.goal-phase-native-work-request.v1") {
      override def encode(value: Request): Json = {
        val payload = value.asJson.mapObject { fields =>
          val review = if (value.review.isEmpty) fields.remove("review") else fields
          if (value.decisionContext.isEmpty) review.remove("decisionContext") else review
        }
        Json.obj("schemaVersion" -> Json.fromString("sm-workflow.goal-phase-native-work-request.v1"),
          "payload" -> payload)
      }
    }
  val resultCodec: WorkflowResultJsonV1.PayloadCodec[Result] =
    new Codec[Result]("GoalPhaseWorkResult", "sm-workflow.goal-phase-native-work-result.v1")

  def providedDefinition: GeneratedProvidedApiAbi.Definition = {
    val p = domain.statemachine.providedapi.GoalPhaseWorkflowStateMachineProvidedApi1.workflow
    GeneratedProvidedApiAbi.Definition(p.schemaVersion, p.generator, p.identity, p.version,
      GeneratedProvidedApiAbi.WorkflowSourceCorrelation(GeneratedProvidedApiAbi.SourceIdentity(p.source.root.line),
        GeneratedProvidedApiAbi.SourceIdentity(p.source.definition.line)),
      p.providedOperations.map(o => GeneratedProvidedApiAbi.Operation(o.service, o.name, o.inputType, o.resultType,
        GeneratedProvidedApiAbi.SourceIdentity(o.source.line))))
  }
}
