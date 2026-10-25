package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, parser}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseHumanScopeResolution as H,
  SplitPhaseHumanScopeResolutionJsonV1 as J, SplitPhaseScopeSemantic as S,
  SplitPhaseExplicitPartitionSemantic as T, SplitPhaseScopeProjectionCheckpointPreparation as C}
import SplitPhaseExplicitPartitionFixture.*

/** Passive data contracts, separate from the native use-case specification.
  *
  * @since Oct.  5, 2026
  * @version Oct. 24, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class SplitPhaseHumanScopeResolutionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _record = {
    val source = base(Vector.fill(4)(180))
    val candidate = enumerate(source).candidates.find(_.cutAfterIndices == Vector(1)).get
    val boundary = proof(source, candidate, "human-data-template", handoffPairs = Vector(0 -> 1))
    val checkpoint = SplitPhasePlanningCheckpointFixture.forBoundary(source, boundary).record
    val request = take(S.requestC(checkpoint))
    H.Record(H.Reference("human-resolution", "1"), request.context, request.checkpoint,
      C.Reference("published-scope", "1"), H.DecisionReference("issued-human-decision"),
      request.gaps.map(gap => S.Scope(gap.original.identity, gap.producingItems.takeRight(1),
        gap.receivingItems.take(1), "explicit producer and receiver evidence")),
      "Human interpretation of original transfer ranges", Vector(T.EvidenceReference("human-evidence", "1")))
  }

  "Human interpretation storage representation" should {
    "keep opaque storage text separate from the unchanged passive JSON body" in {
      Given("one complete interpretation and its passive JSON representation")
      val record = _record
      val body = take(J.encodeC(record))
      When("the storage codec encodes and decodes the exact selected revision")
      val stored = take(J.encodeStorageC(record))
      val decoded = J.decodeStorageC(record.reference, stored)
      Then("the prefixed value preserves the JSON body but the two formats cannot impersonate each other")
      stored shouldBe J.schema + ":" + body
      take(decoded) shouldBe record
      take(J.decodeC(record.reference, body)) shouldBe record
      J.decodeStorageC(record.reference, body).isSuccess shouldBe false
      J.decodeC(record.reference, stored).isSuccess shouldBe false
    }

    "refuse foreign storage forms without relaxing body, revision or full-size admission" in {
      Given("one exact storage record and malformed, foreign, oversized or contradictory variants")
      val record = _record
      val body = take(J.encodeC(record))
      val stored = take(J.encodeStorageC(record))
      val prefix = J.schema + ":"
      val extra = take(parser.parse(body).fold(
        error => org.goldenport.Consequence.stateConflict(error.message), org.goldenport.Consequence.success))
        .hcursor.downField("payload").withFocus(_.mapObject(_.add("authority", Json.fromString("owned")))).top.get
      val invalid = Vector(null, "", body, "foreign:" + body, prefix + "{",
        prefix + ("x" * J.maximumBytes))
      When("the owning storage decoder consumes these variants or a stale selected revision")
      val annotated = J.decodeStorageC(record.reference, prefix + extra.noSpaces)
      val rejected = invalid.map(value => J.decodeStorageC(record.reference, value))
      val stale = J.decodeStorageC(record.reference.copy(revision = "2"), stored)
      Then("all storage contradictions are refused and no passive/native admission is fabricated")
      rejected.foreach(_.isSuccess shouldBe false)
      stale.isSuccess shouldBe false
      take(annotated) shouldBe record
      J.decodeStorageC(null, stored).isSuccess shouldBe false
    }
  }

  "Passive Human interpretation data" should {
    "roundtrip one exact data revision without creating Human or AI authority" in {
      Given("complete passive interpretation data naming its separate original purposes")
      val record = _record
      When("the Component data codec interprets the selected revision")
      val decoded = J.decodeC(record.reference, take(J.encodeC(record)))
      val unobserved = H.admitC(null, record)
      Then("the data is preserved, but no Human admission can be decoded or constructed")
      take(decoded) shouldBe record
      unobserved.isSuccess shouldBe false
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseHumanScopeResolution.Admitted(null, null, null)")
    }

    "consume unused routing annotations without accepting revision substitution or Human authority" in {
      Given("one selected interpretation and a complete closed JSON envelope")
      val record = _record
      val json = take(parser.parse(take(J.encodeC(record))).fold(
        error => org.goldenport.Consequence.stateConflict(error.message), org.goldenport.Consequence.success))
      val extra = Vector(json.mapObject(_.add("operation", Json.fromString("apply"))),
        json.hcursor.downField("payload").withFocus(_.mapObject(_.add("winner", Json.fromInt(0)))).top.get,
        json.hcursor.downField("payload").downField("reference")
          .withFocus(_.mapObject(_.add("authority", Json.fromString("owned")))).top.get)
      When("the passive codec consumes stale selections or unused dispatch/authority assertions")
      val stale = J.decodeC(record.reference.copy(revision = "2"), json.noSpaces)
      val decoded = extra.map(value => J.decodeC(record.reference, value.noSpaces))
      val unobserved = decoded.map(value => value.flatMap(H.admitC(null, _)))
      Then("the original typed data survives annotations, but a foreign selected revision or absent canonical Human observation does not")
      stale.isSuccess shouldBe false
      decoded.foreach(value => take(value) shouldBe record)
      unobserved.foreach(_.isSuccess shouldBe false)
    }

    "refuse structurally absent or oversized data without performing semantic interpretation" in {
      Given("a complete interpretation and incomplete range/evidence variants")
      val record = _record
      val invalid = Vector(null, record.copy(scopes = null), record.copy(scopes = Vector(null)),
        record.copy(evidence = null), record.copy(evidence = Vector(null)), record.copy(rationale = null),
        record.copy(decision = null), record.copy(reference = record.reference.copy(identity = null)))
      When("data encoding and bounded decoding interpret these records")
      val rejected = invalid.map(J.encodeC)
      val oversized = J.decodeC(record.reference, "x" * (J.maximumBytes + 1))
      Then("invalid records and oversized frames produce refusal, not admitted capabilities")
      rejected.foreach(_.isSuccess shouldBe false)
      oversized.isSuccess shouldBe false
    }

    "transport semantic-incomplete interpretation data without treating it as Human consent" in {
      Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R9; Example: E22; declared passive records with contradictory semantic quality")
      val record = _record
      val scope = record.scopes.head
      val incomplete = Vector(record.copy(scopes = Vector.empty), record.copy(scopes = record.scopes ++ record.scopes),
        record.copy(scopes = Vector(scope.copy(producingItems = Vector.empty))), record.copy(evidence = Vector.empty),
        record.copy(evidence = record.evidence ++ record.evidence), record.copy(rationale = " "),
        record.copy(decision = H.DecisionReference("foreign\n")))

      When("the codec writes and reads actual declared fields and the semantic boundary inspects each interpretation")
      val actual = incomplete.map { value =>
        val wire = take(J.encodeC(value))
        val presented = parser.parse(wire).toOption.get.asObject.get.toVector.reverse
        val decoded = J.decodeC(value.reference, Json.obj(presented*).spaces2)
        (value, decoded, H.inspectC(value), H.admitC(null, value))
      }

      Then("passive data survives without serializer-body proof while invalid interpretations grant no Human admission")
      actual.foreach { (value, decoded, inspected, admitted) =>
        take(decoded) shouldBe value
        inspected.isSuccess shouldBe false
        admitted.isSuccess shouldBe false
      }
    }

    "consume nested annotations and reject missing or wrong typed consumed data without reencoding" in {
      Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R9; Example: E23; one passive resolution and all nested declared objects")
      val record = _record
      val wire = parser.parse(take(J.encodeC(record))).toOption.get
      def _extensions_(value: Json): Vector[Json] = value.asObject match {
        case Some(fields) =>
          Vector(value.mapObject(_.add("commandGrant", Json.fromBoolean(true)))) ++
            fields.toVector.flatMap { (field, child) =>
              _extensions_(child).map(changed => value.mapObject(_.add(field, changed)))
            }
        case None => value.asArray.map(_.zipWithIndex.flatMap { (child, index) =>
          _extensions_(child).map(changed => value.mapArray(_.updated(index, changed)))
        }.toVector).getOrElse(Vector.empty)
      }
      def _mutations_(value: Json): Vector[Json] = value.asObject match {
        case Some(fields) =>
          fields.keys.toVector.map(field => value.mapObject(_.remove(field))) ++
            fields.toVector.flatMap { (field, child) =>
              _mutations_(child).map(changed => value.mapObject(_.add(field, changed)))
            }
        case None => value.asArray.map(_.zipWithIndex.flatMap { (child, index) =>
          _mutations_(child).map(changed => value.mapArray(_.updated(index, changed)))
        }.toVector).getOrElse(Vector.empty)
      }
      val invalid = _mutations_(wire) ++ Vector(
        wire.hcursor.downField("schemaVersion").withFocus(_ => Json.fromString("foreign")).top.get,
        wire.hcursor.downField("payload").downField("reference").downField("revision").withFocus(_ => Json.fromInt(1)).top.get,
        wire.hcursor.downField("payload").downField("scopes").withFocus(_ => Json.Null).top.get)

      When("the passive body reader consumes every nested unused annotation without a Human observation")
      val annotated = _extensions_(wire).map(value => J.decodeC(record.reference, value.noSpaces))
      val unobserved = annotated.map(value => value.flatMap(H.admitC(null, _)))
      Then("all declared interpretation data survives, but ignored commandGrant fields create no admission")
      annotated.nonEmpty shouldBe true
      annotated.foreach(value => take(value) shouldBe record)
      unobserved.foreach(_.isSuccess shouldBe false)

      When("the passive body reader consumes each invalid form or a foreign selected reference")
      val rejected = invalid.map(value => J.decodeC(record.reference, value.noSpaces))
      val foreign = J.decodeC(record.reference.copy(identity = "foreign-resolution"), wire.noSpaces)

      Then("actual shape type schema and selected-target contradictions are refused without a second serialized image")
      rejected.nonEmpty shouldBe true
      rejected.foreach(_.isSuccess shouldBe false)
      foreign.isSuccess shouldBe false
    }
  }
}
