package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value.RepositorySyncStartInput

/**
 * @since   Sep. 28, 2026
 * @version Oct.  8, 2026
 */
final class RepositorySyncPushAssessmentSpec extends AnyWordSpec with Matchers {
  import RepositorySyncRelationAssessment.*
  import RepositorySyncFastForwardAssessment.State
  import RepositorySyncPushAssessment.{FrozenState, Authority, Receipt as PushReceipt, Port as PushPort}

  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"RepositorySync relation fixture failed: $failure")
  }

  private final class Fixture(dirty: Boolean = false, route: Relation = Relation.LocalAhead,
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
    val fetchedTip = if (route == Relation.Equal) localTip else "f" * oidWidth
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
      Vector(route match {
        case Relation.Equal => localTip
        case Relation.LocalAhead => fetchedTip
        case Relation.RemoteAhead => localTip
        case Relation.Diverged => "0" * oidWidth
      }),
      if (route == Relation.Equal || route == Relation.RemoteAhead) 0L else 3L,
      if (route == Relation.Equal || route == Relation.LocalAhead) 0L else 2L,
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
    val authority = Authority(relation.digest, "repository-1", "worktree-1", "main", "origin", "main",
      "remote-1", "policy-v1", localTip, fetchedTip, nonForceOnlyAccepted = true,
      remoteWriteAuthorityAccepted = true, evidenceDigest = "c" * 64)
    val frozen = FrozenState(before, fetchedTip, remoteAncestorOfLocal = true,
      historyComplete = true, replacementHistory = false, graphInspectionDigest = "d" * 64, authority = authority)
    var frozenValue: Option[FrozenState] = Some(frozen)
    var receiptTransform: PushReceipt => PushReceipt = identity
    var postTransform: State => State = identity
    var receiptMissing = false
    var postMissing = false
    var failureStage = ""
    var frozenLoads = 0
    var receiptLoads = 0
    var postLoads = 0
    val pushes = new PushPort {
      def loadFrozenC(repositoryIdentity: String, relationDigest: String,
        sourceTipOid: String, destinationTipBeforeOid: String): Consequence[Option[FrozenState]] = {
        repositoryIdentity shouldBe input.repositoryIdentity
        relationDigest shouldBe relation.digest
        sourceTipOid shouldBe localTip
        destinationTipBeforeOid shouldBe fetchedTip
        frozenLoads += 1
        if (failureStage == "freeze") Consequence.stateConflict("host push pre-state load failed")
        else Consequence.success(frozenValue)
      }
      def loadReceiptC(repositoryIdentity: String, frozenStateDigest: String): Consequence[Option[PushReceipt]] = {
        repositoryIdentity shouldBe input.repositoryIdentity
        receiptLoads += 1
        if (failureStage == "receipt") Consequence.stateConflict("host push receipt load failed")
        else Consequence.success(if (receiptMissing) None else Some(receiptTransform(PushReceipt(
          frozenStateDigest, "refs/heads/main", "refs/heads/main", localTip, fetchedTip, localTip,
          fetchedTip, Vector("refs/heads/main"), Vector.empty, forceUsed = false, tagsIncluded = false,
          mirrorUsed = false, endpointPolicyAccepted = true, completed = true, executionDigest = "e" * 64))))
      }
      def loadPostStateC(repositoryIdentity: String, receiptDigest: String): Consequence[Option[State]] = {
        repositoryIdentity shouldBe input.repositoryIdentity
        postLoads += 1
        if (failureStage == "post") Consequence.stateConflict("host push post-state load failed")
        else Consequence.success(if (postMissing) None else Some(postTransform(before.copy(
          evidenceBinding = receiptDigest, inspectionDigest = "f" * 64))))
      }
    }
    def assess() = RepositorySyncPushAssessment.assessC(boundary, scope, fetch, preservation, relation, pushes)
  }

  "RepositorySync retained non-force push admission" should {
    "admit an exact local-ahead push without fetch, local mutation or a convergence claim" in {
      val fixture = new Fixture
      val admitted = take(fixture.assess())
      admitted.frozen.state shouldBe fixture.frozen
      admitted.receipt.destinationTipAfterOid shouldBe fixture.localTip
      admitted.after.localHeadOid shouldBe fixture.localTip
      admitted.after.localTreeOid shouldBe fixture.before.localTreeOid
      admitted.after.trackingTipOid shouldBe fixture.fetchedTip
      admitted.digest should fullyMatch regex "[0-9a-f]{64}"
      take(fixture.assess()) shouldBe admitted
      fixture.snapshotLoads shouldBe 1
      fixture.fetchLoads shouldBe 1
      fixture.loads shouldBe 1
    }

    "preserve a full-state checkpoint and use its local tip and tree" in {
      val fixture = new Fixture(dirty = true)
      val admitted = take(fixture.assess())
      admitted.receipt.sourceTipOid shouldBe fixture.preservation.checkpoint.get.checkpointCommitOid
      admitted.after.localTreeOid shouldBe fixture.checkpointFrozen.completeTreeOid
      admitted.after.localTreeDigest shouldBe fixture.checkpointFrozen.completeTreeDigest
      fixture.fetchLoads shouldBe 1
    }

    "refuse every non-local-ahead route before reading push evidence" in {
      Vector(Relation.Equal, Relation.RemoteAhead, Relation.Diverged).foreach { route =>
        val fixture = new Fixture(route = route)
        fixture.assess() shouldBe a[Consequence.Failure[?]]
        fixture.frozenLoads shouldBe 0
        fixture.receiptLoads shouldBe 0
        fixture.postLoads shouldBe 0
      }
    }

    "re-admit upstream identities and reject forged relation facts before loading evidence" in {
      val f = new Fixture
      Vector(
        RepositorySyncPushAssessment.assessC(f.boundary.copy(digest = "0" * 64), f.scope, f.fetch, f.preservation, f.relation, f.pushes),
        RepositorySyncPushAssessment.assessC(f.boundary, f.scope.copy(digest = "0" * 64), f.fetch, f.preservation, f.relation, f.pushes),
        RepositorySyncPushAssessment.assessC(f.boundary, f.scope, f.fetch.copy(digest = "0" * 64), f.preservation, f.relation, f.pushes),
        RepositorySyncPushAssessment.assessC(f.boundary, f.scope, f.fetch, f.preservation.copy(digest = "0" * 64), f.relation, f.pushes),
        RepositorySyncPushAssessment.assessC(f.boundary, f.scope, f.fetch, f.preservation,
          f.relation.copy(digest = "0" * 64), f.pushes),
        RepositorySyncPushAssessment.assessC(f.boundary, f.scope, f.fetch, f.preservation,
          f.relation.copy(relation = Relation.RemoteAhead), f.pushes),
        RepositorySyncPushAssessment.assessC(f.boundary, f.scope, f.fetch, f.preservation,
          f.relation.copy(receipt = f.relation.receipt.copy(localAhead = 4L)), f.pushes),
        RepositorySyncPushAssessment.assessC(f.boundary, f.scope, f.fetch, f.preservation,
          f.relation.copy(receipt = null), f.pushes),
        RepositorySyncPushAssessment.assessC(null, f.scope, f.fetch, f.preservation, f.relation, f.pushes),
        RepositorySyncPushAssessment.assessC(f.boundary, null, f.fetch, f.preservation, f.relation, f.pushes),
        RepositorySyncPushAssessment.assessC(f.boundary, f.scope, null, f.preservation, f.relation, f.pushes),
        RepositorySyncPushAssessment.assessC(f.boundary, f.scope, f.fetch, null, f.relation, f.pushes),
        RepositorySyncPushAssessment.assessC(f.boundary, f.scope, f.fetch, f.preservation, null, f.pushes),
        RepositorySyncPushAssessment.assessC(f.boundary, f.scope, f.fetch, f.preservation, f.relation, null))
        .foreach(_ shouldBe a[Consequence.Failure[?]])
      f.frozenLoads shouldBe 0
      f.receiptLoads shouldBe 0
      f.postLoads shouldBe 0
    }

    "reject pre-state identity, tip, tree and clean-gate drift before reading a receipt" in {
      stateDrifts.foreach { transform =>
        val fixture = new Fixture
        fixture.frozenValue = Some(fixture.frozen.copy(before = transform(fixture.before)))
        fixture.assess() shouldBe a[Consequence.Failure[?]]
        fixture.receiptLoads shouldBe 0
        fixture.postLoads shouldBe 0
      }
    }

    "reject remote advance or incomplete and replacement ancestor evidence without re-fetch" in {
      val transforms: Vector[FrozenState => FrozenState] = Vector(
        _.copy(before = null), _.copy(advertisedRemoteTipOid = "0" * 40),
        _.copy(advertisedRemoteTipOid = null), _.copy(remoteAncestorOfLocal = false),
        _.copy(historyComplete = false), _.copy(replacementHistory = true),
        _.copy(graphInspectionDigest = "bad"))
      transforms.foreach { transform =>
        val fixture = new Fixture
        fixture.frozenValue = Some(transform(fixture.frozen))
        fixture.assess() shouldBe a[Consequence.Failure[?]]
        fixture.receiptLoads shouldBe 0
        fixture.postLoads shouldBe 0
        fixture.fetchLoads shouldBe 1
      }
    }

    "require exact non-force remote-write authority rather than accepting a generic approval" in {
      val transforms: Vector[Authority => Authority] = Vector(
        _ => null, _.copy(relationDigest = "0" * 64), _.copy(repositoryIdentity = "other"),
        _.copy(worktreeIdentity = "other"), _.copy(localBranch = "release"), _.copy(trackingRemote = "upstream"),
        _.copy(trackingBranch = "release"), _.copy(remoteEndpointIdentity = "other"),
        _.copy(repositoryPolicyVersion = "policy-v2"), _.copy(sourceTipOid = "0" * 40),
        _.copy(destinationTipBeforeOid = "0" * 40), _.copy(nonForceOnlyAccepted = false),
        _.copy(remoteWriteAuthorityAccepted = false), _.copy(evidenceDigest = "bad"))
      transforms.foreach { transform =>
        val fixture = new Fixture
        fixture.frozenValue = Some(fixture.frozen.copy(authority = transform(fixture.authority)))
        fixture.assess() shouldBe a[Consequence.Failure[?]]
        fixture.receiptLoads shouldBe 0
        fixture.postLoads shouldBe 0
      }
    }

    "reject wrong ref or tip, force, tags, mirror, rejection and incomplete execution receipts" in {
      val transforms: Vector[PushReceipt => PushReceipt] = Vector(
        _.copy(frozenStateDigest = "0" * 64), _.copy(sourceLocalRef = "refs/heads/release"),
        _.copy(destinationRemoteRef = "refs/heads/release"), _.copy(sourceTipOid = "0" * 40),
        _.copy(destinationTipBeforeOid = "0" * 40), _.copy(destinationTipAfterOid = "0" * 40),
        _.copy(destinationTipAfterOid = null), _.copy(changedRemoteRefs = Vector.empty),
        _.copy(changedRemoteRefs = Vector("refs/heads/main", "refs/tags/release")),
        _.copy(changedRemoteRefs = Vector("refs/heads/main", "refs/heads/main")),
        _.copy(changedRemoteRefs = null), _.copy(forceUsed = true), _.copy(tagsIncluded = true),
        _.copy(mirrorUsed = true), _.copy(endpointPolicyAccepted = false),
        _.copy(completed = false), _.copy(executionDigest = "bad"))
      transforms.foreach { transform =>
        val fixture = new Fixture
        fixture.receiptTransform = transform
        fixture.assess() shouldBe a[Consequence.Failure[?]]
        fixture.postLoads shouldBe 0
      }
    }

    "admit only a precisely reported selected tracking ref update and corresponding post-state" in {
      val fixture = new Fixture
      fixture.receiptTransform = _.copy(trackingTipAfterOid = fixture.localTip,
        changedLocalRefs = Vector("refs/remotes/origin/main"))
      fixture.postTransform = _.copy(trackingTipOid = fixture.localTip)
      val accepted = take(fixture.assess())
      accepted.after.localHeadOid shouldBe fixture.before.localHeadOid
      accepted.after.trackingTipOid shouldBe fixture.localTip
      take(fixture.assess()) shouldBe accepted
      val transforms: Vector[PushReceipt => PushReceipt] = Vector(
        _.copy(trackingTipAfterOid = "0" * 40), _.copy(changedLocalRefs = null),
        _.copy(changedLocalRefs = Vector("refs/heads/main")),
        _.copy(changedLocalRefs = Vector("refs/remotes/origin/main")),
        _.copy(trackingTipAfterOid = fixture.localTip, changedLocalRefs = Vector.empty),
        _.copy(trackingTipAfterOid = fixture.localTip,
          changedLocalRefs = Vector("refs/remotes/origin/main", "refs/remotes/origin/main")),
        _.copy(trackingTipAfterOid = fixture.localTip,
          changedLocalRefs = Vector("refs/remotes/origin/main", "refs/remotes/upstream/main")))
      transforms.foreach { transform =>
        val invalid = new Fixture
        invalid.receiptTransform = transform
        invalid.assess() shouldBe a[Consequence.Failure[?]]
        invalid.postLoads shouldBe 0
      }
    }

    "reject post-state drift even when the remote accepted the selected tip" in {
      stateDrifts.foreach { transform =>
        val fixture = new Fixture
        fixture.postTransform = transform
        fixture.assess() shouldBe a[Consequence.Failure[?]]
        fixture.postLoads shouldBe 1
      }
    }

    "stop at missing, null and failed evidence without inventing success or retrying a push" in {
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
      Vector("freeze", "receipt", "post").foreach { stage =>
        val fixture = new Fixture
        fixture.failureStage = stage
        fixture.assess() shouldBe a[Consequence.Failure[?]]
        if (stage == "freeze") fixture.receiptLoads shouldBe 0
        if (stage != "post") fixture.postLoads shouldBe 0
        fixture.failureStage = ""
        val admitted = take(fixture.assess())
        take(fixture.assess()) shouldBe admitted
        fixture.fetchLoads shouldBe 1
      }
    }

    "bind authority, ancestor graph, pre-state, execution and post-state evidence to the result digest" in {
      val fixture = new Fixture
      val original = take(fixture.assess())
      fixture.frozenValue = Some(fixture.frozen.copy(authority = fixture.authority.copy(evidenceDigest = "0" * 64)))
      take(fixture.assess()).digest should not be original.digest
      fixture.frozenValue = Some(fixture.frozen.copy(graphInspectionDigest = "0" * 64))
      take(fixture.assess()).digest should not be original.digest
      fixture.frozenValue = Some(fixture.frozen.copy(before = fixture.before.copy(inspectionDigest = "0" * 64)))
      take(fixture.assess()).digest should not be original.digest
      fixture.frozenValue = Some(fixture.frozen)
      fixture.receiptTransform = _.copy(executionDigest = "0" * 64)
      take(fixture.assess()).digest should not be original.digest
      fixture.receiptTransform = identity
      fixture.postTransform = _.copy(inspectionDigest = "0" * 64)
      take(fixture.assess()).digest should not be original.digest
      fixture.postTransform = identity
      take(fixture.assess()) shouldBe original
    }

    "accept consistent SHA-256 object identities without narrowing them to SHA-1" in {
      val fixture = new Fixture(oidWidth = 64)
      val admitted = take(fixture.assess())
      admitted.receipt.sourceTipOid.length shouldBe 64
      admitted.after.localTreeOid.length shouldBe 64
      take(fixture.assess()) shouldBe admitted
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
