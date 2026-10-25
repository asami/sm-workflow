package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, parser}
import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitPlanningInputJsonV1 as J,
  SplitPhaseExplicitPartitionComputation as E, SplitPhaseExplicitPartitionContext as C,
  SplitPhaseExplicitProposalPrecheck as P, SplitPhaseExplicitBoundaryValidation as B}
import SplitPhaseExplicitPartitionFixture.take
import SplitPhasePartitionContextFixture.*

/**
 * @since   Oct.  2, 2026
 * @version Oct. 24, 2026
 */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
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

    "consume current typed rows without refusing inert fields or absent optional data" in {
      Given("independent records with annotations or malformed required values")
      val all = rows
      val cases = all.map { r =>
        val original = json(r.raw)
        val unused = Vector(original.mapObject(_.add("admitted", Json.True)),
          payload(original)(_.add("digest", Json.fromString("legacy"))),
          payload(original)(o => o.add("reference", o("reference").get.mapObject(_.add("claim", Json.True)))))
        val invalid = Vector(original.mapObject(_.add("schemaVersion", Json.fromString("legacy"))),
          original.mapObject(_.add("purpose", Json.fromString("foreign"))),
          payload(original)(_.remove("reference")),
          payload(original)(o => o.add("reference", o("reference").get.mapObject(_.remove("revision")))))
        (r, unused, invalid)
      }
      val planning = all.last
      When("the existing raw decoder consumes the selected purpose and typed values")
      val accepted = cases.flatMap { case (r, unused, _) => unused.map(v => r.recover(wire(v))) }
      val refused = cases.flatMap { case (r, _, invalid) => invalid.map(v => r.valid(wire(v))) }
      val optional = planning.recover(wire(payload(json(planning.raw))(_.remove("allocations"))))
      Then("inert data and optional absence are accepted, but missing consumed data and crossed purposes fail")
      accepted.foreach(_ shouldBe true)
      refused.foreach(_ shouldBe false)
      optional shouldBe true
      all.foreach { r =>
        r.valid("not-json") shouldBe false
        r.valid(null) shouldBe false
      }
    }

    "consume decoder-supported durations while refusing unknown variants and oversized UTF-8 data" in {
      Given("known and unresolved duration rows plus a typed ownership enum")
      val estimates = json(take(J.encodeEstimatesC(facts.partition.estimates)))
      val items = estimates.hcursor.downField("payload").downField("items").focus.get.asArray.get
      val invalidDurations = Vector(Json.obj("kind" -> Json.fromString("Unknown")),
        Json.obj("kind" -> Json.fromString("Known"), "minutes" -> Json.fromString("not-a-minute")))
      val annotated = payload(estimates)(_.add("items", Json.fromValues(items.updated(0,
        items.head.mapObject(_.add("duration", Json.obj("kind" -> Json.fromString("Known"),
          "minutes" -> Json.fromString("260"), "reason" -> Json.fromString("unused"))))))))
      val ownership = json(take(J.encodeOwnershipC(facts.boundary.ownership)))
      val huge = facts.partition.estimates.copy(items = facts.partition.estimates.items.updated(1,
        facts.partition.estimates.items(1).copy(duration = E.Duration.Unresolved("語" * (J.maximumPayloadBytes / 3 + 1)))))
      When("the existing type decoder consumes durations and enums within the storage bound")
      val accepted = J.decodeEstimatesC(facts.partition.estimates.reference, wire(annotated))
      val refused = invalidDurations.map { duration =>
        val variant = payload(estimates)(_.add("items", Json.fromValues(items.updated(0,
          items.head.mapObject(_.add("duration", duration))))))
        J.decodeEstimatesC(facts.partition.estimates.reference, wire(variant))
      }
      val wrongenum = J.decodeOwnershipC(facts.boundary.ownership.reference,
        wire(payload(ownership)(_.add("timing", Json.fromString("Unknown")))))
      val oversized = J.encodeEstimatesC(huge)
      Then("unknown variants and unreadable values fail, while supported numeric strings and inert annotations recover the same duration")
      take(accepted) shouldBe facts.partition.estimates
      refused.foreach(_.isSuccess shouldBe false)
      wrongenum.isSuccess shouldBe false
      oversized.isSuccess shouldBe false
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

  }
}
