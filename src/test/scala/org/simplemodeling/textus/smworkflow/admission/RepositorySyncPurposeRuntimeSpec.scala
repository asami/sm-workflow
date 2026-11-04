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
    "retain additional qualified baseline observations and original root expectations across successive overlap repairs" in {
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
      f.progress.asJson.hcursor.downField("projectedWorktree").focus shouldBe None
      f.progress.asJson.as[RepositorySyncPurpose.Progress] shouldBe Right(f.progress)

      Given("that current candidate fails again after changing the first baseline, with one more newly qualified unchanged file")
      val current = next.plan.get.groups.head
      val candidate = f.candidate.copy(tree = "7" * 40, changes = current.content,
        expected = current.content.map(content => RepositorySyncPurposeOwnership.expected(next, content.path).get),
        baselineTree = next.baselineTree)
      val progress = next.copy(candidate = Some(candidate), receipts = f.progress.receipts, selection = f.progress.selection)
      val newbaseline = f.baseline.copy(path = "src/test/scala/SecondBaselineSpec.scala",
        original = Some("second old test"), indexed = Some("second old test"), worktree = Some("second old test"))
      val candidatebaseline = f.baseline.copy(original = Some("corrected extra test"), indexed = Some("corrected extra test"),
        worktree = Some("corrected extra test"))
      val baselines = Vector(candidatebaseline, newbaseline)
      val secondpaths = (paths :+ newbaseline.path).sorted
      val secondentries = secondpaths.map(path => RepositorySyncRepairInventory.Entry(path, "100644", "8" * 40))
      val review = f.review.copy(validatedRevision = "20", indexTree = candidate.tree, changedPaths = current.content.map(_.path).sorted)
      val secondscope = scope.copy(sourceRevision = "20", validatedRevision = "20", candidateTree = candidate.tree,
        paths = secondpaths, additions = secondentries.filterNot(entry => review.changedPaths.contains(entry.path)),
        purpose = Some(RepositorySyncRepairInventory.Purpose(f.snapshot.copy(progress = progress), secondentries, baselines)))
      val secondinput = input.copy(review = review, files = (current.content.map(content =>
        RepositorySyncNativeMergeRepair.File(content.path, content.text.get)) :+
        RepositorySyncNativeMergeRepair.File(newbaseline.path, "second old test")).sortBy(_.path), admittedInventory = Some(secondscope))
      val integrated = f.continuation.groups.map(group => group.copy(content =
        group.content.map(_.copy(text = Some("second correction plus future test")))))
      val continuation = f.continuation.copy(groups = integrated, rationale = "Retain the later intent with the second correction")
      val secondresult = result.copy(replacements = secondinput.files.map(file =>
        if (file.path == f.baselinePath) file.copy(text = "second corrected extra test")
        else if (file.path == newbaseline.path) file.copy(text = "second corrected new test")
        else if (file.path == f.laterPath) file.copy(text = "second correction") else file), purposeContinuation = Some(continuation))
      When("the second repair authenticates root expectations from its saved progress before retaining any replacements")
      val rootexpectations = take(RepositorySyncPurposeOwnership.baselineRootExpectationsC(progress, baselines))
      val second = take(RepositorySyncPurposeRuntime.repairedProgressC(progress, secondinput, secondresult,
        RepositorySyncNativeConflictInput.Policy(secondpaths.toSet)))
      Then("candidate baseline text stays separate while both retained and newly captured root expectations survive")
      RepositorySyncPurposeRuntime.validRepairSnapshot(f.snapshot.copy(progress = progress)) shouldBe true
      RepositorySyncRepairInventory.valid(secondscope, review, Some("test")) shouldBe true
      rootexpectations shouldBe Vector(RepositorySyncPurpose.Content(f.baselinePath, Some("old test")),
        RepositorySyncPurpose.Content(newbaseline.path, Some("second old test")))
      rootexpectations.head.text shouldBe f.baseline.worktree
      (rootexpectations.head.text == candidatebaseline.worktree) shouldBe false
      (rootexpectations.head.text == Some("drifted original root")) shouldBe false
      secondscope.purpose.get.baselines shouldBe baselines
      second.projectedWorktree shouldBe Some(rootexpectations)
      second.request shouldBe f.request
      second.plan.get.groups.tail shouldBe f.progress.plan.get.groups.tail
      second.plan.get.groups.head.content should contain(RepositorySyncPurpose.Content(f.baselinePath, Some("second corrected extra test")))
      second.plan.get.groups.head.content should contain(RepositorySyncPurpose.Content(newbaseline.path, Some("second corrected new test")))
      second.plan.get.groups.head.content should contain(RepositorySyncPurpose.Content(f.laterPath, Some("second correction")))
      RepositorySyncPurposeOwnership.effectiveGroup(second, second.plan.get.groups(1)).content.head.text shouldBe
        Some("second correction plus future test")
      (second.cursor, second.attempt, second.slotCursor, second.baselineTree, second.commits, second.effects, second.hostContinuation) shouldBe
        ((next.cursor, next.attempt + 1, next.slotCursor + 1, Some(candidate.tree), next.commits, next.effects, next.hostContinuation))
      second.asJson.as[RepositorySyncPurpose.Progress] shouldBe Right(second)

      When("the second repair prepares root-after projections for its current effective group")
      val secondgroup = RepositorySyncPurposeOwnership.effectiveGroup(second, second.plan.get.groups.head)
      val secondprojection = take(RepositorySyncPurposeOwnership.worktreeProjectionC(second, secondgroup))
      Then("current-only paths project the current correction while the later owner's supplied integration takes precedence")
      secondprojection shouldBe Vector(f.current.content.head, RepositorySyncPurpose.Content(f.baselinePath, Some("second corrected extra test")),
        RepositorySyncPurpose.Content(f.laterPath, Some("second correction plus future test")),
        RepositorySyncPurpose.Content(newbaseline.path, Some("second corrected new test")))
      RepositorySyncPurposeOwnership.expected(second, f.baselinePath) shouldBe Some(rootexpectations.head)
      RepositorySyncPurposeOwnership.expected(second, "value.txt") shouldBe
        Some(RepositorySyncPurpose.Content("value.txt", f.request.files.find(_.path == "value.txt").get.worktree))

      Given("the prior captured root explicitly records deletion while the qualified candidate baseline still has text")
      val deletedroot = Vector(RepositorySyncPurpose.Content(f.baselinePath, None))
      val deleted = progress.copy(projectedWorktree = Some(deletedroot), candidate = Some(candidate.copy(expected =
        candidate.expected.map(content => if (content.path == f.baselinePath) content.copy(text = None) else content))))
      val deletedinput = secondinput.copy(admittedInventory = Some(secondscope.copy(purpose = secondscope.purpose.map(value =>
        value.copy(snapshot = value.snapshot.copy(progress = deleted))))))
      When("the same qualified repair resolves the existing explicit absence before considering candidate text")
      val deletedexpectations = take(RepositorySyncPurposeOwnership.baselineRootExpectationsC(deleted, baselines))
      val deletednext = take(RepositorySyncPurposeRuntime.repairedProgressC(deleted, deletedinput, secondresult,
        RepositorySyncNativeConflictInput.Policy(secondpaths.toSet)))
      Then("explicit None is retained through both authentication and the next saved projection")
      deletedexpectations.head shouldBe deletedroot.head
      RepositorySyncPurposeOwnership.expected(deletednext, f.baselinePath) shouldBe Some(deletedroot.head)
      deletednext.projectedWorktree shouldBe Some(deletedexpectations)
      deletednext.asJson.as[RepositorySyncPurpose.Progress] shouldBe Right(deletednext)
    }
    "preserve explicit current-only content and clean-addition deletion without resolving unsupplied later ownership" in {
      Given("a qualified clean addition whose newly admitted current content is deletion and an original dirty path")
      val f = RepositorySyncPurposeRepairScopeFixture
      val clean = RepositorySyncPurpose.Content(f.baselinePath, None)
      val group = f.current.copy(content = f.current.content :+ clean)
      val plan = f.progress.plan.get.copy(groups = Vector(group, f.later))
      val progress = f.progress.copy(plan = Some(plan), baselineTree = Some(f.candidate.tree),
        projectedWorktree = Some(Vector(RepositorySyncPurpose.Content(f.baselinePath, Some("old test")),
          f.current.content.head)))
      val later = f.later.copy(content = f.later.content :+ clean.copy(text = Some("later clean-file intent")))
      val owned = progress.copy(plan = Some(plan.copy(groups = Vector(group, later))))
      When("root-after projection examines explicit current deletion and a later owner without supplied resolution")
      val projection = take(RepositorySyncPurposeOwnership.worktreeProjectionC(progress, group))
      val unresolved = take(RepositorySyncPurposeOwnership.worktreeProjectionC(owned, group))
      val uncaptured = take(RepositorySyncPurposeOwnership.worktreeProjectionC(progress.copy(projectedWorktree = None), group))
      Then("deletion stays explicit, original current-only content is selected, and unresolved or uncaptured additions receive none")
      projection shouldBe Vector(f.current.content.head, clean)
      projection.last.text shouldBe None
      unresolved shouldBe f.current.content
      uncaptured shouldBe f.current.content
      RepositorySyncPurposeOwnership.expected(progress, "value.txt") shouldBe Some(f.current.content.head)
      RepositorySyncPurposeOwnership.expected(progress, f.baselinePath) shouldBe
        Some(RepositorySyncPurpose.Content(f.baselinePath, Some("old test")))

      Given("two accepted later owners supply conflicting text for that same qualified clean addition")
      val third = later.copy(identity = "third-purpose", dependencies = Vector(later.identity))
      val conflicting = owned.copy(plan = Some(plan.copy(groups = Vector(group, later, third))), ownership = Some(
        RepositorySyncPurpose.OwnershipContinuation(Vector(
          RepositorySyncPurpose.OwnershipGroup(later.identity, Vector(clean.copy(text = Some("first later resolution")))),
          RepositorySyncPurpose.OwnershipGroup(third.identity, Vector(clean.copy(text = Some("conflicting later resolution"))))),
          "Distinct later intents need actual resolution")))
      When("the existing later-owner consensus guard compares their supplied text")
      val refused = RepositorySyncPurposeOwnership.worktreeProjectionC(conflicting, group)
      Then("the clean-addition rule cannot substitute current text for conflicting later intent")
      refused.isSuccess shouldBe false
      refused.toString should include(f.baselinePath)
      refused.toString should include("Actual semantic/Human resolution")
    }
    "reject malformed or duplicated qualified baselines and invalid retained projections before deriving root expectations" in {
      Given("an ordinary candidate baseline and malformed clean-file or ownership observations")
      val f = RepositorySyncPurposeRepairScopeFixture
      val nonordinary = Vector("binary\u0000text", "\ud800")
      val malformed = Vector(f.baseline.copy(path = "../outside.scala"), f.baseline.copy(indexCode = "M"),
        f.baseline.copy(worktreeCode = "M"), f.baseline.copy(unexamined = Some("unavailable")),
        f.baseline.copy(original = None), f.baseline.copy(indexed = Some("different indexed text")),
        f.baseline.copy(worktree = Some("different worktree text"))) ++ nonordinary.map(text =>
        f.baseline.copy(original = Some(text), indexed = Some(text), worktree = Some(text)))
      val duplicateprojection = f.progress.copy(projectedWorktree = Some(Vector.fill(2)(f.current.content.head)))
      val absentrequest = f.progress.copy(request = f.request.copy(files = f.request.files.map(file =>
        if (file.path == "value.txt") file.copy(worktree = None) else file)))
      When("root expectations are derived only from well-shaped baselines and valid progress")
      val refused = malformed.map(file => RepositorySyncPurposeOwnership.baselineRootExpectationsC(f.progress, Vector(file)))
      val duplicate = RepositorySyncPurposeOwnership.baselineRootExpectationsC(f.progress, Vector.fill(2)(f.baseline))
      val invalidprogress = RepositorySyncPurposeOwnership.baselineRootExpectationsC(duplicateprojection, Vector(f.baseline))
      val absent = take(RepositorySyncPurposeOwnership.baselineRootExpectationsC(absentrequest, Vector(f.baseline.copy(path = "value.txt"))))
      Then("malformed candidate provenance refuses and a captured request deletion also takes precedence over candidate text")
      refused.foreach(_.isSuccess shouldBe false)
      duplicate.isSuccess shouldBe false
      invalidprogress.isSuccess shouldBe false
      absent shouldBe Vector(RepositorySyncPurpose.Content("value.txt", None))
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

  "ID08 explicit accepted later-root projection" should {
    "project each successive still-failing current-file correction while retaining original root expectations and repair provenance" in {
      Given("an originally owned current file whose selected input and captured root both still fail the retained Test receipt")
      val f = RepositorySyncPurposeRepairScopeFixture
      val initial = RepositorySyncPurpose.Content("value.txt", Some("wrong initial"))
      val current = f.current.copy(content = Vector(initial))
      val request = f.request.copy(files = f.request.files.map(file =>
        if (file.path == initial.path) file.copy(worktree = initial.text) else file))
      val original = f.progress.copy(request = request, plan = f.progress.plan.map(plan =>
        plan.copy(groups = plan.groups.updated(0, current))), candidate = Some(f.candidate.copy(
        changes = current.content, expected = current.content)))
      val policy = RepositorySyncNativeConflictInput.Policy(Set(initial.path))
      val firstinput = RepositorySyncNativeMergeRepair.Request(f.review, Vector.empty,
        Vector(RepositorySyncNativeMergeRepair.File(initial.path, initial.text.get)), Some(f.failed.identity))
      val firstcontent = initial.copy(text = Some("wrong first correction"))
      val firstresult = RepositorySyncNativeMergeRepair.Result("repaired", "First still-failing fixture proposal",
        Vector(RepositorySyncNativeMergeRepair.File(firstcontent.path, firstcontent.text.get)))
      When("the first accepted proposal prepares its fresh attempt and explicit current-only root-after selection")
      val first = take(RepositorySyncPurposeRuntime.repairedProgressC(original, firstinput, firstresult, policy))
      val firstgroup = RepositorySyncPurposeOwnership.effectiveGroup(first, first.plan.get.groups.head)
      val firstprojection = take(RepositorySyncPurposeOwnership.worktreeProjectionC(first, firstgroup))
      Then("the selected correction is explicit and the captured root expectation remains the original wrong text")
      firstprojection shouldBe Vector(firstcontent)
      RepositorySyncPurposeOwnership.expected(first, initial.path) shouldBe Some(initial)
      first.baselineTree shouldBe Some(f.candidate.tree)

      Given("that new candidate also has a retained failed Test while the root before-text remains the original captured input")
      val candidate = f.candidate.copy(tree = "7" * 40, changes = firstgroup.content,
        expected = Vector(initial), baselineTree = first.baselineTree, worktreeProjection = Some(firstprojection))
      val failedagain = first.copy(candidate = Some(candidate), receipts = original.receipts, selection = original.selection)
      val secondinput = firstinput.copy(review = f.review.copy(validatedRevision = "20", indexTree = candidate.tree),
        files = Vector(RepositorySyncNativeMergeRepair.File(firstcontent.path, firstcontent.text.get)))
      val secondcontent = initial.copy(text = Some("wrong second correction"))
      val secondresult = firstresult.copy(rationale = "Second still-failing fixture proposal", replacements =
        Vector(RepositorySyncNativeMergeRepair.File(secondcontent.path, secondcontent.text.get)))
      When("the second accepted proposal advances the baseline and reconstructs the typed Git request")
      val second = take(RepositorySyncPurposeRuntime.repairedProgressC(failedagain, secondinput, secondresult, policy))
      val secondgroup = RepositorySyncPurposeOwnership.effectiveGroup(second, second.plan.get.groups.head)
      val secondprojection = take(RepositorySyncPurposeOwnership.worktreeProjectionC(second, secondgroup))
      val secondcandidate = candidate.copy(tree = "8" * 40, changes = secondgroup.content,
        expected = secondgroup.content.map(content => RepositorySyncPurposeOwnership.expected(second, content.path).get),
        baselineTree = second.baselineTree, worktreeProjection = Some(secondprojection))
      val selected = take(RepositorySyncPurpose.producerC(secondcandidate)).request.changes.head
      Then("explicit selected-after content replaces three-way inference without rewriting original expectations receipts commits or counters")
      secondprojection shouldBe Vector(secondcontent)
      selected.content shouldBe secondcontent.text
      selected.expectedWorktree shouldBe initial.text
      selected.worktreeProjection.map(_.content) shouldBe Some(secondcontent.text)
      RepositorySyncPurposeOwnership.expected(second, initial.path) shouldBe Some(initial)
      second.request shouldBe original.request
      second.plan.get.groups.tail shouldBe original.plan.get.groups.tail
      second.baselineTree shouldBe Some(candidate.tree)
      (second.cursor, second.attempt, second.slotCursor, second.commits, second.effects, second.hostContinuation) shouldBe
        ((original.cursor, original.attempt + 2, original.slotCursor + 2, original.commits, original.effects, original.hostContinuation))
      original.receipts shouldBe f.progress.receipts
      failedagain.receipts shouldBe original.receipts
      failedagain.receipts.last shouldBe f.failed
      second.receipts shouldBe Vector.empty
      second.candidate shouldBe None
      RepositorySyncPurposeOwnership.worktreeProjectionC(second.copy(baselineTree = None), secondgroup).toOption shouldBe Some(Vector.empty)
    }
    "reconstruct the complete CNCF request for materialization and commit while legacy candidates retain absence" in {
      Given("an accepted same-hunk correction with different current commit and integrated later-owner text")
      val f = RepositorySyncPurposeRepairScopeFixture
      val progress = take(RepositorySyncPurposeRuntime.repairedProgressC(f.progress, f.ownershipInput, f.ownershipResult,
        RepositorySyncNativeConflictInput.Policy(f.ownershipPaths.toSet)))
      val group = RepositorySyncPurposeOwnership.effectiveGroup(progress, progress.plan.get.groups.head)
      val projection = take(RepositorySyncPurposeOwnership.worktreeProjectionC(progress, group))
      val candidate = f.candidate.copy(changes = group.content,
        expected = group.content.map(content => RepositorySyncPurposeOwnership.expected(progress, content.path).get),
        baselineTree = progress.baselineTree, worktreeBefore = Some("7" * 40), worktreeAfter = Some("8" * 40),
        worktreeProjection = Some(projection))
      When("the retained candidate is reconstructed for either ordinary downstream Git operation")
      val reconstructed = take(RepositorySyncPurpose.producerC(candidate))
      val repeated = take(RepositorySyncPurpose.producerC(candidate))
      val path = reconstructed.request.changes.find(_.path.value == f.laterPath).get
      Then("only the explicit root-after projection differs from the captured expectation and current corrected commit")
      repeated shouldBe reconstructed
      path.content shouldBe Some("corrected baseline")
      path.expectedWorktree shouldBe Some("future test")
      path.worktreeProjection.map(_.content) shouldBe Some(Some("corrected baseline plus future test"))
      reconstructed.request.baselineTree.map(_.value) shouldBe progress.baselineTree
      reconstructed.worktreeBefore.map(_.value) shouldBe candidate.worktreeBefore
      reconstructed.worktreeAfter.map(_.value) shouldBe candidate.worktreeAfter
      reconstructed.projectedIndex.value shouldBe candidate.projectedIndex
      val legacy = candidate.copy(worktreeProjection = None)
      take(RepositorySyncPurpose.producerC(legacy)).request.changes.forall(_.worktreeProjection.isEmpty) shouldBe true
      val oldCandidateJson = legacy.asJson.mapObject(_.remove("worktreeProjection"))
      val decodedLegacy = oldCandidateJson.as[RepositorySyncPurpose.Candidate]
      decodedLegacy shouldBe Right(legacy)
      decodedLegacy.toOption.get.asJson shouldBe oldCandidateJson
      decodedLegacy.toOption.get.asJson.hcursor.downField("worktreeProjection").focus shouldBe None
      candidate.asJson.as[RepositorySyncPurpose.Candidate] shouldBe Right(candidate)
      candidate.asJson.hcursor.downField("worktreeProjection").focus.exists(_.isArray) shouldBe true
      RepositorySyncPurposeOwnership.worktreeProjectionC(progress.copy(baselineTree = None), group).toOption shouldBe Some(Vector.empty)
    }
    "retain explicit deletion and reject duplicated or foreign root-projection paths" in {
      Given("a selected path whose accepted integrated worktree outcome is deletion")
      val f = RepositorySyncPurposeRepairScopeFixture
      val candidate = f.candidate.copy(baselineTree = Some("d" * 40),
        worktreeProjection = Some(Vector(RepositorySyncPurpose.Content("value.txt", None))))
      When("the complete typed request is reconstructed")
      val change = take(RepositorySyncPurpose.producerC(candidate)).request.changes.head
      Then("explicit deletion remains distinguishable from legacy absence and crossed projections refuse")
      change.worktreeProjection.map(_.content) shouldBe Some(None)
      change.content shouldBe Some("expected")
      val restored = candidate.asJson.as[RepositorySyncPurpose.Candidate]
      restored shouldBe Right(candidate)
      restored.toOption.get.worktreeProjection shouldBe Some(Vector(RepositorySyncPurpose.Content("value.txt", None)))
      take(RepositorySyncPurpose.producerC(restored.toOption.get)).request.changes.head.worktreeProjection.map(_.content) shouldBe Some(None)
      RepositorySyncPurpose.producerC(candidate.copy(baselineTree = None)).isSuccess shouldBe false
      RepositorySyncPurpose.producerC(candidate.copy(worktreeProjection = Some(Vector.fill(2)(RepositorySyncPurpose.Content("value.txt", None))))).isSuccess shouldBe false
      RepositorySyncPurpose.producerC(candidate.copy(worktreeProjection = Some(Vector(RepositorySyncPurpose.Content("foreign.txt", None))))).isSuccess shouldBe false
    }
    "require consensus among every accepted later original owner instead of choosing an arbitrary owner" in {
      Given("two original later groups own the same path with explicitly supplied integrated projections")
      val f = RepositorySyncPurposeRepairScopeFixture
      val progress = take(RepositorySyncPurposeRuntime.repairedProgressC(f.progress, f.ownershipInput, f.ownershipResult,
        RepositorySyncNativeConflictInput.Policy(f.ownershipPaths.toSet)))
      val third = f.later.copy(identity = "third-purpose", dependencies = Vector(f.later.identity))
      val expanded = progress.copy(plan = progress.plan.map(plan => plan.copy(groups = plan.groups :+ third)),
        ownership = progress.ownership.map(owner => owner.copy(groups = owner.groups :+
          RepositorySyncPurpose.OwnershipGroup(third.identity, f.continuation.groups.head.content))))
      val group = expanded.plan.get.groups.head
      When("matching and conflicting complete later projections are resolved")
      val same = RepositorySyncPurposeOwnership.worktreeProjectionC(expanded, group)
      val conflicting = expanded.copy(ownership = expanded.ownership.map(owner => owner.copy(groups = owner.groups.updated(1,
        owner.groups(1).copy(content = Vector(RepositorySyncPurpose.Content(f.laterPath, Some("different semantic outcome"))))))))
      Then("agreement is explicit and disagreement stays decision-needed before any Git effect")
      same.toOption shouldBe Some(f.current.content ++ f.continuation.groups.head.content)
      RepositorySyncPurposeOwnership.worktreeProjectionC(conflicting, group).isSuccess shouldBe false
      val deleted = expanded.copy(ownership = expanded.ownership.map(owner => owner.copy(groups = owner.groups.map(owner =>
        owner.copy(content = Vector(RepositorySyncPurpose.Content(f.laterPath, None)))))))
      RepositorySyncPurposeOwnership.worktreeProjectionC(deleted, group).toOption shouldBe
        Some(f.current.content :+ RepositorySyncPurpose.Content(f.laterPath, None))
      progress.plan.get.groups.tail shouldBe Vector(f.later)
      progress.request shouldBe f.request
    }
  }

}
