package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.time.Instant
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.workflow.WorkflowProtocolV1
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import NativeHostEvidence.*

/** Passive current JSON fixture; no enrollment or independent model claim. */
private[admission] object NativeHostReviewEvidenceFixture {
  val snapshot = Json.obj("head" -> Json.fromString("a" * 40), "tree" -> Json.fromString("b" * 40))
  val plan = Json.obj("identity" -> Json.fromString("plan"), "revision" -> Json.fromString("1"),
    "phase" -> Json.fromString("phase"), "repository" -> Json.fromString("repository"))
  val goalInput = Json.obj("instance" -> Json.fromString("instance"), "sourceRevision" -> Json.fromString("7"),
    "workKind" -> Json.fromString("ReviewStep"), "step" -> Json.fromString("step"), "generation" -> Json.fromLong(1),
    "plan" -> plan, "base" -> snapshot, "candidate" -> snapshot, "checksReceipt" -> Json.fromString("checks"),
    "changedPaths" -> Json.arr(Json.fromString("one.scala")))
  val repositoryInput = Json.obj("instance" -> Json.fromString("instance"), "validatedRevision" -> Json.fromString("7"),
    "localTip" -> Json.fromString("a" * 40), "remoteTip" -> Json.fromString("c" * 40), "indexTree" -> Json.fromString("b" * 40),
    "changedPaths" -> Json.arr(Json.fromString("one.scala")), "validationMode" -> Json.fromString("required"),
    "selectedChecks" -> Json.arr(Json.fromString("focused")),
    "checks" -> Json.fromValues(Vector("index-local", "index-remote", "focused").map(id => Json.obj(
      "identity" -> Json.fromString(id), "termination" -> Json.fromString("exited"), "exitCode" -> Json.fromInt(0)))))
  private def result(purpose: Purpose, verdict: Json) = Json.obj(
    "handle" -> Json.obj("componentIdentity" -> Json.fromString("sm-workflow"),
      "workflowIdentity" -> Json.fromString(if (purpose == Purpose.GoalReview) "GoalPhaseWorkflow" else "RepositorySyncWorkflow"),
      "workflowRevision" -> Json.fromString("actual-definition"), "instanceIdentity" -> Json.fromString("instance"),
      "protocolVersion" -> Json.fromString(WorkflowProtocolV1.schemaVersion)), "runId" -> Json.fromString("instance"),
    "continuationId" -> Json.fromString("actual-slot"), "worker" -> Json.fromString("actual-reviewer"),
    "typeIdentity" -> Json.fromString(if (purpose == Purpose.GoalReview) "GoalPhaseWorkResult" else "RepositorySyncNativeMergeReviewResult"),
    "reference" -> Json.obj("identity" -> Json.fromString("result"), "revision" -> Json.fromString("1")), "verdict" -> verdict)
  def binding(purpose: Purpose, input: Option[Json] = None, verdict: Option[Json] = None): Json = Json.obj(
    "componentInstance" -> Json.fromString("owned-component-instance"), "attestation" -> Json.obj(
      "kind" -> Json.fromString(if (purpose == Purpose.GoalReview) "goal-review" else "repository-review"),
      "target" -> input.getOrElse(if (purpose == Purpose.GoalReview) goalInput else repositoryInput),
      "result" -> result(purpose, verdict.getOrElse(if (purpose == Purpose.GoalReview)
        Json.obj("outcome" -> Json.fromString("accept"), "repairSummary" -> Json.Null, "findings" -> Json.arr())
        else Json.obj("decision" -> Json.fromString("accept"), "findings" -> Json.arr())))))
  def alterResult(binding: Json)(f: Json => Json): Json = binding.mapObject(_.add("attestation",
    binding.hcursor.downField("attestation").focus.get.mapObject(_.add("result", f(binding.hcursor.downField("attestation").downField("result").focus.get)))))
}

/** Real target/disposition selection, not whole input or transport certification.
  * @since   Oct.  8, 2026
  *  version Oct. 11, 2026
  *  version Oct. 16, 2026
  * @version Oct. 18, 2026
  * @author  ASAMI, Tomoharu
  */
final class NativeHostReviewScopeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import NativeHostReviewEvidenceFixture.*
  private val now = Instant.parse("2026-10-08T00:00:00Z")
  private val declared = Declaration("workflow", DataStore.CollectionId.EntityStore(EntityCollectionId("test", "host", "evidence")), "independent-host")
  private def record(purpose: Purpose, binding: Json) = Evidence(schema, purpose.identity, "result-at-1", "result", "1",
    declared.issuer, "actual-reviewer", binding, now.minusSeconds(1).toString)
  private def read(purpose: Purpose, binding: Json) = decodeStorageC(encodeStorageC(record(purpose, binding)).toOption.get).toOption.get
  private def observe(value: Evidence, purpose: Purpose, binding: Json) = observeC(value, declared, purpose, "result-at-1", binding, now)

  "Structured Goal issue scope" should afterWord(
    "in spec:goal-phase-fix-issues, failure:P62-C-REVIEW-SCOPE-01, phase:6.2"
  ) {
    "retain complete issue state and report meaning and refuse crossed selected values" in {
      Given("docs/spec/goal-phase-fix-issues.md; P62-C-REVIEW-SCOPE-01; a specification-issued review with actual issue state and explicit recurring coverage")
      val issue = GoalPhaseFixIssues.Issue("issue-1", "repository", "step", "REVIEW", Some("review-finding"),
        "Selected required behavior", "prior-review", "REOPENED", Some("RECURRING"))
      val state = GoalPhaseFixIssues.State(Vector(issue), Vector(
        GoalPhaseFixIssues.Transition(issue.identity, None, "OPEN", "first-review"),
        GoalPhaseFixIssues.Transition(issue.identity, Some("OPEN"), "RESOLVED", "passing-review"),
        GoalPhaseFixIssues.Transition(issue.identity, Some("RESOLVED"), "REOPENED", "prior-review")),
        Some(GoalPhaseFixIssues.Movement("prior-review", 1, 0, 0, 1, true)))
      val report = GoalPhaseFixIssues.Report(Vector(GoalPhaseFixIssues.Disposition(
        issue.identity, "RECURRING", "Independent review observed the selected recurrence", Some(1))))
      val target = goalInput.mapObject(_.add("issues", state.asJson))
      val verdict = Json.obj("outcome" -> Json.fromString("repair"), "repairSummary" -> Json.fromString("Repair the selected recurrence"),
        "findings" -> Json.arr(Json.fromString("Observed recurring required behavior")), "issueReport" -> report.asJson)
      val original = binding(Purpose.GoalReview, Some(target), Some(verdict))
      When("normal evidence storage encodes and decodes the selected issue state and independent report")
      val saved = read(Purpose.GoalReview, original)
      val accepted = observe(saved, Purpose.GoalReview, original)
      Then("all issue fields, transitions, movement, disposition rationale and finding ordinal survive and apply to the same review")
      saved.binding.hcursor.downField("attestation").downField("target").downField("issues").focus shouldBe Some(state.asJson)
      saved.binding.hcursor.downField("attestation").downField("result").downField("verdict").downField("issueReport").focus shouldBe Some(report.asJson)
      accepted.isSuccess shouldBe true

      Given("the same stored independent result with separately changed issue meaning, history or movement")
      val states = Vector(
        state.copy(issues = Vector(issue.copy(summary = "Different required behavior"))),
        state.copy(issues = Vector(issue.copy(repository = "other-repository"))),
        state.copy(issues = Vector(issue.copy(step = "other-step"))),
        state.copy(issues = Vector(issue.copy(sourceIdentity = None))),
        state.copy(issues = Vector(issue.copy(latestDisposition = Some("NOT_REVIEWED")))),
        state.copy(history = state.history.updated(1, state.history(1).copy(evidenceReference = "different-review"))),
        state.copy(movement = state.movement.map(_.copy(reopenedIssues = 0))),
        state.copy(movement = state.movement.map(_.copy(correspondenceKnown = false))))
      When("each changed issue target is presented against that stored independent review")
      val crossedstates = states.map(changed => observe(saved, Purpose.GoalReview, binding(Purpose.GoalReview,
        Some(target.mapObject(_.add("issues", changed.asJson))), Some(verdict))))
      Then("a different issue context cannot reuse the row")
      crossedstates.foreach(_.isSuccess shouldBe false)

      Given("the same stored result with separately changed coverage disposition, rationale or finding correspondence")
      val disposition = report.dispositions.head
      val reports = Vector(disposition.copy(disposition = "NOT_REVIEWED", findingOrdinal = None),
        disposition.copy(rationale = "Different independent explanation"), disposition.copy(findingOrdinal = Some(2)))
        .map(entry => report.copy(dispositions = Vector(entry)))
      When("each changed report is presented against the stored independent review")
      val crossedreports = reports.map(changed => observe(saved, Purpose.GoalReview, binding(Purpose.GoalReview,
        Some(target), Some(verdict.mapObject(_.add("issueReport", changed.asJson))))))
      Then("different independent coverage cannot reuse the row")
      crossedreports.foreach(_.isSuccess shouldBe false)
    }

    "apply valid empty and absent coverage to the same selected issue context" in {
      Given("docs/spec/goal-phase-fix-issues.md; P62-C-EMPTY-COVERAGE-02; the same review with a resolved TEST issue and no required REVIEW coverage")
      val issue = GoalPhaseFixIssues.Issue("issue-1", "repository", "step", "TEST", Some("focused"),
        "Selected tested behavior", "passing-check", "RESOLVED", None)
      val state = GoalPhaseFixIssues.State(Vector(issue), Vector(
        GoalPhaseFixIssues.Transition(issue.identity, None, "OPEN", "failing-check"),
        GoalPhaseFixIssues.Transition(issue.identity, Some("OPEN"), "RESOLVED", "passing-check")),
        Some(GoalPhaseFixIssues.Movement("passing-check", 0, 1, 0, 0, true)))
      val target = goalInput.mapObject(_.add("issues", state.asJson))
      val verdict = Json.obj("outcome" -> Json.fromString("accept"), "findings" -> Json.arr())
      val empty = GoalPhaseFixIssues.Report(Vector.empty)
      val absent = binding(Purpose.GoalReview, Some(target), Some(verdict))
      val supplied = binding(Purpose.GoalReview, Some(target), Some(verdict.mapObject(_.add("issueReport", empty.asJson))))
      When("normal storage selects either valid no-coverage representation and observation receives either representation")
      val saved = Vector(absent, supplied).map(read(Purpose.GoalReview, _))
      val accepted = saved.flatMap(value => Vector(absent, supplied).map(observe(value, Purpose.GoalReview, _)))
      Then("both apply to one selected meaning while the complete actual issue state remains bound")
      saved.map(_.binding).distinct.size shouldBe 1
      accepted.foreach(_.isSuccess shouldBe true)
      saved.foreach { value =>
        value.binding.hcursor.downField("attestation").downField("target").downField("issues").focus shouldBe Some(state.asJson)
        value.binding.hcursor.downField("attestation").downField("result").downField("verdict").downField("issueReport").focus shouldBe None
      }

      Given("the same no-coverage review row with a different selected issue history")
      val changed = state.copy(history = state.history.updated(1, state.history(1).copy(evidenceReference = "other-check")))
      When("observation compares that changed issue context with the stored empty-coverage review")
      val crossed = observe(saved.last, Purpose.GoalReview, binding(Purpose.GoalReview,
        Some(target.mapObject(_.add("issues", changed.asJson))), Some(verdict)))
      Then("empty coverage cannot erase the actual selected issue context")
      crossed.isSuccess shouldBe false

      Given("a valid nonempty report claims actual coverage of the selected TEST issue")
      val report = GoalPhaseFixIssues.Report(Vector(GoalPhaseFixIssues.Disposition(
        issue.identity, "RESOLVED", "Independent review observed the selected resolution", None)))
      val covered = binding(Purpose.GoalReview, Some(target), Some(verdict.mapObject(_.add("issueReport", report.asJson))))
      When("normal storage and observation select the nonempty report")
      val nonempty = read(Purpose.GoalReview, covered)
      val crossedreports = Vector(observe(saved.last, Purpose.GoalReview, covered), observe(nonempty, Purpose.GoalReview, supplied))
      Then("the actual report remains selected and neither coverage claim can reuse the other row")
      nonempty.binding.hcursor.downField("attestation").downField("result").downField("verdict").downField("issueReport").focus shouldBe Some(report.asJson)
      crossedreports.foreach(_.isSuccess shouldBe false)
    }

    "keep missing issue fields absent for existing Goal and Repository reviews" in {
      Given("docs/spec/goal-phase-fix-issues.md; P62-C-REVIEW-SCOPE-01; existing Goal and Repository review fixtures without issue fields")
      Vector(Purpose.GoalReview, Purpose.RepositoryReview).foreach { purpose =>
        val original = binding(purpose)
        When("normal evidence storage and observation consume a review with no issue claim")
        val saved = read(purpose, original)
        val accepted = observe(saved, purpose, original)
        Then("the original review remains applicable and no empty issue state or report is fabricated")
        accepted.isSuccess shouldBe true
        saved.binding.hcursor.downField("attestation").downField("target").downField("issues").focus shouldBe None
        saved.binding.hcursor.downField("attestation").downField("result").downField("verdict").downField("issueReport").focus shouldBe None
      }
    }

    "reject present null malformed and invalid issue values during encoding decoding and observation" in {
      Given("docs/spec/goal-phase-fix-issues.md; P62-C-REVIEW-SCOPE-01; an existing independent review and present invalid issue state or report values")
      val original = binding(Purpose.GoalReview)
      val saved = read(Purpose.GoalReview, original)
      val wire = encodeStorageC(saved).toOption.get
      val payload = io.circe.parser.parse(wire.substring(schema.length + 1)).toOption.get
      val issue = GoalPhaseFixIssues.Issue("issue-1", "repository", "step", "REVIEW", None,
        "Required behavior", "prior-review", "OPEN", None)
      val state = GoalPhaseFixIssues.State(Vector(issue), Vector.empty, None)
      val disposition = GoalPhaseFixIssues.Disposition(issue.identity, "RESOLVED", "Observed actual resolution", None)
      val report = GoalPhaseFixIssues.Report(Vector(disposition))
      val badstates = Vector(Json.Null, Json.True, Json.obj(),
        state.copy(issues = Vector(issue.copy(status = "UNKNOWN"))).asJson,
        state.copy(issues = Vector(issue, issue)).asJson,
        state.copy(history = Vector(GoalPhaseFixIssues.Transition("issue-2", None, "OPEN", "unrelated"))).asJson,
        state.copy(movement = Some(GoalPhaseFixIssues.Movement("prior-review", -1, 0, 0, 0, false))).asJson,
        state.asJson.mapObject(_.add("movement", Json.Null)))
      val badreports = Vector(Json.Null, Json.True, Json.obj(),
        report.copy(dispositions = Vector(disposition.copy(disposition = "UNKNOWN"))).asJson,
        report.copy(dispositions = Vector(disposition.copy(rationale = ""))).asJson,
        report.copy(dispositions = Vector(disposition.copy(findingOrdinal = Some(0)))).asJson,
        report.copy(dispositions = Vector(disposition, disposition)).asJson,
        Json.obj("dispositions" -> Json.arr(disposition.asJson.mapObject(_.add("findingOrdinal", Json.Null)))))
      val invalid = badstates.map(value => binding(Purpose.GoalReview,
        Some(goalInput.mapObject(_.add("issues", value))))) ++ badreports.map(value => binding(Purpose.GoalReview,
        verdict = Some(Json.obj("outcome" -> Json.fromString("accept"), "findings" -> Json.arr(), "issueReport" -> value))))
      When("each invalid presence reaches storage encoding, stored-row decoding and independent review observation")
      val encoded = invalid.map(value => encodeStorageC(record(Purpose.GoalReview, value)))
      val decoded = invalid.map(value => decodeStorageC(schema + ":" + payload.mapObject(_.add("binding", value)).noSpaces))
      val observed = invalid.map(observe(saved, Purpose.GoalReview, _))
      Then("all three consumers reject rather than silently omitting the malformed issue claim")
      encoded.foreach(_.isSuccess shouldBe false)
      decoded.foreach(_.isSuccess shouldBe false)
      observed.foreach(_.isSuccess shouldBe false)
    }
  }

  "Independent review attestation" should {
    "retain actual validation metadata as selected independent review context and reject malformed presence" in {
      Given("a real fixture Batch and the same ordinary review assignment with that selected metadata context")
      val batch = GoalPhaseNativeMetadataFixture.batch
      val metadata = Vector(batch).asJson
      val target = NativeHostReviewEvidenceFixture.goalInput.mapObject(_.add("validationMetadata", metadata))
      val binding = NativeHostReviewEvidenceFixture.binding(Purpose.GoalReview, Some(target))
      When("the host scope selects and encodes the actual metadata context and a different real subset selection")
      val selected = NativeHostReviewScope.select(Purpose.GoalReview, binding).toOption.get
      val retained = read(Purpose.GoalReview, binding)
      val originalcheck = GoalPhaseNativeMetadataFixture.check
      val changedcheck = originalcheck.copy(metadata = originalcheck.metadata.map(_.copy(supplemental = Vector.empty)))
      val changed = SplitPhaseExplicitPartitionFixture.take(GoalPhaseNativeMetadata.prepareC(
        Vector(changedcheck), GoalPhaseNativeMetadataFixture.loader))._2.asJson
      val crossed = NativeHostReviewEvidenceFixture.binding(Purpose.GoalReview,
        Some(target.mapObject(_.add("validationMetadata", changed))))
      Then("the selected scope retains the full canonical Batch and another valid selection cannot reuse that evidence")
      selected.hcursor.downField("attestation").downField("target").get[Vector[Json]]("validationMetadata") shouldBe
        Right(Vector(batch.asJson))
      retained.binding shouldBe selected
      observe(retained, Purpose.GoalReview, binding).isSuccess shouldBe true
      observe(retained, Purpose.GoalReview, crossed).isSuccess shouldBe false
      Vector(Json.Null, Json.True, Json.arr(Json.Null), Json.arr(Json.obj())).foreach { malformed =>
        NativeHostReviewScope.select(Purpose.GoalReview, NativeHostReviewEvidenceFixture.binding(Purpose.GoalReview,
          Some(target.mapObject(_.add("validationMetadata", malformed))))).isLeft shouldBe true
      }
    }

    "retain actual effective conditions and report meaning while refusing contradictory quality targets or dispositions" in {
      Given("spec:goal-phase-acceptance-boundary E5 R6; actual selected effective quality and report values in a fixture independent review")
      val boundary = GoalPhaseAcceptanceBoundary.Effective(Some("Explicit selected usage"), Vector(
        GoalPhaseAcceptanceBoundary.EffectiveCondition("behavior", "all", true, "Selected behavior", "project"),
        GoalPhaseAcceptanceBoundary.EffectiveCondition("extra", "step", false, "Excluded robustness", "adopted")))
      val report = GoalPhaseAcceptanceBoundary.Report(Vector(
        GoalPhaseAcceptanceBoundary.Observation("behavior", "satisfied", "Observed selected behavior"),
        GoalPhaseAcceptanceBoundary.Observation("extra", "unsatisfied", "Excluded suggestion")), Vector("Consider extra later"))
      val target = goalInput.mapObject(_.add("effectiveAcceptance", boundary.asJson))
      val verdict = Json.obj("outcome" -> Json.fromString("accept"), "findings" -> Json.arr(), "acceptance" -> report.asJson)
      When("normal evidence storage selects the actual typed boundary and report")
      val saved = read(Purpose.GoalReview, binding(Purpose.GoalReview, Some(target), Some(verdict)))
      Then("complete condition scope, required false, source, observations and advisories survive without a custody certificate")
      saved.binding.hcursor.downField("attestation").downField("target").downField("effectiveAcceptance").focus shouldBe Some(boundary.asJson)
      saved.binding.hcursor.downField("attestation").downField("result").downField("verdict").downField("acceptance").focus shouldBe Some(report.asJson)
      observe(saved, Purpose.GoalReview, binding(Purpose.GoalReview, Some(target), Some(verdict))).isSuccess shouldBe true
      saved.binding.noSpaces should not include "contextSnapshot"
      val replacements = Vector(boundary.conditions.head.copy(scope = "phase"),
        boundary.conditions.head.copy(required = false), boundary.conditions.head.copy(source = "adopted"),
        boundary.conditions.head.copy(description = "Different quality meaning"))
      replacements.foreach { replacement =>
        Given("the same independent result reference with a different meaningful effective condition")
        When("review applicability compares scope, requirement, source or description")
        val changed = target.mapObject(_.add("effectiveAcceptance", boundary.copy(
          conditions = boundary.conditions.updated(0, replacement)).asJson))
        val result = observe(saved, Purpose.GoalReview, binding(Purpose.GoalReview, Some(changed), Some(verdict)))
        Then("a review for the original selected conditions cannot attest the changed target")
        result.isSuccess shouldBe false
      }
      val reports = Vector(report.copy(conditions = report.conditions.updated(0, report.conditions.head.copy(status = "unsatisfied"))),
        report.copy(conditions = report.conditions.updated(1, report.conditions(1).copy(detail = "Different excluded observation"))),
        report.copy(advisories = Vector("Different advisory")))
      reports.foreach { changed =>
        Given("a different observation status, detail or advisory at the same independently attested result slot")
        When("the existing independent review reader selects the submitted report")
        val result = observe(saved, Purpose.GoalReview, binding(Purpose.GoalReview, Some(target),
          Some(verdict.mapObject(_.add("acceptance", changed.asJson)))))
        Then("the unrelated report cannot reuse the original review attestation")
        result.isSuccess shouldBe false
      }
      Given("a selected report with an unknown observation status")
      When("the existing storage encoder validates the typed report")
      val invalid = verdict.mapObject(_.add("acceptance", report.copy(conditions = Vector(
        report.conditions.head.copy(status = "unknown"))).asJson))
      Then("invalid typed presence fails rather than being omitted")
      encodeStorageC(record(Purpose.GoalReview, binding(Purpose.GoalReview, Some(target), Some(invalid)))).isSuccess shouldBe false
    }
    "select actual Goal review targets without Plan policy diff log or Workspace body certificates" in {
      Given("Spec: docs/spec/native-host-review-applicability.md; Rule: R1; Example: E1; independently issued actual review scope")
      val saved = read(Purpose.GoalReview, binding(Purpose.GoalReview))
      val changed = goalInput.mapObject(_.add("plan", plan.mapObject(_.add("steps", Json.True).add("repairLimit", Json.Null)))
        .add("diff", Json.True).add("checks", Json.True))
      val transported = alterResult(binding(Purpose.GoalReview, Some(changed)))(_.mapObject(
        _.add("contextSnapshot", Json.False).add("completionFacts", Json.True)))
      When("unselected planning policy and diagnostic transport data change")
      val result = observe(saved, Purpose.GoalReview, transported)
      Then("actual candidate and disposition still resolve and no copied declarations or logs are saved")
      result.isSuccess shouldBe true
      val target = saved.binding.hcursor.downField("attestation").downField("target").focus.get
      target.hcursor.downField("plan").focus.get.asObject.get.keys.toSet shouldBe Set("identity", "revision", "phase", "repository")
      saved.binding.noSpaces should not include "policyIdentity"
      saved.binding.noSpaces should not include "workspaceRevision"
      observe(saved, Purpose.GoalReview, binding(Purpose.GoalReview, verdict = Some(Json.obj("outcome" -> Json.fromString("accept"),
        "repairSummary" -> Json.True, "findings" -> Json.arr())))).isSuccess shouldBe true
    }
    "refuse foreign assignment candidate coverage result worker and semantic disposition" in {
      Given("Spec: docs/spec/native-host-review-applicability.md; Rule: R1; Example: E2; actual selected reviewer and candidate")
      val saved = read(Purpose.GoalReview, binding(Purpose.GoalReview))
      val inputs = Vector(goalInput.mapObject(_.add("candidate", snapshot.mapObject(_.add("tree", Json.fromString("d" * 40))))),
        goalInput.mapObject(_.add("step", Json.fromString("foreign-step"))), goalInput.mapObject(_.add("checksReceipt", Json.fromString("foreign-check"))),
        goalInput.mapObject(_.add("changedPaths", Json.arr(Json.fromString("other.scala")))))
      val resultVariants = Vector("continuationId").map(key => alterResult(binding(Purpose.GoalReview))(_.mapObject(_.add(key, Json.fromString("foreign"))))) ++
        Vector(alterResult(binding(Purpose.GoalReview))(_.mapObject(_.add("worker", Json.fromString("foreign-reviewer")))))
      When("actual target or reviewer/result scope differs")
      val results = inputs.map(v => observe(saved, Purpose.GoalReview, binding(Purpose.GoalReview, Some(v)))) ++ resultVariants.map(observe(saved, Purpose.GoalReview, _))
      Then("none becomes independent review acceptance")
      results.foreach(_.isSuccess shouldBe false)
      observe(saved.copy(participant = "foreign-reviewer"), Purpose.GoalReview, binding(Purpose.GoalReview)).isSuccess shouldBe false
      observe(saved.copy(reference = "foreign-result"), Purpose.GoalReview, binding(Purpose.GoalReview)).isSuccess shouldBe false
      observe(saved.copy(revision = "foreign-revision"), Purpose.GoalReview, binding(Purpose.GoalReview)).isSuccess shouldBe false
      val repair = Json.obj("outcome" -> Json.fromString("repair"), "repairSummary" -> Json.fromString("Fix actual issue"), "findings" -> Json.arr(Json.fromString("actual finding")))
      observe(saved, Purpose.GoalReview, binding(Purpose.GoalReview, verdict = Some(repair))).isSuccess shouldBe false
    }
    "retain semantic repair direction rather than treating its directive as mere presentation" in {
      Given("Spec: docs/spec/native-host-review-applicability.md; Rule: R2; Example: E3; an actual repair result")
      val verdict = Json.obj("outcome" -> Json.fromString("repair"), "repairSummary" -> Json.fromString("Fix the assigned boundary"), "findings" -> Json.arr(Json.fromString("Actual missing case")))
      val saved = read(Purpose.GoalReview, binding(Purpose.GoalReview, verdict = Some(verdict)))
      When("a repair directive or findings would be replaced using the same result reference")
      val changed = verdict.mapObject(_.add("repairSummary", Json.fromString("Repair another boundary")))
      Then("actual repair meaning cannot be changed by reusing an independently attested result slot")
      observe(saved, Purpose.GoalReview, binding(Purpose.GoalReview, verdict = Some(changed))).isSuccess shouldBe false
      saved.binding.hcursor.downField("attestation").downField("result").downField("verdict").get[String]("repairSummary").toOption shouldBe Some("Fix the assigned boundary")
    }
    "select actual merge native targets checks and verdict without diff rationale or output copies" in {
      Given("Spec: docs/spec/native-host-review-applicability.md; Rule: R1; Example: E4; actual validated merge review")
      val saved = read(Purpose.RepositoryReview, binding(Purpose.RepositoryReview))
      val changed = repositoryInput.mapObject(_.add("validationReason", Json.True).add("localDiff", Json.Null).add("remoteDiff", Json.True)
        .add("checks", repositoryInput.hcursor.downField("checks").focus.get.mapArray(_.map(_.mapObject(_.add("stdout", Json.True).add("stderr", Json.Null))))))
      When("only explanation and process-output projections differ")
      observe(saved, Purpose.RepositoryReview, binding(Purpose.RepositoryReview, Some(changed))).isSuccess shouldBe true
      Then("actual native parent tree check outcome and verdict contradictions still refuse")
      val realChanges = Vector(repositoryInput.mapObject(_.add("remoteTip", Json.fromString("d" * 40))),
        repositoryInput.mapObject(_.add("checks", repositoryInput.hcursor.downField("checks").focus.get.mapArray(_.map(_.mapObject(_.add("exitCode", Json.fromInt(1))))))))
      realChanges.foreach(v => observe(saved, Purpose.RepositoryReview, binding(Purpose.RepositoryReview, Some(v))).isSuccess shouldBe false)
      saved.binding.noSpaces should not include "stdout"
      saved.binding.noSpaces should not include "localDiff"
    }
    "consume actual review scope without copied completion revision or model snapshot witnesses" in {
      Given("Spec: docs/spec/native-host-review-applicability.md; Rule: R4; Example: E8; Goal and Repository independently issued reviews")
      Vector(Purpose.GoalReview, Purpose.RepositoryReview).foreach { purpose =>
        val original = binding(purpose)
        val saved = read(purpose, original)
        val variants = Vector(
          alterResult(original)(_.mapObject(_.remove("expectedRevision").remove("contextSnapshot"))),
          alterResult(original)(_.mapObject(_.add("expectedRevision", Json.True).add("contextSnapshot", Json.False))),
          alterResult(original)(_.mapObject(_.add("expectedRevision", Json.fromString("later-revision"))
            .add("contextSnapshot", Json.obj("workflowRevision" -> Json.fromString("later-definition"),
              "modelRevision" -> Json.fromString("later-model"))))))
        When("obsolete witnesses are absent, unusable or different in current records")
        val results = variants.map(observe(saved, purpose, _))
        val prior = saved.binding.mapObject(_.add("attestation", saved.binding.hcursor.downField("attestation").focus.get
          .mapObject(_.add("result", saved.binding.hcursor.downField("attestation").downField("result").focus.get
            .mapObject(_.add("expectedRevision", Json.True).add("modelRevision", Json.False))))))
        val wire = encodeStorageC(saved).toOption.get
        val payload = io.circe.parser.parse(wire.substring(schema.length + 1)).toOption.get
        val retained = decodeStorageC(schema + ":" + payload.mapObject(_.add("binding", prior)).noSpaces)
        Then("the actual attestation survives while both producers and current readers discard the redundant copies")
        results.foreach(_.isSuccess shouldBe true)
        retained.toOption shouldBe Some(saved)
        encodeStorageC(record(purpose, variants.head)).toOption shouldBe Some(wire)
        saved.binding.hcursor.downField("attestation").downField("result").focus.get.asObject.get.keys.toSet shouldBe
          Set("handle", "runId", "continuationId", "reference", "typeIdentity", "worker", "verdict")
        saved.binding.noSpaces should not include "expectedRevision"
        saved.binding.noSpaces should not include "modelRevision"
        observe(saved, purpose, alterResult(original)(_.mapObject(_.add("continuationId", Json.fromString("another-pending-slot"))))).isSuccess shouldBe false
        observe(saved.copy(participant = "another-worker"), purpose, variants.head).isSuccess shouldBe false
      }
    }
  }
}
