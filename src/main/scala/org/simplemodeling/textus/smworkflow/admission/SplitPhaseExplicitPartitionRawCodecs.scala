package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json, KeyDecoder, KeyEncoder}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.workflow.WorkflowProtocolV1.ReasoningLevel
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitProposalPrecheck as P,
  SplitPhaseExplicitBoundaryValidation as B, SplitPhaseExplicitPartitionComputation as E,
  SplitPhaseExplicitObjectiveSelection as O, SplitPhaseExplicitPlanningAdvance as W}
import SplitPhasePartitionEnumeration.EdgeDisposition

/**
 * One mapping for new typed raw data. Not an admitted state or legacy decoder.
 * @since   Oct.  2, 2026
 * @version Oct. 21, 2026
 */
private[smworkflow] object SplitPhaseExplicitPartitionRawCodecs {
  given Encoder[ComponentId] = Encoder.encodeString.contramap(_.name)
  given Decoder[ComponentId] = Decoder.decodeString.emap(value =>
    if (name(value)) ComponentId.parseC(value).toOption.toRight("Invalid Component identity")
    else Left("Invalid Component identity"))
  given KeyEncoder[P.PhaseIdentity] = KeyEncoder.instance(_.value)
  given KeyDecoder[P.PhaseIdentity] = KeyDecoder.instance(value =>
    Option.when(name(value))(P.PhaseIdentity(value)))
  given phaseSetEncoder: Encoder[Set[P.PhaseIdentity]] = Encoder.encodeVector[P.PhaseIdentity]
    .contramap(_.toVector.sortBy(_.value))
  given authoritySetEncoder: Encoder[Set[P.AuthorityReference]] = Encoder.encodeVector[P.AuthorityReference]
    .contramap(_.toVector.sortBy(r => (r.identity, r.revision)))
  given Encoder[B.Timing] = Encoder.encodeString.contramap(_.toString)
  given Decoder[B.Timing] = Decoder.decodeString.emap(value =>
    B.Timing.values.find(_.toString == value).toRight("Unknown validation timing"))
  given Encoder[EdgeDisposition] = Encoder.encodeString.contramap(_.toString)
  given Decoder[EdgeDisposition] = Decoder.decodeString.emap(value =>
    EdgeDisposition.values.find(_.toString == value).toRight("Unknown edge disposition"))
  given Encoder[ReasoningLevel] = Encoder.encodeString.contramap(_.value)
  given Decoder[ReasoningLevel] = Decoder.decodeString.emap(value =>
    ReasoningLevel.values.find(_.value == value).toRight("Unknown reasoning level"))
  given Encoder[E.Duration] = Encoder.instance {
    case E.Duration.Known(minutes) => Json.obj("kind" -> Json.fromString("Known"), "minutes" -> Json.fromInt(minutes))
    case E.Duration.Unresolved(reason) => Json.obj("kind" -> Json.fromString("Unresolved"), "reason" -> Json.fromString(reason))
  }
  given Decoder[E.Duration] = Decoder.instance { cursor =>
    cursor.get[String]("kind").flatMap {
      case "Known" => cursor.get[Int]("minutes").map(E.Duration.Known(_))
      case "Unresolved" => cursor.get[String]("reason").map(E.Duration.Unresolved(_))
      case _ => Left(io.circe.DecodingFailure("Unknown duration", cursor.history))
    }
  }
  given Encoder[O.Demand] = Encoder.instance {
    case O.Demand.ExpensiveReasoning => Json.obj("kind" -> Json.fromString("ExpensiveReasoning"))
    case O.Demand.SettledExecution => Json.obj("kind" -> Json.fromString("SettledExecution"))
    case O.Demand.Unresolved(reason) => Json.obj("kind" -> Json.fromString("Unresolved"), "reason" -> Json.fromString(reason))
  }
  given Decoder[O.Demand] = Decoder.instance { cursor =>
    cursor.get[String]("kind").flatMap {
      case "ExpensiveReasoning" => Right(O.Demand.ExpensiveReasoning)
      case "SettledExecution" => Right(O.Demand.SettledExecution)
      case "Unresolved" => cursor.get[String]("reason").map(O.Demand.Unresolved(_))
      case _ => Left(io.circe.DecodingFailure("Unknown reasoning demand", cursor.history))
    }
  }

  // Bind leaf mappings before their aggregates to avoid forward initialization
  // and repeated nested inline derivation. These are the same wire projections.
  given sourceRecordEncoder: Encoder[P.SourceRecord] = deriveEncoder[P.SourceRecord]
  given sourceRecordDecoder: Decoder[P.SourceRecord] = deriveDecoder[P.SourceRecord]
  given inventoryRecordEncoder: Encoder[P.InventoryRecord] = deriveEncoder[P.InventoryRecord]
  given inventoryRecordDecoder: Decoder[P.InventoryRecord] = deriveDecoder[P.InventoryRecord]
  given proposalRecordEncoder: Encoder[P.ProposalRecord] = deriveEncoder[P.ProposalRecord]
  given proposalRecordDecoder: Decoder[P.ProposalRecord] = deriveDecoder[P.ProposalRecord]
  given handoffEncoder: Encoder[B.Handoff] = deriveEncoder[B.Handoff]
  given handoffDecoder: Decoder[B.Handoff] = deriveDecoder[B.Handoff]
  given validationEncoder: Encoder[B.ValidationChild] = deriveEncoder[B.ValidationChild]
  given validationDecoder: Decoder[B.ValidationChild] = deriveDecoder[B.ValidationChild]
  given boundaryRecordEncoder: Encoder[B.Boundary] = deriveEncoder[B.Boundary]
  given boundaryRecordDecoder: Decoder[B.Boundary] = deriveDecoder[B.Boundary]
  given ownershipRecordEncoder: Encoder[B.Ownership] = deriveEncoder[B.Ownership]
  given ownershipRecordDecoder: Decoder[B.Ownership] = deriveDecoder[B.Ownership]
  given handoffPlanEncoder: Encoder[B.HandoffPlan] = deriveEncoder[B.HandoffPlan]
  given handoffPlanDecoder: Decoder[B.HandoffPlan] = deriveDecoder[B.HandoffPlan]
  given estimatesRecordEncoder: Encoder[E.Estimates] = deriveEncoder[E.Estimates]
  given estimatesRecordDecoder: Decoder[E.Estimates] = deriveDecoder[E.Estimates]
  given partitionPolicyEncoder: Encoder[E.Policy] = deriveEncoder[E.Policy]
  given partitionPolicyDecoder: Decoder[E.Policy] = deriveDecoder[E.Policy]
  given objectiveEncoder: Encoder[O.Records] = deriveEncoder[O.Records]
  given objectiveDecoder: Decoder[O.Records] = deriveDecoder[O.Records]
  given planningEncoder: Encoder[W.Data] = deriveEncoder[W.Data]
  given planningDecoder: Decoder[W.Data] = deriveDecoder[W.Data]
  given proposalEncoder: Encoder[P.Records] = deriveEncoder[P.Records]
  given proposalDecoder: Decoder[P.Records] = deriveDecoder[P.Records]
  given boundaryEncoder: Encoder[B.Records] = deriveEncoder[B.Records]
  given boundaryDecoder: Decoder[B.Records] = deriveDecoder[B.Records]
  given partitionEncoder: Encoder[E.Records] = deriveEncoder[E.Records]
  given partitionDecoder: Decoder[E.Records] = deriveDecoder[E.Records]
  private def name(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
}
