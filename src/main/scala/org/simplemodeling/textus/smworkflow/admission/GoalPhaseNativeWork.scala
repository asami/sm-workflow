package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Encoder, Decoder, Json, JsonObject}
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

  /** Resolve the actual assignment/target, not the unchanged semantic body.
    * The selected WorkOrder supplies its original meaning/presentation; the
    * controller owns effects from the retained native plan and history.
    */
  private[admission] def sameAssignment(left: Request, right: Request): Boolean =
    left != null && right != null && left.instanceIdentity == right.instanceIdentity &&
      left.sourceRevision == right.sourceRevision && left.kind == right.kind &&
      left.stepIdentity == right.stepIdentity && left.generation == right.generation &&
      samePlanTarget(left.selectedPlan, right.selectedPlan) && left.originalBase == right.originalBase &&
      left.candidate == right.candidate && left.checksReceipt == right.checksReceipt

  private[admission] def samePlanTarget(left: O.Plan, right: O.Plan): Boolean =
    left != null && right != null && left.identity == right.identity && left.revision == right.revision &&
      left.phase == right.phase && left.repository == right.repository

  private[admission] def matchesSemanticTarget(input: Request, work: O.Semantic): Boolean =
    input != null && work != null && samePlanTarget(input.selectedPlan, work.selectedPlan) &&
      input.kind == work.key.kind.toString && input.stepIdentity == work.key.step &&
      input.generation == work.key.generation && input.originalBase == work.originalBase &&
      input.candidate == work.candidate && input.checksReceipt == work.checksReceipt

  /** Resolve an already issued slot and its current executable contract.
    * Original meaning, review context and presentation come from that slot;
    * no freshly projected body replaces or certifies them.
    */
  private[admission] def resolveIssuedC(expected: WorkflowInteraction[Request, Nothing],
    actual: WorkflowInteraction[Request, Nothing]): Consequence[Request] =
    if (expected == null || actual == null || actual.handle == null || actual.handle != expected.handle)
      F.invalid("issued semantic work has another instance or execution reference")
    else (expected.current, actual.current) match {
      case (planned: WorkflowContinuation.WorkOrder[Request], selected: WorkflowContinuation.WorkOrder[Request])
          if planned.request != null && planned.requirement != null && selected.request != null && selected.requirement != null =>
        val e = planned.request
        val q = selected.request
        for {
          _ <- actual.handle.validateC
          requirement <- selected.requirement.validateC
          input <- q.input match {
            case Some(value) if value != null && value.typeIdentity == inputCodec.typeIdentity && value.value != null =>
              Consequence.success(value.value)
            case _ => F.invalid("issued semantic work has no assigned typed input")
          }
          _ <- if (q.runId == e.runId && q.continuationId == e.continuationId && q.expectedRevision == e.expectedRevision &&
              q.requiredOperation == e.requiredOperation && q.operation == e.operation && q.resultType == e.resultType &&
              q.completionOperation == e.completionOperation && q.completion == e.completion && q.evidence == e.evidence &&
              q.context != null && e.context != null && GoalPhaseClosureAuthority.sameContextScope(q.context.snapshot, e.context.snapshot) &&
              requirement.reviewRequired == planned.requirement.reviewRequired &&
              requirement.capabilities == planned.requirement.capabilities &&
              e.input.exists(value => value != null && sameAssignment(input, value.value))) Consequence.unit
            else F.invalid("issued semantic work has an incompatible assignment, scope or executable route")
        } yield input
      case _ => F.invalid("selected semantic issue is not a compatible WorkOrder")
    }

  private[admission] def sameResultTarget(left: ContinuationResult[Result], right: ContinuationResult[Result]): Boolean =
    left != null && right != null && left.result != null && right.result != null &&
      left.handle == right.handle && left.runId == right.runId && left.continuationId == right.continuationId &&
      left.expectedRevision == right.expectedRevision && left.resultReference == right.resultReference &&
      left.result.typeIdentity == right.result.typeIdentity

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

  // Declared structural admission is not an encode/decode content certificate.
  // Decoders below own actual value/type admission, including optional fields.
  private def fields(value: Json, allowed: Set[String], nested: JsonObject => Boolean = _ => true): Boolean =
    Option(value).flatMap(_.asObject).exists(f => f.keys.forall(allowed) && nested(f))
  private def optional(f: JsonObject, key: String, shape: Json => Boolean): Boolean =
    f(key).forall(value => value.isNull || shape(value))
  private def child(f: JsonObject, key: String, shape: Json => Boolean): Boolean = f(key).exists(shape)
  private def array(f: JsonObject, key: String, shape: Json => Boolean): Boolean =
    f(key).flatMap(_.asArray).exists(_.forall(shape))
  private[admission] def snapshotShape(value: Json): Boolean = fields(value, Set("head", "tree"))
  private def classificationShape(value: Json): Boolean = fields(value, Set("kind", "complexity", "domain", "footprint"))
  private def assignmentShape(value: Json): Boolean = fields(value, Set("implementation", "repair", "review"), f =>
    Vector("implementation", "repair", "review").forall(child(f, _, classificationShape)))
  private def stepShape(value: Json): Boolean = fields(value,
    Set("identity", "purpose", "ownedPaths", "dependencies", "checks", "workClassification"),
    f => optional(f, "workClassification", assignmentShape))
  private[admission] def planShape(value: Json): Boolean = fields(value,
    Set("identity", "revision", "phase", "repository", "steps", "phaseChecks", "repairLimit", "phaseReviewClassification"),
    f => array(f, "steps", stepShape) && optional(f, "phaseReviewClassification", classificationShape))
  private[admission] def closedShape(value: Json): Boolean = fields(value, Set("step", "commit", "review"),
    f => child(f, "commit", value => fields(value, Set("identity", "parent", "tree"))))
  private def directiveShape(value: Json): Boolean = fields(value, Set("reason", "findings"))
  private def resultShape(value: Json): Boolean = fields(value, Set("outcome", "summary", "findings"))
  private def receiptShape(value: Json): Boolean = fields(value, Set("identity", "termination", "exitCode", "stdout", "stderr"))
  private def reviewShape(value: Json): Boolean = fields(value,
    Set("changedPaths", "diff", "checks"), f => array(f, "checks", receiptShape))
  private def decisionShape(value: Json): Boolean = fields(value, Set("requestingResult", "choice", "rationale"),
    f => child(f, "requestingResult", resultShape))
  private def inputShape(value: Json): Boolean = fields(value,
    Set("instanceIdentity", "sourceRevision", "kind", "stepIdentity", "generation", "purpose", "selectedPlan",
      "originalBase", "completed", "candidate", "checksReceipt", "repairDirective", "review", "decisionContext"),
    f => child(f, "selectedPlan", planShape) && child(f, "originalBase", snapshotShape) && array(f, "completed", closedShape) &&
      optional(f, "candidate", snapshotShape) && optional(f, "repairDirective", directiveShape) &&
      optional(f, "review", reviewShape) && optional(f, "decisionContext", decisionShape))

  private class Codec[A](val typeIdentity: String, schema: String, shape: Json => Boolean,
    legacy: Option[String] = None, normalizeLegacy: Json => Json = value => value)(using Encoder[A], Decoder[A])
      extends WorkflowResultJsonV1.PayloadCodec[A] {
    def encode(value: A) = Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> value.asJson)
    def decode(value: Json): Either[String, A] = for {
      root <- Option(value).flatMap(_.asObject).filter(_.keys.toSet == Set("schemaVersion", "payload"))
        .toRight("native ordered work envelope differs")
      version <- root("schemaVersion").flatMap(_.asString).toRight("native ordered work schema is absent")
      _ <- Either.cond(version == schema || legacy.contains(version), (), "native ordered work schema differs")
      selected = if (legacy.contains(version)) normalizeLegacy(root("payload").get) else root("payload").get
      _ <- Either.cond(shape(selected), (), "native ordered work contains undeclared fields")
      parsed <- selected.as[A].left.map(_.message)
    } yield parsed
  }
  val inputCodec: WorkflowResultJsonV1.PayloadCodec[Request] =
    new Codec[Request]("GoalPhaseWorkRequest", "sm-workflow.goal-phase-native-work-request.v2", inputShape,
      Some("sm-workflow.goal-phase-native-work-request.v1"), body => body.mapObject { fields =>
        fields("review").filterNot(_.isNull).map(review => fields.add("review", review.mapObject(_.remove("policyIdentity").remove("policyRevision"))))
          .getOrElse(fields)
      }) {
      override def encode(value: Request): Json = {
        val payload = value.asJson.mapObject { fields =>
          val review = if (value.review.isEmpty) fields.remove("review") else fields
          if (value.decisionContext.isEmpty) review.remove("decisionContext") else review
        }
        Json.obj("schemaVersion" -> Json.fromString("sm-workflow.goal-phase-native-work-request.v2"),
          "payload" -> payload)
      }
    }
  val resultCodec: WorkflowResultJsonV1.PayloadCodec[Result] =
    new Codec[Result]("GoalPhaseWorkResult", "sm-workflow.goal-phase-native-work-result.v1", resultShape)

  def providedDefinition: GeneratedProvidedApiAbi.Definition = {
    val p = domain.statemachine.providedapi.GoalPhaseWorkflowStateMachineProvidedApi1.workflow
    GeneratedProvidedApiAbi.Definition(p.schemaVersion, p.generator, p.identity, p.version,
      GeneratedProvidedApiAbi.WorkflowSourceCorrelation(GeneratedProvidedApiAbi.SourceIdentity(p.source.root.line),
        GeneratedProvidedApiAbi.SourceIdentity(p.source.definition.line)),
      p.providedOperations.map(o => GeneratedProvidedApiAbi.Operation(o.service, o.name, o.inputType, o.resultType,
        GeneratedProvidedApiAbi.SourceIdentity(o.source.line))))
  }
}
