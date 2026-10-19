package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import io.circe.Json
import io.circe.syntax.*
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import GoalPhaseNativeReviewCompletion as Q
import GoalPhaseNativeReview as R
import GoalPhaseNativeWork as W
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeValidation as V
import GoalPhaseFixIssues as I
import SplitPhaseExplicitPartitionFixture.take

/** Pure actual-shaped history, completions, checks and producer values confer
  * no actual independence, command execution or installed acceptance.
  * @since   Oct. 19, 2026
  * @version Oct. 19, 2026
  */
final class GoalPhaseNativeReviewCompletionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _base = O.Snapshot("a" * 40, "b" * 40)
  private val _old = O.Snapshot(_base.head, "c" * 40)
  private val _current = O.Snapshot(_base.head, "d" * 40)
  private val _paths = Vector("src/one.scala", "docs/check.md")
  private val _conditions = Q.Conditions("terminal-lf", _paths)
  private val _checks = Vector(V.Check("compile", 1, WorkflowSbtValidation.Kind.Compile),
    V.Check("test", 2, WorkflowSbtValidation.Kind.Test))
  private val _plan = O.Plan("covered-plan", "1", "PHASE-6.3", "sm-workflow", Vector(
    O.Step("S1", "Implement the selected boundary", _paths, Vector.empty, _checks.map(_.identity)),
    O.Step("S2", "Continue the selected Plan", Vector("next.scala"), Vector("S1"), Vector("test"))), Vector("test"))
  private val _report = W.Result("repair", "All semantic judgments are settled; remove only extra terminal LF",
    Vector("Remove the extra terminal LF in src/one.scala", "Remove the extra terminal LF in docs/check.md"),
    completionConditions = Some(_conditions))
  private def _append(from: InstanceRecord, stage: String, cause: String): InstanceRecord =
    take(from.appendC(from.revision, GoalPhaseNativeRun.history(from, "phase." + stage, cause)))
  private def _record: InstanceRecord = {
    val initial = take(InstanceRecord(InstanceIdentity("covered-native"), take(GoalPhaseRuntimeCatalog.loadC()).definition,
      initialRevision, Lifecycle.NotStarted, None, CorrelationReference("covered-correlation"),
      CausationReference("covered-start"), None, None, None, Vector.empty).validateC)
    _append(initial, "Review", "specification-original-review-frontier")
  }
  private def _receipts(checks: Vector[V.Check]) = checks.map(check => RepositorySyncNativeMergeValidation.Receipt(
    check.identity, "exited", Some(0), "actual-shaped selected result", "",
    Some(WorkflowSbtValidation.Facts(check.kind, WorkflowSbtValidation.Outcome.Succeeded,
      Option.when(check.kind == WorkflowSbtValidation.Kind.TestOnly)(WorkflowSbtValidation.Selection(
        Vector("example.CoveredSpec"), 1, 1L)), None))))
  private def _submitted(record: InstanceRecord, request: W.Request, result: W.Result, identity: String): ContinuationResult[W.Result] = {
    val reference = ContextReference(identity, "1")
    val handle = WorkflowHandle(SmWorkflowComponent.componentId, record.definition.workflowIdentity,
      record.definition.workflowRevision, record.identity)
    ContinuationResult(handle, StateMachineRunIdentity(record.identity.value), org.goldenport.cncf.workflow.ContinuationIdentity("fixture-slot-" + request.kind),
      StateMachineRevision(record.revision.next.value.toString), ContextSnapshot(record.definition.workflowRevision.value),
      TypedValue(W.resultCodec.typeIdentity, result), reference, Vector.empty,
      ExecutionEvidence(Vector(reference), Some("specification-worker-with-no-authority")))
  }
  final case class Fixture(from: InstanceRecord, controller: O.State, candidate: GoalPhaseNativeCandidate.Saved,
    validation: V.Saved, repair: W.Request, result: ContinuationResult[W.Result],
    prior: R.Prior, issues: Option[I.State], checks: Vector[V.Check]) {
    def selectC = Q.selectC(from, controller, Some(candidate), Some(validation),
      Some(repair -> result), issues, Vector(prior), checks)
  }
  private def _fixture(checks: Vector[V.Check] = _checks, report: W.Result = _report): Fixture = {
    val plan = _plan.copy(steps = _plan.steps.updated(0, _plan.steps.head.copy(checks = checks.map(_.identity))))
    val initial = take(O.startC(plan, _base))
    val implemented = take(initial.acceptC(O.SemanticCompleted(initial.next.asInstanceOf[O.Semantic].key, "implemented")))
    val observed = take(implemented.acceptC(O.CandidateObserved(_old)))
    val reviewed = take(observed.acceptC(O.ChecksObserved(_old, checks.map(_.identity), "original-checks", true)))
    val originalrecord = _record
    val context = R.Context(_paths, "original selected diff", _receipts(checks))
    val request = W.Request(originalrecord.identity.value, originalrecord.revision.value.toString, "ReviewStep", Some("S1"),
      reviewed.generation, plan.steps.head.purpose, plan, _base, Vector.empty, Some(_old), Some("original-checks"), None,
      review = Some(context))
    val original = _submitted(originalrecord, request, report, "actual-shaped-original-review")
    val prior = take(R.retainPriorC(Vector.empty, request, original)).head
    val openissues = take(I.completedC(request, report, original.resultReference.identity))
    val assigned = take(R.completedStateC(report, original.resultReference.identity, reviewed))
    val repairrecord = _append(originalrecord, "Repair", original.resultReference.identity)
    val repair = W.Request(repairrecord.identity.value, repairrecord.revision.value.toString, "RepairStep", Some("S1"),
      assigned.generation, plan.steps.head.purpose, plan, _base, Vector.empty, None, None, assigned.repairDirective,
      review = Some(context.copy(priorReviews = Vector(prior))), issues = openissues)
    val repaired = W.Result("completed", "Corrected only the assigned terminal LF findings", Vector.empty,
      issueReport = Some(I.Report(openissues.toVector.flatMap(_.issues).map(issue =>
        I.Disposition(issue.identity, "FIXED", "Removed the assigned extra terminal LF", None)))))
    take(W.admitSemanticResultC(repair, repaired))
    val result = _submitted(repairrecord, repair, repaired, "actual-shaped-repair-result")
    val repairedissues = take(I.completedC(repair, repaired, result.resultReference.identity))
    val ready = take(assigned.acceptC(O.SemanticCompleted(assigned.next.asInstanceOf[O.Semantic].key, repaired.summary)))
    val inspected = take(ready.acceptC(O.CandidateObserved(_current)))
    val inspectionrecord = _append(repairrecord, "InspectCandidate", result.resultReference.identity)
    val candidaterecord = _append(inspectionrecord, "Validation", "ordered-candidate:candidate")
    val candidate = GoalPhaseNativeCandidate.Saved(candidaterecord, GoalPhaseNativeCandidate.Payload(
      inspectionrecord.revision.value.toString, "S1", _paths, _paths, Some(_current), _paths, Some("current selected diff")))
    val validationrecord = _append(candidaterecord, "Review", "ordered-validation:validation")
    val validation = V.Saved(validationrecord, V.Payload(candidaterecord.revision.value.toString, "S1", _current,
      checks, _receipts(checks), Some("passed")))
    val checksreference = validationrecord.identity.value + ":ordered-validation:" + candidaterecord.revision.value + ":validation"
    val controller = take(V.observedStateC(inspected, validation.payload, checksreference))
    val issues = take(I.validationC(validationrecord.identity.value, plan.repository, repairedissues,
      validation.payload, checksreference))
    Fixture(validationrecord, controller, candidate, validation, repair, result, prior, issues, checks)
  }
  private def _source(fixture: Fixture): Q.Source = take(fixture.selectC).get
  private def _change(path: String, oldblob: String, newblob: String): GitTreeChange =
    GitTreeChange("100644", "100644", Some(take(GitObjectId.parseC(oldblob))), Some(take(GitObjectId.parseC(newblob))),
      "M", take(GitPath.parseC(path)))
  private val _changes = Vector(_change(_paths.head, "1" * 40, "2" * 40), _change(_paths(1), "3" * 40, "4" * 40))
  private def _blob(text: String): GitBlobContent = GitBlobContent(text.getBytes(UTF_8).toVector, Some(text))
  private def _blobs = Map("1" * 40 -> _blob("source\n\n\n"), "2" * 40 -> _blob("source\n"),
    "3" * 40 -> _blob("document\n\n"), "4" * 40 -> _blob("document\n"))
  private def _payload(source: Q.Source, changes: Vector[GoalPhaseNativeClosure.Change]): Q.Payload =
    Q.Payload(source.from.revision.value.toString, "S1", source.controller.generation,
      ContextReference(source.prior.resultIdentity, source.prior.resultRevision), source.prior.generation,
      source.prior.candidate, source.prior.checksReceipt, source.result.resultReference, _current,
      source.from.identity.value + ":ordered-candidate:" + source.candidate.payload.sourceRevision + ":candidate",
      source.controller.checksReceipt.get, source.conditions.paths, changes)

  "Original review completion conditions" should {
    "retain historical absence and strictly decode only bounded terminal-LF paths" in {
      Given("docs/spec/native-goal-command-task.md; an original repair report and the historical absent optional value")
      val absent = _report.copy(completionConditions = None)
      val oldwire = W.resultCodec.encode(absent)
      val wire = W.resultCodec.encode(_report)
      val badconditions = Vector(Json.Null, Json.True, Json.obj(), _conditions.copy(kind = "semantic").asJson,
        _conditions.copy(paths = Vector.empty).asJson, _conditions.copy(paths = Vector.fill(2)(_paths.head)).asJson,
        _conditions.copy(paths = (1 to 17).map(n => "path-" + n).toVector).asJson,
        _conditions.copy(paths = Vector("../foreign", "/absolute", "with\u0001control")).asJson,
        _conditions.copy(paths = Vector("a" * 4097)).asJson,
        _conditions.asJson.mapObject(_.add("extra", Json.True)))
      When("the ordinary result codec reads absent, valid and malformed condition values")
      val decoded = W.resultCodec.decode(wire)
      val invalid = badconditions.map(value => W.resultCodec.decode(wire.mapObject(_.add("payload",
        wire.hcursor.downField("payload").focus.get.mapObject(_.add("completionConditions", value))))))
      Then("absence stays absent, exact ordered semantic content survives, and malformed presence refuses")
      oldwire.hcursor.downField("payload").downField("completionConditions").focus shouldBe None
      W.resultCodec.decode(oldwire) shouldBe Right(absent)
      decoded shouldBe Right(_report)
      invalid.foreach(_.isLeft shouldBe true)
    }

    "convert the actual public Record strictly without erasing null or malformed presence" in {
      Given("the actual public Record representation used by the ordinary generated Submit receiver")
      val actual = GoalPhaseNativeMetadata.record(_conditions.asJson)
      val malformed = GoalPhaseNativeMetadata.record(Json.obj("kind" -> Json.fromString("terminal-lf"), "paths" -> Json.Null))
      When("the owner converts absence, the valid actual Record, null options and malformed Records")
      val absent = Q.fromPublicConditionsC(None)
      val valid = Q.fromPublicConditionsC(Some(actual))
      val invalid = Vector(Q.fromPublicConditionsC(null), Q.fromPublicConditionsC(Some(null.asInstanceOf[Record])),
        Q.fromPublicConditionsC(Some(malformed)), Q.fromPublicConditionsC(Some(GoalPhaseNativeMetadata.record(
          _conditions.copy(kind = "other").asJson))))
      Then("only genuine absence maps to None and exact valid Record content maps to Conditions")
      take(absent) shouldBe None
      take(valid) shouldBe Some(_conditions)
      invalid.foreach(_.isSuccess shouldBe false)
    }

    "admit conditions only for an original owned Step repair verdict" in {
      Given("an independently judged original Step repair verdict with one ordered terminal-LF finding per owned path")
      When("the common ordinary-result gate checks its actual kind, outcome, findings and selected ownership")
      val invalid = Vector(
        Q.admitResultC("ImplementStep", _report, Some(_paths)), Q.admitResultC("RepairStep", _report, Some(_paths)),
        Q.admitResultC("ReviewPhase", _report, Some(_paths)),
        Q.admitResultC("ReviewStep", _report.copy(outcome = "accept"), Some(_paths)),
        Q.admitResultC("ReviewStep", _report.copy(outcome = "decision-needed"), Some(_paths)),
        Q.admitResultC("ReviewStep", _report.copy(findings = _report.findings.take(1)), Some(_paths)),
        Q.admitResultC("ReviewStep", _report.copy(findings = Vector("", "")), Some(_paths)),
        Q.admitResultC("ReviewStep", _report.copy(planRevision = Some(GoalPhasePlanRevision.Proposal(
          "proposal", "1", "A separate semantic proposal", _plan))), Some(_paths)),
        Q.admitResultC("ReviewStep", _report, Some(Vector("foreign"))))
      val admitted = Q.admitResultC("ReviewStep", _report, Some(_paths))
      val fixture = _fixture()
      val rejectedrepair = W.admitSemanticResultC(fixture.repair, fixture.result.result.value.copy(completionConditions = Some(_conditions)))
      val initial = fixture.repair.copy(kind = "ImplementStep", repairDirective = None, review = None, issues = None)
      val rejectedimplementation = W.admitInitialResultC(initial, W.Result("completed", "Implemented", Vector.empty,
        completionConditions = Some(_conditions)))
      Then("only the actual original Step repair report can express the optional condition contract")
      admitted.isSuccess shouldBe true
      invalid.foreach(_.isSuccess shouldBe false)
      rejectedrepair.isSuccess shouldBe false
      rejectedimplementation.isSuccess shouldBe false
    }
  }

  "Native covered review completion" should {
    "admit real-shaped repair, fresh checks and exact typed bytes while retaining original reports and issue failures" in {
      Given("docs/spec/native-goal-command-task.md; an admitted original review, a completed FIXED repair, fresh Compile/Test and exact typed blobs")
      val fixture = _fixture()
      val source = _source(fixture)
      val original = fixture.prior
      val oldhistory = fixture.issues.get.history
      When("the native owner selects actual-shaped material, proves bytes and admits its bounded mechanical result")
      val changes = take(Q.proveC(source, _changes, _blobs)).get
      val payload = _payload(source, changes)
      val (closed, issues) = take(Q.admittedStateC(source, payload))
      val retained = take(R.retainPriorC(Vector(original), fixture.repair, fixture.result))
      Then("Closing retains current candidate/checks, original review identity and every consumed counter without new semantics")
      closed.stage shouldBe O.Stage.Closing
      closed.candidate shouldBe Some(_current)
      closed.checksReceipt shouldBe fixture.controller.checksReceipt
      closed.reviewEvidence shouldBe Some(original.resultIdentity)
      closed.generation shouldBe fixture.controller.generation
      closed.repairs shouldBe fixture.controller.repairs
      closed.ordinaryRepairs shouldBe fixture.controller.ordinaryRepairs
      source.prior shouldBe original
      retained shouldBe Vector(original)
      original.candidate shouldBe _old
      original.checksReceipt shouldBe "original-checks"
      original.result.outcome shouldBe "repair"
      original.result.completionConditions shouldBe Some(_conditions)
      issues.get.history.take(oldhistory.size) shouldBe oldhistory
      issues.get.history.drop(oldhistory.size).map(_.status) shouldBe Vector("RESOLVED", "RESOLVED")
      issues.get.history.drop(oldhistory.size).map(_.evidenceReference).distinct shouldBe Vector(source.reference.identity)
      issues.get.issues.foreach { issue =>
        issue.status shouldBe "RESOLVED"
        issue.latestDisposition shouldBe Some("FIXED")
        issue.evidenceReference shouldBe source.reference.identity
      }
      issues.get.movement.get.resolvedIssues shouldBe 2
      Q.message(payload) should include(original.resultIdentity)
      Q.message(payload) should include(fixture.controller.checksReceipt.get)
      _paths.foreach(path => Q.message(payload) should include(path))
    }

    "leave ordinary affected review required for successful uncovered changes or missing Compile/Test coverage" in {
      Given("an eligible actual-shaped source with successful typed reads showing semantic bytes, CR, changed mode or an extra/missing path")
      val source = _source(_fixture())
      val semantic = _blobs.updated("2" * 40, _blob("changed source\n"))
      val cr = _blobs.updated("1" * 40, _blob("source\r\n\r\n"))
      val space = _blobs.updated("1" * 40, _blob("source \n\n"))
      val mode = _changes.updated(0, _changes.head.copy(newMode = "100755"))
      val extra = _changes :+ _change("src/extra.scala", "5" * 40, "6" * 40)
      val extrablobs = _blobs ++ Map("5" * 40 -> _blob("extra\n\n"), "6" * 40 -> _blob("extra\n"))
      When("complete producer values reach the same bounded rule, or selection lacks Compile or Test")
      val uncovered = Vector(Q.proveC(source, _changes, semantic), Q.proveC(source, _changes, cr),
        Q.proveC(source, _changes, space), Q.proveC(source, mode, _blobs), Q.proveC(source, extra, extrablobs),
        Q.proveC(source, _changes.take(1), _blobs), Q.proveC(source, Vector.empty, Map.empty))
      val incompletechecks = Vector(_fixture(_checks.take(1)), _fixture(_checks.drop(1)),
        _fixture(Vector(_checks.head, V.Check("focused", 2, WorkflowSbtValidation.Kind.TestOnly)))).map(_.selectC)
      Then("successful inapplicability selects None without accepting or rewriting an old report")
      uncovered.foreach(value => take(value) shouldBe None)
      incompletechecks.foreach(value => take(value) shouldBe None)
      source.controller.stage shouldBe O.Stage.Review
      source.prior.result.outcome shouldBe "repair"
    }

    "leave ordinary affected review required when a conditioned file becomes binary after successful typed reads" in {
      Given("an eligible original terminal-LF review and complete NUL-bearing or invalid UTF8 bytes with the producer's absent text projection")
      val fixture = _fixture()
      val source = _source(fixture)
      val original = fixture.prior
      val binaryblobs = Vector(GitBlobContent(Vector('s'.toByte, 0.toByte, '\n'.toByte), None),
        GitBlobContent(Vector(0xc3.toByte, 0x28.toByte), None))
      val alternatives = binaryblobs.map(blob => _blobs.updated("2" * 40, blob))
      When("the native owner checks each complete binary replacement of the conditioned file")
      val uncovered = alternatives.map(Q.proveC(source, _changes, _))
      Then("each successful read selects ordinary review and retains the original report and current checks without closing")
      uncovered.foreach(value => take(value) shouldBe None)
      source.controller.stage shouldBe O.Stage.Review
      source.controller shouldBe fixture.controller
      source.controller.checksReceipt shouldBe fixture.controller.checksReceipt
      source.prior shouldBe original
      source.prior.result shouldBe _report
      source.prior.checksReceipt shouldBe "original-checks"
      source.issues shouldBe fixture.issues
    }

    "leave ordinary affected review required when an extra binary path accompanies exact terminal-LF repair" in {
      Given("exact terminal-LF repairs plus an extra changed path with complete NUL-bearing or invalid UTF8 bytes and absent text")
      val fixture = _fixture()
      val source = _source(fixture)
      val original = fixture.prior
      val changes = _changes :+ _change("src/extra.bin", "5" * 40, "6" * 40)
      val binaryblobs = Vector(GitBlobContent(Vector('s'.toByte, 0.toByte, '\n'.toByte), None),
        GitBlobContent(Vector(0xc3.toByte, 0x28.toByte), None))
      val alternatives = binaryblobs.map(blob => _blobs ++ Map("5" * 40 -> _blob("extra\n"), "6" * 40 -> blob))
      When("the native owner checks the otherwise exact repair together with each successful extra binary read")
      val uncovered = alternatives.map(Q.proveC(source, changes, _))
      Then("each uncovered path selects ordinary review and retains original report and current checks without reuse closure")
      uncovered.foreach(value => take(value) shouldBe None)
      source.controller.stage shouldBe O.Stage.Review
      source.controller shouldBe fixture.controller
      source.controller.checksReceipt shouldBe fixture.controller.checksReceipt
      source.prior shouldBe original
      source.prior.result shouldBe _report
      source.prior.checksReceipt shouldBe "original-checks"
      source.issues shouldBe fixture.issues
    }

    "stop on unavailable incomplete malformed or contradictory actual input" in {
      Given("an eligible source with incomplete actual blob capture, malformed change facts or crossed generation and directive")
      val fixture = _fixture()
      val source = _source(fixture)
      val badblobs = Vector(_blobs - ("2" * 40), _blobs.updated("1" * 40, _blob("source\n\n").copy(text = None)),
        _blobs.updated("1" * 40, _blob("source\n\n").copy(bytes = Vector(1.toByte))),
        _blobs.updated("1" * 40, GitBlobContent(Vector(0xc3.toByte, 0x28.toByte), Some("invalid"))),
        _blobs.updated("1" * 40, null),
        _blobs.updated("1" * 40, _blob("source\n\n").copy(bytes = null)),
        _blobs.updated("1" * 40, _blob("source\n\n").copy(text = null)),
        _blobs.updated("1" * 40, _blob("source\n\n").copy(text = Some(null))),
        _blobs.updated("1" * 40, _blob("source\u0000\n")))
      val crossedrepairs = Vector(fixture.repair.copy(generation = fixture.repair.generation + 1),
        fixture.repair.copy(selectedPlan = fixture.repair.selectedPlan.copy(revision = "2")),
        fixture.repair.copy(originalBase = _old), fixture.repair.copy(stepIdentity = Some("S2")),
        fixture.repair.copy(repairDirective = Some(O.RepairDirective("different directive", _report.findings))),
        fixture.repair.copy(review = Some(fixture.repair.review.get.copy(priorReviews = Vector.empty))))
      When("the owner consumes these actual-shaped values without substituting a process or another checker")
      val failures = badblobs.map(Q.proveC(source, _changes, _)) ++ Vector(
        Q.proveC(source, Vector(_changes.head, _changes.head), _blobs), Q.proveC(source, null, _blobs)) ++
        crossedrepairs.map(repair => fixture.copy(repair = repair).selectC) ++ Vector(
          fixture.copy(prior = fixture.prior.copy(generation = fixture.prior.generation + 1)).selectC,
          fixture.copy(validation = fixture.validation.copy(payload = fixture.validation.payload.copy(candidate = _old))).selectC,
          Q.selectC(fixture.from, fixture.controller, Some(null), Some(fixture.validation), Some(fixture.repair -> fixture.result),
            fixture.issues, Vector(fixture.prior), fixture.checks))
      Then("each fails explicitly and never dispatches semantic work or closes using inferred coverage")
      failures.foreach(_.isSuccess shouldBe false)
    }

    "refuse foreign or still-open Step issues without erasing original failures" in {
      Given("an eligible source plus a foreign finding, an unresolved TEST issue, or an unfixed original REVIEW issue")
      val fixture = _fixture()
      val state = fixture.issues.get
      val original = state.issues.head
      val foreign = original.copy(identity = "issue-3", summary = "Another unresolved finding", evidenceReference = "foreign-review")
      val testissue = foreign.copy(source = "TEST", sourceIdentity = Some("test:foreign"))
      val alternatives = Vector(state.copy(issues = state.issues :+ foreign), state.copy(issues = state.issues :+ testissue),
        state.copy(issues = state.issues.updated(0, original.copy(latestDisposition = Some("UNRESOLVED")))),
        state.copy(issues = state.issues.updated(0, original.copy(evidenceReference = "foreign-review"))))
      When("actual eligibility checks exact original finding correspondence and latest repair dispositions")
      val selected = alternatives.map(value => fixture.copy(issues = Some(value)).selectC)
      val failedrepair = fixture.result.copy(result = fixture.result.result.copy(value = fixture.result.result.value.copy(
        issueReport = Some(I.Report(fixture.result.result.value.issueReport.get.dispositions.map(_.copy(disposition = "UNRESOLVED")))))))
      val unfixed = fixture.copy(result = failedrepair).selectC
      Then("ordinary affected review remains required and the issue histories remain exact")
      selected.foreach(value => take(value) shouldBe None)
      take(unfixed) shouldBe None
      fixture.issues.get.history shouldBe state.history
    }

    "keep ordinary review for historical absence and preserve the exact bounded payload and canonical successor" in {
      Given("a historical review with no condition claim and a separate actual-shaped covered completion")
      val historical = _fixture(report = _report.copy(completionConditions = None))
      val fixture = _fixture()
      val source = _source(fixture)
      val payload = _payload(source, take(Q.proveC(source, _changes, _blobs)).get)
      val saved = _append(source.from, "Closing", "ordered-review-completion:covered")
      val wire = Q.codec.encode(payload)
      val malformed = Vector("sourceRevision", "step", "generation", "originalReview", "originalGeneration", "originalCandidate",
        "originalChecksReceipt", "repairResult", "currentCandidate", "currentCandidateReference", "currentChecksReceipt",
        "conditionPaths", "changes").map(key => Q.codec.decode(wire.mapObject(_.add("payload",
          payload.asJson.mapObject(_.add(key, Json.Null))))))
      When("the payload codec and ordinary native successor contract consume exact versus crossed values")
      val absent = historical.selectC
      val decoded = Q.codec.decode(wire)
      val valid = Q.successorC(source.from, saved)
      val invalidrecords = Vector(saved.copy(identity = InstanceIdentity("foreign")),
        saved.copy(definition = take(RepositorySyncDefinitionBinding.bindC)),
        saved.copy(causation = CausationReference("ordered-review-completion:other")),
        saved.copy(history = saved.history.drop(1)), _append(saved, "Closing", "ordered-review-completion:covered"))
      val crossed = invalidrecords.map(Q.successorC(source.from, _))
      val rewritten = Q.admittedStateC(source, payload.copy(originalChecksReceipt = "different-old-checks"))
      Then("historical absence selects ordinary review while exact actual references and one full native successor are required")
      take(absent) shouldBe None
      decoded shouldBe Right(payload)
      valid.isSuccess shouldBe true
      take(GoalPhaseNativeHistory.prefixC(saved, source.from.revision)) shouldBe source.from
      source.reference.identity shouldBe source.from.identity.value + ":ordered-review-completion:" + source.from.revision.value
      source.reference.revision shouldBe "1"
      malformed.foreach(_.isLeft shouldBe true)
      crossed.foreach(_.isSuccess shouldBe false)
      rewritten.isSuccess shouldBe false
    }
  }
}
