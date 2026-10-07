package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value.RepositorySyncStartInput

/**
 * @since   Sep. 28, 2026
 * @version Oct.  8, 2026
 */
final class RepositorySyncFetchAssessmentSpec extends AnyWordSpec with Matchers {
  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"RepositorySync fetch fixture failed: $failure")
  }

  private val input = RepositorySyncStartInput(
    "repository-1", "main", "origin", "main", "remote-1", "FULL_STATE", "policy-v1")
  private val snapshot = RepositorySyncBoundaryAssessment.Snapshot(
    "snapshot-1", "repository-1", "worktree-1", "main", "origin", "main",
    "remote-1", "policy-v1", "a" * 40, "b" * 64, "c" * 64, "d" * 64,
    Vector("notes.txt"), "EMPTY", detachedHead = false, pendingMerge = false)
  private val boundary = take(RepositorySyncBoundaryAssessment.assessC(input,
    new RepositorySyncBoundaryAssessment.Port {
      def captureC(repositoryIdentity: String) = Consequence.success(Some(snapshot))
    }))
  private val scopeEvidence = RepositorySyncDirtyScopeAssessment.Evidence(
    boundary.digest, "policy-v1", Vector.empty, Vector("notes.txt"), Vector.empty, "e" * 64)
  private val scope = take(RepositorySyncDirtyScopeAssessment.assessC(boundary,
    new RepositorySyncDirtyScopeAssessment.Port {
      def loadC(repositoryIdentity: String, snapshotDigest: String) =
        Consequence.success(Some(scopeEvidence))
    }))
  private val receipt = RepositorySyncFetchAssessment.Receipt(
    boundary.digest, scope.digest, "repository-1", "worktree-1", "main", "origin",
    "main", "remote-1", "f" * 40, Some("9" * 40), "f" * 40,
    snapshot.headOid, snapshot.headOid,
    snapshot.treeDigest, snapshot.indexDigest, snapshot.worktreeDigest,
    "refs/remotes/origin/main", Vector("refs/remotes/origin/main"),
    endpointPolicyAccepted = true,
    fetchCompleted = true, executionDigest = "1" * 64)

  private def assess(value: Option[RepositorySyncFetchAssessment.Receipt]) =
    RepositorySyncFetchAssessment.assessC(boundary, scope,
      new RepositorySyncFetchAssessment.Port {
        def fetchC(repositoryIdentity: String, boundaryDigest: String,
          dirtyScopeDigest: String) = Consequence.success(value)
      })

  "RepositorySync fetch admission" should {
    "accept only a completed fetch of the configured tracking ref" in {
      val admitted = take(assess(Some(receipt)))
      admitted.receipt.fetchedTipOid shouldBe "f" * 40
      admitted.digest.matches("[0-9a-f]{64}") shouldBe true
      take(assess(Some(receipt))) shouldBe admitted
    }

    "reject a missing or failed fetch and any different remote boundary" in {
      for (invalid <- Vector(
          None,
          Some(receipt.copy(boundaryDigest = "0" * 64)),
          Some(receipt.copy(dirtyScopeDigest = "0" * 64)),
          Some(receipt.copy(repositoryIdentity = "other-repository")),
          Some(receipt.copy(worktreeIdentity = "other-worktree")),
          Some(receipt.copy(trackingRemote = "other")),
          Some(receipt.copy(trackingBranch = "release")),
          Some(receipt.copy(remoteEndpointIdentity = "other-endpoint")),
          Some(receipt.copy(targetedTrackingRef = "refs/remotes/origin/other")),
          Some(receipt.copy(changedRefs = Vector("refs/remotes/origin/other"))),
          Some(receipt.copy(changedRefs = Vector(
            "refs/remotes/origin/main", "refs/remotes/origin/other"))),
          Some(receipt.copy(changedRefs = Vector.empty)),
          Some(receipt.copy(trackingTipAfter = "9" * 40)),
          Some(receipt.copy(endpointPolicyAccepted = false)),
          Some(receipt.copy(fetchCompleted = false)),
          Some(receipt.copy(executionDigest = "bad"))))
        assess(invalid) shouldBe a[Consequence.Failure[?]]
    }

    "reject local branch or worktree mutation during fetch" in {
      for (invalid <- Vector(
          receipt.copy(localBranch = "release"),
          receipt.copy(localHeadBefore = "2" * 40),
          receipt.copy(localHeadAfter = "2" * 40),
          receipt.copy(treeDigestAfter = "2" * 64),
          receipt.copy(indexDigestAfter = "2" * 64),
          receipt.copy(worktreeDigestAfter = "2" * 64),
          receipt.copy(fetchedTipOid = "bad")))
        assess(Some(invalid)) shouldBe a[Consequence.Failure[?]]
    }

    "bind the admitted digest to the fetched tip and execution evidence" in {
      take(assess(Some(receipt.copy(fetchedTipOid = "3" * 40,
        trackingTipAfter = "3" * 40)))).digest should not be
        take(assess(Some(receipt))).digest
      take(assess(Some(receipt.copy(executionDigest = "4" * 64)))).digest should not be
        take(assess(Some(receipt))).digest
    }

    "admit a no-op fetch only when the tracking ref already points at the fetched tip" in {
      take(assess(Some(receipt.copy(trackingTipBefore = Some(receipt.fetchedTipOid),
        changedRefs = Vector.empty)))).receipt.changedRefs shouldBe Vector.empty
    }
  }
}
