package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value.RepositorySyncStartInput
import RepositorySyncConvergenceAssessment.{Verification as ConvergenceVerification}

/**
 * @since   Sep. 28, 2026
 * @version Oct.  8, 2026
 */
final class RepositorySyncConvergenceAssessmentSpec extends AnyWordSpec with Matchers {
  import RepositorySyncRelationAssessment.*
  import RepositorySyncFastForwardAssessment.{FrozenState, State, Receipt as FastForwardReceipt, Port as FastForwardPort}

  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"RepositorySync convergence fixture failed: $failure")
  }

  private final class Fixture(dirty: Boolean = false, equal: Boolean = false,
    oidWidth: Int = 40, localAhead: Boolean = false, trackingUpdated: Boolean = false) {
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
      Vector(if (equal || localAhead) fetchedTip else snapshot.headOid),
      if (localAhead) 3L else if (equal) 0L else if (dirty) 1L else 0L,
      if (equal || localAhead) 0L else 3L,
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
    def assessFastForward() = RepositorySyncFastForwardAssessment.assessC(boundary, scope, fetch,
      preservation, relation, fastForwards)
    val admittedForward = if (!dirty && !equal && !localAhead) Some(take(assessFastForward())) else None
    var forwardAssessment = admittedForward
    val admittedPush = if (localAhead) Some(take(RepositorySyncPushAssessment.assessC(
      boundary, scope, fetch, preservation, relation, new RepositorySyncPushAssessment.Port {
        def loadFrozenC(repositoryIdentity: String, relationDigest: String,
          sourceTipOid: String, destinationTipBeforeOid: String) =
          Consequence.success(Some(RepositorySyncPushAssessment.FrozenState(before, fetchedTip,
            remoteAncestorOfLocal = true, historyComplete = true, replacementHistory = false,
            graphInspectionDigest = "c" * 64,
            authority = RepositorySyncPushAssessment.Authority(relationDigest, "repository-1", "worktree-1",
              "main", "origin", "main", "remote-1", "policy-v1", localTip, fetchedTip,
              nonForceOnlyAccepted = true, remoteWriteAuthorityAccepted = true, evidenceDigest = "d" * 64))))
        def loadReceiptC(repositoryIdentity: String, frozenStateDigest: String) =
          Consequence.success(Some(RepositorySyncPushAssessment.Receipt(frozenStateDigest,
            "refs/heads/main", "refs/heads/main", localTip, fetchedTip, localTip,
            if (trackingUpdated) localTip else fetchedTip, Vector("refs/heads/main"),
            if (trackingUpdated) Vector("refs/remotes/origin/main") else Vector.empty,
            forceUsed = false, tagsIncluded = false, mirrorUsed = false,
            endpointPolicyAccepted = true, completed = true, executionDigest = "e" * 64)))
        def loadPostStateC(repositoryIdentity: String, receiptDigest: String) =
          Consequence.success(Some(before.copy(evidenceBinding = receiptDigest,
            trackingTipOid = if (trackingUpdated) localTip else fetchedTip, inspectionDigest = "f" * 64)))
      }))) else None
    var pushAssessment = admittedPush
    val convergenceState = admittedPush.map(value => value.after.copy(evidenceBinding = value.digest))
      .orElse(admittedForward.map(value => value.after.copy(evidenceBinding = value.digest)))
      .getOrElse(before.copy(evidenceBinding = relation.digest))
    val verificationFetch = RepositorySyncConvergenceAssessment.FetchReceipt(convergenceState,
      convergenceState.localHeadOid, convergenceState.localHeadOid, convergenceState.localHeadOid,
      convergenceState.localTreeOid, convergenceState.localTreeDigest, "refs/heads/main",
      "refs/remotes/origin/main",
      if (convergenceState.trackingTipOid == convergenceState.localHeadOid) Vector.empty
      else Vector("refs/remotes/origin/main"), endpointPolicyAccepted = true,
      completed = true, executionDigest = "1" * 64)
    var verificationFetchValue: Option[RepositorySyncConvergenceAssessment.FetchReceipt] = Some(verificationFetch)
    var verificationTransform: ConvergenceVerification => ConvergenceVerification = identity
    var verificationMissing = false
    var convergenceFailureStage = ""
    var verificationFetchLoads = 0
    var verificationLoads = 0
    val convergencePort = new RepositorySyncConvergenceAssessment.Port {
      def loadFetchC(repositoryIdentity: String, predecessorDigest: String,
        targetTipOid: String): Consequence[Option[RepositorySyncConvergenceAssessment.FetchReceipt]] = {
        repositoryIdentity shouldBe input.repositoryIdentity
        predecessorDigest shouldBe convergenceState.evidenceBinding
        targetTipOid shouldBe convergenceState.localHeadOid
        verificationFetchLoads += 1
        if (convergenceFailureStage == "fetch") Consequence.stateConflict("host verification-fetch load failed")
        else Consequence.success(verificationFetchValue)
      }
      def loadVerificationC(repositoryIdentity: String,
        verificationFetchDigest: String): Consequence[Option[RepositorySyncConvergenceAssessment.Verification]] = {
        repositoryIdentity shouldBe input.repositoryIdentity
        verificationLoads += 1
        if (convergenceFailureStage == "verification") Consequence.stateConflict("host convergence load failed")
        else Consequence.success(if (verificationMissing) None else Some(verificationTransform(
          RepositorySyncConvergenceAssessment.Verification(
            convergenceState.copy(evidenceBinding = verificationFetchDigest,
              trackingTipOid = convergenceState.localHeadOid, inspectionDigest = "2" * 64),
            0L, 0L, historyComplete = true, replacementHistory = false,
            graphInspectionDigest = "3" * 64))))
      }
    }
    def assessConvergence() = RepositorySyncConvergenceAssessment.assessC(boundary, scope, fetch,
      preservation, relation, forwardAssessment, convergencePort, pushAssessment)
  }

  "RepositorySync convergence evidence" should {
    "verify an admitted push with retained or updated tracking refs, checkpoint trees and either OID width" in {
      for (dirty <- Vector(false, true); updated <- Vector(false, true); width <- Vector(40, 64)) {
        val fixture = new Fixture(dirty = dirty, localAhead = true, trackingUpdated = updated, oidWidth = width)
        val admitted = take(fixture.assessConvergence())
        admitted.route shouldBe RepositorySyncConvergenceAssessment.Route.Push
        admitted.predecessorDigest shouldBe fixture.admittedPush.get.digest
        admitted.verification.state.localHeadOid shouldBe fixture.localTip
        admitted.verification.state.trackingTipOid shouldBe fixture.localTip
        admitted.verification.state.localTreeOid shouldBe fixture.preservation.cleanState.localTreeOid
        take(fixture.assessConvergence()) shouldBe admitted
        fixture.fetchLoads shouldBe 1
      }
    }

    "re-admit the complete push evidence and refuse mismatched or multiple predecessors before verification loads" in {
      val fixture = new Fixture(localAhead = true)
      val push = fixture.admittedPush.get
      val invalid = Vector(None, Some(null), Some(push.copy(digest = "0" * 64)),
        Some(push.copy(frozen = null)), Some(push.copy(receipt = null)), Some(push.copy(after = null)),
        Some(push.copy(frozen = push.frozen.copy(state = null))),
        Some(push.copy(frozen = push.frozen.copy(state = push.frozen.state.copy(
          authority = push.frozen.state.authority.copy(remoteWriteAuthorityAccepted = false))))),
        Some(push.copy(receipt = push.receipt.copy(forceUsed = true))),
        Some(push.copy(after = push.after.copy(trackingTipOid = fixture.localTip))))
      invalid.foreach { value =>
        fixture.pushAssessment = value
        fixture.assessConvergence() shouldBe a[Consequence.Failure[?]]
      }
      fixture.pushAssessment = null
      fixture.assessConvergence() shouldBe a[Consequence.Failure[?]]
      fixture.pushAssessment = Some(push)
      fixture.forwardAssessment = (new Fixture).admittedForward
      fixture.assessConvergence() shouldBe a[Consequence.Failure[?]]
      fixture.verificationFetchLoads shouldBe 0
      fixture.verificationLoads shouldBe 0
      val equal = new Fixture(equal = true)
      equal.pushAssessment = Some(push)
      equal.assessConvergence() shouldBe a[Consequence.Failure[?]]
      equal.verificationFetchLoads shouldBe 0
    }

    "require the reported tracking update and reject remote advance, local drift and nonzero push verification" in {
      val transforms: Vector[RepositorySyncConvergenceAssessment.FetchReceipt => RepositorySyncConvergenceAssessment.FetchReceipt] =
        Vector(_.copy(changedRefs = Vector.empty), _.copy(changedRefs = Vector("refs/heads/main")),
          _.copy(fetchedTipOid = "0" * 40),
          value => value.copy(before = value.before.copy(trackingTipOid = value.before.localHeadOid)),
          _.copy(localTipAfterOid = "0" * 40), _.copy(localTreeAfterDigest = "0" * 64))
      transforms.foreach { transform =>
        val fixture = new Fixture(localAhead = true)
        fixture.verificationFetchValue = Some(transform(fixture.verificationFetch))
        fixture.assessConvergence() shouldBe a[Consequence.Failure[?]]
        fixture.verificationLoads shouldBe 0
      }
      val fixture = new Fixture(localAhead = true)
      fixture.verificationTransform = _.copy(localAhead = 1L)
      fixture.assessConvergence() shouldBe a[Consequence.Failure[?]]
      fixture.verificationTransform = value => value.copy(state = value.state.copy(trackingTipOid = fixture.fetchedTip))
      fixture.assessConvergence() shouldBe a[Consequence.Failure[?]]
    }

    "admit exact equal and fast-forward paths only after verification fetch and a clean 0/0 receipt" in {
      Vector(new Fixture(equal = true), new Fixture).foreach { fixture =>
        val admitted = take(fixture.assessConvergence())
        admitted.route shouldBe (if (fixture.admittedForward.isDefined)
          RepositorySyncConvergenceAssessment.Route.FastForward else RepositorySyncConvergenceAssessment.Route.Equal)
        admitted.predecessorDigest shouldBe fixture.convergenceState.evidenceBinding
        admitted.verification.state.localHeadOid shouldBe fixture.fetchedTip
        admitted.verification.state.trackingTipOid shouldBe fixture.fetchedTip
        admitted.verification.localAhead shouldBe 0L
        admitted.verification.remoteAhead shouldBe 0L
        admitted.digest should fullyMatch regex "[0-9a-f]{64}"
        take(fixture.assessConvergence()) shouldBe admitted
        fixture.verificationFetchLoads shouldBe 2
        fixture.verificationLoads shouldBe 2
        fixture.snapshotLoads shouldBe 1
        fixture.fetchLoads shouldBe 1
        fixture.loads shouldBe 1
      }
    }

    "reject local-ahead, diverged and mismatched route evidence before convergence loads" in {
      val localAhead = new Fixture
      localAhead.receiptValue = Some(localAhead.baseline.copy(mergeBases = Vector(localAhead.fetchedTip),
        localAhead = 2L, remoteAhead = 0L))
      localAhead.relation = take(localAhead.assessRelation())
      val diverged = new Fixture(dirty = true)
      val missingForward = new Fixture
      missingForward.forwardAssessment = None
      val extraneousForward = new Fixture(equal = true)
      extraneousForward.forwardAssessment = (new Fixture).admittedForward
      Vector(localAhead, diverged, missingForward, extraneousForward).foreach { fixture =>
        fixture.assessConvergence() shouldBe a[Consequence.Failure[?]]
        fixture.verificationFetchLoads shouldBe 0
        fixture.verificationLoads shouldBe 0
      }
    }

    "re-admit upstream digests, relation and full fast-forward evidence before host loads" in {
      val fixture = new Fixture
      val forward = fixture.admittedForward.get
      def assess(boundary: RepositorySyncBoundaryAssessment.Assessment = fixture.boundary,
        scope: RepositorySyncDirtyScopeAssessment.Assessment = fixture.scope,
        fetch: RepositorySyncFetchAssessment.Assessment = fixture.fetch,
        preservation: RepositorySyncCheckpointAssessment.Assessment = fixture.preservation,
        relation: Assessment = fixture.relation,
        fastForward: Option[RepositorySyncFastForwardAssessment.Assessment] = fixture.admittedForward,
        port: RepositorySyncConvergenceAssessment.Port = fixture.convergencePort) =
        RepositorySyncConvergenceAssessment.assessC(boundary, scope, fetch, preservation, relation, fastForward, port)
      val outcomes = Vector(
        assess(boundary = fixture.boundary.copy(digest = "0" * 64)),
        assess(scope = fixture.scope.copy(digest = "0" * 64)),
        assess(fetch = fixture.fetch.copy(digest = "0" * 64)),
        assess(preservation = fixture.preservation.copy(digest = "0" * 64)),
        assess(relation = fixture.relation.copy(digest = "0" * 64)),
        assess(relation = fixture.relation.copy(relation = Relation.Equal)),
        assess(fastForward = Some(forward.copy(digest = "0" * 64))),
        assess(fastForward = Some(forward.copy(after = forward.after.copy(inspectionDigest = "0" * 64)))),
        assess(fastForward = Some(forward.copy(frozen = forward.frozen.copy(
          state = forward.frozen.state.copy(targetTreeOid = "0" * 40))))),
        assess(fastForward = Some(forward.copy(receipt = forward.receipt.copy(resultTipOid = "0" * 40)))),
        assess(boundary = null), assess(scope = null), assess(fetch = null), assess(preservation = null),
        assess(relation = null), assess(relation = fixture.relation.copy(receipt = null)),
        assess(fastForward = null), assess(fastForward = Some(null)),
        assess(fastForward = Some(forward.copy(frozen = null))),
        assess(fastForward = Some(forward.copy(receipt = null))),
        assess(fastForward = Some(forward.copy(after = null))), assess(port = null))
      outcomes.foreach(_ shouldBe a[Consequence.Failure[?]])
      fixture.verificationFetchLoads shouldBe 0
      fixture.verificationLoads shouldBe 0
    }

    "reject pre-verification identity, mapping, tip, tree and clean-gate drift" in {
      stateDrifts.foreach { transform =>
        Vector(new Fixture(equal = true), new Fixture).foreach { fixture =>
          fixture.verificationFetchValue = Some(fixture.verificationFetch.copy(
            before = transform(fixture.convergenceState)))
          fixture.assessConvergence() shouldBe a[Consequence.Failure[?]]
          fixture.verificationLoads shouldBe 0
        }
      }
    }

    "reject wrong-target, local mutation, extra-ref and incomplete verification fetch receipts" in {
      import RepositorySyncConvergenceAssessment.FetchReceipt
      val transforms: Vector[FetchReceipt => FetchReceipt] = Vector(
        _.copy(before = null), _.copy(fetchedTipOid = "0" * 40),
        _.copy(trackingTipAfterOid = "0" * 40), _.copy(localTipAfterOid = "0" * 40),
        _.copy(localTreeAfterOid = "0" * 40), _.copy(localTreeAfterDigest = "0" * 64),
        _.copy(remoteRef = "refs/heads/release"), _.copy(targetedTrackingRef = "refs/remotes/upstream/main"),
        _.copy(changedRefs = Vector("refs/heads/main")),
        _.copy(changedRefs = Vector("refs/remotes/origin/main", "refs/heads/main")),
        _.copy(changedRefs = Vector("refs/remotes/origin/main", "refs/remotes/origin/main")),
        _.copy(changedRefs = null), _.copy(endpointPolicyAccepted = false),
        _.copy(completed = false), _.copy(executionDigest = "bad"))
      transforms.foreach { transform =>
        val fixture = new Fixture
        fixture.verificationFetchValue = Some(transform(fixture.verificationFetch))
        fixture.assessConvergence() shouldBe a[Consequence.Failure[?]]
        fixture.verificationLoads shouldBe 0
      }
    }

    "reject post-fetch state drift, nonzero counts and incomplete or replacement graph inspection" in {
      import RepositorySyncConvergenceAssessment.Verification
      val graphDrifts: Vector[Verification => Verification] = Vector(
        _.copy(state = null), _.copy(localAhead = 1L), _.copy(remoteAhead = 1L),
        _.copy(localAhead = -1L), _.copy(remoteAhead = -1L), _.copy(historyComplete = false),
        _.copy(replacementHistory = true), _.copy(graphInspectionDigest = "bad"))
      val transforms = stateDrifts.map(transform =>
        (value: Verification) => value.copy(state = transform(value.state))) ++ graphDrifts
      transforms.foreach { transform =>
        val fixture = new Fixture
        fixture.verificationTransform = transform
        fixture.assessConvergence() shouldBe a[Consequence.Failure[?]]
        fixture.verificationLoads shouldBe 1
      }
    }

    "stop on missing or null retained records rather than inventing convergence" in {
      val fetch = new Fixture
      Vector(None, Some(null)).foreach { value =>
        fetch.verificationFetchValue = value
        fetch.assessConvergence() shouldBe a[Consequence.Failure[?]]
      }
      fetch.verificationLoads shouldBe 0
      val verification = new Fixture
      verification.verificationMissing = true
      verification.assessConvergence() shouldBe a[Consequence.Failure[?]]
      verification.verificationMissing = false
      verification.verificationTransform = _ => null
      verification.assessConvergence() shouldBe a[Consequence.Failure[?]]
    }

    "propagate host-load failures and retry the same retained evidence without an implicit fetch" in {
      Vector("fetch", "verification").foreach { stage =>
        val fixture = new Fixture
        fixture.convergenceFailureStage = stage
        fixture.assessConvergence() shouldBe a[Consequence.Failure[?]]
        if (stage == "fetch") fixture.verificationLoads shouldBe 0
        fixture.convergenceFailureStage = ""
        val admitted = take(fixture.assessConvergence())
        take(fixture.assessConvergence()) shouldBe admitted
        fixture.fetchLoads shouldBe 1
        fixture.snapshotLoads shouldBe 1
      }
    }

    "bind verification pre-state, fetch execution, graph and post-state inspection into the digest" in {
      val fixture = new Fixture
      val initial = take(fixture.assessConvergence())
      fixture.verificationFetchValue = Some(fixture.verificationFetch.copy(
        before = fixture.convergenceState.copy(inspectionDigest = "0" * 64)))
      take(fixture.assessConvergence()).digest should not be initial.digest
      fixture.verificationFetchValue = Some(fixture.verificationFetch.copy(executionDigest = "0" * 64))
      take(fixture.assessConvergence()).digest should not be initial.digest
      fixture.verificationFetchValue = Some(fixture.verificationFetch.copy(
        changedRefs = Vector("refs/remotes/origin/main")))
      take(fixture.assessConvergence()).digest should not be initial.digest
      fixture.verificationFetchValue = Some(fixture.verificationFetch)
      fixture.verificationTransform = _.copy(graphInspectionDigest = "0" * 64)
      take(fixture.assessConvergence()).digest should not be initial.digest
      fixture.verificationTransform = value => value.copy(state = value.state.copy(inspectionDigest = "0" * 64))
      take(fixture.assessConvergence()).digest should not be initial.digest
      fixture.verificationTransform = identity
      take(fixture.assessConvergence()) shouldBe initial
    }

    "admit consistent SHA-256 commit and tree identities on both routes" in {
      Vector(new Fixture(equal = true, oidWidth = 64), new Fixture(oidWidth = 64)).foreach { fixture =>
        val admitted = take(fixture.assessConvergence())
        admitted.verification.state.localHeadOid.length shouldBe 64
        admitted.verification.state.localTreeOid.length shouldBe 64
        take(fixture.assessConvergence()) shouldBe admitted
      }
    }

    "verify an equal checkpoint tip without discarding the preserved local state" in {
      val fixture = new Fixture(dirty = true, equal = true)
      val admitted = take(fixture.assessConvergence())
      fixture.preservation.checkpoint.isDefined shouldBe true
      admitted.route shouldBe RepositorySyncConvergenceAssessment.Route.Equal
      admitted.verification.state.localHeadOid shouldBe fixture.localTip
      admitted.verification.state.localTreeOid shouldBe fixture.checkpointFrozen.completeTreeOid
      fixture.localTip should not be fixture.snapshot.headOid
      fixture.admittedForward shouldBe None
    }

    "refuse a newly advanced remote even if the host reports equal new tips and 0/0" in {
      val fixture = new Fixture
      val advanced = "0" * 40
      fixture.verificationFetchValue = Some(fixture.verificationFetch.copy(
        fetchedTipOid = advanced, trackingTipAfterOid = advanced))
      fixture.verificationTransform = value => value.copy(state = value.state.copy(
        localHeadOid = advanced, trackingTipOid = advanced))
      fixture.assessConvergence() shouldBe a[Consequence.Failure[?]]
      fixture.verificationLoads shouldBe 0
      fixture.fetchLoads shouldBe 1
      fixture.verificationFetchValue = Some(fixture.verificationFetch)
      fixture.verificationTransform = identity
      take(fixture.assessConvergence())
      fixture.fetchLoads shouldBe 1
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
