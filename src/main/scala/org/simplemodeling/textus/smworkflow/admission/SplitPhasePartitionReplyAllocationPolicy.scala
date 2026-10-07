package org.simplemodeling.textus.smworkflow.admission

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.goldenport.configuration.{ConfigurationValue, ResolvedConfiguration}
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseTypedStartPreparation as T,
  SplitPhaseExplicitPartitionSemantic as S, SplitPhaseExplicitPartitionComputation as E,
  SplitPhaseExplicitBoundaryValidation as B}

/**
 * Issue-time identity construction only; no grant, effect or recovery recipe.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object SplitPhasePartitionReplyAllocationPolicy {
  val prefix = "sm-workflow.split-phase.partition-reply."
  final class Policy private[SplitPhasePartitionReplyAllocationPolicy] (
    val identity: String, val revision: String, val namespace: String) {
    def allocateC(binding: T.PartitionBound): Consequence[Allocated] =
      if (binding == null) Consequence.stateConflict("SplitPhase reply requires constructed Start/context binding")
      else {
        val start = binding.start.record
        val context = binding.context.record
        val qualified = "sm-split-reply:" + namespace + ":policy:" + _encode(identity) + "@" + _encode(revision) +
          ":start:" + _encode(start.reference.identity) + "@" + _encode(start.reference.revision) +
          ":context:" + _encode(context.reference.identity) + "@" + _encode(context.reference.revision) +
          ":instance:" + _encode(context.owner.handle.instanceIdentity.value) +
          ":continuation:" + _encode(context.owner.continuation.value)
        val need = binding.context.semantic.pending
        val reply = S.Reply(S.ResultReference(qualified + ":result", "1"), S.ResolutionReferences(
          Option.when(need.items.nonEmpty)(E.EstimateReference(qualified + ":estimates", "1")),
          Option.when(need.afterItems.nonEmpty)(B.HandoffPlanReference(qualified + ":handoffs", "1"))))
        _admit_c(binding, reply)
      }
  }
  final class Allocated private[SplitPhasePartitionReplyAllocationPolicy] (
    val binding: T.PartitionBound, val reply: S.Reply)

  def decodeC(host: ResolvedConfiguration): Consequence[Policy] =
    if (host == null) Consequence.configurationInvalid("SplitPhase reply policy is absent")
    else for {
      identity <- _string_c(host, "policy-identity", 128)
      revision <- _string_c(host, "policy-revision", 128)
      namespace <- _string_c(host, "namespace", 64)
      _ <- if (namespace.matches("[A-Za-z][A-Za-z0-9._-]{0,63}")) Consequence.unit
        else Consequence.configurationInvalid("SplitPhase reply namespace is not canonical")
    } yield new Policy(identity, revision, namespace)

  /** Natural canonical issued-data boundary, without policy or allocation. */
  def observeIssuedC(binding: T.PartitionBound, reply: S.Reply): Consequence[Allocated] =
    _admit_c(binding, reply)

  private def _admit_c(binding: T.PartitionBound, reply: S.Reply): Consequence[Allocated] =
    if (binding == null || reply == null || reply.result == null ||
        !_reference(reply.result.identity, reply.result.revision) || reply.resolutions == null ||
        reply.resolutions.estimates == null || reply.resolutions.handoffs == null)
      Consequence.stateConflict("SplitPhase issued reply is incomplete")
    else {
      val pending = binding.context.semantic.pending
      val original = binding.context.selection.partition
      val estimates = reply.resolutions.estimates
      val handoffs = reply.resolutions.handoffs
      val references = Vector(reply.result.identity) ++ estimates.flatMap(r => Option(r).map(_.identity)) ++
        handoffs.flatMap(r => Option(r).map(_.identity))
      if (estimates.isDefined != pending.items.nonEmpty || handoffs.isDefined != pending.afterItems.nonEmpty ||
          !estimates.forall(r => r != null && _reference(r.identity, r.revision) &&
            r.identity != original.records.estimates.reference.identity) ||
          !handoffs.forall(r => r != null && _reference(r.identity, r.revision) &&
            r.identity != original.boundary.records.handoffs.reference.identity) ||
          references.distinct != references)
        Consequence.stateConflict("SplitPhase issued reply crosses gap roles or original identities")
      else Consequence.success(new Allocated(binding, reply))
    }

  private def _string_c(host: ResolvedConfiguration, key: String, limit: Int): Consequence[String] =
    GoalPhaseHostRecordReadPolicy.storedValue(host.configuration.values, prefix + key) match {
      case Some(ConfigurationValue.StringValue(value)) if _name(value, limit) => Consequence.success(value)
      case _ => Consequence.configurationInvalid("SplitPhase reply requires exact host policy metadata")
    }
  private def _encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
  private def _reference(identity: String, revision: String): Boolean = _name(identity, 1024) && _name(revision, 1024)
  private def _name(value: String, limit: Int): Boolean = value != null && value.nonEmpty && value.length <= limit &&
    value.trim == value && !value.exists(c => Character.isISOControl(c))
}
