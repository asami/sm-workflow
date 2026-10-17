package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.host.NativeHostSplitStartInput as Main
import NativeHostEvidence.Purpose
import NativeHostStartPreparationFixture.*
import SplitPhaseExplicitPartitionFixture.take
import SplitPhaseTypedStartFixture as F

/** Finite file and metadata inputs are specification-owned, not genuine consent.
  * Parent-selected validation owns execution of these examples.
  * @since   Oct. 14, 2026
  * @version Oct. 15, 2026
  * @author  ASAMI, Tomoharu
  */
final class NativeHostSplitStartInputSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with NativeHostPreparationManagedFixture {
  private def _json(value: Record): Json = io.circe.parser.parse(value.toJsonStringC.toOption.get).toOption.get
  private val _collection = "entity:smworkflow.cli.host_evidence"
  private def _arguments(owner: String): Map[String, String] = Map("component-instance" -> owner,
    "allocation-reference" -> "allocation-reference.json", "allocation" -> "allocation.json", "source" -> "source.json",
    "reference" -> "reference.json", "participant" -> participant, "issuer" -> issuer,
    "occurred-at" -> now.minusSeconds(60).toString, "collection" -> _collection)
  private def _files(start: SplitPhaseTypedStartPreparation.Record): Map[String, String] = {
    Map("allocation-reference.json" -> take(allocationReference(start).toRecord().toJsonStringC),
      "allocation.json" -> take(allocationRow(start).toJsonStringC), "source.json" -> take(sourceRow(start).toJsonStringC),
      "reference.json" -> take(reference.toRecord().toJsonStringC))
  }
  private def _read(files: Map[String, String])(path: String): Consequence[String] = {
    files.get(path).map(Consequence.success).getOrElse(Consequence.stateConflict("Specification file absent"))
  }
  private def _replace(args: Map[String, String], key: String, value: String): Map[String, String] = {
    args.updated(key, value)
  }
  private def _row(value: Record): Json = {
    _json(value).hcursor.downField("datastore").downArray.downField("records").downArray.focus.get
  }
  private def _evidence(text: Record): NativeHostEvidence.Evidence = {
    take(NativeHostEvidence.decodeStorageC(_row(text).hcursor.get[String](NativeHostEvidence.storageField).toOption.get))
  }

  "T1 Actual selected file intake" should afterWord("in spec:native-host-start-preparation, example:T1, rules:R1,R2,R3,R5, phase:6") {
    "emit the same single standard importer row as public preparation with or without a proposal" in {
      Given("four saved current specification-owned rows and references and the actual owning component with explicit operator metadata")
      withCore { call =>
        val owner = call.core.component.get.core.instanceId.canonicalKey
        When("the finite tool reads each selected PREVIEW APPLY and optional-proposal file set")
        val seeds = starts.map(start => take(Main.prepareC(_arguments(owner), _read(_files(start)))))
        val expected = starts.map(start => take(prepareC(owner, start)))
        Then("each standard configured importer contains exactly the selected public preparation input")
        seeds.zip(expected).zip(starts).foreach { (pair, start) =>
          val (seed, prepared) = pair
          val bootstrap = _json(seed)
          bootstrap.hcursor.downField("datastore").focus.get.asArray.get.size shouldBe 1
          bootstrap.hcursor.downField("datastore").downArray.get[String]("collection").toOption shouldBe Some(_collection)
          bootstrap.hcursor.downField("datastore").downArray.downField("records").focus.get.asArray.get.size shouldBe 1
          _row(seed).hcursor.get[String]("id").toOption shouldBe Some(prepared.recordIdentity)
          _row(seed).hcursor.get[String](NativeHostEvidence.storageField).toOption shouldBe prepared.record.getString(NativeHostEvidence.storageField)
          _evidence(seed).lookupIdentity shouldBe startLookup(start)
          _evidence(seed).purpose shouldBe Purpose.SplitStart.identity
          _evidence(seed).participant shouldBe participant
        }
        Consequence.unit
      }
    }
  }

  "T2 Explicit invalid input" should afterWord("in spec:native-host-start-preparation, example:T2, rules:R1,R3,R5,R6, phase:6") {
    "refuse malformed metadata files current schemas required references time and collection without successful JSON" in {
      Given("a saved current specification allocation and Source plus bounded malformed explicit arguments and file variants")
      withCore { call =>
        val args = _arguments(call.core.component.get.core.instanceId.canonicalKey)
        val files = _files(F.record)
        val badArguments: Vector[Map[String, String]] = Vector(null, Map.empty[String, String],
          args.updated("unknown", "value"), args.updated("command", "start")) ++
          args.keys.toVector.map(args - _) ++ args.keys.toVector.flatMap(key =>
            Vector[String](null, "", " ", " leading", "control\nvalue").map(value => args.updated(key, value))) ++ Vector(
          args.updated("occurred-at", "invalid-time"),
          args.updated("collection", "smworkflow.cli.host_evidence"),
          args.updated("collection", "entity:smworkflow..host_evidence"),
          args.updated("collection", "entity:smworkflow.cli.host-evidence"),
          args.updated("collection", "entity:1workflow.cli.host_evidence"),
          args.updated("collection", "entity:smworkflow.cli.host_evidence.extra"))
        val af = SplitPhaseTypedStartJsonV1.storageField
        val sf = SplitPhaseExplicitPlanningInputJsonV1.storageField
        val badFiles = files.keys.toVector.map(files - _) ++ Vector(files.updated("allocation.json", "not-json"),
          files.updated("source.json", "{}"), files.updated("allocation-reference.json", "{}"),
          files.updated("reference.json", "{\"identity\":null,\"revision\":\"1\"}"),
          files.updated("allocation-reference.json", "{\"identity\":\"selected\",\"revision\":null}"),
          files.updated("allocation.json", take(org.goldenport.record.Record.data(af -> "unsupported:").toJsonStringC)),
          files.updated("source.json", take(org.goldenport.record.Record.data(sf -> "unsupported:").toJsonStringC)),
          files.updated("source.json", take(sourceRow(F.record.copy(source = F.source.copy(
            reference = F.source.reference.copy(identity = "foreign-source")))).toJsonStringC)))
        When("ordinary argument and typed file readers receive the malformed supplied values")
        val failures = badArguments.map(Main.prepareC(_, _read(files))) ++ badFiles.map(input => Main.prepareC(args, _read(input)))
        Then("every invalid case refuses without a successful importer result")
        failures.foreach(_.isSuccess shouldBe false)
        Consequence.unit
      }
    }
  }

  "T3 Exact files and selected scope" should afterWord("in spec:native-host-start-preparation, example:T3, rules:R2,R4,R5,R6, phase:6") {
    "read only the four selected files and discard inert annotations without inferring clock consent or permissions" in {
      Given("current specification allocation and Source files with inert row annotations and explicit owner operator time and dotted address")
      withCore { call =>
        val owner = call.core.component.get.core.instanceId.canonicalKey
        val args = _arguments(owner)
        val files = _files(F.record)
        val annotated = files.updated("allocation.json", json(allocationRow(F.record)).mapObject(
          _.add("grant", Json.True).add("clock", Json.Null)).noSpaces).updated("source.json",
          json(sourceRow(F.record)).mapObject(_.add("operatorApproved", Json.True)).noSpaces)
        var reads = Vector.empty[String]
        When("the finite tool reads the explicit files and emits the selected current payload")
        val seed = take(Main.prepareC(_replace(args, "collection", "entity:Major_1.Minor_2.Host_3"), path => {
          reads = reads :+ path
          _read(annotated)(path)
        }))
        val original = take(Main.prepareC(args, _read(files)))
        val unsupported = Main.prepareC(_replace(args, "collection", "ENTITY:Major_1.Minor_2.Host_3"), _read(files))
        val booleanReferenceFiles = files.updated("reference.json", "{\"identity\":true,\"revision\":\"1\"}")
        val converted = take(Main.prepareC(args, _read(booleanReferenceFiles)))
        Then("only named reads occur and the existing generated String conversion and native selected target remain unchanged")
        reads shouldBe Vector("allocation-reference.json", "allocation.json", "source.json", "reference.json")
        _json(seed).hcursor.downField("datastore").downArray.get[String]("collection").toOption shouldBe
          Some("entity:Major_1.Minor_2.Host_3")
        _row(seed) shouldBe _row(original)
        _evidence(seed).binding shouldBe evidence(take(prepareC(owner, F.record))).binding
        _evidence(seed).occurredAt shouldBe now.minusSeconds(60).toString
        _evidence(seed).participant shouldBe participant
        _evidence(seed).binding.noSpaces should not include "grant"
        _evidence(converted).reference shouldBe "true"
        unsupported.isSuccess shouldBe false
        Consequence.unit
      }
    }
  }

}
