package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.{ComponentRecordStorage, DataStore}
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.goldenport.record.io.RecordDecoder
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import NativePreparationFixture.json
import SplitPhaseExplicitPartitionFixture.take

/** Actual public passive preparation and normal resource ownership, not authority issuance.
  * Existing NativeWorkflowCliSpec owns separate StartupImport and Start/Advance episodes.
  * @since   Oct. 15, 2026
  * @version Oct. 26, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativePreparationOperationSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with NativePreparationOperationFixture {
  private def _response(value: Consequence[OperationResponse]): Record = take(value) match {
    case OperationResponse.Json(result) =>
      result.asObject.map(_.keys.toSet) shouldBe Some(Set("datastore"))
      take(new RecordDecoder().json(result.noSpaces))
    case other => fail("Expected direct structured importer output: " + other)
  }
  private def field(input: Record, key: String, value: String): Record =
    input.removeKeys(Set(key)) ++ Record.data(key -> value)
  private def _entries(value: Record): Vector[Json] = json(value).hcursor.get[Vector[Json]]("datastore").toOption.get
  private def rows(value: Record): Vector[Json] = _entries(value).flatMap(_.hcursor.get[Vector[Json]]("records").toOption.get)

  "P1 Normal typed passive preparation" should afterWord(
    "in spec:normal-host-preparation-operations, example:P1, rules:R1,R2,R3,R4,R5, phase:6") {
    preparationOperations.foreach { operation =>
      s"execute $operation with real Unicode selected files and preserve its native owners" in {
        Given("a normal configured component, its actual resource context and specification-owned explicit inputs for " + operation)
        withPreparation() { episode =>
          val declared = SmWorkflowComponent.NativeWorkflowPreparationServiceService.operations.operations.find(_.name == operation).get
          val bound = episode.component.protocol.services.services.toVector.find(_.name == preparationService).get
            .operations.operations.find(_.name == operation).get
          val selected = episode.cases(operation)
          selected should not be empty
          selected.foreach { sample =>
            When("the actual current Operation executes " + sample.label + " through its generated named input")
            val actual = _response(episode.invoke(operation, sample.input))
            Then("the structured collections, identities and payloads equal the unchanged business owners and decode with existing storage codecs")
            bound.specification.request shouldBe declared.specification.request
            bound.specification.response shouldBe declared.specification.response
            json(actual) shouldBe json(sample.expected)
            actual.keySet shouldBe Set("datastore")
            rows(actual).foreach { row =>
              val fields = row.asObject.get.keys.toSet
              fields should contain("id")
              (fields - "id").size shouldBe 1
              row.hcursor.get[String](ProfileSelectionJsonV1.storageField).toOption.foreach { payload =>
                ProfileSelectionJsonV1.decodeStorageC(payload).isSuccess shouldBe true
              }
              row.hcursor.get[String](GoalPhaseSelectedPlanJsonV1.storageField).toOption.foreach { payload =>
                GoalPhaseSelectedPlanJsonV1.decodeStorageC(payload).isSuccess shouldBe true
              }
              row.hcursor.get[String](NativeExternalEvidence.storageField).toOption.foreach { payload =>
                NativeExternalEvidence.decodeStorageC(payload).isSuccess shouldBe true
              }
              fields should not contain "sessionId"
              fields should not contain "accepted"
              fields should not contain "permission"
            }
            episode.component.mcpReadyOperations shouldBe ThreeWorkflowLauncherAssembly.publishedMcpOperations
          }
          if (Set("prepareSplitStart", "prepareRepositoryEffect", "prepareAuthority", "prepareEvidence")(operation)) {
            Given("only passive output has been returned, without StartupImport or a workflow command")
            episode.project { call =>
              val ids = selected.flatMap(sample => rows(sample.expected).map(_.hcursor.get[String]("id").toOption.get)).distinct
              When("the ordinary managed store is read for each prepared native row")
              val observed = ids.map(id => take(ComponentRecordStorage.bindForCallC(call.core, "workflow",
                DataStore.CollectionId.EntityStore(EntityCollectionId("smworkflow", "cli", "host_evidence")),
                DataStore.StringEntryId(id)).flatMap(_.loadC())))
              Then("none of the returned importer rows has been saved by preparation")
              observed.foreach(_ shouldBe None)
              Consequence.unit
            }
          }
        }
      }
    }
  }

  "E9 Public typed SBT review preparation" should afterWord(
    "in spec:native-host-review-preparation, example:E9, rules:R1,R2,R4,R5,R6, phase:6") {
    "preserve two-class twenty-five-test review facts and child ownership through the actual prepareEvidence Operation" in {
      Given("Spec: docs/spec/native-host-review-preparation.md; Rules: R1,R2,R4,R5,R6; Example: E9; four fixture-issued typed reviews in selected normal output files")
      withPreparation() { episode =>
        val labels = Set("TypedReviewStep", "TypedReviewPhase", "TypedDirectRepositoryReview", "TypedForwardedRepositoryReview")
        When("the normal fixture selects the four typed review cases")
        val selected = episode.cases("prepareEvidence").filter(sample => labels(sample.label))
        Then("each declared Goal and direct or forwarded Repository case is present")
        selected.map(_.label).toSet shouldBe labels
        selected.foreach { sample =>
          Given("the selected normal output file for " + sample.label)
          val request = take(new RecordDecoder().json(Files.readString(
            Path.of(URI.create(sample.input.getString("request").get)), UTF_8)))
          val (context, purpose, session, submitSession) = if (sample.label == "TypedDirectRepositoryReview") {
            val issued = take(V.RepositorySyncAdvanceResult.createC(request))
            (issued.review.get.reviewContext.get, NativeExternalEvidence.Purpose.RepositoryReview, issued.sessionId, issued.submitSessionId)
          } else {
            val issued = take(V.GoalPhaseAdvanceResult.createC(request))
            if (sample.label == "TypedForwardedRepositoryReview") {
              (issued.repositoryReview.get.reviewContext.get, NativeExternalEvidence.Purpose.RepositoryReview, issued.sessionId, issued.submitSessionId)
            } else (issued.work.get.review.get.reviewContext.get, NativeExternalEvidence.Purpose.GoalReview, issued.sessionId, issued.submitSessionId)
          }
          When("the real prepareEvidence Operation reads the selected request result reference and execution files for " + sample.label)
          val actual = _response(episode.invoke("prepareEvidence", sample.input))
          val outputrows = rows(actual)
          val raw = outputrows.head.hcursor.get[String](NativeExternalEvidence.storageField).toOption.get
          val decoded = take(NativeExternalEvidence.decodeStorageC(raw))
          val stored = io.circe.parser.parse(raw.substring(NativeExternalEvidence.schema.length + 1)).toOption.get
          val target = stored.hcursor.downField("binding").downField("attestation").downField("target")
          val sbt = target.downField(if (purpose == NativeExternalEvidence.Purpose.GoalReview) "testChecks" else "checks")
            .downArray.downField("sbt")
          val result = stored.hcursor.downField("binding").downField("attestation").downField("result")
          Then("the raw importer payload explicitly retains kind outcome classes counts and its actual native owner and completion")
          outputrows.size shouldBe 1
          actual.keySet shouldBe Set("datastore")
          outputrows.head.asObject.get.keys.toSet shouldBe Set("id", NativeExternalEvidence.storageField)
          sbt.get[String]("kind") shouldBe Right("test-only")
          sbt.get[String]("outcome") shouldBe Right("succeeded")
          sbt.downField("selection").get[Vector[String]]("testClasses") shouldBe Right(Vector("example.FirstSpec", "example.SecondSpec"))
          sbt.downField("selection").get[Int]("completedSuites") shouldBe Right(2)
          sbt.downField("selection").get[Long]("testsRun") shouldBe Right(25L)
          decoded.purpose shouldBe purpose.identity
          stored.hcursor.downField("binding").get[String]("componentInstance") shouldBe Right(context.componentInstance)
          result.get[String]("runId") shouldBe Right(context.completion.runId)
          result.get[String]("continuationId") shouldBe Right(context.completion.continuationId)
          result.get[String]("typeIdentity") shouldBe Right(context.completion.typeIdentity)
          result.downField("handle").get[String]("instanceIdentity") shouldBe Right("instance")
          result.downField("handle").get[String]("workflowIdentity") shouldBe Right(
            if (purpose == NativeExternalEvidence.Purpose.GoalReview) "GoalPhaseWorkflow" else "RepositorySyncWorkflow")
          session shouldBe (if (sample.label == "TypedForwardedRepositoryReview") "outer-goal" else "instance")
          submitSession shouldBe Some("instance")
          json(actual) shouldBe json(sample.expected)
          Given("only passive importer output has returned for " + sample.label + " without explicit provisioning or workflow progression")
          episode.project { call =>
            When("the normal managed store reads the exact returned evidence identity")
            val identity = outputrows.head.hcursor.get[String]("id").toOption.get
            val saved = take(ComponentRecordStorage.bindForCallC(call.core, "workflow",
              DataStore.CollectionId.EntityStore(EntityCollectionId("smworkflow", "cli", "host_evidence")),
              DataStore.StringEntryId(identity)).flatMap(_.loadC()))
            Then("preparation has saved no evidence row and returned no workflow acceptance or effect authority")
            saved shouldBe None
            context.componentInstance shouldBe call.core.component.get.core.instanceId.canonicalKey
            decoded.purpose should not be NativeExternalEvidence.Purpose.RepositoryEffect.identity
            decoded.purpose should not be NativeExternalEvidence.Purpose.StepClosure.identity
            decoded.purpose should not be NativeExternalEvidence.Purpose.PhaseClosure.identity
            Consequence.unit
          }
        }
      }
    }
  }

  "P2 Typed request and execution ownership" should afterWord(
    "in spec:normal-host-preparation-operations, example:P2, rules:R1,R2,R4, phase:6") {
    "require the exact owning service and concrete input without acquiring a selected resource during request creation" in {
      Given("a bound normal Operation and a valid typed input pointing to an actual absent local file")
      withPreparation() { episode =>
        val selected = episode.cases("prepareRepositoryStart").head
        val missing = episode.root.resolve("未作成.json")
        val input = field(selected.input, "input", missing.toUri.toASCIIString)
        val operation = episode.component.protocol.services.services.toVector.find(_.name == preparationService).get
          .operations.operations.find(_.name == "prepareRepositoryStart").get
        val request = episode.request("prepareRepositoryStart", input)
        When("normal request creation parses only the generated DTO and the actual Operation subsequently executes")
        val created = operation.createOperationRequest(request)
        val executed = episode.invoke("prepareRepositoryStart", input)
        val crossed = operation.createOperationRequest(Request.of(component = episode.component.name,
          service = "GoalPhaseService", operation = "prepareRepositoryStart", properties = request.properties))
        val absent = operation.createOperationRequest(Request.of(component = episode.component.name,
          service = preparationService, operation = "prepareRepositoryStart"))
        val incomplete = operation.createOperationRequest(episode.request("prepareRepositoryStart", input.removeKeys(Set("participant"))))
        Then("creation succeeds without file acquisition while execution refuses absence and crossed or incomplete typed requests fail")
        created.isSuccess shouldBe true
        Files.exists(missing) shouldBe false
        executed.isSuccess shouldBe false
        Vector(crossed, absent, incomplete).foreach(_.isSuccess shouldBe false)
      }
    }

    "reject finite metadata before acquiring a missing resource and refuse unsupported or crossed assigned contexts" in {
      Given("normal typed inputs with malformed collection or time, unsupported evidence family-kind and an actual crossed Human context")
      withPreparation() { episode =>
        val authority = episode.cases("prepareAuthority").head
        val evidence = episode.cases("prepareEvidence").find(_.label == "GoalHuman").get
        val missing = episode.root.resolve("存在しない.json").toUri.toASCIIString
        val malformed = field(field(authority.input, "request", missing), "collection", "entity:invalid")
        val badtime = field(field(authority.input, "request", missing), "occurredAt", "invalid-time")
        val unsupported = field(field(evidence.input, "request", missing), "family", "Unknown")
        val splitreview = field(field(field(evidence.input, "request", missing), "family", "SplitPhase"), "kind", "review")
        val requestpath = Path.of(URI.create(evidence.input.getString("request").get))
        val original = take(V.GoalPhaseAdvanceResult.createC(take(new RecordDecoder().json(Files.readString(requestpath, UTF_8)))))
        val context = original.human.get.humanDecisionContext.get
        val crossed = original.copy(human = Some(original.human.get.copy(humanDecisionContext = Some(
          context.copy(purpose = NativeExternalEvidence.Purpose.RepositoryHuman.identity)))))
        val crossedinput = field(evidence.input, "request", episode.saveValue("crossed-human", crossed))
        When("the actual normal Operations validate each selected typed input")
        val collectionfailure = episode.invoke("prepareAuthority", malformed)
        val timefailure = episode.invoke("prepareAuthority", badtime)
        val failures = Vector(episode.invoke("prepareEvidence", unsupported), episode.invoke("prepareEvidence", splitreview),
          episode.invoke("prepareEvidence", evidence.input.removeKeys(Set("participant"))), episode.invoke("prepareEvidence", crossedinput))
        Then("metadata errors precede the missing-resource error and every invalid assignment returns no successful importer")
        collectionfailure.isSuccess shouldBe false
        collectionfailure.toString should include("collection")
        timefailure.isSuccess shouldBe false
        timefailure.toString should include("ISO Instant")
        failures.foreach(_.isSuccess shouldBe false)
      }
    }
  }

  "P3 Ordinary resource policy" should afterWord(
    "in spec:normal-host-preparation-operations, example:P3, rules:R2,R4, phase:6") {
    "apply the actual runtime byte cap, deny files outside configured roots and accept only exact local file URI references" in {
      Given("a real normal runtime with an explicit episode read root, a 256-byte ordinary read cap, an oversized selected Unicode file and a real file outside that root")
      withPreparation(cap = 256) { episode =>
        val selected = episode.cases("prepareRepositoryStart").head
        val oversized = episode.save("大きな入力", "あ" * 257)
        val outsideroot = Files.createTempDirectory(episode.root.getParent, "outside-")
        val outsidefile = outsideroot.resolve("入力.json")
        Files.writeString(outsidefile, "{}", UTF_8)
        When("the actual call reads the oversized file, attempts the outside-root file and rejects malformed or unsupported resource references")
        val denied = episode.invoke("prepareRepositoryStart", field(selected.input, "input", oversized))
        val outside = episode.invoke("prepareRepositoryStart", field(selected.input, "input", outsidefile.toUri.toASCIIString))
        val invalid = Vector("relative.json", "https://example.invalid/input.json", "urn:textus:input", " file:///tmp/input.json", "file:///tmp/input.json\n")
          .map(value => episode.invoke("prepareRepositoryStart", field(selected.input, "input", value)))
        Then("the CNCF resource owner reports its ordinary cap and outside-root denial, and no invalid reference becomes successful importer output")
        denied.isSuccess shouldBe false
        denied.toString should include("runtime byte limit")
        outside.isSuccess shouldBe false
        outside.toString should include("outside configured read-only roots")
        invalid.foreach(_.isSuccess shouldBe false)
      }
    }
  }

  "T4 and T6 Standard command preparation" should afterWord(
    "in spec:normal-host-preparation-operations, example:P4, rules:R1,R2,R3,R4,R5, phase:6") {
    preparationOperations.foreach { operation =>
      s"preserve the former ${operation} process examples through the ordinary CncfMain command" in {
        Given("the normal installed-host launcher profile and actual selected files for " + operation)
        withPreparation() { episode =>
          val cli = NativeWorkflowCliFixture.fresh("preparation-" + operation.toLowerCase,
            resourceRoots = Vector(episode.root))
          val cases = episode.cases(operation)
          val selected = if (operation == "prepareEvidence") cases.filter(sample =>
            Set("GoalHuman", "DirectRepositoryHuman", "ForwardedRepositoryHuman")(sample.label)) else cases
          selected.foreach { sample =>
            When("the ordinary CncfMain launcher command receives one concrete input for " + sample.label)
            val result = cli.prepareHostInput(operation, sample.input)
            Then("standard --json returns the direct datastore object with unchanged native payload and no wrapper")
            result.exitCode shouldBe 0
            val actual = take(new RecordDecoder().json(Files.readString(result.invocation.stdout, UTF_8)))
            actual.keySet shouldBe Set("datastore")
            json(actual) shouldBe json(sample.expected)
            result.invocation.argv.count(_ == "--input") shouldBe 1
            sys.props.get("smworkflow.spec.installedCommand") match {
              case Some(command) =>
                result.invocation.argv.take(3) shouldBe Vector(command, "--runtime-root", cli.root.toString)
              case None => result.invocation.argv should contain("org.goldenport.cncf.CncfMain")
            }
            result.invocation.argv should contain(
              s"${SmWorkflowComponent.componentId.name}.$preparationService.$operation")
          }
          Given("a selected command input with malformed finite metadata")
          When("the same standard command receives an invalid collection")
          val collectionkey = if (Set("prepareGoalStart", "prepareRepositoryStart")(operation)) "selectionCollection" else "collection"
          val refused = cli.prepareHostInput(operation, field(selected.head.input, collectionkey, "entity:invalid"))
          Then("the normal command returns failure without a successful datastore object")
          refused.exitCode should not be 0
          io.circe.parser.parse(Files.readString(refused.invocation.stdout, UTF_8)).toOption
            .flatMap(_.hcursor.get[Vector[Json]]("datastore").toOption) shouldBe None
        }
      }
    }

    "name the actual passive preparation service in normal CLI help" in {
      Given("the normal configured host and packaged generated preparation declaration")
      val cli = NativeWorkflowCliFixture.fresh("preparation-help")
      When("the standard component meta Help command selects the preparation service")
      val result = cli.preparationHelp()
      Then("normal Help exposes all six concrete preparation Operations")
      result.exitCode shouldBe 0
      val text = Files.readString(result.invocation.stdout, UTF_8)
      text should include(preparationService)
      preparationOperations.foreach(name => text should include(name))
    }
  }

  "P5 Raw standard output and explicit StartupImport" should afterWord(
    "in spec:normal-host-preparation-operations, example:P5, rules:R1,R2,R3,R4,R5, phase:6") {
    "start only after the actual prepared stdout is selected for a separate normal bootstrap" in {
      Given("a fresh normal host with selected Goal files but no helper seed or configured import")
      val cli = NativeWorkflowCliFixture.fresh("raw-prepared-import", bootstrapSelectedInput = false)
      When("normal Start runs before any explicit provisioning")
      val absent = cli.start()
      Then("no successful workflow Start is returned")
      absent.exitCode should not be 0
      Files.exists(cli.root.resolve("selected-input.json")) shouldBe false
      Given("the same selected files and concrete Goal preparation input")
      When("the standard preparation command writes its actual stdout")
      val prepared = cli.prepareHostInput("prepareGoalStart", cli.selectedGoalPreparationInput.toRecord())
      Then("the raw direct importer object retains the exact native storage fields")
      prepared.exitCode shouldBe 0
      val actual = take(new RecordDecoder().json(Files.readString(prepared.invocation.stdout, UTF_8)))
      actual.keySet shouldBe Set("datastore")
      rows(actual).map(_.asObject.get.keys.toSet).toSet shouldBe Set(
        Set("id", ProfileSelectionJsonV1.storageField), Set("id", GoalPhaseSelectedPlanJsonV1.storageField))
      Given("the specification explicitly selects that same actual stdout path as the trusted import input")
      cli.selectPreparedImport(prepared)
      When("a separate normal command bootstraps through real CNCF StartupImport and starts Goal")
      val started = cli.start()
      Then("the normal workflow reports started with an actual nonempty session")
      started.exitCode shouldBe 0
      val business = started.business[V.GoalPhaseStartResult]
      business.outcome shouldBe "started"
      business.sessionId.trim should not be empty
      started.invocation.pid should not be prepared.invocation.pid
    }
  }
}
