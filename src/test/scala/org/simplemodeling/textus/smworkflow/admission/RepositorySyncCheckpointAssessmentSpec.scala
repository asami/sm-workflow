package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value.RepositorySyncStartInput

/**
 * @since   Sep. 28, 2026
 * @version Oct.  8, 2026
 */
final class RepositorySyncCheckpointAssessmentSpec extends AnyWordSpec with Matchers {
  import RepositorySyncCheckpointAssessment.*

  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"RepositorySync preservation fixture failed: $failure")
  }

  private final class Fixture(dirty: Boolean = true, staged: Boolean = false,
    oidWidth: Int = 40) {
    val paths = if (dirty) Vector("deleted.txt", "new.txt", "src/main.scala") else Vector.empty
    val input = RepositorySyncStartInput(
      "repository-1", "main", "origin", "main", "remote-1", "FULL_STATE", "policy-v1")
    val snapshot = RepositorySyncBoundaryAssessment.Snapshot(
      "snapshot-1", "repository-1", "worktree-1", "main", "origin", "main", "remote-1",
      "policy-v1", "a" * oidWidth, "b" * 64, "c" * 64, "d" * 64,
      paths, if (staged) "COMPLETE_FULLY_STAGED" else "EMPTY",
      detachedHead = false, pendingMerge = false)
    val boundary = take(RepositorySyncBoundaryAssessment.assessC(input,
      new RepositorySyncBoundaryAssessment.Port {
        def captureC(repositoryIdentity: String) = Consequence.success(Some(snapshot))
      }))
    val scopeEvidence = RepositorySyncDirtyScopeAssessment.Evidence(
      boundary.digest, "policy-v1", if (staged) paths else Vector.empty,
      paths, Vector.empty, "e" * 64)
    val scope = take(RepositorySyncDirtyScopeAssessment.assessC(boundary,
      new RepositorySyncDirtyScopeAssessment.Port {
        def loadC(repositoryIdentity: String, snapshotDigest: String) =
          Consequence.success(Some(scopeEvidence))
      }))
    val fetchReceipt = RepositorySyncFetchAssessment.Receipt(
      boundary.digest, scope.digest, "repository-1", "worktree-1", "main", "origin", "main",
      "remote-1", "f" * oidWidth, Some("9" * oidWidth), "f" * oidWidth,
      snapshot.headOid, snapshot.headOid, snapshot.treeDigest, snapshot.indexDigest,
      snapshot.worktreeDigest, "refs/remotes/origin/main", Vector("refs/remotes/origin/main"),
      endpointPolicyAccepted = true, fetchCompleted = true, executionDigest = "1" * 64)
    val fetch = take(RepositorySyncFetchAssessment.assessC(boundary, scope,
      new RepositorySyncFetchAssessment.Port {
        def fetchC(repositoryIdentity: String, boundaryDigest: String,
          dirtyScopeDigest: String) = Consequence.success(Some(fetchReceipt))
      }))
    var frozen = FrozenState(boundary.digest, scope.digest, fetch.digest,
      "repository-1", "worktree-1", "main", "policy-v1", snapshot.headOid,
      snapshot.treeDigest, snapshot.indexDigest, snapshot.worktreeDigest, paths,
      "3" * oidWidth, "4" * 64, "5" * 64)
    var checkpointOid = "2" * oidWidth
    var freezeMissing = false
    var checkpointMissing = false
    var cleanMissing = false
    var checkpointFailure = false
    var receiptTransform: Receipt => Receipt = identity
    var cleanTransform: CleanState => CleanState = identity
    var freezeLoads = 0
    var checkpointLoads = 0
    var cleanLoads = 0
    var requestedFreezeDigest: Option[String] = None
    var requestedPreservationDigest: Option[String] = None

    val port = new Port {
      def loadFrozenC(repositoryIdentity: String, boundaryDigest: String,
        dirtyScopeDigest: String, fetchDigest: String): Consequence[Option[FrozenState]] = {
        repositoryIdentity shouldBe input.repositoryIdentity
        boundaryDigest shouldBe boundary.digest
        dirtyScopeDigest shouldBe scope.digest
        fetchDigest shouldBe fetch.digest
        freezeLoads += 1
        Consequence.success(if (freezeMissing) None else Some(frozen))
      }

      def loadCheckpointC(repositoryIdentity: String,
        frozenStateDigest: String): Consequence[Option[Receipt]] = {
        repositoryIdentity shouldBe input.repositoryIdentity
        checkpointLoads += 1
        requestedFreezeDigest = Some(frozenStateDigest)
        if (checkpointFailure) Consequence.stateConflict("host receipt load failed")
        else Consequence.success(if (checkpointMissing) None else Some(receiptTransform(Receipt(
          frozenStateDigest, checkpointOid, Vector(snapshot.headOid), frozen.completeTreeOid,
          frozen.completeTreeDigest, checkpointPurpose, completed = true, executionDigest = "6" * 64))))
      }

      def loadCleanStateC(repositoryIdentity: String,
        preservationDigest: String): Consequence[Option[CleanState]] = {
        repositoryIdentity shouldBe input.repositoryIdentity
        cleanLoads += 1
        requestedPreservationDigest = Some(preservationDigest)
        Consequence.success(if (cleanMissing) None else Some(cleanTransform(CleanState(
          preservationDigest, "repository-1", "worktree-1", "main", "origin", "main",
          "remote-1", "policy-v1", if (dirty) checkpointOid else snapshot.headOid,
          if (dirty) frozen.completeTreeOid else "7" * oidWidth,
          if (dirty) frozen.completeTreeDigest else snapshot.treeDigest,
          fetchReceipt.fetchedTipOid, Vector.empty, "EMPTY", detachedHead = false,
          pendingMerge = false, inspectionDigest = "8" * 64))))
      }
    }

    def assess() = RepositorySyncCheckpointAssessment.assessC(boundary, scope, fetch, port)
  }

  "RepositorySync full-state preservation admission" should {
    "bind all dirty paths to a one-parent non-acceptance checkpoint and a clean post-state" in {
      val fixture = new Fixture
      val admitted = take(fixture.assess())
      admitted.frozen.get.state.includedPaths shouldBe fixture.paths
      admitted.frozen.get.digest shouldBe fixture.requestedFreezeDigest.get
      admitted.checkpoint.get.parentOids shouldBe Vector(fixture.snapshot.headOid)
      admitted.checkpoint.get.purpose shouldBe checkpointPurpose
      admitted.cleanState.localHeadOid shouldBe fixture.checkpointOid
      admitted.cleanState.trackingTipOid shouldBe fixture.fetchReceipt.fetchedTipOid
      admitted.digest.matches("[0-9a-f]{64}") shouldBe true
      take(fixture.assess()) shouldBe admitted
    }

    "admit an exact complete-fully-staged scope without normalizing the index" in {
      val fixture = new Fixture(staged = true)
      val admitted = take(fixture.assess())
      admitted.frozen.get.state.sourceIndexDigest shouldBe fixture.snapshot.indexDigest
      admitted.frozen.get.state.includedPaths shouldBe fixture.scopeEvidence.stagedPaths
      fixture.snapshot.indexShape shouldBe "COMPLETE_FULLY_STAGED"
    }

    "skip checkpoint evidence for an initially clean repository but still require the clean gate" in {
      val fixture = new Fixture(dirty = false)
      val admitted = take(fixture.assess())
      admitted.frozen shouldBe None
      admitted.checkpoint shouldBe None
      admitted.cleanState.localHeadOid shouldBe fixture.snapshot.headOid
      fixture.requestedPreservationDigest shouldBe Some(fixture.fetch.digest)
      fixture.freezeLoads shouldBe 0
      fixture.checkpointLoads shouldBe 0
      fixture.cleanLoads shouldBe 1
      fixture.cleanMissing = true
      fixture.assess() shouldBe a[Consequence.Failure[?]]
    }

    "reject missing or stale full-state freezes before reading any checkpoint receipt" in {
      val transformations: Vector[FrozenState => FrozenState] = Vector(
        _.copy(boundaryDigest = "0" * 64), _.copy(dirtyScopeDigest = "0" * 64),
        _.copy(fetchDigest = "0" * 64), _.copy(repositoryIdentity = "other"),
        _.copy(worktreeIdentity = "other"), _.copy(localBranch = "release"),
        _.copy(repositoryPolicyVersion = "policy-v2"), _.copy(sourceHeadOid = "0" * 40),
        _.copy(sourceTreeDigest = "0" * 64), _.copy(sourceIndexDigest = "0" * 64),
        _.copy(sourceWorktreeDigest = "0" * 64), _.copy(includedPaths = Vector("new.txt")),
        _.copy(includedPaths = Vector("../escape")), _.copy(completeTreeOid = "bad"),
        _.copy(completeTreeDigest = "bad"), _.copy(freezeEvidenceDigest = "bad"))
      transformations.foreach { transform =>
        val fixture = new Fixture
        fixture.frozen = transform(fixture.frozen)
        fixture.assess() shouldBe a[Consequence.Failure[?]]
        fixture.checkpointLoads shouldBe 0
        fixture.cleanLoads shouldBe 0
      }
      val fixture = new Fixture
      fixture.freezeMissing = true
      fixture.assess() shouldBe a[Consequence.Failure[?]]
      fixture.checkpointLoads shouldBe 0
    }

    "reject wrong trees, rewritten or merged history, acceptance claims, and incomplete commits" in {
      val transformations: Vector[Receipt => Receipt] = Vector(
        _.copy(frozenStateDigest = "0" * 64), _.copy(checkpointCommitOid = "bad"),
        _.copy(checkpointCommitOid = "a" * 40), _.copy(parentOids = Vector.empty),
        _.copy(parentOids = Vector("0" * 40)),
        _.copy(parentOids = Vector("a" * 40, "f" * 40)),
        _.copy(committedTreeOid = "0" * 40), _.copy(committedTreeDigest = "0" * 64),
        _.copy(purpose = "ACCEPTANCE"), _.copy(purpose = "RELEASE"),
        _.copy(completed = false), _.copy(executionDigest = "bad"))
      transformations.foreach { transform =>
        val fixture = new Fixture
        fixture.receiptTransform = transform
        fixture.assess() shouldBe a[Consequence.Failure[?]]
        fixture.cleanLoads shouldBe 0
      }
      val fixture = new Fixture
      fixture.checkpointMissing = true
      fixture.assess() shouldBe a[Consequence.Failure[?]]
      fixture.cleanLoads shouldBe 0
    }

    "fail the clean gate on any post-checkpoint identity, tree, tracking, or dirty-state drift" in {
      val transformations: Vector[CleanState => CleanState] = Vector(
        _.copy(preservationDigest = "0" * 64), _.copy(repositoryIdentity = "other"),
        _.copy(worktreeIdentity = "other"), _.copy(localBranch = "release"),
        _.copy(trackingRemote = "upstream"), _.copy(trackingBranch = "release"),
        _.copy(remoteEndpointIdentity = "other"), _.copy(repositoryPolicyVersion = "policy-v2"),
        _.copy(localHeadOid = "0" * 40), _.copy(localTreeOid = "0" * 40),
        _.copy(localTreeDigest = "0" * 64), _.copy(trackingTipOid = "0" * 40),
        _.copy(dirtyPaths = Vector("new.txt")), _.copy(indexShape = "COMPLETE_FULLY_STAGED"),
        _.copy(detachedHead = true), _.copy(pendingMerge = true), _.copy(inspectionDigest = "bad"))
      transformations.foreach { transform =>
        val fixture = new Fixture
        fixture.cleanTransform = transform
        fixture.assess() shouldBe a[Consequence.Failure[?]]
      }
      val fixture = new Fixture
      fixture.cleanMissing = true
      fixture.assess() shouldBe a[Consequence.Failure[?]]
    }

    "reject clean-route mutation without requiring or inventing a checkpoint" in {
      val transformations: Vector[CleanState => CleanState] = Vector(
        _.copy(localHeadOid = "2" * 40), _.copy(localTreeDigest = "0" * 64),
        _.copy(trackingTipOid = "0" * 40), _.copy(dirtyPaths = Vector("new.txt")))
      transformations.foreach { transform =>
        val fixture = new Fixture(dirty = false)
        fixture.cleanTransform = transform
        fixture.assess() shouldBe a[Consequence.Failure[?]]
        fixture.freezeLoads shouldBe 0
        fixture.checkpointLoads shouldBe 0
      }
    }

    "bind the result digest to freeze, commit, and clean-inspection evidence" in {
      val fixture = new Fixture
      val baseline = take(fixture.assess()).digest
      fixture.frozen = fixture.frozen.copy(freezeEvidenceDigest = "9" * 64)
      val refrozen = take(fixture.assess()).digest
      refrozen should not be baseline
      fixture.receiptTransform = _.copy(executionDigest = "a" * 64)
      val recommitted = take(fixture.assess()).digest
      recommitted should not be refrozen
      fixture.cleanTransform = _.copy(inspectionDigest = "b" * 64)
      val reinspected = take(fixture.assess()).digest
      reinspected should not be recommitted
      fixture.checkpointOid = "c" * 40
      take(fixture.assess()).digest should not be reinspected
    }

    "reject forged or unsafe upstream assessments before loading preservation evidence" in {
      val fixture = new Fixture
      val outcomes = Vector(
        RepositorySyncCheckpointAssessment.assessC(
          fixture.boundary.copy(digest = "0" * 64), fixture.scope, fixture.fetch, fixture.port),
        RepositorySyncCheckpointAssessment.assessC(fixture.boundary,
          fixture.scope.copy(digest = "0" * 64), fixture.fetch, fixture.port),
        RepositorySyncCheckpointAssessment.assessC(fixture.boundary, fixture.scope,
          fixture.fetch.copy(digest = "0" * 64), fixture.port),
        RepositorySyncCheckpointAssessment.assessC(fixture.boundary,
          fixture.scope.copy(evidence = fixture.scopeEvidence.copy(unsafeFindings = Vector("secret"))),
          fixture.fetch, fixture.port),
        RepositorySyncCheckpointAssessment.assessC(fixture.boundary, fixture.scope,
          fixture.fetch.copy(receipt = fixture.fetchReceipt.copy(fetchCompleted = false)), fixture.port),
        RepositorySyncCheckpointAssessment.assessC(null, fixture.scope, fixture.fetch, fixture.port))
      outcomes.foreach(_ shouldBe a[Consequence.Failure[?]])
      fixture.freezeLoads shouldBe 0
      fixture.checkpointLoads shouldBe 0
      fixture.cleanLoads shouldBe 0
    }

    "propagate host load failure and admit only consistently sized SHA-256 object identities" in {
      val fixture = new Fixture(oidWidth = 64)
      take(fixture.assess()).cleanState.localHeadOid.length shouldBe 64
      fixture.checkpointFailure = true
      fixture.assess() shouldBe a[Consequence.Failure[?]]
      fixture.cleanLoads shouldBe 1
      fixture.checkpointFailure = false
      fixture.receiptTransform = _.copy(checkpointCommitOid = "2" * 40)
      fixture.assess() shouldBe a[Consequence.Failure[?]]
      fixture.cleanLoads shouldBe 1
      fixture.receiptTransform = identity
      fixture.frozen = fixture.frozen.copy(completeTreeOid = "3" * 40)
      fixture.assess() shouldBe a[Consequence.Failure[?]]
    }
  }
}
