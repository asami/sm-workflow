package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.preparation.NativeRepositoryEffectInput as Main
import NativeExternalEvidence.Purpose
import NativeRepositoryEffectPreparationFixture.*
import RepositorySyncNativeConvergence.Effect
import SplitPhaseExplicitPartitionFixture.take

/** Saved files and metadata inputs are specification-owned, not genuine consent.
  * Parent-selected validation owns execution of these finite examples.
  * @since   Oct. 14, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class NativeRepositoryEffectInputSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with NativePreparationManagedFixture {
  private def _json(value: Record): Json = io.circe.parser.parse(value.toJsonStringC.toOption.get).toOption.get
  private val _collection = "entity:smworkflow.cli.host_evidence"
  private def _arguments(owner: String, effect: Effect = Effect.Push): Map[String, String] = Map(
    "component-instance" -> owner, "selection" -> "selection.json", "target" -> "target.json", "effect" -> effect.toString,
    "reference" -> "reference.json", "participant" -> participant, "issuer" -> issuer,
    "occurred-at" -> now.minusSeconds(60).toString, "collection" -> _collection)
  private def _files: Map[String, String] = Map("selection.json" -> take(selectionRow().toJsonStringC),
    "target.json" -> take(targetRow().toJsonStringC), "reference.json" -> take(reference.toRecord().toJsonStringC))
  private def _read(files: Map[String, String])(path: String): Consequence[String] = {
    files.get(path).map(Consequence.success).getOrElse(Consequence.stateConflict("Specification file absent"))
  }
  private def _replace(args: Map[String, String], key: String, value: String): Map[String, String] = {
    args.updated(key, value)
  }
  private def _row(value: Record): Json = {
    _json(value).hcursor.downField("datastore").downArray.downField("records").downArray.focus.get
  }
  private def _evidence(text: Record): NativeExternalEvidence.Evidence = {
    take(NativeExternalEvidence.decodeStorageC(_row(text).hcursor.get[String](NativeExternalEvidence.storageField).toOption.get))
  }

  "T1 Current selected three-file intake" should afterWord("in spec:native-host-repository-effect-preparation, example:T1, rules:R1,R2,R3,R5, phase:6") {
    "emit exactly the same single standard importer row as public preparation for every exact effect" in {
      Given("three saved current specification-owned rows with the actual component owner and explicit operator metadata")
      withCore { call =>
        val owner = call.core.component.get.core.instanceId.canonicalKey
        When("the finite tool consumes each single-effect file set")
        val seeds = effects.map(effect => take(Main.prepareC(_arguments(owner, effect), _read(_files))))
        val expected = effects.map(effect => take(prepareC(owner, effect)))
        Then("each standard importer contains exactly one collection and one matching public row")
        seeds.zip(expected).zip(effects).foreach { (pair, effect) =>
          val (seed, prepared) = pair
          val bootstrap = _json(seed)
          bootstrap.hcursor.downField("datastore").focus.get.asArray.get.size shouldBe 1
          bootstrap.hcursor.downField("datastore").downArray.get[String]("collection").toOption shouldBe Some(_collection)
          bootstrap.hcursor.downField("datastore").downArray.downField("records").focus.get.asArray.get.size shouldBe 1
          _row(seed).hcursor.get[String]("id").toOption shouldBe Some(prepared.recordIdentity)
          _row(seed).hcursor.get[String](NativeExternalEvidence.storageField).toOption shouldBe prepared.record.getString(NativeExternalEvidence.storageField)
          _evidence(seed).lookupIdentity shouldBe effectLookup(effect)
          _evidence(seed).purpose shouldBe Purpose.RepositoryEffect.identity
          _evidence(seed).binding shouldBe evidence(prepared).binding
          _evidence(seed).reference shouldBe reference.identity
          _evidence(seed).revision shouldBe reference.revision
          _evidence(seed).participant shouldBe participant
          _evidence(seed).issuer shouldBe issuer
        }
        Consequence.unit
      }
    }
  }

  "T2 Invalid explicit input" should afterWord("in spec:native-host-repository-effect-preparation, example:T2, rules:R1,R3,R5,R6, phase:6") {
    "refuse malformed metadata files selection target effect reference clock and collection without successful JSON" in {
      Given("a saved current specification selection, physical target and bounded malformed explicit values")
      withCore { call =>
        val args = _arguments(call.core.component.get.core.instanceId.canonicalKey)
        val files = _files
        val malformed: Vector[Map[String, String]] = Vector(null, Map.empty[String, String],
          args.updated("unknown", "value"), args.updated("command", "start")) ++
          args.keys.toVector.map(args - _) ++ args.keys.toVector.flatMap(key =>
            Vector[String](null, "", " ", " leading", "control\nvalue").map(value => args.updated(key, value))) ++ Vector(
          args.updated("occurred-at", "invalid-time"),
          args.updated("collection", "smworkflow.cli.host_evidence"),
          args.updated("collection", "entity:smworkflow..host_evidence"),
          args.updated("collection", "entity:smworkflow.cli.host-evidence"),
          args.updated("collection", "entity:1workflow.cli.host_evidence"),
          args.updated("collection", "entity:smworkflow.cli.host_evidence.extra")) ++ Vector(args.updated("effect", "push"), args.updated("effect", "All"))
        val targetjson = json(targetRow())
        val badtargets = targetjson.asObject.get.keys.toVector.flatMap(field => Vector(
          targetjson.mapObject(_.remove(field)), targetjson.mapObject(_.add(field, Json.True)), targetjson.mapObject(_.add(field, Json.Null)))) ++
          Vector(json(targetRow(target.copy(root = "relative"))), json(targetRow(target.copy(root = "/specification/../repository"))))
        val badselections = Vector(org.goldenport.record.Record.empty, org.goldenport.record.Record.data("selectionPayload" -> true),
          org.goldenport.record.Record.data("selectionPayload" -> "unsupported:"),
          changeSelection(_.mapObject(_.add("schemaVersion", Json.fromString("unsupported")))),
          changeSelection(_.mapObject(_.remove("startOperation"))),
          changeSelection(_.mapObject(_.add("startOperation", Json.obj("service" -> Json.fromString("GoalPhaseService"),
            "operation" -> Json.fromString("startGoalPhase"))))),
          changeSelection(_.mapObject(_.add("selectedInput", Json.Null))),
          changeSelection(original => NativeHumanEvidenceFixture.alter(original, Vector("selectedInput"))(
            _.mapObject(_.add("typeIdentity", Json.fromString("GoalPhaseStartInput"))))),
          changeSelection(original => NativeHumanEvidenceFixture.alter(original, Vector("selectedInput", "value"))(
            _.mapObject(_.add("repositoryIdentity", Json.True)))))
        val badfiles = files.keys.toVector.map(files - _) ++ Vector(files.updated("selection.json", "not-json"),
          files.updated("target.json", "[]"), files.updated("reference.json", "{}"),
          files.updated("reference.json", "{\"identity\":null,\"revision\":\"1\"}"),
          files.updated("reference.json", "{\"identity\":\"reference\",\"revision\":null}")) ++
          badtargets.map(value => files.updated("target.json", value.noSpaces)) ++
          badselections.map(value => files.updated("selection.json", take(value.toJsonStringC)))
        When("the finite tool consumes each malformed supplied argument or file set")
        val failures = malformed.map(Main.prepareC(_, _read(files))) ++ badfiles.map(input => Main.prepareC(args, _read(input)))
        Then("every invalid input fails without any successful importer result")
        failures.foreach(_.isSuccess shouldBe false)
        Consequence.unit
      }
    }
  }

  "T3 Exact files and inert annotations" should afterWord("in spec:native-host-repository-effect-preparation, example:T3, rules:R2,R4,R5,R6, phase:6") {
    "read three explicit files in order preserve generated reference conversion and retain selected metadata without extra reads" in {
      Given("annotated specification selection and physical target files with explicit metadata time and dotted collection")
      withCore { call =>
        val owner = call.core.component.get.core.instanceId.canonicalKey
        val args = _arguments(owner)
        val files = _files
        val annotated = files.updated("selection.json", json(selectionRow()).mapObject(_.add("grant", Json.True)).noSpaces)
          .updated("target.json", json(targetRow()).mapObject(_.add("operatorApproved", Json.True).add("clock", Json.Null)).noSpaces)
        var reads = Vector.empty[String]
        When("the tool consumes only selected paths and emits current payloads with existing generated reference conversions")
        val seed = take(Main.prepareC(_replace(args, "collection", "entity:Major_1.Minor_2.Host_3"), path => {
          reads = reads :+ path
          _read(annotated)(path)
        }))
        val original = take(Main.prepareC(args, _read(files)))
        val converted = take(Main.prepareC(args, _read(files.updated("reference.json", "{\"identity\":true,\"revision\":\"1\"}"))))
        val unsupported = Main.prepareC(_replace(args, "collection", "ENTITY:Major_1.Minor_2.Host_3"), _read(files))
        Then("file order, strict dotted grammar, current metadata and reference conversion remain explicit and annotations grant nothing")
        reads shouldBe Vector("selection.json", "target.json", "reference.json")
        _json(seed).hcursor.downField("datastore").downArray.get[String]("collection").toOption shouldBe
          Some("entity:Major_1.Minor_2.Host_3")
        _row(seed) shouldBe _row(original)
        _evidence(seed).binding shouldBe evidence(take(prepareC(owner, Effect.Push))).binding
        _evidence(seed).binding.noSpaces should not include "grant"
        _evidence(seed).binding.noSpaces should not include "operatorApproved"
        _evidence(seed).occurredAt shouldBe now.minusSeconds(60).toString
        _evidence(seed).participant shouldBe participant
        _evidence(converted).reference shouldBe "true"
        unsupported.isSuccess shouldBe false
        Consequence.unit
      }
    }
  }

}
