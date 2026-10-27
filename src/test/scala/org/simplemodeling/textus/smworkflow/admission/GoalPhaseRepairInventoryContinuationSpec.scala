package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.cncf.workflow.ContextReference
import GoalPhaseOrderedProgression as O
import GoalPhasePlanRevision as R
import GoalPhaseAcceptanceBoundary as A
import GoalPhaseRepairInventoryContinuation as K
import SplitPhaseExplicitPartitionFixture.take

/** Pure inventory contracts; the CLI specification owns native provenance.
  * @since   Oct. 27, 2026
  * @version Oct. 27, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseRepairInventoryContinuationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _oid(value: Char): String = value.toString * 40
  private val _base = O.Snapshot(_oid('a'), _oid('b'))
  private val _plan = O.Plan("inventory-plan", "1", "phase-fixture", "repository",
    Vector(O.Step("S1", "Implement the accepted product", Vector("src/Product.scala"), Vector.empty, Vector("compile"))),
    Vector("phase-test"), repairLimit = 2)
  private val _directive = O.RepairDirective("Repair the actual failed Phase check", Vector("Product check fails"))
  private def _key(state: O.State): O.WorkKey = state.next.asInstanceOf[O.Semantic].key
  private def _closed: O.State = {
    val started = take(O.startC(_plan, _base, Some(GoalPhaseRuntimeRepairControl.version)))
    val ready = take(started.acceptC(O.SemanticCompleted(_key(started), "Product ready")))
    val candidate = O.Snapshot(_base.head, _oid('c'))
    val observed = take(ready.acceptC(O.CandidateObserved(candidate)))
    val checked = take(observed.acceptC(O.ChecksObserved(candidate, Vector("compile"), "step-check", true)))
    val accepted = take(checked.acceptC(O.ReviewAdmitted(_key(checked), candidate, "step-check", "independent-review", O.ReviewOutcome.Accept)))
    val intent = take(accepted.acceptC(O.ClosureIntentSaved(candidate, "step-intent")))
    take(intent.acceptC(O.StepCommitObserved("step-intent", O.Commit(_oid('d'), _base.head, candidate.tree))))
  }
  private def _repair: O.State = {
    val closed = _closed
    val seen = take(closed.acceptC(O.CandidateObserved(closed.tipSnapshot)))
    val failed = take(seen.acceptC(O.ChecksObserved(closed.tipSnapshot, _plan.phaseChecks, "real-failure", false)))
    take(failed.continueInitialPhaseCheckRepairC("real-failure", _directive))
  }
  private def _proposed(state: O.State): O.Plan = state.plan.copy(revision = "2",
    phaseRepairInventory = Vector("src/test/ProductSpec.scala"))

  "E1 the exact phase repair inventory delta" should afterWord(
    "in spec:goal-phase-repair-inventory-continuation, example:E1, rules:R1,R2, phase:P99-INVENTORY-01") {
    "preserve accepted Steps base acceptance counters policy and directive while advancing one generation" in {
      Given("docs/spec/goal-phase-repair-inventory-continuation.md R1,R2 E1: all Steps closed and one already reserved Phase repair")
      val repair = _repair
      val decision = take(repair.acceptC(O.NativeFailure("A necessary test file is outside the original inventory")))
      When("the active repair or its ordinary Decision receives the exact finite append")
      Vector(repair, decision).foreach { source =>
        val selected = take(source.adoptPhaseRepairInventoryC(_proposed(source), "actual-source-reference"))
        Then("the same Phase repair retains every original accepted value and consumes no new batch")
        selected.stage shouldBe O.Stage.Repair
        selected.cursor shouldBe source.cursor
        selected.plan.steps shouldBe source.plan.steps
        selected.plan.phaseOwnedPaths shouldBe Vector("src/Product.scala", "src/test/ProductSpec.scala")
        selected.completed shouldBe source.completed
        selected.base shouldBe source.base
        selected.generation shouldBe source.generation + 1
        GoalPhaseRepairProgress.snapshot(selected) shouldBe GoalPhaseRepairProgress.snapshot(source)
        selected.repairControl shouldBe source.repairControl
        selected.repairObservations shouldBe source.repairObservations
        selected.repairDirective shouldBe source.repairDirective
        selected.candidate shouldBe None
        selected.checksReceipt shouldBe None
        selected.reviewEvidence shouldBe None
        selected.closureIntent shouldBe None
        _key(selected).step shouldBe None
      }
    }

    "capture the actual raw proposal with the original effective boundary rather than current project defaults" in {
      Given("docs/spec/goal-phase-repair-inventory-continuation.md R1,R2 E1: an inventory-only typed semantic planRevision")
      val decision = take(_repair.acceptC(O.NativeFailure("Inventory append required")))
      val proposal = R.Proposal("inventory-proposal", "1", "Correct the failed product check", _proposed(decision))
      When("semantic capture selects the explicit inventory scope")
      val captured = take(R.captureSemanticProposalC(decision, proposal, Some(A.Project(Vector(
        A.Condition("new-host-default", "phase", true, "A later unrelated default"))))))
      Then("the exact raw and saved effective acceptance survive and the captured scope is explicit")
      captured.proposal shouldBe proposal
      captured.plan.effectiveAcceptance shouldBe decision.plan.effectiveAcceptance
      captured.revisionScope shouldBe Some(R.phaseRepairInventoryScope)
      take(R.adoptedC(decision, captured, "semantic-source")).stage shouldBe O.Stage.Repair
    }

    "preserve original Step roots while granting an inventory entry only its exact file identity" in {
      Given("docs/spec/goal-phase-repair-inventory-continuation.md R1,R6 E1: original Step directory ownership and a literal inventory entry")
      val plan = _plan.copy(steps = _plan.steps.map(_.copy(ownedPaths = Vector("original-sources"))),
        phaseRepairInventory = Vector("new-tests"))
      When("actual changed paths are compared with the complete Phase ownership")
      plan.phasePathsOwned(Vector("original-sources/A.scala", "original-sources/nested/B.scala", "new-tests")) shouldBe true
      Then("the new literal never expands to descendants or duplicate actual paths")
      plan.phasePathsOwned(Vector("new-tests/A.scala", "new-tests/B.scala")) shouldBe false
      plan.phasePathsOwned(Vector("new-tests", "new-tests/A.scala")) shouldBe false
      plan.phasePathsOwned(Vector("new-tests", "new-tests")) shouldBe false
      plan.phasePathsOwned(Vector("unowned.scala")) shouldBe false
    }

    "reject changed contracts Steps policy check sets identities overlap and invalid inventory syntax" in {
      Given("docs/spec/goal-phase-repair-inventory-continuation.md R1,R2 E1: an immutable accepted Plan and a proposed literal append")
      val state = _repair
      val proposed = _proposed(state)
      val crossed = Vector(proposed.copy(identity = "other"), proposed.copy(repository = "other-component"),
        proposed.copy(phase = "other-phase"), proposed.copy(revision = state.plan.revision),
        proposed.copy(repairLimit = 3), proposed.copy(phaseChecks = Vector("other-test")),
        proposed.copy(acceptance = Some(A.Adoption("Changed purpose", Vector.empty))),
        proposed.copy(steps = proposed.steps.map(_.copy(purpose = "Other purpose"))),
        proposed.copy(steps = proposed.steps.map(_.copy(checks = Vector("other-check")))),
        proposed.copy(steps = proposed.steps :+ O.Step("S2", "New Step", Vector("other.scala"), Vector("S1"), Vector("compile"))))
      val paths = Vector(Vector.empty[String], Vector("src/Product.scala"), Vector("src"), Vector("src/*.scala"),
        Vector("src/?.scala"), Vector("src/../Outside.scala"), Vector("/absolute.scala"), Vector("src/Bad\u0000.scala"),
        Vector("src/test/A.scala", "src/test/A.scala"), Vector("src/test", "src/test/A.scala"),
        Vector.tabulate(257)(index => "src/test/Test" + index + ".scala"))
      When("each crossed contract or nonliteral append is admitted")
      val results = crossed.map(state.adoptPhaseRepairInventoryC(_, "source")) ++ paths.map(value =>
        state.adoptPhaseRepairInventoryC(proposed.copy(phaseRepairInventory = value), "source"))
      Then("all refuse before changing the original reserved repair")
      results.foreach(_.isSuccess shouldBe false)
      state.plan shouldBe _plan
      state.adoptPhaseRepairInventoryC(proposed, "").isSuccess shouldBe false
      _closed.adoptPhaseRepairInventoryC(proposed, "source").isSuccess shouldBe false
      O.startC(proposed, _base).isSuccess shouldBe false
    }

    "preserve the prefix of an already admitted inventory and deny other Plan routes" in {
      Given("docs/spec/goal-phase-repair-inventory-continuation.md R1,R2 E1: one admitted inventory revision")
      val original = _repair
      val first = take(original.adoptPhaseRepairInventoryC(_proposed(original), "source"))
      val second = first.plan.copy(revision = "3", phaseRepairInventory = first.plan.phaseRepairInventory :+ "src/test/SecondSpec.scala")
      val decision = take(first.acceptC(O.NativeFailure("Another exact file is needed")))
      When("the same repair appends a second file and ordinary Plan routes receive that inventory change")
      val continued = take(decision.adoptPhaseRepairInventoryC(second, "second-source"))
      Then("the inventory prefix and counts survive while unrelated adoption and refresh reject")
      continued.plan.phaseRepairInventory.take(1) shouldBe first.plan.phaseRepairInventory
      continued.repairs shouldBe original.repairs
      continued.ordinaryRepairs shouldBe original.ordinaryRepairs
      decision.adoptRevisionC(second, "source").isSuccess shouldBe false
      decision.adoptRemainingRevisionC(second, "source").isSuccess shouldBe false
      first.refreshedC(second, issued = false).toOption.get.isLeft shouldBe true
      decision.adoptPhaseRepairInventoryC(second.copy(phaseRepairInventory = second.phaseRepairInventory.reverse), "source").isSuccess shouldBe false
    }

    "retain the last reserved batch and refuse actual pending document and unknown-closure frontiers" in {
      Given("docs/spec/goal-phase-repair-inventory-continuation.md R2 E1: an active reserved repair and real pure frontier transitions")
      val first = _repair
      def seen(state: O.State, tree: Char): O.State = {
        val ready = take(state.acceptC(O.SemanticCompleted(_key(state), "Actual correction completed")))
        take(ready.acceptC(O.CandidateObserved(state.tipSnapshot.copy(tree = _oid(tree)))))
      }
      val inspected = seen(first, 'e')
      val last = take(inspected.acceptC(O.RepairableChecksObserved(inspected.candidate.get, _plan.phaseChecks,
        "second-failure", _directive)))
      val lastdecision = take(last.acceptC(O.NativeFailure("Inventory requested within the last reserved batch")))
      val inspectedagain = seen(last, 'f')
      val pending = take(inspectedagain.acceptC(O.RepairableChecksObserved(inspectedagain.candidate.get, _plan.phaseChecks,
        "third-failure", _directive)))
      val document = take(first.acceptC(O.DocumentConflictObserved(Vector("Actual document conflict"))))
      val passed = take(inspected.acceptC(O.ChecksObserved(inspected.candidate.get, _plan.phaseChecks, "fresh-phase-pass", true)))
      val accepted = take(passed.acceptC(O.ReviewAdmitted(_key(passed), passed.candidate.get, "fresh-phase-pass",
        "independent-phase-review", O.ReviewOutcome.Accept)))
      val intent = take(accepted.acceptC(O.ClosureIntentSaved(accepted.candidate.get, "unknown-release-intent")))
      val unknown = take(intent.acceptC(O.ClosureUnknown("unknown-release-intent")))
      When("each stopped frontier receives an inventory append")
      Vector(last, lastdecision).foreach { source =>
        val continued = take(source.adoptPhaseRepairInventoryC(_proposed(source), "last-reserved-direction"))
        continued.repairs shouldBe 2
        continued.ordinaryRepairs shouldBe source.ordinaryRepairs
        continued.repairDirective shouldBe source.repairDirective
        continued.pendingRepair shouldBe None
      }
      val results = Vector(pending, document, unknown).map(source =>
        source.adoptPhaseRepairInventoryC(_proposed(source), "independent-direction"))
      Then("none can refund allowance reinterpret an unknown effect or open another repair")
      results.foreach(_.isSuccess shouldBe false)
      lastdecision.repairs shouldBe 2
      pending.pendingRepair should not be empty
      unknown.closureIntent shouldBe Some("unknown-release-intent")
    }
  }

  "E2 compatible old Plan wires" should afterWord(
    "in spec:goal-phase-repair-inventory-continuation, example:E2, rules:R1,R5, phase:P99-INVENTORY-01") {
    "retain bounded native references across exact codec and public Record boundaries" in {
      Given("docs/spec/goal-phase-repair-inventory-continuation.md R4,R5 E2: the all-closed repair facts and syntactically valid immutable native references")
      val decision = take(_repair.acceptC(O.NativeFailure("The assigned repair needs its exact inventory")))
      val proposal = R.Proposal("bounded-inventory", "1", "Correct the actual product check", _proposed(decision))
      val captured = take(R.captureSemanticProposalC(decision, proposal, None))
      val session = "bounded-native-fixture"
      val target = K.Target(K.schema, "default", session, "42", "42", decision.plan, decision.base,
        decision.completed, decision.generation, GoalPhaseRepairProgress.snapshot(decision),
        ContextReference(session + ":ordered-repair:40", "40"), ContextReference("native-semantic-result", "1"),
        None, None, None, None, (decision.plan.phaseOwnedPaths ++ captured.plan.phaseRepairInventory).distinct, captured)
      val pending = target.copy(frontierRevision = "43", questionIdentity = Some(session + ":ordered-human:42"),
        pendingRevision = Some("43"))
      val deferred = pending.copy(frontierRevision = "44", deferredResult = Some(ContextReference("actual-defer-result", "1")),
        deferredChoice = Some("Defer"))
      val directive = ContextReference("exact-inventory-direction", "1")
      val retired = Vector("semanticRequest", "semanticResult", "pendingPrefix", "deferredAnswer")
      When("automatic unanswered and deferred targets cross the actual public Record conversion")
      Vector(target, pending, deferred).foreach { value =>
        K.targetValid(value) shouldBe true
        value.asJson.as[K.Target] shouldBe Right(value)
        take(K.targetC(K.targetRecord(value))) shouldBe value
        take(K.targetRecord(value).toJsonStringC).getBytes(java.nio.charset.StandardCharsets.UTF_8).length should be < (64 * 1024)
        retired.foreach(field => value.asJson.hcursor.downField(field).succeeded shouldBe false)
        val payload = K.Payload(value, Option.when(value.questionIdentity.nonEmpty)(directive), "participant", "Exact independent direction")
        K.codec.decode(K.codec.encode(payload)) shouldBe Right(payload)
        K.codec.decode(K.codec.encode(payload).mapObject(_.add("schemaVersion", "sm-workflow.phase-repair-inventory.v1".asJson))).isLeft shouldBe true
      }
      Then("only reference-bound v2 targets survive and crossed lineage extra fields and explicit null refuse")
      val crossed = Vector(target.copy(schemaVersion = "sm-workflow.phase-repair-inventory.v1"),
        target.copy(requestingWork = ContextReference("foreign-work", "40")), target.copy(sourceRevision = "43", frontierRevision = "43"),
        pending.copy(questionIdentity = Some("foreign-question")), pending.copy(pendingRevision = Some("44"), frontierRevision = "44"),
        deferred.copy(deferredChoice = Some("Cancel")), deferred.copy(frontierRevision = "43"),
        target.copy(deferredResult = Some(ContextReference("unpaired-defer", "1"))))
      crossed.foreach(value => value.asJson.as[K.Target].isLeft shouldBe true)
      (retired :+ "extra").foreach(field => target.asJson.mapObject(_.add(field, Json.obj())).as[K.Target].isLeft shouldBe true)
      target.asJson.mapObject(_.add("pendingRevision", Json.Null)).as[K.Target].isLeft shouldBe true
      val binding = take(K.bindingC("default", pending, "Exact independent direction", "participant", directive))
      K.selectBinding(binding) shouldBe Right(binding)
      K.selectBinding(binding.mapObject(_.add("extra", Json.True))).isLeft shouldBe true
    }

    "default only the absent extension and refuse explicit null malformed and wildcard fields" in {
      Given("docs/spec/goal-phase-repair-inventory-continuation.md R1,R5 E2: the unchanged legacy Plan wire")
      val legacy = _plan.asJson
      When("legacy absent, explicit empty and malformed extension values decode")
      val absent = legacy.as[O.Plan]
      val empty = legacy.mapObject(_.add("phaseRepairInventory", Json.arr())).as[O.Plan]
      val invalid = Vector(Json.Null, Json.fromString("src/test/A.scala"), Json.arr(Json.Null),
        Json.arr(Json.fromString("src/**")), Json.arr(Json.fromString("../other.scala")))
      Then("old saved meaning remains exact and only non-null literal vectors are accepted")
      legacy.hcursor.downField("phaseRepairInventory").succeeded shouldBe false
      absent shouldBe Right(_plan)
      empty shouldBe Right(_plan)
      invalid.foreach(value => legacy.mapObject(_.add("phaseRepairInventory", value)).as[O.Plan].isLeft shouldBe true)
      val extended = _proposed(_repair)
      extended.asJson.as[O.Plan] shouldBe Right(extended)
      val proposal = R.Proposal("literal-inventory", "1", "Correct the actual failed check", extended)
      take(R.fromPublicProposalC(R.publicProposal(Some(proposal)))) shouldBe Some(proposal)
      val extra = proposal.asJson.mapObject(_.add("plan", extended.asJson.mapObject(_.add("unknown", Json.True))))
      R.fromPublicProposalC(Some(GoalPhaseNativeMetadata.record(extra))).isSuccess shouldBe false
    }

    "retain exact historical staging ordinals while allowing only a finite host suffix append" in {
      Given("docs/spec/goal-phase-repair-inventory-continuation.md R1,R5 E2: the complete recorded host candidatePaths vector")
      val recorded = Vector("src/Product.scala", "src/test/ProductSpec.scala")
      val current = recorded :+ "docs/future.md"
      When("replay reconciles valid suffix extensions and changed old ordinals")
      take(GoalPhaseRepairInventoryContinuation.retainedCandidatePathsC(recorded, current)) shouldBe recorded
      Then("removal insertion reordering duplicates wildcard and null all refuse")
      Vector(recorded.reverse, recorded.take(1), "docs/first.md" +: recorded, recorded :+ recorded.head,
        recorded :+ "src/**", null).foreach(paths =>
        GoalPhaseRepairInventoryContinuation.retainedCandidatePathsC(recorded, paths).isSuccess shouldBe false)
    }
  }
}
