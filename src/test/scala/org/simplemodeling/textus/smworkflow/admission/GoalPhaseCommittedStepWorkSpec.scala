package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseNativeCandidate as C
import GoalPhaseCommittedStepWork as W
import GoalPhaseOrderedProgression as O
import SplitPhaseExplicitPartitionFixture.take

/** Specification-owned native-shaped values, never installed Git or authority evidence.
  * @since   Nov.  9, 2026
  * @version Nov.  9, 2026
  */
private[admission] object CommittedStepWorkSpecificationFixture {
  val base = O.Snapshot("a" * 40, "b" * 40)
  val intermediate = O.Snapshot("c" * 40, "d" * 40)
  val checkpoint = O.Snapshot("e" * 40, "f" * 40)
  val proof = O.CandidateContinuation(checkpoint, Vector(
    O.HistoryCommit(checkpoint.head, checkpoint.tree, Vector(intermediate.head)),
    O.HistoryCommit(intermediate.head, intermediate.tree, Vector(base.head)),
    O.HistoryCommit(base.head, base.tree, Vector.empty)))
  val commit = O.Commit(checkpoint.head, intermediate.head, checkpoint.tree)
  val paths = Vector("src/one.scala")
  val diff = "diff --git a/src/one.scala b/src/one.scala\n--- a/src/one.scala\n+++ b/src/one.scala\n@@ -1 +1 @@\n-old\n+new\n"
  val plan = O.Plan("checkpoint-plan", "1", "CHECKPOINT-A", "sm-workflow", Vector(
    O.Step("S1", "Accept existing selected work after current checks and review", Vector("src"), Vector.empty, Vector("focused")),
    O.Step("S2", "Continue remaining work", Vector("docs"), Vector("S1"), Vector("focused"))), Vector("phase"))
  val started = take(O.startC(plan, base))
  val inspecting = take(started.acceptC(O.SemanticCompleted(started.next.asInstanceOf[O.Semantic].key, "Specification implementation")))
  val validation = take(inspecting.acceptC(O.CandidateContinuationObserved(checkpoint, proof)))
  private val _empty = take(InstanceRecord(InstanceIdentity("checkpoint-spec-session"),
    take(GoalPhaseRuntimeCatalog.loadC()).definition, initialRevision, Lifecycle.NotStarted, None,
    CorrelationReference("checkpoint-spec-correlation"), CausationReference("checkpoint-spec-start"),
    None, None, None, Vector.empty).validateC)
  private val _inspection = take(_empty.appendC(_empty.revision,
    GoalPhaseNativeRun.history(_empty, "phase.InspectCandidate", "specification-implementation")))
  val originalRecord = take(_inspection.appendC(_inspection.revision,
    GoalPhaseNativeRun.history(_inspection, "phase.Validation", "ordered-candidate:candidate")))
  val original = C.Payload("1", "S1", Vector("src"), Vector("src"), Some(checkpoint), Vector.empty, Some(""), Some(proof))
  val work = W.Work(ContextReference(originalRecord.identity.value, originalRecord.revision.value.toString), base, proof, paths, diff)
  val qualified = original.copy(committedWork = Some(work))
  val requiredReview = take(validation.requireCommittedWorkReviewC)
  val reviewed = {
    val checked = take(requiredReview.acceptC(O.ChecksObserved(checkpoint, Vector("focused"), "spec-current-checks", true)))
    take(checked.acceptC(O.ReviewAdmitted(checked.next.asInstanceOf[O.Semantic].key, checkpoint,
      "spec-current-checks", "spec-independent-review", O.ReviewOutcome.Accept)))
  }
  val closed = take(reviewed.acceptC(O.ExistingStepWorkAccepted(commit, proof, paths)))
}

/** Pure candidate qualification and wire compatibility; no live effect proof.
  * @since   Nov.  9, 2026
  * @version Nov.  9, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseCommittedStepWorkSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import CommittedStepWorkSpecificationFixture as F
  "Committed Step work qualification" should {
    "preserve the original empty pending delta and expose complete committed work separately" in {
      Given("P611-CHECKPOINT-A-01: a pure unchecked candidate at a clean linear checkpoint")
      val saved = C.Saved(F.originalRecord, F.original)
      When("qualification eligibility and full-work accessors interpret the original and supplement")
      val eligible = W.eligible(F.validation, saved)
      Then("the original remains literal and only the supplement selects the complete work")
      eligible shouldBe true
      W.bound(F.qualified, F.work) shouldBe true
      F.qualified.copy(committedWork = None) shouldBe F.original
      F.qualified.changedPaths shouldBe Vector.empty
      F.qualified.diff shouldBe Some("")
      C.workPaths(F.original) shouldBe Vector.empty
      C.workDiff(F.original) shouldBe Some("")
      C.workPaths(F.qualified) shouldBe F.paths
      C.workDiff(F.qualified) shouldBe Some(F.diff)
      F.requiredReview.generation shouldBe F.validation.generation
      F.requiredReview.repairs shouldBe F.validation.repairs
      F.requiredReview.ordinaryRepairs shouldBe F.validation.ordinaryRepairs
      F.requiredReview.base shouldBe F.validation.base
      F.requiredReview.completed shouldBe F.validation.completed
    }

    "refuse pending changes, wrong Step, non-linear ancestry and already checked subjects" in {
      Given("the original pure qualification subject and independent crossed variants")
      val merge = F.proof.copy(lineage = F.proof.lineage.updated(0,
        F.proof.lineage.head.copy(parents = Vector(F.intermediate.head, F.base.head))))
      val crossed = Vector(F.original.copy(changedPaths = F.paths), F.original.copy(diff = Some(F.diff)),
        F.original.copy(step = "S2"), F.original.copy(candidate = Some(F.checkpoint.copy(tree = F.base.tree))),
        F.original.copy(continuation = Some(merge)), F.qualified)
      val checked = take(F.requiredReview.acceptC(O.ChecksObserved(F.checkpoint, Vector("focused"), "checked", true)))
      When("each variant asks for initial qualification")
      val admitted = crossed.map(p => W.eligible(F.validation, C.Saved(F.originalRecord, p)))
      Then("no existing evidence is reinterpreted as coverage of a new subject")
      admitted shouldBe Vector.fill(crossed.size)(false)
      W.eligible(checked, C.Saved(F.originalRecord, F.original)) shouldBe false
      W.valid(F.work.copy(continuation = merge)) shouldBe false
      W.valid(F.work.copy(paths = Vector.empty)) shouldBe false
      W.valid(F.work.copy(paths = Vector("../outside"))) shouldBe false
      W.bound(F.qualified, F.work.copy(acceptedTip = F.intermediate)) shouldBe false
    }

    "bind the supplemental view to exact adjacent native history, source, Step and ownership" in {
      Given("a specification-owned original record and its canonical adjacent supplement")
      val original = C.Saved(F.originalRecord, F.original)
      val record = take(F.originalRecord.appendC(F.originalRecord.revision,
        GoalPhaseNativeRun.history(F.originalRecord, "phase.Validation", "ordered-candidate:committed-work")))
      val supplement = C.Saved(record, F.qualified)
      val crossedRecord = take(F.originalRecord.appendC(F.originalRecord.revision,
        GoalPhaseNativeRun.history(F.originalRecord, "phase.Validation", "ordered-candidate:unrelated")))
      val invalid = Vector(supplement.copy(record = F.originalRecord), supplement.copy(record = crossedRecord),
        supplement.copy(payload = F.qualified.copy(sourceRevision = "2")),
        supplement.copy(payload = F.qualified.copy(step = "S2")),
        supplement.copy(payload = F.qualified.copy(committedWork = Some(F.work.copy(paths = Vector("docs/foreign.md"))))),
        supplement.copy(payload = F.qualified.copy(committedWork = Some(F.work.copy(originalCandidate =
          ContextReference("crossed-session", F.work.originalCandidate.revision))))))
      When("the pure native relation is checked without loading or appending storage")
      val valid = W.relationC(original, supplement, F.validation)
      val refused = invalid.map(W.relationC(original, _, F.validation))
      Then("only the exact source supplement is structurally eligible; this grants no native effect authority")
      valid.isSuccess shouldBe true
      refused.foreach(_.isSuccess shouldBe false)
      original.payload shouldBe F.original
      original.record shouldBe F.originalRecord
    }
  }

  "Committed work candidate v6" should {
    "round trip the supplement and retain literal old v2 omission" in {
      Given("one old raw candidate and its separate qualified view")
      val old = C.codec.encode(F.original)
      val fresh = C.codec.encode(F.qualified)
      When("the versioned reader consumes both")
      val values = Vector(old, fresh).map(C.codec.decode)
      Then("only v6 carries committedWork and old pending facts do not change")
      old.hcursor.get[String]("schemaVersion").toOption shouldBe Some("sm-workflow.goal-phase-native-candidate.v2")
      old.hcursor.downField("payload").downField("committedWork").focus shouldBe None
      fresh.hcursor.get[String]("schemaVersion").toOption shouldBe Some("sm-workflow.goal-phase-native-candidate.v6")
      values shouldBe Vector(Right(F.original), Right(F.qualified))
    }

    "reject null, unknown, crossed and old-version smuggling without inferring acceptance" in {
      Given("the exact new candidate envelope and independent wire corruptions")
      val fresh = C.codec.encode(F.qualified)
      def _payload_(f: Json => Json): Json = fresh.mapObject(o => o.add("payload", f(o("payload").get)))
      val invalid = Vector(_payload_(_.mapObject(_.add("committedWork", Json.Null))),
        _payload_(_.mapObject(_.add("unknown", Json.True))),
        _payload_(_.mapObject(_.add("changedPaths", F.paths.asJson))),
        _payload_(_.mapObject(_.add("committedWork", F.work.copy(originalCandidate = ContextReference("other", "1")).asJson))),
        fresh.mapObject(_.add("schemaVersion", Json.fromString("sm-workflow.goal-phase-native-candidate.v2"))),
        C.codec.encode(F.original).mapObject(_.add("schemaVersion", Json.fromString("sm-workflow.goal-phase-native-candidate.v6"))))
      When("the candidate decoder reads each purported supplement")
      val values = invalid.map(C.codec.decode)
      Then("every malformed extension is refused")
      values.foreach(_.isLeft shouldBe true)
      F.validation.completed shouldBe empty
    }
  }
}
