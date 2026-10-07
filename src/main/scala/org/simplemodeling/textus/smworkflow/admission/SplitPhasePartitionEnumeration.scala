package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import org.goldenport.Consequence

/** Enumerates duration-feasible contiguous split shapes from explicit edge
  * dispositions. Edge semantics and child closure must be admitted separately;
  * this operation neither chooses a winner nor admits a proposal.
  * @since   Sep. 27, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhasePartitionEnumeration {
  final case class WorkItem(identity: String, estimateMinutes: Int)

  enum EdgeDisposition {
    case CutOnly, CrossOnly, Either, Unresolved
  }

  final case class Edge(afterItem: String, disposition: EdgeDisposition,
                        authorityReference: String)
  final case class Policy(version: String, phaseOverheadMinutes: Int,
                          maximumCombinations: Int)
  final case class ChildRange(firstIndex: Int, endExclusive: Int,
                              ownedItems: Vector[String], expectedMinutes: Int)
  final case class Candidate(cutAfterIndices: Vector[Int],
                             children: Vector[ChildRange], digest: String)

  sealed trait Outcome
  object Outcome {
    final case class Enumerated(policyDigest: String, consideredDigest: String,
                                rejectedByCeiling: Int,
                                candidates: Vector[Candidate]) extends Outcome
    final case class SemanticAssessmentRequired(
      unresolvedAfterItems: Vector[String]) extends Outcome
    final case class NoAdmissible(reason: String) extends Outcome
  }

  def enumerateC(sourceSnapshotDigest: String, items: Vector[WorkItem],
                 edges: Vector[Edge], policy: Policy): Consequence[Outcome] =
    if (!_complete(sourceSnapshotDigest, items, edges, policy))
      Consequence.stateConflict("SplitPhase partition enumeration input is incomplete")
    else {
      val unresolved = edges.collect {
        case edge if edge.disposition == EdgeDisposition.Unresolved => edge.afterItem
      }
      if (unresolved.nonEmpty)
        Consequence.success(Outcome.SemanticAssessmentRequired(unresolved))
      else {
        val combinations = edges.foldLeft(BigInt(1)) { (count, edge) =>
          count * (if (edge.disposition == EdgeDisposition.Either) 2 else 1)
        }
        if (combinations > policy.maximumCombinations)
          Consequence.stateConflict("SplitPhase partition enumeration exceeds policy limit")
        else {
          val cutSets = edges.zipWithIndex.foldLeft(Vector(Vector.empty[Int])) {
            case (current, (edge, index)) =>
              edge.disposition match {
                case EdgeDisposition.CutOnly => current.map(_ :+ index)
                case EdgeDisposition.CrossOnly => current
                case EdgeDisposition.Either => current.flatMap(cuts =>
                  Vector(cuts, cuts :+ index))
                case EdgeDisposition.Unresolved => current // handled above
              }
          }.filter(_.nonEmpty).sortWith { (left, right) =>
            if (left.size != right.size) left.size < right.size
            else _lexLess(left, right)
          }
          if (cutSets.isEmpty)
            Consequence.success(Outcome.NoAdmissible("no-authorized-split-boundary"))
          else {
            val evaluated = cutSets.map(cuts => cuts -> _children(items, cuts, policy))
            val feasible = evaluated.collect { case (cuts, Some(children)) =>
              Candidate(cuts, children, candidateDigestOf(sourceSnapshotDigest,
                policyDigestOf(policy), cuts, children))
            }
            if (feasible.isEmpty)
              Consequence.success(Outcome.NoAdmissible("every-split-exceeds-eight-hours"))
            else {
              val policyDigest = policyDigestOf(policy)
              val consideredDigest = _sha(Vector(sourceSnapshotDigest, policyDigest,
                items.size.toString) ++ items.flatMap(item => Vector(item.identity,
                item.estimateMinutes.toString)) ++ edges.flatMap(edge => Vector(
                edge.afterItem, edge.disposition.toString, edge.authorityReference)) ++
                evaluated.flatMap { case (cuts, result) =>
                  Vector(cuts.mkString(","), result.fold("over-ceiling")(_ => "feasible"))
                })
              Consequence.success(Outcome.Enumerated(policyDigest, consideredDigest,
                evaluated.count(_._2.isEmpty), feasible))
            }
          }
        }
      }
    }

  private def _children(items: Vector[WorkItem], cuts: Vector[Int],
                        policy: Policy): Option[Vector[ChildRange]] = {
    val ends = cuts.map(_ + 1) :+ items.size
    val starts = Vector(0) ++ ends.init
    val ranges = starts.zip(ends).map { case (start, end) =>
      val owned = items.slice(start, end)
      (start, end, owned,
        owned.map(_.estimateMinutes.toLong).sum + policy.phaseOverheadMinutes.toLong)
    }
    if (ranges.exists(_._4 > 480L)) None
    else Some(ranges.map { case (start, end, owned, minutes) =>
      ChildRange(start, end, owned.map(_.identity), minutes.toInt)
    })
  }

  private def _complete(source: String, items: Vector[WorkItem],
                        edges: Vector[Edge], policy: Policy): Boolean =
    source != null && source.matches("[0-9a-f]{64}") && items != null &&
      items.size >= 2 && items.size <= 128 &&
      items.forall(item => item != null && _name(item.identity) &&
        item.estimateMinutes > 0) &&
      items.map(_.identity).distinct.size == items.size && edges != null &&
      edges.size == items.size - 1 && edges.zipWithIndex.forall {
        case (edge, index) => edge != null &&
          edge.afterItem == items(index).identity &&
          edge.disposition != null && _name(edge.authorityReference)
      } && policy != null && _name(policy.version) &&
      policy.phaseOverheadMinutes >= 0 && policy.phaseOverheadMinutes < 480 &&
      policy.maximumCombinations >= 1 && policy.maximumCombinations <= 4096

  def policyDigestOf(policy: Policy): String = _sha(Vector(policy.version,
    policy.phaseOverheadMinutes.toString, policy.maximumCombinations.toString))

  def candidateDigestOf(source: String, policyDigest: String,
                               cuts: Vector[Int], children: Vector[ChildRange]): String =
    _sha(Vector(source, policyDigest, cuts.size.toString) ++ cuts.map(_.toString) ++
      children.flatMap(child => Vector(child.firstIndex.toString,
        child.endExclusive.toString, child.expectedMinutes.toString,
        child.ownedItems.size.toString) ++ child.ownedItems))

  private def _name(value: String): Boolean = value != null && value.trim.nonEmpty

  private def _lexLess(left: Vector[Int], right: Vector[Int]): Boolean =
    left.zip(right).find { case (a, b) => a != b } match {
      case Some((a, b)) => a < b
      case None => left.size < right.size
    }

  private def _sha(fields: Vector[String]): String = {
    val bytes = fields.map(value => s"${value.length}:$value")
      .mkString.getBytes(StandardCharsets.UTF_8)
    MessageDigest.getInstance("SHA-256").digest(bytes)
      .map(byte => f"${byte & 0xff}%02x").mkString
  }
}
