package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value.RepositorySyncStartInput

/**
 * @since   Sep. 28, 2026
 * @version Oct.  8, 2026
 */
final class RepositorySyncMergeValidationAssessmentSpec extends AnyWordSpec with Matchers {
  import RepositorySyncMergeValidationAssessment as V
  import RepositorySyncMergeReviewAdmission as R
  import java.time.Instant
  import org.goldenport.cncf.workflow.CandidateAdmissionModel.{AdmissionResult, EvidenceDefect}
  import RepositorySyncRelationAssessment.*
  import RepositorySyncFastForwardAssessment.State
  import RepositorySyncMergeAssessment.{FrozenState, Receipt as MergeReceipt, PendingState, StructuralEvidence, WhitespaceDiagnostic}

  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"RepositorySync relation fixture failed: $failure")
  }

  private final class Fixture(dirty: Boolean = false, equal: Boolean = false,
    oidWidth: Int = 40, mergePaths: Vector[String] = Vector("README.md", "src/main.scala")) {
    val input = RepositorySyncStartInput(
      "repository-1", "main", "origin", "main", "remote-1", "FULL_STATE", "policy-v1")
    val paths = if (dirty) Vector("new.txt", "src/main.scala") else Vector.empty
    val snapshot = RepositorySyncBoundaryAssessment.Snapshot(
      "snapshot-1", "repository-1", "worktree-1", "main", "origin", "main", "remote-1",
      "policy-v1", "a" * oidWidth, "b" * 64, "c" * 64, "d" * 64, paths,
      "EMPTY", detachedHead = false, pendingMerge = false)
    var snapshotLoads = 0
    val boundary = take(RepositorySyncBoundaryAssessment.assessC(input,
      new RepositorySyncBoundaryAssessment.Port {
        def captureC(repositoryIdentity: String) = {
          snapshotLoads += 1
          Consequence.success(Some(snapshot))
        }
      }))
    val scopeEvidence = RepositorySyncDirtyScopeAssessment.Evidence(
      boundary.digest, "policy-v1", Vector.empty, paths, Vector.empty, "e" * 64)
    val scope = take(RepositorySyncDirtyScopeAssessment.assessC(boundary,
      new RepositorySyncDirtyScopeAssessment.Port {
        def loadC(repositoryIdentity: String, snapshotDigest: String) =
          Consequence.success(Some(scopeEvidence))
      }))
    val localTip = if (dirty) "2" * oidWidth else snapshot.headOid
    val fetchedTip = if (equal) localTip else "f" * oidWidth
    val fetchReceipt = RepositorySyncFetchAssessment.Receipt(
      boundary.digest, scope.digest, "repository-1", "worktree-1", "main", "origin", "main",
      "remote-1", fetchedTip, Some("9" * oidWidth), fetchedTip, snapshot.headOid,
      snapshot.headOid, snapshot.treeDigest, snapshot.indexDigest, snapshot.worktreeDigest,
      "refs/remotes/origin/main", Vector("refs/remotes/origin/main"),
      endpointPolicyAccepted = true, fetchCompleted = true, executionDigest = "1" * 64)
    var fetchLoads = 0
    val fetch = take(RepositorySyncFetchAssessment.assessC(boundary, scope,
      new RepositorySyncFetchAssessment.Port {
        def fetchC(repositoryIdentity: String, boundaryDigest: String, dirtyScopeDigest: String) = {
          fetchLoads += 1
          Consequence.success(Some(fetchReceipt))
        }
      }))
    val checkpointFrozen = RepositorySyncCheckpointAssessment.FrozenState(boundary.digest, scope.digest,
      fetch.digest, "repository-1", "worktree-1", "main", "policy-v1", snapshot.headOid,
      snapshot.treeDigest, snapshot.indexDigest, snapshot.worktreeDigest, paths,
      "3" * oidWidth, "4" * 64, "5" * 64)
    val preservation = take(RepositorySyncCheckpointAssessment.assessC(boundary, scope, fetch,
      new RepositorySyncCheckpointAssessment.Port {
        def loadFrozenC(repositoryIdentity: String, boundaryDigest: String,
          dirtyScopeDigest: String, fetchDigest: String) = Consequence.success(Some(checkpointFrozen))
        def loadCheckpointC(repositoryIdentity: String, frozenStateDigest: String) =
          Consequence.success(Some(RepositorySyncCheckpointAssessment.Receipt(
            frozenStateDigest, localTip, Vector(snapshot.headOid), checkpointFrozen.completeTreeOid,
            checkpointFrozen.completeTreeDigest, RepositorySyncCheckpointAssessment.checkpointPurpose,
            completed = true, executionDigest = "6" * 64)))
        def loadCleanStateC(repositoryIdentity: String, preservationDigest: String) =
          Consequence.success(Some(RepositorySyncCheckpointAssessment.CleanState(
            preservationDigest, "repository-1", "worktree-1", "main", "origin", "main",
            "remote-1", "policy-v1", localTip, if (dirty) checkpointFrozen.completeTreeOid else "7" * oidWidth,
            if (dirty) checkpointFrozen.completeTreeDigest else snapshot.treeDigest, fetchedTip,
            Vector.empty, "EMPTY", detachedHead = false, pendingMerge = false,
            inspectionDigest = "8" * 64)))
      }))
    val baseline = Receipt(preservation.digest, "repository-1", "worktree-1", "main", "origin",
      "main", "remote-1", "policy-v1", localTip, fetchedTip,
      Vector(if (equal) localTip else "0" * oidWidth),
      if (equal) 0L else 2L, if (equal) 0L else 3L,
      historyComplete = true, replacementHistory = false, inspectionDigest = "9" * 64)
    var receiptValue: Option[Receipt] = Some(baseline)
    var loadFailure = false
    var loads = 0
    val port = new Port {
      def loadC(repositoryIdentity: String, preservationDigest: String,
        localTipOid: String, fetchedTipOid: String): Consequence[Option[Receipt]] = {
        repositoryIdentity shouldBe input.repositoryIdentity
        preservationDigest shouldBe preservation.digest
        localTipOid shouldBe localTip
        fetchedTipOid shouldBe fetchedTip
        loads += 1
        if (loadFailure) Consequence.stateConflict("host graph receipt load failed")
        else Consequence.success(receiptValue)
      }
    }
    def assessRelation() = RepositorySyncRelationAssessment.assessC(boundary, scope, fetch, preservation, port)
    var relation = take(assessRelation())
    val before = State(relation.digest, "repository-1", "worktree-1", "main", "origin", "main",
      "remote-1", "policy-v1", localTip, preservation.cleanState.localTreeOid,
      preservation.cleanState.localTreeDigest, fetchedTip, Vector.empty, "EMPTY",
      detachedHead = false, pendingOperations = Vector.empty, inspectionDigest = "b" * 64)
    val frozen = FrozenState(before, Vector(localTip, fetchedTip),
      baseline.mergeBases.head, "c" * oidWidth, "d" * 64)
    var frozenValue: Option[FrozenState] = Some(frozen)
    val changedPaths = mergePaths
    var receiptTransform: MergeReceipt => MergeReceipt = identity
    var pendingTransform: PendingState => PendingState = identity
    var structuralTransform: StructuralEvidence => StructuralEvidence = identity
    var missingStage = ""
    var nullStage = ""
    var failureStage = ""
    var frozenLoads = 0
    var receiptLoads = 0
    var pendingLoads = 0
    var structuralLoads = 0
    val merges = new RepositorySyncMergeAssessment.Port {
      private def loaded[A](stage: String, value: => A): Consequence[Option[A]] =
        if (failureStage == stage) Consequence.stateConflict(s"host $stage load failed")
        else Consequence.success(if (missingStage == stage) None
          else if (nullStage == stage) Some(null.asInstanceOf[A]) else Some(value))
      def loadFrozenC(repositoryIdentity: String, relationDigest: String,
        localTipOid: String, fetchedTipOid: String): Consequence[Option[FrozenState]] = {
        repositoryIdentity shouldBe input.repositoryIdentity
        relationDigest shouldBe relation.digest
        localTipOid shouldBe localTip
        fetchedTipOid shouldBe fetchedTip
        frozenLoads += 1
        if (failureStage == "freeze") Consequence.stateConflict("host freeze load failed")
        else Consequence.success(if (missingStage == "freeze") None
          else if (nullStage == "freeze") Some(null) else frozenValue)
      }
      def loadReceiptC(repositoryIdentity: String, frozenStateDigest: String) = {
        repositoryIdentity shouldBe input.repositoryIdentity
        receiptLoads += 1
        loaded("receipt", receiptTransform(MergeReceipt(frozenStateDigest, "refs/heads/main",
          localTip, localTip, Vector(fetchedTip), "e" * oidWidth, "f" * 64, changedPaths,
          Vector.empty, noCommit = true, noFastForward = true, completed = true, executionDigest = "1" * 64)))
      }
      def loadPendingStateC(repositoryIdentity: String, receiptDigest: String) = {
        repositoryIdentity shouldBe input.repositoryIdentity
        pendingLoads += 1
        loaded("pending", pendingTransform(PendingState(before.copy(evidenceBinding = receiptDigest,
          dirtyPaths = changedPaths, indexShape = RepositorySyncMergeAssessment.pendingIndexShape,
          pendingOperations = Vector("MERGE"), inspectionDigest = "2" * 64),
          Vector(fetchedTip), "e" * oidWidth, "f" * 64, Vector.empty)))
      }
      def loadStructuralC(repositoryIdentity: String, pendingStateDigest: String) = {
        repositoryIdentity shouldBe input.repositoryIdentity
        structuralLoads += 1
        loaded("structural", structuralTransform(StructuralEvidence(pendingStateDigest,
          Vector(localTip, fetchedTip), "e" * oidWidth, "f" * 64, Vector.empty, Vector.empty,
          Vector.empty, Vector.empty, scanCompleted = true, inspectionDigest = "3" * 64)))
      }
    }
    def assess() = RepositorySyncMergeAssessment.assessC(boundary, scope, fetch,
      preservation, relation, merges)
    var merge = take(assess())
    var context = V.Context(boundary, scope, fetch, preservation, relation, merge)
    var changes = changedPaths.map(path => V.Change(path,
      if (path == "README.md") V.ChangeKind.Documentation else V.ChangeKind.LocalSource,
      if (path == "README.md") Vector.empty else Vector("module-contract")))
    var deltaTransform: V.Delta => V.Delta = identity
    var policyTransform: V.Policy => V.Policy = identity
    var validationTransform: V.ValidationEvidence => V.ValidationEvidence = identity
    var expectedChecks = Vector("module-check")
    var validationMissingStage = ""
    var validationNullStage = ""
    var validationFailureStage = ""
    var deltaLoads = 0
    var policyLoads = 0
    var validationLoads = 0
    private def validationLoaded[A](stage: String, value: => A): Consequence[Option[A]] =
      if (validationFailureStage == stage) Consequence.stateConflict("host validation load failed")
      else Consequence.success(if (validationMissingStage == stage) None
        else if (validationNullStage == stage) Some(null.asInstanceOf[A]) else Some(value))
    val validations = new V.Port {
      def loadDeltaC(repositoryIdentity: String, mergeDigest: String,
        parentTips: Vector[String], resultTreeOid: String) = {
        repositoryIdentity shouldBe input.repositoryIdentity
        deltaLoads += 1
        validationLoaded("delta", deltaTransform(V.Delta(mergeDigest, parentTips,
          resultTreeOid, merge.pending.resultTreeDigest, changes, "4" * 64)))
      }
      def loadPolicyC(repositoryIdentity: String, repositoryPolicyVersion: String, deltaDigest: String) = {
        repositoryIdentity shouldBe input.repositoryIdentity
        policyLoads += 1
        validationLoaded("policy", policyTransform(V.Policy(deltaDigest, repositoryPolicyVersion,
          "validation-v1", Vector(V.LocalCheck("full-check", changedPaths, 10L),
            V.LocalCheck("module-check", Vector("src/main.scala"), 0L)),
          Vector("full-check", "runtime-check"), "merge-review-v1", "5" * 64)))
      }
      def loadValidationC(repositoryIdentity: String, planDigest: String) = {
        repositoryIdentity shouldBe input.repositoryIdentity
        validationLoads += 1
        validationLoaded("validation", validationTransform(V.ValidationEvidence(planDigest, merge.pending,
          expectedChecks.map(identity => V.CheckReceipt(identity, successful = true,
            completed = true, executionDigest = "6" * 64)), completed = true, inspectionDigest = "7" * 64)))
      }
    }
    def plan() = V.planC(context, validations)
    def validate() = V.assessC(context, validations)
  }
  "RepositorySync merge product validation" should {
    "select the narrowest declared covering check and replay retained validation for both OID widths and checkpoints" in {
      for (dirty <- Vector(false, true); width <- Vector(40, 64)) {
        val f = new Fixture(dirty = dirty, oidWidth = width)
        val plan = take(f.plan())
        plan.mode shouldBe V.SelectionMode.Localized
        plan.selectedChecks shouldBe Vector("module-check")
        plan.affectedContracts shouldBe Vector("module-contract")
        val validated = take(f.validate())
        validated.plan shouldBe plan
        take(f.validate()) shouldBe validated
        take(V.readmitC(validated)) shouldBe validated
        validated.evidence.pending.state.localHeadOid shouldBe f.localTip
        validated.evidence.pending.resultTreeOid.length shouldBe width
        f.snapshotLoads shouldBe 1
        f.fetchLoads shouldBe 1
      }
    }

    "select no product checks for documentation, metadata and empty deltas without skipping structural evidence" in {
      Vector(Vector("README.md", "project.yaml"), Vector.empty[String]).foreach { changed =>
        val f = new Fixture(mergePaths = changed)
        f.changes = changed.map(path => V.Change(path, if (path == "README.md")
          V.ChangeKind.Documentation else V.ChangeKind.Metadata, Vector.empty))
        // An empty delta still has an explicit repository policy and completed retained receipt.
        f.policyTransform = _.copy(localizedChecks = Vector.empty)
        f.expectedChecks = Vector.empty
        val validated = take(f.validate())
        validated.plan.mode shouldBe V.SelectionMode.NoProductTest
        validated.plan.selectedChecks shouldBe empty
        validated.evidence.checks shouldBe empty
        f.validationLoads shouldBe 1
        val invalid = f.context.copy(merge = f.merge.copy(structural = f.merge.structural.copy(scanCompleted = false)))
        V.planC(invalid, f.validations) shouldBe a[Consequence.Failure[?]]
        f.deltaLoads shouldBe 1
      }
    }

    "use the declared covering preference for source, build and spec changes rather than infer tests from extensions" in {
      Vector(V.ChangeKind.LocalSource, V.ChangeKind.LocalBuild, V.ChangeKind.LocalSpec).foreach { kind =>
        val f = new Fixture(mergePaths = Vector("README.md", "build.sbt", "src/main.scala"))
        f.changes = f.changedPaths.map(path => V.Change(path, kind, Vector.empty))
        f.expectedChecks = Vector("full-check")
        val plan = take(f.validate()).plan
        plan.mode shouldBe V.SelectionMode.Localized
        plan.selectedChecks shouldBe Vector("full-check")
        f.policyTransform = _.copy(localizedChecks = Vector(V.LocalCheck("module-check", Vector("src/main.scala"), 0L)))
        f.plan() shouldBe a[Consequence.Failure[?]]
      }
    }

    "require all declared broad checks for runtime or public-contract changes including a documentation-named path" in {
      Vector(V.ChangeKind.Runtime, V.ChangeKind.PublicContract).foreach { kind =>
        val f = new Fixture
        f.changes = f.changes.map(change => if (change.path == "README.md") change.copy(kind = kind) else change)
        f.expectedChecks = Vector("full-check", "runtime-check")
        val validated = take(f.validate())
        validated.plan.mode shouldBe V.SelectionMode.Broad
        validated.plan.selectedChecks shouldBe f.expectedChecks
        f.policyTransform = _.copy(broadChecks = Vector.empty)
        f.plan() shouldBe a[Consequence.Failure[?]]
      }
    }

    "reject incomplete, unbound or unsafe delta classifications before loading policy" in {
      val transforms: Vector[V.Delta => V.Delta] = Vector(
        _.copy(mergeDigest = "0" * 64), value => value.copy(parentTips = value.parentTips.reverse),
        _.copy(parentTips = null), _.copy(resultTreeOid = "0" * 40), _.copy(resultTreeDigest = "0" * 64),
        _.copy(inspectionDigest = "bad"), _.copy(changes = null), _.copy(changes = Vector(null)),
        _.copy(changes = Vector.empty), value => value.copy(changes = value.changes.reverse),
        value => value.copy(changes = value.changes :+ value.changes.head),
        value => value.copy(changes = value.changes.updated(0, value.changes.head.copy(path = "../outside"))),
        value => value.copy(changes = value.changes.updated(0, value.changes.head.copy(kind = null))),
        value => value.copy(changes = value.changes.updated(0, value.changes.head.copy(affectedContracts = null))),
        value => value.copy(changes = value.changes.updated(0, value.changes.head.copy(affectedContracts = Vector("b", "a")))))
      transforms.foreach { transform =>
        val f = new Fixture
        f.deltaTransform = transform
        f.validate() shouldBe a[Consequence.Failure[?]]
        f.policyLoads shouldBe 0
        f.validationLoads shouldBe 0
      }
    }

    "reject missing versions, ambiguous ranks and raw-command or unsafe-path policy declarations before validation loads" in {
      val transforms: Vector[V.Policy => V.Policy] = Vector(
        _.copy(deltaDigest = "0" * 64), _.copy(repositoryPolicyVersion = "other-policy"),
        _.copy(validationPolicyVersion = ""), _.copy(reviewPolicyVersion = null), _.copy(inspectionDigest = "bad"),
        _.copy(localizedChecks = null), _.copy(localizedChecks = Vector(null)), _.copy(broadChecks = null),
        _.copy(broadChecks = Vector("runtime-check", "full-check")), _.copy(broadChecks = Vector("check", "check")),
        _.copy(broadChecks = Vector("sbt test; git push")),
        value => value.copy(localizedChecks = value.localizedChecks.reverse),
        value => value.copy(localizedChecks = value.localizedChecks.map(_.copy(rank = 0L))),
        _.copy(localizedChecks = Vector(V.LocalCheck("module-check", Vector("src/main.scala"), -1L))),
        _.copy(localizedChecks = Vector(V.LocalCheck("module-check", Vector.empty, 0L))),
        _.copy(localizedChecks = Vector(V.LocalCheck("module-check", Vector(".GIT/config"), 0L))),
        _.copy(localizedChecks = Vector(V.LocalCheck("module-check", Vector("src\\main.scala"), 0L))))
      transforms.foreach { transform =>
        val f = new Fixture
        f.policyTransform = transform
        f.validate() shouldBe a[Consequence.Failure[?]]
        f.validationLoads shouldBe 0
      }
    }

    "re-admit the complete upstream merge chain before loading any delta or policy" in {
      val f = new Fixture
      val invalid = Vector(null, f.context.copy(boundary = null), f.context.copy(scope = null),
        f.context.copy(fetch = f.fetch.copy(digest = "0" * 64)),
        f.context.copy(preservation = null), f.context.copy(relation = null), f.context.copy(merge = null),
        f.context.copy(merge = f.merge.copy(digest = "0" * 64)),
        f.context.copy(merge = f.merge.copy(frozen = null)),
        f.context.copy(merge = f.merge.copy(receipt = f.merge.receipt.copy(noCommit = false))),
        f.context.copy(merge = f.merge.copy(pending = f.merge.pending.copy(resultTreeDigest = "0" * 64))))
      invalid.foreach(context => V.planC(context, f.validations) shouldBe a[Consequence.Failure[?]])
      V.planC(f.context, null) shouldBe a[Consequence.Failure[?]]
      f.deltaLoads shouldBe 0
      f.policyLoads shouldBe 0
      f.validationLoads shouldBe 0
    }

    "stop on missing, null and failed evidence at each boundary without implicitly rerunning product checks" in {
      Vector("delta", "policy", "validation").foreach { stage =>
        val f = new Fixture
        f.validationMissingStage = stage
        f.validate() shouldBe a[Consequence.Failure[?]]
        f.validationMissingStage = ""
        f.validationNullStage = stage
        f.validate() shouldBe a[Consequence.Failure[?]]
        f.validationNullStage = ""
        f.validationFailureStage = stage
        f.validate() shouldBe a[Consequence.Failure[?]]
        f.validationFailureStage = ""
        val validated = take(f.validate())
        take(f.validate()) shouldBe validated
        f.fetchLoads shouldBe 1
      }
    }

    "require exact completed successful checks and the unchanged pending merge including HEAD and staged tree" in {
      val transforms: Vector[V.ValidationEvidence => V.ValidationEvidence] = Vector(
        _.copy(planDigest = "0" * 64), _.copy(pending = null), _.copy(checks = null), _.copy(checks = Vector(null)),
        _.copy(checks = Vector.empty), _.copy(completed = false), _.copy(inspectionDigest = "bad"),
        value => value.copy(checks = value.checks :+ value.checks.head),
        value => value.copy(checks = value.checks.map(_.copy(identity = "full-check"))),
        value => value.copy(checks = value.checks.map(_.copy(successful = false))),
        value => value.copy(checks = value.checks.map(_.copy(completed = false))),
        value => value.copy(checks = value.checks.map(_.copy(executionDigest = "bad"))),
        value => value.copy(pending = value.pending.copy(resultTreeOid = "0" * 40)),
        value => value.copy(pending = value.pending.copy(resultTreeDigest = "0" * 64)),
        value => value.copy(pending = value.pending.copy(mergeHeads = Vector.empty)),
        value => value.copy(pending = value.pending.copy(state = value.pending.state.copy(localHeadOid = "0" * 40))),
        value => value.copy(pending = value.pending.copy(state = value.pending.state.copy(dirtyPaths = Vector.empty))),
        value => value.copy(pending = value.pending.copy(state = value.pending.state.copy(pendingOperations = Vector.empty))))
      transforms.foreach { transform =>
        val f = new Fixture
        f.validationTransform = transform
        f.validate() shouldBe a[Consequence.Failure[?]]
      }
    }

    "bind classification, declarations and receipts and reject reconstructed selection or digest weakening" in {
      val f = new Fixture
      val validated = take(f.validate())
      f.deltaTransform = _.copy(inspectionDigest = "0" * 64)
      take(f.validate()).digest should not be validated.digest
      f.deltaTransform = identity
      f.policyTransform = _.copy(validationPolicyVersion = "validation-v2")
      take(f.validate()).digest should not be validated.digest
      f.policyTransform = identity
      f.validationTransform = value => value.copy(checks = value.checks.map(_.copy(executionDigest = "0" * 64)))
      take(f.validate()).digest should not be validated.digest
      f.validationTransform = identity
      val invalid = Vector(null, validated.copy(digest = "0" * 64), validated.copy(plan = null),
        validated.copy(plan = validated.plan.copy(mode = V.SelectionMode.NoProductTest)),
        validated.copy(plan = validated.plan.copy(selectedChecks = Vector.empty)),
        validated.copy(evidence = validated.evidence.copy(checks = Vector.empty)),
        validated.copy(plan = validated.plan.copy(policy = validated.plan.policy.copy(reviewPolicyVersion = "review-v2"))))
      invalid.foreach(value => V.readmitC(value) shouldBe a[Consequence.Failure[?]])
      take(V.readmitC(validated)) shouldBe validated
    }
  }

  private val now = Instant.parse("2026-09-28T00:00:00Z")
  private def review(reference: String, disposition: R.Disposition = R.Disposition.Accept,
    findings: Vector[R.Finding] = Vector.empty) = R.Review(reference, now.minusSeconds(60),
      now.plusSeconds(3600), disposition, findings, "review rationale", "8" * 64)

  "RepositorySync pending merge review CAM" should {
    "reuse a fresh full-merge review supplied initially or later without issuing semantic work" in {
      val f = new Fixture
      val validated = take(f.validate())
      val submission = take(R.submissionC("merge-review-1", validated))
      val evidence = take(R.evidenceC("review-1", submission.candidate.reference,
        review(submission.candidate.reference.revision), R.Scope.FullMerge, "reviewer-1"))
      val pending = take(R.assessC(submission, now))
      pending.accepted shouldBe false
      pending.evaluation.gaps.head.defects should contain (EvidenceDefect.Missing)
      val supplied = take(R.assessC(submission.copy(evidence = Vector(evidence)), now))
      val later = take(R.assessC(submission, now, Vector(evidence)))
      supplied shouldBe later
      supplied.accepted shouldBe true
      supplied.evaluation.result shouldBe AdmissionResult.Admitted(submission.candidate.reference, Vector("review-1"))
      f.validationLoads shouldBe 1
      f.fetchLoads shouldBe 1
    }

    "retain focused and wrongly bound evidence as CAM gaps without accepting the review" in {
      val f = new Fixture
      val submission = take(R.submissionC("merge-review-1", take(f.validate())))
      val evidence = take(R.evidenceC("review-1", submission.candidate.reference,
        review(submission.candidate.reference.revision), R.Scope.FullMerge, "reviewer-1"))
      val focused = evidence.copy(scope = evidence.scope.copy(value = R.Scope.Focused(Vector("src/main.scala"))))
      val wrongTarget = evidence.copy(target = evidence.target.copy(revision = "0" * 64))
      val wrongType = evidence.copy(payload = evidence.payload.copy(typeIdentity = "other-review"))
      val wrongScope = evidence.copy(scope = evidence.scope.copy(typeIdentity = "other-scope"))
      Vector(focused -> EvidenceDefect.InsufficientCoverage, wrongTarget -> EvidenceDefect.WrongTarget,
        wrongType -> EvidenceDefect.WrongType, wrongScope -> EvidenceDefect.WrongScopeType).foreach { (value, defect) =>
        val assessed = take(R.assessC(submission, now, Vector(value)))
        assessed.accepted shouldBe false
        assessed.evaluation.gaps.head.defects should contain (defect)
      }
    }

    "enforce caller-supplied time and full review even when no product test is selected" in {
      val f = new Fixture(mergePaths = Vector("README.md"))
      f.changes = Vector(V.Change("README.md", V.ChangeKind.Documentation, Vector.empty))
      f.expectedChecks = Vector.empty
      val submission = take(R.submissionC("merge-review-1", take(f.validate())))
      val evidence = take(R.evidenceC("review-1", submission.candidate.reference,
        review(submission.candidate.reference.revision), R.Scope.FullMerge, "reviewer-1"))
      take(R.assessC(submission, now)).accepted shouldBe false
      take(R.assessC(submission, evidence.payload.value.reviewedAt, Vector(evidence))).accepted shouldBe true
      take(R.assessC(submission, evidence.payload.value.validUntil, Vector(evidence))).accepted shouldBe true
      Vector(now.minusSeconds(120), now.plusSeconds(7200)).foreach { instant =>
        val assessed = take(R.assessC(submission, instant, Vector(evidence)))
        assessed.evaluation.gaps.head.defects should contain (EvidenceDefect.Stale)
      }
      R.assessC(submission, null, Vector(evidence)) shouldBe a[Consequence.Failure[?]]
      val malformed = evidence.copy(payload = evidence.payload.copy(value = evidence.payload.value.copy(rationale = "")))
      take(R.assessC(submission, now, Vector(malformed))).accepted shouldBe false
    }

    "re-admit validation and candidate identities before consuming review evidence" in {
      val f = new Fixture
      val validated = take(f.validate())
      val submission = take(R.submissionC("merge-review-1", validated))
      R.submissionC("merge-review-1", validated.copy(digest = "0" * 64)) shouldBe a[Consequence.Failure[?]]
      val weak = submission.copy(candidate = submission.candidate.copy(payload = submission.candidate.payload.copy(
        value = validated.copy(plan = validated.plan.copy(selectedChecks = Vector.empty)))))
      R.assessC(weak, now) shouldBe a[Consequence.Failure[?]]
      R.assessC(submission.copy(candidate = submission.candidate.copy(reference =
        submission.candidate.reference.copy(revision = "0" * 64))), now) shouldBe a[Consequence.Failure[?]]
      R.assessC(submission.copy(candidate = submission.candidate.copy(payload =
        submission.candidate.payload.copy(typeIdentity = "other-candidate"))), now) shouldBe a[Consequence.Failure[?]]
      R.assessC(null, now) shouldBe a[Consequence.Failure[?]]
      R.submissionC("merge-review-1", null) shouldBe a[Consequence.Failure[?]]
    }

    "retain repair, decision and rejection dispositions without claiming acceptance or applying changes" in {
      val f = new Fixture
      val submission = take(R.submissionC("merge-review-1", take(f.validate())))
      val finding = R.Finding("finding-1", Vector("src/main.scala"), "bounded finding")
      Vector(R.Disposition.Repair, R.Disposition.DecisionNeeded, R.Disposition.Reject).foreach { disposition =>
        val evidence = take(R.evidenceC("review-1", submission.candidate.reference,
          review(submission.candidate.reference.revision, disposition,
            if (disposition == R.Disposition.Repair) Vector(finding) else Vector.empty), R.Scope.FullMerge, "reviewer-1"))
        val assessed = take(R.assessC(submission, now, Vector(evidence)))
        assessed.evaluation.gaps shouldBe empty
        assessed.accepted shouldBe false
        assessed.review.get.disposition shouldBe disposition
      }
      R.evidenceC("review-1", submission.candidate.reference,
        review(submission.candidate.reference.revision, R.Disposition.Accept, Vector(finding)),
        R.Scope.FullMerge, "reviewer-1") shouldBe a[Consequence.Failure[?]]
      R.evidenceC("review-1", submission.candidate.reference,
        review(submission.candidate.reference.revision, R.Disposition.Repair),
        R.Scope.FullMerge, "reviewer-1") shouldBe a[Consequence.Failure[?]]
      val outside = take(R.evidenceC("review-1", submission.candidate.reference,
        review(submission.candidate.reference.revision, R.Disposition.Repair,
          Vector(finding.copy(affectedPaths = Vector("unrelated.scala")))), R.Scope.FullMerge, "reviewer-1"))
      take(R.assessC(submission, now, Vector(outside))).evaluation.gaps.head.defects should contain (EvidenceDefect.Stale)
    }

    "reject ambiguous reviews and malformed scopes, findings or provenance" in {
      val f = new Fixture
      val submission = take(R.submissionC("merge-review-1", take(f.validate())))
      val validReview = review(submission.candidate.reference.revision)
      val evidence = take(R.evidenceC("review-1", submission.candidate.reference, validReview, R.Scope.FullMerge, "reviewer-1"))
      R.assessC(submission.copy(evidence = Vector(evidence)), now,
        Vector(evidence.copy(identity = "review-2"))) shouldBe a[Consequence.Failure[?]]
      val invalid = Vector(null, validReview.copy(reviewedRevision = "bad"), validReview.copy(reviewedAt = null),
        validReview.copy(validUntil = now.minusSeconds(120)), validReview.copy(disposition = null),
        validReview.copy(findings = null), validReview.copy(evidenceDigest = "bad"),
        validReview.copy(findings = Vector(null)), validReview.copy(rationale = null))
      invalid.foreach(value => R.evidenceC("review-1", submission.candidate.reference, value,
        R.Scope.FullMerge, "reviewer-1") shouldBe a[Consequence.Failure[?]])
      Vector(null, R.Scope.Focused(Vector.empty), R.Scope.Focused(Vector("../outside"))).foreach { scope =>
        R.evidenceC("review-1", submission.candidate.reference, validReview,
          scope, "reviewer-1") shouldBe a[Consequence.Failure[?]]
      }
      R.evidenceC("review-1", submission.candidate.reference, validReview,
        R.Scope.FullMerge, "") shouldBe a[Consequence.Failure[?]]
    }

    "invalidate the old review when validation receipts or declared review policy change" in {
      val f = new Fixture
      val submission = take(R.submissionC("merge-review-1", take(f.validate())))
      val evidence = take(R.evidenceC("review-1", submission.candidate.reference,
        review(submission.candidate.reference.revision), R.Scope.FullMerge, "reviewer-1"))
      f.validationTransform = _.copy(inspectionDigest = "0" * 64)
      val revalidated = take(R.submissionC("merge-review-2", take(f.validate())))
      take(R.assessC(revalidated, now, Vector(evidence))).evaluation.gaps.head.defects should contain (EvidenceDefect.WrongTarget)
      f.validationTransform = identity
      f.policyTransform = _.copy(reviewPolicyVersion = "merge-review-v2")
      val replanned = take(R.submissionC("merge-review-3", take(f.validate())))
      take(R.assessC(replanned, now, Vector(evidence))).evaluation.gaps.head.defects should contain (EvidenceDefect.WrongTarget)
      f.policyTransform = identity
      take(R.assessC(submission, now, Vector(evidence))).accepted shouldBe true
    }
  }
}
