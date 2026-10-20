package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import org.simplemodeling.textus.smworkflow.host.NativeWorkflowHostExecution
import SplitPhaseExplicitPartitionFixture.take
import RepositorySyncNativeMergeRepair as M
import RepositorySyncNativeMergeReview as R
import RepositorySyncNativeProgressStore as N

/** Normal production Operations, SQLite and real owned Git. Setup creates
  * specification-owned inputs/evidence and reads actual native records. This
  * is no genuine AI review, live task repair or Phase acceptance evidence.
  * @since   Oct. 15, 2026
  * @version Oct. 20, 2026
  * @author  ASAMI, Tomoharu
  */
final class RepositorySyncNativeMergeRepairContinuationSpec extends AnyWordSpec with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  private val _replacement = "repaired shared line\n"

  private def _repair_mode(root: Path): String =
    Vector("failed", "indeterminate", "intent-only").find(mode => root.getFileName.toString.startsWith(mode + "-"))
      .getOrElse("connected")
  override protected def _prepare_sbt_build_(root: Path, scenario: String): Unit =
    if (!Files.exists(root.resolve("repository/build.sbt"))) TypedSbtHostPlanFixture.buildRepair(root, _repair_mode(root))
  override protected def _sbt_execution_timeout_millis_(root: Path, scenario: String): Long =
    if (_repair_mode(root) == "indeterminate") 90000L else 180000L

  private final class Episode(val root: Path, val input: V.RepositorySyncStartInput,
    call: (String, List[Property]) => Consequence[OperationResponse], prepare: (Setup => Consequence[Unit]) => Unit,
    existingIdentity: Option[InstanceIdentity] = None,
    execution: Option[(WorkflowExecutionProfileMapping, GoalPhaseNativeExecution.Origin)] = None) {
    private var _identity: Option[InstanceIdentity] = existingIdentity
    def identity: InstanceIdentity = _identity.get
    def text: String = Files.readString(root.resolve("repository/shared.txt"))
    def invoke[A: org.goldenport.convert.ValueReader](operation: String, fields: List[Property]): A =
      read_value[A](take(call(operation, fields)))
    def advance(): V.RepositorySyncAdvanceResult = invoke("advanceRepositorySync", NativeWorkflowOperationInputFixture.advance(identity.value))
    def advanceC(): Consequence[OperationResponse] = call("advanceRepositorySync", NativeWorkflowOperationInputFixture.advance(identity.value))
    def current(): InstanceRecord = {
      var actual = Option.empty[InstanceRecord]
      prepare(_.currentC(identity).map(value => { actual = Some(value); () }))
      actual.get
    }
    def work[A](operation: StateMachineOperationIdentity, codec: WorkflowResultJsonV1.PayloadCodec[A]): WorkflowInteraction[A, Nothing] = {
      var actual = Option.empty[WorkflowInteraction[A, Nothing]]
      prepare(_.repositoryWorkC(identity, operation, codec).map(value => { actual = Some(value); () }))
      actual.get
    }
    def result[A, B <: NativeWorkflowResultInput.Value](work: WorkflowInteraction[A, Nothing], value: B,
      reference: String, worker: String = "specification-worker"): ContinuationResult[B] = {
      val assigned = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[A]]
      val order = assigned.request
      val ref = ContextReference(reference, order.expectedRevision.value)
      val evidence = if (assigned.requirement.disposition.isEmpty) ExecutionEvidence(Vector(ref), Some(worker)) else {
        Given("E6: the actual mapped pending Repository issue and its original canonical host snapshot; all executor observations are specification-owned")
        val (mapping, origin) = execution.get
        val before = current().revision
        When("normal pending Advance supplies its public context for the host prepare record and completion bridge")
        val issued = advance().issuedWorkContext.get
        val prepared = take(NativeWorkflowHostExecution.prepareC(issued, mapping, origin.current))
        val observed = RepositoryExecutionFixture.evidence(prepared, ref, worker)
        Then("the bridge consumes the actual assignment and preserves the pending revision while supplying only generic execution evidence")
        issued.continuationId shouldBe order.continuationId.value
        issued.expectedRevision shouldBe order.expectedRevision.value
        prepared.session.requirement shouldBe assigned.requirement
        prepared.session.mappingIdentity shouldBe mapping.identity
        prepared.session.mappingRevision shouldBe mapping.revision
        observed.execution.get.originContextIdentity shouldBe origin.current.contextIdentity
        current().revision shouldBe before
        observed
      }
      ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision, order.context.snapshot,
        TypedValue(order.resultType.get.value, value), ref, Vector.empty, evidence)
    }
    def submitC[B <: NativeWorkflowResultInput.Value](operation: String, result: ContinuationResult[B]): Consequence[OperationResponse] =
      call(operation, take(NativeWorkflowOperationInputFixture.submissionC(result)))
    def submitRepair(result: ContinuationResult[M.Result]): V.RepositorySyncNativeMergeRepairSubmissionResult =
      read_value(take(submitC("submitNativeMergeRepair", result)))
    def stage(key: String): Option[N.Stage] = {
      var actual = Option.empty[N.Stage]
      prepare(_.nativeStageC(input, identity, root, key).map(value => { actual = value; () }))
      actual
    }
    private def _storage_c(call: Setup): Consequence[WorkflowComponentStorage] = for {
      definition <- RepositorySyncDefinitionBinding.bindC
      storage <- WorkflowComponentStorage.bindC(ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin),
        SmWorkflowComponent.componentId, _configuration_, Vector(definition), _collection_("instances"), _collection_("continuations"))
    } yield storage
    def savedWork[A](assigned: WorkflowInteraction[A, Nothing], operation: StateMachineOperationIdentity,
      codec: WorkflowResultJsonV1.PayloadCodec[A]): Option[WorkflowInteraction[A, Nothing]] = {
      var actual = Option.empty[WorkflowInteraction[A, Nothing]]
      val id = assigned.current.asInstanceOf[WorkflowContinuation.WorkOrder[A]].request.continuationId
      prepare(call => _storage_c(call).flatMap(_.bindIssuedC(operation, codec)).flatMap(
        _.loadC(id)(using call.executionContext)).map(value => { actual = value; () }))
      actual
    }
    def savedCompletion[A, B](assigned: WorkflowInteraction[A, Nothing], operation: StateMachineOperationIdentity,
      request: WorkflowResultJsonV1.PayloadCodec[A], result: WorkflowResultJsonV1.PayloadCodec[B],
      closing: StateMachineResultTypeReference): Option[WorkflowCompletionRecordJsonV1.Record[B]] = {
      var actual = Option.empty[WorkflowCompletionRecordJsonV1.Record[B]]
      val id = assigned.current.asInstanceOf[WorkflowContinuation.WorkOrder[A]].request.continuationId
      prepare(call => _storage_c(call).flatMap(_.bindCompletionC(operation, request, result, closing,
        (_, _) => Consequence.success(Vector.empty))).flatMap(_.loadRetainedC(id)(using call.executionContext))
        .map(value => { actual = value.map(_.completion); () }))
      actual
    }
    def reviewEvidence(work: WorkflowInteraction[R.Request, Nothing], result: ContinuationResult[R.Result]): Unit =
      prepare(_.reviewEvidenceC(work.current.asInstanceOf[WorkflowContinuation.WorkOrder[R.Request]].request.input.get.value, result))
    def originalReview(): WorkflowInteraction[R.Request, Nothing] = {
      Given("the real tracked repair build and an original conflict whose resolved text must pass ordinary SBT")
      val selection = _repository_selection_(input, "specification-repair-selection", "specification-repair-start")
      prepare(call => call.setupC(_setup_commands_(root, "Conflict").size, Some(root), "Conflict")
        .flatMap(_ => _prepare_input_(call, input, selection, root, merged = true, provisionEffects = false)))
      val started = invoke[V.RepositorySyncStartResult]("startRepositorySync", NativeWorkflowOperationInputFixture.repository(input))
      _identity = Some(InstanceIdentity(started.sessionId))
      val conflictresponse = advance()
      conflictresponse.kind shouldBe "Request"
      val conflict = work(RepositorySyncNativeConflictContinuation.completionOperation, RepositorySyncConflictSemantic.inputCodec)
      val selected = conflict.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeConflictInput.Request]].request.input.get.value
      val resolved = result(conflict, RepositorySyncConflictSemantic.Result("resolved", selected.conflicts.map(c =>
        RepositorySyncConflictSemantic.Resolution(c.identity, "resolved shared line\n")), "Specification-owned conflict resolution"), "specification-conflict")
      read_value[V.RepositorySyncConflictSubmissionResult](take(submitC("submitRepositorySyncWorkResult", resolved))).accepted shouldBe true
      When("owning Advance validates the actual original resolved candidate before independent review")
      val response = advance()
      Then("the original resolved candidate passes actual ordinary SBT with its checkout cwd and external PID")
      response.kind shouldBe "Request"
      response.review.get.changedPaths shouldBe Vector("shared.txt")
      _assert_product_check_(root, response.review.get.checks.last.stdout)
      Files.readString(root.resolve("sbt-executions.txt")).linesIterator.size shouldBe 1
      work(RepositorySyncNativeMergeReviewContinuation.completionOperation, R.inputCodec)
    }
    def completeRepairReview(review: WorkflowInteraction[R.Request, Nothing],
      paths: Vector[String] = Vector("shared.txt")): ContinuationResult[R.Result] = {
      val submitted = result(review, R.Result("repair", "Specification review found an affected text defect",
        Vector(R.Finding("fixture-repair", paths, "Use the intended shared explanation"))),
        "specification-original-review", "specification-reviewer")
      reviewEvidence(review, submitted)
      read_value[V.RepositorySyncNativeMergeReviewSubmissionResult](take(submitC("submitNativeMergeReview", submitted))).accepted shouldBe true
      submitted
    }
    def repairWork(): WorkflowInteraction[M.Request, Nothing] = work(RepositorySyncNativeMergeRepairContinuation.completionOperation, M.inputCodec)
    def repaired(work: WorkflowInteraction[M.Request, Nothing]): ContinuationResult[M.Result] = {
      val input = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[M.Request]].request.input.get.value
      result(work, M.Result("repaired", "Specification-owned affected-file replacement",
        input.files.map(file => M.File(file.path, _replacement))), "specification-repair")
    }
  }

  private def _with_episode(mode: String = "connected", hostparameters: Map[String, String] = Map.empty)(task: Episode => Unit): Unit = {
    val owned = Files.createDirectories(Path.of("target/sm-workflow/work/native-merge-repair").toAbsolutePath.normalize)
    val root = Files.createTempDirectory(owned, mode + "-")
    val input = ThreeWorkflowNormalHostFixture.definition(root).repository.input
    _with_runtime_(root, "Conflict", hostParameters = hostparameters) { (owner, component, actor, prepare) =>
      val call = (operation: String, fields: List[Property]) => owner.executeOperationResponseInContext(
        Request.of(component = component.name, service = "RepositorySyncService", operation = operation, properties = fields), actor)
      val execution = if (!hostparameters.contains(RepositorySyncNativeExecution.declaration.name)) None else Some((
        take(WorkflowExecutionProfileParameters.resolveC(component.initializationParameters)).get,
        take(GoalPhaseNativeExecution.resolveOriginC(component.initializationParameters)).get))
      task(new Episode(root, input, call, prepare, execution = execution))
    }
  }

  "Mapped Repository repair" should afterWord("in spec:repository-sync-native-execution, example:E6, rules:R1,R2,R3,R4,R5") {
    "complete mapped repository execution repair through separate Submit Advance fresh SBT and newly mapped review" in {
      Given("an owned real conflict, normal SQLite Operations, paired review/repair classifications with explicit conflict CODING and specification-owned canonical execution observations")
      _with_episode(hostparameters = RepositoryExecutionFixture.parameters(
        conflict = Some(RepositoryExecutionFixture.classifications.repair))) { episode =>
        When("normal Start conflict completion and owning Advance build and actually SBT-validate the original resolved candidate")
        val original = episode.originalReview()
        val oldorder = original.current.asInstanceOf[WorkflowContinuation.WorkOrder[R.Request]]
        val oldinput = oldorder.request.input.get.value
        val oldvalidated = episode.stage("merge-validated").get
        Then("the actual first review is ReviewStandard with required separately scoped execution")
        oldorder.requirement.reasoningClass shouldBe Some(ReasoningClassReference("ReviewStandard"))
        oldorder.requirement.disposition shouldBe Some(ExecutionDisposition(ExecutionPlacement.Delegated, ExecutionIndependence.Required,
          RepositoryExecutionFixture.origin.current.contextIdentity))
        When("the actual independently evidenced mapped review is submitted with a repair finding")
        val reviewresult = episode.completeRepairReview(original)
        val reviewed = episode.current()
        val namespace = "repair-" + reviewed.revision.value
        Then("only the original review completion is saved before repair issuance")
        reviewed.currentProgression shouldBe Some(ProgressionReference("sync.ReviewRepairRequired"))
        reviewed.suspension shouldBe None
        When("separate owning Advance issues the genuine affected-file repair and pending Advance displays it again")
        val response = episode.advance()
        val assigned = episode.repairWork()
        val order = assigned.current.asInstanceOf[WorkflowContinuation.WorkOrder[M.Request]]
        val revision = episode.current().revision
        val pending = episode.advance()
        Then("actual stored and public CodingProgrammingStandard repair selects current INLINE and retains its original findings scope and issue")
        response.kind shouldBe "Request"
        response.issuedWorkContext.get.thinkingMode shouldBe "implementation"
        response.issuedWorkContext.get.executionRequirement.getString("reasoningClass") shouldBe Some("CodingProgrammingStandard")
        response.issuedWorkContext.get.continuationId shouldBe order.request.continuationId.value
        response.issuedWorkContext.get.expectedRevision shouldBe order.request.expectedRevision.value
        order.requirement.reasoningClass shouldBe Some(ReasoningClassReference("CodingProgrammingStandard"))
        order.requirement.disposition shouldBe Some(ExecutionDisposition(ExecutionPlacement.Inline, ExecutionIndependence.Optional,
          RepositoryExecutionFixture.origin.current.contextIdentity))
        order.request.input.get.value.findings shouldBe reviewresult.result.value.findings
        order.request.input.get.value.files.map(_.text) shouldBe Vector("resolved shared line\n")
        pending.issuedWorkContext shouldBe response.issuedWorkContext
        episode.repairWork().current.asInstanceOf[WorkflowContinuation.WorkOrder[M.Request]].requirement shouldBe order.requirement
        episode.current().revision shouldBe revision
        val before = episode.text
        val proposed = episode.repaired(assigned)
        Given("the actual repair result with a bridge observation and variants missing or contradicting it")
        When("normal repair Submit consumes missing and mismatched observations")
        val bad = Vector(proposed.copy(evidence = proposed.evidence.copy(execution = None)), proposed.copy(evidence = proposed.evidence.copy(
          execution = proposed.evidence.execution.map(_.copy(executorContextIdentity = "foreign-inline-executor")))))
        val refused = bad.map(episode.submitC("submitNativeMergeRepair", _))
        Then("generic observation admission refuses both without appending or materializing")
        refused.foreach(_.isSuccess shouldBe false)
        episode.current().revision shouldBe revision
        episode.text shouldBe before
        When("semantic repair Submit admits only the exact selected observed proposal")
        episode.submitRepair(proposed).accepted shouldBe true
        Then("Submit saves the proposal without file index validation or next-review effects")
        episode.current().currentProgression shouldBe Some(ProgressionReference("sync.RepairProposalAdmitted"))
        episode.text shouldBe before
        episode.stage(N.candidateKey(namespace, "pending-merge")) shouldBe None
        Files.readString(episode.root.resolve("sbt-executions.txt")).linesIterator.size shouldBe 1
        When("a separate owning Advance applies stages and freshly SBT-validates the candidate before issuing another independent review")
        val responsefresh = episode.advance()
        val fresh = episode.work(RepositorySyncNativeMergeReviewContinuation.completionOperation, R.inputCodec)
        val freshorder = fresh.current.asInstanceOf[WorkflowContinuation.WorkOrder[R.Request]]
        val freshinput = freshorder.request.input.get.value
        val candidate = episode.stage(N.candidateKey(namespace, "pending-merge")).get
        val validated = episode.stage(N.candidateKey(namespace, "merge-validated")).get
        Then("the new full ReviewStandard belongs to the changed index and real fresh check while original issue and completion records remain readable")
        episode.text shouldBe _replacement
        responsefresh.kind shouldBe "Request"
        responsefresh.repair shouldBe None
        responsefresh.issuedWorkContext.get.thinkingMode shouldBe "review"
        responsefresh.issuedWorkContext.get.executionRequirement.getString("reasoningClass") shouldBe Some("ReviewStandard")
        freshorder.requirement.reasoningClass shouldBe Some(ReasoningClassReference("ReviewStandard"))
        freshorder.requirement.disposition shouldBe oldorder.requirement.disposition
        freshorder.request.continuationId should not be oldorder.request.continuationId
        freshinput.indexTree should not be oldinput.indexTree
        freshinput.indexTree shouldBe candidate.payload.indexTree.get
        freshinput.validatedRevision shouldBe validated.record.revision.value.toString
        freshinput.localDiff should include(_replacement.trim)
        freshinput.remoteDiff should include(_replacement.trim)
        validated.payload.validation.get.sourceRevision shouldBe candidate.record.revision.value.toString
        validated.payload.validation.get.receipts.map(_.identity) shouldBe Vector("index-local", "index-remote", "test")
        _assert_product_check_(episode.root, validated.payload.validation.get.receipts.last.stdout)
        Files.readString(episode.root.resolve("sbt-executions.txt")).linesIterator.size shouldBe 2
        episode.stage("merge-validated").get.record.revision shouldBe oldvalidated.record.revision
        episode.savedWork(original, RepositorySyncNativeMergeReviewContinuation.completionOperation, R.inputCodec).get
          .current.asInstanceOf[WorkflowContinuation.WorkOrder[R.Request]].requirement shouldBe oldorder.requirement
        episode.savedWork(assigned, RepositorySyncNativeMergeRepairContinuation.completionOperation, M.inputCodec).get
          .current.asInstanceOf[WorkflowContinuation.WorkOrder[M.Request]].requirement shouldBe order.requirement
        episode.savedCompletion(original, RepositorySyncNativeMergeReviewContinuation.completionOperation, R.inputCodec,
          R.resultCodec, RepositorySyncNativeMergeReviewContinuation.closingType).get.result.result.value.decision shouldBe "repair"
        episode.savedCompletion(assigned, RepositorySyncNativeMergeRepairContinuation.completionOperation, M.inputCodec,
          M.resultCodec, RepositorySyncNativeMergeRepairContinuation.closingType).get.result.result.value shouldBe proposed.result.value
      }
    }
  }

  "C1 connected actual repair candidate" should afterWord(
    "in spec:repository-sync-native-merge-repair, example:C1, rules:R1,R2,R3,R5,R6"
  ) {
    "issue, submit, apply, stage and freshly validate before a new independent full review" in {
      Given("a production normal host, SQLite, real owned conflict and specification-owned independent review evidence")
      _with_episode() { episode =>
        val original = episode.originalReview()
        val oldinput = original.current.asInstanceOf[WorkflowContinuation.WorkOrder[R.Request]].request.input.get.value
        val oldvalidated = episode.stage("merge-validated").get
        val reviewresult = episode.completeRepairReview(original)
        val completed = episode.current()
        val namespace = "repair-" + completed.revision.value
        When("normal Advance issues the affected repair from that actual paired review completion")
        val response = episode.advance()
        val assigned = episode.repairWork()
        val order = assigned.current.asInstanceOf[WorkflowContinuation.WorkOrder[M.Request]]
        val input = order.request.input.get.value
        Then("the Request and its real native issued context use implementation mode and exact original findings/texts")
        response.kind shouldBe "Request"
        response.resultType shouldBe Some("RepositorySyncNativeMergeRepairResult")
        response.repair.get.files.map(_.path) shouldBe Vector("shared.txt")
        input.findings shouldBe reviewresult.result.value.findings
        input.files.map(_.text) shouldBe Vector("resolved shared line\n")
        order.request.continuationId.value shouldBe episode.identity.value + ":native-repair:" + completed.revision.value
        order.requirement.reviewRequired shouldBe false
        response.issuedWorkContext.get.thinkingMode shouldBe "implementation"
        response.issuedWorkContext.get.continuationId shouldBe order.request.continuationId.value
        response.issuedWorkContext.get.expectedRevision shouldBe order.request.expectedRevision.value
        response.review shouldBe None
        val forwarded = NativeWorkflowOperationOutput.goalChild("specification-owned-outer", response)
        forwarded.sessionId shouldBe "specification-owned-outer"
        forwarded.submitSessionId shouldBe Some(episode.identity.value)
        forwarded.repositoryRepair shouldBe response.repair
        forwarded.issuedWorkContext.get.continuationId shouldBe order.request.continuationId.value
        When("pending Advance displays the genuine retained issue and Submit saves only its replacement proposal")
        episode.advance().repair.get.files.map(_.text) shouldBe input.files.map(_.text)
        val before = episode.text
        val submitted = episode.repaired(assigned)
        episode.submitRepair(submitted).accepted shouldBe true
        Then("Submit performs no file, index, validation or subsequent review effect")
        episode.text shouldBe before
        episode.current().currentProgression shouldBe Some(ProgressionReference("sync.RepairProposalAdmitted"))
        episode.stage(N.candidateKey(namespace, "pending-merge")) shouldBe None
        When("a separate owning Advance materializes, stages and executes the new candidate's actual checks")
        val fresh = episode.advance()
        val pending = episode.stage(N.candidateKey(namespace, "pending-merge")).get
        val validated = episode.stage(N.candidateKey(namespace, "merge-validated")).get
        val newreview = episode.work(RepositorySyncNativeMergeReviewContinuation.completionOperation, R.inputCodec)
        val neworder = newreview.current.asInstanceOf[WorkflowContinuation.WorkOrder[R.Request]]
        val newinput = neworder.request.input.get.value
        Then("the current full review belongs to the changed index and actual new SBT cwd/pass/PID while original records remain readable")
        episode.text shouldBe _replacement
        fresh.kind shouldBe "Request"
        fresh.repair shouldBe None
        newinput.indexTree should not be oldinput.indexTree
        newinput.indexTree shouldBe pending.payload.indexTree.get
        newinput.localDiff should include(_replacement.trim)
        newinput.remoteDiff should include(_replacement.trim)
        newinput.validatedRevision shouldBe validated.record.revision.value.toString
        validated.payload.validation.get.sourceRevision shouldBe pending.record.revision.value.toString
        validated.payload.validation.get.receipts.map(_.identity) shouldBe Vector("index-local", "index-remote", "test")
        _assert_product_check_(episode.root, validated.payload.validation.get.receipts.last.stdout)
        Files.readString(episode.root.resolve("sbt-executions.txt")).linesIterator.size shouldBe 2
        Files.exists(episode.root.resolve("repaired-check-entered.txt")) shouldBe false
        neworder.request.continuationId.value shouldBe episode.identity.value + ":native-review:" + validated.record.revision.value
        neworder.requirement.reviewRequired shouldBe true
        episode.stage("merge-validated").get.record.revision shouldBe oldvalidated.record.revision
        episode.savedWork(original, RepositorySyncNativeMergeReviewContinuation.completionOperation, R.inputCodec).get
          .current.asInstanceOf[WorkflowContinuation.WorkOrder[R.Request]].request.input.get.value.indexTree shouldBe oldinput.indexTree
        episode.savedCompletion(original, RepositorySyncNativeMergeReviewContinuation.completionOperation, R.inputCodec,
          R.resultCodec, RepositorySyncNativeMergeReviewContinuation.closingType).get.result.result.value.decision shouldBe "repair"
        episode.savedCompletion(assigned, RepositorySyncNativeMergeRepairContinuation.completionOperation, M.inputCodec,
          M.resultCodec, RepositorySyncNativeMergeRepairContinuation.closingType).get.result.result.value shouldBe submitted.result.value
        episode.stage(N.candidateKey(namespace, "stage-result-0")) should not be empty
      }
    }
  }

  "C2 actual assignment result boundaries" should afterWord(
    "in spec:repository-sync-native-merge-repair, example:C2, rules:R1,R2,R3,R4,R6"
  ) {
    "refuse unissued, crossed, foreign, incomplete, duplicate and no-op proposals without effects" in {
      Given("an original full review followed by its genuine affected-file repair issue")
      _with_episode() { episode =>
        val review = episode.originalReview()
        val originalorder = review.current.asInstanceOf[WorkflowContinuation.WorkOrder[R.Request]].request
        val unissued = episode.result(review, M.Result("repaired", "No repair assignment", Vector(M.File("shared.txt", _replacement))), "unissued")
        When("repair Submit is attempted against the pending review instead of a repair assignment")
        val refused = episode.submitC("submitNativeMergeRepair", unissued)
        Then("no repair is admitted")
        refused.isSuccess shouldBe false
        episode.completeRepairReview(review)
        episode.advance()
        val assigned = episode.repairWork()
        val input = assigned.current.asInstanceOf[WorkflowContinuation.WorkOrder[M.Request]].request.input.get.value
        val good = episode.repaired(assigned)
        val before = episode.text
        val revision = episode.current().revision
        val invalid = Vector(good.copy(result = TypedValue(good.result.typeIdentity, good.result.value.copy(replacements = Vector.empty))),
          good.copy(result = TypedValue(good.result.typeIdentity, good.result.value.copy(replacements = input.files))),
          good.copy(result = TypedValue(good.result.typeIdentity, good.result.value.copy(replacements = Vector(M.File("foreign.txt", _replacement))))),
          good.copy(result = TypedValue(good.result.typeIdentity, good.result.value.copy(replacements = good.result.value.replacements ++ good.result.value.replacements))),
          good.copy(result = TypedValue(good.result.typeIdentity, good.result.value.copy(decision = "decision-needed"))))
        When("normal Submit consumes each invalid current repair result and a foreign execution session")
        val actual = invalid.map(episode.submitC("submitNativeMergeRepair", _))
        val foreign = good.copy(handle = good.handle.copy(instanceIdentity = InstanceIdentity("foreign-repair-instance")))
        val crossed = episode.submitC("submitNativeMergeReview", good)
        Then("scope, coverage and target refusals precede every file effect or native append")
        actual.foreach(_.isSuccess shouldBe false)
        episode.submitC("submitNativeMergeRepair", foreign).isSuccess shouldBe false
        crossed.isSuccess shouldBe false
        episode.current().revision shouldBe revision
        episode.text shouldBe before
        When("one valid proposal is admitted and submitted again after its suspension is closed")
        episode.submitRepair(good).accepted shouldBe true
        val duplicate = episode.submitC("submitNativeMergeRepair", good)
        Then("a duplicate cannot replay its issue or materialize effects")
        duplicate.isSuccess shouldBe false
        episode.text shouldBe before
      }
    }
    "keep decision-needed as a nonterminal Wait requiring actual Human handling" in {
      Given("a genuine repair assignment with an unresolved semantic decision")
      _with_episode() { episode =>
        episode.completeRepairReview(episode.originalReview())
        episode.advance()
        val assigned = episode.repairWork()
        val before = episode.text
        When("Submit admits decision-needed with rationale and no replacements, followed by explicit Advance")
        episode.submitRepair(episode.result(assigned, M.Result("decision-needed", "An actual policy decision is required", Vector.empty), "decision-needed"))
          .accepted shouldBe true
        val response = episode.advance()
        Then("the result stays nonterminal and grants neither consent nor file effects")
        response.kind shouldBe "Wait"
        response.condition shouldBe Some("RepairDecisionRequired")
        response.human shouldBe None
        episode.current().lifecycle shouldBe Lifecycle.Active
        episode.text shouldBe before
      }
    }
    "refuse absent capture and intervening file edits without overwrite or apply replay" in {
      for (whenEdited <- Vector("before-issue", "after-submit")) {
        Given("a genuine repair review and an absent or independently edited assigned path")
        _with_episode() { episode =>
          episode.completeRepairReview(episode.originalReview())
          if (whenEdited == "before-issue") Files.delete(episode.root.resolve("repository/shared.txt"))
          else {
            episode.advance()
            episode.submitRepair(episode.repaired(episode.repairWork()))
            Files.writeString(episode.root.resolve("repository/shared.txt"), "independent user edit\n")
          }
          When("owning Advance consumes the actual pending application preconditions")
          val actual = episode.advanceC()
          Then("it stops before overwriting or issuing a new review")
          actual.isSuccess shouldBe false
          episode.current().suspension shouldBe None
          if (whenEdited == "before-issue") Files.exists(episode.root.resolve("repository/shared.txt")) shouldBe false
          else episode.text shouldBe "independent user edit\n"
        }
      }
    }
    "refuse an actual changed-path finding outside the currently explicit text policy before capture" in {
      Given("a genuine completed repair review whose affected path was reviewed in the original normal host")
      var saved = Option.empty[(Path, V.RepositorySyncStartInput, InstanceIdentity, String)]
      _with_episode() { episode =>
        episode.completeRepairReview(episode.originalReview())
        saved = Some((episode.root, episode.input, episode.identity, episode.current().revision.value.toString))
      }
      val (root, input, identity, revision) = saved.get
      val original = ThreeWorkflowNormalHostFixture.definition(root)
      val selected = original.copy(target = original.target.copy(root = root.resolve("repository").toString),
        repository = original.repository.copy(conflicts = RepositorySyncNativeConflictInput.Policy(Set("unconfigured.txt"))))
      When("a normal host reopens the real stored completion under an explicit nonempty policy that does not allow that path")
      _with_runtime_(root, "Conflict", hostParameters = Map(
        "sm-workflow.host.definition" -> ThreeWorkflowHostParameters.encode(selected).noSpaces)) {
        (owner, component, actor, prepare) =>
          val call = (operation: String, fields: List[Property]) => owner.executeOperationResponseInContext(
            Request.of(component = component.name, service = "RepositorySyncService", operation = operation, properties = fields), actor)
          val episode = new Episode(root, input, call, prepare, Some(identity))
          val actual = episode.advanceC()
          Then("the actual finding remains retained and no capture, repair issue or file effect is admitted")
          actual.isSuccess shouldBe false
          episode.current().revision.value.toString shouldBe revision
          episode.current().suspension shouldBe None
          episode.current().currentProgression shouldBe Some(ProgressionReference("sync.ReviewRepairRequired"))
          episode.text shouldBe "resolved shared line\n"
      }
    }
    "refuse review findings outside actual changed-path coverage before repair issuance" in {
      Given("the real independently reviewed shared path and a proposed foreign finding")
      _with_episode() { episode =>
        val review = episode.originalReview()
        val foreign = episode.result(review, R.Result("repair", "Foreign finding", Vector(
          R.Finding("foreign", Vector("unconfigured.txt"), "Outside the original review"))), "foreign-finding", "specification-reviewer")
        episode.reviewEvidence(review, foreign)
        When("the normal independent review completion consumes its finding coverage")
        val actual = episode.submitC("submitNativeMergeReview", foreign)
        Then("foreign findings cannot supply a repair capture or replace the genuine current issue")
        actual.isSuccess shouldBe false
        episode.current().suspension should not be empty
        episode.text shouldBe "resolved shared line\n"
      }
    }
  }

  "C3 fresh independent review and separate effect authority" should afterWord(
    "in spec:repository-sync-native-merge-repair, example:C3, rules:R1,R6"
  ) {
    "refuse old or missing reviewer evidence and require separate current convergence grants after fresh acceptance" in {
      Given("a genuine repaired candidate whose original review evidence is retained separately")
      _with_episode() { episode =>
        val original = episode.originalReview()
        val originalresult = episode.completeRepairReview(original)
        episode.advance()
        episode.submitRepair(episode.repaired(episode.repairWork()))
        episode.advance()
        val fresh = episode.work(RepositorySyncNativeMergeReviewContinuation.completionOperation, R.inputCodec)
        val accepted = episode.result(fresh, R.Result("accept", "Specification-owned fresh full review", Vector.empty),
          "specification-fresh-review", "specification-independent-reviewer")
        val before = episode.current().revision
        When("the new review attempts acceptance with missing evidence and then the old review's evidence reference")
        val missing = episode.submitC("submitNativeMergeReview", accepted)
        val old = episode.submitC("submitNativeMergeReview", accepted.copy(resultReference = originalresult.resultReference,
          evidence = originalresult.evidence))
        Then("neither evidence source accepts the new candidate or appends its result")
        missing.isSuccess shouldBe false
        old.isSuccess shouldBe false
        episode.current().revision shouldBe before
        When("fresh separately prepared specification review evidence admits only this actual new review")
        episode.reviewEvidence(fresh, accepted)
        read_value[V.RepositorySyncNativeMergeReviewSubmissionResult](take(episode.submitC("submitNativeMergeReview", accepted))).accepted shouldBe true
        Then("fresh acceptance remains before current separately authorized convergence effects")
        episode.current().currentProgression shouldBe Some(ProgressionReference("sync.ReviewAccepted"))
        val beforeadvance = episode.current().revision
        When("Advance attempts convergence without any specification effect grant")
        val response = episode.advanceC()
        Then("no commit/push or Terminal is inferred from review acceptance")
        response.isSuccess shouldBe false
        episode.current().revision shouldBe beforeadvance
        episode.stage("commit-intent") shouldBe None
        episode.text shouldBe _replacement
      }
    }
  }

  "C4 changed candidate check uncertainty" should afterWord(
    "in spec:repository-sync-native-merge-repair, example:C4, rules:R6"
  ) {
    for (mode <- Vector("failed", "indeterminate", "intent-only")) {
      s"retain the actual $mode new check and never replay it or issue a new review" in {
        Given("a tracked ordinary SBT build whose actual resolved shared text passes and whose repaired text selects " + mode)
        _with_episode(mode) { episode =>
          episode.completeRepairReview(episode.originalReview())
          val revision = episode.current().revision
          val namespace = "repair-" + revision.value
          episode.advance()
          episode.submitRepair(episode.repaired(episode.repairWork()))
          When("separate Advance applies the replacement and executes the fresh selected check")
          val actual = episode.advanceC()
          val marker = episode.root.resolve("repaired-check-entered.txt")
          val entered = Files.readString(marker)
          val executions = Files.readString(episode.root.resolve("sbt-executions.txt"))
          val result = episode.stage(N.candidateKey(namespace, "validation-result-2"))
          Then("two actual external SBT entries retain the real failure, timeout or NUL-refused intent before any new review")
          val pids = executions.linesIterator.map(_.toLong).toVector
          pids.size shouldBe 2
          pids.distinct shouldBe pids
          pids.last shouldBe entered.toLong
          pids.foreach { pid =>
            pid should be > 0L
            pid should not be ProcessHandle.current().pid()
          }
          val checkintent = episode.stage(N.candidateKey(namespace, "validation-intent-2")).get
          checkintent.payload.validation.get.receipts.map(_.identity) shouldBe Vector("index-local", "index-remote")
          if (mode == "intent-only") {
            actual.isSuccess shouldBe false
            actual.toString should include("validation capture is not ordinary text")
            result shouldBe None
            episode.stage(N.candidateKey(namespace, "validation-intent-2")) should not be empty
          } else {
            val output = read_value[V.RepositorySyncAdvanceResult](take(actual))
            output.kind shouldBe "Wait"
            output.review shouldBe None
          }
          episode.stage(N.candidateKey(namespace, "merge-validated")) shouldBe None
          if (mode != "intent-only") {
            val receipts = result.get.payload.validation.get.receipts
            receipts.map(_.identity) shouldBe Vector("index-local", "index-remote", "test")
            receipts.last.stdout should include("PRODUCT_CHECK_ENTERED " + episode.root.resolve("repository"))
            receipts.last.stdout should not include "PRODUCT_CHECK_PASSED"
            if (mode == "failed") {
              receipts.last.termination shouldBe "exited"
              receipts.last.exitCode shouldBe Some(1)
              (receipts.last.stdout + receipts.last.stderr) should include("actual repaired product input fails")
            } else {
              receipts.last.termination shouldBe "timed-out"
              receipts.last.exitCode shouldBe None
            }
          }
          val retained = episode.current().revision
          When("a later explicit Advance consumes the retained check boundary")
          val again = episode.advance()
          Then("the actual check PID markers and native revision remain unchanged and no review is issued")
          again.kind shouldBe "Wait"
          again.review shouldBe None
          episode.current().revision shouldBe retained
          Files.readString(marker) shouldBe entered
          Files.readString(episode.root.resolve("sbt-executions.txt")) shouldBe executions
        }
      }
    }
  }
}
