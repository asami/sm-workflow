package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value.RepositorySyncStartInput

/**
 * @since   Sep. 28, 2026
 * @version Oct.  8, 2026
 */
final class RepositorySyncMergeAssessmentSpec extends AnyWordSpec with Matchers {
  import RepositorySyncRelationAssessment.*
  import RepositorySyncFastForwardAssessment.State
  import RepositorySyncMergeAssessment.{FrozenState, Receipt as MergeReceipt, PendingState, StructuralEvidence, WhitespaceDiagnostic}

  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"RepositorySync relation fixture failed: $failure")
  }

  private final class Fixture(dirty: Boolean = false, equal: Boolean = false,
    oidWidth: Int = 40) {
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
    val changedPaths = Vector("README.md", "src/main.scala")
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
  }

  "RepositorySync retained pending merge" should {
    "admit the exact two parents without moving HEAD or discarding a checkpoint for either OID width" in {
      for (dirty <- Vector(false, true); width <- Vector(40, 64)) {
        val fixture = new Fixture(dirty = dirty, oidWidth = width)
        val admitted = take(fixture.assess())
        admitted.frozen.state.parentTips shouldBe Vector(fixture.localTip, fixture.fetchedTip)
        admitted.pending.state.localHeadOid shouldBe fixture.localTip
        admitted.pending.state.localTreeOid shouldBe fixture.preservation.cleanState.localTreeOid
        admitted.pending.mergeHeads shouldBe Vector(fixture.fetchedTip)
        admitted.receipt.changedRefs shouldBe Vector.empty
        admitted.pending.state.pendingOperations shouldBe Vector("MERGE")
        admitted.pending.resultTreeOid.length shouldBe width
        admitted.digest should fullyMatch regex "[0-9a-f]{64}"
        take(fixture.assess()) shouldBe admitted
        fixture.snapshotLoads shouldBe 1
        fixture.fetchLoads shouldBe 1
        fixture.loads shouldBe 1
      }
    }

    "refuse equal and ahead routes before loading merge evidence" in {
      val equal = new Fixture(equal = true)
      val local = new Fixture
      local.receiptValue = Some(local.baseline.copy(mergeBases = Vector(local.fetchedTip),
        localAhead = 2L, remoteAhead = 0L))
      local.relation = take(local.assessRelation())
      val remote = new Fixture
      remote.receiptValue = Some(remote.baseline.copy(mergeBases = Vector(remote.localTip),
        localAhead = 0L, remoteAhead = 3L))
      remote.relation = take(remote.assessRelation())
      Vector(equal, local, remote).foreach { fixture =>
        fixture.assess() shouldBe a[Consequence.Failure[?]]
        fixture.frozenLoads shouldBe 0
        fixture.receiptLoads shouldBe 0
      }
    }

    "re-admit upstream identities and full graph facts before any merge evidence load" in {
      val f = new Fixture
      def assess(boundary: RepositorySyncBoundaryAssessment.Assessment = f.boundary,
        scope: RepositorySyncDirtyScopeAssessment.Assessment = f.scope,
        fetch: RepositorySyncFetchAssessment.Assessment = f.fetch,
        preservation: RepositorySyncCheckpointAssessment.Assessment = f.preservation,
        relation: RepositorySyncRelationAssessment.Assessment = f.relation,
        port: RepositorySyncMergeAssessment.Port = f.merges) =
        RepositorySyncMergeAssessment.assessC(boundary, scope, fetch, preservation, relation, port)
      Vector(assess(boundary = f.boundary.copy(digest = "0" * 64)),
        assess(scope = f.scope.copy(digest = "0" * 64)), assess(fetch = f.fetch.copy(digest = "0" * 64)),
        assess(preservation = f.preservation.copy(digest = "0" * 64)),
        assess(relation = f.relation.copy(digest = "0" * 64)),
        assess(relation = f.relation.copy(relation = Relation.RemoteAhead)),
        assess(relation = f.relation.copy(receipt = f.baseline.copy(localAhead = 3L))),
        assess(relation = f.relation.copy(receipt = f.baseline.copy(mergeBases = Vector.empty))),
        assess(boundary = null), assess(scope = null), assess(fetch = null), assess(preservation = null),
        assess(relation = null), assess(relation = f.relation.copy(receipt = null)), assess(port = null))
        .foreach(_ shouldBe a[Consequence.Failure[?]])
      f.frozenLoads shouldBe 0
    }

    "reject frozen identity, preserved tree and clean-gate drift" in {
      stateDrifts.foreach { transform =>
        val f = new Fixture
        f.frozenValue = Some(f.frozen.copy(before = transform(f.before)))
        f.assess() shouldBe a[Consequence.Failure[?]]
        f.receiptLoads shouldBe 0
      }
      val f = new Fixture
      f.frozenValue = Some(f.frozen.copy(before = null))
      f.assess() shouldBe a[Consequence.Failure[?]]
      f.receiptLoads shouldBe 0
    }

    "reject reordered, missing or extra parents, a different base and invalid remote tree facts" in {
      val transforms: Vector[FrozenState => FrozenState] = Vector(
        _.copy(parentTips = Vector.empty), value => value.copy(parentTips = value.parentTips.reverse),
        value => value.copy(parentTips = value.parentTips :+ value.parentTips.head),
        _.copy(parentTips = null), _.copy(mergeBaseOid = "a" * 40),
        _.copy(remoteTreeOid = "bad"), _.copy(remoteTreeOid = "c" * 64),
        _.copy(remoteTreeDigest = "bad"))
      transforms.foreach { transform =>
        val f = new Fixture
        f.frozenValue = Some(transform(f.frozen))
        f.assess() shouldBe a[Consequence.Failure[?]]
        f.receiptLoads shouldBe 0
      }
    }

    "refuse auto-commit, fast-forward, wrong HEAD or MERGE_HEAD, changed refs and incomplete receipts" in {
      val transforms: Vector[MergeReceipt => MergeReceipt] = Vector(
        _.copy(frozenStateDigest = "0" * 64), _.copy(localRef = "refs/heads/release"),
        _.copy(localTipBeforeOid = "0" * 40), _.copy(localTipAfterOid = "0" * 40),
        _.copy(mergeHeads = Vector.empty), _.copy(mergeHeads = Vector("0" * 40)),
        _.copy(mergeHeads = Vector("f" * 40, "a" * 40)), _.copy(mergeHeads = null),
        _.copy(resultTreeOid = "bad"), _.copy(resultTreeOid = "e" * 64),
        _.copy(resultTreeDigest = "bad"), _.copy(changedRefs = Vector("refs/heads/main")),
        _.copy(changedRefs = null), _.copy(noCommit = false), _.copy(noFastForward = false),
        _.copy(completed = false), _.copy(executionDigest = "bad"))
      transforms.foreach { transform =>
        val f = new Fixture
        f.receiptTransform = transform
        f.assess() shouldBe a[Consequence.Failure[?]]
        f.pendingLoads shouldBe 0
      }
    }

    "reject noncanonical or unsafe merge paths before pending state loads" in {
      val invalid = Vector(null, Vector(null), Vector("src/main.scala", "README.md"),
        Vector("README.md", "README.md"), Vector(""), Vector("/outside"), Vector("../outside"),
        Vector("src/../outside"), Vector("./README.md"), Vector("src//main.scala"),
        Vector(".git/config"), Vector("src/.git/config"), Vector(".GIT/config"), Vector("src\\main.scala"),
        Vector("src\u0000main.scala"))
      invalid.foreach { paths =>
        val f = new Fixture
        f.receiptTransform = _.copy(changedPaths = paths)
        f.assess() shouldBe a[Consequence.Failure[?]]
        f.pendingLoads shouldBe 0
      }
    }

    "reject pending mapping, HEAD tree, tracking tip and staged-path drift before structural scans" in {
      stateDrifts.foreach { transform =>
        val f = new Fixture
        f.pendingTransform = value => value.copy(state = transform(value.state))
        f.assess() shouldBe a[Consequence.Failure[?]]
        f.structuralLoads shouldBe 0
      }
      val transforms: Vector[PendingState => PendingState] = Vector(
        _.copy(state = null), _.copy(mergeHeads = Vector.empty), _.copy(mergeHeads = Vector("0" * 40)),
        _.copy(resultTreeOid = "0" * 40), _.copy(resultTreeDigest = "0" * 64),
        _.copy(unmergedPaths = Vector("src/main.scala")), _.copy(unmergedPaths = null),
        value => value.copy(state = value.state.copy(pendingOperations = Vector.empty)),
        value => value.copy(state = value.state.copy(pendingOperations = Vector("MERGE", "REBASE"))),
        value => value.copy(state = value.state.copy(dirtyPaths = value.state.dirtyPaths :+ "unknown.txt")))
      transforms.foreach { transform =>
        val f = new Fixture
        f.pendingTransform = transform
        f.assess() shouldBe a[Consequence.Failure[?]]
        f.structuralLoads shouldBe 0
      }
    }

    "reject structural input mismatch, unresolved conflicts, markers, unsafe material and incomplete scans" in {
      val transforms: Vector[StructuralEvidence => StructuralEvidence] = Vector(
        _.copy(pendingStateDigest = "0" * 64), value => value.copy(parentTips = value.parentTips.reverse),
        _.copy(parentTips = null), _.copy(resultTreeOid = "0" * 40), _.copy(resultTreeDigest = "0" * 64),
        _.copy(unmergedPaths = Vector("src/main.scala")), _.copy(unmergedPaths = null),
        _.copy(conflictMarkerPaths = Vector("src/main.scala")), _.copy(conflictMarkerPaths = null),
        _.copy(unsafeFindings = Vector("private-key")), _.copy(unsafeFindings = null),
        _.copy(scanCompleted = false), _.copy(inspectionDigest = "bad"))
      transforms.foreach { transform =>
        val f = new Fixture
        f.structuralTransform = transform
        f.assess() shouldBe a[Consequence.Failure[?]]
      }
    }

    "retain one-sided whitespace without rewriting it and block only the same path and line on both parents" in {
      val f = new Fixture
      val local = WhitespaceDiagnostic("src/main.scala", 12L, f.localTip)
      val remote = local.copy(parentTipOid = f.fetchedTip)
      f.structuralTransform = _.copy(whitespaceDiagnostics = Vector(local))
      val inherited = take(f.assess())
      inherited.structural.whitespaceDiagnostics shouldBe Vector(local)
      inherited.pending.resultTreeDigest shouldBe "f" * 64
      f.structuralTransform = _.copy(whitespaceDiagnostics = Vector(local, remote.copy(line = 13L)))
      take(f.assess())
      f.structuralTransform = _.copy(whitespaceDiagnostics = Vector(local,
        remote.copy(path = "src/other.scala")))
      take(f.assess())
      f.structuralTransform = _.copy(whitespaceDiagnostics = Vector(local, remote))
      f.assess() shouldBe a[Consequence.Failure[?]]
    }

    "reject malformed, unbound, repeated and noncanonical parent diagnostics" in {
      val f = new Fixture
      val diagnostic = WhitespaceDiagnostic("README.md", 1L, f.localTip)
      Vector(null, Vector(null), Vector(diagnostic.copy(path = "../outside")),
        Vector(diagnostic.copy(line = 0L)), Vector(diagnostic.copy(line = -1L)),
        Vector(diagnostic.copy(parentTipOid = "0" * 40)), Vector(diagnostic, diagnostic),
        Vector(diagnostic.copy(line = 2L), diagnostic)).foreach { diagnostics =>
        f.structuralTransform = _.copy(whitespaceDiagnostics = diagnostics)
        f.assess() shouldBe a[Consequence.Failure[?]]
      }
    }

    "stop on missing and null records at every retained evidence boundary" in {
      Vector("freeze", "receipt", "pending", "structural").foreach { stage =>
        val f = new Fixture
        f.missingStage = stage
        f.assess() shouldBe a[Consequence.Failure[?]]
        f.missingStage = ""
        f.nullStage = stage
        f.assess() shouldBe a[Consequence.Failure[?]]
        f.nullStage = ""
        take(f.assess())
        f.fetchLoads shouldBe 1
      }
    }

    "propagate load failure and replay only the same retained evidence without implicit Git operations" in {
      Vector("freeze", "receipt", "pending", "structural").foreach { stage =>
        val f = new Fixture
        f.failureStage = stage
        f.assess() shouldBe a[Consequence.Failure[?]]
        f.failureStage = ""
        val admitted = take(f.assess())
        take(f.assess()) shouldBe admitted
        f.fetchLoads shouldBe 1
        f.snapshotLoads shouldBe 1
      }
    }

    "bind both parents, pre-state, execution, pending state and structural diagnostics into the digest" in {
      val f = new Fixture
      val initial = take(f.assess())
      f.frozenValue = Some(f.frozen.copy(before = f.before.copy(inspectionDigest = "0" * 64)))
      take(f.assess()).digest should not be initial.digest
      f.frozenValue = Some(f.frozen.copy(remoteTreeDigest = "0" * 64))
      take(f.assess()).digest should not be initial.digest
      f.frozenValue = Some(f.frozen)
      f.receiptTransform = _.copy(executionDigest = "0" * 64)
      take(f.assess()).digest should not be initial.digest
      f.receiptTransform = identity
      f.pendingTransform = value => value.copy(state = value.state.copy(inspectionDigest = "0" * 64))
      take(f.assess()).digest should not be initial.digest
      f.pendingTransform = identity
      f.structuralTransform = _.copy(inspectionDigest = "0" * 64)
      take(f.assess()).digest should not be initial.digest
      f.structuralTransform = _.copy(whitespaceDiagnostics =
        Vector(WhitespaceDiagnostic("README.md", 1L, f.localTip)))
      take(f.assess()).digest should not be initial.digest
      f.structuralTransform = identity
      take(f.assess()) shouldBe initial
    }
  }

  private val stateDrifts: Vector[State => State] = Vector(
    _.copy(evidenceBinding = "0" * 64), _.copy(repositoryIdentity = "other"),
    _.copy(worktreeIdentity = "other"), _.copy(localBranch = "release"),
    _.copy(trackingRemote = "upstream"), _.copy(trackingBranch = "release"),
    _.copy(remoteEndpointIdentity = "other"), _.copy(repositoryPolicyVersion = "policy-v2"),
    _.copy(localHeadOid = "0" * 40), _.copy(localTreeOid = "0" * 40),
    _.copy(localTreeOid = "c" * 64), _.copy(localTreeDigest = "0" * 64),
    _.copy(trackingTipOid = "0" * 40), _.copy(dirtyPaths = Vector("new.txt")),
    _.copy(dirtyPaths = null), _.copy(indexShape = "COMPLETE_FULLY_STAGED"),
    _.copy(detachedHead = true), _.copy(pendingOperations = Vector("REBASE")),
    _.copy(pendingOperations = Vector("CHERRY_PICK")), _.copy(pendingOperations = Vector("REVERT")),
    _.copy(pendingOperations = Vector("UNKNOWN")), _.copy(pendingOperations = null),
    _.copy(inspectionDigest = "bad"))
}
