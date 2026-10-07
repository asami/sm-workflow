package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value.RepositorySyncStartInput

/**
 * @since   Sep. 28, 2026
 * @version Oct.  8, 2026
 */
final class RepositorySyncFastForwardAssessmentSpec extends AnyWordSpec with Matchers {
  import RepositorySyncRelationAssessment.*
  import RepositorySyncFastForwardAssessment.{FrozenState, State, Receipt as FastForwardReceipt, Port as FastForwardPort}

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
      Vector(if (equal) localTip else snapshot.headOid),
      if (equal) 0L else if (dirty) 1L else 0L, if (equal) 0L else 3L,
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
    val frozen = FrozenState(before, fetchedTip, "c" * oidWidth, "d" * 64)
    var frozenValue: Option[FrozenState] = Some(frozen)
    var receiptTransform: FastForwardReceipt => FastForwardReceipt = identity
    var postTransform: State => State = identity
    var receiptMissing = false
    var postMissing = false
    var failureStage = ""
    var frozenLoads = 0
    var receiptLoads = 0
    var postLoads = 0
    val fastForwards = new FastForwardPort {
      def loadFrozenC(repositoryIdentity: String, relationDigest: String,
        sourceTipOid: String, targetTipOid: String): Consequence[Option[FrozenState]] = {
        repositoryIdentity shouldBe input.repositoryIdentity
        relationDigest shouldBe relation.digest
        sourceTipOid shouldBe localTip
        targetTipOid shouldBe fetchedTip
        frozenLoads += 1
        if (failureStage == "freeze") Consequence.stateConflict("host pre-state load failed")
        else Consequence.success(frozenValue)
      }
      def loadReceiptC(repositoryIdentity: String,
        frozenStateDigest: String): Consequence[Option[FastForwardReceipt]] = {
        repositoryIdentity shouldBe input.repositoryIdentity
        receiptLoads += 1
        if (failureStage == "receipt") Consequence.stateConflict("host receipt load failed")
        else Consequence.success(if (receiptMissing) None else Some(receiptTransform(
          FastForwardReceipt(frozenStateDigest, localTip, fetchedTip, fetchedTip,
            frozen.targetTreeOid, frozen.targetTreeDigest, "refs/heads/main", Vector("refs/heads/main"),
            fastForwardOnly = true, completed = true, executionDigest = "e" * 64))))
      }
      def loadPostStateC(repositoryIdentity: String, receiptDigest: String): Consequence[Option[State]] = {
        repositoryIdentity shouldBe input.repositoryIdentity
        postLoads += 1
        if (failureStage == "post") Consequence.stateConflict("host post-state load failed")
        else Consequence.success(if (postMissing) None else Some(postTransform(before.copy(
          evidenceBinding = receiptDigest, localHeadOid = fetchedTip,
          localTreeOid = frozen.targetTreeOid, localTreeDigest = frozen.targetTreeDigest,
          inspectionDigest = "f" * 64))))
      }
    }
    def assess() = RepositorySyncFastForwardAssessment.assessC(boundary, scope, fetch,
      preservation, relation, fastForwards)
  }

  "RepositorySync fast-forward admission" should {
    "admit only the exact fetched target and its frozen tree without another fetch" in {
      val fixture = new Fixture
      val admitted = take(fixture.assess())
      admitted.frozen.state shouldBe fixture.frozen
      admitted.receipt.resultTipOid shouldBe fixture.fetchedTip
      admitted.after.localHeadOid shouldBe fixture.fetchedTip
      admitted.after.localTreeOid shouldBe fixture.frozen.targetTreeOid
      admitted.after.trackingTipOid shouldBe fixture.fetchedTip
      admitted.digest should fullyMatch regex "[0-9a-f]{64}"
      take(fixture.assess()) shouldBe admitted
      fixture.snapshotLoads shouldBe 1
      fixture.fetchLoads shouldBe 1
      fixture.loads shouldBe 1
    }

    "reject equal, local-ahead and diverged routes before loading fast-forward evidence" in {
      val equal = new Fixture(equal = true)
      val localAhead = new Fixture
      localAhead.receiptValue = Some(localAhead.baseline.copy(mergeBases = Vector(localAhead.fetchedTip),
        localAhead = 1L, remoteAhead = 0L))
      localAhead.relation = take(localAhead.assessRelation())
      val diverged = new Fixture(dirty = true)
      Vector(equal, localAhead, diverged).foreach { fixture =>
        fixture.assess() shouldBe a[Consequence.Failure[?]]
        fixture.frozenLoads shouldBe 0
        fixture.receiptLoads shouldBe 0
        fixture.postLoads shouldBe 0
      }
    }

    "re-admit all upstream digests and typed relation facts before any evidence loads" in {
      val fixture = new Fixture
      val relation = fixture.relation
      val outcomes = Vector(
        RepositorySyncFastForwardAssessment.assessC(fixture.boundary.copy(digest = "0" * 64),
          fixture.scope, fixture.fetch, fixture.preservation, relation, fixture.fastForwards),
        RepositorySyncFastForwardAssessment.assessC(fixture.boundary,
          fixture.scope.copy(digest = "0" * 64), fixture.fetch, fixture.preservation, relation, fixture.fastForwards),
        RepositorySyncFastForwardAssessment.assessC(fixture.boundary, fixture.scope,
          fixture.fetch.copy(digest = "0" * 64), fixture.preservation, relation, fixture.fastForwards),
        RepositorySyncFastForwardAssessment.assessC(fixture.boundary, fixture.scope, fixture.fetch,
          fixture.preservation.copy(digest = "0" * 64), relation, fixture.fastForwards),
        RepositorySyncFastForwardAssessment.assessC(fixture.boundary, fixture.scope, fixture.fetch,
          fixture.preservation.copy(cleanState = fixture.preservation.cleanState.copy(dirtyPaths = Vector("new"))),
          relation, fixture.fastForwards),
        RepositorySyncFastForwardAssessment.assessC(fixture.boundary, fixture.scope, fixture.fetch,
          fixture.preservation, relation.copy(digest = "0" * 64), fixture.fastForwards),
        RepositorySyncFastForwardAssessment.assessC(fixture.boundary, fixture.scope, fixture.fetch,
          fixture.preservation, relation.copy(relation = Relation.LocalAhead), fixture.fastForwards),
        RepositorySyncFastForwardAssessment.assessC(fixture.boundary, fixture.scope, fixture.fetch,
          fixture.preservation, relation.copy(receipt = relation.receipt.copy(remoteAhead = 4L)), fixture.fastForwards),
        RepositorySyncFastForwardAssessment.assessC(null, fixture.scope, fixture.fetch,
          fixture.preservation, relation, fixture.fastForwards),
        RepositorySyncFastForwardAssessment.assessC(fixture.boundary, fixture.scope, fixture.fetch,
          null, relation, fixture.fastForwards),
        RepositorySyncFastForwardAssessment.assessC(fixture.boundary, fixture.scope, fixture.fetch,
          fixture.preservation, relation.copy(receipt = null), fixture.fastForwards),
        RepositorySyncFastForwardAssessment.assessC(fixture.boundary, fixture.scope, fixture.fetch,
          fixture.preservation, null, fixture.fastForwards),
        RepositorySyncFastForwardAssessment.assessC(fixture.boundary, fixture.scope, fixture.fetch,
          fixture.preservation, relation, null))
      outcomes.foreach(_ shouldBe a[Consequence.Failure[?]])
      fixture.frozenLoads shouldBe 0
      fixture.receiptLoads shouldBe 0
      fixture.postLoads shouldBe 0
    }

    "reject pre-state identity, clean-gate and target-tree drift before reading a receipt" in {
      val transforms: Vector[FrozenState => FrozenState] = Vector(
        _.copy(before = null), _.copy(targetTipOid = "0" * 40),
        _.copy(targetTreeOid = "bad"), _.copy(targetTreeOid = "c" * 64),
        _.copy(targetTreeDigest = "bad"))
      transforms.foreach { transform =>
        val fixture = new Fixture
        fixture.frozenValue = Some(transform(fixture.frozen))
        fixture.assess() shouldBe a[Consequence.Failure[?]]
        fixture.receiptLoads shouldBe 0
        fixture.postLoads shouldBe 0
      }
      stateDrifts.foreach { transform =>
        val fixture = new Fixture
        fixture.frozenValue = Some(fixture.frozen.copy(before = transform(fixture.before)))
        fixture.assess() shouldBe a[Consequence.Failure[?]]
        fixture.receiptLoads shouldBe 0
        fixture.postLoads shouldBe 0
      }
    }

    "reject rewritten, wrong-target, extra-ref and incomplete execution receipts" in {
      val transforms: Vector[FastForwardReceipt => FastForwardReceipt] = Vector(
        _.copy(frozenStateDigest = "0" * 64), _.copy(sourceTipOid = "0" * 40),
        _.copy(targetTipOid = "0" * 40), _.copy(resultTipOid = "0" * 40),
        _.copy(resultTreeOid = "0" * 40), _.copy(resultTreeDigest = "0" * 64),
        _.copy(updatedLocalRef = "refs/heads/release"), _.copy(changedRefs = Vector.empty),
        _.copy(changedRefs = Vector("refs/heads/main", "refs/remotes/origin/main")),
        _.copy(changedRefs = Vector("refs/heads/main", "refs/heads/main")),
        _.copy(changedRefs = null), _.copy(fastForwardOnly = false),
        _.copy(completed = false), _.copy(executionDigest = "bad"))
      transforms.foreach { transform =>
        val fixture = new Fixture
        fixture.receiptTransform = transform
        fixture.assess() shouldBe a[Consequence.Failure[?]]
        fixture.postLoads shouldBe 0
      }
    }

    "reject post-state mapping, target, tracking and clean-gate drift" in {
      stateDrifts.foreach { transform =>
        val fixture = new Fixture
        fixture.postTransform = transform
        fixture.assess() shouldBe a[Consequence.Failure[?]]
        fixture.postLoads shouldBe 1
      }
    }

    "stop at missing or null evidence without inventing a successful result" in {
      val frozen = new Fixture
      Vector(None, Some(null)).foreach { value =>
        frozen.frozenValue = value
        frozen.assess() shouldBe a[Consequence.Failure[?]]
      }
      frozen.receiptLoads shouldBe 0
      val receipt = new Fixture
      receipt.receiptMissing = true
      receipt.assess() shouldBe a[Consequence.Failure[?]]
      receipt.receiptMissing = false
      receipt.receiptTransform = _ => null
      receipt.assess() shouldBe a[Consequence.Failure[?]]
      receipt.postLoads shouldBe 0
      val post = new Fixture
      post.postMissing = true
      post.assess() shouldBe a[Consequence.Failure[?]]
      post.postMissing = false
      post.postTransform = _ => null
      post.assess() shouldBe a[Consequence.Failure[?]]
    }

    "propagate failures at each host evidence boundary and allow retained-evidence retry" in {
      Vector("freeze", "receipt", "post").foreach { stage =>
        val fixture = new Fixture
        fixture.failureStage = stage
        fixture.assess() shouldBe a[Consequence.Failure[?]]
        if (stage == "freeze") fixture.receiptLoads shouldBe 0
        if (stage != "post") fixture.postLoads shouldBe 0
        fixture.failureStage = ""
        val accepted = take(fixture.assess())
        take(fixture.assess()) shouldBe accepted
        fixture.fetchLoads shouldBe 1
      }
    }

    "bind pre-state, target-tree, execution and post-state evidence in the result digest" in {
      val fixture = new Fixture
      val initial = take(fixture.assess())
      fixture.frozenValue = Some(fixture.frozen.copy(before = fixture.before.copy(inspectionDigest = "0" * 64)))
      val reinspected = take(fixture.assess())
      reinspected.frozen.digest should not be initial.frozen.digest
      reinspected.digest should not be initial.digest
      fixture.frozenValue = Some(fixture.frozen)
      fixture.receiptTransform = _.copy(executionDigest = "0" * 64)
      take(fixture.assess()).digest should not be initial.digest
      fixture.receiptTransform = identity
      fixture.postTransform = _.copy(inspectionDigest = "0" * 64)
      take(fixture.assess()).digest should not be initial.digest
      fixture.postTransform = identity
      val targetTree = "0" * 40
      val targetDigest = "0" * 64
      fixture.frozenValue = Some(fixture.frozen.copy(targetTreeOid = targetTree, targetTreeDigest = targetDigest))
      fixture.receiptTransform = _.copy(resultTreeOid = targetTree, resultTreeDigest = targetDigest)
      fixture.postTransform = _.copy(localTreeOid = targetTree, localTreeDigest = targetDigest)
      take(fixture.assess()).digest should not be initial.digest
    }

    "accept consistent SHA-256 commit and tree identities" in {
      val fixture = new Fixture(oidWidth = 64)
      val accepted = take(fixture.assess())
      accepted.receipt.resultTipOid shouldBe fixture.fetchedTip
      accepted.after.localTreeOid.length shouldBe 64
      take(fixture.assess()) shouldBe accepted
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
    _.copy(detachedHead = true), _.copy(pendingOperations = Vector("MERGE")),
    _.copy(pendingOperations = Vector("REBASE")), _.copy(pendingOperations = Vector("CHERRY_PICK")),
    _.copy(pendingOperations = Vector("REVERT")), _.copy(pendingOperations = Vector("UNKNOWN")),
    _.copy(pendingOperations = null), _.copy(inspectionDigest = "bad"))
}
