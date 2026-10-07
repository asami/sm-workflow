package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.goldenport.Consequence

/** Selects declared product checks from an attested merge delta and admits their
  * retained receipts. It never executes a test, Git operation or review.
  * @since   Sep. 28, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositorySyncMergeValidationAssessment {
  enum ChangeKind {
    case Documentation, Metadata, LocalSource, LocalBuild, LocalSpec, Runtime, PublicContract
  }
  enum SelectionMode { case NoProductTest, Localized, Broad }

  final case class Context(
    boundary: RepositorySyncBoundaryAssessment.Assessment,
    scope: RepositorySyncDirtyScopeAssessment.Assessment,
    fetch: RepositorySyncFetchAssessment.Assessment,
    preservation: RepositorySyncCheckpointAssessment.Assessment,
    relation: RepositorySyncRelationAssessment.Assessment,
    merge: RepositorySyncMergeAssessment.Assessment
  )
  final case class Change(path: String, kind: ChangeKind, affectedContracts: Vector[String])
  final case class Delta(
    mergeDigest: String,
    parentTips: Vector[String],
    resultTreeOid: String,
    resultTreeDigest: String,
    changes: Vector[Change],
    inspectionDigest: String
  )
  /** Lower rank is the repository's explicit narrower-check preference.
    * Paths are declared coverage, not extension-based test inference.
    */
  final case class LocalCheck(identity: String, coveredPaths: Vector[String], rank: Long)
  final case class Policy(
    deltaDigest: String,
    repositoryPolicyVersion: String,
    validationPolicyVersion: String,
    localizedChecks: Vector[LocalCheck],
    broadChecks: Vector[String],
    reviewPolicyVersion: String,
    inspectionDigest: String
  )
  final case class Plan(context: Context, delta: Delta, policy: Policy,
    mode: SelectionMode, selectedChecks: Vector[String], digest: String) {
    def affectedContracts: Vector[String] = delta.changes.flatMap(_.affectedContracts).distinct.sorted
  }
  final case class CheckReceipt(identity: String, successful: Boolean,
    completed: Boolean, executionDigest: String)
  final case class ValidationEvidence(
    planDigest: String,
    pending: RepositorySyncMergeAssessment.PendingState,
    checks: Vector[CheckReceipt],
    completed: Boolean,
    inspectionDigest: String
  )
  final case class Assessment(plan: Plan, evidence: ValidationEvidence, digest: String)
  trait Port {
    def loadDeltaC(repositoryIdentity: String, mergeDigest: String,
      parentTips: Vector[String], resultTreeOid: String): Consequence[Option[Delta]]
    def loadPolicyC(repositoryIdentity: String, repositoryPolicyVersion: String,
      deltaDigest: String): Consequence[Option[Policy]]
    def loadValidationC(repositoryIdentity: String, planDigest: String): Consequence[Option[ValidationEvidence]]
  }

  def planC(context: Context, port: Port): Consequence[Plan] =
    if (context == null || context.merge == null || context.merge.frozen == null || port == null)
      Consequence.stateConflict("RepositorySync merge validation context is missing")
    else for {
      admitted <- _readmitMergeC(context)
      merge = admitted.merge
      before = merge.frozen.state.before
      loadedDelta <- port.loadDeltaC(before.repositoryIdentity, merge.digest,
        merge.frozen.state.parentTips, merge.pending.resultTreeOid)
      delta <- loadedDelta match {
        case Some(value) if value != null && _validDelta(merge, value) => Consequence.success(value)
        case _ => Consequence.stateConflict("RepositorySync validation delta is not the complete exact merge delta")
      }
      deltaDigest = _deltaDigest(delta)
      loadedPolicy <- port.loadPolicyC(before.repositoryIdentity, before.repositoryPolicyVersion, deltaDigest)
      policy <- loadedPolicy match {
        case Some(value) if value != null && _validPolicy(before.repositoryPolicyVersion, deltaDigest, value) =>
          Consequence.success(value)
        case _ => Consequence.stateConflict("RepositorySync validation policy is missing, ambiguous or incompatible")
      }
      selected <- _selectC(delta, policy)
    } yield Plan(admitted, delta, policy, selected._1, selected._2,
      digest(Vector("repository-sync-merge-validation-plan-v1", merge.digest, deltaDigest,
        _policyDigest(policy), selected._1.toString, selected._2.size.toString) ++ selected._2))

  def assessC(context: Context, port: Port): Consequence[Assessment] = for {
    plan <- planC(context, port)
    loaded <- port.loadValidationC(plan.context.merge.frozen.state.before.repositoryIdentity, plan.digest)
    evidence <- loaded match {
      case Some(value) if value != null && _validEvidence(plan, value) => Consequence.success(value)
      case _ => Consequence.stateConflict("RepositorySync product validation is incomplete, unsuccessful or changed the merge")
    }
  } yield Assessment(plan, evidence, digest(Vector("repository-sync-merge-validated-v1",
    plan.digest, evidence.planDigest, evidence.completed.toString, evidence.inspectionDigest,
    evidence.checks.size.toString) ++ evidence.checks.flatMap(check =>
      Vector(check.identity, check.successful.toString, check.completed.toString, check.executionDigest))))

  /** External case-class copies cannot bypass the original admission chain. */
  def readmitC(value: Assessment): Consequence[Assessment] =
    if (value == null || value.plan == null)
      Consequence.stateConflict("RepositorySync retained validation assessment is missing")
    else assessC(value.plan.context, new Port {
      def loadDeltaC(repositoryIdentity: String, mergeDigest: String,
        parentTips: Vector[String], resultTreeOid: String) = Consequence.success(Some(value.plan.delta))
      def loadPolicyC(repositoryIdentity: String, repositoryPolicyVersion: String,
        deltaDigest: String) = Consequence.success(Some(value.plan.policy))
      def loadValidationC(repositoryIdentity: String, planDigest: String) = Consequence.success(Some(value.evidence))
    }).flatMap(admitted => if (admitted == value) Consequence.success(admitted)
      else Consequence.stateConflict("RepositorySync retained validation digest or selection differs"))

  private def _readmitMergeC(context: Context): Consequence[Context] = {
    val retained = context.merge
    RepositorySyncMergeAssessment.assessC(context.boundary, context.scope, context.fetch,
      context.preservation, context.relation, new RepositorySyncMergeAssessment.Port {
        def loadFrozenC(repositoryIdentity: String, relationDigest: String,
          localTipOid: String, fetchedTipOid: String) = Consequence.success(Some(retained.frozen.state))
        def loadReceiptC(repositoryIdentity: String, frozenStateDigest: String) = Consequence.success(Some(retained.receipt))
        def loadPendingStateC(repositoryIdentity: String, receiptDigest: String) = Consequence.success(Some(retained.pending))
        def loadStructuralC(repositoryIdentity: String, pendingStateDigest: String) = Consequence.success(Some(retained.structural))
      }).flatMap(admitted => if (admitted == retained) Consequence.success(context)
        else Consequence.stateConflict("RepositorySync validation requires the exact admitted pending merge"))
  }

  private def _validDelta(merge: RepositorySyncMergeAssessment.Assessment, delta: Delta): Boolean =
    delta.mergeDigest == merge.digest && delta.parentTips == merge.frozen.state.parentTips &&
      delta.resultTreeOid == merge.pending.resultTreeOid && delta.resultTreeDigest == merge.pending.resultTreeDigest &&
      hash(delta.inspectionDigest) && delta.changes != null && delta.changes.forall(change =>
        change != null && path(change.path) && change.kind != null && names(change.affectedContracts)) &&
      delta.changes.map(_.path) == merge.receipt.changedPaths

  private def _validPolicy(repositoryVersion: String, deltaDigest: String, policy: Policy): Boolean =
    policy.deltaDigest == deltaDigest && policy.repositoryPolicyVersion == repositoryVersion &&
      name(policy.validationPolicyVersion) && name(policy.reviewPolicyVersion) && hash(policy.inspectionDigest) &&
      names(policy.broadChecks) && policy.localizedChecks != null && policy.localizedChecks.forall(check =>
        check != null && name(check.identity) && paths(check.coveredPaths) && check.coveredPaths.nonEmpty && check.rank >= 0L) &&
      policy.localizedChecks.map(_.identity) == policy.localizedChecks.map(_.identity).distinct.sorted &&
      policy.localizedChecks.map(_.rank).distinct.size == policy.localizedChecks.size

  private def _selectC(delta: Delta, policy: Policy): Consequence[(SelectionMode, Vector[String])] = {
    val broad = delta.changes.exists(change =>
      change.kind == ChangeKind.Runtime || change.kind == ChangeKind.PublicContract)
    val local = delta.changes.filter(change =>
      Set(ChangeKind.LocalSource, ChangeKind.LocalBuild, ChangeKind.LocalSpec).contains(change.kind)).map(_.path)
    if (broad && policy.broadChecks.nonEmpty)
      Consequence.success((SelectionMode.Broad, policy.broadChecks))
    else if (broad) Consequence.stateConflict("RepositorySync cross-cutting delta has no declared broad validation")
    else if (local.nonEmpty) policy.localizedChecks.filter(check => local.forall(check.coveredPaths.contains))
      .sortBy(_.rank).headOption match {
        case Some(check) => Consequence.success((SelectionMode.Localized, Vector(check.identity)))
        case None => Consequence.stateConflict("RepositorySync localized delta has no declared covering check")
      }
    else Consequence.success((SelectionMode.NoProductTest, Vector.empty))
  }

  private def _validEvidence(plan: Plan, evidence: ValidationEvidence): Boolean =
    evidence.planDigest == plan.digest && evidence.pending != null &&
      evidence.pending == plan.context.merge.pending && evidence.completed && hash(evidence.inspectionDigest) &&
      evidence.checks != null && evidence.checks.forall(check =>
        check != null && name(check.identity) && check.successful && check.completed && hash(check.executionDigest)) &&
      evidence.checks.map(_.identity) == plan.selectedChecks

  private def _deltaDigest(delta: Delta): String = digest(Vector("repository-sync-merge-delta-v1",
    delta.mergeDigest, delta.resultTreeOid, delta.resultTreeDigest, delta.inspectionDigest,
    delta.parentTips.size.toString, delta.changes.size.toString) ++ delta.parentTips ++
    delta.changes.flatMap(change => Vector(change.path, change.kind.toString,
      change.affectedContracts.size.toString) ++ change.affectedContracts))
  private def _policyDigest(policy: Policy): String = digest(Vector("repository-sync-merge-policy-v1",
    policy.deltaDigest, policy.repositoryPolicyVersion, policy.validationPolicyVersion,
    policy.reviewPolicyVersion, policy.inspectionDigest, policy.localizedChecks.size.toString,
    policy.broadChecks.size.toString) ++ policy.localizedChecks.flatMap(check =>
      Vector(check.identity, check.rank.toString, check.coveredPaths.size.toString) ++ check.coveredPaths) ++ policy.broadChecks)

  private[admission] def name(value: String): Boolean =
    value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
  private[admission] def names(values: Vector[String]): Boolean =
    values != null && values == values.distinct.sorted && values.forall(name)
  private[admission] def path(value: String): Boolean = value != null && value.nonEmpty && !value.startsWith("/") &&
    !value.contains('\\') && !value.exists(ch => Character.isISOControl(ch)) &&
    !value.split("/", -1).exists(part => part.isEmpty || part == "." || part == ".." || part.equalsIgnoreCase(".git"))
  private[admission] def paths(values: Vector[String]): Boolean =
    values != null && values == values.distinct.sorted && values.forall(path)
  private[admission] def hash(value: String): Boolean = value != null && value.matches("[0-9a-f]{64}")
  private[admission] def digest(values: Vector[String]): String = MessageDigest.getInstance("SHA-256")
    .digest(values.map(value => s"${value.length}:$value").mkString.getBytes(StandardCharsets.UTF_8))
    .map(byte => f"${byte & 0xff}%02x").mkString
}
