package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, parser}
import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitPlanningInputJsonV1 as J,
  SplitPhaseExplicitPartitionComputation as E, SplitPhaseExplicitPartitionContext as C,
  SplitPhaseExplicitProposalPrecheck as P, SplitPhaseExplicitBoundaryValidation as B,
  SplitPhaseExplicitObjectiveSelection as O, SplitPhaseExplicitCandidateProjection as Q}
import SplitPhaseExplicitPartitionFixture.take
import SplitPhasePartitionContextFixture.*

/**
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhaseExplicitPlanningInputRecordSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private final case class Row(purpose: J.Purpose, raw: String, recover: String => Boolean,
    valid: String => Boolean, foreignValid: String => Boolean, revisionValid: String => Boolean)
  private def row[A](purpose: J.Purpose, value: A, encode: A => Consequence[String],
    decode: String => Consequence[A], foreign: String => Consequence[A], revision: String => Consequence[A]): Row =
    Row(purpose, take(encode(value)), raw => decode(raw).toOption.contains(value),
      raw => decode(raw).isSuccess, raw => foreign(raw).isSuccess, raw => revision(raw).isSuccess)
  private def rows = Vector(
    row(J.Purpose.Source, facts.proposal.source, J.encodeSourceC,
      raw => J.decodeSourceC(facts.proposal.source.reference, raw),
      raw => J.decodeSourceC(facts.proposal.source.reference.copy(identity = "foreign"), raw),
      raw => J.decodeSourceC(facts.proposal.source.reference.copy(revision = "2"), raw)),
    row(J.Purpose.Inventory, facts.proposal.inventory, J.encodeInventoryC,
      raw => J.decodeInventoryC(facts.proposal.inventory.reference, raw),
      raw => J.decodeInventoryC(facts.proposal.inventory.reference.copy(identity = "foreign"), raw),
      raw => J.decodeInventoryC(facts.proposal.inventory.reference.copy(revision = "2"), raw)),
    row(J.Purpose.Proposal, facts.proposal.proposal, J.encodeProposalC,
      raw => J.decodeProposalC(facts.proposal.proposal.reference, raw),
      raw => J.decodeProposalC(facts.proposal.proposal.reference.copy(identity = "foreign"), raw),
      raw => J.decodeProposalC(facts.proposal.proposal.reference.copy(revision = "2"), raw)),
    row(J.Purpose.Boundary, facts.boundary.boundary, J.encodeBoundaryC,
      raw => J.decodeBoundaryC(facts.boundary.boundary.reference, raw),
      raw => J.decodeBoundaryC(facts.boundary.boundary.reference.copy(identity = "foreign"), raw),
      raw => J.decodeBoundaryC(facts.boundary.boundary.reference.copy(revision = "2"), raw)),
    row(J.Purpose.Ownership, facts.boundary.ownership, J.encodeOwnershipC,
      raw => J.decodeOwnershipC(facts.boundary.ownership.reference, raw),
      raw => J.decodeOwnershipC(facts.boundary.ownership.reference.copy(identity = "foreign"), raw),
      raw => J.decodeOwnershipC(facts.boundary.ownership.reference.copy(revision = "2"), raw)),
    row(J.Purpose.Handoffs, facts.boundary.handoffs, J.encodeHandoffsC,
      raw => J.decodeHandoffsC(facts.boundary.handoffs.reference, raw),
      raw => J.decodeHandoffsC(facts.boundary.handoffs.reference.copy(identity = "foreign"), raw),
      raw => J.decodeHandoffsC(facts.boundary.handoffs.reference.copy(revision = "2"), raw)),
    row(J.Purpose.Estimates, facts.partition.estimates, J.encodeEstimatesC,
      raw => J.decodeEstimatesC(facts.partition.estimates.reference, raw),
      raw => J.decodeEstimatesC(facts.partition.estimates.reference.copy(identity = "foreign"), raw),
      raw => J.decodeEstimatesC(facts.partition.estimates.reference.copy(revision = "2"), raw)),
    row(J.Purpose.PartitionPolicy, facts.partition.policy, J.encodePartitionPolicyC,
      raw => J.decodePartitionPolicyC(facts.partition.policy.reference, raw),
      raw => J.decodePartitionPolicyC(facts.partition.policy.reference.copy(identity = "foreign"), raw),
      raw => J.decodePartitionPolicyC(facts.partition.policy.reference.copy(revision = "2"), raw)),
    row(J.Purpose.Planning, facts.planning, J.encodePlanningC,
      raw => J.decodePlanningC(facts.planning.reference, raw),
      raw => J.decodePlanningC(facts.planning.reference.copy(identity = "foreign"), raw),
      raw => J.decodePlanningC(facts.planning.reference.copy(revision = "2"), raw))
  )
  private def json(raw: String) = parser.parse(raw.substring(J.schema.length + 1)).toOption.get
  private def wire(value: Json) = J.schema + ":" + value.noSpaces
  private def payload(value: Json)(f: io.circe.JsonObject => io.circe.JsonObject) =
    value.mapObject(o => o.add("payload", o("payload").get.mapObject(f)))

  // Mutate actual declared data, not source text or a canonical encoder image.
  // The incoming-by-child object is a dynamic typed map, not a closed record.
  private def _malformed(value: Json, openmap: Boolean = false): Vector[Json] = value.asObject match {
    case Some(fields) =>
      val direct = if (openmap) Vector.empty else
        Vector(value.mapObject(_.add("undeclared", Json.True))) ++
          fields.keys.toVector.map(key => value.mapObject(_.remove(key)))
      direct ++ fields.toVector.flatMap { case (key, child) =>
        _malformed(child, key == "incomingByChild").map(changed => value.mapObject(_.add(key, changed)))
      }
    case None => value.asArray match {
      case Some(items) => items.indices.toVector.flatMap(index =>
        _malformed(items(index)).map(changed => Json.fromValues(items.updated(index, changed))))
      case None if value.isNull => Vector.empty
      case None if value.isString => Vector(Json.True, Json.Null)
      case None => Vector(Json.fromString("wrong-type"), Json.Null)
    }
  }

  "Independent typed planning input records" should {
    "round-trip every purpose with its own exact reference without context data" in {
      Given("nine independent source records and explicit optional absences")
      val all = rows
      When("each closed row is decoded")
      Then("its own type and purpose are recovered with no context or admission object")
      all.map(_.purpose).toSet shouldBe J.Purpose.values.toSet
      all.foreach { r =>
        withClue(r.purpose.toString) {
          r.recover(r.raw) shouldBe true
          r.recover(J.schema + ":" + json(r.raw).spaces2) shouldBe true
          r.foreignValid(r.raw) shouldBe false
          r.revisionValid(r.raw) shouldBe false
          json(r.raw).asObject.get.keys.toSet shouldBe Set("schemaVersion", "purpose", "payload")
        }
      }
      val planning = json(all.last.raw).hcursor.downField("payload")
      planning.downField("allocations").focus shouldBe Some(Json.Null)
      planning.downField("objectives").downField("cost").focus shouldBe Some(Json.Null)
    }

    "close envelope nested fields and explicit optional absence for all nine purposes" in {
      Given("valid independent typed records")
      When("foreign schema, purpose, unknown and missing nested data arrive")
      Then("raw decoding refuses them without legacy fallback")
      rows.foreach { r =>
        val original = json(r.raw)
        val changed = Vector(
          original.mapObject(_.add("schemaVersion", Json.fromString("legacy"))),
          original.mapObject(_.add("purpose", Json.fromString("foreign"))),
          original.mapObject(_.add("admitted", Json.True)),
          payload(original)(_.remove("reference")),
          payload(original)(_.add("digest", Json.fromString("legacy"))),
          payload(original)(o => o.add("reference", o("reference").get.mapObject(_.add("claim", Json.True)))),
          payload(original)(o => o.add("reference", o("reference").get.mapObject(_.remove("revision")))))
        changed.foreach(v => r.valid(wire(v)) shouldBe false)
        r.valid("not-json") shouldBe false
        r.valid(null) shouldBe false
      }
      val planning = rows.last
      planning.valid(wire(payload(json(planning.raw))(_.remove("allocations")))) shouldBe false
      J.encodePlanningC(facts.planning.copy(objectives = null)).isSuccess shouldBe false
    }

    "refuse malformed sums enums primitives and oversized UTF-8 data at the raw boundary" in {
      Given("known and unresolved duration rows plus a typed ownership enum")
      val estimates = json(take(J.encodeEstimatesC(facts.partition.estimates)))
      val items = estimates.hcursor.downField("payload").downField("items").focus.get.asArray.get
      val invalidDurations = Vector(Json.obj("kind" -> Json.fromString("Unknown")),
        Json.obj("kind" -> Json.fromString("Known"), "minutes" -> Json.fromString("260")),
        Json.obj("kind" -> Json.fromString("Known"), "minutes" -> Json.fromInt(260), "reason" -> Json.fromString("extra")))
      When("malformed durations, enums and byte limits are checked")
      Then("the storage boundary rejects structural deviations")
      invalidDurations.foreach { duration =>
        val variant = payload(estimates)(_.add("items", Json.fromValues(items.updated(0,
          items.head.mapObject(_.add("duration", duration))))))
        J.decodeEstimatesC(facts.partition.estimates.reference, wire(variant)).isSuccess shouldBe false
      }
      val ownership = json(take(J.encodeOwnershipC(facts.boundary.ownership)))
      J.decodeOwnershipC(facts.boundary.ownership.reference,
        wire(payload(ownership)(_.add("timing", Json.fromString("Unknown"))))).isSuccess shouldBe false
      val huge = facts.partition.estimates.copy(items = facts.partition.estimates.items.updated(1,
        facts.partition.estimates.items(1).copy(duration = E.Duration.Unresolved("語" * (J.maximumPayloadBytes / 3 + 1)))))
      J.encodeEstimatesC(huge).isSuccess shouldBe false
      rows.foreach(r => r.valid(J.schema + ":" + "語" * (J.maximumPayloadBytes / 3 + 1)) shouldBe false)
    }

    "leave numerical admission to the owning semantic constructor" in {
      Given("a structurally complete but negative partition overhead")
      val policy = facts.partition.policy.copy(phaseOverheadMinutes = -1)
      When("the raw row is decoded separately from context preparation")
      val rawPolicy = take(J.decodePartitionPolicyC(policy.reference, take(J.encodePartitionPolicyC(policy))))
      Then("shape succeeds while the actual admitting constructor refuses execution")
      rawPolicy shouldBe policy
      val current = facts.copy(partition = facts.partition.copy(policy = rawPolicy))
      C.prepareC(record.copy(facts = current), current, instance, configuration, catalog).isSuccess shouldBe false
    }

    "read declared Set meaning without proving the encoder's array ordering or repetition" in {
      Given("Spec: docs/spec/split-phase-planning-input-applicability.md; Rule: R1; Example: E1; independent inventory and boundary records with multiple Set members")
      val inventory = facts.proposal.inventory.copy(occupiedPhases = Set(P.PhaseIdentity("7"), P.PhaseIdentity("9")))
      val boundary = facts.boundary.boundary.copy(authorizedHandoffAuthorities = Set(facts.boundary.boundary.authority,
        P.AuthorityReference("additional-authority", "1")))
      val inventorywire = json(take(J.encodeInventoryC(inventory)))
      val boundarywire = json(take(J.encodeBoundaryC(boundary)))
      def _changed_(value: Json, field: String) = payload(value) { fields =>
        val members = fields(field).get.asArray.get
        fields.add(field, Json.fromValues(members.reverse :+ members.head))
      }
      When("both independent raw readers receive valid reordered and repeated Set members")
      val readinventory = J.decodeInventoryC(inventory.reference, wire(_changed_(inventorywire, "occupiedPhases")))
      val readboundary = J.decodeBoundaryC(boundary.reference, wire(_changed_(boundarywire, "authorizedHandoffAuthorities")))
      Then("the selected references and typed Set data resolve without whole-body re-encoding or a rewrite")
      take(readinventory) shouldBe inventory
      take(readboundary) shouldBe boundary
    }

    "reject recursively malformed declared data including populated optional allocations and objective policies" in {
      Given("Spec: docs/spec/split-phase-planning-input-applicability.md; Rule: R1; Example: E2; all nine purposes and populated optional planning subrecords")
      val source = baseData.source
      val objectives = O.Records(
        Some(O.CostPolicy(O.CostPolicyReference("cost", "1"), source.reference, source.authority,
          O.ProfileIdentity("deep"), O.ProfileIdentity("routine"), Long.MaxValue, 1L, 300L)),
        Some(O.DemandRecord(O.DemandReference("demands", "1"), source.reference, source.authority,
          Vector(O.ItemDemand(P.WorkItemIdentity("item-0"), O.Demand.ExpensiveReasoning),
            O.ItemDemand(P.WorkItemIdentity("item-1"), O.Demand.Unresolved("unknown")),
            O.ItemDemand(P.WorkItemIdentity("item-2"), O.Demand.SettledExecution)))),
        Some(O.OverheadPolicy(O.OverheadPolicyReference("overhead", "1"), source.reference, source.authority, 1L, 2L, 3L, 4L, 5L)))
      val allocations = Q.Records(Q.AllocationReference("allocations", "1"), source.reference, source.authority,
        Vector(Q.Allocation(Vector(0), P.ProposalReference("allocated", "1"),
          B.OwnershipReference("allocated-owner", "1"), B.HandoffPlanReference("allocated-handoff", "1"))))
      val planning = facts.planning.copy(allocations = Some(allocations), objectives = objectives)
      val populated = row(J.Purpose.Planning, planning, J.encodePlanningC,
        raw => J.decodePlanningC(planning.reference, raw),
        raw => J.decodePlanningC(planning.reference.copy(identity = "foreign"), raw),
        raw => J.decodePlanningC(planning.reference.copy(revision = "2"), raw))
      val independent = rows.init :+ populated
      When("unknown or missing record fields and wrong primitive types arrive at every populated nested position")
      val results = independent.map { r =>
        val original = json(r.raw)
        val malformed = _malformed(original.hcursor.downField("payload").focus.get)
        r -> malformed.map(changed => r.valid(wire(original.mapObject(_.add("payload", changed)))))
      }
      Then("every structural deviation is refused, while complete optional data and Long values remain readable")
      populated.recover(populated.raw) shouldBe true
      results.foreach { case (r, valid) =>
        withClue(r.purpose.toString) {
          valid.nonEmpty shouldBe true
          valid.forall(value => !value) shouldBe true
        }
      }
    }
  }
}
