package org.simplemodeling.textus.smworkflow.admission

import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitPartitionFixture.take

@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class RepositorySyncPurposeRuntimeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "RS-FORMAL-02 purpose attempt provenance" should {
    "retain original checks in history while each corrected attempt uses the exact proposed selection and a fresh host namespace" in {
      Given("an actual selection-correction direction on the original failed Purpose")
      val target = RepositorySyncFormalFailureContinuationFixture.corrected
      val direction = RepositorySyncFormalFailureContinuation.Direction(target,
        RepositorySyncFormalFailureContinuation.selectionChoices.head, "Append registered selected tests", "participant",
        ContextReference("selection-direction", "1"), false, false)
      val original = target.purpose.get
      When("the new attempt is prepared without resetting repair counters")
      val next = original.copy(receipts = target.successfulPrefix, selection = RepositorySyncFormalFailureContinuation.attemptSelection(target),
        assessment = None, review = None, hostContinuation = Some(direction))
      Then("only the precise stored direction admits transformed selection and original prefix receipts")
      RepositorySyncFormalFailureContinuation.validPurposeAttempt(next, direction) shouldBe true
      RepositorySyncPurposeRuntime.attemptPrefix(next) shouldBe "purpose-1-host-12"
      next.selection.get.checks shouldBe target.proposedChecks.get
      next.selection.get.copy(checks = original.selection.get.checks) shouldBe original.selection.get
      (next.cursor, next.attempt, next.slotCursor, next.baselineTree, next.candidate, next.commits, next.effects, next.reusable) shouldBe
        ((original.cursor, original.attempt, original.slotCursor, original.baselineTree, original.candidate, original.commits, original.effects, original.reusable))
      next.asJson.as[RepositorySyncPurpose.Progress] shouldBe Right(next)
      val failedAgain = next.copy(receipts = target.successfulPrefix :+ target.failed)
      RepositorySyncFormalFailureContinuation.validPurposeAttempt(failedAgain, direction) shouldBe true
      RepositorySyncPurposeRuntime.attemptPrefix(failedAgain) shouldBe "purpose-1-host-12"
      original.receipts.last shouldBe target.failed
      original.selection.get.checks shouldBe target.selectedChecks
    }
    "allocate a distinct validation attempt without moving repair cursor slot candidate or commits" in {
      Given("the unchanged failed purpose candidate and a Human-authorized host correction")
      val target = RepositorySyncFormalFailureContinuationFixture.purpose
      val original = target.purpose.get
      val direction = RepositorySyncFormalFailureContinuation.Direction(target,
        RepositorySyncFormalFailureContinuation.choices.head, "Host correction", "participant", ContextReference("direction", "1"), false, false)
      When("new attempt progress retains only the actual successful prefix")
      val next = original.copy(receipts = target.successfulPrefix, hostContinuation = Some(direction))
      Then("its key is independent of actual repair accounting and the historical progress still decodes")
      RepositorySyncPurposeRuntime.attemptPrefix(original) shouldBe "purpose-1"
      RepositorySyncPurposeRuntime.attemptPrefix(next) shouldBe "purpose-1-host-12"
      (next.cursor, next.attempt, next.slotCursor, next.candidate, next.commits) shouldBe
        ((original.cursor, original.attempt, original.slotCursor, original.candidate, original.commits))
      next.asJson.as[RepositorySyncPurpose.Progress] shouldBe Right(next)
      val historical = original.asJson.mapObject(_.remove("hostContinuation"))
      historical.as[RepositorySyncPurpose.Progress] shouldBe Right(original)
      historical.hcursor.downField("hostContinuation").focus shouldBe None
      historical.mapObject(_.add("hostContinuation", io.circe.Json.Null)).as[RepositorySyncPurpose.Progress].isLeft shouldBe true
      original.receipts.last shouldBe target.failed
    }
  }

  "ID06 qualified Purpose repair transition" should {
    "propagate exact additional candidate text and captured baseline while preserving the whole original request and other groups" in {
      Given("the complete failed Purpose and one qualified unchanged baseline outside its current group")
      val f = RepositorySyncPurposeRepairScopeFixture
      val policy = RepositorySyncNativeConflictInput.Policy(f.paths.toSet)
      val result = RepositorySyncNativeMergeRepair.Result("repaired", "Fixture corrects only the stale baseline expectation",
        f.input.files.map(file => if (file.path == f.baselinePath) file.copy(text = "corrected actual test") else file))
      When("the ordinary repair proposal produces the next attempt progress")
      val next = take(RepositorySyncPurposeRuntime.repairedProgressC(f.progress, f.input, result, policy))
      Then("the additional test enters this group and its original baseline enters only the request inventory")
      next.plan.get.groups.head.content shouldBe f.current.content :+ RepositorySyncPurpose.Content(f.baselinePath, Some("corrected actual test"))
      next.request.files shouldBe f.request.files :+ f.baseline
      next.request.copy(files = f.request.files) shouldBe f.request
      next.plan.get.groups.tail shouldBe f.progress.plan.get.groups.tail
      next.plan.get.excludedPaths shouldBe f.progress.plan.get.excludedPaths
      (next.cursor, next.attempt, next.slotCursor, next.baselineTree) shouldBe
        ((f.progress.cursor, f.progress.attempt + 1, f.progress.slotCursor + 1, Some(f.candidate.tree)))
      next.candidate shouldBe None
      next.receipts shouldBe Vector.empty
      next.selection shouldBe None
      next.commits shouldBe f.progress.commits
      next.effects shouldBe f.progress.effects
      f.progress.receipts.last shouldBe f.failed
      next.asJson.as[RepositorySyncPurpose.Progress] shouldBe Right(next)
      RepositorySyncNativeMergeRepair.inputCodec.decode(RepositorySyncNativeMergeRepair.inputCodec.encode(f.input)) shouldBe Right(f.input)
      val stale = f.progress.copy(attempt = f.progress.attempt + 1)
      RepositorySyncPurposeRuntime.repairedProgressC(stale, f.input, result, policy).isSuccess shouldBe false
    }
    "name a newly added path and its later owner before any transition while preserving ordinary shared-path repairs" in {
      Given("a later planned Purpose owns the proposed extra test and an ordinary current path may already be shared")
      val f = RepositorySyncPurposeRepairScopeFixture
      When("scope eligibility examines new and already owned current paths")
      val conflict = RepositorySyncPurposeRuntime.noLaterRepairOverlapC(f.progress, Vector(f.laterPath))
      val shared = f.progress.copy(plan = f.progress.plan.map(plan => plan.copy(groups =
        plan.groups.updated(1, f.later.copy(content = f.later.content :+ f.current.content.head)))))
      Then("only the newly added later-owned path conflicts and the ordinary repair retains its existing semantics")
      conflict.isSuccess shouldBe false
      conflict.toString should include(f.laterPath)
      conflict.toString should include("future-purpose")
      RepositorySyncPurposeRuntime.noLaterRepairOverlapC(shared, Vector("value.txt")).isSuccess shouldBe true
      RepositorySyncPurposeOwnership.assigned(shared, Vector("value.txt")) shouldBe Vector.empty
      val ordinary = f.input.copy(files = Vector(RepositorySyncNativeMergeRepair.File("value.txt", "expected")), admittedInventory = None)
      val replacement = RepositorySyncNativeMergeRepair.Result("repaired", "Correct the existing shared current file",
        Vector(RepositorySyncNativeMergeRepair.File("value.txt", "ordinary repaired input")))
      val next = take(RepositorySyncPurposeRuntime.repairedProgressC(shared, ordinary, replacement,
        RepositorySyncNativeConflictInput.Policy(Set("value.txt"))))
      next.plan.get.groups(1) shouldBe shared.plan.get.groups(1)
      next.request shouldBe shared.request
      next.plan.get.groups.head.content shouldBe Vector(RepositorySyncPurpose.Content("value.txt", Some("ordinary repaired input")))
    }
  }

  "ID07 later-purpose ownership continuation" should {
    "refresh the same retained future owner on a second current repair through the default fresh qualification route" in {
      Given("a first accepted baseline correction whose later-owner projection remains required when its fresh current candidate fails again")
      val f = RepositorySyncPurposeRepairScopeFixture
      val policy = RepositorySyncNativeConflictInput.Policy(f.ownershipPaths.toSet)
      val first = take(RepositorySyncPurposeRuntime.repairedProgressC(f.progress, f.ownershipInput, f.ownershipResult, policy))
      val current = first.plan.get.groups.head
      val candidate = f.candidate.copy(tree = "7" * 40, changes = current.content,
        expected = current.content.map(content => RepositorySyncPurposeOwnership.expected(first, content.path).get),
        baselineTree = first.baselineTree)
      val progress = first.copy(candidate = Some(candidate), receipts = f.progress.receipts, selection = f.progress.selection)
      val review = f.review.copy(validatedRevision = "20", indexTree = candidate.tree, changedPaths = f.ownershipPaths)
      val scope = f.ownershipScope.copy(sourceRevision = "20", validatedRevision = "20", candidateTree = candidate.tree,
        additions = Vector.empty, purpose = f.ownershipScope.purpose.map(value =>
          value.copy(snapshot = f.snapshot.copy(progress = progress))))
      var calls = Vector.empty[(Vector[String], String)]
      When("the directly used default-capture qualifier derives a new inventory before binding the new request")
      val captured = take(RepositorySyncNativeMergeRepairContinuation.freshPurposeInventoryC(progress,
        f.ownershipPaths, None, true, (paths, rationale) => {
          calls = calls :+ (paths -> rationale)
          org.goldenport.Consequence.success(scope)
        }))
      val input = f.ownershipInput.copy(review = review, files = candidate.changes.map(content =>
        RepositorySyncNativeMergeRepair.File(content.path, content.text.get)).sortBy(_.path), admittedInventory = captured)
      val integrated = f.continuation.groups.map(group => group.copy(content =
        group.content.map(_.copy(text = Some("latest baseline plus future test")))))
      val continuation = f.continuation.copy(groups = integrated,
        rationale = "Refresh the same future owner's intent against the latest current correction")
      val result = f.ownershipResult.copy(replacements = input.files.map(file =>
        if (file.path == f.laterPath) file.copy(text = "latest baseline") else file), purposeContinuation = Some(continuation))
      val next = take(RepositorySyncPurposeRuntime.repairedProgressC(progress, input, result, policy))
      Then("the same future owner is explicitly reassigned and both current and later effective text include the second repair")
      calls.size shouldBe 1
      calls.head._1 shouldBe f.ownershipPaths
      calls.head._2 should include("retained later projection")
      captured shouldBe Some(scope)
      RepositorySyncPurposeRuntime.validRepairSnapshot(f.snapshot.copy(progress = progress)) shouldBe true
      RepositorySyncRepairInventory.valid(scope, review, Some("test")) shouldBe true
      RepositorySyncPurposeOwnership.assigned(progress, f.ownershipPaths) shouldBe
        Vector(RepositorySyncPurpose.OwnershipGroup(f.later.identity, Vector(f.later.content.head)))
      RepositorySyncPurposeOwnership.work(input).get.hcursor.downField("requiredResultPairs").focus.get.asArray.get.size shouldBe 1
      next.plan.get.groups.head.content should contain(RepositorySyncPurpose.Content(f.laterPath, Some("latest baseline")))
      RepositorySyncPurposeOwnership.effectiveGroup(next, next.plan.get.groups(1)).content.head.text shouldBe Some("latest baseline plus future test")
      next.plan.get.groups.tail shouldBe f.progress.plan.get.groups.tail
      next.request shouldBe f.request
      (next.attempt, next.slotCursor, next.commits, next.effects) shouldBe
        ((first.attempt + 1, first.slotCursor + 1, f.progress.commits, f.progress.effects))
      next.ownership shouldBe Some(continuation)
      RepositorySyncPurposeRuntime.repairedProgressC(progress, input.copy(admittedInventory = None),
        result.copy(purposeContinuation = None), policy).isSuccess shouldBe false
      RepositorySyncPurposeRuntime.repairedProgressC(progress, f.ownershipInput, f.ownershipResult, policy).isSuccess shouldBe false
      val oldscope = scope.copy(inventoryPolicy = RepositorySyncRepairInventory.purposePolicy)
      RepositorySyncPurposeRuntime.repairedProgressC(progress, input.copy(admittedInventory = Some(oldscope)),
        result.copy(purposeContinuation = None), policy).isSuccess shouldBe false
      RepositorySyncNativeMergeRepair.admitC(input, result.copy(purposeContinuation = None), policy).isSuccess shouldBe false
    }

    "refuse a default fresh ownership capture when actual qualification fails without issuing unqualified work or seeking consent" in {
      Given("an already repaired current group sharing an exact retained future owner/path")
      val f = RepositorySyncPurposeRepairScopeFixture
      val progress = take(RepositorySyncPurposeRuntime.repairedProgressC(f.progress, f.ownershipInput, f.ownershipResult,
        RepositorySyncNativeConflictInput.Policy(f.ownershipPaths.toSet)))
      var calls = 0
      When("the directly used capture guard consumes a real qualifier failure or an unavailable automatic route")
      val failed = RepositorySyncNativeMergeRepairContinuation.freshPurposeInventoryC(progress,
        f.ownershipPaths, None, true, (_, _) => {
          calls += 1
          org.goldenport.Consequence.stateConflict("Actual root content drifted at " + f.laterPath)
        })
      val unavailable = RepositorySyncNativeMergeRepairContinuation.freshPurposeInventoryC(progress,
        f.ownershipPaths, None, false, (_, _) => fail("Unavailable automatic qualification must remain passive"))
      val existing = take(RepositorySyncNativeMergeRepairContinuation.freshPurposeInventoryC(progress,
        f.ownershipPaths, Some(f.ownershipScope), true, (_, _) => fail("An existing scope follows its normal authentication route")))
      val ordinary = take(RepositorySyncNativeMergeRepairContinuation.freshPurposeInventoryC(f.progress,
        Vector("value.txt"), None, true, (_, _) => fail("An unrelated request must not acquire overlap rights")))
      Then("failure remains precise and points to existing prepare while unrelated and supplied scopes retain their existing routes")
      calls shouldBe 1
      failed.isSuccess shouldBe false
      failed.toString should include("prepareRepositorySyncRepairScopeCorrection")
      failed.toString should include("Actual root content drifted at " + f.laterPath)
      unavailable.isSuccess shouldBe false
      unavailable.toString should include("prepareRepositorySyncRepairScopeCorrection")
      existing shouldBe Some(f.ownershipScope)
      ordinary shouldBe None
    }
    "retain additional qualified baseline observations separately from the original request on an overlap repair" in {
      Given("one later-owned path and another qualified unchanged baseline outside the original captured dirty inventory")
      val f = RepositorySyncPurposeRepairScopeFixture
      val paths = (f.ownershipPaths :+ f.baselinePath).sorted
      val entries = paths.map(path => RepositorySyncRepairInventory.Entry(path, "100644", "8" * 40))
      val scope = f.ownershipScope.copy(paths = paths, additions = entries.filterNot(entry => f.review.changedPaths.contains(entry.path)),
        purpose = Some(RepositorySyncRepairInventory.Purpose(f.snapshot, entries, Vector(f.baseline))))
      val input = f.ownershipInput.copy(files = (f.ownershipInput.files :+
        RepositorySyncNativeMergeRepair.File(f.baselinePath, "old test")).sortBy(_.path), admittedInventory = Some(scope))
      val result = f.ownershipResult.copy(replacements = (f.ownershipResult.replacements :+
        RepositorySyncNativeMergeRepair.File(f.baselinePath, "corrected extra test")).sortBy(_.path))
      When("the exact v5 result prepares the current group's fresh attempt")
      val next = take(RepositorySyncPurposeRuntime.repairedProgressC(f.progress, input, result,
        RepositorySyncNativeConflictInput.Policy(paths.toSet)))
      Then("all original request facts remain immutable and only the authenticated expected map holds the extra baseline")
      next.request shouldBe f.request
      next.plan.get.groups.tail shouldBe f.progress.plan.get.groups.tail
      next.projectedWorktree shouldBe Some(Vector(RepositorySyncPurpose.Content(f.baselinePath, Some("old test"))))
      RepositorySyncPurposeOwnership.expected(next, f.baselinePath) shouldBe
        Some(RepositorySyncPurpose.Content(f.baselinePath, Some("old test")))
      next.plan.get.groups.head.content should contain(RepositorySyncPurpose.Content(f.baselinePath, Some("corrected extra test")))
      next.asJson.as[RepositorySyncPurpose.Progress] shouldBe Right(next)
    }
    "carry the current correction and resolved later text through separate projections while retaining original provenance" in {
      Given("a qualified baseline file also owned by a later group, with different original root and candidate text")
      val f = RepositorySyncPurposeRepairScopeFixture
      val policy = RepositorySyncNativeConflictInput.Policy(f.ownershipPaths.toSet)
      When("the exact repair result is retained for a fresh current candidate")
      val next = take(RepositorySyncPurposeRuntime.repairedProgressC(f.progress, f.ownershipInput, f.ownershipResult, policy))
      Then("the current group gets only its repair, the later group keeps its original plan and the later projection includes both meanings")
      next.request shouldBe f.request
      next.plan.get.groups.tail shouldBe f.progress.plan.get.groups.tail
      next.plan.get.groups.head.content shouldBe f.current.content :+ RepositorySyncPurpose.Content(f.laterPath, Some("corrected baseline"))
      next.ownership shouldBe Some(f.continuation)
      RepositorySyncPurposeOwnership.effectiveGroup(next, next.plan.get.groups(1)).content shouldBe
        f.continuation.groups.head.content ++ f.later.content.tail
      (next.cursor, next.attempt, next.slotCursor, next.baselineTree, next.commits, next.effects, next.hostContinuation) shouldBe
        ((f.progress.cursor, f.progress.attempt + 1, f.progress.slotCursor + 1, Some(f.candidate.tree),
          f.progress.commits, f.progress.effects, f.progress.hostContinuation))
      next.receipts shouldBe Vector.empty
      next.candidate shouldBe None
      next.asJson.as[RepositorySyncPurpose.Progress] shouldBe Right(next)
      RepositorySyncNativeMergeRepair.inputCodec.decode(RepositorySyncNativeMergeRepair.inputCodec.encode(f.ownershipInput)) shouldBe Right(f.ownershipInput)
    }

    "use the actual post-install projection for later expected text without changing the original request or desired owner text" in {
      Given("an admitted correction whose native Git worktreeAfter contains the merged current correction and later dirty intent")
      val f = RepositorySyncPurposeRepairScopeFixture
      val next = take(RepositorySyncPurposeRuntime.repairedProgressC(f.progress, f.ownershipInput, f.ownershipResult,
        RepositorySyncNativeConflictInput.Policy(f.ownershipPaths.toSet)))
      val actual = Vector(RepositorySyncPurpose.Content("value.txt", Some("expected")),
        RepositorySyncPurpose.Content(f.laterPath, Some("corrected baseline plus future test")))
      When("the typed authenticated installation projection is retained before preparing the next group")
      val installed = next.copy(cursor = 1, projectedWorktree = Some(RepositorySyncPurposeOwnership.project(next, actual)))
      val later = RepositorySyncPurposeOwnership.effectiveGroup(installed, installed.plan.get.groups(1))
      Then("later prepare uses the new root expectation and integrated desired text while original captured files stay exact")
      RepositorySyncPurposeOwnership.expected(installed, f.laterPath) shouldBe Some(actual.last)
      RepositorySyncPurposeOwnership.expected(installed, "docs/future.md") shouldBe Some(f.later.content.last)
      later.content.head shouldBe actual.last
      installed.request shouldBe f.request
      installed.plan.get.groups(1) shouldBe f.later
      installed.asJson.as[RepositorySyncPurpose.Progress] shouldBe Right(installed)
      val candidate = f.candidate.copy(changes = later.content,
        expected = later.content.map(content => RepositorySyncPurposeOwnership.expected(installed, content.path).get))
      val failed = installed.copy(candidate = Some(candidate), receipts = f.progress.receipts, selection = f.progress.selection)
      RepositorySyncPurposeRuntime.validRepairSnapshot(f.snapshot.copy(progress = failed)) shouldBe true
      val stale = candidate.copy(expected = later.content.map(content =>
        RepositorySyncPurpose.Content(content.path, f.request.files.find(_.path == content.path).get.worktree)))
      RepositorySyncPurposeRuntime.validRepairSnapshot(f.snapshot.copy(progress = failed.copy(candidate = Some(stale)))) shouldBe false
    }

    "retain earlier unrelated owner projections when another exact pair is accepted and repair an already projected current path" in {
      Given("an earlier accepted projection for the later document plus a newly assigned later test")
      val f = RepositorySyncPurposeRepairScopeFixture
      val prior = RepositorySyncPurpose.OwnershipContinuation(Vector(RepositorySyncPurpose.OwnershipGroup(f.later.identity,
        Vector(RepositorySyncPurpose.Content("docs/future.md", Some("earlier integrated document"))))), "Earlier accepted repair")
      val progress = f.progress.copy(ownership = Some(prior))
      When("the new owner/path projection is combined with prior accepted independent pairs")
      val merged = RepositorySyncPurposeOwnership.merge(progress, f.continuation)
      val laterprogress = progress.copy(cursor = 1, ownership = Some(merged))
      val current = RepositorySyncPurposeOwnership.effectiveGroup(laterprogress, f.later)
      val repaired = current.copy(content = current.content.updated(0, current.content.head.copy(text = Some("new current correction with both intents"))))
      val updated = RepositorySyncPurposeOwnership.repaired(laterprogress, repaired, None)
      Then("both owner pairs survive and the current repair supersedes its prior projection instead of being hidden by it")
      merged.groups.head.content shouldBe f.continuation.groups.head.content ++ prior.groups.head.content
      updated.get.groups.head.content shouldBe repaired.content
      RepositorySyncPurposeOwnership.effectiveGroup(laterprogress.copy(ownership = updated), f.later).content shouldBe repaired.content
      progress.plan.get.groups(1) shouldBe f.later
    }

    "keep genuine conflicting meanings decision-needed with the actual rationale and all progress preserved" in {
      Given("the same line cannot preserve the current baseline correction and later semantic intent without actual resolution")
      val f = RepositorySyncPurposeRepairScopeFixture
      val reason = "future-purpose and " + f.laterPath + " assign incompatible meanings to the same assertion; an actual Human must choose the intended behavior"
      val decision = RepositorySyncNativeMergeRepair.Result("decision-needed", reason, Vector.empty)
      val original = RepositorySyncPurpose.repairProgressJson(f.progress)
      When("the genuine semantic decision is admitted and the effects-only repaired transition is requested")
      val admitted = RepositorySyncNativeMergeRepair.admitC(f.ownershipInput, decision,
        RepositorySyncNativeConflictInput.Policy(f.ownershipPaths.toSet))
      val refused = RepositorySyncPurposeRuntime.repairedProgressC(f.progress, f.ownershipInput, decision,
        RepositorySyncNativeConflictInput.Policy(f.ownershipPaths.toSet))
      val message = RepositorySyncPurposeOwnership.decisionMessage(f.ownershipInput, decision.rationale)
      Then("the public Wait explains the exact owner/path and real decision while no candidate, check, commit or consent is invented")
      admitted.isSuccess shouldBe true
      refused.isSuccess shouldBe false
      message should include(reason)
      message should include(f.later.identity + ":" + f.laterPath)
      message should include("Actual semantic/Human resolution")
      RepositorySyncPurpose.repairProgressJson(f.progress) shouldBe original
      decision.purposeContinuation shouldBe None
      decision.replacements shouldBe Vector.empty
    }
  }
}
