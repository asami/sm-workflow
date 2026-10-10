package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.unitofwork.UnitOfWork
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as InstanceContinuationIdentity, *}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{ReviewStepResult, StepCloseRequest}

/** Result translation and actual completion are different boundaries.
  * The application fixture calls real Operations, with in-memory host ports;
  * it is not managed SQLite, executor provenance or Phase acceptance coverage.
  * @since   Oct.  9, 2026
  * @version Oct.  9, 2026
  */
final class WorkflowWorkerResultConsumptionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"Unexpected result conversion or completion failure: $other")
  }
  private def _metadata(example: String, rule: String) =
    afterWord(s"in spec:workflow-worker-result-consumption, example:$example, rules:$rule, phase:5")
  private def _skill[R](result: ContinuationResult[R]): SkillWorkResult[R] =
    SkillWorkResult(result.runId, result.continuationId, result.expectedRevision,
      result.contextSnapshot, result.result, result.resultReference, result.completionFacts,
      result.evidence.references, result.evidence.workerIdentity, result.evidence.modelIdentity,
      "returned-profile", "returned-policy", result.evidence.execution)
  private def _normalize(issued: WorkflowInteraction[StepCloseRequest, Nothing],
      result: ContinuationResult[ReviewStepResult]): ContinuationResult[ReviewStepResult] =
    _take(new SkillWorkResultNormalizerV1[StepCloseRequest, ReviewStepResult].admitC(issued, _skill(result)))
  private def _wire(result: ContinuationResult[ReviewStepResult], annotated: Boolean = false): String = {
    val encoded = _take(WorkflowResultJsonV1.encodeC(result, GoalPhaseReviewCompletionOperation.payloadCodec))
    if (!annotated) encoded
    else parse(encoded).fold(error => fail(error.message), identity)
      .mapObject(_.add("diagnosticNote", Json.fromString("not a progression selector"))).noSpaces
  }

  private final class NativeFixture {
    val configuration = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("worker-result-fixture")),
      MigrationPolicy("fixture"), RetentionPolicy("fixture"), LeasePolicy("fixture"),
      DeliveryConfiguration.SameStore(WorkflowStoreIdentity("worker-result-fixture")))
    val fact = ContextReference("native-required-fact", "1")
    val evidence = ContextReference("native-required-evidence", "1")
    val identity = org.goldenport.cncf.workflow.ContinuationIdentity("native-worker-result")
    val definition = GoalPhaseApplicationFixture.binding
    val snapshot = ContextSnapshot(definition.workflowRevision.value, Some("native-model"))
    val required = StateMachineRequiredOperation(StateMachineRequiredOperationIdentity("fixture-review"), "ReviewStep",
      StateMachineOperationIdentity("FixtureReviewService", "review"), Some(StateMachineInputTypeReference("FixtureInput")),
      Some(StateMachineResultTypeReference("FixtureResult")), StateMachineRequiredOperationMetadata(
        ContextContract("fixture-context", Vector.empty, Vector.empty),
        CompletionContract("fixture-completion", Vector(fact)), EvidenceContract("fixture-evidence", Vector(evidence)), Vector.empty))
    val native = Continuation(StateMachineRunIdentity("native-worker-run"), identity, StateMachineRevision("native-revision"),
      required, ContextBundle("Native work", Vector.empty, Vector.empty, snapshot))
    val initial = InstanceRecord(InstanceIdentity("native-worker-instance"), definition, initialRevision,
      Lifecycle.NotStarted, None, CorrelationReference("native-correlation"), CausationReference("native-start"),
      None, None, None, Vector.empty)
    val current = _take(initial.appendC(initialRevision, HistoryEntry(HistorySequence(1L), InstanceRevision(1L),
      Lifecycle.Active, Some(ProgressionReference("step.ReviewPending")), initial.correlation, initial.causation, None, None,
      Some(SuspensionBoundary(InstanceContinuationIdentity(identity.value), InstanceRevision(1L),
        ContextSnapshotReference(snapshot.workflowRevision), CompletionReference("fixture-completion"),
        EvidenceReference("fixture-evidence"), CorrelationReference("native-resume"))))))
    val handle = _take(WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, current))
    val request = ContinuationRequest(native.runId, identity, native.expectedRevision, required.identity, required.operation,
      Some(TypedValue("FixtureInput", "original input")), required.resultType, native.context,
      required.metadata.completionContract, required.metadata.evidenceContract,
      StateMachineOperationIdentity("FixtureWorkflowService", "complete"))
    val requirement = ExecutionRequirement(Vector(CapabilityRequirement("fixture-review")), RiskLevel("bounded"),
      ReasoningLevel.Deep, true, Some(ExecutionDisposition(ExecutionPlacement.Delegated,
        ExecutionIndependence.Required, "native-origin")))
    val work = WorkflowInteraction[String, Nothing](handle,
      WorkflowContinuation.WorkOrder(request, requirement, MinimalPresentation("Native review", "Awaiting result")))
    val issued = new IssuedWorkOrderPersistence.InMemory[String]
    _take(issued.putIfAbsentC(work))
    val persistence = new ContinuationRuntimePersistence.InMemory
    _take(persistence.createC(ContinuationRuntimePersistence.Record(native, ContinuationRuntimePersistence.Status.Available)))
    val runtime = new PersistentContinuationRuntime(persistence)
    _take(runtime.claimC(identity))
    val instances = new WorkflowInstancePersistence {
      def create(configuration: Configuration, record: InstanceRecord): Consequence[InstanceRecord] =
        Consequence.stateConflict("Loose result fixture does not create instances")
      def load(configuration: Configuration, identity: InstanceIdentity): Consequence[Option[InstanceRecord]] =
        Consequence.success(Some(current).filter(_.identity == identity))
      def append(configuration: Configuration, identity: InstanceIdentity, expectedRevision: InstanceRevision,
          entry: HistoryEntry): Consequence[InstanceRecord] =
        Consequence.stateConflict("Loose result fixture does not append history")
    }
    var openings = 0
    var commits = 0
    val result = ContinuationResult(handle, native.runId, identity, native.expectedRevision, snapshot,
      TypedValue("FixtureResult", "completed"), ContextReference("native-result", "1"), Vector(fact),
      ExecutionEvidence(Vector(evidence), execution = Some(ExecutionObservation(ExecutionPlacement.Delegated,
        "native-origin", "native-executor", ""))))
    def completeC(submitted: ContinuationResult[String]): Consequence[ContinuationRuntime.Resume] =
      resumePersistedWorkOrderC(SmWorkflowComponent.componentId, submitted, issued, runtime, instances, configuration,
        () => {
          openings += 1
          val uow = new UnitOfWork(ExecutionContext.create())
          uow.stagePostCommitC { commits += 1; Consequence.unit }
          uow
        })
  }

  "Worker result consumption" should {
    "public GoalPhase completion" which {
      "E1 preserve harmless returned diagnostics and annotations" must _metadata("E1", "R2 R3") {
        "close the reviewed Step through its actual Operation without a copied-selection certificate" in {
          Given("R2 R3: legacy no-disposition review work and harmless result variants; a codec is not completion authority")
          val variants = Vector("diagnostics", "references", "dispatch-copy", "annotations", "combined")
          variants.foreach { variant =>
            When(s"public GoalPhase Operations issue work and translate the $variant completion")
            val fixture = new GoalPhaseApplicationFixture()
            try {
              val issued = fixture.firstReview()
              val raw = fixture.reviewResult(issued, "accept")
              val translated = _normalize(issued, raw)
              val extra = ContextReference("", "")
              val diagnostics = translated.copy(contextSnapshot = translated.contextSnapshot.copy(
                workspaceRevision = Some(""), evidenceRevision = Some("")),
                evidence = translated.evidence.copy(workerIdentity = Some(""), modelIdentity = Some(" ")))
              val references = translated.copy(completionFacts = Vector(extra, extra),
                evidence = translated.evidence.copy(references = Vector(extra, extra)))
              val dispatch = translated.evidence.skillDispatch.get
              val copied = dispatch.copy(selectedWorkerProfile = "", mappingPolicyVersion = "",
                requestedRequirement = dispatch.requestedRequirement.copy(capabilities = Vector(CapabilityRequirement("")),
                  risk = RiskLevel(""), reasoningClass = Some(ReasoningClassReference(""))))
              val submitted = variant match {
                case "diagnostics" => diagnostics
                case "references" => references
                case "dispatch-copy" => translated.copy(evidence = translated.evidence.copy(skillDispatch = Some(copied)))
                case "combined" => diagnostics.copy(completionFacts = references.completionFacts,
                  evidence = diagnostics.evidence.copy(references = references.evidence.references, skillDispatch = Some(copied)))
                case _ => translated
              }
              val wire = _wire(submitted, variant == "annotations" || variant == "combined")
              val decoded = WorkflowResultJsonV1.decodeC(wire, GoalPhaseReviewCompletionOperation.payloadCodec)
              val reply = fixture.invoke("submitStepReview", wire)
              val completed = fixture.stored
              val commits = fixture.commits
              Then("conversion retains the data and the actual typed completion closes the Step once")
              decoded shouldBe Consequence.success(submitted)
              reply.isSuccess shouldBe true
              completed.lifecycle shouldBe Lifecycle.Completed
              completed.suspension shouldBe None
              commits.size shouldBe 1
              When("the same result is submitted again through the actual public completion Operation")
              val replay = fixture.invoke("submitStepReview", wire)
              val afterreplay = fixture.stored
              val replaycommits = fixture.commits
              Then("the replay is refused without another history entry or commit")
              replay.isSuccess shouldBe false
              afterreplay.history shouldBe completed.history
              afterreplay.lifecycle shouldBe Lifecycle.Completed
              replaycommits.size shouldBe 1
            } finally fixture.subsystem.shutdown()
          }
        }
      }
      "E2 distinguish representable results from applicable native results" must _metadata("E2", "R2 R4") {
        "reject real target, type and semantic evidence errors without a commit or history append" in {
          Given("R2 R4: an actual pending review and result errors that must be stopped by its consuming Operation")
          val variants = Vector("run", "continuation", "instance", "revision", "model", "type", "candidate", "review-evidence")
          variants.foreach { variant =>
            When(s"public Operations issue a review and normalization converts the $variant error")
            val fixture = new GoalPhaseApplicationFixture()
            try {
              val issued = fixture.firstReview()
              val original = fixture.reviewResult(issued, "accept")
              val raw = variant match {
                case "run" => original.copy(runId = StateMachineRunIdentity("another-run"))
                case "continuation" => original.copy(continuationId = org.goldenport.cncf.workflow.ContinuationIdentity("another-continuation"))
                case "instance" => original.copy(handle = original.handle.copy(instanceIdentity = InstanceIdentity("another-instance")))
                case "revision" => original.copy(expectedRevision = StateMachineRevision("stale"))
                case "model" => original.copy(contextSnapshot = original.contextSnapshot.copy(modelRevision = Some("another-model")))
                case "type" => original.copy(result = original.result.copy(typeIdentity = "AnotherResult"))
                case "candidate" => fixture.reviewResult(issued, "accept", Some("another-candidate"))
                case _ => original.copy(result = original.result.copy(value = original.result.value.copy(reviewEvidence = Vector.empty)))
              }
              val translated = _normalize(issued, raw).copy(handle = raw.handle)
              val wire = _wire(translated)
              val decoded = WorkflowResultJsonV1.decodeC(wire, GoalPhaseReviewCompletionOperation.payloadCodec)
              val before = fixture.stored
              val rejected = fixture.invoke("submitStepReview", wire)
              val after = fixture.stored
              val committed = fixture.commits
              val claim = fixture.runtime.recoverClaimC(original.continuationId)
              Then("data conversion succeeds but the Operation refuses the native mismatch without consuming its claim")
              decoded shouldBe Consequence.success(translated)
              rejected shouldBe a[Consequence.Failure[?]]
              after.history shouldBe before.history
              after.suspension shouldBe before.suspension
              committed shouldBe empty
              claim.isSuccess shouldBe true
              When("the originally assigned review is submitted after the rejected input")
              val accepted = fixture.invoke("submitStepReview", _wire(_normalize(issued, original)))
              val completed = fixture.stored
              Then("the failed input grants no authority and does not prevent the actual result completing once")
              accepted.isSuccess shouldBe true
              completed.lifecycle shouldBe Lifecycle.Completed
              fixture.commits.size shouldBe 1
            } finally fixture.subsystem.shutdown()
          }
        }
      }
    }
    "native loose completion" which {
      "E3 consume required native references and executor relations before fresh UoW" must _metadata("E3", "R4") {
        "reject missing native prerequisites and an empty independent executor without treating profile text as authority" in {
          Given("R4: a native claimed continuation and fixture-owned executor observations, not proof an AI ran")
          When("the runtime records its actual claim and receives missing prerequisites or incorrect executor relations")
          val fixture = new NativeFixture
          val result = fixture.result
          val observation = result.evidence.execution.get
          val invalid = Vector(result.copy(completionFacts = Vector.empty),
            result.copy(evidence = result.evidence.copy(references = Vector.empty)),
            result.copy(evidence = result.evidence.copy(execution = None)),
            result.copy(evidence = result.evidence.copy(execution = Some(observation.copy(executorContextIdentity = "")))),
            result.copy(evidence = result.evidence.copy(execution = Some(observation.copy(executorContextIdentity = "native-origin")))),
            result.copy(evidence = result.evidence.copy(execution = Some(observation.copy(placement = ExecutionPlacement.Inline)))),
            result.copy(evidence = result.evidence.copy(execution = Some(observation.copy(originContextIdentity = "another-origin")))))
          val rejected = invalid.map(fixture.completeC)
          val before = _take(fixture.persistence.loadC(fixture.identity)).get
          val openings = fixture.openings
          val commits = fixture.commits
          Then("actual completion conditions fail before a fresh transaction and the native claim remains available to its owner")
          rejected.foreach(_ shouldBe a[Consequence.Failure[?]])
          before.status shouldBe ContinuationRuntimePersistence.Status.Claimed
          openings shouldBe 0
          commits shouldBe 0
          When("the native prerequisites and executor relations are satisfied, with no profile diagnostic text")
          val accepted = fixture.completeC(result)
          val replay = fixture.completeC(result)
          val completed = _take(fixture.persistence.loadC(fixture.identity)).get
          Then("one real fresh UoW completes the continuation and replay creates no second transaction")
          accepted.isSuccess shouldBe true
          replay shouldBe a[Consequence.Failure[?]]
          completed.status shouldBe ContinuationRuntimePersistence.Status.Completed
          fixture.openings shouldBe 1
          fixture.commits shouldBe 1
        }
      }
    }
    "retained result conversion" which {
      "E4 read V1 and V2 diagnostic annotations without a saved-shape certificate" must _metadata("E4", "R3") {
        "preserve declared result facts and reject only unconvertible frames in this codec example" in {
          Given("R3: V1 and V2 passive result frames, not evidence of a durable completion or executor run")
          val versions = Vector("V1", "V2")
          versions.foreach { version =>
            When(s"actual GoalPhase Operations issue sample work and the $version result codec reads annotations")
            val fixture = new GoalPhaseApplicationFixture()
            try {
              val issued = fixture.firstReview()
              val original = _normalize(issued, fixture.reviewResult(issued, "accept"))
              val closing = if (version == "V2") Some(StateMachineOperationResult(
                StateMachineResultTypeReference("StepCompleted"), original.resultReference)) else None
              val record = WorkflowCompletionRecordJsonV1.Record(InstanceRevision(2L), original, closing)
              val encoded = _take(WorkflowCompletionRecordJsonV1.encodeC(record,
                GoalPhaseReviewCompletionOperation.payloadCodec))
              val root = parse(encoded).fold(error => fail(error.message), identity)
              val returned = original.copy(evidence = original.evidence.copy(workerIdentity = Some(""), modelIdentity = Some(" ")))
              val semantic = if (version == "V2") root.hcursor.downField("semanticCompletion").focus.get else root
              val body = semantic.hcursor.downField("result").focus.get
              val evidence = body.hcursor.downField("executionEvidence").focus.get.mapObject(
                _.add("workerIdentity", Json.fromString(""))
                  .add("modelIdentity", Json.fromString(" "))
                  .add("diagnosticNote", Json.fromString("not an execution grant")))
              val annotatedbody = body.mapObject(_.add("executionEvidence", evidence)
                .add("diagnosticNote", Json.fromString("not a transition selector")))
              val annotatedsemantic = semantic.mapObject(_.add("result", annotatedbody)
                .add("diagnosticNote", Json.fromString("not a saved-copy proof")))
              val annotated = if (version == "V2") root.mapObject(_.add("semanticCompletion", annotatedsemantic)
                .add("diagnosticNote", Json.fromString("not a closing-program selector"))) else annotatedsemantic
              val decoded = WorkflowCompletionRecordJsonV1.decodeC(annotated.noSpaces,
                GoalPhaseReviewCompletionOperation.payloadCodec)
              val unsupported = root.mapObject(_.add("schemaVersion", Json.fromString("unsupported")))
              val unsupportedresult = WorkflowCompletionRecordJsonV1.decodeC(unsupported.noSpaces,
                GoalPhaseReviewCompletionOperation.payloadCodec)
              val malformed = WorkflowCompletionRecordJsonV1.decodeC("{", GoalPhaseReviewCompletionOperation.payloadCodec)
              val missingresult = annotatedsemantic.mapObject(_.remove("result"))
              val incomplete = if (version == "V2") root.mapObject(_.add("semanticCompletion", missingresult)) else missingresult
              val incompleteresult = WorkflowCompletionRecordJsonV1.decodeC(incomplete.noSpaces,
                GoalPhaseReviewCompletionOperation.payloadCodec)
              val commits = fixture.commits
              Then("declared data survives annotations while unsupported or missing representation fails without completing work")
              decoded shouldBe Consequence.success(record.copy(result = returned))
              unsupportedresult shouldBe a[Consequence.Failure[?]]
              malformed shouldBe a[Consequence.Failure[?]]
              incompleteresult shouldBe a[Consequence.Failure[?]]
              commits shouldBe empty
            } finally fixture.subsystem.shutdown()
          }
        }
      }
    }
  }
}
