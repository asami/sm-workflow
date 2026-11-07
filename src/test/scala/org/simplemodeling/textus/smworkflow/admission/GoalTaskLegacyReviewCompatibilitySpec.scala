package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.time.Instant
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.processexecution.SbtValidationEstimate
import org.goldenport.cncf.datastore.DataStore
import org.simplemodeling.model.datatype.EntityCollectionId
import NativeExternalEvidence.*
import StandaloneFixExecution.given
import NativeReviewEvidenceFixture.*

/** Regression for historical typed receipt encoding at the review evidence boundary. */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class GoalTaskLegacyReviewCompatibilitySpec extends GoalTaskValidationContinuationManagedSpec {
  private val now = Instant.parse("2026-11-07T00:00:00Z")
  private val declared = Declaration("workflow", DataStore.CollectionId.EntityStore(EntityCollectionId("test", "host", "evidence")), "independent-host")
  private val facts = WorkflowSbtValidation.Facts(WorkflowSbtValidation.Kind.Compile, WorkflowSbtValidation.Outcome.Succeeded, None, None)
  private val explicit = facts.asJson.mapObject(_.add("estimate", Json.Null)).as[WorkflowSbtValidation.Facts].toOption.get
  private val check = GoalPhaseNativeValidation.Check("compile", 1, WorkflowSbtValidation.Kind.Compile)
  private val input = StandaloneFixExecution.Input("repo", "task", "objective", Vector("src"), Vector("compile"),
    Vector("condition"), Vector(ContextReference("source", "1")), true)
  private val initial = StandaloneFixExecution.startC(input, "participant", Vector(check)).toOption.get
  private val receipt = RepositorySyncNativeMergeValidation.Receipt("compile", "exited", Some(0), "", "", Some(explicit))
  private val work = StandaloneFixExecution.Work("session", "7", "Review", initial.copy(stage = "Review", receipts = Vector(receipt)))
  private val verdict = StandaloneFixExecution.Result("repair", "repair finding", Vector("finding"), Vector.empty, Vector.empty)
  private def target(value: StandaloneFixExecution.Work) = {
    val base = binding(Purpose.GoalReview)
    val attestation = base.hcursor.downField("attestation").focus.get
    val result = attestation.hcursor.downField("result").focus.get.mapObject(_.add("verdict", verdict.asJson))
    base.mapObject(_.add("attestation", attestation.mapObject(_.add("target", value.asJson).add("result", result))))
  }
  private def record(value: Json) = Evidence(schema, Purpose.StandaloneReview.identity, "result-at-1", "result", "1",
    declared.issuer, "actual-reviewer", value, now.minusSeconds(1).toString)
  private def observe(value: Evidence, binding: Json) = observeC(value, declared, Purpose.StandaloneReview, "result-at-1", binding, now)

  "Historical GoalTask review evidence" should {
    "compare typed absent estimate values while retaining stored wire provenance and identity boundaries" in {
      val old = target(work)
      val fresh = target(work.copy(state = work.state.copy(receipts = Vector(receipt.copy(sbt = Some(facts))))))
      val saved = decodeStorageC(encodeStorageC(record(old)).toOption.get).toOption.get
      observe(saved, fresh).isSuccess shouldBe true
      work.state.receipts.head.sbt.get.asJson.hcursor.downField("estimate").focus shouldBe Some(Json.Null)
      facts.asJson.hcursor.downField("estimate").focus shouldBe None
      val changed = target(work.copy(state = work.state.copy(receipts = Vector(receipt.copy(identity = "other")))))
      observe(saved, changed).isSuccess shouldBe false
      observe(saved.copy(issuer = "other"), fresh).isSuccess shouldBe false
      observe(saved.copy(occurredAt = now.plusSeconds(1).toString), fresh).isSuccess shouldBe false
      observe(saved.copy(participant = "other"), fresh).isSuccess shouldBe false
      observe(saved, alterResult(fresh)(_.mapObject(_.add("worker", "other".asJson)))).isSuccess shouldBe false
      observe(saved, fresh.mapObject(_.add("componentInstance", "other".asJson))).isSuccess shouldBe false
      observeC(saved, declared, Purpose.GoalReview, "result-at-1", fresh, now).isSuccess shouldBe false
    }

    "keep distinct real estimates distinct in all review targets" in {
      val estimate = SbtValidationEstimate("changed leaf", "candidate-tree",
        Vector(SbtValidationEstimate.Leaf("fixture.ExampleSpec", "selected", 1000000000L, true)),
        SbtValidationEstimate.Preparation(1000000000L, 0L, 0L, 1000000000L), BigDecimal("1.5"))
      val selected = WorkflowSbtValidation.Facts(WorkflowSbtValidation.Kind.TestOnly, WorkflowSbtValidation.Outcome.Succeeded,
        Some(WorkflowSbtValidation.Selection(Vector("fixture.ExampleSpec"), 1, 1)), None, Some(estimate))
      val other = selected.copy(estimate = Some(estimate.copy(selectionReason = "other reason")))
      val one = target(work.copy(state = work.state.copy(receipts = Vector(receipt.copy(sbt = Some(selected))))))
      val two = target(work.copy(state = work.state.copy(receipts = Vector(receipt.copy(sbt = Some(other))))))
      observe(record(one), two).isSuccess shouldBe false
      val raw = Json.obj("identity" -> "focused".asJson, "termination" -> "exited".asJson,
        "exitCode" -> 0.asJson, "sbt" -> selected.asJson)
      val rawOther = raw.mapObject(_.add("sbt", other.asJson))
      Vector(Purpose.GoalReview -> "testChecks", Purpose.RepositoryReview -> "checks").foreach { (purpose, field) =>
        val base = if (purpose == Purpose.GoalReview) goalInput else repositoryInput
        val absent = raw.mapObject(_.add("sbt", facts.asJson))
        val historical = absent.mapObject(_.add("sbt", explicit.asJson))
        NativeReviewScope.select(purpose, binding(purpose, Some(base.mapObject(_.add(field, Json.arr(absent)))))) shouldBe
          NativeReviewScope.select(purpose, binding(purpose, Some(base.mapObject(_.add(field, Json.arr(historical))))))
        val first = binding(purpose, Some(base.mapObject(_.add(field, Json.arr(raw)))))
        val second = binding(purpose, Some(base.mapObject(_.add(field, Json.arr(rawOther)))))
        NativeReviewScope.select(purpose, first) should not be NativeReviewScope.select(purpose, second)
      }
    }
  }
}
