package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.time.Instant
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.workflow.WorkflowProtocolV1
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import NativeHostEvidence.*

/** Passive JSON fixture only; no host enrollment or independent model claim. */
private[admission] object NativeHostReviewEvidenceFixture {
  val snapshot = Json.obj("head" -> Json.fromString("a" * 40), "tree" -> Json.fromString("b" * 40))
  val plan = Json.obj("identity" -> Json.fromString("plan"), "revision" -> Json.fromString("1"),
    "phase" -> Json.fromString("phase"), "repository" -> Json.fromString("repository"), "steps" -> Json.arr(), "repairLimit" -> Json.fromInt(3))
  val goalInput = Json.obj("instanceIdentity" -> Json.fromString("instance"), "sourceRevision" -> Json.fromString("7"),
    "kind" -> Json.fromString("ReviewStep"), "stepIdentity" -> Json.fromString("step"), "generation" -> Json.fromLong(1),
    "selectedPlan" -> plan, "originalBase" -> snapshot, "candidate" -> snapshot, "checksReceipt" -> Json.fromString("checks"),
    "review" -> Json.obj("policyIdentity" -> Json.fromString("old-policy"), "policyRevision" -> Json.fromInt(1),
      "changedPaths" -> Json.arr(Json.fromString("one.scala")), "diff" -> Json.fromString("Original diagnostic diff"), "checks" -> Json.arr()))
  val repositoryInput = Json.obj("instanceIdentity" -> Json.fromString("instance"), "validatedRevision" -> Json.fromString("7"),
    "localTip" -> Json.fromString("a" * 40), "remoteTip" -> Json.fromString("c" * 40), "indexTree" -> Json.fromString("b" * 40),
    "changedPaths" -> Json.arr(Json.fromString("one.scala")), "validationMode" -> Json.fromString("required"),
    "selectedChecks" -> Json.arr(Json.fromString("focused")), "validationReason" -> Json.fromString("Original rationale"),
    "localDiff" -> Json.fromString("Original local diff"), "remoteDiff" -> Json.fromString("Original remote diff"),
    "checks" -> Json.fromValues(Vector("index-local", "index-remote", "focused").map(id => Json.obj(
      "identity" -> Json.fromString(id), "termination" -> Json.fromString("exited"), "exitCode" -> Json.fromInt(0),
      "stdout" -> Json.fromString("Original diagnostics"), "stderr" -> Json.fromString("")))))
  private def wireResult(purpose: Purpose, verdict: Json) = Json.obj(
    "schemaVersion" -> Json.fromString(WorkflowProtocolV1.schemaVersion), "kind" -> Json.fromString("CONTINUATION_RESULT"),
    "handle" -> Json.obj("componentIdentity" -> Json.fromString("sm-workflow"),
      "workflowIdentity" -> Json.fromString(if (purpose == Purpose.GoalReview) "GoalPhaseWorkflow" else "RepositorySyncWorkflow"),
      "workflowRevision" -> Json.fromString("actual-definition"), "instanceIdentity" -> Json.fromString("instance"),
      "protocolVersion" -> Json.fromString(WorkflowProtocolV1.schemaVersion)), "runId" -> Json.fromString("instance"),
    "continuationId" -> Json.fromString("actual-slot"), "expectedRevision" -> Json.fromString("8"),
    "contextSnapshot" -> Json.obj("workflowRevision" -> Json.fromString("actual-definition"), "modelRevision" -> Json.fromString("actual-model"),
      "workspaceRevision" -> Json.fromString("old-diagnostic-workspace")), "completionFacts" -> Json.arr(),
    "executionEvidence" -> Json.obj("workerIdentity" -> Json.fromString("actual-reviewer"), "references" -> Json.arr(), "modelIdentity" -> Json.Null),
    "result" -> Json.obj("typeIdentity" -> Json.fromString(if (purpose == Purpose.GoalReview) "GoalPhaseWorkResult" else "RepositorySyncNativeMergeReviewResult"),
      "reference" -> Json.obj("identity" -> Json.fromString("result"), "revision" -> Json.fromString("1")),
      "value" -> Json.obj("schemaVersion" -> Json.fromString(if (purpose == Purpose.GoalReview) "sm-workflow.goal-phase-native-work-result.v1"
        else "sm-workflow.repository-native-merge-review-result.v1"), "payload" -> verdict)))
  def raw(purpose: Purpose, input: Option[Json] = None, verdict: Option[Json] = None): Json = Json.obj(
    "componentInstance" -> Json.fromString("owned-component-instance"), "value" -> Json.obj(
      "input" -> Json.obj("schemaVersion" -> Json.fromString(if (purpose == Purpose.GoalReview) "sm-workflow.goal-phase-native-work-request.v1"
        else "sm-workflow.repository-native-merge-review-request.v2"), "payload" -> input.getOrElse(if (purpose == Purpose.GoalReview) goalInput else repositoryInput)),
      "result" -> wireResult(purpose, verdict.getOrElse(if (purpose == Purpose.GoalReview)
        Json.obj("outcome" -> Json.fromString("accept"), "summary" -> Json.fromString("Original explanation"), "findings" -> Json.arr())
        else Json.obj("decision" -> Json.fromString("accept"), "rationale" -> Json.fromString("Original explanation"), "findings" -> Json.arr())))))
  def alterResult(binding: Json)(f: Json => Json): Json = binding.mapObject(_.add("value",
    binding.hcursor.downField("value").focus.get.mapObject(_.add("result", f(binding.hcursor.downField("value").downField("result").focus.get)))))
}

/** Real target/disposition selection, not whole input or transport certification.
  * @since   Oct.  8, 2026
  * @version Oct.  9, 2026
  */
final class NativeHostReviewScopeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import NativeHostReviewEvidenceFixture.*
  private val now = Instant.parse("2026-10-08T00:00:00Z")
  private val declared = Declaration("workflow", DataStore.CollectionId.EntityStore(EntityCollectionId("test", "host", "evidence")), "independent-host")
  private def record(purpose: Purpose, binding: Json) = Evidence(schema, purpose.identity, "result-at-1", "result", "1",
    declared.issuer, "actual-reviewer", binding, now.minusSeconds(1).toString)
  private def read(purpose: Purpose, binding: Json) = decodeStorageC(encodeStorageC(record(purpose, binding)).toOption.get).toOption.get
  private def observe(value: Evidence, purpose: Purpose, binding: Json) = observeC(value, declared, purpose, "result-at-1", binding, now)

  "Independent review attestation" should {
    "select actual Goal review targets without Plan policy diff log or Workspace body certificates" in {
      Given("Spec: docs/spec/native-host-review-applicability.md; Rule: R1; Example: E1; independently issued actual review scope")
      val saved = read(Purpose.GoalReview, raw(Purpose.GoalReview))
      val changed = goalInput.mapObject(_.add("selectedPlan", plan.mapObject(_.add("steps", Json.True).add("repairLimit", Json.Null)))
        .add("review", goalInput.hcursor.downField("review").focus.get.mapObject(_.add("policyIdentity", Json.Null).add("policyRevision", Json.True)
          .add("diff", Json.True).add("checks", Json.True))))
      val transported = alterResult(raw(Purpose.GoalReview, Some(changed)))(r => r.mapObject(_.add("contextSnapshot",
        r.hcursor.downField("contextSnapshot").focus.get.mapObject(_.add("workspaceRevision", Json.True))).add("completionFacts", Json.True)))
      When("unselected planning policy and diagnostic transport data change")
      val result = observe(saved, Purpose.GoalReview, transported)
      Then("actual candidate and disposition still resolve and no copied declarations or logs are saved")
      result.isSuccess shouldBe true
      val target = saved.binding.hcursor.downField("attestation").downField("target").focus.get
      target.hcursor.downField("plan").focus.get.asObject.get.keys.toSet shouldBe Set("identity", "revision", "phase", "repository")
      saved.binding.noSpaces should not include "policyIdentity"
      saved.binding.noSpaces should not include "workspaceRevision"
      observe(saved, Purpose.GoalReview, raw(Purpose.GoalReview, verdict = Some(Json.obj("outcome" -> Json.fromString("accept"),
        "summary" -> Json.True, "findings" -> Json.arr())))).isSuccess shouldBe true
    }
    "refuse foreign assignment candidate coverage result worker and semantic disposition" in {
      Given("Spec: docs/spec/native-host-review-applicability.md; Rule: R1; Example: E2; actual selected reviewer and candidate")
      val saved = read(Purpose.GoalReview, raw(Purpose.GoalReview))
      val inputs = Vector(goalInput.mapObject(_.add("candidate", snapshot.mapObject(_.add("tree", Json.fromString("d" * 40))))),
        goalInput.mapObject(_.add("stepIdentity", Json.fromString("foreign-step"))), goalInput.mapObject(_.add("checksReceipt", Json.fromString("foreign-check"))),
        goalInput.mapObject(_.add("review", goalInput.hcursor.downField("review").focus.get.mapObject(_.add("changedPaths", Json.arr(Json.fromString("other.scala")))))))
      val resultVariants = Vector("continuationId").map(key => alterResult(raw(Purpose.GoalReview))(_.mapObject(_.add(key, Json.fromString("foreign"))))) ++
        Vector(alterResult(raw(Purpose.GoalReview))(r => r.mapObject(_.add("executionEvidence", r.hcursor.downField("executionEvidence").focus.get
          .mapObject(_.add("workerIdentity", Json.fromString("foreign-reviewer")))))))
      When("actual target or reviewer/result scope differs")
      val results = inputs.map(v => observe(saved, Purpose.GoalReview, raw(Purpose.GoalReview, Some(v)))) ++ resultVariants.map(observe(saved, Purpose.GoalReview, _))
      Then("none becomes independent review acceptance")
      results.foreach(_.isSuccess shouldBe false)
      observe(saved.copy(participant = "foreign-reviewer"), Purpose.GoalReview, raw(Purpose.GoalReview)).isSuccess shouldBe false
      observe(saved.copy(reference = "foreign-result"), Purpose.GoalReview, raw(Purpose.GoalReview)).isSuccess shouldBe false
      observe(saved.copy(revision = "foreign-revision"), Purpose.GoalReview, raw(Purpose.GoalReview)).isSuccess shouldBe false
      val repair = Json.obj("outcome" -> Json.fromString("repair"), "summary" -> Json.fromString("Fix actual issue"), "findings" -> Json.arr(Json.fromString("actual finding")))
      observe(saved, Purpose.GoalReview, raw(Purpose.GoalReview, verdict = Some(repair))).isSuccess shouldBe false
    }
    "retain semantic repair direction rather than treating its directive as mere presentation" in {
      Given("Spec: docs/spec/native-host-review-applicability.md; Rule: R2; Example: E3; an actual repair result")
      val verdict = Json.obj("outcome" -> Json.fromString("repair"), "summary" -> Json.fromString("Fix the assigned boundary"), "findings" -> Json.arr(Json.fromString("Actual missing case")))
      val saved = read(Purpose.GoalReview, raw(Purpose.GoalReview, verdict = Some(verdict)))
      When("a repair directive or findings would be replaced using the same result reference")
      val changed = verdict.mapObject(_.add("summary", Json.fromString("Repair another boundary")))
      Then("actual repair meaning cannot be changed by reusing an independently attested result slot")
      observe(saved, Purpose.GoalReview, raw(Purpose.GoalReview, verdict = Some(changed))).isSuccess shouldBe false
      saved.binding.hcursor.downField("attestation").downField("result").downField("verdict").get[String]("repairSummary").toOption shouldBe Some("Fix the assigned boundary")
    }
    "select actual merge native targets checks and verdict without diff rationale or output copies" in {
      Given("Spec: docs/spec/native-host-review-applicability.md; Rule: R1; Example: E4; actual validated merge review")
      val saved = read(Purpose.RepositoryReview, raw(Purpose.RepositoryReview))
      val changed = repositoryInput.mapObject(_.add("validationReason", Json.True).add("localDiff", Json.Null).add("remoteDiff", Json.True)
        .add("checks", repositoryInput.hcursor.downField("checks").focus.get.mapArray(_.map(_.mapObject(_.add("stdout", Json.True).add("stderr", Json.Null))))))
      When("only explanation and process-output projections differ")
      observe(saved, Purpose.RepositoryReview, raw(Purpose.RepositoryReview, Some(changed))).isSuccess shouldBe true
      Then("actual native parent tree check outcome and verdict contradictions still refuse")
      val realChanges = Vector(repositoryInput.mapObject(_.add("remoteTip", Json.fromString("d" * 40))),
        repositoryInput.mapObject(_.add("checks", repositoryInput.hcursor.downField("checks").focus.get.mapArray(_.map(_.mapObject(_.add("exitCode", Json.fromInt(1))))))))
      realChanges.foreach(v => observe(saved, Purpose.RepositoryReview, raw(Purpose.RepositoryReview, Some(v))).isSuccess shouldBe false)
      saved.binding.noSpaces should not include "stdout"
      saved.binding.noSpaces should not include "localDiff"
    }
    "read legacy complete records as actual attestations and reject copied body reintroduction in current storage" in {
      Given("Spec: docs/spec/native-host-review-applicability.md; Rule: R3; Example: E5; recognized earlier review evidence formats")
      When("old rows are read without irrelevant full-body witnesses")
      Vector(Purpose.GoalReview, Purpose.RepositoryReview).foreach { purpose =>
        val original = raw(purpose)
        val current = read(purpose, original)
        val wire = encodeStorageC(current).toOption.get
        val payload = io.circe.parser.parse(wire.substring(schema.length + 1)).toOption.get
        for (version <- Vector("v1", "v2", "v3", "v4", "v5", "v6")) {
          val oldSchema = "sm-workflow.native-host-evidence." + version
          val old = payload.mapObject(_.add("schemaVersion", Json.fromString(oldSchema)).add("binding", original))
          val expired = if (version == "v1") old.mapObject(_.add("validUntil", Json.True)) else old
          decodeStorageC(oldSchema + ":" + expired.noSpaces).toOption.get shouldBe current
        }
        Then("original scope event time and issuer survive, with no row rewrite or renewed evidence")
        current.occurredAt shouldBe record(purpose, original).occurredAt
        decodeStorageC(schema + ":" + payload.mapObject(_.add("binding", original)).noSpaces).isSuccess shouldBe false
        val invalid = current.binding.mapObject(_.add("attestation", current.binding.hcursor.downField("attestation").focus.get.mapObject(_.add("managementHash", Json.True))))
        decodeStorageC(schema + ":" + payload.mapObject(_.add("binding", invalid)).noSpaces).isSuccess shouldBe false
      }
    }
    "consume actual review scope without copied completion revision or model snapshot witnesses" in {
      Given("Spec: docs/spec/native-host-review-applicability.md; Rule: R4; Example: E8; Goal and Repository independently issued reviews")
      Vector(Purpose.GoalReview, Purpose.RepositoryReview).foreach { purpose =>
        val original = raw(purpose)
        val saved = read(purpose, original)
        val variants = Vector(
          alterResult(original)(_.mapObject(_.remove("expectedRevision").remove("contextSnapshot"))),
          alterResult(original)(_.mapObject(_.add("expectedRevision", Json.True).add("contextSnapshot", Json.False))),
          alterResult(original)(_.mapObject(_.add("expectedRevision", Json.fromString("later-revision"))
            .add("contextSnapshot", Json.obj("workflowRevision" -> Json.fromString("later-definition"),
              "modelRevision" -> Json.fromString("later-model"))))))
        When("obsolete witnesses are absent, unusable or different in legacy transport and saved attestations")
        val results = variants.map(observe(saved, purpose, _))
        val prior = saved.binding.mapObject(_.add("attestation", saved.binding.hcursor.downField("attestation").focus.get
          .mapObject(_.add("result", saved.binding.hcursor.downField("attestation").downField("result").focus.get
            .mapObject(_.add("expectedRevision", Json.True).add("modelRevision", Json.False))))))
        val wire = encodeStorageC(saved).toOption.get
        val payload = io.circe.parser.parse(wire.substring(schema.length + 1)).toOption.get
        val retained = decodeStorageC(schema + ":" + payload.mapObject(_.add("binding", prior)).noSpaces)
        Then("the actual attestation survives while both producers and current/old readers discard the redundant copies")
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
