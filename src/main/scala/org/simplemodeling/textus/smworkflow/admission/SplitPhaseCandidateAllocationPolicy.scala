package org.simplemodeling.textus.smworkflow.admission

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowMechanicalTransition as M
import org.goldenport.configuration.{ConfigurationValue, ResolvedConfiguration}
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitPlanningAdvance as W,
  SplitPhaseExplicitCandidateProjection as Q, SplitPhaseExplicitProposalPrecheck as P,
  SplitPhaseExplicitBoundaryValidation as B, SplitPhaseExplicitPartitionSemantic as S}

/**
 * Explicit host policy, not an execution grant or a recovery naming guess.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object SplitPhaseCandidateAllocationPolicy {
  val prefix = "sm-workflow.split-phase.candidate-allocation."
  final class Policy private[SplitPhaseCandidateAllocationPolicy] (
    val identity: String, val revision: String, val namespace: String) {

    def allocateC(need: W.AllocationRequired, result: S.ResultReference): Consequence[Allocated] =
      if (need == null || !_result(result))
        Consequence.stateConflict("SplitPhase candidate allocation requires its constructed need and canonical result")
      else {
        val qualified = "sm-split:" + namespace + ":policy:" + _encode(identity) + "@" + _encode(revision) +
          ":result:" + _encode(result.identity) + "@" + _encode(result.revision)
        for {
          data <- _data_c(need, qualified)
          allocated <- _admit_c(need, result, data)
        } yield allocated
      }

    def allocateMechanicalC(need: W.AllocationRequired, anchor: M.Reference): Consequence[MechanicalAllocated] =
      if (need == null) Consequence.stateConflict("SplitPhase mechanical allocation requires its constructed need")
      else for {
        _ <- _anchor_c(anchor)
        qualified = "sm-split:" + namespace + ":policy:" + _encode(identity) + "@" + _encode(revision) +
          ":mechanical:" + _encode(anchor.identity) + "@" + _encode(anchor.revision)
        data <- _data_c(need, qualified)
        allocated <- recoverMechanicalC(need, anchor, data)
      } yield allocated

    private def _data_c(need: W.AllocationRequired, qualified: String): Consequence[W.Data] = {
        val original = need.selection.data
        val records = Q.Records(Q.AllocationReference(qualified + ":allocation", "1"), original.source, original.authority,
          need.enumerated.candidates.zipWithIndex.map { (candidate, ordinal) =>
            val base = qualified + ":candidate:" + ordinal.toString
            Q.Allocation(candidate.cutAfterIndices, P.ProposalReference(base + ":proposal", "1"),
              B.OwnershipReference(base + ":ownership", "1"), B.HandoffPlanReference(base + ":handoffs", "1"))
          })
        val data = original.copy(reference = W.DataReference(qualified + ":planning", "1"), allocations = Some(records))
        for {
          // Existing whole-row storage bounds apply before any effect is staged.
          _ <- SplitPhaseExplicitPlanningInputJsonV1.encodePlanningC(data).map(_ => ())
        } yield data
    }
  }

  final class Allocated private[SplitPhaseCandidateAllocationPolicy] (
    val need: W.AllocationRequired, val result: S.ResultReference,
    val data: W.Data, val projection: Q.Input)

  final class MechanicalAllocated private[SplitPhaseCandidateAllocationPolicy] (
    val need: W.AllocationRequired, val anchor: M.Reference,
    val data: W.Data, val projection: Q.Input)

  def decodeC(host: ResolvedConfiguration): Consequence[Policy] =
    if (host == null) Consequence.configurationInvalid("SplitPhase candidate allocation policy is absent")
    else for {
      identity <- _string_c(host, "policy-identity", 128)
      revision <- _string_c(host, "policy-revision", 128)
      namespace <- _string_c(host, "namespace", 64)
      _ <- if (namespace.matches("[A-Za-z][A-Za-z0-9._-]{0,63}")) Consequence.unit
        else Consequence.configurationInvalid("SplitPhase candidate allocation namespace is not canonical")
    } yield new Policy(identity, revision, namespace)

  /** Natural raw-data recovery boundary. No current Policy, allocation recipe,
    * newest-row lookup or regenerated identity participates in reconstruction.
    */
  def recoverC(need: W.AllocationRequired, result: S.ResultReference, data: W.Data): Consequence[Allocated] =
    _admit_c(need, result, data)

  def recoverMechanicalC(need: W.AllocationRequired, anchor: M.Reference, data: W.Data): Consequence[MechanicalAllocated] =
    for {
      _ <- _anchor_c(anchor)
      input <- _projection_c(need, data)
    } yield new MechanicalAllocated(need, anchor, data, input)

  private def _anchor_c(anchor: M.Reference): Consequence[Unit] =
    if (anchor == null || !_reference(anchor.identity, anchor.revision))
      Consequence.stateConflict("SplitPhase mechanical allocation requires a canonical checkpoint anchor")
    else M.keyC(anchor).map(_ => ())

  private def _admit_c(need: W.AllocationRequired, result: S.ResultReference, data: W.Data): Consequence[Allocated] =
    if (!_result(result)) Consequence.stateConflict("SplitPhase candidate allocation requires a canonical result")
    else _projection_c(need, data).map(input => new Allocated(need, result, data, input))

  private def _projection_c(need: W.AllocationRequired, data: W.Data): Consequence[Q.Input] =
    if (need == null || data == null || data.reference == null ||
        !_reference(data.reference.identity, data.reference.revision) || data.allocations == null || data.allocations.isEmpty)
      Consequence.stateConflict("SplitPhase candidate allocation dataset is incomplete")
    else {
      val original = need.selection.data
      val records = data.allocations.get
      val references = Option(records).toVector.flatMap(r => Option(r.allocations).toVector.flatten.flatMap(a =>
        if (a == null) Vector.empty else Vector(Option(a.proposal).map(r => (r.identity, r.revision)),
          Option(a.ownership).map(r => (r.identity, r.revision)), Option(a.handoffs).map(r => (r.identity, r.revision))).flatten))
      if (data.reference.identity == original.reference.identity ||
          data != original.copy(reference = data.reference, allocations = data.allocations) || records == null ||
          records.reference == null || !_reference(records.reference.identity, records.reference.revision) ||
          !references.forall((identity, revision) => _reference(identity, revision)))
        Consequence.stateConflict("SplitPhase candidate allocation changes original facts or has unsupported references")
      else Q.loadC(need.enumerated, records)
    }

  private def _string_c(host: ResolvedConfiguration, key: String, limit: Int): Consequence[String] =
    GoalPhaseHostRecordReadPolicy.storedValue(host.configuration.values, prefix + key) match {
      case Some(ConfigurationValue.StringValue(value)) if _name(value, limit) => Consequence.success(value)
      case _ => Consequence.configurationInvalid("SplitPhase candidate allocation requires exact host policy metadata")
    }
  private def _encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
  private def _result(value: S.ResultReference): Boolean = value != null && _reference(value.identity, value.revision)
  private def _reference(identity: String, revision: String): Boolean = _name(identity, 1024) && _name(revision, 1024)
  private def _name(value: String, limit: Int): Boolean = value != null && value.nonEmpty && value.length <= limit &&
    value.trim == value && !value.exists(c => Character.isISOControl(c))
}
