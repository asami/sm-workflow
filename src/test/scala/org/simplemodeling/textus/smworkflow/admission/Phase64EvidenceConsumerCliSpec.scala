package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import io.circe.Json
import io.circe.parser.parse
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V

/** Consumer qualification through the installed public JSON contract.
  * Two isolated, specification-owned producers release distinct actual Test
  * outputs. No production Session, accepted result or timestamp is changed.
  * New producer DTOs and implementation classes are deliberately not imported.
  * @since   Oct. 21, 2026
  * @version Oct. 22, 2026
  * @author  ASAMI, Tomoharu
  */
final class Phase64EvidenceConsumerCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  /** Explicitly cut over the two completed local producers' configuration at
    * this installed-consumer boundary. Retain original bytes; never rewrite
    * their Workflow, Common Evidence or Journal records or accept old aliases.
    */
  private def _configureInstalledRuntime(root: Path): Unit = {
    val path = root.resolve(".textus/config.json")
    val original = Files.readString(path)
    val settings = parse(original).fold(error => fail(error.message), identity).asObject.get
    val renamed = Vector(
      "sm-workflow.host.definition" -> "sm-workflow.runtime.definition",
      "sm-workflow.host.evidence.store-name" -> "sm-workflow.external-evidence.store-name",
      "sm-workflow.host.evidence.record-collection" -> "sm-workflow.external-evidence.record-collection",
      "sm-workflow.host.evidence.issuer" -> "sm-workflow.external-evidence.issuer") ++
      Vector("store-name", "selection-collection", "plan-collection", "association-collection",
        "authorization-collection", "decision-collection").map(key =>
        s"sm-workflow.goal-phase.host-records.$key" -> s"sm-workflow.goal-phase.input-records.$key") ++
      Vector("repository-sync", "split-phase").flatMap(family =>
        Vector("store-name", "selection-collection").map(key =>
          s"sm-workflow.$family.host-records.$key" -> s"sm-workflow.$family.input-records.$key"))
    renamed should have size 14
    val definition = parse(settings("sm-workflow.host.definition").flatMap(_.asString).get)
      .fold(error => fail(error.message), identity)
    definition.hcursor.get[String]("schemaVersion").toOption.get shouldBe "sm-workflow.three-workflow-host.v1"
    val currentDefinition = definition.mapObject(_.add("schemaVersion",
      Json.fromString("sm-workflow.three-workflow-runtime.v1")))
    val current = renamed.foldLeft(settings) { case (values, (oldName, newName)) =>
      values.contains(oldName) shouldBe true
      values.contains(newName) shouldBe false
      values.remove(oldName).add(newName, values(oldName).get)
    }.add("sm-workflow.runtime.definition", Json.fromString(currentDefinition.noSpaces))
    Files.writeString(root.resolve(".textus/config-before-installed-consumer.json"), original)
    Files.writeString(root.resolve("installed-consumer-configuration-cutover.json"), Json.obj(
      "provenance" -> Json.fromString("SPECIFICATION-OWNED ISOLATED PRODUCER CONFIGURATION ONLY"),
      "renamedSettings" -> Json.fromValues(renamed.map { case (from, to) =>
        Json.obj("from" -> Json.fromString(from), "to" -> Json.fromString(to)) }),
      "originalDefinitionSchema" -> Json.fromString("sm-workflow.three-workflow-host.v1"),
      "currentDefinitionSchema" -> Json.fromString("sm-workflow.three-workflow-runtime.v1")).spaces2 + "\n")
    Files.writeString(path, Json.fromJsonObject(current).noSpaces)
  }

  private def _call(executable: Path, root: Path, operation: String, input: Json): NativeWorkflowCliFixture.Result = {
    val directory = Files.createTempDirectory(root, "consumer-evidence-call-")
    val stdout = directory.resolve("response.json")
    val stderr = directory.resolve("stderr.log")
    val argv = Vector(executable.toString, "--runtime-root", root.toString, "command",
      "org.simplemodeling.textus.SmWorkflow.EvidenceService." + operation,
      "--input", input.noSpaces, "--json")
    val process = new ProcessBuilder(argv.asJava).directory(root.toFile)
      .redirectOutput(stdout.toFile).redirectError(stderr.toFile).start()
    try {
      process.getOutputStream.close()
      if (!process.waitFor(240, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        process.waitFor()
        fail("Owned isolated Evidence call timed out; effects remain unknown: " + directory)
      }
      NativeWorkflowCliFixture.Result(process.exitValue(),
        NativeWorkflowCliFixture.Invocation(argv, process.pid(), stdout, stderr))
    } finally {
      if (process.isAlive) { process.destroyForcibly(); process.waitFor() }
      process.getInputStream.close()
      process.getErrorStream.close()
    }
  }
  private def _json(result: NativeWorkflowCliFixture.Result): Json = {
    result.exitCode shouldBe 0
    parse(Files.readString(result.invocation.stdout)).fold(error => fail(error.message + ": " + result.invocation.stdout), identity)
  }
  private def _reference(value: V.WorkflowArtifactReference): Json = Json.obj(
    "identity" -> Json.fromString(value.identity), "revision" -> Json.fromString(value.revision))
  private def _readInput(value: V.EvidenceReference): Json = Json.obj(
    "identity" -> Json.fromString(value.identity), "revision" -> Json.fromString(value.revision))
  private def _item(read: V.EvidenceReadResult, compression: String): Json = {
    val metadata = read.metadata.get
    val fields = Vector(
      "sourceReference" -> _reference(metadata.sourceReference),
      "repositoryIdentity" -> Json.fromString(metadata.repositoryIdentity),
      "phaseIdentity" -> Json.fromString(metadata.phaseIdentity),
      "sessionIdentity" -> Json.fromString(metadata.sessionIdentity),
      "outputIdentity" -> Json.fromString("released-consumer-copy"),
      "actionOutcome" -> Json.fromString(metadata.actionOutcome),
      "text" -> Json.fromString(read.text.get), "compression" -> Json.fromString(compression)) ++
      metadata.stepIdentity.toVector.map(value => "stepIdentity" -> Json.fromString(value))
    Json.obj(fields*)
  }
  private def _status(value: Json): String = value.hcursor.get[String]("status").fold(error => fail(error.message), identity)
  private def _field(value: Json, name: String): Json = value.hcursor.downField(name).focus.getOrElse(fail("Missing " + name))

  "The Phase 6.4 installed Evidence consumer" should {
    "retain two released source mappings through a partial gzip batch and independent lifecycle reads" in {
      val configured = sys.env.getOrElse("SM_WORKFLOW_INSTALLED_COMMAND",
        cancel("Explicit installed consumer qualification requires SM_WORKFLOW_INSTALLED_COMMAND"))
      val executable = Path.of(configured)
      executable.isAbsolute shouldBe true
      Files.isRegularFile(executable) shouldBe true
      Files.isExecutable(executable) shouldBe true
      Given("two distinct isolated normal producers, each after its own actual Test and automatic Evidence recording")
      val (root, plan, first) = new GoalPhaseNativeFindingRecordingSpec().produceEvidenceForCli("-p64-e4-first")
      val (other, otherplan, second) = new GoalPhaseNativeFindingRecordingSpec().produceEvidenceForCli("-p64-e4-second")
      NativeWorkflowCliFixture.journalReader(root, plan)
      NativeWorkflowCliFixture.journalReader(other, otherplan)
      Vector(root, other).foreach(_configureInstalledRuntime)
      val originalcalls = Vector(
        _call(executable, root, "readEvidence", _readInput(first.evidence.get)),
        _call(executable, other, "readEvidence", _readInput(second.evidence.get)))
      val originals: Vector[V.EvidenceReadResult] = originalcalls.map(_.business[V.EvidenceReadResult])
      originals.foreach { value =>
        value.status shouldBe "found"
        value.text.get should not be empty
        value.observation.get.status shouldBe "stored"
      }
      originals.head.metadata.get.sourceReference should not be originals(1).metadata.get.sourceReference
      val items = Vector(_item(originals.head, "gzip"), _item(originals(1), "identity"), _item(originals.head, "identity"))
      When("one fresh installed public operation registers both released files and observes the representation collision")
      val batchcall = _call(executable, root, "archiveEvidenceBatch", Json.obj("items" -> Json.fromValues(items)))
      val batch = _json(batchcall)
      Then("partial retains ordered per-file sources outputs Evidence references and separate storage and Journal outcomes")
      _status(batch) shouldBe "partial"
      val results = _field(batch, "items").asArray.get.toVector
      results should have size 3
      results.map(value => _status(_field(value, "state"))) shouldBe Vector("stored", "stored", "conflict")
      results.map(value => _field(value, "source_reference")) shouldBe
        Vector(originals.head, originals(1), originals.head).map(value => _reference(value.metadata.get.sourceReference))
      results.foreach(value => _field(value, "output_identity").asString.get shouldBe "released-consumer-copy")
      results.take(2).foreach(value => _status(_field(_field(value, "state"), "journal")) shouldBe "recorded")
      val references = results.take(2).map(value => _field(_field(value, "state"), "evidence"))
      references.distinct should have size 2
      When("separate installed processes retrieve each payload by its returned Evidence ID")
      val readcalls = references.map(reference => _call(executable, root, "readEvidence", reference))
      val reads: Vector[V.EvidenceReadResult] = readcalls.map(_.business[V.EvidenceReadResult])
      Then("both exact released texts remain readable and the compressed material keeps its selected final gzip name")
      reads.map(_.text) shouldBe originals.map(_.text)
      reads.map(_.metadata.get.sourceReference) shouldBe originals.map(_.metadata.get.sourceReference)
      reads.head.metadata.get.relativePath should endWith(".log.gz")
      reads(1).metadata.get.relativePath should endWith(".log")
      reads.foreach(_.status shouldBe "found")
      When("fresh installed operations describe active lifetime and manage only a reasoned hold")
      val detailcall = _call(executable, root, "describeEvidence", references.head)
      val detail = _json(detailcall)
      _status(detail) shouldBe "active"
      _field(detail, "representation").asString.get shouldBe "gzip"
      _field(detail, "lifecycle").hcursor.get[Option[String]]("expires_at").toOption.get shouldBe None
      val id = references.head.hcursor.get[String]("identity").toOption.get
      val holdcall = _call(executable, root, "manageEvidenceRetention", Json.obj(
        "identity" -> Json.fromString(id), "action" -> Json.fromString("hold"),
        "reason" -> Json.fromString("Inspect isolated consumer material"), "reviewAt" -> Json.fromString("2099-01-01T00:00:00Z")))
      val hold = _json(holdcall)
      Then("held state preserves the original storage observation and currently found payload")
      _status(hold) shouldBe "held"
      _field(_field(hold, "lifecycle"), "hold_reason").asString.get shouldBe "Inspect isolated consumer material"
      _field(_field(hold, "read"), "observation") shouldBe _field(_json(readcalls.head), "observation")
      val metadata = originals.head.metadata.get
      val duebase = Vector("repositoryIdentity" -> Json.fromString(metadata.repositoryIdentity),
        "phaseIdentity" -> Json.fromString(metadata.phaseIdentity), "sessionIdentity" -> Json.fromString(metadata.sessionIdentity),
        "limit" -> Json.fromInt(1))
      When("bounded due operations query the actual host clock and exact original producing scope")
      val duecalls = Vector("expiry", "holdReview").map(kind => _call(executable, root, "findDueEvidence",
        Json.obj((duebase :+ ("kind" -> Json.fromString(kind)))*)))
      Then("an active held item with a future review is not reported due or deleted")
      duecalls.map(_json).foreach { result =>
        _status(result) shouldBe "found"
        _field(result, "items").asArray.get shouldBe empty
        _field(result, "has_more").asBoolean.get shouldBe false
      }
      When("an explicit reasoned release returns the item to active no-expiry")
      val releasecall = _call(executable, root, "manageEvidenceRetention", Json.obj("identity" -> Json.fromString(id),
        "action" -> Json.fromString("release"), "reason" -> Json.fromString("Isolated inspection finished")))
      val released = _json(releasecall)
      Then("no source Action was replayed and the retained storage observation and payload stay unchanged")
      _status(released) shouldBe "active"
      _field(_field(released, "read"), "observation") shouldBe _field(_json(readcalls.head), "observation")
      _field(_field(released, "read"), "text").asString shouldBe reads.head.text
      Vector(root, other).foreach(directory => Files.readString(directory.resolve("sbt-executions.txt")).linesIterator.size shouldBe 1)
      val calls = originalcalls ++ Vector(batchcall) ++ readcalls ++ Vector(detailcall, holdcall) ++ duecalls ++ Vector(releasecall)
      calls.map(_.invocation.pid).distinct should have size calls.size
      Files.writeString(root.resolve("phase64-consumer-qualification.json"), Json.obj(
        "provenance" -> Json.fromString("SPECIFICATION-OWNED ISOLATED PRODUCERS; ACTUAL INSTALLED PUBLIC OPERATIONS"),
        "hostRoot" -> Json.fromString(root.toString), "otherProducerRoot" -> Json.fromString(other.toString),
        "sourceCount" -> Json.fromInt(2), "calls" -> Json.fromValues(calls.map(call => Json.obj(
          "pid" -> Json.fromLong(call.invocation.pid), "exitCode" -> Json.fromInt(call.exitCode),
          "stdout" -> Json.fromString(call.invocation.stdout.toString), "stderr" -> Json.fromString(call.invocation.stderr.toString))))).spaces2 + "\n")
    }
  }
}
