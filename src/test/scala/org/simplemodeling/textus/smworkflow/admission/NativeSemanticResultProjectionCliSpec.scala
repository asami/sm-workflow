/**
 * @since   Nov. 18, 2026
 * @version Nov. 18, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.zip.ZipFile
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import org.goldenport.protocol.{Property, Request}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseDependencyValidation.given
import DependencyValidationSpecificationFixture.*
import SplitPhaseExplicitPartitionFixture.take

/** Source-distributed caller projection connected to the authoritative public reader. */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.PackagedRuntimeTestDeclaration])
final class NativeSemanticResultProjectionCliSpec extends AnyWordSpec with Matchers
    with org.simplemodeling.textus.smworkflow.testing.DevelopFixtureValidation {
  private def candidateRuntime(car: Path): String = {
    val archive = new ZipFile(car.toFile)
    try {
      val stream = archive.getInputStream(archive.getEntry("car-runtime-manifest.json"))
      val text = try new String(stream.readAllBytes(), UTF_8) finally stream.close()
      parse(text).toOption.get.hcursor.downField("runtime").downField("cncf").get[String]("minimum").toOption.get
    } finally archive.close()
  }
  private def fixtureExecutable: Path = sys.props.get("smworkflow.spec.cncfCommand")
    .orElse(sys.env.get("SM_WORKFLOW_SPEC_CNCF_COMMAND")).map(Path.of(_)).getOrElse {
      sys.env.getOrElse("PATH", "").split(java.io.File.pathSeparator).iterator.filter(_.nonEmpty)
        .map(x => Path.of(x).toAbsolutePath.normalize.resolve("cncf"))
        .find(x => Files.isRegularFile(x) && Files.isExecutable(x)).get
    }

  "The public worker return" should {
    "exercise both finite caller actions and their pre-Submit refusals through specification-owned native doubles" in {
      val process = new ProcessBuilder("uv", "run", "--no-cache", "--managed-python", "--no-python-downloads",
        "--script", "scripts/tests/test_native_goal_command.py", "-k", "test_semantic_projection_precedes_both_submits")
        .redirectErrorStream(true).start()
      val output = new String(process.getInputStream.readAllBytes(), UTF_8)
      withClue(output) { process.waitFor() shouldBe 0 }
      output should include("Ran 1 test")
    }
    "keep arbitrary nested JSON keys opaque in the string transport independently of business admission" in {
      val arbitrary = Json.obj("hyphenated-group" -> Json.obj("processPlan" -> Json.obj("mixed-Key" -> "literal".asJson)))
      val output = take(Record.data("resultJson" -> arbitrary.noSpaces).toJsonStringC)
      val text = parse(output).toOption.get.hcursor.get[String]("resultJson").toOption.get
      text shouldBe arbitrary.noSpaces
      parse(text).toOption shouldBe Some(arbitrary)
    }

    "read a selected real issued work and file artifact using the packaged public Operation" in {
      val car = Path.of(Files.readString(Path.of("target/sm-workflow/packaged-test-car.txt")).trim)
      val scenario = F.fresh("semantic-result-projection", bootstrapSelectedInput = false,
        sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable))
      val runtime = sys.props.get("smworkflow.spec.cncfRuntime").orElse(sys.env.get("SM_WORKFLOW_SPEC_CNCF_RUNTIME"))
        .orElse(Some(candidateRuntime(car)))
      scenario.selectPackagedInstalled(F.PackagedInstalled(fixtureExecutable, car, runtime))
      val prepared = scenario.prepareHostInput("prepareGoalStart", scenario.selectedGoalPreparationInput.toRecord())
      withClue(prepared.invocation.logs) { prepared.exitCode shouldBe 0 }
      scenario.selectPreparedImport(prepared)
      val task = scenario.ownTask("projection-fixture")
      val started = scenario.start().business[V.GoalPhaseStartResult]
      scenario.retain(task, started)
      val issued = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      // The semantic worker's isolated product edit supplies a real candidate for
      // the normal post-Submit Advance and its registered typed validation.
      Files.writeString(Path.of(scenario.host.target.root).resolve("value.txt"), "expected\n", UTF_8)
      val reference = issued.retainedWork.getOrElse(fail("fixture must retain its exact issued work"))
      val context = parse(take(issued.issuedWorkContext.get.toRecord().toJsonStringC)).toOption.get
      val public = Json.obj("outcome" -> "completed".asJson, "summary" -> "Fixture completion".asJson,
        "findings" -> Json.arr(), "dependencyValidation" -> selection.asJson)
      val envelope = Json.obj("schemaVersion" -> "sm-workflow.public-worker-return.v1".asJson,
        "resultType" -> "GoalPhaseWorkResult".asJson, "workResource" -> reference.resource.asJson, "result" -> public)
      val artifact = scenario.root.resolve("worker-result.json")
      Files.writeString(artifact, envelope.noSpaces, UTF_8)
      val dispatchRequest = Json.obj("kind" -> "Request".asJson, "result_type" -> "GoalPhaseWorkResult".asJson,
        "issued_work_context" -> context, "retained_work" -> Json.obj("resource" -> reference.resource.asJson))
      val dispatchState = Json.obj("status" -> "STARTED".asJson, "request" -> dispatchRequest,
        "launchReturn" -> Json.obj("task_name" -> "fixture-worker".asJson),
        "resolved" -> Json.obj("concrete" -> Json.obj("model" -> "fixture".asJson, "effort" -> "medium".asJson)))
      val compactWorker = Json.obj("outcome" -> "completed".asJson, "summary" -> "Fixture completion".asJson,
        "resultType" -> "GoalPhaseWorkResult".asJson, "resultResource" -> artifact.toUri.toASCIIString.asJson)
      val adapterInput = scenario.root.resolve("dispatch-complete.json")
      Files.writeString(adapterInput, Json.obj("state" -> dispatchState, "agent_id" -> "fixture-worker".asJson,
        "semantic_result" -> compactWorker).noSpaces, UTF_8)
      val adapter = new ProcessBuilder("uv", "run", "--no-cache", "--managed-python", "--no-python-downloads",
        "--script", "scripts/goal-phase-dispatch.py", "complete", adapterInput.toString).redirectErrorStream(true).start()
      val adapterOutput = new String(adapter.getInputStream.readAllBytes(), UTF_8)
      withClue(adapterOutput) { adapter.waitFor() shouldBe 0 }
      val source = parse(adapterOutput).toOption.get.hcursor.downField("semanticResult").get[String]("resultResource").toOption.get
      def call(format: Option[String]) = scenario.projectSemanticResult(Record.createFull(
        Vector("workResource" -> reference.resource, "resultResource" -> source) ++
          format.toVector.map("format" -> _)))
      val compact = call(None)
      withClue(compact.invocation.logs) { compact.exitCode shouldBe 0 }
      val summary = parse(Files.readString(compact.invocation.stdout)).toOption.get
      summary.hcursor.downField("resultJson").focus shouldBe None
      summary.hcursor.get[String]("status").toOption shouldBe Some("completed")
      val full = call(Some("public-result"))
      withClue(full.invocation.logs) { full.exitCode shouldBe 0 }
      val result = parse(parse(Files.readString(full.invocation.stdout)).toOption.get.hcursor.get[String]("resultJson").toOption.get).toOption.get
      result shouldBe public
      val request = Request.ofOperation("specificationSubmission").copy(properties = List(Property("result", result.noSpaces, None)))
      take(NativeWorkflowResultInput.goalResultC(request)).dependencyValidation shouldBe Some(selection)
      val nativeReference = take(WorkflowRetainedWork.decodeResourceC(reference.resource))
      val crossedResources = Vector(
        WorkflowRetainedWork.resource(nativeReference.copy(owner = "GoalTaskWorkflow")),
        WorkflowRetainedWork.resource(nativeReference.copy(identity = nativeReference.identity + "-other")),
        WorkflowRetainedWork.resource(nativeReference.copy(revision = (nativeReference.revision.toLong + 1).toString)))
      val invalidArtifacts = crossedResources.map(value => envelope.mapObject(_.add("workResource", value.asJson))) ++ Vector(
        envelope.mapObject(_.add("resultType", "GoalTaskWorkResult".asJson)),
        envelope.mapObject(_.remove("workResource").add("issuedWorkContext", context)),
        envelope.mapObject(_.remove("result")))
      invalidArtifacts.foreach { invalid =>
        Files.writeString(artifact, invalid.noSpaces, UTF_8)
        call(Some("public-result")).exitCode should not be 0
      }
      // This facade is specification-owned; every operation it forwards runs in
      // the installed CNCF against this candidate CAR and isolated assembly.
      val callerPublic = Json.obj("outcome" -> "completed".asJson, "summary" -> "Implement step completed".asJson,
        "findings" -> Json.arr())
      val callerArtifact = scenario.root.resolve("caller-worker-result.json")
      val callerEnvelope = Json.obj("schemaVersion" -> "sm-workflow.public-worker-return.v1".asJson,
        "resultType" -> "GoalPhaseWorkResult".asJson, "workResource" -> reference.resource.asJson, "result" -> callerPublic)
      Files.writeString(callerArtifact, callerEnvelope.noSpaces, UTF_8)
      val callerCompact = Json.obj("outcome" -> "completed".asJson, "summary" -> "Implement step completed".asJson,
        "resultType" -> "GoalPhaseWorkResult".asJson, "resultResource" -> callerArtifact.toUri.toASCIIString.asJson)
      val callerAdapterInput = scenario.root.resolve("caller-dispatch-complete.json")
      Files.writeString(callerAdapterInput, Json.obj("state" -> dispatchState, "agent_id" -> "fixture-worker".asJson,
        "semantic_result" -> callerCompact).noSpaces, UTF_8)
      val callerAdapter = new ProcessBuilder("uv", "run", "--no-cache", "--managed-python", "--no-python-downloads",
        "--script", "scripts/goal-phase-dispatch.py", "complete", callerAdapterInput.toString).redirectErrorStream(true).start()
      val callerAdapterOutput = new String(callerAdapter.getInputStream.readAllBytes(), UTF_8)
      withClue(callerAdapterOutput) { callerAdapter.waitFor() shouldBe 0 }
      val detail = scenario.readRetainedWork(reference.toRecord())
      withClue(detail.invocation.logs) { detail.exitCode shouldBe 0 }
      val selectedRequest = scenario.root.resolve("selected-retained-request.json")
      Files.copy(detail.invocation.stdout, selectedRequest)
      val submission = scenario.root.resolve("caller-submission.json")
      val dispatchResult = parse(callerAdapterOutput).toOption.get.hcursor.downField("semanticResult").focus.get
      Files.writeString(submission, Json.obj("result" -> dispatchResult,
        "resultReference" -> Json.obj("identity" -> "specification-result".asJson, "revision" -> "1".asJson)).noSpaces, UTF_8)
      def shell(value: String) = "'" + value.replace("'", "'\\''") + "'"
      val facade = scenario.root.resolve("candidate-cli")
      val prefix = Vector(fixtureExecutable.toString) ++ F.runtimeArguments(runtime) ++ Vector(car.toString,
        "command", "--force-exit", "--no-default-components", s"--textus.assembly.descriptor=${scenario.root.resolve(".textus/assembly.yaml")}")
      Files.writeString(facade, "#!/bin/sh\nexec " + prefix.map(shell).mkString(" ") + " \"$@\"\n", UTF_8)
      facade.toFile.setExecutable(true) shouldBe true
      val callerOutput = task.resolve("caller-complete")
      val caller = new ProcessBuilder("uv", "run", "--no-cache", "--managed-python", "--no-python-downloads",
        "--script", "scripts/native-goal-command.py", "--executable", facade.toString,
        "--runtime-root", scenario.root.toString, "--task", task.toString, "--action", "complete",
        "--output", callerOutput.toString, "--request", selectedRequest.toString,
        "--submission", submission.toString).redirectErrorStream(true).start()
      val callerLog = new String(caller.getInputStream.readAllBytes(), UTF_8)
      withClue(callerLog) { caller.waitFor() shouldBe 0 }
      val projection = parse(Files.readString(callerOutput.resolve("projection/response.json"))).toOption.get
      parse(projection.hcursor.get[String]("resultJson").toOption.get).toOption shouldBe Some(callerPublic)
      val acknowledgement = parse(Files.readString(callerOutput.resolve("response.json"))).toOption.get
      acknowledgement.hcursor.get[Boolean]("accepted").toOption shouldBe Some(true)
      Files.isRegularFile(callerOutput.resolve("advance/response.json")) shouldBe true
    }

    "preserve enriched reference data through the normal dispatch completion and full public reader" in {
      val context = Json.obj("result_type" -> "GoalPhaseWorkResult".asJson,
        "continuation_id" -> "fixture:1".asJson, "expected_revision" -> "2".asJson)
      val request = Json.obj("kind" -> "Request".asJson, "result_type" -> "GoalPhaseWorkResult".asJson,
        "issued_work_context" -> context, "submit_operation" -> "submitGoalPhaseWorkResult".asJson,
        "submit_session_id" -> "fixture-session".asJson)
      val public = Json.obj("outcome" -> "completed".asJson, "summary" -> "Fixture completion".asJson,
        "findings" -> Json.arr(), "dependencyValidation" -> selection.asJson)
      val workResource = "sm-workflow-work:fixture"
      val envelope = Json.obj("schemaVersion" -> "sm-workflow.public-worker-return.v1".asJson,
        "resultType" -> "GoalPhaseWorkResult".asJson, "workResource" -> workResource.asJson, "result" -> public)
      val resultResource = "file:///fixture/result.json"
      def projection(doc: Json, format: Option[String]) = NativeSemanticResultProjection.projectC(
        workResource, resultResource, format, _ => org.goldenport.Consequence.success(request.noSpaces),
        _ => org.goldenport.Consequence.success(doc.noSpaces))
      val compact = take(projection(envelope, None))
      compact.hcursor.downField("resultJson").focus shouldBe None
      compact.hcursor.get[String]("status").toOption shouldBe Some("completed")
      compact.hcursor.get[String]("nextOperation").toOption shouldBe Some("submitGoalPhaseWorkResult")
      parse(take(projection(envelope, Some("public-result"))).hcursor.get[String]("resultJson").toOption.get).toOption shouldBe Some(public)
      projection(envelope.mapObject(_.add("resultType", "GoalTaskWorkResult".asJson)), None).isSuccess shouldBe false
      projection(envelope.mapObject(_.add("workResource", "sm-workflow-work:other".asJson)), None).isSuccess shouldBe false
      projection(envelope.mapObject(_.remove("workResource").add("issuedWorkContext", context)), None).isSuccess shouldBe false
      projection(envelope.mapObject(_.add("payload", public)), None).isSuccess shouldBe false
      projection(envelope, Some("other")).isSuccess shouldBe false
      val dispatchRequest = request.mapObject(_.add("retained_work", Json.obj("resource" -> workResource.asJson)))
      val state = Json.obj("status" -> "STARTED".asJson, "request" -> dispatchRequest,
        "launchReturn" -> Json.obj("task_name" -> "fixture-worker".asJson),
        "resolved" -> Json.obj("concrete" -> Json.obj("model" -> "fixture".asJson, "effort" -> "medium".asJson)))
      val input = Files.createTempFile("public-worker-result", ".json")
      try {
        Files.writeString(input, Json.obj("state" -> state, "agent_id" -> "fixture-worker".asJson,
          "semantic_result" -> envelope).noSpaces, UTF_8)
        val process = new ProcessBuilder("uv", "run", "--no-cache", "--managed-python", "--no-python-downloads",
          "--script", "scripts/goal-phase-dispatch.py", "complete", input.toString).redirectErrorStream(true).start()
        val output = new String(process.getInputStream.readAllBytes(), UTF_8)
        withClue(output) { process.waitFor() shouldBe 0 }
        val projected = parse(output).toOption.get.hcursor.downField("semanticResult").focus.get
        projected shouldBe public
        def read(value: Json) = NativeWorkflowResultInput.goalResultC(
          Request.ofOperation("specificationSubmission").copy(properties = List(Property("result", value.noSpaces, None))))
        take(read(projected)).dependencyValidation shouldBe Some(selection)
        read(projected.mapObject(_.add("dependencyAdmission", Json.Null))).isSuccess shouldBe false
        val badSelection = projected.mapObject(_.add("dependencyValidation", Json.obj("unexpected" -> Json.True)))
        read(badSelection).isSuccess shouldBe false
      } finally Files.deleteIfExists(input)
    }
  }
}
