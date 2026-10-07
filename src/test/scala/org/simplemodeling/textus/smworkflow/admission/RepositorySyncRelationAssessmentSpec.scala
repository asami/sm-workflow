package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value.RepositorySyncStartInput

/**
 * @since   Sep. 28, 2026
 * @version Oct.  8, 2026
 */
final class RepositorySyncRelationAssessmentSpec extends AnyWordSpec with Matchers {
  import RepositorySyncRelationAssessment.*

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
    val frozen = RepositorySyncCheckpointAssessment.FrozenState(boundary.digest, scope.digest,
      fetch.digest, "repository-1", "worktree-1", "main", "policy-v1", snapshot.headOid,
      snapshot.treeDigest, snapshot.indexDigest, snapshot.worktreeDigest, paths,
      "3" * oidWidth, "4" * 64, "5" * 64)
    val preservation = take(RepositorySyncCheckpointAssessment.assessC(boundary, scope, fetch,
      new RepositorySyncCheckpointAssessment.Port {
        def loadFrozenC(repositoryIdentity: String, boundaryDigest: String,
          dirtyScopeDigest: String, fetchDigest: String) = Consequence.success(Some(frozen))
        def loadCheckpointC(repositoryIdentity: String, frozenStateDigest: String) =
          Consequence.success(Some(RepositorySyncCheckpointAssessment.Receipt(
            frozenStateDigest, localTip, Vector(snapshot.headOid), frozen.completeTreeOid,
            frozen.completeTreeDigest, RepositorySyncCheckpointAssessment.checkpointPurpose,
            completed = true, executionDigest = "6" * 64)))
        def loadCleanStateC(repositoryIdentity: String, preservationDigest: String) =
          Consequence.success(Some(RepositorySyncCheckpointAssessment.CleanState(
            preservationDigest, "repository-1", "worktree-1", "main", "origin", "main",
            "remote-1", "policy-v1", localTip, if (dirty) frozen.completeTreeOid else "7" * oidWidth,
            if (dirty) frozen.completeTreeDigest else snapshot.treeDigest, fetchedTip,
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
    def assess() = RepositorySyncRelationAssessment.assessC(boundary, scope, fetch, preservation, port)
  }

  "RepositorySync relation admission" should {
    "classify exact equal tips only with the same merge base and zero ahead/behind" in {
      val fixture = new Fixture(equal = true)
      val admitted = take(fixture.assess())
      admitted.relation shouldBe Relation.Equal
      admitted.relation.nextAction shouldBe "Verify convergence"
      admitted.receipt.localAhead shouldBe 0L
      admitted.receipt.remoteAhead shouldBe 0L
      take(fixture.assess()) shouldBe admitted
    }

    "classify local-ahead and remote-ahead from their exact ancestor tip" in {
      val fixture = new Fixture
      fixture.receiptValue = Some(fixture.baseline.copy(mergeBases = Vector(fixture.fetchedTip),
        localAhead = 2L, remoteAhead = 0L))
      take(fixture.assess()).relation shouldBe Relation.LocalAhead
      fixture.receiptValue = Some(fixture.baseline)
      take(fixture.assess()).relation shouldBe Relation.RemoteAhead
    }

    "classify a divergence only when neither tip is the single merge base" in {
      val fixture = new Fixture
      fixture.receiptValue = Some(fixture.baseline.copy(mergeBases = Vector("0" * 40),
        localAhead = 2L, remoteAhead = 3L))
      val admitted = take(fixture.assess())
      admitted.relation shouldBe Relation.Diverged
      admitted.relation.nextAction shouldBe "Prepare merge of the exact local and fetched tips"
    }

    "use the post-checkpoint local tip over a remote-ahead base without another fetch" in {
      val fixture = new Fixture(dirty = true)
      val admitted = take(fixture.assess())
      admitted.relation shouldBe Relation.Diverged
      admitted.receipt.localTipOid shouldBe fixture.preservation.checkpoint.get.checkpointCommitOid
      admitted.receipt.mergeBases shouldBe Vector(fixture.snapshot.headOid)
      admitted.receipt.fetchedTipOid shouldBe fixture.fetchReceipt.fetchedTipOid
      fixture.receiptValue = Some(fixture.baseline.copy(localTipOid = fixture.snapshot.headOid,
        localAhead = 0L))
      fixture.assess() shouldBe a[Consequence.Failure[?]]
      fixture.snapshotLoads shouldBe 1
      fixture.fetchLoads shouldBe 1
    }

    "reject stale repository, mapping, preservation, policy and tip identities" in {
      val transformations: Vector[Receipt => Receipt] = Vector(
        _.copy(preservationDigest = "0" * 64), _.copy(repositoryIdentity = "other"),
        _.copy(worktreeIdentity = "other"), _.copy(localBranch = "release"),
        _.copy(trackingRemote = "upstream"), _.copy(trackingBranch = "release"),
        _.copy(remoteEndpointIdentity = "other"), _.copy(repositoryPolicyVersion = "policy-v2"),
        _.copy(localTipOid = "0" * 40), _.copy(fetchedTipOid = "0" * 40),
        _.copy(inspectionDigest = "bad"))
      transformations.foreach { transform =>
        val fixture = new Fixture
        fixture.receiptValue = Some(transform(fixture.baseline))
        fixture.assess() shouldBe a[Consequence.Failure[?]]
      }
    }

    "reject unrelated, ambiguous, duplicate, incomplete, replacement and malformed graph evidence" in {
      val transformations: Vector[Receipt => Receipt] = Vector(
        _.copy(mergeBases = Vector.empty), _.copy(mergeBases = Vector("a" * 40, "0" * 40)),
        _.copy(mergeBases = Vector("a" * 40, "a" * 40)), _.copy(mergeBases = null),
        _.copy(mergeBases = Vector("bad")), _.copy(mergeBases = Vector("a" * 64)),
        _.copy(mergeBases = Vector(null)), _.copy(localAhead = -1L), _.copy(remoteAhead = -1L),
        _.copy(historyComplete = false), _.copy(replacementHistory = true))
      transformations.foreach { transform =>
        val fixture = new Fixture
        fixture.receiptValue = Some(transform(fixture.baseline))
        fixture.assess() shouldBe a[Consequence.Failure[?]]
      }
    }

    "reject every conflicting merge-base and ahead/behind classification shape" in {
      val fixture = new Fixture
      val receipts = Vector(
        fixture.baseline.copy(localAhead = 0L, remoteAhead = 0L),
        fixture.baseline.copy(mergeBases = Vector(fixture.fetchedTip)),
        fixture.baseline.copy(mergeBases = Vector("0" * 40)),
        fixture.baseline.copy(localAhead = 2L, remoteAhead = 0L),
        fixture.baseline.copy(localAhead = 2L),
        fixture.baseline.copy(localAhead = 2L, mergeBases = Vector(fixture.fetchedTip)),
        fixture.baseline.copy(localAhead = 2L, remoteAhead = 0L, mergeBases = Vector("0" * 40)))
      receipts.foreach { value =>
        fixture.receiptValue = Some(value)
        fixture.assess() shouldBe a[Consequence.Failure[?]]
      }
      val same = new Fixture(equal = true)
      Vector(same.baseline.copy(localAhead = 1L), same.baseline.copy(remoteAhead = 1L),
        same.baseline.copy(mergeBases = Vector("0" * 40))).foreach { value =>
        same.receiptValue = Some(value)
        same.assess() shouldBe a[Consequence.Failure[?]]
      }
    }

    "re-admit preservation and all upstream evidence before any host graph load" in {
      val fixture = new Fixture(dirty = true)
      val preservation = fixture.preservation
      val outcomes = Vector(
        RepositorySyncRelationAssessment.assessC(fixture.boundary.copy(digest = "0" * 64),
          fixture.scope, fixture.fetch, preservation, fixture.port),
        RepositorySyncRelationAssessment.assessC(fixture.boundary,
          fixture.scope.copy(digest = "0" * 64), fixture.fetch, preservation, fixture.port),
        RepositorySyncRelationAssessment.assessC(fixture.boundary, fixture.scope,
          fixture.fetch.copy(digest = "0" * 64), preservation, fixture.port),
        RepositorySyncRelationAssessment.assessC(fixture.boundary, fixture.scope, fixture.fetch,
          preservation.copy(digest = "0" * 64), fixture.port),
        RepositorySyncRelationAssessment.assessC(fixture.boundary, fixture.scope, fixture.fetch,
          preservation.copy(frozen = preservation.frozen.map(_.copy(digest = "0" * 64))), fixture.port),
        RepositorySyncRelationAssessment.assessC(fixture.boundary, fixture.scope, fixture.fetch,
          preservation.copy(checkpoint = preservation.checkpoint.map(_.copy(purpose = "ACCEPTANCE"))),
          fixture.port),
        RepositorySyncRelationAssessment.assessC(fixture.boundary, fixture.scope, fixture.fetch,
          preservation.copy(cleanState = preservation.cleanState.copy(dirtyPaths = Vector("new.txt"))),
          fixture.port),
        RepositorySyncRelationAssessment.assessC(fixture.boundary, fixture.scope, fixture.fetch,
          preservation.copy(frozen = Some(null)), fixture.port),
        RepositorySyncRelationAssessment.assessC(fixture.boundary, fixture.scope, fixture.fetch,
          null, fixture.port),
        RepositorySyncRelationAssessment.assessC(null, fixture.scope, fixture.fetch, preservation, fixture.port))
      outcomes.foreach(_ shouldBe a[Consequence.Failure[?]])
      fixture.loads shouldBe 0
    }

    "propagate missing and failed host graph receipt loads without inventing a route" in {
      val fixture = new Fixture
      fixture.receiptValue = None
      fixture.assess() shouldBe a[Consequence.Failure[?]]
      fixture.receiptValue = Some(null)
      fixture.assess() shouldBe a[Consequence.Failure[?]]
      fixture.receiptValue = Some(fixture.baseline)
      fixture.loadFailure = true
      fixture.assess() shouldBe a[Consequence.Failure[?]]
      fixture.loadFailure = false
      take(fixture.assess()).relation shouldBe Relation.RemoteAhead
    }

    "bind graph counts and inspection evidence in the deterministic result digest" in {
      val fixture = new Fixture
      val baseline = take(fixture.assess()).digest
      baseline.matches("[0-9a-f]{64}") shouldBe true
      fixture.receiptValue = Some(fixture.baseline.copy(inspectionDigest = "0" * 64))
      val reinspected = take(fixture.assess()).digest
      reinspected should not be baseline
      fixture.receiptValue = Some(fixture.baseline.copy(remoteAhead = 4L))
      take(fixture.assess()).digest should not be baseline
    }

    "accept consistent SHA-256 commit identities in all four relation shapes" in {
      val equal = new Fixture(equal = true, oidWidth = 64)
      take(equal.assess()).relation shouldBe Relation.Equal
      val ahead = new Fixture(oidWidth = 64)
      take(ahead.assess()).relation shouldBe Relation.RemoteAhead
      ahead.receiptValue = Some(ahead.baseline.copy(mergeBases = Vector(ahead.fetchedTip),
        localAhead = 1L, remoteAhead = 0L))
      take(ahead.assess()).relation shouldBe Relation.LocalAhead
      val diverged = new Fixture(dirty = true, oidWidth = 64)
      take(diverged.assess()).relation shouldBe Relation.Diverged
    }
  }
}
