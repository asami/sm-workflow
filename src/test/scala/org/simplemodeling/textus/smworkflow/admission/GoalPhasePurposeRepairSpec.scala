package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhasePurposeRepair as P
import GoalPhaseNativeWork as W
import GoalPhaseAcceptanceBoundary as B
import GoalPhaseOrderedProgression as O
import SplitPhaseExplicitPartitionFixture.take

/** Specification-owned semantic fixtures; no independent authority is issued.
  * @since   Oct. 24, 2026
  * @version Oct. 24, 2026
  */
private[admission] object GoalPhasePurposeRepairFixture {
  val required = B.Condition("required-one", "step", true, "Preserve required behavior")
  val excluded = B.Condition("excluded-one", "step", false, "An excluded idea")
  val raw = GoalPhaseReviewRepairAdoptionFixture.raw.copy(acceptance = Some(B.Adoption("Selected usage", Vector(required, excluded))))
  val plan = take(B.captureC(None, raw))
  def controller: O.State = GoalPhaseReviewRepairAdoptionFixture.review(plan, 0)
  def request: W.Request = {
    val state = controller
    GoalPhaseReviewRepairAdoptionFixture.request(state).copy(issues = None,
      purposeRepair = P.capture("ReviewStep", Some("current"), plan))
  }
  val report = B.Report(Vector(B.Observation(required.identity, "satisfied", "Required behavior is correct")), Vector("A future idea"))
  val purpose: P.Source = P.StepPurpose(1, "current", "The selected purpose still has an independently observed defect", Vector("current.scala"))
  val result = W.Result("repair", "Repair the original Step defect", Vector("An actual original-purpose defect"),
    acceptance = Some(report), repairFindingSources = Some(Vector(purpose)))
}

@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhasePurposeRepairSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import GoalPhasePurposeRepairFixture.*

  "New issued Step purpose repair" should {
    "enter ordinary Repair without a Human or new acceptance condition when required conditions pass" in {
      Given("a newly issued Step contract, satisfied required observations and one independently judged typed purpose defect")
      val state = controller
      val input = request
      When("actual-request-aware independent review admission completes")
      val next = take(GoalPhaseNativeReview.completedRequestC(input, result, "independent-review", state))
      Then("the existing same-Step repair generation and ordinary budget own the correction")
      next.stage shouldBe O.Stage.Repair
      next.next.asInstanceOf[O.Semantic].key.step shouldBe input.stepIdentity
      next.plan shouldBe input.selectedPlan
      next.base shouldBe input.originalBase
      next.completed shouldBe input.completed
      next.generation shouldBe input.generation + 1
      next.repairs shouldBe state.repairs + 1
      next.ordinaryRepairs shouldBe state.ordinaryRepairs + 1
      next.repairDirective.get.findings shouldBe result.findings
      next.decision shouldBe None
      GoalPhaseNativeReview.completedStateC(result, "independent-review", state).isSuccess shouldBe false
    }

    "cover every required failure exactly in report order while retaining additional purpose findings" in {
      Given("two required unsatisfied observations and one purpose defect in an issued contract")
      val extra = required.copy(identity = "required-two")
      val selected = take(B.captureC(None, raw.copy(acceptance = raw.acceptance.map(a => a.copy(conditions = a.conditions :+ extra)))))
      val input = request.copy(selectedPlan = selected)
      val observations = B.Report(Vector(B.Observation(required.identity, "unsatisfied", "First required failure"),
        B.Observation(extra.identity, "unsatisfied", "Second required failure")), Vector("Only advisory"))
      val findings = Vector("First required failure", "Additional purpose failure", "Second required failure")
      val sources = Vector[P.Source](P.RequiredCondition(1, required.identity),
        P.StepPurpose(2, "current", "Inside the original purpose", Vector("current.scala")), P.RequiredCondition(3, extra.identity))
      val mixed = result.copy(findings = findings, acceptance = Some(observations), repairFindingSources = Some(sources))
      When("the exact full source sequence and separately missing, crossed or misclassified sequences are admitted")
      val accepted = B.admitRequestC(input, mixed)
      val variants = Vector(
        mixed.copy(repairFindingSources = Some(sources.updated(0, P.StepPurpose(1, "current", "Cannot omit a required source", Vector("current.scala"))))),
        mixed.copy(repairFindingSources = Some(sources.updated(0, P.RequiredCondition(1, excluded.identity)))),
        mixed.copy(repairFindingSources = Some(sources.updated(0, P.RequiredCondition(1, "unknown")))),
        mixed.copy(findings = findings.updated(0, "Changed required detail")),
        mixed.copy(repairFindingSources = Some(Vector(P.RequiredCondition(1, extra.identity), sources(1), P.RequiredCondition(3, required.identity)))),
        mixed.copy(acceptance = Some(observations.copy(conditions = observations.conditions.tail))),
        mixed.copy(acceptance = Some(observations.copy(conditions = observations.conditions.updated(0, observations.conditions.head.copy(status = "not-evaluated"))))),
        mixed.copy(acceptance = Some(observations.copy(conditions = observations.conditions.updated(0, observations.conditions.head.copy(status = "satisfied"))))))
      Then("only complete ordered actual required failures plus additional purpose defects admit")
      accepted.isSuccess shouldBe true
      variants.foreach(value => B.admitRequestC(input, value).isSuccess shouldBe false)
    }

    "retain accept gates and reject untyped purpose reinterpretation and non-review source use" in {
      Given("the same actual request and a required-only legacy failure form")
      val legacy = result.copy(findings = Vector("Required failure"), repairFindingSources = None,
        acceptance = Some(B.Report(Vector(B.Observation(required.identity, "unsatisfied", "Required failure")), Vector.empty)))
      When("legacy equality, accept gates and source ownership are evaluated")
      val accept = result.copy(outcome = "accept", findings = Vector.empty, repairFindingSources = None)
      Then("only honest required-only repair or satisfied acceptance succeeds")
      B.admitRequestC(request, legacy).isSuccess shouldBe true
      B.admitRequestC(request.copy(purposeRepair = None), legacy).isSuccess shouldBe true
      B.admitRequestC(request, accept).isSuccess shouldBe true
      Vector(result.copy(repairFindingSources = None), accept.copy(repairFindingSources = result.repairFindingSources),
        accept.copy(acceptance = legacy.acceptance), accept.copy(acceptance = Some(report.copy(conditions = Vector.empty))),
        accept.copy(acceptance = Some(report.copy(conditions = report.conditions.map(_.copy(status = "not-evaluated"))))))
        .foreach(value => B.admitRequestC(request, value).isSuccess shouldBe false)
      Vector("ImplementStep", "RepairStep", "ReviewPhase").foreach(kind =>
        B.admitRequestC(request.copy(kind = kind, purposeRepair = None), result).isSuccess shouldBe false)
      B.admitRequestC(request.copy(purposeRepair = None), result).isSuccess shouldBe false
      B.admitRequestC(request.copy(purpose = "Different purpose"), result).isSuccess shouldBe false
      B.admitRequestC(request.copy(stepIdentity = Some("first")), result).isSuccess shouldBe false
    }
  }

  "Exact versioned source codecs" should {
    "retain old versions and absent-field projections while new issued work reopens with its immutable descriptor" in {
      Given("old unmarked work and result plus new explicitly marked work and typed result")
      val oldinput = request.copy(purposeRepair = None)
      val oldresult = W.Result("accept", "Accepted old report", Vector.empty)
      When("both generations encode and decode without any history-time capture")
      val oldwire = W.inputCodec.encode(oldinput)
      val oldreply = W.resultCodec.encode(oldresult)
      val newwire = W.inputCodec.encode(request)
      val newreply = W.resultCodec.encode(result)
      Then("old schema/type/projections remain absent and new values retain exact explicit meaning")
      oldwire.hcursor.get[String]("schemaVersion") shouldBe Right("sm-workflow.goal-phase-native-work-request.v2")
      oldwire.hcursor.downField("payload").downField("purposeRepair").focus shouldBe None
      oldreply shouldBe Json.obj("schemaVersion" -> "sm-workflow.goal-phase-native-work-result.v1".asJson,
        "payload" -> Json.obj("outcome" -> "accept".asJson, "summary" -> "Accepted old report".asJson, "findings" -> Json.arr()))
      W.inputCodec.decode(oldwire) shouldBe Right(oldinput)
      W.resultCodec.decode(oldreply) shouldBe Right(oldresult)
      W.inputCodec.decode(newwire) shouldBe Right(request)
      W.resultCodec.decode(newreply) shouldBe Right(result)
      W.inputCodec.decode(W.inputCodec.encode(W.inputCodec.decode(oldwire).toOption.get)).toOption.get.purposeRepair shouldBe None
      newwire.hcursor.get[String]("schemaVersion") shouldBe Right("sm-workflow.goal-phase-native-work-request.v3")
      newreply.hcursor.get[String]("schemaVersion") shouldBe Right("sm-workflow.goal-phase-native-work-result.v2")
      W.inputCodec.typeIdentity shouldBe "GoalPhaseWorkRequest"
      W.resultCodec.typeIdentity shouldBe "GoalPhaseWorkResult"
      W.inputCodec.decode(newwire.mapObject(_.add("schemaVersion", "sm-workflow.goal-phase-native-work-request.v2".asJson))).isLeft shouldBe true
      W.resultCodec.decode(newreply.mapObject(_.add("schemaVersion", "sm-workflow.goal-phase-native-work-result.v1".asJson))).isLeft shouldBe true
      W.inputCodec.decode(oldwire.mapObject(_.add("schemaVersion", "sm-workflow.goal-phase-native-work-request.v3".asJson))).isLeft shouldBe true
      W.resultCodec.decode(oldreply.mapObject(_.add("schemaVersion", "sm-workflow.goal-phase-native-work-result.v2".asJson))).isLeft shouldBe true
    }

    "reject null unknown extra fields ordinal gaps bounds and paths outside actual ownership" in {
      Given("one exact tagged source and malformed representations of every declared constraint")
      val source = purpose.asJson
      val malformed = Vector(Json.Null, source.mapObject(_.add("kind", "unknown".asJson)),
        source.mapObject(_.add("conditionIdentity", "excluded-one".asJson)), source.mapObject(_.add("unknown", true.asJson)),
        source.mapObject(_.add("findingOrdinal", 0.asJson)), source.mapObject(_.add("findingOrdinal", 129.asJson)),
        source.mapObject(_.add("rationale", Json.Null)), source.mapObject(_.add("rationale", " ".asJson)),
        source.mapObject(_.add("rationale", ("x" * 16385).asJson)), source.mapObject(_.add("rationale", "bad\u0000text".asJson)),
        source.mapObject(_.add("affectedPaths", Vector.empty[String].asJson)),
        source.mapObject(_.add("affectedPaths", Vector.fill(2)("current.scala").asJson)),
        source.mapObject(_.add("affectedPaths", (1 to 257).map(n => s"current.scala/$n").asJson))) ++
        Vector("../current.scala", "/current.scala", "current.scala/../foreign", "current.scala\n", "current.scala\\foreign")
          .map(path => source.mapObject(_.add("affectedPaths", Vector(path).asJson)))
      When("closed decoding and actual request admission consume these sources")
      val decoded = malformed.map(_.as[P.Source])
      val outside = result.copy(repairFindingSources = Some(Vector(P.StepPurpose(1, "current", "Another scope", Vector("first.scala")))))
      Then("malformed shapes, sequence defects, excess findings and foreign scope reject")
      decoded.foreach(_.isLeft shouldBe true)
      B.admitRequestC(request, outside).isSuccess shouldBe false
      P.validateSourcesC(Some(Vector(purpose, purpose))).isSuccess shouldBe false
      P.validateSourcesC(Some(Vector.empty)).isSuccess shouldBe false
      P.validateSourcesC(Some((1 to 129).map(n => P.RequiredCondition(n, required.identity): P.Source).toVector)).isSuccess shouldBe false
      val nullreply = W.resultCodec.encode(result).mapObject(_.add("payload",
        W.resultCodec.encode(result).hcursor.downField("payload").focus.get.mapObject(_.add("repairFindingSources", Json.Null))))
      W.resultCodec.decode(nullreply).isLeft shouldBe true
    }

    "box public sources explicitly and reject raw wrapper null or unknown fields before generated decoding" in {
      Given("the public optional wrapper and exact native source vector")
      val publicvalue = P.publicSources(result.repairFindingSources)
      val raw = GoalPhaseNativeMetadata.record(Json.obj("repairFindingSources" -> Json.obj("sources" -> Vector(purpose).asJson)))
      When("public boxing and raw-ingress validation consume the original Records")
      val restored = take(P.internalSourcesC(publicvalue))
      val unknown = GoalPhaseNativeMetadata.record(Json.obj("repairFindingSources" -> Json.obj("sources" -> Vector(purpose).asJson, "extra" -> true.asJson)))
      val rejectednull = NativeWorkflowOperationInput.recordC(org.goldenport.protocol.Request.ofOperation("review").copy(
        properties = List(org.goldenport.protocol.Property("result", "{\"repairFindingSources\":null}", None))), "result")
      Then("the source array retains exact meaning while malformed wrappers are rejected")
      restored shouldBe result.repairFindingSources
      P.validatePublicResultC(raw).isSuccess shouldBe true
      P.validatePublicResultC(unknown).isSuccess shouldBe false
      rejectednull.isSuccess shouldBe false
      P.internalSourcesC(Some(org.simplemodeling.textus.smworkflow.value.GoalPhaseRepairFindingSources(Vector.empty))).isSuccess shouldBe false
    }
  }
}
