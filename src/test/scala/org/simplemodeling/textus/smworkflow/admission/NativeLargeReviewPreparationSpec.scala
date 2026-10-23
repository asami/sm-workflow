package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.{ComponentRecordStorage, DataStore}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.goldenport.record.io.RecordDecoder
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.value as V
import NativePreparationFixture.json
import SplitPhaseExplicitPartitionFixture.take

/** Complete specification-owned ReviewPhase files through ordinary public preparation.
  * Supplied reviewer and execution facts confer no independent review or execution authority.
  * These episodes neither provision importer output nor operate a Workflow Session.
  * @since   Oct. 23, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
final class NativeLargeReviewPreparationSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with NativePreparationOperationFixture {
  private def _field(input: Record, key: String, value: String): Record =
    input.removeKeys(Set(key)) ++ Record.data(key -> value)

  private def _large_case(episode: PreparationEpisode): PreparedCase = {
    val selected = episode.cases("prepareEvidence").find(_.label == "ReviewPhase").get
    val original = parse(Files.readString(Path.of(URI.create(selected.input.getString("request").get)), UTF_8))
      .fold(error => fail(error.message), identity)
    val message = original.hcursor.get[String]("message").toOption.get
    val expanded = original.mapObject(_.add("message", Json.fromString(message + ("あ" * (7 * 1024 * 1024)))))
    val reference = episode.save("large-review-phase-request", expanded.noSpaces)
    val size = Files.size(Path.of(URI.create(reference)))
    size should be > 16777216L
    size should be < 134217728L
    expanded.mapObject(_.remove("message")) == original.mapObject(_.remove("message")) shouldBe true
    selected.copy(input = _field(selected.input, "request", reference))
  }

  private def _response(value: Consequence[OperationResponse]): Record = take(value) match {
    case OperationResponse.Json(result) => take(new RecordDecoder().json(result.noSpaces))
    case _ => fail("Expected direct structured importer output")
  }

  private def _rows(value: Record): Vector[Json] = json(value).hcursor.get[Vector[Json]]("datastore").toOption.get
    .flatMap(_.hcursor.get[Vector[Json]]("records").toOption.get)

  private def _assert_evidence(actual: Record, selected: PreparedCase): String = {
    json(actual) shouldBe json(selected.expected)
    actual.keySet shouldBe Set("datastore")
    val rows = _rows(actual)
    rows.size shouldBe 1
    val row = rows.head
    row.asObject.get.keys.toSet shouldBe Set("id", NativeExternalEvidence.storageField)
    val raw = row.hcursor.get[String](NativeExternalEvidence.storageField).toOption.get
    val decoded = take(NativeExternalEvidence.decodeStorageC(raw))
    val request = take(V.GoalPhaseAdvanceResult.createC(take(new RecordDecoder().json(Files.readString(
      Path.of(URI.create(selected.input.getString("request").get)), UTF_8)))))
    val context = request.work.get.review.get.reviewContext.get
    val stored = parse(raw.substring(NativeExternalEvidence.schema.length + 1)).toOption.get
    val binding = stored.hcursor.downField("binding")
    val result = binding.downField("attestation").downField("result")
    val expectedraw = _rows(selected.expected).head.hcursor.get[String](NativeExternalEvidence.storageField).toOption.get
    val expected = parse(expectedraw.substring(NativeExternalEvidence.schema.length + 1)).toOption.get
    binding.downField("attestation").downField("target").focus shouldBe
      expected.hcursor.downField("binding").downField("attestation").downField("target").focus
    decoded.purpose shouldBe NativeExternalEvidence.Purpose.GoalReview.identity
    binding.get[String]("componentInstance") shouldBe Right(context.componentInstance)
    result.get[String]("runId") shouldBe Right(context.completion.runId)
    result.get[String]("continuationId") shouldBe Right(context.completion.continuationId)
    result.get[String]("typeIdentity") shouldBe Right(context.completion.typeIdentity)
    result.downField("handle").get[String]("instanceIdentity") shouldBe Right("instance")
    result.downField("handle").get[String]("workflowIdentity") shouldBe Right("GoalPhaseWorkflow")
    row.hcursor.get[String]("id").toOption.get
  }

  private def _configure_limit(root: Path, cap: Option[Int]): Unit = {
    val path = root.resolve(".textus/config.json")
    val current = parse(Files.readString(path, UTF_8)).fold(error => fail(error.message), identity)
    val updated = current.mapObject { fields =>
      val omitted = fields.remove("textus.resource.read.max-bytes")
      cap.fold(omitted)(value => omitted.add("textus.resource.read.max-bytes", Json.fromString(value.toString)))
    }
    Files.writeString(path, updated.noSpaces, UTF_8)
  }

  private def _assert_cli_failure(result: NativeWorkflowCliFixture.Result): Unit = {
    result.exitCode should not be 0
    parse(Files.readString(result.invocation.stdout, UTF_8)).toOption
      .flatMap(_.hcursor.get[Vector[Json]]("datastore").toOption) shouldBe None
  }

  "P116 Complete large ReviewPhase preparation" should {
    "apply default and old runtime ceilings and preserve complete bound passive evidence at 128 MiB" in {
      Vector(None, Some(16777216), Some(134217728)).foreach { cap =>
        Given("a specification-owned complete ReviewPhase Request above 16 MiB and the ordinary runtime limit " + cap)
        with_preparation_limit(cap) { episode =>
          val selected = _large_case(episode)
          When("the public prepareEvidence Operation reads the actual complete UTF-8 Request file")
          val result = episode.invoke("prepareEvidence", selected.input)
          Then("default and 16 MiB reject with runtime-byte-limit evidence while 128 MiB retains the exact native binding")
          if (cap.contains(134217728)) {
            val identity = _assert_evidence(_response(result), selected)
            Given("only passive importer output has returned without provisioning")
            episode.project { call =>
              When("the existing managed reader loads the returned evidence identity")
              val saved = take(ComponentRecordStorage.bindForCallC(call.core, "workflow",
                DataStore.CollectionId.EntityStore(EntityCollectionId("smworkflow", "cli", "host_evidence")),
                DataStore.StringEntryId(identity)).flatMap(_.loadC()))
              Then("no evidence row has been saved by preparation")
              saved shouldBe None
              Consequence.unit
            }
          } else {
            result.isSuccess shouldBe false
            result.toString should include("runtime byte limit")
          }
        }
      }
    }

    "qualify the installed passive CLI with large complete files and unchanged root and JSON restrictions" in {
      Given("an explicitly selected installed command and a fresh isolated host without selected-input bootstrap")
      val executable = Path.of(sys.props.getOrElse("smworkflow.spec.installedCommand",
        cancel("Installed large ReviewPhase preparation requires -Dsmworkflow.spec.installedCommand=/absolute/executable")))
      executable.isAbsolute shouldBe true
      Files.isRegularFile(executable) shouldBe true
      Files.isExecutable(executable) shouldBe true
      withPreparation() { episode =>
        val selected = _large_case(episode)
        val cli = NativeWorkflowCliFixture.fresh("large-review-preparation", resourceRoots = Vector(episode.root),
          bootstrapSelectedInput = false)
        def _invoke_(input: Record): NativeWorkflowCliFixture.Result = cli.callInstalled(executable,
          preparationService, "prepareEvidence", Vector("--input", take(input.toJsonStringC)))
        Vector(None, Some(16777216)).foreach { cap =>
          Given("only the isolated host's ordinary byte-limit property is omitted or set to 16 MiB: " + cap)
          _configure_limit(cli.root, cap)
          When("the installed public prepareEvidence reads the complete large Request")
          val denied = _invoke_(selected.input)
          Then("the runtime ceiling refuses the command without partial successful evidence")
          _assert_cli_failure(denied)
          (Files.readString(denied.invocation.stdout, UTF_8) + Files.readString(denied.invocation.stderr, UTF_8)) should
            include("runtime byte limit")
        }
        Given("the same isolated host with only its ordinary byte-limit property set to 128 MiB")
        _configure_limit(cli.root, Some(134217728))
        When("the installed public prepareEvidence reads the same complete Request")
        val successful = _invoke_(selected.input)
        Then("the complete expected evidence retains its target completion type run and handle")
        successful.exitCode shouldBe 0
        val actual = take(new RecordDecoder().json(Files.readString(successful.invocation.stdout, UTF_8)))
        _assert_evidence(actual, selected)
        Files.exists(cli.root.resolve("selected-input.json")) shouldBe false

        Given("an actually created out-of-root file and isolated malformed and trailing JSON files under the read root")
        val outside = Files.createTempFile(episode.root.getParent, "outside-review-", ".json")
        Files.writeString(outside, "{}", UTF_8)
        val malformed = episode.save("malformed-review-request", "{invalid")
        val trailing = episode.save("trailing-review-request", Files.readString(
          Path.of(URI.create(selected.input.getString("request").get)), UTF_8) + " {}")
        When("the same 128 MiB installed host receives each restricted Request reference")
        val outsideresult = _invoke_(_field(selected.input, "request", outside.toUri.toASCIIString))
        val invalid = Vector(malformed, trailing).map(reference => _invoke_(_field(selected.input, "request", reference)))
        Then("root and exact JSON restrictions reject each input while the valid result remains intact")
        _assert_cli_failure(outsideresult)
        (Files.readString(outsideresult.invocation.stdout, UTF_8) + Files.readString(outsideresult.invocation.stderr, UTF_8)) should
          include("outside configured read-only roots")
        invalid.foreach(_assert_cli_failure)
        _assert_evidence(actual, selected)
      }
    }
  }
}
