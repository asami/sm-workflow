package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.host.{NativeHostAuthorityInput as Main, NativeHostAuthorityPreparation as Preparation}
import org.simplemodeling.textus.smworkflow.value as V
import NativeHostEvidence.Purpose
import NativeHostAuthorityPreparationFixture.*
import SplitPhaseExplicitPartitionFixture.take

/** Finite tool specifications use explicit specification-owned saved files.
  * Fixture process success supplies no genuine operator or task provenance.
  * @since   Oct. 14, 2026
  * @version Oct. 15, 2026
  * @author  ASAMI, Tomoharu
  */
final class NativeHostAuthorityInputSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with NativeHostPreparationManagedFixture {
  private def _json(value: Record): Json = io.circe.parser.parse(value.toJsonStringC.toOption.get).toOption.get
  private val _collection = "entity:smworkflow.cli.host_evidence"
  private val _arguments = Map("request" -> "request.json", "reference" -> "reference.json", "participant" -> participant,
    "issuer" -> issuer, "occurred-at" -> now.minusSeconds(60).toString, "collection" -> _collection)
  private def _files(output: V.GoalPhaseAdvanceResult): Map[String, String] = {
    Map("request.json" -> take(output.toRecord().toJsonStringC), "reference.json" -> take(reference.toRecord().toJsonStringC))
  }
  private def _normal_files(output: V.GoalPhaseAdvanceResult): Map[String, String] = {
    _files(output).updated("request.json", normalCommandJson(output))
  }
  private def _read(files: Map[String, String])(path: String): Consequence[String] = {
    files.get(path).map(Consequence.success).getOrElse(Consequence.stateConflict("Specification file absent"))
  }
  private def _row(value: Record): Json = {
    _json(value).hcursor.downField("datastore").downArray.downField("records").downArray.focus.get
  }
  private def _evidence(text: Record): NativeHostEvidence.Evidence = {
    take(NativeHostEvidence.decodeStorageC(_row(text).hcursor.get[String](NativeHostEvidence.storageField).toOption.get))
  }
  private def _replace(key: String, value: String): Map[String, String] = {
    _arguments.updated(key, value)
  }

  "T1 Step and Phase Wait intake" should afterWord("in spec:native-host-authority-preparation, example:T1, rules:R1,R2,R3,R4, phase:6") {
    "export the same standard configured importer row as public passive preparation for both native purposes" in {
      Given("actual owning-core projections of specification native Step and Phase Inputs with explicit operator metadata")
      withCore { call =>
        val outputs = purposes.map(purpose => take(outputC(call.core, purpose)))
        When("the finite tool reads the saved full Goal Wait JSON and supplied reference participant issuer and historical time")
        val seeds = outputs.map(output => take(Main.prepareC(_arguments, _read(_files(output)))))
        val prepared = outputs.map(output => take(Preparation.goalC(output.authorityContext.get, reference,
          participant, issuer, now.minusSeconds(60))))
        Then("each single standard EntityCollection row preserves the existing identity and current v7 payload")
        seeds.zip(prepared).zip(purposes).foreach { (pair, purpose) =>
          val (seed, expected) = pair
          val bootstrap = _json(seed)
          bootstrap.hcursor.downField("datastore").focus.get.asArray.get.size shouldBe 1
          bootstrap.hcursor.downField("datastore").downArray.get[String]("collection").toOption shouldBe Some(_collection)
          bootstrap.hcursor.downField("datastore").downArray.downField("records").focus.get.asArray.get.size shouldBe 1
          _row(seed).hcursor.get[String]("id").toOption shouldBe Some(expected.recordIdentity)
          _row(seed).hcursor.get[String](NativeHostEvidence.storageField).toOption shouldBe expected.record.getString(NativeHostEvidence.storageField)
          _evidence(seed).purpose shouldBe purpose.identity
          _evidence(seed).lookupIdentity shouldBe authorityKey(purpose)
          _evidence(seed).participant shouldBe participant
          _evidence(seed).reference shouldBe reference.identity
        }
        Consequence.unit
      }
    }
  }

  "T2 Explicit invalid input" should afterWord("in spec:native-host-authority-preparation, example:T2, rules:R3,R4,R6, phase:6") {
    "refuse malformed metadata files types metadata time collection and nonclosing outcomes without successful JSON" in {
      Given("a full specification Step Wait and bounded invalid CLI typed-file and output variants")
      withCore { call =>
        val output = take(outputC(call.core, Purpose.StepClosure))
        val files = _files(output)
        val malformedarguments: Vector[Map[String, String]] = Vector(null, Map.empty[String, String],
          _arguments.updated("unknown", "value"), _arguments.updated("command", "start")) ++
          _arguments.keys.toVector.map(_arguments - _) ++ _arguments.keys.toVector.flatMap(key =>
            Vector[String](null, "", " ", " leading", "control\nvalue").map(value => _arguments.updated(key, value))) ++ Vector(
          _arguments.updated("occurred-at", "invalid-time"),
          _arguments.updated("collection", "smworkflow.cli.host_evidence"),
          _arguments.updated("collection", "entity:smworkflow..host_evidence"),
          _arguments.updated("collection", "entity:smworkflow.cli.host-evidence"),
          _arguments.updated("collection", "entity:1workflow.cli.host_evidence"),
          _arguments.updated("collection", "entity:smworkflow.cli.host_evidence.extra"))
        val malformedfiles = Vector(files - "request.json", files - "reference.json", files.updated("request.json", "not-json"),
          files.updated("request.json", "{}"), files.updated("reference.json", "{}"),
          files.updated("reference.json", "{\"identity\":null,\"revision\":\"1\"}"),
          files.updated("reference.json", take(reference.copy(identity = "").toRecord().toJsonStringC)),
          files.updated("reference.json", take(reference.copy(revision = "").toRecord().toJsonStringC)))
        val context = output.authorityContext.get
        val malformedoutputs = Vector(output.copy(kind = "Request"), output.copy(kind = "Human"), output.copy(kind = "Terminal"),
          output.copy(authorityContext = None), output.copy(authorityContext = Some(context.copy(componentInstance = ""))),
          output.copy(authorityContext = Some(context.copy(scope = _invalid_generation(context.scope)))),
          output.copy(authorityContext = Some(context.copy(purpose = Purpose.RepositoryEffect.identity))),
          output.copy(authorityContext = Some(context.copy(purpose = Purpose.SplitStart.identity))),
          output.copy(authorityContext = Some(context.copy(purpose = Purpose.GoalReview.identity))),
          output.copy(authorityContext = Some(context.copy(purpose = Purpose.GoalHuman.identity))))
        When("the finite adapter receives each explicit failure through the ordinary typed readers")
        val failures = malformedarguments.map(Main.prepareC(_, _read(files))) ++
          malformedfiles.map(inputs => Main.prepareC(_arguments, _read(inputs))) ++
          malformedoutputs.map(value => Main.prepareC(_arguments, _read(_files(value))))
        Then("every case returns a failure and no successful bootstrap input")
        failures.foreach(_.isSuccess shouldBe false)
        Consequence.unit
      }
    }
  }
  private def _invalid_generation(scope: org.goldenport.record.Record): org.goldenport.record.Record = {
    record(json(scope).mapObject(_.add("generation", Json.True)))
  }

  "T3 Strict collection and selected scope" should afterWord("in spec:native-host-authority-preparation, example:T3, rules:R2,R3,R4,R6, phase:6") {
    "retain the exact dotted collection grammar and selected scope without choosing identities clock or permissions" in {
      Given("a specification Phase Wait with inert annotations and an explicitly selected dotted importer address")
      withCore { call =>
        val output = take(outputC(call.core, Purpose.PhaseClosure))
        val context = output.authorityContext.get
        val annotated = output.copy(authorityContext = Some(context.copy(scope = record(json(context.scope).mapObject(
          _.add("grant", Json.True).add("clock", Json.True).add("command", Json.Null))))))
        val files = _files(annotated)
        var reads = Vector.empty[String]
        When("the finite tool reads only explicitly named files and constructs the declared entity collection without aliases")
        val seed = take(Main.prepareC(_replace("collection", "entity:Major_1.Minor_2.Host_3"), path => {
          reads = reads :+ path
          _read(files)(path)
        }))
        val original = take(Main.prepareC(_arguments, _read(_files(output))))
        val unsupported = Main.prepareC(_replace("collection", "ENTITY:Major_1.Minor_2.Host_3"), _read(files))
        Then("supplied metadata and current selected payload are unchanged and no extra input or permission is inferred")
        reads shouldBe Vector("request.json", "reference.json")
        _json(seed).hcursor.downField("datastore").downArray.get[String]("collection").toOption shouldBe
          Some("entity:Major_1.Minor_2.Host_3")
        _row(seed) shouldBe _row(original)
        _evidence(seed).occurredAt shouldBe now.minusSeconds(60).toString
        _evidence(seed).binding.noSpaces should not include "grant"
        _evidence(seed).binding.noSpaces should not include "clock"
        unsupported.isSuccess shouldBe false
        Consequence.unit
      }
    }
  }

  "T5 Normal full command JSON intake" should afterWord("in spec:native-host-authority-preparation, example:T5, rules:R1,R2,R3,R4,R6, phase:6") {
    "export the canonical native binding from actual formatted full Step and Phase Wait outputs" in {
      Given("actual owning-core Step and Phase projections saved as normal full command JSON with explicit metadata")
      withCore { call =>
        val inputs = purposes.map { purpose =>
          (purpose, _normal_files(take(outputC(call.core, purpose))), take(bindingC(call.core, purpose)))
        }
        When("Main reads the formatter's full output through RecordDecoder and the strict generated Goal reader")
        val seeds = inputs.map { (purpose, files, binding) => (purpose, take(Main.prepareC(_arguments, _read(files))), binding) }
        Then("both standard v7 rows match the canonical native target, lookup, identity and all supplied metadata")
        seeds.foreach { (purpose, seed, binding) =>
          val actual = _evidence(seed)
          actual.binding shouldBe binding
          actual.purpose shouldBe purpose.identity
          actual.lookupIdentity shouldBe authorityKey(purpose)
          actual.reference shouldBe reference.identity
          actual.revision shouldBe reference.revision
          actual.participant shouldBe participant
          actual.issuer shouldBe issuer
          actual.occurredAt shouldBe now.minusSeconds(60).toString
          _row(seed).hcursor.get[String]("id").toOption shouldBe Some(NativeHostEvidence.recordIdentity(purpose, authorityKey(purpose)))
        }
        Consequence.unit
      }
    }
  }
}
