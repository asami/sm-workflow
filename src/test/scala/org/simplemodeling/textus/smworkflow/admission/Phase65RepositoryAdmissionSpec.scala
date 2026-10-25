package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.file.{Files, Path}
import org.goldenport.record.Record
import org.goldenport.cncf.workflow.ContextReference
import DevelopmentJournalMergeAssessment as J
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.protocol.{Property, Request}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import RepositorySyncNativeMergeRepair as M
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import SplitPhaseExplicitPartitionFixture.take
import RepositorySyncNativeProgressStore as N

/** Real pending merge, selected SBT, formal admission and native convergence.
  * Fixture-owned grants are not evidence of an external user's authorization.
  * @since   Oct. 24, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class Phase65RepositoryAdmissionSpec extends AnyWordSpec with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  private val assessment = RepositorySyncMergeAssessment.Policy("conditional", "not-applicable", "not-applicable",
    "This selected merge changes two plain-text files and has no Scala or CAR source", Vector.empty)

  "Nested native merge repair compatibility" should {
    "roundtrip raw process facts together with current triage and prior-review fields" in {
      Given("a native-shaped Compile observation nested in a current merge review and its repair request")
      import org.goldenport.cncf.processexecution.*
      val empty = ProcessExecutionCapture(Vector.empty, 0L, false)
      val process = ProcessExecutionResult(ProcessExecutionTermination.Exited(0), empty, empty,
        Vector.empty, 1L, "specification-nested-merge-check")
      val receipt = take(RepositorySyncNativeMergeValidation.sbtReceiptC("compile", WorkflowSbtValidation.Kind.Compile,
        SbtExecutionResult(SbtTarget(take(org.goldenport.cncf.workspace.WorkspaceId.parseC("merge-checks"))),
          SbtOperation.Compile, process)))
      val finding = RepositorySyncNativeMergeReview.Finding("merge-finding", Vector("value.txt"), "Retain the selected meaning")
      val prior = RepositorySyncNativeMergeReview.PriorReview(RepositorySyncNativeMergeReview.RepairContext(
        ContextReference("original-merge-review", "1"), "c" * 40, Vector(finding), "Actual prior finding", Vector("value.txt")),
        "diff --git a/value.txt b/value.txt\n+corrected\n")
      val review = RepositorySyncNativeMergeReview.Request("merge-session", "8", "a" * 40, "b" * 40, "d" * 40,
        Vector("value.txt"), "local diff", "remote diff", "selected", "Actual selected compile", Vector("compile"), Vector(receipt),
        judgmentMode = "triage", uncertainties = Vector("Retain the changed contract"), priorReview = Some(prior))
      val request = M.Request(review, Vector(finding), Vector(M.File("value.txt", "corrected\n")))
      When("the outer repair codec encodes and restores both independent nested contracts")
      val wire = M.inputCodec.encode(request)
      val restored = M.inputCodec.decode(wire)
      Then("raw process observations and every current review field retain their exact values")
      restored shouldBe Right(request)
      restored.toOption.get.review.checks.head.processObservation shouldBe Some(process)
      restored.toOption.get.review.judgmentMode shouldBe "triage"
      restored.toOption.get.review.uncertainties shouldBe review.uncertainties
      restored.toOption.get.review.priorReview shouldBe Some(prior)
      wire.hcursor.downField("payload").downField("review").downField("checks").downArray
        .get[String]("schemaVersion").toOption shouldBe Some(WorkflowSbtCapture.schema)
    }

    "decode historical sbt null receipts when new nested review fields are absent" in {
      Given("a historical merge repair request with its six-field receipt and no triage or prior-review extension")
      val receipt = RepositorySyncNativeMergeValidation.Receipt("compile", "exited", Some(0), "historical output", "", sbt = None)
      val finding = RepositorySyncNativeMergeReview.Finding("historical-finding", Vector("value.txt"), "Retain the prior boundary")
      val review = RepositorySyncNativeMergeReview.Request("merge-session", "8", "a" * 40, "b" * 40, "c" * 40,
        Vector("value.txt"), "local diff", "remote diff", "selected", "Historical selected compile", Vector("compile"), Vector(receipt))
      val request = M.Request(review, Vector(finding), Vector(M.File("value.txt", "historical\n")))
      val wire = M.inputCodec.encode(request)
      val nested = wire.hcursor.downField("payload").focus.get
      val oldreview = nested.hcursor.downField("review").focus.get.mapObject(
        _.remove("judgmentMode").remove("uncertainties").remove("priorReview"))
      val historical = wire.mapObject(_.add("payload", nested.mapObject(_.add("review", oldreview))))
      When("the current outer codec restores the historical nested receipt and review defaults together")
      val restored = M.inputCodec.decode(historical)
      Then("historical null remains compatible without inventing raw process facts or a new semantic judgment")
      oldreview.hcursor.downField("checks").downArray.downField("sbt").focus shouldBe Some(Json.Null)
      restored shouldBe Right(request)
      restored.toOption.get.review.judgmentMode shouldBe "detailed-review"
      restored.toOption.get.review.uncertainties shouldBe empty
      restored.toOption.get.review.priorReview shouldBe None
      restored.toOption.get.review.checks.head.processObservation shouldBe None
      M.inputCodec.decode(M.inputCodec.encode(restored.toOption.get)) shouldBe Right(request)
    }
  }

  override protected def _prepare_sbt_build_(root: Path, scenario: String): Unit = {
    super._prepare_sbt_build_(root, scenario)
    if (root.getFileName.toString.startsWith("check-failure-")) {
      val path = root.resolve("repository/src/test/scala/ProductCheck.scala")
      Files.writeString(path, Files.readString(path).replace("value.txt", "remote.txt"))
    }
  }
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val git = TypedGitHostPlanFixture.plan(org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms.Target(
      root.resolve("repository"), "main", "origin", "main", root.resolve("upstream.git").toString),
      conflictPaths = conflict_path(scenario).map(Set(_)).getOrElse(Set("remote.txt")))
    TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(1), Some(git))
  }

  /** DefinitionBinding is re-created by each fixture read and has reference
    * equality. Compare its canonical handle and every retained record field. */
  private def sameRecord(actual: InstanceRecord, expected: InstanceRecord): Unit = {
    val component = org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId
    take(WorkflowHandle.fromRecordC(component, actual)) shouldBe take(WorkflowHandle.fromRecordC(component, expected))
    actual.copy(definition = expected.definition) shouldBe expected
  }
  private def sameStage(actual: N.Stage, expected: N.Stage): Unit = {
    sameRecord(actual.record, expected.record)
    actual.payload shouldBe expected.payload
  }

  private final class Episode(val root: Path, val id: InstanceIdentity, val input: V.RepositorySyncStartInput,
    call: (String, List[Property]) => Consequence[org.goldenport.protocol.operation.OperationResponse],
    prepare: (Setup => Consequence[Unit]) => Unit) {
    def advanceC() = call("advanceRepositorySync", NativeWorkflowOperationInputFixture.advance(id.value))
    def advance(): V.RepositorySyncAdvanceResult = read_value(take(advanceC()))
    def journalRead(reference: V.JournalEntryReference): V.JournalReadResult = read_value(take(call("journal:readJournalEntry",
      List(Property("input", V.JournalReadInput(reference.identity, Some(reference.revision)), None)))))
    def journalFind(): V.JournalFindResult = read_value(take(call("journal:findJournalEntries",
      List(Property("input", V.JournalFindInput(input.repositoryIdentity, None, Some(id.value),
        Some("MergeAssessment"), None, None, None, None), None)))))
    def current(): InstanceRecord = {
      var saved = Option.empty[InstanceRecord]
      prepare(_.currentC(id).map(value => { saved = Some(value); () }))
      saved.get
    }
    def stage(key: String): Option[N.Stage] = {
      var saved = Option.empty[N.Stage]
      prepare(_.nativeStageC(input, id, root, key).map(value => { saved = value; () }))
      saved
    }
    def work[A](operation: StateMachineOperationIdentity, codec: WorkflowResultJsonV1.PayloadCodec[A]): WorkflowInteraction[A, Nothing] = {
      var selected = Option.empty[WorkflowInteraction[A, Nothing]]
      prepare(_.repositoryWorkC(id, operation, codec).map(value => { selected = Some(value); () }))
      selected.get
    }
    def result[A, B <: NativeWorkflowResultInput.Value](work: WorkflowInteraction[A, Nothing], value: B,
      reference: String, worker: String): ContinuationResult[B] = {
      val request = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[A]].request
      val ref = ContextReference(reference, request.expectedRevision.value)
      ContinuationResult(work.handle, request.runId, request.continuationId, request.expectedRevision,
        request.context.snapshot, TypedValue(request.resultType.get.value, value), ref, Vector.empty,
        ExecutionEvidence(Vector(ref), Some(worker)))
    }
    def repair(files: Vector[M.File]): Unit = {
      val assigned = work(RepositorySyncNativeMergeRepairContinuation.completionOperation, M.inputCodec)
      val submitted = result(assigned, M.Result("repaired", "Specification-owned correction of the actual failing product text", files),
        "spec-p65-check-origin-repair", "specification-repair-worker")
      read_value[V.RepositorySyncNativeMergeRepairSubmissionResult](take(call("submitNativeMergeRepair",
        take(NativeWorkflowOperationInputFixture.submissionC(submitted))))).accepted shouldBe true
    }
    def judge(decision: String, findings: Vector[RepositorySyncNativeMergeReview.Finding] = Vector.empty): Unit = {
      val assigned = work(RepositorySyncNativeMergeReviewContinuation.completionOperation, RepositorySyncNativeMergeReview.inputCodec)
      val submitted = result(assigned, RepositorySyncNativeMergeReview.Result(decision,
        "Specification-owned independent judgment of the captured merge interaction", findings),
        "spec-p65-merge-judgment-" + decision, "specification-independent-merge-judge")
      val input = assigned.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeMergeReview.Request]].request.input.get.value
      prepare(_.reviewEvidenceC(input, submitted))
      read_value[V.RepositorySyncNativeMergeReviewSubmissionResult](take(call("submitNativeMergeReview",
        take(NativeWorkflowOperationInputFixture.submissionC(submitted))))).accepted shouldBe true
    }
    def resolve(): Unit = {
      val assigned = work(RepositorySyncNativeConflictContinuation.completionOperation, RepositorySyncConflictSemantic.inputCodec)
      val input = assigned.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeConflictInput.Request]].request.input.get.value
      val submitted = result(assigned, RepositorySyncConflictSemantic.Result("resolved", input.conflicts.map(c =>
        RepositorySyncConflictSemantic.Resolution(c.identity, "resolved shared line\n")), "Specification-owned conflict resolution"),
        "spec-p65-conflict-resolution", "specification-conflict-author")
      read_value[V.RepositorySyncConflictSubmissionResult](take(call("submitRepositorySyncWorkResult",
        take(NativeWorkflowOperationInputFixture.submissionC(submitted))))).accepted shouldBe true
    }
    def parents: Vector[String] = {
      val process = new ProcessBuilder("/usr/bin/git", "-C", root.resolve("repository").toString,
        "show", "--format=%P", "--no-patch", "HEAD").redirectErrorStream(true).start()
      val text = new String(process.getInputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim
      process.waitFor() shouldBe 0
      text.split(" ").toVector
    }
    def sbtRuns: Int = Files.readString(root.resolve("sbt-executions.txt")).linesIterator.size
  }

  private def episode(name: String, policy: Option[RepositorySyncMergeAssessment.Policy],
    effects: Boolean = true, interrupt: Option[String => Consequence[Unit]] = None, scenario: String = "Diverged",
    componentInstance: String = "repository-spec")
    (run: Episode => Unit): Unit = {
    val owned = Files.createDirectories(Path.of("target/sm-workflow/work/p65-repository-admission").toAbsolutePath.normalize)
    val root = Files.createTempDirectory(owned, name + "-")
    val original = ThreeWorkflowNormalHostFixture.definition(root)
    val host = original.copy(repository = original.repository.copy(validation =
      original.repository.validation.copy(assessment = policy), conflicts = RepositorySyncNativeConflictInput.Policy(Set("remote.txt"))))
    val input = host.repository.input
    val selection = _repository_selection_(input, "spec-p65-merge-selection", "spec-p65-merge-start")
    val journalParameters = if (name == "journal") {
      val collection = ThreeWorkflowNormalHostFixture.collection("journal")
      val storage = ThreeWorkflowNormalHostFixture.storagePlan.mapObject(fields => fields.add("records",
        Json.fromValues(fields("records").get.asArray.get :+ Json.obj("componentId" -> Json.fromString(org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId.name),
          "storeName" -> Json.fromString("workflow"), "collection" -> Json.fromString(collection)))))
      Map("sm-workflow.journal" -> Json.obj("schemaVersion" -> Json.fromString(DevelopmentJournal.schemaVersion),
        "storeName" -> Json.fromString("workflow"), "collection" -> Json.fromString(collection)).noSpaces,
        "textus.subsystem.storage.plan" -> storage.noSpaces)
    } else Map.empty[String, String]
    _with_runtime_(root, scenario, host = Some(host), hostParameters = journalParameters, nativeAcknowledgement = interrupt,
      componentInstance = componentInstance) { (owner, component, actor, prepare) =>
      def call(operation: String, fields: List[Property]) = owner.executeOperationResponseInContext(
        Request.of(component = component.name, service = if (operation.startsWith("journal:")) "JournalService" else "RepositorySyncService",
          operation = operation.stripPrefix("journal:"), properties = fields), actor)
      prepare(setup => setup.setupC(_setup_commands_(root, scenario).size)
        .flatMap(_ => _prepare_input_(setup, input, selection, root, merged = true, provisionEffects = effects)))
      val started = read_value[V.RepositorySyncStartResult](take(call("startRepositorySync", NativeWorkflowOperationInputFixture.repository(input))))
      run(new Episode(root, InstanceIdentity(started.sessionId), input, call, prepare))
    }
  }

  /** Q4 receives an actual normal Start and owned diverged repository, before
    * validation or merge effects. A separate native client continues it. */
  private[admission] def prepareClientScenario(): (Path, String) = {
    var selected = Option.empty[(Path, String)]
    episode("journal", Some(assessment), componentInstance = "default") { e => selected = Some(e.root -> e.id.value) }
    selected.get
  }

  "Q3-R formal pending merge admission" should {
    "commit and converge an actual disjoint diverged merge without a semantic assignment or fake review" in {
      Given("a specification-owned diverged Git repository, selected real SBT and explicit conditional assessment")
      episode("clean", Some(assessment)) { e =>
        When("the normal owning Advance validates and assesses the genuine pending merge")
        val result = e.advance()
        Then("formal MergeAdmitted precedes a real merge commit and observed convergence without any semantic suspension")
        result.kind shouldBe "Terminal"
        val record = e.current()
        record.lifecycle shouldBe Lifecycle.Completed
        record.history.flatMap(_.suspension) shouldBe empty
        record.history.flatMap(_.currentProgression.map(_.value)) should contain("sync.MergeAdmitted")
        record.history.flatMap(_.currentProgression.map(_.value)) should not contain "sync.ReviewAccepted"
        record.history.flatMap(_.currentProgression.map(_.value)) should not contain "sync.IntegrationValidated"
        val admitted = e.stage("merge-admitted").get
        admitted.payload.method shouldBe Some("merge")
        admitted.payload.mergeHead should not be empty
        admitted.payload.assessment.get.branch shouldBe "admit"
        admitted.payload.assessment.get.overlappingPaths shouldBe empty
        admitted.payload.assessment.get.contractPaths shouldBe empty
        admitted.payload.validation.get.receipts.map(_.identity) shouldBe Vector("index-local", "index-remote", "test")
        _assert_product_check_(e.root, admitted.payload.validation.get.receipts.last.stdout)
        e.sbtRuns shouldBe 1
        e.stage("commit-observed").get.payload.finalTip should not be admitted.payload.after.map(_.head)
        e.parents shouldBe Vector(admitted.payload.before.head, admitted.payload.mergeHead.get)
        result.journalRecording.get.status shouldBe "disabled"
        val retained = e.current()
        When("the already completed Session is read again through ordinary Advance")
        val again = e.advance()
        Then("the actual terminal result and checks remain retained without another transition or SBT launch")
        again.copy(journalRecording = result.journalRecording) shouldBe result
        sameRecord(e.current(), retained)
        e.sbtRuns shouldBe 1
      }
    }

    "keep legacy and explicitly mandatory review ahead of convergence" in {
      for (policy <- Vector(None, Some(assessment.copy(detailedReview = "required")))) {
        Given("a fresh actual disjoint merge whose old policy is absent or explicitly selects required review")
        episode("required", policy) { e =>
          When("the normal owning Advance completes selected validation")
          val result = e.advance()
          Then("the actual independent Review is issued directly without any fabricated admission or commit")
          result.kind shouldBe "Request"
          result.review should not be empty
          e.current().suspension should not be empty
          e.stage("merge-admitted") shouldBe None
          e.stage("commit-intent") shouldBe None
          _assert_product_check_(e.root, result.review.get.checks.last.stdout)
          e.sbtRuns shouldBe 1
        }
      }
    }

    "route actual selected contract uncertainty to independent judgment instead of zero-signal admission" in {
      Given("the actual pending paths include a declared public contract path")
      episode("contract", Some(assessment.copy(contractPaths = Vector("remote.txt")))) { e =>
        When("the owning Advance assesses complete actual selected checks")
        val result = e.advance()
        Then("the unresolved interaction cannot reach native commit through a clean-only route")
        result.kind shouldBe "Request"
        result.review.get.changedPaths should contain("remote.txt")
        result.review.get.judgmentMode shouldBe Some("triage")
        result.review.get.uncertainties should contain("Selected contract path: remote.txt")
        e.current().suspension should not be empty
        e.stage("merge-admitted") shouldBe None
        e.stage("commit-intent") shouldBe None
        val first = e.current()
        e.advance().review shouldBe result.review
        sameRecord(e.current(), first)
        e.sbtRuns shouldBe 1
        e.judge("clean")
        e.advance().kind shouldBe "Terminal"
        e.current().history.flatMap(_.suspension).size shouldBe 1
        e.sbtRuns shouldBe 1
      }
    }
    "repair an actual failed merge check without inventing a review finding and retain the failed batch" in {
      Given("an actual selected SBT test fails on the merged remote text and a bounded selected text repair is installed")
      episode("check-failure", Some(assessment)) { e =>
        When("normal Advance observes the actual nonzero selected check")
        val failed = e.advance()
        Then("it issues a check-origin Repair with empty review findings and the original failed output")
        failed.kind shouldBe "Request"
        failed.repair.get.validationFailure shouldBe Some("test")
        failed.repair.get.findings shouldBe empty
        failed.repair.get.files.map(_.path) shouldBe Vector("remote.txt")
        failed.repair.get.review.checks.last.exitCode should not be 0
        e.stage("merge-admitted") shouldBe None
        e.stage("commit-intent") shouldBe None
        val retainedFailure = e.stage("validation-result-2").get
        val repairSource = e.stage("validation-repair-required").get
        val prefix = e.current().history
        When("the specification worker returns only the assigned replacement and the owner separately Advances")
        e.repair(Vector(M.File("remote.txt", "expected\n")))
        val triage = e.advance()
        triage.kind shouldBe "Request"
        triage.review.get.judgmentMode shouldBe Some("triage")
        e.judge("clean")
        val completed = e.advance()
        Then("fresh affected checks and actual triage admit the same integration and native convergence preserves the failed prefix")
        completed.kind shouldBe "Terminal"
        e.current().history.take(prefix.size) shouldBe prefix
        sameStage(e.stage("validation-result-2").get, retainedFailure)
        sameStage(e.stage("validation-repair-required").get, repairSource)
        val namespace = "repair-" + repairSource.record.revision.value
        e.stage(namespace + "-merge-admitted").get.payload.assessment.get.branch shouldBe "admit"
        e.current().history.flatMap(_.currentProgression.map(_.value)) should not contain "sync.ReviewAccepted"
        e.current().history.flatMap(_.suspension.map(_.continuationIdentity.value)).distinct.size shouldBe 2
        e.sbtRuns shouldBe 2
      }
    }

    "escalate actual suspicious and semantic decline triage to one independent detailed review without replaying checks" in {
      for (decision <- Vector("suspicious", "decline")) episode("escalate-" + decision,
        Some(assessment.copy(contractPaths = Vector("remote.txt")))) { e =>
        Given("the owner has issued actual contract-path triage")
        e.advance().review.get.judgmentMode shouldBe Some("triage")
        e.judge(decision)
        When("ordinary Advance consumes the actual scoped semantic escalation")
        val detailed = e.advance()
        Then("detailed review is independently issued and the successful check is retained")
        detailed.kind shouldBe "Request"
        detailed.review.get.judgmentMode shouldBe None
        e.stage("merge-admitted") shouldBe None
        e.stage("commit-intent") shouldBe None
        e.sbtRuns shouldBe 1
        e.judge("accept")
        e.advance().kind shouldBe "Terminal"
        e.current().history.flatMap(_.suspension).size shouldBe 2
        e.current().history.flatMap(_.currentProgression.map(_.value)) should contain("sync.ReviewAccepted")
        e.sbtRuns shouldBe 1
      }
    }

    "require detailed independent review after actual AI-authored conflict resolution despite conditional policy" in {
      episode("resolved", Some(assessment), scenario = "Conflict") { e =>
        Given("an actual conflicted pending merge and selected conditional review")
        e.advance().conflict should not be empty
        e.resolve()
        When("normal Advance materializes only the actual issued conflict resolution and validates it")
        val review = e.advance()
        Then("conflict provenance bypasses triage and cannot be waived by the conditional policy")
        review.kind shouldBe "Request"
        review.review.get.judgmentMode shouldBe None
        e.stage("merge-admitted") shouldBe None
        e.stage("commit-intent") shouldBe None
        e.judge("accept")
        e.advance().kind shouldBe "Terminal"
        e.current().history.flatMap(_.suspension).size shouldBe 2
        e.sbtRuns shouldBe 1
      }
    }

    "retain an executed but unrecorded check as unknown instead of fabricating failure or automatically replaying it" in {
      Given("a specification acknowledgement interruption after the real selected test terminated")
      val interrupt: String => Consequence[Unit] = stage => if (stage == "merge-check-executed:test")
        Consequence.stateConflict("Specification-owned interruption before receipt persistence") else Consequence.unit
      episode("unknown", Some(assessment), interrupt = Some(interrupt)) { e =>
        When("the actual Advance loses acknowledgement after the check")
        e.advanceC().isSuccess shouldBe false
        e.sbtRuns shouldBe 1
        val first = e.current()
        Then("later ordinary Advance retains the unknown effect and neither issues repair nor reruns the check")
        val retained = e.advance()
        retained.kind shouldBe "Wait"
        retained.repair shouldBe None
        sameRecord(e.current(), first)
        e.stage("validation-result-2") shouldBe None
        e.stage("validation-repair-required") shouldBe None
        e.stage("merge-admitted") shouldBe None
        e.stage("commit-intent") shouldBe None
        e.sbtRuns shouldBe 1
      }
    }

    "preserve independent Git effect authorization after formal reviewless merge admission" in {
      episode("authority", Some(assessment), effects = false) { e =>
        Given("complete selected checks and an actual clean merge without a commit grant")
        When("normal Advance reaches its independent native effect boundary")
        e.advanceC().isSuccess shouldBe false
        Then("formal admission exists but supplies neither commit authority nor convergence")
        e.stage("merge-admitted") should not be empty
        e.stage("commit-intent") shouldBe None
        e.current().lifecycle shouldBe Lifecycle.Active
        e.current().history.flatMap(_.suspension) shouldBe empty
        e.sbtRuns shouldBe 1
      }
    }

    "carry the original genuine findings and bounded repair delta into affected independent re-review" in {
      episode("affected-review", Some(assessment.copy(detailedReview = "required"))) { e =>
        Given("an actual required merge review returns a scoped repair finding")
        val first = e.advance()
        e.judge("repair", Vector(RepositorySyncNativeMergeReview.Finding("remote-text", Vector("remote.txt"),
          "The remote text needs the assigned bounded semantic correction")))
        val repair = e.advance()
        repair.repair.get.validationFailure shouldBe None
        e.repair(Vector(M.File("remote.txt", "corrected remote text\n")))
        When("normal Advance applies the assigned repair and executes checks for its new candidate")
        val second = e.advance()
        Then("the actual prior report, affected paths and delta constrain re-review without a new fictitious prior PASS")
        second.review.get.indexTree should not be first.review.get.indexTree
        val issued = e.work(RepositorySyncNativeMergeReviewContinuation.completionOperation, RepositorySyncNativeMergeReview.inputCodec)
        val input = issued.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeMergeReview.Request]].request.input.get.value
        input.priorReview.get.context.indexTree shouldBe first.review.get.indexTree
        input.priorReview.get.context.findings.map(_.identity) shouldBe Vector("remote-text")
        input.priorReview.get.context.affectedPaths shouldBe Vector("remote.txt")
        input.priorReview.get.repairDelta should include("corrected remote text")
        e.judge("accept")
        e.advance().kind shouldBe "Terminal"
        e.sbtRuns shouldBe 2
      }
    }

    "retain detailed re-review after conditional triage escalates to a genuine repair finding" in {
      for (decision <- Vector("suspicious", "decline")) episode("conditional-repair-" + decision,
        Some(assessment.copy(contractPaths = Vector("remote.txt")))) { e =>
        Given("actual conditional contract-path triage escalates to independent detailed review")
        e.advance().review.get.judgmentMode shouldBe Some("triage")
        e.judge(decision)
        val original = e.advance().review.get
        original.judgmentMode shouldBe None
        val finding = RepositorySyncNativeMergeReview.Finding("conditional-remote-text", Vector("remote.txt"),
          "Correct the remote contract text and independently review the affected meaning")
        e.judge("repair", Vector(finding))
        val repair = e.advance()
        repair.repair.get.findings.map(f => (f.identity, f.affectedPaths, f.rationale)) shouldBe
          Vector((finding.identity, finding.affectedPaths, finding.rationale))
        repair.repair.get.validationFailure shouldBe None
        val prefix = e.current().history
        e.sbtRuns shouldBe 1
        e.repair(Vector(M.File("remote.txt", "independently reviewed contract correction\n")))

        When("normal Advance materializes the repair and runs the selected checks on its fresh candidate")
        val affected = e.advance()
        Then("the genuine original finding requires detailed re-review without another triage or admission")
        affected.kind shouldBe "Request"
        affected.review.get.judgmentMode shouldBe None
        affected.review.get.indexTree should not be original.indexTree
        affected.review.get.checks.last.exitCode shouldBe 0
        e.sbtRuns shouldBe 2
        e.current().history.take(prefix.size) shouldBe prefix
        e.current().history.flatMap(_.currentProgression.map(_.value)) should not contain "sync.MergeAdmitted"
        e.current().history.flatMap(_.currentProgression.map(_.value)) should not contain "sync.NativeCommitPreparing"
        e.parents.size shouldBe 1
        val assigned = e.work(RepositorySyncNativeMergeReviewContinuation.completionOperation, RepositorySyncNativeMergeReview.inputCodec)
        val input = assigned.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeMergeReview.Request]].request.input.get.value
        input.judgmentMode shouldBe "detailed-review"
        input.priorReview.get.context.indexTree shouldBe original.indexTree
        input.priorReview.get.context.findings shouldBe Vector(finding)
        input.priorReview.get.context.affectedPaths shouldBe Vector("remote.txt")
        input.priorReview.get.repairDelta should include("independently reviewed contract correction")
        val pending = e.current()
        e.advance().review shouldBe affected.review
        sameRecord(e.current(), pending)
        e.sbtRuns shouldBe 2

        When("the independently issued affected detailed review actually accepts the repair")
        e.judge("accept")
        Then("the same integration reaches its genuine merge commit and convergence without replaying checks")
        e.advance().kind shouldBe "Terminal"
        e.parents.size shouldBe 2
        e.current().history.flatMap(_.suspension).size shouldBe 4
        e.sbtRuns shouldBe 2
      }
    }

    "publish actual native routing through the existing Journal read/find service with retained source and no second validation" in {
      episode("journal", Some(assessment)) { e =>
        Given("the normal host explicitly selects the common Journal store before starting the owned episode")
        val result = e.advance()
        Then("the native result names the actual recorded observation, readable through the same public Journal service")
        result.kind shouldBe "Terminal"
        val state = result.journalRecording.get
        state.status shouldBe "recorded"
        val entry = e.journalRead(state.entry.get).entry.get
        entry.kind shouldBe "MergeAssessment"
        entry.sessionIdentity shouldBe e.id.value
        entry.sourceReference shouldBe state.sourceReference
        e.journalFind().entries.map(_.entry) shouldBe Vector(state.entry.get)
        val current = e.current()
        e.advance().journalRecording.get.status shouldBe "alreadyRecorded"
        sameRecord(e.current(), current)
        e.sbtRuns shouldBe 1
      }
    }

    "retain the failed batch and require Human handling when the selected merge repair budget is exhausted" in {
      episode("check-failure-budget", Some(assessment.copy(repairLimit = 0))) { e =>
        Given("the actual selected check fails with an explicit zero remaining repair budget")
        val result = e.advance()
        result.kind shouldBe "Wait"
        result.condition shouldBe Some("native-repair-limit")
        result.repair shouldBe None
        e.stage("validation-repair-required") should not be empty
        val retained = e.current()
        e.advance().condition shouldBe result.condition
        sameRecord(e.current(), retained)
        e.current().history.flatMap(_.suspension) shouldBe empty
        e.stage("commit-intent") shouldBe None
        e.sbtRuns shouldBe 1
      }
    }

    "record actual merge routing in the common immutable Journal without changing business acceptance on storage failure" in {
      Given("an actual merge assessment observation and the existing common Journal storage contract")
      val reference = ContextReference("specification-owned-validation-entry", "17")
      val observed = RepositorySyncMergeAssessment.Result(assessment, "17", "admit",
        "Actual complete checks and disjoint parents selected no semantic action", Vector.empty, Vector.empty, false)
      val entry = J.Entry(J.entryIdentity(reference),
        "specification-repository", "RepositorySync", "specification-session", reference, observed, "a" * 40, "2026-10-24T00:00:00Z")
      var saved = Option.empty[Record]
      val storage = new DevelopmentJournal.Storage {
        def loadC() = Consequence.success(saved)
        def createC(row: Record) = { saved = Some(row); Consequence.unit }
      }
      When("the common Journal records the actual immutable observation and subsequently sees the same source")
      val recorded = take(J.appendC(storage, entry))
      recorded.status shouldBe "recorded"
      take(J.appendC(storage, entry.copy(recordedAt = "2026-10-24T00:00:01Z"))).status shouldBe "alreadyRecorded"
      Then("read/find decode the real typed observation; conflicting content and unavailable storage stay honest")
      take(J.storedC(saved.get, entry.identity)) shouldBe entry
      J.publicEntry(entry).kind shouldBe "MergeAssessment"
      take(DevelopmentJournalFind.validateInputC(V.JournalFindInput("specification-repository", None, None,
        Some("MergeAssessment"), None, None, None, None))) shouldBe (())
      take(J.appendC(storage, entry.copy(candidateTree = "b" * 40))).status shouldBe "conflict"
      val unavailable = new DevelopmentJournal.Storage {
        def loadC() = Consequence.stateConflict("Specification-owned unavailable store")
        def createC(row: Record) = Consequence.stateConflict("Unexpected write")
      }
      take(J.appendC(unavailable, entry)).status shouldBe "unavailable"
    }

  }
}
