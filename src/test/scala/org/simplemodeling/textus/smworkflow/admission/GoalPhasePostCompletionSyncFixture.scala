package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.Property
import org.goldenport.protocol.Request
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.preparation.NativeCompletionPolicyPreparation
import org.simplemodeling.textus.smworkflow.value as V
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import GoalPhaseCompletionPolicyAdoption as A
import GoalPhaseNativeWork as S
import SplitPhaseExplicitPartitionFixture.take

/** Isolated normal Starts and retained transitions, real Compile/Test and Git.
  * All Human/reviewer evidence belongs to this test fixture only.
  * @since   Oct. 23, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
private[admission] trait GoalPhasePostCompletionSyncFixture extends GoalPhaseKnownFailedPhaseValidationFixture {
  override protected def goal_completion_policy: String = GoalPhasePostCompletionSync.localPolicy
  protected def post_completion_parameters(root: Path): Map[String, String] = Map.empty
  override protected def _with_runtime_(root: Path, scenario: String,
    host: Option[ThreeWorkflowRuntimeParameters.Definition], hostParameters: Map[String, String],
    nativeAcknowledgement: Option[String => Consequence[Unit]],
    goalAcknowledgement: Option[String => Consequence[Unit]], componentInstance: String)(
    task: (org.goldenport.cncf.subsystem.Subsystem, org.goldenport.cncf.component.Component,
      ExecutionContext, (Setup => Consequence[Unit]) => Unit) => Unit): Unit =
    super._with_runtime_(root, scenario, host, hostParameters ++ post_completion_parameters(root),
      nativeAcknowledgement, goalAcknowledgement, componentInstance)(task)
  override protected def recoveryHost(root: Path): ThreeWorkflowRuntimeParameters.Definition = {
    val original = super.recoveryHost(root)
    original.copy(repository = original.repository.copy(validation = RepositorySyncNativeMergeValidation.Policy(
      "post-completion-structural-checks", 1, "explicit-no-product-tests",
      "This fixture explicitly selects structural repository checks after the accepted Phase Compile and Test", Vector.empty)))
  }
  def freshRoot(name: String): Path = newKnownRoot(name)
  def sameInstance(actual: InstanceRecord, expected: InstanceRecord): Unit = {
    actual.definition.workflowIdentity shouldBe expected.definition.workflowIdentity
    actual.definition.workflowRevision shouldBe expected.definition.workflowRevision
    actual.definition.sourceCorrelation shouldBe expected.definition.sourceCorrelation
    actual.definition.producerAbiIdentity shouldBe expected.definition.producerAbiIdentity
    actual.definition.workflowAbiIdentity shouldBe expected.definition.workflowAbiIdentity
    actual.definition.bootstrapAbiIdentity shouldBe expected.definition.bootstrapAbiIdentity
    actual.definition.producerRevision shouldBe expected.definition.producerRevision
    actual.copy(definition = expected.definition) shouldBe expected
  }
  def episode(root: Path, initialize: Boolean = false,
    acknowledgement: Option[String => Consequence[Unit]] = None,
    componentInstance: String = "repository-spec")(task: Episode => Unit): Unit = {
    if (initialize) Files.writeString(root.resolve("phase-ready"), "ready\n")
    inKnownEpisode(root, initialize, acknowledgement, componentInstance)(value => task(new Episode(value)))
  }
  final class Episode(val native: RecoveryEpisode) {
    val root: Path = native.root
    private var _released_evidence = Vector.empty[V.EvidenceRecordingState]
    def releasedEvidence: Vector[V.EvidenceRecordingState] = _released_evidence
    def current(identity: String): InstanceRecord = native.current(identity)
    def advance(identity: String): V.GoalPhaseAdvanceResult = {
      val result = native.advance(identity)
      _released_evidence = _released_evidence ++ result.validation.toVector.flatMap(_.evidenceRecording)
      result
    }
    def describeEvidence(reference: V.EvidenceReference): V.EvidenceDetailResult = read_value[V.EvidenceDetailResult](take(
      native.owner.executeOperationResponseInContext(Request.of(component = native.component.name,
        service = "EvidenceService", operation = "describeEvidence", properties = List(Property("input",
          V.EvidenceDescribeInput(reference.identity, Some(reference.revision)).toRecord(), None))), native.actor)))
    def call(operation: String, input: org.goldenport.record.Record) = native.call(operation, List(Property("input", input, None)))
    def describe(identity: String): V.GoalPhaseCompletionPolicyDescriptionResult = native.invoke[AResult](
      A.describeOperation, List(Property("input", V.GoalPhaseCompletionPolicyDescriptionInput(identity).toRecord(), None)))
    private type AResult = V.GoalPhaseCompletionPolicyDescriptionResult
    def adopt(input: V.GoalPhaseCompletionPolicyAdoptionInput): V.GoalPhaseCompletionPolicyAdoptionResult =
      native.invoke[V.GoalPhaseCompletionPolicyAdoptionResult](A.adoptionOperation, List(Property("input", input.toRecord(), None)))
    def direction(description: V.GoalPhaseCompletionPolicyDescriptionResult, choice: String,
      name: String): V.GoalPhaseCompletionPolicyAdoptionInput = {
      val reference = V.WorkflowArtifactReference(name, "1")
      val input = V.GoalPhaseCompletionPolicyAdoptionInput(description.sessionId, description.target, choice,
        "Fixture-owned independent Human completion policy direction", reference)
      val evidence = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
      val prepared = take(NativeCompletionPolicyPreparation.prepareC(description, input, reference, evidence,
        "participant", "normal-development-host", native.actor.clock.instant().minusSeconds(60)))
      native.prepare(call => call.createC("host_evidence", prepared.recordIdentity, prepared.record))
      input
    }
    def observation(): RepositorySyncManagedGitObservation.Observed = {
      var result = Option.empty[RepositorySyncManagedGitObservation.Observed]
      native.prepare(call => call.observeC().map(value => { result = Some(value); () }))
      result.get
    }
    def child(identity: String): InstanceRecord = {
      var result = Option.empty[InstanceRecord]
      native.prepare(call => call.currentC(InstanceIdentity(identity)).map(value => { result = Some(value); () }))
      result.get
    }
    def childOption(identity: String): Option[InstanceRecord] = {
      var result = Option.empty[InstanceRecord]
      native.prepare(call => for {
        definition <- RepositorySyncDefinitionBinding.bindC
        storage <- WorkflowComponentStorage.bindC(ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin),
          SmWorkflowComponent.componentId, _configuration_, Vector(definition), _collection_("instances"), _collection_("continuations"))
        instances <- storage.bindInstancesC()(using call.executionContext)
        observed <- instances.loadC(InstanceIdentity(identity))(using call.executionContext)
        _ = result = observed
      } yield ())
      result
    }
    def closeDevelopment(observations: Option[GoalPhaseFindingRecording.Observations] = None):
      (String, GoalPhaseNativeRepositoryClosure.Input) = {
      val identity = native.start().sessionId
      native.submitImplementation(advance(identity)).accepted shouldBe true
      val step = advance(identity)
      step.work.get.kind shouldBe "ReviewStep"
      native.acceptStep(step)
      val phase = advance(identity)
      phase.work.get.kind shouldBe "ReviewPhase"
      identity -> acceptPhase(phase, observations)
    }
    private def _accept_review(review: V.GoalPhaseAdvanceResult,
      observations: Option[GoalPhaseFindingRecording.Observations]): (S.Request, ContextReference) = {
      var issued = Option.empty[WorkflowInteraction[S.Request, Nothing]]
      native.prepare(call => call.goalWorkC(InstanceIdentity(review.sessionId)).map(value => { issued = Some(value); () }))
      val work = issued.get
      val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request
      val input = order.input.get.value
      val reference = ContextReference("post-completion-" + input.kind + "-review", "1")
      val result = ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision,
        order.context.snapshot, TypedValue(S.resultCodec.typeIdentity,
          S.Result("accept", "Independent fixture reviewer accepts the exact selected work", Vector.empty,
            reviewObservations = observations)),
        reference, Vector.empty, ExecutionEvidence(Vector(reference), Some("fixture-independent-reviewer")))
      native.prepare(call => ThreeWorkflowExternalEvidencePorts.goalReviewBindingC(call.core, input, result)
        .flatMap(binding => call.evidenceC(NativeExternalEvidence.Purpose.GoalReview,
          ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision), reference,
          "fixture-independent-reviewer", binding)))
      native.invoke[V.GoalPhaseWorkSubmissionResult]("submitGoalPhaseWorkResult",
        take(NativeWorkflowOperationInputFixture.submissionC(result))).accepted shouldBe true
      input -> reference
    }
    def acceptStep(review: V.GoalPhaseAdvanceResult, observations: GoalPhaseFindingRecording.Observations): Unit = {
      val (input, reference) = _accept_review(review, Some(observations))
      val from = current(review.sessionId)
      val closure = GoalPhaseNativeClosure.Input(review.sessionId, from.revision.value.toString,
        input.selectedPlan.identity, input.selectedPlan.revision, input.stepIdentity.get, input.generation,
        input.originalBase, input.candidate.get, reference.identity)
      native.prepare(call => call.evidenceC(NativeExternalEvidence.Purpose.StepClosure,
        ThreeWorkflowExternalEvidencePorts.key(closure.instance, closure.sourceRevision), ContextReference("fixture-step-authority", "1"),
        "participant", take(ThreeWorkflowExternalEvidencePorts.stepClosureBindingC(call.core, closure))))
    }
    def failRecording(identity: String): V.GoalPhaseFindingRecordingSubmissionResult = {
      import GoalPhaseFindingRecording as R
      var assigned = Option.empty[WorkflowInteraction[R.Request, Nothing]]
      native.prepare(call => call.goalRecordingC(InstanceIdentity(identity)).map(value => { assigned = Some(value); () }))
      val work = assigned.get
      val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[R.Request]].request
      val reference = ContextReference("post-completion-failed-recording", "1")
      val items = order.input.get.value.items.map(item => R.ItemResult(item.sourceReference,
        item.observation.identity, "failed", Vector.empty, "Fixture worker honestly could not record this observation"))
      val result = ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision,
        order.context.snapshot, TypedValue(R.resultCodec.typeIdentity, R.Result("failed", "Actual no-write clerical failure", items)),
        reference, Vector.empty, ExecutionEvidence(Vector(reference), Some("fixture-recording-worker")))
      native.invoke[V.GoalPhaseFindingRecordingSubmissionResult]("submitGoalPhaseFindingRecording",
        take(NativeWorkflowOperationInputFixture.submissionC(result)))
    }
    def acceptPhase(review: V.GoalPhaseAdvanceResult,
      observations: Option[GoalPhaseFindingRecording.Observations] = None): GoalPhaseNativeRepositoryClosure.Input = {
      val (input, reference) = _accept_review(review, observations)
      val from = current(review.sessionId)
      GoalPhaseNativeRepositoryClosure.Input(review.sessionId, from.revision.value.toString, input.selectedPlan,
        input.originalBase, input.completed, input.candidate.get, reference.identity,
        review.sessionId + ":repository-closure:" + from.revision.value)
    }
    def authorize(input: GoalPhaseNativeRepositoryClosure.Input, effects: Boolean = true): Unit = {
      val host = recoveryHost(root)
      val selection = _repository_selection_(host.repository.input, "post-completion-child-selection", input.childInvocation)
      native.prepare(call => for {
        binding <- ThreeWorkflowExternalEvidencePorts.phaseClosureBindingC(call.core, input)
        _ <- call.evidenceC(NativeExternalEvidence.Purpose.PhaseClosure,
          ThreeWorkflowExternalEvidencePorts.key(input.instance, input.sourceRevision), ContextReference("post-completion-authority", "1"),
          "participant", binding)
        _ <- _prepare_input_(call, host.repository.input, selection, root, provisionEffects = effects)
      } yield ())
    }
    def appendIndependentDescendant(): Unit = {
      take(_run_setup_command_(Vector("-C", root.resolve("repository").toString, "-c", "user.name=Fixture",
        "-c", "user.email=fixture@example.invalid", "commit", "--allow-empty", "-m", "Separately accepted fixture descendant")))
    }
    def divergeRemote(): Unit = {
      val peer = root.resolve("peer")
      take(_run_setup_command_(Vector("clone", "--no-local", root.resolve("upstream.git").toString, peer.toString)))
      Files.writeString(peer.resolve("value.txt"), "independent remote semantic change\n")
      take(_run_setup_command_(Vector("-C", peer.toString, "add", "--", "value.txt")))
      take(_run_setup_command_(Vector("-C", peer.toString, "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid",
        "commit", "-m", "Remote fixture branch")))
      take(_run_setup_command_(Vector("-C", peer.toString, "push", root.resolve("upstream.git").toString, "HEAD:refs/heads/main")))
    }
    def advanceChild(identity: String): V.RepositorySyncAdvanceResult = read_value[V.RepositorySyncAdvanceResult](take(
      native.owner.executeOperationResponseInContext(Request.of(component = native.component.name,
        service = "RepositorySyncService", operation = "advanceRepositorySync",
        properties = NativeWorkflowOperationInputFixture.advance(identity)), native.actor)))
    def requestChildHuman(identity: String): V.RepositorySyncAdvanceResult = {
      var assigned = Option.empty[WorkflowInteraction[RepositorySyncNativeConflictInput.Request, Nothing]]
      native.prepare(call => call.repositoryWorkC(InstanceIdentity(identity),
        RepositorySyncNativeConflictContinuation.completionOperation, RepositorySyncConflictSemantic.inputCodec)
        .map(value => { assigned = Some(value); () }))
      val work = assigned.get
      val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeConflictInput.Request]].request
      val reference = ContextReference("post-completion-child-decision", "1")
      val result = ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision,
        order.context.snapshot, TypedValue(RepositorySyncConflictSemantic.resultCodec.typeIdentity,
          RepositorySyncConflictSemantic.Result("decision-needed", Vector.empty, "Fixture conflict requires actual Human direction")),
        reference, Vector.empty, ExecutionEvidence(Vector(reference), Some("fixture-conflict-worker")))
      val response = take(native.owner.executeOperationResponseInContext(Request.of(component = native.component.name,
        service = "RepositorySyncService", operation = "submitRepositorySyncWorkResult",
        properties = take(NativeWorkflowOperationInputFixture.submissionC(result))), native.actor))
      read_value[V.RepositorySyncConflictSubmissionResult](response).accepted shouldBe true
      advanceChild(identity)
    }
  }
}

/** Explicit normal host configuration for real Test stdout and managed lifetime.
  * @since   Oct. 23, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
private[admission] trait GoalPhasePostCompletionEvidenceFixture extends GoalPhasePostCompletionSyncFixture {
  def prepareInstalled(root: Path): Unit = {
    NativeWorkflowCliFixture.journalReader(root, _recovery_plan)
    ()
  }
  override protected def post_completion_parameters(root: Path): Map[String, String] = {
    Files.createDirectories(root.resolve("repository"))
    val archive = Files.createDirectories(root.resolve("archive/logs")).getParent.toRealPath()
    val collection = EntityCollectionId("smworkflow", "cli", "common_evidence").print
    val declared = Json.obj("schemaVersion" -> Json.fromString(CommonEvidence.schemaVersion),
      "storeName" -> Json.fromString("workflow"), "collection" -> Json.fromString(collection),
      "hostIdentity" -> Json.fromString("spec-host"), "workspaceIdentity" -> Json.fromString("evidence-archive"),
      "directory" -> Json.fromString("logs"))
    val journal = NativeWorkflowCliFixture.JournalHost(DevelopmentJournal.Policy("workflow",
      DataStore.CollectionId.EntityStore(EntityCollectionId("smworkflow", "cli", "development_journal")))).values
    val storage = io.circe.parser.parse(journal("textus.subsystem.storage.plan")).toOption.get.mapObject(fields =>
      fields.add("records", Json.fromValues(fields("records").get.asArray.get :+ Json.obj(
        "componentId" -> Json.fromString(SmWorkflowComponent.componentId.name),
        "storeName" -> Json.fromString("workflow"), "collection" -> Json.fromString(collection)))))
    val process = process_plan(root, "Goal", recoveryHost(root).goal).mapObject(fields => fields
      .add("workspaces", Json.fromValues(fields("workspaces").get.asArray.get :+ Json.obj(
        "identity" -> Json.fromString("evidence-archive"), "root" -> Json.fromString(archive.toString))))
      .add("workspaceGrants", Json.fromValues(fields("workspaceGrants").get.asArray.get :+ Json.obj(
        "componentId" -> Json.fromString(SmWorkflowComponent.componentId.name),
        "workspaceId" -> Json.fromString("evidence-archive"), "access" -> Json.fromString("manage")))))
    super.post_completion_parameters(root) ++ journal ++ Map(CommonEvidence.declaration.name -> declared.noSpaces,
      "textus.subsystem.storage.plan" -> storage.noSpaces,
      "textus.subsystem.process-execution.plan" -> process.noSpaces)
  }
}
